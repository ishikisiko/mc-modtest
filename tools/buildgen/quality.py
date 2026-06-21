"""Quality Check layer (quality_check_pass).

Automatic filtering of obviously broken buildings. Scores are heuristic by
design; hard failures gate export.
"""

from __future__ import annotations

import hashlib
import os
from typing import Dict, List, Optional, Tuple

from .archetypes import variant_index
from .grid import AIR
from .massing import WALL_OUTWARD
from .ops import wall_info
from .passes import BuildContext

FUNCTION_BLOCKS = (
    "crafting_table", "furnace", "smithing_table", "barrel", "anvil",
    "brewing_stand", "lectern", "bell", "bookshelf", "cauldron", "chest",
)
PASSABLE_FOOT = ("_stairs", "_slab", "coarse_dirt", "gravel", "cobblestone",
                 "carpet", "_pressure_plate")
CULTIVATION_ROOF_FORMS = {
    "sweeping_eave_roof",
    "hip_roof",
    "pyramidal_roof",
    "tiered_eave_roof",
    "pagoda",
    "pavilion",
    "bell_drum_tower",
}
# Vertical-landmark roof forms (pagoda / pavilion / bell_drum_tower) built from
# the existing terrace + tiered_eave_roof vocabulary; the civic-core skyline
# rule requires at least one of these in the core district.
VERTICAL_LANDMARK_ROOF_FORMS = {
    "pagoda",
    "pavilion",
    "bell_drum_tower",
}
# Roof forms derived from the tiered flying-eave massing, so they must carry a
# non-fallback upper tier and upturned corners like tiered_eave_roof itself.
TIERED_DERIVATIVE_FORMS = {"tiered_eave_roof", "pagoda", "bell_drum_tower"}
SWEEPING_DERIVATIVE_FORMS = {"sweeping_eave_roof", "pavilion", "tiered_eave_roof",
                             "pagoda", "bell_drum_tower"}
