#!/usr/bin/env python3
"""
Convert a small custom Minecraft structure JSON DSL into a gzipped Java NBT
structure file that can be loaded by a Structure Block or /place template.

Supported DSL forms:
  - legacy top-level "blocks": [{"pos": [x,y,z], "state": "alias_or_blockstate"}]
  - top-level "ops" or "operations":
      {"op": "set", "pos": [x,y,z], "state": "alias_or_blockstate"}
      {"op": "fill", "from": [x1,y1,z1], "to": [x2,y2,z2], "state": "alias_or_blockstate"}
      {"op": "line", "from": [x1,y1,z1], "to": [x2,y2,z2], "state": "alias_or_blockstate"}

No third-party Python packages are required.
"""

from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import struct
from collections import OrderedDict
from dataclasses import dataclass
from typing import Any, Dict, Iterable, Iterator, List, Mapping, Tuple

# Java NBT tag ids
TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

# This project targets Minecraft 1.21.1 only (stage 4+). 1.20.1 export was
# removed; use --data-version to override the DataVersion integer if needed.
SUPPORTED_MC_VERSION = "1.21.1"
DATA_VERSION_BY_MC_VERSION = {
    "1.21.1": 3955,
}


@dataclass(frozen=True)
class Tag:
    tag_id: int
    value: Any
    element_type: int | None = None


def Int(value: int) -> Tag:
    return Tag(TAG_INT, int(value))


def Byte(value: int) -> Tag:
    return Tag(TAG_BYTE, int(value))


def Double(value: float) -> Tag:
    return Tag(TAG_DOUBLE, float(value))


def String(value: str) -> Tag:
    return Tag(TAG_STRING, str(value))


def ListTag(element_type: int, values: Iterable[Tag]) -> Tag:
    values = list(values)
    for i, item in enumerate(values):
        if item.tag_id != element_type:
            raise TypeError(f"List item {i} has tag {item.tag_id}, expected {element_type}")
    return Tag(TAG_LIST, values, element_type)


def Compound(values: Mapping[str, Tag] | None = None) -> Tag:
    return Tag(TAG_COMPOUND, OrderedDict(values or {}))


def _write_utf(out, text: str) -> None:
    raw = text.encode("utf-8")
    if len(raw) > 65535:
        raise ValueError(f"NBT string is too long: {len(raw)} bytes")
    out.write(struct.pack(">H", len(raw)))
    out.write(raw)


def _write_named_tag(out, name: str, tag: Tag) -> None:
    out.write(struct.pack(">B", tag.tag_id))
    if tag.tag_id == TAG_END:
        return
    _write_utf(out, name)
    _write_payload(out, tag)


def _write_payload(out, tag: Tag) -> None:
    if tag.tag_id == TAG_INT:
        out.write(struct.pack(">i", tag.value))
    elif tag.tag_id == TAG_BYTE:
        out.write(struct.pack(">b", tag.value))
    elif tag.tag_id == TAG_DOUBLE:
        out.write(struct.pack(">d", tag.value))
    elif tag.tag_id == TAG_STRING:
        _write_utf(out, tag.value)
    elif tag.tag_id == TAG_LIST:
        element_type = tag.element_type if tag.element_type is not None else TAG_END
        out.write(struct.pack(">B", element_type))
        out.write(struct.pack(">i", len(tag.value)))
        for item in tag.value:
            _write_payload(out, item)
    elif tag.tag_id == TAG_COMPOUND:
        for child_name, child_tag in tag.value.items():
            _write_named_tag(out, child_name, child_tag)
        out.write(struct.pack(">B", TAG_END))
    else:
        raise NotImplementedError(f"Writer only implements tags used by structure NBT; got tag id {tag.tag_id}")


def write_gzipped_nbt(root: Tag, path: str, root_name: str = "") -> None:
    if root.tag_id != TAG_COMPOUND:
        raise TypeError("Root NBT tag must be a compound")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
            _write_named_tag(gz, root_name, root)


_BLOCKSTATE_RE = re.compile(
    r"^(?P<name>[a-z0-9_\-.]+:[a-z0-9_./\-]+|[a-z0-9_./\-]+)(?:\[(?P<props>.*)\])?$"
)
_PROP_KEY_RE = re.compile(r"^[a-z0-9_\-.]+$")
_PROP_VALUE_RE = re.compile(r"^[a-z0-9_\-.]+$")


