"""Capture session: Xvfb + the repository's acceptance server + one smoke
client, run under a supervising process that holds the shared heavy-work lock
for as long as any of them lives.

`session start` launches the supervisor detached (own session, own log) and
waits until it reports ready; later commands find the session through the
state file `run-combat-capture/session.json`. `session stop` asks the
supervisor to shut down; if it is gone, the recorded PIDs are stopped
directly (each checked against its recorded start time).
"""
from __future__ import annotations

import ctypes
import datetime
import json
import os
import secrets
import shutil
import signal
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path

from . import procs
from .rcon import RconError, connect as rcon_connect
from .xgame import Game

REPO = Path(__file__).resolve().parents[2]
RUN_DIR = REPO / "run-combat-capture"
STATE_FILE = RUN_DIR / "session.json"
VIEW_FILE = RUN_DIR / "view.json"
LOG_DIR = RUN_DIR / "logs"
CLIENT_DIR_REL = "run-combat-capture/client"
CLIENT_DIR = REPO / CLIENT_DIR_REL
SERVER_DIR = REPO / "run-acceptance"  # gameDirectory of the runAcceptanceServer run (build.gradle)
PROPS_BACKUP = RUN_DIR / "server.properties.orig"
PROPS_ABSENT_MARK = RUN_DIR / "server.properties.absent"
LEVEL_NAME = "combat_capture_world"
DEFAULT_USERNAME = "CaptureDev"
SCREEN = (960, 540)  # the smoke client window size (build.gradle combat_smoke_server)
RIG_LOADED = r"Loaded first-person swing rig"

PHASES_ACTIVE = ("waiting_lock", "starting_xvfb", "starting_server", "starting_client", "ready", "stopping")

# Forced client options (merged into the capture game dir's options.txt before launch).
CLIENT_OPTIONS = {
    "version": "3955",
    "pauseOnLostFocus": "false",
    "onboardAccessibility": "false",
    "tutorialStep": "none",
    "skipMultiplayerWarning": "true",
    "joinedFirstServer": "true",
    "realmsNotifications": "false",
    "narrator": "0",
    "renderDistance": "6",
    "simulationDistance": "5",
    "maxFps": "60",
    "enableVsync": "false",
    "graphicsMode": "1",
    "renderClouds": '"false"',  # moving clouds would keep two grabs from ever matching
    "guiScale": "0",
    "fov": "0.0",  # 70 degrees
    "lang": "en_us",
    "autoJump": "false",
    "showSubtitles": "false",
    "fullscreen": "false",
    "key_key.attack": "key.mouse.left",
    "key_key.togglePerspective": "key.keyboard.f5",
    "key_key.chat": "key.keyboard.t",
}


def server_properties(server_port: int, rcon_port: int, password: str) -> dict:
    return {
        "server-ip": "127.0.0.1",
        "server-port": str(server_port),
        "online-mode": "false",
        "enforce-secure-profile": "false",
        "enable-rcon": "true",
        "rcon.port": str(rcon_port),
        "rcon.password": password,
        "broadcast-rcon-to-ops": "false",
        "broadcast-console-to-ops": "false",
        "enable-query": "false",
        "level-name": LEVEL_NAME,
        "level-seed": "20261002",
        "level-type": "minecraft\\:flat",
        "generator-settings": '{"layers":[{"block":"minecraft:bedrock","height":1},'
                              '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
                              '"biome":"minecraft:plains"}',
        "generate-structures": "false",
        "gamemode": "survival",
        "force-gamemode": "true",
        "difficulty": "normal",
        "spawn-protection": "0",
        "spawn-monsters": "false",
        "spawn-animals": "false",
        "spawn-npcs": "false",
        "pvp": "false",
        "allow-flight": "true",
        "allow-nether": "false",
        "max-players": "2",
        "view-distance": "6",
        "simulation-distance": "4",
        "max-tick-time": "-1",
        "enable-command-block": "false",
        "motd": "MyVillage combat capture",
        "player-idle-timeout": "0",
        "white-list": "false",
        "sync-chunk-writes": "true",
    }


