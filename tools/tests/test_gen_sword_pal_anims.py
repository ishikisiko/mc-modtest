from __future__ import annotations

import copy
import dataclasses
import io
import json
import math
import unittest
import zipfile
from contextlib import redirect_stderr
from unittest import mock

from tools import gen_sword_pal_anims as gen


PAL_JAR = gen.ROOT / "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"


def style() -> dict:
    return copy.deepcopy(gen.combat_styles()[gen.BASIC_SWORD.style_id])


def move(animation_id: str) -> gen.Move:
    return next(m for m in gen.moves() if m.animation_id == animation_id)


def pose_at(m: gen.Move, phase: str):
    index = next(i for i, k in enumerate(m.keys) if k.phase == phase)
    return m.keys[index], gen.resolve(m.keys)[index], gen.skeleton(gen.resolve(m.keys)[index])


class GeneratedJsonTest(unittest.TestCase):
    def test_committed_json_matches_generator(self) -> None:
        expected = gen.render(gen.build_document())
        actual = gen.OUTPUT.read_text(encoding="utf-8")
        self.assertEqual(
            expected, actual,
            "sword_combat.json drifted; run python3 tools/gen_sword_pal_anims.py")

    def test_generator_is_deterministic(self) -> None:
        self.assertEqual(gen.render(gen.build_document()), gen.render(gen.build_document()))

    def test_self_checks_pass(self) -> None:
        self.assertEqual([], gen.check_document(gen.build_document()))

    def test_validator_contract(self) -> None:
        document = json.loads(gen.OUTPUT.read_text(encoding="utf-8"))
        self.assertEqual("1.8.0", document["format_version"])
        animations = document["animations"]
        self.assertEqual(
            {"sword_mode_enter", "sword_ready_idle", *(m.animation_id for m in gen.moves())},
            set(animations))
        self.assertIs(True, animations["sword_ready_idle"]["loop"])
        self.assertEqual(1.2, animations["sword_ready_idle"]["animation_length"])
        for m in gen.moves():
            animation = animations[m.animation_id]
            self.assertAlmostEqual(m.total / 20.0, animation["animation_length"], places=6)
            for bone in gen.REQUIRED_BONES:
                ticks = [float(t) * 20.0 for t in animation["bones"][bone]["rotation"]]
                self.assertIn(0.0, ticks)
                self.assertTrue(any(abs(t - m.total) < 1e-6 for t in ticks), (m.animation_id, bone))
                self.assertTrue(any(m.active_start <= t <= m.active_end for t in ticks), (m.animation_id, bone))
            for bone, channels in animation["bones"].items():
                for frames in channels.values():
                    times = [float(t) for t in frames]
                    self.assertEqual(sorted(times), times, (m.animation_id, bone))
        lunges = [m for m in gen.moves() if m.lunge]
        self.assertTrue(lunges)
        for m in lunges:
            position = animations[m.animation_id]["bones"]["body"]["position"]
            self.assertTrue(any(isinstance(v, list) and any(x != 0 for x in v) for v in position.values()))

    def test_keyframes_use_pal_object_form_and_valid_easings(self) -> None:
        document = gen.build_document()
        seen = set()
        for animation in document["animations"].values():
            for channels in animation["bones"].values():
                for frames in channels.values():
                    for value in frames.values():
                        if isinstance(value, dict):
                            self.assertEqual({"vector", "easing"}, set(value))
                            self.assertEqual(3, len(value["vector"]))
                            seen.add(value["easing"])
                        else:
                            self.assertEqual(3, len(value))
        self.assertTrue(seen <= gen.PAL_EASINGS)
        self.assertIn("easeoutcubic", seen)
        self.assertIn("easeinoutsine", seen)

    @unittest.skipUnless(PAL_JAR.is_file(), "PAL jar not present")
    def test_easing_names_exist_in_pal(self) -> None:
        with zipfile.ZipFile(PAL_JAR) as jar:
            easing_class = jar.read("com/zigythebird/playeranimcore/easing/EasingType.class")
        for name in gen.PAL_EASINGS:
            self.assertIn(name.encode(), easing_class, name)


