"""Build Ops layer (the component DSL, construction only).

Every op:
  - resolves materials through Style slots (no hardcoded concrete blocks
    except derived items such as the door of the FRAME_WOOD species)
  - writes tagged, prioritized cells into the BlockGrid
  - respects PROTECTED cells (grid.set returns False instead of overwriting)

Facing conventions follow docs/blockstate_notes.md (visually verified):
  stairs/doors face the "inward / toward ridge" direction,
  trapdoor trim faces the outward direction with open=true.
"""

from __future__ import annotations

import random
import warnings
from typing import Any, Callable, Dict, Iterable, List, Optional, Tuple

from . import plaque_bindings
from .grid import AIR, BlockGrid, PRIORITY
from .massing import INWARD_FACING, OUTWARD_FACING, Node, WALL_OUTWARD
from .orientation import orient_block
from .style import Style

Pos = Tuple[int, int, int]
RoofHandler = Callable[[BlockGrid, Style, random.Random, Node, Optional[Any]], dict]
MotifHandler = Callable[[BlockGrid, Style, random.Random, Node, Optional[Node]], bool]

DIR_VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west",
            "front": "back", "back": "front"}
ROOF_REGISTRY: Dict[str, RoofHandler] = {}
MOTIF_REGISTRY: Dict[str, MotifHandler] = {}


def register_roof(name: str, handler: RoofHandler) -> None:
    ROOF_REGISTRY[name] = handler


def register_motif(name: str, handler: MotifHandler) -> None:
    MOTIF_REGISTRY[name] = handler


def roof_handler(name: str) -> RoofHandler:
    try:
        return ROOF_REGISTRY[name]
    except KeyError:
        raise ValueError(f"unregistered roof type {name!r}") from None


def motif_handler(name: str) -> MotifHandler:
    try:
        return MOTIF_REGISTRY[name]
    except KeyError:
        raise ValueError(f"unregistered motif {name!r}") from None


def place_motif(name: str, grid: BlockGrid, style: Style, rng: random.Random,
                node: Node, related: Optional[Node] = None) -> bool:
    return motif_handler(name)(grid, style, rng, node, related)


def validate_style_vocabulary(style: Style) -> None:
    unknown_roofs = sorted(set(style.allowed_roof_types) - set(ROOF_REGISTRY))
    unknown_motifs = sorted(set(style.allowed_motifs) - set(MOTIF_REGISTRY))
    errors = []
    if unknown_roofs:
        errors.append(f"allowed_roof_types={unknown_roofs}")
    if unknown_motifs:
        errors.append(f"allowed_motifs={unknown_motifs}")
    if errors:
        raise ValueError(
            f"style {style.style_id!r} references unregistered form names: "
            + "; ".join(errors))


def _block_id(state: str) -> str:
    return state.split("[", 1)[0]


def _species(state: str) -> str:
    """'minecraft:oak_log' -> 'oak'."""
    name = state.split(":", 1)[1].split("[", 1)[0]
    for suffix in ("_log", "_planks", "_wood"):
        name = name.replace(suffix, "")
    return name.replace("stripped_", "")


def stair_state(style: Style, facing: str, half: str = "bottom") -> str:
    base = style.slot_entry("ROOF_DARK", "_stairs")
    return orient_block("vanilla_stairs", _block_id(base), "stair",
                        facing=facing, half=half)


def slab_state(style: Style, kind: str = "bottom", slot: str = "ROOF_DARK") -> str:
    base = style.slot_entry(slot, "_slab")
    return orient_block("vanilla_slab", _block_id(base), "slab", kind=kind)


def awning_state(style: Style, facing: str, vertical: str = "bottom",
                 slot: str = "ROOF_TILE") -> Optional[str]:
    base = style.optional_slot_entry(slot, "awning")
    if not base:
        return None
    return orient_block("supplementaries:awning", _block_id(base), "eave",
                        facing=facing, vertical=vertical, slanted=True)


def canopy_roof_state(style: Style, facing: str,
                      allow_attached: bool = True) -> Tuple[str, str]:
    awning = awning_state(style, facing) if allow_attached else None
    if awning:
        return awning, "ROOF_TILE"
    base = style.optional_slot_entry("ROOF_TILE", "_stairs")
    if base:
        return (orient_block("vanilla_stairs", _block_id(base), "canopy",
                             facing=facing), "ROOF_TILE")
    base = style.optional_slot_entry("ROOF_TILE", "_slab")
    if base:
        return (orient_block("vanilla_slab", _block_id(base), "canopy",
                             kind="bottom"), "ROOF_TILE")
    return stair_state(style, facing), "ROOF_DARK"


def roof_stair_state(style: Style, facing: str,
                     half: str = "bottom") -> Tuple[str, str]:
    base = style.optional_slot_entry("ROOF_TILE", "_stairs")
    if base:
        return (orient_block("vanilla_stairs", _block_id(base), "roof_tile",
                             facing=facing, half=half), "ROOF_TILE")
    return stair_state(style, facing, half), "ROOF_DARK"


def roof_slab_state(style: Style, kind: str = "bottom") -> Tuple[str, str]:
    base = style.optional_slot_entry("ROOF_TILE", "_slab")
    if base:
        return (orient_block("vanilla_slab", _block_id(base), "roof_tile",
                             kind=kind), "ROOF_TILE")
    return slab_state(style, kind), "ROOF_DARK"


def _column_state(style: Style) -> Tuple[Optional[str], Optional[str]]:
    if not style.has_slot("COLUMN"):
        return None, None
    state = style.primary("COLUMN")
    block = _block_id(state)
    if "[" not in state and (block.endswith("_log") or block.endswith("_wood")
                             or block.endswith("_pillar")):
        state = f"{block}[axis=y]"
    return state, "COLUMN"


def _balustrade_state(style: Style) -> Tuple[Optional[str], Optional[str]]:
    if not style.has_slot("BALUSTRADE"):
        return None, None
    return style.primary("BALUSTRADE"), "BALUSTRADE"


def _platform_state(style: Style) -> Tuple[str, str]:
    if style.has_slot("PLATFORM_STONE"):
        return style.primary("PLATFORM_STONE"), "PLATFORM_STONE"
    return style.primary("BASE_STONE"), "BASE_STONE"


def _platform_stair_state(style: Style, facing: str) -> Tuple[str, str]:
    base = style.optional_slot_entry("PLATFORM_STONE", "_stairs")
    if base:
        return (orient_block("vanilla_stairs", _block_id(base), "platform",
                             facing=facing), "PLATFORM_STONE")
    base = style.material_slots["PLATFORM_STONE"][-1] if style.has_slot("PLATFORM_STONE") else None
    derived = {
        "minecraft:stone_bricks": "minecraft:stone_brick_stairs",
        "minecraft:polished_andesite": "minecraft:polished_andesite_stairs",
        "minecraft:quartz_block": "minecraft:quartz_stairs",
    }
    if base:
        block = _block_id(base)
        block = derived.get(block)
        if block:
            return (orient_block("vanilla_stairs", block, "platform",
                                 facing=facing), "PLATFORM_STONE")
    base = style.optional_slot_entry("BASE_STONE", "_stairs")
    if base:
        return (orient_block("vanilla_stairs", _block_id(base), "platform",
                             facing=facing), "BASE_STONE")
    return stair_state(style, facing), "ROOF_DARK"


def _ornament_state(style: Style) -> Tuple[Optional[str], Optional[str]]:
    if not style.has_slot("RIDGE_ORNAMENT"):
        return None, None
    state = style.primary("RIDGE_ORNAMENT")
    block = _block_id(state)
    if "[" not in state and block.endswith("lantern"):
        state = f"{block}[hanging=false,waterlogged=false]"
    return state, "RIDGE_ORNAMENT"


def trapdoor_state(style: Style, facing: str, rng: random.Random,
                   half: str = "bottom") -> str:
    options = [s for s in style.material_slots["DETAIL_WOOD"] if "trapdoor" in s]
    base = rng.choice(options)
    return f"{base}[facing={facing},half={half},open=true,powered=false,waterlogged=false]"


def fence_state(style: Style, connections: Iterable[str] = ()) -> str:
    base = style.slot_entry("DETAIL_WOOD", "oak_fence")
    conn = set(connections)
    props = ",".join(f"{d}={'true' if d in conn else 'false'}"
                     for d in ("east", "north", "south", "west"))
    return f"{base}[{props},waterlogged=false]"


def log_state(style: Style, axis: str, rng: Optional[random.Random] = None,
              stripped_chance: float = 0.0) -> str:
    base = style.primary("FRAME_WOOD")
    if rng is not None and rng.random() < stripped_chance and style.alternates("FRAME_WOOD"):
        base = style.alternates("FRAME_WOOD")[0]
    return f"{base}[axis={axis}]"


def lantern_state(style: Style, hanging: bool) -> str:
    base = style.slot_entry("LIGHTING", "lantern")
    return f"{base}[hanging={'true' if hanging else 'false'},waterlogged=false]"


def door_states(style: Style, facing: str, hinge: str) -> Tuple[str, str]:
    species = _species(style.primary("FRAME_WOOD"))
    base = f"minecraft:{species}_door"
    common = f"facing={facing},hinge={hinge},open=false,powered=false"
    return (f"{base}[{common},half=lower]", f"{base}[{common},half=upper]")


def wall_info(vol: Node, wall: str):
    """Returns (along_axis, fixed_coord, span(lo,hi), outward_name)."""
    if wall == "front":
        return "x", vol.z0, (vol.x0, vol.x1), "front"
    if wall == "back":
        return "x", vol.z1, (vol.x0, vol.x1), "back"
    if wall == "west":
        return "z", vol.x0, (vol.z0, vol.z1), "west"
    if wall == "east":
        return "z", vol.x1, (vol.z0, vol.z1), "east"
    raise ValueError(wall)


def wall_pos(vol: Node, wall: str, along: int, y: int, depth_offset: int = 0) -> Pos:
    """Cell on (or outside, depth_offset>0) a wall plane."""
    axis, fixed, _, _ = wall_info(vol, wall)
    ox, oz = WALL_OUTWARD[wall]
    if axis == "x":
        return (along, y, fixed + oz * depth_offset)
    return (fixed + ox * depth_offset, y, along)


# ---------------------------------------------------------------------------
# primitives
# ---------------------------------------------------------------------------

