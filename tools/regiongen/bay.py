"""Compile the authored bay settlement's common terrain and connected street levels.

All junctions have one height. Straight segments own grade changes, with level
landings at bends and junctions. Existing building generators retain ownership.
"""
from __future__ import annotations
from collections import defaultdict, deque
import heapq
import json
import math
from pathlib import Path

import numpy as np

from .assets import ROOT, load_assets, rotated_pos, digest
from .scene import build, validate
from .build import write_json, render
from buildgen.groups import get_group

RECIPE = ROOT/'blueprints/regiongen/bay_terraces_v1.json'
DIRECTIONS = ((1,0),(0,1),(-1,0),(0,-1))


def point(x,y,z):return {'x':int(x),'y':int(y),'z':int(z)}
def inside(x,z,rect):return rect[0]<=x<=rect[2] and rect[1]<=z<=rect[3]


def natural_terrain(recipe):
    n=recipe['size'];z,x=np.mgrid[:n,:n];t=recipe['terrain'];r=recipe['river']
    h=np.full((n,n),float(t['base']))
    for cx,cz,rise,sx,sz in t['hills']:h+=rise*np.exp(-((x-cx)/sx)**2-((z-cz)/sz)**2)
    sx,sz=t['detail_scale'];h+=t['detail_amplitude']*np.sin(x/sx)*np.cos(z/sz)
    center=np.full((n,n),float(r['center']))
    for amplitude,offset,scale in r['waves']:center+=amplitude*np.sin((x-offset)/scale)
    weight=np.clip((np.abs(z-center)-r['half_width'])/r['bank_width'],0,1)
    weight=weight*weight*(3-2*weight)
    # A bounded river bay closes inside the fully owned core before native-terrain blending.
    distance=np.minimum(np.minimum(x,z),np.minimum(n-1-x,n-1-z))
    ends=np.clip(distance/r['end_fade'],0,1);ends=ends*ends*(3-2*ends)
    weight=1-(1-weight)*ends
    return np.rint((r['water_y']-r['bed_depth'])*(1-weight)+h*weight).astype(np.int16)


def buildings(recipe,assets):
    result=[];margin=recipe['terrain']['foundation_margin']
    for spec in recipe['buildings']:
        a=assets[spec['asset']]['public'];size=a['size'];q=spec['rotation'];x,z=spec['x'],spec['z']
        w,d=(size[2],size[0]) if q%2 else (size[0],size[2])
        ex,_,ez=rotated_pos((a['entry'][0],a['entry'][1],-1),size,q)
        dx,dz=((0,-1),(1,0),(0,1),(-1,0))[q]
        result.append({**spec,'y':spec['level']-a['entry'][1],'width':w,'depth':d,'height':size[1],
                       'cluster':'terraces','approach':point(x+ex,spec['level'],z+ez),
                       'outer':point(x+ex+dx*margin,spec['level'],z+ez+dz*margin),
                       'pad':[x-margin,z-margin,x+w+margin-1,z+d+margin-1]})
    return result


def route_xy(start,end,heights,blocked,recipe,existing=()):
    """Bounded cardinal A*, with a turn cost to keep stair flights straight."""
    n=recipe['size'];cfg=recipe['layout'];start=(start[0],start[2]);end=(end[0],end[2])
    existing=set(existing);near=set()
    for px,pz in existing:
        radius=cfg['adjacent_route_radius']
        for dz in range(-radius,radius+1):
            for dx in range(-radius,radius+1):near.add((px+dx,pz+dz))
    if blocked[start[1],start[0]] or blocked[end[1],end[0]]:
        raise ValueError(f'Route endpoint in reserved pad: {start} -> {end}')
    initial=(*start,-1);queue=[(0,0,initial)];cost={initial:0};prev={}
    while queue:
        _,g,state=heapq.heappop(queue)
        if g!=cost[state]:continue
        x,z,heading=state
        if (x,z)==end:
            result=[(x,z)]
            while state!=initial:state=prev[state];result.append(state[:2])
            return result[::-1]
        for direction,(dx,dz) in enumerate(DIRECTIONS):
            nx,nz=x+dx,z+dz
            if not(2<=nx<n-2 and 2<=nz<n-2) or blocked[nz,nx]:continue
            if round(heights[nz,nx])<recipe['river']['water_y']+1:continue
            ng=g+1+cfg['height_cost']*abs(int(heights[nz,nx])-int(heights[z,x]))
            if heading not in (-1,direction):ng+=cfg['turn_cost']
            if (nx,nz) in near and (nx,nz) not in existing:ng+=cfg['close_route_penalty']
            ns=(nx,nz,direction)
            if ng<cost.get(ns,float('inf')):
                cost[ns]=ng;prev[ns]=state
                heapq.heappush(queue,(ng+abs(nx-end[0])+abs(nz-end[1]),ng,ns))
    raise ValueError(f'No bounded street route: {start} -> {end}')


