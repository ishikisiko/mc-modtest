#!/usr/bin/env python3
"""Generate every stage-4 structure DSL, validate, and optionally export/copy.

One command produces all building/prop/road JSON files under examples/,
validates them with validate_structure_json, exports 1.21.1 NBT via
batch_export.py (--export), and copies them into a test world via
copy_to_world.py (--world). Writes out/generated_buildings_report.json.

Usage:
  python tools/generate_all_buildings.py --mc-version 1.21.1 --force --export
  python tools/generate_all_buildings.py --mc-version 1.21.1 --force --export --world "<save>" --clean
"""

from __future__ import annotations

import argparse
import json
import os
import random
import subprocess
import sys
from collections import OrderedDict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from json_to_nbt import SUPPORTED_MC_VERSION, parse_block_state, structure_json_to_root_nbt  # noqa: E402
from validate_structure_json import ValidationError, validate_structure  # noqa: E402

REPORT_PATH = os.path.join("out", "generated_buildings_report.json")
OUTPUT_DIR = os.path.join("out", "1_21_1")


# ---------------------------------------------------------------------------
# DSL helpers
# ---------------------------------------------------------------------------

def _set(pos, state):
    return {"op": "set", "pos": list(pos), "state": state}


def _fill(from_pos, to_pos, state):
    return {"op": "fill", "from": list(from_pos), "to": list(to_pos), "state": state}


def _line(from_pos, to_pos, state):
    return {"op": "line", "from": list(from_pos), "to": list(to_pos), "state": state}


def _dsl(name, size, palette, ops, metadata, fill_air=True):
    return OrderedDict([
        ("format", "mc_structure_dsl_v1"),
        ("name", name),
        ("mc_version", SUPPORTED_MC_VERSION),
        ("data_version", 3955),
        ("author", "generate_all_buildings.py"),
        ("metadata", metadata),
        ("size", list(size)),
        ("fill_air", fill_air),
        ("palette", palette),
        ("ops", ops),
    ])


def _metadata(structure_id, category, size, entrances, connections, weight, tags):
    return OrderedDict([
        ("id", structure_id),
        ("category", category),
        ("size", list(size)),
        ("entrances", entrances),
        ("connections", connections),
        ("weight", weight),
        ("tags", tags),
    ])


STAIRS = "minecraft:{wood}_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]"
SLAB = "minecraft:{material}_slab[type=bottom,waterlogged=false]"
DOOR = "minecraft:{wood}_door[facing={facing},half={half},hinge={hinge},open=false,powered=false]"
FENCE = "minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]"
WALL_POST = "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"
LANTERN_FLOOR = "minecraft:lantern[hanging=false,waterlogged=false]"
LANTERN_HANGING = "minecraft:lantern[hanging=true,waterlogged=false]"


def _gable_roof(ops, x0, x1, z0, z1, y_base, roof_n, roof_s, ridge, ridge_width=None):
    """Build a north/south gabled stair roof (ridge along x) starting at y_base.

    Stair rows climb inward from both z edges; the slab ridge sits at the same
    height as the last stair rows, covering the remaining ridge_width columns
    (defaults to 1 for an odd z span, 2 for an even one).
    """
    if ridge_width is None:
        ridge_width = 1 if (z1 - z0 + 1) % 2 == 1 else 2
    y = y_base
    zn, zs = z0, z1
    while True:
        ops.append(_fill([x0, y, zn], [x1, y, zn], roof_n))
        ops.append(_fill([x0, y, zs], [x1, y, zs], roof_s))
        if zs - zn - 1 <= ridge_width:
            ops.append(_fill([x0, y, zn + 1], [x1, y, zs - 1], ridge))
            return y
        zn += 1
        zs -= 1
        y += 1


# ---------------------------------------------------------------------------
# Buildings
# ---------------------------------------------------------------------------

