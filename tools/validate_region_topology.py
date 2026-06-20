#!/usr/bin/env python3
"""Validate generated region-topology graphs: structural invariants + determinism.

Reuses the shared model/generator in ``buildgen.region_topology`` so generation
and validation agree on one source. For each seed the validator checks:

- region count is within ``[5, 7]`` inclusive;
- exactly one ``anchor`` region exists and is centered at ``(0, 0)``;
- the anchor holds the (strictly) highest tier in the graph;
- the 连-subgraph connects every non-walled region to the anchor;
- every 连 edge respects the tier-step limit ``N = tier_step``;
- each 隔 edge carries a separator from the legal palette
  ``{特殊山脉, 特殊海洋}``;
- each ``walled`` region has at most one 连 edge (its 关隘), with every other
  incident edge 隔, and any retained 连 edge is marked as a pass;
- the same seed reproduces an identical graph (determinism).

A deliberately broken graph SHALL fail with the offending invariant named. An
unsatisfiable ruleset SHALL be reported explicitly (it raises, never loops).

Writes the multi-seed survey to ``reports/region_topology_validation.json``.
Exits non-zero if any seeded graph, the determinism check, or a deliberate-break
assertion fails.
"""

from __future__ import annotations

import argparse
import copy
import json
import sys
from collections import deque
from pathlib import Path
from typing import Any, Dict, List, Mapping, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.region_topology import (  # noqa: E402
    DEFAULT_CATALOG_DIR,
    DEFAULT_RULESET_PATH,
    EDGE_GE,
    EDGE_LIAN,
    PASS_GUANAI,
    RegionGraph,
    Ruleset,
    UnsatisfiableRuleset,
    assert_deterministic,
    generate,
    graph_from_dict,
    load_catalog_dir,
    load_ruleset,
)

REPORT_PATH = REPO_ROOT / "reports" / "region_topology_validation.json"


