#!/usr/bin/env python3
"""Headless in-game evidence for joining a sect (player sect entry, slice 1), rerunnable.

One capture session (about 15-25 min, most of it waiting for the framed gate build; it holds the shared
heavy-work lock while Minecraft runs, so never wrap this script in flock): Xvfb + the acceptance server + one
client (``tools/combat_capture`` session, fresh superflat world, surface y = -60). The player is creative, the
clock fixed at noon, ledger settlement paused (``advance`` still settles). The player gets a spiritual root
(peak 4000 bp, so even a selective sect admits) and the realm stage mortal_qi_sensed (rules: awakened, mortal
stage 1).

1. Pick the active, not yet built sect with the most members at the gate (``world sects``, ``world sect <id>``),
   ``tp`` the player 140 blocks in front of the ledger gate and wait for the server's
   ``GATE_REALIZE sect=<id> state=done`` (at most 600 s; ``tick query`` every 20 s while it builds). No ``done``
   in time: the check fails and ``world sect <id> build`` builds it synchronously (recorded as the fallback).
   A wide shot from where the player stands.
2. The steward: the cultivator whose CustomName holds ``cultivator.avatar.steward``; the player stands 3 blocks
   in front of it and looks at it (nameplate shot).
3. Right click: the client logs ``SECT_DIALOGUE option=JOIN x= y= w= h=`` per button (screen pixels); a click on
   the JOIN centre; the server logs ``SECT_ENTRY player=<name> intent=JOIN sect=<id> result=ok``; ``world player``
   shows an outer disciple, ``world chronicle 5`` the join.
4. H panel: the 天下 tab is clicked at the position the panel layout gives at GUI scale 2 (960x540, guiScale
   auto); the shot shows the 我的宗门 card. Not machine-checked (no log line): the shot is the evidence.
5. ``world advance 24`` (one year): no promotion below the threshold; then ``cultivation setrealm`` to
   qi_refining_5 and another year: ``player.promote.inner`` (skip with ``--skip-promotion``).
6. Right click -> LEAVE -> ``intent=LEAVE result=ok``; right click again -> JOIN -> ``result=rejoin_cooldown``
   (or no JOIN button offered at all, which is also a refusal; recorded as such).
7. ``world sect <id> join <player>`` forces the join past the cooldown; ``world sect <id> leave <player>``.

Output in ``out/preview/world_sim/entry/`` (publicly served: no paths, ports or passwords): ``commands.txt``,
``server_log.txt``, ``client_log.txt``, the PNGs, ``evidence.json`` and ``index.html``.

    python3 tools/world_sim_entry_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600]
                                              [--skip-promotion] [--dialogue-scale 1]
"""

from __future__ import annotations

import argparse
import html
import json
import math
import re
import secrets
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Callable

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

from tools.combat_capture.xgame import log_line_time  # noqa: E402
from tools.world_sim_avatar_evidence import (  # noqa: E402
    AT_SECT, CULT, EYE, PASSED, PLAYER, POS, SECT_LINE, SERVER_LOG, SITE_D, SITE_W, SURFACE_Y, UUID, W,
    Transcript, check, log, look, now,
)

OUT = REPO / "out" / "preview" / "world_sim" / "entry"
SCREEN = (960, 540)
GUI_SCALE = 2  # guiScale auto at 960x540: the largest i with 960/i >= 320 and 540/i >= 240

# ---------------------------------------------------------------- log lines (packages B and D)
NUM = r"-?\d+(?:\.\d+)?"
GATE_REALIZE = re.compile(r"GATE_REALIZE sect=(\d+) state=(queued|started|clip (\d+)/(\d+)|done|failed)(.*)$")
SECT_ENTRY = re.compile(r"SECT_ENTRY player=(\S+) intent=(JOIN|LEAVE) sect=(-?\d+) result=(\S+)")
SECT_DIALOGUE = re.compile(rf"SECT_DIALOGUE option=(JOIN|LEAVE|FAREWELL) x=({NUM}) y=({NUM}) w=({NUM}) h=({NUM})")

GATE = re.compile(r"Mountain gate at x=(-?\d+) z=(-?\d+) (\(.*?\))")
ROTATION = re.compile(r"\[(-?[\d.]+)f, (-?[\d.]+)f\]")
MSPT = re.compile(r"Average time per tick: ([\d.]+)\s*ms")
ENTITY_DATA = "has the following entity data: "
STEWARD_KEY = "cultivator.avatar.steward"
LOG_KEEP = ("GATE_REALIZE", "SECT_ENTRY", "SECT_DIALOGUE", "World sim", "SectDialogue", "WorldSimPlayers",
            "GateRealizer")

# CultivationProfileScreen layout (GUI pixels): MAX_PANEL_WIDTH 480, MAX_PANEL_HEIGHT 246, SCREEN_MARGIN 4,
# HEADER_HEIGHT 32, BODY_GAP 4, TAB_HEIGHT 18, TAB_GAP 3, tab x = panelLeft + 3, rail width >= 44.
PANEL_TABS = ("profile", "meditation", "techniques", "world")


