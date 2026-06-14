#!/usr/bin/env python3
"""Validate generated Chinese courtyard compound resources.

This validator checks the generated report from the parcel-layer validator and
then verifies that the exported NBT and place/gallery functions exist and parse.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen.nbtread import read_gzipped_nbt, state_string
from buildgen.groups import get_group
from buildgen.style import load_style

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ID = "myvillage"
RES = os.path.join(PROJECT_ROOT, "src", "main", "resources", "data", MOD_ID)
DEFAULT_REPORT = os.path.join(PROJECT_ROOT, "reports", "compound_library_report.json")
OUT_REPORT = os.path.join(PROJECT_ROOT, "reports", "compound_library_validation.json")
DATA_VERSION = 3955


def validate_nbt(name: str, style, max_size: int = 64) -> dict:
    path = os.path.join(RES, "structure", f"{name}.nbt")
    errors = []
    if not os.path.isfile(path):
        return {"name": name, "passed": False, "errors": ["missing_file"]}
    try:
        _, root = read_gzipped_nbt(path)
    except Exception as exc:
        return {"name": name, "passed": False, "errors": [f"nbt_parse: {exc}"]}
    if root.get("DataVersion") != DATA_VERSION:
        errors.append(f"data_version: {root.get('DataVersion')} != {DATA_VERSION}")
    size = root.get("size", [])
    if len(size) != 3 or any(v <= 0 or v > max_size for v in size):
        errors.append(f"bad_size: {size}")
    palette = [state_string(p) for p in root.get("palette", [])]
    blocks = root.get("blocks", [])
    if len(blocks) < 500:
        errors.append(f"too_few_blocks: {len(blocks)}")
    forbidden = sorted({s for s in palette if style.is_forbidden(s)})
    if forbidden:
        errors.append(f"forbidden_blocks: {forbidden}")
    if not any("_stairs" in s or "_slab" in s for s in palette):
        errors.append("no_roof_blocks")
    if not any(s == "minecraft:water" or s.startswith("minecraft:water[") for s in palette):
        errors.append("missing_water_feature")
    planting = ("moss_block", "azalea_leaves", "flowering_azalea_leaves", "bamboo")
    if not any(any(p in s for p in planting) for s in palette):
        errors.append("missing_planting")
    by_pos = {tuple(block["pos"]): palette[block["state"]] for block in blocks}
    for pos, state in by_pos.items():
        if state == "minecraft:water" or state.startswith("minecraft:water["):
            if pos[1] != 0:
                errors.append(f"water_not_ground_layer: {state} at {pos}")
    for pos, state in by_pos.items():
        if not any(p in state for p in planting):
            continue
        if "bamboo" in state:
            if pos[1] < 1:
                errors.append(f"bamboo_not_plant_layer: {state} at {pos}")
        elif pos[1] != 1:
            errors.append(f"planting_not_plant_layer: {state} at {pos}")
    return {
        "name": name,
        "passed": not errors,
        "errors": errors,
        "size": size,
        "block_count": len(blocks),
        "palette_count": len(palette),
    }


def validate_functions(style_id: str, names: list) -> list:
    errors = []
    gallery = os.path.join(RES, "function", "gallery", f"{style_id}.mcfunction")
    if not os.path.isfile(gallery):
        errors.append(f"missing_gallery: {gallery}")
    else:
        with open(gallery, "r", encoding="utf-8") as f:
            text = f.read()
        for name in names:
            if f"place template {MOD_ID}:{name} " not in text:
                errors.append(f"gallery_missing_structure: {name}")
    pat = re.compile(r"^place template myvillage:\w+ ~ ~(-?\d+)? ~$")
    for name in names:
        path = os.path.join(RES, "function", "place", f"{name}.mcfunction")
        if not os.path.isfile(path):
            errors.append(f"missing_place_function: {name}")
            continue
        with open(path, "r", encoding="utf-8") as f:
            line = f.read().strip()
        if not pat.match(line):
            errors.append(f"bad_place_function: {name}: {line}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default=None)
    parser.add_argument("--group", default="chinese_courtyard")
    parser.add_argument("--report", default=None)
    parser.add_argument("--count", type=int, default=6)
    args = parser.parse_args()

    group = get_group(args.group)
    style_id = args.style or group.style_id
    if style_id != group.style_id:
        parser.error(
            f"group {args.group!r} requires --style {group.style_id!r}, got {style_id!r}")
    report_path = args.report
    if report_path is None:
        report_path = DEFAULT_REPORT if args.group == "chinese_courtyard" else os.path.join(
            PROJECT_ROOT, "reports", f"{args.group}_compound_library_report.json")
    elif not os.path.isabs(report_path):
        report_path = os.path.join(PROJECT_ROOT, report_path)
    out_report = OUT_REPORT if args.group == "chinese_courtyard" else os.path.join(
        PROJECT_ROOT, "reports", f"{args.group}_compound_library_validation.json")

    style = load_style(style_id)
    errors = []
    if not os.path.isfile(report_path):
        errors.append(f"missing_report: {report_path}")
        data = {}
    else:
        with open(report_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    compounds = data.get("compounds", [])
    if len(compounds) < args.count:
        errors.append(f"too_few_compounds: {len(compounds)} < {args.count}")
    if group.layout_strategy == "courtyard_street_block":
        variant_fields = (
            "rows", "courtyards_per_row", "street_width",
            "lane", "corner_frontage", "courtyard_size")
    else:
        variant_fields = (
            "courtyard_size", "water_form", "planting_layout",
            "roof_grade", "gate_style", "symmetry")
    variant_keys = {
        tuple(c.get("variant", {}).get(k) for k in variant_fields)
        for c in compounds
    }
    if len(variant_keys) < args.count:
        errors.append(f"too_few_distinct_variants: {len(variant_keys)} < {args.count}")
    for c in compounds:
        if not c.get("passed"):
            errors.append(f"compound_report_failed: {c.get('name')}: {c.get('errors')}")

    names = [c.get("name") for c in compounds if c.get("name")]
    max_size = 128 if group.layout_strategy == "courtyard_street_block" else 64
    nbt_results = [validate_nbt(name, style, max_size=max_size) for name in names]
    fn_errors = validate_functions(style_id, names)
    failed_nbt = [r for r in nbt_results if not r["passed"]]
    if failed_nbt:
        errors.append(f"failed_nbt: {[r['name'] for r in failed_nbt]}")
    errors.extend(fn_errors)

    for result in nbt_results:
        status = "OK  " if result["passed"] else "FAIL"
        extra = (f"size={result.get('size')} blocks={result.get('block_count')}"
                 if result["passed"] else result["errors"])
        print(f"{status} {result['name']:24s} {extra}")
    for err in errors:
        print(f"FAIL {err}")

    summary = {
        "style_id": style_id,
        "group_id": args.group,
        "passed": not errors,
        "errors": errors,
        "total": len(nbt_results),
        "nbt_results": nbt_results,
    }
    os.makedirs(os.path.dirname(out_report), exist_ok=True)
    with open(out_report, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"\n{len(nbt_results) - len(failed_nbt)}/{len(nbt_results)} compound structures passed")
    print(f"report: {os.path.relpath(out_report, PROJECT_ROOT)}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
