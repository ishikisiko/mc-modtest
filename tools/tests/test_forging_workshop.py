import json
from pathlib import Path
import unittest
from tools.forging import workshop
from tools import gen_forging_assets
ROOT=Path(__file__).resolve().parents[2]

class WorkshopResourcesTest(unittest.TestCase):
    def test_physical_stage_models_and_orientation_variants_resolve(self):
        from tools.forging import anvil
        base=ROOT/'src/main/resources';out=anvil.outputs(base)
        states=json.loads(out[base/'assets/myvillage/blockstates/forging_anvil.json'])['variants']
        self.assertEqual(8,len(states))
        for kind in anvil.KINDS:
            for stage in range(6):
                model=json.loads(out[base/f'assets/myvillage/models/block/workshop/{kind}_{stage}.json'])
                self.assertTrue(model['elements'])
                for element in model['elements']:
                    self.assertTrue(all(-16<=v<=32 for v in element['from']+element['to']))
                    self.assertTrue(all(a<b for a,b in zip(element['from'],element['to'])))
                    for face in element['faces'].values():
                        texture=model['textures'][face['texture'][1:]].split(':')[1]
                        self.assertIn(base/f'assets/myvillage/textures/{texture}.png',out)
    def test_each_stage_has_a_distinct_owned_sprite_model_and_localization(self):
        output=workshop.outputs();base=ROOT/'src/main/resources'
        rules=json.loads((base/'data/myvillage/forging/workshop.json').read_text())
        for craft in rules['crafts']:
            seen=set()
            for stage in workshop.STAGES:
                name=f"workpiece_{craft['recipe']}_{stage}"
                model=base/f'assets/myvillage/models/item/{name}.json'
                sprite=base/f'assets/myvillage/textures/item/{name}.png'
                self.assertEqual(f'myvillage:item/{name}',json.loads(output[model])['textures']['layer0'])
                self.assertIn('item.myvillage.'+name,workshop.LANG)
                seen.add(output[sprite])
            self.assertGreaterEqual(len(seen),4)
        for path,data in gen_forging_assets.outputs().items():self.assertEqual(data,path.read_bytes(),str(path))
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
