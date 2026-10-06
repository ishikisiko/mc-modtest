from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.combat_capture import cli, dodge
from tools.combat_capture.beast import beast_data
from tools.combat_capture.data import Move, load_weapon

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
BITE = "myvillage:demon_wolf_bite"
POUNCE = "myvillage:demon_wolf_pounce"

STARTED = ("[12:00:01] [Server thread/INFO] [co.ex.my.co.ru.CombatDodgeService/]: DODGE_DEBUG player=CaptureDev "
           "t=1204 result=started technique=myvillage:taxue_wuhen dir=NONE yaw=180.0 distance=4.50 invuln=7 cooldown=24")
REJECTED = ("[12:00:02] [Server thread/INFO] [co.ex.my.co.ru.CombatDodgeService/]: DODGE_DEBUG player=CaptureDev "
            "t=1209 result=rejected reason=COOLDOWN")
CANCELLED = ("[12:00:01] [Server thread/INFO] [co.ex.my.co.ru.CombatDodgeService/]: DODGE_DEBUG player=CaptureDev "
             "t=1206 cancelled_damage=4.5 source=minecraft:mob_attack")
HIT = ("[12:00:01] [Server thread/INFO] [co.ex.my.en.be.BeastEntity/]: BEAST_DEBUG #7 t=15 hit minecraft:player "
       "with myvillage:demon_wolf_bite at move tick 10: damage 4.5 accepted=true hp 20.0 -> 15.5")
MISS = HIT.replace("accepted=true hp 20.0 -> 15.5", "accepted=false hp 20.0 -> 20.0")


def sword_move(active=(3, 4), total=11) -> Move:
    return Move(index=1, id="myvillage:basic_sword_01_thrust", kind="thrust", total_ticks=total,
                active_ticks=list(active), buffer_start_tick=3, chain_tick=7)


class PlanTest(unittest.TestCase):
    def setUp(self):
        self.wolf = beast_data("myvillage:demon_wolf")
        self.moves = {m["id"]: m for m in self.wolf["moves"]}

    def test_demon_wolf_trials_follow_the_brief(self):
        bite = dodge.beast_trials(self.moves[BITE])
        self.assertEqual([t["at"] for t in bite], [None, 4, 7, 9, 10, 9])
        self.assertEqual([t["direction"] for t in bite], [None, "back", "back", "back", "back", "left"])
        self.assertTrue(all(t["distance"] == 2.9 for t in bite))
        pounce = dodge.beast_trials(self.moves[POUNCE])
        self.assertEqual([t["at"] for t in pounce], [None, 14, 17, 18, 19, 18])
        self.assertTrue(all(t["distance"] == 6.0 for t in pounce))

    def test_planned_ticks_lie_inside_each_move(self):
        for move in self.wolf["moves"]:
            plan = dodge.trial_ticks(move)
            low, high = move["use_range"]
            self.assertTrue(low <= plan["distance"] <= high, move["id"])
            for t in (*plan["back"], plan["left"]):
                self.assertTrue(0 <= t < move["total_ticks"], (move["id"], t))
            self.assertLessEqual(plan["left"] - dodge.LEFT_LEAD_TICKS, plan["left"])

    def test_derived_ticks_for_an_unlisted_move(self):
        move = dict(self.moves[BITE], id="myvillage:other_bite")
        self.assertEqual(dodge.trial_ticks(move), {"distance": 2.9, "back": (4, 7, 9, 10), "left": 9})
        far = dict(self.moves[POUNCE], id="myvillage:other_pounce")
        self.assertEqual(dodge.trial_ticks(far), {"distance": 6.0, "back": (13, 16, 18, 19), "left": 18})

    def test_recovery_ticks_from_the_first_sword_move(self):
        w = load_weapon(RESOURCES, dodge.SWORD, require_rig=False)
        first = w.moves[0]
        rec = dodge.recovery_ticks(first)
        self.assertEqual(rec["timing"], 3)
        self.assertLessEqual(rec["timing"], first.active_ticks[1])
        self.assertEqual(rec["cancel"], first.active_ticks[1] + 2)
        self.assertLess(rec["cancel"], first.total_ticks)
        style = json.loads((RESOURCES / "data/myvillage/combat/style/basic_sword.json").read_text(encoding="utf-8"))
        self.assertEqual(first.id, style["moves"][0]["id"])

    def test_recovery_ticks_refuse_an_impossible_move(self):
        with self.assertRaises(ValueError):
            dodge.recovery_ticks(sword_move(active=(1, 2)))
        with self.assertRaises(ValueError):
            dodge.recovery_ticks(sword_move(active=(3, 9), total=11))
        self.assertEqual(dodge.recovery_ticks(sword_move())["cancel"], 6)

    def test_full_plan_order_and_expectations(self):
        plan = dodge.trial_plan(self.wolf["moves"], sword_move())
        self.assertEqual([t["kind"] for t in plan], ["beast"] * 12 + ["cooldown", "recovery", "recovery"])
        self.assertEqual(plan[12]["gap"], 5)
        self.assertEqual([(t["at"], t["expect"]) for t in plan[13:]], [(3, "rejected TIMING"), (6, "started")])


