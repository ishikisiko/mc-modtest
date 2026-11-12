"""御街西侧总舵：原尺寸楼体、逐栋台地与真实公共通路。"""
from __future__ import annotations
from collections import defaultdict
from functools import lru_cache
import copy,gzip,heapq,json,math,sys
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
SOURCE=ROOT/'blueprints/qianji/zhongzhou_infill.json'

def read():return json.loads(SOURCE.read_text())

@lru_cache(maxsize=1)
def site():
    from province_capitals.blueprint_plan.central import geometry
    from player_estates.zhongzhou_plan import source_grid
    from player_estates.build_terraces import original_roads
    spec=json.loads((ROOT/'blueprints/province_capitals/central/game-spec.json').read_text())
    g=geometry(spec);folder=ROOT/'out/preview/qianji-infill';folder.mkdir(parents=True,exist_ok=True);cache=source_grid(folder,g)
    heights=np.frombuffer(gzip.decompress((cache/'terrain.bin.gz').read_bytes()),dtype='<i2').reshape(1440,1200)
    cfg=read();a,b,e,f=cfg['plot'];w,d=cfg['size'];base=cfg['base_y']
    if (w,d)!=(f-b,e-a):raise ValueError('总舵模型与获准地块尺寸不一致')
    terrain=np.array([[heights[b+x,e-1-z]-base for x in range(w)]for z in range(d)])
    roads=original_roads(spec,g,cache,folder)
    existing_road_cuts=[]
    for x,z,foot2,*_ in roads['columns']:
        if not(a<=x<e and b<=z<f):continue
        lx,lz=z-b,e-1-x;ground=math.ceil(foot2/2)-1-base
        if ground<int(terrain[lz,lx]):
            existing_road_cuts.append([lx,lz,int(terrain[lz,lx]),ground,foot2])
            terrain[lz,lx]=ground
    shifts={}
    for key,p in cfg['parts'].items():
        dx,dz=p['shift'];need=[]
        for x,z,ww,dd,y in p['pads']:
            for zz in range(math.floor(z+dz),math.ceil(z+dz+dd)):
                for xx in range(math.floor(x+dx),math.ceil(x+dx+ww)):
                    if not(0<=xx<w and 0<=zz<d):raise ValueError('楼院台基越出地块 '+key)
                    need.append(int(terrain[zz,xx])-y+1.5-cfg['local_earthwork_limit'])
        # Integer translation retains the task board's integer operating floor;
        # the gate keeps its authored half-height threshold within that frame.
        dy=math.ceil(max(need))+p.get('elevation_offset',0)
        shifts[key]=(dx,dy,dz)
    columns={(x,z):values for x,z,*values in roads['columns']}
    ex,ez=cfg['entrance'][0],cfg['entrance'][2];city_point=(e-1-ez,b+ex)
    if city_point not in columns:raise ValueError('总舵入口必须接在已有公共巷道上 '+str(city_point))
    entry_y=math.ceil(columns[city_point][0]/2)-base
    return dict(config=cfg,geometry=g,heights=heights,source_cache=cache,terrain=terrain,shifts=shifts,roads=roads,entry_y=entry_y,existing_road_cuts=existing_road_cuts)

def translate(p,key):return [round(a+b,5)for a,b in zip(p,site()['shifts'][key])]

def box_bounds(b):
    x,y,z,w=b[9:13]
    rotation=[[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],
              [2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],
              [2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]]
    extent=[sum(abs(row[k])*b[k+3]/2 for k in range(3))for row in rotation]
    return [b[i]-extent[i]for i in range(3)]+[b[i]+extent[i]for i in range(3)]

def lighting_checks():
    cfg=read();light=json.loads((ROOT/'blueprints/qianji/lighting.json').read_text())
    return [{**row,'position':translate(row['position'],key)}for row,key in zip(light['checks']['zhongzhou_headquarters'],cfg['fixture_parts'])]

