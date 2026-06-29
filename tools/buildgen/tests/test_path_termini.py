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


def test_waterside_gallery_is_a_real_3d_building() -> None:
    """Arc 5: the 水边廊 is a real covered gallery — floor + COLUMN posts +
    ROOF_DARK roof + BALUSTRADE railing — not a single floor tile."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        wg = next(n for n in compound.parcel_nodes if n.id == "waterside_gallery")
        base_y = min(__column_surface_y(compound, c) for c in wg.cells)
        slots_in_col = set()
        for (x, z) in wg.cells:
            for y in range(base_y, base_y + 5):
                cell = compound.grid.get((x, y, z))
                if cell is not None and getattr(cell, "slot", None):
                    slots_in_col.add(cell.slot)
        # Floor (PATH_GALLERY) + columns (COLUMN) + roof (ROOF_DARK) are mandatory;
        # balustrade (BALUSTRADE) sits on the open edge just outside the gallery.
        _assert("PATH_GALLERY" in slots_in_col,
                f"mansion_{i+1:03d} 水边廊 has no PATH_GALLERY floor")
        _assert("COLUMN" in slots_in_col,
                f"mansion_{i+1:03d} 水边廊 has no COLUMN posts (not a 3D gallery)")
        _assert("ROOF_DARK" in slots_in_col,
                f"mansion_{i+1:03d} 水边廊 has no ROOF_DARK roof (not a 3D gallery)")
        # Balustrade on the open edge.
        open_side = wg.meta.get("water_side")
        delta = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
        if open_side in delta:
            dx, dz = delta[open_side]
            rail_found = False
            for (x, z) in wg.cells:
                edge = (x + dx, z + dz)
                if edge in wg.cells:
                    continue
                cell = compound.grid.get((edge[0], base_y + 2, edge[1]))
                if cell is not None and cell.slot == "BALUSTRADE":
                    rail_found = True
                    break
            _assert(rail_found,
                    f"mansion_{i+1:03d} 水边廊 has no BALUSTRADE on its open edge")


def test_mansion_main_yard_has_returning_galleries() -> None:
    """Arc 5: the mansion 主院 gains east + west 抄手游廊 (3D covered galleries)."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        galleries = [n for n in compound.parcel_nodes
                     if n.type == "covered_gallery" and n.id.endswith("_gallery")
                     and n.id in ("west_gallery", "east_gallery")]
        _assert(len(galleries) >= 1,
                f"mansion_{i+1:03d} 主院 has no 抄手游廊 (expected ≥1, got {len(galleries)})")


def test_tower_house_does_not_overlap_the_garden() -> None:
    """Arc 6: the 绣楼 stands in its own 后院 — no footprint cell coincides with a
    花园 feature cell, and the 后院/花园 bands do not share a z-interval."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        report = validate_mansion(compound)
        overlap_errs = [e for e in report["errors"]
                        if e.startswith("tower_overlaps_garden")
                        or e.startswith("back_yard_garden_overlap")]
        _assert(not overlap_errs,
                f"mansion_{i+1:03d} layout overlap: {overlap_errs}")


def __column_surface_y(compound, cell):
    """Local helper: the standable surface y of a column (mirrors
    _natural_surface_y without importing the private name per-cell)."""
    from buildgen.compound import _natural_surface_y
    return _natural_surface_y(compound, cell)


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
