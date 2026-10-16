"""Evidence must require mapped-action server events plus actual target health."""
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch
from tools.combat_capture import session
from tools.combat_capture.sword_qi import expectation_met, parse_events, parse_power, trial_plan
from tools.combat_capture.cli import build_parser


class SwordQiEvidenceTest(unittest.TestCase):
    def test_new_margin_fixtures_require_hits_and_have_outside_controls(self):
        plan={t['id']:t for t in trial_plan()}
        for name in ('tap_wide_edge','charged_wide_edge','tap_high_edge','charged_high_edge'):
            self.assertEqual(plan[name]['expect'],'hit')
        for name in ('tap_outside_width','charged_outside_width'):
            self.assertEqual(plan[name]['expect'],'range')
        self.assertTrue(plan['tap_high_edge']['no_gravity'])
        self.assertTrue(plan['charged_high_edge']['no_gravity'])

    def test_power_snapshot_and_client_gate(self):
        self.assertEqual(parse_power('Spiritual power CaptureDev: current=20, cap=40, recovery_ticks=5'),20)
        self.assertIsNone(parse_power('Unknown command'))
        self.assertTrue(expectation_met({'expect':'client_gate'},[],100,100))
        self.assertFalse(expectation_met({'expect':'client_gate'},[{'result':'charging'}],100,100))

    def test_missing_health_or_missing_server_events_never_pass(self):
        trial={'expect':'hit'}
        events=[{'result':'released'},{'result':'hit'}]
        self.assertFalse(expectation_met(trial, events, None, None))
        self.assertFalse(expectation_met(trial, [], 100, 90))
        self.assertFalse(expectation_met(trial, events, 100, 100))
        self.assertTrue(expectation_met(trial, events, 100, 90))

    def test_wall_requires_wall_stop_and_no_damage(self):
        events=[{'result':'released'},{'result':'wave_stop','reason':'WALL'}]
        self.assertTrue(expectation_met({'expect':'wall'},events,100,100))
        self.assertFalse(expectation_met({'expect':'wall'},events,100,90))
        self.assertFalse(expectation_met({'expect':'wall'},[{'result':'wave_stop','reason':'RANGE'}],100,100))

    def test_cancel_requires_started_charge_and_no_release(self):
        events=[{'result':'charging'},{'result':'cancelled'}]
        self.assertTrue(expectation_met({'expect':'cancel'},events,100,100))
        self.assertFalse(expectation_met({'expect':'cancel'},events[1:],100,100))
        self.assertFalse(expectation_met({'expect':'cancel'},events+[{'result':'released'}],100,100))

    def test_rejection_cannot_mask_a_successful_release(self):
        events=[{'result':'rejected','reason':'POWER'}]
        self.assertTrue(expectation_met({'expect':'POWER'},events,100,100))
        self.assertFalse(expectation_met({'expect':'POWER'},events+[{'result':'released'}],100,100))

    def test_log_parser_ignores_unrelated_and_preserves_revision(self):
        self.assertEqual(parse_events(['other log']),[])
        self.assertEqual(parse_events(['[server] SWORD_QI player=CaptureDev t=14 result=hit revision=3 target=2 damage=6.30']),
                         [{'player':'CaptureDev','t':'14','result':'hit','revision':'3','target':'2','damage':'6.30'}])

    def test_plan_and_cli_cover_boundary_scenarios(self):
        ids={t['id'] for t in trial_plan()}
        self.assertEqual(len(ids),len(trial_plan()))
        self.assertTrue({'tap_hit','charged_hit','wall','edge_wall','insufficient','not_learned','switch_cancel','damage_cancel','dodge_cancel'} <= ids)
        args=build_parser().parse_args(['sword-qi','--trials','tap_hit,wall'])
        self.assertEqual(args.trials,'tap_hit,wall')

class CaptureReuseWorldTest(unittest.TestCase):
    def test_reuse_preserves_world_and_default_only_removes_capture_world(self):
        for reuse in (False, True):
            with self.subTest(reuse=reuse), tempfile.TemporaryDirectory() as temp:
                root=Path(temp); server=root/'server'; server.mkdir()
                world=server/session.LEVEL_NAME; world.mkdir(); (world/'sentinel.dat').write_text('saved')
                unrelated=server/'owner_world'; unrelated.mkdir(); (unrelated/'sentinel.dat').write_text('keep')
                with patch.multiple(session, SERVER_DIR=server, PROPS_BACKUP=root/'backup',
                                    PROPS_ABSENT_MARK=root/'absent'), patch.object(session, 'write_state'), \
                     patch.object(session.procs,'free_ports',return_value=(25610,25611)):
                    supervisor=session.Supervisor(session.SessionConfig(session_id='test',reuse_world=reuse))
                    supervisor.write_server_config()
                self.assertEqual((world/'sentinel.dat').exists(),reuse)
                self.assertEqual((unrelated/'sentinel.dat').read_text(),'keep')
                self.assertEqual(supervisor.state['reuse_world'],reuse)
                self.assertTrue(supervisor.state['world_existed'])

    def test_reuse_refuses_missing_world_before_writing_config(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(session,'SERVER_DIR',Path(temp)):
            supervisor=session.Supervisor(session.SessionConfig(session_id='test',reuse_world=True))
            with self.assertRaisesRegex(RuntimeError,'absent'):
                supervisor.write_server_config()
            self.assertFalse((Path(temp)/'server.properties').exists())

    def test_cli_reuse_is_explicit(self):
        parser=build_parser()
        self.assertFalse(parser.parse_args(['session','start']).reuse_world)
        self.assertEqual(parser.parse_args(['session','start']).language,'en_us')
        self.assertEqual(parser.parse_args(['session','start','--language','zh_cn']).language,'zh_cn')
        self.assertTrue(parser.parse_args(['session','start','--reuse-world']).reuse_world)


if __name__ == '__main__': unittest.main()
