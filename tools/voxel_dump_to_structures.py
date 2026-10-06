#!/usr/bin/env python3
"""Turn a world voxel dump into reusable structure files.

Input: a JSON array of ``[x, y, z, "namespace:block[prop=val,...]"]`` in world
coordinates (non-air blocks only), e.g. a DevBridge dump of a built compound.

Output, for an output stem ``S``:

- ``S.blueprint.json``: blueprint v1 (see docs/ai-kb/02_blueprint_schema.md),
  palette of local aliases -> block ids, per-block ``state`` objects.
- ``S.nbt``: vanilla structure template (gzip NBT), written with the same
  writer as the generated templates (tools/json_to_nbt.py).
- ``S.schem``: Sponge schematic v2 (gzip NBT), for WorldEdit ``//schem load``.

Filtering: blocks below ``--min-y`` are dropped, and so are natural ground
blocks (``minecraft:grass_block`` and ``minecraft:dirt``, any state) unless
``--include-natural`` is given. The bounding box of what is kept becomes the
structure size, with its min corner as (0, 0, 0).

Block states are kept verbatim. Block entities are not part of the dump, so
signs, plaques, chests etc. come out without their NBT (no text, no items).

Standard library only.
"""

from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import struct
import sys
from collections import Counter, OrderedDict
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import json_to_nbt  # noqa: E402
from json_to_nbt import (  # noqa: E402
    TAG_BYTE_ARRAY,
    TAG_COMPOUND,
    TAG_INT,
    TAG_INT_ARRAY,
    TAG_LIST,
    TAG_SHORT,
    Compound,
    Int,
    ListTag,
    String,
    Tag,
    parse_block_state,
)

NATURAL_BLOCKS = frozenset({"minecraft:grass_block", "minecraft:dirt"})
DEFAULT_DATA_VERSION = json_to_nbt.DATA_VERSION_BY_MC_VERSION[json_to_nbt.SUPPORTED_MC_VERSION]
AIR = "minecraft:air"

Pos = Tuple[int, int, int]


# --------------------------------------------------------------------------
# Loading and filtering
# --------------------------------------------------------------------------

def block_id(state: str) -> str:
    return state.split("[", 1)[0]


def load_dump(path: Path) -> List[Tuple[int, int, int, str]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("voxel dump must be a JSON array")
    out = []
    for i, item in enumerate(raw):
        if not (isinstance(item, list) and len(item) == 4 and isinstance(item[3], str)):
            raise ValueError(f"dump[{i}] must be [x, y, z, state], got {item!r}")
        out.append((int(item[0]), int(item[1]), int(item[2]), item[3]))
    return out


def filter_voxels(
    voxels: Iterable[Tuple[int, int, int, str]],
    min_y: int | None,
    include_natural: bool = False,
) -> Dict[Pos, str]:
    kept: Dict[Pos, str] = {}
    for x, y, z, state in voxels:
        if min_y is not None and y < min_y:
            continue
        if block_id(state) == AIR:
            continue
        if not include_natural and block_id(state) in NATURAL_BLOCKS:
            continue
        if (x, y, z) in kept and kept[(x, y, z)] != state:
            raise ValueError(f"conflicting states at {(x, y, z)}: {kept[(x, y, z)]!r} vs {state!r}")
        kept[(x, y, z)] = state
    if not kept:
        raise ValueError("no blocks left after filtering")
    return kept


class Structure:
    """Blocks normalised to a min-corner origin."""

    def __init__(self, world_blocks: Dict[Pos, str]) -> None:
        xs = [p[0] for p in world_blocks]
        ys = [p[1] for p in world_blocks]
        zs = [p[2] for p in world_blocks]
        self.world_min: Pos = (min(xs), min(ys), min(zs))
        self.world_max: Pos = (max(xs), max(ys), max(zs))
        self.size: Pos = tuple(hi - lo + 1 for lo, hi in zip(self.world_min, self.world_max))  # type: ignore[assignment]
        mx, my, mz = self.world_min
        local = {(x - mx, y - my, z - mz): s for (x, y, z), s in world_blocks.items()}
        # y, z, x order, as json_to_nbt sorts its blocks.
        self.blocks: "OrderedDict[Pos, str]" = OrderedDict(
            sorted(local.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))
        )
        self.states: List[str] = []
        seen = set()
        for state in self.blocks.values():
            if state not in seen:
                seen.add(state)
                self.states.append(state)


# --------------------------------------------------------------------------
# Blueprint v1
# --------------------------------------------------------------------------

_ALIAS_BAD = re.compile(r"[^a-z0-9_]+")


