"""In-game evidence for the player's movement dodge (身法闪避, 0.40.0) against a
beast (default myvillage:demon_wolf) in the capture session. Re-runnable with
one command:

    python3 -m tools.combat_capture dodge            # starts and stops its own session

Scene (as `beast dodge`): a survival player with no armour, no effects and
naturalRegeneration off, holding the Qingfeng sword in cultivation combat
mode, who has learned the movement technique (default
myvillage:taxue_wuhen, through `/myvillage cultivation learn`). The dodge key
is the default Left Alt; a left dodge holds A first, no direction is the
backstep.

Trials, all at /tick rate 5 (client and server slow down together):

- per beast move, against a fresh beast forced into it with
  `/myvillage beast move`: no dodge; a backstep pressed when the polled
  `/myvillage beast status` first shows each planned move tick; a left dodge
  at one tick (see DODGE_TRIALS);
- cooldown: no beast, two presses about COOLDOWN_GAP_TICKS game ticks apart
  (the second should be `rejected reason=COOLDOWN`);
- recovery cancel: one sword swing and a press at action tick
  TIMING_ACTION_TICK (should be `rejected reason=TIMING`), then another swing
  and a press two ticks after the first move's last active tick (should be
  `started`).

Each trial keeps health before and after, how far the player moved, the
server's DODGE_DEBUG lines and the beast's hits on the player
(BEAST_DEBUG ... hit minecraft:player ... accepted=). One F5-back video per
beast move. Output: out/preview/movement_dodge/ (index.html, manifest.json,
video/, dodge_log.txt).
"""
from __future__ import annotations

import html
import json
import math
import re
import time
from pathlib import Path

from . import scene
from .beast import TAG as TAG_BEAST
from .beast import HIT_LINE, BeastCapture, parse_float, parse_status, short_id, summon
from .capture import press_f5_to
from .data import load_weapon
from .session import LOG_DIR, REPO, Session

RESOURCES = REPO / "src/main/resources"
DEFAULT_BEAST = "myvillage:demon_wolf"
DEFAULT_TECHNIQUE = "myvillage:taxue_wuhen"
DEFAULT_OUT = REPO / "out/preview/movement_dodge"
SWORD = "myvillage:qingfeng_sword"
PITCH = 35.0
TRIAL_RATE = 5  # /tick rate during a trial; a tick then lasts 0.2 s
LEFT_LEAD_TICKS = 2  # A goes down this many move ticks before Alt, so the client samples it first
LEFT_HOLD_SECONDS = 0.3  # A stays down this long after Alt
COOLDOWN_GAP_TICKS = 5
TIMING_ACTION_TICK = 3
FALLBACK_COOLDOWN_TICKS = 30  # used for the waits when the technique file is not there

# move id -> beast distance ahead of the player, backstep move ticks, left dodge move tick
DODGE_TRIALS = {
    "myvillage:demon_wolf_bite": {"distance": 2.9, "back": (4, 7, 9, 10), "left": 9},
    "myvillage:demon_wolf_pounce": {"distance": 6.0, "back": (14, 17, 18, 19), "left": 18},
}
GAMETIME_REPLY = re.compile(r"The time is (-?\d+)")
DODGE_FIELD = re.compile(r"(\w+)=(\S+)")


# ------------------------------------------------------------------ pure helpers
def trial_ticks(move: dict) -> dict:
    """Distance and dodge ticks for one beast move: DODGE_TRIALS, else derived
    from its data (backsteps 6, 3 and 1 ticks before the first active tick and
    on it, the left dodge 1 tick before; distance the hit's reach for a
    close-range move, else the middle of its use_range)."""
    if move["id"] in DODGE_TRIALS:
        return dict(DODGE_TRIALS[move["id"]])
    first = move["active_ticks"][0]
    low, high = move["use_range"]
    distance = min(move["hit"]["forward"][1], high) if low <= 0.0 else (low + high) / 2.0
    back = tuple(sorted({max(0, first - k) for k in (6, 3, 1, 0)}))
    return {"distance": round(distance, 2), "back": back, "left": max(0, first - 1)}


