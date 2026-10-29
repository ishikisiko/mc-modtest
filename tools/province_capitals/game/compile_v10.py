#!/usr/bin/env python3
"""V10 八州环境聚合；V9 与更早产物严格只读。"""
from __future__ import annotations
import argparse
from array import array
import base64, copy, gzip, io, json, re, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from province_capitals.game.compile import canonical,identity,life_inputs,resource_file,sha,write
from province_capitals.game import compile_v9 as legacy
read_bounded=legacy.read_bounded
SUPPORTED_VERSIONS=frozenset(('v3',))
DEFAULT_OUT=ROOT/'build/generated/province_capitals_v10/resources'
V9_OUT=ROOT/'build/generated/province_capitals_v9/resources'
LEGACY_OUT=ROOT/'build/generated/province_capitals/resources'
RULES=ROOT/'blueprints/worldplan/rules_v10.json'

def inspect_city(source: Path, definition: dict, limit: int) -> tuple[dict, dict]:
    city_id, region, version = (definition[key] for key in ('city_id', 'region_id', 'resource_version'))
    if version not in SUPPORTED_VERSIONS or city_id != 'capital_' + region or not re.fullmatch(r'[a-z0-9_]+', region):
        raise ValueError('主城身份或资源版本不合法')
    manifest = f'myvillage:capitals/{version}/{city_id}/city.json'
    path = resource_file(source, manifest)
    if not path.is_file():
        raise FileNotFoundError(f'正式主城尚未齐备，拒绝聚合不完整 V10：{path}')
    manifest_bytes = read_bounded(path, 8 * 1024 * 1024)
    city = json.loads(manifest_bytes)
    if (city.get('schema'), city.get('format'), city.get('city_id'), city.get('region_id')) != (1, 'capital_game_bundle', city_id, region):
        raise ValueError(f'{manifest} 不是对应城市的 capital_game_bundle schema=1')
    if 'layout_family_id' in definition and city['layout_family_id'] != definition['layout_family_id']:
        raise ValueError(f'{city_id} 的布局族与 V10 配置不符')
    if 'life_layout_id' in definition and city['life']['layout_id'] != definition['life_layout_id']:
        raise ValueError(f'{city_id} 的生活布局身份与 V10 配置不符')
    size = city['size']
    if not isinstance(size, list) or len(size) != 3 or any(type(n) is not int for n in size):
        raise ValueError(f'{city_id} 必须有整数三维尺寸')
    width, height, depth = size
    if not (16 <= width <= 2048 and 8 <= height <= 320 and 16 <= depth <= 2048) or width % 16 or depth % 16:
        raise ValueError(f'{city_id} 超出既有主城尺寸边界')
    offset = city['source_offset']
    if not isinstance(offset, list) or len(offset) != 3 or any(type(v) is not int or abs(v) > 30000000 for v in offset):
        raise ValueError(f'{city_id} 源坐标必须为有界整数三元组')
    placement = city['placement']
    if placement['vertical_mode'] not in ('absolute', 'sea_relative', 'terrain_relative') or type(placement['rotation_quarters']) is not int or placement['rotation_quarters'] != 0:
        raise ValueError(f'{city_id} 放置语义不受支持')
    if type(placement['anchor_y']) is not int or not -64 <= placement['anchor_y'] < 320 or type(placement['blend_margin']) is not int or not 16 <= placement['blend_margin'] <= 256:
        raise ValueError(f'{city_id} 高度原点或融合外缘越界')
    if region == 'zhongzhou' and (placement['vertical_mode'], placement['anchor_y']) != ('absolute', 0):
        raise ValueError('中州 v3 必须保持 absolute originY=0')
    terrain = city['terrain']
    prefix = f'myvillage:capitals/{version}/{city_id}/'
    if not terrain['resource'].startswith(prefix) or not re.fullmatch(r'myvillage:[a-z0-9_/.-]+', terrain['resource']) or '..' in terrain['resource']:
        raise ValueError(f'{city_id} 地形必须与 manifest 的城 ID 和资源版本一致')
    if (terrain['encoding'], terrain['width'], terrain['depth']) != ('i16le_z_major_top_solid', width, depth):
        raise ValueError(f'{city_id} heightfield 必须记录同尺寸的 top-solid')
    for key in ('top_block', 'fill_block', 'subsurface_block'):
        if not re.fullmatch(r'[a-z0-9_]+:[a-z0-9_/]+', terrain[key]):
            raise ValueError(f'{city_id} 地基方块 ID 不合法')
    if type(terrain['subsurface_depth']) is not int or not 0 <= terrain['subsurface_depth'] <= 16:
        raise ValueError(f'{city_id} 表土厚度越界')
    terrain_bytes = read_bounded(resource_file(source, terrain['resource']), 16 * 1024 * 1024)
    expected_length = width * depth * 2
    with gzip.GzipFile(fileobj=io.BytesIO(terrain_bytes)) as stream:
        raw_height = stream.read(expected_length + 1)
    if len(raw_height) != expected_length:
        raise ValueError(f'{city_id} heightfield 长度不符')
    heights = array('h'); heights.frombytes(raw_height)
    if sys.byteorder != 'little':
        heights.byteswap()
    if min(heights) < 0 or max(heights) >= height:
        raise ValueError(f'{city_id} top-solid 越出本地 Y 范围')
    resources = {terrain['resource']: sha(terrain_bytes)}
    if resources[terrain['resource']] != terrain['sha256']:
        raise ValueError(f'{city_id} 地形声明摘要不符')
    positions, total_bytes = set(), len(manifest_bytes) + len(terrain_bytes)
    if len(city['tiles']) > width * depth // 256:
        raise ValueError(f'{city_id} 超出既有稀疏 tile 数量边界')
    for tile in city['tiles']:
        tx, tz = tile['x'], tile['z']
        if type(tx) is not int or type(tz) is not int or tx % 16 or tz % 16 or not (0 <= tx < width and 0 <= tz < depth) or (tx, tz) in positions:
            raise ValueError(f'{city_id} 稀疏 tile 重叠或越界')
        positions.add((tx, tz))
        if not re.fullmatch(f'myvillage:province_capitals/{version}/{city_id}/[a-z0-9_]+', tile['template']):
            raise ValueError(f'{city_id} tile 必须与 manifest 的城 ID 和资源版本一致')
        data = read_bounded(resource_file(source, tile['template'], template=True), 8 * 1024 * 1024)
        file_id = 'myvillage:structure/' + tile['template'].split(':', 1)[1] + '.nbt'
        resources[file_id] = sha(data); total_bytes += len(data)
        if resources[file_id] != tile['sha256']:
            raise ValueError(f'{city_id} NBT 声明摘要不符：{file_id}')
    life_inputs(city, width, height, depth, limit)
    manifest_hash = sha(canonical(identity(city)))
    ref = {'city_id': city_id, 'region_id': region, 'resource': manifest,
           'manifest_sha256': manifest_hash,
           'bundle_sha256': sha(canonical({'manifest_sha256': manifest_hash, 'files': resources}))}
    summary = {**ref, 'resource_version': version, 'layout_id': city['life']['layout_id'],
               'layout_family_id': city['layout_family_id'], 'size': size, 'tiles': len(city['tiles']),
               'life_places': len(city['life']['places']), 'files': len(resources) + 1, 'bytes': total_bytes,
               'raw_city_json_sha256': sha(manifest_bytes)}
    return ref, summary


