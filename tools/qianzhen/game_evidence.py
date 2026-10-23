#!/usr/bin/env python3
"""阡镇六处原生 MC 验收；仅在显式隔离云端 checkout 启动游戏。"""
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
from tools.modal_runner.remote import select_qianzhen_sites
from tools.qianzhen.evidence_candidate import Candidate, DEFAULT_REQUEST, add_arguments, runtime_request

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


def day_frame_plan(d):
    """原生完整结构镜头；review_views 的 remove 只属于离线预览。"""
    w, h, l = d['size']
    presentation = d['presentation']
    slug = presentation['role']
    shots = [
        {'name': 'exterior', 'pos': [w+15, h+14, -25], 'target': [w/2, h*.35, l/2]},
        {'name': 'rear', 'pos': [-16, h+12, l+23], 'target': [w/2, h*.38, l/2]},
        {'name': 'entry', 'pos': [presentation['eye'][0], presentation['eye'][1]-1.62,
                                 presentation['eye'][2]], 'target': presentation['eye_target']},
    ]
    key, target = INTERIORS[slug]
    shots.append({'name': 'interior', 'pos': centered(d['walk_targets'][key]), 'target': target})
    if slug == 'spring_array':
        shots[-1]['evidence_scope'] = '旧固定镜头；不能作为新维护室视角证据'
        for view in presentation.get('review_views', []):
            if view['id'] == 'maintenance_interior':
                shots.append({'name': 'maintenance_interior_uncut',
                              'pos': [view['eye'][0], view['eye'][1]-1.62, view['eye'][2]],
                              'target': view['target'],
                              'evidence_scope': '完整未剖原生维护室；仅相机夹具，不证明映射行动'})
    return shots


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
    def __init__(self, out, walk_locations=None, facilities_only=False, *,
                 candidate=None, request=DEFAULT_REQUEST, provider="Modal"):
        self.candidate = Candidate(candidate, ROOT)
        self.request_path, self.provider = request, provider
        if candidate and out.exists() and any(out.iterdir()):
            raise ValueError("Keep prior failures; use an empty evidence directory")
        Evidence.__init__(self, out)
        manifest = self.candidate.source/'manifest.json'
        self.spec = json.loads(manifest.read_text())
        self.buildings = []
        self.blocks = {}
        self.graphs = {}
        self.routes = {}
        self.source_hashes = {}
        self.held = set()
        self.hud_hidden = False
        self.recorder = None
        self.facilities_only = facilities_only
        self.walk_locations = select_qianzhen_sites(walk_locations)
        self.walk_phase = 'mapped_all_walk_targets' if len(self.walk_locations)==6 else 'mapped_selected_walk_targets'
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
        phases = [self.walk_phase if name=='mapped_all_walk_targets' else name for name in PHASES]
        if facilities_only:
            phases = ['place_six', 'mapped_existing_facilities']
        self.report.update(kind='qianzhen_native_mc', selected=phases, completed=[], unrun=phases.copy(),
            status='running', buildings={}, inputs=[], tours={}, method=__doc__,
            day_frame_plan={d['presentation']['role']: day_frame_plan(d) for d in self.buildings},
            candidate=self.candidate.identity, provider=self.provider,
            walk_locations=self.walk_locations, walk_scope='all' if len(self.walk_locations)==6 else 'selected_subset',
            movement='One administrator start outside each entrance; actual W/A/S/D throughout each tour, no teleport between targets, no jump',
            doors='Original closed state saved first; each door opened with actual right mouse before the walking tour',
            software_rendering=True, owner_appearance='not_verified', owner_fps='not_verified',
            npc_and_quest_runtime='not_implemented', fixture='independent superflat showcase, no user save modified')
        if facilities_only:
            self.report.update(walk_locations=[], walk_scope='not_selected',
                               scope='facilities_only; no new movement, export or exterior claims')

    def complete(self, phase):
        self.report['completed'].append(phase)
        self.report['unrun'] = [x for x in self.report['selected'] if x not in self.report['completed']]
        self.save()

    def start(self):
        request = runtime_request(ROOT, self.request_path, self.provider, self.candidate)
        pack_config = self.candidate.prepare_pack(self.out)
        self.report.update(source_commit=request['commit'], candidate=self.candidate.identity,
                           request_sha256=hashlib.sha256(Path(self.request_path).read_bytes()).hexdigest())
        cs.merge_options(cs.CLIENT_DIR/'options.txt', KEYS)
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn',
            lock=str(procs.default_lock_path(ROOT)), framebuffer_width=1280, framebuffer_height=720, **pack_config))
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
        self.verify_candidate_pack()
        self.report['mod_version'] = next(x.split('=',1)[1] for x in (ROOT/'gradle.properties').read_text().splitlines() if x.startswith('mod_version='))
        self.save()

    def verify_candidate_pack(self):
        if self.candidate.folder:
            self.candidate.verify_installed(cs.SERVER_DIR/cs.LEVEL_NAME)
            reply = self.rc('datapack list enabled')
            enabled = 'file/qianzhen_candidate' in reply
            self.check('candidate_pack_enabled_'+str(self.sessions), enabled, reply)
            if not enabled:
                raise ValueError('Candidate startup datapack is not enabled')
            self.report['candidate_pack_hashes'] = self.candidate.pack_hashes.copy()
            self.report['runtime_resources'] = 'hash-pinned world startup datapack; Gradle development classpath for mod code'

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
            shutil.copyfile(self.candidate.template(d, ROOT/'out/preview/qianzhen-blueprints'), target/(stem+'.nbt'))
            if self.candidate.folder:
                matches = (hashlib.sha256((target/(stem+'.nbt')).read_bytes()).hexdigest() ==
                           self.candidate.identity['resource_sha256']['data/myvillage/structure/qianzhen/'+slug+'.nbt'])
                self.check(slug+'_candidate_template_bytes', matches,
                           {'template':d['id'], 'candidate':self.candidate.identity['scene_version']})
                if not matches:
                    raise ValueError('Placed template bytes differ from candidate: '+slug)
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
        slug = d['presentation']['role']
        self.rc('time set 6000')
        shots = day_frame_plan(d)
        for shot in shots:
            self.photograph(d,shot)
        if slug in ('relay_inn','apothecary','spring_array'):
            self.rc('time set 18000')
            night = dict(shots[0],name='night')
            self.photograph(d,night)
            self.rc('time set 6000')

    def facilities(self,d):
        """Wait for rendered menus; pointer release alone can precede their frame."""
        u = self.s.username
        self.set_hud(False)
        self.rc(f'gamemode survival {u}')
        for service in d.get('service_blocks',[]):
            p = d['walk_targets'][service['approach']]
            pos = self.xyz(d,centered(p))
            target = self.xyz(d,[v+.5 for v in service['pos']])
            dx,dz = pos[0]-target[0],pos[2]-target[2]
            length = math.hypot(dx,dz)
            pos[0] = target[0]+2.2*dx/length
            pos[2] = target[2]+2.2*dz/length
            self.rc(f'clear {u}')
            yaw,pitch = aim(pos,target)
            self.teleport(pos,yaw,pitch)
            folder = self.out/d['presentation']['role']
            name = service['approach']
            self.s.game.shot(folder/(name+'-before-use.png'),max_wait=2,settle=.5)
            before = self.rc(f'myvillage cultivation info {u}') if 'inheritance_stele' in service['state'] else None
            video = folder/(name+'-interaction.mp4')
            self.recorder = subprocess.Popen(ffmpeg_record_argv(self.s.state['display'],video,30,fps=12,size=self.s.game.size),
                stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=(folder/(name+'-video.log')).open('w'))
            try:
                self.s.game.require_ingame('mapped facility use')
                self.s.game.xdo('click','3')
                self.report['inputs'].append({'action':'right_mouse','purpose':name,'block':self.xyz(d,service['pos'])})
                if before is not None:
                    after = self.poll(lambda:self.rc(f'myvillage cultivation info {u}'),
                        lambda s:'myvillage:basic_breathing' in s,10,'mapped inheritance')
                    self.check(d['id']+'_mapped_inheritance','myvillage:basic_breathing' not in before,
                               {'before':before,'after':after,'video':str(video.relative_to(self.out))})
                    time.sleep(1)
                else:
                    self.poll(self.s.game.pointer_grabbed,lambda p:p is False,10,'mapped facility UI')
                    # Observed software-rendering race: the first released-pointer
                    # frame still showed the world. Keep the menu open and retain
                    # two delayed raw frames plus video for actual visual review.
                    time.sleep(2)
                    captures = []
                    window = self.s.game.xdo('search','--name','Minecraft').split()[0]
                    for suffix in ('-ui.png','-ui-confirm.png'):
                        path = folder/(name+suffix)
                        subprocess.run(['import','-silent','-window',window,str(path)],
                                       env=self.s.game.env,check=True,timeout=30)
                        captures.append(str(path.relative_to(self.out)))
                        if self.s.game.pointer_grabbed() is not False:
                            raise AssertionError('Facility menu did not stay open: '+name)
                        time.sleep(.7)
                    self.check(d['id']+'_mapped_'+name,True,{'block':service['state'],
                        'captures':captures,'video':str(video.relative_to(self.out)),
                        'render_settle_seconds':2,'visual_review':'required'})
                    self.s.game.xdo('key','Escape')
                    self.poll(self.s.game.pointer_grabbed,bool,8,'close facility UI')
                    time.sleep(.3)
            finally:
                result = stop_recording(self.recorder)
                self.recorder = None
                if result:
                    raise RuntimeError('facility recording failed: '+name)

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
            walk_link = f'<a href="{slug}/walkthrough.mp4">完整按键行走录像</a>' if (self.out/slug/'walkthrough.mp4').exists() else '本次未选择此地点步行'
            interior_label = '旧固定室内镜头；不是新维护室视角' if slug == 'spring_array' else '实际游戏室内'
            extra = ''
            if any(s['name'] == 'maintenance_interior_uncut' for s in day_frame_plan(d)):
                extra = (f'<p>新阵庭眼位（完整未剖）</p><img src="{slug}/entry.png" alt="原生阵庭新眼位">'
                         f'<p>维护室补拍（完整未剖；相机夹具）</p><img src="{slug}/maintenance_interior_uncut.png" alt="原生维护室补拍">')
            cards.append(f'<article><h2>{html.escape(d["presentation"]["name"])}</h2><img src="{slug}/exterior.png" alt="实际游戏外观"><p>{interior_label}</p><img src="{slug}/interior.png" alt="{interior_label}">{extra}<p>{walk_link} · <a href="{slug}/{stem}.nbt">游戏保存 NBT</a> · <a href="{slug}/{stem}.schem">SCHEM</a></p></article>')
        page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>阡镇 · MC 内验收</title><style>*{box-sizing:border-box}body{margin:0;background:#eee9dd;color:#343f35;font:15px/1.8 system-ui}main{max-width:1200px;margin:auto;padding:24px}.cards{display:grid;grid-template-columns:1fr 1fr;gap:20px}article{padding:16px;border-radius:10px;background:#faf7ee}article img{width:100%;display:block;margin:10px 0}a{color:#3f6c59}h2{font-size:20px}@media(max-width:650px){.cards{grid-template-columns:1fr}}</style><main><h1>阡镇 · 原生 MC 验收</h1><p>画面来自真实 NeoForge 客户端。独立平坦测试世界；原建筑闭门状态先由结构方块 SAVE 并逐格回读，再用实际右键开门、W/A/S/D 完成连续路线。没有修改用户存档。</p><p><a href="qianzhen_game_blueprints.zip">游戏蓝图和证据包</a> · <a href="qianzhen_showcase_world.zip">独立测试世界</a> · <a href="evidence.json">逐项结果</a></p><p>这是软件渲染的开发者验收；玩家审美、手感、声音、FPS、多人仍未验证。NPC、主线触发和自然生成尚未接入。</p><div class="cards">'''+''.join(cards)+'''</div></main></html>'''
        page = page.replace('<h1>阡镇 · 原生 MC 验收</h1>', '<h1>阡镇 · 原生 MC 验收</h1><p>本次步行选择：'+ '、'.join(d['presentation']['name'] for d in self.buildings if d['presentation']['role'] in self.walk_locations) +'。其他地点的通过证据需要按源文件身份单独复用，不能视为本次重新验证。</p>')
        (self.out/'index.html').write_text(page)
        with zipfile.ZipFile(self.out/'qianzhen_game_blueprints.zip','w',zipfile.ZIP_DEFLATED) as z:
            for p in sorted(self.out.rglob('*')):
                if p.is_file() and p.suffix in ('.nbt','.schem','.json','.png','.html'):
                    z.write(p,p.relative_to(self.out))
        paths=[p for p in self.out.rglob('*') if p.is_file() and p.suffix in ('.zip','.nbt','.schem','.mp4')]
        (self.out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(self.out))+'\n' for p in sorted(paths)))

    def run(self):
        self.start(); self.setup()
        if self.facilities_only:
            for d in self.buildings:
                self.facilities(d)
            self.complete('mapped_existing_facilities')
            self.stop()
            self.report['status']='pass'
            self.save()
            return
        for d in self.buildings:
            self.save_building(d)
        self.complete('native_structure_save_six')
        for d in self.buildings:
            self.doors(d)
        self.complete('mapped_doors')
        for d in self.buildings:
            if d['presentation']['role'] in self.walk_locations:
                self.walk_tour(d)
        self.complete(self.walk_phase)
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
    parser.add_argument('--walk-locations',help='只复验指定地点的连续步行；放置、保存、设施和画面仍覆盖六处')
    parser.add_argument('--facilities-only',action='store_true',help='仅复验既有设施交互，不重复动线和原生导出')
    add_arguments(parser)
    parser.add_argument("--plan", action="store_true", help="仅校验候选并规划路线，不启动游戏")
    args=parser.parse_args()
    if args.facilities_only and args.walk_locations:
        parser.error('--facilities-only cannot be combined with --walk-locations')
    e=QianzhenEvidence(args.out,args.walk_locations,args.facilities_only,
                       candidate=args.candidate, request=args.request, provider=args.provider)
    try:
        if args.plan:
            e.report["status"] = "ready_not_run"
            e.save()
        else:
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
