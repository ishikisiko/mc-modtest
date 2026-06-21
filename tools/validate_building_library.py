#!/usr/bin/env python3
"""Validate the exported building library NBT + mcfunction mod resources.

Usage:
    python tools/validate_building_library.py --style medieval_village

Checks the actual files that go into the jar (not the in-memory grids):
  - 10 structures per archetype exist and parse as gzipped structure NBT
  - DataVersion / size sanity, non-trivial block counts
  - entrance (door), windows (glass), interior function blocks present
  - no style-forbidden blocks, no block entity NBT, no entities
  - block ids exist in the 1.21.1 registry (docs/ai-kb/references)
  - gallery mcfunction places all structures with enough spacing
  - every structure has a single-place mcfunction

Writes reports/building_library_validation.json and exits non-zero on failure.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen.archetypes import ARCHETYPES, NEW_ARCHETYPE_COUNTS
from buildgen import export
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt, state_string
from buildgen.style import load_style

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ID = "myvillage"
RES = os.path.join(PROJECT_ROOT, "src", "main", "resources", "data", MOD_ID)
REGISTRY = os.path.join(PROJECT_ROOT, "docs", "ai-kb", "references", "blocks_121.json")
DATA_VERSION = 3955
MIN_SPACING = 20
MULTISTORY_ARCHETYPES = {"medium_shop", "big_house"}

FUNCTION_BLOCKS = ("crafting_table", "furnace", "smithing_table", "barrel", "anvil")


def load_registry() -> set:
    with open(REGISTRY, "r", encoding="utf-8") as f:
        data = json.load(f)
    ids = data.get("blocks", data) if isinstance(data, dict) else data
    return {b if ":" in b else f"minecraft:{b}" for b in ids}


def validate_structure(path: str, style, registry: set, modset,
                       expect_multistory: bool = False) -> dict:
    errors, warnings = [], []
    name = os.path.splitext(os.path.basename(path))[0]
    try:
        _, root = read_gzipped_nbt(path)
    except Exception as exc:  # parse failure is a hard error
        return {"name": name, "passed": False,
                "errors": [f"nbt_parse: {exc}"], "warnings": []}

    if root.get("DataVersion") != DATA_VERSION:
        errors.append(f"data_version: {root.get('DataVersion')} != {DATA_VERSION}")
    size = root.get("size", [])
    if len(size) != 3 or any(v <= 0 or v > 64 for v in size):
        errors.append(f"bad_size: {size}")
    if root.get("entities"):
        errors.append(f"entities_present: {len(root['entities'])}")

    palette = [state_string(p) for p in root.get("palette", [])]
    blocks = root.get("blocks", [])
    if len(blocks) < 100:
        errors.append(f"too_few_blocks: {len(blocks)}")

    forbidden = sorted({s for s in palette if style.is_forbidden(s)})
    if forbidden:
        errors.append(f"forbidden_blocks: {forbidden}")
    unknown = sorted({block for s in palette
                      for block in [s.split("[", 1)[0]]
                      if block.startswith("minecraft:") and block not in registry})
    if unknown:
        errors.append(f"unknown_block_ids: {unknown}")
    errors.extend(modset.palette_block_errors(palette))

    if not any("_door" in s for s in palette):
        errors.append("no_entrance: no door in palette")
    glass_idx = {i for i, s in enumerate(palette) if s.startswith("minecraft:glass")}
    glass_count = sum(1 for b in blocks if b["state"] in glass_idx)
    if glass_count < style.prop("window_min_count"):
        errors.append(f"too_few_windows: {glass_count}")

    fn_idx = {i for i, s in enumerate(palette)
              if any(f in s for f in FUNCTION_BLOCKS)}
    fn_count = sum(1 for b in blocks if b["state"] in fn_idx)
    if fn_count < style.prop("interior_required_function_blocks"):
        errors.append(f"underfurnished: {fn_count} function blocks")

    with_nbt = sum(1 for b in blocks if "nbt" in b)
    if with_nbt:
        errors.append(f"block_entity_nbt: {with_nbt} blocks carry nbt")

    if not any("_stairs" in s or "_slab" in s for s in palette):
        warnings.append("no_roof_blocks: no stairs/slabs in palette")
    if expect_multistory:
        if len(size) == 3 and size[1] < 12:
            errors.append(f"multi_story_too_short: {size}")
        air_idx = {i for i, s in enumerate(palette) if s == "minecraft:air"}
        air_positions = {tuple(b["pos"]) for b in blocks if b["state"] in air_idx}
        aligned_air = any((x, y + 1, z) in air_positions
                          for x, y, z in air_positions if y >= 4)
        if not aligned_air:
            errors.append("multi_story_stair_opening_missing")

    return {"name": name, "passed": not errors, "errors": errors,
            "warnings": warnings, "size": size,
            "block_count": len(blocks), "palette_count": len(palette),
            "glass": glass_count, "function_blocks": fn_count}


def validate_functions(style_id: str, structure_names: list) -> list:
    errors = []
    gallery = os.path.join(RES, "function", "gallery", f"{style_id}.mcfunction")
    if not os.path.isfile(gallery):
        return [f"missing_gallery: {gallery}"]
    with open(gallery, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f if l.strip() and not l.startswith("#")]
    pat = re.compile(
        rf"^place template {MOD_ID}:(\w+) ~(-?\d+)? ~(-?\d+)? ~(-?\d+)?$")
    placed = {}
    for line in lines:
        m = pat.match(line)
        if not m:
            errors.append(f"gallery_bad_line: {line}")
            continue
        placed[m.group(1)] = (int(m.group(2) or 0), int(m.group(4) or 0))
    missing = set(structure_names) - set(placed)
    if missing:
        errors.append(f"gallery_missing_structures: {sorted(missing)}")
    coords = list(placed.values())
    for i, a in enumerate(coords):
        for b in coords[i + 1:]:
            if abs(a[0] - b[0]) < MIN_SPACING and abs(a[1] - b[1]) < MIN_SPACING:
                errors.append(f"gallery_spacing: {a} vs {b} closer than "
                              f"{MIN_SPACING}")
    for name in structure_names:
        p = os.path.join(RES, "function", "place", f"{name}.mcfunction")
        if not os.path.isfile(p):
            errors.append(f"missing_place_function: {name}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default="medieval_village")
    parser.add_argument("--count", type=int, default=10)
    parser.add_argument("--profile", default="full", choices=("vanilla", "full"),
                        help="modset profile: 'vanilla' forbids all mod ids, 'full' allows confirmed catalog ids")
    args = parser.parse_args()

    style = load_style(args.style)
    registry = load_registry()
    modset = load_modset(args.profile)
    struct_dir = os.path.join(RES, "structure")

    results = []
    names = []
    for archetype in ARCHETYPES:
        archetype_count = NEW_ARCHETYPE_COUNTS.get(archetype, args.count)
        for i in range(1, archetype_count + 1):
            name = f"{archetype}_{i:03d}"
            names.append(name)
            path = os.path.join(struct_dir, f"{name}.nbt")
            if not os.path.isfile(path):
                results.append({"name": name, "passed": False,
                                "errors": ["missing_file"], "warnings": []})
                continue
            results.append(validate_structure(
                path, style, registry, modset,
                archetype in MULTISTORY_ARCHETYPES))

    fn_errors = validate_functions(args.style, names)

    passed = sum(1 for r in results if r["passed"])
    failed = [r for r in results if not r["passed"]]
    for r in results:
        status = "OK  " if r["passed"] else "FAIL"
        extra = (f"size={r.get('size')} blocks={r.get('block_count')} "
                 f"glass={r.get('glass')} fn={r.get('function_blocks')}"
                 if r["passed"] else f"{r['errors']}")
        print(f"{status} {r['name']:24s} {extra}")
    for e in fn_errors:
        print(f"FAIL functions: {e}")

    report = {"style_id": args.style, "profile": args.profile,
              "total": len(results), "passed": passed,
              "failed": len(failed), "function_errors": fn_errors,
              "results": results}
    out = os.path.join(PROJECT_ROOT, "reports",
                       "building_library_validation.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    ok = passed == len(results) and not fn_errors
    print(f"\n{passed}/{len(results)} structures passed, "
          f"{len(fn_errors)} function errors")
    print(f"report: {export.repo_relpath(out)}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
