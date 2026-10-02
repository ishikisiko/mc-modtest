from __future__ import annotations

import copy
import dataclasses
import io
import json
import math
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest import mock

from tools import gen_sword_pal_anims as gen


PAL_JAR = gen.ROOT / "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"
PIKE = "myvillage:test_pike"


def style() -> dict:
    return copy.deepcopy(gen.combat_styles()[gen.BASIC_SWORD.style_id])


def synthetic_weapon(root: Path, name: str = "test_pike", scale: float = 0.85, grip_y: float = -2.0,
                     off_hand_y: float | None = 11.0, grip_shift_px: float = 0.0) -> tuple[Path, Path]:
    """A two-handed weapon in the Qingfeng frame spanning y = -16..32 (contract + 3D model display).

    The display translation is solved so the grip centre lands where the Qingfeng grip does (in the
    fist); ``grip_shift_px`` moves it off the fist along the display y axis.
    """
    sword_display = json.loads(gen.SWORD_MODEL_3D.read_text(encoding="utf-8"))["display"]["thirdperson_righthand"]
    sword_grip = json.loads(gen.SWORD_GEOMETRY.read_text(encoding="utf-8"))["grip_center"]
    rx, ry, rz = sword_display["rotation"]
    rotation = gen._chain(gen._r4("x", rx), gen._r4("y", ry), gen._r4("z", rz))
    s0 = sword_display["scale"][0]
    grip = (sword_grip[0], grip_y, sword_grip[2])
    delta = gen._apply_raw(rotation, [s0 * (sword_grip[i] / 16 - 0.5) - scale * (grip[i] / 16 - 0.5) for i in range(3)])
    translation = [round(sword_display["translation"][i] + 16 * delta[i] + (grip_shift_px if i == 1 else 0.0), 6)
                   for i in range(3)]
    geometry = {
        "format": 2, "units": "model_pixels", "model": f"myvillage:item/{name}_3d",
        "axes": {"length": "+y", "flat_normal": "x", "edge": "z", "center_x": 8.0, "center_z": 8.0},
        "grip_center": [8.0, grip_y, 8.0],
        "handle": {"y": [-14.5, 22.0], "half_width": 0.7, "half_thickness": 0.7},
        "collar": {"y": [22.0, 24.0], "half_width": 1.5, "half_thickness": 1.5},
        "butt": {"y": [-16.0, -14.5], "half_width": 0.9, "half_thickness": 0.9},
        "head_base": [8.0, 24.0, 8.0], "head_tip": [8.0, 32.0, 8.0], "overall_y": [-16.0, 32.0],
    }
    if off_hand_y is not None:
        geometry["off_hand_grip_center"] = [8.0, off_hand_y, 8.0]
    geometry_path = root / f"assets/myvillage/combat/{name}_geometry.json"
    model_path = root / f"assets/myvillage/models/item/{name}_3d.json"
    geometry_path.parent.mkdir(parents=True, exist_ok=True)
    model_path.parent.mkdir(parents=True, exist_ok=True)
    geometry_path.write_text(json.dumps(geometry), encoding="utf-8")
    model_path.write_text(json.dumps({"display": {"thirdperson_righthand": {
        "rotation": sword_display["rotation"], "translation": translation, "scale": [scale] * 3}}}), encoding="utf-8")
    return geometry_path, model_path


def pike_style() -> dict:
    """The sword style renamed to a synthetic second style (same timing)."""
    data = style()
    data["id"] = PIKE
    data["animations"] = {"ready_idle": "myvillage:pike_ready_idle", "mode_enter": "myvillage:pike_mode_enter"}
    for entry in data["moves"]:
        entry["id"] = entry["id"].replace("basic_sword", "test_pike")
    return data


def pike_table(root: Path, weapon: tuple[Path, Path] | None = None, **changes) -> gen.PoseTable:
    """A second, one-handed table: the sword poses on the synthetic long weapon."""
    geometry, model = synthetic_weapon(root) if weapon is None else weapon
    table = dataclasses.replace(
        gen.BASIC_SWORD, style_id=PIKE, output=root / "assets/myvillage/player_animations/pike_combat.json",
        moves={move_id.replace("basic_sword", "test_pike"): poses for move_id, poses in gen.BASIC_SWORD_MOVES.items()},
        geometry=geometry, item_model=model)
    return dataclasses.replace(table, **changes)


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
                mock.patch.object(gen, "POSE_TABLES", (gen.BASIC_SWORD,)), redirect_stderr(stderr):
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


class TempDirTest(unittest.TestCase):
    def setUp(self) -> None:
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)


