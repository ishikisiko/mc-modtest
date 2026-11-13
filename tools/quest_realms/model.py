"""按作者布局生成有内部空间的方块场景；本模块不写游戏资源。"""
from __future__ import annotations

from collections import defaultdict, deque
import json
import math
from pathlib import Path
import random

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'blueprints/quest_realms'


def read(ident):
    return json.loads((SOURCE / f'{ident}.json').read_text())


def distance_segment(x, z, a, b):
    dx, dz = b[0]-a[0], b[2]-a[2]
    t = max(0, min(1, ((x-a[0])*dx+(z-a[2])*dz)/(dx*dx+dz*dz or 1)))
    return math.hypot(x-a[0]-dx*t, z-a[2]-dz*t), a[1]+(b[1]-a[1])*t


def routes(spec):
    nodes = {n['id']: n for n in spec['nodes']}
    return [dict(e, points=[nodes[e['a']]['pos'], *e['via'], nodes[e['b']]['pos']]) for e in spec['edges']]


def reachable(spec, done=()):
    found = {spec['entry']}
    queue = deque(found)
    edges = spec['edges']
    while queue:
        node = queue.popleft()
        for edge in edges:
            if not set(edge['requires']) <= set(done):
                continue
            other = edge['b'] if edge['a'] == node else edge['a'] if edge['b'] == node else None
            if other and other not in found:
                found.add(other)
                queue.append(other)
    return found


def validate(spec, catalog=None):
    catalog = catalog or read('catalog')
    nodes = {n['id']: n for n in spec['nodes']}
    if len(nodes) != len(spec['nodes']) or len(nodes) < catalog['rules']['minimum_node_count']:
        raise ValueError('节点重复或数量不足')
    action_ids = {a['id'] for a in spec['actions']}
    if len(action_ids) != len(spec['actions']):
        raise ValueError('机关名称重复')
    for edge in spec['edges']:
        if edge['a'] not in nodes or edge['b'] not in nodes or edge['a'] == edge['b']:
            raise ValueError('路线端点无效')
        if not set(edge['requires']) <= action_ids:
            raise ValueError('路线引用未知机关')
    all_reached = reachable(spec, action_ids)
    if all_reached != set(nodes):
        raise ValueError('全部机关开启后仍有孤立区域：' + str(set(nodes)-all_reached))
    cycles = len(spec['edges'])-len(nodes)+1
    if cycles < catalog['rules']['minimum_cycle_count']:
        raise ValueError('回环数量不足')
    done = set()
    for action in spec['actions']:
        if not set(action.get('requires', [])) <= done:
            raise ValueError('机关先后关系无效：' + action['id'])
        if action['node'] not in reachable(spec, done):
            raise ValueError('机关不能从当前阶段到达：' + action['id'])
        done.add(action['id'])
    for node in nodes.values():
        if not all(0 <= v < bound for v, bound in zip(node['pos'], spec['size'])):
            raise ValueError('节点越过地图边界：' + node['id'])
    for route in routes(spec):
        for a, b in zip(route['points'], route['points'][1:]):
            run = math.hypot(b[0]-a[0], b[2]-a[2])
            if route['kind'] != 'lift' and abs(b[1]-a[1]) > run:
                raise ValueError('路线高度变化不能用台阶承接：' + route['a']+' / '+route['b'])
    return dict(nodes=len(nodes), edges=len(spec['edges']), cycles=cycles,
                optional_areas=sum(n['role']=='optional' for n in nodes.values()),
                phases=[dict(name=p['name'], reachable=len(reachable(spec, p['done']))) for p in spec['phases']])


def standing_issues(spec, boxes, catalog):
    """只核对作者关键点的站立体积；不冒充连续路线或原生碰撞验收。"""
    r=catalog['rules'];bad=[]
    for n in spec['nodes']:
        x,y,z=n['pos'];rad=r['player_radius'];height=r['player_height']
        hits={b[7]for b in boxes if not b[8] and b[7]!='water'
              and b[0]<x+rad and b[0]+b[3]>x-rad
              and b[1]<y+height and b[1]+b[4]>y+r['standing_epsilon']
              and b[2]<z+rad and b[2]+b[5]>z-rad}
        if hits:bad.append(dict(node=n['id'],layers=sorted(hits)))
    return bad


def camera_issues(spec, boxes):
    bad=[]
    for c in spec['cameras'][1:]:
        x,y,z=c['eye']
        if any(not b[8]and b[7]!='water'and all(b[i]<v<b[i]+b[i+3]for i,v in enumerate((x,y,z)))for b in boxes):
            bad.append(c['name'])
    return bad


