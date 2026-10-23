"""离线时钟/网络夹具：部署超时不能重发或冒充最终部署状态。"""
import copy
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tools.qianzhen.game_evidence import nbt
from tools.qianzhen.story_evidence import StoryEvidence
from tools.qianzhen.story_deployment import (
    _DeadlineRcon, deployment_budgets, deployment_command, saved_deployment,
)


class Clock:
    def __init__(self):
        self.value = 0.0

    def now(self):
        return self.value

    def sleep(self, seconds):
        self.value += seconds


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.e = StoryEvidence(self.root/'evidence')
        self.addCleanup(self.e.position_stream.close)
        self.addCleanup(self.e.action_stream.close)
        self.e.s = SimpleNamespace(username='CaptureDev', state={'rcon_port':12345, 'rcon_password':'fixture'})
        self.e.layout = {'id':'qianzhen_chapter_1', 'version':'seeded-version'}
        self.roles = ('guide', 'pharmacist', 'zhou_he', 'warden')
        self.e.chapter = {'roles':self.roles}
        self.state = {'npc':{role:{'personId':i, 'position':{'x':i, 'y':-60, 'z':2}}
                              for i, role in enumerate(self.roles)}}
        self.saved = {'schema':1, 'phase':'COMPLETE', **self.e.layout,
                      'dimension':'minecraft:overworld', 'x':0, 'y':-61, 'z':0}
        self.path = self.root/'world/data/myvillage_qianzhen_scene.dat'
        self.path.parent.mkdir(parents=True)
        self.clock = Clock()
        patches = [patch('time.monotonic', self.clock.now), patch('time.sleep', self.clock.sleep),
                   patch('tools.qianzhen.story_evidence.cs.SERVER_DIR', self.root),
                   patch('tools.qianzhen.story_evidence.cs.LEVEL_NAME', 'world')]
        for item in patches:
            item.start()
            self.addCleanup(item.stop)

    def write_saved(self, **changes):
        nbt.write_gzipped_nbt(nbt._nbt_value_to_tag('', {'data':{**self.saved, **changes}}), str(self.path))

    def status_reply(self, state=None):
        return 'QIANZHEN_STORY_STATUS '+json.dumps(self.state if state is None else state)

    def run_deploy(self, dispatch):
        with patch('tools.qianzhen.story_evidence.deployment_command', side_effect=dispatch) as run:
            state = self.e.deploy_scene()
        self.assertEqual(state, self.state)
        self.assertEqual(sum(call.args[1].startswith('myvillage qianzhen deploy ') for call in run.call_args_list), 1)
        self.assertEqual(self.e.report['deployment']['status'], 'confirmed')
        # Confirmation belongs to setup; this helper cannot complete the phase.
        self.assertEqual(self.e.report['completed'], [])
        self.assertIn('production_deploy', self.e.report['unrun'])
        return run

    def test_thirteen_second_deploy_uses_larger_bounded_command_budget(self):
        def dispatch(_session, command, timeout):
            self.assertEqual(timeout, 30)
            if ' deploy ' in command:
                self.clock.sleep(13.1)
                self.write_saved()
                return '阡镇已部署'
            return self.status_reply()
        self.run_deploy(dispatch)
        diagnostic = self.e.report['deployment']
        self.assertAlmostEqual(diagnostic['elapsed_seconds'], 13.1)
        self.assertEqual(diagnostic['final_saved_state']['phase'], 'COMPLETE')
        self.assertEqual(diagnostic['final_story_state'], self.state)

    def test_command_timeout_then_read_timeout_then_authoritative_success_without_replay(self):
        calls = []
        def dispatch(_session, command, timeout):
            calls.append(command)
            if len(calls) <= 2:
                self.clock.sleep(timeout)
                raise TimeoutError('fixture timeout')
            self.write_saved()
            # Vanilla can attach the previous late deployment reply; require JSON.
            return '阡镇已部署\n'+self.status_reply()
        self.run_deploy(dispatch)
        self.assertEqual(len(calls), 3)
        self.assertTrue(all(c.startswith('myvillage story status ') for c in calls[1:]))
        self.assertEqual([a['result'] for a in self.e.report['deployment']['attempts']],
                         ['timeout', 'timeout', 'reply'])

    def test_hung_deployment_stops_at_total_deadline_and_preserves_unrun(self):
        calls = []
        def dispatch(_session, command, timeout):
            calls.append(timeout)
            self.clock.sleep(timeout)
            raise TimeoutError('fixture timeout')
        with patch('tools.qianzhen.story_evidence.deployment_command', side_effect=dispatch), \
             self.assertRaisesRegex(TimeoutError, 'deadline exhausted'):
            self.e.deploy_scene()
        self.assertEqual(self.clock.value, 90)
        self.assertEqual(calls, [30,30,29.5])
        diagnostic = self.e.report['deployment']
        self.assertEqual(diagnostic['status'], 'fail')
        self.assertIsNone(diagnostic['final_saved_state'])
        self.assertEqual(self.e.report['completed'], [])
        self.assertEqual(len(self.e.report['unrun']), 10)

    def test_success_text_and_roles_cannot_hide_missing_placing_or_bad_saved_state(self):
        for changes in (None, {'phase':'PLACING'}, {'phase':'FAILED'}, {'version':'old-version'},
                        {'x':1}, {'dimension':'minecraft:the_nether'}, {'id':'other'}, {'schema':2}):
            with self.subTest(changes=changes):
                self.path.unlink(missing_ok=True)
                self.clock.value = 0
                if changes is not None:
                    self.write_saved(**changes)
                def dispatch(_session, command, timeout):
                    return '阡镇已部署' if ' deploy ' in command else self.status_reply()
                with patch('tools.qianzhen.story_evidence.deployment_command', side_effect=dispatch), \
                     self.assertRaises((TimeoutError, ValueError)):
                    self.e.deploy_scene()
                self.assertEqual(self.e.report['deployment']['status'], 'fail')
                self.assertEqual(self.e.report['completed'], [])
                if changes == {'phase':'PLACING'}:
                    self.assertEqual(self.e.report['deployment']['final_saved_state']['phase'], 'PLACING')

    def test_complete_saved_state_still_requires_all_live_roles(self):
        self.write_saved()
        state = copy.deepcopy(self.state)
        state['npc']['warden']['position'] = None
        def dispatch(_session, command, timeout):
            return '' if ' deploy ' in command else self.status_reply(state)
        with patch('tools.qianzhen.story_evidence.deployment_command', side_effect=dispatch), \
             self.assertRaises(TimeoutError):
            self.e.deploy_scene()
        self.assertEqual(self.e.report['deployment']['final_saved_state']['phase'], 'COMPLETE')
        self.assertEqual(self.e.report['deployment']['final_story_state'], state)
        self.assertEqual(self.e.report['completed'], [])

    def test_duplicate_roles_fail_after_saved_complete(self):
        self.write_saved()
        self.state['npc']['warden']['personId'] = 0
        with patch('tools.qianzhen.story_evidence.deployment_command',
                   side_effect=['阡镇已部署', self.status_reply()]), \
             self.assertRaisesRegex(ValueError, 'unique identities'):
            self.e.deploy_scene()
        self.assertEqual(self.e.report['deployment']['status'], 'fail')

    def test_setup_propagates_deployment_failure_without_completing_phase(self):
        self.e.layout['bounds'] = {'min':[0,0,0], 'max':[10,10,10]}
        self.e.rc = Mock(return_value='Test passed')
        self.e.fixture_teleport = Mock()
        self.e.poll = lambda getter, predicate, timeout, label: getter()
        self.e.deploy_scene = Mock(side_effect=TimeoutError('deployment not confirmed'))
        with self.assertRaisesRegex(TimeoutError, 'deployment not confirmed'):
            self.e.setup()
        self.e.deploy_scene.assert_called_once()
        self.assertEqual(self.e.report['completed'], [])
        self.assertEqual(len(self.e.report['unrun']), 10)

    def test_rejection_and_invalid_status_fail_instead_of_being_retried(self):
        for replies, error in ((['阡镇部署拒绝：旧版本'], RuntimeError),
                               (['阡镇已部署', '阡镇已部署'], ValueError)):
            with self.subTest(replies=replies), \
                 patch('tools.qianzhen.story_evidence.deployment_command', side_effect=replies) as run, \
                 self.assertRaises(error):
                self.e.deploy_scene()
            self.assertEqual(run.call_count, len(replies))
            self.assertEqual(self.e.report['deployment']['status'], 'fail')

    def test_late_reply_is_not_accepted_after_overall_deadline(self):
        self.write_saved()
        def dispatch(_session, command, timeout):
            if ' deploy ' in command:
                return '阡镇已部署'
            self.clock.sleep(91)
            return self.status_reply()
        with patch('tools.qianzhen.story_evidence.deployment_command', side_effect=dispatch), \
             self.assertRaises(TimeoutError):
            self.e.deploy_scene()
        self.assertEqual(self.e.report['deployment']['status'], 'fail')

    def test_authored_and_seeded_saved_state_have_same_identity_requirements(self):
        for version in ('1-authored-version', '1-seeded-version'):
            self.e.layout['version'] = self.saved['version'] = version
            self.write_saved()
            self.assertEqual(saved_deployment(self.root/'world', self.e.layout, self.e.origin)['version'], version)


