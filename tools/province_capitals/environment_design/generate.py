#!/usr/bin/env python3
"""八州地标与两城环境设计。只生成审阅几何；不写正式 worldgen、NBT 或 mod 资源。"""
from __future__ import annotations

import argparse
import base64
import copy
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools'))
from province_capitals.regional.v2.city import Capital, load_city
from province_capitals.regional.v2.preview import SceneBuilder
from province_capitals.environment_design.geometry import Scene

HERE = Path(__file__).resolve().parent
INPUT = ROOT / 'blueprints/province_capitals/environment_design'


def read(path):
    return json.loads(Path(path).read_text())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def base_identity(region):
    files = [ROOT / f'blueprints/province_capitals/regional/v2/cities/{region}.json',
             ROOT / 'blueprints/province_capitals/regional/v2/building_families.json']
    for directory in ['tools/province_capitals/regional/v2', 'tools/buildgen']:
        files.extend(p for p in (ROOT / directory).rglob('*') if p.suffix in {'.py', '.json'})
    return {str(p.relative_to(ROOT)): sha(p) for p in sorted(set(files))}


def baseline(region, cache):
    identity = base_identity(region)
    path = cache / f'{region}.json.gz'
    if path.exists():
        previous = json.loads(gzip.decompress(path.read_bytes()))
        if previous['source'] == identity:
            return previous
    model = Capital(load_city(region)).construct()
    builder = SceneBuilder(model)
    for tx, tz in sorted(model.construction.by_tile):
        builder.add_tile(tx, tz, model.construction.tile(tx, tz))
    result = {'source': identity, 'boxes': builder.boxes, 'meshes': builder.meshes,
              'materials': {k: {'color': v} for k, v in builder.palette.items()},
              'terrain': model.terrain, 'size': [model.width, model.height, model.depth],
              'buildings': model.buildings, 'roads': model.roads,
              'road_columns': [[x, z] for x, z in sorted(model.road_columns)],
              'access': model.access, 'courtyards': model.city.get('courtyards', [])}
    cache.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(json.dumps(result, ensure_ascii=False, separators=(',', ':')).encode(), mtime=0))
    return result


def person(scene, x, y, z, tag='人物尺度'):
    scene.box(x-.16, y, z-.13, .13, .7, .26, 'slate', 'scale', tag)
    scene.box(x+.03, y, z-.13, .13, .7, .26, 'slate', 'scale', tag)
    scene.box(x-.28, y+.7, z-.15, .56, .66, .3, 'cloth', 'scale', tag)
    scene.box(x-.2, y+1.36, z-.2, .4, .44, .4, 'human', 'scale', tag)


def scale_house(scene, p):
    x, y, z = p
    scene.box(x-4, y, z-4, 8, 4, 8, 'plaster', 'context', '普通民居尺度')
    for xx in [-4, 3.5]:
        for zz in [-4, 3.5]:
            scene.box(x+xx, y, z+zz, .5, 4, .5, 'bark', 'context', '普通民居尺度')
    for n in range(5):
        scene.box(x-5+n, y+4+n, z-5, 10-2*n, 1, 10, 'roof', 'context', '普通民居尺度')
    scene.box(x-1, y, z+4.01, 2, 2.7, .1, 'bark', 'context', '普通民居尺度')


def showcase(catalog, spec):
    scene = Scene(catalog['materials'])
    half = catalog['showcase']['platform_half']
    scene.box(-half, -1, -half, half*2, 1, half*2, 'path', 'terrain', '尺度广场')
    scene.recipe(spec['recipe'], catalog['recipes'])
    scale_house(scene, catalog['showcase']['house_position'])
    person(scene, *catalog['showcase']['human_position'])
    return scene.export({**spec, 'id': spec['region']+'_landmark', 'mode': 'landmark',
                         'scope': '独立三维设计稿；普通民居与人物用于比例参照。',
                         'cameras': {'overview': {'eye': catalog['showcase']['overview_eye'], 'target': catalog['showcase']['overview_target']},
                                     'close': {'eye': [45, 36, 57], 'target': [0, 20, 0]},
                                     'human': {'eye': [-10, 2.7, 32], 'target': [0, 22, 0]}}})


