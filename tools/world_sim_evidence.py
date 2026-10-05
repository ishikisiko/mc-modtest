#!/usr/bin/env python3
"""Headless in-game evidence for the world ledger (world sim P1/P2), rerunnable.

Two parts, each holding the shared heavy-work lock while Minecraft runs (never wrap this script in flock):

``session`` (about 6-8 min): Xvfb + the acceptance server + one client (``tools/combat_capture`` session,
fresh superflat world). The calendar is sped up for the run: NeoForge 21.1 reads server configs from the
instance's ``config/`` (a world's ``serverconfig/`` only overrides it, and the session deletes its world at
start), so ``ticks_per_day`` in ``run-acceptance/config/myvillage-server.toml`` is set (default 200 = 10 s
per calendar day) for the session and the file is restored byte for byte afterwards. The player is
switched to creative, then
every ``/myvillage world`` command runs over RCON; the calendar and the ledger are shown advancing in
creative; ``pause`` holds the ledger while at least two calendar days pass; ``resume`` restarts it without
catching up; ``advance 60`` with the player online produces rumors, whose chat lines are read from the
client log (plus one screenshot of the chat); ``advance 600`` then floods the queue to show the per-minute
rate limit.

``restart`` (about 3-4 min): the acceptance server alone (no client, so the calendar stands still) on a
world kept across two starts (``world_sim_evidence_world``, wiped once at the beginning): ``info``,
``advance 30``, ``pause``, ``info``, ``chronicle 5``, stop; a per-world override
``<world>/serverconfig/myvillage-world_sim-server.toml`` with ``tier = "medium"`` is then added (it must be
ignored: the tier is fixed at genesis); start again; ``info`` and
``chronicle 5`` must equal the replies before the stop, and the log must say "loaded", not "genesis".

Output in ``out/preview/world_sim/evidence/`` (publicly served: no paths, ports or passwords):
``commands.txt``, ``rumors.txt``, ``rumor_chat.png``, ``server_log_session.txt``, ``restart_run1.txt``,
``restart_run2.txt``, ``server_log_restart.txt``, ``evidence.json``, ``summary.md``.

    python3 tools/world_sim_evidence.py [--part session|restart|all] [--ticks-per-day N]
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Callable

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

from tools.combat_capture import procs  # noqa: E402
from tools.combat_capture.rcon import RconError, connect as rcon_connect  # noqa: E402

OUT = REPO / "out" / "preview" / "world_sim" / "evidence"
SERVER_DIR = REPO / "run-acceptance"
CULTIVATION_CONFIG = SERVER_DIR / "config" / "myvillage-server.toml"  # instance config read by every world
WORLD_SIM_CONFIG = "myvillage-world_sim-server.toml"
WORK_DIR = SERVER_DIR / "world-sim-evidence"  # inside the ignored run-acceptance/
PROPS_BACKUP = WORK_DIR / "server.properties.orig"
PROPS_ABSENT = WORK_DIR / "server.properties.absent"
RESTART_WORLD = "world_sim_evidence_world"
PLAYER = "CaptureDev"
W = "myvillage world"

SIM_DAY = re.compile(r"\(sim day (\d+)\)")
SETTLEMENT = re.compile(r"Cultivation calendar day (\d+); (\d+) day\(s\) pending; settlement (\w+)")


def now() -> str:
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def log(msg: str) -> None:
    print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


class Transcript:
    """Raw RCON replies with wall-clock timestamps."""

    def __init__(self, path: Path, title: str):
        self.path = path
        self.lines: list[str] = [f"# {title}", f"# started {now()}", ""]
        self.records: list[dict[str, Any]] = []

    def note(self, text: str) -> None:
        self.lines.append(f"[{now()}] # {text}")
        self.flush()

    def add(self, command: str, reply: str) -> None:
        stamp = now()
        self.records.append({"time": stamp, "command": command, "reply": reply})
        self.lines.append(f"[{stamp}] > {command}")
        body = reply.rstrip("\n")
        self.lines.extend(body.split("\n") if body else ["(no output)"])
        self.lines.append("")
        self.flush()

    def flush(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text("\n".join(self.lines) + "\n", encoding="utf-8")


def parse_info(reply: str) -> dict[str, Any]:
    sim = SIM_DAY.search(reply)
    st = SETTLEMENT.search(reply)
    if not sim or not st:
        raise RuntimeError(f"unexpected info reply: {reply[:300]!r}")
    return {"sim_day": int(sim.group(1)), "calendar_day": int(st.group(1)), "pending": int(st.group(2)),
            "settlement": st.group(3)}


CHAT_TIME = re.compile(r"^\[\d{2}\w{3}\d{4} (\d{2}):(\d{2}):(\d{2})\.(\d{3})\]")


def chat_time(line: str) -> float | None:
    m = CHAT_TIME.match(line)
    if not m:
        return None
    h, mi, sec, ms = (int(x) for x in m.groups())
    return h * 3600 + mi * 60 + sec + ms / 1000


def check(results: list[dict[str, Any]], name: str, ok: bool, detail: str) -> bool:
    results.append({"check": name, "pass": bool(ok), "detail": detail})
    log(f"  {'PASS' if ok else 'FAIL'} {name}: {detail}")
    return ok


def require_lock_free() -> Path:
    lock = procs.default_lock_path(REPO)
    if not procs.lock_is_free(lock):
        raise SystemExit(f"the heavy-work lock {lock.name} is held by another job; run again when it is free")
    return lock


# ================================================================================ part A: session
def part_session(ticks_per_day: int, results: list[dict[str, Any]]) -> dict[str, Any]:
    from tools.combat_capture import session as cs

    lock = require_lock_free()
    original = CULTIVATION_CONFIG.read_bytes() if CULTIVATION_CONFIG.exists() else None
    CULTIVATION_CONFIG.parent.mkdir(parents=True, exist_ok=True)
    text = original.decode("utf-8") if original is not None else "[cultivation_time]\n\tticks_per_day = 24000\n"
    if not re.search(r"ticks_per_day = \d+", text):
        raise SystemExit(f"{CULTIVATION_CONFIG.relative_to(REPO)} has no ticks_per_day line")
    CULTIVATION_CONFIG.write_text(re.sub(r"ticks_per_day = \d+", f"ticks_per_day = {ticks_per_day}", text),
                                  encoding="utf-8")
    log(f"{CULTIVATION_CONFIG.relative_to(REPO)}: ticks_per_day = {ticks_per_day} for this session")
    t = Transcript(OUT / "commands.txt", f"/myvillage world session transcript (creative, ticks_per_day={ticks_per_day})")
    facts: dict[str, Any] = {"ticks_per_day": ticks_per_day}
    try:
        log("starting the capture session (Xvfb + acceptance server + client); it takes the heavy lock")
        st = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), lock=str(lock)), log=log)
        facts["session_timings"] = st.get("timings")
        s = cs.Session.attach(ui_check=True, log=log)
        client_log = Path(st["client_log"])

        def rc(cmd: str) -> str:
            reply = s.run(cmd, warn=False)[0]
            t.add(cmd, reply)
            return reply

        def quiet_info() -> dict[str, Any]:
            return parse_info(s.run(f"{W} info", warn=False)[0])

        def wait_calendar(target: int, timeout: float = 120.0) -> None:
            deadline = time.time() + timeout
            while time.time() < deadline:
                if quiet_info()["calendar_day"] >= target:
                    return
                time.sleep(2)
            raise RuntimeError(f"calendar day did not reach {target} within {timeout:.0f}s")

        t.note("setup: creative mode")
        rc(f"gamemode creative {PLAYER}")
        mode = rc(f"data get entity {PLAYER} playerGameType")
        check(results, "player_in_creative", mode.rstrip().endswith(" 1"), mode.strip())

        t.note("every /myvillage world command")
        first = rc(W)
        check(results, "world_ledger_active", "World ledger:" in first, first.split("\n")[0])
        rc(f"{W} info")
        sects = rc(f"{W} sects")
        rc(f"{W} sects all")
        sect1 = rc(f"{W} sect 1")
        m = re.search(r"^#\d+ (\S+) \|", sects, re.MULTILINE)
        if m:
            rc(f"{W} sect {m.group(1)}")
        master = re.search(r"^Master: (\S+)", sect1, re.MULTILINE)
        if master and master.group(1) != "none":
            rc(f"{W} person {master.group(1)}")
            rc(f"{W} person {master.group(1)[0]}")
        rc(f"{W} chronicle")
        rc(f"{W} chronicle 5")
        here = rc(f"execute as {PLAYER} at {PLAYER} run {W} here")
        check(results, "here_reports_region", "cultivators here" in here, here.split("\n")[0])
        t.note("error cases")
        rc(f"{W} sect 不存在之宗")
        rc(f"{W} person 无名氏")
        rc(f"{W} here")
        bad = rc(f"{W} advance 0")
        bad2 = rc(f"{W} advance 3651")
        check(results, "advance_bounds", "<--[HERE]" in bad and "<--[HERE]" in bad2,
              f"{bad.splitlines()[0]} / {bad2.splitlines()[0]}")

        t.note("running in creative: the calendar and the ledger both advance")
        a = parse_info(rc(f"{W} info"))
        wait_calendar(a["calendar_day"] + 2)
        b = parse_info(rc(f"{W} info"))
        dc, ds = b["calendar_day"] - a["calendar_day"], b["sim_day"] - a["sim_day"]
        check(results, "creative_calendar_and_ledger_advance", dc >= 2 and ds >= 2 and abs(ds - dc) <= 1,
              f"calendar +{dc}, sim +{ds}")

        t.note("pause: the calendar keeps running, the ledger stops")
        rc(f"{W} pause")
        rc(f"{W} pause")
        c = parse_info(rc(f"{W} info"))
        wait_calendar(c["calendar_day"] + 3)
        d = parse_info(rc(f"{W} info"))
        dc = d["calendar_day"] - c["calendar_day"]
        check(results, "paused_ledger_holds", d["sim_day"] == c["sim_day"] and dc >= 2 and d["pending"] == 0
              and d["settlement"] == "paused", f"calendar +{dc}, sim {c['sim_day']} -> {d['sim_day']}, "
                                               f"pending {d['pending']}")

        t.note("resume: no catch-up for the paused days, then one sim day per calendar day")
        rc(f"{W} resume")
        e = parse_info(rc(f"{W} info"))
        check(results, "resume_does_not_catch_up", e["sim_day"] == d["sim_day"] and e["pending"] == 0,
              f"sim {d['sim_day']} -> {e['sim_day']} right after resume, pending {e['pending']}")
        wait_calendar(e["calendar_day"] + 2)
        f = parse_info(rc(f"{W} info"))
        dc, ds = f["calendar_day"] - e["calendar_day"], f["sim_day"] - e["sim_day"]
        paused_span = d["calendar_day"] - c["calendar_day"]
        check(results, "resumed_ledger_advances", ds >= 1 and abs(ds - dc) <= 1,
              f"calendar +{dc}, sim +{ds} (the {paused_span} paused calendar days were not settled)")
        total_cal = f["calendar_day"] - a["calendar_day"]
        total_sim = f["sim_day"] - a["sim_day"]
        check(results, "ledger_lags_calendar_by_the_pause", total_cal - total_sim >= 2,
              f"since the first info: calendar +{total_cal}, sim +{total_sim}")

        def chat_since(offset: int) -> list[str]:
            if not client_log.exists():
                return []
            with client_log.open("rb") as fh:
                fh.seek(offset)
                return [x for x in fh.read().decode("utf-8", "replace").splitlines() if "[CHAT]" in x]

        def log_offset() -> int:
            return client_log.stat().st_size if client_log.exists() else 0

        t.note("rumors: advance 60 with the player online")
        offset = log_offset()
        advance_reply = rc(f"{W} advance 60")
        time.sleep(1.5)
        try:
            shot = s.game.shot(OUT / "rumor_chat.png", max_wait=4.0)
            facts["screenshot"] = {"file": "rumor_chat.png", "stable": shot.get("stable")}
        except Exception as exc:  # noqa: BLE001 - the chat lines in the client log are the evidence
            facts["screenshot"] = {"error": str(exc)}
            log(f"  screenshot failed: {exc}")
        t.note("waiting 70 s for the rate limit window (2 rumors per real minute by default)")
        time.sleep(70)
        rc(f"{W} info")
        rc(f"{W} chronicle 8")
        chat = chat_since(offset)
        rumors = [line for line in chat if "Rumor:" in line or "江湖传闻" in line]
        facts["advance_60"] = advance_reply.strip()
        facts["rumor_lines_60"] = len(rumors)
        check(results, "rumors_reach_the_player", len(rumors) >= 1, f"{len(rumors)} rumor chat line(s) in ~72 s")

        t.note("rate limit: advance 600 (many major events at once), then 70 s")
        offset = log_offset()
        rc(f"{W} advance 600")
        time.sleep(70)
        rc(f"{W} chronicle 12")
        chat2 = chat_since(offset)
        rumors2 = [line for line in chat2 if "Rumor:" in line or "江湖传闻" in line]
        stamps = sorted(x for x in (chat_time(line) for line in rumors + rumors2) if x is not None)
        per_window = max((sum(1 for y in stamps if x <= y < x + 59.5) for x in stamps), default=0)
        releases = sorted({round(x) for x in stamps})
        spaced = [b - a for a, b in zip(releases, releases[1:]) if b - a > 2]
        check(results, "rumors_rate_limited", per_window <= 2 and len(rumors2) >= 1
              and all(g >= 55 for g in spaced),
              f"{len(rumors) + len(rumors2)} rumor lines; at most {per_window} in any minute; release gaps "
              f"{', '.join(f'{g:.0f} s' for g in spaced) or '-'} (rumors_per_minute=2, queue cap 6)")
        (OUT / "rumors.txt").write_text(
            "# client log [CHAT] lines (client language en_us; a zh_cn client reads 江湖传闻：...)\n"
            "# after `advance 60`:\n" + "\n".join(chat) + "\n"
            "# after `advance 600` (rate limit: 2 per real minute, the rest queued, queue cap 6):\n"
            + "\n".join(chat2) + "\n", encoding="utf-8")

        server_log = SERVER_DIR / "logs" / "latest.log"
        if server_log.exists():
            lines = [x for x in server_log.read_text(encoding="utf-8", errors="replace").splitlines()
                     if "World sim" in x or "Region runtime loaded" in x]
            (OUT / "server_log_session.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
            check(results, "session_genesis_logged", any("World sim genesis" in x for x in lines),
                  next((x.split("]: ", 1)[-1] for x in lines if "World sim genesis" in x), "no genesis line"))
    finally:
        try:
            cs.stop(log=log)
        finally:
            if original is None:
                CULTIVATION_CONFIG.unlink(missing_ok=True)
            else:
                CULTIVATION_CONFIG.write_bytes(original)
            log(f"restored {CULTIVATION_CONFIG.relative_to(REPO)}")
    return facts


# ================================================================================ part B: restart
def write_properties(server_port: int, rcon_port: int, password: str) -> None:
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    props = SERVER_DIR / "server.properties"
    if not PROPS_BACKUP.exists() and not PROPS_ABSENT.exists():
        if props.is_file():
            shutil.copy2(props, PROPS_BACKUP)
        else:
            PROPS_ABSENT.write_text("no server.properties before the evidence run\n", encoding="utf-8")
    values = {
        "server-ip": "127.0.0.1", "server-port": server_port, "online-mode": "false",
        "enforce-secure-profile": "false", "enable-rcon": "true", "rcon.port": rcon_port,
        "rcon.password": password, "broadcast-rcon-to-ops": "false", "broadcast-console-to-ops": "false",
        "enable-query": "false", "level-name": RESTART_WORLD, "level-seed": "20261006",
        "level-type": "minecraft\\:flat", "generate-structures": "false", "gamemode": "creative",
        "difficulty": "peaceful", "spawn-protection": "0", "spawn-monsters": "false", "spawn-animals": "false",
        "spawn-npcs": "false", "max-players": "2", "view-distance": "4", "simulation-distance": "4",
        "max-tick-time": "-1", "motd": "MyVillage world-sim evidence", "white-list": "false",
        "sync-chunk-writes": "true",
    }
    fd = os.open(str(props), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write("# written by tools/world_sim_evidence.py; the original is restored afterwards\n")
        fh.write("".join(f"{k}={v}\n" for k, v in values.items()))
    eula = SERVER_DIR / "eula.txt"
    if not eula.is_file():
        eula.write_text("eula=true\n", encoding="utf-8")


def restore_properties() -> None:
    props = SERVER_DIR / "server.properties"
    if PROPS_BACKUP.exists():
        shutil.copy2(PROPS_BACKUP, props)
        PROPS_BACKUP.unlink()
        log("restored run-acceptance/server.properties")
    elif PROPS_ABSENT.exists():
        if props.exists():
            props.unlink()
        PROPS_ABSENT.unlink()


def run_server(lock_fd: int, rcon_port: int, password: str, label: str,
               body: Callable[[Callable[[str], str]], None]) -> list[str]:
    """Start the acceptance server, run ``body(rc)``, stop it; returns its world-sim log lines."""
    WORK_DIR.mkdir(parents=True, exist_ok=True)
    out = open(WORK_DIR / f"{label}.log", "wb")
    argv = ["./gradlew", "--no-daemon", "--console=plain", "-Dorg.gradle.jvmargs=-Xmx1g",
            "-x", "generateAllStructures", "runAcceptanceServer"]
    log(f"starting the acceptance server ({label})")
    proc = subprocess.Popen(argv, cwd=REPO, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT,
                            pass_fds=(lock_fd,), start_new_session=True)
    out.close()
    started = procs.start_time(proc.pid)
    try:
        deadline = time.time() + 900
        while True:
            if proc.poll() is not None:
                raise RuntimeError(f"server exited early (rc {proc.returncode}); see {WORK_DIR.name}/{label}.log")
            try:
                with rcon_connect(rcon_port, password, retries=1, timeout=5.0) as r:
                    r.cmd("list")
                break
            except (OSError, RconError):
                if time.time() > deadline:
                    raise RuntimeError("server not answering rcon within 900 s")
                time.sleep(2)
        log(f"server ready ({label})")
        with rcon_connect(rcon_port, password, retries=3, timeout=60.0) as r:
            body(r.cmd)
            r.cmd("stop")
        proc.wait(timeout=180)
    finally:
        if proc.poll() is None:
            tree = [(d, procs.start_time(d), "server child") for d in procs.descendants(proc.pid)]
            procs.kill_pids(tree + [(proc.pid, started, "server")], log=log)
    server_log = SERVER_DIR / "logs" / "latest.log"
    text = server_log.read_text(encoding="utf-8", errors="replace") if server_log.exists() else ""
    return [f"[{label}] " + x for x in text.splitlines() if "World sim" in x or "Region runtime loaded" in x]


def part_restart(results: list[dict[str, Any]]) -> dict[str, Any]:
    lock = require_lock_free()
    world = SERVER_DIR / RESTART_WORLD
    facts: dict[str, Any] = {}
    log("taking the heavy-work lock")
    lock_fd = procs.acquire_lock(lock, timeout=60)
    try:
        if world.exists():
            shutil.rmtree(world)
        server_port, rcon_port = procs.free_ports(2)
        password = secrets.token_urlsafe(18)
        write_properties(server_port, rcon_port, password)
        t1 = Transcript(OUT / "restart_run1.txt", "restart persistence, first start (fresh kept world)")
        t2 = Transcript(OUT / "restart_run2.txt", "restart persistence, second start (same world)")
        before: dict[str, str] = {}
        after: dict[str, str] = {}

        def first(cmd: Callable[[str], str]) -> None:
            def rc(c: str) -> str:
                reply = cmd(c)
                t1.add(c, reply)
                return reply
            rc(f"{W} info")
            rc(f"{W} advance 30")
            rc(f"{W} pause")
            before["info"] = rc(f"{W} info")
            before["chronicle"] = rc(f"{W} chronicle 5")
            rc("save-all flush")

        def second(cmd: Callable[[str], str]) -> None:
            def rc(c: str) -> str:
                reply = cmd(c)
                t2.add(c, reply)
                return reply
            after["info"] = rc(f"{W} info")
            after["chronicle"] = rc(f"{W} chronicle 5")
            rc(f"{W} resume")

        logs = run_server(lock_fd, rcon_port, password, "restart-1", first)
        instance = SERVER_DIR / "config" / WORLD_SIM_CONFIG
        base = instance.read_text(encoding="utf-8") if instance.is_file() else '[world_sim]\n\ttier = "small"\n'
        override = world / "serverconfig" / WORLD_SIM_CONFIG
        override.parent.mkdir(parents=True, exist_ok=True)
        override.write_text(re.sub(r'tier = "\w+"', 'tier = "medium"', base), encoding="utf-8")
        t2.note(f'before this start serverconfig/{WORLD_SIM_CONFIG} was added to the world with tier = "medium" '
                '(must be ignored: the tier is fixed at genesis)')
        facts["tier_config_changed"] = 'tier = "medium"' in override.read_text(encoding="utf-8")
        logs += run_server(lock_fd, rcon_port, password, "restart-2", second)
        (OUT / "server_log_restart.txt").write_text("\n".join(logs) + "\n", encoding="utf-8")

        b, a = parse_info(before["info"]), parse_info(after["info"])
        check(results, "restart_info_identical", before["info"] == after["info"],
              f"sim day {b['sim_day']} / {a['sim_day']}, settlement {b['settlement']} / {a['settlement']}")
        check(results, "restart_chronicle_identical", before["chronicle"] == after["chronicle"],
              f"{len(before['chronicle'].splitlines())} lines each")
        check(results, "restart_paused_persisted", a["settlement"] == "paused", a["settlement"])
        run1 = [x for x in logs if x.startswith("[restart-1]")]
        run2 = [x for x in logs if x.startswith("[restart-2]")]
        check(results, "restart_genesis_once", any("World sim genesis" in x for x in run1)
              and not any("World sim genesis" in x for x in run2) and any("World sim loaded" in x for x in run2),
              "genesis on the first start only; loaded on the second")
        check(results, "restart_tier_change_ignored",
              facts["tier_config_changed"] and any("configured tier 'medium' is ignored" in x for x in run2)
              and "Tier small" in after["info"], "config tier medium ignored, info still says small")
    finally:
        restore_properties()
        os.close(lock_fd)
    return facts


# ================================================================================ main
def write_summary(evidence: dict[str, Any]) -> None:
    rows = "\n".join(f"| {r['check']} | {'pass' if r['pass'] else 'FAIL'} | {r['detail']} |"
                     for r in evidence["checks"])
    files = sorted(p.name for p in OUT.iterdir() if p.is_file() and p.name != "summary.md")
    text = f"""# World sim in-game evidence

