#!/usr/bin/env python3
"""Production Qianzhen templates and scene geometry; no viewer, SCHEM or browser output."""
from __future__ import annotations

import argparse
from collections import deque
import copy
import hashlib
from itertools import product
import json
import math
import shutil
from pathlib import Path
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
from buildgen.nbtread import read_gzipped_nbt, state_string
from qianzhen.build import validate, DIRECTIONS
from yunshan.build_inn import canonical, sign_data

SOURCE = ROOT / 'blueprints/qianzhen'
OPENING_SOURCE = ROOT / 'blueprints/qianzhen_opening'
FACILITIES = {'testing_stele', 'inheritance_stele', 'apprentice_plot'}
DEFAULT_OUT = ROOT / 'build/generated/qianzhen/resources'
ROLES = {'spawn', 'guide', 'pharmacist', 'zhou_he', 'zhou_he_home', 'warden'}
TARGETS = {'water_sample', 'withered_crop', 'sample_examination', 'medicine_chest',
           'transport_record', 'recut_array', 'fresh_cut', 'old_array_text', 'spring_core'}


def add(a, b):
    return tuple(x+y for x, y in zip(a, b))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def state_at(blocks, pos):
    return nbt.parse_block_state(blocks.get(tuple(pos), 'minecraft:air'))


def sightline_cells(start, end):
    """Cells crossed by a segment, ordered by entry; zero-length tangencies excluded."""
    crossed = []
    ranges = [range(math.floor(min(a, b)), math.floor(max(a, b))+1)
              for a, b in zip(start, end)]
    for cell in product(*ranges):
        entry, leave = 0., 1.
        for axis, low in enumerate(cell):
            delta = end[axis]-start[axis]
            if delta == 0:
                if not low <= start[axis] < low+1:
                    leave = -1.
                    break
            else:
                near, far = sorted(((low-start[axis])/delta, (low+1-start[axis])/delta))
                entry, leave = max(entry, near), min(leave, far)
        if leave-entry > 1e-12:
            crossed.append((entry, cell))
    return [cell for _, cell in sorted(crossed)]


def check_target_sightline(blocks, key, pos, approach):
    """Reject intervening non-air cells on the same center-aim ray as the collector.

    This deliberately treats partial blocks (including brewing stands and doors)
    as whole-cell obstacles. Only the destination cell is excluded: cauldrons,
    chests and lecterns need their real outline hit tested in Minecraft, not an
    invented VoxelShape here. Passing proves a clear corridor to that cell only.
    """
    eye = [value+(.5 if axis != 1 else 1.62) for axis, value in enumerate(approach)]
    click_point = [value+.5 for value in pos]
    intermediate = [cell for cell in sightline_cells(eye, click_point) if cell != tuple(pos)]
    for cell in intermediate:
        name, _ = state_at(blocks, cell)
        if name not in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
            raise ValueError(f'target sightline obstructed: {key} at {cell} by {blocks[cell]}')
    return {'eye':eye, 'click_point':click_point, 'intermediate_cells':[list(p) for p in intermediate]}


def empty(blocks, pos):
    name, _ = state_at(blocks, pos)
    return name in ('minecraft:air', 'minecraft:cave_air') or name.endswith('_door')


def support(blocks, pos):
    name, props = state_at(blocks, pos)
    if empty(blocks, pos) or name in ('minecraft:water', 'minecraft:lava'):
        return False
    if any(word in name for word in ('fence', 'bed', 'lantern', 'sign', 'pressure_plate',
                                    'torch', 'ladder', 'chain', 'potted_', 'trapdoor')):
        return False
    return not name.endswith('_slab') or props.get('type') == 'double'


def graph_for(blocks):
    """Same conservative stair topology as the blueprint checker, on sparse global cells."""
    nodes = {add(p, (0, 1, 0)) for p in blocks if support(blocks, p)
             and empty(blocks, add(p, (0, 1, 0))) and empty(blocks, add(p, (0, 2, 0)))}
    graph = {}
    for x, y, z in nodes:
        neighbours = []
        for direction, (dx, dz) in DIRECTIONS.items():
            for dy in (0, 1, -1):
                p = (x+dx, y+dy, z+dz)
                if p not in nodes:
                    continue
                if dy:
                    high = p if dy > 0 else (x, y, z)
                    name, props = state_at(blocks, add(high, (0, -1, 0)))
                    ascent = direction if dy > 0 else next(k for k, v in DIRECTIONS.items() if v == (-dx, -dz))
                    low = (x, y, z) if dy > 0 else p
                    if not name.endswith('_stairs') or props.get('facing') != ascent or props.get('half', 'bottom') != 'bottom':
                        continue
                    if not empty(blocks, (low[0], high[1]+1, low[2])):
                        continue
                neighbours.append(p)
        graph[x, y, z] = neighbours
    return graph


