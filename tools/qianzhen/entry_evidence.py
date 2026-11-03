"""Colab 专用：真实新玩家留在主世界，经 H 菜单入境和返回，并拍摄 18 款亡灵。

菜单只用映射键鼠；RCON 仅准备隔离画面、读取维度和保存状态。
画面属于 captured_for_review，不代表玩家审美、战斗手感或 FPS 验收。
"""
from pathlib import Path
import argparse
import json
import math
import os
import re
import secrets
import time
import traceback

from .realm_evidence import Client, Evidence, ROOT, cs, support, wait, read_data
from tools.gen_undead_roster import catalogue
from tools.regiongen.bay_native import archive_world

CASES = ['fresh_world_overworld', 'h_menu_entry', 'h_menu_return',
         'new_player_overworld', 'restart_overworld', 'saved_menu_resume', 'undead_client_render']


def record_failure(report, error):
    report.update(status='failed', error=repr(error))
    # A failed selected case is no longer unrun. Completed evidence remains unchanged.
    if report['unrun']:
        report['failed'].append(report['unrun'].pop(0))


class EntryClient(Client):
    def spawn(self, args, path, **kwargs):
        if 'runClient' in args:
            kwargs['env'] = {**kwargs.get('env', os.environ), 'JAVA_TOOL_OPTIONS': '-Xmx6g'}
            cs.merge_options(self.directory / 'options.txt', {'simulationDistance': '5'})
            self.e.action('client_environment', player=self.name, java_heap='6g', simulation_distance=5)
        return super().spawn(args, path, **kwargs)