def build_small_house_01(rng):
    size = [11, 8, 10]
    palette = {
        "foundation": "minecraft:cobblestone",
        "floor": "minecraft:oak_planks",
        "wall_lower": "minecraft:cobblestone",
        "wall_upper": "minecraft:oak_planks",
        "corner_log": "minecraft:oak_log[axis=y]",
        "window": "minecraft:glass",
        "door_lower": DOOR.format(wood="oak", facing="north", half="lower", hinge="left"),
        "door_upper": DOOR.format(wood="oak", facing="north", half="upper", hinge="left"),
        "entry_stair": STAIRS.format(wood="cobblestone", facing="south").replace("cobblestone_stairs", "cobblestone_stairs"),
        "roof_n": STAIRS.format(wood="spruce", facing="south"),
        "roof_s": STAIRS.format(wood="spruce", facing="north"),
        "ridge": SLAB.format(material="spruce"),
        "gable": "minecraft:oak_planks",
        "crafting_table": "minecraft:crafting_table",
        "furnace": "minecraft:furnace[facing=east,lit=false]",
        "barrel": "minecraft:barrel[facing=up,open=false]",
        "lantern_floor": LANTERN_FLOOR,
    }
    palette["entry_stair"] = "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
    ops = [
        _fill([1, 0, 1], [9, 0, 8], "foundation"),
        _fill([2, 0, 2], [8, 0, 7], "floor"),
        _set([5, 0, 0], "foundation"),
        _set([5, 1, 0], "entry_stair"),
        # walls: cobblestone base course, oak above
        _fill([1, 1, 1], [9, 1, 1], "wall_lower"),
        _fill([1, 1, 8], [9, 1, 8], "wall_lower"),
        _fill([1, 1, 2], [1, 1, 7], "wall_lower"),
        _fill([9, 1, 2], [9, 1, 7], "wall_lower"),
        _fill([1, 2, 1], [9, 4, 1], "wall_upper"),
        _fill([1, 2, 8], [9, 4, 8], "wall_upper"),
        _fill([1, 2, 2], [1, 4, 7], "wall_upper"),
        _fill([9, 2, 2], [9, 4, 7], "wall_upper"),
        _line([1, 1, 1], [1, 4, 1], "corner_log"),
        _line([9, 1, 1], [9, 4, 1], "corner_log"),
        _line([1, 1, 8], [1, 4, 8], "corner_log"),
        _line([9, 1, 8], [9, 4, 8], "corner_log"),
        _set([5, 1, 1], "door_lower"),
        _set([5, 2, 1], "door_upper"),
    ]
    for pos in ([3, 2, 1], [7, 2, 1], [3, 2, 8], [7, 2, 8], [1, 2, 4], [1, 2, 5], [9, 2, 4], [9, 2, 5]):
        ops.append(_set(pos, "window"))
    ops += [
        _set([2, 1, 2], "crafting_table"),
        _set([2, 1, 7], "furnace"),
        _set([8, 1, 7], "barrel"),
        _set([8, 2, 7], "lantern_floor"),
    ]
    _gable_roof(ops, 0, 10, 0, 9, 4, "roof_n", "roof_s", "ridge")
    ops += [
        _fill([1, 5, 2], [1, 5, 7], "gable"),
        _fill([9, 5, 2], [9, 5, 7], "gable"),
        _fill([1, 6, 3], [1, 6, 6], "gable"),
        _fill([9, 6, 3], [9, 6, 6], "gable"),
    ]
    metadata = _metadata(
        "myvillage:small_house_01", "building", size,
        [{"pos": [5, 1, 0], "facing": "north"}], [],
        10, ["house", "medieval", "oak", "cobblestone"],
    )
    return _dsl("small_house_01", size, palette, ops, metadata)


