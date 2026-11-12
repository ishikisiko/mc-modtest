"""总舵正式方块模型及按真实细分碰撞进行的步行连通性检查。"""
from __future__ import annotations
from collections import defaultdict,deque
from functools import lru_cache
import gzip,json,math
from pathlib import Path
from .components import compile_components,identity,ROOT,FACINGS
from tools.buildgen.massing import MassingGraph,Node
from tools.buildgen.compound import BuildingSlot

@lru_cache(maxsize=1)
def data():
    d=compile_components();return d

class Collision:
    def __init__(self,d):
        self.d=d;self.columns=defaultdict(list);self.rules=d['config'];self.noncollision=set(self.rules['non_collision_roles'])|set(d['special']);self.cache={};self.shape_cache={}
        for (x,y,z),s in d['blocks'].items():self.columns[x,z].append((y,s))
    def shape(self,s):
        if s in self.shape_cache:return self.shape_cache[s]
        if s.startswith('myvillage:qianji_carved_'):
            bank=int(s.split('_carved_',1)[1].split('[',1)[0]);props=dict(p.split('=') for p in s.split('[',1)[1][:-1].split(','));index=bank*32+int(props['shape']);turn=FACINGS.index(props['facing']);raw=self.d['profiles'][min(index,len(self.d['profiles'])-1)];boxes=[]
            for b in raw:
                if b[6] in self.noncollision:continue
                a=list(b[:6])
                for _ in range(turn):a=[16-a[5],a[1],a[0],16-a[2],a[4],a[3]]
                boxes.append(tuple(v/16 for v in a))
        elif s.startswith(('minecraft:water','minecraft:air')) or s.removeprefix('myvillage:qianji_') in self.noncollision:boxes=[]
        elif s.startswith('minecraft:bamboo'):boxes=[(.375,0,.375,.625,1,.625)]
        else:boxes=[(0,0,0,1,1,1)]
        self.shape_cache[s]=boxes;return boxes
    def levels(self,ix,iz):
        key=ix,iz
        if key in self.cache:return self.cache[key]
        x,z=ix/4+.25,iz/4+.25;half=.3-1e-5;intervals=[]
        for bx in range(math.floor(x-half),math.floor(x+half)+1):
            for bz in range(math.floor(z-half),math.floor(z+half)+1):
                for y,state in self.columns.get((bx,bz),[]):
                    for a,b,c,d,e,f in self.shape(state):
                        if bx+d>x-half and bx+a<x+half and bz+f>z-half and bz+c<z+half:intervals.append((y+b,y+e))
        intervals.sort();merged=[]
        for a,b in intervals:
            if merged and a<=merged[-1][1]+1e-6:merged[-1]=(merged[-1][0],max(merged[-1][1],b))
            else:merged.append((a,b))
        result=[b for i,(a,b) in enumerate(merged) if (i+1==len(merged) or merged[i+1][0]-b>=1.8-1e-5)]
        self.cache[key]=result;return result
    def resolve(self,p):
        x,y,z=p;ix=round(x*2)*2;iz=round(z*2)*2;levels=self.levels(ix,iz)
        if not levels or min(abs(v-y) for v in levels)>1.25:raise ValueError(f'功能点没有站立空间 {p} {levels}')
        return ix,min(levels,key=lambda v:abs(v-y)),iz
    def connected(self,spec):
        start=self.resolve(spec['paths'][0]);targets={};invalid=[]
        raw={r['id']+'_'+str(i+1):l['target'] for r in spec['rooms'] for i,l in enumerate(r['levels'])}
        raw.update({t['id']:t['position'] for t in spec.get('extra_targets',[])})
        for name,p in raw.items():
            try:targets[name]=self.resolve(p)
            except ValueError as e:invalid.append([name,str(e)])
        if invalid:raise ValueError('总舵站立点不成立 '+str(invalid))
        targets['jianghu_board']=self.resolve(spec['reader']);targets['entrance']=start
        wanted=set(targets.values());seen={start};queue=deque([start]);step=.6+1e-6
        while queue and wanted:
            ix,y,iz=queue.popleft();wanted.discard((ix,y,iz))
            for dx,dz in [(2,0),(-2,0),(0,2),(0,-2)]:
                xx,zz=ix+dx,iz+dz
                if not(0<=xx<168*4 and 0<=zz<184*4):continue
                for yy in self.levels(xx,zz):
                    node=xx,yy,zz
                    if node in seen or abs(yy-y)>step*2:continue
                    # 半格采样之间还核对四分之一格的支撑，避免跨过窄踏步或净空障碍。
                    if not any(abs(mid-y)<=step and abs(yy-mid)<=step for mid in self.levels(ix+dx//2,iz+dz//2)):continue
                    seen.add(node);queue.append(node)
        bad=[name for name,p in targets.items() if p in wanted]
        self.last_seen=seen;self.target_states=targets
        if bad:raise ValueError('总舵实际碰撞不能从入口到达 '+str(bad))
        return dict(reachable_targets=len(targets),visited=len(seen),headroom=1.8,maximum_step=.6,geometry='authored_sixteenth_block_collision',resolved={n:[x/4+.25,y,z/4+.25] for n,(x,y,z) in targets.items()})

def build(model_class,spec,validate=True):
    d=data();m=model_class(spec);m.blocks=dict(d['blocks']);m.h=max(p[1] for p in m.blocks)+1;m.layers={p:'arch' for p in m.blocks};m.signs=[]
    for room in spec['rooms']:
        graph=MassingGraph(meta={'function':room['name'],'roof':room['roof']});levels=[];x,z,w,depth=room['box']
        for i,l in enumerate(room['levels']):
            graph.add(Node('level_'+str(i+1),'great_hall_volume',(x,l['floor_y'],z),(w,l['height'],depth),attach_to=None if i==0 else 'level_'+str(i),meta={'use':l['use'],'roof':room['roof']}))
            m.target(room['id']+'_'+str(i+1),l['target']);levels.append(dict(use=l['use'],floor_y=l['floor_y'],clear_height=l['height']-.6,storey_height=l['height'],box=room['box'],target=l['target']))
        m.compound.building_slots.append(BuildingSlot(room['id'],room['kind'],(x,room['floor_y'],z),graph,{(xx,zz) for xx in range(x,x+w) for zz in range(z,z+depth)}))
        m.rooms.append(dict(id=room['id'],name=room['name'],use='；'.join(l['use'] for l in room['levels']),rect=room['box'],floors=len(levels),levels=levels))
    for t in spec.get('extra_targets',[]):m.target(t['id'],t['position'])
    m.target('jianghu_board',spec['reader']);m.target('entrance',spec['paths'][0])
    for x,z,w,depth,feet in [(7,6,154,57,0),(9,64,150,43,3),(10,110,148,65,6),(52,128,65,49,10)]:
        for zz in range(z,z+depth):
            for xx in range(x,x+w):m.terrain[zz*m.w+xx]=feet
    for pos,state in m.blocks.items():
        if state=='myvillage:qianji_lamp':m.lights.append(pos)
        elif state.startswith('myvillage:qianji_carved_'):
            bank=int(state.split('_carved_',1)[1].split('[',1)[0]);shape=int(state.split('shape=',1)[1].split(']',1)[0]);index=bank*32+shape
            if any(b[6]=='lamp' for b in d['profiles'][index]):m.lights.append(pos)
    checks=Collision(d).connected(spec) if validate else dict(reachable_targets=len(m.targets),geometry='not_checked')
    return m,checks

if __name__=='__main__':
    spec=json.loads((ROOT/'blueprints/qianji/zhongzhou_headquarters.json').read_text());d=data();print(json.dumps(Collision(d).connected(spec),ensure_ascii=False))
