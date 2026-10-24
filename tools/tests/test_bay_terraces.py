"""Production geometry and failure checks for the selected terrace-market layout."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import numpy as np
import gzip
import struct

from tools.regiongen.bay import generate, solve_levels, RECIPE
from tools.regiongen.scene import validate, AIR
from tools.regiongen.bay_native import creative_handoff_metadata, sign_component
from buildgen.groups import get_group, group_for_style


class BayTerracesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory()
        cls.plan,cls.volume,cls.preview,cls.expected,cls.signs,cls.report=generate(Path(cls.temp.name))

    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def test_complete_templates_and_shared_street_graph(self):
        self.assertTrue(self.report['passed'],self.report['failures'])
        self.assertEqual(14,len(self.plan['buildings']))
        self.assertEqual({0,1,2,3},{b['rotation'] for b in self.plan['buildings']})
        self.assertTrue(all(self.report['checks']['all_building_approaches_connected']['detail'].values()))
        self.assertEqual(self.report['checks']['all_route_points_connected']['detail']['total'],
                         self.report['checks']['all_route_points_connected']['detail']['visited'])

    def test_junction_cannot_have_two_incompatible_fixed_heights(self):
        recipe=json.loads(RECIPE.read_text())
        paths=[('short',[(10,10),(11,10),(12,10)])]
        with self.assertRaisesRegex(ValueError,'Insufficient street length'):
            solve_levels(paths,{(10,10):70,(12,10):74},recipe,np.full((32,32),70))

    def test_stair_facing_corruption_is_not_accepted(self):
        r=next(r for r in self.plan['roads'] if r['facing']);p=(r['x'],r['height']-1,r['z']);old=self.volume.get(p)
        wrong={'north':'south','south':'north','east':'west','west':'east'}[r['facing']]
        try:
            self.volume.set(p,old.replace('facing='+r['facing'],'facing='+wrong))
            report=validate(self.plan,self.volume,self.expected,self.preview)
            self.assertIn('stair_transitions',report['failures'])
        finally:self.volume.set(p,old)

    def test_missing_support_is_not_accepted(self):
        a=self.plan['spawn'];p=(a['x'],a['y']-1,a['z']);old=self.volume.get(p)
        try:
            self.volume.set(p,AIR)
            self.assertIn('route_support',validate(self.plan,self.volume,self.expected,self.preview)['failures'])
        finally:self.volume.set(p,old)

    def test_river_closes_before_native_transition(self):
        recipe=self.plan['recipe'];cfg=recipe['worldgen'];n=recipe['size']
        for z in range(n):
            for x in range(n):
                if self.plan['heights'][z*n+x]<recipe['river']['water_y']:
                    self.assertGreaterEqual(min(x+cfg['margin'],z+cfg['margin'],n+cfg['margin']-1-x,n+cfg['margin']-1-z),cfg['blend_width'])

    def test_no_overlapping_building_envelopes(self):
        for i,a in enumerate(self.plan['buildings']):
            for b in self.plan['buildings'][i+1:]:
                self.assertFalse(a['x']<b['x']+b['width'] and b['x']<a['x']+a['width'] and
                                 a['z']<b['z']+b['depth'] and b['z']<a['z']+a['depth'],(a['id'],b['id']))

    def test_group_registration_keeps_existing_style_owner(self):
        self.assertEqual('terrace_market_region',get_group('bay_terraces').layout_strategy)
        self.assertEqual('cultivation_town',group_for_style('cultivation_town').group_id)

    def test_handoff_only_changes_existing_typed_world_settings(self):
        def name(value):
            value=value.encode();return struct.pack('>H',len(value))+value
        original=(b'\x0a\x00\x00\x0a'+name('Data')+b'\x01'+name('allowCommands')+b'\x00'
                  +b'\x03'+name('GameType')+struct.pack('>i',0)+b'\x04'+name('RandomSeed')
                  +struct.pack('>q',20261025)+b'\x08'+name('LevelName')+name('保留存档')+b'\x00\x00')
        path=Path(self.temp.name)/'settings.dat';path.write_bytes(gzip.compress(original))
        changed=creative_handoff_metadata(path);updated=gzip.decompress(path.read_bytes())
        self.assertEqual({'allowCommands','GameType'},set(changed))
        self.assertEqual(len(original),len(updated))
        self.assertEqual(2,sum(a!=b for a,b in zip(original,updated)))
        self.assertIn(struct.pack('>q',20261025),updated)
        self.assertIn('保留存档'.encode(),updated)
        with self.assertRaises(ValueError):
            path.write_bytes(gzip.compress(original[:-3]));creative_handoff_metadata(path)

    def test_vanilla_literal_sign_encoding_preserves_text_and_style(self):
        self.assertEqual(sign_component('"药铺"'),sign_component('{"text":"药铺"}'))
        self.assertNotEqual(sign_component('"客栈"'),sign_component('"药铺"'))
        self.assertNotEqual(sign_component('{"text":"药铺","bold":true}'),sign_component('"药铺"'))


if __name__=='__main__':unittest.main()