def build_small_house_02(rng):
    size = [9, 9, 9]
    palette = {
        "foundation": "minecraft:stone_bricks",
        "wall_lower": "minecraft:stone_bricks",
        "wall_upper": "minecraft:white_terracotta",
        "corner_log": "minecraft:spruce_log[axis=y]",
        "floor2": "minecraft:spruce_planks",
        "window": "minecraft:glass",
        "door_lower": DOOR.format(wood="spruce", facing="south", half="lower", hinge="left"),
        "door_upper": DOOR.format(wood="spruce", facing="south", half="upper", hinge="left"),
        "entry_stair": "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
        "roof_n": "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
        "roof_s": "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
        "ridge": SLAB.format(material="stone_brick"),
        "gable": "minecraft:white_terracotta",
        "ladder": "minecraft:ladder[facing=south,waterlogged=false]",
        "barrel": "minecraft:barrel[facing=up,open=false]",
        "lantern_floor": LANTERN_FLOOR,
        "bookshelf": "minecraft:bookshelf",
        "air": "minecraft:air",
    }
    ops = [
        _fill([1, 0, 1], [7, 0, 7], "foundation"),
        _set([4, 0, 8], "foundation"),
        _set([4, 1, 8], "entry_stair"),
        _fill([1, 1, 1], [7, 2, 1], "wall_lower"),
        _fill([1, 1, 7], [7, 2, 7], "wall_lower"),
        _fill([1, 1, 2], [1, 2, 6], "wall_lower"),
        _fill([7, 1, 2], [7, 2, 6], "wall_lower"),
        _fill([1, 3, 1], [7, 5, 1], "wall_upper"),
        _fill([1, 3, 7], [7, 5, 7], "wall_upper"),
        _fill([1, 3, 2], [1, 5, 6], "wall_upper"),
        _fill([7, 3, 2], [7, 5, 6], "wall_upper"),
        _line([1, 1, 1], [1, 5, 1], "corner_log"),
        _line([7, 1, 1], [7, 5, 1], "corner_log"),
        _line([1, 1, 7], [1, 5, 7], "corner_log"),
        _line([7, 1, 7], [7, 5, 7], "corner_log"),
        _fill([2, 3, 2], [6, 3, 6], "floor2"),
        _set([6, 3, 2], "air"),
        _line([6, 1, 2], [6, 3, 2], "ladder"),
        _set([4, 1, 7], "door_lower"),
        _set([4, 2, 7], "door_upper"),
    ]
    for pos in ([2, 2, 7], [6, 2, 7], [4, 2, 1], [1, 2, 4], [7, 2, 4],
                [2, 4, 7], [6, 4, 7], [2, 4, 1], [6, 4, 1], [1, 4, 4], [7, 4, 4]):
        ops.append(_set(pos, "window"))
    ops += [
        _set([2, 1, 2], "barrel"),
        _set([2, 2, 2], "lantern_floor"),
        _set([2, 4, 2], "bookshelf"),
        _set([2, 5, 2], "lantern_floor"),
    ]
    _gable_roof(ops, 0, 8, 0, 8, 5, "roof_n", "roof_s", "ridge")
    ops += [
        _fill([1, 6, 2], [1, 6, 6], "gable"),
        _fill([7, 6, 2], [7, 6, 6], "gable"),
        _fill([1, 7, 3], [1, 7, 5], "gable"),
        _fill([7, 7, 3], [7, 7, 5], "gable"),
    ]
    metadata = _metadata(
        "myvillage:small_house_02", "building", size,
        [{"pos": [4, 1, 8], "facing": "south"}], [],
        10, ["house", "medieval", "spruce", "stone_bricks", "two_story"],
    )
    return _dsl("small_house_02", size, palette, ops, metadata)


