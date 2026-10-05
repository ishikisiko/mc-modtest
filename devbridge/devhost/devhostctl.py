#!/usr/bin/env python3
"""Drive DevHost (launcher side) and DevBridge (in game) from the agent's machine.

    devhostctl.py status
    devhostctl.py deploy build/libs/mymod-1.0.jar        # stop, install, launch, wait in world
    devhostctl.py launch --world dev1 | --title
    devhostctl.py stop | install JAR... | remove NAME | wait | logs [NAME] | crash | shot OUT.png
    devhostctl.py worlds                                 # saves: owner's and bridge-owned test worlds
    devhostctl.py world ensure t1 [--preset flat ...]    # start the game if needed, create t1 if missing, open it
    devhostctl.py world create|open|leave|save|snapshot|restore|reset|delete|snapshots|snapshot-delete|status ...

World commands wait until the operation has finished and exit 1 with its error if it failed.
Restore, reset and delete only work on worlds DevBridge created (devbridge-world.json).

Settings come from the environment or an env file (default ~/.config/devbridge/devhost.env,
override with --env): DEVHOST_URL, DEVHOST_TOKEN, DEVBRIDGE_URL, DEVBRIDGE_TOKEN.
Standard library only.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_ENV = Path.home() / ".config" / "devbridge" / "devhost.env"
# Screens shown while a world loads; any other screen (e.g. the inventory) means the world is up.
LOADING_SCREENS = ("LevelLoadingScreen", "ReceivingLevelScreen", "GenericMessageScreen", "ProgressScreen",
                   "ConnectScreen", "GenericWaitingScreen")


def load_env(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())


def call(base_env: str, method: str, path: str, params: dict | None = None, body: bytes | None = None,
         timeout: float = 30) -> dict:
    base = os.environ.get(f"{base_env}_URL", "").rstrip("/")
    token = os.environ.get(f"{base_env}_TOKEN", "")
    if not base:
        raise SystemExit(f"{base_env}_URL is not set")
    url = base + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    req = urllib.request.Request(url, data=body, method=method, headers={"Authorization": f"Bearer {token}"})
    if body is not None:
        req.add_header("Content-Type", "application/octet-stream")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except ValueError:
            return {"ok": False, "error": f"HTTP {e.code}"}
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        return {"ok": False, "error": f"unreachable: {e}"}


def host(method: str, path: str, **kw) -> dict:
    return call("DEVHOST", method, path, **kw)


def bridge(method: str, path: str, params: dict | None = None, timeout: float = 15) -> dict:
    if method == "POST":
        return call("DEVBRIDGE", "POST", path, body=json.dumps(params or {}).encode(), timeout=timeout)
    return call("DEVBRIDGE", "GET", path, params=params, timeout=timeout)


def must(res: dict, what: str):
    if not res.get("ok"):
        raise SystemExit(f"{what} failed: {res.get('error')}")
    return res["result"]


def show(obj) -> None:
    print(json.dumps(obj, ensure_ascii=False, indent=2))


# ---------------------------------------------------------------- commands

def cmd_status(a) -> None:
    show(must(host("GET", "/status"), "status"))


def cmd_stop(a) -> None:
    show(must(host("POST", "/stop", params={"timeoutSec": a.timeout}, timeout=a.timeout + 30), "stop"))


def install(jar: Path) -> dict:
    data = jar.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    return must(host("POST", "/mods/install", params={"name": jar.name, "sha256": sha}, body=data, timeout=120),
                f"install {jar.name}")


def cmd_install(a) -> None:
    for jar in a.jars:
        show(install(Path(jar)))


def cmd_remove(a) -> None:
    show(must(host("POST", "/mods/remove", params={"name": a.name}), "remove"))


def launch_params(a) -> dict:
    if a.title:
        return {"world": ""}
    return {"world": a.world} if a.world else {}


def cmd_launch(a) -> None:
    show(must(host("POST", "/launch", params=launch_params(a)), "launch"))


def wait_in_world(world: str | None, timeout: float) -> dict:
    """Wait for DevBridge, then for the world. Fails fast when the game process is gone."""
    start = time.monotonic()
    seen_running = False
    last = ""
    while time.monotonic() - start < timeout:
        st = host("GET", "/status")
        running = st.get("ok") and st["result"]["running"]
        seen_running = seen_running or running
        if seen_running and not running:
            crash = host("GET", "/crash").get("result") or {}
            tail = host("GET", "/log", params={"name": "latest.log", "lines": 40}).get("result") or {}
            print("game exited before reaching the world", file=sys.stderr)
            if crash.get("name"):
                print(f"newest crash report: {crash['name']}\n{crash.get('text', '')[:3000]}", file=sys.stderr)
            print("\n".join(tail.get("lines", [])), file=sys.stderr)
            raise SystemExit(1)
        w = bridge("GET", "/client/world", timeout=5)
        if w.get("ok"):
            r = w["result"]
            state = bridge("GET", "/client/state", timeout=5).get("result") or {}
            now = f"bridge up, inWorld={r['inWorld']} folder={r.get('folder')} screen={state.get('screen')}"
            screen = state.get("screen") or ""
            loading = any(screen.endswith(name) for name in LOADING_SCREENS)
            if r["inWorld"] and not loading and (world is None or r.get("folder") == world):
                print(f"[{time.monotonic() - start:5.0f}s] in world {r.get('folder')}" + (f" (screen {screen.rsplit('.', 1)[-1]})" if screen else ""))
                return r
            if world == "" and screen.endswith("TitleScreen"):
                print(f"[{time.monotonic() - start:5.0f}s] at the title screen")
                return r
        else:
            now = "game running, bridge not up yet" if running else "waiting for the game process"
        if now != last:
            print(f"[{time.monotonic() - start:5.0f}s] {now}")
            last = now
        time.sleep(3)
    raise SystemExit(f"timed out after {timeout:.0f}s ({last})")


def cmd_wait(a) -> None:
    wait_in_world(a.world, a.timeout)


def cmd_deploy(a) -> None:
    jars = [Path(j) for j in a.jars]
    for j in jars:
        if not j.is_file():
            raise SystemExit(f"no such jar: {j}")
    st = must(host("GET", "/status"), "status")
    if st["running"]:
        r = must(host("POST", "/stop", params={"timeoutSec": a.stop_timeout}, timeout=a.stop_timeout + 30), "stop")
        print(f"stopped (world={r.get('world')}, forced={r.get('forced')}, {r.get('seconds')}s)")
    for j in jars:
        r = install(j)
        print(f"installed {r['installed']} (modIds {', '.join(r['modIds'])}; replaced {', '.join(r['replaced']) or 'nothing'})")
    r = must(host("POST", "/launch", params=launch_params(a)), "launch")
    print(f"launched, world={r.get('world')!r}")
    if not a.no_wait:
        target = "" if a.title else (a.world or r.get("world"))
        wait_in_world(target, a.timeout)


def cmd_logs(a) -> None:
    r = must(host("GET", "/log", params={"name": a.name, "lines": a.lines}), "log")
    print("\n".join(r["lines"]))


def cmd_crash(a) -> None:
    r = must(host("GET", "/crash", params={"name": a.name}), "crash")
    print("reports:", ", ".join(r["reports"]) or "none")
    if r.get("text"):
        print(f"\n== {r['name']}\n{r['text']}")


# ---------------------------------------------------------------- worlds

def bridge_up() -> bool:
    return bool(bridge("GET", "/ping", timeout=5).get("ok"))


def wait_op(op: dict, timeout: float) -> dict:
    """Poll a world operation until it finishes; print its phases; exit 1 with its error if it failed."""
    start = time.monotonic()
    last, down_since = "", None
    label = f"{op['name']} {op.get('folder') or ''}".strip()
    while time.monotonic() - start < timeout:
        r = bridge("GET", "/client/world/operation", {"id": op["id"]}, timeout=10)
        if r.get("ok"):
            down_since = None
            o = r["result"]
            if o["state"] == "failed":
                raise SystemExit(f"{label} failed: {o.get('error')}")
            if o["state"] == "done":
                print(f"[{time.monotonic() - start:5.0f}s] {label} done ({o.get('millis', 0) / 1000:.1f}s)")
                return o
            if o["state"] != "running":
                raise SystemExit(f"{label}: operation {op['id']} is {o['state']} (did the game restart?)")
            now = f"{label}: {o.get('phase')}"
        else:
            down_since = down_since or time.monotonic()
            now = f"{label}: bridge not answering ({r.get('error')})"
            if time.monotonic() - down_since > 90:
                raise SystemExit(f"DevBridge stopped answering during {label}; did the game exit? (devhostctl.py logs / crash)")
        if now != last:
            print(f"[{time.monotonic() - start:5.0f}s] {now}")
            last = now
        time.sleep(1)
    raise SystemExit(f"timed out after {timeout:.0f}s waiting for {label} (operation {op['id']} may still be running: world status)")


def world_op(path: str, params: dict, timeout: float) -> dict:
    """Start a world operation and wait for it; returns the finished operation."""
    r = must(bridge("POST", path, params, timeout=60), path.rsplit("/", 1)[-1])
    return wait_op(r["operation"], timeout)


def settle(timeout: float) -> dict:
    """Wait until DevBridge answers, the game is at the title screen or in a world, and no world operation runs."""
    start = time.monotonic()
    last, seen_running = "", False
    while time.monotonic() - start < timeout:
        w = bridge("GET", "/client/world", timeout=10)
        if w.get("ok"):
            r = w["result"]
            st = bridge("GET", "/client/state", timeout=10).get("result") or {}
            screen = st.get("screen") or ""
            loading = any(screen.endswith(n) for n in LOADING_SCREENS) or st.get("overlay")
            if r.get("busy"):
                now = f"waiting for {r['busy']} {r['current'].get('folder') or ''} ({r['current'].get('phase')})"
            elif loading or (not r.get("inWorld") and not screen.endswith("TitleScreen")):
                now = f"game loading (screen {screen.rsplit('.', 1)[-1] or 'none'})"
            else:
                return r
        else:
            now = "waiting for DevBridge"
            if os.environ.get("DEVHOST_URL"):
                hs = host("GET", "/status")
                running = bool(hs.get("ok") and hs["result"]["running"])
                if seen_running and not running:
                    raise SystemExit("the game exited before DevBridge was ready (devhostctl.py logs / crash)")
                seen_running = seen_running or running
        if now != last:
            print(f"[{time.monotonic() - start:5.0f}s] {now}")
            last = now
        time.sleep(2)
    raise SystemExit(f"timed out after {timeout:.0f}s ({last})")


def create_params(a, open_world: bool = True) -> dict:
    p = {"folder": a.folder, "preset": a.preset, "gameMode": a.game_mode, "cheats": not a.no_cheats, "open": open_world}
    for key, value in (("name", a.name), ("difficulty", a.difficulty), ("seed", a.seed), ("time", a.time), ("structures", a.structures)):
        if value is not None:
            p[key] = value
    if a.hardcore:
        p["hardcore"] = True
    if a.rule:
        rules = {}
        for r in a.rule:
            if "=" not in r:
                raise SystemExit(f"--rule takes NAME=VALUE, not {r!r}")
            k, v = r.split("=", 1)
            rules[k.strip()] = v.strip()
        p["gameRules"] = rules
    return p


def reopen_param(a, p: dict) -> dict:
    if a.reopen is not None:
        p["reopen"] = a.reopen
    return p


def cmd_worlds(a) -> None:
    r = must(bridge("GET", "/client/worlds"), "worlds")
    if a.json:
        show(r)
        return
    print(f"saves: {r['savesDir']}\nsnapshots: {r['snapshotsDir']}  trash: {r['trashDir']}")
    print(f"{'folder':24} {'name':20} {'mode':9} {'cheats':6} {'bridge':6} {'open':4}  snapshots")
    for w in r["worlds"]:
        snaps = ", ".join(s["name"] for s in w.get("snapshots", []))
        print(f"{w['folder']:24} {str(w.get('name', '?'))[:20]:20} {str(w.get('gameMode', '?')):9} {str(w.get('cheats', '?')):6} "
              f"{'yes' if w.get('bridgeOwned') else 'no':6} {'yes' if w.get('open') else '':4}  {snaps}")


def cmd_world(a) -> None:
    t = a.timeout
    if a.action == "status":
        show(must(bridge("GET", "/client/world"), "world"))
    elif a.action == "snapshots":
        show(must(bridge("GET", "/client/world/snapshots", {"folder": a.folder}), "snapshots"))
    elif a.action == "create":
        show(world_op("/client/world/create", create_params(a, not a.no_open), t))
    elif a.action == "open":
        show(world_op("/client/world/open", {"folder": a.folder}, t))
    elif a.action == "leave":
        world_op("/client/world/leave", {}, t)
    elif a.action == "save":
        show(world_op("/client/world/save", {}, t))
    elif a.action == "snapshot":
        show(world_op("/client/world/snapshot", reopen_param(a, {"folder": a.folder, "name": a.name, "overwrite": a.overwrite}), t))
    elif a.action == "restore":
        show(world_op("/client/world/restore", reopen_param(a, {"folder": a.folder, "name": a.name}), t))
    elif a.action == "reset":
        show(world_op("/client/world/reset", reopen_param(a, {"folder": a.folder}), t))
    elif a.action == "delete":
        show(world_op("/client/world/delete", {"folder": a.folder}, t))
    elif a.action == "snapshot-delete":
        show(world_op("/client/world/snapshot/delete", {"folder": a.folder, "name": a.name}, t))
    elif a.action == "ensure":
        world_ensure(a)


def world_ensure(a) -> None:
    """Make sure world a.folder exists and is open: start the game if needed, create the world if missing."""
    if not bridge_up():
        if not os.environ.get("DEVHOST_URL"):
            raise SystemExit("DevBridge is not answering and DEVHOST_URL is not set, so the game cannot be started from here")
        st = must(host("GET", "/status"), "status")
        if st["running"]:
            print("the game is running but DevBridge is not answering yet; waiting")
        else:
            r = host("POST", "/launch", params={"world": a.folder})
            if not r.get("ok") and "no world folder" in str(r.get("error")):
                print(f"{a.folder} does not exist yet: launching to the title screen")
                r = host("POST", "/launch", params={"world": ""})
            r = must(r, "launch")
            print(f"launched, world={r.get('world')!r}")
    w = settle(a.timeout)
    if w.get("inWorld") and w.get("singleplayer") and w.get("folder") == a.folder:
        print(f"{a.folder} is open")
        return
    worlds = must(bridge("GET", "/client/worlds"), "worlds")["worlds"]
    if any(x["folder"] == a.folder for x in worlds):
        world_op("/client/world/open", {"folder": a.folder}, a.timeout)
    else:
        print(f"{a.folder} does not exist: creating it")
        show(world_op("/client/world/create", create_params(a), a.timeout)["result"])
    print(f"{a.folder} is open")


def cmd_shot(a) -> None:
    r = must(bridge("GET", "/client/screenshot", {"maxWidth": a.max_width}, timeout=60), "screenshot")
    Path(a.out).write_bytes(base64.b64decode(r["base64"]))
    print(a.out)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--env", type=Path, default=DEFAULT_ENV, help=f"env file (default {DEFAULT_ENV})")
    sub = p.add_subparsers(dest="cmd", required=True)

    def world_opts(sp):
        g = sp.add_mutually_exclusive_group()
        g.add_argument("--world", help="saves/ folder to open (default: the world open at the last stop)")
        g.add_argument("--title", action="store_true", help="stay at the title screen")

    sub.add_parser("status").set_defaults(fn=cmd_status)
    sp = sub.add_parser("stop"); sp.add_argument("--timeout", type=int, default=60); sp.set_defaults(fn=cmd_stop)
    sp = sub.add_parser("install"); sp.add_argument("jars", nargs="+"); sp.set_defaults(fn=cmd_install)
    sp = sub.add_parser("remove"); sp.add_argument("name"); sp.set_defaults(fn=cmd_remove)
    sp = sub.add_parser("launch"); world_opts(sp); sp.set_defaults(fn=cmd_launch)
    sp = sub.add_parser("wait"); sp.add_argument("--world"); sp.add_argument("--timeout", type=float, default=300)
    sp.set_defaults(fn=cmd_wait)
    sp = sub.add_parser("deploy", help="stop, install the jars, launch and wait until in the world")
    sp.add_argument("jars", nargs="+"); world_opts(sp)
    sp.add_argument("--stop-timeout", type=int, default=60)
    sp.add_argument("--timeout", type=float, default=300, help="seconds to wait for the world")
    sp.add_argument("--no-wait", action="store_true"); sp.set_defaults(fn=cmd_deploy)
    sp = sub.add_parser("logs"); sp.add_argument("name", nargs="?", default="latest.log")
    sp.add_argument("--lines", type=int, default=100); sp.set_defaults(fn=cmd_logs)
    sp = sub.add_parser("crash"); sp.add_argument("name", nargs="?"); sp.set_defaults(fn=cmd_crash)
    sp = sub.add_parser("shot"); sp.add_argument("out"); sp.add_argument("--max-width", type=int, default=1600)
    sp.set_defaults(fn=cmd_shot)
    sp = sub.add_parser("worlds", help="list saves (bridge-owned or not, snapshots)"); sp.add_argument("--json", action="store_true")
    sp.set_defaults(fn=cmd_worlds)

    def create_opts(sp):
        sp.add_argument("--name", help="level name (default: the folder)")
        sp.add_argument("--preset", choices=("flat", "default", "void"), default="flat")
        sp.add_argument("--game-mode", choices=("creative", "survival", "adventure", "spectator"), default="creative")
        sp.add_argument("--difficulty", choices=("peaceful", "easy", "normal", "hard"))
        sp.add_argument("--no-cheats", action="store_true")
        sp.add_argument("--seed")
        sp.add_argument("--hardcore", action="store_true")
        g = sp.add_mutually_exclusive_group()
        g.add_argument("--structures", dest="structures", action="store_const", const=True)
        g.add_argument("--no-structures", dest="structures", action="store_const", const=False)
        sp.add_argument("--rule", action="append", metavar="NAME=VALUE", help="game rule, e.g. doDaylightCycle=false (repeatable)")
        sp.add_argument("--time", type=int, help="day time in ticks after creation (6000 = noon)")

    def reopen_opts(sp):
        g = sp.add_mutually_exclusive_group()
        g.add_argument("--reopen", dest="reopen", action="store_const", const=True, help="open the world afterwards")
        g.add_argument("--no-reopen", dest="reopen", action="store_const", const=False, help="stay closed afterwards")

    wp = sub.add_parser("world", help="test worlds: create, open, save, snapshot, restore, reset, delete")
    wp.add_argument("--timeout", type=float, default=600, help="seconds to wait for the operation")
    wsub = wp.add_subparsers(dest="action", required=True)
    wsub.add_parser("status")
    sp = wsub.add_parser("snapshots"); sp.add_argument("folder", nargs="?")
    sp = wsub.add_parser("create"); sp.add_argument("folder"); create_opts(sp)
    sp.add_argument("--no-open", action="store_true", help="go back to the world open before (or the title screen)")
    sp = wsub.add_parser("open"); sp.add_argument("folder")
    wsub.add_parser("leave")
    wsub.add_parser("save")
    sp = wsub.add_parser("snapshot"); sp.add_argument("folder"); sp.add_argument("name"); sp.add_argument("--overwrite", action="store_true")
    reopen_opts(sp)
    sp = wsub.add_parser("restore"); sp.add_argument("folder"); sp.add_argument("name"); reopen_opts(sp)
    sp = wsub.add_parser("reset"); sp.add_argument("folder"); reopen_opts(sp)
    sp = wsub.add_parser("delete"); sp.add_argument("folder")
    sp = wsub.add_parser("snapshot-delete"); sp.add_argument("folder"); sp.add_argument("name")
    sp = wsub.add_parser("ensure", help="start the game if needed, create the world if missing, open it"); sp.add_argument("folder")
    create_opts(sp)
    wp.set_defaults(fn=cmd_world)

    a = p.parse_args()
    load_env(a.env)
    a.fn(a)


if __name__ == "__main__":
    main()