def line(grid: BlockGrid, a: Pos, b: Pos, state: str, tags, priority: int,
         slot: Optional[str] = None) -> None:
    steps = max(abs(b[i] - a[i]) for i in range(3))
    if steps == 0:
        grid.set(a, state, tags, priority, slot)
        return
    for i in range(steps + 1):
        pos = tuple(a[k] + (b[k] - a[k]) * i // steps for k in range(3))
        grid.set(pos, state, tags, priority, slot)


def plane(grid: BlockGrid, a: Pos, b: Pos, state: str, tags, priority: int,
          slot: Optional[str] = None) -> None:
    for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                grid.set((x, y, z), state, tags, priority, slot)


def hollow_box(grid: BlockGrid, style: Style, vol: Node) -> None:
    """Foundation + plain wall shell + interior floor for a volume."""
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    base = style.primary("BASE_STONE")
    wall = style.primary("WALL_MAIN")
    floor = style.primary("WALL_MAIN")
    # foundation
    plane(grid, (vol.x0, 0, vol.z0), (vol.x1, fh - 1, vol.z1),
          base, ["FOUNDATION", "STRUCTURE"], PRIORITY["FOUNDATION"], "BASE_STONE")
    # walls
    for w in ("front", "back", "west", "east"):
        axis, fixed, (a0, a1), _ = wall_info(vol, w)
        for along in range(a0, a1 + 1):
            for y in range(fh, wall_top + 1):
                grid.set(wall_pos(vol, w, along, y), wall,
                         ["STRUCTURE"], PRIORITY["STRUCTURE"], "WALL_MAIN")
    # interior floor on top of the foundation
    plane(grid, (vol.x0 + 1, fh - 1, vol.z0 + 1), (vol.x1 - 1, fh - 1, vol.z1 - 1),
          floor, ["STRUCTURE", "INTERIOR"], PRIORITY["STRUCTURE"], "WALL_MAIN")


def clear_inside(grid: BlockGrid, vol: Node) -> None:
    """Carve interior air; never clears INTERIOR or PROTECTED cells."""
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    for x in range(vol.x0 + 1, vol.x1):
        for z in range(vol.z0 + 1, vol.z1):
            for y in range(fh, wall_top + 1):
                cell = grid.get((x, y, z))
                if cell is not None and ({"INTERIOR", "PROTECTED"} & cell.tags):
                    continue
                grid.set((x, y, z), AIR, ["AIR_CARVE", "INTERIOR"],
                         PRIORITY["AIR_CARVE"])


# ---------------------------------------------------------------------------
# facade construction
# ---------------------------------------------------------------------------

def wall_frame(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
               wall: str, post_positions: List[int], wall_type: str) -> None:
    """Rebuild one wall with stone band / planks / timber posts / top beam."""
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    axis, fixed, (a0, a1), _ = wall_info(vol, wall)
    # every wall type keeps a stone plinth so lower/upper walls stay layered
    stone_rows = {"stone_lower_wall": 2, "mixed_stone_wood_wall": 1,
                  "timber_frame_wall": 1,
                  "white_plaster_timber_wall": 1}[wall_type]
    stone_rows = min(stone_rows, vol.meta["wall_h"] - 2)
    if vol.meta["wall_h"] >= 3:
        # every wall tall enough to carry a plinth keeps at least one stone row
        stone_rows = max(1, stone_rows)
    stone = style.primary("BASE_STONE")
    planks = style.primary("WALL_MAIN")
    tags = ["FACADE", "STRUCTURE"]
    p = PRIORITY["FACADE"]
    for along in range(a0, a1 + 1):
        for y in range(fh, wall_top + 1):
            if y < fh + stone_rows:
                grid.set(wall_pos(vol, wall, along, y), stone, tags, p, "BASE_STONE")
            else:
                grid.set(wall_pos(vol, wall, along, y), planks, tags, p, "WALL_MAIN")
    # top beam along the wall
    beam = log_state(style, "x" if axis == "x" else "z", rng, 0.3)
    for along in range(a0, a1 + 1):
        grid.set(wall_pos(vol, wall, along, wall_top), beam, tags, p, "FRAME_WOOD")
    # timber frame walls get an extra mid-height beam (windows cut through it)
    if wall_type in ("timber_frame_wall", "white_plaster_timber_wall") and vol.meta["wall_h"] >= 4:
        for along in range(a0, a1 + 1):
            grid.set(wall_pos(vol, wall, along, fh + 2), beam, tags, p, "FRAME_WOOD")
    # vertical posts: corners + bay boundaries
    for along in sorted(set([a0, a1] + post_positions)):
        for y in range(fh, wall_top + 1):
            grid.set(wall_pos(vol, wall, along, y), log_state(style, "y"),
                     tags, p, "FRAME_WOOD")


def doorway(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
            wall: str, along: int) -> dict:
    """Door + frame + hood + entry step; PROTECTED with clear approach."""
    fh = vol.meta["foundation_h"]
    inward = INWARD_FACING[wall]
    lower, upper = door_states(style, inward, rng.choice(["left", "right"]))
    p = PRIORITY["OPENING"]
    tags = ["OPENING", "FACADE", "PROTECTED"]
    grid.set(wall_pos(vol, wall, along, fh), lower, tags, p)
    grid.set(wall_pos(vol, wall, along, fh + 1), upper, tags, p)
    # frame posts + lintel
    for side in (-1, 1):
        for y in range(fh, fh + 2):
            grid.set(wall_pos(vol, wall, along + side, y), log_state(style, "y"),
                     ["FACADE", "STRUCTURE"], p, "FRAME_WOOD")
    lintel_axis = "x" if wall in ("front", "back") else "z"
    if fh + 2 <= fh + vol.meta["wall_h"] - 1:
        line(grid,
             wall_pos(vol, wall, along - 1, fh + 2),
             wall_pos(vol, wall, along + 1, fh + 2),
             log_state(style, lintel_axis), ["FACADE", "STRUCTURE"], p, "FRAME_WOOD")
    # hood stair outside above the door
    grid.set(wall_pos(vol, wall, along, fh + 2, depth_offset=1),
             stair_state(style, inward), ["DETAIL", "PROTECTED"], p)
    # entry step + protected breathing room in front of the door
    step_pos = wall_pos(vol, wall, along, fh - 1, depth_offset=1)
    grid.set(step_pos, stair_state(style, inward),
             ["DETAIL", "PROTECTED"], p, force=True)
    for side in (-1, 1):
        side_pos = wall_pos(vol, wall, along + side, fh - 1, depth_offset=1)
        grid.set(side_pos, AIR, ["AIR_CARVE", "PROTECTED"], p, force=True)
        lower_pos = (side_pos[0], fh - 2, side_pos[2])
        if grid.is_empty(lower_pos):
            grid.set(lower_pos, rng.choice(style.material_slots["GROUND_PATH"]),
                     ["DETAIL", "GROUND", "PROTECTED"], p, "GROUND_PATH",
                     force=True)
    for y in (fh, fh + 1):
        grid.set(wall_pos(vol, wall, along, y, depth_offset=1), AIR,
                 ["AIR_CARVE", "PROTECTED"], p)
    return {"wall": wall, "along": along, "y": fh,
            "front": wall_pos(vol, wall, along, fh, depth_offset=1)}


def window_kit(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
               wall: str, along: int, width: int = 1, opening_style: str =
               "window_with_trapdoor_frame", glass: str = "minecraft:glass",
               y_base: Optional[int] = None) -> int:
    """Window with frame/sill; returns number of glass cells placed."""
    fh = y_base if y_base is not None else vol.meta["foundation_h"]
    wall_top = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
    outward = OUTWARD_FACING[wall]
    p = PRIORITY["OPENING"]
    placed = 0
    if opening_style == "small_high_window":
        ys = [min(fh + 2, wall_top - 1)]
        width = 1
    else:
        ys = [fh + 1, fh + 2] if vol.meta["wall_h"] >= 4 else [fh + 1]
    cols = [along] if width == 1 else [along, along + 1]
    for cx in cols:
        for y in ys:
            if y >= wall_top:  # keep the top beam intact
                continue
            if grid.set(wall_pos(vol, wall, cx, y), glass,
                        ["OPENING", "FACADE", "PROTECTED"], p):
                placed += 1
    if not placed:
        return 0
    # sill below the window, outside
    if opening_style == "window_with_slab_sill":
        sill = slab_state(style, "top", "DETAIL_WOOD")
    else:
        sill = trapdoor_state(style, outward, rng)
    for cx in cols:
        pos = wall_pos(vol, wall, cx, ys[0] - 1, depth_offset=1)
        if grid.is_empty(pos):
            grid.set(pos, sill, ["DETAIL"], PRIORITY["DETAIL"], "DETAIL_WOOD")
    # shutters at the sides for the trapdoor frame style
    if opening_style == "window_with_trapdoor_frame":
        shutter = trapdoor_state(style, outward, rng)
        for cx, side in ((cols[0] - 1, -1), (cols[-1] + 1, 1)):
            pos = wall_pos(vol, wall, cx, ys[-1], depth_offset=1)
            if grid.is_empty(pos):
                grid.set(pos, shutter, ["DETAIL"], PRIORITY["DETAIL"], "DETAIL_WOOD")
    return placed


def storefront(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
               wall: str, door_along: int, meta: dict) -> None:
    """Wide ground-floor shopfront using glass, timber trim, and a wood awning."""
    fh = vol.meta["foundation_h"]
    story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
    wall_top = fh + story_wall_h - 1
    width = meta.get("width", 5)
    a0, a1 = wall_info(vol, wall)[2]
    lo = max(a0 + 2, door_along - width // 2)
    hi = min(a1 - 2, lo + width - 1)
    lo = max(a0 + 2, hi - width + 1)
    p = PRIORITY["OPENING"]
    glass = "minecraft:glass"
    for along in range(lo, hi + 1):
        if abs(along - door_along) <= 1:
            continue
        for y in range(fh + 1, min(wall_top, fh + 2) + 1):
            grid.set(wall_pos(vol, wall, along, y), glass,
                     ["OPENING", "FACADE", "PROTECTED"], p)
    beam_axis = "x" if wall in ("front", "back") else "z"
    for along in range(lo - 1, hi + 2):
        if a0 <= along <= a1 and wall_top >= fh + 3:
            grid.set(wall_pos(vol, wall, along, fh + 3),
                     log_state(style, beam_axis, rng, 0.2),
                     ["FACADE", "DETAIL"], PRIORITY["DETAIL"], "FRAME_WOOD")
        awning_y = min(fh + 3, wall_top)
        grid.set(wall_pos(vol, wall, along, awning_y, depth_offset=1),
                 slab_state(style, "bottom", "DETAIL_WOOD"),
                 ["DETAIL", "ROOF"], PRIORITY["DETAIL"], "DETAIL_WOOD")
    if meta.get("signage") == "post":
        for along in (lo - 1, hi + 1):
            if a0 <= along <= a1:
                grid.set(wall_pos(vol, wall, along, fh + 2, depth_offset=1),
                         fence_state(style), ["DETAIL"], PRIORITY["DETAIL"],
                         "DETAIL_WOOD")


def floor_slab(grid: BlockGrid, style: Style, vol: Node, y: int,
               opening: Optional[dict]) -> None:
    floor = style.primary("WALL_MAIN")
    for x in range(vol.x0 + 1, vol.x1):
        for z in range(vol.z0 + 1, vol.z1):
            if opening and opening["x0"] <= x <= opening["x1"] and opening["z0"] <= z <= opening["z1"]:
                continue
            grid.set((x, y, z), floor, ["STRUCTURE", "INTERIOR"],
                     PRIORITY["STRUCTURE"], "WALL_MAIN")


def stairwell(grid: BlockGrid, style: Style, vol: Node, opening: dict) -> None:
    stories = vol.meta.get("stories", 1)
    if stories <= 1:
        return
    fh = vol.meta["foundation_h"]
    story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
    stair_x = opening["x0"]
    z_values = list(range(opening["z0"], opening["z1"] + 1))
    if opening.get("direction") == "south":
        facing = "south"
    else:
        z_values = list(reversed(z_values))
        facing = "north"
    for story in range(stories - 1):
        base_y = fh + story * story_wall_h
        boundary_y = fh + (story + 1) * story_wall_h
        for x in range(opening["x0"], opening["x1"] + 1):
            for z in range(opening["z0"], opening["z1"] + 1):
                for y in (boundary_y, boundary_y + 1):
                    grid.set((x, y, z), AIR, ["AIR_CARVE", "PROTECTED"],
                             PRIORITY["OPENING"], force=True)
        landing_z = opening.get("landing_z", opening["z1"] + 1)
        for x in range(opening["x0"], opening["x1"] + 1):
            grid.set((x, boundary_y + 1, landing_z), AIR,
                     ["AIR_CARVE", "PROTECTED"], PRIORITY["OPENING"],
                     force=True)
        for step, z in enumerate(z_values[:story_wall_h]):
            grid.set((stair_x, base_y + step, z),
                     stair_state(style, facing), ["STRUCTURE", "INTERIOR"],
                     PRIORITY["INTERIOR"], "ROOF_DARK")


# ---------------------------------------------------------------------------
# roofs
# ---------------------------------------------------------------------------

def gable_infill(style: Style) -> Tuple[str, str]:
    """Resolve the gable-triangle infill material and the slot it belongs to.

    Styles that opt into a timber-infill look declare a dedicated GABLE_INFILL
    slot; otherwise the gable is filled with the volume's primary WALL_MAIN so
    stone-walled styles get a solid wall-coloured gable (no dark roof planks).
    """
    if style.has_slot("GABLE_INFILL"):
        return style.primary("GABLE_INFILL"), "GABLE_INFILL"
    return style.primary("WALL_MAIN"), "WALL_MAIN"


def gable_roof(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
               ridge_axis: str, overhang: int,
               attached_side: Optional[str] = None) -> dict:
    """Stair gable roof with sealed gable triangles and protected ridge.

    Returns {"gable_cells": [...], "roof_cells": [...]} for quality checks.
    """
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    p = PRIORITY["ROOF"]
    gable_cells: List[Pos] = []
    roof_cells: List[Pos] = []
    gable_state, gable_slot = gable_infill(style)
    planks = style.slot_entry("ROOF_DARK", "_planks")

    if ridge_axis == "x":
        lo_edge, hi_edge = vol.z0, vol.z1
        span_lo, span_hi = vol.x0 - overhang, vol.x1 + overhang
        lo_face, hi_face = "south", "north"     # stair facing = toward ridge
        gable_walls = ("west", "east")
    else:
        lo_edge, hi_edge = vol.x0, vol.x1
        span_lo, span_hi = vol.z0 - overhang, vol.z1 + overhang
        lo_face, hi_face = "east", "west"
        gable_walls = ("front", "back")

    # symmetric overhang except on an attached side
    ov_lo = overhang
    ov_hi = overhang
    if attached_side == ("north" if ridge_axis == "x" else "west"):
        ov_lo = 0
    if attached_side == ("south" if ridge_axis == "x" else "east"):
        ov_hi = 0
    lo = lo_edge - ov_lo
    hi = hi_edge + ov_hi

    def put_row(coord: int, y: int, state: str, protect: bool = False) -> None:
        tags = ["ROOF"] + (["PROTECTED"] if protect else [])
        for a in range(span_lo, span_hi + 1):
            pos = (a, y, coord) if ridge_axis == "x" else (coord, y, a)
            if grid.set(pos, state, tags, p, "ROOF_DARK"):
                roof_cells.append(pos)

    y = wall_top + 1
    while lo < hi:
        put_row(lo, y, stair_state(style, lo_face))
        put_row(hi, y, stair_state(style, hi_face))
        lo += 1
        hi -= 1
        y += 1
    if lo == hi:  # odd span: solid ridge line, protected
        put_row(lo, y, planks, protect=True)
        ridge_y = y
    else:         # even span: the two top stair rows meet back to back
        ridge_y = y - 1

    # Gable end-wall infill, flush with the end walls. Each end-wall column is
    # sealed from wall_top+1 up to the roof skin directly above it (the slope
    # cell or ridge), so the fill tracks the true roofline for every column:
    # no apex gap at the centre, no edge gap where an overhung slope arrives
    # late, and no see-through air cells along the roofline.
    for wallname in gable_walls:
        _, fixed, (a0, a1), _ = wall_info(vol, wallname)
        for c in range(a0, a1 + 1):
            roof_y = None
            for y in range(wall_top + 1, ridge_y + 1):
                pos = (fixed, y, c) if ridge_axis == "x" else (c, y, fixed)
                rc = grid.get(pos)
                if rc and "ROOF" in rc.tags and "FACADE" not in rc.tags:
                    roof_y = y
                    break
            if roof_y is None:
                roof_y = ridge_y
            for y in range(wall_top + 1, roof_y):
                pos = (fixed, y, c) if ridge_axis == "x" else (c, y, fixed)
                if grid.is_empty(pos):
                    if grid.set(pos, gable_state, ["FACADE", "ROOF"], p,
                                gable_slot):
                        gable_cells.append(pos)

    # Back any gable-plane cell that only carries a roof stair with a full
    # gable block one step inboard, so the end wall has no see-through
    # half-block gaps along the roofline. Record both cells for quality checks.
    for wallname in gable_walls:
        _, fixed, (a0, a1), outward = wall_info(vol, wallname)
        ox, oz = WALL_OUTWARD[wallname]
        inboard = (-ox, -oz)  # one step toward the volume interior
        for c in range(a0, a1 + 1):
            for yy in range(wall_top + 1, ridge_y + 1):
                pos = (fixed, yy, c) if ridge_axis == "x" else (c, yy, fixed)
                sc = grid.get(pos)
                if sc and "ROOF" in sc.tags and "_stairs" in sc.state:
                    back = (pos[0] + inboard[0], yy, pos[2] + inboard[1])
                    if grid.is_empty(back):
                        if grid.set(back, gable_state, ["FACADE", "ROOF"], p,
                                    gable_slot):
                            gable_cells.append(back)
                    gable_cells.append(pos)
    return {"gable_cells": gable_cells, "roof_cells": roof_cells, "peak_y": ridge_y}


def cross_gable_roof(grid: BlockGrid, style: Style, rng: random.Random,
                     main: Node, wing: Node) -> dict:
    """Main gable + perpendicular wing gable (wing written after main)."""
    info_main = gable_roof(grid, style, rng, main,
                           main.meta["roof"].get("ridge_axis", "x"),
                           main.meta["roof"].get("overhang", 1))
    info_wing = gable_roof(grid, style, rng, wing,
                           wing.meta["roof"].get("ridge_axis", "z"),
                           wing.meta["roof"].get("overhang", 1),
                           attached_side=wing.meta["roof"].get("attached_side"))
    return {
        "gable_cells": info_main["gable_cells"] + info_wing["gable_cells"],
        "roof_cells": info_main["roof_cells"] + info_wing["roof_cells"],
        "peak_y": max(info_main["peak_y"], info_wing["peak_y"]),
    }


def lean_to_roof(grid: BlockGrid, style: Style, vol: Node, low_side: str,
                 high_y: int) -> dict:
    """Half-step slab roof sloping down toward low_side, starting at high_y."""
    p = PRIORITY["ROOF"]
    roof_cells: List[Pos] = []
    axis, fixed, (a0, a1), _ = wall_info(vol, low_side)
    # rows run from the attached (high) side toward low_side
    if low_side in ("front", "back"):
        depth = vol.size[2]
        coords = range(vol.z1, vol.z0 - 1, -1) if low_side == "front" \
            else range(vol.z0, vol.z1 + 1)
        span = range(vol.x0 - 1, vol.x1 + 2)
    else:
        depth = vol.size[0]
        coords = range(vol.x1, vol.x0 - 1, -1) if low_side == "west" \
            else range(vol.x0, vol.x1 + 1)
        span = range(vol.z0 - 1, vol.z1 + 2)
    for i, c in enumerate(coords):
        half = 2 * high_y + 1 - i           # height in half blocks
        y, top_half = divmod(half - 1, 2)   # top_half: upper or lower slab
        state = slab_state(style, "top" if top_half else "bottom")
        for a in span:
            pos = (a, y, c) if low_side in ("front", "back") else (c, y, a)
            if grid.set(pos, state, ["ROOF"], p, "ROOF_DARK"):
                roof_cells.append(pos)
    low_y = y
    return {"roof_cells": roof_cells, "gable_cells": [], "peak_y": high_y,
            "low_y": low_y}


def _roof_bounds(vol: Node, overhang: int) -> Tuple[int, int, int, int]:
    inset = max(0, int(vol.meta.get("roof", {}).get("footprint_inset", 0)))
    x0 = min(vol.x0 + inset, vol.x1)
    x1 = max(x0, vol.x1 - inset)
    z0 = min(vol.z0 + inset, vol.z1)
    z1 = max(z0, vol.z1 - inset)
    return x0 - overhang, x1 + overhang, z0 - overhang, z1 + overhang


def _place_ridge_ornaments(grid: BlockGrid, style: Style,
                           ridge_cells: List[Pos]) -> List[Pos]:
    state, slot = _ornament_state(style)
    if not state or not ridge_cells:
        return []
    p = PRIORITY["DETAIL"]
    if len(ridge_cells) == 1:
        targets = [(ridge_cells[0][0], ridge_cells[0][1] + 1, ridge_cells[0][2])]
    else:
        ordered = sorted(ridge_cells)
        targets = [
            (ordered[0][0], ordered[0][1] + 1, ordered[0][2]),
            (ordered[-1][0], ordered[-1][1] + 1, ordered[-1][2]),
        ]
        center = ordered[len(ordered) // 2]
        targets.append((center[0], center[1] + 1, center[2]))
    placed: List[Pos] = []
    for pos in targets:
        if grid.set(pos, state, ["DETAIL", "ROOF", "PROTECTED"], p, slot):
            placed.append(pos)
    return placed


def _eave_corner_lift(along: int, span_lo: int, span_hi: int,
                      max_lift: int) -> int:
    """Eave height lift at one position along the ridge span.

    Returns 0 at the span centre and grows to ``max_lift`` at both gable ends
    along a concave (swooping) curve, so the eave line droops in the middle and
    lifts toward the corners (飞檐翘角).
    """
    span = span_hi - span_lo
    if span <= 0 or max_lift <= 0:
        return 0
    t = (along - span_lo) / span
    d = min(t, 1.0 - t) * 2.0      # 1 at centre, 0 at both ends
    curve = 1.0 - d * d            # 0 at centre, 1 at both ends
    return int(round(max_lift * curve))


def _place_eave_corners(grid: BlockGrid, style: Style, ridge_axis: str,
                        bounds: Tuple[int, int, int, int], span_lo: int,
                        span_hi: int, base: int, max_lift: int,
                        min_run: int) -> List[Pos]:
    """Crisp upturned finial plus an outward wing at each eave corner."""
    x0, x1, z0, z1 = bounds
    p = PRIORITY["ROOF"]
    placed: List[Pos] = []
    lift_cap = max(0, min_run - 1)
    if ridge_axis == "x":
        perp_edges = [(z0, "south"), (z1, "north")]
    else:
        perp_edges = [(x0, "east"), (x1, "west")]
    slab, slab_slot = roof_slab_state(style, "top")
    for sx in (-1, 1):
        along_edge = span_lo if sx < 0 else span_hi
        lift = min(_eave_corner_lift(along_edge, span_lo, span_hi, max_lift),
                   lift_cap)
        eave_y = base + lift
        for perp_edge, facing in perp_edges:
            stair, stair_slot = roof_stair_state(style, facing, half="top")
            cap = ((along_edge, eave_y + 1, perp_edge) if ridge_axis == "x"
                   else (perp_edge, eave_y + 1, along_edge))
            if grid.set(cap, stair, ["ROOF", "DETAIL"], p, stair_slot):
                placed.append(cap)
            sz = -1 if perp_edge in (z0, x0) else 1
            wing_perp = perp_edge + sz
            wing = ((along_edge, eave_y + 1, wing_perp) if ridge_axis == "x"
                    else (wing_perp, eave_y + 1, along_edge))
            if grid.set(wing, slab, ["ROOF", "DETAIL"], p, slab_slot):
                placed.append(wing)
    return placed


def _add_eave_brackets(grid: BlockGrid, style: Style, vol: Node,
                       ridge_axis: str, wall_top: int) -> List[Pos]:
    """Dougong / 额枋 bracket course under the eave, slot-resolved (DETAIL_WOOD).

    Skipped entirely when the style has no `_fence` in `DETAIL_WOOD`, so mortal
    styles are unaffected.
    """
    fence = style.optional_slot_entry("DETAIL_WOOD", "_fence")
    if not fence:
        return []
    p = PRIORITY["DETAIL"]
    placed: List[Pos] = []
    if ridge_axis == "x":
        perps = [vol.z0 - 1, vol.z1 + 1]
        span = range(vol.x0 + 1, vol.x1)
    else:
        perps = [vol.x0 - 1, vol.x1 + 1]
        span = range(vol.z0 + 1, vol.z1)
    for perp in perps:
        for i, along in enumerate(span):
            if i % 2:
                continue
            pos = ((along, wall_top, perp) if ridge_axis == "x"
                   else (perp, wall_top, along))
            if grid.set(pos, fence, ["DETAIL", "ROOF"], p, "DETAIL_WOOD"):
                placed.append(pos)
    return placed


def sweeping_eave_roof(grid: BlockGrid, style: Style, rng: random.Random,
                       vol: Node, ridge_axis: str, overhang: int,
                       attached_side: Optional[str] = None) -> dict:
    """Ridged roof with a curved 举折 cross-section and upturned corner sweep.

    Two axes of curvature, both built from stair geometry alone:
      * cross-section (perpendicular to the ridge): each eave side rises through
        a flat eave run (举折) before climbing one block per row toward the ridge;
      * along the ridge span: the eave line lifts toward the gable ends
        (`_eave_corner_lift`), so the eave droops in the middle and swoops up at
        the corners (飞檐翘角). The ridge itself stays level.
    """
    del rng  # deterministic geometry
    overhang = max(2, int(overhang))
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    base = wall_top + 1
    p = PRIORITY["ROOF"]
    gable_cells: List[Pos] = []
    roof_cells: List[Pos] = []
    ridge_cells: List[Pos] = []
    gable_state = style.alternates("WALL_MAIN")[0] if style.alternates("WALL_MAIN") \
        else style.primary("WALL_MAIN")
    planks = style.slot_entry("ROOF_DARK", "_planks")
    x0, x1, z0, z1 = _roof_bounds(vol, overhang)

    if ridge_axis == "x":
        lo_edge, hi_edge = z0, z1
        span_lo, span_hi = x0, x1
        lo_face, hi_face = "south", "north"
        gable_walls = ("west", "east")
    else:
        lo_edge, hi_edge = x0, x1
        span_lo, span_hi = z0, z1
        lo_face, hi_face = "east", "west"
        gable_walls = ("front", "back")

    lo, hi = lo_edge, hi_edge
    if attached_side == ("north" if ridge_axis == "x" else "west"):
        lo = lo_edge + overhang
    if attached_side == ("south" if ridge_axis == "x" else "east"):
        hi = hi_edge - overhang

    ridge_coord = (lo + hi) // 2
    run_lo = ridge_coord - lo
    run_hi = hi - ridge_coord
    min_run = min(run_lo, run_hi)
    ridge_y = base + max(0, min_run)
    max_lift = min(3, max(2, (span_hi - span_lo) // 6))
    lift_cap = max(0, min_run - 1)

    lo_stair, lo_slot = roof_stair_state(style, lo_face)
    hi_stair, hi_slot = roof_stair_state(style, hi_face)

    def put_cell(along: int, perp: int, y: int, state: str, slot: str,
                 protect: bool = False) -> bool:
        pos = (along, y, perp) if ridge_axis == "x" else (perp, y, along)
        tags = ["ROOF"] + (["PROTECTED"] if protect else [])
        if grid.set(pos, state, tags, p, slot):
            roof_cells.append(pos)
            if protect:
                ridge_cells.append(pos)
            return True
        return False

    # Curved eave surface, built per span column so the eave can lift at corners.
    for along in range(span_lo, span_hi + 1):
        lift = min(_eave_corner_lift(along, span_lo, span_hi, max_lift), lift_cap)
        eave_y = base + lift
        rise = max(0, min_run - lift)          # climbing rows on the short side
        # LO side: perpendicular coord walks from the eave (lo) toward the ridge.
        flat_lo = run_lo - rise
        for k in range(run_lo):
            perp = lo + k
            y = eave_y + max(0, k - flat_lo)
            put_cell(along, perp, y, lo_stair, lo_slot)
        # HI side: perpendicular coord walks from the eave (hi) toward the ridge.
        flat_hi = run_hi - rise
        for k in range(run_hi):
            perp = hi - k
            y = eave_y + max(0, k - flat_hi)
            put_cell(along, perp, y, hi_stair, hi_slot)

    # Level ridge cap, shared by both eaves.
    for along in range(span_lo, span_hi + 1):
        put_cell(along, ridge_coord, ridge_y, planks, "ROOF_DARK", protect=True)

    # Gable-end triangle infill on the short walls, driven per-column up to
    # the roof skin directly above each column (the same column-scan fix as
    # gable_roof) so the edge gaps where a rising slope arrives late and the
    # apex gap are both sealed.
    gable_wall_spans = {}
    if ridge_axis == "x":
        for wallname in gable_walls:
            _ax, _fx, (a0, a1), _ = wall_info(vol, wallname)
            gable_wall_spans[wallname] = (a0, a1)
    else:
        for wallname in gable_walls:
            _ax, _fx, (a0, a1), _ = wall_info(vol, wallname)
            gable_wall_spans[wallname] = (a0, a1)
    for wallname, (a0, a1) in gable_wall_spans.items():
        _ax, fixed, _, _ = wall_info(vol, wallname)
        for c in range(a0, a1 + 1):
            roof_y = None
            for y in range(wall_top + 1, ridge_y + 6):
                pos = (fixed, y, c) if ridge_axis == "x" else (c, y, fixed)
                rc = grid.get(pos)
                if rc and "ROOF" in rc.tags and "FACADE" not in rc.tags:
                    roof_y = y
                    break
            if roof_y is None:
                continue
            for y in range(wall_top + 1, roof_y):
                pos = (fixed, y, c) if ridge_axis == "x" else (c, y, fixed)
                if grid.is_empty(pos):
                    if grid.set(pos, gable_state, ["FACADE", "ROOF"], p, "WALL_MAIN"):
                        gable_cells.append(pos)

    # Seal the eave (side) walls where the upturned corner sweep leaves a
    # vertical gap between the wall top and the lifted eave. Each side-wall
    # column is filled from wall_top+1 up to the first roof-skin cell above
    # (the lower-eave line), so the single-tier side wall is fully enclosed
    # under the curved eave. Tiered roofs may still show a gap between the
    # lower and upper eaves on the side walls; that requires the tiered-roof
    # handler to close the upper gap explicitly.
    eave_walls = tuple(w for w in ("front", "back", "west", "east")
                       if w not in gable_walls)
    for wallname in eave_walls:
        _axisname, fixed, (a0, a1), _ = wall_info(vol, wallname)
        axis_is_x = (wallname in ("front", "back"))
        for along in range(a0, a1 + 1):
            roof_y = None
            for y in range(wall_top + 1, ridge_y + 8):
                pos = (along, y, fixed) if axis_is_x else (fixed, y, along)
                rc = grid.get(pos)
                if rc and "ROOF" in rc.tags and "FACADE" not in rc.tags:
                    roof_y = y
                    break
            if roof_y is None:
                continue
            for y in range(wall_top + 1, roof_y):
                pos = (along, y, fixed) if axis_is_x else (fixed, y, along)
                if grid.is_empty(pos):
                    if grid.set(pos, gable_state, ["FACADE", "STRUCTURE"], p,
                                "WALL_MAIN"):
                        gable_cells.append(pos)

    corners = _place_eave_corners(grid, style, ridge_axis, (x0, x1, z0, z1),
                                  span_lo, span_hi, base, max_lift, min_run)
    brackets = _add_eave_brackets(grid, style, vol, ridge_axis, wall_top)
    ornaments = _place_ridge_ornaments(grid, style, ridge_cells)
    return {
        "gable_cells": gable_cells,
        "roof_cells": roof_cells + corners + ornaments,
        "peak_y": ridge_y,
        "upturned_corners": corners,
        "ridge_ornaments": ornaments,
        "eave_brackets": brackets,
        "overhang": overhang,
    }


def _ring_roof(grid: BlockGrid, style: Style,
               bounds: Tuple[int, int, int, int], start_y: int,
               crown: bool = False) -> dict:
    x0, x1, z0, z1 = bounds
    p = PRIORITY["ROOF"]
    roof_cells: List[Pos] = []
    ridge_cells: List[Pos] = []
    y = start_y
    while x0 <= x1 and z0 <= z1:
        if x0 == x1 and z0 == z1:
            pos = (x0, y, z0)
            grid.set(pos, style.slot_entry("ROOF_DARK", "_planks"),
                     ["ROOF", "PROTECTED"], p, "ROOF_DARK")
            roof_cells.append(pos)
            ridge_cells.append(pos)
            break
        if x0 == x1:
            for z in range(z0, z1 + 1):
                pos = (x0, y, z)
                grid.set(pos, style.slot_entry("ROOF_DARK", "_planks"),
                         ["ROOF", "PROTECTED"], p, "ROOF_DARK")
                roof_cells.append(pos)
                ridge_cells.append(pos)
            break
        if z0 == z1:
            for x in range(x0, x1 + 1):
                pos = (x, y, z0)
                grid.set(pos, style.slot_entry("ROOF_DARK", "_planks"),
                         ["ROOF", "PROTECTED"], p, "ROOF_DARK")
                roof_cells.append(pos)
                ridge_cells.append(pos)
            break
        north, north_slot = roof_stair_state(style, "south")
        south, south_slot = roof_stair_state(style, "north")
        west, west_slot = roof_stair_state(style, "east")
        east, east_slot = roof_stair_state(style, "west")
        for x in range(x0, x1 + 1):
            for pos, state, slot in (
                    ((x, y, z0), north, north_slot),
                    ((x, y, z1), south, south_slot)):
                if grid.set(pos, state, ["ROOF"], p, slot):
                    roof_cells.append(pos)
        for z in range(z0 + 1, z1):
            for pos, state, slot in (
                    ((x0, y, z), west, west_slot),
                    ((x1, y, z), east, east_slot)):
                if grid.set(pos, state, ["ROOF"], p, slot):
                    roof_cells.append(pos)
        x0 += 1
        x1 -= 1
        z0 += 1
        z1 -= 1
        y += 1
    ornaments = _place_ridge_ornaments(grid, style, ridge_cells)
    if crown and ridge_cells and not ornaments:
        ornaments = _place_ridge_ornaments(grid, style, ridge_cells[:1])
    return {
        "roof_cells": roof_cells + ornaments,
        "gable_cells": [],
        "peak_y": y,
        "ridge_cells": ridge_cells,
        "ridge_ornaments": ornaments,
    }


def hip_roof(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
             overhang: int) -> dict:
    del rng
    wall_top = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
    return _ring_roof(grid, style, _roof_bounds(vol, max(1, int(overhang))),
                      wall_top + 1)


def pyramidal_roof(grid: BlockGrid, style: Style, rng: random.Random,
                   vol: Node, overhang: int) -> dict:
    del rng
    wall_top = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
    x0, x1, z0, z1 = _roof_bounds(vol, max(1, int(overhang)))
    side = min(x1 - x0 + 1, z1 - z0 + 1)
    if side % 2 == 0:
        side -= 1
    cx = (x0 + x1) // 2
    cz = (z0 + z1) // 2
    half = max(1, side // 2)
    square = (cx - half, cx + half, cz - half, cz + half)
    return _ring_roof(grid, style, square, wall_top + 1, crown=True)


def _with_roofed_ids(info: dict, *volume_ids: str) -> dict:
    info["roofed_volume_ids"] = list(volume_ids)
    return info


def _with_roof_type(info: dict, roof_type: str) -> dict:
    info["roof_type"] = roof_type
    return info


def _gable_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                        vol: Node, graph: Optional[Any] = None) -> dict:
    roof = vol.meta.get("roof", {})
    return _with_roofed_ids(
        _with_roof_type(gable_roof(
            grid, style, rng, vol,
            roof.get("ridge_axis", "x"),
            roof.get("overhang", 1),
            attached_side=roof.get("attached_side")),
            roof.get("grade", "gable_roof")),
        vol.id)


def _cross_gable_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                              vol: Node, graph: Optional[Any] = None) -> dict:
    wings = graph.by_type("side_wing") if graph is not None else []
    if not wings:
        return _gable_roof_handler(grid, style, rng, vol, graph)
    info = _with_roof_type(cross_gable_roof(grid, style, rng, vol, wings[0]),
                           "cross_gable_roof")
    return _with_roofed_ids(info, vol.id, wings[0].id)


def _lean_to_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                          vol: Node, graph: Optional[Any] = None) -> dict:
    if graph is None:
        raise ValueError(f"lean_to_roof for volume {vol.id!r} requires graph context")
    roof = vol.meta.get("roof", {})
    parent = graph.get(vol.attach_to) if vol.attach_to else graph.get("main")
    high_y = parent.meta["foundation_h"] + parent.meta["wall_h"]
    return _with_roofed_ids(
        _with_roof_type(lean_to_roof(grid, style, vol, roof["low_side"], high_y),
                        "lean_to_roof"),
        vol.id)


def _sweeping_eave_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                                vol: Node, graph: Optional[Any] = None) -> dict:
    roof = vol.meta.get("roof", {})
    info = sweeping_eave_roof(
        grid, style, rng, vol,
        roof.get("ridge_axis", "x"),
        roof.get("overhang", 2),
        attached_side=roof.get("attached_side"))
    return _with_roofed_ids(_with_roof_type(info, "sweeping_eave_roof"), vol.id)


def _hip_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                      vol: Node, graph: Optional[Any] = None) -> dict:
    roof = vol.meta.get("roof", {})
    info = hip_roof(grid, style, rng, vol, roof.get("overhang", 1))
    return _with_roofed_ids(_with_roof_type(info, "hip_roof"), vol.id)


def _pyramidal_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                            vol: Node, graph: Optional[Any] = None) -> dict:
    roof = vol.meta.get("roof", {})
    info = pyramidal_roof(grid, style, rng, vol, roof.get("overhang", 1))
    info["finial"] = bool(info.get("ridge_ornaments"))
    return _with_roofed_ids(_with_roof_type(info, "pyramidal_roof"), vol.id)


