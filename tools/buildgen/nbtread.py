"""Minimal gzipped Java NBT reader for validating exported structure files."""

from __future__ import annotations

import gzip
import struct
from typing import Any, BinaryIO, Tuple

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, \
    TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, \
    TAG_LONG_ARRAY = range(13)

_FMT = {TAG_BYTE: ">b", TAG_SHORT: ">h", TAG_INT: ">i", TAG_LONG: ">q",
        TAG_FLOAT: ">f", TAG_DOUBLE: ">d"}


def _read_utf(f: BinaryIO) -> str:
    (n,) = struct.unpack(">H", f.read(2))
    return f.read(n).decode("utf-8")


def _read_payload(f: BinaryIO, tag_id: int) -> Any:
    if tag_id in _FMT:
        fmt = _FMT[tag_id]
        (v,) = struct.unpack(fmt, f.read(struct.calcsize(fmt)))
        return v
    if tag_id == TAG_STRING:
        return _read_utf(f)
    if tag_id == TAG_BYTE_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return list(f.read(n))
    if tag_id in (TAG_INT_ARRAY, TAG_LONG_ARRAY):
        (n,) = struct.unpack(">i", f.read(4))
        fmt = ">i" if tag_id == TAG_INT_ARRAY else ">q"
        return [struct.unpack(fmt, f.read(struct.calcsize(fmt)))[0]
                for _ in range(n)]
    if tag_id == TAG_LIST:
        (etype,) = struct.unpack(">B", f.read(1))
        (n,) = struct.unpack(">i", f.read(4))
        return [_read_payload(f, etype) for _ in range(n)]
    if tag_id == TAG_COMPOUND:
        out = {}
        while True:
            (child,) = struct.unpack(">B", f.read(1))
            if child == TAG_END:
                return out
            name = _read_utf(f)
            out[name] = _read_payload(f, child)
    raise ValueError(f"unsupported tag id {tag_id}")


def read_gzipped_nbt(path: str) -> Tuple[str, dict]:
    """Returns (root_name, root_compound_dict)."""
    with gzip.open(path, "rb") as f:
        (tag_id,) = struct.unpack(">B", f.read(1))
        if tag_id != TAG_COMPOUND:
            raise ValueError(f"root tag is {tag_id}, expected compound")
        name = _read_utf(f)
        return name, _read_payload(f, TAG_COMPOUND)


def state_string(palette_entry: dict) -> str:
    """Palette compound -> 'minecraft:block[propk=v,...]' string."""
    name = palette_entry["Name"]
    props = palette_entry.get("Properties") or {}
    if not props:
        return name
    inner = ",".join(f"{k}={v}" for k, v in sorted(props.items()))
    return f"{name}[{inner}]"
