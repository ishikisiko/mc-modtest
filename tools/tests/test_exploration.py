import json
from pathlib import Path
import tempfile
import shutil
import unittest
from tools.validate_exploration import ROOT, validate


class ExplorationResourceTests(unittest.TestCase):
    def test_bundled_resource_links(self):
        self.assertEqual([], validate())

    def test_player_guides_follow_the_runtime_configuration(self):
        from tools.gen_exploration_guide import outputs
        for path, expected in outputs().items():
            self.assertEqual(expected, path.read_text(), str(path))

    def test_missing_tip_translation_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copytree(ROOT / 'src/main/resources', root / 'src/main/resources',
                            ignore=shutil.ignore_patterns('structure', 'textures', 'models', 'blockstates'))
            path = root / 'src/main/resources/assets/myvillage/lang/en_us.json'
            data = json.loads(path.read_text())
            del data['exploration.myvillage.tip.fire']
            path.write_text(json.dumps(data))
            self.assertIn('missing translation exploration.myvillage.tip.fire', validate(root))

    def test_end_city_rewards_and_optional_branches_remain_separate(self):
        data = json.loads((ROOT / 'src/main/resources/data/myvillage/exploration/rules.json').read_text())
        self.assertEqual('endgame', data['loot_tables']['minecraft:chests/end_city_treasure'])
        self.assertEqual(['minecraft:chests/end_city_treasure'],
                         [table for table, pool in data['loot_tables'].items() if pool == 'endgame'])
        self.assertNotIn('endgame', [tier['pool'] for tier in data['reward_tiers']])
        self.assertFalse(data['portal_gates']['enabled'])
        self.assertEqual({'elder_guardian', 'wither', 'warden'}, {r['id'] for r in data['side_routes']})
        self.assertFalse(any('warden' in flag or 'wither' in flag or 'elder' in flag
                             for step in data['milestones'] for flag in step['flags']))
        self.assertEqual(['dragon', 'islands', 'end_city', 'elytra'],
                         [step['id'] for step in data['milestones'][-4:]])
        self.assertTrue(all(not location['regional'] for location in data['locations']
                            if location['dimension'] != 'minecraft:overworld'))

    def test_no_repeatable_trial_or_monster_rewards(self):
        data = json.loads((ROOT / 'src/main/resources/data/myvillage/exploration/rules.json').read_text())
        self.assertIn('minecraft:chests/trial_chambers/reward', data['loot_tables'])
        self.assertIn('minecraft:chests/trial_chambers/reward_ominous', data['loot_tables'])
        self.assertFalse(any('spawners' in s or '/entities/' in s or s.endswith('_common') for s in data['loot_tables']))


if __name__ == '__main__':
    unittest.main()
