"""森林候选须容纳完整树冠；作者指定的越界树必须明确拒绝。"""
import unittest
from tools.sect_blueprints.generate import SectModel, Construction

class TreeBoundsTest(unittest.TestCase):
    def model(self,root=(3,3),height=10,authored=None):
        model=SectModel.__new__(SectModel);model.width=model.depth=7;model.height=height
        model.spec={'id':'yuanxian:valley_retreat','trees':authored or [],'landscape':{
            'forests':[{'bounds':[root[0],root[1],root[0]+1,root[1]+1],'spacing':1,'density':1,'jitter':1,'radius':2,'height':6,'height_variation':1}],
            'path_tree_clearance':0,'soil_radius':0},'runtime':{'avatar_areas':[]}}
        model.materials={'rock':'minecraft:stone','surface':'minecraft:grass_block','soil':'minecraft:podzol','tree_log':'minecraft:oak_log','tree_leaves':'minecraft:oak_leaves'}
        model.influence=[255]*49;model.surface_states=['minecraft:grass_block']*49;model.surface_columns={};model.buildings=[]
        model.ground=lambda x,z:0;model.construction=Construction([7,height,7]);return model

    def test_rejects_forest_candidates_at_all_four_edges_before_writing(self):
        for root in [(0,3),(6,3),(3,0),(3,6)]:
            with self.subTest(root=root):
                model=self.model(root);model.plant_trees();self.assertEqual([],model.trees);self.assertEqual(0,model.construction.operations)

    def test_valid_tree_keeps_the_full_canopy_and_strict_construction_bounds(self):
        model=self.model();model.plant_trees();self.assertEqual(1,len(model.trees));self.assertGreater(model.construction.operations,1)
        leaves=[op for ops in model.construction.by_tile.values() for op in ops if op.state=='minecraft:oak_leaves']
        self.assertTrue(leaves);self.assertEqual((1,5),(min(op.low[0] for op in leaves),max(op.high[0] for op in leaves)))
        for op in leaves:
            for low,high,limit in zip(op.low,op.high,[7,10,7]):self.assertTrue(0<=low<=high<limit)

    def test_tree_top_must_fit_the_vertical_envelope(self):
        model=self.model(height=7);model.plant_trees();self.assertEqual([],model.trees);self.assertEqual(0,model.construction.operations)

    def test_authored_out_of_bounds_tree_is_rejected_without_partial_writes(self):
        model=self.model(authored=[{'position':[6,1,3],'height':6,'radius':2}]);model.spec['landscape']['forests']=[]
        with self.assertRaisesRegex(ValueError,'树冠超出蓝图'):model.plant_trees()
        self.assertEqual(0,model.construction.operations)

if __name__=='__main__':unittest.main()
