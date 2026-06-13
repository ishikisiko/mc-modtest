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
from typing import Dict, Iterable, List, Optional, Tuple

from .grid import AIR, BlockGrid, PRIORITY
from .massing import INWARD_FACING, OUTWARD_FACING, Node, WALL_OUTWARD
from .style import Style

Pos = Tuple[int, int, int]

DIR_VEC = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west",
            "front": "back", "back": "front"}


def _species(state: str) -> str:
    """'minecraft:oak_log' -> 'oak'."""
    name = state.split(":", 1)[1].split("[", 1)[0]
    for suffix in ("_log", "_planks", "_wood"):
        name = name.replace(suffix, "")
    return name.replace("stripped_", "")


def stair_state(style: Style, facing: str, half: str = "bottom") -> str:
    base = style.slot_entry("ROOF_DARK", "_stairs")
    return f"{base}[facing={facing},half={half},shape=straight,waterlogged=false]"


def slab_state(style: Style, kind: str = "bottom", slot: str = "ROOF_DARK") -> str:
    base = style.slot_entry(slot, "_slab")
    return f"{base}[type={kind},waterlogged=false]"


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
                  "timber_frame_wall": 1}[wall_type]
    stone_rows = min(stone_rows, vol.meta["wall_h"] - 2)
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
    if wall_type == "timber_frame_wall" and vol.meta["wall_h"] >= 4:
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
    grid.set(wall_pos(vol, wall, along, fh - 1, depth_offset=1),
             stair_state(style, inward), ["DETAIL", "PROTECTED"], p)
    for y in (fh, fh + 1):
        grid.set(wall_pos(vol, wall, along, y, depth_offset=1), AIR,
                 ["AIR_CARVE", "PROTECTED"], p)
    return {"wall": wall, "along": along, "y": fh,
            "front": wall_pos(vol, wall, along, fh, depth_offset=1)}


def window_kit(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
               wall: str, along: int, width: int = 1, opening_style: str =
               "window_with_trapdoor_frame", glass: str = "minecraft:glass") -> int:
    """Window with frame/sill; returns number of glass cells placed."""
    fh = vol.meta["foundation_h"]
    wall_top = fh + vol.meta["wall_h"] - 1
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


# ---------------------------------------------------------------------------
# roofs
# ---------------------------------------------------------------------------

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
    gable_state = style.alternates("WALL_MAIN")[0] if style.alternates("WALL_MAIN") \
        else style.primary("WALL_MAIN")
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

    # gable triangles, flush with the end walls
    if ridge_axis == "x":
        lo2, hi2 = vol.z0, vol.z1
    else:
        lo2, hi2 = vol.x0, vol.x1
    yy = wall_top + 1
    while lo2 <= hi2:
        for wallname in gable_walls:
            axisname, fixed, _, _ = wall_info(vol, wallname)
            for c in range(lo2, hi2 + 1):
                pos = (fixed, yy, c) if ridge_axis == "x" else (c, yy, fixed)
                # never punch through the roof slope rows themselves
                if grid.is_empty(pos):
                    state = gable_state if rng.random() < 0.6 else planks
                    if grid.set(pos, state, ["FACADE", "ROOF"], p, "WALL_MAIN"):
                        gable_cells.append(pos)
        lo2 += 1
        hi2 -= 1
        yy += 1
        if yy > ridge_y:
            break
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
    # corner posts on the outer edge
    for px in (node.x0, node.x1):
        for y in range(0, roof_y):
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
    # porch floor pad
    for x in range(node.x0, node.x1 + 1):
        for z in range(node.z0, node.z1 + 1):
            pos = (x, 0, z)
            if grid.is_empty(pos):
                grid.set(pos, rng.choice(style.material_slots["GROUND_PATH"]),
                         ["DETAIL", "GROUND"], p, "GROUND_PATH")


