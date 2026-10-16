import json
from pathlib import Path
import unittest
from tools import gen_forging_assets as assets
from tools.modal_runner.remote import commands_for

ROOT=Path(__file__).resolve().parents[2]
class ForgingResourcesTest(unittest.TestCase):
    def test_generator_outputs_match_and_pattern_recipes_exist(self):
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
    def test_materials_keep_consumers_but_have_no_acquisition_recipes(self):
        from tools.forging import materials
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        generated=materials.outputs()
        registry=(ROOT/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()
        for m in materials.load():
            rid='myvillage:'+m['id'];self.assertEqual({rid:1},rules['traits'][m['trait']]['costs'])
            self.assertIn('"'+m['id']+'"',registry)
            self.assertIn('output.accept('+m['id'].upper()+'.get())',registry)
        for path in materials.retired_paths():
            self.assertNotIn(path,generated)
            self.assertFalse(path.exists(),str(path))
        ids={'myvillage:'+m['id'] for m in materials.load()}
        for path in (ROOT/'src/main/resources/data/myvillage/recipe').glob('*.json'):
            result=json.loads(path.read_text()).get('result',{})
            if isinstance(result,dict):self.assertNotIn(result.get('id'),ids,str(path))

    def test_materials_do_not_change_base_weapon_or_repair_costs(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        self.assertEqual({'minecraft:iron_ingot':1,'myvillage:low_grade_spirit_stone':1},rules['repair']['costs'])
        for recipe in rules['recipes']:
            self.assertEqual({'minecraft:diamond','minecraft:stick','myvillage:low_grade_spirit_stone'},set(recipe['costs']))
    def test_retired_recipes_fail_check_and_generation_cleans_only_owned_paths(self):
        import tempfile
        from unittest.mock import patch
        from tools.forging import materials
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            retired=materials.retired_paths(root)
            for path in retired:path.parent.mkdir(parents=True,exist_ok=True);path.write_text('{}')
            other=retired[0].with_name('unrelated.json');other.write_text('{}')
            with patch.object(assets,'ROOT',root),patch.object(assets,'outputs',return_value={}):
                with patch('sys.argv',['generator','--check']):self.assertEqual(1,assets.main())
                self.assertTrue(all(p.exists() for p in retired))
                with patch('sys.argv',['generator']):self.assertEqual(0,assets.main())
                with patch('sys.argv',['generator','--check']):self.assertEqual(0,assets.main())
            self.assertTrue(other.exists())
            self.assertFalse(any(p.exists() for p in retired))

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