def beast_trials(move: dict) -> list[dict]:
    """The trials of one beast move: no dodge, a backstep per planned tick, one left dodge."""
    plan = trial_ticks(move)
    name = short_id(move["id"])
    base = {"kind": "beast", "move": move["id"], "distance": plan["distance"]}
    trials = [dict(base, name=f"{name}: no dodge", at=None, direction=None)]
    trials += [dict(base, name=f"{name}: backstep at move tick {t}", at=t, direction="back") for t in plan["back"]]
    trials.append(dict(base, name=f"{name}: left dodge at move tick {plan['left']}", at=plan["left"],
                       direction="left"))
    return trials


def recovery_ticks(sword_move) -> dict:
    """Action ticks for the recovery-cancel trials on one sword move (a
    data.Move): a press inside the strike (TIMING_ACTION_TICK, must not be past
    the last active tick) and a press two ticks after the last active tick
    (must be before the move ends)."""
    active_end = int(sword_move.active_ticks[1])
    total = int(sword_move.total_ticks)
    cancel = active_end + 2
    if TIMING_ACTION_TICK > active_end:
        raise ValueError(f"{sword_move.id}: action tick {TIMING_ACTION_TICK} is past the last active tick {active_end}")
    if cancel >= total:
        raise ValueError(f"{sword_move.id}: action tick {cancel} is not before the move ends ({total} ticks)")
    return {"move": sword_move.id, "active_end": active_end, "total": total, "timing": TIMING_ACTION_TICK,
            "cancel": cancel}


def trial_plan(moves: list[dict], sword_move) -> list[dict]:
    """Every trial in run order, with what each should show (`expect`)."""
    plan = [t for move in moves for t in beast_trials(move)]
    plan.append({"kind": "cooldown", "name": f"cooldown: two backsteps {COOLDOWN_GAP_TICKS} ticks apart",
                 "gap": COOLDOWN_GAP_TICKS, "expect": "started, then rejected COOLDOWN"})
    rec = recovery_ticks(sword_move)
    plan.append({"kind": "recovery", "name": f"swing, backstep at action tick {rec['timing']} (strike)",
                 "move": rec["move"], "at": rec["timing"], "expect": "rejected TIMING"})
    plan.append({"kind": "recovery", "name": f"swing, backstep at action tick {rec['cancel']} "
                 f"(recovery, last active tick {rec['active_end']})", "move": rec["move"], "at": rec["cancel"],
                 "expect": "started"})
    return plan


def technique_movement(resources: Path, technique: str) -> dict | None:
    """The `effects.movement` of a technique data file, or None if absent."""
    ns, path = technique.split(":", 1)
    p = Path(resources) / f"data/{ns}/myvillage/technique/{path}.json"
    if not p.is_file():
        return None
    return json.loads(p.read_text(encoding="utf-8")).get("effects", {}).get("movement")


def parse_gametime(reply: str):
    m = GAMETIME_REPLY.search(reply)
    return int(m.group(1)) if m else None


def parse_dodge_line(line: str) -> dict | None:
    """One server DODGE_DEBUG line -> {kind: started|rejected|cancelled_damage,
    t, player, and the line's other fields}; None for any other line."""
    if "DODGE_DEBUG " not in line:
        return None
    fields = dict(DODGE_FIELD.findall(line.split("DODGE_DEBUG ", 1)[1]))
    if "cancelled_damage" in fields:
        kind = "cancelled_damage"
    elif fields.get("result") in ("started", "rejected"):
        kind = fields["result"]
    else:
        return None
    event = {"kind": kind, **fields}
    try:
        event["t"] = int(fields["t"])
    except (KeyError, ValueError):
        event["t"] = None
    return event


def dodge_events(lines: list[str]) -> list[dict]:
    return [e for e in (parse_dodge_line(l) for l in lines) if e]


