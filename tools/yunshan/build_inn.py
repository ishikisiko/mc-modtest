#!/usr/bin/env python3
"""Export the authored, standalone Yunshan inn; never writes the old D1 site.

No settlement family or runtime content is registered. The JSON DSL owns every
block and site coordinate. Outputs are review candidates, not game captures.
"""
from __future__ import annotations

import argparse
from collections import deque
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from buildgen.nbtread import read_gzipped_nbt, state_string
from validate_blueprint import validate_blueprint


def canonical(state):
    name, props = nbt.parse_block_state(state)
    return name + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


def check_roof(blocks, spec, size):
    """Trace the roof shell in half-block cross sections, including stair shape.

    Eight-neighbour connectivity allows the edge contact of normal stair roofs,
    but rejects a real half-block air gap. This is not a lighting simulation.
    """
    xmin, xmax = spec['x_range']
    checked = []
    for z in range(spec['z_range'][0], spec['z_range'][1]+1):
        solid = set()
        for x in range(xmin, xmax+1):
            for y in range(spec['min_y'], size[1]):
                name, props = nbt.parse_block_state(blocks.get((x,y,z), 'minecraft:air'))
                if name == 'minecraft:air':
                    continue
                for a in (0,1):
                    for b in (0,1):
                        if name.endswith('_slab') and props.get('type') != 'double':
                            if b != (1 if props.get('type') == 'top' else 0):
                                continue
                        elif name.endswith('_stairs'):
                            assert props.get('facing') in ('east','west'), 'unsupported roof stair axis'
                            base = 1 if props.get('half') == 'top' else 0
                            high_x = 1 if props.get('facing') == 'east' else 0
                            if b != base and a != high_x:
                                continue
                        solid.add((2*x+a,2*y+b))
        seen = {p for p in solid if p[0] == 2*xmin}
        todo = deque(seen)
        while todo:
            x,y = todo.popleft()
            for dx in (-1,0,1):
                for dy in (-1,0,1):
                    p = (x+dx,y+dy)
                    if p in solid and p not in seen:
                        seen.add(p)
                        todo.append(p)
        assert any(x == 2*xmax+1 for x,y in seen), f'roof shell gap at z={z}'
        checked.append(z)
    return {'connected_sections': checked, 'resolution_blocks': 0.5}


def check_walks(blocks, targets, size):
    """Conservative two-block headroom, doors opened, stair-only elevation gain.

    This checks block topology, not Minecraft collision or player acceptance.
    Furniture and thin decoration are conservatively treated as full obstacles.
    """
    def empty(p):
        s = blocks.get(p, 'minecraft:air')
        return s == 'minecraft:air' or s.split('[')[0].endswith('_door')

    def support(p):
        s = blocks.get(p, 'minecraft:air')
        name, props = nbt.parse_block_state(s)
        return not empty(p) and not any(t in name for t in ('fence', 'bed', 'lantern', 'sign', 'slab', 'pressure_plate'))

    nodes = {(x, y, z) for x in range(size[0]) for z in range(size[2]) for y in range(1, size[1]-1)
             if empty((x, y, z)) and empty((x, y+1, z)) and support((x, y-1, z))}
    start = tuple(targets['entry'])
    assert start in nodes, 'entry obstructed'
    seen, todo = {start}, deque([start])
    step_dir = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}
    while todo:
        x, y, z = todo.popleft()
        for (dx, dz), direction in step_dir.items():
            for dy in (0, 1, -1):
                p = (x+dx, y+dy, z+dz)
                if p not in nodes or p in seen:
                    continue
                if dy:
                    high = p if dy > 0 else (x, y, z)
                    name, props = nbt.parse_block_state(blocks[(high[0], high[1]-1, high[2])])
                    ascent = direction if dy > 0 else step_dir[(-dx, -dz)]
                    if not name.endswith('_stairs') or props.get('facing') != ascent:
                        continue
                    low = (x, y, z) if dy > 0 else p
                    if not empty((low[0], high[1]+1, low[2])):
                        continue
                seen.add(p)
                todo.append(p)
    result = {name: tuple(p) in seen for name, p in targets.items()}
    assert all(result.values()), f'unreachable: {result}'
    return result


def sign_data(lines):
    return {
        'id': 'minecraft:sign',
        'front_text': {'messages': [json.dumps({'text': t}, ensure_ascii=False) for t in lines],
                       'color': 'black', 'has_glowing_text': False},
        'back_text': {'messages': ['{"text":""}'] * 4, 'color': 'black', 'has_glowing_text': False},
        'is_waxed': False,
    }


def attach_signs(structure_root, schematic_root, signs):
    sign_map = {tuple(s['pos']): sign_data(s['messages']) for s in signs}
    for block in structure_root.value['blocks'].value:
        pos = tuple(v.value for v in block.value['pos'].value)
        if pos in sign_map:
            block.value['nbt'] = nbt._nbt_value_to_tag('nbt', sign_map[pos])
    entries = []
    for pos, data in sign_map.items():
        tags = {k: nbt._nbt_value_to_tag(k, v) for k, v in data.items() if k != 'id'}
        tags.update({'Id': nbt.String(data['id']), 'Pos': export.IntArray(pos)})
        entries.append(nbt.Compound(tags))
    schematic_root.value['BlockEntities'] = nbt.ListTag(nbt.TAG_COMPOUND, entries)


