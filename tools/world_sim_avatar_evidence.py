#!/usr/bin/env python3
"""Headless in-game evidence for the world-sim compounds and avatars (world sim P3), rerunnable.

One capture session (about 8-12 min; it holds the shared heavy-work lock while Minecraft runs, so never
wrap this script in flock): Xvfb + the acceptance server + one client (``tools/combat_capture`` session,
fresh superflat world, surface y = -60). The player is switched to creative and stays creative. Ledger
settlement is paused first so no calendar day changes the members mid-check (``advance`` still settles).

1. Pick the active sect with the most members at the gate (``/myvillage world sects`` and ``sect <id>``).
2. ``execute positioned <x> -60 <z> run myvillage world sect <id> build here`` (moves the ledger gate to that
   point, builds the compound there, records the realization).
3. ``tp`` the player into the courtyard behind the gate on the lowest terrace; the number of
   ``myvillage:cultivator`` entities must reach min(members at the gate, 12); every avatar must stand on a
   stone-brick terrace floor with open sky above it (the MOTION_BLOCKING column top is its feet); names and
   UUIDs are distinct. Screenshots: first person close, third person (F5), and a wide shot from above.
4. Force-load the courtyard chunks, ``tp`` 300 blocks away: the count must drop to 0 although those chunks
   stay loaded (withdrawn, not merely unloaded). ``forceload remove``, return: the count matches again and every
   UUID is new (a fresh projection, not the old entities coming back).
5. ``/myvillage world advance 60``: the count follows the ledger again, and every avatar's ledger record says
   that person is alive, at the sect, and of that sect.
6. A ``/damage`` on an avatar fails (invulnerable); a plainly summoned cultivator still takes damage; a summon
   carrying the avatar tag is refused when it joins the level.

Output in ``out/preview/world_sim/avatars/`` (publicly served: no paths, ports or passwords): ``commands.txt``,
``server_log.txt``, the PNGs, ``evidence.json`` and ``index.html``.

    python3 tools/world_sim_avatar_evidence.py [--x 200 --z 200]
"""

from __future__ import annotations

import argparse
import datetime
import html
import json
import math
import re
import secrets
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

from tools.combat_capture import procs  # noqa: E402
from tools.combat_capture.capture import press_f5_to  # noqa: E402
from tools.combat_capture.rcon import connect as rcon_connect  # noqa: E402

OUT = REPO / "out" / "preview" / "world_sim" / "avatars"
SERVER_LOG = REPO / "run-acceptance" / "logs" / "latest.log"
PLAYER = "CaptureDev"
W = "myvillage world"
CULT = "@e[type=myvillage:cultivator]"
SURFACE_Y = -60
EYE = 1.62
MAX_PER_SECT = 12  # WorldSimAvatarConfig.DEFAULT_MAX_PER_SECT
SITE_W, SITE_D = 64, 180  # SectGenerator.SITE_WIDTH / SITE_DEPTH: base = anchor - (32, 0, 90)

SECT_LINE = re.compile(r"^#(\d+) (\S+) \| .*? \| (\d+) members \|", re.MULTILINE)
AT_SECT = re.compile(r"^At the sect \((\d+)\): ", re.MULTILINE)
MASTER = re.compile(r"^Master: (\S+)", re.MULTILINE)
COUNT = re.compile(r"Test passed, count: (\d+)")
PASSED = re.compile(r"^Test passed", re.MULTILINE)  # one line per execution of a multi-entity `execute as`
POS = re.compile(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]")
UUID = re.compile(r"\[I; (-?\d+), (-?\d+), (-?\d+), (-?\d+)\]")
NAME = re.compile(r'"with":\["((?:[^"\\]|\\.)*)",\{"translate":"world_sim\.realm\.(\w+)"\},"((?:[^"\\]|\\.)*)"\]')


def now() -> str:
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def log(msg: str) -> None:
    print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


