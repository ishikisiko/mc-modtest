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
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen import export
from buildgen.compound import (sample_compound_library, sample_sect_compound_library,
                               validate_compound, validate_compound_library,
                               validate_sect_compound)
from buildgen.compound import generate_subbuilding
from buildgen.groups import get_group
from buildgen.quality import quality_check
from buildgen.style import load_style

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
        report["massing_graph"] = ctx.graph.to_dict()
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
    args = parser.parse_args()

    group = get_group(args.group)
    style_id = args.style
    if args.style == parser.get_default("style") and group.style_id != args.style:
        style_id = group.style_id
    elif args.style != group.style_id:
        parser.error(
            f"group {args.group!r} requires --style {group.style_id!r}, got {args.style!r}")
    style = load_style(style_id)
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
            export.write_place_function(style.style_id, name)
            report["name"] = name
            report["export"] = info
            report["compound_graph"] = compound.to_dict()
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
            "gallery_function": os.path.relpath(gallery_path, PROJECT_ROOT),
            "standalone_reports": [],
            "compounds": compound_reports,
        }
        os.makedirs(os.path.dirname(report_path), exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)
        print(f"\ngenerated {len(compound_reports)}/{args.count} sect compounds")
        print(f"report: {os.path.relpath(report_path, PROJECT_ROOT)}")
        print(f"gallery: {summary['gallery_function']}")
        return 0

    standalone_reports = generate_standalone_reviews(style, args.base_seed)
    compounds = sample_compound_library(args.count, args.base_seed, style)

    entries = []
    compound_reports = []
    for index, compound in enumerate(compounds, start=1):
        name = f"chinese_courtyard_{index:03d}"
        report = validate_compound(compound)
        if not report["passed"]:
            raise RuntimeError(f"{name} failed compound validation: {report['errors']}")
        _, info = export.write_structure_nbt(compound.grid, style.style_id, name)
        export.write_place_function(style.style_id, name)
        report["name"] = name
        report["export"] = info
        report["compound_graph"] = compound.to_dict()
        compound_reports.append(report)
        entries.append({"name": name, "archetype": "chinese_courtyard",
                        "scale_tier": compound.variant.courtyard_size,
                        "size": info["size"],
                        "group_id": "chinese_courtyard"})
        print(f"OK {name:24s} variant={compound.variant.key()} size={info['size']} blocks={info['block_count']}")

    gallery_path = export.write_gallery_function(style.style_id, entries,
                                                 spacing_x=56, spacing_z=60)
    library_report = validate_compound_library(compounds, min_distinct=args.count)
    summary = {
        "style_id": style.style_id,
        "requested": args.count,
        "generated": len(compound_reports),
        "passed": library_report["passed"],
        "errors": library_report["errors"],
        "distinct_variants": library_report["distinct_variants"],
        "gallery_function": os.path.relpath(gallery_path, PROJECT_ROOT),
        "standalone_reports": standalone_reports,
        "compounds": compound_reports,
    }
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"\ngenerated {len(compound_reports)}/{args.count} compounds")
    print(f"report: {os.path.relpath(report_path, PROJECT_ROOT)}")
    print(f"gallery: {summary['gallery_function']}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
