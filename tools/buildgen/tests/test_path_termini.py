"""Regression tests for the path termini and water-garden visual composition.

The 生活 route's endpoint is a ``service_house`` sub-building placed along the
倒座 side alley; its ``door_info["front"]`` is a mandatory path endpoint, so the
formal/service BFS reaches it. Arc 11 removes the separate waterside shed and
turns the garden pavilion into a near-replica of the supplied heavy scenic
pavilion reference.

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


def test_every_mansion_has_no_separate_waterside_shed() -> None:
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        wg = [n for n in compound.parcel_nodes if n.id == "waterside_gallery"]
        _assert(not wg,
                f"mansion_{i+1:03d} still has a separate waterside shed: {wg}")


def test_garden_pavilion_is_a_waterside_pavilion() -> None:
    """The garden 亭 must sit on a pond bank, not across the garden lawn."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        pavilion = next((n for n in compound.parcel_nodes
                         if n.type == "garden_pavilion"), None)
        pond = next((n for n in compound.parcel_nodes
                     if n.type == "garden_pond"), None)
        _assert(pavilion is not None,
                f"mansion_{i+1:03d} has no garden_pavilion")
        _assert(pond is not None,
                f"mansion_{i+1:03d} has no garden_pond")
        touches_water = any(
            (x + dx, z + dz) in pond.cells
            for x, z in pavilion.cells
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
        )
        _assert(touches_water,
                f"mansion_{i+1:03d} garden_pavilion center "
                f"{pavilion.meta.get('center')} is detached from pond "
                f"{pond.meta.get('bbox')}")
        pond_z1 = pond.meta.get("bbox", [0, 0, 0, 0])[3]
        pavilion_z0 = min(z for _, z in pavilion.cells)
        _assert(pavilion_z0 > pond_z1,
                f"mansion_{i+1:03d} garden_pavilion is not on the dry south bank: "
                f"pavilion_z0={pavilion_z0}, pond_z1={pond_z1}")
        _assert(pavilion.meta.get("water_side") == "north",
                f"mansion_{i+1:03d} garden_pavilion water side is not north: "
                f"{pavilion.meta.get('water_side')}")
        _assert(pavilion.meta.get("entry_side") == "south",
                f"mansion_{i+1:03d} garden_pavilion entry side is not south: "
                f"{pavilion.meta.get('entry_side')}")
        lot_w, _lot_d = compound.lot_size
        center_x = int(pavilion.meta["center"][0])
        _assert(abs(center_x - lot_w // 2) <= 2,
                f"mansion_{i+1:03d} garden_pavilion is still an edge-corner object: "
                f"center_x={center_x}, axis={lot_w // 2}")
        opening = pavilion.meta.get("scenic_opening", {})
        _assert(opening.get("side") == "south",
                f"mansion_{i+1:03d} garden_pavilion has no south scenic opening")
        z = int(opening.get("boundary_z", -1))
        x0 = int(opening.get("x0", 0))
        x1 = int(opening.get("x1", -1))
        blocked = [
            (x, y, z)
            for x in range(x0, x1 + 1)
            for y in range(0, 8)
            if not compound.grid.is_empty((x, y, z))
        ]
        _assert(not blocked,
                f"mansion_{i+1:03d} garden_pavilion scenic opening is blocked: "
                f"{blocked[:6]}")
        side_blocked = [
            (x, y, z)
            for x, z in (tuple(c) for c in opening.get("side_cells", []))
            for y in range(0, 8)
            if not compound.grid.is_empty((x, y, z))
        ]
        _assert(not side_blocked,
                f"mansion_{i+1:03d} garden_pavilion side scenic opening is blocked: "
                f"{side_blocked[:6]}")
        backdrop = pavilion.meta.get("backdrop_opening", {})
        _assert(backdrop,
                f"mansion_{i+1:03d} garden_pavilion has no water backdrop opening")
        bz = int(backdrop.get("wall_z", -1))
        bx0 = int(backdrop.get("x0", 0))
        bx1 = int(backdrop.get("x1", -1))
        backdrop_blocked = [
            (x, y, bz)
            for x in range(bx0, bx1 + 1)
            for y in range(0, 8)
            if not compound.grid.is_empty((x, y, bz))
        ]
        _assert(not backdrop_blocked,
                f"mansion_{i+1:03d} garden_pavilion backdrop opening is blocked: "
                f"{backdrop_blocked[:6]}")


def test_garden_pavilion_replicates_reference_pavilion_parts() -> None:
    """Arc 11: the water pavilion follows the supplied reference image:
    raised stone base, wooden deck, heavy log posts, railings, lattice/bracket
    details, hanging lanterns, double eaves, and grey stone roof ornaments."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        pavilion = next((n for n in compound.parcel_nodes
                         if n.type == "garden_pavilion"), None)
        _assert(pavilion is not None,
                f"mansion_{i+1:03d} has no garden_pavilion")
        cx, cz = pavilion.meta["center"]
        base_y = pavilion.meta["base_y"]
        lower_half = pavilion.meta["lower_eave_half"]
        upper_half = pavilion.meta["upper_eave_half"]
        lower_eave_y = pavilion.meta.get("lower_eave_y", base_y + 5)
        upper_eave_y = pavilion.meta.get("upper_eave_y", base_y + 6)
        roof_layer_ys = {
            int(layer["y"])
            for layer in pavilion.meta.get("roof_layers", [])
            if "y" in layer
        } or {base_y + 5, base_y + 6, base_y + 7}
        lower_roof_cells = 0
        upper_roof_cells = 0
        raw_stairs = []
        for x in range(cx - lower_half, cx + lower_half + 1):
            for z in range(cz - lower_half, cz + lower_half + 1):
                for y in roof_layer_ys:
                    cell = compound.grid.get((x, y, z))
                    if cell is None or cell.slot != "ROOF_DARK":
                        continue
                    if y == lower_eave_y:
                        lower_roof_cells += 1
                    if y == upper_eave_y:
                        upper_roof_cells += 1
                    state = cell.state
                    if state.endswith("_stairs") and "[" not in state:
                        raw_stairs.append((x, y, z, state))
        _assert(pavilion.meta.get("roof_form") == "reference_heavy_double_eave",
                f"mansion_{i+1:03d} garden_pavilion is not reference roof form: "
                f"{pavilion.meta.get('roof_form')}")
        _assert(pavilion.meta.get("size") >= 9,
                f"mansion_{i+1:03d} garden_pavilion is too small: "
                f"{pavilion.meta.get('size')}")
        _assert(len(pavilion.meta.get("roof_layers", [])) >= 4,
                f"mansion_{i+1:03d} garden_pavilion roof is not layered enough")
        _assert(lower_roof_cells >= (2 * lower_half + 1) ** 2,
                f"mansion_{i+1:03d} garden_pavilion lower eave is incomplete: "
                f"{lower_roof_cells}")
        _assert(upper_roof_cells >= (2 * upper_half + 1) ** 2,
                f"mansion_{i+1:03d} garden_pavilion upper eave is incomplete: "
                f"{upper_roof_cells}")
        _assert(not raw_stairs,
                f"mansion_{i+1:03d} garden_pavilion has raw stair roof cap: "
                f"{raw_stairs[:4]}")
        for x, z in pavilion.meta["columns"]:
            for y in range(base_y + 1,
                           int(pavilion.meta.get("column_top_y", base_y + 4)) + 1):
                cell = compound.grid.get((x, y, z))
                _assert(cell is not None and cell.slot == "COLUMN",
                        f"mansion_{i+1:03d} garden_pavilion missing column at "
                        f"{(x, y, z)}")
                _assert("_fence" not in cell.state,
                        f"mansion_{i+1:03d} garden_pavilion column is too light: "
                        f"{(x, y, z, cell.state)}")
        _assert(len(pavilion.meta.get("lanterns", [])) >= 5,
                f"mansion_{i+1:03d} garden_pavilion missing hanging lanterns")
        _assert(len(pavilion.meta.get("visible_lamps", [])) >= 4,
                f"mansion_{i+1:03d} garden_pavilion missing visible hanging lanterns")
        for pos in pavilion.meta.get("visible_lamps", []):
            cell = compound.grid.get(tuple(pos))
            _assert(cell is not None and cell.state.startswith("minecraft:lantern["),
                    f"mansion_{i+1:03d} garden_pavilion visible lamp is not a lantern: "
                    f"{pos}")
        _assert(len(pavilion.meta.get("lantern_cages", [])) >= 16,
                f"mansion_{i+1:03d} garden_pavilion missing framed lantern cages")
        for pos in pavilion.meta.get("lantern_cages", []):
            cell = compound.grid.get(tuple(pos))
            _assert(cell is not None and ("oak_" in cell.state or "trapdoor" in cell.state),
                    f"mansion_{i+1:03d} garden_pavilion lantern cage is not light wood: "
                    f"{pos}")
        _assert(len(pavilion.meta.get("base_lanterns", [])) >= 4,
                f"mansion_{i+1:03d} garden_pavilion missing base lanterns")
        _assert(len(pavilion.meta.get("bracket_details", [])) >= 16,
                f"mansion_{i+1:03d} garden_pavilion missing visible brackets")
        visible_light_wood = 0
        for pos in pavilion.meta.get("bracket_details", []):
            cell = compound.grid.get(tuple(pos))
            if cell is not None and ("oak_" in cell.state or "stripped_oak" in cell.state):
                visible_light_wood += 1
        _assert(visible_light_wood >= 12,
                f"mansion_{i+1:03d} garden_pavilion brackets are too dark")
        _assert(len(pavilion.meta.get("ridge_ornaments", [])) >= 5,
                f"mansion_{i+1:03d} garden_pavilion missing stone roof ornaments")
        for ox, oy, oz in pavilion.meta.get("ridge_ornaments", []):
            below = compound.grid.get((ox, oy - 1, oz))
            _assert(below is not None and below.state != "minecraft:air",
                    f"mansion_{i+1:03d} garden_pavilion floating roof ornament "
                    f"{(ox, oy, oz)}")
        platform_half = pavilion.meta["platform_half"]
        deck_cells = 0
        stone_cells = 0
        for x in range(cx - platform_half, cx + platform_half + 1):
            for z in range(cz - platform_half, cz + platform_half + 1):
                support = compound.grid.get((x, base_y - 1, z))
                top = compound.grid.get((x, base_y, z))
                if support is not None and support.slot == "PLATFORM_STONE":
                    stone_cells += 1
                if top is not None and top.slot == "PATH_GALLERY":
                    deck_cells += 1
        _assert(stone_cells >= (2 * platform_half + 1) ** 2,
                f"mansion_{i+1:03d} garden_pavilion has incomplete stone base")
        _assert(deck_cells >= 40,
                f"mansion_{i+1:03d} garden_pavilion has too little wood deck")


def test_garden_pavilion_has_reference_landscape_context() -> None:
    """The reference image is not a bare wall courtyard: it has water beside the
    pavilion, bamboo, flowers/grass, and a soft path in the foreground."""
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        pavilion = next((n for n in compound.parcel_nodes
                         if n.type == "garden_pavilion"), None)
        _assert(pavilion is not None,
                f"mansion_{i+1:03d} has no garden_pavilion")
        landscape = pavilion.meta.get("reference_landscape", {})
        _assert(len(landscape.get("side_water", [])) >= 12,
                f"mansion_{i+1:03d} missing visible side water")
        _assert(len(landscape.get("flowers", [])) >= 12,
                f"mansion_{i+1:03d} missing foreground flowers/grass")
        _assert(len(landscape.get("bamboo", [])) >= 4,
                f"mansion_{i+1:03d} missing bamboo cluster")
        _assert(len(landscape.get("path_cells", [])) >= 8,
                f"mansion_{i+1:03d} missing foreground path cells")
        _assert(len(landscape.get("green_backdrop", [])) >= 10,
                f"mansion_{i+1:03d} missing green backdrop")


def test_waterside_bridge_clear_lane_has_no_lily_pads() -> None:
    """The pond keeps clear water around the bridge after the shed is removed."""
    from buildgen.compound import _chebyshev_ring
    for i in range(VARIANT_COUNT):
        compound = generate_mansion(BASE_SEED + i)
        pond = next(n for n in compound.parcel_nodes if n.type == "garden_pond")
        bridge = next(n for n in compound.parcel_nodes if n.type == "waterside_bridge")
        clear = set(bridge.cells)
        clear |= _chebyshev_ring(bridge.cells) & pond.cells
        lilies = {
            (pos[0], pos[2])
            for pos, cell in compound.grid.iter_cells()
            if cell.state.startswith("minecraft:lily_pad")
        }
        clutter = lilies & clear
        _assert(not clutter,
                f"mansion_{i+1:03d} lily pads clutter bridge clear lane: "
                f"{sorted(clutter)}")


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
