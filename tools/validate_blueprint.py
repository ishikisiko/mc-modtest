#!/usr/bin/env python3
"""Validate a Minecraft town blueprint JSON file."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def validate_blueprint(data: dict) -> list[str]:
    errors: list[str] = []

    if data.get("schema_version") != 1:
        errors.append("schema_version must be 1")

    blueprint_id = data.get("id")
    if not isinstance(blueprint_id, str) or ":" not in blueprint_id:
        errors.append("id must be a namespaced string")

    size = data.get("size")
    if (
        not isinstance(size, list)
        or len(size) != 3
        or not all(isinstance(value, int) and value > 0 for value in size)
    ):
        errors.append("size must be three positive integers")

    palette = data.get("palette")
    if not isinstance(palette, dict):
        errors.append("palette must be an object")
        palette = {}

    blocks = data.get("blocks")
    if not isinstance(blocks, list):
        errors.append("blocks must be a list")
        blocks = []

    for index, block in enumerate(blocks):
        if not isinstance(block, dict):
            errors.append(f"blocks[{index}] must be an object")
            continue

        pos = block.get("pos")
        if not isinstance(pos, list) or len(pos) != 3 or not all(isinstance(value, int) for value in pos):
            errors.append(f"blocks[{index}].pos must be three integers")
        elif isinstance(size, list) and len(size) == 3 and all(isinstance(value, int) for value in size):
            if any(coord < 0 or coord >= limit for coord, limit in zip(pos, size)):
                errors.append(f"blocks[{index}].pos is outside size bounds")

        palette_key = block.get("palette")
        block_id = block.get("block")
        if palette_key is None and block_id is None:
            errors.append(f"blocks[{index}] must define palette or block")
        if palette_key is not None and palette_key not in palette:
            errors.append(f"blocks[{index}].palette references unknown key")
        if block_id is not None and (not isinstance(block_id, str) or ":" not in block_id):
            errors.append(f"blocks[{index}].block must be namespaced")

        state = block.get("state", {})
        if not isinstance(state, dict) or not all(isinstance(value, str) for value in state.values()):
            errors.append(f"blocks[{index}].state must be an object with string values")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("blueprint", type=Path)
    args = parser.parse_args()

    data = json.loads(args.blueprint.read_text(encoding="utf-8"))
    errors = validate_blueprint(data)
    if errors:
        for error in errors:
            print(error)
        return 1

    print("valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

