#!/usr/bin/env python3
"""Smoke-check the cultivation form registry and legacy non-invocation gates."""

from __future__ import annotations

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen import contact, ops  # noqa: E402
from buildgen.compound import generate_subbuilding  # noqa: E402
from buildgen.grid import BlockGrid  # noqa: E402
from buildgen.massing import Node  # noqa: E402
from buildgen.passes import generate_building  # noqa: E402
from buildgen.quality import CULTIVATION_MOTIFS, CULTIVATION_ROOF_FORMS  # noqa: E402
from buildgen.style import Style, load_style  # noqa: E402


def sect_style() -> Style:
    return Style({
        "style_id": "cultivation_sect",
        "material_slots": {
            "BASE_STONE": ["minecraft:polished_andesite"],
            "WALL_MAIN": ["minecraft:calcite", "minecraft:quartz_block"],
            "FRAME_WOOD": ["minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log"],
            "ROOF_DARK": ["minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_planks"],
            "DETAIL_WOOD": ["minecraft:dark_oak_trapdoor", "minecraft:dark_oak_fence", "minecraft:dark_oak_slab"],
            "LIGHTING": ["minecraft:sea_lantern", "minecraft:lantern"],
            "GROUND_PATH": ["minecraft:polished_andesite"],
            "INTERIOR_WORK": ["minecraft:crafting_table", "minecraft:furnace", "minecraft:smithing_table"],
            "INTERIOR_STORAGE": ["minecraft:barrel"],
            "SPIRIT_CRYSTAL": ["minecraft:amethyst_block"],
            "RITUAL_METAL": ["minecraft:oxidized_copper"],
            "RITUAL_ANCHOR": ["minecraft:cauldron"],
            "COLUMN": ["minecraft:quartz_pillar[axis=y]"],
            "PLATFORM_STONE": ["minecraft:polished_andesite"],
            "RIDGE_ORNAMENT": ["minecraft:sea_lantern"],
            "BALUSTRADE": ["minecraft:polished_blackstone_wall"],
        },
        "allowed_roof_types": [
            "sweeping_eave_roof",
            "hip_roof",
            "pyramidal_roof",
            "tiered_eave_roof",
        ],
        "allowed_wall_types": ["white_plaster_timber_wall"],
        "allowed_opening_styles": ["single_door_with_frame"],
        "allowed_motifs": [
            "moon_gate",
            "spirit_array",
            "incense_altar",
            "cloud_rail",
            "sect_gate_paifang",
        ],
        "forbidden_blocks": [],
        "proportions": {
            "max_flat_wall_width": 999,
            "window_min_count": 0,
            "interior_required_function_blocks": 0,
            "exterior_required_decoration_count": 0,
        },
    })


def volume(size: tuple[int, int, int]) -> Node:
    return Node(
        id="main",
        type="main_volume",
        origin=(0, 0, 0),
        size=size,
        meta={
            "foundation_h": 1,
            "wall_h": 4,
            "roof": {"type": "tiered_eave_roof", "ridge_axis": "x", "overhang": 1},
        },
    )


def detached_corners(grid: BlockGrid, corners) -> list:
    """Upturned corners that hover: not a slab/stair touching a neighbour."""
    return [tuple(c) for c in corners
            if not contact.is_attached_upturn(grid.state_at, tuple(c))]


def corner_upturn_errors(grid: BlockGrid, corners, edge_x: tuple[int, int],
                         edge_z: tuple[int, int]) -> list[str]:
    """Each ring corner rises half a block on its own level, joined to the ring.

    The corner's outer faces must reach the full block height (the raised
    quarter of an outer-corner stair) while the next eave cell along each edge
    stays half a block lower there, and nothing may sit one block above the
    corner (the old lifted top slab).
    """
    errors = []
    for x, y, z in corners:
        state = grid.state_at((x, y, z))
        ns = "north" if z == edge_z[0] else "south"
        we = "west" if x == edge_x[0] else "east"
        step_x = 1 if x == edge_x[0] else -1
        step_z = 1 if z == edge_z[0] else -1
        along_x = grid.state_at((x + step_x, y, z))
        along_z = grid.state_at((x, y, z + step_z))
        if not contact.is_attached_upturn(grid.state_at, (x, y, z)):
            errors.append(f"corner {(x, y, z)} is not attached: {state}")
        if contact.face_height(state, ns) != 2 or contact.face_height(state, we) != 2:
            errors.append(f"corner {(x, y, z)} does not rise on its outer tip: {state}")
        if (contact.face_height(along_x, ns) >= 2
                or contact.face_height(along_z, we) >= 2):
            errors.append(f"corner {(x, y, z)} is not higher than the eave beside it")
        if not grid.is_empty((x, y + 1, z)):
            errors.append(f"corner {(x, y, z)} still carries a lifted piece above it")
    return errors


