"""Real fork/exec/kill/reap tests with short budgets; no game or long sleeps."""
import ctypes
import json
import os
from pathlib import Path
import signal
import select
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from tools.qianzhen.story_pointer import BOUNDARY, X11Probe, probe_pointer, probe_input_state, sample_input, _kill_reap

REAL_MONOTONIC = time.monotonic


class FocusModel:
    """Subscription queue, including stale events and intra-grab focus changes."""
    def __init__(self, changes=(), grabbed=False):
        self.current, self.changes, self.result = '42', changes, grabbed
        self.queue = [dict(type='FocusOut', window='42', old=True)]
        self.steps = []

    def begin(self, window):
        self.steps.append('subscribe_sync_discard')
        self.queue.clear()

    def focus(self):
        self.steps.append('focus')
        return self.current

    def grabbed(self):
        self.steps.append('grab')
        changes, self.changes = self.changes, ()
        for target in changes:
            self.queue.append(dict(type='FocusOut' if self.current == '42' else 'FocusIn', window='42'))
            self.current = target
        return self.result.pop(0) if isinstance(self.result, list) else self.result

    def events(self, window):
        self.steps.append('sync_drain')
        result, self.queue = self.queue, []
        return result


class FocusBoundaryTests(unittest.TestCase):
    def test_target_other_target_is_visible_even_with_identical_endpoints(self):
        model = FocusModel(('99', '42'))
        result = sample_input(model, '42')
        self.assertEqual((result['focused'], result['grabbed'], result['focused_after']), ('42', False, '42'))
        self.assertEqual([e['type'] for e in result['focus_events']], ['FocusOut', 'FocusIn'])
        self.assertEqual(model.steps, ['subscribe_sync_discard', 'focus', 'grab', 'focus', 'sync_drain', 'grab'])

    def test_real_menu_sample_discards_only_pre_boundary_events(self):
        result = sample_input(FocusModel(), '42')
        self.assertEqual(result['focus_events'], [])
        self.assertIs(result['grabbed'], False)
        self.assertEqual(result['boundary'], BOUNDARY)

    def test_grab_start_end_sequences_preserve_every_raw_value(self):
        from tools.qianzhen.story_input import InputState
        for pair in ((False, False), (False, True), (True, False), (True, True),
                     (None, False), (False, None)):
            with self.subTest(pair=pair):
                row = sample_input(FocusModel(grabbed=list(pair)), '42')
                state = InputState(row['focused'], row['grabbed'], row['grabbed_after'],
                                   row['focused_after'], '42', tuple(row['focus_events']), row['boundary'])
                self.assertEqual((row['grabbed'], row['grabbed_after']), pair)
                self.assertEqual(state.menu, pair == (False, False))
                self.assertEqual(state.ingame, pair == (True, True))

    def test_protocol_requires_event_boundary_and_correct_target(self):
        good = sample_input(FocusModel(), '42')
        for bad in (False, dict(good, boundary=None), dict(good, window='99'),
                    dict(good, grabbed_after='false'), dict(good, focus_events=None), dict(good, focus_events=[dict(type='FocusOut', window='99')])):
            with self.subTest(bad=bad), patch('tools.qianzhen.story_pointer._run_probe', return_value=bad):
                with self.assertRaises(RuntimeError): probe_input_state(':92', '42', time.monotonic()+1)

    @unittest.skipUnless(shutil.which('Xvfb'), 'requires local lightweight Xvfb, never Minecraft')
    def test_real_x11_events_cover_roundtrip_and_discard_old_events(self):
        reader, writer = os.pipe()
        server = subprocess.Popen(['Xvfb', '-displayfd', str(writer), '-screen', '0', '320x240x24',
                                   '-nolisten', 'tcp'], pass_fds=(writer,),
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        os.close(writer)
        try:
            self.assertTrue(select.select([reader], [], [], 2.)[0], 'Xvfb display allocation')
            display = ':'+os.read(reader, 32).decode().strip()
            controller, probe = X11Probe(display), X11Probe(display)
            try:
                x, d = controller.x, controller.d
                x.XCreateSimpleWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int,
                    ctypes.c_int, ctypes.c_uint, ctypes.c_uint, ctypes.c_uint, ctypes.c_ulong, ctypes.c_ulong]
                x.XCreateSimpleWindow.restype = ctypes.c_ulong
                x.XMapWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
                x.XSetInputFocus.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
                windows = [x.XCreateSimpleWindow(d, x.XDefaultRootWindow(d), i*20, 0, 40, 40, 0, 0, 0)
                           for i in range(2)]
                for window in windows: x.XMapWindow(d, window)
                def focus(window):
                    x.XSetInputFocus(d, window, 1, 0)
                    x.XSync(d, False)
                target, other = map(str, windows)
                focus(windows[0])
                probe.begin(target)
                focus(windows[1]); focus(windows[0])  # queued OLD events
                stable = sample_input(probe, target)
                self.assertEqual(stable['focus_events'], [])
                self.assertEqual((stable['focused'], stable['grabbed'], stable['focused_after']),
                                 (target, False, target))
                self.assertIs(probe_pointer(display, time.monotonic()+1.), False)
                self.assertEqual(probe_input_state(display, target, time.monotonic()+1.)['focus_events'], [])
                real_grab = probe.grabbed
                changes = [True]
                def roundtrip():
                    if changes:
                        changes.pop()
                        focus(windows[1]); focus(windows[0])  # INSIDE sample
                    return real_grab()
                probe.grabbed = roundtrip
                changed = sample_input(probe, target)
                self.assertEqual((changed['focused'], changed['grabbed'], changed['focused_after']),
                                 (target, False, target))
                self.assertEqual([e['type'] for e in changed['focus_events']], ['FocusOut', 'FocusIn'])
                self.assertTrue(all(e['window'] == target and not e['synthetic']
                                    for e in changed['focus_events']))
            finally:
                probe.close(); controller.close()
        finally:
            os.close(reader)
            server.kill()
            server.wait(timeout=2.)


