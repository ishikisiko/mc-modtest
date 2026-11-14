"""生态模板的施工范围、成员和临时奖励保持完整，并验证作者输出一致。"""
import json,unittest
from pathlib import Path
from tools.wilderness_habitats.generate import generate,ROOT

class HabitatAssetsTest(unittest.TestCase):
    def test_export_matches_authority(self):
        self.assertEqual(generate(),json.loads((ROOT/'src/main/resources/data/myvillage/wilderness/habitats.json').read_text()))
    def test_templates_cover_spawn_space_have_readable_landmarks_and_stay_in_bounds(self):
        cfg=generate();self.assertEqual('donghuang',cfg['region']);self.assertEqual(1,cfg['refresh_months'])
        self.assertEqual({'wolf_den','lizard_shelf','spore_grove','bandit_camp'},set(cfg['templates']))
        for name,template in cfg['templates'].items():
            with self.subTest(name=name):
                cells={(b['x'],b['y'],b['z']):b['block'] for b in template}
                self.assertEqual(len(cells),len(template));self.assertGreater(sum(b.endswith('air')for b in cells.values()),150)
                for x,y,z in cells:self.assertLessEqual(max(abs(x),abs(z)),cfg['site_radius']);self.assertLessEqual(y,5);self.assertGreaterEqual(y,-1)
                self.assertTrue(any(v!='minecraft:air' and y>=0 for (x,y,z),v in cells.items()))
        camp=[b['block']for b in cfg['templates']['bandit_camp']]
        self.assertTrue(any('campfire' in b for b in camp));self.assertTrue(any('barrel' in b for b in camp))
    def test_peer_levels_use_identical_encounter_ranges(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/wilderness/rules.json').read_text())['regions']
        for group in json.loads((ROOT/'blueprints/worldplan/rules_v11.json').read_text())['region_progression']['levels']:
            self.assertTrue(all(rules[region]==rules[group[0]]for region in group))
