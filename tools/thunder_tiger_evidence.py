#!/usr/bin/env python3
"""Bounded Modal-only boss evidence. Commands prepare/read; mapped input fights.

Run only inside the fixed-source Modal preset. The independent NORMAL world is
exported before lethal testing. Developer frames/audio event logs do not certify
owner appearance, combat feel, sound quality, FPS or natural encounter density.
"""
from __future__ import annotations

import argparse
import copy
import gzip
import io
import json
import math
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.combat_capture import procs, scene, session as cs
from tools.combat_capture.beast import camera_pose, parse_status
from tools.combat_capture.capture import ffmpeg_record_argv, press_f5_to
from tools.combat_capture.xgame import LogTail
from tools.alchemy_quality_evidence import read_data, SecondClient
from tools.danxia_ruin_evidence import saved_cores
from tools.realm_stats_evidence import Evidence
from tools.yunshan.game_evidence import aim, teleport_command
from tools.buildgen.nbtread import _read_utf, _read_payload, TAG_COMPOUND

ENTITY = 'myvillage:thunder_tiger'
PARENT_TAG = 'thunder_tiger_lair_boss'
CASES = ('entry', 'visuals', 'combat', 'phase2', 'horn', 'observers', 'restart', 'death')
NUMBER = r'-?\d+(?:\.\d+)?'


def select_cases(value=None):
    names = list(CASES) if value in (None, 'all') else value.split(',')
    if not names or len(names) != len(set(names)) or any(n not in CASES for n in names):
        raise ValueError('Cases must be all or unique names from: '+','.join(CASES))
    return [name for name in CASES if name in names]


def parse_boss_status(reply):
    row = parse_status(reply)
    required = {'uuid', 'boss_phase', 'horn_broken', 'horn_exposure', 'max', 'move', 'tick', 'pos'}
    if not required <= row.keys():
        raise ValueError('Incomplete boss status: '+reply)
    for key in ('boss_phase', 'horn_exposure', 'tick'):
        row[key] = int(row[key])
    row['max'] = float(row['max'])
    if row['boss_phase'] not in (1, 2) or row['horn_broken'] not in ('true', 'false'):
        raise ValueError('Invalid boss status: '+reply)
    row['horn_broken'] = row['horn_broken'] == 'true'
    for key in ('pos', 'home', 'horn_pos'):
        if key in row:
            values = re.findall(NUMBER, row[key])
            if len(values) != 3:
                raise ValueError('Invalid vector '+key)
            row[key] = [float(v) for v in values]
    if not math.isfinite(row['max']) or row['max'] <= 0:
        raise ValueError('Invalid boss max health')
    return row


def parse_boss_roots(reply):
    """BeastCommands.status filters PartEntity through instanceof BeastEntity."""
    return [parse_boss_status(line) for line in reply.splitlines() if line.startswith(ENTITY+' ')]


def parent_selector(limit=None):
    fields=f'type={ENTITY},tag={PARENT_TAG},distance=..80'
    return '@e['+fields+(f',limit={limit}' if limit is not None else '')+']'


def allow_commands_handoff_copy(data):
    """Change one named byte in an exported copy, retaining every other NBT byte."""
    raw=gzip.decompress(data)
    marker=b'\x01\x00\x0dallowCommands'
    if raw.count(marker)!=1:raise ValueError('Expected exactly one named allowCommands TAG_BYTE')
    position=raw.index(marker)+len(marker)
    if position>=len(raw) or raw[position] not in (0,1):raise ValueError('Invalid allowCommands boolean byte')
    def parsed(payload):
        stream=io.BytesIO(payload)
        if stream.read(1)!=bytes([TAG_COMPOUND]):raise ValueError('level.dat is not a root compound')
        _read_utf(stream);doc=_read_payload(stream,TAG_COMPOUND)
        if stream.read():raise ValueError('Trailing bytes in level.dat')
        return doc
    before=parsed(raw)
    if before.get('Data',{}).get('allowCommands')!=raw[position]:
        raise ValueError('Named byte is not Data.allowCommands')
    updated=raw[:position]+b'\x01'+raw[position+1:]
    expected=copy.deepcopy(before);expected['Data']['allowCommands']=1
    if parsed(updated)!=expected:raise ValueError('Export metadata changed another NBT field')
    output=io.BytesIO()
    with gzip.GzipFile(fileobj=output,mode='wb',filename='',mtime=0) as stream:stream.write(updated)
    return output.getvalue(),raw[position]


def tiger_events(text):
    rows = []
    for line in text.splitlines():
        if 'THUNDER_TIGER ' in line:
            row = parse_status(line.split('THUNDER_TIGER ', 1)[1])
            if 'event' not in row or 'uuid' not in row:
                raise ValueError('Incomplete boss event: '+line)
            rows.append(row)
    return rows


def attack_evidence(events, uuid, move):
    """An attack exists only if one boss emitted ordered telegraph and active events."""
    relevant = [e for e in events if e['uuid'] == uuid and e.get('move') == move]
    telegraph = next((i for i, e in enumerate(relevant) if e['event'] == 'telegraph'), None)
    return telegraph is not None and any(e['event'] == 'active' for e in relevant[telegraph+1:])


def persistence_equal(before, after):
    """Never replace missing state with a default that could hide a failed save."""
    required = ('UUID', 'Health', 'thunder_tiger')
    if any(k not in before or k not in after for k in required):
        return False
    old, new = before['thunder_tiger'], after['thunder_tiger']
    return (before['UUID'] == after['UUID'] and abs(before['Health']-after['Health']) < .002
            and all(k in old and k in new and old[k] == new[k]
                    for k in ('horn_broken', 'phase', 'home', 'pillars', 'encounter_realm', 'encounter_stage')))


def inventory_counts(items):
    counts={}
    for item in items:
        identity=item['id']
        counts[identity]=counts.get(identity,0)+item.get('count',1)
    return counts


def body_contact_geometry(status,player,horn_rules):
    """Match the authored body's occupied box; normal high head is not a sword-height target."""
    yaw=math.radians(float(status['yaw']))
    forward=(-math.sin(yaw),math.cos(yaw))
    middle=(horn_rules['body_front']+horn_rules['body_back'])/2
    length=(horn_rules['body_front']-horn_rules['body_back'])/2
    width=horn_rules['body_width']/2
    center=[status['pos'][0]+forward[0]*middle,status['pos'][1]+1.1,status['pos'][2]+forward[1]*middle]
    half_x=abs(forward[0])*length+abs(forward[1])*width
    half_z=abs(forward[1])*length+abs(forward[0])*width
    near_x=max(center[0]-half_x,min(player[0],center[0]+half_x))
    near_z=max(center[2]-half_z,min(player[2],center[2]+half_z))
    return center,math.hypot(player[0]-near_x,player[2]-near_z)


