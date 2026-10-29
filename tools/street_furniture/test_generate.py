import copy
import json
import math
import unittest

from .generate import INPUT,read,parts,outputs,validate


class FurnitureResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.catalog=read(INPUT)

    def test_part_cuts_preserve_thin_geometry_and_exact_volume(self):
        for family in self.catalog['families']:
            split=parts(family)
            self.assertEqual(math.prod(family['size']),len(split))
            for original in family['elements']:
                volume=math.prod(b-a for a,b in zip(original['from'],original['to']))
                pieces=[p for part in split for p in part['elements'] if p['name']==original['name']]
                self.assertAlmostEqual(volume,sum(math.prod(b-a for a,b in zip(p['from'],p['to'])) for p in pieces))
                for p in pieces:
                    self.assertTrue(all(0<=a<b<=16 for a,b in zip(p['from'],p['to'])))

    def test_cloth_uv_is_one_canvas_across_all_four_cells(self):
        rack=next(f for f in self.catalog['families'] if f['id']=='drying_rack')
        pieces=[e for p in parts(rack) for e in p['elements'] if e['name']=='薄布面']
        self.assertEqual(4,len(pieces))
        for side in ('north','south'):
            uvs=[e['faces'][side]['uv'] for e in pieces]
            self.assertEqual(4,len({tuple(uv) for uv in uvs}))
            self.assertAlmostEqual(256,sum((u1-u0)*(v1-v0) for u0,v0,u1,v1 in uvs),places=4)
        self.assertTrue(all(e['to'][2]-e['from'][2]<.3 for e in pieces))

    def test_only_lamp_head_emits_and_cloth_is_not_a_solid_wall(self):
        lamp,rack=self.catalog['families']
        self.assertEqual([0,14],[p['light'] for p in parts(lamp)])
        self.assertEqual([0,0,0,0],[p['light'] for p in parts(rack)])
        for p in parts(rack):
            for box in p['collision']:
                self.assertLess(math.prod(box[i+3]-box[i] for i in range(3)),16**3)
            self.assertGreater(len(p['outline']),len(p['collision']))

    def test_every_facing_part_and_water_state_has_a_model_and_only_root_drops(self):
        files=outputs(self.catalog)
        for family in self.catalog['families']:
            ident=family['id'];count=math.prod(family['size'])
            states=json.loads(files[f'assets/myvillage/blockstates/{ident}.json'])['variants']
            self.assertEqual(count*8,len(states))
            for state,ref in states.items():
                path='assets/myvillage/models/'+ref['model'].split(':',1)[1]+'.json'
                self.assertIn(path,files)
            pools=json.loads(files[f'data/myvillage/loot_table/blocks/{ident}.json'])['pools']
            self.assertEqual(1,len(pools))
            self.assertIn({'condition':'minecraft:block_state_property','block':'myvillage:'+ident,'properties':{'part':'0'}},pools[0]['conditions'])
            self.assertEqual('myvillage:'+ident,json.loads(files[f'data/myvillage/recipe/{ident}.json'])['result']['id'])

    def test_out_of_footprint_geometry_and_missing_family_are_rejected(self):
        invalid=copy.deepcopy(self.catalog);invalid['families'][0]['elements'][0]['to'][0]=17
        with self.assertRaises(ValueError):validate(invalid)
        invalid=copy.deepcopy(self.catalog);invalid['families'].pop()
        with self.assertRaises(ValueError):validate(invalid)


if __name__=='__main__':unittest.main()