def reachable(graph, start):
    if start not in graph:
        raise ValueError('unsupported or obstructed start: '+str(start))
    seen, todo = {start}, deque([start])
    while todo:
        for pos in graph[todo.popleft()]:
            if pos not in seen:
                seen.add(pos)
                todo.append(pos)
    return seen


def road_blocks(config, sites):
    width = config['width']
    if width != 3 or config['clearance'] < 3:
        raise ValueError('roads must have width three and at least three clear blocks')
    radius, result = width//2, {}

    def inside_site(x, z):
        return any(s['origin'][0] <= x < s['origin'][0]+s['size'][0]
                   and s['origin'][2] <= z < s['origin'][2]+s['size'][2] for s in sites)

    for path in config['paths']:
        if len(path['points']) < 2:
            raise ValueError('road needs two endpoints: '+path['id'])
        for a, b in zip(path['points'], path['points'][1:]):
            if a[1] != 0 or b[1] != 0:
                raise ValueError('road surfaces must be on the authored ground plane')
            for x, y, z in nbt.iter_line(tuple(a), tuple(b)):
                for dx in range(-radius, radius+1):
                    for dz in range(-radius, radius+1):
                        if inside_site(x+dx, z+dz):
                            continue  # Buildings own their whole volume, including their explicit air.
                        result[x+dx, y, z+dz] = config['surface']
                        for dy in range(1, config['clearance']+1):
                            result[x+dx, y+dy, z+dz] = 'minecraft:air'
    for step in config['steps']:
        pos = tuple(step['pos'])
        if result.get(pos) != 'minecraft:air' or not support(result, add(pos, (0, -1, 0))):
            raise ValueError('step must be supported in the road clearance: '+str(pos))
        result[pos] = step['state']
    return result


