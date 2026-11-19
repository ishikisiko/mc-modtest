from __future__ import annotations

import json
import math
import unittest

from tools import gen_xuantie_gauntlet_model as gen
from tools.equipment import gauntlet_heat, gauntlet_mesh, geometry, mesh


class GauntletModelTest(unittest.TestCase):
    def test_export_matches_authored_models(self) -> None:
        outputs = gen.outputs()
        for item in gen.ITEMS:
            path = gen.ASSETS / f"models/item/{item}_3d.obj"
            faces = mesh.read_obj(outputs[path], outputs[path.with_suffix(".mtl")])
            data, texture = gen.source_mesh(item)
            expected = [gauntlet_mesh.model_point(part["vertices"][i])
                        for part in data for i in part["indices"]]
            body = [face for face in faces if not face["texture"].endswith("_heat")]
            observed = [point for face in body for point in face["points"][:3]]
            self.assertEqual(len(expected), len(observed), item)
            self.assertTrue(all(math.dist(a, b) < 1e-6 for a, b in zip(expected, observed)), item)
            self.assertEqual(outputs[gen.ASSETS / f"textures/item/{item}_model.png"], texture)
        for path, data in outputs.items():
            with self.subTest(path=path.name):
                self.assertEqual(path.read_bytes(), data)

    def test_all_three_use_the_same_worn_pivot_and_visible_palms(self) -> None:
        self.assertEqual([], gen.self_check())
        base = {key: value for key, value in gen.geometry_contract().items() if key != "model"}
        for item in gen.ITEMS:
            doc = gen.build_model_3d(item)
            self.assertEqual(doc["display"]["thirdperson_righthand"], doc["display"]["thirdperson_lefthand"])
            self.assertEqual(doc["display"]["firstperson_righthand"], doc["display"]["firstperson_lefthand"])
            self.assertEqual(base, {key: value for key, value in gen.geometry_contract(item).items() if key != "model"})
            vertices = [p for part in gen.source_mesh(item)[0] for p in part["vertices"]]
            self.assertFalse(any(-2 < x < 2 and -4 < y < 3.9 and z < -3.95 for x, y, z in vertices), item)

    def test_punch_glow_and_sparks_form_a_four_second_loop(self) -> None:
        assets = gen.outputs()
        stem = gen.ASSETS / f"textures/item/{gen.JINWU}_heat.png"
        w, h, data = geometry.decode_png_rgba(assets[stem])
        animation = json.loads(assets[stem.with_suffix(".png.mcmeta")])["animation"]
        frames = animation["frames"]
        self.assertEqual(animation["frametime"] * len(frames), 80)
        self.assertEqual(w, animation["width"])
        self.assertEqual(animation["height"], gauntlet_heat.HEIGHT)
        self.assertEqual(h % gauntlet_heat.HEIGHT, 0)
        self.assertEqual(h // gauntlet_heat.HEIGHT, max(frames) + 1)
        stride = w * gauntlet_heat.HEIGHT * 4

        def lit(frame: int, section: gauntlet_heat.Patch) -> list[int]:
            return [i for i in range(section.columns) for j in range(section.row, section.row + section.rows)
                    if data[frames[frame] * stride + (j * w + i) * 4 + 3]]

        # The lit pixels move toward the outside after the centre peaks.
        centre = lit(7, gauntlet_heat.FRONT)
        later = lit(14, gauntlet_heat.FRONT)
        self.assertTrue(centre and later)
        self.assertGreater(max(abs(i - w / 2) for i in later), max(abs(i - w / 2) for i in centre))
        self.assertTrue(any(lit(i, gauntlet_heat.EMBERS) for i in range(10, 20)))
        self.assertFalse(lit(25, gauntlet_heat.FRONT))
        self.assertFalse(lit(25, gauntlet_heat.EMBERS))
        self.assertTrue(lit(8, gauntlet_heat.CORE))

    def test_every_variant_uses_its_ingot_and_existing_fist_style(self) -> None:
        data = gen.ROOT / "src/main/resources/data/yuanxian"
        for item in gen.ITEMS:
            weapon = json.loads((data / f"combat/weapon/{item}.json").read_text())
            self.assertTrue(weapon["paired"])
            self.assertEqual(weapon["family"], "fist")
            self.assertEqual(weapon["style"], "yuanxian:basic_fist")
            self.assertEqual(weapon["first_person_rig"], "yuanxian:combat/xuantie_gauntlet_first_person.json")
            recipe = json.loads((data / f"recipe/{item}.json").read_text())
            self.assertEqual(recipe["pattern"], ["II", "II"])
            self.assertEqual(recipe["key"]["I"]["item"], "yuanxian:" + item.replace("_gauntlet", "_ingot"))
            self.assertEqual(recipe["result"], {"id": "yuanxian:" + item, "count": 1})
