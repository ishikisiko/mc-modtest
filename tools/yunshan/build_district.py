#!/usr/bin/env python3
"""Realize the owner-approved D1 plan as a full Sponge schematic and tiled NBT.

No game, network, ignored historical files, or optional packages are required.
The immutable column snapshot and complete original house builders preserve D1.
All positions in manifest.json are absolute Minecraft coordinates unless marked
``artwork``. A ground level is the player's feet, not the top block coordinate.
"""
from __future__ import annotations

import argparse
from array import array
from collections import Counter, deque
import copy
import hashlib
import json
import math
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from yunshan import baseline_houses
from yunshan.build_functional import rotated_pos, rotated_state
from yunshan.build_inn import attach_signs, canonical
from yunshan.plan_district import prepare, validate as validate_plan, rect_cells, polygon_cells, street_cells

AIR = 'minecraft:air'
FACE = {(0, -1): 'north', (1, 0): 'east', (0, 1): 'south', (-1, 0): 'west'}


def read(path):
    return json.loads(Path(path).read_text())


class Volume:
    """Dense palette indices in Sponge x-fastest ordering, bounded writes."""
    def __init__(self, minimum, maximum):
        self.minimum = tuple(minimum)
        self.maximum = tuple(maximum)
        self.size = tuple(b-a+1 for a,b in zip(minimum,maximum))
        self.palette = [AIR]
        self.indices = {AIR: 0}
        self.cells = array('H', [0]) * math.prod(self.size)

    def index(self, p):
        x,y,z = (p[i]-self.minimum[i] for i in range(3))
        w,h,d = self.size
        if not (0 <= x < w and 0 <= y < h and 0 <= z < d):
            raise ValueError(f'block outside district: {p}')
        return (y*d+z)*w+x

    def set(self, p, state):
        state = canonical(state)
        if state not in self.indices:
            self.indices[state] = len(self.palette)
            self.palette.append(state)
        self.cells[self.index(p)] = self.indices[state]

    def get(self, p):
        return self.palette[self.cells[self.index(p)]]

    def fill(self, minimum, maximum, state):
        # x-runs reduce both runtime and temporary memory for terrain bodies.
        state = canonical(state)
        if state not in self.indices:
            self.indices[state] = len(self.palette)
            self.palette.append(state)
        row = array('H', [self.indices[state]]) * (maximum[0]-minimum[0]+1)
        for y in range(minimum[1], maximum[1]+1):
            for z in range(minimum[2], maximum[2]+1):
                i = self.index((minimum[0],y,z))
                self.cells[i:i+len(row)] = row


def realized_grid(plan, site, land, buildings, streets, retained, baseline):
    """Start from the exact old terrain and apply accepted lots and pathways."""
    x0,z0,x1,z1 = baseline['window']
    levels = {(x,z): baseline['levels'][(z-z0)*(x1-x0)+x-x0]
              for z in range(z0,z1) for x in range(x0,x1)}
    kinds = {(x,z): baseline['kinds'][(z-z0)*(x1-x0)+x-x0]
             for z in range(z0,z1) for x in range(x0,x1)}
    # Removing a street must also remove its former paved/stair surface.
    replaced = {s['id'] for s in plan['replace_streets']}
    for st in site['streets']:
        if st['id'] in replaced:
            for p in street_cells(st):
                kinds[p] = 'terrace'
    for b in buildings:
        for p in rect_cells(b['site']):
            levels[p], kinds[p] = b['level'], 'forecourt'
    for l in retained:
        for p in rect_cells(l['rect']):
            levels[p], kinds[p] = l['level'], 'yard'
    walking = {}
    for plaza in site['plazas']:
        for p in polygon_cells(plaza['poly']):
            walking[p] = plaza['level']
            levels[p], kinds[p] = plaza['level'], 'plaza'
    for st in streets:
        for p,y in street_cells(st).items():
            walking[p] = y
            levels[p], kinds[p] = y, st['kind']
    for court in plan['forecourts']:
        for p in rect_cells(court['rect']):
            walking[p] = court['level']
            levels[p], kinds[p] = court['level'], 'forecourt'
    for garden in plan['gardens']:
        for p in rect_cells(garden['rect']):
            levels[p], kinds[p] = garden['level'], 'yard'
    return levels, kinds, walking


