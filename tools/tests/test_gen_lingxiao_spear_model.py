from __future__ import annotations

import contextlib
import copy
import io
import json
import math
import unittest

from tools import gen_lingxiao_spear_model as gen
from tools import gen_qingfeng_sword_model as sword


class LingxiaoSpearModelTest(unittest.TestCase):
    def test_committed_outputs_match_the_generator(self) -> None:
        for path, data in gen.outputs().items():
            current = path.read_bytes()
            if path.suffix == ".png":
                self.assertEqual(sword.decode_png_rgba(data), sword.decode_png_rgba(current), path.name)
            else:
                self.assertEqual(data, current, f"{path.name} drifted; run python3 tools/gen_lingxiao_spear_model.py")

    def test_generator_is_deterministic(self) -> None:
        self.assertEqual(gen.outputs(), gen.outputs())

    def test_check_mode_passes(self) -> None:
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gen.main(["--check"]))

    def test_self_checks_pass(self) -> None:
        self.assertEqual([], gen.self_check())

    def test_textures_have_the_expected_sizes(self) -> None:
        width, height, raw = sword.decode_png_rgba(gen.outputs()[gen.TEXTURE_PATH])
        self.assertEqual((128, 128), (width, height))
        width, height, raw = sword.decode_png_rgba(gen.outputs()[gen.ICON_PATH])
        self.assertEqual((64, 64), (width, height))  # same convention as qingfeng_sword.png
        alphas = raw[3::4]
        self.assertIn(0, alphas)
        self.assertIn(255, alphas)
        # The icon lies on the diagonal: butt in the lower-left, tip in the upper-right corner.
        opaque = [(i % 64, i // 64) for i, a in enumerate(alphas) if a]
        self.assertLess(min(x - y for x, y in opaque), -50)
        self.assertGreater(max(x - y for x, y in opaque), 50)

    def test_wrapper_keeps_the_2d_icon_in_the_gui(self) -> None:
        model = gen.build_model()
        self.assertEqual("neoforge:separate_transforms", model["loader"])
        self.assertEqual("front", model["gui_light"])
        self.assertEqual({"parent": gen.MODEL_3D_ID}, model["base"])
        self.assertEqual({"gui": {"parent": "minecraft:item/handheld",
                                  "textures": {"layer0": "myvillage:item/lingxiao_spear"}}}, model["perspectives"])
        model_3d = gen.build_model_3d()
        self.assertNotIn("parent", model_3d)
        self.assertNotIn("gui", model_3d["display"])
        for context in ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
                        "firstperson_lefthand", "ground", "fixed", "head"):
            self.assertIn(context, model_3d["display"])
        for key, ref in model_3d["textures"].items():
            if key != "particle":
                self.assertTrue(ref.startswith("myvillage:item/lingxiao_spear_"), ref)

    def test_model_stays_within_vanilla_element_limits(self) -> None:
        elements = gen.build_elements()
        self.assertEqual([], gen.check_elements(elements))
        self.assertLess(len(elements), 120)  # the sword's order of magnitude
        for e in elements:
            rot = e.get("rotation")
            if rot:
                self.assertIn(rot["angle"], (-45, -22.5, 22.5, 45))
                self.assertEqual("x", rot["axis"])
        ys = [p[1] for e in elements for p in sword.element_corners(e)]
        self.assertAlmostEqual(-16.0, min(ys), places=6)
        self.assertAlmostEqual(32.0, max(ys), places=6)

    def test_invalid_element_rotation_and_bounds_are_rejected(self) -> None:
        elements = copy.deepcopy(gen.build_elements())
        elements[0]["rotation"] = {"angle": 30, "axis": "x", "origin": [8, 8, 8]}
        elements[1]["to"][1] = 33.0
        elements[2]["rotation"] = {"angle": 45, "axis": "y", "origin": [8, 8, 8]}
        errors = gen.check_elements(elements)
        self.assertTrue(any("rotation 30" in e for e in errors))
        self.assertTrue(any("outside [-16, 32]" in e for e in errors))
        self.assertTrue(any("rotation axis y" in e for e in errors))

    def test_glowing_parts_use_neoforge_light_data(self) -> None:
        elements = {e["name"]: e for e in gen.build_elements()}
        for name in ("blade_step_0", "blade_body_2", "star_gem", "star_ring", "butt_gem_x", "butt_collar_1_inlay_x",
                     "plaque_bead"):
            self.assertEqual({"block_light": 15, "sky_light": 15}, elements[name]["neoforge_data"], name)
        for name in ("shaft", "pennant", "butt_cap", "butt_collar_1", "blade_navy_0", "blade_spine_0"):
            self.assertNotIn("neoforge_data", elements[name], name)

    def test_spearhead_is_barbed_and_inside_the_contract(self) -> None:
        self.assertEqual([], gen.blade_outline_errors(gen.build_elements()))
        widths = [w for _, _, w in gen.BLADE_STEPS]
        self.assertGreaterEqual(sum(1 for a, b in zip(widths, widths[1:]) if b > a), 2)
        self.assertLessEqual(widths[-1], 0.2)  # needle tip
        wide = copy.deepcopy(gen.build_elements())
        next(e for e in wide if e["name"] == "blade_step_2")["to"][2] += 0.3
        self.assertTrue(any("blade half width" in e for e in gen.blade_outline_errors(wide)))

    def test_contract_and_display_are_frozen(self) -> None:
        # Pose and first-person workers build on these values; a change needs the lead's sign-off.
        self.assertEqual({"y": [16.0, 23.4], "half_width": 2.35, "half_thickness": 0.68},
                         gen.geometry_contract()["collar"])
        self.assertEqual({"y": [-16.0, -13.4], "half_width": 1.0, "half_thickness": 1.0},
                         gen.geometry_contract()["butt"])
        self.assertEqual({"half_width": 1.9, "half_thickness": 0.35, "ridge_half_thickness": 0.35,
                          "taper_start_y": 25.6}, gen.geometry_contract()["head"])
        third = gen._json_display(gen.build_display())["thirdperson_righthand"]
        self.assertEqual({"rotation": [-10.81, 180.0, 0.0], "translation": [0.0, 6.84, 0.312],
                          "scale": [0.9, 0.9, 0.9]}, third)

    def test_left_hand_entries_draw_the_mirror_image_of_the_right_hand(self) -> None:
        # ItemTransform.apply(leftHand=true) negates rotation y/z and translation x.  The left hand
        # is a true mirror when that applied transform equals M * (right-hand transform) * M with
        # M = diag(-1, 1, 1), the reflection across the player's sagittal plane.
        m = [[-1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
        display = gen.build_display()
        for ctx in ("firstperson", "thirdperson"):
            right_r, right_t = gen.game_apply(display[f"{ctx}_righthand"], left_hand=False)
            left_r, left_t = gen.game_apply(display[f"{ctx}_lefthand"], left_hand=True)
            mirrored = sword.mm(sword.mm(m, right_r), m)
            for i in range(3):
                self.assertAlmostEqual(-right_t[0] if i == 0 else right_t[i], left_t[i], places=6, msg=ctx)
                for j in range(3):
                    self.assertAlmostEqual(mirrored[i][j], left_r[i][j], places=6, msg=ctx)
        # Pre-negating y/z (the sword's mirror()) breaks the property for the spear's tilted hold.
        broken_r, _ = gen.game_apply(sword.mirror(display["firstperson_righthand"]), left_hand=True)
        right_r, _ = gen.game_apply(display["firstperson_righthand"], left_hand=False)
        mirrored = sword.mm(sword.mm(m, right_r), m)
        self.assertGreater(max(abs(mirrored[i][j] - broken_r[i][j]) for i in range(3) for j in range(3)), 0.1)

    def test_icon_is_32_logical_pixels_in_2x2_blocks(self) -> None:
        width, height, raw = sword.decode_png_rgba(gen.outputs()[gen.ICON_PATH])
        pixel = lambda x, y: raw[(y * width + x) * 4:(y * width + x) * 4 + 4]
        for y in range(0, height, 2):
            for x in range(0, width, 2):
                block = {pixel(x + dx, y + dy) for dx in (0, 1) for dy in (0, 1)}
                self.assertEqual(1, len(block), (x, y))
        self.assertEqual({0, 255}, set(raw[3::4]))
        grid = gen.icon_grid()
        blade = [xy for xy, c in grid.items() if c in (gen.CYAN["base"], gen.CYAN["pale"], gen.CYAN["white"],
                                                        gen._mix(gen.CYAN["base"], gen.CYAN["mid"], 0.3))]
        self.assertGreater(len(blade), 40)  # an oversized head, not a speck

    def test_hands_have_bare_shaft(self) -> None:
        # Rule: a 4.4 px fist box on the axis, for every leading-hand position y 0..13.5 and around
        # the rear hand at y -2 (+-2.4), contains nothing but the shaft.
        self.assertEqual(((-2.0, -2.0), 2.4), gen.HAND_ZONES[0][1:])
        self.assertEqual(((0.0, 13.5), 2.2), gen.HAND_ZONES[1][1:])
        self.assertEqual(2.2, gen.FIST_HALF)
        self.assertEqual([], gen.bare_shaft_errors(gen.build_elements()))
        names = {e["name"] for e in gen.build_elements()}
        self.assertNotIn("mid_collar", names)  # the mid-shaft collar is a flush painted band now
        for y in (0.0, 2.6, 11.0, 13.5):
            crowded = gen.build_elements() + [gen.collar("extra", y - 0.5, y + 0.5)[0]]
            self.assertTrue(any("leading hand" in e for e in gen.bare_shaft_errors(crowded)), y)
        crowded = gen.build_elements() + [gen.collar("extra", -2.5, -1.5)[0]]
        self.assertTrue(any("rear hand" in e for e in gen.bare_shaft_errors(crowded)))

    def test_hanging_pieces_stay_out_of_the_hand_zone(self) -> None:
        saved = gen.PENNANT_ANCHOR
        try:
            gen.PENNANT_ANCHOR = (7.9, 16.4, 9.4)  # the old hang point beside the leading hand
            self.assertTrue(any(e.startswith("pennant ") for e in gen.bare_shaft_errors(gen.build_elements())))
        finally:
            gen.PENNANT_ANCHOR = saved
        # An exact oriented-box test: a 45-degree piece whose corner merely nears the zone is clear.
        zone = gen.hand_zone_boxes()[1]
        probe = gen.box("probe", (8.0, 18.0, 10.4), (8.1, 19.0, 11.0), {},
                        {"angle": -45, "axis": "x", "origin": (8.0, 18.0, 10.4)})
        self.assertFalse(gen.box_overlaps(probe, zone[1], zone[2]))
        probe = gen.box("probe", (8.0, 14.0, 9.5), (8.1, 15.0, 10.0), {},
                        {"angle": -45, "axis": "x", "origin": (8.0, 14.0, 9.5)})  # swings to z 8.8, y 14.7
        self.assertTrue(gen.box_overlaps(probe, zone[1], zone[2]))

    def test_hanging_pieces_hang_down_in_third_person(self) -> None:
        # Item frame: +Z points to the shoulder (up), so a piece hanging toward -Y/+Z in the model
        # must point down (item -Z) and back (item -Y).
        hang = gen.fist_fit()["hang_dir_item"]
        self.assertLess(hang[2], -0.4)
        self.assertLess(hang[1], 0.0)
        for e in gen.build_elements():
            if e["name"].startswith(("pennant", "plaque", "butt_tassel", "butt_bead", "butt_chain")):
                centre = [sum(p[i] for p in sword.element_corners(e)) / 8 for i in range(3)]
                self.assertGreater(centre[2], gen.CZ, e["name"])

    def test_third_person_grip_sits_in_the_fist_along_the_sword_axis(self) -> None:
        fit = gen.fist_fit()
        self.assertLess(fit["grip_error"], 0.01)
        self.assertLess(fit["axis_angle_error"], 0.2)
        self.assertAlmostEqual(1.0, abs(fit["flat_normal"][0]), places=3)
        self.assertEqual([], fit["fist_intruders"])
        self.assertGreaterEqual(gen.THIRD_PERSON_SCALE, 0.85)
        self.assertLessEqual(gen.THIRD_PERSON_SCALE, 0.9)
        self.assertGreater(fit["in_hand_length_blocks"], 2.3)

    def test_third_person_axis_mapping_matches_qingfeng(self) -> None:
        spear = gen.build_display()["thirdperson_righthand"]
        jian = sword.build_display()["thirdperson_righthand"]
        for v in ((0.0, 1.0, 0.0), (1.0, 0.0, 0.0), (0.0, 0.0, 1.0)):
            self.assertLess(math.dist(sword.display_vector(spear, v), sword.display_vector(jian, v)), 1e-3)
        self.assertEqual(spear["rotation"], sword.mirror(sword.mirror(spear))["rotation"])

    def test_item_frame_keeps_the_spear_inside_the_frame(self) -> None:
        self.assertLessEqual(gen.fixed_extent(), 12.0)
        ground = gen.build_display()["ground"]
        length = (gen.TIP_Y - gen.BUTT_Y) * ground["scale"][0] / 16.0
        self.assertLess(length, 1.2)

    def test_geometry_contract_is_ordered_and_matches_the_model(self) -> None:
        geometry = json.loads(gen.GEOMETRY_PATH.read_text(encoding="utf-8"))
        self.assertEqual("model_pixels", geometry["units"])
        self.assertEqual({"length": "+y", "flat_normal": "x", "edge": "z", "center_x": 8.0, "center_z": 8.0},
                         geometry["axes"])
        self.assertEqual([8.0, -2.0, 8.0], geometry["grip_center"])
        self.assertEqual([8.0, 11.0, 8.0], geometry["off_hand_grip_center"])
        # WeaponGeometry.parse: butt <= handle <= collar <= head base < tip, grips on the handle.
        self.assertLessEqual(geometry["butt"]["y"][1], geometry["handle"]["y"][0])
        for key in ("grip_center", "off_hand_grip_center"):
            self.assertLess(geometry["handle"]["y"][0], geometry[key][1])
            self.assertLess(geometry[key][1], geometry["handle"]["y"][1])
        self.assertLessEqual(geometry["handle"]["y"][1], geometry["collar"]["y"][0])
        self.assertLessEqual(geometry["collar"]["y"][1], geometry["head_base"][1])
        self.assertLess(geometry["head_base"][1], geometry["head_tip"][1])
        for part in ("handle", "collar", "butt"):
            self.assertGreater(geometry[part]["half_width"], 0)
            self.assertGreater(geometry[part]["half_thickness"], 0)
        self.assertEqual(geometry["grip_center"][0], geometry["head_base"][0])
        self.assertEqual(geometry["grip_center"][2], geometry["head_base"][2])
        self.assertEqual([-16.0, 32.0], geometry["overall_y"])
        tops = [max(p[1] for p in sword.element_corners(e)) for e in gen.build_elements()]
        self.assertAlmostEqual(geometry["head_tip"][1], max(tops), places=3)
        for field in ("grip_center", "handle", "collar", "butt", "head_base", "head_tip",
                      "edge_axis", "flat_axis", "axes"):  # validate_sword_combat_foundation GEOMETRY_FIELDS
            self.assertIn(field, geometry)


    def test_trail_span_is_the_front_shaft_and_head(self) -> None:
        geometry = json.loads(gen.GEOMETRY_PATH.read_text(encoding="utf-8"))
        self.assertEqual({"base": [8.0, 16.0, 8.0], "tip": [8.0, 32.0, 8.0]}, geometry["trail"])
        base, tip = geometry["trail"]["base"][1], geometry["trail"]["tip"][1]
        # About as long as the sword's blade, which draws the sword's trails.
        blade = sword.geometry_contract()
        self.assertAlmostEqual(blade["head_tip"][1] - blade["head_base"][1], tip - base, delta=0.5)
        # Starts above every hand position and ends at the tip: WeaponGeometry and the validator
        # need it on the axis, ordered, and on the weapon.
        self.assertGreaterEqual(base, max(y1 + half for _, (_, y1), half in gen.HAND_ZONES))
        self.assertEqual(geometry["head_tip"][1], tip)
        self.assertLess(base, geometry["head_base"][1])

    def test_bad_trail_span_fails_the_self_check(self) -> None:
        original = gen.TRAIL_Y
        try:
            for span, needle in (((32.0, 16.0), "base to tip"), ((16.0, 33.0), "base to tip"),
                                 ((10.0, 32.0), "inside the hand zones")):
                gen.TRAIL_Y = span
                self.assertTrue(any(needle in e for e in gen.self_check()), (span, gen.self_check()))
        finally:
            gen.TRAIL_Y = original
        self.assertEqual([], gen.self_check())

    def test_trail_block_leaves_every_other_contract_value_unchanged(self) -> None:
        # The values the other workers built on, as committed before the trail block existed.
        contract = gen.geometry_contract()
        contract.pop("trail")
        self.assertEqual({
            "format": 2, "units": "model_pixels", "generator": "tools/gen_lingxiao_spear_model.py",
            "model": "myvillage:item/lingxiao_spear_3d",
            "axes": {"length": "+y", "flat_normal": "x", "edge": "z", "center_x": 8.0, "center_z": 8.0},
            "grip_center": [8.0, -2.0, 8.0], "off_hand_grip_center": [8.0, 11.0, 8.0],
            "handle": {"y": [-13.4, 16.0], "half_width": 0.5, "half_thickness": 0.5},
            "collar": {"y": [16.0, 23.4], "half_width": 2.35, "half_thickness": 0.68},
            "butt": {"y": [-16.0, -13.4], "half_width": 1.0, "half_thickness": 1.0},
            "head": {"half_width": 1.9, "half_thickness": 0.35, "ridge_half_thickness": 0.35,
                      "taper_start_y": 25.6},
            "head_base": [8.0, 23.4, 8.0], "head_tip": [8.0, 32.0, 8.0],
            "edge_axis": [0.0, 0.0, 1.0], "flat_axis": [1.0, 0.0, 0.0], "overall_y": [-16.0, 32.0],
        }, contract)


if __name__ == "__main__":
    unittest.main()
