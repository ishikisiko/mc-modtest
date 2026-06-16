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
    parser.add_argument("--width", type=int, default=96)
    parser.add_argument("--depth", type=int, default=80)
    args = parser.parse_args()

    group = get_group("cultivation_town")
    brief = dict(group.scale_params.get("soft_functional_brief", {}))
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
        print(f"{status} town seed={seed} budget={budget['total_budget']}")

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

    oversize = generate_town_plan(seeds[0], TownSite(128, 128), brief)
    oversize_budget = estimate_block_budget(oversize)
    if oversize_budget["bounded"]:
        errors.append("oversize_plan_not_reported_by_budget")
    print(("OK" if not oversize_budget["bounded"] else "FAIL") + " oversize budget check")

    summary = {
        "passed": not errors,
        "errors": errors,
        "results": results,
        "sloped_site": sloped_report,
        "broken_plan": broken_report,
        "oversize_budget": oversize_budget,
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with REPORT_PATH.open("w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"report: {REPORT_PATH.relative_to(REPO_ROOT)}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