class StyleBindingTest(unittest.TestCase):
    """Timing and kind come from the style file; the pose table holds only poses."""

    def test_moves_follow_the_style_file(self) -> None:
        data = style()
        bound = gen.moves()
        self.assertEqual([m["id"] for m in data["moves"]], [m.move_id for m in bound])
        for source, m in zip(data["moves"], bound):
            self.assertEqual(source["id"].split(":", 1)[1], m.animation_id)
            self.assertEqual(source["kind"], m.kind)
            self.assertEqual(source["total_ticks"], m.total)
            self.assertEqual(source["active_ticks"], [m.active_start, m.active_end])
            self.assertEqual(source["chain_tick"], m.chain_tick)
            self.assertEqual(source.get("step", {}).get("tick"), m.step_tick)

    def test_pose_table_carries_no_timing(self) -> None:
        self.assertEqual({"keys", "lunge", "cut_path"}, {f.name for f in dataclasses.fields(gen.MovePoses)})

    def test_retimed_style_changes_the_bound_move(self) -> None:
        data = style()
        data["moves"][0]["total_ticks"] = 12
        data["moves"][0]["chain_tick"] = 8
        bound = gen.bind(gen.BASIC_SWORD, data)
        self.assertEqual((12, 8), (bound[0].total, bound[0].chain_tick))
        # The poses still end on tick 11, so the self-check now reports the mismatch.
        errors = gen.check_move(bound[0])
        self.assertTrue(any("end at the total" in e for e in errors), errors)

    def test_moved_active_window_fails_the_pose_checks(self) -> None:
        data = style()
        data["moves"][1]["active_ticks"] = [5, 6]
        data["moves"][1]["buffer_start_tick"] = 5
        errors = gen.check_move(gen.bind(gen.BASIC_SWORD, data)[1])
        self.assertTrue(any("expected activeStart 5" in e for e in errors), errors)


class PoseTableDisagreementTest(unittest.TestCase):
    def test_style_move_without_poses_names_the_move(self) -> None:
        data = style()
        extra = copy.deepcopy(data["moves"][0])
        extra["id"] = "myvillage:basic_sword_06_extra"
        data["moves"].append(extra)
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.bind(gen.BASIC_SWORD, data)
        self.assertIn("myvillage:basic_sword_06_extra", str(raised.exception))

    def test_posed_move_missing_from_the_style_names_the_move(self) -> None:
        data = style()
        removed = data["moves"].pop(2)
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.bind(gen.BASIC_SWORD, data)
        self.assertIn(removed["id"], str(raised.exception))

    def test_generator_exits_with_an_error_naming_the_move(self) -> None:
        data = style()
        extra = copy.deepcopy(data["moves"][0])
        extra["id"] = "myvillage:basic_sword_06_extra"
        data["moves"].append(extra)
        stderr = io.StringIO()
        with mock.patch.object(gen, "combat_styles", return_value={gen.BASIC_SWORD.style_id: data}), \
                redirect_stderr(stderr):
            self.assertEqual(1, gen.main(["--check"]))
        self.assertIn("POSE_TABLE", stderr.getvalue())
        self.assertIn("myvillage:basic_sword_06_extra", stderr.getvalue())

    def test_style_without_a_pose_table_is_rejected(self) -> None:
        styles = {gen.BASIC_SWORD.style_id: style(), "myvillage:spear": style()}
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.check_tables(styles)
        self.assertIn("myvillage:spear", str(raised.exception))

    def test_check_passes_on_the_committed_data(self) -> None:
        with mock.patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(0, gen.main(["--check"]))


