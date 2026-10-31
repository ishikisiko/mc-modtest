import json
import unittest
from tools.lord_manors.native_trees import ROOT,CONFIG,floor_covered,place
from tools.province_capitals.blueprint_plan.export_minecraft import fixture,tree_planting

class NativeTreeTests(unittest.TestCase):
    def test_source_no_longer_contains_sculpted_trees(self):
        cfg=json.loads((ROOT/CONFIG).read_text());roles=set(json.loads((ROOT/'blueprints/lord_manors/config.json').read_text())['native_tree_only_roles'])
        self.assertEqual(34,sum(len(c['trees']) for c in cfg['cities'].values()))
        for city in cfg['cities']:
            scene=json.loads((ROOT/'blueprints/lord_manors/cities'/f'{city}.json').read_text())
            self.assertFalse(any(b[6] in roles for b in scene['boxes']),city)

    def test_planting_soil_requires_complete_support(self):
        left=((0,-16,0),(8,0,16),'stone');right=((8,-16,0),(16,0,16),'stone')
        self.assertFalse(floor_covered([],0,0,0));self.assertFalse(floor_covered([left],0,0,0))
        self.assertTrue(floor_covered([left,right],0,0,0))
        self.assertFalse(floor_covered([((0,-16,0),(16,-1,16),'stone')],0,0,0))

    def test_native_trunk_cannot_replace_architecture(self):
        with self.assertRaises(ValueError):place({(0,0,0):'myvillage:roof'},{'native_tree_cells':{(0,0,0):'minecraft:oak_log[axis=y]'},'native_tree_soil':set()})

    def test_planned_soil_can_replace_the_original_ground(self):
        blocks={(0,0,0):'myvillage:ground'}
        place(blocks,{'native_tree_cells':{(0,0,0):'minecraft:dirt'},'native_tree_soil':{(0,0,0)}})
        self.assertEqual('minecraft:dirt',blocks[0,0,0])

    def test_dark_oak_keeps_its_real_two_by_two_planting_pattern(self):
        item={'position':[10,20,30],'size':[3,7,3]};data=tree_planting(item,'dark_oak','minecraft:dirt')
        self.assertEqual({(10,20,30),(11,20,30),(10,20,31),(11,20,31)},set(map(tuple,data['roots'])))
        self.assertEqual(4,len(data['soil']));self.assertEqual('minecraft:dark_oak_sapling',data['sapling'])

    def test_small_garden_places_real_sapling_and_soil_instead_of_a_model_tree(self):
        class World:
            def __init__(self):self.cells={}
            def set(self,p,state,*args):self.cells[p]=state
            def box(self,*args):raise AssertionError('不能把小院树苗做成原木/树冠模型')
        world=World();item={'id':'tree','kind':'tree','position':[0,4,0],'size':[2,5,2]}
        self.assertEqual(set(),fixture(world,item,{'tree_soil':'minecraft:dirt'},'north','acacia'))
        self.assertEqual({(0,3,0):'minecraft:dirt',(0,4,0):'minecraft:acacia_sapling[stage=0]'},world.cells)

if __name__=='__main__':unittest.main()