def aggregate(out:Path, v9_out:Path, legacy_out:Path, rules_path:Path=RULES):
    if out.resolve() in {v9_out.resolve(),legacy_out.resolve()}:
        raise ValueError('V10 输出必须独立')
    spec=json.loads(rules_path.read_text())
    if (spec.get('schema'),spec.get('algorithm'),spec.get('base'))!=(1,'myvillage:worldplan_v10','frozen_v9.json'):
        raise ValueError('V10 必须继承 frozen_v9')
    base_path=v9_out/'data/myvillage/worldplan/frozen_v9.json'
    original=read_bounded(base_path,4*1024*1024);base=json.loads(original)
    if base['algorithm']!='myvillage:worldplan_v9':raise ValueError('前置资源不是 V9')
    definitions=spec['assets']['cities'];regions={p['id'] for p in base['profiles']}
    if spec['assets']['accepted_versions']!=['v3'] or len(definitions)!=8 or {c['region_id'] for c in definitions}!=regions or len({c['city_id'] for c in definitions})!=8:
        raise ValueError('V10 必须八州齐全且全部明确采用 v3')
    old_definitions={c['city_id']:c for c in base['capitals']['assets']['cities']}
    for old in base['capitals']['bundles']:
        definition=old_definitions[old['city_id']]
        src=legacy_out if definition['resource_version']=='v1' else v9_out
        actual,_=legacy.inspect_city(src,definition,base['capitals']['life_places_limit'])
        if actual!=old:raise ValueError('旧 V9 资源身份漂移：'+old['city_id'])
    refs=[];summaries=[]
    for definition in sorted(definitions,key=lambda d:d['city_id']):
        ref,summary=inspect_city(out,definition,base['capitals']['life_places_limit'])
        refs.append(ref);summaries.append(summary)
    frozen=copy.deepcopy(base);frozen['algorithm']=spec['algorithm'];frozen['capitals']['assets']=copy.deepcopy(spec['assets']);frozen['capitals']['bundles']=refs
    raw=canonical(frozen);transport='gzip:'+base64.b64encode(gzip.compress(raw,mtime=0)).decode()
    if len(raw)>4*1024*1024 or len(transport)>65000:raise ValueError('V10 冻结规则超出既有边界')
    preset=json.loads((v9_out/'data/myvillage/worldgen/world_preset/cultivation.json').read_text())
    generator=preset['dimensions']['minecraft:overworld']['generator']
    if generator['type']!='myvillage:planned_v9':raise ValueError('前置默认预设不是 V9')
    generator.update(type='myvillage:planned_v10',rules=transport);generator.pop('plan_hash',None)
    write(out/'data/myvillage/worldplan/frozen_v10.json',frozen)
    write(out/'data/myvillage/worldgen/world_preset/cultivation.json',preset)
    report={'schema':1,'format':'capital_game_index','algorithm':spec['algorithm'],'cities':summaries,
            'total_tiles':sum(v['tiles'] for v in summaries),'total_bytes':sum(v['bytes'] for v in summaries),
            'frozen_rules_sha256':sha(raw),'legacy_v9_file_sha256':sha(original),
            'status':{'generation':'completed','native':'not_run','owner_appearance':'not_verified'}}
    write(out/'data/myvillage/capitals/v3/index.json',report)
    if base_path.read_bytes()!=original:raise ValueError('V9 冻结资源被并发改写')
    return report


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=DEFAULT_OUT);p.add_argument('--v9-out',type=Path,default=V9_OUT);p.add_argument('--legacy-out',type=Path,default=LEGACY_OUT);p.add_argument('--skip-base-export',action='store_true');p.add_argument('--aggregate-only',action='store_true');a=p.parse_args()
    if not a.aggregate_only:
        if not a.skip_base_export:subprocess.run([sys.executable,str(Path(__file__).with_name('compile_v9.py')),'--out',str(a.v9_out),'--legacy-out',str(a.legacy_out)],check=True)
        subprocess.run([sys.executable,str(ROOT/'tools/province_capitals/environment_v3/export_game.py'),'--out',str(a.out),'--v9-out',str(a.v9_out),'--legacy-out',str(a.legacy_out)],check=True)
    result=aggregate(a.out,a.v9_out,a.legacy_out)
    print(json.dumps({'V10':'aggregated','cities':len(result['cities']),'tiles':result['total_tiles'],'bytes':result['total_bytes']},ensure_ascii=False))


if __name__=='__main__':main()
