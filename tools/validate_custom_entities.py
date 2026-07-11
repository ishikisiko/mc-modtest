#!/usr/bin/env python3
"""Validate the first MyVillage custom entity and its pack resources."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import zlib
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path}: invalid JSON: {exc}") from exc


def _paeth(a: int, b: int, c: int) -> int:
    estimate = a + b - c
    distance_a = abs(estimate - a)
    distance_b = abs(estimate - b)
    distance_c = abs(estimate - c)
    if distance_a <= distance_b and distance_a <= distance_c:
        return a
    return b if distance_b <= distance_c else c


def read_png_rgba(path: Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"{path}: not a PNG")

    offset = 8
    width = height = None
    color_type = None
    channels = None
    idat = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _compression, _filter, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            channels = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
            if bit_depth != 8 or channels is None or interlace != 0:
                raise ValueError(f"{path}: expected non-interlaced 8-bit grayscale/RGB/RGBA PNG")
        elif chunk_type == b"IDAT":
            idat.extend(payload)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None or color_type is None or channels is None:
        raise ValueError(f"{path}: missing IHDR")

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    expected = height * (stride + 1)
    if len(raw) != expected:
        raise ValueError(f"{path}: unexpected decompressed byte count {len(raw)} != {expected}")

    decoded = bytearray()
    previous = bytearray(stride)
    cursor = 0
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
        decoded.extend(scanline)
        previous = scanline

    rgba = bytearray()
    for index in range(0, len(decoded), channels):
        pixel = decoded[index:index + channels]
        if color_type == 0:
            rgba.extend((pixel[0], pixel[0], pixel[0], 255))
        elif color_type == 2:
            rgba.extend((pixel[0], pixel[1], pixel[2], 255))
        elif color_type == 4:
            rgba.extend((pixel[0], pixel[0], pixel[0], pixel[1]))
        else:
            rgba.extend(pixel)
    return width, height, bytes(rgba)


def require_text(path: Path, needles: list[str], errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"missing_file:{path}")
        return ""
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            errors.append(f"missing_text:{path}:{needle}")
    return text


def require_json(path: Path, errors: list[str]) -> Any:
    if not path.is_file():
        errors.append(f"missing_file:{path}")
        return {}
    try:
        return load_json(path)
    except ValueError as exc:
        errors.append(str(exc))
        return {}


def validate_texture(root: Path, errors: list[str]) -> dict[str, Any]:
    texture_path = root / "src/main/resources/assets/myvillage/textures/entity/simple_fox/simple_fox.png"
    mask_path = root / "art/entities/simple_fox/uv_islands_mask.png"
    result: dict[str, Any] = {"texture": str(texture_path.relative_to(root))}
    if not texture_path.is_file() or not mask_path.is_file():
        if not texture_path.is_file():
            errors.append(f"missing_file:{texture_path}")
        if not mask_path.is_file():
            errors.append(f"missing_file:{mask_path}")
        return result

    try:
        width, height, texture = read_png_rgba(texture_path)
        mask_width, mask_height, mask = read_png_rgba(mask_path)
    except ValueError as exc:
        errors.append(str(exc))
        return result

    if (width, height) != (48, 32):
        errors.append(f"invalid_texture_dimensions:{width}x{height}")
    if (mask_width, mask_height) != (width, height):
        errors.append(f"mask_dimension_mismatch:{mask_width}x{mask_height}:{width}x{height}")
        return result

    texture_used: set[int] = set()
    mask_used: set[int] = set()
    colors: set[tuple[int, int, int]] = set()
    non_binary_alpha = 0
    uncleared_transparent = 0
    for pixel in range(width * height):
        base = pixel * 4
        red, green, blue, alpha = texture[base:base + 4]
        mask_red, mask_green, mask_blue, mask_alpha = mask[base:base + 4]
        if alpha:
            texture_used.add(pixel)
            colors.add((red, green, blue))
        elif red or green or blue:
            uncleared_transparent += 1
        if alpha not in (0, 255):
            non_binary_alpha += 1
        if mask_alpha and (mask_red or mask_green or mask_blue):
            mask_used.add(pixel)

    if texture_used != mask_used:
        errors.append(
            f"texture_mask_coverage_mismatch:texture={len(texture_used)}:mask={len(mask_used)}"
        )
    if len(mask_used) != 998:
        errors.append(f"unexpected_fox_uv_coverage:{len(mask_used)}")
    if non_binary_alpha:
        errors.append(f"non_binary_texture_alpha:{non_binary_alpha}")
    if uncleared_transparent:
        errors.append(f"uncleared_transparent_texels:{uncleared_transparent}")
    if len(colors) > 32:
        errors.append(f"texture_palette_too_large:{len(colors)}")

    result.update(
        {
            "dimensions": [width, height],
            "used_texels": len(texture_used),
            "mask_texels": len(mask_used),
            "opaque_colors": len(colors),
            "binary_alpha": non_binary_alpha == 0,
            "transparent_rgb_cleared": uncleared_transparent == 0,
        }
    )
    return result


def validate(root: Path = ROOT) -> dict[str, Any]:
    errors: list[str] = []
    java_root = root / "src/main/java/com/example/myvillage"
    resource_root = root / "src/main/resources"
    asset_root = resource_root / "assets/myvillage"
    data_root = resource_root / "data/myvillage"

    contract = require_text(
        root / "genops/contracts/entities/simple_fox.yaml",
        [
            "resource_location: myvillage:simple_fox",
            "route: vanilla",
            "base_texture: myvillage:textures/entity/simple_fox/simple_fox.png",
            "final_texture_size:",
            "width: 48",
            "height: 32",
            "working_texture_size:",
            "uv_face_map: art/entities/simple_fox/texture_faces.json",
            "native_texture_patches: art/entities/simple_fox/texture_patches.json",
            "natural: true",
            "placement: no_restrictions",
            "human_verdict_status: accepted",
        ],
        errors,
    )
    if "custom_state:" not in contract or "statement: SimpleFox adds no custom" not in contract:
        errors.append("contract_missing_explicit_custom_state_boundary")

    require_text(
        java_root / "entity/ModEntities.java",
        ["ENTITY_TYPES.register(\"simple_fox\"", ".sized(0.6F, 0.7F)", ".clientTrackingRange(8)"],
        errors,
    )
    require_text(
        java_root / "entity/ModEntityEvents.java",
        [
            "EntityAttributeCreationEvent",
            "RegisterSpawnPlacementsEvent",
            "SpawnPlacementTypes.NO_RESTRICTIONS",
            "Heightmap.Types.MOTION_BLOCKING_NO_LEAVES",
            "Operation.REPLACE",
        ],
        errors,
    )
    require_text(
        java_root / "entity/SimpleFoxEntity.java",
        ["extends Fox", "getBreedOffspring", "ModEntities.SIMPLE_FOX.get().create"],
        errors,
    )
    require_text(java_root / "MyVillageMod.java", ["ModEntities.register(modEventBus)"], errors)
    require_text(
        java_root / "client/entity/SimpleFoxRenderer.java",
        ["extends FoxRenderer", "textures/entity/simple_fox/simple_fox.png"],
        errors,
    )
    require_text(
        java_root / "client/MyVillageClient.java",
        ["EntityRenderersEvent.RegisterRenderers", "SimpleFoxRenderer::new"],
        errors,
    )

    common_sources = [java_root / "MyVillageMod.java", *sorted((java_root / "entity").glob("*.java"))]
    for path in common_sources:
        if path.is_file() and "net.minecraft.client" in path.read_text(encoding="utf-8"):
            errors.append(f"client_import_in_common_source:{path.relative_to(root)}")

    require_text(
        java_root / "item/ModItems.java",
        ["DeferredSpawnEggItem", "SIMPLE_FOX_SPAWN_EGG", "output.accept(SIMPLE_FOX_SPAWN_EGG.get())"],
        errors,
    )

    item_model = require_json(asset_root / "models/item/simple_fox_spawn_egg.json", errors)
    if item_model.get("parent") != "minecraft:item/template_spawn_egg":
        errors.append("invalid_spawn_egg_model_parent")

    for locale, expected in (
        ("en_us", ("Simple Fox", "Simple Fox Spawn Egg")),
        ("zh_cn", ("简易狐狸", "简易狐狸生成蛋")),
    ):
        lang = require_json(asset_root / f"lang/{locale}.json", errors)
        if lang.get("entity.myvillage.simple_fox") != expected[0]:
            errors.append(f"invalid_entity_translation:{locale}")
        if lang.get("item.myvillage.simple_fox_spawn_egg") != expected[1]:
            errors.append(f"invalid_spawn_egg_translation:{locale}")

    loot = require_json(data_root / "loot_table/entities/simple_fox.json", errors)
    if loot.get("type") != "minecraft:entity" or loot.get("pools") != []:
        errors.append("invalid_simple_fox_loot_table")

    biome_tag = require_json(data_root / "tags/worldgen/biome/has_simple_fox.json", errors)
    if biome_tag.get("replace") is not False or biome_tag.get("values") != ["#minecraft:is_taiga"]:
        errors.append("invalid_simple_fox_biome_tag")

    modifier = require_json(data_root / "neoforge/biome_modifier/add_simple_fox_spawns.json", errors)
    spawner = modifier.get("spawners", {}) if isinstance(modifier, dict) else {}
    if modifier.get("type") != "neoforge:add_spawns":
        errors.append("invalid_simple_fox_biome_modifier_type")
    if modifier.get("biomes") != "#myvillage:has_simple_fox":
        errors.append("invalid_simple_fox_biome_modifier_tag")
    if not isinstance(spawner, dict) or spawner != {
        "type": "myvillage:simple_fox",
        "weight": 8,
        "minCount": 1,
        "maxCount": 2,
    }:
        errors.append("invalid_simple_fox_spawner")

    uv_truth = require_json(root / "art/entities/simple_fox/uv_truth.json", errors)
    if uv_truth and uv_truth.get("canvas") != {"width": 48, "height": 32}:
        errors.append("invalid_simple_fox_uv_truth_canvas")
    face_map = require_json(root / "art/entities/simple_fox/texture_faces.json", errors)
    faces = face_map.get("faces", []) if isinstance(face_map, dict) else []
    if face_map and (
        face_map.get("canvas") != {"width": 48, "height": 32}
        or len(faces) != 48
        or len({face.get("id") for face in faces if isinstance(face, dict)}) != 48
    ):
        errors.append("invalid_simple_fox_face_map")
    patches = require_json(root / "art/entities/simple_fox/texture_patches.json", errors)
    if patches and (
        patches.get("canvas") != {"width": 48, "height": 32}
        or {patch.get("face") for patch in patches.get("patches", []) if isinstance(patch, dict)}
        != {"head.north", "muzzle.north"}
    ):
        errors.append("invalid_simple_fox_native_texture_patches")
    provenance = require_json(root / "art/entities/simple_fox/art_provenance.json", errors)
    imagegen = provenance.get("imagegen", {}) if isinstance(provenance, dict) else {}
    if provenance and (
        "gpt_image" in provenance
        or imagegen.get("generator") != "codex_builtin_imagegen"
        or imagegen.get("direct_api") is not False
        or imagegen.get("concept_generation", {}).get("status") != "executed"
        or imagegen.get("atlas_edit", {}).get("status") not in {"adopted", "rejected"}
        or imagegen.get("deterministic_composite", {}).get("protected_pixels_changed") != 0
    ):
        errors.append("invalid_simple_fox_imagegen_provenance")
    concept_path = root / "art/entities/simple_fox/references/concept_builtin.png"
    concept_record = imagegen.get("concept_generation", {}) if isinstance(imagegen, dict) else {}
    if not concept_path.is_file():
        errors.append(f"missing_file:{concept_path}")
    elif concept_record.get("output_sha256") != sha256_file(concept_path):
        errors.append("simple_fox_concept_hash_mismatch")

    texture = validate_texture(root, errors)
    return {
        "schema_version": 1,
        "entity": "myvillage:simple_fox",
        "texture": texture,
        "errors": errors,
        "status": "pass" if not errors else "fail",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--report", type=Path, default=ROOT / "reports/custom_entity_validation.json")
    args = parser.parse_args()

    root = args.root.resolve()
    report = validate(root)
    report_path = args.report if args.report.is_absolute() else root / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
