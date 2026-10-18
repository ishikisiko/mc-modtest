"""Controlled V2 boss contacts through one actual mapped sword input per target.

RCON builds isolated, full-health stationary fixtures and reads state. It never
hurts a target, edits target Health, starts a combat action, or claims an AI kill.
The caller owns the Modal session, lifecycle, output directory and final report.
"""
from __future__ import annotations

import json
from pathlib import Path
import time

from tools.alchemy_quality_evidence import read_data
from tools.combat_capture import scene, session as cs
from tools.realm_stats_evidence import json_lines

ROOT = Path(__file__).resolve().parents[1]
BIRTH = 'NeoForgeData."myvillage:exploration_boss_birth"'
REALM = 'NeoForgeData."myvillage:stats_realm"'
ORDINARY_BIRTH = 'NeoForgeData."myvillage:vanilla_monster_birth"'
STAGES = {('myvillage:foundation_establishment', 1): 'myvillage:foundation_middle',
          ('myvillage:golden_core', 1): 'myvillage:golden_middle',
          ('myvillage:golden_core', 2): 'myvillage:golden_late'}


def _trace(e, target):
    rows = json_lines(e.rc(f'myvillage stats trace {target}'), 'STATS_TRACE')
    if len(rows) != 1:
        raise ValueError(f'Expected one server damage trace for {target}: {rows}')
    return rows[0]


def _data(e, target, path):
    return read_data(e.rc(f'data get entity {target} {path}'))


def _platform(e, x, z):
    u = e.s.username
    e.rc(f'gamemode creative {u}')
    e.rc(f'execute in minecraft:overworld run tp {u} {x+.5} 79 {z+.5} 0 0')
    e.poll(lambda: e.rc(f'execute in minecraft:overworld if loaded {x} 78 {z}'),
           lambda r: 'passed' in r, 30, 'isolated boss platform chunk loaded')
    # A 15x15 floor well above terrain; each case is 64 blocks apart.
    e.rc(f'fill {x-7} 78 {z-7} {x+7} 78 {z+7} minecraft:smooth_stone')
    e.rc(f'fill {x-7} 79 {z-7} {x+7} 89 {z+7} minecraft:air')
    e.rc(f'tp {u} {x+.5} 79 {z+.5} 0 0')


def _player(e, realm, stage):
    u, g = e.s.username, e.s.game
    e.rc(f'myvillage cultivation setrealm {u} {realm} {STAGES[(realm, stage)]}')
    e.rc(f'effect clear {u}')
    for slot in ('armor.head', 'armor.chest', 'armor.legs', 'armor.feet', 'weapon.offhand'):
        e.rc(f'item replace entity {u} {slot} with minecraft:air')
    e.rc(f'item replace entity {u} hotbar.0 with myvillage:qingfeng_sword')
    g.key('1')
    e.poll(lambda: e.rc(f'data get entity {u} SelectedItem.id'),
           lambda r: 'myvillage:qingfeng_sword' in r, 8, 'mapped hotbar one selects fixed sword')
    e.rc(f'gamemode survival {u}')
    # Healing the actor is fixture setup; targets always retain their actual full profile HP.
    e.rc(f'effect give {u} minecraft:instant_health 1 10 true')
    e.rc(f'effect give {u} minecraft:saturation 1 10 true')
    time.sleep(.4)
    scene.ensure_cultivation(e.s)
    time.sleep(1.0)  # Previous move/combo expires; every case starts from the first sword move.
    g.require_ingame('controlled boss mapped sword contact')


