"""Talking to the running client on the session's X display: UI-state probe,
keys, chat, clicks, F5, F3+T, client-log tailing and stable screen grabs.

Keys typed while no screen is open hit game binds (R toggles combat mode, V/B
start meditation, X stops it), and keys typed while a screen is open go into
that screen. Every keystroke here is preceded by a UI-state check: the game
holds the X pointer grab only when it is in-game with no screen open.
"""
from __future__ import annotations

import ctypes
import datetime
import os
import re
import subprocess
import time
from pathlib import Path


class GameInputError(RuntimeError):
    pass


class ChatStuck(GameInputError):
    pass


class ProbeRejected(GameInputError):
    """The client logged a refusal for the command (e.g. `PAL_SMOKE third_person
    rejected reason=...`); retrying the same command cannot help."""

    def __init__(self, command: str, line: str, reason: str | None):
        self.command, self.line, self.reason = command, line, reason
        super().__init__(f"client refused {command!r}: reason={reason or '?'} ({line.strip()[-200:]})")


# Largest per-channel difference two grabs may show and still count as the
# same picture (software rendering noise across the whole frame).
NOISE_LEVELS = 2

LOG_TS = re.compile(r"^\[(\d{2}[A-Za-z]{3}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\]")


def log_line_time(line: str):
    m = LOG_TS.match(line)
    if not m:
        return None
    try:
        return datetime.datetime.strptime(m.group(1), "%d%b%Y %H:%M:%S.%f").timestamp()
    except ValueError:
        return None


class LogTail:
    """Reads only lines appended to a log after construction."""

    def __init__(self, path):
        self.path = Path(path)
        self.pos = self.path.stat().st_size if self.path.is_file() else 0
        self.partial = ""
        self.lines: list[str] = []
        self.cursor = 0

    def poll(self):
        if not self.path.is_file():
            return
        size = self.path.stat().st_size
        if size < self.pos:  # rotated
            self.pos = 0
        if size == self.pos:
            return
        with open(self.path, "rb") as f:
            f.seek(self.pos)
            chunk = f.read()
        self.pos += len(chunk)
        text = self.partial + chunk.decode("utf-8", errors="replace")
        parts = text.split("\n")
        self.partial = parts.pop()
        self.lines.extend(parts)

    def wait(self, pattern: str, timeout: float, poll: float = 0.1):
        rx = re.compile(pattern)
        deadline = time.time() + timeout
        while True:
            self.poll()
            for i in range(self.cursor, len(self.lines)):
                m = rx.search(self.lines[i])
                if m:
                    self.cursor = i + 1
                    return m, self.lines[i]
            if time.time() >= deadline:
                return None, None
            time.sleep(poll)

    def wait_either(self, pattern: str, reject: str | None, timeout: float, poll: float = 0.1):
        """Like wait, but also stops at the first line matching `reject`.
        Returns (match, line, rejected)."""
        if not reject:
            m, line = self.wait(pattern, timeout, poll)
            return m, line, False
        rx, rj = re.compile(pattern), re.compile(reject)
        deadline = time.time() + timeout
        while True:
            self.poll()
            for i in range(self.cursor, len(self.lines)):
                for r, rejected in ((rx, False), (rj, True)):
                    m = r.search(self.lines[i])
                    if m:
                        self.cursor = i + 1
                        return m, self.lines[i], rejected
            if time.time() >= deadline:
                return None, None, False
            time.sleep(poll)

    def tail_text(self, n: int = 12) -> str:
        self.poll()
        return "\n".join("    " + l[:220] for l in self.lines[-n:]) or "    (no new log lines)"


