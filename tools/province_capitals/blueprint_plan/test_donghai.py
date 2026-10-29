"""东海 authored 蓝图的覆盖、归属和海岸空间约束；不声称游戏验收。"""
import unittest

from .generate import ROOT, generate, read, rect


class DonghaiPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.planner, cls.data = generate(
            ROOT / "blueprints/province_capitals/blueprint_plan/donghai.json"
        )

    def test_all_active_buildings_and_original_gardens_are_retained(self):
        p, d = self.planner, self.data
        city = read(ROOT / p.spec["sources"]["city"])
        environment = read(ROOT / p.spec["sources"]["environment"])
        expected = {b["id"] for b in city["buildings"]} - set(environment["remove_buildings"])
        self.assertEqual(expected, {b["id"] for b in d["plots"]})
        self.assertEqual({g["id"] for g in environment["gardens"]}, {g["id"] for g in d["gardens"]})
        self.assertEqual(expected, {a["building_id"] for a in d["building_access"]})
        self.assertFalse(d["checks"]["issues"])

    def test_private_courts_do_not_take_public_space_and_have_accessible_doors(self):
        p = self.planner
        private = [g for g in self.data["gardens"] if g["access"] == "private"]
        self.assertTrue(private)
        for g in private:
            with self.subTest(garden=g["id"]):
                columns = rect(g["bounds"]) | {(x, z) for x, _, z in g["path"]}
                self.assertFalse(columns & p.model.road_columns.keys())
                self.assertFalse(columns & p.model.court_columns.keys())
                self.assertFalse(columns & p.reserve)
                door = g["door"]
                self.assertEqual(g["owner"], door["owner"])
                self.assertEqual(g["path"][-1], door["outside"])
                inside, outside = door["inside"], door["outside"]
                self.assertEqual(2, abs(inside[0] - outside[0]) + abs(inside[2] - outside[2]))
                self.assertEqual(g["y"], p.buildings[g["owner"]]["position"][1])
                self.assertTrue(p.clear(tuple(inside), p.rules["headroom"]))

    def test_public_gardens_and_existing_forecourts_have_explicit_reasons(self):
        p = self.planner
        public = [g for g in self.data["gardens"] if g["access"] == "public"]
        self.assertTrue(public)
        for g in public:
            with self.subTest(garden=g["id"]):
                self.assertTrue(g["reason"].strip())
                x, y, z = g["path"][-1]
                self.assertEqual(y, p.model.road_columns[x, z][0])
                self.assertIsNone(g["door"])
        bindings = p.spec["courtyard_bindings"]
        self.assertEqual({c["id"] for c in self.data["courtyards"]}, set(bindings))
        for binding in bindings.values():
            self.assertEqual("public", binding["access"])
            self.assertTrue(binding["owner"].strip())
            self.assertTrue(binding["reason"].strip())

    def test_routes_reach_furnished_interiors_without_step_or_fixture_conflicts(self):
        p = self.planner
        for g in self.data["gardens"]:
            with self.subTest(garden=g["id"]):
                self.assertEqual("planned_connected", g["status"])
                self.assertEqual(g["gate"], g["path"][0])
                self.assertEqual(g["gate"], g["internal_path"][0])
                limit = p.rules[g["access"] + "_access_max_length"]
                self.assertLessEqual(len(g["path"]) - 1, limit)
                for path in (g["path"], g["internal_path"]):
                    self.assertTrue(path)
                    for a, b in zip(path, path[1:]):
                        self.assertEqual(1, abs(a[0] - b[0]) + abs(a[2] - b[2]))
                        self.assertLessEqual(abs(a[1] - b[1]), 1)
                    self.assertTrue(all(p.clear(tuple(point), p.rules["headroom"]) for point in path))
                self.assertEqual(
                    [f["kind"] for f in p.spec["garden_programs"][g["kind"]]["fixtures"]],
                    [f["kind"] for f in g["fixtures"]],
                )
                blocked = {(x, z) for x, _, z in g["internal_path"]}
                for fixture in g["fixtures"]:
                    x, y, z = fixture["position"]
                    w, h, depth = fixture["size"]
                    cells = rect([x, z, x + w - 1, z + depth - 1])
                    self.assertFalse(cells & blocked)
                    self.assertTrue(all(p.clear((xx, y, zz), h) for xx, zz in cells))
                    blocked |= cells

    def test_coastline_and_open_space_are_preserved_with_bounded_earthwork(self):
        p = self.planner
        self.assertEqual("coastal_heightfield", p.model.city["terrain"]["kind"])
        self.assertEqual(p.original_terrain, p.model.terrain)
        authorized = set()
        for g in self.data["gardens"]:
            authorized |= rect(g["bounds"]) | {(x, z) for x, _, z in g["path"]}
        for e in self.data["earthwork"]:
            x, z = e["column"]
            self.assertIn((x, z), authorized)
            self.assertLessEqual(abs(e["air_y"] - e["before_air_y"]), p.rules["local_earthwork_limit"])
            self.assertGreater(p.original_terrain[z * p.model.width + x] + 1, p.model.city["terrain"]["sea_y"])
        owned = {(x, z) for plot in self.data["plots"] for z, x0, x1 in plot["cell_runs"] for x in range(x0, x1 + 1)}
        self.assertNotIn((50, 10), owned)
        self.assertNotIn((112, 180), owned)
        self.assertNotIn((210, 180), owned)


if __name__ == "__main__":
    unittest.main()
