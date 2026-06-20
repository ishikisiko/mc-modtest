#!/usr/bin/env python3
"""Validate the runtime town planner's districted plan and template fit.

The Java ``TownGenerator`` re-derives a plan structurally equivalent to the
Python planner in ``buildgen.town``. This validator drives that plan from the
``cultivation_town`` group's district brief and asserts:

  * the footprint is partitioned into the required districts (gate / market /
    residential / civic_core / fringe) with no overlaps;
  * the civic_core outranks the fringe (taller storey band, denser target) and
    carries the sole dominant landmark (the shrine);
  * the ritual axis (plaza / paifang / lanterns) lives inside the civic core;
  * the district partition is deterministic for a fixed seed + site;
  * every parcel's referenced structure template fits inside the parcel, does
    not spill into streets, and carries a non-empty ground layer so realized
    buildings neither float over a one-block hollow nor bury into the ground.

It replaces the previous hardcoded 96x80 nine-parcel ritual-axis layout check.
"""

from __future__ import annotations

import copy
import json
import sys
from itertools import combinations
from pathlib import Path
from typing import Dict, Set, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.groups import get_group  # noqa: E402
from buildgen.nbtread import read_gzipped_nbt  # noqa: E402
from buildgen.town import (  # noqa: E402
    DISTRICT_IMPORTANCE,
    MAX_FOOTPRINT_AXIS,
    MIN_FOOTPRINT_AXIS,
    TownSite,
    generate_town_plan,
    parity_constants,
    validate_town_plan,
    validate_realized_town,
)

Cell = Tuple[int, int]
Rect = Tuple[int, int, int, int]

TEMPLATE_GROUND_LAYER = 1
STRUCTURE_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"

REQUIRED_KINDS = ("gate", "market", "residential", "civic_core", "fringe")
DEFAULT_SEED = 20260618
PROBE_SEEDS = (20260618, 20260719, 20260820, 20260921, 20261022)
DISTINCTNESS_REPORT = REPO_ROOT / "reports" / "town_distinctness_calibration.json"


def rect(x0: int, z0: int, x1: int, z1: int) -> Set[Cell]:
    if x1 < x0 or z1 < z0:
        return set()
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


def intersects(a: Set[Cell], b: Set[Cell]) -> bool:
    return any(cell in b for cell in a)


def template_root(name: str) -> dict:
    _name, root = read_gzipped_nbt(str(STRUCTURE_DIR / f"{name}.nbt"))
    return root


def template_size(name: str) -> Tuple[int, int]:
    root = template_root(name)
    sx, _sy, sz = root["size"]
    return int(sx), int(sz)


def template_layer_coverage(name: str, y: int) -> int:
    root = template_root(name)
    return sum(1 for block in root["blocks"] if int(block["pos"][1]) == y)


