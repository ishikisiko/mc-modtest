"""Materialize the Java plan. Geometry, export and preview share these cells."""
from __future__ import annotations
from collections import deque
import hashlib
import json
import random

import numpy as np

from .assets import canonical, rotated_pos, rotated_state
from yunshan.build_district import Volume, schematic
from buildgen.modset import load_modset
from yunshan.preview_building import model_payload

AIR = 'minecraft:air'


def xyz(point):
    return point['x'], point['y'], point['z']


def build(plan, assets):
    n = plan['size']; recipe = plan['recipe']; mats = recipe['materials']
    heights = np.array(plan['heights'], dtype=np.int16).reshape(n, n)
    kinds = np.array(plan['kinds'], dtype=np.uint8).reshape(n, n)
    base_y = recipe['base_y']
    top_y = max(int(heights.max())+16, max(b['y']+b['height'] for b in plan['buildings']))+2
    volume = Volume((0, base_y, 0), (n-1, top_y, n-1))

    def state_id(state):
        state = canonical(state)
        if state not in volume.indices:
            volume.indices[state] = len(volume.palette)
            volume.palette.append(state)
        return volume.indices[state]

    grid = np.frombuffer(volume.cells, dtype=np.uint16).reshape(top_y-base_y+1, n, n)
    stone, soil, grass, bank, court, road, water = [state_id(mats[k]) for k in ('rock','soil','ground','bank','court','road','water')]
    surface = np.full((n,n), grass, dtype=np.uint16)
    surface[kinds==1] = court; surface[kinds==2] = road
    water_y = recipe['river']['water_y']
    surface[heights <= water_y+1] = bank
    soil_depth=recipe['construction']['soil_depth']
    for y in range(base_y, top_y+1):
        layer = grid[y-base_y]
        layer[y < heights-soil_depth] = stone
        layer[(y >= heights-soil_depth) & (y < heights-1)] = soil
        layer[y == heights-1] = surface[y == heights-1]
        layer[(y >= heights) & (y < water_y)] = water

    render_heights = heights.copy()
    overlay = {}
    building_expected = {}
    signs = []

    def put(pos, state):
        x,y,z = pos
        if not (0<=x<n and 0<=z<n and base_y<=y<=top_y):
            raise ValueError(f'outside materialized region: {pos}')
        state = canonical(state)
        grid[y-base_y,z,x] = state_id(state)
        overlay[pos] = state
        if y < render_heights[z,x]:
            render_heights[z,x] = y

    for r in plan['roads']:
        state = mats['bridge'] if r['bridge'] else mats['road']
        if r['facing']:
            state = mats['step']+'[facing='+r['facing']+',half=bottom,shape=straight,waterlogged=false]'
        put((r['x'],r['height']-1,r['z']),state)

    for b in plan['buildings']:
        asset = assets[b['asset']]; size = asset['public']['size']; q = b['rotation']
        expected = {}
        # Explicit air is applied only inside the original template envelope.
        for y in range(size[1]):
            for z in range(size[2]):
                for x in range(size[0]):
                    local = (x,y,z)
                    r = rotated_pos(local,size,q)
                    p = (b['x']+r[0],b['y']+r[1],b['z']+r[2])
                    state = canonical(rotated_state(asset['blocks'].get(local,AIR),q))
                    put(p,state); expected[p] = state
        building_expected[b['id']] = expected
        for s in asset['signs']:
            p=rotated_pos(s['pos'],size,q)
            signs.append({**s,'pos':[b['x']+p[0],b['y']+p[1],b['z']+p[2]]})

    road_positions={(r['x'],r['z']) for r in plan['roads']}
    construction=recipe['construction']
    for b in plan['buildings']:
        if b['cluster'] not in construction['courtyard_clusters']:continue
        x0,z0,x1,z1=b['pad'];level=b['approach']['y']
        perimeter={(x,z) for x in range(x0,x1+1) for z in (z0,z1)}|{(x,z) for z in range(z0,z1+1) for x in (x0,x1)}
        for x,z in sorted(perimeter):
            if (x,z) in road_positions:continue
            for y in range(level,level+construction['courtyard_wall_height']):put((x,y,z),'minecraft:smooth_quartz')
            put((x,level+construction['courtyard_wall_height'],z),'minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]')

    # Bridges own deck, rails and piers, with a real channel below the deck.
    bridge = [r for r in plan['roads'] if r['bridge']]
    if bridge:
        zcenter = next(r['points'][0]['z'] for r in plan['routes'] if r['id']=='river_bridge')
        xs = sorted({r['x'] for r in bridge})
        deck_y = bridge[0]['height']-1
        for x in xs:
            half=construction['bridge_half_width']
            for z in (zcenter-half,zcenter+half):
                put((x,deck_y,z),mats['bridge'])
                put((x,deck_y+1,z),'minecraft:spruce_fence[east=true,west=true,north=false,south=false,waterlogged=false]')
            if (x-xs[0])%construction['bridge_pier_spacing']==0 or x==xs[-1]:
                for z in (zcenter-2,zcenter+2):
                    for y in range(int(heights[z,x]),deck_y):put((x,y,z),'minecraft:stripped_spruce_log[axis=y]')

    # Stable scenery outside all pads and route clearance; no floating decor.
    rng = random.Random(plan['seed'] ^ 0x56414c4c4559)
    v=recipe['vegetation']; road_xy=[(r['x'],r['z']) for r in plan['roads']]
    trees=[]
    for _ in range(v['attempts']):
        if len(trees)>=v['count']: break
        x=rng.randrange(v['edge_margin'],n-v['edge_margin']);z=rng.randrange(v['edge_margin'],n-v['edge_margin'])
        y=int(heights[z,x])
        if y<=water_y+1 or kinds[z,x]:continue
        if any((x-a)**2+(z-b)**2<v['path_clearance']**2 for a,b in road_xy):continue
        if any(b['pad'][0]-v['pad_clearance']<=x<=b['pad'][2]+v['pad_clearance'] and b['pad'][1]-v['pad_clearance']<=z<=b['pad'][3]+v['pad_clearance'] for b in plan['buildings']):continue
        if any((x-t[0])**2+(z-t[2])**2<v['spacing']**2 for t in trees):continue
        h=rng.randint(v['height_min'],v['height_max']);tree={}
        for dy in range(h):tree[x,y+dy,z]='minecraft:spruce_log[axis=y]'
        for offset,radius in v['canopy_layers']:
            dy=h+offset
            for dx in range(-radius,radius+1):
                for dz in range(-radius,radius+1):
                    if abs(dx)+abs(dz)>radius+1 or (dx==0 and dz==0 and dy<h):continue
                    tree[x+dx,y+dy,z+dz]='minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]'
        if any(grid[p[1]-base_y,p[2],p[0]]!=0 for p in tree):continue
        for p,s in tree.items():put(p,s)
        trees.append([x,y,z,h])

    # Show the final terrain body, retaining true one-block template recesses.
    render_ground=[]
    for z in range(n):
        for x in range(n):render_ground.append(int(grid[int(render_heights[z,x])-base_y-1,z,x]))
    palette=volume.palette
    nonair={p:s for p,s in overlay.items() if s!=AIR}
    preview={'size':n,'base_y':base_y,'heights':render_heights.flatten().tolist(),
             'ground':render_ground,'water_y':water_y,'palette':palette,
             'blocks':[[*p,volume.indices[s]] for p,s in sorted(nonair.items())],
             'models':model_payload(nonair),'signs':signs,'clusters':plan['clusters'],
             'buildings':plan['buildings'],'routes':plan['routes'],'spawn':plan['spawn'],
             'seed':plan['seed'],'trees':len(trees),'source':plan['algorithm']}
    errors=load_modset('vanilla').palette_block_errors(palette)
    if errors:raise ValueError(errors)
    return volume,preview,building_expected,signs


