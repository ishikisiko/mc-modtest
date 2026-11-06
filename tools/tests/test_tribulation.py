import importlib.util
import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('gen_tribulation',ROOT/'tools/gen_tribulation.py')
gen=importlib.util.module_from_spec(spec);spec.loader.exec_module(gen)
class TribulationResourcesTest(unittest.TestCase):
    def test_generated_resources_are_exact_and_retired_resources_absent(self):
        for path,data in gen.outputs().items():self.assertEqual(json.loads(path.read_text()),data,str(path))
        for path in ['tribulation/golden_core.json','damage_type/tribulation_echo.json']:
            self.assertFalse((ROOT/'src/main/resources/data/myvillage'/path).exists())
    def test_defenses_remain_single_and_no_damage_bypass_tags_are_added(self):
        data=gen.outputs()
        for path in data:
            if 'tags/damage_type' in str(path):
                self.assertFalse(any(b in path.name for b in ['bypasses_armor','bypasses_effects','bypasses_resistance','bypasses_invulnerability','bypasses_shields']))
        for path,value in data.items():
            if 'tags/damage_type' in str(path):self.assertEqual(['myvillage:tribulation_main'],value['values'])
    def test_four_counts_sources_targets_and_timelines_follow_one_author(self):
        doc=json.loads(gen.SOURCE.read_text());self.assertEqual(2,doc['schema_version'])
        counts={t['kind']:t['required_hits'] for t in doc['trials']};self.assertEqual({'heaven':6,'golden_low':6,'golden_middle':9,'golden_high':12},counts)
        outputs=gen.outputs()
        for path,r in outputs.items():
            if path.parent.name!='tribulation':continue
            realm=json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/realm'/(r['source_realm'].split(':')[1]+'.json')).read_text())
            stage=next(s for s in realm['stages'] if s['id']==r['source_stage'])
            self.assertEqual(r['target_stage'],stage['advancement']['target_stage']);self.assertEqual(r['target_realm'],stage['advancement']['target_realm'])
            self.assertEqual(counts[r['kind']],len(r['strikes']));self.assertTrue(all(s['main'] for s in r['strikes']))
            ticks=[s['tick'] for s in r['strikes']];self.assertEqual(sorted(set(ticks)),ticks)
            self.assertTrue(all(0<s['warning_ticks']<s['tick']<r['duration_ticks'] for s in r['strikes']))
    def test_numeric_progression_contains_no_mock_or_duplicate_hit_counts(self):
        p=json.loads((ROOT/'src/main/resources/data/myvillage/stats/progression.json').read_text())
        self.assertNotIn('mock_tribulation',p)
        for name in ['foundation','golden_core']:self.assertNotIn('mock_main_hits',p[name])
        production=ROOT/'src/main/java/com/example/myvillage'
        self.assertFalse((production/'cultivation/MockBreakthroughs.java').exists())
        panel=(production/'client/cultivation/panel/NumericalStatsPage.java').read_text();self.assertNotIn('MOCK',panel)
if __name__=='__main__':unittest.main()
