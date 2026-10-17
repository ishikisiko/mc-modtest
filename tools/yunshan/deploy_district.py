#!/usr/bin/env python3
"""Export a bounded district command plan; owner writes require --execute.

The caller must first create/open a NEW DevBridge-owned flat creative world.
No launch, create, install, reset, delete or original-save operation occurs here.
Interrupted --resume deployments replay all non-clear phases idempotently. A
completed deployment cannot resume, so later player edits are not overwritten.
"""
from __future__ import annotations

import argparse
from array import array
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import http.client
import importlib.util
import json
import math
import os
from pathlib import Path
import queue
import re
import secrets
import sys
import threading
import time
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from yunshan.build_district import Volume, realize
from yunshan.build_inn import sign_data
import json_to_nbt as nbt

PHASES = ("clear", "solid", "fluid", "attachments", "pairs", "sign_text")
LIMIT = 32768
TILE = 128


def split_box(lo, hi, limit=LIMIT):
    lengths = [b-a+1 for a,b in zip(lo,hi)]
    if math.prod(lengths) <= limit:
        yield list(lo), list(hi)
        return
    axis = max(range(3), key=lambda i: lengths[i])
    middle = (lo[axis]+hi[axis])//2
    left, right = list(hi), list(lo)
    left[axis], right[axis] = middle, middle+1
    yield from split_box(lo,left,limit)
    yield from split_box(right,hi,limit)


def kind(state):
    name, _ = nbt.parse_block_state(state)
    if name in ("minecraft:water", "minecraft:lava"):
        return "fluid"
    if name.endswith(("_door", "_bed")):
        return "pairs"
    if name.endswith(("_sign", "_hanging_sign", "_carpet", "_pressure_plate", "_button", "_trapdoor")) or name in (
            "minecraft:ladder", "minecraft:lantern", "minecraft:torch", "minecraft:wall_torch",
            "minecraft:redstone_torch", "minecraft:redstone_wall_torch"):
        return "attachments"
    return "solid"


def block_record(lo, hi, state):
    lo, hi = list(lo), list(hi)
    command = (f"setblock {' '.join(map(str,lo))} {state}" if lo == hi else
               f"fill {' '.join(map(str,lo))} {' '.join(map(str,hi))} {state}")
    return {"command": command, "lo": lo, "hi": hi, "state": state}


