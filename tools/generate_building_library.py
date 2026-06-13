#!/usr/bin/env python3
"""Generate the building library for a style and export it as mod resources.

Usage:
    python tools/generate_building_library.py --style medieval_village --count 10

Pipeline per building (see tools/buildgen/):
    Style Profile -> Archetype -> Scale Tier -> Massing Graph ->
    Facade Grammar -> Build Ops -> Passes (+PROTECTED) -> Quality Check ->
    NBT + mcfunction resources

Outputs (resource_export_pass):
    src/main/resources/data/myvillage/structure/<archetype>_NNN.nbt
    src/main/resources/data/myvillage/function/gallery/<style>.mcfunction
    src/main/resources/data/myvillage/function/place/<name>.mcfunction
    reports/building_library_report.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen import export
from buildgen.archetypes import ARCHETYPES, NEW_ARCHETYPE_COUNTS, TIER_PLAN
from buildgen.passes import generate_building
from buildgen.quality import quality_check
from buildgen.style import load_style

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT_PATH = os.path.join(PROJECT_ROOT, "reports", "building_library_report.json")
MAX_ATTEMPTS = 8


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default="medieval_village")
    parser.add_argument("--count", type=int, default=10,
                        help="buildings per archetype (default 10)")
    parser.add_argument("--base-seed", type=int, default=20260612)
    args = parser.parse_args()

    style = load_style(args.style)
    entries = []
    reports = []
    failed_attempts = 0

    requested = 0
    for archetype in ARCHETYPES:
        archetype_count = NEW_ARCHETYPE_COUNTS.get(archetype, args.count)
        requested += archetype_count
        for i in range(1, archetype_count + 1):
            tier = TIER_PLAN[archetype].get(i, "medium")
            name = f"{archetype}_{i:03d}"
            report = None
            ctx = None
            for attempt in range(MAX_ATTEMPTS):
                seed = (args.base_seed + ARCHETYPES.index(archetype) * 100000
                        + i * 101 + attempt * 7919)
                ctx = generate_building(style, archetype, tier, seed)
                report = quality_check(ctx, f"{args.style}/{name}")
                report["attempt"] = attempt + 1
                if report["passed"]:
                    break
                failed_attempts += 1
                print(f"  retry {name} (attempt {attempt + 1}): "
                      f"{report['errors']}")
            if not report["passed"]:
                print(f"FATAL: {name} failed quality check after "
                      f"{MAX_ATTEMPTS} attempts: {report['errors']}")
                reports.append(report)
                continue
            ctx.passes_run.append("quality_check_pass")
            path, info = export.write_structure_nbt(ctx.grid, args.style, name)
            export.write_place_function(args.style, name)
            ctx.passes_run.append("resource_export_pass")
            report["export"] = info
            report["massing_graph"] = ctx.graph.to_dict()
            report["passes_run"] = ctx.passes_run
            reports.append(report)
            entries.append({"name": name, "archetype": archetype,
                            "scale_tier": tier, "size": info["size"]})
            print(f"OK {name:24s} tier={tier:10s} size={info['size']} "
                  f"blocks={info['block_count']} seed={report['seed']}")

    gallery_path = export.write_gallery_function(args.style, entries)

    passed = sum(1 for r in reports if r["passed"])
    summary = {
        "style_id": args.style,
        "requested": requested,
        "generated": len(entries),
        "passed": passed,
        "failed": len(reports) - passed,
        "rejected_attempts": failed_attempts,
        "gallery_function": os.path.relpath(gallery_path, PROJECT_ROOT),
        "reports": reports,
    }
    os.makedirs(os.path.dirname(REPORT_PATH), exist_ok=True)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(f"\ngenerated {len(entries)}/{summary['requested']} buildings "
          f"({failed_attempts} rejected attempts)")
    print(f"report: {os.path.relpath(REPORT_PATH, PROJECT_ROOT)}")
    print(f"gallery: {summary['gallery_function']}")
    return 0 if len(entries) == summary["requested"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
