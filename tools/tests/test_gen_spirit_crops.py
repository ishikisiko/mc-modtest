import json
import unittest
from tools import gen_spirit_crops as gen

class SpiritCropsAssetsTest(unittest.TestCase):
    def test_exactly_three_distinct_models_per_herb_with_bounded_geometry(self):
        for herb in gen.HERBS:
            models=[gen.crop_model(herb,age) for age in range(3)]
            self.assertEqual(3,len({json.dumps(m,sort_keys=True) for m in models}))
            for model in models:
                self.assertGreater(len(model['elements']),3)
                for e in model['elements']:
                    for a,b in zip(e['from'],e['to']):
                        self.assertTrue(-16<=a<b<=32)
                    self.assertEqual(6,len(e['faces']))
                    if 'rotation' in e:self.assertIn(e['rotation']['angle'],(-45,-22.5,0,22.5,45))
        self.assertEqual(6,len({gen.texture(h) for h in gen.HERBS}))
    def test_loot_returns_same_herb_without_separate_seed_and_only_maturity_multiplies(self):
        files=gen.outputs()
        for herb in gen.HERBS:
            state=json.loads(files[gen.ROOT/gen.ASSET/f'blockstates/{herb}_crop.json'])
            self.assertEqual({'age=0','age=1','age=2'},set(state['variants']))
            loot=json.loads(files[gen.ROOT/gen.DATA/f'myvillage/loot_table/blocks/{herb}_crop.json'])
            item=loot['pools'][0]['entries'][0]
            self.assertEqual('myvillage:'+herb,item['name'])
            function=item['functions'][0]
            self.assertEqual({'age':'2'},function['conditions'][0]['properties'])
            self.assertGreaterEqual(function['count']['min'],2)
            self.assertLessEqual(function['count']['max'],64)
        self.assertFalse(any('seed' in str(p) for p in files))
    def test_all_owned_outputs_match_checkout(self):
        for path,value in gen.outputs().items():self.assertEqual(value,path.read_bytes(),str(path))
