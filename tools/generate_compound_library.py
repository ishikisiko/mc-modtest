#!/usr/bin/env python3
"""Generate Chinese courtyard compounds and review sub-buildings.

Outputs:
  src/main/resources/data/myvillage/structure/main_hall_review.nbt
  src/main/resources/data/myvillage/structure/side_wing_review.nbt
  src/main/resources/data/myvillage/structure/front_row_review.nbt
  src/main/resources/data/myvillage/structure/chinese_courtyard_001.nbt ...
  src/main/resources/data/myvillage/function/place/*.mcfunction
  src/main/resources/data/myvillage/function/gallery/chinese_courtyard.mcfunction
  reports/compound_library_report.json
"""

from __future__ import annotations

import argparse
from copy import deepcopy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen import export
from buildgen.compound import (sample_compound_library, sample_sect_compound_library,
                               sample_huipai_mansion_library,
                               sample_town_block_library, validate_compound,
                               validate_huipai_mansion,
                               validate_compound_library, validate_sect_compound,
                               validate_town_block, validate_town_block_library,
                               generate_mansion, validate_mansion,
                               generate_hero_rockery_fragment,
                               compound_silhouette_score)
from buildgen.compound import generate_subbuilding
from buildgen.groups import get_group
from buildgen.quality import quality_check
from buildgen.style import load_style, modset_namespaces

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT_PATH = os.path.join(PROJECT_ROOT, "reports", "compound_library_report.json")
MAX_ATTEMPTS = 8


