"""正式七府蓝图：独立 authored 布局 -> 实际建筑、地形、通路和既有府邸方块。"""
from __future__ import annotations

from collections import Counter,defaultdict,deque
import copy
import hashlib
import heapq
import json
import math
from pathlib import Path
from functools import lru_cache

from province_capitals.regional.v2.city import Capital
from province_capitals.regional.v2.buildings import Building
from province_capitals.regional.v2.common import AIR,DIR,FACING,DIRECTIONS,point,props,rotated_state
from province_capitals.environment_design.geometry import Scene
from buildgen.nbtread import read_gzipped_nbt,state_string

ROOT=Path(__file__).resolve().parents[3]

def read(path):return json.loads((ROOT/path).read_text())
def half_cells(box):
    a,b,e,f=box
    return {(x,z) for z in range(b,f) for x in range(a,e)}
def closed(box):return [box[0],box[1],box[2]-1,box[3]-1]
def rotate_cell(x,z,w,d,q):return ((x,z),(d-1-z,x),(w-1-x,d-1-z),(z,w-1-x))[q]
def rotate_position(x,z,w,d,q):return ((x,z),(d-z,x),(w-x,d-z),(z,w-x))[q]
def solid(state):
    return state not in (AIR,None) and not any(t in state for t in ('_door[','water[','minecraft:water','leaves[','_crop['))
def passable(state):return state in (None,AIR) or '_door[' in state
@lru_cache(maxsize=1)
def manor_catalog():return read('src/main/resources/data/myvillage/lord_manors/catalog.json')