def export_plan(volume, signs):
    """Dense x/z/y greedy merging, bounded by load tiles and vanilla fill cap."""
    w,h,d = volume.size
    ox,oy,oz = volume.minimum
    left = array('H',volume.cells)
    classes = [kind(s) for s in volume.palette]
    phase_tiles = {phase:{} for phase in PHASES}
    attachments = {}

    def tile_key(x,z):
        return f"{x//TILE},{z//TILE}"

    def add(phase,lo,hi,state):
        key = tile_key(lo[0],lo[2])
        phase_tiles[phase].setdefault(key,[]).extend(block_record(a,b,state) for a,b in split_box(lo,hi))

    for tz in range(oz//TILE, (oz+d-1)//TILE+1):
        for tx in range(ox//TILE, (ox+w-1)//TILE+1):
            x0,x1 = max(ox,tx*TILE),min(ox+w-1,(tx+1)*TILE-1)
            z0,z1 = max(oz,tz*TILE),min(oz+d-1,(tz+1)*TILE-1)
            add("clear",(x0,oy,z0),(x1,oy+h-1,z1),"minecraft:air")
    for y in range(h):
        for z in range(d):
            row = (y*d+z)*w
            for x in range(w):
                value = left[row+x]
                if not value:
                    continue
                state,phase = volume.palette[value],classes[value]
                if phase in ("attachments","pairs"):
                    p = (x+ox,y+oy,z+oz)
                    attachments[p] = state
                    left[row+x] = 0
                    continue
                xmax = min(w-1, ((x+ox)//TILE+1)*TILE-1-ox)
                zmax = min(d-1, ((z+oz)//TILE+1)*TILE-1-oz)
                xx,zz,yy=x,z,y
                while xx<xmax and left[row+xx+1]==value:
                    xx+=1
                while zz<zmax and all(left[(y*d+zz+1)*w+i]==value for i in range(x,xx+1)):
                    zz+=1
                while yy+1<h and all(left[((yy+1)*d+j)*w+i]==value
                                     for j in range(z,zz+1) for i in range(x,xx+1)):
                    yy+=1
                zero=array('H',[0])*(xx-x+1)
                for j in range(y,yy+1):
                    for k in range(z,zz+1):
                        base=(j*d+k)*w+x
                        left[base:base+len(zero)]=zero
                add(phase,(x+ox,y+oy,z+oz),(xx+ox,yy+oy,zz+oz),state)

    used=set()
    for p,state in sorted(attachments.items(), key=lambda pair:(pair[0][1],pair[0][2],pair[0][0])):
        if p in used:
            continue
        phase=kind(state)
        if phase!="pairs":
            add(phase,p,p,state)
            continue
        name,props=nbt.parse_block_state(state)
        if name.endswith('_door'):
            low=(p[0],p[1]-(props.get('half')=='upper'),p[2])
            high=(low[0],low[1]+1,low[2])
            pair=(low,high)
        else:
            dx,dz={'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}[props['facing']]
            foot=(p[0]-dx,p[1],p[2]-dz) if props.get('part')=='head' else p
            pair=(foot,(foot[0]+dx,foot[1],foot[2]+dz))
        if any(q not in attachments or nbt.parse_block_state(attachments[q])[0]!=name for q in pair):
            raise ValueError(f'incomplete door/bed pair at {p}')
        # Both halves stay in one ordered tile job, even across its edge.
        key=tile_key(pair[0][0],pair[0][2])
        phase_tiles['pairs'].setdefault(key,[]).extend(block_record(q,q,attachments[q]) for q in pair)
        used.update(pair)
    for sign in signs:
        p=sign['pos']
        payload=sign_data(sign['messages'])
        payload.pop('id',None)
        record={'command':f"data merge block {' '.join(map(str,p))} "+json.dumps(payload,ensure_ascii=False,separators=(',',':')),
                'pos':p, 'sign':payload}
        phase_tiles['sign_text'].setdefault(tile_key(p[0],p[2]),[]).append(record)

    stages=[]
    for phase in PHASES:
        for key,commands in sorted(phase_tiles[phase].items()):
            tx,tz=map(int,key.split(','))
            stages.append({'phase':phase,'tile':key,
                'load':[tx*TILE-16,tz*TILE-16,(tx+1)*TILE+15,(tz+1)*TILE+15],
                'commands':commands})
    result={'format':1,'minimum':volume.minimum,'maximum':volume.maximum,
            'size':volume.size,'stages':stages,'signs':signs}
    result['logical_volume_match']=verify_plan(result,volume)
    result['counts']=dict(Counter({phase:sum(len(s['commands']) for s in stages if s['phase']==phase) for phase in PHASES}))
    result['command_count']=sum(result['counts'].values())
    result['estimated_seconds_at_20_to_30_commands_per_second']=[round(result['command_count']/30),round(result['command_count']/20)]
    result['plan_sha256']=hashlib.sha256(json.dumps(result,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    return result


def verify_plan(plan,volume):
    replay=Volume(volume.minimum,volume.maximum)
    for stage in plan['stages']:
        x0,z0,x1,z1=stage['load']
        if ((x1//16-x0//16+1)*(z1//16-z0//16+1))>256:
            raise ValueError('load tile exceeds 256 chunks')
        for record in stage['commands']:
            if 'state' not in record:
                continue
            if math.prod(b-a+1 for a,b in zip(record['lo'],record['hi']))>LIMIT:
                raise ValueError('fill exceeds vanilla limit')
            if not (x0<=record['lo'][0]<=record['hi'][0]<=x1 and z0<=record['lo'][2]<=record['hi'][2]<=z1):
                raise ValueError('block command escapes its loaded tile')
            replay.fill(record['lo'],record['hi'],record['state'])
    remap=[volume.indices.get(state,-1) for state in replay.palette]
    if any(remap[value]!=expected for value,expected in zip(replay.cells,volume.cells)):
        raise ValueError('exported commands do not reconstruct the authored volume')
    return True


class Bridge:
    def __init__(self,env_path=None):
        spec=importlib.util.spec_from_file_location('yunshan_devhostctl',ROOT/'devbridge/devhost/devhostctl.py')
        module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.load_env(Path(env_path) if env_path else module.DEFAULT_ENV)
        self.url=urllib.parse.urlparse(os.environ['DEVBRIDGE_URL'])
        self.token=os.environ['DEVBRIDGE_TOKEN']
        self.connections=queue.LifoQueue(maxsize=4)
        self.connection_count=0
        self.connection_lock=threading.Lock()

    def call(self,method,path,params=None):
        data=json.dumps(params or {},ensure_ascii=False).encode() if method=='POST' else None
        if method=='GET' and params:
            path+='?'+urllib.parse.urlencode(params)
        try:
            connection=self.connections.get_nowait()
        except queue.Empty:
            with self.connection_lock:
                create=self.connection_count<4
                if create:self.connection_count+=1
            if create:
                cls=http.client.HTTPSConnection if self.url.scheme=='https' else http.client.HTTPConnection
                connection=cls(self.url.hostname,self.url.port,timeout=45)
            else:
                connection=self.connections.get()
        try:
            connection.request(method,(self.url.path.rstrip('/')+path),body=data,
                               headers={'Authorization':'Bearer '+self.token,'Content-Type':'application/json'})
            response=connection.getresponse()
            payload=json.loads(response.read())
        except BaseException:
            connection.close()
            with self.connection_lock:self.connection_count-=1
            raise
        self.connections.put(connection)
        if not payload.get('ok'):
            raise RuntimeError(f'bridge {path}: {payload.get("error","unknown error")}')
        return payload.get('result')


def assert_world(bridge,folder,managed=False):
    world=bridge.call('GET','/client/world')
    if world.get('folder')!=folder or not world.get('inWorld') or not world.get('singleplayer') or world.get('busy'):
        raise RuntimeError('Exact new singleplayer world is not open and idle; no further writes allowed')
    if managed:
        worlds=bridge.call('GET','/client/worlds')['worlds']
        found=[w for w in worlds if w.get('folder')==folder]
        if len(found)!=1 or not found[0].get('bridgeOwned') or found[0].get('preset')!='flat' or found[0].get('gameMode')!='creative':
            raise RuntimeError('Target must be a new DevBridge-owned flat creative save')
    return world


def state_matches(actual,expected):
    an,ap=nbt.parse_block_state(actual)
    en,ep=nbt.parse_block_state(expected)
    return an==en and all(ap.get(k)==v for k,v in ep.items())


def checked_command(bridge,command,record=None):
    result=bridge.call('POST','/command',{'command':command})
    if result.get('success'):
        return result
    output=' '.join(result.get('output',[]))
    # Only recognize explicit no-change responses. Block writes additionally
    # read back the entire bounded box, including air, before accepting them.
    block_nochange=('No blocks were filled','Could not set the block','没有方块被填充','无法放置方块')
    other_nochange=('The specified properties already have these values','指定属性已经拥有这些值',
                    'No chunks were marked for force loading','No chunks were unmarked for force loading',
                    '没有区块被标记为强制加载','没有区块被取消标记为强制加载')
    if record and 'state' in record and any(s in output for s in block_nochange):
        lo,hi=record['lo'],record['hi']
        area=bridge.call('GET','/world/area',dict(zip(('x1','y1','z1','x2','y2','z2'),lo+hi),includeAir='true'))
        counts=area.get('counts',{})
        if sum(counts.values())==math.prod(b-a+1 for a,b in zip(lo,hi)) and all(state_matches(s,record['state']) for s in counts):
            return result
    elif any(s in output for s in other_nochange):
        return result
    raise RuntimeError(f'Command failed: {command[:180]}: {output[:300]}')


def write_json(path,data):
    tmp=path.with_suffix(path.suffix+'.tmp')
    tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    tmp.replace(path)


def wait_loaded(bridge,folder,guard,load,y,timeout=90):
    """A bounded, read-only command checks every chunk in the tile's halo."""
    x0,z0,x1,z1=load
    conditions=' '.join(f'if loaded {x*16+8} {y} {z*16+8}'
                        for z in range(z0//16,z1//16+1) for x in range(x0//16,x1//16+1))
    command=guard[:-4]+conditions+' run time query gametime'
    start=time.monotonic()
    while time.monotonic()-start<timeout:
        assert_world(bridge,folder)
        result=bridge.call('POST','/command',{'command':command})
        if result.get('success'):
            return
        # A command syntax or permission failure is not transient loading.
        if any(word in ' '.join(result.get('output',[])).lower() for word in ('unknown','incorrect','permission','未知','不正确')):
            raise RuntimeError('Loaded-chunk readiness command failed: '+str(result.get('output')))
        time.sleep(.5)
    raise TimeoutError('Load tile did not become ready within the bounded timeout')


def execute(plan,bridge,folder,progress_path,workers=4,resume=False):
    if not re.fullmatch(r'yunshan-d1-v2-[A-Za-z0-9_-]+',folder):
        raise ValueError('Deployment folder must use a unique yunshan-d1-v2- suffix')
    if not 1<=workers<=4:
        raise ValueError('HTTP workers must be between 1 and 4')
    assert_world(bridge,folder,managed=True)
    previous=json.loads(progress_path.read_text()) if progress_path.exists() else None
    if previous:
        if not resume or previous['plan_sha256']!=plan['plan_sha256'] or previous['world_folder']!=folder or previous.get('status')=='complete':
            raise ValueError('Progress exists; only an interrupted matching deployment can explicitly --resume')
    elif resume:
        raise ValueError('No matching interrupted deployment to resume')
    token=previous['guard_token'] if previous else secrets.randbelow(2_000_000_000)+1
    guard=f'execute if score #delivery ysd1delivery matches {token} run '
    if previous:
        score=checked_command(bridge,'scoreboard players get #delivery ysd1delivery')
        if score.get('result')!=token:
            raise RuntimeError('World deployment guard does not match the interrupted job')
    else:
        checked_command(bridge,'scoreboard objectives add ysd1delivery dummy')
        checked_command(bridge,f'scoreboard players set #delivery ysd1delivery {token}')
    progress={'plan_sha256':plan['plan_sha256'],'world_folder':folder,'guard_token':token,
              'status':'running','clear_complete':bool(previous and previous.get('clear_complete')),
              'completed_commands':0,'total_commands':plan['command_count'],'started_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
              'resume_mode':'replay_all_nonclear_phases' if previous else None}
    write_json(progress_path,progress)
    start=time.monotonic()
    pending=None
    try:
        for stage_index,stage in enumerate(plan['stages']):
            phase=stage['phase']
            if phase=='clear' and progress['clear_complete']:
                continue
            assert_world(bridge,folder)
            progress.update(phase=phase,tile=stage['tile'],stage_index=stage_index)
            write_json(progress_path,progress)
            load=stage['load']
            checked_command(bridge,guard+'forceload add '+' '.join(map(str,load)))
            pending=load
            wait_loaded(bridge,folder,guard,load,plan['minimum'][1])
            commands=stage['commands']
            active_workers=1 if phase in ('pairs','sign_text') else workers
            with ThreadPoolExecutor(max_workers=active_workers) as pool:
                # Bound submitted work: a world switch/failure cannot leave
                # thousands of writes queued behind the next assertion.
                for offset in range(0,len(commands),32):
                    assert_world(bridge,folder)
                    batch=commands[offset:offset+32]
                    futures=[pool.submit(checked_command,bridge,guard+r['command'],r) for r in batch]
                    errors=[]
                    for future in futures:
                        try:future.result()
                        except Exception as exc:errors.append(exc)
                    if errors:
                        raise errors[0]
                    progress['completed_commands']+=len(batch)
                    progress['elapsed_seconds']=round(time.monotonic()-start,2)
                    write_json(progress_path,progress)
            assert_world(bridge,folder)
            checked_command(bridge,guard+'forceload remove '+' '.join(map(str,load)))
            pending=None
            if phase=='clear' and (stage_index+1==len(plan['stages']) or plan['stages'][stage_index+1]['phase']!='clear'):
                progress['clear_complete']=True
            print(json.dumps({k:progress[k] for k in ('phase','tile','completed_commands','elapsed_seconds')},ensure_ascii=False),flush=True)
        assert_world(bridge,folder)
        checked_command(bridge,guard+'save-all flush')
        progress['status']='complete'
        progress['elapsed_seconds']=round(time.monotonic()-start,2)
        write_json(progress_path,progress)
        return progress
    except BaseException as exc:
        progress['status']='interrupted'
        progress['error']=str(exc)
        progress['pending_forceload']=pending
        write_json(progress_path,progress)
        raise
    finally:
        if pending:
            # Never clean up a different world. Preserve cleanup failure in
            # progress so the parent knows this job's bounded ticket remains.
            try:
                assert_world(bridge,folder)
                checked_command(bridge,guard+'forceload remove '+' '.join(map(str,pending)))
                progress.pop('pending_forceload',None)
                write_json(progress_path,progress)
            except Exception as exc:
                progress['cleanup_error']=str(exc)
                write_json(progress_path,progress)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,default=ROOT/'blueprints/yunshan/district_realization_v2.json')
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--execute',action='store_true')
    parser.add_argument('--world-folder')
    parser.add_argument('--workers',type=int,default=4)
    parser.add_argument('--resume',action='store_true')
    parser.add_argument('--env',type=Path)
    args=parser.parse_args()
    realized=realize(args.config)
    # The archival city retains its old world coordinates. A new default-flat
    # preview uses the same rigid translation as the cloud-tested placement.
    from yunshan.preview_site import translate_point
    delta=realized[0].get('preview_translation',[0,0,0])
    volume=realized[4]
    volume.minimum=tuple(translate_point(volume.minimum,delta))
    volume.maximum=tuple(translate_point(volume.maximum,delta))
    signs=[{**s,'pos':translate_point(s['pos'],delta)} for s in realized[10]]
    plan=export_plan(volume,signs)
    args.out.mkdir(parents=True,exist_ok=True)
    write_json(args.out/'command-plan.json',plan)
    print(json.dumps({k:plan[k] for k in ('plan_sha256','counts','command_count','logical_volume_match','estimated_seconds_at_20_to_30_commands_per_second')},indent=2),flush=True)
    if args.execute:
        if not args.world_folder:
            parser.error('--execute requires the exact --world-folder')
        execute(plan,Bridge(args.env),args.world_folder,args.out/'deployment-progress.json',args.workers,args.resume)
    elif args.resume:
        parser.error('--resume requires --execute')


if __name__=='__main__':
    main()