def place_original_houses(volume, retained, offset):
    result = []
    for lot in retained:
        fn = getattr(baseline_houses, lot['family'])
        structure, attachments, meta = fn(1 if lot['tier']=='small' else 1.5)
        q = {'S':0,'W':1,'N':2,'E':3}[lot['facing']]
        dx,dz = lot['door']
        def world(p):
            x,y,z = p
            x,z = x-meta['door_x'],z-meta['D']
            for _ in range(q): x,z = -z,x
            return dx+x+offset[0],lot['level']+y+offset[1],dz+z+offset[2]
        cells = dict(structure)
        cells.update(attachments)
        for p,state in cells.items():
            volume.set(world(p),rotated_state(state,q))
        result.append({'id':lot['id'],'family':lot['family'],'tier':lot['tier'],
                       'blocks':len(cells),'door':[dx+offset[0],lot['level']+offset[1],dz+offset[2]],
                       'preserved_complete_template':True, '_expected': {world(p):rotated_state(state,q) for p,state in cells.items()}})
    return result


def place_landmarks(volume, land, levels, offset, material, gate_level=38):
    """Preserve the original D1 gate/tower massing, trees and wells exactly."""
    ox,oy,oz = offset
    def box(b,state):
        a,y,c,d,h,f = b
        a,d = max(a,volume.minimum[0]-ox),min(d,volume.maximum[0]-ox+1)
        c,f = max(c,volume.minimum[2]-oz),min(f,volume.maximum[2]-oz+1)
        if a<d and c<f:
            volume.fill((a+ox,y+oy,c+oz),(d-1+ox,h-1+oy,f-1+oz),state)
    gate = land['site']['gate'];gx,gz=gate['x'],gate['z']
    x0,x1=int(gx-gate['w']/2),int(gx+gate['w']/2)
    z0,z1=int(gz-gate['d']/2),int(gz+gate['d']/2)
    px0,px1=int(gx-4.6),math.ceil(gx+4.6)
    gy=gate_level;wall_top=gy+9
    for a,b in [(x0,px0),(px1,x1)]: box((a,gy-1,z0,b,wall_top,z1),material)
    box((px0,gy+6,z0,px1,wall_top,z1),material)
    box((x0+4,wall_top,z0+4,x1-4,wall_top+8,z1-4),material)
    box((x0+1,wall_top+8,z0+2,x1-1,wall_top+10,z1-2),material)
    # The baseline gate passage intersects the high wall underlay. An explicit
    # matching street-level passage keeps the existing gate actually enterable.
    volume.fill((px0+ox,gy+oy,z0+oz),(px1-1+ox,gy+5+oy,z1-1+oz),AIR)
    volume.fill((px0+ox,gy-1+oy,z0+oz),(px1-1+ox,gy-1+oy,z1-1+oz),'stone_bricks')
    for sp in land['spots']:
        if sp['kind']=='landmark':
            a,c,d,f=sp['rect'];y=levels[a,c]
            for b in [(a,y,c,d+1,y+7,f+1),(a+2,y+7,c+2,d-1,y+14,f-1),(a+1,y+14,c+1,d,y+16,f)]:box(b,material)
        elif sp['kind']=='tree':
            x,z=sp['at'];y=levels[x,z]
            box((x-2,y+3,z-2,x+3,y+6,z+3),'oak_leaves[persistent=true]')
            box((x-1,y+6,z-1,x+2,y+7,z+2),'oak_leaves[persistent=true]')
            box((x,y,z,x+1,y+5,z+1),'oak_log')
        elif sp['kind']=='well':
            x,z=sp['at'];y=levels[x,z]
            box((x-1,y,z-1,x+2,y+1,z+2),'cobblestone')
            volume.set((x+ox,y+oy,z+oz),'water')


def flatten_layout(plan,site,buildings,streets,retained,level):
    """Translate whole buildings; only exterior street/parcel elevations change."""
    if type(level) is not int:raise ValueError('flat_ground_level must be an integer')
    for lot in site['lots']:lot['level']=level
    for plaza in site['plazas']:plaza['level']=level
    for key in ('forecourts','gardens'):
        for parcel in plan[key]:parcel['level']=level
    for street in streets:
        street['pts']=[[x,z,level] for x,z,_ in street['pts']]
    # validate_plan also reads the authored changed-road records.
    for street in plan['replace_streets']+plan['add_streets']:
        street['pts']=[[x,z,level] for x,z,_ in street['pts']]
    for building in buildings:
        shift=level-building['level'];building['level']=level
        building['proposed_world_min'][1]+=shift
        building['threshold_world_y']+=shift


