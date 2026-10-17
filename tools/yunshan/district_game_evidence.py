#!/usr/bin/env python3
"""Place the complete Yunshan district in disposable Modal Minecraft and export its world.

Commands only prepare fixtures and read server state. Traversal and facility
activation use the actual client's mapped W and right mouse bindings.
"""
from __future__ import annotations

import argparse
import copy
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
from tools.realm_stats_evidence import Evidence
from tools.json_to_nbt import String
from tools.yunshan.game_evidence import BuildingEvidence
from tools.yunshan.build_functional import rotated_state
from tools.yunshan.game_export import read_typed, write_typed
from tools.yunshan.preview_site import translate_manifest
from tools.combat_capture import session as cs, scene, procs


def world_position(building, source_size, pos, *, block=False):
    """Rotate continuous player/camera coordinates around the source voxel edges.

    Block indices rotate around width-1; continuous positions around width.
    Keeping these distinct prevents half-block shifts after a quarter turn.
    """
    x, y, z = pos
    width, _, depth = source_size
    edge = 1 if block else 0
    for _ in range(building['rotation_quarters'] % 4):
        x, z = depth-edge-z, x
        width, depth = depth, width
    return [building['world_min'][i]+v for i, v in enumerate((x, y, z))]


def chunk_rectangle(origin, size):
    """Return a bounded forceload rectangle, including negative chunk coordinates."""
    x, _, z = origin
    end_x, end_z = x+size[0]-1, z+size[2]-1
    count = (end_x//16-x//16+1)*(end_z//16-z//16+1)
    if not 0 < count <= 256:
        raise ValueError(f'Tile requires {count} chunks; maximum is 256')
    return [x, z, end_x, end_z]


def traversal_passed(start, end, current, min_y):
    dx, dz = end[0]-start[0], end[2]-start[2]
    length = math.hypot(dx, dz)
    if length == 0:
        raise ValueError('Traversal needs horizontal displacement')
    along = ((current[0]-start[0])*dx+(current[2]-start[2])*dz)/length
    across = abs((current[0]-start[0])*dz-(current[2]-start[2])*dx)/length
    return along >= length-.15 and across <= .85 and current[1] >= min_y-.1


def package_world(world, output, *, level_name='云山 D1 v2 · 完整街区'):
    """Keep the entire stopped world, including mod attachments and player state."""
    if not (world/'level.dat').is_file():
        raise FileNotFoundError('Stopped Minecraft world has no level.dat')
    # Only the display name changes. In particular, Data/attachments, dimensions,
    # playerdata, stats and advancements must survive the delivery unchanged.
    for name in ('level.dat', 'level.dat_old'):
        path = world/name
        if path.is_file():
            root_name, root = read_typed(path)
            data = root.value['Data'].value
            data['LevelName'] = String(level_name)
            write_typed(path, root_name, root)
    files = []
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(world.rglob('*')):
            relative = path.relative_to(world)
            if not path.is_file() or path.is_symlink() or path.name == 'session.lock':
                continue
            if not path.resolve().is_relative_to(world.resolve()):
                raise ValueError(f'World export escaped its root: {relative}')
            archive.write(path, 'Yunshan D1 v2/'+relative.as_posix())
            files.append(relative.as_posix())
    with zipfile.ZipFile(output) as archive:
        bad = archive.testzip()
        if bad:
            raise AssertionError(f'World ZIP CRC failure: {bad}')
    return {'file': output.name, 'files': len(files),
            'sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
            'preserves': ['dimensions', 'data', 'playerdata', 'stats', 'advancements', 'mod attachments'],
            'excluded': ['session.lock'], 'level_name': level_name}


class DistrictEvidence(BuildingEvidence):
    def __init__(self, manifest, out):
        Evidence.__init__(self, out)
        self.manifest = manifest
        self.spec = translate_manifest(json.loads(manifest.read_text()))
        self.hud_hidden = False
        self.buildings = []
        for installation in self.spec['buildings']:
            source = ROOT/'blueprints/yunshan'/installation['source']
            data = copy.deepcopy(json.loads(source.read_text()))
            data['_installation'] = installation
            data['presentation']['role'] = installation['key']
            self.buildings.append(data)
            (out/installation['key']).mkdir(exist_ok=True)
        self.report.update(kind='yunshan_d1_v2_full_district_actual_game', status='running',
            selected=['place_complete_district', 'mapped_modified_paths', 'mapped_rotated_entries',
                      'mapped_inn_stair', 'mapped_facilities', 'district_and_street_screenshots', 'playable_world_export'],
            completed=[], buildings={d['id']: {'installation': d['_installation'], 'screenshots': []} for d in self.buildings},
            screenshots=[], method='full tiled city; server block readback; actual mapped W/right mouse; actual Minecraft screenshots',
            owner_appearance='not_verified', owner_fps='not_verified', owner_feel='not_verified',
            owner_sound='not_verified', npc_services='not_verified', software_rendering=True,
            source={'manifest_sha256': hashlib.sha256(manifest.read_bytes()).hexdigest(),
                    'buildings': {d['_installation']['source']: hashlib.sha256((ROOT/'blueprints/yunshan'/d['_installation']['source']).read_bytes()).hexdigest() for d in self.buildings},
                    'tiles': {t['nbt']: hashlib.sha256((manifest.parent/t['nbt']).read_bytes()).hexdigest() for t in self.spec['tiles']}},
            source_schematic='authored/generated district source; not exported from Minecraft')
        self.report['preview_site'] = {key: self.spec[key] for key in (
            'original_world_coordinates', 'applied_preview_translation', 'world_offset', 'world_min', 'world_max')}
        self.save()

    def save(self):
        self.report['unrun'] = [case for case in self.report.get('selected', []) if case not in self.report.get('completed', [])]
        super().save()

    def xyz(self, data, local):
        return world_position(data['_installation'], data['size'], local)

    def stop(self):
        super().stop()
        state = cs.read_state() or {}
        remaining = [role for role, process in state.get('procs', {}).items()
                     if procs.is_alive(process['pid'], process.get('start'))]
        supervisor_alive = cs.supervisor_alive(state)
        self.report['capture_cleanup'] = {'phase': state.get('phase'),
            'supervisor_alive': supervisor_alive, 'remaining_process_roles': remaining}
        self.save()
        if supervisor_alive or remaining:
            raise RuntimeError('Capture cleanup left owned processes alive: '+str(remaining))

    def start(self):
        if ROOT != Path('/workspace/repo'):
            raise RuntimeError('Actual game job is restricted to the Modal disposable /workspace/repo checkout')
        began = time.monotonic()
        cs.merge_options(cs.CLIENT_DIR/'options.txt', {'key_key.forward': 'key.keyboard.w', 'key_key.use': 'key.mouse.right'})
        state = cs.start(cs.SessionConfig(session_id=secrets.token_hex(6), language='zh_cn',
                         lock=str(procs.default_lock_path(ROOT)), view_distance=16))
        self.s = cs.Session.attach(ui_check=True); self.sessions += 1
        self.report['phases'].append({'phase': 'client_server_start', 'wall_s': round(time.monotonic()-began, 3), 'capture_timings': state.get('timings', {})})
        bindings = dict(line.split(':', 1) for line in (cs.CLIENT_DIR/'options.txt').read_text().splitlines() if ':' in line)
        self.check('mapped_input_bindings', bindings.get('key_key.forward') == 'key.keyboard.w' and bindings.get('key_key.use') == 'key.mouse.right', {key: bindings.get(key) for key in ('key_key.forward', 'key_key.use', 'renderDistance')})
        self.report['graphics'] = subprocess.check_output(['glxinfo', '-B'], env=self.s.game.env, text=True, timeout=20)
        request = Path('/workspace/request.json')
        if request.is_file():
            self.report['source_commit'] = json.loads(request.read_text())['commit']
        self.report['mod_version'] = next(line.split('=', 1)[1] for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
        self.report['built_jars'] = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in (ROOT/'build/libs').glob('*.jar')}
        self.save()

    def setup(self):
        user = self.s.username
        for command in (*scene.world_rules(), f'op {user}', f'clear {user}',
                        f'gamemode spectator {user}', 'myvillage world pause',
                        'gamerule doFireTick false', 'gamerule randomTickSpeed 0',
                        'gamerule spawnRadius 0', 'defaultgamemode creative',
                        f'effect give {user} minecraft:saturation infinite 255 true'):
            self.rc(command)
        pack = cs.SERVER_DIR/cs.LEVEL_NAME/'datapacks/yunshan_d1_v2'
        pack.mkdir(parents=True, exist_ok=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Complete authored Yunshan D1 v2 district'}}))
        for tile in self.spec['tiles']:
            namespace, name = tile['id'].split(':', 1)
            relative = Path(name+'.nbt')
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Unsafe tile resource ID')
            target = pack/'data'/namespace/'structure'/relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(self.manifest.parent/tile['nbt'], target)
        self.rc('reload')
        self.poll(lambda: self.rc('datapack list enabled'), lambda reply: 'yunshan_d1_v2' in reply, 20, 'district datapack enabled')
        placed = []
        for tile in self.spec['tiles']:
            rectangle = ' '.join(map(str, chunk_rectangle(tile['world_min'], tile['size'])))
            # At most one bounded tile is forced at any time; this avoids
            # relying on the full district remaining below the 256-chunk cap.
            self.rc('forceload add '+rectangle)
            try:
                probe = tile['probe']; xyz = ' '.join(map(str, probe['pos']))
                self.poll(lambda: self.rc('execute if loaded '+xyz), lambda reply: 'passed' in reply, 30, tile['id']+' chunks loaded')
                if not placed:
                    support = [tile['world_min'][0], self.spec['world_min'][1]-1, tile['world_min'][2]]
                    grounded = self.rc('execute if block '+' '.join(map(str,support))+' minecraft:grass_block')
                    self.check('preview_base_meets_flat_world_surface', 'passed' in grounded,
                               {'below_city_base':support,'readback':grounded})
                # A differing marker makes even an all-air tile's readback
                # meaningful: pre-existing flat-world air cannot satisfy it.
                marker = 'minecraft:bedrock' if probe['state'].split('[', 1)[0] == 'minecraft:barrier' else 'minecraft:barrier'
                self.rc('setblock '+xyz+' '+marker)
                self.poll(lambda: self.rc('execute if block '+xyz+' '+marker), lambda value: 'passed' in value, 5, 'tile probe marker installed')
                reply = self.rc('place template '+tile['id']+' '+' '.join(map(str, tile['world_min'])))
                readback = self.poll(lambda: self.rc('execute if block '+xyz+' '+probe['state']),
                                     lambda value: 'passed' in value, 15, tile['id']+' actual block placement')
                placed.append({'id': tile['id'], 'probe': probe, 'overwritten_marker': marker, 'placement_reply': reply, 'block_readback': readback})
                self.report['placed_tiles'] = placed
                self.save()
            finally:
                self.rc('forceload remove '+rectangle)
        self.check('all_district_tiles_placed', len(placed) == len(self.spec['tiles']), {'tiles': len(placed), 'world_min': self.spec['world_min'], 'world_max': self.spec['world_max']})
        self.report['completed'].append('place_complete_district')
        self.save()

    def walk_world(self, name, start, end, *, min_y, timeout_s):
        self.set_hud(False)
        self.rc(f'gamemode survival {self.s.username}')
        yaw = math.degrees(math.atan2(-(end[0]-start[0]), end[2]-start[2]))
        self.teleport(start, yaw, 0)
        self.s.game.require_ingame('district physical mapped W traversal')
        positions = []; began = time.monotonic()
        self.s.game.xdo('keydown', 'w')
        try:
            while time.monotonic()-began < timeout_s:
                current = self.player_pos(); positions.append(current)
                if traversal_passed(start, end, current, min_y):
                    break
                time.sleep(.12)
        finally:
            self.s.game.xdo('keyup', 'w')
        final = self.player_pos()
        self.check(name, traversal_passed(start, end, final, min_y),
                   {'start': start, 'end': end, 'min_y': min_y, 'final': final,
                    'positions': positions, 'input': 'actual mapped W; no jump', 'elapsed_s': round(time.monotonic()-began, 3)})
        self.s.game.shot(self.out/(name+'-walk.png'), max_wait=2, settle=.3)

    def building_walk(self, data, trial):
        start = self.xyz(data, trial['start'])
        end = self.xyz(data, [trial['start'][0], trial['min_y'], trial['end_z']])
        self.walk_world(data['_installation']['key']+'_'+trial['name'], start, end,
                        min_y=data['_installation']['world_min'][1]+trial['min_y'], timeout_s=trial['timeout_s'])

    def facilities(self, data):
        for service in data.get('service_blocks', []):
            installed = data['_installation']
            pos = world_position(installed, data['size'], service['pos'], block=True)
            state = rotated_state(service['state'], installed['rotation_quarters'])
            reply = self.rc('execute if block '+' '.join(map(str, pos))+' '+state)
            self.check(installed['key']+'_'+service['approach']+'_actual_block', 'passed' in reply,
                       {'pos': pos, 'expected_rotated_state': state, 'readback': reply})
        super().facilities(data)

    def capture_district(self):
        self.rc(f'gamemode spectator {self.s.username}')
        self.set_hud(True)
        minimum, maximum = self.spec['world_min'], self.spec['world_max']
        # A centered downward view fits the whole 265-block district inside
        # the 70-degree vertical FOV and avoids distant terrain fog.
        overhead = {'name': 'complete_district_aerial',
                    'pos': [(minimum[0]+maximum[0]+1)/2, maximum[1]+180,
                            (minimum[2]+maximum[2]+1)/2], 'yaw': 0, 'pitch': 90}
        for camera in [overhead, *self.spec['fixture_cameras']]:
            self.teleport(camera['pos'], camera['yaw'], camera['pitch'])
            readiness = None
            if camera['name'] == 'complete_district_aerial':
                # The centered 16-chunk view covers every district column.
                # Wait on actual server chunk availability before allowing
                # the client time to receive and tessellate the full district.
                conditions = ' '.join(f'if loaded {x*16} {minimum[1]} {z*16}'
                    for x in range(minimum[0]//16, maximum[0]//16+1)
                    for z in range(minimum[2]//16, maximum[2]//16+1))
                began = time.monotonic()
                reply = self.poll(lambda: self.rc('execute '+conditions), lambda value: 'passed' in value,
                                  60, 'all district chunk columns available at centered camera')
                readiness = {'server_all_columns': reply, 'server_wait_s': round(time.monotonic()-began, 3),
                             'client_render_completion': 'not instrumented; bounded stable-frame sampling and screenshot review required'}
                # Three bounded samples, separated in time, retain the final
                # frame rather than accepting the first identical sky frame.
                for _ in range(3):
                    self.s.game.stable_grab(max_wait=5, settle=2)
            capture = self.s.game.shot(self.out/(camera['name']+'.png'), max_wait=15, settle=2)
            self.report['screenshots'].append({**camera, 'capture': capture, 'readiness': readiness})
            self.save()
        for data in self.buildings:
            for shot in data['game_shots']:
                if shot['name'] in ('front', 'interior', 'guestroom', 'upper_corridor'):
                    self.photograph(data, shot)
        self.set_hud(False)

    def prepare_delivery(self):
        spawn = self.spec['recommended_spawn']; pos = spawn['pos']; block = [math.floor(v) for v in pos]
        self.rc('setworldspawn '+' '.join(map(str, block))+' '+str(spawn.get('yaw', 0)))
        self.rc(f'spawnpoint {self.s.username} '+' '.join(map(str, block)))
        self.rc(f'gamemode creative {self.s.username}')
        self.rc(f'effect clear {self.s.username}')
        self.rc('myvillage world resume')
        self.rc('gamerule randomTickSpeed 3')
        self.rc('forceload remove all')
        self.teleport(pos, spawn.get('yaw', 0), spawn.get('pitch', 0))
        self.check('delivery_spawn_reached', all(abs(a-b) < .2 for a,b in zip(self.player_pos(), pos)), spawn)
        self.rc('save-all flush')
        self.stop()
        result = package_world(cs.SERVER_DIR/cs.LEVEL_NAME, self.out/'yunshan_d1_v2_world.zip')
        self.report['world_export'] = result
        self.report['completed'].append('playable_world_export')
        self.save()

    def page(self):
        images = [f'<figure><img src="{html.escape(c["name"])}.png"><figcaption>{html.escape(c["name"])}</figcaption></figure>' for c in self.report['screenshots']]
        for data in self.buildings:
            role = html.escape(data['presentation']['role'])
            images.extend(f'<figure><img src="{role}/{name}.png"><figcaption>{html.escape(data["presentation"]["name"])} · {name}</figcaption></figure>' for name in ('front', 'interior'))
        (self.out/'index.html').write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>云山 D1 v2 完整街区</title><style>body{margin:24px;background:#eeeae0;color:#30382f;font:16px system-ui}main{max-width:1280px;margin:auto}section{display:grid;grid-template-columns:1fr 1fr}figure{margin:12px}img{width:100%}p{line-height:1.8}@media(max-width:700px){section{display:block}}</style><main><h1>云山 D1 v2 · 完整街区实际游戏</h1><p>完整地形、街巷与建筑已放入独立 Minecraft 世界；下方是云端真实客户端截图。修改街道、四栋旋转建筑入口、客栈楼梯和设施使用以实际 W 键及右键采集。软件渲染不代表玩家 FPS、外观或手感验收。</p><p><a href="yunshan_d1_v2_world.zip" download>下载完整可进入世界</a> · <a href="evidence.json">查看实际证据</a></p><p>世界需要匹配的 MyVillage 及依赖。世界包保留数据、维度、玩家与模组状态。全城 SCHEM 为生成源文件，未宣称由游戏导出。</p><section>'+''.join(images)+'</section></main></html>')

    def run(self):
        self.start(); self.setup()
        if not self.spec['path_trials']:
            raise ValueError('District manifest has no mapped modified-path trials')
        for trial in self.spec['path_trials']:
            self.walk_world(trial['name'], trial['start'], trial['end'], min_y=max(trial.get('min_y', trial['end'][1]), trial['end'][1]-.1), timeout_s=trial.get('timeout_s', 12))
        self.report['completed'].append('mapped_modified_paths')
        for data in self.buildings:
            for trial in data['walk_trials']:
                self.building_walk(data, trial)
        self.report['completed'] += ['mapped_rotated_entries', 'mapped_inn_stair']
        for data in self.buildings:
            self.facilities(data)
        self.report['completed'].append('mapped_facilities')
        self.capture_district()
        self.report['completed'].append('district_and_street_screenshots')
        self.prepare_delivery()
        self.report['status'] = 'pass'; self.save(); self.page()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, default=ROOT/'out/preview/yunshan-d1-v2/runtime-build/manifest.json')
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/yunshan-d1-v2/runtime')
    args = parser.parse_args(); evidence = DistrictEvidence(args.manifest, args.out)
    try:
        evidence.run()
    except BaseException as error:
        evidence.report['status'] = 'fail'; evidence.report['failures'].append(repr(error)); evidence.save()
        raise
    finally:
        if evidence.s:
            try:
                evidence.s.game.xdo('keyup', 'w')
            finally:
                evidence.stop()
        evidence.save()


if __name__ == '__main__':
    main()
