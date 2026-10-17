import copy
import unittest
from tools.fates_purple_evidence import selected_cases, refund_is_once
from tools.modal_runner.remote import commands_for
from tools.combat_capture.session import server_properties, SessionConfig


class PurpleEvidenceTests(unittest.TestCase):
    def test_pvp_is_explicitly_enabled_only_by_opted_in_fixture(self):
        self.assertFalse(SessionConfig(session_id='test').pvp)
        self.assertEqual(server_properties(1,2,'test')['pvp'],'false')
        self.assertEqual(server_properties(1,2,'test',pvp=True)['pvp'],'true')

    def test_cases_are_independent_and_canonical(self):
        self.assertEqual(['pill_aegis', 'lifecycle'], selected_cases('lifecycle,pill_aegis'))
        self.assertEqual(commands_for('fates-purple-runtime', 'lifecycle')[-1][-2:], ['--cases','lifecycle'])
        for value in ('', 'unknown', 'meridians,meridians'):
            with self.assertRaises(ValueError): selected_cases(value)

    def test_refund_requires_same_paid_cast_and_exact_single_recovery(self):
        rows = [{'result':'released','revision':'3','cost':'9'},
                {'result':'hit_refund','revision':'3','paid':'9','refund':'2','recovered':'2','success':'true'},
                {'result':'hit','target':'1'},{'result':'hit','target':'2'}]
        self.assertTrue(refund_is_once(rows,.25))
        self.assertFalse(refund_is_once(rows+[rows[1]],.25))
        for key,value in [('revision','4'),('paid','12'),('refund','3'),('recovered','1'),('success','false')]:
            bad=copy.deepcopy(rows);bad[1][key]=value
            self.assertFalse(refund_is_once(bad,.25),key)
        self.assertFalse(refund_is_once(rows[:1],.25))

    def test_runtime_only_builds_then_selected_game_cases(self):
        commands=commands_for('fates-purple-runtime','meridians')
        self.assertEqual(commands[1][:2],['./gradlew','jar'])
        self.assertIn('generateAllStructures',commands[1])
        self.assertFalse(any('release_gate.py' in part or 'run_chunky' in part for cmd in commands for part in cmd))
        build=commands_for('fates-purple-build')
        self.assertIn('com.example.myvillage.cultivation.fate.*',build[1])
        self.assertNotIn('tools/fates_purple_evidence.py',str(build))