def access():
    cfg=read();s=copy.deepcopy(json.loads((ROOT/'blueprints/qianji/zhongzhou_headquarters.json').read_text()))
    s['size']=cfg['size'];s['site']='zhongzhou_west_infill'
    for room in s['rooms']:
        key=cfg['room_parts'][room['id']];dx,dy,dz=site()['shifts'][key]
        x,z,w,d=room['box'];room['box']=[x+dx,z+dz,w,d];room['floor_y']+=dy
        for level in room['levels']:
            level['floor_y']+=dy;level['target']=translate(level['target'],key)
    s['board']=list(map(round,translate(s['board'],'office')))
    s['reader']=list(map(round,translate(s['reader'],'office')))
    s['atmosphere_anchor']=list(map(round,translate(cfg['anchor_reference'],'core')))
    entry=cfg['entrance'][:];entry[1]=site()['entry_y']
    s['paths']=[entry,translate([84,1,6],'gate')]
    opening_parts={'archive_to_west_gallery':'atlas','west_annex_to_core':'core','east_gallery_to_core':'core','east_annex_to_core':'core','west_annex_roof_clearance':'core','east_annex_roof_clearance':'core'}
    for o in s['openings']:
        o['bounds']=translate(o['bounds'][:3],opening_parts[o['id']])+translate(o['bounds'][3:],opening_parts[o['id']])
    for o in cfg.get('extra_openings',[]):
        s['openings'].append(dict(id=o['id'],bounds=translate(o['bounds'][:3],o['part'])+translate(o['bounds'][3:],o['part'])))
    extra_parts={'hall_east_mezzanine':'hall','core_clerk_gallery':'core','water_pavilion':'envoy'}
    # The external galleries are now ground-following covered stairways.
    s['extra_targets']=[{**t,'position':translate(t['position'],extra_parts[t['id']])}for t in s['extra_targets']if t['id']in extra_parts]
    return s

