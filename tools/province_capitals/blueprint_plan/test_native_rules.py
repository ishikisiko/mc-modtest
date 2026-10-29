"""真实失败回归：逐高度路幅、跨块完整组件及紧凑原生模板。"""
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

from buildgen.nbtread import read_gzipped_nbt,state_string
from province_capitals.regional.v2.common import props
from .atomic_blocks import collect
from .nbt_entities import write_tile
from .regional_roads import road_positions
from .export_minecraft import road_audit


class World:
    def __init__(self,cells):
        self.cells=cells;self.refs={(x//16,z//16):{} for x,y,z in cells};self.dirty=set()
    def tile(self,key):
        return {(x%16,y,z%16):s for (x,y,z),s in self.cells.items() if (x//16,z//16)==key}
    def get(self,p):return self.cells.get(p,'minecraft:air')


class NativeRuleTests(unittest.TestCase):
    def test_overlapping_road_heights_are_both_audited(self):
        roads=[{'id':'lower','width':1,'points':[[0,4,0],[2,4,0]]},
               {'id':'upper','width':1,'points':[[1,5,0],[1,5,2]]}]
        points=road_positions(roads,16,16)
        self.assertEqual({'lower'},points[1,4,0]);self.assertEqual({'upper'},points[1,5,0])
        planner=SimpleNamespace(spec={},model=SimpleNamespace(roads=roads))
        world=SimpleNamespace(width=16,depth=16,config={'collision':{'headroom':2}},
                              walkable=lambda p:p!=(1,4,0),get=lambda p:'minecraft:air')
        result=road_audit(planner,world)
        self.assertEqual('needs_design',result['status'])
        self.assertEqual([1,4,0],result['failed'][0]['position'])

    def test_cross_boundary_bed_is_one_complete_group(self):
        cells={(15,3,4):props('minecraft:white_bed',facing='east',part='foot',occupied=False),
               (16,3,4):props('minecraft:white_bed',facing='east',part='head',occupied=False)}
        groups=collect(World(cells),{'atomic_blocks':{'minecraft:white_bed':'bed'}},{'families':[]})
        self.assertEqual(1,len(groups));self.assertEqual([15,3,4],groups[0]['origin'])
        self.assertEqual([2,1,1],groups[0]['size']);self.assertEqual(cells,groups[0]['cells'])

    def test_orphan_bed_head_is_rejected(self):
        cells={(16,3,4):props('minecraft:white_bed',facing='east',part='head',occupied=False)}
        with self.assertRaisesRegex(ValueError,'失去根部'):
            collect(World(cells),{'atomic_blocks':{'minecraft:white_bed':'bed'}},{'families':[]})

    def test_rotated_rack_keeps_all_four_parts(self):
        cells={(15,3+part//2,15+part%2):props('myvillage:drying_rack',facing='east',part=part,waterlogged=False) for part in range(4)}
        groups=collect(World(cells),{'atomic_blocks':{'myvillage:drying_rack':'street_furniture'}},
                       {'families':[{'id':'drying_rack','size':[2,2,1]}]})
        self.assertEqual([1,2,2],groups[0]['size']);self.assertEqual(cells,groups[0]['cells'])

    def test_compact_template_preserves_real_origin_relative_cells(self):
        cells={(0,0,0):('minecraft:white_bed[facing=east,occupied=false,part=foot]','',''),
               (1,0,0):('minecraft:white_bed[facing=east,occupied=false,part=head]','','')}
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'bed.nbt';count,_,_=write_tile(path,cells,1,{},size=[2,1,1])
            _,raw=read_gzipped_nbt(str(path));palette=[state_string(s) for s in raw['palette']]
            self.assertEqual(2,count);self.assertEqual([2,1,1],raw['size'])
            self.assertEqual({p:s[0] for p,s in cells.items()}, {tuple(b['pos']):palette[b['state']] for b in raw['blocks']})

    def test_wall_sign_support_is_opposite_facing_at_every_tile_edge(self):
        rules={'atomic_blocks':{'minecraft:spruce_wall_sign':'wall_sign'}}
        for facing,p,support in (
                ('west',(15,3,8),(16,3,8)),('east',(16,3,8),(15,3,8)),
                ('north',(8,3,15),(8,3,16)),('south',(8,3,16),(8,3,15))):
            with self.subTest(facing=facing):
                state=props('minecraft:spruce_wall_sign',facing=facing,waterlogged=False)
                groups=collect(World({p:state,support:'minecraft:glass_pane'}),rules,{'families':[]})
                self.assertEqual(1,len(groups));self.assertEqual([1,1,1],groups[0]['size'])
                self.assertEqual({p:state},groups[0]['cells'])
                self.assertEqual([list(support)],groups[0]['supports'])

    def test_wall_sign_without_support_is_rejected(self):
        state=props('minecraft:spruce_wall_sign',facing='west',waterlogged=False)
        with self.assertRaisesRegex(ValueError,'缺少原生支撑'):
            collect(World({(15,3,8):state}),{'atomic_blocks':{'minecraft:spruce_wall_sign':'wall_sign'}},{'families':[]})

    def test_wall_sign_and_support_in_same_tile_stay_in_original_template(self):
        state=props('minecraft:spruce_wall_sign',facing='west',waterlogged=False)
        self.assertEqual([],collect(World({(14,3,8):state,(15,3,8):'minecraft:glass_pane'}),
                                   {'atomic_blocks':{'minecraft:spruce_wall_sign':'wall_sign'}},{'families':[]}))


if __name__=='__main__':unittest.main()