def polyline(points):
    result = []
    for a, b in zip(points, points[1:]):
        if a[0] != b[0] and a[1] != b[1]:
            raise ValueError('设计道路、城墙必须使用正交折线')
        length = max(abs(a[i]-b[i]) for i in range(2))
        for n in range(length):
            result.append(tuple(round(a[i]+(b[i]-a[i])*n/max(length, 1)) for i in range(2)))
    return result + [tuple(points[-1])]


def garden(scene, patch, catalog):
    x0, z0, x1, z1 = patch['bounds']
    y, kind, tag = patch['y']+.125, patch['kind'], patch['id']
    w, d = x1-x0+1, z1-z0+1
    cx, cz = (x0+x1)/2, (z0+z1)/2
    scene.box(x0, y-.125, z0, w, .125, d, 'soil' if kind == 'herb' else 'path', 'garden', tag)
    # 院门保留在连接小径的同一边；不把整块余地封闭起来。
    gate = patch['gate']
    for x in range(x0, x1+1):
        for z in [z0, z1]:
            if abs(x-gate[0]) <= 1 and abs(z-gate[1]) <= 1:
                continue
            scene.box(x, y, z, 1, 1.1, .5, 'stone_light', 'garden', tag)
    for z in range(z0+1, z1):
        for x in [x0, x1]:
            if abs(x-gate[0]) <= 1 and abs(z-gate[1]) <= 1:
                continue
            scene.box(x, y, z, .5, 1.1, 1, 'stone_light', 'garden', tag)
    recipes = catalog['recipes']
    if kind == 'herb':
        for x in range(x0+2, x1-1, 4):
            scene.box(x, y+.05, z0+2, 2, .25, d-4, 'soil', 'garden', tag)
            for z in range(z0+2, z1-1, 2):
                scene.box(x+.25, y+.3, z+.25, 1.5, .5, 1, 'herb', 'garden', tag)
                scene.box(x+.65, y+.8, z+.6, .3, .3, .3, 'blossom', 'garden', tag)
        scene.recipe('jar', recipes, (x1-1, y, z0+1), .25, 'props', tag+'_水缸')
    elif kind in {'pine', 'flower'}:
        tree = 'pine' if kind == 'pine' else 'blossom_tree'
        scene.recipe(tree, recipes, (x0+3, y, z0+3), .8, 'green', tag+'_庭树')
        if w > 12:
            scene.recipe('stone_boulder', recipes, (x1-3, y, z1-3), .65, 'garden', tag+'_庭石')
        scene.recipe('bench', recipes, (cx, y, z1-2), .5, 'props', tag+'_坐凳')
        for z in range(z0+2, z1-1, 2):
            scene.box(x1-1.8, y+.1, z, 1.1, .6, 1.4, 'leaf_light', 'green', tag)
            scene.box(x1-1.5, y+.7, z+.3, .4, .35, .4, 'flower', 'green', tag)
    elif kind == 'well':
        scene.recipe('well', recipes, (cx, y, cz), .65, 'props', tag+'_水井')
        scene.recipe('shade_tree', recipes, (x0+3, y, z0+3), .75, 'green', tag+'_遮荫树')
        scene.recipe('bench', recipes, (x1-3, y, z1-2), .6, 'props', tag+'_坐凳')
    elif kind == 'work':
        for x in [x0+2, x0+4, x0+6]:
            scene.box(x, y, z0+2, 1.5, 1.4, 1.5, 'wood_light', 'props', tag+'_货箱')
        scene.box(x0+2, y+.6, z1-3, min(6, w-4), .4, 2, 'bark', 'props', tag+'_作台')
        scene.recipe('jar', recipes, (x1-2, y, z0+2), .35, 'props', tag+'_器缸')
        # 连续网面与实际支架；只表达晾晒场，不声称新增玩法。
        for x in [x0+2, x1-2]:
            scene.box(x, y, cz, .3, 3, .3, 'bark', 'props', tag+'_晒架')
        scene.box(x0+2, y+1.3, cz, w-4, 1.4, .08, 'rope', 'props', tag+'_晒网')
    else:
        raise ValueError(f'未消费的庭院类型：{kind}')
    scene.recipe('rune_lamp', recipes, (gate[0]+(2 if gate[0]+2 < x1 else -2), y, gate[1]), .35, 'cultivation', tag+'_符灯')
    for x, z in patch['path']:
        scene.box(x-.5, y-.12, z-.5, 2, .12, 2, 'path', 'path', tag+'_接路')
    scene.items.append({'id': tag, 'kind': kind, 'bounds': patch['bounds'], 'near_house': patch['near_house']})


