import json
from pathlib import Path
import unittest
from tools import gen_forging_assets as assets
from tools.modal_runner.remote import commands_for

ROOT=Path(__file__).resolve().parents[2]
class ForgingResourcesTest(unittest.TestCase):
    def test_generator_outputs_match_and_survival_routes_exist(self):
        generated=assets.outputs()
        for path,data in generated.items():self.assertEqual(data,path.read_bytes(),str(path))
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        registered=(ROOT/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()
        for recipe in rules['recipes']:
            self.assertIn('"'+recipe['output'].split(':')[1]+'"',registered)
            self.assertIn('"'+recipe['pattern'].split(':')[1]+'"',registered)
            path=ROOT/'src/main/resources/data/myvillage/recipe'/f"{recipe['pattern'].split(':')[1]}.json"
            self.assertEqual(recipe['pattern'],json.loads(path.read_text())['result']['id'])
            for trait in recipe['traits']:
                self.assertLessEqual(len(set(recipe['costs'])|set(rules['traits'][trait]['costs'])),4)
        self.assertNotIn('channeling',next(r for r in rules['recipes'] if r['id']=='lingxiao_spear')['traits'])
    def test_localized_keys_and_placeholders_match(self):
        for key,(en,zh) in assets.LANG.items():
            self.assertEqual(en.count('%s'),zh.count('%s'),key)
            for lang,text in [('en_us',en),('zh_cn',zh)]:
                data=json.loads((ROOT/f'src/main/resources/assets/myvillage/lang/{lang}.json').read_text())
                self.assertEqual(text,data[key])
    def test_focused_runtime_builds_then_runs_only_forging(self):
        commands=commands_for('forging-runtime')
        self.assertEqual(['python3','tools/forging_evidence.py'],commands[-1])
        self.assertIn('com.example.myvillage.forging.*',commands[-2])
        self.assertFalse(any('release_gate.py' in ' '.join(c) for c in commands))
        lifecycle=commands_for('forging-lifecycle')
        self.assertEqual(['python3','tools/forging_evidence.py','--lifecycle-only'],lifecycle[-1])
        self.assertNotIn('test',lifecycle[0])

class DingGeometryTest(unittest.TestCase):
    def test_open_hearth_and_three_feet_are_structural_and_lit_changes_no_geometry(self):
        from tools.forging import ding
        cold,shape=ding.geometry(False);hot,hot_shape=ding.geometry(True)
        self.assertEqual(shape,hot_shape)
        self.assertEqual([(e['name'],e['from'],e['to']) for e in cold],[(e['name'],e['from'],e['to']) for e in hot])
        self.assertEqual(3,len([e for e in cold if e['name'].endswith('_foot')]))
        self.assertEqual(2,len([e for e in cold if e['name'].endswith('_ear_top')]))
        self.assertFalse(any(e['from'][0]<8<e['to'][0] and e['from'][2]<8<e['to'][2] and e['to'][1]>10.1 for e in cold))
        for e in cold:
            self.assertTrue(all(a<b for a,b in zip(e['from'],e['to'])),e['name'])
            self.assertGreaterEqual(min(e['from']),0)
            self.assertLessEqual(e['to'][0],16);self.assertLessEqual(e['to'][2],16)
        self.assertLessEqual(len(cold),160)
    def test_generated_collision_and_states_have_one_geometry_owner(self):
        from tools.forging import ding
        base=ROOT/'src/main/resources';generated=ding.outputs()
        data=json.loads(generated[base/'data/myvillage/forging/ding.json'])
        self.assertEqual(ding.geometry()[1],data['collision'])
        states=json.loads(generated[base/'assets/myvillage/blockstates/forging_table.json'])['variants']
        self.assertEqual(8,len(states))
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]:
            for lit in ['false','true']:
                self.assertEqual(angle,states[f'facing={direction},lit={lit}']['y'])
        for e in ding.geometry(True)[0]:
            if e['name'].startswith(('ember_','fire_window_')):
                self.assertTrue(all(f['neoforge_data']['block_light']==15 for f in e['faces'].values()))

class MaterialResourcesTest(unittest.TestCase):
    def test_each_material_has_a_real_consumer_and_exact_acquisition_recipe(self):
        from collections import Counter
        from tools.forging import materials
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        generated=materials.outputs()
        registry=(ROOT/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()
        for m in materials.load():
            rid='myvillage:'+m['id'];self.assertEqual({rid:1},rules['traits'][m['trait']]['costs'])
            recipe=json.loads(generated[ROOT/f'src/main/resources/data/myvillage/recipe/{m["id"]}.json'])
            self.assertEqual(m['ingredients'],Counter(i['item'] for i in recipe['ingredients']))
            self.assertEqual({'id':rid,'count':m['output_count']},recipe['result'])
            self.assertIn('"'+m['id']+'"',registry)
            self.assertIn('output.accept('+m['id'].upper()+'.get())',registry)
            unlock=json.loads(generated[ROOT/f'src/main/resources/data/myvillage/advancement/recipes/misc/{m["id"]}.json'])
            self.assertEqual([rid],unlock['rewards']['recipes'])
            self.assertIn('myvillage:low_grade_spirit_stone',m['ingredients'])
    def test_materials_do_not_change_base_weapon_or_repair_costs(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        self.assertEqual({'minecraft:iron_ingot':1,'myvillage:low_grade_spirit_stone':1},rules['repair']['costs'])
        for recipe in rules['recipes']:
            self.assertEqual({'minecraft:diamond','minecraft:stick','myvillage:low_grade_spirit_stone'},set(recipe['costs']))
    def test_invalid_counts_and_overfull_crafting_grid_fail_before_generation(self):
        import tempfile
        from tools.forging import materials
        original=json.loads((ROOT/'tools/forging/materials.json').read_text())
        for key,n in [('minecraft:iron_ingot',0),('minecraft:iron_ingot',10),('minecraft:iron_ingot',1.5)]:
            data=json.loads(json.dumps(original));data['materials'][0]['ingredients'][key]=n
            with tempfile.TemporaryDirectory() as temp:
                path=Path(temp)/'materials.json';path.write_text(json.dumps(data))
                with self.assertRaises(ValueError):materials.load(path)
    def test_icons_are_distinct_transparent_sprites_with_visible_silhouettes(self):
        from tools.forging import materials
        from tools.gen_alchemy_assets import png
        seen=set()
        for m in materials.load():
            rows=materials.icon(m['id']);self.assertEqual((32,32),(len(rows),len(rows[0])))
            opaque=sum(p[3]>0 for row in rows for p in row)
            self.assertGreater(opaque,150);self.assertLess(opaque,650)
            self.assertEqual(0,rows[0][0][3]);seen.add(png(rows))
        self.assertEqual(3,len(seen))
