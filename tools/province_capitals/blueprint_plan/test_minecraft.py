"""原生导出关键方向与部件语义，独立于网页几何检查。"""
import unittest

from .export_minecraft import route, fixture, leaf_distances


class TinyWorld:
    def __init__(self):self.cells={}
    def set(self,p,state,reason):self.cells[p]=state
    def get(self,p):return self.cells.get(p,'minecraft:air')
    def surface(self,x,y,z,state,reason):self.cells[x,y-1,z]=state
    def walkable(self,p):return (p[0],p[1]-1,p[2]) in self.cells


class MinecraftTests(unittest.TestCase):
    def test_uphill_and_downhill_generate_same_stair_facing(self):
        materials={'path':'minecraft:stone_bricks','stairs':'minecraft:stone_brick_stairs'}
        points=[[0,13,0],[1,12,0],[2,11,0]];a,b=TinyWorld(),TinyWorld()
        descending=route(a,points,materials,'down',{})
        ascending=route(b,list(reversed(points)),materials,'up',{})
        self.assertEqual(descending,ascending)
        self.assertEqual({(0,12,0),(1,11,0)},set(descending))
        self.assertTrue(all('facing=west' in s for s in descending.values()))

    def test_oversteep_route_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'超过一格'):
            route(TinyWorld(),[[0,10,0],[1,12,0]],{'path':'minecraft:stone_bricks'},'bad',{})

    def test_native_rack_uses_all_four_registered_parts(self):
        world=TinyWorld()
        fixture(world,{'id':'rack','kind':'drying_rack','position':[10,20,30],'size':[2,2,1]},
                {'drying_rack':'myvillage:drying_rack'},'north')
        self.assertEqual({(10,20,30),(11,20,30),(10,21,30),(11,21,30)},set(world.cells))
        for part,state in enumerate(world.cells.values()):self.assertIn(f'part={part}',state)

    def test_leaf_distance_follows_connected_leaves_to_logs(self):
        world=TinyWorld();world.set((0,0,0),'minecraft:oak_log','trunk')
        new={(1,0,0),(2,0,0),(3,0,0),(5,0,0)}
        for p in new:world.set(p,'minecraft:oak_leaves[persistent=true,waterlogged=false]','leaf')
        result=leaf_distances(world,new,{'minecraft:oak_log'})
        self.assertEqual({(1,0,0):1,(2,0,0):2,(3,0,0):3,(5,0,0):7},result)
        self.assertTrue(all('persistent=true' in world.get(p) and 'waterlogged=false' in world.get(p) for p in new))


if __name__=='__main__':unittest.main()
