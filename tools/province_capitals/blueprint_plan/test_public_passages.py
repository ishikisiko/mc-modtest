"""公共门楼修正只打开已声明路幅，错误归属或楼层必须拒绝。"""
import copy
import unittest

from .generate import ROOT, Planner, generate, read


class PublicPassageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.planner, cls.data = generate()

    def planner_for(self, spec):
        p = self.planner
        return Planner(spec, read(ROOT/spec['sources']['city']), p.env, p.catalog)

    def test_opening_clears_declared_road_and_retains_roof(self):
        p = self.planner
        self.assertTrue(self.data['public_passages'])
        for opening in self.data['public_passages']:
            self.assertTrue(opening['changes'])
            for x,y,z in opening['cells']:
                self.assertEqual('minecraft:air', p.state((x,y,z)))
                self.assertEqual(opening['road_id'], p.model.road_columns[x,z][4])
            self.assertTrue(all(row['position'][1] < opening['y']+opening['height'] for row in opening['changes']))

    def test_wrong_owner_road_or_storey_rejects(self):
        for change, message in (({'owner':'house_001'}, '现有门楼'), ({'road_id':'unknown'}, '声明道路'),
                                ({'y':20}, '首层'), ({'height':8}, '净空高度')):
            spec = copy.deepcopy(self.planner.spec)
            spec['public_passages'][0].update(change)
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, message):
                self.planner_for(spec)

    def test_opening_cannot_remove_side_piers(self):
        spec = copy.deepcopy(self.planner.spec)
        opening = spec['public_passages'][0]
        footprint = self.planner.model.footprints[opening['owner']]
        opening['bounds'][0] = min(x for x,z in footprint)
        with self.assertRaisesRegex(ValueError, '两侧门墩'):
            self.planner_for(spec)

    def test_door_material_comes_from_a_style_without_door_slot(self):
        p, data = generate(ROOT/'blueprints/province_capitals/blueprint_plan/beiyuan.json')
        self.assertFalse(p.model.style.material_slots.get('DOOR'))
        owner = next(d['owner'] for d in data['doors'] if d['action']=='open_door')
        material = p.door_material(owner)
        self.assertTrue(material.startswith('minecraft:') and material.endswith('_door'))


if __name__ == '__main__':
    unittest.main()
