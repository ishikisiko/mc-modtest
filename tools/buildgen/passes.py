"""Pass pipeline + protection mechanism.

Pass order (priorities in grid.PRIORITY):
    massing_pass -> structure_pass -> facade_detail_pass -> roof_pass ->
    roof_cleanup_pass -> material_variation_pass -> interior_furnishing_pass ->
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
from .archetypes import build_massing
from .facade import plan_building_facades
from .grid import AIR, BlockGrid, PRIORITY
from .massing import MassingGraph, Node
from .style import Style

Pos = Tuple[int, int, int]


@dataclass
class BuildContext:
    style: Style
    archetype: str
    scale_tier: str
    seed: int
    rng: random.Random
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
    ctx.graph = build_massing(ctx.archetype, ctx.style, ctx.rng, ctx.scale_tier)
    ctx.passes_run.append("massing_pass")


def _carve_connection(ctx: BuildContext, vol: Node) -> None:
    """Protected 1x2 opening through the shared wall(s) to the parent."""
    parent = ctx.graph.get(vol.attach_to)
    fh = parent.meta["foundation_h"]
    if vol.side in ("west", "east"):
        planes = ((parent.x0 if vol.side == "west" else parent.x1),
                  (vol.x1 if vol.side == "west" else vol.x0))
        zmid = (max(parent.z0, vol.z0) + min(parent.z1, vol.z1)) // 2
        cells = [(px, y, zmid) for px in set(planes) for y in (fh, fh + 1)]
    else:
        planes = ((parent.z1 if vol.side == "back" else parent.z0),
                  (vol.z0 if vol.side == "back" else vol.z1))
        xmid = (max(parent.x0, vol.x0) + min(parent.x1, vol.x1)) // 2
        cells = [(xmid, y, pz) for pz in set(planes) for y in (fh, fh + 1)]
    for pos in cells:
        ctx.grid.set(pos, AIR, ["OPENING", "AIR_CARVE", "PROTECTED"],
                     PRIORITY["OPENING"], force=True)


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
    # interior connections between attached closed volumes and into side sheds
    for vol in graph.volumes():
        if vol.attach_to and vol.type in ("side_wing", "rear_shed"):
            _carve_connection(ctx, vol)
        elif vol.type == "shed" and vol.meta.get("open") and vol.side in ("west", "east"):
            _carve_connection(ctx, vol)
    ctx.passes_run.append("structure_pass")


def facade_detail_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    ctx.wall_plans = plan_building_facades(graph, style, rng)
    for plan in ctx.wall_plans:
        vol = graph.get(plan.volume_id)
        ops.wall_frame(grid, style, rng, vol, plan.wall, plan.post_positions,
                       vol.meta.get("wall_type", "mixed_stone_wood_wall"))
    door = graph.meta["door"]
    door_vol = graph.get(door["volume"])
    ctx.door_info = ops.doorway(grid, style, rng, door_vol, door["wall"], door["x"])
    for plan in ctx.wall_plans:
        vol = graph.get(plan.volume_id)
        for along, ostyle in plan.windows:
            ctx.window_cells += ops.window_kit(grid, style, rng, vol, plan.wall,
                                               along, opening_style=ostyle)
    ctx.passes_run.append("facade_detail_pass")


def roof_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    main = graph.get("main")
    wings = graph.by_type("side_wing")
    roof = main.meta["roof"]
    if roof["type"] == "cross_gable_roof" and wings:
        ctx.roof_info.append(ops.cross_gable_roof(grid, style, rng, main, wings[0]))
    else:
        ctx.roof_info.append(ops.gable_roof(grid, style, rng, main,
                                            roof.get("ridge_axis", "x"),
                                            roof.get("overhang", 1)))
    for vol in graph.volumes():
        if vol.id == "main" or vol.type == "side_wing":
            continue
        vroof = vol.meta.get("roof")
        if not vroof:
            continue
        parent = ctx.graph.get(vol.attach_to) if vol.attach_to else main
        high_y = parent.meta["foundation_h"] + parent.meta["wall_h"]
        ctx.roof_info.append(ops.lean_to_roof(grid, style, vol,
                                              vroof["low_side"], high_y))
    ctx.passes_run.append("roof_pass")


def roof_cleanup_pass(ctx: BuildContext) -> None:
    """Re-seal gable cells lost to collisions; close lean-to side gaps."""
    grid, style, graph = ctx.grid, ctx.style, ctx.graph
    gable_state = style.alternates("WALL_MAIN")[0] if style.alternates("WALL_MAIN") \
        else style.primary("WALL_MAIN")
    for info in ctx.roof_info:
        for pos in info["gable_cells"]:
            if grid.is_empty(pos):
                grid.set(pos, gable_state, ["FACADE", "ROOF"],
                         PRIORITY["ROOF"], "WALL_MAIN")
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
    """Speckle slot materials and break up any same-state flat run > max."""
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
    ctx.passes_run.append("interior_furnishing_pass")


def exterior_decoration_pass(ctx: BuildContext) -> None:
    grid, style, graph, rng = ctx.grid, ctx.style, ctx.graph, ctx.rng
    for node in graph.by_type("porch"):
        main = graph.get(node.attach_to)
        ops.porch(grid, style, rng, node, main)
        ctx.decoration_motifs.append("small_porch")
    for node in graph.by_type("path_patch", "courtyard_patch"):
        ops.ground_patch(grid, style, rng, node)
        ctx.decoration_motifs.append(
            "small_path_patch" if node.type == "path_patch" else "courtyard")
    for node in graph.by_type("decoration_patch"):
        if ops.exterior_decoration_patch(grid, style, rng, node):
            ctx.decoration_motifs.append(node.meta["motif"])
    if graph.by_type("chimney"):
        ctx.decoration_motifs.append("side_chimney")
    ctx.passes_run.append("exterior_decoration_pass")


PIPELINE = [
    massing_pass,
    structure_pass,
    facade_detail_pass,
    roof_pass,
    roof_cleanup_pass,
    material_variation_pass,
    interior_furnishing_pass,
    exterior_decoration_pass,
]


def generate_building(style: Style, archetype: str, scale_tier: str,
                      seed: int) -> BuildContext:
    ctx = BuildContext(style=style, archetype=archetype, scale_tier=scale_tier,
                       seed=seed, rng=random.Random(seed))
    for pass_fn in PIPELINE:
        pass_fn(ctx)
    return ctx
