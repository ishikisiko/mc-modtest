#!/usr/bin/env python3
"""Headless in-game evidence for sect tasks and apprenticeship (player sect entry, slice 3), rerunnable.

One capture session (about 15-25 min, most of it waiting for the framed gate build; it holds the shared
heavy-work lock while Minecraft runs, so never wrap this script in flock): Xvfb + the acceptance server + one
client (``tools/combat_capture`` session, fresh superflat world, surface y = -60, client language en_us). The
opening is the one of ``world_sim_entry_evidence.py``: creative, noon, ledger settlement paused, a spiritual root
and the realm stage mortal_qi_sensed.

1. Pick an active sect with members at the gate, preferring one with a heritage and a gate not built yet
   (``world sects``, ``world sect <id>``); the gate is realized by standing in front of it (fallback
   ``world sect <id> build``). ``world sect <id> join <player>`` (admin join) and
   ``world sect <id> rank <player> inner`` (apprenticeship asks for inner rank).
2. The steward (name tag ``cultivator.avatar.steward``), a right click: the client logs
   ``SECT_DIALOGUE option=TASK_ACCEPT x= y= w= h=``; shot ``task_offer.png``; a click; the server logs
   ``SECT_ENTRY player=<p> intent=TASK_ACCEPT sect=<id> result=ok``. The task's kind (``detect_task_kind``):
   a) ``world player <p>`` names it, b) the latest ``SECT_TASK player=<p> kind=<k>`` server line, c) the
   ``SectDialogue: <p> speaks ...`` line or the chat line of the acceptance (client log ``[CHAT]``, en_us task
   name), else ``unknown`` (then all three are tried; the first ``SECT_TASK`` line settles it).
3. The work. patrol: ``summon myvillage:demon_wolf ... {NoAI:1b}`` next to the player at the steward (the sect's
   region), ``damage ... 1000 minecraft:player_attack by <p>``, one ``SECT_TASK ... kind=patrol progress=<i>/3``
   per kill. tribute: ``give <p> myvillage:low_grade_spirit_stone 5``, counted in ``data get entity <p>
   Inventory``. courier: ``tp`` to the gate of another active sect (the one the task names first, if any text
   names it), ``SECT_TASK ... kind=courier progress=1/1`` within 15 s; back to the steward.
4. Right click the steward: ``option=TASK_TURN_IN``, shot ``task_ready.png``, a click,
   ``intent=TASK_TURN_IN ... result=ok``; ``world player``'s ``Contribution: N`` grows by the task's merit; a
   tribute takes 5 stones from the inventory.
5. Right click again: no ``TASK_ACCEPT`` (this year's task is done), a ``FAREWELL`` button; Escape.
6. Optional meditation sample (key V, ``--meditation-seconds``) before the apprenticeship.
7. An elder (or the sect master): ``world sect <id>`` names them (``Master:``, the ``elder`` / ``sect master``
   entries of ``At the sect``), matched against the avatars' name tags; a right click,
   ``option=APPRENTICE``, shot ``apprentice_offer.png``, a click, ``intent=APPRENTICE ... result=ok``;
   ``world player``'s ``Master:`` line; ``world chronicle 5`` names the apprenticeship.
8. Optional meditation sample with the master (facts only, the expected ratio is about 1.15).
9. H panel, the 天下 tab, shot ``panel_task.png``.

Output in ``out/preview/world_sim/tasks/`` (publicly served: no paths, ports or passwords): ``commands.txt``,
``server_log.txt``, ``client_log.txt``, the PNGs, ``evidence.json`` and ``index.html``.

    python3 tools/world_sim_tasks_evidence.py [--sect ID] [--distance 140] [--realize-timeout 600]
                                              [--dialogue-scale 1] [--courier-tries 6] [--courier-timeout 15]
                                              [--skip-meditation] [--meditation-seconds 10] [--lock-wait 1800]
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
from typing import Any, Iterable

REPO = Path(__file__).resolve().parents[1]
if str(REPO) not in sys.path:
    sys.path.insert(0, str(REPO))

from tools import world_sim_entry_evidence as entry  # noqa: E402
from tools.world_sim_avatar_evidence import (  # noqa: E402
    AT_SECT, CULT, EYE, NAME, PASSED, PLAYER, POS, SECT_LINE, SERVER_LOG, SITE_D, SITE_W, SURFACE_Y, UUID, W,
    Transcript, log, look, now, unescape,
)
from tools.world_sim_entry_evidence import (  # noqa: E402
    GATE, ROTATION, STEWARD_KEY, button_center, front_candidates, mentions, panel_tab_center, scrub,
    split_entities, uuid_from_ints,
)
from tools.world_sim_scripture_evidence import heritage_of, split_items  # noqa: E402

OUT = REPO / "out" / "preview" / "world_sim" / "tasks"

# ---------------------------------------------------------------- log lines (packages S3-B and S3-K)
NUM = r"-?\d+(?:\.\d+)?"
INTENTS = "JOIN|LEAVE|TASK_ACCEPT|TASK_TURN_IN|APPRENTICE"
OPTIONS = "JOIN|LEAVE|FAREWELL|APPRENTICE|TASK_ACCEPT|TASK_TURN_IN"
SECT_ENTRY = re.compile(rf"SECT_ENTRY player=(\S+) intent=({INTENTS}) sect=(-?\d+) result=(\S+)")
SECT_DIALOGUE = re.compile(rf"SECT_DIALOGUE option=({OPTIONS}) x=({NUM}) y=({NUM}) w=({NUM}) h=({NUM})")
SECT_TASK = re.compile(r"SECT_TASK player=(\S+) kind=(\w+) progress=(-?\d+)/(-?\d+)")
SPEAKS = re.compile(r"SectDialogue: (\S+) speaks with (.*)$")

CONTRIBUTION = re.compile(r"^Contribution: (-?\d+)", re.MULTILINE)
MASTER_LINE = re.compile(r"^Master: (.*?)\s*$", re.MULTILINE)
SECTS_ROW = re.compile(r"^#(\d+) (\S+) \|.*?\| gate (-?\d+), (-?\d+) (\(.*?\))", re.MULTILINE)
PROGRESS = re.compile(r"^cultivation progress: (-?\d+)", re.MULTILINE)
ITEM_ID = re.compile(r"\bid: ?\"([^\"]+)\"")
ITEM_COUNT = re.compile(r"\b[Cc]ount: ?(\d+)")
WITH_FIRST = re.compile(r'"with":\["((?:[^"\\]|\\.)*)"')

STONE = "myvillage:low_grade_spirit_stone"
WOLF = "myvillage:demon_wolf"
NONE_WORDS = ("none", "无", "")

KINDS = ("patrol", "tribute", "courier")
# sect_tasks.json (schema 1): id -> kind, count, contribution
TASKS = {"patrol_beasts": ("patrol", 3, 10), "tribute_stones": ("tribute", 5, 10),
         "courier_letter": ("courier", 1, 15)}
KIND_TASK = {v[0]: k for k, v in TASKS.items()}
# the kind word, the task id, the en_us and zh_cn task names (world_sim.task.<id>.name)
KIND_WORDS = {
    "patrol": r"patrol|巡山|Patrol the Hills|巡山除兽",
    "tribute": r"tribute|供奉|Spirit Stone Tribute|供奉灵石",
    "courier": r"courier|传信|Carry a Letter|传信他宗|deliver a letter|把信送到",
}
ELDER_RANKS = re.compile(r"(?:^|\s)(elder|长老)\s*$", re.IGNORECASE)
MASTER_RANKS = re.compile(r"(?:^|\s)(sect master|掌门)\s*$", re.IGNORECASE)
APPRENTICE_WORDS = r"apprentic|disciple of|拜(?!入)"

LOG_KEEP = ("GATE_REALIZE", "SECT_ENTRY", "SECT_DIALOGUE", "SECT_TASK", "SectTasks", "SectDialogue", "World sim",
            "WorldSimPlayers", "GateRealizer", "[CHAT]", "Meditation")


# ================================================================ pure helpers (tested)

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


def parse_sect_task(line: str) -> dict[str, Any] | None:
    """``SECT_TASK player=<p> kind=<k> progress=<i>/<n>``."""
    m = SECT_TASK.search(line)
    if not m:
        return None
    return {"player": m.group(1), "kind": m.group(2), "progress": int(m.group(3)), "count": int(m.group(4))}


def contribution_of(record: str) -> int | None:
    """``Contribution: N`` of a ``world player`` reply (None when the player is in no sect)."""
    m = CONTRIBUTION.search(record)
    return int(m.group(1)) if m else None


def master_of(record: str) -> str | None:
    """The ``Master:`` line of a ``world player`` (or ``world sect``) reply; None for none / no line."""
    m = MASTER_LINE.search(record)
    if not m:
        return None
    name = m.group(1).strip()
    return None if name.lower() in NONE_WORDS else name


def progress_of(info: str) -> int | None:
    """``cultivation progress: N`` of ``myvillage cultivation info <p>``."""
    m = PROGRESS.search(info)
    return int(m.group(1)) if m else None


def kind_from_text(text: str) -> str | None:
    """The one task kind `text` names (task id, kind word or task name, en_us or zh_cn); None if it names none
    or more than one."""
    if not text:
        return None
    for tid, (kind, _, _) in TASKS.items():
        if tid in text:
            return kind
    hits = [k for k, rx in KIND_WORDS.items() if re.search(rx, text, re.IGNORECASE)]
    return hits[0] if len(hits) == 1 else None


def task_lines_of_record(record: str) -> str:
    """The lines of a ``world player`` reply that can carry a task: not the header (it holds the sect's name),
    nor the standing / left lines (other sects' names)."""
    lines = record.strip().splitlines()[1:]
    return "\n".join(ln for ln in lines
                     if not re.match(r"\s*(Joined:|Master:|Contribution:|Standing with |Left )", ln))


def detect_task_kind(player_record: str, server_lines: Iterable[str], player: str,
                     client_lines: Iterable[str] = ()) -> dict[str, Any]:
    """Which task the player holds: a) the ``world player`` reply, b) the latest ``SECT_TASK`` line of the
    player, c) the latest ``SectDialogue: <p> speaks with`` line naming a kind, d) the latest chat line in the
    client log naming a task, else ``unknown``. Returns {kind, source, evidence}."""
    text = task_lines_of_record(player_record)
    k = kind_from_text(text)
    if k:
        return {"kind": k, "source": "world player", "evidence": text.strip()[:200]}
    server_lines = list(server_lines)
    for line in reversed(server_lines):
        p = parse_sect_task(line)
        if p and p["player"] == player and p["kind"] in KINDS:
            return {"kind": p["kind"], "source": "SECT_TASK", "evidence": line.split("]: ", 1)[-1].strip()}
    for line in reversed(server_lines):
        m = SPEAKS.search(line)
        if m and m.group(1) == player:
            k = kind_from_text(m.group(2))
            if k:
                return {"kind": k, "source": "SectDialogue", "evidence": line.split("]: ", 1)[-1].strip()}
    for line in reversed(list(client_lines)):
        if "[CHAT]" in line:
            k = kind_from_text(line.split("[CHAT]", 1)[1])
            if k:
                return {"kind": k, "source": "chat", "evidence": line.split("[CHAT]", 1)[1].strip()[:200]}
    return {"kind": "unknown", "source": None, "evidence": None}


def split_top(text: str, sep: str = ", ") -> list[str]:
    """Split on `sep` outside parentheses ('A (x, y), B (z)' -> ['A (x, y)', 'B (z)'])."""
    out, depth, start, i = [], 0, 0, 0
    while i < len(text):
        c = text[i]
        if c in "(（":
            depth += 1
        elif c in ")）":
            depth = max(0, depth - 1)
        elif depth == 0 and text.startswith(sep, i):
            out.append(text[start:i])
            start = i + len(sep)
            i += len(sep)
            continue
        i += 1
    out.append(text[start:])
    return [p.strip() for p in out if p.strip()]


def parse_members(sect_reply: str) -> list[dict[str, str]]:
    """The ``At the sect (n): Name[ title] (stage, sect rank), ...`` entries: {name, rank} (rank: the words after
    the sect name in the parentheses, '' if none)."""
    m = AT_SECT.search(sect_reply)
    if not m:
        return []
    rest = sect_reply[m.end():].split("\n", 1)[0]
    out = []
    for part in split_top(rest):
        head, _, tail = part.partition(" (")
        name = head.split(" ")[0].strip()
        inner = tail.rsplit(")", 1)[0]
        rank = inner.rsplit(", ", 1)[-1] if ", " in inner else inner
        if name:
            out.append({"name": name, "rank": rank.strip()})
    return out


def elder_names(sect_reply: str) -> list[str]:
    """Who may take an apprentice: the elders listed at the sect first, then the sect master (``Master:`` line and
    the ``sect master`` entries), without repeats."""
    members = parse_members(sect_reply)
    out: list[str] = []
    for mm in members:
        if ELDER_RANKS.search(" " + mm["rank"]) and mm["name"] not in out:
            out.append(mm["name"])
    masters = [mm["name"] for mm in members if MASTER_RANKS.search(" " + mm["rank"])]
    master = master_of(sect_reply)
    for name in ([master] if master else []) + masters:
        if name not in out:
            out.append(name)
    return out


def avatar_names(names_reply: str) -> list[dict[str, Any]]:
    """Per entity of ``execute as <cultivators> if data entity @s CustomName run data get entity @s CustomName``,
    in selector order: {name, sect, steward}. The name is the first argument of the avatar name tag, the sect
    its third (None when the tag has another shape)."""
    out = []
    for part in split_entities(names_reply):
        m = NAME.search(part)
        if m:
            name, sect = unescape(m.group(1)), unescape(m.group(3))
        else:
            f = WITH_FIRST.search(part)
            name, sect = (unescape(f.group(1)) if f else None), None
        out.append({"name": name, "sect": sect, "steward": STEWARD_KEY in part})
    return out


def rank_elder_candidates(avatars: list[dict[str, Any]], wanted: list[str], sect_name: str) -> list[int]:
    """Indices of the avatars to try for the apprenticeship: the named elders / master first (in `wanted` order),
    then the other non-steward avatars of the sect (their tags carry no rank)."""
    own = [i for i, a in enumerate(avatars)
           if not a["steward"] and a["name"] and (a["sect"] is None or a["sect"] == sect_name)]
    first = []
    for w in wanted:
        for i in own:
            if i not in first and avatars[i]["name"] == w:
                first.append(i)
    return first + [i for i in own if i not in first]


def inventory_count(reply: str, item_id: str) -> int:
    """Items of `item_id` over the stacks of a ``data get entity <p> Inventory`` reply."""
    total = 0
    for item in split_items(reply):
        m = ITEM_ID.search(item)
        if not m or m.group(1) != item_id:
            continue
        c = ITEM_COUNT.search(item)
        total += int(c.group(1)) if c else 1
    return total


def parse_sects(sects_reply: str) -> list[dict[str, Any]]:
    """``world sects`` rows: {id, name, gate: [x, z], built}."""
    return [{"id": int(m.group(1)), "name": m.group(2), "gate": [int(m.group(3)), int(m.group(4))],
             "built": m.group(5) == "(built)"} for m in SECTS_ROW.finditer(sects_reply)]


def order_courier_targets(others: list[dict[str, Any]], hint: str) -> list[dict[str, Any]]:
    """The other sects to try as the letter's destination: those whose name appears in `hint` first (longest
    name first: '玄黄' must not beat '玄黄阁'), then the rest in their order."""
    named = sorted((s for s in others if s["name"] and s["name"] in (hint or "")), key=lambda s: -len(s["name"]))
    if named:
        named = named[:1]
    return named + [s for s in others if s not in named]


def patrol_spots(pos: tuple[float, float, float], gap: int = 2) -> list[tuple[int, int, int]]:
    """Block cells around the player's feet to summon the beasts in, `gap` blocks apart: the four axes, the
    diagonals, then twice as far."""
    bx, by, bz = math.floor(pos[0]), math.floor(pos[1] + 0.01), math.floor(pos[2])
    rings = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1), (1, -1), (-1, -1),
             (2, 0), (-2, 0), (0, 2), (0, -2)]
    return [(bx + dx * gap, by, bz + dz * gap) for dx, dz in rings]


