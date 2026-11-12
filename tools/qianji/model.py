"""千机阁的语义体量与完整方块施工，供预览和正式导出共同消费。"""
from __future__ import annotations
from collections import deque,defaultdict
from dataclasses import dataclass
import json,math
from pathlib import Path
from buildgen.groups import get_group
from buildgen.massing import MassingGraph,Node
from buildgen.compound import CompoundGraph,CompoundVariant,BuildingSlot,ParcelNode
from buildgen.modset import load_modset
ROOT=Path(__file__).resolve().parents[2]
AIR='minecraft:air'

class Model:
    def __init__(self,spec):
        self.spec=spec;self.w,self.d=spec['size'];self.blocks={};self.layers={};self.targets=[];self.rooms=[];self.signs=[]
        self.material=json.loads((ROOT/'blueprints/qianji/materials.json').read_text());self.lighting=json.loads((ROOT/'blueprints/qianji/lighting.json').read_text());self.lights=[];self.terrain=[0]*(self.w*self.d)
        self.group=get_group('qianji');self.h=1
        self.compound=CompoundGraph(self.group.style_id,0,CompoundVariant(spec['scale'],'authored','authored','sweeping_eave_roof','authored'),(self.w,self.d),self.w//2)
    def put(self,x,y,z,state,layer='arch'):
        if not(0<=x<self.w and 0<=z<self.d and y>=0):raise ValueError(f"千机阁施工越界 {self.spec['id']} {(x,y,z)} {state}")
        self.blocks[x,y,z]=self.material.get(state,state);self.layers[x,y,z]=layer;self.h=max(self.h,y+1)
    def box(self,x,y,z,w,h,d,state,layer='arch'):
        for yy in range(y,y+h):
            for zz in range(z,z+d):
                for xx in range(x,x+w):self.put(xx,yy,zz,state,layer)
    def clear(self,x,y,z,w,h,d):self.box(x,y,z,w,h,d,AIR)
    def floor(self,x,feet,z,w,d,state='paving',layer='ground'):self.box(x,feet-1,z,w,1,d,state,layer)
    def target(self,id,p):self.targets.append(dict(id=id,position=list(p)))
    def platform(self,x,z,w,d,feet):
        self.box(x,0,z,w,feet,d,'base','base');self.floor(x,feet,z,w,d,'paving')
        for pz in range(z,z+d):
            for px in range(x,x+w):self.terrain[pz*self.w+px]=feet-1
        for zz in [z,z+d-1]:self.box(x,max(0,feet-3),zz,w,1,1,'foundation','base')
    def stairs(self,cx,z,low,high,width):
        for i in range(high-low):
            y=low+i;self.box(cx-width//2,0,z+i,width,y+1,1,'base','base')
            self.box(cx-width//2,y,z+i,width,1,1,'stairs','arch')
            self.clear(cx-width//2,y+1,z+i,width,4,1)
        self.floor(cx-width//2,high,z+high-low,width,3)
        self.clear(cx-width//2,high,z+high-low,width,4,3)
    def gallery(self,a,b,feet,width):
        x,z=a;xx,zz=b
        x0,z0=min(x,xx)-width//2,min(z,zz)-width//2
        w,d=abs(xx-x)+width,abs(zz-z)+width
        self.floor(x0,feet,z0,w,d,'floor','arch');self.clear(x0,feet,z0,w,5,d)
        if w>d:
            for p in range(x0,x0+w,6):
                for q in [z0,z0+d-1]:self.box(p,1,q,1,feet+4,1,'column')
        else:
            for q in range(z0,z0+d,6):
                for p in [x0,x0+w-1]:self.box(p,1,q,1,feet+4,1,'column')
        roof(self,x0,feet+5,z0,w,d,'sweeping_eave_roof')
    def hall(self,room):
        x,z,w,d=room['box'];cx=x+w//2;feet=room['floor_y'];shaft_x=x+w-max(v['inset'] for v in room['levels'])-9;shaft_z=z+max(v['inset'] for v in room['levels'])+3
        maxrise=max(v['height'] for v in room['levels']);run=(maxrise+1)//2
        if d<run+7:raise ValueError('楼梯井深度不足：'+room['id'])
        self.platform(x-1,z-1,w+2,d+2,feet)
        graph=MassingGraph(meta={'function':room['name'],'roof':room['roof']});next_y=feet
        for i,plan in enumerate(room['levels']):
            inset=plan['inset'];graph.add(Node('level_'+str(i+1),'great_hall_volume',(x+inset,next_y,z+inset),(w-2*inset,plan['height'],d-2*inset),attach_to=None if i==0 else 'level_'+str(i),meta={'use':plan['use'],'roof':room['roof']}));next_y+=plan['height']
        self.compound.building_slots.append(BuildingSlot(room['id'],room['kind'],(x,room['floor_y'],z),graph,{(xx,zz) for xx in range(x,x+w) for zz in range(z,z+d)}))
        levels=[];stair_jobs=[]
        for level,(plan,node) in enumerate(zip(room['levels'],graph.volumes())):
            xx,feet,zz=node.origin;ww,hh,dd=node.size;top=feet+hh
            self.clear(xx,feet,zz,ww,hh,dd);self.floor(xx,feet,zz,ww,dd,'floor','arch' if level==0 else 'upper')
            # 楼梯井贯通楼板；中庭上层保留围廊而非重复整层楼板。
            if level:self.clear(shaft_x,feet-1,shaft_z,6,1,run+3)
            if room['kind']=='atrium' and level:
                self.clear(xx+ww//4,feet-1,zz+dd//4,ww//2,1,dd//2)
            layer='arch' if level==0 else 'upper';spacing=room.get('column_spacing',6);column_width=room.get('column_width',1);sill=room.get('window_sill',2);head=room.get('window_head',2);door=9 if self.spec['scale']=='headquarters' else 3
            cut=max(2,min(ww,dd)//5)
            def inside(px,pz):
                if not(xx<=px<xx+ww and zz<=pz<zz+dd):return False
                if room.get('shape')!='octagon':return True
                return min(px-xx,xx+ww-1-px)+min(pz-zz,zz+dd-1-pz)>=cut
            for wx in range(xx,xx+ww):
                for wz in range(zz,zz+dd):
                    if not inside(wx,wz):continue
                    if all(inside(wx+dx,wz+dz) for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]):continue
                    is_front=wz==zz;is_back=wz==zz+dd-1
                    entry=(is_front or is_back) and abs(wx-cx)<=door//2
                    side_entry=room['kind']=='gate' and (is_front or is_back) and any(abs(wx-(cx+sign*ww//3))<=3 for sign in (-1,1))
                    if room['kind']=='open' and level==0 and is_front:
                        if (wx-xx)%6!=0:continue
                    for yy in range(feet,top):
                        if entry and yy<feet+(18 if room['kind']=='gate' else min(room.get('portal_height',6),hh-2)):continue
                        if side_entry and yy<feet+10:continue
                        state='wall'
                        if ((wx-xx)%spacing<column_width if is_front or is_back else (wz-zz)%spacing<column_width):state='column'
                        elif feet+sill<=yy<top-head:state='rail'
                        self.put(wx,yy,wz,state,layer)
            if room.get('shape')=='octagon':
                for wx in range(xx,xx+ww):
                    for wz in range(zz,zz+dd):
                        if not inside(wx,wz):self.clear(wx,feet-1,wz,1,hh+1,1)
            # 大柱与横梁形成完整主体；前后门洞上方保留承重梁。
            self.box(xx,top-1,zz,ww,1,1,'beam',layer);self.box(xx,top-1,zz+dd-1,ww,1,1,'beam',layer)
            self.box(xx,top-1,zz,1,1,dd,'column',layer);self.box(xx+ww-1,top-1,zz,1,1,dd,'column',layer)
            if level<len(room['levels'])-1:
                stair_jobs.append((shaft_x,shaft_z,feet,hh,layer))
                # 檐廊与腰檐位于楼身以外，保持楼心楼梯净空。
                for step in range(3):
                    ex,ez=xx-2+step,zz-2+step;ew,ed=ww+4-step*2,dd+4-step*2
                    for q in [ez,ez+ed-1]:self.floor(ex,top-1+step//2,q,ew,1,'roof_slab','roof')
                    for q in [ex,ex+ew-1]:self.floor(q,top-1+step//2,ez,1,ed,'roof_slab','roof')
            else:
                roof(self,xx,top,zz,ww,dd,room['roof'])
                if room.get('roof_accent'):
                    span=min(ww,dd);py=top+(span+3)//4;px=xx+span//2-1;length=max(1,ww-span+2)
                    self.box(px,py+1,zz+dd//2,length,1,1,'metal','detail')
                    for rx in [px,px+length-1]:self.box(rx,py+2,zz+dd//2,1,3,1,'gold','detail')
            if room.get('interior_colonnade'):
                for px in [xx+ww//4,xx+ww*3//4]:
                    for pz in [zz+dd//4,zz+dd*3//4]:self.box(px,feet,pz,2,hh,2,'column',layer)
                for pz in [zz+dd//4,zz+dd*3//4]:self.box(xx,top-2,pz,ww,2,2,'beam',layer)

            # 各层功能围绕中央通路布置，楼梯一侧不摆家具。
            self.furnish(room['id'],plan['use'],xx+2,feet,zz+3,max(3,ww-14),dd-6)
            target=(xx+min(5,ww//3),feet,zz+min(5,dd//3))
            self.target(room['id']+'_'+str(level+1),target)
            levels.append(dict(use=plan['use'],floor_y=feet,clear_height=hh-1,storey_height=hh,box=[xx,zz,ww,dd],target=list(target)))
            feet=top
        for job in stair_jobs:self.switchback(*job)
        self.signs.append(dict(position=[cx,room['floor_y']+room['levels'][0]['height']-1,z-1],facing='north',text=room['name']))
        self.put(cx,room['floor_y']+room['levels'][0]['height']-1,z-1,'minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]','detail')
        self.rooms.append(dict(id=room['id'],name=room['name'],rect=room['box'],y=room['floor_y'],floors=len(levels),use='；'.join(v['use'] for v in levels),levels=levels,roof_top=self.h))
    def switchback(self,x,z,feet,height,layer):
        first=(height+1)//2;second=height-first
        for i in range(first):
            self.box(x,feet+i-1,z+i,2,1,1,'floor',layer)
            self.box(x,feet+i,z+i,2,1,1,'stairs',layer)
            self.clear(x,feet+i+1,z+i,2,3,1)
        self.floor(x,feet+first,z+first,6,2,'floor',layer)
        for i in range(second):
            yy=feet+first+i;zz=z+first-i-1
            self.box(x+4,yy-1,zz,2,1,1,'floor',layer)
            self.box(x+4,yy,zz,2,1,1,self.material['stairs'].replace('facing=south','facing=north'),layer)
            self.clear(x+4,yy+1,zz,2,3,1)
        self.floor(x+2,feet+height,z-1,4,2,'floor',layer)
    def furnish(self,id,use,x,y,z,w,d):
        if '卷' in use or '档' in use or '图' in use:
            for zz in range(z,z+d,4):self.box(x,y,zz,2,3,2,'books','furniture')
            self.put(x+4,y,z+d-2,'cartography','furniture')
        elif '器物' in use or '储备' in use:
            for zz in range(z,z+d,4):self.box(x,y,zz,2,2,2,'storage','furniture')
        elif '起居' in use or '静修' in use or '闭关' in use:
            for zz in [z,z+d-2]:self.box(x,y,zz,3,1,2,'floor','furniture')
        else:
            for zz in [z+d//2,z+d-2]:self.box(x,y+1,zz,max(3,w//2),1,1,'desk','furniture')
    def illuminate(self):
        policy=self.lighting;step=policy['spacing'];border=policy['border']
        # 地灯与原地板齐平；只替换已有楼板，保留中庭、楼梯井和门外台阶。
        for room in self.rooms:
            for index,level in enumerate(room['levels']):
                x,z,w,d=level['box'];feet=level['floor_y'];layer='upper' if index else 'arch'
                for zz in range(z+border,z+d-border,step):
                    for xx in range(x+border,x+w-border,step):
                        if self.blocks.get((xx,feet-1,zz))!=self.material['floor'] or self.blocks.get((xx,feet,zz),AIR)!=AIR:continue
                        for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                            p=(xx+dx,feet-1,zz+dz)
                            if self.blocks.get(p)==self.material['floor']:self.put(*p,policy['frame_material'],layer)
                        self.put(xx,feet-1,zz,policy['source_material'],layer);self.lights.append([xx,feet-1,zz])
    def instrument(self,cx,feet,cz,radius):
        for x in range(cx-radius,cx+radius+1):
            for z in range(cz-radius,cz+radius+1):
                r=math.hypot(x-cx,z-cz)
                if radius-2<=r<=radius:self.put(x,feet,z,'metal','detail')
        for dy in range(radius*2+1):
            for dx in range(-radius,radius+1):
                r=math.hypot(dx,dy-radius)
                if radius-1.2<=r<=radius:
                    self.put(cx+dx,feet+dy,cz,'metal','detail')
        for x in [cx-radius,cx+radius]:self.box(x-1,feet-3,cz-2,3,8,5,'base','detail')
        self.box(cx-2,feet+radius-2,cz-2,5,5,5,'gold','detail')
    def connected(self):
        start=tuple(self.spec['paths'][0]);self.target('entrance',start)
        # 台阶、半砖和足下完整支撑分别按实际方块碰撞高度检查。
        cache={}
        def levels(x,z):
            key=x,z
            if key in cache:return cache[key]
            out=[]
            for y in range(self.h+1):
                floor=self.blocks.get((x,y-1,z),AIR);a=self.blocks.get((x,y,z),AIR);b=self.blocks.get((x,y+1,z),AIR)
                if floor.split('[',1)[0] not in (AIR,'minecraft:water','minecraft:lava') and not any(v in floor for v in ['fence','_sign[','_wall_sign[','type=bottom']) and a==AIR and b==AIR:out.append(y)
            cache[key]=out;return out
        seen={start};queue=deque([start]);remaining={tuple(t['position']) for t in self.targets}
        while queue and remaining:
            x,y,z=queue.popleft();remaining.discard((x,y,z))
            for xx,zz in [(x+1,z),(x-1,z),(x,z+1),(x,z-1)]:
                if not(0<=xx<self.w and 0<=zz<self.d):continue
                for yy in levels(xx,zz):
                    p=xx,yy,zz
                    if abs(yy-y)<=1 and p not in seen:seen.add(p);queue.append(p)
        bad=[t['id'] for t in self.targets if tuple(t['position']) in remaining]
        if bad:raise ValueError(f"{self.spec['id']} 实际体素不能从入口到达 {bad}")
        return dict(reachable_targets=len(self.targets),visited=len(seen),headroom=2,maximum_step=1)

# 本生成族的 roof 注册表；样式选择只使用注册 ID。
def hip(m,x,y,z,w,d):
    for i in range((min(w,d)+3)//2):
        xx,zz=x-2+i,z-2+i;ww,dd=w+4-2*i,d+4-2*i
        yy=y+i//2
        for px in range(xx,xx+ww):
            for pz in range(zz,zz+dd):
                if px in (xx,xx+ww-1) or pz in (zz,zz+dd-1):
                    state=m.material['roof_slab'] if i%2==0 else m.material['roof']
                    m.put(px,yy,pz,state,'roof')
    # 脊线和角部上挑均为实体构件。
    peak=y+(min(w,d)+3)//4
    m.box(x+min(w,d)//2-1,peak,z+d//2,max(1,w-min(w,d)+2),1,1,'roof','roof')
    for px,pz in [(x-2,z-2),(x+w+1,z-2),(x-2,z+d+1),(x+w+1,z+d+1)]:m.put(px,y+1,pz,'roof_slab','roof')
def octagonal(m,x,y,z,w,d):
    for i in range((min(w,d)+3)//2):
        xx,zz=x-2+i,z-2+i;ww,dd=w+4-2*i,d+4-2*i;cut=max(1,min(ww,dd)//5)
        def inside(px,pz):return xx<=px<xx+ww and zz<=pz<zz+dd and min(px-xx,xx+ww-1-px)+min(pz-zz,zz+dd-1-pz)>=cut
        for px in range(xx,xx+ww):
            for pz in range(zz,zz+dd):
                if inside(px,pz) and any(not inside(px+dx,pz+dz) for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]):m.put(px,y+i//2,pz,'roof_slab' if i%2==0 else 'roof','roof')
ROOFS={'sweeping_eave_roof':hip,'hip_roof':hip,'pyramidal_roof':octagonal}
def roof(m,x,y,z,w,d,id):
    if id not in ROOFS:raise ValueError('未注册的千机阁屋顶：'+id)
    ROOFS[id](m,x,y,z,w,d)

def armillary_sphere(m,cx,feet,cz,radius):
    cy=feet+radius
    # 四个有厚度的实体铜环交错成完整浑天仪，中心为铜玉色机枢。
    for plane,r in [('xy',radius),('yz',radius-3),('xz',radius-2),('diagonal',radius-1)]:
        for u in range(-r-1,r+2):
            for v in range(-r-1,r+2):
                if abs(math.hypot(u,v)-r)>1.05:continue
                if plane=='xy':x,y,z=cx+u,cy+v,cz
                elif plane=='yz':x,y,z=cx,cy+v,cz+u
                elif plane=='xz':x,y,z=cx+u,cy,cz+v
                else:x,y,z=cx+round(u*.70710678),cy+v,cz+round(u*.70710678)
                m.put(x,y,z,'metal','detail')
    for x in range(-6,7):
        for y in range(-6,7):
            for z in range(-6,7):
                if x*x+y*y+z*z<=36:m.put(cx+x,cy+y,cz+z,'gold' if y==0 else 'artifact_core','detail')
ARTIFACTS={'armillary_sphere':armillary_sphere}

def build(ident,validate=True):
    spec=json.loads((ROOT/'blueprints/qianji'/f'{ident}.json').read_text())
    if spec.get('generator')=='sculpted_headquarters':
        from qianji.headquarters_model import build as detailed
        return detailed(Model,spec,validate)
    m=Model(spec)
    m.floor(0,1,0,m.w,m.d,'ground')
    for x,z,w,d in spec.get('water',[]):m.floor(x,1,z,w,d,'water','water')
    for i,v in enumerate(spec.get('platforms',[])):
        m.platform(*v);x,z,w,d,y=v;m.compound.parcel_nodes.append(ParcelNode('terrace_'+str(i),'platform',{(xx,zz) for xx in range(x,x+w) for zz in range(z,z+d)},dict(floor_y=y)))
    for room in spec['rooms']:m.hall(room)
    for index,(a,b,y,width) in enumerate(spec.get('bridges',[])):
        m.gallery(a,b,y,width);m.target('gallery_'+str(index),((a[0]+b[0])//2,y,(a[1]+b[1])//2))
    # 主要院路沿作者声明的折线连接，并保留通行净空。
    for a,b in zip(spec['paths'],spec['paths'][1:]):
        x,y,z=a;xx,yy,zz=b
        if x!=xx and z!=zz:mid=[xx,y,z];segments=[(a,mid),(mid,b)]
        else:segments=[(a,b)]
        for aa,bb in segments:
            ax,ay,az=aa;bx,by,bz=bb;n=max(abs(bx-ax),abs(bz-az),1)
            width=next((v[4] for v in spec.get('stairs',[]) if ax==bx==v[0] and ay==v[2] and by==v[3]),5)
            last_y=ay
            for k in range(n+1):
                px=round(ax+(bx-ax)*k/n);pz=round(az+(bz-az)*k/n);py=round(ay+(by-ay)*k/n)
                state=m.material['stairs'] if py>last_y else m.material['paving']
                m.floor(px-width//2,py,pz,width,1,state);m.clear(px-width//2,py,pz,width,4,1)
                for qx in range(px-width//2,px+width//2+1):m.terrain[pz*m.w+qx]=py-1
                last_y=py
    for args in spec.get('access_stairs',[]):m.stairs(*args)
    if 'instrument' in spec:
        cx,fy,cz,r=spec['instrument'];m.platform(cx-r-2,cz-r-2,r*2+5,r*2+5,fy);m.instrument(*spec['instrument'])
        m.target('天机仪操作台',(cx-4,fy,cz-2))
        emblem=json.loads((ROOT/'blueprints/qianji/emblem.json').read_text());rows=emblem['rows'];width=len(rows[0]);height=len(rows)
        core=next(r for r in spec['rooms'] if r['id']=='core');x,z,w,d=core['box'];cx=x+w//2
        yy=core['floor_y']+sum(v['height'] for v in core['levels'][:-1])+3;zz=z+core['levels'][-1]['inset']-1
        m.box(cx-width//2-2,yy-2,zz,width+4,height+4,1,'base','detail')
        for y,row in enumerate(reversed(rows)):
            for k,value in enumerate(row):
                if value=='1':m.put(cx+width//2-k,yy+y,zz-1,'gold','detail')

    if 'roof_instrument' in spec:
        cx,fy,cz,r=spec['roof_instrument'];ARTIFACTS[spec['roof_instrument_form']](m,cx,fy,cz,r)
        core=next(v for v in spec['rooms'] if v['id']=='core');low=core['floor_y']+sum(v['height'] for v in core['levels'][:-1])
        for dx in [-r*2//3,r*2//3]:
            for dz in [-r*2//3,r*2//3]:m.box(cx+dx-1,low,cz+dz-1,2,fy+r-low+1,2,'base','detail')
        m.box(cx,fy,cz,1,r+1,1,'metal','detail')
    for stele in spec.get('state_steles',[]):
        x,y,z=stele['position'];m.box(x-1,y-1,z-1,3,3,3,'base','detail');m.box(x,y+2,z,1,7,1,'metal','detail')
        m.put(x,y+7,z-1,'minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]','detail')
        m.signs.append(dict(position=[x,y+7,z-1],facing='north',text=stele['name']+'分阁'))
    for tx,fy,tz in spec.get('trees',[]):
        m.put(tx,fy-1,tz,'minecraft:dirt','nature')
        for dy in range(6):m.put(tx,fy+dy,tz,'minecraft:oak_log[axis=y]','nature')
        for dy,radius in [(3,2),(4,3),(5,2),(6,1)]:
            for dx in range(-radius,radius+1):
                for dz in range(-radius,radius+1):
                    if dx==0 and dz==0 and dy<6:continue
                    if abs(dx)==radius and abs(dz)==radius:continue
                    distance=min(6,abs(dx)+abs(dz)+max(0,dy-5))
                    m.put(tx+dx,fy+dy,tz+dz,f'minecraft:oak_leaves[distance={max(1,distance)},persistent=false,waterlogged=false]','nature')
    for x,y,z in spec.get('monuments',[]):
        m.box(x-2,y-1,z-2,5,4,5,'base','detail');m.box(x-1,y+3,z-1,3,17,3,'metal','detail');m.box(x,y+20,z,1,4,1,'gold','detail')
    bx,by,bz=spec['board'];m.put(bx,by,bz,'myvillage:jianghu_board[facing=north]','furniture')
    rx,ry,rz=spec['reader'];m.floor(rx,ry,rz,1,1,'floor');m.clear(rx,ry,rz,1,3,1);m.target('jianghu_board',spec['reader'])
    m.illuminate()
    legality=load_modset('full').palette_block_errors(sorted(set(m.blocks.values())))
    if legality:raise ValueError(str(legality))
    checks=m.connected() if validate else {}
    return m,checks
