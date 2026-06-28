"""Regression tests for the tour route + 月洞门 passage (path-surface-zoning).

The tour route is a winding mossy-stone-brick (PATH_TOUR) polyline routed from
the 月洞门 passage inner cell through the garden's scenic waypoints (rockery
view-point → pond shore → 亭), where each segment is a single-source shortest
path. The 月洞门 passage is the material boundary: cells before it are formal
(PATH_FORMAL), cells after are tour (PATH_TOUR), and the two cell sets MUST NOT
intersect.

Run from the repository root:
    python3 tools/buildgen/tests/test_tour_route.py
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Set, Tuple

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.compound import generate_mansion, validate_mansion  # noqa: E402

Cell2 = Tuple[int, int]
BASE_SEED = 20260618
VARIANT_COUNT = 6


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def _tour_nodes(compound):
    return [n for n in compound.parcel_nodes if n.type == "tour_path"]


def _formal_cells(compound) -> Set[Cell2]:
    nodes = [n for n in compound.parcel_nodes if n.type == "path"]
    return set().union(*(n.cells for n in nodes)) if nodes else set()


def _feature_cells(compound) -> Set[Cell2]:
    feats = set()
    for ftype in ("garden_rockery", "garden_pond", "garden_pavilion"):
        feats |= compound.node_cells(ftype)
    return feats


def test_every_mansion_has_a_moon_gate_passage() -> None:
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        mg = [n for n in compound.parcel_nodes if n.type == "moon_gate_passage"]
        _assert(len(mg) == 1,
                f"mansion_{i+1:03d} expected exactly one moon_gate_passage, "
                f"got {len(mg)}")


def test_tour_route_is_present_and_winds() -> None:
    """Every mansion's tour route has ≥2 distinct segment directions (it bends)."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        tours = _tour_nodes(compound)
        _assert(len(tours) == 1,
                f"mansion_{i+1:03d} expected one tour_path node, got {len(tours)}")
        tour = tours[0]
        _assert(len(tour.cells) > 0,
                f"mansion_{i+1:03d} tour path has no cells")
        waypoints = [tuple(w) for w in tour.meta["waypoints"]]
        directions = {(b[0] - a[0], b[1] - a[1])
                      for a, b in zip(waypoints, waypoints[1:]) if a != b}
        _assert(len(directions) >= 2,
                f"mansion_{i+1:03d} tour does not wind: waypoints {waypoints} "
                f"directions {directions}")


def test_tour_route_does_not_overlap_formal_backbone() -> None:
    """The material boundary at the 月洞门 means no cell is both formal and tour."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        tour_cells = _tour_nodes(compound)[0].cells
        formal = _formal_cells(compound)
        overlap = tour_cells & formal
        _assert(not overlap,
                f"mansion_{i+1:03d} formal/tour cell intersection is non-empty: "
                f"{sorted(overlap)[:8]}")


def test_tour_route_does_not_coincide_with_garden_features() -> None:
    """No tour cell sits on a rockery, pond, or pavilion cell (obstacle-avoided)."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        tour_cells = _tour_nodes(compound)[0].cells
        feats = _feature_cells(compound)
        on_feature = tour_cells & feats
        _assert(not on_feature,
                f"mansion_{i+1:03d} tour coincides with a garden feature at "
                f"{sorted(on_feature)[:8]}")


def test_tour_route_first_waypoint_is_inside_the_garden() -> None:
    """The tour's first waypoint is the 月洞门 passage inner cell (花园 side)."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        tour = _tour_nodes(compound)[0]
        mg = next(n for n in compound.parcel_nodes if n.type == "moon_gate_passage")
        wall_z = mg.meta["wall_z"]
        axis_x = mg.meta["axis_x"]
        first_wp = tuple(tour.meta["waypoints"][0])
        _assert(first_wp == (axis_x, wall_z + 1),
                f"mansion_{i+1:03d} first tour waypoint {first_wp} is not the "
                f"passage inner cell {(axis_x, wall_z + 1)}")


def test_mansion_still_validates() -> None:
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        report = validate_mansion(compound)
        _assert(report["passed"],
                f"mansion_{i+1:03d} failed validation: {report['errors'][:5]}")


def main() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    failures = 0
    for t in tests:
        try:
            t()
            print(f"PASS {t.__name__}")
        except AssertionError as e:
            failures += 1
            print(f"FAIL {t.__name__}: {e}")
    print(f"{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
