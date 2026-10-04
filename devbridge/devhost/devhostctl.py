#!/usr/bin/env python3
"""Drive DevHost (launcher side) and DevBridge (in game) from the agent's machine.

    devhostctl.py status
    devhostctl.py deploy build/libs/mymod-1.0.jar        # stop, install, launch, wait in world
    devhostctl.py launch --world dev1 | --title
    devhostctl.py stop | install JAR... | remove NAME | wait | logs [NAME] | crash | shot OUT.png

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
            if r["inWorld"] and state.get("screen") is None and (world is None or r.get("folder") == world):
                print(f"[{time.monotonic() - start:5.0f}s] in world {r.get('folder')}")
                return r
            if world == "" and state.get("screen", "").endswith("TitleScreen"):
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

    a = p.parse_args()
    load_env(a.env)
    a.fn(a)


if __name__ == "__main__":
    main()