def _tiered_eave_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                              vol: Node, graph: Optional[Any] = None) -> dict:
    roof = vol.meta.get("roof", {})
    ridge_axis = roof.get("ridge_axis", "x")
    overhang = max(2, roof.get("overhang", 2))
    if vol.size[0] < 9 or vol.size[2] < 9:
        info = sweeping_eave_roof(
            grid, style, rng, vol, ridge_axis, overhang,
            attached_side=roof.get("attached_side"))
        info["roof_type"] = "tiered_eave_roof"
        info["tier_count"] = 1
        info["fallback"] = "single_eave"
        info["roofed_volume_ids"] = [vol.id]
        return info

    lower = sweeping_eave_roof(
        grid, style, rng, vol, ridge_axis, overhang,
        attached_side=roof.get("attached_side"))
    inset = 2
    upper_w = max(3, vol.size[0] - inset * 2)
    upper_d = max(3, vol.size[2] - inset * 2)
    upper_wall_top = max(lower["peak_y"] - 1,
                         vol.meta["foundation_h"] + vol.meta["wall_h"])
    upper = Node(
        id=f"{vol.id}_upper_eave",
        type="roof_tier",
        origin=(vol.x0 + inset, vol.y0, vol.z0 + inset),
        size=(upper_w, upper_wall_top + 1, upper_d),
        attach_to=vol.id,
        priority=vol.priority,
        tags=list(vol.tags),
        meta={
            "foundation_h": vol.meta["foundation_h"],
            "wall_h": upper_wall_top - vol.meta["foundation_h"] + 1,
            "roof": {
                "type": "sweeping_eave_roof",
                "ridge_axis": ridge_axis,
                "overhang": 1,
            },
        },
    )
    upper_info = sweeping_eave_roof(grid, style, rng, upper, ridge_axis, 1)

    # Enclose the eave (side) walls between the lower roof and the upper tier.
    # This vertical plane is the upper story's wall, so it is built from
    # WALL_MAIN to read as a continuation of the building's stone wall rather
    # than a flat slab of dark roof material (which otherwise looked like a
    # redundant coloured "wall" on the eave faces). Only the short fascia band
    # tucked directly under the projecting upper eave (below) stays ROOF_DARK.
    eave_walls = ("front", "back") if ridge_axis == "x" else ("west", "east")
    wall_state = style.primary("WALL_MAIN")
    soffit_state = style.slot_entry("ROOF_DARK", "_slab", style.primary("WALL_MAIN"))
    p_soffit = PRIORITY["ROOF"]
    main_fh = vol.meta["foundation_h"]
    main_wall_top = main_fh + vol.meta["wall_h"] - 1
    soffit_top = upper_wall_top + 1
    soffit_cells: List[Pos] = []
    for wallname in eave_walls:
        _ax, fixed, (a0, a1), _ = wall_info(vol, wallname)
        axis_is_x = (wallname in ("front", "back"))
        for along in range(a0, a1 + 1):
            lower_eave_y = None
            for y in range(main_wall_top + 1, soffit_top + 1):
                pos = (along, y, fixed) if axis_is_x else (fixed, y, along)
                rc = grid.get(pos)
                if rc and "ROOF" in rc.tags and "FACADE" not in rc.tags:
                    lower_eave_y = y
                    break
            if lower_eave_y is None:
                continue
            for y in range(lower_eave_y, soffit_top):
                pos = (along, y, fixed) if axis_is_x else (fixed, y, along)
                if grid.is_empty(pos):
                    if grid.set(pos, wall_state, ["ROOF"], p_soffit,
                                "WALL_MAIN", force=True):
                        soffit_cells.append(pos)

    # Also seal the upper eave overhang gap on the eave walls: the space
    # between the upper eave line and where the upper roof's slope begins
    # to project onto the eave wall. Fill a short run above the upper eave
    # so the visible side-wall air just above the upper eave is closed,
    # but stop well short of the upper peak so the eave does not appear to
    # fly up to the roof.
    overhang_top = upper_wall_top + 3
    overhang_cells: List[Pos] = []
    for wallname in eave_walls:
        _ax, fixed, (a0, a1), _ = wall_info(vol, wallname)
        axis_is_x = (wallname in ("front", "back"))
        for along in range(a0, a1 + 1):
            for y in range(soffit_top, overhang_top):
                pos = (along, y, fixed) if axis_is_x else (fixed, y, along)
                if grid.is_empty(pos):
                    if grid.set(pos, soffit_state, ["ROOF"], p_soffit,
                                "ROOF_DARK", force=True):
                        overhang_cells.append(pos)

    # Floor for the upper tier (walkable terrace on top of the lower roof),
    # spanning the upper tier's inset footprint at the lower roof's ridge
    # level, so the upper tier has a solid terrace surface.
    upper_floor_cells: List[Pos] = []
    p_floor = PRIORITY["STRUCTURE"]
    inset = 2
    floor_y = upper_wall_top
    for x in range(vol.x0 + inset, vol.x1 - inset + 1):
        for z in range(vol.z0 + inset, vol.z1 - inset + 1):
            grid.set((x, floor_y, z), style.primary("WALL_MAIN"),
                     ["STRUCTURE", "INTERIOR"], p_floor, "WALL_MAIN",
                     force=True)
            upper_floor_cells.append((x, floor_y, z))

    return _with_roofed_ids({
        "gable_cells": lower["gable_cells"] + upper_info["gable_cells"]
                       + soffit_cells + upper_floor_cells,
        "roof_cells": lower["roof_cells"] + upper_info["roof_cells"]
                      + soffit_cells,
        "peak_y": max(lower["peak_y"], upper_info["peak_y"]),
        "roof_type": "tiered_eave_roof",
        "tier_count": 2,
        "upturned_corners": lower.get("upturned_corners", []) + upper_info.get("upturned_corners", []),
        "ridge_ornaments": lower.get("ridge_ornaments", []) + upper_info.get("ridge_ornaments", []),
    }, vol.id)