class ParseTest(unittest.TestCase):
    def test_dodge_lines(self):
        e = dodge.parse_dodge_line(STARTED)
        self.assertEqual((e["kind"], e["t"], e["dir"], e["technique"]), ("started", 1204, "NONE", "myvillage:taxue_wuhen"))
        r = dodge.parse_dodge_line(REJECTED)
        self.assertEqual((r["kind"], r["reason"], r["t"]), ("rejected", "COOLDOWN", 1209))
        c = dodge.parse_dodge_line(CANCELLED)
        self.assertEqual((c["kind"], c["cancelled_damage"], c["source"]), ("cancelled_damage", "4.5", "minecraft:mob_attack"))
        self.assertIsNone(dodge.parse_dodge_line(HIT))
        self.assertIsNone(dodge.parse_dodge_line("DODGE_DEBUG player=x t=1 result=maybe"))
        self.assertEqual([e["kind"] for e in dodge.dodge_events([STARTED, HIT, CANCELLED, REJECTED])],
                         ["started", "cancelled_damage", "rejected"])
        self.assertEqual(len(dodge.press_events(dodge.dodge_events([STARTED, CANCELLED, REJECTED]))), 2)

    def test_player_hits(self):
        hits = dodge.player_hits([HIT, MISS, STARTED, HIT.replace("minecraft:player", "minecraft:husk")])
        self.assertEqual(len(hits), 2)
        self.assertEqual((hits[0]["move_tick"], hits[0]["damage"], hits[0]["accepted"]), (10, 4.5, True))
        self.assertFalse(hits[1]["accepted"])

    def test_gametime(self):
        self.assertEqual(dodge.parse_gametime("The time is 48213"), 48213)
        self.assertIsNone(dodge.parse_gametime("Unknown command"))


