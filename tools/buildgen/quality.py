"""Quality Check layer (quality_check_pass).

Automatic filtering of obviously broken buildings. Scores are heuristic by
design; hard failures gate export.
"""

from __future__ import annotations

from typing import Dict, List, Tuple

from .grid import AIR
from .passes import BuildContext

FUNCTION_BLOCKS = ("crafting_table", "furnace", "smithing_table", "barrel", "anvil")
PASSABLE_FOOT = ("_stairs", "_slab", "coarse_dirt", "gravel", "cobblestone",
                 "carpet", "_pressure_plate")


def _clamp(v: float) -> int:
    return int(max(0, min(100, round(v))))


def quality_check(ctx: BuildContext, structure_id: str) -> dict:
    grid, style, graph = ctx.grid, ctx.style, ctx.graph
    errors: List[str] = []
    warnings: List[str] = []

    states = [(pos, cell) for pos, cell in grid.iter_cells() if not cell.is_air]

    # 1. entrance
    door_cells = [p for p, c in states if "_door[" in c.state]
    if not door_cells:
        errors.append("no_entrance: building has no door")

    # 2. windows
    glass_cells = [p for p, c in states if c.state.startswith("minecraft:glass")]
    min_windows = style.prop("window_min_count")
    if len(glass_cells) < min_windows:
        errors.append(f"too_few_windows: {len(glass_cells)} glass cells "
                      f"< required {min_windows}")

    # 3. interior furnished
    required_fn = style.prop("interior_required_function_blocks")
    fn_blocks = [p for p, c in states
                 if any(f in c.state for f in FUNCTION_BLOCKS)
                 and "INTERIOR" in c.tags]
    fn_total = [p for p, c in states if any(f in c.state for f in FUNCTION_BLOCKS)]
    if not fn_blocks:
        errors.append("empty_interior: no interior function blocks")
    elif len(fn_total) < required_fn:
        errors.append(f"underfurnished: {len(fn_total)} function blocks "
                      f"< required {required_fn}")

    # 4. flat wall runs (post material_variation_pass)
    max_flat = style.prop("max_flat_wall_width")
    worst_run = 0
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
                    countable = (cell is not None and "FACADE" in cell.tags and
                                 "[" not in cell.state and not cell.is_air)
                    if countable and cell.state == run_state:
                        run_len += 1
                        worst_run = max(worst_run, run_len)
                    else:
                        run_state = cell.state if countable else None
                        run_len = 1 if countable else 0
    if worst_run > max_flat + 2:
        errors.append(f"flat_wall: run of {worst_run} identical facade blocks")
    elif worst_run > max_flat:
        warnings.append(f"flat_wall: run of {worst_run} identical facade blocks")

    # 5. gables sealed
    open_gables = 0
    for info in ctx.roof_info:
        for pos in info.get("gable_cells", []):
            if grid.is_empty(pos):
                open_gables += 1
    if open_gables:
        errors.append(f"open_gable: {open_gables} unsealed gable cells")

    # 6. entrance clear
    if ctx.door_info:
        fx, fy, fz = ctx.door_info["front"]
        foot = grid.state_at((fx, fy, fz))
        head = grid.state_at((fx, fy + 1, fz))
        foot_ok = foot == AIR or any(t in foot for t in PASSABLE_FOOT)
        if not foot_ok or head != AIR:
            errors.append(f"blocked_entrance: foot={foot} head={head}")

    # 7. forbidden blocks
    forbidden = sorted({c.state for _, c in states if style.is_forbidden(c.state)})
    if forbidden:
        errors.append(f"forbidden_blocks: {forbidden}")

    # 8. material balance on facades
    facade_cells = [c for _, c in states if "FACADE" in c.tags]
    stone_frac = 0.0
    if facade_cells:
        stone = sum(1 for c in facade_cells if c.slot == "BASE_STONE")
        stone_frac = stone / len(facade_cells)
        if not (0.02 <= stone_frac <= 0.7):
            warnings.append(f"material_balance: stone fraction {stone_frac:.2f} "
                            "outside [0.02, 0.7]")

    # 9. floating roof cells (no neighbor in any direction)
    floating = 0
    for info in ctx.roof_info:
        for (rx, ry, rz) in info.get("roof_cells", []):
            neighbors = [(rx + 1, ry, rz), (rx - 1, ry, rz), (rx, ry - 1, rz),
                         (rx, ry, rz + 1), (rx, ry, rz - 1), (rx, ry + 1, rz)]
            if all(grid.is_empty(n) for n in neighbors):
                floating += 1
    if floating > 2:
        warnings.append(f"floating_roof: {floating} unsupported roof cells")

    # 10. multi-story invariants
    multi_story_vols = [v for v in graph.volumes() if v.meta.get("stories", 1) > 1]
    if multi_story_vols:
        highest_roof = max((info.get("peak_y", 0) for info in ctx.roof_info), default=0)
        stair = graph.meta.get("stairwell")
        if not stair:
            errors.append("multi_story_missing_stairwell")
        for vol in multi_story_vols:
            top_story_y = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
            if highest_roof <= top_story_y:
                errors.append(
                    f"roof_below_top_story: peak={highest_roof} top={top_story_y}")
            if not stair or stair.get("volume") != vol.id:
                continue
            story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
            for story in range(1, vol.meta.get("stories", 1)):
                y = vol.meta["foundation_h"] + story * story_wall_h
                aligned_opening = False
                for x in range(stair["x0"], stair["x1"] + 1):
                    for z in range(stair["z0"], stair["z1"] + 1):
                        foot = grid.get((x, y, z))
                        head = grid.get((x, y + 1, z))
                        if (foot and head and foot.is_air and head.is_air and
                                foot.protected and head.protected):
                            aligned_opening = True
                if not aligned_opening:
                    errors.append(
                        f"stair_opening_not_aligned: story={story} y={y}")
                landing_z = stair.get("landing_z", stair["z1"] + 1)
                landing = grid.get((stair["x0"], y, landing_z))
                landing_head = grid.get((stair["x0"], y + 1, landing_z))
                if not landing or landing.is_air or "INTERIOR" not in landing.tags:
                    errors.append(
                        f"stair_landing_missing: story={story} pos={(stair['x0'], y, landing_z)}")
                if landing_head and not landing_head.is_air:
                    errors.append(
                        f"stair_landing_head_blocked: story={story} pos={(stair['x0'], y + 1, landing_z)}")

    # 11. no complex block entity NBT: the exporter only writes blockstates,
    # still verify nothing slipped in that needs container/text NBT
    complex_be = sorted({c.state for _, c in states
                         if any(k in c.state for k in ("chest", "sign", "spawner"))})
    if complex_be:
        errors.append(f"complex_block_entity: {complex_be}")

    # ---- scores -------------------------------------------------------
    n_volumes = len(graph.volumes())
    silhouette = 55 + 15 * (n_volumes - 1) + (10 if graph.by_type("chimney") else 0)
    scores = {
        "style_score": _clamp(100 - 40 * len(forbidden) -
                              (15 if not (0.02 <= stone_frac <= 0.7) else 0)),
        "facade_score": _clamp(60 + 6 * min(len(glass_cells), 6) +
                               (10 if ctx.wall_plans else 0) -
                               (25 if worst_run > max_flat else 0)),
        "roof_score": _clamp(90 - 30 * min(open_gables, 3) - 5 * floating),
        "interior_score": _clamp(40 + 12 * min(len(fn_total), 5)),
        "material_balance_score": _clamp(
            100 - abs(stone_frac - 0.25) * 120 if facade_cells else 0),
        "silhouette_score": _clamp(silhouette),
    }

    deco_required = style.prop("exterior_required_decoration_count")
    if len(ctx.decoration_motifs) < deco_required:
        warnings.append(f"few_decorations: {len(ctx.decoration_motifs)} "
                        f"< required {deco_required}")

    return {
        "structure_id": structure_id,
        "style_id": style.style_id,
        "archetype": ctx.archetype,
        "scale_tier": ctx.scale_tier,
        "seed": ctx.seed,
        "passed": not errors,
        "errors": errors,
        "warnings": warnings,
        "scores": scores,
        "stats": {
            "volumes": n_volumes,
            "window_cells": len(glass_cells),
            "function_blocks": len(fn_total),
            "decorations": ctx.decoration_motifs,
            "worst_flat_run": worst_run,
            "stone_fraction": round(stone_frac, 3),
        },
    }
