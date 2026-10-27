import json
from pathlib import Path
import unittest
from tools.forging import workshop
from tools import gen_forging_assets
ROOT=Path(__file__).resolve().parents[2]

class WorkshopResourcesTest(unittest.TestCase):
    def test_trough_states_resolve_and_only_filled_model_has_water(self):
        from tools.forging import trough
        base=ROOT/'src/main/resources';out=workshop.outputs()
        states=json.loads(out[base/'assets/myvillage/blockstates/quenching_trough.json'])['variants']
        self.assertEqual(8,len(states))
        for state,variant in states.items():
            path=base/f"assets/myvillage/models/{variant['model'].split(':')[1]}.json"
            model=json.loads(out[path])
            self.assertEqual('filled=true' in state,any(e['name']=='水面' for e in model['elements']))
            for element in model['elements']:
                self.assertTrue(all(a<b for a,b in zip(element['from'],element['to'])))
                for face in element['faces'].values():
                    self.assertIn(base/f"assets/myvillage/textures/{model['textures'][face['texture'][1:]].split(':')[1]}.png",out)
        self.assertIn(base/'data/myvillage/recipe/quenching_trough.json',out)
        self.assertIn(base/'data/myvillage/loot_table/blocks/quenching_trough.json',out)
    def test_physical_stage_models_and_orientation_variants_resolve(self):
        from tools.forging import anvil
        base=ROOT/'src/main/resources';out=workshop.outputs()
        states=json.loads(out[base/'assets/myvillage/blockstates/forging_anvil.json'])['variants']
        self.assertEqual(8,len(states))
        for kind in anvil.KINDS:
            for stage in range(6):
                model=json.loads(out[base/f'assets/myvillage/models/block/workshop/{kind}_{stage}.json'])
                from tools.equipment.mesh import read_obj
                ref=model['model'].split(':')[1]
                faces=read_obj(out[base/'assets/myvillage'/ref],out[base/'assets/myvillage'/ref.replace('.obj','.mtl')])
                self.assertTrue(faces)
                for face in faces:
                    self.assertTrue(all(-16<=v<=32 for point in face['points'] for v in point))
                    texture=base/f"assets/myvillage/textures/{face['texture'].split(':')[1]}.png"
                    self.assertTrue(texture in out or texture.is_file(),str(texture))
    def test_each_stage_has_a_distinct_owned_sprite_model_and_localization(self):
        output=workshop.outputs();base=ROOT/'src/main/resources'
        rules=json.loads((base/'data/myvillage/forging/workshop.json').read_text())
        for craft in rules['crafts']:
            seen=set()
            for stage in workshop.STAGES:
                name=f"workpiece_{craft['recipe']}_{stage}"
                model=base/f'assets/myvillage/models/item/{name}.json'
                from tools.equipment.mesh import read_obj
                descriptor=json.loads(output[model]);self.assertEqual('neoforge:obj',descriptor['loader'])
                ref=descriptor['model'].split(':')[1]
                faces=read_obj(output[base/'assets/myvillage'/ref],output[base/'assets/myvillage'/ref.replace('.obj','.mtl')])
                self.assertIn('item.myvillage.'+name,json.loads(output[base/'assets/myvillage/lang/zh_cn.json']))
                seen.add(json.dumps(faces,sort_keys=True))
            self.assertGreaterEqual(len(seen),4)
        for path,data in gen_forging_assets.outputs().items():self.assertEqual(data,path.read_bytes(),str(path))
    def test_armor_patterns_cost_partition_and_non_tool_geometry(self):
        from tools.forging import anvil
        base=ROOT/'src/main/resources';out=workshop.outputs()
        recipes=json.loads((base/'data/myvillage/forging/rules.json').read_text())['recipes']
        seen=set()
        for slot in ('helmet','chestplate','leggings','boots'):
            recipe=next(r for r in recipes if r['id']=='xuantie_'+slot)
            self.assertEqual(['tough'],recipe['traits'])
            self.assertGreater(recipe['costs']['#myvillage:forging_materials/xuantie'],0)
            pattern=json.loads(out[base/f'data/myvillage/recipe/pattern_xuantie_{slot}.json'])
            signature=tuple(pattern['pattern']);self.assertNotIn(signature,seen);seen.add(signature)
            self.assertEqual(1,''.join(signature).count('B'))
            for stage in range(1,6):
                from tools.forging.models import Catalogue
                names=[f['part'] for f in Catalogue().stage('xuantie_'+slot,stage)]
                self.assertNotIn('装柄',names);self.assertNotIn('锄眼',names)
        self.assertEqual(4,len(seen))

    def test_strike_variants_and_each_operation_have_real_vanilla_sound_references(self):
        path=ROOT/'src/main/resources/assets/myvillage/sounds.json';sounds=json.loads(workshop.outputs()[path])
        self.assertEqual(3,len(sounds['forging.strike']['sounds']))
        for name in ('strike','heat','quench','temper','grind','assemble','infuse','repair'):
            event=sounds['forging.'+name]
            self.assertIn('subtitle',event)
            self.assertTrue(all(s['name'].startswith('minecraft:') and s['type']=='event' for s in event['sounds']))
    def test_workbench_is_available_without_spirit_materials_and_all_tools_have_patterns(self):
        out=workshop.outputs();base=ROOT/'src/main/resources'
        recipe=json.loads(out[base/'data/myvillage/recipe/forging_anvil.json'])
        self.assertFalse(any('spirit' in str(value) for value in recipe['key'].values()))
        for kind in ['pickaxe','axe','shovel','hoe']:
            self.assertIn(base/f'assets/myvillage/models/item/forged_{kind}.json',out)
            self.assertIn(base/f'data/myvillage/recipe/pattern_forged_{kind}.json',out)

if __name__=='__main__':unittest.main()
