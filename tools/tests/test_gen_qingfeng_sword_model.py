from __future__ import annotations

import copy
import json
import math
import unittest

from tools import gen_qingfeng_sword_model as gen


class QingfengSwordModelTest(unittest.TestCase):
    def test_committed_outputs_match_the_generator(self) -> None:
        for path, data in gen.outputs().items():
            current = path.read_bytes()
            if path.suffix == ".png":
                self.assertEqual(gen.decode_png_rgba(data), gen.decode_png_rgba(current), path.name)
            else:
                self.assertEqual(data, current, f"{path.name} drifted; run python3 tools/gen_qingfeng_sword_model.py")

    def test_generator_is_deterministic(self) -> None:
        self.assertEqual(gen.outputs(), gen.outputs())

    def test_self_checks_pass(self) -> None:
        self.assertEqual([], gen.self_check())

    def test_png_round_trip(self) -> None:
        width, height, raw = gen.decode_png_rgba(gen.encode_png(gen.render_texture()))
        self.assertEqual((64, 64), (width, height))
        self.assertEqual(64 * 64 * 4, len(raw))

    def test_wrapper_keeps_the_2d_icon_in_the_gui(self) -> None:
        model = gen.build_model()
        self.assertEqual("neoforge:separate_transforms", model["loader"])
        self.assertEqual({"parent": gen.MODEL_3D_ID}, model["base"])
        self.assertEqual({"gui"}, set(model["perspectives"]))
        self.assertEqual("myvillage:item/qingfeng_sword", model["perspectives"]["gui"]["textures"]["layer0"])
        model_3d = gen.build_model_3d()
        self.assertNotIn("parent", model_3d)  # a builtin/generated parent would drop the elements
        self.assertNotIn("gui", model_3d["display"])

    def test_blade_runs_along_y_with_straight_edges(self) -> None:
        self.assertEqual([], gen.silhouette_errors(gen.build_elements()))
        names = {e["name"] for e in gen.build_elements()}
        for part in ("blade", "ridge", "tip_point", "tip_edge_left", "tip_edge_right", "guard",
                     "grip_wrap", "pommel", "upper_ferrule", "lower_ferrule"):
            self.assertIn(part, names)

    def test_poking_or_holed_tip_is_rejected(self) -> None:
        elements = gen.build_elements()
        poke = copy.deepcopy(elements)
        next(e for e in poke if e["name"] == "tip_core")["to"][2] += 0.3
        self.assertTrue(any("pokes" in e for e in gen.silhouette_errors(poke)))
        hole = [e for e in elements if e["name"] != "tip_core"]
        self.assertTrue(any("hole" in e for e in gen.silhouette_errors(hole)))

    def test_invalid_element_rotation_and_bounds_are_rejected(self) -> None:
        elements = gen.build_elements()
        elements[0]["rotation"] = {"angle": 30, "axis": "x", "origin": [8, 8, 8]}
        elements[1]["to"][1] = 33.0
        errors = gen.check_elements(elements)
        self.assertTrue(any("rotation 30" in e for e in errors))
        self.assertTrue(any("outside [-16, 32]" in e for e in errors))

    def test_third_person_grip_sits_in_the_fist_along_the_old_blade(self) -> None:
        fit = gen.fist_fit()
        self.assertLess(fit["grip_error"], 0.01)
        self.assertLess(fit["blade_angle_error"], 0.2)
        self.assertGreater(fit["guard_front_gap"], 0.0)   # guard just in front of the fist
        self.assertGreater(fit["pommel_behind_fist"], 0.2)  # pommel shows behind it

    def test_display_rotation_follows_item_transform_order(self) -> None:
        # rotationXYZ is Rx * Ry * Rz; the vanilla handheld blade direction must be reproduced.
        display = gen.build_display()["thirdperson_righthand"]
        old = gen.VANILLA["thirdperson_righthand"]
        new_dir = gen.display_vector(display, (0.0, 1.0, 0.0))
        old_dir = gen.display_vector(old, gen.OLD_BLADE_DIR)
        self.assertLess(math.dist(new_dir, old_dir), 1e-3)

    def test_geometry_contract_is_ordered_and_matches_the_model(self) -> None:
        geometry = json.loads(gen.GEOMETRY_PATH.read_text(encoding="utf-8"))
        self.assertEqual("model_pixels", geometry["units"])
        self.assertEqual({"length": "+y", "flat_normal": "x", "edge": "z", "center_x": 8.0, "center_z": 8.0},
                         geometry["axes"])
        self.assertLessEqual(geometry["butt"]["y"][1], geometry["handle"]["y"][0])
        self.assertLess(geometry["handle"]["y"][0], geometry["grip_center"][1])
        self.assertLess(geometry["grip_center"][1], geometry["handle"]["y"][1])
        self.assertLessEqual(geometry["handle"]["y"][1], geometry["collar"]["y"][0])
        self.assertEqual(geometry["collar"]["y"][1], geometry["head_base"][1])
        tops = [max(p[1] for p in gen.element_corners(e)) for e in gen.build_elements()]
        self.assertAlmostEqual(geometry["head_tip"][1], max(tops), places=3)
        bottoms = [min(p[1] for p in gen.element_corners(e)) for e in gen.build_elements()]
        self.assertAlmostEqual(geometry["overall_y"][0], min(bottoms), places=3)


if __name__ == "__main__":
    unittest.main()