def _hit(e, target, name, before):
    g = e.s.game
    player_before = e.number(f'data get entity {e.s.username} Health')
    g.require_ingame(name + ' actual mapped left mouse')
    # Exactly one press/release, with no command action or health manipulation.
    g.xdo('mousedown', '1')
    try:
        time.sleep(.10)
    finally:
        g.xdo('mouseup', '1')
    after = e.poll(lambda: e.number(f'data get entity {target} Health'),
                   lambda hp: 0 < hp < before, 8, name + ' actual sword health loss')
    trace = _trace(e, target)
    player_after = e.number(f'data get entity {e.s.username} Health')
    loss = before - after
    detail = {'before': before, 'after': after, 'health_loss': loss,
              'trace': trace, 'player_before': player_before, 'player_after': player_after,
              'input': 'mapped hotbar key 1; one left mouse button 1 down/up',
              'equipment': 'unenchanted Qingfeng sword; no armor or offhand; no target Health edits'}
    e.check(name + '_mapped_shared_sword_health_loss',
            trace.get('status') == 'observed' and trace.get('outgoing') is not None
            and trace.get('sourceId') == 'minecraft:player_attack'
            and 0 < loss < before and abs(trace.get('actualHealthLoss', -999) - loss) <= 1.01,
            detail)
    # A wither can retain its native passive healing; trace measures actual hurt independently.
    detail['native_healing_difference'] = trace.get('actualHealthLoss', 0) - loss
    e.gui_shot(g, name + '-mapped-sword.png')
    return detail