def walls(scene, pilot, height, catalog):
    cfg = catalog['environment']
    wall = pilot['wall']
    route = polyline(wall['points'])
    if route[0] != route[-1]:
        raise ValueError('城防边界未闭合')
    width = cfg['wall_width']
    for i, (x, z) in enumerate(route):
        if any(g['bounds'][0] <= x <= g['bounds'][2] and g['bounds'][1] <= z <= g['bounds'][3] for g in wall['gates']):
            continue
        y = height(x, z)
        h = wall.get('sea_height', cfg['wall_height']) if x >= wall.get('sea_x', 9999) else cfg['wall_height']
        scene.box(x-width/2, y, z-width/2, width, h, width, wall['material'], 'walls', '城墙')
        # 墙顶为可读的实体步道，两侧女墙与交替垛口。
        a = route[max(0, i-1)]; b = route[min(len(route)-1, i+1)]
        along_x = abs(b[0]-a[0]) > abs(b[1]-a[1])
        for sign in [-1, 1]:
            xx, zz = (x, z+sign*(width/2-.4)) if along_x else (x+sign*(width/2-.4), z)
            scene.box(xx-.45, y+h, zz-.45, .9, .7, .9, 'stone_light', 'walls', '女墙')
            if i % cfg['battlement_step'] < 2:
                scene.box(xx-.45, y+h+.7, zz-.45, .9, 1.1, .9, wall['material'], 'walls', '垛口')
    for gate in wall['gates']:
        x0, z0, x1, z1 = gate['bounds']; cx, cz = (x0+x1)/2, (z0+z1)/2
        y = gate['y']
        if gate['type'] == 'water':
            for zz in [z0-3, z1+3]:
                scene.box(cx-3, y, zz-3, 6, 15, 6, wall['material'], 'walls', gate['name'])
            scene.box(cx-3, y+11, z0, 6, 4, z1-z0+1, 'stone_light', 'walls', gate['name'])
            scene.recipe('rune_stele', catalog['recipes'], (cx, y+15, cz), .8, 'cultivation', gate['name']+'_镇潮碑')
        else:
            # 旧门楼保留，两侧接厚墙与望台。
            for dx, dz in gate['towers']:
                tx, tz = cx+dx, cz+dz; gy = height(round(tx), round(tz))
                scene.box(tx-3, gy, tz-3, 6, 11, 6, wall['material'], 'walls', gate['name'])
                scene.box(tx-4, gy+11, tz-4, 8, 1, 8, 'stone_light', 'walls', gate['name'])
                scene.recipe('rune_lamp', catalog['recipes'], (tx, gy+12, tz), .65, 'cultivation', gate['name']+'_阵灯')
    for stair in wall['stairs']:
        x, z = stair['start']; dx, dz = stair['direction']; y = height(x, z)
        for i in range(cfg['wall_height']*2):
            scene.box(x+dx*i-1, y, z+dz*i-1, 3, (i+1)*.5, 3, wall['material'], 'walls', '登城阶')