def merge_options(path: Path, forced: dict) -> None:
    lines, seen = [], set()
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            key = line.split(":", 1)[0]
            if key in forced:
                line = f"{key}:{forced[key]}"
                seen.add(key)
            lines.append(line)
    for k, v in forced.items():
        if k not in seen:
            lines.append(f"{k}:{v}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def gradle_argv(task: str, jvmargs: str, extra=()) -> list[str]:
    # generateAllStructures rewrites tracked files under src/ and is not needed to run the game.
    return ["./gradlew", "--no-daemon", "--console=plain", f"-Dorg.gradle.jvmargs={jvmargs}",
            "-x", "generateAllStructures", task, *extra]


def client_extra(server_port: int, username: str) -> list[str]:
    return [f"-Pcombat_smoke_server=127.0.0.1:{server_port}", f"-Pcombat_smoke_game_dir={CLIENT_DIR_REL}",
            f"-Pcombat_smoke_username={username}"]


def now_iso() -> str:
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def read_state() -> dict | None:
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def write_state(state: dict) -> None:
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    tmp = STATE_FILE.with_suffix(".tmp")
    fd = os.open(str(tmp), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)
    os.replace(tmp, STATE_FILE)


def x_display_ready(display: str) -> bool:
    try:
        x = ctypes.cdll.LoadLibrary("libX11.so.6")
    except OSError:
        return Path(f"/tmp/.X11-unix/X{display.lstrip(':')}").exists()
    x.XOpenDisplay.restype = ctypes.c_void_p
    x.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x.XCloseDisplay.argtypes = [ctypes.c_void_p]
    d = x.XOpenDisplay(display.encode())
    if not d:
        return False
    x.XCloseDisplay(d)
    return True


def supervisor_alive(state: dict | None) -> bool:
    if not state or not state.get("supervisor"):
        return False
    s = state["supervisor"]
    return procs.is_alive(s["pid"], s.get("start"))


# ====================================================================== supervisor
@dataclass
class SessionConfig:
    session_id: str
    username: str = DEFAULT_USERNAME
    lock: str = ""
    lock_timeout: float = 5400.0
    server_timeout: float = 900.0
    client_timeout: float = 900.0
    ingame_timeout: float = 240.0
    settle: float = 10.0
    gradle_jvmargs: str = "-Xmx1g"


class StopRequested(Exception):
    pass


class Supervisor:
    def __init__(self, cfg: SessionConfig):
        self.cfg = cfg
        self.state = {"session_id": cfg.session_id, "phase": "waiting_lock", "error": None,
                      "created": now_iso(), "username": cfg.username, "lock": cfg.lock,
                      "supervisor": {"pid": os.getpid(), "start": procs.start_time(os.getpid())},
                      "procs": {}, "timings": {}}
        self.children: dict[str, subprocess.Popen] = {}
        self.lock_fd = None
        self.stopping = False

    def log(self, msg: str):
        print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)

    def set_phase(self, phase: str, **extra):
        self.state["phase"] = phase
        self.state.update(extra)
        write_state(self.state)
        self.log(f"phase {phase}")

    def _on_signal(self, signum, frame):
        if not self.stopping:
            self.stopping = True
            raise StopRequested(f"signal {signum}")

    def spawn(self, role: str, argv, env=None, cwd=REPO, keep_lock=True):
        LOG_DIR.mkdir(parents=True, exist_ok=True)
        out = open(LOG_DIR / f"{role}.log", "ab")
        out.write(f"\n===== {now_iso()} {' '.join(argv)}\n".encode())
        out.flush()
        p = subprocess.Popen(argv, cwd=cwd, env=env, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT,
                             pass_fds=(self.lock_fd,) if keep_lock and self.lock_fd is not None else ())
        out.close()
        self.children[role] = p
        self.state["procs"][role] = {"pid": p.pid, "start": procs.start_time(p.pid)}
        write_state(self.state)
        self.log(f"started {role} pid {p.pid}")
        return p

    def alive(self, role: str) -> bool:
        p = self.children.get(role)
        return p is not None and p.poll() is None

    def require_alive(self, role: str):
        if not self.alive(role):
            raise RuntimeError(f"{role} exited early (rc {self.children[role].returncode}); see {LOG_DIR / (role + '.log')}")

    def rcon(self):
        return rcon_connect(self.state["rcon_port"], self.state["rcon_password"], retries=1, timeout=5.0)

    # -------------------------------------------------------------- steps
    def take_lock(self):
        path = Path(self.cfg.lock)
        self.log(f"waiting for heavy-work lock {path}")
        t0 = time.time()
        self.lock_fd = procs.acquire_lock(path, self.cfg.lock_timeout,
                                          on_wait=lambda s: self.log(f"  still waiting for the lock ({s:.0f}s)"))
        self.state["timings"]["lock_wait_s"] = round(time.time() - t0, 1)
        self.log(f"lock acquired after {time.time() - t0:.0f}s")

    def start_xvfb(self):
        self.set_phase("starting_xvfb")
        n = procs.free_display()
        display = f":{n}"
        self.state["display"] = display
        self.spawn("xvfb", ["Xvfb", display, "-screen", "0", f"{SCREEN[0]}x{SCREEN[1]}x24", "+extension", "GLX",
                            "-nolisten", "tcp", "-noreset"], keep_lock=False)
        deadline = time.time() + 20
        while time.time() < deadline:
            self.require_alive("xvfb")
            if x_display_ready(display):
                self.log(f"Xvfb ready on {display}")
                return
            time.sleep(0.3)
        raise RuntimeError(f"Xvfb on {display} did not come up within 20 s")

    def write_server_config(self):
        SERVER_DIR.mkdir(parents=True, exist_ok=True)
        props = SERVER_DIR / "server.properties"
        # Keep the checkout's own acceptance config; restore() puts it back.
        if not PROPS_BACKUP.exists() and not PROPS_ABSENT_MARK.exists():
            if props.is_file():
                shutil.copy2(props, PROPS_BACKUP)
            else:
                PROPS_ABSENT_MARK.write_text("server.properties did not exist before the capture session\n")
        server_port, rcon_port = procs.free_ports(2)
        password = secrets.token_urlsafe(18)
        self.state.update(server_port=server_port, rcon_port=rcon_port, rcon_password=password)
        values = server_properties(server_port, rcon_port, password)
        fd = os.open(str(props), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write("# written by tools/combat_capture for a capture session; the original is restored at stop\n")
            f.write("".join(f"{k}={v}\n" for k, v in values.items()))
        eula = SERVER_DIR / "eula.txt"
        if not eula.is_file():
            eula.write_text("eula=true\n", encoding="utf-8")
        world = SERVER_DIR / LEVEL_NAME
        if world.exists():
            shutil.rmtree(world)
        write_state(self.state)

    def start_server(self):
        self.set_phase("starting_server")
        self.write_server_config()
        t0 = time.time()
        self.spawn("server", gradle_argv("runAcceptanceServer", self.cfg.gradle_jvmargs))
        deadline = t0 + self.cfg.server_timeout
        while time.time() < deadline:
            self.require_alive("server")
            try:
                with self.rcon() as r:
                    r.cmd("list")
                self.state["timings"]["server_ready_s"] = round(time.time() - t0, 1)
                self.log(f"server ready after {time.time() - t0:.0f}s")
                return
            except (OSError, RconError):
                time.sleep(2)
        raise RuntimeError(f"server not ready within {self.cfg.server_timeout:.0f}s; see {LOG_DIR / 'server.log'}")

    def start_client(self):
        self.set_phase("starting_client")
        merge_options(CLIENT_DIR / "options.txt", CLIENT_OPTIONS)
        env = dict(os.environ, DISPLAY=self.state["display"])
        self.state["client_log"] = str(CLIENT_DIR / "logs" / "latest.log")
        t0 = time.time()
        self.spawn("client", gradle_argv("runClient", self.cfg.gradle_jvmargs,
                                         client_extra(self.state["server_port"], self.cfg.username)), env=env)
        deadline = t0 + self.cfg.client_timeout
        joined = False
        while time.time() < deadline:
            self.require_alive("client")
            try:
                with self.rcon() as r:
                    if self.cfg.username in r.cmd("list"):
                        joined = True
                        break
            except (OSError, RconError):
                pass
            time.sleep(3)
        if not joined:
            raise RuntimeError(f"{self.cfg.username} did not join within {self.cfg.client_timeout:.0f}s; "
                               f"see {LOG_DIR / 'client.log'}")
        self.state["timings"]["client_join_s"] = round(time.time() - t0, 1)
        self.log(f"player joined after {time.time() - t0:.0f}s")
        game = Game(self.state["display"], Path(self.state["client_log"]), SCREEN)
        # poll slowly: each probe grabs the pointer for an instant and could collide with the
        # game's own grab when the loading screen closes; a middle click repairs a lost grab
        if not (game.wait_ingame(self.cfg.ingame_timeout, poll=1.0) or game.recover_grab()):
            raise RuntimeError("player joined but the client never reached in-game (pointer never grabbed)")
        time.sleep(self.cfg.settle)
        self.state["timings"]["client_ingame_s"] = round(time.time() - t0, 1)
        VIEW_FILE.write_text(json.dumps({"view": "first"}), encoding="utf-8")

    def run(self) -> int:
        signal.signal(signal.SIGTERM, self._on_signal)
        signal.signal(signal.SIGINT, self._on_signal)
        signal.signal(signal.SIGHUP, self._on_signal)
        procs.set_child_subreaper()
        RUN_DIR.mkdir(parents=True, exist_ok=True)
        write_state(self.state)
        rc = 0
        try:
            self.take_lock()
            t0 = time.time()
            self.start_xvfb()
            self.start_server()
            self.start_client()
            self.state["timings"]["startup_s"] = round(time.time() - t0, 1)
            self.set_phase("ready", ready_at=now_iso())
            while True:
                procs.reap()
                for role in ("xvfb", "server", "client"):
                    if not self.alive(role):
                        raise RuntimeError(f"{role} exited during the session (rc {self.children[role].returncode})")
                time.sleep(1.0)
        except StopRequested as e:
            self.log(f"stop requested ({e})")
        except BaseException as e:  # noqa: BLE001 - always clean up, then report
            rc = 1
            self.state["error"] = str(e)
            self.log(f"ERROR: {e}")
        finally:
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            signal.signal(signal.SIGINT, signal.SIG_IGN)
            self.cleanup()
        return rc

    def cleanup(self):
        self.state["phase"] = "stopping"
        write_state(self.state)
        self.log("cleanup")
        if self.alive("server") and self.state.get("rcon_password"):
            try:
                with self.rcon() as r:
                    r.cmd("stop")
                self.log("  rcon stop sent; waiting for the server to save and exit")
                deadline = time.time() + 60
                while time.time() < deadline and self.alive("server"):
                    time.sleep(1)
            except (OSError, RconError) as e:
                self.log(f"  rcon stop failed: {e}")
        stop_tree(self.state, self.log, own_pid=os.getpid())
        restore_server_properties(self.log)
        if self.lock_fd is not None:
            os.close(self.lock_fd)
            self.lock_fd = None
        self.state["phase"] = "failed" if self.state.get("error") else "stopped"
        self.state["stopped_at"] = now_iso()
        write_state(self.state)
        self.log(f"phase {self.state['phase']}")


def stop_tree(state: dict, log=print, own_pid: int | None = None) -> None:
    """Stop the client tree, then the server tree, then Xvfb, plus any other
    descendant of the supervisor (when we are the supervisor). Only PIDs we
    started or their descendants, each checked against its start time."""
    recorded = state.get("procs", {})
    table = procs.proc_table()
    order = ["client", "server", "xvfb"]
    for role in order:
        info = recorded.get(role)
        if not info:
            continue
        pid, start = info["pid"], info.get("start")
        if not procs.is_alive(pid, start):
            continue
        tree = [(d, procs.start_time(d), f"{role} child") for d in procs.descendants(pid, table)]
        procs.kill_pids(tree + [(pid, start, role)], log=log)
    if own_pid is not None:
        rest = [(d, procs.start_time(d), "leftover child") for d in procs.descendants(own_pid)]
        if rest:
            procs.kill_pids(rest, log=log)


def restore_server_properties(log=print) -> None:
    props = SERVER_DIR / "server.properties"
    if PROPS_BACKUP.exists():
        shutil.copy2(PROPS_BACKUP, props)
        PROPS_BACKUP.unlink()
        log("  restored the original run-acceptance/server.properties")
    elif PROPS_ABSENT_MARK.exists():
        if props.exists():
            props.unlink()
        PROPS_ABSENT_MARK.unlink()
        log("  removed the capture server.properties (there was none before)")


def supervise_main(argv: list[str]) -> int:
    cfg = SessionConfig(**json.loads(argv[0]))
    return Supervisor(cfg).run()


# ====================================================================== front end
def start(cfg: SessionConfig, wait_timeout: float = 7200.0, log=print) -> dict:
    """Launch the supervisor detached and wait until it is ready (or failed)."""
    old = read_state()
    if supervisor_alive(old):
        raise RuntimeError(f"a capture session is already running (phase {old.get('phase')}, supervisor pid "
                           f"{old['supervisor']['pid']}); run `session stop` first")
    if old and old.get("procs"):
        leftovers = [r for r, i in old["procs"].items() if procs.is_alive(i["pid"], i.get("start"))]
        if leftovers:
            raise RuntimeError(f"processes from a previous session are still alive ({leftovers}); "
                               f"run `session stop` first")
    restore_server_properties(log)  # a crashed earlier session may have left its config behind
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    if STATE_FILE.exists():
        STATE_FILE.unlink()
    out = open(LOG_DIR / "supervisor.log", "ab")
    p = subprocess.Popen([sys.executable, "-m", "tools.combat_capture", "_supervise", json.dumps(asdict(cfg))],
                         cwd=REPO, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT,
                         start_new_session=True)
    out.close()
    log(f"supervisor pid {p.pid}; log {LOG_DIR / 'supervisor.log'}")
    t0 = time.time()
    last_phase = None
    try:
        while True:
            st = read_state()
            if st and st.get("session_id") == cfg.session_id:
                if st["phase"] != last_phase:
                    last_phase = st["phase"]
                    log(f"  [{time.time() - t0:5.0f}s] {last_phase}")
                if st["phase"] == "ready":
                    return st
                if st["phase"] in ("failed", "stopped"):
                    raise RuntimeError(f"session {st['phase']}: {st.get('error')}; see {LOG_DIR / 'supervisor.log'}")
            if p.poll() is not None:
                p.wait()
                st = read_state() or {}
                raise RuntimeError(f"supervisor exited (rc {p.returncode}): {st.get('error')}; "
                                   f"see {LOG_DIR / 'supervisor.log'}")
            if time.time() - t0 > wait_timeout:
                raise TimeoutError(f"session not ready after {wait_timeout:.0f}s")
            time.sleep(2)
    except BaseException:
        stop(log=log)
        raise


def stop(timeout: float = 180.0, log=print) -> dict | None:
    st = read_state()
    if not st:
        log("no capture session state; nothing to stop")
        return None
    if supervisor_alive(st):
        pid = st["supervisor"]["pid"]
        log(f"asking supervisor {pid} to stop")
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        deadline = time.time() + timeout
        while time.time() < deadline and supervisor_alive(st):
            time.sleep(1)
        if supervisor_alive(st):
            log(f"supervisor {pid} did not exit within {timeout:.0f}s; stopping recorded processes directly")
            stop_tree(st, log)
            procs.kill_pids([(pid, st["supervisor"].get("start"), "supervisor")], log=log)
    else:
        stop_tree(st, log)
        restore_server_properties(log)
    st = read_state() or st
    alive = [r for r, i in st.get("procs", {}).items() if procs.is_alive(i["pid"], i.get("start"))]
    if alive:
        log(f"WARNING: still alive: {alive}")
    if st.get("phase") in PHASES_ACTIVE:
        st["phase"] = "stopped"
        write_state(st)
    log(f"session {st.get('phase')}; lock {'free' if procs.lock_is_free(Path(st.get('lock') or '/nonexistent')) else 'busy (held by another process now)'}")
    return st


def status(log=print) -> dict | None:
    st = read_state()
    if not st:
        log("no capture session")
        return None
    log(f"phase      {st.get('phase')}  (session {st.get('session_id')})")
    log(f"supervisor {st['supervisor']['pid']} {'alive' if supervisor_alive(st) else 'gone'}")
    for role, info in st.get("procs", {}).items():
        log(f"  {role:<7} pid {info['pid']:<8} {'alive' if procs.is_alive(info['pid'], info.get('start')) else 'gone'}")
    for k in ("display", "server_port", "rcon_port", "username", "client_log", "lock", "error"):
        if st.get(k) is not None:
            log(f"{k:<10} {st[k]}")
    if st.get("timings"):
        log(f"timings    {st['timings']}")
    return st


class Session:
    """Handle on a ready session for the capture commands."""

    def __init__(self, state: dict, ui_check: bool = True, log=print):
        self.state = state
        self.log = log
        self.username = state["username"]
        self.game = Game(state["display"], Path(state["client_log"]), SCREEN, ui_check=ui_check, log=log)

    @classmethod
    def attach(cls, ui_check: bool = True, log=print) -> "Session":
        st = read_state()
        if not st or st.get("phase") != "ready" or not supervisor_alive(st):
            raise RuntimeError("no ready capture session; run `python3 -m tools.combat_capture session start`")
        return cls(st, ui_check=ui_check, log=log)

    def rcon(self, retries: int = 3):
        return rcon_connect(self.state["rcon_port"], self.state["rcon_password"], retries=retries)

    def run(self, *cmds: str, warn: bool = True) -> list[str]:
        outs = []
        with self.rcon() as r:
            for c in cmds:
                o = r.cmd(c)
                outs.append(o)
                if warn and any(w in o.lower() for w in ("unknown", "incorrect", "expected", "error", "invalid")):
                    self.log(f"  rcon WARN > {c}\n    {o.strip()[:200]}")
        return outs

    @property
    def view(self) -> str:
        try:
            return json.loads(VIEW_FILE.read_text(encoding="utf-8"))["view"]
        except (OSError, ValueError, KeyError):
            return "first"

    @view.setter
    def view(self, value: str):
        VIEW_FILE.write_text(json.dumps({"view": value}), encoding="utf-8")

    def assert_alive(self):
        if not supervisor_alive(read_state()):
            raise RuntimeError("the capture session ended underneath us")
