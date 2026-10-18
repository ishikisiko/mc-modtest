#!/usr/bin/env python3
"""Bounded dedicated-server acceptance. Unique disposable worlds, loopback RCON, owned child processes only."""
from __future__ import annotations
from contextlib import contextmanager
import hashlib
import base64
import gzip
import io
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from combat_capture.rcon import Rcon
from buildgen.nbtread import read_gzipped_nbt
OUT=ROOT/'reports/worldplan/runtime'
GAME=ROOT/'run-acceptance'
SEED=20261017

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1',0));return s.getsockname()[1]

class Server:
    def __init__(self,name,label,planned=True,structures=True,view_distance=3):
        self.name,self.label=name,label
        self.planned,self.structures=planned,structures
        self.view_distance=max(3,min(12,int(view_distance)))
        self.password,self.rport,self.sport=secrets.token_hex(20),port(),port()
        self.proc=None;self.log=None;self.commands=[];self.world=GAME/name
    def __enter__(self):
        GAME.mkdir(exist_ok=True)
        values={'server-ip':'127.0.0.1','server-port':self.sport,'online-mode':'false','enforce-secure-profile':'false',
            'enable-rcon':'true','rcon.port':self.rport,'rcon.password':self.password,'broadcast-rcon-to-ops':'false','enable-query':'false',
            'level-name':self.name,'level-seed':SEED,'level-type':'myvillage\\:cultivation' if self.planned else 'minecraft\\:normal',
            'generate-structures':str(self.structures).lower(),'generator-settings':'{}','gamemode':'creative','difficulty':'peaceful',
            'spawn-protection':0,'spawn-monsters':'false','spawn-animals':'false','spawn-npcs':'false','allow-flight':'true',
            'view-distance':self.view_distance,'simulation-distance':3,'max-tick-time':180000,'max-players':2,'sync-chunk-writes':'true','pause-when-empty-seconds':-1}
        self.backups={}
        for filename,body in [('server.properties','\n'.join(f'{k}={v}' for k,v in values.items())+'\n'),('eula.txt','eula=true\n')]:
            path=GAME/filename;self.backups[path]=path.read_bytes() if path.exists() else None;path.write_text(body)
        self.log=(OUT/(self.label+'.log')).open('w')
        args=['./gradlew','runAcceptanceServer','-x','generateAllStructures','--no-daemon','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx3g']
        self.proc=subprocess.Popen(args,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=self.log,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            deadline=time.monotonic()+480
            while time.monotonic()<deadline:
                if self.proc.poll() is not None:raise RuntimeError(f'{self.label}: server exited with {self.proc.returncode}')
                try:
                    if self.planned:
                        info=self.query('info');atlas=self.world/'data/myvillage/worldplan/worldplan.json'
                        if info.get('active') and atlas.exists():break
                    elif 'players online' in self.cmd('list'):break
                except (OSError,RuntimeError,ValueError):pass
                time.sleep(2)
            else:raise RuntimeError(f'{self.label}: server did not become ready')
            for command in ['gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doMobSpawning false','gamerule randomTickSpeed 0','time set noon','weather clear']:self.cmd(command)
            return self
        except BaseException:
            self.__exit__(*sys.exc_info());raise
    def cmd(self,command):
        with Rcon('127.0.0.1',self.rport,self.password,timeout=180) as r:reply=r.cmd(command)
        self.commands.append({'command':command,'reply':reply});return reply
    def query(self,command):
        reply=self.cmd('myvillage worldplan '+command)
        if 'WORLDPLAN ' not in reply:raise RuntimeError(reply)
        return json.loads(reply.split('WORLDPLAN ',1)[1])
    def __exit__(self,*unused):
        cleanup={'label':self.label,'pid':self.proc.pid if self.proc else None,'forced_stop':False}
        try:
            if self.proc and self.proc.poll() is None:
                try:self.cmd('save-all flush');self.cmd('stop')
                except Exception as e:cleanup['stop_error']=str(e)
                try:self.proc.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    cleanup['forced_stop']=True;os.killpg(self.proc.pid,signal.SIGTERM)
                    try:self.proc.wait(timeout=15)
                    except subprocess.TimeoutExpired:os.killpg(self.proc.pid,signal.SIGKILL);self.proc.wait(timeout=10)
            cleanup['returncode']=self.proc.returncode if self.proc else None
            cleanup['terminated']=not self.proc or self.proc.poll() is not None
        finally:
            if self.log:self.log.close()
            for path,content in self.backups.items():
                if content is None:path.unlink(missing_ok=True)
                else:path.write_bytes(content)
            write(OUT/(self.label+'-commands.json'),self.commands);write(OUT/(self.label+'-cleanup.json'),cleanup)

@contextmanager
def test_tickets(server,x0,z0,x1=None,z1=None):
    """Bounded test-only tickets emulate a player keeping the inspected chunks interactive."""
    x1=x0 if x1 is None else x1;z1=z0 if z1 is None else z1
    count=(abs(x1//16-x0//16)+1)*(abs(z1//16-z0//16)+1)
    if count>256:raise ValueError(f'Acceptance ticket budget exceeded: {count}')
    server.cmd(f'forceload add {x0} {z0} {x1} {z1}')
    try:
        deadline=time.monotonic()+30
        while True:
            replies=[server.cmd(f'execute if loaded {x} 80 {z}') for x,z in [(x0,z0),(x1,z1)]]
            if all('Test passed' in reply for reply in replies):break
            if time.monotonic()>deadline:raise RuntimeError(f'Test chunks did not become interactive: {replies}')
            time.sleep(.2)
        yield
    finally:
        server.cmd(f'forceload remove {x0} {z0} {x1} {z1}')
        server.cmd('forceload query')

def decode_saved_rules(encoded):
    """Decode V4's bounded NBT-string transport without unbounded inflation."""
    if not isinstance(encoded,str) or len(encoded)>65000 or not encoded.startswith('gzip:'):
        raise ValueError('Expected bounded V4 compressed rules')
    compressed=base64.b64decode(encoded[5:],validate=True)
    with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
        raw=stream.read(4*1024*1024+1)
    if len(raw)>4*1024*1024:raise ValueError('Inflated V4 rules limit')
    rules=json.loads(raw)
    if rules.get('algorithm')!='myvillage:worldplan_v4':raise ValueError('Unexpected rules algorithm')
    return rules

def check_saved(world,expected):
    _,tag=read_gzipped_nbt(str(world/'level.dat'))
    generator=tag['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']
    assert generator['type']=='myvillage:planned_v4',generator
    assert generator['plan_hash']==expected
    rules=decode_saved_rules(generator['rules'])
    assert rules['core_radius']==4000 and len(rules['city']['layout']['tiles'])==238
    return {'plan_sha256':generator['plan_hash'],'rules_bytes':len(generator['rules'].encode()),'algorithm':rules['algorithm']}

def check_jar():
    version=next(line.split('=',1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    jars=list((ROOT/'build/libs').glob(f'myvillage-{version}.jar'));assert len(jars)==1,jars
    with zipfile.ZipFile(jars[0]) as z:
        names=set(z.namelist())
        def tiles(family):return [n for n in names if n.startswith(f'data/myvillage/structure/worldplan/{family}/') and n.endswith('.nbt')]
        assert len(tiles('yunshan_v1'))==35
        assert len(tiles('yunshan_v4'))==238
        for name in ['data/myvillage/worldgen/world_preset/cultivation.json',
                *[f'data/myvillage/worldplan/frozen_v{v}.json' for v in (1,2,3,4)],
                'com/example/myvillage/worldplan/runtime/PlannedChunkGenerator.class',
                'com/example/myvillage/sim/runtime/commerce/CommerceMenu.class',
                'data/myvillage/world_sim/commerce_rules.json']:assert name in names,name
        assert {'WorldPlanReservationMixin','WorldPlanOreMixin'}.issubset(json.loads(z.read('myvillage.mixins.json'))['mixins'])
        preset=json.loads(z.read('data/myvillage/worldgen/world_preset/cultivation.json'))
        generator=preset['dimensions']['minecraft:overworld']['generator']
        assert generator['type']=='myvillage:planned_v4'
        decode_saved_rules(generator['rules'])
    return {'file':jars[0].name,'sha256':hashlib.sha256(jars[0].read_bytes()).hexdigest(),'tiles':238,'legacy_tiles':35}

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    result={'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'seed':SEED,'status':'running','scope':'dedicated-server generation, not physical client or owner aesthetic acceptance','checks':{}}
    suffix=secrets.token_hex(4);world_a='worldplan_accept_a_'+suffix;world_b='worldplan_accept_b_'+suffix
    probes=[(249,0),(250,0),(-251,0),(-250,0),(6250,-6250),(-6250,6250)]
    try:
        result['jar']=check_jar()
        with Server(world_a,'a-create') as s:
            s.cmd('gamerule randomTickSpeed 0')  # Exact order hashes compare immutable generation, not elapsed grass decay.
            info=s.query('info');expected=info['plan_sha256'];city=info['city'];result['info']=info
            atlas=json.loads((s.world/'data/myvillage/worldplan/worldplan.json').read_text())
            assert atlas['plan_sha256']==expected and str(atlas['seed'])==str(SEED)
            assert s.query('at 100000 -100000')['regionId'].startswith('outer/')
            result['checks']['initial_atlas']=True;heights={}
            for x,z in probes:
                check=s.query(f'probe {x} {z}');assert check['height_mismatches']==0 and check['exact_noise_stage'],check
                heights[f'{x},{z}']=check
            result['checks']['noise_a']=heights
            s.query('pregen_city forward 8')
            partial=s.query('audit_city');assert partial['loaded_tiles']>0 and partial['missing_tiles']>0 and partial['differences']==0,partial
            result['checks']['partial_city']=partial
            marker=(city['x']+8,city['baseY']+city['height']-2,city['z']+8);at=' '.join(map(str,marker))
            with test_tickets(s,marker[0],marker[2]):
                reply=s.cmd(f'execute if block {at} minecraft:air');assert 'Test passed' in reply,reply
                s.cmd(f'setblock {at} minecraft:gold_block');s.cmd('save-all flush')
        result['checks']['saved_a']=check_saved(GAME/world_a,expected)
        with Server(world_a,'a-restart') as s:
            assert s.query('info')['plan_sha256']==expected
            s.query('pregen_city reverse')
            with test_tickets(s,city['x'],city['z'],city['x']+city['width']-1,city['z']+city['depth']-1):
                reply=s.cmd(f'execute if block {at} minecraft:gold_block');assert 'Test passed' in reply,reply
                changed=s.query('audit_city');assert changed['missing_tiles']==0 and changed['differences']==1,changed
                s.cmd(f'setblock {at} minecraft:air');clean=s.query('audit_city');assert clean['complete'],clean
            result['checks']['city_a']=clean;result['checks']['player_edit_survived_restart_and_extension']=True;result['checks']['saved_plan_unchanged']=True
        result['checks']['saved_a_again']=check_saved(GAME/world_a,expected)
        with Server(world_b,'b-reversed-order') as s:
            s.cmd('gamerule randomTickSpeed 0')
            assert s.query('info')['plan_sha256']==expected;other={}
            for x,z in reversed(probes):
                check=s.query(f'probe {x} {z}');assert check['height_mismatches']==0 and check['exact_noise_stage'],check
                assert check['height_sha256']==heights[f'{x},{z}']['height_sha256'],check
                other[f'{x},{z}']=check
            result['checks']['noise_b']=other;s.query('pregen_city reverse')
            with test_tickets(s,city['x'],city['z'],city['x']+city['width']-1,city['z']+city['depth']-1):
                clean_b=s.query('audit_city');assert clean_b['complete'],clean_b
                assert clean_b['actual_sha256']==clean['actual_sha256'],(clean_b,clean)
            result['checks']['city_b']=clean_b;shutil.copytree(s.world/'data/myvillage/worldplan',OUT/'actual-world-atlas',dirs_exist_ok=True)
        result['checks']['saved_b']=check_saved(GAME/world_b,expected)
        ordinary='worldplan_ordinary_'+suffix
        with Server(ordinary,'ordinary-preset',planned=False) as s:
            reply=s.cmd('myvillage worldplan info');assert 'not using myvillage:cultivation' in reply,reply
        _,ordinary_data=read_gzipped_nbt(str(GAME/ordinary/'level.dat'))
        ordinary_generator=ordinary_data['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']
        assert ordinary_generator['type']=='minecraft:noise' and 'plan_hash' not in ordinary_generator
        result['checks']['ordinary_world_not_converted']=True
        result['checks']['test_tickets']='one marker chunk, then 238 already-generated V4 city chunks for audit; capped at 256, released before every shutdown'
        result['status']='pass'
        with zipfile.ZipFile(OUT/'worldplan-acceptance-world.zip','w',zipfile.ZIP_DEFLATED) as z:
            for path in sorted((GAME/world_a).rglob('*')):
                if path.is_file() and path.name not in ('session.lock','level.dat_old'):z.write(path,'worldplan-acceptance/'+str(path.relative_to(GAME/world_a)))
    except BaseException as e:
        result['status']='fail';result['error']=repr(e);raise
    finally:
        write(OUT/'acceptance.json',result);print(json.dumps(result,ensure_ascii=False,indent=2),flush=True)
if __name__=='__main__':main()
