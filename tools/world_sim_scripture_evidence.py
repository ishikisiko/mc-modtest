#!/usr/bin/env python3
"""Headless in-game evidence for the scripture hall (player sect entry, slice 2), rerunnable.

One capture session (about 15-25 min, most of it waiting for the framed gate build; it holds the shared
heavy-work lock while Minecraft runs, so never wrap this script in flock): Xvfb + the acceptance server + one
client (``tools/combat_capture`` session, fresh superflat world, surface y = -60). The opening is the one of
``world_sim_entry_evidence.py``: creative, noon, ledger settlement paused, a spiritual root and the realm stage
mortal_qi_sensed so the player can be admitted.

1. Pick an active sect with a heritage (``world sect <id>`` says ``Heritage:`` something other than none; else any
   active sect), preferring one whose gate is not built yet.
2. The gate: the player stands 140 blocks in front of it until ``GATE_REALIZE sect=<id> state=done`` (fallback
   ``world sect <id> build``). The server logs ``SCRIPTURE_SHELF sect=<id> placed=<n>/<m> at=<x y z;...>``; both
   shelves must be placed. ``world sect <id> shelves`` is recorded (its ``shelf at x y z`` lines are the fallback
   source of the coordinates).
3. ``world sect <id> join <player>`` (admin join): ``SECT_ENTRY ... intent=JOIN ... result=ok``.
4. The player stands 2.5 blocks from the first shelf's face (3 blocks centre to centre, +z/-z/+x/-x, open floor,
   clear line) and looks at its centre; a right click. The server logs
   ``SCRIPTURE_HALL player=<p> intent=OPEN sect=<id> member=<bool> entries=<n>``, the client one
   ``SCRIPTURE_HALL_UI technique=<id> borrowed=<bool> x= y= w= h=`` per entry button (screen pixels). An outer
   disciple sees one.
5. A click on the first button: ``intent=BORROW ... technique=<id> result=ok``; Escape;
   ``data get entity <player> Inventory`` holds a ``myvillage:manual_*`` whose ``myvillage:technique`` component
   is that technique; ``world player <player>`` is recorded.
6. Right click again: the borrowed book's UI line says ``borrowed=true`` (a disabled button; it is not clicked);
   the inventory still holds exactly one copy.
7. ``world sect <id> rank <player> inner``: the hall lists two entries; the second one is borrowed.
8. ``world sect <id> leave <player>``: the hall answers ``member=false`` (not_member).

Output in ``out/preview/world_sim/scripture/`` (publicly served: no paths, ports or passwords): ``commands.txt``,
``server_log.txt``, ``client_log.txt``, the PNGs, ``evidence.json`` and ``index.html``.

    python3 tools/world_sim_scripture_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600]
                                                  [--shelf-distance 3.0] [--ui-scale 1] [--lock-wait 1800]
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
from typing import Any

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

from tools import world_sim_entry_evidence as entry  # noqa: E402
from tools.world_sim_avatar_evidence import (  # noqa: E402
    EYE, PASSED, PLAYER, SECT_LINE, SERVER_LOG, Transcript, W, log, look, now,
)
from tools.world_sim_entry_evidence import GATE, button_center, is_member, scrub  # noqa: E402

OUT = REPO / "out" / "preview" / "world_sim" / "scripture"
NS = "myvillage:"

# ---------------------------------------------------------------- log lines (packages S-B and S-C)
NUM = r"-?\d+(?:\.\d+)?"
SHELF = re.compile(r"SCRIPTURE_SHELF sect=(\d+) placed=(\d+)/(\d+) at=(.*)$")
HALL = re.compile(r"SCRIPTURE_HALL player=(\S+) intent=(OPEN|BORROW) sect=(-?\d+)((?:\s+\w+=\S*)*)")
HALL_UI = re.compile(rf"SCRIPTURE_HALL_UI technique=(\S+)(?: borrowed=(true|false))? x=({NUM}) y=({NUM}) "
                     rf"w=({NUM}) h=({NUM})")
KV = re.compile(r"(\w+)=(\S*)")
REFUSALS = ("not_member", "member_elsewhere")

HERITAGE = re.compile(r"Heritage: (.*?)\s*(?:\n|Mountain gate at|$)")
SHELF_REPLY = re.compile(r"shelf at (-?\d+) (-?\d+) (-?\d+), owned by sect (-?\d+)")
MANUAL = re.compile(r"\"?myvillage:(manual_\w+)\"?")
TECH_COMPONENT = re.compile(r"\"?myvillage:technique\"?\s*:\s*\"([^\"]+)\"")
ENTITY_DATA = "has the following entity data: "
LOG_KEEP = ("GATE_REALIZE", "SECT_ENTRY", "SCRIPTURE_SHELF", "SCRIPTURE_HALL", "ScriptureHall", "ScriptureShel",
            "World sim", "WorldSimPlayers", "GateRealizer")


# ================================================================ pure helpers (tested)

def parse_coords(text: str) -> list[tuple[int, int, int]]:
    """'1 -59 2;3 -59 4' -> [(1, -59, 2), (3, -59, 4)]; empty parts are skipped, malformed ones too."""
    out = []
    for part in text.split(";"):
        nums = part.split()
        if len(nums) == 3:
            try:
                out.append((int(nums[0]), int(nums[1]), int(nums[2])))
            except ValueError:
                continue
    return out


def parse_shelf(line: str) -> dict[str, Any] | None:
    m = SHELF.search(line)
    if not m:
        return None
    return {"sect": int(m.group(1)), "placed": int(m.group(2)), "sites": int(m.group(3)),
            "at": parse_coords(m.group(4))}


def parse_hall(line: str) -> dict[str, Any] | None:
    """A SCRIPTURE_HALL line: player, intent, sect and the key=value tail (member as a bool, entries as an int)."""
    m = HALL.search(line)
    if not m:
        return None
    out: dict[str, Any] = {"player": m.group(1), "intent": m.group(2), "sect": int(m.group(3))}
    for k, v in KV.findall(m.group(4)):
        if k == "member":
            out[k] = v.lower() == "true"
        elif k == "entries":
            try:
                out[k] = int(v)
            except ValueError:
                out[k] = v
        else:
            out[k] = v
    return out


def parse_hall_ui(line: str) -> dict[str, Any] | None:
    m = HALL_UI.search(line)
    if not m:
        return None
    x, y, w, h = (float(v) for v in m.groups()[2:])
    borrowed = None if m.group(2) is None else m.group(2) == "true"
    return {"technique": tech_path(m.group(1)), "borrowed": borrowed, "x": x, "y": y, "w": w, "h": h}


def tech_path(tid: str) -> str:
    """'myvillage:azure_breath' and 'azure_breath' -> 'azure_breath'."""
    return tid[len(NS):] if tid.startswith(NS) else tid


def heritage_of(sect_reply: str) -> str | None:
    """The heritage named by `world sect <id>` ('Heritage: <name>'); None for 'none' or no such line."""
    m = HERITAGE.search(sect_reply)
    if not m:
        return None
    name = m.group(1).strip()
    return None if not name or name.lower() in ("none", "无") else name


def shelves_from_reply(reply: str) -> list[dict[str, Any]]:
    """`world sect <id> shelves` lines '  shelf at x y z, owned by sect n'."""
    return [{"pos": (int(m.group(1)), int(m.group(2)), int(m.group(3))), "sect": int(m.group(4))}
            for m in SHELF_REPLY.finditer(reply)]


def split_items(reply: str) -> list[str]:
    """The top-level compounds of the list in a `data get entity <p> Inventory` reply (SNBT), as text."""
    text = reply.split(ENTITY_DATA, 1)[-1]
    start = text.find("[")
    if start < 0:
        return []
    items, depth, quote, esc, begin = [], 0, None, False, -1
    for i in range(start, len(text)):
        c = text[i]
        if quote:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == quote:
                quote = None
            continue
        if c in "\"'":
            quote = c
        elif c in "[{":
            depth += 1
            if c == "{" and depth == 2:
                begin = i
        elif c in "]}":
            if c == "}" and depth == 2 and begin >= 0:
                items.append(text[begin:i + 1])
                begin = -1
            depth -= 1
            if depth == 0:
                break
    return items


def manuals_in_inventory(reply: str) -> list[dict[str, Any]]:
    """Every technique manual in the reply: {'manual': 'manual_<category>_<grade>', 'technique': '<path>' or None}."""
    out = []
    for item in split_items(reply):
        m = MANUAL.search(item)
        if not m:
            continue
        t = TECH_COMPONENT.search(item)
        out.append({"manual": m.group(1), "technique": tech_path(t.group(1)) if t else None})
    return out


def manual_count(reply: str, technique: str) -> int:
    return sum(1 for x in manuals_in_inventory(reply) if x["technique"] == tech_path(technique))


def stand_candidates(shelf: tuple[int, int, int], dist: float = 3.0) \
        -> list[dict[str, Any]]:
    """Places to stand `dist` blocks (centre to centre, horizontal) from a shelf block: +z, -z, +x, -x. Each holds
    the feet position, the feet cell and the cells between it and the shelf (for a clear line of sight)."""
    x, y, z = shelf
    n = max(1, int(round(dist)))
    out = []
    for dx, dz in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        feet = (x + 0.5 + dx * dist, float(y), z + 0.5 + dz * dist)
        cell = (math.floor(feet[0]), y, math.floor(feet[2]))
        between = [(x + dx * k, y, z + dz * k) for k in range(1, n)]
        out.append({"dir": (dx, dz), "feet": feet, "cell": cell, "between": between})
    return out


def shelf_center(shelf: tuple[int, int, int]) -> tuple[float, float, float]:
    return shelf[0] + 0.5, shelf[1] + 0.5, shelf[2] + 0.5


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
    passed = sum(1 for r in checks if r["pass"])
    notes = "\n".join(f"<li>{html.escape(scrub(str(n)))}</li>" for n in facts.get("notes", []))
    shelves = "; ".join(" ".join(str(v) for v in p) for p in facts.get("shelves", [])) or "none"
    borrowed = ", ".join(facts.get("borrowed", [])) or "none"
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Scripture hall evidence</title>
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
<h1>World sim: the scripture hall</h1>
<p class="muted">Generated by <code>python3 tools/world_sim_scripture_evidence.py</code> on
{html.escape(str(evidence.get('finished', '?')))} (commit <code>{html.escape(str(evidence.get('commit', '?')))}</code>;
the working tree may hold uncommitted changes). Fresh superflat world, one client in creative, client language
en_us. Developer evidence only: the hall screen's look and the shelf model on a physical client are
<strong>not_verified</strong>.</p>
<p>Sect #{html.escape(str(facts.get('sect_id', '?')))} {html.escape(str(facts.get('sect_name', '?')))}
(heritage: {html.escape(str(facts.get('heritage') or 'none'))}). Shelves: {html.escape(shelves)}.
Borrowed: {html.escape(borrowed)}{' (gate fallback: synchronous build)' if facts.get('realize_fallback') else ''}.
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
<ul><li><a href="commands.txt">commands.txt</a> (every command and reply)</li>
<li><a href="server_log.txt">server_log.txt</a> (GATE_REALIZE, SCRIPTURE_SHELF, SECT_ENTRY, SCRIPTURE_HALL lines)</li>
<li><a href="client_log.txt">client_log.txt</a> (SCRIPTURE_HALL_UI lines)</li>
<li><a href="evidence.json">evidence.json</a></li></ul>
</body></html>
"""


