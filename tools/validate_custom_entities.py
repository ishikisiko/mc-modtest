#!/usr/bin/env python3
"""Validate the MyVillage custom entities and their pack resources.

Two routes: the vanilla-model `myvillage:simple_fox`, checked against its pinned contract and UV
evidence, and every beast listed in `data/myvillage/beast/index.json` (BeastEntity subclasses with
data-driven moves and generated model/animation/texture files), checked generically: server data
invariants, client asset files and their cross-file rules, the Entity Contract against the data
file and Java registration, resources, side safety, and the combat package staying beast-neutral.
Standard library only; tuned numbers are read from the data file, never pinned here.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import zlib
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = "src/main/java/com/example/myvillage"
RESOURCE_ROOT = "src/main/resources"
BEAST_INDEX = "data/myvillage/beast/index.json"
TICKS_PER_SECOND = 20
CLIP_LENGTH_TOLERANCE = 1.0e-4
RESERVED_CLIPS = ("idle", "walk", "run")
NPC_CLIPS = ("idle", "walk")
NPCGEN_BUILD = "tools/npcgen/build.py"
NAME_PATTERN = re.compile(r"^[a-z0-9_]+$")
ID_PATTERN = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
# Field sets of schema 1 (mirroring BeastDataLoader, BeastModelFile, BeastAnimationFile).
BEAST_FIELDS = {"schema", "entity", "attributes", "chase", "stagger", "moves"}
BEAST_ATTRIBUTE_RULES = {
    "max_health": "positive",
    "attack_damage": "non_negative",
    "movement_speed": "positive",
    "follow_range": "positive",
    "armor": "non_negative",
    "knockback_resistance": "fraction",
    "step_height": "non_negative",
}
BEAST_CHASE_FIELDS = {"speed_modifier", "move_gap_ticks", "cancelled_cooldown_ticks"}
BEAST_MOVE_FIELDS = {
    "id", "animation", "total_ticks", "windup_ticks", "turn_lock_tick", "active_ticks", "immune_ticks",
    "damage_multiplier", "maximum_targets", "use_range", "cooldown_ticks", "weight", "lunge", "hit", "knockback",
}
BEAST_LUNGE_FIELDS = {"tick", "forward_min", "forward_max", "up"}
BEAST_HIT_FIELDS = {"forward", "half_width", "height"}
BEAST_KNOCKBACK_FIELDS = {"strength", "lift"}
MODEL_FIELDS = {"schema", "id", "texture", "look", "shadow_radius", "bones"}
MODEL_OPTIONAL_FIELDS = {"scale"}  # renderer scale, 1 when absent
BONE_FIELDS = {"name", "parent", "pivot", "rotation", "cubes"}
CUBE_FIELDS = {"origin", "size", "uv", "inflate", "mirror"}
ANIMATION_FIELDS = {"schema", "id", "clips"}
CLIP_FIELDS = {"length", "loop", "channels"}
CHANNEL_FIELDS = {"bone", "target", "keyframes"}
KEYFRAME_FIELDS = {"time", "value", "interp"}
CHANNEL_TARGETS = {"rotation", "position", "scale"}
INTERPOLATIONS = {"linear", "catmullrom"}


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


def validate_simple_fox(root: Path, errors: list[str]) -> dict[str, Any]:
    """The vanilla-route fox: pinned contract, registration, resources, spawning, UV evidence."""
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

    return validate_texture(root, errors)


# ---------------------------------------------------------------------------------------------
# Beasts: pure checks over parsed files (unit tested on mutated copies), then the file-system pass.
# ---------------------------------------------------------------------------------------------

def is_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def number_pair(value: Any) -> bool:
    return isinstance(value, list) and len(value) == 2 and all(is_number(v) for v in value)


def int_pair(value: Any) -> bool:
    return isinstance(value, list) and len(value) == 2 and all(is_int(v) for v in value)


def vec3(value: Any) -> bool:
    return isinstance(value, list) and len(value) == 3 and all(is_number(v) for v in value)


def split_id(entity_id: str) -> tuple[str, str]:
    namespace, _, path = entity_id.partition(":")
    return namespace, path


def constant_name(name: str) -> str:
    return name.upper()


def class_name(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("_")) + "Entity"


def _fields(obj: Any, path: str, allowed: set[str], errors: list[str]) -> bool:
    """Exactly ``allowed`` keys (BeastDataLoader rejects unknown and missing fields alike)."""
    if not isinstance(obj, dict):
        errors.append(f"not_an_object:{path}")
        return False
    for key in sorted(set(obj) - allowed):
        errors.append(f"unknown_field:{path}.{key}" if path else f"unknown_field:{key}")
    for key in sorted(allowed - set(obj)):
        errors.append(f"missing_field:{path}.{key}" if path else f"missing_field:{key}")
    return allowed <= set(obj)


def _rule(value: Any, rule: str) -> bool:
    if not is_number(value):
        return False
    if rule == "positive":
        return value > 0
    if rule == "non_negative":
        return value >= 0
    return 0 <= value <= 1  # fraction


def check_beast_index(index: Any) -> tuple[list[str], list[str]]:
    """(errors, listed beast ids) for ``data/myvillage/beast/index.json``."""
    errors: list[str] = []
    if not _fields(index, "", {"schema", "beasts"}, errors):
        return errors, []
    if index["schema"] != 1:
        errors.append(f"unsupported_schema:{index['schema']}")
    beasts = index["beasts"]
    if not isinstance(beasts, list) or not all(isinstance(b, str) and ID_PATTERN.match(b) for b in beasts):
        errors.append("beasts_not_a_list_of_ids")
        return errors, []
    if len(set(beasts)) != len(beasts):
        errors.append("duplicate_beast_id")
    return errors, list(dict.fromkeys(beasts))


def check_beast_data(data: Any, entity_id: str) -> list[str]:
    """Schema-1 invariants of one beast data file (the rules BeastDataLoader enforces)."""
    errors: list[str] = []
    if not _fields(data, "", BEAST_FIELDS, errors):
        return errors
    if data["schema"] != 1:
        errors.append(f"unsupported_schema:{data['schema']}")
    if data["entity"] != entity_id:
        errors.append(f"entity_mismatch:{data['entity']}")

    attributes = data["attributes"]
    if _fields(attributes, "attributes", set(BEAST_ATTRIBUTE_RULES), errors):
        for name, rule in BEAST_ATTRIBUTE_RULES.items():
            if not _rule(attributes[name], rule):
                errors.append(f"invalid_attribute:{name}:{rule}")
    chase = data["chase"]
    if _fields(chase, "chase", BEAST_CHASE_FIELDS, errors):
        if not _rule(chase["speed_modifier"], "positive"):
            errors.append("invalid_chase:speed_modifier")
        for key in ("move_gap_ticks", "cancelled_cooldown_ticks"):
            if not is_int(chase[key]) or chase[key] < 0:
                errors.append(f"invalid_chase:{key}")
    stagger_clip = None
    if _fields(data["stagger"], "stagger", {"animation"}, errors):
        stagger_clip = data["stagger"]["animation"]
        if not isinstance(stagger_clip, str) or not NAME_PATTERN.match(stagger_clip):
            errors.append("invalid_clip_name:stagger.animation")
        elif stagger_clip in RESERVED_CLIPS:
            errors.append(f"reserved_clip:stagger.animation:{stagger_clip}")

    moves = data["moves"]
    if not isinstance(moves, list) or not moves:
        errors.append("moves_empty")
        return errors
    clips = set(RESERVED_CLIPS) | ({stagger_clip} if isinstance(stagger_clip, str) else set())
    move_ids: set[str] = set()
    for position, move in enumerate(moves):
        errors.extend(check_beast_move(move, f"moves[{position}]", move_ids, clips))
    return errors


def check_beast_move(move: Any, path: str, move_ids: set[str], clips: set[str]) -> list[str]:
    errors: list[str] = []
    if not _fields(move, path, BEAST_MOVE_FIELDS, errors):
        return errors
    move_id, clip = move["id"], move["animation"]
    if not isinstance(move_id, str) or not ID_PATTERN.match(move_id):
        errors.append(f"invalid_move_id:{path}")
    elif move_id in move_ids:
        errors.append(f"duplicate_move_id:{move_id}")
    else:
        move_ids.add(move_id)
    where = move_id if isinstance(move_id, str) else path
    if not isinstance(clip, str) or not NAME_PATTERN.match(clip):
        errors.append(f"invalid_clip_name:{where}")
    elif clip in clips:
        errors.append(f"clip_reserved_or_reused:{where}:{clip}")
    else:
        clips.add(clip)

    total = move["total_ticks"]
    if not is_int(total) or total <= 0:
        errors.append(f"invalid_total_ticks:{where}")
        return errors
    active, immune = move["active_ticks"], move["immune_ticks"]
    windup, lock = move["windup_ticks"], move["turn_lock_tick"]
    if not int_pair(active) or not 0 <= active[0] <= active[1] < total:
        errors.append(f"active_ticks_outside_move:{where}")
        return errors
    if not int_pair(immune) or not 0 <= immune[0] <= immune[1] < total:
        errors.append(f"immune_ticks_outside_move:{where}")
    if not is_int(windup) or not 0 <= windup <= active[0]:
        errors.append(f"windup_after_first_active_tick:{where}")
    elif not is_int(lock) or not 0 <= lock <= windup:
        errors.append(f"turn_lock_after_windup:{where}")
    if not _rule(move["damage_multiplier"], "positive"):
        errors.append(f"invalid_damage_multiplier:{where}")
    for key in ("maximum_targets", "weight"):
        if not is_int(move[key]) or move[key] <= 0:
            errors.append(f"invalid_{key}:{where}")
    if not is_int(move["cooldown_ticks"]) or move["cooldown_ticks"] < 0:
        errors.append(f"invalid_cooldown_ticks:{where}")
    use_range = move["use_range"]
    if not number_pair(use_range) or not 0 <= use_range[0] <= use_range[1]:
        errors.append(f"invalid_use_range:{where}")

    lunge = move["lunge"]
    if _fields(lunge, f"{path}.lunge", BEAST_LUNGE_FIELDS, errors):
        if not is_int(lunge["tick"]) or not (is_int(lock) and lock <= lunge["tick"] <= active[1]):
            errors.append(f"lunge_tick_outside_lock_to_last_active:{where}")
        if not all(_rule(lunge[k], "non_negative") for k in ("forward_min", "forward_max", "up")):
            errors.append(f"invalid_lunge_speed:{where}")
        elif lunge["forward_max"] < lunge["forward_min"]:
            errors.append(f"lunge_forward_max_below_min:{where}")
        elif lunge["forward_max"] > 4.0 or lunge["up"] > 1.5:  # BeastMoveDefinition.Lunge bounds
            errors.append(f"lunge_speed_above_bound:{where}")
    hit = move["hit"]
    if _fields(hit, f"{path}.hit", BEAST_HIT_FIELDS, errors):
        if not number_pair(hit["forward"]) or not hit["forward"][0] < hit["forward"][1]:
            errors.append(f"invalid_hit_forward:{where}")
        if not number_pair(hit["height"]) or not hit["height"][0] < hit["height"][1]:
            errors.append(f"invalid_hit_height:{where}")
        if not _rule(hit["half_width"], "positive"):
            errors.append(f"invalid_hit_half_width:{where}")
    knockback = move["knockback"]
    if _fields(knockback, f"{path}.knockback", BEAST_KNOCKBACK_FIELDS, errors):
        if not all(_rule(knockback[k], "non_negative") for k in ("strength", "lift")):
            errors.append(f"invalid_knockback:{where}")
    return errors


def check_beast_model(model: Any, entity_id: str) -> tuple[list[str], set[str]]:
    """(errors, bone names) of a schema-1 model file."""
    errors: list[str] = []
    present = MODEL_OPTIONAL_FIELDS & set(model) if isinstance(model, dict) else set()
    if not _fields(model, "", MODEL_FIELDS | present, errors):
        return errors, set()
    if "scale" in model and not _rule(model["scale"], "positive"):
        errors.append("invalid_scale")
    if model["schema"] != 1:
        errors.append(f"unsupported_schema:{model['schema']}")
    if model["id"] != entity_id:
        errors.append(f"id_mismatch:{model['id']}")
    texture = model["texture"]
    width = height = 0
    if _fields(texture, "texture", {"width", "height"}, errors):
        width, height = texture["width"], texture["height"]
        if not (is_int(width) and is_int(height) and width > 0 and height > 0):
            errors.append("invalid_texture_size")
            width = height = 0
    if not _rule(model["shadow_radius"], "non_negative"):
        errors.append("invalid_shadow_radius")
    names: list[str] = []
    bones = model["bones"]
    if not isinstance(bones, list) or not bones:
        errors.append("bones_empty")
        bones = []
    for position, bone in enumerate(bones):
        path = f"bones[{position}]"
        if not _fields(bone, path, BONE_FIELDS, errors):
            continue
        name, parent = bone["name"], bone["parent"]
        if not isinstance(name, str) or not NAME_PATTERN.match(name):
            errors.append(f"invalid_bone_name:{path}")
            continue
        if name in names:
            errors.append(f"duplicate_bone:{name}")
        if parent is not None and parent not in names:
            errors.append(f"parent_not_listed_before:{name}:{parent}")
        if not vec3(bone["pivot"]) or not vec3(bone["rotation"]):
            errors.append(f"invalid_bone_pose:{name}")
        for cube_index, cube in enumerate(bone["cubes"] if isinstance(bone["cubes"], list) else []):
            cube_path = f"{name}.cubes[{cube_index}]"
            if not _fields(cube, cube_path, CUBE_FIELDS, errors):
                continue
            uv = cube["uv"]
            if not (vec3(cube["origin"]) and vec3(cube["size"]) and is_number(cube["inflate"])
                    and isinstance(cube["mirror"], bool) and number_pair(uv)):
                errors.append(f"invalid_cube:{cube_path}")
            elif width and not (0 <= uv[0] < width and 0 <= uv[1] < height):
                errors.append(f"uv_outside_texture:{cube_path}")
        if not isinstance(bone["cubes"], list):
            errors.append(f"invalid_cubes:{name}")
        names.append(name)
    look = model["look"]
    if _fields(look, "look", {"bone", "max_yaw", "max_pitch"}, errors):
        if look["bone"] not in names:
            errors.append(f"look_bone_missing:{look['bone']}")
        if not (_rule(look["max_yaw"], "non_negative") and _rule(look["max_pitch"], "non_negative")):
            errors.append("invalid_look_limits")
    return errors, set(names)


def check_beast_animations(animations: Any, entity_id: str, bones: set[str], data: Any,
                           looping: tuple[str, ...] = RESERVED_CLIPS) -> list[str]:
    """Schema-1 clips plus the cross-file rules ``BeastAnimationFile.check`` enforces: the
    ``looping`` clips exist and loop, and ``data`` (a beast's server data, or None) names the rest."""
    errors: list[str] = []
    if not _fields(animations, "", ANIMATION_FIELDS, errors):
        return errors
    if animations["schema"] != 1:
        errors.append(f"unsupported_schema:{animations['schema']}")
    if animations["id"] != entity_id:
        errors.append(f"id_mismatch:{animations['id']}")
    clips = animations["clips"]
    if not isinstance(clips, dict):
        return errors + ["clips_not_an_object"]
    for name, clip in clips.items():
        if not NAME_PATTERN.match(name):
            errors.append(f"invalid_clip_name:{name}")
        if not _fields(clip, f"clips.{name}", CLIP_FIELDS, errors):
            continue
        length = clip["length"]
        if not _rule(length, "positive") or not isinstance(clip["loop"], bool):
            errors.append(f"invalid_clip_header:{name}")
            continue
        for channel_index, channel in enumerate(clip["channels"] if isinstance(clip["channels"], list) else []):
            path = f"clips.{name}.channels[{channel_index}]"
            if not _fields(channel, path, CHANNEL_FIELDS, errors):
                continue
            if channel["bone"] not in bones:
                errors.append(f"channel_bone_missing:{name}:{channel['bone']}")
            if channel["target"] not in CHANNEL_TARGETS:
                errors.append(f"invalid_channel_target:{name}:{channel['target']}")
            keyframes = channel["keyframes"]
            if not isinstance(keyframes, list) or not keyframes:
                errors.append(f"channel_without_keyframes:{path}")
                continue
            for keyframe in keyframes:
                if not _fields(keyframe, f"{path}.keyframe", KEYFRAME_FIELDS, errors):
                    break
                if (not is_number(keyframe["time"]) or not 0 <= keyframe["time"] <= length + CLIP_LENGTH_TOLERANCE
                        or not vec3(keyframe["value"]) or keyframe["interp"] not in INTERPOLATIONS):
                    errors.append(f"invalid_keyframe:{path}")
                    break

    def require(name: str, loop: bool) -> dict | None:
        clip = clips.get(name)
        if not isinstance(clip, dict):
            errors.append(f"missing_clip:{name}")
            return None
        if clip.get("loop") is not loop:
            errors.append(f"clip_loop_must_be_{str(loop).lower()}:{name}")
        return clip

    for name in looping:
        require(name, True)
    if isinstance(data, dict):
        stagger = data.get("stagger", {}).get("animation") if isinstance(data.get("stagger"), dict) else None
        if isinstance(stagger, str):
            require(stagger, False)
        for move in data.get("moves", []) if isinstance(data.get("moves"), list) else []:
            if not isinstance(move, dict) or not isinstance(move.get("animation"), str):
                continue
            clip = require(move["animation"], False)
            total = move.get("total_ticks")
            if clip is not None and is_int(total) and is_number(clip.get("length")):
                expected = total / TICKS_PER_SECOND
                if abs(clip["length"] - expected) > CLIP_LENGTH_TOLERANCE:
                    errors.append(f"move_clip_length:{move['animation']}:{clip['length']}!={expected}")
    return errors


def yaml_block(text: str, key: str) -> str:
    """The indented lines under a top-level ``key:`` of a block-style YAML file ('' when absent)."""
    lines = text.splitlines()
    for start, line in enumerate(lines):
        if line.rstrip() == f"{key}:":
            block = []
            for following in lines[start + 1:]:
                if following and not following[0].isspace() and not following.startswith("#"):
                    break
                block.append(following)
            return "\n".join(block)
    return ""


def yaml_scalar(block: str, key: str) -> str | None:
    """The first ``key: value`` scalar in ``block`` (quotes stripped)."""
    match = re.search(rf"^\s*{re.escape(key)}:[ \t]*(\S[^\n]*?)\s*$", block, re.MULTILINE)
    return match.group(1).strip("\"'") if match else None


def check_beast_contract(contract: str, entity_id: str, data: Any, lang: dict[str, dict]) -> list[str]:
    """The contract names what the data file and language files hold, without repeating numbers."""
    errors: list[str] = []
    namespace, name = split_id(entity_id)
    entity = yaml_block(contract, "entity")
    if yaml_scalar(entity, "resource_location") != entity_id:
        errors.append("contract_resource_location")
    if yaml_scalar(entity, "temperament") not in {"hostile", "neutral", "passive", "boss"}:
        errors.append("contract_temperament")
    if yaml_scalar(yaml_block(contract, "data_sources"), "server_data") != f"data/{namespace}/beast/{name}.json":
        errors.append("contract_server_data_path")
    for locale in ("en_us", "zh_cn"):
        expected = lang.get(locale, {}).get(f"entity.{namespace}.{name}")
        if yaml_scalar(entity, locale) != expected:
            errors.append(f"contract_display_name_differs_from_lang:{locale}")
    attributes = yaml_block(contract, "attributes")
    if not isinstance(data, dict):
        return errors
    if isinstance(data.get("attributes"), dict):
        listed = set(re.findall(r"^\s*-\s+([a-z_]+)\s*$", attributes, re.MULTILINE))
        if listed != set(data["attributes"]):
            errors.append(f"contract_attribute_names:{sorted(listed ^ set(data['attributes']))}")
        if re.search(r"^\s*(max_health|movement_speed|follow_range|attack_damage):\s*[-\d.]", attributes, re.MULTILINE):
            errors.append("contract_repeats_tuned_attribute_values")
    behavior = yaml_block(contract, "behavior")
    moves = [m for m in data.get("moves", []) if isinstance(m, dict)] if isinstance(data.get("moves"), list) else []
    listed_moves = set(re.findall(r"^\s*-\s+id:\s*([a-z0-9_.-]+:[a-z0-9_./-]+)\s*$", behavior, re.MULTILINE))
    if listed_moves != {m.get("id") for m in moves}:
        errors.append("contract_move_ids_differ_from_data")
    behavior_clips = set(re.findall(r"^\s*animation:\s*([a-z0-9_]+)\s*$", behavior, re.MULTILINE))
    stagger = data.get("stagger", {}).get("animation") if isinstance(data.get("stagger"), dict) else None
    if behavior_clips != {m.get("animation") for m in moves} | {stagger}:
        errors.append("contract_move_clips_differ_from_data")
    rendering_clips = set(re.findall(r"^\s*-\s+id:\s*([a-z0-9_]+)\s*$", yaml_block(contract, "rendering"), re.MULTILINE))
    needed = set(RESERVED_CLIPS) | {m.get("animation") for m in moves} | {stagger}
    if not needed <= rendering_clips:
        errors.append(f"contract_rendering_clips_missing:{sorted(needed - rendering_clips)}")
    if yaml_scalar(yaml_block(contract, "acceptance"), "human_verdict_status") is None:
        errors.append("contract_missing_human_verdict_status")
    return errors


def scan_sources(paths: list[Path], needles: list[str], root: Path, code: str) -> list[str]:
    """``code:<file>:<needle>`` for every needle found in a source file."""
    errors = []
    for path in paths:
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        for needle in needles:
            if needle in text:
                errors.append(f"{code}:{path.relative_to(root)}:{needle}")
    return errors


def float_literal(text: str, pattern: str) -> float | None:
    match = re.search(pattern, text)
    return float(match.group(1)) if match else None


def registration_block(text: str, name: str) -> str:
    """The ``ENTITY_TYPES.register("<name>", ...)`` call up to its ``.build(``."""
    start = text.find(f'ENTITY_TYPES.register("{name}"')
    if start < 0:
        return ""
    end = text.find(".build(", start)
    return text[start:end if end > 0 else len(text)]


def check_entity_registration(root: Path, entity_id: str, contract: str) -> list[str]:
    """``ModEntities`` against the contract's pinned size, eye height, tracking range and category."""
    _, name = split_id(entity_id)
    const = constant_name(name)
    found: list[str] = []
    entities_text = require_text(root / JAVA_ROOT / "entity/ModEntities.java",
                                 [f"EntityType<{class_name(name)}>> {const} ="], found)
    block = registration_block(entities_text, name)
    physical = yaml_block(contract, "physical")
    if not block:
        found.append(f"entity_type_not_registered:{name}")
        return found
    for field, pattern in (("width", r"\.sized\(([\d.]+)F,"), ("height", r"\.sized\([\d.]+F,\s*([\d.]+)F\)"),
                           ("eye_height", r"\.eyeHeight\(([\d.]+)F\)"),
                           ("client_tracking_range", r"\.clientTrackingRange\((\d+)\)")):
        declared, registered = yaml_scalar(physical, field), float_literal(block, pattern)
        if declared is None or registered is None or float(declared) != registered:
            found.append(f"registration_differs_from_contract:{field}:{registered}!={declared}")
    category = yaml_scalar(yaml_block(contract, "entity"), "mob_category")
    if f"MobCategory.{(category or '').upper()}" not in block:
        found.append(f"registration_mob_category_differs_from_contract:{category}")
    return found


def check_spawn_egg(root: Path, entity_id: str, contract: str) -> list[str]:
    """The spawn egg item, its creative-tab entry and its colours against the contract."""
    _, name = split_id(entity_id)
    const = constant_name(name)
    found: list[str] = []
    items_text = require_text(root / JAVA_ROOT / "item/ModItems.java",
                              [f"{const}_SPAWN_EGG", f"ModEntities.{const},", f"output.accept({const}_SPAWN_EGG.get())"],
                              found)
    egg_block = items_text[items_text.find(f"{const}_SPAWN_EGG ="):][:400] if items_text else ""
    for field in ("primary_color", "secondary_color"):
        color = yaml_scalar(yaml_block(contract, "items"), field)
        if not color or f"0x{color.lstrip('#').upper()}" not in egg_block:
            found.append(f"spawn_egg_{field}_differs_from_contract:{color}")
    return found


def check_no_natural_spawning(root: Path, entity_id: str, contract: str, kind: str) -> list[str]:
    """No spawn placement, biome modifier or spawn biome tag; the contract must say ``natural: false``."""
    namespace, name = split_id(entity_id)
    const = constant_name(name)
    resources = root / RESOURCE_ROOT
    found: list[str] = []
    if yaml_scalar(yaml_block(contract, "spawn"), "natural") != "false":
        found.append(f"{kind}_natural_spawning_not_supported_by_validator")
    events = root / JAVA_ROOT / "entity/ModEntityEvents.java"
    events_text = events.read_text(encoding="utf-8") if events.is_file() else ""
    placements = events_text[events_text.find("RegisterSpawnPlacementsEvent event"):]
    if f"ModEntities.{const}" in placements:
        found.append(f"{kind}_has_spawn_placement")
    for modifier in sorted((resources / "data").glob("*/neoforge/biome_modifier/*.json")):
        if entity_id in modifier.read_text(encoding="utf-8"):
            found.append(f"{kind}_has_biome_modifier:{modifier.relative_to(root)}")
    if (resources / f"data/{namespace}/tags/worldgen/biome/has_{name}.json").is_file():
        found.append(f"{kind}_has_spawn_biome_tag")
    return found


def check_entity_resources(root: Path, entity_id: str, found: list[str]) -> tuple[dict[str, dict], Any]:
    """Names in both languages, the spawn egg item model and the loot table; returns (lang, loot)."""
    namespace, name = split_id(entity_id)
    resources = root / RESOURCE_ROOT
    assets = resources / f"assets/{namespace}"
    lang = {locale: require_json(assets / f"lang/{locale}.json", found) for locale in ("en_us", "zh_cn")}
    for locale, table in lang.items():
        for key in (f"entity.{namespace}.{name}", f"item.{namespace}.{name}_spawn_egg"):
            value = table.get(key) if isinstance(table, dict) else None
            if not isinstance(value, str) or not value.strip():
                found.append(f"missing_translation:{locale}:{key}")
    egg_model = require_json(assets / f"models/item/{name}_spawn_egg.json", found)
    if egg_model and egg_model.get("parent") != "minecraft:item/template_spawn_egg":
        found.append("invalid_spawn_egg_model_parent")
    loot = require_json(resources / f"data/{namespace}/loot_table/entities/{name}.json", found)
    if loot and (loot.get("type") != "minecraft:entity" or not isinstance(loot.get("pools"), list)):
        found.append("invalid_loot_table")
    return lang, loot


def validate_beast_framework(root: Path, errors: list[str]) -> list[str]:
    """Beast-wide wiring; returns the beast ids the data index lists."""
    java_root = root / JAVA_ROOT
    index = require_json(root / RESOURCE_ROOT / BEAST_INDEX, errors)
    if not index:
        return []
    index_errors, beasts = check_beast_index(index)
    errors.extend(f"beast_index:{e}" for e in index_errors)
    if not beasts:
        return beasts

    require_text(java_root / "MyVillageMod.java", [".then(BeastCommands.command())"], errors)
    require_text(
        java_root / "entity/beast/BeastCommands.java",
        ['Commands.literal("move")', 'Commands.literal("status")', 'Commands.literal("debug")', "hasPermission(2)"],
        errors,
    )
    require_text(java_root / "entity/beast/BeastEntity.java", ["implements StaggerResistant"], errors)
    require_text(java_root / "combat/runtime/StaggerResistant.java", ["boolean resistsStagger()"], errors)
    require_text(java_root / "combat/runtime/CombatReactionService.java", ["StaggerResistant.resists("], errors)

    # Common beast code never loads client classes.
    common = sorted((java_root / "entity").rglob("*.java"))
    errors.extend(scan_sources(common, ["import net.minecraft.client", "import com.example.myvillage.client"],
                               root, "client_import_in_common_source"))
    # The combat package resolves stagger resistance through StaggerResistant and names no beast.
    combat = sorted((java_root / "combat").rglob("*.java")) + sorted((java_root / "client/combat").rglob("*.java"))
    needles = ["entity.beast", "BeastEntity"]
    for entity_id in beasts:
        _, name = split_id(entity_id)
        needles += [name, class_name(name)]
    errors.extend(scan_sources(combat, needles, root, "beast_name_in_combat_source"))
    return beasts


def validate_beast(root: Path, entity_id: str, errors: list[str]) -> dict[str, Any]:
    """One beast: data, client files, contract, registration, resources, spawning."""
    namespace, name = split_id(entity_id)
    const = constant_name(name)
    java_root = root / JAVA_ROOT
    resources = root / RESOURCE_ROOT
    assets = resources / f"assets/{namespace}"
    found: list[str] = []

    def add(prefix: str, problems: list[str]) -> None:
        found.extend(f"{prefix}:{problem}" for problem in problems)

    data_path = resources / f"data/{namespace}/beast/{name}.json"
    data = require_json(data_path, found)
    if data:
        add("beast_data", check_beast_data(data, entity_id))
    model = require_json(assets / f"beast/{name}_model.json", found)
    bones: set[str] = set()
    if model:
        model_errors, bones = check_beast_model(model, entity_id)
        add("beast_model", model_errors)
    animations = require_json(assets / f"beast/{name}_animations.json", found)
    if animations:
        add("beast_animations", check_beast_animations(animations, entity_id, bones, data))

    texture_size = None
    if isinstance(model, dict) and isinstance(model.get("texture"), dict):
        texture_size = (model["texture"].get("width"), model["texture"].get("height"))
    texture_report: dict[str, Any] = {}
    for kind, path in (("texture", assets / f"textures/entity/{name}/{name}.png"),
                       ("glow", assets / f"textures/entity/{name}/{name}_eyes.png")):
        if not path.is_file():
            found.append(f"missing_file:{path}")
            continue
        try:
            width, height, rgba = read_png_rgba(path)
        except ValueError as exc:
            found.append(str(exc))
            continue
        texture_report[kind] = [width, height]
        if texture_size and (width, height) != texture_size:
            found.append(f"beast_{kind}_size_differs_from_model:{width}x{height}")
        if kind == "glow":
            alphas = rgba[3::4]
            if not any(alphas) or all(alphas):
                found.append("beast_glow_must_be_partly_transparent")

    beastgen = root / "tools/beastgen"
    if not (beastgen / f"defs/{name}.py").is_file():
        found.append(f"missing_file:{beastgen / f'defs/{name}.py'}")
    build_text = (beastgen / "build.py").read_text(encoding="utf-8") if (beastgen / "build.py").is_file() else ""
    if not re.search(rf"DEFINITIONS\s*=\s*\([^)]*\"{re.escape(name)}\"", build_text):
        found.append(f"beastgen_definition_not_registered:{name}")

    lang, loot = check_entity_resources(root, entity_id, found)

    contract_path = root / f"genops/contracts/entities/{name}.yaml"
    contract = require_text(contract_path, [], found)
    if contract:
        add("beast_contract", check_beast_contract(contract, entity_id, data, lang))
        if "pools: []" in yaml_block(contract, "loot") and loot and loot.get("pools") != []:
            found.append("loot_table_not_empty_as_contracted")

    # Java registration, compared with the contract where the contract pins a value.
    found.extend(check_entity_registration(root, entity_id, contract))
    java_class = yaml_scalar(yaml_block(contract, "entity"), "java_class") or ""
    class_path = root / "src/main/java" / (java_class.replace(".", "/") + ".java")
    require_text(class_path, ["extends BeastEntity", f'"{name}"'], found)
    require_text(java_root / "entity/ModEntityEvents.java",
                 [f"event.put(ModEntities.{const}.get()", "BeastEntity.createAttributes("], found)
    found.extend(check_spawn_egg(root, entity_id, contract))
    require_text(java_root / "client/MyVillageClient.java",
                 ["value = Dist.CLIENT", f"BeastRenderer.register(event, ModEntities.{const})",
                  f"BeastRenderer.registerLayer(event, ModEntities.{const}.getId())"], found)

    # No natural spawning unless the contract says so (and then this validator must learn it).
    found.extend(check_no_natural_spawning(root, entity_id, contract, "beast"))

    errors.extend(found)
    moves = data.get("moves", []) if isinstance(data, dict) and isinstance(data.get("moves"), list) else []
    return {
        "moves": [m.get("id") for m in moves if isinstance(m, dict)],
        "clips": sorted(animations.get("clips", {})) if isinstance(animations, dict) and isinstance(animations.get("clips"), dict) else [],
        "bones": len(bones),
        "textures": texture_report,
        "errors": len(found),
    }


def npc_ids(root: Path) -> list[str]:
    """Entity ids of the NPCs ``tools/npcgen`` builds (its ``DEFINITIONS``), in the mod's namespace."""
    build = root / NPCGEN_BUILD
    if not build.is_file():
        return []
    match = re.search(r"^DEFINITIONS\s*=\s*\(([^)]*)\)", build.read_text(encoding="utf-8"), re.MULTILINE)
    return [f"myvillage:{name}" for name in re.findall(r"\"([a-z0-9_]+)\"", match.group(1))] if match else []


def check_npc_contract(contract: str, entity_id: str, lang: dict[str, dict]) -> list[str]:
    """The contract names the NPC, its generated files and its clips, and leaves the verdict open."""
    errors: list[str] = []
    namespace, name = split_id(entity_id)
    entity = yaml_block(contract, "entity")
    if yaml_scalar(entity, "resource_location") != entity_id:
        errors.append("contract_resource_location")
    if yaml_scalar(entity, "kind") != "npc":
        errors.append("contract_kind_must_be_npc")
    if yaml_scalar(entity, "temperament") not in {"undecided", "friendly", "neutral", "hostile"}:
        errors.append("contract_temperament")
    for locale in ("en_us", "zh_cn"):
        expected = lang.get(locale, {}).get(f"entity.{namespace}.{name}") if isinstance(lang.get(locale), dict) else None
        if yaml_scalar(entity, locale) != expected:
            errors.append(f"contract_display_name_differs_from_lang:{locale}")
    sources = yaml_block(contract, "data_sources")
    for key, expected in (("model", f"assets/{namespace}/npc/{name}_model.json"),
                          ("animations", f"assets/{namespace}/npc/{name}_animations.json"),
                          ("texture", f"assets/{namespace}/textures/entity/{name}/{name}.png"),
                          ("art_generator", f"tools/npcgen/defs/{name}.py")):
        if yaml_scalar(sources, key) != expected:
            errors.append(f"contract_data_source:{key}")
    rendering_clips = set(re.findall(r"^\s*-\s+id:\s*([a-z0-9_]+)\s*$", yaml_block(contract, "rendering"), re.MULTILINE))
    if not set(NPC_CLIPS) <= rendering_clips:
        errors.append(f"contract_rendering_clips_missing:{sorted(set(NPC_CLIPS) - rendering_clips)}")
    if yaml_scalar(yaml_block(contract, "acceptance"), "human_verdict_status") is None:
        errors.append("contract_missing_human_verdict_status")
    return errors


def validate_npc(root: Path, entity_id: str, errors: list[str]) -> dict[str, Any]:
    """One humanoid NPC: generated client files, contract, registration, resources, spawning."""
    namespace, name = split_id(entity_id)
    const = constant_name(name)
    java_root = root / JAVA_ROOT
    assets = root / RESOURCE_ROOT / f"assets/{namespace}"
    found: list[str] = []

    model = require_json(assets / f"npc/{name}_model.json", found)
    bones: set[str] = set()
    if model:
        model_errors, bones = check_beast_model(model, entity_id)
        found.extend(f"npc_model:{problem}" for problem in model_errors)
    animations = require_json(assets / f"npc/{name}_animations.json", found)
    if animations:
        found.extend(f"npc_animations:{problem}"
                     for problem in check_beast_animations(animations, entity_id, bones, None, looping=NPC_CLIPS))

    texture_report: list[int] = []
    cutout_texels = 0
    texture_path = assets / f"textures/entity/{name}/{name}.png"
    if not texture_path.is_file():
        found.append(f"missing_file:{texture_path}")
    else:
        try:
            width, height, rgba = read_png_rgba(texture_path)
            texture_report = [width, height]
            atlas = model.get("texture") if isinstance(model, dict) else None
            if isinstance(atlas, dict) and (width, height) != (atlas.get("width"), atlas.get("height")):
                found.append(f"npc_texture_size_differs_from_model:{width}x{height}")
            alphas = rgba[3::4]
            if any(alpha not in (0, 255) for alpha in alphas):
                found.append("npc_texture_alpha_must_be_binary")  # the cut-out render type has no blending
            cutout_texels = sum(1 for alpha in alphas if alpha == 0)
        except ValueError as exc:
            found.append(str(exc))

    if not (root / f"tools/npcgen/defs/{name}.py").is_file():
        found.append(f"missing_file:{root / f'tools/npcgen/defs/{name}.py'}")

    lang, loot = check_entity_resources(root, entity_id, found)
    contract = require_text(root / f"genops/contracts/entities/{name}.yaml", [], found)
    if contract:
        found.extend(f"npc_contract:{problem}" for problem in check_npc_contract(contract, entity_id, lang))
        if "pools: []" in yaml_block(contract, "loot") and loot and loot.get("pools") != []:
            found.append("loot_table_not_empty_as_contracted")

    found.extend(check_entity_registration(root, entity_id, contract))
    java_class = yaml_scalar(yaml_block(contract, "entity"), "java_class") or ""
    class_path = root / "src/main/java" / (java_class.replace(".", "/") + ".java")
    require_text(class_path, ["extends NpcEntity", f'"{name}"'], found)
    require_text(java_root / "entity/ModEntityEvents.java", [f"event.put(ModEntities.{const}.get()"], found)
    found.extend(check_spawn_egg(root, entity_id, contract))
    require_text(java_root / "client/MyVillageClient.java",
                 ["value = Dist.CLIENT", f"NpcRenderer.register(event, ModEntities.{const})",
                  f"NpcRenderer.registerLayer(event, ModEntities.{const}.getId())"], found)
    found.extend(check_no_natural_spawning(root, entity_id, contract, "npc"))

    errors.extend(found)
    return {
        "clips": sorted(animations.get("clips", {})) if isinstance(animations, dict) and isinstance(animations.get("clips"), dict) else [],
        "bones": len(bones),
        "scale": model.get("scale", 1.0) if isinstance(model, dict) else None,
        "texture": texture_report,
        "transparent_texels": cutout_texels,
        "errors": len(found),
    }


def validate(root: Path = ROOT) -> dict[str, Any]:
    errors: list[str] = []
    texture = validate_simple_fox(root, errors)
    beasts = validate_beast_framework(root, errors)
    beast_reports = {entity_id: validate_beast(root, entity_id, errors) for entity_id in beasts}
    npcs = npc_ids(root)
    if npcs and not beasts:
        # validate_beast_framework scans entity/** for client imports; without beasts, do it here.
        common = sorted((root / JAVA_ROOT / "entity").rglob("*.java"))
        errors.extend(scan_sources(common, ["import net.minecraft.client", "import com.example.myvillage.client"],
                                   root, "client_import_in_common_source"))
    npc_reports = {entity_id: validate_npc(root, entity_id, errors) for entity_id in npcs}
    return {
        "schema_version": 1,
        "entities": ["myvillage:simple_fox", *beasts, *npcs],
        "texture": texture,
        "beasts": beast_reports,
        "npcs": npc_reports,
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
