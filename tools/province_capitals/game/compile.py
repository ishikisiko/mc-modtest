#!/usr/bin/env python3
"""正式八州资源聚合入口；不导入预览 manifest，不改各作者的 city.json。"""
from __future__ import annotations
import argparse
from array import array
import copy
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = ROOT / 'build/generated/province_capitals/resources'
BASE = ROOT / 'build/generated/worldplan/resources/data/myvillage/worldplan/frozen_v7.json'
# This projection is shared with CapitalGameBundle.identityJson. Provenance/status are diagnostics,
# never geometry inputs; changing a timestamp cannot change a saved city's identity.
IDENTITY_KEYS = ('schema', 'format', 'city_id', 'region_id', 'display_name', 'layout_family_id',
                 'size', 'source_offset', 'placement', 'terrain', 'tiles', 'entries', 'life')


def canonical(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def identity(city):
    """Only fields actually consumed by worldgen/life; omit nested report/binding timestamps too."""
    def pick(value, keys):
        return {key: value[key] for key in keys.split()}
    projected = {key: city[key] for key in IDENTITY_KEYS}
    projected['placement'] = pick(city['placement'], 'vertical_mode anchor_y rotation_quarters blend_margin')
    projected['terrain'] = pick(city['terrain'], 'resource sha256 encoding width depth top_block fill_block subsurface_block subsurface_depth')
    projected['tiles'] = [pick(tile, 'x z template sha256') for tile in city['tiles']]
    projected['entries'] = [pick(entry, 'id position facing') for entry in city['entries']]
    projected['life'] = {'layout_id': city['life']['layout_id'],
                         'places': [pick(point, 'id kind pos capacity block block_pos') for point in city['life']['places']]}
    return projected


def write(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n', encoding='utf-8')


def resource_file(out: Path, resource: str, *, template=False) -> Path:
    namespace, relative = resource.split(':', 1)
    if namespace != 'myvillage' or '..' in relative.split('/') or relative.startswith('/'):
        raise ValueError(f'不合法的正式资源路径：{resource}')
    return out / 'data' / namespace / (f'structure/{relative}.nbt' if template else relative)


def local_point(value, width, height, depth, name):
    if not isinstance(value, list) or len(value) != 3 or any(type(v) is not int for v in value):
        raise ValueError(f'{name} 必须是整数坐标')
    x, y, z = value
    if not (0 <= x < width and 1 <= y < height - 1 and 0 <= z < depth):
        raise ValueError(f'{name} 不在规范化本地范围内')


def life_inputs(city, width, height, depth, limit):
    seen = set()
    for point in city['life']['places']:
        if point['id'] in seen or point['kind'] not in ('rest', 'work', 'gather', 'garden', 'market', 'social', 'entry', 'cultivate'):
            raise ValueError(f'{city["city_id"]} 岗位重复或类型不合法')
        seen.add(point['id'])
        local_point(point['pos'], width, height, depth, point['id'])
        if type(point['capacity']) is not int or not 1 <= point['capacity'] <= 64:
            raise ValueError(f'{point["id"]} 容量越界')
        if point['block']:
            local_point(point['block_pos'], width, height, depth, point['id'] + '/fixture')
    required = {'inn_work', 'inn_rest', 'inn_social', 'guest_west', 'herbalist_work', 'herbalist_rest', 'smith_work', 'smith_rest'}
    if not required <= seen or not 1 <= len(seen) <= limit:
        raise ValueError(f'{city["city_id"]} 缺少协调契约的服务岗位：{sorted(required - seen)}')
    kinds = {point['kind'] for point in city['life']['places']}
    if not {'entry', 'market', 'cultivate'} <= kinds:
        raise ValueError(f'{city["city_id"]} 缺少入口、市场或修炼场所')
    entries = city['entries']
    if not 1 <= len(entries) <= 32 or len({entry['id'] for entry in entries}) != len(entries):
        raise ValueError(f'{city["city_id"]} 入口数量或身份不合法')
    for entry in entries:
        local_point(entry['position'], width, height, depth, entry['id'])
        if entry['facing'] not in ('north', 'south', 'east', 'west'):
            raise ValueError(f'{entry["id"]} 朝向不合法')


def aggregate(out: Path, base_rules: Path) -> dict:
    catalog = json.loads((ROOT / 'blueprints/province_capitals/catalog.json').read_text())
    spec = json.loads((ROOT / 'blueprints/worldplan/rules_v8.json').read_text())
    if not base_rules.is_file():
        raise FileNotFoundError(f'缺少原有资源任务的产物 {base_rules}；先正常生成 generateWorldPlanAssets 的资源，不运行其 --check 或测试套件')
    base = json.loads(base_rules.read_text())
    if base['algorithm'] != 'myvillage:worldplan_v7':
        raise ValueError('V8 只从独立 frozen_v7 输入继承自然地形与聚落配置')
    refs, summaries = [], []
    seen = set()
    for definition in sorted(catalog['capitals'], key=lambda c: c['city_id']):
        city_id, region = definition['city_id'], definition['region_id']
        path = out / f'data/myvillage/capitals/v1/{city_id}/city.json'
        if not path.is_file():
            raise FileNotFoundError(f'正式八城缺城，拒绝生成不完整 V8：{path}')
        city = json.loads(path.read_text())
        if (city.get('schema'), city.get('format'), city.get('city_id'), city.get('region_id')) != (1, 'capital_game_bundle', city_id, region):
            raise ValueError(f'{path} 必须是对应城市的 capital_game_bundle schema=1')
        if city['layout_family_id'] != definition['layout_id']:
            raise ValueError(f'{city_id} 的布局族与公共目录不符')
        if region in seen:
            raise ValueError(f'重复州域：{region}')
        seen.add(region)
        width, height, depth = city['size']
        if any(type(n) is not int for n in city['size']) or not (16 <= width <= 2048 and 8 <= height <= 320 and 16 <= depth <= 2048) or width % 16 or depth % 16:
            raise ValueError(f'{city_id} 的正式整数 tile 尺寸越界')
        placement = city['placement']
        if placement['vertical_mode'] not in ('absolute', 'sea_relative', 'terrain_relative') or placement['rotation_quarters'] != 0:
            raise ValueError(f'{city_id} 的放置语义不受 V8 支持')
        if region == 'zhongzhou' and (placement['vertical_mode'], placement['anchor_y']) != ('absolute', 0):
            raise ValueError('中州必须保持 absolute originY=0')
        if not 16 <= placement['blend_margin'] <= 256:
            raise ValueError(f'{city_id} 的融合外缘越界')
        terrain = city['terrain']
        if (terrain['encoding'], terrain['width'], terrain['depth']) != ('i16le_z_major_top_solid', width, depth):
            raise ValueError(f'{city_id} heightfield 必须记录 top-solid，而非站立空气格')
        terrain_path = resource_file(out, terrain['resource'])
        raw_height = gzip.decompress(terrain_path.read_bytes())
        if len(raw_height) != width * depth * 2:
            raise ValueError(f'{city_id} heightfield 长度不符')
        heights = array('h'); heights.frombytes(raw_height)
        if sys.byteorder != 'little':
            heights.byteswap()
        if min(heights) < 0 or max(heights) >= height:
            raise ValueError(f'{city_id} top-solid 越出本地 Y 范围')
        resources = {terrain['resource']: sha(terrain_path.read_bytes())}
        if resources[terrain['resource']] != terrain['sha256']:
            raise ValueError(f'{city_id} 地形声明摘要不符')
        positions = set()
        for tile in city['tiles']:
            tx, tz = tile['x'], tile['z']
            if type(tx) is not int or type(tz) is not int or tx % 16 or tz % 16 or not (0 <= tx < width and 0 <= tz < depth) or (tx, tz) in positions:
                raise ValueError(f'{city_id} 稀疏 tile 重叠或越界')
            positions.add((tx, tz))
            if not tile['template'].startswith(f'myvillage:province_capitals/v1/{city_id}/'):
                raise ValueError(f'{city_id} tile 不属于其正式资源族')
            file_id = 'myvillage:structure/' + tile['template'].split(':', 1)[1] + '.nbt'
            resources[file_id] = sha(resource_file(out, tile['template'], template=True).read_bytes())
            if resources[file_id] != tile['sha256']:
                raise ValueError(f'{city_id} NBT 声明摘要不符：{file_id}')
        if not city['entries'] or not city['life']['places']:
            raise ValueError(f'{city_id} 缺入口或真实生活位置')
        life_inputs(city, width, height, depth, spec['planning']['life_places_limit'])
        manifest_hash = sha(canonical(identity(city)))
        content_hash = sha(canonical({'manifest_sha256': manifest_hash, 'files': resources}))
        ref = {'city_id': city_id, 'region_id': region,
               'resource': f'myvillage:capitals/v1/{city_id}/city.json',
               'manifest_sha256': manifest_hash, 'bundle_sha256': content_hash}
        refs.append(ref)
        summaries.append({**ref, 'layout_id': city['life']['layout_id'], 'layout_family_id': city['layout_family_id'],
                          'size': city['size'], 'tiles': len(city['tiles']), 'files': len(resources) + 1,
                          'bytes': path.stat().st_size + sum(resource_file(out, key).stat().st_size for key in resources),
                          'raw_city_json_sha256': sha(path.read_bytes())})
    if len(seen) != 8 or len(refs) != 8 or seen != {p['id'] for p in base['profiles']}:
        raise ValueError('V8 必须八州齐全，每州一城（含魔域），不允许候选耗尽后跳过')
    frozen = copy.deepcopy(base)
    frozen.pop('city')
    frozen.update(spec['world'])
    frozen['algorithm'] = spec['algorithm']
    frozen['capitals'] = {**spec['planning'], 'schema': 1, 'bundles': refs}
    text = 'gzip:' + __import__('base64').b64encode(gzip.compress(canonical(frozen), mtime=0)).decode('ascii')
    if len(text) > 65000:
        raise ValueError('V8 冻结规则超出 NBT 字符串界限；不得把整城 NBT/heightfield 塞入 level.dat')
    preset_path = base_rules.parent.parent / 'worldgen/world_preset/cultivation.json'
    preset = json.loads(preset_path.read_text())
    generator = preset['dimensions']['minecraft:overworld']['generator']
    generator.update(type='myvillage:planned_v8', rules=text)
    generator.pop('plan_hash', None)
    write(out / 'data/myvillage/worldplan/frozen_v8.json', frozen)
    write(out / 'data/myvillage/worldgen/world_preset/cultivation.json', preset)
    report = {'schema': 1, 'format': 'capital_game_index', 'algorithm': spec['algorithm'], 'cities': summaries,
              'total_tiles': sum(c['tiles'] for c in summaries), 'total_bytes': sum(c['bytes'] for c in summaries),
              'frozen_rules_sha256': sha(canonical(frozen)), 'frozen_transport_chars': len(text),
              'status': {'tests': 'not_run', 'compile': 'not_run', 'minecraft': 'not_run', 'owner_appearance': 'not_verified'}}
    write(out / 'data/myvillage/capitals/v1/index.json', report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    parser.add_argument('--base-rules', type=Path, default=BASE)
    parser.add_argument('--aggregate-only', action='store_true', help='只聚合已有正式导出，便于共享作者工作区；不是检查模式')
    args = parser.parse_args()
    out = args.out.resolve()
    if not args.aggregate_only:
        for owner in ('regional', 'central'):
            script = ROOT / f'tools/province_capitals/{owner}/export_game.py'
            if not script.is_file():
                raise FileNotFoundError(f'缺少 owning exporter：{script}')
            subprocess.run([sys.executable, str(script), '--out', str(out)], cwd=ROOT, check=True)
    report = aggregate(out, args.base_rules.resolve())
    print(json.dumps({k: report[k] for k in ('algorithm', 'total_tiles', 'total_bytes', 'frozen_transport_chars')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