def press_events(events: list[dict]) -> list[dict]:
    """The started/rejected events (one per key press the server saw)."""
    return [e for e in events if e["kind"] in ("started", "rejected")]


def player_hits(lines: list[str]) -> list[dict]:
    """The beast's hits on the player from its BEAST_DEBUG lines."""
    hits = []
    for line in lines:
        m = HIT_LINE.search(line)
        if m and m.group(3) == "minecraft:player":
            hits.append({"beast": int(m.group(1)), "t": int(m.group(2)), "move": m.group(4),
                         "move_tick": int(m.group(5)), "damage": float(m.group(6)), "accepted": m.group(7) == "true",
                         "hp": [float(m.group(8)), float(m.group(9))]})
    return hits


def verdict(events: list[dict], hits: list[dict]) -> dict:
    """bitten: a hit on the player was accepted; dodged: the server started a
    dodge; cancelled_damage: the dodge's window cancelled incoming damage."""
    return {"bitten": any(h["accepted"] for h in hits),
            "dodged": any(e["kind"] == "started" for e in events),
            "cancelled_damage": any(e["kind"] == "cancelled_damage" for e in events)}


def press_result(event: dict | None) -> str:
    if event is None:
        return "none"
    return "started" if event["kind"] == "started" else f"rejected {event.get('reason', '?')}"


def expectation_met(trial: dict, events: list[dict]):
    """True/False against the trial's `expect`; None for a trial without one."""
    presses = press_events(events)
    if trial["kind"] == "cooldown":
        return (len(presses) >= 2 and presses[0]["kind"] == "started"
                and presses[1]["kind"] == "rejected" and presses[1].get("reason") == "COOLDOWN")
    if trial["kind"] == "recovery":
        return bool(presses) and press_result(presses[0]) == trial["expect"]
    return None


def landed_move_tick(press_move_tick, press_gametime, dodge_t):
    """Beast move tick when the server handled the press: the tick seen when
    Alt went down plus the game ticks between that poll and the DODGE_DEBUG
    line (the beast's move tick advances with game time)."""
    if press_move_tick is None or press_gametime is None or dodge_t is None:
        return None
    return press_move_tick + (dodge_t - press_gametime)


def planar_distance(a, b):
    if not a or not b:
        return None
    return round(math.hypot(b[0] - a[0], b[2] - a[2]), 2)


def video_seconds(trials: int, move_ticks: int, cooldown_ticks: int, rate: int = TRIAL_RATE) -> float:
    """Recording length for one beast move's trials: setup, the cooldown wait
    at /tick rate 20, the move at `rate`, the log catching up."""
    per_trial = 4.0 + cooldown_ticks / 20.0 + move_ticks / rate + 3.0
    return round(trials * per_trial + 6.0, 1)


