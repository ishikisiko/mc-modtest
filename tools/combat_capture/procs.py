"""Process bookkeeping for the capture session: identity by (pid, start time),
descendants via /proc, kill by PID, the heavy-work lock, free ports and X
displays, and the host-program check. Linux only."""
from __future__ import annotations

import ctypes
import errno
import fcntl
import os
import shutil
import signal
import socket
import subprocess
import time
from pathlib import Path

REQUIRED_PROGRAMS = ("Xvfb", "xdotool", "ffmpeg", "import", "convert", "montage")
RESERVED_PORTS = {8765}  # taken by an unrelated service on the capture host


def missing_programs(names=REQUIRED_PROGRAMS, which=None) -> list[str]:
    which = which or shutil.which
    return [n for n in names if which(n) is None]


def default_lock_path(repo: Path) -> Path:
    """$MC_HEAVY_LOCK, else ``.mc-heavy.lock`` next to the main checkout (the
    directory that holds the repository; worktrees resolve to the same file)."""
    env = os.environ.get("MC_HEAVY_LOCK")
    if env:
        return Path(env)
    main = repo
    try:
        common = subprocess.run(["git", "-C", str(repo), "rev-parse", "--path-format=absolute", "--git-common-dir"],
                                capture_output=True, text=True, timeout=10).stdout.strip()
        if common:
            main = Path(common).parent
    except (OSError, subprocess.SubprocessError):
        pass
    return main.parent / ".mc-heavy.lock"


def acquire_lock(path: Path, timeout: float | None, on_wait=None) -> int:
    """Open and flock(LOCK_EX) the lock file; returns the fd. Polls so a
    timeout can apply; on_wait(seconds_waited) is called about every 30 s."""
    fd = os.open(str(path), os.O_RDWR | os.O_CREAT, 0o664)
    start = time.time()
    last_note = start
    while True:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return fd
        except OSError as e:
            if e.errno not in (errno.EAGAIN, errno.EACCES):
                os.close(fd)
                raise
        now = time.time()
        if timeout is not None and now - start > timeout:
            os.close(fd)
            raise TimeoutError(f"heavy-work lock {path} still busy after {timeout:.0f}s")
        if on_wait and now - last_note >= 30:
            on_wait(now - start)
            last_note = now
        time.sleep(1.0)


def lock_is_free(path: Path) -> bool:
    try:
        fd = os.open(str(path), os.O_RDWR | os.O_CREAT, 0o664)
    except OSError:
        return False
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.flock(fd, fcntl.LOCK_UN)
        return True
    except OSError:
        return False
    finally:
        os.close(fd)


def set_child_subreaper() -> bool:
    """Orphaned descendants (e.g. a JVM whose Gradle parent died) are
    re-parented to this process, so they stay findable as descendants."""
    try:
        libc = ctypes.CDLL(None, use_errno=True)
        return libc.prctl(36, 1, 0, 0, 0) == 0  # PR_SET_CHILD_SUBREAPER
    except (OSError, AttributeError):
        return False


def _stat_fields(pid: int):
    try:
        raw = Path(f"/proc/{pid}/stat").read_text()
    except OSError:
        return None
    rest = raw.rsplit(")", 1)[1].split()
    return rest  # rest[0]=state, rest[1]=ppid, rest[19]=starttime


def start_time(pid: int) -> int | None:
    f = _stat_fields(pid)
    return int(f[19]) if f else None


def is_alive(pid: int, started: int | None = None) -> bool:
    """True if pid exists, is not a zombie and (when given) has the same start
    time as when it was recorded, so a reused PID never counts."""
    f = _stat_fields(pid)
    if not f or f[0] in ("Z", "X"):
        return False
    return started is None or int(f[19]) == int(started)


def proc_table() -> dict[int, tuple[int, str]]:
    table = {}
    for d in Path("/proc").iterdir():
        if not d.name.isdigit():
            continue
        f = _stat_fields(int(d.name))
        if not f:
            continue
        try:
            cmd = (d / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace").strip()
        except OSError:
            cmd = ""
        table[int(d.name)] = (int(f[1]), cmd)
    return table


def descendants(root: int, table=None) -> list[int]:
    table = table if table is not None else proc_table()
    children: dict[int, list[int]] = {}
    for pid, (ppid, _) in table.items():
        children.setdefault(ppid, []).append(pid)
    out, stack = [], [root]
    while stack:
        p = stack.pop()
        for c in children.get(p, []):
            if c not in out and c != root:
                out.append(c)
                stack.append(c)
    return out


def describe(pid: int) -> str:
    try:
        return Path(f"/proc/{pid}/cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")[:160]
    except OSError:
        return "?"


def kill_pids(pids, grace: float = 20.0, log=print) -> None:
    """SIGTERM each live pid, wait up to `grace`, SIGKILL what is left.
    pids: iterable of (pid, start_time_or_None, label)."""
    targets = [(p, s, label) for p, s, label in pids if is_alive(p, s)]
    for p, _, label in targets:
        log(f"  SIGTERM {p} ({label})")
        try:
            os.kill(p, signal.SIGTERM)
        except ProcessLookupError:
            pass
    deadline = time.time() + grace
    while time.time() < deadline and any(is_alive(p, s) for p, s, _ in targets):
        reap()
        time.sleep(0.5)
    for p, s, label in targets:
        if is_alive(p, s):
            log(f"  SIGKILL {p} ({label})")
            try:
                os.kill(p, signal.SIGKILL)
            except ProcessLookupError:
                pass
    time.sleep(0.5)
    reap()


def reap() -> None:
    """Collect exited children (we are a subreaper, so orphans land here)."""
    while True:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            return
        if pid == 0:
            return


def port_free(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("127.0.0.1", port))
            return True
        except OSError:
            return False


def free_ports(count: int, start: int = 25610, stop: int = 25700, check=port_free) -> list[int]:
    out = []
    for p in range(start, stop):
        if p in RESERVED_PORTS:
            continue
        if check(p):
            out.append(p)
            if len(out) == count:
                return out
    raise RuntimeError(f"no {count} free TCP ports in {start}..{stop}")


def display_free(n: int) -> bool:
    return not Path(f"/tmp/.X11-unix/X{n}").exists() and not Path(f"/tmp/.X{n}-lock").exists()


def free_display(start: int = 90, stop: int = 200, check=display_free) -> int:
    for n in range(start, stop):
        if check(n):
            return n
    raise RuntimeError(f"no free X display number in {start}..{stop}")
