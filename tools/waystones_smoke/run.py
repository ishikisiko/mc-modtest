"""在云端隔离世界检查可选加载、实际传送和灵石费用；不操作玩家存档。"""
from __future__ import annotations

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
sys.path.insert(0, str(ROOT / 'tools'))
from combat_capture.rcon import Rcon, RconError
from combat_capture.session import CLIENT_OPTIONS, merge_options
from combat_capture.xgame import Game


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def stop(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=25)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)
    return process.returncode


class Server:
    def __init__(self, out, optional):
        self.out, self.optional = out, optional
        self.game = out / 'server'
        self.password, self.sport, self.rport = secrets.token_hex(16), port(), port()
        self.commands = []

    def command(self, command):
        with Rcon('127.0.0.1', self.rport, self.password, timeout=45) as rcon:
            result = rcon.cmd(command)
        self.commands.append({'command': command, 'response': result})
        write(self.out / 'commands.json', self.commands)
        return result

    def query(self, action):
        response = self.command('waystonesprobe ' + action)
        if 'WAYSTONES_PROBE ' not in response:
            raise RuntimeError(response)
        return json.loads(response.split('WAYSTONES_PROBE ', 1)[1])

    def __enter__(self):
        self.game.mkdir(parents=True)
        props = {'server-ip': '127.0.0.1', 'server-port': self.sport, 'online-mode': 'false',
                 'enforce-secure-profile': 'false', 'enable-rcon': 'true', 'rcon.port': self.rport,
                 'rcon.password': self.password, 'broadcast-rcon-to-ops': 'false', 'level-name': 'world',
                 'level-type': 'minecraft\\:flat', 'generate-structures': 'false', 'spawn-protection': 0,
                 'generator-settings': '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
                 'difficulty': 'peaceful', 'allow-flight': 'true', 'view-distance': 3,
                 'simulation-distance': 3, 'max-tick-time': 180000, 'max-players': 2}
        (self.game / 'server.properties').write_text('\n'.join(f'{k}={v}' for k, v in props.items()) + '\n')
        (self.game / 'eula.txt').write_text('eula=true\n')
        if self.optional:
            (self.game / 'config').mkdir()
            (self.game / 'config/waystones-common.toml').write_bytes((ROOT / 'config-examples/waystones-common.toml').read_bytes())
        self.log = (self.out / 'server.log').open('w')
        args = ['./gradlew', '-I', 'tools/waystones_smoke/runtime.init.gradle', 'runAcceptanceServer',
                '-x', 'processResources', '--no-daemon', '--console=plain', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx3g']
        if self.optional:
            args += ['-Pwaystones_test', '-PwaystonesEvidence']
        self.process = subprocess.Popen(args, cwd=ROOT, env=dict(os.environ, MYVILLAGE_WAYSTONES_GAME=str(self.game)),
                                        stdout=self.log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + 600
            while time.monotonic() < deadline:
                if self.process.poll() is not None:
                    raise RuntimeError(f'Server exited: {self.process.returncode}')
                try:
                    if 'players online' in self.command('list'):
                        return self
                except (OSError, RconError):
                    pass
                time.sleep(3)
            raise TimeoutError('Server startup timeout')
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def __exit__(self, *args):
        try:
            self.command('stop')
            self.process.wait(timeout=45)
        except (OSError, RconError, subprocess.TimeoutExpired):
            stop(self.process)
        self.log.close()
        write(self.out / 'server-cleanup.json', {'exit_code': self.process.returncode, 'stopped': self.process.poll() is not None})


def client_checks(server):
    display = ':' + str(next(n for n in range(91, 120) if not Path(f'/tmp/.X11-unix/X{n}').exists()))
    game_dir = server.out / 'client'
    game_dir.mkdir()
    merge_options(game_dir / 'options.txt', {**CLIENT_OPTIONS, 'lang': 'zh_cn', 'guiScale': '2', 'maxFps': '20', 'renderDistance': '3'})
    xlog = (server.out / 'xvfb.log').open('w')
    clog = (server.out / 'client.log').open('w')
    xvfb = subprocess.Popen(['Xvfb', display, '-screen', '0', '960x540x24', '+extension', 'GLX', '+render', '-noreset'],
                            stdout=xlog, stderr=subprocess.STDOUT, start_new_session=True)
    client = None
    result = {'status': 'running', 'mapped_payment': 'unrun', 'developer_failure_probe': 'unrun',
              'owner_appearance': 'not_verified', 'fps': 'not_verified', 'multiplayer': 'not_verified'}
    try:
        time.sleep(2)
        args = ['./gradlew', 'runClient', '-x', 'processResources',
                '--no-daemon', '--console=plain', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx2g',
                f'-Pcombat_smoke_game_dir={game_dir}', '-Pcombat_smoke_username=WaystoneBagTest',
                f'-Pcombat_smoke_server=127.0.0.1:{server.sport}']
        if server.optional:
            args += ['-Pwaystones_test', '-PwaystonesEvidence']
        client = subprocess.Popen(args, cwd=ROOT, env=dict(os.environ, DISPLAY=display, LIBGL_ALWAYS_SOFTWARE='1', LP_NUM_THREADS='2'),
                                  stdout=clog, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.monotonic() + 600
        while 'WaystoneBagTest' not in server.command('list'):
            text = (server.out / 'client.log').read_text(errors='replace')
            if 'NoClassDefFoundError' in text or 'Description: Mod loading failures' in text:
                raise RuntimeError('Native client mod loading failed; see client.log')
            if client.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Client did not join')
            time.sleep(3)
        game = Game(display, game_dir / 'logs/latest.log', (960, 540))
        if not (game.wait_ingame(60, poll=1) or game.recover_grab()):
            raise RuntimeError('Client did not reach in-game view')
        if not server.optional:
            result['status'] = 'passed'
            result['optional_absent_client_boot'] = 'passed'
            return
        time.sleep(4)
        huge = 10 ** 40 + 17
        result['before'] = server.query(f'fixture WaystoneBagTest {huge}')
        assert result['before']['can_afford'] is True, result['before']
        assert result['before']['requirement_class'].endswith('.BagAwareItemRequirement'), result['before']
        time.sleep(3)
        game.require_ingame('right click source Waystone')
        game.xdo('click', '3')
        time.sleep(3)
        game.shot(server.out / 'paid-menu.png')
        assert game.pointer_grabbed() is False, 'Waystones selection menu did not open'
        result['clicks'] = []
        for y in [216, 264, 312]:
            game.xdo('mousemove', '480', str(y), 'click', '1')
            result['clicks'].append([480, y])
            time.sleep(3)
            after = server.query('status WaystoneBagTest')
            if after['x'] > 60:
                break
        else:
            game.shot(server.out / 'failed-selection.png')
            raise AssertionError('Mapped selection did not teleport the player')
        result['after'] = after
        assert int(after['balance']) == huge - 1, after
        result['mapped_payment'] = 'passed'
        game.shot(server.out / 'after-payment.png')
        result['failure_probes'] = server.query('failure WaystoneBagTest')
        assert all(result['failure_probes'].get(key) is True for key in
                   ['sync_failure_refund', 'repeated_completion', 'insufficient_funds', 'ordinary_item_cost'])
        result['developer_failure_probe'] = 'passed'
        result['status'] = 'passed'
    except BaseException as error:
        result['status'], result['error'], result['traceback'] = 'failed', repr(error), traceback.format_exc()
        raise
    finally:
        if client:
            result['client_exit_code'] = stop(client)
        result['xvfb_exit_code'] = stop(xvfb)
        xlog.close()
        clog.close()
        write(server.out / 'client-result.json', result)


def main():
    if os.environ.get('MYVILLAGE_CLOUD_CHECK') != 'waystones':
        raise SystemExit('Set MYVILLAGE_CLOUD_CHECK=waystones in the isolated cloud checkout')
    out = Path(sys.argv[1]).resolve()
    if out.exists():
        raise SystemExit('Use a new evidence directory; previous failures must be retained')
    out.mkdir(parents=True)
    report = {'status': 'running', 'without_waystones': 'unrun', 'with_waystones': 'unrun'}
    try:
        with Server(out / 'without', False) as server:
            client_checks(server)
            report['without_waystones'] = 'passed'
        with Server(out / 'with', True) as server:
            client_checks(server)
            report['with_waystones'] = 'passed'
        report['status'] = 'passed'
    except BaseException as error:
        report.update(status='failed', error=repr(error), traceback=traceback.format_exc())
        raise
    finally:
        write(out / 'result.json', report)


if __name__ == '__main__':
    main()
