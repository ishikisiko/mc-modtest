"""离线反例：单次长 flush、权威保存窗口及真实重进检查链。"""
import copy
from contextlib import contextmanager, redirect_stdout
import io
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tools.qianzhen.game_evidence import nbt
from tools.qianzhen import story_evidence
from tools.qianzhen.story_evidence import StoryEvidence
from tools.qianzhen.story_lifecycle import flush_budget, lifecycle_operation
from tools.tests.test_qianzhen_story_deployment import Clock

ACK = 'Saving the game (this may take a moment!)\nSaved the game\n'
MARKER = '[Server thread/INFO] [net.minecraft.server.MinecraftServer/]: ThreadedAnvilChunkStorage: All dimensions are saved\n'


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.e = StoryEvidence(self.root/'evidence')
        self.addCleanup(self.e.position_stream.close)
        self.addCleanup(self.e.action_stream.close)
        self.output = redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)
        self.clock = Clock()
        for item in (patch('time.monotonic', self.clock.now), patch('time.sleep', self.clock.sleep),
                     patch('tools.qianzhen.story_evidence.cs.SERVER_DIR', self.root),
                     patch('tools.qianzhen.story_evidence.cs.LEVEL_NAME', 'world')):
            item.start(); self.addCleanup(item.stop)
        self.e.sessions = 1
        self.e.s = SimpleNamespace(username='CaptureDev', state={'rcon_port':1, 'rcon_password':'fixture'})
        self.e.layout = {'id':'qianzhen_chapter_1', 'version':'candidate-version'}
        self.e.chapter = {'roles': ['guide', 'pharmacist', 'zhou_he', 'warden']}
        self.e.initial_people = dict(zip(self.e.chapter['roles'], range(4)))
        self.e.positions = {'zhou_he_home': {'pos':[2,-60,0]}}
        self.before = dict(player='owner-uuid', chapter='qianzhen_chapter_1', stage='complete',
            revision=15, accepted=True, completed=True, schema=1, definition_version=1,
            site='qianzhen_chapter_1', dimension='minecraft:overworld', evidence=['spring_core'],
            receipts=['qianzhen_chapter_1_reward'], reward='claimed',
            shared={'returned':True, 'regulated':True}, npc={role:dict(personId=i,
                position={'x':i,'y':-60,'z':0}) for role,i in self.e.initial_people.items()})
        self.after = copy.deepcopy(self.before)
        self.guest = copy.deepcopy(self.before)
        self.guest['player'] = 'guest-uuid'
        self.e.final_guest = {'username':'AlchemyGuest', 'state':copy.deepcopy(self.guest),
                              'inventory':[{'id':'myvillage:low_grade_spirit_stone', 'count':3}]}
        self.inventory = copy.deepcopy(self.e.final_guest['inventory'])
        self.e.inventory = Mock(side_effect=lambda *args: copy.deepcopy(self.inventory))
        self.e.close_screen = Mock()
        self.e.player_pos = Mock(return_value=[0,-60,0])
        self.e.stop = Mock()
        self.e.start = Mock()
        self.e.expect_client_view = Mock()
        self.e.poll = lambda getter, predicate, *args: getter() if predicate(getter()) else self.fail('invalid restored actors')
        self.e.status = lambda label: copy.deepcopy(self.guest if self.e.actor else
                                                   self.after if label.endswith('-after') else self.before)
        self.log = self.root/'logs/latest.log'
        self.log.parent.mkdir(); self.log.write_text('old server log\n')
        self.world = self.root/'world'
        (self.world/'data').mkdir(parents=True)
        (self.world/'playerdata').mkdir()
        for name in ('owner-uuid', 'guest-uuid'):
            (self.world/'playerdata'/f'{name}.dat').write_bytes(b'saved-player-fixture')
        (self.world/'level.dat').write_bytes(b'level-fixture')
        (self.world/'data/myvillage_world_sim.dat').write_bytes(b'ledger-fixture')
        self.scene = dict(schema=1, phase='COMPLETE', **self.e.layout,
                          dimension='minecraft:overworld', x=0,y=-61,z=0)
        self.write_scene()

    def write_scene(self, **changes):
        nbt.write_gzipped_nbt(nbt._nbt_value_to_tag('', {'data':{**self.scene, **changes}}),
                             str(self.world/'data/myvillage_qianzhen_scene.dat'))

    def dispatch(self, _state, command, timeout):
        self.assertEqual(command, 'save-all flush')
        self.assertLessEqual(timeout, self.e.flush_timeout)
        self.clock.sleep(18.4)
        with self.log.open('a') as stream: stream.write(MARKER)
        return ACK

    def assert_unrun(self):
        self.assertNotIn('saved_world', self.e.report['completed'])
        self.assertIn('saved_world', self.e.report['unrun'])

    def test_flush_over_old_ten_seconds_completes_once_in_explicit_budget(self):
        with patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=self.dispatch) as run:
            self.assertEqual(self.e.rc('save-all flush'), ACK)
        run.assert_called_once()
        self.assertNotIn('run', self.e.s.__dict__)
        row = self.e.report['flushes'][0]
        self.assertEqual((row['requests'], row['status'], row['elapsed_seconds']), (1,'confirmed',18.4))
        self.assertEqual(row['saved_scene']['version'], 'candidate-version')
        self.assertIn(MARKER, (self.e.out/row['server_log_artifact']).read_text())
        self.assert_unrun()  # Acknowledging flush alone cannot complete saved_world.

    def test_upper_bound_timeout_is_terminal_no_reconnection_or_second_flush(self):
        self.e.flush_timeout = flush_budget(120)
        def timeout(_state, _command, budget):
            self.clock.sleep(budget)
            raise TimeoutError('fixture server still saving')
        with patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=timeout) as run:
            with self.assertRaises(TimeoutError): self.e.flush_world('upper-bound')
            with self.assertRaisesRegex(RuntimeError, 'Previous flush'): self.e.rc('save-all flush')
        run.assert_called_once()
        self.assertEqual(self.clock.value,120)
        self.assertEqual(self.e.report['flushes'][0]['status'], 'fail')
        self.assert_unrun()

    def test_late_success_after_upper_bound_still_fails(self):
        self.e.flush_timeout = flush_budget(120)
        def late(*args):
            reply = self.dispatch(*args)
            self.clock.sleep(102)
            return reply
        with patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=late) as run:
            with self.assertRaisesRegex(TimeoutError, 'deadline exhausted'): self.e.flush_world('late')
        run.assert_called_once(); self.assert_unrun()

    def test_success_needs_own_full_response_and_fresh_save_log(self):
        for reply, fresh in ((ACK,False), ('Saved the game\n',True),
                             ('Saved the game\n'+ACK,True), ('',True)):
            with self.subTest(reply=reply,fresh=fresh):
                self.e.flush_failed = False
                # Even a valid old completion line is not this operation's log.
                self.log.write_text(MARKER)
                def dispatch(*args):
                    if fresh:
                        with self.log.open('a') as stream: stream.write(MARKER)
                    return reply
                with patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=dispatch) as run:
                    with self.assertRaises((ValueError,TimeoutError)): self.e.flush_world('ambiguous')
                run.assert_called_once(); self.assert_unrun()

    def test_flush_response_log_cannot_hide_wrong_durable_scene(self):
        self.write_scene(version='old-version')
        with patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=self.dispatch):
            with self.assertRaisesRegex(ValueError, 'differs'): self.e.flush_world('wrong-scene')
        self.assert_unrun()

    def test_log_drain_wait_shares_deadline_and_never_sends_again(self):
        def drain(seconds):
            self.clock.sleep(seconds)
            if self.clock.value >= 5:
                with self.log.open('a') as stream: stream.write(MARKER)
        with patch('tools.qianzhen.story_evidence.lifecycle_command',return_value=ACK) as run, \
             patch('time.sleep',side_effect=drain):
            self.e.flush_world('log-drain')
        run.assert_called_once()
        self.assertGreaterEqual(self.e.report['flushes'][0]['elapsed_seconds'],5)
        self.assertLess(self.clock.value,60)
        self.assert_unrun()

    def test_normal_queries_keep_session_default_deploy_cannot_bypass_confirmation(self):
        self.e.s.run = Mock(return_value=['ordinary readback'])
        with patch('tools.qianzhen.story_evidence.lifecycle_command') as long_command:
            self.assertEqual(self.e.rc('list'), 'ordinary readback')
            with self.assertRaisesRegex(AssertionError, 'deploy_scene'):
                self.e.rc('myvillage qianzhen deploy 0 -61 0')
            with self.assertRaises(ValueError): self.e.rc('save-all')
        long_command.assert_not_called(); self.e.s.run.assert_called_once_with('list',warn=False)

    def test_invalid_unbounded_flush_budgets_fail_before_output_or_launch(self):
        for value in (0,-1,121,float('inf'),float('nan'),True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                StoryEvidence(self.root/'must-not-exist',flush_timeout=value)
        self.assertFalse((self.root/'must-not-exist').exists())
        self.assertEqual(flush_budget(120),120)
        self.assertEqual(lifecycle_operation('/save-all flush'), 'flush')

    def test_final_saved_world_requires_reentry_and_both_players_then_preserves_archive(self):
        @contextmanager
        def as_guest(guest):
            self.e.actor = guest
            try: yield
            finally: self.e.actor = None
        self.e.as_guest = as_guest
        with patch('tools.qianzhen.story_evidence.SecondClient') as second, \
             patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=self.dispatch) as run:
            second.return_value.username = 'AlchemyGuest'
            self.e.final_saved_world()
        run.assert_called_once()
        self.e.start.assert_called_once_with(reuse=True)
        self.assertEqual(self.e.stop.call_count,2)
        second.assert_called_once_with(self.e, output_name='final-save-second-client',
                                       prepare_game=self.e.prepare_guest_window)
        self.assertIn('saved_world',self.e.report['completed'])
        self.assertEqual(self.e.report['saved_world_verification']['status'],'confirmed')
        archive = self.e.out/'qianzhen_story_world.zip'
        data = archive.read_bytes()
        self.e.package_world()
        self.assertEqual(archive.read_bytes(),data)
        archive.write_bytes(b'tampered')
        with self.assertRaisesRegex(ValueError,'archive changed'): self.e.package_world()

    def test_actual_restart_state_drift_fails_without_completing_saved_world(self):
        variants = [('stage','new'), ('receipts',[]), ('reward','pending'), ('evidence',[]),
                    ('revision',0), ('definition_version',2), ('shared',{'returned':False,'regulated':True}),
                    ('inventory',[]), ('scene','wrong-version'), ('identity',99)]
        for key,value in variants:
            with self.subTest(key=key):
                self.e.flush_failed = False
                self.after = copy.deepcopy(self.before)
                self.inventory = copy.deepcopy(self.e.final_guest['inventory'])
                self.e.start.reset_mock(side_effect=True)
                self.write_scene()
                def restart(*args, **kwargs):
                    if key == 'inventory': self.inventory = value
                    elif key == 'scene': self.write_scene(version=value)
                    elif key == 'identity': self.after['npc']['guide']['personId'] = value
                    else: self.after[key] = value
                self.e.start.side_effect = restart
                with patch('tools.qianzhen.story_evidence.lifecycle_command', side_effect=self.dispatch) as run:
                    with self.assertRaises((AssertionError,ValueError)): self.e.final_saved_world()
                run.assert_called_once()
                self.e.start.assert_called_once_with(reuse=True)
                self.assertEqual(self.e.report['saved_world_verification']['status'],'fail')
                self.assert_unrun()

    def test_guest_reentry_reward_drift_also_keeps_saved_world_unrun(self):
        @contextmanager
        def as_guest(guest):
            self.e.actor = guest
            try: yield
            finally: self.e.actor = None
        self.e.as_guest = as_guest
        self.guest['receipts'] = []
        with patch('tools.qianzhen.story_evidence.SecondClient') as second, \
             patch('tools.qianzhen.story_evidence.lifecycle_command',side_effect=self.dispatch) as run:
            second.return_value.username = 'AlchemyGuest'
            with self.assertRaisesRegex(AssertionError,'final_save_guest_progress_exact'):
                self.e.final_saved_world()
        run.assert_called_once()
        self.assertEqual(self.e.report['saved_world_verification']['status'],'fail')
        self.assert_unrun()

    def test_error_capture_and_cleanup_never_replay_timed_out_flush(self):
        self.e.run = lambda *args: self.e.rc('save-all flush')
        self.e.input_keys = Mock(); self.e.capture = Mock(); self.e.package_world = Mock()
        with patch.object(story_evidence,'StoryEvidence',return_value=self.e), \
             patch('sys.argv',['story_evidence','--out',str(self.e.out)]), \
             patch('tools.qianzhen.story_evidence.lifecycle_command',side_effect=TimeoutError('flush timeout')) as run:
            with self.assertRaises(TimeoutError): story_evidence.main()
        run.assert_called_once(); self.e.stop.assert_called_once(); self.assert_unrun()
        self.assertEqual(self.e.report['status'],'fail')


if __name__ == '__main__':
    unittest.main()
