#!/usr/bin/env python3
"""Validate the first rideable flying-sword runtime and resource contract."""

from __future__ import annotations

import json
import re
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src" / "main" / "java" / "com" / "example" / "myvillage"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "myvillage"

REQUIRED_FILES = (
    ROOT / "genops" / "contracts" / "items" / "rideable_flying_sword.json",
    ROOT / "genops" / "contracts" / "entities" / "rideable_flying_sword.yaml",
    JAVA / "entity" / "RideableFlyingSwordEntity.java",
    JAVA / "item" / "RideableFlyingSwordItem.java",
    JAVA / "network" / "FlyingSwordInputPayload.java",
    JAVA / "network" / "FlyingSwordInputFlags.java",
    JAVA / "network" / "ModPayloads.java",
    JAVA / "client" / "FlyingSwordClientInput.java",
    JAVA / "client" / "entity" / "RideableFlyingSwordRenderer.java",
    ASSETS / "models" / "item" / "rideable_flying_sword.json",
    ASSETS / "textures" / "item" / "rideable_flying_sword.png",
)

COMMON_FILES = (
    JAVA / "MyVillageMod.java",
    JAVA / "entity" / "ModEntities.java",
    JAVA / "entity" / "RideableFlyingSwordEntity.java",
    JAVA / "item" / "ModItems.java",
    JAVA / "item" / "RideableFlyingSwordItem.java",
    JAVA / "network" / "FlyingSwordInputPayload.java",
    JAVA / "network" / "FlyingSwordInputFlags.java",
    JAVA / "network" / "ModPayloads.java",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
        raise ValueError("not_png")
    return struct.unpack(">II", data[16:24])


def require(text: str, needle: str, label: str, errors: list[str]) -> None:
    if needle not in text:
        errors.append(f"missing:{label}:{needle}")


def validate() -> list[str]:
    errors: list[str] = []
    for path in REQUIRED_FILES:
        if not path.exists():
            errors.append(f"missing_file:{path.relative_to(ROOT)}")
    if errors:
        return errors

    entities = read(JAVA / "entity" / "ModEntities.java")
    items = read(JAVA / "item" / "ModItems.java")
    entity = read(JAVA / "entity" / "RideableFlyingSwordEntity.java")
    item = read(JAVA / "item" / "RideableFlyingSwordItem.java")
    payload = read(JAVA / "network" / "FlyingSwordInputPayload.java")
    handler = read(JAVA / "network" / "ModPayloads.java")
    client_input = read(JAVA / "client" / "FlyingSwordClientInput.java")
    renderer = read(JAVA / "client" / "entity" / "RideableFlyingSwordRenderer.java")

    require(entities, 'register("rideable_flying_sword"', "entity_registration", errors)
    require(entities, ".noSave()", "transient_entity", errors)
    require(items, 'ITEMS.registerItem("rideable_flying_sword"', "item_registration", errors)
    require(items, "output.accept(RIDEABLE_FLYING_SWORD.get())", "creative_tab", errors)
    require(entity, "move(MoverType.SELF", "collision_movement", errors)
    require(entity, "setNoGravity(true)", "no_gravity", errors)
    require(entity, "resetFallDistance()", "fall_distance", errors)
    require(entity, "INPUT_TIMEOUT_TICKS", "input_timeout", errors)
    require(item, "noBlockCollision", "collision_free_spawn", errors)
    if "getControllingPassenger(" in entity:
        errors.append("client_vehicle_coordinate_path_enabled:getControllingPassenger")

    record_match = re.search(r"record\s+FlyingSwordInputPayload\s*\(([^)]*)\)", payload)
    if record_match is None or record_match.group(1).strip() != "byte flags":
        errors.append("payload_shape_must_be_single_byte_flags")
    require(payload, "ALL_FLAGS", "payload_known_mask", errors)
    require(handler, "player.getVehicle()", "server_derives_vehicle", errors)
    require(handler, "sword.isOwnedBy(player)", "server_validates_owner", errors)

    require(client_input, "MovementInputUpdateEvent", "movement_input_event", errors)
    require(client_input, "shiftKeyDown = false", "shift_dismount_suppression", errors)
    require(client_input, "PacketDistributor.sendToServer", "key_payload_send", errors)
    require(renderer, "ItemRenderer", "item_model_renderer", errors)
    require(renderer, "ItemDisplayContext.FIXED", "fixed_item_transform", errors)

    for path in COMMON_FILES:
        text = read(path)
        if "import net.minecraft.client" in text or "import com.example.myvillage.client" in text:
            errors.append(f"client_import_in_common:{path.relative_to(ROOT)}")

    model = json.loads(read(ASSETS / "models" / "item" / "rideable_flying_sword.json"))
    if model.get("textures", {}).get("layer0") != "myvillage:item/rideable_flying_sword":
        errors.append("item_model_texture_reference")
    try:
        if png_size(ASSETS / "textures" / "item" / "rideable_flying_sword.png") != (32, 32):
            errors.append("placeholder_texture_size")
    except ValueError as exc:
        errors.append(str(exc))

    for locale in ("en_us", "zh_cn"):
        lang = json.loads(read(ASSETS / "lang" / f"{locale}.json"))
        for key in (
            "item.myvillage.rideable_flying_sword",
            "entity.myvillage.rideable_flying_sword",
        ):
            if key not in lang:
                errors.append(f"missing_lang:{locale}:{key}")

    return errors


def main() -> int:
    errors = validate()
    if errors:
        print("\n".join(errors))
        return 1
    print("rideable flying sword validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
