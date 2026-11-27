"""Convert an approved voxel study into authored terrain and construction runs.

This one-time authoring tool takes a grid and route metadata. The runtime exporter
reads the resulting sources without relying on NumPy or an offline preview directory.
"""
from __future__ import annotations

import argparse
from collections import deque
from concurrent.futures import ProcessPoolExecutor
from itertools import repeat
import gzip
import hashlib
import json
from pathlib import Path
import struct

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
FORMS = ROOT / 'blueprints/sects/forms'
NAMES = dict(cloud_gate='云阙', cliff_retreat='松崖', valley_courts='听泉双岸',
             valley_retreat='听泉谷中', lake_isles='照月连岛', lake_retreat='照月湖心')
RANKS = {'compact': ('branch', '分宗'), 'headquarters': ('headquarters', '总舵'), 'main': ('main', '主宗')}
LAND = {
    'rock': 'stone', 'strata': 'andesite', 'deep': 'deepslate', 'grass': 'grass_block',
    'dirt': 'dirt', 'moss': 'moss_block', 'cream': 'sandstone', 'limestone': 'sandstone',
    'umber': 'sandstone', 'weathered_stone': 'andesite', 'wet_stone': 'stone',
}
WATER = {'water', 'spring_sheet', 'spring_froth'}
PLANTS = {'pine', 'leaf', 'bark', 'blossom', 'reed'}
BLOCKS = {
    'path': 'smooth_stone', 'deck': 'spruce_planks', 'carving': 'cut_sandstone', 'brick': 'stone_bricks',
    'wood': 'spruce_wood', 'red': 'stripped_mangrove_wood', 'dark': 'dark_oak_planks',
    'jade': 'prismarine_bricks', 'plaster': 'smooth_sandstone', 'window': 'cyan_stained_glass',
    'metal': 'polished_andesite', 'brass': 'cut_copper', 'column': 'smooth_sandstone',
    'ochre': 'terracotta', 'cedar': 'dark_oak_wood', 'lacquer': 'red_terracotta', 'ink': 'polished_deepslate',
    'ridge_metal': 'waxed_cut_copper', 'lake_frame': 'stripped_warped_hyphae', 'glass': 'glass',
    'tile': 'dark_prismarine', 'tile_end': 'prismarine_bricks', 'dark_tile': 'deepslate_tiles',
    'dark_end': 'polished_andesite', 'blue_tile': 'oxidized_cut_copper', 'blue_end': 'prismarine',
    'warm_tile': 'mud_bricks', 'warm_end': 'terracotta', 'lamp': 'sea_lantern', 'book': 'bookshelf',
    'lily': 'lily_pad', 'lighter_bed': 'smooth_sandstone', 'sandstone_dado': 'cut_sandstone',
    'blue_slate': 'deepslate_tiles', 'blue_slate_shade': 'polished_deepslate', 'celadon_edge': 'prismarine_bricks',
    'green_tile': 'dark_prismarine', 'green_tile_shade': 'prismarine', 'hall_column': 'stripped_spruce_wood',
    'hall_tile': 'deepslate_tiles', 'hall_tile_shade': 'polished_deepslate', 'gallery_column': 'dark_oak_wood',
    'frieze': 'chiseled_sandstone', 'gilt_molding': 'waxed_cut_copper', 'lampstone': 'sea_lantern',
    'pale_cap': 'smooth_sandstone', 'rail': 'spruce_planks', 'roof_wood': 'dark_oak_planks',
    'copper': 'waxed_cut_copper', 'quiet_tile': 'mud_bricks', 'quiet_tile_shade': 'brown_terracotta',
    'rice_plaster': 'smooth_sandstone', 'screen_brown': 'brown_stained_glass', 'slate_bed': 'polished_andesite',
    'quartz': 'smooth_quartz', 'stone_recess': 'polished_andesite', 'shrine_red': 'red_terracotta',
}
ENTRY = {
    'cloud_gate-headquarters': '山门', 'lake_isles-main': '西水门下到渡头',
    'lake_isles-headquarters': '西岸日常廊', 'lake_retreat-headquarters': '岸上进环廊',
    'valley_courts-compact': '岸上进山', 'valley_courts-headquarters': '河岸入口',
}
DIRECTIONS = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}
ROW = struct.Struct('<5H')


