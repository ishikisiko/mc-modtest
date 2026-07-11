#!/usr/bin/env python3
"""Validate generated Chinese courtyard compound resources.

This validator checks the generated report from the parcel-layer validator and
then verifies that the exported NBT and place/gallery functions exist and parse.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen.nbtread import read_gzipped_nbt, state_string
from buildgen import export
from buildgen.groups import get_group
from buildgen.modset import load_modset
from buildgen.style import load_style, modset_namespaces

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ID = "myvillage"
RES = os.path.join(PROJECT_ROOT, "src", "main", "resources", "data", MOD_ID)
DEFAULT_REPORT = os.path.join(PROJECT_ROOT, "reports", "compound_library_report.json")
OUT_REPORT = os.path.join(PROJECT_ROOT, "reports", "compound_library_validation.json")
DATA_VERSION = 3955


def validate_nbt(name: str, style, modset, max_size: int = 64,
                 require_landscape_features: bool = True) -> dict:
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
    errors.extend(modset.palette_block_errors(palette))
    if not any("_stairs" in s or "_slab" in s for s in palette):
        errors.append("no_roof_blocks")
    planting = ("moss_block", "azalea_leaves", "flowering_azalea_leaves", "bamboo")
    planting_presence = planting + ("oak_leaves", "oak_sapling", "grass_block")
    by_pos = {tuple(block["pos"]): palette[block["state"]] for block in blocks}
    if require_landscape_features:
        if not any(s == "minecraft:water" or s.startswith("minecraft:water[") for s in palette):
            errors.append("missing_water_feature")
        if not any(any(p in s for p in planting_presence) for s in palette):
            errors.append("missing_planting")
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


def _frontage_errors(compound: dict) -> list:
    errors = []
    for slot in compound.get("compound_graph", {}).get("building_slots", []):
        graph = slot.get("massing_graph", {})
        meta = graph.get("meta", {})
        frontage = meta.get("frontage")
        if not frontage:
            errors.append(f"missing_frontage: {slot.get('id')}")
            continue
        side = frontage.get("side")
        if side not in ("front", "back", "west", "east"):
            errors.append(f"bad_frontage_side: {slot.get('id')}: {side!r}")
            continue
        cells = [tuple(c) for c in frontage.get("opening_cells", [])]
        if not cells:
            errors.append(f"missing_frontage_opening: {slot.get('id')}")
            continue
        main = None
        for node in graph.get("nodes", []):
            if node.get("id") == frontage.get("volume", "main"):
                main = node
                break
        if main is None:
            errors.append(f"frontage_volume_missing: {slot.get('id')}: {frontage.get('volume')}")
            continue
        ox, _oy, oz = main["origin"]
        sx, _sy, sz = main["size"]
        x0, x1 = ox, ox + sx - 1
        z0, z1 = oz, oz + sz - 1
        for x, z in cells:
            on_side = (
                (side == "front" and z == z0 and x0 <= x <= x1) or
                (side == "back" and z == z1 and x0 <= x <= x1) or
                (side == "west" and x == x0 and z0 <= z <= z1) or
                (side == "east" and x == x1 and z0 <= z <= z1)
            )
            if not on_side:
                errors.append(
                    f"frontage_opening_off_side: {slot.get('id')}: {side} {sorted(cells)[:4]}")
                break
    return errors


def _sect_metadata_errors(name: str, compound: dict) -> list:
    errors = []
    path = os.path.join(RES, "settlement_meta", f"{name}.json")
    if not os.path.isfile(path):
        return [f"missing_settlement_metadata: {name}"]
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as exc:
        return [f"bad_settlement_metadata_json: {name}: {exc}"]
    graph_meta = compound.get("compound_graph", {}).get("meta", {})
    if data.get("structure") != name:
        errors.append(f"metadata_structure_mismatch: {name}: {data.get('structure')}")
    if data.get("layout_strategy") != "sect_terraced_axial_compound":
        errors.append(f"metadata_bad_layout_strategy: {name}: {data.get('layout_strategy')}")
    siting = data.get("siting_context", {})
    for key in ("mountain_slope", "cliff_back", "water_front", "cloud_sea"):
        if key not in siting:
            errors.append(f"metadata_missing_siting_context: {name}: {key}")
    terraces = data.get("terraces", [])
    if len(terraces) < 3:
        errors.append(f"metadata_too_few_terraces: {name}: {len(terraces)}")
    levels = data.get("terrace_levels", {})
    if levels != graph_meta.get("terrace_levels", {}):
        errors.append(f"metadata_terrace_levels_mismatch: {name}")
    if not data.get("links"):
        errors.append(f"metadata_missing_links: {name}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default=None)
    parser.add_argument("--group", default="chinese_courtyard")
    parser.add_argument("--report", default=None)
    parser.add_argument("--count", type=int, default=6)
    parser.add_argument("--profile", default="full", choices=("vanilla", "full"),
                        help="modset profile: 'vanilla' forbids all mod ids, 'full' allows confirmed catalog ids")
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

    style = load_style(style_id, modset_namespaces(args.profile))
    modset = load_modset(args.profile)
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
    if args.group == "chinese_courtyard":
        scores = data.get("silhouette_scores", [])
        spread = max(scores) - min(scores) if scores else 0
        if spread < 15:
            errors.append(f"compound_silhouette_spread_too_low: {spread} < 15")
        for slot in ("PLATFORM_STONE", "COLUMN"):
            values = style.material_slots.get(slot, [])
            if (not values or any(v == "minecraft:air" or
                                  not v.startswith("minecraft:") for v in values)):
                errors.append(f"non_vanilla_or_empty_slot: {slot}: {values}")
    if args.group == "chinese_courtyard":
        variant_fields = (
            "courtyard_size", "water_form", "planting_layout", "roof_grade",
            "gate_type", "symmetry", "gallery_type", "gate_side",
            "main_hall_bays", "platform_tier")
    elif group.layout_strategy in ("courtyard_street_block", "town_generation"):
        variant_fields = (
            "rows", "courtyards_per_row", "street_width",
            "lane", "corner_frontage", "courtyard_size")
    else:
        variant_fields = (
            "courtyard_size", "roof_grade", "gate_type", "layout_type",
            "main_orientation", "main_bays", "platform_tier")
    variant_keys = {
        tuple((c.get("variant") or c.get("compound_graph", {}).get("variant", {})).get(k)
              for k in variant_fields)
        for c in compounds
    }
    min_distinct_variants = (
        1 if group.layout_strategy == "sect_terraced_axial_compound"
        else args.count
    )
    if len(variant_keys) < min_distinct_variants:
        errors.append(
            f"too_few_distinct_variants: {len(variant_keys)} < {min_distinct_variants}")
    for c in compounds:
        if not c.get("passed"):
            errors.append(f"compound_report_failed: {c.get('name')}: {c.get('errors')}")
        if group.layout_strategy == "huipai_tianjing_reference_slice":
            stats = c.get("stats", {})
            if data.get("reference_candidate") != "candidate_003":
                errors.append("huipai_reference_candidate_missing")
            if data.get("source_usage_decision") != "local_research":
                errors.append("huipai_source_usage_decision_missing")
            if data.get("original_generated") is not True:
                errors.append("huipai_original_generated_missing")
            if data.get("copied_source_assets") is not False:
                errors.append("huipai_copied_source_assets_forbidden")
            sequence = stats.get("sequence", [])
            if sequence != ["mentang", "tianjing_1", "xiangtang", "tianjing_2", "qintang"]:
                errors.append(f"{c.get('name')}: huipai_sequence_missing:{sequence}")
            if int(stats.get("min_sequence_gap") or 0) < 3:
                errors.append(f"{c.get('name')}: huipai_sequence_gap_too_tight")
            if int(stats.get("min_hall_area") or 0) < 250:
                errors.append(f"{c.get('name')}: huipai_hall_mass_too_small")
            if int(stats.get("structure_height") or 0) < 16:
                errors.append(f"{c.get('name')}: huipai_height_too_low")
            if stats.get("closed_facade_entries") != 1:
                errors.append(f"{c.get('name')}: huipai_closed_facade_entry_count")
            if int(stats.get("stepped_gable_stages") or 0) < 2:
                errors.append(f"{c.get('name')}: huipai_stepped_gable_stage_count")
            if int(stats.get("stepped_gable_visual_thickness") or 0) < 2:
                errors.append(f"{c.get('name')}: huipai_stepped_gable_too_thin")
            if stats.get("stepped_gable_dark_cap") is not True:
                errors.append(f"{c.get('name')}: huipai_stepped_gable_dark_cap_missing")
            if stats.get("stepped_gable_short_returns") is not True:
                errors.append(f"{c.get('name')}: huipai_stepped_gable_returns_missing")
            if int(stats.get("side_wing_count") or 0) < 4:
                errors.append(f"{c.get('name')}: huipai_side_wing_count")
            if int(stats.get("side_wing_pairs") or 0) < 2:
                errors.append(f"{c.get('name')}: huipai_side_wing_pair_count")
            if int(stats.get("enclosed_tianjing_count") or 0) < 2:
                errors.append(f"{c.get('name')}: huipai_tianjing_not_flanked")
            if int(stats.get("max_side_wing_width") or 99) > 8:
                errors.append(f"{c.get('name')}: huipai_side_wing_overfilled")
            if int(stats.get("min_side_wing_width") or 0) < 8:
                errors.append(f"{c.get('name')}: huipai_side_wing_mass_too_small")
            if stats.get("footprint_mode") != "expanded_review_lot":
                errors.append(f"{c.get('name')}: huipai_footprint_mode_missing")
            if stats.get("garden_nodes"):
                errors.append(f"{c.get('name')}: huipai_garden_drift:{stats.get('garden_nodes')}")
            for node_id, dims in stats.get("tianjing_dims", {}).items():
                if max(dims or [99]) > 6:
                    errors.append(f"{c.get('name')}: huipai_tianjing_too_large:{node_id}:{dims}")
        if group.layout_strategy == "ganlan_stilted_reference_slice":
            stats = c.get("stats", {})
            if data.get("reference_candidate") != "candidate_005":
                errors.append("ganlan_reference_candidate_missing")
            if data.get("source_usage_decision") != "local_research":
                errors.append("ganlan_source_usage_decision_missing")
            if data.get("original_generated") is not True:
                errors.append("ganlan_original_generated_missing")
            if data.get("copied_source_assets") is not False:
                errors.append("ganlan_copied_source_assets_forbidden")
            if int(stats.get("height_above_support") or 0) < 2:
                errors.append(f"{c.get('name')}: ganlan_raised_floor_too_low")
            if int(stats.get("support_post_count") or 0) < 6:
                errors.append(f"{c.get('name')}: ganlan_too_few_support_posts")
            if stats.get("unsupported_posts"):
                errors.append(f"{c.get('name')}: ganlan_support_posts_not_connected")
            if float(stats.get("underside_open_ratio") or 0.0) < 0.65:
                errors.append(f"{c.get('name')}: ganlan_underside_too_filled")
            if int(stats.get("veranda_cells") or 0) < 20:
                errors.append(f"{c.get('name')}: ganlan_veranda_too_small")
            if int(stats.get("roof_overhang") or 0) < 2:
                errors.append(f"{c.get('name')}: ganlan_deep_eave_missing")
        if args.group == "cultivation_town":
            for err in _frontage_errors(c):
                errors.append(f"{c.get('name')}: {err}")
        if args.group == "cultivation_sect" and c.get("name"):
            for err in _sect_metadata_errors(c["name"], c):
                errors.append(err)

    names = [c.get("name") for c in compounds if c.get("name")]
    max_size = 128 if group.layout_strategy in (
        "courtyard_street_block", "town_generation", "sect_terraced_axial_compound",
        "mansion_compound", "huipai_tianjing_reference_slice") else 64
    require_landscape_features = group.layout_strategy not in (
        "sect_terraced_axial_compound",
        "huipai_tianjing_reference_slice",
    )
    nbt_results = [
        validate_nbt(name, style, modset, max_size=max_size,
                     require_landscape_features=require_landscape_features)
        for name in names
    ]
    nbt_hashes = []
    for name in names:
        path = os.path.join(RES, "structure", f"{name}.nbt")
        try:
            with open(path, "rb") as handle:
                nbt_hashes.append(hashlib.sha256(handle.read()).hexdigest())
        except OSError:
            nbt_hashes.append("")
    if args.group == "chinese_courtyard":
        duplicates = {value for value in nbt_hashes
                      if value and nbt_hashes.count(value) > 1}
        if duplicates:
            errors.append(f"byte_identical_compounds: {len(duplicates)} duplicate hashes")
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
        "profile": args.profile,
        "passed": not errors,
        "errors": errors,
        "total": len(nbt_results),
        "nbt_results": nbt_results,
        "silhouette_scores": data.get("silhouette_scores", []),
        "silhouette_spread": data.get("silhouette_spread"),
        "nbt_sha256": nbt_hashes,
    }
    os.makedirs(os.path.dirname(out_report), exist_ok=True)
    with open(out_report, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"\n{len(nbt_results) - len(failed_nbt)}/{len(nbt_results)} compound structures passed")
    print(f"report: {export.repo_relpath(out_report)}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
