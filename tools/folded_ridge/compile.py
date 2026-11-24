"""编译确认的折山岭体素、夹层植物点位和游戏方块资源。"""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct

import numpy as np

from .geometry import NX, NY, NZ, PALETTE, ROUTE, SOILS, STEP, X0, Z0, build_volume, cell_at, check_footpath
from ..gen_qingfeng_sword_model import encode_png

ROOT = Path(__file__).resolve().parents[2]
PLANTS = ROOT / 'blueprints/worldplan/folded_ridge/plants.json'
TERRAIN = 'data/yuanxian/worldplan/folded_ridge.bin.gz'
DIRECTIONS = ((0, 1, 0), (0, -1, 0), (0, 0, -1), (1, 0, 0), (0, 0, 1), (-1, 0, 0))


def terrain(out):
    volume, distance, floor = build_volume()
    walkable = check_footpath(volume, distance, floor)
    plants = json.loads(PLANTS.read_text())
    positions = {}

    def stratum(x, y, z):
        i, j = cell_at(x, z)
        return int(volume[i, y, j]) if 0 <= i < NZ and 0 <= j < NX and 0 <= y < NY else 0

    def add(x, y, z, dx, dy, dz, material, supports):
        if (x, y, z) in positions or stratum(x, y, z) or stratum(x-dx, y-dy, z-dz) not in supports:
            raise ValueError(f'植物在 {(x, y, z)} 处重叠或缺少基底')
        positions[x, y, z] = material

    for x, y, z in plants['lamps']:
        add(x, y, z, 0, 1, 0, 13, range(1, len(PALETTE)+1))
    for x, y, z, dx, dy, dz, _size, _seed in plants['ferns']:
        x, y, z = int(x) + min(0, dx), int(y) + min(0, dy), int(z) + min(0, dz)
        # Native blocks cannot share a cell. Shift a coincident fern along the same rock face.
        if (x, y, z) in positions:
            candidates = ((x-1, y, z), (x+1, y, z), (x, y, z-1), (x, y, z+1))
            x, y, z = next(p for p in candidates if p not in positions and not stratum(*p)
                           and stratum(p[0]-dx, p[1]-dy, p[2]-dz) in SOILS)
        add(x, y, z, dx, dy, dz, 14 + DIRECTIONS.index((dx, dy, dz)), SOILS)

    entry_x, _, entry_z = map(int, ROUTE[0])
    entry_y = int(floor[cell_at(entry_x, entry_z)])
    packed = struct.pack('>4s6hi3h', b'FRD1', NX, NY, NZ, X0, Z0, STEP, len(positions), entry_x, entry_y, entry_z)
    packed += np.ascontiguousarray(volume.transpose(0, 2, 1)).tobytes()
    packed += b''.join(struct.pack('>3hB', *p, value) for p, value in sorted(positions.items()))
    data = gzip.compress(packed, mtime=0)
    path = out / TERRAIN
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(f'折山岭步道 {walkable} 个连通取样柱，灯苔 {len(plants["lamps"])} 株，鳞蕨 {len(plants["ferns"])} 株')
    return hashlib.sha256(data).hexdigest()


