"""保护原版空间、卧房分隔、工作站位和对照图层。"""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import json,tempfile,unittest
from PIL import Image
from tools.moon_courtyard_home.model import build,check_preservation,walking
from tools.moon_courtyard_home.build import fixture_supports
from tools.player_homes.preview import bake_instances

class CourtyardHomeTest(unittest.TestCase):
    def setUp(self):self.site=build()

    def test_roof_part_cannot_be_removed(self):
        p=next(p for p,s in self.site.original.items()if s.startswith('yuanxian:courtyard_roof['))
        self.site.blocks.pop(p)
        with self.assertRaisesRegex(AssertionError,'原版方块'):check_preservation(self.site)

    def test_pool_cannot_be_replaced_with_paving(self):
        p=next(p for p,s in self.site.original.items()if s.startswith('minecraft:water['))
        self.site.blocks[p]='minecraft:stone'
        with self.assertRaisesRegex(AssertionError,'原版方块'):check_preservation(self.site)

    def test_open_door_reaches_all_targets_and_closed_door_separates_bedroom(self):
        self.assertEqual(6,len(walking(self.site)['paths']))
        with self.assertRaisesRegex(AssertionError,'卧床侧面'):walking(self.site,closed_doors=True)

    def test_blocked_kitchen_working_position_is_rejected(self):
        self.site.blocks[(16,3,35)]='minecraft:stone'
        with self.assertRaisesRegex(AssertionError,'厨房工作位'):walking(self.site)

    def test_lamp_chain_must_touch_the_actual_beam_geometry(self):
        self.assertEqual(4,len(fixture_supports(self.site)))
        self.site.blocks[(28,9,35)]=self.site.blocks[(28,9,35)].replace('part=2','part=1')
        with self.assertRaisesRegex(AssertionError,'不接触'):fixture_supports(self.site)

    def test_fixture_support_cannot_name_a_remote_beam(self):
        self.site.config['kit_fixtures'][1]['support']=[39,9,35]
        with self.assertRaisesRegex(AssertionError,'不相邻'):fixture_supports(self.site)

    def test_comparison_keeps_identical_original_and_added_walls_separate(self):
        class Models:
            def __init__(self,unused):self.textures={'test:wall':Image.new('RGBA',(16,16),'white')}
            def faces(self,state):return[([(0,0,0),(16,0,0),(16,16,0),(0,16,0)],(0,0,-1),[(0,0),(16,0),(16,16),(0,16)],'test:wall',(1,1,1),False,False)]
        site=SimpleNamespace(blocks={(0,0,0):'minecraft:stone',(1,0,0):'minecraft:stone'},layers={(0,0,0):'architecture',(1,0,0):'residential'},assemblies=[],c={'size':[2,1,1],'views':{}},rooms=[])
        with tempfile.TemporaryDirectory()as d,patch('tools.player_homes.preview.HomeModels',Models):
            bake_instances(site,Path('unused'),Path(d));result=json.loads((Path(d)/'scene.json').read_text())
        self.assertEqual({'architecture','residential'},{p['layer']for p in result['prototypes']})
        self.assertTrue(all(len(p['instances'])==1 for p in result['prototypes']))

if __name__=='__main__':unittest.main()
