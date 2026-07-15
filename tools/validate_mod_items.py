#!/usr/bin/env python3
"""Validate MyVillage item/block-item registration and client resources."""

from __future__ import annotations

import json
import re
import struct
import sys
import zlib
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "myvillage"
MOD_ITEMS = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage" / "item" / "ModItems.java"
MOD_BLOCKS = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage" / "block" / "ModBlocks.java"
ROCKERY_BLOCK = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage" / "block" / "RockeryBlock.java"
LANG = ASSET_ROOT / "lang" / "en_us.json"
ZH_LANG = ASSET_ROOT / "lang" / "zh_cn.json"

SWORD_CONTRACTS = (
    (
        "qingfeng_sword",
        "QINGFENG_SWORD",
        "Qingfeng Sword",
        "青锋剑",
        "qingfeng",
        False,
    ),
    (
        "xuanyue_zhenshan_sword",
        "XUANYUE_ZHENSHAN_SWORD",
        "Xuanyue Zhenshan Sword",
        "玄岳镇山剑",
        "xuanyue_zhenshan",
        True,
    ),
    (
        "chilian_lihuo_sword",
        "CHILIAN_LIHUO_SWORD",
        "Chilian Lihuo Sword",
        "赤炼离火剑",
        "chilian_lihuo",
        True,
    ),
    (
        "qingxiao_liuyun_sword",
        "QINGXIAO_LIUYUN_SWORD",
        "Qingxiao Liuyun Sword",
        "青霄流云剑",
        "qingxiao_liuyun",
        True,
    ),
)
SWORD_ATTRIBUTES = "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)"
CREATIVE_SWORD_ORDER = (
    "RIDEABLE_FLYING_SWORD",
    "QINGFENG_SWORD",
    "XUANYUE_ZHENSHAN_SWORD",
    "CHILIAN_LIHUO_SWORD",
    "QINGXIAO_LIUYUN_SWORD",
    "LOW_GRADE_SPIRIT_STONE",
)


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


def _paeth(a: int, b: int, c: int) -> int:
    estimate = a + b - c
    distance_a = abs(estimate - a)
    distance_b = abs(estimate - b)
    distance_c = abs(estimate - c)
    if distance_a <= distance_b and distance_a <= distance_c:
        return a
    return b if distance_b <= distance_c else c


def png_non_binary_alpha_count(path: Path) -> int:
    """Return the count of semi-transparent pixels in an 8-bit alpha PNG."""
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"{path}: not a PNG file")

    offset = 8
    width = height = channels = None
    idat = bytearray()
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError(f"{path}: truncated PNG chunk")
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        payload_end = offset + 8 + length
        if payload_end + 4 > len(data):
            raise ValueError(f"{path}: truncated PNG chunk")
        payload = data[offset + 8:payload_end]
        offset = payload_end + 4

        if chunk_type == b"IHDR":
            if len(payload) != 13:
                raise ValueError(f"{path}: invalid IHDR")
            width, height, bit_depth, color_type, compression, filtering, interlace = (
                struct.unpack(">IIBBBBB", payload)
            )
            channels = {4: 2, 6: 4}.get(color_type)
            if (bit_depth != 8 or channels is None or compression != 0
                    or filtering != 0 or interlace != 0):
                raise ValueError(
                    f"{path}: expected non-interlaced 8-bit grayscale-alpha or RGBA PNG")
        elif chunk_type == b"IDAT":
            idat.extend(payload)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None or channels is None or not idat:
        raise ValueError(f"{path}: incomplete alpha PNG")

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    expected_length = height * (stride + 1)
    if len(raw) != expected_length:
        raise ValueError(
            f"{path}: unexpected decompressed byte count {len(raw)} != {expected_length}")

    previous = bytearray(stride)
    cursor = 0
    non_binary_alpha = 0
    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        scanline = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        for index, value in enumerate(scanline):
            left = scanline[index - channels] if index >= channels else 0
            up = previous[index]
            up_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                scanline[index] = (value + left) & 0xFF
            elif filter_type == 2:
                scanline[index] = (value + up) & 0xFF
            elif filter_type == 3:
                scanline[index] = (value + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                scanline[index] = (value + _paeth(left, up, up_left)) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"{path}: unsupported PNG filter {filter_type}")

        non_binary_alpha += sum(
            alpha not in (0, 255)
            for alpha in scanline[channels - 1::channels]
        )
        previous = scanline

    return non_binary_alpha


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


