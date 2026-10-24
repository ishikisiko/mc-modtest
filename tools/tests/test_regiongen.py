"""Focused offline regressions for the actual region plan / block contract."""
import json
from pathlib import Path
import tempfile
import unittest

from tools.regiongen.assets import ROOT, load_assets
from tools.regiongen.planner import plan as make_plan
from tools.regiongen.scene import build, validate, AIR
from buildgen.groups import get_group, group_for_style


class RegionSampleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory()
        cls.recipe=ROOT/'blueprints/regiongen/river_valley_v1.json'
        cls.assets=load_assets(json.loads(cls.recipe.read_text()))
        cls.plan=make_plan(cls.recipe,cls.assets,Path(cls.temp.name)/'first',20261024)
        cls.volume,cls.preview,cls.expected,cls.signs=build(cls.plan,cls.assets)

    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def report(self):return validate(self.plan,self.volume,self.expected,self.preview)

    def test_actual_blocks_satisfy_plan(self):
        report=self.report()
        self.assertTrue(report['passed'],report['failures'])
        self.assertEqual(report['buildings'],16)
        self.assertGreater(max(self.plan['heights'])-min(self.plan['heights']),40)

    def test_replay_and_other_seed(self):
        same=make_plan(self.recipe,self.assets,Path(self.temp.name)/'same',20261024)
        self.assertEqual(same,self.plan)
        other=make_plan(self.recipe,self.assets,Path(self.temp.name)/'other',20261025)
        self.assertNotEqual(other['heights'],same['heights'])
        self.assertNotEqual([(b['x'],b['z']) for b in other['buildings']],[(b['x'],b['z']) for b in same['buildings']])

    def test_blocked_approach_is_not_connected(self):
        a=self.plan['buildings'][0]['approach'];p=(a['x'],a['y'],a['z']);old=self.volume.get(p)
        try:
            self.volume.set(p,'minecraft:stone')
            report=self.report()
            self.assertIn('route_clearance',report['failures'])
            self.assertFalse(report['checks']['all_building_approaches_connected']['passed'])
        finally:self.volume.set(p,old)

    def test_missing_support_is_not_walkable(self):
        a=self.plan['spawn'];p=(a['x'],a['y']-1,a['z']);old=self.volume.get(p)
        try:
            self.volume.set(p,AIR)
            self.assertIn('route_support',self.report()['failures'])
        finally:self.volume.set(p,old)

    def test_wrong_facing_stair_rejected(self):
        r=next(r for r in self.plan['roads'] if r['facing']);p=(r['x'],r['height']-1,r['z']);old=self.volume.get(p)
        opposite={'north':'south','south':'north','east':'west','west':'east'}[r['facing']]
        try:
            self.volume.set(p,old.replace('facing='+r['facing'],'facing='+opposite))
            self.assertIn('stair_transitions',self.report()['failures'])
        finally:self.volume.set(p,old)

    def test_foundation_hole_rejected(self):
        b=self.plan['buildings'][0];p=(b['x'],self.volume.minimum[1]+1,b['z']);old=self.volume.get(p)
        try:
            self.volume.set(p,AIR)
            self.assertIn('building_substrate_solid',self.report()['failures'])
        finally:self.volume.set(p,old)

    def test_template_change_rejected(self):
        id=next(iter(self.expected));p=next(p for p,s in self.expected[id].items() if 'deepslate' in s);old=self.volume.get(p)
        try:
            self.volume.set(p,AIR)
            self.assertIn('template:'+id,self.report()['failures'])
        finally:self.volume.set(p,old)

    def test_new_group_preserves_existing_style_owner(self):
        self.assertEqual(get_group('river_valley').layout_strategy,'region_recipe')
        self.assertEqual(group_for_style('cultivation_town').group_id,'cultivation_town')


if __name__=='__main__':unittest.main()
