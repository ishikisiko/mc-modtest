"""将总舵作者几何裁入真实方块格；模型、选框与碰撞共用同一组 1/16 格构件。"""
from __future__ import annotations
from collections import defaultdict,deque
from functools import lru_cache
import gzip,hashlib,json,math
from pathlib import Path

from .headquarters import Scene,build_scene,ROOT
from tools.lord_manors.geometry import canonical,normalize,FULL

SOURCE=ROOT/'blueprints/qianji/headquarters_materials.json'
FACINGS=('north','east','south','west')

class ConstructionScene(Scene):
    def person(self,*args,**kwargs):pass
    def pine(self,x,y,z,h=9,seed=1):self.tree_sites.append(dict(kind='spruce',x=x,y=y,z=z,height=round(h),seed=seed))
    def cherry(self,x,y,z,h=7):self.tree_sites.append(dict(kind='cherry',x=x,y=y,z=z,height=round(h),seed=0))
    def bamboo(self,x,y,z,h=7):self.tree_sites.append(dict(kind='bamboo',x=x,y=y,z=z,height=round(h),seed=0))

def rotation(q):
    x,y,z,w=q
    r=[[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
       [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
       [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]]
    return [[round(v) if abs(v-round(v))<.0001 else v for v in row] for row in r]

def box_fragments(b,grid):
    """任意朝向梁按世界 XZ 栅格解析 Y 区间；不把斜梁替换为外包盒。"""
    r=rotation(b[9:13]);half=[v/2 for v in b[3:6]];centre=b[:3]
    ext=[sum(abs(row[i])*half[i] for i in range(3)) for row in r]
    if all(sum(abs(v)>.0001 for v in row)==1 for row in r):
        yield [centre[i]-ext[i] for i in range(3)]+[centre[i]+ext[i] for i in range(3)];return
    inv=list(zip(*r));g=grid
    for ix in range(math.floor((centre[0]-ext[0])*g),math.ceil((centre[0]+ext[0])*g)):
        x=(ix+.5)/g-centre[0]
        for iz in range(math.floor((centre[2]-ext[2])*g),math.ceil((centre[2]+ext[2])*g)):
            z=(iz+.5)/g-centre[2];low,high=-ext[1],ext[1]
            for row,h in zip(inv,half):
                a=row[0]*x+row[2]*z;k=row[1]
                if abs(k)<1e-9:
                    if abs(a)>h:low=1;high=0;break
                else:
                    lo,hi=sorted(((-h-a)/k,(h-a)/k));low=max(low,lo);high=min(high,hi)
            if high<=low:continue
            lo=math.ceil((centre[1]+low)*g-.5)/g;hi=(math.floor((centre[1]+high)*g-.5)+1)/g
            if hi>lo:yield [ix/g,lo,iz/g,(ix+1)/g,hi,(iz+1)/g]

def mesh_fragments(mesh,grid):
    # 作者网格是完整的竖向棱柱；以各三角面和水平射线求实心截面。
    vs=[mesh['vertices'][i:i+3] for i in range(0,len(mesh['vertices']),3)];low=min(p[1] for p in vs);high=max(p[1] for p in vs)
    ring=[]
    for p in vs:
        if abs(p[1]-low)<1e-5 and (p[0],p[2]) not in ring:ring.append((p[0],p[2]))
    if len(ring)<3:raise ValueError('总舵棱柱底面缺失')
    def inside(x,z):
        hit=False;j=len(ring)-1
        for i,(a,b) in enumerate(ring):
            c,d=ring[j]
            if (b>z)!=(d>z) and x<(c-a)*(z-b)/(d-b)+a:hit=not hit
            j=i
        return hit
    for ix in range(math.floor(min(x for x,z in ring)*grid),math.ceil(max(x for x,z in ring)*grid)):
        start=None
        z0=math.floor(min(z for x,z in ring)*grid);z1=math.ceil(max(z for x,z in ring)*grid)
        for iz in range(z0,z1+1):
            yes=iz<z1 and inside((ix+.5)/grid,(iz+.5)/grid)
            if yes and start is None:start=iz
            if not yes and start is not None:
                yield [ix/grid,low,start/grid,(ix+1)/grid,high,iz/grid];start=None

def add_box(cells,b,role,uv=None):
    lo=[round(b[i]*16) for i in range(3)];hi=[max(lo[i]+1,round(b[i+3]*16)) for i in range(3)]
    lo[0]=max(0,lo[0]);lo[1]=max(-16,lo[1]);lo[2]=max(0,lo[2]);hi[0]=min(168*16,hi[0]);hi[2]=min(184*16,hi[2])
    if any(lo[i]>=hi[i] for i in range(3)):return
    for y in range(lo[1]//16,(hi[1]+15)//16):
        for z in range(lo[2]//16,(hi[2]+15)//16):
            for x in range(lo[0]//16,(hi[0]+15)//16):
                off=(x*16,y*16,z*16);a=tuple(max(0,lo[i]-off[i]) for i in range(3));b=tuple(min(16,hi[i]-off[i]) for i in range(3));p=a+b+(role,)
                if uv:
                    uvdata=[]
                    for face in ('west','east','down','up','north','south'):
                        global_lo=[off[i]+a[i] for i in range(3)];global_hi=[off[i]+b[i] for i in range(3)]
                        if uv['axis']=='z':
                            u0=(global_lo[0]-uv['lo'][0])/(uv['hi'][0]-uv['lo'][0])*16;u1=(global_hi[0]-uv['lo'][0])/(uv['hi'][0]-uv['lo'][0])*16
                            v0=(uv['hi'][1]-global_hi[1])/(uv['hi'][1]-uv['lo'][1])*16;v1=(uv['hi'][1]-global_lo[1])/(uv['hi'][1]-uv['lo'][1])*16
                            if face=='north':u0,u1=16-u1,16-u0
                        else:
                            u0=(global_lo[0]-uv['lo'][0])/(uv['hi'][0]-uv['lo'][0])*16;u1=(global_hi[0]-uv['lo'][0])/(uv['hi'][0]-uv['lo'][0])*16
                            v0=(global_lo[2]-uv['lo'][2])/(uv['hi'][2]-uv['lo'][2])*16;v1=(global_hi[2]-uv['lo'][2])/(uv['hi'][2]-uv['lo'][2])*16
                        uvdata.extend(round(n,5) for n in (u0,v0,u1,v1))
                    p+=tuple(uvdata)
                key=x,y+1,z
                if p[:6]==FULL and not uv:cells[key]=[p]
                else:cells[key].append(p)

def carve_openings(raw,openings):
    for o in openings:
        if 'layers' in o:continue
        low=o['bounds'][:3];high=o['bounds'][3:]
        for y in range(math.floor(low[1]+1),math.ceil(high[1]+1)):
            for z in range(math.floor(low[2]),math.ceil(high[2])):
                for x in range(math.floor(low[0]),math.ceil(high[0])):
                    key=x,y,z
                    if key not in raw:continue
                    off=[x,y-1,z];cut=[max(0,round((low[i]-off[i])*16)) for i in range(3)]+[min(16,round((high[i]-off[i])*16)) for i in range(3)];parts=[]
                    for b in raw[key]:
                        overlap=[min(b[i+3],cut[i+3])-max(b[i],cut[i]) for i in range(3)]
                        if any(n<=0 for n in overlap):parts.append(b);continue
                        core=list(b[:6])
                        for axis in range(3):
                            if core[axis]<cut[axis]:
                                q=core.copy();q[axis+3]=cut[axis];parts.append(tuple(q)+b[6:]);core[axis]=cut[axis]
                            if core[axis+3]>cut[axis+3]:
                                q=core.copy();q[axis]=cut[axis+3];parts.append(tuple(q)+b[6:]);core[axis+3]=cut[axis+3]
                    if parts:raw[key]=parts
                    else:del raw[key]

def subtract_box(b,cut):
    if any(min(b[i+3],cut[i+3])<=max(b[i],cut[i]) for i in range(3)):return [b]
    core=list(b);result=[]
    for axis in range(3):
        if core[axis]<cut[axis]:q=core.copy();q[axis+3]=cut[axis];result.append(q);core[axis]=cut[axis]
        if core[axis+3]>cut[axis+3]:q=core.copy();q[axis]=cut[axis+3];result.append(q);core[axis+3]=cut[axis+3]
    return result

def native_tree(site):
    x,z=math.floor(site['x']-.5),math.floor(site['z']-.5);y=round(site['y']+1);h=site['height'];kind=site['kind'];out={}
    if kind=='bamboo':
        for dx,dz,hh in [(-1,0,h),(0,1,h-1),(1,0,h)]:
            out[x+dx,y-1,z+dz]='minecraft:dirt'
            for i in range(hh):out[x+dx,y+i,z+dz]=f'minecraft:bamboo[age=1,leaves={"large" if i>=hh-2 else "small" if i==hh-3 else "none"},stage={1 if i==hh-1 else 0}]'
        return out
    logs={(x,y+i,z) for i in range(h)}
    if kind=='cherry':
        for dx,dz in [(-1,0),(1,1),(0,-1)]:
            for n in range(1,3):logs.add((x+dx*n,y+h-3+n//2,z+dz*n))
    leaves=set()
    layers=[(i,max(1,round((h-i)*.48))) for i in range(3,h+1)] if kind=='spruce' else [(h-1,3),(h,3),(h+1,2)]
    for i,r in layers:
        for dx in range(-r,r+1):
            for dz in range(-r,r+1):
                if abs(dx)==r and abs(dz)==r and (i+dx*3+dz*7)%3:continue
                p=x+dx,y+i,z+dz
                if p not in logs:leaves.add(p)
    distance={p:0 for p in logs};q=deque(logs)
    while q:
        p=q.popleft();d=distance[p]
        if d>=6:continue
        for delta in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]:
            n=tuple(a+b for a,b in zip(p,delta))
            if n in leaves and n not in distance:distance[n]=d+1;q.append(n)
    out[x,y-1,z]='minecraft:dirt'
    for p in logs:out[p]=f'minecraft:{kind}_log[axis=y]'
    for p in leaves:
        if p in distance:out[p]=f'minecraft:{kind}_leaves[distance={distance[p]},persistent=false,waterlogged=false]'
    return out

def identity():
    paths=[SOURCE,ROOT/'blueprints/qianji/headquarters_design.json',ROOT/'blueprints/qianji/zhongzhou_headquarters.json',ROOT/'blueprints/qianji/lighting.json',ROOT/'tools/qianji/headquarters.py',Path(__file__),ROOT/'tools/lord_manors/geometry.py']
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}

@lru_cache(maxsize=1)
def compile_components():
    cfg=json.loads(SOURCE.read_text());scene=build_scene(ConstructionScene)['current'];raw=defaultdict(list);water=[]
    access=json.loads((ROOT/'blueprints/qianji/zhongzhou_headquarters.json').read_text())
    for b in scene['boxes']:
        if b[6]=='water':water.append(b);continue
        for part in box_fragments(b,cfg['rotation_grid']):
            parts=[part]
            for opening in access.get('openings',[]):
                if b[8] in opening.get('layers',[]):parts=[piece for p in parts for piece in subtract_box(p,opening['bounds'])]
            for p in parts:add_box(raw,p,b[6])
    for mesh in scene['meshes']:
        for part in mesh_fragments(mesh,cfg['rotation_grid']):add_box(raw,part,mesh['material'])
    special={}
    for i,s in enumerate(scene['signs']):
        x,y,z=s['p'];w,h=s['size'];b=[x-w/2,y-h/2,z-.035,x+w/2,y+h/2,z+.035];role='plaque_'+str(i)
        special[role]=dict(kind='plaque',text=s['text'],size=[w,h]);add_box(raw,b,role,dict(axis='z',lo=[v*16 for v in b[:3]],hi=[v*16 for v in b[3:]]))
    for i,m in enumerate(scene['maps']):
        x,y,z=m['p'];w,d=m['size'];b=[x-w/2,y-.015,z-d/2,x+w/2,y+.015,z+d/2];role='map_'+str(i)
        special[role]=dict(kind='map',map=m['kind']);add_box(raw,b,role,dict(axis='y',lo=[v*16 for v in b[:3]],hi=[v*16 for v in b[3:]]))
    carve_openings(raw,access.get('openings',[]))
    converted={};unique=set()
    for pos,pieces in raw.items():
        p,turn=canonical(tuple(sorted(set(pieces),key=repr)));converted[pos]=(p,turn)
        if not(len(p)==1 and p[0][:6]==FULL and len(p[0])==7):unique.add(p)
    del raw
    profiles=sorted(unique,key=repr);lookup={p:i for i,p in enumerate(profiles)};blocks={};roles=set()
    for pos,(p,turn) in converted.items():
        roles.update(b[6] for b in p)
        if len(p)==1 and p[0][:6]==FULL and len(p[0])==7:state='myvillage:qianji_'+p[0][6]
        else:
            i=lookup[p];state=f'myvillage:qianji_carved_{i//32}[facing={FACINGS[turn]},shape={i%32}]'
        blocks[pos]=state
    for b in water:
        for x in range(math.ceil(b[0]-b[3]/2),math.floor(b[0]+b[3]/2)):
            for z in range(math.ceil(b[2]-b[5]/2),math.floor(b[2]+b[5]/2)):
                position=x,math.floor(b[1]+1),z
                if position not in blocks:blocks[position]='minecraft:water[level=0]'
    for site in scene['tree_sites']:
        tree=native_tree(site);leaves={p for p,s in tree.items() if '_leaves[' in s};removed=leaves.intersection(converted)
        if leaves and len(removed)/len(leaves)>cfg['native_tree_prune_max_share']:raise ValueError(f'庭院树冠无法在既定净空内保留完整形态 {site} {len(removed)}/{len(leaves)}')
        for p in removed:del tree[p]
        # 花园树冠修剪不能残留与真实树干断连的装饰树叶。
        live_leaves=leaves-removed;distance={p:0 for p,s in tree.items() if '_log[' in s};queue=deque(distance)
        while queue:
            p=queue.popleft();dist=distance[p]
            if dist>=6:continue
            for delta in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]:
                q=tuple(a+b for a,b in zip(p,delta))
                if q in live_leaves and q not in distance:distance[q]=dist+1;queue.append(q)
        for p in live_leaves:
            if p not in distance:del tree[p]
            else:tree[p]=f'minecraft:{site["kind"]}_leaves[distance={distance[p]},persistent=false,waterlogged=false]'
        site['trimmed_leaf_blocks']=len(leaves)-sum('_leaves[' in s for s in tree.values())
        for p,state in tree.items():
            if not(0<=p[0]<168 and 0<=p[2]<184):raise ValueError('总舵原版树越出地块')
            # 原木根部可替换种植土，树冠不能侵入屋面/梁架。
            if p in converted and state.split('[',1)[0].endswith(('_leaves','_log')):
                existing={b[6] for b in converted[p][0]}
                root=(math.floor(site['x']-.5),round(site['y']+1),math.floor(site['z']-.5))
                thin_planting_surface=p==root and all(b[4]<=4 for b in converted[p][0])
                if not thin_planting_surface and existing-{'grass','moss','soil','stone','stone_dark','paving'}:raise ValueError(f'原版树侵入总舵构件 {site} {p} {existing}')
            blocks[p]=state
    # 委托榜保留真实交互，锚点控制同一座总舵的客户端动效。
    blocks[tuple(access['board'])]='myvillage:jianghu_board[facing=north]'
    blocks[tuple(access['atmosphere_anchor'])]='myvillage:qianji_atmosphere_anchor[facing=north]'
    return dict(scene=scene,config=cfg,blocks=blocks,converted=converted,profiles=profiles,roles=sorted(roles),special=special,identity=identity())

if __name__=='__main__':
    d=compile_components();print(json.dumps(dict(blocks=len(d['blocks']),profiles=len(d['profiles']),banks=math.ceil(len(d['profiles'])/32),max_boxes=max(map(len,d['profiles'])),trees=len(d['scene']['tree_sites'])),ensure_ascii=False))
