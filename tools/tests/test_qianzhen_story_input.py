"""Stateful X/renderer model: test input effects, deadlines and wrong owners."""
from pathlib import Path
import ctypes
import os
import select
import shutil
import subprocess
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.qianzhen.story_input import StoryInput
from tools.qianzhen.story_evidence import StoryEvidence
from tools.qianzhen.story_pointer import X11Probe, _Event, sample_input
from tools.tests.test_qianzhen_story_pointer import FocusModel


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
        def sample(display, window, deadline):
            model = FocusModel(grabbed=game.pointer_grabbed())
            model.current = game.focused
            return sample_input(model, window)
        probe = patch('tools.qianzhen.story_input.probe_input_state', side_effect=sample)
        probe.start(); self.addCleanup(probe.stop)
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

    def test_closed_focus_roundtrip_released_pointer_never_confirms_or_clicks(self):
        game, gate = self.controller(menu=False, grabbed=False)
        with patch('tools.qianzhen.story_input.probe_input_state',
                   side_effect=lambda *args: sample_input(FocusModel(('99', '42')), '42')):
            with self.assertRaisesRegex(RuntimeError, 'lost focus'):
                gate.menu('closed-focus-roundtrip')
        sample = next(row for row in self.rows if row['event'] == 'grab')
        self.assertEqual((sample['focused'], sample['grabbed'], sample['focused_after']),
                         ('42', False, '42'))
        self.assertEqual([e['type'] for e in sample['focus_events']], ['FocusOut','FocusIn'])
        self.assertFalse(any(row['event'] == 'menu_confirmed' for row in self.rows))
        self.assertFalse(any(args[0] in ('key', 'click') for _, args, _ in game.commands))

    def test_roundtrip_menu_rejection_prevents_evidence_left_mouse_dispatch(self):
        game, gate = self.controller(menu=False, grabbed=False)
        game.story_input = gate
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'blocked-click')
            self.addCleanup(e.position_stream.close); self.addCleanup(e.action_stream.close)
            e.s = SimpleNamespace(game=game, username='TestPlayer')
            with patch('tools.qianzhen.story_input.probe_input_state',
                       side_effect=lambda *args: sample_input(FocusModel(('99', '42')), '42')):
                with self.assertRaisesRegex(RuntimeError, 'lost focus'):
                    e.click_dialogue({'x':100,'y':200,'w':240,'h':40,'option':'accept','nonce':8},
                                     'guide', 'roundtrip-must-fail')
            self.assertFalse(any(args[0] in ('mousemove', 'click') for _, args, _ in game.commands))
            e.action_stream.flush()
            self.assertNotIn('left_mouse', (e.out/'actions.jsonl').read_text())

    def test_menu_click_rejects_grab_changes_at_each_sample_and_final_gate(self):
        for sequence in ((True, False), (False, True), (False, False, True, True),
                         (False, False, False, True), (False, False, True, False)):
            with self.subTest(sequence=sequence):
                self.rows.clear()
                game, gate = self.controller(menu=True, grabbed=False)
                game.story_input = gate
                values = iter(sequence)
                def probe(*args):
                    return sample_input(FocusModel(grabbed=[next(values), next(values)]), '42')
                with tempfile.TemporaryDirectory() as folder:
                    e = StoryEvidence(Path(folder)/'blocked')
                    self.addCleanup(e.position_stream.close); self.addCleanup(e.action_stream.close)
                    e.s = SimpleNamespace(game=game, username='TestPlayer')
                    with patch('tools.qianzhen.story_input.probe_input_state', side_effect=probe):
                        with self.assertRaisesRegex(RuntimeError, 'Expected open actor menu'):
                            e.click_dialogue({'x':100,'y':200,'w':240,'h':40,'option':'accept','nonce':8},
                                             'guide', 'grab-change')
                    self.assertFalse(any(args == ('click', '1') for _, args, _ in game.commands))
                    self.assertEqual(self.rows[-1]['event'], 'failed')
                    e.action_stream.flush()
                    self.assertNotIn('left_mouse', (e.out/'actions.jsonl').read_text())
                    if sequence[:2] != (False, False):
                        self.assertFalse(any(row['event'] == 'menu_confirmed' for row in self.rows))

    def test_menu_click_samples_after_move_and_rechecks_pid_inside_original_budget(self):
        game, gate = self.controller(menu=True, grabbed=False)
        gate.click_menu('stable', 220, 220)
        self.assertEqual(sum(args == ('click', '1') for _, args, _ in game.commands), 1)
        self.assertEqual(self.rows[-1]['event'], 'menu_click_dispatched')
        self.assertEqual(len([row for row in self.rows if row['event'] == 'grab']), 2)
        game.commands.clear()
        original = game.xdo
        def changed(*args, **kwargs):
            result = original(*args, **kwargs)
            if args[0] == 'mousemove': game.pid = 'unexpected'
            return result
        game.xdo = changed
        with self.assertRaisesRegex(RuntimeError, 'identity changed'):
            gate.click_menu('wrong-pid', 220, 220)
        self.assertFalse(any(args == ('click', '1') for _, args, _ in game.commands))

    def test_open_menu_rejects_events_and_endpoint_drift(self):
        for changes in (('99',), ('99','42'), ('42',)):
            with self.subTest(changes=changes):
                game, gate = self.controller(menu=True, grabbed=False)
                with patch('tools.qianzhen.story_input.probe_input_state',
                           side_effect=lambda *args: sample_input(FocusModel(changes), '42')):
                    with self.assertRaisesRegex(RuntimeError, 'lost focus'):
                        gate.menu('open-focus-drift')
        self.assertFalse(any(row['event'] == 'menu_confirmed' for row in self.rows))

    def test_closed_grabbed_menu_focus_target_other_target_never_confirms(self):
        game, gate = self.controller(menu=False, grabbed=True)
        model = FocusModel(('42',), grabbed=True)
        model.current = '99'
        with patch('tools.qianzhen.story_input.probe_input_state',
                   side_effect=lambda *args: sample_input(model, '42')):
            with self.assertRaisesRegex(RuntimeError, 'lost focus'):
                gate.menu('closed-focus-drift')
        row = next(row for row in self.rows if row['event'] == 'grab')
        self.assertEqual((row['focused'], row['grabbed'], row['focused_after']), ('99', True, '42'))
        self.assertFalse(any(row['event'] == 'menu_confirmed' for row in self.rows))
        self.assertFalse(any(args[0] in ('key', 'click') for _, args, _ in game.commands))

    def test_open_menu_rejects_all_original_wrong_focus_endpoint_pairs(self):
        for before, after in (('99', '42'), ('42', '99'), ('99', '99')):
            with self.subTest(before=before, after=after):
                game, gate = self.controller(menu=True, grabbed=False)
                model = FocusModel((after,) if after != before else ())
                model.current = before
                with patch('tools.qianzhen.story_input.probe_input_state',
                           side_effect=lambda *args: sample_input(model, '42')):
                    with self.assertRaisesRegex(RuntimeError, 'lost focus'):
                        gate.menu('open-focus-drift')
        self.assertFalse(any(row['event'] == 'menu_confirmed' for row in self.rows))

    def test_isolated_probe_deadline_is_gate_deadline_and_timeout_is_terminal(self):
        game, gate = self.controller()
        with patch('tools.qianzhen.story_input.probe_input_state') as probe:
            def stalled(display, window, deadline):
                self.assertEqual(display, ':92')
                self.assertEqual(window, '42')
                self.assertEqual(deadline, gate.started+8.)
                self.clock.sleep(deadline-self.clock.now)
                raise TimeoutError('isolated probe timed out and reaped')
            probe.side_effect = stalled
            with self.assertRaises(TimeoutError): gate.ready('blocked-xlib')
        self.assertEqual(self.clock.now, 8.)
        self.assertEqual(self.rows[-1]['event'], 'failed')
        self.assertFalse(any(row['event'] == 'confirmed' for row in self.rows))

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


class RealGrabDispatchTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('Xvfb') and shutil.which('xdotool'), 'requires lightweight X11')
    def test_real_grab_transition_and_final_gate_never_send_left_click(self):
        reader, writer = os.pipe()
        server = subprocess.Popen(['Xvfb', '-displayfd', str(writer), '-screen', '0', '400x300x24',
                                   '-nolisten', 'tcp'], pass_fds=(writer,),
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        os.close(writer)
        try:
            self.assertTrue(select.select([reader], [], [], 2.)[0])
            display = ':'+os.read(reader, 32).decode().strip()
            controller, probe = X11Probe(display), X11Probe(display)
            try:
                x, d = controller.x, controller.d
                x.XCreateSimpleWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int,
                    ctypes.c_int, ctypes.c_uint, ctypes.c_uint, ctypes.c_uint, ctypes.c_ulong, ctypes.c_ulong]
                x.XCreateSimpleWindow.restype = ctypes.c_ulong
                x.XMapWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
                x.XSetInputFocus.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
                x.XStoreName.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_char_p]
                x.XInternAtom.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]
                x.XInternAtom.restype = ctypes.c_ulong
                x.XChangeProperty.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_ulong,
                    ctypes.c_ulong, ctypes.c_int, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
                window = x.XCreateSimpleWindow(d, x.XDefaultRootWindow(d), 0, 0, 300, 200, 0, 0, 0)
                x.XStoreName(d, window, b'Minecraft X11 test')
                pid = ctypes.c_ulong(os.getpid())
                x.XChangeProperty(d, window, x.XInternAtom(d, b'_NET_WM_PID', False),
                                  6, 32, 0, ctypes.byref(pid), 1)  # XA_CARDINAL
                x.XSelectInput(d, window, 1 << 2)  # ButtonPressMask: actual receiver
                x.XMapWindow(d, window)
                x.XSetInputFocus(d, window, 1, 0)
                x.XSync(d, False)
                def take_grab():
                    self.assertEqual(x.XGrabPointer(d, window, False, 1 << 2, 1, 1, 0, 0, 0), 0)
                    x.XSync(d, False)
                def drain_clicks():
                    x.XSync(d, False)
                    events = []
                    while x.XPending(d):
                        event = _Event()
                        x.XNextEvent(d, ctypes.byref(event))
                        if event.type == 4: events.append(event.type)
                    return events
                real_events = probe.events
                for stage in ('start', 'event_end', 'before_dispatch', 'stable'):
                    with self.subTest(stage=stage), tempfile.TemporaryDirectory() as folder:
                        x.XUngrabPointer(d, 0); x.XSync(d, False)
                        drain_clicks()
                        subprocess.run(['xdotool', 'mousemove', '--window', str(window), '10', '10'],
                                       env=dict(os.environ, DISPLAY=display), check=True, timeout=2.)
                        rows, commands, event_calls = [], [], []
                        if stage == 'start': take_grab()
                        def events(target):
                            result = real_events(target)
                            event_calls.append(True)
                            if stage == 'event_end' and len(event_calls) == 1: take_grab()
                            return result
                        probe.events = events
                        def xdo(*args, timeout):
                            commands.append(args)
                            result = subprocess.run(['xdotool', *args], env=dict(os.environ, DISPLAY=display),
                                                    capture_output=True, text=True, check=True, timeout=timeout)
                            if stage == 'before_dispatch' and args[0] == 'mousemove': take_grab()
                            return result.stdout
                        game = SimpleNamespace(display=display, client_log=Path(folder)/'client.log',
                                               size=(300, 200), xdo=xdo)
                        gate = StoryInput(game, None, lambda action, **row: rows.append(row))
                        game.story_input = gate
                        e = StoryEvidence(Path(folder)/'evidence')
                        e.s = SimpleNamespace(game=game, username='X11Test')
                        try:
                            with patch('tools.qianzhen.story_input.probe_input_state',
                                       side_effect=lambda *args: sample_input(probe, str(window))):
                                button = dict(x=50, y=60, w=100, h=40, option='accept', nonce=8)
                                if stage == 'stable': e.click_dialogue(button, 'guide', stage)
                                else:
                                    with self.assertRaisesRegex(RuntimeError, 'Expected open actor menu'):
                                        e.click_dialogue(button, 'guide', stage)
                            e.action_stream.flush()
                            sent = 'left_mouse' in (e.out/'actions.jsonl').read_text()
                            self.assertEqual(sent, stage == 'stable')
                            self.assertEqual(drain_clicks(), [4] if stage == 'stable' else [])
                            self.assertEqual(('click', '1') in commands, stage == 'stable')
                            if stage == 'event_end':
                                sample = next(row for row in rows if row['event'] == 'grab')
                                self.assertEqual((sample['grabbed'], sample['grabbed_after']), (False, True))
                                self.assertEqual(sample['focus_events'], [])
                                self.assertEqual(sample['focused'], sample['focused_after'])
                                self.assertFalse(any(row['event'] == 'menu_confirmed' for row in rows))
                        finally:
                            e.position_stream.close(); e.action_stream.close()
            finally:
                probe.close(); controller.close()
        finally:
            os.close(reader)
            server.kill(); server.wait(timeout=2.)


if __name__=='__main__': unittest.main()
