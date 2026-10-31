"""在隔离的新世界运行 RoadWeaver 原包桥接实验。仅在指定云端环境执行。"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from combat_capture.rcon import Rcon
from roadweaver_probe.audit import summarize


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix+'.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')
    temporary.replace(path)


def port():
    with socket.socket() as connection:
        connection.bind(('127.0.0.1', 0))
        return connection.getsockname()[1]


def terminate(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=25)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)
    return {'pid': process.pid, 'returncode': process.returncode, 'terminated': process.poll() is not None}


class ExperimentServer:
    def __init__(self, out, mode, jar, config, source, runtime_jar=None, observe_game=None):
        self.out, self.mode, self.jar, self.config, self.source = out, mode, jar, config, source
        self.game = ROOT/'run-roadweaver'/f'{mode.lower()}-{secrets.token_hex(4)}'
        self.password, self.rport, self.sport = secrets.token_hex(20), port(), port()
        self.commands = []
        self.process = None
        self.runtime_jar = runtime_jar
        self.observe = observe_game is not None
        if observe_game:
            self.game = observe_game.resolve()
            if not self.game.is_relative_to(ROOT/'run-roadweaver') or not (self.game/'world/level.dat').is_file():
                raise ValueError('Observation accepts only an existing world owned by this experiment')

    def command(self, command):
        with Rcon('127.0.0.1', self.rport, self.password, timeout=180) as connection:
            response = connection.cmd(command)
        self.commands.append({'command': command, 'response': response})
        return response

    def query(self, action):
        response = self.command('roadprobe '+action)
        if 'ROADPROBE ' not in response:
            raise RuntimeError(response)
        return json.loads(response.split('ROADPROBE ', 1)[1])

    def wait_phase(self, expected, seconds):
        deadline = time.monotonic()+seconds
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError('Server exited: '+str(self.process.returncode))
            status = self.query('status')
            if status['phase'] == 'failed':
                raise RuntimeError('Probe failed; see probe.json')
            if status['phase'] in expected:
                return status
            write(self.out/'progress.json', status)
            time.sleep(3)
        raise TimeoutError('Phase timeout: '+str(status))

    def __enter__(self):
        self.out.mkdir(parents=True, exist_ok=True)
        self.game.mkdir(parents=True, exist_ok=self.observe)
        config = json.loads(self.config.read_text())
        values = {
            'server-ip': '127.0.0.1', 'server-port': self.sport, 'online-mode': 'false',
            'enforce-secure-profile': 'false', 'enable-rcon': 'true', 'rcon.port': self.rport,
            'rcon.password': self.password, 'broadcast-rcon-to-ops': 'false', 'enable-query': 'false',
            'level-name': 'world', 'level-seed': config['seed'], 'level-type': 'myvillage\\:cultivation',
            'generate-structures': 'true', 'gamemode': 'creative', 'difficulty': 'peaceful',
            'spawn-protection': 0, 'allow-flight': 'true', 'view-distance': 4,
            'simulation-distance': 3, 'max-tick-time': 180000, 'max-players': 2,
            'sync-chunk-writes': 'true', 'pause-when-empty-seconds': -1,
        }
        (self.game/'server.properties').write_text('\n'.join(f'{key}={value}' for key, value in values.items())+'\n')
        (self.game/'eula.txt').write_text('eula=true\n')
        self.env = dict(os.environ, MYVILLAGE_ROAD_PROBE='1', MYVILLAGE_ROAD_PROBE_MODE=self.mode,
                        MYVILLAGE_ROAD_PROBE_JAR=str(self.jar), MYVILLAGE_ROAD_PROBE_CONFIG=str(self.config),
                        MYVILLAGE_ROAD_PROBE_GAME=str(self.game),
                        MYVILLAGE_ROAD_PROBE_OUT=str(self.out/'lifecycle' if self.observe else self.out),
                        MYVILLAGE_ROAD_PROBE_SOURCE=self.source)
        args = ['./gradlew', '-I', 'tools/roadweaver_probe/probe.init.gradle', 'runAcceptanceServer',
                '-x', 'generateAllStructures', '--no-daemon', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx5g']
        if self.runtime_jar:
            self.env['MYVILLAGE_ROAD_PROBE_RUNTIME_JAR'] = str(self.runtime_jar)
            args += ['-x', 'processResources']
        self.log = (self.out/'server.log').open('w')
        self.process = subprocess.Popen(args, cwd=ROOT, env=self.env, stdin=subprocess.DEVNULL,
                                        stdout=self.log, stderr=subprocess.STDOUT, start_new_session=True)
        write(self.out/'launch.json', {'pid': self.process.pid, 'game_directory': str(self.game),
                                      'source_commit': self.source, 'mode': self.mode, 'args': args})
        try:
            deadline = time.monotonic()+600
            while time.monotonic() < deadline:
                if self.process.poll() is not None:
                    raise RuntimeError('Server startup exit '+str(self.process.returncode))
                try:
                    self.query('status')
                    break
                except (OSError, RuntimeError, ValueError):
                    time.sleep(3)
            else:
                raise TimeoutError('Server did not become ready')
            for command in ['gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
                            'gamerule doMobSpawning false', 'gamerule randomTickSpeed 0', 'time set noon', 'weather clear']:
                self.command(command)
            return self
        except BaseException:
            self.__exit__(*sys.exc_info())
            raise

    def __exit__(self, *_):
        if self.process and self.process.poll() is None:
            try:
                self.command('save-all flush')
                self.command('stop')
                self.process.wait(timeout=60)
            except Exception:
                pass
        cleanup = terminate(self.process) if self.process else {'terminated': True}
        if hasattr(self, 'log'):
            self.log.close()
        write(self.out/'cleanup.json', cleanup)
        write(self.out/'commands.json', self.commands)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--mode', choices=['COARSE_CORRIDOR', 'FULL_REGION'])
    parser.add_argument('--capture', action='store_true', help='启动真实客户端采集城门与道路观察图')
    parser.add_argument('--runtime-jar', type=Path, help='复用已核对固定源的发布资源，并强制逐文件检查运行目录')
    args = parser.parse_args()
    args.jar, args.out = args.jar.resolve(), args.out.resolve()
    config_path = Path(__file__).with_name('experiment.json')
    config = json.loads(config_path.read_text())
    actual = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    if actual != config['roadweaver_sha256']:
        raise SystemExit('RoadWeaver jar hash mismatch')
    modes = [args.mode] if args.mode else config['modes']
    result = {'selected': modes, 'completed': [], 'failed': [], 'unrun': modes.copy(),
              'source_commit': args.source, 'roadweaver_sha256': actual,
              'kind': 'developer generation experiment; no mapped-input player acceptance'}
    write(args.out/'result.json', result)
    for mode in modes:
        directory = args.out/mode.lower()
        try:
            with ExperimentServer(directory, mode, args.jar, config_path, args.source,
                                  args.runtime_jar.resolve() if args.runtime_jar else None) as server:
                server.query('prepare')
                server.query('generate')
                state = server.wait_phase({'generated', 'no_road'}, config['generation_timeout_seconds'])
                if state['phase'] == 'generated':
                    server.query('bake')
                    server.wait_phase({'audited'}, config['audit_timeout_seconds'])
                probe = json.loads((directory/'probe.json').read_text())
                roads = json.loads((directory/'roads.json').read_text())
                columns = json.loads((directory/'columns.json').read_text()) if (directory/'columns.json').exists() else []
                audit = summarize(probe, roads, columns)
                write(directory/'audit.json', audit)
                if args.capture and columns:
                    from roadweaver_probe.capture import capture
                    capture(server, ROOT, terminate)
                result['completed'].append(mode)
                result['unrun'].remove(mode)
                if not audit['pavement_connectivity_pass']:
                    result['failed'].append({'mode': mode, 'reason': 'geometry_not_passed', 'phase': probe['phase']})
        except Exception as error:
            result['failed'].append({'mode': mode, 'error': repr(error), 'traceback': traceback.format_exc()})
        write(args.out/'result.json', result)
        print(json.dumps({key: value for key, value in result.items() if key != 'roadweaver_sha256'}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
