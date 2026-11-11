"""西坡独立样稿：逐栋标高、真实支承、生活巷和阶廊共用同一方块集合。"""
from __future__ import annotations

from collections import deque
import copy
import heapq
import math

import numpy as np

from .geometry import AIR, Estate, state


SOFT = {'air','water','wheat','sweet_berry_bush','short_grass','tall_grass','fern','torch','lantern'}


def simple_name(block):
    return block.split('[')[0].split(':')[-1]


class TerraceEstate(Estate):
    def __init__(self, estate, cfg, terrain, cut_limit):
        c=copy.deepcopy(estate);c['size']=[104,cfg['height'],117]
        self.cfg=cfg;self.terrain=np.array(terrain,dtype=int);self.original=self.terrain.copy()
        self.cut_limit=cut_limit;self.base_y=cfg['base_y'];self.floor_by_id={};self.portals={}
        self.route_records=[];self.path_field={};self.path_layers={};self.component={}
        self.support_heights=[];self.roof_junctions=[];self.ground_edits={}
        self.phase='';self.active=''
        for b in c['buildings']:
            raw=cfg['building_rect_overrides'].get(b['id'],b['rect'])
            b['rect']=[v+3 for v in raw]
            x0,z0,x1,z1=b['rect']
            h=self.terrain[z0-2:z1,x0-1:x1+1]
            b['floor']=int(h.max())-cut_limit
            self.floor_by_id[b['id']]=b['floor']
        for group in cfg['floor_groups']:
            floor=max(self.floor_by_id[key]for key in group)
            for b in c['buildings']:
                if b['id']in group:b['floor']=floor;self.floor_by_id[b['id']]=floor
        super().__init__(c)
        self.buildings={b['id']:b for b in c['buildings']}
        self.body_mask=np.zeros(self.terrain.shape,bool)

    def put(self,x,y,z,block,layer='architecture'):
        x,y,z=int(x),int(y),int(z)
        if not(0<=x<104 and 0<=z<117 and 0<=y<self.c['size'][1]):
            raise ValueError(f'台地构造越界 {(x,y,z)} {block}')
        if layer=='foundation' and self.phase=='buildings':return
        if layer=='roof' and block not in(None,AIR):
            # Eaves at a named adjoining building yield to its intact room body.
            for b in self.c['buildings']:
                if b['id']==self.active:continue
                a,c,e,f=b['rect']
                if a<=x<e and c<=z<f and b['floor']<=y<b['floor']+self.c['facade']['wall_height']:
                    self.roof_junctions.append((self.active,b['id'],x,y,z));return
        super().put(x,y,z,block,layer)
        if block in(None,AIR):self.component.pop((x,y,z),None)
        else:self.component[x,y,z]=self.active

    def box(self,x0,y0,z0,x1,y1,z1,block,layer='architecture'):
        if layer=='foundation'and self.phase=='buildings':return
        super().box(x0,y0,z0,x1,y1,z1,block,layer)

    def stairs_to_ground(self,*args):
        pass

    def portal(self,rect,floor,side,width=3):
        if side=='north':super().portal(rect,floor,side,width)

    def grade(self,x,z,top):
        if top>=self.terrain[z,x]:return
        cut=int(self.original[z,x])-top
        if cut>self.cut_limit:raise ValueError(f'整地超过原边界 {(x,z)}: {cut}>{self.cut_limit}')
        self.terrain[z,x]=top;self.ground_edits[x,z]=top

    def doorway(self,key,side):
        cache=(key,side)
        if cache in self.portals:return self.portals[cache]
        b=self.buildings[key];x0,z0,x1,z1=b['rect'];f=b['floor'];cx,cz=(x0+x1)//2,(z0+z1)//2
        offset=self.cfg.get('door_offsets',{}).get(key+':'+side,0)
        if side in('east','west'):cz+=offset
        else:cx+=offset
        if side=='north':p=(cx,z0-3);area=(cx-1,z0-3,cx+2,z0+2)
        elif side=='south':p=(cx,z1+1);area=(cx-1,z1-2,cx+2,z1+2)
        elif side=='east':p=(x1+1,cz);area=(x1-2,cz-1,x1+2,cz+2)
        else:p=(x0-2,cz);area=(x0-2,cz-1,x0+2,cz+2)
        self.active=key
        a,c,e,h=area
        for z in range(c,h):
            for x in range(a,e):
                self.grade(x,z,f)
                for y in range(f+1,f+5):self.put(x,y,z,None)
                self.put(x,f,z,'floor','floor')
                self.body_mask[z,x]=False
        self.portals[cache]=(*p,f+1)
        return self.portals[cache]

    def buildings_build(self):
        self.phase='buildings'
        for b in self.c['buildings']:
            self.active=b['id'];x0,z0,x1,z1=b['rect'];f=b['floor']
            self.body_mask[z0-1:z1+1,x0-1:x1+1]=True
            for z in range(z0-2,z1):
                for x in range(x0-1,x1+1):self.grade(x,z,f)
            for level in range(b['levels']):self.tier(b,level)
        for b in self.c['buildings']:
            self.active=b['id']
            if b['levels']>1:self.interior_stairs(b)
        self.phase=''
        for b in self.c['buildings']:
            self.active=b['id'];x0,z0,x1,z1=b['rect'];f=b['floor']
            spacing=self.cfg['foundation']['pier_spacing'];width=self.cfg['foundation']['pier_width']
            for z in range(z0-2,z1):
                for x in range(x0-1,x1+1):
                    ground=int(self.terrain[z,x]);drop=f-ground
                    self.support_heights.append(drop)
                    pier=((x-x0)%spacing<width and(z-z0)%spacing<width)or x in(x0,x1-1)and(z-z0)%spacing<width or z in(z0,z1-1)and(x-x0)%spacing<width
                    low=ground+1 if pier else max(ground+1,f-self.cfg['foundation']['beam_depth'])
                    for y in range(low,f):self.put(x,y,z,'stone','supports')
        # Shared ground-floor links in the front gate and gallery ensemble.
        f=self.floor_by_id['gate'];self.active='front_shared_doorways'
        for x in(43,60):
            for xx in range(x-2,x+3):
                for z in range(17,20):
                    for y in range(f+1,f+5):self.put(xx,y,z,None)
                    self.put(xx,f,z,'floor','floor');self.body_mask[z,xx]=False
        # A lower approach can enter under the gate's floor before reaching its
        # inner landing; this opening belongs to the authored gate passage only.
        cx=52
        for z in range(3,29):
            for x in range(cx-2,cx+3):
                for y in range(max(0,f-9),f+5):self.put(x,y,z,None)
                self.body_mask[z,x]=False
                if z>=20:self.put(x,f,z,'floor','floor')
        self.targets['gate_1']=(52,f+1,20)
        self.doorway('gate','south')
        for route in self.cfg['paths']['covered_links']:
            self.doorway(*route['from']);self.doorway(*route['to'])
        self.doorway('main','north')
        return self

    def route_xz(self,waypoints):
        result=[]
        for start,goal in zip(waypoints,waypoints[1:]):
            start,goal=tuple(start),tuple(goal);queue=[(0,0,start)];costs={start:0};parents={};found=False
            while queue:
                _,cost,p=heapq.heappop(queue)
                if cost!=costs[p]:continue
                if p==goal:found=True;break
                x,z=p
                for dx,dz in((1,0),(-1,0),(0,1),(0,-1)):
                    q=xx,zz=x+dx,z+dz
                    if not(1<=xx<103 and 1<=zz<116):continue
                    if self.body_mask[zz,xx]and q not in(start,goal):continue
                    ground=int(self.terrain[zz,xx]);prior=int(self.terrain[z,x])
                    next_cost=cost+10+min(40,abs(ground-prior)*3)
                    if next_cost<costs.get(q,10**12):
                        costs[q]=next_cost;parents[q]=p
                        h=10*(abs(xx-goal[0])+abs(zz-goal[1]));heapq.heappush(queue,(next_cost+h,next_cost,q))
            if not found:raise ValueError(f'阶廊平面无路 {start}->{goal}')
            chain=[goal]
            while chain[-1]!=start:chain.append(parents[chain[-1]])
            chain.reverse();result.extend(chain if not result else chain[1:])
        return result

    def brush(self,path,i):
        x,z=path[i]
        prev=path[max(0,i-1)];nxt=path[min(len(path)-1,i+1)]
        if i in(0,len(path)-1)or(prev[0]!=x and nxt[1]!=z)or(prev[1]!=z and nxt[0]!=x):
            return[(x+dx,z+dz)for dz in(-1,0,1)for dx in(-1,0,1)]
        if prev[0]!=nxt[0]:return[(x,z+dz)for dz in(-1,0,1)]
        return[(x+dx,z)for dx in(-1,0,1)]

    def body_clear(self,x,z,feet):
        for y in range(feet,feet+self.cfg['paths']['headroom']):
            s=self.blocks.get((x,y,z),AIR);name=simple_name(s)
            # Window planters and projecting shutters may be omitted only where
            # an authored connecting passage needs this exact clear envelope.
            removable=self.layers.get((x,y,z))in('supports','garden')or name.endswith('_trapdoor')
            if name not in SOFT and not removable:return False
        return True

    def profile(self,path,anchors):
        # Equal-level landings around every corner prevent overlapping treads
        # from assigning different heights to the same three-wide turn.
        equal=set()
        for i in range(1,len(path)-1):
            a,b,c=path[i-1],path[i],path[i+1]
            if(a[0]!=b[0]and b[1]!=c[1])or(a[1]!=b[1]and b[0]!=c[0]):equal.update((i-1,i))
        # Four-tread level landings also leave room for a later branch to turn
        # without touching a differently elevated approach tread.
        equal.update(range(min(3,len(path)-1)))
        equal.update(range(max(0,len(path)-4),len(path)-1))
        groups=[];start=0
        for i in range(len(path)-1):
            if i not in equal:groups.append(list(range(start,i+1)));start=i+1
        groups.append(list(range(start,len(path))))
        states={};back=[];max_support=self.cfg['paths']['max_structural_support']
        for group in groups:
            cells=set(p for i in group for p in self.brush(path,i))
            if any(not(0<=x<104 and 0<=z<117)for x,z in cells):raise ValueError('阶廊路幅越出批准地块')
            low=max(int(self.terrain[z,x])+1-self.cut_limit for x,z in cells)
            high=min(int(self.terrain[z,x])+1+max_support for x,z in cells)
            fixed={anchors[path[i]]for i in group if path[i]in anchors}
            fixed|={self.path_field[p]for p in cells if p in self.path_field}
            if len(fixed)>1:raise ValueError(f'路口标高冲突 {group} {fixed}')
            allowed=sorted(fixed)if fixed else range(low,high+1)
            next_states={};parents={}
            for feet in allowed:
                if not low<=feet<=high:continue
                if not all(self.body_clear(x,z,feet)for x,z in cells):continue
                preference=sum(abs(feet-int(self.terrain[z,x])-1)for x,z in cells)
                if not states:next_states[feet]=preference;parents[feet]=None
                else:
                    possibles=[(score+abs(feet-prior)*2,prior)for prior,score in states.items()if abs(feet-prior)<=1]
                    if possibles:
                        cost,parent=min(possibles);next_states[feet]=cost+preference;parents[feet]=parent
            if not next_states:
                raise ValueError(f'阶廊标高无解：段 {group[0]}，位置 {path[group[0]]}，允许 {low}..{high}，固定 {fixed}，前段 {sorted(states)}')
            states=next_states;back.append(parents)
        feet=min(states,key=states.get);levels=[]
        for parents in reversed(back):levels.append(feet);feet=parents[feet]
        levels.reverse();values=[0]*len(path)
        for group,feet in zip(groups,levels):
            for i in group:values[i]=feet
        return values

    def make_route(self,key,waypoints,anchors=None,covered=False,direct=False):
        self.active=key
        path=list(waypoints)if direct else self.route_xz(waypoints)
        anchors=dict(anchors or {})
        levels=self.profile(path,anchors);columns={};facings={}
        for i,(x,z)in enumerate(path):
            feet=levels[i];before=levels[max(0,i-1)];after=levels[min(len(path)-1,i+1)]
            lower=path[i-1]if i and before<feet else path[i+1]if i+1<len(path)and after<feet else None
            face=None
            if lower:
                dx,dz=x-lower[0],z-lower[1]
                face=('east'if dx>0 else'west')if dx else('south'if dz>0 else'north')
            for p in self.brush(path,i):
                if p in columns and columns[p]!=feet:raise ValueError(f'三格路幅自相交标高不符 {key} {p}')
                columns[p]=feet
                if face:facings[p]=face
        for(x,z),feet in columns.items():
            self.grade(x,z,feet-1)
            for y in range(feet,feet+self.cfg['paths']['headroom']):self.put(x,y,z,None)
            ground=int(self.terrain[z,x]);self.support_heights.append(feet-1-ground)
            for y in range(ground+1,feet-1):self.put(x,y,z,'stone','supports')
            face=facings.get((x,z))
            block=state('stone_brick_stairs',facing=face,half='bottom',shape='straight',waterlogged=False)if face else'minecraft:stone_bricks'
            self.put(x,feet-1,z,block,'paths');self.path_field[x,z]=feet
        self.route_records.append({'id':key,'covered':covered,'points':[[x,feet+self.base_y,z]for(x,z),feet in zip(path,levels)],'width':3})
        if covered:self.cover(path,levels)
        return path,levels

    def cover(self,path,levels):
        for i,(x,z)in enumerate(path):
            feet=levels[i];a=path[max(0,i-1)];b=path[min(len(path)-1,i+1)];along_x=a[0]!=b[0]
            for across in range(-2,3):
                xx,zz=(x,z+across)if along_x else(x+across,z)
                yy=feet+4+(2-abs(across))
                if not(0<=xx<104 and 0<=zz<117):continue
                if(xx,zz)in self.path_field and yy<self.path_field[xx,zz]+self.cfg['paths']['headroom']:continue
                if any((xx,yy+k,zz)in self.blocks for k in(-1,0,1,2)):continue
                face=('south'if across<0 else'north')if along_x else('east'if across<0 else'west')
                self.put(xx,yy,zz,state('stone_brick_stairs',facing=face,half='bottom',shape='straight',waterlogged=False),'roof')
            if i%6==0:
                for side in(-2,2):
                    xx,zz=(x,z+side)if along_x else(x+side,z)
                    if not(0<=xx<104 and 0<=zz<117):continue
                    if(xx,zz)in self.path_field:continue
                    for y in range(feet,feet+4):
                        if(xx,y,zz)not in self.blocks:self.put(xx,y,zz,state('oak_log',axis='y'),'architecture')

    def routes_build(self,external_anchors):
        ring=[]
        corners=[(1,1),(102,1),(102,115),(1,115),(1,1)]
        for a,b in zip(corners,corners[1:]):
            dx=(b[0]>a[0])-(b[0]<a[0]);dz=(b[1]>a[1])-(b[1]<a[1]);p=a
            while p!=b:ring.append(p);p=(p[0]+dx,p[1]+dz)
        ring.append(corners[0]);anchors=dict(external_anchors)
        if corners[0]not in anchors:
            anchors[corners[0]]=max(int(self.terrain[1,1])+1,int(self.terrain[:6,:6].max())+1-self.cut_limit)
        self.make_route('public_ring',ring,anchors,direct=True)
        # Public gate approach continues into the lower gate passage.
        landing=tuple(v+3 for v in self.cfg['paths']['gate_landing'])
        start=(52,1);end=landing;f=self.floor_by_id['gate']+1
        self.make_route('gate_entry',[start,(52,10),end],{start:self.path_field[start],end:f})
        # Restore the flat rear gate passage after the lower entry stair.
        self.active='gate_passage'
        for z in range(landing[1],29):
            for x in range(51,54):
                self.put(x,f-1,z,'floor','floor')
                for y in range(f,f+3):self.put(x,y,z,None)
        waypoints=[tuple(v+3 for v in p)for p in self.cfg['paths']['central_axis']]
        a=self.doorway('gate','south');b=self.doorway('main','north')
        waypoints[0]=a[:2];waypoints[-1]=b[:2]
        self.make_route('central_steps',waypoints,{a[:2]:a[2],b[:2]:b[2]})
        for item in self.cfg['paths']['covered_links']:
            print('构造阶廊',item['id'],flush=True)
            a=self.doorway(*item['from']);b=self.doorway(*item['to'])
            points=[a[:2],*[tuple(v+3 for v in p)for p in item['via']],b[:2]]
            self.make_route(item['id'],points,{a[:2]:a[2],b[:2]:b[2]},covered=True)
        return self

    def gardens_build(self):
        self.active='garden'
        pond=self.cfg['garden']['pond'];cx,cz=[v+3 for v in pond['center']];rx,rz=pond['radius']
        cells={(x,z)for z in range(cz-rz,cz+rz+1)for x in range(cx-rx,cx+rx+1)if((x-cx)/rx)**2+((z-cz)/rz)**2<=1}
        top=max(int(self.terrain[z,x])for x,z in cells);floor=top-2
        for x,z in cells:
            if(x,z)in self.path_field:raise ValueError('池塘占用既有阶梯')
            self.grade(x,z,floor)
            self.put(x,floor,z,'stone','garden')
            self.put(x,floor+1,z,state('water',level=0),'water')
            self.put(x,floor+2,z,state('water',level=0),'water')
            for dx,dz in((1,0),(-1,0),(0,1),(0,-1)):
                p=x+dx,z+dz
                if p not in cells:
                    xx,zz=p
                    if p in self.path_field:raise ValueError(f'池岸占用阶梯完整路幅 {p}')
                    for y in range(min(int(self.terrain[zz,xx])+1,floor),floor+4):self.put(xx,y,zz,'stone','garden')
        # The bridge is a real timber deck above the closed water basin.
        for x in range(cx-rx-1,cx+rx+2):
            for z in range(cz-1,cz+2):self.put(x,floor+3,z,'wood','garden')
        for x,z,h in self.cfg['garden']['trees']:
            x+=3;z+=3;y=int(self.terrain[z,x])+1
            if any((xx,zz)in self.path_field for xx in range(x-3,x+4)for zz in range(z-3,z+4)):continue
            if any((x,yy,z)in self.blocks for yy in range(y,y+h+4)):continue
            for yy in range(y,y+h):self.put(x,yy,z,state('oak_log',axis='y'),'vegetation')
            for dx in range(-2,3):
                for dz in range(-2,3):
                    for dy in range(-2,2):
                        if dx*dx+dz*dz+2*dy*dy>8:continue
                        p=x+dx,y+h+dy,z+dz
                        if p not in self.blocks:self.put(*p,state('oak_leaves',distance=1,persistent=False,waterlogged=False),'vegetation')
        return self