# ------------------------------------------------------------------ session steps
class DodgeCapture(BeastCapture):
    def __init__(self, session: Session, beast: str, technique: str, out: Path, log=print):
        super().__init__(session, beast, out, log=log)
        self.technique = technique
        self.movement = technique_movement(RESOURCES, technique)
        self.cooldown_ticks = int((self.movement or {}).get("cooldown_ticks", FALLBACK_COOLDOWN_TICKS))
        self.sword = load_weapon(RESOURCES, SWORD, require_rig=False)
        self.manifest = {"beast": beast, "technique": technique, "movement": self.movement, "rate": TRIAL_RATE,
                         "videos": [], "notes": [], "trials": []}
        self.server_log = LOG_DIR / "server.log"
        self.log_text: list[str] = []
        self.y = scene.FLOOR_Y

    # -------------------------------------------------------------- plumbing
    def log_mark(self) -> int:
        return self.server_log.stat().st_size if self.server_log.is_file() else 0

    def log_since(self, mark: int) -> list[str]:
        if not self.server_log.is_file():
            return []
        with open(self.server_log, "rb") as f:
            f.seek(mark)
            text = f.read().decode("utf-8", "replace")
        return [l for l in text.splitlines() if "DODGE_DEBUG" in l or "BEAST_DEBUG" in l]

    def gametime(self, r=None):
        reply = r.cmd("time query gametime") if r else self.run("time query gametime", warn=False)[0]
        return parse_gametime(reply)

    def health(self):
        return parse_float(self.run(f"data get entity {self.user} Health", warn=False)[0])

    def alt(self):
        self.g.require_ingame("dodge")
        self.g.xdo("key", "Alt_L")

    def wait_gametime(self, target: int, timeout: float = 20.0):
        deadline = time.time() + timeout
        with self.s.rcon() as r:
            while time.time() < deadline:
                gt = self.gametime(r)
                if gt is not None and gt >= target:
                    return gt
                time.sleep(0.02)
        raise RuntimeError(f"game time did not reach {target} within {timeout:g}s")

    def ready(self):
        """World running at rate 20 with the last dodge's cooldown over; the
        player back at the start, healed."""
        self.run("tick rate 20", "tick unfreeze", warn=False)
        self.bury()
        self.run(f"tp {self.user} 0.5 {self.y} 0.5 0 {PITCH:g}",
                 f"effect give {self.user} minecraft:instant_health 1 10 true", warn=False)
        time.sleep(self.cooldown_ticks / 20.0 + 0.6)

    def setup(self):
        self.setup_world()
        u = self.user
        self.run(f"gamemode survival {u}", "gamerule naturalRegeneration false",
                 f"tp {u} 0.5 {self.y} 0.5 0 {PITCH:g}", f"item replace entity {u} weapon.mainhand with {SWORD}",
                 f"effect give {u} minecraft:instant_health 1 10 true", warn=False)
        learn = self.run(f"myvillage cultivation learn {u} {self.technique}", warn=False)[0].strip()
        self.manifest["notes"].append(f"learn {self.technique}: {learn[:200] or '(no reply)'}")
        self.manifest["notes"].append("survival, no armour, no effects but saturation, naturalRegeneration false, "
                                      f"Qingfeng sword in hand, healed before each trial; trials at /tick rate {TRIAL_RATE}")
        if self.movement is None:
            self.manifest["notes"].append(f"no technique data for {self.technique} in the source tree; waits use a "
                                          f"cooldown of {FALLBACK_COOLDOWN_TICKS} ticks")
        time.sleep(0.8)
        press_f5_to(self.s, "first")
        self.manifest["notes"].append(f"combat mode: {scene.ensure_cultivation(self.s)}")
        press_f5_to(self.s, "back")

    def keep(self, trial: dict, lines: list[str], extra: dict, samples=()):
        events = dodge_events(lines)
        hits = player_hits(lines)
        presses = press_events(events)
        entry = {"trial": trial["name"], "kind": trial["kind"], **{k: trial[k] for k in ("move", "at", "direction",
                                                                                          "distance", "expect")
                                                                    if k in trial},
                 **extra, "dodge_events": events, "player_hits": hits, "result": press_result(presses[0] if presses
                                                                                              else None),
                 **verdict(events, hits), "expectation_met": expectation_met(trial, events)}
        hp0, hp1 = entry.get("hp_before"), entry.get("hp_after")
        entry["hp_kept"] = None if hp0 is None or hp1 is None else hp1 >= hp0
        self.manifest["trials"].append(entry)
        self.log_text += [f"## {trial['name']}", *lines,
                          *(f"  poll s={s} move_tick={tick} gametime={gt}" for s, tick, gt in samples), ""]
        self.log(f"  {trial['name']}: {entry['result']}, hp {hp0} -> {hp1}, bitten {entry['bitten']}, "
                 f"cancelled_damage {entry['cancelled_damage']}, moved {entry.get('player_moved')}")

    # -------------------------------------------------------------- trials
    def beast_trial(self, trial: dict):
        mark = self.log_mark()
        self.ready()
        self.run("tick freeze")
        self.remove(TAG_BEAST)
        self.run(f"tp {self.user} 0.5 {self.y} 0.5 0 {PITCH:g}")
        time.sleep(0.6)
        self.run(summon(self.beast, (0.5, self.y, 0.5 + trial["distance"]), 180.0), "tick step 2")
        time.sleep(0.5)
        hp_before = self.health()
        start_pos = self.pos(None)
        self.run(f"damage {self.sel()} 0.5 minecraft:player_attack by {self.user}",
                 f"myvillage beast move {self.sel()} {trial['move']}", f"tick rate {TRIAL_RATE}", "tick unfreeze")
        at, left = trial["at"], trial["direction"] == "left"
        held = False
        release_at = None
        press = {"press_move_tick": None, "press_gametime": None}
        samples = []
        seen = False
        t0 = time.time()
        try:
            with self.s.rcon() as r:
                while time.time() - t0 < 30:
                    st = parse_status(r.cmd(f"myvillage beast status {self.sel()}"))
                    gt = self.gametime(r)
                    moving = st.get("move", "none") != "none"
                    tick = int(st.get("tick", "-1")) if moving else -1
                    if moving:
                        seen = True
                        samples.append((round(time.time() - t0, 3), tick, gt))
                    elif seen:
                        break
                    if at is not None and seen:
                        if left and not held and press["press_move_tick"] is None and tick >= at - LEFT_LEAD_TICKS:
                            self.g.require_ingame("strafe")
                            self.g.xdo("keydown", "a")
                            held = True
                        if press["press_move_tick"] is None and tick >= at:
                            self.alt()
                            press = {"press_move_tick": tick, "press_gametime": gt}
                            release_at = time.time() + LEFT_HOLD_SECONDS
                    if held and release_at is not None and time.time() >= release_at:
                        self.g.xdo("keyup", "a")
                        held = False
                    time.sleep(0.02)
        finally:
            if held:
                self.g.xdo("keyup", "a")
            self.run("tick freeze", "tick rate 20")
        time.sleep(0.6)  # let the server log catch up
        hp_after = self.health()
        end_pos = self.pos(None)
        lines = self.log_since(mark)
        started = next((e for e in dodge_events(lines) if e["kind"] in ("started", "rejected")), None)
        extra = {"hp_before": hp_before, "hp_after": hp_after, "player_moved": planar_distance(start_pos, end_pos),
                 **press, "landed_move_tick": landed_move_tick(press["press_move_tick"], press["press_gametime"],
                                                               started["t"] if started else None)}
        self.keep(trial, lines, extra, samples)
        self.remove(TAG_BEAST)
        self.run("tick unfreeze")
        self.bury()

    def cooldown_trial(self, trial: dict):
        mark = self.log_mark()
        self.ready()
        self.remove(TAG_BEAST)
        hp_before = self.health()
        start_pos = self.pos(None)
        self.run(f"tick rate {TRIAL_RATE}")
        try:
            g1 = self.gametime()
            self.alt()
            g2 = self.wait_gametime(g1 + trial["gap"])
            self.alt()
            time.sleep(1.5)
        finally:
            self.run("tick rate 20")
        time.sleep(0.4)
        extra = {"hp_before": hp_before, "hp_after": self.health(),
                 "player_moved": planar_distance(start_pos, self.pos(None)), "press_gametimes": [g1, g2]}
        self.keep(trial, self.log_since(mark), extra)

    def recovery_trial(self, trial: dict):
        """One swing (a real left click), then Alt once the game time is
        `at` ticks past the click. The click and the press each reach the
        server within about a tick, so the action tick lands within one of `at`."""
        mark = self.log_mark()
        self.ready()
        self.remove(TAG_BEAST)
        # The previous swing's combo must have timed out, or this click would chain into its next move.
        time.sleep((self.sword.combo_timeout_ticks + self.sword.moves[0].total_ticks) / 20.0)
        hp_before = self.health()
        start_pos = self.pos(None)
        self.run(f"tick rate {TRIAL_RATE}")
        try:
            g_click = self.gametime()
            self.g.require_ingame("swing")
            self.g.click()
            g_press = self.wait_gametime(g_click + trial["at"])
            self.alt()
            time.sleep(1.5 + self.sword.moves[0].total_ticks / TRIAL_RATE)
        finally:
            self.run("tick rate 20")
        time.sleep(0.4)
        lines = self.log_since(mark)
        first = next(iter(press_events(dodge_events(lines))), None)
        extra = {"hp_before": hp_before, "hp_after": self.health(),
                 "player_moved": planar_distance(start_pos, self.pos(None)), "click_gametime": g_click,
                 "press_gametime": g_press,
                 "ticks_click_to_dodge": (first["t"] - g_click) if first and first["t"] is not None else None}
        self.keep(trial, lines, extra)

    def run_trials(self):
        plan = trial_plan(self.data["moves"], self.sword.moves[0])
        self.manifest["plan"] = plan
        for move in self.data["moves"]:
            mine = [t for t in plan if t["kind"] == "beast" and t["move"] == move["id"]]
            name = short_id(move["id"])
            seconds = video_seconds(len(mine), move["total_ticks"], self.cooldown_ticks)

            def act(total, mine=mine):
                for t in mine:
                    self.beast_trial(t)

            self.log(f"{move['id']}: {len(mine)} trials")
            self.record(f"dodge_{name}", seconds, f"{move['id']}: no dodge, backsteps at move ticks "
                        f"{', '.join(str(t['at']) for t in mine if t['direction'] == 'back')}, left dodge at "
                        f"{next((t['at'] for t in mine if t['direction'] == 'left'), '-')} "
                        f"(/tick rate {TRIAL_RATE}, F5 back)", act)
        for t in plan:
            if t["kind"] == "cooldown":
                self.cooldown_trial(t)
            elif t["kind"] == "recovery":
                self.recovery_trial(t)

    def finish(self):
        self.out.mkdir(parents=True, exist_ok=True)
        (self.out / "dodge_log.txt").write_text(
            "# server DODGE_DEBUG and BEAST_DEBUG lines per trial, with the polled beast move tick and game time\n"
            + "\n".join(self.log_text) + "\n", encoding="utf-8")
        self.manifest["summary"] = summarize(self.manifest["trials"])
        (self.out / "manifest.json").write_text(json.dumps(self.manifest, indent=2), encoding="utf-8")
        write_page(self.out, self.manifest)



