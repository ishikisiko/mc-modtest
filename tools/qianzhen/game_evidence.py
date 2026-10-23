#!/usr/bin/env python3
"""阡镇六处原生 MC 验收；仅在隔离 Modal checkout 启动游戏。"""
from __future__ import annotations
import argparse
import hashlib
import html
import json
import math
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT/'tools'))
import json_to_nbt as nbt
from tools.realm_stats_evidence import Evidence
from tools.combat_capture import session as cs, procs, scene
from tools.combat_capture.capture import ffmpeg_record_argv, stop_recording
from tools.alchemy_quality_evidence import read_data
from tools.yunshan.game_evidence import BuildingEvidence, aim
from tools.qianzhen.build import walk_graph, block_at
from tools.qianzhen.game_routes import tour, traversal_ends, movement_key, opened_door_graph, walk_point

PHASES = ['place_six', 'native_structure_save_six', 'mapped_doors',
          'mapped_all_walk_targets', 'mapped_existing_facilities', 'day_night_frames', 'saved_world']
KEYS = {'key_key.forward': 'key.keyboard.w', 'key_key.back': 'key.keyboard.s',
        'key_key.left': 'key.keyboard.a', 'key_key.right': 'key.keyboard.d',
        'key_key.sneak': 'key.keyboard.left.shift', 'key_key.use': 'key.mouse.right',
        'autoJump': 'false'}
INTERIORS = {
    'relay_inn': ('tea_hall', [22, 3, 34]),
    'apothecary': ('pharmacist', [19, 3, 8]),
    'canal_gardens': ('middle_east', [25, 9, 35]),
    'old_relay': ('front_court', [33, 5, 25]),
    'spring_array': ('east_descent_foot', [23, 4, 27]),
    'initiation_court': ('forecourt', [15, 6, 29]),
}


def centered(p):
    return [p[0]+.5, p[1], p[2]+.5]


def door_stance(blocks, graph, position, props):
    """Pick a clear administrator start for a real mouse interaction."""
    x, y, z = position
    dx, dz = {'north': (0,-1), 'south': (0,1), 'east': (1,0), 'west': (-1,0)}[props['facing']]
    for distance in (2, 1):
        for sign in (1, -1):
            p = (x+dx*distance*sign, y, z+dz*distance*sign)
            if p not in graph or block_at(blocks, p)[0] != 'minecraft:air':
                continue
            if all(block_at(blocks, (x+dx*i*sign,y,z+dz*i*sign))[0] == 'minecraft:air'
                   for i in range(1, distance+1)):
                return centered(p)
    raise ValueError(f'No clear door interaction stance at {position}')


