"""Pass pipeline + protection mechanism.

Pass order (priorities in grid.PRIORITY):
    massing_pass -> structure_pass -> mezzanine_floor_pass ->
    floor_slab_pass -> stair_pass -> facade_detail_pass -> roof_pass ->
    roof_cleanup_pass ->
    material_variation_pass -> interior_furnishing_pass ->
    exterior_decoration_pass -> quality_check_pass -> resource_export_pass

PROTECTED cells (doorway, windows, roof ridge, chimney core, porch slots)
are never overwritten by later normal passes; clear_inside skips INTERIOR
and PROTECTED cells; detail ops only place on empty/air cells.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

from . import ops
from .archetypes import build_massing, reserved_stair_footprint
from .facade import plan_building_facades
from .grid import AIR, BlockGrid, PRIORITY
from .massing import MassingGraph, Node
from .plaque_bindings import binding_for
from .style import Style

Pos = Tuple[int, int, int]


@dataclass
class BuildContext:
    style: Style
    archetype: str
    scale_tier: str
    seed: int
    rng: random.Random
    group_id: Optional[str] = None
    importance_tier: Optional[int] = None
    graph: Optional[MassingGraph] = None
    grid: BlockGrid = field(default_factory=BlockGrid)
    door_info: Optional[dict] = None
    roof_info: List[dict] = field(default_factory=list)
    wall_plans: list = field(default_factory=list)
    window_cells: int = 0
    interior_function_blocks: int = 0
    decoration_motifs: List[str] = field(default_factory=list)
    passes_run: List[str] = field(default_factory=list)


def massing_pass(ctx: BuildContext) -> None:
    ctx.graph = build_massing(ctx.archetype, ctx.style, ctx.rng, ctx.scale_tier,
                              ctx.group_id, ctx.importance_tier)
    ctx.passes_run.append("massing_pass")


def _carve_connection(ctx: BuildContext, vol: Node) -> None:
    """Protected 1x2 opening through the shared wall(s) to the parent.

    Open sheds (no wall) are skipped. The opening column is nudged off the
    parent wall's post / window / door columns (consulting the facade plan),
    and any post column the opening must cross is re-sealed beside it.
    """
    if vol.meta.get("open"):
        return  # open sheds have no wall to carve
    grid, graph = ctx.grid, ctx.graph
    parent = graph.get(vol.attach_to)
    fh = parent.meta["foundation_h"]
    parent_wall = vol.side  # the parent wall that faces this attached volume
    _, _, (pa0, pa1), _ = ops.wall_info(parent, parent_wall)
    if parent_wall in ("front", "back"):
        va0, va1 = vol.x0, vol.x1
    else:
        va0, va1 = vol.z0, vol.z1
    lo = max(pa0, va0)
    hi = min(pa1, va1)
    if hi < lo:
        return

    # Columns the parent facade plan already reserves (posts, windows, door).
    reserved = {pa0, pa1}  # corners are always structural
    door_along = None
    for plan in ctx.wall_plans:
        if plan.volume_id == parent.id and plan.wall == parent_wall:
            reserved.update(plan.post_positions)
            reserved.update(w for w, _ in plan.windows)
            if plan.door_along is not None:
                door_along = plan.door_along
            break
    if door_along is not None:
        reserved.update((door_along - 1, door_along, door_along + 1))

    # Pick the shared-span midpoint, then nudge to the nearest conflict-free
    # column that keeps the opening clear of posts/windows/door.
    mid = (lo + hi) // 2
    chosen = None
    for delta in (0, 1, -1, 2, -2, 3, -3):
        cand = mid + delta
        if lo < cand < hi and cand not in reserved:
            chosen = cand
            break
    if chosen is None:
        chosen = mid  # span too tight; re-seal the post we must cross below
    crossed_post = chosen in reserved

    if parent_wall in ("west", "east"):
        planes = (parent.x0 if parent_wall == "west" else parent.x1,
                  (vol.x1 if parent_wall == "west" else vol.x0))
        cells = [(px, y, chosen) for px in set(planes) for y in (fh, fh + 1)]
        post_axis = "z"
    else:
        planes = (parent.z1 if parent_wall == "back" else parent.z0,
                  (vol.z0 if parent_wall == "back" else vol.z1))
        cells = [(chosen, y, pz) for pz in set(planes) for y in (fh, fh + 1)]
        post_axis = "x"
    for pos in cells:
        grid.set(pos, AIR, ["OPENING", "AIR_CARVE", "PROTECTED"],
                 PRIORITY["OPENING"], force=True)

    # Re-seal a post column the opening had to cross, placing a fresh timber
    # post beside the opening so the facade rhythm is not gutted.
    if crossed_post:
        style = ctx.style
        log = ops.log_state(style, "y")
        seal_cols = [c for c in (chosen - 1, chosen + 1)
                     if lo < c < hi and c not in reserved]
        for px in set(planes):
            for c in seal_cols[:1]:
                pos = (px, fh, chosen) if parent_wall in ("west", "east") \
                    else (chosen, fh, px)
                # place replacement post on the chosen side column
                rep = (c, fh, pos[2]) if parent_wall in ("west", "east") \
                    else (pos[0], fh, c)
                wall_top = parent.meta["foundation_h"] + parent.meta["wall_h"] - 1
                for y in range(fh, wall_top + 1):
                    rp = (rep[0], y, rep[2])
                    if grid.get(rp) is None or grid.get(rp).is_air:
                        grid.set(rp, log, ["FACADE", "STRUCTURE"],
                                 PRIORITY["FACADE"], "FRAME_WOOD")


def structure_pass(ctx: BuildContext) -> None:
    grid, style, graph = ctx.grid, ctx.style, ctx.graph
    for vol in graph.volumes():
        if vol.meta.get("open"):
            ops.open_shed(grid, style, ctx.rng, vol,
                          high_side=vol.meta["roof"].get("attached_side", "back"))
        else:
            ops.hollow_box(grid, style, vol)
            ops.clear_inside(grid, vol)
    for node in graph.by_type("chimney"):
        ops.chimney(grid, style, node)
    for node in graph.by_type("platform"):
        ops.raised_platform(grid, style, node)
    ctx.passes_run.append("structure_pass")


def mezzanine_floor_pass(ctx: BuildContext) -> None:
    graph = ctx.graph
    for vol in graph.volumes():
        mezzanine = vol.meta.get("mezzanine")
        if mezzanine:
            ops.mezzanine_floor(ctx.grid, ctx.style, vol, mezzanine)
    ctx.passes_run.append("mezzanine_floor_pass")


def floor_slab_pass(ctx: BuildContext) -> None:
    graph = ctx.graph
    for vol in graph.volumes():
        stories = vol.meta.get("stories", 1)
        if stories <= 1:
            continue
        fh = vol.meta["foundation_h"]
        story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
        opening = reserved_stair_footprint(graph, vol.id)
        mezzanine_story = vol.meta.get("mezzanine_story")
        mezzanine_stories = set(vol.meta.get("mezzanine_stories", []))
        if mezzanine_story is True:
            mezzanine_stories.add(1)
        elif isinstance(mezzanine_story, int):
            mezzanine_stories.add(mezzanine_story)
        for story in range(1, stories):
            if story in mezzanine_stories:
                continue
            ops.floor_slab(ctx.grid, ctx.style, vol, fh + story * story_wall_h,
                           opening)
    ctx.passes_run.append("floor_slab_pass")


def stair_pass(ctx: BuildContext) -> None:
    graph = ctx.graph
    for vol in graph.volumes():
        stories = vol.meta.get("stories", 1)
        if stories <= 1:
            continue
        opening = reserved_stair_footprint(graph, vol.id)
        if opening:
            ops.stairwell(ctx.grid, ctx.style, vol, opening)
    ctx.passes_run.append("stair_pass")


def facade_detail_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    ctx.wall_plans = plan_building_facades(graph, style, rng)
    framed = set()
    for plan in ctx.wall_plans:
        key = (plan.volume_id, plan.wall)
        if key in framed:
            continue
        framed.add(key)
        vol = graph.get(plan.volume_id)
        ops.wall_frame(grid, style, rng, vol, plan.wall, plan.post_positions,
                       vol.meta.get("wall_type", "mixed_stone_wood_wall"))
    door = graph.meta["door"]
    door_vol = graph.get(door["volume"])
    ctx.door_info = ops.doorway(grid, style, rng, door_vol, door["wall"], door["x"])
    if door_vol.meta.get("storefront"):
        ops.storefront(grid, style, rng, door_vol, door["wall"], door["x"],
                       door_vol.meta["storefront"])
    marker_y = min(
        door_vol.meta["foundation_h"] + 3,
        door_vol.meta["foundation_h"] + door_vol.meta["wall_h"] - 1,
    )
    if door_vol.meta.get("entry_signage"):
        binding = binding_for(ctx.archetype, rng)
        if binding:
            if binding.mount == "hanging":
                anchor = ops.plaque_wall_anchor(door_vol, door["wall"], door["x"],
                                                marker_y, binding)
                ops.place_hanging_plaque(
                    grid, style, rng, door_vol, anchor,
                    ops.OUTWARD_FACING[door["wall"]], binding)
            else:
                ops.place_wall_plaque(grid, style, rng, door_vol, door["wall"],
                                      door["x"], marker_y, binding)
        else:
            ops.wall_hanging(grid, style, rng, door_vol, door["wall"], door["x"],
                             marker_y, "SIGNAGE", "_wall_sign", outside=True)
    if door_vol.meta.get("entry_heraldry"):
        ops.wall_hanging(grid, style, rng, door_vol, door["wall"], door["x"],
                         marker_y, "HERALDRY", "_wall_banner", outside=True)
    for plan in ctx.wall_plans:
        vol = graph.get(plan.volume_id)
        for along, ostyle in plan.windows:
            ctx.window_cells += ops.window_kit(grid, style, rng, vol, plan.wall,
                                               along, opening_style=ostyle,
                                               y_base=plan.y_base)
    ctx.passes_run.append("facade_detail_pass")


def connection_carve_pass(ctx: BuildContext) -> None:
    """Carve inter-volume connections after facades are planned.

    Runs after facade_detail_pass so the openings can be nudged off the
    parent wall's post / window / door columns.
    """
    for vol in ctx.graph.volumes():
        if not vol.attach_to:
            continue
        if vol.type in ("side_wing", "rear_shed", "tower_volume", "shed"):
            _carve_connection(ctx, vol)
    ctx.passes_run.append("connection_carve_pass")


def pagoda_shape_pass(ctx: BuildContext) -> None:
    for vol in ctx.graph.volumes():
        if vol.meta.get("story_insets"):
            ops.pagoda_story_insets(ctx.grid, ctx.style, vol)
    ctx.passes_run.append("pagoda_shape_pass")


def roof_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    main = graph.get("main")
    roofed = set()

    def apply_roof(vol: Node) -> None:
        roof = vol.meta.get("roof")
        if not roof:
            return
        info = ops.roof_handler(roof["type"])(grid, style, rng, vol, graph)
        ctx.roof_info.append(info)
        roofed.update(info.get("roofed_volume_ids", [vol.id]))

    apply_roof(main)
    for vol in graph.volumes():
        if vol.id in roofed:
            continue
        apply_roof(vol)
    ctx.passes_run.append("roof_pass")


def roof_cleanup_pass(ctx: BuildContext) -> None:
    """Re-seal gable cells lost to collisions; close lean-to side gaps."""
    grid, style, graph = ctx.grid, ctx.style, ctx.graph
    gable_state, gable_slot = ops.gable_infill(style)
    for info in ctx.roof_info:
        for pos in info["gable_cells"]:
            if grid.is_empty(pos):
                grid.set(pos, gable_state, ["FACADE", "ROOF"],
                         PRIORITY["ROOF"], gable_slot)
    # close any remaining vertical gap between a closed wall and the first
    # roof cell above it, including gable/cross-gable volumes.
    for vol in graph.volumes():
        if vol.meta.get("open"):
            continue
        fh = vol.meta["foundation_h"]
        wall_top = fh + vol.meta["wall_h"] - 1
        for x in range(vol.x0, vol.x1 + 1):
            for z in range(vol.z0, vol.z1 + 1):
                on_perimeter = (x in (vol.x0, vol.x1) or z in (vol.z0, vol.z1))
                if not on_perimeter:
                    continue
                roof_y = None
                for y in range(wall_top + 1, wall_top + 10):
                    cell = grid.get((x, y, z))
                    if cell and "ROOF" in cell.tags:
                        roof_y = y
                        break
                if roof_y is None:
                    continue
                for y in range(wall_top + 1, roof_y):
                    if grid.is_empty((x, y, z)):
                        grid.set((x, y, z), style.primary("WALL_MAIN"),
                                 ["FACADE", "STRUCTURE"], PRIORITY["ROOF"],
                                 "WALL_MAIN")
    # fill wall-top gaps under lean-to roofs of closed sheds
    for vol in graph.volumes():
        vroof = vol.meta.get("roof", {})
        if vroof.get("type") != "lean_to_roof" or vol.meta.get("open"):
            continue
        fh = vol.meta["foundation_h"]
        wall_top = fh + vol.meta["wall_h"] - 1
        for x in range(vol.x0, vol.x1 + 1):
            for z in range(vol.z0, vol.z1 + 1):
                on_perimeter = (x in (vol.x0, vol.x1) or z in (vol.z0, vol.z1))
                if not on_perimeter:
                    continue
                roof_y = None
                for y in range(wall_top + 1, wall_top + 8):
                    cell = grid.get((x, y, z))
                    if cell and "ROOF" in cell.tags:
                        roof_y = y
                        break
                if roof_y is None:
                    continue
                for y in range(wall_top + 1, roof_y):
                    if grid.is_empty((x, y, z)):
                        grid.set((x, y, z), style.primary("WALL_MAIN"),
                                 ["FACADE", "STRUCTURE"], PRIORITY["ROOF"],
                                 "WALL_MAIN")
    # raise open-shed corner posts up to the lean-to roof underside
    for vol in graph.volumes():
        if not vol.meta.get("open"):
            continue
        for cx in (vol.x0, vol.x1):
            for cz in (vol.z0, vol.z1):
                roof_y = None
                for y in range(1, vol.y1 + 8):
                    cell = grid.get((cx, y, cz))
                    if cell and "ROOF" in cell.tags:
                        roof_y = y
                        break
                if roof_y is None:
                    continue
                from .ops import log_state
                for y in range(1, roof_y):
                    grid.set((cx, y, cz), log_state(style, "y"),
                             ["STRUCTURE"], PRIORITY["STRUCTURE"], "FRAME_WOOD")
    ctx.passes_run.append("roof_cleanup_pass")


def material_variation_pass(ctx: BuildContext) -> None:
    """Speckle slot materials and break up any same-state flat run > max.

    The dominant side-wall noise sources (smithy furniture leak, dark-plank
    gable mix) are fixed at their source; the speckle is retained at full
    strength because it is also what keeps long plank runs under the flat-wall
    limit, and clamping it on side/back facades regresses `flat_wall`.
    """
    grid, style, rng = ctx.grid, ctx.style, ctx.rng
    max_flat = style.prop("max_flat_wall_width")
    # random speckle
    for pos, cell in list(grid.iter_cells()):
        if cell.protected or cell.slot not in style.variation_rate:
            continue
        if cell.state != style.primary(cell.slot):
            continue
        if rng.random() < style.variation_rate[cell.slot]:
            grid.replace_state(pos, rng.choice(style.alternates(cell.slot)))
    # deterministic run-breaking on facades
    (x0, y0, z0), (x1, y1, z1) = grid.bounds()
    for y in range(y0, y1 + 1):
        for axis in ("x", "z"):
            outer = range(z0, z1 + 1) if axis == "x" else range(x0, x1 + 1)
            inner = range(x0, x1 + 1) if axis == "x" else range(z0, z1 + 1)
            for o in outer:
                run_state, run_len = None, 0
                for i in inner:
                    pos = (i, y, o) if axis == "x" else (o, y, i)
                    cell = grid.get(pos)
                    variable = (cell is not None and not cell.protected and
                                cell.slot in style.variation_rate and
                                "FACADE" in cell.tags)
                    state = cell.state if cell else None
                    if variable and state == run_state:
                        run_len += 1
                        if run_len >= max_flat:
                            alts = [a for a in style.alternates(cell.slot)
                                    if a != state] or \
                                   ([style.primary(cell.slot)]
                                    if style.primary(cell.slot) != state else [])
                            if alts:
                                grid.replace_state(pos, rng.choice(alts))
                                run_state, run_len = None, 0
                    else:
                        run_state = state if variable else None
                        run_len = 1 if variable else 0
    ctx.passes_run.append("material_variation_pass")


def interior_furnishing_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    door_cells = [ctx.door_info["front"],
                  (ctx.door_info["front"][0], ctx.door_info["front"][1],
                   ctx.door_info["front"][2])] if ctx.door_info else []
    # also keep the cell just inside the door free
    if ctx.door_info:
        dx, dy, dz = ctx.door_info["front"]
        door = graph.meta["door"]
        vol = graph.get(door["volume"])
        inside = (door["x"], dy, vol.z0 + 1)
        door_cells.append(inside)
    for zone in graph.by_type("interior_zone"):
        vol = graph.get(zone.attach_to)
        ctx.interior_function_blocks += ops.interior_zone(
            grid, style, rng, vol, zone, door_cells)
    for vol in graph.by_type("tower_volume"):
        if vol.meta.get("belfry") and ops.belfry_bell(grid, style, vol):
            ctx.interior_function_blocks += 1
    ctx.passes_run.append("interior_furnishing_pass")


def exterior_decoration_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    if graph.meta.get("style_family") == "chinese_courtyard":
        for vol in graph.volumes():
            if vol.meta.get("open"):
                continue
            ops.place_motif("timber_bracket", grid, style, rng, vol)
        ctx.decoration_motifs.append("timber_bracket")
    for node in graph.by_type("porch"):
        main = graph.get(node.attach_to)
        if ops.place_motif("small_porch", grid, style, rng, node, main):
            ctx.decoration_motifs.append("small_porch")
    for node in graph.by_type("path_patch", "courtyard_patch"):
        ops.place_motif("ground_patch", grid, style, rng, node)
        ctx.decoration_motifs.append(
            "small_path_patch" if node.type == "path_patch" else "courtyard")
    for node in graph.by_type("decoration_patch"):
        if ops.exterior_decoration_patch(grid, style, rng, node):
            ctx.decoration_motifs.append(node.meta["motif"])
    for node in graph.by_type("mountain_gate"):
        ops.mountain_gate_detail(grid, style, rng, node, graph.get(node.attach_to))
    for node in graph.by_type("colonnade"):
        ops.colonnade(grid, style, rng, node, graph.get(node.attach_to))
    for node in graph.by_type("balustrade"):
        ops.balustrade(grid, style, node)
    for node in graph.by_type("alchemy_furnace"):
        ops.alchemy_furnace(grid, style, node)
    if graph.by_type("chimney"):
        if ops.place_motif("side_chimney", grid, style, rng, graph.by_type("chimney")[0]):
            ctx.decoration_motifs.append("side_chimney")
    ctx.passes_run.append("exterior_decoration_pass")


PIPELINE = [
    massing_pass,
    structure_pass,
    mezzanine_floor_pass,
    floor_slab_pass,
    stair_pass,
    facade_detail_pass,
    connection_carve_pass,
    pagoda_shape_pass,
    roof_pass,
    roof_cleanup_pass,
    material_variation_pass,
    interior_furnishing_pass,
    exterior_decoration_pass,
]


def generate_building(style: Style, archetype: str, scale_tier: str,
                      seed: int, group_id: Optional[str] = None,
                      importance_tier: Optional[int] = None) -> BuildContext:
    ctx = BuildContext(style=style, archetype=archetype, scale_tier=scale_tier,
                       seed=seed, rng=random.Random(seed), group_id=group_id,
                       importance_tier=importance_tier)
    for pass_fn in PIPELINE:
        pass_fn(ctx)
    return ctx