class ActionFeelTargetsTest(unittest.TestCase):
    def test_phase_timing(self) -> None:
        for m in gen.moves():
            keys = m.keys
            phases = [k.phase for k in keys]
            contact = phases.index("contact")
            through = phases.index("through")
            hold = phases.index("hold")
            self.assertEqual(m.active_start, keys[contact].tick, m.animation_id)
            self.assertLessEqual(keys[contact].tick - keys[contact - 1].tick, 2, m.animation_id)
            self.assertTrue(keys[contact].easing.startswith("easeout"), m.animation_id)
            self.assertEqual(m.active_end, keys[through].tick, m.animation_id)
            self.assertTrue(3 <= keys[hold].tick - keys[through].tick <= 5, m.animation_id)
            self.assertTrue(keys[-1].easing.startswith("easeinout"), m.animation_id)

    def test_hold_drift_is_small(self) -> None:
        for m in gen.moves():
            poses = gen.resolve(m.keys)
            phases = [k.phase for k in m.keys]
            a, b = poses[phases.index("through")], poses[phases.index("hold")]
            for bone in gen.BONE_ORDER:
                self.assertLessEqual(max(abs(a[bone][j] - b[bone][j]) for j in range(3)), 5.0, (m.animation_id, bone))

    def test_cuts_turn_the_whole_figure_and_leave_the_silhouette(self) -> None:
        for m in gen.moves():
            if m.kind != "cut":
                continue
            yaws = [p["body"][1] for p in gen.resolve(m.keys)]
            self.assertTrue(70 <= max(yaws) - min(yaws) <= 110, m.animation_id)
            for phase in ("through", "hold"):
                _, _, skeleton = pose_at(m, phase)
                self.assertGreaterEqual(abs(skeleton["right_hand"][0]), 0.35, (m.animation_id, phase))
                blade_yaw, _ = gen.direction_angles(skeleton["grip"], skeleton["tip"])
                self.assertTrue(80 <= abs(blade_yaw) <= 110, (m.animation_id, phase, blade_yaw))

    def test_cut_directions_match_hitboxes(self) -> None:
        horizontal = move("basic_sword_02_horizontal_cut")
        _, _, coil = pose_at(horizontal, "coil")
        _, _, hold = pose_at(horizontal, "hold")
        self.assertLess(coil["tip"][0], 0.0)   # starts on the player's left
        self.assertGreater(hold["tip"][0], 0.0)  # ends on the player's right

        rising = move("basic_sword_03_rising_cut")
        _, _, contact = pose_at(rising, "contact")
        _, _, hold = pose_at(rising, "hold")
        self.assertGreater(contact["tip"][0], 0.0)
        self.assertLess(hold["tip"][0], 0.0)
        self.assertGreater(hold["tip"][1], contact["tip"][1])

        diagonal = move("basic_sword_04_diagonal_cut")
        _, _, apex = pose_at(diagonal, "coil")
        _, _, hold = pose_at(diagonal, "hold")
        self.assertLess(apex["tip"][0], 0.0)
        self.assertGreater(hold["tip"][0], 0.0)
        self.assertLess(hold["tip"][1], apex["tip"][1])

    def test_thrusts_are_bladed_t_shapes(self) -> None:
        for m in gen.moves():
            if m.kind != "thrust":
                continue
            _, pose, skeleton = pose_at(m, "through")
            self.assertLessEqual(pose["body"][1], -40.0, m.animation_id)
            blade_yaw, blade_elevation = gen.direction_angles(skeleton["grip"], skeleton["tip"])
            self.assertLessEqual(abs(blade_yaw), 20.0, m.animation_id)
            self.assertLessEqual(abs(blade_elevation), 15.0, m.animation_id)
            self.assertLess(skeleton["left_hand"][0], -0.45, m.animation_id)

    def test_hip_drop_and_lunge(self) -> None:
        for m in gen.moves():
            for phase in ("contact", "through", "hold"):
                _, pose, _ = pose_at(m, phase)
                self.assertTrue(-3.5 <= pose["body_pos"][1] <= -2.0, (m.animation_id, phase))
        lunge = move("basic_sword_05_lunge_thrust")
        coil, coil_pose, _ = pose_at(lunge, "coil")
        _, contact_pose, contact_skeleton = pose_at(lunge, "contact")
        self.assertTrue(lunge.lunge)
        self.assertEqual(lunge.step_tick, coil.tick)
        # Negative PAL body z moves the figure forward (PlayerRendererMixin.applyBodyTransforms).
        self.assertLessEqual(contact_pose["body_pos"][2], -4.0)
        self.assertGreater(coil_pose["body_pos"][2], 0.0)
        self.assertGreater(contact_skeleton["hip"][2], 0.2)

    def test_feet_stay_on_the_ground(self) -> None:
        for keys in (*(m.keys for m in gen.moves()), gen.idle_keys(), gen.enter_keys()):
            for pose in gen.resolve(keys):
                skeleton = gen.skeleton(pose)
                low = min(skeleton["right_foot"][1], skeleton["left_foot"][1]) * 16.0
                self.assertLessEqual(abs(low), 0.6)

    def test_idle_and_enter_hand_over_on_the_guard(self) -> None:
        guard = gen.canonical_guard()
        idle = gen.resolve(gen.idle_keys())
        self.assertEqual(guard, idle[0])
        self.assertEqual(guard, idle[-1])
        self.assertEqual(guard, gen.resolve(gen.enter_keys())[-1])
        for m in gen.moves():
            poses = gen.resolve(m.keys)
            self.assertEqual(guard, poses[0])
            self.assertEqual(guard, poses[-1])


