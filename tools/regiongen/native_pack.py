"""Export the exact offline volume as vanilla templates and a mapped-input tour."""
from __future__ import annotations
import argparse
from collections import deque
import gzip
import hashlib
import json
from pathlib import Path
import shutil

from .assets import load_assets, rotated_pos
from .scene import build, validate, AIR
from yunshan.build_district import export_tiles


def point(p):return tuple(p[k] for k in ('x','y','z'))


def shortest(graph, start, goal):
    parents={start:None};queue=deque([start])
    while queue:
        p=queue.popleft()
        if p==goal:
            result=[]
            while p is not None:result.append(p);p=parents[p]
            return result[::-1]
        for q in sorted(graph.get(p,())):
            if q not in parents:parents[q]=p;queue.append(q)
    raise ValueError(f'No physical route: {start} -> {goal}')


def compress(path, limit=10):
    if len(path)<2:return path
    out=[path[0]];run=0
    for i in range(1,len(path)-1):
        a,p,b=path[i-1:i+2];run+=1
        if (p[0]-a[0],p[2]-a[2])!=(b[0]-p[0],b[2]-p[2]) or run>=limit:
            out.append(p);run=0
    out.append(path[-1]);return out


def make_tour(plan, volume, assets):
    graph={}
    def edge(p,q):graph.setdefault(p,set()).add(q);graph.setdefault(q,set()).add(p)
    for route in plan['routes']:
        points=list(map(point,route['points']))
        for a,b in zip(points,points[1:]):edge(a,b)
    targets=[]
    for b in plan['buildings']:
        a=assets[b['asset']];size=a['public']['size'];origin=(b['x'],b['y'],b['z'])
        def world(p):return tuple(x+y for x,y in zip(origin,rotated_pos(p,size,b['rotation'])))
        local=a['targets'].get('hall',a['targets'].get('counter'))
        if local is None:local=[a['public']['entry'][0],a['public']['entry'][1],3]
        target=world(local);start=point(b['approach']);y=start[1]
        x0,z0,x1,z1=b['pad'];walk=set()
        for x in range(x0,x1+1):
            for z in range(z0,z1+1):
                p=(x,y,z);floor=volume.get((x,y-1,z))
                if floor in (AIR,'minecraft:water'):continue
                if all(volume.get((x,y+dy,z))==AIR or '_door[' in volume.get((x,y+dy,z)) or (dy==0 and volume.get((x,y,z)).endswith('_carpet')) for dy in (0,1)):walk.add(p)
        inner={p:set() for p in walk}
        for p in walk:
            for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                q=(p[0]+dx,y,p[2]+dz)
                if q in walk:inner[p].add(q)
        path=shortest(inner,start,target)
        doors=[]
        for i,p in enumerate(path):
            state=volume.get(p)
            if '_door[' in state:
                if i<1 or i+1==len(path):raise ValueError('Door lacks two-sided approach')
                doors.append({'pos':p,'state':state,'before':path[i-1],'after':path[i+1]})
        targets.append({'id':b['id'],'approach':start,'inside':target,'entry_path':path,'doors':doors})
    current=point(plan['spawn']);tour=[]
    # Visit all entrances, the upper road end, and the far bank; return to spawn.
    for target in targets:
        path=shortest(graph,current,tuple(target['approach']))
        tour.append({**target,'route':compress(path)})
        current=tuple(target['approach'])
    for rid in ('relay_street','river_bridge'):
        r=next(r for r in plan['routes'] if r['id']==rid)
        goal=point(r['points'][0] if rid=='relay_street' else r['points'][-1])
        tour.append({'id':rid,'route':compress(shortest(graph,current,goal))});current=goal
    tour.append({'id':'return_to_spawn','route':compress(shortest(graph,current,point(plan['spawn'])))})
    return tour


def export(plan_path,out):
    out.mkdir(parents=True,exist_ok=False)
    plan=json.loads(plan_path.read_text());assets=load_assets(plan['recipe'])
    volume,preview,expected,signs=build(plan,assets)
    report=validate(plan,volume,expected,preview)
    if not report['passed']:raise AssertionError(report['failures'])
    tour=make_tour(plan,volume,assets)
    tiles=export_tiles(volume,signs,out,32)
    # Existing tile exporter owns serialization; isolate this sample's namespace path.
    old=out/'datapack/data/myvillage/structure/yunshan_d1_v2';new=old.with_name('river_valley_sample');old.rename(new)
    for tile in tiles:tile['id']=tile['id'].replace('yunshan_d1_v2','river_valley_sample')
    (out/'datapack/pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'河谷聚落固定样板'}}))
    (out/'plan.json').write_bytes(plan_path.read_bytes())
    (out/'expected-cells.bin.gz').write_bytes(gzip.compress(volume.cells.tobytes(),mtime=0))
    manifest={'format':1,'plan_sha256':plan['plan_sha256'],'geometry_sha256':report['geometry_sha256'],
        'minimum':volume.minimum,'size':volume.size,'palette':volume.palette,'cell_type':'uint16_little_endian',
        'tiles':tiles,'tour':tour,'spawn':point(plan['spawn']),'signs':signs,
        'selected':['complete_placement','mapped_all_entrances','mapped_roads_bridge','saved_world_comparison','native_screenshots']}
    manifest['files']={str(p.relative_to(out)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.rglob('*')) if p.is_file()}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False))
    shutil.make_archive(str(out),'zip',out)
    print(json.dumps({'tiles':len(tiles),'tour':len(tour),'zip':str(out)+'.zip','geometry':manifest['geometry_sha256']}))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();export(a.plan,a.out)