class Transcript:
    def __init__(self, path: Path, title: str):
        self.path = path
        self.lines: list[str] = [f"# {title}", f"# started {now()}", ""]

    def note(self, text: str) -> None:
        self.lines.append(f"[{now()}] # {text}")
        self.flush()

    def add(self, command: str, reply: str) -> None:
        self.lines.append(f"[{now()}] > {command}")
        body = reply.rstrip("\n")
        self.lines.extend(body.split("\n") if body else ["(no output)"])
        self.lines.append("")
        self.flush()

    def flush(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text("\n".join(self.lines) + "\n", encoding="utf-8")


def check(results: list[dict[str, Any]], name: str, ok: bool, detail: str) -> bool:
    results.append({"check": name, "pass": bool(ok), "detail": detail})
    log(f"  {'PASS' if ok else 'FAIL'} {name}: {detail}")
    return ok


def unescape(text: str) -> str:
    try:
        return json.loads(f'"{text}"')
    except ValueError:
        return text


def look(eye: tuple[float, float, float], target: tuple[float, float, float]) -> tuple[float, float]:
    dx, dy, dz = target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]
    yaw = math.degrees(math.atan2(dz, dx)) - 90.0
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    return yaw, pitch


def run(args: argparse.Namespace, results: list[dict[str, Any]]) -> dict[str, Any]:
    from tools.combat_capture import session as cs

    lock = procs.default_lock_path(REPO)
    if not procs.lock_is_free(lock):
        raise SystemExit(f"the heavy-work lock {lock.name} is held by another job; run again when it is free")
    t = Transcript(OUT / "commands.txt", "world-sim compound and avatars, capture session transcript (creative)")
    facts: dict[str, Any] = {"shots": []}
    log_offset = 0
    try:
        log("starting the capture session (Xvfb + acceptance server + client); it takes the heavy lock")
        st = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), lock=str(lock)), log=log)
        facts["session_timings"] = st.get("timings")
        s = cs.Session.attach(ui_check=True, log=log)
        log_offset = SERVER_LOG.stat().st_size if SERVER_LOG.exists() else 0

        def rc(cmd: str, timeout: float = 30.0) -> str:
            with rcon_connect(st["rcon_port"], st["rcon_password"], retries=3, timeout=timeout) as r:
                reply = r.cmd(cmd)
            t.add(cmd, reply)
            return reply

        def count() -> int:
            m = COUNT.search(rc(f"execute if entity {CULT}"))
            return int(m.group(1)) if m else 0

        def wait_count(expected: int, timeout: float = 45.0) -> int:
            deadline, n = time.time() + timeout, -1
            while time.time() < deadline:
                n = count()
                if n == expected:
                    return n
                time.sleep(2.0)
            return n

        def avatars() -> list[dict[str, Any]]:
            names = rc(f"execute as {CULT} run data get entity @s CustomName")
            uuids = rc(f"execute as {CULT} run data get entity @s UUID")
            poses = rc(f"execute as {CULT} run data get entity @s Pos")
            out = []
            for n, u, p in zip(NAME.finditer(names), UUID.finditer(uuids), POS.finditer(poses)):
                out.append({"name": unescape(n.group(1)), "realm": n.group(2), "sect": unescape(n.group(3)),
                            "uuid": "-".join(u.groups()), "pos": [float(x) for x in p.groups()]})
            return out

        def shot(name: str, title: str) -> None:
            time.sleep(1.5)
            try:
                info = s.game.shot(OUT / f"{name}.png", max_wait=6.0)
                facts["shots"].append({"file": f"{name}.png", "title": title, "stable": info.get("stable")})
                log(f"  shot {name}: {info}")
            except Exception as exc:  # noqa: BLE001 - the RCON checks are the primary evidence
                facts["shots"].append({"file": None, "title": title, "error": str(exc)})
                log(f"  shot {name} failed: {exc}")

        def tp(x: float, y: float, z: float, yaw: float, pitch: float, settle: float = 3.0) -> None:
            rc(f"tp {PLAYER} {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}")
            time.sleep(settle)

        # ------------------------------------------------------------ setup
        t.note("setup: creative player, fixed noon, clear weather, ledger settlement paused")
        s.view = "first"
        rc(f"gamemode creative {PLAYER}")
        mode = rc(f"data get entity {PLAYER} playerGameType")
        check(results, "player_in_creative", mode.rstrip().endswith(" 1"), mode.strip())
        for cmd in ("gamerule doDaylightCycle false", "time set 6000", "gamerule doWeatherCycle false",
                    "weather clear 1000000", "gamerule doMobSpawning false", f"{W} pause"):
            rc(cmd)
        info = rc(W)
        check(results, "world_ledger_active", "World ledger:" in info, info.split("\n")[0])

        # ------------------------------------------------------------ pick the sect
        sects = rc(f"{W} sects")
        best: tuple[int, int, str] | None = None
        for m in SECT_LINE.finditer(sects):
            sect_id = int(m.group(1))
            reply = rc(f"{W} sect {sect_id}")
            at = AT_SECT.search(reply)
            n = int(at.group(1)) if at else 0
            if best is None or n > best[1]:
                best = (sect_id, n, m.group(2))
        if best is None or best[1] == 0:
            raise RuntimeError("no active sect has members at its gate")
        sect_id, at_sect, sect_name = best
        expected = min(at_sect, MAX_PER_SECT)
        facts.update({"sect_id": sect_id, "sect_name": sect_name, "at_sect": at_sect, "expected": expected})
        log(f"sect #{sect_id} {sect_name}: {at_sect} at the gate, expecting {expected} avatars")
        check(results, "no_avatars_before_build", count() == 0, "no cultivator exists before any compound is built")

        # ------------------------------------------------------------ build here
        ax, az = args.x, args.z
        t.note(f"build: the ledger gate of #{sect_id} moves to ({ax}, {az}) and the compound is built there")
        t0 = time.time()
        built = rc(f"execute in minecraft:overworld positioned {ax} {SURFACE_Y} {az} run {W} sect {sect_id} build here",
                   timeout=900.0)
        facts["build_seconds"] = round(time.time() - t0, 1)
        check(results, "compound_built", "Built the compound of" in built and "Generated sect compound" in built,
              f"{facts['build_seconds']} s; " + " | ".join(x for x in built.splitlines() if x.startswith(("Built", "Generated"))))
        gate = rc(f"{W} sect {sect_id}")
        check(results, "gate_realized_in_ledger", f"x={ax} z={az} (built)" in gate,
              next((x for x in gate.splitlines() if x.startswith("Mountain gate")), "no gate line"))
        bx, bz = ax - SITE_W // 2, az - SITE_D // 2
        # The courtyard behind the gate on the lowest terrace: local x 22..40, z 21..30 (SectCourtyardTest).
        court = (bx + 31 + 0.5, float(SURFACE_Y), bz + 25 + 0.5)
        court_box = (bx + 22, bz + 21, bx + 40, bz + 30)

        # ------------------------------------------------------------ near
        t.note("near: the player stands in the courtyard behind the gate")
        tp(court[0], court[1], court[2], 180.0, 10.0, settle=2.0)
        n_near = wait_count(expected)
        check(results, "avatars_present_near", n_near == expected, f"{n_near} cultivators, expected {expected}")
        first = avatars()
        facts["avatars_near"] = first
        names = [a["name"] for a in first]
        check(results, "one_avatar_per_person", len(set(names)) == len(names) == n_near
              and len({a["uuid"] for a in first}) == n_near, f"{len(set(names))} distinct names, {n_near} entities")
        check(results, "avatars_named_with_sect_and_realm",
              all(a["sect"] == sect_name and a["realm"] for a in first),
              "; ".join(f"{a['name']} · {a['realm']} · {a['sect']}" for a in first))
        floor = len(PASSED.findall(rc(f"execute as {CULT} at @s if block ~ ~-1 ~ minecraft:stone_bricks")))
        sky = len(PASSED.findall(rc(f"execute as {CULT} at @s positioned over motion_blocking "
                                    f"if entity @s[distance=..0.6]")))
        lowest = sum(1 for a in first if abs(a["pos"][1] - SURFACE_Y) < 0.01)
        check(results, "avatars_on_courtyard_ground", floor == n_near and sky == n_near,
              f"{floor}/{n_near} stand on a stone-brick terrace floor, {sky}/{n_near} have open sky above them "
              f"(the MOTION_BLOCKING column top is their feet: no roof), {lowest} on the lowest terrace")

        # ------------------------------------------------------------ screenshots
        in_court = [a for a in first if court_box[0] <= a["pos"][0] <= court_box[2] + 1
                    and court_box[1] <= a["pos"][2] <= court_box[3] + 1]
        group = in_court if len(in_court) >= 2 else [a for a in first if abs(a["pos"][1] - SURFACE_Y) < 0.01] or first
        cx = sum(a["pos"][0] for a in group) / len(group)
        cz = sum(a["pos"][2] for a in group) / len(group)
        cy = group[0]["pos"][1]
        facts["camera_target"] = [round(cx, 2), cy, round(cz, 2), len(group)]
        # first person, from the back of the courtyard toward the gate
        eye = (cx, cy + EYE, bz + 30.5)
        yaw, pitch = look(eye, (cx, cy + 1.2, cz))
        tp(eye[0], cy, eye[2], yaw, pitch)
        press_f5_to(s, "first")
        shot("courtyard_close", "first person at the back of the courtyard behind the gate, facing the avatars")
        # the same from three blocks up (one barrier block), so the name tags separate by depth
        ny = cy + 3
        rc(f"setblock {math.floor(cx)} {math.floor(ny) - 1} {math.floor(bz + 30.5)} minecraft:barrier")
        yaw, pitch = look((math.floor(cx) + 0.5, ny + EYE, bz + 30.5), (cx, cy + 1.0, cz))
        tp(math.floor(cx) + 0.5, ny, bz + 30.5, yaw, pitch)
        shot("courtyard_names", "first person from three blocks up at the back of the courtyard: the name tags")
        rc(f"setblock {math.floor(cx)} {math.floor(ny) - 1} {math.floor(bz + 30.5)} minecraft:air")
        # third person (F5, camera behind and above the player)
        px, pz = cx, min(bz + 28.5, cz + 6.0)
        tp(px, cy, pz, 180.0, 22.0)
        press_f5_to(s, "back")
        shot("courtyard_third_person", "third person (F5): the player among the avatars on the courtyard ground")
        press_f5_to(s, "first")
        # wide, from above the next terrace (the player stands on one barrier block)
        wy = SURFACE_Y + 20
        wz = bz + 40.5
        rc(f"setblock {math.floor(cx)} {wy - 1} {math.floor(wz)} minecraft:barrier")
        yaw, pitch = look((cx, wy + EYE, wz), (cx, cy, cz - 6.0))
        tp(math.floor(cx) + 0.5, wy, wz, yaw, pitch, settle=4.0)
        shot("courtyard_wide", "wide shot from above the second terrace: the gate terrace with its avatars")

        # ------------------------------------------------------------ far, with the courtyard chunks kept loaded
        t.note("far: the courtyard chunks are force-loaded, then the player goes 300 blocks away")
        rc(f"forceload add {bx} {bz} {bx + SITE_W - 1} {bz + 40}")
        tp(court[0], court[1], bz - 300.0, 180.0, 0.0, settle=6.0)
        n_far = count()
        check(results, "avatars_withdrawn_far", n_far == 0,
              f"{n_far} cultivators with the player 300 blocks away while the courtyard chunks stay force-loaded")
        rc(f"forceload remove {bx} {bz} {bx + SITE_W - 1} {bz + 40}")

        # ------------------------------------------------------------ back
        t.note("back: the player returns to the courtyard")
        tp(court[0], court[1], court[2], 180.0, 10.0, settle=2.0)
        n_back = wait_count(expected)
        again = avatars()
        old_ids = {a["uuid"] for a in first}
        check(results, "avatars_present_again", n_back == expected
              and {a["name"] for a in again} == set(names), f"{n_back} cultivators, same {len(set(names))} people")
        check(results, "avatars_reprojected_not_reloaded", not (old_ids & {a["uuid"] for a in again}),
              f"0 of {len(again)} UUIDs survive the withdrawal (fresh entities, none saved or reloaded)")

        # ------------------------------------------------------------ advance
        t.note("advance: 60 sim days settle with the player in the courtyard")
        rc(f"{W} advance 60", timeout=120.0)
        reply = rc(f"{W} sect {sect_id}")
        at = AT_SECT.search(reply)
        at_after = int(at.group(1)) if at else 0
        expected_after = min(at_after, MAX_PER_SECT)
        n_after = wait_count(expected_after)
        after = avatars()
        facts.update({"at_sect_after_advance": at_after, "avatars_after_advance": after})
        left = sorted(set(names) - {a["name"] for a in after})
        came = sorted({a["name"] for a in after} - set(names))
        check(results, "avatars_follow_advance", n_after == expected_after
              and len({a["name"] for a in after}) == n_after,
              f"{at_after} at the gate after 60 days -> {n_after} avatars (expected {expected_after}); "
              f"left: {', '.join(left) or '-'}; arrived: {', '.join(came) or '-'}")
        bad = []
        for a in after:
            person = rc(f"{W} person {a['name']}")
            block = person.split("\n#")[0]
            if f"of {sect_name}" not in block or "at the sect" not in block or "(deceased)" in block:
                bad.append(a["name"])
        check(results, "avatars_match_ledger", not bad,
              f"{len(after) - len(bad)}/{len(after)} avatars are living members at the sect in the ledger"
              + (f"; mismatched: {', '.join(bad)}" if bad else ""))
        master = MASTER.search(reply)
        if master and master.group(1) != "none":
            facts["master"] = master.group(1)
        tp(court[0], court[1], bz + 28.5, 180.0, 22.0)
        press_f5_to(s, "back")
        shot("after_advance", "third person after 60 sim days: the courtyard shows the ledger's current members")
        press_f5_to(s, "first")

        # ------------------------------------------------------------ neutral; summon unchanged
        t.note("neutral avatars; the summoned cultivator is unchanged; an escaped avatar copy is refused")
        hurt = rc(f"damage @e[type=myvillage:cultivator,limit=1,sort=nearest] 5 minecraft:player_attack by {PLAYER}")
        check(results, "avatar_invulnerable", "invulnerable" in hurt.lower(), hurt.strip())
        sx, sz = court[0] + 3, court[2] + 4
        rc(f"summon myvillage:cultivator {sx:.1f} {SURFACE_Y} {sz:.1f} {{Tags:[\"plain\"]}}")
        plain = rc("damage @e[type=myvillage:cultivator,tag=plain,limit=1] 5 minecraft:player_attack")
        health = rc("data get entity @e[type=myvillage:cultivator,tag=plain,limit=1] Health")
        check(results, "summoned_cultivator_unchanged", "Applied 5" in plain and "15.0f" in health,
              f"{plain.strip()} / {health.strip()}")
        rc("kill @e[type=myvillage:cultivator,tag=plain]")
        escaped = rc(f"summon myvillage:cultivator {sx:.1f} {SURFACE_Y} {sz:.1f} {{WorldSimPerson:1,Tags:[\"escaped\"]}}")
        check(results, "escaped_avatar_refused", COUNT.search(rc("execute if entity @e[tag=escaped]")) is None,
              f"a summon carrying the avatar tag (WorldSimPerson:1) replied \"{escaped.strip()}\" (vanilla reports "
              f"success before the join event), yet no such entity exists: the manager refused it on joining")

        if SERVER_LOG.exists():
            with SERVER_LOG.open("rb") as fh:
                fh.seek(log_offset)
                text = fh.read().decode("utf-8", "replace")
            lines = [x for x in text.splitlines() if "World sim avatars" in x or "World sim: built" in x
                     or "refused an unmanaged avatar" in x]
            (OUT / "server_log.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
            check(results, "withdrawal_logged", any("no player within" in x for x in lines),
                  next((x.split("]: ", 1)[-1] for x in lines if "no player within" in x), "no withdrawal line"))
            check(results, "escaped_avatar_refusal_logged", any("refused an unmanaged avatar" in x for x in lines),
                  next((x.split("]: ", 1)[-1] for x in lines if "refused an unmanaged avatar" in x), "no refusal line"))
    finally:
        cs.stop(log=log)
    return facts


def write_index(evidence: dict[str, Any]) -> None:
    rows = "\n".join(f"<tr><td>{html.escape(r['check'])}</td><td class=\"{'ok' if r['pass'] else 'bad'}\">"
                     f"{'pass' if r['pass'] else 'FAIL'}</td><td>{html.escape(r['detail'])}</td></tr>"
                     for r in evidence.get("checks", []))
    facts = evidence.get("facts", {})
    shots = "\n".join(f"<figure><img src=\"{html.escape(x['file'])}\" alt=\"{html.escape(x['title'])}\">"
                      f"<figcaption>{html.escape(x['title'])}</figcaption></figure>"
                      for x in facts.get("shots", []) if x.get("file"))
    avatars = "\n".join(f"<li>{html.escape(a['name'])} · {html.escape(a['realm'])} · {html.escape(a['sect'])} "
                        f"at {', '.join(f'{v:g}' for v in a['pos'])}</li>" for a in facts.get("avatars_near", []))
    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>World sim avatars</title>
<style>
:root {{ --bg: #f7f6f2; --fg: #1d1d1b; --muted: #6b6b66; --ok: #1f7a3a; --bad: #b3261e; --line: #d9d6cc; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #17181a; --fg: #ecebe6; --muted: #a3a29c; --ok: #6fcf8b;
  --bad: #ff8a80; --line: #34363a; }} }}
body {{ background: var(--bg); color: var(--fg); font: 15px/1.5 system-ui, sans-serif; margin: 0 auto;
  max-width: 1000px; padding: 16px; }}
table {{ border-collapse: collapse; width: 100%; }} td, th {{ border-bottom: 1px solid var(--line); padding: 6px;
  text-align: left; vertical-align: top; }} .ok {{ color: var(--ok); }} .bad {{ color: var(--bad); font-weight: 600; }}
figure {{ margin: 16px 0; }} img {{ max-width: 100%; height: auto; border: 1px solid var(--line); }}
figcaption, .muted {{ color: var(--muted); }}
</style></head><body>
<h1>World sim: a sect compound and its avatars</h1>
<p class="muted">Generated by <code>python3 tools/world_sim_avatar_evidence.py</code> on {html.escape(evidence.get('finished', '?'))}
(commit <code>{html.escape(evidence.get('commit', '?'))}</code>; the working tree may hold uncommitted changes).
Fresh superflat world, one client in creative, client language en_us (a zh_cn client reads the realm in Chinese:
the name tag is the language key <code>entity.myvillage.cultivator.avatar</code> with the realm as
<code>world_sim.realm.*</code>). Developer evidence only: look, motion and name-tag legibility on a physical
client are <strong>not_verified</strong>.</p>
<p>Sect #{facts.get('sect_id', '?')} {html.escape(str(facts.get('sect_name', '?')))}: {facts.get('at_sect', '?')}
members at the gate, {facts.get('expected', '?')} avatars expected; build took {facts.get('build_seconds', '?')} s.</p>
<h2>Checks</h2>
<table><tr><th>check</th><th>result</th><th>detail</th></tr>
{rows}
</table>
<h2>Screenshots</h2>
{shots}
<h2>Avatars near (first projection)</h2>
<ul>{avatars}</ul>
<h2>Files</h2>
<ul><li><a href="commands.txt">commands.txt</a> (every RCON command and reply)</li>
<li><a href="server_log.txt">server_log.txt</a> (avatar and build lines from the server log)</li>
<li><a href="evidence.json">evidence.json</a></li></ul>
</body></html>
"""
    (OUT / "index.html").write_text(page, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--x", type=int, default=200, help="gate x for `build here` (must lie inside a region)")
    ap.add_argument("--z", type=int, default=200, help="gate z for `build here`")
    a = ap.parse_args(argv)
    OUT.mkdir(parents=True, exist_ok=True)
    evidence: dict[str, Any] = {"started": now()}
    try:
        evidence["commit"] = subprocess.run(["git", "-C", str(REPO), "rev-parse", "--short", "HEAD"],
                                            capture_output=True, text=True, timeout=10).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    results: list[dict[str, Any]] = []
    rc = 0
    try:
        evidence["facts"] = run(a, results)
    except Exception as exc:  # noqa: BLE001 - record the failure and keep the partial evidence
        results.append({"check": "session_completed", "pass": False, "detail": str(exc)})
        log(f"failed: {exc}")
        rc = 1
    evidence["checks"] = results
    evidence["finished"] = now()
    (OUT / "evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_index(evidence)
    failed = [r for r in results if not r["pass"]]
    log(f"evidence in {OUT.relative_to(REPO)}: {len(results) - len(failed)} pass, {len(failed)} fail")
    return 1 if failed or rc else 0


if __name__ == "__main__":
    raise SystemExit(main())
