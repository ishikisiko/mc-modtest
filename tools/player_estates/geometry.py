"""按 authored 院落与建筑层级生成原版方块，所有预览与导出共用结果。"""
from __future__ import annotations

from collections import deque
import math

AIR = 'minecraft:air'


def state(name, **props):
    if ':' not in name:
        name = 'minecraft:' + name
    return name + ('[' + ','.join(f'{k}={str(v).lower()}' for k, v in sorted(props.items())) + ']' if props else '')


class Estate:
    def __init__(self, config):
        self.c = config
        self.blocks = {}
        self.layers = {}
        self.targets = {}
        self.rooms = []

    def put(self, x, y, z, block, layer='architecture'):
        p = (int(x), int(y), int(z))
        if not all(0 <= p[i] < self.c['size'][i] for i in range(3)):
            raise ValueError(f'方块越界: {p} {block}')
        if block is None or block == AIR:
            self.blocks.pop(p, None)
            self.layers.pop(p, None)
        else:
            self.blocks[p] = self.c['palette'].get(block, block)
            self.layers[p] = layer

    def box(self, x0, y0, z0, x1, y1, z1, block, layer='architecture'):
        for y in range(y0, y1):
            for z in range(z0, z1):
                for x in range(x0, x1):
                    self.put(x, y, z, block, layer)

    def post(self, x, z, f, h):
        self.put(x, f, z, 'stone')
        for y in range(f + 1, h):
            self.put(x, y, z, state(self.c['palette']['post'], axis='y'))
        self.put(x, h, z, 'beam')

    def roof(self, rect, eave, rise, axis='x', above=None, gable=False):
        x0, z0, x1, z1 = rect
        cx, cz = (x0 + x1 - 1) / 2, (z0 + z1 - 1) / 2
        hw, hd = (x1 - x0 - 1) / 2, (z1 - z0 - 1) / 2
        profile = {}
        for z in range(z0, z1):
            for x in range(x0, x1):
                across = abs(z - cz) if axis == 'x' else abs(x - cx)
                half = hd if axis == 'x' else hw
                along = abs(x - cx) if axis == 'x' else abs(z - cz)
                long_half = hw if axis == 'x' else hd
                edge_distance = max(across, along - max(0, long_half - half)) if not gable else across
                t = min(1, edge_distance / max(half, 1))
                h = eave + round(rise * (1 - t) ** 1.08 + self.c['roof']['kick'] * t ** 10)
                corner_distance=min(x-x0,x1-1-x)+min(z-z0,z1-1-z)
                if corner_distance<2:h+=2-corner_distance
                profile[x, z] = h
                if above and above[0] <= x < above[2] and above[1] <= z < above[3] and h >= above[4]:
                    continue
                edge = x in (x0, x1 - 1) or z in (z0, z1 - 1)
                # Thin eaves and real stair/slab roof surfaces, with a light fascia.
                if axis == 'x':
                    facing = 'south' if z < cz else 'north'
                else:
                    facing = 'east' if x < cx else 'west'
                if not gable and along - max(0, long_half - half) > across:
                    facing = ('east' if x < cx else 'west') if axis == 'x' else ('south' if z < cz else 'north')
                if across < .75 and (gable or along <= max(0, long_half - half)):
                    block = state(self.c['palette']['trim_slab'], type='bottom', waterlogged=False)
                    self.put(x, h, z, 'tile', 'roof')
                    self.put(x, h + 1, z, block, 'roof')
                else:
                    block = state(self.c['palette']['trim_stair'] if edge else self.c['palette']['tile_stair'], facing=facing, half='bottom', shape='straight', waterlogged=False)
                    self.put(x, h, z, block, 'roof')
                self.put(x, h - 1, z, state(self.c['palette']['trim_slab'] if edge else 'minecraft:spruce_slab', type='top', waterlogged=False), 'roof')
                if edge:
                    self.put(x, h - 2, z, state('smooth_quartz_slab', type='top', waterlogged=False), 'roof')
        if gable:
            # The front triangular plaster field and projecting stone verge.
            for z in (z0, z1 - 1):
                for x in range(x0, x1):
                    h = profile[x, z]
                    for y in range(eave - 1, h):
                        if above and above[0] <= x < above[2] and above[1] <= z < above[3] and y >= above[4]:
                            continue
                        mat = 'gable' if y >= h - 2 else 'inset'
                        self.put(x, y, z, mat, 'roof')
                    if not (above and above[0] <= x < above[2] and above[1] <= z < above[3] and h >= above[4]):
                        self.put(x, h, z, state(self.c['palette']['trim_stair'], facing='east' if x < cx else 'west', half='bottom', shape='straight', waterlogged=False), 'roof')
            for z in (z0 - 1, z1):
                if 0 <= z < self.c['size'][2]:
                    h = eave + rise
                    if not (above and above[0] <= round(cx) < above[2] and above[1] <= z < above[3] and h+1 >= above[4]):
                        self.put(round(cx), h + 1, z, state(self.c['palette']['trim_stair'], facing='south' if z == z0 - 1 else 'north', half='bottom', shape='straight', waterlogged=False), 'roof')

    def window(self, x, f, z, facing):
        dx, dz = {'north': (1, 0), 'south': (1, 0), 'east': (0, 1), 'west': (0, 1)}[facing]
        ox, oz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
        for w in (-1, 0):
            for y in range(f + 2, f + 5):
                self.put(x + dx*w, y, z + dz*w, state('glass_pane', north=dx == 0, south=dx == 0, east=dz == 0, west=dz == 0, waterlogged=False))
        for w in (-2, 1):
            for y in (f + 2, f + 3):
                self.put(x + dx*w + ox, y, z + dz*w + oz, state('spruce_trapdoor', facing=facing, half='bottom', open=True, powered=False, waterlogged=False))
        for w in (-1, 0):
            self.put(x + dx*w + ox, f + 1, z + dz*w + oz, state('grass_block',snowy=False), 'garden')
            self.put(x + dx*w + 2*ox, f + 1, z + dz*w + 2*oz, state('oak_trapdoor', facing=facing, half='bottom', open=True, powered=False, waterlogged=False))
            self.put(x + dx*w + ox, f + 2, z + dz*w + oz, state('sweet_berry_bush', age=3), 'garden')

    def portal(self, rect, f, side, width=3):
        x0, z0, x1, z1 = rect
        if side in ('north', 'south'):
            c = (x0 + x1) // 2
            z = z0 if side == 'north' else z1 - 1
            self.box(c-width//2, f+1, z-2, c+width//2+1, f+5, z+2, None)
            self.box(c-width//2, f, z-2, c+width//2+1, f+1, z+3, 'floor', 'floor')
            self.targets.setdefault('portals', []).append((c, f+1, z+(-1 if side == 'north' else 1)))
        else:
            c = (z0 + z1) // 2
            x = x0 if side == 'west' else x1 - 1
            self.box(x-2, f+1, c-width//2, x+2, f+5, c+width//2+1, None)
            self.box(x-2, f, c-width//2, x+3, f+1, c+width//2+1, 'floor', 'floor')

    def tier(self, b, level):
        inset = level * 2
        a, c, d, e = b['rect']
        r = x0, z0, x1, z1 = a+inset, c+inset, d-inset, e-inset
        f = b['floor'] + b['step'] * level
        top = f + self.c['facade']['wall_height']
        above = (x0+2, z0+2, x1-2, z1-2, f+b['step']) if level + 1 < b['levels'] else None
        self.box(x0, f, z0, x1, f+1, z1, 'floor', 'floor')
        if not level:
            self.box(x0, 3, z0, x1, f, z1, 'stone', 'foundation')
        for y in range(f+1, top):
            for x in range(x0, x1):
                for z in (z0, z1-1):
                    self.put(x, y, z, 'wall')
            for z in range(z0+1, z1-1):
                for x in (x0, x1-1):
                    self.put(x, y, z, 'wall')
        for z in (z0, z1-1):
            for x in range(x0, x1):
                self.put(x, top-1, z, state(self.c['palette']['beam'], axis='x'))
            posts = sorted(set([x0, x1-1] + list(range(x0, x1, self.c['facade']['bay']))))
            for x in posts:
                self.post(x, z, f, top-1)
                if x+2 < x1-1:
                    self.window(x+3, f, z, 'north' if z == z0 else 'south')
                if x not in (x0, x1-1):
                    self.put(x+1, top-2, z+(-1 if z == z0 else 1), state('lantern', hanging=True, waterlogged=False), 'lights')
                    self.put(x+1, top-1, z+(-1 if z == z0 else 1), state('spruce_trapdoor', facing='north', half='top', open=False, powered=False, waterlogged=False))
        for x in (x0, x1-1):
            for z in range(z0, z1, self.c['facade']['bay']):
                self.post(x, z, f, top-1)
                if z+3 < z1-2:
                    self.window(x, f, z+3, 'west' if x == x0 else 'east')
            for z in range(z0, z1):
                self.put(x, top-1, z, state(self.c['palette']['beam'], axis='z'))
        # The porch lies under the overhanging roof, outside the enclosed room.
        self.box(x0-1, f, z0-2, x1+1, f+1, z0, 'floor', 'floor')
        for x in range(x0, x1, 5):
            self.post(x, z0-2, f, top-1)
        if b['role']=='gallery':
            for x in range(x0+1,x1-1):
                if (x-x0)%self.c['facade']['bay']:
                    for y in range(f+2,top-1):self.put(x,y,z0,'red')
        self.portal(r, f, 'north')
        if not level:
            self.portal(r, f, 'south')
            self.portal(r, f, 'east')
            self.portal(r, f, 'west')
            self.stairs_to_ground((x0+x1)//2, z0-3, f)
        overhang = self.c['roof']['overhang']
        self.roof((x0-overhang, z0-overhang, x1+overhang, z1+overhang), top, b['rise'], above=above)
        if b['cross']:
            cx = (x0+x1)//2
            w = min(self.c['roof']['cross_width'], x1-x0-4)
            self.roof((cx-w//2, z0-4, cx+w//2+1, z0+self.c['roof']['cross_depth']), top, min(b['rise']-1, w//2+1), axis='z', gable=True, above=above)
        if not level:
            self.furnish(b, r, f)
        else:
            self.furnish(b, r, f, upper=True)
        self.rooms.append({'building':b['id'], 'level':level+1, 'rect':list(r), 'floor':f})
        self.targets[b['id']+f'_{level+1}'] = ((x0+x1)//2, f+1, z0+3)

    def stairs_to_ground(self, x, z, floor):
        for i in range(floor-2):
            y = floor-i
            self.box(x-2, 3, z-i, x+3, y+1, z-i+1, 'stone', 'floor')
            for xx in range(x-2, x+3):
                self.put(xx, y, z-i, state('stone_brick_stairs', facing='south', half='bottom', shape='straight', waterlogged=False), 'floor')
        self.box(x-2, 3, z+1, x+3, floor+1, z+2, 'stone', 'floor')

    def interior_stairs(self, b):
        # Two real flights in the common interior of all storeys.
        max_inset = 2*(b['levels']-1)
        x1 = b['rect'][2] - max_inset
        z1 = b['rect'][3] - max_inset
        x, z = x1-9, z1-9
        rise = b['step']; n = rise//2
        for level in range(b['levels']-1):
            f = b['floor'] + rise*level
            self.box(x-1, f+1, z-1, x+6, f+rise+4, z+n+2, None)
            self.box(x, f, z-2, x+2, f+1, z, 'floor', 'floor')
            for i in range(n):
                for xx in (x, x+1):
                    self.put(xx, f+i, z+i, 'wood', 'floor')
                    self.put(xx, f+i+1, z+i, state('oak_stairs', facing='south', half='bottom', shape='straight', waterlogged=False), 'floor')
            self.box(x, f+n, z+n, x+5, f+n+1, z+n+2, 'floor', 'floor')
            for i in range(rise-n):
                for xx in (x+3, x+4):
                    self.put(xx, f+n+i, z+n-1-i, 'wood', 'floor')
                    self.put(xx, f+n+i+1, z+n-1-i, state('oak_stairs', facing='north', half='bottom', shape='straight', waterlogged=False), 'floor')
            # An odd rise has one more returning tread. Keep its final stair
            # at z-1 instead of replacing it with the landing's full block.
            landing_shift=rise%2
            self.box(x+3, f+rise, z-2-landing_shift, x+5, f+rise+1, z-landing_shift, 'floor', 'floor')

    def furnish(self, b, r, f, upper=False):
        x0, z0, x1, z1 = r
        if b['role'] == 'gate':
            cx=(x0+x1)//2
            for x in (x0+2, x1-3):
                self.put(x, f+1, z0+3, state('barrel', facing='up', open=False), 'interior')
            return
        if upper or b['role'] == 'guest':
            for x in (x0+3, x0+7):
                if x+1 >= x1-10:
                    continue
                for part,dz in [('foot',0),('head',1)]:
                    self.put(x, f+1, z1-4+dz, state('red_bed', facing='south', part=part, occupied=False), 'interior')
                self.put(x+1, f+1, z1-3, state('barrel', facing='up', open=False), 'interior')
        if b['role'] == 'library':
            for x in range(x0+2, x1-11):
                self.box(x, f+1, z1-3, x+1, f+4, z1-2, 'minecraft:bookshelf', 'interior')
        elif b['role'] == 'workshop':
            for i, block in enumerate(['minecraft:crafting_table',state('furnace', facing='north', lit=False),state('blast_furnace', facing='north', lit=False),'minecraft:smithing_table',state('anvil', facing='north')]):
                self.put(x0+2+i, f+1, z1-3, block, 'interior')
        if b['role'] in ('main','guest','library'):
            cx=(x0+x1)//2-3;cz=(z0+z1)//2
            self.box(cx-2, f+1, cz, cx+3, f+2, cz+2, state('spruce_slab',type='top',waterlogged=False), 'interior')
            for xx in range(cx-2,cx+3,2):
                self.put(xx,f+1,cz-1,state('oak_stairs',facing='south',half='bottom',shape='straight',waterlogged=False),'interior')
                self.put(xx,f+1,cz+2,state('oak_stairs',facing='north',half='bottom',shape='straight',waterlogged=False),'interior')
        self.put(x0+2,f+1,z0+3,state('lantern',hanging=False,waterlogged=False),'lights')

    def corridor(self, c):
        x0,z0,x1,z1=c['rect'];f=c['floor']
        self.box(x0,3,z0,x1,f+1,z1,'floor','floor')
        axis=c['axis']
        if axis=='z':
            for z in range(z0,z1,5):
                for x in (x0,x1-1):self.post(x,z,f,f+5)
        else:
            for x in range(x0,x1,5):
                for z in (z0,z1-1):self.post(x,z,f,f+5)
        self.roof((x0-1,z0-1,x1+1,z1+1),f+5,3,axis=axis)

    def garden(self):
        W,_,D=self.c['size']
        # Main formal route and secondary paths stay at the natural yard level.
        paths=[(46,24,52,58),(42,55,47,76),(51,55,56,76),(43,73,55,78),
               (25,42,73,47),(27,73,71,78),(28,44,32,78),(66,44,70,78)]
        for x0,z0,x1,z1 in paths:
            for x in range(x0,x1):
                for z in range(z0,z1):
                    self.put(x,2,z,'paving','garden')
        cx,cz=self.c['pond']['center'];rx,rz=self.c['pond']['radius']
        for x in range(cx-rx-1,cx+rx+2):
            for z in range(cz-rz-1,cz+rz+2):
                d=((x-cx)/rx)**2+((z-cz)/rz)**2
                if d<1:
                    self.put(x,1,z,'minecraft:clay','garden');self.put(x,2,z,state('water',level=0),'water')
                elif d<1.24:self.put(x,2,z,'minecraft:sandstone','garden')
        bz=self.c['pond']['bridge_z']
        self.box(cx-rx-1,3,bz-1,cx+rx+2,4,bz+2,'wood','floor')
        for x in range(cx-rx-1,cx+rx+2):
            for z in (bz-2,bz+2):
                self.put(x,3,z,state('spruce_trapdoor',facing='north' if z<bz else 'south',open=True,half='bottom',powered=False,waterlogged=False),'garden')
        x0,z0,x1,z1=self.c['water_garden']['rect']
        for x in range(x0-1,x1+1):
            for z in range(z0,z1):
                self.put(x,2,z,'stone','garden')
                if x0<=x<x1:self.put(x,2,z,state('water',level=0),'water')
        for x in range(x0-1,x1+1):
            for z in range(z0-1,z1+1):
                if x in (x0-1,x1) or z in (z0-1,z1):self.put(x,3,z,'stone','garden')
        mid=(x0+x1)//2
        self.box(mid-1,3,z1-2,mid+2,7,z1,'stone','garden')
        for y in range(3,7):
            self.put(mid-1,y,z1-3,'stone','garden');self.put(mid+1,y,z1-3,'stone','garden')
        self.put(mid,6,z1-2,state('water',level=0),'water')
        for y in range(3,7):self.put(mid,y,z1-3,state('water',level=8),'water')
        x0,z0,x1,z1=self.c['crop_garden']['rect']
        for x in range(x0-1,x1+1):
            for z in range(z0-1,z1+1):
                if x in (x0-1,x1) or z in (z0-1,z1):
                    self.put(x,3,z,state('cobblestone_slab',type='bottom',waterlogged=False),'garden')
                else:
                    self.put(x,2,z,state('water',level=0) if x==(x0+x1)//2 else state('farmland',moisture=7),'garden')
                    if x!=(x0+x1)//2:self.put(x,3,z,state('wheat',age=7),'garden')
        for i,(x,z,h) in enumerate(self.c['trees']):
            for y in range(3,3+h):self.put(x,y,z,state('oak_log',axis='y'),'vegetation')
            for dx in range(-3,4):
                for dz in range(-3,4):
                    for dy in range(-2,2):
                        if dx*dx+dz*dz+dy*dy*2>12:continue
                        p=(x+dx,3+h+dy,z+dz)
                        if p not in self.blocks:self.put(*p,state('oak_leaves',distance=1,persistent=True,waterlogged=False),'vegetation')
        for x,z in [(43,29),(56,29),(43,48),(57,48),(30,70),(67,70)]:
            self.put(x,3,z,state('oak_fence',east=False,west=False,north=False,south=False,waterlogged=False),'garden')
            self.put(x,4,z,state('lantern',hanging=False,waterlogged=False),'lights')

    def build(self):
        W,H,D=self.c['size']
        self.box(0,0,0,W,1,D,'minecraft:stone','foundation')
        self.box(0,1,0,W,2,D,'minecraft:dirt','foundation')
        self.box(0,2,0,W,3,D,state('grass_block',snowy=False),'garden')
        for c in self.c['corridors']:self.corridor(c)
        for b in self.c['buildings']:
            for level in range(b['levels']):self.tier(b,level)
        # Open all linked facades after furnishing and roofs; never erase a whole room.
        for b in self.c['buildings']:
            if b['levels']>1:self.interior_stairs(b)
        g=next(b for b in self.c['buildings'] if b['id']=='gate');cx=49
        self.box(cx-2,5,3,cx+3,9,27,None)
        self.box(cx-2,4,7,cx+3,5,27,'floor','floor')
        # The yard has ground-to-porch access on every side of each pavilion.
        for b in self.c['buildings']:
            x0,z0,x1,z1=b['rect'];f=b['floor'];cz=(z0+z1)//2
            for x,side in [(x0-1,'east'),(x1,'west')]:
                for i in range(f-2):
                    xx=x-i if side=='east' else x+i
                    self.box(xx,3,cz-1,xx+1,f-i+1,cz+2,'stone','floor')
                    for z in range(cz-1,cz+2):self.put(xx,f-i,z,state('stone_brick_stairs',facing=side,half='bottom',shape='straight',waterlogged=False),'floor')
            for i in range(f-2):
                y=f-i;zz=z1+i
                self.box((x0+x1)//2-1,3,zz,(x0+x1)//2+2,y+1,zz+1,'stone','floor')
        # The gate passage extends beyond its facade porch. Its last tread must
        # meet the lower yard instead of leaving a two-block drop at z=27.
        for x in range(cx-2,cx+3):
            self.put(x,3,27,state('stone_brick_stairs',facing='north',half='bottom',shape='straight',waterlogged=False),'floor')
        self.garden()
        return self


def walkable_blocks(e):
    """Conservative full-block grid: stairs count as one block, doors stay passable."""
    passable=('air','water','glass_pane','sweet_berry_bush','wheat','lantern','torch','red_bed')
    def empty(p):
        name=e.blocks.get(p,AIR).split('[')[0].split(':')[1]
        return name in passable
    result=set()
    for (x,y,z),s in e.blocks.items():
        name=s.split('[')[0].split(':')[1]
        if name in passable or name.endswith(('leaves','trapdoor','fence')):continue
        p=(x,y+1,z)
        if empty(p) and empty((x,y+2,z)):result.add(p)
    return result


def connectivity(e):
    nodes=walkable_blocks(e);start=(49,3,1)
    if start not in nodes:raise ValueError('入口缺少安全站位')
    seen={start};q=deque([start])
    while q:
        x,y,z=q.popleft()
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            for dy in (-1,0,1):
                p=(x+dx,y+dy,z+dz)
                if p in nodes and p not in seen:seen.add(p);q.append(p)
    targets={k:list(p) for k,p in e.targets.items() if k!='portals'}
    failed={k:p for k,p in targets.items() if tuple(p) not in seen}
    return {'reachable_cells':len(seen),'targets':targets,'failed':failed,'all_reachable':not failed}