def generate_standalone_reviews(style, base_seed: int) -> list:
    reports = []
    for index, archetype in enumerate(("main_hall", "side_wing", "front_row"), start=1):
        ctx = None
        report = None
        for attempt in range(MAX_ATTEMPTS):
            seed = base_seed + index * 1000 + attempt * 137
            roof_grade = style.allowed_roof_types[(index + attempt) % len(style.allowed_roof_types)]
            ctx = generate_subbuilding(style, archetype, seed, roof_grade,
                                       "chinese_courtyard")
            report = quality_check(ctx, f"{style.style_id}/{archetype}_review")
            report["attempt"] = attempt + 1
            if report["passed"]:
                break
        if not report or not report["passed"]:
            raise RuntimeError(f"{archetype}_review failed quality: {report['errors'] if report else 'missing report'}")
        name = f"{archetype}_review"
        _, info = export.write_structure_nbt(ctx.grid, style.style_id, name)
        export.write_place_function(style.style_id, name)
        report["export"] = info
        report["massing_graph"] = ctx.graph.to_summary_dict()
        reports.append(report)
        print(f"OK {name:24s} size={info['size']} blocks={info['block_count']} seed={report['seed']}")
    return reports


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default="chinese_courtyard")
    parser.add_argument("--group", default="chinese_courtyard")
    parser.add_argument("--count", type=int, default=6)
    parser.add_argument("--base-seed", type=int, default=20260614)
    parser.add_argument("--report", default=None,
                        help="optional report path; cultivation groups default to a group-specific report")
    parser.add_argument("--profile", default="full", choices=("vanilla", "full"),
                        help="modset profile: 'full' keeps mod ids, 'vanilla' drops them to fallbacks")
    args = parser.parse_args()

    group = get_group(args.group)
    style_id = args.style
    if args.style == parser.get_default("style") and group.style_id != args.style:
        style_id = group.style_id
    elif args.style != group.style_id:
        parser.error(
            f"group {args.group!r} requires --style {group.style_id!r}, got {args.style!r}")
    style = load_style(style_id, modset_namespaces(args.profile))
    report_path = args.report
    if report_path is None:
        if args.group == "chinese_courtyard":
            report_path = REPORT_PATH
        else:
            report_path = os.path.join(PROJECT_ROOT, "reports", f"{args.group}_compound_library_report.json")
    elif not os.path.isabs(report_path):
        report_path = os.path.join(PROJECT_ROOT, report_path)

    if args.group == "cultivation_sect":
        compounds = sample_sect_compound_library(args.count, args.base_seed, style)
        entries = []
        compound_reports = []
        for index, compound in enumerate(compounds, start=1):
            name = f"cultivation_sect_{index:03d}"
            report = validate_sect_compound(compound)
            if not report["passed"]:
                raise RuntimeError(f"{name} failed sect compound validation: {report['errors']}")
            _, info = export.write_structure_nbt(compound.grid, style.style_id, name)
            metadata = {
                "structure": name,
                "layout_strategy": compound.meta.get("layout_strategy"),
                "siting_context": compound.meta.get("siting_context", {}),
                "terraces": compound.meta.get("terraces", []),
                "terrace_levels": compound.meta.get("terrace_levels", {}),
                "importance_tiers": compound.meta.get("importance_tiers", {}),
                "hierarchy": compound.meta.get("hierarchy", []),
                "links": [
                    {
                        "id": node.id,
                        "kind": node.meta.get("kind"),
                        "endpoints": node.meta.get("endpoints", []),
                        "relative_y": node.meta.get("relative_y"),
                        "over": node.meta.get("over"),
                    }
                    for node in compound.parcel_nodes
                    if node.type == "link"
                ],
            }
            meta_path = export.write_settlement_metadata(name, metadata)
            export.write_place_function(style.style_id, name)
            info["settlement_metadata"] = export.repo_relpath(meta_path)
            report["name"] = name
            report["export"] = info
            report["compound_graph"] = compound.to_summary_dict()
            compound_reports.append(report)
            entries.append({"name": name, "archetype": "cultivation_sect",
                            "scale_tier": "sect_terraced",
                            "size": info["size"],
                            "group_id": "cultivation_sect"})
            print(f"OK {name:24s} size={info['size']} blocks={info['block_count']}")
        gallery_path = export.write_gallery_function(style.style_id, entries,
                                                     spacing_x=64, spacing_z=64)
        summary = {
            "style_id": style.style_id,
            "group_id": args.group,
            "requested": args.count,
            "generated": len(compound_reports),
            "passed": True,
            "errors": [],
            "distinct_variants": len(compound_reports),
            "gallery_function": export.repo_relpath(gallery_path),
            "standalone_reports": [],
            "compounds": compound_reports,
        }
        os.makedirs(os.path.dirname(report_path), exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)
        print(f"\ngenerated {len(compound_reports)}/{args.count} sect compounds")
        print(f"report: {export.repo_relpath(report_path)}")
        print(f"gallery: {summary['gallery_function']}")
        return 0

    if group.layout_strategy == "huipai_tianjing_reference_slice":
        compounds = sample_huipai_mansion_library(args.count, args.base_seed, style)
        entries = []
        compound_reports = []
        for index, compound in enumerate(compounds, start=1):
            name = f"chinese_huipai_mansion_{index:03d}"
            report = validate_huipai_mansion(compound)
            if not report["passed"]:
                raise RuntimeError(f"{name} failed Hui-style validation: {report['errors']}")
            _, info = export.write_structure_nbt(deepcopy(compound.grid),
                                                 style.style_id, name)
            export.write_place_function(style.style_id, name)
            report["name"] = name
            report["export"] = info
            report["compound_graph"] = compound.to_summary_dict()
            compound_reports.append(report)
            entries.append({
                "name": name,
                "archetype": "chinese_huipai_mansion",
                "scale_tier": compound.variant.courtyard_size,
                "size": info["size"],
                "group_id": "chinese_huipai_mansion",
            })
            print(f"OK {name:24s} variant={compound.variant.key()} "
                  f"size={info['size']} blocks={info['block_count']}")
        gallery_path = export.write_gallery_function(style.style_id, entries,
                                                     spacing_x=56, spacing_z=72)
        passed = all(report["passed"] for report in compound_reports)
        summary = {
            "style_id": style.style_id,
            "group_id": args.group,
            "requested": args.count,
            "generated": len(compound_reports),
            "passed": passed,
            "errors": [error for report in compound_reports for error in report["errors"]],
            "distinct_variants": len(set(c.variant.key() for c in compounds)),
            "gallery_function": export.repo_relpath(gallery_path),
            "reference_candidate": group.scale_params.get("reference_candidate", "candidate_003"),
            "source_usage_decision": "local_research",
            "original_generated": True,
            "copied_source_assets": False,
            "partial_implementation": True,
            "requires_owner_visual_verdict": True,
            "standalone_reports": [],
            "compounds": compound_reports,
        }
        os.makedirs(os.path.dirname(report_path), exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)
        print(f"\ngenerated {len(compound_reports)}/{args.count} Hui-style compounds")
        print(f"report: {export.repo_relpath(report_path)}")
        print(f"gallery: {summary['gallery_function']}")
        return 0 if passed else 1

    if group.layout_strategy == "mansion_compound":
        # Standalone hero 假山 review fragment (add-hero-rockery task 4.0): a
        # self-contained specimen + basin, stamped via /myvillage place hero_rockery.
        fragment = generate_hero_rockery_fragment()
        _, frag_info = export.write_structure_nbt(deepcopy(fragment.grid),
                                                  style.style_id, "hero_rockery")
        export.write_place_function(style.style_id, "hero_rockery")
        print(f"OK {'hero_rockery':24s} size={frag_info['size']} "
              f"blocks={frag_info['block_count']}")
        compounds = [generate_mansion(args.base_seed + i, style) for i in range(args.count)]
        entries = []
        compound_reports = []
        for index, compound in enumerate(compounds, start=1):
            name = f"chinese_mansion_{index:03d}"
            report = validate_mansion(compound)
            if not report["passed"]:
                raise RuntimeError(f"{name} failed mansion validation: {report['errors']}")
            _, info = export.write_structure_nbt(deepcopy(compound.grid), style.style_id, name)
            export.write_place_function(style.style_id, name)
            report["name"] = name
            report["export"] = info
            report["compound_graph"] = compound.to_summary_dict()
            compound_reports.append(report)
            entries.append({"name": name, "archetype": "chinese_mansion",
                            "scale_tier": compound.variant.courtyard_size,
                            "size": info["size"],
                            "group_id": "chinese_mansion"})
            print(f"OK {name:24s} variant={compound.variant.key()} "
                  f"size={info['size']} blocks={info['block_count']}")
        gallery_path = export.write_gallery_function(style.style_id, entries,
                                                     spacing_x=64, spacing_z=80)
        # Use raw (uncapped) silhouette scores for spread; mansion compounds
        # with multiple towers hit the 100 cap making capped spread too narrow.
        # tower_count * 5 adds a visible-roofline bonus: 绣楼 pair ≠ single 楼阁.
        def _raw_score(c):
            (_, y0, _), (_, y1, _) = c.grid.bounds()
            h = y1 - min(0, y0) + 1
            return (h * 2 + len(c.building_slots) * 10
                    + c.variant.main_bays * 2 + c.variant.tower_count * 5)
        silhouette_scores = [compound_silhouette_score(c) for c in compounds]
        raw_scores = [_raw_score(c) for c in compounds]
        score_spread = max(raw_scores) - min(raw_scores)
        min_spread = 15
        passed = score_spread >= min_spread
        summary = {
            "style_id": style.style_id,
            "group_id": args.group,
            "requested": args.count,
            "generated": len(compound_reports),
            "passed": passed,
            "errors": [] if passed else [f"silhouette_spread_too_low: {score_spread} < {min_spread}"],
            "distinct_variants": len(set(c.variant.key() for c in compounds)),
            "silhouette_scores": silhouette_scores,
            "silhouette_spread": score_spread,
            "min_silhouette_spread": min_spread,
            "gallery_function": export.repo_relpath(gallery_path),
            "standalone_reports": [],
            "compounds": compound_reports,
        }
        os.makedirs(os.path.dirname(report_path), exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)
        print(f"\ngenerated {len(compound_reports)}/{args.count} mansion compounds")
        print(f"report: {export.repo_relpath(report_path)}")
        print(f"gallery: {summary['gallery_function']}")
        return 0 if passed else 1

    if group.layout_strategy in ("courtyard_street_block", "town_generation"):
        compounds = sample_town_block_library(
            args.count, args.base_seed, style, group.archetype_roster)
        entries = []
        compound_reports = []
        for index, compound in enumerate(compounds, start=1):
            name = f"{args.group}_{index:03d}"
            report = validate_town_block(compound)
            if not report["passed"]:
                raise RuntimeError(f"{name} failed town block validation: {report['errors']}")
            _, info = export.write_structure_nbt(compound.grid, style.style_id, name)
            export.write_place_function(style.style_id, name)
            report["name"] = name
            report["export"] = info
            report["compound_graph"] = compound.to_summary_dict()
            compound_reports.append(report)
            entries.append({"name": name, "archetype": args.group,
                            "scale_tier": f"{compound.variant.rows}x{compound.variant.courtyards_per_row}",
                            "size": info["size"],
                            "group_id": args.group})
            print(f"OK {name:24s} variant={compound.variant.key()} size={info['size']} blocks={info['block_count']}")
        gallery_path = export.write_gallery_function(style.style_id, entries,
                                                     spacing_x=128, spacing_z=96)
        library_report = validate_town_block_library(compounds, min_distinct=args.count)
        summary = {
            "style_id": style.style_id,
            "group_id": args.group,
            "requested": args.count,
            "generated": len(compound_reports),
            "passed": library_report["passed"],
            "errors": library_report["errors"],
            "distinct_variants": library_report["distinct_variants"],
            "gallery_function": export.repo_relpath(gallery_path),
            "standalone_reports": [],
            "compounds": compound_reports,
        }
        os.makedirs(os.path.dirname(report_path), exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)
        print(f"\ngenerated {len(compound_reports)}/{args.count} town blocks")
        print(f"report: {export.repo_relpath(report_path)}")
        print(f"gallery: {summary['gallery_function']}")
        return 0 if summary["passed"] else 1

    standalone_reports = generate_standalone_reviews(style, args.base_seed)
    compounds = sample_compound_library(args.count, args.base_seed, style)

    entries = []
    compound_reports = []
    for index, compound in enumerate(compounds, start=1):
        name = f"chinese_courtyard_{index:03d}"
        report = validate_compound(compound)
        if not report["passed"]:
            raise RuntimeError(f"{name} failed compound validation: {report['errors']}")
        # Export normalizes its grid in place; keep the parcel graph in world
        # coordinates for the whole-library structural acceptance pass below.
        _, info = export.write_structure_nbt(deepcopy(compound.grid), style.style_id, name)
        export.write_place_function(style.style_id, name)
        report["name"] = name
        report["export"] = info
        report["compound_graph"] = compound.to_summary_dict()
        compound_reports.append(report)
        entries.append({"name": name, "archetype": "chinese_courtyard",
                        "scale_tier": compound.variant.courtyard_size,
                        "size": info["size"],
                        "group_id": "chinese_courtyard"})
        print(f"OK {name:24s} variant={compound.variant.key()} size={info['size']} blocks={info['block_count']}")

    gallery_path = export.write_gallery_function(style.style_id, entries,
                                                 spacing_x=56, spacing_z=60)
    structure_names = [entry["name"] for entry in entries]
    structure_dir = os.path.join(PROJECT_ROOT, "src", "main", "resources",
                                 "data", "myvillage", "structure")
    library_report = validate_compound_library(
        compounds, min_distinct=args.count, structure_dir=structure_dir,
        structure_names=structure_names)
    summary = {
        "style_id": style.style_id,
        "requested": args.count,
        "generated": len(compound_reports),
        "passed": library_report["passed"],
        "errors": library_report["errors"],
        "distinct_variants": library_report["distinct_variants"],
        "silhouette_scores": library_report["silhouette_scores"],
        "silhouette_spread": library_report["silhouette_spread"],
        "min_silhouette_spread": library_report["min_silhouette_spread"],
        "nbt_sha256": library_report["nbt_sha256"],
        "gallery_function": export.repo_relpath(gallery_path),
        "standalone_reports": standalone_reports,
        "compounds": compound_reports,
    }
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"\ngenerated {len(compound_reports)}/{args.count} compounds")
    print(f"report: {export.repo_relpath(report_path)}")
    print(f"gallery: {summary['gallery_function']}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