# ================================================================ pure helpers (tested)

def parse_gate_realize(line: str) -> dict[str, Any] | None:
    m = GATE_REALIZE.search(line)
    if not m:
        return None
    state = "clip" if m.group(3) else m.group(2)
    out: dict[str, Any] = {"sect": int(m.group(1)), "state": state, "rest": m.group(5).strip()}
    if m.group(3):
        out["clip"], out["clips"] = int(m.group(3)), int(m.group(4))
    return out


def parse_sect_entry(line: str) -> dict[str, Any] | None:
    m = SECT_ENTRY.search(line)
    if not m:
        return None
    return {"player": m.group(1), "intent": m.group(2), "sect": int(m.group(3)), "result": m.group(4)}


def parse_dialogue_option(line: str) -> dict[str, Any] | None:
    m = SECT_DIALOGUE.search(line)
    if not m:
        return None
    x, y, w, h = (float(v) for v in m.groups()[1:])
    return {"option": m.group(1), "x": x, "y": y, "w": w, "h": h}


def button_center(opt: dict[str, Any], scale: float = 1.0) -> tuple[int, int]:
    """Screen pixel at the centre of a logged button; `scale` multiplies logged GUI coordinates (1: the log is
    already in screen pixels, as package B specifies)."""
    return (int(round((opt["x"] + opt["w"] / 2.0) * scale)), int(round((opt["y"] + opt["h"] / 2.0) * scale)))


def realize_summary(lines: list[str], sect_id: int) -> dict[str, Any]:
    """Collect the GATE_REALIZE lines of one sect: first queued/started/done/failed line, clip count, and the
    started->done seconds from the log timestamps (None if either is missing or unstamped)."""
    out: dict[str, Any] = {"queued": None, "started": None, "done": None, "failed": None, "clips_logged": 0,
                           "clips_total": None, "seconds": None}
    times: dict[str, float | None] = {}
    for line in lines:
        p = parse_gate_realize(line)
        if not p or p["sect"] != sect_id:
            continue
        if p["state"] == "clip":
            out["clips_logged"] += 1
            out["clips_total"] = p["clips"]
        elif out[p["state"]] is None:
            out[p["state"]] = line.split("]: ", 1)[-1].strip()
            times[p["state"]] = log_line_time(line)
    if times.get("started") is not None and times.get("done") is not None:
        out["seconds"] = round(times["done"] - times["started"], 1)
    return out


def split_entities(reply: str) -> list[str]:
    """`execute as @e run data get entity @s X` concatenates one '<name> has the following entity data: <v>'
    per entity (with or without newlines); returns the <v> parts in order."""
    parts = reply.split(ENTITY_DATA)
    return [p.strip() for p in parts[1:]]


def find_steward(names_reply: str) -> int | None:
    """Index (in selector order) of the first entity whose CustomName holds the steward name-tag key."""
    for i, part in enumerate(split_entities(names_reply)):
        # the next entity's display name trails this part when the lines are concatenated; the key cannot
        # appear in a resolved display name, so a match here is this entity's CustomName
        if STEWARD_KEY in part:
            return i
    return None


def uuid_from_ints(ints: tuple[int, int, int, int] | list[int]) -> str:
    hexs = "".join(f"{int(v) & 0xFFFFFFFF:08x}" for v in ints)
    return f"{hexs[0:8]}-{hexs[8:12]}-{hexs[12:16]}-{hexs[16:20]}-{hexs[20:32]}"


def front_candidates(pos: tuple[float, float, float], yaw: float | None, dist: float = 3.0) \
        -> list[tuple[float, float, float]]:
    """Stand points `dist` blocks from an entity: in front of its facing first (MC yaw 0 faces +z), then the
    four axes (+z first: the courtyard side behind the gate)."""
    x, y, z = pos
    out = []
    if yaw is not None:
        r = math.radians(yaw)
        out.append((x - math.sin(r) * dist, y, z + math.cos(r) * dist))
    out += [(x, y, z + dist), (x, y, z - dist), (x + dist, y, z), (x - dist, y, z)]
    return out


def panel_tab_center(tab: str, screen: tuple[int, int] = SCREEN, scale: int = GUI_SCALE) -> tuple[int, int]:
    """Screen pixel inside a rail tab of the H panel (CultivationProfileScreen.init / updatePanelBounds). The x is
    20 GUI px into the rail (the rail is at least 44 wide), the y the tab's middle."""
    gw, gh = screen[0] // scale, screen[1] // scale
    pw = max(1, min(480, gw - 8))
    ph = max(1, min(246, gh - 8))
    left, top = (gw - pw) // 2, (gh - ph) // 2
    i = PANEL_TABS.index(tab)
    y = top + 32 + 4 + i * (18 + 3) + 9
    return (left + 3 + 20) * scale, y * scale


def parse_mspt(reply: str) -> float | None:
    m = MSPT.search(reply)
    return float(m.group(1)) if m else None


def mentions(text: str, name: str, pattern: str) -> list[str]:
    """Lines of `text` that hold `name` and match `pattern` (case-insensitive)."""
    rx = re.compile(pattern, re.IGNORECASE)
    return [ln.strip() for ln in text.splitlines() if name in ln and rx.search(ln)]


