"""从当前城市导出资源读取门内只读地坪，保留原建筑并绕开阻挡。"""
from __future__ import annotations
import gzip,hashlib,heapq,io,json,struct
from pathlib import Path
from buildgen.nbtread import _read_utf,_read_payload,state_string

EMPTY={'minecraft:air','minecraft:cave_air','minecraft:void_air','minecraft:vine','minecraft:short_grass','minecraft:tall_grass','minecraft:fern','minecraft:large_fern','minecraft:snow','minecraft:dead_bush','minecraft:poppy','minecraft:dandelion','minecraft:red_mushroom','minecraft:brown_mushroom'}
class CitySource:
    def __init__(self,root,region):
        self.root=Path(root);self.path=self.root/f'data/myvillage/capitals/v4/capital_{region}/city.json'
        self.raw=self.path.read_bytes();self.city=json.loads(self.raw);self.w,self.h,self.d=self.city['size'];t=self.city['terrain']
        path=self.resource(t['resource']);raw=gzip.decompress(path.read_bytes());self.terrain=struct.unpack('<'+'h'*(self.w*self.d),raw)
        assert hashlib.sha256(path.read_bytes()).hexdigest()==t['sha256'],'城市地形摘要不一致'
        self.tiles={(v['x']//16,v['z']//16):v for v in self.city['tiles']};self.cache={};self.floors={};self.source_hashes={}
    def resource(self,ident,structure=False):return self.root/('data/'+ident.replace(':','/structure/' if structure else '/',1)+('.nbt' if structure else ''))
    def block(self,x,y,z):
        key=x//16,z//16
        if key not in self.cache:
            blocks={};tile=self.tiles.get(key)
            if tile:
                p=self.resource(tile['template'],True);raw=p.read_bytes();h=hashlib.sha256(raw).hexdigest();assert h==tile['sha256'],'城市模板摘要不一致'
                self.source_hashes[tile['template']]=h;b=io.BytesIO(gzip.decompress(raw));b.read(1);_read_utf(b);nbt=_read_payload(b,10);palette=[state_string(v) for v in nbt['palette']]
                blocks={tuple(v['pos']):palette[v['state']] for v in nbt['blocks']}
            self.cache[key]=blocks
        return self.cache[key].get((x%16,y,z%16),'minecraft:stone' if y<=self.terrain[z*self.w+x] else 'minecraft:air')
    @staticmethod
    def empty(state):return state.split('[')[0] in EMPTY or 'sign' in state.split('[')[0]
    def floor(self,x,z,probe,clearance):
        if not(0<=x<self.w and 0<=z<self.d):return None
        key=x,z
        if key not in self.floors:
            expected=self.terrain[z*self.w+x]+1;found=None
            for d in range(probe+1):
                for sign in ([0] if d==0 else [1,-1]):
                    y=expected-1+d*sign;state=self.block(x,y,z);base=state.split('[')[0]
                    if self.empty(state) or base in ['minecraft:water','minecraft:lava'] or base.endswith(('_leaves','_log','_wood','_fence','_wall')):continue
                    if all(self.empty(self.block(x,y+dy,z)) for dy in range(1,clearance+1)):
                        found=y*2+(1 if '_slab[' in state and 'type=bottom' in state else 2);break
                if found is not None:break
            self.floors[key]=found
        return self.floors[key]
    def corridor(self,x,z,facing,width,probe,clearance,side,max_step2):
        nx,nz={'south':(0,1),'north':(0,-1),'east':(1,0),'west':(-1,0)}[facing];tx,tz=nz,-nx
        distance=self.d-z if nz==1 else z+1 if nz==-1 else self.w-x if nx==1 else x+1
        start=(0,0);goal=(0,distance-1);cache={}
        def section(point):
            if point not in cache:
                t,s=point;rows=[(x+nx*s+tx*(t+k),z+nz*s+tz*(t+k)) for k in range(-width//2+1,width//2+1)]
                heights=[self.floor(a,b,probe,clearance) for a,b in rows]
                cache[point]=None if any(h is None for h in heights) or any(abs(a-b)>max_step2 for a,b in zip(heights,heights[1:])) else list(zip(rows,heights))
            return cache[point]
        if section(start) is None or section(goal) is None:raise ValueError('正式门洞或城域边缘缺少连续只读地坪')
        queue=[(distance,0,start)];cost={start:0};parent={};seen=0
        while queue:
            _,g,p=heapq.heappop(queue)
            if g!=cost[p]:continue
            if p==goal:break
            seen+=1
            if seen>(distance+2)*(side*2+1):raise ValueError('门内通道搜索预算耗尽')
            for dt,ds in [(0,1),(-1,0),(1,0),(0,-1)]:
                q=p[0]+dt,p[1]+ds
                if not(-side<=q[0]<=side and 0<=q[1]<distance):continue
                rows=section(q)
                if rows is None:continue
                if max(abs(a[1]-b[1]) for a,b in zip(section(p),rows))>max_step2:continue
                ng=g+10+abs(q[0])*.01+sum(abs(a[1]-b[1]) for a,b in zip(section(p),rows))*.05
                if ng<cost.get(q,float('inf')):cost[q]=ng;parent[q]=p;heapq.heappush(queue,(ng+10*(abs(q[0])+abs(q[1]-goal[1])),ng,q))
        else:raise ValueError('正式城门到城域边缘的只读通道不连通')
        path=[goal]
        while path[-1]!=start:path.append(parent[path[-1]])
        cells={}
        for p in reversed(path):
            for pos,h in section(p):
                if pos in cells and cells[pos]!=h:raise ValueError('只读门内通道高度不一致')
                cells[pos]=h
        return [[a,b,h] for (a,b),h in sorted(cells.items())]
