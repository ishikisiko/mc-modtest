#!/usr/bin/env python3
"""Validate the custom Minecraft structure JSON DSL before NBT conversion.

Checks blockstate string syntax, palette resolution, operation bounds,
coordinate overwrites, the optional structure metadata block, and block ids
against the Minecraft 1.21.1 registry (docs/ai-kb/references/blocks_121.json).
Blockstate property names/values are checked for syntax only, not against the
per-block property registry.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import OrderedDict
from typing import Any, Dict, Iterable, List, Mapping, Tuple

# Allow running as: python tools/validate_structure_json.py examples/test.json
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from json_to_nbt import iter_box, iter_line, parse_block_state, validate_pos, validate_size  # noqa: E402


class ValidationError(Exception):
    pass


SUPPORTED_MC_VERSION = "1.21.1"
VALID_CATEGORIES = ("building", "prop", "road")
VALID_FACINGS = ("north", "south", "east", "west")
METADATA_ID_RE = re.compile(r"^[a-z0-9_\-.]+:[a-z0-9_/\-.]+$")
BLOCKS_121_PATH = os.path.normpath(
    os.path.join(SCRIPT_DIR, "..", "docs", "ai-kb", "references", "blocks_121.json")
)

_BLOCK_REGISTRY_CACHE: set[str] | None = None


def load_block_registry() -> set[str]:
    """Load the Minecraft 1.21.1 block id registry (bare ids, no namespace)."""
    global _BLOCK_REGISTRY_CACHE
    if _BLOCK_REGISTRY_CACHE is None:
        with open(BLOCKS_121_PATH, "r", encoding="utf-8") as f:
            _BLOCK_REGISTRY_CACHE = set(json.load(f))
    return _BLOCK_REGISTRY_CACHE


def validate_block_id(state_text: str, context: str) -> None:
    name, _props = parse_block_state(state_text)
    namespace, _, bare = name.partition(":")
    if namespace != "minecraft":
        raise ValidationError(
            f"{context}: block {name!r} is not in the minecraft namespace; only vanilla 1.21.1 blocks can be validated"
        )
    if bare not in load_block_registry():
        raise ValidationError(f"{context}: block {name!r} does not exist in Minecraft {SUPPORTED_MC_VERSION}")


def _validate_anchor(anchor: Any, size: List[int], context: str) -> None:
    if not isinstance(anchor, dict):
        raise ValidationError(f"{context}: expected object, got {anchor!r}")
    pos = anchor.get("pos")
    try:
        validate_pos(pos, size, f"{context}.pos")
    except ValueError as exc:
        raise ValidationError(str(exc)) from exc
    facing = anchor.get("facing")
    if facing not in VALID_FACINGS:
        raise ValidationError(f"{context}.facing must be one of {list(VALID_FACINGS)}, got {facing!r}")


def validate_metadata(data: Mapping[str, Any]) -> Dict[str, Any] | None:
    """Validate the optional metadata block. Returns the metadata dict or None."""
    metadata = data.get("metadata")
    if metadata is None:
        return None
    if not isinstance(metadata, dict):
        raise ValidationError("metadata must be an object")

    meta_id = metadata.get("id")
    if not isinstance(meta_id, str) or not METADATA_ID_RE.match(meta_id):
        raise ValidationError(
            f"metadata.id is required and must look like 'myvillage:small_house_01', got {meta_id!r}"
        )

    category = metadata.get("category")
    if category not in VALID_CATEGORIES:
        raise ValidationError(f"metadata.category must be one of {list(VALID_CATEGORIES)}, got {category!r}")

    try:
        meta_size = validate_size(metadata.get("size"))
    except ValueError as exc:
        raise ValidationError(f"metadata.size: {exc}") from exc
    structure_size = validate_size(data.get("size"))
    if meta_size != structure_size:
        raise ValidationError(f"metadata.size {meta_size!r} does not match structure size {structure_size!r}")

    entrances = metadata.get("entrances", [])
    if not isinstance(entrances, list):
        raise ValidationError("metadata.entrances must be a list")
    for i, entrance in enumerate(entrances):
        _validate_anchor(entrance, meta_size, f"metadata.entrances[{i}]")

    connections = metadata.get("connections", [])
    if not isinstance(connections, list):
        raise ValidationError("metadata.connections must be a list")
    for i, connection in enumerate(connections):
        _validate_anchor(connection, meta_size, f"metadata.connections[{i}]")

    if category == "building" and len(entrances) < 1:
        raise ValidationError("metadata: category 'building' requires at least one entrance")
    if category == "road" and len(entrances) + len(connections) < 2:
        raise ValidationError("metadata: category 'road' requires at least two connections or entrances")

    weight = metadata.get("weight")
    if not isinstance(weight, (int, float)) or isinstance(weight, bool) or weight <= 0:
        raise ValidationError(f"metadata.weight must be a positive number, got {weight!r}")

    tags = metadata.get("tags")
    if not isinstance(tags, list) or not all(isinstance(t, str) and t for t in tags):
        raise ValidationError(f"metadata.tags must be an array of non-empty strings, got {tags!r}")

    return metadata


def _is_direct_blockstate(text: str) -> bool:
    """Return True when text appears to be an explicit blockstate, not an alias.

    Bare strings such as "floor" are treated as palette aliases. Direct block
    states should be namespaced, e.g. minecraft:oak_planks, or contain explicit
    property brackets.
    """
    return ":" in text or "[" in text


def _resolve_state_or_error(state_key: Any, palette: Mapping[str, str], context: str) -> str:
    if not isinstance(state_key, str) or not state_key.strip():
        raise ValidationError(f"{context}: state must be a non-empty string, got {state_key!r}")
    state_key = state_key.strip()
    if state_key in palette:
        state_text = palette[state_key]
    elif _is_direct_blockstate(state_key):
        state_text = state_key
    else:
        raise ValidationError(
            f"{context}: unknown state alias {state_key!r}. Add it to palette or use a namespaced blockstate like minecraft:oak_planks."
        )
    try:
        parse_block_state(state_text)
    except ValueError as exc:
        raise ValidationError(f"{context}: invalid blockstate for state {state_key!r}: {state_text!r}; {exc}") from exc
    validate_block_id(state_text, context)
    return state_text


def _put_block(
    blocks: Dict[Tuple[int, int, int], str],
    pos: Tuple[int, int, int],
    state_text: str,
) -> int:
    overwritten = 1 if pos in blocks else 0
    blocks[pos] = state_text
    return overwritten


def validate_structure(data: Mapping[str, Any]) -> OrderedDict[str, Any]:
    metadata = validate_metadata(data)
    size = validate_size(data.get("size"))
    sx, sy, sz = size
    volume = sx * sy * sz

    palette_raw = data.get("palette", {})
    if not isinstance(palette_raw, dict):
        raise ValidationError("palette must be an object mapping alias -> blockstate")
    palette: Dict[str, str] = {}
    for alias, state_text in palette_raw.items():
        if not isinstance(alias, str) or not alias.strip():
            raise ValidationError(f"palette contains invalid alias key: {alias!r}")
        if not isinstance(state_text, str) or not state_text.strip():
            raise ValidationError(f"palette[{alias!r}] must be a non-empty blockstate string, got {state_text!r}")
        try:
            parse_block_state(state_text)
        except ValueError as exc:
            raise ValidationError(f"palette[{alias!r}] invalid blockstate {state_text!r}: {exc}") from exc
        validate_block_id(state_text, f"palette[{alias!r}]")
        palette[alias] = state_text

    final_blocks: Dict[Tuple[int, int, int], str] = {}
    overwritten = 0

    blocks_raw = data.get("blocks", [])
    if blocks_raw is None:
        blocks_raw = []
    if not isinstance(blocks_raw, list):
        raise ValidationError("blocks must be a list")
    for i, entry in enumerate(blocks_raw):
        context = f"blocks[{i}]"
        if not isinstance(entry, dict):
            raise ValidationError(f"{context}: expected object, got {entry!r}")
        try:
            pos = validate_pos(entry.get("pos"), size, f"{context}.pos")
        except ValueError as exc:
            raise ValidationError(str(exc)) from exc
        state_text = _resolve_state_or_error(entry.get("state"), palette, f"{context} at {list(pos)}")
        overwritten += _put_block(final_blocks, pos, state_text)

    ops_raw = data.get("ops", data.get("operations", []))
    if ops_raw is None:
        ops_raw = []
    if not isinstance(ops_raw, list):
        raise ValidationError("ops/operations must be a list")
    for i, op in enumerate(ops_raw):
        context = f"ops[{i}]"
        if not isinstance(op, dict):
            raise ValidationError(f"{context}: expected object, got {op!r}")
        kind = str(op.get("op", "")).strip().lower()
        if kind == "set":
            try:
                pos = validate_pos(op.get("pos"), size, f"{context}.pos")
            except ValueError as exc:
                raise ValidationError(str(exc)) from exc
            state_text = _resolve_state_or_error(op.get("state"), palette, f"{context} set at {list(pos)}")
            overwritten += _put_block(final_blocks, pos, state_text)
        elif kind == "fill":
            try:
                from_pos = validate_pos(op.get("from"), size, f"{context}.from")
                to_pos = validate_pos(op.get("to"), size, f"{context}.to")
            except ValueError as exc:
                raise ValidationError(str(exc)) from exc
            state_text = _resolve_state_or_error(
                op.get("state"), palette, f"{context} fill from {list(from_pos)} to {list(to_pos)}"
            )
            for pos in iter_box(from_pos, to_pos):
                overwritten += _put_block(final_blocks, pos, state_text)
        elif kind == "line":
            try:
                from_pos = validate_pos(op.get("from"), size, f"{context}.from")
                to_pos = validate_pos(op.get("to"), size, f"{context}.to")
                positions = list(iter_line(from_pos, to_pos))
            except ValueError as exc:
                raise ValidationError(str(exc)) from exc
            state_text = _resolve_state_or_error(
                op.get("state"), palette, f"{context} line from {list(from_pos)} to {list(to_pos)}"
            )
            for pos in positions:
                overwritten += _put_block(final_blocks, pos, state_text)
        else:
            raise ValidationError(f"{context}: unsupported op {op.get('op')!r}; expected 'set', 'fill', or 'line'")

    if bool(data.get("fill_air", False)):
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    final_blocks.setdefault((x, y, z), "minecraft:air")

    unique_states = OrderedDict()
    non_air_blocks = 0
    for state_text in final_blocks.values():
        name, _props = parse_block_state(state_text)
        if name != "minecraft:air":
            non_air_blocks += 1
        unique_states.setdefault(state_text, len(unique_states))

    air_blocks = volume - non_air_blocks
    if air_blocks < 0:
        # This should be unreachable because coordinates are unique and bounded.
        raise ValidationError("internal error: non_air_blocks exceeded volume")

    stats: OrderedDict[str, Any] = OrderedDict([
        ("size", size),
        ("volume", volume),
        ("non_air_blocks", non_air_blocks),
        ("palette_count", len(unique_states)),
        ("overwritten_blocks", overwritten),
        ("air_blocks", air_blocks),
    ])
    if metadata is not None:
        stats["metadata_id"] = metadata["id"]
        stats["category"] = metadata["category"]
        stats["entrances"] = len(metadata.get("entrances", []))
        stats["connections"] = len(metadata.get("connections", []))
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate custom Minecraft structure JSON DSL")
    parser.add_argument("input_json", help="Input structure JSON")
    parser.add_argument(
        "--mc-version",
        default=SUPPORTED_MC_VERSION,
        help=f"Target Minecraft version (only {SUPPORTED_MC_VERSION} is supported)",
    )
    args = parser.parse_args()

    if args.mc_version != SUPPORTED_MC_VERSION:
        print(
            f"VALIDATION FAILED: unsupported --mc-version {args.mc_version!r}; "
            f"this project only targets Minecraft {SUPPORTED_MC_VERSION}",
            file=sys.stderr,
        )
        return 1

    try:
        with open(args.input_json, "r", encoding="utf-8") as f:
            data = json.load(f)
        stats = validate_structure(data)
    except (OSError, json.JSONDecodeError, ValidationError, ValueError) as exc:
        print(f"VALIDATION FAILED: {exc}", file=sys.stderr)
        return 1

    print("VALIDATION OK")
    for key, value in stats.items():
        print(f"{key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
