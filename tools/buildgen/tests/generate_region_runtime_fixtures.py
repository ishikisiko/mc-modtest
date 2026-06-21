"""Emit the Python⇄Java parity fixtures for the region-runtime-binding layer.

For a fixed list of fixture seeds (each chosen to exercise one structural case),
this dumps the canonical region graph JSON produced by
``tools/buildgen/region_topology.py`` together with the **spawn binding** the
runtime computes from that graph (the eligible region with the lowest assigned
tier, deterministic tie-break, translated to a world block by the placement
transform). The JUnit parity test
``src/test/java/com/example/myvillage/region/runtime/RegionRuntimeParityTest.java``
reads these files and asserts the Java port of the generator reproduces every
graph **byte-identically** and derives the same spawn binding.

Regenerate after changing ``region_topology.py`` or the placement transform::

    python3 tools/buildgen/tests/generate_region_runtime_fixtures.py

Fixture-seed selection (deterministic search over ascending positive seeds):

- ``min_count``  — a seed whose generated graph hits the ruleset's minimum
  region count (5).
- ``max_count``  — a seed hitting the maximum region count (7).
- ``tier_tie``   — a seed where two non-walled regions share the same assigned
  tier (a branch point the alignment system will later resolve).
- ``walled_low`` — a seed where the walled region's assigned tier is the global
  minimum, so the spawn selector is proven to exclude ``walled`` regions even
  when one would otherwise win the sort.
- ``shipped``    — the shipped example seed ``20260620``.

The fixture format (one file per seed under ``src/test/resources/
region_runtime_fixtures/seed_<n>.json``)::

    {
      "case": "min_count",
      "seed": 1234,
      "placement": {
        "radius_world": 4000,
        "radius_graph_outer": 1.45,
        "scale": 2758.6206896551726
      },
      "spawn": {
        "region_id": "donghai",
        "graph_center": [0.309, -0.9511],
        "world_block": [883, -2717]
      },
      "graph": { ...the canonical graph.to_dict() payload... }
    }

The spawn ``world_block`` is the spawn region's graph center translated by the
pure-math placement transform (``world = SCALE * graph``, block = rounded int).
The runtime's safe-surface search (spiral for a standable block, world-dependent)
is intentionally NOT part of the fixture — it is not deterministic across world
terrain and is exercised separately by tasks 4.4 / 8.3.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any, Dict, List, Mapping

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.region_topology import (  # noqa: E402
    DEFAULT_CATALOG_DIR,
    DEFAULT_RULESET_PATH,
    RegionGraph,
    generate,
    load_catalog_dir,
    load_ruleset,
)

# Script lives at <repo>/tools/buildgen/tests/; repo root is parents[3].
REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE_DIR = REPO_ROOT / "src" / "test" / "resources" / "region_runtime_fixtures"

# Placement transform — single source documented in
# ``docs/ai-kb/13_region_topology.md`` and mirrored as Java constants in the
# runtime package (``RegionPlacement``).
RADIUS_WORLD = 4000
# Effective outermost center radius: the walled ring (1.4) plus the maximum
# embed jitter (+0.05), so EVERY region center — including the walled 魔域,
# whose deterministic radius jitter can push it to 1.45 — fits within
# RADIUS_WORLD. Using the nominal walled ring (1.4) alone would let walled
# centers overshoot the 4000-block bound by ~3.5%; the +jitter term closes
# that gap. The ``region-runtime-binding`` spec mandates the 4000 bound for
# every region center, so the effective (jitter-inclusive) radius is the
# correct divisor.
EMBED_JITTER_MAX = 0.05
RADIUS_GRAPH_OUTER_DEFAULT = 1.4 + EMBED_JITTER_MAX
REGION_SCALE = RADIUS_WORLD / RADIUS_GRAPH_OUTER_DEFAULT

# Maximum number of seeds to scan when looking for each structural case.
SEED_SEARCH_CAP = 200_000

SHIPPED_SEED = 20260620


def _to_world_block(graph_x: float, graph_z: float) -> List[int]:
    return [int(round(REGION_SCALE * graph_x)), int(round(REGION_SCALE * graph_z))]


def _spawn_binding(graph: RegionGraph) -> Dict[str, Any]:
    """Compute the spawn binding the runtime derives from ``graph``.

    Mirrors the spawn-region selector in the runtime spec: filter
    ``role != "walled"`` AND ``admitted_subjects`` non-empty, sort by
    ``(assigned_tier ASC, distance_from_anchor DESC, qi_midpoint ASC, id ASC)``,
    take the first. ``qi_midpoint`` is encoded as the int sum ``qi[0]+qi[1]``
    (orders identically to the float midpoint). The result is the spawn region
    id and its placed world block (pure-math transform; the runtime's
    safe-surface search resolves the ``y`` later and is not part of this
    fixture).
    """
    anchor = next(r for r in graph.regions if r.role == "anchor")

    def sort_key(r: Any) -> Any:
        dx = r.position[0] - anchor.position[0]
        dz = r.position[1] - anchor.position[1]
        dist = math.hypot(dx, dz)
        qi_mid = r.qi[0] + r.qi[1]
        return (r.tier, _desc(dist), qi_mid, r.id)

    eligible = [r for r in graph.regions if r.role != "walled" and r.admitted_subjects]
    chosen = min(eligible, key=sort_key)
    return {
        "region_id": chosen.id,
        "graph_center": [chosen.position[0], chosen.position[1]],
        "world_block": _to_world_block(chosen.position[0], chosen.position[1]),
    }


class _Desc:
    """A reverse-order wrapper so a single tuple sort key can mix ASC/DESC.

    ``sort_key`` needs ``distance DESC`` while the other fields are ASC; rather
    than run two passes we negate via a comparator wrapper. Only used on floats
    here, so the negation is exact.
    """

    __slots__ = ("value",)

    def __init__(self, value: float) -> None:
        self.value = value

    def __lt__(self, other: "_Desc") -> bool:
        return self.value > other.value

    def __eq__(self, other: object) -> bool:
        return isinstance(other, _Desc) and self.value == other.value


def _desc(value: float) -> _Desc:
    return _Desc(value)


def _has_tier_tie(graph: RegionGraph) -> bool:
    tiers: Dict[int, int] = {}
    for r in graph.regions:
        if r.role == "walled":
            continue
        tiers[r.tier] = tiers.get(r.tier, 0) + 1
    return any(c >= 2 for c in tiers.values())


def _is_walled_lowest(graph: RegionGraph) -> bool:
    walled = [r for r in graph.regions if r.role == "walled"]
    if not walled:
        return False
    wmin = min(r.tier for r in walled)
    gmin = min(r.tier for r in graph.regions)
    return wmin <= gmin


def _find_seed_for_case(
    ruleset: Any,
    catalog: List[Any],
    predicate,
    skip: set,
) -> int:
    for seed in range(1, SEED_SEARCH_CAP):
        if seed in skip:
            continue
        graph = generate(seed, ruleset, catalog)
        if predicate(graph):
            return seed
    raise RuntimeError(
        f"no seed < {SEED_SEARCH_CAP} satisfied the requested fixture case"
    )


def _build_case_plan(
    ruleset: Any, catalog: List[Any]
) -> List[tuple]:
    lo, hi = ruleset.region_count
    plan: List[tuple] = []
    chosen: set = set()

    min_seed = _find_seed_for_case(
        ruleset, catalog, lambda g: g.count == lo, chosen)
    chosen.add(min_seed)
    plan.append(("min_count", min_seed))

    max_seed = _find_seed_for_case(
        ruleset, catalog, lambda g: g.count == hi, chosen)
    chosen.add(max_seed)
    plan.append(("max_count", max_seed))

    tie_seed = _find_seed_for_case(
        ruleset, catalog, lambda g: _has_tier_tie(g), chosen)
    chosen.add(tie_seed)
    plan.append(("tier_tie", tie_seed))

    walled_seed = _find_seed_for_case(
        ruleset, catalog, lambda g: _is_walled_lowest(g), chosen)
    chosen.add(walled_seed)
    plan.append(("walled_low", walled_seed))

    chosen.add(SHIPPED_SEED)
    plan.append(("shipped", SHIPPED_SEED))
    return plan


def _fixture_for_seed(case: str, seed: int, ruleset: Any, catalog: List[Any]) -> Dict[str, Any]:
    graph = generate(seed, ruleset, catalog)
    return {
        "case": case,
        "seed": seed,
        "placement": {
            "radius_world": RADIUS_WORLD,
            "radius_graph_outer": RADIUS_GRAPH_OUTER_DEFAULT,
            "scale": REGION_SCALE,
        },
        "spawn": _spawn_binding(graph),
        "graph": graph.to_dict(),
    }


def _write_canonical(path: Path, payload: Mapping[str, Any]) -> None:
    # Canonical form: indent=2, sorted keys, no ensure_ascii reordering of the
    # CJK glyphs (they ship as UTF-8 so the file diff stays readable and the
    # Java side's canonical serializer reproduces this exact byte stream).
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True)
    path.write_text(text + "\n", encoding="utf-8")


def build_fixtures() -> List[tuple]:
    ruleset = load_ruleset(DEFAULT_RULESET_PATH)
    catalog = load_catalog_dir(DEFAULT_CATALOG_DIR)
    plan = _build_case_plan(ruleset, catalog)
    FIXTURE_DIR.mkdir(parents=True, exist_ok=True)
    summary: List[tuple] = []
    for case, seed in plan:
        payload = _fixture_for_seed(case, seed, ruleset, catalog)
        path = FIXTURE_DIR / f"seed_{seed}.json"
        _write_canonical(path, payload)
        summary.append((case, seed, payload["spawn"]["region_id"], path))
    index = {
        "generator": "tools/buildgen/tests/generate_region_runtime_fixtures.py",
        "note": (
            "Python⇄Java parity fixtures for the region runtime. Each seed_*.json "
            "holds the canonical graph + spawn binding; the Java port must "
            "reproduce both byte-identically."
        ),
        "placement": {
            "radius_world": RADIUS_WORLD,
            "radius_graph_outer": RADIUS_GRAPH_OUTER_DEFAULT,
            "scale": REGION_SCALE,
        },
        "cases": [
            {
                "case": case,
                "seed": seed,
                "file": f"seed_{seed}.json",
                "spawn_region": spawn_region,
            }
            for case, seed, spawn_region, _ in summary
        ],
    }
    # ``index.json`` is informational; the parity test enumerates ``seed_*.json``
    # directly so adding a case never requires touching the test.
    _write_canonical(FIXTURE_DIR / "index.json", index)
    return summary


def main() -> int:
    summary = build_fixtures()
    for case, seed, spawn_region, path in summary:
        print(f"  {case:11s} seed={seed:<8d} spawn={spawn_region} -> {path}")
    print(
        f"wrote {len(summary)} region runtime fixtures under {FIXTURE_DIR} "
        f"(scale={REGION_SCALE:.6f})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
