"""东荒全城覆盖与私院归属；只验证离线蓝图，不声称游戏或玩家验收。"""
from collections import Counter
import unittest

from .generate import ROOT, generate, perimeter, read, rect


class DonghuangPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.planner, cls.data = generate(ROOT / 'blueprints/province_capitals/blueprint_plan/donghuang.json')

    def test_all_active_buildings_and_existing_courts_are_preserved(self):
        p, d = self.planner, self.data
        city = read(ROOT / p.spec['sources']['city'])
        environment = read(ROOT / p.spec['sources']['environment'])
        active = {b['id'] for b in city['buildings']} - set(environment['remove_buildings'])
        self.assertEqual(active, {b['id'] for b in d['plots']})
        self.assertEqual(len(active), len(d['plots']))
        self.assertEqual(Counter(b['role'] for b in city['buildings'] if b['id'] in active),
                         Counter(b['role'] for b in d['plots']))
        self.assertEqual(city['courtyards'], [{key: court[key] for key in source}
                         for source, court in zip(city['courtyards'], d['courtyards'])])
        self.assertEqual(len(city['courtyards']), len(d['courtyards']))
        self.assertTrue(all(court['access']=='public' and court['owner'] and court['reason']
                            for court in d['courtyards']))
        self.assertEqual({g['id'] for g in environment['gardens']}, {g['id'] for g in d['gardens']})
        self.assertEqual(len(environment['gardens']), len(d['gardens']))
        self.assertFalse(d['checks']['issues'])
        self.assertEqual(p.original_terrain, p.model.terrain)

    def test_work_courts_belong_to_same_side_house_or_institution(self):
        p = self.planner
        gardens = {g['id']: g for g in self.data['gardens']}
        self.assertEqual('smith', gardens['donghuang_garden_3']['owner'])
        self.assertEqual('warehouse', gardens['donghuang_garden_16']['owner'])
        self.assertTrue(gardens['donghuang_garden_16']['reason'])
        for g in gardens.values():
            with self.subTest(garden=g['id']):
                self.assertIn(g['access'], ('private', 'public'))
                self.assertEqual('work', g['kind'])
                self.assertTrue(g['name'])
                if g['access'] == 'public':
                    self.assertTrue(g['reason'])
                    continue
                self.assertIn(g['owner'], p.buildings)
                columns = rect(g['bounds']) | {(x, z) for x, _, z in g['path']}
                self.assertFalse(columns & p.model.road_columns.keys())
                self.assertFalse(columns & p.model.court_columns.keys())
                self.assertFalse(columns & p.reserve)
                self.assertEqual(g['owner'], g['door']['owner'])
                self.assertEqual(g['path'][-1], g['door']['outside'])
                self.assertTrue(p.clear(tuple(g['door']['inside']), 2))
                self.assertIn(tuple(g['path'][-1]), p.door_candidates(g['owner']))
                plot = next(plot for plot in self.data['plots'] if plot['id'] == g['owner'])
                self.assertIn(g['id'], plot['garden_ids'])

    def test_routes_and_local_earthwork_stay_within_declared_limits(self):
        p = self.planner
        allowed = set()
        for g in self.data['gardens']:
            with self.subTest(garden=g['id']):
                self.assertEqual(g['gate'], g['path'][0])
                self.assertIn((g['gate'][0], g['gate'][2]), perimeter(g['bounds']))
                limit = p.rules['private_access_max_length' if g['access'] == 'private' else 'public_access_max_length']
                self.assertLessEqual(len(g['path']) - 1, limit)
                for route in (g['path'], g['internal_path']):
                    self.assertTrue(route)
                    for a, b in zip(route, route[1:]):
                        self.assertEqual(1, abs(a[0] - b[0]) + abs(a[2] - b[2]))
                        self.assertLessEqual(abs(a[1] - b[1]), 1)
                allowed |= rect(g['bounds']) | {(x, z) for x, _, z in g['path']}
        for e in self.data['earthwork']:
            self.assertIn(tuple(e['column']), allowed)
            self.assertLessEqual(abs(e['air_y'] - e['before_air_y']), p.rules['local_earthwork_limit'])

    def test_each_work_court_keeps_usable_facilities_and_internal_path(self):
        for g in self.data['gardens']:
            with self.subTest(garden=g['id']):
                self.assertEqual({'drying_rack', 'workbench'}, {f['kind'] for f in g['fixtures']})
                reserved = {(x, z) for x, _, z in g['internal_path']}
                gx, _, gz = g['gate']
                boundary = {p for p in perimeter(g['bounds']) if abs(p[0] - gx) + abs(p[1] - gz) > 1}
                self.assertFalse(reserved & boundary)
                for f in g['fixtures']:
                    x, y, z = f['position']
                    w, h, depth = f['size']
                    cells = rect([x, z, x + w - 1, z + depth - 1])
                    self.assertLessEqual(cells, rect(g['bounds']))
                    self.assertFalse(cells & reserved)
                    self.assertFalse(cells & boundary)
                    self.assertTrue(all(self.planner.clear((xx, y, zz), h) for xx, zz in cells))
                    reserved |= cells


if __name__ == '__main__':
    unittest.main()