def parse_block_state(text: str) -> Tuple[str, OrderedDict[str, str]]:
    if not isinstance(text, str):
        raise ValueError(f"Block state must be a string, got: {text!r}")
    text = text.strip()
    if not text:
        raise ValueError("Block state cannot be empty")

    match = _BLOCKSTATE_RE.match(text)
    if not match:
        raise ValueError(f"Invalid block state syntax: {text!r}")

    name = match.group("name")
    if ":" not in name:
        name = f"minecraft:{name}"

    props_text = match.group("props")
    props: OrderedDict[str, str] = OrderedDict()
    if props_text is not None:
        if not props_text:
            raise ValueError(f"Empty block state property list: {text!r}")
        for pair in props_text.split(","):
            if "=" not in pair:
                raise ValueError(f"Invalid property pair in {text!r}: {pair!r}")
            key, value = pair.split("=", 1)
            key = key.strip()
            value = value.strip()
            if not key or not value:
                raise ValueError(f"Invalid property pair in {text!r}: {pair!r}")
            if not _PROP_KEY_RE.match(key):
                raise ValueError(f"Invalid property key in {text!r}: {key!r}")
            if not _PROP_VALUE_RE.match(value):
                raise ValueError(f"Invalid property value in {text!r}: {value!r}")
            if key in props:
                raise ValueError(f"Duplicate property key in {text!r}: {key!r}")
            props[key] = value

    return name, props


def palette_state_to_tag(state_text: str) -> Tag:
    name, props = parse_block_state(state_text)
    state = OrderedDict()
    state["Name"] = String(name)
    if props:
        state["Properties"] = Compound(OrderedDict((k, String(v)) for k, v in props.items()))
    return Compound(state)


def _nbt_scalar_to_tag(key: str, value: Any) -> Tag:
    if isinstance(value, bool):
        return Byte(1 if value else 0)
    if isinstance(value, int):
        return Byte(value) if key == "facing" else Int(value)
    if isinstance(value, float):
        return Double(value)
    return String(str(value))


def _nbt_value_to_tag(key: str, value: Any) -> Tag:
    if isinstance(value, dict):
        return Compound(OrderedDict((str(k), _nbt_value_to_tag(str(k), v)) for k, v in value.items()))
    if isinstance(value, list):
        if not value:
            return ListTag(TAG_END, [])
        if all(isinstance(v, int) and not isinstance(v, bool) for v in value):
            return ListTag(TAG_INT, [Int(v) for v in value])
        if all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in value):
            return ListTag(TAG_DOUBLE, [Double(float(v)) for v in value])
        return ListTag(TAG_STRING, [String(str(v)) for v in value])
    return _nbt_scalar_to_tag(key, value)


def entity_to_tag(entity: Mapping[str, Any]) -> Tag:
    pos = entity.get("pos")
    block_pos = entity.get("blockPos")
    nbt = entity.get("nbt")
    if not isinstance(pos, list) or len(pos) != 3:
        raise ValueError(f"entity pos must be [x,y,z], got {pos!r}")
    if not isinstance(block_pos, list) or len(block_pos) != 3:
        raise ValueError(f"entity blockPos must be [x,y,z], got {block_pos!r}")
    if not isinstance(nbt, dict):
        raise ValueError(f"entity nbt must be an object, got {nbt!r}")
    return Compound(OrderedDict([
        ("pos", ListTag(TAG_DOUBLE, [Double(float(pos[0])), Double(float(pos[1])), Double(float(pos[2]))])),
        ("blockPos", ListTag(TAG_INT, [Int(int(block_pos[0])), Int(int(block_pos[1])), Int(int(block_pos[2]))])),
        ("nbt", Compound(OrderedDict((str(k), _nbt_value_to_tag(str(k), v)) for k, v in nbt.items()))),
    ]))


def resolve_state(state: str, palette_aliases: Mapping[str, str]) -> str:
    if state in palette_aliases:
        return palette_aliases[state]
    if ":" not in state and "[" not in state:
        # Backward-compatible convenience: bare block ids become minecraft namespace ids.
        return f"minecraft:{state}"
    return state


def validate_size(raw_size: Any) -> List[int]:
    if not isinstance(raw_size, list) or len(raw_size) != 3:
        raise ValueError("JSON must contain size: [x, y, z]")
    try:
        size = [int(raw_size[0]), int(raw_size[1]), int(raw_size[2])]
    except (TypeError, ValueError) as exc:
        raise ValueError(f"size values must be integers: {raw_size!r}") from exc
    if any(v <= 0 for v in size):
        raise ValueError(f"Invalid size; values must be positive: {size!r}")
    return size


def validate_pos(pos: Any, size: List[int], context: str = "pos") -> Tuple[int, int, int]:
    if not isinstance(pos, list) or len(pos) != 3:
        raise ValueError(f"{context} must be a 3-item list, got: {pos!r}")
    try:
        x, y, z = (int(pos[0]), int(pos[1]), int(pos[2]))
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{context} values must be integers, got: {pos!r}") from exc
    sx, sy, sz = size
    if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
        raise ValueError(f"{context} {pos!r} outside structure size {size!r}")
    return x, y, z