def solve_levels(paths, anchors, recipe,reference):
    """Difference constraints on straight segments; no independently graded crossings."""
    graph=defaultdict(set)
    for _,path in paths:
        for a,b in zip(path,path[1:]):graph[a].add(b);graph[b].add(a)
    missing=set(anchors)-set(graph)
    if missing:raise ValueError(f'Unconnected height anchors: {missing}')
    vertices=set(anchors)
    for p,neighbours in graph.items():
        if len(neighbours)!=2:vertices.add(p);continue
        a,b=neighbours
        if a[0]!=b[0] and a[1]!=b[1]:vertices.add(p)
    owners=defaultdict(set);half=recipe['layout']['road_width']//2
    for p,neighbours in graph.items():
        for q in neighbours:
            axis=(0,1) if p[0]!=q[0] else (1,0)
            for i in range(-half,half+1):owners[p[0]+axis[0]*i,p[1]+axis[1]*i].add(p)
    joined=[sorted(ps) for ps in owners.values() if len(ps)>1]
    for ps in joined:
        for p in ps:vertices.add(p)
    segments=[];seen=set();coarse=defaultdict(list)
    spacing=recipe['layout']['step_spacing'];landing=recipe['layout']['landing']
    for start in sorted(vertices):
        for second in sorted(graph[start]):
            if frozenset((start,second)) in seen:continue
            line=[start,second];seen.add(frozenset((start,second)))
            while line[-1] not in vertices:
                q=next(q for q in graph[line[-1]] if q!=line[-2])
                seen.add(frozenset((line[-1],q)));line.append(q)
            end=line[-1];length=len(line)-1
            capacity=max(0,(length-2*landing+1)//spacing)
            index=len(segments);segments.append((line,capacity))
            coarse[start].append((end,capacity));coarse[end].append((start,capacity))
    for ps in joined:
        for a,b in zip(ps,ps[1:]):coarse[a].append((b,0));coarse[b].append((a,0))

    # Highest feasible lower envelope, anchored by every public landing and entrance.
    levels={p:-10**9 for p in vertices};queue=[];source={}
    for p,h in anchors.items():levels[p]=h;source[p]=p;heapq.heappush(queue,(-h,p))
    while queue:
        neg,p=heapq.heappop(queue);h=-neg
        if h!=levels[p]:continue
        for q,capacity in coarse[p]:
            value=h-capacity
            if value>levels[q]:levels[q]=value;source[q]=source[p];heapq.heappush(queue,(-value,q))
    conflicts={p:(anchors[p],levels[p],source[p]) for p in anchors if levels[p]!=anchors[p]}
    if conflicts:raise ValueError(f'Insufficient street length between fixed levels: {conflicts}')
    upper={p:10**9 for p in vertices};queue=[]
    for p,h in anchors.items():upper[p]=h;heapq.heappush(queue,(h,p))
    while queue:
        h,p=heapq.heappop(queue)
        if h!=upper[p]:continue
        for q,capacity in coarse[p]:
            value=h+capacity
            if value<upper[q]:upper[q]=value;heapq.heappush(queue,(value,q))
    for chosen in sorted(vertices-set(anchors)):
        wanted=max(recipe['river']['water_y']+1,round(reference[chosen[1],chosen[0]]))
        value=max(levels[chosen],min(upper[chosen],wanted))
        if value<=recipe['river']['water_y']:raise ValueError('Street would enter the water')
        # Freeze this junction inside the feasible interval, then tighten neighbours.
        levels[chosen]=upper[chosen]=value
        for bounds,sign in ((levels,-1),(upper,1)):
            queue=[(sign*value,chosen)]
            while queue:
                signed,p=heapq.heappop(queue);h=signed*sign
                if h!=bounds[p]:continue
                for q,capacity in coarse[p]:
                    candidate=h+sign*capacity
                    if sign*candidate<sign*bounds[q]:
                        bounds[q]=candidate;heapq.heappush(queue,(sign*candidate,q))
    result={}
    for line,capacity in segments:
        a,b=levels[line[0]],levels[line[-1]];change=b-a;length=len(line)-1
        assert abs(change)<=capacity
        steps=[]
        if change:
            # All rises stay clear of the two level junction landings.
            lo=landing+1;hi=length-landing
            steps=[round(lo+i*(hi-lo)/max(1,abs(change)-1)) for i in range(abs(change))]
            if len(steps)==1:steps=[(lo+hi)//2]
            assert all(b-a>=spacing for a,b in zip(steps,steps[1:])),(line,steps)
        for i,p in enumerate(line):
            h=a+int(math.copysign(sum(step<=i for step in steps),change)) if change else a
            if p in result and result[p]!=h:raise AssertionError('Junction grade disagreement')
            result[p]=h
    return result,graph


def plan(recipe,assets):
    n=recipe['size'];natural=natural_terrain(recipe);height=natural.astype(float);kinds=np.zeros((n,n),np.uint8)
    bs=buildings(recipe,assets);z,x=np.mgrid[:n,:n];constraints=[]
    for b in bs:constraints.append((b['pad'],b['level']))
    landing=recipe['layout']['landing']
    constraints.extend(([px-landing,pz-landing,px+landing,pz+landing],py)
                       for px,py,pz in recipe['nodes'].values())
    constraints.extend((p['bounds'],p['level']) for p in recipe['plazas'])
    for (x0,z0,x1,z1),level in constraints:
        distance=np.maximum(np.maximum(x0-x,x-x1),np.maximum(z0-z,z-z1)).clip(0)
        weight=np.clip(1-distance/recipe['terrain']['blend_radius'],0,1);weight=weight*weight*(3-2*weight)
        height=height*(1-weight)+level*weight
    for (x0,z0,x1,z1),level in constraints:
        height[z0:z1+1,x0:x1+1]=level;kinds[z0:z1+1,x0:x1+1]=1
    paths=[];nodes=recipe['nodes'];clearance=recipe['layout']['pad_clearance']
    def mask(except_id=None):
        blocked=np.zeros((n,n),bool)
        for b in bs:
            if b['id']==except_id:
                x0,z0,x1,z1=b['x'],b['z'],b['x']+b['width']-1,b['z']+b['depth']-1
            else:x0,z0,x1,z1=b['pad']
            blocked[z0-clearance:z1+clearance+1,x0-clearance:x1+clearance+1]=True
        return blocked
    for edge in recipe['connections']:
        existing={p for _,path in paths for p in path}
        paths.append((edge['id'],route_xy(nodes[edge['from']],nodes[edge['to']],height,mask(),recipe,existing)))
    for b in bs:
        existing={p for _,path in paths for p in path}
        p=b['outer'];path=route_xy(nodes[recipe['entrance_links'][b['id']]],(p['x'],p['y'],p['z']),height,mask(b['id']),recipe,existing)
        end=(b['approach']['x'],b['approach']['z'])
        while path[-1]!=end:
            px,pz=path[-1];path.append((px+int(np.sign(end[0]-px)),pz+int(np.sign(end[1]-pz))))
        paths.append(('entry_'+b['id'],path))
    all_xy={p for _,path in paths for p in path};anchors={(p[0],p[2]):p[1] for p in nodes.values()}
    half=recipe['layout']['road_width']//2
    for p in all_xy:
        for rect,level in constraints:
            # Full-width roads may enter a pad only at its already chosen level.
            expanded=[rect[0]-half,rect[1]-half,rect[2]+half,rect[3]+half]
            if inside(*p,expanded):
                if p in anchors and anchors[p]!=level:raise ValueError(f'Pad grade conflict at {p}')
                anchors[p]=level
    levels,graph=solve_levels(paths,anchors,recipe,height)
    facing={};roads={}
    for a,neighbours in graph.items():
        for b in neighbours:
            if levels[a]==levels[b]+1:
                direction={(1,0):'east',(-1,0):'west',(0,1):'south',(0,-1):'north'}[(a[0]-b[0],a[1]-b[1])]
                if a in facing and facing[a]!=direction:raise ValueError(f'Conflicting stair direction {a}')
                facing[a]=direction
    for p,neighbours in graph.items():
        offsets={(0,0)}
        for q in neighbours:
            axis=(0,1) if p[0]!=q[0] else (1,0)
            offsets.update((axis[0]*i,axis[1]*i) for i in range(-half,half+1))
        for dx,dz in offsets:
            at=(p[0]+dx,p[1]+dz);h=levels[p];f=facing.get(p)
            previous=roads.get(at)
            if previous and (previous['height']!=h or previous['facing'] and f and previous['facing']!=f):
                raise ValueError(f'Road shoulders conflict at {at}: {previous} vs {h}/{f}')
            if previous and previous['facing']:f=previous['facing']
            roads[at]={'x':at[0],'z':at[1],'height':h,'facing':f,'bridge':False}
    # Blend around the resolved street footprint, retaining pads and the water bed.
    wet=natural<recipe['river']['water_y']
    for r in roads.values():
        px,pz,y=r['x'],r['z'],r['height']
        radius=recipe['layout']['road_blend_radius'];strength=recipe['layout']['road_blend_strength']
        for dz in range(-radius,radius+1):
            for dx in range(-radius,radius+1):
                qx,qz=px+dx,pz+dz;distance=max(abs(dx),abs(dz));weight=max(0,1-distance/(radius+1))*strength
                if not kinds[qz,qx] and not wet[qz,qx]:height[qz,qx]=height[qz,qx]*(1-weight)+y*weight
    for r in roads.values():height[r['z'],r['x']]=r['height'];kinds[r['z'],r['x']]=2
    height[wet]=natural[wet]
    heights=np.rint(height).astype(np.int16)
    if np.max(abs(heights-natural))>recipe['terrain']['max_earthwork']:raise ValueError('Earthwork budget exceeded')
    routes=[{'id':name,'width':recipe['layout']['road_width'],'points':[point(x,levels[x,z],z) for x,z in path]} for name,path in paths]
    clusters=[{**c,'x':c['center'][0],'z':c['center'][2],'north':point(*c['center']),'south':point(*c['center'])} for c in recipe['clusters']]
    result={'size':n,'seed':recipe['seed'],'algorithm':recipe['id'],'recipe':recipe,'buildings':bs,'clusters':clusters,
            'heights':heights.flatten().tolist(),'natural_heights':natural.flatten().tolist(),'kinds':kinds.flatten().tolist(),
            'roads':list(roads.values()),'routes':routes,'spawn':point(*nodes[recipe['entry']['node']])}
    result['plan_sha256']=digest(result)
    return result


def generate(out,recipe_path=RECIPE):
    recipe=json.loads(Path(recipe_path).read_text());get_group(recipe['group']);assets=load_assets(recipe)
    compiled=plan(recipe,assets);volume,preview,expected,signs=build(compiled,assets)
    report=validate(compiled,volume,expected,preview);out=Path(out);out.mkdir(parents=True,exist_ok=True)
    for name,value in [('plan',compiled),('preview',preview),('report',report)]:write_json(out/(name+'.json'),value)
    if not report['passed']:raise ValueError(report['failures'])
    return compiled,volume,preview,expected,signs,report


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,default=ROOT/'reports/regiongen/bay-production')
    args=parser.parse_args();*_,report=generate(args.out);print(json.dumps(report,ensure_ascii=False))