def _alias_for(block: str, taken: set) -> str:
    ns, _, path = block.partition(":")
    base = path if ns == "minecraft" else f"{ns}_{path}"
    base = _ALIAS_BAD.sub("_", base.lower()).strip("_") or "block"
    alias, n = base, 2
    while alias in taken:
        alias = f"{base}_{n}"
        n += 1
    return alias


def build_blueprint(structure: Structure, blueprint_id: str, meta: dict | None = None) -> dict:
    palette: "OrderedDict[str, str]" = OrderedDict()
    alias_of: Dict[str, str] = {}
    for state in structure.states:
        name, _props = parse_block_state(state)
        if name not in alias_of:
            alias = _alias_for(name, set(palette))
            alias_of[name] = alias
            palette[alias] = name
    blocks = []
    for pos, state in structure.blocks.items():
        name, props = parse_block_state(state)
        entry: dict = {"pos": list(pos), "palette": alias_of[name]}
        if props:
            entry["state"] = dict(props)
        blocks.append(entry)
    data: dict = OrderedDict()
    data["schema_version"] = 1
    data["id"] = blueprint_id
    data["size"] = list(structure.size)
    data["origin"] = [0, 0, 0]
    if meta:
        data["meta"] = meta
    data["palette"] = palette
    data["blocks"] = blocks
    return data


def write_blueprint(data: dict, path: Path) -> None:
    head = {k: v for k, v in data.items() if k != "blocks"}
    with open(path, "w", encoding="utf-8") as f:
        text = json.dumps(head, indent=2, ensure_ascii=False)
        f.write(text[:-2] if text.endswith("\n}") else text[:-1])
        f.write(',\n  "blocks": [\n')
        blocks = data["blocks"]
        for i, entry in enumerate(blocks):
            f.write("    " + json.dumps(entry, separators=(",", ":"), ensure_ascii=False))
            f.write(",\n" if i + 1 < len(blocks) else "\n")
        f.write("  ]\n}\n")


# --------------------------------------------------------------------------
# Vanilla structure template (.nbt)
# --------------------------------------------------------------------------

def build_structure_nbt(structure: Structure, data_version: int, author: str) -> Tag:
    data = {
        "size": list(structure.size),
        "author": author,
        "blocks": [{"pos": list(pos), "state": state} for pos, state in structure.blocks.items()],
        "entities": [],
    }
    return json_to_nbt.structure_json_to_root_nbt(data, data_version_override=data_version)


# --------------------------------------------------------------------------
# Sponge schematic v2 (.schem)
# --------------------------------------------------------------------------

def Short(value: int) -> Tag:
    if not -32768 <= value <= 32767:
        raise ValueError(f"short out of range: {value}")
    return Tag(TAG_SHORT, int(value))


def ByteArray(data: bytes) -> Tag:
    return Tag(TAG_BYTE_ARRAY, bytes(data))


def IntArray(values: Sequence[int]) -> Tag:
    return Tag(TAG_INT_ARRAY, [int(v) for v in values])


def _write_payload_ext(out, tag: Tag) -> None:
    """json_to_nbt's writer plus the short / byte-array / int-array tags."""
    if tag.tag_id == TAG_SHORT:
        out.write(struct.pack(">h", tag.value))
    elif tag.tag_id == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(tag.value)))
        out.write(tag.value)
    elif tag.tag_id == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(tag.value)))
        out.write(struct.pack(f">{len(tag.value)}i", *tag.value))
    elif tag.tag_id == TAG_COMPOUND:
        for name, child in tag.value.items():
            out.write(struct.pack(">B", child.tag_id))
            json_to_nbt._write_utf(out, name)
            _write_payload_ext(out, child)
        out.write(struct.pack(">B", json_to_nbt.TAG_END))
    elif tag.tag_id == TAG_LIST:
        element_type = tag.element_type if tag.element_type is not None else json_to_nbt.TAG_END
        out.write(struct.pack(">B", element_type))
        out.write(struct.pack(">i", len(tag.value)))
        for item in tag.value:
            _write_payload_ext(out, item)
    else:
        json_to_nbt._write_payload(out, tag)


