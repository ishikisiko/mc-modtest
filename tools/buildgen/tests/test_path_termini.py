"""Regression tests for the path termini: 仆役房 + 水边廊 (path-surface-zoning task 3).

The 生活 route's endpoint is a ``service_house`` sub-building placed along the
倒座 side alley; its ``door_info["front"]`` is a mandatory path endpoint, so the
formal/service BFS reaches it. The 游赏 route's 水边廊 is a shoreside
``covered_gallery`` whose cells line the pond's near shore and resolve through
``PATH_GALLERY``.

Run from the repository root:
    python3 tools/buildgen/tests/test_path_termini.py
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


def test_every_mansion_has_a_service_house_with_door_endpoint() -> None:
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        sh = [s for s in compound.building_slots if s.archetype == "service_house"]
        _assert(len(sh) == 1,
                f"mansion_{i+1:03d} expected one service_house slot, got {len(sh)}")
        door = sh[0].door_info.get("front") if sh[0].door_info else None
        _assert(isinstance(door, tuple),
                f"mansion_{i+1:03d} service_house has no door_info['front']")


def test_service_house_door_is_a_path_endpoint() -> None:
    """The service_house door-front cell must be in the path endpoint set."""
    from buildgen.compound import _collect_path_endpoints
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        sh = next(s for s in compound.building_slots
                  if s.archetype == "service_house")
        door = sh.door_info["front"]
        endpoints = set(_collect_path_endpoints(compound))
        _assert((door[0], door[2]) in endpoints,
                f"mansion_{i+1:03d} service_house door-front {(door[0], door[2])} "
                f"is not in the path endpoint set")


def test_service_house_sits_on_the_south_wall_band() -> None:
    """The service_house footprint must be in the front-yard band (south, low z)."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        sh = next(s for s in compound.building_slots
                  if s.archetype == "service_house")
        max_z = max(z for _, z in sh.footprint)
        # The front yard band is shallow (gate_house + 1 row); the service_house
        # is a south-wall building, so its deepest cell is well before the 仪门.
        yimen_z = compound.meta["outer_yard_band"][1] + 1
        _assert(max_z < yimen_z,
                f"mansion_{i+1:03d} service_house (max_z={max_z}) extends past "
                f"the front-yard band (yimen~{yimen_z})")


def test_every_mansion_has_a_shoreside_gallery() -> None:
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        wg = [n for n in compound.parcel_nodes if n.id == "waterside_gallery"]
        _assert(len(wg) == 1,
                f"mansion_{i+1:03d} expected one waterside_gallery, got {len(wg)}")


def test_waterside_gallery_cells_are_adjacent_to_the_pond() -> None:
    """Every 水边廊 cell must be 4-adjacent to a pond water cell."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        wg = next(n for n in compound.parcel_nodes if n.id == "waterside_gallery")
        pond = next((n for n in compound.parcel_nodes
                     if n.type == "garden_pond"), None)
        _assert(pond is not None, f"mansion_{i+1:03d} has no garden_pond")
        water = pond.cells
        for (gx, gz) in wg.cells:
            nbrs = {(gx + 1, gz), (gx - 1, gz), (gx, gz + 1), (gx, gz - 1)}
            _assert(nbrs & water,
                    f"mansion_{i+1:03d} 水边廊 cell {(gx, gz)} is not adjacent to "
                    f"the pond")


def test_waterside_gallery_is_a_covered_gallery_parcel() -> None:
    """The 水边廊 reuses the covered_gallery parcel type (PATH_GALLERY floor)."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        wg = next(n for n in compound.parcel_nodes if n.id == "waterside_gallery")
        _assert(wg.type == "covered_gallery",
                f"mansion_{i+1:03d} waterside_gallery type is {wg.type!r}, "
                f"expected 'covered_gallery'")


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
