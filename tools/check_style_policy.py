#!/usr/bin/env python3
"""Check active-style forbidden block behavior for cultivation spirit materials."""

from __future__ import annotations

import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen.grid import AIR, BlockGrid, PRIORITY  # noqa: E402
from buildgen.massing import MassingGraph, Node  # noqa: E402
from buildgen.passes import BuildContext  # noqa: E402
from buildgen.quality import quality_check  # noqa: E402
from buildgen.style import Style  # noqa: E402


def style_data(style_id: str, forbidden_blocks: list[str], spirit_slots: bool) -> dict:
    slots = {
        "BASE_STONE": ["minecraft:stone_bricks"],
        "WALL_MAIN": ["minecraft:oak_planks"],
        "FRAME_WOOD": ["minecraft:oak_log"],
        "ROOF_DARK": ["minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_planks"],
        "DETAIL_WOOD": ["minecraft:oak_trapdoor", "minecraft:oak_fence", "minecraft:oak_slab"],
        "LIGHTING": ["minecraft:lantern"],
        "GROUND_PATH": ["minecraft:gravel"],
        "INTERIOR_WORK": ["minecraft:crafting_table", "minecraft:furnace", "minecraft:smithing_table"],
        "INTERIOR_STORAGE": ["minecraft:barrel"],
    }
    if spirit_slots:
        slots["SPIRIT_CRYSTAL"] = ["minecraft:amethyst_block"]
        slots["RITUAL_METAL"] = ["minecraft:oxidized_copper"]
    return {
        "style_id": style_id,
        "material_slots": slots,
        "allowed_roof_types": ["gable_roof"],
        "allowed_wall_types": ["timber_frame_wall"],
        "allowed_opening_styles": ["single_door_with_frame"],
        "allowed_motifs": ["lantern_post"],
        "forbidden_blocks": forbidden_blocks,
        "proportions": {
            "max_flat_wall_width": 999,
            "window_min_count": 1,
            "interior_required_function_blocks": 1,
            "exterior_required_decoration_count": 0,
        },
    }


def probe_context(style: Style, spirit_block: str) -> BuildContext:
    graph = MassingGraph(meta={"door": {"volume": "main", "wall": "front", "x": 2}})
    graph.add(Node(
        id="main",
        type="main_volume",
        origin=(0, 0, 0),
        size=(5, 4, 5),
        meta={"foundation_h": 1, "wall_h": 3},
    ))
    grid = BlockGrid()
    grid.set((2, 1, 0), "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]",
             ["OPENING"], PRIORITY["OPENING"])
    grid.set((2, 2, 0), "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]",
             ["OPENING"], PRIORITY["OPENING"])
    grid.set((1, 2, 0), "minecraft:glass", ["OPENING", "FACADE"], PRIORITY["OPENING"])
    grid.set((1, 1, 1), "minecraft:crafting_table", ["INTERIOR"], PRIORITY["INTERIOR"])
    grid.set((3, 1, 1), spirit_block, ["DETAIL"], PRIORITY["DETAIL"])
    grid.set((2, 1, -1), AIR, ["AIR_CARVE", "PROTECTED"], PRIORITY["OPENING"], force=True)
    grid.set((2, 2, -1), AIR, ["AIR_CARVE", "PROTECTED"], PRIORITY["OPENING"], force=True)
    return BuildContext(
        style=style,
        archetype="policy_probe",
        scale_tier="policy_probe",
        seed=0,
        rng=random.Random(0),
        graph=graph,
        grid=grid,
        door_info={"front": (2, 1, -1)},
    )


def has_forbidden_error(report: dict) -> bool:
    return any(error.startswith("forbidden_blocks:") for error in report["errors"])


def main() -> int:
    town = Style(style_data("cultivation_town", ["quartz", "copper"], False))
    sect = Style(style_data("cultivation_sect", [], True))
    assert not town.has_slot("SPIRIT_CRYSTAL")
    assert sect.has_slot("SPIRIT_CRYSTAL")
    assert town.optional_slot_entry("SPIRIT_CRYSTAL", "amethyst") is None
    assert sect.optional_slot_entry("SPIRIT_CRYSTAL", "amethyst") == "minecraft:amethyst_block"

    town_report = quality_check(probe_context(town, "minecraft:quartz_block"), "cultivation_town/policy_probe")
    sect_report = quality_check(probe_context(sect, "minecraft:quartz_block"), "cultivation_sect/policy_probe")
    if not has_forbidden_error(town_report):
        print(f"FAIL town style did not reject quartz: {town_report['errors']}")
        return 1
    if has_forbidden_error(sect_report):
        print(f"FAIL sect style rejected quartz: {sect_report['errors']}")
        return 1
    print("OK style forbidden-block policy is per-style")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
