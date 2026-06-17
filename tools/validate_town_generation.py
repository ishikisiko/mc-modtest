#!/usr/bin/env python3
"""Validate the deterministic living-town planner and structural invariants."""

from __future__ import annotations

import argparse
import copy
import json
import os
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.groups import get_group  # noqa: E402
from buildgen.town import (  # noqa: E402
    TownSite,
    estimate_block_budget,
    generate_town_plan,
    validate_realized_town,
    validate_town_plan,
)

REPORT_PATH = REPO_ROOT / "reports" / "town_generation_validation.json"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seeds", default="20260618,20260719,20260820")
    parser.add_argument("--width", type=int, default=160)
    parser.add_argument("--depth", type=int, default=160)
    args = parser.parse_args()

    from buildgen.town import MAX_FOOTPRINT_AXIS, MIN_FOOTPRINT_AXIS
    group = get_group("cultivation_town")
    brief = list(group.scale_params.get("district_brief", []))
    seeds = [int(s.strip()) for s in args.seeds.split(",") if s.strip()]
    errors = []
    results = []
    for seed in seeds:
        plan = generate_town_plan(seed, TownSite(args.width, args.depth), brief)
        plan_report = validate_town_plan(plan)
        realized_report = validate_realized_town(plan)
        budget = estimate_block_budget(plan)
        if not plan_report["passed"]:
            errors.append(f"plan_failed:{seed}:{plan_report['errors']}")
        if not realized_report["passed"]:
            errors.append(f"realized_failed:{seed}:{realized_report['errors']}")
        if not budget["bounded"]:
            errors.append(f"budget_unbounded:{seed}:{budget}")
        results.append({
            "seed": seed,
            "plan": plan_report,
            "realized": realized_report,
            "budget": budget,
        })
        status = "OK" if plan_report["passed"] and realized_report["passed"] and budget["bounded"] else "FAIL"
        print(f"{status} town seed={seed} budget={budget['total_budget']} parcels={plan_report['stats']['parcel_count']}")

    sloped = generate_town_plan(seeds[0] + 77, TownSite(args.width, args.depth, base_y=72, max_slope=5), brief)
    sloped_report = validate_realized_town(sloped)
    if not sloped_report["passed"]:
        errors.append(f"sloped_site_failed:{sloped_report['errors']}")
    print(("OK" if sloped_report["passed"] else "FAIL") + " sloped-site structural validation")

    broken = copy.deepcopy(generate_town_plan(seeds[0], TownSite(args.width, args.depth), brief))
    broken_cell = next(iter(broken.wall_cells))
    broken.wall_cells.remove(broken_cell)
    broken.perimeter.remove(broken_cell)
    broken_report = validate_town_plan(broken)
    if broken_report["passed"]:
        errors.append("broken_plan_unexpectedly_passed")
        print("FAIL broken-plan self-check")
    else:
        print(f"OK broken-plan self-check failed with {broken_report['errors'][0]}")

    # Determinism: same seed + site yields identical district partition.
    determA = generate_town_plan(seeds[0], TownSite(args.width, args.depth), brief)
    determB = generate_town_plan(seeds[0], TownSite(args.width, args.depth), brief)
    if [(d.id, d.kind, d.bounds) for d in determA.districts] != \
            [(d.id, d.kind, d.bounds) for d in determB.districts]:
        errors.append("district_partition_not_deterministic")
        print("FAIL determinism self-check")
    else:
        print("OK district partition is deterministic per seed+site")

    # Determinism: same seed + site yields identical civic-precinct framing
    # (wall, precinct/spine gates, spirit way, colonnade, side-hall parcels).
    precinct_keys = ("precinct_gate_cells", "spirit_way_cells", "colonnade_cells",
                     "precinct_wall_cells", "precinct_side_gate_cells")
    precinctA = {k: determA.ritual_axis.get(k) for k in precinct_keys}
    precinctB = {k: determB.ritual_axis.get(k) for k in precinct_keys}
    sideA = [(p.id, p.bounds, p.importance_tier)
             for p in determA.parcels if p.id.startswith("civic_side_hall")]
    sideB = [(p.id, p.bounds, p.importance_tier)
             for p in determB.parcels if p.id.startswith("civic_side_hall")]
    if precinctA != precinctB or sideA != sideB:
        errors.append("precinct_framing_not_deterministic")
        print("FAIL precinct determinism self-check")
    else:
        print("OK civic-precinct framing is deterministic per seed+site")

    # Oversize (> cap) must be rejected by the planner itself.
    oversize_axis = MAX_FOOTPRINT_AXIS + 16
    try:
        generate_town_plan(seeds[0], TownSite(oversize_axis, oversize_axis), brief)
        errors.append("oversize_plan_not_rejected")
        print("FAIL oversize rejection self-check")
    except ValueError:
        print("OK oversize plan rejected by planner")

    # Undersize (< min axis) must be rejected by the planner.
    try:
        generate_town_plan(seeds[0], TownSite(MIN_FOOTPRINT_AXIS - 8, MIN_FOOTPRINT_AXIS - 8), brief)
        errors.append("undersize_plan_not_rejected")
        print("FAIL undersize rejection self-check")
    except ValueError:
        print("OK undersize plan rejected")

    summary = {
        "passed": not errors,
        "errors": errors,
        "results": results,
        "sloped_site": sloped_report,
        "broken_plan": broken_report,
        "footprint_cap": {"max_axis": 160, "min_axis": 96, "oversize_rejected": True, "undersize_rejected": True},
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with REPORT_PATH.open("w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"report: {REPORT_PATH.relative_to(REPO_ROOT)}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