def entrance_trial_positions(home,entry,distance):
    origin=[home[0]+.5,home[1],home[2]+.5]
    dx,dz=entry[0]-origin[0],entry[2]-origin[2]
    length=math.hypot(dx,dz)
    if length<=0:raise ValueError('Authored lair entry coincides with boss home')
    return origin,[origin[0]+dx/length*distance,origin[1],origin[2]+dz/length*distance]


def diagnostic_boss_patch(health,phase,broken,restore=None):
    if not math.isfinite(health) or health<=0 or phase not in (1,2):
        raise ValueError('Invalid diagnostic boss state')
    saved=restore or {}
    fields=[f'Health:{health}f',f'HurtTime:{saved.get("HurtTime",0)}s',f'DeathTime:{saved.get("DeathTime",0)}s',
            f'thunder_tiger:{{phase:{phase},horn_broken:{int(bool(broken))}b}}']
    if restore is not None:
        for key,suffix in (('Pos','d'),('Motion','d'),('Rotation','f')):
            fields.append(key+':['+','.join(f'{value}{suffix}' for value in saved[key])+']')
    return '{'+','.join(fields)+'}'


def horn_chain(events, uuid):
    own = [e for e in events if e['uuid'] == uuid]
    collision = next((i for i,e in enumerate(own) if e['event'] == 'horn_collision' and 'pillar' in e), None)
    return collision is not None and any(e['event'] == 'horn_break' for e in own[collision+1:])


