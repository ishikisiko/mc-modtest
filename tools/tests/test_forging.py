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
