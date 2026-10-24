"""受控入口公共接线：录像首帧、严格结果层次与可选下界启动。

不改通用session/生产。下界只在本入口的独立supervisor启动服务器前启用，
仍使用原Supervisor的锁、备份与清理；不在运行中重写全世界配置。
"""
from __future__ import annotations

from dataclasses import asdict
import json
from pathlib import Path
import subprocess
import sys
import time


def start_recording(game, path, deadline):
    from tools.combat_capture.capture import ffmpeg_record_argv
    from tools.qianzhen_opening_run import wait_recorded_frame
    progress = path.with_suffix('.progress.log')
    log = path.with_suffix('.record.log').open('xb')
    argv = ffmpeg_record_argv(game.display, path, max(1, deadline - time.monotonic()), size=game.size)
    argv[1:1] = ['-progress', str(progress), '-stats_period', '0.2']
    process = None
    try:
        process = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=log, stderr=log)
        wait_recorded_frame(process, progress)
        game.save_png(game.grab_raw(), path.with_suffix('.startup.png'))
        return process, log, path
    except BaseException:
        try:
            game.save_png(game.grab_raw(), path.with_suffix('.startup-failure.png'))
        except BaseException:
            pass  # Original startup/first-frame failure remains the exception.
        if process is not None and process.poll() is None:
            process.kill()
            process.wait(timeout=3)
        log.close()
        raise


def finalize_levels(trace):
    """Attempted failures are explicit; captured never implies accepted/completed."""
    trace['captured'] = [r['id'] for r in trace.get('stages', []) if r['status'] == 'captured' and r['id'] in trace['selected']]
    trace['completed'] = []
    trace['failed'] = list(dict.fromkeys(trace.get('failed', []) +
                           [r['id'] for r in trace.get('stages', []) if r['status'] == 'failed' and r['id'] in trace['selected']]))
    attempted = set(trace['captured']) | set(trace['failed']) | set(trace.get('attempted', []))
    trace['unrun'] = [name for name in trace['selected'] if name not in attempted]
    trace['pending_acceptance'] = trace['captured'].copy()
    return trace


def start_session(cfg, *, allow_nether=False, wait_timeout=1350, log=lambda _: None):
    from tools.combat_capture import session as cs, procs
    if not allow_nether:
        return cs.start(cfg, wait_timeout=wait_timeout, log=log)
    old = cs.read_state()
    if cs.supervisor_alive(old) or (old and any(procs.is_alive(p['pid'], p.get('start')) for p in old.get('procs', {}).values())):
        raise RuntimeError('已有会话/残留进程，拒绝下界启动')
    cs.restore_server_properties(log)
    cs.RUN_DIR.mkdir(parents=True, exist_ok=True)
    cs.LOG_DIR.mkdir(parents=True, exist_ok=True)
    cs.STATE_FILE.unlink(missing_ok=True)
    with (cs.LOG_DIR / 'supervisor.log').open('ab') as output:
        process = subprocess.Popen([sys.executable, '-m', 'tools.qianzhen_opening_extended_integration',
                                    '--supervise-nether', json.dumps(asdict(cfg))], cwd=cs.REPO,
                                   stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    deadline = time.monotonic() + wait_timeout
    try:
        while time.monotonic() < deadline:
            state = cs.read_state()
            if state and state.get('session_id') == cfg.session_id:
                if state['phase'] == 'ready':
                    if state.get('allow_nether') is not True:
                        raise ValueError('实际server未启用下界')
                    return state
                if state['phase'] in ('failed', 'stopped'):
                    raise RuntimeError('下界supervisor失败: ' + str(state.get('error')))
            if process.poll() is not None:
                raise RuntimeError('下界supervisor提前退出: ' + str(process.returncode))
            time.sleep(2)
        raise TimeoutError('下界session启动到期')
    except BaseException:
        from tools.qianzhen_opening_run import stop_owned
        # Only the freshly launched supervisor, never another session.
        current = cs.read_state()
        if current and current.get('session_id') == cfg.session_id:
            stop_owned(cs.REPO, cfg.session_id)
        elif process.poll() is None:
            process.terminate()
            process.wait(timeout=10)
        raise


def supervise_nether(payload):
    from tools.combat_capture import session as cs
    from tools.qianzhen.evidence_candidate import assert_cloud
    assert_cloud(cs.REPO, 'Colab')
    class NetherSupervisor(cs.Supervisor):
        def write_server_config(self):
            super().write_server_config()
            props = cs.SERVER_DIR / 'server.properties'
            text = props.read_text()
            if text.count('allow-nether=false\n') != 1:
                raise ValueError('服务器配置下界项不唯一，拒绝替换')
            props.write_text(text.replace('allow-nether=false\n', 'allow-nether=true\n'))
            self.state['allow_nether'] = True
            cs.write_state(self.state)
    return NetherSupervisor(cs.SessionConfig(**json.loads(payload))).run()


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[1] != '--supervise-nether':
        raise SystemExit('仅供固定入口的显式下界supervisor调用')
    raise SystemExit(supervise_nether(sys.argv[2]))
