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
from buildgen.compound import (sample_compound_library, validate_compound,
                               validate_compound_library)
from buildgen.compound import generate_subbuilding
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
            ctx = generate_subbuilding(style, archetype, seed, roof_grade)
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
    parser.add_argument("--count", type=int, default=6)
    parser.add_argument("--base-seed", type=int, default=20260614)
    args = parser.parse_args()

    style = load_style(args.style)
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
                        "size": info["size"]})
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
    os.makedirs(os.path.dirname(REPORT_PATH), exist_ok=True)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"\ngenerated {len(compound_reports)}/{args.count} compounds")
    print(f"report: {os.path.relpath(REPORT_PATH, PROJECT_ROOT)}")
    print(f"gallery: {summary['gallery_function']}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
