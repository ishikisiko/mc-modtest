"""将拥有者导出的当前资源转为可逐字节恢复的共享几何和分组资源。"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import struct
import time
import zlib

CONFIG = Path(__file__).with_name('packing.json')
VARIABLE_FIELDS = frozenset(('textures', 'texture', 'uv', 'book_regions'))


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf-8')


def digest(blob):
    return hashlib.sha256(blob).hexdigest()


def valid_path(value):
    return (isinstance(value, str) and value and not value.startswith('/')
            and all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_./-' for c in value)
            and all(p not in ('', '.', '..') for p in value.split('/')))


def factor_model(blob):
    """模板保存精确几何文本；每个实例提供材质绑定和 UV 字面量。"""
    model = json.loads(blob)
    constants = ['']
    parameters = []

    def text(value):
        constants[-1] += value

    def emit(value):
        if isinstance(value, dict):
            text('{')
            for i, (key, item) in enumerate(value.items()):
                if i:
                    text(',')
                text(encoded(key).decode() + ':')
                if key in VARIABLE_FIELDS:
                    parameters.append(encoded(item).decode())
                    constants.append('')
                else:
                    emit(item)
            text('}')
        elif isinstance(value, list):
            text('[')
            for i, item in enumerate(value):
                if i:
                    text(',')
                emit(item)
            text(']')
        else:
            text(encoded(value).decode())

    emit(model)
    constants[-1] += '\n'
    assert restore_model(constants, parameters) == blob, '构件模型必须逐字节恢复'
    return constants, parameters


def restore_model(constants, parameters):
    if len(constants) != len(parameters) + 1:
        raise ValueError('几何模板参数数量不符')
    return (''.join(a + b for a, b in zip(constants, parameters)) + constants[-1]).encode()


def encode_records(values):
    out = bytearray(b'MVRB' + struct.pack('>BI', 1, len(values)))
    for value in values:
        out += struct.pack('>I', len(value)) + value
    return bytes(out)


def decode_records(blob, maximum):
    if len(blob) < 9 or blob[:5] != b'MVRB\x01':
        raise ValueError('分组资源格式错误')
    count, = struct.unpack_from('>I', blob, 5)
    if count > 4096:
        raise ValueError('分组记录数量越界')
    offset = 9
    out = []
    for _ in range(count):
        if offset + 4 > len(blob):
            raise ValueError('分组记录截断')
        size, = struct.unpack_from('>I', blob, offset)
        offset += 4
        if size > maximum or offset + size > len(blob):
            raise ValueError('分组记录大小越界')
        out.append(blob[offset:offset + size])
        offset += size
    if offset != len(blob):
        raise ValueError('分组资源存在尾部数据')
    return out


def png_recompress(blob):
    """保留所有非 IDAT chunk 和过滤后的像素流，只调整无损压缩。"""
    if not blob.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('PNG 文件头错误')
    chunks = []
    offset = 8
    while offset < len(blob):
        if offset + 12 > len(blob):
            raise ValueError('PNG chunk 截断')
        size, = struct.unpack_from('>I', blob, offset)
        end = offset + 12 + size
        if end > len(blob):
            raise ValueError('PNG chunk 长度越界')
        kind = blob[offset + 4:offset + 8]
        data = blob[offset + 8:end - 4]
        crc, = struct.unpack_from('>I', blob, end - 4)
        if zlib.crc32(kind + data) & 0xffffffff != crc:
            raise ValueError('PNG CRC 错误')
        chunks.append((kind, data, blob[offset:end]))
        offset = end
    if any(kind in (b'acTL', b'fcTL', b'fdAT') for kind, _, _ in chunks):
        return blob
    filtered = zlib.decompress(b''.join(data for kind, data, _ in chunks if kind == b'IDAT'))
    packed = zlib.compress(filtered, 9)
    fresh = bytearray(blob[:8])
    inserted = False
    for kind, _, original in chunks:
        if kind == b'IDAT':
            if not inserted:
                fresh += struct.pack('>I', len(packed)) + kind + packed
                fresh += struct.pack('>I', zlib.crc32(kind + packed) & 0xffffffff)
                inserted = True
        else:
            fresh += original
    return bytes(fresh) if len(fresh) < len(blob) else blob


def pack(directory, report=None, config_path=CONFIG):
    started = time.monotonic()
    cfg = json.loads(config_path.read_text())
    if cfg['schema'] != 1 or not valid_path(cfg['root']):
        raise ValueError('资源打包配置错误')
    root = cfg['root']
    namespace = cfg['namespace']
    temporary = directory / (root + '.tmp')
    if temporary.exists():
        shutil.rmtree(temporary)
    temporary.mkdir(parents=True)
    chunks = []
    entries = []
    templates = []
    template_ids = {}
    template_uses = Counter()
    template_buildings = defaultdict(set)
    recipes = []
    sources = {}
    banks = []
    for name in cfg['catalogs']:
        catalog = json.loads((directory / name).read_bytes())
        banks.extend(catalog['banks'])
    if len({bank['id'] for bank in banks}) != len(banks):
        raise ValueError('构件组重复')

    def chunk(codec, payload, count):
        if len(payload) > cfg['max_group_bytes']:
            raise ValueError('分组资源超过大小限制')
        number = len(chunks)
        name = f'{number:05d}.{codec}.gz'
        value = gzip.compress(payload, compresslevel=cfg['compression_level'], mtime=0)
        (temporary / name).write_bytes(value)
        chunks.append([root + '/' + name, codec, len(payload), digest(value), count])
        return number

    def record(name, group, index, value):
        if not valid_path(name) or len(value) > cfg['max_file_bytes'] or name in sources:
            raise ValueError('资源路径、重复或大小错误：' + name)
        sources[name] = value
        entries.append([name, group, index, digest(value), len(value)])

    def raw_group(names):
        values = [(directory / name).read_bytes() for name in names]
        blob = encode_records(values)
        assert decode_records(blob, cfg['max_file_bytes']) == values
        number = chunk('bytes', blob, len(names))
        for i, (name, value) in enumerate(zip(names, values)):
            record(name, number, i, value)

    for bank in sorted(banks, key=lambda b: b['id']):
        ident = bank['id']
        names = [f'assets/{namespace}/models/block/{ident}/shape_{i}.json' for i in range(len(bank['profiles']))]
        for off in range(0, len(names), cfg['models_per_group']):
            batch = names[off:off + cfg['models_per_group']]
            instances = []
            for name in batch:
                value = (directory / name).read_bytes()
                constants, parameters = factor_model(value)
                key = digest(encoded(constants))
                if key not in template_ids:
                    template_ids[key] = len(templates)
                    templates.append(constants)
                template = template_ids[key]
                assert templates[template] == constants, '共享模板摘要冲突'
                instances.append([template, parameters])
                template_uses[template] += 1
                building = 'qianji' if ident.startswith('qianji_') else ident.split('_carved_')[0]
                template_buildings[template].add(building)
            number = chunk('models', encoded(instances), len(instances))
            for i, name in enumerate(batch):
                record(name, number, i, (directory / name).read_bytes())
        linked = [f'assets/{namespace}/blockstates/{ident}.json', f'assets/{namespace}/models/item/{ident}.json',
                  f'data/{namespace}/loot_table/blocks/{ident}.json']
        raw_group(linked)

    other_models = defaultdict(list)
    for path in sorted((directory / 'assets' / namespace / 'models').rglob('*')):
        if path.is_file() and path.suffix in cfg['client_model_extensions']:
            name = path.relative_to(directory).as_posix()
            if name not in sources:
                other_models[name.rsplit('/', 1)[0]].append(name)
    for _, names in sorted(other_models.items()):
        for off in range(0, len(names), cfg['files_per_group']):
            raw_group(names[off:off + cfg['files_per_group']])

    for off in range(0, len(templates), cfg['templates_per_group']):
        batch = templates[off:off + cfg['templates_per_group']]
        number = chunk('templates', encoded(batch), len(batch))
        recipes.extend([number, i] for i in range(len(batch)))

    nbt_groups = defaultdict(list)
    for path in sorted((directory / 'data' / namespace / 'structure').rglob('*.nbt')):
        name = path.relative_to(directory).as_posix()
        nbt_groups[name.rsplit('/', 1)[0]].append(name)
    for _, names in sorted(nbt_groups.items()):
        for off in range(0, len(names), cfg['files_per_group']):
            raw_group(names[off:off + cfg['files_per_group']])

    if len(entries) > cfg['max_entries']:
        raise ValueError('资源清单条目越界')
    limits = {k: cfg[k] for k in ('max_entries', 'max_file_bytes', 'max_group_bytes', 'max_manifest_bytes', 'cache_bytes', 'cache_groups')}
    manifest = dict(schema=1, namespace=namespace, limits=limits, chunks=chunks, templates=recipes, entries=entries)
    blob = encoded(manifest) + b'\n'
    if len(blob) > cfg['max_manifest_bytes']:
        raise ValueError('资源清单体积越界')
    (temporary / 'manifest.json').write_bytes(blob)
    (temporary / 'rules.json').write_bytes(encoded(cfg) + b'\n')
    # 复验实际写出的压缩块，而不是只检查构造过程中的内存对象。
    decoded = {}
    for number, (name, codec, size, sha, count) in enumerate(chunks):
        value = (temporary / name.rsplit('/', 1)[1]).read_bytes()
        assert digest(value) == sha
        raw = gzip.decompress(value)
        assert len(raw) == size
        records = decode_records(raw, cfg['max_file_bytes']) if codec == 'bytes' else json.loads(raw)
        assert len(records) == count
        decoded[number] = records
    for name, group, index, sha, size in entries:
        item = decoded[group][index]
        if chunks[group][1] == 'models':
            template, parameters = item
            tg, ti = recipes[template]
            item = restore_model(decoded[tg][ti], parameters)
        assert len(item) == size and digest(item) == sha and item == sources[name], name

    target = directory / root
    if target.exists():
        shutil.rmtree(target)
    temporary.rename(target)
    for name in sources:
        (directory / name).unlink()
    pngs = changed_pngs = png_saved = 0
    if cfg['png_recompress']:
        for path in (directory / 'assets').rglob('*.png'):
            before = path.read_bytes()
            after = png_recompress(before)
            pngs += 1
            if after != before:
                path.write_bytes(after)
                changed_pngs += 1
                png_saved += len(before) - len(after)
    result = dict(logical_files=len(entries), physical_chunks=len(chunks), model_instances=sum(template_uses.values()),
                  shared_geometry_templates=len(templates), repeated_template_references=sum(v - 1 for v in template_uses.values()),
                  cross_building_templates=sum(len(v) > 1 for v in template_buildings.values()),
                  cross_building_model_instances=sum(template_uses[k] for k, v in template_buildings.items() if len(v) > 1),
                  other_model_resources=sum(len(v) for v in other_models.values()),
                  structures=sum(len(v) for v in nbt_groups.values()), pngs_checked=pngs, pngs_recompressed=changed_pngs,
                  png_raw_bytes_saved=png_saved, manifest_bytes=len(blob), verified_bytes=sum(map(len, sources.values())),
                  seconds=round(time.monotonic() - started, 3), all_logical_bytes_restored=True)
    if report:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False), flush=True)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    pack(args.input, args.report)
