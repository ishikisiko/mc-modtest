"""Mapped dragon-part checks on a labelled End fixture, never a complete boss-fight verdict.

Caller first takes the actual client through an End portal, then calls run_dragon(e).
The existing options must bind left/right mouse to attack/use and R to combat mode.
Only this disposable fixture is summoned/positioned administratively; attacks are X input.
"""
from __future__ import annotations

import json
import math
import re
import time

from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene, session as cs
from tools.combat_capture.sword_qi import parse_events
from tools.realm_stats_evidence import json_lines


TAG = "exploration_v2_mapped_dragon"
TARGET = f"@e[type=minecraft:ender_dragon,tag={TAG},limit=1]"
CENTER = (400.5, 100.0, 400.5)


def run_dragon(e):
    """Return a case dict; caller owns selected/completed/unrun bookkeeping and game lifecycle."""
    u, g = e.s.username, e.s.game
    result = {
        "method": "actual mapped left mouse and right mouse charge/release; command setup/readback",
        "fixture": "summoned vanilla End dragon; live AI ticks; controlled Hover then SittingFlaming phases",
        "dimension": "minecraft:the_end",
        "natural_dragon_ai_victory": "not_verified",
        "owner_balance_feel_fps": "not_verified",
        "bindings": {"attack": "mouse.left", "use": "mouse.right", "combat_mode": "r", "slot": "1"},
        "phase_note": "Hover 10 isSitting but lacks AbstractDragonSittingPhase arrow immunity; phase 5 supplies the native arrow rejection control",
        "trials": [],
    }

    def rc(command):
        return e.rc("execute in minecraft:the_end run " + command)

    def number(command):
        return e.number("execute in minecraft:the_end run " + command)

    def hp():
        return number(f"data get entity {TARGET} Health")

    def phase():
        return int(number(f"data get entity {TARGET} DragonPhase"))

    def trace():
        rows = json_lines(rc(f"myvillage stats trace {TARGET}"), "STATS_TRACE")
        if len(rows) != 1:
            raise RuntimeError("Missing unique dragon STATS_TRACE: " + repr(rows))
        return rows[0]

    def shot(name):
        e.gui_shot(g, "dragon-" + name + ".png")

    def click(button, duration=.10):
        g.require_ingame("mapped dragon input")
        g.xdo("mousedown", str(button))
        try:
            time.sleep(duration)
        finally:
            g.xdo("mouseup", str(button))

    def place(z, yaw=0, pitch=0):
        rc(f"tp {u} {CENTER[0]} 98.0 {z} {yaw} {pitch}")
        time.sleep(.22)

    def log_since(mark):
        with (cs.LOG_DIR / "server.log").open("rb") as stream:
            stream.seek(mark)
            return stream.read().decode(errors="replace")

    def sword_qi(label):
        rc(f"myvillage cultivation qi fill {u}")
        before = hp()
        mark = (cs.LOG_DIR / "server.log").stat().st_size
        click(3, .32)
        log = e.poll(lambda: log_since(mark),
                     lambda value: "result=wave_stop" in value, 12, label + " wave terminates")
        events = parse_events(log.splitlines())
        releases = [row for row in events if row.get("result") == "released"]
        hits = [row for row in events if row.get("result") == "hit"]
        after, receipt = hp(), trace()
        detail = {"label": label, "before": before, "after": after,
                  "phase": phase(), "events": events, "trace": receipt}
        # There is only this one dragon in the swept fixture. A single wave traverses its
        # overlapping head and neck; one hit log plus owner HP/trace agreement exposes stacking.
        loss = before - after
        logged_loss = sum(float(row.get("damage", "nan")) for row in hits)
        observed = receipt.get("actualHealthLoss")
        e.check(label + "_mapped_release_one_owner_hit",
                len(releases) == 1 and len(hits) == 1 and loss > 0
                and math.isfinite(logged_loss) and abs(logged_loss - loss) < .02
                and isinstance(observed, (int, float)) and abs(observed - loss) < .02
                and receipt.get("status") == "observed" and receipt.get("outgoing") is not None
                and receipt.get("sourceId") == "minecraft:player_attack", detail)
        result["trials"].append(detail)
        (e.out / ("dragon-" + label + ".log")).write_text(log)
        shot(label)
        return detail

    dimension = read_data(e.rc(f"data get entity {u} Dimension"))
    e.check("dragon_client_entered_actual_end", dimension == "minecraft:the_end", dimension)
    e.rc("tick rate 20")
    e.rc("tick unfreeze")
    e.rc("gamerule doMobSpawning false")
    e.rc(f"gamemode creative {u}")
    e.rc(f"myvillage cultivation awaken {u}")
    e.rc(f"myvillage cultivation initiate {u}")
    e.rc(f"myvillage cultivation setrealm {u} myvillage:golden_core myvillage:golden_early")
    e.rc(f"myvillage cultivation learn {u} myvillage:jianqi_jue")
    rc(f"kill @e[tag={TAG}]")
    rc("forceload add 384 368 416 416")
    # A narrow player floor lies below all dragon hurt boxes, so no block obscures the strike.
    rc("fill 397 97 378 403 97 393 minecraft:obsidian")
    rc(f"tp {u} 400.5 98.0 381.0 0 0")
    rc('summon minecraft:ender_dragon 400.5 100.0 400.5 '
       '{DragonPhase:10,Rotation:[0.0f,0.0f],Motion:[0.0d,0.0d,0.0d],'
       'PersistenceRequired:1b,Tags:["' + TAG + '"]}')
    e.poll(phase, lambda value: value == 10, 15, "live Hover fixture")
    time.sleep(2.0)  # Populate the dragon's real latency/part-position history without NoAI.
    fixture = read_data(rc(f"data get entity {TARGET}"))
    result["initial_entity_nbt"] = fixture
    e.check("dragon_fixture_ai_and_real_phase", fixture.get("NoAI", 0) == 0
            and fixture.get("DragonPhase") == 10, fixture)
    e.rc(f"item replace entity {u} hotbar.0 with myvillage:qingfeng_sword")
    g.key("1")
    scene.ensure_cultivation(e.s)
    shot("live-hover-fixture")

    try:
        # Yaw 0: native head center z=394.0, y=99.0; neck z=395.0, y=99.0.
        # The first thrust faces away and misses. The second mapped action is the actual
        # maximumTargets=3 horizontal cut, intersecting both live head and neck hurt boxes.
        place(391.8, 180)
        before = hp()
        click(1)
        time.sleep(.60)
        e.check("dragon_combo_first_thrust_deliberately_misses", hp() == before, {"health": before})
        place(391.8, 0)
        click(1)
        time.sleep(1.0)
        after, receipt = hp(), trace()
        loss = before - after
        observed = receipt.get("actualHealthLoss")
        move = (receipt.get("outgoing") or {}).get("moveMultiplier")
        melee = {"before": before, "after": after, "phase": phase(), "trace": receipt,
                 "overlapping_parts": "head [393.5,394.5], neck [393.5,396.5] on Z at settled yaw 0",
                 "input": "one deliberate miss then one mapped horizontal cut; no held autoattack"}
        e.check("dragon_mapped_basic_sweep_owner_receipt_and_dedup",
                loss > 0 and isinstance(observed, (int, float)) and abs(loss-observed) < .02
                and receipt.get("status") == "observed" and move is not None and abs(move-.95) < .0001
                and receipt.get("sourceId") == "minecraft:player_attack", melee)
        result["trials"].append(melee)
        shot("mapped-basic-sweep")

        time.sleep(.8)
        place(381.0)
        sword_qi("hover-sword-qi")

        # SittingFlaming has a 200-tick stable window and inherits native arrow immunity.
        # Hover deliberately is NOT used for this control; neither fixture disables AI.
        rc(f"data merge entity {TARGET} {{DragonPhase:5,Motion:[0.0d,0.0d,0.0d]}}")
        e.poll(phase, lambda value: value == 5, 8, "native SittingFlaming phase")
        e.rc(f"item replace entity {u} hotbar.0 with minecraft:bow")
        e.rc(f"give {u} minecraft:arrow 8")
        g.key("1")
        place(381.0, 0, -1.8)
        arrow_before = hp()
        click(3, 1.05)

        def arrow_fire():
            reply = rc("execute positioned 400.5 100 400.5 as "
                       "@e[type=minecraft:arrow,distance=..40] run data get entity @s Fire")
            values = [int(value) for value in re.findall(r"data:\s*(-?\d+)s", reply)]
            return {"reply": reply, "burning": any(value > 0 for value in values)}

        burning = e.poll(arrow_fire, lambda value: value["burning"], 4,
                         "native sitting phase ignites and rejects actual mapped arrow")
        arrow_after = hp()
        arrow = {"before": arrow_before, "after": arrow_after, "phase": phase(), "arrow": burning}
        e.check("dragon_native_sitting_arrow_rejected_on_observed_contact",
                phase() == 5 and arrow_before == arrow_after and burning["burning"], arrow)
        result["trials"].append(arrow)
        shot("sitting-arrow-rejection")
        e.rc(f"item replace entity {u} hotbar.0 with myvillage:qingfeng_sword")
        g.key("1")
        place(381.0)
        # Start a fresh native 200-tick phase window after evidence/screenshot I/O. This is
        # explicit fixture control; no hurt call or arrow/skill immunity is manufactured.
        rc(f"data merge entity {TARGET} {{DragonPhase:10,Motion:[0.0d,0.0d,0.0d]}}")
        rc(f"data merge entity {TARGET} {{DragonPhase:5,Motion:[0.0d,0.0d,0.0d]}}")
        e.poll(phase, lambda value: value == 5, 8, "fresh SittingFlaming sword-qi window")
        sword_qi("sitting-sword-qi")
        e.check("dragon_native_sitting_phase_survives_sword_qi", phase() == 5, {"phase": phase()})
        result["final_entity_nbt"] = read_data(rc(f"data get entity {TARGET}"))
        return result
    finally:
        g.xdo("mouseup", "1")
        g.xdo("mouseup", "3")
        rc(f"kill @e[tag={TAG}]")
        rc("forceload remove 384 368 416 416")
        (e.out / "dragon-mapped-case.json").write_text(json.dumps(result, indent=2) + "\n")
