import json
import unittest
from tools.ningyuan_tree import assets as a

class NingyuanResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.files=a.outputs()
    def read(self,path):return json.loads(self.files[a.RES/path])
    def test_whole_family_has_items_states_models_loot_and_names(self):
        catalogue=self.read('data/myvillage/orchard/catalogue.json')
        for name in catalogue['blocks']:
            state=self.read(f'assets/myvillage/blockstates/{name}.json')
            self.assertTrue(state.get('variants') or state.get('multipart'),name)
            self.read(f'data/myvillage/loot_table/blocks/{name}.json')
        for name in catalogue['items']:self.read(f'assets/myvillage/models/item/{name}.json')
        for name in ('ningyuan_boat','ningyuan_chest_boat','ningyuan_sign','ningyuan_hanging_sign','ningyuan_sapling','ningyuan_fruit_cluster','ningyuan_fertilizer'):
            self.read(f'data/myvillage/recipe/{name}.json')
    def test_model_references_and_all_faces_have_real_textures(self):
        def references(obj):
            if isinstance(obj,dict):
                for k,v in obj.items():
                    if k in ('model','parent') and isinstance(v,str) and v.startswith('myvillage:'):
                        yield ('models/'+v.split(':')[1]+'.json')
                    elif k=='textures':
                        for t in v.values():
                            if t.startswith('myvillage:'):yield ('textures/'+t.split(':')[1]+'.png')
                    else:yield from references(v)
            elif isinstance(obj,list):
                for v in obj:yield from references(v)
        for path,data in self.files.items():
            if 'assets/myvillage' not in str(path) or path.suffix!='.json' or '/lang/' in str(path):continue
            if '/models/' in str(path):
                for texture in json.loads(data).get('textures',{}).values():
                    self.assertNotIn('iron_bars',texture,str(path))
                    self.assertNotIn('oak_',texture,str(path))
            for ref in references(json.loads(data)):
                p=a.ASSET/ref;self.assertTrue(p in self.files or (a.ROOT/p).is_file(),f'{path}: missing {ref}')
    def test_no_recipe_has_identical_ingredients_to_another_orchard_recipe(self):
        signatures={}
        for path,data in self.files.items():
            if '/recipe/' not in str(path):continue
            r=json.loads(data)
            if r['type']=='minecraft:crafting_shapeless':signature=('shapeless',tuple(sorted(json.dumps(v,sort_keys=True) for v in r['ingredients'])))
            elif r['type']=='minecraft:crafting_shaped':signature=('shaped',tuple(r['pattern']),json.dumps(r['key'],sort_keys=True))
            else:continue
            self.assertNotIn(signature,signatures,(path,signatures.get(signature)));signatures[signature]=path
    def test_fruit_break_loot_requires_maturity_and_matches_one_harvest(self):
        r=json.loads((a.ROOT/a.RES/'data/myvillage/orchard/rules.json').read_text())
        pools=self.read('data/myvillage/loot_table/blocks/ningyuan_fruit_cluster.json')['pools']
        self.assertEqual(2,len(pools))
        for pool in pools:
            e=pool['entries'][0];p=e['conditions'][0]['properties']
            self.assertEqual('2',p['age']);self.assertIn(p['enriched'],('true','false'))
            self.assertEqual(r['harvest']['count'],e['functions'][0]['count'])
            self.assertEqual(r['harvest']['high_item' if p['enriched']=='true' else 'low_item'],e['name'])
    def test_selection_and_lantern_collision_come_from_rendered_elements(self):
        geo=self.read('data/myvillage/orchard/geometry.json')
        for age in range(3):
            m=a.fruit_model(age,False);e=m['elements']
            self.assertEqual(geo['fruit_bounds'][age],[min(b['from'][i] for b in e) for i in range(3)]+[max(b['to'][i] for b in e) for i in range(3)])
        for h in (False,True):
            e=a.lantern_model(h)['elements'];self.assertEqual(geo['lantern_shapes'][str(h).lower()],[b['from']+b['to'] for b in e])
            for b in e:
                for lo,hi in zip(b['from'],b['to']):self.assertTrue(0<=lo<hi<=16)
    def test_generated_files_are_current(self):
        for path,data in self.files.items():self.assertEqual(data,(a.ROOT/path).read_bytes(),str(path))

if __name__=='__main__':unittest.main()
