#!/usr/bin/env python3
"""Validate MyVillage item/block-item registration and client resources."""

from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "myvillage"
MOD_ITEMS = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage" / "item" / "ModItems.java"
MOD_BLOCKS = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage" / "block" / "ModBlocks.java"
ROCKERY_BLOCK = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage" / "block" / "RockeryBlock.java"
LANG = ASSET_ROOT / "lang" / "en_us.json"


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path}: invalid JSON: {exc}") from exc


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
        raise ValueError(f"{path}: not a PNG file")
    width, height = struct.unpack(">II", data[16:24])
    return width, height


def registered_items() -> set[str]:
    text = MOD_ITEMS.read_text(encoding="utf-8")
    return set(re.findall(r'ITEMS\.registerItem\("([a-z0-9_./-]+)"', text))


def registered_block_items() -> set[str]:
    text = MOD_ITEMS.read_text(encoding="utf-8")
    pattern = re.compile(
        r'ITEMS\.registerItem\("([a-z0-9_./-]+)"\s*,\s*'
        r'props\s*->\s*new\s+BlockItem\(',
        re.MULTILINE,
    )
    return set(pattern.findall(text))


def registered_blocks() -> set[str]:
    text = MOD_BLOCKS.read_text(encoding="utf-8")
    return set(re.findall(r'BLOCKS\.registerBlock\("([a-z0-9_./-]+)"', text))


def texture_path(ref: str) -> Path | None:
    if ref.startswith("#"):
        return None
    namespace, _, path = ref.partition(":")
    if not path:
        return None
    if namespace == "myvillage":
        return ASSET_ROOT / "textures" / f"{path}.png"
    return None


def model_path(ref: str) -> Path | None:
    namespace, _, path = ref.partition(":")
    if not path:
        return None
    if namespace == "myvillage":
        return ASSET_ROOT / "models" / f"{path}.json"
    return None


def validate_model(path: Path, errors: list[str], visited: set[Path] | None = None) -> None:
    visited = visited or set()
    if path in visited:
        return
    visited.add(path)
    if not path.exists():
        errors.append(f"missing_model:{path.relative_to(ROOT)}")
        return
    try:
        model = load_json(path)
    except ValueError as exc:
        errors.append(str(exc))
        return

    parent = model.get("parent")
    if isinstance(parent, str):
        parent_path = model_path(parent)
        if parent_path is not None:
            validate_model(parent_path, errors, visited)

    textures = model.get("textures", {})
    if isinstance(textures, dict):
        for key, ref in textures.items():
            if not isinstance(ref, str):
                errors.append(f"invalid_texture_ref:{path.relative_to(ROOT)}:{key}")
                continue
            tex = texture_path(ref)
            if tex is None:
                continue
            if not tex.exists():
                errors.append(f"missing_texture:{tex.relative_to(ROOT)}")
                continue
            try:
                width, height = png_size(tex)
            except ValueError as exc:
                errors.append(str(exc))
                continue
            if width <= 0 or height <= 0:
                errors.append(f"invalid_texture_size:{tex.relative_to(ROOT)}:{width}x{height}")


def validate() -> list[str]:
    errors: list[str] = []
    items = registered_items()
    block_items = registered_block_items()
    blocks = registered_blocks()
    lang = load_json(LANG)
    rockery_text = ROCKERY_BLOCK.read_text(encoding="utf-8") if ROCKERY_BLOCK.exists() else ""

    if not items:
        errors.append("no_registered_items")

    for item_id in sorted(items):
        item_model = ASSET_ROOT / "models" / "item" / f"{item_id}.json"
        if not item_model.exists():
            errors.append(f"missing_item_model:{item_model.relative_to(ROOT)}")
        else:
            validate_model(item_model, errors)

        item_key = f"item.myvillage.{item_id}"
        block_key = f"block.myvillage.{item_id}"
        if item_key not in lang and block_key not in lang:
            errors.append(f"missing_lang:{item_key}|{block_key}")

    for item_id in sorted(block_items):
        if item_id not in blocks:
            errors.append(f"missing_block_registration:{item_id}")
        blockstate = ASSET_ROOT / "blockstates" / f"{item_id}.json"
        if not blockstate.exists():
            errors.append(f"missing_blockstate:{blockstate.relative_to(ROOT)}")
        else:
            try:
                blockstate_data = load_json(blockstate)
            except ValueError as exc:
                errors.append(str(exc))
            else:
                variants = blockstate_data.get("variants", {})
                if not isinstance(variants, dict) or not variants:
                    errors.append(f"missing_blockstate_variants:{blockstate.relative_to(ROOT)}")

    if "rockery_block" in block_items:
        if "getCloneItemStack(" not in rockery_text or "ROCKERY_BLOCK_ITEM.get()" not in rockery_text:
            errors.append("missing_pick_block_clone:myvillage:rockery_block")

    return errors


def main() -> int:
    errors = validate()
    if errors:
        for error in errors:
            print(error)
        return 1
    print("mod item validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