def coarse_connectivity(model,start):
    """离线体素检查，保留脚下支撑和三格净空；不声称游戏操作验收。"""
    nodes=set()
    def empty(x,y,z):
        name=simple_name(model.blocks.get((x,y,z),AIR))
        return name in SOFT and name!='water'and y>int(model.terrain[z,x])
    for z in range(117):
        for x in range(104):
            h=int(model.terrain[z,x]);tops={h+1}
            for y in range(h+1,model.c['size'][1]-2):
                s=model.blocks.get((x,y,z))
                if s and simple_name(s)not in SOFT and not simple_name(s).endswith(('_leaves','_trapdoor','_fence')):tops.add(y+1)
            for feet in tops:
                if all(empty(x,feet+i,z)for i in range(2)):nodes.add((x,feet,z))
    start=tuple(start)
    if start not in nodes:raise ValueError(f'外部入口无有效站位 {start}')
    seen={start};queue=deque([start])
    while queue:
        x,y,z=queue.popleft()
        for dx,dz in((1,0),(-1,0),(0,1),(0,-1)):
            for dy in(-1,0,1):
                p=x+dx,y+dy,z+dz
                if dy==1:
                    support=model.blocks.get((p[0],p[1]-1,p[2]),AIR)
                    face=('east'if dx>0 else'west')if dx else('south'if dz>0 else'north')
                    if not simple_name(support).endswith('_stairs')or 'facing='+face not in support:continue
                if p in nodes and p not in seen:seen.add(p);queue.append(p)
    targets={k:list(v)for k,v in model.targets.items()if k!='portals'}
    failed={k:v for k,v in targets.items()if tuple(v)not in seen}
    details={}
    for key,(x,y,z)in failed.items():
        nearest=min(seen,key=lambda p:abs(p[0]-x)+abs(p[1]-y)+abs(p[2]-z))
        details[key]={'is_node':(x,y,z)in nodes,'nearest_reachable':list(nearest),'at_feet':model.blocks.get((x,y,z)),
                      'at_head':model.blocks.get((x,y+1,z)),'ground':int(model.terrain[z,x])}
    failed_routes=[]
    for route in model.route_records:
        for i,(x,world_y,z)in enumerate(route['points']):
            p=(x,world_y-model.base_y,z)
            if p not in seen:failed_routes.append({'route':route['id'],'index':i,'point':list(p),
                'is_node':p in nodes,'feet_block':model.blocks.get(p),
                'head_block':model.blocks.get((p[0],p[1]+1,p[2])),
                'support':model.blocks.get((p[0],p[1]-1,p[2]))})
    bad_columns=[(x,y,z)for(x,z),y in model.path_field.items()if(x,y,z)not in seen]
    return{'nodes':len(nodes),'reachable':len(seen),'targets':targets,'failed':failed,'failed_details':details,
           'unreachable_route_points':len(failed_routes),'route_failures':failed_routes[:20],
           'route_width_columns':len(model.path_field),'unreachable_route_width_columns':len(bad_columns),
           'route_width_failures':[list(p)for p in bad_columns[:20]],
           'step_model':'上升一格须有正确朝向的楼梯；下行最多一格；两格头部净空。',
           'all_reachable':not failed and not failed_routes and not bad_columns}
