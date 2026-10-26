import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('gen_tribulation', ROOT / 'tools/gen_tribulation.py')
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)


class TribulationResourcesTest(unittest.TestCase):
    def test_generated_resources_are_exact(self):
        for path, data in gen.outputs().items():
            self.assertEqual(json.loads(path.read_text()), data, str(path))

    def test_damage_keeps_armor_effects_and_shared_shields(self):
        data = gen.outputs()
        for path, value in data.items():
            if 'tags/damage_type' not in str(path):
                continue
            self.assertFalse(any(bad in path.name for bad in ('bypasses_armor', 'bypasses_effects', 'bypasses_resistance', 'bypasses_invulnerability', 'bypasses_shields')))
        cooldown = ROOT / 'src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json'
        self.assertEqual(data[cooldown]['values'], ['myvillage:tribulation_main'])
        main = ROOT / 'src/main/resources/data/myvillage/tags/damage_type/tribulation_unavoidable.json'
        self.assertEqual(data[main]['values'], ['myvillage:tribulation_main'])

    def test_authored_duration_and_realm_eligibility_are_consistent(self):
        r = json.loads(gen.SOURCE.read_text())
        realm = json.loads((ROOT / 'src/main/resources/data/myvillage/myvillage/realm/foundation_establishment.json').read_text())
        stage = next(s for s in realm['stages'] if s['id'] == r['source_stage'])
        self.assertEqual(stage['advancement']['target_stage'], r['target_stage'])
        self.assertEqual(stage['advancement']['target_realm'], r['target_realm'])
        self.assertEqual(sum(s['main'] for s in r['strikes']), 6)
        self.assertTrue(120 <= (r['preparation_ticks'] + r['duration_ticks']) / 20 <= 180)

    def test_echo_warnings_cannot_be_zero_or_same_tick(self):
        r = json.loads(gen.SOURCE.read_text())
        echoes = [s for s in r['strikes'] if not s['main']]
        self.assertGreaterEqual(len(echoes), 2)
        for s in echoes:
            self.assertGreater(s['warning_ticks'], 0)
            self.assertLess(s['warning_ticks'], s['tick'])


if __name__ == '__main__':
    unittest.main()
