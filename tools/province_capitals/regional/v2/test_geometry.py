"""只覆盖 v2 新行为；不启动游戏，也不放宽旧资源边界。"""
from __future__ import annotations

import unittest

from .buildings import Building
from .common import AIR, FACING, LIBRARY, ROOT, point, read_json
from buildgen.style import Style


class GeometryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.library = read_json(LIBRARY)
        cls.style = Style(read_json(ROOT / "tools/buildgen/styles/province_capital_donghai.json"))

    def source(self, family, variant, size, stories=2, facing="south"):
        spec = self.library["families"][family]
        return dict(id="sample", name=spec["name"], family=family, role=spec["role"], variant=variant,
                    size=list(size), stories=stories, facing=facing, position=[60, 12, 60])

    def test_all_sixty_variants_at_minimum_and_sample_sizes(self):
        for family, spec in self.library["families"].items():
            for variant in range(4):
                for size in (spec["minimum"], spec["sample"]):
                    for stories in (1, 2):
                        with self.subTest(family=family, variant=variant, size=size, stories=stories):
                            b = Building(self.source(family, variant, size, stories), self.style, library=self.library).build()
                            self.assertTrue(any(c[2] == "roof" for c in b.cells.values()))
                            self.assertTrue(b.doors)
                            for p in b.places:
                                local = tuple(b.local(p["pos"]))
                                self.assertTrue(b.free(local), p["id"])
                            if spec["role"] in ("house", "inn", "apothecary", "smith"):
                                feet = [p for p, c in b.cells.items() if "_bed[" in c[0] and "part=foot" in c[0]]
                                heads = [p for p, c in b.cells.items() if "_bed[" in c[0] and "part=head" in c[0]]
                                self.assertEqual(len(feet), len(heads)); self.assertTrue(feet)
                            for flight in b.stairs:
                                for x, y, z in flight["steps"]:
                                    for head in (1, 2):
                                        self.assertEqual(b.cells[x, y + head, z][0], AIR)

    def test_variants_change_structure_with_one_material_palette(self):
        for family, spec in self.library["families"].items():
            with self.subTest(family=family):
                signatures = {Building(self.source(family, v, spec["sample"]), self.style, library=self.library).build().summary()["geometry_sha256"] for v in range(4)}
                self.assertEqual(len(signatures), 4)

    def test_even_and_odd_actual_footprints_under_all_rotations(self):
        for size in ([8, 12], [9, 13], [10, 13], [9, 14]):
            for facing, q in FACING.items():
                with self.subTest(size=size, facing=facing):
                    source = self.source("lane_house", 0, size, 1, facing)
                    b = Building(source, self.style, library=self.library).build()
                    nonair = [point(source, *p) for p, c in b.cells.items() if c[0] != AIR]
                    extent = [max(p[i] for p in nonair) - min(p[i] for p in nonair) + 1 for i in (0, 2)]
                    self.assertEqual(extent, list(reversed(size)) if q % 2 else size)
                    self.assertEqual(b.summary()["source_size"], size)
                    self.assertEqual(b.source["position"][1], 12)

    def test_secondary_services_cannot_claim_primary_job_keys(self):
        required = {"inn_work", "inn_rest", "inn_social", "guest_west", "herbalist_work", "herbalist_rest", "smith_work", "smith_rest"}
        for family in ("courtyard_inn", "herb_hall", "forge_yard"):
            source = self.source(family, 0, self.library["families"][family]["sample"])
            source["primary_service"] = False
            b = Building(source, self.style, library=self.library).build()
            self.assertFalse(required & {p["id"] for p in b.places})

    def test_two_thick_walls_have_through_doors_and_safe_fixtures(self):
        source = self.source("l_court", 1, [23, 23])
        b = Building(source, self.style, {"wall_thickness": 2}, self.library).build()
        x, z = b.south_door
        self.assertEqual(b.wall_thickness, 2)
        self.assertIn("_door[", b.cells[x, 0, z][0])
        self.assertEqual(b.cells[x, 0, z - 1][0], AIR)
        self.assertEqual(b.cells[x, 1, z - 1][0], AIR)
        self.assertIn((x, z - 2), b.interiors[0])
        self.assertTrue(all(b.free(tuple(b.local(p["pos"]))) for p in b.places))
        bad = self.source("lane_house", 0, [7, 9])
        with self.assertRaises(ValueError):
            Building(bad, self.style, {"wall_thickness": 2}, self.library).build()

    def test_roof_form_does_not_need_regional_import(self):
        import subprocess
        import sys
        code = "from buildgen.ops import roof_handler; assert callable(roof_handler('province_oasis_terrace')); assert callable(roof_handler('chinese_half_hip'))"
        subprocess.run([sys.executable, "-c", code], cwd=ROOT / "tools", check=True)

    def test_quay_court_preserves_water_rooms_and_existing_stairs(self):
        from .city import Capital
        from .common import Construction
        m = Capital.__new__(Capital)
        m.width = m.depth = 32; m.height = 64
        m.geometry = self.library["construction"]
        m.style = self.style
        m.terrain = [2] * 1024
        m.construction = Construction([32, 64, 32])
        m.construction.box((0, 3, 0), (31, 5, 31), "minecraft:water[level=0]", "river", "water")
        stair = "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
        m.construction.cell((16, 11, 16), stair, "authored_steps", "road")
        m.construction.cell((14, 14, 14), "minecraft:stone", "room", "building")
        m.road_columns = {(16, 16): (12, "stairs", True, "north", "steps", 0, 0)}
        m.occupied = {(14, 14): "room"}
        m.diagnostics = {"courtyards": []}
        m.city = {"courtyards": [{"id": "quay", "center": [16, 14, 16], "size": [14, 10], "road_id": "steps", "kind": "quay", "wind_wall": True}]}
        m.courtyards()
        self.assertEqual(m.construction.at((16, 11, 16)), stair)
        self.assertEqual(m.construction.at((14, 14, 14)), "minecraft:stone")
        self.assertEqual(m.construction.at((15, 4, 15)), "minecraft:water[level=0]")
        self.assertEqual(m.ground(15, 15), 2)
        self.assertEqual(m.construction.at((15, 13, 15)), self.style.primary("GROUND_PATH"))
        self.assertEqual(m.construction.at((9, 14, 11)), self.style.primary("BASE_STONE"))
        info = m.diagnostics["courtyards"][0]
        self.assertEqual(info["preserved_road_columns"], 1)
        self.assertLess(info["pier_columns"], info["deck_columns"] // 2)


if __name__ == "__main__":
    unittest.main()