def _pagoda_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                         vol: Node, graph: Optional[Any] = None) -> dict:
    """Pagoda crown: tiered flying-eave massing topped by a finial spire.

    Composed entirely from the existing ``tiered_eave_roof`` + ridge-ornament
    vocabulary (no bespoke geometry): the body reuses the two-tier sweeping
    eave, and the crown is a FRAME_WOOD post stack capped by a RIDGE_ORNAMENT
    finial above the peak. Registered as the ``pagoda`` form.
    """
    roof = vol.meta.get("roof", {})
    base = _tiered_eave_roof_handler(grid, style, rng, vol, graph)
    peak_y = int(base.get("peak_y", vol.meta["foundation_h"] + vol.meta["wall_h"]))
    cx = (vol.x0 + vol.x1) // 2
    cz = (vol.z0 + vol.z1) // 2
    p_detail = PRIORITY["DETAIL"]
    spire_height = max(2, int(roof.get("spire_height", 4)))
    post = log_state(style, "y")
    spire_cells: List[Pos] = []
    for dy in range(1, spire_height + 1):
        pos = (cx, peak_y + dy, cz)
        if grid.set(pos, post, ["DETAIL", "ROOF", "PROTECTED"], p_detail, "FRAME_WOOD"):
            spire_cells.append(pos)
    ornament_state, ornament_slot = _ornament_state(style)
    finial_cells: List[Pos] = []
    if ornament_state:
        cap = (cx, peak_y + spire_height + 1, cz)
        if grid.set(cap, ornament_state, ["DETAIL", "ROOF", "PROTECTED"],
                    p_detail, ornament_slot):
            finial_cells.append(cap)
    base["roof_type"] = "pagoda"
    base["spire_cells"] = spire_cells + finial_cells
    base["roof_cells"] = base.get("roof_cells", []) + spire_cells + finial_cells
    base["peak_y"] = peak_y + spire_height + (1 if finial_cells else 0)
    base["vertical_landmark"] = True
    return _with_roofed_ids(base, vol.id)


def _pavilion_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                           vol: Node, graph: Optional[Any] = None) -> dict:
    """Pavilion roof: a single wide sweeping eave with a balustrade-style crown.

    Composed from the existing ``sweeping_eave_roof`` vocabulary with a
    generous overhang so the roofline reads as a viewing pavilion; ridge
    ornaments cap the line. Registered as the ``pavilion`` form.
    """
    roof = vol.meta.get("roof", {})
    overhang = max(3, int(roof.get("overhang", 3)))
    info = sweeping_eave_roof(
        grid, style, rng, vol,
        roof.get("ridge_axis", "x"), overhang,
        attached_side=roof.get("attached_side"))
    info["roof_type"] = "pavilion"
    info["vertical_landmark"] = True
    return _with_roofed_ids(info, vol.id)


def _bell_drum_tower_roof_handler(grid: BlockGrid, style: Style, rng: random.Random,
                                  vol: Node, graph: Optional[Any] = None) -> dict:
    """Bell/drum tower roof: tiered flying eaves crowned by a belfry bell.

    Composed from the existing ``tiered_eave_roof`` vocabulary plus an
    INTERIOR_CIVIC bell marker placed at the crown. Registered as the
    ``bell_drum_tower`` form.
    """
    base = _tiered_eave_roof_handler(grid, style, rng, vol, graph)
    peak_y = int(base.get("peak_y", vol.meta["foundation_h"] + vol.meta["wall_h"]))
    cx = (vol.x0 + vol.x1) // 2
    cz = (vol.z0 + vol.z1) // 2
    bell = _bell_state(style, "north", "ceiling")
    bell_cells: List[Pos] = []
    for dy in (2, 3, 4, 5):
        pos = (cx, peak_y + dy, cz)
        if grid.is_empty(pos):
            if grid.set(pos, bell, ["INTERIOR", "DETAIL", "ROOF", "PROTECTED"],
                        PRIORITY["DETAIL"], "INTERIOR_CIVIC"):
                bell_cells.append(pos)
                break
    base["roof_type"] = "bell_drum_tower"
    base["belfry_bell"] = bool(bell_cells)
    base["roof_cells"] = base.get("roof_cells", []) + bell_cells
    base["peak_y"] = peak_y + (bell_cells[-1][1] - peak_y if bell_cells else 0)
    base["vertical_landmark"] = True
    return _with_roofed_ids(base, vol.id)


# ---------------------------------------------------------------------------
# Chinese vernacular (民居) roof vocabulary
#
# Four real forms, each a thin handler over one shared geometry helper
# (`_chinese_gable_planes`) plus, for 歇山, a hip-skirt helper. The handlers do
# NOT string-match among themselves: each calls the shared helper with its own
# overhang / crown parameters. These are the vernacular counterparts to the
# cultivation monumental forms (`sweeping_eave_roof` etc.): smaller overhang,
# plain rafter feet, no举折 curve, no required platform.
# ---------------------------------------------------------------------------

def _chinese_gable_planes(grid: BlockGrid, style: Style, vol: Node,
                          ridge_axis: str, gable_overhang: int,
                          eave_overhang: int, *, rounded: bool = False,
                          seal_gables: bool = True) -> dict:
    """Stair gable roof with independently controlled gable-end and eave overhang.

    硬山 passes ``gable_overhang=0`` (flush gable, modest eave); 悬山 passes a
    positive ``gable_overhang`` (roof overhangs the gable wall). ``rounded``
    crowns the ridge with top slabs instead of a peak plank (卷棚 — no ridge
    block). Returns the same {gable_cells, roof_cells, peak_y, ...} contract as
    ``gable_roof`` so the quality passes can re-verify enclosure.
    """
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    p = PRIORITY["ROOF"]
    gable_cells: List[Pos] = []
    roof_cells: List[Pos] = []
    ridge_cells: List[Pos] = []
    gable_state, gable_slot = gable_infill(style)
    planks = style.slot_entry("ROOF_DARK", "_planks")

    if ridge_axis == "x":
        lo_edge, hi_edge = vol.z0, vol.z1
        span_lo, span_hi = vol.x0 - gable_overhang, vol.x1 + gable_overhang
        lo_face, hi_face = "south", "north"
        gable_walls = ("west", "east")
    else:
        lo_edge, hi_edge = vol.x0, vol.x1
        span_lo, span_hi = vol.z0 - gable_overhang, vol.z1 + gable_overhang
        lo_face, hi_face = "east", "west"
        gable_walls = ("front", "back")

    lo = lo_edge - eave_overhang
    hi = hi_edge + eave_overhang

    def put_row(coord: int, y: int, state: str, protect: bool = False) -> None:
        tags = ["ROOF"] + (["PROTECTED"] if protect else [])
        for a in range(span_lo, span_hi + 1):
            pos = (a, y, coord) if ridge_axis == "x" else (coord, y, a)
            if grid.set(pos, state, tags, p, "ROOF_DARK"):
                roof_cells.append(pos)
                if protect:
                    ridge_cells.append(pos)

    y = wall_top + 1
    crown_gap = 1 if rounded else 0
    while hi - lo > crown_gap:
        put_row(lo, y, stair_state(style, lo_face))
        put_row(hi, y, stair_state(style, hi_face))
        lo += 1
        hi -= 1
        y += 1
    if rounded:
        # 卷棚: no ridge plank. Cap the central one or two columns with top
        # slabs so the two slopes roll over a smooth crown.
        crown = slab_state(style, "top")
        for coord in sorted({lo, hi}):
            put_row(coord, y, crown, protect=True)
        ridge_y = y
    elif lo == hi:        # odd span: solid ridge line, protected
        put_row(lo, y, planks, protect=True)
        ridge_y = y
    else:                 # even span: the two top stair rows meet back to back
        ridge_y = y - 1

    if seal_gables:
        # Gable end-wall infill, flush with the end walls, driven per-column up
        # to the roof skin directly above each column (the same column-scan as
        # gable_roof: no apex gap, no edge gap, no see-through air).
        for wallname in gable_walls:
            _, fixed, (a0, a1), _ = wall_info(vol, wallname)
            for c in range(a0, a1 + 1):
                roof_y = None
                for yy in range(wall_top + 1, ridge_y + 1):
                    pos = (fixed, yy, c) if ridge_axis == "x" else (c, yy, fixed)
                    rc = grid.get(pos)
                    if rc and "ROOF" in rc.tags and "FACADE" not in rc.tags:
                        roof_y = yy
                        break
                if roof_y is None:
                    roof_y = ridge_y
                for yy in range(wall_top + 1, roof_y):
                    pos = (fixed, yy, c) if ridge_axis == "x" else (c, yy, fixed)
                    if grid.is_empty(pos):
                        if grid.set(pos, gable_state, ["FACADE", "ROOF"], p,
                                    gable_slot):
                            gable_cells.append(pos)
        # Back any gable-plane stair with a full block one step inboard so the
        # end wall has no see-through half-block gaps along the roofline.
        for wallname in gable_walls:
            _, fixed, (a0, a1), _ = wall_info(vol, wallname)
            ox, oz = WALL_OUTWARD[wallname]
            inboard = (-ox, -oz)
            for c in range(a0, a1 + 1):
                for yy in range(wall_top + 1, ridge_y + 1):
                    pos = (fixed, yy, c) if ridge_axis == "x" else (c, yy, fixed)
                    sc = grid.get(pos)
                    if sc and "ROOF" in sc.tags and "_stairs" in sc.state:
                        back = (pos[0] + inboard[0], yy, pos[2] + inboard[1])
                        if grid.is_empty(back):
                            if grid.set(back, gable_state, ["FACADE", "ROOF"], p,
                                        gable_slot):
                                gable_cells.append(back)
                        gable_cells.append(pos)
    return {"gable_cells": gable_cells, "roof_cells": roof_cells,
            "ridge_cells": ridge_cells, "peak_y": ridge_y}