class PerTableRigTest(TempDirTest):
    def test_sword_table_names_the_qingfeng_rig(self) -> None:
        self.assertEqual(gen.SWORD_GEOMETRY, gen.BASIC_SWORD.geometry)
        self.assertEqual(gen.SWORD_MODEL_3D, gen.BASIC_SWORD.item_model)
        self.assertFalse(gen.BASIC_SWORD.two_handed)
        self.assertEqual(gen.SWORD_RULES, gen.BASIC_SWORD.rules)

    def test_forward_kinematics_measure_the_table_weapon(self) -> None:
        pike = pike_table(self.root)
        sword_sk = gen.skeleton(gen.canonical_guard(), gen.BASIC_SWORD)
        pike_sk = gen.skeleton(gen.canonical_guard(pike), pike)
        scale = 0.85 * gen.PLAYER_SCALE
        self.assertAlmostEqual((32 + 2) / 16 * scale, math.dist(pike_sk["grip"], pike_sk["tip"]), places=6)
        self.assertAlmostEqual(48 / 16 * scale, math.dist(pike_sk["butt"], pike_sk["tip"]), places=6)
        self.assertLess(math.dist(sword_sk["grip"], sword_sk["tip"]), 1.0)
        # Same frame and grip in the fist: the blade direction is the sword's, the tip twice as far.
        self.assertLess(math.dist(pike_sk["grip"], pike_sk["fist"]) * 16 / gen.PLAYER_SCALE, 0.1)
        self.assertLess(gen.angle_between(gen.direction_angles(sword_sk["grip"], sword_sk["tip"]),
                                          gen.direction_angles(pike_sk["grip"], pike_sk["tip"])), 0.1)

    def test_sword_poses_pass_every_check_on_a_one_handed_long_weapon(self) -> None:
        pike = pike_table(self.root)
        data = pike_style()
        self.assertEqual([], gen.check_document(gen.build_document(pike, data), pike, data))

    def test_grip_compensation_uses_the_table_grip(self) -> None:
        shifted = pike_table(self.root, synthetic_weapon(self.root, "shifted", grip_shift_px=2.0))
        rotation = (80, 17, -2)
        self.assertNotEqual(gen.grip_compensation(rotation), gen.grip_compensation(rotation, shifted))

        def grip_off(item):
            k = gen.key(0, "probe", "linear", **dict(gen.GUARD, item=item))
            sk = gen.skeleton(gen._resolve_key(k, None, shifted), shifted)
            return math.dist(sk["grip"], sk["fist"]) * 16 / gen.PLAYER_SCALE

        # The item turns about the table's own grip: its offset from the fist does not change.
        self.assertGreater(grip_off((0, 0, 0)), 1.0)
        self.assertAlmostEqual(grip_off((0, 0, 0)), grip_off(rotation), places=2)
        errors = gen.check_keys("idle", gen.idle_keys(), shifted)
        self.assertTrue(any("from the fist centre" in e for e in errors), errors)
        self.assertEqual([], gen.check_keys("idle", gen.idle_keys()))

    def test_contract_for_another_model_is_rejected(self) -> None:
        geometry, _ = synthetic_weapon(self.root)
        _, other_model = synthetic_weapon(self.root, "other")
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.load_rig(geometry, other_model)
        self.assertIn("test_pike_3d", str(raised.exception))

    def test_missing_contract_is_a_pose_table_error(self) -> None:
        with self.assertRaises(gen.PoseTableError):
            gen.load_rig(self.root / "missing_geometry.json", gen.SWORD_MODEL_3D)


class MultiTableTest(TempDirTest):
    def tables(self):
        sword = dataclasses.replace(gen.BASIC_SWORD, output=self.root / "assets/myvillage/player_animations/sword_combat.json")
        sword.output.parent.mkdir(parents=True, exist_ok=True)
        return sword, pike_table(self.root)

    def run_main(self, argv, tables):
        styles = {gen.BASIC_SWORD.style_id: style(), PIKE: pike_style()}
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch.object(gen, "combat_styles", return_value=styles), \
                mock.patch.object(gen, "POSE_TABLES", tables), redirect_stdout(stdout), redirect_stderr(stderr):
            status = gen.main(argv)
        return status, stdout.getvalue(), stderr.getvalue()

    def test_every_table_writes_and_checks_its_own_file(self) -> None:
        sword, pike = self.tables()
        status, out, err = self.run_main([], (sword, pike))
        self.assertEqual(0, status, err)
        self.assertEqual(gen.OUTPUT.read_bytes(), sword.output.read_bytes())
        animations = json.loads(pike.output.read_text(encoding="utf-8"))["animations"]
        self.assertIn("pike_ready_idle", animations)
        self.assertIn("test_pike_05_lunge_thrust", animations)
        self.assertNotIn("sword_ready_idle", animations)
        status, out, err = self.run_main(["--check"], (sword, pike))
        self.assertEqual(0, status, err)
        self.assertIn("OK sword_combat.json", out)
        self.assertIn("OK pike_combat.json", out)
        status, out, _ = self.run_main(["--report"], (sword, pike))
        self.assertEqual(0, status)
        self.assertIn(f"## {PIKE} -> pike_combat.json", out)
        self.assertIn("== test_pike_02_horizontal_cut", out)
        self.assertIn("== basic_sword_02_horizontal_cut", out)

    def test_drift_is_reported_per_table(self) -> None:
        sword, pike = self.tables()
        self.run_main([], (sword, pike))
        pike.output.write_text("{}\n", encoding="utf-8")
        status, out, err = self.run_main(["--check"], (sword, pike))
        self.assertEqual(1, status)
        self.assertIn("OK sword_combat.json", out)
        self.assertIn("DRIFT", err)
        self.assertIn("pike_combat.json", err)

    def test_two_tables_cannot_share_an_output(self) -> None:
        sword, pike = self.tables()
        styles = {gen.BASIC_SWORD.style_id: style(), PIKE: pike_style()}
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.check_tables(styles, (sword, dataclasses.replace(pike, output=sword.output)))
        self.assertIn("sword_combat.json", str(raised.exception))

    def test_cut_paths_are_per_table(self) -> None:
        # A spear-like table whose horizontal path sweeps right -> left fails the sword's swing.
        right_to_left = gen.CutPath((gen.PathRule(
            "sweep must travel the player's right -> left", (("wind", "tip_x", ">", 0.4), ("hold", "tip_x", "<", -0.6))),))
        pike = pike_table(self.root, cut_paths=dict(gen.SWORD_CUT_PATHS, left_to_right=right_to_left))
        horizontal = next(m for m in gen.bind(pike, pike_style()) if m.cut_path == "left_to_right")
        errors = gen.check_move(horizontal, pike)
        self.assertIn("test_pike_02_horizontal_cut: sweep must travel the player's right -> left", errors)
        without_rising = {name: path for name, path in gen.SWORD_CUT_PATHS.items() if name != "rising"}
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.bind(pike_table(self.root, cut_paths=without_rising), pike_style())
        self.assertIn("unknown cut_path 'rising'", str(raised.exception))

    def test_rules_are_per_table(self) -> None:
        thrust = next(m for m in gen.bind(pike_table(self.root), pike_style()) if m.kind == "thrust")
        self.assertEqual([], gen.check_move(thrust, pike_table(self.root)))
        far = pike_table(self.root, rules=dataclasses.replace(gen.SWORD_RULES, thrust_reach=(4.0, 4.5)))
        self.assertTrue(any("thrust reach" in e for e in gen.check_move(thrust, far)))
        bladed_right = pike_table(self.root, rules=dataclasses.replace(gen.SWORD_RULES, thrust_body_yaw=(30.0, None)))
        self.assertTrue(any("not bladed" in e for e in gen.check_move(thrust, bladed_right)))


