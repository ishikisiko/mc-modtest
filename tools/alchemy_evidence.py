#!/usr/bin/env python3
"""Alchemy developer evidence with actual mapped menu/pill input and saved-server readback.

Run clean stage-1 acceptance first. This owns the shared capture lock across two
isolated client/server sessions (restart reuses only its own capture world).
RCON supplies administrator fixtures, never simulates player item use. Survival
acquisition, owner appearance/balance and multiplayer remain not_verified.
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import math
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.combat_capture import procs, scene, session as cs
from tools.realm_stats_evidence import Evidence, close
from tools.world_sim_scripture_evidence import split_items
from tools.golden_core_evidence import parse_profile

POS = '0 -60 3'
MEDICINE = 'NeoForgeData.myvillage_medicine'


def meditation_bindings(options, registration):
    """Fresh options files can omit custom keys; Minecraft uses the registered defaults."""
    verified = {}
    for action, expected in [('start_normal_meditation', 'v'), ('stop_meditation', 'x')]:
        option = 'key_key.myvillage.' + action
        actual = options.get(option)
        source = 'options'
        if actual is None:
            pattern = (r'"key\.myvillage\.' + re.escape(action)
                       + r'"\s*,\s*InputConstants\.Type\.KEYSYM\s*,\s*GLFW\.GLFW_KEY_([A-Z])\s*,')
            match = re.search(pattern, registration)
            if match is None:
                raise RuntimeError('Cannot establish registered default for ' + action)
            actual = 'key.keyboard.' + match.group(1).lower()
            source = 'registered_default'
        if actual != 'key.keyboard.' + expected:
            raise RuntimeError('Unexpected mapped key for ' + action + ': ' + actual)
        verified[action] = {'key': expected, 'source': source}
    return verified


def stacks(reply: str) -> dict[int, dict]:
    """Decode only vanilla ItemStack Slot/id/count triples, rejecting partial evidence."""
    if 'data: ' not in reply:
        raise ValueError('Missing item-list readback: ' + reply)
    found = {}
    for row in split_items(reply):
        slot = re.search(r'\bSlot:\s*(-?\d+)[bB]?', row)
        item = re.search(r'\bid:\s*"([\w:./-]+)"', row)
        count = re.search(r'\bcount:\s*(\d+)', row)
        if not slot or not item or not count:
            raise ValueError('Incomplete stack readback: ' + row)
        key = int(slot.group(1))
        if key in found:
            raise ValueError('Duplicate slot in readback')
        found[key] = {'id': item.group(1), 'count': int(count.group(1))}
    return found


def medicine(reply: str) -> dict:
    if "Found no elements matching" in reply:
        return {'cooldown_ticks': 0, 'effect': '', 'effect_ticks': 0, 'healing_elapsed': 0}
    if 'data: ' not in reply:
        raise ValueError('Missing medicine readback: ' + reply)
    result = {}
    for key in ('cooldown_ticks', 'effect_ticks', 'healing_elapsed'):
        match = re.search(r'\b' + key + r':\s*(\d+)', reply)
        if not match:
            raise ValueError('Missing medicine counter: ' + key)
        result[key] = int(match.group(1))
    effect = re.search(r'\beffect:\s*"([a-z]*)"', reply)
    if not effect:
        raise ValueError('Missing medicine effect')
    result['effect'] = effect.group(1)
    return result


class AlchemyEvidence(Evidence):
    def __init__(self, out, language):
        super().__init__(out)
        self.language = language
        self.rules = json.loads((ROOT/'src/main/resources/data/myvillage/alchemy/rules.json').read_text())
        self.report.update(kind='alchemy_foundation', language=language,
                           method='mapped furnace right-click, shift-click and held pill use; RCON fixtures/readback; no DevBridge',
                           natural_acquisition='not_verified', owner_appearance='not_verified', owner_balance='not_verified')
        self.report.pop('natural_encounter_density', None)

    def start(self, reuse=False):
        started = time.monotonic()
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse,
                                         language=self.language, lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        options = ROOT/'run-combat-capture/client/options.txt'
        bindings = dict(line.split(':', 1) for line in options.read_text().splitlines() if ':' in line)
        registration = (ROOT/'src/main/java/com/example/myvillage/client/cultivation/ClientCultivationKeyMappings.java').read_text()
        self.report['binding_sources'] = meditation_bindings(bindings, registration)
        self.report['verified_bindings'] = {'start_normal_meditation': 'v', 'stop_meditation': 'x'}
        self.sessions += 1
        self.report['phases'].append({'phase': 'restart_setup' if reuse else 'initial_setup',
                                     'wall_s': round(time.monotonic()-started, 3),
                                     'capture_timings': state.get('timings', {})})
        self.save()

    def poll(self, getter, predicate, timeout, label):
        deadline = time.monotonic() + timeout
        last = None
        while time.monotonic() < deadline:
            last = getter()
            if predicate(last):
                return last
            time.sleep(.25)
        raise RuntimeError(f'Timed out {label}: {last}')

    def block_items(self):
        return stacks(self.rc(f'data get block {POS} Items'))

    def inventory(self):
        return stacks(self.rc(f'data get entity {self.s.username} Inventory'))

    def med(self):
        return medicine(self.rc(f'data get entity {self.s.username} {MEDICINE}'))

    def profile(self):
        return parse_profile(self.rc(f'myvillage cultivation info {self.s.username}'))

    def progress(self):
        return int(self.number(f'data get block {POS} Progress'))

    def slot(self, slot, item, count=1):
        self.rc(f'item replace block {POS} container.{slot} with {item} {count}')

    def fixture(self, recipe, *, herbs=1, fuel=True):
        self.slot(0, recipe['formula'])
        for i, item in enumerate(recipe['ingredients'], 1):
            self.slot(i, item, herbs)
        self.slot(3, 'myvillage:low_grade_spirit_stone' if fuel else 'minecraft:air', recipe['fuel_count'])
        self.slot(4, 'minecraft:air')

    def capture(self, name):
        game = self.s.game
        game.xdo('mousemove', '0', '0')
        time.sleep(.15)
        window = game.xdo('search', '--name', 'Minecraft').split()[0]
        subprocess.run(['import', '-silent', '-window', window, str(self.out/name)],
                       env=game.env, check=True, timeout=30)

    def menu_click(self, x, y, shift=False):
        # Capture defaults: 960x540, vanilla automatic GUI scale 2 -> GUI480x270.
        width, height = self.s.game.size
        scale = max(1, min(width//320, height//240))
        left = (width//scale - 212)//2
        top = (height//scale - 222)//2
        game = self.s.game
        game.xdo('mousemove', str(scale*(left+x+8)), str(scale*(top+y+8)))
        if shift:
            game.xdo('keydown', 'Shift_L')
        try:
            game.xdo('click', '1')
        finally:
            if shift:
                game.xdo('keyup', 'Shift_L')
        time.sleep(.4)

    def open_furnace(self):
        self.rc(f'tp {self.s.username} .5 -60 .5 0 20.5')
        time.sleep(.3)
        self.s.game.require_ingame('open furnace')
        self.s.game.xdo('click', '3')
        self.poll(self.s.game.pointer_grabbed, lambda grabbed: grabbed is False, 8, 'mapped furnace menu')

    def close_menu(self):
        self.s.game.xdo('key', 'Escape')
        time.sleep(.3)
        self.s.game.require_ingame('close furnace menu')

    def initial(self):
        started = time.monotonic()
        user = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(user, 'minecraft:air'),
                    'gamerule naturalRegeneration false', 'myvillage world pause',
                    'forceload add -16 -16 32 32', f'myvillage cultivation awaken {user}',
                    f'myvillage cultivation initiate {user}',
                    f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1',
                    f'setblock {POS} myvillage:alchemy_furnace[facing=north]'):
            self.rc(cmd)
        self.poll(self.block_items, lambda rows: rows == {}, 10, 'furnace entity')
        self.rc(f'tp {user} -1.5 -60 1.5 -45 21.6')
        time.sleep(2)
        self.capture('placed-furnace.png')
        recipe = self.rules['recipes'][0]
        self.fixture(recipe, fuel=False)
        self.rc(f'item replace entity {user} hotbar.0 with myvillage:low_grade_spirit_stone 1')
        self.s.game.key('1')
        self.open_furnace()
        self.capture('furnace-ready.png')
        self.menu_click(26, 198, shift=True)
        self.menu_click(98, 105)  # Start button centre (106,113), no implicit auto-start.
        self.poll(self.progress, lambda n: n > 0, 8, 'mapped start begins brewing')
        self.capture('furnace-brewing.png')
        completed = self.poll(self.block_items, lambda rows: rows.get(4, {}).get('count') == recipe['output_count'],
                              recipe['duration_ticks']/20 + 15, 'first batch')
        self.check('mapped_fuel_brews_and_retains_formula',
                   completed == {0: {'id': recipe['formula'], 'count': 1},
                                 4: {'id': recipe['output'], 'count': recipe['output_count']}}, completed)
        self.menu_click(178, 38, shift=True)
        self.menu_click(178, 38, shift=True)
        inventory = self.inventory()
        total = sum(s['count'] for s in inventory.values() if s['id'] == recipe['output'])
        self.check('mapped_output_transfer_exactly_once', 4 not in self.block_items() and total == recipe['output_count'], inventory)
        self.capture('furnace-complete.png')
        self.close_menu()
        # Three deterministic recipes, including reversed herb order; speed only the administrator fixture.
        self.rc('tick rate 80')
        for recipe in self.rules['recipes'][1:]:
            self.fixture(recipe)
            self.slot(1, recipe['ingredients'][1]); self.slot(2, recipe['ingredients'][0])
            self.open_furnace(); self.menu_click(98, 105); self.close_menu()
            done = self.poll(self.block_items, lambda rows: rows.get(4, {}).get('id') == recipe['output'], 30, recipe['id'])
            self.check('reverse_herbs_'+recipe['id'], done == {0: {'id': recipe['formula'], 'count': 1},
                         4: {'id': recipe['output'], 'count': recipe['output_count']}}, done)
        self.rc('tick rate 20')
        recipe = self.rules['recipes'][0]
        self.fixture(recipe)
        self.open_furnace(); self.menu_click(98, 105); self.close_menu()
        self.poll(self.progress, lambda p: p >= 30, 10, 'partial progress')
        before_inputs = self.block_items()
        self.slot(1, 'myvillage:sunroot')
        self.check('active_inputs_reject_admin_container_replace', self.block_items() == before_inputs,
                   self.block_items())
        self.slot(4, recipe['output'], 16)
        paused = self.progress(); before = self.block_items()
        time.sleep(1)
        self.check('full_output_pauses_without_cost', self.progress() == paused and self.block_items() == before,
                   {'progress': paused, 'items': before})
        self.slot(4, 'minecraft:air')
        self.poll(self.progress, lambda p: p > paused, 8, 'cleared output resumes')
        self.report['phases'].append({'phase': 'furnace_mapped_and_transaction_checks', 'wall_s': round(time.monotonic()-started, 3)})
        self.save()

    def held_pill(self, id):
        user = self.s.username
        self.rc(f'tp {user} .5 -60 .5 180 0')
        self.rc(f'item replace entity {user} hotbar.0 with myvillage:pill_{id} 2')
        self.s.game.key('1')
        self.s.game.require_ingame('consume medicine')
        self.s.game.xdo('mousedown', '3')
        try:
            time.sleep(self.rules['use_ticks']/20 + .8)
        finally:
            self.s.game.xdo('mouseup', '3')
        time.sleep(.2)
        return self.inventory().get(0, {}).get('count', 0)

    def wait_medication(self):
        self.rc('tick rate 100')
        try:
            return self.poll(self.med, lambda s: s['cooldown_ticks'] == 0 and s['effect_ticks'] == 0, 40, 'medicine interval')
        finally:
            self.rc('tick rate 20')

    def medicines(self):
        started = time.monotonic(); user = self.s.username
        self.rc(f'myvillage cultivation qi fill {user}')
        count = self.held_pill('huiling')
        self.check('full_power_rejects_without_item_or_interval', count == 2 and self.med()['cooldown_ticks'] == 0, self.med())
        self.rc(f'myvillage cultivation qi set {user} 0')
        before = self.profile()
        count = self.held_pill('huiling')
        after = self.profile(); med = self.med()
        expected = max(1, math.floor(before['power_cap']*self.rules['effects']['huiling']['power_fraction']))
        self.check('mapped_huiling_recovers_and_spends_one', count == 1 and expected <= after['power'] <= before['power_cap']
                   and med['cooldown_ticks'] > 0, {'before': before['power'], 'after': after['power'], 'expected_min': expected, 'medicine': med})
        self.rc(f'damage {user} 6 minecraft:generic')
        count = self.held_pill('yangyuan')
        self.check('cross_pill_shared_interval', count == 2 and self.med()['effect'] == '', self.med())
        self.wait_medication()
        before_hp = self.number(f'data get entity {user} Health')
        count = self.held_pill('yangyuan')
        self.poll(lambda: self.number(f'data get entity {user} Health'), lambda hp: hp > before_hp, 5, 'gradual healing')
        self.check('mapped_yangyuan_heals_gradually', count == 1 and self.med()['effect'] == 'yangyuan', self.med())
        self.rc(f'damage {user} 1 minecraft:generic')
        stopped = self.poll(self.med, lambda s: s['effect'] == '', 5, 'damage cancels medicine')
        hp = self.number(f'data get entity {user} Health'); time.sleep(1.3)
        self.check('damage_stops_healing_preserves_interval', stopped['cooldown_ticks'] > 0 and
                   close(self.number(f'data get entity {user} Health'), hp), stopped)
        self.wait_medication()
        self.rc(f'myvillage cultivation setprogress {user} 0')
        count = self.held_pill('ningshen')
        self.check('ningshen_rejects_before_progress_cap', count == 2 and self.med()['cooldown_ticks'] == 0, self.med())
        stage = json.loads((ROOT/'src/main/resources/data/myvillage/myvillage/realm/qi_refining.json').read_text())['stages'][0]
        self.rc(f'myvillage cultivation setprogress {user} {stage["cultivation_cap"]}')
        self.rc(f'myvillage cultivation setstability {user} 0')
        count = self.held_pill('ningshen')
        self.check('mapped_ningshen_starts', count == 1 and self.med()['effect'] == 'ningshen', self.med())
        self.s.game.key('v')
        boosted = self.poll(self.profile, lambda p: p['stability'] > 0, 15, 'boosted stability settlement')
        self.s.game.key('x')
        self.check('ningshen_affects_only_stability', boosted['progress'] == stage['cultivation_cap'] and
                   boosted['stability'] % math.floor(boosted['affinity'] * self.rules['effects']['ningshen']['stability_multiplier']) == 0, {'profile': boosted, 'note': 'server affinity times configured multiplier; pure tests cover per-batch exactness'})
        self.report['phases'].append({'phase': 'mapped_medicine_checks', 'wall_s': round(time.monotonic()-started, 3)})
        self.save()

    def prepare_restart(self):
        recipe = self.rules['recipes'][0]
        self.fixture(recipe)
        self.open_furnace(); self.menu_click(98, 105); self.close_menu()
        self.poll(self.progress, lambda p: p >= 40, 10, 'restart partial brew')
        self.slot(4, recipe['output'], 16)  # pause through the entire restart, no admin input after load
        self.report['before_restart'] = {'items': self.block_items(), 'progress': self.progress(), 'medicine': self.med()}
        self.rc('save-all flush'); self.save()

    def after_restart(self):
        before = self.report['before_restart']; actual = {'items': self.block_items(), 'progress': self.progress(), 'medicine': self.med()}
        # JSON round-trip makes slot keys strings; normalize the fixture for the comparison.
        expected_items = {int(k): v for k, v in before['items'].items()}
        self.check('restart_preserves_inventory_and_paused_progress', actual['items'] == expected_items and actual['progress'] == before['progress'], actual)
        self.check('restart_preserves_active_medicine', actual['medicine']['effect'] == 'ningshen'
                   and 0 < actual['medicine']['effect_ticks'] <= before['medicine']['effect_ticks'],
                   {'before': before['medicine'], 'after': actual['medicine']})
        self.slot(4, 'minecraft:air')
        self.poll(self.block_items, lambda rows: rows.get(4, {}).get('count') == self.rules['recipes'][0]['output_count'], 30, 'restart completion')
        self.open_furnace(); self.capture('after-restart-menu.png'); self.close_menu()
        # Breaking is an administrator fixture, not a mapped survival pickaxe observation.
        self.rc('kill @e[type=minecraft:item]')
        self.rc(f'setblock {POS} minecraft:air destroy')
        drops = self.rc('execute positioned 0 -60 3 as @e[type=minecraft:item,distance=..8] run data get entity @s Item')
        dropped = {}
        for line in drops.splitlines():
            item = re.search(r'\bid:\s*"([\w:./-]+)"', line)
            count = re.search(r'\bcount:\s*(\d+)', line)
            if item and count:
                dropped[item.group(1)] = dropped.get(item.group(1), 0) + int(count.group(1))
        self.check('break_drops_inventory_exactly_once', dropped == {
            'myvillage:alchemy_furnace': 1, 'myvillage:formula_huiling': 1,
            'myvillage:pill_huiling': self.rules['recipes'][0]['output_count']}, dropped)

    def visuals(self):
        """Bounded extra display checks, separate from medicine/restart acceptance."""
        user = self.s.username
        for cmd in (*scene.world_rules(), *scene.player_setup(user, 'minecraft:air'),
                    'myvillage world pause', 'forceload add -16 -16 32 32',
                    f'setblock {POS} myvillage:alchemy_furnace[facing=north]',
                    f'tp {user} -1.5 -60 1.5 -45 21.6'):
            self.rc(cmd)
        time.sleep(2)
        self.capture('furnace-in-world-unlit.png')
        self.rc(f'myvillage cultivation awaken {user}')
        self.fixture(self.rules['recipes'][0], herbs=64)
        self.slot(3, 'myvillage:low_grade_spirit_stone', 64)
        self.open_furnace(); self.menu_click(98, 105); self.close_menu()
        self.poll(self.progress, lambda n: n > 0, 10, 'visible burning furnace')
        time.sleep(1)
        self.capture('furnace-in-world-lit.png')
        self.open_furnace()
        game = self.s.game
        window = game.xdo('search', '--name', 'Minecraft').split()[0]
        for width, height in ((960, 540), (854, 480), (640, 480)):
            game.xdo('windowsize', window, str(width), str(height))
            game.xdo('windowmove', window, '0', '0')
            game.size = (width, height)
            time.sleep(.8)
            self.capture(f'{self.language}_{width//2}x{height//2}_menu.png')
            self.check(f'mapped_menu_visible_{self.language}_{width//2}x{height//2}',
                       game.pointer_grabbed() is False,
                       {'window': [width, height], 'expected_gui': [width//2, height//2],
                        'note': 'layout/legibility judged from screenshot separately'})
        game.xdo('windowsize', window, '960', '540'); game.size = (960, 540)
        time.sleep(.3); self.close_menu()

    def finish(self):
        self.report['ended_utc'] = dt.datetime.now(dt.timezone.utc).isoformat()
        self.report['status'] = 'pass' if not self.report['failures'] and all(r['pass'] for r in self.report['checks']) else 'fail'
        self.save()
        images = ''.join(f'<figure><img src="{html.escape(p.name)}"><figcaption>{html.escape(p.stem)}</figcaption></figure>'
                         for p in sorted(self.out.glob('*.png')))
        rows = ''.join('<li>'+html.escape(('PASS ' if row['pass'] else 'FAIL ')+row['check'])+'</li>' for row in self.report['checks'])
        (self.out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Alchemy runtime evidence</title>'
            '<style>body{background:#17201d;color:#eee;font:16px system-ui;max-width:1100px;margin:32px auto}img{max-width:100%}figure{margin:24px 0}</style>'
            '<h1>炼丹运行验证</h1><p>开发者证据；材料由管理员准备。玩家自然获取、外观、手感与多人验收未确认。</p>'
            '<a href="evidence.json">完整读数与耗时</a><ul>'+rows+'</ul>'+images)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/alchemy/runtime')
    parser.add_argument('--language', choices=('en_us', 'zh_cn'), default='zh_cn')
    parser.add_argument('--visual-only', action='store_true', help='One fresh capture session for model and three menu sizes only')
    args = parser.parse_args(argv)
    evidence = AlchemyEvidence(args.out, args.language)
    try:
        if args.visual_only:
            evidence.report['kind'] = 'alchemy_visual_developer_checks'
            evidence.start(); evidence.visuals()
        else:
            evidence.start(); evidence.initial(); evidence.medicines(); evidence.prepare_restart(); evidence.stop()
            evidence.start(reuse=True); evidence.after_restart()
    except Exception as exc:
        evidence.report['failures'].append(str(exc)); raise
    finally:
        if evidence.s is not None:
            try: evidence.stop()
            except Exception as exc: evidence.report['failures'].append('cleanup: '+str(exc))
        evidence.finish()
    return 0 if evidence.report['status'] == 'pass' else 1


if __name__ == '__main__':
    raise SystemExit(main())
