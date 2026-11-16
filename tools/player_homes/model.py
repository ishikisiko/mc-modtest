"""复用月洞水院的完整构件，组合独立住宅；不注册或改写游戏资源。"""
from collections import deque
from functools import lru_cache
import copy, json, math

from tools.reference_courtyard.assembly_scene import Site, build_scene
from tools.player_estates.geometry import state
from tools.player_estates.preview import parse


class Home(Site):
    def __init__(self, catalog, config, kit):
        super().__init__({**kit,'size':config['size']})
        self.catalog=catalog;self.config=config;self.layers={};self.rooms=[];self.targets={}
        self.c={**config,'reference':{'source':'月洞水院自有构件','scope':'独立住宅选型模板'}}
        self.floor={};self.doors=[];self.module_bounds=[]

    def put(self,p,block):
        super().put(p,block)
        name=block.split('[')[0]
        self.layers[p]='roof' if name=='yuanxian:courtyard_roof' else 'water' if name=='minecraft:water' else 'architecture'

    def door(self,x,y,z,facing='north'):
        for half,dy in [('lower',0),('upper',1)]:
            self.put((x,y+dy,z),state('dark_oak_door',facing=facing,half=half,hinge='left',open=False,powered=False))
        self.doors.append((x,y,z))

    def window(self,x,y,z,facing='north',key='octagonal'):
        self.place((x,y,z),'window',key,facing)

    def module(self,m):
        t=self.catalog['module_types'][m['type']];rx=m['roof_x'];F=m['front_z'];B=m['back_z']
        feet=self.catalog['room_feet_y'];eave=feet+t['eave_above_feet'];inset=t['body_inset'];width=t['roof_width']
        x0,x1=rx+inset,rx+width-inset;cx=(x0+x1-1)//2
        self.fill([x0,feet-1,F-1,x1,feet,B+1],'floor','wood')
        for z in range(F-1,B+1):
            for x in range(x0,x1):self.floor[x,z]=feet
        for x in (x0,x1-1):self.fill([x,feet,F,x+1,eave,B+1],'wall','plaster')
        for z in (F,B):self.fill([x0,feet,z,x1,eave,z+1],'wall','plaster')
        self.door(cx,feet,F)
        if width>=17:
            for wx in (x0+2,x1-5):
                self.window(wx,feet+1,F);self.window(wx,feet+1,B,key='round')
        else:self.window(x0+2,feet+1,B,key='square')
        for z in range(F+3,B-2,6):
            self.window(x0,feet+1,z+2,'west','round')
            self.window(x1-1,feet+1,z,'east','round')
        if t['post_columns']:
            for z in (F-1,B-1):
                for x in t['post_columns']:self.place((rx+x,feet,z),'column','round')
                for x,key in t['beam_segments']:self.place((rx+x,feet+4,z),'beam',key)
                for x in range(min(t['post_columns']),max(t['post_columns'])+1):
                    self.place((rx+x,feet+5,z),'wall','timber_transom')
        for z in range(F-t['front_roof_overhang'],B+2):
            ends=(F-1,B) if t['post_columns'] else (F,B)
            key=t['end_key'] if z in ends else t['roof_key'] if F<=z<B else t['overhang_key']
            self.place((rx,eave,z),'roof',key)
        for x in range(cx-1,cx+2):
            self.put((x,feet-1,F-2),state('spruce_stairs',facing='south',half='bottom',shape='straight',waterlogged=False))
            self.floor[x,F-2]=feet-.5
        self.rooms.append({'id':m['id'],'name':m['name'],'bounds':[x0,F,x1,B+1],'floor_y':feet})
        self.module_bounds.append([rx,F-t['front_roof_overhang'],rx+width,B+2])

    def boundary(self):
        W,_,D=self.c['size'];n=self.catalog['boundary_inset'];y=self.catalog['court_feet_y'];h=self.catalog['wall_height']
        for box in ([n,y,n,W-n,y+h,n+1],[n,y,D-n-1,W-n,y+h,D-n],
                    [n,y,n,n+1,y+h,D-n],[W-n-1,y,n,W-n,y+h,D-n]):
            self.fill(box,'wall','plaster')
            self.fill([box[0],y+h,box[2],box[3],y+h+1,box[5]],'floor','stone_slab')
        for cx in self.config['front_gates']:self.place((cx-2,y,n),'gate','stone')
        for q in self.config['screens']:
            line=q['line'];a,b=q['span'];g=q['moon_gate_start']
            if q['axis']=='x':
                self.fill([line,y,a,line+1,y+h,b],'wall','plaster')
                self.fill([line,y+h,a,line+1,y+h+1,b],'floor','stone_slab')
                self.place((line,y,g),'gate','stone','east')
            else:
                self.fill([a,y,line,b,y+h,line+1],'wall','plaster')
                self.place((g,y,line),'gate','stone')

    def build(self):
        W,_,D=self.c['size'];y=self.catalog['court_feet_y']
        self.fill([0,0,0,W,y,D],'floor','stone')
        self.floor={(x,z):y for z in range(D) for x in range(W)}
        for m in self.config['modules']:self.module(m)
        for p in self.config['partitions']:
            a,b=p['span'];line=p['line'];f=self.catalog['room_feet_y']
            if p['axis']=='z':self.fill([a,f,line,b,f+5,line+1],'wall','plaster');self.door(p['door'],f,line)
            else:self.fill([line,f,a,line+1,f+5,b],'wall','plaster');self.door(line,f,p['door'],'east')
        self.boundary()
        for pond in self.config['ponds']:
            a,b,c,d=pond
            for z in range(b,d):
                for x in range(a,c):self.put((x,y-1,z),'minecraft:water[level=0]');self.floor.pop((x,z),None)
        decor=self.catalog['decor_materials']
        for feature in self.config['courtyard_features']:
            if feature['kind']=='bench':
                x,z=feature['position']
                for dx in range(feature['length']):
                    self.put((x+dx,y,z),state(decor['bench'],facing=feature['facing'],half='bottom',shape='straight',waterlogged=False))
                continue
            a,b,c,d=feature['box'];well=feature['kind']=='well'
            for z in range(b,d):
                for x in range(a,c):
                    if x in(a,c-1)or z in(b,d-1):self.put((x,y,z),decor['well_rim']if well else decor['planter_rim'])
                    else:
                        self.put((x,y-1,z),'minecraft:water[level=0]'if well else decor['soil']);self.floor.pop((x,z),None)
            if not well:self.put(((a+c-1)//2,y,(b+d-1)//2),decor['plant'])
        for q in self.config['furniture']:
            x,z=q['position'];f=self.catalog['room_feet_y'];kind=q['kind']
            if kind=='bed':
                for part,dz in [('foot',0),('head',1)]:self.put((x,f,z+dz),state('red_bed',part=part,facing='south',occupied=False))
            elif kind in ('furnace','barrel'):self.put((x,f,z),state(kind,facing='north',**({'lit':False}if kind=='furnace'else{'open':False})))
            elif kind=='anvil':self.put((x,f,z),state('anvil',facing='north'))
            else:self.put((x,f,z),'minecraft:'+kind)
        for x,z in self.config['lanterns']:self.place((x,y,z),'lantern','standing')
        for t in self.config['room_targets']:
            x,z=t['position'];self.targets[t['name']]=(x,self.floor[x,z],z)
        return self


def build_home(root,catalog,config):
    kit=json.loads((root/catalog['kit']).read_text())
    return Home(catalog,config,kit).build()


def build_reference(root,catalog):
    kit=json.loads((root/catalog['kit']).read_text());site=build_scene(kit)
    spec=catalog['reference'];site.config=spec;site.c={**spec,'reference':{'source':'找回的月洞水院','scope':'原尺寸构件样板'}}
    site.layers={p:'roof'if s.startswith('yuanxian:courtyard_roof[')else'water'if s.startswith('minecraft:water[')else'architecture'for p,s in site.blocks.items()}
    site.blocks={p:s for p,s in site.blocks.items()if s!='minecraft:air'}
    site.rooms=[];site.targets={};site.module_bounds=[]
    return site


def assembly_check(site):
    failures=[]
    for i,a in enumerate(site.assemblies):
        for p in a['parts']:
            p=tuple(p)
            if site.owners.get(p)!=i or not site.blocks[p].startswith('yuanxian:'+a['family']+'['):failures.append([i,list(p)])
    if failures:raise ValueError(('完整构件被覆盖',failures[:5]))
    return {'whole_assemblies':len(site.assemblies),'overwritten_parts':0}


def walk_check(root,catalog,site):
    """用当前1/16格碰撞目录核对站位与房间动线，原版门按可开启处理。"""
    entries=json.loads((root/catalog['collision_catalog']).read_text())['families'];families={f['id']:f for f in entries}
    @lru_cache(None)
    def colliders(block):
        name,p=parse(block)
        if name.startswith('yuanxian:courtyard_'):
            v=families[name.split(':')[1]]['variants'][int(p['variant'])];part=int(p.get('part','0'));boxes=v['parts'][part]['boxes'];turn=['north','east','south','west'].index(p['facing']);result=[]
            for box in boxes:
                x0,y0,z0,x1,y1,z1=[a/16 for a in box]
                for _ in range(turn):x0,z0,x1,z1=1-z1,x0,1-z0,x1
                result.append([x0,y0,z0,x1,y1,z1])
            return result
        if name.endswith('_door')or name=='minecraft:air':return []
        if name.endswith('_stairs'):return [[0,0,0,1,.5,1]]
        if name.endswith('_bed'):return [[0,0,0,1,.5625,1]]
        return [[0,0,0,1,1,1]]
    def standable(x,y,z):
        supported=False
        for yy in range(math.floor(y)-1,math.floor(y)+1):
            block=site.blocks.get((x,yy,z))
            if block and any(abs(yy+c[4]-y)<1e-5 and c[0]<=.5<=c[3] and c[2]<=.5<=c[5] for c in colliders(block)):
                supported=True
        if not supported:return False
        body=[x+.2,y+.12,z+.2,x+.8,y+1.8,z+.8]
        for yy in range(math.floor(y),math.ceil(y+1.8)):
            block=site.blocks.get((x,yy,z))
            if not block:continue
            for c in colliders(block):
                moved=[c[0]+x,c[1]+yy,c[2]+z,c[3]+x,c[4]+yy,c[5]+z]
                if all(min(body[i+3],moved[i+3])-max(body[i],moved[i])>1e-5 for i in range(3)):return False
        return True
    nodes={(x,z):y for (x,z),y in site.floor.items()if standable(x,y,z)}
    start=(site.config['front_gates'][0],0)
    if start not in nodes:raise ValueError('住宅入口站位被阻挡')
    seen={start};q=deque([start])
    while q:
        x,z=q.popleft()
        for n in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if n in nodes and n not in seen and abs(nodes[n]-nodes[x,z])<=.6:seen.add(n);q.append(n)
    missing={name:list(pos)for name,pos in site.targets.items()if(pos[0],pos[2])not in seen}
    if missing:raise ValueError(('房间离线动线不通',missing))
    return {'status':'pass','reachable_cells':len(seen),'targets':{k:list(v)for k,v in site.targets.items()},'failed':{},'doors':'assumed_openable','scope':'格点站位及当前构件碰撞，未代替游戏操作'}
