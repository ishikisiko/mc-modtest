import json
import unittest
from tools.gen_gathering_assets import outputs, ROOT
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
        for kind in ('core','node'):
            bid='spirit_gathering_'+kind
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


if __name__=='__main__':unittest.main()