class Game:
    def __init__(self, display: str, client_log: Path, size=(960, 540), ui_check: bool = True, log=print):
        self.display = display
        self.client_log = Path(client_log)
        self.size = size
        self.ui_check = ui_check
        self.log = log
        self.env = dict(os.environ, DISPLAY=display)

    # ------------------------------------------------------------ low level
    def xdo(self, *args):
        return subprocess.run(["xdotool", *args], env=self.env, check=True, capture_output=True,
                              text=True, timeout=20).stdout

    def pointer_grabbed(self):
        """True: in-game, no screen open (the game holds the pointer grab).
        False: a screen is open or the window is unfocused. None: no probe."""
        try:
            x = ctypes.cdll.LoadLibrary("libX11.so.6")
        except OSError:
            return None
        x.XOpenDisplay.restype = ctypes.c_void_p
        x.XOpenDisplay.argtypes = [ctypes.c_char_p]
        x.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
        x.XDefaultRootWindow.restype = ctypes.c_ulong
        x.XGrabPointer.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_uint, ctypes.c_int,
                                   ctypes.c_int, ctypes.c_ulong, ctypes.c_ulong, ctypes.c_ulong]
        x.XUngrabPointer.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        x.XCloseDisplay.argtypes = [ctypes.c_void_p]
        d = x.XOpenDisplay(self.display.encode())
        if not d:
            return None
        try:
            rc = x.XGrabPointer(d, x.XDefaultRootWindow(d), 0, 0, 1, 1, 0, 0, 0)
            if rc == 0:  # GrabSuccess: nobody held it
                x.XUngrabPointer(d, 0)
                return False
            return rc == 1  # AlreadyGrabbed
        finally:
            x.XCloseDisplay(d)

    def wait_ingame(self, timeout: float = 3.0, poll: float = 0.1) -> bool:
        deadline = time.time() + timeout
        while True:
            state = self.pointer_grabbed()
            if state is not False:
                return True
            if time.time() >= deadline:
                return False
            time.sleep(poll)

    def require_ingame(self, what: str, timeout: float = 3.0):
        if not self.ui_check:
            return
        if not self.wait_ingame(timeout):
            raise GameInputError(f"not sending {what}: a game screen looks open (the pointer is not grabbed)")

    # ------------------------------------------------------------ actions
    def key(self, keysym: str):
        self.require_ingame(f"key {keysym}")
        self.xdo("key", "--delay", "60", keysym)

    def type_chat(self, text: str) -> float:
        """T, type, Enter; returns the time right after Enter. The chat must
        close again (pointer re-grabbed) before the call returns."""
        self.require_ingame(f"chat {text!r}")
        self.xdo("key", "t")
        time.sleep(0.5)
        self.xdo("type", "--delay", "35", text)
        time.sleep(0.2)
        self.xdo("key", "Return")
        t = time.time()
        if not self.ui_check:
            time.sleep(0.4)
            return t
        # The game re-grabs the pointer on the frame after the chat closes. Our probe grabs
        # the pointer for an instant; if the two collide the game's grab fails and is not
        # retried, so do not probe during that window.
        time.sleep(0.6)
        if self.wait_ingame(5.0) or self.recover_grab():
            return t
        raise ChatStuck(f"after {text!r} a screen is still open (chat did not close?)")

    def recover_grab(self) -> bool:
        """The client is either in game without the pointer grab, or a screen
        is really open. A middle click re-grabs the pointer in game (it picks
        nothing when aimed at air and is ignored by the chat screen); only if
        that does not help is Escape sent, which closes the open screen."""
        self.xdo("click", "2")
        time.sleep(0.6)
        if self.pointer_grabbed() is not False:
            self.log("  pointer re-grabbed with a middle click (the game had lost its grab)")
            return True
        self.xdo("key", "Escape")
        time.sleep(0.8)
        if self.wait_ingame(2.0):
            self.log("  a screen was open after Enter; closed it with Escape")
        return False

    def chat(self, text: str, confirm: str | None = None, timeout: float = 8.0, retries: int = 1,
             reject: str | None = None):
        """Send a chat line; with `confirm`, wait for a client log line
        matching it and return (match, line). A chat that did not close or
        did not confirm is retried (the probes are idempotent). With `reject`,
        a client log line matching it ends the wait at once with
        ProbeRejected (no retry); its first group, if any, is the reason."""
        for attempt in range(retries + 1):
            tail = LogTail(self.client_log)
            try:
                self.type_chat(text)
            except ChatStuck:
                m, line, rejected = tail.wait_either(confirm, reject, 1.0) if confirm else (None, None, False)
                if m and rejected:
                    raise ProbeRejected(text, line, m.group(1) if m.groups() else None) from None
                if m and self.wait_ingame(1.0):
                    return m, line
                if attempt < retries and self.wait_ingame(1.0):
                    self.log(f"  retrying {text!r}")
                    continue
                raise
            if not confirm:
                return None, None
            m, line, rejected = tail.wait_either(confirm, reject, timeout)
            if m and rejected:
                raise ProbeRejected(text, line, m.group(1) if m.groups() else None)
            if m:
                return m, line
            if attempt < retries:
                self.log(f"  no confirmation for {text!r}; retrying")
                continue
            raise GameInputError(f"client did not confirm {text!r} within {timeout}s (pattern {confirm!r}). "
                                 f"Last client log lines:\n{tail.tail_text()}")
        return None, None

    def click(self, hold: float = 0.06):
        # no mousemove: the game holds the cursor and pointer motion turns the camera
        self.xdo("mousedown", "1")
        time.sleep(hold)
        self.xdo("mouseup", "1")

    def f3_t(self, rig_pattern: str, timeout: float = 120.0, settle: float = 5.0):
        """Resource reload: F3+T, wait for 'Reloading ResourceManager' and the
        rig-loaded line, then settle before any further keystroke."""
        self.require_ingame("F3+T")
        tail = LogTail(self.client_log)
        t0 = time.time()
        self.xdo("keydown", "F3")
        time.sleep(0.15)
        self.xdo("key", "t")
        time.sleep(0.15)
        self.xdo("keyup", "F3")
        m, _ = tail.wait(r"Reloading ResourceManager", 20)
        if not m:
            raise GameInputError("F3+T sent but the client log shows no 'Reloading ResourceManager' within 20 s.\n"
                                 + tail.tail_text())
        m2, line2 = tail.wait(rig_pattern, timeout)
        if not m2:
            raise GameInputError(f"no log line matching {rig_pattern!r} within {timeout:.0f}s after F3+T.\n"
                                 + tail.tail_text())
        time.sleep(settle)
        tail.poll()
        problems = [l for l in tail.lines if re.search(r"/ERROR\]", l) and re.search(r"combat|rig|sword", l, re.I)]
        return {"seconds": round(time.time() - t0, 1), "rig_line": line2.strip()[:300], "problems": problems[:20]}

    # ------------------------------------------------------------ frames
    def grab_raw(self) -> bytes:
        w, h = self.size
        out = subprocess.run(["import", "-silent", "-window", "root", "-crop", f"{w}x{h}+0+0", "-depth", "8", "rgb:-"],
                             env=self.env, capture_output=True, timeout=30, check=True).stdout
        if len(out) != w * h * 3:
            raise GameInputError(f"screen grab returned {len(out)} bytes, expected {w * h * 3}")
        return out

    def stable_grab(self, max_wait: float = 6.0, gap: float = 0.15, settle: float = 0.3,
                    noise_levels: int = NOISE_LEVELS):
        """Grab until the picture holds still. Settled means two consecutive
        grabs are identical, or (whole-frame rendering noise) three consecutive
        grabs are pairwise within `noise_levels` per channel everywhere. A pose
        still moving changes edge pixels by far more than that, and a slow drift
        shows up between the first and third grab. Returns (raw, info):
        info['match'] is 'exact' or 'noise' (then info['max_level_diff'] is the
        largest difference seen); info['stable'] is False if max_wait passed
        first (the last grab is returned with info['diff_fraction'] and
        info['max_level_diff'])."""
        time.sleep(settle)
        t0 = time.time()
        recent = [self.grab_raw()]
        grabs = 1
        while True:
            time.sleep(gap)
            recent = (recent + [self.grab_raw()])[-3:]
            grabs += 1
            verdict, worst = settled(recent, noise_levels)
            if verdict:
                info = {"stable": True, "grabs": grabs, "seconds": round(time.time() - t0, 2), "match": verdict}
                if verdict == "noise":
                    info["max_level_diff"] = worst
                return recent[-1], info
            if time.time() - t0 >= max_wait:
                return recent[-1], {"stable": False, "grabs": grabs, "seconds": round(time.time() - t0, 2),
                                    "diff_fraction": round(diff_fraction(recent[-2], recent[-1]), 5),
                                    "max_level_diff": worst}

    def shot(self, path: Path, max_wait: float = 6.0, settle: float = 0.3) -> dict:
        """One full frame to a PNG once the picture holds still (or max_wait
        passes). Refused while a screen is open (it would be in the frame and
        the pointer is not the game's); nothing is typed or clicked."""
        self.require_ingame("a screen grab")
        raw, info = self.stable_grab(max_wait=max_wait, settle=settle)
        self.save_png(raw, Path(path))
        return info

    def save_png(self, raw: bytes, path: Path):
        w, h = self.size
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["convert", "-size", f"{w}x{h}", "-depth", "8", "rgb:-", f"png:{path}"],
                       input=raw, check=True, timeout=60)


