import json
import unittest
from tools import gen_demon_trial as gen

class DemonTrialResourcesTest(unittest.TestCase):
    def test_model_and_blockstates_close_with_no_block_item(self):
        files=gen.outputs()
        states=json.loads(files[gen.ASSETS/'blockstates/demon_trial_core.json'])
        self.assertEqual(len(states['variants']),8)
        for value in states['variants'].values():
            self.assertIn(gen.ASSETS/('models/'+value['model'].split(':')[1]+'.json'),files)
        self.assertIn(gen.ASSETS/'models/item/demon_lure_pearl.json',files)
        self.assertNotIn(gen.ASSETS/'models/item/demon_trial_core.json',files)
        for lit in (False,True):
            model=json.loads(files[gen.ASSETS/f'models/block/demon_trial_core_{str(lit).lower()}.json'])
            for e in model['elements']:
                self.assertTrue(all(0<=a<b<=16 for a,b in zip(e['from'],e['to'])))
    def test_crafting_and_recipe_discovery_match(self):
        files=gen.outputs();recipe=json.loads(files[gen.DATA/'recipe/demon_lure_pearl.json'])
        self.assertEqual(recipe['result'],{'id':'myvillage:demon_lure_pearl','count':1})
        self.assertIn('minecraft:ender_pearl',[v['item'] for v in recipe['key'].values()])
        advancement=json.loads(files[gen.DATA/'advancement/recipes/misc/demon_lure_pearl.json'])
        self.assertEqual(advancement['rewards']['recipes'],['myvillage:demon_lure_pearl'])
    def test_bilingual_keys_and_placeholders_match(self):
        self.assertEqual(gen.MESSAGES['zh_cn'].keys(),gen.MESSAGES['en_us'].keys())
        for k,v in gen.MESSAGES['zh_cn'].items():self.assertEqual(v.count('%s'),gen.MESSAGES['en_us'][k].count('%s'),k)
if __name__=='__main__':unittest.main()