# A two-handed guard whose shaft passes through the left fist's reach ahead of the right hand.
SPEAR_GUARD = dict(body=(4, -15, 0), stance=("R", 24, 0), rarm=(-150, -20), larm=gen.ON_SHAFT,
                   item=(-60, -90, 0), blade=(-12, 13))
LEFT_FIST_REACH = math.hypot(1, 8) / 16 * gen.PLAYER_SCALE


class OffHandTest(TempDirTest):
    def setUp(self) -> None:
        super().setUp()
        self.table = pike_table(self.root, two_handed=True, guard=SPEAR_GUARD)

    def resolve_one(self, **changes):
        k = gen.key(0, "probe", "linear", **dict(SPEAR_GUARD, **changes))
        pose = gen._resolve_key(k, None, self.table)
        return pose, gen.skeleton(pose, self.table)

    def shaft_oracle(self, sk, preferred):
        """Brute force over the handle: (smallest residual px, shaft y of the reachable point nearest the
        preferred y, shaft y of the smallest residual)."""
        rig = gen.weapon_rig(self.table)
        h0, h1 = rig.handle
        a, b, shoulder = sk["handle_start"], sk["handle_end"], sk["left_shoulder"]
        gap = gen.FIST_WIDTH / (math.dist(a, b) / (h1 - h0))
        samples = []
        for i in range(8001):
            s = h0 + (h1 - h0) * i / 8000
            if abs(s - rig.grip_y) < gap:
                continue
            point = [a[j] + (b[j] - a[j]) * (s - h0) / (h1 - h0) for j in range(3)]
            samples.append((abs(math.dist(point, shoulder) - LEFT_FIST_REACH) * 16 / gen.PLAYER_SCALE, s))
        best, argmin = min(samples)
        return best, min((s for r, s in samples if r <= best + 0.01), key=lambda s: abs(s - preferred)), argmin

    def test_left_hand_lands_on_the_shaft(self) -> None:
        _, sk = self.resolve_one()
        offset, at = gen.off_hand_offset(sk, self.table)
        self.assertLess(offset, 0.05)
        residual, expected, _ = self.shaft_oracle(sk, 11.0)
        self.assertLess(residual, 0.01)
        self.assertAlmostEqual(expected, at, delta=0.1)
        self.assertGreater(at, -2.0 + 4 / 0.85)  # ahead of the right fist, clear of it
        self.assertEqual([], [e for e in gen.check_keys("guard", (gen.key(0, "guard", "linear", **SPEAR_GUARD),), self.table)
                              if "shaft" in e])

    def test_preferred_point_out_of_reach_falls_back_to_the_nearest_reachable_point(self) -> None:
        _, sk = self.resolve_one(larm=gen.OnShaft(at=20.0))
        offset, at = gen.off_hand_offset(sk, self.table)
        self.assertLess(offset, 0.05)
        self.assertLess(at, 19.0)
        _, expected, _ = self.shaft_oracle(sk, 20.0)
        self.assertAlmostEqual(expected, at, delta=0.1)

    def test_unreachable_shaft_reports_the_residual(self) -> None:
        guard = dict(gen.GUARD, larm=gen.ON_SHAFT)  # the sword guard: the shaft runs ahead, out of reach
        pose, sk = self.resolve_one(**guard)
        offset, at = gen.off_hand_offset(sk, self.table)
        s, residual = gen.shaft_target(pose, self.table)
        oracle_residual, _, oracle_s = self.shaft_oracle(sk, 11.0)
        self.assertGreater(offset, 3.0)
        self.assertAlmostEqual(oracle_residual, residual * 16 / gen.PLAYER_SCALE, delta=0.05)
        self.assertAlmostEqual(oracle_s, s, delta=0.1)
        self.assertAlmostEqual(offset, oracle_residual, delta=0.1)  # the fist is as close as the arm allows
        errors = gen.check_keys("probe", (gen.key(0, "guard", "linear", **guard),), self.table)
        self.assertIn(f"probe: left hand {offset:.2f} px off the shaft at tick 0", "\n".join(errors))
        self.assertTrue(any(f"nearest reachable shaft y {at:.1f}, wanted 11" in e for e in errors), errors)

    def segment(self, larm=gen.ON_SHAFT):
        start = gen.key(0, "guard", "linear", **SPEAR_GUARD)
        end = gen.key(6, "turn", "easeinoutsine", (4, 20, 0), ("R", 24, 0), (-120, -40), larm, (-30, -60, 30), (0, 0))
        return start, end

    def test_drift_between_keys_names_the_move_and_tick(self) -> None:
        start, end = self.segment()
        errors = [e for e in gen.check_keys("test_pike_02_sweep", (start, end), self.table) if "shaft" in e]
        self.assertEqual(1, len(errors), errors)
        self.assertRegex(errors[0], r"^test_pike_02_sweep: left hand leaves the shaft by \d+\.\d\d px near tick "
                                    r"3\.00 between keys 0 and 6 \(> 1\.9\); add a key with larm=ON_SHAFT at tick 3$")
        middle = gen.key(3, "mid", "easeinoutsine", (4, 2.5, 0), ("R", 24, 0), (-135, -30), gen.ON_SHAFT,
                         (-45, -75, 15), (0, 0))
        self.assertEqual([], [e for e in gen.check_keys("x", (start, middle, end), self.table) if "shaft" in e])

    def test_released_hand_is_not_held_to_the_shaft(self) -> None:
        start, end = self.segment(larm=(-30, -20))
        self.assertEqual([], [e for e in gen.check_keys("x", (start, end), self.table) if "shaft" in e])

    def test_adjacent_keys_get_a_different_hint(self) -> None:
        start, end = self.segment()
        end = dataclasses.replace(end, tick=1)
        errors = [e for e in gen.check_keys("x", (start, end), self.table) if "shaft" in e]
        self.assertTrue(errors and "one tick apart" in errors[0], errors)

    def test_on_shaft_needs_a_two_handed_table(self) -> None:
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.bind(dataclasses.replace(self.table, two_handed=False), pike_style())
        self.assertIn("not two_handed", str(raised.exception))

    def test_two_handed_table_needs_an_off_hand_grip(self) -> None:
        one_hand = synthetic_weapon(self.root, "one_hand", off_hand_y=None)
        table = pike_table(self.root, one_hand, two_handed=True, guard=SPEAR_GUARD)
        with self.assertRaises(gen.PoseTableError) as raised:
            gen.bind(table, pike_style())
        self.assertIn("off_hand_grip_center", str(raised.exception))

    def test_report_shows_the_left_hand_on_the_shaft(self) -> None:
        table = pike_table(self.root, two_handed=True)
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            gen.report(table, pike_style())
        self.assertIn("two-handed", stdout.getvalue())
        self.assertIn(" free", stdout.getvalue())


