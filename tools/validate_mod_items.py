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
ZH_LANG = ASSET_ROOT / "lang" / "zh_cn.json"
QINGFENG_ID = "qingfeng_sword"


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


def png_has_alpha(path: Path) -> bool:
    data = path.read_bytes()
    return (data.startswith(b"\x89PNG\r\n\x1a\n")
            and data[12:16] == b"IHDR"
            and data[25] in (4, 6))


def registered_items() -> set[str]:
    text = MOD_ITEMS.read_text(encoding="utf-8")
    explicit = re.findall(r'ITEMS\.registerItem\(\s*"([a-z0-9_./-]+)"', text, re.MULTILINE)
    simple = re.findall(r'ITEMS\.registerSimpleItem\(\s*"([a-z0-9_./-]+)"', text, re.MULTILINE)
    return set(explicit) | set(simple)


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
    return set(re.findall(
        r'BLOCKS\.registerBlock\(\s*"([a-z0-9_./-]+)"',
        text,
        re.MULTILINE,
    ))


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
    zh_lang = load_json(ZH_LANG)
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

    if QINGFENG_ID in items:
        items_text = MOD_ITEMS.read_text(encoding="utf-8")
        registration_tokens = (
            "DeferredItem<SwordItem> QINGFENG_SWORD",
            'ITEMS.registerItem("qingfeng_sword"',
            "Tiers.DIAMOND",
            "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
        )
        for token in registration_tokens:
            if token not in items_text:
                errors.append(f"qingfeng_registration_drift:{token}")
        rideable_position = items_text.find("output.accept(RIDEABLE_FLYING_SWORD.get())")
        qingfeng_position = items_text.find("output.accept(QINGFENG_SWORD.get())")
        spirit_position = items_text.find("output.accept(LOW_GRADE_SPIRIT_STONE.get())")
        if not (0 <= rideable_position < qingfeng_position < spirit_position):
            errors.append("qingfeng_creative_order")

        if lang.get("item.myvillage.qingfeng_sword") != "Qingfeng Sword":
            errors.append("qingfeng_en_us_name")
        if zh_lang.get("item.myvillage.qingfeng_sword") != "青锋剑":
            errors.append("qingfeng_zh_cn_name")

        texture = ASSET_ROOT / "textures/item/qingfeng_sword.png"
        if texture.is_file():
            if png_size(texture) != (64, 64):
                errors.append("qingfeng_texture_dimensions:expected_64x64")
            if not png_has_alpha(texture):
                errors.append("qingfeng_texture_alpha")

        recipe_path = ROOT / "src/main/resources/data/myvillage/recipe/qingfeng_sword.json"
        if not recipe_path.is_file():
            errors.append("qingfeng_recipe_missing")
        else:
            recipe = load_json(recipe_path)
            expected_pattern = ["D", "D", "S"]
            if (recipe.get("type") != "minecraft:crafting_shaped"
                    or recipe.get("pattern") != expected_pattern
                    or recipe.get("key", {}).get("D", {}).get("item") != "minecraft:diamond"
                    or recipe.get("key", {}).get("S", {}).get("item") != "minecraft:stick"
                    or recipe.get("result", {}).get("id") != "myvillage:qingfeng_sword"):
                errors.append("qingfeng_recipe_contract")

        tag_path = ROOT / "src/main/resources/data/minecraft/tags/item/swords.json"
        if not tag_path.is_file():
            errors.append("qingfeng_sword_tag_missing")
        else:
            tag = load_json(tag_path)
            if tag.get("replace") is not False or "myvillage:qingfeng_sword" not in tag.get("values", []):
                errors.append("qingfeng_sword_tag_contract")

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