def invoked_forms(ctx) -> set[str]:
    roofs = {info.get("roof_type") for info in ctx.roof_info if info.get("roof_type")}
    motifs = set(ctx.decoration_motifs)
    return (roofs & CULTIVATION_ROOF_FORMS) | (motifs & CULTIVATION_MOTIFS)


def main() -> int:
    style = sect_style()
    rng = random.Random(42)
    sweep_grid = BlockGrid()
    sweeping = ops.roof_handler("sweeping_eave_roof")(
        sweep_grid, style, rng, volume((11, 5, 11)), None)
    if len(sweeping.get("upturned_corners", [])) < 4:
        print(f"FAIL sweeping_eave_roof missing upturned corners: {sweeping}")
        return 1
    if detached_corners(sweep_grid, sweeping["upturned_corners"]):
        print("FAIL sweeping_eave_roof upturned corners hover: "
              f"{detached_corners(sweep_grid, sweeping['upturned_corners'])}")
        return 1
    if not sweeping.get("eave_brackets"):
        print(f"FAIL sweeping_eave_roof placed no dougong eave brackets: {sweeping}")
        return 1
    # The eave line must actually swoop: corner eave rises above the mid eave.
    sv = volume((11, 5, 11))
    sx0, sx1, sz0, _sz1 = ops._roof_bounds(sv, 2)

    def eave_top(x: int) -> int:
        ys = [y for y in range(0, 40)
              if (cell := sweep_grid.get((x, y, sz0))) and not cell.is_air]
        return max(ys) if ys else -1

    if eave_top(sx0) <= eave_top((sx0 + sx1) // 2):
        print(f"FAIL sweeping_eave_roof eave does not lift at the corner "
              f"(corner={eave_top(sx0)} mid={eave_top((sx0 + sx1) // 2)})")
        return 1
    hip = ops.roof_handler("hip_roof")(
        BlockGrid(), style, random.Random(42), volume((11, 5, 9)), None)
    if not hip.get("roof_cells") or hip.get("gable_cells"):
        print(f"FAIL hip_roof did not produce four-sided roof cells: {hip}")
        return 1
    pyramid = ops.roof_handler("pyramidal_roof")(
        BlockGrid(), style, random.Random(42), volume((11, 5, 11)), None)
    if not pyramid.get("ridge_ornaments"):
        print(f"FAIL pyramidal_roof did not place a finial: {pyramid}")
        return 1
    tiered_grid = BlockGrid()
    large_info = ops.roof_handler("tiered_eave_roof")(
        tiered_grid, style, rng, volume((11, 5, 11)), None)
    if large_info.get("tier_count") < 2:
        print(f"FAIL tiered_eave_roof did not produce two tiers: {large_info}")
        return 1
    if len(large_info.get("upturned_corners", [])) < 8:
        print(f"FAIL tiered_eave_roof tiers are not sweeping eaves: {large_info}")
        return 1
    if detached_corners(tiered_grid, large_info["upturned_corners"]):
        print("FAIL tiered_eave_roof upturned corners hover: "
              f"{detached_corners(tiered_grid, large_info['upturned_corners'])}")
        return 1
    small_info = ops.roof_handler("tiered_eave_roof")(
        BlockGrid(), style, random.Random(42), volume((7, 5, 7)), None)
    if small_info.get("fallback") != "single_eave":
        print(f"FAIL tiered_eave_roof did not fallback on a small footprint: {small_info}")
        return 1

    # Vertical-landmark forms resolve through the registry from the existing
    # terrace + tiered_eave_roof vocabulary (no string-prefix dispatch).
    pagoda_grid = BlockGrid()
    pagoda_info = ops.roof_handler("pagoda")(
        pagoda_grid, style, rng, volume((11, 5, 11)), None)
    if pagoda_info.get("roof_type") != "pagoda" or not pagoda_info.get("spire_cells"):
        print(f"FAIL pagoda form did not place a finial spire: {pagoda_info}")
        return 1
    cb = pagoda_info["crown_bounds"]
    crown_corners = pagoda_info.get("upturned_corners", [])
    crown_errors = corner_upturn_errors(pagoda_grid, crown_corners,
                                        (cb[0], cb[1]), (cb[2], cb[3]))
    if len(crown_corners) != 4 or crown_errors:
        print(f"FAIL pagoda crown corners are not contiguous upturns: "
              f"{crown_corners} {crown_errors}")
        return 1
    # Storey eave band (腰檐): slab ring on a beam ring, corners upturned on
    # the ring's own level, every slab/stair touching a neighbour.
    band_grid = BlockGrid()
    band_y, projection = 5, 2
    band_cells, band_corners, band_brackets = ops._pagoda_eave_band(
        band_grid, style, (0, 8, 0, 8), band_y, projection)
    band_errors = corner_upturn_errors(
        band_grid, band_corners, (-projection, 8 + projection),
        (-projection, 8 + projection))
    if len(band_corners) != 4 or any(c[1] != band_y for c in band_corners):
        band_errors.append(f"corners not on the ring level: {band_corners}")
    band_blocks = {pos: cell.state for pos, cell in band_grid.iter_cells()
                   if not cell.is_air}
    loose = contact.detached_partials(band_blocks, ground_y=None)
    if loose:
        band_errors.append(f"band cells touching nothing: {loose[:4]}")
    if not band_brackets or not band_cells:
        band_errors.append("band placed no slabs or brackets")
    if band_errors:
        print(f"FAIL pagoda eave band corners: {band_errors}")
        return 1
    pavilion_grid = BlockGrid()
    pavilion_info = ops.roof_handler("pavilion")(
        pavilion_grid, style, rng, volume((11, 5, 11)), None)
    if pavilion_info.get("roof_type") != "pavilion" or not pavilion_info.get("upturned_corners"):
        print(f"FAIL pavilion form missing sweeping upturned corners: {pavilion_info}")
        return 1
    if detached_corners(pavilion_grid, pavilion_info["upturned_corners"]):
        print("FAIL pavilion upturned corners hover: "
              f"{detached_corners(pavilion_grid, pavilion_info['upturned_corners'])}")
        return 1
    tower_info = ops.roof_handler("bell_drum_tower")(
        BlockGrid(), style, rng, volume((11, 5, 11)), None)
    if tower_info.get("roof_type") != "bell_drum_tower" or not tower_info.get("belfry_bell"):
        print(f"FAIL bell_drum_tower form did not crown a belfry bell: {tower_info}")
        return 1

    motif_nodes = {
        "moon_gate": Node("moon_gate", "decoration_patch", (0, 0, 0), (5, 5, 1)),
        "spirit_array": Node("spirit_array", "decoration_patch", (0, 0, 0), (5, 1, 5)),
        "incense_altar": Node("incense_altar", "decoration_patch", (0, 0, 0), (3, 2, 1)),
        "cloud_rail": Node("cloud_rail", "decoration_patch", (0, 0, 0), (5, 2, 1)),
        "sect_gate_paifang": Node(
            "sect_gate_paifang", "decoration_patch", (0, 0, 0), (5, 5, 1),
            meta={"facing": "north"}),
    }
    for motif, node in motif_nodes.items():
        if not ops.place_motif(motif, BlockGrid(), style, random.Random(42), node):
            print(f"FAIL motif did not place: {motif}")
            return 1

    medieval = load_style("medieval_village")
    medieval_ctx = generate_building(medieval, "small_house", "small", 20260713)
    civic_ctx = generate_building(medieval, "tavern", "tavern_v1", 20260715, group_id="civic")
    chinese = load_style("chinese_courtyard")
    chinese_ctx = generate_subbuilding(chinese, "main_hall", 20261614, "硬山",
                                       "chinese_courtyard")
    for label, ctx in (("medieval", medieval_ctx), ("civic", civic_ctx), ("chinese", chinese_ctx)):
        forms = invoked_forms(ctx)
        if forms:
            print(f"FAIL {label} invoked cultivation forms: {sorted(forms)}")
            return 1
    print("OK cultivation forms registered and legacy samples do not invoke them")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
