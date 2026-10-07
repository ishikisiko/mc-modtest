#!/usr/bin/env python3
"""Headless in-game evidence for sect news, hostile refusal and a destroyed sect (player sect entry, slice 4).

One capture session (about 15-30 min, most of it waiting for two framed gate builds; it holds the shared
heavy-work lock while Minecraft runs, so never wrap this script in flock): Xvfb + the acceptance server + one
client (``tools/combat_capture`` session, fresh superflat world, surface y = -60, client language en_us). The
opening is the one of ``world_sim_entry_evidence.py``: creative, noon, ledger settlement paused, a spiritual root
and the realm stage mortal_qi_sensed.

1. Two active sects (``world sects``, ``world sect <id>``): A with members at its gate, preferably not built yet;
   B another one with members at its gate (a steward), preferably built, then the farthest from A. A's gate is
   realized by standing 140 blocks in front of it (``GATE_REALIZE sect=<A> state=done``; fallback
   ``world sect <A> build``); ``world sect <A> join <player>`` (admin join).
2. News wherever the player is: ``tp`` 140 blocks in front of B's gate (far from A; B's gate starts to realize),
   the player's position is recorded; ``world sect <A> war <B>`` (reply: ``sect_war.done``); the client chat line
   with the prefix ``【宗门】`` (zh_cn) or ``[Sect]`` (en_us) of ``message.myvillage.world.sect.news`` is awaited
   (the admin act announces at once; then ``world advance 1``, one settled day, and the line is awaited again,
   10 s each); shots ``news_chat.png`` (in game) and ``news_chat_open.png`` (the chat screen, T).
3. Hostile refusal: B's gate realized (auto, fallback ``world sect <B> build``); B's steward (name tag
   ``cultivator.avatar.steward`` with B's name); a right click: the client logs ``SECT_DIALOGUE option=FAREWELL``
   and no ``option=JOIN`` within 2 s; the server line ``SectDialogue: <p> speaks with ... -> options [FAREWELL]``
   (its lines name ``steward.refuse.at_war``); shot ``hostile_refused.png``; Escape.
4. Destroyed: back to A's courtyard (A's steward found again, A's avatars counted), ``world sect <A> destroy``
   (reply: ``sect_destroy.done``); ``world player <p>`` names a rogue cultivator; ``world chronicle 5`` the ruin or
   ``player_leave.sect_gone``; the chat line of it (at once, else after ``world advance 1``); A's avatars at its
   site counted again until 0 (``--withdraw-timeout``).

Output in ``out/preview/world_sim/news/`` (publicly served: no paths, ports or passwords): ``commands.txt``,
``server_log.txt``, ``client_log.txt``, the PNGs, ``evidence.json`` and ``index.html``.

    python3 tools/world_sim_news_evidence.py [--sect-a ID] [--sect-b ID] [--distance 140]
                                             [--realize-timeout 600] [--news-timeout 10]
                                             [--withdraw-timeout 10] [--dialogue-scale 1] [--lock-wait 1800]
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

from tools import world_sim_tasks_evidence as tasks  # noqa: E402
from tools.world_sim_avatar_evidence import (  # noqa: E402
    AT_SECT, CULT, PLAYER, SECT_LINE, SERVER_LOG, SITE_D, SITE_W, SURFACE_Y, UUID, W, Transcript, log, now,
)
from tools.world_sim_entry_evidence import (  # noqa: E402
    GATE, parse_gate_realize, realize_summary, scrub, uuid_from_ints,
)
from tools.world_sim_tasks_evidence import avatar_names, parse_sects  # noqa: E402

OUT = REPO / "out" / "preview" / "world_sim" / "news"

# ---------------------------------------------------------------- log lines and replies (package S4-A)
# message.myvillage.world.sect.news: zh_cn "【宗门】%1$s", en_us "[Sect] %1$s"
NEWS_PREFIX = r"(?:【宗门】|\[Sect\])"
NEWS_CHAT = re.compile(rf"\[CHAT\]\s*{NEWS_PREFIX}\s*(.*?)\s*$")
CHAT = re.compile(r"\[CHAT\]\s*(.*?)\s*$")
# the ruin (world_sim.event.sect.ruin "... falls apart."), player_leave.sect_gone ("... is no more; ... rogue")
GONE_WORDS = r"sect_gone|覆灭|灭门|destroy|no longer|no more|falls apart|rogue|散修"
WAR_WORDS = r"war|宣战|开战|交战"
FAILED = re.compile(r"fail|失败|Unknown or incomplete|Incorrect argument|error|No sect|not active", re.IGNORECASE)
SPEAKS = re.compile(r"SectDialogue: (\S+) speaks with (.+?) \((\w+) of sect (-?\d+)\): \[(.*?)\] -> options \[(.*?)\]")
SECT_NEWS = re.compile(r"SECT_NEWS player=(\S+) event=(\d+) type=(\S+) sects=\[(.*?)\]")
COUNT = re.compile(r"Test passed, count: (\d+)")
REFUSE_AT_WAR = "refuse.at_war"

LOG_KEEP = ("GATE_REALIZE", "SECT_ENTRY", "SECT_DIALOGUE", "SECT_NEWS", "SectDialogue", "World sim",
            "WorldSimPlayers", "GateRealizer", "[CHAT]")


# ================================================================ pure helpers (tested)

def parse_news_chat(line: str) -> str | None:
    """The text after the sect news prefix of a client ``[CHAT]`` line (zh_cn 【宗门】 or en_us [Sect]); None for
    any other line."""
    m = NEWS_CHAT.search(line)
    return m.group(1) if m else None


def chat_text(line: str) -> str | None:
    m = CHAT.search(line)
    return m.group(1) if m else None


def is_gone_chat(line: str) -> bool:
    """A chat line telling of the destruction: a sect news line, or one with the ruin / sect_gone words."""
    text = chat_text(line)
    if text is None:
        return False
    return parse_news_chat(line) is not None or bool(re.search(GONE_WORDS, text, re.IGNORECASE))


def reply_ok(reply: str, words: str) -> bool:
    """An admin command's reply reports success: names `words` (case-insensitive) and no failure."""
    return bool(reply.strip()) and not FAILED.search(reply) and bool(re.search(words, reply, re.IGNORECASE))