def city_scene(catalog, pilot, base):
    if len(pilot['gardens']) < catalog['environment']['minimum_patches']:
        raise ValueError('生活院落设计尚未达到本轮输入要求')
    seen = set()
    for patch in pilot['gardens']:
        if patch['id'] in seen:
            raise ValueError('院落 ID 重复')
        seen.add(patch['id'])
        x0,z0,x1,z1 = patch['bounds']; gx,gz = patch['gate']
        if not (x0 <= gx <= x1 and z0 <= gz <= z1 and (gx in (x0,x1) or gz in (z0,z1))):
            raise ValueError('院门不在院界上')
        if not patch['path'] or patch['path'][0] != patch['gate']:
            raise ValueError('庭院缺少从院门出发的接路')
        if any(abs(a[0]-b[0])+abs(a[1]-b[1]) != 1 for a,b in zip(patch['path'],patch['path'][1:])):
            raise ValueError('庭院接路不连续')
    if not {p['recipe'] for p in pilot['public_objects']} <= catalog['recipes'].keys():
        raise ValueError('公共设施存在未消费配方')
    scene = Scene({**base['materials'], **catalog['materials']})
    removed = set(pilot['remove_buildings'])
    scene.boxes = [copy.deepcopy(b) for b in base['boxes'] if b['tag'] not in removed and b['layer'] not in {'tree', 'wall'}]
    # 仅删除明确改线的局部滨海步道，原桥与城市道路仍完整展示。
    if pilot.get('clear_road'):
        c = pilot['clear_road']
        scene.boxes = [b for b in scene.boxes if not(b['tag'] == c['id'] and b['p'][2] >= c['z_min'])]
    scene.meshes = copy.deepcopy(base['meshes'])
    w, _, d = base['size']
    def height(x, z):
        return base['terrain'][min(d-1, max(0, int(z)))*w+min(w-1, max(0, int(x)))]+1
    walls(scene, pilot, height, catalog)
    for patch in pilot['gardens']:
        garden(scene, patch, catalog)
    for p in pilot['public_objects']+pilot.get('frontages', []):
        scene.recipe(p['recipe'], catalog['recipes'], p['position'], p['scale'], p['layer'], p['name'], p.get('q', 0))
    for street in pilot.get('new_paths', []):
        for x, z in polyline(street['points']):
            y = street['y']
            scene.box(x-street['width']/2, y-1, z-street['width']/2, street['width'], 1.125, street['width'], 'path', 'path', street['name'])
    for stair in pilot.get('landmark_stairs', []):
        x,y,z=stair['start']; dx,dz=stair['direction']; width=stair['width']
        for n in range(stair['steps']):
            xx,zz=x+dx*n,z+dz*n
            scene.box(xx-(width/2 if dz else 0),y,zz-(width/2 if dx else 0),
                      width if dz else 1,(n+1)*stair['rise'],width if dx else 1,
                      'stone_light','landmark',stair['name'])
    landmark = next(x for x in catalog['landmarks'] if x['region'] == pilot['region'])
    scene.recipe(landmark['recipe'], catalog['recipes'], pilot['landmark_origin'], pilot['landmark_scale'], 'landmark', landmark['name'])
    if pilot['region'] == 'donghai':
        # 巨盘台基以四座石墩贯通至既有海床，不悬在水面上。
        x, y, z = pilot['landmark_origin']
        for dx in [-14, 14]:
            for dz in [-14, 14]:
                ground = height(x+dx, z+dz)
                scene.box(x+dx-2, min(ground, y-1), z+dz-2, 5, max(1, y-ground), 5, 'slate', 'landmark', '巨盘海床支墩')
    for p in pilot['people']:
        person(scene, *p)
    return scene.export({'id': pilot['region']+'_city', 'region': pilot['region'], 'region_name': landmark['region_name'],
                         'name': pilot['name'], 'mode': 'city', 'kind': '整城环境设计样板',
                         'function': landmark['function'], 'intent': pilot['intent'], 'scope': 'V9 方块形态底座＋新增设计几何；未导出新游戏资源。',
                         'cameras': pilot['cameras'], 'removed_for_design': pilot['remove_buildings'],
                         'voxel_terrain': {'width':w,'depth':d,'data':base64.b64encode(struct.pack('<'+'h'*len(base['terrain']),*base['terrain'])).decode()},
                         'base_source': base['source'], 'garden_count': len(pilot['gardens']),
                         'frontage_count': len(pilot.get('frontages', [])),
                         'public_object_count': len(pilot['public_objects'])})