class TigerEvidence(Evidence):
    def __init__(self, out, cases, seed=20261002):
        if out.exists() and any(out.iterdir()):
            raise ValueError('Retain earlier evidence; output must be empty: '+str(out))
        super().__init__(out)
        self.seed = seed
        self.selected = select_cases(cases)
        self.report.update(kind='thunder_tiger', selected=self.selected, completed=[], unrun=self.selected[:],
                           cases={}, inputs=[], state_samples=[], screenshots=[], videos=[], world_seed=seed,
                           owner_appearance='not_verified', owner_feel='not_verified', owner_audio='not_verified',
                           owner_fps='not_verified', owner_multiplayer='not_verified',
                           natural_encounter_density='not_verified', method=__doc__)
        self.anchor = None
        self.uuid = None
        self.hud_hidden=False
        self.target = parent_selector(limit=1)

    def must(self, name, passed, detail):
        if not self.check(name, passed, detail):
            raise AssertionError(name)

    def poll(self, fn, predicate, timeout, reason):
        end = time.monotonic()+timeout
        last = None
        while time.monotonic() < end:
            last = fn()
            if predicate(last):
                return last
            time.sleep(.1)
        raise TimeoutError(f'{reason}: last={last}')

    def start(self, reuse=False):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Actual game job is restricted to the Modal disposable /workspace/repo checkout')
        started = time.monotonic()
        bindings={'key_key.forward':'key.keyboard.w','key_key.left':'key.keyboard.a',
                  'key_key.use':'key.mouse.right','key_key.myvillage.dodge':'key.keyboard.left.alt'}
        cs.merge_options(cs.CLIENT_DIR/'options.txt',bindings)
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), reuse_world=reuse,
            natural_terrain=True, natural_monsters=True, world_seed=self.seed, language='zh_cn',
            lock=str(procs.default_lock_path(ROOT))))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        saved=dict(line.split(':',1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines() if ':' in line)
        self.must('isolated_client_mapped_bindings',all(saved.get(k)==v for k,v in bindings.items()),bindings)
        self.report['phases'].append({'phase': 'restart' if reuse else 'initial_setup',
            'wall_s': round(time.monotonic()-started, 3), 'capture_timings': state.get('timings', {})})
        self.rc(f'op {self.s.username}')
        self.rc('myvillage beast debug on')
        request=Path('/workspace/request.json')
        if request.exists():
            self.report['source_commit']=json.loads(request.read_text())['commit']
        self.report['mod_version']=next(line.split('=',1)[1] for line in (ROOT/'gradle.properties').read_text().splitlines()
                                        if line.startswith('mod_version='))
        if not reuse:
            sounds=json.loads((ROOT/'src/main/resources/assets/myvillage/sounds.json').read_text())
            self.report['audio_resources']={key:value for key,value in sounds.items() if key.startswith('thunder_tiger.')}
            self.report['audio_resources_scope']='registered cue aliases and subtitles; no captured audio or owner sound acceptance'
        self.save()

    def body(self):
        return read_data(self.rc('data get entity '+self.target))

    def status(self):
        row = parse_boss_status(self.rc('myvillage beast status '+self.target))
        self.report['state_samples'].append({'wall': time.monotonic(), **row})
        return row

    def events(self):
        path = ROOT/'run-acceptance/logs/latest.log'
        return tiger_events(path.read_text(errors='replace')) if path.exists() else []

    @staticmethod
    def coords(position):
        return ' '.join(str(int(v)) for v in position)

    def block(self):
        return read_data(self.rc('data get block '+self.coords(self.anchor)))

    def count(self):
        reply = self.rc(f'execute positioned {self.coords(self.anchor)} if entity {parent_selector()}')
        found = re.search(r'count: (\d+)', reply)
        return int(found.group(1)) if found else 0

    def roots(self,position,radius):
        reply=self.rc(f'execute positioned {self.coords(position)} run myvillage beast status @e[type={ENTITY},distance=..{radius}]')
        return parse_boss_roots(reply)

    def shot(self, name, game=None, **detail):
        path = self.out/(name+'.png')
        result = (game or self.s.game).shot(path, max_wait=3, settle=.2)
        self.report['screenshots'].append({'file': path.name, 'capture': result, **detail})
        self.save()

    def hud(self,hidden):
        if self.hud_hidden!=hidden:
            self.s.game.key('F1');self.hud_hidden=hidden;time.sleep(.15)

    def action_shot(self,name):
        self.rc('tick freeze')
        was_hidden=self.hud_hidden
        try:
            self.hud(True)
            self.shot(name,state=self.status(),paused_for_capture=True)
        finally:
            self.hud(was_hidden)
            self.rc('tick unfreeze')

    def mapped(self, kind, *args):
        game = self.s.game
        game.require_ingame('thunder tiger '+kind)
        self.report['inputs'].append({'kind': kind, 'args': list(args), 'at': time.monotonic(),
                                      'boss': self.status()})
        game.xdo(*args)
        self.save()

    def look(self, position, target, mode='survival', username=None):
        user = username or self.s.username
        self.rc(f'gamemode {mode} {user}')
        yaw, pitch = aim(position, target)
        self.rc(teleport_command(user, position, yaw, pitch))

    def aim_current(self,target):
        user=self.s.username
        position=read_data(self.rc(f'data get entity {user} Pos'))
        yaw,pitch=aim(position,target)
        self.rc(f'execute as {user} at @s run tp @s ~ ~ ~ {yaw:.3f} {pitch:.3f}')

    def natural(self):
        user = self.s.username
        for command in (*scene.world_rules(), f'gamemode spectator {user}', 'gamerule naturalRegeneration false',
                        'gamerule doMobLoot true', 'myvillage world pause'):
            self.rc(command)
        reply = self.rc('execute positioned 0 100 0 run locate structure myvillage:thunder_tiger_lair')
        locate_reply = reply
        found = re.search(r'\[(-?\d+), (?:~|-?\d+), (-?\d+)\]', reply)
        self.must('normal_world_natural_locate', bool(found), reply)
        x,z = map(int, found.groups())
        self.rc(f'forceload add {x-48} {z-48} {x+48} {z+48}')
        self.rc(f'tp {user} {x} 180 {z}')
        def find():
            self.rc('save-all flush')
            anchors = saved_cores(cs.SERVER_DIR/cs.LEVEL_NAME/'region', (x,z), radius=5,
                                  block_entity_id='myvillage:thunder_tiger_lair_anchor')
            return min(anchors, key=lambda p:(p['x']-x)**2+(p['z']-z)**2) if anchors else None
        be = self.poll(find, bool, 90, 'natural anchor in bounded saved chunks')
        self.anchor = [be['x'],be['y'],be['z']]
        rules = json.loads((ROOT/'src/main/resources/data/myvillage/boss/thunder_tiger_lair.json').read_text())
        turn = None
        for index, face in enumerate(('north','east','south','west')):
            reply = self.rc(f'execute if block {self.coords(self.anchor)} myvillage:thunder_tiger_lair_anchor[facing={face}] run data get block {self.coords(self.anchor)}')
            if 'LairState' in reply:
                turn = index; break
        self.must('natural_lair_has_native_orientation',turn is not None,be)
        def mapped_point(point):
            dx,dy,dz = [a-b for a,b in zip(point,rules['anchor'])]
            for _ in range(turn): dx,dz = -dz,dx
            return [self.anchor[0]+dx+.5,self.anchor[1]+dy,self.anchor[2]+dz+.5]
        entry, trigger = mapped_point(rules['entry']),mapped_point(rules['trigger'])
        self.rc(f'attribute {user} minecraft:generic.max_health base set 1000')
        self.rc(f'effect give {user} minecraft:instant_health 1 20 true')
        self.look(entry,trigger)
        self.s.game.require_ingame('mapped entry into natural lair trigger')
        self.report['inputs'].append({'kind':'natural_lair_entry','args':['keydown','w'],'at':time.monotonic()})
        self.s.game.xdo('keydown','w')
        try:
            self.poll(lambda:self.block().get('LairState'),lambda state:state=='ACTIVE',15,'mapped natural lair trigger')
        finally:
            self.s.game.xdo('keyup','w')
        self.rc(f'gamemode creative {user}')
        self.poll(self.count, lambda n:n == 1, 30, 'exactly one natural boss')
        roots=self.roots(self.anchor,80)
        anchor_uuid=scene.ints_to_uuid(self.block()['Boss'])
        self.must('one_actual_parent_matches_lair_uuid',len(roots)==1 and roots[0]['uuid']==anchor_uuid,
                  {'anchor_uuid':anchor_uuid,'root_statuses':roots,'count_policy':'parent-only lair tag; root instanceof status excludes multipart colliders'})
        self.target=anchor_uuid
        state = self.status()
        self.uuid = state['uuid']
        self.target = self.uuid
        self.must('natural_single_bound_boss', self.block().get('LairState') == 'ACTIVE', {'anchor':be, 'boss':self.body()})
        self.report['natural_lair'] = {'anchor':self.anchor, 'locate':locate_reply, 'uuid':self.uuid,'entry':entry,
            'forceload':[x-48,z-48,x+48,z+48]}
        p=state['pos']
        self.look([p[0]+34,p[1]+27,p[2]-34],[p[0],p[1]+3,p[2]],'spectator')
        self.shot('natural_lair_overview',natural_generation=True)
        self.save()

    def ordinary_guide_command(self):
        """A bounded retry of an idempotent read, after actual chat-screen readiness."""
        game=self.s.game
        command='/exploration guide tiger'
        for attempt in range(2):
            row={'command':command,'attempt':attempt+1,'source':'mapped ordinary-player read command'}
            self.report.setdefault('client_commands',[]).append(row)
            tail=LogTail(game.client_log)
            try:
                game.require_ingame('ordinary-player tiger guide')
                time.sleep(1.5)  # Let the teleport/chunk-render transition finish before opening chat.
                game.xdo('key','t')
                self.poll(game.pointer_grabbed,lambda grabbed:grabbed is False,8,'actual chat screen opens')
                time.sleep(.8)
                game.xdo('type','--delay','70',command)
                time.sleep(.3);game.xdo('key','Return');time.sleep(.8)
                if not game.wait_ingame(8):
                    raise RuntimeError('Guide chat did not close after Enter')
                match,line=tail.wait(r'\[CHAT\].*(雷角山君|Thunderhorn Tiger)',10,poll=.2)
                row['client_log_tail']=tail.tail_text(20)
                if not match:raise RuntimeError('No actual guide response; retain any partial chat in client_log_tail')
                row.update(result='pass',reply=line);self.save();return match,line
            except Exception as error:
                row.update(result='retry' if attempt==0 else 'fail',error=repr(error),client_log_tail=tail.tail_text(20))
                self.save()
                if attempt:raise
                if not game.wait_ingame(2):game.recover_grab()
        raise AssertionError('Unreachable guide retry state')

    def entry(self):
        """Natural entry is a prerequisite; manual entry modes get independent actors."""
        home = self.status()['pos']
        x,y,z = home[0]+96,home[1],home[2]
        user = self.s.username
        self.rc(f'fill {int(x)-4} {int(y)-1} {int(z)-4} {int(x)+4} {int(y)-1} {int(z)+4} minecraft:stone')
        self.look([x,y,z-2], [x,y-.5,z], 'creative')
        self.rc(f'gamemode survival {user}');self.rc(f'deop {user}')
        try:
            _,line=self.ordinary_guide_command()
            self.report['inputs'].append({'kind':'ordinary_player_guide','text':'/exploration guide tiger','reply':line})
            self.rc(f'gamemode creative {user}')  # The command above ran as an ordinary survival player.
            self.s.game.xdo('key','t');time.sleep(.3)
            raw,info=self.s.game.stable_grab(max_wait=3,settle=.2)
            self.s.game.save_png(raw,self.out/'ordinary_player_guide.png')
            self.report['screenshots'].append({'file':'ordinary_player_guide.png','capture':info,'operator':False})
            self.s.game.xdo('key','Escape')
            self.must('guide_available_without_operator',bool(line),line)
        finally:
            self.rc(f'op {user}');self.rc(f'gamemode creative {user}')
        self.rc(f'item replace entity {user} weapon.mainhand with myvillage:thunder_tiger_spawn_egg')
        self.mapped('spawn_egg', 'click', '3')
        roots=self.poll(lambda:self.roots([x,y,z],12),lambda rows:len(rows)==1,10,'one actual egg root excluding multipart colliders')
        egg=roots[0]['uuid']
        reply = self.rc(f'data get entity {egg}')
        self.must('mapped_spawn_egg_creates_boss', isinstance(read_data(reply),dict), reply)
        def fresh_health(selector):
            body=read_data(self.rc(f'execute positioned {x} {y} {z} run data get entity {selector}'))
            maximum=self.number(f'execute positioned {x} {y} {z} run attribute {selector} minecraft:generic.max_health get')
            return {'health':body['Health'],'max':maximum}
        fresh=self.poll(lambda:fresh_health(egg),lambda r:abs(r['health']-r['max'])<.002,10,'egg starts at full native realm health')
        self.must('fresh_egg_health_equals_native_max',abs(fresh['health']-fresh['max'])<.002,fresh)
        self.rc(f'kill {egg}')
        self.rc(f'summon {ENTITY} {x} {y} {z} {{Tags:["tiger_summon_check"]}}')
        body = read_data(self.rc('data get entity @e[tag=tiger_summon_check,limit=1]'))
        self.must('dedicated_summon_creates_boss', isinstance(body,dict), body)
        fresh=self.poll(lambda:fresh_health('@e[tag=tiger_summon_check,limit=1]'),
                        lambda r:abs(r['health']-r['max'])<.002,10,'summon starts at full native realm health')
        self.must('fresh_summon_health_equals_native_max',abs(fresh['health']-fresh['max'])<.002,fresh)
        self.rc('kill @e[tag=tiger_summon_check]')
        self.look([home[0]+12,home[1]+3,home[2]-12], [home[0],home[1]+1.5,home[2]], 'spectator')

    def visuals(self):
        self.rc('tick freeze')
        try:
            state = self.status()
            pos = state['pos']
            self.rc(f'gamemode spectator {self.s.username}')
            self.hud(True)
            try:
                for view in ('front','side','q_front','q_back'):
                    distance=16 if view=='side' else 14
                    pose = camera_pose(pos, float(state.get('yaw',0)), view, distance, 4.3, 3.2)
                    self.rc(teleport_command(self.s.username,pose[:3],pose[3],pose[4]))
                    time.sleep(.6)
                    self.shot('idle_'+view, state=state)
            finally:
                self.hud(False)
            self.look([pos[0]+4,pos[1],pos[2]-4], [pos[0]+8,pos[1]+1.62,pos[2]-8], 'survival')
            press_f5_to(self.s,'front')
            self.hud(True)
            self.shot('player_scale', state=state)
            self.hud(False)
            press_f5_to(self.s,'first')
            self.look([pos[0]+12,pos[1]+2,pos[2]-12],[pos[0],pos[1]+2.5,pos[2]],'creative')
            self.rc('tick step 2');time.sleep(.4)  # Publish observer eligibility/bossbar while the world is otherwise paused.
            self.shot('normal_hud_bossbar',state=self.status(),fixture='creative observer: ordinary HUD without enlarged survival hearts')
            self.diagnostic_visual_states()
        finally:
            self.rc('tick unfreeze')

    def diagnostic_visual_states(self):
        """Explicit renderer fixtures; caller has frozen the world and is creative.

        These clean camera views do not prove a live phase transition or player
        horn break. Those claims belong to the independent mapped fight cases.
        """
        before=self.body()
        player=read_data(self.rc(f'data get entity {self.s.username} Pos'))
        rotation=read_data(self.rc(f'data get entity {self.s.username} Rotation'))
        was_hidden=self.hud_hidden
        self.report['diagnostic_visual_fixture']={
            'method':'administrator persistent render flags; frozen camera views',
            'proves':'native synchronized renderer appearance only',
            'does_not_prove':['combat phase transition','player-triggered horn break'],
            'restoration':'original boss health, persistent encounter fields, position, motion and rotation restored'}
        self.hud(True)
        try:
            for label,broken in (('phase2_intact',False),('phase2_broken',True)):
                patch=diagnostic_boss_patch(before['Health'],2,broken)
                self.rc(f'data merge entity {self.target} {patch}')
                self.rc('tick step 2');time.sleep(.6)
                state=self.status();pos=state['pos'];yaw=float(state.get('yaw',0))
                self.must('diagnostic_'+label+'_flags',state['boss_phase']==2 and state['horn_broken']==broken,state)
                for view in ('front','q_front'):
                    pose=camera_pose(pos,yaw,view,15,4.5,3.2)
                    self.rc(teleport_command(self.s.username,pose[:3],pose[3],pose[4]))
                    time.sleep(.6)
                    self.shot('diagnostic_'+label+'_'+view,state=state,
                              fixture='administrator render state; not actual combat execution evidence',
                              paused_for_capture=True,view=view)
                if broken:
                    pose=camera_pose(state['horn_pos'],yaw,'q_front',6,1.2,0)
                    self.rc(teleport_command(self.s.username,pose[:3],pose[3],pose[4]))
                    time.sleep(.6)
                    self.shot('diagnostic_phase2_broken_head',state=state,
                              fixture='administrator broken-horn renderer detail; not actual player horn-break evidence',
                              paused_for_capture=True,view='head_detail')
        finally:
            old=before['thunder_tiger']
            patch=diagnostic_boss_patch(before['Health'],old['phase'],bool(old['horn_broken']),restore=before)
            self.rc(f'data merge entity {self.target} {patch}')
            restored=self.body()
            self.must('diagnostic_visual_state_fully_restored',persistence_equal(before,restored)
                and before['thunder_tiger']==restored['thunder_tiger']
                and all(len(before[key])==len(restored[key]) and all(abs(a-b)<.002 for a,b in zip(before[key],restored[key]))
                        for key in ('Pos','Motion','Rotation')),
                {'before':before,'after':restored})
            self.rc(f'gamemode creative {self.s.username}')
            self.rc(teleport_command(self.s.username,player,rotation[0],rotation[1]))
            self.hud(was_hidden)

    def prepare_fighter(self):
        user = self.s.username
        for command in (f'gamemode survival {user}', f'effect clear {user}',
                        f'attribute {user} minecraft:generic.max_health base set 1000',
                        f'effect give {user} minecraft:instant_health 1 20 true',
                        f'effect give {user} minecraft:saturation infinite 0 true',
                        f'item replace entity {user} hotbar.0 with myvillage:qingfeng_sword',
                        f'myvillage cultivation learn {user} myvillage:taxue_wuhen', 'tick rate 5'):
            self.rc(command)
        self.s.game.key('1')
        self.report['combat_mode'] = scene.ensure_cultivation(self.s)
        self.rc(f'execute as {user} run myvillage combat debug on')
        self.report['combat_debug_source']='administrator particle-only fixture; attacks and dodges remain mapped inputs'
        options = ROOT/'run-combat-capture/client/options.txt'
        values = dict(line.split(':',1) for line in options.read_text().splitlines() if ':' in line)
        self.must('physical_attack_binding', values.get('key_key.attack') == 'key.mouse.left', values.get('key_key.attack'))
        self.must('physical_dodge_binding', values.get('key_key.myvillage.dodge') == 'key.keyboard.left.alt',
                  {k:v for k,v in values.items() if 'dodge' in k})
        self.report['fixture_preparation'] = '1000 player max health and healing; no resistance/invulnerability; learned dodge; tick rate 5'

    def record(self, name, seconds, operation):
        was_hidden=self.hud_hidden
        self.hud(True)
        video = self.out/(name+'.mp4')
        process = subprocess.Popen(ffmpeg_record_argv(self.s.state['display'],video,seconds,fps=20),
                                   stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            operation()
        finally:
            # Stop on the completed action instead of keeping an idle recording alive.
            if process.poll() is None:
                process.send_signal(2)
            try:
                result = process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(); raise
            self.report['videos'].append({'file':video.name,'ffmpeg_exit':result,'duration_limit_s':seconds})
            self.save()
            self.hud(was_hidden)
            if not video.is_file() or video.stat().st_size<1000:
                raise RuntimeError('Video recording failed: '+name)

    def position_for_move(self, move):
        # Placement is a labelled initial fixture, never a forced action. Distant
        # selection remains ordinary AI, movement and counterattacks use input.
        self.rc(f'gamemode creative {self.s.username}')
        state=self.poll(self.status,lambda row:row['move']=='none',18,'idle before safe entrance-lane trial fixture')
        distance = (move['use_range'][0]+move['use_range'][1])/2
        if move['id'].endswith('_claw'):
            distance = move['use_range'][1]-.4
        origin,player=entrance_trial_positions(state['home'],self.report['natural_lair']['entry'],distance)
        self.rc('tick freeze')
        try:
            self.rc(teleport_command(self.target,origin))
            self.look(player,[origin[0],origin[1]+1.8,origin[2]],mode='survival')
            clearance=self.rc('execute positioned '+' '.join(map(str,player))+
                              ' if block ~ ~ ~ minecraft:air if block ~ ~1 ~ minecraft:air')
            self.must('entrance_trial_has_player_clearance_'+move['animation'] if 'animation' in move else 'entrance_trial_clearance',
                      'Test passed' in clearance,{'player':player,'reply':clearance})
            self.report.setdefault('trial_fixtures',[]).append({'move':move['id'],'boss':origin,'player':player,
                'policy':'idle-only initial placement on authored home-to-entry clear lane; ordinary AI action'})
        finally:self.rc('tick unfreeze')

    def wait_move(self, move, timeout=90, reposition=None):
        deadline=time.monotonic()+timeout
        was_idle=True
        last=None
        while time.monotonic()<deadline:
            last=self.status()
            if last['move']==move['id'] and 0<=last['tick']<move['turn_lock_tick']:
                return last
            idle=last['move']=='none'
            # An unwanted AI attack may close the distance. A new labelled
            # initial placement starts the next trial only after that action ends.
            if idle and not was_idle:
                (reposition or (lambda:self.position_for_move(move)))()
            was_idle=idle
            time.sleep(.1)
        raise TimeoutError(f'AI telegraph {move["id"]}: last={last}')

    def dodge(self):
        self.mapped('left_held', 'keydown','a')
        time.sleep(.25)
        try:
            self.mapped('dodge', 'key','Alt_L')
            time.sleep(.4)
        finally:
            self.s.game.xdo('keyup','a')

    def approach_head(self, distance=2.5, timeout=9):
        deadline = time.monotonic()+timeout
        self.mapped('approach_head', 'keydown','w')
        try:
            while time.monotonic() < deadline:
                state = self.status()
                player = read_data(self.rc(f'data get entity {self.s.username} Pos'))
                head = state['horn_pos']
                horizontal = math.hypot(player[0]-head[0],player[2]-head[2])
                if horizontal <= distance:
                    self.aim_current(head)
                    return state
                self.aim_current(head)
                time.sleep(.15)
        finally:
            self.s.game.xdo('keyup','w')
        raise TimeoutError('mapped approach did not reach head')

    def approach_body(self,move,distance=2.2,timeout=4):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger_boss.json').read_text())['horn']
        deadline=time.monotonic()+timeout
        self.mapped('approach_body','keydown','w')
        try:
            while time.monotonic()<deadline:
                state=self.status()
                if state['move']!=move['id'] or state['tick']>=move['total_ticks']-5:
                    raise RuntimeError('Recovery window ended before body approach/click')
                player=read_data(self.rc(f'data get entity {self.s.username} Pos'))
                center,gap=body_contact_geometry(state,player,rules)
                self.aim_current(center)
                if gap<=distance:
                    self.report['body_approach_contact']={'player':player,'body_center':center,'gap':gap,'state':state}
                    return state
                time.sleep(.1)
        finally:self.s.game.xdo('keyup','w')
        raise TimeoutError('Mapped body approach did not reach sword distance during recovery')

    def place_at_body_flank(self):
        state=self.status()
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger_boss.json').read_text())['horn']
        yaw=math.radians(float(state['yaw']))
        radius=rules['body_width']/2+1.8
        p=state['pos'];player=[p[0]+math.cos(yaw)*radius,p[1],p[2]+math.sin(yaw)*radius]
        center,gap=body_contact_geometry(state,player,rules)
        self.look(player,center)
        self.report['body_flank_fixture']={'player':player,'body_center':center,'gap':gap,'boss':state}
        return state

    def combat(self):
        self.prepare_fighter()
        data = json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger.json').read_text())
        for move in data['moves']:
            self.position_for_move(move)
            def fight(move=move):
                telegraph = self.wait_move(move)
                self.action_shot(move['animation']+'_telegraph')
                hp_before = self.number(f'data get entity {self.s.username} Health')
                self.poll(self.status,lambda s:s['move']==move['id'] and s['tick']>=move['active_ticks'][0]-4,
                          18,'dodge timing')
                self.dodge()
                active = self.poll(self.status,
                    lambda s:s['move']==move['id'] and s['tick']>=move['active_ticks'][0],8,'AI active frame')
                self.action_shot(move['animation']+'_active')
                self.poll(self.status,lambda s:s['move']!=move['id'] or s['tick']>move['active_ticks'][1],
                          15,'attack recovery')
                hp_after = self.number(f'data get entity {self.s.username} Health')
                self.must(move['animation']+'_natural_telegraph_and_active',
                          attack_evidence(self.events(),self.uuid,move['id']), {'before':telegraph,'active':active})
                if move['animation'] == 'claw':
                    self.must('mapped_claw_dodge_avoids_damage', abs(hp_before-hp_after)<.002,
                              {'before':hp_before,'after':hp_after})
                    self.approach_body(move)
                    before = self.body()['Health']
                    state = self.status()
                    self.mapped('recovery_punish','click','1')
                    after = self.poll(self.body,lambda b:b['Health']<before,5,'mapped recovery sword damage')
                    self.must('recovery_punish_real_damage', state['tick']>move['active_ticks'][1]
                              and state['move']==move['id'], {'input_state':state,'before':before,'after':after['Health']})
                self.report['cases'].setdefault('combat',{}).setdefault('attacks',[]).append({
                    'move':move['id'],'hp_before':hp_before,'hp_after':hp_after,'selection':'ordinary AI',
                    'dodge':'mapped A + Left Alt'})
            self.record(move['animation']+'_mapped',115,fight)
            # Allow the move cooldown and player dodge cooldown to clear at normal rate.
            self.rc(f'gamemode creative {self.s.username}'); self.rc('tick rate 20');time.sleep(9)
            self.rc('tick rate 5')
        log = (ROOT/'run-acceptance/logs/latest.log').read_text(errors='replace')
        self.must('mapped_dodge_server_accepted',bool(re.search(r'DODGE_DEBUG .*result=started',log)),
                  'physical Alt inputs and server started events retained')

    def phase2(self):
        # Above-threshold preparation uses a load/merge fixture. Only the real
        # subsequent player hit may produce the live phase transition.
        tuning = json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger_boss.json').read_text())
        self.prepare_fighter()
        state = self.status()
        health = state['max']*tuning['phase_health_fraction']+.25
        self.rc(f'data merge entity {self.target} {{Health:{health}f}}')
        state=self.place_at_body_flank()
        event_start=len(self.events())
        self.mapped('phase_threshold_sword_hit','click','1')
        result = self.poll(self.status,lambda s:s['boss_phase']==2,10,'health threshold phase two')
        self.action_shot('phase_two_transition')
        self.must('mapped_hit_crosses_phase_threshold',self.body()['Health']<state['max']*tuning['phase_health_fraction']
            and any(e['uuid']==self.uuid and e['event']=='phase_change' for e in self.events()[event_start:]),result)
        self.report['phase_two_trigger'] = 'administrator above-threshold health fixture; actual mapped sword crosses threshold; no natural full-fight claim'
        move=next(m for m in json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger.json').read_text())['moves']
                  if m['animation']=='roar')
        self.position_for_move(move)
        def aftershock():
            self.wait_move(move)
            self.poll(self.status,lambda s:s['move']==move['id'] and s['tick']>=move['active_ticks'][0]-4,20,'phase two dodge timing')
            self.dodge()
            event_start=len(self.events())
            expected=move['active_ticks'][0]+tuning['phase_two']['aftershock_delay_ticks']
            self.poll(self.events,lambda rows:any(e['uuid']==self.uuid and e['event']=='aftershock'
                and e.get('move')==move['id'] and e.get('phase')=='2' and int(e['tick'])==expected
                for e in rows[event_start:]),18,'phase two authored delayed thunder')
            self.action_shot('phase_two_aftershock')
        self.record('phase_two_mapped_aftershock',120,aftershock)
        # A second ordinary AI trial covers the distinct landing-triggered
        # aftershock. Its initial placement uses the authored clear entrance
        # lane, away from the three pillars; neither move nor landing is forced.
        pounce=next(m for m in json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger.json').read_text())['moves']
                    if m['animation']=='pounce')
        self.rc(f'gamemode creative {self.s.username}')
        self.poll(self.status,lambda row:row['move']=='none',18,'roar recovery before separate pounce fixture')
        home=self.status()['home'];entry=self.report['natural_lair']['entry']
        pounce_origin,pounce_player=entrance_trial_positions(home,entry,12)
        def place_pounce():
            self.rc('tick freeze')
            try:
                self.rc(teleport_command(self.target,pounce_origin))
                self.look(pounce_player,[pounce_origin[0],pounce_origin[1]+1.8,pounce_origin[2]])
            finally:self.rc('tick unfreeze')
        def landing_trial():
            place_pounce();event_start=len(self.events())
            self.wait_move(pounce,80,reposition=place_pounce)
            self.poll(self.status,lambda row:row['move']==pounce['id'] and row['tick']>=pounce['turn_lock_tick'],
                      15,'phase two pounce aim lock')
            self.dodge()
            def own_events():
                return [e for e in self.events()[event_start:] if e['uuid']==self.uuid and e.get('move')==pounce['id']]
            rows=self.poll(own_events,lambda rows:any(e['event']=='landing_telegraph' for e in rows),
                           15,'ordinary pounce landing mark')
            landing=next(e for e in rows if e['event']=='landing_telegraph')
            self.action_shot('phase_two_pounce_landing_mark')
            rows=self.poll(own_events,lambda rows:any(e['event']=='aftershock' for e in rows),
                           10,'ordinary pounce delayed aftershock')
            shock=next(e for e in rows if e['event']=='aftershock')
            self.must('phase_two_landing_then_authored_aftershock',
                int(shock['tick'])-int(landing['tick'])==tuning['phase_two']['aftershock_delay_ticks']
                and attack_evidence(rows,self.uuid,pounce['id']),{'landing':landing,'aftershock':shock})
            self.action_shot('phase_two_pounce_aftershock')
        self.report['phase_two_pounce_fixture']='initial boss/player placement on authored entrance lane while idle; ordinary AI selects and executes move'
        self.record('phase_two_pounce_landing_aftershock',125,landing_trial)

    @staticmethod
    def unpack_pos(value):
        x,z,y = value >> 38, (value >> 12) & 0x3ffffff, value & 0xfff
        if x >= 1 << 25: x -= 1 << 26
        if z >= 1 << 25: z -= 1 << 26
        if y >= 1 << 11: y -= 1 << 12
        return [x,y,z]

    def horn(self):
        self.prepare_fighter()
        move = next(m for m in json.loads((ROOT/'src/main/resources/data/myvillage/beast/thunder_tiger.json').read_text())['moves']
                    if m['animation']=='pounce')
        pillars = [self.unpack_pos(p) for p in self.body()['thunder_tiger']['pillars']]
        self.must('authored_pillars_bound',len(pillars)>0,pillars)
        def lure():
            for attempt in range(6):
                selected={}
                def place_lure():
                    state=self.status();p=state['pos']
                    pillar=min(pillars,key=lambda q:abs(math.dist([p[0],p[2]],[q[0],q[2]])-11))
                    dx,dz=pillar[0]+.5-p[0],pillar[2]+.5-p[2]
                    length=math.hypot(dx,dz)
                    if length<4:raise RuntimeError('Boss too close to every selected lure pillar')
                    self.look([pillar[0]+.5-dx/length*3.5,p[1],pillar[2]+.5-dz/length*3.5],
                              [p[0],p[1]+1.8,p[2]])
                    selected['pillar']=pillar
                place_lure()
                try:
                    start = self.wait_move(move,35,reposition=place_lure)
                    self.poll(self.status,lambda s:s['move']==move['id'] and s['tick']>=move['turn_lock_tick'],
                              12,'pounce aim lock')
                    self.dodge()
                    exposed = self.poll(self.status,lambda s:s['horn_exposure']>0,12,'authored pillar collision')
                except TimeoutError:
                    if attempt == 5: raise
                    continue
                self.action_shot('horn_exposed')
                self.approach_head(timeout=9)
                deadline = time.monotonic()+12
                while time.monotonic()<deadline and not self.status()['horn_broken']:
                    self.aim_current(self.status()['horn_pos'])
                    self.mapped('exposed_horn_sword_hit','click','1')
                    time.sleep(.8)
                if self.status()['horn_broken']:
                    self.shot('horn_broken',state=self.status())
                    self.must('pillar_collision_then_player_head_break',horn_chain(self.events(),self.uuid),self.events())
                    self.report['horn_lure']={'attempt':attempt+1,'start':start,'pillar':selected['pillar']}
                    return
            raise AssertionError('No mapped horn break within bounded lure attempts')
        self.record('pillar_lure_and_head_break',360,lure)
        # Observe actual AI after break at ranges selecting each thunder move.
        self.rc('tick rate 20')
        for distance in (12,24):
            self.position_for_move({'id':f'broken_horn_range_{distance}','animation':f'broken_horn_{distance}',
                                    'use_range':[distance,distance]})
            for _ in range(100):
                row=self.status()
                self.must('broken_horn_disables_thunder_'+str(distance),
                          row['move'] not in ('myvillage:thunder_tiger_thunder_stomp','myvillage:thunder_tiger_roar'),row)
                time.sleep(.1)

    def observers(self):
        self.rc('tick rate 5')
        self.position_for_move({'id':'myvillage:thunder_tiger_claw','use_range':[0,5.8]})
        self.poll(self.status,lambda s:s['move']!='none',20,'ongoing boss action before late observer')
        self.rc('tick freeze')
        before=self.status()
        try:
            with SecondClient(self) as guest:
                p=before['pos']
                self.look([p[0]+10,p[1]+1,p[2]-8],[p[0],p[1]+1.5,p[2]],'creative',guest.username)
                time.sleep(3)
                self.shot('late_observer_action',guest.game,state=before)
                self.hud(True)
                try:self.shot('first_observer_action',state=before)
                finally:self.hud(False)
                after=self.status()
                self.must('late_observer_shared_authoritative_state',self.count()==1
                    and all(before[k]==after[k] for k in ('uuid','move','tick','boss_phase','horn_broken','horn_exposure')),
                    {'before':before,'after':after})
                self.report['multiplayer']='two real clients; shared frozen action and late tracking frames observed; owner acceptance not verified'
        finally:
            self.rc('tick unfreeze')

    def restart(self):
        self.rc(f'gamemode creative {self.s.username}')
        self.rc('tick rate 20'); self.rc('tick freeze')
        before=self.body(); block=self.block()
        # force-gamemode makes the capture actor survival on login. Save it well
        # outside aggro range so a correct new AI action cannot be mistaken for
        # an incorrectly persisted old move during the client's startup delay.
        p=before['Pos']
        self.rc(teleport_command(self.s.username,[p[0],p[1]+40,p[2]-90]))
        self.rc('save-all flush');self.stop();self.start(reuse=True)
        self.rc('tick freeze')
        after=self.body(); state=self.status()
        self.must('boss_health_horn_lair_survive_real_restart',persistence_equal(before,after),{'before':before,'after':after})
        self.must('restart_cancels_transient_attack_exposure',state['move']=='none' and state['horn_exposure']==0,state)
        self.must('lair_uuid_survives_restart',self.block().get('Boss')==block.get('Boss') and self.count()==1,self.block())
        p=state['pos']
        self.look([p[0]+12,p[1]+5,p[2]-12],[p[0],p[1]+2,p[2]],'creative')
        self.shot('restart_persisted_boss',state=state)
        self.rc('tick unfreeze')

    def export_world(self,pristine=False):
        label='pristine' if pristine else 'wounded'
        target=self.out/f'thunder-tiger-{label}-world.zip'
        if target.exists():raise FileExistsError('Refusing to overwrite sample: '+str(target))
        state=self.status();body=self.body()
        if pristine:
            self.must('pristine_sample_is_full_first_encounter',state['boss_phase']==1 and not state['horn_broken']
                      and abs(body['Health']-state['max'])<.002,{'boss':state,'health':body['Health']})
        entry=self.report['natural_lair']['entry']
        # A different user has a different UUID. Set the world's default spawn
        # and game mode, and omit all fixture actor playerdata from this export.
        self.rc('tick freeze');self.rc('save-off')
        normal_rules={'doDaylightCycle':True,'doWeatherCycle':True,'doMobSpawning':True,
            'doPatrolSpawning':True,'doTraderSpawning':True,'doInsomnia':True,'naturalRegeneration':True,
            'keepInventory':False,'doImmediateRespawn':False,'announceAdvancements':True,
            'sendCommandFeedback':True,'commandBlockOutput':True}
        saved_rules={}
        for rule in normal_rules:
            reply=self.rc('gamerule '+rule)
            match=re.search(r'\b(true|false)\b',reply)
            if not match:raise ValueError('Unreadable gamerule: '+reply)
            saved_rules[rule]=match.group(1)
        old_rate=self.rc('tick query')
        rate_match=re.search(r'(\d+(?:\.\d+)?) ticks per second',old_rate)
        saved_rate=rate_match.group(1) if rate_match else '20'
        self.rc('tick rate 20')
        self.rc('defaultgamemode creative')
        self.rc('setworldspawn '+self.coords(entry))
        self.look(entry,[entry[0],entry[1]+1.6,entry[2]+1],'creative')
        for rule,value in normal_rules.items():self.rc(f'gamerule {rule} {str(value).lower()}')
        self.rc('myvillage world resume')
        self.rc('forceload remove all')
        self.rc('save-all flush')
        world=cs.SERVER_DIR/cs.LEVEL_NAME
        instructions=(f'雷角山君 {label} 独立 NORMAL 样板\n\n'
            f'模组版本：{self.report.get("mod_version", "unknown")}\n'
            f'世界种子：{self.seed}\n巢穴入口：{self.coords(entry)}\n'
            f'定位命令：/tp @s {self.coords(entry)}\n'
            '新玩家默认创造模式，便于先观察、准备装备；用 /gamemode survival 开始挑战。\n'
            'ZIP副本已开启单人命令权限，可直接切换模式；服务器源世界文件未改写此字段。\n'
            '普通玩家可用 /exploration guide tiger 查看招式、诱扑和头部破角说明。\n'
            '本轮无山君掉落、经验或宝箱奖励。\n'
            '测试角色的玩家数据、装备和1000生命上限未放入此ZIP。\n'
            '昼夜、天气、自然回血和命簿模拟已恢复；死亡会掉落背包物品。\n'
            + ('山君满血、第一阶段、完整雷角；此前未进行战斗测试。\n' if pristine
               else '开发者持久性样板：山君已经历采集战斗，可能受伤、狂暴且断角。\n'))
        try:
            with zipfile.ZipFile(target,'x',zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(world.rglob('*')):
                    relative=path.relative_to(world)
                    if (path.is_file() and not path.is_symlink() and path.name not in ('session.lock','level.dat_old')
                        and relative.parts[0] not in ('playerdata','advancements','stats')):
                        destination=Path('thunder-tiger-'+label+'-world')/relative
                        if relative==Path('level.dat'):
                            converted,old_commands=allow_commands_handoff_copy(path.read_bytes())
                            archive.writestr(str(destination),converted)
                        else:archive.write(path,destination)
                archive.writestr('thunder-tiger-'+label+'-world/README.txt',instructions)
            (self.out/f'thunder-tiger-{label}-world-README.txt').write_text(instructions)
            self.report[label+'_world']={'file':target.name,'source':'isolated NORMAL capture world',
                'boss_state':state,'health':body['Health'],'bytes':target.stat().st_size,
                'spawn':entry,'default_gamemode':'creative','fixture_playerdata_included':False,
                'handoff_metadata':{'field':'level.dat Data.allowCommands','before':old_commands,'after':1,
                    'scope':'ZIP copy only; source server file unchanged; all other decompressed NBT bytes preserved'}}
        finally:
            for rule,value in saved_rules.items():self.rc(f'gamerule {rule} {value}')
            self.rc('myvillage world pause')
            self.rc('defaultgamemode survival')
            self.rc('forceload add '+' '.join(map(str,self.report['natural_lair']['forceload'])))
            self.rc('tick rate '+saved_rate)
            self.rc('save-on');self.rc('tick unfreeze')

    def death(self):
        self.prepare_fighter()
        # Near-lethal setup isolates the actual player killing blow and death
        # lifecycle. No /kill or /damage is used to produce the boss's death.
        state=self.place_at_body_flank();p=state['pos']
        self.rc(f'data merge entity {self.target} {{Health:1.0f}}')
        self.rc('kill @e[type=minecraft:item]');self.rc('kill @e[type=minecraft:experience_orb]')
        before=inventory_counts(read_data(self.rc(f'data get entity {self.s.username} Inventory')))
        xp=self.number(f'data get entity {self.s.username} XpTotal')
        def lethal():
            self.mapped('lethal_sword_hit','click','1')
            self.poll(self.body,lambda body:body['Health']<=0,8,'actual player lethal damage')
            self.rc('tick freeze')
            try:
                self.rc('tick step 8');time.sleep(.5)
                pose=camera_pose(p,float(state.get('yaw',0)),'q_front',14,4.3,3.2)
                self.look(pose[:3],[p[0],p[1]+3.2,p[2]],'spectator')
                self.shot('death_pose',paused_for_capture=True,death_step=8)
            finally:
                self.rc('tick unfreeze')
        self.record('player_killing_blow',30,lethal)
        self.poll(lambda:self.block().get('LairState'),lambda s:s=='CLEARED',15,'death tombstone')
        self.rc('tick rate 20');time.sleep(3)
        self.must('boss_death_no_items', 'Test failed' in self.rc('execute if entity @e[type=minecraft:item]'), 'doMobLoot remained true')
        self.must('boss_death_no_experience_orbs', 'Test failed' in self.rc('execute if entity @e[type=minecraft:experience_orb]'),
                  'nearby drops inspected before any pickup; player XP alone cannot establish zero orb drops')
        self.must('boss_death_no_xp_or_inventory_reward',
                  xp==self.number(f'data get entity {self.s.username} XpTotal')
                  and before==inventory_counts(read_data(self.rc(f'data get entity {self.s.username} Inventory'))),
                  {'xp_before':xp,'inventory_before':before})
        self.rc(f'gamemode creative {self.s.username}');self.rc('save-all flush');self.stop();self.start(reuse=True)
        time.sleep(3)
        self.must('defeated_lair_no_respawn_after_reload',self.block().get('LairState')=='CLEARED' and self.count()==0,self.block())
        self.report['lethal_preparation']='administrator Health=1; killing blow actual mapped sword'

    def run_case(self, name, operation):
        started = time.monotonic()
        self.report['cases'][name] = {'result':'running'}
        self.save()
        try:
            operation()
            self.report['completed'].append(name)
            self.report['unrun'].remove(name)
            self.report['cases'][name]['result'] = 'pass'
        except BaseException as error:
            self.report['cases'][name].update(result='fail',error=repr(error))
            raise
        finally:
            self.report['cases'][name]['wall_s'] = round(time.monotonic()-started,3)
            self.save()


    def run(self):
        self.start();self.natural()
        for name in self.selected:
            if name not in ('entry','visuals') and 'pristine_world' not in self.report:
                self.export_world(pristine=True)
            if name=='death':self.export_world(pristine=False)
            self.run_case(name,getattr(self,name))
        if 'pristine_world' not in self.report:self.export_world(pristine=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases',default='all')
    parser.add_argument('--seed',type=int,default=20261002)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/thunder_tiger/runtime')
    args=parser.parse_args()
    evidence=TigerEvidence(args.out,args.cases,args.seed)
    try:
        evidence.run();evidence.report['status']='pass'
    except BaseException as error:
        evidence.report['status']='fail';evidence.report['failures'].append(repr(error));raise
    finally:
        if evidence.s:
            try:evidence.rc('tick unfreeze');evidence.rc('save-on');evidence.stop()
            except BaseException as error:evidence.report['failures'].append('cleanup: '+repr(error))
        evidence.save()
    return int(bool(evidence.report['failures']))


if __name__=='__main__':
    raise SystemExit(main())