def war_reply_ok(reply: str) -> bool:
    """``world sect <a> war <b>`` succeeded (``sect_war.done``: "A declared war on B (admin)")."""
    return reply_ok(reply, WAR_WORDS + r"|sect_war\.done")


def destroy_reply_ok(reply: str) -> bool:
    """``world sect <id> destroy`` succeeded (``sect_destroy.done``: "A is destroyed (admin); ...")."""
    return reply_ok(reply, r"destroy|覆灭|灭|sect_destroy\.done")


def is_rogue(record: str) -> bool:
    """``world player`` shows the player in no sect: the header line names a rogue cultivator."""
    first = record.strip().splitlines()[0] if record.strip() else ""
    return bool(re.search(r"rogue|散修", first, re.IGNORECASE))


def gone_lines(chronicle: str) -> list[str]:
    """Lines of ``world chronicle`` telling of a destroyed sect or a player it released."""
    rx = re.compile(GONE_WORDS, re.IGNORECASE)
    return [ln.strip() for ln in chronicle.splitlines() if rx.search(ln)]


def parse_speaks(line: str) -> dict[str, Any] | None:
    """``SectDialogue: <p> speaks with <name> (<role> of sect <id>): [<line keys>] -> options [<options>]``."""
    m = SPEAKS.search(line)
    if not m:
        return None
    split = lambda s: [x.strip() for x in s.split(",") if x.strip()]  # noqa: E731
    return {"player": m.group(1), "speaker": m.group(2), "role": m.group(3), "sect": int(m.group(4)),
            "lines": split(m.group(5)), "options": split(m.group(6))}