SPEAR = gen.BASIC_SPEAR


def spear_keys() -> list[tuple[str, tuple[gen.Key, ...]]]:
    """Every animation of the spear table: mode entry, ready idle and the five moves."""
    style_data = gen.combat_styles()[SPEAR.style_id]
    enter, idle = gen.animation_names(style_data)
    return [(enter, SPEAR.mode_enter), (idle, SPEAR.ready_idle),
            *((m.animation_id, m.keys) for m in gen.moves(SPEAR))]


def spear_probe(**changes) -> tuple[gen.Key, ...]:
    """A one-key animation: the spear guard with some fields changed."""
    return (gen.key(0, "probe", "linear", **dict(SPEAR.guard, **changes)),)


class SpearTableTest(unittest.TestCase):
    """The committed Lingxiao spear table (myvillage:basic_spear)."""

    def test_table_binds_to_the_spear_style(self) -> None:
        self.assertIn(SPEAR, gen.POSE_TABLES)
        self.assertTrue(SPEAR.two_handed)
        self.assertEqual("spear_combat.json", SPEAR.output.name)
        self.assertEqual("lingxiao_spear_geometry.json", SPEAR.geometry.name)
        self.assertEqual("lingxiao_spear_3d.json", SPEAR.item_model.name)
        data = gen.combat_styles()[SPEAR.style_id]
        bound = gen.moves(SPEAR)
        self.assertEqual([m["id"] for m in data["moves"]], [m.move_id for m in bound])
        self.assertEqual(["thrust", "cut", "cut", "cut", "thrust"], [m.kind for m in bound])
        self.assertEqual([None, "left_to_right", "rising", "descending", None], [m.cut_path for m in bound])
        self.assertEqual([False, False, False, False, True], [m.lunge for m in bound])
        self.assertEqual(("spear_mode_enter", "spear_ready_idle"), gen.animation_names(data))

    def test_committed_json_matches_generator(self) -> None:
        self.assertEqual(gen.render(gen.build_document(SPEAR)), SPEAR.output.read_text(encoding="utf-8"),
                         "spear_combat.json drifted; run python3 tools/gen_sword_pal_anims.py")

    def test_self_checks_pass(self) -> None:
        self.assertEqual([], gen.check_document(gen.build_document(SPEAR), SPEAR))

    def test_sword_output_is_untouched_by_the_spear_table(self) -> None:
        self.assertEqual(gen.render(gen.build_document(gen.BASIC_SWORD)), gen.OUTPUT.read_text(encoding="utf-8"))
        for name in ("weapon_ground_clearance", "min_hand_separation_px", "shaft_body_clearance_px", "lunge_body_yaw"):
            self.assertIsNone(getattr(gen.SWORD_RULES, name), name)

    def test_left_hand_is_on_the_shaft_and_apart_from_the_right(self) -> None:
        rig = gen.weapon_rig(SPEAR)
        held = 0
        for name, keys in spear_keys():
            for k, pose in zip(keys, gen.resolve(keys, SPEAR)):
                if not isinstance(k.larm, gen.OnShaft):
                    continue
                held += 1
                offset, at = gen.off_hand_offset(gen.skeleton(pose, SPEAR), SPEAR)
                self.assertLessEqual(offset, gen.OFF_HAND_KEY_TOLERANCE_PX, (name, k.tick))
                self.assertGreaterEqual(at - rig.grip_y, SPEAR.rules.min_hand_separation_px, (name, k.tick))
                self.assertLessEqual(at, rig.handle[1], (name, k.tick))
        self.assertGreater(held, 30)

    def test_left_hand_schedule(self) -> None:
        # The leading hand lets go only where holding on would cripple the move (see the table's
        # comment): never in the thrust, idle or guard; for the swing of the sweep and the flick,
        # the overhead smash and the lunge's finish; always back on the shaft before the guard.
        free = {name: [k.tick for k in keys if not isinstance(k.larm, gen.OnShaft)] for name, keys in spear_keys()}
        self.assertEqual({
            "spear_mode_enter": [0, 4],
            "spear_ready_idle": [],
            "basic_spear_01_mid_thrust": [],
            "basic_spear_02_sweep": [4, 5, 6, 7, 10],
            "basic_spear_03_rising_flick": [3, 4, 5, 6, 7, 10, 12],
            "basic_spear_04_overhead_smash": [3, 4, 6, 7, 8, 9, 13, 15],
            "basic_spear_05_dragon_lunge": [8, 10, 14, 16],
        }, free)
        for name, keys in spear_keys():
            self.assertIsInstance(keys[-1].larm, gen.OnShaft, name)
            if name != "spear_mode_enter":
                self.assertIsInstance(keys[-2].larm, gen.OnShaft, name)  # regripped before the guard

    def test_released_arm_is_never_a_t_pose_or_hanging(self) -> None:
        # A free left arm counterbalances behind or reaches forward: never straight out to the side
        # level with the shoulder, never hanging straight down.
        for name, keys in spear_keys():
            if name == "spear_mode_enter":
                continue
            for k, pose in zip(keys, gen.resolve(keys, SPEAR)):
                if isinstance(k.larm, gen.OnShaft):
                    continue
                direction = gen.limb_direction(pose["body"], pose["left_arm"][:2])
                yaw, elevation = gen.direction_angles((0, 0, 0), direction)
                relative = (yaw - pose["body"][1] + 180) % 360 - 180
                self.assertGreater(elevation, -80, (name, k.tick))
                self.assertFalse(-110 < relative < -70 and abs(elevation) < 20, (name, k.tick, relative, elevation))

    def test_long_weapon_margins(self) -> None:
        rules = SPEAR.rules
        for name, keys in spear_keys():
            margins = gen.long_weapon_margins(keys, SPEAR)
            self.assertGreaterEqual(margins["ground"], rules.weapon_ground_clearance, name)
            self.assertGreaterEqual(margins["hands"], rules.min_hand_separation_px, name)
            self.assertGreaterEqual(margins["body"], rules.shaft_body_clearance_px, name)

    def test_guard_is_a_bladed_left_lead(self) -> None:
        guard = SPEAR.guard
        self.assertEqual("L", guard["stance"][0])
        self.assertGreater(guard["body"][1], 40)  # turned right: left shoulder and hand lead
        sk = gen.skeleton(gen.canonical_guard(SPEAR), SPEAR)
        yaw, elevation = gen.direction_angles(sk["grip"], sk["tip"])
        self.assertLessEqual(abs(yaw), 5)
        self.assertTrue(0 <= elevation <= 20, elevation)
        self.assertLess(sk["right_hand"][1], 0.8)  # rear hand by the hip
        self.assertLess(sk["right_hand"][2], sk["left_hand"][2])  # left hand leads

    def test_cut_senses_agree_with_the_hit_samples(self) -> None:
        # A spear cut's samples run from near the attacker out along the spearhead's direction, so its
        # sense is read from the samples' far ends (where the hit volume and the world trail point),
        # in the rig frame (+x = the player's right; the server's +x is the left), and every far end
        # must point where the posed spearhead points at the sample's time (seen from the trail
        # pivot, within SENSE_TOLERANCE_DEG).  The sword's diagonal cuts keep their slanted-line
        # reading in SWORD_CUT_PATHS and test_cut_directions_match_hitboxes.
        pivot = (0.0, gen.TRAIL_PIVOT_HEIGHT, 0.0)

        def angle(a, b):
            dot = sum(a[i] * b[i] for i in range(3)) / math.sqrt(sum(v * v for v in a) * sum(v * v for v in b))
            return math.degrees(math.acos(max(-1.0, min(1.0, dot))))

        senses = {}
        for m in gen.moves(SPEAR):
            if m.kind != "cut":
                continue
            samples = gen.hit_samples(m)
            poses = gen.resolve(m.keys, SPEAR)
            far = [(-end[0], end[1], end[2]) for _, _, end in samples]
            for (_, _, end), time, point in zip(samples, gen.trail_sample_times(samples), far):
                tip = gen.skeleton(gen.sample(m.keys, poses, time), SPEAR)["tip"]
                self.assertLessEqual(angle([point[i] - pivot[i] for i in range(3)], [tip[i] - pivot[i] for i in range(3)]),
                                     self.SENSE_TOLERANCE_DEG, (m.animation_id, time))
            senses[m.cut_path] = far
        sweep, flick, smash = senses["left_to_right"], senses["rising"], senses["descending"]
        # Sweep: left to right, level.
        self.assertLess(sweep[0][0], -1.0)
        self.assertGreater(sweep[-1][0], 1.0)
        yaws = [math.degrees(math.atan2(p[0], p[2])) for p in sweep]
        self.assertEqual(sorted(yaws), yaws)
        for p in sweep:
            self.assertLessEqual(abs(math.degrees(math.atan2(p[1] - pivot[1], math.hypot(p[0], p[2])))), 10.0)
        # Flick: starts low on the right, ends high on the left.
        self.assertGreater(flick[0][0], 0.3)
        self.assertLess(flick[0][1], 1.0)
        self.assertLess(flick[-1][0], -0.3)
        self.assertGreater(flick[-1][1], 2.0)
        # Smash: starts high near the centre line, ends low in front and slightly to the right.
        self.assertGreater(smash[0][1], 2.0)
        self.assertLessEqual(abs(smash[0][0]), 0.3)
        self.assertLess(smash[-1][1], 0.6)
        self.assertGreater(smash[-1][2], 2.0)
        self.assertTrue(0.0 < smash[-1][0] <= 1.0, smash[-1])

    SENSE_TOLERANCE_DEG = 15.0  # the sweep's samples are the authored 140-degree arc; its pose lags it by <10

    def test_smash_crosses_the_top_without_a_reversal(self) -> None:
        # The head comes over the top and down: at the contact key it may stop (the strike eases
        # out) but must not turn back on itself.
        smash = gen.moves(SPEAR)[3]
        poses = gen.resolve(smash.keys, SPEAR)

        def tip(t):
            return gen.skeleton(gen.sample(smash.keys, poses, t), SPEAR)["tip"]

        before = [tip(7.0)[i] - tip(6.9)[i] for i in range(3)]
        after = [tip(7.1)[i] - tip(7.0)[i] for i in range(3)]
        dot = sum(before[i] * after[i] for i in range(3)) / math.sqrt(sum(v * v for v in before) * sum(v * v for v in after))
        self.assertLess(math.degrees(math.acos(max(-1.0, min(1.0, dot)))), 45.0)
        self.assertLess(before[1], 0.0)  # already descending when the contact key arrives

    def through(self, m):
        poses = gen.resolve(m.keys, SPEAR)
        index = [k.phase for k in m.keys].index("through")
        return m.keys[index], poses[index], gen.skeleton(poses[index], SPEAR)

    def test_thrust_is_short_two_handed_and_slides(self) -> None:
        thrust = gen.moves(SPEAR)[0]
        k, pose, sk = self.through(thrust)
        self.assertGreaterEqual(sk["tip"][2], SPEAR.rules.thrust_reach[0])
        self.assertIsInstance(k.larm, gen.OnShaft)
        self.assertGreaterEqual(pose["body"][1], 40)  # still bladed, hips turned in from the coil
        # The rear hand drives forward: the shaft slides through the leading hand toward the tip.
        guard_at = gen.off_hand_offset(gen.skeleton(gen.canonical_guard(SPEAR), SPEAR), SPEAR)[1]
        self.assertLess(gen.off_hand_offset(sk, SPEAR)[1], guard_at - 4.0)

    def test_lunge_is_one_handed_and_reaches_clearly_further(self) -> None:
        thrust, lunge = gen.moves(SPEAR)[0], gen.moves(SPEAR)[4]
        _, thrust_pose, thrust_sk = self.through(thrust)
        k, pose, sk = self.through(lunge)
        self.assertFalse(isinstance(k.larm, gen.OnShaft))  # one-handed finish
        self.assertGreaterEqual(sk["tip"][2], thrust_sk["tip"][2] + 0.5)
        self.assertGreaterEqual(sk["tip"][2], SPEAR.rules.thrust_reach[1])
        self.assertLessEqual(pose["body"][1], 20)  # the rear shoulder driven through, not bladed
        self.assertGreater(pose["body"][0], thrust_pose["body"][0] + 8)  # longer forward lean
        self.assertLess(pose["body_pos"][2], -8)  # hips driven forward
        arm = gen._unit([sk["right_hand"][i] - sk["right_shoulder"][i] for i in range(3)])
        shaft = gen._unit([sk["tip"][i] - sk["grip"][i] for i in range(3)])
        self.assertGreater(sum(arm[i] * shaft[i] for i in range(3)), math.cos(math.radians(30)))  # in line
        self.assertLess(sk["left_hand"][2], sk["hip"][2])  # the free arm thrown back

    def test_smash_goes_overhead(self) -> None:
        smash = gen.moves(SPEAR)[3]
        poses = gen.resolve(smash.keys, SPEAR)
        phases = [k.phase for k in smash.keys]
        raised = gen.skeleton(poses[phases.index("anticipation")], SPEAR)
        self.assertGreater(raised["tip"][1], 2.9)  # spear raised high
        self.assertGreater(raised["right_hand"][1], raised["right_shoulder"][1] + 0.3)  # rear arm up
        self.assertLess(raised["tip"][2], 0.0)  # tip behind the head
        over = gen.skeleton(poses[phases.index("coil")], SPEAR)  # coming over the top into the strike
        self.assertGreater(over["tip"][1], 2.9)
        self.assertGreater(over["right_hand"][1], over["right_shoulder"][1] + 0.3)
        hold = gen.skeleton(poses[phases.index("hold")], SPEAR)
        self.assertLess(hold["tip"][1], 0.6)
        self.assertGreater(hold["tip"][2], 1.8)  # driven into the ground in front, not by the feet
        self.assertGreater(poses[phases.index("hold")]["body"][0], 25)  # the body folds into it


