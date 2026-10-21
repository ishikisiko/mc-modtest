import json
import unittest
from tools.gen_gathering_assets import outputs, ROOT, IDS
from tools.gathering_evidence import energy
from tools.modal_runner.remote import commands_for, select_gathering_cases


class GatheringTests(unittest.TestCase):
    def test_preset_selection_is_bounded_and_keeps_packaging_dependencies(self):
        self.assertEqual(['mapped','restart'],select_gathering_cases('restart,mapped'))
        self.assertEqual(['--cases','mapped,restart'],commands_for('gathering-runtime','restart,mapped')[-1][-2:])
        self.assertNotIn('generateWorldPlanAssets',commands_for('gathering-build')[-1])
        for value in ('','missing','restart,restart'):
            with self.assertRaises(ValueError):select_gathering_cases(value)

    def test_energy_evidence_is_strict_and_accounts_for_remaining_stones(self):
        self.assertEqual(59,energy({'core':True,'stones':2,'charges':19}))
        for value in ({'core':False,'stones':1,'charges':0},{'core':True,'stones':0,'charges':-1},{}):
            with self.assertRaises(ValueError):energy(value)

    def test_generated_resource_references_close_and_recipes_have_real_materials(self):
        generated=outputs()
        for bid in (prefix+'_'+kind for prefix in IDS.values() for kind in ('core','node')):
            block=json.loads(generated[ROOT/f'src/main/resources/assets/myvillage/models/block/{bid}.json'])
            for texture in block['textures'].values():
                relative=texture.removeprefix('myvillage:')
                self.assertIn(ROOT/f'src/main/resources/assets/myvillage/textures/{relative}.png',generated)
            recipe=json.loads(generated[ROOT/f'src/main/resources/data/myvillage/recipe/{bid}.json'])
            used=set(''.join(recipe['pattern']))-{' '}
            self.assertEqual(used,set(recipe['key']))
            self.assertEqual({'id':'myvillage:'+bid,'count':1},recipe['result'])
            loot=json.loads(generated[ROOT/f'src/main/resources/data/myvillage/loot_table/blocks/{bid}.json'])
            self.assertEqual('myvillage:'+bid,loot['pools'][0]['entries'][0]['name'])
        for path,content in generated.items():self.assertEqual(path.read_bytes(),content,str(path))

    def test_tiers_have_distinct_geometry_and_runtime_appearance_from_same_source(self):
        generated=outputs()
        elements=[]
        for prefix in IDS.values():
            model=json.loads(generated[ROOT/f'src/main/resources/assets/myvillage/models/block/{prefix}_node.json'])
            elements.append(json.dumps(model['elements'],sort_keys=True))
            for element in model['elements']:
                self.assertTrue(all(0<=a<b<=16 for a,b in zip(element['from'],element['to'])))
                if 'rotation' in element:self.assertIn(element['rotation']['angle'],(-45,-22.5,0,22.5,45))
        self.assertEqual(3,len(set(elements)))
        authored=json.loads((ROOT/'tools/gathering/visual.json').read_text())
        appearance=json.loads(generated[ROOT/'src/main/resources/data/myvillage/gathering/appearance.json'])
        for tier,row in {'low':authored,**authored['tiers']}.items():
            self.assertEqual({**row['visual'],'collision_boxes':row['collision_boxes']},appearance[tier])
        self.assertEqual([8,32,48],[appearance[k]['segments'] for k in IDS])

    def test_declared_tier_sizes_and_non_currency_fuel_lifetimes(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/gathering/rules.json').read_text())
        self.assertEqual([7,9,11],[2*rules['tiers'][k]['radius']+1 for k in IDS])
        self.assertEqual([3,5,7],[2*rules['tiers'][k]['inner_radius']+1 for k in IDS])
        self.assertEqual({'low_grade':20,'medium_grade':200,'high_grade':2000},rules['fuel_charges'])


if __name__=='__main__':unittest.main()