def _normalized_box(a: Tuple[int, int, int], b: Tuple[int, int, int]) -> Tuple[Tuple[int, int, int], Tuple[int, int, int]]:
    return (
        (min(a[0], b[0]), min(a[1], b[1]), min(a[2], b[2])),
        (max(a[0], b[0]), max(a[1], b[1]), max(a[2], b[2])),
    )


def iter_box(a: Tuple[int, int, int], b: Tuple[int, int, int]) -> Iterator[Tuple[int, int, int]]:
    low, high = _normalized_box(a, b)
    for y in range(low[1], high[1] + 1):
        for z in range(low[2], high[2] + 1):
            for x in range(low[0], high[0] + 1):
                yield (x, y, z)


def iter_line(a: Tuple[int, int, int], b: Tuple[int, int, int]) -> Iterator[Tuple[int, int, int]]:
    varying_axes = [axis for axis in range(3) if a[axis] != b[axis]]
    if len(varying_axes) > 1:
        raise ValueError(f"line endpoints must be axis-aligned, got {list(a)!r} to {list(b)!r}")
    if not varying_axes:
        yield a
        return

    axis = varying_axes[0]
    step = 1 if b[axis] > a[axis] else -1
    pos = list(a)
    for value in range(a[axis], b[axis] + step, step):
        pos[axis] = value
        yield tuple(pos)  # type: ignore[misc]


NormalizedTarget = Tuple[int, int, int] | Tuple[Tuple[int, int, int], Tuple[int, int, int]]


def iter_json_ops(data: Mapping[str, Any], size: List[int]) -> Iterator[Tuple[str, NormalizedTarget, str]]:
    """Yield normalized operations as ('set', pos, state), ('fill', (from,to), state), or ('line', (from,to), state)."""
    blocks = data.get("blocks", [])
    if blocks is None:
        blocks = []
    if not isinstance(blocks, list):
        raise ValueError("blocks must be a list")
    for i, entry in enumerate(blocks):
        if not isinstance(entry, dict):
            raise ValueError(f"blocks[{i}] must be an object, got: {entry!r}")
        pos = validate_pos(entry.get("pos"), size, f"blocks[{i}].pos")
        if "state" not in entry:
            raise ValueError(f"blocks[{i}] is missing state")
        yield ("set", pos, str(entry.get("state")))

    ops = data.get("ops", data.get("operations", []))
    if ops is None:
        ops = []
    if not isinstance(ops, list):
        raise ValueError("ops/operations must be a list")
    for i, op in enumerate(ops):
        if not isinstance(op, dict):
            raise ValueError(f"ops[{i}] must be an object, got: {op!r}")
        kind = str(op.get("op", "")).strip().lower()
        if kind == "set":
            pos = validate_pos(op.get("pos"), size, f"ops[{i}].pos")
            if "state" not in op:
                raise ValueError(f"ops[{i}] set operation is missing state")
            yield ("set", pos, str(op.get("state")))
        elif kind == "fill":
            from_pos = validate_pos(op.get("from"), size, f"ops[{i}].from")
            to_pos = validate_pos(op.get("to"), size, f"ops[{i}].to")
            if "state" not in op:
                raise ValueError(f"ops[{i}] fill operation is missing state")
            low, high = _normalized_box(from_pos, to_pos)
            yield ("fill", (low, high), str(op.get("state")))
        elif kind == "line":
            from_pos = validate_pos(op.get("from"), size, f"ops[{i}].from")
            to_pos = validate_pos(op.get("to"), size, f"ops[{i}].to")
            if "state" not in op:
                raise ValueError(f"ops[{i}] line operation is missing state")
            list(iter_line(from_pos, to_pos))
            yield ("line", (from_pos, to_pos), str(op.get("state")))
        else:
            raise ValueError(f"ops[{i}] has unsupported op {op.get('op')!r}; expected 'set', 'fill', or 'line'")