class EntryEvidence(Evidence):
    def __init__(self, out, request):
        super().__init__(out, request)
        self.report.update(kind='overworld_default_and_undead_render', selected=CASES.copy(),
                           completed=[], failed=[], unrun=CASES.copy(), method=__doc__,
                           owner_appearance='human_review_pending', full_quest='not_selected',
                           multiplayer_combat='not_selected', rendered_variants=[])
        self.save()

    def complete(self, name):
        self.report['completed'].append(name)
        self.report['unrun'].remove(name)
        self.save()

    def fresh(self, client, case):
        start = int(self.rc('time query gametime').rsplit(' ', 1)[-1])
        wait(lambda: int(self.rc('time query gametime').rsplit(' ', 1)[-1]),
             lambda tick: tick >= start + 80, 30, 'four seconds of actual server ticks')
        state = self.realm(client.name)
        self.check(case, state['current_dimension'] == 'minecraft:overworld'
                   and not state['exists'] and state['phase'] == 'ABSENT'
                   and not state['building'] and not state['waiting'], state)
        self.complete(case)
        client.capture(case)
        return state

    def menu(self, client, action):
        client.close_screen(); client.guard()
        offset = client.game.client_log.stat().st_size
        client.game.xdo('key', 'h'); self.action('key', player=client.name, key='h')
        wait(client.game.pointer_grabbed, lambda grabbed: grabbed is False, 10, 'H panel opens')
        # The collector pins GUI scale 2 and the owned window at 960x540. Click the actual 行迹 tab.
        scale = 2; width, height = client.game.size
        gw, gh = math.ceil(width / scale), math.ceil(height / scale)
        left = (gw - min(480, gw - 8)) // 2
        top = (gh - min(282, gh - 8)) // 2
        x, y = (left + 16) * scale, (top + 36 + 7 * 21 + 9) * scale
        client.guard(menu=True); client.game.xdo('mousemove', '--sync', str(x), str(y))
        client.game.xdo('click', '1')
        self.action('h_journey_tab_click', player=client.name, point=[x, y])
        pattern = re.compile(r'QIANZHEN_JOURNAL action=' + action + r' x=(\d+) y=(\d+) w=(\d+) h=(\d+)')
        rows = wait(lambda: pattern.findall(client.tail(offset)), bool, 15, 'H journey action ' + action)
        x, y, width, height = map(int, rows[-1])
        client.capture('h-menu-' + action + '-' + str(len(self.report['screenshots'])))
        client.guard(menu=True)
        client.game.xdo('mousemove', '--sync', str(x + width // 2), str(y + height // 2))
        client.game.xdo('click', '1')
        self.action('h_travel_click', player=client.name, action=action, rectangle=[x, y, width, height])
        time.sleep(.7)

    def gallery(self, client):
        client.close_screen()
        self.rc('difficulty normal')
        # Four bounded chunks must actually be loaded before placing the review stage or actors.
        with support.test_tickets(self.server, -7, -7, 7, 7):
            self.gallery_loaded(client)

    def gallery_loaded(self, client):
        self.rc('gamemode creative ' + client.name)
        self.rc('execute in minecraft:overworld run fill -7 219 -7 7 219 7 minecraft:smooth_stone')
        self.rc('execute in minecraft:overworld run fill -7 220 -7 7 225 7 minecraft:air')
        floor = self.rc('execute in minecraft:overworld if block 0 219 0 minecraft:smooth_stone')
        self.check('review_stage_has_a_real_floor', 'Test passed' in floor, floor)
        self.rc('execute in minecraft:overworld run tp ' + client.name + ' 0.0 220.0 -4.0 0.0 6.0')
        wait(lambda: read_data(self.rc('data get entity ' + client.name + ' Pos')),
             lambda position: math.dist(position, [0, 220, -4]) < .25, 20, 'review camera fixture position')
        data = catalogue()['variants']
        for region in dict.fromkeys(v['region'] for v in data):
            variants = [v for v in data if v['region'] == region]
            self.rc('kill @e[tag=undead_review_fixture]')
            # Death animation and poof particles outlive the server-side entity. Wait actual ticks.
            start = int(self.rc('time query gametime').rsplit(' ', 1)[-1])
            wait(lambda: int(self.rc('time query gametime').rsplit(' ', 1)[-1]),
                 lambda tick: tick >= start + 60, 20, 'previous fixture death effects finish')
            for kind in ('item', 'experience_orb'):
                self.rc('kill @e[type=minecraft:' + kind + ',x=-7,y=219,z=-7,dx=14,dy=8,dz=14]')
            for i, variant in enumerate(variants):
                self.rc('execute in minecraft:overworld run summon myvillage:' + variant['id']
                        + f' {-1.25 if i == 0 else 1.25} 220.0 0.0 '
                        + '{NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,'
                        + 'Tags:["undead_review_fixture"],Rotation:[180.0f,0.0f]}')
            for variant in variants:
                wait(lambda v=variant: self.rc('execute if entity @e[type=myvillage:' + v['id'] + ',tag=undead_review_fixture]'),
                     lambda reply: 'Test passed' in reply, 20, 'review entity is active: ' + variant['id'])
            for label, angle in [('front', 180), ('side', 90), ('other_side', 270), ('back', 0)]:
                self.rc('execute as @e[tag=undead_review_fixture] at @s run tp @s ~ ~ ~ ' + str(angle) + ' 0')
                time.sleep(1.2)
                for variant in variants:
                    reply = self.rc('execute if entity @e[type=myvillage:' + variant['id'] + ',tag=undead_review_fixture]')
                    self.check('fixture_visible_' + variant['id'] + '_' + label, 'Test passed' in reply, reply)
                client.capture('undead-' + region + '-' + label)
            self.report['rendered_variants'].extend(v['id'] for v in variants); self.save()
        self.rc('kill @e[tag=undead_review_fixture]')
        self.rc('difficulty peaceful')
        text = client.game.client_log.read_text(errors='replace')
        errors = [line for line in text.splitlines() if any(s in line for s in
                  ['Exception rendering entity', 'Missing texture', 'Failed to load texture', 'Using missing texture'])
                  and ('myvillage' in line or 'Exception rendering entity' in line)]
        self.check('all_18_rendered_without_missing_resources', len(set(self.report['rendered_variants'])) == 18
                   and not errors, {'variants': self.report['rendered_variants'], 'render_errors': errors})
        self.complete('undead_client_render')


def run(out, request):
    if os.environ.get('MYVILLAGE_REMOTE_PROVIDER') != 'colab' or not str(ROOT).startswith('/content/'):
        raise RuntimeError('Colab only; no local Minecraft fallback')
    e = EntryEvidence(out, request); support.OUT = out
    name = 'overworld_entry_' + secrets.token_hex(5); world = support.GAME / name
    cs.CLIENT_OPTIONS['guiScale'] = '2'
    try:
        with support.Server(name, 'first', planned=False) as server:
            e.server = server
            with EntryClient(e, server, 'EntryAlice', 'first') as client:
                e.fresh(client, 'fresh_world_overworld')
                e.menu(client, 'enter')
                inside = wait(lambda: e.realm(client.name),
                              lambda s: s['phase'] == 'COMPLETE' and s['current_dimension'] == s['dimension'],
                              150, 'explicit first H entry')
                story = e.story(client.name)
                e.check('menu_entry_does_not_accept_chapter', inside['authorized'] and not story['accepted'], story)
                e.complete('h_menu_entry'); client.capture('explicit-entry')
                e.menu(client, 'leave')
                left = wait(lambda: e.realm(client.name), lambda s: s['current_dimension'] == 'minecraft:overworld',
                            30, 'explicit H return')
                e.check('return_matches_saved_outside', left['exists'] and left['outside'] == inside['outside'], left)
                e.complete('h_menu_return')
            with EntryClient(e, server, 'EntryBob', 'fresh-player') as client:
                e.fresh(client, 'new_player_overworld')
        with support.Server(name, 'restart', planned=False) as server:
            e.server = server
            with EntryClient(e, server, 'EntryAlice', 'restart') as client:
                state = e.realm(client.name)
                e.check('existing_realm_does_not_force_login_travel', state['exists']
                        and state['phase'] == 'COMPLETE' and state['current_dimension'] == 'minecraft:overworld', state)
                e.complete('restart_overworld')
                e.menu(client, 'enter')
                resumed = wait(lambda: e.realm(client.name), lambda s: s['current_dimension'] == s['dimension'],
                               30, 'saved H resume')
                after = e.story(client.name)
                e.check('same_realm_people_and_unaccepted_progress', resumed['dimension'] == inside['dimension']
                        and not after['accepted'] and after['revision'] == story['revision']
                        and {k: v['personId'] for k, v in after['npc'].items()}
                        == {k: v['personId'] for k, v in story['npc'].items()}, after)
                e.complete('saved_menu_resume')
                e.menu(client, 'leave')
                wait(lambda: e.realm(client.name), lambda s: s['current_dimension'] == 'minecraft:overworld', 30, 'leave before gallery')
                e.gallery(client)
        for label in ('first', 'restart'):
            cleanup = json.loads((out / (label + '-cleanup.json')).read_text())
            e.check(label + '_normal_shutdown', cleanup['terminated'] and cleanup['returncode'] == 0
                    and not cleanup['forced_stop'], cleanup)
        e.report['status'] = 'passed'
    except BaseException as error:
        record_failure(e.report,error); traceback.print_exc()
    finally:
        if world.exists():
            archive_world(world, out / 'saved-world.zip')
        e.save()
    if e.report['status'] != 'passed':
        raise RuntimeError(e.report.get('error'))


def run_gallery(out, request):
    """模型修整后只重跑新包首次登录与全部模型画面；不重复未改变的生命周期场景。"""
    if os.environ.get('MYVILLAGE_REMOTE_PROVIDER') != 'colab' or not str(ROOT).startswith('/content/'):
        raise RuntimeError('Colab only; no local Minecraft fallback')
    e = EntryEvidence(out, request)
    selected = ['fresh_world_overworld', 'undead_client_render']
    e.report.update(selected=selected, completed=[], failed=[], unrun=selected.copy(),
                    kind='final_undead_render', other_lifecycle_cases='not_selected')
    e.save(); support.OUT = out; cs.CLIENT_OPTIONS['guiScale'] = '2'
    name = 'undead_render_' + secrets.token_hex(5); world = support.GAME / name
    try:
        with support.Server(name, 'gallery', planned=False) as server:
            e.server = server
            with EntryClient(e, server, 'ReviewPlayer', 'gallery') as client:
                e.fresh(client, 'fresh_world_overworld')
                e.gallery(client)
        cleanup = json.loads((out / 'gallery-cleanup.json').read_text())
        e.check('gallery_normal_shutdown', cleanup['terminated'] and cleanup['returncode'] == 0
                and not cleanup['forced_stop'], cleanup)
        e.report['status'] = 'passed'
    except BaseException as error:
        record_failure(e.report,error); traceback.print_exc()
    finally:
        if world.exists():
            archive_world(world, out / 'saved-world.zip')
        e.save()
    if e.report['status'] != 'passed':
        raise RuntimeError(e.report.get('error'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--request', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--gallery-only', action='store_true')
    args = parser.parse_args()
    (run_gallery if args.gallery_only else run)(args.out, json.loads(args.request.read_text()))