def max_level_diff(a: bytes, b: bytes) -> int:
    """Largest per-byte (per-channel) difference; 0 for identical grabs, which
    return at once. Otherwise every byte is checked (about 0.1 s at 960x540)."""
    if a == b:
        return 0
    if len(a) != len(b):
        return 255
    return max(x - y if x > y else y - x for x, y in zip(a, b))


def settled(recent: list[bytes], noise_levels: int = NOISE_LEVELS) -> tuple[str | None, int]:
    """Stability rule over the most recent grabs (oldest first). Returns
    ('exact', 0) if the last two are identical; ('noise', d) if the last three
    are pairwise at most noise_levels apart per channel (d = the largest of the
    three differences); otherwise (None, d) with d = the last two's difference."""
    if len(recent) < 2:
        return None, 0
    last = max_level_diff(recent[-2], recent[-1])
    if last == 0:
        return "exact", 0
    if last > noise_levels or len(recent) < 3:
        return None, last
    worst = max(last, max_level_diff(recent[-3], recent[-2]))
    if worst <= noise_levels:
        worst = max(worst, max_level_diff(recent[-3], recent[-1]))
    return ("noise" if worst <= noise_levels else None), worst


def diff_fraction(a: bytes, b: bytes, stride: int = 7) -> float:
    """Share of sampled bytes that differ (cheap, stdlib only)."""
    n = min(len(a), len(b))
    if n == 0:
        return 0.0
    idx = range(0, n, stride)
    diff = sum(1 for i in idx if a[i] != b[i])
    return diff / len(idx)
