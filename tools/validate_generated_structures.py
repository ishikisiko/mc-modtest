#!/usr/bin/env python3
"""Validate generated structure NBT files that will be packed into the mod jar."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.nbtread import read_gzipped_nbt, state_string  # noqa: E402

ROOF_MARKERS = ("_stairs", "_slab", "spruce_planks", "dark_oak_planks")
KEY_BLOCK_MARKERS = ("_stairs", "_slab", "_log", "_wood", "_planks")
HOUSE_FUNCTION_BLOCKS = ("crafting_table", "furnace", "barrel")
BLACKSMITH_FUNCTION_BLOCKS = ("furnace", "campfire", "smoker", "blast_furnace")
SHOP_FUNCTION_BLOCKS = ("crafting_table", "barrel")
CIVIC_TAVERN_MARKERS = ("brewing_stand", "barrel")
CIVIC_MANOR_MARKERS = ("bell", "lectern")
MULTISTORY_NAMES = ("medium_shop", "big_house", "tavern", "lord_manor")


def is_air(state: str) -> bool:
    return state == "minecraft:air"


def is_roof_like(state: str) -> bool:
    return any(marker in state for marker in ROOF_MARKERS)


def has_marker(states: Iterable[str], markers: tuple[str, ...]) -> bool:
    return any(any(marker in state for marker in markers) for state in states)


def validate_gable_heuristic(name: str, palette: list[str], blocks: list[dict]) -> list[str]:
    errors: list[str] = []
    by_pos = {tuple(block["pos"]): palette[block["state"]] for block in blocks}
    stair_blocks = [
        (tuple(block["pos"]), palette[block["state"]])
        for block in blocks
        if "_stairs" in palette[block["state"]]
    ]
    if not stair_blocks:
        return errors

    facings = {state.split("facing=", 1)[1].split(",", 1)[0].split("]", 1)[0] for _, state in stair_blocks if "facing=" in state}
    checks: list[tuple[str, int, int]] = []
    xs = [pos[0] for pos, _ in stair_blocks]
    zs = [pos[2] for pos, _ in stair_blocks]
    if {"north", "south"} & facings:
        checks.append(("x", min(xs) + 1, max(xs) - 1))
    if {"east", "west"} & facings:
        checks.append(("z", min(zs) + 1, max(zs) - 1))

    min_roof_y = min(pos[1] for pos, _ in stair_blocks)
    for axis, low_plane, high_plane in checks:
        for side, plane in (("low", low_plane), ("high", high_plane)):
            direction = 1 if side == "low" else -1
            candidate_planes = [plane + direction * offset for offset in range(3)]
            sealed = 0
            for pos, state in by_pos.items():
                if is_air(state) or is_roof_like(state) or pos[1] < min_roof_y:
                    continue
                coord = pos[0] if axis == "x" else pos[2]
                if coord in candidate_planes:
                    sealed += 1
            if sealed == 0:
                errors.append(
                    f"{name}: possible open gable on {axis} near {plane}")
    return errors


def validate_file(path: Path, root_dir: Path) -> dict:
    rel = path.relative_to(root_dir).as_posix()
    errors: list[str] = []
    warnings: list[str] = []

    try:
        _, root = read_gzipped_nbt(str(path))
    except Exception as exc:
        return {"path": rel, "passed": False, "errors": [f"nbt_parse: {exc}"], "warnings": []}

    palette = [state_string(entry) for entry in root.get("palette", [])]
    blocks = root.get("blocks", [])
    if not palette:
        errors.append("palette_empty")
    if not blocks:
        errors.append("blocks_empty")

    size = root.get("size", [])
    if len(size) != 3 or any(not isinstance(v, int) or v <= 0 for v in size):
        errors.append(f"bad_size: {size}")

    non_air_blocks = [block for block in blocks if block["state"] < len(palette) and not is_air(palette[block["state"]])]
    states_present = {palette[block["state"]] for block in non_air_blocks}
    roof_blocks = [block for block in non_air_blocks if is_roof_like(palette[block["state"]]) and block["pos"][1] >= max(1, size[1] // 2)]
    top_layers = set(range(max(0, size[1] - 3), size[1])) if len(size) == 3 else set()
    top_non_air = [block for block in non_air_blocks if block["pos"][1] in top_layers]

    if not non_air_blocks:
        errors.append("non_air_blocks_empty")
    if not roof_blocks:
        errors.append("roof_blocks_missing")
    if not top_non_air:
        errors.append("top_layers_empty")
    if not has_marker(states_present, KEY_BLOCK_MARKERS):
        errors.append("key_building_blocks_missing")

    building_name = Path(rel).stem
    block_state_counts = Counter(palette[block["state"]].split("[", 1)[0] for block in non_air_blocks)
    if building_name.startswith("tavern_"):
        barrel_count = sum(count for state, count in block_state_counts.items() if "barrel" in state)
        has_brewing = has_marker(states_present, ("brewing_stand",))
        has_bed = has_marker(states_present, ("_bed",))
        if not (has_brewing or barrel_count >= 3):
            errors.append("civic_signature_tavern_hall_missing")
        if not has_bed:
            errors.append("civic_signature_tavern_bed_missing")
    elif building_name.startswith("lord_manor_"):
        if not has_marker(states_present, CIVIC_MANOR_MARKERS):
            errors.append("civic_signature_lord_manor_marker_missing")
        if not has_marker(states_present, ("banner",)):
            errors.append("civic_signature_lord_manor_banner_missing")
    elif building_name.startswith("blacksmith"):
        if not has_marker(states_present, BLACKSMITH_FUNCTION_BLOCKS):
            errors.append("blacksmith_function_block_missing")
    elif building_name.startswith(("small_house", "medium_house", "big_house")):
        missing = [marker for marker in HOUSE_FUNCTION_BLOCKS if not has_marker(states_present, (marker,))]
        if missing:
            errors.append(f"house_function_blocks_missing: {missing}")
    elif building_name.startswith(("small_shop", "medium_shop")):
        missing = [marker for marker in SHOP_FUNCTION_BLOCKS if not has_marker(states_present, (marker,))]
        if missing:
            errors.append(f"shop_function_blocks_missing: {missing}")
    elif building_name.startswith(("cultivation_house", "disciple_quarters")):
        missing = [marker for marker in HOUSE_FUNCTION_BLOCKS if not has_marker(states_present, (marker,))]
        if missing:
            errors.append(f"cultivation_housing_function_blocks_missing: {missing}")
    elif building_name.startswith(("cultivation_shop", "cultivation_market")):
        missing = [marker for marker in SHOP_FUNCTION_BLOCKS if not has_marker(states_present, (marker,))]
        if missing:
            errors.append(f"cultivation_shop_function_blocks_missing: {missing}")
    elif building_name.startswith("cultivation_inn"):
        if not has_marker(states_present, CIVIC_TAVERN_MARKERS):
            errors.append("cultivation_inn_signature_missing")
    elif building_name.startswith("town_shrine"):
        if not has_marker(states_present, CIVIC_MANOR_MARKERS):
            errors.append("town_shrine_civic_marker_missing")
    elif building_name.startswith(("sect_gate", "sect_main_hall", "scripture_pavilion", "alchemy_room")):
        if not has_marker(states_present, ("amethyst", "oxidized_copper", "quartz", "sea_lantern", "dark_prismarine", "calcite")):
            errors.append("sect_spirit_material_marker_missing")
    if building_name.startswith(MULTISTORY_NAMES + (
        "cultivation_inn", "town_shrine", "sect_main_hall",
        "scripture_pavilion", "disciple_quarters",
    )):
        if len(size) == 3 and size[1] < 12:
            errors.append(f"multi_story_too_short: {size}")
        air_positions = {
            tuple(block["pos"]) for block in blocks
            if block["state"] < len(palette) and is_air(palette[block["state"]])
        }
        aligned_air = any((x, y + 1, z) in air_positions
                          for x, y, z in air_positions if y >= 4)
        if not aligned_air:
            errors.append("multi_story_stair_opening_missing")

    errors.extend(validate_gable_heuristic(rel, palette, non_air_blocks))

    by_y = Counter(block["pos"][1] for block in non_air_blocks)
    state_counts = block_state_counts
    return {
        "path": rel,
        "passed": not errors,
        "errors": errors,
        "warnings": warnings,
        "size": size,
        "palette_count": len(palette),
        "block_count": len(non_air_blocks),
        "roof_block_count": len(roof_blocks),
        "top_layer_counts": {str(y): by_y.get(y, 0) for y in sorted(top_layers)},
        "key_state_counts": {
            key: value for key, value in sorted(state_counts.items())
            if any(marker in key for marker in KEY_BLOCK_MARKERS + HOUSE_FUNCTION_BLOCKS + BLACKSMITH_FUNCTION_BLOCKS + CIVIC_TAVERN_MARKERS + CIVIC_MANOR_MARKERS + ("bed", "banner"))
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("structure_dir", help="Directory containing generated .nbt structure files")
    parser.add_argument("--report", default="reports/generated_structure_validation.json")
    args = parser.parse_args()

    structure_dir = (REPO_ROOT / args.structure_dir).resolve() if not Path(args.structure_dir).is_absolute() else Path(args.structure_dir)
    report_path = (REPO_ROOT / args.report).resolve() if not Path(args.report).is_absolute() else Path(args.report)

    errors: list[str] = []
    if not structure_dir.is_dir():
        errors.append(f"missing_structure_dir: {structure_dir}")
        results = []
    else:
        files = sorted(structure_dir.rglob("*.nbt"))
        if not files:
            errors.append("no_structure_files")
        results = [validate_file(path, structure_dir) for path in files]

    failed = [result for result in results if not result["passed"]]
    errors.extend(f"{result['path']}: {message}" for result in failed for message in result["errors"])

    by_category: dict[str, int] = defaultdict(int)
    for result in results:
        name = Path(result["path"]).stem
        if name.startswith("small_house"):
            by_category["small_house"] += 1
        elif name.startswith("medium_house"):
            by_category["medium_house"] += 1
        elif name.startswith("blacksmith"):
            by_category["blacksmith"] += 1
        elif name.startswith("small_shop"):
            by_category["small_shop"] += 1
        elif name.startswith("medium_shop"):
            by_category["medium_shop"] += 1
        elif name.startswith("big_house"):
            by_category["big_house"] += 1
        elif name.startswith("chinese_courtyard"):
            by_category["chinese_courtyard"] += 1
        elif name.startswith("tavern"):
            by_category["tavern"] += 1
        elif name.startswith("lord_manor"):
            by_category["lord_manor"] += 1
        elif name.startswith("cultivation_house"):
            by_category["cultivation_house"] += 1
        elif name.startswith("cultivation_shop"):
            by_category["cultivation_shop"] += 1
        elif name.startswith("cultivation_inn"):
            by_category["cultivation_inn"] += 1
        elif name.startswith("cultivation_market"):
            by_category["cultivation_market"] += 1
        elif name.startswith("town_shrine"):
            by_category["town_shrine"] += 1
        elif name.startswith("cultivation_town"):
            by_category["cultivation_town"] += 1
        elif name.startswith("cultivation_sect"):
            by_category["cultivation_sect"] += 1
        elif name.startswith("sect_gate"):
            by_category["sect_gate"] += 1
        elif name.startswith("sect_main_hall"):
            by_category["sect_main_hall"] += 1
        elif name.startswith("scripture_pavilion"):
            by_category["scripture_pavilion"] += 1
        elif name.startswith("alchemy_room"):
            by_category["alchemy_room"] += 1
        elif name.startswith("disciple_quarters"):
            by_category["disciple_quarters"] += 1
        elif name in ("main_hall_review", "side_wing_review", "front_row_review"):
            by_category["chinese_review"] += 1
        elif name == "test_house_03":
            by_category["test_house_03"] += 1
        else:
            by_category["other"] += 1

    report = {
        "structure_dir": str(structure_dir.relative_to(REPO_ROOT)),
        "passed": not errors,
        "file_count": len(results),
        "category_counts": dict(sorted(by_category.items())),
        "errors": errors,
        "results": results,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    for result in results:
        status = "OK  " if result["passed"] else "FAIL"
        print(
            f"{status} {result['path']:48s} "
            f"size={result.get('size')} blocks={result.get('block_count')} roof={result.get('roof_block_count')}"
        )
        for error in result["errors"]:
            print(f"     - {error}")
    print(f"\nvalidated structures: {len(results)}")
    print(f"report: {report_path.relative_to(REPO_ROOT)}")
    if errors:
        print(f"errors: {len(errors)}")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
