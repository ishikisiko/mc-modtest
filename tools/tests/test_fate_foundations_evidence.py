"""离线解析、拒绝路径与调度测试；不会启动游戏、Gradle 或 Colab。"""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.fate_foundations_evidence import (
    ABILITY, CASES, FIRST_PASS, FoundationEvidence, allow_rcon, assert_colab,
    close, initial_report, lifesteal_expected, parse_status, select_cases,
)


def state(**changes):
    value = dict(uuid='角色甲', now=100, health=70, max_health=100, rooted=False, shield=0,
                 qi=100, wood_root=dict(eligible=True, cost=10, cooldown_remaining=0))
    value.update(changes)
    return value


class ParserTests(unittest.TestCase):
    def test_default_is_first_pass_and_alias_is_canonical(self):
        self.assertEqual(list(FIRST_PASS), select_cases())
        self.assertEqual(list(CASES), select_cases('all'))
        self.assertEqual(['selection_binding', 'wood_root'], select_cases('wood_root,binding'))
        self.assertEqual(['two_players'], select_cases(['two_players']))

    def test_bad_duplicate_or_partial_selection_rejected(self):
        for bad in ('', 'binding,selection_binding', 'all,wood_root', 'wood', 'wood_root,', []):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                select_cases(bad)

    def test_readback_is_exact_prefix_and_supports_multiple_targets(self):
        a = state(); b = state(uuid='角色乙', health=55)
        reply = '无关日志\nFOUNDATION ' + json.dumps(a) + '\nFOUNDATION ' + json.dumps(b)
        self.assertEqual([a, b], parse_status(reply))
        with self.assertRaises(ValueError):
            parse_status('FOUNDATION_FIXTURE 接受目标=1')

    def test_missing_malformed_and_nonfinite_readback_rejected(self):
        invalid = [dict(), state(now=-1), state(health=float('nan')), state(shield=float('inf')),
                   state(rooted=1), state(uuid=''), state(wood_root={}), state(qi=True)]
        for row in invalid:
            with self.subTest(row=row), self.assertRaises(ValueError):
                parse_status('FOUNDATION ' + json.dumps(row))
        for reply in ('FOUNDATION [1]', 'FOUNDATION {', 'FOUNDATION null', '无实体'):
            with self.subTest(reply=reply), self.assertRaises((ValueError, TypeError)):
                parse_status(reply)

    def test_negative_or_noninteger_cooldown_rejected(self):
        for n in (-1, 3.5, True, float('nan')):
            value = state(); value['wood_root']['cooldown_remaining'] = n
            with self.subTest(n=n), self.assertRaises(ValueError):
                parse_status('FOUNDATION ' + json.dumps(value))

    def test_lifesteal_uses_actual_loss_missing_health_and_remaining_cap(self):
        self.assertAlmostEqual(.8, lifesteal_expected(10, .08, 100, .05))
        self.assertEqual(5, lifesteal_expected(100, .08, 100, .05))
        self.assertEqual(1, lifesteal_expected(100, .08, 100, .05, already=4))
        self.assertEqual(0, lifesteal_expected(100, .08, 100, .05, already=5))
        self.assertEqual(.2, lifesteal_expected(10, .08, 100, .05, missing=.2))

    def test_invalid_lifesteal_inputs_fail_closed(self):
        for value in (-1, float('nan'), float('inf'), True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                lifesteal_expected(value, .08, 100, .05)
        self.assertFalse(close(float('nan'), 1))
        self.assertFalse(close(True, 1))


class AuthorityTests(unittest.TestCase):
    def test_rcon_can_prepare_and_read_but_cannot_bind_or_cast(self):
        allowed = ('myvillage foundation grant shield CaptureDev myvillage:fixture_a 8 60',
                   'myvillage foundation grant root CaptureDev @e[tag=fixture,limit=1] 60',
                   'myvillage foundation grant poise CaptureDev myvillage:fixture 60',
                   'myvillage foundation status CaptureDev', 'tick freeze', 'tick step 60',
                   'execute as CaptureDev run myvillage combat bindings',
                   'execute if loaded 0 -61 0 run time query gametime',
                   'myvillage cultivation fate roll CaptureDev')
        for command in allowed:
            with self.subTest(command=command):
                self.assertTrue(allow_rcon(command))
        forbidden = ('myvillage foundation cast wood_root', 'myvillage combat skill 0',
                     'myvillage cultivation fate confirm CaptureDev 7',
                     'execute as CaptureDev run myvillage foundation cast wood_root',
                     'execute if entity @a run myvillage combat cast wood_root',
                     'tick freeze\nmyvillage combat cast wood_root',
                     'data modify entity CaptureDev Health set value 100f')
        for command in forbidden:
            with self.subTest(command=command):
                self.assertFalse(allow_rcon(command))

    def test_local_or_wrong_cloud_cannot_start_game(self):
        for root, env in ((Path('/home/ubuntu/code/mc'), {'MYVILLAGE_CLOUD_PROVIDER': 'Colab'}),
                          (Path('/content/wilderness-roster'), {}),
                          (Path('/content/wilderness-roster'), {'MYVILLAGE_CLOUD_PROVIDER': 'Modal'})):
            with self.subTest(root=root, env=env), self.assertRaises(RuntimeError):
                assert_colab(root, env)
        assert_colab(Path('/content/wilderness-roster'), {'MYVILLAGE_CLOUD_PROVIDER': 'Colab'})

    def test_nonempty_output_preserves_previous_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); old = root/'failure.txt'; old.write_text('已有失败证据')
            with self.assertRaises(ValueError):
                FoundationEvidence(root, ['wood_root'])
            self.assertEqual('已有失败证据', old.read_text())

    def test_rcon_cast_rejected_before_transport(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.s = Mock()
        with self.assertRaises(ValueError):
            evidence.rc('myvillage foundation cast wood_root')
        evidence.s.run.assert_not_called()


class DispatchTests(unittest.TestCase):
    def bare(self, root, selected):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.out = evidence.root_out = root
        evidence.report = initial_report(selected)
        evidence.s = Mock(); evidence.s.game.client_log = root/'原始客户端.log'
        evidence.s.game.client_log.write_text('客户端失败上下文')
        evidence.start = Mock(); evidence.offset = Mock(return_value=0)
        evidence.log = Mock(return_value='服务器失败上下文')
        evidence.capture = Mock()
        evidence.prepare_case = Mock()
        return evidence

    def test_case_failure_is_retained_and_next_selected_case_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); evidence = self.bare(root, ['wood_root', 'foundations'])
            evidence.wood_root = Mock(side_effect=AssertionError('墙后目标被控制'))
            evidence.foundations = Mock()
            with patch('tools.fate_foundations_evidence.assert_colab'):
                evidence.run()
            self.assertEqual(['foundations'], evidence.report['completed'])
            self.assertEqual('fail', evidence.report['cases']['wood_root']['result'])
            self.assertEqual('pass', evidence.report['cases']['foundations']['result'])
            self.assertNotIn('wood_root', evidence.report['unrun'])
            self.assertIn('lifecycle', evidence.report['unrun'])
            self.assertIn('墙后目标被控制', (root/'wood_root/failure.txt').read_text())
            self.assertIn('服务器失败上下文', (root/'wood_root/server.log').read_text())
            self.assertTrue((root/'foundations/client.log').exists())
            evidence.foundations.assert_called_once()
            evidence.capture.assert_called_once_with('失败现场')
            self.assertFalse(evidence.report['passed'])

    def test_unselected_handlers_never_run(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = self.bare(Path(directory), ['foundations'])
            evidence.foundations = Mock(); evidence.two_players = Mock(); evidence.lifecycle = Mock()
            with patch('tools.fate_foundations_evidence.assert_colab'):
                evidence.run()
            evidence.foundations.assert_called_once()
            evidence.two_players.assert_not_called(); evidence.lifecycle.assert_not_called()
            self.assertEqual(['foundations'], evidence.report['completed'])
            self.assertIn('two_players', evidence.report['unrun'])

    def test_refusal_requires_no_extra_qi_or_cooldown_change(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        before = state(); after = deepcopy(before); after['qi'] -= 10
        evidence.status = Mock(side_effect=[before, after]); evidence.offset = Mock(return_value=0)
        evidence.key = Mock()
        def check(name, passed, detail):
            if not passed:
                raise AssertionError(name)
        evidence.check = check
        with patch('tools.fate_foundations_evidence.time.sleep'), self.assertRaises(AssertionError):
            evidence.cast_wood(False)
        evidence.key.assert_called_once_with('z')

    def test_success_requires_exact_single_payment(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        before = state(); after = state(qi=80, wood_root=dict(eligible=True, cost=10, cooldown_remaining=400))
        evidence.status = Mock(side_effect=[before, after, after]); evidence.offset = Mock(return_value=0)
        evidence.key = Mock(); evidence.poll = Mock(return_value=after)
        evidence.check = lambda name, passed, detail: None if passed else (_ for _ in ()).throw(AssertionError(name))
        with patch('tools.fate_foundations_evidence.time.sleep'), self.assertRaises(AssertionError):
            evidence.cast_wood()

    def totem_evidence(self, gained):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.s = Mock(); evidence.s.username = 'CaptureDev'
        evidence.report = initial_report(['two_players'])
        evidence.healing_config = {'lifesteal_window_ticks': 20}
        evidence.definitions = {'myvillage:wood_spirit_body': {'effects': {'lifesteal_fraction': .08}}}
        evidence.rc = Mock(return_value='目标的实体数据: []')
        evidence.hp = Mock(side_effect=[200, 1, 60, 1, 60+gained])
        evidence.wound = Mock(); evidence.step = Mock(); evidence.now = Mock(return_value=100)
        evidence.capture = Mock()
        evidence.check = lambda name, passed, detail: None if passed else (_ for _ in ()).throw(AssertionError(name))
        return evidence

    def test_totem_scenario_uses_real_custom_mouse_attack_and_1hp_budget(self):
        evidence = self.totem_evidence(.08)
        with patch('tools.fate_foundations_evidence.scene.ensure_cultivation'), patch('tools.fate_foundations_evidence.read_data', return_value=[]):
            evidence.totem_lifesteal('AlchemyGuest')
        evidence.s.game.xdo.assert_called_once_with('click', '1')
        self.assertTrue(any(row['method'] == '映射左键青锋自定义近战触发图腾' for row in evidence.report['inputs']))
        self.assertFalse(any('cast' in call.args[0] for call in evidence.rc.call_args_list))
        evidence.step.assert_any_call(21)

    def test_totem_scenario_rejects_overhealing(self):
        evidence = self.totem_evidence(.16)
        with patch('tools.fate_foundations_evidence.scene.ensure_cultivation'), patch('tools.fate_foundations_evidence.read_data', return_value=[]), self.assertRaises(AssertionError):
            evidence.totem_lifesteal('AlchemyGuest')

    def test_poise_charge_scenario_requires_real_incoming_health_loss(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.s = Mock(); evidence.s.username = 'CaptureDev'
        evidence.rc = Mock(); evidence.step = Mock(); evidence.offset = Mock(return_value=0)
        evidence.now = Mock(return_value=100); evidence.capture = Mock()
        evidence.hostile_hit = Mock(return_value=(state(health=70), state(health=70)))
        evidence.report = initial_report(['foundations'])
        evidence.check = lambda name, passed, detail: None if passed else (_ for _ in ()).throw(AssertionError(name))
        with patch('tools.fate_foundations_evidence.scene.ensure_cultivation'), self.assertRaises(AssertionError):
            evidence.poise_charging()
        self.assertEqual([(('mousedown', '3'),), (('mouseup', '3'),)], [(call.args,) for call in evidence.s.game.xdo.call_args_list])


if __name__ == '__main__':
    unittest.main()