class QianzhenEvidence(BuildingEvidence):
    def __init__(self, out):
        Evidence.__init__(self, out)
        manifest = ROOT/'blueprints/qianzhen/manifest.json'
        self.spec = json.loads(manifest.read_text())
        self.buildings = []
        self.blocks = {}
        self.graphs = {}
        self.routes = {}
        self.source_hashes = {}
        self.held = set()
        self.hud_hidden = False
        self.recorder = None
        self.position_stream = (out/'movement-readbacks.jsonl').open('w')
        for i, row in enumerate(self.spec['locations']):
            path = manifest.parent/row['source']
            d = json.loads(path.read_text())
            self.source_hashes[d['id']] = hashlib.sha256(path.read_bytes()).hexdigest()
            d['site']['world_min'] = [(i % 3)*72, -61, (i//3)*72]
            self.buildings.append(d)
            _, _, blocks, _ = nbt.expand_structure(d)
            graph = opened_door_graph(walk_graph(blocks, d['size']), blocks)
            self.blocks[d['id']] = blocks
            self.graphs[d['id']] = graph
            self.routes[d['id']] = tour(graph, d['walk_targets'])
        self.report.update(kind='qianzhen_native_mc', selected=PHASES, completed=[], unrun=PHASES.copy(),
            status='running', buildings={}, inputs=[], tours={}, method=__doc__,
            movement='One administrator start outside each entrance; actual W/A/S/D throughout each tour, no teleport between targets, no jump',
            doors='Original closed state saved first; each door opened with actual right mouse before the walking tour',
            software_rendering=True, owner_appearance='not_verified', owner_fps='not_verified',
            npc_and_quest_runtime='not_implemented', fixture='independent superflat showcase, no user save modified')

    def complete(self, phase):
        self.report['completed'].append(phase)
        self.report['unrun'] = [x for x in PHASES if x not in self.report['completed']]
        self.save()

    def start(self):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Minecraft acceptance is restricted to the Modal disposable /workspace/repo checkout')
        cs.merge_options(cs.CLIENT_DIR/'options.txt', KEYS)
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn',
            lock=str(procs.default_lock_path(ROOT)), framebuffer_width=1280, framebuffer_height=720))
        self.s = cs.Session.attach(ui_check=True)
        self.sessions += 1
        window = self.s.game.xdo('search', '--name', 'Minecraft').split()[0]
        self.s.game.xdo('windowsize', '--sync', window, '1280', '720')
        self.s.game.xdo('windowmove', '--sync', window, '0', '0')
        self.s.game.size = (1280, 720)
        time.sleep(.8)
        self.report['phases'].append({'phase':'client_server_start', 'capture_timings':state.get('timings',{})})
        options = dict(line.split(':',1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines() if ':' in line)
        self.check('explicit_input_bindings', all(options.get(k)==v for k,v in KEYS.items()), {k:options.get(k) for k in KEYS})
        self.report['graphics'] = subprocess.check_output(['glxinfo','-B'], env=self.s.game.env, text=True, timeout=20)
        self.report['source_commit'] = json.loads(Path('/workspace/request.json').read_text())['commit']
        self.report['mod_version'] = next(x.split('=',1)[1] for x in (ROOT/'gradle.properties').read_text().splitlines() if x.startswith('mod_version='))
        self.save()

    def player_pos(self):
        reply = self.s.run(f'data get entity {self.s.username} Pos', warn=False)[0]
        p = read_data(reply)
        self.position_stream.write(json.dumps({'time':round(time.monotonic(),4),'reply':reply,'position':p})+'\n')
        return p

    def setup(self):
        u = self.s.username
        for cmd in (*scene.world_rules(), f'op {u}', f'clear {u}', f'gamemode creative {u}',
                    f'effect give {u} minecraft:saturation infinite 255 true',
                    'gamerule doFireTick false', 'gamerule randomTickSpeed 0', 'myvillage world pause',
                    'forceload add -16 -16 208 128', 'defaultgamemode creative', 'setworldspawn 23 -60 -5 0'):
            self.rc(cmd)
        pack = cs.SERVER_DIR/cs.LEVEL_NAME/'datapacks/qianzhen_acceptance'
        target = pack/'data/myvillage/structure'
        target.mkdir(parents=True, exist_ok=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'阡镇独立蓝图验收场景'}}))
        for d in self.buildings:
            slug = d['presentation']['role']
            stem = d['id'].split(':')[1]
            shutil.copyfile(ROOT/'out/preview/qianzhen-blueprints'/slug/(stem+'.nbt'), target/(stem+'.nbt'))
        self.rc('reload')
        self.poll(lambda:self.rc('datapack list enabled'), lambda r:'qianzhen_acceptance' in r, 20, 'datapack ready')
        for d in self.buildings:
            slug = d['presentation']['role']
            (self.out/slug).mkdir(exist_ok=True)
            origin = d['site']['world_min']; x,y,z = origin; w,h,l = d['size']
            self.poll(lambda:self.rc(f'execute if loaded {x} {y} {z}'), lambda r:'passed' in r, 20, 'plot loaded')
            self.rc(f'fill {x-3} {y} {z-5} {x+w+2} {y} {z+l+2} minecraft:sandstone')
            entry = d['walk_targets']['entry']; ex,ey,ez = self.xyz(d, entry)
            self.rc(f'fill {ex-2} {ey-1} {ez-4} {ex+2} {ey-1} {ez-1} minecraft:cut_sandstone')
            self.report['buildings'][d['id']] = {'name':d['presentation']['name'],'size':d['size'],
                'origin':origin,'source_sha256':self.source_hashes[d['id']],'screenshots':[]}
            reply = self.rc(f'place template {d["id"]} '+' '.join(map(str,origin)))
            pos, block = next((p,s) for p,s in sorted(self.blocks[d['id']].items(), key=lambda item:(item[0][1],item[0][0],item[0][2])) if s!='minecraft:air')
            point = self.xyz(d,pos); state = block.split('[')[0]
            probe = self.poll(lambda:self.rc('execute if block '+' '.join(map(str,point))+' '+state),lambda r:'passed' in r,10,'native placement')
            self.check(slug+'_placed', True, {'reply':reply,'probe':probe})
        time.sleep(2)
        self.complete('place_six')

    def input_keys(self, want):
        want = set(want)
        for key in sorted(self.held-want):
            self.s.game.xdo('keyup', key)
            self.report['inputs'].append({'time':round(time.monotonic(),4),'action':'keyup','key':key})
        for key in sorted(want-self.held):
            self.s.game.xdo('keydown', key)
            self.report['inputs'].append({'time':round(time.monotonic(),4),'action':'keydown','key':key})
        self.held = want

    def travel_to(self, target):
        initial = self.player_pos()
        primary_axis = max((0,2), key=lambda i:abs(target[i]-initial[i]))
        cross_axis = 2 if primary_axis == 0 else 0
        deadline = time.monotonic()+max(10, 5+2.5*math.dist(initial,target))
        samples = []
        while time.monotonic()<deadline:
            p = self.player_pos()
            samples.append(p)
            errors = {i:target[i]-p[i] for i in (0,2)}
            # Align before crossing a narrow opening. Longitudinal distance must
            # not hide a small lateral error against an opened door panel.
            axis = cross_axis if abs(errors[cross_axis])>.05 else primary_axis
            distance = abs(errors[axis])
            if max(abs(value) for value in errors.values()) < .05:
                self.input_keys([])
                time.sleep(.18)
                p = self.player_pos()
                if max(abs(p[i]-target[i]) for i in (0,2))<.085 and abs(p[1]-target[1])<.60:
                    return {'start':initial,'target':target,'final':p,'samples':samples}
                continue
            key = movement_key(axis, errors[axis])
            self.input_keys([key,'Shift_L'] if distance < .85 else [key])
            time.sleep(.07)
        self.input_keys([])
        raise AssertionError(f'mapped route stalled: start={initial} target={target} actual={self.player_pos()}')

    def doors(self, d):
        slug = d['presentation']['role']; u = self.s.username
        self.set_hud(False); self.rc(f'gamemode survival {u}')
        count = 0
        for pos,state in self.blocks[d['id']].items():
            name, props = nbt.parse_block_state(state)
            if not name.endswith('_door') or props.get('half')!='lower':
                continue
            stance = door_stance(self.blocks[d['id']], self.graphs[d['id']],pos,props)
            player = self.xyz(d,stance); block = self.xyz(d,pos)
            target = [block[0]+.5,block[1]+.8,block[2]+.5]
            yaw,pitch = aim(player,target)
            self.teleport(player,yaw,pitch)
            time.sleep(.3)
            self.s.game.require_ingame('mapped door opening')
            self.s.game.xdo('click','3')
            self.report['inputs'].append({'action':'right_mouse','purpose':'open_door','block':block})
            reply = self.poll(lambda:self.rc('execute if block '+' '.join(map(str,block))+' '+name+'[open=true]'),lambda r:'passed' in r,8,'door opened')
            self.check(slug+'_door_'+str(count),True,{'position':block,'reply':reply,'input':'mapped right mouse'})
            count += 1
        self.report['buildings'][d['id']]['mapped_doors'] = count

    def walk_tour(self, d):
        slug = d['presentation']['role']; entry = d['walk_targets']['entry']
        self.set_hud(False); self.rc(f'gamemode survival {self.s.username}')
        start = centered(entry); start[2] -= 3
        self.teleport(self.xyz(d,start),0,0)
        time.sleep(.35)
        self.s.game.require_ingame('continuous mapped movement tour')
        video = self.out/slug/'walkthrough.mp4'
        self.recorder = subprocess.Popen(ffmpeg_record_argv(self.s.state['display'],video,1200,fps=12,size=self.s.game.size),
            stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=(self.out/slug/'video.log').open('w'))
        rows = []
        self.report['tours'][slug] = rows
        try:
            trial = self.travel_to(self.xyz(d,centered(entry)))
            rows.append({'targets':['entry'],'legs':[trial]})
            self.check(slug+'_walk_entry',True,{'final':trial['final'],'input':'W from outside prepared entry apron'})
            for step in self.routes[d['id']]:
                legs = []
                for endpoint in traversal_ends(step['path'],self.blocks[d['id']]):
                    point = walk_point(endpoint,self.blocks[d['id']])
                    legs.append(self.travel_to(self.xyz(d,point)))
                final = self.player_pos()
                expected = self.xyz(d,centered(step['path'][-1]))
                passed = max(abs(final[i]-expected[i]) for i in (0,2))<.35 and abs(final[1]-expected[1])<.6
                rows.append({'targets':step['targets'],'path':step['path'],'legs':legs,'final':final})
                for name in step['targets']:
                    self.check(slug+'_walk_'+name,passed,{'expected':expected,'actual':final,'input':'mapped W/A/S/D; no teleport or jump during tour'})
                self.position_stream.flush()
            self.s.game.shot(self.out/slug/'walk-complete.png',max_wait=2,settle=.3)
        finally:
            self.input_keys([])
            result = stop_recording(self.recorder)
            self.recorder = None
            if result:
                raise RuntimeError('walkthrough recording failed: '+slug)
        self.report['buildings'][d['id']]['mapped_targets'] = list(d['walk_targets'])
        self.save()

    def frames(self,d):
        w,h,l = d['size']; slug = d['presentation']['role']
        self.rc('time set 6000')
        shots = [
            {'name':'exterior','pos':[w+15,h+14,-25],'target':[w/2,h*.35,l/2]},
            {'name':'rear','pos':[-16,h+12,l+23],'target':[w/2,h*.38,l/2]},
            {'name':'entry','pos':[d['presentation']['eye'][0],d['presentation']['eye'][1]-1.62,d['presentation']['eye'][2]],'target':d['presentation']['eye_target']},
        ]
        key,target = INTERIORS[slug]
        shots.append({'name':'interior','pos':centered(d['walk_targets'][key]),'target':target})
        for shot in shots:
            self.photograph(d,shot)
        if slug in ('relay_inn','apothecary','spring_array'):
            self.rc('time set 18000')
            night = dict(shots[0],name='night')
            self.photograph(d,night)
            self.rc('time set 6000')

    def package_world(self):
        world = cs.SERVER_DIR/cs.LEVEL_NAME
        if not world.exists():
            return
        with zipfile.ZipFile(self.out/'qianzhen_showcase_world.zip','w',zipfile.ZIP_DEFLATED) as z:
            for p in sorted(world.rglob('*')):
                rel = p.relative_to(world)
                if p.is_file() and not p.is_symlink() and not any(part in ('playerdata','stats','advancements','session.lock') for part in rel.parts):
                    z.write(p,'Qianzhen MC Acceptance/'+str(rel))

    def page(self):
        cards = []
        for d in self.buildings:
            slug = d['presentation']['role']; stem = d['id'].split(':')[1]+'_game'
            cards.append(f'<article><h2>{html.escape(d["presentation"]["name"])}</h2><img src="{slug}/exterior.png" alt="实际游戏外观"><img src="{slug}/interior.png" alt="实际游戏室内"><p><a href="{slug}/walkthrough.mp4">完整按键行走录像</a> · <a href="{slug}/{stem}.nbt">游戏保存 NBT</a> · <a href="{slug}/{stem}.schem">SCHEM</a></p></article>')
        page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>阡镇 · MC 内验收</title><style>*{box-sizing:border-box}body{margin:0;background:#eee9dd;color:#343f35;font:15px/1.8 system-ui}main{max-width:1200px;margin:auto;padding:24px}.cards{display:grid;grid-template-columns:1fr 1fr;gap:20px}article{padding:16px;border-radius:10px;background:#faf7ee}article img{width:100%;display:block;margin:10px 0}a{color:#3f6c59}h2{font-size:20px}@media(max-width:650px){.cards{grid-template-columns:1fr}}</style><main><h1>阡镇 · 原生 MC 验收</h1><p>画面来自真实 NeoForge 客户端。独立平坦测试世界；原建筑闭门状态先由结构方块 SAVE 并逐格回读，再用实际右键开门、W/A/S/D 完成连续路线。没有修改用户存档。</p><p><a href="qianzhen_game_blueprints.zip">游戏蓝图和证据包</a> · <a href="qianzhen_showcase_world.zip">独立测试世界</a> · <a href="evidence.json">逐项结果</a></p><p>这是软件渲染的开发者验收；玩家审美、手感、声音、FPS、多人仍未验证。NPC、主线触发和自然生成尚未接入。</p><div class="cards">'''+''.join(cards)+'''</div></main></html>'''
        (self.out/'index.html').write_text(page)
        with zipfile.ZipFile(self.out/'qianzhen_game_blueprints.zip','w',zipfile.ZIP_DEFLATED) as z:
            for p in sorted(self.out.rglob('*')):
                if p.is_file() and p.suffix in ('.nbt','.schem','.json','.png','.html'):
                    z.write(p,p.relative_to(self.out))
        paths=[p for p in self.out.rglob('*') if p.is_file() and p.suffix in ('.zip','.nbt','.schem','.mp4')]
        (self.out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(self.out))+'\n' for p in sorted(paths)))

    def run(self):
        self.start(); self.setup()
        for d in self.buildings:
            self.save_building(d)
        self.complete('native_structure_save_six')
        for d in self.buildings:
            self.doors(d)
        self.complete('mapped_doors')
        for d in self.buildings:
            self.walk_tour(d)
        self.complete('mapped_all_walk_targets')
        for d in self.buildings:
            self.facilities(d)
        self.complete('mapped_existing_facilities')
        for d in self.buildings:
            self.frames(d)
        self.complete('day_night_frames')
        self.set_hud(False)
        self.rc(f'gamemode creative {self.s.username}')
        self.teleport([23.5,-60,-4.5],0,0)
        self.rc('save-all flush')
        self.stop(); self.package_world()
        self.complete('saved_world')
        self.report['status']='pass'; self.save(); self.page()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/qianzhen-mc-runtime')
    args=parser.parse_args()
    e=QianzhenEvidence(args.out)
    try:
        e.run()
    except BaseException as error:
        e.report['status']='fail';e.report['failures'].append(repr(error));e.save()
        if e.s:
            try:
                e.input_keys([])
                window=e.s.game.xdo('search','--name','Minecraft').split()[0]
                subprocess.run(['import','-silent','-window',window,str(e.out/'failure.png')],env=e.s.game.env,timeout=20,check=True)
                e.rc('save-all flush')
            except Exception as capture_error:
                e.report['failures'].append('failure capture: '+repr(capture_error))
        raise
    finally:
        if e.recorder is not None:
            stop_recording(e.recorder)
        if e.s:
            try:
                e.input_keys([])
            finally:
                e.stop()
            e.package_world()
        e.position_stream.close()
        e.save()


if __name__=='__main__':
    main()