def check_graph(graph: RegionGraph, ruleset: Ruleset) -> Dict[str, Any]:
    """Run every structural invariant on ``graph``. Returns a report dict.

    Each failure appends an ``"<invariant>: <detail>"`` string to ``errors`` so
    a broken graph names the offending invariant.
    """
    errors: List[str] = []
    n = ruleset.tier_step
    palette = set(ruleset.separator_palette)

    by_id = {r.id: r for r in graph.regions}

    # --- region_count ---
    lo, hi = ruleset.region_count
    if not (lo <= len(graph.regions) <= hi):
        errors.append(f"region_count: {len(graph.regions)} outside [{lo}, {hi}]")

    # --- single anchor, centered ---
    anchors = [r for r in graph.regions if r.role == "anchor"]
    if len(anchors) != 1:
        errors.append(f"anchor_count: expected exactly 1 anchor, found {len(anchors)}")
    if anchors:
        a = anchors[0]
        if tuple(a.position) != (0.0, 0.0):
            errors.append(
                f"anchor_centered: anchor {a.id} at {a.position}, expected (0.0, 0.0)"
            )
    else:
        a = None

    # --- anchor holds the strictly-highest tier ---
    if graph.regions:
        top = max(r.tier for r in graph.regions)
        top_holders = [r.id for r in graph.regions if r.tier == top]
        if a is not None and a.tier != top:
            errors.append(
                f"anchor_top_tier: anchor tier {a.tier} != graph top {top}"
            )
        if len(top_holders) != 1:
            errors.append(
                f"anchor_unique_top: tier {top} held by {top_holders}; "
                "anchor must be the sole highest"
            )

    # --- edge typing sanity + tier-step on 连 + separator palette on 隔 ---
    for e in graph.edges:
        if e.type == EDGE_LIAN:
            dt = abs(by_id[e.a].tier - by_id[e.b].tier)
            if dt > n:
                errors.append(
                    f"tier_step: 连 edge {e.a}--{e.b} |Δtier|={dt} > N={n}"
                )
        elif e.type == EDGE_GE:
            if e.separator not in palette:
                errors.append(
                    f"separator_palette: 隔 edge {e.a}--{e.b} separator "
                    f"{e.separator!r} not in {sorted(palette)}"
                )
        else:
            errors.append(f"edge_type: {e.a}--{e.b} unknown type {e.type!r}")

    # --- 连-subgraph connects every non-walled region to the anchor ---
    non_walled = {r.id for r in graph.regions if r.role != "walled"}
    adj: Dict[str, set] = {rid: set() for rid in by_id}
    for e in graph.edges:
        if e.type == EDGE_LIAN:
            adj[e.a].add(e.b)
            adj[e.b].add(e.a)
    seen: set = set()
    if a is not None:
        seen = {a.id}
        queue = deque([a.id])
        while queue:
            u = queue.popleft()
            for v in adj[u]:
                if v in non_walled and v not in seen:
                    seen.add(v)
                    queue.append(v)
        unreachable = sorted(non_walled - seen)
        if unreachable:
            errors.append(
                f"connectivity: non-walled regions unreachable from anchor: {unreachable}"
            )

    # --- walled region rule: <=1 连 (关隘), rest 隔; any 连 marked as pass ---
    walled = [r for r in graph.regions if r.role == "walled"]
    walled_with_pass = 0
    for r in walled:
        inc = [e for e in graph.edges if r.id in (e.a, e.b)]
        lian = [e for e in inc if e.type == EDGE_LIAN]
        if len(lian) > 1:
            errors.append(
                f"walled_pass_count: walled {r.id} has {len(lian)} 连 edges "
                f"(> 1 关隘)"
            )
        for e in lian:
            if not e.is_pass:
                errors.append(
                    f"walled_pass_unmarked: walled {r.id} 连 edge {e.a}--{e.b} "
                    "not marked as 关隘"
                )
        if lian:
            walled_with_pass += 1

    tiers = [r.tier for r in graph.regions]
    stats = {
        "count": len(graph.regions),
        "anchor": a.id if a else None,
        "anchor_tier": a.tier if a else None,
        "top_tier": max(tiers) if tiers else None,
        "min_tier": min(tiers) if tiers else None,
        "max_tier": max(tiers) if tiers else None,
        "non_walled_reachable": (a is not None) and (not (non_walled - seen)) if graph.regions else True,
        "walled_count": len(walled),
        "walled_with_pass": walled_with_pass,
        "lian_edges": sum(1 for e in graph.edges if e.type == EDGE_LIAN),
        "ge_edges": sum(1 for e in graph.edges if e.type == EDGE_GE),
        "separators_used": sorted({e.separator for e in graph.edges if e.type == EDGE_GE}),
    }
    return {"passed": not errors, "errors": errors, "stats": stats}


# --------------------------------------------------------------------------- #
# Deliberate-break cases: each mutates a known-good graph to break exactly one
# invariant, then asserts the validator names it.
# --------------------------------------------------------------------------- #


def _base_graph(ruleset: Ruleset, catalog) -> RegionGraph:
    return generate(12345, ruleset, catalog)