def summarize(trials: list[dict]) -> dict:
    """Per kind: trials, dodges started, hits taken, damage cancelled, and how
    many trials with an expectation met it."""
    out = {}
    for t in trials:
        key = short_id(t["move"]) if t["kind"] == "beast" else t["kind"]
        s = out.setdefault(key, {"trials": 0, "started": 0, "bitten": 0, "cancelled_damage": 0, "hp_kept": 0,
                                 "expected": 0, "met": 0})
        s["trials"] += 1
        s["started"] += int(t.get("dodged", False))
        s["bitten"] += int(t.get("bitten", False))
        s["cancelled_damage"] += int(t.get("cancelled_damage", False))
        s["hp_kept"] += int(bool(t.get("hp_kept")))
        if t.get("expectation_met") is not None:
            s["expected"] += 1
            s["met"] += int(t["expectation_met"])
    return out


def _cell(v) -> str:
    if v is None:
        return "-"
    if isinstance(v, bool):
        return "yes" if v else "no"
    return html.escape(str(v))


def write_page(out: Path, manifest: dict) -> None:
    vids = [f'<figure><video src="{v["file"]}" controls preload="metadata"></video>'
            f'<figcaption>{html.escape(v["title"])}</figcaption></figure>' for v in manifest.get("videos", [])]
    rows = "".join(
        f"<tr><td>{html.escape(t['trial'])}</td><td>{_cell(t.get('press_move_tick'))}</td>"
        f"<td>{_cell(t.get('landed_move_tick'))}</td><td>{_cell(t.get('result'))}</td>"
        f"<td>{_cell(t.get('hp_before'))} -&gt; {_cell(t.get('hp_after'))}</td><td>{_cell(t.get('player_moved'))}</td>"
        f"<td>{_cell(t.get('bitten'))}</td><td>{_cell(t.get('dodged'))}</td><td>{_cell(t.get('cancelled_damage'))}</td>"
        f"<td>{_cell(t.get('expect'))}{'' if t.get('expectation_met') is None else ' (' + _cell(t['expectation_met']) + ')'}"
        f"</td></tr>" for t in manifest.get("trials", []))
    summary = "".join(
        f"<li>{html.escape(k)}: {s['trials']} trials, dodge started {s['started']}, bitten {s['bitten']}, "
        f"damage cancelled {s['cancelled_damage']}, health kept {s['hp_kept']}"
        f"{', expectation met ' + str(s['met']) + '/' + str(s['expected']) if s['expected'] else ''}</li>"
        for k, s in manifest.get("summary", {}).items())
    notes = "".join(f"<li>{html.escape(n)}</li>" for n in manifest.get("notes", []))
    movement = manifest.get("movement")
    tech = html.escape(manifest.get("technique", ""))
    if movement:
        tech += " (" + ", ".join(f"{html.escape(k)} {html.escape(str(v))}" for k, v in movement.items()) + ")"
    page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Movement dodge trials</title>
