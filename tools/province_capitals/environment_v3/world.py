"""有界的正式方块施工；旧模板只读，新模板独立写入 v3。"""
from __future__ import annotations

from collections import defaultdict, deque
import gzip
import math
from pathlib import Path
import struct

from buildgen.nbtread import read_gzipped_nbt, state_string
from province_capitals.game.compile import resource_file

AIR = 'minecraft:air'


class World:
    def __init__(self, root, manifest, config):
        self.root, self.manifest, self.config = Path(root), manifest, config
        self.width, self.height, self.depth = manifest['size']
        raw = gzip.decompress(resource_file(root, manifest['terrain']['resource']).read_bytes())
        self.terrain = list(struct.unpack('<'+'h'*(self.width*self.depth), raw))
        self.refs = {(t['x']//16,t['z']//16):t for t in manifest['tiles']}
        self.cache = {}
        self.originals = {}
        self.nbt_tiles = set()
        self.dirty = set()
        self.changes = defaultdict(int)
        self.edit_source = {}
        self.protected = {}
        self.protected_walkpoints = {}
        self.decorations = []

    def ground(self, x, z):
        return self.terrain[z*self.width+x]

    def tile(self, key):
        if key not in self.cache:
            cells = {}
            if key in self.refs:
                _, data = read_gzipped_nbt(str(resource_file(self.root,self.refs[key]['template'],template=True)))
                if data.get('entities') or any(b.get('nbt') for b in data['blocks']):
                    self.nbt_tiles.add(key)
                palette = [state_string(p) for p in data['palette']]
                cells = {tuple(b['pos']):palette[b['state']] for b in data['blocks']}
            self.cache[key] = cells
            self.originals[key] = dict(cells)
        return self.cache[key]

    def get(self, p):
        x,y,z = p
        if not (0<=x<self.width and 0<=z<self.depth and 0<=y<self.height):
            return AIR
        found = self.tile((x//16,z//16)).get((x%16,y,z%16))
        if found is not None:
            return found
        top = self.ground(x,z)
        if y > top:
            return AIR
        return self.manifest['terrain']['top_block'] if y == top else self.manifest['terrain']['fill_block']

    def set(self, p, state, reason, *, authority=False):
        x,y,z = p
        if any(type(n) is not int for n in p) or not (0<=x<self.width and 0<=z<self.depth and 0<=y<self.height):
            raise ValueError(f'正式施工越界 {reason} {p}')
        if p in self.protected and state != self.get(p) and not authority:
            raise ValueError(f'施工覆盖受保护通路或岗位：{reason} {p} {state} / {self.protected[p]}')
        self.tile((x//16,z//16))[x%16,y,z%16] = state
        self.edit_source[p]=reason
        self.dirty.add((x//16,z//16)); self.changes[reason] += 1

    def box(self, low, high, state, reason, *, authority=False):
        for y in range(low[1],high[1]+1):
            for z in range(low[2],high[2]+1):
                for x in range(low[0],high[0]+1):
                    self.set((x,y,z),state,reason,authority=authority)

    def protect_life(self):
        for point in self.manifest['life']['places']:
            x,y,z=point['pos']
            for dy in range(self.config['collision']['headroom']):
                self.protected[x,y+dy,z]='life:'+point['id']
            if point['block']:
                self.protected[tuple(point['block_pos'])]='fixture:'+point['id']
        for entry in self.manifest['entries']:
            x,y,z=entry['position']
            for dy in range(self.config['collision']['headroom']):
                self.protected[x,y+dy,z]='entry:'+entry['id']

    def protect_routes(self, routes, exclude=()):
        for route in routes:
            if route['id'] in exclude:
                continue
            points=route['points']
            width=route.get('width',1)
            if type(width) is not int or width < 1:
                raise ValueError('通路宽度必须为正整数：'+route['id'])
            for a,b in zip(points,points[1:]):
                length=max(abs(b[0]-a[0]),abs(b[2]-a[2]),1)
                major_x=abs(b[0]-a[0])>=abs(b[2]-a[2])
                for n in range(length+1):
                    x,y,z=[round(a[j]+(b[j]-a[j])*n/length) for j in range(3)]
                    # 与 owning v2 的道路横向展开一致，包含偶数宽度与转角。
                    for cross in range(-(width//2),width-width//2):
                        xx,zz=(x,z+cross) if major_x else (x+cross,z)
                        if not(0<=xx<self.width and 0<=zz<self.depth):continue
                        if self.walkable((xx,y,zz)):
                            self.protected_walkpoints[xx,y,zz]='route:'+route['id']
                        # 原有精确台阶保持；只保留已存在的身体/头部空气。
                        for dy in range(self.config['collision']['headroom']):
                            if self.get((xx,y+dy,zz)) == AIR:
                                self.protected[xx,y+dy,zz]='route:'+route['id']

    def walkable(self, p):
        x,y,z=p
        return self.get((x,y-1,z)) not in (AIR,'minecraft:water','minecraft:lava') and all(self.passable(self.get((x,y+dy,z))) for dy in range(2))

    @staticmethod
    def passable(state):
        return state == AIR or state.split('[',1)[0] in {'minecraft:short_grass','minecraft:tall_grass','minecraft:fern','minecraft:dead_bush'}

    def verify_life(self):
        for point,reason in self.protected_walkpoints.items():
            if not self.walkable(point):raise ValueError(f'原有通路失去支撑或净空：{reason} {point}')
        for point in self.manifest['life']['places']:
            if not self.walkable(tuple(point['pos'])):
                raise ValueError(f'最终岗位缺支撑或净空：{point["id"]} {point["pos"]}')
            if point['block'] and self.get(tuple(point['block_pos'])).split('[',1)[0] != point['block']:
                raise ValueError('最终功能方块与岗位绑定不一致：'+point['id'])
        for entry in self.manifest['entries']:
            if not self.walkable(tuple(entry['position'])):
                raise ValueError('最终城门入口不可站立：'+entry['id'])

    def verify_retained_buildings(self):
        errors=[]
        for b in self.manifest.get('buildings',[]):
            lo,hi=b['bounds']['min'],b['bounds']['max']; floor=b['position'][1]
            for tx in range(lo[0]//16,hi[0]//16+1):
                for tz in range(lo[2]//16,hi[2]//16+1):
                    if (tx,tz) not in self.dirty:continue
                    for (lx,y,lz),old in self.originals[tx,tz].items():
                        x,z=tx*16+lx,tz*16+lz
                        if not(lo[0]<=x<=hi[0] and lo[2]<=z<=hi[2] and floor<=y<=hi[1]):continue
                        if old==AIR or 'leaves' in old or old.startswith('minecraft:water'):continue
                        if self.get((x,y,z))!=old:
                            if len(errors)<16:errors.append(f'{b["id"]} {(x,y,z)} {old} -> {self.get((x,y,z))} by {self.edit_source.get((x,y,z))}')
        if errors:raise ValueError('施工改写了保留建筑：'+'; '.join(errors))

    def surface(self, x, y, z, material, reason, width=1, axis=None):
        wx=1 if axis=='x' else width
        wz=1 if axis=='z' else width
        for xx in range(x-wx//2,x-wx//2+wx):
            for zz in range(z-wz//2,z-wz//2+wz):
                ground=self.ground(xx,zz)
                for yy in range(min(ground+1,y-1),y):
                    self.set((xx,yy,zz),material,reason)
                for dy in range(self.config['collision']['clearance_above']):
                    self.set((xx,y+dy,zz),AIR,reason)

    def corridor(self, points, width, material, reason):
        route=[]
        for a,b in zip(points,points[1:]):
            if a[0]!=b[0] and a[2]!=b[2]:raise ValueError('接路必须正交：'+reason)
            axis='x' if a[0]!=b[0] else 'z'
            length=max(abs(b[0]-a[0]),abs(b[2]-a[2]),1)
            if abs(b[1]-a[1])>length:
                raise ValueError('明确接路的高差超过逐格步进：'+reason)
            for n in range(length+1):
                p=tuple(round(a[j]+(b[j]-a[j])*n/length) for j in range(3))
                self.surface(*p,material,reason,width,axis); route.append(p)
        for a,b in zip(route,route[1:]):
            if b[1]<=a[1]:continue
            stair=self.config['stairs'].get(material)
            if stair is None:raise ValueError('坡道材质没有明确台阶映射：'+material)
            dx,dz=b[0]-a[0],b[2]-a[2]
            facing='east' if dx>0 else 'west' if dx<0 else 'south' if dz>0 else 'north'
            for offset in range(-(width//2),width-width//2):
                x,z=(b[0],b[2]+offset) if dx else (b[0]+offset,b[2])
                self.set((x,b[1]-1,z),stair+f'[facing={facing},half=bottom,shape=straight,waterlogged=false]',reason+'_台阶')
        if not all(self.walkable(p) for p in route):
            raise ValueError('施工后接路未保持支撑与净空：'+reason)
        for x,y,z in route:
            for dy in range(2):self.protected[x,y+dy,z]=reason
        return route


def raster(world, boxes, palette, tag, *, allow_ground=False):
    """设计体素到实际方块的外包络；薄铺地落回地表，不生成半悬空地板。"""
    before=sum(world.changes.values())
    for b in boxes:
        lo=[b['p'][i]-b['s'][i]/2 for i in range(3)]
        hi=[b['p'][i]+b['s'][i]/2 for i in range(3)]
        state=palette[b['m']]
        if b['s'][1] <= .25 and b['s'][0] >= 1 and b['s'][2] >= 1:
            yy=round(hi[1])-1
            for z in range(math.floor(lo[2]),math.ceil(hi[2])):
                for x in range(math.floor(lo[0]),math.ceil(hi[0])):
                    world.set((x,yy,z),state,tag)
            continue
        for y in range(math.floor(lo[1]+1e-6),math.ceil(hi[1]-1e-6)):
            for z in range(math.floor(lo[2]+1e-6),math.ceil(hi[2]-1e-6)):
                for x in range(math.floor(lo[0]+1e-6),math.ceil(hi[0]-1e-6)):
                    world.set((x,y,z),state,tag)
    if sum(world.changes.values()) == before:
        raise ValueError('声明的装饰未生成任何方块：'+tag)
