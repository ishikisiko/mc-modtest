import json
import unittest
from pathlib import Path
from tools import gen_technique_growth as gen


class TechniqueGrowthResourcesTest(unittest.TestCase):
    def test_generated_outputs_match_the_author_inputs(self):
        for path, expected in gen.outputs().items():
            self.assertEqual(expected, (gen.ROOT / path).read_text(), str(path))

    def test_every_sample_and_continuation_resolves_to_the_existing_catalogue(self):
        source = json.loads((gen.ROOT / gen.SOURCE).read_text())
        samples = source['rules']['techniques']
        base = gen.ROOT / 'src/main/resources/data/myvillage/myvillage/technique'
        for key, spec in samples.items():
            actual = json.loads((base / (key.split(':')[1] + '.json')).read_text())
            self.assertEqual(spec['category'], actual['category'])
            if 'next' in spec:
                target = json.loads((base / (spec['next'].split(':')[1] + '.json')).read_text())
                self.assertEqual(key, target['lineage']['previous'])
            self.assertEqual(5, len(spec['thresholds']))
            self.assertEqual(spec['thresholds'], sorted(set(spec['thresholds'])))
        self.assertEqual(4, len(samples))

    def test_survival_recipes_bind_real_manuals_without_preset_comprehension(self):
        source = json.loads((gen.ROOT / gen.SOURCE).read_text())
        outputs = gen.outputs()
        for manual in source['manuals']:
            self.assertLessEqual(len(manual['ingredients']), 9)
            self.assertTrue((gen.ROOT / ('src/main/resources/assets/myvillage/models/item/' + manual['item'] + '.json')).exists())
            path = Path('src/main/resources/data/myvillage/recipe/manual_' + manual['technique'] + '.json')
            recipe = json.loads(outputs[path])
            self.assertEqual({'myvillage:technique': 'myvillage:' + manual['technique']}, recipe['result']['components'])
            self.assertEqual(1, recipe['result']['count'])
            advancement = json.loads(outputs[Path('src/main/resources/data/myvillage/advancement/recipes/manual_' + manual['technique'] + '.json')])
            self.assertEqual(['myvillage:manual_' + manual['technique']], advancement['rewards']['recipes'])
            self.assertIn('has_book', advancement['criteria'])

    def test_growth_session_and_state_keys_are_readable_in_both_existing_locales(self):
        source = json.loads((gen.ROOT / gen.SOURCE).read_text())
        for locale in ('zh_cn', 'en_us'):
            lang = json.loads((gen.ROOT / ('src/main/resources/assets/myvillage/lang/' + locale + '.json')).read_text())
            for key, text in source['messages'].items():
                self.assertEqual(text, lang[key])


if __name__ == '__main__':
    unittest.main()
