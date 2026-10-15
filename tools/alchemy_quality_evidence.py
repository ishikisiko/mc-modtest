#!/usr/bin/env python3
"""Bounded alchemy-quality evidence, run on Modal after clean stage 1.

Owns capture sessions/heavy lock and a separate second real client. All starts,
slot-transfer attempts and pill uses are mapped mouse/key input. RCON sets up
administrator fixtures and reads authoritative state. No DevBridge. Cold legacy
save migration, natural acquisition and owner balance/appearance remain unverified.
"""
from __future__ import annotations

import argparse
import html
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.alchemy_evidence import AlchemyEvidence, MEDICINE, POS
from tools.combat_capture import procs, scene, session as cs
from tools.combat_capture.xgame import Game
from tools.realm_stats_evidence import close
from tools.modal_runner.remote import ALCHEMY_CASES, select_cases

QUALITY = 'myvillage:pill_quality'
SLOTS = ((16, 38), (52, 38), (76, 38), (112, 38), (178, 38))
MAIN_VIEW = '.5 -60 .5 0 20.5'
GUEST_VIEW = '-1.5 -60 1.5 -45 21.6'


class Snbt:
    """Small strict SNBT reader retaining nested components and whole UUID arrays.

    Unlike the old id/count-only evidence parser this must never drop a quality
    component or one field of a frozen Batch. Reject duplicate keys/trailing data.
    """
    def __init__(self, value):
        self.value, self.i = value, 0

    def space(self):
        while self.i < len(self.value) and self.value[self.i].isspace():
            self.i += 1

    def take(self, expected):
        self.space()
        if not self.value.startswith(expected, self.i):
            raise ValueError('Malformed SNBT near ' + self.value[self.i:self.i+60])
        self.i += len(expected)

    def quoted(self):
        quote = self.value[self.i]; self.i += 1; result = ''
        while self.i < len(self.value):
            c = self.value[self.i]; self.i += 1
            if c == quote:
                return result
            if c == '\\':
                if self.i >= len(self.value) or self.value[self.i] not in (quote, '\\'):
                    raise ValueError('Invalid SNBT string escape')
                c = self.value[self.i]; self.i += 1
            result += c
        raise ValueError('Unterminated SNBT string')

    def token(self, *, key=False):
        self.space()
        if self.i < len(self.value) and self.value[self.i] in ('"', "'"):
            return self.quoted()
        match = re.match(r'[^\s,:{}\[\]]+' if key else r'[^\s,{}\[\]]+', self.value[self.i:])
        if not match:
            raise ValueError('Missing SNBT token')
        self.i += len(match.group()); return match.group()

    def read(self):
        self.space()
        if self.i >= len(self.value):
            raise ValueError('Missing SNBT value')
        c = self.value[self.i]
        if c == '{':
            self.i += 1; result = {}; self.space()
            while self.value[self.i:self.i+1] != '}':
                key = self.token(key=True); self.take(':')
                if key in result:
                    raise ValueError('Duplicate SNBT key: ' + key)
                result[key] = self.read(); self.space()
                if self.value[self.i:self.i+1] != ',':
                    break
                self.i += 1
            self.take('}'); return result
        if c == '[':
            self.i += 1; result = []; self.space()
            # Typed UUID arrays remain arrays of all four signed ints.
            typed = re.match(r'[BIL];', self.value[self.i:])
            if typed:
                self.i += 2
            while self.value[self.i:self.i+1] != ']':
                result.append(self.read()); self.space()
                if self.value[self.i:self.i+1] != ',':
                    break
                self.i += 1
            self.take(']'); return result
        if c in ('"', "'"):
            return self.quoted()
        value = self.token()
        if re.fullmatch(r'[-+]?\d+[bBsSlL]?', value):
            return int(value.rstrip('bBsSlL'))
        if re.fullmatch(r'[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?[fFdD]?', value):
            number = float(value.rstrip('fFdD'))
            if not math.isfinite(number):
                raise ValueError('Nonfinite SNBT number')
            return number
        return value


def read_data(reply):
    if 'data: ' not in reply:
        raise ValueError('Missing authoritative data readback: ' + reply)
    parser = Snbt(reply.split('data: ', 1)[1]); value = parser.read(); parser.space()
    if parser.i != len(parser.value):
        raise ValueError('Trailing or partial SNBT readback')
    return value


def quality_stacks(reply):
    rows = read_data(reply)
    if not isinstance(rows, list):
        raise ValueError('Expected saved ItemStack list')
    result = {}
    for row in rows:
        if not isinstance(row, dict) or not all(k in row for k in ('Slot', 'id', 'count')):
            raise ValueError('Incomplete saved stack')
        if type(row['Slot']) is not int or type(row['count']) is not int or row['count'] <= 0:
            raise ValueError('Invalid stack slot/count')
        if row['Slot'] in result:
            raise ValueError('Duplicate stack slot')
        result[row['Slot']] = {k: v for k, v in row.items() if k != 'Slot'}
    return result