def parse_sect_news(line: str) -> dict[str, Any] | None:
    m = SECT_NEWS.search(line)
    if not m:
        return None
    return {"player": m.group(1), "event": int(m.group(2)), "type": m.group(3),
            "sects": [int(x) for x in re.findall(r"-?\d+", m.group(4))]}


def count_of(reply: str) -> int:
    """``execute if entity <selector>``: the count of 'Test passed, count: N', 0 for 'Test failed'."""
    m = COUNT.search(reply)
    return int(m.group(1)) if m else 0


def steward_index(avatars: list[dict[str, Any]], sect_name: str) -> int | None:
    """Index of the steward of `sect_name` among `avatar_names` entries (a steward tag without a readable sect only
    when no tagged steward of that sect is there)."""
    loose = None
    for i, a in enumerate(avatars):
        if not a["steward"]:
            continue
        if a["sect"] == sect_name:
            return i
        if a["sect"] is None and loose is None:
            loose = i
    return loose


def avatars_of(avatars: list[dict[str, Any]], sect_name: str) -> int:
    return sum(1 for a in avatars if a["sect"] == sect_name)


def choose_pair(rows: list[dict[str, Any]], want_a: int | None = None, want_b: int | None = None) \
        -> tuple[dict[str, Any], dict[str, Any]] | None:
    """Sect A and sect B from rows {id, name, gate: [x, z], built, at}: A has members at the gate, not built
    first, then the most members; B another sect with members at the gate (a steward), built first, then the
    farthest from A. `want_a` / `want_b` pin either."""
    cands_a = [r for r in rows if (want_a is None or r["id"] == want_a) and r.get("at", 0) > 0]
    if not cands_a:
        return None
    a = max(cands_a, key=lambda r: (not r["built"], r["at"], -r["id"]))
    others = [r for r in rows if r["id"] != a["id"] and (want_b is None or r["id"] == want_b)]
    if want_b is None:
        others = [r for r in others if r.get("at", 0) > 0]
    if not others:
        return None
    dist = lambda r: math.hypot(r["gate"][0] - a["gate"][0], r["gate"][1] - a["gate"][1])  # noqa: E731
    b = max(others, key=lambda r: (r["built"], dist(r), -r["id"]))
    return a, b


def site_box(gate: list[int] | tuple[int, int]) -> tuple[int, int, int, int]:
    """The compound's footprint (x, z, dx, dz) from the ledger gate anchor (SectGenerator: base = anchor -
    (32, 0, 90), 64 x 180)."""
    gx, gz = gate
    return gx - SITE_W // 2, gz - SITE_D // 2, SITE_W, SITE_D


def court_of(gate: list[int] | tuple[int, int]) -> tuple[float, float, float]:
    """The courtyard point behind the gate (the avatar evidence's)."""
    bx, bz, _, _ = site_box(gate)
    return bx + 31.5, float(SURFACE_Y), bz + 25.5