def flat_routes(volume,walking,plan,buildings,offset):
    """Route around retained wells/props after composing the actual flat blocks."""
    ox,oy,oz=offset
    navigable=set()
    for (x,z),y in walking.items():
        if (all(passable(volume.get((x+ox,y+oy+dy,z+oz))) for dy in (0,1))
                and volume.get((x+ox,y+oy-1,z+oz)) not in (AIR,'minecraft:water')):
            navigable.add((x,z))
    start=tuple(plan['gate_start']);parents={start:None};queue=deque([start])
    if start not in navigable:raise ValueError('flat town gate is obstructed')
    while queue:
        x,z=queue.popleft()
        for point in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if point in navigable and point not in parents:
                parents[point]=(x,z);queue.append(point)
    routes={}
    for key,target in [(b['key'],b['approach']) for b in buildings]+[('market',plan['market_target'])]:
        point=tuple(target);route=[]
        if point not in parents:raise ValueError('flat town destination is unreachable: '+key)
        while point is not None:
            route.append([*point,walking[point]]);point=parents[point]
        routes[key]=route[::-1]
    return routes


def realize(config_path):
    cfg=read(config_path);parent=config_path.parent
    source=parent/cfg['plan']
    plan,site,land,buildings,streets,retained=prepare(source)
    planning=validate_plan(plan,site,land,buildings,streets,retained)
    if not planning['passed']:raise ValueError('accepted plan failed its own geometry checks')
    flat=cfg.get('flat_ground_level')
    if flat is not None:
        if flat!=cfg['water_level']+1:raise ValueError('flat streets must be one block above lake water feet')
        flatten_layout(plan,site,buildings,streets,retained,flat)
        planning=validate_plan(plan,site,land,buildings,streets,retained)
        if not planning['passed']:raise ValueError('flat layout failed exterior connectivity')
        cfg['recommended_spawn_artwork'][1]=flat
    baseline=read(parent/cfg['baseline']);offset=site['world_offset'];ox,oy,oz=offset
    x0,z0,x1,z1=baseline['window']
    volume=Volume((x0+ox,cfg['y_base'],z0+oz),(x1-1+ox,cfg['y_top'],z1-1+oz))
    levels,kinds,walking=realized_grid(plan,site,land,buildings,streets,retained,baseline)
    if flat is not None:
        levels={point:flat for point in levels}
    water_top=cfg['water_level'];bed=cfg['lake_bed_level']
    for (x,z),level in levels.items():
        kind=kinds[x,z];wx,wz=x+ox,z+oz
        wet=kind in ('water','bridge')
        ground=bed if wet else level
        body=cfg['body_art'] if kind in ('art','water','bridge','island') else cfg['body_design']
        volume.fill((wx,cfg['y_base'],wz),(wx,ground+oy-2,wz),body)
        volume.set((wx,ground+oy-1,wz),cfg['materials']['bed'] if wet else cfg['materials'][kind])
        if wet:
            volume.fill((wx,bed+oy,wz),(wx,water_top+oy-1,wz),'water')
        if kind=='bridge':volume.set((wx,max(level,water_top+1)+oy-1,wz),'spruce_planks')
    # Stairs at each rising edge face uphill, including the width of each flight.
    stair_cells={}
    for street in streets:
        local=street_cells(street)
        for (x,z),y in local.items():
            if walking.get((x,z))!=y:continue
            candidates=[(dx,dz) for dx,dz in FACE if local.get((x-dx,z-dz))==y-1
                        and walking.get((x-dx,z-dz))==y-1]
            if candidates:
                dx,dz=candidates[0]
                stair_cells[x,z]=f"{cfg['stairs']}[facing={FACE[dx,dz]},half=bottom]"
    for (x,z),state in stair_cells.items():volume.set((x+ox,walking[x,z]+oy-1,z+oz),state)
    # A closed submerged perimeter and a continuous solid bed prevent a floating
    # lake or edge spill, while leaving all above-water exits open.
    lo,hi=volume.minimum,volume.maximum
    for a,b in [((lo[0],lo[1],lo[2]),(hi[0],cfg['boundary_water_barrier_top'],lo[2])),
                ((lo[0],lo[1],hi[2]),(hi[0],cfg['boundary_water_barrier_top'],hi[2])),
                ((lo[0],lo[1],lo[2]),(lo[0],cfg['boundary_water_barrier_top'],hi[2])),
                ((hi[0],lo[1],lo[2]),(hi[0],cfg['boundary_water_barrier_top'],hi[2]))]:
        volume.fill(a,b,'stone_bricks')
    place_landmarks(volume,land,levels,offset,cfg['landmark_material'],flat if flat is not None else 38)
    old_houses=place_original_houses(volume,retained,offset)
    signs=[];realized_buildings=[]
    for b in buildings:
        data=read(parent/b['source']);size,_,blocks,_=nbt.expand_structure(data)
        minimum=b['proposed_world_min'];q=b['rotation_quarters']
        for p,state in blocks.items():
            rp=rotated_pos(p,size,q)
            volume.set(tuple(minimum[i]+rp[i] for i in range(3)),rotated_state(state,q))
        for s in data['signs']:
            sign=copy.deepcopy(s);rp=rotated_pos(s['pos'],size,q)
            sign['pos']=[minimum[i]+rp[i] for i in range(3)];signs.append(sign)
        realized_buildings.append({**b,'world_min':minimum,
            'world_max':[minimum[i]+b['size'][i]-1 for i in range(3)],
            'approach':[b['approach'][0]+ox,b['level']+oy,b['approach'][1]+oz],
            'door':[b['door'][0]+ox,b['threshold_world_y'],b['door'][1]+oz]})
    if flat is not None:planning['routes']=flat_routes(volume,walking,plan,buildings,offset)
    planning['routes']['flat_west_street' if flat is not None else 'S1a_shifted_steps']=[[x,336,walking[x,336]] for x in range(-50,-58,-1)]
    planning['routes']['flat_herb_lane' if flat is not None else 'HERB_LANE_steps']=[[x,309,walking[x,309]] for x in range(-66,-76,-1)]
    planning['routes']['south_gate_passage']=[[-30,z,flat if flat is not None else 38] for z in range(399,368,-1)]
    return cfg,plan,site,land,volume,levels,kinds,walking,realized_buildings,old_houses,signs,planning