def run_bosses(e):
    """Return restart-readable stationary fixtures; never starts/stops a client or server."""
    result = {'kind': 'controlled_stationary_mapped_targets', 'targets': [],
              'method': 'RCON fixture/readback; one actual mapped sword contact per full-health target',
              'ai_victory': 'not_verified', 'player_balance': 'not_verified',
              'natural_encounter': 'not_verified', 'owner_feel': 'not_verified'}
    e.report.setdefault('cases', {})['controlled_bosses'] = result
    profiles = {row['entity']: row for row in json.loads(
        (ROOT/'src/main/resources/data/myvillage/exploration/bosses.json').read_text())['profiles']}
    options = dict(line.split(':', 1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines()
                   if ':' in line)
    e.check('boss_mapped_attack_binding', options.get('key_key.attack') == 'key.mouse.left',
            {'key_key.attack': options.get('key_key.attack'), 'hotbar': options.get('key_key.hotbar.1')})
    regen_reply = e.rc('gamerule naturalRegeneration')
    old_regen = 'true' if 'true' in regen_reply.lower() else 'false'
    e.rc('gamerule naturalRegeneration false')
    try:
        for index, entity in enumerate(('minecraft:elder_guardian', 'minecraft:wither', 'minecraft:warden')):
            profile = profiles[entity]; short = entity.split(':')[1]
            tag = 'exploration_v2_mapped_' + short
            target = f'@e[tag={tag},limit=1]'
            x, z = 320 + index * 64, 320
            _platform(e, x, z)
            e.rc(f'kill @e[tag={tag}]')
            extra = ',Invul:0' if short == 'wither' else ''
            e.rc(f'summon {entity} {x+.5} 79 {z+3.5} '
                 '{NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b,Tags:["'+tag+'"]'+extra+'}')
            e.poll(lambda: e.rc(f'data get entity {target} {BIRTH}'),
                   lambda r: 'data: ' in r, 15, short + ' boss assignment')
            birth_before, realm_before = _data(e, target, BIRTH), _data(e, target, REALM)
            before = e.number(f'data get entity {target} Health')
            maximum = e.number(f'attribute {target} minecraft:generic.max_health get')
            base = e.number(f'attribute {target} minecraft:generic.max_health base get')
            e.check(short + '_actual_full_profile_health', abs(before - profile['health']) < .01
                    and abs(maximum - profile['health']) < .01
                    and birth_before.get('profile') == profile['id']
                    and realm_before == {'realm': profile['realm'], 'stage': profile['stage']},
                    {'health': before, 'maximum': maximum, 'native_base': base,
                     'expected_profile': profile, 'birth': birth_before, 'realm': realm_before})
            e.check(short + '_controlled_no_ai', _data(e, target, 'NoAI') == 1,
                    {'fixture': 'fresh COMMAND summon, stationary NoAI/NoGravity, full HP'})
            if short == 'wither':
                e.check('wither_not_charging', _data(e, target, 'Invul') == 0,
                        {'Invul': _data(e, target, 'Invul'), 'health': before})
            if short == 'warden':
                brain = _data(e, target, 'Brain')
                memories = brain.get('memories', {})
                e.check('warden_no_emerge_or_dig_memory',
                        not any('dig' in key or 'emerg' in key for key in memories), brain)
            _player(e, profile['realm'], profile['stage'])
            row = _hit(e, target, short, before)
            row.update(entity=entity, selector=target, position=[x+.5, 79, z+3.5],
                       birth=birth_before, realm=realm_before, expected_profile=profile['id'])
            e.check(short + '_birth_profile_kept_after_mapped_hit',
                    _data(e, target, BIRTH) == birth_before and _data(e, target, REALM) == realm_before,
                    {'birth_before': birth_before, 'birth_after': _data(e, target, BIRTH)})
            if short == 'elder_guardian':
                player_trace = _trace(e, e.s.username)
                row['native_thorns_trace'] = player_trace
                e.check('elder_guardian_native_thorns_reaches_player',
                        row['player_after'] < row['player_before']
                        and player_trace.get('sourceId') == 'minecraft:thorns'
                        and player_trace.get('actualHealthLoss', 0) > 0, player_trace)
            if short == 'warden':
                row['standing_evidence'] = 'fresh NoAI COMMAND summon, no emergence/dig memories, actual positive player sword damage'
            result['targets'].append(row)

        # A real spawner produces the ordinary-monster assignment. COMMAND summons stay baseline.
        x, z = 512, 320
        _platform(e, x, z)
        tag = 'exploration_v2_mapped_zombie'; target = f'@e[tag={tag},limit=1]'
        e.rc(f'kill @e[tag={tag}]')
        spawner = f'{x} 78 {z+3}'
        e.rc('setblock '+spawner+' minecraft:spawner{Delay:1s,MinSpawnDelay:200s,MaxSpawnDelay:200s,'
             'SpawnCount:1s,SpawnRange:0s,RequiredPlayerRange:16s,MaxNearbyEntities:1s,'
             'SpawnData:{entity:{id:"minecraft:zombie",NoAI:1b,NoGravity:1b,PersistenceRequired:1b,'
             'Tags:["'+tag+'"]},custom_spawn_rules:{block_light_limit:{min_inclusive:0,max_inclusive:15},'
             'sky_light_limit:{min_inclusive:0,max_inclusive:15}}}}')
        try:
            e.poll(lambda: e.rc(f'data get entity {target} {ORDINARY_BIRTH}'),
                   lambda r: 'data: ' in r, 35, 'ordinary native spawner assignment')
        finally:
            e.rc(f'setblock {spawner} minecraft:smooth_stone')
        e.rc(f'tp {target} {x+.5} 79 {z+3.5}')
        e.rc(f'execute as {target} run data merge entity @s {{Fire:0s}}')
        e.rc(f'effect give {target} minecraft:fire_resistance 90 0 true')
        birth_before = _data(e, target, ORDINARY_BIRTH)
        realm_before = _data(e, target, REALM)
        e.check('ordinary_regression_has_spawner_birth', birth_before.get('reason') == 'SPAWNER', birth_before)
        # Foundation middle actor uses the same fixed equipment and shared move. The regional
        # zombie has its genuine rolled HP; no test lowers it to manufacture a player kill.
        _player(e, 'myvillage:foundation_establishment', 1)
        before = e.number(f'data get entity {target} Health')
        row = _hit(e, target, 'ordinary_zombie', before)
        e.check('ordinary_birth_kept_after_mapped_shared_hit',
                _data(e, target, ORDINARY_BIRTH) == birth_before and _data(e, target, REALM) == realm_before,
                birth_before)
        row.update(entity='minecraft:zombie', selector=target, position=[x+.5, 79, z+3.5],
                   birth=birth_before, realm=realm_before, birth_path=ORDINARY_BIRTH)
        result['targets'].append(row)
        result['completed'] = ['elder_guardian', 'wither', 'warden', 'ordinary_spawner_zombie']
        return result
    finally:
        e.s.game.xdo('mouseup', '1')
        e.rc(f'gamemode creative {e.s.username}')
        e.rc(f'gamerule naturalRegeneration {old_regen}')