class SpearRuleNegativeTest(unittest.TestCase):
    def test_weapon_into_the_ground_is_rejected(self) -> None:
        item = SPEAR.guard["item"]
        errors = gen.check_keys("probe", spear_probe(item=(item[0] + 90, item[1], item[2])), SPEAR)
        self.assertTrue(any("blade" in e and "above the ground" in e for e in errors), errors)

    def test_shaft_through_the_body_is_rejected(self) -> None:
        item = SPEAR.guard["item"]
        errors = gen.check_keys("probe", spear_probe(item=(item[0] - 60, item[1], item[2])), SPEAR)
        self.assertTrue(any("shaft 0.00 px from the head" in e for e in errors), errors)

    def test_hands_too_close_are_rejected(self) -> None:
        thrust = gen.moves(SPEAR)[0]
        close = dataclasses.replace(SPEAR, rules=dataclasses.replace(SPEAR.rules, min_hand_separation_px=6.0))
        errors = gen.check_keys(thrust.animation_id, thrust.keys, close)
        self.assertTrue(any("hands only" in e and "apart on the shaft" in e for e in errors), errors)
        self.assertEqual([], gen.check_keys(thrust.animation_id, thrust.keys, SPEAR))

    def test_rules_are_checked_between_keys(self) -> None:
        # Both keys keep the shaft clear of the body; the Euler interpolation between them swings
        # it through the torso.
        item = SPEAR.guard["item"]
        start = spear_probe()[0]
        end = dataclasses.replace(spear_probe(item=(item[0] - 180, item[1] + 45, item[2] - 180))[0], tick=4)
        poses = gen.resolve((start, end), SPEAR)
        for pose in poses:
            self.assertGreater(gen.shaft_body_clearance(pose, gen.skeleton(pose, SPEAR))[0], 0.5)
        errors = [e for e in gen.check_keys("probe", (start, end), SPEAR) if "px from the torso" in e]
        self.assertEqual(1, len(errors), errors)  # one error per body part
        self.assertRegex(errors[0], r"^probe: shaft 0\.00 px from the torso near tick [123]\.\d\d ")

    def test_leg_branch_follows_the_previous_key(self) -> None:
        # A body turned ~100 degrees off the stance line flipped a leg between Euler branches
        # before the stance solve followed the previous key.
        for name, keys in spear_keys():
            poses = gen.resolve(keys, SPEAR)
            for a, b in zip(poses, poses[1:]):
                for bone in ("right_leg", "left_leg"):
                    # A flip between the two Euler branches shows as about 180 degrees.
                    self.assertLess(max(abs(a[bone][j] - b[bone][j]) for j in range(2)), 160, (name, bone))