def passable(state):
    name,props=nbt.parse_block_state(state)
    return (name in (AIR,'minecraft:cave_air') or name.endswith('_carpet') or
            name.endswith('_door') or name.endswith('_sign') or name.endswith('_wall_sign') or
            name.endswith('_pressure_plate'))


def validate_blocks(volume,walking,buildings,houses,signs,planning,offset,changed_cells=None):
    checks=[];ox,oy,oz=offset
    def check(name,ok,detail=None):checks.append({'name':name,'passed':bool(ok),'detail':detail})
    failures=[];support=[]
    for (x,z),y in walking.items():
        p=(x+ox,y+oy,z+oz)
        head=[volume.get((p[0],p[1]+dy,p[2])) for dy in (0,1)]
        if not all(passable(s) for s in head):failures.append({'pos':p,'states':head})
        if volume.get((p[0],p[1]-1,p[2])) in (AIR,'minecraft:water'):support.append(p)
    # Check the actual travelled routes; landmarks may intentionally occupy a
    # plaza cell, but may never obstruct an accepted gate-to-building route.
    route_failures=[];routes={}
    for key,path in planning['routes'].items():
        route=[[x+ox,y+oy,z+oz] for x,z,y in path];routes[key]=route
        for p in route:
            states=[volume.get((p[0],p[1]+dy,p[2])) for dy in (0,1)]
            floor=volume.get((p[0],p[1]-1,p[2]))
            if not all(passable(s) for s in states) or floor in (AIR,'minecraft:water'):
                route_failures.append({'route':key,'pos':p,'body':states,'floor':floor})
    check('accepted gate routes have real two-block clearance and floor',not route_failures,route_failures[:20])
    bad_steps=[]
    for key,route in routes.items():
        for a,b in zip(route,route[1:]):
            if a[1]==b[1]:continue
            low,high=(a,b) if a[1]<b[1] else (b,a)
            ascent=FACE[high[0]-low[0],high[2]-low[2]]
            name,props=nbt.parse_block_state(volume.get((high[0],high[1]-1,high[2])))
            if not name.endswith('_stairs') or props.get('facing')!=ascent:bad_steps.append({'route':key,'at':high})
    check('height changes on accepted routes use correctly oriented stairs',not bad_steps,bad_steps[:20])
    check('all authored streets have a solid supported walking surface',not support,support[:20])
    changed_failures=[f for f in failures if (f['pos'][0]-ox,f['pos'][2]-oz) in (changed_cells or set())]
    check('changed parcels and roads have no street clearance blockers',not changed_failures,changed_failures[:20])
    # Verify each entire new foundation is backed by solid terrain to y_base.
    for b in buildings:
        lo=b['world_min'];sx,_,sz=b['size'];bad=[]
        for x in range(lo[0],lo[0]+sx):
            for z in range(lo[2],lo[2]+sz):
                for y in range(volume.minimum[1],lo[1]):
                    if volume.get((x,y,z)) in (AIR,'minecraft:water'):
                        bad.append((x,y,z));break
        check(b['key']+': continuous foundation support',not bad,bad[:5])
        p=b['approach'];check(b['key']+': clear level approach',all(passable(volume.get((p[0],p[1]+dy,p[2]))) for dy in (0,1)))
    check('all 70 original complete house templates retained',len(houses)==70 and all(h['blocks']>=200 for h in houses))
    for house in houses:
        expected=house['_expected']
        mismatches=[p for p,state in expected.items() if volume.get(p)!=canonical(state)]
        check(house['id']+': original house blocks survive final composition',not mismatches,mismatches[:5])
    for sign in signs:check('sign block at '+str(sign['pos']),'sign' in volume.get(sign['pos']))
    house_blocks=set().union(*(set(h['_expected']) for h in houses))
    categories=Counter()
    for f in failures:
        p=f['pos']
        if any((p[0],p[1]+dy,p[2]) in house_blocks for dy in (0,1)):
            categories['preserved_original_house_frontage']+=1
        elif any('smooth_stone' in state for state in f['states']):
            categories['preserved_original_landmark']+=1
        else:categories['preserved_original_props']+=1
    return {'passed':all(c['passed'] for c in checks),'checks':checks,'routes':routes,
            'counts':{'retained_houses':len(houses),'new_buildings':len(buildings),'signs':len(signs),
                      'palette':len(volume.palette),'volume_blocks_including_air':len(volume.cells),
                      'non_air_blocks':sum(i!=0 for i in volume.cells),'street_clearance_conflicts':len(failures),'changed_parcel_street_conflicts':len(changed_failures)},
            'street_clearance_conflicts':failures[:40],'street_clearance_categories':dict(categories),
            'scope':'Offline block-grid floor, headroom, approach, foundation and export checks; live collision pending.'}


