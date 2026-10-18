#!/usr/bin/env python3
"""Generate NPC place metadata from authored Yunshan targets; no world or block writes.

Uses the existing JSON structure expander and rotation implementation, both standard-library
only. Coordinates belong to the source blueprints; layouts.json only assigns gameplay meanings.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from json_to_nbt import expand_structure, parse_block_state
from yunshan.build_functional import rotated_pos

AUTHORED = ROOT / 'blueprints/npc_life/layouts.json'
OUTPUT = ROOT / 'src/main/resources/data/myvillage/npc_life/places.json'
KINDS = frozenset(('rest', 'work', 'gather', 'market', 'social', 'entry', 'cultivate'))
ID = re.compile(r'[a-z0-9_]+')


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding='utf-8'))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def resolve_source(root: Path, name: str) -> Path:
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()) or not path.is_file():
        raise ValueError(f'Invalid blueprint source: {name}')
    return path


def layout_frame(layout: dict, root: Path) -> tuple[list[int], list[int], str, dict[str, str]]:
    """Return source-world origin, dimensions, frozen worldplan hash, and provenance."""
    if 'worldplan_district' in layout:
        from worldplan.district import descriptor
        _, frame, _ = descriptor(root)
        if layout['worldplan_district'] != 'blueprints/worldplan/rules_v4.json':
            raise ValueError('Unsupported district version')
        return frame['source_origin'], frame['size'], frame['layout_hash'], frame['sources']
    if 'worldplan_layout' in layout:
        path = resolve_source(root, layout['worldplan_layout'])
        data = read(path)
        manifest_path = resolve_source(root, data['source_manifest'])
        manifest = read(manifest_path)
        reference = manifest_path.parent / manifest['site_reference']
        sources = {str(manifest_path.relative_to(root)): digest(manifest_path),
                   str(reference.relative_to(root)): digest(reference)}
        for name in manifest['buildings']:
            source = manifest_path.parent / name
            sources[str(source.relative_to(root))] = digest(source)
        # Exactly the fingerprint grammar in tools/worldplan/generate_assets.py. This guards
        # auto-registration in old frozen worlds against newer incompatible building assets.
        frozen_hash = hashlib.sha256(json.dumps({'layout': data, 'sources': sources},
                                                sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        return data['source_origin'], data['size'], frozen_hash, {**sources, layout['worldplan_layout']: digest(path)}
    path = resolve_source(root, layout['source_manifest'])
    manifest = read(path)
    buildings = [read(path.parent / name) for name in manifest['buildings']]
    origin = [min(d['site']['world_min'][i] for d in buildings) for i in range(3)]
    maximum = [max(d['site']['world_max'][i] for d in buildings) for i in range(3)]
    sources = {layout['source_manifest']: digest(path)}
    for name in manifest['buildings']:
        source = path.parent / name
        sources[str(source.relative_to(root))] = digest(source)
    return origin, [maximum[i] - origin[i] + 1 for i in range(3)], '', sources


def compile_layout(layout: dict, root: Path = ROOT) -> dict:
    if not ID.fullmatch(layout['id']):
        raise ValueError('Invalid layout id')
    origin, dimensions, frozen_hash, sources = layout_frame(layout, root)
    places, ids, beds, occupied = [], set(), set(), set()
    placements = {}
    if 'worldplan_district' in layout:
        from worldplan.district import descriptor
        _, _, district_buildings = descriptor(root)
        placements = {b['source']: b for b in district_buildings}
    for building in layout['buildings']:
        path = resolve_source(root, building['source'])
        data = read(path)
        size, _, blocks, _ = expand_structure(data)
        sources[building['source']] = digest(path)
        placement = placements.get(path.name)
        if placements and placement is None:
            raise ValueError(f'Building is not in the approved D1 plan: {path.name}')
        minimum = placement['proposed_world_min'] if placement else data['site']['world_min']
        offset = [a - b for a, b in zip(minimum, origin)]
        quarters = placement['rotation_quarters'] if placement else data['site']['rotation_quarters']

        def position(local):
            return [a + b for a, b in zip(rotated_pos(local, size, quarters), offset)]

        for spec in building['places']:
            place_id, kind = spec['id'], spec['kind']
            if not ID.fullmatch(place_id) or place_id in ids or kind not in KINDS:
                raise ValueError(f'Invalid/duplicate place: {place_id}')
            if kind == 'gather':
                raise ValueError('Gather places require explicit administrator binding; not a building yard')
            ids.add(place_id)
            local = data['walk_targets'].get(spec['target'])
            if local is None:
                raise ValueError(f'Unknown walk target {spec["target"]} in {building["source"]}')
            x, y, z = local
            # Only known empty blueprint targets are exposed; no heightmap/roof guesses.
            if blocks.get((x, y, z)) != 'minecraft:air' or blocks.get((x, y + 1, z)) != 'minecraft:air':
                raise ValueError(f'Blocked body at {place_id}')
            if blocks.get((x, y - 1, z), 'minecraft:air') == 'minecraft:air':
                raise ValueError(f'Unsupported target {place_id}')
            target = position(local)
            if not all(0 <= target[i] < dimensions[i] for i in range(3)):
                raise ValueError(f'Place outside layout: {place_id}')
            if tuple(target) in occupied:
                raise ValueError(f'Overlapping place stations: {place_id}')
            occupied.add(tuple(target))
            checked_block, checked_at = '', [0, 0, 0]
            if spec.get('bed'):
                heads = [p for p, state in blocks.items()
                         if parse_block_state(state)[0].endswith('_bed')
                         and parse_block_state(state)[1].get('part') == 'head' and p[1] == y]
                if not heads:
                    raise ValueError(f'No real bed for {place_id}')
                nearest = min(heads, key=lambda p: (sum(abs(a-b) for a, b in zip(p, local)), p))
                checked_at = position(nearest)
                if tuple(checked_at) in beds:
                    raise ValueError(f'Two rest places share one bed: {place_id}')
                beds.add(tuple(checked_at))
                checked_block = parse_block_state(blocks[nearest])[0]
            if spec.get('service'):
                service = next((s for s in data.get('service_blocks', [])
                                if s['approach'] == spec['service']), None)
                if service is None or blocks.get(tuple(service['pos'])) != service['state']:
                    raise ValueError(f'Missing/overwritten facility for {place_id}')
                checked_at = position(service['pos'])
                checked_block = parse_block_state(service['state'])[0]
            places.append({'id': place_id, 'kind': kind, 'pos': target, 'capacity': 1,
                           'block': checked_block, 'block_pos': checked_at,
                           'source': building['source'], 'target': spec['target']})
    compiled = {'id': layout['id'], 'name': layout['name'], 'size': dimensions,
            'source_origin': origin, 'worldplan_layout_hash': frozen_hash,
            'source_hashes': dict(sorted(sources.items())), 'places': places}
    if 'worldplan_district' in layout:
        from worldplan.district import compatible_layouts, content_hash
        aliases=compatible_layouts(root)
        actual=content_hash({k:compiled[k] for k in ('size','source_origin','places')})
        if any(entry['places_sha256']!=actual for entry in aliases):
            raise ValueError('V4 compatibility alias does not match NPC facilities')
        compiled['compatible_worldplan_layout_hashes']=[entry['layout_hash'] for entry in aliases]
    return compiled


def generate(authored: dict | None = None, root: Path = ROOT) -> dict:
    authored = read(root / 'blueprints/npc_life/layouts.json') if authored is None else authored
    if authored.get('schema') != 1:
        raise ValueError('Unsupported NPC place schema')
    layouts = [compile_layout(layout, root) for layout in authored['layouts']]
    if len({layout['id'] for layout in layouts}) != len(layouts):
        raise ValueError('Duplicate layout ids')
    return {'schema': 1, 'generated_from': 'blueprints/npc_life/layouts.json',
            'coordinate_semantics': 'local feet position; bind origin is the minimum layout corner',
            'gather_semantics': 'Explicit bounded ledger resource point; no crop blocks are generated or harvested',
            'layouts': layouts}


def encoded(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def check(path: Path, data: dict) -> None:
    if not path.is_file() or path.read_text(encoding='utf-8') != encoded(data):
        raise ValueError(f'NPC place resources are missing or stale: {path}; run tools/gen_npc_life_places.py')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--out', type=Path, default=OUTPUT)
    args = parser.parse_args()
    data = generate()
    if args.check:
        check(args.out, data)
    else:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(encoded(data), encoding='utf-8')
    print(json.dumps({'layouts': len(data['layouts']), 'places': sum(len(x['places']) for x in data['layouts']),
                      'check': args.check}, ensure_ascii=False))


if __name__ == '__main__':
    main()