# The v1 sweep (the in-game capture of 2026-10-02): at its follow-through the arms were spread in a
# T and the shaft ran back through the right arm and across the chest.  The torso-only check passed
# it at 1.39 px.
V1_SWEEP = (
    (0, "guard", "linear", None),
    (2, "wind", "easeinoutsine", ((6, 30, 0), ("L", 31, -5), (50, -70), gen.ON_SHAFT, (-24.2, -101.1, -19.3), (-55, 8))),
    (3, "anticipation", "easeinoutsine", ((4, 8, 0), ("L", 32, -10), (5, -30), gen.ON_SHAFT, (-33.5, -109.6, -58.5), (-95, 6))),
    (4, "coil", "linear", ((5, 4, 0), ("L", 32, -10), (35, -30), gen.ON_SHAFT, (-66.6, -149, -39.3), (-98, 6))),
    (5, "contact", "easeoutcubic", ((8, 48, 0), ("L", 36, -10), (70, -40), gen.OnShaft(at=12), (-45.2, -133.9, -38.9), (-55, 0))),
    (6, "sweep", "linear", ((10, 78, 0), ("L", 38, -10), (150, -40), gen.OnShaft(at=12), (-54.7, -147.8, -22.5), (0, -2))),
    (7, "through", "easeoutsine", ((10, 112, 0), ("L", 40, -10), (-135, -20), gen.OnShaft(at=13), (-76.8, -163.3, -17.8), (64, -3))),
)


