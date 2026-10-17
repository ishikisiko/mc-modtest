#!/usr/bin/env python3
"""Focused NPC life checks in a disposable Modal world with real clients.

Administrator commands prepare the authored showcase and read state. NPCs move
under their actual Goal; conversations use mapped right-click. Saved ledger NBT
is read, never edited. No owner appearance/FPS or natural-worldgen verdict.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.realm_stats_evidence import Evidence, json_lines
from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.combat_capture import session as cs
from tools.yunshan.game_evidence import aim, teleport_command
from tools.yunshan.game_export import read_typed
from tools.modal_runner.remote import NPC_LIFE_CASES, select_npc_life_cases

CASES = NPC_LIFE_CASES
FAILURES = {'stuck', 'path_unavailable', 'path_timeout', 'target_blocked', 'facility_missing'}


def resident_rows(reply):
    rows = json_lines(reply, 'NPC_LIFE')
    ids = [r['personId'] for r in rows]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate resident identities in status reply')
    for row in rows:
        if row['phase'] not in ('waiting', 'moving', 'working'):
            raise ValueError('Unknown resident phase')
    return rows


def read_ledger(path):
    _, root = read_typed(path)
    return json.loads(root.value['data'].value['payload'].value)


def assets(state):
    return {r['person_id']: (r['herbs'], r['coins'], r['completed_activities'])
            for r in state['life']['residents']}


class NpcLifeEvidence(Evidence):
    def __init__(self, out, cases=None):
        super().__init__(out)
        self.report.update(kind='npc_life_runtime', selected=select_npc_life_cases(cases), completed=[], cases={},
            unrun=list(CASES), sect_regression='not_verified', owner_sound='not_verified',
            owner_fps='not_verified', owner_appearance='not_verified', natural_worldgen='not_verified',
            method='actual NPC Goal; mapped right mouse; RCON fixture/readback; read-only saved ledger',
            default_life_rules=True, software_rendering=True)
        self.offset = [0, 0]
        self.ids = {}
        self.serial = 0
        self.rules = json.loads((ROOT/'src/main/resources/data/myvillage/world_sim/life_rules.json').read_text())
        layout = next(x for x in json.loads((ROOT/'src/main/resources/data/myvillage/npc_life/places.json').read_text())['layouts']
                      if x['id'] == 'yunshan_showcase_v2')
        self.places = {p['id']: p for p in layout['places']}

    def check(self, name, passed, detail):
        super().check(name, passed, detail)
        if not passed:
            raise AssertionError(name)

    def poll(self, getter, predicate, timeout, label):
        end = time.monotonic() + timeout
        last = None
        while time.monotonic() < end:
            last = getter()
            if predicate(last):
                return last
            time.sleep(.5)
        raise TimeoutError(f'{label}: {last}')

    def start(self, reuse=False):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('NPC game evidence requires the disposable Modal checkout')
        if not reuse:
            # A standard supported population tier, fixed before genesis. The small
            # seed-20261002 fixture was retained separately as a demo shortage.
            config = cs.SERVER_DIR/'config/myvillage-world_sim-server.toml'
            config.parent.mkdir(parents=True, exist_ok=True)
            config.write_text('[world_sim]\ntier = "large"\n')
        cs.merge_options(cs.CLIENT_DIR/'options.txt', {'key_key.use': 'key.mouse.right'})
        super().start(reuse)
        request = json.loads(Path('/workspace/request.json').read_text())
        self.report['source_commit'] = request['commit']
        self.report['jar_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'build/libs').glob('*.jar')}
        self.rc(f'op {self.s.username}')
        self.rc(f'gamemode creative {self.s.username}')

    def ledger(self, label, flush=True):
        if flush:
            self.rc('save-all flush')
        state = read_ledger(cs.SERVER_DIR/cs.LEVEL_NAME/'data/myvillage_world_sim.dat')
        self.serial += 1
        (self.out/f'{self.serial:03d}-{label}-ledger.json').write_text(json.dumps(state, indent=2))
        return state

    def status(self, person):
        rows = resident_rows(self.rc(f'myvillage life status {person}'))
        if len(rows) != 1:
            raise ValueError(f'Missing resident {person}')
        return rows[0]

    def residents(self):
        return resident_rows(self.rc('myvillage life status site showcase'))

    def xyz(self, local):
        return [self.offset[0]+local[0], -61+local[1], self.offset[1]+local[2]]

    def center(self, username=None):
        self.rc(teleport_command(username or self.s.username, self.xyz([35.5, 1, 25.5])))

    def entity(self, person, limit=True):
        return '@e[type=myvillage:cultivator,nbt={WorldSimPerson:'+str(person)+'}'+(',limit=1' if limit else '')+']'

    def body(self, person):
        return read_data(self.rc(f'data get entity {self.entity(person)}'))

    def count(self, person):
        reply = self.rc(f'execute as {self.entity(person, False)} run data get entity @s UUID')
        return reply.count('data: ')

    def setup(self):
        u = self.s.username
        for command in ('myvillage world pause', 'gamerule doMobSpawning false',
                        'gamerule doFireTick false', 'gamerule randomTickSpeed 0',
                        'gamerule spawnChunkRadius 0', 'time set day'):
            self.rc(command)
        origin = self.one('myvillage wilderness info 0 -60 0', 'WILDERNESS_INFO')
        candidates = self.ledger('candidate-selection')
        self.report['population_tier'] = candidates['tier']
        by_region = {}
        for person in candidates['persons']:
            if person['sect'] < 0 and person['rank'] == 'rogue' and person['status'] not in ('travelling', 'secluded'):
                by_region.setdefault(person['region'], []).append(person['id'])
        centers = {c['region']: c for c in origin['centers']}
        eligible = [region for region, people in by_region.items() if len(people) >= 4 and region in centers]
        self.check('four_existing_local_candidates', bool(eligible), by_region)
        region = max(eligible, key=lambda r: len(by_region[r]))
        chosen = centers[region]
        self.offset = [chosen['x'], chosen['z']]
        x, z = self.offset
        self.center()
        self.rc(f'forceload add {x-16} {z-32} {x+88} {z+88}')
        self.poll(lambda: self.rc(f'execute if loaded {x+80} -60 {z+80}'), lambda r: 'passed' in r, 45, 'showcase loaded')
        self.rc(f'fill {x-16} -62 {z-32} {x+88} -61 {z+88} minecraft:smooth_stone')
        self.rc(f'setworldspawn {x+600} -60 {z+600}')
        manifest = ROOT/'blueprints/yunshan/functional_buildings_v2.json'
        spec = json.loads(manifest.read_text())
        pack = cs.SERVER_DIR/cs.LEVEL_NAME/'datapacks/npc_life_showcase'
        target = pack/'data/myvillage/structure'
        target.mkdir(parents=True, exist_ok=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'NPC life isolated authored showcase'}}))
        buildings = [json.loads((manifest.parent/f).read_text()) for f in spec['buildings']]
        for b in buildings:
            name = b['id'].split(':')[1]
            source = ROOT/'out/preview/npc-life-buildings'/b['presentation']['role']/(name+'.nbt')
            shutil.copyfile(source, target/(name+'.nbt'))
        self.rc('reload')
        self.poll(lambda: self.rc('datapack list enabled'), lambda r: 'npc_life_showcase' in r, 20, 'fixture datapack')
        for b in buildings:
            px, py, pz = b['site']['world_min']
            self.rc(f'place template {b["id"]} {x+px} {py} {z+pz}')
        self.rc('forceload remove all')
        self.center()
        self.report['fixture'] = {'seed': 20261002, 'offset': self.offset, 'layout': 'yunshan_showcase_v2',
            'placement': 'administrator authored templates; no worldgen claim', 'life_rules': self.rules}
        self.initial = self.ledger('before-bind')

    def bind_idempotence(self):
        x, z = self.offset
        bind = f'myvillage life bind showcase yunshan_showcase_v2 {x} -61 {z}'
        self.rc(bind)
        gather = self.xyz([33, 1, 14])
        self.rc('myvillage life place showcase gather_lane gather '+' '.join(map(str, gather)))
        first = self.ledger('bound')
        self.rc(bind)
        again = self.ledger('rebound')
        self.check('bind_preserves_places_and_population', first['life']['sites'] == again['life']['sites']
            and len(again['life']['sites'][0]['places']) == 14
            and self.initial['persons'] == again['persons'], {'sites': again['life']['sites']})
        self.rc(f'myvillage life bind showcase yunshan_showcase_v2 {x+1} -61 {z}')
        conflict = self.ledger('conflict')
        self.check('conflicting_origin_rejected', conflict['life']['sites'] == again['life']['sites'], {})
        self.check('all_authored_places_available', all(v == 'available' for v in again['life']['observations'].values()), again['life']['observations'])

    def demo_identity(self):
        self.rc('myvillage life demo showcase')
        first = self.residents()
        self.check('four_residents_created', len(first) == 4, first)
        self.ids = {r['role']: r['personId'] for r in first}
        self.rc('myvillage life demo showcase')
        second = self.residents()
        self.check('demo_idempotent', first == second, second)
        state = self.ledger('demo')
        self.check('demo_uses_existing_people', {r['id'] for r in state['persons']} == {r['id'] for r in self.initial['persons']}, self.ids)
        self.rc('myvillage world resume')
        self.poll(self.residents, lambda rows: len(rows) == 4 and all(r['embodied'] for r in rows), 35, 'four actual bodies')

    def day_loop(self):
        pid = self.ids['rogue']
        samples = []
        end = time.monotonic()+240
        gathered = delivered = False
        while time.monotonic() < end:
            rows = self.residents()
            samples.append(rows)
            (self.out/'day-loop.json').write_text(json.dumps(samples, indent=2))
            r = next(r for r in rows if r['personId'] == pid)
            gathered |= r['herbs'] >= self.rules['gather_units']
            delivered |= gathered and r['herbs'] == 0 and r['coins'] > self.rules['initial_coins']
            if delivered:
                state = self.ledger('day-loop')
                if state['life']['supplies']['showcase']['medicine'] > 0:
                    break
            time.sleep(2)
        state = self.ledger('day-loop-end')
        supply = state['life']['supplies']['showcase']
        self.check('actual_gather_deliver_medicine_chain', gathered and delivered and supply['medicine'] > 0,
                   {'gathered': gathered, 'delivered': delivered, 'supply': supply, 'last': samples[-1]})
        positions = {(r['x'], r['y'], r['z']) for rows in samples for r in rows if r['personId'] == pid}
        self.check('rogue_walks_stairs_and_street', len(positions) >= 5 and len({p[1] for p in positions}) >= 2, sorted(positions))
        self.check('bounded_gather_quota', supply['gathered_today'] <= self.rules['gather_quota_per_day'], supply)
        self.s.game.shot(self.out/'day-loop.png', max_wait=3, settle=.4)

    def talk(self, person, username=None, game=None):
        username, game = username or self.s.username, game or self.s.game
        self.rc('myvillage world pause')
        body = self.body(person)
        x, y, z = body['Pos']
        # Choose an actually empty adjacent feet/head position; never teleport the NPC.
        possible = []
        for dx, dz in ((0, -2), (0, 2), (-2, 0), (2, 0)):
            px, py, pz = math.floor(x+dx), math.floor(y), math.floor(z+dz)
            reply = self.rc(f'execute if block {px} {py} {pz} minecraft:air if block {px} {py+1} {pz} minecraft:air unless block {px} {py-1} {pz} minecraft:air')
            if 'passed' in reply:
                possible.append([px+.5, py, pz+.5])
        self.check('conversation_standing_space', bool(possible), possible)
        contacted = False
        for attempt, position in enumerate(possible):
            # Empty feet/head cells do not prove line of sight past a counter.
            # Try the other genuinely standable approaches if the mapped ray misses.
            yaw, pitch = aim(position, [x, y+1.5, z])
            self.rc(teleport_command(username, position, yaw, pitch))
            self.rc(f'clear {username}')
            game.require_ingame('NPC mapped conversation')
            time.sleep(.5)
            game.shot(self.out/f'conversation-{username}-approach-{attempt}.png', max_wait=2, settle=.1)
            game.xdo('mousedown', '3')
            time.sleep(.1)
            game.xdo('mouseup', '3')
            try:
                self.poll(lambda: self.status(person), lambda r: r['pauseTicks'] > 0, 3, 'mapped conversation pause')
                contacted = True
                break
            except TimeoutError:
                continue
        self.check('mapped_conversation_reaches_resident', contacted, {'person': person, 'approaches': possible})
        game.shot(self.out/f'conversation-{username}.png', max_wait=3, settle=.2)
        self.rc('myvillage world resume')

    def conversation(self):
        pid = self.ids['innkeeper']
        before = self.ledger('conversation-before')
        self.talk(pid)
        first = self.ledger('conversation-first')
        self.poll(lambda: self.status(pid), lambda r: r['pauseTicks'] == 0, 12, 'conversation resumes')
        self.talk(pid)
        second = self.ledger('conversation-repeat')
        a = [r for r in first['social']['acquaintances'] if r['npc'] == f'npc:{pid}']
        b = [r for r in second['social']['acquaintances'] if r['npc'] == f'npc:{pid}']
        self.check('mapped_conversation_saved_once_per_day', len(a) == 1 and a == b,
                   {'before': before['social'], 'first': a, 'repeat': b})
        self.center()

    def blocked_route(self):
        pid = self.ids['rogue']
        self.rc('myvillage world resume')
        r = self.poll(lambda: self.status(pid), lambda r: r['phase'] == 'moving' and r['embodied'], 90, 'moving resident')
        self.rc('myvillage world pause')
        body = self.body(pid)
        x, y, z = map(math.floor, body['Pos'])
        target = next(p for p in self.ledger('route-before')['life']['sites'][0]['places'] if p['id'] == r['targetId'])
        self.check('barrier_separate_from_target', max(abs(target['x']-x), abs(target['z']-z)) > 2, target)
        # Reversible shell around current body, leaving feet/head and target untouched.
        backup = []
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if dx == dz == 0:
                    continue
                for dy in (0, 1, 2):
                    p = (x+dx, y+dy, z+dz)
                    reply = self.rc('execute if block '+' '.join(map(str, p))+' minecraft:air')
                    if 'passed' in reply:
                        backup.append(p)
        before = self.status(pid)
        try:
            for p in backup:
                self.rc('setblock '+' '.join(map(str, p))+' minecraft:barrier')
            self.rc('myvillage world resume')
            failed = self.poll(lambda: self.status(pid), lambda r: r['reason'] in FAILURES, 70, 'blocked route fails')
            self.check('blocked_job_no_reward', failed['completedActivities'] == before['completedActivities']
                       and failed['herbs'] == before['herbs'] and failed['coins'] == before['coins'], {'before': before, 'after': failed})
        finally:
            for p in backup:
                self.rc('setblock '+' '.join(map(str, p))+' minecraft:air')
            self.rc('myvillage world resume')
        self.poll(lambda: self.status(pid), lambda r: r['completedActivities'] > before['completedActivities'], 100, 'restored route completes')

    def removed_facility(self):
        p = self.places['herbalist_work']
        xyz = self.xyz(p['block_pos'])
        pos = ' '.join(map(str, xyz))
        self.rc('myvillage world pause')
        before = self.ledger('facility-before')
        try:
            self.rc(f'setblock {pos} minecraft:air')
            self.rc('myvillage life status site showcase')
            removed = self.ledger('facility-removed')
            self.check('removed_furnace_blocks_station', removed['life']['observations']['showcase|herbalist_work'] == 'blocked', {})
            self.rc('myvillage world resume')
            time.sleep(self.rules['durations']['work']/20+3)
            after = self.ledger('facility-no-production')
            self.check('absent_furnace_no_medicine', before['life']['supplies']['showcase']['medicine'] == after['life']['supplies']['showcase']['medicine'], after['life']['supplies'])
        finally:
            self.rc(f'setblock {pos} {p["block"]}')
        self.rc('myvillage life status site showcase')
        self.check('restored_furnace_available', self.ledger('facility-restored')['life']['observations']['showcase|herbalist_work'] == 'available', {})

    def away(self, username=None):
        x, z = self.offset
        self.rc(teleport_command(username or self.s.username, [x+600.5, -60, z+600.5]))

    def unload_return(self):
        before = self.ledger('unload-before')
        self.away()
        self.poll(self.residents, lambda rows: all(not r['embodied'] and not r['observed'] for r in rows), 30, 'withdraw bodies')
        time.sleep(35)
        cold = self.ledger('cold')
        self.check('same_ids_after_withdraw', set(assets(before)) == set(assets(cold)), assets(cold))
        self.check('cold_mode_advances_finitely', any(assets(cold)[p][2] > assets(before)[p][2] for p in assets(before)), {'before': assets(before), 'cold': assets(cold)})
        self.center()
        self.poll(self.residents, lambda rows: all(r['embodied'] for r in rows), 35, 'return bodies')
        self.check('single_body_per_person_after_return', all(self.count(p) == 1 for p in self.ids.values()), self.ids)

    def restart_mid_activity(self):
        pid = self.ids['innkeeper']
        self.poll(lambda: self.status(pid), lambda r: r['phase'] == 'working' and 0 < r['progressTicks'] < r['requiredTicks']-40, 120, 'unfinished actual work')
        self.rc('myvillage world pause')
        before_rows = self.residents()
        before = self.ledger('restart-before')
        self.stop()
        stopped = self.ledger('restart-stopped', flush=False)
        time.sleep(5)
        self.start(reuse=True)
        after = self.ledger('restart-paused')
        fields = ('person_id', 'revision', 'phase', 'activity', 'target_id', 'progress_ticks', 'completed_activities', 'coins', 'herbs')
        project = lambda s: [{k: r[k] for k in fields} for r in s['life']['residents']]
        self.check('restart_exact_activity_and_balances', project(before) == project(stopped) == project(after)
            and before['life']['supplies'] == after['life']['supplies'], {'before': project(before), 'after': project(after)})
        self.rc('myvillage world resume')
        rows = self.poll(self.residents, lambda rows: all(r['embodied'] for r in rows), 30, 'new leases')
        self.check('restart_renews_leases', all(r['epoch'] > next(b['epoch'] for b in before_rows if b['personId'] == r['personId']) for r in rows), rows)
        before_count = next(r['completedActivities'] for r in before_rows if r['personId'] == pid)
        self.poll(lambda: self.status(pid), lambda r: r['completedActivities'] == before_count+1, 45, 'work continues exactly once')

    def two_players(self):
        pid = self.ids['innkeeper']
        self.center()
        with SecondClient(self) as guest:
            self.rc(f'gamemode creative {guest.username}')
            self.center(guest.username)
            self.poll(lambda: self.status(pid), lambda r: r['embodied'], 20, 'body near two players')
            uuid = self.body(pid)['UUID']
            self.talk(pid, guest.username, guest.game)
            state = self.ledger('two-player-conversation')
            known = [r for r in state['social']['acquaintances'] if r['npc'] == f'npc:{pid}']
            self.check('two_independent_acquaintances', len(known) == 2 and len({r['player'] for r in known}) == 2, known)
            self.away()
            time.sleep(5)
            self.check('guest_keeps_same_single_body', self.status(pid)['embodied'] and self.body(pid)['UUID'] == uuid and self.count(pid) == 1, {})
            self.away(guest.username)
            self.poll(lambda: self.status(pid), lambda r: not r['embodied'], 30, 'both players leave')
        self.center()
        self.report['multiplayer'] = 'two_actual_clients_mapped_conversation_and_shared_body_pass'

    def avatar_cap(self):
        self.rc('myvillage world pause')
        self.stop()
        config = cs.SERVER_DIR/cs.LEVEL_NAME/'serverconfig/myvillage-world_sim-server.toml'
        config_existed = config.exists()
        original = (config if config_existed else cs.SERVER_DIR/'config/myvillage-world_sim-server.toml').read_text()
        config.parent.mkdir(parents=True, exist_ok=True)
        changed, n = re.subn(r'(?m)^(\s*max_avatars\s*=\s*)\d+', r'\g<1>1', original)
        self.check('avatar_cap_config_found', n == 1, {'path': str(config)})
        config.write_text(changed)
        try:
            self.start(reuse=True)
            self.center()
            self.rc('myvillage world resume')
            rows = self.poll(self.residents, lambda rows: sum(r['embodied'] for r in rows) == 1 and all(r['observed'] for r in rows), 35, 'one body and four observers')
            waiting = {r['personId']: r for r in rows if not r['embodied']}
            time.sleep(25)
            after = {r['personId']: r for r in self.residents()}
            fields = ('revision', 'progressTicks', 'completedActivities', 'herbs', 'coins')
            self.check('cap_waiters_no_hidden_progress', all(not after[p]['embodied'] and all(r[f] == after[p][f] for f in fields) for p, r in waiting.items()), {'before': waiting, 'after': after})
        finally:
            if self.s:
                self.rc('myvillage world pause')
                self.stop()
            if config_existed:
                config.write_text(original)
            else:
                config.unlink()
            self.start(reuse=True)
            self.center()
            self.rc('myvillage world resume')

    def occupied_spawn(self):
        pid = self.ids['innkeeper']
        self.rc('myvillage world pause')
        self.away()
        self.poll(lambda: self.status(pid), lambda r: not r['embodied'], 30, 'withdraw for obstruction')
        r = self.status(pid)
        pos = f'{r["x"]} {r["y"]} {r["z"]}'
        # Load through an actual spectator; spectators cannot count as observers.
        self.rc(f'gamemode spectator {self.s.username}')
        self.center()
        self.poll(lambda: self.rc(f'execute if loaded {pos}'), lambda s: 'passed' in s, 25, 'obstruction chunk')
        self.check('spawn_obstruction_reversible_air', 'passed' in self.rc(f'execute if block {pos} minecraft:air'), pos)
        try:
            self.rc(f'setblock {pos} minecraft:barrier')
            self.rc(f'gamemode creative {self.s.username}')
            self.rc('myvillage world resume')
            time.sleep(5)
            after = self.status(pid)
            self.check('occupied_spawn_waits_without_reward', after['observed'] and not after['embodied']
                and all(after[k] == r[k] for k in ('completedActivities', 'herbs', 'coins')), {'before': r, 'after': after})
        finally:
            self.rc(f'setblock {pos} minecraft:air')
        self.poll(lambda: self.status(pid), lambda r: r['embodied'], 30, 'unobstructed spawn')

    def sect_dialogue(self, person, label):
        body = self.body(person)
        x, y, z = body['Pos']
        position = None
        for dx, dz in ((0, 2), (0, -2), (2, 0), (-2, 0)):
            bx, by, bz = math.floor(x+dx), math.floor(y), math.floor(z+dz)
            reply = self.rc(f'execute if block {bx} {by} {bz} minecraft:air if block {bx} {by+1} {bz} minecraft:air unless block {bx} {by-1} {bz} minecraft:air')
            if 'passed' in reply:
                position = [bx+.5, by, bz+.5]
                break
        self.check(label+'_standing_space', position is not None, position)
        yaw, pitch = aim(position, [x, y+1.2, z])
        self.rc(teleport_command(self.s.username, position, yaw, pitch))
        self.rc(f'clear {self.s.username}')
        self.s.game.require_ingame('mapped sect dialogue')
        log = cs.CLIENT_DIR/'logs/latest.log'
        offset = log.stat().st_size
        time.sleep(.6)
        self.s.game.xdo('click', '3')
        self.poll(self.s.game.pointer_grabbed, lambda v: not v, 10, label+' opens')
        time.sleep(.5)
        text = log.read_bytes()[offset:].decode(errors='replace')
        options = {}
        for m in re.finditer(r'SECT_DIALOGUE option=(\w+) x=(\d+) y=(\d+) w=(\d+) h=(\d+)', text):
            key, x, y, w, h = m.groups()
            options[key] = [int(x)+int(w)//2, int(y)+int(h)//2]
        self.check(label+'_dialogue_options', bool(options), options)
        raw, _ = self.s.game.stable_grab(max_wait=3)
        self.s.game.save_png(raw, self.out/(label+'.png'))
        return options

    def close_screen(self):
        if not self.s.game.pointer_grabbed():
            self.s.game.xdo('key', 'Escape')
            self.poll(self.s.game.pointer_grabbed, bool, 8, 'dialogue closed')

    def choose_option(self, options, option):
        self.check('sect_option_'+option, option in options, options)
        x, y = options[option]
        self.s.game.xdo('mousemove', str(x), str(y))
        self.s.game.xdo('click', '1')
        time.sleep(.6)
        self.close_screen()

    def sect_regression(self):
        from tools.combat_capture.rcon import connect
        from tools.world_sim_entry_evidence import panel_tab_center
        self.rc('myvillage world pause')
        initial = self.ledger('sect-before')
        members = lambda sect: [p for p in initial['persons'] if p['sect'] == sect['id'] and p['status'] == 'at_sect']
        sects = [s for s in initial['sects'] if s['state'] == 'active' and len(members(s)) >= 2]
        self.check('existing_sect_fixture', bool(sects), {'count': len(sects)})
        sect = max(sects, key=lambda s: len(members(s)))
        sid, u = sect['id'], self.s.username
        x, z = sect['gate_x'], sect['gate_z']
        self.rc(teleport_command(u, [x+.5, -60, z-20.5]))
        command = f'myvillage world sect {sid} build'
        with connect(self.s.state['rcon_port'], self.s.state['rcon_password'], retries=1, timeout=180) as r:
            reply = r.cmd(command)
        self.report['commands'].append({'command': command, 'reply': reply, 'purpose': 'admin gate fixture; no natural-realization claim'})
        self.save()
        self.rc(teleport_command(u, [x+.5, -59, z+5.5]))
        def avatars():
            found = {}
            for p in members(sect):
                reply = self.rc(f'data get entity {self.entity(p["id"])}')
                if 'data: ' in reply:
                    found[p['id']] = read_data(reply)
            return found
        bodies = self.poll(avatars, lambda rows: any('avatar.steward' in b.get('CustomName', '') for b in rows.values()), 60, 'actual sect steward')
        steward = next(pid for pid, b in bodies.items() if 'avatar.steward' in b.get('CustomName', ''))
        self.check('town_residents_excluded_from_gate_avatars', not set(bodies).intersection(self.ids.values()), {'steward': steward, 'gate_people': list(bodies)})
        self.rc(f'myvillage cultivation setroot {u} 4000 1500 1500 1500 1500')
        self.rc(f'myvillage cultivation setrealm {u} myvillage:mortal myvillage:mortal_qi_sensed')
        options = self.sect_dialogue(steward, 'sect-join')
        self.choose_option(options, 'JOIN')
        joined = self.ledger('sect-joined')
        member = next((p for p in joined['player_members'] if p['name'] == u), None)
        self.check('mapped_sect_join', member is not None and member['sect'] == sid, member)
        options = self.sect_dialogue(steward, 'sect-task')
        self.choose_option(options, 'TASK_ACCEPT')
        tasked = self.ledger('sect-task-accepted')
        member = next(p for p in tasked['player_members'] if p['name'] == u)
        self.check('mapped_task_acceptance', bool(member['task']), member)
        self.rc(f'myvillage world sect {sid} rank {u} inner')
        elders = [p for p in members(sect) if p['rank'] in ('elder', 'master') and p['id'] in bodies and p['id'] != steward]
        self.check('existing_elder_available', bool(elders), [p['id'] for p in elders])
        options = self.sect_dialogue(elders[0]['id'], 'sect-elder')
        self.check('elder_interaction_available', 'APPRENTICE' in options or 'FAREWELL' in options, options)
        self.close_screen()
        self.s.game.xdo('key', 'h')
        self.poll(self.s.game.pointer_grabbed, lambda p: not p, 8, 'H panel opens')
        px, py = panel_tab_center('world')
        self.s.game.xdo('mousemove', str(px), str(py))
        self.s.game.xdo('click', '1')
        time.sleep(1)
        raw, _ = self.s.game.stable_grab(max_wait=3)
        self.s.game.save_png(raw, self.out/'sect-world-panel.png')
        self.close_screen()
        options = self.sect_dialogue(steward, 'sect-leave')
        self.choose_option(options, 'LEAVE')
        left = self.ledger('sect-left')
        member = next(p for p in left['player_members'] if p['name'] == u)
        self.check('mapped_sect_leave', member['sect'] == -1, member)
        self.center()
        after = self.ledger('sect-return')
        self.check('town_identity_and_work_preserved_during_sect_visit', assets(initial) == assets(after), {'before': assets(initial), 'after': assets(after)})
        self.rc('myvillage world resume')
        self.report['sect_regression'] = 'mapped_join_task_accept_elder_dialogue_leave_and_panel_capture; task_completion_not_verified'

    def run(self):
        self.start()
        self.setup()
        for name in self.report['selected']:
            started = time.monotonic()
            self.report['unrun'].remove(name)
            try:
                getattr(self, name)()
                self.report['cases'][name] = {'result': 'pass'}
                self.report['completed'].append(name)
            except Exception as error:
                self.report['cases'][name] = {'result': 'fail', 'error': f'{type(error).__name__}: {error}'}
                self.report['failures'].append(name+': '+str(error))
                print('CASE FAILED', name, repr(error), flush=True)
                if name in ('bind_idempotence', 'demo_identity'):
                    raise
                if self.s:
                    self.close_screen()
                    self.rc('myvillage world resume')
                    self.center()
            finally:
                self.report['cases'].setdefault(name, {})['wall_seconds'] = round(time.monotonic()-started, 3)
                self.save()
        self.rc('myvillage world pause')
        final = self.ledger('final')
        self.report['final_supply'] = final['life']['supplies']
        for pid in self.ids.values():
            self.rc(f'myvillage life unassign {pid}')
        cleanup = self.ledger('cleanup')
        self.check('cleanup_retains_people', all(pid in {p['id'] for p in cleanup['persons']} for pid in self.ids.values())
                   and not cleanup['life']['residents'], self.ids)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/npc-life')
    parser.add_argument('--cases')
    args = parser.parse_args()
    evidence = NpcLifeEvidence(args.out, args.cases)
    try:
        evidence.run()
    except Exception as error:
        evidence.report['failures'].append(f'{type(error).__name__}: {error}')
        raise
    finally:
        if evidence.s:
            evidence.stop()
        evidence.save()
    return 1 if evidence.report['failures'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