def pill_quality(stack):
    value = stack.get('components', {}).get(QUALITY, 1)
    return value if type(value) is int and 1 <= value <= 4 else 1


def read_batch(reply):
    if reply.startswith('Found no elements matching Batch'):
        return None
    value = read_data(reply)
    if not isinstance(value, dict) or set(value) != {'Version', 'Brewer', 'Recipe', 'Score', 'Cap', 'Quality'}:
        raise ValueError('Incomplete or unknown frozen batch fields')
    if (value['Version'] != 1 or not isinstance(value['Recipe'], str)
            or not isinstance(value['Brewer'], list) or len(value['Brewer']) != 4
            or not all(type(n) is int for n in value['Brewer']) or type(value['Version']) is not int
            or any(type(value[k]) is not int for k in ('Score', 'Cap', 'Quality'))
            or not 0 <= value['Score'] <= 100 or not 1 <= value['Quality'] <= value['Cap'] <= 4):
        raise ValueError('Invalid authoritative frozen batch')
    return value


def healing_snapshot(reply):
    """Health and medication from ONE player serialization, never two RCON ticks."""
    player = read_data(reply)
    if not isinstance(player, dict):
        raise ValueError('Expected atomic player compound')
    health = player.get('Health')
    if type(health) not in (int, float) or not math.isfinite(health) or health < 0:
        raise ValueError('Missing or invalid atomic player health')
    persistent = player.get('NeoForgeData')
    medication = persistent.get('myvillage_medicine') if isinstance(persistent, dict) else None
    if not isinstance(medication, dict):
        raise ValueError('Missing atomic medication compound')
    return {'health': health, 'medicine': medication}


def healing_delta(before, after, interval):
    """Count delivered heals by saved online counters, including a partial period.

    Vanilla /tick freeze excludes players, so medicine continues even while the
    world is frozen. Compare naturally running atomic snapshots instead.
    """
    if type(interval) is not int or interval <= 0:
        raise ValueError('Healing interval must be positive')
    for snapshot in (before, after):
        health, med = snapshot['health'], snapshot['medicine']
        if type(health) not in (int, float) or not math.isfinite(health) or health < 0:
            raise ValueError('Invalid atomic health')
        if (med.get('effect') != 'yangyuan' or type(med.get('effect_ticks')) is not int
                or med['effect_ticks'] <= 0 or type(med.get('quality')) is not int
                or not 1 <= med['quality'] <= 4 or type(med.get('healing_elapsed')) is not int
                or not 0 <= med['healing_elapsed'] < interval):
            raise ValueError('Healing snapshot must retain an active valid quality/phase')
    first, last = before['medicine'], after['medicine']
    if first['quality'] != last['quality']:
        raise ValueError('Healing quality changed between atomic snapshots')
    ticks = first['effect_ticks']-last['effect_ticks']
    if ticks <= 0:
        raise ValueError('Healing snapshots must advance online ticks')
    heals, elapsed = divmod(first['healing_elapsed']+ticks, interval)
    if last['healing_elapsed'] != elapsed:
        raise ValueError('Healing phase disagrees with authoritative elapsed ticks')
    return {'ticks': ticks, 'heals': heals, 'health_gain': after['health']-before['health'],
            'quality': first['quality'], 'before_phase': first['healing_elapsed'], 'after_phase': elapsed}


