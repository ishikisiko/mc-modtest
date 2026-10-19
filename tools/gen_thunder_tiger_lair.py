#!/usr/bin/env python3
"""Generate the authored natural Thunderhorn Tiger valley and its native worldgen closure."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from buildgen.groups import get_group
from buildgen.modset import load_modset
from gen_danxia_ruin import template_bytes

DATA = ROOT / 'src/main/resources/data/myvillage'
ASSETS = ROOT / 'src/main/resources/assets/myvillage'
IDENTITY = 'thunder_tiger_lair'
RULES = DATA / f'boss/{IDENTITY}.json'


def blueprint():
    r = json.loads(RULES.read_text())
    group = get_group(IDENTITY)
    assert {'valley', 'arena', 'pillar'} <= set(group.archetype_roster)
    w, h, d = r['size']
    sy = r['surface_y']
    p, v = r['palette'], r['valley']
    cx, cz = v['center']
    rx, rz = v['radius']
    blocks = {}

    def put(x, y, z, state):
        assert 0 <= x < w and 0 <= y < h and 0 <= z < d, (x, y, z)
        blocks[x, y, z] = state

    # A filled U-shaped shoulder surrounds an open, broad fighting floor. The contour is
    # irregular but continuous; rock is a filled body, never a hollow heightfield shell.
    for x in range(w):
        for z in range(d):
            edge = min(x, w - 1 - x, z, d - 1 - z)
            if edge == 0 and (x * 13 + z * 7) % 5 > 1:
                continue
            ellipse = math.sqrt(((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2)
            ridge_distance = abs(ellipse - 1.13) * min(rx, rz)
            ridge = max(0, 1 - ridge_distance / v['ridge_width'])
            ridge *= min(1, max(0, (ellipse - v['arena_radius']) / v['inner_slope_width']))
            undulation = 0.80 + 0.12 * math.sin(x * .47 + z * .29) + 0.08 * math.cos(z * .61)
            rise = int(v['ridge_height'] * ridge * undulation) if ellipse > .80 else 0
            # Keep the full central arena flat and leave a generous open valley mouth.
            entrance = z <= v['entrance_depth'] and abs(x - cx) <= v['entrance_half_width']
            if entrance:
                rise = 0
            shoulder = min(sy, max(0, sy - max(0, 3 - edge)))
            top = shoulder + rise
            for y in range(top + 1):
                material = p['rock']
                if y == top and rise == 0:
                    patch = math.sin(x * .21 + z * .12) + .55 * math.cos(z * .27 - x * .09)
                    if patch > v['earth_patch_threshold']:
                        material = p['earth']
                    elif patch < v['gravel_patch_threshold']:
                        material = p['worn']
                    elif math.sin(x * .31 - z * .17) > .65:
                        material = p['pillar']
                elif y > sy and (x * 7 + y * 3 + z) % 11 < 3:
                    material = p['pillar']
                put(x, y, z, material)
            # Native jigsaw clears only newly generated template chunks; runtime never
            # clears player blocks. Authored air keeps mountain slopes out of the arena.
            for y in range(top + 1, h):
                put(x, y, z, 'minecraft:air')

    # Continuous level approach, with full support under the entire boss footprint.
    for x in range(cx - v['entrance_half_width'], cx + v['entrance_half_width'] + 1):
        for z in range(v['entrance_depth'] + 1):
            for y in range(sy + 1):
                put(x, y, z, p['pillar'] if y == sy else p['rock'])
            for y in range(sy + 1, h):
                put(x, y, z, 'minecraft:air')
    for px, py, pz in r['pillars']:
        for dx in range(-r['pillar_radius'], r['pillar_radius'] + 1):
            for dz in range(-r['pillar_radius'], r['pillar_radius'] + 1):
                for dy in range(r['pillar_height']):
                    put(px + dx, py + dy, pz + dz, p['pillar'] if (dx + dz + dy) % 3 else p['rock'])
    anchor = tuple(r['anchor'])
    put(*anchor, 'myvillage:thunder_tiger_lair_anchor[facing=north]')
    errors = load_modset('vanilla').palette_block_errors(sorted(set(blocks.values())))
    if errors:
        raise ValueError(errors)
    data = {'size': r['size'], 'author': 'gen_thunder_tiger_lair.py',
            'blocks': [{'pos': list(pos), 'state': state} for pos, state in sorted(blocks.items())]}
    bes = {anchor: {'id': 'myvillage:thunder_tiger_lair_anchor', 'LairState': 'READY'}}
    return r, data, bes, []


def outputs():
    r, data, bes, entities = blueprint()
    files = {DATA / f'structure/{IDENTITY}.nbt': template_bytes(data, bes, entities)}
    def emit(path, value):
        files[path] = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
    emit(DATA / f'worldgen/structure/{IDENTITY}.json', {
        'type': 'minecraft:jigsaw', 'biomes': f'#myvillage:has_{IDENTITY}',
        'step': 'surface_structures', 'terrain_adaptation': 'beard_thin', 'spawn_overrides': {},
        'start_pool': f'myvillage:{IDENTITY}', 'size': 1,
        'start_height': {'absolute': -r['surface_y']}, 'project_start_to_heightmap': 'WORLD_SURFACE_WG',
        'max_distance_from_center': 80, 'use_expansion_hack': False})
    emit(DATA / f'worldgen/template_pool/{IDENTITY}.json', {
        'name': f'myvillage:{IDENTITY}', 'fallback': 'minecraft:empty', 'elements': [{
            'weight': 1, 'element': {'element_type': 'minecraft:single_pool_element',
                'location': f'myvillage:{IDENTITY}', 'processors': 'minecraft:empty',
                'projection': 'rigid', 'override_liquid_settings': r['liquid_settings']}}]})
    emit(DATA / f'worldgen/structure_set/{IDENTITY}.json', {
        'structures': [{'structure': f'myvillage:{IDENTITY}', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', **r['placement']}})
    emit(DATA / f'tags/worldgen/biome/has_{IDENTITY}.json', {'replace': False, 'values': r['biomes']})
    emit(ASSETS / 'models/block/thunder_tiger_lair_anchor.json', {
        'parent': 'minecraft:block/cube_all', 'textures': {'all': 'minecraft:block/stone'}})
    emit(ASSETS / 'blockstates/thunder_tiger_lair_anchor.json', {
        'variants': {f'facing={face}': {'model': 'myvillage:block/thunder_tiger_lair_anchor', 'y': turn}
                     for face, turn in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    drift = []
    for path, content in outputs().items():
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if drift:
        raise SystemExit('Thunder tiger lair resource drift: ' + ', '.join(drift))
    print('Thunder tiger lair resources checked' if args.check else 'Thunder tiger lair resources generated')


if __name__ == '__main__':
    main()