def build_workshop_01(rng):
    size = [13, 8, 11]
    palette = {
        "foundation": "minecraft:cobblestone",
        "floor": "minecraft:smooth_stone",
        "wall_lower": "minecraft:cobblestone",
        "wall_upper": "minecraft:oak_planks",
        "corner_log": "minecraft:oak_log[axis=y]",
        "window": "minecraft:glass",
        "door_lower": DOOR.format(wood="oak", facing="north", half="lower", hinge="left"),
        "door_upper": DOOR.format(wood="oak", facing="north", half="upper", hinge="left"),
        "roof_n": STAIRS.format(wood="spruce", facing="south"),
        "roof_s": STAIRS.format(wood="spruce", facing="north"),
        "ridge": SLAB.format(material="spruce"),
        "gable": "minecraft:oak_planks",
        "post": "minecraft:oak_log[axis=y]",
        "furnace": "minecraft:furnace[facing=north,lit=false]",
        "crafting_table": "minecraft:crafting_table",
        "barrel": "minecraft:barrel[facing=up,open=false]",
        "anvil": "minecraft:anvil[facing=east]",
        "fence": FENCE,
        "lantern_floor": LANTERN_FLOOR,
    }
    ops = [
        _fill([1, 0, 1], [11, 0, 9], "foundation"),
        _fill([2, 0, 2], [7, 0, 8], "floor"),
        _set([4, 0, 0], "foundation"),
        # enclosed room (west)
        _fill([1, 1, 1], [7, 1, 1], "wall_lower"),
        _fill([1, 1, 9], [7, 1, 9], "wall_lower"),
        _fill([1, 1, 2], [1, 1, 8], "wall_lower"),
        _fill([7, 1, 2], [7, 1, 8], "wall_lower"),
        _fill([1, 2, 1], [7, 4, 1], "wall_upper"),
        _fill([1, 2, 9], [7, 4, 9], "wall_upper"),
        _fill([1, 2, 2], [1, 4, 8], "wall_upper"),
        _fill([7, 2, 2], [7, 4, 8], "wall_upper"),
        _line([1, 1, 1], [1, 4, 1], "corner_log"),
        _line([7, 1, 1], [7, 4, 1], "corner_log"),
        _line([1, 1, 9], [1, 4, 9], "corner_log"),
        _line([7, 1, 9], [7, 4, 9], "corner_log"),
        _set([4, 1, 1], "door_lower"),
        _set([4, 2, 1], "door_upper"),
        # archway from room into the open-air smithy
        _fill([7, 1, 4], [7, 2, 6], "minecraft:air"),
    ]
    for pos in ([2, 2, 1], [6, 2, 1], [3, 2, 9], [5, 2, 9], [1, 2, 4], [1, 2, 6]):
        ops.append(_set(pos, "window"))
    ops += [
        _set([2, 1, 8], "crafting_table"),
        _set([6, 1, 8], "barrel"),
        _set([6, 2, 8], "lantern_floor"),
        # open work area (east) under the shared roof
        _line([11, 1, 1], [11, 4, 1], "post"),
        _line([11, 1, 9], [11, 4, 9], "post"),
        _set([10, 1, 8], "furnace"),
        _set([10, 2, 8], "furnace"),
        _set([9, 1, 5], "anvil"),
        _set([11, 1, 5], "fence"),
        _set([11, 2, 5], "lantern_floor"),
    ]
    _gable_roof(ops, 0, 12, 0, 10, 4, "roof_n", "roof_s", "ridge", ridge_width=3)
    ops += [
        _fill([1, 5, 2], [1, 5, 8], "gable"),
        _fill([1, 6, 3], [1, 6, 7], "gable"),
    ]
    metadata = _metadata(
        "myvillage:workshop_01", "building", size,
        [{"pos": [4, 1, 0], "facing": "north"}, {"pos": [12, 1, 5], "facing": "east"}], [],
        6, ["workshop", "smithy", "medieval", "oak", "cobblestone"],
    )
    return _dsl("workshop_01", size, palette, ops, metadata)