def expand_structure(data: Mapping[str, Any]) -> Tuple[List[int], int, Dict[Tuple[int, int, int], str], OrderedDict[str, int]]:
    """Expand JSON DSL to a coordinate -> resolved blockstate map.

    Returns (size, explicit_overwrite_count, blocks, unique_states_index).
    fill_air initializes missing coordinates to minecraft:air and is not counted
    as explicit overwrite noise.
    """
    size = validate_size(data.get("size"))

    palette_aliases = data.get("palette", {})
    if not isinstance(palette_aliases, dict):
        raise ValueError("palette must be an object mapping alias -> blockstate")
    palette_aliases = {str(k): str(v) for k, v in palette_aliases.items()}

    for alias, state_text in palette_aliases.items():
        try:
            parse_block_state(state_text)
        except ValueError as exc:
            raise ValueError(f"palette[{alias!r}] has invalid block state {state_text!r}: {exc}") from exc

    explicit: Dict[Tuple[int, int, int], str] = {}
    overwritten = 0

    for kind, target, state_key in iter_json_ops(data, size):
        try:
            state_text = resolve_state(state_key, palette_aliases)
            parse_block_state(state_text)
        except ValueError as exc:
            raise ValueError(f"Invalid state reference {state_key!r}: {exc}") from exc

        if kind == "set":
            pos = target  # type: ignore[assignment]
            if pos in explicit:
                overwritten += 1
            explicit[pos] = state_text  # type: ignore[index]
        elif kind == "fill":
            low, high = target  # type: ignore[misc]
            for pos in iter_box(low, high):
                if pos in explicit:
                    overwritten += 1
                explicit[pos] = state_text
        elif kind == "line":
            from_pos, to_pos = target  # type: ignore[misc]
            for pos in iter_line(from_pos, to_pos):
                if pos in explicit:
                    overwritten += 1
                explicit[pos] = state_text
        else:
            raise AssertionError(kind)

    fill_air = bool(data.get("fill_air", False))
    if fill_air:
        for y in range(size[1]):
            for z in range(size[2]):
                for x in range(size[0]):
                    explicit.setdefault((x, y, z), "minecraft:air")

    # Stable block order: y, z, x, matching common structure-file readability.
    ordered_items = sorted(explicit.items(), key=lambda item: (item[0][1], item[0][2], item[0][0]))

    unique_states: OrderedDict[str, int] = OrderedDict()
    for _, state_text in ordered_items:
        parse_block_state(state_text)
        if state_text not in unique_states:
            unique_states[state_text] = len(unique_states)

    return size, overwritten, dict(ordered_items), unique_states


def structure_json_to_root_nbt(
    data: Mapping[str, Any],
    mc_version_override: str | None = None,
    data_version_override: int | None = None,
) -> Tag:
    size, _overwritten, blocks, unique_states = expand_structure(data)

    mc_version = mc_version_override or str(data.get("mc_version", SUPPORTED_MC_VERSION))
    if mc_version != SUPPORTED_MC_VERSION:
        raise ValueError(
            f"unsupported mc_version {mc_version!r}; this project only exports Minecraft {SUPPORTED_MC_VERSION} NBT"
        )
    if data_version_override is not None:
        data_version = int(data_version_override)
    else:
        data_version = DATA_VERSION_BY_MC_VERSION[SUPPORTED_MC_VERSION]

    palette_tags = [palette_state_to_tag(state_text) for state_text in unique_states.keys()]

    block_tags: List[Tag] = []
    for (x, y, z), state_text in blocks.items():
        block_tags.append(Compound(OrderedDict([
            ("pos", ListTag(TAG_INT, [Int(x), Int(y), Int(z)])),
            ("state", Int(unique_states[state_text])),
        ])))

    entities_raw = data.get("entities", [])
    if entities_raw is None:
        entities_raw = []
    if not isinstance(entities_raw, list):
        raise ValueError("entities must be a list")
    entity_tags = [entity_to_tag(entity) for entity in entities_raw]

    root = Compound(OrderedDict([
        ("DataVersion", Int(data_version)),
        ("author", String(str(data.get("author", "json_to_nbt.py")))),
        ("size", ListTag(TAG_INT, [Int(size[0]), Int(size[1]), Int(size[2])])),
        ("palette", ListTag(TAG_COMPOUND, palette_tags)),
        ("blocks", ListTag(TAG_COMPOUND, block_tags)),
        ("entities", ListTag(TAG_COMPOUND, entity_tags)),
    ]))
    return root


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert custom structure JSON to gzipped Minecraft structure NBT")
    parser.add_argument("input_json", help="Input structure JSON")
    parser.add_argument("output_nbt", help="Output .nbt path")
    parser.add_argument(
        "--mc-version",
        default=SUPPORTED_MC_VERSION,
        help=f"Target Minecraft version (only {SUPPORTED_MC_VERSION} is supported)",
    )
    parser.add_argument("--data-version", type=int, default=None, help="Override DataVersion integer")
    args = parser.parse_args()

    if args.mc_version != SUPPORTED_MC_VERSION:
        parser.error(f"unsupported --mc-version {args.mc_version!r}; only {SUPPORTED_MC_VERSION} is supported")

    with open(args.input_json, "r", encoding="utf-8") as f:
        data = json.load(f)

    root = structure_json_to_root_nbt(data, args.mc_version, args.data_version)
    write_gzipped_nbt(root, args.output_nbt)

    palette_count = len(root.value["palette"].value)
    block_count = len(root.value["blocks"].value)
    size = [tag.value for tag in root.value["size"].value]
    data_version = root.value["DataVersion"].value
    print(f"output path: {args.output_nbt}")
    print(f"DataVersion: {data_version}")
    print(f"size: {size}")
    print(f"palette count: {palette_count}")
    print(f"block count: {block_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
