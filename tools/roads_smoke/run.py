"""在 Colab 的隔离新世界验证驿道：原生全路幅、拒绝覆盖、重启续建及客户端观察。"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
from combat_capture.rcon import Rcon
from roadweaver_probe.run import port, terminate, write


def verify_candidate(jar):
    with zipfile.ZipFile(jar) as archive:
        actual = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/') and name != 'META-INF/MANIFEST.MF'}
    expected = {}
    for directory in [ROOT/'build/classes/java/main', ROOT/'build/resources/main']:
        for path in directory.rglob('*'):
            if path.is_file():
                expected[path.relative_to(directory).as_posix()] = path.read_bytes()
    if actual.keys() != expected.keys():
        raise AssertionError({'jar_only': sorted(actual.keys()-expected.keys()), 'runtime_only': sorted(expected.keys()-actual.keys())})
    mismatches = [name for name in actual if actual[name] != expected[name]]
    if mismatches:
        raise AssertionError({'candidate_runtime_mismatches': mismatches})
    assert not any(n.startswith('net/shiroha233/') or '/evidence/' in n for n in actual)
    return {'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'entries': len(actual),
            'classes': sum(n.endswith('.class') for n in actual), 'runtime_entries_equal': True,
            'roadweaver_jar_installed': False}


class Server:
    def __init__(self, out, game):
        self.out, self.game = out, game
        self.password, self.rport, self.sport = secrets.token_hex(20), port(), port()
        self.process = None
        self.commands = []
        self.starts = 0
        self.data = game/'world/data/myvillage/worldplan'

    def command(self, text):
        with Rcon('127.0.0.1', self.rport, self.password, timeout=180) as connection:
            answer = connection.cmd(text)
        self.commands.append({'command': text, 'response': answer})
        write(self.out/'commands.json', self.commands)
        return answer

    def roads(self, action):
        answer = self.command('myvillage roads '+action)
        # RCON's Unicode JSON is read as the explicit production command result.
        match = re.search(r'\{"phase":.*\}', answer)
        if not match:
            raise RuntimeError(answer)
        return json.loads(match.group())

    def test(self, condition):
        return 'Test passed' in self.command('execute '+condition)

    def wait(self, predicate, seconds=900, allow_blocked=False):
        deadline = time.monotonic()+seconds
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError('Server exited '+str(self.process.returncode))
            status = self.roads('status')
            write(self.out/'progress.json', status)
            if predicate(status):
                return status
            if status['phase'] == 'failed' or (status['phase'] == 'blocked' and not allow_blocked):
                raise RuntimeError(str(status))
            time.sleep(2)
        raise TimeoutError(str(status))

    def start(self):
        self.starts += 1
        self.game.mkdir(parents=True, exist_ok=True)
        properties = {'server-ip': '127.0.0.1', 'server-port': self.sport, 'online-mode': 'false',
            'enforce-secure-profile': 'false', 'enable-rcon': 'true', 'rcon.port': self.rport,
            'rcon.password': self.password, 'broadcast-rcon-to-ops': 'false', 'enable-query': 'false',
            'level-name': 'world', 'level-seed': 20261101, 'level-type': 'myvillage\\:cultivation',
            'generate-structures': 'true', 'gamemode': 'creative', 'difficulty': 'peaceful',
            'spawn-protection': 0, 'allow-flight': 'true', 'view-distance': 4, 'simulation-distance': 3,
            'max-tick-time': 180000, 'max-players': 2, 'sync-chunk-writes': 'true', 'pause-when-empty-seconds': -1}
        (self.game/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in properties.items())+'\n')
        (self.game/'eula.txt').write_text('eula=true\n')
        args = ['./gradlew', '-I', 'tools/roads_smoke/runtime.init.gradle', 'runAcceptanceServer',
            '-x', 'processResources', '--no-daemon', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx6g']
        self.log = (self.out/f'server-{self.starts}.log').open('w')
        env = dict(os.environ, MYVILLAGE_ROAD_ADAPTER_GAME=str(self.game))
        self.process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=self.log, stderr=subprocess.STDOUT,
                                        stdin=subprocess.DEVNULL, start_new_session=True)
        deadline = time.monotonic()+600
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError('Server startup exit '+str(self.process.returncode))
            try:
                self.roads('status')
                break
            except (OSError, ValueError, RuntimeError):
                time.sleep(2)
        else:
            raise TimeoutError('Server not ready')
        for command in ['gamerule doDaylightCycle false', 'gamerule doWeatherCycle false',
                        'gamerule doMobSpawning false', 'gamerule randomTickSpeed 0', 'time set noon', 'weather clear']:
            self.command(command)

    def stop(self):
        if self.process and self.process.poll() is None:
            try:
                self.command('save-all flush')
                self.command('stop')
                self.process.wait(timeout=60)
            except Exception:
                pass
        if self.process:
            write(self.out/f'cleanup-{self.starts}.json', terminate(self.process))
        if hasattr(self, 'log'):
            self.log.close()

    def audit(self, name):
        self.roads('audit')
        self.wait(lambda s:s['audit_phase'] == 'complete', allow_blocked=True)
        source = self.data/'roads-audit.json'
        shutil.copy2(source, self.out/name)
        return json.loads(source.read_text())


def block_command(state):
    match = re.fullmatch(r'Block\{([a-z0-9_:]+)\}(\[.*\])?', state)
    if not match:
        raise ValueError(state)
    return match[1]+(match[2] or '')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--source', required=True)
    parser.add_argument('--capture', action='store_true')
    args = parser.parse_args()
    args.out = args.out.resolve();args.out.mkdir(parents=True, exist_ok=False)
    identity = verify_candidate(args.jar.resolve());identity['source'] = args.source
    write(args.out/'identity.json', identity)
    selected = ['native_plan', 'prebuild_readback', 'block_entity_veto', 'protected_block_detour', 'pause_restart_resume',
                'full_width_readback', 'city_gate_preservation', 'completed_restart']
    if args.capture:
        selected += ['mapped_gate_walks', 'native_client_screenshots']
    result = {'selected': selected, 'completed': [], 'failed': [], 'unrun': selected.copy(),
              'source': args.source, 'owner_acceptance': 'not_verified', 'full_route_player_walk': 'not_verified'}
    def complete(name):
        result['completed'].append(name);result['unrun'].remove(name);write(args.out/'result.json',result)
    write(args.out/'result.json', result)
    game = ROOT/'run-road-adapter'/('native-'+secrets.token_hex(4))
    write(args.out/'world.json', {'directory': str(game), 'seed': 20261101, 'kind': 'fresh isolated probe world'})
    server = Server(args.out, game)
    stage = selected[0]
    try:
        server.start();server.roads('plan nanjiang ximo');server.wait(lambda s:s['phase']=='ready')
        server.roads('export');shutil.copy2(server.data/'roads.json',args.out/'roads.json')
        route = json.loads((args.out/'roads.json').read_text())['plan']
        assert route['worldHash'] == 'a0fcb201ef25cf0cbb170fd28cad7c4ed21d2bf9b5e81701d978af1aa48c4ca3'
        assert route['width']==5 and route['gateWidths']=={'nanjiang':3,'ximo':5}
        outside={(c['x'],c['z']) for c in route['cells'] if not c['existing']}
        for p in route['centerline']:
            for dx in range(-2,3):
                for dz in range(-2,3):assert (p['x']+dx,p['z']+dz) in outside
        for x0,z0,x1,z1 in [(5744,656,5984,944),(4656,-3808,4880,-3536)]:
            assert all(c['existing'] or not (x0<=c['x']<x1 and z0<=c['z']<z1) for c in route['cells'])
        complete(stage)
        stage = 'prebuild_readback';before = server.audit('before-initial.json');complete(stage)
        stage = 'block_entity_veto'
        cell = next(c for c in route['cells'] if not c['existing'])
        floor_y = (cell['feet2']-1)//2;position = f"{cell['x']} {floor_y} {cell['z']}"
        old = next(c for c in before['samples'] if (c['x'],c['z'])==(cell['x'],cell['z']))['floor']
        server.command(f'setblock {position} minecraft:chest');server.roads('build')
        rejected = server.wait(lambda s:s['phase']=='blocked', allow_blocked=True)
        assert rejected['completed_chunks']==0 and 'minecraft:chest' in rejected['failure'],rejected
        write(args.out/'block-entity-veto.json',rejected)
        server.command(f'setblock {position} '+block_command(old));complete(stage)
        stage = 'protected_block_detour'
        marker=route['centerline'][len(route['centerline'])//3]
        obstacle=next(c for c in route['cells'] if (c['x'],c['z'])==(marker['x'],marker['z']) and not c['existing'])
        obstacle_pos=f"{obstacle['x']} {(obstacle['feet2']-1)//2} {obstacle['z']}"
        server.command(f"forceload add {obstacle['x']} {obstacle['z']}")
        deadline=time.monotonic()+60
        while not server.test('if loaded '+obstacle_pos):
            if time.monotonic()>deadline:raise TimeoutError('Protected obstacle chunk did not load')
            time.sleep(1)
        server.command(f'setblock {obstacle_pos} minecraft:obsidian')
        assert server.test(f'if block {obstacle_pos} minecraft:obsidian')
        reroutes=[]
        limit=json.loads((ROOT/'config/roads/prototype.json').read_text())['max_obstacles']
        for attempt in range(limit):
            server.roads('build')
            partial=server.wait(lambda s:s['phase']=='blocked' or(s['phase']=='building' and s['completed_chunks']>=8),allow_blocked=True)
            if partial['phase']!='blocked':break
            assert partial['completed_chunks']==0,partial
            reroutes.append(partial);write(args.out/'reroutes.json',reroutes)
            server.roads('plan nanjiang ximo');server.wait(lambda s:s['phase']=='ready')
            server.roads('export');shutil.copy2(server.data/'roads.json',args.out/'roads.json')
            route=json.loads((args.out/'roads.json').read_text())['plan']
            before=server.audit(f'before-reroute-{attempt+1}.json')
        else:raise RuntimeError('Protected obstacle reroute budget exhausted')
        assert reroutes and route['obstacles']
        assert server.test(f'if block {obstacle_pos} minecraft:obsidian')
        server.command(f"forceload remove {obstacle['x']} {obstacle['z']}")
        write(args.out/'before.json',before);complete(stage)
        stage = 'pause_restart_resume'
        paused=server.roads('pause');assert paused['phase']=='paused'
        server.stop();server.start();resumed=server.roads('status')
        assert resumed['phase']=='paused' and resumed['completed_chunks']==paused['completed_chunks'],(paused,resumed)
        write(args.out/'restart-partial.json',{'before':partial,'paused':paused,'loaded':resumed})
        server.roads('build');server.wait(lambda s:s['phase']=='complete');complete(stage)
        stage = 'full_width_readback';after = server.audit('after.json')
        assert after['failed_columns']==0 and after['missing_material']==0,after['lost_positions'][:20]
        assert after['columns']==len(route['cells'])
        assert after['route_sha256']==before['route_sha256']
        complete(stage)
        stage = 'city_gate_preservation'
        gate = lambda audit:{(c['x'],c['z']):(c['feet2'],c['floor'],c['head']) for c in audit['samples'] if c['existing']}
        assert gate(before)==gate(after)
        write(args.out/'gate-preservation.json',{'columns':len(gate(after)),'identical_floor_and_head_states':True});complete(stage)
        stage = 'completed_restart';server.stop();server.start();loaded=server.roads('status')
        assert loaded['phase']=='complete';assert server.roads('build')['completed_chunks']==loaded['completed_chunks']
        restarted = server.audit('after-restart.json')
        assert restarted['failed_columns']==0 and restarted['missing_material']==0
        assert restarted['route_sha256']==after['route_sha256']
        write(args.out/'restart-complete.json',loaded);complete(stage)
        if args.capture:
            stage = 'mapped_gate_walks'
            from roads_smoke.capture import capture
            observation = capture(server,route)
            assert observation['mapped_gate_walks']=='passed',observation
            complete(stage);stage = 'native_client_screenshots'
            assert observation['status']=='captured_for_review',observation
            complete(stage)
    except BaseException as error:
        result['failed'].append({'case':stage,'error':repr(error)})
        result['unrun']=[case for case in selected if case not in result['completed'] and case!=stage]
        write(args.out/'result.json',result)
        raise
    finally:
        server.stop();write(args.out/'result.json',result)


if __name__ == '__main__':
    main()
