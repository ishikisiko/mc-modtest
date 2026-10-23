"""离线解析、拒绝路径与调度测试；不会启动游戏、Gradle 或 Colab。"""
from copy import deepcopy
from contextlib import contextmanager
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

from tools.fate_foundations_evidence import (
    ABILITY, CASES, FIRST_PASS, EarlySessionFreeze, FoundationEvidence, allow_rcon, assert_colab, cs,
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


class EarlyFreezeTests(unittest.TestCase):
    def session(self, ident='new'):
        return dict(session_id=ident, phase='starting_server', supervisor={'pid': 321, 'start': 5},
                    procs={'server': {'pid': 322, 'start': 6}}, rcon_port=23456,
                    rcon_password='不能进入报告的密码')

    def connection(self):
        rcon = Mock()
        rcon.cmd.side_effect = ['The game is frozen', 'The time is 1234']
        return rcon

    def test_only_new_session_is_frozen_before_client_start_finishes(self):
        current = self.session('old')
        old_observed = threading.Event()
        worker = EarlySessionFreeze('new', 2, poll=.005)
        rcon = self.connection()
        def read():
            if current['session_id'] == 'old':
                old_observed.set()
            return deepcopy(current)
        def start(config):
            self.assertTrue(old_observed.wait(1))
            transport.assert_not_called()
            current.update(self.session(config.session_id))
            self.assertTrue(worker.done.wait(1), '应在客户端冷启动完成前冻结')
            current['phase'] = 'ready'
            current['procs']['client'] = {'pid': 323, 'start': 7}
            return deepcopy(current)
        with patch('tools.fate_foundations_evidence.cs.read_state', side_effect=read), \
                patch('tools.fate_foundations_evidence.cs.start', side_effect=start), \
                patch('tools.fate_foundations_evidence.Rcon', return_value=rcon) as transport:
            result, clock = worker.start_session(cs.SessionConfig('new', reuse_world=True))
        self.assertEqual('ready', result['phase'])
        self.assertEqual({'session_id': 'new', 'frozen_gametime': 1234}, clock)
        self.assertNotIn(current['rcon_password'], json.dumps(clock))
        self.assertEqual(['tick freeze', 'time query gametime'], [c.args[0] for c in rcon.cmd.call_args_list])
        rcon.close.assert_called_once()
        self.assertTrue(worker.cancel.is_set())
        self.assertFalse(worker.thread.is_alive())

    def test_session_replaced_after_connect_never_receives_commands(self):
        current = self.session()
        worker = EarlySessionFreeze('new', 1, poll=.005)
        rcon = self.connection()
        rcon.connect.side_effect = lambda: current.update(session_id='another-thread')
        def start(config):
            self.assertTrue(worker.done.wait(1))
            return self.session()
        with patch('tools.fate_foundations_evidence.cs.read_state', side_effect=lambda: deepcopy(current)), \
                patch('tools.fate_foundations_evidence.cs.start', side_effect=start), \
                patch('tools.fate_foundations_evidence.Rcon', return_value=rcon):
            with self.assertRaisesRegex(RuntimeError, '冻结前核对会话身份'):
                worker.start_session(cs.SessionConfig('new', reuse_world=True))
        rcon.cmd.assert_not_called()
        rcon.close.assert_called_once()
        self.assertFalse(worker.thread.is_alive())

    def test_freeze_rejection_exception_or_bad_time_cannot_pass_or_leak_password(self):
        for replies in (['Unknown command'], [RuntimeError('不能进入报告的密码')],
                        ['The game is frozen', 'bad time']):
            with self.subTest(replies=replies):
                worker = EarlySessionFreeze('new', 1, poll=.005)
                rcon = self.connection(); rcon.cmd.side_effect = replies
                def start(config):
                    self.assertTrue(worker.done.wait(1))
                    return self.session()
                with patch('tools.fate_foundations_evidence.cs.read_state', side_effect=self.session), \
                        patch('tools.fate_foundations_evidence.cs.start', side_effect=start), \
                        patch('tools.fate_foundations_evidence.Rcon', return_value=rcon):
                    with self.assertRaisesRegex(RuntimeError, '早期冻结失败') as caught:
                        worker.start_session(cs.SessionConfig('new', reuse_world=True))
                self.assertIsNone(worker.result)
                self.assertNotIn('不能进入报告的密码', str(caught.exception))
                self.assertIsNone(caught.exception.__context__)
                rcon.close.assert_called_once()
                self.assertFalse(worker.thread.is_alive())

    def test_rcon_not_ready_retries_with_bound_and_closes_every_connection(self):
        worker = EarlySessionFreeze('new', 1, poll=.005)
        pending = Mock(); pending.connect.side_effect = ConnectionRefusedError()
        ready = self.connection()
        def start(config):
            self.assertTrue(worker.done.wait(1))
            return self.session()
        with patch('tools.fate_foundations_evidence.cs.read_state', side_effect=self.session), \
                patch('tools.fate_foundations_evidence.cs.start', side_effect=start), \
                patch('tools.fate_foundations_evidence.Rcon', side_effect=[pending, ready]):
            _, clock = worker.start_session(cs.SessionConfig('new', reuse_world=True))
        self.assertEqual(1234, clock['frozen_gametime'])
        pending.cmd.assert_not_called()
        pending.close.assert_called_once(); ready.close.assert_called_once()
        self.assertFalse(worker.thread.is_alive())

    def test_old_session_times_out_without_connecting_and_cleans_worker(self):
        worker = EarlySessionFreeze('new', .02, poll=.005)
        def start(config):
            self.assertTrue(worker.done.wait(1))
            return self.session()
        with patch('tools.fate_foundations_evidence.cs.read_state', return_value=self.session('old')), \
                patch('tools.fate_foundations_evidence.cs.start', side_effect=start), \
                patch('tools.fate_foundations_evidence.Rcon') as transport:
            with self.assertRaisesRegex(RuntimeError, 'TimeoutError'):
                worker.start_session(cs.SessionConfig('new', reuse_world=True))
        transport.assert_not_called()
        self.assertFalse(worker.thread.is_alive())

    def test_start_failure_always_cancels_and_joins_worker(self):
        worker = EarlySessionFreeze('new', 60, poll=.1)
        with patch('tools.fate_foundations_evidence.cs.read_state', return_value=None), \
                patch('tools.fate_foundations_evidence.cs.start', side_effect=RuntimeError('启动失败')):
            with self.assertRaisesRegex(RuntimeError, '启动失败'):
                worker.start_session(cs.SessionConfig('new', reuse_world=True))
        self.assertTrue(worker.cancel.is_set())
        self.assertTrue(worker.done.is_set())
        self.assertFalse(worker.thread.is_alive())

    def test_non_reuse_or_other_config_rejected_before_start(self):
        for config in (cs.SessionConfig('new'), cs.SessionConfig('old', reuse_world=True)):
            worker = EarlySessionFreeze('new', 1)
            with patch('tools.fate_foundations_evidence.cs.start') as start, self.assertRaises(ValueError):
                worker.start_session(config)
            start.assert_not_called()
            self.assertIsNone(worker.thread.ident)

    def test_reuse_start_records_only_sanitized_clock_from_new_config(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.attach = False; evidence.pvp_enabled = True; evidence.sessions = 0
        evidence.report = initial_report(['lifecycle'])
        evidence.freeze = Mock(); evidence.position = Mock(); evidence.check = Mock()
        evidence.now = Mock(return_value=1234)
        ready = self.session(); ready.update(phase='ready', pvp=True)
        clock = {'session_id': 'new', 'frozen_gametime': 1234}
        fixture = Mock(); fixture.start_session.return_value = (ready, clock)
        with patch('tools.fate_foundations_evidence.assert_colab'), \
                patch('tools.fate_foundations_evidence.secrets.token_hex', return_value='new'), \
                patch('tools.fate_foundations_evidence.EarlySessionFreeze', return_value=fixture) as worker, \
                patch('tools.fate_foundations_evidence.cs.supervisor_alive', return_value=True), \
                patch('tools.fate_foundations_evidence.cs.Session') as session:
            evidence.start(reuse=True)
        config = fixture.start_session.call_args.args[0]
        self.assertEqual('new', config.session_id)
        self.assertTrue(config.reuse_world)
        worker.assert_called_once_with('new', config.lock_timeout + config.server_timeout + 60)
        session.assert_called_once_with(ready, ui_check=True)
        self.assertEqual(clock, evidence.report['phases'][0]['early_freeze'])
        self.assertNotIn(ready['rcon_password'], json.dumps(evidence.report))
        evidence.check.assert_called_once_with('重启早期冻结持续至客户端就绪', True, clock)


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

    def test_persisted_cooldown_matches_elapsed_ticks_and_never_accepts_zero(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.check = lambda name, passed, detail: None if passed else (_ for _ in ()).throw(AssertionError(detail))
        before = state(now=100, wood_root=dict(eligible=True, cost=10, cooldown_remaining=400))
        def after(now, remaining):
            return state(now=now, wood_root=dict(eligible=True, cost=10, cooldown_remaining=remaining))
        evidence.check_cooldown_persisted('持久化', before, after(107, 393))
        for value in (after(107, 392), after(107, 400), after(500, 0), after(99, 401)):
            with self.subTest(after=value), self.assertRaises(AssertionError):
                evidence.check_cooldown_persisted('持久化', before, value)

    def test_home_clears_only_stele_and_base_after_loaded_chunk_check(self):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.s = Mock(); evidence.s.username = 'CaptureDev'
        events = []
        evidence.rc = Mock(side_effect=lambda command: events.append(command) or 'Test passed')
        def loaded(getter, predicate, **kwargs):
            self.assertTrue(predicate(getter()))
            events.append('loaded')
        evidence.poll = Mock(side_effect=loaded)
        evidence.home()
        self.assertEqual(['tp CaptureDev .5 -60 .5 0 0', 'execute if loaded 0 -60 2', 'loaded',
                          'setblock 0 -59 2 minecraft:air', 'setblock 0 -60 2 minecraft:air'], events)
        evidence.s.game.require_ingame.assert_called_once_with('天命基础验收')

    def test_logout_keeps_clock_frozen_across_guest_exit_and_rejoin(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = FoundationEvidence.__new__(FoundationEvidence)
            evidence.out = Path(directory); evidence.s = Mock(username='CaptureDev')
            original = evidence.s
            frozen = False; joins = []
            def command(text):
                nonlocal frozen
                if text == 'tick freeze': frozen = True
                if text == 'tick unfreeze': frozen = False
                return 'CaptureDev'
            evidence.rc = Mock(side_effect=command)
            evidence.position = Mock(); evidence.configure = Mock(); evidence.step = Mock()
            evidence.choose_wood = Mock(side_effect=evidence.freeze)
            evidence.bind_wood = Mock(side_effect=evidence.freeze)
            evidence.cast_wood = Mock(); evidence.assert_healing_restored = Mock()
            evidence.now = Mock(return_value=100)
            before = state(fields=[], wood_root=dict(eligible=True, cost=10, cooldown_remaining=400))
            after = deepcopy(before)
            evidence.status = Mock(side_effect=[state(), before, after])
            evidence.poll = Mock(side_effect=lambda getter, predicate, **kwargs: self.assertTrue(predicate(getter())))
            evidence.check = lambda name, passed, detail: self.assertTrue(passed, name)
            @contextmanager
            def guest_context(owner):
                joins.append(frozen)
                guest = Mock(); guest.username = 'AlchemyGuest'
                yield guest
                self.assertTrue(frozen, '第二客户端退出不得解除冻结')
            with patch('tools.fate_foundations_evidence.SecondClient', side_effect=guest_context), \
                    patch('tools.fate_foundations_evidence.scene.ensure_cultivation'):
                evidence.logout_reconnect()
            self.assertEqual([False, True], joins)
            self.assertTrue(frozen)
            self.assertIs(original, evidence.s)
            self.assertIsNone(evidence.guest)
            self.assertEqual([(), (False,)], [call.args for call in evidence.cast_wood.call_args_list])

    def cap_evidence(self, first_heal=17.25, second_heal=0):
        evidence = FoundationEvidence.__new__(FoundationEvidence)
        evidence.s = Mock(); evidence.s.username = 'CaptureDev'
        evidence.status = Mock(return_value=state(max_health=345))
        evidence.healing_config = {'lifesteal_window_ticks': 20}
        evidence.number = Mock(side_effect=[1, 449])
        evidence.rc = Mock(); evidence.step = Mock(); evidence.wound = Mock()
        evidence.now = Mock(return_value=100)
        evidence.dummy = Mock(return_value='second')
        health = {'CaptureDev': 300, 'first': 899, 'second': 899}
        evidence.hp = Mock(side_effect=lambda target: health[target])
        gains = iter([first_heal, second_heal, 1])
        def attack(target):
            health[target] -= 449
            health['CaptureDev'] += next(gains)
            return 449
        evidence.attack = Mock(side_effect=attack)
        evidence.check = lambda name, passed, detail: None if passed else (_ for _ in ()).throw(AssertionError(name))
        return evidence

    def test_lifesteal_cap_fixture_scales_for_345hp_and_preserves_real_attacks(self):
        evidence = self.cap_evidence()
        evidence.lifesteal_cap_probe('first', .08, .05)
        evidence.rc.assert_any_call('attribute CaptureDev minecraft:generic.attack_damage base set 432')
        for target in ('first', 'second'):
            evidence.rc.assert_any_call(f'attribute {target} minecraft:generic.max_health base set 899')
            evidence.rc.assert_any_call(f'data merge entity {target} {{Health:899f,HurtTime:0s}}')
        self.assertEqual([('first',), ('second',), ('second',)], [call.args for call in evidence.attack.call_args_list])
        self.assertEqual('attribute CaptureDev minecraft:generic.attack_damage base set 1', evidence.rc.call_args.args[0])

    def test_lifesteal_cap_underfill_or_second_hit_healing_still_fails_and_restores(self):
        for first, second in ((14.4, 0), (17.25, 1)):
            with self.subTest(first=first, second=second):
                evidence = self.cap_evidence(first, second)
                with self.assertRaisesRegex(AssertionError, '每秒最大生命吸血上限'):
                    evidence.lifesteal_cap_probe('first', .08, .05)
                self.assertEqual('attribute CaptureDev minecraft:generic.attack_damage base set 1', evidence.rc.call_args.args[0])

    def test_lifesteal_cap_input_failure_restores_original_attack(self):
        evidence = self.cap_evidence()
        evidence.attack.side_effect = RuntimeError('真实鼠标命中失败')
        with self.assertRaisesRegex(RuntimeError, '真实鼠标命中失败'):
            evidence.lifesteal_cap_probe('first', .08, .05)
        self.assertEqual('attribute CaptureDev minecraft:generic.attack_damage base set 1', evidence.rc.call_args.args[0])

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
