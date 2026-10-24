"""剧情专用 X11 隔离；目标窗口焦点事件与 grab 在同一连接/边界内采样。"""
from __future__ import annotations

import argparse
import ctypes
import errno
import json
import os
from pathlib import Path
import select
import signal
import sys
import time

# Logical work ends before the input deadline. Even a late exception receives a
# finite reap opportunity, rather than wait(timeout=0). No unbounded wait exists.
REAP_SECONDS = .25
MAX_OUTPUT = 65536
BOUNDARY = 'x11-focus-events-v1'


def _kill_reap(pid, deadline):
    """PID is an unreaped direct child: neither it nor its PGID can be reused."""
    # Defer a second Ctrl-C until the owned child is reaped.
    mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT})
    try:
        for send in (os.killpg, os.kill):
            try:
                send(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        while True:
            try:
                found, _ = os.waitpid(pid, os.WNOHANG)
            except InterruptedError:
                continue
            if found == pid:
                return
            if time.monotonic() >= deadline:
                raise RuntimeError(f'Pointer cleanup blocker: owned pid {pid} not reaped within bounded grace')
            time.sleep(min(.002, max(0., deadline-time.monotonic())))
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, mask)


def _run_probe(display, deadline, window=None):
    remaining = deadline-time.monotonic()
    if remaining <= 0:
        raise TimeoutError('Pointer probe deadline exhausted before spawn')
    work_deadline = deadline-min(REAP_SECONDS, remaining*.2)
    command = [sys.executable, '-m', 'tools.qianzhen.story_pointer', '--display', display]
    if window is not None:
        command += ['--window', str(window)]
    env = dict(os.environ, DISPLAY=display)
    cwd = str(Path(__file__).resolve().parents[2])
    pid = None
    reader = writer = None
    mask = None
    completed = False
    try:
        # Unlike Popen._execute_child, fork returns without waiting for exec's
        # error pipe. Block SIGINT over creation + PID assignment; the pending
        # handler runs only once the enclosing finally owns the child PID.
        mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT})
        reader, writer = os.pipe2(os.O_CLOEXEC)
        pid = os.fork()
        if pid == 0:
            try:
                os.setpgid(0, 0)
                os.close(reader)
                os.dup2(writer, 1)
                os.dup2(writer, 2)
                os.close(writer)
                signal.pthread_sigmask(signal.SIG_SETMASK, mask)
                os.chdir(cwd)
                os.execve(command[0], command, env)
            except BaseException:
                os._exit(127)
        # Parent and child both establish the same dedicated group. If exec won
        # the race, EACCES is expected; the child has already set its PGID.
        try:
            os.setpgid(pid, pid)
        except OSError as error:
            if error.errno not in (errno.EACCES, errno.ESRCH):
                raise
        os.close(writer)
        writer = None
        os.set_blocking(reader, False)
        previous_mask, mask = mask, None
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
        output = bytearray()
        while True:
            budget = work_deadline-time.monotonic()
            if budget <= 0:
                raise TimeoutError('Pointer probe exceeded story input deadline (startup included)')
            readable, _, _ = select.select([reader], [], [], budget)
            if not readable:
                raise TimeoutError('Pointer probe exceeded story input deadline (startup included)')
            chunk = os.read(reader, 4096)
            if chunk:
                output.extend(chunk)
                if len(output) > MAX_OUTPUT:
                    raise RuntimeError('Pointer probe output limit exceeded')
                continue
            # EOF is not enough: also require process exit under the work budget.
            while True:
                # Inspect without reaping: the PID stays owned even if a
                # Python signal arrives immediately after this syscall.
                status = os.waitid(os.P_PID, pid, os.WEXITED | os.WNOHANG | os.WNOWAIT)
                if status is not None:
                    break
                if time.monotonic() >= work_deadline:
                    raise TimeoutError('Pointer probe did not exit after closing output')
                time.sleep(.002)
            if time.monotonic() >= work_deadline:
                raise TimeoutError('Late pointer probe result rejected')
            if status.si_code != os.CLD_EXITED or status.si_status:
                raise RuntimeError(f'Pointer probe failed ({status.si_status}): '
                                   +output.decode(errors='replace').strip())
            result = json.loads(output)
            completed = True
            return result
    finally:
        try:
            if pid is not None and pid > 0:
                # Normally fits the reserved time. An already late exception
                # still receives at most .25s of recovery; it cannot turn into
                # logical success. A kernel that cannot reap yields a blocker.
                _kill_reap(pid, time.monotonic()+REAP_SECONDS)
        finally:
            for fd in (reader, writer):
                if fd is not None:
                    os.close(fd)
            if mask is not None:
                signal.pthread_sigmask(signal.SIG_SETMASK, mask)
        if completed and time.monotonic() >= deadline:
            raise TimeoutError('Pointer probe deadline exhausted during reap')