class SelfCheckNegativeTest(unittest.TestCase):
    def _with_key(self, m: gen.Move, phase: str, **changes) -> gen.Move:
        keys = tuple(dataclasses.replace(k, **changes) if k.phase == phase else k for k in m.keys)
        return dataclasses.replace(m, keys=keys)

    def test_grip_stays_in_the_fist(self) -> None:
        # right_item position turns PAL's item-origin rotation into a rotation about the grip centre.
        for m in gen.moves():
            for pose in gen.resolve(m.keys):
                skeleton = gen.skeleton(pose)
                self.assertLess(math.dist(skeleton["grip"], skeleton["fist"]) * 16 / 0.9375, 0.1, m.animation_id)

    def test_uncompensated_item_rotation_is_rejected(self) -> None:
        with mock.patch.object(gen, "grip_compensation", return_value=(0.0, 0.0, 0.0)):
            errors = gen.check_move(move("basic_sword_04_diagonal_cut"))
        self.assertTrue(any("from the fist centre" in e for e in errors), errors)

    def test_slow_strike_is_rejected(self) -> None:
        m = move("basic_sword_05_lunge_thrust")
        keys = tuple(k for k in m.keys if k.phase != "coil")
        errors = gen.check_move(dataclasses.replace(m, keys=keys))
        self.assertTrue(any("strike segment lasts" in e for e in errors), errors)

    def test_linear_strike_is_rejected(self) -> None:
        errors = gen.check_move(self._with_key(move("basic_sword_01_thrust"), "contact", easing="linear"))
        self.assertTrue(any("is not an ease-out" in e for e in errors), errors)

    def test_drifting_hold_is_rejected(self) -> None:
        m = move("basic_sword_04_diagonal_cut")
        hold = next(k for k in m.keys if k.phase == "hold")
        errors = gen.check_move(self._with_key(m, "hold", body=(hold.body[0], hold.body[1] + 12, 0)))
        self.assertTrue(any("hold drift on body" in e for e in errors), errors)

    def test_wrong_blade_intent_is_rejected(self) -> None:
        errors = gen.check_move(self._with_key(move("basic_sword_02_horizontal_cut"), "hold", item=(0, 0, 0)))
        self.assertTrue(any("blade at tick" in e for e in errors), errors)

    def test_small_turn_is_rejected(self) -> None:
        m = move("basic_sword_03_rising_cut")
        keys = tuple(
            dataclasses.replace(k, body=(k.body[0], k.body[1] * 0.4, k.body[2])) if not gen._is_guard(k) else k
            for k in m.keys)
        errors = gen.check_move(dataclasses.replace(m, keys=keys))
        self.assertTrue(any("whole-figure turn" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