def build_scene(scene_type):
    from .headquarters import gate,public_court,great_hall,archive_court,workshop_court,envoy_court,core,PALETTE,SPEC
    class Parts(scene_type):
        def terrace(self,*args,**kwargs):pass
        def pine(self,*args,**kwargs):pass
        def cherry(self,*args,**kwargs):pass
        def bamboo(self,*args,**kwargs):pass
    raw=Parts()
    gate(raw,inscription_stones=False)
    for fn in [public_court,great_hall,archive_court,workshop_court,envoy_court,core]:fn(raw)
    cfg=site()['config'];out=scene_type();out.terrain=site()['terrain'].copy();out.shifts=site()['shifts'];out.pad_columns={};out.floor_by_part={}
    for b in raw.boxes:
        if b[7]not in cfg['parts']or b[8]in('base','stair')or(b[8]=='ground'and b[7]!='envoy'):continue
        n=b[:];n[:3]=translate(b[:3],b[7]);out.boxes.append(n)
    for m in raw.meshes:
        if m['part']not in cfg['parts']:continue
        n=copy.deepcopy(m);n['vertices']=[v for i in range(0,len(m['vertices']),3)for v in translate(m['vertices'][i:i+3],m['part'])];out.meshes.append(n)
    for attr in ['signs','lamps','maps']:
        for item in getattr(raw,attr):
            if item['part']in cfg['parts']:
                n=copy.deepcopy(item);n['p']=translate(n['p'],n['part']);getattr(out,attr).append(n)
    for level in raw.floor_targets:
        n=copy.deepcopy(level);dx,dy,dz=site()['shifts']['core'];n['y']+=dy;n['rect'][0]+=dx;n['rect'][1]+=dz
        if n.get('stair_hole'):n['stair_hole'][0]+=dx;n['stair_hole'][1]+=dz
        out.floor_targets.append(n)
    for key,p in cfg['parts'].items():
        out.part=key;dx,dy,dz=site()['shifts'][key]
        for x,z,w,d,y in p['pads']:
            xx,zz,yy=x+dx,z+dz,y+dy
            # The outdoor stone finish sits below the retained timber floor.
            out.floor(xx,yy-.0625,zz,w,d,'paving','infill_pad')
            for iz in range(math.floor(zz),math.ceil(zz+d)):
                for ix in range(math.floor(xx),math.ceil(xx+w)):
                    ground=int(out.terrain[iz,ix]);limit=math.floor(yy-.52)
                    lowered=min(ground,limit)
                    if ground-lowered>cfg['local_earthwork_limit']:raise ValueError('总舵台基下挖超限 '+str((key,ix,iz,ground,lowered)))
                    out.terrain[iz,ix]=lowered;out.pad_columns[ix,iz]=yy+1
                    # Full real foundations meet the natural hillside.
                    bottom=lowered
                    if yy-.51>bottom:out.box(ix,bottom,iz,1,yy-.51-bottom,1,'stone_dark','infill_foundation')
        out.floor_by_part[key]=p['pads'][0][4]+dy+1
    for pool in cfg.get('pool_liners',[]):
        out.part=pool['part']
        for x,y,z,w,h,d in pool['boxes']:
            xx,yy,zz=translate([x,y,z],pool['part']);out.box(xx,yy,zz,w,h,d,'stone_dark','infill_pool_liner')
    lighting=json.loads((ROOT/'blueprints/qianji/lighting.json').read_text())
    fixtures=lighting['headquarters_fixtures']
    if len(fixtures)!=len(cfg['fixture_parts']):raise ValueError('总舵灯具必须逐项绑定实际楼院')
    for item,key in zip(fixtures,cfg['fixture_parts']):
        out.part=key;x,y,z=translate(item['position'],key);out.lantern(x,y-1,z,ceiling=item['ceiling']-1+site()['shifts'][key][1])
    out.part='garden'
    for tree in cfg.get('garden_trees',[]):
        x,z=tree['x'],tree['z'];y=int(out.terrain[math.floor(z),math.floor(x)])
        if tree['kind']=='spruce':out.pine(x,y,z,tree['height'],tree['seed'])
        elif tree['kind']=='cherry':out.cherry(x,y,z,tree['height'])
        elif tree['kind']=='bamboo':out.bamboo(x,y,z,tree['height'])
        else:raise ValueError('总舵庭园树种无效')
    names={r['id']:r['name']for r in access()['rooms']};parts=[]
    for key in cfg['parts']:
        cells=[b for b in out.boxes if b[7]==key]
        extents=[box_bounds(b)for b in cells]
        bounds=[min(b[i]for b in extents)for i in range(3)]+[max(b[i]for b in extents)for i in range(3,6)]
        if bounds[0]<0 or bounds[2]<0 or bounds[3]>cfg['size'][0] or bounds[5]>cfg['size'][1]:raise ValueError('原尺寸楼体越出获准地块 '+key)
        parts.append(dict(id=key,name=names.get(key,key),bounds=bounds,components=len(cells)))
    for box in out.boxes:
        if len(box)!=13 or not all(math.isfinite(v)for v in box[:6]) or min(box[3:6])<=0 or box[6]not in PALETTE:raise ValueError('总舵构件几何无效')
    scene=dict(id='zhongzhou_west_infill',name=cfg['name'],size=[cfg['size'][0],math.ceil(max(b[1]+b[4]/2 for b in out.boxes)),cfg['size'][1]],boxes=out.boxes,meshes=out.meshes,signs=out.signs,lamps=out.lamps,maps=out.maps,parts=parts,levels=out.floor_targets,tree_sites=out.tree_sites,terrain=out.terrain.tolist(),pads=[[x,z,y]for(x,z),y in out.pad_columns.items()],floor_by_part=out.floor_by_part)
    return dict(palette=PALETTE,current=scene,design={**SPEC,'name':cfg['name'],'site':dict(plot=cfg['plot'],facing='east',retired_buildings=len(cfg['approved_retirements']))})


