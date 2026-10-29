#!/usr/bin/env python3
"""七城 v2 与中州 v1 的独立 V9 聚合入口；只写 V9 输出，不迁移旧世界。"""
from __future__ import annotations

import argparse
from array import array
import base64
import copy
import gzip
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = ROOT / 'build/generated/province_capitals_v9/resources'
LEGACY_OUT = ROOT / 'build/generated/province_capitals/resources'
RULES = ROOT / 'blueprints/worldplan/rules_v9.json'
SUPPORTED_VERSIONS = frozenset(('v1', 'v2'))
OPTIONAL_CATALOGUE = 'exmod/mod_block_catalog.json'

# 共用原 V8 的身份投影和生活位置解析，旧入口及其输出逻辑保持不变。
_module_spec = importlib.util.spec_from_file_location('province_capitals_v8', Path(__file__).with_name('compile.py'))
_v8 = importlib.util.module_from_spec(_module_spec)
_module_spec.loader.exec_module(_v8)
canonical, identity, life_inputs = _v8.canonical, _v8.identity, _v8.life_inputs
resource_file, sha, write = _v8.resource_file, _v8.sha, _v8.write


def read_bounded(path: Path, maximum: int) -> bytes:
    with path.open('rb') as stream:
        data = stream.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError(f'正式资源超出既有读取上限：{path}')
    return data


def definitions(spec: dict, regions: set[str]) -> list[dict]:
    if (spec.get('schema'), spec.get('algorithm'), spec.get('base')) != (1, 'myvillage:worldplan_v9', 'frozen_v8.json'):
        raise ValueError('V9 必须继承独立 frozen_v8，不能改写旧聚合规则')
    assets = spec['assets']
    versions = assets['accepted_versions']
    if not versions or len(set(versions)) != len(versions) or not set(versions) <= SUPPORTED_VERSIONS:
        raise ValueError('V9 资源版本必须显式列在 v1/v2 白名单中')
    cities = assets['cities']
    if len(cities) != len(regions) or {c['region_id'] for c in cities} != regions:
        raise ValueError('V9 必须八州齐全，每州一城')
    seen = set()
    for city in cities:
        if city['city_id'] != 'capital_' + city['region_id'] or city['city_id'] in seen:
            raise ValueError('V9 主城身份重复或与州域不匹配')
        seen.add(city['city_id'])
        if city['resource_version'] not in versions:
            raise ValueError(f'{city["city_id"]} 的资源版本未获配置允许')
    return sorted(cities, key=lambda city: city['city_id'])


def inspect_city(source: Path, definition: dict, limit: int) -> tuple[dict, dict]:
    city_id, region, version = (definition[key] for key in ('city_id', 'region_id', 'resource_version'))
    if version not in SUPPORTED_VERSIONS or city_id != 'capital_' + region or not re.fullmatch(r'[a-z0-9_]+', region):
        raise ValueError('主城身份或资源版本不合法')
    manifest = f'myvillage:capitals/{version}/{city_id}/city.json'
    path = resource_file(source, manifest)
    if not path.is_file():
        raise FileNotFoundError(f'正式主城尚未齐备，拒绝聚合不完整 V9：{path}')
    manifest_bytes = read_bounded(path, 8 * 1024 * 1024)
    city = json.loads(manifest_bytes)
    if (city.get('schema'), city.get('format'), city.get('city_id'), city.get('region_id')) != (1, 'capital_game_bundle', city_id, region):
        raise ValueError(f'{manifest} 不是对应城市的 capital_game_bundle schema=1')
    if 'layout_family_id' in definition and city['layout_family_id'] != definition['layout_family_id']:
        raise ValueError(f'{city_id} 的布局族与 V9 配置不符')
    if 'life_layout_id' in definition and city['life']['layout_id'] != definition['life_layout_id']:
        raise ValueError(f'{city_id} 的生活布局身份与 V9 配置不符')
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
    if region == 'zhongzhou' and (version, placement['vertical_mode'], placement['anchor_y']) != ('v1', 'absolute', 0):
        raise ValueError('中州必须保持 v1 和 absolute originY=0')
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