def schematic(volume,signs,path):
    encoded=bytearray()
    for i in volume.cells:export.encode_varint(i,encoded)
    w,h,d=volume.size
    root=nbt.Compound({'Version':nbt.Int(2),'DataVersion':nbt.Int(export.DEFAULT_DATA_VERSION),
        'Width':export.Short(w),'Height':export.Short(h),'Length':export.Short(d),
        'Offset':export.IntArray([0,0,0]),'PaletteMax':nbt.Int(len(volume.palette)),
        'Palette':nbt.Compound({s:nbt.Int(i) for i,s in enumerate(volume.palette)}),
        'BlockData':export.ByteArray(encoded),'Metadata':nbt.Compound(),
        'Entities':nbt.ListTag(nbt.TAG_COMPOUND,[])})
    local=[{**s,'pos':[s['pos'][i]-volume.minimum[i] for i in range(3)]} for s in signs]
    dummy=nbt.Compound({'blocks':nbt.ListTag(nbt.TAG_COMPOUND,[])})
    attach_signs(dummy,root,local)
    export.write_gzipped_nbt_ext(root,path,'Schematic')


def verify_schematic(volume,signs,path):
    """Read every full-city palette index back, including intentional clear air."""
    from buildgen.nbtread import read_gzipped_nbt
    name,data=read_gzipped_nbt(str(path))
    if name!='Schematic':raise AssertionError('wrong schematic root')
    if (data['Width'],data['Height'],data['Length'])!=volume.size:raise AssertionError('schematic size changed')
    if data['Offset']!=[0,0,0]:raise AssertionError('clipboard origin changed')
    actual=export.decode_varints(data['BlockData'])
    if len(actual)!=len(volume.cells) or any(a!=b for a,b in zip(actual,volume.cells)):
        raise AssertionError('full schematic cell roundtrip changed')
    if data['Palette']!={s:i for i,s in enumerate(volume.palette)}:raise AssertionError('palette changed')
    if len(data['BlockEntities'])!=len(signs):raise AssertionError('missing schematic signs')
    return {'full_schematic_cells_verified':len(actual),'clipboard_offset':[0,0,0],
            'sign_block_entities':len(signs),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}