def v1_sweep_keys() -> tuple[gen.Key, ...]:
    return tuple(gen.key(t, phase, easing, **SPEAR.guard) if spec is None else gen.key(t, phase, easing, *spec)
                 for t, phase, easing, spec in V1_SWEEP)


class ShaftClearanceRegressionTest(unittest.TestCase):
    def test_v1_sweep_t_pose_is_caught(self) -> None:
        keys = v1_sweep_keys()
        pose = gen.resolve(keys, SPEAR)[-1]
        sk = gen.skeleton(pose, SPEAR)
        gaps = gen.shaft_body_gaps(pose, sk)
        self.assertGreater(gaps["torso"], 1.0)  # what the torso/head/legs-only check measured
        self.assertEqual(0.0, gaps["right upper arm"])  # the shaft folded back through the arm
        errors = [e for e in gen.check_keys("v1_sweep", keys, SPEAR) if "right upper arm" in e]
        self.assertTrue(errors, "the folded-back shaft must fail the clearance check")

    def test_shaft_along_the_forearm_is_allowed(self) -> None:
        # The thrust's butt rides along the driving forearm and the guard's shaft crosses the belly:
        # neither is a fold-back.
        thrust = gen.moves(SPEAR)[0]
        poses = gen.resolve(thrust.keys, SPEAR)
        for pose in (poses[0], poses[[k.phase for k in thrust.keys].index("contact")]):
            gaps = gen.shaft_body_gaps(pose, gen.skeleton(pose, SPEAR))
            self.assertGreaterEqual(min(gaps.values()), SPEAR.rules.shaft_body_clearance_px, gaps)


# The v2 sweep (in-game capture spear-v2, 2026-10-03): at the first-person strike-start tick 4.6 the
# rear hand crossed the chest at jaw height and the shaft lay under the jaw and in front of the
# neck, a "yoke".  It cleared the bare head and torso boxes by 1.3 and 1.0 px.
V2_SWEEP = (
    (0, "guard", "linear", None),
    (2, "wind", "easeinoutsine", ((6, 30, 0), ("L", 31, -5), (50, -70), gen.ON_SHAFT, (-24.2, -101.1, -19.3), (-55, 8))),
    (3, "anticipation", "easeinoutsine", ((4, 8, 0), ("L", 32, -10), (5, -50), gen.ON_SHAFT, (-25.1, -103.2, -39.3), (-95, 6))),
    (4, "coil", "linear", ((18, -40, 15), ("L", 36, -10), (-80, -5), (-165, -10), (73, -11.2, -14.9), (-95, 1))),
    (5, "contact", "easeoutquad", ((18, -15, 10), ("L", 38, -10), (-65, -5), (-150, -5), (73.2, -10.7, -7.9), (-73, 1), (0, -2))),
)


class HeadAndNeckClearanceTest(unittest.TestCase):
    def v2_pose(self, tick):
        keys = tuple(gen.key(t, phase, easing, **SPEAR.guard) if spec is None else gen.key(t, phase, easing, *spec)
                     for t, phase, easing, spec in V2_SWEEP)
        return keys, gen.sample(keys, gen.resolve(keys, SPEAR), tick)

    def test_v2_sweep_strike_start_yoke_is_caught(self) -> None:
        keys, pose = self.v2_pose(4.6)
        sk = gen.skeleton(pose, SPEAR)
        root = gen._root_frame(pose)
        shaft = gen._segment(sk["butt"], sk["tip"], 96)
        bare_head = gen._box_gap(gen._part_frame(root, (0, 0, 0), pose["head"]), shaft, (-4, -8, -4), (4, 0, 4))
        bare_torso = gen._box_gap(gen._part_frame(root, (0, 0, 0), pose["torso"]), shaft, (-4, 0, -2), (4, 12, 2))
        self.assertGreater(bare_head, 1.0)   # why the earlier check passed it
        self.assertGreater(bare_torso, 0.9)
        self.assertEqual(0.0, gen.shaft_body_gaps(pose, sk, left_free=True)["head and neck"])
        errors = [e for e in gen.check_keys("v2_sweep", keys, SPEAR) if "head and neck" in e]
        self.assertTrue(errors, "the shaft under the jaw must fail")

    def test_sweep_wind_up_is_carried_low_and_clear(self) -> None:
        sweep = gen.moves(SPEAR)[1]
        poses = gen.resolve(sweep.keys, SPEAR)
        for step in range(0, 121):  # ticks 0 to 6, the wind-up, coil and release into the swing
            pose = gen.sample(sweep.keys, poses, step / 20)
            sk = gen.skeleton(pose, SPEAR)
            gaps = gen.shaft_body_gaps(pose, sk, left_free=step > 60)
            self.assertGreaterEqual(gaps["head and neck"], 1.4, step / 20)
        pose = gen.sample(sweep.keys, poses, 4.6)
        self.assertLess(gen.skeleton(pose, SPEAR)["grip"][1], 1.1)  # rear hand at chest or waist height

    def test_skin_layers_and_neck_notch(self) -> None:
        self.assertEqual(0.5, gen.HAT_LAYER)
        self.assertEqual(0.25, gen.SKIN_LAYER)
        head = next(b for b in gen.BODY_BOXES if b[0] == "head and neck")
        self.assertEqual((4.5, gen.NECK_NOTCH, 4.5), head[4])  # down past the jaw into the shoulders


