#!/usr/bin/env python3
"""Colab-only mapped-input evidence for the three cultivation demos.

Every run binds one frozen product source and jar. RCON prepares fixtures and
reads state; player actions go through an owned X11 window. Failed runs retain
their checks, screenshots, logs and unrun cases. No rehearsal gameplay is added.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.combat_capture import procs, scene, session as cs
from tools.golden_core_evidence import parse_profile

CASES = ('flight', 'drawing', 'talismans', 'tribulation', 'failure', 'persistence', 'two_players')
DESK = '0 -60 3'


def select_cases(value=None):
    result = list(CASES) if value is None else value.split(',')
    if not result or any(c not in CASES for c in result) or len(set(result)) != len(result):
        raise ValueError('Cases must be a unique nonempty subset of ' + ','.join(CASES))
    return result


def status_json(reply, prefix):
    rows = [line[len(prefix):].strip() for line in reply.splitlines() if line.startswith(prefix)]
    if len(rows) != 1:
        raise ValueError('Missing unique status ' + prefix + ': ' + repr(reply))
    value = json.loads(rows[0])
    if not isinstance(value, dict):
        raise ValueError('Status is not an object')
    return value


def allowed_rcon(command):
    """Deliberately omit player casts, menu actions and advancement commands."""
    if '\n' in command or '\r' in command:
        return False
    simple = ('list', 'save-all flush', 'tick freeze', 'tick unfreeze', 'myvillage world pause')
    if command in simple:
        return True
    prefixes = ('op ', 'gamemode ', 'tp ', 'clear ', 'give ', 'item replace ', 'effect ',
                'attribute ', 'fill ', 'setblock ', 'forceload ', 'gamerule ', 'time ',
                'weather ', 'data get ', 'summon ', 'kill ', 'team ', 'tick step ', 'tick rate ',
                'execute if loaded ', 'execute in minecraft:overworld run tp ',
                'myvillage qianzhen realm_status ', 'myvillage flying_sword status', 'myvillage talisman status',
                'myvillage tribulation status', 'myvillage tribulation stop',
                'myvillage foundation status', 'myvillage foundation clear', 'myvillage stats trace', 'damage ')
    if command.startswith(prefixes):
        return True
    return bool(re.fullmatch(r'myvillage cultivation (?:awaken|initiate|setrealm|setprogress|setstability|setroot|info|learn|qi|crafting)\b[^\n\r]*', command))


def menu_point(size, x, y, width=300, height=238):
    w, h = size
    scale = max(1, min(w // 320, h // 240))
    left, top = (w // scale - width) // 2, (h // scale - height) // 2
    return round((left + x) * scale), round((top + y) * scale)


class DemoEvidence:
    def __init__(self, out, cases):
        if out.exists():
            raise ValueError('Retain earlier evidence; output already exists: ' + str(out))
        out.mkdir(parents=True)
        self.out, self.cases, self.language = out, cases, 'zh_cn'
        self.s = None
        self.sessions = 0
        self.guest = None
        self.video = None
        self.video_stream = None
        self.windows = {}
        self.report = {
            'kind': 'cultivation_demos', 'provider': 'Colab', 'status': 'running',
            'started_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
            'selected': cases, 'completed': [], 'unrun': cases.copy(), 'checks': [],
            'actions': [], 'commands': [], 'screenshots': [], 'videos': [], 'phases': [],
            'failures': [], 'cleanup': [], 'method': __doc__,
            'owner_appearance': 'not_verified', 'owner_feel': 'not_verified',
            'owner_audio': 'not_verified', 'owner_fps': 'not_verified',
            'long_term_balance': 'not_verified', 'natural_acquisition': 'not_verified',
        }
        self.verify_source()
        self.save()

    def verify_source(self):
        if os.environ.get('MYVILLAGE_CLOUD_PROVIDER') != 'Colab' or not str(ROOT).startswith('/content/'):
            raise RuntimeError('Run only inside the authorized Colab VM')
        manifest = json.loads((ROOT / 'source-manifest.json').read_text())
        hashes = json.loads((ROOT / 'source-files.json').read_text())
        bad = [name for name, expected in hashes.items()
               if not (ROOT / name).is_file() or hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != expected]
        if bad:
            raise RuntimeError('Frozen source drift: ' + repr(bad[:12]))
        jars = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT / 'build/libs').glob('*.jar')}
        if len(jars) != 1:
            raise RuntimeError('Exactly one deliverable jar is required: ' + repr(jars))
        self.report.update(source=manifest, source_file_count=len(hashes), jar_sha256=jars,
                           collector_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())

    def save(self):
        (self.out / 'evidence.json').write_text(json.dumps(self.report, ensure_ascii=False, indent=2) + '\n')

    def check(self, name, passed, detail):
        self.report['checks'].append({'name': name, 'pass': bool(passed), 'detail': detail})
        self.save()
        print(('PASS ' if passed else 'FAIL ') + name, flush=True)
        if not passed:
            raise AssertionError(name + ': ' + repr(detail))

    def action(self, kind, **details):
        self.report['actions'].append({'kind': kind, 'monotonic': time.monotonic(), **details})
        self.save()

    def poll(self, getter, accept, timeout=15, label='condition'):
        deadline, last = time.monotonic() + timeout, None
        while time.monotonic() < deadline:
            last = getter()
            if accept(last):
                return last
            time.sleep(.2)
        raise TimeoutError(label + ': ' + repr(last))

    def rc(self, command):
        if not allowed_rcon(command):
            raise ValueError('RCON is not an allowed fixture/readback: ' + command)
        reply = self.s.run(command, warn=False)[0]
        self.report['commands'].append({'command': command, 'reply': reply})
        self.save()
        return reply

    def profile(self, user=None):
        return parse_profile(self.rc('myvillage cultivation info ' + (user or self.s.username)))

    def data(self, path, user=None):
        return read_data(self.rc(f'data get entity {user or self.s.username} {path}'))

    def inventory(self, user=None):
        return {int(row['Slot']): row for row in self.data('Inventory', user)}

    def count(self, ident, user=None):
        return sum(row['count'] for row in self.inventory(user).values() if row['id'] == ident)

    def hp(self, user=None):
        return float(self.data('Health', user))

    def pos(self, user=None):
        return self.data('Pos', user)

    def attach_window(self, game=None):
        game = game or self.s.game
        windows = game.xdo('search', '--name', 'Minecraft').split()
        if len(windows) != 1:
            raise RuntimeError('Expected one owned Minecraft window: ' + repr(windows))
        window = windows[0]
        game.xdo('windowfocus', '--sync', window)
        game.xdo('windowsize', '--sync', window, '960', '540')
        game.xdo('windowmove', '--sync', window, '0', '0')
        game.size = (960, 540)
        root = self.guest.processes[-1][0].pid if self.guest and game is self.guest.game else self.s.state['procs']['client']['pid']
        pid = int(game.xdo('getwindowpid', window).strip())
        if pid not in {root, *procs.descendants(root)}:
            raise RuntimeError('Minecraft window is not a child of the owned process')
        self.windows[game.display] = (window, pid, procs.start_time(pid))
        time.sleep(.5)

    def guard(self, game=None, menu=False):
        game = game or self.s.game
        window, pid, start = self.windows[game.display]
        game.xdo('windowfocus', '--sync', window)
        actual = game.xdo('getwindowfocus').strip()
        actual_pid = int(game.xdo('getwindowpid', window).strip())
        grabbed = game.pointer_grabbed()
        details = {'display': game.display, 'window': window, 'focus': actual,
                   'pid': actual_pid, 'grabbed': grabbed, 'menu': menu}
        self.action('input_guard', **details)
        if actual != window or actual_pid != pid or not procs.is_alive(pid, start) or grabbed is not (not menu):
            raise RuntimeError('Input guard refused: ' + repr(details))

    def key(self, key, game=None, menu=False):
        game = game or self.s.game
        self.guard(game, menu)
        game.xdo('key', '--delay', '70', key)
        self.action('mapped_key', display=game.display, key=key)
        time.sleep(.4)

    def hold(self, keys, seconds, game=None, mouse=None, menu=False, sample=None):
        game = game or self.s.game
        self.guard(game, menu)
        for key in keys:
            game.xdo('keydown', key)
        if mouse:
            game.xdo('mousedown', str(mouse))
        try:
            time.sleep(seconds)
            result = sample() if sample else None
        finally:
            if mouse:
                game.xdo('mouseup', str(mouse))
            for key in reversed(keys):
                game.xdo('keyup', key)
        self.action('mapped_hold', display=game.display, keys=keys, mouse=mouse, seconds=seconds)
        time.sleep(.35)
        return result

    def right(self, game=None):
        self.hold([], .12, game, mouse=3)

    def look(self, dx, dy, game=None):
        game = game or self.s.game
        self.guard(game)
        game.xdo('mousemove_relative', '--', str(dx), str(dy))
        self.action('mapped_mouse_look', display=game.display, dx=dx, dy=dy)
        time.sleep(.5)

    def menu_click(self, x, y, shift=False, game=None):
        game = game or self.s.game
        px, py = menu_point(game.size, x, y)
        self.guard(game, menu=True)
        game.xdo('mousemove', '--sync', str(px), str(py))
        self.guard(game, menu=True)
        if shift:
            game.xdo('keydown', 'Shift_L')
        try:
            game.xdo('click', '1')
        finally:
            if shift:
                game.xdo('keyup', 'Shift_L')
        self.action('mapped_menu_click', display=game.display, x=px, y=py, shift=shift)
        time.sleep(.4)

    def close_menu(self, game=None):
        game = game or self.s.game
        self.key('Escape', game, menu=True)
        self.poll(game.pointer_grabbed, lambda v: v is True, label='menu closed')

    def capture(self, name, game=None):
        game = game or self.s.game
        window = self.windows[game.display][0]
        subprocess.run(['import', '-silent', '-window', window, str(self.out / (name + '.png'))],
                       env=game.env, check=True, timeout=30)
        self.report['screenshots'].append(name + '.png')
        self.save()

    def start_video(self, name):
        self.stop_video()
        path = self.out / (name + '.mp4')
        self.video_stream = (self.out / (name + '-ffmpeg.log')).open('w')
        self.video = subprocess.Popen(['ffmpeg', '-nostdin', '-y', '-f', 'x11grab', '-framerate', '20',
            '-video_size', '960x540', '-i', self.s.game.display, '-an', '-c:v', 'libx264', '-preset', 'ultrafast',
            '-crf', '22', '-pix_fmt', 'yuv420p', str(path)], stdout=self.video_stream, stderr=subprocess.STDOUT)
        self.report['videos'].append({'file': path.name, 'audio': 'not_captured'})
        self.save()

    def stop_video(self):
        if self.video:
            self.video.send_signal(signal.SIGINT)
            self.video.wait(timeout=15)
            self.video_stream.close()
            self.video = self.video_stream = None

    def start(self, reuse=False):
        cs.merge_options(cs.CLIENT_DIR / 'options.txt', {'rawMouseInput': 'false', 'maxFps': '30', 'key_key.use': 'key.mouse.right'})
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), username='DemoTester', language='zh_cn',
            reuse_world=reuse, prebuilt_resources=True, lock=str(procs.default_lock_path(ROOT)),
            gradle_jvmargs='-Xmx2g', view_distance=4, world_seed=20261027))
        self.s = cs.Session(state, ui_check=True)
        self.sessions += 1
        self.report['phases'].append({'phase': 'restart' if reuse else 'start', 'timings': state.get('timings'),
                                      'session_id': state['session_id']})
        self.attach_window()
        self.poll(self.s.game.pointer_grabbed, lambda v: v is True, label='actual game pointer')
        self.rc('tick unfreeze')
        if not reuse:
            for command in (*scene.world_rules(), 'gamerule naturalRegeneration false', 'myvillage world pause',
                            'forceload add -48 -48 80 96'):
                self.rc(command)
            self.poll(lambda: self.rc('execute if loaded 64 -61 64 run time query gametime'),
                      lambda r: 'The time is' in r, label='fixture chunks loaded')
            self.rc('fill -32 -61 -32 64 -61 64 minecraft:stone')
            self.rc('setblock ' + DESK + ' myvillage:talisman_desk')
            self.configure()
        self.save()

    def configure(self, user=None):
        u = user or self.s.username
        # New characters may be entering their private opening. Let that bounded
        # native setup finish before moving this test actor to the fixture arena.
        self.poll(lambda: status_json(self.rc('myvillage qianzhen realm_status ' + u), 'QIANZHEN_REALM_STATUS '),
                  lambda r: not r['building'] and not r['waiting'], 150, 'initial personal realm setup finishes')
        for command in (f'op {u}', f'gamemode survival {u}', f'execute in minecraft:overworld run tp {u} .5 -60 .5 0 0', f'clear {u}',
            f'effect clear {u}', f'effect give {u} minecraft:saturation infinite 255 true',
            f'myvillage cultivation awaken {u}', f'myvillage cultivation initiate {u}',
            f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_early',
            f'myvillage cultivation qi fill {u}', f'effect give {u} minecraft:instant_health 1 10 true'):
            self.rc(command)
        self.poll(lambda: self.pos(u), lambda p: abs(p[0] - .5) < .05 and abs(p[1] + 60) < .05, label='fixture player settled')

    def stop(self):
        self.stop_video()
        state = cs.stop()
        self.report['cleanup'].append({'session': self.sessions, 'phase': state.get('phase') if state else None})
        for label, path in [('server', cs.SERVER_DIR / 'logs/latest.log'), ('client', cs.CLIENT_DIR / 'logs/latest.log')]:
            if path.exists():
                shutil.copy2(path, self.out / f'session-{self.sessions}-{label}.log')
        self.s = None
        self.save()

    def flight_status(self, user=None):
        return status_json(self.rc('myvillage flying_sword status ' + (user or self.s.username)), 'FLYING_SWORD_STATUS ')

    def flight(self):
        self.configure()
        u = self.s.username
        self.rc(f'tp {u} 12.5 -60 .5 0 0')
        self.rc('fill 4 -60 50 20 -42 50 minecraft:stone')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:azure_cloud_flying_sword')
        self.key('1')
        before = self.profile()
        hp = self.hp()
        self.start_video('flight')
        self.right()
        started = self.poll(self.flight_status, lambda s: s['active'], label='mapped flight mount')
        self.check('flight_azure_identity', started['tier'] == 'azure' and started['escrow'], started)
        self.hold(['space'], 1.6)
        raised = self.flight_status()
        self.check('flight_real_ascent', raised['position'][1] > started['position'][1] + 3, [started, raised])
        rotation = self.data('Rotation')
        self.look(40, 0)
        turned = self.data('Rotation')
        yaw_delta = lambda a, b: (a - b + 180) % 360 - 180
        self.check('flight_physical_mouse_turns', abs(yaw_delta(turned[0], rotation[0])) > .2, [rotation, turned])
        self.look(-40, 0)
        restored = self.data('Rotation')
        self.check('flight_physical_mouse_returns_heading', abs(yaw_delta(restored[0], rotation[0])) < 2, [rotation, restored])
        cruise = self.hold(['w'], 1.1, sample=self.flight_status)
        boost = self.hold(['w', 'Control_L'], 1.2, sample=self.flight_status)
        speed = lambda s: math.hypot(s['velocity'][0], s['velocity'][2])
        self.check('flight_mapped_boost_faster', cruise['phase'] == 'CRUISE' and boost['phase'] == 'BOOST'
                   and speed(boost) > speed(cruise) * 1.3, [cruise, boost])
        self.check('flight_spends_server_qi', self.profile()['power'] < before['power'], [before, self.profile()])
        self.key('F5')
        self.capture('flight-standing-back')
        self.key('F5')
        self.capture('flight-standing-front')
        self.key('F5')
        wall = self.hold(['w', 'Control_L'], 3, sample=self.flight_status)
        self.check('flight_collision_blocks_forward', wall['position'][2] < 50, wall)
        self.right()
        landing = self.poll(self.flight_status, lambda s: s['landing'] or not s['active'], label='mapped recall lands')
        ended = self.poll(self.flight_status, lambda s: not s['active'], 35, 'flight landed and recalled')
        self.check('flight_returns_exactly_one_sword', self.count('myvillage:azure_cloud_flying_sword') == 1 and not ended['escrow'], self.inventory())
        self.check('flight_no_touchdown_damage', self.hp() == hp, {'before': hp, 'after': self.hp(), 'landing': landing})
        self.rc('fill 4 -60 50 20 -42 50 minecraft:air')
        self.rc(f'tp {u} 12.5 -60 .5 0 0')
        self.rc(f'myvillage cultivation qi fill {u}')
        # The returned stack is in an arbitrary inventory slot. Select it through a fixture,
        # then perform the second launch and controls through mapped input again.
        self.rc(f'clear {u}')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:rideable_flying_sword')
        self.key('1')
        self.right()
        self.poll(self.flight_status, lambda s: s['active'], label='basic sword mounted')
        self.hold(['space'], 2)
        self.rc(f'myvillage cultivation qi set {u} 0')
        falling = self.hold(['space', 'w', 'Control_L'], .7, sample=self.flight_status)
        self.check('empty_qi_cannot_climb_or_boost', falling['phase'] == 'EMPTY_DESCENT'
                   and falling['velocity'][1] < 0 and falling['qi'] == 0, falling)
        self.capture('flight-empty-descent')
        ended = self.poll(self.flight_status, lambda s: not s['active'], 35, 'empty-qi sword landed')
        self.check('empty_descent_returns_item_once', self.count('myvillage:rideable_flying_sword') == 1 and not ended['escrow'], self.inventory())
        self.check('empty_descent_no_fall_damage', self.hp() == hp, self.hp())
        self.stop_video()

    def talisman_status(self, user=None):
        return status_json(self.rc(f'myvillage talisman status {user or self.s.username} {DESK}'), 'TALISMAN_STATUS ')

    def open_desk(self, game=None, user=None):
        game, user = game or self.s.game, user or self.s.username
        self.rc(f'tp {user} .5 -60 .5 0 20.5')
        time.sleep(.5)
        self.right(game)
        self.poll(game.pointer_grabbed, lambda v: v is False, label='mapped desk opens')

    def draw_hold(self, seconds, sample=None, game=None):
        game = game or self.s.game
        px, py = menu_point(game.size, 199, 122)
        self.guard(game, menu=True)
        game.xdo('mousemove', '--sync', str(px), str(py))
        return self.hold([], seconds, game, mouse=1, menu=True, sample=sample)

    def drawing(self):
        self.configure()
        u = self.s.username
        self.rc(f'myvillage cultivation crafting set {u} talisman 90')
        self.rc(f'give {u} myvillage:talisman_paper 4')
        self.rc(f'give {u} myvillage:spiritual_ink 4')
        self.open_desk()
        self.start_video('drawing')
        self.menu_click(78, 222, shift=True)
        self.menu_click(96, 222, shift=True)
        ready = self.talisman_status()
        self.check('drawing_inputs_inserted_by_mouse', [s['count'] for s in ready['inventory'][:2]] == [4, 4], ready)
        self.menu_click(52, 88)  # 护身符
        self.draw_hold(2, sample=lambda: self.capture('drawing-in-progress'))
        paused = self.poll(self.talisman_status, lambda s: not s['holding'], label='release stops drawing')
        self.check('drawing_paid_partial_batch', paused['batch'] and 0 < paused['progress'] < paused['required']
                   and paused['frozenAptitude'] == 90 and paused['frozenQuality'] == 4, paused)
        time.sleep(1)
        self.check('drawing_release_stable', self.talisman_status()['progress'] == paused['progress'], self.talisman_status())
        self.close_menu()
        time.sleep(1)
        self.check('drawing_closed_menu_pauses', self.talisman_status()['progress'] == paused['progress'], self.talisman_status())
        # A changed aptitude must not reroll an already-paid sheet.
        self.rc(f'myvillage cultivation crafting set {u} talisman 20')
        self.open_desk()
        self.draw_hold(12)
        made = self.talisman_status()
        self.check('drawing_frozen_quality_survives_reopen', not made['batch'] and made['inventory'][2]['item'] == 'myvillage:talisman_ward'
                   and made['inventory'][2]['count'] == 1 and made['inventory'][2]['quality'] == 4, made)
        self.check('drawing_costs_material_once', [s['count'] for s in made['inventory'][:2]] == [3, 3], made)
        self.capture('drawing-completed')
        self.draw_hold(1)
        blocked = self.talisman_status()
        self.check('drawing_full_output_no_extra_cost', blocked['inventory'] == made['inventory'] and not blocked['batch'], blocked)
        self.menu_click(270, 42, shift=True)
        self.close_menu()
        self.check('drawing_product_received', self.count('myvillage:talisman_ward') == 1, self.inventory())
        self.rc(f'myvillage cultivation crafting set {u} talisman 90')
        for index, ident in [(0, 'talisman_lightning'), (1, 'talisman_snare')]:
            self.rc(f'myvillage cultivation qi fill {u}')
            self.open_desk()
            self.menu_click(52, 40 + index * 24)
            self.draw_hold(12)
            result = self.talisman_status()
            self.check('drawing_' + ident, result['inventory'][2]['item'] == 'myvillage:' + ident
                       and result['inventory'][2]['count'] == 1 and result['inventory'][2]['quality'] == 4, result)
            self.menu_click(270, 42, shift=True)
            self.close_menu()
        self.stop_video()
        self.show_crafting_page()

    def show_crafting_page(self):
        from tools.ink_hub_evidence import bounds
        self.key('h')
        self.poll(self.s.game.pointer_grabbed, lambda v: v is False, label='H page opened')
        b = bounds(self.s.game.size)
        px, py = round((b['left'] + 26) * b['scale']), round((b['top'] + 36 + 6 * 21 + 9) * b['scale'])
        self.guard(menu=True)
        self.s.game.xdo('mousemove', '--sync', str(px), str(py))
        self.guard(menu=True)
        self.s.game.xdo('click', '1')
        self.action('mapped_crafting_page', x=px, y=py)
        time.sleep(.6)
        self.capture('crafting-aptitudes')
        self.close_menu()

    def foundation(self, target=None):
        reply = self.rc('myvillage foundation status ' + (target or self.s.username))
        return status_json(reply, 'FOUNDATION ')

    def talismans(self):
        self.configure()
        u = self.s.username
        rules = json.loads((ROOT / 'src/main/resources/data/myvillage/talisman/rules.json').read_text())
        self.rc(f'tp {u} 12.5 -60 .5 0 24')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:talisman_ward[myvillage:talisman_quality=4] 3')
        self.key('1')
        self.start_video('talismans')
        self.right()
        ward = self.poll(self.foundation, lambda s: s['shield'] > 0, label='mapped ward shield')
        self.check('ward_uses_one_and_quality', self.count('myvillage:talisman_ward') == 2
                   and ward['shield'] == rules['ward']['shield'][3], ward)
        time.sleep(1)
        self.right()
        replaced = self.foundation()
        self.check('ward_same_source_replaces', replaced['shield'] == ward['shield'] and self.count('myvillage:talisman_ward') == 1, replaced)
        self.capture('talisman-ward')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:talisman_lightning[myvillage:talisman_quality=4] 2')
        self.rc('summon minecraft:husk 12.5 -60 4 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:1000f,Tags:["demo_lightning"],attributes:[{id:"minecraft:generic.max_health",base:1000d}]}')
        target = '@e[tag=demo_lightning,limit=1]'
        hp = self.hp(target)
        time.sleep(1)
        self.right()
        self.poll(lambda: self.hp(target), lambda h: h < hp, label='mapped talisman lightning damages')
        self.check('lightning_single_consumption', self.count('myvillage:talisman_lightning') == 1, self.inventory())
        self.capture('talisman-lightning')
        self.rc('kill @e[tag=demo_lightning]')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:talisman_snare[myvillage:talisman_quality=4] 2')
        time.sleep(1)
        self.right()
        placed = self.poll(self.talisman_status, lambda s: s['activeSeals'] == 1, label='ground snare armed')
        self.capture('talisman-snare-armed')
        # Target placement is a fixture. Triggering is the paid ground seal's own server behavior.
        self.rc('summon minecraft:husk 12.5 -60 4 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["demo_snare"]}')
        triggered = self.poll(lambda: self.foundation('@e[tag=demo_snare,limit=1]'), lambda s: s.get('rooted') is True,
                              label='ground snare applies shared root')
        self.check('snare_single_trigger', self.talisman_status()['activeSeals'] == 0
                   and self.count('myvillage:talisman_snare') == 1, triggered)
        self.capture('talisman-snare-triggered')
        self.rc('kill @e[tag=demo_snare]')
        self.stop_video()

    def tribulation_status(self, user=None):
        return status_json(self.rc('myvillage tribulation status ' + (user or self.s.username)), 'TRIBULATION_STATUS ')

    def tribulation_fixture(self, protected=True):
        self.configure()
        u = self.s.username
        self.rc(f'myvillage foundation clear {u}')
        self.rc(f'tp {u} 32.5 -60 20.5 0 0')
        realm = json.loads((ROOT / 'src/main/resources/data/myvillage/myvillage/realm/foundation_establishment.json').read_text())
        stage = next(s for s in realm['stages'] if s['id'] == 'myvillage:foundation_late')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:foundation_establishment myvillage:foundation_late')
        self.rc(f'myvillage cultivation setprogress {u} ' + str(stage['cultivation_cap']))
        self.rc(f'myvillage cultivation setstability {u} ' + str(stage['advancement']['required_stability']))
        self.rc(f'myvillage cultivation qi fill {u}')
        self.rc(f'effect give {u} minecraft:instant_health 1 10 true')
        for slot, item in [('head', 'helmet'), ('chest', 'chestplate'), ('legs', 'leggings'), ('feet', 'boots')]:
            self.rc(f'item replace entity {u} armor.{slot} with ' + ('minecraft:diamond_' + item if protected else 'minecraft:air'))
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:talisman_ward[myvillage:talisman_quality=4] 8')
        self.rc(f'item replace entity {u} hotbar.1 with myvillage:pill_yangyuan 4')
        self.rc(f'item replace entity {u} hotbar.2 with myvillage:azure_cloud_flying_sword')
        self.key('1')
        time.sleep(.5)
        self.poll(lambda: self.pos(), lambda p: abs(p[1] + 60) < .05, label='tribulation ground fixture')
        state = self.tribulation_status()
        if state['phase'] == 'RECOVERING':
            self.poll(self.tribulation_status, lambda s: s['phase'] != 'RECOVERING', 15, 'previous attempt recovered')
        return stage

    def chat(self, text):
        self.guard()
        self.s.game.xdo('key', 't')
        time.sleep(.5)
        self.guard(menu=True)
        self.s.game.xdo('type', '--delay', '25', text)
        self.s.game.xdo('key', 'Return')
        self.action('mapped_typed_command', text=text)
        time.sleep(.8)
        self.poll(self.s.game.pointer_grabbed, lambda v: v is True, label='typed command chat closed')

    def tribulation(self):
        self.tribulation_fixture()
        u = self.s.username
        before = self.profile()
        self.start_video('tribulation')
        self.key('n')
        started = self.poll(self.tribulation_status, lambda s: s['phase'] == 'PREPARING', label='normal N starts tribulation')
        self.capture('tribulation-preparation')
        # A real item attempt must not escape the active trial through flight.
        self.key('3')
        self.right()
        self.check('tribulation_blocks_flying_sword', not self.flight_status()['active']
                   and self.count('myvillage:azure_cloud_flying_sword') == 1, self.flight_status())
        self.key('1')
        active = self.poll(self.tribulation_status, lambda s: s['phase'] == 'ACTIVE', 12, 'tribulation begins')
        startpos = self.pos()
        self.hold(['d'], .7)
        moved = self.pos()
        self.check('tribulation_allows_real_movement', math.dist(startpos, moved) > .5
                   and self.tribulation_status()['attempt_id'] == active['attempt_id'], [startpos, moved, active])
        self.hold(['a'], .7)
        rules = json.loads((ROOT / 'src/main/resources/data/myvillage/tribulation/golden_core.json').read_text())
        main_ticks = [s['tick'] for s in rules['strikes'] if s['main']]
        protected, captured, observed, last_heal = set(), set(), [], 0
        until = time.monotonic() + rules['duration_ticks'] / 20 + 45
        while time.monotonic() < until:
            state = self.tribulation_status()
            if state['phase'] not in ('ACTIVE', 'PREPARING'):
                break
            for index, tick in enumerate(main_ticks):
                if index not in protected and state['elapsed'] >= tick - 50:
                    self.key('1')
                    self.right()
                    shield = self.foundation()
                    self.check(f'tribulation_ward_{index + 1}_accepted', shield['shield'] > 0, shield)
                    protected.add(index)
            if state['main_completed'] not in captured:
                captured.add(state['main_completed'])
                observed.append({'state': state, 'health': self.hp(), 'shield': self.foundation()['shield']})
                if state['main_completed'] in (1, 4, 6):
                    self.capture('tribulation-main-' + str(state['main_completed']))
            if self.hp() < self.foundation()['max_health'] * .65 and time.monotonic() - last_heal > 20:
                self.key('2')
                self.hold([], 1.8, mouse=3)
                last_heal = time.monotonic()
            time.sleep(.6)
        ended = self.tribulation_status()
        self.check('tribulation_six_main_strikes_complete', ended['phase'] == 'SUCCEEDED'
                   and ended['main_completed'] == 6 and ended['attempt_id'] == started['attempt_id'], ended)
        after = self.profile()
        self.check('tribulation_advances_once_to_golden', after['realm'] == 'myvillage:golden_core'
                   and after['stage'] == 'myvillage:golden_early' and after['progress'] == 0
                   and after['stability'] == before['stability'] // 2, {'before': before, 'after': after, 'observations': observed})
        self.capture('tribulation-completed')
        time.sleep(1)
        self.check('tribulation_settlement_not_repeated', self.tribulation_status()['attempt_id'] == ended['attempt_id']
                   and self.profile()['stage'] == after['stage'] and self.profile()['stability'] == after['stability'], self.profile())
        self.stop_video()

    def failure(self):
        self.tribulation_fixture(protected=False)
        u = self.s.username
        self.rc(f'myvillage foundation clear {u}')
        hp = self.hp()
        if hp > 3:
            self.rc(f'damage {u} {hp - 3:.6f} minecraft:generic_kill')
        self.check('failure_low_health_fixture', 0 < self.hp() <= 3.01, self.hp())
        before = self.profile()
        self.start_video('tribulation-failure')
        self.chat('/myvillage tribulation start ' + u)
        started = self.poll(self.tribulation_status, lambda s: s['phase'] == 'PREPARING', label='administrator summon uses real attempt')
        ended = self.poll(self.tribulation_status, lambda s: s['phase'] in ('FAILED', 'RECOVERING'), 30, 'nonlethal trial failure')
        after = self.profile()
        rules = json.loads((ROOT / 'src/main/resources/data/myvillage/tribulation/golden_core.json').read_text())
        self.check('tribulation_failure_keeps_alive_and_stage', self.hp() > 0 and after['stage'] == before['stage']
                   and after['progress'] == before['progress'] and ended['attempt_id'] == started['attempt_id'], ended)
        expected = max(0, before['stability'] - rules['failure_stability_loss'])
        self.check('tribulation_failure_penalty_once', after['stability'] == expected and ended['penalty_applied'], [before, after, ended])
        self.capture('tribulation-nonlethal-failure')
        time.sleep(1)
        self.check('tribulation_failure_penalty_not_repeated', self.profile()['stability'] == expected, self.profile())
        self.stop_video()

    def persistence(self):
        self.configure()
        u = self.s.username
        # A new owned desk keeps this case independent of the earlier drawing case.
        self.rc('setblock ' + DESK + ' minecraft:air')
        self.rc('setblock ' + DESK + ' myvillage:talisman_desk')
        self.rc(f'myvillage cultivation crafting set {u} talisman 90')
        self.rc(f'give {u} myvillage:talisman_paper')
        self.rc(f'give {u} myvillage:spiritual_ink')
        self.open_desk()
        self.menu_click(78, 222, shift=True)
        self.menu_click(96, 222, shift=True)
        self.draw_hold(2)
        self.close_menu()
        batch = self.talisman_status()
        self.check('restart_has_real_paid_partial_batch', batch['batch'] and 0 < batch['progress'] < batch['required'], batch)
        self.tribulation_fixture()
        self.key('n')
        attempt = self.poll(self.tribulation_status, lambda s: s.get('main_completed', 0) >= 1, 30, 'one real main strike before restart')
        profile = self.profile()
        self.rc('save-all flush')
        self.stop()
        self.start(reuse=True)
        resumed = self.tribulation_status()
        self.check('restart_keeps_attempt_and_plan', resumed['attempt_id'] == attempt['attempt_id']
                   and resumed['plan_sha256'] == attempt['plan_sha256'] and resumed['rules_sha256'] == attempt['rules_sha256']
                   and resumed['next_strike'] >= attempt['next_strike'] and resumed['elapsed'] >= attempt['elapsed'], [attempt, resumed])
        self.check('restart_does_not_award_early', self.profile()['stage'] == profile['stage'], self.profile())
        reopened = self.talisman_status()
        self.check('restart_keeps_paid_sheet_exactly', reopened['progress'] == batch['progress']
                   and reopened['frozenQuality'] == batch['frozenQuality'] and reopened['owner'] == batch['owner']
                   and not reopened['holding'], [batch, reopened])
        self.rc('myvillage tribulation stop ' + u)
        self.rc(f'myvillage cultivation qi fill {u}')
        self.open_desk()
        self.draw_hold(12)
        done = self.talisman_status()
        self.check('restart_sheet_finishes_once', done['inventory'][2]['count'] == 1
                   and done['inventory'][2]['quality'] == batch['frozenQuality'], done)
        self.capture('drawing-after-restart')
        self.close_menu()
        self.configure()
        self.rc(f'tp {u} 12.5 -60 .5 0 0')
        self.rc(f'item replace entity {u} hotbar.0 with myvillage:azure_cloud_flying_sword')
        self.key('1')
        self.right()
        self.poll(self.flight_status, lambda s: s['active'], label='flight before restart')
        self.hold(['space'], 2)
        health = self.hp()
        self.check('restart_flight_is_airborne', self.pos()[1] > -55, self.flight_status())
        self.rc('save-all flush')
        self.stop()
        self.start(reuse=True)
        self.poll(self.flight_status, lambda s: not s['active'], 35, 'rejoined emergency landing completes')
        self.check('restart_flight_item_returned_once', self.count('myvillage:azure_cloud_flying_sword') == 1
                   and not self.flight_status()['escrow'], self.inventory())
        self.check('restart_flight_has_no_fall_damage', self.hp() == health, {'before': health, 'after': self.hp()})
        self.capture('flight-after-restart')

    def two_players(self):
        self.configure()
        u = self.s.username
        self.rc('setblock ' + DESK + ' minecraft:air')
        self.rc('setblock ' + DESK + ' myvillage:talisman_desk')
        self.rc(f'give {u} myvillage:talisman_paper')
        self.rc(f'give {u} myvillage:spiritual_ink')
        with SecondClient(self, timeout=360) as guest:
            self.guest = guest
            self.attach_window(guest.game)
            self.configure(guest.username)
            self.rc(f'tp {guest.username} -1.5 -60 1.5 -45 21.6')
            self.open_desk()
            self.menu_click(78, 222, shift=True)
            self.menu_click(96, 222, shift=True)
            self.draw_hold(1)
            owner = self.talisman_status()
            self.right(guest.game)
            self.check('desk_live_viewer_exclusive', guest.game.pointer_grabbed() is True
                       and self.talisman_status(guest.username)['viewer'] == owner['viewer'], self.talisman_status(guest.username))
            self.capture('two-player-desk-owner')
            self.close_menu()
            self.rc(f'tp {u} 12.5 -60 .5 0 0')
            self.rc(f'item replace entity {u} hotbar.0 with myvillage:azure_cloud_flying_sword')
            self.key('1')
            self.right()
            self.poll(self.flight_status, lambda s: s['active'], label='owner mounts with guest present')
            self.hold(['space'], .5)
            self.rc(f'tp {guest.username} 18.5 -60 5.5 130 -25')
            time.sleep(.6)
            self.right(guest.game)
            self.check('sword_does_not_take_other_passenger', self.flight_status()['active']
                       and not self.flight_status(guest.username)['active'], [self.flight_status(), self.flight_status(guest.username)])
            self.capture('two-player-flight-observer', guest.game)
            self.key('F1', guest.game)
            self.capture('flying-sword-model-in-game', guest.game)
            self.key('F1', guest.game)
            self.right()
            self.poll(self.flight_status, lambda s: not s['active'], 20, 'owner recall with observer')
            self.guest = None

    def run(self):
        try:
            self.start()
            for name in self.cases:
                self.report['active_case'] = name
                self.save()
                getattr(self, name)()
                self.report['completed'].append(name)
                self.report['unrun'] = [c for c in self.cases if c not in self.report['completed']]
                self.save()
            self.verify_source()
            self.report['status'] = 'passed'
        except BaseException as error:
            self.report['status'] = 'failed'
            self.report['failures'].append({'case': self.report.get('active_case'), 'type': type(error).__name__,
                                            'message': str(error), 'traceback': traceback.format_exc()})
            try:
                if self.s:
                    self.capture('failure')
            except Exception as capture_error:
                self.report['failures'].append({'capture_error': type(capture_error).__name__})
            raise
        finally:
            if self.s:
                self.stop()
            self.report['finished_utc'] = dt.datetime.now(dt.timezone.utc).isoformat()
            self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--cases')
    args = parser.parse_args()
    evidence = DemoEvidence(args.out.resolve(), select_cases(args.cases))
    evidence.run()


if __name__ == '__main__':
    main()

