"""保留认证源模板中同位置、同方块类型的方块实体，保持原始 NBT 类型。"""
from __future__ import annotations

import gzip
import io
from pathlib import Path
import struct

from buildgen import nbtread
import json_to_nbt as nbt


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


def write_tile(path, cells, height, entities):
    """普通方块沿用相同排序；嵌入的方块实体字节原样输出。"""
    from province_capitals.regional.v2.export_game import write_tile as plain_tile
    preserved = {p: row for p, row in entities.items()
                 if p in cells and cells[p][0].split("[", 1)[0] == row["block"]}
    if not preserved:
        return (*plain_tile(path, cells, height), 0)
    ordered = sorted(cells.items(), key=lambda row: (row[0][1], row[0][2], row[0][0]))
    palette = sorted({data[0] for _, data in ordered})
    indices = {state: i for i, state in enumerate(palette)}
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with Path(path).open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as stream:
            stream.write(bytes([nbt.TAG_COMPOUND])); nbt._write_utf(stream, "")
            for name, tag in (
                ("DataVersion", nbt.Int(nbt.DATA_VERSION_BY_MC_VERSION["1.21.1"])),
                ("size", nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in (16, height, 16)])),
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
    actual = block_entities(path)
    if actual != preserved:
        raise ValueError("导出方块实体没有逐字节保留原始 NBT")
    return len(ordered), len(palette), len(preserved)