# ================================================================ the session

class Run(entry.Run):
    """The entry script's session plumbing (rcon, tp, log waits, clicks, Escape, the gate realization) with this
    script's output folder, log filter and steps."""

    def __init__(self, args: argparse.Namespace, results: list[dict[str, Any]]):
        super().__init__(args, results)
        self.t = Transcript(OUT / "commands.txt", "scripture hall, capture session transcript (creative)")
        self.facts = {"shots": [], "notes": [], "tick_samples": [], "dialogues": [], "entries": [], "opens": [],
                      "borrows": [], "borrowed": [], "shelves": []}
        self.shelves: list[tuple[int, int, int]] = []
        self.stand: dict[str, Any] | None = None

    # ------------------------------------------------------------ overrides (output folder, log filter)
    def poll_logs(self) -> None:
        for tail, keep in ((self.slog, self.server_lines), (self.clog, self.client_lines)):
            if tail is None:
                continue
            before = len(tail.lines)
            tail.poll()
            keep.extend(x for x in tail.lines[before:] if any(k in x for k in LOG_KEEP))

    def shot(self, name: str, title: str, screen_open: bool = False) -> None:
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

    def mark(self) -> None:
        """Move both log cursors to the end: later waits only see lines caused by what follows."""
        self.poll_logs()
        for tail in (self.slog, self.clog):
            if tail is not None:
                tail.cursor = len(tail.lines)

    # ------------------------------------------------------------ sect
    def pick_sect(self) -> None:
        sects = self.rc(f"{W} sects")
        best: tuple[tuple[bool, bool, int], dict[str, Any]] | None = None
        for m in SECT_LINE.finditer(sects):
            sid = int(m.group(1))
            if self.a.sect is not None and sid != self.a.sect:
                continue
            reply = self.rc(f"{W} sect {sid}")
            gate = GATE.search(reply)
            if not gate:
                continue
            her = heritage_of(reply)
            built = gate.group(3) == "(built)"
            key = (her is not None, not built, int(m.group(3)))
            if best is None or key > best[0]:
                best = (key, {"sect_id": sid, "sect_name": m.group(2), "heritage": her,
                              "gate": [int(gate.group(1)), int(gate.group(2))], "gate_built_at_start": built})
        if best is None:
            raise RuntimeError("no active sect with a gate" + (f" (asked for #{self.a.sect})" if self.a.sect is not None else ""))
        self.facts.update(best[1])
        self.sect_id = best[1]["sect_id"]
        if best[1]["heritage"] is None:
            self.note("no active sect keeps a heritage: the inner list is basic + signature technique")
        log(f"sect #{self.sect_id} {best[1]['sect_name']} heritage={best[1]['heritage']} gate={best[1]['gate']}")

    # ------------------------------------------------------------ shelves
    def find_shelf_line(self) -> dict[str, Any] | None:
        found = None
        for line in self.server_lines:
            p = parse_shelf(line)
            if p and p["sect"] == self.sect_id:
                found = p  # the last placement wins (a fallback build places again)
        return found

    def shelves_step(self) -> None:
        self.t.note("shelves: the SCRIPTURE_SHELF line of the build, then `world sect <id> shelves`")
        deadline = time.time() + 30.0
        p = None
        while time.time() < deadline:
            self.poll_logs()
            p = self.find_shelf_line()
            if p:
                break
            time.sleep(0.5)
        self.facts["shelf_line"] = p
        self.check("shelves_placed", bool(p and p["placed"] == 2 and len(p["at"]) == 2),
                   json.dumps(p) if p else "no SCRIPTURE_SHELF line for the sect within 30 s of the build")
        reply = self.rc(f"{W} sect {self.sect_id} shelves")
        self.facts["shelves_reply"] = reply
        listed = shelves_from_reply(reply)
        self.check("shelves_listed_for_sect", len(listed) == 2 and all(s["sect"] == self.sect_id for s in listed),
                   reply.strip()[:300])
        if p and p["at"]:
            self.shelves = list(p["at"])
        else:
            self.shelves = [s["pos"] for s in listed if s["sect"] == self.sect_id]
            if self.shelves:
                self.note("shelf coordinates taken from `world sect <id> shelves` (no usable SCRIPTURE_SHELF line)")
        self.facts["shelves"] = [list(s) for s in self.shelves]

    # ------------------------------------------------------------ the hall
    def block_test(self, cond: str, cell: tuple[int, int, int], block: str = "minecraft:air") -> bool:
        return bool(PASSED.search(self.rc(f"execute {cond} block {cell[0]} {cell[1]} {cell[2]} {block}")))

    def face_shelf(self, label: str, skip: set[tuple[int, int]] | None = None) -> bool:
        """Stand on open floor `--shelf-distance` blocks from the first shelf and look at its centre. The stand
        found first is reused; `skip` excludes directions that failed to open the hall."""
        if not self.shelves:
            raise RuntimeError("no scripture shelf coordinates")
        shelf = self.shelves[0]
        target = shelf_center(shelf)
        cands = [c for c in stand_candidates(shelf, self.a.shelf_distance) if not skip or c["dir"] not in skip]
        if self.stand and (not skip or self.stand["dir"] not in skip):
            cands = [self.stand]
        else:
            # load the chunks around the shelf before testing blocks there
            f0 = cands[0]["feet"] if cands else (target[0], float(shelf[1]), target[2])
            self.tp(f0[0], f0[1], f0[2], 0.0, 0.0, settle=3.0)
        loose = None
        chosen = None
        for c in cands:
            cx, cy, cz = c["cell"]
            if c is self.stand:
                chosen = c
                break
            ok = (self.block_test("if", (cx, cy, cz)) and self.block_test("if", (cx, cy + 1, cz))
                  and self.block_test("unless", (cx, cy - 1, cz)))
            if not ok:
                continue
            clear = all(self.block_test("if", b) and self.block_test("if", (b[0], b[1] + 1, b[2]))
                        for b in c["between"])
            if clear:
                chosen = dict(c, clear=True)
                break
            loose = loose or dict(c, clear=False)
        chosen = chosen or loose
        if not chosen:
            self.note(f"{label}: no open floor {self.a.shelf_distance} blocks from the shelf at {shelf}")
            return False
        feet = chosen["feet"]
        yaw, pitch = look((feet[0], feet[1] + EYE, feet[2]), target)
        self.tp(feet[0], feet[1], feet[2], yaw, pitch, settle=1.5)
        self.stand = chosen
        self.facts.setdefault("stand", {})[label] = {"player": [round(v, 2) for v in feet], "shelf": list(shelf),
                                                     "yaw": round(yaw, 1), "pitch": round(pitch, 1),
                                                     "clear_line": chosen.get("clear")}
        return True

    def wait_hall(self, intent: str, timeout: float) -> dict[str, Any] | None:
        m, line = self.wait_line(self.slog, HALL, timeout,
                                 lambda mm: mm.group(1) == PLAYER and mm.group(2) == intent)
        if not m:
            return None
        p = parse_hall(line)
        self.facts["opens" if intent == "OPEN" else "borrows"].append(p)
        return p

    def wait_ui(self, timeout: float) -> list[dict[str, Any]]:
        """The borrow buttons of the next hall screen: the first SCRIPTURE_HALL_UI line, then every one logged
        within the following second."""
        m, line = self.wait_line(self.clog, HALL_UI, timeout)
        if not m:
            return []
        buttons = [parse_hall_ui(line)]
        time.sleep(1.0)
        self.poll_logs()
        for i in range(self.clog.cursor, len(self.clog.lines)):
            p = parse_hall_ui(self.clog.lines[i])
            if p:
                buttons.append(p)
                self.clog.cursor = i + 1
        self.facts.setdefault("ui", []).append([[b["technique"], b["borrowed"], b["x"], b["y"], b["w"], b["h"]]
                                                for b in buttons])
        return buttons

    def open_hall(self, label: str, ui_timeout: float = 6.0, tries: int = 2) \
            -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
        """Face the shelf, right click: the server's OPEN line and the client's button lines."""
        skip: set[tuple[int, int]] = set()
        for attempt in range(tries):
            if not self.s.game.wait_ingame(1.0):
                self.close_screen()
            if not self.face_shelf(label, skip):
                return None, []
            self.mark()
            self.right_click()
            opened = self.wait_hall("OPEN", 10.0)
            if opened:
                buttons = self.wait_ui(ui_timeout) if ui_timeout > 0 else []
                return opened, buttons
            log(f"  no SCRIPTURE_HALL OPEN after the right click ({label}, try {attempt + 1})")
            if not self.s.game.wait_ingame(1.0):
                self.close_screen()  # something else opened (an avatar in the line of sight?)
            if self.stand:
                skip.add(self.stand["dir"])
            self.stand = None
        return None, []

    def borrow(self, button: dict[str, Any], timeout: float = 10.0) -> dict[str, Any] | None:
        self.mark()
        self.click_at(*button_center(button, self.a.ui_scale))
        return self.wait_hall("BORROW", timeout)

    def inventory(self) -> str:
        return self.rc(f"data get entity {PLAYER} Inventory")

    # ------------------------------------------------------------ steps
    def join_step(self) -> None:
        self.t.note("join: admin join of the player")
        self.mark()
        reply = self.rc(f"{W} sect {self.sect_id} join {PLAYER}")
        e = self.wait_entry("JOIN")
        self.check("admin_join_ok", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id),
                   f"{json.dumps(e)} | {reply.strip()[:200]}")

    def outer_step(self) -> None:
        self.t.note("outer: right click the shelf, one entry, borrow it, the manual in the inventory")
        opened, buttons = self.open_hall("outer")
        self.check("hall_opens_for_outer",
                   bool(opened and opened.get("member", True) and opened.get("entries") == 1
                        and opened["sect"] == self.sect_id),
                   f"{json.dumps(opened)} | buttons {[b['technique'] for b in buttons]}")
        if not opened:
            return
        self.shot("hall_outer", "the hall for an outer disciple: one entry", screen_open=True)
        free = [x for x in buttons if not x["borrowed"]]
        if not free:
            self.check("borrow_ok", False, f"no SCRIPTURE_HALL_UI line of a book not yet borrowed ({len(buttons)} lines)")
            self.close_screen()
            return
        b = self.borrow(free[0])
        ok = bool(b and b.get("result") == "ok" and b["sect"] == self.sect_id
                  and tech_path(b.get("technique", "")) == free[0]["technique"])
        self.check("borrow_ok", ok, f"clicked {free[0]['technique']}: {json.dumps(b)}")
        tech = tech_path(b["technique"]) if b and b.get("technique") else free[0]["technique"]
        if b and b.get("result") == "ok":
            self.facts["borrowed"].append(tech)
        time.sleep(1.0)
        self.close_screen()
        inv = self.inventory()
        self.facts["inventory_after_borrow"] = manuals_in_inventory(inv)
        self.check("manual_in_inventory", manual_count(inv, tech) >= 1,
                   f"{tech}: manuals {json.dumps(self.facts['inventory_after_borrow'])}")
        rec = self.rc(f"{W} player {PLAYER}")
        self.facts["player_after_borrow"] = rec
        lines = [ln.strip() for ln in rec.splitlines() if re.search(r"borrow|借", ln, re.I)]
        if lines:
            self.check("player_record_borrowed", True, " | ".join(lines))
        else:
            self.note("`world player` shows no borrow line (none expected by the task list; recorded only)")

    def again_step(self) -> None:
        self.t.note("again: right click again, the borrowed book's button is disabled (borrowed=true, no click)")
        tech = self.facts["borrowed"][0] if self.facts["borrowed"] else None
        opened, buttons = self.open_hall("again")
        if not opened:
            self.check("borrowed_button_disabled", False, "the hall did not open")
            return
        same = [b for b in buttons if b["technique"] == tech]
        self.check("borrowed_button_disabled", bool(tech and same and all(b["borrowed"] is True for b in same)),
                   f"{tech or 'nothing borrowed before'}: UI lines "
                   f"{[(b['technique'], b['borrowed']) for b in buttons]}; OPEN entries={opened.get('entries')}")
        self.shot("hall_borrowed", "the hall after the borrow: the book is marked borrowed", screen_open=True)
        self.close_screen()
        if tech:
            inv = self.inventory()
            n = manual_count(inv, tech)
            self.check("no_second_copy", n == 1, f"{n} manual(s) of {tech} in the inventory")

    def inner_step(self) -> None:
        self.t.note("inner: rank the player inner, the hall lists two entries, borrow the second")
        reply = self.rc(f"{W} sect {self.sect_id} rank {PLAYER} inner")
        rec = self.rc(f"{W} player {PLAYER}")
        self.check("rank_inner", bool(re.search(r"inner|内门", rec.splitlines()[0] if rec.strip() else "", re.I)),
                   f"{reply.strip()[:200]} | {rec.strip()[:200]}")
        opened, buttons = self.open_hall("inner")
        self.check("hall_inner_sees_two", bool(opened and opened.get("entries") == 2),
                   f"{json.dumps(opened)} | buttons {[b['technique'] for b in buttons]} | heritage "
                   f"{self.facts.get('heritage') or 'none'}")
        if not opened:
            return
        self.shot("hall_inner", "the hall for an inner disciple: two entries", screen_open=True)
        fresh = [b for b in buttons if not b["borrowed"] and b["technique"] not in self.facts["borrowed"]]
        if not fresh:
            self.check("borrow_second_ok", False, f"no button for a book not yet borrowed ({len(buttons)} buttons)")
            self.close_screen()
            return
        b = self.borrow(fresh[0])
        self.check("borrow_second_ok", bool(b and b.get("result") == "ok"
                                            and tech_path(b.get("technique", "")) == fresh[0]["technique"]),
                   f"clicked {fresh[0]['technique']}: {json.dumps(b)}")
        if b and b.get("result") == "ok":
            self.facts["borrowed"].append(tech_path(b["technique"]))
        time.sleep(1.0)
        self.close_screen()
        inv = self.inventory()
        self.facts["inventory_end"] = manuals_in_inventory(inv)
        if len(self.facts["borrowed"]) >= 2:
            self.check("second_manual_in_inventory", manual_count(inv, self.facts["borrowed"][1]) >= 1,
                       json.dumps(self.facts["inventory_end"]))
        self.facts["player_after_inner"] = self.rc(f"{W} player {PLAYER}")

    def refuse_step(self) -> None:
        self.t.note("refuse: the player leaves the sect, the hall refuses")
        self.mark()
        reply = self.rc(f"{W} sect {self.sect_id} leave {PLAYER}")
        e = self.wait_entry("LEAVE")
        rec = self.rc(f"{W} player {PLAYER}")
        self.check("admin_leave_ok", bool(e and e["result"] == "ok") and not is_member(rec, self.facts["sect_name"]),
                   f"{json.dumps(e)} | {reply.strip()[:160]} | {rec.strip()[:160]}")
        opened, buttons = self.open_hall("refused", ui_timeout=3.0)
        refused = bool(opened) and (opened.get("member") is False
                                    or (opened.get("member") is None and opened.get("reason") in REFUSALS))
        self.check("hall_refuses_non_member", refused and not buttons,
                   f"{json.dumps(opened)} | buttons {[b['technique'] for b in buttons]}")
        if opened:
            self.shot("hall_refused", "the hall for a non-member: not a member of this sect", screen_open=True)
            self.close_screen()
        self.facts["chronicle_end"] = self.rc(f"{W} chronicle 8")

    # ------------------------------------------------------------ driver
    def run(self) -> dict[str, Any]:
        from tools.combat_capture import procs
        from tools.combat_capture import session as cs
        from tools.combat_capture.xgame import LogTail

        lock = procs.default_lock_path(REPO)
        deadline = time.time() + self.a.lock_wait
        while not procs.lock_is_free(lock):
            if time.time() >= deadline:
                raise SystemExit(f"the heavy-work lock {lock.name} stayed held for {self.a.lock_wait:.0f} s; "
                                 f"run again when it is free")
            log(f"waiting for the heavy lock {lock.name} (held by another job; up to {self.a.lock_wait:.0f} s)")
            time.sleep(10.0)
        try:
            log("starting the capture session (Xvfb + acceptance server + client); it takes the heavy lock")
            self.st = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), lock=str(lock)), log=log)
            self.facts["session_timings"] = self.st.get("timings")
            self.s = cs.Session.attach(ui_check=True, log=log)
            self.slog = LogTail(SERVER_LOG)
            self.clog = LogTail(self.s.game.client_log)
            self.setup()
            self.pick_sect()
            for name, fn in (("realize", self.realize), ("shelves", self.shelves_step), ("join", self.join_step),
                             ("outer", self.outer_step), ("again", self.again_step), ("inner", self.inner_step),
                             ("refuse", self.refuse_step)):
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
    ap.add_argument("--sect", type=int, default=None, help="sect id (default: one with a heritage, gate not built)")
    ap.add_argument("--distance", type=int, default=140, help="blocks from the ledger gate for the auto build")
    ap.add_argument("--realize-timeout", type=float, default=600.0, help="seconds to wait for GATE_REALIZE done")
    ap.add_argument("--shelf-distance", type=float, default=3.0,
                    help="horizontal blocks from the shelf centre to the player (3.0: 2.5 from its face)")
    ap.add_argument("--ui-scale", type=float, default=1.0,
                    help="multiply SCRIPTURE_HALL_UI coordinates (1: logged in screen pixels; 2: logged in GUI px)")
    ap.add_argument("--lock-wait", type=float, default=1800.0,
                    help="seconds to wait for the heavy-work lock (polled every 10 s) before giving up")
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
