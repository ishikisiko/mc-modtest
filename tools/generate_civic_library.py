#!/usr/bin/env python3
"""Generate tavern and lord manor civic structures as mod resources.

Outputs:
  src/main/resources/data/myvillage/structure/tavern_001.nbt ... tavern_005.nbt
  src/main/resources/data/myvillage/structure/lord_manor_001.nbt ... lord_manor_003.nbt
  src/main/resources/data/myvillage/function/place/<name>.mcfunction
  src/main/resources/data/myvillage/function/gallery/civic.mcfunction
  reports/civic_library_report.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen import export
from buildgen.archetypes import CIVIC_ARCHETYPES, CIVIC_ARCHETYPE_COUNTS
from buildgen.passes import generate_building
from buildgen.quality import quality_check
from buildgen.style import load_style

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT_PATH = os.path.join(PROJECT_ROOT, "reports", "civic_library_report.json")
MAX_ATTEMPTS = 8


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default="medieval_village")
    parser.add_argument("--base-seed", type=int, default=20260615)
    args = parser.parse_args()

    style = load_style(args.style)
    entries = []
    reports = []
    failed_attempts = 0
    requested = sum(CIVIC_ARCHETYPE_COUNTS[a] for a in CIVIC_ARCHETYPES)

    for archetype_index, archetype in enumerate(CIVIC_ARCHETYPES):
        count = CIVIC_ARCHETYPE_COUNTS[archetype]
        for i in range(1, count + 1):
            tier = f"{archetype}_v{i}"
            name = f"{archetype}_{i:03d}"
            report = None
            ctx = None
            for attempt in range(MAX_ATTEMPTS):
                seed = args.base_seed + archetype_index * 100000 + i * 101 + attempt * 7919
                ctx = generate_building(style, archetype, tier, seed, "civic")
                report = quality_check(ctx, f"{args.style}/{name}")
                report["attempt"] = attempt + 1
                if report["passed"]:
                    break
                failed_attempts += 1
                print(f"  retry {name} (attempt {attempt + 1}): {report['errors']}")
            if not report or not report["passed"]:
                print(f"FATAL: {name} failed quality check after {MAX_ATTEMPTS} attempts: {report['errors'] if report else 'missing report'}")
                if report:
                    reports.append(report)
                continue
            ctx.passes_run.append("quality_check_pass")
            _, info = export.write_structure_nbt(ctx.grid, args.style, name)
            export.write_place_function(args.style, name)
            ctx.passes_run.append("resource_export_pass")
            report["export"] = info
            report["massing_graph"] = ctx.graph.to_dict()
            report["passes_run"] = ctx.passes_run
            reports.append(report)
            entries.append({"name": name, "archetype": archetype,
                            "scale_tier": tier, "size": info["size"],
                            "group_id": "civic"})
            print(f"OK {name:24s} tier={tier:14s} size={info['size']} blocks={info['block_count']} seed={report['seed']}")

    gallery_path = export.write_civic_gallery_function(entries)
    passed = sum(1 for r in reports if r.get("passed"))
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
    print(f"\ngenerated {len(entries)}/{requested} civic structures ({failed_attempts} rejected attempts)")
    print(f"report: {os.path.relpath(REPORT_PATH, PROJECT_ROOT)}")
    print(f"gallery: {summary['gallery_function']}")
    return 0 if len(entries) == requested else 1


if __name__ == "__main__":
    raise SystemExit(main())
