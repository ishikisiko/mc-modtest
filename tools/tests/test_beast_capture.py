import math
import unittest

from tools.combat_capture import beast


class BeastCaptureTest(unittest.TestCase):
    def test_key_ticks_follow_the_move_data(self):
        moves = {m["id"]: m for m in beast.beast_data("myvillage:demon_wolf")["moves"]}
        bite = dict(beast.key_ticks(moves["myvillage:demon_wolf_bite"]))
        self.assertEqual(bite, {"start": 0, "turn_lock": 4, "lunge": 9, "active_first": 10, "active_last": 12,
                                "recovery_mid": 20})
        pounce = dict(beast.key_ticks(moves["myvillage:demon_wolf_pounce"]))
        self.assertEqual(pounce["lunge"], 18)
        self.assertEqual(pounce["recovery_mid"], 40)

    def test_camera_looks_at_the_beast_from_the_requested_side(self):
        target = (0.5, -60.0, 8.5)
        # Beast faces south (+z): the front camera sits south of it looking north (yaw 180).
        x, y, z, yaw, pitch = beast.camera_pose(target, 0.0, "front", 3.0, 1.1, 0.75)
        self.assertAlmostEqual(z, 11.5)
        self.assertAlmostEqual(x, 0.5)
        self.assertAlmostEqual(abs(yaw) % 360, 180.0, places=4)
        self.assertGreater(pitch, 0.0)  # looking slightly down
        self.assertAlmostEqual(y + beast.EYE_HEIGHT, -58.9)
        # Its left side is +x; the camera there looks west (yaw 90).
        x, _, z, yaw, _ = beast.camera_pose(target, 0.0, "side", 3.0, 1.1, 0.75)
        self.assertAlmostEqual(x, 3.5)
        self.assertAlmostEqual(yaw % 360, 90.0, places=4)
        # Three-quarter back sits behind and to the left.
        x, _, z, _, _ = beast.camera_pose(target, 0.0, "q_back", 3.0, 1.1, 0.75)
        self.assertLess(z, 8.5)
        self.assertGreater(x, 0.5)
        self.assertAlmostEqual(math.hypot(x - 0.5, z - 8.5), 3.0)

    def test_status_and_replies_parse(self):
        st = beast.parse_status("myvillage:demon_wolf #5 pos=(0.500, -60.000, 8.500) move=myvillage:demon_wolf_bite "
                                "tick=4 synced=1 cooldowns=[0, 12]")
        self.assertEqual(st["tick"], "4")
        self.assertEqual(st["cooldowns"], "[0, 12]")
        self.assertEqual(beast.parse_pos("X has the following entity data: [0.5d, -60.0d, 8.25d]"), (0.5, -60.0, 8.25))
        self.assertEqual(beast.parse_float("X has the following entity data: 17.5f"), 17.5)

    def test_fight_analysis_pairs_hits_with_staggers(self):
        lines = [
            "[x] BEAST_DEBUG #7 t=100 hurt by minecraft:player (player) amount=6.0 accepted=true hp 50.0 -> 44.0 "
            "move=myvillage:demon_wolf_bite tick=2 resists=false",
            "[x] BEAST_DEBUG #7 t=103 staggered: cancelled myvillage:demon_wolf_bite at move tick 2",
            "[x] BEAST_DEBUG #7 t=140 hurt by minecraft:player (player) amount=6.0 accepted=true hp 44.0 -> 38.0 "
            "move=myvillage:demon_wolf_pounce tick=15 resists=true",
        ]
        result = beast.analyse_fight(lines)
        self.assertEqual(result["outside_window"], {"count": 1, "staggered": 1, "cancelled_a_move": 1})
        self.assertEqual(result["inside_window"], {"count": 1, "staggered": 0})

    def test_fight_rounds_count_completed_moves_and_damage(self):
        moves = beast.beast_data("myvillage:demon_wolf")["moves"]
        lines = [
            "BEAST_DEBUG #4 t=30 start myvillage:demon_wolf_bite",
            "BEAST_DEBUG #4 t=31 hurt by minecraft:player (player) amount=6.0 accepted=true hp 50.0 -> 44.0 "
            "move=myvillage:demon_wolf_bite tick=0 resists=true",
            "BEAST_DEBUG #4 t=40 hit minecraft:player with myvillage:demon_wolf_bite at move tick 10: damage 5.0 "
            "accepted=true hp 20.0 -> 15.0",
            "BEAST_DEBUG #4 t=57 end myvillage:demon_wolf_bite at move tick 27, cooldown 24",
            "BEAST_DEBUG #4 t=70 start myvillage:demon_wolf_pounce",
            "BEAST_DEBUG #4 t=74 hurt by minecraft:player (player) amount=6.0 accepted=true hp 44.0 -> 38.0 "
            "move=myvillage:demon_wolf_pounce tick=4 resists=false",
            "BEAST_DEBUG #4 t=75 staggered: cancelled myvillage:demon_wolf_pounce at move tick 5",
            "BEAST_DEBUG #4 t=75 end myvillage:demon_wolf_pounce at move tick 5, cooldown 10",
            "BEAST_DEBUG #4 t=80 start myvillage:demon_wolf_bite",
            "BEAST_DEBUG #4 t=90 hurt by minecraft:player (player) amount=6.0 accepted=true hp 6.0 -> 0.0 "
            "move=myvillage:demon_wolf_bite tick=10 resists=true",
        ]
        samples = [
            (0.0, "myvillage:demon_wolf #4 pos=(0.5, -60.0, 5.0) t=20 move=none dist=5.00", 20.0),
            (0.1, "myvillage:demon_wolf #4 pos=(0.5, -60.0, 3.0) t=27 move=none dist=3.20", 20.0),
            (0.2, "myvillage:demon_wolf #4 pos=(0.5, -60.0, 3.0) t=26 move=none dist=3.30", 20.0),
        ]
        (r,) = beast.fight_rounds(lines, moves, samples)
        self.assertEqual(r["started"], {"demon_wolf_bite": 2, "demon_wolf_pounce": 1})
        self.assertEqual(r["completed"], {"demon_wolf_bite": 1})
        self.assertEqual(r["cancelled"], {"demon_wolf_pounce": 1})
        self.assertEqual(r["landed"], {"demon_wolf_bite": 1})
        self.assertEqual(r["landed_t"], {"demon_wolf_bite": [40]})
        self.assertEqual(r["hits_taken"], 1)
        self.assertEqual(r["damage_to_player"], 5.0)
        self.assertEqual(r["ticks_to_kill"], 59)
        self.assertEqual(r["first_bite_after_ticks"], 9)
        self.assertEqual(r["player_hits"], 3)
        self.assertEqual(r["player_hits_resisted"], 2)
        self.assertEqual((r["staggered_inside_window"], r["staggered_outside_window"]), (0, 1))
        # In bite range first at t=26 (polled out of order), pounce range at t=20; the bite landed at 40.
        self.assertEqual(r["first_in_range_t"], {"demon_wolf_pounce": 20, "demon_wolf_bite": 26})
        self.assertEqual(r["in_range_to_landed"], {"demon_wolf_pounce": None, "demon_wolf_bite": 14})

if __name__ == "__main__":
    unittest.main()
