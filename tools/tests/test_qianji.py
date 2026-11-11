"""千机阁正式模型的功能层、原定占地、入口和楼梯连通性。"""
from functools import lru_cache
import json,unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tools.qianji.model import build,AIR,ROOT

@lru_cache(maxsize=8)
def authored(name):return build(name)

class QianjiTests(unittest.TestCase):
    def test_every_function_floor_and_gallery_is_connected_in_all_seven_models(self):
        for name in json.loads((ROOT/'blueprints/qianji/placement.json').read_text())['regions'].values():
            m,checks=authored(name)
            with self.subTest(name=name):
                self.assertGreaterEqual(checks['reachable_targets'],sum(len(r['levels']) for r in m.spec['rooms'])+2)
                self.assertEqual(len(m.spec['rooms']),len(m.compound.building_slots))
                self.assertEqual(sum(len(r['levels']) for r in m.spec['rooms']),sum(len(s.graph.volumes()) for s in m.compound.building_slots))
                x,y,z=m.spec['board'];self.assertNotEqual(AIR,m.blocks[x,y-1,z]);self.assertTrue(m.blocks[x,y,z].startswith('myvillage:jianghu_board['))
    def test_compact_footprints_stay_at_one_third_of_the_retained_large_versions(self):
        for small,large in [('folded_library','town_courts'),('island_octagon','town_water'),('terraced_gallery','town_frontier')]:
            a,_=authored(small);b,_=authored(large);self.assertTrue(.32<a.w*a.d/(b.w*b.d)<.35)
        self.assertEqual((60,67),(authored('folded_library')[0].w,authored('folded_library')[0].d))
        self.assertEqual((62,70),(authored('island_octagon')[0].w,authored('island_octagon')[0].d))
        self.assertEqual((64,66),(authored('terraced_gallery')[0].w,authored('terraced_gallery')[0].d))
    def test_headquarters_uses_large_functional_spaces_and_a_real_atrium(self):
        m,_=authored('zhongzhou_headquarters');core=next(r for r in m.rooms if r['id']=='core');hall=next(r for r in m.rooms if r['id']=='hall')
        self.assertEqual([24,20,22],[v['storey_height'] for v in core['levels']]);self.assertEqual(3,core['floors'])
        self.assertGreaterEqual(core['rect'][2]*core['rect'][3],4000);self.assertGreaterEqual(m.h,100)
        x,z,w,d=hall['rect'];upper=hall['levels'][1]['floor_y'];self.assertEqual(AIR,m.blocks[x+w//2,upper-1,z+d//2])
        self.assertIn('天机仪操作台',[t['id'] for t in m.targets]);self.assertEqual(8,len(m.spec['state_steles']))
    def test_blocking_a_real_stairwell_fails_the_connectivity_gate(self):
        m,_=build('folded_library');r=m.spec['rooms'][-1];x,z,w,d=r['box']
        shaft_x=x+w-max(v['inset'] for v in r['levels'])-9;shaft_z=z+max(v['inset'] for v in r['levels'])+3
        m.box(shaft_x,r['floor_y'],shaft_z,6,30,10,'base')
        with self.assertRaisesRegex(ValueError,'不能从入口到达'):m.connected()

if __name__=='__main__':unittest.main()
