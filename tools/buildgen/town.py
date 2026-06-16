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
    width: int = 96
    depth: int = 80
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
    template_id: str = ""

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
    ritual_axis: Dict[str, object] = field(default_factory=dict)

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
            "ritual_axis": self.ritual_axis,
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
    if site.width < 72 or site.depth < 64:
        raise ValueError(f"site too small for default town: {site.width}x{site.depth}")

    rng = random.Random(seed)
    soft_brief = dict(soft_brief or {})
    perimeter = _boundary(site)
    cx = site.width // 2
    gate_half = 2
    south_gate_cells = tuple((x, 0) for x in range(cx - gate_half, cx + gate_half + 1))
    gates = [
        TownGate("south_gate", "south", south_gate_cells),
    ]
    gate_cells = set(south_gate_cells)
    wall_cells = perimeter - gate_cells

    shrine_w = 27
    shrine_d = 20
    shrine_x0 = cx - shrine_w // 2
    shrine_x1 = shrine_x0 + shrine_w - 1
    shrine_z1 = site.depth - 3
    shrine_z0 = shrine_z1 - shrine_d + 1
    plaza = (cx - 16, shrine_z0 - 9, cx + 16, shrine_z0 - 1)
    paifang_cells = tuple((x, plaza[1] - 1) for x in range(cx - 6, cx + 7))
    lantern_cells = tuple(
        cell
        for z in range(8, plaza[1] - 2, 5)
        for cell in ((cx - 5, z), (cx + 5, z))
    )

    spine_half = 3
    spine = _rect(cx - spine_half, 0, cx + spine_half, plaza[3])
    spine |= _rect(*plaza)
    spine |= set(paifang_cells)

    lane_z = site.depth // 2 - 2
    lane_cells = {
        (x, z)
        for x in range(8, site.width - 8)
        for z in range(lane_z - 1, lane_z + 2)
    } - spine
    lane_cells |= {
        (x, z)
        for x in range(8, site.width - 8)
        for z in range(16, 19)
    } - spine
    lane_cells |= {
        (x, shrine_z0 - 1)
        for x in range(8, site.width - 8)
    } - spine

    role_pool = _role_sequence(rng, soft_brief)
    parcels: List[TownParcel] = []
    height, roof = _importance_hint(MAX_IMPORTANCE_TIER)
    parcels.append(TownParcel(
        "town_shrine",
        "civic",
        (shrine_x0, shrine_z0, shrine_x1, shrine_z1),
        MAX_IMPORTANCE_TIER,
        site.base_y,
        roof,
        height,
        dominant_landmark=True,
        template_id="town_shrine_001",
    ))

    side_specs = [
        ("west_core_shop", 20, 20, 42, lane_z - 2, 2, "market", "cultivation_shop_002"),
        ("east_core_shop", site.width - 42, 20, site.width - 20, lane_z - 2, 2, "market", "cultivation_shop_003"),
        ("west_market", 12, lane_z + 2, 31, shrine_z0 - 2, 2, "market", "cultivation_market_001"),
        ("east_market", site.width - 31, lane_z + 2, site.width - 9, shrine_z0 - 2, 2, "market", "cultivation_market_001"),
        ("west_outer_south", 16, 1, 36, 15, 1, "housing", "cultivation_house_001"),
        ("east_outer_south", site.width - 36, 1, site.width - 16, 15, 1, "housing", "cultivation_house_002"),
        ("west_outer_north", 8, shrine_z0, 31, shrine_z1 - 1, 1, "housing", "cultivation_house_003"),
        ("east_outer_north", site.width - 31, shrine_z0, site.width - 9, shrine_z1 - 1, 1, "defense", "cultivation_market_002"),
    ]
    for idx, (pid, x0, z0, x1, z1, tier, default_role, template_id) in enumerate(side_specs):
        role = default_role or role_pool[idx % len(role_pool)]
        height, roof = _importance_hint(tier)
        parcels.append(TownParcel(
            pid,
            role,
            (x0, z0, x1, z1),
            tier,
            site.base_y,
            roof,
            height,
            template_id=template_id,
        ))

    negative_spaces = [
        NegativeSpace("market_mouth_square", "market_square",
                      (cx - 16, lane_z + 2, cx - 5, min(lane_z + 7, plaza[1] - 1)), 3),
        NegativeSpace("well_court", "well_plaza",
                      (8, lane_z - 11, 16, lane_z - 5), 2),
        NegativeSpace("back_lane_yard", "domestic_yard",
                      (site.width - 18, lane_z - 13, site.width - 9, lane_z - 7), 1),
    ]
    ritual_axis = {
        "kind": "cultivation_town_ritual_axis",
        "from_gate": "south_gate",
        "terminus_parcel": "town_shrine",
        "plaza_bounds": list(plaza),
        "paifang_gate_cells": _cells_to_json(paifang_cells),
        "lantern_cells": _cells_to_json(lantern_cells),
        "axis_center_x": cx,
        "approach_z_range": [0, plaza[3]],
    }

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
        ritual_axis=ritual_axis,
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
    elif landmarks[0].id != "town_shrine":
        errors.append(f"dominant_landmark_not_town_shrine: {landmarks[0].id}")
    for parcel in plan.parcels:
        if parcel.importance_tier < 0 or parcel.importance_tier > MAX_IMPORTANCE_TIER:
            errors.append(f"bad_importance_tier: {parcel.id}: {parcel.importance_tier}")

    axis = plan.ritual_axis
    if not axis:
        errors.append("missing_ritual_axis")
    else:
        if axis.get("terminus_parcel") != "town_shrine":
            errors.append(f"ritual_axis_wrong_terminus: {axis.get('terminus_parcel')}")
        shrine = next((p for p in plan.parcels if p.id == "town_shrine"), None)
        plaza_bounds = axis.get("plaza_bounds", [])
        if shrine is None:
            errors.append("ritual_axis_missing_town_shrine_parcel")
        elif len(plaza_bounds) == 4:
            plaza_cells = _rect(*plaza_bounds)
            shrine_front = _rect(shrine.bounds[0], shrine.bounds[1] - 1,
                                 shrine.bounds[2], shrine.bounds[1] - 1)
            if not (shrine_front & plaza_cells):
                errors.append("town_shrine_not_fronted_by_plaza")
            if not (plaza_cells & plan.spine):
                errors.append("ritual_plaza_not_on_axis_spine")
        else:
            errors.append(f"bad_ritual_plaza_bounds: {plaza_bounds}")
        paifang = {tuple(c) for c in axis.get("paifang_gate_cells", [])}
        if not paifang or not paifang <= plan.spine:
            errors.append("paifang_gate_not_on_axis")
        lanterns = {tuple(c) for c in axis.get("lantern_cells", [])}
        if len(lanterns) < 4:
            errors.append("lantern_approach_too_sparse")

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