def _chinese_hip_skirt(grid: BlockGrid, style: Style, vol: Node,
                       steps: int, eave_overhang: int) -> dict:
    """45° 抱厦 skirt: ``steps`` hipped stair rings wrapping all four sides,
    rising from the eave. Returns the inner rectangle the upper gable sits on,
    the skirt-top y (where the 围脊 lands), and the placed cells."""
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    p = PRIORITY["ROOF"]
    roof_cells: List[Pos] = []
    x0, x1 = vol.x0 - eave_overhang, vol.x1 + eave_overhang
    z0, z1 = vol.z0 - eave_overhang, vol.z1 + eave_overhang
    y = wall_top + 1
    north, north_slot = roof_stair_state(style, "south")
    south, south_slot = roof_stair_state(style, "north")
    west, west_slot = roof_stair_state(style, "east")
    east, east_slot = roof_stair_state(style, "west")
    for _ in range(max(1, steps)):
        if x0 > x1 or z0 > z1:
            break
        for x in range(x0, x1 + 1):
            for pos, state, slot in (((x, y, z0), north, north_slot),
                                     ((x, y, z1), south, south_slot)):
                if grid.set(pos, state, ["ROOF"], p, slot):
                    roof_cells.append(pos)
        for z in range(z0 + 1, z1):
            for pos, state, slot in (((x0, y, z), west, west_slot),
                                     ((x1, y, z), east, east_slot)):
                if grid.set(pos, state, ["ROOF"], p, slot):
                    roof_cells.append(pos)
        x0 += 1
        x1 -= 1
        z0 += 1
        z1 -= 1
        y += 1
    return {"roof_cells": roof_cells, "inner_bounds": (x0, x1, z0, z1),
            "skirt_top_y": y}


def _chinese_flush_gable_handler(grid: BlockGrid, style: Style,
                                 rng: random.Random, vol: Node,
                                 graph: Optional[Any] = None) -> dict:
    """硬山: gable flush with the side walls (no overhang past the gable end)."""
    del rng
    roof = vol.meta.get("roof", {})
    info = _chinese_gable_planes(grid, style, vol, roof.get("ridge_axis", "x"),
                                 gable_overhang=0,
                                 eave_overhang=max(1, roof.get("overhang", 1)))
    return _with_roofed_ids(_with_roof_type(info, "chinese_flush_gable"), vol.id)


def _chinese_overhang_gable_handler(grid: BlockGrid, style: Style,
                                    rng: random.Random, vol: Node,
                                    graph: Optional[Any] = None) -> dict:
    """悬山: roof overhangs past the gable wall on both gable ends."""
    del rng
    roof = vol.meta.get("roof", {})
    gov = max(1, roof.get("gable_overhang", roof.get("overhang", 2)))
    info = _chinese_gable_planes(grid, style, vol, roof.get("ridge_axis", "x"),
                                 gable_overhang=gov,
                                 eave_overhang=max(1, roof.get("overhang", 1)))
    return _with_roofed_ids(_with_roof_type(info, "chinese_overhang_gable"),
                            vol.id)


def _chinese_half_hip_handler(grid: BlockGrid, style: Style, rng: random.Random,
                              vol: Node, graph: Optional[Any] = None) -> dict:
    """歇山: upper 悬山 gable over the gable wall + lower 45° 抱厦 skirt + 围脊."""
    del rng
    roof = vol.meta.get("roof", {})
    ridge_axis = roof.get("ridge_axis", "x")
    eave = max(1, roof.get("overhang", 1))
    steps = 2 if min(vol.size[0], vol.size[2]) >= 9 else 1
    skirt = _chinese_hip_skirt(grid, style, vol, steps, eave)
    ix0, ix1, iz0, iz1 = skirt["inner_bounds"]
    skirt_top = skirt["skirt_top_y"]
    p = PRIORITY["ROOF"]
    gable_cells: List[Pos] = []
    roof_cells = list(skirt["roof_cells"])

    if ix0 > ix1 or iz0 > iz1:
        # Footprint too small for a skirt + gable; fall back to a closed hip.
        info = _ring_roof(grid, style, (vol.x0 - eave, vol.x1 + eave,
                                        vol.z0 - eave, vol.z1 + eave),
                          wall_top_plus_one(vol))
        info["roof_cells"] = roof_cells + info["roof_cells"]
        return _with_roofed_ids(_with_roof_type(info, "chinese_half_hip"), vol.id)

    # Upper gable sits on the inner rectangle, raised to the skirt top. Build it
    # as a sub-volume whose wall_top equals the skirt top so the shared gable
    # helper drops the slopes from there.
    inner_wall_h = skirt_top - vol.meta["foundation_h"]
    upper = Node(
        id=f"{vol.id}_upper_gable", type="roof_tier",
        origin=(ix0, vol.y0, iz0),
        size=(ix1 - ix0 + 1, skirt_top - vol.y0, iz1 - iz0 + 1),
        attach_to=vol.id, priority=vol.priority, tags=list(vol.tags),
        meta={"foundation_h": vol.meta["foundation_h"], "wall_h": inner_wall_h},
    )
    upper_info = _chinese_gable_planes(grid, style, upper, ridge_axis,
                                       gable_overhang=1, eave_overhang=1)
    roof_cells += upper_info["roof_cells"]
    gable_cells += upper_info["gable_cells"]

    # 围脊: a ridge-tile course along the inner perimeter where the skirt meets
    # the upper gable wall, sealing the gable-to-skirt seam.
    weiji = style.slot_entry("ROOF_DARK", "_slab")
    for x in range(ix0, ix1 + 1):
        for z in (iz0, iz1):
            pos = (x, skirt_top, z)
            if grid.set(pos, weiji, ["ROOF", "PROTECTED"], p, "ROOF_DARK"):
                roof_cells.append(pos)
    for z in range(iz0 + 1, iz1):
        for x in (ix0, ix1):
            pos = (x, skirt_top, z)
            if grid.set(pos, weiji, ["ROOF", "PROTECTED"], p, "ROOF_DARK"):
                roof_cells.append(pos)

    # Close any gap on the inner perimeter wall between the skirt top and the
    # upper roof skin (gable-to-skirt seam closure — the half-hip roof-hole
    # defect class from the side-wall-cleanup lesson).
    gable_state, gable_slot = gable_infill(style)
    peak_y = upper_info["peak_y"]
    for x in range(ix0, ix1 + 1):
        for z in (iz0, iz1):
            _seal_seam_column(grid, (x, z), skirt_top, peak_y, gable_state,
                              gable_slot, p, gable_cells)
    for z in range(iz0 + 1, iz1):
        for x in (ix0, ix1):
            _seal_seam_column(grid, (x, z), skirt_top, peak_y, gable_state,
                              gable_slot, p, gable_cells)

    seam_columns = ({(x, z) for x in range(ix0, ix1 + 1) for z in (iz0, iz1)} |
                    {(x, z) for z in range(iz0 + 1, iz1) for x in (ix0, ix1)})
    seam_cells: List[Pos] = []
    for x, z in sorted(seam_columns):
        roof_y = next((y for y in range(skirt_top, peak_y + 2)
                       if (grid.get((x, y, z)) is not None
                           and "ROOF" in grid.get((x, y, z)).tags
                           and "FACADE" not in grid.get((x, y, z)).tags)), None)
        if roof_y is not None:
            seam_cells.extend((x, y, z) for y in range(skirt_top, roof_y + 1))

    info = {"gable_cells": gable_cells, "roof_cells": roof_cells,
            "peak_y": peak_y, "skirt_top_y": skirt_top,
            "seam_cells": seam_cells}
    return _with_roofed_ids(_with_roof_type(info, "chinese_half_hip"), vol.id)


def _chinese_round_ridge_handler(grid: BlockGrid, style: Style,
                                 rng: random.Random, vol: Node,
                                 graph: Optional[Any] = None) -> dict:
    """卷棚: no main ridge block — the two slopes roll over a slab crown."""
    del rng
    roof = vol.meta.get("roof", {})
    info = _chinese_gable_planes(grid, style, vol, roof.get("ridge_axis", "x"),
                                 gable_overhang=max(0, roof.get("overhang", 1) - 1),
                                 eave_overhang=max(1, roof.get("overhang", 1)),
                                 rounded=True)
    return _with_roofed_ids(_with_roof_type(info, "chinese_round_ridge"), vol.id)


def wall_top_plus_one(vol: Node) -> int:
    return vol.meta["foundation_h"] + vol.meta["wall_h"]


def _seal_seam_column(grid: BlockGrid, cell: Tuple[int, int], y_lo: int,
                      y_hi: int, state: str, slot: str, p: int,
                      sink: List[Pos]) -> None:
    x, z = cell
    roof_y = None
    for y in range(y_lo, y_hi + 2):
        rc = grid.get((x, y, z))
        if rc and "ROOF" in rc.tags and "FACADE" not in rc.tags:
            roof_y = y
            break
    if roof_y is None:
        return
    for y in range(y_lo, roof_y):
        if grid.is_empty((x, y, z)):
            if grid.set((x, y, z), state, ["FACADE", "ROOF"], p, slot):
                sink.append((x, y, z))


# ---------------------------------------------------------------------------
# attachments & details
# ---------------------------------------------------------------------------

def chimney(grid: BlockGrid, style: Style, node: Node) -> None:
    base = style.primary("BASE_STONE")
    top = style.slot_entry("BASE_STONE", "brick", style.primary("BASE_STONE"))
    p = PRIORITY["STRUCTURE"]
    for z in range(node.z0, node.z1 + 1):
        for y in range(node.y0, node.y1 + 1):
            state = top if y >= node.y1 - 1 else base
            grid.set((node.x0, y, z), state,
                     ["STRUCTURE", "DETAIL", "PROTECTED"], p, "BASE_STONE",
                     force=True)


