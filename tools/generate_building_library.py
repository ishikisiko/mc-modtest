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
from buildgen.groups import get_group
from buildgen.passes import generate_building
from buildgen.quality import cultivation_variant_distinctness, quality_check
from buildgen.style import load_style, modset_namespaces

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT_PATH = os.path.join(PROJECT_ROOT, "reports", "building_library_report.json")
STRUCTURE_DIR = os.path.join(
    PROJECT_ROOT, "src", "main", "resources", "data", "myvillage", "structure")
MAX_ATTEMPTS = 8


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--style", default="medieval_village")
    parser.add_argument("--group", default=None,
                        help="optional settlement group id; defaults to the legacy medieval library")
    parser.add_argument("--report", default=None,
                        help="optional report path; cultivation groups default to a group-specific report")
    parser.add_argument("--count", type=int, default=10,
                        help="buildings per archetype (default 10)")
    parser.add_argument("--base-seed", type=int, default=20260612)
    parser.add_argument("--profile", default="full", choices=("vanilla", "full"),
                        help="modset profile: 'full' keeps mod ids, 'vanilla' drops them to fallbacks")
    args = parser.parse_args()

    style_id = args.style
    archetypes = ARCHETYPES
    group_id = args.group
    if group_id:
        group = get_group(group_id)
        if args.style == parser.get_default("style"):
            style_id = group.style_id
        elif args.style != group.style_id:
            parser.error(
                f"group {group_id!r} requires --style {group.style_id!r}, got {args.style!r}")
        archetypes = group.archetype_roster

    style = load_style(style_id, modset_namespaces(args.profile))
    report_path = args.report
    if report_path is None:
        report_name = f"{group_id}_building_library_report.json" if group_id else "building_library_report.json"
        report_path = os.path.join(PROJECT_ROOT, "reports", report_name)
    elif not os.path.isabs(report_path):
        report_path = os.path.join(PROJECT_ROOT, report_path)
    entries = []
    reports = []
    failed_attempts = 0

    requested = 0
    for archetype in archetypes:
        archetype_count = NEW_ARCHETYPE_COUNTS.get(archetype, args.count)
        requested += archetype_count
        for i in range(1, archetype_count + 1):
            tier = TIER_PLAN[archetype].get(i, "medium")
            name = f"{archetype}_{i:03d}"
            report = None
            ctx = None
            for attempt in range(MAX_ATTEMPTS):
                seed = (args.base_seed + list(archetypes).index(archetype) * 100000
                        + i * 101 + attempt * 7919)
                ctx = generate_building(style, archetype, tier, seed, group_id)
                report = quality_check(ctx, f"{style_id}/{name}")
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
            path, info = export.write_structure_nbt(ctx.grid, style_id, name)
            export.write_place_function(style_id, name)
            ctx.passes_run.append("resource_export_pass")
            report["export"] = info
            report["massing_graph"] = ctx.graph.to_summary_dict()
            report["passes_run"] = ctx.passes_run
            reports.append(report)
            entries.append({"name": name, "archetype": archetype,
                            "scale_tier": tier, "size": info["size"],
                            "group_id": group_id or ""})
            print(f"OK {name:24s} tier={tier:10s} size={info['size']} "
                  f"blocks={info['block_count']} seed={report['seed']}")

    gallery_path = export.write_gallery_function(style_id, entries)

    passed = sum(1 for r in reports if r["passed"])
    # Variant distinctness gate for the cultivation-town small/medium archetypes
    # (per-archetype silhouette spread >= 30, no byte-identical variant pair).
    # Runs post-export so the .nbt hashes exist; a failure fails this build.
    variant_distinctness = None
    distinctness_ok = True
    if group_id == "cultivation_town":
        variant_distinctness = cultivation_variant_distinctness(reports, STRUCTURE_DIR)
        distinctness_ok = variant_distinctness["passed"]
        if not distinctness_ok:
            for err in variant_distinctness["errors"]:
                print(f"FAIL {err}")
    summary = {
        "style_id": style_id,
        "group_id": group_id,
        "requested": requested,
        "generated": len(entries),
        "passed": passed,
        "failed": len(reports) - passed,
        "rejected_attempts": failed_attempts,
        "gallery_function": export.repo_relpath(gallery_path),
        "variant_distinctness": variant_distinctness,
        "reports": reports,
    }
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)
    print(f"\ngenerated {len(entries)}/{summary['requested']} buildings "
          f"({failed_attempts} rejected attempts)")
    print(f"report: {export.repo_relpath(report_path)}")
    print(f"gallery: {summary['gallery_function']}")
    if variant_distinctness is not None:
        gate = "PASS" if distinctness_ok else "FAIL"
        spreads = ", ".join(f"{a}={s}" for a, s in
                            sorted(variant_distinctness["spreads"].items()))
        print(f"variant distinctness {gate} (spread>="
              f"{variant_distinctness['min_spread']}: {spreads})")
    built_ok = len(entries) == summary["requested"]
    return 0 if (built_ok and distinctness_ok) else 1


if __name__ == "__main__":
    raise SystemExit(main())
