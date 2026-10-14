#!/usr/bin/env python3
"""Bounded player progression evidence with mapped V/B/X/N and a direct restart.

Run clean acceptance stage 1 first. Administrative near-cap preparation is a
fixture, not natural acquisition or a pacing measurement. No DevBridge is used.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import secrets
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.combat_capture import procs, scene, session as cs  # noqa: E402
from tools.realm_stats_evidence import Evidence, close  # noqa: E402


def parse_profile(reply: str) -> dict:
    """Read the existing operator output; absent fields are evidence failures."""
    names = {'realm': 'realm', 'stage': 'stage', 'cultivation progress': 'progress',
             'stability': 'stability', 'spiritual affinity': 'affinity',
             'lifespan consumed ticks': 'age_ticks', 'spiritual root': 'root',
             'learned techniques': 'learned', 'running core technique': 'core'}
    found = {}
    for line in reply.splitlines():
        key, sep, value = line.partition(': ')
        if sep and key in names:
            found[names[key]] = value.strip()
        if key == 'current spiritual power' and sep:
            match = re.fullmatch(r'(\d+)/(\d+)', value.strip())
            if match:
                found['power'], found['power_cap'] = map(int, match.groups())
    missing = set(names.values()) | {'power', 'power_cap'}
    missing -= found.keys()
    if missing:
        raise ValueError(f'Incomplete cultivation readback: {sorted(missing)}')
    for key in ('progress', 'stability', 'affinity', 'age_ticks'):
        found[key] = int(found[key])
    if any('(unavailable)' in found[key] for key in ('realm', 'stage')):
        raise ValueError('Unavailable realm/stage in cultivation readback')
    return found


def shipped_chain(root: Path = ROOT) -> list[dict]:
    """Follow authored targets, refusing a cycle or an incomplete evidence path."""
    directory = root / 'src/main/resources/data/myvillage/myvillage/realm'
    stages = {}
    for path in directory.glob('*.json'):
        realm = json.loads(path.read_text())
        for row in realm['stages']:
            if row['id'] in stages:
                raise ValueError(f'Duplicate stage: {row["id"]}')
            stages[row['id']] = {**row, 'realm': f'myvillage:{path.stem}',
                                 'lifespan': realm['maximum_lifespan_years']}
    chain, seen, current = [], set(), 'myvillage:mortal_qi_sensed'
    while current not in seen:
        seen.add(current)
        row = stages[current]
        chain.append(row)
        if 'advancement' not in row:
            if current != 'myvillage:golden_late':
                raise ValueError(f'Premature terminal: {current}')
            if len(chain) != 16:
                raise ValueError(f'Expected sixteen reachable stages, got {len(chain)}')
            return chain
        target = row['advancement']
        current = target['target_stage']
        if stages[current]['realm'] != target['target_realm']:
            raise ValueError(f'Target realm mismatch: {current}')
    raise ValueError(f'Cycle at {current}')


class GoldenEvidence(Evidence):
    def __init__(self, out: Path, tick_rate: int):
        super().__init__(out)
        self.tick_rate = tick_rate
        self.report.update(kind='player_golden_core_progression',
                           method='physical client mapped V/B/X/N; RCON fixture setup/readback; no DevBridge',
                           preparation='administrator near-cap and stability fixtures; no intermediate setrealm',
                           natural_acquisition='not_verified', progression_pacing='not_verified',
                           death_end_dimension='not_verified', tick_rate=tick_rate,
                           transitions=[])
        self.report.pop('natural_encounter_density', None)

    def start(self, reuse=False):
        started = time.monotonic()
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse,
                                         lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        options = ROOT/'run-combat-capture/client/options.txt'
        saved = dict(line.split(':', 1) for line in options.read_text().splitlines() if ':' in line)
        bindings = {'start_normal_meditation': 'v', 'start_spirit_meditation': 'b',
                    'stop_meditation': 'x', 'start_advancement': 'n', 'open_cultivation_profile': 'h'}
        for action, key in bindings.items():
            if saved.get('key_key.myvillage.'+action) != 'key.keyboard.'+key:
                raise RuntimeError(f'Capture binding differs for {action}; no binding is rewritten')
        self.report['verified_bindings'] = bindings
        self.sessions += 1
        self.report['phases'].append({'phase': 'restart_setup' if reuse else 'initial_setup',
                                     'wall_s': round(time.monotonic() - started, 3),
                                     'capture_timings': state.get('timings', {})})
        self.save()

    def profile(self):
        return parse_profile(self.rc(f'myvillage cultivation info {self.s.username}'))

    def wait_profile(self, predicate, timeout, reason):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            row = self.profile()
            if predicate(row):
                return row
            time.sleep(.15)
        raise RuntimeError(f'Timed out waiting for {reason}; last={row}')

    def stop_input(self):
        self.s.game.key('x')
        time.sleep(.15)

    def stone_count(self):
        reply = self.rc(f'clear {self.s.username} myvillage:low_grade_spirit_stone 0')
        match = re.search(r'Found (\d+)', reply)
        if match:
            return int(match.group(1))
        if 'No items were found' in reply or 'No items found' in reply:
            return 0
        raise ValueError(f'Unrecognized item count: {reply}')

    def near_cap(self, source, mode):
        user = self.s.username
        self.stop_input()
        self.rc(f'myvillage cultivation setprogress {user} {source["cultivation_cap"] - 1}')
        self.rc(f'myvillage cultivation setstability {user} 0')
        self.rc(f'clear {user} myvillage:low_grade_spirit_stone')
        if mode == 'b':
            self.rc(f'give {user} myvillage:low_grade_spirit_stone {source["spirit_stone_cost"]}')
        self.s.game.key(mode)
        self.wait_profile(lambda p: p['progress'] == source['cultivation_cap'], 12,
                          f'{source["id"]} {mode} gain to cap')
        self.stop_input()
        row = self.profile()
        remaining = self.stone_count()
        self.check(f'{source["id"]}_{mode}_near_cap',
                   row['stage'] == source['id'] and row['progress'] == source['cultivation_cap']
                   and remaining == 0,
                   {'progress': row['progress'], 'remaining_stones': remaining,
                    'expected_cost': source['spirit_stone_cost'] if mode == 'b' else 0})

    def initial(self):
        started = time.monotonic()
        user = self.s.username
        for command in (*scene.world_rules(), *scene.player_setup(user, 'minecraft:stick'),
                        'gamerule naturalRegeneration false', 'myvillage world pause',
                        'forceload add -16 -16 32 32', f'myvillage cultivation awaken {user}',
                        f'myvillage cultivation initiate {user}', f'tick rate {self.tick_rate}'):
            self.rc(command)
        # Existing acquisition is a separate acceptance surface; these are explicit setup fixtures.
        self.rc(f'myvillage cultivation setrealm {user} myvillage:mortal myvillage:mortal_qi_sensed')
        self.rc(f'damage {user} 3 minecraft:generic')
        time.sleep(max(.5, 110 / self.tick_rate))
        self.report['wounded_health'] = self.number(f'data get entity {user} Health')
        chain = shipped_chain()
        rules = json.loads((ROOT/'src/main/resources/data/myvillage/stats/rules.json').read_text())
        for source, target in zip(chain, chain[1:]):
            row = self.profile()
            if (row['realm'], row['stage']) != (source['realm'], source['id']):
                raise RuntimeError(f'Chain is not at expected source {source["id"]}: {row}')
            stage_started = time.monotonic()
            self.near_cap(source, 'v')
            self.near_cap(source, 'b')
            advancement = source['advancement']
            required = advancement['required_stability']
            # Reject an underfilled source through the same mapped advancement input.
            if source['realm'] != target['realm']:
                self.rc(f'myvillage cultivation setprogress {user} {source["cultivation_cap"]-1}')
                self.rc(f'myvillage cultivation setstability {user} {required}')
                self.s.game.key('n'); time.sleep(.4); self.stop_input()
                rejected = self.profile()
                self.check(f'{source["id"]}_incomplete_progress_rejected',
                           rejected['stage'] == source['id']
                           and rejected['progress'] == source['cultivation_cap']-1
                           and rejected['stability'] == required, rejected)
                self.rc(f'myvillage cultivation setprogress {user} {source["cultivation_cap"]}')
            self.rc(f'myvillage cultivation setstability {user} {required}')
            if advancement['kind'] == 'bottleneck':
                self.s.game.key('n'); time.sleep(.4); self.stop_input()
                interrupted = self.profile()
                self.check(f'{source["id"]}_interruption_loss',
                           interrupted['stage'] == source['id']
                           and interrupted['stability'] == required-advancement['interruption_stability_loss'],
                           interrupted)
                self.rc(f'myvillage cultivation setstability {user} {required}')
            self.rc(f'myvillage cultivation setpower {user} 0')
            before = self.profile()
            self.s.game.key('n')
            after = self.wait_profile(lambda p: p['stage'] != source['id'],
                                      min(60, advancement['duration_ticks'] / self.tick_rate + 15),
                                      f'{source["id"]} -> {target["id"]}')
            stats = self.one(f'myvillage stats info {user}', 'STATS_INFO')
            hp = self.number(f'data get entity {user} Health')
            maximum = self.number(f'attribute {user} minecraft:generic.max_health get')
            alias = next(a for a in rules['player_stage_aliases'] if a['id'] == target['id'])
            realm = next(r for r in rules['realms'] if r['id'] == alias['realm'])
            growth = rules['growth']
            expected_max = 20 * growth['entry_multiplier'] * growth['major_ratio']**realm['order'] * (1+growth['minor_slope']*alias['stage'])
            passed = ((after['realm'], after['stage']) == (target['realm'], target['id'])
                      and after['progress'] == 0 and after['stability'] == before['stability']//2
                      and after['power_cap'] == target['spiritual_power_cap']
                      and before['power'] <= after['power'] < after['power_cap']
                      and after['age_ticks'] >= before['age_ticks']
                      and all(after[k] == before[k] for k in ('root', 'learned', 'core', 'affinity'))
                      and close(hp, self.report['wounded_health']) and close(maximum, expected_max))
            observation = {'source': source['id'], 'target': target['id'], 'before': before,
                           'after': after, 'health': hp, 'max_health': maximum,
                           'expected_max_health': expected_max, 'stats': stats,
                           'power_note': 'ordinary regeneration continues; advancement must not fill new capacity',
                           'wall_s': round(time.monotonic()-stage_started, 3)}
            self.report['transitions'].append(observation)
            self.check(f'{source["id"]}_advance', passed, observation)
            if after['stage'] != target['id']:
                raise RuntimeError('Unexpected advancement target; refusing to repair the chain with setrealm')
        self.stop_input()
        baseline = self.profile()
        self.rc(f'give {user} myvillage:low_grade_spirit_stone 64')
        stones = self.stone_count()
        for key in ('v', 'b', 'n'):
            self.s.game.key(key); time.sleep(.8)
            terminal = self.profile()
            self.check(f'golden_late_{key}_refused',
                       terminal['stage'] == 'myvillage:golden_late'
                       and terminal['progress'] == baseline['progress']
                       and terminal['stability'] == baseline['stability']
                       and self.stone_count() == stones, terminal)
            self.stop_input()
        self.rc('tick rate 20')
        self.rc(f'myvillage cultivation setpower {user} 123')
        self.rc(f'gamemode creative {user}')  # Freeze personal age during restart checks.
        self.rc('save-all flush')  # Commit pending lifespan before taking the comparison baseline.
        self.report['before_restart'] = self.profile()
        self.report['health_before_restart'] = self.number(f'data get entity {user} Health')
        self.report['max_before_restart'] = self.number(f'attribute {user} minecraft:generic.max_health get')
        self.rc('save-all flush')
        self.s.game.key('h')
        try:
            time.sleep(.8)
            self.s.game.save_png(self.s.game.grab_raw(), self.out/'golden_late_panel.png')
            self.report['panel_image'] = 'golden_late_panel.png'
        finally:
            self.s.game.xdo('key', 'Escape')
            time.sleep(.5)
            self.s.game.require_ingame('return from Golden Core panel')
        self.report['phases'].append({'phase': 'progression_checks', 'wall_s': round(time.monotonic()-started, 3)})
        self.save()

    def restored(self):
        started = time.monotonic()
        user = self.s.username
        self.rc('gamerule naturalRegeneration false')
        after = self.profile(); before = self.report['before_restart']
        hp = self.number(f'data get entity {user} Health')
        maximum = self.number(f'attribute {user} minecraft:generic.max_health get')
        setup = self.report['phases'][-1]['wall_s']
        # At ordinary 20 TPS at most one base point per second; allow readiness overhead.
        recovery_bound = math.ceil(setup) + 5
        self.check('golden_late_direct_restart',
                   all(after[k] == before[k] for k in ('realm','stage','progress','stability','root','learned','core','affinity','age_ticks','power_cap'))
                   and before['power'] <= after['power'] <= before['power']+recovery_bound
                   and after['power'] < after['power_cap']
                   and close(hp,self.report['health_before_restart'])
                   and close(maximum,self.report['max_before_restart']),
                   {'before':before,'after':after,'health':hp,'maximum':maximum,
                    'power_recovery_bound':recovery_bound})
        self.report['phases'].append({'phase':'restart_checks','wall_s':round(time.monotonic()-started,3)})
        self.save()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/golden_core')
    parser.add_argument('--tick-rate',type=int,default=80)
    args=parser.parse_args(argv)
    if not 20 <= args.tick_rate <= 100:
        parser.error('--tick-rate must be between 20 and 100')
    shipped_chain()  # Fail before allocating Minecraft for an inconsistent evidence path.
    if not procs.lock_is_free(procs.default_lock_path(ROOT)):
        raise SystemExit('Heavy-work lock held by another Gradle or Minecraft job.')
    evidence=GoldenEvidence(args.out,args.tick_rate)
    try:
        evidence.start(); evidence.initial(); evidence.stop()
        evidence.start(reuse=True); evidence.restored()
    except Exception as exc:
        evidence.report['failures'].append(f'{type(exc).__name__}: {exc}')
        raise
    finally:
        try:
            if evidence.s is not None or cs.supervisor_alive(cs.read_state()): evidence.stop()
        finally:
            evidence.report['finished_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
            evidence.report['pass'] = (len(evidence.report['transitions'])==15
                and not evidence.report['failures'] and all(c['pass'] for c in evidence.report['checks']))
            evidence.save()
    return 0 if evidence.report['pass'] else 1


if __name__=='__main__':
    raise SystemExit(main())