class ManorPlanner:
    def __init__(self,spec,city,environment,catalog,**unused):
        self.spec=spec;self.layout=read(spec['sources']['layout']);self.catalog=catalog;self.env=copy.deepcopy(environment)
        if self.layout['schema']!=1 or self.layout['city_id']!=spec['city_id'] or city['city_id']!=spec['city_id']:
            raise ValueError('正式重排蓝图城市身份不一致')
        self.region=city['region_id'];self.source_city=copy.deepcopy(city)
        self.native_rules=read(spec['sources']['native_rules'])
        self.city=copy.deepcopy(city);self.city['dimensions']=self.layout['dimensions']
        self.city['buildings']=[];self.city['roads']=[];self.city['courtyards']=[];self.city['trees']=[];self.city['walls']=[]
        self.model=Capital(self.city);self.rules=spec['rules'];self.cfg=self.layout['construction']
        if self.cfg['door_width']!=1 or self.cfg['approach_width']!=1:raise ValueError('当前门洞和入户接路只支持一格宽')
        if self.cfg['headroom']<self.rules['headroom'] or self.cfg['road_step_limit']!=1:raise ValueError('不能放宽既定净空或台阶限界')
        self.width,self.height,self.depth=self.city['dimensions'];self.original_terrain=self.model.terrain[:]
        self.cache={};self.tags={};self.changes=Counter();self.templates={};self.summaries={};self.paths=[];self.doors=[];self.gardens=[];self.fixtures=[]
        self.public_passages=[];self.earthwork=[];self.issues=[];self.built=False;self.road_field={};self.road_owners=defaultdict(set)
        self.road_columns=set();self.private_owner={};self.building_columns={};self.core=set();self.manor_cells={};self.manor_boxes=[]
        self.goal_distances={};self.public_garden_columns=set();self.courtyards=[];self.access_field={};self.floor_columns=set()
        self.parcels={b['id']:b for b in self.layout['buildings']}
        self.removed=set(environment.get('remove_buildings',[]))|set(self.layout['retired_buildings'])
        active={b['id'] for b in city['buildings']}-set(environment.get('remove_buildings',[]))
        if set(self.parcels)|set(self.layout['retired_buildings'])!=active or set(self.parcels)&set(self.layout['retired_buildings']):
            raise ValueError('重排须逐栋覆盖在用建筑，且只撤并已声明住宅')
        source={b['id']:b for b in city['buildings']}
        if any(source[k]['role']!='house' or source[k]['family'] in ('shop_house','stilt_longhouse') for k in self.layout['retired_buildings']):
            raise ValueError('不能撤并功能建筑、商住门面或干栏住宅')
        for b in self.layout['buildings']:
            for p in half_cells(b['box']):
                if p in self.building_columns:raise ValueError('建筑拟用地重叠：'+b['id'])
                self.building_columns[p]=b['id']
        for g in self.layout['gardens']:
            if g['access']=='private':
                if g['owner'] not in self.parcels:raise ValueError('私院缺少在用主人')
                for p in half_cells(g['box']):self.private_owner[p]=g['owner']
            else:self.public_garden_columns.update(half_cells(g['box']))
        self.core=half_cells(self.layout['manor']['bounds'])|half_cells(self.layout['landmark']['bounds'])
        if self.core&self.building_columns.keys():raise ValueError('府邸或地标侵入保留建筑')
        if any(self.core&half_cells(g['box']) for g in self.layout['gardens']):raise ValueError('府邸或地标侵入保留庭院')
        if any(set(self.building_columns)&half_cells(g['box']) for g in self.layout['gardens']):raise ValueError('庭院侵入保留建筑地块')

    def tile(self,key):
        if key not in self.cache:self.cache[key]={p:r[0] for p,r in self.model.construction.tile(*key).items()}
        return self.cache[key]
    def get(self,p):
        x,y,z=p
        if not(0<=x<self.width and 0<=y<self.height and 0<=z<self.depth):return AIR
        v=self.tile((x//16,z//16)).get((x%16,y,z%16))
        if v is not None:return v
        top=self.model.ground(x,z)
        return self.model.material('GROUND_TOP') if y==top else self.model.material('GROUND_FILL') if y<top else AIR
    def set(self,p,state,tag,layer='building'):
        self.changes[tag]+=1
        self.model.construction.cell(p,state,tag,layer);x,y,z=p;key=(x//16,z//16)
        if key in self.cache:self.cache[key][x%16,y,z%16]=state
    def box(self,a,b,state,tag,layer='building'):
        self.changes[tag]+=(b[0]-a[0]+1)*(b[1]-a[1]+1)*(b[2]-a[2]+1)
        self.model.construction.box(a,b,state,tag,layer)
        for key in list(self.cache):
            if a[0]//16<=key[0]<=b[0]//16 and a[2]//16<=key[1]<=b[2]//16:del self.cache[key]
    def ground(self,x,z):return self.model.ground(x,z)
    def set_ground(self,x,z,y):
        if not 0<=y<self.height:raise ValueError('地形标高越界')
        old=self.ground(x,z);self.model.set_ground(x,z,y)
        if old!=y:self.earthwork.append({'column':[x,z],'air_y':y+1,'source_air_y':old+1})
    def clear(self,p,height=2):return all(passable(self.get((p[0],p[1]+n,p[2]))) for n in range(height))
    def walkable(self,p):return self.clear(p) and solid(self.get((p[0],p[1]-1,p[2])))
    def is_wet(self,x,z):
        kind=self.city['terrain']['kind'];t=self.city['terrain']
        if kind=='connected_supported_rift_banks':
            from province_capitals.regional.v2.common import lerp
            return abs(x-lerp(t['rift_x_by_z'],z))<t['half_width']
        water_y=self.city.get('water_top_y',t.get('water_top_y',t.get('sea_y')))
        return water_y is not None and self.ground(x,z)<water_y

    def prepare_buildings(self):
        original=Capital(self.source_city)
        for raw in self.source_city['buildings']:
            if raw['id'] not in self.parcels:continue
            placement=self.parcels[raw['id']];template=original.prepare_building(raw)
            source=copy.deepcopy(template.source);turn=placement.get('extra_rotation_quarters',0)
            source['facing']=DIRECTIONS[(FACING[source['facing']]+turn)%4]
            oldpos=source['position'][:];source['position']=[0,0,0];template.source=source;zero=template.summary()
            b=placement['box'];cx,cz=(b[0]+b[2]-1)//2,(b[1]+b[3]-1)//2
            lift=0
            if template.family.get('stilt'):
                lift=max(0,oldpos[1]-round(original.natural_air(oldpos[0],oldpos[2])))
            y=oldpos[1] if placement['status']=='retained' else self.ground(cx,cz)+1+lift
            source['position']=[b[0]-zero['bounds']['min'][0],y,b[1]-zero['bounds']['min'][2]]
            template.source=source;info=template.summary()
            actual=[info['bounds']['min'][0],info['bounds']['min'][2],info['bounds']['max'][0]+1,info['bounds']['max'][2]+1]
            if actual!=b:raise ValueError('原尺寸模板与选定地块不符：'+str((source['id'],actual,b)))
            if not 1<=y or info['bounds']['max'][1]>=self.height:raise ValueError('保留建筑Y越界：'+source['id'])
            self.templates[source['id']]=template;self.summaries[source['id']]=info
            footprint={point(source,x,0,z)[::2] for x in range(template.w) for z in range(template.d)}
            self.model.footprints[source['id']]=footprint
            self.model.occupied.update({p:source['id'] for p in footprint})
            self.floor_columns.update(point(source,x,0,z)[::2] for x,z in template.coverage[0])
            self.model.foundation(template,emit=False)
        if original.diagnostics['geometry_conflicts']:raise ValueError('原始建筑输入出现重叠，不能继续')

    def validate_passages(self):
        from province_capitals.regional.v2.common import rotate
        owners=set()
        for opening in self.layout['public_passages']:
            owner=opening['owner']
            if owner not in self.summaries or self.summaries[owner]['role']!='gate':raise ValueError('公共开口须属于现有门楼')
            if owner in owners:raise ValueError('门楼开口声明重复')
            owners.add(owner);template=self.templates[owner];src=template.source
            if opening['y']!=src['position'][1]:raise ValueError('公共开口须使用门楼首层标高')
            if opening['height']!=self.cfg['headroom']:raise ValueError('公共开口不能改变规定净空高度')
            bounds=opening['bounds']
            if len(bounds)!=4 or any(type(v)is not int for v in bounds) or bounds[0]>bounds[2] or bounds[1]>bounds[3]:raise ValueError('公共开口须使用有效整数闭区间')
            for x,z in ((bounds[0],bounds[1]),(bounds[0],bounds[3]),(bounds[2],bounds[1]),(bounds[2],bounds[3])):
                u,v=rotate(x-src['position'][0],z-src['position'][2],-FACING[src['facing']]);u+=template.w//2;v+=template.d//2
                if not 2<=u<template.w-2 or not 0<=v<template.d:raise ValueError('公共开口须保留两侧门墩，并限于门楼足迹')

    def prepare_manor(self):
        cfg=self.layout['manor'];scene=read(cfg['scene']);manifest=read(cfg['manifest'])
        actual=hashlib.sha256((ROOT/cfg['scene']).read_bytes()).hexdigest()
        if actual!=manifest['source_sha256']:raise ValueError('府邸模板与 authored 几何不同源')
        from lord_manors.construction import prepare
        rules_path=ROOT/'blueprints/lord_manors/structure_rules.json';rules=read('blueprints/lord_manors/structure_rules.json')
        if manifest['construction']['rules_sha256']!=hashlib.sha256(rules_path.read_bytes()).hexdigest():raise ValueError('默认城市与府邸施工合同不同源')
        scene,construction=prepare(scene,rules['cities'][self.region],rules)
        if any(manifest['construction'].get(k)!=v for k,v in construction.items()):raise ValueError('默认城市施工掩膜与模板构造不一致')
        from lord_manors.native_trees import attach,GENERATED
        if manifest['native_trees']['source_sha256']!=hashlib.sha256((ROOT/GENERATED).read_bytes()).hexdigest():raise ValueError('默认城市与原版树形不同源')
        scene=attach(scene,self.region,ROOT)
        self.manor_scene=scene
        w,h,d=manifest['size'];q=cfg['rotation_quarters'];sx,sz=cfg['site'];minimum=manifest['source_min']
        expected=[sx,sz,sx+(d if q%2 else w),sz+(w if q%2 else d)]
        if expected!=cfg['bounds']:raise ValueError('不能缩小或裁切府邸完整占地')
        # 独立样板的土块/静态水面不覆盖城市河海；建筑接触格仍使用原注册构件。
        mask=set();ground={}
        for b in scene['boxes']:
            x,y,z,bw,bh,bd,mat,group,role=b
            xa,xe=math.floor(x-bw/2),math.ceil(x+bw/2);za,ze=math.floor(z-bd/2),math.ceil(z+bd/2)
            if role=='ground':
                for zz in range(za,ze):
                    for xx in range(xa,xe):ground[xx,zz]=max(ground.get((xx,zz),-999),y+bh/2)
            elif role not in self.cfg['source_ground_roles']:
                for yy in range(math.floor(y-bh/2),math.ceil(y+bh/2)):
                    for zz in range(za,ze):
                        for xx in range(xa,xe):mask.add((xx,yy,zz))
        mask.update(scene['native_tree_cells'])
        for text,x,y,z,bw in scene.get('signs',[]):
            for yy in range(math.floor(y-.65),math.ceil(y+.65)):
                for zz in range(math.floor(z-.125),math.ceil(z+.125)):
                    for xx in range(math.floor(x-bw/2),math.ceil(x+bw/2)):mask.add((xx,yy,zz))
        if scene.get('edict_roots'):
            pillar=read('src/main/resources/data/myvillage/decoration/demon_pact_pillar.json')
            for root in scene['edict_roots']:
                mask.update(tuple(a+b for a,b in zip(root,part['offset'])) for part in pillar['parts'])
        front=minimum[2];source_y=round(ground.get((0,front),ground.get((0,front+1),0)))
        fx,fz=rotate_cell(w//2,0,w,d,q);fx+=sx;fz+=sz
        delta=self.ground(min(self.width-1,fx),min(self.depth-1,fz))+1-source_y
        self.manor_offset=[sx,minimum[1]+delta,sz];self.manor_manifest=manifest;self.manor_ground_delta=delta
        transformed={};lowest={};contacts={}
        for tile in manifest['tiles']:
            path=ROOT/'src/main/resources/data/myvillage/structure'/ (tile['template'].split(':',1)[1]+'.nbt')
            if hashlib.sha256(path.read_bytes()).hexdigest()!=tile['sha256']:raise ValueError('府邸切片摘要不符')
            _,nbt=read_gzipped_nbt(str(path));palette=[state_string(v) for v in nbt['palette']]
            for cell in nbt['blocks']:
                x,y,z=[a+b for a,b in zip(cell['pos'],tile['offset'])];world_src=(x+minimum[0],y+minimum[1],z+minimum[2])
                if world_src not in mask:continue
                xx,zz=rotate_cell(x,z,w,d,q);pos=(sx+xx,y+minimum[1]+delta,sz+zz)
                if not(0<=pos[1]<self.height):raise ValueError('府邸标高超出城市容量：'+str(pos))
                transformed[pos]=rotated_state(palette[cell['state']],q);lowest[pos[0],pos[2]]=min(lowest.get((pos[0],pos[2]),999),pos[1])
                native_ground=ground.get((world_src[0],world_src[2]))
                if native_ground is not None and world_src[1]<=round(native_ground)+1:
                    contacts[pos[0],pos[2]]=min(contacts.get((pos[0],pos[2]),999),pos[1])
        for (x,z),top in ground.items():
            u,v=rotate_cell(x-minimum[0],z-minimum[2],w,d,q);xx,zz=sx+u,sz+v
            if not(sx<=xx<expected[2] and sz<=zz<expected[3]) or self.is_wet(xx,zz):continue
            target=min(round(top)+delta-1,lowest.get((xx,zz),999)-1)
            if target>=0:self.set_ground(xx,zz,target)
        self.manor_cells=transformed
        self.manor_lowest=lowest
        self.manor_contacts=contacts

    def prepare_roads(self):
        # 所有交叉口共享同一个高度场；只抬低格形成连续阶坡，不重写全城地形。
        field={};owners=defaultdict(set);roads=[]
        road_inputs=copy.deepcopy(self.layout['roads'])
        base_columns=set()
        for r in road_inputs:
            for a,b in zip(r['points'],r['points'][1:]):
                n=max(abs(a[0]-b[0]),abs(a[1]-b[1]));vx=0 if a[0]==b[0] else (1 if b[0]>a[0] else -1);vz=0 if a[1]==b[1] else (1 if b[1]>a[1] else -1)
                for i in range(n+1):
                    for k in range(-(r['width']//2),-(r['width']//2)+r['width']):
                        base_columns.add((a[0]+vx*i+(k if not vx else 0),a[1]+vz*i+(k if vx else 0)))
        gate_columns={p for p,bid in self.building_columns.items() if self.summaries[bid]['role']=='gate'}
        forbidden=set(self.building_columns)|self.core|self.public_garden_columns|set(self.private_owner)
        for hint in self.layout['access_hint']:
            if self.summaries[hint['building_id']]['role']=='gate':continue
            pts=hint['points']
            if len(pts)<2:continue
            trace=set()
            for a,b in zip(pts,pts[1:]):
                n=max(abs(a[0]-b[0]),abs(a[1]-b[1]))
                trace.update((round(a[0]+(b[0]-a[0])*i/max(n,1)),round(a[1]+(b[1]-a[1])*i/max(n,1))) for i in range(n+1))
            adjusted=bool((trace&gate_columns)-base_columns)
            if adjusted:
                start=tuple(pts[0]);previous={start:None};todo=deque([start]);end=None
                while todo:
                    p=todo.popleft()
                    if p in base_columns:end=p;break
                    for dx,dz in DIR.values():
                        n=p[0]+dx,p[1]+dz
                        if n in previous or not(0<=n[0]<self.width and 0<=n[1]<self.depth):continue
                        if n not in base_columns and (n in forbidden or self.is_wet(*n)):continue
                        previous[n]=p;todo.append(n)
                if end is None:raise ValueError('接巷不能绕过保留城门：'+hint['building_id'])
                pts=[]
                while end is not None:pts.append(list(end));end=previous[end]
                pts.reverse()
            if len(pts)>1:road_inputs.append({'id':'alley_'+hint['building_id'],'name':self.summaries[hint['building_id']]['name']+'公共接巷','width':1,'points':pts,'kind':'alley','gate_detour':adjusted})
        for raw in road_inputs:
            r=copy.deepcopy(raw);samples=[]
            for a,b in zip(r['points'],r['points'][1:]):
                if a[0]!=b[0] and a[1]!=b[1]:raise ValueError('正式街路须正交')
                length=max(abs(a[0]-b[0]),abs(a[1]-b[1]));vx=0 if a[0]==b[0] else (1 if b[0]>a[0] else -1);vz=0 if a[1]==b[1] else (1 if b[1]>a[1] else -1)
                for i in range(length+1):
                    x,z=a[0]+vx*i,a[1]+vz*i;y=self.original_terrain[z*self.width+x]+1
                    if self.region=='donghai':y=8
                    elif self.region=='nanjiang' and (x>=130 or r['kind']=='bridge'):y=17 if x>=130 else 14
                    elif self.region=='moyu':y=18 if x<120 else 27 if x>=150 else 18+round((x-120)*9/30)
                    elif self.region=='lingyue' and r['name'] in ('玄璧环场','观璧接廊'):y=32
                    elif self.region=='lingyue' and r['name']=='沟桥':y=28+round((x-118)*4/11)
                    samples.append([x,y,z])
                    for cross in range(-(r['width']//2),-(r['width']//2)+r['width']):
                        xx,zz=(x,z+cross) if vx else (x+cross,z)
                        if not(0<=xx<self.width and 0<=zz<self.depth):continue
                        if (xx,zz) in self.core:raise ValueError('道路侵入完整府邸/地标预留：'+r['name'])
                        field[xx,zz]=max(field.get((xx,zz),0),y);owners[xx,zz].add(r['id'])
            r['points']=samples;roads.append(r)
        fixed={p:self.summaries[self.building_columns[p]]['position'][1] for p in field if p in self.building_columns and self.summaries[self.building_columns[p]]['role']=='gate'}
        for entry in self.model.entries:
            x,y,z=entry['position']
            if (x,z) not in field:raise ValueError('重排街路遗漏保留城门外接口：'+entry['id'])
            fixed[x,z]=y
        field.update(fixed)
        # 原门楼首层是固定接口；先降低邻近高路，再对低处补阶，不能抬地封住旧门。
        heap=[(h,*p) for p,h in fixed.items()];heapq.heapify(heap)
        ceiling=dict(fixed)
        while heap:
            h,x,z=heapq.heappop(heap)
            if ceiling[x,z]!=h:continue
            for dx,dz in DIR.values():
                p=x+dx,z+dz
                if p in field and h+1<ceiling.get(p,999):
                    ceiling[p]=h+1;field[p]=min(field[p],h+1);heapq.heappush(heap,(h+1,*p))
        if any(field[p]!=h for p,h in fixed.items()):raise ValueError('城门固定接口标高互相冲突')
        heap=[(-h,x,z) for (x,z),h in field.items()];heapq.heapify(heap)
        while heap:
            neg,x,z=heapq.heappop(heap);h=-neg
            if field[x,z]!=h:continue
            for dx,dz in DIR.values():
                p=x+dx,z+dz
                if p in field and field[p]<h-1:field[p]=h-1;heapq.heappush(heap,(-(h-1),*p))
        if any(field[p]!=h for p,h in fixed.items()):raise ValueError('坡阶抬高了门楼固定接口')
        for r in roads:
            r['points']=[[x,field[x,z],z] for x,_,z in r['points']]
            r['surface_columns']=[[x,field[x,z],z] for x,z in sorted(field) if r['id'] in owners[x,z]]
        self.road_field=field;self.road_owners=owners;self.road_columns=set(field);self.model.roads=roads
        if set(field)&set(self.private_owner):raise ValueError('公共街路侵入保留私院')
        self.model.road_columns={p:(y,'street',False,'north',next(iter(owners[p])),0,0) for p,y in field.items()}
        self.model.road_goals={r['id']:{p:field[p] for p in field if r['id'] in owners[p]} for r in roads}

    def emit_floor(self,x,y,z,tag,kind='road',stair=None):
        ground=self.ground(x,z)
        if ground>=y-1:self.set_ground(x,z,y-2);ground=y-2
        state=self.model.material('GROUND_PATH')
        if stair:state=props(self.model.material('PATH_STAIRS'),facing=stair,half='bottom',shape='straight',waterlogged=False)
        self.set((x,y-1,z),state,tag,kind)
        if ground+1<=y-2:
            spacing=self.cfg['support_spacing']
            if not self.is_wet(x,z) or x%spacing==0 and z%spacing==0:
                self.box((x,ground+1,z),(x,y-2,z),self.model.material('BASE_STONE'),tag,'support')
        for dy in range(self.cfg['headroom']):self.set((x,y+dy,z),AIR,tag,'clearance')

    def emit_structures(self):
        self.model.water();self.cache.clear()
        for box in self.layout['water_channels']:
            for x,z in half_cells(box):
                self.set_ground(x,z,3);self.box((x,4,z),(x,5,z),'minecraft:water[level=0]','引潮渠','water')
        for pos,state in self.manor_cells.items():self.set(pos,state,'lord_manor','manor')
        for (x,z),low in self.manor_contacts.items():
            g=self.ground(x,z);spacing=self.cfg['support_spacing']
            if low>g+1:
                material=self.model.material('FRAME_WOOD') if self.is_wet(x,z) else self.model.material('BASE_STONE')
                self.box((x,g+1,z),(x,low-1,z),material,'府邸基础','support')
        for bid,template in self.templates.items():
            self.model.foundation(template)
            template.emit(self.model.construction)
        self.cache.clear()
        # 先留住各栋真实岗位，不允许道路或门洞把功能家具当障碍清掉。
        self.model.buildings=list(self.summaries.values())
        self.model.places=[];self.model.fixtures=[]
        for bid,template in self.templates.items():
            source=next(b for b in self.source_city['buildings'] if b['id']==bid)
            for group,target in ((template.places,self.model.places),(template.fixtures,self.model.fixtures)):
                for row in group:
                    out=copy.deepcopy(row)
                    # owning Building 在 build 时保存了原坐标；经局部坐标统一重定位。
                    for key in ('pos','block_pos','position'):
                        if key in out:
                            local=template.local_from(out[key],source) if hasattr(template,'local_from') else None
                            if local is None:
                                ox,oy,oz=source['position'];xx,yy,zz=out[key];q=FACING[source['facing']]
                                from province_capitals.regional.v2.common import rotate
                                a,b=rotate(xx-ox,zz-oz,-q);local=(a+template.w//2,yy-oy,b+template.d//2)
                            out[key]=list(point(template.source,*local))
                    target.append(out)
        self.protected_fixtures={tuple(f['block_pos']):f['id'] for f in self.model.fixtures if 'block_pos' in f}
        openings={o['owner']:{**copy.deepcopy(o),'cells':[],'changes':[]} for o in self.layout['public_passages']}
        for (x,z),y in self.road_field.items():
            owner=self.building_columns.get((x,z))
            if owner and self.summaries[owner]['role']!='gate':raise ValueError('公共道路覆盖保留房屋：'+owner)
            for dy in range(self.cfg['headroom']):
                if (x,y+dy,z) in self.protected_fixtures:raise ValueError('公共道路覆盖岗位家具')
                before=self.get((x,y+dy,z))
                if owner and before!=AIR:
                    opening=openings.get(owner);bounds=opening['bounds'] if opening else None
                    if not opening or y!=opening['y'] or not(bounds[0]<=x<=bounds[2] and bounds[1]<=z<=bounds[3]) or dy>=opening['height']:
                        raise ValueError('公共道路超出已声明门楼开口：'+str((owner,x,y+dy,z,before)))
                    opening['cells'].append([x,y+dy,z]);opening['changes'].append({'position':[x,y+dy,z],'before':before,'after':AIR})
            stair=next((DIRECTIONS[(FACING[face]+2)%4] for face,(dx,dz) in DIR.items() if self.road_field.get((x+dx,z+dz),y)<y),None)
            self.emit_floor(x,y,z,'公共路网',stair=stair)
        self.public_passages=list(openings.values())
        self.emit_landmark()

    def emit_landmark(self):
        spec=next(v for v in self.catalog['landmarks'] if v['region']==self.region);origin=self.layout['landmark']['origin'];q=self.env.get('landmark_rotation_quarters',0)
        self.env['landmark_origin']=origin;self.env['reserve']=closed(self.layout['landmark']['bounds'])
        scene=Scene(self.catalog['materials']);scene.recipe(spec['recipe'],self.catalog['recipes'],origin,self.env.get('landmark_scale',1),'landmark','landmark',q)
        materials=read('blueprints/province_capitals/environment_v3/materials.json')['materials'];materials.update(self.native_rules['environment_material_overrides'])
        from province_capitals.environment_v3.world import raster
        raster(self,scene.boxes,materials,'主地标')
        self.landmark_boxes=scene.boxes

    def path(self,start,goals,*,owner=None,garden_id=None,private=False,limit=48):
        """从真实入口单源搜索，拒绝其他宅院、公共穿私院及未声明跨水。"""
        if not goals:return None
        column=start[0],start[2]
        if self.access_field.get(column,start[1])!=start[1] or self.road_field.get(column,start[1])!=start[1]:return None
        goals=set(goals)
        goals_xz={(p[0],p[2]) for p in goals};distance={start:0};previous={start:None};queue=[(0,0,start)]
        key=frozenset(goals_xz);heuristic=None
        if len(key)>16:
            if key not in self.goal_distances:
                field={p:0 for p in key};todo=deque(key)
                while todo:
                    x,z=todo.popleft()
                    for dx,dz in DIR.values():
                        n=x+dx,z+dz
                        if 0<=n[0]<self.width and 0<=n[1]<self.depth and n not in field:field[n]=field[x,z]+1;todo.append(n)
                self.goal_distances[key]=field
            heuristic=self.goal_distances[key]
        while queue:
            _,steps,p=heapq.heappop(queue)
            if steps!=distance[p]:continue
            if p in goals:
                result=[]
                while p is not None:result.append(list(p));p=previous[p]
                return result[::-1]
            if steps>=limit:continue
            x,y,z=p
            ancestor=p;columns=set()
            while ancestor is not None:
                columns.add((ancestor[0],ancestor[2]));ancestor=previous[ancestor]
            for dx,dz in DIR.values():
                xx,zz=x+dx,z+dz;column=(xx,zz)
                if column in columns:continue
                if not(0<=xx<self.width and 0<=zz<self.depth) or column in self.core:continue
                if self.building_columns.get(column,owner)!=owner:continue
                if self.private_owner.get(column,owner)!=owner:continue
                if private and column in self.road_field:continue
                if private and column in self.public_garden_columns:continue
                if self.is_wet(xx,zz) and column not in self.road_field and self.private_owner.get(column)!=owner:continue
                heights=(y,) if private else (y,y-1,y+1)
                for yy in heights:
                    np=(xx,yy,zz)
                    if not 1<=yy<self.height-self.cfg['headroom'] or distance.get(np,999)<=steps+1:continue
                    if column in self.road_field and yy!=self.road_field[column]:continue
                    if column in self.access_field and yy!=self.access_field[column]:continue
                    current=column in self.floor_columns
                    if current:
                        if not self.walkable(np) or not self.clear(np,self.cfg['headroom']):continue
                    else:
                        own_floor=owner in self.summaries and yy==self.summaries[owner]['position'][1]
                        if abs(yy-(self.ground(xx,zz)+1))>self.rules['local_earthwork_limit'] and not(own_floor and self.private_owner.get(column)==owner):continue
                        # 空地允许有界整地，不允许把已有实体抹成空气。
                        if any(not passable(self.get((xx,yy+k,zz))) and self.model.construction.record_at((xx,yy+k,zz)) is not None for k in range(self.cfg['headroom'])):continue
                    distance[np]=steps+1;previous[np]=p
                    estimate=heuristic[column] if heuristic is not None else min(abs(xx-a)+abs(zz-b) for a,b in goals_xz)
                    heapq.heappush(queue,(steps+1+estimate,steps+1,np))
        return None

    def emit_path(self,points,tag,owner=None):
        for i,(x,y,z) in enumerate(points):
            if (x,z) in self.access_field and self.access_field[x,z]!=y:raise ValueError('接路复用了不同标高：'+tag)
            self.access_field[x,z]=y
            if (x,z) in self.road_field or (x,z) in self.floor_columns:continue
            stair=None
            for neighbor in points[max(0,i-1):i]+points[i+1:i+2]:
                if neighbor[1]<y:
                    dx,dz=x-neighbor[0],z-neighbor[2]
                    stair=next(k for k,v in DIR.items() if v==(dx,dz));break
            self.emit_floor(x,y,z,tag,'road',stair)

    def new_door_candidates(self,bid):
        template=self.templates[bid];src=template.source;allowed=set()
        for z in range(template.d):
            for x in range(template.w):
                if all(passable(template.cells.get((x,y,z),(AIR,))[0]) for y in (0,1)):allowed.add((x,z))
        start=tuple(template.entrance[::2]);reachable={start};q=deque([start])
        while q:
            x,z=q.popleft()
            for dx,dz in DIR.values():
                n=x+dx,z+dz
                if n in allowed and n not in reachable:reachable.add(n);q.append(n)
        results={};thickness=template.wall_thickness
        for x,z in sorted(template.interiors[0]&reachable):
            for face,(dx,dz) in DIR.items():
                walls=[]
                for step in range(1,thickness+3):
                    xx,zz=x+dx*step,z+dz*step
                    if (xx,zz) not in template.coverage[0]:
                        out=point(src,xx,0,zz);column=out[::2]
                        if not(0<=out[0]<self.width and 0<=out[2]<self.depth):break
                        if self.building_columns.get(column,bid)!=bid or column in self.core:break
                        if column in self.road_field and self.road_field[column]!=out[1] or column in self.access_field and self.access_field[column]!=out[1]:break
                        if abs(out[1]-(self.ground(*column)+1))>self.rules['local_earthwork_limit'] and self.private_owner.get(column)!=bid:break
                        if any(not passable(self.get((out[0],out[1]+dy,out[2]))) and self.model.construction.record_at((out[0],out[1]+dy,out[2])) for dy in range(self.cfg['headroom'])):break
                        if walls:
                            world_walls=[point(src,a,0,b) for a,b in walls]
                            results[out]={'owner':bid,'position':list(world_walls[0]),'inside':list(point(src,x,0,z)),'outside':list(out),'cells':[list(v) for v in world_walls],'action':'open_door','headroom':self.cfg['headroom'],'width':1}
                        break
                    if (xx,zz) in template.interiors[0]:break
                    records=[template.cells.get((xx,dy,zz),(AIR,'','')) for dy in range(2)]
                    if any(r[2] not in ('wall','building','door') for r in records):break
                    walls.append((xx,zz))
        return results

    def open_door(self,record):
        owner=record['owner'];template=self.templates[owner]
        material=next((s.split('[',1)[0] for s,_,_ in template.cells.values() if '_door[' in s),None)
        if material is None:
            fresh=Building(template.source,self.model.style,self.city['construction'],self.model.library).build();fresh.opening(*fresh.south_door)
            material=next(s.split('[',1)[0] for s,_,_ in fresh.cells.values() if '_door[' in s)
        out=record['outside'];pos=record['cells'][-1];dx,dz=out[0]-pos[0],out[2]-pos[2]
        face=next(k for k,v in DIR.items() if v==(dx,dz))
        for cell in record['cells']:
            for dy in range(record['headroom']):
                p=(cell[0],cell[1]+dy,cell[2])
                if p in self.protected_fixtures:raise ValueError('门洞覆盖原有功能家具')
                self.set(p,AIR,owner,'door_clearance')
        for dy in (0,1):self.set((pos[0],pos[1]+dy,pos[2]),props(material,facing=face,half='lower' if dy==0 else 'upper',hinge='left',open=False,powered=False),owner,'door')
        self.emit_floor(*out,owner+'_门前落脚','road')
        self.doors.append(record)

    def grade_gardens(self):
        original_courts={c['id']:c for c in self.source_city['courtyards']}
        for g in self.layout['gardens']:
            if g['access']=='private':y=self.summaries[g['owner']]['position'][1]
            else:
                cx,cz=(g['box'][0]+g['box'][2]-1)//2,(g['box'][1]+g['box'][3]-1)//2
                y=self.ground(cx,cz)+1
            g['y']=y
            if g['id'] in original_courts:
                court=copy.deepcopy(original_courts[g['id']]);a,b,e,f=g['box'];court['center']=[a+(e-a)//2,y,b+(f-b)//2];court['size']=[e-a,f-b]
                court.update({k:g[k] for k in ('access','owner','steward','reason') if k in g})
                self.courtyards.append(court)
                continue
            for x,z in half_cells(g['box']):
                if (x,z) in self.road_field:continue
                self.emit_floor(x,y,z,g['id'],'garden')
        self.model.city['courtyards']=self.courtyards
        self.model.courtyards();self.cache.clear()

    def connect_buildings(self):
        goals={(x,y,z) for (x,z),y in self.road_field.items()}
        maximum=self.model.geometry.get('max_access_length',self.model.geometry['access_max_distance'])
        for bid,b in self.summaries.items():
            start=tuple(b['entrance']);path=self.path(start,goals,owner=bid,limit=maximum);door=None
            if path is None:
                candidates=self.new_door_candidates(bid)
                for outside,record in sorted(candidates.items(),key=lambda v:min(abs(v[0][0]-a)+abs(v[0][2]-c) for a,c in self.road_field)):
                    path=self.path(outside,goals,owner=bid,limit=maximum)
                    if path:door=record;break
            if path is None:raise ValueError('正式建筑入口不能接路：'+bid)
            if door:self.open_door(door);b['entrance']=door['outside']
            self.emit_path(path,bid+'_接街',bid)
            rid=next(iter(sorted(self.road_owners[path[-1][0],path[-1][2]])))
            b['road_id']=rid;self.model.access.append({'building_id':bid,'road_id':rid,'points':path,'length':len(path)-1})

    def connect_manor(self):
        """按原府门轴线把街网接到府邸前缘，内部保持原作方块。"""
        m=self.layout['manor'];minimum=self.manor_manifest['source_min'];w,_,d=self.manor_manifest['size']
        x,z=m['entry_axis_source_x']-minimum[0],0
        x,z=rotate_cell(x,z,w,d,m['rotation_quarters']);x+=m['site'][0];z+=m['site'][1]
        self.manor_entry=[x,self.ground(x,z)+1,z]
        goals={(xx,y,zz) for (xx,zz),y in self.road_field.items()}
        path=self.path(tuple(self.manor_entry),goals,limit=self.rules['public_access_max_length'])
        if not path:raise ValueError('府前街未接到原府门轴线：'+self.region)
        for xx,y,zz in path:
            if any((xx,y+n,zz) in self.manor_cells for n in range(-1,self.cfg['headroom'])):raise ValueError('府前接路覆盖原府邸构件')
        self.emit_path(path,'府前接路')
        rid=next(iter(sorted(self.road_owners[path[-1][0],path[-1][2]])))
        self.model.access.append({'building_id':'lord_manor','road_id':rid,'points':path,'length':len(path)-1})

    def complete_gardens(self):
        goals={(x,y,z) for (x,z),y in self.road_field.items()};native_materials=self.spec['minecraft']['materials']
        from province_capitals.blueprint_plan.export_minecraft import fixture
        planting=read('blueprints/lord_manors/native_trees.json')['species'][self.spec['minecraft']['tree_species']]
        for source in self.layout['gardens']:
            gid=source['id'];owner=source.get('owner');private=source['access']=='private';a,z,e,f=source['box'];y=source['y']
            starts=[(x,y,z) for x in range(a+1,e-1)]+[(x,y,f-1) for x in range(a+1,e-1)]+[(a,y,zz) for zz in range(z+1,f-1)]+[(e-1,y,zz) for zz in range(z+1,f-1)]
            starts=[p for p in starts if self.walkable(p)]
            candidates=self.new_door_candidates(owner) if private else {}
            if private:
                # 既有门也可作为私院目标；新门候选仍需接通真实室内。
                candidates[tuple(self.summaries[owner]['entrance'])]={'action':'retain_door','owner':owner}
                target=set(candidates)
            else:target=goals
            best=None
            for start in sorted(set(starts),key=lambda p:min(abs(p[0]-t[0])+abs(p[2]-t[2]) for t in target)):
                path=self.path(start,target,owner=owner if private else None,garden_id=gid,private=private,limit=self.rules['private_access_max_length' if private else 'public_access_max_length'])
                if path:best=path;break
            if best is None:raise ValueError('正式庭院不能接通：'+gid)
            door=candidates.get(tuple(best[-1])) if private else None
            if door and door['action']=='open_door':self.open_door(door)
            self.emit_path(best,gid+'_接路',owner)
            gate=best[0];used={(p[0],p[2]) for p in best}
            used.update((x,z) for x,z in self.access_field if a<=x<e and source['box'][1]<=z<f)
            used.update((x,z) for x,z in self.road_field if a<=x<e and source['box'][1]<=z<f)
            inward=(0,1) if gate[2]==z else (0,-1) if gate[2]==f-1 else (1,0) if gate[0]==a else (-1,0)
            used.update((gate[0]+inward[0]*n,gate[2]+inward[1]*n) for n in (1,2))
            is_court=any(c['id']==gid for c in self.courtyards)
            internal=[gate]
            for xx,zz in half_cells(source['box']):
                if xx in (a,e-1) or zz in (z,f-1):
                    if abs(xx-gate[0])+abs(zz-gate[2])<=1 or (xx,zz) in used:continue
                    if not is_court:self.set((xx,y,zz),self.model.material('BASE_STONE'),gid,'garden_wall')
            original=next((g for g in self.env['gardens'] if g['id']==gid),None);kind=original['kind'] if original else 'flower'
            program={'fixtures':[]} if is_court else self.spec['garden_programs'][kind];items=[];occupied=set(used)
            for index,requested in enumerate(program['fixtures']):
                w,h,d=requested['size'];found=None
                for zz in range(z+1,f-d):
                    for xx in range(a+1,e-w):
                        area={(u,v) for v in range(zz,zz+d) for u in range(xx,xx+w)}
                        # 树冠在三格净空之上，可遮住井边和步道；树干仍须独占落脚格。
                        n=planting['soil_size']
                        body={(xx+(w-n)//2+dx,zz+(d-n)//2+dz) for dx in range(n) for dz in range(n)} if requested['kind']=='tree' else area
                        if not body&occupied:found=(xx,zz,body);break
                    if found:break
                if found is None:raise ValueError('保留庭院设施没有实际空间：'+gid+'/'+requested['kind'])
                xx,zz,area=found;occupied|=area;item={**requested,'id':gid+'_fixture_'+str(index),'garden_id':gid,'position':[xx,y,zz]}
                fixture(self,item,native_materials,self.spec['minecraft']['rack_facing'],self.spec['minecraft']['tree_species']);items.append(item);self.fixtures.append(item)
            # 设施落位后检查真实身体净空；树冠可高于走道，树干不可挡门。
            valid={(xx,zz) for zz in range(z,f) for xx in range(a,e) if self.walkable((xx,y,zz))}
            seen={tuple(gate[::2])};parents={tuple(gate[::2]):None};todo=deque(seen)
            while todo:
                xx,zz=todo.popleft()
                for dx,dz in DIR.values():
                    n=xx+dx,zz+dz
                    if n in valid and n not in seen:seen.add(n);parents[n]=(xx,zz);todo.append(n)
            required=[]
            for item in items:
                xx,_,zz=item['position'];ww,_,dd=item['size'];surround={(u,v) for v in range(zz-1,zz+dd+1) for u in range(xx-1,xx+ww+1)}
                candidates=surround&seen
                if not candidates:raise ValueError('庭院设施无法从院门到达：'+item['id'])
                target=min(candidates,key=lambda t:abs(t[0]-gate[0])+abs(t[1]-gate[2]));branch=[]
                while target is not None:branch.append([target[0],y,target[1]]);target=parents[target]
                for step in branch[::-1]+branch[1:]:
                    if not required or required[-1]!=step:required.append(step)
            internal=required or [gate]
            record={'id':gid,'name':source['name'],'access':source['access'],'owner':owner,'steward':source.get('steward'),'reason':source['reason'],'bounds':closed(source['box']),'y':y,'gate':gate,'path':best,'internal_path':internal,'door':door,'fixtures':items,'status':'connected','kind':kind}
            self.gardens.append(record);self.paths.append({'id':gid,'points':best,'road_id':None if private else next(iter(self.road_owners[best[-1][0],best[-1][2]]))})

    def emit_walls(self):
        points=self.env['wall'].get('points',[])
        accesses={(x,z) for route in self.model.access for x,_,z in route['points']}
        accesses.update((x,z) for g in self.gardens for x,_,z in g['path']+g['internal_path'])
        accesses.update(p for g in self.layout['gardens'] for p in half_cells(g['box']))
        for a,b in zip(points,points[1:]):
            n=max(abs(b[0]-a[0]),abs(b[1]-a[1]))
            for i in range(n+1):
                x,z=round(a[0]+(b[0]-a[0])*i/max(n,1)),round(a[1]+(b[1]-a[1])*i/max(n,1))
                if not(0<=x<self.width and 0<=z<self.depth) or (x,z) in self.road_field or (x,z) in self.core or (x,z) in self.building_columns or (x,z) in accesses:continue
                y=self.ground(x,z)+1;self.box((x,y,z),(x,min(self.height-1,y+self.cfg['wall_height']-1),z),self.model.material('BASE_STONE'),'城防','wall')

    def audit(self):
        routes=[('road',[(x,y,z)]) for (x,z),y in self.road_field.items()]
        routes += [(r['building_id'],r['points']) for r in self.model.access]
        routes += [(g['id'],g['path']) for g in self.gardens]+[(g['id']+'_院内',g['internal_path']) for g in self.gardens]
        losses=[]
        for rid,path in routes:
            for p in path:
                if not self.walkable(tuple(p)) or rid=='road' and not self.clear(tuple(p),self.cfg['headroom']):
                    losses.append({'id':rid,'position':list(p),'kind':'blocked_or_unsupported','states':[self.get((p[0],p[1]+n,p[2])) for n in (-1,0,1,2)]})
            for a,b in zip(path,path[1:]):
                if abs(a[0]-b[0])+abs(a[2]-b[2])!=1 or abs(a[1]-b[1])>self.cfg['road_step_limit']:
                    losses.append({'id':rid,'from':a,'to':b,'kind':'route_discontinuous'})
        for p,state in self.manor_cells.items():
            if self.get(p)!=state:losses.append({'id':'lord_manor','position':list(p),'kind':'native_component_overwritten'})
        # 被巨型地标替换的旧地标屋，其修炼站位迁入公共观景街。
        if not any(p['kind']=='cultivate' for p in self.model.places):
            lx,_,lz=self.layout['landmark']['origin']
            candidates=[(x,y,z) for (x,z),y in self.road_field.items() if self.walkable((x,y,z))]
            position=list(min(candidates,key=lambda p:abs(p[0]-lx)+abs(p[2]-lz)))
            old=next(b for b in self.source_city['buildings'] if b['role']=='landmark')
            self.model.places.append({'id':old['id']+'_cultivation','kind':'cultivate','pos':position,'capacity':4,'block':'','block_pos':position,'building_id':'main_landmark'})
        self.model.validate_places()
        for p in self.model.places:
            if not self.walkable(tuple(p['pos'])):losses.append({'id':p['id'],'position':p['pos'],'kind':'functional_stand_blocked'})
        self.issues=losses
        self.audit_result={'road_columns':len(self.road_field),'routes':len(routes)-len(self.road_field),'functional_stands':len(self.model.places),'native_manor_cells':len(self.manor_cells),'losses':losses}

    def compile(self):
        if not self.built:
            self.prepare_buildings();self.validate_passages();self.prepare_manor();self.prepare_roads();self.emit_structures();self.grade_gardens()
            self.connect_buildings();self.connect_manor();self.complete_gardens();self.emit_walls();self.audit()
            self.built=True
        return self.data()

    def data(self):
        # 府邸也是完整可选地块；内部原作构造由现有 MC 模板保持。
        plots=[]
        for bid,b in self.summaries.items():
            box=self.parcels[bid]['box'];runs=[[z,box[0],box[2]-1] for z in range(box[1],box[3])]
            plots.append({'id':bid,'name':b['name'],'building':b,'cell_runs':runs,'garden_ids':[g['id'] for g in self.layout['gardens'] if g.get('owner')==bid]})
        m=self.layout['manor'];box=m['bounds'];w,h,d=self.manor_manifest['size'];entry=self.manor_entry
        access=next(r for r in self.model.access if r['building_id']=='lord_manor')
        b={'id':'lord_manor','name':'城主府','role':'lord_manor','family':'lord_manor','size':[box[2]-box[0],box[3]-box[1]],'position':[box[0],self.manor_offset[1],box[1]],'bounds':{'min':[box[0],self.manor_offset[1],box[1]],'max':[box[2]-1,self.manor_offset[1]+h-1,box[3]-1]},'rotation_quarters':m['rotation_quarters'],'entrance':entry,'volumes':[],'road_id':access['road_id']}
        plots.append({'id':'lord_manor','name':'城主府','building':b,'cell_runs':[[z,box[0],box[2]-1] for z in range(box[1],box[3])],'garden_ids':[]})
        counts=Counter(v['role'] for v in self.summaries.values())
        expected=self.layout['expected']
        if counts['house']!=expected['houses_after'] or len(self.summaries)-counts['house']!=expected['services']:raise ValueError('正式建筑数量与选定方案不符')
        return {'schema':1,'city_id':self.spec['city_id'],'region':self.region,'name':self.spec['name'],'scope':self.spec['scope'],
          'dimensions':self.city['dimensions'],'terrain':self.model.terrain,'terrain_policy':self.spec['terrain_policy'],'districts':self.city['districts'],'terraces':self.city.get('terrain_patches',[]),
          'roads':self.model.roads,'building_access':self.model.access,'plots':plots,'gardens':self.gardens,'courtyards':self.courtyards,
          'landmark':{'name':next(v['name'] for v in self.catalog['landmarks'] if v['region']==self.region),'bounds':self.env['reserve'],'origin':self.env['landmark_origin']},
          'manor':{'bounds':closed(box),'origin':self.manor_offset,'rotation_quarters':m['rotation_quarters'],'native_architectural_cells':len(self.manor_cells),'size':self.manor_manifest['size'],'ground_policy':m['context_policy']},
          'wall':self.env['wall'],'paths':self.paths,'doors':self.doors,'public_passages':self.public_passages,'retained_environment':[],'landmark_paths':[],
          'earthwork':self.earthwork,'fixtures':self.fixtures,'removed_source_buildings':sorted(self.removed),'design_notes':self.layout['design_notes'],'relayout':True,'construction_audit':self.audit_result,
          'checks':{'status':'pass' if not self.issues else 'needs_design','issues':self.issues,'houses':counts['house'],'public_buildings':expected['services']+1,'plots':len(plots),'gardens':len(self.gardens),'connected_gardens':len(self.paths),'new_doors':len(self.doors),'private_gardens':sum(g['access']=='private' for g in self.gardens),'public_gardens':sum(g['access']=='public' for g in self.gardens),'game_runtime':'not_verified','owner_acceptance':'not_verified'}}