class PointerIsolationTests(unittest.TestCase):
    def launch_fake(self, body, *, budget=.3, startup_delay=0., interrupt=False, killed=False, max_elapsed=None, exec_error=False, interrupt_at=None, interrupt_occurrence=1):
        """Delay exec in CHILD, never hide an owned child inside a parent Popen."""
        real_exec, real_fork, real_wait = os.execve, os.fork, os.waitpid
        pids, statuses, pipes = [], [], []
        real_pipe = os.pipe2
        def pipe(flags):
            result = real_pipe(flags)
            pipes.extend(result)
            return result
        def fork():
            pid = real_fork()
            if pid:
                pids.append(pid)
                if interrupt:
                    # Exactly after child creation, before os.fork's caller has
                    # stored the return value. SIGINT must remain pending here.
                    os.kill(os.getpid(), signal.SIGINT)
            return pid
        def execute(path, args, env):
            if args[-2:] != ['--display', ':92'] or env['DISPLAY'] != ':92':
                os._exit(99)
            time.sleep(startup_delay)
            if exec_error: raise OSError('controlled exec failure')
            real_exec(sys.executable, [sys.executable, '-c', body], env)
        def wait(pid, flags):
            result = real_wait(pid, flags)
            if result[0]: statuses.append(result[1])
            return result
        # Real SIGINT at outer ownership stages and immediately BEFORE cleanup
        # enters. Every injection must observe the outer mask already in force.
        from contextlib import ExitStack
        parent_pid, injected, occurrences = os.getpid(), [], []
        def inject(fn):
            def call(*args, **kwargs):
                if os.getpid() == parent_pid:
                    occurrences.append(True)
                    if len(occurrences) == interrupt_occurrence:
                        self.assertIn(signal.SIGINT, signal.pthread_sigmask(signal.SIG_BLOCK, set()))
                        injected.append(True)
                        os.kill(parent_pid, signal.SIGINT)
                return fn(*args, **kwargs)
            return call
        started = REAL_MONOTONIC()
        with ExitStack() as stack:
            stack.enter_context(patch('tools.qianzhen.story_pointer.os.fork', fork))
            stack.enter_context(patch('tools.qianzhen.story_pointer.os.execve', execute))
            stack.enter_context(patch('tools.qianzhen.story_pointer.os.pipe2', pipe))
            stack.enter_context(patch('tools.qianzhen.story_pointer.os.waitpid', wait))
            if interrupt_at:
                module, attr = interrupt_at
                stack.enter_context(patch.object(module, attr, inject(getattr(module, attr))))
            try:
                return probe_pointer(':92', started+budget)
            finally:
                if interrupt_at: self.assertEqual(injected, [True])
                self.assertEqual(len(pids), 1)
                self.assertEqual(len(statuses), 1)
                if killed: self.assertEqual(os.waitstatus_to_exitcode(statuses[0]), -signal.SIGKILL)
                with self.assertRaises(ChildProcessError): real_wait(pids[0], os.WNOHANG)
                with self.assertRaises(ProcessLookupError): os.kill(pids[0], 0)
                with self.assertRaises(ProcessLookupError): os.killpg(pids[0], 0)
                for fd in pipes:
                    with self.assertRaises(OSError): os.fstat(fd)
                self.assertLess(REAL_MONOTONIC()-started, max_elapsed or budget+.25)

    def test_boolean_and_unavailable_results_survive_isolation(self):
        for value in (True, False, None):
            with self.subTest(value=value):
                self.assertIs(self.launch_fake('print('+repr(json.dumps(value))+')'), value)

    def test_twenty_second_probe_is_killed_and_reaped_inside_short_budget(self):
        with self.assertRaisesRegex(TimeoutError, 'Pointer probe exceeded'):
            self.launch_fake('import time; time.sleep(20); print("true")', killed=True)

    def test_never_returning_probe_ignoring_term_is_killed_and_reaped(self):
        with tempfile.TemporaryDirectory() as folder:
            started = Path(folder)/'started'
            body = ('import signal\nfrom pathlib import Path\n'
                    'signal.signal(signal.SIGTERM, signal.SIG_IGN)\n'
                    f'Path({str(started)!r}).write_text("entered")\n'
                    'while True: signal.pause()\n')
            with self.assertRaisesRegex(TimeoutError, 'Pointer probe exceeded'):
                self.launch_fake(body, killed=True)
            self.assertEqual(started.read_text(), 'entered')

    def test_startup_delay_point35_cannot_block_point2_budget(self):
        with self.assertRaises(TimeoutError):
            self.launch_fake('print("true")', budget=.2, startup_delay=.35, killed=True, max_elapsed=.2)

    def test_interrupt_after_fork_before_pid_assignment_is_deferred_then_reaped(self):
        with self.assertRaises(KeyboardInterrupt):
            self.launch_fake('import time; time.sleep(20)', interrupt=True, killed=True)

    def test_runtime_interrupt_and_other_exception_reap_owned_child(self):
        for error in (KeyboardInterrupt, RuntimeError('select failed')):
            with self.subTest(error=error), patch('tools.qianzhen.story_pointer.select.select', side_effect=error):
                with self.assertRaises(type(error) if isinstance(error, BaseException) else error):
                    self.launch_fake('import time; time.sleep(20)', killed=True)

    def test_real_sigint_at_cleanup_entry_and_outer_ownership_stages(self):
        import tools.qianzhen.story_pointer as pointer
        for position in ((pointer, '_kill_reap'), (os, 'setpgid'),
                         (os, 'set_blocking'), (select, 'select'), (os, 'read'),
                         (os, 'waitid'), (os, 'close'), (os, 'pipe2'),
                         (os, 'fork'), (os, 'killpg'), (os, 'waitpid')):
            with self.subTest(position=position[1]), self.assertRaises(KeyboardInterrupt):
                # Finite successful worker for late stages; SIGINT is delivered
                # only when the worker is reaped and both pipe FDs are closed.
                self.launch_fake('print("true")', interrupt_at=position)
        self.assertNotIn(signal.SIGINT, signal.pthread_sigmask(signal.SIG_BLOCK, set()))

    def test_real_sigint_before_cleanup_of_live_sleep_worker_and_during_fd_cleanup(self):
        import tools.qianzhen.story_pointer as pointer
        with self.assertRaises(KeyboardInterrupt):
            self.launch_fake('import time; time.sleep(20)', budget=.15, killed=True,
                             interrupt_at=(pointer, '_kill_reap'))
        with self.assertRaises(KeyboardInterrupt):
            self.launch_fake('print("true")', interrupt_at=(os, 'close'), interrupt_occurrence=2)
        # Two genuine interrupts in one ownership interval are both deferred.
        with self.assertRaises(KeyboardInterrupt):
            self.launch_fake('import time; time.sleep(20)', budget=.15, killed=True,
                             interrupt=True, interrupt_at=(pointer, '_kill_reap'))

    def test_pending_real_sigint_cannot_hide_cleanup_blocker(self):
        import tools.qianzhen.story_pointer as pointer
        def blocker(*args):
            self.assertIn(signal.SIGINT, signal.pthread_sigmask(signal.SIG_BLOCK, set()))
            os.kill(os.getpid(), signal.SIGINT)
            raise pointer.PointerCleanupBlocker('Pointer cleanup blocker: controlled unreaped pid')
        with patch.object(pointer, '_run_probe_blocked', blocker):
            with self.assertRaisesRegex(pointer.PointerCleanupBlocker, 'controlled unreaped'):
                probe_pointer(':92', time.monotonic()+.1)
        self.assertNotIn(signal.SIGINT, signal.pthread_sigmask(signal.SIG_BLOCK, set()))

    def test_creation_failure_cannot_signal_an_unowned_pid(self):
        with patch('tools.qianzhen.story_pointer.os.fork', side_effect=OSError('fork failed')), \
             patch('tools.qianzhen.story_pointer.os.killpg') as killpg, \
             patch('tools.qianzhen.story_pointer.os.kill') as kill:
            with self.assertRaisesRegex(OSError, 'fork failed'):
                probe_pointer(':92', time.monotonic()+.2)
        kill.assert_not_called(); killpg.assert_not_called()

    def test_exec_failure_after_creation_is_reaped(self):
        with self.assertRaisesRegex(RuntimeError, r'failed \(127\)'):
            self.launch_fake('print("true")', exec_error=True)

    def test_setpgid_failure_after_creation_still_reaps_direct_child(self):
        with patch('tools.qianzhen.story_pointer.os.setpgid', side_effect=OSError('controlled group failure')):
            with self.assertRaisesRegex(OSError, 'group failure'):
                self.launch_fake('import time; time.sleep(20)')

    def test_worker_crash_and_invalid_result_fail_closed_and_reap(self):
        for body, error in (('raise RuntimeError("fake Xlib failure")', RuntimeError),
                            ('print("42")', RuntimeError), ('print("garbage")', ValueError)):
            with self.subTest(body=body), self.assertRaises(error): self.launch_fake(body)

    def test_expired_budget_never_starts_worker(self):
        with patch('tools.qianzhen.story_pointer.os.fork') as fork:
            with self.assertRaises(TimeoutError): probe_pointer(':92', time.monotonic()-1.)
        fork.assert_not_called()

    def test_recovery_still_waits_when_logical_budget_already_exhausted(self):
        real_waitid, monotonic = os.waitid, time.monotonic
        offset = [0.]
        def late(*args):
            result = real_waitid(*args)
            if result is not None: offset[0] = 1.
            return result
        with patch('tools.qianzhen.story_pointer.os.waitid', late), \
             patch('tools.qianzhen.story_pointer.time.monotonic', side_effect=lambda: monotonic()+offset[0]):
            with self.assertRaisesRegex(TimeoutError, 'Late'):
                self.launch_fake('print("true")', budget=.3)

    def test_late_cleanup_must_really_wait_and_reap_then_reject_success(self):
        real_wait, real_waitid, monotonic = os.waitpid, os.waitid, time.monotonic
        offset, attempts = [0.], []
        def near_deadline(*args):
            result = real_waitid(*args)
            if result is not None: offset[0] = .15
            return result
        def delayed_reap(pid, flags):
            attempts.append(pid)
            offset[0] = .31
            if len(attempts) < 3:
                return (0, 0)
            return real_wait(pid, flags)
        with patch('tools.qianzhen.story_pointer.os.waitpid', delayed_reap), \
             patch('tools.qianzhen.story_pointer.os.waitid', near_deadline), \
             patch('tools.qianzhen.story_pointer.time.monotonic', side_effect=lambda: monotonic()+offset[0]):
            with self.assertRaisesRegex(TimeoutError, 'during reap'):
                self.launch_fake('print("true")')
        self.assertGreaterEqual(len(attempts), 3)

    def test_unreapable_kernel_condition_is_bounded_explicit_blocker(self):
        clock = [10.]
        def advance(seconds): clock[0] += seconds
        with patch('tools.qianzhen.story_pointer.os.killpg') as group, \
             patch('tools.qianzhen.story_pointer.os.kill') as child, \
             patch('tools.qianzhen.story_pointer.os.waitpid', return_value=(0, 0)), \
             patch('tools.qianzhen.story_pointer.time.monotonic', side_effect=lambda: clock[0]), \
             patch('tools.qianzhen.story_pointer.time.sleep', side_effect=advance):
            with self.assertRaisesRegex(RuntimeError, 'cleanup blocker.*12345.*not reaped'):
                _kill_reap(12345, 10.25)
        self.assertAlmostEqual(clock[0], 10.25)
        group.assert_called_once_with(12345, signal.SIGKILL)
        child.assert_called_once_with(12345, signal.SIGKILL)

    def test_timeout_does_not_signal_other_process_groups(self):
        sentinel = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(20)'],
                                    start_new_session=True)
        try:
            with self.assertRaises(TimeoutError):
                self.launch_fake('import time; time.sleep(20)', budget=.15, killed=True)
            self.assertIsNone(sentinel.poll())
            os.kill(sentinel.pid, 0)
        finally:
            sentinel.kill(); sentinel.wait(timeout=.5)

    def test_output_closed_without_exit_is_not_a_success(self):
        with self.assertRaisesRegex(TimeoutError, 'did not exit'):
            self.launch_fake('import os,time; os.close(1); os.close(2); time.sleep(20)', killed=True)


if __name__ == '__main__': unittest.main()