def write(out, path, value):
    path = out / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def texture(out, path, colors):
    """小尺度像素层理按面连续平铺，避免每一格都出现明显的纹理边框。"""
    path = out / 'assets/yuanxian/textures' / (path + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            fleck = (x * 31 ^ y * 13 ^ x * y * 31) % 11 - 5
            layer = colors[((y + (x // 4)) % 16) // 4 % len(colors)]
            row.append(tuple(max(0, min(255, c+fleck)) for c in layer) + (255,))
        pixels.append(row)
    path.write_bytes(encode_png(pixels))


def block_art(out):
    asset = 'assets/yuanxian/'
    data = 'data/yuanxian/loot_table/blocks/'
    stone = 'folded_ridge_stone'
    variants = {}
    for n, color in enumerate(PALETTE, 1):
        name = f'{stone}_{n}'
        texture(out, f'block/{name}', [tuple(c+k for c in color) for k in (-5, 0, 3, 0)])
        write(out, f'{asset}models/block/{name}.json',
              {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'yuanxian:block/{name}'}})
        variants[f'stratum={n}'] = {'model': f'yuanxian:block/{name}'}
    write(out, f'{asset}blockstates/{stone}.json', {'variants': variants})
    write(out, f'{asset}models/item/{stone}.json', {'parent': f'yuanxian:block/{stone}_3'})

    # 低矮的灯苔用一簇根叶托住亮芽，鳞蕨的枝叶由粗到细分段。
    moss = [
        ((5, 0, 5), (11, 1, 11), 'root'), ((2, 0, 6), (6, 1, 10), 'leaf'),
        ((10, 0, 8), (14, 1, 12), 'leaf'), ((8, 0, 2), (12, 1, 6), 'leaf'),
        ((6, 1, 6), (10, 3, 10), 'leaf'), ((7, 3, 7), (9, 5, 9), 'bud'),
        ((4, 1, 7), (6, 3, 9), 'bud'), ((10, 1, 7), (12, 2, 9), 'root'),
        ((11, 2, 8), (13, 4, 10), 'bud'),
    ]
    fern = [
        ((6, 0, 6), (10, 2, 10), 'root'), ((7, 1, 7), (9, 6, 9), 'root'),
        ((6, 5, 6), (10, 8, 10), 'leaf'), ((7, 8, 7), (9, 11, 9), 'tip'),
        ((3, 2, 7), (7, 4, 9), 'leaf'), ((2, 4, 6), (5, 6, 10), 'leaf'),
        ((1, 6, 7), (3, 8, 9), 'tip'), ((9, 3, 7), (12, 5, 9), 'leaf'),
        ((11, 5, 6), (14, 7, 10), 'leaf'), ((13, 7, 7), (15, 9, 9), 'tip'),
        ((7, 3, 4), (9, 5, 7), 'leaf'), ((6, 5, 2), (10, 7, 5), 'leaf'),
        ((7, 7, 1), (9, 9, 3), 'tip'), ((7, 3, 9), (10, 5, 13), 'leaf'),
        ((8, 5, 12), (11, 7, 15), 'tip'),
    ]
    for name, parts, colors in [
        ('ridge_lamp_moss', moss, {'root': (65, 81, 52), 'leaf': (99, 126, 81), 'bud': (224, 230, 154)}),
        ('ridge_scale_fern', fern, {'root': (86, 81, 48), 'leaf': (111, 132, 72), 'tip': (161, 161, 92)}),
    ]:
        tex = {key: f'yuanxian:block/{name}_{key}' for key in colors}
        tex['particle'] = tex['leaf']
        for key, color in colors.items():
            texture(out, f'block/{name}_{key}', [tuple(c+k for c in color) for k in (-7, 6, 2, -3)])
        elements = [{'from': lo, 'to': hi, 'faces': {face: {'texture': '#'+key}
                     for face in ('up', 'down', 'north', 'south', 'east', 'west')}} for lo, hi, key in parts]
        write(out, f'{asset}models/block/{name}.json', {'textures': tex, 'elements': elements})
        write(out, f'{asset}models/item/{name}.json', {'parent': f'yuanxian:block/{name}'})
        rotations = {'up': (0, 0), 'down': (180, 0), 'north': (90, 0),
                     'east': (90, 90), 'south': (90, 180), 'west': (90, 270)}
        variants = {f'facing={face}': {'model': f'yuanxian:block/{name}', 'x': x, 'y': y}
                    for face, (x, y) in rotations.items()}
        write(out, f'{asset}blockstates/{name}.json', {'variants': variants})
    for name in (stone, 'ridge_lamp_moss', 'ridge_scale_fern'):
        write(out, data+name+'.json', {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
             'entries': [{'type': 'minecraft:item', 'name': 'yuanxian:'+name}]},
        ]})


def compile_assets(out):
    digest = terrain(out)
    block_art(out)
    return digest


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('out', type=Path)
    print(compile_assets(parser.parse_args().out))