def validate(plan, volume, expected, preview):
    failures=[];checks={}
    def check(name, ok, detail=None):
        checks[name]={'passed':bool(ok),'detail':detail}
        if not ok:failures.append(name)
    def get(p):return volume.get(p)
    def empty(p):return get(p)==AIR or get(p).split('[')[0].endswith('_door')
    for id,cells in expected.items():
        diff=[p for p,s in cells.items() if get(p)!=s]
        check('template:'+id,not diff,{'cells':len(cells),'differences':diff[:4]})
    missing=[];blocked=[];bad_steps=[]
    for r in plan['roads']:
        p=(r['x'],r['height'],r['z'])
        if not empty(p) or not empty((p[0],p[1]+1,p[2])):blocked.append(p)
        floor=get((p[0],p[1]-1,p[2]))
        if floor in (AIR,'minecraft:water'):missing.append(p)
        if r['facing'] and ('_stairs[' not in floor or 'facing='+r['facing'] not in floor):bad_steps.append(p)
    for route in plan['routes']:
        for a in route['points']:
            p=xyz(a)
            if not empty(p) or not empty((p[0],p[1]+1,p[2])):blocked.append(p)
            if get((p[0],p[1]-1,p[2])) in (AIR,'minecraft:water'):missing.append(p)
        for aa,bb in zip(route['points'],route['points'][1:]):
            a,b=xyz(aa),xyz(bb)
            if abs(a[0]-b[0])+abs(a[2]-b[2])!=1 or abs(a[1]-b[1])>1:bad_steps.append((a,b));continue
            if a[1]!=b[1]:
                lo,hi=(a,b) if a[1]<b[1] else (b,a)
                direction={(1,0):'east',(-1,0):'west',(0,1):'south',(0,-1):'north'}[(hi[0]-lo[0],hi[2]-lo[2])]
                state=get((hi[0],hi[1]-1,hi[2]))
                if '_stairs[' not in state or 'facing='+direction not in state:bad_steps.append((a,b))
                if not empty((lo[0],hi[1]+1,lo[2])):blocked.append(lo)
    check('route_clearance',not blocked,blocked[:8]);check('route_support',not missing,missing[:8]);check('stair_transitions',not bad_steps,bad_steps[:8])
    # Independent graph from the final route-support blocks, not route labels.
    nodes={xyz(p) for r in plan['routes'] for p in r['points']};start=xyz(plan['spawn'])
    start_ok=empty(start) and empty((start[0],start[1]+1,start[2])) and get((start[0],start[1]-1,start[2])) not in (AIR,'minecraft:water')
    seen={start} if start_ok else set();queue=deque(seen)
    while queue:
        p=queue.popleft()
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            for dy in (-1,0,1):
                q=(p[0]+dx,p[1]+dy,p[2]+dz)
                if q not in nodes or q in seen:continue
                if not empty(q) or not empty((q[0],q[1]+1,q[2])):continue
                if get((q[0],q[1]-1,q[2])) in (AIR,'minecraft:water'):continue
                if dy:
                    hi,lo=(q,p) if dy>0 else (p,q)
                    facing={(1,0):'east',(-1,0):'west',(0,1):'south',(0,-1):'north'}[(hi[0]-lo[0],hi[2]-lo[2])]
                    if 'stairs' not in get((hi[0],hi[1]-1,hi[2])) or 'facing='+facing not in get((hi[0],hi[1]-1,hi[2])):continue
                seen.add(q);queue.append(q)
    approaches={b['id']:xyz(b['approach']) in seen for b in plan['buildings']}
    check('all_building_approaches_connected',all(approaches.values()),approaches)
    check('all_route_points_connected',seen==nodes,{'visited':len(seen),'total':len(nodes)})
    # Solid substrate under each building pad, excluding original intentional air above it.
    holes=[]
    for b in plan['buildings']:
        for z in range(b['pad'][1],b['pad'][3]+1):
            for x in range(b['pad'][0],b['pad'][2]+1):
                for y in range(volume.minimum[1],b['y']):
                    if get((x,y,z)) in (AIR,'minecraft:water'):holes.append((x,y,z));break
    check('building_substrate_solid',not holes,holes[:8])
    wet=[(x,z) for z in range(plan['size']) for x in range(plan['size']) if plan['natural_heights'][z*plan['size']+x]<plan['recipe']['river']['water_y']]
    check('river_bed_preserved',all(plan['heights'][z*plan['size']+x]==plan['natural_heights'][z*plan['size']+x] for x,z in wet),len(wet))
    check('actual_river_bed',all(get((x,plan['natural_heights'][z*plan['size']+x]-1,z))==plan['recipe']['materials']['bank'] for x,z in wet),len(wet))
    report={'passed':not failures,'checks':checks,'failures':failures,'seed':plan['seed'],
            'buildings':len(plan['buildings']),'trees':preview['trees'],'route_points':len(nodes),
            'geometry_sha256':hashlib.sha256(json.dumps([volume.minimum,volume.size,volume.palette],separators=(',',':')).encode()+volume.cells.tobytes()).hexdigest(),
            'max_earthwork':max(abs(a-b) for a,b in zip(plan['heights'],plan['natural_heights'])),
            'walk_semantics':'conservative two-block headroom, opened doors, directed straight stairs; not Minecraft collision',
            'not_verified':['Minecraft placement','full player collision','NPC navigation','FPS','owner visual acceptance']}
    return report


def export_scene(volume, signs, out):
    path=out/'river_valley.schem'
    schematic(volume,signs,path)
    # Full palette / byte-stream verification, without expanding millions of Python ints.
    from buildgen.nbtread import read_gzipped_nbt
    _,root=read_gzipped_nbt(str(path));raw=root['BlockData'];actual=np.frombuffer(volume.cells,dtype=np.uint16)
    i=0;value=0;shift=0
    for byte in raw:
        byte &= 255;value|=(byte&127)<<shift
        if byte&128:shift+=7;continue
        if i>=len(actual) or actual[i]!=value:raise AssertionError('schematic roundtrip mismatch at '+str(i))
        i+=1;value=0;shift=0
    if i!=len(actual) or shift:raise AssertionError('truncated schematic')
    if root['Palette']!={s:i for i,s in enumerate(volume.palette)}:raise AssertionError('schematic palette drift')
    return {'path':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'cells_verified':i,'size':volume.size,'minimum':volume.minimum}
