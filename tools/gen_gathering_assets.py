#!/usr/bin/env python3
"""Owned gathering-array models, textures, recipes, loot and Chinese names."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import Canvas, png


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def outputs(root=ROOT):
    cfg = json.loads((root/'tools/gathering/visual.json').read_text())
    assets = root/'src/main/resources/assets/myvillage'
    data = root/'src/main/resources/data'
    result = {}
    for name, color in cfg['palette'].items():
        canvas = Canvas(32)
        for y in range(32):
            for x in range(32):
                noise = (x*17 + y*23 + x*y*3) % 11 - 5
                if name == 'jade': noise += 12 if (x+y*2) % 19 < 2 else 0
                if name == 'stone' and y in (0, 31): noise -= 12
                canvas.put(x, y, tuple(max(0, min(255, c+noise)) for c in color))
        result[assets/f'textures/block/gathering_{name}.png'] = png(canvas.rows)
    top = Canvas(32)
    for y in range(32):
        for x in range(32):
            dx, dy = abs(x-15.5), abs(y-15.5)
            octagon = max(dx, dy, (dx+dy)/1.4)
            color = cfg['palette']['stone']
            if octagon < 12: color = cfg['palette']['dark']
            if 11 < octagon < 13: color = cfg['palette']['copper']
            if (13 <= x <= 18 and 7 <= y <= 24) or (13 <= y <= 18 and 7 <= x <= 24):
                color = cfg['palette']['jade']
            top.put(x, y, tuple(min(255, max(0, c+(x*7+y*3)%5-2)) for c in color))
    result[assets/'textures/block/gathering_core_top.png'] = png(top.rows)
    # White texel for tinted, thin world-space rune ribbons.
    result[assets/'textures/block/gathering_glow.png'] = png([[(255,255,255,255)]])
    textures = {key: f'myvillage:block/gathering_{key}' for key in cfg['palette']}
    textures['particle'] = textures['stone']; textures['top'] = 'myvillage:block/gathering_core_top'
    for kind in ('core', 'node'):
        bid = 'spirit_gathering_'+kind
        boxes = cfg['node_boxes'] if kind == 'node' else [{'from':[0,0,0], 'to':[16,16,16], 'texture':'stone'}]
        elements = []
        for box in boxes:
            faces = {side:{'texture':'#'+('top' if kind == 'core' and side == 'up' else box['texture']), 'uv':[0,0,16,16]}
                     for side in ('up','down','north','south','east','west')}
            if kind == 'core':
                for side in faces: faces[side]['cullface'] = side
            elements.append({'from':box['from'],'to':box['to'],'faces':faces})
        result[assets/f'models/block/{bid}.json'] = encode({'parent':'minecraft:block/block', 'textures':textures, 'elements':elements})
        result[assets/f'models/item/{bid}.json'] = encode({'parent':f'myvillage:block/{bid}'})
        result[assets/f'blockstates/{bid}.json'] = encode({'variants':{'':{'model':f'myvillage:block/{bid}'}}})
        result[data/f'myvillage/loot_table/blocks/{bid}.json'] = encode({'type':'minecraft:block','pools':[{'rolls':1,
            'entries':[{'type':'minecraft:item','name':f'myvillage:{bid}'}],
            'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        recipe = cfg['recipes'][kind]
        result[data/f'myvillage/recipe/{bid}.json'] = encode({'type':'minecraft:crafting_shaped','category':'misc',
            'pattern':recipe['pattern'], 'key':{key:{'tag' if value.startswith('#') else 'item':value.removeprefix('#')}
            for key,value in recipe['key'].items()}, 'result':{'id':f'myvillage:{bid}','count':1}})
    lang = assets/'lang/zh_cn.json'
    translated = json.loads(lang.read_text())
    translated.update({f'block.myvillage.spirit_gathering_{kind}':name for kind,name in cfg['names'].items()})
    result[lang] = encode(translated)
    for name in ('mineable/pickaxe', 'needs_stone_tool'):
        path = data/f'minecraft/tags/block/{name}.json'
        content = json.loads(path.read_text())
        for kind in ('core','node'):
            bid = 'myvillage:spirit_gathering_'+kind
            if bid not in content['values']: content['values'].append(bid)
        result[path] = encode(content)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    drift = []
    for path, content in outputs().items():
        if args.check:
            if not path.is_file() or path.read_bytes() != content: drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(content)
    if drift: raise SystemExit('聚灵阵生成资源漂移：\n'+'\n'.join(drift))
    print('聚灵阵资源检查通过' if args.check else '聚灵阵资源已生成')


if __name__ == '__main__': main()