def validate_sword_registration(
        items_text: str,
        item_id: str,
        holder: str,
        error_key: str,
        errors: list[str]) -> None:
    declaration = re.search(
        rf"public\s+static\s+final\s+DeferredItem<SwordItem>\s+{re.escape(holder)}\s*=\s*"
        rf"(?P<body>.*?\)\s*;)",
        items_text,
        re.DOTALL,
    )
    if declaration is None:
        errors.append(f"{error_key}_registration_drift:DeferredItem<SwordItem> {holder}")
        return

    body = declaration.group("body")
    if re.search(rf'ITEMS\.registerItem\(\s*"{re.escape(item_id)}"', body) is None:
        errors.append(f'{error_key}_registration_drift:ITEMS.registerItem("{item_id}"')

    exact_attributes = re.compile(
        r"new\s+SwordItem\(\s*Tiers\.DIAMOND\s*,\s*"
        r"[A-Za-z_][A-Za-z0-9_]*\.attributes\(\s*"
        r"SwordItem\.createAttributes\(\s*Tiers\.DIAMOND\s*,\s*3\s*,\s*-2\.4F\s*\)"
        r"\s*\)\s*\)",
        re.DOTALL,
    )
    if exact_attributes.search(body) is None:
        errors.append(f"{error_key}_registration_drift:{SWORD_ATTRIBUTES}")


def validate_swords(items_text: str, lang: dict[str, Any], zh_lang: dict[str, Any], errors: list[str]) -> None:
    for item_id, holder, en_name, zh_name, error_key, binary_alpha in SWORD_CONTRACTS:
        validate_sword_registration(items_text, item_id, holder, error_key, errors)

        lang_key = f"item.myvillage.{item_id}"
        if lang.get(lang_key) != en_name:
            errors.append(f"{error_key}_en_us_name")
        if zh_lang.get(lang_key) != zh_name:
            errors.append(f"{error_key}_zh_cn_name")

        model_path = ASSET_ROOT / "models" / "item" / f"{item_id}.json"
        if not model_path.is_file():
            errors.append(f"{error_key}_model_missing")
        else:
            try:
                model = load_json(model_path)
            except ValueError:
                errors.append(f"{error_key}_model_json")
            else:
                if (model.get("parent") != "minecraft:item/handheld"
                        or model.get("textures", {}).get("layer0") != f"myvillage:item/{item_id}"):
                    errors.append(f"{error_key}_model_contract")

        texture = ASSET_ROOT / "textures" / "item" / f"{item_id}.png"
        if not texture.is_file():
            errors.append(f"{error_key}_texture_missing")
        else:
            try:
                dimensions = png_size(texture)
            except ValueError:
                errors.append(f"{error_key}_texture_png")
            else:
                if dimensions != (64, 64):
                    errors.append(f"{error_key}_texture_dimensions:expected_64x64")
                if not png_has_alpha(texture):
                    errors.append(f"{error_key}_texture_alpha")
                elif binary_alpha:
                    try:
                        non_binary_alpha = png_non_binary_alpha_count(texture)
                    except (ValueError, struct.error, zlib.error):
                        errors.append(f"{error_key}_texture_alpha_decode")
                    else:
                        if non_binary_alpha:
                            errors.append(
                                f"{error_key}_texture_non_binary_alpha:{non_binary_alpha}")

    creative_positions = [
        items_text.find(f"output.accept({holder}.get())")
        for holder in CREATIVE_SWORD_ORDER
    ]
    if any(position < 0 for position in creative_positions) or creative_positions != sorted(creative_positions):
        errors.append(
            "sword_creative_order:rideable->qingfeng->xuanyue->chilian->qingxiao->spirit_stone")

    tag_path = ROOT / "src/main/resources/data/minecraft/tags/item/swords.json"
    if not tag_path.is_file():
        errors.append("qingfeng_sword_tag_missing")
    else:
        try:
            tag = load_json(tag_path)
        except ValueError as exc:
            errors.append(str(exc))
        else:
            values = tag.get("values", [])
            if tag.get("replace") is not False or not isinstance(values, list):
                errors.append("qingfeng_sword_tag_contract")
                values = []
            for item_id, _, _, _, error_key, _ in SWORD_CONTRACTS:
                if f"myvillage:{item_id}" not in values:
                    errors.append(f"{error_key}_sword_tag_contract")


def validate_qingfeng_recipe(errors: list[str]) -> None:
    recipe_path = ROOT / "src/main/resources/data/myvillage/recipe/qingfeng_sword.json"
    if not recipe_path.is_file():
        errors.append("qingfeng_recipe_missing")
        return

    try:
        recipe = load_json(recipe_path)
    except ValueError as exc:
        errors.append(str(exc))
        return
    expected_pattern = ["D", "D", "S"]
    if (recipe.get("type") != "minecraft:crafting_shaped"
            or recipe.get("pattern") != expected_pattern
            or recipe.get("key", {}).get("D", {}).get("item") != "minecraft:diamond"
            or recipe.get("key", {}).get("S", {}).get("item") != "minecraft:stick"
            or recipe.get("result", {}).get("id") != "myvillage:qingfeng_sword"):
        errors.append("qingfeng_recipe_contract")


def validate() -> list[str]:
    errors: list[str] = []
    items = registered_items()
    block_items = registered_block_items()
    blocks = registered_blocks()
    lang = load_json(LANG)
    zh_lang = load_json(ZH_LANG)
    items_text = MOD_ITEMS.read_text(encoding="utf-8")
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

    validate_swords(items_text, lang, zh_lang, errors)
    validate_qingfeng_recipe(errors)

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