def assemble(source=SOURCE):
    manifest = json.loads((source/'manifest.json').read_text())
    config = json.loads((source/'story_scene.json').read_text())
    if config['schema'] != 1 or set(config['positions']) != ROLES or set(config['targets']) != TARGETS:
        raise ValueError('scene role/target contract differs from the chapter API')
    if 'facilities' in config and set(config['facilities']) != FACILITIES:
        raise ValueError('scene facility contract must declare all three opening facilities')
    sites, authored, by_id, global_blocks, checks = [], {}, {}, {}, {}
    for row in manifest['locations']:
        site = row['id']
        data = json.loads((source/row['source']).read_text())
        for prop in config['props']:
            if prop['site'] == site:
                _, _, original, _ = nbt.expand_structure(data)
                nbt.validate_pos(prop['pos'], data['size'], 'prop.'+prop['id'])
                if canonical(original[tuple(prop['pos'])]) != canonical(prop['replace']):
                    raise ValueError('authored prop would replace another block: '+prop['id'])
                data['ops'].append({'op':'set', 'pos':prop['pos'], 'state':prop['state']})
        blocks, _, checked = validate(data, row['max_size'])
        checks[site] = {'walk_targets': len(checked['walks']['targets']), 'reachable_cells':checked['walks']['reachable_cells']}
        metadata = {'id':site, 'template':'myvillage:qianzhen/'+site,
                    'origin':row['origin'], 'size':data['size']}
        for p, state in blocks.items():
            at = add(row['origin'], p)
            if at in global_blocks:
                raise ValueError('overlapping site geometry: '+site)
            global_blocks[at] = state
        sites.append(metadata)
        by_id[site] = (data, blocks, row['origin'])
        authored[site] = data
    roads = road_blocks(config['roads'], sites)
    if set(roads) & set(global_blocks):
        raise ValueError('road overwrites building geometry')
    global_blocks.update(roads)

    def resolve(spec):
        data, blocks, origin = by_id[spec['site']]
        nbt.validate_pos(spec['pos'], data['size'], 'scene.pos')
        approach = spec['approach'] if 'approach' in spec else data['walk_targets'][spec['walk_target']]
        nbt.validate_pos(approach, data['size'], 'scene.approach')
        return blocks, list(add(origin, spec['pos'])), list(add(origin, approach))

    positions, targets, facilities, sightlines, facility_sightlines = {}, [], [], {}, {}
    for key, spec in config['positions'].items():
        _, pos, approach = resolve(spec)
        positions[key] = {'pos':pos, 'approach':approach}
    for key, spec in config['targets'].items():
        blocks, pos, approach = resolve(spec)
        state = blocks[tuple(spec['pos'])]
        if nbt.parse_block_state(state)[0] != spec['block']:
            raise ValueError('target is not the declared clickable block: '+key)
        if sum((pos[i]+.5-(approach[i]+(.5 if i != 1 else 1.62)))**2 for i in range(3)) > 4.5**2:
            raise ValueError('target too far from approach: '+key)
        sightlines[key] = check_target_sightline(global_blocks, key, pos, approach)
        targets.append({'id':key, 'block':spec['block'], 'state':state, 'pos':pos, 'approach':approach})
    for key, spec in config.get('facilities', {}).items():
        blocks, pos, approach = resolve(spec)
        state = blocks[tuple(spec['pos'])]
        if nbt.parse_block_state(state)[0] != spec['block'] or empty(blocks, spec['pos']):
            raise ValueError('facility is not the declared solid block: '+key)
        if pos == approach or sum((pos[i]+.5-(approach[i]+(.5 if i != 1 else 1.62)))**2 for i in range(3)) > 4.5**2:
            raise ValueError('facility too far from or overlaps approach: '+key)
        facility_sightlines[key] = check_target_sightline(global_blocks, key, pos, approach)
        facilities.append({'id':key, 'block':spec['block'], 'state':state, 'pos':pos, 'approach':approach})

    graph = graph_for(global_blocks)
    seen = reachable(graph, tuple(positions['spawn']['pos']))
    required = {site+':'+key:add(origin, pos) for site, (data, _, origin) in by_id.items()
                for key, pos in data['walk_targets'].items()}
    required.update({key+':approach':tuple(value['approach']) for key, value in positions.items()})
    required.update({target['id']:tuple(target['approach']) for target in targets})
    required.update({facility['id']+':approach':tuple(facility['approach']) for facility in facilities})
    required.update({key+':feet':tuple(value['pos']) for key, value in positions.items()})
    missing = [key for key, pos in required.items() if pos not in seen]
    if missing:
        raise ValueError('disconnected scene geometry: '+', '.join(missing))
    # Reserve all five NPC bodies, including both mutually exclusive Zhou He locations.
    # Prove that these safe neighbours do not break any player approach or inter-site route.
    occupied = copy.copy(global_blocks)
    for role, value in positions.items():
        if role != 'spawn':
            p = tuple(value['pos'])
            occupied[p] = occupied[add(p, (0, 1, 0))] = 'minecraft:stone'
    clear = reachable(graph_for(occupied), tuple(positions['spawn']['pos']))
    blocked = [key for key, pos in required.items() if not key.endswith(':feet') and pos not in clear]
    # Zhou He's authored bedside is also his NPC foot; its player approach is the adjacent safe tile.
    blocked = [key for key in blocked if key != 'apothecary:zhou_he_bedside']
    if blocked:
        raise ValueError('NPC reservation blocks player routes: '+', '.join(blocked))
    for facility in facilities:
        check_target_sightline(occupied, facility['id'], facility['pos'], facility['approach'])
    for target in targets:
        positions[target['id']] = {'pos':target['pos'], 'approach':target['approach']}
    for facility in facilities:
        positions[facility['id']] = {'pos':facility['pos'], 'approach':facility['approach']}
    inputs = {'manifest.json':sha(source/'manifest.json'), 'story_scene.json':sha(source/'story_scene.json')}
    inputs.update({row['source']:sha(source/row['source']) for row in manifest['locations']})
    fingerprint = hashlib.sha256(json.dumps(inputs, sort_keys=True).encode()).hexdigest()
    mins = [min(p[i] for p in global_blocks) for i in range(3)]
    maxs = [max(p[i] for p in global_blocks) for i in range(3)]
    layout = {'schema':1, 'id':config['id'], 'version':config['version']+'-'+fingerprint[:16],
              'inputs':inputs, 'sites':sites, 'bounds':{'min':mins, 'max':maxs}, 'positions':positions,
              'targets':targets, 'roads':[{'pos':list(pos), 'state':state} for pos, state in sorted(roads.items())]}
    if 'facilities' in config:
        layout['facilities'] = facilities
    report = {'status':'passed', 'scene_version':layout['version'], 'sites':checks,
              'road_columns':sum(p[1] == 0 for p in roads), 'road_cells':len(roads),
              'stair_cells':len(config['roads']['steps']), 'road_building_overlap':0,
              'reachable_cells':len(seen), 'connected_stations':len(required),
              'all_player_approaches_reachable_with_npcs':True,
              'target_sightlines':sightlines,
              'facility_sightlines':facility_sightlines, 'facilities':facilities,
              'targets':targets, 'positions':positions,
              'method':'conservative block topology, doors opened, stair-only height transitions; every original walk target plus story feet/approaches; eye-to-center rays reject intervening non-air cells, destination outline not simulated',
              'not_verified':['native_collision', 'native_target_outline_hit', 'continuous_mapped_player_walk', 'deployment_runtime',
                              'multiplayer', 'restart_persistence', 'owner_visual_acceptance']}
    return layout, authored, report