def build_storage_01(rng):
    size = [9, 7, 9]
    palette = {
        "foundation": "minecraft:cobblestone",
        "floor": "minecraft:coarse_dirt",
        "wall": "minecraft:spruce_planks",
        "corner_log": "minecraft:oak_log[axis=y]",
        "window": "minecraft:glass",
        "door_left_lower": DOOR.format(wood="spruce", facing="north", half="lower", hinge="left"),
        "door_left_upper": DOOR.format(wood="spruce", facing="north", half="upper", hinge="left"),
        "door_right_lower": DOOR.format(wood="spruce", facing="north", half="lower", hinge="right"),
        "door_right_upper": DOOR.format(wood="spruce", facing="north", half="upper", hinge="right"),
        "roof_n": STAIRS.format(wood="oak", facing="south"),
        "roof_s": STAIRS.format(wood="oak", facing="north"),
        "ridge": SLAB.format(material="oak"),
        "gable": "minecraft:spruce_planks",
        "beam": "minecraft:oak_log[axis=x]",
        "barrel": "minecraft:barrel[facing=up,open=false]",
        "hay": "minecraft:hay_block[axis=y]",
        "crafting_table": "minecraft:crafting_table",
        "lantern_floor": LANTERN_FLOOR,
    }
    ops = [
        _fill([1, 0, 1], [7, 0, 7], "foundation"),
        _fill([2, 0, 2], [6, 0, 6], "floor"),
        _fill([3, 0, 0], [4, 0, 0], "foundation"),
        _fill([1, 1, 1], [7, 3, 1], "wall"),
        _fill([1, 1, 7], [7, 3, 7], "wall"),
        _fill([1, 1, 2], [1, 3, 6], "wall"),
        _fill([7, 1, 2], [7, 3, 6], "wall"),
        _line([1, 1, 1], [1, 3, 1], "corner_log"),
        _line([7, 1, 1], [7, 3, 1], "corner_log"),
        _line([1, 1, 7], [1, 3, 7], "corner_log"),
        _line([7, 1, 7], [7, 3, 7], "corner_log"),
        # cross beam under the ridge
        _line([1, 3, 4], [7, 3, 4], "beam"),
        # wide double door
        _set([3, 1, 1], "door_right_lower"),
        _set([3, 2, 1], "door_right_upper"),
        _set([4, 1, 1], "door_left_lower"),
        _set([4, 2, 1], "door_left_upper"),
        _set([1, 2, 4], "window"),
        _set([7, 2, 4], "window"),
        _set([4, 2, 7], "window"),
        _fill([5, 1, 6], [6, 1, 6], "barrel"),
        _set([6, 2, 6], "barrel"),
        _set([6, 1, 5], "barrel"),
        _fill([2, 1, 5], [2, 1, 6], "hay"),
        _set([2, 2, 6], "hay"),
        _set([6, 1, 2], "crafting_table"),
        _set([5, 2, 6], "lantern_floor"),
    ]
    _gable_roof(ops, 0, 8, 0, 8, 3, "roof_n", "roof_s", "ridge")
    ops += [
        _fill([1, 4, 2], [1, 4, 6], "gable"),
        _fill([7, 4, 2], [7, 4, 6], "gable"),
        _fill([1, 5, 3], [1, 5, 5], "gable"),
        _fill([7, 5, 3], [7, 5, 5], "gable"),
    ]
    metadata = _metadata(
        "myvillage:storage_01", "building", size,
        [{"pos": [3, 1, 0], "facing": "north"}, {"pos": [4, 1, 0], "facing": "north"}], [],
        6, ["storage", "barn", "medieval", "spruce", "cobblestone"],
    )
    return _dsl("storage_01", size, palette, ops, metadata)


# ---------------------------------------------------------------------------
# Props
# ---------------------------------------------------------------------------

def build_well_01(rng):
    size = [5, 6, 5]
    palette = {
        "base": "minecraft:cobblestone",
        "rim": "minecraft:stone_bricks",
        "water": "minecraft:water",
        "post": WALL_POST,
        "roof_plank": "minecraft:spruce_planks",
        "roof_top": SLAB.format(material="spruce"),
        "roof_n": STAIRS.format(wood="spruce", facing="south"),
        "roof_s": STAIRS.format(wood="spruce", facing="north"),
        "roof_w": STAIRS.format(wood="spruce", facing="east"),
        "roof_e": STAIRS.format(wood="spruce", facing="west"),
    }
    ops = [
        _fill([1, 0, 1], [3, 0, 3], "base"),
        _fill([1, 1, 1], [3, 1, 3], "rim"),
        _set([2, 1, 2], "water"),
        _line([1, 2, 1], [1, 3, 1], "post"),
        _line([3, 2, 1], [3, 3, 1], "post"),
        _line([1, 2, 3], [1, 3, 3], "post"),
        _line([3, 2, 3], [3, 3, 3], "post"),
        _fill([0, 4, 0], [4, 4, 0], "roof_n"),
        _fill([0, 4, 4], [4, 4, 4], "roof_s"),
        _fill([0, 4, 1], [0, 4, 3], "roof_w"),
        _fill([4, 4, 1], [4, 4, 3], "roof_e"),
        _fill([1, 4, 1], [3, 4, 3], "roof_plank"),
        _set([2, 5, 2], "roof_top"),
    ]
    metadata = _metadata(
        "myvillage:well_01", "prop", size, [], [],
        4, ["well", "medieval", "stone_bricks", "water"],
    )
    return _dsl("well_01", size, palette, ops, metadata)


