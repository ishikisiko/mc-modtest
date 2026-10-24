"""剧情专用 Xlib 隔离：只探测给定 display，不发送玩家输入或继承 X 连接。"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def probe_pointer(display: str, deadline: float):
    """Use the caller's absolute deadline, including spawn and kill/reap.

    The fresh interpreter opens/closes its own X connection and never spawns
    children. Its grab is display-global; the caller must check window focus
    and obtain the actor's Rotation receipt separately. No shared Game API or
    alchemy/combat capture behavior changes here.
    """
    remaining = deadline-time.monotonic()
    if remaining <= 0:
        raise TimeoutError('Pointer probe deadline exhausted before spawn')
    # Reserve part of the SAME budget for SIGKILL and waitpid, not extra time
    # after timeout. A blocked XCloseDisplay must be killable as well.
    cleanup = min(.25, remaining*.2)
    work_deadline = deadline-cleanup
    process = subprocess.Popen(
        [sys.executable, '-m', 'tools.qianzhen.story_pointer', '--display', display],
        cwd=Path(__file__).resolve().parents[2], env=dict(os.environ, DISPLAY=display),
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, start_new_session=True)
    try:
        budget = work_deadline-time.monotonic()
        if budget <= 0:
            raise TimeoutError('Pointer probe deadline exhausted during spawn')
        try:
            output, error = process.communicate(timeout=budget)
        except subprocess.TimeoutExpired as exc:
            raise TimeoutError('Pointer probe exceeded story input deadline') from exc
        if time.monotonic() >= work_deadline:
            raise TimeoutError('Late pointer probe result rejected')
        if process.returncode:
            raise RuntimeError(f'Pointer probe failed ({process.returncode}): {error.strip()}')
        result = json.loads(output)
        if result is not None and type(result) is not bool:
            raise RuntimeError('Invalid pointer probe result')
        return result
    finally:
        try:
            if process.poll() is None:
                # Only this newly created probe session is ours. Never signal
                # the Minecraft PID, X server, or another actor's display.
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            process.wait(timeout=max(0., deadline-time.monotonic()))
        finally:
            process.stdout.close()
            process.stderr.close()
        if time.monotonic() >= deadline:
            raise TimeoutError('Pointer probe deadline exhausted during reap')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--display', required=True)
    args = parser.parse_args()
    from tools.combat_capture.xgame import Game
    # Exactly one Xlib probe; Game stores no pre-existing X connection.
    print(json.dumps(Game(args.display, Path('/dev/null')).pointer_grabbed()))


if __name__ == '__main__':
    main()
