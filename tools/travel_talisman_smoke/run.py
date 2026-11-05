"""云端固定源的制符、传送与存档检查。RCON 只准备夹具和读回；施符由映射输入完成。"""
from __future__ import annotations

import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.waystones_smoke.run import port, stop, write
from tools.combat_capture.rcon import Rcon, RconError
from tools.combat_capture.session import CLIENT_OPTIONS, merge_options
from tools.combat_capture.xgame import Game

GRADLE = ['--no-daemon', '--console=plain', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx2g']
A, B = 'TravelTalismanA', 'TravelTalismanB'


class Server:
    def __init__(self, out, optional, resume=None):
        self.out, self.optional, self.resume = out, optional, resume
        self.game = out / 'server'
        self.password, self.sport, self.rport = secrets.token_hex(16), port(), port()
        self.commands = []

    def command(self, command):
        with Rcon('127.0.0.1', self.rport, self.password, timeout=45) as rcon:
            response = rcon.cmd(command)
        self.commands.append({'command': command, 'response': response})
        write(self.out / 'commands.json', self.commands)
        return response

    def query(self, action='status', player=A):
        text = self.command(f'travelprobe {action} {player}')
        if 'TRAVEL_PROBE ' not in text:
            raise AssertionError(text)
        return json.loads(text.split('TRAVEL_PROBE ', 1)[1])

    def desk(self):
        text = self.command(f'myvillage talisman status {A} 0 80 -3')
        assert 'TALISMAN_STATUS ' in text, text
        return json.loads(text.split('TALISMAN_STATUS ', 1)[1])

    def __enter__(self):
        self.game.mkdir(parents=True)
        if self.resume:
            shutil.copytree(self.resume, self.game / 'world')
        props = {'server-ip': '127.0.0.1', 'server-port': self.sport, 'online-mode': 'false',
                 'enforce-secure-profile': 'false', 'enable-rcon': 'true', 'rcon.port': self.rport,
                 'rcon.password': self.password, 'broadcast-rcon-to-ops': 'false', 'level-name': 'world',
                 'level-type': 'minecraft\\:flat', 'generate-structures': 'false', 'spawn-protection': 0,
                 'generator-settings': '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}',
                 'difficulty': 'peaceful', 'allow-flight': 'true', 'view-distance': 3,
                 'simulation-distance': 3, 'max-tick-time': 180000, 'max-players': 2}
        (self.game / 'server.properties').write_text('\n'.join(f'{k}={v}' for k, v in props.items()) + '\n')
        (self.game / 'eula.txt').write_text('eula=true\n')
        self.log = (self.out / 'server.log').open('w')
        args = ['./gradlew', '-I', 'tools/travel_talisman_smoke/runtime.init.gradle', 'runAcceptanceServer',
                '-x', 'processResources', '-PtravelTalismanEvidence', *GRADLE]
        if self.optional:
            args.append('-Pwaystones_test')
        self.process = subprocess.Popen(args, cwd=ROOT, env=dict(os.environ, MYVILLAGE_TRAVEL_GAME=str(self.game)),
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


class Client:
    def __init__(self, server, name):
        self.server, self.name = server, name
        self.out = server.out / name
        self.inputs = []
        self.client = None

    def __enter__(self):
        self.out.mkdir()
        self.display = ':' + str(next(n for n in range(91, 120) if not Path(f'/tmp/.X11-unix/X{n}').exists()))
        game_dir = self.out / 'game'; game_dir.mkdir()
        merge_options(game_dir / 'options.txt', {**CLIENT_OPTIONS, 'lang': 'zh_cn', 'guiScale': '2',
                      'maxFps': '20', 'renderDistance': '3', 'key_key.use': 'key.mouse.right',
                      'key_key.sneak': 'key.keyboard.left.shift', 'key_key.hotbar.1': 'key.keyboard.1',
                      'key_key.hotbar.2': 'key.keyboard.2', 'key_key.hotbar.9': 'key.keyboard.9'})
        self.xlog = (self.out / 'xvfb.log').open('w'); self.clog = (self.out / 'client.log').open('w')
        self.xvfb = subprocess.Popen(['Xvfb', self.display, '-screen', '0', '960x540x24', '+extension', 'GLX', '+render', '-noreset'],
                                    stdout=self.xlog, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            time.sleep(2)
            args = ['./gradlew', 'runClient', '-x', 'processResources', '-PtravelTalismanEvidence', *GRADLE,
                    f'-Pcombat_smoke_game_dir={game_dir}', f'-Pcombat_smoke_username={self.name}',
                    f'-Pcombat_smoke_server=127.0.0.1:{self.server.sport}']
            if self.server.optional:
                args.append('-Pwaystones_test')
            self.client = subprocess.Popen(args, cwd=ROOT, env=dict(os.environ, DISPLAY=self.display,
                        LIBGL_ALWAYS_SOFTWARE='1', LP_NUM_THREADS='2'), stdout=self.clog, stderr=subprocess.STDOUT, start_new_session=True)
            deadline = time.monotonic() + 600
            while self.name not in self.server.command('list'):
                if self.client.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError(f'Client {self.name} did not join')
                time.sleep(3)
            self.game = Game(self.display, game_dir / 'logs/latest.log', (960, 540))
            if not (self.game.wait_ingame(60, poll=1) or self.game.recover_grab()):
                raise RuntimeError('Client did not reach gameplay')
            self.capture = Game(self.display, game_dir / 'logs/latest.log', (960, 540), ui_check=False)
            time.sleep(2)
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def __exit__(self, *args):
        result = {}
        if self.client:
            result['client_exit_code'] = stop(self.client)
        result['xvfb_exit_code'] = stop(self.xvfb)
        self.xlog.close(); self.clog.close()
        write(self.out / 'cleanup.json', result)

    def record(self, action, **fields):
        self.inputs.append({'action': action, 'time': time.time(), **fields})
        write(self.out / 'mapped-inputs.json', self.inputs)

    def slot(self, number):
        self.game.key(str(number)); self.record('hotbar', number=number); time.sleep(.2)

    def right(self, shift=False):
        self.game.require_ingame('use talisman')
        self.record('use', button=3, shift=shift)
        if shift:
            self.game.xdo('keydown', 'Shift_L'); time.sleep(.15)
        try:
            self.game.xdo('click', '3'); time.sleep(.25)
        finally:
            if shift:
                self.game.xdo('keyup', 'Shift_L')
        time.sleep(.25)

    def close(self):
        self.record('close_menu', key='Escape'); self.game.xdo('key', 'Escape'); time.sleep(.5)

    def click(self, width, height, x, y, shift=False, hold=0):
        px, py = (960-width*2)//2+x*2, (540-height*2)//2+y*2
        self.record('menu_click', pixel=[px, py], logical=[x, y], shift=shift, hold=hold)
        self.game.xdo('mousemove', '--sync', str(px), str(py))
        if shift:
            self.game.xdo('keydown', 'Shift_L')
        try:
            if hold:
                self.game.xdo('mousedown', '1'); time.sleep(hold); self.game.xdo('mouseup', '1')
            else:
                self.game.xdo('click', '1')
        finally:
            if shift:
                self.game.xdo('keyup', 'Shift_L')
        time.sleep(.5)


def assert_at(state, x, dimension='minecraft:overworld'):
    assert state['dimension'] == dimension and abs(state['x'] - x) < .1, state


def recall_checks(server, client):
    before = server.query('fixture'); client.slot(1); client.right(shift=True)
    mark = server.query(); assert mark['mark']['x'] == .5 and mark['counts'] == before['counts'], mark
    server.query('away'); client.right()
    used = server.query(); assert_at(used, .5)
    assert used['counts']['talisman_recall'] == before['counts']['talisman_recall'] - 1, used
    time.sleep(1)
    server.query('away'); client.right(shift=True)
    updated = server.query(); assert updated['mark']['x'] == 40.5, updated
    server.query('origin'); server.query('block'); client.right()
    failed = server.query(); assert_at(failed, .5)
    assert failed['counts'] == used['counts'] and failed['mark'] == updated['mark'], failed
    server.query('unblock'); server.query('nether'); client.right()
    crossed = server.query(); assert_at(crossed, 40.5)
    assert crossed['counts']['talisman_recall'] == used['counts']['talisman_recall'] - 1, crossed
    client.capture.shot(client.out / 'after-recall.png')
    return {'initial': before, 'marked': mark, 'recalled': used, 'overwritten': updated, 'blocked': failed, 'cross_dimension': crossed}


def home_checks(server, client):
    time.sleep(1); server.query('restore_power'); client.slot(2); client.right()
    menu = server.query(); assert menu['menu'] == 'TalismanTravelMenu' and menu['pages'] == 2, menu
    assert len(menu['destinations']) == 8 and '未激活石碑' not in menu['destinations'], menu
    client.capture.shot(client.out / 'home-menu.png'); client.close()
    canceled = server.query(); assert canceled['counts'] == menu['counts'], canceled
    client.right(); client.click(320, 244, 271, 203)
    page = server.query(); assert page['page'] == 1 and len(page['rows']) == 2, page
    target = page['rows'][1]['x'] + .5
    client.click(320, 244, 160, 72)
    traveled = server.query(); assert_at(traveled, target)
    assert traveled['counts']['talisman_home'] == menu['counts']['talisman_home'] - 1, traveled
    results = {'menu': menu, 'cancel': canceled, 'page_two': page, 'success': traveled}
    for action in ['cancel_next', 'remove_first', 'revoke_first']:
        time.sleep(1); server.query('restore_power'); client.right()
        before = server.query(); assert before['menu'] == 'TalismanTravelMenu', before
        server.query(action); client.click(320, 244, 160, 47)
        after = server.query()
        assert after['counts'] == before['counts'] and after['power'] == before['power'], after
        assert_at(after, before['x'], before['dimension'])
        results[action] = {'before': before, 'after': after}
    return results


def crafting_checks(server, client):
    server.query('fixture'); client.slot(9); server.query('desk'); time.sleep(.5); client.right()
    opened = server.query(); assert opened['menu'] == 'TalismanMenu', opened
    results = []
    for recipe, row, item in [(3, 112, 'talisman_home'), (4, 136, 'talisman_recall')]:
        client.click(300, 238, 52, row)
        assert server.desk()['recipe'] == recipe
        client.click(300, 238, 199, 122, hold=12)
        completed = server.desk()
        assert completed['inventory'][2]['item'] == 'myvillage:' + item and completed['inventory'][2]['count'] == 1, completed
        assert completed['batch'] is False, completed
        client.capture.shot(client.out / (item + '-crafted.png'))
        client.click(300, 238, 270, 42, shift=True)
        assert server.desk()['inventory'][2]['count'] == 0
        results.append(completed)
    client.close()
    return results


def ownership_checks(server, client):
    server.query('origin'); client.slot(1); client.right(shift=True)
    a_mark = server.query()['mark']
    with Client(server, B) as second:
        server.query('fixture', B); server.query('second', B)
        second.slot(1); second.right(shift=True)
        b_mark = server.query(player=B)['mark']
        assert b_mark['x'] == 20.5 and server.query()['mark'] == a_mark
        for name, instance, x in [(A, client, .5), (B, second, 20.5)]:
            server.query('away', name); instance.right()
            assert_at(server.query(player=name), x)
        result = {'a': server.query(), 'b': server.query(player=B)}
    return result


def main():
    if os.environ.get('MYVILLAGE_CLOUD_CHECK') != 'travel-talismans':
        raise SystemExit('Run only in the isolated Colab checkout with MYVILLAGE_CLOUD_CHECK=travel-talismans')
    out = Path(sys.argv[1]).resolve(); out.mkdir(parents=True, exist_ok=False)
    report = {'selected': ['without_waystones', 'recall', 'home', 'crafting', 'two_players', 'restart'],
              'completed': [], 'failed': [], 'unrun': [], 'status': 'running', 'owner_visual': 'not_verified', 'owner_feel': 'not_verified', 'fps': 'not_verified'}
    active = None
    try:
        active = 'without_waystones'
        with Server(out / 'without', False) as server:
            with Client(server, A) as client:
                report['without_recall'] = recall_checks(server, client)
                client.slot(2); before = server.query(); client.right(); after = server.query()
                assert after['menu'] != 'TalismanTravelMenu' and before['counts'] == after['counts'], after
                report['without_home'] = after
        report['completed'].append(active); write(out / 'result.json', report)
        with Server(out / 'with', True) as server:
            with Client(server, A) as client:
                for name, check in [('recall', recall_checks), ('home', home_checks), ('crafting', crafting_checks), ('two_players', ownership_checks)]:
                    active = name; report[name] = check(server, client); report['completed'].append(name); write(out / 'result.json', report)
                saved = server.query()['mark']; assert saved is not None
        active = 'restart'
        with Server(out / 'restart', True, out / 'with/server/world') as server:
            with Client(server, A) as client:
                state = server.query(); assert state['mark'] == saved, state
                server.query('away'); client.slot(1); client.right()
                returned = server.query(); assert_at(returned, saved['x'], saved['dimension'])
                report['restart'] = {'loaded': state, 'returned': returned}
        report['completed'].append(active); report['status'] = 'passed'
    except BaseException as error:
        report['status'] = 'failed'; report['failed'].append(active)
        report['error'] = repr(error); report['traceback'] = traceback.format_exc()
        raise
    finally:
        report['unrun'] = [name for name in report['selected'] if name not in report['completed'] and name not in report['failed']]
        write(out / 'result.json', report)


if __name__ == '__main__':
    main()