def aggregate(out: Path, legacy_out: Path, rules_path: Path = RULES) -> dict:
    if out.resolve() == legacy_out.resolve():
        raise ValueError('V9 输出必须与旧 V8 输出分开，不能覆盖旧 cultivation.json')
    spec = json.loads(rules_path.read_text(encoding='utf-8'))
    base_path = legacy_out / 'data/myvillage/worldplan/frozen_v8.json'
    if not base_path.is_file():
        raise FileNotFoundError(f'缺少 owning V8 聚合产物：{base_path}；先正常运行旧 compile.py')
    base_bytes = read_bounded(base_path, 4 * 1024 * 1024)
    base = json.loads(base_bytes)
    if base.get('algorithm') != 'myvillage:worldplan_v8':
        raise ValueError('V9 仅从独立 frozen_v8 继承统一选址、出生与地形规则')
    v8_spec = json.loads((ROOT / 'blueprints/worldplan/rules_v8.json').read_text(encoding='utf-8'))
    if any(base.get(key) != value for key, value in v8_spec['world'].items()) or any(base['capitals'].get(key) != value for key, value in v8_spec['planning'].items()):
        raise ValueError('旧 frozen_v8 与 authored 世界/选址/生活预算不一致，拒绝改变旧语义')
    regions = {profile['id'] for profile in base['profiles']}
    configured = definitions(spec, regions)
    old_refs = {ref['city_id']: ref for ref in base['capitals']['bundles']}
    if len(old_refs) != len(regions) or len(base['capitals']['bundles']) != len(regions):
        raise ValueError('旧 frozen_v8 缺城或身份重复')
    # V8 的全部 v1 清单、地形和切片仍须存在且与冻结摘要一致，不能只留中州。
    refs, summaries = [], []
    for definition in configured:
        source = legacy_out if definition['resource_version'] == 'v1' else out
        ref, summary = inspect_city(source, definition, base['capitals']['life_places_limit'])
        refs.append(ref); summaries.append(summary)
    frozen = copy.deepcopy(base)
    frozen['algorithm'] = spec['algorithm']
    frozen['capitals']['bundles'] = refs
    frozen['capitals']['assets'] = copy.deepcopy(spec['assets'])
    raw = canonical(frozen)
    transport = 'gzip:' + base64.b64encode(gzip.compress(raw, mtime=0)).decode('ascii')
    if len(raw) > 4 * 1024 * 1024 or len(transport) > 65000:
        raise ValueError('V9 冻结规则超出既有内存或 NBT 字符串边界')
    preset_path = legacy_out / 'data/myvillage/worldgen/world_preset/cultivation.json'
    preset = json.loads(preset_path.read_text(encoding='utf-8'))
    generator = preset['dimensions']['minecraft:overworld']['generator']
    if generator['type'] != 'myvillage:planned_v8':
        raise ValueError('V9 默认预设必须继承 owning V8 预设')
    generator.update(type='myvillage:planned_v9', rules=transport)
    generator.pop('plan_hash', None)
    report = {'schema': 1, 'format': 'capital_game_index', 'algorithm': spec['algorithm'], 'cities': summaries,
              'total_tiles': sum(c['tiles'] for c in summaries), 'total_bytes': sum(c['bytes'] for c in summaries),
              'frozen_rules_sha256': sha(raw), 'frozen_transport_chars': len(transport),
              'legacy_v8_rules_sha256': sha(canonical(base)), 'legacy_v8_file_sha256': sha(base_bytes),
              'status': {'tests': 'not_run', 'compile': 'not_run', 'minecraft': 'not_run', 'owner_appearance': 'not_verified'}}
    # 全部输入通过消费者边界后才写本版本产物；旧输出目录从不写入。
    write(out / 'data/myvillage/worldplan/frozen_v9.json', frozen)
    write(out / 'data/myvillage/worldgen/world_preset/cultivation.json', preset)
    write(out / 'data/myvillage/capitals/v2/index.json', report)
    return report


