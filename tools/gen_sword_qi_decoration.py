#!/usr/bin/env python3
"""剑意灵台：由一份外观输入生成模型、像素材质、光晕和物品资源。"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import png

ASSET = Path('src/main/resources/assets/myvillage')
DATA = Path('src/main/resources/data/myvillage')
IDENTITY = 'sword_qi_pedestal'


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def appearance():
    a = json.loads((ROOT / ASSET / 'sword_qi/appearance.json').read_text())
    for profile in ('blade', 'grip'):
        points = a[profile]
        if len(points) < 2 or any(len(p) != 3 or not all(math.isfinite(v) for v in p) for p in points):
            raise ValueError(f'{profile}: invalid connected profile')
        if any(p[1] < 0 or p[2] < 0 for p in points) or any(b[0] <= p[0] for p, b in zip(points, points[1:])):
            raise ValueError(f'{profile}: widths/depths must be positive and heights strictly increasing')
    if a['blade'][-1][1:] != [0, 0] or a['grip'][-1][0] < a['blade'][0][0]:
        raise ValueError('The complete sword must have a closed tip and connected grip')
    if not 0 < a['light_level'] <= 15 or not 8 <= a['ribbon_samples'] <= 128:
        raise ValueError('Invalid light level or ribbon rendering budget')
    if not 8 <= a['ring_samples'] <= 128 or not 0 <= a['motes'] <= 16:
        raise ValueError('Invalid ring/mote rendering budget')
    if min(a[k] for k in ('breath_ticks', 'ribbon_ticks', 'mote_ticks')) <= 0:
        raise ValueError('Animation periods must be positive')
    if a['blade'][-1][0] + a['bob_height'] > 6 or a['ribbon_radius'] + a['ribbon_width'] * 2.6 > 2:
        raise ValueError('Decoration exceeds its bounded visual envelope')
    if len(a['base_box']) != 6 or any(not 0 <= n <= 16 for n in a['base_box']):
        raise ValueError('Pedestal collision must remain inside one block')
    return a


def rgb(value):
    return tuple(bytes.fromhex(value))


def stone_texture(a, top):
    base = rgb(a['colors']['stone_top' if top else 'stone'])
    gold = rgb(a['colors']['gold'])
    rows = []
    for y in range(64):
        row = []
        for x in range(64):
            u, v = (x - 31.5) / 32, (y - 31.5) / 32
            noise = ((x * 13 + y * 23 + x * y * 7) % 11 - 5) * .65
            vein = 5 * math.sin(x * .22 + math.sin(y * .11) * 2)
            shade = noise + vein
            color = tuple(max(0, min(255, round(c + shade))) for c in base)
            if top:
                r = math.hypot(u, v)
                # An engraved circular seal and four closed cloud curls on one carved jade body.
                engraved = abs(r - .71) < .017 or abs(r - .79) < .009
                for turn in range(4):
                    angle = turn * math.pi / 2
                    cx, cy = .47 * math.cos(angle), .47 * math.sin(angle)
                    radius = math.hypot(u - cx, v - cy)
                    engraved |= abs(radius - .12) < .014 and abs(math.atan2(v - cy, u - cx) - angle) > .4
                if engraved: color = tuple(round(c * .80) for c in gold)
                if abs(u) > .9 or abs(v) > .9: color = tuple(round(c * .62) for c in color)
            else:
                if y in (5, 6, 54): color = tuple(round(c * .76) for c in gold)
                # Subtle single-body bevel and flowing cloud vein, no added plate geometry.
                if x < 5 or x > 58: color = tuple(round(c * .70) for c in color)
                if abs(y - (31 + 7 * math.sin(x * math.pi / 32))) < .8: color = (112, 153, 138)
            row.append((*color, 255))
        rows.append(row)
    return png(rows)


def soft_texture(radial=False):
    rows = []
    for y in range(64):
        row = []
        for x in range(64):
            distance = abs((x - 31.5) / 31.5)
            if radial: distance = math.hypot((x - 31.5) / 31.5, (y - 31.5) / 31.5)
            alpha = round(255 * max(0, 1 - distance * distance) ** (3 if radial else 2.5))
            row.append((255, 255, 255, alpha))
        rows.append(row)
    return png(rows)


def icon(a):
    rows = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
    for y in range(32):
        h = (30 - y) / 5.6
        for x in range(32):
            dx = (x - 15.5) / 8
            if y >= 28 and 8 <= x <= 23: rows[y][x] = (*rgb(a['colors']['stone_top']), 255)
            for points in (a['blade'], a['grip']):
                for lower, upper in zip(points, points[1:]):
                    if lower[0] <= h <= upper[0]:
                        t = (h - lower[0]) / (upper[0] - lower[0])
                        w = lower[1] * (1 - t) + upper[1] * t
                        if abs(dx) <= max(w, .06): rows[y][x] = (*rgb(a['colors']['core' if dx < 0 else 'jade']), 255)
            if abs(dx) < a['guard_width'] and abs(h - a['guard_y'] - a['guard_sweep'] * (dx / a['guard_width']) ** 2) < .08:
                rows[y][x] = (*rgb(a['colors']['gold']), 255)
    for i in range(100):
        t = i / 99
        theta = t * math.tau * a['ribbon_turns']
        x = round(15.5 + math.cos(theta) * 6 * math.sin(math.pi * t))
        y = round(28 - t * 20)
        if 0 <= y < 32 and 0 <= x < 32 and rows[y][x][3] == 0:
            rows[y][x] = (*rgb(a['colors']['gold']), 200)
    return png(rows)


def generated(a):
    out = {}
    variants = {f'facing={f},lit={lit}': {'model': f'myvillage:block/{IDENTITY}', 'y': angle}
                for f, angle in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for lit in ('false', 'true')}
    out[ASSET / f'blockstates/{IDENTITY}.json'] = encoded({'variants': variants})
    faces = {f: {'texture': '#top' if f == 'up' else '#side'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
    box = a['base_box']
    out[ASSET / f'models/block/{IDENTITY}.json'] = encoded({
        'parent': 'minecraft:block/block', 'textures': {'top': 'myvillage:block/sword_qi_pedestal_top',
        'side': 'myvillage:block/sword_qi_pedestal_side', 'particle': 'myvillage:block/sword_qi_pedestal_side'},
        'elements': [{'from': box[:3], 'to': box[3:], 'faces': faces}]})
    out[ASSET / f'models/item/{IDENTITY}.json'] = encoded({'parent': 'minecraft:item/generated',
        'textures': {'layer0': f'myvillage:item/{IDENTITY}'}})
    out[DATA / f'loot_table/blocks/{IDENTITY}.json'] = encoded({'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'myvillage:{IDENTITY}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    out[ASSET / 'textures/block/sword_qi_pedestal_top.png'] = stone_texture(a, True)
    out[ASSET / 'textures/block/sword_qi_pedestal_side.png'] = stone_texture(a, False)
    out[ASSET / 'textures/block/sword_qi_glow.png'] = soft_texture()
    out[ASSET / 'textures/block/sword_qi_mote.png'] = soft_texture(True)
    out[ASSET / f'textures/item/{IDENTITY}.png'] = icon(a)
    path = ASSET / 'lang/zh_cn.json'
    names = json.loads((ROOT / path).read_text())
    names[f'block.myvillage.{IDENTITY}'] = a['name']
    out[path] = encoded(names)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    outputs = generated(appearance())
    mismatched = []
    for relative, data in outputs.items():
        path = ROOT / relative
        if args.check:
            if not path.exists() or path.read_bytes() != data: mismatched.append(str(relative))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if mismatched:
        raise SystemExit('资源与外观输入不一致：\n' + '\n'.join(mismatched))
    print(f'剑意灵台：{len(outputs)} 个资源' + ('检查通过' if args.check else '已生成'))


if __name__ == '__main__':
    main()
