"""Stateful X/renderer model: test input effects, deadlines and wrong owners."""
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.qianzhen.story_input import StoryInput
from tools.qianzhen.story_evidence import StoryEvidence


class Clock:
    def __init__(self): self.now = 0.
    def monotonic(self): return self.now
    def sleep(self, seconds): self.now += seconds


class WindowGame:
    display = ':92'
    client_log = Path('/guest/logs/latest.log')
    size = (960, 540)

    def __init__(self, clock, *, menu=False, grabbed=True, delay=.2):
        self.clock, self.menu, self.grabbed, self.delay = clock, menu, grabbed, delay
        self.focused, self.yaw, self.commands = '42', 10., []
        self.due, self.probes, self.wrong_owner = None, [], False
        self.escape_works = self.middle_works = True
        self.windows, self.pid, self.late_rotation = ['42'], '123', False

    def pointer_grabbed(self):
        self.probes.append(self.clock.now)
        if self.due is not None and self.clock.now >= self.due:
            self.grabbed, self.due = True, None
        return self.grabbed

    def xdo(self, *args, timeout):
        self.commands.append((self.clock.now, args, timeout))
        if args[0] == 'search': return '\n'.join(self.windows)
        if args[0] == 'getwindowpid': return self.pid
        if args[0] == 'getwindowname': return 'Minecraft 1.21.1'
        if args[0] == 'getwindowfocus': return self.focused
        if args[0] == 'windowfocus': self.focused = args[-1]
        if args == ('key', 'Escape') and self.escape_works:
            self.menu = not self.menu
            if not self.menu: self.due = self.clock.now+self.delay
        if args == ('click', '2') and not self.menu and self.middle_works:
            self.due = self.clock.now+self.delay
        if args[0] == 'mousemove_relative' and self.grabbed and not self.menu and not self.wrong_owner:
            self.yaw += int(args[-2])*.15
        return ''

    def rotation(self, remaining):
        if self.late_rotation: self.clock.sleep(remaining+.1)
        return [self.yaw, 0.]


