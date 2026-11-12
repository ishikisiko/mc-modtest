"""千机阁正式模型的功能层、原定占地、入口和楼梯连通性。"""
from functools import lru_cache
import json,unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from tools.qianji.model import build,AIR,ROOT

@lru_cache(maxsize=8)
def authored(name):return build(name)

class QianjiTests(unittest.TestCase):
    def test_existing_road_cut_uses_actual_ground_and_support_under_retired_eaves(self):
        from types import SimpleNamespace
        from tools.qianji.infill_export import verify_existing_road_cut
        blocks={(512,100,1063):'minecraft:stone_brick_slab[type=bottom,waterlogged=false]',(512,101,1063):'minecraft:deepslate_tile_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'}
        world=SimpleNamespace(ground=lambda x,z:100,get=lambda p:blocks.get(p,AIR),walkable=lambda p:False)
        self.assertFalse(verify_existing_road_cut(world,512,1063,101,100))
        with self.assertRaisesRegex(ValueError,'地形列与实际切片不一致'):verify_existing_road_cut(world,512,1063,101,99)
        blocks.pop((512,100,1063))
        with self.assertRaisesRegex(ValueError,'缺少实际路面支撑'):verify_existing_road_cut(world,512,1063,101,100)

    def test_annex_cache_comparison_includes_the_authored_entrance_road(self):
        from tools.qianji.runtime_export import entrance_road
        cells=dict(entrance_road([267,11,254],280,10,dict(road_width=7,headroom=3),'minecraft:polished_andesite'))
        # 西漠分阁入口的最终石坪覆盖了作者草坪，原失败坐标必须参与比较。
        self.assertEqual('minecraft:polished_andesite',cells[270,10,255])
        self.assertEqual(AIR,cells[270,12,255]);self.assertNotIn((271,10,255),cells)

    def test_east_facing_reader_camera_preserves_its_block_centre(self):
        from tools.qianji.capture import placed_point
        office=dict(size=[170,137,159],rotation_quarters=1,bounds=[365,946,524,1116],base_y=80)
        # 办事廊读者点为模型(20,33,14)，东向旋转后位于城市(509,113,966)。
        self.assertEqual([509.5,114.62,966.5],placed_point(office,[20.5,34.62,14.5]))

    def test_atmosphere_anchor_payload_survives_structure_export(self):
        import tempfile,io
        from tools.qianji.runtime_export import atmosphere_payload
        from province_capitals.blueprint_plan.nbt_entities import write_tile,block_entities
        from buildgen.nbtread import _read_payload,TAG_COMPOUND
        payload=atmosphere_payload();decoded=_read_payload(io.BytesIO(payload),TAG_COMPOUND)
        self.assertEqual('myvillage:qianji_atmosphere_anchor',decoded['id'])
        self.assertEqual(0,decoded.get('CycleOffset',0))
        entities={(3,4,5):{'block':'myvillage:qianji_atmosphere_anchor','payload':payload}}
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'tile.nbt'
            write_tile(path,{(3,4,5):('myvillage:qianji_atmosphere_anchor[facing=south]','qianji','organization')},16,entities)
            self.assertEqual(entities,block_entities(path))

    def test_every_function_floor_and_gallery_is_connected_in_all_seven_models(self):
        for name in json.loads((ROOT/'blueprints/qianji/placement.json').read_text())['regions'].values():
            m,checks=authored(name)
            with self.subTest(name=name):
                self.assertGreaterEqual(checks['reachable_targets'],sum(len(r['levels']) for r in m.spec['rooms'])+2)
                self.assertEqual(len(m.spec['rooms']),len(m.compound.building_slots))
                self.assertEqual(sum(len(r['levels']) for r in m.spec['rooms']),sum(len(s.graph.volumes()) for s in m.compound.building_slots))
                x,y,z=m.spec['board'];self.assertNotEqual(AIR,m.blocks[x,y-1,z]);self.assertTrue(m.blocks[x,y,z].startswith('myvillage:jianghu_board['))
                rx,ry,rz=m.spec['reader']
                for dy in (0,1,2):self.assertEqual(AIR,m.blocks.get((rx,ry+dy,rz),AIR),(name,dy))
                if name=='zhongzhou_headquarters':self.assertEqual('myvillage:qianji_floor',m.blocks[rx,ry-1,rz])
    def test_compact_footprints_stay_at_one_third_of_the_retained_large_versions(self):
        for small,large in [('folded_library','town_courts'),('island_octagon','town_water'),('terraced_gallery','town_frontier')]:
            a,_=authored(small);b,_=authored(large);self.assertTrue(.32<a.w*a.d/(b.w*b.d)<.35)
        self.assertEqual((60,67),(authored('folded_library')[0].w,authored('folded_library')[0].d))
        self.assertEqual((62,70),(authored('island_octagon')[0].w,authored('island_octagon')[0].d))
        self.assertEqual((64,66),(authored('terraced_gallery')[0].w,authored('terraced_gallery')[0].d))
    def test_headquarters_uses_large_functional_spaces_and_a_real_atrium(self):
        m,_=authored('zhongzhou_headquarters');core=next(r for r in m.rooms if r['id']=='core');hall=next(r for r in m.rooms if r['id']=='hall')
        self.assertEqual((170,159),(m.w,m.d));self.assertEqual(5,core['floors'])
        base=core['levels'][0]['floor_y'];self.assertEqual([0,15,28,41,54],[v['floor_y']-base for v in core['levels']])
        self.assertEqual([14,12,12,12,10],[v['storey_height'] for v in core['levels']])
        from tools.qianji.headquarters_model import Collision,data
        collision=Collision(data())
        rx,ry,rz=m.spec['reader'];self.assertEqual({'floor'},{box[6] for box in data()['converted'][rx,ry-1,rz][0]})
        # 大殿中央从首层保留通高净空，夹层目标分别位于两侧回廊。
        from tools.qianji.infill import translate
        x,y,z=translate([84,4,82],'hall');self.assertIn(y+.0625,collision.levels(round(x*4),round(z*4)))
        self.assertNotIn(translate([84,10.25,82],'hall')[1],collision.levels(round(x*4),round(z*4)))
        self.assertIn('hall_east_mezzanine',[t['id'] for t in m.targets])
        anchors=[p for p,s in m.blocks.items() if s.startswith('myvillage:qianji_atmosphere_anchor[')]
        self.assertEqual([tuple(m.spec['atmosphere_anchor'])],anchors)
    def test_blocking_a_real_stairwell_fails_the_connectivity_gate(self):
        m,_=build('folded_library');r=m.spec['rooms'][-1];x,z,w,d=r['box']
        shaft_x=x+w-max(v['inset'] for v in r['levels'])-9;shaft_z=z+max(v['inset'] for v in r['levels'])+3
        m.box(shaft_x,r['floor_y'],shaft_z,6,30,10,'base')
        with self.assertRaisesRegex(ValueError,'不能从入口到达'):m.connected()
    def test_large_room_centres_receive_independent_floor_lighting(self):
        m,_=authored('zhongzhou_headquarters')
        self.assertTrue(m.lights)
        for item in m.lighting['checks'][m.spec['id']]:
            x,y,z=item['position']
            distance=min(abs(x-a)+abs(y-b)+abs(z-c) for a,b,c in m.lights)
            self.assertLessEqual(distance,15-m.lighting['minimum_light'],item['name'])
        for name in json.loads((ROOT/'blueprints/qianji/placement.json').read_text())['regions'].values():
            model,_=authored(name)
            if name=='zhongzhou_headquarters':continue # 总舵改为有实体吊杆的顶灯，原有分阁保留地灯规则。
            for room in model.rooms:
                for level in room['levels']:
                    self.assertTrue(any(y==level['floor_y']-1 and level['box'][0]<=x<level['box'][0]+level['box'][2]
                                        and level['box'][1]<=z<level['box'][1]+level['box'][3] for x,y,z in model.lights),(name,room['id']))

if __name__=='__main__':unittest.main()