class DeploymentTransportTests(unittest.TestCase):
    def test_budget_validation_rejects_unbounded_or_invalid_values(self):
        self.assertEqual(deployment_budgets(90,30), (90,30))
        self.assertEqual(deployment_budgets(180,60), (180,60))
        for total, command in ((0,1),(-1,1),(181,30),(90,61),(10,20),
                                (float('inf'),30),(90,float('nan')),(True,1)):
            with self.subTest(total=total,command=command), self.assertRaises(ValueError):
                deployment_budgets(total,command)

    def test_partial_reads_share_absolute_deadline(self):
        clock = Clock()
        sock = Mock()
        sock.gettimeout.return_value = 10
        def receive(size):
            clock.sleep(6)
            return b'x'
        sock.recv.side_effect = receive
        with patch('time.monotonic',clock.now):
            connection = _DeadlineRcon({'rcon_port':1, 'rcon_password':'fixture'},10)
            connection.sock = sock
            with self.assertRaisesRegex(TimeoutError, 'budget exhausted'):
                connection._recv_exact(3)
        self.assertEqual(sock.recv.call_count, 2)
        self.assertEqual([c.args[0] for c in sock.settimeout.call_args_list], [10,4])

    def test_every_command_uses_fresh_connection_and_closes_auth_timeout(self):
        with patch('tools.qianzhen.story_deployment._DeadlineRcon') as factory:
            first, second = Mock(), Mock()
            factory.side_effect = [first,second]
            first.connect.side_effect = TimeoutError('auth timeout')
            second.cmd.return_value = 'read-only state'
            state = {'rcon_port':1, 'rcon_password':'fixture'}
            with self.assertRaises(TimeoutError):
                deployment_command(state,'deploy',30)
            self.assertEqual(deployment_command(state,'status',20), 'read-only state')
            first.close.assert_called_once()
            second.close.assert_called_once()
            first.cmd.assert_not_called()
            second.cmd.assert_called_once_with('status')


if __name__ == '__main__':
    unittest.main()
