"""真实施工边界回归：斜阶、墙顶、原建筑及岗位保护。"""
import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest

from .world import World,AIR
from .export_game import fortifications,INPUT,copy_unchanged_tile,copy_inherited_tile
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt
import json_to_nbt as nbt
from province_capitals.regional.v2.export_game import write_tile


class ConstructionTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        root=Path(self.tmp.name);self.root=root
        p=root/'data/myvillage/capitals/v3/fixture/terrain.bin.gz';p.parent.mkdir(parents=True)
        p.write_bytes(gzip.compress(struct.pack('<'+'h'*1024,*([3]*1024)),mtime=0))
        manifest={'size':[32,40,32],'terrain':{'resource':'myvillage:capitals/v3/fixture/terrain.bin.gz','top_block':'minecraft:stone','fill_block':'minecraft:stone'},'tiles':[],
                  'buildings':[],'entries':[],'life':{'places':[]}}
        self.world=World(root,manifest,json.loads((INPUT/'materials.json').read_text()))

    def test_ascending_route_width_is_perpendicular_to_travel(self):
        route=self.world.corridor([[8,4,4],[8,4,7],[8,8,17]],3,'minecraft:stone_bricks','登临阶')
        self.assertTrue(all(self.world.walkable(p) for p in route))
        self.assertEqual(AIR,self.world.get((11,5,12)))
        self.assertTrue(all(abs(a[1]-b[1])<=1 for a,b in zip(route,route[1:])))

    def test_closed_wall_handles_abrupt_ground_without_unwalkable_steps(self):
        for z in range(32):
            for x in range(16,32):self.world.terrain[z*32+x]=10
        plan={'wall':{'points':[[3,3],[28,3],[28,28],[3,28],[3,3]],'material':'slate','gates':[],'accesses':[]}}
        fortifications(self.world,plan)
        route=self.world.manifest['fortification']['walk_route']
        self.assertTrue(all(self.world.walkable(tuple(p)) for p in route))
        self.assertTrue(all(abs(a[1]-b[1])<=1 for a,b in zip(route,route[1:]+route[:1])))
        self.assertEqual(len(route),len({(p[0],p[2]) for p in route}))

    def test_fixture_and_bounds_are_not_silently_skipped(self):
        self.world.protected[7,5,7]='已有炼器工位'
        with self.assertRaisesRegex(ValueError,'受保护'):
            self.world.set((7,5,7),'minecraft:stone','新雕像')
        with self.assertRaisesRegex(ValueError,'越界'):
            self.world.set((32,5,7),'minecraft:stone','新雕像')

    def test_full_road_width_protects_both_edges_and_turns(self):
        self.world.protect_routes([{'id':'旧街','width':4,'points':[[6,4,8],[14,4,8],[14,4,16]]}])
        for point in [(8,4,6),(8,5,9),(12,4,12),(15,5,12),(15,4,8)]:
            with self.subTest(point=point),self.assertRaisesRegex(ValueError,'受保护'):
                self.world.set(point,'minecraft:stone','占道陈设')
        self.world.set((8,4,10),'minecraft:stone','街外陈设')
        self.world.verify_life()

    def test_road_edge_support_loss_is_rejected_after_authorized_clearance(self):
        self.world.protect_routes([{'id':'旧坡道','width':3,'points':[[8,4,6],[8,4,16]]}])
        self.world.set((9,3,12),AIR,'接路错误挖低',authority=True)
        with self.assertRaisesRegex(ValueError,'原有通路失去支撑'):
            self.world.verify_life()

    def test_retained_building_body_must_stay_identical(self):
        self.world.tile((0,0))[5,4,5]='minecraft:oak_planks'
        self.world.originals[0,0][5,4,5]='minecraft:oak_planks'
        self.world.manifest['buildings']=[{'id':'existing_house','position':[5,4,5],'bounds':{'min':[4,4,4],'max':[6,8,6]}}]
        self.world.set((5,4,5),'minecraft:stone','错误的新台阶')
        with self.assertRaisesRegex(ValueError,'保留建筑'):
            self.world.verify_retained_buildings()

    def test_open_wall_and_missing_landing_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'未闭合'):
            fortifications(self.world,{'wall':{'points':[[3,3],[28,3]],'material':'slate'}})
        with self.assertRaisesRegex(ValueError,'不在城墙'):
            fortifications(self.world,{'wall':{'points':[[3,3],[28,3],[28,28],[3,28],[3,3]],'material':'slate','gates':[],
                                              'accesses':[{'wall_target':[15,15],'points':[]} ]}})

    def test_unchanged_tile_palette_is_checked_without_rewriting_bytes(self):
        source=self.root/'old.nbt';destination=self.root/'new.nbt'
        write_tile(source,{(1,4,1):('minecraft:stone','',''),
                           (2,4,1):('unknown_mod:unlisted_block','','')},40)
        states=copy_unchanged_tile(source,destination)
        self.assertEqual(source.read_bytes(),destination.read_bytes())
        self.assertEqual({'minecraft:stone','unknown_mod:unlisted_block'},states)
        self.assertTrue(load_modset('vanilla').palette_block_errors(sorted(states)))

    def test_inherited_invalid_state_is_rewritten_without_dropping_nbt(self):
        source=self.root/'old.nbt';destination=self.root/'new.nbt'
        invalid='minecraft:sea_lantern[hanging=false,waterlogged=false]'
        root=nbt.Compound({'DataVersion':nbt.Int(3955),'author':nbt.String('preserved'),
            'size':nbt.ListTag(nbt.TAG_INT,[nbt.Int(16),nbt.Int(40),nbt.Int(16)]),
            'palette':nbt.ListTag(nbt.TAG_COMPOUND,[nbt.palette_state_to_tag(invalid),nbt.palette_state_to_tag('minecraft:stone')]),
            'blocks':nbt.ListTag(nbt.TAG_COMPOUND,[nbt.Compound({'pos':nbt.ListTag(nbt.TAG_INT,[nbt.Int(1),nbt.Int(4),nbt.Int(2)]),'state':nbt.Int(0),'nbt':nbt.Compound({'id':nbt.String('preserved')})})]),
            'entities':nbt.ListTag(nbt.TAG_COMPOUND,[nbt.Compound({'id':nbt.String('preserved')})])})
        nbt.write_gzipped_nbt(root,str(source))
        states,counts=copy_inherited_tile(source,destination,{invalid:'minecraft:sea_lantern'})
        _,before=read_gzipped_nbt(str(source));_,after=read_gzipped_nbt(str(destination))
        self.assertEqual({'minecraft:sea_lantern','minecraft:stone'},states)
        self.assertEqual({invalid:1},counts)
        self.assertEqual({'Name':'minecraft:sea_lantern'},after.pop('palette')[0])
        before.pop('palette');self.assertEqual(before,after)
        copy_inherited_tile(source,destination,{})
        self.assertEqual(source.read_bytes(),destination.read_bytes())


if __name__=='__main__':unittest.main()
