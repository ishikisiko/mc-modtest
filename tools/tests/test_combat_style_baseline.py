"""Pins the accepted myvillage:basic_sword (Qingfeng) values.

This is the one place in Python where these numbers are written down.  A deliberate retune
updates basic_sword.json, the first-person rig, the regenerated animation, and this test
together; an accidental edit of the style file fails here and names the move and field.
"""

from __future__ import annotations

import unittest

from tools import combat_data

STYLE_ID = "myvillage:basic_sword"

# move id: (total, active, buffer start, chain tick, damage multiplier, maximum targets, range,
#           step (tick, maximum distance))
ACCEPTED_MOVES = {
    "myvillage:basic_sword_01_thrust": (11, [3, 4], 3, 7, 0.90, 1, 3.0, (2, 0.30)),
    "myvillage:basic_sword_02_horizontal_cut": (13, [4, 6], 4, 8, 0.95, 3, 2.8, (3, 0.25)),
    "myvillage:basic_sword_03_rising_cut": (15, [5, 7], 5, 10, 1.00, 2, 2.8, (4, 0.30)),
    "myvillage:basic_sword_04_diagonal_cut": (17, [6, 8], 6, 13, 1.10, 3, 3.0, (5, 0.45)),
    "myvillage:basic_sword_05_lunge_thrust": (20, [7, 9], 7, 20, 1.25, 2, 3.5, (6, 1.40)),
}
SUPPORT_DEPTH = 0.35
COMBO_TIMEOUT_TICKS = 14
MINIMUM_INTENT_INTERVAL_TICKS = 2


class BasicSwordBaselineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        data = combat_data.load()
        cls.issues = data.issues
        cls.style = data.styles.get(STYLE_ID)

    def test_style_loads(self) -> None:
        self.assertEqual((), self.issues)
        self.assertIsNotNone(self.style, STYLE_ID)

    def test_style_timing(self) -> None:
        self.assertEqual(COMBO_TIMEOUT_TICKS, self.style["combo_timeout_ticks"], "combo_timeout_ticks")
        self.assertEqual(MINIMUM_INTENT_INTERVAL_TICKS, self.style["minimum_intent_interval_ticks"],
                         "minimum_intent_interval_ticks")

    def test_move_order(self) -> None:
        self.assertEqual(list(ACCEPTED_MOVES), [move["id"] for move in self.style["moves"]])

    def test_accepted_move_values(self) -> None:
        moves = {move["id"]: move for move in self.style["moves"]}
        for move_id, expected in ACCEPTED_MOVES.items():
            move = moves.get(move_id)
            self.assertIsNotNone(move, move_id)
            total, active, buffer_start, chain, multiplier, targets, reach, (step_tick, step_distance) = expected
            for field, want, got in (
                    ("total_ticks", total, move["total_ticks"]),
                    ("active_ticks", active, move["active_ticks"]),
                    ("buffer_start_tick", buffer_start, move["buffer_start_tick"]),
                    ("chain_tick", chain, move["chain_tick"]),
                    ("damage_multiplier", multiplier, move["damage_multiplier"]),
                    ("maximum_targets", targets, move["maximum_targets"]),
                    ("range", reach, move["range"]),
                    ("step.tick", step_tick, move.get("step", {}).get("tick")),
                    ("step.maximum_distance", step_distance, move.get("step", {}).get("maximum_distance")),
                    ("step.support_depth", SUPPORT_DEPTH, move.get("step", {}).get("support_depth"))):
                with self.subTest(move=move_id, field=field):
                    self.assertEqual(want, got, f"{move_id} {field}: accepted {want}, style file has {got}")


if __name__ == "__main__":
    unittest.main()