def generate(out, source=SOURCE):
    layout, authored, report = assemble(source)
    for site in layout['sites']:
        data = authored[site['id']]
        root = nbt.structure_json_to_root_nbt(data)
        signs = {tuple(s['pos']):sign_data(s['messages']) for s in data.get('signs', [])}
        for block in root.value['blocks'].value:
            pos = tuple(v.value for v in block.value['pos'].value)
            if pos in signs:
                block.value['nbt'] = nbt._nbt_value_to_tag('nbt', signs[pos])
        path = out/'data/myvillage/structure/qianzhen'/(site['id']+'.nbt')
        nbt.write_gzipped_nbt(root, str(path))
        _, readback = read_gzipped_nbt(str(path))
        actual = {tuple(b['pos']):canonical(state_string(readback['palette'][b['state']])) for b in readback['blocks']}
        _, _, expanded, _ = nbt.expand_structure(data)
        if actual != {pos:canonical(state) for pos, state in expanded.items()}:
            raise ValueError('production NBT roundtrip differs: '+site['id'])
        for pos, sign in signs.items():
            if next(b.get('nbt') for b in readback['blocks'] if tuple(b['pos']) == pos) != sign:
                raise ValueError('production sign NBT differs: '+site['id'])
        site['sha256'] = sha(path)
    # Identity covers generated geometry and exact template bytes as well as authored sources.
    # A future generator change cannot silently reuse a completed scene's saved identity.
    content = {key:value for key, value in layout.items() if key != 'version'}
    layout['version'] = layout['version'].rsplit('-', 1)[0]+'-'+hashlib.sha256(
        json.dumps(content, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:16]
    report['scene_version'] = layout['version']
    path = out/'data/myvillage/story/qianzhen_scene.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(layout, ensure_ascii=False, indent=2)+'\n')
    return report


def generate_opening(out):
    """Overlay authored apprentice facilities without changing the frozen V6 building source."""
    with tempfile.TemporaryDirectory(prefix='qianzhen-opening-source-') as folder:
        source=Path(folder)
        for item in SOURCE.glob('*.json'):shutil.copy2(item,source/item.name)
        for item in OPENING_SOURCE.glob('*.json'):shutil.copy2(item,source/item.name)
        return generate(out,source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    parser.add_argument('--opening', action='store_true', help='generate the private apprentice scene')
    parser.add_argument('--check', action='store_true', help='regenerate to a temporary directory and compare every owned resource')
    parser.add_argument('--report', type=Path, help='optional geometry/road report outside packaged resources')
    args = parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory(prefix='qianzhen-story-check-') as folder:
            expected = Path(folder)
            report = generate_opening(expected) if args.opening else generate(expected)
            files = {p.relative_to(expected) for p in expected.rglob('*') if p.is_file()}
            actual = {p.relative_to(args.out) for p in (args.out/'data/myvillage/structure/qianzhen').glob('*.nbt')}
            scene_path = Path('data/myvillage/story/qianzhen_scene.json')
            if (args.out/scene_path).exists():
                actual.add(scene_path)
            differences = sorted(str(p) for p in files | actual
                                 if not (expected/p).is_file() or not (args.out/p).is_file()
                                 or (expected/p).read_bytes() != (args.out/p).read_bytes())
            if differences:
                parser.exit(1, 'stale Qianzhen resources: '+', '.join(differences)+'\n')
    else:
        report = generate_opening(args.out) if args.opening else generate(args.out)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status':'passed', 'mode':'check' if args.check else 'generate',
                      'version':report['scene_version'], 'road_columns':report['road_columns'],
                      'connected_stations':report['connected_stations']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