def frontage_placement(parcel_rect: Rect, edge: str, tw: int, td: int) -> Rect:
    """Template footprint when placed aligned to the parcel's frontage edge."""
    x0, z0, x1, z1 = parcel_rect
    if edge == "S":
        return (x0, z0, x0 + tw - 1, z0 + td - 1)
    if edge == "N":
        return (x0, z1 - td + 1, x0 + tw - 1, z1)
    if edge == "E":
        return (x1 - tw + 1, z0, x1, z0 + td - 1)
    if edge == "W":
        return (x0, z0, x0 + tw - 1, z0 + td - 1)
    # no frontage: center the template in the parcel
    px = x0 + max(0, ((x1 - x0 + 1) - tw) // 2)
    pz = z0 + max(0, ((z1 - z0 + 1) - td) // 2)
    return (px, pz, px + tw - 1, pz + td - 1)


def validate_district_partition(plan) -> list:
    errors = []
    kinds = {d.kind for d in plan.districts}
    for k in REQUIRED_KINDS:
        if k not in kinds:
            errors.append(f"missing_district_kind:{k}")
    covered: Set[Cell] = set()
    for d in plan.districts:
        cells = d.cells
        overlap = covered & cells
        if overlap:
            errors.append(f"district_overlap:{d.id}:{sorted(overlap)[:6]}")
        covered |= cells
    # civic_core storey band must outrank every other district's max
    cores = [d for d in plan.districts if d.kind == "civic_core"]
    fringes = [d for d in plan.districts if d.kind == "fringe"]
    if cores:
        core_max = max(d.storey_band[1] for d in cores)
        for d in plan.districts:
            if d.kind != "civic_core" and d.storey_band[1] > core_max:
                errors.append(f"district_overtops_core:{d.id}:{d.storey_band}")
    if cores and fringes:
        if max(d.density for d in fringes) >= max(d.density for d in cores):
            errors.append("fringe_not_loosest")
    return errors


def validate_template_fit(plan) -> list:
    errors = []
    streets = plan.street_cells
    for parcel in plan.parcels:
        if not parcel.template_id:
            continue
        try:
            tw, td = template_size(parcel.template_id)
        except FileNotFoundError:
            errors.append(f"template_missing:{parcel.id}:{parcel.template_id}")
            continue
        tpl_rect = frontage_placement(parcel.bounds, parcel.frontage_edge, tw, td)
        tpl_cells = rect(*tpl_rect)
        # template must fit within the parcel
        if not tpl_cells <= parcel.cells:
            errors.append(
                f"template_outside_parcel:{parcel.id}:{parcel.template_id}:"
                f"tpl={tpl_rect} parcel={parcel.bounds}")
        # template must not spill into streets
        if intersects(tpl_cells, streets):
            errors.append(f"template_street_overlap:{parcel.id}:{parcel.template_id}")
        # ground layer must be non-empty and reasonably dense
        ground = template_layer_coverage(parcel.template_id, TEMPLATE_GROUND_LAYER)
        under = template_layer_coverage(parcel.template_id, TEMPLATE_GROUND_LAYER - 1)
        if ground == 0:
            errors.append(f"template_ground_layer_empty:{parcel.id}:{parcel.template_id}")
        elif ground < max(1, under // 2):
            errors.append(
                f"template_ground_layer_too_sparse:{parcel.id}:{parcel.template_id}:"
                f"ground={ground}:under={under}")
    return errors


def validate_determinism(seed: int, site: TownSite, brief: list) -> list:
    errors = []
    a = generate_town_plan(seed, site, brief)
    b = generate_town_plan(seed, site, brief)
    ka = [(d.kind, d.bounds, d.cells_override) for d in a.districts]
    kb = [(d.kind, d.bounds, d.cells_override) for d in b.districts]
    if ka != kb:
        errors.append("district_partition_not_deterministic")
    pa = [(p.id, p.bounds, p.template_id) for p in a.parcels]
    pb = [(p.id, p.bounds, p.template_id) for p in b.parcels]
    if pa != pb:
        errors.append("parcel_layout_not_deterministic")
    if a.shape_id != b.shape_id:
        errors.append("shape_id_not_deterministic")
    if a.perimeter != b.perimeter:
        errors.append("perimeter_not_deterministic")
    return errors


def perimeter_jaccard_distance(a: Set[Cell], b: Set[Cell]) -> float:
    """Cell-set Jaccard distance (0 identical, 1 disjoint)."""
    union = a | b
    return 0.0 if not union else 1.0 - (len(a & b) / len(union))


def silhouette_descriptor(perimeter: Set[Cell]) -> Tuple[float, float, float]:
    """Return bbox aspect, corner fraction, and raster-curve fraction."""
    if not perimeter:
        return (0.0, 0.0, 0.0)
    xs = [x for x, _z in perimeter]
    zs = [z for _x, z in perimeter]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    width, depth = x1 - x0 + 1, z1 - z0 + 1
    x_lo, x_hi = x0 + width // 4, x1 - width // 4
    z_lo, z_hi = z0 + depth // 4, z1 - depth // 4
    corners = sum(
        1 for x, z in perimeter
        if (x < x_lo or x > x_hi) and (z < z_lo or z > z_hi))
    curves = 0
    for x, z in perimeter:
        horizontal = int((x - 1, z) in perimeter) + int((x + 1, z) in perimeter)
        vertical = int((x, z - 1) in perimeter) + int((x, z + 1) in perimeter)
        if horizontal < 2 and vertical < 2:
            curves += 1
    count = len(perimeter)
    return (width / depth, corners / count, curves / count)


def silhouette_distance(a: Set[Cell], b: Set[Cell]) -> float:
    return sum(abs(x - y) for x, y in zip(
        silhouette_descriptor(a), silhouette_descriptor(b)))


def validate_distinctness(plans: Dict[int, object]) -> list:
    """Fail on any colliding probe pair and name its missed metric."""
    errors = []
    calibration = json.loads(DISTINCTNESS_REPORT.read_text(encoding="utf-8"))
    jaccard_floor = float(calibration["floors"]["perimeter_jaccard"])
    descriptor_floor = float(calibration["floors"]["silhouette_descriptor"])
    for seed_a, seed_b in combinations(PROBE_SEEDS, 2):
        a, b = plans[seed_a].perimeter, plans[seed_b].perimeter
        jaccard = perimeter_jaccard_distance(a, b)
        descriptor = silhouette_distance(a, b)
        pair = f"{seed_a},{seed_b}"
        if jaccard < jaccard_floor:
            errors.append(
                f"distinctness_pair={pair}:perimeter_jaccard="
                f"{jaccard:.6f}<{jaccard_floor:.6f}")
        if descriptor < descriptor_floor:
            errors.append(
                f"distinctness_pair={pair}:silhouette_descriptor="
                f"{descriptor:.6f}<{descriptor_floor:.6f}")
    return errors


def validate_parity_constants() -> list:
    """Confirm the Python planner geometry matches TownGenerator.java.

    The ``expected`` map mirrors the Java realizer's static final constants and
    its civic-precinct + perimeter-vocabulary derivation for the default
    160x160 footprint. If either side changes, update both and this map together.
    """
    expected = {
        "BARBICAN_DEPTH": 6,
        "BARBICAN_OFFSET": 9,
        "BARBICAN_WIDTH": 8,
        "BASE_CENTER_X": 80,
        "BASTION_DEPTH": 8,
        "BASTION_HALF_W": 10,
        "CENTER_X": 82,
        "CENTER_X_JITTER": 4,
        "CIRCLE_MARGIN": 1,
        "COLONNADE_CELLS": 146,
        "COLONNADE_SLIVER_WIDTH": 2,
        "CORE_BOUNDS": [46, 111, 118, 158],
        "DEPTH": 160,
        "DISTRICT_WIDTH_JITTER": 3,
        "FRINGE_CELL_COUNT_SQUARE": 3552,
        "GATE_BAND_DEPTH": 2,
        "GATE_RUN_HALF": 8,
        "GRID_REFERENCE_SEED": 20260618,
        "INTERIOR_CELLS_CIRCLE": 19794,
        "INTERIOR_CELLS_DSHAPE": 22853,
        "INTERIOR_CELLS_OCTAGON": 21643,
        "INTERIOR_CELLS_OVAL": 14401,
        "INTERIOR_CELLS_SQUARE": 25600,
        "INTERIOR_CELLS_TRAPEZOID": 23836,
        "LANDMARK_D": 21,
        "LANDMARK_W": 19,
        "LANE_JITTER": 2,
        "LANE_M": [60, 62],
        "LANE_N": [108, 110],
        "LANE_S": [17, 19],
        "MODIFIER_BITTEN_CELLS_BARBICAN": 56,
        "MODIFIER_BITTEN_CELLS_BASTION": 336,
        "MODIFIER_BITTEN_CELLS_NONE": 0,
        "OCTAGON_K": 44,
        "OVAL_RX_MAX": 74,
        "OVAL_RX_MIN": 60,
        "OVAL_RX_REF": 63,
        "OVAL_RZ_MAX": 64,
        "OVAL_RZ_MIN": 50,
        "OVAL_RZ_REF": 55,
        "PERIMETER_CELLS_CIRCLE": 462,
        "PERIMETER_CELLS_DSHAPE": 558,
        "PERIMETER_CELLS_OCTAGON": 462,
        "PERIMETER_CELLS_OVAL": 528,
        "PERIMETER_CELLS_SQUARE": 636,
        "PERIMETER_CELLS_TRAPEZOID": 612,
        "PERIMETER_FAMILIES": ["square", "circle", "oval", "dshape", "octagon", "trapezoid"],
        "PERIMETER_MODIFIERS": ["none", "barbican", "bastion"],
        "PERIMETER_REFERENCE_SEED": 20260618,
        "PLAZA": [66, 128, 98, 136],
        "PRECINCT_GATE_CELLS": 7,
        "PRECINCT_WALL_CELLS": 156,
        "SHRINE_D": 21,
        "SHRINE_W": 23,
        "SIDE_GATE_CELLS": 4,
        "SIDE_HALL_PARCELS": 2,
        "SIDE_HALL_WIDTH": 11,
        "SPINE_HALF_WIDTH": 3,
        "SPIRIT_WAY_CELLS": 12,
        "TRAPEZOID_SLANT": 12,
        "WIDTH": 160,
    }
    errors = []
    py = parity_constants()
    for key, value in expected.items():
        if py.get(key) != value:
            errors.append(f"parity_constant_mismatch:{key}:python={py.get(key)}!=java={value}")
    # The same fixture is consumed by TownGeometryParityTest, making this a
    # two-sided contract: Python validates every probe row here; Java validates
    # the identical rows in JUnit (including circle/oval sweeps).
    geometry_fixture = json.loads((
        REPO_ROOT / "src/test/resources/town_geometry_parity.json"
    ).read_text(encoding="utf-8"))
    for row in geometry_fixture["snapshots"]:
        seed = int(row["seed"])
        snap = parity_constants(seed)
        checks = {
            "family": snap["SELECTED_FAMILY"],
            "modifier": snap["SELECTED_MODIFIERS"][0],
            "center_x": snap["CENTER_X"],
            "lanes": [snap["LANE_S"], snap["LANE_M"], snap["LANE_N"]],
            "perimeter_cells": snap["SELECTED_PERIMETER_CELLS"],
            "interior_cells": snap["SELECTED_INTERIOR_CELLS"],
            "district_widths": [
                snap[next(k for k in snap if k.startswith(f"DISTRICT_WIDTH_{i}_"))]
                for i in range(10)],
            "district_cells": [
                snap[next(k for k in snap if k.startswith(f"DISTRICT_CELL_COUNT_{i}_"))]
                for i in range(10)],
        }
        for key, actual in checks.items():
            if actual != row[key]:
                errors.append(
                    f"parity_probe_mismatch:seed={seed}:{key}:"
                    f"python={actual}!=fixture={row[key]}")
    return errors


def main() -> int:
    group = get_group("cultivation_town")
    brief = list(group.scale_params.get("district_brief", []))
    footprint = group.scale_params.get("footprint", {"width": 160, "depth": 160})
    width = int(footprint.get("width", 160))
    depth = int(footprint.get("depth", 160))

    errors: list = []
    if width > MAX_FOOTPRINT_AXIS or depth > MAX_FOOTPRINT_AXIS:
        errors.append(f"footprint_exceeds_cap:{width}x{depth}>{MAX_FOOTPRINT_AXIS}")
    if width < MIN_FOOTPRINT_AXIS or depth < MIN_FOOTPRINT_AXIS:
        errors.append(f"footprint_below_min:{width}x{depth}<{MIN_FOOTPRINT_AXIS}")

    probe_plans = {}
    for seed in PROBE_SEEDS:
        site = TownSite(width, depth)
        plan = generate_town_plan(seed, site, brief)
        probe_plans[seed] = plan
        plan_report = validate_town_plan(plan)
        realized_report = validate_realized_town(plan)
        for err in plan_report["errors"]:
            errors.append(f"seed={seed}:{err}")
        for err in realized_report["errors"]:
            errors.append(f"seed={seed}:realized:{err}")
        errors.extend(f"seed={seed}:{e}" for e in validate_district_partition(plan))
        errors.extend(f"seed={seed}:{e}" for e in validate_template_fit(plan))

    errors.extend(validate_distinctness(probe_plans))

    # determinism on the canonical seed
    errors.extend(validate_determinism(DEFAULT_SEED, TownSite(width, depth), brief))

    # Python/Java parity: the layout + civic-precinct geometry the Java
    # realizer hardcodes must match this planner.
    parity_errors = validate_parity_constants()
    errors.extend(parity_errors)

    # importance tier derives from district kind
    plan = generate_town_plan(DEFAULT_SEED, TownSite(width, depth), brief)
    for p in plan.parcels:
        if p.dominant_landmark:
            continue
        expected = DISTRICT_IMPORTANCE.get(p.district_kind, 0)
        if p.importance_tier != expected and p.id not in ("civic_west_hall", "civic_east_hall"):
            # civic halls are pinned to a tier inside the core band; allow that.
            if p.district_kind == "civic_core":
                continue
            errors.append(f"tier_not_derived_from_kind:{p.id}:{p.district_kind}:{p.importance_tier}!={expected}")

    for error in errors:
        print(f"FAIL {error}")
    if errors:
        return 1
    print(
        "OK runtime districted town plan: districts partitioned, civic core "
        "outranks fringe, ritual axis inside the core, civic-precinct framing "
        "present, partition deterministic, Python/Java parity holds, and every "
        "parcel template fits its parcel off the streets with a non-empty "
        "ground layer"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
