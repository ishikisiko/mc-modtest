#!/usr/bin/env python3
"""Check the shipped two-half scripture furnishing resource contract, without a renderer."""
from __future__ import annotations

import argparse
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path("src/main/resources/assets/myvillage")
LOOT = Path("src/main/resources/data/myvillage/loot_table/blocks/scripture_shelf.json")
SHELF = "myvillage:scripture_shelf"
MODEL = "myvillage:block/scripture_shelf"


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []

    def error(code, detail):
        errors.append(f"{code}: {detail}")

    def read(relative):
        try:
            def unique(pairs):
                result = {}
                for key, value in pairs:
                    if key in result:
                        raise ValueError(f"duplicate key {key!r}")
                    result[key] = value
                return result
            value = json.loads((root / relative).read_text(encoding="utf-8"), object_pairs_hook=unique)
            if not isinstance(value, dict):
                raise ValueError("expected a JSON object")
            return value
        except (OSError, ValueError) as exc:
            error("SCRIPTURE_RESOURCE", f"{relative}: {exc}")
            return {}

    blockstate = read(ASSETS / "blockstates/scripture_shelf.json")
    expected = {f"facing={direction},half={half}": {"model": f"{MODEL}_{half}", "y": rotation}
                for half in ("lower", "upper")
                for direction, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
    if blockstate.get("variants") != expected or "multipart" in blockstate:
        error("SCRIPTURE_BLOCKSTATES", "all eight facing/half variants must select their own half with matching rotations")
    item = read(ASSETS / "models/item/scripture_shelf.json")
    if item.get("parent") != MODEL or "elements" in item:
        error("SCRIPTURE_ITEM_MODEL", "item must inherit the full furnishing, never one half or a cube")

    for suffix in ("", "_lower", "_upper"):
        relative = ASSETS / f"models/block/scripture_shelf{suffix}.json"
        model = read(relative)
        elements = model.get("elements")
        if model.get("parent") or not isinstance(elements, list) or not elements:
            error("SCRIPTURE_MODEL", f"{relative}: needs its own explicit furnishing geometry")
            continue
        textures = model.get("textures", {})
        if not isinstance(textures, dict) or not textures:
            error("SCRIPTURE_TEXTURE", f"{relative}: no own texture mapping")
            textures = {}
        for slot, texture in textures.items():
            if not isinstance(texture, str) or not texture.startswith("myvillage:block/scripture_shelf"):
                error("SCRIPTURE_TEXTURE", f"{relative}: {slot} must use a dedicated scripture shelf texture")
            else:
                path = ASSETS / "textures" / (texture.split(":", 1)[1] + ".png")
                try:
                    data = (root / path).read_bytes()
                    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                        raise ValueError("not a PNG")
                except (OSError, ValueError) as exc:
                    error("SCRIPTURE_TEXTURE", f"{path}: {exc}")
        bounds = []
        for index, element in enumerate(elements):
            try:
                if not isinstance(element, dict):
                    raise ValueError("element must be an object")
                start, end = element["from"], element["to"]
                if len(start) != 3 or len(end) != 3 or any(
                        not isinstance(v, (int, float)) or not math.isfinite(v) for v in [*start, *end]):
                    raise ValueError("invalid coordinates")
                if any(a >= b for a, b in zip(start, end)):
                    raise ValueError("non-positive box")
                points = list(itertools.product(*zip(start, end)))
                rotation = element.get("rotation")
                if rotation:
                    angle, axis = rotation["angle"], rotation["axis"]
                    if angle not in (-45, -22.5, 0, 22.5, 45) or axis not in "xyz" or len(axis) != 1:
                        raise ValueError("rotation is not vanilla-legal")
                    origin = rotation["origin"]
                    if len(origin) != 3 or rotation.get("rescale", False):
                        raise ValueError("invalid rotation origin or unsupported rescale")
                    a, b = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]
                    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
                    transformed = []
                    for point in points:
                        point = list(point)
                        u, v = point[a] - origin[a], point[b] - origin[b]
                        point[a], point[b] = origin[a] + c * u - s * v, origin[b] + s * u + c * v
                        transformed.append(point)
                    points = transformed
                limit = 32 if not suffix else 16
                if any(not -1e-5 <= p[1] <= limit + 1e-5 for p in points):
                    raise ValueError(f"geometry escapes its {limit}-unit vertical cell")
                bounds.extend(p[1] for p in points)
                faces = element.get("faces")
                if not isinstance(faces, dict) or not faces:
                    raise ValueError("no visible element faces")
                for face in faces.values():
                    if not isinstance(face, dict):
                        raise ValueError("face must be an object")
                    if face.get("texture", "").removeprefix("#") not in textures or not face.get("texture", "").startswith("#"):
                        error("SCRIPTURE_TEXTURE", f"{relative}: element {index} face has an unresolved texture")
            except (KeyError, TypeError, ValueError, AttributeError) as exc:
                error("SCRIPTURE_GEOMETRY", f"{relative}: element {index}: {exc}")
        if not suffix and bounds and max(bounds) <= 16:
            error("SCRIPTURE_GEOMETRY", "full item model must contain both vertical halves")

    loot = read(LOOT)
    pools = loot.get("pools", [])
    if loot.get("type") != "minecraft:block" or not isinstance(pools, list) or len(pools) != 1:
        error("SCRIPTURE_LOOT", "exactly one block-loot pool is required")
    elif isinstance(pools[0], dict):
        pool = pools[0]
        entries = pool.get("entries", [])
        conditions = pool.get("conditions", [])
        required = {"condition": "minecraft:block_state_property", "block": SHELF, "properties": {"half": "lower"}}
        if not isinstance(conditions, list) or required not in conditions:
            error("SCRIPTURE_LOOT", "item drop must require half=lower on the owning root")
        if (pool.get("rolls") != 1 or pool.get("bonus_rolls", 0) != 0
                or entries != [{"type": "minecraft:item", "name": SHELF}]
                or pool.get("functions") or loot.get("functions")):
            error("SCRIPTURE_LOOT", "one lower root must yield exactly one shelf item, without bonus/count functions")
    else:
        error("SCRIPTURE_LOOT", "invalid block-loot pool")
    return errors


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    errors = validate(args.root)
    for error in errors:
        print(error)
    if not errors:
        print("Scripture shelf resource contract: PASS")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