def write_gzipped_nbt_ext(root: Tag, path: Path, root_name: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
            gz.write(struct.pack(">B", root.tag_id))
            json_to_nbt._write_utf(gz, root_name)
            _write_payload_ext(gz, root)


def encode_varint(value: int, out: bytearray) -> None:
    if value < 0:
        raise ValueError("varint must be non-negative")
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return


def decode_varints(data: Sequence[int]) -> List[int]:
    out, value, shift = [], 0, 0
    for byte in data:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
        else:
            out.append(value)
            value, shift = 0, 0
    if shift:
        raise ValueError("truncated varint")
    return out


def schem_state_key(state: str) -> str:
    """Canonical ``ns:block[k=v,...]`` palette key (property order kept)."""
    name, props = parse_block_state(state)
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


def schem_index(x: int, y: int, z: int, width: int, length: int) -> int:
    return (y * length + z) * width + x


def build_schem(structure: Structure, data_version: int) -> Tuple[Tag, List[str]]:
    width, height, length = structure.size
    palette: "OrderedDict[str, int]" = OrderedDict([(AIR, 0)])
    normalised: List[str] = []
    cell_keys: Dict[Pos, int] = {}
    for pos, state in structure.blocks.items():
        key = schem_state_key(state)
        if key != state:
            normalised.append(f"{state} -> {key}")
        if key not in palette:
            palette[key] = len(palette)
        cell_keys[pos] = palette[key]
    data = bytearray()
    for y in range(height):
        for z in range(length):
            for x in range(width):
                encode_varint(cell_keys.get((x, y, z), 0), data)
    root = Compound(OrderedDict([
        ("Version", Int(2)),
        ("DataVersion", Int(data_version)),
        ("Width", Short(width)),
        ("Height", Short(height)),
        ("Length", Short(length)),
        ("Offset", IntArray([0, 0, 0])),
        ("PaletteMax", Int(len(palette))),
        ("Palette", Compound(OrderedDict((k, Int(v)) for k, v in palette.items()))),
        ("BlockData", ByteArray(bytes(data))),
        ("BlockEntities", ListTag(TAG_COMPOUND, [])),
    ]))
    return root, sorted(set(normalised))


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def convert(
    dump: Path,
    stem: Path,
    min_y: int | None,
    include_natural: bool = False,
    blueprint_id: str | None = None,
    data_version: int = DEFAULT_DATA_VERSION,
) -> dict:
    voxels = load_dump(dump)
    structure = Structure(filter_voxels(voxels, min_y, include_natural))
    stem = Path(stem)
    stem.parent.mkdir(parents=True, exist_ok=True)
    if blueprint_id is None:
        path = re.sub(r"[^a-z0-9_./-]+", "_", stem.name.lower())
        blueprint_id = f"myvillage:{path}"
    meta = {
        "source": str(dump),
        "world_min": list(structure.world_min),
        "min_y": min_y,
        "include_natural": include_natural,
        "block_entities": "not captured; signs/plaques have no text, containers are empty",
    }
    paths = {
        "blueprint": stem.with_name(stem.name + ".blueprint.json"),
        "nbt": stem.with_name(stem.name + ".nbt"),
        "schem": stem.with_name(stem.name + ".schem"),
    }
    write_blueprint(build_blueprint(structure, blueprint_id, meta), paths["blueprint"])
    json_to_nbt.write_gzipped_nbt(build_structure_nbt(structure, data_version, "voxel_dump_to_structures.py"), str(paths["nbt"]))
    schem_root, normalised = build_schem(structure, data_version)
    write_gzipped_nbt_ext(schem_root, paths["schem"], "Schematic")
    return {
        "input_voxels": len(voxels),
        "blocks": len(structure.blocks),
        "size": list(structure.size),
        "world_min": list(structure.world_min),
        "world_max": list(structure.world_max),
        "states": len(structure.states),
        "block_ids": len({block_id(s) for s in structure.states}),
        "namespaces": dict(Counter(block_id(s).split(":")[0] for s in structure.states)),
        "schem_normalised": normalised,
        "paths": {k: str(v) for k, v in paths.items()},
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("dump", type=Path, help="voxel dump JSON: [[x, y, z, state], ...]")
    parser.add_argument("stem", type=Path, help="output stem; writes <stem>.blueprint.json, .nbt, .schem")
    parser.add_argument("--min-y", type=int, default=None, help="drop blocks with world y below this")
    parser.add_argument("--include-natural", action="store_true",
                        help="keep minecraft:grass_block and minecraft:dirt (dropped by default)")
    parser.add_argument("--id", dest="blueprint_id", default=None,
                        help="blueprint id (default myvillage:<stem name>)")
    parser.add_argument("--data-version", type=int, default=DEFAULT_DATA_VERSION,
                        help=f"DataVersion for .nbt and .schem (default {DEFAULT_DATA_VERSION}, MC "
                             f"{json_to_nbt.SUPPORTED_MC_VERSION})")
    args = parser.parse_args(argv)
    report = convert(args.dump, args.stem, args.min_y, args.include_natural, args.blueprint_id, args.data_version)
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