def build_lamp_post_01(rng):
    size = [2, 6, 1]
    palette = {
        "base": WALL_POST,
        "pole": FENCE,
        "lamp": LANTERN_HANGING,
    }
    ops = [
        _set([0, 0, 0], "base"),
        _line([0, 1, 0], [0, 4, 0], "pole"),
        # arm with a hanging lantern
        _set([0, 5, 0], "pole"),
        _set([1, 5, 0], "pole"),
        _set([1, 4, 0], "lamp"),
    ]
    metadata = _metadata(
        "myvillage:lamp_post_01", "prop", size, [], [],
        8, ["lamp", "light", "medieval", "fence", "lantern"],
    )
    return _dsl("lamp_post_01", size, palette, ops, metadata, fill_air=False)


def build_market_stall_01(rng):
    size = [5, 4, 5]
    palette = {
        "post": FENCE,
        "counter_base": "minecraft:barrel[facing=up,open=false]",
        "counter_top": SLAB.format(material="oak"),
        "canopy_white": "minecraft:white_wool",
        "canopy_red": "minecraft:red_wool",
        "hay": "minecraft:hay_block[axis=y]",
        "barrel": "minecraft:barrel[facing=up,open=false]",
    }
    ops = [
        _line([0, 0, 0], [0, 2, 0], "post"),
        _line([4, 0, 0], [4, 2, 0], "post"),
        _line([0, 0, 4], [0, 2, 4], "post"),
        _line([4, 0, 4], [4, 2, 4], "post"),
        # front counter
        _fill([1, 0, 0], [3, 0, 0], "counter_base"),
        _fill([1, 1, 0], [3, 1, 0], "counter_top"),
        # side counter
        _fill([4, 0, 1], [4, 0, 3], "counter_base"),
        _fill([4, 1, 1], [4, 1, 3], "counter_top"),
        # wares
        _set([3, 0, 3], "hay"),
        _set([1, 0, 3], "barrel"),
        _set([1, 1, 3], "hay"),
    ]
    for z in range(5):
        ops.append(_fill([0, 3, z], [4, 3, z], "canopy_white" if z % 2 == 0 else "canopy_red"))
    metadata = _metadata(
        "myvillage:market_stall_01", "prop", size, [], [],
        5, ["market", "stall", "medieval", "wool", "barrel"],
    )
    return _dsl("market_stall_01", size, palette, ops, metadata, fill_air=False)


# ---------------------------------------------------------------------------
# Roads
# ---------------------------------------------------------------------------

ROAD_PALETTE = {
    "path": "minecraft:dirt_path",
    "gravel": "minecraft:gravel",
    "coarse": "minecraft:coarse_dirt",
    "cobble": "minecraft:cobblestone",
}
ROAD_ACCENTS = ["gravel", "coarse", "cobble"]


def _scatter_accents(rng, ops, cells, count):
    cells = sorted(cells)
    rng.shuffle(cells)
    for x, z in cells[:count]:
        ops.append(_set([x, 0, z], rng.choice(ROAD_ACCENTS)))


def build_path_straight_01(rng):
    size = [5, 1, 7]
    ops = [
        _fill([0, 0, 0], [4, 0, 6], "path"),
        _line([2, 0, 0], [2, 0, 6], "gravel"),
    ]
    cells = [(x, z) for x in range(5) for z in range(7) if x != 2]
    _scatter_accents(rng, ops, cells, 6)
    metadata = _metadata(
        "myvillage:path_straight_01", "road", size, [],
        [{"pos": [2, 0, 0], "facing": "north"}, {"pos": [2, 0, 6], "facing": "south"}],
        10, ["road", "path", "straight"],
    )
    return _dsl("path_straight_01", size, ROAD_PALETTE, ops, metadata, fill_air=False)


