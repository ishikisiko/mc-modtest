"""真实施工边界回归：斜阶、墙顶、原建筑及岗位保护。"""
import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest

from .world import World,AIR
from .export_game import fortifications,INPUT


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


if __name__=='__main__':unittest.main()