def menu_point(size, x, y):
    width, height = size
    scale = max(1, min(width//320, height//240))
    left, top = (width//scale-212)//2, (height//scale-222)//2
    return scale*(left+x), scale*(top+y)


def inventory_point(size, slot, recipe_book_open=False):
    """Vanilla InventoryScreen hotbar centre, including a visible recipe book."""
    if type(slot) is not int or not 0 <= slot <= 8:
        raise ValueError('Expected vanilla hotbar slot 0..8')
    width, height = size
    scale = max(1, min(width//320, height//240))
    gui_width, gui_height = width//scale, height//scale
    left = (gui_width-176)//2
    if recipe_book_open and gui_width >= 379:
        left = 177+(gui_width-176-200)//2
    top = (gui_height-166)//2
    return scale*(left+16+18*slot), scale*(top+150)


class SecondClient:
    """Only this actor's two spawned process trees are killed; lock stays with cs."""
    username = 'AlchemyGuest'
    def __init__(self, evidence, timeout=240):
        self.e, self.timeout, self.processes, self.handles = evidence, timeout, [], []
        self.identities = {}
        self.out = evidence.out/'second-client'; self.out.mkdir()
        self.directory = ROOT/'run-combat-capture/alchemy-quality-guest'
        self.directory.mkdir(parents=True, exist_ok=True)
        self.game = None

    def spawn(self, argv, path, **kwargs):
        stream = path.open('w'); self.handles.append(stream)
        process = subprocess.Popen(argv, stdout=stream, stderr=subprocess.STDOUT, **kwargs)
        started = procs.start_time(process.pid)
        self.processes.append((process, started))
        self.identities[process.pid] = started
        return process

    def remember_descendants(self):
        for process, started in self.processes:
            if procs.is_alive(process.pid, started):
                for pid in procs.descendants(process.pid):
                    self.identities.setdefault(pid, procs.start_time(pid))

    def __enter__(self):
        try:
            started = time.monotonic(); procs.set_child_subreaper()
            display = ':'+str(procs.free_display())
            self.spawn(['Xvfb', display, '-screen', '0', '960x540x24', '+extension', 'GLX', '-nolisten', 'tcp', '-noreset'],
                       self.out/'xvfb.log')
            self.e.poll(lambda: cs.x_display_ready(display), bool, 20, 'second X display')
            cs.merge_options(self.directory/'options.txt', {**cs.CLIENT_OPTIONS, 'lang': 'en_us',
                             'renderDistance': '3', 'simulationDistance': '3', 'maxFps': '20'})
            argv = cs.gradle_argv('runClient', '-Xmx512m', [
                '-Pcombat_smoke_server=127.0.0.1:'+str(self.e.s.state['server_port']),
                '-Pcombat_smoke_game_dir='+str(self.directory), '-Pcombat_smoke_username='+self.username])
            client = self.spawn(argv, self.out/'client-process.log', cwd=ROOT,
                                env=dict(os.environ, DISPLAY=display, JAVA_TOOL_OPTIONS='-Xmx768m'))
            def joined():
                self.remember_descendants()
                if client.poll() is not None:
                    raise RuntimeError('Second client exited: '+str(client.returncode))
                return self.username in self.e.rc('list')
            self.e.poll(joined, bool, self.timeout, 'second actual player joins')
            self.game = Game(display, self.directory/'logs/latest.log', (960, 540))
            if not (self.game.wait_ingame(45, poll=1) or self.game.recover_grab()):
                raise RuntimeError('Second client did not reach actual in-game state')
            time.sleep(3)
            self.e.report['phases'].append({'phase': 'second_client_start', 'wall_s': round(time.monotonic()-started, 3),
                'username': self.username, 'separate_directory': str(self.directory.relative_to(ROOT)),
                'heap_cap': '768 MiB', 'max_fps': 20})
            return self
        except BaseException:
            self.__exit__(*sys.exc_info()); raise

    def __exit__(self, *error):
        self.remember_descendants()
        targets = [(pid, started, 'quality guest owned process') for pid, started in self.identities.items()]
        procs.kill_pids(targets, grace=8)
        for process, _ in self.processes:
            try: process.wait(timeout=2)
            except subprocess.TimeoutExpired: pass
        for stream in self.handles:
            stream.close()
        log = self.directory/'logs/latest.log'
        if log.exists(): shutil.copy2(log, self.out/'client.log')
        self.e.check('second_client_owned_process_cleanup', all(not procs.is_alive(pid, started) for pid, started, _ in targets),
                     {'targets': len(targets)})


class QualityEvidence(AlchemyEvidence):
    def __init__(self, out, cases=None):
        selected = select_cases(cases)
        if out.exists() and any(out.iterdir()):
            raise ValueError('Evidence directory must be empty; retain earlier runs: '+str(out))
        super().__init__(out, 'zh_cn')
        self.report.update(kind='alchemy_quality', method='real clients; mapped right-click/start/shift-click/pill use; RCON fixtures/readback only; no DevBridge',
            multiplayer='developer_mapped_two_client_checks_pending', legacy_save_live_migration='not_verified',
            owner_feel='not_verified', owner_multiplayer='not_verified',
            selected_cases=selected, completed_cases=[],
            case_status={name: 'not_run' if name in selected else 'not_selected' for name in ALCHEMY_CASES},
            coverage='full' if selected == list(ALCHEMY_CASES) else 'partial', full_acceptance=False)

    def check(self, name, passed, detail):
        super().check(name, passed, detail)
        if not passed:
            raise AssertionError(name)
        return True

    def case_phase(self, name, phase, operation):
        started = time.monotonic()
        row = {'phase': phase, 'case': name, 'status': 'running'}
        self.report['phases'].append(row)
        self.report['case_status'][name] = 'running'
        self.save()
        try:
            operation()
            row['status'] = 'pass'
        except BaseException as error:
            row.update(status='fail', error=type(error).__name__ + ': ' + str(error))
            self.report['case_status'][name] = 'fail'
            raise
        finally:
            row['wall_s'] = round(time.monotonic()-started, 3)
            self.save()

    def complete_case(self, name):
        self.report['case_status'][name] = 'pass'
        self.report['completed_cases'].append(name)
        self.save()

    def prepared_case(self, operation):
        self.prepare_scene()
        operation()

    def run_selected(self):
        selected = self.report['selected_cases']
        self.start()
        for name, operation in (('brewing', self.initial), ('multiplayer', self.two_clients), ('medicine', self.medicines)):
            if name in selected:
                self.case_phase(name, name, operation if name == 'brewing' else lambda op=operation: self.prepared_case(op))
                self.complete_case(name)
        if 'ui' in selected:
            self.case_phase('ui', 'ui_zh_cn', lambda: self.prepared_case(lambda: self.capture_ui('zh_cn')))
        if 'persistence' in selected:
            self.case_phase('persistence', 'save_fixture', lambda: self.prepared_case(self.prepare_restart))
        if 'persistence' in selected or 'ui' in selected:
            # One actual restart serves persistence and the second UI language.
            self.stop()
            self.language = 'en_us'
            self.start(reuse=True)
        if 'persistence' in selected:
            self.case_phase('persistence', 'restore_and_complete', self.after_restart)
            self.complete_case('persistence')
        if 'ui' in selected:
            self.case_phase('ui', 'ui_en_us', lambda: self.prepared_case(lambda: self.capture_ui('en_us')))
            self.complete_case('ui')

    def capture_ui(self, language):
        self.layouts(language)
        self.pill_tooltips(language)

    def prepare_scene(self, awakened=True):
        user = self.s.username
        for command in ('tick rate 20', f'setblock {POS} minecraft:air',
                        *scene.world_rules(), *scene.player_setup(user, 'minecraft:air'),
                        'gamerule naturalRegeneration false', 'myvillage world pause', 'forceload add -16 -16 32 32',
                        f'setblock {POS} myvillage:alchemy_furnace[facing=north]'):
            self.rc(command)
        if awakened:
            self.s.game.key('x')
            self.wait_medication()
            for command in (f'myvillage cultivation awaken {user}', f'myvillage cultivation initiate {user}',
                            f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1',
                            f'myvillage cultivation crafting set {user} alchemy 100'):
                self.rc(command)

    def block_items(self):
        return quality_stacks(self.rc(f'data get block {POS} Items'))

    def inventory(self, username=None):
        return quality_stacks(self.rc(f'data get entity {username or self.s.username} Inventory'))

    def batch(self):
        return read_batch(self.rc(f'data get block {POS} Batch'))

    def med(self):
        reply = self.rc(f'data get entity {self.s.username} {MEDICINE}')
        if reply.startswith('Found no elements matching'):
            return {'cooldown_ticks': 0, 'effect': '', 'effect_ticks': 0, 'healing_elapsed': 0, 'quality': 1}
        value = read_data(reply)
        if not isinstance(value, dict) or not all(k in value for k in ('cooldown_ticks', 'effect', 'effect_ticks', 'healing_elapsed')):
            raise ValueError('Incomplete medication counters')
        value.setdefault('quality', 1)
        return value

    def click(self, x, y, *, game=None, shift=False, button='1'):
        game = game or self.s.game
        px, py = menu_point(game.size, x, y)
        game.xdo('mousemove', str(px), str(py))
        if shift: game.xdo('keydown', 'Shift_L')
        try: game.xdo('click', button)
        finally:
            if shift: game.xdo('keyup', 'Shift_L')
        time.sleep(.35)

    def open_furnace(self, game=None, username=None):
        game, username = game or self.s.game, username or self.s.username
        position = MAIN_VIEW if username == self.s.username else GUEST_VIEW
        self.rc(f'tp {username} {position}'); time.sleep(.4)
        game.require_ingame('mapped quality furnace right click'); game.xdo('click', '3')
        try:
            self.poll(game.pointer_grabbed, lambda value: value is False, 10, 'mapped furnace menu')
        except Exception:
            window = game.xdo('search', '--name', 'Minecraft').split()[0]
            subprocess.run(['import', '-silent', '-window', window,
                            str(self.out/f'failed-menu-{username}.png')], env=game.env, check=False, timeout=15)
            raise

    def close_menu(self, game=None):
        game = game or self.s.game; game.xdo('key', 'Escape'); time.sleep(.35)
        game.require_ingame('close quality furnace')

    def mapped_start(self, game=None):
        self.click(106, 113, game=game)

    def begin(self):
        self.open_furnace(); self.mapped_start(); self.close_menu()
        return self.poll(self.batch, lambda value: value is not None, 8, 'mapped manual batch')

    def wait_complete(self, recipe):
        rows = self.poll(self.block_items, lambda rows: rows.get(4, {}).get('id') == recipe['output'],
                         recipe['duration_ticks']/20+15, 'quality completion '+recipe['id'])
        self.poll(self.batch, lambda value: value is None, 5, 'one batch retired')
        return rows

    def assert_result(self, recipe, batch, rows, label):
        stack = rows.get(4, {})
        self.check(label, stack.get('id') == recipe['output'] and stack.get('count') == recipe['output_count']
                   and pill_quality(stack) == batch['Quality']
                   and (batch['Quality'] != 1 or QUALITY not in stack.get('components', {})),
                   {'saved_batch': batch, 'result': stack})

    def initial(self):
        user = self.s.username; recipe = self.rules['recipes'][0]
        self.prepare_scene(awakened=False)
        self.fixture(recipe, herbs=2); self.slot(3, 'myvillage:low_grade_spirit_stone', 2)
        before = self.block_items(); time.sleep(1)
        self.check('ready_furnace_never_autostarts', self.batch() is None and self.progress() == 0 and self.block_items() == before, before)
        self.open_furnace(); self.mapped_start(); self.capture('unawakened-start-rejected.png'); self.close_menu()
        self.check('mapped_unawakened_start_rejected_without_cost', self.batch() is None and self.block_items() == before, self.block_items())
        for command in (f'myvillage cultivation awaken {user}', f'myvillage cultivation initiate {user}',
                        f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1',
                        f'myvillage cultivation crafting set {user} alchemy 0'):
            self.rc(command)
        batch = self.begin(); owner = read_data(self.rc(f'data get entity {user} UUID'))
        self.check('mapped_start_snapshots_brewer_score_and_cap', batch['Brewer'] == owner and batch['Score'] == 0
                   and batch['Cap'] == batch['Quality'] == 1, batch)
        self.rc(f'myvillage cultivation crafting set {user} alchemy 100')
        self.open_furnace(); locked = self.block_items(); inventory = self.inventory()
        for x, y in SLOTS[:4]: self.click(x+8, y+8, shift=True)
        self.mapped_start(); self.mapped_start()
        self.check('mapped_duplicate_and_all_input_transfers_keep_batch', self.batch() == batch and self.block_items() == locked
                   and self.inventory() == inventory, {'before': batch, 'after': self.batch(), 'items': self.block_items()})
        self.capture('frozen-low-cap-active.png'); self.close_menu()
        self.slot(4, 'minecraft:cobblestone', 64)
        paused = self.progress(); blocked_items = self.block_items(); time.sleep(1)
        self.check('active_output_block_pauses_without_reroll_or_cost', self.batch() == batch and self.progress() == paused
                   and self.block_items() == blocked_items, {'batch': self.batch(), 'progress': self.progress()})
        self.slot(4, 'minecraft:air')
        rows = self.wait_complete(recipe); self.assert_result(recipe, batch, rows, 'cap1_output_legacy_stack_identity')
        self.open_furnace(); self.click(186, 46, shift=True); self.close_menu()
        remaining = self.block_items(); time.sleep(1)
        self.check('completed_batch_never_chains_after_output_removed', self.batch() is None and self.progress() == 0
                   and self.block_items() == remaining and remaining[1]['count'] == 1, remaining)
        self.check('mapped_output_retains_legacy_absent_component', any(stack['id'] == recipe['output'] and pill_quality(stack) == 1
                   and QUALITY not in stack.get('components', {}) for stack in self.inventory().values()), self.inventory())
        self.rc('tick rate 80')
        try:
            for recipe in self.rules['recipes']:
                self.fixture(recipe)
                if recipe['id'] != 'huiling':
                    self.slot(1, recipe['ingredients'][1]); self.slot(2, recipe['ingredients'][0])
                batch = self.begin(); rows = self.wait_complete(recipe)
                self.assert_result(recipe, batch, rows, 'quality_result_'+recipe['id'])
                self.check('quality_recipe_cost_'+recipe['id'], set(rows) == {0, 4} and rows[0]['count'] == 1, rows)
        finally:
            self.rc('tick rate 20')

    def layouts(self, language):
        temporary = self.batch() is None
        if temporary:
            self.fixture(self.rules['recipes'][0]); self.begin(); self.slot(4, 'minecraft:cobblestone', 64)
        self.open_furnace(); game = self.s.game; window = game.xdo('search', '--name', 'Minecraft').split()[0]
        try:
            for width, height in ((960, 540), (854, 480), (640, 480)):
                game.xdo('windowsize', window, str(width), str(height)); game.xdo('windowmove', window, '0', '0')
                game.size = (width, height); time.sleep(.8)
                self.capture(f'{language}_{width//2}x{height//2}_quality-menu.png')
                self.check(f'layout_{language}_{width//2}x{height//2}', game.pointer_grabbed() is False,
                           {'gui': [width//2, height//2], 'visual_legibility': 'requires_downloaded_PNG_inspection'})
        finally:
            game.xdo('windowsize', window, '960', '540'); game.size = (960, 540); time.sleep(.3); self.close_menu()
        if temporary:
            self.slot(4, 'minecraft:air'); self.rc('tick rate 80')
            try: self.wait_complete(self.rules['recipes'][0])
            finally: self.rc('tick rate 20')

    def pill_tooltips(self, language):
        """Actual E/hover, no item-use input; do not move cursor before capture."""
        started = time.monotonic(); game = self.s.game; user = self.s.username
        if language != self.language:
            raise ValueError('Tooltip language does not match active capture session')
        options = ROOT/'run-combat-capture/client/options.txt'
        bindings = dict(line.split(':', 1) for line in options.read_text().splitlines() if ':' in line)
        if bindings.get('key_key.inventory', 'key.keyboard.e') != 'key.keyboard.e':
            raise RuntimeError('Inventory E binding differs; no binding changed')
        self.report.setdefault('verified_bindings', {})['inventory'] = 'e'
        for slot, recipe in enumerate(self.rules['recipes']):
            self.rc(f'item replace entity {user} hotbar.{slot} with {recipe["output"]}[{QUALITY}=4] 2')
        inventory = self.inventory(); medication = self.med()
        self.check('tooltip_admin_quality4_fixtures_'+language,
                   all(inventory.get(slot, {}).get('id') == recipe['output'] and
                       inventory[slot]['count'] == 2 and pill_quality(inventory[slot]) == 4
                       for slot, recipe in enumerate(self.rules['recipes'])), inventory)
        game.require_ingame('mapped E opens pill tooltip inventory'); game.key('e')
        self.poll(game.pointer_grabbed, lambda grabbed: grabbed is False, 8, 'mapped inventory screen')
        images = []
        try:
            book_value = read_data(self.rc(f'data get entity {user} recipeBook.isGuiOpen'))
            if type(book_value) is not int or book_value not in (0, 1):
                raise ValueError('Invalid saved recipe-book visibility')
            book = bool(book_value)
            window = game.xdo('search', '--name', 'Minecraft').split()[0]
            for slot, recipe in enumerate(self.rules['recipes']):
                x, y = inventory_point(game.size, slot, book)
                game.xdo('mousemove', str(x), str(y)); time.sleep(.6)
                name = f'{language}_quality4_{recipe["id"]}_tooltip.png'
                # capture() intentionally clears hover; a raw window grab retains it.
                subprocess.run(['import', '-silent', '-window', window, str(self.out/name)],
                               env=game.env, check=True, timeout=30)
                images.append({'file': name, 'item': recipe['output'], 'quality': 4,
                               'hover_pixels': [x, y], 'recipe_book_open': book})
        finally:
            game.xdo('key', 'Escape'); time.sleep(.3)
            game.require_ingame('close pill tooltip inventory')
        after, med_after = self.inventory(), self.med()
        self.check('mapped_pill_tooltips_never_consume_'+language, after == inventory
                   and med_after['cooldown_ticks'] <= medication['cooldown_ticks']
                   and med_after['effect_ticks'] <= medication['effect_ticks']
                   and med_after['quality'] == medication['quality'],
                   {'before_inventory': inventory, 'after_inventory': after,
                    'before_medicine': medication, 'after_medicine': med_after,
                    'visual_name_effect_legibility': 'requires_downloaded_PNG_inspection'})
        self.report['phases'].append({'phase': 'pill_quality_tooltips', 'language': language,
                                     'wall_s': round(time.monotonic()-started, 3), 'images': images,
                                     'fixtures': 'administrator quality4 items; actual mapped E and hover; no pill use'})
        self.save()

    def held_quality(self, pill, quality=None):
        user = self.s.username
        suffix = '' if quality is None else '['+QUALITY+'='+str(quality)+']'
        self.rc(f'tp {user} .5 -60 .5 180 0')
        self.rc(f'item replace entity {user} hotbar.0 with myvillage:pill_{pill}{suffix} 2')
        self.s.game.key('1'); self.s.game.require_ingame('mapped quality pill use')
        self.s.game.xdo('mousedown', '3')
        try: time.sleep(self.rules['use_ticks']/20+.8)
        finally: self.s.game.xdo('mouseup', '3')
        time.sleep(.2)
        return self.inventory().get(0, {}).get('count', 0)

    def medicines(self):
        user = self.s.username; observed = {}
        # Natural online regeneration exists; use a bounded tolerance below its one-point cadence.
        for quality in (None, 4):
            self.wait_medication(); self.rc(f'myvillage cultivation qi set {user} 0')
            before = self.profile(); count = self.held_quality('huiling', quality); after = self.profile()
            factor = self.rules['quality']['strength_multipliers'][(quality or 1)-1]
            expected = max(1, math.floor(before['power_cap']*self.rules['effects']['huiling']['power_fraction']*factor))
            observed[str(quality)] = after['power']
            self.check('mapped_huiling_strength_'+str(quality or 'legacy'), count == 1 and expected <= after['power'] <= expected+3,
                       {'expected': expected, 'actual': after['power'], 'quality': quality, 'medicine': self.med()})
        self.check('quality4_huiling_stronger_than_legacy', observed['4'] > observed['None'], observed)
        heals = {}
        for quality in (None, 4):
            self.wait_medication(); self.rc(f'damage {user} 8 minecraft:generic')
            count = self.held_quality('yangyuan', quality)
            interval = self.rules['effects']['yangyuan']['interval_ticks']
            maximum = self.number(f'attribute {user} minecraft:generic.max_health get')
            before = healing_snapshot(self.rc(f'data get entity {user}'))
            def sampled():
                snapshot = healing_snapshot(self.rc(f'data get entity {user}'))
                med = snapshot['medicine']
                if med.get('effect') != 'yangyuan' or med.get('effect_ticks', 0) <= 0:
                    raise RuntimeError('Healing ended before atomic interval measurement')
                return snapshot
            after = self.poll(sampled,
                              lambda sample: before['medicine']['effect_ticks']-sample['medicine']['effect_ticks'] >= interval,
                              8, 'authoritative online healing interval')
            measured = healing_delta(before, after, interval)
            factor = self.rules['quality']['strength_multipliers'][(quality or 1)-1]
            expected_per_heal = maximum*self.rules['effects']['yangyuan']['heal_fraction']*factor
            expected = expected_per_heal*measured['heals']
            heals[str(quality)] = measured['health_gain']/measured['heals']
            self.check('mapped_yangyuan_per_interval_'+str(quality or 'legacy'), count == 1
                       and measured['heals'] >= 1 and close(measured['health_gain'], expected)
                       and measured['quality'] == (quality or 1),
                       {'before_atomic': before, 'after_atomic': after, 'measured': measured,
                        'expected_total': expected, 'expected_per_heal': expected_per_heal,
                        'method': 'same-command Health+medicine snapshots; elapsed online counters; no tick freeze'})
        self.check('quality4_healing_stronger_than_legacy', heals['4'] > heals['None'], heals)
        # Exact readback checks below test multiplier, while pure Java tests cover rounding/cap edges.
        stage = json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/realm/qi_refining.json').read_text())['stages'][0]
        stability = {}
        for quality in (None, 4):
            self.wait_medication()
            self.rc(f'myvillage cultivation setprogress {user} {stage["cultivation_cap"]}')
            self.rc(f'myvillage cultivation setstability {user} 0')
            count = self.held_quality('ningshen', quality); med = self.med(); initial = self.profile()
            self.s.game.key('v')
            gained = self.poll(self.profile, lambda state: state['stability'] > 0, 15, 'mapped ningshen meditation settlement')
            self.s.game.key('x')
            factor = 1+(self.rules['effects']['ningshen']['stability_multiplier']-1)*self.rules['quality']['strength_multipliers'][(quality or 1)-1]
            expected = math.floor(initial['affinity']*factor)
            stability[str(quality)] = {'gain': gained['stability'], 'expected_per_settlement': expected}
            self.check('mapped_ningshen_quality_'+str(quality or 'legacy'), count == 1 and med['quality'] == (quality or 1)
                       and gained['progress'] == stage['cultivation_cap'] and gained['stability'] >= expected
                       and gained['stability'] % expected == 0, {'initial': initial, 'after': gained, 'factor': factor, 'medicine': med})
        self.check('quality4_stability_bonus_stronger_than_legacy', stability['4']['expected_per_settlement'] > stability['None']['expected_per_settlement'], stability)

    def two_clients(self):
        recipe = self.rules['recipes'][0]; user = self.s.username
        self.slot(4, 'minecraft:air')
        with SecondClient(self) as guest:
            for command in (*scene.player_setup(guest.username, 'minecraft:air'),
                            f'tp {guest.username} {GUEST_VIEW}',
                            f'myvillage cultivation awaken {guest.username}',
                            f'myvillage cultivation crafting set {guest.username} alchemy 100',
                            f'myvillage cultivation crafting set {user} alchemy 0'):
                self.rc(command)
            guest_position = read_data(self.rc(f'data get entity {guest.username} Pos'))
            self.check('guest_stands_clear_of_main_furnace_ray', guest_position[0] < -1,
                       {'guest': guest_position, 'main_view': MAIN_VIEW})
            self.fixture(recipe)
            main_batch = self.begin()
            self.open_furnace(guest.game, guest.username)
            guest_before = self.inventory(guest.username); inputs = self.block_items()
            self.mapped_start(guest.game)
            for x, y in SLOTS[:4]: self.click(x+8, y+8, game=guest.game, shift=True)
            self.check('second_real_client_cannot_replace_owner_or_take_inputs', self.batch() == main_batch
                       and self.block_items() == inputs and self.inventory(guest.username) == guest_before,
                       {'owner_batch': main_batch, 'after_guest_clicks': self.batch(), 'players': self.rc('list')})
            self.close_menu(guest.game)
            self.rc('tick rate 80'); self.wait_complete(recipe); self.rc('tick rate 20')
            self.fixture(recipe)
            self.open_furnace(guest.game, guest.username); self.mapped_start(guest.game); self.close_menu(guest.game)
            batch = self.poll(self.batch, lambda batch: batch is not None, 8, 'guest mapped start')
            guest_uuid = read_data(self.rc(f'data get entity {guest.username} UUID'))
            self.open_furnace(); self.mapped_start(); self.click(60, 46, shift=True); self.capture('two-client-other-brewer-active.png'); self.close_menu()
            self.check('guest_mapped_start_owns_new_batch_independent_of_viewer', batch['Brewer'] == guest_uuid
                       and batch['Score'] == 100 and batch['Cap'] == 4 and self.batch() == batch,
                       {'guest_batch': batch, 'after_main_attempts': self.batch(), 'players': self.rc('list')})
            self.rc(f'myvillage cultivation crafting set {guest.username} alchemy 0')
            self.rc('tick rate 80'); rows = self.wait_complete(recipe); self.rc('tick rate 20')
            self.assert_result(recipe, batch, rows, 'two_client_original_quality_survives_brewer_score_change')
        self.report['multiplayer'] = 'developer_two_real_clients_mapped_interactions_observed_owner_acceptance_pending'

    def prepare_restart(self):
        recipe = self.rules['recipes'][0]
        # Persistence must work without first running the medicine or UI cases.
        stage = json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/realm/qi_refining.json').read_text())['stages'][0]
        self.rc(f'myvillage cultivation setprogress {self.s.username} {stage["cultivation_cap"]}')
        self.rc(f'myvillage cultivation setstability {self.s.username} 0')
        count = self.held_quality('ningshen', 4)
        medication = self.med()
        self.check('persistence_fixture_mapped_quality4_use', count == 1 and medication['quality'] == 4
                   and medication['effect'] == 'ningshen' and medication['effect_ticks'] > 0, medication)
        for slot, pill in enumerate(self.rules['recipes']):
            self.rc(f'item replace entity {self.s.username} hotbar.{slot} with {pill["output"]}[{QUALITY}=4] 2')
        self.fixture(recipe); self.rc(f'myvillage cultivation crafting set {self.s.username} alchemy 100')
        batch = self.begin()
        self.poll(self.progress, lambda progress: progress >= 30, 10, 'saved partial quality batch')
        self.slot(4, 'minecraft:cobblestone', 64)
        self.report['before_restart'] = {'items': self.block_items(), 'progress': self.progress(), 'batch': batch, 'medicine': self.med(), 'inventory': self.inventory()}
        self.rc('save-all flush'); self.save()

    def after_restart(self):
        before = self.report['before_restart']; actual_items = self.block_items(); actual_batch = self.batch(); med = self.med()
        self.check('restart_preserves_whole_batch_and_all_item_components', actual_batch == before['batch']
                   and actual_items == {int(key): value for key, value in before['items'].items()}
                   and self.progress() == before['progress'], {'before': before, 'after_batch': actual_batch, 'after_items': actual_items})
        self.check('restart_preserves_player_pill_components', self.inventory() == {int(key): value for key, value in before['inventory'].items()},
                   {'before': before['inventory'], 'after': self.inventory()})
        self.check('restart_preserves_sustained_quality_and_online_counters', med['quality'] == 4 and med['effect'] == 'ningshen'
                   and 0 < med['effect_ticks'] <= before['medicine']['effect_ticks'] and med['effect_ticks'] >= before['medicine']['effect_ticks']-400,
                   {'before': before['medicine'], 'after': med})
        self.slot(4, 'minecraft:air'); self.rc('tick rate 80')
        rows = self.wait_complete(self.rules['recipes'][0]); self.rc('tick rate 20')
        self.assert_result(self.rules['recipes'][0], before['batch'], rows, 'saved_quality_is_exact_output_after_restart')

    def finish(self):
        missing = [name for name in self.report['selected_cases'] if self.report['case_status'][name] != 'pass']
        if missing and not self.report['failures']:
            self.report['failures'].append('Selected cases incomplete: ' + ','.join(missing))
        self.report['unrun_cases'] = [name for name in ALCHEMY_CASES if name not in self.report['completed_cases']]
        self.report['full_acceptance'] = (self.report['coverage'] == 'full' and not missing
                                          and not self.report['failures'] and all(row['pass'] for row in self.report['checks']))
        super().finish()
        page = self.out/'index.html'
        text = page.read_text().replace('Alchemy runtime evidence', 'Alchemy quality evidence').replace('炼丹运行验证', '成丹品质 · 运行验证')
        text = text.replace('玩家自然获取、外观、手感与多人验收未确认。', '真实双客户端交互仅为开发者证据；自然获取、外观、手感、平衡与玩家多人验收未确认。')
        text = text.replace('<ul>', '<p>覆盖：'+self.report['coverage']+'；已完成：'
                            +html.escape(', '.join(self.report['completed_cases']))+'。局部通过不能代替完整验收。</p><ul>', 1)
        page.write_text(text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/alchemy/quality')
    parser.add_argument('--cases', default='all', help='all or comma-separated: '+','.join(ALCHEMY_CASES))
    args = parser.parse_args(argv)
    try:
        evidence = QualityEvidence(args.out, args.cases)
    except ValueError as error:
        parser.error(str(error))
    try:
        evidence.run_selected()
    except BaseException as error:
        evidence.report['failures'].append(type(error).__name__ + ': ' + str(error)); raise
    finally:
        if evidence.s is not None:
            try: evidence.rc('tick rate 20')
            except Exception as error: evidence.report['failures'].append('tick cleanup: '+str(error))
            try: evidence.stop()
            except Exception as error: evidence.report['failures'].append('session cleanup: '+str(error))
        evidence.finish()
    return 0 if evidence.report['status'] == 'pass' else 1


if __name__ == '__main__':
    raise SystemExit(main())