def build_path_corner_01(rng):
    size = [7, 1, 7]
    ops = [
        # north arm and east arm of the L
        _fill([2, 0, 0], [4, 0, 4], "path"),
        _fill([2, 0, 2], [6, 0, 4], "path"),
        # cobblestone curb on the outer (west) edge
        _line([1, 0, 0], [1, 0, 4], "cobble"),
        # gravel center line through the turn
        _line([3, 0, 0], [3, 0, 3], "gravel"),
        _line([3, 0, 3], [6, 0, 3], "gravel"),
    ]
    arm_cells = {(x, z) for x in range(2, 5) for z in range(5)}
    arm_cells |= {(x, z) for x in range(2, 7) for z in range(2, 5)}
    cells = [(x, z) for (x, z) in arm_cells if not (x == 3 and z <= 3) and not (z == 3 and x >= 3)]
    _scatter_accents(rng, ops, cells, 6)
    metadata = _metadata(
        "myvillage:path_corner_01", "road", size, [],
        [{"pos": [3, 0, 0], "facing": "north"}, {"pos": [6, 0, 3], "facing": "east"}],
        8, ["road", "path", "corner"],
    )
    return _dsl("path_corner_01", size, ROAD_PALETTE, ops, metadata, fill_air=False)


BUILDERS = OrderedDict([
    ("examples/buildings/small_house_01.json", build_small_house_01),
    ("examples/buildings/small_house_02.json", build_small_house_02),
    ("examples/buildings/workshop_01.json", build_workshop_01),
    ("examples/buildings/storage_01.json", build_storage_01),
    ("examples/props/well_01.json", build_well_01),
    ("examples/props/lamp_post_01.json", build_lamp_post_01),
    ("examples/props/market_stall_01.json", build_market_stall_01),
    ("examples/roads/path_straight_01.json", build_path_straight_01),
    ("examples/roads/path_corner_01.json", build_path_corner_01),
])

MIN_BLOCK_COUNT = {
    "small_house_01": 150,
    "small_house_02": 150,
    "workshop_01": 180,
    "storage_01": 160,
    "well_01": 40,
    "lamp_post_01": 8,
    "market_stall_01": 50,
    "path_straight_01": 20,
    "path_corner_01": 25,
}


