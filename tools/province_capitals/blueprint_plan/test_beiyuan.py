"""北原庭院归属、公共边界和完整设施的定向回归；不运行游戏。"""
import unittest

from .generate import ROOT, generate, perimeter, read, rect


class BeiyuanPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec_path = ROOT / 'blueprints/province_capitals/blueprint_plan/beiyuan.json'
        cls.planner, cls.data = generate(cls.spec_path)
        cls.city = read(ROOT / cls.planner.spec['sources']['city'])
        cls.environment = read(ROOT / cls.planner.spec['sources']['environment'])

    def test_all_active_buildings_and_original_gardens_are_preserved(self):
        original = {b['id']: b for b in self.city['buildings']}
        plots = self.data['plots']
        self.assertEqual(set(original) - set(self.environment['remove_buildings']), {p['id'] for p in plots})
        self.assertEqual(len(plots), len({p['id'] for p in plots}))
        for plot in plots:
            self.assertEqual(original[plot['id']]['position'], plot['building']['position'])
            self.assertEqual(original[plot['id']]['size'], plot['building']['size'])
            self.assertEqual(original[plot['id']]['family'], plot['building']['family'])
        self.assertEqual({g['id'] for g in self.environment['gardens']}, {g['id'] for g in self.data['gardens']})
        self.assertFalse(self.data['checks']['issues'])
        self.assertEqual(self.planner.original_terrain, self.planner.model.terrain)

    def test_private_gardens_do_not_take_public_roads_or_forecourts(self):
        planner = self.planner
        for garden in self.data['gardens']:
            if garden['access'] != 'private':
                continue
            with self.subTest(garden=garden['id']):
                cells = rect(garden['bounds']) | {(x, z) for x, _, z in garden['path']}
                self.assertFalse(cells & planner.model.road_columns.keys())
                self.assertFalse(cells & planner.model.court_columns.keys())
                self.assertEqual(garden['owner'], garden['door']['owner'])
                self.assertEqual(garden['path'][-1], garden['door']['outside'])
                self.assertTrue(planner.clear(tuple(garden['door']['inside']), 2))
                self.assertIn((garden['door']['inside'][0], garden['door']['inside'][2]), planner.model.footprints[garden['owner']])

    def test_public_gardens_have_custodians_and_specific_reasons(self):
        gardens = {g['id']: g for g in self.data['gardens']}
        expected = {'beiyuan_garden_9': 'market', 'beiyuan_garden_17': 'warehouse', 'beiyuan_garden_19': 'inn'}
        self.assertEqual(set(expected), {g['id'] for g in gardens.values() if g['access'] == 'public'})
        for gid, custodian in expected.items():
            garden = gardens[gid]
            self.assertEqual(custodian, garden['owner'])
            self.assertTrue(garden['reason'].strip())
            self.assertTrue(garden['path'])
            x, y, z = garden['path'][-1]
            self.assertEqual(y, self.planner.model.road_columns[x, z][0])
        self.assertTrue(rect(gardens['beiyuan_garden_19']['bounds']) & self.planner.model.court_columns.keys())
        self.assertFalse(rect(gardens['beiyuan_garden_17']['bounds']) & self.planner.model.occupied.keys())

    def test_east_forge_garden_belongs_to_same_side_house(self):
        gardens = {g['id']: g for g in self.data['gardens']}
        self.assertEqual('se_n_01', gardens['beiyuan_garden_20']['owner'])
        self.assertEqual('private', gardens['beiyuan_garden_20']['access'])
        self.assertEqual('se_s_01', gardens['beiyuan_garden_22']['owner'])
        self.assertEqual('private', gardens['beiyuan_garden_22']['access'])

    def test_full_garden_programs_fit_outside_walkways_and_edges(self):
        for garden in self.data['gardens']:
            with self.subTest(garden=garden['id']):
                expected = self.planner.spec['garden_programs'][garden['kind']]['fixtures']
                self.assertEqual([f['kind'] for f in expected], [f['kind'] for f in garden['fixtures']])
                self.assertEqual([f['size'] for f in expected], [f['size'] for f in garden['fixtures']])
                blocked = {(x, z) for x, _, z in garden['internal_path']}
                gx, _, gz = garden['gate']
                edge = {p for p in perimeter(garden['bounds']) if abs(p[0] - gx) + abs(p[1] - gz) > 1}
                self.assertFalse(blocked & edge)
                for fixture in garden['fixtures']:
                    x, _, z = fixture['position']; w, _, d = fixture['size']
                    footprint = rect([x, z, x + w - 1, z + d - 1])
                    self.assertFalse(footprint & (blocked | edge))
                    self.assertTrue(footprint <= rect(garden['bounds']))
                    blocked |= footprint

    def test_gardens_leave_original_building_approaches_clear(self):
        approaches = {(x, z) for access in self.planner.model.access for x, _, z in access['points']}
        for garden in self.data['gardens']:
            with self.subTest(garden=garden['id']):
                self.assertFalse(rect(garden['bounds']) & approaches)

    def test_routes_and_local_earthwork_remain_bounded(self):
        allowed = set()
        for garden in self.data['gardens']:
            allowed |= rect(garden['bounds']) | {(x, z) for x, _, z in garden['path']}
            limit = self.planner.rules[garden['access'] + '_access_max_length']
            self.assertLessEqual(len(garden['path']) - 1, limit)
            for route in (garden['path'], garden['internal_path']):
                self.assertTrue(route)
                for a, b in zip(route, route[1:]):
                    self.assertEqual(1, abs(a[0] - b[0]) + abs(a[2] - b[2]))
                    self.assertLessEqual(abs(a[1] - b[1]), 1)
        for edit in self.data['earthwork']:
            self.assertIn(tuple(edit['column']), allowed)
            self.assertLessEqual(abs(edit['air_y'] - edit['before_air_y']), self.planner.rules['local_earthwork_limit'])


if __name__ == '__main__':
    unittest.main()