def audit_legacy(legacy_out: Path, baseline: Path, *, catalogue_sha256: str | None = None) -> dict:
    """对照保留的原件逐字段说明诊断变化；frozen、terrain、NBT 不接受字节差异。"""
    if catalogue_sha256 is not None:
        catalogue = ROOT / OPTIONAL_CATALOGUE
        if (not re.fullmatch(r'[0-9a-f]{64}', catalogue_sha256) or not catalogue.is_file()
                or sha(catalogue.read_bytes()) != catalogue_sha256):
            raise ValueError('显式 catalogue 摘要与当前实际文件不符')
    records = json.loads(baseline.read_text(encoding='utf-8'))
    legacy_out = legacy_out.resolve()
    original_json = baseline.parent / 'legacy-json-before'
    changed, unchanged = [], 0
    protected = [legacy_out / f'data/myvillage/{relative}' for relative in (
        'worldplan', 'capitals/v1', 'structure/province_capitals/v1')]
    known = {(ROOT / key).resolve(): digest for key, digest in records.items()}
    current = {p.resolve() for directory in protected for p in directory.rglob('*') if p.is_file()}
    expected = {p for p in known if p.is_relative_to(legacy_out)}
    if current != expected:
        raise ValueError(f'旧资源文件集合变化：新增 {sorted(str(p) for p in current - expected)}；缺失 {sorted(str(p) for p in expected - current)}')
    for path in sorted(expected):
        data = path.read_bytes()
        if sha(data) == known[path]:
            unchanged += 1
            continue
        relative = path.relative_to(legacy_out)
        snapshot = original_json / relative
        fields = []
        if snapshot.is_file() and sha(snapshot.read_bytes()) != known[path]:
            raise ValueError(f'原始 JSON 基线原件损坏：{snapshot}')
        if path.name == 'city.json' and '/capitals/v1/' in path.as_posix():
            before, after = json.loads(snapshot.read_text(encoding='utf-8')), json.loads(data)
            if identity(before) != identity(after):
                raise ValueError(f'旧主城运行时身份漂移：{path}')
            old_hashes, new_hashes = before['source']['hashes'], after['source']['hashes']
            added, removed = new_hashes.keys() - old_hashes.keys(), old_hashes.keys() - new_hashes.keys()
            if removed or added - {OPTIONAL_CATALOGUE}:
                raise ValueError(f'旧主城诊断字段增删，需单独核对：{path}')
            restored = copy.deepcopy(after); restored['source']['hashes'] = old_hashes
            if added:
                # The unchanged v1 exporter records an optional checkout catalogue even
                # for vanilla. Admit that provenance only with an explicit file digest.
                old_modset = before['source'].get('modset', {})
                new_modset = after['source'].get('modset', {})
                if (catalogue_sha256 is None or new_hashes[OPTIONAL_CATALOGUE] != catalogue_sha256
                        or old_modset.get('profile') != 'vanilla' or new_modset.get('profile') != 'vanilla'
                        or old_modset.get('external_catalogue_required') is not False
                        or new_modset.get('external_catalogue_required') is not False
                        or old_modset.get('external_catalogue') is not None
                        or new_modset.get('external_catalogue') != OPTIONAL_CATALOGUE):
                    raise ValueError(f'可选 catalogue 来源变化缺少明确摘要或 vanilla 来源配对：{path}')
                restored['source']['modset']['external_catalogue'] = None
                fields.append({'field': 'source.modset.external_catalogue', 'before': None, 'after': OPTIONAL_CATALOGUE})
            if restored != before:
                raise ValueError(f'旧主城存在已核验来源字段以外的内容变化：{path}')
            for source in sorted(new_hashes):
                if old_hashes.get(source) == new_hashes[source]:
                    continue
                file = ROOT / source
                if not file.is_file() or sha(file.read_bytes()) != new_hashes[source]:
                    raise ValueError(f'旧主城诊断摘要与实际源不符：{path} / {source}')
                fields.append({'field': 'source.hashes[' + source + ']', 'before': old_hashes.get(source), 'after': new_hashes[source]})
        elif relative.as_posix() == 'data/myvillage/capitals/v1/index.json':
            before, after = json.loads(snapshot.read_text(encoding='utf-8')), json.loads(data)
            restored = copy.deepcopy(after)
            if len(before['cities']) != len(after['cities']):
                raise ValueError('旧 index 城市数量变化')
            for i, (old, new) in enumerate(zip(before['cities'], after['cities'])):
                city_path = legacy_out / 'data/myvillage/capitals/v1' / old['city_id'] / 'city.json'
                prior_path = original_json / city_path.relative_to(legacy_out)
                prior_bytes, current_bytes = prior_path.read_bytes(), city_path.read_bytes()
                if (city_path not in known or sha(prior_bytes) != known[city_path]
                        or old['raw_city_json_sha256'] != sha(prior_bytes)):
                    raise ValueError('旧 index 的原始清单摘要与保留基线不符')
                if new['city_id'] != old['city_id'] or new['raw_city_json_sha256'] != sha(current_bytes):
                    raise ValueError('旧 index 的原始清单摘要与实际城市不符')
                city = json.loads(current_bytes)
                resource_bytes = resource_file(legacy_out, city['terrain']['resource']).stat().st_size
                resource_bytes += sum(resource_file(legacy_out, tile['template'], template=True).stat().st_size for tile in city['tiles'])
                if (type(old.get('bytes')) is not int or old['bytes'] != len(prior_bytes) + resource_bytes
                        or type(new.get('bytes')) is not int or new['bytes'] != len(current_bytes) + resource_bytes):
                    raise ValueError('旧 index 字节计数与实际清单、地形和 NBT 不符')
                if old['raw_city_json_sha256'] != new['raw_city_json_sha256']:
                    fields.append({'field': f'cities[{i}].raw_city_json_sha256', 'city_id': old['city_id'],
                                   'before': old['raw_city_json_sha256'], 'after': new['raw_city_json_sha256']})
                restored['cities'][i]['raw_city_json_sha256'] = old['raw_city_json_sha256']
                if old['bytes'] != new['bytes']:
                    fields.append({'field': f'cities[{i}].bytes', 'city_id': old['city_id'],
                                   'before': old['bytes'], 'after': new['bytes']})
                restored['cities'][i]['bytes'] = old['bytes']
            for index in (before, after):
                if type(index.get('total_bytes')) is not int or index['total_bytes'] != sum(city['bytes'] for city in index['cities']):
                    raise ValueError('旧 index 总字节数与逐城实际计数不符')
            if before['total_bytes'] != after['total_bytes']:
                fields.append({'field': 'total_bytes', 'before': before['total_bytes'], 'after': after['total_bytes']})
            restored['total_bytes'] = before['total_bytes']
            if restored != before:
                raise ValueError('旧 index 存在实际清单摘要和字节计数以外的变化')
        else:
            raise ValueError(f'旧 frozen、terrain、NBT 或其它资源字节漂移：{path}')
        if not fields:
            raise ValueError(f'旧 JSON 仅序列化字节变化，不能冒充已说明的诊断差异：{path}')
        changed.append({'resource': relative.as_posix(), 'before_sha256': known[path], 'after_sha256': sha(data), 'fields': fields})
    return {'schema': 1, 'byte_identical_files': unchanged, 'diagnostic_changed_files': changed,
            'runtime_identity': 'unchanged', 'frozen_terrain_nbt': 'byte_identical',
            'scope': '仅覆盖原始基线中实际存在的资源；未在基线中的旧版本需最终打包检查'}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    parser.add_argument('--legacy-out', type=Path, default=LEGACY_OUT)
    parser.add_argument('--rules', type=Path, default=RULES)
    parser.add_argument('--skip-legacy-export', action='store_true', help='已由 Gradle 前置任务正常生成 V8，仅导出七城 v2 后聚合')
    parser.add_argument('--aggregate-only', action='store_true', help='只聚合现有 v1/v2 正式输出；缺城报错，不是检查通过')
    parser.add_argument('--legacy-baseline', type=Path, help='原始逐文件 SHA 基线；相邻 legacy-json-before 保留原 JSON')
    parser.add_argument('--catalogue-sha256', help='显式核验可选 catalogue 来源新增；仅用于旧 vanilla 清单的诊断审阅')
    parser.add_argument('--audit-only', action='store_true', help='仅逐字段审阅旧资源差异，不导出或聚合')
    args = parser.parse_args()
    if args.catalogue_sha256 is not None and args.legacy_baseline is None:
        parser.error('--catalogue-sha256 必须与 --legacy-baseline 同用')
    out, legacy_out = args.out.resolve(), args.legacy_out.resolve()
    if args.audit_only:
        if args.legacy_baseline is None:
            parser.error('--audit-only 必须给出 --legacy-baseline')
        print(json.dumps(audit_legacy(legacy_out, args.legacy_baseline.resolve(), catalogue_sha256=args.catalogue_sha256), ensure_ascii=False, indent=2))
        return
    if out == legacy_out:
        raise ValueError('V9 与旧 V8 必须使用独立输出目录')
    if not args.aggregate_only:
        if not args.skip_legacy_export:
            subprocess.run([sys.executable, str(Path(__file__).with_name('compile.py')), '--out', str(legacy_out)], cwd=ROOT, check=True)
        exporter = ROOT / 'tools/province_capitals/regional/v2/export_game.py'
        if not exporter.is_file():
            raise FileNotFoundError(f'缺少七城 v2 owning exporter：{exporter}')
        subprocess.run([sys.executable, str(exporter), '--out', str(out)], cwd=ROOT, check=True)
    if args.legacy_baseline is not None:
        print(json.dumps(audit_legacy(legacy_out, args.legacy_baseline.resolve(), catalogue_sha256=args.catalogue_sha256), ensure_ascii=False))
    report = aggregate(out, legacy_out, args.rules.resolve())
    print(json.dumps({key: report[key] for key in ('algorithm', 'total_tiles', 'total_bytes', 'frozen_transport_chars')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
