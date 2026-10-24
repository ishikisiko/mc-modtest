"""Real short-lived OS probes; no X server, game or twenty-second test sleeps."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from tools.qianzhen.story_pointer import probe_pointer


class PointerIsolationTests(unittest.TestCase):
    def launch_fake(self, body, *, budget=.6, spawn_delay=0., killed=False):
        """Replace only worker contents; use real spawn/pipe/kill/waitpid."""
        real_popen = subprocess.Popen
        processes = []
        def popen(command, **kwargs):
            self.assertEqual(command[-2:], ['--display', ':92'])
            self.assertEqual(kwargs['env']['DISPLAY'], ':92')
            self.assertTrue(kwargs['start_new_session'])
            process = real_popen([sys.executable, '-c', body], **kwargs)
            processes.append(process)
            time.sleep(spawn_delay)
            return process
        started = time.monotonic()
        with patch('tools.qianzhen.story_pointer.subprocess.Popen', side_effect=popen):
            try:
                return probe_pointer(':92', started+budget)
            finally:
                self.assertEqual(len(processes), 1)
                process = processes[0]
                self.assertIsNotNone(process.returncode)
                if killed: self.assertEqual(process.returncode, -signal.SIGKILL)
                # It was reaped, not merely signalled or left as a zombie.
                with self.assertRaises(ChildProcessError): os.waitpid(process.pid, os.WNOHANG)
                with self.assertRaises(ProcessLookupError): os.kill(process.pid, 0)
                with self.assertRaises(ProcessLookupError): os.killpg(process.pid, 0)
                self.assertTrue(process.stdout.closed)
                self.assertTrue(process.stderr.closed)
                self.assertLess(time.monotonic()-started, budget)

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

    def test_spawn_overhead_reduces_wait_budget(self):
        for delay in (.3, .5):
            with self.subTest(delay=delay), self.assertRaises(TimeoutError):
                self.launch_fake('import time; time.sleep(20)', spawn_delay=delay, killed=True)

    def test_interruption_still_kills_and_reaps_owned_probe(self):
        with patch.object(subprocess.Popen, 'communicate', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                self.launch_fake('import time; time.sleep(20)', killed=True)

    def test_worker_crash_and_invalid_result_fail_closed_and_reap(self):
        for body, error in (('raise RuntimeError("fake Xlib failure")', RuntimeError),
                            ('print("42")', RuntimeError), ('print("garbage")', ValueError)):
            with self.subTest(body=body), self.assertRaises(error): self.launch_fake(body)

    def test_expired_budget_never_starts_worker(self):
        with patch('tools.qianzhen.story_pointer.subprocess.Popen') as popen:
            with self.assertRaises(TimeoutError): probe_pointer(':92', time.monotonic()-1.)
        popen.assert_not_called()

    def test_late_success_is_rejected_and_reaped(self):
        real_communicate = subprocess.Popen.communicate
        def late(process, **kwargs):
            output = real_communicate(process, **kwargs)
            # Cross the deadline deterministically without a real long wait.
            clock.return_value = deadline+1.
            return output
        deadline = time.monotonic()+2.
        with patch('tools.qianzhen.story_pointer.time.monotonic', return_value=deadline-1.) as clock:
            with patch.object(subprocess.Popen, 'communicate', late):
                with self.assertRaisesRegex(TimeoutError, 'deadline|Late'):
                    probe_pointer(':92', deadline)


if __name__ == '__main__': unittest.main()