def _non_air_block_count(root):
    air_indexes = {
        i for i, state in enumerate(root.value["palette"].value)
        if state.value["Name"].value == "minecraft:air"
    }
    return sum(1 for b in root.value["blocks"].value if b.value["state"].value not in air_indexes)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate all stage-4 structure DSL JSON files")
    parser.add_argument("--mc-version", default=SUPPORTED_MC_VERSION,
                        help=f"Target Minecraft version (only {SUPPORTED_MC_VERSION} is supported)")
    parser.add_argument("--export", action="store_true", help="Export NBT via batch_export.py after generating")
    parser.add_argument("--world", default=None, help="Copy exported NBT into this world save (implies --export)")
    parser.add_argument("--clean", action="store_true", help="Pass --clean to copy_to_world.py")
    parser.add_argument("--force", action="store_true", help="Overwrite existing example JSON files")
    parser.add_argument("--seed", type=int, default=0, help="Seed for reproducible material variation")
    args = parser.parse_args()

    if args.mc_version != SUPPORTED_MC_VERSION:
        parser.error(f"unsupported --mc-version {args.mc_version!r}; only {SUPPORTED_MC_VERSION} is supported")
    if args.world:
        args.export = True

    rng = random.Random(args.seed)
    entries = []
    generated = validated = failed = 0

    for rel_path, builder in BUILDERS.items():
        abs_path = os.path.join(REPO_ROOT, rel_path)
        name = os.path.splitext(os.path.basename(rel_path))[0]
        entry = OrderedDict([
            ("source", rel_path), ("id", None), ("metadata", None),
            ("block_count", None), ("palette_count", None), ("used_blocks", None),
            ("validation_status", "skipped"), ("export_status", "skipped"),
            ("copy_status", "skipped"), ("errors", []),
        ])
        entries.append(entry)
        try:
            data = builder(rng)
            entry["id"] = data["metadata"]["id"]
            entry["metadata"] = data["metadata"]
            if os.path.exists(abs_path) and not args.force:
                with open(abs_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                entry["metadata"] = data.get("metadata")
                print(f"KEEP  {rel_path} (exists; use --force to overwrite)")
            else:
                os.makedirs(os.path.dirname(abs_path), exist_ok=True)
                with open(abs_path, "w", encoding="utf-8") as f:
                    json.dump(data, f, indent=2, ensure_ascii=False)
                    f.write("\n")
                generated += 1
                print(f"WRITE {rel_path}")

            validate_structure(data)
            entry["validation_status"] = "ok"
            validated += 1

            root = structure_json_to_root_nbt(data, SUPPORTED_MC_VERSION, None)
            entry["block_count"] = len(root.value["blocks"].value)
            entry["palette_count"] = len(root.value["palette"].value)
            entry["used_blocks"] = sorted({
                parse_block_state(s.value["Name"].value)[0] for s in root.value["palette"].value
            })
            non_air = _non_air_block_count(root)
            minimum = MIN_BLOCK_COUNT.get(name, 0)
            if non_air < minimum:
                raise ValidationError(f"non-air block count {non_air} is below the minimum {minimum} for {name}")
        except (ValidationError, ValueError, OSError, json.JSONDecodeError) as exc:
            entry["validation_status"] = "failed"
            entry["errors"].append(str(exc))
            failed += 1
            print(f"FAIL  {rel_path}: {exc}", file=sys.stderr)

    exported = copied = 0
    if args.export and failed == 0:
        result = subprocess.run(
            [sys.executable, os.path.join(SCRIPT_DIR, "batch_export.py"), "--mc-version", SUPPORTED_MC_VERSION],
            cwd=REPO_ROOT,
        )
        for entry in entries:
            name = os.path.splitext(os.path.basename(entry["source"]))[0]
            output_path = os.path.join("out", "1_21_1", f"{name}.nbt")
            if result.returncode == 0 and os.path.exists(os.path.join(REPO_ROOT, output_path)):
                entry["export_status"] = "ok"
                entry["output_path"] = output_path.replace(os.sep, "/")
                exported += 1
            else:
                entry["export_status"] = "failed"
                entry["errors"].append("batch export failed")
        if result.returncode != 0:
            failed += 1
    elif args.export:
        print("skipping export because generation/validation failed", file=sys.stderr)

    if args.world and exported:
        cmd = [sys.executable, os.path.join(SCRIPT_DIR, "copy_to_world.py"), "--world", args.world]
        if args.clean:
            cmd.append("--clean")
        result = subprocess.run(cmd, cwd=REPO_ROOT)
        status = "ok" if result.returncode == 0 else "failed"
        for entry in entries:
            if entry["export_status"] == "ok":
                entry["copy_status"] = status
                if status == "ok":
                    copied += 1
        if result.returncode != 0:
            failed += 1

    report = OrderedDict([
        ("mc_version", SUPPORTED_MC_VERSION),
        ("seed", args.seed),
        ("generated_json_count", generated),
        ("validated_count", validated),
        ("exported_nbt_count", exported),
        ("copied_count", copied),
        ("failed_count", failed),
        ("structures", entries),
    ])
    report_path = os.path.join(REPO_ROOT, REPORT_PATH)
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
        f.write("\n")

    print("\nsummary:")
    for key in ("generated_json_count", "validated_count", "exported_nbt_count", "copied_count", "failed_count"):
        print(f"  {key}: {report[key]}")
    for entry in entries:
        meta = entry["metadata"] or {}
        print(f"  {entry['id']}: category={meta.get('category')}, size={meta.get('size')}, "
              f"blocks={entry['block_count']}, palette={entry['palette_count']}, "
              f"nbt={entry.get('output_path', '-')}")
    print(f"report: {REPORT_PATH}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