def interior_zone(grid: BlockGrid, style: Style, rng: random.Random, vol: Node,
                  zone: Node, door_cells: List[Pos]) -> int:
    """Furnish one interior zone; returns count of function blocks placed."""
    kind = zone.meta["kind"]
    fh = vol.meta.get("foundation_h", 1)
    p = PRIORITY["INTERIOR"]
    placed = 0

    def spots_along_walls() -> List[Tuple[Pos, str]]:
        out = []
        for x in range(zone.x0, zone.x1 + 1):
            for z in range(zone.z0, zone.z1 + 1):
                pos = (x, fh, z)
                if not grid.is_empty(pos):
                    continue
                if any(abs(pos[0] - d[0]) + abs(pos[2] - d[2]) <= 1 for d in door_cells):
                    continue
                for wname, (ox, oz) in WALL_OUTWARD.items():
                    npos = (x + ox, fh, z + oz)
                    ncell = grid.get(npos)
                    if ncell and not ncell.is_air and "STRUCTURE" in ncell.tags:
                        out.append((pos, INWARD_FACING[wname]))
                        break
        rng.shuffle(out)
        return out

    def put(state: str, slot: str, n: int = 1) -> int:
        nonlocal placed
        done = 0
        for pos, facing in spots_along_walls():
            if done >= n:
                break
            st = state.replace("{facing}", facing)
            if grid.set(pos, st, ["INTERIOR", "PROTECTED"], p, slot):
                placed += 1
                done += 1
        return done

    crafting = style.slot_entry("INTERIOR_WORK", "crafting")
    furnace = style.slot_entry("INTERIOR_WORK", "furnace")
    smithing = style.slot_entry("INTERIOR_WORK", "smithing")
    barrel = style.slot_entry("INTERIOR_STORAGE", "barrel")

    furnace_tpl = f"{furnace}[facing={{facing}},lit=false]"
    if kind == "living":
        put(crafting, "INTERIOR_WORK")
        put(furnace_tpl, "INTERIOR_WORK")
        put(f"{barrel}[facing=up,open=false]", "INTERIOR_STORAGE")
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
    # ceiling lantern for living-ish zones
    if kind in ("living", "work", "forge"):
        cy = fh + vol.meta.get("wall_h", 3) - 1
        cpos = ((zone.x0 + zone.x1) // 2, cy, (zone.z0 + zone.z1) // 2)
        if grid.is_empty(cpos):
            grid.set(cpos, lantern_state(style, hanging=True),
                     ["INTERIOR", "DETAIL"], p, "LIGHTING")
    return placed


def exterior_decoration_patch(grid: BlockGrid, style: Style, rng: random.Random,
                              node: Node) -> bool:
    """Place one decoration motif on empty ground; returns success."""
    motif = node.meta["motif"]
    p = PRIORITY["DETAIL"]
    x0, z0 = node.x0, node.z0
    ok = False
    if motif == "woodpile":
        log = log_state(style, "x")
        for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (0, 0, 1), (1, 0, 1), (0, 1, 0), (1, 1, 0)):
            pos = (x0 + dx, dy, z0 + dz)
            if grid.is_empty(pos) and (dy == 0 or not grid.is_empty((pos[0], 0, pos[2]))):
                ok |= grid.set(pos, log, ["DETAIL"], p, "FRAME_WOOD")
    elif motif == "barrel_cluster":
        barrel = style.slot_entry("INTERIOR_STORAGE", "barrel")
        n = rng.choice([2, 3])
        offsets = [(0, 0, 0), (1, 0, 0), (0, 0, 1), (0, 1, 0)][:n]
        for dx, dy, dz in offsets:
            pos = (x0 + dx, dy, z0 + dz)
            if grid.is_empty(pos) and (dy == 0 or not grid.is_empty((pos[0], 0, pos[2]))):
                facing = "up" if dy == 0 else "up"
                ok |= grid.set(pos, f"{barrel}[facing={facing},open=false]",
                               ["DETAIL"], p, "INTERIOR_STORAGE")
    elif motif == "fence_patch":
        for i, (dx, dz, conn) in enumerate(
                [(0, 0, ("east",)), (1, 0, ("east", "west")), (2, 0, ("west",))]):
            pos = (x0 + dx, 0, z0 + dz)
            if grid.is_empty(pos):
                ok |= grid.set(pos, fence_state(style, conn), ["DETAIL"], p,
                               "DETAIL_WOOD")
    elif motif == "lantern_post":
        if grid.is_empty((x0, 0, z0)) and grid.is_empty((x0, 1, z0)):
            grid.set((x0, 0, z0), fence_state(style), ["DETAIL"], p, "DETAIL_WOOD")
            grid.set((x0, 1, z0), fence_state(style), ["DETAIL"], p, "DETAIL_WOOD")
            ok = grid.set((x0, 2, z0), lantern_state(style, hanging=False),
                          ["DETAIL"], p, "LIGHTING")
    elif motif == "small_path_patch":
        for dx in range(2):
            for dz in range(2):
                pos = (x0 + dx, 0, z0 + dz)
                if grid.is_empty(pos):
                    ok |= grid.set(pos, rng.choice(style.material_slots["GROUND_PATH"]),
                                   ["DETAIL", "GROUND"], p, "GROUND_PATH")
    return ok


def ground_patch(grid: BlockGrid, style: Style, rng: random.Random,
                 node: Node) -> None:
    """path_patch / courtyard_patch: ground material mix at y=0."""
    p = PRIORITY["DETAIL"]
    for x in range(node.x0, node.x1 + 1):
        for z in range(node.z0, node.z1 + 1):
            pos = (x, 0, z)
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
