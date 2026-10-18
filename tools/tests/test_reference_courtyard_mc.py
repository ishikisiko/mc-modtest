"""Geometry/resource boundary checks for the actual Minecraft export."""
import json
from pathlib import Path
import tempfile
import unittest

from tools.reference_courtyard import minecraft as mc
from tools.buildgen.nbtread import read_gzipped_nbt, state_string


class CourtyardExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.outputs,cls.runtime,cls.catalog,cls.cells,cls.placements=mc.generate()

    def test_all_local_geometry_matches_rendered_rotations_and_mirrors(self):
        report=mc.validate_outputs(self.outputs,self.catalog,self.cells,self.placements)
        self.assertEqual(report['render_cells_verified'],len(self.cells))
        self.assertLessEqual(report['max_collision_boxes'],64)

    def test_collision_boxes_exactly_cover_visual_microvoxels(self):
        library=json.loads(mc.LIBRARY.read_text())
        by_id={p['id']:p for p in self.catalog}
        for entry in library['pieces']:
            mask=set()
            for a,b,c,d,e,f in by_id[entry['id']]['boxes']:
                for x in range(a//4,d//4):
                    for y in range(b//4,e//4):
                        for z in range(c//4,f//4):
                            self.assertNotIn((x,y,z),mask)
                            mask.add((x,y,z))
            self.assertEqual(mask,{tuple(r[:3]) for r in entry['cells']},entry['id'])

    def test_canonical_is_order_independent_and_invertible(self):
        local={(0,0,0):'plaster',(3,1,0):'timber',(2,3,1):'tile'}
        baseline=mc.canonical(local)[0]
        for mirror in [False,True]:
            for turn in range(4):
                changed=mc.transform(local,turn,mirror)
                key,inverse,m=mc.canonical(changed)
                self.assertEqual(key,baseline)
                self.assertEqual(mc.transform({tuple(r[:3]):r[3] for r in key},inverse,m),changed)

    def test_template_has_real_water_and_waterlogged_collision(self):
        rows=self.runtime['fixture_cells'];lookup={p['id']:p for p in self.catalog}
        water=[r for r in rows if r[3].startswith('minecraft:water')]
        wet=[r for r in rows if 'waterlogged=true' in r[3]]
        self.assertGreater(len(water),0);self.assertGreater(len(wet),0)
        for row in wet:
            identity=row[3].split('[')[0].split(':')[1]
            self.assertFalse(lookup[identity]['full'])
        self.assertLessEqual(max(self.runtime['size']),48)

    def test_library_ids_do_not_depend_on_enumeration(self):
        config=json.loads((mc.HERE/'minecraft.json').read_text())
        for row in json.loads(mc.LIBRARY.read_text())['pieces']:
            self.assertEqual(mc.piece_id(tuple(tuple(c) for c in row['cells']),config),row['id'])

    def test_every_item_has_model_loot_names_and_eight_visual_transforms(self):
        zh=json.loads(self.outputs[mc.ASSETS/'lang/zh_cn.json']);en=json.loads(self.outputs[mc.ASSETS/'lang/en_us.json'])
        for p in self.catalog:
            identity=p['id'];name='block.myvillage.'+identity
            self.assertIn(name,zh);self.assertIn(name,en)
            self.assertIn(mc.ASSETS/f'models/item/{identity}.json',self.outputs)
            loot=json.loads(self.outputs[mc.DATA/f'loot_table/blocks/{identity}.json'])
            entry=loot['pools'][0]['entries'][0]
            self.assertEqual(entry['name'],'myvillage:'+identity)
            self.assertEqual(entry['functions'][0]['properties'],['mirrored'])
            variants=json.loads(self.outputs[mc.ASSETS/f'blockstates/{identity}.json'])['variants']
            self.assertEqual(len(variants),8)


if __name__=='__main__':unittest.main()
