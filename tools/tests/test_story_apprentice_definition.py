"""Offline authored-story contract. Does not claim Java execution or native play evidence."""
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
OPENING = ROOT / 'src/main/resources/data/myvillage/story/qianzhen_opening.json'
LEGACY = 'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json'


class ApprenticeDefinitionTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(OPENING.read_text())
        self.actions = {a['id']: a for a in self.data['actions']}

    def test_old_accepted_definition_is_byte_preserved(self):
        self.assertEqual((ROOT / LEGACY).read_bytes(), subprocess.check_output(['git', 'show', f'HEAD:{LEGACY}'], cwd=ROOT))

    def test_employer_accepts_explicit_terms_before_early_teaching_and_actual_work(self):
        self.assertEqual('pharmacist', self.actions['accept']['role'])
        self.assertEqual(['new'], self.actions['accept']['stages'])
        cursor = 'new'
        for action_id in ('accept', 'confirm_root', 'confirm_inheritance', 'confirm_breath', 'take_supplies', 'report_work', 'claim_reward'):
            action = self.actions[action_id]
            self.assertIn(cursor, action['stages'])
            cursor = action['next']
        self.assertEqual('complete', cursor)
        self.assertFalse(any(a['kind'] == 'investigate' for a in self.actions.values()))
        self.assertEqual({'verify_root', 'verify_inheritance', 'verify_breath', 'supply_work', 'verify_work'}, {a['effect'] for a in self.actions.values()} - {''})
        player_actions = {a['id'] for a in self.actions.values() if a['kind'] in ('talk', 'claim')}
        self.assertEqual({'accept', 'take_supplies', 'claim_reward'}, player_actions)
        self.assertEqual({'confirm_root', 'confirm_inheritance', 'confirm_breath', 'report_work'},
                         {a['id'] for a in self.actions.values() if a['kind'] == 'fact'})
        self.assertEqual('', next(s for s in self.data['stages'] if s['id'] == 'first_breath')['target'])

    def test_work_and_pay_have_one_bounded_authored_source(self):
        self.assertEqual({'item': 'myvillage:spirit_leaf', 'count': 3, 'radius': 8, 'target': 'apprentice_plot'}, self.data['work'])
        self.assertEqual([{'item': 'minecraft:bread', 'count': 6}, {'item': 'myvillage:low_grade_spirit_stone', 'count': 1}], self.data['rewards'])
        claims = [a for a in self.actions.values() if a['kind'] == 'claim']
        self.assertEqual(1, len(claims))
        self.assertEqual(['report'], claims[0]['stages'])
        self.assertEqual('complete', claims[0]['next'])

    def test_all_new_dialogue_and_marker_text_fit_wire_bounds(self):
        for stage in self.data['stages']:
            self.assertLessEqual(len(stage['objective']), 256)
            self.assertLessEqual(len(stage['hint']), 256)
            for lines in stage['dialogue'].values():
                self.assertLessEqual(len(lines), 6)
                self.assertTrue(all(len(line) <= 256 for line in lines))
        for action in self.actions.values():
            self.assertLessEqual(len(action['text']), 96)
            self.assertLessEqual(len(action['journal']), 256)


if __name__ == '__main__':
    unittest.main()