def deliberate_breaks(ruleset: Ruleset, catalog) -> List[Dict[str, Any]]:
    cases: List[Dict[str, Any]] = []

    def case(name: str, mutate) -> None:
        g = _base_graph(ruleset, catalog)
        good = check_graph(g, ruleset)
        if not good["passed"]:
            raise RuntimeError(f"base graph for break {name!r} is not clean: {good['errors']}")
        broken = mutate(copy.deepcopy(g))
        report = check_graph(broken, ruleset)
        named = name in " ".join(report["errors"])
        cases.append(
            {
                "break": name,
                "passed_before": True,
                "caught": (not report["passed"]),
                "named_correctly": named,
                "errors": report["errors"],
            }
        )

    # 1. count out of range: drop enough regions to fall below 5.
    def b_count(g: RegionGraph) -> RegionGraph:
        while len(g.regions) > 4:
            g.regions.pop()
        g.count = len(g.regions)
        # keep edges consistent with survivors
        survivors = {r.id for r in g.regions}
        g.edges = [e for e in g.edges if e.a in survivors and e.b in survivors]
        return g

    case("region_count", b_count)

    # 2. zero anchors.
    def b_no_anchor(g: RegionGraph) -> RegionGraph:
        for r in g.regions:
            if r.role == "anchor":
                r.role = "peripheral"
        return g

    case("anchor_count", b_no_anchor)

    # 3. anchor off-center.
    def b_anchor_pos(g: RegionGraph) -> RegionGraph:
        for r in g.regions:
            if r.role == "anchor":
                r.position = (1.0, 1.0)
        return g

    case("anchor_centered", b_anchor_pos)

    # 4. anchor not strictly top: push a peripheral above it.
    def b_anchor_top(g: RegionGraph) -> RegionGraph:
        anchor = next(r for r in g.regions if r.role == "anchor")
        peer = next(r for r in g.regions if r.role != "walled" and r.id != anchor.id)
        peer.tier = anchor.tier + 1
        return g

    case("anchor_top_tier", b_anchor_top)

    # 5. 连 edge tier-step violation: push one endpoint's tier down so the
    #    gap exceeds N (down, not up, so it stays below the anchor's top tier).
    def b_tier_step(g: RegionGraph) -> RegionGraph:
        e = next(e for e in g.edges if e.type == EDGE_LIAN)
        a_tier = next(r.tier for r in g.regions if r.id == e.a)
        target = next(r for r in g.regions if r.id == e.b)
        target.tier = a_tier - (ruleset.tier_step + 1)
        return g

    case("tier_step", b_tier_step)

    # 6. disconnect: isolate one non-anchor, non-walled region by flipping
    #    ALL its 连 edges to 隔. The 连-graph is dense (the anchor hubs many
    #    non-tree 连 edges), so a single-edge flip is rarely a bridge; removing
    #    every 连 edge incident to one region guarantees it is unreachable.
    def b_connectivity(g: RegionGraph) -> RegionGraph:
        victim = next(
            r for r in g.regions if r.role == "peripheral"
        )
        for e in g.edges:
            if e.type == EDGE_LIAN and victim.id in (e.a, e.b):
                e.type = EDGE_GE
                e.separator = ruleset.separator_palette[0]
        return g

    case("connectivity", b_connectivity)

    # 7. 隔 edge with illegal separator.
    def b_separator(g: RegionGraph) -> RegionGraph:
        e = next(e for e in g.edges if e.type == EDGE_GE)
        e.separator = "虚空裂隙"
        return g

    case("separator_palette", b_separator)

    # 8. walled region with two 连 (关隘) edges.
    # 8. walled region with two 连 (关隘) edges. The base walled region may be
    #    sealed (0 passes) or have one; force it to exactly two so the > 1
    #    invariant fires regardless of the seed's gate draw.
    def b_walled_pass(g: RegionGraph) -> RegionGraph:
        from buildgen.region_topology import GenEdge

        w = next(r for r in g.regions if r.role == "walled")
        lian = [e for e in g.edges if e.type == EDGE_LIAN and w.id in (e.a, e.b)]
        ge = [e for e in g.edges if e.type == EDGE_GE and w.id in (e.a, e.b)]
        need = 2 - len(lian)
        for e in ge[:need]:
            e.type = EDGE_LIAN
            e.separator = None
            e.is_pass = True
        still_need = 2 - (len(lian) + min(need, len(ge)))
        if still_need > 0:
            other = next(r for r in g.regions if r.id != w.id)
            g.edges.append(GenEdge(a=w.id, b=other.id, type=EDGE_LIAN, is_pass=True))
        return g

    case("walled_pass_count", b_walled_pass)

    return cases


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--seeds",
        default="20260620,20260721,20260822,20260923,20261024,20261125",
        help="comma-separated seeds to survey (default: the preview seed set)",
    )
    parser.add_argument("--ruleset", type=Path, default=DEFAULT_RULESET_PATH)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG_DIR)
    parser.add_argument("--report", type=Path, default=REPORT_PATH)
    args = parser.parse_args()

    ruleset = load_ruleset(args.ruleset)
    catalog = load_catalog_dir(args.catalog)
    seeds = [int(s.strip()) for s in args.seeds.split(",") if s.strip()]

    per_seed: List[Dict[str, Any]] = []
    count_dist: Dict[int, int] = {}
    all_separators: set = set()
    tier_spread: Dict[str, int] = {"min": 10 ** 9, "max": -1}
    walled_presence = 0
    failures: List[str] = []

    for seed in seeds:
        graph = generate(seed, ruleset, catalog)
        report = check_graph(graph, ruleset)
        per_seed.append({"seed": seed, "report": report})
        count_dist[graph.count] = count_dist.get(graph.count, 0) + 1
        all_separators.update(report["stats"]["separators_used"])
        tier_spread["min"] = min(tier_spread["min"], report["stats"]["min_tier"])
        tier_spread["max"] = max(tier_spread["max"], report["stats"]["max_tier"])
        if report["stats"]["walled_count"] > 0:
            walled_presence += 1
        status = "OK" if report["passed"] else "FAIL"
        print(
            f"{status} region_topology seed={seed} count={graph.count} "
            f"连={report['stats']['lian_edges']} 隔={report['stats']['ge_edges']} "
            f"walled={report['stats']['walled_count']}"
        )
        if not report["passed"]:
            failures.append(f"seed:{seed}:{report['errors']}")

    # --- determinism: regenerate every seed twice and compare byte-for-byte ---
    det_ok = True
    det_detail: List[str] = []
    for seed in seeds:
        try:
            assert_deterministic(seed, ruleset, catalog)
        except AssertionError as exc:
            det_ok = False
            det_detail.append(f"seed:{seed}:{exc}")
    print(("OK" if det_ok else "FAIL") + " determinism (same seed -> identical graph)")

    # --- deliberate breaks: each must be caught and named correctly ---
    breaks = deliberate_breaks(ruleset, catalog)
    for b in breaks:
        ok = b["caught"] and b["named_correctly"]
        print(
            ("OK " if ok else "FAIL ")
            + f"break {b['break']}: caught={b['caught']} named={b['named_correctly']}"
        )
        if not ok:
            failures.append(f"break:{b['break']}: errors={b['errors']}")

    # --- unsatisfiable ruleset: reported explicitly (raises), never loops ---
    unsat_ok = False
    unsat_msg = ""
    no_anchor = [r for r in catalog if r.role != "anchor"]
    try:
        generate(seeds[0], ruleset, no_anchor)
    except UnsatisfiableRuleset as exc:
        unsat_ok = True
        unsat_msg = str(exc)
    print(("OK" if unsat_ok else "FAIL") + " unsatisfiable ruleset reported explicitly")
    if not unsat_ok:
        failures.append("unsatisfiable ruleset was not reported")

    survey = {
        "ruleset": ruleset.id,
        "tier_step": ruleset.tier_step,
        "region_count_range": list(ruleset.region_count),
        "tier_range": list(ruleset.tier_range),
        "separator_palette": list(ruleset.separator_palette),
        "seeds": seeds,
        "count_distribution": dict(sorted(count_dist.items())),
        "tier_spread": tier_spread,
        "separators_observed": sorted(all_separators),
        "walled_region_presence": {
            "seeds_with_walled": walled_presence,
            "seeds_total": len(seeds),
        },
        "connectivity": {
            "all_non_walled_reachable": all(
                s["report"]["stats"]["non_walled_reachable"] for s in per_seed
            )
        },
        "determinism": {"passed": det_ok, "details": det_detail},
        "deliberate_breaks": breaks,
        "unsatisfiable_ruleset": {"reported": unsat_ok, "message": unsat_msg},
        "per_seed": per_seed,
    }

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(survey, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"wrote {args.report.relative_to(REPO_ROOT)}")

    if failures:
        print(f"\n{len(failures)} FAILURE(S):")
        for f in failures:
            print("  " + f)
        return 1
    print("\nall region-topology invariants hold across surveyed seeds")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
