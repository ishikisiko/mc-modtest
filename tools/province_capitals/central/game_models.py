"""中州正式方块建筑。仅建立单栋模板；屋顶调用现有 buildgen 注册表。"""
from __future__ import annotations

import copy
from dataclasses import dataclass, field
import json
import math
from pathlib import Path
import random

from buildgen.grid import BlockGrid
from buildgen.massing import Node
from buildgen.modset import load_modset
from buildgen.ops import roof_handler
from buildgen.style import Style

AIR = 'minecraft:air'


@dataclass
class Model:
    cells: dict = field(default_factory=dict)
    nodes: list = field(default_factory=list)
    places: list = field(default_factory=list)
    doors: list = field(default_factory=list)
    signs: dict = field(default_factory=dict)
    floor_footprint: set = field(default_factory=set)
    entry: tuple = (0, 1, 0)
    floors: int = 1
    roof_type: str = ''

    def put(self, x, y, z, state):
        self.cells[int(x), int(y), int(z)] = state

    def box(self, x0, y0, z0, x1, y1, z1, state):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    self.put(x, y, z, state)

    def place(self, place_id, kind, pos, block='', block_pos=None, capacity=1):
        self.places.append({'id': place_id, 'kind': kind, 'pos': list(pos),
                            'capacity': capacity, 'block': block,
                            'block_pos': list(block_pos or (0, 0, 0))})

    @property
    def top(self):
        return max(y for x, y, z in self.cells)


def rotate_point(p, quarters):
    x, y, z = p
    for _ in range(quarters % 4):
        x, z = -z, x
    return x, y, z


def rotate_state(state, quarters):
    if '[' not in state or not quarters:
        return state
    name, raw = state[:-1].split('[', 1)
    props = dict(item.split('=') for item in raw.split(','))
    faces = ['east', 'south', 'west', 'north']
    if props.get('facing') in faces:
        props['facing'] = faces[(faces.index(props['facing']) + quarters) % 4]
    if quarters % 2 and props.get('axis') in ('x', 'z'):
        props['axis'] = 'z' if props['axis'] == 'x' else 'x'
    for key in ('north', 'east', 'south', 'west'):
        if key in props:
            value = props.pop(key)
            props['_'+faces[(faces.index(key)+quarters) % 4]] = value
    props = {k.lstrip('_'): v for k, v in props.items()}
    if 'rotation' in props:
        props['rotation'] = str((int(props['rotation']) + quarters * 4) % 16)
    return name + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'