def scrub(text: str) -> str:
    """Drop the checkout path and the home directory from a log excerpt (the page is public)."""
    text = text.replace(str(REPO), "<repo>")
    return re.sub(r"/home/[^/\s]+", "<home>", text)


def render_index(evidence: dict[str, Any]) -> str:
    checks = evidence.get("checks", [])
    rows = "\n".join(f"<tr><td>{html.escape(r['check'])}</td><td class=\"{'ok' if r['pass'] else 'bad'}\">"
                     f"{'pass' if r['pass'] else 'FAIL'}</td><td>{html.escape(scrub(str(r['detail'])))}</td></tr>"
                     for r in checks)
    facts = evidence.get("facts", {})
    shots = "\n".join(f"<figure><img src=\"{html.escape(x['file'])}\" alt=\"{html.escape(x['title'])}\" "
                      f"loading=\"lazy\"><figcaption>{html.escape(x['title'])}"
                      f"{'' if x.get('stable', True) else ' [UNSTABLE]'}</figcaption></figure>"
                      for x in facts.get("shots", []) if x.get("file"))
    missing = "\n".join(f"<li>{html.escape(x['title'])}: {html.escape(scrub(str(x.get('error', 'not captured'))))}</li>"
                        for x in facts.get("shots", []) if not x.get("file"))
    real = facts.get("realize", {})
    passed = sum(1 for r in checks if r["pass"])
    notes = "\n".join(f"<li>{html.escape(scrub(str(n)))}</li>" for n in facts.get("notes", []))
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sect entry evidence</title>
<style>
:root {{ --bg: #f7f6f2; --fg: #1d1d1b; --muted: #6b6b66; --ok: #1f7a3a; --bad: #b3261e; --line: #d9d6cc; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #17181a; --fg: #ecebe6; --muted: #a3a29c; --ok: #6fcf8b;
  --bad: #ff8a80; --line: #34363a; }} }}
body {{ background: var(--bg); color: var(--fg); font: 15px/1.5 system-ui, sans-serif; margin: 0 auto;
  max-width: 1000px; padding: 16px; }}
table {{ border-collapse: collapse; width: 100%; }} td, th {{ border-bottom: 1px solid var(--line); padding: 6px;
  text-align: left; vertical-align: top; overflow-wrap: anywhere; }}
.ok {{ color: var(--ok); }} .bad {{ color: var(--bad); font-weight: 600; }}
figure {{ margin: 16px 0; }} img {{ max-width: 100%; height: auto; border: 1px solid var(--line); }}
figcaption, .muted {{ color: var(--muted); }}
</style></head><body>
<h1>World sim: a player joins a sect</h1>
<p class="muted">Generated by <code>python3 tools/world_sim_entry_evidence.py</code> on
{html.escape(str(evidence.get('finished', '?')))} (commit <code>{html.escape(str(evidence.get('commit', '?')))}</code>;
the working tree may hold uncommitted changes). Fresh superflat world, one client in creative, client language
en_us. Developer evidence only: dialogue look, nameplate legibility and build hitching on a physical client are
<strong>not_verified</strong>.</p>
<p>Sect #{html.escape(str(facts.get('sect_id', '?')))} {html.escape(str(facts.get('sect_name', '?')))}
({html.escape(str(facts.get('at_sect', '?')))} at the gate). Gate realization: started→done
{html.escape(str(real.get('seconds', '?')))} s by the server log, {html.escape(str(real.get('clips_logged', '?')))}
clip lines{' (fallback: synchronous build)' if facts.get('realize_fallback') else ''}.
Checks: {passed} pass, {len(checks) - passed} fail.</p>
<h2>Checks</h2>
<table><tr><th>check</th><th>result</th><th>detail</th></tr>
{rows}
</table>
<h2>Notes</h2>
<ul>{notes}</ul>
<h2>Screenshots</h2>
{shots}
{f'<h3>Not captured</h3><ul>{missing}</ul>' if missing else ''}
<h2>Files</h2>
<ul><li><a href="commands.txt">commands.txt</a> (every RCON command and reply)</li>
<li><a href="server_log.txt">server_log.txt</a> (GATE_REALIZE, SECT_ENTRY and world sim lines)</li>
<li><a href="client_log.txt">client_log.txt</a> (SECT_DIALOGUE lines)</li>
<li><a href="evidence.json">evidence.json</a></li></ul>
</body></html>
"""


# ================================================================ the session

class Run:
    def __init__(self, args: argparse.Namespace, results: list[dict[str, Any]]):
        self.a = args
        self.results = results
        self.t = Transcript(OUT / "commands.txt", "player sect entry, capture session transcript (creative)")
        self.facts: dict[str, Any] = {"shots": [], "notes": [], "tick_samples": [], "dialogues": [], "entries": []}
        self.st: dict[str, Any] = {}
        self.s = None
        self.slog = None
        self.clog = None
        self.server_lines: list[str] = []
        self.client_lines: list[str] = []
        self.window: str | None = None
        self.sect_id = -1
        self.steward_uuid: str | None = None

    # ------------------------------------------------------------ plumbing
    def check(self, name: str, ok: bool, detail: str) -> bool:
        return check(self.results, name, ok, detail)

    def note(self, text: str) -> None:
        self.facts["notes"].append(text)
        self.t.note(text)

    def rc(self, cmd: str, timeout: float = 30.0) -> str:
        from tools.combat_capture.rcon import connect as rcon_connect
        with rcon_connect(self.st["rcon_port"], self.st["rcon_password"], retries=3, timeout=timeout) as r:
            reply = r.cmd(cmd)
        self.t.add(cmd, reply)
        return reply

    def tp(self, x: float, y: float, z: float, yaw: float, pitch: float, settle: float = 2.0) -> None:
        self.rc(f"tp {PLAYER} {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}")
        time.sleep(settle)

    def poll_logs(self) -> None:
        for tail, keep in ((self.slog, self.server_lines), (self.clog, self.client_lines)):
            if tail is None:
                continue
            before = len(tail.lines)
            tail.poll()
            keep.extend(x for x in tail.lines[before:] if any(k in x for k in LOG_KEEP))

    def wait_line(self, tail, rx: re.Pattern, timeout: float, accept: Callable[[re.Match], bool] = lambda m: True):
        """First new line (after the tail's cursor) matching rx and accepted; moves the cursor past it."""
        deadline = time.time() + timeout
        while True:
            self.poll_logs()
            for i in range(tail.cursor, len(tail.lines)):
                m = rx.search(tail.lines[i])
                if m and accept(m):
                    tail.cursor = i + 1
                    return m, tail.lines[i]
            if time.time() >= deadline:
                return None, None
            time.sleep(0.2)

    def shot(self, name: str, title: str, screen_open: bool = False) -> None:
        """A stable full frame. With a screen open, Game.shot refuses (it requires the pointer grab), so the grab
        goes through stable_grab/save_png directly."""
        time.sleep(1.5)
        try:
            if screen_open:
                raw, info = self.s.game.stable_grab(max_wait=6.0)
                self.s.game.save_png(raw, OUT / f"{name}.png")
            else:
                info = self.s.game.shot(OUT / f"{name}.png", max_wait=6.0)
            self.facts["shots"].append({"file": f"{name}.png", "title": title, "stable": info.get("stable")})
            log(f"  shot {name}: {info}")
        except Exception as exc:  # noqa: BLE001 - the log and RCON checks are the primary evidence
            self.facts["shots"].append({"file": None, "title": title, "error": str(exc)})
            log(f"  shot {name} failed: {exc}")

    # ------------------------------------------------------------ input
    def window_id(self) -> str | None:
        if self.window is None:
            try:
                ids = self.s.game.xdo("search", "--name", "Minecraft").split()
                self.window = ids[0] if ids else ""
            except Exception:  # noqa: BLE001
                self.window = ""
        return self.window or None

    def right_click(self) -> None:
        self.s.game.require_ingame("a right click")
        self.s.game.xdo("mousedown", "3")
        time.sleep(0.06)
        self.s.game.xdo("mouseup", "3")

    def click_at(self, x: int, y: int) -> None:
        """Left click at screen pixel (x, y) while a screen is open (the pointer is free, so moving it does not
        turn the camera)."""
        wid = self.window_id()
        if wid:
            self.s.game.xdo("mousemove", "--window", wid, str(x), str(y))
        else:
            self.s.game.xdo("mousemove", str(x), str(y))
        time.sleep(0.3)
        self.s.game.xdo("mousedown", "1")
        time.sleep(0.06)
        self.s.game.xdo("mouseup", "1")

    def close_screen(self) -> bool:
        if self.s.game.pointer_grabbed() is not False:
            return True  # nothing open (Escape would open the pause menu)
        self.s.game.xdo("key", "Escape")
        time.sleep(0.8)
        ok = self.s.game.wait_ingame(3.0) or self.s.game.recover_grab()
        if not ok:
            self.note("a screen did not close after Escape")
        return ok

    def wait_dialogue(self, timeout: float = 10.0) -> dict[str, dict[str, Any]]:
        """Buttons of the next dialogue open (or refresh): {option: {x, y, w, h}}. After the first SECT_DIALOGUE
        line, waits briefly so every button line of that open is in."""
        m, line = self.wait_line(self.clog, SECT_DIALOGUE, timeout)
        if not m:
            return {}
        opts = {m.group(1): parse_dialogue_option(line)}
        time.sleep(0.7)
        self.poll_logs()
        for i in range(self.clog.cursor, len(self.clog.lines)):
            p = parse_dialogue_option(self.clog.lines[i])
            if p:
                opts[p["option"]] = p
                self.clog.cursor = i + 1
        self.facts["dialogues"].append({k: [v["x"], v["y"], v["w"], v["h"]] for k, v in opts.items()})
        return opts

    def wait_entry(self, intent: str, timeout: float = 15.0) -> dict[str, Any] | None:
        m, line = self.wait_line(self.slog, SECT_ENTRY, timeout,
                                 lambda mm: mm.group(1) == PLAYER and mm.group(2) == intent)
        if not m:
            return None
        p = parse_sect_entry(line)
        self.facts["entries"].append(p)
        return p

    # ------------------------------------------------------------ steward
    def find_steward(self) -> tuple[str, tuple[float, float, float]] | None:
        named = f"execute as {CULT} if data entity @s CustomName run data get entity @s"
        names = self.rc(f"{named} CustomName")
        i = find_steward(names)
        if i is None:
            return None
        uuids = [tuple(int(v) for v in u.groups()) for u in UUID.finditer(self.rc(f"{named} UUID"))]
        if i >= len(uuids):
            return None
        uid = uuid_from_ints(uuids[i])
        pos = self.steward_pos(uid)
        return (uid, pos) if pos else None

    def steward_pos(self, uid: str) -> tuple[float, float, float] | None:
        m = POS.search(self.rc(f"data get entity {uid} Pos"))
        return tuple(float(v) for v in m.groups()) if m else None  # type: ignore[return-value]

    def face_steward(self, label: str) -> bool:
        """Stand 3 blocks in front of the steward on open ground and look at its chest."""
        if not self.steward_uuid:
            return False
        pos = self.steward_pos(self.steward_uuid)
        if not pos:
            self.note(f"{label}: the steward entity is gone")
            return False
        rot = ROTATION.search(self.rc(f"data get entity {self.steward_uuid} Rotation"))
        yaw = float(rot.group(1)) if rot else None
        for cand in front_candidates(pos, yaw):
            bx, by, bz = math.floor(cand[0]), math.floor(cand[1] + 0.01), math.floor(cand[2])
            feet = self.rc(f"execute if block {bx} {by} {bz} minecraft:air")
            head = self.rc(f"execute if block {bx} {by + 1} {bz} minecraft:air")
            floor = self.rc(f"execute unless block {bx} {by - 1} {bz} minecraft:air")
            if all(PASSED.search(r) for r in (feet, head, floor)):
                eye = (cand[0], cand[1] + EYE, cand[2])
                yw, pt = look(eye, (pos[0], pos[1] + 1.2, pos[2]))
                self.tp(cand[0], cand[1], cand[2], yw, pt)
                now_pos = self.steward_pos(self.steward_uuid)
                if now_pos and math.dist(now_pos, pos) > 0.5:  # it walked: aim again once
                    yw, pt = look(eye, (now_pos[0], now_pos[1] + 1.2, now_pos[2]))
                    self.tp(cand[0], cand[1], cand[2], yw, pt, settle=1.0)
                    pos = now_pos
                self.facts.setdefault("stand", {})[label] = {"player": [round(v, 2) for v in cand],
                                                              "steward": [round(v, 2) for v in pos]}
                return True
        self.note(f"{label}: no open ground 3 blocks from the steward")
        return False

    def open_dialogue(self, label: str, tries: int = 2) -> dict[str, dict[str, Any]]:
        for attempt in range(tries):
            if not self.face_steward(label):
                return {}
            self.right_click()
            opts = self.wait_dialogue(10.0)
            if opts:
                return opts
            log(f"  no SECT_DIALOGUE after the right click ({label}, try {attempt + 1})")
            if not self.s.game.wait_ingame(1.0):
                self.close_screen()
        return {}

    # ------------------------------------------------------------ steps
    def setup(self) -> None:
        self.t.note("setup: creative player, fixed noon, clear weather, ledger settlement paused, awakened root")
        self.s.view = "first"
        for cmd in (f"gamemode creative {PLAYER}", "gamerule doDaylightCycle false", "time set 6000",
                    "gamerule doWeatherCycle false", "weather clear 1000000", "gamerule doMobSpawning false",
                    f"{W} pause"):
            self.rc(cmd)
        info = self.rc(W)
        self.check("world_ledger_active", "World ledger:" in info, info.split("\n")[0])
        root = self.rc(f"myvillage cultivation setroot {PLAYER} 4000 1500 1500 1500 1500")
        realm = self.rc(f"myvillage cultivation setrealm {PLAYER} myvillage:mortal myvillage:mortal_qi_sensed")
        self.check("player_qualified_setup", "Cultivation update for" in root and "Cultivation update for" in realm,
                   f"{root.strip()} / {realm.strip()}")

    def pick_sect(self) -> None:
        sects = self.rc(f"{W} sects")
        best: tuple[int, int, str, int, int, bool] | None = None
        for m in SECT_LINE.finditer(sects):
            sid = int(m.group(1))
            if self.a.sect is not None and sid != self.a.sect:
                continue
            reply = self.rc(f"{W} sect {sid}")
            at = AT_SECT.search(reply)
            gate = GATE.search(reply)
            if not gate:
                continue
            n = int(at.group(1)) if at else 0
            built = gate.group(3) == "(built)"
            key = (n > 0, not built, n)
            if best is None or key > (best[1] > 0, not best[5], best[1]):
                best = (sid, n, m.group(2), int(gate.group(1)), int(gate.group(2)), built)
        if best is None or best[1] == 0:
            raise RuntimeError("no active sect with members at its gate" + (f" (asked for #{self.a.sect})" if self.a.sect is not None else ""))
        self.sect_id, at_sect, name, gx, gz, built = best
        self.facts.update({"sect_id": self.sect_id, "sect_name": name, "at_sect": at_sect, "gate": [gx, gz],
                           "gate_built_at_start": built})
        log(f"sect #{self.sect_id} {name}: {at_sect} at the gate, gate at ({gx}, {gz}){' (already built)' if built else ''}")

    def realize(self) -> None:
        gx, gz = self.facts["gate"]
        d = self.a.distance
        self.t.note(f"realize: the player stands {d} blocks in front of the ledger gate of #{self.sect_id}")
        px, pz = gx + 0.5, gz - d + 0.5
        self.tp(px, SURFACE_Y, pz, 0.0, -4.0, settle=1.0)
        if self.facts["gate_built_at_start"]:
            self.check("gate_realized_automatically", False, "the gate was already built when the run started")
            return
        t0 = time.time()
        deadline, next_tick = t0 + self.a.realize_timeout, t0
        started_at = done_at = None
        state = None
        while time.time() < deadline:
            self.poll_logs()
            for line in self.slog.lines[self.slog.cursor:]:
                p = parse_gate_realize(line)
                if p and p["sect"] == self.sect_id:
                    if p["state"] == "started" and started_at is None:
                        started_at = time.time()
                    if p["state"] in ("done", "failed"):
                        state, done_at = p["state"], time.time()
            self.slog.cursor = len(self.slog.lines)
            if state:
                break
            if time.time() >= next_tick:
                next_tick += 20.0
                sample: dict[str, Any] = {"t": round(time.time() - t0, 1)}
                try:
                    reply = self.rc("tick query", timeout=10.0)
                    sample["mspt"] = parse_mspt(reply)
                except Exception as exc:  # noqa: BLE001 - a stalled server is the finding
                    sample["error"] = str(exc)
                self.facts["tick_samples"].append(sample)
            time.sleep(0.5)
        summ = realize_summary(self.server_lines, self.sect_id)
        summ["wall_seconds_started_to_done"] = round(done_at - started_at, 1) if started_at and done_at else None
        summ["wall_seconds_waited"] = round(time.time() - t0, 1)
        self.facts["realize"] = summ
        self.check("gate_realized_automatically", state == "done",
                   f"state={state or 'timeout'} after {summ['wall_seconds_waited']} s; started→done "
                   f"{summ['seconds']} s (log), {summ['clips_logged']} clip lines of {summ['clips_total']}; "
                   f"queued: {summ['queued'] or '-'}")
        samples = self.facts["tick_samples"]
        answered = [x for x in samples if "error" not in x]
        msp = [x["mspt"] for x in answered if x.get("mspt") is not None]
        self.check("server_responsive_while_building", len(answered) == len(samples),
                   f"{len(answered)}/{len(samples)} tick queries answered within 10 s; average ms/tick "
                   + (f"max {max(msp):.1f}, mean {sum(msp) / len(msp):.1f}" if msp else "not reported"))
        if state != "done":
            self.facts["realize_fallback"] = True
            self.note("fallback: the gate is built synchronously with `world sect <id> build`")
            built = self.rc(f"{W} sect {self.sect_id} build", timeout=900.0)
            self.check("gate_built_fallback", "Built the compound of" in built,
                       next((x for x in built.splitlines() if x.startswith("Built")), built.strip()[:200]))
        gate = GATE.search(self.rc(f"{W} sect {self.sect_id}"))
        self.check("gate_realized_in_ledger", bool(gate and gate.group(3) == "(built)"),
                   gate.group(0) if gate else "no gate line")
        time.sleep(4.0)
        self.shot("gate_far", f"first person {d} blocks in front of the ledger gate after the build")

    def meet_steward(self) -> None:
        gx, gz = self.facts["gate"]
        bx, bz = gx - SITE_W // 2, gz - SITE_D // 2
        court = (bx + 31.5, float(SURFACE_Y), bz + 25.5)  # the courtyard behind the gate (avatar evidence)
        self.t.note("steward: the player enters the courtyard; the avatar with the steward name tag is found")
        self.tp(*court, 180.0, 10.0, settle=2.0)
        found = None
        deadline = time.time() + 60.0
        while time.time() < deadline and not found:
            found = self.find_steward()
            if not found:
                time.sleep(3.0)
        if not found:
            self.check("steward_present", False, "no cultivator with the steward name tag within 60 s")
            return
        self.steward_uuid, pos = found
        self.facts["steward"] = {"uuid": self.steward_uuid, "pos": [round(v, 2) for v in pos]}
        self.check("steward_present", True, f"steward at {', '.join(f'{v:.1f}' for v in pos)}")
        ok = self.face_steward("nameplate")
        self.check("player_faces_steward", ok, json.dumps(self.facts.get("stand", {}).get("nameplate")))
        if ok:
            self.shot("steward_nameplate", "first person 3 blocks in front of the steward: its name tag")

    def join(self) -> None:
        self.t.note("join: right click the steward, click JOIN")
        opts = self.open_dialogue("join")
        self.check("dialogue_opens_with_join", "JOIN" in opts, f"options {sorted(opts)}")
        if "JOIN" not in opts:
            if opts:
                self.close_screen()
            return
        self.shot("dialogue_open", "the steward's dialogue: greeting, introduction and the JOIN option",
                  screen_open=True)
        self.click_at(*button_center(opts["JOIN"], self.a.dialogue_scale))
        e = self.wait_entry("JOIN")
        self.check("join_ok", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id), json.dumps(e))
        self.wait_dialogue(5.0)  # the refreshed (welcome) screen
        self.shot("dialogue_welcome", "the dialogue after JOIN: the welcome", screen_open=True)
        self.close_screen()
        rec = self.rc(f"{W} player {PLAYER}")
        self.facts["player_after_join"] = rec
        self.check("player_record_outer", bool(re.search(r"outer|外门", rec, re.I))
                   and self.facts["sect_name"] in rec, rec.strip()[:300])
        chron = self.rc(f"{W} chronicle 5")
        hits = mentions(chron, PLAYER, r"join|拜入")
        self.check("chronicle_has_join", bool(hits), hits[0] if hits else "no chronicle line names the player")

    def panel(self) -> None:
        self.t.note("panel: H, the 天下 tab, a shot, Escape")
        try:
            self.s.game.key("h")
        except Exception as exc:  # noqa: BLE001
            self.facts["shots"].append({"file": None, "title": "H panel, 天下 page", "error": str(exc)})
            return
        time.sleep(1.5)
        tab = panel_tab_center("world")
        self.click_at(*tab)
        time.sleep(2.0)
        self.note(f"panel: the 天下 tab was clicked at screen pixel {tab} (CultivationProfileScreen layout at GUI "
                  f"scale {GUI_SCALE}); the page shown is not machine-checked, the shot is the evidence")
        self.shot("panel_after_join", "H panel, 天下 page after joining: the 我的宗门 card", screen_open=True)
        self.close_screen()

    def year(self) -> None:
        self.t.note("advance: one year (24 days) below the promotion threshold")
        self.rc(f"{W} advance 24", timeout=180.0)
        chron = self.rc(f"{W} chronicle 5")
        promo = mentions(chron, PLAYER, r"promot|inner|内门|elder|长老")
        self.check("no_promotion_below_threshold", not promo,
                   promo[0] if promo else "no promotion of the player after a year at mortal_qi_sensed")
        if self.a.skip_promotion:
            self.facts["promotion"] = "not_captured (--skip-promotion)"
            return
        self.t.note("advance: the player's realm set to qi_refining_5, one more year")
        realm = self.rc(f"myvillage cultivation setrealm {PLAYER} myvillage:qi_refining myvillage:qi_refining_5")
        if "Cultivation update for" not in realm:
            self.facts["promotion"] = f"not_captured (setrealm failed: {realm.strip()[:160]})"
            self.note(self.facts["promotion"])
            return
        self.rc(f"{W} advance 24", timeout=180.0)
        chron = self.rc(f"{W} chronicle 5")
        promo = mentions(chron, PLAYER, r"promot|inner|内门")
        rec = self.rc(f"{W} player {PLAYER}")
        self.facts["promotion"] = promo[0] if promo else None
        self.check("promoted_inner_at_threshold", bool(promo) and bool(re.search(r"inner|内门", rec, re.I)),
                   (promo[0] if promo else "no promotion line") + " | " + rec.strip()[:200])

    def leave_and_rejoin(self) -> None:
        self.t.note("leave: right click the steward, click LEAVE")
        opts = self.open_dialogue("leave")
        self.check("dialogue_offers_leave", "LEAVE" in opts, f"options {sorted(opts)}")
        if "LEAVE" in opts:
            self.shot("dialogue_member", "the dialogue for a member: the LEAVE option", screen_open=True)
            self.click_at(*button_center(opts["LEAVE"], self.a.dialogue_scale))
            e = self.wait_entry("LEAVE")
            self.check("leave_ok", bool(e and e["result"] == "ok"), json.dumps(e))
            self.wait_dialogue(5.0)
            self.shot("dialogue_left", "the dialogue after LEAVE: the farewell", screen_open=True)
            self.close_screen()
        elif opts:
            self.close_screen()
        rec = self.rc(f"{W} player {PLAYER}")
        self.facts["player_after_leave"] = rec
        self.t.note("rejoin: right click again, JOIN must be refused (rejoin_cooldown)")
        opts = self.open_dialogue("rejoin")
        if "JOIN" in opts:
            self.click_at(*button_center(opts["JOIN"], self.a.dialogue_scale))
            e = self.wait_entry("JOIN")
            self.wait_dialogue(5.0)
            self.shot("dialogue_rejoin_refused", "JOIN right after leaving: refused", screen_open=True)
            self.check("rejoin_refused_cooldown", bool(e and e["result"] == "rejoin_cooldown"), json.dumps(e))
        elif opts:
            self.shot("dialogue_rejoin_refused", "the dialogue right after leaving: no JOIN option",
                      screen_open=True)
            self.check("rejoin_refused_cooldown", True,
                       f"the dialogue offers no JOIN (options {sorted(opts)}); the refusal reason is in the shot, "
                       f"no SECT_ENTRY line can name it")
        else:
            self.check("rejoin_refused_cooldown", False, "the dialogue did not open")
        if opts:
            self.close_screen()

    def admin(self) -> None:
        self.t.note("admin: forced join past the cooldown, then leave")
        reply = self.rc(f"{W} sect {self.sect_id} join {PLAYER}")
        e = self.wait_entry("JOIN")
        self.check("admin_join_forced", bool(e and e["result"] == "ok"), f"{json.dumps(e)} | {reply.strip()[:200]}")
        rec = self.rc(f"{W} player {PLAYER}")
        self.check("admin_join_record", self.facts["sect_name"] in rec, rec.strip()[:200])
        reply = self.rc(f"{W} sect {self.sect_id} leave {PLAYER}")
        e = self.wait_entry("LEAVE")
        self.check("admin_leave", bool(e and e["result"] == "ok"), f"{json.dumps(e)} | {reply.strip()[:200]}")
        self.facts["chronicle_end"] = self.rc(f"{W} chronicle 8")

    # ------------------------------------------------------------ driver
    def step(self, name: str, fn: Callable[[], None]) -> None:
        log(f"step {name}")
        try:
            fn()
        except Exception as exc:  # noqa: BLE001 - record it, keep going with the next step
            self.check(f"step_{name}_completed", False, f"{type(exc).__name__}: {exc}")
            if self.s is not None and not self.s.game.wait_ingame(1.0):
                try:
                    self.close_screen()
                except Exception:  # noqa: BLE001
                    pass

    def run(self) -> dict[str, Any]:
        from tools.combat_capture import procs
        from tools.combat_capture import session as cs
        from tools.combat_capture.xgame import LogTail

        lock = procs.default_lock_path(REPO)
        if not procs.lock_is_free(lock):
            raise SystemExit(f"the heavy-work lock {lock.name} is held by another job; run again when it is free")
        try:
            log("starting the capture session (Xvfb + acceptance server + client); it takes the heavy lock")
            self.st = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), lock=str(lock)), log=log)
            self.facts["session_timings"] = self.st.get("timings")
            self.s = cs.Session.attach(ui_check=True, log=log)
            self.slog = LogTail(SERVER_LOG)
            self.clog = LogTail(self.s.game.client_log)
            self.setup()
            self.pick_sect()
            for name, fn in (("realize", self.realize), ("steward", self.meet_steward), ("join", self.join),
                             ("panel", self.panel), ("year", self.year), ("leave", self.leave_and_rejoin),
                             ("admin", self.admin)):
                self.step(name, fn)
        finally:
            try:
                self.poll_logs()
            except Exception:  # noqa: BLE001
                pass
            (OUT / "server_log.txt").write_text(scrub("\n".join(self.server_lines)) + "\n", encoding="utf-8")
            (OUT / "client_log.txt").write_text(scrub("\n".join(self.client_lines)) + "\n", encoding="utf-8")
            cs.stop(log=log)
        return self.facts


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--sect", type=int, default=None, help="sect id (default: most members at the gate, not built)")
    ap.add_argument("--distance", type=int, default=140, help="blocks from the ledger gate for the auto build")
    ap.add_argument("--realize-timeout", type=float, default=600.0, help="seconds to wait for GATE_REALIZE done")
    ap.add_argument("--skip-promotion", action="store_true", help="skip the setrealm + second year step")
    ap.add_argument("--dialogue-scale", type=float, default=1.0,
                    help="multiply SECT_DIALOGUE coordinates (1: logged in screen pixels; 2: logged in GUI px)")
    a = ap.parse_args(argv)
    OUT.mkdir(parents=True, exist_ok=True)
    evidence: dict[str, Any] = {"started": now()}
    try:
        evidence["commit"] = subprocess.run(["git", "-C", str(REPO), "rev-parse", "--short", "HEAD"],
                                            capture_output=True, text=True, timeout=10).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    results: list[dict[str, Any]] = []
    runner = Run(a, results)
    rc = 0
    try:
        runner.run()
    except Exception as exc:  # noqa: BLE001 - record the failure and keep the partial evidence
        results.append({"check": "session_completed", "pass": False, "detail": str(exc)})
        log(f"failed: {exc}")
        rc = 1
    evidence["facts"] = runner.facts
    evidence["checks"] = results
    evidence["finished"] = now()
    (OUT / "evidence.json").write_text(scrub(json.dumps(evidence, ensure_ascii=False, indent=2)) + "\n",
                                       encoding="utf-8")
    (OUT / "index.html").write_text(render_index(evidence), encoding="utf-8")
    failed = [r for r in results if not r["pass"]]
    log(f"evidence in {OUT.relative_to(REPO)}: {len(results) - len(failed)} pass, {len(failed)} fail")
    return 1 if failed or rc else 0


if __name__ == "__main__":
    raise SystemExit(main())
