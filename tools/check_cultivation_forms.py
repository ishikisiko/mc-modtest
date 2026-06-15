#!/usr/bin/env python3
"""Smoke-check the cultivation form registry and legacy non-invocation gates."""

from __future__ import annotations

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen import ops  # noqa: E402
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
        },
        "allowed_roof_types": ["gable_roof", "tiered_eave_roof"],
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


def invoked_forms(ctx) -> set[str]:
    roofs = {info.get("roof_type") for info in ctx.roof_info if info.get("roof_type")}
    motifs = set(ctx.decoration_motifs)
    return (roofs & CULTIVATION_ROOF_FORMS) | (motifs & CULTIVATION_MOTIFS)


def main() -> int:
    style = sect_style()
    rng = random.Random(42)
    large_info = ops.roof_handler("tiered_eave_roof")(
        BlockGrid(), style, rng, volume((11, 5, 11)), None)
    if large_info.get("tier_count") < 2:
        print(f"FAIL tiered_eave_roof did not produce two tiers: {large_info}")
        return 1
    small_info = ops.roof_handler("tiered_eave_roof")(
        BlockGrid(), style, random.Random(42), volume((7, 5, 7)), None)
    if small_info.get("fallback") != "single_eave":
        print(f"FAIL tiered_eave_roof did not fallback on a small footprint: {small_info}")
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
    chinese = load_style("chinese_courtyard")
    chinese_ctx = generate_subbuilding(chinese, "main_hall", 20261614, "硬山",
                                       "chinese_courtyard")
    for label, ctx in (("medieval", medieval_ctx), ("chinese", chinese_ctx)):
        forms = invoked_forms(ctx)
        if forms:
            print(f"FAIL {label} invoked cultivation forms: {sorted(forms)}")
            return 1
    print("OK cultivation forms registered and legacy samples do not invoke them")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
