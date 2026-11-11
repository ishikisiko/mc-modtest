"""坡地构造的整地边界与实际楼梯依赖回归。"""
from types import SimpleNamespace
import unittest

import numpy as np

from .terrace_model import TerraceEstate, coarse_connectivity
from .geometry import state, Estate


class TerraceSafetyTests(unittest.TestCase):
    def fixture(self, facing='east'):
        return SimpleNamespace(
            terrain=np.zeros((117,104),int),base_y=0,c={'size':[104,8,117]},
            blocks={(10,1,10):state('oak_stairs',facing=facing,half='bottom',shape='straight',waterlogged=False),
                    (11,1,10):'minecraft:oak_planks'},
            targets={'upper_landing':(11,2,10)},route_records=[],path_field={})

    def test_authored_stair_connects_upper_landing(self):
        self.assertTrue(coarse_connectivity(self.fixture(),(9,1,10))['all_reachable'])

    def test_missing_stair_cannot_pass_as_a_one_block_jump(self):
        model=self.fixture();model.blocks.pop((10,1,10))
        self.assertIn('upper_landing',coarse_connectivity(model,(9,1,10))['failed'])

    def test_reversed_stair_is_not_an_upward_connection(self):
        self.assertIn('upper_landing',coarse_connectivity(self.fixture('west'),(9,1,10))['failed'])

    def test_cut_limit_is_cumulative_from_original_ground(self):
        estate={'size':[98,44,111],'buildings':[],'palette':{}}
        cfg={'height':8,'base_y':0,'building_rect_overrides':{},'floor_groups':[]}
        model=TerraceEstate(estate,cfg,np.full((117,104),4),3)
        model.grade(10,10,2)
        with self.assertRaisesRegex(ValueError,'整地超过原边界'):model.grade(10,10,0)

    def test_odd_rise_landing_does_not_cover_last_returning_stair(self):
        estate=Estate({'size':[50,40,50],'palette':{'floor':'minecraft:oak_planks','wood':'minecraft:spruce_planks'}})
        building={'rect':[5,5,35,35],'levels':2,'step':9,'floor':4}
        estate.interior_stairs(building)
        # x/z are the authored U-stair coordinates after upper-floor inset.
        self.assertIn('oak_stairs',estate.blocks[27,13,23])
        self.assertEqual(estate.blocks[27,13,22],'minecraft:oak_planks')

    def test_pond_rim_must_keep_the_full_path_width(self):
        estate={'size':[98,44,111],'buildings':[],'palette':{'stone':'minecraft:stone_bricks'}}
        cfg={'height':32,'base_y':0,'building_rect_overrides':{},'floor_groups':[],
             'garden':{'pond':{'center':[30,30],'radius':[4,3]},'trees':[]}}
        model=TerraceEstate(estate,cfg,np.full((117,104),10),3)
        model.path_field[28,33]=11
        with self.assertRaisesRegex(ValueError,'池岸占用阶梯完整路幅'):model.gardens_build()


if __name__=='__main__':unittest.main()
