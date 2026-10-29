"""保留认证源模板中同位置、同方块类型的方块实体，保持原始 NBT 类型。"""
from __future__ import annotations

import gzip
import hashlib
import io
import json
from pathlib import Path
import struct

from buildgen import nbtread
import json_to_nbt as nbt


def audit_entries(cells, entities, origin=(0,0,0)):
    """保留每个实际导出实体的状态、原始字节摘要和存档回读预期。"""
    result=[]
    for p,row in sorted(entities.items()):
        if p not in cells or cells[p][0].split('[',1)[0]!=row['block']:
            continue
        result.append({'position':[p[i]+origin[i] for i in range(3)],
                       'state':cells[p][0],'block':row['block'],
                       'raw_nbt_sha256':hashlib.sha256(row['payload']).hexdigest(),
                       'nbt':nbtread._read_payload(io.BytesIO(row['payload']),nbt.TAG_COMPOUND)})
    return result


def matches_saved_entity(expected, actual, path=()):
    """检查全部源字段；仅牌子消息允许原版纯文本组件的等价编码。"""
    if isinstance(expected,dict):
        return isinstance(actual,dict) and all(k in actual and matches_saved_entity(v,actual[k],path+(k,))
                                               for k,v in expected.items())
    if isinstance(expected,list):
        return isinstance(actual,list) and len(expected)==len(actual) and all(
            matches_saved_entity(a,b,path+(i,)) for i,(a,b) in enumerate(zip(expected,actual)))
    if expected==actual:return True
    if (len(path)==3 and path[0] in ('front_text','back_text') and
            path[1] in ('messages','filtered_messages') and isinstance(expected,str) and isinstance(actual,str)):
        def plain_text(value):
            value=json.loads(value)
            if isinstance(value,str):return value
            if isinstance(value,dict) and set(value)=={'text'} and isinstance(value['text'],str):return value['text']
            raise ValueError('不是无样式纯文本组件')
        try:return plain_text(expected)==plain_text(actual)
        except (ValueError,TypeError):return False
    return False


def block_entities(path):
    """返回局部位置到方块类型、原始 Compound payload；不猜测数值的 NBT 类型。"""
    raw = gzip.decompress(Path(path).read_bytes())
    stream = io.BytesIO(raw)
    if stream.read(1) != bytes([nbt.TAG_COMPOUND]):
        raise ValueError("原生模板根标签不是 Compound")
    nbtread._read_utf(stream)
    palette = None
    rows = []
    while True:
        kind = stream.read(1)[0]
        if kind == nbt.TAG_END:
            break
        name = nbtread._read_utf(stream)
        if name != "blocks":
            value = nbtread._read_payload(stream, kind)
            if name == "palette":
                palette = value
            if name == "entities" and value:
                raise ValueError("蓝图导出尚不支持搬移模板实体，不能静默丢弃")
            continue
        if kind != nbt.TAG_LIST:
            raise ValueError("原生模板 blocks 不是 List")
        element_type, count = struct.unpack(">Bi", stream.read(5))
        if element_type != nbt.TAG_COMPOUND:
            raise ValueError("原生模板 block 不是 Compound")
        for _ in range(count):
            block = {}
            payload = None
            while True:
                child = stream.read(1)[0]
                if child == nbt.TAG_END:
                    break
                key = nbtread._read_utf(stream)
                start = stream.tell()
                block[key] = nbtread._read_payload(stream, child)
                if key == "nbt":
                    if child != nbt.TAG_COMPOUND:
                        raise ValueError("方块实体标签不是 Compound")
                    payload = raw[start:stream.tell()]
            if payload is not None:
                rows.append((tuple(block["pos"]), block["state"], payload))
    if palette is None:
        raise ValueError("原生模板缺少 palette")
    return {pos: {"block": palette[state]["Name"], "payload": payload} for pos, state, payload in rows}


def write_tile(path, cells, height, entities, *, size=None):
    """保持原编码与排序；逐分块缓冲后压缩，不为每个字段调用 gzip。"""
    preserved = {p: row for p, row in entities.items()
                 if p in cells and cells[p][0].split("[", 1)[0] == row["block"]}
    size=tuple(size or (16,height,16))
    ordered = sorted(cells.items(), key=lambda row: (row[0][1], row[0][2], row[0][0]))
    palette = sorted({data[0] for _, data in ordered})
    indices = {state: i for i, state in enumerate(palette)}
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with io.BytesIO() as stream:
        stream.write(bytes([nbt.TAG_COMPOUND])); nbt._write_utf(stream, "")
        for name, tag in (
            ("DataVersion", nbt.Int(nbt.DATA_VERSION_BY_MC_VERSION["1.21.1"])),
            ("size", nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in size])),
            ("palette", nbt.ListTag(nbt.TAG_COMPOUND, [nbt.palette_state_to_tag(s) for s in palette]))):
            nbt._write_named_tag(stream, name, tag)
        stream.write(bytes([nbt.TAG_LIST])); nbt._write_utf(stream, "blocks")
        stream.write(struct.pack(">Bi", nbt.TAG_COMPOUND, len(ordered)))
        for pos, data in ordered:
            nbt._write_named_tag(stream, "pos", nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in pos]))
            nbt._write_named_tag(stream, "state", nbt.Int(indices[data[0]]))
            if pos in preserved:
                stream.write(bytes([nbt.TAG_COMPOUND])); nbt._write_utf(stream, "nbt")
                stream.write(preserved[pos]["payload"])
            stream.write(bytes([nbt.TAG_END]))
        nbt._write_named_tag(stream, "entities", nbt.ListTag(nbt.TAG_COMPOUND, []))
        stream.write(bytes([nbt.TAG_END]))
        with Path(path).open("wb") as raw:
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
                compressed.write(stream.getbuffer())
    if preserved and block_entities(path) != preserved:
        raise ValueError("导出方块实体没有逐字节保留原始 NBT")
    return len(ordered), len(palette), len(preserved)