CULTIVATION_MOTIFS = {
    "moon_gate",
    "spirit_array",
    "incense_altar",
    "cloud_rail",
    "sect_gate_paifang",
}
CULTIVATION_FAMILIES = {"cultivation_town", "cultivation_sect"}
# Roofs that produce a triangular gable end wall which must be enclosed up to
# the ridge. Hip/sweeping/pyramidal/tiered/lean-to roofs have their own eave
# semantics (handled by the roof form + roof_cleanup_pass) and are intentionally
# excluded from the plane-enclosure check.
GABLE_FAMILY_ROOFS = {
    "gable_roof",
    "cross_gable_roof",
    # Vernacular Chinese forms with a triangular gable end wall to enclose.
    # 歇山 (chinese_half_hip) and 卷棚 (chinese_round_ridge) carry their own
    # skirt/curved eave semantics and self-seal, so they are excluded here.
    "chinese_flush_gable",
    "chinese_overhang_gable",
}
WESTERN_DOMESTIC_MOTIFS = {
    "small_porch",
    "side_chimney",
    "woodpile",
    "barrel_cluster",
    "fence_patch",
}


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
                                 "ROOF" not in cell.tags and
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
    invoked_roofs = {info.get("roof_type") for info in ctx.roof_info if info.get("roof_type")}
    unknown_roof_forms = sorted(invoked_roofs - set(style.allowed_roof_types))
    if unknown_roof_forms:
        errors.append(f"roof_form_not_allowed: {unknown_roof_forms}")
    for info in ctx.roof_info:
        if info.get("roof_type") == "chinese_half_hip":
            open_seam = [pos for pos in info.get("seam_cells", [])
                         if grid.is_empty(pos)]
            if not info.get("seam_cells"):
                errors.append("chinese_half_hip_missing_seam_contract")
            elif open_seam:
                errors.append(
                    f"chinese_half_hip_open_seam: {len(open_seam)} cells "
                    f"(e.g. {open_seam[0]})")
        if (info.get("roof_type") in TIERED_DERIVATIVE_FORMS
                and not info.get("fallback")
                and info.get("tier_count", 0) < 2):
            errors.append(f"{info.get('roof_type')}_missing_upper_tier")
        if (info.get("roof_type") in SWEEPING_DERIVATIVE_FORMS
                and not info.get("fallback")
                and not info.get("upturned_corners")):
            errors.append(f"{info.get('roof_type')}_missing_upturned_corners")
        if (info.get("roof_type") == "pyramidal_roof"
                and style.has_slot("RIDGE_ORNAMENT")
                and not info.get("ridge_ornaments")):
            errors.append("pyramidal_roof_missing_finial")
        if info.get("roof_type") == "pagoda" and not info.get("spire_cells"):
            errors.append("pagoda_missing_spire")
        if info.get("roof_type") == "bell_drum_tower" and not info.get("belfry_bell"):
            errors.append("bell_drum_tower_missing_bell")

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
        for vol in multi_story_vols:
            stair = (vol.meta.get("stairwell") or
                     graph.meta.get("stairwells", {}).get(vol.id) or
                     (graph.meta.get("stairwell") if graph.meta.get("stairwell", {}).get("volume") == vol.id else None))
            if not stair:
                errors.append(f"multi_story_missing_stairwell: {vol.id}")
                continue
            top_story_y = vol.meta["foundation_h"] + vol.meta["wall_h"] - 1
            if highest_roof <= top_story_y:
                errors.append(
                    f"roof_below_top_story: {vol.id} peak={highest_roof} top={top_story_y}")
            story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
            mezzanine_story = vol.meta.get("mezzanine_story")
            mezzanine_stories = set(vol.meta.get("mezzanine_stories", []))
            if mezzanine_story is True:
                mezzanine_stories.add(1)
            elif isinstance(mezzanine_story, int):
                mezzanine_stories.add(mezzanine_story)
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
                        f"stair_opening_not_aligned: {vol.id} story={story} y={y}")
                if story in mezzanine_stories:
                    continue
                landing_z = stair.get("landing_z", stair["z1"] + 1)
                landing = grid.get((stair["x0"], y, landing_z))
                landing_head = grid.get((stair["x0"], y + 1, landing_z))
                if not landing or landing.is_air or "INTERIOR" not in landing.tags:
                    errors.append(
                        f"stair_landing_missing: {vol.id} story={story} pos={(stair['x0'], y, landing_z)}")
                if landing_head and not landing_head.is_air:
                    errors.append(
                        f"stair_landing_head_blocked: {vol.id} story={story} pos={(stair['x0'], y + 1, landing_z)}")

    # 11. mezzanine ceilings stay open over the uncovered half-plane.
    for vol in graph.volumes():
        mezzanine = vol.meta.get("mezzanine")
        if not mezzanine:
            continue
        story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
        y = vol.meta["foundation_h"] + story_wall_h + mezzanine.get("y_offset", 0)
        depth = mezzanine.get("depth", max(1, (vol.size[0] - 2) // 2))
        ix0, ix1 = vol.x0 + 1, vol.x1 - 1
        if mezzanine.get("covers") == "east":
            ux0, ux1 = ix0, max(ix0, vol.x1 - depth) - 1
        else:
            ux0, ux1 = min(ix1, vol.x0 + depth) + 1, ix1
        blocked = []
        for x in range(ux0, ux1 + 1):
            for z in range(vol.z0 + 1, vol.z1):
                cell = grid.get((x, y, z))
                if cell and not cell.is_air:
                    blocked.append((x, y, z))
        if blocked:
            errors.append(f"mezzanine_uncovered_ceiling_blocked: {vol.id} count={len(blocked)}")

    # 12. belfry towers carry a bell marker.
    for vol in graph.by_type("tower_volume"):
        if not vol.meta.get("belfry"):
            continue
        bells = [p for p, c in states
                 if "bell" in c.state and vol.x0 <= p[0] <= vol.x1 and vol.z0 <= p[2] <= vol.z1]
        if not bells:
            errors.append(f"belfry_missing_bell: {vol.id}")

    # 13. no forbidden complex block entities: empty chests, blank signs,
    # and banners are exported as blockstates only and are allowed.
    complex_be = sorted({c.state for _, c in states if "spawner" in c.state})
    if complex_be:
        errors.append(f"complex_block_entity: {complex_be}")

    # 14. side-wall plane enclosure: every closed-volume wall cell from the
    # foundation top up to the roofline directly above it SHALL be non-air
    # unless it is a planned OPENING. Catches apex gaps, eave/connection
    # holes, and any other unplanned gap in the wall skin. The roofline is the
    # first *roof-skin* cell (ROOF without FACADE) directly above, so gable
    # infill (FACADE+ROOF, part of the wall enclosure) is scanned through and
    # an apex gap above it is still caught, while a neighbour volume's higher
    # roof does not extend this wall's required enclosure.
    peak_max = max((info.get("peak_y", 0) for info in ctx.roof_info), default=0)
    vols = graph.volumes()
    open_side_holes = 0
    sample_side_hole: Optional[Tuple[int, int, int]] = None
    for vol in vols:
        if vol.meta.get("open"):
            continue
        # Only gable-family roofs carry a gable end wall whose enclosure this
        # check guards; other roof forms have their own eave/hip handling.
        if vol.meta.get("roof", {}).get("type") not in GABLE_FAMILY_ROOFS:
            continue
        fh = vol.meta["foundation_h"]
        wall_top = fh + vol.meta["wall_h"] - 1
        scan_cap = max(peak_max, wall_top) + 2
        for wall in ("front", "back", "west", "east"):
            axis, fixed, (a0, a1), _ = wall_info(vol, wall)
            for along in range(a0, a1 + 1):
                # roofline = first roof-skin cell above the wall body
                roof_y = None
                topmost_roof = None
                for y in range(fh, scan_cap + 1):
                    pos = (along, y, fixed) if axis == "x" else (fixed, y, along)
                    rcell = grid.get(pos)
                    if rcell and "ROOF" in rcell.tags:
                        topmost_roof = y
                        if "FACADE" not in rcell.tags:
                            roof_y = y
                            break
                if roof_y is None:
                    roof_y = topmost_roof  # fallback: no roof skin, use any roof
                if roof_y is None:
                    continue
                for y in range(fh, roof_y):
                    pos = (along, y, fixed) if axis == "x" else (fixed, y, along)
                    hcell = grid.get(pos)
                    if hcell is None or hcell.is_air:
                        if hcell is not None and "OPENING" in hcell.tags:
                            continue
                        open_side_holes += 1
                        if sample_side_hole is None:
                            sample_side_hole = pos
    if open_side_holes:
        errors.append(f"open_side_wall: {open_side_holes} unplanned holes "
                      f"(e.g. {sample_side_hole})")

    # 15. stray exterior block: no INTERIOR/PROTECTED non-OPENING block SHALL
    # sit cardinally adjacent to a *different* volume's exterior wall. Catches
    # interior furniture (anvil/barrel/furnace) mounted against a neighbour's
    # wall skin, e.g. the blacksmith smithy leak.
    wall_cell_owner: Dict[Tuple[int, int, int], str] = {}
    for vol in vols:
        if vol.meta.get("open"):
            continue
        fh = vol.meta["foundation_h"]
        wall_top = fh + vol.meta["wall_h"] - 1
        for wall in ("front", "back", "west", "east"):
            axis, fixed, (a0, a1), _ = wall_info(vol, wall)
            for along in range(a0, a1 + 1):
                for y in range(fh, wall_top + 1):
                    pos = (along, y, fixed) if axis == "x" else (fixed, y, along)
                    wall_cell_owner.setdefault(pos, vol.id)
    furniture_on_wall: List[Tuple[int, int, int]] = []
    for pos, cell in states:
        if cell.is_air or "INTERIOR" not in cell.tags or "OPENING" in cell.tags:
            continue
        if "STRUCTURE" in cell.tags:
            continue  # interior floors/slabs belong to their own volume
        px, py, pz = pos
        against_neighbour = False
        for ox, oz in WALL_OUTWARD.values():
            npos = (px + ox, py, pz + oz)
            owner_id = wall_cell_owner.get(npos)
            if owner_id is None:
                continue
            # is `pos` enclosed by a different volume than the wall owner?
            for vol in vols:
                if vol.id == owner_id:
                    continue
                vfh = vol.meta["foundation_h"]
                vwt = vfh + vol.meta["wall_h"] - 1
                if (vol.x0 <= px <= vol.x1 and vol.z0 <= pz <= vol.z1
                        and vfh <= py <= vwt):
                    against_neighbour = True
                    break
            if against_neighbour:
                break
        if against_neighbour:
            furniture_on_wall.append(pos)
    if furniture_on_wall:
        errors.append(f"furniture_on_wall: {len(furniture_on_wall)} blocks "
                      f"against a neighbour wall (e.g. {furniture_on_wall[0]})")

    # ---- scores -------------------------------------------------------
    n_volumes = len(graph.volumes())
    # Tall cultivation landmarks (pagoda / pavilion / bell_drum_tower) and tall
    # rooflines raise the silhouette so the civic-core skyline reads above the
    # surrounding roofline rather than scoring flat next to a single-storey shed.
    vertical_landmark_bonus = sum(
        12 for info in ctx.roof_info if info.get("vertical_landmark"))
    tallest_wall = max(
        (v.meta.get("foundation_h", 0) + v.meta.get("wall_h", 0)
         for v in graph.volumes()),
        default=0)
    height_bonus = max(0, int(tallest_wall) - 8)
    silhouette = (55 + 15 * (n_volumes - 1)
                  + (10 if graph.by_type("chimney") else 0)
                  + vertical_landmark_bonus + height_bonus)
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
    disallowed_cultivation_motifs = sorted(
        set(ctx.decoration_motifs) & CULTIVATION_MOTIFS - set(style.allowed_motifs))
    if disallowed_cultivation_motifs:
        errors.append(f"cultivation_motif_not_allowed: {disallowed_cultivation_motifs}")
    is_cultivation = (
        graph.meta.get("cultivation_form")
        or graph.meta.get("style_family") in CULTIVATION_FAMILIES
    )
    if is_cultivation:
        western_nodes = [node.id for node in graph.by_type("chimney", "porch")]
        if western_nodes:
            errors.append(f"cultivation_western_nodes_present: {western_nodes}")
        western_motifs = sorted(set(ctx.decoration_motifs) & WESTERN_DOMESTIC_MOTIFS)
        if western_motifs:
            errors.append(f"cultivation_western_motifs_present: {western_motifs}")
        if graph.meta.get("requires_platform_colonnade"):
            if not graph.by_type("platform"):
                errors.append("cultivation_platform_missing")
            if not graph.by_type("colonnade"):
                errors.append("cultivation_colonnade_missing")
        if graph.meta.get("requires_pagoda_insets"):
            insets = list(graph.meta.get("pagoda_story_insets", []))
            if len(insets) < 2 or any(b <= a for a, b in zip(insets, insets[1:])):
                errors.append(f"pagoda_story_insets_invalid: {insets}")
            pagoda_roofs = {info.get("roof_type") for info in ctx.roof_info}
            if "pyramidal_roof" not in pagoda_roofs:
                errors.append("pagoda_crown_not_pyramidal")

    legacy_styles = {"medieval_village", "chinese_courtyard"}
    if style.style_id in legacy_styles or ctx.group_id == "civic":
        invoked_cultivation = sorted(
            (invoked_roofs & CULTIVATION_ROOF_FORMS) |
            (set(ctx.decoration_motifs) & CULTIVATION_MOTIFS))
        if invoked_cultivation:
            errors.append(f"legacy_style_invoked_cultivation_forms: {invoked_cultivation}")
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


# ---- cultivation variant distinctness gate -------------------------------
# Cross-variant aggregation over a generated report set. Verifies the four
# small/medium cultivation-town archetypes ship deliberately distinct 形制
# (高低/长宽/胖瘦/后院) rather than re-rolls of one footprint. See the openspec
# capability `cultivation-variant-differentiation`: per-archetype silhouette
# spread SHALL be >= 30 and no two shipped variant NBTs SHALL be byte-identical.

CULTIVATION_VARIANT_GATE_ARCHETYPES = (
    "cultivation_house",
    "cultivation_shop",
    "cultivation_market",
    "cultivation_inn",
)
CULTIVATION_VARIANT_MIN_SPREAD = 30


def _nbt_sha256(path: str) -> Optional[str]:
    try:
        with open(path, "rb") as f:
            return hashlib.sha256(f.read()).hexdigest()
    except OSError:
        return None


def cultivation_variant_distinctness(reports: List[dict], structure_dir: str) -> dict:
    """Aggregate the cultivation-town variant distinctness gate over a report set.

    Groups the four gated archetypes by variant index (parsed off the
    ``scale_tier`` ``_vN`` suffix). For each archetype records the per-variant
    ``silhouette_score`` and the sha256 of the exported ``.nbt``, computes the
    silhouette spread (max - min), and flags byte-identical variant pairs.

    ``structure_dir`` is the mod structure resource directory used to locate the
    exported ``<name>.nbt`` files; ``name`` is derived from each report's
    ``structure_id`` (the segment after the ``/``).

    Returns a dict with:

    - ``archetypes``: ``{archetype: {str(vindex): {name, silhouette, nbt_sha256}}}``
    - ``spreads``: ``{archetype: int}`` (max silhouette - min silhouette)
    - ``byte_identical_pairs``: list of ``{archetype, variants, names}``
    - ``errors``: one descriptive string per failed spread or byte-identity,
      naming the offending archetype/variants
    - ``min_spread``: the gate threshold (CULTIVATION_VARIANT_MIN_SPREAD)
    - ``passed``: ``True`` iff no spread or byte-identity error
    """
    by_arch: Dict[str, Dict[int, dict]] = {}
    for r in reports:
        arch = r.get("archetype")
        if arch not in CULTIVATION_VARIANT_GATE_ARCHETYPES:
            continue
        score = r.get("scores", {}).get("silhouette_score")
        if score is None:
            continue
        tier = str(r.get("scale_tier", ""))
        vi = variant_index(tier)
        sid = str(r.get("structure_id", ""))
        name = sid.split("/", 1)[-1] if "/" in sid else sid
        nbt_path = os.path.join(structure_dir, f"{name}.nbt")
        by_arch.setdefault(arch, {})[vi] = {
            "name": name,
            "silhouette": int(score),
            "nbt_sha256": _nbt_sha256(nbt_path),
        }

    spreads: Dict[str, int] = {}
    byte_pairs: List[dict] = []
    errors: List[str] = []
    for arch in CULTIVATION_VARIANT_GATE_ARCHETYPES:
        variants = by_arch.get(arch)
        if not variants:
            continue
        scores = [v["silhouette"] for v in variants.values()]
        spread = max(scores) - min(scores) if scores else 0
        spreads[arch] = spread
        if spread < CULTIVATION_VARIANT_MIN_SPREAD:
            per_variant = ", ".join(
                f"v{vi}={v['silhouette']}" for vi, v in sorted(variants.items()))
            errors.append(
                f"cultivation_variant_spread_too_low: {arch} spread={spread} "
                f"< {CULTIVATION_VARIANT_MIN_SPREAD} ({per_variant})")
        items = sorted(variants.items())
        for i in range(len(items)):
            for j in range(i + 1, len(items)):
                vi_lo, a = items[i]
                vi_hi, b = items[j]
                if a.get("nbt_sha256") and a["nbt_sha256"] == b["nbt_sha256"]:
                    byte_pairs.append({
                        "archetype": arch,
                        "variants": [vi_lo, vi_hi],
                        "names": [a["name"], b["name"]],
                    })
                    errors.append(
                        f"cultivation_variants_byte_identical: {arch} "
                        f"v{vi_lo}({a['name']}) == v{vi_hi}({b['name']})")

    return {
        "archetypes": {a: {str(vi): data for vi, data in sorted(v.items())}
                       for a, v in by_arch.items()},
        "spreads": spreads,
        "byte_identical_pairs": byte_pairs,
        "min_spread": CULTIVATION_VARIANT_MIN_SPREAD,
        "errors": errors,
        "passed": not errors,
    }