def render_index(evidence: dict[str, Any]) -> str:
    checks = evidence.get("checks", [])
    esc = lambda v: html.escape(scrub(str(v)))  # noqa: E731
    rows = "\n".join(f"<tr><td>{esc(r['check'])}</td><td class=\"{'ok' if r['pass'] else 'bad'}\">"
                     f"{'pass' if r['pass'] else 'FAIL'}</td><td>{esc(r['detail'])}</td></tr>"
                     for r in checks)
    facts = evidence.get("facts", {})
    shots = "\n".join(f"<figure><img src=\"{esc(x['file'])}\" alt=\"{esc(x['title'])}\" "
                      f"loading=\"lazy\"><figcaption>{esc(x['title'])}"
                      f"{'' if x.get('stable', True) else ' [UNSTABLE]'}</figcaption></figure>"
                      for x in facts.get("shots", []) if x.get("file"))
    missing = "\n".join(f"<li>{esc(x['title'])}: {esc(x.get('error', 'not captured'))}</li>"
                        for x in facts.get("shots", []) if not x.get("file"))
    passed = sum(1 for r in checks if r["pass"])
    notes = "\n".join(f"<li>{esc(n)}</li>" for n in facts.get("notes", []))
    a, b = facts.get("sect_a") or {}, facts.get("sect_b") or {}
    news = "\n".join(f"<li>{esc(n)}</li>" for n in facts.get("news_lines", []) + facts.get("destroy_lines", []))
    where = facts.get("news_player_pos")
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Sect news evidence</title>
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
<h1>World sim: sect news, hostile refusal, a destroyed sect</h1>
<p class="muted">Generated by <code>python3 tools/world_sim_news_evidence.py</code> on
{esc(evidence.get('finished', '?'))} (commit <code>{esc(evidence.get('commit', '?'))}</code>;
the working tree may hold uncommitted changes). Fresh superflat world, one client in creative, client language
en_us. Wars and the destruction are made with admin commands. Developer evidence only: how the news reads and
the refusal feels on a physical client are <strong>not_verified</strong>.</p>
<p>Own sect A #{esc(a.get('id', '?'))} {esc(a.get('name', '?'))}; enemy B #{esc(b.get('id', '?'))}
{esc(b.get('name', '?'))}. The player stood at {esc(where if where else '?')}, about
{esc(facts.get('news_distance_from_a', '?'))} blocks from A's gate, when the war news came.
Checks: {passed} pass, {len(checks) - passed} fail.</p>
<h2>Checks</h2>
<table><tr><th>check</th><th>result</th><th>detail</th></tr>
{rows}
</table>
<h2>Chat lines</h2>
<ul>{news}</ul>
<h2>Notes</h2>
<ul>{notes}</ul>
<h2>Screenshots</h2>
{shots}
{f'<h3>Not captured</h3><ul>{missing}</ul>' if missing else ''}
<h2>Files</h2>
<ul><li><a href="commands.txt">commands.txt</a> (every command and reply)</li>
<li><a href="server_log.txt">server_log.txt</a> (GATE_REALIZE, SECT_ENTRY, SECT_NEWS and dialogue lines)</li>
<li><a href="client_log.txt">client_log.txt</a> (SECT_DIALOGUE and chat lines)</li>
<li><a href="evidence.json">evidence.json</a></li></ul>
</body></html>
"""


# ================================================================ the session

class Run(tasks.Run):
    """The tasks script's session plumbing (rcon, tp, log waits, clicks, Escape, facing an avatar, dialogue
    buttons) with this script's output folder, log filter and steps."""

    def __init__(self, args: argparse.Namespace, results: list[dict[str, Any]]):
        super().__init__(args, results)
        self.t = Transcript(OUT / "commands.txt", "sect news, hostile refusal and destruction, capture transcript")
        self.facts = {"shots": [], "notes": [], "tick_samples": [], "dialogues": [], "entries": [],
                      "news_lines": [], "destroy_lines": [], "sects": []}
        self.a_row: dict[str, Any] = {}
        self.b_row: dict[str, Any] = {}

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

    # ------------------------------------------------------------ helpers
    def wait_chat(self, start: int, accept, timeout: float) -> list[str]:
        """Client ``[CHAT]`` lines from index `start` of the client log that `accept` takes; waits up to `timeout`
        for the first, then 1 s more for the rest."""
        deadline = time.time() + timeout
        while True:
            self.poll_logs()
            hits = [ln for ln in self.clog.lines[start:] if "[CHAT]" in ln and accept(ln)]
            if hits:
                time.sleep(1.0)
                self.poll_logs()
                return [ln for ln in self.clog.lines[start:] if "[CHAT]" in ln and accept(ln)]
            if time.time() >= deadline:
                return []
            time.sleep(0.25)

    def news_server_lines(self, start: int) -> list[dict[str, Any]]:
        self.poll_logs()
        return [p for p in (parse_sect_news(ln) for ln in self.slog.lines[start:]) if p and p["player"] == PLAYER]

    def advance_day(self, why: str) -> str:
        reply = self.rc(f"{W} advance 1", timeout=180.0)
        self.facts.setdefault("advances", []).append({"days": 1, "why": why, "reply": reply.strip()[:200]})
        return reply

    def realize_gate(self, label: str, row: dict[str, Any]) -> None:
        """Stand `--distance` blocks in front of the sect's ledger gate and wait for its GATE_REALIZE done; the
        synchronous build as the fallback. One check `<label>_gate_ready`."""
        sid, (gx, gz) = row["id"], row["gate"]
        d = self.a.distance
        self.t.note(f"realize {label}: the player stands {d} blocks in front of the ledger gate of #{sid}")
        self.tp(gx + 0.5, SURFACE_Y, gz - d + 0.5, 0.0, -4.0, settle=1.0)
        if row["built"]:
            self.check(f"{label}_gate_ready", True, f"#{sid}: the gate was already built when the run started")
            return
        t0 = time.time()
        self.poll_logs()
        early = realize_summary(self.server_lines, sid)  # B may have finished while the news step ran
        state = "done" if early["done"] else ("failed" if early["failed"] else None)
        while state is None and time.time() < t0 + self.a.realize_timeout:
            self.poll_logs()
            for line in self.slog.lines[self.slog.cursor:]:
                p = parse_gate_realize(line)
                if p and p["sect"] == sid and p["state"] in ("done", "failed"):
                    state = p["state"]
            self.slog.cursor = len(self.slog.lines)
            if state:
                break
            time.sleep(0.5)
        summ = realize_summary(self.server_lines, sid)
        summ["wall_seconds_waited"] = round(time.time() - t0, 1)
        self.facts.setdefault("realize", {})[label] = summ
        detail = (f"#{sid}: state={state or 'timeout'} after {summ['wall_seconds_waited']} s; started→done "
                  f"{summ['seconds']} s (log), {summ['clips_logged']} clip lines")
        if state != "done":
            self.facts.setdefault("realize_fallback", []).append(label)
            self.note(f"fallback for {label}: the gate is built synchronously with `world sect {sid} build`")
            built = self.rc(f"{W} sect {sid} build", timeout=900.0)
            detail += " | fallback: " + next((x for x in built.splitlines() if x.startswith("Built")),
                                             built.strip()[:200])
        gate = GATE.search(self.rc(f"{W} sect {sid}"))
        ok = bool(gate and gate.group(3) == "(built)")
        row["built"] = ok
        self.check(f"{label}_gate_ready", ok, detail + (f" | ledger: {gate.group(0)}" if gate else " | no gate line"))

    def find_steward_of(self, row: dict[str, Any], timeout: float = 60.0) -> str | None:
        """UUID of the steward avatar of the sect in `row` (its name tag names the sect)."""
        named = f"execute as {CULT} if data entity @s CustomName run data get entity @s"
        deadline = time.time() + timeout
        while True:
            avatars = avatar_names(self.rc(f"{named} CustomName"))
            i = steward_index(avatars, row["name"])
            if i is not None:
                uuids = [tuple(int(v) for v in u.groups()) for u in UUID.finditer(self.rc(f"{named} UUID"))]
                if i < len(uuids):
                    return uuid_from_ints(uuids[i])
            if time.time() >= deadline:
                return None
            time.sleep(3.0)

    def count_avatars(self, row: dict[str, Any]) -> dict[str, int]:
        """Cultivators in the compound's footprint, and loaded avatars whose name tag names the sect."""
        bx, bz, dx, dz = site_box(row["gate"])
        site = count_of(self.rc(f"execute if entity @e[type=myvillage:cultivator,x={bx},y={SURFACE_Y - 8},"
                                f"z={bz},dx={dx},dy=72,dz={dz}]"))
        named = f"execute as {CULT} if data entity @s CustomName run data get entity @s"
        tagged = avatars_of(avatar_names(self.rc(f"{named} CustomName")), row["name"])
        return {"site": site, "tagged": tagged}

    def distance_from(self, pos: tuple[float, float, float] | None, gate: list[int]) -> float | None:
        return round(math.hypot(pos[0] - gate[0], pos[2] - gate[1]), 1) if pos else None

    # ------------------------------------------------------------ steps
    def pick_sects(self) -> None:
        sects = self.rc(f"{W} sects")
        rows = {r["id"]: r for r in parse_sects(sects)}
        for m in SECT_LINE.finditer(sects):
            sid = int(m.group(1))
            reply = self.rc(f"{W} sect {sid}")
            gate = GATE.search(reply)
            at = AT_SECT.search(reply)
            row = rows.setdefault(sid, {"id": sid, "name": m.group(2), "gate": None, "built": False})
            if gate:
                row["gate"] = [int(gate.group(1)), int(gate.group(2))]
                row["built"] = gate.group(3) == "(built)"
            row["at"] = int(at.group(1)) if at else 0
        usable = [r for r in rows.values() if r.get("gate")]
        self.facts["sects"] = usable
        pair = choose_pair(usable, self.a.sect_a, self.a.sect_b)
        if pair is None:
            raise RuntimeError("no two active sects (A with members at its gate, B another one with members at its "
                               "gate)" + (f" (asked for A #{self.a.sect_a}, B #{self.a.sect_b})"
                                          if self.a.sect_a is not None or self.a.sect_b is not None else ""))
        self.a_row, self.b_row = dict(pair[0]), dict(pair[1])
        self.sect_id = self.a_row["id"]
        self.court = court_of(self.a_row["gate"])
        self.facts.update({"sect_a": self.a_row, "sect_b": self.b_row, "sect_id": self.sect_id,
                           "sect_name": self.a_row["name"]})
        log(f"A #{self.a_row['id']} {self.a_row['name']} gate={self.a_row['gate']} built={self.a_row['built']}; "
            f"B #{self.b_row['id']} {self.b_row['name']} gate={self.b_row['gate']} built={self.b_row['built']}")

    def join_step(self) -> None:
        self.t.note("join: admin join of sect A")
        self.mark()
        reply = self.rc(f"{W} sect {self.sect_id} join {PLAYER}")
        e = self.wait_entry("JOIN")
        self.check("admin_join_a", bool(e and e["result"] == "ok" and e["sect"] == self.sect_id),
                   f"{json.dumps(e)} | {reply.strip()[:200]}")
        self.record("player_after_join")

    def news_step(self) -> None:
        a, b = self.a_row, self.b_row
        gx, gz = b["gate"]
        self.t.note(f"news: the player stands {self.a.distance} blocks in front of B's gate, far from A; "
                    f"A declares war on B; the 【宗门】/[Sect] chat line is awaited (at once, then after advance 1)")
        self.tp(gx + 0.5, SURFACE_Y, gz - self.a.distance + 0.5, 0.0, -4.0, settle=2.0)
        pos = self.player_pos()
        self.facts["news_player_pos"] = [round(v, 1) for v in pos] if pos else None
        self.facts["news_distance_from_a"] = self.distance_from(pos, a["gate"])
        self.mark()
        c0, s0 = len(self.clog.lines), len(self.slog.lines)
        reply = self.rc(f"{W} sect {a['id']} war {b['id']}")
        already = "already_at_war" in reply
        if already:
            self.note("A and B were already at war (the ledger's own politics): no war event from the command; "
                      "the news can only come from a settled day")
        self.check("war_declared", war_reply_ok(reply) or already, reply.strip()[:200])
        lines = self.wait_chat(c0, lambda ln: parse_news_chat(ln) is not None, self.a.news_timeout)
        when = "at once after the war command"
        if not lines:
            self.note("no sect news line right after the war command: one settled day (`world advance 1`)")
        reply = self.advance_day("settle a day after the war")
        if not lines:
            when = "after `world advance 1`"
            lines = self.wait_chat(c0, lambda ln: parse_news_chat(ln) is not None, self.a.news_timeout)
        texts = [parse_news_chat(ln) or "" for ln in lines]
        self.facts["news_lines"] = texts
        self.facts["news_when"] = when if texts else None
        self.facts["news_server"] = self.news_server_lines(s0)
        war_named = [t for t in texts if re.search(WAR_WORDS, t, re.IGNORECASE)
                     or a["name"] in t or b["name"] in t]
        self.check("sect_news_delivered", bool(texts),
                   (f"{len(texts)} line(s) {when}, the player {self.facts['news_distance_from_a']} blocks from A's "
                    f"gate at {self.facts['news_player_pos']}; first: {texts[0]}"
                    + ("" if war_named else " (no line names the war or the two sects)"))
                   if texts else f"no 【宗门】/[Sect] chat line within {self.a.news_timeout:.0f} s after the war "
                                 f"command nor after `world advance 1` ({reply.strip()[:120]})")
        if texts:
            self.shot("news_chat", "in game far from the own sect: the 【宗门】/[Sect] war news in the chat")
            self.chat_screen_shot("news_chat_open", "the chat screen (T): the sect news line of the war")

    def chat_screen_shot(self, name: str, title: str) -> None:
        try:
            self.s.game.key("t")
        except Exception as exc:  # noqa: BLE001
            self.facts["shots"].append({"file": None, "title": title, "error": str(exc)})
            return
        time.sleep(1.0)
        self.shot(name, title, screen_open=True)
        self.close_screen()

    def hostile_step(self) -> None:
        b = self.b_row
        self.realize_gate("b", b)
        self.t.note("hostile: B's steward, right click: only FAREWELL (at war with the player's sect)")
        self.tp(*court_of(b["gate"]), 180.0, 10.0, settle=3.0)
        uid = self.find_steward_of(b)
        self.check("b_steward_present", uid is not None,
                   f"steward {uid}" if uid else f"no steward of {b['name']} within 60 s")
        if not uid:
            return
        s0 = len(self.slog.lines)
        opts = self.open_dialogue("hostile", uid=uid, settle=2.0)
        self.poll_logs()
        speaks = [p for p in (parse_speaks(ln) for ln in self.slog.lines[s0:])
                  if p and p["player"] == PLAYER and p["sect"] == b["id"]]
        last = speaks[-1] if speaks else None
        self.facts["hostile_dialogue"] = {"client_options": sorted(opts), "server": last}
        server_only_farewell = bool(last and last["options"] == ["FAREWELL"])
        self.check("hostile_steward_refuses", bool(opts) and set(opts) == {"FAREWELL"} and server_only_farewell,
                   f"client options {sorted(opts)} (2 s settle, no JOIN); server: "
                   + (f"{last['role']} {last['speaker']} lines {last['lines']} -> options {last['options']}"
                      if last else "no `SectDialogue: ... speaks with` line for B"))
        self.check("hostile_reason_at_war", bool(last and any(REFUSE_AT_WAR in x for x in last["lines"])),
                   f"line keys {last['lines'] if last else None} (want ...{REFUSE_AT_WAR})")
        if opts:
            self.shot("hostile_refused", f"the steward of the enemy sect {b['name']}: refused, only farewell",
                      screen_open=True)
            self.close_screen()

    def destroy_step(self) -> None:
        a = self.a_row
        self.t.note("destroy: back to A's courtyard, A's avatars counted, `world sect <A> destroy`")
        self.tp(*court_of(a["gate"]), 180.0, 10.0, settle=3.0)
        uid = self.find_steward_of(a, timeout=40.0)
        before = self.count_avatars(a)
        self.facts["avatars_before_destroy"] = before
        self.facts["a_steward_before_destroy"] = uid
        self.mark()
        c0 = len(self.clog.lines)
        reply = self.rc(f"{W} sect {a['id']} destroy")
        t_destroy = time.time()
        self.check("sect_destroyed", destroy_reply_ok(reply), reply.strip()[:200])
        rec = self.record("player_after_destroy")
        self.check("player_now_rogue", is_rogue(rec), rec.strip()[:200])
        chron = self.rc(f"{W} chronicle 5")
        hits = gone_lines(chron)
        self.facts["chronicle_after_destroy"] = chron
        self.check("chronicle_sect_gone", bool(hits), hits[0] if hits else "no chronicle line tells of it")
        lines = self.wait_chat(c0, is_gone_chat, self.a.news_timeout)
        when = "at once after the destroy command"
        if not lines:
            when = "after `world advance 1`"
            self.note("no chat line right after the destroy command: one settled day (`world advance 1`)")
            self.advance_day("settle a day after the destruction")
            lines = self.wait_chat(c0, is_gone_chat, self.a.news_timeout)
        texts = [chat_text(ln) or "" for ln in lines]
        self.facts["destroy_lines"] = texts
        self.check("destroy_news_delivered", bool(texts),
                   f"{len(texts)} line(s) {when}: " + " | ".join(texts[:3]) if texts
                   else f"no chat line of the destruction within {self.a.news_timeout:.0f} s (twice)")
        if texts:
            self.chat_screen_shot("destroy_chat_open", "the chat screen (T): the destruction and the player's release")
        deadline = time.time() + self.a.withdraw_timeout
        after = self.count_avatars(a)
        while (after["site"] > 0 or after["tagged"] > 0) and time.time() < deadline:
            time.sleep(1.0)
            after = self.count_avatars(a)
        self.facts["avatars_after_destroy"] = after
        gone = after["tagged"] == 0 and (after["site"] == 0 or after["site"] < before["site"])
        self.check("avatars_withdrawn_after_destroy", gone,
                   f"A's avatars: site {before['site']} -> {after['site']}, name-tagged {before['tagged']} -> "
                   f"{after['tagged']} ({round(time.time() - t_destroy, 1)} s after the command; "
                   f"withdraw timeout {self.a.withdraw_timeout:.0f} s from the last chat wait)")
        self.shot("destroyed_court", "A's courtyard after the destruction: the avatars withdrawn")

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
            self.pick_sects()
            for name, fn in (("realize_a", lambda: self.realize_gate("a", self.a_row)), ("join", self.join_step),
                             ("news", self.news_step), ("hostile", self.hostile_step),
                             ("destroy", self.destroy_step)):
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
    ap.add_argument("--sect-a", type=int, default=None,
                    help="the player's sect (default: members at the gate, gate not built, most members)")
    ap.add_argument("--sect-b", type=int, default=None,
                    help="the enemy sect (default: members at the gate, built first, then farthest from A)")
    ap.add_argument("--distance", type=int, default=140, help="blocks from a ledger gate for the auto build")
    ap.add_argument("--realize-timeout", type=float, default=600.0, help="seconds to wait for GATE_REALIZE done")
    ap.add_argument("--news-timeout", type=float, default=10.0, help="seconds to wait for a chat line, per try")
    ap.add_argument("--withdraw-timeout", type=float, default=10.0,
                    help="seconds for A's avatars to be withdrawn after the destruction")
    ap.add_argument("--dialogue-scale", type=float, default=1.0,
                    help="multiply SECT_DIALOGUE coordinates (1: logged in screen pixels; 2: logged in GUI px)")
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