class WeaponGroundTest(unittest.TestCase):
    def hold_probe(self, tilt):
        smash = gen.moves(SPEAR)[3]
        hold = next(k for k in smash.keys if k.phase == "hold")
        return dataclasses.replace(hold, tick=0, item=(hold.item[0] + tilt, hold.item[1], hold.item[2]))

    def test_axis_clear_but_pennant_in_the_grass_is_caught(self) -> None:
        probe = self.hold_probe(8)
        pose = gen._resolve_key(probe, None, SPEAR)
        sk = gen.skeleton(pose, SPEAR)
        self.assertGreater(min(sk["tip"][1], sk["butt"][1]), 0.1)  # the axis-only check passed this
        height, part = gen.weapon_lowest_point(pose, SPEAR)
        self.assertLess(height, 0.0)
        self.assertEqual("pennant", part)
        errors = [e for e in gen.check_keys("probe", (probe,), SPEAR) if "above the ground" in e]
        self.assertTrue(errors and "pennant" in errors[0], errors)

    def test_model_corners_cover_the_hanging_pieces(self) -> None:
        names = {name for name, _ in gen.weapon_rig(SPEAR).corners}
        for piece in ("pennant", "plaque", "plaque_tassel", "butt_tassel", "blade_step_2", "star_points_tip"):
            self.assertIn(piece, names)

    def test_smash_lands_just_above_the_ground(self) -> None:
        smash = gen.moves(SPEAR)[3]
        poses = gen.resolve(smash.keys, SPEAR)
        hold = poses[[k.phase for k in smash.keys].index("hold")]
        height, part = gen.weapon_lowest_point(hold, SPEAR)
        self.assertTrue(SPEAR.rules.weapon_ground_clearance <= height < 0.15, (height, part))


class WorldTrailTest(unittest.TestCase):
    """The port of CombatWorldTrails / HitboxGenerators."""

    def test_tip_radius_comes_from_the_weapon(self) -> None:
        # 0.705 + (blade tip 32 - grip -2) px * 0.9 / 16 (CombatWorldTrails.trailSize).
        self.assertAlmostEqual(0.705 + 34 * 0.9 / 16, gen.trail_tip_radius(SPEAR), places=6)
        self.assertAlmostEqual(0.705 + 0.995, gen.trail_tip_radius(gen.BASIC_SWORD), places=2)  # the 1.7 jian

    def test_sample_times_spread_each_tick(self) -> None:
        sweep = gen.moves(SPEAR)[1]
        times = gen.trail_sample_times(gen.hit_samples(sweep))
        self.assertEqual(9, len(times))
        for got, want in zip(times, [4 + 2 / 3, 5, 5 + 1 / 3, 5 + 2 / 3, 6, 6 + 1 / 3, 6 + 2 / 3, 7, 7 + 1 / 3]):
            self.assertAlmostEqual(want, got, places=6)
        thrust = gen.moves(SPEAR)[0]
        self.assertEqual([4.0, 5.0], gen.trail_sample_times(gen.hit_samples(thrust)))  # a lone sample sits on its tick

    def test_trail_head_follows_the_far_end_at_the_tip_radius(self) -> None:
        sweep = gen.moves(SPEAR)[1]
        samples, radius = gen.hit_samples(sweep), gen.trail_tip_radius(SPEAR)
        head = gen.trail_head(samples, 6.0, radius)  # the straight-ahead sample (0, 1.05, 3.8)
        self.assertAlmostEqual(0.0, head[0], places=6)
        self.assertAlmostEqual(radius, math.dist(head, (0.0, gen.TRAIL_PIVOT_HEIGHT, 0.0)), places=6)
        self.assertLess(head[1], gen.TRAIL_PIVOT_HEIGHT)
        first = gen.trail_head(samples, 4.0, radius)  # before the first sample: held there
        self.assertLess(first[0], -2.0)  # server +x (left) is the rig's -x
        last = gen.trail_head(samples, 8.0, radius)
        self.assertGreater(last[0], 2.0)

    def test_sweep_spearhead_stays_with_the_trail(self) -> None:
        sweep = gen.moves(SPEAR)[1]
        tolerance = SPEAR.cut_paths["left_to_right"].trail_tolerance
        self.assertEqual(0.48, tolerance)
        self.assertLessEqual(max(r for _, r in gen.trail_residuals(sweep, SPEAR)), tolerance)
        # The v1 sweep was about 1.5 blocks off the trail.
        v1 = dataclasses.replace(sweep, keys=v1_sweep_keys() + sweep.keys[-3:])
        self.assertGreater(sum(r for _, r in gen.trail_residuals(v1, SPEAR)) / 9, 1.0)

    def test_trail_residuals_follow_the_trail_samples(self) -> None:
        # Without trail samples the world trail draws the hit samples; with them, it draws those, and
        # the residual check measures the posed tip against what is drawn.
        sweep = gen.moves(SPEAR)[1]
        self.assertIsNone(sweep.trail_samples)
        self.assertEqual(gen.hit_samples(sweep), gen.world_trail_samples(sweep))
        mirrored = [dict(s, end=[-s["end"][0], *s["end"][1:]]) for s in json.loads(sweep.samples)]
        moved = dataclasses.replace(sweep, trail_samples=json.dumps(mirrored))
        self.assertEqual(gen.hit_samples(sweep), gen.hit_samples(moved))
        self.assertEqual([(s["tick"], tuple(s["start"]), tuple(s["end"])) for s in mirrored],
                         gen.world_trail_samples(moved))
        self.assertGreater(max(r for _, r in gen.trail_residuals(moved, SPEAR)),
                           max(r for _, r in gen.trail_residuals(sweep, SPEAR)) + 0.5)

    def test_detached_trail_fails_the_check(self) -> None:
        sweep = gen.moves(SPEAR)[1]
        tight = dataclasses.replace(SPEAR, cut_paths=dict(SPEAR.cut_paths, left_to_right=dataclasses.replace(
            SPEAR.cut_paths["left_to_right"], trail_tolerance=0.2)))
        errors = gen.check_move(sweep, tight)
        self.assertTrue(any("from the world trail head" in e for e in errors), errors)

    def test_lunge_tip_reaches_the_streak(self) -> None:
        lunge = gen.moves(SPEAR)[4]
        self.assertLess(max(r for _, r in gen.trail_residuals(lunge, SPEAR)), 0.2)


class ArmBranchTest(unittest.TestCase):
    def test_released_arm_comes_back_on_the_guard_branch(self) -> None:
        # The lunge's left arm is thrown behind and regrips: every segment takes the short way.
        lunge = gen.moves(SPEAR)[4]
        self.assertEqual([], [e for e in gen.check_keys(lunge.animation_id, lunge.keys, SPEAR) if "left_arm" in e])

    def test_the_sword_is_resolved_as_before(self) -> None:
        for m in gen.moves():
            self.assertEqual(gen.resolve(m.keys), gen._settle_arm_branches(m.keys, gen.resolve(m.keys), gen.BASIC_SWORD))


if __name__ == "__main__":
    unittest.main()