class Routes:
    """三格完整路幅；转角平台等高，路口共用先建通行面的标高。"""
    def __init__(self,d):
        from .headquarters_model import Collision
        self.d=d;self.cfg=read();self.w,self.depth=self.cfg['size'];self.terrain=np.array(d['scene']['terrain'],dtype=int);self.original=self.terrain.copy();self.field={};self.records=[];self.collision=Collision(d);self.pad={(x,z):y for x,z,y in d['scene']['pads']}
    def body_clear(self,x,z,feet):
        if not(0<=x<self.w and 0<=z<self.depth):return False
        for y,s in self.collision.columns.get((x,z),[]):
            if s.startswith('minecraft:grass_block'):continue
            for a,b,c,e,f,g in self.collision.shape(s):
                if y+f>feet+.001 and y+b<feet+self.cfg['route_headroom']-.001:return False
        return True
    def support_limit(self,x,z):
        for key,part in self.cfg['parts'].items():
            p=translate(part['portal'],key);back=part.get('back_entry',False)
            lo,hi=(p[2]-2,p[2]+8)if back else(p[2]-8,p[2]+2)
            if abs(x-p[0])<=3 and lo<=z<=hi:return self.cfg['building_entry_support']
        for span in self.cfg.get('elevated_spans',[]):
            a,b,e,f=span['bounds']
            if a<=x<e and b<=z<f:return span['max_support']
        return self.cfg['max_path_support']
    def brush(self,path,i):
        x,z=path[i];a=path[max(0,i-1)];b=path[min(len(path)-1,i+1)]
        if i not in(0,len(path)-1)and((a[0]!=x and b[1]!=z)or(a[1]!=z and b[0]!=x)):return[(x+dx,z+dz)for dz in(-1,0,1)for dx in(-1,0,1)]
        return[(x,z+k)for k in(-1,0,1)]if a[0]!=b[0]else[(x+k,z)for k in(-1,0,1)]
    def route(self,start,goal):
        queue=[(0,0,start)];costs={start:0};parents={};seen=set()
        while queue:
            _,cost,p=heapq.heappop(queue)
            if p in seen:continue
            seen.add(p)
            if p==goal:
                chain=[p]
                while chain[-1]!=start:chain.append(parents[chain[-1]])
                return list(reversed(chain))
            x,z=p
            for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                q=xx,zz=x+dx,z+dz
                if not(0<=xx<self.w and 0<=zz<self.depth):continue
                if not(1<=xx<self.w-1 and 1<=zz<self.depth-1)and q not in(start,goal):continue
                if q in self.field and math.dist(q,start)>2 and math.dist(q,goal)>3:continue
                # Existing floor and headroom constrain the planar search too.
                near_pad=any((xx+sx,zz+sz)in self.pad for sx in(-1,0,1)for sz in(-1,0,1))
                if near_pad and q not in(start,goal)and math.dist(q,goal)>5 and math.dist(q,start)>5:continue
                high=int(self.terrain[zz,xx])+1
                if not any(self.body_clear(xx+sx,zz+sz,y)for y in range(high-self.cfg['local_earthwork_limit'],high+self.cfg['max_path_support']+1)for sx,sz in [(0,0)]):continue
                new=cost+10+min(40,abs(int(self.terrain[zz,xx])-int(self.terrain[z,x]))*4)
                if new<costs.get(q,10**12):
                    costs[q]=new;parents[q]=p;heapq.heappush(queue,(new+7*(abs(xx-goal[0])+abs(zz-goal[1])),new,q))
        raise ValueError('总舵阶廊平面无路 '+str((start,goal)))
    def profile(self,path,anchors,landing=3):
        equal=set(range(min(landing,len(path)-1)))|set(range(max(0,len(path)-landing-1),len(path)-1))
        for i in range(1,len(path)-1):
            a,b,c=path[i-1],path[i],path[i+1]
            if(a[0]!=b[0]and b[1]!=c[1])or(a[1]!=b[1]and b[0]!=c[0]):equal.update((i-1,i))
        touched={}
        for i in range(len(path)):
            for column in self.brush(path,i):
                if column in touched:equal.update(range(touched[column],i))
                touched[column]=i
        groups=[];begin=0
        for i in range(len(path)-1):
            if i not in equal:groups.append(list(range(begin,i+1)));begin=i+1
        groups.append(list(range(begin,len(path))));states={};history=[]
        for group in groups:
            cells=set(p for i in group for p in self.brush(path,i))
            if any(not(0<=x<self.w and 0<=z<self.depth)for x,z in cells):raise ValueError('总舵阶廊完整路幅越界')
            low=max(int(self.terrain[z,x])+1-self.cfg['local_earthwork_limit']for x,z in cells);high=min(int(self.terrain[z,x])+1+self.support_limit(x,z)for x,z in cells)
            fixed={anchors[path[i]]for i in group if path[i]in anchors}|{self.field[p]for p in cells if p in self.field}
            if len(fixed)>1:raise ValueError('总舵公共路口标高冲突 '+str((path[group[0]],fixed)))
            candidates=sorted(fixed)if fixed else range(low,high+1);nxt={};back={}
            for y in candidates:
                if not low<=y<=high or not all(self.body_clear(x,z,y)for x,z in cells):continue
                prefer=sum(abs(y-int(self.terrain[z,x])-1)for x,z in cells)
                if not states:nxt[y]=prefer;back[y]=None
                else:
                    previous=[(v+abs(y-old)*3,old)for old,v in states.items()if abs(y-old)<=1]
                    if previous:
                        v,old=min(previous);nxt[y]=v+prefer;back[y]=old
            if not nxt:
                tested=list(candidates);blocked={y:[(x,z)for x,z in cells if not self.body_clear(x,z,y)][:8]for y in tested if y in fixed}
                raise ValueError('总舵阶廊标高无解 '+str((path[group[0]],low,high,fixed,sorted(states),blocked)))
            states=nxt;history.append(back)
        y=min(states,key=states.get);ys=[]
        for back in reversed(history):ys.append(y);y=back[y]
        ys.reverse();levels=[0]*len(path)
        for group,y in zip(groups,ys):
            for i in group:levels[i]=y
        return levels
    def build(self,key,points,anchors,covered=False,direct=False,landing=3):
        path=list(points) if direct else []
        if not direct:
            for a,b in zip(points,points[1:]):
                if a[0]==b[0]or a[1]==b[1]:
                    dx=(b[0]>a[0])-(b[0]<a[0]);dz=(b[1]>a[1])-(b[1]<a[1]);n=abs(b[0]-a[0])+abs(b[1]-a[1]);leg=[(a[0]+dx*i,a[1]+dz*i)for i in range(n+1)]
                else:leg=self.route(tuple(a),tuple(b))
                path.extend(leg if not path else leg[1:])
        levels=self.profile(path,anchors,landing);columns={};faces={}
        for i,p in enumerate(path):
            y=levels[i];lower=path[i-1]if i and levels[i-1]<y else path[i+1]if i+1<len(path)and levels[i+1]<y else None
            for q in self.brush(path,i):
                if q in columns and columns[q]!=y:raise ValueError('总舵阶廊路幅自交 '+key)
                columns[q]=y
                if lower:
                    dx,dz=p[0]-lower[0],p[1]-lower[1];faces[q]=('east'if dx>0 else'west')if dx else('south'if dz>0 else'north')
        for (x,z),y in columns.items():
            original=int(self.terrain[z,x]);ground=min(original,y-1)
            if original-ground>self.cfg['local_earthwork_limit']:raise ValueError('总舵路面下挖超限')
            self.terrain[z,x]=ground
            for yy in range(ground+1,y-1):self.d['blocks'][x,yy,z]='minecraft:stone_bricks'
            face=faces.get((x,z));self.d['blocks'][x,y-1,z]=f'minecraft:stone_brick_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]'if face else'minecraft:stone_bricks'
            for yy in range(y,y+self.cfg['route_headroom']):
                old=self.d['blocks'].get((x,yy,z),'minecraft:air')
                if old.startswith('minecraft:grass_block'):self.d['blocks'].pop((x,yy,z),None)
            self.field[x,z]=y
        self.collision.__init__(self.d)
        self.records.append(dict(id=key,points=[[x,y,z]for(x,z),y in zip(path,levels)],width=self.cfg['route_width'],covered=covered))
        return path,levels

    def cover(self,path):
        for i,(x,y,z)in enumerate(path):
            if i<4 or i>=len(path)-4:continue
            a=path[i-1];b=path[i+1];along_x=a[0]!=b[0]
            for k in range(-2,3):
                xx,zz=(x,z+k)if along_x else(x+k,z)
                if not(0<=xx<self.w and 0<=zz<self.depth):continue
                top=y+5+(1 if k==0 else 0);p=(xx,top,zz)
                if any((xx,top+j,zz)in self.d['blocks']for j in(-1,0,1)):continue
                face=('south'if k<0 else'north')if along_x else('east'if k<0 else'west')
                block='minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]'if k==0 else f'minecraft:deepslate_tile_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]'
                self.d['blocks'][p]=block
            if i%6:continue
            for k in(-2,2):
                xx,zz=(x,z+k)if along_x else(x+k,z)
                if not(0<=xx<self.w and 0<=zz<self.depth)or(xx,zz)in self.field:continue
                if any((xx,yy,zz)in self.d['blocks']for yy in range(y,y+5)):continue
                for yy in range(int(self.terrain[zz,xx])+1,y):self.d['blocks'][xx,yy,zz]='minecraft:stone_bricks'
                for yy in range(y,y+5):self.d['blocks'][xx,yy,zz]='minecraft:dark_oak_log[axis=y]'

    def guard_edges(self):
        rails={}
        for record in self.records:
            points=record['points']
            for i,(x,y,z)in enumerate(points):
                before=points[max(0,i-1)];after=points[min(len(points)-1,i+1)];along_x=before[0]!=after[0]
                for side in(-2,2):
                    xx,zz=(x,z+side)if along_x else(x+side,z)
                    if not(0<=xx<self.w and 0<=zz<self.depth)or(xx,zz)in self.field or(xx,zz)in self.pad:continue
                    ground=int(self.terrain[zz,xx])
                    if y-ground<3 or any((xx,yy,zz)in self.d['blocks']for yy in range(ground+1,y+2)):continue
                    rails[xx,y,zz]=ground
        for(x,y,z),ground in rails.items():
            for yy in range(ground+1,y):self.d['blocks'][x,yy,z]='minecraft:stone_bricks'
            props=[]
            for name,dx,dz in [('east',1,0),('north',0,-1),('south',0,1),('west',-1,0)]:
                connected=(x+dx,y,z+dz)in rails or self.d['blocks'].get((x+dx,y,z+dz),'').startswith('minecraft:dark_oak_log')
                props.append(name+'='+str(connected).lower())
            self.d['blocks'][x,y,z]='minecraft:dark_oak_fence['+','.join(props)+',waterlogged=false]'

