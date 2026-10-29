"""南疆双岸蓝图的归属、门洞与完整路幅检查；不声称原生游戏验收。"""
import unittest

from .generate import ROOT, generate, read, rect


SPEC = ROOT / 'blueprints/province_capitals/blueprint_plan/nanjiang.json'


class NanjiangBlueprintTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.planner, cls.data = generate(SPEC)

    def test_all_active_buildings_and_existing_gardens_are_retained(self):
        p, d = self.planner, self.data
        city = read(ROOT / p.spec['sources']['city'])
        environment = read(ROOT / p.spec['sources']['environment'])
        self.assertEqual({b['id'] for b in city['buildings']} - set(environment['remove_buildings']),
                         {plot['id'] for plot in d['plots']})
        self.assertEqual({g['id'] for g in environment['gardens']}, {g['id'] for g in d['gardens']})
        self.assertFalse(d['checks']['issues'])

    def test_every_public_forecourt_exports_its_explicit_owner(self):
        p, d = self.planner, self.data
        bindings = p.spec['courtyard_bindings']
        self.assertEqual({c['id'] for c in p.model.city['courtyards']}, set(bindings))
        for court in d['courtyards']:
            with self.subTest(court=court['id']):
                binding = bindings[court['id']]
                self.assertEqual('public', binding['access'])
                self.assertTrue(binding['owner'])
                self.assertTrue(binding['reason'])
                for key, value in binding.items():
                    self.assertEqual(value, court.get(key), key)

    def test_private_gardens_link_only_their_reachable_house(self):
        p = self.planner
        for garden in self.data['gardens']:
            with self.subTest(garden=garden['id']):
                self.assertTrue(garden['reason'])
                self.assertTrue(garden.get('owner') or garden.get('steward'))
                for route in (garden['path'], garden['internal_path']):
                    self.assertTrue(route)
                    for a, b in zip(route, route[1:]):
                        self.assertEqual(1, abs(a[0]-b[0]) + abs(a[2]-b[2]))
                        self.assertLessEqual(abs(a[1]-b[1]), 1)
                if garden['access'] != 'private':
                    x, y, z = garden['path'][-1]
                    self.assertEqual(y, p.model.road_columns[x, z][0])
                    continue
                cells = rect(garden['bounds']) | {(x, z) for x, y, z in garden['path']}
                self.assertFalse(cells & p.model.road_columns.keys())
                self.assertFalse(cells & p.model.court_columns.keys())
                door = garden['door']
                self.assertEqual(garden['owner'], door['owner'])
                self.assertEqual(garden['path'][-1], door['outside'])
                self.assertTrue(p.clear(tuple(door['inside']), 2))

    def test_ground_herb_garden_cannot_be_claimed_by_lifted_longhouse(self):
        garden = next(g for g in self.data['gardens'] if g['id'] == 'nanjiang_garden_24')
        self.assertEqual('public', garden['access'])
        self.assertNotEqual(garden['y'], self.planner.buildings['house_036']['position'][1])
        with self.assertRaisesRegex(ValueError, '标高不同'):
            self.planner.garden_path(garden, {'access': 'private', 'owner': 'house_036'})

    def test_every_declared_road_column_has_full_headroom(self):
        p = self.planner
        failures = []
        for (x, z), road in p.model.road_columns.items():
            if not p.clear((x, road[0], z), p.rules['headroom']):
                failures.append({'road_id': road[4], 'position': [x, road[0], z]})
        self.assertEqual([], failures)


if __name__ == '__main__':
    unittest.main()