def pick_spaced(cells: list[tuple[int, int, int]], n: int, gap: int = 2) -> list[tuple[int, int, int]]:
    """Up to `n` of `cells` (in order) at least `gap` blocks apart (horizontal)."""
    out: list[tuple[int, int, int]] = []
    for c in cells:
        if all(math.hypot(c[0] - o[0], c[2] - o[2]) >= gap for o in out):
            out.append(c)
        if len(out) == n:
            break
    return out


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
    task = facts.get("task") or {}
    med = facts.get("meditation") or {}
    ratio = med.get("ratio")
    esc = lambda v: html.escape(scrub(str(v)))  # noqa: E731
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sect tasks evidence</title>
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
<h1>World sim: sect tasks and apprenticeship</h1>
<p class="muted">Generated by <code>python3 tools/world_sim_tasks_evidence.py</code> on
{esc(evidence.get('finished', '?'))} (commit <code>{esc(evidence.get('commit', '?'))}</code>;
the working tree may hold uncommitted changes). Fresh superflat world, one client in creative, client language
en_us. Developer evidence only: the dialogue's look, the panel's task row and the feel of the tasks on a physical
client are <strong>not_verified</strong>.</p>
<p>Sect #{esc(facts.get('sect_id', '?'))} {esc(facts.get('sect_name', '?'))}
(heritage: {esc(facts.get('heritage') or 'none')}). Task: {esc(task.get('kind', '?'))}
(detected from {esc(task.get('source') or 'nothing')}). Contribution {esc(facts.get('contribution_before', '?'))}
→ {esc(facts.get('contribution_after', '?'))}. Master: {esc(facts.get('master') or 'none')}.
Meditation gain ratio with / without the master: {esc(ratio if ratio is not None else 'not measured')}.
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
<li><a href="server_log.txt">server_log.txt</a> (GATE_REALIZE, SECT_ENTRY, SECT_TASK and dialogue lines)</li>
<li><a href="client_log.txt">client_log.txt</a> (SECT_DIALOGUE and chat lines)</li>
<li><a href="evidence.json">evidence.json</a></li></ul>
</body></html>
"""


# ================================================================ the session

class Run(entry.Run):
    """The entry script's session plumbing (rcon, tp, log waits, clicks, Escape, the gate realization, the steward)
    with this script's output folder, log filter, option set and steps."""

    def __init__(self, args: argparse.Namespace, results: list[dict[str, Any]]):
        super().__init__(args, results)
        self.t = Transcript(OUT / "commands.txt", "sect tasks and apprenticeship, capture session transcript")
        self.facts = {"shots": [], "notes": [], "tick_samples": [], "dialogues": [], "entries": [],
                      "task_lines": [], "sects": []}
        self.kind = "unknown"
        self.court: tuple[float, float, float] | None = None

    # ------------------------------------------------------------ overrides (output folder, log filter, options)
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

    def wait_dialogue(self, timeout: float = 10.0, settle: float = 0.7) -> dict[str, dict[str, Any]]:
        """Buttons of the next dialogue open (or refresh): {option: {x, y, w, h}}; after the first SECT_DIALOGUE
        line, `settle` seconds more so every button line of that open is in."""
        m, line = self.wait_line(self.clog, SECT_DIALOGUE, timeout)
        if not m:
            return {}
        opts = {m.group(1): parse_dialogue_option(line)}
        time.sleep(settle)
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

    def wait_task(self, kind: str | None, progress: int, timeout: float) -> dict[str, Any] | None:
        """The next SECT_TASK line of the player (of `kind`, any kind if None) with at least `progress` done."""
        m, line = self.wait_line(self.slog, SECT_TASK, timeout,
                                 lambda mm: mm.group(1) == PLAYER and (kind is None or mm.group(2) == kind)
                                 and int(mm.group(3)) >= progress)
        if not m:
            return None
        p = parse_sect_task(line)
        self.facts["task_lines"].append(p)
        return p

    # ------------------------------------------------------------ entities
    def entity_pos(self, uid: str) -> tuple[float, float, float] | None:
        m = POS.search(self.rc(f"data get entity {uid} Pos"))
        return tuple(float(v) for v in m.groups()) if m else None  # type: ignore[return-value]

    def player_pos(self) -> tuple[float, float, float] | None:
        return self.entity_pos(PLAYER)

    def face_uuid(self, uid: str | None, label: str) -> bool:
        """Stand 3 blocks in front of the avatar `uid` on open ground and look at its chest."""
        if not uid:
            return False
        pos = self.entity_pos(uid)
        if not pos:
            self.note(f"{label}: the avatar entity is gone")
            return False
        rot = ROTATION.search(self.rc(f"data get entity {uid} Rotation"))
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
                now_pos = self.entity_pos(uid)
                if now_pos and math.dist(now_pos, pos) > 0.5:  # it walked: aim again once
                    yw, pt = look(eye, (now_pos[0], now_pos[1] + 1.2, now_pos[2]))
                    self.tp(cand[0], cand[1], cand[2], yw, pt, settle=1.0)
                    pos = now_pos
                self.facts.setdefault("stand", {})[label] = {"player": [round(v, 2) for v in cand],
                                                              "avatar": [round(v, 2) for v in pos]}
                return True
        self.note(f"{label}: no open ground 3 blocks from the avatar")
        return False

    def face_steward(self, label: str) -> bool:
        return self.face_uuid(self.steward_uuid, label)

    def open_dialogue(self, label: str, tries: int = 2, uid: str | None = None,
                      settle: float = 0.7) -> dict[str, dict[str, Any]]:
        """Face the avatar (`uid`, the steward by default), right click: the dialogue's buttons."""
        for attempt in range(tries):
            if not self.s.game.wait_ingame(1.0):
                self.close_screen()
            if not self.face_uuid(uid or self.steward_uuid, label):
                return {}
            self.mark()
            self.right_click()
            opts = self.wait_dialogue(10.0, settle=settle)
            if opts:
                return opts
            log(f"  no SECT_DIALOGUE after the right click ({label}, try {attempt + 1})")
            if not self.s.game.wait_ingame(1.0):
                self.close_screen()
        return {}

    def record(self, key: str) -> str:
        rec = self.rc(f"{W} player {PLAYER}")
        self.facts[key] = rec
        return rec

    def stones(self) -> int:
        return inventory_count(self.rc(f"data get entity {PLAYER} Inventory"), STONE)

    def home(self, label: str) -> bool:
        """Back to the own courtyard and the steward (found again: the avatars may have been respawned)."""
        if self.court:
            self.tp(*self.court, 180.0, 10.0, settle=3.0)
        return self.await_steward(label, timeout=40.0, settle=1.0)

    # ------------------------------------------------------------ sect
    def pick_sect(self) -> None:
        sects = self.rc(f"{W} sects")
        rows = {r["id"]: r for r in parse_sects(sects)}
        best: tuple[tuple[bool, bool, bool, int], dict[str, Any]] | None = None
        for m in SECT_LINE.finditer(sects):
            sid = int(m.group(1))
            if self.a.sect is not None and sid != self.a.sect:
                continue
            reply = self.rc(f"{W} sect {sid}")
            gate = GATE.search(reply)
            if not gate:
                continue
            at = AT_SECT.search(reply)
            n = int(at.group(1)) if at else 0
            her = heritage_of(reply)
            built = gate.group(3) == "(built)"
            key = (n > 0, her is not None, not built, n)
            if best is None or key > best[0]:
                best = (key, {"sect_id": sid, "sect_name": m.group(2), "heritage": her, "at_sect": n,
                              "gate": [int(gate.group(1)), int(gate.group(2))], "gate_built_at_start": built,
                              "elders_listed": elder_names(reply)})
        if best is None or not best[0][0]:
            raise RuntimeError("no active sect with members at its gate"
                               + (f" (asked for #{self.a.sect})" if self.a.sect is not None else ""))
        self.facts.update(best[1])
        self.sect_id = best[1]["sect_id"]
        self.facts["sects"] = [r for r in rows.values()]
        gx, gz = best[1]["gate"]
        bx, bz = gx - SITE_W // 2, gz - SITE_D // 2
        self.court = (bx + 31.5, float(SURFACE_Y), bz + 25.5)  # the courtyard behind the gate (avatar evidence)
        others = [r for r in rows.values() if r["id"] != self.sect_id]
        if not others:
            self.note("no other active sect: a courier task cannot be handed out (and cannot be delivered)")
        log(f"sect #{self.sect_id} {best[1]['sect_name']} heritage={best[1]['heritage']} at={best[1]['at_sect']} "
            f"gate={best[1]['gate']}; {len(others)} other active sect(s)")

    def realize(self) -> None:
        if self.facts.get("gate_built_at_start"):
            self.check("gate_ready", True, "the gate was already built when the run started (no realization)")
            return
        super().realize()

    # ------------------------------------------------------------ steps
    def join_step(self) -> None:
        self.t.note("join: admin join, then rank inner (apprenticeship asks for inner rank)")
        self.mark()
        reply = self.rc(f"{W} sect {self.sect_id} join {PLAYER}")
        e = self.wait_entry("JOIN")
        self.check("admin_join_ok", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id),
                   f"{json.dumps(e)} | {reply.strip()[:200]}")
        reply = self.rc(f"{W} sect {self.sect_id} rank {PLAYER} inner")
        rec = self.record("player_after_rank")
        first = rec.strip().splitlines()[0] if rec.strip() else ""
        self.check("rank_inner", bool(re.search(r"inner|内门", first, re.I)), f"{reply.strip()[:200]} | {first}")
        self.facts["contribution_before"] = contribution_of(rec)

    def accept_step(self) -> None:
        self.t.note("accept: right click the steward, click TASK_ACCEPT")
        self.facts["stones_before"] = self.stones()
        opts = self.open_dialogue("task_offer")
        self.check("dialogue_offers_task", "TASK_ACCEPT" in opts, f"options {sorted(opts)}")
        if "TASK_ACCEPT" not in opts:
            if opts:
                self.shot("task_offer", "the steward's dialogue (no TASK_ACCEPT offered)", screen_open=True)
                self.close_screen()
            return
        self.shot("task_offer", "the steward's dialogue for an inner disciple: the TASK_ACCEPT option",
                  screen_open=True)
        self.click_at(*button_center(opts["TASK_ACCEPT"], self.a.dialogue_scale))
        e = self.wait_entry("TASK_ACCEPT")
        self.check("task_accepted", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id), json.dumps(e))
        after = self.wait_dialogue(5.0)  # the refreshed screen: the task's progress
        if after:
            self.shot("task_taken", "the dialogue after TASK_ACCEPT: the task and its progress", screen_open=True)
        self.close_screen()
        time.sleep(1.0)
        rec = self.record("player_after_accept")
        task_line = task_lines_of_record(rec)
        if task_line.strip():
            self.note(f"`world player` after the acceptance: {task_line.strip()[:200]}")
        self.redetect("after the acceptance")

    def redetect(self, when: str) -> str:
        self.poll_logs()
        d = detect_task_kind(self.facts.get("player_after_accept", ""), self.server_lines, PLAYER,
                             self.client_lines)
        if d["kind"] != "unknown" or "task" not in self.facts:
            self.facts["task"] = dict(d, when=when)
        self.kind = d["kind"]
        log(f"  task kind {when}: {d}")
        return self.kind

    def patrol(self, strict: bool = True, done: int = 0) -> bool:
        """Demon wolves killed by the player at the steward (in the sect's region): SECT_TASK patrol <i>/3 per kill,
        from kill `done` + 1 to 3. With `strict` False (kind unknown) one kill only, to see whether the task is a
        patrol (no check then)."""
        self.t.note("patrol: summon a demon wolf (NoAI) next to the player at the steward, damage it by the player")
        self.face_steward("patrol")
        pos = self.player_pos()
        if not pos:
            self.check("patrol_progress", False, "no player position")
            return False
        free = []
        for c in patrol_spots(pos):
            ok = all(PASSED.search(self.rc(f"execute {cond} block {x} {y} {z} minecraft:air"))
                     for cond, (x, y, z) in (("if", c), ("if", (c[0], c[1] + 1, c[2])),
                                             ("unless", (c[0], c[1] - 1, c[2]))))
            if ok:
                free.append(c)
            if len(pick_spaced(free, 3)) == 3:
                break
        spots = pick_spaced(free, 3) or [(math.floor(pos[0]), math.floor(pos[1] + 0.01), math.floor(pos[2]))]
        self.facts["patrol_spots"] = [list(s) for s in spots]
        seen: list[dict[str, Any]] = list(self.facts.get("patrol_lines", [])) if done else []
        for i in range(done + 1, 4 if strict else done + 2):
            x, y, z = spots[(i - 1) % len(spots)]
            self.mark()
            self.rc(f"summon {WOLF} {x + 0.5} {y} {z + 0.5} {{NoAI:1b,PersistenceRequired:1b}}")
            time.sleep(0.5)
            self.rc(f"execute at {PLAYER} run damage @e[type={WOLF},limit=1,sort=nearest,distance=..12] 1000 "
                    f"minecraft:player_attack by {PLAYER}")
            p = self.wait_task("patrol" if strict else None, i if strict else 0, 8.0)
            if p:
                seen.append(p)
                if p["kind"] != "patrol":
                    break
            elif strict:
                self.note(f"patrol: no SECT_TASK progress line after kill {i}")
        self.facts["patrol_lines"] = seen
        if not strict:
            return bool(seen and seen[-1]["kind"] == "patrol")
        last = seen[-1] if seen else None
        self.check("patrol_progress",
                   bool(last and last["progress"] == 3 and last["count"] == 3)
                   and [p["progress"] for p in seen] == [1, 2, 3],
                   " | ".join(f"{p['kind']} {p['progress']}/{p['count']}" for p in seen) or "no SECT_TASK line")
        self.rc(f"kill @e[type={WOLF}]")  # nothing should be left, but a survivor would disturb later steps
        return bool(last and last["progress"] >= last["count"])

    def tribute(self) -> None:
        self.t.note("tribute: give the player 5 low-grade spirit stones")
        before = self.stones()
        reply = self.rc(f"give {PLAYER} {STONE} 5")
        after = self.stones()
        self.facts["stones_given"] = {"before": before, "after": after}
        self.check("tribute_stones_given", after >= 5 and after - before == 5,
                   f"{before} -> {after} {STONE} | {reply.strip()[:120]}")

    def courier(self, strict: bool = True) -> bool:
        """Visit other sects' gates until SECT_TASK courier 1/1 (the named destination first). With `strict` False
        (kind unknown) the outcome is recorded, not checked."""
        self.t.note("courier: tp to the gate of another active sect, wait for SECT_TASK courier 1/1")
        others = [s for s in self.facts.get("sects", []) if s["id"] != self.sect_id]
        self.poll_logs()
        hint = "\n".join([self.facts.get("player_after_accept", "")]
                         + [ln.split("[CHAT]", 1)[1] for ln in self.client_lines if "[CHAT]" in ln])
        order = order_courier_targets(others, hint)[: max(1, self.a.courier_tries)]
        attempts = []
        arrived = None
        for target in order:
            gx, gz = target["gate"]
            self.mark()
            # load the chunk first, then stand on the highest motion-blocking block at the gate anchor
            self.rc(f"tp {PLAYER} {gx + 0.5} {SURFACE_Y + 2} {gz + 0.5}")
            time.sleep(2.0)
            reply = self.rc(f"execute positioned {gx + 0.5} 0 {gz + 0.5} positioned over motion_blocking "
                            f"run tp {PLAYER} ~ ~ ~")
            p = self.wait_task("courier", 1, self.a.courier_timeout)
            attempts.append({"sect": target["id"], "name": target["name"], "gate": target["gate"],
                             "tp": reply.strip()[:120], "line": p})
            if p:
                arrived = p
                break
        self.facts["courier_attempts"] = attempts
        detail = json.dumps(attempts, ensure_ascii=False)[:600] if attempts else "no other active sect to visit"
        if strict or arrived:
            self.check("courier_arrived", bool(arrived and arrived["progress"] >= arrived["count"]), detail)
        else:
            self.note(f"courier probe: no letter delivered ({len(attempts)} gate(s) visited)")
        ok = self.home("courier return")
        self.check("steward_found_after_courier", ok,
                   json.dumps((self.facts.get("steward_refound") or [None])[-1]))
        return bool(arrived)

    def work_step(self) -> None:
        kind = self.kind
        self.facts["work_plan"] = kind
        if kind == "patrol":
            self.patrol()
        elif kind == "tribute":
            self.tribute()
        elif kind == "courier":
            self.courier()
        else:
            self.note("task kind unknown after the acceptance: give the stones, try one kill, then the courier")
            self.tribute()
            if self.patrol(strict=False):
                self.kind = "patrol"
                self.facts["task"] = {"kind": "patrol", "source": "SECT_TASK (probe kill)",
                                      "evidence": json.dumps(self.facts.get("patrol_lines"))}
                self.patrol(done=1)  # the remaining kills
                return
            rest = self.redetect("after the probe kill")
            if rest == "patrol":
                self.patrol()
            elif rest == "courier":
                self.courier()
            elif rest == "unknown":
                if self.courier(strict=False):
                    self.kind = "courier"
                    self.facts["task"] = {"kind": "courier", "source": "SECT_TASK (courier visit)",
                                          "evidence": json.dumps(self.facts.get("courier_attempts", [])[-1:])}
                else:
                    self.kind = "tribute"  # neither a kill nor a visit moved it: the stones are the task
                    self.facts["task"] = {"kind": "tribute", "source": "elimination", "evidence": None}

    def turn_in_step(self) -> None:
        self.t.note("turn in: right click the steward, click TASK_TURN_IN")
        if not self.steward_uuid or not self.entity_pos(self.steward_uuid):
            self.home("turn in")
        stones_before = self.stones()
        opts = self.open_dialogue("task_ready")
        self.check("dialogue_offers_turn_in", "TASK_TURN_IN" in opts, f"options {sorted(opts)}")
        if "TASK_TURN_IN" not in opts:
            if opts:
                self.shot("task_ready", "the steward's dialogue (no TASK_TURN_IN offered)", screen_open=True)
                self.close_screen()
            return
        self.shot("task_ready", "the steward's dialogue with the task done: the TASK_TURN_IN option",
                  screen_open=True)
        self.click_at(*button_center(opts["TASK_TURN_IN"], self.a.dialogue_scale))
        e = self.wait_entry("TASK_TURN_IN")
        self.check("task_turned_in", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id), json.dumps(e))
        if self.wait_dialogue(5.0):
            self.shot("task_done", "the dialogue after TASK_TURN_IN", screen_open=True)
        self.close_screen()
        rec = self.record("player_after_turn_in")
        before = self.facts.get("contribution_before") or 0
        after = contribution_of(rec)
        self.facts["contribution_after"] = after
        merit = TASKS[KIND_TASK[self.kind]][2] if self.kind in KIND_TASK else 10
        self.check("contribution_awarded", after is not None and after - before >= merit and after >= 10,
                   f"Contribution {before} -> {after} (task {self.kind}, merit {merit})")
        if self.kind == "tribute":
            stones_after = self.stones()
            self.facts["stones_turn_in"] = {"before": stones_before, "after": stones_after}
            self.check("tribute_taken", stones_before - stones_after == 5,
                       f"{stones_before} -> {stones_after} {STONE}")
        chron = self.rc(f"{W} chronicle 5")
        hits = mentions(chron, PLAYER, r"complet|task|事务")
        if hits:
            self.note(f"chronicle: {hits[0]}")

    def again_step(self) -> None:
        self.t.note("again: right click the steward; no TASK_ACCEPT this year, a FAREWELL button")
        opts = self.open_dialogue("task_again", settle=2.0)
        self.check("no_second_task_this_year", bool(opts) and "TASK_ACCEPT" not in opts and "FAREWELL" in opts,
                   f"options {sorted(opts)}" if opts else "the dialogue did not open")
        if opts:
            self.shot("task_again", "the steward's dialogue after the turn-in: no second task this year",
                      screen_open=True)
            self.close_screen()

    def meditation(self, label: str) -> None:
        """Facts only: progress gained by `--meditation-seconds` of normal meditation (key V) at the courtyard
        point, from progress 0."""
        if self.a.skip_meditation:
            return
        med = self.facts.setdefault("meditation", {})
        try:
            if self.court:
                self.tp(*self.court, 180.0, 10.0, settle=2.0)
            if not med.get("realm_set"):
                r = self.rc(f"myvillage cultivation setrealm {PLAYER} myvillage:qi_refining myvillage:qi_refining_1")
                med["realm_set"] = "Cultivation update for" in r
                self.note("meditation: realm set to qi_refining_1 so the stage gains progress (both samples)")
            self.rc(f"myvillage cultivation setprogress {PLAYER} 0")
            p0 = progress_of(self.rc(f"myvillage cultivation info {PLAYER}"))
            self.s.game.require_ingame("meditation")
            t0 = time.time()
            self.s.game.key("v")
            time.sleep(self.a.meditation_seconds + 2.0)  # + the 40 preparation ticks
            p1 = progress_of(self.rc(f"myvillage cultivation info {PLAYER}"))
            self.s.game.key("x")
            gain = (p1 - p0) if p0 is not None and p1 is not None else None
            med[label] = {"before": p0, "after": p1, "gain": gain, "wall_seconds": round(time.time() - t0, 1)}
            a, b = med.get("without_master", {}).get("gain"), med.get("with_master", {}).get("gain")
            if a and b is not None:
                med["ratio"] = round(b / a, 3)
            log(f"  meditation {label}: {med[label]}")
        except Exception as exc:  # noqa: BLE001 - optional
            med[label] = {"error": str(exc)}
            self.note(f"meditation {label} not measured: {exc}")

    def find_elders(self) -> list[tuple[str, str]]:
        """(uuid, name) of the avatars to try for the apprenticeship, named elders / master first."""
        reply = self.rc(f"{W} sect {self.sect_id}")
        wanted = elder_names(reply)
        self.facts["elders_listed"] = wanted
        named = f"execute as {CULT} if data entity @s CustomName run data get entity @s"
        avatars = avatar_names(self.rc(f"{named} CustomName"))
        uuids = [tuple(int(v) for v in u.groups()) for u in UUID.finditer(self.rc(f"{named} UUID"))]
        order = rank_elder_candidates(avatars, wanted, self.facts.get("sect_name", ""))
        self.facts["avatars_seen"] = [a["name"] for a in avatars]
        out = []
        for i in order:
            if i < len(uuids):
                out.append((uuid_from_ints(uuids[i]), avatars[i]["name"]))
        return out

    def apprentice_step(self) -> None:
        self.t.note("apprentice: find an elder (or the master) avatar, right click, click APPRENTICE")
        if self.court:
            self.tp(*self.court, 180.0, 10.0, settle=3.0)
        cands = self.find_elders()
        wanted = set(self.facts.get("elders_listed", []))
        tried = []
        opts: dict[str, dict[str, Any]] = {}
        chosen = None
        for uid, name in cands[:6]:
            opts = self.open_dialogue(f"elder {name}", uid=uid)
            tried.append({"name": name, "listed_elder": name in wanted, "options": sorted(opts)})
            if "APPRENTICE" in opts:
                chosen = name
                break
            if opts:
                self.close_screen()
        self.facts["elder_tries"] = tried
        self.check("dialogue_offers_apprentice", chosen is not None,
                   json.dumps(tried, ensure_ascii=False) if tried else
                   f"no elder avatar found (listed {sorted(wanted)}, seen {self.facts.get('avatars_seen')})")
        if chosen is None:
            return
        self.facts["master_avatar"] = chosen
        self.shot("apprentice_offer", f"an elder's dialogue for an inner disciple: the APPRENTICE option ({chosen})",
                  screen_open=True)
        self.click_at(*button_center(opts["APPRENTICE"], self.a.dialogue_scale))
        e = self.wait_entry("APPRENTICE")
        self.check("apprenticed", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id), json.dumps(e))
        if self.wait_dialogue(5.0):
            self.shot("apprentice_done", "the elder's dialogue after APPRENTICE", screen_open=True)
        self.close_screen()
        rec = self.record("player_after_apprentice")
        master = master_of(rec)
        self.facts["master"] = master
        self.check("master_recorded", master is not None,
                   f"Master: {master or 'none'} (the avatar clicked: {chosen})")
        chron = self.rc(f"{W} chronicle 5")
        hits = mentions(chron, PLAYER, APPRENTICE_WORDS)
        self.check("chronicle_has_apprentice", bool(hits), hits[0] if hits else "no chronicle line names it")

    def panel(self) -> None:
        self.t.note("panel: H, the 天下 tab, a shot, Escape")
        if not self.s.game.wait_ingame(1.0):
            self.close_screen()
        try:
            self.s.game.key("h")
        except Exception as exc:  # noqa: BLE001
            self.facts["shots"].append({"file": None, "title": "H panel, 天下 page", "error": str(exc)})
            return
        time.sleep(1.5)
        tab = panel_tab_center("world")
        self.click_at(*tab)
        time.sleep(2.0)
        self.note(f"panel: the 天下 tab was clicked at screen pixel {tab}; the page is not machine-checked, the "
                  f"shot is the evidence (我的宗门 card: contribution, master, task row)")
        self.shot("panel_task", "H panel, 天下 page after the task and the apprenticeship: the 我的宗门 card",
                  screen_open=True)
        self.close_screen()

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
            for name, fn in (("realize", self.realize), ("join", self.join_step), ("steward", self.meet_steward),
                             ("accept", self.accept_step), ("work", self.work_step), ("turn_in", self.turn_in_step),
                             ("again", self.again_step),
                             ("meditate_before", lambda: self.meditation("without_master")),
                             ("apprentice", self.apprentice_step),
                             ("meditate_after", lambda: self.meditation("with_master")),
                             ("panel", self.panel)):
                self.step(name, fn)
            self.facts["chronicle_end"] = self.rc(f"{W} chronicle 8")
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
    ap.add_argument("--sect", type=int, default=None,
                    help="sect id (default: members at the gate, a heritage, gate not built, most members)")
    ap.add_argument("--distance", type=int, default=140, help="blocks from the ledger gate for the auto build")
    ap.add_argument("--realize-timeout", type=float, default=600.0, help="seconds to wait for GATE_REALIZE done")
    ap.add_argument("--dialogue-scale", type=float, default=1.0,
                    help="multiply SECT_DIALOGUE coordinates (1: logged in screen pixels; 2: logged in GUI px)")
    ap.add_argument("--courier-tries", type=int, default=6, help="other sects' gates to visit at most")
    ap.add_argument("--courier-timeout", type=float, default=15.0, help="seconds to wait at a gate for the letter")
    ap.add_argument("--skip-meditation", action="store_true", help="skip the two meditation samples")
    ap.add_argument("--meditation-seconds", type=float, default=10.0, help="seconds per meditation sample")
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