def export_tiles(volume,signs,out,tile_size):
    tiles=[];w,h,d=volume.size
    datapack=out/'datapack'
    (datapack/'pack.mcmeta').parent.mkdir(parents=True,exist_ok=True)
    (datapack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Yunshan D1 v2 generated district'}})+'\n')
    palette=[]
    for state in volume.palette:
        name,props=nbt.parse_block_state(state)
        p={'Name':nbt.String(name)}
        if props:p['Properties']=nbt.Compound({k:nbt.String(v) for k,v in props.items()})
        palette.append(nbt.Compound(p))
    for y0 in range(0,h,tile_size):
        for z0 in range(0,d,tile_size):
            for x0 in range(0,w,tile_size):
                size=[min(tile_size,w-x0),min(tile_size,h-y0),min(tile_size,d-z0)]
                minimum=[volume.minimum[i]+(x0,y0,z0)[i] for i in range(3)]
                blocks=[];probe=None
                local_signs={tuple(s['pos'][i]-minimum[i] for i in range(3)):s for s in signs
                             if all(minimum[i]<=s['pos'][i]<minimum[i]+size[i] for i in range(3))}
                for y in range(size[1]):
                    for z in range(size[2]):
                        base=((y+y0)*d+z+z0)*w+x0
                        for x in range(size[0]):
                            state=volume.cells[base+x];p=(x,y,z)
                            b={'pos':nbt.ListTag(nbt.TAG_INT,[nbt.Int(v) for v in p]),'state':nbt.Int(state)}
                            if p in local_signs:
                                from yunshan.build_inn import sign_data
                                b['nbt']=nbt._nbt_value_to_tag('nbt',sign_data(local_signs[p]['messages']))
                            blocks.append(nbt.Compound(b))
                            if probe is None and state:
                                probe={'pos':[minimum[i]+p[i] for i in range(3)],'state':volume.palette[state]}
                if probe is None:probe={'pos':minimum,'state':AIR}
                root=nbt.Compound({'DataVersion':nbt.Int(export.DEFAULT_DATA_VERSION),'author':nbt.String('MyVillage Yunshan D1 v2'),
                    'size':nbt.ListTag(nbt.TAG_INT,[nbt.Int(v) for v in size]),
                    'palette':nbt.ListTag(nbt.TAG_COMPOUND,palette),'blocks':nbt.ListTag(nbt.TAG_COMPOUND,blocks),
                    'entities':nbt.ListTag(nbt.TAG_COMPOUND,[])})
                name=f't_{x0}_{y0}_{z0}';relative=f'tiles/{name}.nbt';path=out/relative
                path.parent.mkdir(parents=True,exist_ok=True);nbt.write_gzipped_nbt(root,str(path))
                dest=datapack/f'data/myvillage/structure/yunshan_d1_v2/{name}.nbt'
                dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(path.read_bytes())
                tiles.append({'id':f'myvillage:yunshan_d1_v2/{name}','nbt':relative,'world_min':minimum,'size':size,'probe':probe,
                              'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    return tiles


def path_trials(routes):
    """Short cardinal paths with an actual rise, suitable for mapped W input."""
    candidates=[];seen=set()
    direct=('S1a_shifted_steps','HERB_LANE_steps','flat_west_street','flat_herb_lane','south_gate_passage')
    for key in direct:
        if key not in routes:continue
        part=routes[key];a,b=part[0],part[-1]
        dx=(b[0]>a[0])-(b[0]<a[0]);dz=(b[2]>a[2])-(b[2]<a[2])
        candidates.append({'name':key,'start':[a[0]+.5,a[1],a[2]+.5],
            'end':[b[0]+.5,b[1],b[2]+.5],'min_y':b[1]-.1,'timeout_s':15,
            'yaw':math.degrees(math.atan2(-dx,dz)),'pitch':0})
        seen.add((tuple(a),tuple(b)))
    for key,route in routes.items():
        if key in direct:continue
        for i in range(len(route)-3):
            for count in (7,5,3):
                part=route[i:i+count+1]
                if len(part)!=count+1:continue
                dx=part[1][0]-part[0][0];dz=part[1][2]-part[0][2]
                if any(b[0]-a[0]!=dx or b[2]-a[2]!=dz for a,b in zip(part,part[1:])):continue
                if max(p[1] for p in part)==min(p[1] for p in part):continue
                if part[-1][1]<part[0][1]:
                    part=part[::-1];dx=-dx;dz=-dz
                marker=(tuple(part[0]),tuple(part[-1]))
                if marker in seen:continue
                seen.add(marker)
                candidates.append({'name':f'{key}_street_steps_{len(candidates)+1}',
                    'start':[part[0][0]+.5,part[0][1],part[0][2]+.5],
                    'end':[part[-1][0]+.5,part[-1][1],part[-1][2]+.5],
                    'min_y':part[-1][1]-.1,'timeout_s':12,
                    'yaw':math.degrees(math.atan2(-dx,dz)),'pitch':0})
                break
            else:continue
            break
        if len(candidates)>=6:break
    return candidates[:6]


def build(config_path,out):
    started=time.monotonic()
    cfg,plan,site,land,volume,levels,kinds,walking,buildings,houses,signs,planning=realize(config_path)
    changed_cells=set().union(*(rect_cells(b['site']) for b in buildings),
                                   *(set(street_cells(st)) for st in plan['replace_streets']+plan['add_streets']),
                                   *(rect_cells(c['rect']) for c in plan['forecourts']))
    report=validate_blocks(volume,walking,buildings,houses,signs,planning,site['world_offset'],changed_cells)
    out.mkdir(parents=True,exist_ok=True)
    (out/'block-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    if not report['passed']:
        raise ValueError(json.dumps([c for c in report['checks'] if not c['passed']],ensure_ascii=False))
    schematic(volume,signs,out/'yunshan_d1_v2.schem')
    export_report=verify_schematic(volume,signs,out/'yunshan_d1_v2.schem')
    tiles=export_tiles(volume,signs,out,cfg['tile_size'])
    if sum(math.prod(t['size']) for t in tiles)!=len(volume.cells):raise AssertionError('tile partition lost cells')
    ox,oy,oz=site['world_offset'];spawn=cfg['recommended_spawn_artwork']
    manifest={'schema_version':1,'id':'myvillage:yunshan_d1_v2','source_plan':cfg['plan'],
        'preview_translation':cfg.get('preview_translation',[0,0,0]),
        'world_offset':site['world_offset'],'world_min':volume.minimum,'world_max':volume.maximum,'size':volume.size,
        'schem':'yunshan_d1_v2.schem','clipboard_offset':[0,0,0],
        'tiles':tiles,'buildings':buildings,'retained_houses':[{k:v for k,v in h.items() if not k.startswith('_')} for h in houses],'routes':report['routes'],
        'path_trials':path_trials(report['routes']),
        'recommended_spawn':{'pos':[spawn[0]+ox+.5,spawn[1]+oy,spawn[2]+oz+.5],'yaw':180,'pitch':0},
        'fixture_cameras':[{'name':'south_gate_overview','pos':[2965.5,111,385.5],'yaw':180,'pitch':31},
                           {'name':'district_overview','pos':[3048.5,139,340.5],'yaw':135,'pitch':36},
                           {'name':'market','pos':[2986.5,102,194.5],'yaw':145,'pitch':25}],
        'signs':signs,'export_validation':export_report,'offline_validation':{'passed':report['passed'],'report':'block-validation.json'},
        'provenance':read(config_path.parent/cfg['baseline'])['provenance'],
        'inputs_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in [config_path,config_path.parent/cfg['plan'],config_path.parent/cfg['baseline'],
                      config_path.parent/plan['site_reference'],config_path.parent/plan['landscape_reference'],
                      Path(__file__),Path(baseline_houses.__file__),*[config_path.parent/b['source'] for b in buildings]]},
        'not_verified':['live_world_terrain','rotated_template_gameplay','owner_visual_acceptance','npc_services','sound','fps','multiplayer'],
        'generation_seconds':round(time.monotonic()-started,3)}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'out':str(out),'size':volume.size,'tiles':len(tiles),'checks':len(report['checks']),
                      'non_air':report['counts']['non_air_blocks'],'generation_seconds':manifest['generation_seconds']},ensure_ascii=False))
    return manifest


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--config',type=Path,default=ROOT/'blueprints/yunshan/district_realization_v2.json')
    p.add_argument('--out',type=Path,default=ROOT/'out/preview/yunshan-d1-v2/runtime-build')
    args=p.parse_args();build(args.config.resolve(),args.out.resolve())


if __name__=='__main__':main()