def bundle(scenes):
    compact = []
    for source in scenes:
        scene = dict(source)
        boxes = scene.pop('boxes')
        mats = list(scene['materials'])
        layers = list(dict.fromkeys(b['layer'] for b in boxes))
        tags = list(dict.fromkeys(b['tag'] for b in boxes))
        mi, li, ti = ({v:i for i,v in enumerate(a)} for a in [mats,layers,tags])
        packed = bytearray()
        for component in range(6):
            last = 0
            for b in boxes:
                value = round((b['p']+b['s'])[component]*1000)
                packed.extend(struct.pack('<i', value-last)); last=value
        packed.extend(bytes(mi[b['m']] for b in boxes))
        packed.extend(bytes(li[b['layer']] for b in boxes))
        packed.extend(struct.pack('<'+'H'*len(boxes),*[ti[b['tag']] for b in boxes]))
        scene['packed']={'count':len(boxes),'materials':mats,'layers':layers,'tags':tags,'data':base64.b64encode(packed).decode()}
        if 'voxel_terrain' in scene:
            scene['meshes']=[m for m in scene['meshes'] if m['tag'] not in {'heightfield','heightfield_sides'}]
        # 浏览器不需要审计源清单、每项实例清单；它们保留在独立 manifest/scene 中。
        scene.pop('base_source',None); scene.pop('items',None)
        compact.append(scene)
    payload = {'scenes': compact,
               'three': (ROOT/'tools/web/three.min.js').read_text(),
               'orbit': (ROOT/'tools/web/OrbitControls.js').read_text(),
               'viewer': (HERE/'viewer.js').read_text()}
    packed = base64.b64encode(gzip.compress(json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode(), mtime=0, compresslevel=9)).decode()
    return (HERE/'template.html').read_text().replace('__PAYLOAD__', packed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'out/preview/capital-environment-design-20261029')
    parser.add_argument('--base-only', action='store_true')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    catalog = read(INPUT/'catalog.json')
    regions = [s['region'] for s in catalog['landmarks']]
    if len(regions) != 8 or len(set(regions)) != 8 or len({s['recipe'] for s in catalog['landmarks']}) != 8:
        raise ValueError('须有八州各自独立的主地标配方')
    bases = {r: baseline(r, args.out/'cache') for r in ['lingyue', 'donghai']}
    if args.base_only:
        print('两城原生成器只读设计底座已生成。', flush=True)
        return
    scenes = [city_scene(catalog, read(INPUT/f'{r}.json'), bases[r]) for r in bases]
    scenes += [showcase(catalog, spec) for spec in catalog['landmarks']]
    (args.out/'index.html').write_text(bundle(scenes))
    (args.out/'cities.html').write_text(bundle(scenes[:2]))
    (args.out/'landmarks.html').write_text(bundle(scenes[2:]))
    manifest = {'scope': catalog['scope'], 'status': 'captured_for_review_pending',
                'source': {str(p.relative_to(ROOT)): sha(p) for folder in [INPUT, HERE] for p in sorted(folder.glob('*')) if p.suffix in {'.json', '.py', '.html', '.js'}},
                'scenes': []}
    for scene in scenes:
        (args.out/(scene['id']+'.html')).write_text(bundle([scene]))
        dest = args.out/(scene['id']+'.json.gz')
        dest.write_bytes(gzip.compress(json.dumps(scene, ensure_ascii=False, separators=(',', ':')).encode(), mtime=0))
        manifest['scenes'].append({'id': scene['id'], 'name': scene['name'], 'bounds': scene['bounds'],
                                   'boxes': len(scene['boxes']), 'file': dest.name, 'sha256': sha(dest),
                                   'items': scene['items'], 'cameras': scene['cameras']})
    (args.out/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'scenes':len(scenes), 'html_bytes': (args.out/'index.html').stat().st_size, 'output':str(args.out)}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