def porch(grid: BlockGrid, style: Style, rng: random.Random, node: Node,
          main: Node) -> None:
    fh = main.meta["foundation_h"]
    roof_y = min(fh + 3, fh + main.meta["wall_h"])
    p = PRIORITY["DETAIL"]
    floor_y = fh - 2
    # corner posts on the outer edge
    for px in (node.x0, node.x1):
        for y in range(floor_y, roof_y):
            grid.set((px, y, node.z0), fence_state(style), ["DETAIL"], p,
                     "DETAIL_WOOD")
    # slab roof over the porch, skipping cells the main roof already used
    for x in range(node.x0, node.x1 + 1):
        for z in range(node.z0, node.z1 + 1):
            pos = (x, roof_y, z)
            if grid.is_empty(pos):
                grid.set(pos, slab_state(style, "bottom"), ["DETAIL", "ROOF"],
                         p, "ROOF_DARK")
    # hanging lantern near the door
    door_x = node.meta.get("door_x", (node.x0 + node.x1) // 2)
    lpos = (door_x, roof_y - 1, node.z0 + node.size[2] // 2)
    if grid.is_empty(lpos):
        grid.set(lpos, lantern_state(style, hanging=True), ["DETAIL"], p,
                 "LIGHTING")
    # porch floor pad; all entry hardscape sits one block below the stair.
    for x in range(node.x0, node.x1 + 1):
        for z in range(node.z0, node.z1 + 1):
            pos = (x, floor_y, z)
            if grid.is_empty(pos):
                grid.set(pos, rng.choice(style.material_slots["GROUND_PATH"]),
                         ["DETAIL", "GROUND"], p, "GROUND_PATH")


def chinese_timber_brackets(grid: BlockGrid, style: Style, rng: random.Random,
                            vol: Node) -> None:
    """Small dougong-like rhythm under the front/back eaves."""
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    p = PRIORITY["DETAIL"]
    bracket = fence_state(style)
    for wall in ("front", "back"):
        axis, fixed, (a0, a1), _ = wall_info(vol, wall)
        outward = OUTWARD_FACING[wall]
        y = wall_top
        for along in range(a0 + 2, a1 - 1, 3):
            pos = wall_pos(vol, wall, along, y, depth_offset=1)
            if grid.is_empty(pos):
                grid.set(pos, bracket, ["DETAIL"], p, "DETAIL_WOOD")
            trim = wall_pos(vol, wall, along, y - 1, depth_offset=1)
            if grid.is_empty(trim):
                grid.set(trim, trapdoor_state(style, outward, rng),
                         ["DETAIL"], p, "DETAIL_WOOD")


def raised_platform(grid: BlockGrid, style: Style, node: Node) -> None:
    block, slot = _platform_state(style)
    height = max(1, int(node.meta.get("height", node.size[1])))
    p = PRIORITY["FOUNDATION"]
    for x in range(node.x0, node.x1 + 1):
        for z in range(node.z0, node.z1 + 1):
            for y in range(0, height):
                grid.set((x, y, z), block, ["FOUNDATION", "STRUCTURE", "GROUND"],
                         p, slot)
    door_x = node.meta.get("door_x")
    if door_x is None:
        return
    facing = node.meta.get("facing", "south")
    stair, stair_slot = _platform_stair_state(style, facing)
    z0 = node.z0 - 1
    for dx in (-1, 0, 1):
        grid.set((door_x + dx, height - 1, z0), stair,
                 ["DETAIL", "GROUND", "PROTECTED"], PRIORITY["DETAIL"], stair_slot,
                 force=True)
        if height > 1:
            grid.set((door_x + dx, height - 2, z0 - 1), stair,
                     ["DETAIL", "GROUND", "PROTECTED"], PRIORITY["DETAIL"],
                     stair_slot, force=True)


def dougong_brackets(grid: BlockGrid, style: Style, rng: random.Random,
                     vol: Node, sides: Iterable[str]) -> int:
    del rng
    column, column_slot = _column_state(style)
    bracket = style.slot_entry("DETAIL_WOOD", "_slab")
    wall_top = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
    p = PRIORITY["DETAIL"]
    placed = 0
    for wall in sides:
        axis, _fixed, (a0, a1), _outward = wall_info(vol, wall)
        step = 3
        for along in range(a0 + 2, a1 - 1, step):
            cap_axis = "x" if axis == "x" else "z"
            head = wall_pos(vol, wall, along, wall_top, depth_offset=1)
            outer = wall_pos(vol, wall, along, wall_top, depth_offset=2)
            upper = wall_pos(vol, wall, along, wall_top + 1, depth_offset=2)
            if column:
                grid.set(head, column, ["DETAIL", "STRUCTURE"], p, column_slot)
            for pos in (head, outer, upper):
                if grid.set(pos, bracket, ["DETAIL"], p, "DETAIL_WOOD"):
                    placed += 1
            for side in (-1, 1):
                arm = wall_pos(vol, wall, along + side, wall_top, depth_offset=1)
                if grid.set(arm, slab_state(style, "bottom", "DETAIL_WOOD"),
                            ["DETAIL"], p, "DETAIL_WOOD"):
                    placed += 1
            if column and cap_axis:
                grid.set(wall_pos(vol, wall, along, wall_top - 1, depth_offset=1),
                         column, ["DETAIL", "STRUCTURE"], p, column_slot)
    return placed


def colonnade(grid: BlockGrid, style: Style, rng: random.Random,
              node: Node, vol: Node) -> None:
    column, slot = _column_state(style)
    if not column:
        return
    sides = tuple(node.meta.get("sides", ("front",)))
    start_y = max(0, int(node.meta.get("base_y", vol.meta.get("foundation_h", 1) - 1)))
    wall_top = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
    door = node.meta.get("door_x")
    p = PRIORITY["DETAIL"]
    for wall in sides:
        _axis, _fixed, (a0, a1), _outward = wall_info(vol, wall)
        positions = list(range(a0 + 1, a1, 3))
        for corner in (a0 + 1, a1 - 1):
            if a0 < corner < a1 and corner not in positions:
                positions.append(corner)
        for along in sorted(set(positions)):
            if wall == "front" and door is not None and abs(along - int(door)) <= 1:
                continue
            for y in range(start_y, wall_top + 1):
                grid.set(wall_pos(vol, wall, along, y, depth_offset=2),
                         column, ["DETAIL", "STRUCTURE"], p, slot)
    dougong_brackets(grid, style, rng, vol, sides)


def balustrade(grid: BlockGrid, style: Style, node: Node) -> None:
    rail, slot = _balustrade_state(style)
    if not rail:
        return
    y = node.y0
    gap_wall = node.meta.get("gap_wall")
    gap_center = node.meta.get("gap_center")
    p = PRIORITY["DETAIL"]
    for x in range(node.x0, node.x1 + 1):
        for z, wall in ((node.z0, "front"), (node.z1, "back")):
            if wall == gap_wall and gap_center is not None and abs(x - int(gap_center)) <= 1:
                continue
            grid.set((x, y, z), rail, ["DETAIL", "STRUCTURE"], p, slot)
    for z in range(node.z0 + 1, node.z1):
        for x, wall in ((node.x0, "west"), (node.x1, "east")):
            if wall == gap_wall and gap_center is not None and abs(z - int(gap_center)) <= 1:
                continue
            grid.set((x, y, z), rail, ["DETAIL", "STRUCTURE"], p, slot)


def courtyard_enclosure(grid: BlockGrid, style: Style, node: Node) -> None:
    """院墙: a low enclosing wall ring around a rear courtyard ground patch,
    leaving a single one-cell gate opening on the entry-adjacent side.

    Honours the cultivation grammar's "omit Western domestic tells" rule by
    construction: the wall body resolves through PLATFORM_STONE (BASE_STONE
    fallback) and the coping through BALUSTRADE / RIDGE_ORNAMENT -- stone/tile
    wall slots only, never fence-post or woodpile features."""
    wall, slot = _platform_state(style)
    cap, cap_slot = _balustrade_state(style)
    if not cap:
        cap, cap_slot = _ornament_state(style)
    height = max(1, int(node.meta.get("height", 2)))
    gate_wall = node.meta.get("gate_wall", "front")
    gate_center = node.meta.get("gate_center")
    p = PRIORITY["STRUCTURE"]
    pd = PRIORITY["DETAIL"]

    def is_gate(wall_name: str, along: int) -> bool:
        return (wall_name == gate_wall and gate_center is not None
                and along == int(gate_center))

    def post(x: int, z: int) -> None:
        for y in range(0, height):
            grid.set((x, y, z), wall, ["STRUCTURE", "GROUND"], p, slot)
        if cap:
            grid.set((x, height, z), cap, ["DETAIL", "STRUCTURE"], pd, cap_slot)

    for x in range(node.x0, node.x1 + 1):
        for z, wname in ((node.z0, "front"), (node.z1, "back")):
            if not is_gate(wname, x):
                post(x, z)
    for z in range(node.z0 + 1, node.z1):
        for x, wname in ((node.x0, "west"), (node.x1, "east")):
            if not is_gate(wname, z):
                post(x, z)


def pagoda_story_insets(grid: BlockGrid, style: Style, vol: Node) -> None:
    insets = list(vol.meta.get("story_insets", []))
    stories = int(vol.meta.get("stories", 1))
    if stories <= 1 or len(insets) < stories:
        return
    fh = vol.meta["foundation_h"]
    story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
    wall = style.primary("WALL_MAIN")
    frame = log_state(style, "y")
    p_structure = PRIORITY["FACADE"]
    p_roof = PRIORITY["ROOF"]
    for story in range(1, stories):
        inset = max(0, int(insets[story]))
        prev_inset = max(0, int(insets[story - 1]))
        y0 = fh + story * story_wall_h
        y1 = min(fh + (story + 1) * story_wall_h - 1,
                 fh + vol.meta["wall_h"] - 1)
        # Eave band at the story break on the wider lower footprint.
        bx0, bx1 = vol.x0 + prev_inset - 1, vol.x1 - prev_inset + 1
        bz0, bz1 = vol.z0 + prev_inset - 1, vol.z1 - prev_inset + 1
        slab, slab_slot = roof_slab_state(style, "bottom")
        for x in range(bx0, bx1 + 1):
            grid.set((x, y0, bz0), slab, ["ROOF", "DETAIL"], p_roof, slab_slot)
            grid.set((x, y0, bz1), slab, ["ROOF", "DETAIL"], p_roof, slab_slot)
        for z in range(bz0 + 1, bz1):
            grid.set((bx0, y0, z), slab, ["ROOF", "DETAIL"], p_roof, slab_slot)
            grid.set((bx1, y0, z), slab, ["ROOF", "DETAIL"], p_roof, slab_slot)
        nx0, nx1 = vol.x0 + inset, vol.x1 - inset
        nz0, nz1 = vol.z0 + inset, vol.z1 - inset
        for x in range(vol.x0, vol.x1 + 1):
            for z in range(vol.z0, vol.z1 + 1):
                outside_inset = x < nx0 or x > nx1 or z < nz0 or z > nz1
                if not outside_inset:
                    continue
                for y in range(y0 + 1, y1 + 1):
                    grid.set((x, y, z), AIR, ["AIR_CARVE"], PRIORITY["AIR_CARVE"],
                             force=True)
        for y in range(y0 + 1, y1 + 1):
            for x in range(nx0, nx1 + 1):
                grid.set((x, y, nz0), wall, ["FACADE", "STRUCTURE"],
                         p_structure, "WALL_MAIN")
                grid.set((x, y, nz1), wall, ["FACADE", "STRUCTURE"],
                         p_structure, "WALL_MAIN")
            for z in range(nz0, nz1 + 1):
                grid.set((nx0, y, z), wall, ["FACADE", "STRUCTURE"],
                         p_structure, "WALL_MAIN")
                grid.set((nx1, y, z), wall, ["FACADE", "STRUCTURE"],
                         p_structure, "WALL_MAIN")
            for pos in ((nx0, y, nz0), (nx0, y, nz1), (nx1, y, nz0), (nx1, y, nz1)):
                grid.set(pos, frame, ["FACADE", "STRUCTURE"], p_structure,
                         "FRAME_WOOD")


def mountain_gate_detail(grid: BlockGrid, style: Style, rng: random.Random,
                         node: Node, vol: Node) -> None:
    del node
    column, column_slot = _column_state(style)
    if not column:
        column = log_state(style, "y")
        column_slot = "FRAME_WOOD"
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
    center = (vol.x0 + vol.x1) // 2
    p = PRIORITY["DETAIL"]
    pillar_xs = [vol.x0 + 1, center - 3, center + 3, vol.x1 - 1]
    for px in sorted(set(pillar_xs)):
        for depth in (1, 2):
            for y in range(fh - 1, wall_top + 1):
                grid.set(wall_pos(vol, "front", px, y, depth_offset=depth),
                         column, ["DETAIL", "STRUCTURE"], p, column_slot)
    for along in range(vol.x0 + 1, vol.x1):
        for y in range(fh + 1, min(wall_top, fh + 3) + 1):
            if abs(along - center) <= 1 or abs(along - (center - 5)) <= 1 or abs(along - (center + 5)) <= 1:
                grid.set(wall_pos(vol, "front", along, y), AIR,
                         ["AIR_CARVE", "PROTECTED"], PRIORITY["OPENING"])
    beam = log_state(style, "x", rng)
    for zoff in (1, 2):
        for along in range(vol.x0 + 1, vol.x1):
            grid.set(wall_pos(vol, "front", along, wall_top, depth_offset=zoff),
                     beam, ["DETAIL", "STRUCTURE"], p, "FRAME_WOOD")


def alchemy_furnace(grid: BlockGrid, style: Style, node: Node) -> None:
    anchor = style.optional_slot_entry(
        "RITUAL_ANCHOR", ":",
        style.slot_entry("INTERIOR_CIVIC", "cauldron", "minecraft:cauldron"))
    metal = style.optional_slot_entry(
        "RITUAL_METAL", "copper",
        style.slot_entry("BASE_STONE", "stone", style.primary("BASE_STONE")))
    crystal = (
        style.optional_slot_entry("SPIRIT_CRYSTAL", "amethyst")
        or style.optional_slot_entry("LIGHTING", "lamp")
        or style.slot_entry("LIGHTING", "lantern", "minecraft:lantern[hanging=false,waterlogged=false]")
    )
    x0, y0, z0 = node.x0, node.y0, node.z0
    p = PRIORITY["INTERIOR"]
    for dx in range(3):
        for dz in range(3):
            slot = "RITUAL_METAL" if style.has_slot("RITUAL_METAL") else "BASE_STONE"
            grid.set((x0 + dx, y0, z0 + dz), metal,
                     ["INTERIOR", "DETAIL", "PROTECTED"], p, slot)
    grid.set((x0 + 1, y0 + 1, z0 + 1), anchor,
             ["INTERIOR", "DETAIL", "PROTECTED"], p, "RITUAL_ANCHOR")
    for dx, dz in ((0, 0), (0, 2), (2, 0), (2, 2)):
        grid.set((x0 + dx, y0 + 1, z0 + dz), metal,
                 ["INTERIOR", "DETAIL", "PROTECTED"], p,
                 "RITUAL_METAL" if style.has_slot("RITUAL_METAL") else "BASE_STONE")
    grid.set((x0 + 1, y0 + 2, z0 + 1), crystal,
             ["INTERIOR", "DETAIL", "PROTECTED"], p,
             "SPIRIT_CRYSTAL" if style.has_slot("SPIRIT_CRYSTAL") else "LIGHTING")


def _slot_choice(style: Style, rng: random.Random, slot: str,
                 contains: str, default: Optional[str] = None) -> Optional[str]:
    options = style.slot_options(slot, contains) if hasattr(style, "slot_options") else []
    if options:
        return rng.choice(options)
    return default


def _optional_slot_block(style: Style, slot: str, contains: Iterable[str],
                         default: Optional[str] = None) -> Optional[str]:
    for needle in contains:
        state = style.optional_slot_entry(slot, needle)
        if state:
            return _block_id(state)
    if default is not None:
        return _block_id(default)
    return None


def wall_hanging(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
                 wall: str, along: int, y: int, slot: str, contains: str,
                 outside: bool = False) -> bool:
    base = _slot_choice(style, rng, slot, contains)
    if not base:
        return False
    facing = OUTWARD_FACING[wall] if outside else INWARD_FACING[wall]
    state = f"{_block_id(base)}[facing={facing}]"
    if "sign" in base:
        state = f"{_block_id(base)}[facing={facing},waterlogged=false]"
    pos = wall_pos(vol, wall, along, y, depth_offset=1 if outside else -1)
    if not outside:
        pos = wall_pos(vol, wall, along, y, depth_offset=-1)
    return grid.set(pos, state, ["DETAIL", "PROTECTED"], PRIORITY["DETAIL"], slot)


def _tangent_vec(facing: str) -> tuple[int, int]:
    return (1, 0) if facing in ("north", "south") else (0, 1)


def _offset_xz(pos: Pos, facing: str, tangent_offset: int = 0,
               normal_offset: int = 0, y_offset: int = 0) -> Pos:
    tx, tz = _tangent_vec(facing)
    nx, nz = DIR_VEC[facing]
    return (
        pos[0] + tx * tangent_offset + nx * normal_offset,
        pos[1] + y_offset,
        pos[2] + tz * tangent_offset + nz * normal_offset,
    )


def _col_for_index(index: int, width: int) -> str:
    if width <= 1:
        return "single"
    values = {
        2: ["left", "right"],
        3: ["left", "center", "right"],
        4: ["left", "inner_left", "inner_right", "right"],
        5: ["left", "inner_left", "center", "inner_right", "right"],
    }.get(width)
    if values is None:
        raise ValueError(f"unsupported plaque width {width}")
    return values[index]


def _row_for_y(y_index: int, height: int) -> str:
    if height <= 1:
        return "single"
    bottom_to_top = {
        2: ["bottom", "top"],
        3: ["bottom", "middle", "top"],
        4: ["bottom", "lower_middle", "upper_middle", "top"],
        5: ["bottom", "lower_middle", "middle", "upper_middle", "top"],
    }.get(height)
    if bottom_to_top is None:
        raise ValueError(f"unsupported plaque height {height}")
    return bottom_to_top[y_index]


def _plaque_state(binding: plaque_bindings.Binding, facing: str,
                  row: str, col: str) -> str:
    return (
        f"{binding.block_id}[facing={facing},frame={binding.frame},"
        f"row={row},col={col}]"
    )


def _visual_left_uses_positive_tangent(facing: str) -> bool:
    # For a viewer standing outside the facade, north/east-facing plaques have
    # their visual left side at +x/+z; south/west-facing plaques are the inverse.
    return facing in ("north", "east")


def _place_plaque(grid: BlockGrid, center_pos: Pos, facing: str,
                  binding: plaque_bindings.Binding, chains: bool) -> bool:
    p = PRIORITY["DETAIL"]
    width = binding.width
    height = binding.height
    placed: list[Pos] = []
    ok = False

    if binding.orientation == "vertical":
        for y_index in range(height):
            row = _row_for_y(y_index, height)
            pos = _offset_xz(center_pos, facing, 0, 0, y_index)
            ok |= grid.set(pos, _plaque_state(binding, facing, row, "single"),
                           ["DETAIL", "PROTECTED"], p, "SIGNAGE")
            placed.append(pos)
    else:
        start_offset = -(width // 2)
        reverse_wall_order = (
            binding.mount == "wall"
            and _visual_left_uses_positive_tangent(facing)
        )
        for x_index in range(width):
            offset_index = width - 1 - x_index if reverse_wall_order else x_index
            tangent_offset = start_offset + offset_index
            col = _col_for_index(x_index, width)
            for y_index in range(height):
                row = _row_for_y(y_index, height)
                pos = _offset_xz(center_pos, facing, tangent_offset, 0, y_index)
                ok |= grid.set(pos, _plaque_state(binding, facing, row, col),
                               ["DETAIL", "PROTECTED"], p, "SIGNAGE")
                placed.append(pos)

    if chains:
        chain = "minecraft:chain[axis=y,waterlogged=false]"
        if binding.orientation == "vertical":
            chain_positions = [_offset_xz(center_pos, facing, 0, 0, height)]
        else:
            offsets = [-(width // 2), -(width // 2) + width - 1]
            if width >= 5:
                offsets.append(0)
            chain_positions = [_offset_xz(center_pos, facing, offset, 0, height)
                               for offset in sorted(set(offsets))]
        for pos in chain_positions:
            ok |= grid.set(pos, chain, ["DETAIL"], p, "SIGNAGE")

    return ok


def _side_along_for_vertical(vol: Node, wall: str, along: int,
                             plaque_height: int) -> int:
    _axis, _fixed, (lo, hi), _outward = wall_info(vol, wall)
    del plaque_height
    right = along + 2
    left = along - 2
    if right <= hi - 1:
        return right
    if left >= lo + 1:
        return left
    return max(lo + 1, min(hi - 1, along))


def plaque_wall_anchor(vol: Node, wall: str, along: int, y: int,
                       binding: plaque_bindings.Binding) -> Pos:
    if binding.orientation == "vertical":
        side_along = _side_along_for_vertical(vol, wall, along, binding.height)
        base_y = max(vol.meta.get("foundation_h", 1) + 1, y - binding.height + 1)
        return wall_pos(vol, wall, side_along, base_y, depth_offset=1)
    return wall_pos(vol, wall, along, y, depth_offset=1)


def place_wall_plaque(grid: BlockGrid, style: Style, rng: random.Random,
                      vol: Node, wall: str, along: int, y: int,
                      binding: plaque_bindings.Binding) -> bool:
    del style, rng
    facing = OUTWARD_FACING[wall]
    anchor = plaque_wall_anchor(vol, wall, along, y, binding)
    return _place_plaque(grid, anchor, facing, binding, chains=False)


def place_hanging_plaque(grid: BlockGrid, style: Style, rng: random.Random,
                         vol: Optional[Node], anchor_pos: Pos, facing: str,
                         binding: plaque_bindings.Binding) -> bool:
    del style, rng, vol
    anchor = anchor_pos
    return _place_plaque(grid, anchor, facing, binding, chains=True)


def mezzanine_floor(grid: BlockGrid, style: Style, vol: Node,
                    mezzanine_meta: dict) -> None:
    if vol.meta.get("stories", 1) <= 1:
        return
    story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
    floor_y = vol.meta["foundation_h"] + story_wall_h + mezzanine_meta.get("y_offset", 0)
    depth = mezzanine_meta.get("depth", max(1, (vol.size[0] - 2) // 2))
    ix0, ix1 = vol.x0 + 1, vol.x1 - 1
    if mezzanine_meta.get("covers") == "east":
        x0, x1 = max(ix0, vol.x1 - depth), ix1
    else:
        x0, x1 = ix0, min(ix1, vol.x0 + depth)
    slab = slab_state(style, "top", "DETAIL_WOOD")
    for x in range(x0, x1 + 1):
        for z in range(vol.z0 + 1, vol.z1):
            grid.set((x, floor_y, z), slab, ["STRUCTURE", "INTERIOR", "PROTECTED"],
                     PRIORITY["STRUCTURE"], "DETAIL_WOOD")


def tavern_bar_counter(grid: BlockGrid, style: Style, vol: Node,
                       rng: random.Random) -> int:
    y = vol.meta.get("foundation_h", 1)
    z = vol.z0 + 2
    x0 = vol.x0 + 3
    x1 = vol.x1 - 3
    if x1 < x0:
        return 0
    placed = 0
    for x in range(x0, x1 + 1):
        conn = []
        if x > x0:
            conn.append("west")
        if x < x1:
            conn.append("east")
        if grid.is_empty((x, y, z)):
            grid.set((x, y, z), fence_state(style, conn), ["INTERIOR", "DETAIL"],
                     PRIORITY["INTERIOR"], "DETAIL_WOOD")
            placed += 1
        top = (x, y + 1, z)
        if grid.is_empty(top):
            grid.set(top, slab_state(style, "bottom", "DETAIL_WOOD"),
                     ["INTERIOR", "DETAIL"], PRIORITY["INTERIOR"], "DETAIL_WOOD")
    return placed


def _bell_state(style: Style, facing: str, attachment: str = "floor") -> str:
    base = style.optional_slot_entry("INTERIOR_CIVIC", "bell") or "minecraft:bell"
    return f"{_block_id(base)}[attachment={attachment},facing={facing},powered=false]"


def belfry_bell(grid: BlockGrid, style: Style, vol: Node) -> bool:
    x = (vol.x0 + vol.x1) // 2
    z = (vol.z0 + vol.z1) // 2
    wall_top = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
    for y in range(wall_top + 3, vol.meta["foundation_h"] - 1, -1):
        if grid.is_empty((x, y, z)):
            return grid.set((x, y, z), _bell_state(style, "north", "ceiling"),
                            ["INTERIOR", "DETAIL", "PROTECTED"],
                            PRIORITY["INTERIOR"], "INTERIOR_CIVIC")
    return False


def interior_zone(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
                  zone: Node, door_cells: List[Pos]) -> int:
    """Furnish one interior zone; returns count of function blocks placed."""
    kind = zone.meta["kind"]
    fy = zone.y0
    p = PRIORITY["INTERIOR"]
    placed = 0
    # Owning volume bounds: furniture may mount only on a wall cell that
    # belongs to this same volume, never on a neighbour volume's exterior wall
    # (e.g. a blacksmith shed butted against the main building).
    vol_fh = vol.meta["foundation_h"]
    vol_wt = vol_fh + vol.meta["wall_h"] - 1

    def in_zone(pos: Pos) -> bool:
        return zone.x0 <= pos[0] <= zone.x1 and zone.z0 <= pos[2] <= zone.z1

    def owns_wall(pos: Pos) -> bool:
        return (vol.x0 <= pos[0] <= vol.x1 and vol.z0 <= pos[2] <= vol.z1
                and vol_fh <= pos[1] <= vol_wt)

    def near_door(pos: Pos) -> bool:
        return any(abs(pos[0] - d[0]) + abs(pos[2] - d[2]) <= 1 and abs(pos[1] - d[1]) <= 1
                   for d in door_cells)

    def spots_along_walls(y: Optional[int] = None) -> List[Tuple[Pos, str, str]]:
        base_y = fy if y is None else y
        out = []
        for x in range(zone.x0, zone.x1 + 1):
            for z in range(zone.z0, zone.z1 + 1):
                pos = (x, base_y, z)
                if not grid.is_empty(pos) or near_door(pos):
                    continue
                for wname, (ox, oz) in WALL_OUTWARD.items():
                    npos = (x + ox, base_y, z + oz)
                    ncell = grid.get(npos)
                    if (ncell and not ncell.is_air and "STRUCTURE" in ncell.tags
                            and owns_wall(npos)):
                        out.append((pos, INWARD_FACING[wname], wname))
                        break
        rng.shuffle(out)
        return out

    def put(state: str, slot: str, n: int = 1, y: Optional[int] = None) -> int:
        nonlocal placed
        done = 0
        for pos, facing, _ in spots_along_walls(y):
            if done >= n:
                break
            st = state.replace("{facing}", facing)
            if grid.set(pos, st, ["INTERIOR", "PROTECTED"], p, slot):
                placed += 1
                done += 1
        return done

    def put_wall_item(slot: str, contains: str, n: int = 1) -> int:
        nonlocal placed
        done = 0
        for pos, facing, _ in spots_along_walls(fy + 1):
            if done >= n:
                break
            base = _slot_choice(style, rng, slot, contains)
            if not base:
                break
            state = f"{_block_id(base)}[facing={facing}]"
            if "sign" in base:
                state = f"{_block_id(base)}[facing={facing},waterlogged=false]"
            if grid.set(pos, state, ["INTERIOR", "DETAIL", "PROTECTED"], p, slot):
                placed += 1
                done += 1
        return done

    def put_beds(n: int) -> List[Tuple[Pos, str]]:
        nonlocal placed
        beds: List[Tuple[Pos, str]] = []
        for head, facing, wall in spots_along_walls():
            if len(beds) >= n:
                break
            dx, dz = DIR_VEC[facing]
            foot = (head[0] + dx, head[1], head[2] + dz)
            wall_dx, wall_dz = DIR_VEC[OUTWARD_FACING[wall]]
            wall_cell = grid.get((head[0] + wall_dx, head[1] + 1, head[2] + wall_dz))
            if wall_cell and "glass" in wall_cell.state:
                continue
            if not in_zone(foot) or not grid.is_empty(foot) or near_door(foot):
                continue
            base = _slot_choice(style, rng, "FURNITURE", "_bed", "minecraft:red_bed")
            block = _block_id(base)
            head_state = f"{block}[facing={facing},occupied=false,part=head]"
            foot_state = f"{block}[facing={facing},occupied=false,part=foot]"
            ok_head = grid.set(head, head_state, ["INTERIOR", "PROTECTED"], p, "FURNITURE")
            ok_foot = grid.set(foot, foot_state, ["INTERIOR", "PROTECTED"], p, "FURNITURE")
            if ok_head and ok_foot:
                placed += 2
                beds.append((foot, facing))
        return beds

    def put_chest_at(pos: Pos, facing: str) -> bool:
        nonlocal placed
        if not in_zone(pos) or not grid.is_empty(pos) or near_door(pos):
            return False
        chest = style.slot_entry("FURNITURE", "chest", "minecraft:chest")
        state = f"{_block_id(chest)}[facing={facing},type=single,waterlogged=false]"
        if grid.set(pos, state, ["INTERIOR", "PROTECTED"], p, "FURNITURE"):
            placed += 1
            return True
        return False

    crafting = style.slot_entry("INTERIOR_WORK", "crafting")
    furnace = style.slot_entry("INTERIOR_WORK", "furnace")
    smithing = style.slot_entry("INTERIOR_WORK", "smithing")
    barrel = style.slot_entry("INTERIOR_STORAGE", "barrel")
    furnace_tpl = f"{furnace}[facing={{facing}},lit=false]"

    if kind == "living":
        put(crafting, "INTERIOR_WORK")
        put(furnace_tpl, "INTERIOR_WORK")
        put(f"{barrel}[facing=up,open=false]", "INTERIOR_STORAGE")
        if zone.meta.get("private_quarters"):
            beds = put_beds(1)
            for foot, facing in beds[:1]:
                dx, dz = DIR_VEC[facing]
                put_chest_at((foot[0] + dx, foot[1], foot[2] + dz), facing)
    elif kind == "work":
        put(crafting, "INTERIOR_WORK")
        put(smithing, "INTERIOR_WORK")
    elif kind == "storage":
        put(f"{barrel}[facing=up,open=false]", "INTERIOR_STORAGE",
            n=rng.choice([1, 2]))
    elif kind == "forge":
        put(furnace_tpl, "INTERIOR_WORK", n=2)
        put(smithing, "INTERIOR_WORK")
    elif kind == "smithy":
        put("minecraft:anvil[facing=north]", "INTERIOR_WORK")
        put(f"{barrel}[facing=up,open=false]", "INTERIOR_STORAGE")
    elif kind == "tavern_hall":
        placed += tavern_bar_counter(grid, style, vol, rng)
        brewing = style.slot_entry("INTERIOR_CIVIC", "brewing_stand",
                                   "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]")
        put(brewing, "INTERIOR_CIVIC", n=rng.choice([1, 2]))
        put(f"{barrel}[facing=up,open=false]", "INTERIOR_STORAGE", n=rng.choice([3, 4]))
        put(furnace_tpl, "INTERIOR_WORK")
        put(style.slot_entry("INTERIOR_CIVIC", "cauldron", "minecraft:cauldron"),
            "INTERIOR_CIVIC")
    elif kind == "tavern_inn":
        beds = put_beds(rng.choice([1, 2, 3]))
        for foot, facing in beds[:1]:
            dx, dz = DIR_VEC[facing]
            if not put_chest_at((foot[0] + dx, foot[1], foot[2] + dz), facing):
                put_chest_at(foot, facing)
        put(crafting, "INTERIOR_WORK")
    elif kind == "town_chamber":
        lectern = style.slot_entry("INTERIOR_CIVIC", "lectern", "minecraft:lectern")
        put(f"{_block_id(lectern)}[facing={{facing}},has_book=false,powered=false]",
            "INTERIOR_CIVIC")
        put(style.slot_entry("INTERIOR_CIVIC", "bookshelf", "minecraft:bookshelf"),
            "INTERIOR_CIVIC", n=2)
        put(crafting, "INTERIOR_WORK")
    elif kind == "town_foyer":
        put(_bell_state(style, "{facing}", "floor"), "INTERIOR_CIVIC")
        put(style.slot_entry("INTERIOR_CIVIC", "cauldron", "minecraft:cauldron"),
            "INTERIOR_CIVIC")
        put_wall_item("HERALDRY", "_wall_banner")
    elif kind == "stable":
        for x in range(zone.x0, zone.x1 + 1):
            for z in range(zone.z0, zone.z1 + 1):
                grid.set((x, fy - 1, z), "minecraft:hay_block[axis=y]",
                         ["INTERIOR", "GROUND"], p, None)
        gate_x = (zone.x0 + zone.x1) // 2
        gate = f"minecraft:{_species(style.primary('FRAME_WOOD'))}_fence_gate[facing=north,in_wall=false,open=false,powered=false]"
        if grid.set((gate_x, fy, vol.z0), gate, ["INTERIOR", "DETAIL"], p, "DETAIL_WOOD"):
            placed += 1
        # Keep stable dry by default: old behavior left an optional water trough.
        # If a trough is desired again, re-enable this block behind a feature flag.
    # ceiling lantern for living-ish zones
    if kind in ("living", "work", "forge", "tavern_hall", "town_chamber"):
        story_wall_h = vol.meta.get("story_wall_h", vol.meta.get("wall_h", 3))
        cy = min(fy + story_wall_h - 1, vol.meta.get("foundation_h", 1) + vol.meta.get("wall_h", 3) - 1)
        cpos = ((zone.x0 + zone.x1) // 2, cy, (zone.z0 + zone.z1) // 2)
        if grid.is_empty(cpos):
            grid.set(cpos, lantern_state(style, hanging=True),
                     ["INTERIOR", "DETAIL"], p, "LIGHTING")
    return placed

def exterior_decoration_patch(grid: BlockGrid, style: Style, rng: random.Random,
                              node: Node) -> bool:
    """Place one decoration motif on empty ground; returns success."""
    return place_motif(node.meta["motif"], grid, style, rng, node)


def _woodpile_motif(grid: BlockGrid, style: Style, rng: random.Random,
                    node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, z0 = node.x0, node.z0
    ok = False
    log = log_state(style, "x")
    for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (0, 0, 1),
                       (1, 0, 1), (0, 1, 0), (1, 1, 0)):
        pos = (x0 + dx, dy, z0 + dz)
        if grid.is_empty(pos) and (dy == 0 or not grid.is_empty((pos[0], 0, pos[2]))):
            ok |= grid.set(pos, log, ["DETAIL"], p, "FRAME_WOOD")
    return ok


def _barrel_cluster_motif(grid: BlockGrid, style: Style, rng: random.Random,
                          node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, z0 = node.x0, node.z0
    ok = False
    barrel = style.slot_entry("INTERIOR_STORAGE", "barrel")
    n = rng.choice([2, 3])
    offsets = [(0, 0, 0), (1, 0, 0), (0, 0, 1), (0, 1, 0)][:n]
    for dx, dy, dz in offsets:
        pos = (x0 + dx, dy, z0 + dz)
        if grid.is_empty(pos) and (dy == 0 or not grid.is_empty((pos[0], 0, pos[2]))):
            facing = "up" if dy == 0 else "up"
            ok |= grid.set(pos, f"{barrel}[facing={facing},open=false]",
                           ["DETAIL"], p, "INTERIOR_STORAGE")
    return ok


def _fence_patch_motif(grid: BlockGrid, style: Style, rng: random.Random,
                       node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, z0 = node.x0, node.z0
    ok = False
    for i, (dx, dz, conn) in enumerate(
            [(0, 0, ("east",)), (1, 0, ("east", "west")), (2, 0, ("west",))]):
        pos = (x0 + dx, 0, z0 + dz)
        if grid.is_empty(pos):
            ok |= grid.set(pos, fence_state(style, conn), ["DETAIL"], p,
                           "DETAIL_WOOD")
    return ok


def _lantern_post_motif(grid: BlockGrid, style: Style, rng: random.Random,
                        node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, z0 = node.x0, node.z0
    if grid.is_empty((x0, 0, z0)) and grid.is_empty((x0, 1, z0)):
        grid.set((x0, 0, z0), fence_state(style), ["DETAIL"], p, "DETAIL_WOOD")
        grid.set((x0, 1, z0), fence_state(style), ["DETAIL"], p, "DETAIL_WOOD")
        return grid.set((x0, 2, z0), lantern_state(style, hanging=False),
                        ["DETAIL"], p, "LIGHTING")
    return False


def _small_path_patch_motif(grid: BlockGrid, style: Style, rng: random.Random,
                            node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, z0 = node.x0, node.z0
    ok = False
    for dx in range(2):
        for dz in range(2):
            pos = (x0 + dx, 0, z0 + dz)
            if grid.is_empty(pos):
                ok |= grid.set(pos, rng.choice(style.material_slots["GROUND_PATH"]),
                               ["DETAIL", "GROUND"], p, "GROUND_PATH")
    return ok


def _ground_patch_motif(grid: BlockGrid, style: Style, rng: random.Random,
                        node: Node, related: Optional[Node] = None) -> bool:
    ground_patch(grid, style, rng, node)
    return True


def _moon_gate_motif(grid: BlockGrid, style: Style, rng: random.Random,
                     node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["OPENING"]
    frame = style.slot_entry("DETAIL_WOOD", "_trapdoor", style.primary("BASE_STONE"))
    wall = node.meta.get("wall", "front")
    radius = int(node.meta.get("radius", 2))
    cy = node.meta.get("y", 2)
    if related is not None:
        center = node.meta.get("along")
        if center is None:
            center = (wall_info(related, wall)[2][0] + wall_info(related, wall)[2][1]) // 2
        for dx in range(-radius, radius + 1):
            for dy in range(-radius, radius + 1):
                dist = dx * dx + dy * dy
                pos = wall_pos(related, wall, center + dx, cy + dy)
                if dist <= radius * radius:
                    grid.set(pos, AIR, ["OPENING", "AIR_CARVE", "PROTECTED"], p, force=True)
                elif dist <= (radius + 1) * (radius + 1):
                    grid.set(pos, frame, ["DETAIL", "OPENING"], p, "DETAIL_WOOD")
        return True
    x0, y0, z0 = node.x0, node.y0, node.z0
    ok = False
    for dx in range(-radius - 1, radius + 2):
        for dy in range(-radius - 1, radius + 2):
            dist = dx * dx + dy * dy
            pos = (x0 + radius + 1 + dx, y0 + radius + 1 + dy, z0)
            if dist <= radius * radius:
                grid.set(pos, AIR, ["OPENING", "AIR_CARVE", "PROTECTED"], p, force=True)
            elif dist <= (radius + 1) * (radius + 1):
                ok |= grid.set(pos, frame, ["DETAIL", "OPENING"], p, "DETAIL_WOOD")
    return ok


def _spirit_array_motif(grid: BlockGrid, style: Style, rng: random.Random,
                        node: Node, related: Optional[Node] = None) -> bool:
    crystal = (
        style.optional_slot_entry("SPIRIT_CRYSTAL", "amethyst")
        or style.optional_slot_entry("SPIRIT_CRYSTAL", "quartz")
    )
    metal = style.optional_slot_entry("RITUAL_METAL", "copper", crystal)
    anchor = style.optional_slot_entry("RITUAL_ANCHOR", ":", crystal)
    if not crystal:
        return False
    p = PRIORITY["DETAIL"]
    cx, cy, cz = node.x0 + 2, node.y0, node.z0 + 2
    ok = False
    glyph = {
        (0, 0), (1, 0), (-1, 0), (0, 1), (0, -1),
        (2, 0), (-2, 0), (0, 2), (0, -2),
        (1, 1), (1, -1), (-1, 1), (-1, -1),
    }
    for dx, dz in glyph:
        if (dx, dz) == (0, 0):
            state = anchor
            slot = "RITUAL_ANCHOR"
        elif abs(dx) == abs(dz):
            state = crystal
            slot = "SPIRIT_CRYSTAL"
        else:
            state = metal
            slot = "RITUAL_METAL"
        ok |= grid.set((cx + dx, cy, cz + dz), state,
                       ["DETAIL", "GROUND"], p, slot)
    return ok


def _incense_altar_motif(grid: BlockGrid, style: Style, rng: random.Random,
                         node: Node, related: Optional[Node] = None) -> bool:
    ritual = style.optional_slot_entry(
        "RITUAL_ANCHOR", ":",
        style.optional_slot_entry("RITUAL_METAL", "copper"))
    if not ritual:
        return False
    p = PRIORITY["DETAIL"]
    base = style.primary("BASE_STONE")
    detail = (
        style.optional_slot_entry("DETAIL_WOOD", "_trapdoor")
        or style.slot_entry("DETAIL_WOOD", "_slab")
    )
    x0, y0, z0 = node.x0, node.y0, node.z0
    ok = False
    for dx in range(3):
        ok |= grid.set((x0 + dx, y0, z0), base, ["DETAIL"], p, "BASE_STONE")
    ok |= grid.set((x0 + 1, y0 + 1, z0), ritual, ["DETAIL"], p, "RITUAL_ANCHOR")
    for dx in (0, 2):
        ok |= grid.set((x0 + dx, y0 + 1, z0), detail, ["DETAIL"], p, "DETAIL_WOOD")
    return ok


def _market_stall_motif(grid: BlockGrid, style: Style, rng: random.Random,
                        node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, y0, z0 = node.x0, node.y0, node.z0
    facing = str(node.meta.get("facing", "south"))
    counter = _optional_slot_block(
        style, "MARKET_FITTINGS", ("cabinet", "counter", "barrel"))
    display = _optional_slot_block(
        style, "MARKET_FITTINGS", ("crate", "basket", "jar", "rack", "shelf"),
        counter)
    accent = _optional_slot_block(
        style, "MARKET_FITTINGS", ("cutting_board", "skillet", "stove", "holder"),
        display)
    canopy, canopy_slot = canopy_roof_state(style, facing)
    post = fence_state(style)
    canopy_support = style.slot_entry("ROOF_DARK", "_planks",
                                      style.primary("BASE_STONE"))
    support_dx, support_dz = DIR_VEC[OPPOSITE[facing]]
    ok = False
    for dx in (0, 1):
        if counter:
            ok |= grid.set((x0 + dx, y0, z0), counter, ["DETAIL"], p,
                           "MARKET_FITTINGS")
        goods = display if dx == 0 else accent
        if goods:
            ok |= grid.set((x0 + dx, y0 + 1, z0), goods,
                           ["DETAIL"], p, "MARKET_FITTINGS")
        ok |= grid.set((x0 + dx, y0, z0 + 1), post, ["DETAIL"], p,
                       "DETAIL_WOOD")
        ok |= grid.set((x0 + dx, y0 + 1, z0 + 1), post, ["DETAIL"], p,
                       "DETAIL_WOOD")
    canopy_positions: List[Pos]
    if facing == "north":
        canopy_positions = [(x0, y0 + 2, z0), (x0 + 1, y0 + 2, z0)]
    elif facing == "south":
        canopy_positions = [(x0, y0 + 2, z0 + 1), (x0 + 1, y0 + 2, z0 + 1)]
    elif facing == "west":
        canopy_positions = [(x0, y0 + 2, z0), (x0, y0 + 2, z0 + 1)]
    else:
        canopy_positions = [(x0 + 1, y0 + 2, z0), (x0 + 1, y0 + 2, z0 + 1)]
    support_positions = {
        (x + support_dx, y, z + support_dz) for x, y, z in canopy_positions
    }
    for sx, sy, sz in sorted(support_positions):
        if grid.is_empty((sx, sy - 2, sz)):
            ok |= grid.set((sx, sy - 2, sz), post, ["DETAIL"], p,
                           "DETAIL_WOOD")
        if grid.is_empty((sx, sy - 1, sz)):
            ok |= grid.set((sx, sy - 1, sz), post, ["DETAIL"], p,
                           "DETAIL_WOOD")
        ok |= grid.set((sx, sy, sz), canopy_support, ["DETAIL"], p,
                       "ROOF_DARK")
    for pos in canopy_positions:
        ok |= grid.set(pos, canopy, ["DETAIL", "ROOF"], p, canopy_slot)
    return ok


def _sect_gate_paifang_motif(grid: BlockGrid, style: Style, rng: random.Random,
                             node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, y0, z0 = node.x0, node.y0, node.z0
    facing = str(node.meta.get("facing", "south"))
    trim = _optional_slot_block(
        style, "MARKET_FITTINGS",
        ("pedestal", "rack_a", "rack", "basket", "barrel", "shelf", "holder"))
    post = log_state(style, "y")
    beam = slab_state(style, "bottom", "DETAIL_WOOD")
    backing = style.slot_entry("DETAIL_WOOD", "_planks",
                               style.primary("FRAME_WOOD"))
    support_dx, support_dz = DIR_VEC[OPPOSITE[facing]]
    ok = False
    for dx in (0, 4):
        for dy in range(4):
            ok |= grid.set((x0 + dx, y0 + dy, z0), post,
                           ["DETAIL"], p, "FRAME_WOOD")
    for dx in range(5):
        ok |= grid.set((x0 + dx, y0 + 4, z0), beam,
                       ["DETAIL"], p, "DETAIL_WOOD")
    for dx in (1, 2, 3):
        for dy in (1, 2, 3):
            support_pos = (x0 + dx + support_dx, y0 + dy, z0 + support_dz)
            if grid.is_empty(support_pos):
                ok |= grid.set(support_pos, backing, ["DETAIL"], p,
                               "DETAIL_WOOD")
    binding = plaque_bindings.binding_for("sect_gate", rng)
    if binding:
        anchor = (x0 + 2, y0 + 2, z0)
        if binding.mount == "hanging":
            ok |= place_hanging_plaque(grid, style, rng, related, anchor, facing, binding)
        else:
            ok |= _place_plaque(grid, anchor, facing, binding, chains=False)
    else:
        warnings.warn("sect_gate_paifang motif skipped central tablet: no plaque binding for sect_gate")
    if trim:
        for dx in (1, 3):
            ok |= grid.set((x0 + dx, y0 + 1, z0), trim,
                           ["DETAIL"], p, "MARKET_FITTINGS")
    return ok


def _cloud_rail_motif(grid: BlockGrid, style: Style, rng: random.Random,
                      node: Node, related: Optional[Node] = None) -> bool:
    p = PRIORITY["DETAIL"]
    x0, y0, z0 = node.x0, node.y0, node.z0
    ok = False
    for dx in range(5):
        conn = []
        if dx > 0:
            conn.append("west")
        if dx < 4:
            conn.append("east")
        ok |= grid.set((x0 + dx, y0, z0), fence_state(style, conn),
                       ["DETAIL"], p, "DETAIL_WOOD")
        if dx % 2 == 0:
            ok |= grid.set((x0 + dx, y0 + 1, z0),
                           slab_state(style, "bottom", "DETAIL_WOOD"),
                           ["DETAIL"], p, "DETAIL_WOOD")
    return ok


def _small_porch_motif(grid: BlockGrid, style: Style, rng: random.Random,
                       node: Node, related: Optional[Node] = None) -> bool:
    if related is None:
        raise ValueError(f"small_porch motif for node {node.id!r} requires parent volume")
    porch(grid, style, rng, node, related)
    return True


def _timber_bracket_motif(grid: BlockGrid, style: Style, rng: random.Random,
                          node: Node, related: Optional[Node] = None) -> bool:
    chinese_timber_brackets(grid, style, rng, node)
    return True


def _already_placed_motif(grid: BlockGrid, style: Style, rng: random.Random,
                          node: Node, related: Optional[Node] = None) -> bool:
    return True


def ground_patch(grid: BlockGrid, style: Style, rng: random.Random,
                 node: Node) -> None:
    """path_patch / courtyard_patch: ground material mix at y=0."""
    p = PRIORITY["DETAIL"]
    door_x = node.meta.get("door_x")
    step_z = node.meta.get("step_z")
    step_y = node.meta.get("step_y", 0)
    for x in range(node.x0, node.x1 + 1):
        for z in range(node.z0, node.z1 + 1):
            pos = (x, 0, z)
            if door_x is not None and step_z is not None:
                if not (x == door_x and z == step_z):
                    grid.set((x, step_y, z), AIR,
                             ["AIR_CARVE", "PROTECTED"], p, force=True)
                pos = (x, step_y - 1, z)
            if grid.is_empty(pos):
                grid.set(pos, rng.choice(style.material_slots["GROUND_PATH"]),
                         ["DETAIL", "GROUND"], p, "GROUND_PATH")


def open_shed(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
              high_side: str) -> None:
    """Open work shed: stone work floor pad (posts are raised to the roof
    underside by roof_cleanup_pass once the lean-to roof exists)."""
    p = PRIORITY["STRUCTURE"]
    stone = style.slot_entry("BASE_STONE", "brick", style.primary("BASE_STONE"))
    for x in range(vol.x0, vol.x1 + 1):
        for z in range(vol.z0, vol.z1 + 1):
            grid.set((x, 0, z), stone if rng.random() < 0.7
                     else style.primary("BASE_STONE"),
                     ["STRUCTURE", "GROUND"], p, "BASE_STONE")


register_roof("gable_roof", _gable_roof_handler)
register_roof("cross_gable_roof", _cross_gable_roof_handler)
register_roof("lean_to_roof", _lean_to_roof_handler)
register_roof("sweeping_eave_roof", _sweeping_eave_roof_handler)
register_roof("hip_roof", _hip_roof_handler)
register_roof("pyramidal_roof", _pyramidal_roof_handler)
register_roof("tiered_eave_roof", _tiered_eave_roof_handler)
register_roof("pagoda", _pagoda_roof_handler)
register_roof("pavilion", _pavilion_roof_handler)
register_roof("bell_drum_tower", _bell_drum_tower_roof_handler)
register_roof("chinese_flush_gable", _chinese_flush_gable_handler)
register_roof("chinese_overhang_gable", _chinese_overhang_gable_handler)
register_roof("chinese_half_hip", _chinese_half_hip_handler)
register_roof("chinese_round_ridge", _chinese_round_ridge_handler)

register_motif("woodpile", _woodpile_motif)
register_motif("barrel_cluster", _barrel_cluster_motif)
register_motif("fence_patch", _fence_patch_motif)
register_motif("lantern_post", _lantern_post_motif)
register_motif("small_path_patch", _small_path_patch_motif)
register_motif("small_porch", _small_porch_motif)
register_motif("ground_patch", _ground_patch_motif)
register_motif("stone_path", _ground_patch_motif)
register_motif("courtyard", _ground_patch_motif)
register_motif("timber_bracket", _timber_bracket_motif)
register_motif("moon_gate", _moon_gate_motif)
register_motif("spirit_array", _spirit_array_motif)
register_motif("incense_altar", _incense_altar_motif)
register_motif("cloud_rail", _cloud_rail_motif)
register_motif("market_stall", _market_stall_motif)
register_motif("sect_gate_paifang", _sect_gate_paifang_motif)
for _motif in ("side_chimney", "courtyard_gate", "water_feature", "planting_bed"):
    register_motif(_motif, _already_placed_motif)