class VerdictTest(unittest.TestCase):
    def test_flags(self):
        ev = dodge.dodge_events([STARTED, CANCELLED])
        self.assertEqual(dodge.verdict(ev, dodge.player_hits([MISS])),
                         {"bitten": False, "dodged": True, "cancelled_damage": True})
        self.assertEqual(dodge.verdict([], dodge.player_hits([HIT])),
                         {"bitten": True, "dodged": False, "cancelled_damage": False})

    def test_press_result_and_expectations(self):
        started, rejected = dodge.dodge_events([STARTED, REJECTED])
        self.assertEqual(dodge.press_result(started), "started")
        self.assertEqual(dodge.press_result(rejected), "rejected COOLDOWN")
        self.assertEqual(dodge.press_result(None), "none")
        cooldown = {"kind": "cooldown"}
        self.assertTrue(dodge.expectation_met(cooldown, [started, rejected]))
        self.assertFalse(dodge.expectation_met(cooldown, [started]))
        self.assertFalse(dodge.expectation_met(cooldown, [started, started]))
        timing = dodge.parse_dodge_line(REJECTED.replace("COOLDOWN", "TIMING"))
        self.assertTrue(dodge.expectation_met({"kind": "recovery", "expect": "rejected TIMING"}, [timing]))
        self.assertFalse(dodge.expectation_met({"kind": "recovery", "expect": "started"}, [timing]))
        self.assertTrue(dodge.expectation_met({"kind": "recovery", "expect": "started"}, [started]))
        self.assertFalse(dodge.expectation_met({"kind": "recovery", "expect": "started"}, []))
        self.assertIsNone(dodge.expectation_met({"kind": "beast"}, [started]))

    def test_landed_tick_distance_and_video(self):
        self.assertEqual(dodge.landed_move_tick(9, 1203, 1204), 10)
        self.assertIsNone(dodge.landed_move_tick(9, None, 1204))
        self.assertEqual(dodge.planar_distance((0.5, -60, 0.5), (0.5, -60, -3.5)), 4.0)
        self.assertIsNone(dodge.planar_distance(None, (0, 0, 0)))
        self.assertEqual(dodge.video_seconds(6, 28, 24), round(6 * (4.0 + 1.2 + 5.6 + 3.0) + 6.0, 1))

    def test_summary_counts(self):
        trials = [{"kind": "beast", "move": BITE, "dodged": True, "bitten": False, "cancelled_damage": True,
                   "hp_kept": True, "expectation_met": None},
                  {"kind": "beast", "move": BITE, "dodged": False, "bitten": True, "cancelled_damage": False,
                   "hp_kept": False, "expectation_met": None},
                  {"kind": "cooldown", "dodged": True, "bitten": False, "cancelled_damage": False, "hp_kept": True,
                   "expectation_met": True}]
        s = dodge.summarize(trials)
        self.assertEqual(s["demon_wolf_bite"], {"trials": 2, "started": 1, "bitten": 1, "cancelled_damage": 1,
                                                "hp_kept": 1, "expected": 0, "met": 0})
        self.assertEqual((s["cooldown"]["expected"], s["cooldown"]["met"]), (1, 1))


class PageAndCliTest(unittest.TestCase):
    def test_technique_movement_reads_effects(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "data/myvillage/myvillage/technique/taxue_wuhen.json"
            p.parent.mkdir(parents=True)
            p.write_text(json.dumps({"effects": {"movement": {"dash_distance": 4.5, "cooldown_ticks": 24}}}),
                         encoding="utf-8")
            self.assertEqual(dodge.technique_movement(Path(d), "myvillage:taxue_wuhen")["cooldown_ticks"], 24)
            self.assertIsNone(dodge.technique_movement(Path(d), "myvillage:liuyun_bu"))

    def test_page_renders_and_escapes(self):
        trial = {"trial": "demon_wolf_bite: backstep at move tick 9 <x>", "press_move_tick": 9, "landed_move_tick": 10,
                 "result": "started", "hp_before": 20.0, "hp_after": 20.0, "player_moved": 4.3, "bitten": False,
                 "dodged": True, "cancelled_damage": True, "expect": None, "expectation_met": None}
        m = {"beast": "myvillage:demon_wolf", "technique": "myvillage:taxue_wuhen", "movement": {"dash_distance": 4.5},
             "rate": 5, "notes": ["n"], "videos": [{"file": "video/dodge_demon_wolf_bite.mp4", "title": "bite"}],
             "trials": [trial], "summary": dodge.summarize([dict(trial, kind="beast", move=BITE, hp_kept=True)])}
        with tempfile.TemporaryDirectory() as d:
            dodge.write_page(Path(d), m)
            page = (Path(d) / "index.html").read_text(encoding="utf-8")
        self.assertIn("&lt;x&gt;", page)
        self.assertIn("video/dodge_demon_wolf_bite.mp4", page)
        self.assertIn("dodge_log.txt", page)
        self.assertNotIn("/home/", page)

    def test_cli_defaults(self):
        a = cli.build_parser().parse_args(["dodge"])
        self.assertEqual((a.beast, a.technique, a.out), ("myvillage:demon_wolf", "myvillage:taxue_wuhen", None))
        self.assertIs(a.func, cli.cmd_dodge)
        self.assertEqual(dodge.DEFAULT_OUT, ROOT / "out/preview/movement_dodge")


if __name__ == "__main__":
    unittest.main()