def write_gzip(path, raw):
    data = gzip.compress(raw, mtime=0)
    path.write_bytes(data)
    return hashlib.sha256(data).hexdigest()


def export_form(source, key):
    family, rank = key.rsplit('-', 1)
    tier, tier_name = RANKS[rank]
    layout = family + '_' + tier
    folder = FORMS / layout
    folder.mkdir(parents=True, exist_ok=True)
    archive = np.load(source / f'{key}.npz')
    base = archive['grid']
    origin = archive['origin'].astype(int)
    info = json.loads((source / f'{key}.json').read_text())
    width = (base.shape[0] + 31) // 16 * 16
    depth = (base.shape[2] + 31) // 16 * 16
    height = min(384, max(16, int(np.nonzero(base.any(axis=(0, 2)))[0][-1]) + 12))
    xpad, zpad = (width - base.shape[0]) // 2, (depth - base.shape[2]) // 2
    offset = np.array((xpad, 0, zpad)) - origin

    def local(p):
        return tuple(int(v) for v in np.array(p) + offset)

    palette, ids = [], {}

    def material(name, land=False):
        state = name if ':' in name else 'minecraft:' + name
        tag = (state, land)
        if tag not in ids:
            ids[tag] = len(palette) + 1
            palette.append({'state': state, 'landform': land})
        return ids[tag]

    air, stone = material('air', True), material('stone', True)
    water = material('water', True)
    stone_paving = material('stone_bricks')
    names = {v: k for k, v in info['materials'].items()}
    stride = info['strIDE']
    used = np.unique(base)
    table = np.zeros(int(used[-1]) + 1, dtype=np.uint16)
    for code in used:
        if not code:
            continue
        name = names[code % stride]
        if name in PLANTS:
            continue
        if name in WATER:
            table[code] = water
        elif name in LAND and (family != 'valley_retreat' or rank != 'main' or code // stride == 1):
            table[code] = material(LAND[name], True)
        else:
            table[code] = material(BLOCKS.get(name) or LAND[name])
    grid = np.zeros((width, height, depth), dtype=np.uint16)
    pad = grid[xpad:xpad+base.shape[0], :height, zpad:zpad+base.shape[2]]
    sub = base[:, :height, :]
    pad[:, :sub.shape[1], :] = table[sub]
    routes = [(name, [local(p) for p in points]) for name, points in info['routes']]
    entry_path = next((path for name, path in routes if name == ENTRY.get(key)), routes[0][1])
    entry = entry_path[0]
    ex, ey, ez = entry
    distances = {'west': ex, 'east': width - 1 - ex, 'north': ez, 'south': depth - 1 - ez}
    original_roads = {p for _, route in routes for p in route}

    def collisions(direction):
        vx, vz = direction
        distance = distances[DIRECTIONS[direction]]
        return sum((ex+step*vx+cross*vz, y, ez+step*vz+cross*vx) in original_roads
                   for step in range(distance+1) for cross in range(-2,3)
                   for y in range(ey-4,ey+5) if y != ey)

    dx,dz = min(DIRECTIONS,key=lambda direction:(collisions(direction),distances[DIRECTIONS[direction]]))
    facing = DIRECTIONS[dx,dz]
    length = distances[facing]
    port = (ex+dx*length, ey, ez+dz*length)
    approach = [(ex+dx*i, ey, ez+dz*i) for i in range(length + 1)]
    # Extend the public access from the authored approach to the chunk-aligned edge.
    # Its full width bears on real paving and meets the outside road at the same height.
    approach_cells = set()
    for x, y, z in approach:
        for cross in range(-2, 3):
            vx, vz = x+dz*cross, z+dx*cross
            if any((vx,yy,vz) in original_roads for yy in range(y-4,y+5) if yy!=y):
                continue
            approach_cells.add((vx, vz))
            grid[vx, max(0, y-4):y-1, vz] = stone
            grid[vx, y-1, vz] = stone_paving
            grid[vx, y:y+4, vz] = 0

    road = {p for _, route in routes for p in route}
    for trace in info['traces']:
        if trace['mode'] != 'lift':
            continue
        a, b = [local(p) for p in trace['points']]
        x, y0, z = a
        assert (x, z) == (b[0], b[2])
        y1 = b[1]
        backing = min(DIRECTIONS, key=lambda d: sum((x+d[0],yy,z+d[1]) in road for yy in range(y0-1,y1+1)))
        bx,bz = backing
        face = DIRECTIONS[-bx,-bz]
        ladder = material(f'minecraft:ladder[facing={face},waterlogged=false]')
        grid[x+bx, y0:y1+1, z+bz] = stone_paving
        grid[x, y0:y1, z] = ladder
        grid[x, y1:y1+3, z] = 0
        for yy in (y0, y1):
            grid[x-bx, yy:yy+2, z-bz] = 0
            if not grid[x-bx, yy-1, z-bz]:
                grid[x-bx, yy-1, z-bz] = stone_paving

    # Route rises in the rendering use full blocks. Replace the upper tread with
    # an oriented Minecraft stair when both route positions have a valid floor.
    steps = {}
    for _, points in routes:
        for a, b in zip(points, points[1:]):
            low, high = sorted((a, b), key=lambda p: p[1])
            x, y, z = high
            direction = (x-low[0], z-low[2])
            if y-low[1] == 1 and direction in DIRECTIONS and grid[x, y-1, z] not in (0, water):
                floor = palette[grid[x, y-1, z]-1]['state']
                stairs = 'spruce_stairs' if floor.endswith('_planks') else 'stone_brick_stairs'
                steps.setdefault((x, y-1, z), material(f'minecraft:{stairs}[facing={DIRECTIONS[direction]},half=bottom,shape=straight,waterlogged=false]'))
    for point, state in steps.items():
        grid[point] = state

    # Service fixtures occupy dry, connected floor near the public entrance.
    # Reads are explicit, allowing the command and the natural site to use the same positions.
    stand = np.zeros(grid.shape, dtype=bool)
    stand[:, 1:-1, :] = ((grid[:, :-2, :] != 0) & (grid[:, :-2, :] != water)
                         & (grid[:, 1:-1, :] == 0) & (grid[:, 2:, :] == 0))
    assert stand[entry], (key, entry, grid[ex,ey-1:ey+3,ez])
    nearby = {entry}
    queue = deque([entry])
    while queue:
        x,y,z = queue.popleft()
        for ax,az in DIRECTIONS:
            xx,zz=x+ax,z+az
            if abs(xx-ex)+abs(zz-ez)>65 or not (0<=xx<width and 0<=zz<depth):
                continue
            for yy in (y, y-1, y+1):
                p=(xx,yy,zz)
                if not p in nearby and 1<=yy<height-2 and stand[p] and grid[xx,min(y,yy)+2,zz]==0:
                    nearby.add(p)
                    queue.append(p)
    road.update(approach)
    ranked = sorted(nearby-road, key=lambda p: (abs(p[0]-ex)+abs(p[2]-ez)+2*abs(p[1]-ey), p))
    blocked = set()

    def station(is_shelf):
        for p in ranked:
            x,y,z=p
            if not (2<=x<width-2 and 2<=z<depth-2 and 5<=abs(x-ex)+abs(z-ez)<=48):
                continue
            if p in blocked or (x,z) in approach_cells:
                continue
            for (ax,az), direction in DIRECTIONS.items():
                reader=(x+ax,y,z+az)
                if reader in nearby and reader not in blocked and reader!=entry:
                    fixture='scripture_shelf' if is_shelf else 'linlang_counter'
                    bottom=f'yuanxian:{fixture}[facing={direction}'
                    grid[p]=material(bottom+',half=lower]' if is_shelf else bottom+']')
                    if is_shelf:
                        grid[x,y+1,z]=material(bottom+',half=upper]')
                    blocked.update((p,reader))
                    return {'position':list(p),'reader':list(reader),'facing':direction}
        raise ValueError(f'{key} 无可达设施面')

    shelf, counter = station(True), station(False)
    rooms = [r for r in info['facades'] if r['name'] in ('弟子居', '丹房')]
    if key=='valley_retreat-main':
        rooms.extend([
            {'name':'弟子居','at':(236,17,186),'rx':9,'rz':9,'facing':'south'},
            {'name':'丹房','at':(219,16,164),'rx':9,'rz':7,'facing':'south'},
        ])
    furniture = {}
    for room in rooms:
        rx, rz = room['rx'], room['rz']
        if room['facing'] in ('east','west'):
            rx, rz = rz, rx
        cx, base_y, cz = local(room['at'])
        cells = {(x,y,z) for x in range(cx-rx+1,cx+rx) for z in range(cz-rz+1,cz+rz)
                 for y in range(max(1,base_y),base_y+4) if stand[x,y,z] and
                 (x,y,z) not in road and (x,y,z) not in blocked and not grid[x,y+1,z]}
        for point in sorted(cells,key=lambda p:(abs(p[0]-cx)+abs(p[2]-cz),p)):
            x,y,z = point
            candidate = next(((x+ax,y,z+az,face) for (ax,az),face in DIRECTIONS.items()
                              if (x+ax,y,z+az) in cells and (x+ax,y,z+az) not in blocked),None)
            if candidate is None:
                continue
            xx,yy,zz,face = candidate
            if room['name']=='弟子居':
                for pos,part in ((point,'foot'),((xx,yy,zz),'head')):
                    state=f'minecraft:cyan_bed[facing={face},occupied=false,part={part}]'
                    furniture[pos]=state
                    grid[pos]=material(state)
                    blocked.add(pos)
            else:
                for pos,state in ((point,'minecraft:furnace'),((xx,yy,zz),'minecraft:crafting_table')):
                    furniture[pos]=state
                    grid[pos]=material(state)
                    blocked.add(pos)
            break
    for name, points in routes:
        for x,y,z in points:
            if not grid[x,y-1,z] or grid[x,y-1,z]==water or np.any(grid[x,y:y+2,z]):
                # The authored lift uses replacing rungs in its own shaft.
                states=[palette[index-1]['state'] for index in grid[x,y-1:y+2,z] if index]
                if any(state.startswith('minecraft:ladder[') for state in states):
                    continue
                raise ValueError(f'{key} {name} 施工后动线受阻 {(x,y,z)} {states}')

    activity = [p for p in ranked if p not in blocked and abs(p[0]-ex)+abs(p[2]-ez)<=12][:40]
    steward = entry
    activity.insert(0, steward)

    active_terrain = np.array([False, *(p['landform'] and p['state'] not in ('minecraft:air','minecraft:water') for p in palette)])
    solid_land = active_terrain[grid]
    levels = np.arange(height)[None,:,None]
    tops = np.max(np.where(solid_land, levels, -1), axis=1)
    watertop = int(np.where(grid == water, levels, -1).max()) + 1
    ground = np.maximum(tops, 0)
    # Keep the landscape procedural outside the authored area. Within the lot,
    # only open intervals and exposed ground treatments are shipped.
    xx, zz = np.ogrid[:width, :depth]
    edge = np.minimum(np.minimum(xx-xpad, xpad+base.shape[0]-1-xx),
                      np.minimum(zz-zpad, zpad+base.shape[2]-1-zz))
    weight = np.clip((edge-1)*255//18, 0, 255).astype(np.uint8)
    weight[tops < 3] = 0
    structures = np.array([False, *(not p['landform'] for p in palette)])[grid].any(axis=1)
    wet_columns = (grid == water).any(axis=1)
    weight[structures | wet_columns] = 255
    for x,z in approach_cells:
        weight[x,z] = 255
        ground[x,z] = ey-1

    surface = np.zeros((width,depth), dtype=np.uint8)
    surface_states = ['minecraft:stone']
    cap = np.take_along_axis(grid, ground[:, None, :], axis=1)[:,0,:]
    for idx in np.unique(cap):
        if not idx:
            continue
        state = palette[idx-1]['state']
        if idx not in (water,air) and palette[idx-1]['landform'] and state not in surface_states:
            surface_states.append(state)
        if state in surface_states:
            surface[cap==idx] = surface_states.index(state)
    surface[structures] |= 128
    field = np.zeros((depth,width), dtype=[('height','<i2'),('weight','u1'),('surface','u1')])
    field['height'],field['weight'],field['surface']=ground.T,weight.T,surface.T

    # On full-magnitude columns base terrain supplies stone; write the cave air,
    # exposed strata, water and construction as compact vertical runs.
    mask = (weight[:,None,:] == 255) & (levels <= ground[:,None,:])
    grid[mask & (grid == 0)] = air
    grid[solid_land & (levels < ground[:,None,:]-4)] = 0
    grid[solid_land & (weight[:,None,:] < 255)] = 0
    runs = bytearray()
    for z in range(depth):
        for x in range(width):
            states = grid[x,:,z]
            cuts = np.r_[0, np.flatnonzero(states[1:] != states[:-1])+1, height]
            for a,b in zip(cuts,cuts[1:]):
                if idx:=int(states[a]):
                    runs.extend(ROW.pack(x,z,a,b-a,idx-1))
    field_hash = write_gzip(folder/'terrain.bin.gz', field.tobytes())
    form_hash = write_gzip(folder/'form.bin.gz', runs)
    limits = max(1, int(ground.max()), max(r[2]+r[3]-1 for r in ROW.iter_unpack(runs)))
    spec = {
        'schema':1, 'status':'approved', 'id':'yuanxian:'+layout, 'name':NAMES[family]+' · '+tier_name,
        'size':[width,height,depth], 'tier':tier, 'max_y':limits, 'archetype':{'cloud':'cliff','cliff':'cliff','valley':'valley','lake':'lake'}[family.split('_')[0]],
        'scale':'compact' if rank=='compact' else 'large',
        'blend_margin':16,'vegetation_clearance':0,
        'entry_anchor':list(entry), 'steward':list(steward), 'courtyard_cells':activity,
        'shelves':[shelf],'linlang':counter,'road_port':{'position':port,'width':3,'facing':facing},
        'rock':'minecraft:stone','surface':surface_states[0],'surfaces':surface_states,'subsoil':'minecraft:dirt','topsoil_depth':3,'regional_surfaces':{},
        'dry_gate_margin':max(0,min(40,ey-watertop)) if watertop else 0,
        'terrain':{'bounds':[0,0,width,depth],'sha256':field_hash},
        'form':{'materials':palette,'runs':len(runs)//ROW.size,'sha256':form_hash},
        'tiles':[], 'preview_id':key,
        'furniture':[dict(position=pos,state=state) for pos,state in sorted(furniture.items())],
        'preview_grid_sha256':hashlib.sha256((source / f'{key}.npz').read_bytes()).hexdigest(),
    }
    (folder/'form.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n')
    print(key, spec['size'], len(runs)//ROW.size, 'gate', entry, 'road', port, 'max', limits, flush=True)
    return {k:spec[k] for k in ('id','archetype','scale','tier')} | {'family':family, 'source':f'forms/{layout}/form.json'}


if __name__=='__main__':
    args=argparse.ArgumentParser(description=__doc__)
    args.add_argument('source',type=Path)
    args.add_argument('--jobs',type=int,default=2)
    opts=args.parse_args()
    files=opts.source
    catalog=ROOT/'blueprints/sects/catalog.json'
    policy=json.loads(catalog.read_text())
    with ProcessPoolExecutor(max_workers=opts.jobs) as pool:
        rows=list(pool.map(export_form,repeat(files),(f'{family}-{rank}' for family in NAMES for rank in RANKS)))
    policy['layouts']=rows
    policy['default']='yuanxian:cloud_gate_branch'
    catalog.write_text(json.dumps(policy,ensure_ascii=False,indent=2)+'\n')