<style>body{{font:14px/1.4 sans-serif;margin:16px;background:#14161a;color:#ddd}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px}}
figure{{margin:0}}video{{width:100%;border:1px solid #333}}figcaption{{font-size:12px;color:#aaa}}
table{{border-collapse:collapse;font-size:13px}}th,td{{border:1px solid #333;padding:3px 6px;text-align:left}}
.wrap{{overflow-x:auto}}a{{color:#7cc}}</style></head><body>
<h1>Movement dodge against {html.escape(manifest.get('beast', ''))}</h1>
<p>Developer evidence from the headless capture session (960x540, software GL). Not an owner verdict.
Technique: {tech}. Trials at /tick rate {manifest.get('rate', TRIAL_RATE)}; dodge key Left Alt (left dodge: A held
first; no direction is a backstep). "press tick" is the beast's move tick polled when Alt went down; "landed" adds
the game ticks until the server's DODGE_DEBUG line. Server lines per trial: <a href="dodge_log.txt">dodge_log.txt</a>.</p>
<ul>{notes}</ul>
<h2>Summary</h2><ul>{summary}</ul>
<h2>Trials</h2><div class="wrap"><table><tr><th>trial</th><th>press tick</th><th>landed</th><th>server</th>
<th>hp</th><th>moved</th><th>bitten</th><th>dodged</th><th>cancelled damage</th><th>expected</th></tr>{rows}</table></div>
<h2>Videos</h2><div class="grid">{''.join(vids)}</div></body></html>"""
    (out / "index.html").write_text(page, encoding="utf-8")


def run_dodge(session: Session, beast: str, technique: str, out: Path, log=print) -> dict:
    cap = DodgeCapture(session, beast, technique, out, log=log)
    try:
        cap.setup()
        cap.run_trials()
    finally:
        steps = [lambda: cap.g.xdo("keyup", "a"), lambda: cap.g.xdo("keyup", "Alt_L"),
                 lambda: cap.run("tick rate 20", "tick unfreeze", "myvillage beast debug off",
                                 "gamerule naturalRegeneration true", f"kill @e[tag={TAG_BEAST}]", warn=False),
                 cap.bury, lambda: press_f5_to(cap.s, "first")]
        for step in steps:
            try:
                step()
            except Exception as e:  # noqa: BLE001 - best effort; keep the trial error and the output
                log(f"  cleanup: {type(e).__name__}: {e}")
        cap.finish()
    return cap.manifest