class StoryInputTests(unittest.TestCase):
    def setUp(self):
        self.clock = Clock()
        self.patch = patch('tools.qianzhen.story_input.time', self.clock)
        self.patch.start(); self.addCleanup(self.patch.stop)
        self.rows = []

    def controller(self, **kwargs):
        game = WindowGame(self.clock, **kwargs)
        return game, StoryInput(game, game.rotation, lambda action, **row: self.rows.append(row))

    def test_open_screen_closes_once_then_proves_correct_player_motion(self):
        game, gate = self.controller(menu=True, grabbed=False)
        gate.ready('guest-close')
        self.assertFalse(game.menu)
        self.assertEqual(sum(args == ('key', 'Escape') for _,args,_ in game.commands), 1)
        self.assertEqual(self.rows[-1]['event'], 'confirmed')
        self.assertAlmostEqual(game.yaw, 10.)
        moves = [args for _,args,_ in game.commands if args[0]=='mousemove_relative']
        self.assertEqual(moves, [('mousemove_relative','--','12','0'),('mousemove_relative','--','-12','0')])
        self.assertTrue(all(row['display']==':92' and row['window']=='42' for row in self.rows))

    def test_already_closed_ungrabbed_recovers_without_opening_pause(self):
        game, gate = self.controller(grabbed=False)
        gate.ready('closed')
        self.assertFalse(game.menu)
        self.assertFalse(any(args == ('key','Escape') for _,args,_ in game.commands))
        self.assertTrue(game.grabbed)

    def test_already_grabbed_still_checks_actor_and_sends_no_escape(self):
        game, gate = self.controller()
        gate.ready('already-ready')
        self.assertFalse(any(args[0] in ('click','key') for _,args,_ in game.commands))
        self.assertTrue(any(row['event']=='rotation' for row in self.rows))

    def test_wrong_focused_window_receives_no_inputs_before_real_focus(self):
        game, gate = self.controller(grabbed=False)
        game.focused = '99'
        gate.ready('focus')
        focus = next(i for i,(_,args,_) in enumerate(game.commands) if args[0]=='windowfocus')
        clicks = [i for i,(_,args,_) in enumerate(game.commands) if args[0]=='click']
        self.assertTrue(all(i>focus for i in clicks))
        self.assertEqual(game.focused, '42')

    def test_wrong_window_global_grab_cannot_pass_without_actor_motion(self):
        game, gate = self.controller()
        game.wrong_owner = True
        with self.assertRaises(TimeoutError): gate.ready('wrong-grab')
        self.assertEqual(self.rows[-1]['event'], 'failed')
        self.assertLessEqual(self.clock.now, 8.)

    def test_delayed_grab_recovers_after_escape_without_probe_in_quiet_window(self):
        game, gate = self.controller(menu=True, grabbed=False, delay=1.2)
        gate.ready('delayed')
        escape = next(t for t,args,_ in game.commands if args == ('key','Escape'))
        self.assertTrue(all(not escape<t<escape+.8 for t in game.probes))
        self.assertEqual(self.rows[-1]['event'], 'confirmed')

    def test_screen_that_never_closes_fails_with_bounded_input_count(self):
        game, gate = self.controller(menu=True, grabbed=False)
        game.escape_works = False
        with self.assertRaises(TimeoutError): gate.ready('stuck-menu')
        self.assertEqual(sum(args==('key','Escape') for _,args,_ in game.commands),1)
        self.assertEqual(sum(args==('click','2') for _,args,_ in game.commands),2)
        self.assertEqual(self.rows[-1]['event'],'failed')
        self.assertEqual(self.clock.now,8.)

    def test_o_close_gate_does_not_hide_broken_toggle_with_escape(self):
        game, gate = self.controller(menu=True, grabbed=False)
        with self.assertRaises(TimeoutError): gate.ready('O-toggle',allow_escape=False)
        self.assertFalse(any(args==('key','Escape') for _,args,_ in game.commands))

    def test_regrab_after_limit_and_late_rotation_are_rejected(self):
        game, gate = self.controller(grabbed=False, delay=20.)
        with self.assertRaises(TimeoutError): gate.ready('too-late',allow_escape=False)
        game, gate = self.controller()
        game.late_rotation = True
        with self.assertRaises(TimeoutError): gate.ready('late-receipt')
        self.assertEqual(self.rows[-1]['event'],'failed')

    def test_missing_probe_multiple_windows_or_changed_pid_fail_closed(self):
        for defect in ('probe','windows','pid'):
            with self.subTest(defect=defect):
                game, gate = self.controller()
                if defect=='probe': game.pointer_grabbed=lambda: None
                if defect=='windows': game.windows.append('99')
                if defect=='pid': gate.window,gate.pid='42','old'
                with self.assertRaises(RuntimeError): gate.ready(defect)

    def test_menu_requires_correct_window_and_released_pointer(self):
        game, gate = self.controller(menu=True, grabbed=False)
        gate.menu('dialogue')
        self.assertEqual(self.rows[-1]['event'],'menu_confirmed')
        game.grabbed=True
        with self.assertRaises(RuntimeError): gate.menu('unexpected-closed')

    def test_budget_rejects_invalid_and_preserves_eight_second_default(self):
        for value in (0,-1,True,float('nan'),float('inf'),30.01):
            with self.assertRaises(ValueError): StoryInput(None,None,None,value)
        game, gate = self.controller()
        self.assertEqual(gate.timeout,8.)
        self.assertEqual(StoryInput(game,game.rotation,lambda *a,**k:None,30).timeout,30.)

    def test_snapshot_waits_for_new_revision_and_wrong_view_is_fatal(self):
        with tempfile.TemporaryDirectory() as folder:
            e=StoryEvidence(Path(folder)/'snapshot')
            self.addCleanup(e.position_stream.close); self.addCleanup(e.action_stream.close)
            old='QIANZHEN_STORY_VIEW revision=7 available=true accepted=true completed=false chapter=chapter\n'
            new='QIANZHEN_STORY_VIEW revision=8 available=true accepted=true completed=false chapter=chapter\n'
            expected={'revision':8,'accepted':True,'completed':False,'chapter':'chapter'}
            e.log_tail=Mock(side_effect=[old,old+new])
            def poll(getter,predicate,timeout,label):
                self.assertEqual(timeout,12)
                first=getter(); self.assertFalse(predicate(first))
                second=getter(); self.assertTrue(predicate(second))
                return second
            e.poll=poll
            e.expect_client_view(40,expected,'guest')
            self.assertTrue(e.report['checks'][-1]['pass'])
            # A stale row cannot become success even if a caller hands it back.
            e.poll=lambda *args:[dict(expected,revision=7,available=True)]
            with self.assertRaisesRegex(AssertionError,'client_snapshot_guest'):
                e.expect_client_view(40,expected,'guest')

    def test_guest_entry_and_return_check_separate_games_and_keep_failure_window(self):
        with tempfile.TemporaryDirectory() as folder:
            e=StoryEvidence(Path(folder)/'switch')
            self.addCleanup(e.position_stream.close); self.addCleanup(e.action_stream.close)
            primary=SimpleNamespace(display=':91')
            guest=SimpleNamespace(username='Guest',game=SimpleNamespace(display=':92'))
            e.s=SimpleNamespace(username='Primary',game=primary)
            e.input_keys=Mock(); e.record_start=Mock(); e.record_stop=Mock()
            e.capture=Mock()
            calls=[]
            e.input_ready=lambda label,**kw:calls.append((label,e.game.display,kw))
            with e.as_guest(guest): self.assertIs(e.game,guest.game)
            self.assertEqual(calls,[('guest_entry',':92',{'allow_escape':False}),
                                    ('return_to_primary',':91',{'allow_escape':False})])
            e.input_ready=Mock(side_effect=TimeoutError('guest input'))
            e.capture=lambda name:calls.append(('failed_window',e.game.display))
            with self.assertRaisesRegex(TimeoutError,'guest input'):
                with e.as_guest(guest): self.fail('must not execute guest branch')
            self.assertEqual(calls[-1],('failed_window',':92'))
            self.assertIs(e.game,primary)
            guest.game.display=':91'
            with self.assertRaisesRegex(AssertionError,'own X display'):
                with e.as_guest(guest): self.fail('must not use primary display')

    def test_guest_start_focus_precedes_legacy_wait_and_never_claims_grab(self):
        with tempfile.TemporaryDirectory() as folder:
            e=StoryEvidence(Path(folder)/'start-focus')
            self.addCleanup(e.position_stream.close); self.addCleanup(e.action_stream.close)
            e.s=SimpleNamespace(username='Primary',game=SimpleNamespace(display=':91'))
            game=WindowGame(self.clock,menu=True,grabbed=False)
            game.focused='99'
            e.prepare_guest_window(game)
            self.assertEqual(game.focused,'42')
            self.assertEqual(game.probes,[])  # Startup's 45-second wait stays separate.
            e.action_stream.flush()
            import json
            rows=[json.loads(line) for line in (e.out/'actions.jsonl').read_text().splitlines()]
            self.assertEqual(rows[-1]['event'],'activated')
            self.assertEqual(rows[-1]['player'],'AlchemyGuest')
            self.assertEqual(rows[-1]['display'],':92')

    def test_second_client_step_requires_fresh_matching_applied_snapshot(self):
        with tempfile.TemporaryDirectory() as folder:
            e=StoryEvidence(Path(folder)/'e')
            self.addCleanup(e.position_stream.close); self.addCleanup(e.action_stream.close)
            log=Path(folder)/'guest.log'; log.write_text('old')
            e.s=SimpleNamespace(username='Primary')
            e.actor=SimpleNamespace(username='Guest',game=SimpleNamespace(client_log=log))
            state={'stage':'new','evidence':[],'revision':0}
            after={'stage':'next','evidence':[],'revision':1}
            e.status=Mock(side_effect=[state,after]); e.personal=lambda s:s
            e.choose=Mock(); e.expect_step=Mock(); e.capture=Mock()
            e.poll=lambda getter,*args:getter()
            e.expect_client_view=Mock(side_effect=AssertionError('guest snapshot mismatch'))
            row={'action':{'id':'test','kind':'talk','role':'guide','effect':''},'before':'new','after':'next','evidence':[]}
            with self.assertRaisesRegex(AssertionError,'guest snapshot mismatch'): e.execute_step(row)
            e.expect_client_view.assert_called_once_with(3,after,'guest-test')
            e.capture.assert_not_called()


if __name__=='__main__': unittest.main()
