from __future__ import annotations

import contextlib
import copy
import io
import math
import unittest

from tools import gen_qingfeng_sword_model as sword
from tools import gen_xuantie_gauntlet_model as gen


class XuantieGauntletModelTest(unittest.TestCase):
    def test_committed_outputs_match_the_generator(self) -> None:
        for path, data in gen.outputs().items():
            current = path.read_bytes()
            if path.suffix == ".png":
                self.assertEqual(sword.decode_png_rgba(data), sword.decode_png_rgba(current), path.name)
            else:
                self.assertEqual(data, current, f"{path.name} drifted; run python3 tools/gen_xuantie_gauntlet_model.py")

    def test_generator_is_deterministic(self) -> None:
        self.assertEqual(gen.outputs(), gen.outputs())

    def test_check_mode_passes(self) -> None:
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gen.main(["--check"]))

    def test_self_checks_pass(self) -> None:
        self.assertEqual([], gen.self_check())

    def test_textures_have_the_expected_sizes_and_alpha(self) -> None:
        width, height, raw = sword.decode_png_rgba(gen.outputs()[gen.TEXTURE_PATH])
        self.assertEqual((128, 128), (width, height))
        width, height, raw = sword.decode_png_rgba(gen.outputs()[gen.ICON_PATH])
        self.assertEqual((64, 64), (width, height))
        alphas = set(raw[3::4])
        self.assertEqual({0, 255}, alphas, "the icon has binary alpha")
        # Each logical pixel is a 2x2 block.
        for y in range(0, 64, 2):
            for x in range(0, 64, 2):
                block = {raw[((y + dy) * 64 + x + dx) * 4:((y + dy) * 64 + x + dx) * 4 + 4]
                         for dy in (0, 1) for dx in (0, 1)}
                self.assertEqual(1, len(block), (x, y))

    def test_model_texture_is_painted_not_flat(self) -> None:
        texture = gen.render_texture()
        for name, (x0, y0, x1, y1) in gen.REGIONS.items():
            if (x1 - x0) * (y1 - y0) < 16:
                continue
            colours = {texture[y][x] for y in range(y0, y1) for x in range(x0, x1)}
            self.assertGreater(len(colours), 6, f"{name} is a flat fill")

    def test_wrapper_keeps_the_2d_icon_in_the_gui(self) -> None:
        model = gen.build_model()
        self.assertEqual("neoforge:separate_transforms", model["loader"])
        self.assertEqual("front", model["gui_light"])
        self.assertEqual({"parent": gen.MODEL_3D_ID}, model["base"])
        self.assertEqual({"gui": {"parent": "minecraft:item/handheld",
                                  "textures": {"layer0": "myvillage:item/xuantie_gauntlet"}}}, model["perspectives"])
        model_3d = gen.build_model_3d()
        self.assertNotIn("parent", model_3d)
        self.assertNotIn("gui", model_3d["display"])
        for context in ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
                        "firstperson_lefthand", "ground", "fixed", "head"):
            self.assertIn(context, model_3d["display"])
        for key, ref in model_3d["textures"].items():
            if key != "particle":
                self.assertTrue(ref.startswith("myvillage:item/xuantie_gauntlet_"), ref)

    def test_model_stays_within_vanilla_element_limits(self) -> None:
        elements = gen.build_elements()
        self.assertEqual([], gen.check_elements(elements))
        self.assertLess(len(elements), 80)
        bad = copy.deepcopy(elements)
        bad[0]["to"][1] = 33.0
        bad[1]["rotation"] = {"angle": 22.5, "axis": "x", "origin": [8, 8, 8]}
        errors = gen.check_elements(bad)
        self.assertTrue(any("outside [-16, 32]" in e for e in errors), errors)
        self.assertTrue(any("element rotation" in e for e in errors), errors)

    def test_the_gauntlet_wraps_the_fist_without_cutting_into_it(self) -> None:
        elements = gen.build_elements()
        self.assertEqual([], gen.fist_clearance_errors(elements))
        self.assertEqual([], gen.fist_coverage_errors(elements))
        inside = copy.deepcopy(elements)
        inside.append(gen.box("probe", (6.0, 6.0, 6.0), (9.0, 9.0, 9.0), {"west": gen.uv("glove")}))
        self.assertEqual(["probe.west lies inside the arm"], gen.fist_clearance_errors(inside))
        bare = [e for e in elements if e["name"] not in ("glove", "palm") and not e["name"].endswith("_curl")]
        self.assertTrue(gen.fist_coverage_errors(bare))

    def test_third_person_puts_the_gauntlet_on_the_fist(self) -> None:
        fit = gen.fist_fit()
        self.assertLess(fit["grip_error"], 1e-6)
        self.assertLess(math.dist(fit["punch_axis_item"], (0.0, 0.0, -1.0)), 1e-6)
        self.assertLess(math.dist(fit["palm_item"], (-1.0, 0.0, 0.0)), 1e-6)
        self.assertEqual(gen.build_display()["thirdperson_righthand"], gen.build_display()["thirdperson_lefthand"])

    def test_contract_reads_the_gauntlet_in_format_2_names(self) -> None:
        geo = gen.geometry_contract()
        self.assertEqual(2, geo["format"])
        self.assertEqual("tools/gen_xuantie_gauntlet_model.py", geo["generator"])
        self.assertEqual([8.0, 8.0, 8.0], geo["grip_center"])
        self.assertNotIn("off_hand_grip_center", geo)
        self.assertLess(geo["butt"]["y"][1], geo["head_base"][1])
        self.assertEqual(geo["collar"]["y"][0], geo["trail"]["base"][1], "the trail starts at the knuckle line")
        self.assertEqual(geo["head_tip"], geo["trail"]["tip"])
        self.assertLess(geo["overall_y"][1] - geo["overall_y"][0], 20.0, "a short weapon")


if __name__ == "__main__":
    unittest.main()