def verify_exports(blocks, nbt_path, schem_path, signs):
    _, root = read_gzipped_nbt(str(nbt_path))
    actual = {tuple(b['pos']): canonical(state_string(root['palette'][b['state']])) for b in root['blocks']}
    expected = {p: canonical(s) for p, s in blocks.items()}
    assert actual == expected, 'NBT differs from source'
    _, schem = read_gzipped_nbt(str(schem_path))
    palette = {v: canonical(k) for k, v in schem['Palette'].items()}
    ids = export.decode_varints(schem['BlockData'])
    w, h, length = schem['Width'], schem['Height'], schem['Length']
    actual = {(x,y,z): palette[ids[export.schem_index(x,y,z,w,length)]]
              for y in range(h) for z in range(length) for x in range(w)}
    assert actual == expected, 'schematic differs from source'
    assert len(schem['BlockEntities']) == len(signs)
    for sign in signs:
        pos = sign['pos']
        a = next(b['nbt'] for b in root['blocks'] if b['pos'] == pos)
        b = next(b for b in schem['BlockEntities'] if b['Pos'] == pos)
        for key in ('front_text', 'back_text', 'is_waxed'):
            assert a[key] == b[key]
        assert [json.loads(s)['text'] for s in a['front_text']['messages']] == sign['messages']


def build(source, out):
    data = json.loads(source.read_text())
    size, _, blocks, _ = nbt.expand_structure(data)
    walks = check_walks(blocks, data['walk_targets'], size)
    roof = check_roof(blocks, data['roof_check'], size) if 'roof_check' in data else 'not_checked_legacy'
    structure = export.Structure(blocks)
    assert list(structure.size) == size
    out.mkdir(parents=True, exist_ok=True)
    stem = out / data['id'].split(':')[1]
    blueprint = export.build_blueprint(structure, data['id'], {
        'source': str(source.relative_to(ROOT)), 'site': data['site'],
        'signs': data['signs'], 'status': 'offline_candidate_not_placed',
        'air': 'explicit; replacement paste must include air',
    })
    assert not validate_blueprint(blueprint)
    export.write_blueprint(blueprint, stem.with_suffix('.blueprint.json'))
    root = nbt.structure_json_to_root_nbt(data)
    schem, _ = export.build_schem(structure, export.DEFAULT_DATA_VERSION)
    attach_signs(root, schem, data['signs'])
    nbt.write_gzipped_nbt(root, str(stem.with_suffix('.nbt')))
    export.write_gzipped_nbt_ext(schem, stem.with_suffix('.schem'), 'Schematic')
    verify_exports(blocks, stem.with_suffix('.nbt'), stem.with_suffix('.schem'), data['signs'])
    report = {'id': data['id'], 'size': size, 'non_air_blocks': sum(s != 'minecraft:air' for s in blocks.values()),
              'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'walk_targets': walks, 'walk_check': 'conservative block topology; doors assumed open; no jumping',
              'roof_shell': roof,
              'export_roundtrip': 'all cells and sign text match in NBT and Sponge v2',
              'site': data['site'],
              'not_verified': ['actual_game_placement', 'player_collision', 'visual_acceptance', 'NPC_services', 'FPS']}
    (out/'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    # Preview renderer reads this normalized DSL, not the blueprint-v1 format.
    (out/'preview.json').write_text(json.dumps(data, ensure_ascii=False)+'\n')
    template = (ROOT/'tools/yunshan/inn_viewer.html').read_text()
    version = data['id'].rsplit('_',1)[-1]
    template = template.replace('yunshan_south_gate_inn_v1', data['id'].split(':')[1])
    template = template.replace('独立蓝图 v1', '独立蓝图 ' + version)
    template = template.replace('第一栋功能建筑 · v1', '第一栋功能建筑 · ' + version)
    template = template.replace('href="yunshan_d1_before_inn_v1.zip"',
                                'href="../yunshan-inn-v1/yunshan_d1_before_inn_v1.zip"')
    payload = {'blocks': [[*p, s] for p, s in blocks.items()], 'signs': data['signs']}
    html = template.replace('/*THREE*/', (ROOT/'tools/web/three.min.js').read_text())
    html = html.replace('/*ORBIT*/', (ROOT/'tools/web/OrbitControls.js').read_text())
    html = html.replace('/*DATA*/', json.dumps(payload, ensure_ascii=False))
    (out/'index.html').write_text(html)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT/'blueprints/yunshan/south_gate_inn_v2.json')
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/yunshan-inn-v2')
    args = parser.parse_args()
    build(args.source.resolve(), args.out.resolve())
