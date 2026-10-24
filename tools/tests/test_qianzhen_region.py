"""Verify the settlement adapter contains the actual six Chapter 1 templates."""
import json
from pathlib import Path
import tempfile
import unittest
from tools.regiongen.qianzhen_assets import compile_resources,RESOURCE,ROOT
from tools.qianzhen import story_assets
from buildgen.nbtread import read_gzipped_nbt,state_string


class QianzhenRegionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name)
        cls.report=compile_resources(cls.root/'region');story_assets.generate(cls.root/'original')
        cls.region=json.loads((cls.root/'region'/RESOURCE).read_text())
        cls.scene=json.loads((cls.root/'original/data/myvillage/story/qianzhen_scene.json').read_text())
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def test_every_original_building_cell_and_block_entity_survives_slicing(self):
        merged={}
        for tile in self.region['tiles']:
            _,tag=read_gzipped_nbt(str(self.root/'region'/('data/myvillage/structure/'+tile['template'].split(':')[1]+'.nbt')))
            for b in tag['blocks']:
                x,y,z=b['pos'];key=(tile['x']+x,y,tile['z']+z);self.assertNotIn(key,merged)
                merged[key]=(state_string(tag['palette'][b['state']]),b.get('nbt'))
        self.assertEqual(6,len(self.region['sites']))
        for site in self.region['sites']:
            original=next(s for s in self.scene['sites'] if s['id']==site['id'])
            self.assertEqual(original['sha256'],site['sha256'])
            _,tag=read_gzipped_nbt(str(self.root/'original'/('data/myvillage/structure/'+site['template'].split(':')[1]+'.nbt')))
            for b in tag['blocks']:
                at=tuple(site['origin'][i]+b['pos'][i] for i in range(3))
                self.assertEqual((state_string(tag['palette'][b['state']]),b.get('nbt')),merged[at],(site['id'],at))
        self.assertEqual(self.report['cells'],len(merged))

    def test_roads_and_query_entries_keep_original_relative_positions(self):
        shift=self.region['source_shift']
        self.assertEqual([{**r,'pos':[r['pos'][i]+shift[i] for i in range(3)]} for r in self.scene['roads']],self.region['roads'])
        for site in self.region['sites']:
            original=json.loads((ROOT/'blueprints/qianzhen'/(site['id']+'.json')).read_text())
            self.assertEqual([site['origin'][i]+original['walk_targets']['entry'][i] for i in range(3)],site['entry'])
        self.assertTrue(self.report['source_geometry_unchanged']);self.assertEqual(153,self.report['connected_stations'])


if __name__=='__main__':unittest.main()