def probe_pointer(display: str, deadline: float):
    """Compatibility bool API; cannot establish target-window menu ownership."""
    result = _run_probe(display, deadline)
    if result is not None and type(result) is not bool:
        raise RuntimeError('Invalid pointer probe result')
    return result


def probe_input_state(display: str, window: str, deadline: float):
    result = _run_probe(display, deadline, window)
    if (not isinstance(result, dict) or result.get('boundary') != BOUNDARY
            or result.get('window') != str(window)
            or not all(isinstance(result.get(key), str) for key in ('focused', 'focused_after'))
            or (result.get('grabbed') is not None and type(result.get('grabbed')) is not bool)
            or not isinstance(result.get('focus_events'), list)
            or any(not isinstance(event, dict) or event.get('type') not in ('FocusIn', 'FocusOut')
                   or event.get('window') != str(window) for event in result['focus_events'])):
        raise RuntimeError('Invalid focus-event probe result')
    return result


class _FocusEvent(ctypes.Structure):
    _fields_ = [('type', ctypes.c_int), ('serial', ctypes.c_ulong),
                ('send_event', ctypes.c_int), ('display', ctypes.c_void_p),
                ('window', ctypes.c_ulong), ('mode', ctypes.c_int), ('detail', ctypes.c_int)]


class _Event(ctypes.Union):
    _fields_ = [('type', ctypes.c_int), ('focus', _FocusEvent), ('pad', ctypes.c_long*24)]


class X11Probe:
    def __init__(self, display):
        x = self.x = ctypes.CDLL('libX11.so.6')
        signatures = {
            'XOpenDisplay': ([ctypes.c_char_p], ctypes.c_void_p),
            'XDefaultRootWindow': ([ctypes.c_void_p], ctypes.c_ulong),
            'XSelectInput': ([ctypes.c_void_p, ctypes.c_ulong, ctypes.c_long], ctypes.c_int),
            'XSync': ([ctypes.c_void_p, ctypes.c_int], ctypes.c_int),
            'XGetInputFocus': ([ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong),
                               ctypes.POINTER(ctypes.c_int)], ctypes.c_int),
            'XGrabPointer': ([ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_uint,
                              ctypes.c_int, ctypes.c_int, ctypes.c_ulong, ctypes.c_ulong,
                              ctypes.c_ulong], ctypes.c_int),
            'XUngrabPointer': ([ctypes.c_void_p, ctypes.c_ulong], ctypes.c_int),
            'XPending': ([ctypes.c_void_p], ctypes.c_int),
            'XNextEvent': ([ctypes.c_void_p, ctypes.POINTER(_Event)], ctypes.c_int),
            'XCloseDisplay': ([ctypes.c_void_p], ctypes.c_int),
        }
        for name, (args, result) in signatures.items():
            getattr(x, name).argtypes, getattr(x, name).restype = args, result
        self.d = x.XOpenDisplay(display.encode())
        if not self.d:
            raise RuntimeError('Cannot open actor X display')

    def begin(self, window):
        self.x.XSelectInput(self.d, int(window), 1 << 21)  # FocusChangeMask
        # Establish subscription on the server and discard startup/old events.
        # Nothing is discarded after this boundary until the sample is complete.
        self.x.XSync(self.d, True)

    def focus(self):
        window, revert = ctypes.c_ulong(), ctypes.c_int()
        self.x.XGetInputFocus(self.d, ctypes.byref(window), ctypes.byref(revert))
        return str(window.value)

    def grabbed(self):
        rc = self.x.XGrabPointer(self.d, self.x.XDefaultRootWindow(self.d), 0, 0, 1, 1, 0, 0, 0)
        if rc == 0:
            self.x.XUngrabPointer(self.d, 0)
            return False
        return True if rc == 1 else None

    def events(self, window):
        self.x.XSync(self.d, False)  # Include every focus event through final focus read.
        result = []
        while self.x.XPending(self.d):
            event = _Event()
            self.x.XNextEvent(self.d, ctypes.byref(event))
            if event.type in (9, 10) and event.focus.window == int(window):
                result.append(dict(type='FocusIn' if event.type == 9 else 'FocusOut',
                                   window=str(event.focus.window), mode=event.focus.mode,
                                   detail=event.focus.detail, synthetic=bool(event.focus.send_event)))
        return result

    def close(self):
        self.x.XCloseDisplay(self.d)


def sample_input(probe, window):
    """Single server connection, subscription boundary, no endpoint-only inference."""
    probe.begin(window)
    focused = probe.focus()
    grabbed = probe.grabbed()
    focused_after = probe.focus()
    events = probe.events(window)
    return dict(boundary=BOUNDARY, window=str(window), focused=focused,
                grabbed=grabbed, focused_after=focused_after, focus_events=events)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--display', required=True)
    parser.add_argument('--window')
    args = parser.parse_args()
    probe = X11Probe(args.display)
    try:
        result = sample_input(probe, args.window) if args.window else probe.grabbed()
    finally:
        probe.close()  # Blocking close is also inside the parent's work budget.
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
