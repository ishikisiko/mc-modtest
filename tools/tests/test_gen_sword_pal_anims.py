from __future__ import annotations

import dataclasses
import json
import re
import unittest
import zipfile

from tools import gen_sword_pal_anims as gen


BASIC_SWORD_STYLE = gen.ROOT / "src/main/java/com/example/myvillage/combat/definition/BasicSwordStyle.java"
PAL_JAR = gen.ROOT / "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"


def move(animation_id: str) -> gen.Move:
    return next(m for m in gen.MOVES if m.animation_id == animation_id)


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
            {"sword_mode_enter", "sword_ready_idle", *(m.animation_id for m in gen.MOVES)},
            set(animations))
        self.assertIs(True, animations["sword_ready_idle"]["loop"])
        self.assertEqual(1.2, animations["sword_ready_idle"]["animation_length"])
        for m in gen.MOVES:
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
        lunge = animations["basic_sword_05_lunge_thrust"]["bones"]["body"]["position"]
        self.assertTrue(any(isinstance(v, list) and any(x != 0 for x in v) for v in lunge.values()))

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


class ServerTimingTest(unittest.TestCase):
    def test_table_matches_basic_sword_style(self) -> None:
        source = BASIC_SWORD_STYLE.read_text(encoding="utf-8")
        for m in gen.MOVES:
            match = re.search(
                r'"' + re.escape(m.animation_id) + r'",\s*"[^"]+",\s*'
                r"(\d+),\s*(\d+),\s*(\d+),\s*[\d.]+,\s*\d+,\s*[\d.]+,\s*(\d+),\s*(\d+)",
                source)
            self.assertIsNotNone(match, m.animation_id)
            total, start, end, _buffer, chain = (int(v) for v in match.groups())
            self.assertEqual((total, start, end, chain), (m.total, m.active_start, m.active_end, m.chain_tick))
            step = re.search(
                r'"' + re.escape(m.animation_id) + r'".*?new StepDefinition\((\d+),', source, re.S)
            self.assertEqual(int(step.group(1)), m.step_tick, m.animation_id)


class ActionFeelTargetsTest(unittest.TestCase):
    def test_phase_timing(self) -> None:
        for m in gen.MOVES:
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
        for m in gen.MOVES:
            poses = gen.resolve(m.keys)
            phases = [k.phase for k in m.keys]
            a, b = poses[phases.index("through")], poses[phases.index("hold")]
            for bone in gen.BONE_ORDER:
                self.assertLessEqual(max(abs(a[bone][j] - b[bone][j]) for j in range(3)), 5.0, (m.animation_id, bone))

    def test_cuts_turn_the_whole_figure_and_leave_the_silhouette(self) -> None:
        for m in gen.MOVES:
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
        for m in gen.MOVES:
            if m.kind != "thrust":
                continue
            _, pose, skeleton = pose_at(m, "through")
            self.assertLessEqual(pose["body"][1], -40.0, m.animation_id)
            blade_yaw, blade_elevation = gen.direction_angles(skeleton["grip"], skeleton["tip"])
            self.assertLessEqual(abs(blade_yaw), 20.0, m.animation_id)
            self.assertLessEqual(abs(blade_elevation), 15.0, m.animation_id)
            self.assertLess(skeleton["left_hand"][0], -0.45, m.animation_id)

    def test_hip_drop_and_lunge(self) -> None:
        for m in gen.MOVES:
            for phase in ("contact", "through", "hold"):
                _, pose, _ = pose_at(m, phase)
                self.assertTrue(-3.5 <= pose["body_pos"][1] <= -2.0, (m.animation_id, phase))
        lunge = move("basic_sword_05_lunge_thrust")
        coil, coil_pose, _ = pose_at(lunge, "coil")
        _, contact_pose, contact_skeleton = pose_at(lunge, "contact")
        self.assertEqual(lunge.step_tick, coil.tick)
        # Negative PAL body z moves the figure forward (PlayerRendererMixin.applyBodyTransforms).
        self.assertLessEqual(contact_pose["body_pos"][2], -4.0)
        self.assertGreater(coil_pose["body_pos"][2], 0.0)
        self.assertGreater(contact_skeleton["hip"][2], 0.2)

    def test_feet_stay_on_the_ground(self) -> None:
        for keys in (*(m.keys for m in gen.MOVES), gen.idle_keys(), gen.enter_keys()):
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
        for m in gen.MOVES:
            poses = gen.resolve(m.keys)
            self.assertEqual(guard, poses[0])
            self.assertEqual(guard, poses[-1])


class SelfCheckNegativeTest(unittest.TestCase):
    def _with_key(self, m: gen.Move, phase: str, **changes) -> gen.Move:
        keys = tuple(dataclasses.replace(k, **changes) if k.phase == phase else k for k in m.keys)
        return dataclasses.replace(m, keys=keys)

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
