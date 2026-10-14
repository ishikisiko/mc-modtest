#!/usr/bin/env python3
"""Bounded realm-stat evidence using a real client, mapped input and server readback.

Run the clean stage-1 acceptance first. This script owns two isolated capture
sessions, reusing its world only for the second (save/restart) pass. It never
uses DevBridge and never treats administrator setup as natural acquisition or
an owner verdict. Failures and command replies are retained in the output.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import math
from pathlib import Path
import re
import secrets
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.combat_capture import procs, scene, session as cs  # noqa: E402

TARGET = '@e[tag=realm_stat_target,limit=1]'
WOLF = '@e[tag=realm_stat_persist,limit=1]'
NATURAL_WOLF = '@e[tag=realm_stat_natural_persist,limit=1]'


def json_lines(reply: str, prefix: str) -> list[dict]:
    """Decode only the requested diagnostic prefix; fail on malformed evidence."""
    result = []
    for line in reply.splitlines():
        if line.startswith(prefix + ' '):
            value = json.loads(line[len(prefix) + 1:])
            if not isinstance(value, dict):
                raise ValueError(f'{prefix} did not return an object')
            result.append(value)
    return result


def close(actual, expected, tolerance=0.002) -> bool:
    return (isinstance(actual, (int, float)) and not isinstance(actual, bool)
            and math.isfinite(actual) and abs(actual - expected) <= tolerance)


def realm_in_range(key: dict, bounds: list[dict]) -> bool:
    """Independent readback check against the authored ordinal ladder."""
    rules = json.loads((ROOT / 'src/main/resources/data/myvillage/stats/rules.json').read_text())
    ladder = [(realm['id'], stage) for realm in rules['realms'] for stage in range(realm['stages'])]
    try:
        rank = lambda value: ladder.index((value.get('realmId', value.get('realm')), value['stage']))
        return rank(bounds[0]) <= rank(key) <= rank(bounds[1])
    except (ValueError, KeyError, TypeError):
        return False


class Evidence:
    def __init__(self, out: Path):
        self.out = out
        out.mkdir(parents=True, exist_ok=True)
        self.report = {
            'kind': 'realm_stats_wilderness',
            'started_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
            'method': 'physical client; mapped left/right mouse; RCON fixture setup and readback; no DevBridge',
            'owner_verdict': 'not_verified', 'multiplayer': 'not_verified',
            'natural_encounter_density': 'not_verified',
            'commands': [], 'checks': [], 'phases': [], 'failures': [],
        }
        self.s = None
        self.sessions = 0

    def save(self):
        (self.out / 'evidence.json').write_text(json.dumps(self.report, indent=2) + '\n')

    def rc(self, command):
        reply = self.s.run(command, warn=False)[0]
        self.report['commands'].append({'command': command, 'reply': reply})
        self.save()
        return reply

    def check(self, name, passed, detail):
        self.report['checks'].append({'check': name, 'pass': bool(passed), 'detail': detail})
        print(f'{"PASS" if passed else "FAIL"} {name}: {detail}', flush=True)
        self.save()
        return passed

    def one(self, command, prefix):
        values = json_lines(self.rc(command), prefix)
        if len(values) != 1:
            raise RuntimeError(f'{command} returned {len(values)} {prefix} objects')
        return values[0]

    def number(self, command):
        reply = self.rc(command)
        number = scene.parse_float_data(reply)
        if number is None:
            # Vanilla attribute commands end with an untyped number.
            match = re.search(r'(-?\d+(?:\.\d+)?)\s*$', reply)
            number = float(match.group(1)) if match else None
        if number is None or not math.isfinite(number):
            raise RuntimeError(f'No finite number from {command}: {reply}')
        return number

    def start(self, reuse=False):
        started = time.monotonic()
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse,
                                         lock=str(procs.default_lock_path(ROOT)), natural_monsters=True))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        self.report['phases'].append({'phase': 'restart_setup' if reuse else 'initial_setup',
                                     'wall_s': round(time.monotonic() - started, 3),
                                     'capture_timings': state.get('timings', {})})
        self.save()

    def stop(self):
        started = time.monotonic()
        cs.stop()
        self.s = None
        for kind, path in [('server', ROOT / 'run-acceptance/logs/latest.log'),
                           ('client', ROOT / 'run-combat-capture/client/logs/latest.log')]:
            if path.exists():
                shutil.copy2(path, self.out / f'session-{self.sessions}-{kind}.log')
        self.report['phases'].append({'phase': 'session_stop',
                                     'wall_s': round(time.monotonic() - started, 3)})
        self.save()

    def target(self, armor=0, resistance=0, absorption=False):
        self.rc('kill @e[tag=realm_stat_target]')
        self.rc('summon minecraft:iron_golem 0.5 -60 3 '
                '{NoAI:1b,PersistenceRequired:1b,Tags:["realm_stat_target"]}')
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            reply = self.rc(f'data get entity {TARGET} Health')
            if scene.parse_float_data(reply) is not None:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError('Summoned fixture did not become entity-ticking within 15 seconds')
        self.rc(f'attribute {TARGET} minecraft:generic.armor base set {armor}')
        self.rc(f'attribute {TARGET} myvillage:physical_resistance base set {resistance}')
        if absorption:
            self.rc(f'effect give {TARGET} minecraft:absorption 60 0 true')
        return self.number(f'data get entity {TARGET} Health')

    def damage_probe(self, name, amount, expected_post, expected_final, *, armor=0, resistance=0,
                     absorption=False, source='minecraft:mob_attack'):
        before = self.target(armor, resistance, absorption)
        user = self.s.username
        self.rc(f'damage {TARGET} {amount} {source} by {user}')
        after = self.number(f'data get entity {TARGET} Health')
        trace = self.one(f'myvillage stats trace {TARGET}', 'STATS_TRACE')
        expected_loss = max(0, expected_final - (4 if absorption else 0))
        self.check(name, close(trace.get('postVanilla'), expected_post)
                   and close(trace.get('afterResistance'), expected_final)
                   and close(before - after, expected_loss),
                   {'before': before, 'after': after, 'trace': trace, 'expected_loss': expected_loss})

    def wilderness_probe(self, user):
        """Observe existing rogue projection and actual natural births in an isolated flat world."""
        origin = self.one('myvillage wilderness info 0 -60 0', 'WILDERNESS_INFO')
        chosen = None
        for center in origin['centers']:
            row = self.one(f'myvillage wilderness info {center["x"]} -60 {center["z"]}', 'WILDERNESS_INFO')
            if row.get('eligibleRogues', 0) > 0:
                chosen = (center, row)
                break
        self.check('ledger_has_regional_rogue_candidate', chosen is not None, origin['centers'])
        if chosen is None:
            return
        center, before = chosen
        x, z = center['x'], center['z']
        self.rc(f'tp {user} {x + .5} -60 {z + .5}')
        deadline = time.monotonic() + 40
        current = before
        while time.monotonic() < deadline:
            time.sleep(2)
            current = self.one(f'myvillage wilderness info {x} -60 {z}', 'WILDERNESS_INFO')
            if current.get('projectedRogues', 0) > 0:
                break
        count = current.get('projectedRogues', 0)
        self.check('rogue_projection_within_caps', 0 < count <= current['settings']['maxPerRegion']
                   and current['wildAvatarsTotal'] <= current['settings']['maxTotal']
                   and current['avatarsTotal'] <= current['avatarsLimit'], current)
        people = self.rc('execute as @e[type=myvillage:cultivator,nbt={Tags:["myvillage:wild_avatar"]}] run data get entity @s WorldSimPerson')
        ids = [int(value) for value in re.findall(r': (\d+)\s*$', people, flags=re.MULTILINE)]
        self.check('rogue_identity_deduplicated', len(ids) >= count and len(ids) == len(set(ids)), ids)
        self.report['rogue_stats'] = json_lines(
            self.rc('myvillage stats info @e[type=myvillage:cultivator,nbt={Tags:["myvillage:wild_avatar"]}]'), 'STATS_INFO')
        self.check('projected_rogues_inside_region_range',
                   len(self.report['rogue_stats']) >= count and all(
                       realm_in_range({'realmId': row['realm'], 'stage': row['stage']}, current['rogueRange'])
                       for row in self.report['rogue_stats']),
                   [{'realm': row['realm'], 'stage': row['stage']} for row in self.report['rogue_stats']])

        self.rc('difficulty normal')
        self.rc('time set midnight')
        self.rc('gamerule doMobSpawning true')
        time.sleep(0.5)
        self.report['spawn_conditions'] = self.one(
            f'myvillage wilderness info {x} -60 {z}', 'WILDERNESS_INFO')['spawnDiagnostics']
        deadline = time.monotonic() + 60
        births = []
        try:
            while time.monotonic() < deadline:
                time.sleep(5)
                births = json_lines(self.rc('myvillage wilderness birth @e[type=myvillage:demon_wolf]'),
                                    'WILDERNESS_BIRTH')
                natural = [b for b in births if (b.get('birth') or {}).get('natural')]
                if natural:
                    break
                # Clear other natural monsters from this isolated fixture to permit another
                # bounded spawn round. Do not change biome weights or synthesize a natural flag.
                self.rc('kill @e[type=!minecraft:player,type=!myvillage:cultivator,type=!myvillage:demon_wolf]')
        finally:
            self.rc('gamerule doMobSpawning false')
            self.rc('time set 6000')
        natural = [b for b in births if (b.get('birth') or {}).get('natural')]
        self.report['natural_birth_observation'] = natural
        self.check('natural_birth_observed', bool(natural), natural)
        if natural:
            regions = json.loads((ROOT / 'src/main/resources/data/myvillage/wilderness/rules.json').read_text())['regions']
            valid = all(realm_in_range(row['birth']['realm'],
                                      [regions[row['birth']['region']]['beast']['min'],
                                       regions[row['birth']['region']]['beast']['max']]) for row in natural)
            self.check('natural_births_inside_region_range', valid, natural)
        if natural:
            born = natural[0]
            uuid = born['uuid']
            # Keep one genuinely natural birth for the cross-region/restart check.
            # PersistenceRequired is an explicit fixture, not a despawn-policy claim.
            self.rc(f'tag {uuid} add realm_stat_natural_persist')
            self.rc(f'data merge entity {uuid} {{NoAI:1b,PersistenceRequired:1b}}')
            self.rc(f'damage {uuid} 5 minecraft:generic')
            self.report['natural_before'] = self.one(f'myvillage wilderness birth {uuid}', 'WILDERNESS_BIRTH')
            self.rc(f'tp {uuid} 12.5 -60 12.5')

        self.rc(f'tp {user} 8000.5 -60 8000.5')
        time.sleep(7)
        withdrawn = self.one(f'myvillage wilderness info {x} -60 {z}', 'WILDERNESS_INFO')
        self.check('rogues_withdraw_out_of_range', withdrawn.get('projectedRogues') == 0, withdrawn)
        outside = self.one('myvillage wilderness info 8000 -60 8000', 'WILDERNESS_INFO')
        self.check('outside_region_has_no_fallback', outside.get('region') == '', outside)
        other = self.one('execute in minecraft:the_nether run myvillage wilderness info 0 80 0', 'WILDERNESS_INFO')
        self.check('other_dimension_has_no_region', other.get('region') == '', other)
        self.rc(f'tp {user} 0.5 -60 0.5 0 0')
        time.sleep(3)
        if natural:
            crossed = self.one(f'myvillage wilderness birth {NATURAL_WOLF}', 'WILDERNESS_BIRTH')
            self.check('natural_birth_not_rerolled_after_move',
                       crossed.get('birth') == self.report['natural_before'].get('birth')
                       and close(crossed.get('maxHealth'), self.report['natural_before']['maxHealth']), crossed)

    def initial(self, sword_qi=True):
        user = self.s.username
        started = time.monotonic()
        for command in (*scene.world_rules(), *scene.player_setup(user, 'myvillage:qingfeng_sword'),
                        'gamerule naturalRegeneration false', 'myvillage world pause'):
            self.rc(command)
        # Fixture chunks are explicit. Production wilderness placement never force-loads chunks.
        self.rc('forceload add -16 -16 32 32')
        self.rc(f'myvillage cultivation setrealm {user} myvillage:qi_refining myvillage:qi_refining_1')
        self.rc(f'attribute {user} myvillage:resistance_penetration base set 0')
        growth = json_lines(self.rc('myvillage stats growth'), 'STATS_GROWTH')
        self.check('growth_table_exposed', bool(growth), growth)
        first = self.one('myvillage stats growth myvillage:qi_refining 0', 'STATS_GROWTH')
        expected_first = next(row for row in growth if row['realm'] == 'myvillage:qi_refining' and row['stage'] == 0)
        self.check('namespaced_growth_query', first == expected_first, first)
        preview = self.one('myvillage stats damage preview 10 0 1 0 1 10 physical 0.5 0.1', 'STATS_PREVIEW')
        self.check('damage_preview_readout', close(preview['mitigation']['damage'], 6), preview)
        self.damage_probe('physical_resistance', 10, 10, 5, resistance=0.5)
        self.damage_probe('armor_then_resistance_once', 10, 8, 4, armor=10, resistance=0.5)
        self.damage_probe('absorption_after_resistance', 10, 10, 5, resistance=0.5, absorption=True)
        self.damage_probe('unknown_damage_untouched', 10, 10, 10, resistance=0.5, source='minecraft:generic')
        self.rc(f'attribute {user} myvillage:resistance_penetration base set 0.1')
        self.damage_probe('resistance_penetration', 10, 10, 6, resistance=0.5)
        self.rc(f'attribute {user} myvillage:resistance_penetration base set 0')

        # One mapped attack proves the common outgoing path on a real client.
        self.rc(f'attribute {user} myvillage:damage_flat base set 2')
        self.rc(f'attribute {user} myvillage:damage_bonus base set 0.5')
        self.rc(f'attribute {user} myvillage:damage_multiplier base set 2')
        self.rc(f'tp {user} 0.5 -60 0.5 0 0')
        self.s.game.key('1')
        scene.ensure_cultivation(self.s)
        before = self.target(resistance=0.25)
        base = self.number(f'attribute {user} minecraft:generic.attack_damage get')
        style = json.loads((ROOT / 'src/main/resources/data/myvillage/combat/style/basic_sword.json').read_text())
        multiplier = style['moves'][0]['damage_multiplier']
        expected = (base + 2) * multiplier * 1.5 * 2 * 0.75
        self.s.game.require_ingame('realm stats mapped melee')
        self.s.game.click(0.06)
        time.sleep(2.0)
        after = self.number(f'data get entity {TARGET} Health')
        trace = self.one(f'myvillage stats trace {TARGET}', 'STATS_TRACE')
        self.check('mapped_melee_damage_buckets', close(before - after, expected),
                   {'base': base, 'move': multiplier, 'expected': expected,
                    'before': before, 'after': after, 'trace': trace})
        for attr, value in [('damage_flat', 0), ('damage_bonus', 0), ('damage_multiplier', 1)]:
            self.rc(f'attribute {user} myvillage:{attr} base set {value}')

        if sword_qi:
            from tools.combat_capture.sword_qi import run_sword_qi
            evidence = run_sword_qi(self.s, self.out / 'sword_qi', trials=['tap_hit', 'charged_hit'])
            self.report['sword_qi'] = evidence
            trials = evidence.get('trials', [])
            self.check('mapped_sword_qi_smoke', len(trials) == 2 and all(t.get('passed') for t in trials),
                       [{'id': t.get('id'), 'pass': t.get('passed')} for t in trials])

        self.rc('tick rate 20')
        self.rc('kill @e[tag=realm_stat_target]')
        self.rc('kill @e[tag=sword_qi_target]')
        self.rc(f'gamemode creative {user}')
        self.wilderness_probe(user)
        # Birth fixture uses the same validated assignment as natural wolves;
        # this is explicitly not evidence of natural spawn frequency.
        self.report['wilderness_info'] = self.rc('myvillage wilderness info 0 -60 0')
        self.report['sample_a'] = self.rc('myvillage wilderness sample zhongzhou 12345 64')
        self.report['sample_b'] = self.rc('myvillage wilderness sample zhongzhou 12345 64')
        self.check('region_sampling_replay', self.report['sample_a'] == self.report['sample_b']
                   and 'incorrect' not in self.report['sample_a'].lower(), self.report['sample_a'])
        diagnostic = self.one('myvillage wilderness spawn_test 8 -60 8 12345', 'WILDERNESS_BIRTH')
        self.rc(f'tag {diagnostic["uuid"]} add realm_stat_persist')
        self.rc(f'data merge entity {WOLF} {{NoAI:1b,PersistenceRequired:1b}}')
        self.report['wolf_birth_before'] = self.one(f'myvillage wilderness birth {WOLF}', 'WILDERNESS_BIRTH')
        self.report['wolf_max_before'] = self.number(f'attribute {WOLF} minecraft:generic.max_health get')
        self.rc(f'damage {WOLF} 5 minecraft:generic')
        self.report['wolf_health_before'] = self.number(f'data get entity {WOLF} Health')

        # Save health above vanilla player base max, then restart: this catches
        # transient-modifier loading that would silently clamp Health to 20.
        self.rc(f'myvillage cultivation setrealm {user} myvillage:foundation_establishment myvillage:foundation_early')
        self.rc(f'gamemode survival {user}')
        self.rc(f'effect give {user} minecraft:instant_health 1 10 true')
        time.sleep(1.2)
        self.rc(f'damage {user} 7 minecraft:generic')
        self.report['player_max_before'] = self.number(f'attribute {user} minecraft:generic.max_health get')
        self.report['player_health_before'] = self.number(f'data get entity {user} Health')
        self.rc(f'myvillage cultivation setrealm {user} myvillage:foundation_establishment myvillage:foundation_early')
        refreshed = self.number(f'data get entity {user} Health')
        self.check('refresh_does_not_heal', close(refreshed, self.report['player_health_before']), refreshed)
        self.rc(f'tp {user} 0.5 -60 0.5 0 0')
        self.rc('save-all flush')
        self.report['phases'].append({'phase': 'initial_checks', 'wall_s': round(time.monotonic() - started, 3)})
        self.save()

    def restored(self):
        started = time.monotonic()
        user = self.s.username
        self.rc('gamerule naturalRegeneration false')
        self.rc(f'tp {user} 0.5 -60 0.5 0 0')
        time.sleep(2.0)
        hp = self.number(f'data get entity {user} Health')
        maximum = self.number(f'attribute {user} minecraft:generic.max_health get')
        self.check('player_health_survives_restart', close(hp, self.report['player_health_before'])
                   and close(maximum, self.report['player_max_before']), {'health': hp, 'max': maximum})
        wolf_hp = self.number(f'data get entity {WOLF} Health')
        wolf_max = self.number(f'attribute {WOLF} minecraft:generic.max_health get')
        self.check('beast_health_survives_restart', close(wolf_hp, self.report['wolf_health_before'])
                   and close(wolf_max, self.report['wolf_max_before']), {'health': wolf_hp, 'max': wolf_max})
        self.report['wolf_birth_after'] = self.one(f'myvillage wilderness birth {WOLF}', 'WILDERNESS_BIRTH')
        self.check('birth_realm_survives_restart',
                   self.report['wolf_birth_after'].get('birth') == self.report['wolf_birth_before'].get('birth')
                   and self.report['wolf_birth_after'].get('uuid') == self.report['wolf_birth_before'].get('uuid'),
                   self.report['wolf_birth_after'])
        self.report['player_stats_after'] = self.rc(f'myvillage stats info {user}')
        if 'natural_before' in self.report:
            natural = self.one(f'myvillage wilderness birth {NATURAL_WOLF}', 'WILDERNESS_BIRTH')
            before = self.report['natural_before']
            self.check('natural_birth_and_health_survive_restart',
                       natural.get('birth') == before.get('birth') and natural['birth']['natural']
                       and natural.get('uuid') == before.get('uuid')
                       and close(natural.get('health'), before['health'])
                       and close(natural.get('maxHealth'), before['maxHealth']), natural)
        self.report['phases'].append({'phase': 'restart_checks', 'wall_s': round(time.monotonic() - started, 3)})
        self.save()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT / 'out/preview/realm_stats')
    parser.add_argument('--skip-sword-qi', action='store_true')
    args = parser.parse_args(argv)
    if not procs.lock_is_free(procs.default_lock_path(ROOT)):
        raise SystemExit('Heavy-work lock is held; another Minecraft or Gradle job is active.')
    evidence = Evidence(args.out)
    try:
        evidence.start()
        evidence.initial(sword_qi=not args.skip_sword_qi)
        evidence.stop()
        evidence.start(reuse=True)
        evidence.restored()
    except Exception as exc:
        evidence.report['failures'].append(f'{type(exc).__name__}: {exc}')
        raise
    finally:
        try:
            if evidence.s is not None or cs.supervisor_alive(cs.read_state()):
                evidence.stop()
        finally:
            evidence.report['finished_utc'] = dt.datetime.now(dt.timezone.utc).isoformat()
            evidence.report['pass'] = (not evidence.report['failures'] and bool(evidence.report['checks'])
                                       and all(c['pass'] for c in evidence.report['checks']))
            evidence.save()
    return 0 if evidence.report['pass'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
