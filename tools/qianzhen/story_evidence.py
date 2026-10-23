#!/usr/bin/env python3
"""阡镇第一章真实客户端证据；仅在显式隔离云端 checkout 运行。

Production deploy, physical WASD/right mouse/dialogue buttons, authoritative
readback. This module never supplies a quest completion command or packet.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import math
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT/'tools'))

from tools.qianzhen.game_evidence import QianzhenEvidence, KEYS, cs, procs, scene
from tools.qianzhen.game_evidence import ffmpeg_record_argv, stop_recording
from tools.qianzhen.game_routes import traversal_ends, walk_point
from tools.qianzhen.game_routes import opened_door_graph
from tools.realm_stats_evidence import Evidence
from tools.alchemy_quality_evidence import SecondClient, read_data
from tools.yunshan.game_evidence import aim, teleport_command
from tools.qianzhen.story_evidence_helpers import (
    cardinal_path, client_views, compile_route, dialogue_buttons, finite_vector, require_subset,
    reward_counts, status_object,
)
from tools.qianzhen import story_resume
from tools.qianzhen.evidence_candidate import Candidate, DEFAULT_REQUEST, add_arguments, runtime_request

HANDOFF = ROOT/'reports/qianzhen-story/runtime-handoff.json'
HANDOFF_SNAPSHOT = Path(__file__).with_name('story_evidence_handoff.json')
CHAPTER = ROOT/'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json'
SELECTED = ['production_deploy', 'continuous_first_chapter', 'rescue_restart',
            'full_inventory_reward', 'reward_restart', 'late_second_player_other_branch',
            'death_relog_dimension', 'normal_narrow_ui', 'fresh_entry_world', 'saved_world']


def route_ends(path, blocks):
    """Keep native door-panel precision and a reached cell before every door."""
    ends = set(traversal_ends(path, blocks))
    for index, node in enumerate(path):
        if index and '_door[' in blocks.get(tuple(node), ''):
            ends.add(path[index-1])
    return [node for node in path[1:] if node in ends]


class StoryEvidence(QianzhenEvidence):
    """Reuse input/position/capture primitives, deliberately skip grid __init__/setup."""

    def __init__(self, out, handoff=HANDOFF, origin=(0, -61, 0), *,
                 candidate=None, request=DEFAULT_REQUEST, provider="Modal"):
        self.candidate = Candidate(candidate, ROOT)
        self.request_path, self.provider = request, provider
        if out.exists() and any(out.iterdir()):
            raise ValueError('Keep prior failures; use an empty evidence directory: '+str(out))
        Evidence.__init__(self, out)
        self.language = 'zh_cn'
        self.origin = finite_vector(origin)
        self.handoff_path = handoff
        self.held, self.hud_hidden, self.recorder = set(), False, None
        self.position_stream = (out/'movement-readbacks.jsonl').open('w')
        self.action_stream = (out/'actions.jsonl').open('w')
        self.continuous = False
        self.actor = None
        self.primary_username = None
        self.video_index = 0
        self.report.update(kind='qianzhen_story_native_mc', selected=SELECTED.copy(),
            completed=[], unrun=SELECTED.copy(), status='preflight',
            candidate=self.candidate.identity, provider=self.provider,
            input_log='actions.jsonl', position_log='movement-readbacks.jsonl',
            readbacks=[], routes=[], screenshots=[], api_dependencies=[],
            method=__doc__, fixture='独立新 flatworld；生产 deploy；保留玩家数据',
            software_rendering=True, owner_appearance='not_verified', owner_feel='not_verified',
            owner_sound='not_verified', owner_fps='not_verified', owner_multiplayer='not_verified',
            combat='not_selected: 本章调查/医药检样不作为战斗证据',
            security_packet_replay='not_verified: 未注入 C2S 或旧 nonce',
            termination='not_started')
        self.save()

    def action(self, action, **detail):
        row = {'time': round(time.monotonic(), 4), 'session': self.sessions,
               'player': self.username if self.s else None, 'action': action, **detail}
        self.action_stream.write(json.dumps(row, ensure_ascii=False)+'\n')
        self.action_stream.flush()

    @property
    def username(self):
        return self.actor.username if self.actor else self.s.username

    @property
    def game(self):
        return self.actor.game if self.actor else self.s.game

    def rc(self, command):
        # Even camera alignment during the uninterrupted chapter uses mouse input.
        if self.continuous and re.search(r'(?:^|\s)(?:tp|teleport)\s', command):
            raise AssertionError('Teleport prohibited during continuous chapter: '+command)
        if ('myvillage story ' in command and not command.startswith('myvillage story status ')):
            raise AssertionError('Story RCON is read-only: '+command)
        return Evidence.rc(self, command)

    def player_pos(self):
        # Like QianzhenEvidence.player_pos, keep high-rate RCON samples in the
        # append-only stream instead of rewriting the entire report per sample.
        reply = self.s.run(f'data get entity {self.username} Pos', warn=False)[0]
        point = finite_vector(read_data(reply))
        self.position_stream.write(json.dumps({'time': time.monotonic(), 'player': self.username,
                                               'position': point, 'reply': reply})+'\n')
        self.position_stream.flush()
        return point

    def input_keys(self, want):
        want = set(want)
        for key in sorted(self.held-want):
            self.game.xdo('keyup', key)
            self.action('keyup', key=key)
        for key in sorted(want-self.held):
            self.game.xdo('keydown', key)
            self.action('keydown', key=key)
        self.held = want

    def start(self, reuse=False):
        request = runtime_request(ROOT, self.request_path, self.provider, self.candidate)
        pack_config = self.candidate.prepare_pack(self.out)
        bindings = {**KEYS, **self.contract['bindings'], 'mouseSensitivity': '0.5',
                    'rawMouseInput': 'false', 'invertYMouse': 'false'}
        cs.merge_options(cs.CLIENT_DIR/'options.txt', bindings)
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse,
            lock=str(procs.default_lock_path(ROOT)), language=self.language,
            framebuffer_width=1280, framebuffer_height=720, **pack_config))
        self.sessions += 1
        self.report['termination'] = 'running'
        self.s = cs.Session.attach(ui_check=True)
        self.primary_username = self.s.username
        window = self.game.xdo('search', '--name', 'Minecraft').split()[0]
        self.game.xdo('windowsize', '--sync', window, '1280', '720')
        self.game.xdo('windowmove', '--sync', window, '0', '0')
        self.game.size = (1280, 720)
        time.sleep(.8)
        self.report['phases'].append({'phase': 'restart' if reuse else 'initial_start',
            'capture_timings': state.get('timings', {})})
        self.report['termination'] = 'running'
        actual = dict(line.split(':', 1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines()
                      if ':' in line)
        self.check('explicit_bindings_'+str(self.sessions),
                   all(actual.get(key) == value for key, value in bindings.items()), bindings)
        if self.sessions == 1:
            self.report.update(source_commit=request['commit'], candidate=self.candidate.identity,
                               request_sha256=hashlib.sha256(Path(self.request_path).read_bytes()).hexdigest())
            self.report['mod_version'] = next(line.split('=',1)[1] for line in
                (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
            self.report['jar_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                         for p in (ROOT/'build/libs').glob('*.jar')}
            self.report['graphics'] = subprocess.check_output(['glxinfo', '-B'], env=self.game.env,
                                                               text=True, timeout=20)
        self.verify_candidate_pack()
        self.record_start()

    def record_start(self):
        self.video_index += 1
        video = self.out/f'chapter-{self.video_index:02d}.mp4'
        self.recorder_log = (self.out/f'chapter-{self.video_index:02d}-ffmpeg.log').open('w')
        self.recorder = subprocess.Popen(ffmpeg_record_argv(self.game.display, video, 7200,
            fps=12, size=self.game.size), stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
            stderr=self.recorder_log)
        self.report.setdefault('videos', []).append(video.name)

    def record_stop(self):
        if self.recorder is not None:
            result = stop_recording(self.recorder)
            self.recorder = None
            self.recorder_log.close()
            if result:
                raise RuntimeError('Chapter recording failed: '+str(result))

    def stop(self):
        try:
            self.input_keys([])
            self.record_stop()
        finally:
            Evidence.stop(self)
            state = cs.read_state()
            alive = state and (cs.supervisor_alive(state) or any(
                procs.is_alive(info['pid'], info.get('start')) for info in state.get('procs', {}).values()))
            self.report['termination'] = 'unconfirmed' if alive else 'confirmed'
            self.save()
            if alive:
                raise RuntimeError('Capture process termination is not confirmed')

    def capture(self, name):
        path = self.out/(name+'.png')
        # Game.shot deliberately refuses GUI screens. Capture the real window
        # directly here so dialogue/journal frames retain the visible menu.
        time.sleep(.7)
        window = self.game.xdo('search', '--name', 'Minecraft').split()[0]
        subprocess.run(['import', '-silent', '-window', window, str(path)],
                       env=self.game.env, check=True, timeout=30)
        self.report['screenshots'].append({'path': path.name, 'player': self.username,
                                           'size': self.game.size})
        self.save()

    def status(self, label, username=None):
        username = username or self.username
        value = status_object(self.rc(f'myvillage story status {username}'))
        self.report['readbacks'].append({'label': label, 'player': username, 'state': value})
        self.save()
        return value

    @staticmethod
    def personal(state):
        keys = ('player', 'chapter', 'stage', 'revision', 'accepted', 'completed',
                'site', 'dimension', 'evidence', 'receipts', 'reward')
        return {key: state[key] for key in keys}

    @staticmethod
    def actor_row(state, role):
        return state['npc'][role]

    @staticmethod
    def actor_position(row):
        return finite_vector([row['position'][axis] for axis in ('x', 'y', 'z')])

    @staticmethod
    def returned(state):
        return state['shared']['returned'] is True

    def deployed(self, state):
        return all(self.actor_row(state, role)['personId'] >= 0
                   and self.actor_row(state, role)['position'] is not None for role in self.chapter['roles'])

    def inventory(self, username=None):
        return read_data(self.rc(f'data get entity {username or self.username} Inventory'))

    def look(self, yaw, pitch):
        """Bounded physical mouse feedback, never RCON rotation/TP."""
        self.input_keys([])
        self.game.require_ingame('physical look')
        for _ in range(12):
            rotation = finite_vector(read_data(self.rc(f'data get entity {self.username} Rotation')), 2)
            errors = ((yaw-rotation[0]+180) % 360-180, pitch-rotation[1])
            if max(map(abs, errors)) < .20:
                return
            dx, dy = [round(max(-1200, min(1200, error/.15))) for error in errors]
            self.game.xdo('mousemove_relative', '--', str(dx), str(dy))
            self.action('mouse_look', dx=dx, dy=dy, before=rotation, target=[yaw, pitch])
            time.sleep(.18)
        raise AssertionError(f'Physical camera failed to converge to {yaw}, {pitch}')

    def close_screen(self):
        if self.game.pointer_grabbed() is False:
            self.game.xdo('key', 'Escape')
            self.action('key', key='Escape', purpose='close_screen')
            self.poll(self.game.pointer_grabbed, bool, 8, 'close story screen')

    def fixture_teleport(self, point, reason):
        self.action('fixture_start', reason=reason, target=point)
        self.rc(teleport_command(self.username, point))
        self.poll(self.player_pos, lambda p: math.dist(p, point) < .25, 10, 'fixture start')

    def walk(self, key, state):
        self.close_screen()
        target = self.approach(key, state)
        actual = self.player_pos()
        current = tuple(math.floor(v) for v in actual)
        blocked = self.npc_cells(state)
        path = cardinal_path(self.graph, current, target, blocked)
        self.look(0, 0)
        legs = []
        # Open each original door on the actual route, before colliding with it.
        segments = route_ends(path, self.blocks)
        last_index = 0
        for endpoint in segments:
            index = path.index(endpoint, last_index+1)
            for node in path[last_index+1:index+1]:
                self.open_near_door(node)
            legs.append(self.travel_to(walk_point(endpoint, self.blocks)))
            last_index = index
        self.report['routes'].append({'player': self.username, 'target': key,
            'path': path, 'legs': legs, 'teleports': 0})
        self.action('route_complete', target=key, actual=self.player_pos())
        self.save()

    def open_near_door(self, node):
        state = self.blocks.get(tuple(node), '')
        if '_door[' not in state:
            return
        name = state.split('[')[0]
        # Only interact from a reached path cell within real vanilla reach.
        point = self.player_pos()
        target = [node[0]+.5, node[1]+.65, node[2]+.5]
        if math.dist([point[0], point[1]+1.62, point[2]], target) > 4:
            raise AssertionError('Route compression skipped necessary door approach: '+str(node))
        command = 'execute if block '+' '.join(map(str, node))+' '+name+'[open=true]'
        if 'passed' not in self.rc(command):
            self.look(*aim(point, target))
            self.game.xdo('click', '3')
            self.action('right_mouse', purpose='door', target=node)
            self.poll(lambda: self.rc(command), lambda r: 'passed' in r, 8, 'mapped door opening')
            self.look(0, 0)

    def use_target(self, key, state):
        self.walk(key, state)
        target = self.targets[key]
        command = 'execute if block '+' '.join(map(str, target['pos']))+' '+target['block']
        self.check('real_target_'+key, 'passed' in self.rc(command), target)
        self.look(*aim(self.player_pos(), [v+.5 for v in target['pos']]))
        self.game.require_ingame('physical story investigation')
        self.game.xdo('click', '3')
        self.action('right_mouse', purpose='investigate', key=key, target=target['pos'])
        time.sleep(.35)

    def open_actor(self, role, state):
        self.walk(role, state)
        person = self.actor_row(state, role)['personId']
        selector = f'@e[type=myvillage:cultivator,nbt={{WorldSimPerson:{person}}}]'
        def live_body():
            reply = self.rc(f'execute as {selector} run data get entity @s Pos')
            return reply, [read_data(line) for line in reply.splitlines() if 'data: ' in line]
        reply, rows = self.poll(live_body, lambda pair: len(pair[1]) == 1, 15,
                                'nearby unique live body '+role)
        self.check('one_real_body_'+role, len(rows) == 1, {'personId': person, 'reply': reply})
        live = self.actor_row(self.status('live-body-'+role), role)
        self.check('active_body_lease_'+role, live['personId'] == person and live['epoch'] > 0, live)
        body = finite_vector(rows[0])
        intended = self.actor_position(self.actor_row(state, role))
        self.check('live_body_at_story_station_'+role,
                   math.dist(body, [intended[0]+.5, intended[1], intended[2]+.5]) < .75,
                   {'actual':body, 'story_station':intended})
        self.look(*aim(self.player_pos(), [body[0], body[1]+1.3, body[2]]))
        offset = self.game.client_log.stat().st_size
        self.game.require_ingame('physical NPC right click')
        self.game.xdo('click', '3')
        self.action('right_mouse', purpose='NPC_dialogue', role=role, personId=person, body=body)
        return offset

    def log_tail(self, offset):
        with self.game.client_log.open('rb') as stream:
            stream.seek(offset)
            return stream.read().decode('utf-8', errors='replace')

    def expect_client_view(self, offset, state, label):
        rows = self.poll(lambda: client_views(self.log_tail(offset)), bool, 12,
                         'fresh applied client story snapshot '+label)
        expected = {key: state[key] for key in ('revision', 'accepted', 'completed', 'chapter')}
        expected['available'] = True
        self.check('client_snapshot_'+label, rows[-1] == expected, {'expected':expected, 'actual':rows[-1]})

    def journal_capture(self, label):
        self.close_screen()
        self.game.key('o')
        self.action('key', key='o', purpose='lifecycle_journal_'+label)
        self.poll(self.game.pointer_grabbed, lambda value: value is False, 8, 'lifecycle journal opened')
        self.capture(label+'-journal')
        self.close_screen()

    def choose(self, role, option, state):
        offset = self.open_actor(role, state)
        opening = self.contract['opening']
        if state['accepted'] is False and role == opening['role'] and option in opening['after_learn']:
            buttons = self.poll(lambda: dialogue_buttons(self.log_tail(offset), self.game.size),
                                bool, 12, 'actual opening page')
            self.check('opening_does_not_offer_acceptance_'+self.username,
                       {b['option'] for b in buttons} == set(opening['options']), buttons)
            self.check('opening_did_not_accept_'+self.username,
                       self.personal(state) == self.personal(self.status('opening-unaccepted')), state)
            intro = [name for name in opening['options'] if name != 'close']
            if len(intro) != 1:
                raise ValueError('Handoff opening needs one non-close physical introduction')
            button = next(b for b in buttons if b['option'] == intro[0])
            offset = self.game.client_log.stat().st_size
            self.click_dialogue(button, role, 'hear_introduction')
            self.check('introduction_did_not_accept_'+self.username,
                       self.personal(state) == self.personal(self.status('introduction-unaccepted')), state)
        buttons = self.poll(lambda: dialogue_buttons(self.log_tail(offset), self.game.size, option),
                            bool, 12, 'fresh physical dialogue option '+option)
        if len(buttons) != 1:
            raise AssertionError('Ambiguous physical option: '+repr(buttons))
        button = buttons[0]
        self.last_button = button
        self.capture(f'dialogue-{self.username}-{role}-{option}-{len(self.report["readbacks"])}')
        self.click_dialogue(button, role, 'dialogue_option')
        self.close_screen()

    def click_dialogue(self, button, role, purpose):
        self.game.xdo('mousemove', '--sync', str(button['x']+button['w']//2),
                      str(button['y']+button['h']//2))
        self.game.xdo('click', '1')
        self.action('left_mouse', purpose=purpose, role=role, **button)
        time.sleep(.35)

    def dropped_rewards(self):
        return {reward['item']: self.rc('execute as @e[type=minecraft:item,nbt={Item:{id:"'
                + reward['item'] + '"}}] run data get entity @s Item') for reward in self.chapter['rewards']}

    @contextmanager
    def as_guest(self, guest):
        self.input_keys([])
        self.record_stop()
        self.actor = guest
        self.record_start()
        try:
            yield
        finally:
            self.input_keys([])
            self.record_stop()
            self.actor = None
            self.record_start()

    def package_world(self):
        world = cs.SERVER_DIR/cs.LEVEL_NAME
        if world.exists():
            with zipfile.ZipFile(self.out/'qianzhen_story_world.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(world.rglob('*')):
                    # playerdata is essential to a meaningful restart/reward handoff.
                    if path.is_file() and not path.is_symlink() and path.name != 'session.lock':
                        archive.write(path, 'Qianzhen Story/'+str(path.relative_to(world)))

    def load_scene(self):
        # Consume the generator's actual assembly API, including production props
        # and roads; never reconstruct the obsolete six-plot acceptance fixture.
        from tools.qianzhen.story_assets import assemble, graph_for, nbt
        if self.candidate.folder:
            self.candidate.validate()
            layout, authored = self.candidate.layout, self.candidate.blueprints
        else:
            layout, authored, _ = assemble()
        self.layout = layout
        add = lambda p: tuple(p[i]+self.origin[i] for i in range(3))
        self.blocks = {}
        for site in layout['sites']:
            _, _, blocks, _ = nbt.expand_structure(authored[site['id']])
            for pos, block in blocks.items():
                self.blocks[add([pos[i]+site['origin'][i] for i in range(3)])] = block
        for row in layout['roads']:
            self.blocks[add(row['pos'])] = row['state']
        self.graph = opened_door_graph(graph_for(self.blocks), self.blocks)
        self.positions = {key: {field: list(add(value[field])) for field in ('pos', 'approach')}
                          for key, value in layout['positions'].items()}
        self.targets = {row['id']: {**row, 'pos': list(add(row['pos'])),
                                    'approach': list(add(row['approach']))} for row in layout['targets']}
        self.report['scene'] = {'version': layout['version'], 'inputs': layout['inputs'],
                                'origin': self.origin, 'site_origins': layout['sites']}

    def setup(self):
        user = self.username
        for command in (*scene.world_rules(), f'op {user}', f'clear {user}',
                        f'gamemode survival {user}', 'gamerule doFireTick false',
                        'gamerule randomTickSpeed 0', 'gamerule keepInventory true',
                        'gamerule doImmediateRespawn true', 'difficulty peaceful',
                        f'effect give {user} minecraft:saturation infinite 0 true'):
            self.rc(command)
        low, high = self.layout['bounds']['min'], self.layout['bounds']['max']
        x1, z1 = int(low[0]+self.origin[0]), int(low[2]+self.origin[2])
        x2, z2 = int(high[0]+self.origin[0]), int(high[2]+self.origin[2])
        self.fixture_teleport([x1-8.5, self.origin[1]+1, z1-8.5],
                              'move operator outside protected deployment volume before any building placement')
        chunks = (x2//16-x1//16+1)*(z2//16-z1//16+1)
        self.check('bounded_production_chunk_load', chunks <= 256, {'chunks': chunks})
        self.rc(f'forceload add {x1} {z1} {x2} {z2}')
        self.poll(lambda: self.rc(f'execute if loaded {x2} {self.origin[1]} {z2}'),
                  lambda reply: 'passed' in reply, 30, 'production plot loaded')
        self.action('production_deploy', origin=self.origin)
        self.rc('myvillage qianzhen deploy '+' '.join(str(int(v)) for v in self.origin))
        state = self.poll(lambda: self.status('deploy_wait'), self.deployed, 90, 'production story deployment')
        self.initial_people = {role: self.actor_row(state, role)['personId']
                               for role in self.chapter['roles']}
        self.check('four_expected_unique_roles', len(set(self.initial_people.values())) == 4
                   and all(pid >= 0 for pid in self.initial_people.values()), self.initial_people)
        # Initial administrator start only; every subsequent primary chapter leg
        # remains physical input, including across the two save/restart boundaries.
        start = self.positions['spawn']['pos']
        self.rc('setworldspawn '+' '.join(str(int(v)) for v in start)+' 0')
        self.rc('defaultgamemode survival')
        self.fixture_teleport([start[0]+.5, start[1], start[2]+.5], 'initial chapter entry')
        self.continuous = True
        self.verify_candidate_deployment('initial_deploy')
        self.complete('production_deploy')

    def verify_candidate_deployment(self, label):
        if self.candidate.folder:
            self.rc('save-all flush')
            result = self.candidate.verify_deployment(cs.SERVER_DIR/cs.LEVEL_NAME, self.origin)
            self.check('candidate_scene_version_'+label, True, result)

    def approach(self, key, state):
        if key == 'zhou_he' and self.returned(state):
            key = 'zhou_he_home'
        return tuple(self.positions[key]['approach'])

    def npc_cells(self, state):
        # Reserve intended live NPC feet. The scene generator proves approaches
        # still connect with both mutually exclusive Zhou He locations occupied.
        keys = ['guide', 'pharmacist', 'warden',
                'zhou_he_home' if self.returned(state) else 'zhou_he']
        return [tuple(self.positions[key]['pos']) for key in keys]

    def expect_step(self, row, state):
        progress = self.personal(state)
        require_subset(progress, {'stage': row['after']}, row['action']['id'])
        self.check('evidence_'+row['action']['id'],
                   set(progress['evidence']) == set(row['evidence']), progress)

    def restart_checkpoint(self, label):
        self.close_screen()
        before = self.status(label+'-before')
        position, inventory = self.player_pos(), self.inventory()
        self.rc('save-all flush')
        (self.out/(label+'-state-before.json')).write_text(json.dumps(before, ensure_ascii=False, indent=2)+'\n')
        self.stop()
        # A real stopped server and its stopped player save, not just /reload.
        self.package_world()
        shutil.copy2(self.out/'qianzhen_story_world.zip', self.out/(label+'-world.zip'))
        self.start(reuse=True)
        after = self.poll(lambda: self.status(label+'-after'), self.deployed, 60, label+' ledger bindings restored')
        self.verify_candidate_deployment(label)
        # Each real client restart creates a fresh logs/latest.log.
        self.expect_client_view(0, after, label)
        self.check(label+'_saved_progress_exact', self.personal(before) == self.personal(after),
                   {'before': before, 'after': after})
        self.check(label+'_saved_inventory_exact', inventory == self.inventory(), inventory)
        self.check(label+'_shared_facts_exact', before['shared'] == after['shared'],
                   {'before':before['shared'], 'after':after['shared']})
        self.check(label+'_saved_position', math.dist(position, self.player_pos()) < .35,
                   {'before': position, 'after': self.player_pos()})
        self.assert_shared_identity(after, label)
        if self.returned(before):
            self.check(label+'_zhou_he_stays_home', self.returned(after), after)
        self.report.setdefault('lifecycle', []).append({'kind':'logout_login_with_real_server_restart',
                                                        'checkpoint':label, 'result':'pass'})

    def assert_shared_identity(self, state, label):
        actual = {role: self.actor_row(state, role)['personId'] for role in self.chapter['roles']}
        self.check(label+'_same_person_ids', actual == self.initial_people, actual)
        if self.returned(state):
            row = self.actor_row(state, 'zhou_he')
            expected = self.positions['zhou_he_home']['pos']
            position = self.actor_position(row)
            self.check(label+'_same_zhou_he_home',
                       all(abs(position[i]-expected[i]) < 1 for i in range(3)), row)

    def execute_step(self, row):
        action = row['action']
        before = self.status(action['id']+'-before')
        require_subset(self.personal(before), {'stage': row['before']})
        self.action('chapter_action_begin', id=action['id'], kind=action['kind'], stage=row['before'])
        if action['kind'] == 'investigate':
            self.use_target(action['target'], before)
        else:
            self.choose(action['role'], action['id'], before)
        after = self.poll(lambda: self.status(action['id']+'-after'),
                          lambda state: self.personal(state)['stage'] == row['after']
                          and set(self.personal(state)['evidence']) == set(row['evidence']),
                          12, 'physical action '+action['id'])
        self.expect_step(row, after)
        self.check('one_revision_'+action['id'],
                   self.personal(after)['revision'] == self.personal(before)['revision']+1,
                   {'before': self.personal(before), 'after': self.personal(after)})
        self.action('chapter_action_complete', id=action['id'], stage=row['after'])
        if action['effect'] == 'return_zhou_he':
            self.check('shared_rescue_recorded', self.returned(after), after)
            self.assert_shared_identity(after, 'rescue')
        if action['effect'] == 'regulate':
            self.check('shared_array_regulated_'+self.username, after['shared']['regulated'] is True, after)
        self.capture('after-'+self.username+'-'+action['id'])
        return after

    def reward_step(self, row, full_bag=False):
        user, rewards = self.username, self.chapter['rewards']
        if full_bag:
            self.close_screen()
            filler = 'minecraft:cobblestone'
            if filler in {r['item'] for r in rewards}:
                raise ValueError('Full inventory fixture filler collides with actual rewards')
            # Fixture only: actual claim failure and success still use the UI.
            for i in range(36):
                slot = 'hotbar.'+str(i) if i < 9 else 'inventory.'+str(i-9)
                self.rc(f'item replace entity {user} {slot} with {filler} 64')
            before, inv = self.status('full-bag-before'), self.inventory()
            drops = self.dropped_rewards()
            self.check('full_bag_has_36_full_stacks', len(inv) == 36
                       and all(s.get('count', s.get('Count')) == 64 for s in inv), inv)
            self.choose(row['action']['role'], row['action']['id'], before)
            after = self.status('full-bag-rejected')
            self.check('full_bag_keeps_claimable_state', self.personal(after) == self.personal(before),
                       {'before':before, 'after':after})
            self.check('full_bag_no_loss_or_reward', inv == self.inventory(), inv)
            self.check('full_bag_never_drops_rewards', drops == self.dropped_rewards(), drops)
            self.capture('full-bag-claim-rejected')
            # Free exactly enough distinct slots for the actual reward types.
            for i in range(len(rewards)):
                self.rc(f'item replace entity {user} inventory.{i} with minecraft:air')
        before = reward_counts(self.inventory(), rewards)
        after = self.execute_step(row)
        counts = reward_counts(self.inventory(), rewards)
        self.check('exact_reward_delta_'+user, all(counts[r['item']]-before[r['item']] == r['count']
                   for r in rewards), {'before':before, 'after':counts, 'rewards':rewards})
        self.check('one_personal_receipt_'+user, len(self.personal(after)['receipts']) == 1, after)
        self.repeat_reward(row, after)
        return after

    def repeat_reward(self, row, before):
        saved_inventory = self.inventory()
        previous = dict(self.last_button)
        offset = self.open_actor(row['action']['role'], before)
        # The normal UI removes an ineligible option. Attempt the same physical
        # button location anyway; record that this is not a forged nonce probe.
        self.poll(lambda: dialogue_buttons(self.log_tail(offset), self.game.size), bool, 12,
                  'reopen reward dialogue')
        available = dialogue_buttons(self.log_tail(offset), self.game.size, row['action']['id'])
        button = available[0] if available else previous
        self.game.xdo('mousemove', '--sync', str(button['x']+button['w']//2), str(button['y']+button['h']//2))
        self.game.xdo('click', '1')
        self.action('left_mouse', purpose='repeat_reward_physical_attempt',
                    option_present=bool(available), **button)
        time.sleep(.7)
        self.close_screen()
        after = self.status('repeat-reward-rejected')
        self.check('repeat_reward_no_state_or_item_delta_'+self.username,
                   self.personal(after) == self.personal(before) and saved_inventory == self.inventory(),
                   {'before':before, 'after':after, 'option_present':bool(available),
                    'scope':'normal physical UI; packet replay not verified'})

    def primary_chapter(self, prefix_length=0):
        for row in self.contract['primary'][prefix_length:]:
            action = row['action']
            if action['kind'] == 'claim':
                self.reward_step(row, full_bag=True)
                self.complete('full_inventory_reward')
                self.restart_checkpoint('reward-restart')
                self.complete('reward_restart')
                # Repeat after reload, not just immediately after the UI click.
                self.repeat_reward(row, self.status('reward-restart-repeat-before'))
            else:
                self.execute_step(row)
            if action['effect'] == 'return_zhou_he':
                self.restart_checkpoint('rescue-restart')
                self.complete('rescue_restart')
            if action['effect'] == 'regulate':
                # Capture the active objective and distance, plus a populated
                # journal, before rewards and the terminal no-target snapshot.
                self.ui_evidence()
        final = self.status('first-chapter-complete')
        self.check('first_chapter_terminal', final['completed'] is True and final['reward'] == 'claimed', final)
        self.continuous = False
        if prefix_length:
            self.report['continuous_chapter_provenance'] = {
                'source_run': story_resume.BASE_RUN, 'source_commit': story_resume.BASE_COMMIT,
                'prefix_actions': prefix_length, 'current_source_commit': self.report['source_commit'],
                'compatibility': '生产源哈希一致；仅 StoryClient 默认 P→Y 与中文键位说明归一兼容',
                'movement': '原世界/玩家/位置连续恢复；前缀与剩余动作均实际键鼠，无中途 TP',
                'prior_status': 'fail', 'prior_report': 'prior-run/evidence.json'}
        self.complete('continuous_first_chapter')

    def second_player(self):
        owner = self.status('owner-before-second-client')
        inventory = self.inventory()
        # Keep the stationary observer away from guest routes/approaches.
        spawn = tuple(self.positions['spawn']['pos'])
        reserved = set(self.npc_cells(owner)) | {tuple(p['approach']) for p in self.positions.values()}
        parking = next(p for p in sorted(self.graph)
                       if p not in reserved and p[1] == spawn[1]
                       and 2 <= math.dist(p, spawn) <= 5)
        self.fixture_teleport([parking[0]+.5, parking[1], parking[2]+.5], 'auxiliary observer parking after complete chapter')
        guest = SecondClient(self)
        cs.merge_options(guest.directory/'options.txt', {**KEYS, **self.contract['bindings'],
            'mouseSensitivity':'0.5', 'rawMouseInput':'false', 'invertYMouse':'false'})
        with guest:
            with self.as_guest(guest):
                for command in (f'gamemode survival {self.username}', f'clear {self.username}',
                                f'effect give {self.username} minecraft:saturation infinite 0 true'):
                    self.rc(command)
                self.fixture_teleport([spawn[0]+.5, spawn[1], spawn[2]+.5],
                                      'late second player initial entry; separate personal route')
                initial = self.status('late-player-unaccepted')
                self.check('late_player_has_own_new_state', initial['stage'] == 'new'
                           and not initial['evidence'] and not initial['receipts'], initial)
                self.check('late_player_sees_shared_world_facts', initial['shared'] == owner['shared'], initial)
                self.assert_shared_identity(initial, 'late_player')
                self.continuous = True
                try:
                    for row in self.contract['secondary']:
                        if row['action']['kind'] == 'claim':
                            self.reward_step(row)
                        else:
                            self.execute_step(row)
                        if row['action']['effect'] == 'return_zhou_he':
                            self.check('later_rescue_confirms_same_person_at_home',
                                       self.status('late-rescue')['npc']['zhou_he']['personId'] == self.initial_people['zhou_he'],
                                       self.status('late-rescue-confirmed'))
                finally:
                    self.continuous = False
                final = self.status('late-player-finished')
                self.check('late_player_own_completion_receipt', final['completed'] is True
                           and len(final['receipts']) == 1 and final['player'] != owner['player'], final)
                self.check('other_player_did_not_advance_owner',
                           self.personal(owner) == self.personal(self.status('owner-after-guest', self.primary_username))
                           and inventory == self.inventory(self.primary_username), owner)
                self.report['multiplayer'] = 'bounded_two_actual_clients_independent_branches_and_rewards_pass'
        self.complete('late_second_player_other_branch')

    def lifecycle_probes(self):
        self.close_screen()
        before, inventory, position = self.status('lifecycle-before'), self.inventory(), self.player_pos()
        user = self.username
        # Fixture death only. No fixed HP assignment or softened health assertion.
        self.action('fixture_lifecycle_death', explanation='server kill; native auto-respawn; attachment readback')
        offset = self.game.client_log.stat().st_size
        self.rc(f'kill {user}')
        self.poll(lambda: self.number(f'data get entity {user} Health'), lambda value: value > 0,
                  20, 'normal living respawn')
        self.poll(self.game.pointer_grabbed, bool, 20, 'respawn client')
        after = self.status('after-death')
        self.check('death_preserves_progress_and_reward', self.personal(before) == self.personal(after)
                   and inventory == self.inventory(), {'before':before, 'after':after})
        self.expect_client_view(offset, after, 'after-death')
        self.journal_capture('after-death')
        # The End is available in the fixture even though capture disables Nether
        # portals. This probes lifecycle state, not natural player portal travel.
        self.action('fixture_dimension_roundtrip', dimension='minecraft:the_end',
                    scope='administrator fixture; not natural portal traversal')
        with self.end_platform():
            offset = self.game.client_log.stat().st_size
            self.rc(f'execute in minecraft:the_end run tp {user} 0.5 81.0 0.5')
            self.poll(lambda: read_data(self.rc(f'data get entity {user} Dimension')),
                      lambda dim: dim == 'minecraft:the_end', 15, 'actual dimension change')
            away = self.status('other-dimension')
            self.check('dimension_preserves_personal_story', self.personal(before) == self.personal(away), away)
            self.expect_client_view(offset, away, 'other-dimension')
            self.journal_capture('other-dimension')
            offset = self.game.client_log.stat().st_size
            self.rc('execute in minecraft:overworld run '+teleport_command(user, position))
            self.poll(lambda: read_data(self.rc(f'data get entity {user} Dimension')),
                      lambda dim: dim == 'minecraft:overworld', 15, 'actual dimension return')
            after = self.status('after-dimension-return')
            self.expect_client_view(offset, after, 'dimension-return')
            self.journal_capture('dimension-return')
        self.check('dimension_roundtrip_no_reward_duplication',
                   self.personal(before) == self.personal(after) and inventory == self.inventory(), after)
        self.report.setdefault('lifecycle', []).append({'kind':'death_dimension', 'result':'pass',
            'relog':'actual logout/login covered by both real server restarts; standalone client-only relog not_verified'})
        self.complete('death_relog_dimension')

    @contextmanager
    def end_platform(self):
        """Load only four fixture chunks; verify the floor before any dimension TP."""
        prefix = 'execute in minecraft:the_end '
        self.rc(prefix+'run forceload add -2 -2 2 2')
        try:
            for x, z in ((-2,-2),(-2,2),(2,-2),(2,2)):
                self.poll(lambda: self.rc(prefix+f'if loaded {x} 80 {z}'),
                          lambda reply: 'passed' in reply, 30, 'End fixture chunk loaded')
            self.rc(prefix+'run fill -2 80 -2 2 80 2 minecraft:stone')
            floor = ' '.join(f'if block {x} 80 {z} minecraft:stone' for x in range(-2,3) for z in range(-2,3))
            self.check('end_fixture_floor_verified', 'passed' in self.rc(prefix+floor), '25 stone blocks; four loaded chunks')
            yield
        finally:
            self.rc(prefix+'run forceload remove -2 -2 2 2')

    def ui_evidence(self):
        self.close_screen()
        before = self.status('ui-before')
        window = self.game.xdo('search', '--name', 'Minecraft').split()[0]
        # Auto GUI scale stays at 3 for both: 427x240 then 320x240 GUI units.
        # 960x540 would drop to scale 2 and make the logical viewport wider.
        for label, size in (('normal', (1280,720)), ('narrow', (960,720))):
            self.game.xdo('windowsize', '--sync', window, *map(str, size))
            self.game.xdo('windowmove', '--sync', window, '0', '0')
            self.game.size = size
            time.sleep(1)
            self.capture(label+'-tracker-compact')
            for mode in ('EXPANDED', 'HIDDEN', 'COMPACT'):
                offset = self.game.client_log.stat().st_size
                self.game.key('y')
                self.action('key', key='y', purpose='tracker_'+mode)
                self.poll(lambda: self.log_tail(offset), lambda text: 'QIANZHEN_STORY_TRACKER mode='+mode in text,
                          8, label+' tracker '+mode)
                self.capture(label+'-tracker-'+mode.lower())
            self.game.key('o')
            self.action('key', key='o', purpose='open_journal')
            self.poll(self.game.pointer_grabbed, lambda value: value is False, 8, 'O journal opened')
            self.capture(label+'-journal-top')
            self.game.xdo('click', '--repeat', '12', '--delay', '80', '5')
            self.action('mouse_wheel', direction='down', count=12, purpose='journal_scroll')
            self.capture(label+'-journal-scroll')
            self.close_screen()
        self.game.xdo('windowsize', '--sync', window, '1280', '720')
        self.game.size = (1280,720)
        self.check('ui_read_only', self.personal(before) == self.personal(self.status('ui-after')), before)
        self.report['ui_visual_review'] = 'required: 正常/窄 GUI、头像、换行、滚动与裁切需看截图录像'
        self.complete('normal_narrow_ui')

    def ui_smoke(self, label):
        """Fail early using actual Y/O input, retaining screenshots and state."""
        self.close_screen()
        before = self.status(label+'-ui-smoke-before')
        for mode in ('EXPANDED', 'HIDDEN', 'COMPACT'):
            offset = self.game.client_log.stat().st_size
            self.game.key('y')
            self.action('key', key='y', purpose=label+'_smoke_tracker_'+mode)
            self.poll(lambda: self.log_tail(offset),
                      lambda text: 'QIANZHEN_STORY_TRACKER mode='+mode in text,
                      8, label+' early Y tracker '+mode)
            if self.game.pointer_grabbed() is False:
                raise AssertionError('Y opened an unexpected menu during '+label)
            self.capture(label+'-smoke-tracker-'+mode.lower())
        self.game.key('o')
        self.action('key', key='o', purpose=label+'_smoke_journal')
        self.poll(self.game.pointer_grabbed, lambda value: value is False, 8, label+' early O journal')
        self.capture(label+'-smoke-journal')
        # The production journal handles O again while open. A generic menu
        # merely releasing the pointer must not satisfy this smoke check.
        self.game.xdo('key', 'o')
        self.action('key', key='o', purpose=label+'_smoke_close_journal')
        self.poll(self.game.pointer_grabbed, bool, 8, label+' O closes journal')
        after = self.status(label+'-ui-smoke-after')
        unchanged = (self.personal(before) == self.personal(after)
                     and before['shared'] == after['shared'] and before['npc'] == after['npc'])
        self.check(label+'_early_y_o_smoke_read_only', unchanged, {'before': before, 'after': after})
        if not unchanged:
            raise AssertionError('Y/O smoke changed authoritative story state')
        self.report.setdefault('ui_smoke', []).append({'label': label, 'result': 'pass',
            'input': 'actual Y EXPANDED/HIDDEN/COMPACT then O journal open/close', 'final_mode': 'COMPACT',
            'screenshots': [label+'-smoke-tracker-'+m+'.png' for m in ('expanded','hidden','compact')]
                           + [label+'-smoke-journal.png']})
        self.save()

    def preflight(self):
        source = self.handoff_path
        if not source.exists() and source == HANDOFF:
            source = HANDOFF_SNAPSHOT
        if not source.exists():
            raise FileNotFoundError('TODO integration: runtime-handoff.json is not available; no game may start')
        handoff = json.loads(source.read_text())
        self.chapter = json.loads(CHAPTER.read_text())
        chapter_hash = hashlib.sha256(CHAPTER.read_bytes()).hexdigest()
        if 'chapter_sha256' in handoff:
            if handoff['chapter_sha256'] != chapter_hash:
                raise ValueError('Transported handoff is stale; refresh it from actual runtime-handoff.json')
        else:
            for field in ('actions', 'stages', 'rewards'):
                if handoff[field] != self.chapter[field]:
                    raise ValueError('Runtime handoff differs from actual chapter '+field)
        require_subset(handoff, {'schema':1, 'resource':'data/myvillage/story/qianzhen_chapter_1.json',
            'readback':{'command':'/myvillage story status <player>', 'prefix':'QIANZHEN_STORY_STATUS '},
            'opening':{'no_automatic_accept':True}})
        self.contract = {
            'primary':compile_route(self.chapter, handoff['routes']['medical']),
            'secondary':compile_route(self.chapter, handoff['routes']['road']),
            'opening':handoff['opening'],
            'bindings':{'key_key.myvillage.story_journal':'key.keyboard.o',
                        'key_key.myvillage.story_tracker':'key.keyboard.y'},
        }
        self.load_scene()
        # Prove both authored action sequences have continuous graph routes with
        # the exact NPC feet reserved, including the late shared home location.
        route_summary = {}
        for label, route in (('primary',self.contract['primary']),('secondary',self.contract['secondary'])):
            current = tuple(self.positions['spawn']['pos'])
            returned = label == 'secondary'
            legs = []
            for row in route:
                action = row['action']
                key = action['target'] if action['kind'] == 'investigate' else action['role']
                effective = 'zhou_he_home' if key == 'zhou_he' and returned else key
                target = tuple(self.positions[effective]['approach'])
                blocked = [tuple(self.positions[k]['pos']) for k in ('guide','pharmacist','warden',
                            'zhou_he_home' if returned else 'zhou_he')]
                path = cardinal_path(self.graph, current, target, blocked)
                legs.append({'action':action['id'], 'stage_before':row['before'], 'stage_after':row['after'],
                             'target':effective, 'cells':len(path), 'approach':target})
                current = target
                returned = returned or action['effect'] == 'return_zhou_he'
            route_summary[label] = legs
        self.report.update(status='ready_not_run', handoff_source=str(source.relative_to(ROOT))
            if source.is_relative_to(ROOT) else str(source),
            handoff_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
            chapter_sha256=chapter_hash, planned_routes=route_summary,
            api_dependencies=['生产 /myvillage qianzhen deploy <x> <y> <z>',
                'QIANZHEN_STORY_STATUS 稳定 JSON：个人状态/shared/npc',
                'QIANZHEN_DIALOGUE 实际物理按钮矩形 + nonce',
                'story_assets.assemble / graph_for；实际选中 source / scene / NBT',
                'combat_capture.Session；SecondClient；显式云端 provider / request'])
        shutil.copy2(source, self.out/'runtime-handoff-used.json')
        shutil.copy2(CHAPTER, self.out/'chapter-used.json')
        self.save()

    def resume(self, manifest_path):
        if self.candidate.folder:
            raise ValueError('Seeded candidate cannot reuse the fixed authored resume world')
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Story resume requires the Modal disposable /workspace/repo checkout')
        checkpoint = story_resume.prepare(ROOT, self.out, manifest_path,
                                          self.contract['primary'], self.origin)
        prior = checkpoint['prior']
        self.report.update(
            fixture='固定原运行独立世界续跑；保留玩家存档与失败证据',
            prior_run={'source_run': story_resume.BASE_RUN, 'source_commit': story_resume.BASE_COMMIT,
                'status': prior['status'], 'failures': prior['failures'],
                'termination': prior['termination'], 'report': 'prior-run/evidence.json',
                'manifest': checkpoint['manifest'], 'baseline_sha256': checkpoint['baseline_sha256'],
                'completed': prior['completed'], 'prefix_length': checkpoint['prefix_length']},
            previous_checks={'source_run': story_resume.BASE_RUN, 'checks': prior['checks']},
            previous_routes={'source_run': story_resume.BASE_RUN, 'routes': prior['routes']},
            case_provenance={case: {'source_run': story_resume.BASE_RUN,
                'source_commit': story_resume.BASE_COMMIT, 'report': 'prior-run/evidence.json'}
                for case in prior['completed']})
        # Display copies have distinct names, so later native frames cannot
        # overwrite the failed source's images. The full originals stay intact.
        for row in prior['screenshots']:
            source = self.out/'prior-run'/row['path']
            name = 'prior-'+row['path'].replace('/', '-')
            if (self.out/name).exists():
                raise ValueError('Prior screenshot display name collision: '+name)
            shutil.copy2(source, self.out/name)
            self.report['screenshots'].append({**row, 'path': name,
                'source_run': story_resume.BASE_RUN, 'source_path': 'prior-run/'+row['path']})
        shutil.copy2(self.out/'prior-run/fresh-entry-world.zip', self.out/'fresh-entry-world.zip')
        self.report['world_artifacts'] = [{'path': 'fresh-entry-world.zip',
            'source_run': story_resume.BASE_RUN, 'source_path': 'prior-run/fresh-entry-world.zip'}]
        self.save()
        story_resume.restore_world(ROOT, cs.SERVER_DIR, cs.LEVEL_NAME,
            self.out/'prior-run/qianzhen_story_world.zip', checkpoint['remaining_limit'])
        self.initial_people = checkpoint['people']
        # The no-TP guard is active before first launch/readback and remains
        # active through UI, remaining physical route and reward restart.
        self.continuous = True
        self.start(reuse=True)
        if self.username != 'CaptureDev':
            raise AssertionError('Resume requires the original CaptureDev player')
        state = self.poll(lambda: self.status('resume-restored'), self.deployed, 60, 'restored story bindings')
        expected = checkpoint['state']
        valid = (self.personal(state) == self.personal(expected)
                 and state['shared'] == expected['shared']
                 and {role: row['personId'] for role, row in state['npc'].items()} == self.initial_people)
        self.check('resume_exact_personal_shared_identities', valid, {'expected': expected, 'actual': state})
        if not valid:
            raise AssertionError('Restored story state/identity differs from original ui-before')
        position = self.player_pos()
        same_position = math.dist(position, checkpoint['position']) <= .35
        self.check('resume_exact_last_movement_position', same_position,
                   {'source_run': story_resume.BASE_RUN, 'before': checkpoint['position'], 'after': position})
        if not same_position:
            raise AssertionError('Restored primary moved away from last original sample')
        self.assert_shared_identity(state, 'resume')
        self.expect_client_view(0, state, 'resume')
        # Administrative mode fixture only; no clear, progression or location command.
        self.rc('defaultgamemode survival')
        self.rc('gamemode survival '+self.username)
        self.action('resume_continuous_prefix_verified', source_run=story_resume.BASE_RUN,
                    prefix_length=checkpoint['prefix_length'], position=position)
        for case in prior['completed']:
            self.complete(case)
        self.ui_smoke('restored')
        self.ui_evidence()
        self.primary_chapter(checkpoint['prefix_length'])

    def run(self, resume_manifest=None):
        self.preflight()
        self.report['status'] = 'running'
        self.save()
        if resume_manifest:
            self.resume(resume_manifest)
        else:
            self.start()
            self.setup()
            self.restart_checkpoint('fresh-entry')
            self.ui_smoke('fresh-entry')
            self.check('fresh_entry_has_no_personal_or_shared_completion',
                       not self.status('fresh-entry-ready')['accepted']
                       and not self.status('fresh-entry-facts')['shared']['returned']
                       and not self.status('fresh-entry-array')['shared']['regulated'],
                       'pristine playable chapter world captured after a real stop, before first acceptance')
            self.complete('fresh_entry_world')
            self.primary_chapter()
        self.second_player()
        self.lifecycle_probes()
        self.rc('save-all flush')
        self.stop()
        self.package_world()
        self.check('full_saved_world_with_playerdata', (self.out/'qianzhen_story_world.zip').is_file(),
                   '包含剧情 SavedData、playerdata 与调查方块')
        self.complete('saved_world')
        self.check('all_selected_finished', not self.report['unrun'], self.report['unrun'])
        self.report['status'] = 'pass'
        self.save()


def freeze_handoff():
    """Transport the actual ignored report into fixed-source Modal checkouts.

    Only this collector-owned helper is written; never edit the report or the
    production chapter. The chapter hash makes stale snapshots fail closed.
    """
    handoff = json.loads(HANDOFF.read_text())
    chapter = json.loads(CHAPTER.read_text())
    for field in ('actions','stages','rewards'):
        if handoff[field] != chapter[field]:
            raise ValueError('Cannot freeze inconsistent runtime handoff: '+field)
    fields = ('schema','resource','readback','opening','routes','not_implemented','tests')
    snapshot = {field: handoff[field] for field in fields}
    snapshot.update(chapter_sha256=hashlib.sha256(CHAPTER.read_bytes()).hexdigest(),
        source_handoff_sha256=hashlib.sha256(HANDOFF.read_bytes()).hexdigest(),
        provenance='Snapshot of reports/qianzhen-story/runtime-handoff.json; no guessed action order')
    HANDOFF_SNAPSHOT.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2)+'\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/qianzhen-story-runtime')
    parser.add_argument('--handoff', type=Path, default=HANDOFF)
    parser.add_argument('--origin', nargs=3, type=int, default=(0,-61,0))
    parser.add_argument('--resume-manifest', type=Path,
                        help='固定 HTTPS ZIP/SHA256 manifest；从原失败世界和已证明前缀继续')
    parser.add_argument('--plan', action='store_true', help='仅离线解析 handoff/任务/场景和连续路线，不启动游戏')
    parser.add_argument('--freeze-handoff', action='store_true', help='将实际忽略报告冻结为采集器专属传输 helper；不启动游戏')
    add_arguments(parser)
    args = parser.parse_args()
    if args.freeze_handoff and args.resume_manifest:
        parser.error('--freeze-handoff cannot be combined with --resume-manifest')
    if args.freeze_handoff:
        freeze_handoff()
        return
    if args.candidate and args.resume_manifest:
        parser.error('Seeded candidate cannot reuse authored --resume-manifest')
    evidence = StoryEvidence(args.out, args.handoff, args.origin,
                             candidate=args.candidate, request=args.request, provider=args.provider)
    failure = None
    try:
        if args.plan:
            evidence.preflight()
            if args.resume_manifest:
                evidence.report['resume_plan'] = {
                    'manifest': story_resume.load_manifest(args.resume_manifest),
                    'transport_and_saved_world': 'not_verified: 离线计划不下载或恢复世界'}
                evidence.save()
            print(json.dumps({'status':evidence.report['status'], 'selected':evidence.report['selected'],
                              'completed':[], 'unrun':evidence.report['unrun']}, ensure_ascii=False))
        else:
            evidence.run(args.resume_manifest)
    except BaseException as error:
        failure = error
        evidence.report['status'] = 'fail'
        evidence.report['failures'].append(repr(error))
        if evidence.s:
            try:
                evidence.input_keys([])
                evidence.capture('failure')
                evidence.rc('save-all flush')
            except Exception as capture_error:
                evidence.report['failures'].append('failure capture: '+repr(capture_error))
        raise
    finally:
        try:
            if evidence.s or evidence.report['termination'] == 'running':
                evidence.stop()
            if evidence.sessions:
                evidence.package_world()
        except BaseException as cleanup_error:
            evidence.report['status'] = 'fail'
            evidence.report['failures'].append('cleanup: '+repr(cleanup_error))
            if failure is None:
                raise
        finally:
            evidence.position_stream.close()
            evidence.action_stream.close()
            evidence.save()


if __name__ == '__main__':
    main()