Generated by `python3 tools/world_sim_evidence.py --part {evidence['part']}` on {evidence['finished']}
(commit `{evidence.get('commit', '?')}`, working tree may have uncommitted changes).

Session: fresh superflat world, one client (`{PLAYER}`) switched to **creative**, calendar sped up to
`ticks_per_day = {evidence.get('session', {}).get('ticks_per_day', '-')}` in
`run-acceptance/config/myvillage-server.toml` for the session (restored afterwards). Restart: the acceptance server alone,
no player online (the calendar stands still), same world for both starts.

RCON replies are rendered by the server in en_us; a zh_cn client renders the same language keys in Chinese.

| check | result | detail |
|---|---|---|
{rows}

Files: {', '.join(f'`{f}`' for f in files)}
"""
    (OUT / "summary.md").write_text(text, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--part", choices=("session", "restart", "all"), default="all")
    ap.add_argument("--ticks-per-day", type=int, default=200)
    a = ap.parse_args(argv)
    OUT.mkdir(parents=True, exist_ok=True)
    previous = OUT / "evidence.json"
    evidence: dict[str, Any] = {}
    if previous.exists():
        try:
            evidence = json.loads(previous.read_text(encoding="utf-8"))
        except ValueError:
            evidence = {}
    try:
        evidence["commit"] = subprocess.run(["git", "-C", str(REPO), "rev-parse", "--short", "HEAD"],
                                            capture_output=True, text=True, timeout=10).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    parts = ("session", "restart") if a.part == "all" else (a.part,)
    results_by_part: dict[str, list[dict[str, Any]]] = evidence.get("checks_by_part", {})
    rc = 0
    for part in parts:
        results: list[dict[str, Any]] = []
        log(f"== part {part}")
        try:
            if part == "session":
                evidence["session"] = part_session(a.ticks_per_day, results)
            else:
                evidence["restart"] = part_restart(results)
        except Exception as exc:  # noqa: BLE001 - record the failure and keep the partial evidence
            results.append({"check": f"{part}_completed", "pass": False, "detail": str(exc)})
            log(f"part {part} failed: {exc}")
            rc = 1
        results_by_part[part] = results
    evidence["checks_by_part"] = results_by_part
    evidence["checks"] = [r for p in ("session", "restart") for r in results_by_part.get(p, [])]
    evidence["part"] = a.part
    evidence["finished"] = now()
    previous.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_summary(evidence)
    failed = [r for r in evidence["checks"] if not r["pass"]]
    log(f"evidence in {OUT.relative_to(REPO)}: {len(evidence['checks']) - len(failed)} pass, {len(failed)} fail")
    return 1 if failed or rc else 0


if __name__ == "__main__":
    raise SystemExit(main())