class Factory:
    def __init__(self, root: Path, spec: dict):
        self.root, self.spec = root, spec
        self.materials, self.rules = spec['materials'], spec['templates']
        self.modset = load_modset(self.materials['modset'])
        self.styles = {}
        # Use the existing modset/catalogue source for slot resolution, without
        # invoking a validator or a test command as part of this export.
        for key in ('style', 'monumental_style'):
            data = json.loads((root/'tools/buildgen/styles'/f'{self.materials[key]}.json').read_text())
            data['material_slots'] = {slot: [s for s in values if s.split(':')[0] in self.modset.namespaces]
                                      for slot, values in data['material_slots'].items()}
            self.styles[key] = data
        self.cache = {}

    def style(self, color='grey', wall='hui', monumental=False):
        data = copy.deepcopy(self.styles['monumental_style' if monumental else 'style'])
        base = self.materials['roof'][color]
        data['material_slots']['ROOF_DARK'] = [base+'_stairs', base+'_slab', 'minecraft:dark_oak_planks']
        # Vanilla roof geometry and colour are resolved through the owning slots.
        data['material_slots']['ROOF_TILE'] = []
        data['material_slots']['WALL_MAIN'] = [self.materials['wall'][wall]]
        data['material_slots']['GABLE_INFILL'] = [self.materials['wall'][wall]]
        data['allowed_roof_types'] = self.rules['roof_vocabulary']
        return Style(data)

    def roof(self, model, bounds, wall_top, roof_id, style, seed=1, overhang=1):
        if roof_id not in style.allowed_roof_types:
            raise ValueError(f'中州 authored 风格未允许屋顶 {roof_id}')
        x0, z0, x1, z1 = bounds
        node = Node(id=f'roof_{len(model.nodes)}', type='main_volume', origin=(x0, 0, z0),
                    size=(x1-x0+1, wall_top+1, z1-z0+1),
                    meta={'foundation_h': wall_top, 'wall_h': 1,
                          'roof': {'ridge_axis': 'x', 'overhang': overhang, 'spire_height': 5}})
        model.nodes.append(node.to_dict())
        grid = BlockGrid()
        roof_handler(roof_id)(grid, style, random.Random(seed), node)
        model.cells.update({p: c.state for p, c in grid.cells.items()})
        model.roof_type = roof_id

    def door(self, m, x, floor_y, z, facing='south', width=1):
        for dx in range(width):
            for dy, half in ((1, 'lower'), (2, 'upper')):
                m.put(x+dx, floor_y+dy, z,
                      f"{self.rules['doors']}[facing={facing},half={half},hinge={'left' if dx == 0 else 'right'},open=false,powered=false]")
        m.doors.append([x, floor_y+1, z])

    def rect_story(self, m, bounds, floor_y, stride, wall, open_sides=False):
        x0, z0, x1, z1 = bounds
        m.nodes.append(Node(id=f'story_{len(m.nodes)}', type='main_volume',
                            origin=(x0, floor_y, z0), size=(x1-x0+1, stride, z1-z0+1),
                            meta={'floor_y': floor_y, 'wall_h': stride-1}).to_dict())
        m.box(x0, floor_y, z0, x1, floor_y, z1, self.materials['floor'])
        m.box(x0, floor_y+1, z0, x1, floor_y+stride-1, z1, AIR)
        if not open_sides:
            for y in range(floor_y+1, floor_y+stride):
                for x in range(x0, x1+1):
                    m.put(x, y, z0, wall);m.put(x, y, z1, wall)
                for z in range(z0, z1+1):
                    m.put(x0, y, z, wall);m.put(x1, y, z, wall)
            for x in range(x0+2, x1-1, 3):
                for z in (z0, z1):
                    m.put(x, floor_y+2, z, 'minecraft:glass_pane')
            for z in range(z0+2, z1-1, 3):
                for x in (x0, x1):
                    m.put(x, floor_y+2, z, 'minecraft:glass_pane')
        for x in (x0, x1):
            for z in (z0, z1):
                m.box(x, floor_y+1, z, x, floor_y+stride-1, z, self.materials['frame'])
        if floor_y == 0:
            m.floor_footprint.update((x, z) for x in range(x0, x1+1) for z in range(z0, z1+1))

    def stairs(self, m, lower, upper, floor_y, stride):
        x0, z0, x1, z1 = lower
        ux0, uz0, ux1, uz1 = upper
        x = max(x0, ux0)+1
        front = min(z1, uz1)-1
        for step in range(1, stride+1):
            z = front-step+1
            # Headroom and upper-floor opening are authored, not inferred air.
            m.box(x, floor_y+step, z, x, floor_y+step+3, z, AIR)
            m.box(x, floor_y+1, z, x, floor_y+step-1, z, self.materials['floor'])
            m.put(x, floor_y+step, z,
                  'minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')

    def landing(self, m, bounds, floor_y=0, door_width=1):
        x0, z0, x1, z1 = bounds
        cx = (x0+x1)//2
        self.door(m, cx, floor_y, z1, width=door_width)
        m.entry = (cx, 1, z1+floor_y+1)
        for step in range(floor_y+1):
            z = z1+floor_y-step+1
            height = step
            for x in range(cx-1, cx+2):
                m.box(x, 0, z, x, height, z, self.materials['foundation'])
                if height:
                    m.put(x, height, z, 'minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
                m.box(x, height+1, z, x, height+3, z, AIR)
                m.floor_footprint.add((x, z))

    def bed(self, m, x, y, z):
        for dz, part in ((0, 'foot'), (-1, 'head')):
            m.put(x, y, z+dz, f'minecraft:white_bed[facing=north,part={part},occupied=false]')
        return x, y, z-1

    def furnish(self, m, bounds, stride, role):
        x0, z0, x1, z1 = bounds
        cx, cz = (x0+x1)//2, (z0+z1)//2
        upper = stride if m.floors > 1 else 0
        # The west edge belongs to the stair. All service targets are explicit air.
        rest = (x0+3, upper+1, z0+2)
        bed = self.bed(m, x0+2, upper+1, z0+2)
        work = (x1-2, 1, z1-2)
        fixture = (x1-1, 1, z1-2)
        m.put(x1-1, 1, z0+1, 'minecraft:barrel[facing=up,open=false]')
        if not role:
            m.put(x1-1, 1, z1-2, 'minecraft:crafting_table')
            return
        m.place(role+'_entry', 'entry', m.entry)
        if role == 'inn':
            m.put(*fixture, 'minecraft:barrel[facing=up,open=false]')
            m.place('inn_work', 'work', work, 'minecraft:barrel', fixture)
            m.place('inn_rest', 'rest', rest, 'minecraft:white_bed', bed)
            m.place('inn_social', 'social', (cx, 1, cz-1))
            guest_bed = self.bed(m, x1-1, upper+1, z0+2)
            m.place('guest_west', 'rest', (x1-2, upper+1, z0+2), 'minecraft:white_bed', guest_bed)
            m.put(x1-1, 1, z1-3, 'minecraft:smoker[facing=west,lit=false]')
        elif role == 'apothecary':
            m.put(*fixture, 'minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
            m.put(x1-1, 1, z1-3, 'minecraft:cauldron')
            m.place('herbalist_work', 'work', work, 'minecraft:brewing_stand', fixture)
            m.place('herbalist_rest', 'rest', rest, 'minecraft:white_bed', bed)
            m.place('herb_market', 'market', (cx, 1, cz-1))
        elif role == 'smith':
            m.put(*fixture, 'minecraft:smithing_table')
            m.put(x1-1, 1, z1-3, 'minecraft:furnace[facing=west,lit=false]')
            m.put(x1-1, 1, z0+2, 'minecraft:anvil[facing=north]')
            m.place('smith_work', 'work', work, 'minecraft:smithing_table', fixture)
            m.place('smith_rest', 'rest', rest, 'minecraft:white_bed', bed)
        elif role == 'market':
            m.put(*fixture, 'minecraft:barrel[facing=up,open=false]')
            m.put(x1-1, 1, z1-3, 'minecraft:chest[facing=west,type=single,waterlogged=false]')
            m.place('market', 'market', work, 'minecraft:barrel', fixture, capacity=2)
            m.place('market_social', 'social', (cx, 1, cz-1), capacity=2)
        elif role == 'guide':
            m.put(*fixture, 'minecraft:lectern[facing=west,has_book=false,powered=false]')
            m.place('guide_work', 'work', work, 'minecraft:lectern', fixture)
            m.place('guide_rest', 'rest', rest, 'minecraft:white_bed', bed)
        # The sign is a real BlockEntity; never an invisible label-only service.
        sign = (cx+1, 2, z1+1)
        m.put(*sign, 'minecraft:spruce_wall_sign[facing=south,waterlogged=false]')
        m.signs[sign] = next(s['name'] for s in self.spec['services'] if s['role'] == role)

    def residence(self, d, role='', kind='residence'):
        m = Model()
        w, depth = max(7, round(d['w']*.2)), max(6, round(d['d']*.2))
        x0, z0 = -(w//2), -(depth//2)
        bounds = x0, z0, x0+w-1, z0+depth-1
        style_name = d.get('style', 'jiangnan')
        style = self.style(wall=style_name)
        m.floors = d.get('stories', 1)
        if role or kind in ('shop', 'tea'):
            m.floors = max(2, m.floors)
        stride = self.rules['floor_stride']
        for f in range(m.floors):
            self.rect_story(m, bounds, f*stride, stride, self.materials['wall'][style_name])
        top = (m.floors-1)*stride+stride-1
        roof_id = self.rules['house_roofs'][style_name]
        self.roof(m, bounds, top, roof_id, style, d.get('seed', 1))
        self.landing(m, bounds, door_width=1)
        for f in range(m.floors-1):
            self.stairs(m, bounds, bounds, f*stride, stride)
        self.furnish(m, bounds, stride, role)
        return m

    def hall(self, d, role=''):
        m = Model()
        w, depth = max(9, round(d['w']*.2)), max(8, round(d['d']*.2))
        bounds = (-(w//2), -(depth//2), -(w//2)+w-1, -(depth//2)+depth-1)
        x0, z0, x1, z1 = bounds
        rank = d.get('rank', 1)
        platform = max(1, d.get('platform', 1))
        body = {1: 5, 2: 7, 3: 9}[rank]
        style = self.style(d.get('tile', 'grey'), monumental=True)
        m.box(x0, 0, z0, x1, platform, z1, self.materials['foundation'])
        m.floor_footprint.update((x,z) for x in range(x0,x1+1) for z in range(z0,z1+1))
        self.rect_story(m, bounds, platform, body, 'minecraft:red_terracotta')
        # Open three-bay front and rear circulation; central aisle stays clear.
        self.landing(m, bounds, platform, door_width=2)
        self.door(m, -1, platform, z0, 'north', width=2)
        for x in range(x0+3, x1-1, 5):
            m.box(x, platform+1, z1-2, x, platform+body-1, z1-2, self.materials['frame'])
        roof_id = self.rules['hall_roofs'][d.get('roof', 'xieshan')]
        self.roof(m, bounds, platform+body-1, roof_id, style, overhang=2)
        if d.get('double'):
            # A supported lower eave band retains the double-eaved silhouette;
            # only the exterior band is copied, leaving the high hall open.
            band = Model()
            self.roof(band, bounds, platform+body-4, roof_id, style, overhang=2)
            for (x,y,z), state in band.cells.items():
                if not (x0 <= x <= x1 and z0 <= z <= z1):
                    m.put(x,y,z,state)
            m.nodes.extend(band.nodes)
        m.put(x1-2, platform+1, z0+2, 'minecraft:lectern[facing=south,has_book=false,powered=false]')
        # Rear dais stairs also meet the exterior floor at source ground height.
        for i in range(platform):
            for x in (-1, 0):
                z = z0-1-i
                y = platform-i
                m.box(x,0,z,x,y,z,self.materials['foundation'])
                m.put(x,y,z,'minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
                m.box(x,y+1,z,x,y+3,z,AIR)
                m.floor_footprint.add((x,z))
        return m

    def tower(self, d, role='', kind='tower'):
        m = Model()
        radius = d.get('r', 0)*.2
        w = max(9, round(d.get('w', radius*10)*.2))
        depth = max(9, round(d.get('d', w*5)*.2))
        m.floors = d.get('floors', 2 if kind == 'drumtower' else 1)
        stride = self.rules['tower_stride']
        stories = []
        style = self.style(d.get('tile', 'grey'), monumental=True)
        for f in range(m.floors):
            inset = min(f//3, (min(w,depth)-9)//2) if kind == 'pagoda' else min(f//3,1)
            bounds = (-(w//2)+inset, -(depth//2)+inset, -(w//2)+w-1-inset, -(depth//2)+depth-1-inset)
            stories.append(bounds)
            self.rect_story(m, bounds, f*stride, stride, 'minecraft:white_terracotta')
            if f < m.floors-1:
                band=Model(); self.roof(band,bounds,f*stride+stride-2,'hip_roof',style,overhang=1)
                x0,z0,x1,z1=bounds
                for (x,y,z), state in band.cells.items():
                    if not (x0<=x<=x1 and z0<=z<=z1):m.put(x,y,z,state)
                m.nodes.extend(band.nodes)
        roof_id = {'pagoda':'pagoda', 'drumtower':'bell_drum_tower'}.get(kind, 'chinese_half_hip')
        self.roof(m,stories[-1],m.floors*stride-1,roof_id,style,overhang=1)
        self.landing(m,stories[0],door_width=2)
        for f in range(m.floors-1):self.stairs(m,stories[f],stories[f+1],f*stride,stride)
        if kind=='watchtower':
            x0,z0,x1,z1=stories[0]
            self.door(m,-1,0,z0,'north',width=2)
            for x,face in ((x0,'west'),(x1,'east')):
                self.door(m,x,0,0,face)
        return m

    def pavilion(self, d, role=''):
        m=Model()
        r=max(3,round(d.get('r',15)*.2)); sides=d.get('sides',4)
        # A continuous polygonal platform, with open bays between load-bearing posts.
        for x in range(-r,r+1):
            for z in range(-r,r+1):
                if sides==4 or x*x+z*z <= r*r+1:
                    m.put(x,0,z,self.materials['foundation']);m.floor_footprint.add((x,z))
                    m.box(x,1,z,x,4,z,AIR)
        for i in range(sides):
            angle=2*math.pi*i/sides
            x,z=round((r-1)*math.cos(angle)),round((r-1)*math.sin(angle))
            m.box(x,1,z,x,4,z,self.materials['frame'])
        style=self.style(d.get('tile','grey'),monumental=True)
        roof_id='tiered_eave_roof' if d.get('double') else 'pyramidal_roof'
        self.roof(m,(-r,-r,r,r),4,roof_id,style,overhang=1)
        m.entry=(0,1,r+1)
        for z in (r,r+1):
            for x in (-1,0,1):
                m.put(x,0,z,self.materials['foundation']);m.box(x,1,z,x,3,z,AIR);m.floor_footprint.add((x,z))
        return m

    def gate(self, d, role='', water=False):
        m=Model();w,depth=round(d['w']*.2),max(10,round(d['d']*.2))
        bounds=(-(w//2),-(depth//2),-(w//2)+w-1,-(depth//2)+depth-1)
        x0,z0,x1,z1=bounds
        for f in range(2):self.rect_story(m,bounds,f*6,6,'minecraft:stone_bricks' if f==0 else 'minecraft:red_terracotta')
        m.floors=2
        opening=8 if water else 3
        for x in range(-opening,opening+1):
            m.box(x,1,z0,x,5,z1,AIR)
            if water:
                # Omit the navigable water opening entirely: do not air-carve its
                # real river or insert a floor across the channel.
                for z in range(z0,z1+1):
                    for y in range(6):m.cells.pop((x,y,z),None)
                    m.floor_footprint.discard((x,z))
        self.roof(m,bounds,11,'hip_roof',self.style('yellow' if d['kind']=='palgate' else 'grey',monumental=True),overhang=2)
        self.stairs(m,bounds,bounds,0,6)
        m.entry=(0,1,z1+1)
        if not water:
            for x in range(-3,4):
                m.put(x,0,z1+1,self.materials['foundation']);m.floor_footprint.add((x,z1+1))
                m.box(x,1,z1+1,x,4,z1+1,AIR)
        return m

    def bridge(self, d, role='', covered=False):
        m=Model();span=round(d['span']*.2);width=max(5,round(d['width']*.2))
        x0=-(span//2);x1=x0+span-1;z0=-(width//2);z1=z0+width-1
        heights={x:round(2*(1-(2*(x-(x0+x1)/2)/span)**2)) for x in range(x0,x1+1)}
        for x in range(x0,x1+1):
            h=heights[x]
            for z in range(z0,z1+1):
                m.put(x,h-1,z,self.materials['foundation'])
                high_neighbor=x+1 if x<(x0+x1)/2 else x-1
                stair=heights.get(high_neighbor,h)>h
                state=(f'minecraft:stone_brick_stairs[facing={"east" if high_neighbor>x else "west"},half=bottom,shape=straight,waterlogged=false]'
                       if stair else self.materials['foundation'])
                m.put(x,h,z,state);m.box(x,h+1,z,x,h+4,z,AIR)
                if z in (z0,z1):m.put(x,h+1,z,'minecraft:stone_brick_wall')
        for x in (x0,x0+1,x1-1,x1):
            m.box(x,-4,z0,x,-1,z1,self.materials['foundation'])
        if covered:
            for x in range(x0+2,x1,5):
                for z in (z0,z1):m.box(x,heights[x]+1,z,x,7,z,self.materials['frame'])
            self.roof(m,(x0,z0,x1,z1),7,'chinese_overhang_gable',self.style('green'),overhang=1)
        m.nodes.append(Node(id='bridge_deck',type='main_volume',origin=(x0,-4,z0),size=(span,12,width),meta={'long_axis':'x','source_rotation_corrected':True}).to_dict())
        m.entry=(x0-1,1,0)
        return m

    def build(self, d, role=''):
        key=(d.get('variant',d['id']),role)
        if key in self.cache:return self.cache[key]
        kind=self.rules['kind_builders'][d['kind']]
        builders={
            'residence':lambda:self.residence(d,role),
            'shop':lambda:self.residence(d,role,'shop'),
            'tea':lambda:self.residence(d,role,'tea'),
            'courtyard':lambda:self.residence(d,role),
            'hall':lambda:self.hall(d,role),
            'tower':lambda:self.tower(d,role),
            'pagoda':lambda:self.tower(d,role,'pagoda'),
            'pavilion':lambda:self.pavilion(d,role),
            'gate':lambda:self.gate(d,role),
            'watergate':lambda:self.gate(d,role,water=True),
            'drumtower':lambda:self.tower({**d,'floors':2},role,'drumtower'),
            'watchtower':lambda:self.tower({**d,'d':d['w'],'floors':1},role,'watchtower'),
            'bridge':lambda:self.bridge(d,role),
            'covered_bridge':lambda:self.bridge(d,role,covered=True),
        }
        m=builders[kind]()
        self.cache[key]=m
        return m