def finish_blocks(d):
    cfg=read();terrain=np.array(d['scene']['terrain'],dtype=int)
    for z in range(cfg['size'][1]):
        for x in range(cfg['size'][0]):d['blocks'].setdefault((x,int(terrain[z,x]),z),'minecraft:grass_block[snowy=false]')
    from province_capitals.central.game_models import rotate_state
    a,b,e,f=cfg['plot']
    # The existing sloping street corner already has native half-height treads.
    # Keep those exact cells instead of turning the corner into a flat platform.
    for x,z,foot2,face,wood,rid in site()['roads']['columns']:
        lx,lz=z-b,e-1-x
        if not(0<=lx<8 and 0<=lz<8):continue
        y=foot2//2-1-cfg['base_y'];s='minecraft:spruce_planks'if wood else'minecraft:stone_bricks'
        if face:s=f'minecraft:stone_brick_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]'
        d['blocks'][lx,y,lz]=rotate_state(s,3)
        if foot2%2:d['blocks'][lx,y+1,lz]='minecraft:stone_brick_slab[type=bottom,waterlogged=false]'
        for yy in range(math.ceil(foot2/2)-cfg['base_y'],math.ceil(foot2/2)-cfg['base_y']+3):
            if d['blocks'].get((lx,yy,lz),'').startswith('minecraft:grass_block'):d['blocks'].pop((lx,yy,lz),None)
    routes=Routes(d);spec=access();start=tuple(spec['paths'][0][::2]);start_y=round(spec['paths'][0][1]);connected={start:start_y}
    south=cfg['size'][0]-5
    corners=[(1,1),(south,1),(south,cfg['size'][1]-2),(7,cfg['size'][1]-2),(7,120),(1,120),(1,1)];ring=[]
    for a,b in zip(corners,corners[1:]):
        dx=(b[0]>a[0])-(b[0]<a[0]);dz=(b[1]>a[1])-(b[1]<a[1]);p=a
        while p!=b:ring.append(p);p=(p[0]+dx,p[1]+dz)
    ring=[p for p in ring if not(p[0]<8 and p[1]<8)]
    anchors={start:start_y};a,b,e,f=cfg['plot']
    for x,z,foot2,*_ in site()['roads']['columns']:
        local=(z-b,e-1-x)
        if local in set(ring)and local[0]!=south and(local[0]==1 or local[1]in(1,cfg['size'][1]-2)):anchors[local]=math.ceil(foot2/2)-cfg['base_y']
    for landing in cfg.get('public_landings',[]):
        for x,z,y in landing['points']:
            p=(x,z)
            if p not in ring or p in anchors and anchors[p]!=y:raise ValueError('新公共平台与原巷接点冲突 '+landing['id'])
            anchors[p]=y
    print('总舵公共环路',len(ring),'原路接点',len(anchors),flush=True)
    path,levels=routes.build('public_ring',ring,anchors,direct=True,landing=0);connected.update({p:y for p,y in zip(path,levels)})
    external={}
    for x,z,foot2,face,wood,rid in site()['roads']['columns']:
        lx,lz=z-b,e-1-x
        if lx==cfg['size'][0]-1 and 2<=lz<cfg['size'][1]-2:external.setdefault(('south',rid),[]).append((lx,lz,math.ceil(foot2/2)-cfg['base_y']))
        if lx==0 and 122<=lz<cfg['size'][1]-3:external.setdefault(('north',rid),[]).append((lx,lz,math.ceil(foot2/2)-cfg['base_y']))
    for (side,rid),points in external.items():
        points.sort(key=lambda p:p[1]);x,z,y=points[len(points)//2];end=(south if side=='south'else 7,z)
        if end not in connected:raise ValueError('原巷接点未抵达新公共通路')
        print('保留地块边缘原巷',side,rid,(x,z,y),end,connected[end],flush=True)
        path,levels=routes.build('public_edge_'+side+'_'+rid,[(x,z),end],{(x,z):y,end:connected[end]},landing=1);connected.update({p:y for p,y in zip(path,levels)})
    for key in cfg['route_order']:
        p=translate(cfg['parts'][key]['portal'],key);goal=(round(p[0]),round(p[2]));floor=math.ceil(p[1])
        stable=[q for q in connected if not any((q[0]+dx,q[1]+dz)in routes.pad for dx in(-1,0,1)for dz in(-1,0,1))and len({routes.field[x,z]for x in range(q[0]-3,q[0]+4)for z in range(q[1]-3,q[1]+4)if(x,z)in routes.field})<=1]
        if not stable:raise ValueError('总舵支路缺少可共享的等高接入平台 '+key)
        toward=cfg['route_vias'].get(key,[goal])[0]
        source=min(stable,key=lambda q:abs(q[0]-toward[0])+abs(q[1]-toward[1])+4*abs(connected[q]-floor))
        if key in cfg.get('route_starts',{}):
            source=tuple(cfg['route_starts'][key])
            if source not in connected:raise ValueError('作者接路起点未连接总舵公共通路 '+key)
        print('总舵接路',key,source,connected[source],goal,floor,flush=True)
        vias=list(cfg['route_vias'].get(key,[]))
        manual=bool(vias)
        approach=(goal[0],goal[1]+(4 if cfg['parts'][key].get('back_entry')else -4))
        if not manual and source[0]!=approach[0]and source[1]!=approach[1]:
            exits=[]
            for dx,dz in[(1,0),(-1,0),(0,1),(0,-1)]:
                q=(source[0]+4*dx,source[1]+4*dz)
                if not(1<=q[0]<routes.w-1 and 1<=q[1]<routes.depth-1):continue
                cells={(source[0]+i*dx+k*dz,source[1]+i*dz+k*dx)for i in range(5)for k in(-1,0,1)}
                if any(p in routes.field and routes.field[p]!=connected[source]for p in cells):continue
                if any(not routes.body_clear(x,z,connected[source])for x,z in cells):continue
                exits.append(q)
            if not exits:raise ValueError('总舵支路没有完整的等高起步段 '+key)
            vias=[min(exits,key=lambda p:abs(p[0]-approach[0])+abs(p[1]-approach[1]))]
        if not manual and approach!=source:vias.append(approach)
        points=[source,*vias,goal];path,levels=routes.build(key,points,{source:connected[source],goal:floor},key in cfg['covered_links'],landing=cfg.get('route_landing',{}).get(key,3));connected.update({p:y for p,y in zip(path,levels)})
        if key in ('archive','envoy'):
            i=len(path)//2;spec['extra_targets'].append(dict(id='west_gallery'if key=='archive'else'east_gallery',position=[path[i][0],levels[i],path[i][1]]))
    for step in cfg.get('entry_steps',[]):
        for p in step['cells']:
            if not(0<=p[0]<routes.w and 0<=p[2]<routes.depth):raise ValueError('院路衔接踏步越界 '+step['id'])
            old=d['converted'].get(tuple(p))
            if old and {b[6]for b in old[0]}-{'paving','floor','stone','stone_dark'}:raise ValueError('院路踏步覆盖楼体 '+step['id'])
            d['blocks'][tuple(p)]=step['block']
    for route in routes.records:
        if route['covered']:routes.cover(route['points'])
    north=next(s for s in cfg['elevated_spans']if s['id']=='north_cliff_gallery');a,b,e,f=north['bounds']
    routes.cover([p for p in routes.records[0]['points']if a<=p[0]<e and b<=p[2]<f])
    routes.guard_edges()
    d['terrain']=routes.terrain.reshape(-1).tolist();d['routes']=routes.records;d['path_field']=routes.field
    d['access']=spec
    d['construction_checks']={'maximum_terrain_cut':int((site()['terrain']-routes.terrain).max()),'building_floors':{k:v+cfg['base_y']for k,v in d['scene']['floor_by_part'].items()},'route_count':len(routes.records)}
    water={p for p,s in d['blocks'].items()if s.startswith('minecraft:water')}
    for p in water:
        for step in[(0,-1,0),(-1,0,0),(1,0,0),(0,0,-1),(0,0,1)]:
            q=tuple(a+b for a,b in zip(p,step))
            if d['blocks'].get(q,'minecraft:air')=='minecraft:air':raise ValueError('听雨院水池缺少封闭池壁或池底 '+str((p,q)))
    d['construction_checks']['contained_water_sources']=len(water)
    return d
