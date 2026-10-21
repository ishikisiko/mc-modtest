#!/usr/bin/env python3
"""Owned gathering-array models, textures, recipes, collision data and Chinese names."""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import Canvas, png
from tools.gathering import architecture

IDS = {'low': 'spirit_gathering', 'medium': 'medium_grade_gathering', 'high': 'high_grade_gathering'}


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def tier_outputs(root, tier, cfg):
    assets = root/'src/main/resources/assets/myvillage'
    data = root/'src/main/resources/data'
    prefix = 'gathering' if tier == 'low' else 'gathering_'+tier
    result = {}
    for name, color in cfg['palette'].items():
        canvas = Canvas(32)
        for y in range(32):
            for x in range(32):
                noise = (x*17 + y*23 + x*y*3) % 11 - 5
                if name == 'jade': noise += 12 if (x+y*2) % 19 < 2 else 0
                if name == 'stone' and y in (0, 31): noise -= 12
                canvas.put(x, y, tuple(max(0, min(255, c+noise)) for c in color))
        result[assets/f'textures/block/{prefix}_{name}.png'] = png(canvas.rows)
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
            if tier != 'low':
                radius = math.hypot(x-15.5, y-15.5)
                if 6 < radius < 8: color = cfg['palette']['copper']
                if dx+dy < 6: color = cfg['palette']['jade']
                if tier == 'high' and 2 < dx < 4 and 9 < dy < 12: color = cfg['palette']['copper']
            top.put(x, y, tuple(min(255, max(0, c+(x*7+y*3)%5-2)) for c in color))
    result[assets/f'textures/block/{prefix}_core_top.png'] = png(top.rows)
    textures = {key: f'myvillage:block/{prefix}_{key}' for key in cfg['palette']}
    textures['particle'] = textures['stone']; textures['top'] = 'myvillage:block/'+prefix+'_core_top'
    for kind in ('core', 'node'):
        bid = IDS[tier]+'_'+kind
        boxes = cfg['node_boxes'] if kind == 'node' else [{'from':[0,0,0], 'to':[16,16,16], 'texture':'stone'}]
        elements = []
        for box in boxes:
            faces = {side:{'texture':'#'+('top' if kind == 'core' and side == 'up' else box['texture']), 'uv':[0,0,16,16]}
                     for side in ('up','down','north','south','east','west')}
            if kind == 'core':
                for side in faces: faces[side]['cullface'] = side
            element = {'from':box['from'],'to':box['to'],'faces':faces}
            if 'rotation' in box: element['rotation'] = box['rotation']
            elements.append(element)
        result[assets/f'models/block/{bid}.json'] = encode({'parent':'minecraft:block/block', 'textures':textures, 'elements':elements})
        result[assets/f'models/item/{bid}.json'] = encode({'parent':f'myvillage:block/{bid}'})
        variants = {'':{'model':f'myvillage:block/{bid}'}}
        if tier != 'low' and kind == 'node':
            variants = {f'facing={f}':{'model':f'myvillage:block/{bid}','y':y} for f,y in [('south',0),('west',90),('north',180),('east',270)]}
        result[assets/f'blockstates/{bid}.json'] = encode({'variants':variants})
        result[data/f'myvillage/loot_table/blocks/{bid}.json'] = encode({'type':'minecraft:block','pools':[{'rolls':1,
            'entries':[{'type':'minecraft:item','name':f'myvillage:{bid}'}],
            'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        recipe = cfg['recipes'][kind]
        result[data/f'myvillage/recipe/{bid}.json'] = encode({'type':'minecraft:crafting_shaped','category':'misc',
            'pattern':recipe['pattern'], 'key':{key:{'tag' if value.startswith('#') else 'item':value.removeprefix('#')}
            for key,value in recipe['key'].items()}, 'result':{'id':f'myvillage:{bid}','count':1}})
        if tier != 'low':
            result[data/f'myvillage/advancement/recipes/misc/{bid}.json'] = encode({
                'parent':'minecraft:recipes/root',
                'criteria':{'has_prior':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[recipe['key']['A']]}]}},
                            'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'myvillage:'+bid}}},
                'requirements':[['has_prior','has_recipe']], 'rewards':{'recipes':['myvillage:'+bid]}})
    return result


def outputs(root=ROOT):
    cfg = json.loads((root/'tools/gathering/visual.json').read_text())
    tiers = {'low':cfg, **cfg['tiers']}
    for tier in ('medium','high'):
        tiers[tier]['node_boxes'], tiers[tier]['collision_boxes'] = architecture.node(tier)
    result = {}
    for tier, data in tiers.items(): result.update(tier_outputs(root, tier, data))
    assets = root/'src/main/resources/assets/myvillage'
    data = root/'src/main/resources/data'
    parts = architecture.parts()
    rules = json.loads((root/'src/main/resources/data/myvillage/gathering/rules.json').read_text())
    construction = {'parts':{bid:{'collision_boxes':row['collision_boxes']} for bid,row in parts.items()},
                    'tiers':{tier:architecture.construction(tier,rules['tiers'][tier]['radius']) for tier in tiers}}
    for bid, row in construction['parts'].items():
        row['light_level'] = cfg['lighting'].get(bid, {}).get('block_light', 0)
    result[data/'myvillage/gathering/construction.json'] = encode(construction)
    for bid,row in parts.items():
        textures = {k:f'myvillage:block/gathering_{row["tier"]}_{k}' for k in ('stone','dark','copper','jade')}
        textures['particle']=textures['stone']
        elements=[{'from':b['from'],'to':b['to'],'faces':{side:{'texture':'#'+b['texture'],'uv':[0,0,16,16]}
                  for side in ('up','down','north','south','east','west')}} for b in row['boxes']]
        result[assets/f'models/block/{bid}.json']=encode({'parent':'minecraft:block/block','textures':textures,'elements':elements})
        result[assets/f'models/item/{bid}.json']=encode({'parent':f'myvillage:block/{bid}'})
        result[assets/f'blockstates/{bid}.json']=encode({'variants':{f'facing={f}':{'model':f'myvillage:block/{bid}','y':y}
              for f,y in [('south',0),('west',90),('north',180),('east',270)]}})
        result[data/f'myvillage/loot_table/blocks/{bid}.json']=encode({'type':'minecraft:block','pools':[{'rolls':1,
              'entries':[{'type':'minecraft:item','name':'myvillage:'+bid}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        recipe=row['recipe']
        result[data/f'myvillage/recipe/{bid}.json']=encode({'type':'minecraft:crafting_shaped','category':'building',
              'pattern':recipe['pattern'],'key':{k:{'item':v} for k,v in recipe['key'].items()},'result':{'id':'myvillage:'+bid,'count':recipe['count']}})
        result[data/f'myvillage/advancement/recipes/building_blocks/{bid}.json']=encode({'parent':'minecraft:recipes/root',
              'criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[recipe['key']['M']]}]}}},
              'requirements':[['has_material']],'rewards':{'recipes':['myvillage:'+bid]}})
    # Separate jade texture per luminous component: ordinary jade in pillars/cores stays unchanged.
    for bid, light in cfg['lighting'].items():
        model_path = assets/f'models/block/{bid}.json'
        model = json.loads(result[model_path])
        original = model['textures']['jade'].split(':', 1)[1]
        texture = f'block/{bid}_soft_jade'
        result[assets/f'textures/{texture}.png'] = result[assets/f'textures/{original}.png']
        material = light['labpbr']
        assert 0 <= material['emission'] <= 254
        pixel = tuple(material[k] for k in ('smoothness','f0','porosity','emission'))
        result[assets/f'textures/{texture}_s.png'] = png([[pixel]*32 for _ in range(32)])
        model['textures']['jade'] = 'myvillage:'+texture
        for element in model['elements']:
            for face in element['faces'].values():
                if face['texture'] == '#jade':
                    face['neoforge_data'] = {'block_light':light['face_light'], 'sky_light':light['face_light'],
                                             'ambient_occlusion':False}
        result[model_path] = encode(model)
    result[assets/'textures/block/gathering_glow.png'] = png([[(255,255,255,255)]])
    result[data/'myvillage/gathering/appearance.json'] = encode({tier:{**row['visual'], 'collision_boxes':row['collision_boxes']} for tier,row in tiers.items()})
    lang = assets/'lang/zh_cn.json';translated = json.loads(lang.read_text())
    for tier, row in tiers.items():
        translated.update({f'block.myvillage.{IDS[tier]}_{kind}':name for kind,name in row['names'].items()})
    translated.update({'block.myvillage.'+bid:row['name'] for bid,row in parts.items()})
    result[lang] = encode(translated)
    for name in ('mineable/pickaxe', 'needs_stone_tool'):
        path = data/f'minecraft/tags/block/{name}.json';content = json.loads(path.read_text())
        for prefix in IDS.values():
            for kind in ('core','node'):
                bid = 'myvillage:'+prefix+'_'+kind
                if bid not in content['values']: content['values'].append(bid)
        for bid in parts:
            if 'myvillage:'+bid not in content['values']:content['values'].append('myvillage:'+bid)
        result[path] = encode(content)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__);parser.add_argument('--check', action='store_true');args = parser.parse_args()
    drift = []
    for path, content in outputs().items():
        if args.check:
            if not path.is_file() or path.read_bytes() != content: drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True);path.write_bytes(content)
    if drift: raise SystemExit('聚灵阵生成资源漂移：\n'+'\n'.join(drift))
    print('聚灵阵资源检查通过' if args.check else '聚灵阵资源已生成')


if __name__ == '__main__': main()
