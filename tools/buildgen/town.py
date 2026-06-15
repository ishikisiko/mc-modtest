"""Deterministic town-plan model and validators.

The runtime Java town command mirrors this small heuristic planner. The Python
version exists for offline validation, JSON dumps, and top-down previews.
"""

from __future__ import annotations

import json
import random
from collections import deque
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

Cell2 = Tuple[int, int]

MAX_IMPORTANCE_TIER = 3


@dataclass(frozen=True)
class TownSite:
    width: int = 64
    depth: int = 56
    base_y: int = 64
    max_slope: int = 5

    def contains(self, cell: Cell2) -> bool:
        x, z = cell
        return 0 <= x < self.width and 0 <= z < self.depth


@dataclass(frozen=True)
class TownGate:
    id: str
    side: str
    cells: Tuple[Cell2, ...]


@dataclass(frozen=True)
class TownParcel:
    id: str
    role: str
    bounds: Tuple[int, int, int, int]
    importance_tier: int
    ground_ref: int
    roof_grade_hint: str
    height_hint: str
    dominant_landmark: bool = False

    @property
    def cells(self) -> Set[Cell2]:
        x0, z0, x1, z1 = self.bounds
        return _rect(x0, z0, x1, z1)

    @property
    def center(self) -> Cell2:
        x0, z0, x1, z1 = self.bounds
        return ((x0 + x1) // 2, (z0 + z1) // 2)


@dataclass(frozen=True)
class NegativeSpace:
    id: str
    kind: str
    bounds: Tuple[int, int, int, int]
    density_rank: int

    @property
    def cells(self) -> Set[Cell2]:
        return _rect(*self.bounds)


@dataclass
class TownPlan:
    seed: int
    site: TownSite
    perimeter: Set[Cell2]
    wall_cells: Set[Cell2]
    gates: List[TownGate]
    spine: Set[Cell2]
    lane_cells: Set[Cell2]
    parcels: List[TownParcel]
    negative_spaces: List[NegativeSpace]
    soft_brief: Dict[str, int] = field(default_factory=dict)

    @property
    def street_cells(self) -> Set[Cell2]:
        return set(self.spine) | set(self.lane_cells)

    @property
    def parcel_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for parcel in self.parcels:
            cells.update(parcel.cells)
        return cells

    @property
    def negative_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for region in self.negative_spaces:
            cells.update(region.cells)
        return cells

    def to_dict(self) -> dict:
        return {
            "seed": self.seed,
            "site": asdict(self.site),
            "perimeter": _cells_to_json(self.perimeter),
            "wall_cells": _cells_to_json(self.wall_cells),
            "gates": [
                {"id": g.id, "side": g.side, "cells": _cells_to_json(g.cells)}
                for g in self.gates
            ],
            "spine": _cells_to_json(self.spine),
            "lane_cells": _cells_to_json(self.lane_cells),
            "parcels": [
                {
                    **asdict(parcel),
                    "cells": _cells_to_json(parcel.cells),
                }
                for parcel in self.parcels
            ],
            "negative_spaces": [
                {
                    **asdict(region),
                    "cells": _cells_to_json(region.cells),
                }
                for region in self.negative_spaces
            ],
            "soft_brief": dict(self.soft_brief),
        }


def _rect(x0: int, z0: int, x1: int, z1: int) -> Set[Cell2]:
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


def _cells_to_json(cells: Iterable[Cell2]) -> List[List[int]]:
    return [[x, z] for x, z in sorted(cells)]


def _boundary(site: TownSite) -> Set[Cell2]:
    cells = {(x, 0) for x in range(site.width)}
    cells |= {(x, site.depth - 1) for x in range(site.width)}
    cells |= {(0, z) for z in range(site.depth)}
    cells |= {(site.width - 1, z) for z in range(site.depth)}
    return cells


def _importance_hint(tier: int) -> Tuple[str, str]:
    if tier >= 3:
        return "dominant", "landmark"
    if tier == 2:
        return "tall", "fine"
    if tier == 1:
        return "base", "standard"
    return "low", "plain"


def _role_sequence(rng: random.Random, soft_brief: Dict[str, int]) -> List[str]:
    weighted = []
    for role, count in sorted((soft_brief or {}).items()):
        weighted.extend([role] * max(0, int(count)))
    if not weighted:
        weighted = ["housing"] * 5 + ["market"] * 3 + ["civic"] + ["defense"]
    rng.shuffle(weighted)
    return weighted


def generate_town_plan(seed: int, site: Optional[TownSite] = None,
                       soft_brief: Optional[Dict[str, int]] = None) -> TownPlan:
    site = site or TownSite()
    if site.width < 44 or site.depth < 40:
        raise ValueError(f"site too small for default town: {site.width}x{site.depth}")

    rng = random.Random(seed)
    soft_brief = dict(soft_brief or {})
    perimeter = _boundary(site)
    cx = site.width // 2
    gate_half = 2
    south_gate_cells = tuple((x, 0) for x in range(cx - gate_half, cx + gate_half + 1))
    north_gate_cells = tuple((x, site.depth - 1) for x in range(cx - gate_half, cx + gate_half + 1))
    gates = [
        TownGate("south_gate", "south", south_gate_cells),
        TownGate("north_gate", "north", north_gate_cells),
    ]
    gate_cells = set(south_gate_cells) | set(north_gate_cells)
    wall_cells = perimeter - gate_cells

    spine_width = 5
    spine = {
        (x, z)
        for x in range(cx - spine_width // 2, cx + spine_width // 2 + 1)
        for z in range(0, site.depth)
    }
    lane_z = site.depth // 2 + rng.choice((-2, 0, 2))
    lane_cells = {
        (x, z)
        for x in range(5, site.width - 5)
        for z in range(lane_z - 1, lane_z + 2)
    } - spine
    for cross_z in (11, site.depth - 11):
        lane_cells |= {
            (x, z)
            for x in range(5, site.width - 5)
            for z in range(cross_z - 1, cross_z + 2)
        } - spine

    role_pool = _role_sequence(rng, soft_brief)
    parcels: List[TownParcel] = []
    landmark_bounds = (cx - 11, lane_z + 2, cx - 3, lane_z + 10)
    height, roof = _importance_hint(MAX_IMPORTANCE_TIER)
    parcels.append(TownParcel(
        "landmark_temple",
        "civic",
        landmark_bounds,
        MAX_IMPORTANCE_TIER,
        site.base_y,
        roof,
        height,
        dominant_landmark=True,
    ))

    side_specs = [
        ("west_core", 6, lane_z - 9, 18, lane_z - 2, 2),
        ("east_core", site.width - 19, lane_z - 9, site.width - 7, lane_z - 2, 2),
        ("west_market", 6, lane_z + 2, 18, lane_z + 10, 2),
        ("east_market", site.width - 19, lane_z + 2, site.width - 7, lane_z + 10, 2),
        ("west_outer_south", 6, 5, 18, 9, 1),
        ("east_outer_south", site.width - 19, 5, site.width - 7, 9, 1),
        ("west_outer_north", 6, site.depth - 9, 18, site.depth - 5, 1),
        ("east_outer_north", site.width - 19, site.depth - 9, site.width - 7, site.depth - 5, 1),
    ]
    for idx, (pid, x0, z0, x1, z1, tier) in enumerate(side_specs):
        role = role_pool[idx % len(role_pool)]
        if "market" in pid:
            role = "market"
        height, roof = _importance_hint(tier)
        parcels.append(TownParcel(
            pid,
            role,
            (x0, z0, x1, z1),
            tier,
            site.base_y,
            roof,
            height,
        ))

    negative_spaces = [
        NegativeSpace("market_mouth_square", "market_square",
                      (cx + 4, lane_z + 2, cx + 11, lane_z + 8), 3),
        NegativeSpace("well_court", "well_plaza",
                      (cx - 12, lane_z - 8, cx - 5, lane_z - 3), 2),
        NegativeSpace("back_lane_yard", "domestic_yard",
                      (cx + 6, 16, cx + 12, 21), 1),
    ]

    return TownPlan(
        seed=seed,
        site=site,
        perimeter=perimeter,
        wall_cells=wall_cells,
        gates=gates,
        spine=spine,
        lane_cells=lane_cells,
        parcels=parcels,
        negative_spaces=negative_spaces,
        soft_brief=soft_brief,
    )


def _reachable(start: Cell2, cells: Set[Cell2]) -> Set[Cell2]:
    q = deque([start])
    seen = {start}
    while q:
        x, z = q.popleft()
        for nxt in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if nxt in cells and nxt not in seen:
                seen.add(nxt)
                q.append(nxt)
    return seen


def validate_town_plan(plan: TownPlan) -> dict:
    errors: List[str] = []
    expected_boundary = _boundary(plan.site)
    if plan.perimeter != expected_boundary:
        missing = sorted(expected_boundary - plan.perimeter)[:8]
        extra = sorted(plan.perimeter - expected_boundary)[:8]
        errors.append(f"perimeter_not_boundary: missing={missing} extra={extra}")
    if not plan.gates:
        errors.append("missing_gate")
    for gate in plan.gates:
        off_wall = [cell for cell in gate.cells if cell not in plan.perimeter]
        if off_wall:
            errors.append(f"gate_off_perimeter: {gate.id}: {off_wall[:8]}")

    if not plan.spine:
        errors.append("missing_spine")
    else:
        gate_cells = {cell for gate in plan.gates for cell in gate.cells}
        if not (gate_cells & plan.spine):
            errors.append("spine_not_connected_to_gate")
        core = (plan.site.width // 2, plan.site.depth // 2)
        nearest_core = min(plan.spine, key=lambda c: abs(c[0] - core[0]) + abs(c[1] - core[1]))
        if abs(nearest_core[0] - core[0]) + abs(nearest_core[1] - core[1]) > 4:
            errors.append("spine_misses_core")

    landmarks = [p for p in plan.parcels if p.dominant_landmark]
    if len(landmarks) != 1:
        errors.append(f"dominant_landmark_count: {len(landmarks)}")
    elif landmarks[0].importance_tier != MAX_IMPORTANCE_TIER:
        errors.append("dominant_landmark_not_top_tier")
    for parcel in plan.parcels:
        if parcel.importance_tier < 0 or parcel.importance_tier > MAX_IMPORTANCE_TIER:
            errors.append(f"bad_importance_tier: {parcel.id}: {parcel.importance_tier}")

    core = (plan.site.width // 2, plan.site.depth // 2)
    ordered = sorted(plan.parcels, key=lambda p: abs(p.center[0] - core[0]) + abs(p.center[1] - core[1]))
    max_seen = MAX_IMPORTANCE_TIER
    for parcel in ordered:
        if parcel.importance_tier > max_seen:
            errors.append(f"importance_increases_outward: {parcel.id}")
        max_seen = min(max_seen, parcel.importance_tier)

    all_plan_cells = plan.perimeter | plan.street_cells | plan.parcel_cells | plan.negative_cells
    outside = sorted(c for c in all_plan_cells if not plan.site.contains(c))
    if outside:
        errors.append(f"plan_outside_site: {outside[:8]}")
    if plan.parcel_cells & plan.street_cells:
        errors.append(f"parcel_street_overlap: {sorted(plan.parcel_cells & plan.street_cells)[:8]}")
    if plan.negative_cells & plan.street_cells:
        errors.append(f"negative_space_street_overlap: {sorted(plan.negative_cells & plan.street_cells)[:8]}")
    if plan.negative_cells & plan.parcel_cells:
        errors.append(f"negative_space_parcel_overlap: {sorted(plan.negative_cells & plan.parcel_cells)[:8]}")
    if not plan.negative_spaces:
        errors.append("missing_negative_space")
    for parcel in plan.parcels:
        if parcel.ground_ref is None:
            errors.append(f"missing_ground_ref: {parcel.id}")

    return {
        "passed": not errors,
        "errors": errors,
        "stats": {
            "parcel_count": len(plan.parcels),
            "gate_count": len(plan.gates),
            "street_cells": len(plan.street_cells),
            "negative_spaces": len(plan.negative_spaces),
            "dominant_landmarks": len(landmarks),
        },
    }


def validate_realized_town(plan: TownPlan) -> dict:
    """Validate structural invariants after parcel/street realization."""
    errors: List[str] = []
    plan_report = validate_town_plan(plan)
    errors.extend(plan_report["errors"])
    streets = plan.street_cells
    if streets:
        reachable = _reachable(next(iter(plan.spine)), streets)
        for parcel in plan.parcels:
            border = _parcel_border(parcel)
            if not any(_adjacent(cell, reachable) for cell in border):
                errors.append(f"parcel_not_reachable_from_spine: {parcel.id}")
    for parcel in plan.parcels:
        if parcel.cells & streets:
            errors.append(f"building_footprint_overlaps_street: {parcel.id}")
    for gate in plan.gates:
        if not set(gate.cells) <= plan.perimeter:
            errors.append(f"gate_not_on_wall: {gate.id}")
    return {"passed": not errors, "errors": errors, "plan": plan_report}


def _parcel_border(parcel: TownParcel) -> Set[Cell2]:
    x0, z0, x1, z1 = parcel.bounds
    return _rect(x0, z0, x1, z0) | _rect(x0, z1, x1, z1) | _rect(x0, z0, x0, z1) | _rect(x1, z0, x1, z1)


def _adjacent(cell: Cell2, cells: Set[Cell2]) -> bool:
    x, z = cell
    return any(n in cells for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))


def estimate_block_budget(plan: TownPlan) -> dict:
    wall_blocks = len(plan.wall_cells) * 5
    road_blocks = len(plan.street_cells)
    parcel_blocks = sum(len(parcel.cells) * (5 + parcel.importance_tier * 2) for parcel in plan.parcels)
    prop_blocks = len(plan.negative_cells) // 2 + len(plan.parcels) * 8
    total = wall_blocks + road_blocks + parcel_blocks + prop_blocks
    return {
        "wall_blocks": wall_blocks,
        "road_blocks": road_blocks,
        "parcel_budget": parcel_blocks,
        "prop_budget": prop_blocks,
        "total_budget": total,
        "bounded": plan.site.width <= 96 and plan.site.depth <= 96 and total <= 75000,
    }


def write_plan_json(plan: TownPlan, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(plan.to_dict(), f, indent=2, ensure_ascii=False)