class Scene:
    def __init__(self, spec, catalog):
        self.spec, self.catalog = spec, catalog
        self.boxes = []
        self.materials = list(catalog['palette'])
        self.rng = random.Random(spec['terrain']['seed'])
        self.paths = routes(spec)
        self.segments = [(a,b,e) for e in self.paths for a,b in zip(e['points'],e['points'][1:])]
        self.surface = {}
        self.details = defaultdict(int)

    def box(self,x,y,z,w,h,d,mat='stone',layer='detail',condition=''):
        if min(w,h,d) <= 0:
            return
        if layer in ('prop','post'):
            for a,b,e in self.segments:
                distance,feet=distance_segment(x+w/2,z+d/2,a,b)
                if distance < 2+min(w,d)/2 and y < feet+2.2 and y+h > feet+.1:
                    return
        if mat not in self.materials:
            raise ValueError('未知材质：'+mat)
        self.boxes.append([*[round(v,3) for v in (x,y,z,w,h,d)], self.materials.index(mat),layer,condition])
        self.details[layer] += 1

    def block(self,x,y,z,mat='stone',layer='detail',condition=''):
        self.box(x,y,z,1,1,1,mat,layer,condition)

    def natural(self,x,z):
        t=self.spec['terrain']
        h=t['base']
        for cx,cz,rx,rz,rise in t['ridges']:
            q=((x-cx)/rx)**2+((z-cz)/rz)**2
            h=max(h,t['base']+rise*math.exp(-q*1.6))
        h += 1.7*math.sin(x*.113+z*.07)+1.1*math.cos(x*.043-z*.139)
        for v in t['valleys']:
            ps=[[p[0],v['floor'],p[1]]for p in v['points']]
            dist=min(distance_segment(x,z,a,b)[0]for a,b in zip(ps,ps[1:]))
            strength=max(0,1-dist/(v['width']*1.8))
            h=h*(1-strength)+v['floor']*strength
        for s in t.get('shafts',[]):
            q=((x-s['center'][0])/s['radius'][0])**2+((z-s['center'][1])/s['radius'][1])**2
            if q < 1:
                h=min(h,s['bottom']+math.floor(q*5)*2)
        quantum=self.catalog['rules']['terrain_quantum']
        return max(2,round(h/quantum)*quantum)

    def cuts(self,x,z,h):
        spans=[]
        for a,b,e in self.segments:
            distance,feet=distance_segment(x,z,a,b)
            if e['kind']=='lift' or distance > self.catalog['rules']['path_width']/2+3:
                continue
            if e['kind'] == 'tunnel' and h > feet+10:
                spans.append((feet-1,feet+self.catalog['rules']['tunnel_height']))
            elif h >= feet-1:
                h=min(h,feet-1)
        for s in self.spec['structures']:
            cx,y,cz=s['pos'];w,hh,d=s['size']
            if abs(x-cx) > w/2+3 or abs(z-cz) > d/2+3:
                continue
            if s['kind'] in ('vault','cave') and h>y+hh+2:
                spans.append((y-1,y+hh+2))
            elif s['kind'] not in ('wheel','marker','winch'):
                h=min(h,y-1)
        for pool in self.spec['terrain']['pools']:
            px,pz,w,d=pool['rect']
            if px-2<=x<=px+w+2 and pz-2<=z<=pz+d+2:
                h=min(h,pool.get('drained_y',pool['y']-3)-2)
        for fall in self.spec['terrain']['waterfalls']:
            fx,fy,fz=fall['pos']
            if fx-2<=x<=fx+fall['width']+2 and fz-3<=z<=fz+1:
                h=min(h,fy-fall['height']-1)
        for course in self.spec['terrain'].get('watercourses',[]):
            for a,b in zip(course['points'],course['points'][1:]):
                distance,feet=distance_segment(x,z,a,b)
                if distance<=course['width']/2+2:h=min(h,feet-1)
        return max(1,h),spans

    def terrain(self):
        c=self.catalog['rules']['terrain_cell'];t=self.spec['terrain'];width,_,depth=self.spec['size']
        for x in range(0,width,c):
            for z in range(0,depth,c):
                h,spans=self.cuts(x+c/2,z+c/2,self.natural(x+c/2,z+c/2))
                self.surface[x,z]=h
                intervals=[(0,h)]
                for lo,hi in spans:
                    intervals=[p for a,b in intervals for p in ((a,min(b,lo)),(max(a,hi),b)) if p[1]-p[0]>.1]
                for low,high in intervals:
                    mat=t['rock'] if (x//c+z//c)%9 else 'rock'
                    self.box(x,low,z,c,high-low,c,mat,'geology')
                if intervals and intervals[-1][1]==h:
                    mat=t['cover'] if h>=t['base']-4 else t['rock']
                    self.box(x,h,z,c,.7,c,mat,'ground')
        for pool in t['pools']:
            x,z,w,d=pool['rect'];mat=pool.get('material','water');y=pool['y']
            if 'drained_after' in pool:
                self.box(x,y-.5,z,w,.5,d,mat,'water','!'+pool['drained_after'])
                self.box(x,pool['drained_y']-.5,z,w,.5,d,mat,'water',pool['drained_after'])
            else:
                self.box(x,y-.6,z,w,.6,d,mat,'water')
            for xx,zz,ww,dd in [(x-1,z-1,w+2,1),(x-1,z+d,w+2,1),(x-1,z,1,d),(x+w,z,1,d)]:
                self.box(xx,pool.get('drained_y',y-3)-1,zz,ww,4,dd,'stone','masonry')
        for course in t.get('watercourses',[]):
            # 水路由作者声明的源池接入山坡和下游，逐格承接高差。
            for a,b in zip(course['points'],course['points'][1:]):
                steps=max(1,math.ceil(max(abs(b[0]-a[0]),abs(b[2]-a[2]))/2))
                for j in range(steps+1):
                    x,y,z=[a[i]+(b[i]-a[i])*j/steps for i in range(3)]
                    self.box(x-course['width']/2,y-.4,z-1,course['width'],.4,2,'water','water')
                    if j:
                        previous=a[1]+(b[1]-a[1])*(j-1)/steps
                        self.box(x-course['width']/2,min(y,previous),z-.2,course['width'],abs(y-previous)+.2,.4,'water','water')
            x,y,z=course['points'][0]
            self.box(x-course['width'],y-.4,z-course['width'],course['width']*2,.4,course['width']*2,'water','water')
        for river in t.get('rivers',[]):
            ps=[[p[0],river['y'],p[1]]for p in river['points']]
            for a,b in zip(ps,ps[1:]):
                for x in range(math.floor(min(a[0],b[0])-river['width']),math.ceil(max(a[0],b[0])+river['width']),2):
                    for z in range(math.floor(min(a[2],b[2])-river['width']),math.ceil(max(a[2],b[2])+river['width']),2):
                        if distance_segment(x+1,z+1,a,b)[0]<=river['width']/2:
                            self.box(x,river['y']-.4,z,2,.4,2,'water','water')

    def close_to_route(self,x,z,distance):
        return any(distance_segment(x,z,a,b)[0]<distance for a,b,_ in self.segments)

    def trees(self):
        t=self.spec['terrain'];width,_,depth=self.spec['size'];placed=[]
        for _ in range(t['trees']*20):
            if len(placed)>=t['trees']:break
            x=self.rng.randrange(10,width-10);z=self.rng.randrange(12,depth-10)
            if self.close_to_route(x,z,9):continue
            if any(abs(x-s['pos'][0])<s['size'][0]/2+8 and abs(z-s['pos'][2])<s['size'][2]/2+8 for s in self.spec['structures']):continue
            if any(math.hypot(x-a,z-b)<11 for a,b in placed):continue
            if any(p['rect'][0]-4<x<p['rect'][0]+p['rect'][2]+4 and p['rect'][1]-4<z<p['rect'][1]+p['rect'][3]+4 for p in t['pools']):continue
            if any(abs(x-f['pos'][0])<f['width']+6 and abs(z-f['pos'][2])<12 for f in t['waterfalls']):continue
            if any(distance_segment(x,z,a,b)[0]<c['width']+3 for c in t.get('watercourses',[])for a,b in zip(c['points'],c['points'][1:])):continue
            y=self.natural(x,z)+1
            if y<t['base']-2:continue
            placed.append((x,z));self.tree(x,y,z,self.rng.randrange(10,18),pine=t['tree_species']=='spruce')
        self.tree_count=len(placed)

    def tree(self,x,y,z,h,pine=False):
        self.box(x,y,z,1.3,h,1.3,'darkwood','vegetation')
        if pine:
            for level in range(4):
                radius=4.5-level*.8;yy=y+4+level*2.5
                self.box(x-radius,yy,z-radius,radius*2,2,radius*2,'leaf','vegetation')
                self.box(x-radius+1,yy+2,z-radius+1,radius*2-2,1.5,radius*2-2,'leaf','vegetation')
        else:
            for dx,dz,dy,r in [(-3,-1,-4,3.5),(2,2,-2,4),(1,-3,0,3.2),(-2,2,1,3)]:
                self.box(x+dx-r,y+h+dy,z+dz-r,r*2,3,r*2,'leaf','vegetation')
                self.box(x+dx-r+1,y+h+dy+3,z+dz-r+1,r*2-2,1,r*2-2,'leaf','vegetation')
        for dx,dz in [(-1,0),(1,0),(0,-1),(0,1)]:
            self.box(x+dx,y,z+dz,1,2.5,1,'darkwood','vegetation')

    def paths_geometry(self):
        surfaces={}
        for e in self.paths:
            if e['kind']=='lift':continue
            mat='floor'if e['kind']=='bridge' else 'paving'if e['kind']in('stairs','path') else 'rock_dark'
            cells={}
            for a,b in zip(e['points'],e['points'][1:]):
                for x in range(math.floor(min(a[0],b[0])-3),math.ceil(max(a[0],b[0])+4)):
                    for z in range(math.floor(min(a[2],b[2])-3),math.ceil(max(a[2],b[2])+4)):
                        distance,feet=distance_segment(x+.5,z+.5,a,b)
                        if distance>3.4:continue
                        if (x,z)not in cells or distance<cells[x,z][0]:cells[x,z]=distance,feet
            for (x,z),(_,feet)in cells.items():
                for n in self.spec['nodes']:
                    if math.hypot(x+.5-n['pos'][0],z+.5-n['pos'][2])<4.5 and abs(feet-n['pos'][1])<5:
                        feet=n['pos'][1];break
                surfaces[x,round(feet*2)/2,z,mat]=True
            if e['kind']=='bridge':
                self.bridge(e)
            if e['requires']:
                a,b=e['points'][0],e['points'][1];x,y,z=[(a[i]+b[i])/2 for i in range(3)]
                self.box(x-2.5,y,z-.3,5,3,.6,'darkwood','gate','not_all:'+','.join(e['requires']))
                for dx in [-3.5,3]:self.box(x+dx,y,z-.5,.8,5,1,'stone','masonry')
            if e['kind']=='tunnel':
                for a,b in zip(e['points'],e['points'][1:]):
                    run=math.hypot(b[0]-a[0],b[2]-a[2]);n=max(1,int(run//12))
                    for j in range(n+1):
                        p=[a[i]+(b[i]-a[i])*j/n for i in range(3)]
                        self.support(*p,axis=abs(b[0]-a[0])>abs(b[2]-a[2]))
        rows=defaultdict(list)
        for x,y,z,mat in surfaces:rows[y,z,mat].append(x)
        for (y,z,mat),xs in rows.items():
            xs.sort();start=last=xs[0]
            for x in xs[1:]+[10**9]:
                if x==last+1:last=x;continue
                self.box(start,y-1,z,last-start+1,1,1,mat,'path');start=last=x
        for node in self.spec['nodes']:
            x,y,z=node['pos'];self.box(x-4,y-1,z-4,8,1,8,'paving','path')
            if node['role'] in ('mechanism','objective'):
                self.lantern(x-4,y,z+3)
            elif node['role']=='optional':
                self.crate(x+3,y,z+3)

    def support(self,x,y,z,axis=False):
        if axis:
            for dz in [-4,3]:self.box(x-.5,y,z+dz,1,6.5,1,'darkwood','support')
            self.box(x-.7,y+6,z-4,1.4,1,8,'wood','support')
        else:
            for dx in [-4,3]:self.box(x+dx,y,z-.5,1,6.5,1,'darkwood','support')
            self.box(x-4,y+6,z-.7,8,1,1.4,'wood','support')
        self.lantern(x+3,y+3,z)

    def bridge(self,e):
        for a,b in zip(e['points'],e['points'][1:]):
            dx,dz=b[0]-a[0],b[2]-a[2];run=math.hypot(dx,dz);n=max(1,int(run/3))
            nx,nz=-dz/(run or 1),dx/(run or 1)
            for j in range(n+1):
                p=[a[i]+(b[i]-a[i])*j/n for i in range(3)]
                for side in [-1,1]:
                    x,z=p[0]+nx*side*3.8,p[2]+nz*side*3.8
                    self.box(x-.2,p[1],z-.2,.4,1.5,.4,'darkwood','rail')
                    if abs(dx)>=abs(dz):self.box(x-1.7,p[1]+1.1,z-.12,3.6,.25,.24,'wood','rail')
                    else:self.box(x-.12,p[1]+1.1,z-1.7,.24,.25,3.6,'wood','rail')
                if j%4==0:
                    floor=min(p[1]-2,self.natural(p[0],p[2]))
                    self.box(p[0]-1,p[1]-3,p[2]-4,2,2,8,'darkwood','support')
                    self.box(p[0]-1,floor,p[2]-1,2,max(1,p[1]-floor-2),2,'stone','support')

    def roof(self,x,y,z,w,d,kind='hip',damage=0):
        ROOFS[kind](self,x,y,z,w,d,damage)

    def doorway(self,x,y,z):
        return any(distance_segment(x,z,a,b)[0]<4.5 and abs(distance_segment(x,z,a,b)[1]-y)<3 for a,b,_ in self.segments)

    def wall(self,x,y,z,w,h,d):
        along_x=w>d;length=w if along_x else d
        for i in range(math.ceil(length/2)):
            a=x+2*i if along_x else x;b=z if along_x else z+2*i
            if self.doorway(a+(.7 if along_x else 0),y,b+(0 if along_x else .7)):continue
            ww=min(2,length-2*i)if along_x else w;dd=d if along_x else min(2,length-2*i)
            self.box(a,y,b,ww,1.8,dd,'brick','wall')
            self.box(a,y+1.8,b,ww,h-1.8,dd,'plaster','wall')
            if i%2==0:
                self.box(a+.1,y+2.6,b-.12 if along_x else b+.1,max(.4,ww-.2),2.5,max(.4,dd-.2),'window','window')
        self.box(x,y+h,z,w,.6,d,'darkwood','beam')

    def hall(self,s):
        cx,y,cz=s['pos'];w,h,d=s['size'];x,z=cx-w/2,cz-d/2;levels=s.get('levels',1)
        rise=h/levels
        self.box(x-1,y-2,z-1,w+2,2,d+2,'stone','foundation')
        for px,pz in((x+1,z+1),(x+w-3,z+1),(x+1,z+d-3),(x+w-3,z+d-3)):
            low=min(y-2,self.natural(px,pz))
            self.box(px,low,pz,2,y-low,2,'stone','foundation')
        for level in range(levels):
            yy=y+level*rise
            # 上层楼板在同一侧留楼梯井。
            if level:
                self.box(x,yy-.6,z,w-7,.6,d,'floor','floor')
                self.box(x+w-7,yy-.6,z,7,.6,max(1,d-16),'floor','floor')
            else:self.box(x,yy-.6,z,w,.6,d,'floor','floor')
            for xx in range(0,math.ceil(w)+1,5):
                for zz in (0,d):self.box(x+min(xx,w)-.35,yy,z+zz-.35,.7,rise,.7,'redwood','post')
            for zz in range(5,math.ceil(d),5):
                for xx in(0,w):self.box(x+xx-.35,yy,z+zz-.35,.7,rise,.7,'darkwood','post')
            if not s.get('open'):
                for side in [(x,yy,z,w,rise-1,.8),(x,yy,z+d-.8,w,rise-1,.8),(x,yy,z,.8,rise-1,d),(x+w-.8,yy,z,.8,rise-1,d)]:self.wall(*side)
            else:
                self.box(x,yy+rise-1,z,w,1,1,'darkwood','beam');self.box(x,yy+rise-1,z+d-1,w,1,1,'darkwood','beam')
            self.furniture(s.get('use','tea'),x+3,yy,z+3,w-6,d-6,level)
            if level<levels-1:
                n=math.ceil(rise*2)
                for j in range(n):
                    self.box(x+w-6,yy+j*.5,z+d-2-j,4,.5,1,'floor','stairs')
                for j in range(0,n,3):self.box(x+w-2,yy+j*.5,z+d-2-j,.3,2.2,.4,'wood','rail')
            if levels==1:
                self.roof(x-1.5,yy+rise,z-1.5,w+3,d+3,damage=s.get('damage',0) if level==levels-1 else 0)
            elif level<levels-1:
                for row in range(3):
                    ex,ez=x-2+row,z-2+row;ew,ed=w+4-row*2,d+4-row*2
                    for xx,zz,ww,dd in [(ex,ez,ew,1),(ex,ez+ed-1,ew,1),(ex,ez,1,ed),(ex+ew-1,ez,1,ed)]:
                        self.box(xx,yy+rise-1+row*.5,zz,ww,.6,dd,'tile','roof')
        if levels>1:self.roof(x-2,y+h,z-2,w+4,d+4,damage=s.get('damage',0))
        for xx in (x+2,x+w-3):self.lantern(xx,y+2,z-.8)
        for j in range(3):
            self.box(cx-4,y-1-j*.5,z-2-j,8,.5,1,'stone','stairs')
        if s.get('damage'):
            for dx,dz,hh in [(0,-3,1),(4,-6,2),(9,-4,1),(w+1,2,2)]:
                self.box(x+dx,y,z+dz,2,hh,3,'rock','rubble')

    def lantern(self,x,y,z):
        self.box(x-.5,y,z-.5,1,1.5,1,'darkwood','lamp')
        self.box(x-.38,y+.2,z-.38,.76,.9,.76,'lamp','lamp')
        self.box(x-.65,y+1.5,z-.65,1.3,.3,1.3,'tile_dark','lamp')

    def crate(self,x,y,z):
        self.box(x,y,z,2,2,2,'wood','prop')
        for yy in [.2,1.6]:
            self.box(x-.05,y+yy,z-.05,2.1,.18,2.1,'darkwood','prop')

    def furniture(self,use,x,y,z,w,d,level=0):
        if min(w,d)<5:return
        if use=='archive':
            for xx in range(0,max(1,int(w)-4),6):
                for zz in (0,max(0,d-3)):
                    self.bookcase(x+xx,y,z+zz,4,4.8,1.5)
            for xx in range(3,max(4,int(w)-4),9):
                self.table(x+xx,y,z+d/2,4,2)
        elif use=='barracks':
            for xx in range(0,int(w)-2,5):
                self.box(x+xx,y,z,3,.7,5,'wood','prop');self.box(x+xx,y+.7,z+1,3,.4,3,'cloth','prop')
                self.crate(x+xx,y,z+d-2)
        elif use=='depot':
            for xx in range(0,int(w)-2,4):
                for zz in (0,d-3):
                    self.crate(x+xx,y,z+zz)
                    if int(xx)%3:self.crate(x+xx+.4,y+2,z+zz)
        elif use=='workshop':
            for xx in range(0,int(w)-4,7):
                self.table(x+xx,y,z,5,3)
                self.box(x+xx+1,y+2,z+.5,1,1.4,1,'iron','prop')
            for zz in range(2,int(d)-3,6):self.crate(x,y,z+zz)
        elif use=='shrine':
            self.box(x+w/2-3,y,z+d-5,6,2,4,'stone','altar')
            self.box(x+w/2-1,y+2,z+d-4,2,3,1,'bronze','altar')
            self.lantern(x+2,y,z+d/2);self.lantern(x+w-2,y,z+d/2)
        else:
            for xx in range(0,int(w)-4,8):self.table(x+xx,y,z+d/2,4,3)

    def table(self,x,y,z,w,d):
        for xx in(0,w-.3):
            for zz in(0,d-.3):self.box(x+xx,y,z+zz,.3,1.6,.3,'darkwood','prop')
        self.box(x,y+1.6,z,w,.3,d,'wood','prop')
        self.box(x+.4,y+1.9,z+.4,1.5,.08,1,'book_cream','prop')

    def bookcase(self,x,y,z,w,h,d):
        self.box(x,y,z,w,.3,d,'darkwood','prop')
        for yy in(1.4,2.8,4.2):
            self.box(x,y+yy,z,w,.25,d,'wood','prop')
            for j in range(int(w*2)):
                self.box(x+j*.5,y+yy-.9,z+.1,.38,.8 if j%3 else 1,.9,['book_blue','book_red','book_cream'][j%3],'prop')
        for xx in(0,w-.3):self.box(x+xx,y,z,.3,h,d,'darkwood','prop')

    def arch(self,s):
        cx,y,cz=s['pos'];w,h,d=s['size'];x,z=cx-w/2,cz-d/2
        for xx in(x,x+w-4):
            self.box(xx,y,z,4,h-3,d,'stone','masonry')
            self.box(xx-1,y,z-1,6,2,d+2,'rock_dark','masonry')
        for i in range(4):
            self.box(x+3+i,y+h-6+i,z,w-6-2*i,1,d,'stone','masonry')
        self.box(x-1,y+h-2,z-1,w+2,2,d+2,'carved'if 'carved'in self.materials else 'brick','masonry')
        self.roof(x-2,y+h,z-2,w+4,d+4)

    def frame(self,s):
        cx,y,cz=s['pos'];w,h,d=s['size']
        for x in(cx-w/2,cx+w/2-1):
            for z in(cz-d/2,cz+d/2-1):
                self.box(x,y,z,1.4,h,1.4,'darkwood','support')
                self.box(x-.4,y+h-1,z-.4,2.2,1.6,2.2,'bronze','support')
        self.box(cx-w/2,y+h,cz-d/2,w,1.5,1.5,'wood','support')
        self.box(cx-w/2,y+h,cz+d/2-1,w,1.5,1.5,'wood','support')
        self.box(cx-w/2,y+h,cz-d/2,1.5,1.5,d,'wood','support')
        self.box(cx+w/2-1,y+h,cz-d/2,1.5,1.5,d,'wood','support')

    def wheel(self,s):
        x,y,z=s['pos'];w,h,d=s['size'];r=min(w,h)/2
        if any(math.hypot(x-n['pos'][0],z-n['pos'][2])<2 and abs(y-n['pos'][1])<3 for n in self.spec['nodes']):
            x+=r+3
        for j in range(32):
            a=j*math.tau/32
            xx,yy=x+math.cos(a)*r,y+r+math.sin(a)*r
            self.box(xx-.6,yy-.6,z-1,1.2,1.2,2,'wood','mechanism')
        for j in range(8):
            a=j*math.tau/8
            for k in range(1,int(r)):
                self.box(x+math.cos(a)*k-.35,y+r+math.sin(a)*k-.35,z-.5,.7,.7,1,'bronze','mechanism')
        self.box(x-1,y+r-1,z-3,2,2,6,'bronze','mechanism')
        self.box(x-r-2,y-1,z-1.5,2,r+2,3,'stone','mechanism');self.box(x+r,y-1,z-1.5,2,r+2,3,'stone','mechanism')

    def furnace(self,s):
        cx,y,cz=s['pos'];w,h,d=s['size'];x,z=cx-w/2,cz-d/2
        self.box(x-2,y-3,z-2,w+4,3,d+4,'rock_dark','foundation')
        for dx in (0,w-4):
            for dz in(0,d-4):self.box(x+dx,y,z+dz,4,h*.68,4,'brick','furnace')
        for side in [(x,y,z,w,h*.55,3),(x,y,z+d-3,w,h*.55,3),(x,y,z,3,h*.55,d),(x+w-3,y,z,3,h*.55,d)]:
            xx,yy,zz,ww,hh,dd=side
            if ww>dd:
                self.box(xx,yy,zz,(ww-9)/2,hh,dd,'brick','furnace');self.box(xx+(ww+9)/2,yy,zz,(ww-9)/2,hh,dd,'brick','furnace')
                self.box(cx-4.5,yy+8,zz,9,max(1,hh-8),dd,'brick','furnace')
            else:self.box(xx,yy,zz,ww,hh,dd,'brick','furnace')
        for yy in(y+h*.22,y+h*.5):
            self.box(x-1,yy,z-1,w+2,1.5,d+2,'bronze','furnace')
        for i in range(5):self.box(x+i*2,y+h*.55+i*2,z+i*2,w-i*4,2,d-i*4,'rock_dark','furnace')
        self.box(cx-4,y+h*.55+10,cz-4,8,h*.35,8,'brick','furnace')
        self.box(cx-2,y+.1,cz-2,4,.5,4,'lava','furnace')
        for dx in(-5,5):self.lantern(cx+dx,y+2,z-1)

    def vault(self,s):
        cx,y,cz=s['pos'];w,h,d=s['size'];mat=s.get('material','stone');x,z=cx-w/2,cz-d/2
        self.box(x,y-2,z,w,2,d,'paving','foundation')
        for xx in(x,x+w-2):self.box(xx,y,z,2,h,d,mat,'wall')
        for zz in(z,z+d-2):
            self.box(x,y,zz,w/2-4,h,2,mat,'wall');self.box(cx+4,y,zz,w/2-4,h,2,mat,'wall')
            self.box(cx-4,y+7,zz,8,max(1,h-7),2,mat,'wall')
        for i in range(4):
            self.box(x+i*2,y+h+i*.5,z,w-i*4,1,d,mat,'ceiling')
        self.furniture(s.get('use','shrine'),x+3,y,z+3,w-6,d-6)
        for xx in(x+3,x+w-4):
            for zz in(z+3,z+d-4):self.lantern(xx,y+2,zz)

    def structures(self):
        for s in self.spec['structures']:
            try:FORMS[s['kind']](self,s)
            except KeyError as e:raise ValueError('未注册的构造：'+s['kind'])from e

    def props(self):
        for node in self.spec['nodes']:
            x,y,z=node['pos']
            if node['role']=='mechanism':
                self.box(x+3,y,z-3,2,1.4,2,'stone','mechanism')
                self.box(x+3.5,y+1.4,z-2.5,1,1.8,1,'bronze','mechanism')
            if node['role']=='objective':
                self.box(x+2,y,z+1,1.6,1.2,1.2,'bronze','objective')
                self.box(x+1.9,y+1.2,z+.9,1.8,.3,1.4,'jade','objective')
        for e in self.paths:
            if e['kind']in('bridge','lift'):continue
            for i,p in enumerate(e['points']):
                if i%2==0:self.lantern(p[0]+4,p[1],p[2]+1)


def hip_roof(scene,x,y,z,w,d,damage):
    rows=math.ceil(min(w,d)/2)
    for i in range(rows):
        ww,dd=w-i*2,d-i*2
        if min(ww,dd)<=0:break
        yy=y+i*.55
        # 破损发生在指定侧翼，完整主屋仍保留清楚的檐线。
        ww=ww*(.72 if damage>=2 and i<rows/2 else 1)
        scene.box(x+i,yy,z+i,ww,.7,dd,'tile','roof')
    for xx,zz in[(x,z),(x+w-1,z),(x,z+d-1),(x+w-1,z+d-1)]:
        scene.box(xx-.5,y+.5,zz-.5,2,.7,2,'tile_dark','roof')
        scene.box(xx,y+1.2,zz,1,.7,1,'tile_dark','roof')
    if w>=d:scene.box(x+d/2-1,y+rows*.55,z+d/2-.4,max(2,w-d+2),.8,.8,'tile_dark','roof')
    else:scene.box(x+w/2-.4,y+rows*.55,z+w/2-1,.8,.8,max(2,d-w+2),'tile_dark','roof')


ROOFS={'hip':hip_roof}


def tower(scene,s):
    scene.hall(dict(s,levels=s.get('levels',2),use=s.get('use','tea')))


def gate(scene,s):
    scene.arch(s)
    x,y,z=s['pos'];w,h,d=s['size']
    for dx in(-w/2,w/2-1):scene.box(x+dx,y,z-d,1,h*.55,d*2,'brick','wall')
    for dx in(-w/2-4,w/2+2):
        scene.box(x+dx,y,z-d/2-4,3,1,4,'stone','detail')
        scene.box(x+dx+.3,y+1,z-d/2-3.7,2.4,2.5,3,'rock_dark','detail')
        scene.box(x+dx+.4,y+3.5,z-d/2-4,2.2,1.7,1.8,'stone','detail')
        for ex in(.5,1.9):scene.box(x+dx+ex,y+5,z-d/2-3.8,.5,.6,.6,'stone','detail')


def altar(scene,s):
    x,y,z=s['pos'];w,h,d=s['size']
    for i in range(3):scene.box(x-w/2+i,y-h+i*h/3,z-d/2+i,w-2*i,h/3,d-2*i,'stone','altar')


def lift(scene,s):
    x,y,z=s['pos'];w,h,d=s['size'];scene.frame(s)
    for yy in range(0,int(h),12):
        for xx in(x-w/2,x+w/2-1):scene.box(xx,y+yy,z-d/2,1.2,1,d,'wood','support')
        scene.box(x-w/2,y+yy,z,w,1,1.2,'iron','support')
    for xx in(x-4,x+4):scene.box(xx,y,z-.2,.4,h,.4,'iron','mechanism')
    scene.box(x-4,y,z-4,8,1,8,'floor','mechanism')


def rail(scene,s):
    x,y,z=s['pos'];w,h,d=s['size']
    if s.get('rotation'):
        w,d=d,w
    for xx in(x-w/2+1,x+w/2-1):scene.box(xx,y,z-d/2,.3,.3,d,'iron','rail')
    for zz in range(int(z-d/2),int(z+d/2),3):scene.box(x-w/2,y-.2,zz,w,.3,.6,'wood','rail')


def palisade(scene,s):
    x,y,z=s['pos'];w,h,d=s['size']
    if s.get('rotation'):w,d=d,w
    for xx in range(int(x-w/2),int(x+w/2),2):
        for zz in range(int(z-d/2),int(z+d/2),2):
            if scene.doorway(xx,y,zz):continue
            scene.box(xx,y,zz,1.4,h-(xx+zz)%3,1.4,'darkwood','wall')


def shelves(scene,s):
    x,y,z=s['pos'];w,h,d=s['size']
    for xx in range(int(x-w/2),int(x+w/2)-3,6):
        for zz in range(int(z-d/2),int(z+d/2)-3,8):
            if scene.doorway(xx+2,y,zz):continue
            scene.bookcase(xx,y,zz,4,h,1.5)


def garden(scene,s):
    x,y,z=s['pos'];w,h,d=s['size']
    scene.box(x-w/2,y-.5,z-d/2,w,.5,d,'grass','garden')
    scene.tree(x+min(9,w/3),y,z+min(9,d/3),19)
    for dx,dz in[(-w/2,0),(w/2,0),(0,-d/2),(0,d/2)]:scene.box(x+dx-2,y,z+dz-2,4,1,4,'stone','garden')


def screen(scene,s):
    x,y,z=s['pos'];w,h,d=s['size'];xx,zz=x-w/2,z-d/2
    scene.wall(xx,y,zz,w,h,d)
    for i in range(3):
        scene.box(xx-1+i*.3,y+h+i*.4,zz-1+i*.3,w+2-i*.6,.5,d+2-i*.6,'tile_dark','roof')
    axis=w>d
    for i in range(0,int(w if axis else d),5):
        px=xx+i if axis else xx;pz=zz if axis else zz+i
        if not scene.doorway(px,y,pz):scene.box(px,y,pz,1.2,h+1,1.2,'stone','masonry')


def crucible(scene,s):
    cx,y,cz=s['pos'];w,h,d=s['size'];x,z=cx-w/2,cz-d/2
    scene.box(x-3,y-3,z-3,w+6,3,d+6,'rock_dark','foundation')
    # 四角吊架与分段铜壳围出真实内腔，主炉与两座旁炉使用不同构造。
    for dx in(1,w-4):
        for dz in(1,d-4):
            scene.box(x+dx,y,z+dz,3,h,3,'darkwood','support')
            for yy in(2,h*.5,h-4):scene.box(x+dx-.4,y+yy,z+dz-.4,3.8,1.4,3.8,'bronze','support')
    for row in range(8):
        inset=abs(3-row)*.8;yy=y+5+row*2.8;ww=w-8-inset*2;dd=d-8-inset*2;xx=x+4+inset;zz=z+4+inset
        for bx,bz,bw,bd in[(xx,zz,ww,2),(xx,zz+dd-2,ww,2),(xx,zz,2,dd),(xx+ww-2,zz,2,dd)]:
            if bw>bd and row<3:
                scene.box(bx,yy,bz,(bw-10)/2,2.8,bd,'bronze','furnace');scene.box(bx+(bw+10)/2,yy,bz,(bw-10)/2,2.8,bd,'bronze','furnace')
            else:scene.box(bx,yy,bz,bw,2.8,bd,'jade'if row in(0,7)else'bronze','furnace')
    for yy in(y+h-3,y+h):
        scene.box(x,y+h-3,z,w,1.8,3,'wood','support');scene.box(x,yy,z+d-3,w,1.8,3,'wood','support')
    for xx in(cx-10,cx+10):
        for k in range(14):scene.box(xx,y+28+k*2,cz-.3,.5,1.8,.6,'iron','mechanism')
    scene.box(cx-7,y+3,cz-7,14,.8,14,'lava','furnace')
    scene.box(cx-3,y+29,cz-3,6,2,6,'rock_dark','furnace')
    for xx in(cx-w/2+6,cx+w/2-6):scene.lantern(xx,y+2,z-1)


def kiln(scene,s):
    cx,y,cz=s['pos'];w,h,d=s['size'];x,z=cx-w/2,cz-d/2
    scene.box(x-2,y-2,z-2,w+4,2,d+4,'rock_dark','foundation')
    for i in range(10):
        inset=abs(i-5)*.7
        for zz in(z,z+d-3):
            scene.box(x+inset,y+i*1.6,zz,w-inset*2,1.6,3,'brick','furnace')
        for xx in(x+inset,x+w-inset-3):scene.box(xx,y+i*1.6,z,3,1.6,d,'brick','furnace')
    for xx in(cx-7,cx+5):
        scene.box(xx,y,z-2,2,8,2,'stone','masonry')
    scene.box(cx-5,y+7,z-2,10,2,2,'bronze','masonry')
    scene.box(cx-4,y+16,cz-4,8,h-16,8,'brick','furnace')
    scene.box(cx-3,y+.2,z-1,6,.6,4,'lava','furnace')


FORMS={
    'hall':Scene.hall,'tower':tower,'ruin':Scene.hall,'gate':gate,'arch':Scene.arch,
    'frame':Scene.frame,'wheel':Scene.wheel,'furnace':Scene.furnace,'vault':Scene.vault,'cave':Scene.vault,
    'crucible':crucible,'kiln':kiln,'screen':screen,
    'lift':lift,'rail':rail,'palisade':palisade,'altar':altar,'shelves':shelves,'garden':garden,
    'winch':lambda scene,s:scene.wheel(s),'sluice':lambda scene,s:scene.wheel(s),
    'dock':lambda scene,s:scene.box(s['pos'][0]-s['size'][0]/2,s['pos'][1]-1,s['pos'][2]-s['size'][2]/2,s['size'][0],1,s['size'][2],'floor','path'),
    'marker':lambda scene,s:scene.box(s['pos'][0]+2,s['pos'][1],s['pos'][2]+2,2,2,1,'stone','detail'),
}


def build(ident):
    catalog=read('catalog');spec=read(ident);checks=validate(spec,catalog)
    scene_type=Scene
    if 'construction' in spec:
        from .combat import SCENES
        scene_type=SCENES[spec['construction']]
    scene=scene_type(spec,catalog)
    scene.terrain();scene.paths_geometry();scene.structures();scene.trees();scene.props()
    if hasattr(scene,'compact'):scene.compact()
    standing=standing_issues(spec,scene.boxes,catalog);cameras=camera_issues(spec,scene.boxes)
    if standing or cameras:raise ValueError(str(dict(map=ident,standing=standing,cameras=cameras)))
    checks.update(boxes=len(scene.boxes),trees=scene.tree_count,layers=dict(scene.details))
    checks.update(standing_points_checked=len(spec['nodes']),fixed_cameras_checked=len(spec['cameras'])-1)
    return dict(spec=spec,boxes=scene.boxes,materials=scene.materials,routes=scene.paths,checks=checks)
