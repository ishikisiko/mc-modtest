"""Chinese courtyard compound parcel layer.

The compound graph sits above individual MassingGraphs: it generates each
sub-building through the existing pass pipeline, then translates the resulting
voxel grids into a walled parcel with structural landscape and circulation.
"""

from __future__ import annotations

import hashlib
import os
import random
import zlib
from collections import deque
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Literal, Optional, Sequence, Set, Tuple

from .grid import AIR, BlockGrid, PRIORITY
from .massing import MassingGraph
from .passes import BuildContext, PIPELINE
from .quality import quality_check
from .style import Style, load_style

Cell2 = Tuple[int, int]
Pos = Tuple[int, int, int]

GroundKind = Literal["open_sky", "under_eave", "interior"]


COURTYARD_SIZE = {
    "town_small": (35, 31),
    "small": (39, 45),
    "medium": (43, 47),
    "large": (47, 47),
}
COURTYARD_VARIANT_SIZES = ("small", "medium", "large")
WATER_FORMS = ("pool", "channel", "well")
PLANTING_LAYOUTS = ("corner_beds", "side_beds", "asymmetric_beds")
# The four vernacular roof forms registered in ops.ROOF_REGISTRY. The variant
# axis carries the form name directly so it routes through the form registry
# (no string-prefix dispatch); the Chinese label is documentation only.
CHINESE_ROOF_FORMS = {
    "硬山": "chinese_flush_gable",
    "悬山": "chinese_overhang_gable",
    "歇山": "chinese_half_hip",
    "卷棚": "chinese_round_ridge",
}
ROOF_GRADES = ("chinese_flush_gable", "chinese_overhang_gable",
               "chinese_half_hip", "chinese_round_ridge")
# Plan-level variant axes (Step 2). The shipped library exercises south/north
# orientation; full `east` rotation is deferred (see docs/ai-kb deferred §E).
LAYOUT_TYPES = ("standard", "three-sided", "mu")
MAIN_ORIENTATIONS = ("south", "east", "north")
MAIN_BAYS = (3, 5, 7)
PLATFORM_TIERS = ("none", "stone_2", "xumi_3")
PLATFORM_TIER_HEIGHT = {"none": 0, "stone_2": 2, "xumi_3": 3}
# 街门 forms: 广亮 (gateway through a building), 蛮子 (flush with wall),
# 金柱 (set into the front columns). Legacy small-courtyard/sect/town variants
# still pass their own gate-half labels through the same field.
GATE_TYPES = ("guangliang", "manzi", "jinzhu")
GATE_HALF = {
    # vernacular street-gate forms
    "guangliang": 2, "manzi": 1, "jinzhu": 2,
    # legacy small-courtyard / town / sect gate labels
    "plain_gate": 1, "lantern_gate": 2, "double_eave_gate": 2,
    "moon_gate_axis": 2,
}
SYMMETRY_MODES = ("mild_asymmetry", "strict_mirror")
TOWN_BLOCK_SHAPES = ((1, 2), (1, 3), (2, 2))
TOWN_STREET_WIDTHS = (5, 7)
TOWN_LANE_WIDTH = 3
DEFAULT_TOWN_ROSTER = (
    "cultivation_house",
    "cultivation_shop",
    "cultivation_inn",
    "cultivation_market",
    "town_shrine",
)


@dataclass(frozen=True)
class CompoundVariant:
    courtyard_size: str
    water_form: str
    planting_layout: str
    roof_grade: str
    gate_type: str
    symmetry: str = "mild_asymmetry"
    # Plan-level axes (Step 2). Defaulted so the cultivation small-courtyard,
    # sect, and town variants — which reuse this dataclass — construct unchanged.
    layout_type: str = "standard"
    main_orientation: str = "south"
    main_bays: int = 5
    platform_tier: str = "stone_2"

    def key(self) -> Tuple:
        return (
            self.courtyard_size,
            self.water_form,
            self.planting_layout,
            self.roof_grade,
            self.gate_type,
            self.symmetry,
            self.layout_type,
            self.main_orientation,
            self.main_bays,
            self.platform_tier,
        )

    def to_dict(self) -> dict:
        return {
            "courtyard_size": self.courtyard_size,
            "water_form": self.water_form,
            "planting_layout": self.planting_layout,
            "roof_grade": self.roof_grade,
            "gate_type": self.gate_type,
            "symmetry": self.symmetry,
            "layout_type": self.layout_type,
            "main_orientation": self.main_orientation,
            "main_bays": self.main_bays,
            "platform_tier": self.platform_tier,
        }


@dataclass(frozen=True)
class TownBlockVariant:
    rows: int
    courtyards_per_row: int
    street_width: int
    lane: bool
    corner_frontage: bool
    courtyard_size: str = "town_small"

    def key(self) -> Tuple[int, int, int, bool, bool, str]:
        return (
            self.rows,
            self.courtyards_per_row,
            self.street_width,
            self.lane,
            self.corner_frontage,
            self.courtyard_size,
        )

    def to_dict(self) -> dict:
        return {
            "rows": self.rows,
            "courtyards_per_row": self.courtyards_per_row,
            "street_width": self.street_width,
            "lane": self.lane,
            "corner_frontage": self.corner_frontage,
            "courtyard_size": self.courtyard_size,
        }


@dataclass
class ParcelNode:
    id: str
    type: str
    cells: Set[Cell2]
    meta: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "type": self.type,
            "cells": [list(c) for c in sorted(self.cells)],
            "meta": self.meta,
        }

    def to_summary_dict(self) -> dict:
        # Compact form for library reports: the per-cell `cells` list has no
        # downstream consumer (validators read NBT, not the report graph), so
        # collapse it to a count + bounding box. Use to_dict() if you need the
        # full coordinate list.
        xs = [c[0] for c in self.cells]
        zs = [c[1] for c in self.cells]
        if xs and zs:
            bbox = [min(xs), min(zs), max(xs), max(zs)]
        else:
            bbox = None
        return {
            "id": self.id,
            "type": self.type,
            "cell_count": len(self.cells),
            "bbox": bbox,
            "meta": self.meta,
        }


@dataclass
class BuildingSlot:
    id: str
    archetype: str
    origin: Pos
    graph: MassingGraph
    footprint: Set[Cell2]
    quality: Optional[dict] = None
    door_info: Optional[dict] = None

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "archetype": self.archetype,
            "origin": list(self.origin),
            "footprint": [list(c) for c in sorted(self.footprint)],
            "massing_graph": self.graph.to_dict(),
            "quality": self.quality,
            "door_info": self.door_info,
        }

    def to_summary_dict(self) -> dict:
        # Compact form for library reports: drop the per-cell `footprint` list
        # (no consumer) but keep the full `massing_graph`, since the compound
        # validator reads `massing_graph.meta.frontage` and node origins/sizes
        # from the cultivation_town report.
        xs = [c[0] for c in self.footprint]
        zs = [c[1] for c in self.footprint]
        if xs and zs:
            bbox = [min(xs), min(zs), max(xs), max(zs)]
        else:
            bbox = None
        return {
            "id": self.id,
            "archetype": self.archetype,
            "origin": list(self.origin),
            "footprint_count": len(self.footprint),
            "footprint_bbox": bbox,
            "massing_graph": self.graph.to_summary_dict(),
            "quality": self.quality,
            "door_info": self.door_info,
        }


@dataclass
class CompoundGraph:
    style_id: str
    seed: int
    variant: CompoundVariant
    lot_size: Tuple[int, int]
    axis_x: int
    grid: BlockGrid = field(default_factory=BlockGrid)
    parcel_nodes: List[ParcelNode] = field(default_factory=list)
    building_slots: List[BuildingSlot] = field(default_factory=list)
    meta: dict = field(default_factory=dict)

    def node_cells(self, *types: str) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for node in self.parcel_nodes:
            if node.type in types:
                cells.update(node.cells)
        return cells

    def building_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for slot in self.building_slots:
            cells.update(slot.footprint)
        return cells

    def to_dict(self) -> dict:
        return {
            "style_id": self.style_id,
            "seed": self.seed,
            "variant": self.variant.to_dict(),
            "lot_size": list(self.lot_size),
            "axis_x": self.axis_x,
            "parcel_nodes": [n.to_dict() for n in self.parcel_nodes],
            "building_slots": [s.to_dict() for s in self.building_slots],
            "meta": self.meta,
        }

    def to_summary_dict(self) -> dict:
        # Compact form for the library report JSON: same top-level shape as
        # to_dict(), but per-cell coordinate lists (parcel cells, building
        # footprints) are folded into counts + bounding boxes. `meta` stays
        # full because cultivation_sect validation reads meta.terrace_levels
        # and town meta carries the small `courtyards[]` list.
        return {
            "style_id": self.style_id,
            "seed": self.seed,
            "variant": self.variant.to_dict(),
            "lot_size": list(self.lot_size),
            "axis_x": self.axis_x,
            "parcel_nodes": [n.to_summary_dict() for n in self.parcel_nodes],
            "building_slots": [s.to_summary_dict() for s in self.building_slots],
            "meta": self.meta,
        }


# Deterministic 6-row template table (design D3). Each row pins a distinct
# (layout_type, main_orientation, main_bays, roof_grade, platform_tier,
# gate_type, courtyard_size) tuple so the six shipped NBTs read as visibly
# different 形制, not six random rolls. Minor axes (water_form,
# planting_layout, symmetry) stay RNG-derived per seed.
COMPOUND_TEMPLATES = (
    # 0 — large standard 五间 hall, 歇山, sumi platform, 广亮大门, south
    dict(layout_type="standard", main_orientation="south", main_bays=5,
         roof_grade="chinese_half_hip", platform_tier="xumi_3",
         gate_type="guangliang", courtyard_size="large"),
    # 1 — medium 三合院, 硬山, low platform, 蛮子门, south
    dict(layout_type="three-sided", main_orientation="south", main_bays=3,
         roof_grade="chinese_flush_gable", platform_tier="stone_2",
         gate_type="manzi", courtyard_size="medium"),
    # 2 — large 七间 hall, 悬山, sumi platform, 金柱门
    dict(layout_type="standard", main_orientation="south", main_bays=7,
         roof_grade="chinese_overhang_gable", platform_tier="xumi_3",
         gate_type="jinzhu", courtyard_size="large"),
    # 3 — small 目字 outer band, 卷棚, stone platform, 蛮子门
    dict(layout_type="mu", main_orientation="south", main_bays=3,
         roof_grade="chinese_round_ridge", platform_tier="stone_2",
         gate_type="manzi", courtyard_size="small"),
    # 4 — medium standard 五间, 悬山, stone platform, 广亮大门
    dict(layout_type="standard", main_orientation="south", main_bays=5,
         roof_grade="chinese_overhang_gable", platform_tier="stone_2",
         gate_type="guangliang", courtyard_size="medium"),
    # 5 — small 三合院 三间, 歇山, stone platform, 金柱门, south
    dict(layout_type="three-sided", main_orientation="south", main_bays=3,
         roof_grade="chinese_half_hip", platform_tier="stone_2",
         gate_type="jinzhu", courtyard_size="small"),
)


def select_variant(seed: int) -> CompoundVariant:
    row = COMPOUND_TEMPLATES[seed % len(COMPOUND_TEMPLATES)]
    rng = random.Random(seed)
    return CompoundVariant(
        courtyard_size=row["courtyard_size"],
        water_form=rng.choice(WATER_FORMS),
        planting_layout=rng.choice(PLANTING_LAYOUTS),
        roof_grade=row["roof_grade"],
        gate_type=row["gate_type"],
        symmetry=rng.choice(("mild_asymmetry", "mild_asymmetry", "strict_mirror")),
        layout_type=row["layout_type"],
        main_orientation=row["main_orientation"],
        main_bays=row["main_bays"],
        platform_tier=row["platform_tier"],
    )


def select_town_block_variant(seed: int) -> TownBlockVariant:
    rng = random.Random(seed)
    rows, cols = rng.choice(TOWN_BLOCK_SHAPES)
    lane = cols >= 3 and rng.choice((False, True))
    return TownBlockVariant(
        rows=rows,
        courtyards_per_row=cols,
        street_width=rng.choice(TOWN_STREET_WIDTHS),
        lane=lane,
        corner_frontage=rng.choice((False, True)),
    )


def _apply_roof_grade(ctx: BuildContext, roof_grade: str) -> None:
    # Accept either a registered form name or a legacy Chinese label.
    form = CHINESE_ROOF_FORMS.get(roof_grade, roof_grade)
    ctx.graph.meta["roof_grade"] = form
    for vol in ctx.graph.volumes():
        roof = vol.meta.get("roof")
        if roof and form in ROOF_GRADES:
            roof["grade"] = form
            roof["type"] = form


def generate_subbuilding(style: Style, archetype: str, seed: int,
                         roof_grade: Optional[str],
                         group_id: Optional[str] = None,
                         importance_tier: Optional[int] = None,
                         form_overrides: Optional[dict] = None) -> BuildContext:
    ctx = BuildContext(style=style, archetype=archetype, scale_tier=archetype,
                       seed=seed, rng=random.Random(seed), group_id=group_id,
                       importance_tier=importance_tier,
                       form_overrides=form_overrides or {})
    for pass_fn in PIPELINE:
        pass_fn(ctx)
        if roof_grade and pass_fn.__name__ == "massing_pass":
            _apply_roof_grade(ctx, roof_grade)
    return ctx


def _non_air_bounds(grid: BlockGrid) -> Tuple[Pos, Pos]:
    cells = [(p, c) for p, c in grid.iter_cells() if not c.is_air]
    if not cells:
        return (0, 0, 0), (0, 0, 0)
    xs = [p[0] for p, _ in cells]
    ys = [p[1] for p, _ in cells]
    zs = [p[2] for p, _ in cells]
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def _translate_context(compound: CompoundGraph, slot_id: str, ctx: BuildContext,
                       main_origin: Pos) -> BuildingSlot:
    main = ctx.graph.get("main")
    dx = main_origin[0] - main.x0
    dy = main_origin[1] - main.y0
    dz = main_origin[2] - main.z0
    for (x, y, z), cell in list(ctx.grid.iter_cells()):
        compound.grid.set((x + dx, y + dy, z + dz), cell.state, cell.tags,
                          cell.priority, cell.slot)
    for entity in ctx.grid.entities:
        compound.grid.add_entity(entity, (dx, dy, dz))

    footprint: Set[Cell2] = set()
    for vol in ctx.graph.volumes():
        for x in range(vol.x0 + dx, vol.x1 + dx + 1):
            for z in range(vol.z0 + dz, vol.z1 + dz + 1):
                footprint.add((x, z))

    shifted_graph = MassingGraph(
        meta={**ctx.graph.meta, "compound_offset": [dx, dy, dz]},
        nodes=list(ctx.graph.nodes),
    )
    quality = None
    if ctx.archetype in (
        "main_hall", "side_wing", "front_row",
        "open_hall", "tower_house", "flower_hall",
        "sect_gate", "sect_main_hall", "scripture_pavilion",
        "alchemy_room", "disciple_quarters",
    ):
        quality = quality_check(ctx, f"{compound.style_id}/{slot_id}")
    # Propagate the per-building door front (courtyard-path-network endpoint)
    # into compound-grid coordinates so the path router can stop one cell short
    # of each door. door_info["front"] is a 3-tuple in ctx-local coords.
    door_info: Optional[dict] = None
    if ctx.door_info is not None:
        front = ctx.door_info.get("front")
        if isinstance(front, tuple) and len(front) == 3:
            shifted_front = (front[0] + dx, front[1] + dy, front[2] + dz)
            door_info = {**ctx.door_info, "front": shifted_front}
        else:
            door_info = dict(ctx.door_info)
    slot = BuildingSlot(slot_id, ctx.archetype, main_origin, shifted_graph,
                        footprint, quality, door_info)
    compound.building_slots.append(slot)
    return slot


def _context_bounds_2d(ctx: BuildContext) -> Tuple[int, int, int, int]:
    volumes = ctx.graph.volumes()
    if not volumes:
        main = ctx.graph.get("main")
        volumes = [main]
    return (
        min(vol.x0 for vol in volumes),
        min(vol.z0 for vol in volumes),
        max(vol.x1 for vol in volumes),
        max(vol.z1 for vol in volumes),
    )


def _translate_context_to_min(compound: CompoundGraph, slot_id: str,
                              ctx: BuildContext, target_x0: int,
                              target_z0: int) -> BuildingSlot:
    x0, z0, _, _ = _context_bounds_2d(ctx)
    main = ctx.graph.get("main")
    return _translate_context(
        compound,
        slot_id,
        ctx,
        (main.x0 + target_x0 - x0, 0, main.z0 + target_z0 - z0),
    )


def _rect(x0: int, z0: int, x1: int, z1: int) -> Set[Cell2]:
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


def _bounded(cells: Iterable[Cell2], lot_w: int, lot_d: int,
             blocked: Set[Cell2]) -> Set[Cell2]:
    out = set()
    for x, z in cells:
        if 1 <= x < lot_w - 1 and 1 <= z < lot_d - 1 and (x, z) not in blocked:
            out.add((x, z))
    return out


def _bfs(start: Cell2, goal: Cell2, lot_w: int, lot_d: int,
         blocked: Set[Cell2]) -> List[Cell2]:
    q = deque([start])
    came: Dict[Cell2, Optional[Cell2]] = {start: None}
    while q:
        cur = q.popleft()
        if cur == goal:
            break
        x, z = cur
        for nxt in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if not (1 <= nxt[0] < lot_w - 1 and 1 <= nxt[1] < lot_d - 1):
                continue
            if nxt in blocked and nxt != goal:
                continue
            if nxt in came:
                continue
            came[nxt] = cur
            q.append(nxt)
    if goal not in came:
        raise ValueError(f"no route from {start} to {goal}")
    path = []
    cur: Optional[Cell2] = goal
    while cur is not None:
        path.append(cur)
        cur = came[cur]
    return list(reversed(path))


def _put_cells(compound: CompoundGraph, node_id: str, node_type: str,
               cells: Set[Cell2], state: str, tags: List[str],
               y: int = 0, height: int = 1, slot: Optional[str] = None,
               meta: Optional[dict] = None) -> None:
    for x, z in cells:
        for yy in range(y, y + height):
            compound.grid.set((x, yy, z), state, tags, PRIORITY["DETAIL"], slot)
    compound.parcel_nodes.append(ParcelNode(node_id, node_type, cells, meta or {}))


def _clear_cells(compound: CompoundGraph, cells: Set[Cell2],
                 y0: int = 0, y1: int = 2) -> None:
    for x, z in cells:
        for y in range(y0, y1 + 1):
            compound.grid.set((x, y, z), AIR, ["AIR_CARVE"],
                              PRIORITY["AIR_CARVE"], force=True)


def _add_perimeter(compound: CompoundGraph, style: Style) -> None:
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_half = GATE_HALF[compound.variant.gate_type]
    gate_side = compound.meta.get("gate_side", "south")
    if gate_side == "north":
        gate = {(x, lot_d - 1) for x in range(axis - gate_half, axis + gate_half + 1)}
    elif gate_side == "south":
        gate = {(x, 0) for x in range(axis - gate_half, axis + gate_half + 1)}
    else:
        raise ValueError(f"unsupported gate_side {gate_side!r}")
    wall_cells = set()
    for x in range(lot_w):
        for z in (0, lot_d - 1):
            if (x, z) not in gate:
                wall_cells.add((x, z))
    for z in range(lot_d):
        wall_cells.add((0, z))
        wall_cells.add((lot_w - 1, z))
    for x, z in wall_cells:
        compound.grid.set((x, 0, z), style.primary("BASE_STONE"),
                          ["STRUCTURE"], PRIORITY["STRUCTURE"], "BASE_STONE")
        for y in range(1, 4):
            compound.grid.set((x, y, z), style.primary("WALL_MAIN"),
                              ["STRUCTURE"], PRIORITY["STRUCTURE"], "WALL_MAIN")
        compound.grid.set((x, 4, z), style.slot_entry("ROOF_DARK", "_slab"),
                          ["ROOF"], PRIORITY["ROOF"], "ROOF_DARK")
    compound.parcel_nodes.append(
        ParcelNode("perimeter_wall", "perimeter_wall", wall_cells,
                   {"gate_opening": [
                       min(x for x, _ in gate),
                       min(z for _, z in gate),
                       max(x for x, _ in gate),
                       max(z for _, z in gate),
                   ], "gate_side": gate_side}))


def _place_landscape(compound: CompoundGraph, style: Style,
                     courtyard: Tuple[int, int, int, int]) -> None:
    lot_w, lot_d = compound.lot_size
    x0, z0, x1, z1 = courtyard
    axis = compound.axis_x
    blocked = compound.building_cells()
    mid_z = (z0 + z1) // 2

    if compound.variant.water_form == "pool":
        water = _rect(axis - 7, mid_z - 2, axis - 3, mid_z + 2)
    elif compound.variant.water_form == "channel":
        water = _rect(axis - 8, mid_z, axis + 8, mid_z + 1)
    else:
        water = _rect(axis + 4, mid_z - 1, axis + 6, mid_z + 1)
    water = _bounded(water, lot_w, lot_d, blocked)
    _clear_cells(compound, water, y0=-1, y1=1)
    _put_cells(compound, "water_feature", "water_feature", water,
               style.primary("WATER"), ["DETAIL", "GROUND"], y=-1, slot="WATER")
    blocked |= water

    rng = random.Random(compound.seed + 911)
    bamboo_candidates: Set[Cell2] = set()
    for x, z in water:
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if (1 <= nx < lot_w - 1 and 1 <= nz < lot_d - 1
                    and (nx, nz) not in blocked):
                bamboo_candidates.add((nx, nz))
    bamboo = {cell for cell in bamboo_candidates if rng.random() < 0.28}
    if bamboo_candidates and not bamboo:
        bamboo.add(rng.choice(sorted(bamboo_candidates)))
    for x, z in sorted(bamboo):
        if compound.grid.is_empty((x, -1, z)):
            compound.grid.set((x, -1, z), "minecraft:dirt",
                              ["DETAIL", "GROUND"], PRIORITY["DETAIL"],
                              "PLANTING")
        height = rng.choice((2, 3, 3, 4))
        for y in range(height):
            leaves = "large" if y == height - 1 else ("small" if y == height - 2 else "none")
            stage = 1 if y == height - 1 else 0
            compound.grid.set(
                (x, y, z),
                f"minecraft:bamboo[age=0,leaves={leaves},stage={stage}]",
                ["DETAIL"], PRIORITY["DETAIL"], "PLANTING")
    if bamboo:
        compound.parcel_nodes.append(ParcelNode("bamboo_grove", "planting", bamboo))
        blocked |= bamboo

    if compound.variant.planting_layout == "corner_beds":
        planting = (_rect(x0, z0, x0 + 2, z0 + 2) |
                    _rect(x1 - 2, z0, x1, z0 + 2) |
                    _rect(x0, z1 - 2, x0 + 2, z1) |
                    _rect(x1 - 2, z1 - 2, x1, z1))
    elif compound.variant.planting_layout == "side_beds":
        planting = _rect(x0, z0 + 2, x0 + 1, z1 - 2) | _rect(x1 - 1, z0 + 2, x1, z1 - 2)
    else:
        planting = _rect(x1 - 4, z0 + 1, x1 - 1, z0 + 4) | _rect(x0 + 1, z1 - 4, x0 + 4, z1 - 1)
    planting = _bounded(planting, lot_w, lot_d, blocked)
    plant_states = style.material_slots["PLANTING"]
    _clear_cells(compound, planting, y0=0, y1=1)
    for i, (x, z) in enumerate(sorted(planting)):
        state = plant_states[i % len(plant_states)]
        compound.grid.set((x, 0, z), state, ["DETAIL", "GROUND"],
                          PRIORITY["DETAIL"], "PLANTING")
    compound.parcel_nodes.append(ParcelNode("planting", "planting", planting))


def _route_circulation(compound: CompoundGraph, style: Style,
                       main_slot: BuildingSlot, west_slot: BuildingSlot,
                       east_slot: BuildingSlot) -> None:
    """Small-courtyard circulation router.

    Refactored by fix-courtyard-ground-walkability (task 2.6) to delegate to the
    shared ``_multi_source_bfs``. Kept for any caller that still passes the
    explicit (main, west, east) slots; the canonical small-courtyard flow now
    calls ``_route_complete_path`` directly (task 2.7). Endpoints fall back to
    each slot's footprint center (small-courtyard builds don't always populate
    door_info) plus the gate entry, water, and planting nodes.
    """
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_side = compound.meta.get("gate_side", "south")

    endpoints: List[Cell2] = []
    wall = next((n for n in compound.parcel_nodes if n.type == "perimeter_wall"),
                None)
    if wall is not None:
        opening = wall.meta.get("gate_opening")
        if opening:
            x0, _z0, x1, _z1 = opening
            gate_axis = (x0 + x1) // 2
            z_in = 1 if gate_side == "south" else lot_d - 2
            if 0 < gate_axis < lot_w - 1:
                endpoints.append((gate_axis, z_in))
    for slot in (main_slot, west_slot, east_slot):
        if slot.footprint:
            xs = [x for x, _ in slot.footprint]
            zs = [z for _, z in slot.footprint]
            endpoints.append(((min(xs) + max(xs)) // 2,
                              (min(zs) + max(zs)) // 2))
    endpoints.extend(sorted(compound.node_cells("water_feature")))
    endpoints.extend(sorted(compound.node_cells("planting")))

    blocked = (compound.building_cells() |
               compound.node_cells("water_feature", "planting", "perimeter_wall"))
    blocked = {c for c in blocked if 0 < c[0] < lot_w - 1 and 0 < c[1] < lot_d - 1}
    dist = _multi_source_bfs(endpoints, blocked, lot_w, lot_d)
    path = set(dist.keys())
    _put_cells(compound, "central_path", "path", path,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
               slot="GROUND_PATH")


# ---------------------------------------------------------------------------
# Ground + path layer (courtyard-ground-layer / courtyard-path-network specs).
# One-jin `generate_compound` and small-courtyard `generate_small_courtyard`
# share these passes. The ground layer fills every non-building parcel cell with
# a solid block classified as 露天 (open_sky) or 屋檐下 (under_eave); the path
# layer then overlays a multi-source-BFS walkable network connecting every door
# and landscape feature, with a single stairs block bridging the plinth edge.
# ---------------------------------------------------------------------------


def _lot_interior_cells(compound: CompoundGraph) -> Set[Cell2]:
    """Cells strictly inside the perimeter (1 ≤ x ≤ lot_w-2, 1 ≤ z ≤ lot_d-2)."""
    lot_w, lot_d = compound.lot_size
    return {(x, z) for x in range(1, lot_w - 1) for z in range(1, lot_d - 1)}


def _cell_band(compound: CompoundGraph, cell: Cell2) -> str:
    """Which yard band a cell sits in: outer_yard / inner_gate / main_yard.

    Falls back to ``outer_yard`` for compounds that never published band tuples
    (e.g. the small-courtyard unit, which has no plinth so the band only matters
    for natural-surface-y selection there)."""
    z = cell[1]
    oy = compound.meta.get("outer_yard_band")
    ig = compound.meta.get("inner_gate_band")
    my = compound.meta.get("main_yard_band")
    if oy and ig and my:
        oy0, oy1 = oy
        ig0, ig1 = ig
        my0, my1 = my
        if oy0 <= z <= oy1:
            return "outer_yard"
        if ig0 <= z <= ig1:
            return "inner_gate"
        if my0 <= z <= my1:
            return "main_yard"
    return "outer_yard"


def _natural_surface_y(compound: CompoundGraph, cell: Cell2) -> int:
    """Compound-grid y of the cell's walkable surface.

    ``-1`` everywhere except the main-yard plinth (where the player walks on top
    of the plinth, so the surface is ``plinth_h - 1``).
    """
    plinth_h = compound.meta.get("plinth_height", 0)
    if plinth_h > 0 and _cell_band(compound, cell) == "main_yard":
        return plinth_h - 1
    return -1


def _chebyshev_ring(footprint: Set[Cell2]) -> Set[Cell2]:
    """1-cell-wide Chebyshev ring around a footprint, excluding the footprint."""
    ring: Set[Cell2] = set()
    for fx, fz in footprint:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                cell = (fx + dx, fz + dz)
                if cell not in footprint:
                    ring.add(cell)
    return ring


def _derive_ground_kinds(compound: CompoundGraph) -> Dict[Cell2, str]:
    """Derive a ``ground_kind`` for every lot-interior parcel cell.

    Rule (courtyard-ground-layer + path-surface-zoning specs):
      1. default ``open_sky``;
      2. ``covered_gallery.cells`` → ``gallery`` (path-surface-zoning 廊道 zone);
      3. ``moon_platform.cells`` → ``under_eave``;
      4. the 1-cell Chebyshev ring around every ``BuildingSlot.footprint`` →
         ``heart`` (path-surface-zoning 天井/院心 zone);
      5. cells inside a building footprint are tagged ``interior`` so the yard
         fill skips them (the building pass owns those cells).

    The ``under_eave`` kind is the residual eave classification (e.g. a
    ``moon_platform`` not promoted to a finer zone). ``gallery`` and ``heart``
    are the finer zones the surface-zoning model promotes from the old unified
    ``under_eave``; their union is byte-identical to the pre-zoning
    ``under_eave`` set when the style resolves them to the same block, and the
    resolve step (:func:`_ground_zone_slot`) falls back to
    ``GROUND_YARD_UNDER_EAVE`` for any style that lacks a finer slot.
    """
    kinds: Dict[Cell2, str] = {cell: "open_sky" for cell in _lot_interior_cells(compound)}
    gallery_cells = compound.node_cells("covered_gallery")
    for cell in gallery_cells:
        if cell in kinds:
            kinds[cell] = "gallery"
    moon_cells = compound.node_cells("moon_platform")
    for cell in moon_cells:
        if cell in kinds:
            kinds[cell] = "under_eave"
    for slot in compound.building_slots:
        ring = _chebyshev_ring(slot.footprint)
        for cell in ring:
            if cell in kinds and kinds[cell] not in ("interior", "gallery"):
                kinds[cell] = "heart"
        for cell in slot.footprint & kinds.keys():
            kinds[cell] = "interior"
    # Service alley (path-surface-zoning 夹道 zone): the 2-cell strip between a
    # south-anchored 倒座/front_row footprint and the perimeter wall. Recorded
    # explicitly by the front-row placement as an ``alley`` parcel node; if
    # absent the alley zone is simply empty (small-courtyard / no front-row).
    for cell in compound.node_cells("alley"):
        if cell in kinds and kinds[cell] not in ("interior",):
            kinds[cell] = "alley"
    return kinds


def _ground_zone_slot(style: Style, kind: str) -> str:
    """Resolve a ground ``kind`` to its surface-zone slot's primary block.

    The surface-zoning model (path-surface-zoning spec) maps four ground zones
    to four style slots: ``gallery`` → ``PATH_GALLERY``, ``heart`` →
    ``GROUND_YARD_HEART``, ``alley`` → ``PATH_ALLEY``, ``under_eave`` →
    ``GROUND_YARD_UNDER_EAVE`` (residual eave). ``open_sky`` →
    ``GROUND_YARD_OPEN``.

    Each finer zone falls back to ``GROUND_YARD_UNDER_EAVE`` when the style
    lacks the finer slot, so styles that did not adopt the surface-zoning slots
    (e.g. a style that only carries ``GROUND_YARD_OPEN`` /
    ``GROUND_YARD_UNDER_EAVE``) regenerate byte-identical to the pre-zoning
    behaviour.
    """
    if kind == "gallery":
        if style.has_slot("PATH_GALLERY"):
            return style.primary("PATH_GALLERY")
        return style.primary("GROUND_YARD_UNDER_EAVE")
    if kind == "heart":
        if style.has_slot("GROUND_YARD_HEART"):
            return style.primary("GROUND_YARD_HEART")
        return style.primary("GROUND_YARD_UNDER_EAVE")
    if kind == "alley":
        if style.has_slot("PATH_ALLEY"):
            return style.primary("PATH_ALLEY")
        return style.primary("GROUND_YARD_UNDER_EAVE")
    if kind == "under_eave":
        return style.primary("GROUND_YARD_UNDER_EAVE")
    return style.primary("GROUND_YARD_OPEN")


def _place_yard_ground(compound: CompoundGraph, style: Style) -> None:
    """Yard-fill pass: solid ground at every non-building parcel cell.

    ``open_sky`` cells resolve through ``GROUND_YARD_OPEN``; the eave-classified
    cells (``gallery`` / ``heart`` / ``alley`` / residual ``under_eave``) resolve
    through their surface-zone slots via :func:`_ground_zone_slot`. The path pass
    (run afterwards) overwrites the ground tile along the routed path at the same
    y. Water / planting / tree cells are left for their own passes to own.

    In the 江南大宅, this pass runs BEFORE ``_layout_garden`` so the garden
    features (假山 foot / 水池) stamp their own y=-1 surface on top of the yard
    ground; that keeps the ground layer hole-free under the rockery's air gaps
    while letting the hero foot row and pool survive (add-hero-rockery task 4.1).
    """
    buildings = compound.building_cells()
    skip = (buildings |
            compound.node_cells("water_feature", "water_jar", "planting",
                                "courtyard_tree"))
    # Mansion 主院 plinth only: the open-plinth cells stay bare PLATFORM_STONE
    # (a raised stone floor), so the yard-ground pass skips them. The
    # chinese_courtyard plinth instead wants the open/under-eave ground tile on
    # those cells (its validator checks the ground kind), so it is NOT skipped —
    # keeping the courtyard family byte-stable under the enclosure rewrite (D5).
    if compound.meta.get("layout_strategy") == "mansion_enclosure":
        skip |= compound.node_cells("platform") - buildings
    kinds = _derive_ground_kinds(compound)
    open_block = style.primary("GROUND_YARD_OPEN")
    eave_block = style.primary("GROUND_YARD_UNDER_EAVE")
    written: Set[Cell2] = set()
    for cell in _lot_interior_cells(compound):
        if cell in skip:
            continue
        kind = kinds.get(cell, "open_sky")
        if kind == "open_sky":
            state = open_block
        else:
            state = _ground_zone_slot(style, kind)
        y = _natural_surface_y(compound, cell)
        compound.grid.set((cell[0], y, cell[1]), state, ["DETAIL", "GROUND"],
                          PRIORITY["DETAIL"], "GROUND_YARD")
        written.add(cell)
    compound.parcel_nodes.append(
        ParcelNode("yard_ground", "yard_ground", written, {
            "open_block": open_block, "eave_block": eave_block,
        }))


# ---------------------------------------------------------------------------
# Garden parcel renderers (garden-rockery runtime side). These realize the
# 假山 / 水池 / 亭 / 汀步 parcels for a 江南大宅 花园 band (the chinese_mansion
# family, task 6). Each renderer is a standalone function the mansion's 花园
# layout will call with a bbox; they are NOT wired into chinese_courtyard (which
# has no garden). The parcel machinery (ParcelNode + grid.set) matches the
# existing landscape renderers (_place_landscape / _put_cells).
# ---------------------------------------------------------------------------


def _rockery_block_state(variant: str, facing: str, moss: str,
                         waterlogged: bool = False) -> str:
    """Blockstate string for a placed rockery_block (mod-decor-block-family).

    Includes the ``waterlogged`` property (add-hero-rockery task 2.5); defaults
    to ``false`` for the generic heightfield placements.
    """
    wl = "true" if waterlogged else "false"
    return (f"myvillage:rockery_block[variant={variant},facing={facing},"
            f"moss_level={moss},waterlogged={wl}]")


def place_hero_rockery(compound: CompoundGraph, origin: Cell2, seed: int,
                       facing: str = "north",
                       base_y: Optional[int] = None) -> ParcelNode:
    """Hero 假山 cluster renderer (add-hero-rockery tasks 3.3/3.4).

    Stamps the 19-cell stacked cluster + foliage/water dressing produced by
    :func:`rockery.derive_hero_rockery` at ``origin`` (cluster's -x/-z corner),
    resting the foot on the parcel ground surface. Fixes the spike-field bug
    (the cells stack 3 tall instead of scattering) and exposes a standable summit
    in the node meta for a possible 亭. Determinism: the source JSON is fixed.

    ``base_y`` overrides the natural surface y — used for 水心假山 (island
    rockery) where the base must sit at y=0 (above the y=-1 pond water) so the
    cluster rises from the pond as an island instead of sinking into the water.
    """
    from .rockery import derive_hero_rockery  # local import (avoid cycle)
    plan = derive_hero_rockery()
    ox, oz = origin
    base_y = _natural_surface_y(compound, (ox + 1, oz + 1)) if base_y is None else base_y
    cells: Set[Cell2] = set()
    written: List[List] = []
    for (dx, dy, dz), (variant, moss, f, wl) in sorted(plan.cells.items()):
        x, y, z = ox + dx, base_y + dy, oz + dz
        compound.grid.set((x, y, z), _rockery_block_state(variant, f, moss, wl),
                          ["DETAIL", "STRUCTURE"], PRIORITY["DETAIL"], "ROCKERY_STONE",
                          force=True)
        cells.add((x, z))
        written.append([x, z, variant, moss, dy, wl])
    for (dx, dy, dz), state in plan.dressing:
        is_water = state.startswith("minecraft:water")
        # When the rockery sits as a 水心假山 (island, base_y >= 0), skip its own
        # 山脚水池 dressing: that pool was meant to read as the rockery's ground-
        # level foot bath on dry land, but on an island the surrounding pond
        # already supplies the water, and the dressing would place 3 water
        # sources at y=0 — one block ABOVE the pond's y=-1 surface — reading as
        # floating high water ("高于地面的水") off the island's +z edge.
        if is_water and base_y is not None and base_y >= 0:
            continue
        x, y, z = ox + dx, base_y + dy, oz + dz
        tags = ["DETAIL", "GROUND"] if is_water else ["DETAIL", "STRUCTURE"]
        slot = "WATER" if is_water else (
            "ROCKERY_STONE" if state.startswith("myvillage:rockery_cascade") else "PLANTING")
        compound.grid.set((x, y, z), state, tags, PRIORITY["DETAIL"], slot, force=True)
        if is_water:
            # Carve the cell above the surface pool to air so the water shows
            # (the yard-ground pass may have filled it); the 细瀑 ribbon is placed
            # afterwards in the dressing order and reclaims its own cells.
            compound.grid.set((x, y + 1, z), AIR, ["AIR_CARVE"],
                              PRIORITY["AIR_CARVE"], force=True)
        cells.add((x, z))
    sx, sy, sz = plan.summit
    node = ParcelNode("garden_rockery", "garden_rockery", cells, {
        "bbox": [ox, oz, ox + plan.footprint[0] - 1, oz + plan.footprint[1] - 1],
        "facing": facing,
        "hero": "taihu",
        "summit": [ox + sx, base_y + sy, oz + sz],
        "placement": written,
        "cell_count": len(cells),
    })
    compound.parcel_nodes.append(node)
    return node


def generate_hero_rockery_fragment(seed: int = 0x4A1A5A) -> CompoundGraph:
    """Self-contained hero 假山 review fragment (add-hero-rockery task 4.0).

    Builds a minimal :class:`CompoundGraph` carrying nothing but the hero
    cluster + its foliage/water dressing on a small contained ground+basin slab,
    so ``/myvillage place hero_rockery`` stamps a standalone specimen that holds
    its 山脚水池 without an external ``garden_pond``. The slab is a 2-cell-thick
    foundation (stone floor + grass surface) over the footprint plus a 2-cell
    margin; the foot pool is a 1-deep pocket carved into the surface layer, so
    its four sides and floor are solid and the water sources cannot spill.

    Deterministic: the source sculpt is fixed, so the fragment is byte-stable.
    """
    variant = CompoundVariant(
        courtyard_size="garden_fragment", water_form="rockery_pool",
        planting_layout="single", roof_grade="none", gate_type="none")
    compound = CompoundGraph(style_id="chinese_courtyard", seed=seed,
                             variant=variant, lot_size=(7, 8), axis_x=3)
    from .rockery import derive_hero_rockery  # local import (avoid cycle)
    plan = derive_hero_rockery()
    width, depth = plan.footprint
    # Ground+basin slab: a stone foundation at y=-2 and a grass surface at y=-1
    # over the footprint (x 0..width-1, z 0..depth) plus a 2-cell margin. The
    # hero foot and the surface pool both rest at y=-1; place_hero_rockery stamps
    # the rock (force) and the pool (force + carve the cell above to air) on top,
    # so the surrounding grass at y=-1 forms the basin walls and the stone at y=-2
    # the floor — the pool is fully contained without an external garden_pond.
    margin = 2
    foundation = "minecraft:stone"
    surface = "minecraft:grass_block"
    for x in range(-margin, width + margin):
        for z in range(-margin, depth + 1 + margin):
            compound.grid.set((x, -2, z), foundation, ["FOUNDATION"],
                              PRIORITY["FOUNDATION"], "GROUND_YARD")
            compound.grid.set((x, -1, z), surface, ["DETAIL", "GROUND"],
                              PRIORITY["DETAIL"], "GROUND_YARD")
    place_hero_rockery(compound, (0, 0), seed)
    return compound


def place_garden_rockery(compound: CompoundGraph, bbox: Tuple[int, int, int, int],
                         seed: int, facing: str = "north",
                         hero: Optional[str] = None,
                         base_y: Optional[int] = None) -> ParcelNode:
    """garden_rockery parcel renderer (task 5.2).

    Calls :func:`tools.buildgen.rockery.derive_rockery` to assign each cell in
    ``bbox`` a variant + moss_level by the heightfield, then writes a
    ``myvillage:rockery_block`` at each cell's standable y (the natural surface
    y, so the rockery rests on the garden ground). Returns the ParcelNode
    describing the placed rockery (cells + the role/variant manifest in meta).

    When ``hero == "taihu"`` the parcel is instead realized as the hand-sculpted
    hero 假山 cluster (add-hero-rockery task 3.4), rooted at the bbox's -x/-z
    corner — the cluster is a fixed 3×3×3 sculpt, so the bbox only supplies the
    origin, not the footprint.
    """
    if hero == "taihu":
        return place_hero_rockery(compound, (bbox[0], bbox[1]), seed, facing,
                                  base_y=base_y)
    from .rockery import derive_rockery, RockeryParams  # local import (avoid cycle)
    placement = derive_rockery(seed, bbox, RockeryParams())
    cells: Set[Cell2] = set()
    written: List[List] = []
    for (x, z), (variant, moss) in placement.items():
        y = _natural_surface_y(compound, (x, z))
        compound.grid.set((x, y, z), _rockery_block_state(variant, facing, moss),
                          ["DETAIL", "STRUCTURE"], PRIORITY["DETAIL"], "ROCKERY_STONE")
        cells.add((x, z))
        written.append([x, z, variant, moss])
    node = ParcelNode("garden_rockery", "garden_rockery", cells, {
        "bbox": list(bbox),
        "facing": facing,
        "placement": written,
        "cell_count": len(cells),
    })
    compound.parcel_nodes.append(node)
    return node


def _freeform_pond(compound: CompoundGraph, bbox: Tuple[int, int, int, int],
                   seed: int) -> Set[Cell2]:
    """garden_pond freeform shoreline (task 5.3).

    2D value-noise binalization over the bbox: each cell evaluates a
    deterministic noise; cells above a threshold are water, below are shore.
    Isolated 1-2 cell pockets are filled (water→land, land→water) so there are
    no unreachable islands or odd puddles. Returns the set of water cells (the
    renderer writes ``minecraft:water`` at y=-1 inside the shoreline).
    """
    try:
        from .sect_mountain import _hash2, _noise
    except ImportError:  # pragma: no cover
        from sect_mountain import _hash2, _noise
    x0, z0, x1, z1 = bbox
    lot_w, lot_d = compound.lot_size
    # Binalize: noise in [-2, 2]; water iff noise >= 0 (roughly half the bbox,
    # shoreline-tuned by the radial bias below).
    raw: Set[Cell2] = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (1 <= x < lot_w - 1 and 1 <= z < lot_d - 1):
                continue
            # radial bias toward the bbox center: center cells more likely water,
            # edge cells more likely shore, so the pond reads as a body not a
            # checkerboard.
            cx = (x0 + x1) / 2
            cz = (z0 + z1) / 2
            dx = abs(x - cx) / max(1, (x1 - x0) / 2)
            dz = abs(z - cz) / max(1, (z1 - z0) / 2)
            dist = max(dx, dz)
            n = _noise(seed, x, z, 2)
            if n >= int(-2 + 2 * dist):  # center: threshold -2 (almost always water); edge: 0
                raw.add((x, z))
    # Fill isolated 1-2 cell pockets (water→land, land→water).
    raw = _fill_isolated_pockets(raw, bbox, lot_w, lot_d, pocket_size=2)
    return raw


def _fill_isolated_pockets(water: Set[Cell2], bbox: Tuple[int, int, int, int],
                           lot_w: int, lot_d: int, pocket_size: int) -> Set[Cell2]:
    """Fill 1-N cell isolated pockets (spec: water pocket → land, land → water).

    A pocket is a connected component (4-neighbor) of water-or-land cells that
    is fully surrounded by the opposite kind and has ≤ ``pocket_size`` cells.
    """
    x0, z0, x1, z1 = bbox
    all_cells = {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)
                 if 1 <= x < lot_w - 1 and 1 <= z < lot_d - 1}

    def neighbors(c: Cell2) -> List[Cell2]:
        x, z = c
        return [(nx, nz) for nx, nz in ((x+1, z), (x-1, z), (x, z+1), (x, z-1))
                if (nx, nz) in all_cells]

    def flood(start: Cell2, kind: Set[Cell2]) -> Tuple[Set[Cell2], bool]:
        """Flood-fill the component of ``start`` in ``kind``; return (component,
        touches_boundary). touches_boundary=True means the component reaches the
        bbox edge (so it's open, not an isolated pocket)."""
        comp = {start}
        stack = [start]
        edge = False
        while stack:
            c = stack.pop()
            for nb in neighbors(c):
                if nb in kind and nb not in comp:
                    comp.add(nb)
                    stack.append(nb)
                # touching a non-bbox cell means we reached the outside → open
            if c[0] <= x0 or c[0] >= x1 or c[1] <= z0 or c[1] >= z1:
                edge = True
        return comp, edge

    out = set(water)
    land = all_cells - water
    # Check water pockets (small water components not touching the bbox edge).
    visited_w: Set[Cell2] = set()
    for c in sorted(water):
        if c in visited_w:
            continue
        comp, edge = flood(c, water)
        visited_w |= comp
        if not edge and len(comp) <= pocket_size:
            out -= comp  # water pocket → land
    # Check land pockets (small land components surrounded by water).
    visited_l: Set[Cell2] = set()
    for c in sorted(land):
        if c in visited_l:
            continue
        comp, edge = flood(c, land)
        visited_l |= comp
        if not edge and len(comp) <= pocket_size:
            out |= comp  # land pocket → water
    return out


def place_garden_pond(compound: CompoundGraph, bbox: Tuple[int, int, int, int],
                      seed: int, rockery_node: Optional[ParcelNode] = None,
                      facing: str = "north") -> Tuple[ParcelNode, Set[Cell2]]:
    """garden_pond parcel renderer (task 5.4).

    Writes ``minecraft:water`` at y=-1 inside the freeform shoreline, plus a
    ParcelNode. 山脚入水 composition: if a ``rockery_node`` is provided, its
    base-role cells that meet the pond boundary are re-placed as base variants
    on top of the water at y=0+ (the rockery base rests in the water, reading
    as 山脚入水 — the water stays at y=-1 below).
    """
    from .rockery import derive_rockery, RockeryParams, ROLE_BASE
    water = _freeform_pond(compound, bbox, seed)
    blocked = (compound.building_cells() |
               compound.node_cells("perimeter_wall", "screen_wall", "planting",
                                   "water_jar", "courtyard_tree", "inner_gate"))
    # 水心假山 (island rockery): if a hero rockery sits in the pond, exclude its
    # footprint from the water so it rises from the pond as an island instead of
    # being carved away by the AIR_CARVE below. Generic (land-based) rockeries
    # have no cells in the pond bbox, so this is a no-op for the old composition.
    if rockery_node is not None:
        blocked |= set(rockery_node.cells)
    water &= {c for c in water if c not in blocked}
    for x, z in water:
        # Clear any ground/path blocks then write water at y=-1.
        compound.grid.set((x, -1, z), "minecraft:water",
                          ["DETAIL", "GROUND"], PRIORITY["DETAIL"], "WATER", force=True)
        compound.grid.set((x, 0, z), AIR, ["AIR_CARVE"],
                          PRIORITY["AIR_CARVE"], force=True)
    node = ParcelNode("garden_pond", "garden_pond", set(water), {
        "bbox": list(bbox),
        "water_cell_count": len(water),
    })
    compound.parcel_nodes.append(node)
    # 山脚入水: where the rockery's base-role footprint meets the pond boundary,
    # place base variants on top of the water (y=0+).
    if rockery_node is not None:
        placement = {tuple([m[0], m[1]]): (m[2], m[3])
                     for m in rockery_node.meta.get("placement", [])}
        shore_ring = _chebyshev_ring(water) & {c for c in _lot_interior_cells(compound)}
        for cell in shore_ring:
            if cell in placement:
                variant, moss = placement[cell]
                # Only base-role variants sit in the water (slope/peak stay on land).
                if variant.startswith("base_"):
                    x, z = cell
                    compound.grid.set((x, 0, z),
                                      _rockery_block_state(variant, facing, moss),
                                      ["DETAIL", "STRUCTURE"], PRIORITY["DETAIL"],
                                      "ROCKERY_STONE", force=True)

    # 睡莲 (lily pads) on the pond surface — a sparse, deterministic scattering of
    # minecraft:lily_pad at y=0 (sitting on the water surface one block above the
    # y=-1 water source). Vanilla lily pads are flat non-solid blocks, so they
    # neither block the pond's walkability check nor obscure the island 假山.
    # Density is intentionally low and capped; they avoid the rockery footprint
    # and the immediate shore ring so the water edge stays clean. Later passes
    # prune them around the bridge/gallery clear lanes.
    if water:
        import random as _rng
        rnd = _rng.Random(seed ^ 0x4C494C59)
        shore = _chebyshev_ring(water)
        candidates = [c for c in water if c not in shore]
        if rockery_node is not None:
            rock_cells = set(rockery_node.cells)
            candidates = [c for c in candidates if c not in rock_cells]
        # Also skip cells directly adjacent to the rockery so pads don't hug the
        # island base (keeps a clear water moat around the 假山).
        if rockery_node is not None:
            rock_ring = _chebyshev_ring(rock_cells)
            candidates = [c for c in candidates if c not in rock_ring]
        rnd.shuffle(candidates)
        target = min(12, max(2, len(water) // 10))
        for (x, z) in candidates[:target]:
            compound.grid.set((x, 0, z), "minecraft:lily_pad",
                              ["DETAIL", "GROUND"], PRIORITY["DETAIL"],
                              "POND_PLANTING", force=True)
    return node, water


def _clear_lily_pads(compound: CompoundGraph, cells: Set[Cell2]) -> None:
    """Remove surface lily pads from reserved bridge/gallery water lanes."""
    for x, z in cells:
        if compound.grid.state_at((x, 0, z)).split("[", 1)[0] == "minecraft:lily_pad":
            compound.grid.set((x, 0, z), AIR, ["AIR_CARVE"],
                              PRIORITY["AIR_CARVE"], force=True)


def place_garden_pavilion(compound: CompoundGraph, center: Cell2, size: int,
                          base_y: int, style: Style,
                          roof_grade: str = "chinese_round_ridge") -> ParcelNode:
    """garden_pavilion parcel renderer (task 5.5).

    A small open-sided 亭: 4 standoff columns at the COLUMN slot (one per
    corner of a ``size``×``size`` plan), a ``chinese_round_ridge`` (卷棚) roof
    slab capping the columns, no walls. Supports standalone-on-ground
    (``base_y`` = ground surface) and on-rockery-peak (``base_y`` = the
    rockery's standable top). Reuses the cultivation pavilion geometry pattern
    (4 columns + open eave) per the garden-rockery spec.
    """
    cx, cz = center
    half = size // 2
    column = style.primary("COLUMN")
    roof = style.slot_entry("ROOF_DARK", "_slab")
    cap = style.slot_entry("ROOF_DARK", "_stairs")
    cells: Set[Cell2] = set()
    corners = [(cx - half, cz - half), (cx + half, cz - half),
               (cx - half, cz + half), (cx + half, cz + half)]
    # 4 standoff columns, 3 tall (eave height), carrying the roof.
    for (x, z) in corners:
        for y in range(base_y + 1, base_y + 4):
            compound.grid.set((x, y, z), column, ["DETAIL", "STRUCTURE"],
                              PRIORITY["DETAIL"], "COLUMN")
        cells.add((x, z))
    # Roof slab (chinese_round_ridge 卷棚 reads as a slab cap on 4 columns).
    for x in range(cx - half, cx + half + 1):
        for z in range(cz - half, cz + half + 1):
            compound.grid.set((x, base_y + 4, z), roof, ["DETAIL", "ROOF"],
                              PRIORITY["DETAIL"], "ROOF_DARK")
            cells.add((x, z))
    # Ridge cap running across the center (the 卷棚 ridge).
    ridge_axis = "x" if size >= 3 else "x"
    for x in range(cx - half, cx + half + 1):
        compound.grid.set((x, base_y + 5, cz), cap, ["DETAIL", "ROOF"],
                          PRIORITY["DETAIL"], "ROOF_DARK")
    node = ParcelNode("garden_pavilion", "garden_pavilion", cells, {
        "center": list(center),
        "size": size,
        "base_y": base_y,
        "roof_grade": roof_grade,
        "ridge_axis": ridge_axis,
        "open_sided": True,
        "columns": [list(c) for c in corners],
    })
    compound.parcel_nodes.append(node)
    return node


def _place_covered_gallery_3d(compound: "CompoundGraph", style: "Style",
                              cells: Set[Cell2], base_y: int,
                              open_side: Optional[str],
                              post_side: Optional[str] = None,
                              rail_on_footprint: bool = False,
                              roof_form: str = "single_slope") -> None:
    """3D covered-gallery renderer (path-surface-zoning Arc 5).

    A 廊 is a real building, not a floor tile. The gallery footprint is treated
    as a strip with two long edges: the **open side** (facing water/yard) gets a
    密排 `BALUSTRADE` railing just outside it, and the **post side** (facing the
    building/wall) carries the `COLUMN` posts so the posts do not block the
    walkway in front of doors. Per gallery cell this writes four layers:

    - a `PATH_GALLERY` floor (the surface-zone material is preserved);
    - a `COLUMN` post every other cell along the **post-side** column line, 2
      tall (reused from ``_place_covered_galleries``);
    - a `BALUSTRADE` fence row 密排 on the **open-side** edge (reused from
      ``ops.balustrade``);
    - a single-slope `ROOF_DARK` roof capping the columns, low toward the open
      side.

    Shared by the 水边廊 (pond-shore gallery, open side = water) and the mansion
    主院 抄手游廊 (open side = yard, post side = the wing wall). The walkway
    column between the post line and the railing stays clear of doors because the
    posts hug the building edge, not the walkway.

    Parameters
    ----------
    cells:
        The gallery footprint (2D x,z cells).
    base_y:
        The floor y.
    open_side:
        Which long edge faces open space ("north"/"south"/"east"/"west"); the
        balustrade lines that edge and the roof slopes down toward it. ``None``
        balustrades both long edges.
    post_side:
        Which long edge carries the column posts (defaults to the side opposite
        ``open_side``). The posts hug this edge so the walkway clears doors.
    roof_form:
        ``"single_slope"`` (default) writes a single-slope ROOF_DARK stairs roof,
        low toward ``open_side``.
    """
    column = style.primary("COLUMN")
    # BALUSTRADE is an OPTIONAL slot; fall back to a DETAIL_WOOD fence so a
    # style without BALUSTRADE still gets a railing (mansion defines BALUSTRADE).
    rail = (style.primary("BALUSTRADE") if style.has_slot("BALUSTRADE")
            else style.slot_entry("DETAIL_WOOD", "fence", "minecraft:oak_fence"))
    roof_stairs = style.slot_entry("ROOF_DARK", "_stairs", "minecraft:dark_oak_stairs")
    roof_y = base_y + 3
    column_top = base_y + 2  # columns are 2 tall: base_y+1 .. base_y+2

    delta = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    opp = {"north": "south", "south": "north", "east": "west", "west": "east"}
    if post_side is None:
        post_side = opp[open_side] if open_side else None

    # Classify each gallery cell by which "column line" it is on relative to the
    # post side. Posts go on the SECOND column from the post side (one cell in
    # from the building edge), NOT the boundary column — the boundary column is
    # the door-front walkway that must stay clear (a wing's door opens onto it),
    # so posts must not stand there.
    def _is_post_line(x: int, z: int) -> bool:
        if post_side is None:
            return False
        dx, dz = delta[post_side]
        # One step toward post_side must still be in the gallery (this cell is
        # the second column in), AND two steps out must leave it (this is the
        # innermost column that still "faces" the building).
        one_in = (x + dx, z + dz)
        if one_in not in cells:
            return False  # boundary column — door walkway, keep clear
        return (one_in[0] + dx, one_in[1] + dz) not in cells

    # Stairs face the low/open side (step descends toward open_side).
    stairs_facing = open_side or "north"

    ordered = sorted(cells)
    post_idx = 0
    for _run_idx, (x, z) in enumerate(ordered):
        # Floor (PATH_GALLERY — surface-zone material preserved).
        compound.grid.set((x, base_y, z), style.primary("PATH_GALLERY"),
                          ["DETAIL", "GROUND", "PROTECTED"], PRIORITY["DETAIL"],
                          "PATH_GALLERY", force=True)
        # Column posts on the post-side line only, every other cell along the run.
        if _is_post_line(x, z):
            if post_idx % 2 == 0:
                for y in range(base_y + 1, column_top + 1):
                    compound.grid.set((x, y, z), column, ["DETAIL", "STRUCTURE"],
                                      PRIORITY["DETAIL"], "COLUMN")
            post_idx += 1
        # Single-slope roof: a bottom-half stairs descending toward open_side.
        compound.grid.set((x, roof_y, z),
                          f"{roof_stairs}[facing={stairs_facing},half=bottom]",
                          ["DETAIL", "ROOF"], PRIORITY["DETAIL"], "ROOF_DARK")

    # Balustrade on the open edge(s). Main-yard galleries keep the rail just
    # outside the footprint so the walkway stays clear. Waterside galleries set
    # rail_on_footprint=True so the rail is supported by gallery floor instead
    # of floating over pond water.
    edges: Set[Cell2] = set()
    sides = (open_side,) if open_side else (
        ("north", "south") if (max(z for _, z in cells) - min(z for _, z in cells))
        >= (max(x for x, _ in cells) - min(x for x, _ in cells)) else ("east", "west"))
    for side in sides:
        dx, dz = delta[side]
        for (x, z) in cells:
            edge = (x + dx, z + dz)
            if edge not in cells:
                edges.add((x, z) if rail_on_footprint else edge)
    for (x, z) in edges:
        compound.grid.set((x, base_y + 1, z), rail,
                          ["DETAIL", "PROTECTED"], PRIORITY["DETAIL"], "BALUSTRADE")


def place_moon_gate_screen(compound: CompoundGraph, style: Style,
                           axis: int, wall_z: int, x_lo: int, x_hi: int,
                           height: int = 6, gate_radius: int = 2
                           ) -> Tuple[ParcelNode, Cell2]:
    """月洞门 garden screen wall + passage (path-surface-zoning task 2.2/2.3).

    Builds a free-standing screen wall spanning ``[x_lo, x_hi]`` at row ``wall_z``
    (the boundary between 后院 and 花园), with a 圆洞门 (moon gate) — a circular
    opening carved at the central axis — so the player passes *through* a real
    hole into the garden. The wall is a base + plaster stack + ridge cap, matching
    the 照壁 / perimeter wall register. The opening is the ``moon_gate`` motif
    (radius cells of AIR ringed by a DETAIL_WOOD frame) carved around the axis so
    the sightline through the gate frames the garden.

    Returns ``(screen_node, passage_inner_cell)`` where ``passage_inner_cell`` is
    the first 花园-side cell through the gate (one cell south of the wall, on the
    axis) — this is the tour route's first waypoint (the material boundary per
    design D6: cells before the wall are formal PATH_FORMAL, cells after are tour
    PATH_TOUR).
    """
    base = style.primary("PLATFORM_STONE")
    wall_main = style.primary("WALL_MAIN")
    cap = style.slot_entry("ROOF_DARK", "_stairs")
    frame = style.slot_entry("DETAIL_WOOD", "_trapdoor", base)
    cells: Set[Cell2] = set()
    for x in range(x_lo, x_hi + 1):
        compound.grid.set((x, 0, wall_z), base, ["STRUCTURE"],
                          PRIORITY["STRUCTURE"], "PLATFORM_STONE")
        for y in range(1, height + 1):
            compound.grid.set((x, y, wall_z), wall_main, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "WALL_MAIN")
        compound.grid.set((x, height, wall_z), cap, ["ROOF"], PRIORITY["ROOF"],
                          "ROOF_DARK")
        cells.add((x, wall_z))
    # Carve the 圆洞门: a vertical circle of AIR centred on the axis, ringed by a
    # DETAIL_WOOD frame (the moon_gate motif). radius=2 → a 5-wide, 5-tall circle
    # the player walks through at floor level.
    cy_center = height // 2
    for dx in range(-gate_radius - 1, gate_radius + 2):
        for dy in range(-gate_radius - 1, gate_radius + 2):
            dist = dx * dx + dy * dy
            gx, gy = axis + dx, cy_center + dy
            if not (x_lo <= gx <= x_hi and 0 < gy < height):
                continue
            if dist <= gate_radius * gate_radius:
                compound.grid.set((gx, gy, wall_z), AIR,
                                  ["OPENING", "AIR_CARVE", "PROTECTED"],
                                  PRIORITY["OPENING"], force=True)
            elif dist <= (gate_radius + 1) * (gate_radius + 1):
                compound.grid.set((gx, gy, wall_z), frame, ["DETAIL", "OPENING"],
                                  PRIORITY["OPENING"], "DETAIL_WOOD")
    node = ParcelNode("moon_gate_passage", "moon_gate_passage", cells, {
        "wall_z": wall_z, "axis_x": axis, "radius": gate_radius,
        "height": height, "form": "moon_gate",
        "passage": [[axis, wall_z]],
    })
    compound.parcel_nodes.append(node)
    # The 花园 sits at higher z (south frame: deeper z = further in). The passage
    # inner cell is one step into the garden from the wall.
    passage_inner = (axis, wall_z + 1)
    return node, passage_inner


def place_stepping_stones(compound: CompoundGraph, shore_a: Cell2, shore_b: Cell2,
                          seed: int, facing: str = "north") -> ParcelNode:
    """汀步 (stepping stones) across a garden_pond (task 5.6).

    Places flat ``minecraft:stone`` cells at standable y connecting two shore
    points, so the pond is voxel-walkable as a path across the water. Reuses
    :func:`rockery.derive_stepping_stones` for the path geometry (which cell is
    a step) but renders each step as a plain vanilla stone, NOT a
    ``rockery_block``: each ``rockery_block[variant=standalone]`` renders as an
    independent mini-mountain, so a path of them read in-game as a row of
    stone-textured spikes ("一列小尖刺") instead of flat 汀步. Moss is applied
    deterministically so some steps read as weathered (mossy_cobblestone) for
    variety, but the silhouette stays a flat, low, walkable stone.
    """
    from .rockery import derive_stepping_stones
    placement = derive_stepping_stones(seed, shore_a, shore_b)
    cells: Set[Cell2] = set()
    for (x, z), (variant, moss) in placement.items():
        # 汀步 sit at the water surface (y=0) so the player auto-steps across.
        # Use mossy_cobblestone for 'heavy'/'light' moss picks (weathered steps),
        # plain stone otherwise — both are flat vanilla full blocks, walkable,
        # and read as 汀步 rather than as mini-mountains.
        block = ("minecraft:mossy_cobblestone" if moss in ("light", "heavy")
                 else "minecraft:stone")
        compound.grid.set((x, 0, z), block,
                          ["DETAIL", "STRUCTURE"], PRIORITY["DETAIL"],
                          "POND_STONE", force=True)
        cells.add((x, z))
    node = ParcelNode("garden_stepping_stones", "garden_stepping_stones", cells, {
        "shore_a": list(shore_a),
        "shore_b": list(shore_b),
        "facing": facing,
        "cell_count": len(cells),
    })
    compound.parcel_nodes.append(node)
    return node


# ---------------------------------------------------------------------------
# 一进四合院 layout (Step 2). The compound is built in a canonical south frame
# (street gate at z=0, the axis running inward toward higher z). The lot splits
# into an outer yard (外院), a 垂花门 (inner gate) band, and a main yard (主院),
# with 影壁 / 抄手游廊 / 月台 nodes. `main_orientation` other than `south`
# (north / east) is carried on the variant but not yet realized — see the
# deferred roadmap; `_resolve_gate_side` gates that.
# ---------------------------------------------------------------------------


def _resolve_gate_side(main_orientation: str) -> str:
    if main_orientation == "south":
        return "south"
    raise NotImplementedError(
        f"main_orientation={main_orientation!r} not yet realized "
        "(north/east deferred per docs/ai-kb deferred roadmap §E)")


def _compute_yard_bands(jin_count: int, layout_type: str, lot_d: int) -> dict:
    """Depth bands in the canonical south frame (gate at z=0, inward +z).

    ``jin_count=1`` reproduces the one-courtyard behavior; ``jin_count=3``
    adds the 江南大宅 3-进 sequence (前院→仪门→主院→二门→后院→花园).
    ``jin_count=4`` is reserved for the deferred deeper-garden extension.
    """
    if jin_count == 1:
        outer_depth = 6 if layout_type == "mu" else 13
        oy0 = 1
        oy1 = oy0 + outer_depth - 1
        ig0 = oy1 + 1
        ig1 = ig0 + 2           # 3-deep 垂花门 band
        my0 = ig1 + 1
        my1 = lot_d - 2         # last interior row before the north wall
        return {
            "gate_z": 0,
            "outer_yard_band": (oy0, oy1),
            "inner_gate_band": (ig0, ig1),
            "main_yard_band": (my0, my1),
        }
    elif jin_count == 3:
        # 3-进: 前院 → 仪门 → 主院 → 二门 → 后院 → 花园
        ig_depth = 3          # each inner gate is exactly 3 cells deep
        usable = lot_d - 2    # interior cells (excluding the two perimeter walls)
        yard_pool = usable - 2 * ig_depth
        # Proportional split (front=20%, main=30%, back=22%, garden=28%)
        front_d  = max(8, int(yard_pool * 0.20))
        main_d   = max(8, int(yard_pool * 0.30))
        back_d   = max(8, int(yard_pool * 0.22))
        garden_d = max(8, yard_pool - front_d - main_d - back_d)
        fy0 = 1
        fy1 = fy0 + front_d - 1
        yi0 = fy1 + 1
        yi1 = yi0 + ig_depth - 1  # 仪门 band
        my0 = yi1 + 1
        my1 = my0 + main_d - 1
        er0 = my1 + 1
        er1 = er0 + ig_depth - 1  # 二门 band
        by0 = er1 + 1
        by1 = by0 + back_d - 1
        gy0 = by1 + 1
        gy1 = lot_d - 2           # garden extends to the north wall
        return {
            "gate_z": 0,
            "front_yard_band": (fy0, fy1),
            "yimen_band":       (yi0, yi1),
            "main_yard_band":   (my0, my1),
            "ermen_band":       (er0, er1),
            "back_yard_band":   (by0, by1),
            "garden_band":      (gy0, gy1),
        }
    else:
        raise ValueError(
            f"jin_count={jin_count} not yet realized "
            "(4-进 is a deferred extension per docs/ai-kb deferred roadmap §E)"
        )


def _add_chinese_perimeter(compound: CompoundGraph, style: Style,
                           gate_house_gap: Optional[Tuple[int, int]] = None) -> None:
    """Walled perimeter with a cap ridge, 墙垛 corner/interval piers, and
    optional 漏窗 cutouts. Built from y=0 up so it never floats over a raised
    main-yard platform.

    ``gate_house_gap`` (x0, x1): when set (mansion enclosure path), the south
    wall is gapped across [x0, x1] so the gate_house through-building fills that
    span — its own side walls seal the gap. When None (courtyard path), the
    classic carved-air gate opening on the axis is used instead.
    """
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_side = compound.meta.get("gate_side", "south")
    gate_z = 0 if gate_side == "south" else lot_d - 1

    if gate_house_gap is not None:
        # Mansion: gap the south wall for the gate_house; the gate_house's own
        # side walls close the span, so the perimeter stays sealed except through
        # the gate_house passage (mansion-gate-house spec).
        gx0, gx1 = gate_house_gap
        gate = {(x, gate_z) for x in range(gx0, gx1 + 1)}
    else:
        gate_half = GATE_HALF[compound.variant.gate_type]
        gate = {(x, gate_z) for x in range(axis - gate_half, axis + gate_half + 1)}

    wall_cells: Set[Cell2] = set()
    for x in range(lot_w):
        for z in (0, lot_d - 1):
            if (x, z) not in gate:
                wall_cells.add((x, z))
    for z in range(lot_d):
        wall_cells.add((0, z))
        wall_cells.add((lot_w - 1, z))

    base = style.primary("BASE_STONE")
    wall_main = style.primary("WALL_MAIN")
    cap = style.slot_entry("ROOF_DARK", "_stairs")
    lattice = style.optional_slot_entry("DETAIL_WOOD", "fence",
                                        style.primary("FRAME_WOOD"))

    corners = {(0, 0), (lot_w - 1, 0), (0, lot_d - 1), (lot_w - 1, lot_d - 1)}
    piers = set(corners)
    for x in range(0, lot_w, 6):
        piers.add((x, 0))
        piers.add((x, lot_d - 1))
    for z in range(0, lot_d, 6):
        piers.add((0, z))
        piers.add((lot_w - 1, z))
    piers &= wall_cells

    rng = random.Random(compound.seed + 4242)
    louvres: Set[Cell2] = set()
    for x, z in wall_cells:
        compound.grid.set((x, 0, z), base, ["STRUCTURE"],
                          PRIORITY["STRUCTURE"], "BASE_STONE")
        for y in range(1, 4):
            compound.grid.set((x, y, z), wall_main, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "WALL_MAIN")
        if (x, z) in piers:
            compound.grid.set((x, 4, z), base, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "BASE_STONE")
            compound.grid.set((x, 5, z), cap, ["ROOF"], PRIORITY["ROOF"],
                              "ROOF_DARK")
        else:
            compound.grid.set((x, 4, z), cap, ["ROOF"], PRIORITY["ROOF"],
                              "ROOF_DARK")
            if lattice and (x, z) not in gate and rng.random() < 0.07:
                compound.grid.set((x, 2, z), lattice, ["DETAIL"],
                                  PRIORITY["DETAIL"], "DETAIL_WOOD", force=True)
                louvres.add((x, z))

    compound.parcel_nodes.append(
        ParcelNode("perimeter_wall", "perimeter_wall", wall_cells, {
            "gate_opening": [
                min(x for x, _ in gate), min(z for _, z in gate),
                max(x for x, _ in gate), max(z for _, z in gate),
            ],
            "gate_side": gate_side,
            "gate_type": compound.variant.gate_type,
            "has_cap_ridge": True,
            "piers": [list(c) for c in sorted(piers)],
            "louvre_windows": [list(c) for c in sorted(louvres)],
        }))


def _screen_panel_cells(axis: int, z: int, side: str, width: int = 2) -> Set[Cell2]:
    """Off-axis 照壁 / 影壁 panel cells.

    The screen wall stands to one side of the central axis (江南 照壁侧立 form,
    per design D2), never on it. ``side`` ∈ {"east", "west"} picks which flank;
    ``width`` (1-2) is the panel's cell span. The panel sits immediately
    adjacent to the axis (``axis ± 1`` .. ``axis ± width``) so an oblique
    sightline from the gate still intersects it, but the central-axis column
    stays open for the 3D voxel-walkability STANDABLE rule.
    """
    if side == "east":
        return {(axis + 1 + i, z) for i in range(width)}
    return {(axis - 1 - i, z) for i in range(width)}


def _front_row_origin_x(axis: int, front_w: int, lot_w: int,
                        alley_side: str) -> int:
    """X-origin for the 倒座 (front_row) footprint that leaves a 1-2 cell alley
    on ``alley_side`` between the footprint and the perimeter wall.

    The footprint is pushed OFF the central axis toward the side opposite the
    alley, so the alley column (1-2 cells wide along the perimeter wall) is the
    off-axis circulation route from the gate area to the 仪门 area. The
    footprint never crosses the central axis (it stays entirely on the
    non-alley side), so the axis column itself also stays walkable.
    """
    # Keep a 2-cell alley against the perimeter wall on the chosen side; the
    # footprint is pushed toward the opposite half. The perimeter wall sits at
    # x=0 / x=lot_w-1, so the interior edge is x=1 / x=lot_w-2.
    alley_w = 2
    if alley_side == "east":
        # Footprint occupies the west half: origin so its east edge stops
        # short of (or at) the axis, leaving the east half + alley open.
        return axis - front_w
    # alley_side == "west": footprint occupies the east half, west half + alley open.
    return axis + 1


def _layout_outer_yard(compound: CompoundGraph, style: Style, bands: dict,
                       contexts: Dict[str, BuildContext]) -> None:
    """照壁/影壁 (screen wall) standing OFF-axis inside the gate + 倒座
    (front_row) along the south wall with a side alley (omitted for 三合院).

    Per design D2 + the `courtyard-voxel-walkability` fix, the screen wall is
    placed to one side of the central axis (照壁侧立), blocking the sightline to
    the 垂花门 / main hall WITHOUT sealing the axis — the previous on-axis 影壁
    was the root cause of the player-facing "堵住" complaint. ``meta.form``
    distinguishes 北京 jingbi vs 江南 zhaobi where the form differs; both
    families now ship the side-standing form.
    """
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    oy0, oy1 = bands["outer_yard_band"]

    # 影壁 / 照壁: free-standing screen wall facing the gate, standing OFF the
    # central axis. Side (east/west) is seed-decided so the layout reads as
    # intentional asymmetry, not a fixed offset.
    screen_rng = random.Random(compound.seed + 707)
    screen_side = screen_rng.choice(("east", "west"))
    screen_width = screen_rng.choice((1, 2))
    screen_z = oy0 + 1
    screen_cells = _screen_panel_cells(axis, screen_z, screen_side, screen_width)
    base = style.primary("PLATFORM_STONE")
    wall_main = style.primary("WALL_MAIN")
    cap = style.slot_entry("ROOF_DARK", "_stairs")
    for x, z in screen_cells:
        compound.grid.set((x, 0, z), base, ["STRUCTURE"],
                          PRIORITY["STRUCTURE"], "PLATFORM_STONE")
        for y in range(1, 6):
            compound.grid.set((x, y, z), wall_main, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "WALL_MAIN")
        compound.grid.set((x, 6, z), cap, ["ROOF"], PRIORITY["ROOF"],
                          "ROOF_DARK")
    compound.parcel_nodes.append(
        ParcelNode("screen_wall", "screen_wall", screen_cells, {
            "height": 6, "on_axis": False, "facing_gate": True,
            "form": "jingbi", "side": screen_side, "width": screen_width}))

    # 倒座 (front_row): a set-back outer-yard range facing the gate. Only the
    # `standard` plan carries it — 三合院 omits it (wings extend forward to close
    # the U), and the 目字 (mu) plan's narrow outer band has no room for it.
    #
    # The 倒座 MUST leave a 1-2 cell side alley between its footprint and the
    # perimeter wall on at least one side (the opposite side of the 照壁) so
    # off-axis circulation from the gate to the 仪门 stays open — without this
    # the central axis is the only route and the off-axis 照壁 panel would
    # force a dead-end detour.
    if compound.variant.layout_type == "standard":
        front_ctx = contexts["front_row"]
        front = front_ctx.graph.get("main")
        alley_side = "west" if screen_side == "east" else "east"
        alley_x = _front_row_origin_x(axis, front.size[0], lot_w, alley_side)
        _translate_context(compound, "front_row", front_ctx,
                           (alley_x, 0, oy0 + 4))


def _layout_inner_gate(compound: CompoundGraph, style: Style,
                       bands: dict,
                       gate_band_key: str = "inner_gate_band",
                       node_id: str = "inner_gate",
                       gate_kind: str = "chuihua_gate") -> None:
    """垂花门 / 仪门 / 二门 on the axis between two yards.

    ``gate_band_key`` selects which band entry to read from ``bands``; defaults
    to ``"inner_gate_band"`` for the one-courtyard layout. Mansion layouts pass
    ``"yimen_band"`` or ``"ermen_band"`` and distinctive ``node_id`` /
    ``gate_kind`` labels.
    """
    axis = compound.axis_x
    ig0, ig1 = bands[gate_band_key]
    base = style.primary("PLATFORM_STONE")
    column = style.primary("COLUMN")
    wall_main = style.primary("WALL_MAIN")
    roof = style.slot_entry("ROOF_DARK", "_slab")

    cells: Set[Cell2] = set()
    # 垂花门 passage: at least 3 cells wide (axis-1, axis, axis+1) for every
    # z in the inner-gate band, so off-axis circulation from the 倒座 side alley
    # routes through the gate without forcing the central axis. The previous
    # axis-only passage was a single point of failure for the path router.
    passage = {(x, z) for x in (axis - 1, axis, axis + 1)
               for z in range(ig0, ig1 + 1)}
    for x in range(axis - 2, axis + 3):
        for z in range(ig0, ig1 + 1):
            cells.add((x, z))
    # 垂莲柱: the four corner posts of the gate carry the signature pendant
    # columns; the central axis stays open as the walk-through passage.
    corners = {(axis - 2, ig0), (axis + 2, ig0), (axis - 2, ig1), (axis + 2, ig1)}
    for x, z in cells:
        compound.grid.set((x, 0, z), base, ["STRUCTURE"],
                          PRIORITY["STRUCTURE"], "PLATFORM_STONE")
        if (x, z) in passage:
            continue
        if (x, z) in corners:
            for y in range(1, 5):
                compound.grid.set((x, y, z), column, ["STRUCTURE"],
                                  PRIORITY["STRUCTURE"], "COLUMN")
        else:
            for y in range(1, 4):
                compound.grid.set((x, y, z), wall_main, ["STRUCTURE"],
                                  PRIORITY["STRUCTURE"], "WALL_MAIN")
    for x in range(axis - 2, axis + 3):
        for z in range(ig0, ig1 + 1):
            compound.grid.set((x, 5, z), roof, ["ROOF"], PRIORITY["ROOF"],
                              "ROOF_DARK")
    compound.parcel_nodes.append(
        ParcelNode(node_id, "inner_gate", cells, {
            "kind": gate_kind,
            "pendant_columns": [list(c) for c in sorted(corners)],
            "passage": [list(c) for c in sorted(passage)],
            "band": [ig0, ig1],
        }))


def _layout_main_yard(compound: CompoundGraph, style: Style, bands: dict,
                      contexts: Dict[str, BuildContext]) -> None:
    """主院: a raised platform tier, two 厢房 (side_wing) flanking, the 正房
    (main_hall) on the axis at the inward end, and a 月台 (moon platform)
    apron in front of it."""
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    oy0, _ = bands["outer_yard_band"]
    my0, my1 = bands["main_yard_band"]
    plinth_h = PLATFORM_TIER_HEIGHT[compound.variant.platform_tier]

    # Main-yard platform tier (台基). Fill solidly from ground so the perimeter
    # never floats and buildings sit on the plinth top.
    platform_stone = style.primary("PLATFORM_STONE")
    plinth_cells: Set[Cell2] = set()
    if plinth_h > 0:
        for x in range(1, lot_w - 1):
            for z in range(my0, my1 + 1):
                plinth_cells.add((x, z))
                for y in range(plinth_h):
                    compound.grid.set((x, y, z), platform_stone,
                                      ["STRUCTURE", "GROUND"],
                                      PRIORITY["STRUCTURE"], "PLATFORM_STONE")
        compound.parcel_nodes.append(
            ParcelNode("main_yard_platform", "platform", plinth_cells, {
                "tier": compound.variant.platform_tier, "height": plinth_h}))

    main = contexts["main_hall"].graph.get("main")
    west = contexts["west_wing"].graph.get("main")
    east = contexts["east_wing"].graph.get("main")

    hall_z1 = lot_d - 3
    hall_z0 = hall_z1 - main.size[2] + 1
    main_slot = _translate_context(
        compound, "main_hall", contexts["main_hall"],
        (axis - main.size[0] // 2, plinth_h, hall_z0))

    if compound.variant.layout_type == "three-sided":
        wing_z0 = oy0 + 1
    else:
        wing_z0 = my0 + 1
    if compound.variant.symmetry == "mild_asymmetry":
        wing_z0 += random.Random(compound.seed + 77).choice([0, 1])
    _translate_context(compound, "west_side_wing", contexts["west_wing"],
                       (2, plinth_h, wing_z0))
    _translate_context(compound, "east_side_wing", contexts["east_wing"],
                       (lot_w - 2 - east.size[0], plinth_h, wing_z0))

    # 月台: stone apron in front of the main hall, raised one cell above the
    # main-yard floor, sized to the hall width.
    moon_depth = 3
    hx0 = min(x for x, _ in main_slot.footprint)
    hx1 = max(x for x, _ in main_slot.footprint)
    moon_cells = {(x, z) for x in range(hx0, hx1 + 1)
                  for z in range(hall_z0 - moon_depth, hall_z0)}
    moon_cells = {(x, z) for x, z in moon_cells if 0 < x < lot_w - 1}
    for x, z in moon_cells:
        for y in range(plinth_h + 1):
            compound.grid.set((x, y, z), platform_stone, ["STRUCTURE", "GROUND"],
                              PRIORITY["STRUCTURE"], "PLATFORM_STONE")
    compound.parcel_nodes.append(
        ParcelNode("moon_platform", "moon_platform", moon_cells, {
            "relative_y": plinth_h + 1, "fronts": "main_hall"}))

    compound.meta["plinth_height"] = plinth_h
    compound.meta["hall_front_z"] = hall_z0


def _place_covered_galleries(compound: CompoundGraph, style: Style,
                             bands: dict) -> None:
    """抄手游廊: two 3-wide × 3-tall roofed galleries (east + west) tying the
    垂花门 flanks to the main-hall flanks along the main-yard edges. Reuses the
    sect covered-gallery geometry (floor + standoff columns + single-eave
    roof)."""
    lot_w, _ = compound.lot_size
    _, ig1 = bands["inner_gate_band"]
    plinth_h = compound.meta.get("plinth_height", 0)
    hall_front = compound.meta["hall_front_z"]

    west_slot = next(s for s in compound.building_slots if s.id == "west_side_wing")
    east_slot = next(s for s in compound.building_slots if s.id == "east_side_wing")
    main_slot = next(s for s in compound.building_slots if s.id == "main_hall")
    west_inner_x = max(x for x, _ in west_slot.footprint) + 1
    east_inner_x = min(x for x, _ in east_slot.footprint) - 1
    hall_x0 = min(x for x, _ in main_slot.footprint)
    hall_x1 = max(x for x, _ in main_slot.footprint)

    column = style.primary("COLUMN")
    floor = style.primary("GROUND_PATH")
    roof = style.slot_entry("ROOF_DARK", "_slab")
    z0 = ig1 + 1
    z1 = hall_front - 1
    if z1 < z0:
        z1 = z0

    for side, inner_x, step in (("west", west_inner_x, 1),
                                ("east", east_inner_x, -1)):
        xs = [inner_x + step * d for d in range(3)]
        xs = [x for x in xs if 0 < x < lot_w - 1]
        cells = {(x, z) for x in xs for z in range(z0, z1 + 1)}
        # 抄手 returns at both ends: the south arm meets the corresponding
        # 垂花门 flank, and the north arm meets the main-hall/月台 flank.
        gate_flank_x = compound.axis_x - 2 if side == "west" else compound.axis_x + 2
        hall_flank_x = hall_x0 if side == "west" else hall_x1
        for arm_z in range(z0, min(z1, z0 + 2) + 1):
            cells.update((x, arm_z) for x in range(
                min(xs[-1], gate_flank_x), max(xs[-1], gate_flank_x) + 1))
        for arm_z in range(max(z0, z1 - 2), z1 + 1):
            cells.update((x, arm_z) for x in range(
                min(xs[-1], hall_flank_x), max(xs[-1], hall_flank_x) + 1))
        outer_x = xs[0]
        for x, z in cells:
            compound.grid.set((x, plinth_h, z), floor, ["DETAIL", "GROUND"],
                              PRIORITY["DETAIL"], "GROUND_PATH")
            if x == outer_x and (z - z0) % 2 == 0:
                for y in range(plinth_h + 1, plinth_h + 3):
                    compound.grid.set((x, y, z), column, ["STRUCTURE"],
                                      PRIORITY["STRUCTURE"], "COLUMN")
            compound.grid.set((x, plinth_h + 3, z), roof, ["ROOF"],
                              PRIORITY["ROOF"], "ROOF_DARK")
        compound.parcel_nodes.append(
            ParcelNode(f"{side}_gallery", "covered_gallery", cells, {
                "side": side, "relative_y": plinth_h,
                "endpoints": ["inner_gate", "main_hall"], "circulation": True}))


def _dress_main_yard(compound: CompoundGraph, style: Style, bands: dict) -> None:
    """院中树 (a center-offset courtyard tree) on the main-yard plinth, plus a
    ground-level 水 / 植栽 feature and a 鱼缸 water jar in the open outer yard
    (the raised main yard has no ground-layer water)."""
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    oy0, oy1 = bands["outer_yard_band"]
    my0, my1 = bands["main_yard_band"]
    plinth_h = compound.meta.get("plinth_height", 0)
    hall_front = compound.meta["hall_front_z"]

    occupied = (compound.building_cells() |
                compound.node_cells("covered_gallery", "moon_platform",
                                    "perimeter_wall", "inner_gate", "screen_wall"))

    # 院中树: offset from the central axis so it never blocks the 垂花门 → hall
    # sightline. Real deciduous foliage (dark_oak) — not the ground-cover
    # PLANTING palette — so it may rise above the plant layer.
    yard_z0 = my0 + 1
    yard_z1 = hall_front - 4
    tree_x = axis + 3
    tree_z = (yard_z0 + yard_z1) // 2
    if yard_z1 >= yard_z0 and (tree_x, tree_z) not in occupied:
        trunk = (tree_x, tree_z)
        compound.grid.set((trunk[0], plinth_h, trunk[1]), "minecraft:dirt",
                          ["DETAIL", "GROUND"], PRIORITY["DETAIL"], "PLANTING")
        for y in range(plinth_h + 1, plinth_h + 5):
            compound.grid.set((trunk[0], y, trunk[1]),
                              "minecraft:dark_oak_log[axis=y]", ["STRUCTURE"],
                              PRIORITY["DETAIL"], "FRAME_WOOD")
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if abs(dx) + abs(dz) > 3:
                    continue
                lx, lz = trunk[0] + dx, trunk[1] + dz
                if not (0 < lx < lot_w - 1 and 0 < lz < lot_d - 1):
                    continue
                compound.grid.set((lx, plinth_h + 5, lz),
                                  "minecraft:dark_oak_leaves[persistent=true]",
                                  ["DETAIL"], PRIORITY["DETAIL"], "FRAME_WOOD")
        compound.parcel_nodes.append(
            ParcelNode("courtyard_tree", "courtyard_tree", {trunk}, {
                "variant": "deciduous", "offset_from_axis": tree_x - axis}))
        occupied |= {trunk}

    # Ground-level 水 + 植栽 in an open outer-yard corner (grid y=-1 water /
    # y=0 planting normalize to the NBT ground / plant layers).
    blocked = occupied | compound.node_cells("path")
    pool = _bounded(_rect(axis + 3, oy0, axis + 5, oy0 + 2),
                    lot_w, lot_d, blocked)
    _clear_cells(compound, pool, y0=-1, y1=1)
    _put_cells(compound, "water_feature", "water_feature", pool,
               style.primary("WATER"), ["DETAIL", "GROUND"], y=-1, slot="WATER")
    blocked |= pool

    plant_states = style.material_slots["PLANTING"]
    planting = _bounded(
        (_rect(axis + 2, oy0, axis + 2, oy0 + 3) |
         _rect(axis + 6, oy0, axis + 6, oy0 + 3)),
        lot_w, lot_d, blocked)
    _clear_cells(compound, planting, y0=0, y1=1)
    for i, (x, z) in enumerate(sorted(planting)):
        compound.grid.set((x, 0, z), plant_states[i % len(plant_states)],
                          ["DETAIL", "GROUND"], PRIORITY["DETAIL"], "PLANTING")
    if planting:
        compound.parcel_nodes.append(
            ParcelNode("planting", "planting", planting))
        blocked |= planting

    # 鱼缸 / 石榴缸: a small water jar at a ground-level outer-yard cell.
    jar = _bounded(_rect(axis - 4, oy0, axis - 4, oy0), lot_w, lot_d, blocked)
    if jar:
        _clear_cells(compound, jar, y0=-1, y1=1)
        for x, z in jar:
            compound.grid.set((x, -1, z), style.primary("WATER"),
                              ["DETAIL", "GROUND"], PRIORITY["DETAIL"], "WATER")
        compound.parcel_nodes.append(
            ParcelNode("water_jar", "water_jar", jar, {"kind": "fish_jar"}))


def _path_blocked_cells(compound: CompoundGraph) -> Set[Cell2]:
    """Cells the multi-source BFS treats as walls.

    Per the courtyard-path-network spec: building footprints (except the door
    front cells themselves, which the BFS stops one cell short of), water /
    planting / tree landscape cells, the perimeter wall, the 影壁, and the
    垂花门's solid flanks (its central passage stays open).
    """
    lot_w, lot_d = compound.lot_size
    door_fronts: Set[Cell2] = set()
    for slot in compound.building_slots:
        if slot.door_info and isinstance(slot.door_info.get("front"), tuple):
            fx, _fy, fz = slot.door_info["front"]
            door_fronts.add((fx, fz))
    blocked = (compound.building_cells() |
               compound.node_cells("perimeter_wall", "screen_wall",
                                   "water_feature", "planting", "water_jar",
                                   "courtyard_tree"))
    # Door-front cells themselves must stay walkable so the BFS can stop one
    # cell short of the door; the path pass later drops them from the write set
    # (so the building's own step block owns the door cell).
    blocked -= door_fronts
    # The 垂花门 solid flanks block, but its central passage stays open.
    inner_gate = next((n for n in compound.parcel_nodes
                       if n.type == "inner_gate"), None)
    if inner_gate:
        passage = {tuple(c) for c in inner_gate.meta.get("passage", [])}
        blocked |= (inner_gate.cells - passage)
    # 3D-aware blocking (courtyard-voxel-walkability): a lot-interior cell
    # whose body or head space above its natural surface is already occupied
    # by a SOLID STRUCTURE/ROOF/COLUMN block (e.g. a main-hall porch column
    # standing on the 月台, or a covered-gallery column) is NOT walkable in
    # voxel space, so the 2D path BFS must route around it — otherwise the
    # path is written under a column the player can never stand under, and the
    # voxel validator flags ``voxel_blocked_by_solid``. Ground/DETAIL cells
    # (the yard ground + path layers themselves) never block; only STRUCTURE /
    # ROOF / COLUMN do. Door-front cells stay walkable (the BFS needs to reach
    # them as endpoints; the write pass drops them).
    solid_blocked = _solid_obstructed_path_cells(compound)
    blocked |= solid_blocked
    blocked -= door_fronts
    return {c for c in blocked if 0 < c[0] < lot_w - 1 and 0 < c[1] < lot_d - 1}


def _solid_obstructed_path_cells(compound: CompoundGraph) -> Set[Cell2]:
    """Lot-interior cells with NO standable y in their column — the 2D path
    BFS must route around them (courtyard-voxel-walkability: a path written on
    a cell the player can never stand on is a real ``voxel_blocked_by_solid``
    defect). Uses :func:`_standable_ys` so a cell whose body+head clearance is
    clear at SOME y (e.g. a 月台 cell where the player stands on top of the
    platform, not at the buried natural surface) stays walkable; only a column
    fully sealed by STRUCTURE/ROOF/COLUMN blocks (a porch-column plinth, a
    column-base under a low eave) is treated as a wall."""
    lot_w, lot_d = compound.lot_size
    out: Set[Cell2] = set()
    for x in range(1, lot_w - 1):
        for z in range(1, lot_d - 1):
            if _standable_ys(compound, x, z):
                continue
            out.add((x, z))
    return out


def _collect_path_endpoints(compound: CompoundGraph) -> List[Cell2]:
    """Endpoint registry for the multi-source BFS (courtyard-path-network spec).

    Sources:
      - perimeter_wall gate opening → first cell one z-step inside the yard;
      - every BuildingSlot with ``door_info`` → the door-front cell (2D);
      - every ``water_feature`` / ``water_jar`` node → its single best adjacent
        walkable cell (water itself is blocked; the path stops one cell short,
        matching the planting rule; one endpoint per node, not per cell, so a
        multi-cell pool produces one approach point);
      - one cell per ``planting`` node adjacent to the planting boundary
        (highest open-neighbour count, deterministic ``(x, z)`` tie-break);
      - the front-most ``moon_platform`` cell (closest to the gate).

    Layout-agnostic: a small-courtyard without a moon platform simply has no
    moon-platform endpoint; the endpoint set reflects what *this* compound has.
    """
    lot_w, lot_d = compound.lot_size
    gate_side = compound.meta.get("gate_side", "south")
    wall = next((n for n in compound.parcel_nodes if n.type == "perimeter_wall"),
                None)
    endpoints: List[Cell2] = []
    # Mansion enclosure path: the entrance is a gate_house through-building, so
    # the path starts at the gate_house's INNER opening (recorded in meta), not
    # at the carved-wall z0+1 cell (which sits inside the gate_house volume).
    gate_inner_z = compound.meta.get("gate_inner_z")
    if wall is not None and gate_inner_z is not None:
        axis = compound.axis_x
        if 0 < axis < lot_w - 1 and 0 < gate_inner_z < lot_d - 1:
            endpoints.append((axis, gate_inner_z))
    elif wall is not None:
        opening = wall.meta.get("gate_opening")
        if opening:
            x0, z0, x1, z1 = opening
            axis = (x0 + x1) // 2
            z_in = z0 + (1 if gate_side == "south" else -1)
            if 0 < axis < lot_w - 1 and 0 < z_in < lot_d - 1:
                endpoints.append((axis, z_in))

    blocked_base = _path_blocked_cells(compound)

    for slot in compound.building_slots:
        if slot.door_info and isinstance(slot.door_info.get("front"), tuple):
            fx, _fy, fz = slot.door_info["front"]
            if 0 < fx < lot_w - 1 and 0 < fz < lot_d - 1:
                endpoints.append((fx, fz))

    # Water / jar nodes are themselves blocked (the player cannot walk on
    # water). One endpoint per node, snapped to the single best adjacent
    # walkable cell across all the node's cells — so a multi-cell pool produces
    # one approach endpoint, not one per cell (per-cell endpoints can land in
    # unreachable pockets walled off by the pool itself). The path stops one
    # cell short of the feature, matching the planting rule.
    for kind in ("water_feature", "water_jar"):
        for node in compound.parcel_nodes:
            if node.type != kind or not node.cells:
                continue
            boundary: Set[Cell2] = set()
            for px, pz in node.cells:
                for nx, nz in ((px + 1, pz), (px - 1, pz), (px, pz + 1), (px, pz - 1)):
                    cand = (nx, nz)
                    if cand in node.cells:
                        continue
                    if not (0 < nx < lot_w - 1 and 0 < nz < lot_d - 1):
                        continue
                    if cand in blocked_base:
                        continue
                    boundary.add(cand)
            if boundary:
                best = sorted(boundary, key=lambda c: (
                    -sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                         if (c[0] + dx, c[1] + dz) not in blocked_base
                         and (c[0] + dx, c[1] + dz) not in node.cells), c))[0]
                endpoints.append(best)
            else:
                # No walkable neighbour: surface the first node cell so the
                # validator's endpoint_unreachable reports it rather than
                # silently dropping the endpoint.
                endpoints.append(sorted(node.cells)[0])

    for node in compound.parcel_nodes:
        if node.type != "planting" or not node.cells:
            continue
        # Pick one boundary cell per planting node — the adjacent cell with the
        # most open 4-neighbours. Deterministic (x, z) tie-break (spec).
        boundary = set()
        for px, pz in node.cells:
            for nx, nz in ((px + 1, pz), (px - 1, pz), (px, pz + 1), (px, pz - 1)):
                cand = (nx, nz)
                if cand in node.cells:
                    continue
                if not (0 < nx < lot_w - 1 and 0 < nz < lot_d - 1):
                    continue
                if cand in blocked_base:
                    continue
                boundary.add(cand)
        if boundary:
            best = sorted(boundary, key=lambda c: (
                -sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                     if (c[0] + dx, c[1] + dz) not in blocked_base
                     and (c[0] + dx, c[1] + dz) not in node.cells), c))[0]
            endpoints.append(best)

    moon_nodes = [n for n in compound.parcel_nodes if n.type == "moon_platform"
                  and n.cells]
    if moon_nodes:
        moon = moon_nodes[0].cells
        axis = compound.axis_x
        if gate_side == "north":
            best_z = max(c[1] for c in moon)
        else:
            best_z = min(c[1] for c in moon)
        # Among cells on the gateway-facing row, prefer axis_x (colonnade
        # columns may occupy off-axis positions on the same row).
        front_row_cells = [(x, z) for x, z in moon if z == best_z]
        moon_cell = min(front_row_cells, key=lambda c: abs(c[0] - axis))
        if (0 < moon_cell[0] < lot_w - 1 and 0 < moon_cell[1] < lot_d - 1):
            endpoints.append(moon_cell)

    # De-duplicate while preserving a stable order.
    seen: Set[Cell2] = set()
    unique: List[Cell2] = []
    for cell in endpoints:
        if cell not in seen:
            seen.add(cell)
            unique.append(cell)
    return unique


def _multi_source_bfs(endpoints: Sequence[Cell2], blocked: Set[Cell2],
                      lot_w: int, lot_d: int) -> Dict[Cell2, int]:
    """Multi-source BFS from every endpoint simultaneously.

    Returns a ``dict`` mapping every reached cell to its distance from the
    nearest endpoint. Cells in ``blocked`` are excluded (left unreached). All
    endpoints start at distance 0. The BFS is bounded to the lot interior.

    Used only by the validators' ``endpoint_unreachable`` invariant: every
    endpoint must be reachable in the 2D cell graph. It does NOT drive path
    paving — :func:`_route_complete_path` paves a single-source backbone from
    the street-gate entry (see :func:`_single_source_bfs_pred`).
    """
    dist: Dict[Cell2, int] = {}
    q: deque = deque()
    for cell in endpoints:
        if not (1 <= cell[0] <= lot_w - 2 and 1 <= cell[1] <= lot_d - 2):
            continue
        if cell in blocked:
            continue
        if cell not in dist:
            dist[cell] = 0
            q.append(cell)
    while q:
        x, z = q.popleft()
        d = dist[(x, z)]
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if not (1 <= nx <= lot_w - 2 and 1 <= nz <= lot_d - 2):
                continue
            if (nx, nz) in blocked or (nx, nz) in dist:
                continue
            dist[(nx, nz)] = d + 1
            q.append((nx, nz))
    return dist


def _door_front_cells(compound: CompoundGraph) -> Set[Cell2]:
    fronts: Set[Cell2] = set()
    for slot in compound.building_slots:
        if slot.door_info and isinstance(slot.door_info.get("front"), tuple):
            fx, _fy, fz = slot.door_info["front"]
            fronts.add((fx, fz))
    return fronts


def _single_source_bfs_pred(root: Cell2, blocked: Set[Cell2],
                            lot_w: int, lot_d: int
                            ) -> Dict[Cell2, Optional[Cell2]]:
    """Single-source BFS from ``root``, returning the predecessor map.

    ``pred`` maps every reached cell to the cell it was first reached from (its
    parent in the shortest-path tree rooted at ``root``). The root itself has
    ``pred == None``. This is the tree the backbone tracer walks: from any
    reached cell, following ``pred`` returns to ``root`` along a shortest path,
    and because ``root`` is the single street-gate entry, those per-endpoint
    paths actually traverse the yard and cross the plinth boundary — unlike the
    multi-source predecessor map, whose sources sit next to each other and
    degenerate into a handful of disconnected cells.
    """
    pred: Dict[Cell2, Optional[Cell2]] = {root: None}
    q: deque = deque([root])
    while q:
        x, z = q.popleft()
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if not (1 <= nx <= lot_w - 2 and 1 <= nz <= lot_d - 2):
                continue
            if (nx, nz) in blocked or (nx, nz) in pred:
                continue
            pred[(nx, nz)] = (x, z)
            q.append((nx, nz))
    return pred


def _trace_backbone(endpoints: Sequence[Cell2],
                    pred: Dict[Cell2, Optional[Cell2]]) -> Set[Cell2]:
    """Shortest-path backbone from the street gate to every endpoint.

    For each endpoint, follow ``pred`` back to the root (the cell whose
    ``pred is None``). The union of those per-endpoint paths is a connected
    shortest-path tree rooted at the street-gate entry — the cells that actually
    get paved as the path. This replaces the older "pave every BFS-reached cell"
    behaviour, which flooded the whole yard with gravel and buried the
    ``GROUND_YARD_OPEN`` grass.
    """
    backbone: Set[Cell2] = set()
    for endpoint in endpoints:
        if endpoint not in pred:
            continue
        cell: Optional[Cell2] = endpoint
        # Guard against a malformed pred cycle (should not happen for a BFS
        # tree) so this can never loop forever.
        guard = 0
        while cell is not None:
            backbone.add(cell)
            nxt = pred.get(cell)
            if nxt == cell:
                break
            cell = nxt
            guard += 1
            if guard > len(pred) + 2:
                break
    return backbone


def _route_complete_path(compound: CompoundGraph, style: Style) -> None:
    """Path network (courtyard-path-network spec).

    Collects endpoints, asserts every endpoint is reachable from the street-gate
    entry via a single-source BFS (raises ``ValueError`` on an unreachable
    endpoint — preserved fast-fail behavior), then writes the path block along
    the **shortest-path backbone** (the union of per-endpoint predecessor-traced
    paths back to the street gate) at each cell's natural surface y. Only the
    backbone is paved, so the open-yard grass / under-eave stone of
    :func:`_place_yard_ground` survives off the path. Because the backbone is
    rooted at the gate entry, it necessarily crosses the plinth boundary, so the
    band-transition stair pass still bridges the main-yard plinth. Door-front
    cells are dropped from the write set so the building's own step block owns
    the door cell.
    """
    lot_w, lot_d = compound.lot_size
    endpoints = _collect_path_endpoints(compound)
    blocked = _path_blocked_cells(compound)
    # Multi-source reachability is still reported (reached_cell_count) for the
    # validator's endpoint_unreachable invariant and the library report.
    reached = _multi_source_bfs(endpoints, blocked, lot_w, lot_d)
    for cell in endpoints:
        if cell not in reached:
            raise ValueError(f"endpoint_unreachable: {cell}")

    # The street-gate entry is the first endpoint (perimeter-wall gate opening,
    # one cell inside the yard). The backbone is the shortest-path tree rooted
    # there — every endpoint traces back to it, crossing any plinth boundary.
    gate_entry = endpoints[0]
    pred = _single_source_bfs_pred(gate_entry, blocked, lot_w, lot_d)
    backbone = _trace_backbone(endpoints, pred)
    door_fronts = _door_front_cells(compound)
    # Formal backbone resolves through PATH_FORMAL (path-surface-zoning spec,
    # 中轴通途 / 规整青石路), falling back to GROUND_PATH when the style did not
    # adopt the surface-zoning slots — byte-stable for styles that only carry
    # GROUND_PATH (e.g. cultivation_sect / medieval, which do not run this pass
    # but share the style machinery).
    path_block = (style.primary("PATH_FORMAL")
                  if style.has_slot("PATH_FORMAL")
                  else style.primary("GROUND_PATH"))
    written: Set[Cell2] = set()
    for cell in backbone:
        if cell in door_fronts:
            continue
        y = _natural_surface_y(compound, cell)
        compound.grid.set((cell[0], y, cell[1]), path_block,
                          ["DETAIL", "GROUND", "PROTECTED"], PRIORITY["DETAIL"],
                          "PATH_FORMAL", force=True)
        written.add(cell)
    compound.parcel_nodes.append(
        ParcelNode("path_network", "path", written, {
            "endpoint_count": len(endpoints),
            "endpoint_cells": [list(c) for c in endpoints],
            "reached_cell_count": len(reached),
            "backbone_cell_count": len(backbone),
            "algorithm": "multi_source_bfs",
        }))


def _collect_tour_waypoints(compound: CompoundGraph, start_cell: Cell2
                            ) -> List[Cell2]:
    """Scenic waypoints for the tour route (path-surface-zoning task 2.1).

    Each waypoint is a **free, dry, standable** lot-interior cell adjacent to a
    garden feature — the path approaches the feature from dry land, never
    stepping onto water or the rockery island (the 水心假山 sits in the pond and is
    approached by viewing it across the water, not walking onto it). The waypoint
    sequence is rockery view-point → pond shore → 亭, anchored at ``start_cell``
    (the 月洞门 passage inner cell — the material boundary per design D6), so the
    formal/tour cell intersection is empty by construction.

    A feature that is absent, or whose adjacent dry cells are all blocked, yields
    no waypoint for it; the function returns at least ``[start_cell]``.
    """
    lot_w, lot_d = compound.lot_size
    blocked = _path_blocked_cells(compound)
    # The mansion's garden features (garden_pond / garden_rockery / garden_pavilion)
    # are NOT in ``_path_blocked_cells`` (the formal path never enters the garden),
    # so a tour waypoint must treat them as obstacles too — a dry waypoint cannot
    # sit on a pond cell or a rockery cell.
    obstacles = compound.node_cells("garden_rockery", "garden_pond",
                                    "garden_pavilion")
    forbidden = blocked | obstacles
    waypoints: List[Cell2] = [start_cell]

    def _is_free(cell: Cell2) -> bool:
        x, z = cell
        if not (1 <= x <= lot_w - 2 and 1 <= z <= lot_d - 2):
            return False
        return cell not in forbidden

    # Reachable set from start over free (non-forbidden) cells. A waypoint on the
    # far side of the pond (e.g. an island rockery's dry ring) is NOT reachable by
    # walking, so it is excluded — the island is viewed from across the water.
    reachable: Set[Cell2] = set()
    if _is_free(start_cell):
        reachable = {start_cell}
        rq: deque = deque([start_cell])
        while rq:
            x, z = rq.popleft()
            for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if (nx, nz) in reachable or not _is_free((nx, nz)):
                    continue
                reachable.add((nx, nz))
                rq.append((nx, nz))

    def _dry_ring(feature_cells: Set[Cell2]) -> Set[Cell2]:
        """The 4-neighbour dry cells around a feature's footprint."""
        ring: Set[Cell2] = set()
        for (fx, fz) in feature_cells:
            for nx, nz in ((fx + 1, fz), (fx - 1, fz), (fx, fz + 1), (fx, fz - 1)):
                if (nx, nz) not in feature_cells:
                    ring.add((nx, nz))
        return {c for c in ring if _is_free(c)}

    def _nearest(seed: Cell2, candidates: Set[Cell2]) -> Optional[Cell2]:
        if not candidates:
            return None
        return min(candidates,
                   key=lambda c: (abs(c[0] - seed[0]) + abs(c[1] - seed[1]), c))

    # Rockery view-point: a dry, reachable cell adjacent to the rockery's south
    # face (the side facing the entrance). The rockery is often a 水心假山 (island),
    # so its dry ring is unreachable — in that case the rockery is viewed from
    # across the pond shore, and this waypoint is skipped.
    rockery = next((n for n in compound.parcel_nodes
                    if n.type == "garden_rockery"), None)
    if rockery is not None:
        dry = _dry_ring(rockery.cells) & reachable
        south_dry = {c for c in dry if all(c[1] <= rz for _, rz in rockery.cells)}
        pick = _nearest(start_cell, south_dry) or _nearest(start_cell, dry)
        if pick is not None:
            waypoints.append(pick)

    pavilion = next((n for n in compound.parcel_nodes
                     if n.type == "garden_pavilion"), None)
    pavilion_dry = (_dry_ring(pavilion.cells) & reachable
                    if pavilion is not None and pavilion.cells else set())

    # Pond shore: the dry, reachable cell adjacent to the pond nearest the
    # previous waypoint. Prefer a shore cell that is not also the pavilion's
    # approach cell; once the pavilion is correctly waterside, those rings can
    # touch, and picking the same cell degenerates the tour into a straight line.
    pond = next((n for n in compound.parcel_nodes
                 if n.type == "garden_pond"), None)
    if pond is not None and pond.cells:
        dry = _dry_ring(pond.cells) & reachable
        pick = _nearest(waypoints[-1], dry - pavilion_dry) or _nearest(waypoints[-1], dry)
        if pick is not None:
            waypoints.append(pick)

    # 亭 (pavilion): a dry, reachable cell adjacent to the pavilion, approached
    # from the previous waypoint.
    if pavilion is not None and pavilion.cells:
        dry = pavilion_dry
        pick = _nearest(waypoints[-1], dry)
        if pick is not None:
            waypoints.append(pick)

    # De-duplicate consecutive waypoints (a feature whose dry ring collapses to
    # the same cell as the prior waypoint would otherwise add a zero-length leg).
    unique: List[Cell2] = []
    for w in waypoints:
        if not unique or unique[-1] != w:
            unique.append(w)
    return unique


def _route_tour_path(compound: CompoundGraph, style: Style,
                     start_cell: Cell2) -> Optional[Set[Cell2]]:
    """Tour route: a waypoint polyline of single-source shortest-path segments
    (path-surface-zoning task 2.1, design D4).

    Routes a polyline through the scenic waypoints (see
    :func:`_collect_tour_waypoints`), where each segment between consecutive
    waypoints is a single-source shortest-path tree over the same ``blocked`` set
    as the formal BFS, plus an obstacle set (the rockery + pond + pavilion cells)
    that forces any segment that would otherwise cut straight through a feature
    to route around it. The tour is a separate path set from the formal backbone:
    it is written through ``PATH_TOUR`` (mossy stone bricks) and the material
    boundary lives at the 月洞门 passage (design D6).

    Returns the set of tour cells (or ``None`` if the style has no ``PATH_TOUR``
    slot / the compound has no garden features to wind through). Each segment is
    a connected single-source tree so :func:`validate_mansion` can assert
    per-segment connectivity.
    """
    if not style.has_slot("PATH_TOUR"):
        return None
    lot_w, lot_d = compound.lot_size
    waypoints = _collect_tour_waypoints(compound, start_cell)
    if len(waypoints) < 2:
        return None
    # Obstacle set: the garden features the tour must wind around (not cut
    # through). Buildings are already blocked by _path_blocked_cells. The formal
    # backbone is NOT added to the blocked set here: routing around it would
    # fragment the tour path where the formal path pins the garden edge. Instead
    # the formal/tour cell sets are kept disjoint by dropping formal-overlap cells
    # from the *write* set (the formal block wins there), while the full tour cell
    # set — including the overlap cells — stays recorded so the tour path remains
    # a single connected polyline.
    base_blocked = _path_blocked_cells(compound)
    obstacles = set()
    for ftype in ("garden_rockery", "garden_pond", "garden_pavilion"):
        obstacles |= compound.node_cells(ftype)
    blocked = base_blocked | obstacles
    # Route each consecutive segment as a single-source shortest path.
    tour_cells: Set[Cell2] = set()
    for src, dst in zip(waypoints, waypoints[1:]):
        if src == dst:
            continue
        # The source waypoint itself may sit on an obstacle cell (e.g. a shore
        # cell adjacent to water); temporarily un-block the endpoints so the BFS
        # can start/end there.
        seg_blocked = blocked - {src, dst}
        pred = _single_source_bfs_pred(src, seg_blocked, lot_w, lot_d)
        if dst not in pred:
            # Fall back to the base blocked set (no obstacles) so the segment
            # still connects even if the obstacle set over-tightened routing.
            pred = _single_source_bfs_pred(src, base_blocked - {src, dst},
                                           lot_w, lot_d)
            if dst not in pred:
                continue
        cell: Optional[Cell2] = dst
        guard = 0
        while cell is not None:
            tour_cells.add(cell)
            nxt = pred.get(cell)
            if nxt == cell:
                break
            cell = nxt
            guard += 1
            if guard > len(pred) + 2:
                break
    # Write the tour overlay through PATH_TOUR. Drop formal-overlap /
    # door-front / obstacle cells from the *write* set (the formal block wins on
    # its backbone; the building owns its door step; obstacles are features the
    # path winds around), but keep the FULL tour cell set recorded on the parcel
    # so the tour path stays a single connected polyline for the validator's
    # segment-connectivity check. The formal/tour WRITE overlap is thus empty by
    # construction; the recorded set carries the (overlap) bridge cells that make
    # the polyline continuous.
    formal = compound.node_cells("path")
    door_fronts = _door_front_cells(compound)
    tour_block = style.primary("PATH_TOUR")
    written: Set[Cell2] = set()
    for cell in tour_cells:
        if cell in formal or cell in door_fronts or cell in obstacles:
            continue
        y = _natural_surface_y(compound, cell)
        compound.grid.set((cell[0], y, cell[1]), tour_block,
                          ["DETAIL", "GROUND", "PROTECTED"], PRIORITY["DETAIL"],
                          "PATH_TOUR", force=True)
        written.add(cell)
    if written:
        compound.parcel_nodes.append(
            ParcelNode("tour_path", "tour_path", tour_cells, {
                "waypoints": [list(c) for c in waypoints],
                "segment_count": max(0, len(waypoints) - 1),
                "cell_count": len(tour_cells),
                "written_count": len(written),
                "written_cells": [list(c) for c in sorted(written)],
                "algorithm": "waypoint_polyline",
            }))
    return written


_STAIR_FACING_FROM_DELTA = {
    (0, 1): "north",   # stair rises from south (low) to north (high plinth)
    (0, -1): "south",
    (1, 0): "west",
    (-1, 0): "east",
}


# --- Voxel-walkability helpers (courtyard-voxel-walkability spec) -------------
#
# A 3D replacement for the 2D-cell multi-source BFS reachability check. A real
# Minecraft player walks in voxel space: a cell is STANDABLE iff the block below
# is SOLID (foot support) and the body + head blocks are NON-SOLID, and two
# STANDABLE cells are STEP-ADJACENT iff they are 4-neighbours in (x, z) with a
# y-difference <= 1 (auto-step up 1, free-fall any). The 2D check passed
# compounds the player experienced as "堵住" (blocked); this 3D check catches
# those — see the design doc's 影壁封轴 + 3-block-cliff analysis.

# Block ids (no [props]) that are NON-SOLID: the player body/head passes
# through them. Carries the vanilla passable decorations listed in the spec.
# Anything not in this set is SOLID (stairs, slabs, walls, columns, plaques,
# rockery blocks, leaves when persistent, etc.).
NON_SOLID_STATES: Set[str] = {
    "minecraft:air",
    "minecraft:water",
    "minecraft:lava",
    "minecraft:torch",
    "minecraft:soul_torch",
    "minecraft:wall_torch",
    "minecraft:soul_wall_torch",
    "minecraft:redstone_torch",
    "minecraft:redstone_wall_torch",
    "minecraft:oak_sign",
    "minecraft:spruce_sign",
    "minecraft:birch_sign",
    "minecraft:jungle_sign",
    "minecraft:acacia_sign",
    "minecraft:dark_oak_sign",
    "minecraft:mangrove_sign",
    "minecraft:cherry_sign",
    "minecraft:bamboo_sign",
    "minecraft:crimson_sign",
    "minecraft:warped_sign",
    "minecraft:oak_wall_sign",
    "minecraft:spruce_wall_sign",
    "minecraft:birch_wall_sign",
    "minecraft:jungle_wall_sign",
    "minecraft:acacia_wall_sign",
    "minecraft:dark_oak_wall_sign",
    "minecraft:mangrove_wall_sign",
    "minecraft:cherry_wall_sign",
    "minecraft:bamboo_wall_sign",
    "minecraft:crimson_wall_sign",
    "minecraft:warped_wall_sign",
    "minecraft:stone_button",
    "minecraft:oak_button",
    "minecraft:spruce_button",
    "minecraft:birch_button",
    "minecraft:jungle_button",
    "minecraft:acacia_button",
    "minecraft:dark_oak_button",
    "minecraft:mangrove_button",
    "minecraft:cherry_button",
    "minecraft:bamboo_button",
    "minecraft:crimson_button",
    "minecraft:warped_button",
    "minecraft:polished_blackstone_button",
    "minecraft:lever",
    "minecraft:rail",
    "minecraft:powered_rail",
    "minecraft:detector_rail",
    "minecraft:activator_rail",
    "minecraft:white_carpet",
    "minecraft:orange_carpet",
    "minecraft:magenta_carpet",
    "minecraft:light_blue_carpet",
    "minecraft:yellow_carpet",
    "minecraft:lime_carpet",
    "minecraft:pink_carpet",
    "minecraft:gray_carpet",
    "minecraft:light_gray_carpet",
    "minecraft:cyan_carpet",
    "minecraft:purple_carpet",
    "minecraft:blue_carpet",
    "minecraft:brown_carpet",
    "minecraft:green_carpet",
    "minecraft:red_carpet",
    "minecraft:black_carpet",
    "minecraft:moss_carpet",
    "minecraft:dandelion",
    "minecraft:poppy",
    "minecraft:blue_orchid",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:wither_rose",
    "minecraft:torchflower",
    "minecraft:oak_sapling",
    "minecraft:spruce_sapling",
    "minecraft:birch_sapling",
    "minecraft:jungle_sapling",
    "minecraft:acacia_sapling",
    "minecraft:dark_oak_sapling",
    "minecraft:cherry_sapling",
    "minecraft:mangrove_propagule",
    "minecraft:bamboo_sapling",
    "minecraft:grass",
    "minecraft:tall_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:vine",
    "minecraft:glow_lichen",
    "minecraft:dead_bush",
    "minecraft:lantern",
    "minecraft:soul_lantern",
    "minecraft:beetroots",
    "minecraft:carrots",
    "minecraft:potatoes",
    "minecraft:wheat",
    "minecraft:melon_stem",
    "minecraft:pumpkin_stem",
    "minecraft:torchflower_crop",
    "minecraft:pitcher_crop",
    "minecraft:sweet_berry_bush",
    "minecraft:cobweb",
    "minecraft:snow",
    "minecraft:lily_pad",
    "minecraft:brown_mushroom",
    "minecraft:red_mushroom",
    "minecraft:small_dripleaf",
    "minecraft:big_dripleaf_stem",
    "minecraft:hanging_roots",
    "minecraft:spore_blossom",
    "minecraft:azalea",
    "minecraft:flowering_azalea",
    "minecraft:mangrove_roots",
}


def is_solid(state: str) -> bool:
    """True iff a full-state string reads as a SOLID voxel.

    SOLID means "provides foot support / blocks body+head clearance". Anything
    not in :data:`NON_SOLID_STATES` is SOLID — including stairs, slabs, walls,
    fences, columns, plaques, leaves (when persistent), and the mod's own
    decorative blocks. The block id is the part before any ``[props]``.
    """
    if not state:
        return False
    block_id = state.split("[", 1)[0]
    return block_id not in NON_SOLID_STATES


def _standable_ys(compound: CompoundGraph, x: int, z: int,
                  y_lo: int = -3, y_hi: int = 12) -> List[int]:
    """Every y in ``[y_lo, y_hi]`` where the autostep STANDABLE rule holds.

    A cell ``(x, y, z)`` is STANDABLE iff the block at ``y-1`` is SOLID (foot
    support) and the blocks at ``y`` and ``y+1`` are NON-SOLID (body + head
    clearance). Returns the standable y-values in ascending order.

    For walkability a door is treated as NON-SOLID (a door is a passage, not a
    wall — the player opens and walks through it), so the formal path can pass
    through a building's doorway. This is walkability-only; the global
    :func:`is_solid` (used by wall/ground checks) still treats doors as solid.
    """
    def _walk_solid(state: Optional[str]) -> bool:
        if not state:
            return False
        bid = state.split("[", 1)[0]
        if bid.endswith("_door"):
            return False
        return is_solid(state)

    out: List[int] = []
    for y in range(y_lo, y_hi + 1):
        if not _walk_solid(compound.grid.state_at((x, y - 1, z))):
            continue
        if _walk_solid(compound.grid.state_at((x, y, z))):
            continue
        if _walk_solid(compound.grid.state_at((x, y + 1, z))):
            continue
        out.append(y)
    return out


def _voxel_walk_bfs(compound: CompoundGraph, start_xyz: Pos,
                    lot_w: int, lot_d: int) -> Set[Pos]:
    """3D STEP-ADJACENT BFS over STANDABLE cells, bounded by the lot interior.

    From the gate-entry STANDABLE cell, visit every STANDABLE cell reachable by
    4-neighbour (x, z) steps where ``|y_a - y_b| <= 1`` (auto-step up 1,
    free-fall any distance). The visited set is the 3D reachability answer the
    validator checks door-fronts and landscape endpoints against.
    """
    if start_xyz is None:
        return set()
    visited: Set[Pos] = {start_xyz}
    q: deque = deque([start_xyz])
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (1 <= nx <= lot_w - 2 and 1 <= nz <= lot_d - 2):
                continue
            for ny in _standable_ys(compound, nx, nz):
                if abs(ny - y) > 1:
                    # Auto-step allows a +1 rise and any fall; a >1 rise from
                    # the current cell is unreachable in one step, but a fall
                    # of any size is fine. BFS exploration is symmetric in the
                    # STEP-ADJACENT relation (|Δy| <= 1), so gate on |Δy| here.
                    continue
                if (nx, ny, nz) in visited:
                    continue
                visited.add((nx, ny, nz))
                q.append((nx, ny, nz))
    return visited


def _gate_entry_standable(compound: CompoundGraph) -> Optional[Pos]:
    """Lowest STANDABLE cell in column ``(axis_x, z=1)`` — just inside the gate.

    ``z=1`` is the first interior row behind a south-facing gate (and the last
    but-one row for a north-facing gate; we normalise to the near-interior row
    on the gate side). Returns ``None`` when no STANDABLE cell exists in the
    column (the validator treats this as an unrecoverable entry defect).
    """
    lot_w, lot_d = compound.lot_size
    gate_side = compound.meta.get("gate_side", "south")
    # Mansion enclosure: the entrance is a gate_house through-building, so the
    # standable entry column is the gate_house's inner opening (gate_inner_z),
    # not z=1 (which sits inside the gate_house volume). Courtyard path keeps z=1.
    gate_inner_z = compound.meta.get("gate_inner_z")
    if gate_inner_z is not None:
        z_entry = gate_inner_z
    else:
        z_entry = 1 if gate_side == "south" else lot_d - 2
    ys = _standable_ys(compound, compound.axis_x, z_entry)
    if not ys:
        return None
    return (compound.axis_x, ys[0], z_entry)


def _place_band_transition_stairs(compound: CompoundGraph, style: Style) -> None:
    """Bridge every ``|Δy| ≥ 2`` boundary between adjacent path cells (design D4).

    Generalises the former axis-only ``_place_plinth_stairs``: walk every pair
    of 4-neighbour path cells and, where the natural surface y differs by ``≥
    2``, place ``N = |Δy|`` ascending ``stone_brick_stairs[facing=<uphill>,
    half=bottom]`` blocks bridging the gap. Pairs where either cell is in a
    building footprint or is a ``door_info["front"]`` cell are skipped (the
    building's own step block owns the door cell; a stair inside a footprint
    would collide with the structure). No-op for compounds without a plinth or
    without a multi-source-BFS path network (e.g. ``platform_tier = "none"``).
    """
    plinth_h = compound.meta.get("plinth_height", 0)
    if plinth_h <= 0:
        return
    path_nodes = [n for n in compound.parcel_nodes
                  if n.type == "path" and n.meta.get("algorithm") == "multi_source_bfs"]
    if not path_nodes:
        return
    path_cells = set().union(*(n.cells for n in path_nodes))
    if not path_cells:
        return
    building = compound.building_cells()
    door_fronts = _door_front_cells(compound)
    # Skip any path cell whose column would put a stair inside a footprint or
    # on a door-front cell. Both endpoints of a candidate pair must be clear.
    forbidden = building | door_fronts
    # Use the PLATFORM_STONE stairs entry as the stair material (vanilla-clean).
    stair_state = style.slot_entry("PLATFORM_STONE", "_stairs",
                                   default=f"minecraft:stone_brick_stairs"
                                           f"[facing=north,half=bottom]")
    stair_base = stair_state.split("[", 1)[0]
    stair_cells: Set[Cell2] = set()
    for (x, z) in list(path_cells):
        if (x, z) in forbidden:
            continue
        y_a = _natural_surface_y(compound, (x, z))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nbr = (x + dx, z + dz)
            if nbr not in path_cells or nbr in forbidden:
                continue
            y_b = _natural_surface_y(compound, nbr)
            dy = y_b - y_a
            if abs(dy) < 2:
                continue
            facing = _STAIR_FACING_FROM_DELTA.get((dx, dz))
            if facing is None:
                continue
            # The lower cell carries the stair(s); the facing rises toward the
            # higher cell. Place N = |Δy| ascending bottom-half stairs so the
            # player auto-steps from y_low to y_high. Stairs stack at y_low,
            # y_low+1, ... on the lower cell's column — vanilla stair collision
            # handles the step-up read.
            if dy > 0:
                low_x, low_z, low_y = x, z, y_a
            else:
                low_x, low_z, low_y = nbr[0], nbr[1], y_b
            for step in range(abs(dy)):
                compound.grid.set((low_x, low_y + step, low_z),
                                  f"{stair_base}[facing={facing},half=bottom]",
                                  ["DETAIL", "STRUCTURE"], PRIORITY["STRUCTURE"],
                                  "PLATFORM_STONE", force=True)
            stair_cells.add((low_x, low_z))
            break
    if stair_cells:
        compound.parcel_nodes.append(
            ParcelNode("plinth_stairs", "plinth_stairs", stair_cells, {
                "plinth_height": plinth_h,
                "stair_count": len(stair_cells),
                "algorithm": "band_transition",
            }))


# Back-compat alias for any caller (test/probe) that referenced the old name.
_place_plinth_stairs = _place_band_transition_stairs


def _voxel_walk_check(compound: CompoundGraph) -> Tuple[List[str], dict]:
    """Run the 3D voxel-walkability checks (courtyard-voxel-walkability spec).

    Returns ``(errors, stats)``. The checks are:

      - **door reachability**: every ``BuildingSlot`` whose ``door_info`` is
        populated must have at least one STANDABLE y in the door-front column
        that is in the BFS visited set — else ``voxel_unreachable_door:<arch>``.
      - **endpoint reachability**: every landscape endpoint (water / jar /
        planting / moon platform) must have a STANDABLE adjacent cell in the
        visited set — else ``voxel_unreachable_endpoint:<cell>``.
      - **step cliff**: no two 4-adjacent path cells with ``|Δy| ≥ 2`` may lack
        a ``stone_brick_stairs`` bridge — else ``voxel_step_cliff:<a>-><b>``.
      - **solid blockage**: no path cell may have a SOLID block in its body or
        head plane above the standable y — else ``voxel_blocked_by_solid:<cell>``.

    The ``stats`` dict carries the ``voxel_reachability`` triple (visited,
    unreachable, cliff) reported in the library validation report.
    """
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    # 1) Run the 3D BFS from the gate-entry STANDABLE column. When the entry
    #    column has no standable y at all, every downstream check fails loudly
    #    via a single root error instead of a flood of unreachable_* codes.
    start = _gate_entry_standable(compound)
    if start is None:
        errors.append("voxel_unreachable_entry:no_standable_at_gate")
        visited: Set[Pos] = set()
    else:
        visited = _voxel_walk_bfs(compound, start, lot_w, lot_d)

    # Columns reached by the BFS (collapse the visited 3D set to (x, z)).
    visited_cols: Set[Cell2] = {(x, z) for (x, _, z) in visited}

    # 2) Door reachability — for every door_info front column, at least one
    #    STANDABLE y must be in the visited set.
    for slot in compound.building_slots:
        if not (slot.door_info and isinstance(slot.door_info.get("front"), tuple)):
            continue
        fx, _fy, fz = slot.door_info["front"]
        if not (0 < fx < lot_w - 1 and 0 < fz < lot_d - 1):
            continue
        if any((fx, y, fz) in visited for y in _standable_ys(compound, fx, fz)):
            continue
        errors.append(f"voxel_unreachable_door:{slot.archetype}:{(fx, fz)}")

    # 3) Landscape endpoint reachability — reuses the 2D endpoint registry so
    #    "which cells count as an endpoint" stays defined in one place. An
    #    endpoint is reachable iff at least one STANDABLE y in its column is in
    #    the visited set (it was a BFS source in the 2D world; in 3D it must
    #    also be standable-reachable).
    unreachable_endpoints: List[Cell2] = []
    for cell in _collect_path_endpoints(compound):
        cx, cz = cell
        if any((cx, y, cz) in visited for y in _standable_ys(compound, cx, cz)):
            continue
        unreachable_endpoints.append(cell)
    for cell in unreachable_endpoints:
        errors.append(f"voxel_unreachable_endpoint:{cell}")

    # 4) Step-cliff — every 4-adjacent path-cell pair with |Δy| ≥ 2 must have a
    #    stair block bridging it. Reuses the plinth_stairs parcel node written
    #    by _place_band_transition_stairs.
    path_nodes = [n for n in compound.parcel_nodes
                  if n.type == "path" and n.meta.get("algorithm") == "multi_source_bfs"]
    path_cells = set().union(*(n.cells for n in path_nodes)) if path_nodes else set()
    stair_cells = compound.node_cells("plinth_stairs")
    door_fronts = _door_front_cells(compound)
    building = compound.building_cells()
    cliff_count = 0
    seen_cliffs: Set[Tuple[Cell2, Cell2]] = set()
    for cell in path_cells:
        if cell in building or cell in door_fronts:
            continue
        y_a = _natural_surface_y(compound, cell)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nbr = (cell[0] + dx, cell[1] + dz)
            if nbr not in path_cells or nbr in building or nbr in door_fronts:
                continue
            y_b = _natural_surface_y(compound, nbr)
            if abs(y_b - y_a) < 2:
                continue
            # Canonical ordered pair so each cliff is reported once.
            pair = tuple(sorted((cell, nbr)))
            if pair in seen_cliffs:
                continue
            low = cell if y_a < y_b else nbr
            if low in stair_cells:
                continue
            seen_cliffs.add(pair)
            cliff_count += 1
            errors.append(f"voxel_step_cliff:{cell}->{nbr}")

    # 5) Solid blockage — every path cell must have at least one STANDABLE y in
    #    its column (foot support SOLID, body + head NON-SOLID). A path cell
    #    with no standable y is a real "堵住" defect: a STRUCTURE/ROOF/COLUMN
    #    block occupies every potential body/head clearance in the column. Note
    #    this is column-level, not "the exact surface_y must be clear": a path
    #    cell whose gravel sits one block low but is walkable one block up (e.g.
    #    under a deep eave) is fine; only a column with zero standable ys is a
    #    defect.
    for cell in path_cells:
        if cell in building or cell in door_fronts:
            continue
        if _standable_ys(compound, cell[0], cell[1]):
            continue
        errors.append(f"voxel_blocked_by_solid:{cell}")

    stats = {
        "voxel_reachability": {
            "visited": len(visited),
            "unreachable": len(unreachable_endpoints),
            "cliff": cliff_count,
        },
    }
    return errors, stats



def generate_compound(seed: int, style: Optional[Style] = None,
                      variant: Optional[CompoundVariant] = None) -> CompoundGraph:
    style = style or load_style("chinese_courtyard")
    variant = variant or select_variant(seed)
    gate_side = _resolve_gate_side(variant.main_orientation)
    lot_w, lot_d = COURTYARD_SIZE[variant.courtyard_size]
    axis = lot_w // 2
    bands = _compute_yard_bands(1, variant.layout_type, lot_d)
    compound = CompoundGraph(
        style.style_id, seed, variant, (lot_w, lot_d), axis,
        meta={
            "layout_strategy": "one_jin_courtyard",
            "gate_side": gate_side,
            "outer_yard_band": list(bands["outer_yard_band"]),
            "inner_gate_band": list(bands["inner_gate_band"]),
            "main_yard_band": list(bands["main_yard_band"]),
        })

    slot_seed = seed * 1009
    overrides = {"main_bays": variant.main_bays}
    contexts = {
        "main_hall": generate_subbuilding(
            style, "main_hall", slot_seed + 1, variant.roof_grade,
            "chinese_courtyard", form_overrides=overrides),
        "front_row": generate_subbuilding(
            style, "front_row", slot_seed + 3, variant.roof_grade,
            "chinese_courtyard"),
        "west_wing": generate_subbuilding(
            style, "side_wing", slot_seed + 4, variant.roof_grade,
            "chinese_courtyard"),
        "east_wing": generate_subbuilding(
            style, "side_wing",
            slot_seed + 4 if variant.symmetry == "strict_mirror" else slot_seed + 5,
            variant.roof_grade, "chinese_courtyard"),
    }

    _add_chinese_perimeter(compound, style)
    _layout_outer_yard(compound, style, bands, contexts)
    _layout_inner_gate(compound, style, bands)
    _layout_main_yard(compound, style, bands, contexts)
    _place_covered_galleries(compound, style, bands)
    _dress_main_yard(compound, style, bands)
    _place_yard_ground(compound, style)
    _route_complete_path(compound, style)
    _place_band_transition_stairs(compound, style)
    return compound


# ---------------------------------------------------------------------------
# 江南大宅 (chinese_mansion) 3-进 compound — variant + layout + generator
# ---------------------------------------------------------------------------

MANSION_SIZE = {
    "mansion_small": (49, 64),
    "mansion_medium": (53, 72),
    "mansion_large": (57, 80),
}

# gate_form → GATE_HALF-compatible gate_type for _add_chinese_perimeter
_MANSION_GATE_TYPE: Dict[str, str] = {
    "flush": "manzi", "recessed": "jinzhu", "paifang": "guangliang"
}
_MANSION_GATE_FOOTPRINT: Dict[str, Tuple[int, int]] = {
    "manzi": (9, 5),
    "jinzhu": (11, 5),
    "guangliang": (11, 7),
}

# Deterministic 6-row template table (task 6.5). Each row yields a visibly
# distinct mansion on (gate_form, garden_scale, tower_count).
MANSION_TEMPLATES = (
    # 0: medium, paifang, large garden, 1 tower, half_hip, 5 bays
    dict(courtyard_size="mansion_medium", gate_form="paifang",
         garden_scale="large", tower_count=1,
         roof_grade="chinese_half_hip", open_hall_bays=5),
    # 1: large, recessed, large garden, 2 towers, overhang_gable, 5 bays
    dict(courtyard_size="mansion_large", gate_form="recessed",
         garden_scale="large", tower_count=2,
         roof_grade="chinese_overhang_gable", open_hall_bays=5),
    # 2: medium, flush, small garden, 1 tower, flush_gable, 3 bays
    dict(courtyard_size="mansion_medium", gate_form="flush",
         garden_scale="small", tower_count=1,
         roof_grade="chinese_flush_gable", open_hall_bays=3),
    # 3: large, paifang, large garden, 2 towers, half_hip, 3 bays
    dict(courtyard_size="mansion_large", gate_form="paifang",
         garden_scale="large", tower_count=2,
         roof_grade="chinese_half_hip", open_hall_bays=3),
    # 4: small, recessed, small garden, 1 tower, round_ridge, 5 bays
    dict(courtyard_size="mansion_small", gate_form="recessed",
         garden_scale="small", tower_count=1,
         roof_grade="chinese_round_ridge", open_hall_bays=5),
    # 5: medium, flush, large garden, 1 tower, overhang_gable, 3 bays
    dict(courtyard_size="mansion_medium", gate_form="flush",
         garden_scale="large", tower_count=1,
         roof_grade="chinese_overhang_gable", open_hall_bays=3),
)


@dataclass(frozen=True)
class MansionVariant:
    """Variant axes for a 江南大宅 3-进 compound (task 6.5)."""
    courtyard_size: str   # mansion_small / mansion_medium / mansion_large
    roof_grade: str
    gate_form: str        # flush / recessed / paifang
    garden_scale: str     # small / large
    tower_count: int      # 1 or 2
    open_hall_bays: int   # 3 or 5
    jin_count: int = 3    # always 3 for the shipped library

    @property
    def gate_type(self) -> str:
        return _MANSION_GATE_TYPE[self.gate_form]

    # Compatibility properties so functions written for CompoundVariant do not
    # crash when given a MansionVariant. The mansion layout path never calls
    # _layout_outer_yard / _layout_main_yard, so these are only reached by
    # shared utilities (validate_compound, silhouette_score, etc.).
    @property
    def layout_type(self) -> str:
        return "mansion"

    @property
    def main_bays(self) -> int:
        return self.open_hall_bays

    def key(self) -> Tuple:
        return (self.courtyard_size, self.roof_grade, self.gate_form,
                self.garden_scale, self.tower_count, self.open_hall_bays)

    def to_dict(self) -> dict:
        return {
            "courtyard_size": self.courtyard_size,
            "roof_grade": self.roof_grade,
            "gate_form": self.gate_form,
            "garden_scale": self.garden_scale,
            "tower_count": self.tower_count,
            "open_hall_bays": self.open_hall_bays,
            "jin_count": self.jin_count,
        }


def select_mansion_variant(seed: int) -> MansionVariant:
    row = MANSION_TEMPLATES[seed % len(MANSION_TEMPLATES)]
    return MansionVariant(
        courtyard_size=row["courtyard_size"],
        roof_grade=row["roof_grade"],
        gate_form=row["gate_form"],
        garden_scale=row["garden_scale"],
        tower_count=row["tower_count"],
        open_hall_bays=row["open_hall_bays"],
    )


# ---------------------------------------------------------------------------
# Enclosure-planning model (rebuild-mansion-enclosure-plan)
#
# Replaces the z-band-slice + magic-coordinate layout (_layout_front_yard /
# _layout_main_yard_mansion / _layout_back_yard) with: place oriented buildings
# against their anchor walls per the form rule, derive yards as enclosed
# negative space, route the path as a planning input. See the
# `compound-enclosure-planning` + `building-orientation-variants` +
# `mansion-gate-house` specs.
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class _EnclosurePlacement:
    """One building placement in the mansion enclosure manifest."""
    role: str            # human label
    archetype: str       # generate_subbuilding archetype key
    facing: str          # south/north/east/west — form-rule door direction
    anchor: str          # perimeter wall it backs onto (south/north/east/west)
    slot_id: str         # BuildingSlot id
    x0: int              # lot coords of the main-volume origin (SW corner)
    z0: int
    plinth_h: int = 0    # y origin (sits on the plinth)
    extra_overrides: Tuple = ()  # additional (key, value) form_overrides pairs


def _mansion_yard_depths(lot_d: int, garden_scale: str
                         ) -> Tuple[int, int, int, int]:
    """Proportional 进 depth split (front, main, back, garden) for a lot depth.

    Shares: front 1.0 / main 1.8 (ceremonial core, largest) / back 1.1 /
    garden 1.3 (1.8 for large). A 大宅 reads as 大 because its yards are spacious,
    not because it has more doors. The fixed budget (gate_house + 2 inner gates
    + one building band) is subtracted first, the rest distributed by share.
    """
    gate_depth = 3
    building_band = 13  # a yard must clear its tallest building (~tower/open_hall)
    fixed = 5 + 2 * gate_depth  # gate_house gd=5 + 2 inner gates
    yard_pool = lot_d - 2 - fixed - building_band
    garden_share = 1.8 if garden_scale == "large" else 1.3
    shares = {"front": 1.0, "main": 1.8, "back": 1.1, "garden": garden_share}
    total = sum(shares.values())
    front_d = max(6, int(yard_pool * shares["front"] / total))
    main_d = max(10, int(yard_pool * shares["main"] / total))
    back_d = max(15, int(yard_pool * shares["back"] / total))  # clear tower (13)
    garden_d = max(6, yard_pool - front_d - main_d - back_d)
    return front_d, main_d, back_d, garden_d


def _plan_mansion_enclosure(variant: "MansionVariant", lot_w: int, lot_d: int,
                            axis: int) -> Tuple[List[_EnclosurePlacement], List[Tuple[str, int]], int]:
    """Produce the placement manifest + inner-gate z-rows for a mansion variant.

    Returns (placements, [(gate_role, z), ...], gate_house_inner_z). Each
    placement binds an archetype + the form-rule facing + an anchor wall + a
    concrete (x0, z0) origin. Yards are derived later by _derive_mansion_yards.

    Form rule (building-orientation-variants): 正房/open_hall south; 倒座 north;
    西厢 east; 东厢 west; gate_house inward (north, toward 前院); 楼阁 south
    (toward its enclosing 后院).
    """
    front_d, main_d, back_d, _ = _mansion_yard_depths(lot_d, variant.garden_scale)
    gate_depth = 3

    placements: List[_EnclosurePlacement] = []

    # --- 前院: gate_house (through-building) + 倒座 (beside it, door→yard) ---
    # gate_house straddles z=0 and faces north (inward). gate_type selects the
    # footprint so the perimeter gap and realized side walls match exactly.
    gw, gd = _MANSION_GATE_FOOTPRINT[variant.gate_type]
    placements.append(_EnclosurePlacement(
        role="gate_house", archetype="gate_house", facing="north",
        anchor="south", slot_id="gate_house",
        x0=axis - gw // 2, z0=0,
        extra_overrides=(("footprint", (gw, gd)),)))
    gate_inner_z = gd  # path starts at the gate_house north opening

    # 倒座: south wall line beside the gate_house, door north (→前院). Leave a
    # 1-cell alley to the perimeter AND a 1-cell clear corridor beside the
    # gate_house so the gate_house's north passage opens onto walkable yard,
    # not onto the 倒座 footprint (front_row overlapping the gate_house's inner
    # door was leaving the door voxel-unreachable). Place on the side with more
    # room and pick the largest standard footprint (15/17/19 wide) that fits.
    fw_full, fd = 17, 7
    gate_west = axis - gw // 2
    gate_east = gate_west + gw
    # Reserve a 1-cell corridor immediately west/east of the gate_house.
    avail_west = gate_west - 2 - 1   # -2 perimeter, -1 corridor
    avail_east = (lot_w - 1) - gate_east - 1 - 1
    from .archetypes import SCALE_TIERS  # local import (avoid cycle)
    front_fps = SCALE_TIERS["front_row"]["footprints"]  # [(15,7),(17,7),(19,7)]
    west_fp = next((fp for fp in sorted(front_fps, reverse=True)
                    if fp[0] <= avail_west), None)
    east_fp = next((fp for fp in sorted(front_fps, reverse=True)
                    if fp[0] <= avail_east), None)
    if west_fp and (not east_fp or west_fp[0] >= east_fp[0]):
        fp, fx = west_fp, gate_west - 1 - west_fp[0]
    elif east_fp:
        fp, fx = east_fp, gate_east + 2
    else:
        # No standard footprint fits (very narrow lot): skip 倒座 rather than
        # overlap the gate_house passage. The validator does not require it.
        fp, fx = None, None
    if fp is not None:
        placements.append(_EnclosurePlacement(
            role="front_row", archetype="front_row", facing="north",
            anchor="south", slot_id="front_row",
            x0=fx, z0=0, extra_overrides=(("footprint", fp),)))

    # 仆役房/厨房 (service_house): a small plain building on the OPPOSITE south-
    # wall side from the 倒座, when that side has room (path-surface-zoning task
    # 3.2). It is the 生活 route's endpoint; its door faces north (→前院 alley) so
    # the formal/service BFS reaches it through the alley. Skipped when the lot is
    # too narrow for a 9-wide service_house without overlapping the gate_house or
    # the 倒座. (Only placed in the mansion; the courtyard/small-courtyard families
    # never call this planner.)
    svc_w = 9
    svc_fp = (svc_w, 7)
    if fp is not None and fx <= gate_west - 1:
        # 倒座 is on the WEST; service_house on the EAST (between gate_house and
        # the east perimeter), if it fits with a 1-cell corridor to the gate_house.
        svc_x = gate_east + 1
        svc_room = (lot_w - 1) - svc_x - 1
    elif fp is not None:
        # 倒座 is on the EAST; service_house on the WEST.
        svc_x = 1
        svc_room = (gate_west - 1) - svc_x - 1
    else:
        # No 倒座 placed: service_house on whichever side has more room.
        if avail_west >= svc_w:
            svc_x, svc_room = 1, avail_west
        elif avail_east >= svc_w:
            svc_x, svc_room = gate_east + 1, avail_east
        else:
            svc_x, svc_room = None, 0
    if svc_x is not None and svc_room >= svc_w:
        placements.append(_EnclosurePlacement(
            role="service_house", archetype="service_house", facing="north",
            anchor="south", slot_id="service_house",
            x0=svc_x, z0=0, extra_overrides=(("footprint", svc_fp),)))

    # 仪门 between 前院 and 主院
    yimen_z = gd + front_d + gate_depth - 1

    # --- 主院: open_hall (north end, south-facing) + 厢 (east/west, inward) ---
    plinth_h = 1
    my0 = yimen_z + 1
    my1 = my0 + main_d - 1
    from .archetypes import MAIN_HALL_BAY_FOOTPRINT
    hw, hd = MAIN_HALL_BAY_FOOTPRINT.get(variant.open_hall_bays, (15, 11))
    hall_z1 = my1
    hall_z0 = hall_z1 - hd + 1
    placements.append(_EnclosurePlacement(
        role="open_hall", archetype="open_hall", facing="south",
        anchor="north", slot_id="open_hall",
        x0=axis - hw // 2, z0=hall_z0, plinth_h=plinth_h))

    ww, wd = 7, 15
    wing_z0 = my0 + 2
    wing_d = min(wd, max(5, my1 - 1 - wing_z0))
    placements.append(_EnclosurePlacement(
        role="west_wing", archetype="side_wing", facing="east",
        anchor="west", slot_id="west_side_wing",
        x0=1, z0=wing_z0, plinth_h=plinth_h))
    placements.append(_EnclosurePlacement(
        role="east_wing", archetype="side_wing", facing="west",
        anchor="east", slot_id="east_side_wing",
        x0=lot_w - 1 - ww, z0=wing_z0, plinth_h=plinth_h))

    # 二门 between 主院 and 后院
    ermen_z = my1 + gate_depth

    # --- 后院: 楼阁 off-axis (beside the axis, not against the wall) ---
    # 楼阁 sits at the south edge of the 后院 (just north of the 二门); its yard
    # space is to its NORTH, so it faces north (door→北) — facing south would
    # throw its porch colonnade back across the 二门 into the 主院 plinth
    # (voxel/ground-layer conflict). tower_house is a 2-story mass; a south door
    # would also read as turning its back on the 后院 it belongs to.
    #
    # Arc 6: bound the tower to the 后院. The garden band starts at
    # ermen_z + back_d + 1 (generate_mansion), so the tower's north edge
    # (tz0 + td - 1) must stay strictly south of it — clamp td so a tight back_d
    # never lets the tower spill into the 花园 (the back_yard_garden_overlap /
    # tower_overlaps_garden validators enforce this). A single tower is off-axis
    # (a 江南 form) but its side is chosen deterministically per variant rather
    # than hard-pinned west, so the mansion roster reads as varied not lopsided.
    garden_z0 = ermen_z + back_d + 1
    tw, td = 11, 13
    max_td = max(5, garden_z0 - (ermen_z + 1))   # north edge < garden_z0
    td = min(td, max_td)
    tz0 = ermen_z + 1
    t_west_x = axis - 1 - tw
    t_east_x = axis + 2
    if variant.tower_count == 2:
        # Symmetric pair straddling the axis.
        placements.append(_EnclosurePlacement(
            role="tower_house_1", archetype="tower_house", facing="north",
            anchor="south", slot_id="tower_house_1",
            x0=t_west_x, z0=tz0))
        placements.append(_EnclosurePlacement(
            role="tower_house_2", archetype="tower_house", facing="north",
            anchor="south", slot_id="tower_house_2",
            x0=t_east_x, z0=tz0))
    else:
        # Single tower off-axis; side is deterministic per variant key (crc32,
        # not hash(), which is randomized across runs — see _realize slot seed).
        side_west = (zlib.crc32(repr(variant.key()).encode("utf-8")) & 1) == 0
        t_x = t_west_x if side_west else t_east_x
        placements.append(_EnclosurePlacement(
            role="tower_house_1", archetype="tower_house", facing="north",
            anchor="south", slot_id="tower_house_1",
            x0=t_x, z0=tz0))

    gates = [("yimen", yimen_z), ("ermen", ermen_z)]
    return placements, gates, gate_inner_z


def _derive_mansion_yards(compound: CompoundGraph, placements: List[_EnclosurePlacement],
                          gates: List[Tuple[str, int]], lot_w: int, lot_d: int
                          ) -> Dict[str, Set[Cell2]]:
    """Derive each 进 as the enclosed negative space of its facing-buildings.

    A 进's cells are the interior cells not under any building footprint,
    partitioned by the inner-gate z-rows. This replaces the pre-cut z-band
    model: the yard IS the space the buildings enclose.
    """
    building_cells = set()
    for p in placements:
        # approximate footprint from the placement origin + a nominal size; the
        # real footprint comes from the realized BuildingSlot, but for yard
        # derivation the placement bounding box suffices.
        building_cells.add((p.x0, p.z0))
    # Use the realized building slots for accuracy (called after realization).
    occupied = compound.building_cells() | compound.node_cells(
        "perimeter_wall", "inner_gate", "screen_wall")
    interior = {(x, z) for x in range(1, lot_w - 1) for z in range(1, lot_d - 1)
                if (x, z) not in occupied}
    # Partition by inner-gate z-rows.
    gate_zs = sorted(z for _, z in gates)
    yards: Dict[str, Set[Cell2]] = {}
    if gate_zs:
        yards["front_yard"] = {c for c in interior if c[1] < gate_zs[0]}
        if len(gate_zs) >= 2:
            yards["main_yard"] = {c for c in interior if gate_zs[0] < c[1] < gate_zs[1]}
            yards["back_yard"] = {c for c in interior if c[1] > gate_zs[1]}
        else:
            yards["main_yard"] = {c for c in interior if c[1] > gate_zs[0]}
    else:
        yards["main_yard"] = interior
    return yards


def _realize_mansion_enclosure(compound: CompoundGraph, style: Style,
                               variant: "MansionVariant",
                               placements: List[_EnclosurePlacement],
                               gates: List[Tuple[str, int]],
                               gate_inner_z: int) -> Dict[str, Set[Cell2]]:
    """Realize the enclosure manifest: build + place each oriented building,
    place the gate_house through-building, inner gates, 照壁, and 主院 plinth.

    Each building is built with its form-rule facing via form_overrides, so its
    door lands on the yard-facing wall (building-orientation-variants). Returns
    the realized manifest for downstream yard/path derivation.
    """
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    slot_seed = compound.seed * 1009

    def _build_and_place(p: "_EnclosurePlacement") -> None:
        overrides = {"facing": p.facing}
        if p.archetype == "open_hall":
            overrides["open_hall_bays"] = variant.open_hall_bays
        overrides.update(dict(p.extra_overrides))
        # Stable per-slot seed offset: Python's hash() is randomized across
        # process runs (PYTHONHASHSEED), so derive the offset from zlib.crc32
        # — otherwise the same mansion regenerates with a different sub-building
        # layout each run (and a different plinth-edge detail placement, which
        # left ground_layer_hole cells at the band boundary).
        slot_off = zlib.crc32(p.slot_id.encode("utf-8")) % 1000
        ctx = generate_subbuilding(
            style, p.archetype, slot_seed + slot_off,
            variant.roof_grade, "chinese_mansion", form_overrides=overrides)
        _translate_context(compound, p.slot_id, ctx, (p.x0, p.plinth_h, p.z0))

    # Place the south-wall buildings (gate_house + 倒座/front_row) first so we
    # can measure the real south-line depth before deriving gate_inner_z. Both
    # sit on z=0 but their realized depths differ by tier (gate_house 5 or 7;
    # front_row 7), and gate_inner_z must clear whichever extends furthest north
    # — otherwise the (axis, gate_inner_z) entry cell lands inside front_row's
    # footprint and the path router reports endpoint_unreachable there.
    south_archetypes = ("gate_house", "front_row", "service_house")
    south_places = [p for p in placements if p.archetype in south_archetypes]
    for p in south_places:
        _build_and_place(p)
    gate_inner_z = 0
    for p in south_places:
        slot = next((s for s in compound.building_slots if s.id == p.slot_id), None)
        if slot is None or not slot.footprint:
            continue
        z1 = max(z for _, z in slot.footprint)
        gate_inner_z = max(gate_inner_z, z1 + 1)

    # Place the remaining buildings.
    for p in placements:
        if p.archetype in south_archetypes:
            continue
        _build_and_place(p)

    # --- 夹道 (service alley): the off-axis circulation strip between the
    # south-wall buildings (倒座 / 仆役房) and the perimeter wall
    # (path-surface-zoning task 3.3). Recorded as an ``alley`` parcel node so
    # _derive_ground_kinds resolves it to PATH_ALLEY (brick) and the service_house
    # door reaches the formal path through it. The alley is the 2-cell-wide strip
    # along the east/west perimeter, south of the 仪门, that is not a building
    # footprint — the off-axis route from the gate area to the service door.
    south_fp: Set[Cell2] = set()
    for p in placements:
        if p.archetype in south_archetypes:
            slot = next((s for s in compound.building_slots
                         if s.id == p.slot_id), None)
            if slot is not None:
                south_fp |= slot.footprint
    yimen_z = gates[0][1] if gates else gate_inner_z
    alley_cells: Set[Cell2] = set()
    for side_x in (1, lot_w - 2):
        for z in range(1, yimen_z):
            for dx in (0, 1) if side_x == 1 else (0, -1):
                cell = (side_x + dx, z)
                if cell in south_fp or not (1 <= cell[0] <= lot_w - 2):
                    continue
                alley_cells.add(cell)
    if alley_cells:
        compound.parcel_nodes.append(
            ParcelNode("service_alley", "alley", alley_cells, {
                "side": "east" if any(x > axis for x, _ in alley_cells) else "west",
                "cell_count": len(alley_cells),
            }))

    # --- 主院 plinth (台基): raised stone floor under the 主院 buildings only.
    # Arc 6: previously this full-width-filled the whole 主院 band (a solid stone
    # slab that read as empty). Now it covers only the 主院 building footprints
    # (敞厅 + 厢房) + a ±1-cell skirt + the 抄手游廊 strip, so the 主院 heart
    # falls through to _place_yard_ground → GROUND_YARD_OPEN grass (草+砖结合).
    plinth_h = 1
    platform_stone = style.primary("PLATFORM_STONE")
    gate_zs = sorted(z for _, z in gates)
    if len(gate_zs) >= 2:
        my0, my1 = gate_zs[0] + 1, gate_zs[1] - 1
        # Seed the plinth with the 主院 building footprints (open_hall + wings).
        main_archetypes = ("open_hall", "side_wing")
        plinth_seed: Set[Cell2] = set()
        for slot in compound.building_slots:
            if slot.archetype in main_archetypes:
                plinth_seed |= set(slot.footprint)
        # +1-cell skirt around the buildings (a plinth apron), clamped to the
        # 主院 band + interior.
        skirt: Set[Cell2] = set()
        for (x, z) in plinth_seed:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    c = (x + dx, z + dz)
                    if 1 <= c[0] <= lot_w - 2 and my0 <= c[1] <= my1:
                        skirt.add(c)
        # Reserve the 抄手游廊 strips (east + west edges of the 主院) so the
        # galleries sit on plinth too. _layout_main_yard_galleries computes the
        # exact cells later; here we just widen the plinth to the clear edge
        # strips between the wings and the perimeter so the gallery base is stone.
        wing_xs = sorted({x for slot in compound.building_slots
                          if slot.archetype == "side_wing" for x, _ in slot.footprint})
        if wing_xs:
            west_wing_max = max(x for x in wing_xs if x < axis)
            east_wing_min = min(x for x in wing_xs if x > axis)
            for z in range(my0, my1 + 1):
                for x in range(1, west_wing_max + 2):       # west gallery strip
                    if 1 <= x <= lot_w - 2:
                        skirt.add((x, z))
                for x in range(east_wing_min - 1, lot_w - 1):  # east gallery strip
                    if 1 <= x <= lot_w - 2:
                        skirt.add((x, z))
        plinth_cells = skirt
        for (x, z) in plinth_cells:
            compound.grid.set((x, 0, z), platform_stone,
                              ["STRUCTURE", "GROUND"], PRIORITY["STRUCTURE"],
                              "PLATFORM_STONE")
        compound.parcel_nodes.append(
            ParcelNode("main_yard_platform", "platform", plinth_cells,
                       {"tier": "stone_1", "height": plinth_h}))
        compound.meta["plinth_height"] = plinth_h

    # --- 照壁 off-axis inside 前院 (江南 zhaobi form) ---
    screen_rng = random.Random(compound.seed + 7071)
    screen_side = screen_rng.choice(("east", "west"))
    screen_width = screen_rng.choice((1, 2))
    screen_z = gate_inner_z + 1
    screen_cells = _screen_panel_cells(axis, screen_z, screen_side, screen_width)
    base = style.primary("PLATFORM_STONE")
    wall_main = style.primary("WALL_MAIN")
    cap = style.slot_entry("ROOF_DARK", "_stairs")
    for x, z in screen_cells:
        compound.grid.set((x, 0, z), base, ["STRUCTURE"],
                          PRIORITY["STRUCTURE"], "PLATFORM_STONE")
        for y in range(1, 6):
            compound.grid.set((x, y, z), wall_main, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "WALL_MAIN")
        compound.grid.set((x, 6, z), cap, ["ROOF"], PRIORITY["ROOF"], "ROOF_DARK")
    compound.parcel_nodes.append(
        ParcelNode("screen_wall", "screen_wall", screen_cells, {
            "height": 6, "on_axis": False, "facing_gate": True,
            "form": "zhaobi", "side": screen_side, "width": screen_width}))

    # --- Inner gates (仪门 / 二门) as roofed-wall passages at the 进 boundaries ---
    for role, z in gates:
        _layout_inner_gate(compound, style,
                           {"inner_gate_band": (z, z)},
                           node_id=f"{role}_gate", gate_kind=f"{role}_gate")

    # Record the gate_house inner opening for the path router.
    compound.meta["gate_inner_z"] = gate_inner_z
    # Compatibility aliases so _cell_band / _natural_surface_y resolve a surface y
    # (the validator helpers look for these 1-进 key names).
    compound.meta["outer_yard_band"] = [1, gate_zs[0] - 1] if gate_zs else [1, lot_d - 2]
    compound.meta["inner_gate_band"] = [gate_zs[0], gate_zs[0]] if gate_zs else [1, 1]
    return {"gate_zs": gate_zs, "gate_inner_z": gate_inner_z}


def _layout_main_yard_galleries(compound: CompoundGraph, style: Style) -> None:
    """主院 抄手游廊 (mansion-only, Arc 5).

    The mansion 主院 previously had no 抄手游廊 (only the 1-进 ``chinese_courtyard``
    has them via ``_place_covered_galleries``). This adds east + west galleries
    tying the 仪门 flanks to the 敞厅 flanks along the 主院 edges, rendered as
    real 3D covered galleries (columns + single-slope roof + yard-side
    balustrade) via ``_place_covered_gallery_3d``. A side is skipped when the
    clear strip between its 厢房 and the perimeter is too narrow (< 3 wide) or
    would collide with a building footprint — the gallery is optional.
    """
    lot_w, _ = compound.lot_size
    axis = compound.axis_x
    plinth_h = compound.meta.get("plinth_height", 0)
    my0, my1 = compound.meta["main_yard_band"]

    wings = [s for s in compound.building_slots if s.archetype == "side_wing"]
    hall = next((s for s in compound.building_slots if s.archetype == "open_hall"), None)
    if not wings or hall is None:
        return  # nothing to tie the galleries to
    building_cells = compound.building_cells()

    for side in ("west", "east"):
        wing = next((s for s in wings
                     if (side == "west") == (max(x for x, _ in s.footprint) < axis)), None)
        if wing is None:
            continue
        # The gallery runs along the inner edge of this wing (the 主院 side).
        if side == "west":
            inner_x = max(x for x, _ in wing.footprint) + 1
            step = 1
        else:
            inner_x = min(x for x, _ in wing.footprint) - 1
            step = -1
        # 3-wide gallery strip just inside the wing.
        xs = [inner_x + step * d for d in range(3)]
        xs = [x for x in xs if 0 < x < lot_w - 1]
        if len(xs) < 2:
            continue
        cells: Set[Cell2] = set()
        for x in xs:
            for z in range(my0 + 1, my1):
                if (x, z) in building_cells:
                    continue
                cells.add((x, z))
        if len(cells) < 4:
            continue
        # Balustrade faces the yard (open side away from the wing); posts line
        # the wing side so they hug the wall and do not block the wing's doors.
        yard_side = "east" if side == "west" else "west"
        wing_side = "west" if side == "west" else "east"
        _place_covered_gallery_3d(compound, style, cells, plinth_h,
                                  open_side=yard_side, post_side=wing_side)
        compound.parcel_nodes.append(
            ParcelNode(f"{side}_gallery", "covered_gallery", cells, {
                "side": side, "relative_y": plinth_h, "rendered_as": "3d_covered_gallery",
                "endpoints": ["inner_gate", "open_hall"], "circulation": True,
                "balustrade_side": yard_side,
            }))


def _layout_front_yard(compound: CompoundGraph, style: Style, bands: dict,
                       contexts: Dict[str, BuildContext]) -> None:
    """前院 layout for chinese_mansion: 照壁 off-axis + 倒座 with side alley.

    Parallel to ``_layout_outer_yard`` for the 1-进 courtyard but reads
    ``front_yard_band`` and uses the 江南 vocabulary labels.
    """
    lot_w, _ = compound.lot_size
    axis = compound.axis_x
    fy0, _ = bands["front_yard_band"]

    # 照壁 off-axis (江南 zhaobi form)
    screen_rng = random.Random(compound.seed + 7071)
    screen_side = screen_rng.choice(("east", "west"))
    screen_width = screen_rng.choice((1, 2))
    screen_z = fy0 + 1
    screen_cells = _screen_panel_cells(axis, screen_z, screen_side, screen_width)
    base = style.primary("PLATFORM_STONE")
    wall_main = style.primary("WALL_MAIN")
    cap = style.slot_entry("ROOF_DARK", "_stairs")
    for x, z in screen_cells:
        compound.grid.set((x, 0, z), base, ["STRUCTURE"],
                          PRIORITY["STRUCTURE"], "PLATFORM_STONE")
        for y in range(1, 6):
            compound.grid.set((x, y, z), wall_main, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "WALL_MAIN")
        compound.grid.set((x, 6, z), cap, ["ROOF"], PRIORITY["ROOF"], "ROOF_DARK")
    compound.parcel_nodes.append(
        ParcelNode("screen_wall", "screen_wall", screen_cells, {
            "height": 6, "on_axis": False, "facing_gate": True,
            "form": "zhaobi", "side": screen_side, "width": screen_width,
        }))

    # 倒座 (front_row) with side alley on the side opposite to the 照壁
    front_ctx = contexts["front_row"]
    front = front_ctx.graph.get("main")
    alley_side = "west" if screen_side == "east" else "east"
    alley_x = _front_row_origin_x(axis, front.size[0], lot_w, alley_side)
    _translate_context(compound, "front_row", front_ctx, (alley_x, 0, fy0 + 4))


def _layout_main_yard_mansion(compound: CompoundGraph, style: Style, bands: dict,
                              contexts: Dict[str, BuildContext]) -> None:
    """主院 layout for chinese_mansion: open_hall (敞厅) + side_wings + 月台.

    The 敞厅 sits on the central axis at the inward end of the 主院; the
    厢房 (side_wings) flank it. A shallow 月台 apron fronts the 敞厅.
    """
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    my0, my1 = bands["main_yard_band"]

    plinth_h = 1  # stone_1 raised floor
    platform_stone = style.primary("PLATFORM_STONE")
    plinth_cells: Set[Cell2] = set()
    for x in range(1, lot_w - 1):
        for z in range(my0, my1 + 1):
            plinth_cells.add((x, z))
            compound.grid.set((x, 0, z), platform_stone,
                              ["STRUCTURE", "GROUND"], PRIORITY["STRUCTURE"],
                              "PLATFORM_STONE")
    compound.parcel_nodes.append(
        ParcelNode("main_yard_platform", "platform", plinth_cells,
                   {"tier": "stone_1", "height": plinth_h}))

    # 敞厅 (open_hall) on axis at the inward end
    hall = contexts["open_hall"].graph.get("main")
    hall_z1 = my1 - 1
    hall_z0 = hall_z1 - hall.size[2] + 1
    main_slot = _translate_context(
        compound, "open_hall", contexts["open_hall"],
        (axis - hall.size[0] // 2, plinth_h, hall_z0))

    # 月台 apron in front of 敞厅
    moon_depth = 2
    hx0 = min(x for x, _ in main_slot.footprint)
    hx1 = max(x for x, _ in main_slot.footprint)
    moon_cells = {(x, z) for x in range(hx0, hx1 + 1)
                  for z in range(hall_z0 - moon_depth, hall_z0)
                  if 0 < x < lot_w - 1}
    for x, z in moon_cells:
        compound.grid.set((x, 0, z), platform_stone,
                          ["STRUCTURE", "GROUND"], PRIORITY["STRUCTURE"],
                          "PLATFORM_STONE")
    compound.parcel_nodes.append(
        ParcelNode("moon_platform", "moon_platform", moon_cells,
                   {"relative_y": plinth_h, "fronts": "open_hall"}))

    # 厢房 (side_wings) flanking the 主院
    west = contexts["west_wing"].graph.get("main")
    east = contexts["east_wing"].graph.get("main")
    wing_z0 = my0 + 1
    _translate_context(compound, "west_side_wing", contexts["west_wing"],
                       (2, plinth_h, wing_z0))
    _translate_context(compound, "east_side_wing", contexts["east_wing"],
                       (lot_w - 2 - east.size[0], plinth_h, wing_z0))

    compound.meta["main_yard_plinth_h"] = plinth_h
    compound.meta["plinth_height"] = plinth_h
    compound.meta["open_hall_front_z"] = hall_z0


def _layout_back_yard(compound: CompoundGraph, style: Style, bands: dict,
                      contexts: Dict[str, BuildContext],
                      variant: "MansionVariant") -> None:
    """后院 layout: 楼阁 (tower_house) placed off-axis, one or two towers."""
    lot_w, _ = compound.lot_size
    axis = compound.axis_x
    by0, _ = bands["back_yard_band"]

    tower_rng = random.Random(compound.seed + 8181)
    tower1_side = tower_rng.choice(("east", "west"))

    tower1_ctx = contexts["tower_1"]
    tower1 = tower1_ctx.graph.get("main")
    if tower1_side == "west":
        tower1_x = 2
    else:
        tower1_x = lot_w - 2 - tower1.size[0]
    _translate_context(compound, "tower_house_1", tower1_ctx,
                       (tower1_x, 0, by0 + 1))

    if variant.tower_count == 2 and "tower_2" in contexts:
        tower2_ctx = contexts["tower_2"]
        tower2 = tower2_ctx.graph.get("main")
        tower2_x = (lot_w - 2 - tower2.size[0]) if tower1_side == "west" else 2
        _translate_context(compound, "tower_house_2", tower2_ctx,
                           (tower2_x, 0, by0 + 1))


def _select_waterside_gallery_run(lot_w: int, gy0: int, gy1: int,
                                  water: Set[Cell2],
                                  blocked: Set[Cell2],
                                  preferred_side: str = "north",
                                  max_len: int = 7
                                  ) -> Tuple[Set[Cell2], Optional[str], Set[Cell2]]:
    """Pick one short, straight, two-cell-deep gallery run along the pond.

    The freeform pond shoreline is noisy by design; directly turning every dry
    shore cell into a 3D gallery makes a ragged roof/column cloud. The gallery is
    therefore a composed architectural element: one straight run on a clean bank,
    with a water-edge row plus one dry row behind it.
    """
    if not water:
        return set(), None, set()

    delta = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    side_order = [preferred_side] + [s for s in ("north", "south", "east", "west")
                                    if s != preferred_side]
    pond_cx = sum(x for x, _ in water) / len(water)
    pond_cz = sum(z for _, z in water) / len(water)
    best: Optional[Tuple[Tuple[float, ...], str, List[Cell2], Set[Cell2]]] = None

    def _in_garden(cell: Cell2) -> bool:
        x, z = cell
        return 1 <= x <= lot_w - 2 and gy0 <= z <= gy1

    def _dry(cell: Cell2) -> bool:
        return _in_garden(cell) and cell not in water and cell not in blocked

    for rank, side in enumerate(side_order):
        dx, dz = delta[side]
        front_cells: Set[Cell2] = set()
        back_for: Dict[Cell2, Cell2] = {}
        for wx, wz in water:
            front = (wx - dx, wz - dz)
            back = (front[0] - dx, front[1] - dz)
            if not (_dry(front) and _dry(back)):
                continue
            # The front row must really face open water on the selected side.
            if (front[0] + dx, front[1] + dz) not in water:
                continue
            front_cells.add(front)
            back_for[front] = back

        groups: Dict[int, List[int]] = {}
        if side in ("north", "south"):
            for x, z in front_cells:
                groups.setdefault(z, []).append(x)
        else:
            for x, z in front_cells:
                groups.setdefault(x, []).append(z)

        for fixed, values in groups.items():
            values = sorted(values)
            runs: List[List[int]] = []
            current: List[int] = []
            for value in values:
                if current and value != current[-1] + 1:
                    runs.append(current)
                    current = []
                current.append(value)
            if current:
                runs.append(current)

            for run in runs:
                if len(run) < 3:
                    continue
                run_len = min(max_len, len(run))
                windows = [run[i:i + run_len] for i in range(0, len(run) - run_len + 1)]
                for window in windows:
                    if side in ("north", "south"):
                        front_run = [(v, fixed) for v in window]
                        center = (sum(window) / len(window), fixed)
                    else:
                        front_run = [(fixed, v) for v in window]
                        center = (fixed, sum(window) / len(window))
                    strip = set(front_run) | {back_for[c] for c in front_run}
                    if strip & blocked or strip & water:
                        continue
                    center_dist = abs(center[0] - pond_cx) + abs(center[1] - pond_cz)
                    score = (rank, -len(front_run), center_dist)
                    if best is None or score < best[0]:
                        best = (score, side, front_run, strip)

    if best is None:
        return set(), None, set()
    _, side, front_run, strip = best
    return strip, side, set(front_run)


def _select_waterside_pavilion_center(lot_w: int, gy0: int, gy1: int,
                                      water: Set[Cell2], blocked: Set[Cell2],
                                      size: int = 3) -> Optional[Cell2]:
    """Select a dry 亭 footprint that reads as waterside.

    The rockery was moved into the pond, but the old pavilion placement still
    used the leftover west-rockery band. That could put the pavilion on the far
    side of the garden from the pond. A water pavilion must sit on a dry bank
    with at least one footprint cell 4-adjacent to pond water.
    """
    if not water:
        return None
    half = size // 2
    pond_x0 = min(x for x, _ in water)
    pond_x1 = max(x for x, _ in water)
    pond_z0 = min(z for _, z in water)
    pond_z1 = max(z for _, z in water)
    pond_cx = (pond_x0 + pond_x1) / 2.0
    pond_cz = (pond_z0 + pond_z1) / 2.0
    best: Optional[Tuple[Tuple[float, ...], Cell2]] = None

    for cx in range(1 + half, lot_w - 1 - half):
        for cz in range(gy0 + half, gy1 - half + 1):
            cells = {
                (x, z)
                for x in range(cx - half, cx + half + 1)
                for z in range(cz - half, cz + half + 1)
            }
            if cells & water or cells & blocked:
                continue
            edge_count = 0
            for x, z in cells:
                nbrs = {(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)}
                if nbrs & water:
                    edge_count += 1
            if edge_count == 0:
                continue

            x0 = min(x for x, _ in cells)
            x1 = max(x for x, _ in cells)
            z0 = min(z for _, z in cells)
            z1 = max(z for _, z in cells)
            # Prefer the west bank so the pavilion faces across the pond
            # instead of hiding behind the north gallery or the perimeter wall.
            if x1 < pond_x0:
                side_rank = 0
            elif z0 > pond_z1:
                side_rank = 1
            elif z1 < pond_z0:
                side_rank = 2
            elif x0 > pond_x1:
                side_rank = 3
            else:
                side_rank = 4
            center_dist = abs(cx - pond_cx) + abs(cz - pond_cz)
            score = (
                float(side_rank),
                float(-edge_count),
                float(center_dist),
                float(abs(cz - pond_cz)),
                float(abs(cx - pond_cx)),
                float(cz),
                float(cx),
            )
            if best is None or score < best[0]:
                best = (score, (cx, cz))
    return best[1] if best is not None else None


def _layout_garden(compound: CompoundGraph, style: Style, bands: dict,
                   seed: int, variant: "MansionVariant") -> None:
    """花园 layout: rockery + pond + pavilion + stepping stones.

    The bbox is split: west half for 假山 (garden_rockery), east half for
    水池 (garden_pond). ``garden_scale`` controls feature density.
    """
    lot_w, _ = compound.lot_size
    gy0, gy1 = bands["garden_band"]
    interior_w = lot_w - 2   # columns x=1..lot_w-2

    pond_w   = max(5, interior_w // 3)
    garden_d = gy1 - gy0 + 1
    feature_d = max(6, garden_d * 2 // 3)

    pond_x0, pond_x1 = lot_w - 1 - pond_w, lot_w - 2
    pond_z0, pond_z1 = gy0, gy0 + feature_d - 1

    # Main 假山: the hand-sculpted hero cluster (add-hero-rockery), a fixed 3×3×3
    # sculpt. Placed as a 水心假山 (island rockery) — its base sits in the middle
    # of the pond so it rises from the water as an island, rather than on the
    # dry west band. Fixes the spike-field bug (stacks 3 tall). The pond excludes
    # the rockery footprint (see place_garden_pond), so the rockery cells survive
    # as an island while water fills the rest of the pond bbox.
    hero_base = 3  # hero_taihu sculpt footprint is 3×3
    pond_cx = (pond_x0 + pond_x1) // 2
    pond_cz = (pond_z0 + pond_z1) // 2
    island_x0 = pond_cx - hero_base // 2
    island_z0 = pond_cz - hero_base // 2
    rockery_node = place_garden_rockery(compound,
                                        (island_x0, island_z0,
                                         island_x0 + hero_base - 1, island_z0 + hero_base - 1),
                                        seed + 9100, hero="taihu", base_y=0)
    pond_node, water_cells = place_garden_pond(
        compound, (pond_x0, pond_z0, pond_x1, pond_z1), seed + 9200,
        rockery_node=rockery_node)

    # 亭 pavilion: place it on a dry bank of the pond, not in the leftover west
    # rockery band. The previous position could be 20+ cells from the pond, so
    # low-angle review saw "a pavilion somewhere in the garden" instead of a
    # readable 水亭. The hero summit still carries a 小树, so the 亭 stays at
    # ground level.
    pavilion_blocked = (
        set(rockery_node.cells if rockery_node is not None else set())
        | compound.building_cells()
        | compound.node_cells("perimeter_wall", "screen_wall")
    )
    pavilion_center = _select_waterside_pavilion_center(
        lot_w, gy0, gy1, set(water_cells), pavilion_blocked, size=3)
    if pavilion_center is not None:
        place_garden_pavilion(compound, pavilion_center, 3, 0, style)

    # NOTE: the 汀步 (stepping stones) across the pond were removed. They had
    # previously been rendered as myvillage:rockery_block[variant=standalone] —
    # each block reads in-game as an independent mini-mountain, so a path of
    # them read as a row of stone-textured spikes. They were later switched to
    # flat minecraft:stone / mossy_cobblestone, but that left an unrelated row of
    # mossy stones cutting across the pond which read as clutter rather than
    # water. With the 假山 now sited as a 水心假山 (island in the pond), the pond
    # is a pure water feature; players reach the 亭 from the garden shore, not
    # across the pond, so the stepping path is not needed for walkability.

    # NOTE: the previous "extra rockery for large-scale 花园" block scattered a
    # row of generic standalone rockery_blocks east of the 亭. Each such block
    # renders as an independent mini-mountain (Minecraft block models do not fuse
    # across cells), so it read in-game as a row of stone-textured spikes
    # ("一列小尖刺") rather than a second 假山. The hero 假山 above is the intended
    # mountain; the scattered row was removed so the garden shows one readable
    # 大假山 instead of a spike field. If a second rockery is wanted later it must
    # also go through place_hero_rockery (a self-contained stacked sculpt), not
    # the generic heightfield scatter.

    # 水边石阶与小桥 (PATH_WATERSIDE): a stairs + slab bridge spanning the pond's
    # narrowest crossing to the 水心假山 (island rockery) (path-surface-zoning task
    # 4.1). The rockery is an island in the pond, so the bridge is the only dry
    # approach to it. The bridge is a row of oak/spruce slabs at the water surface
    # y (flat, walkable — reads as a plank bridge, unlike the deleted stepping-
    # stone rockery_blocks that read as a spike row); the descent to the waterline
    # is a stone_brick_stairs on each shore. No rockery_block cells are written.
    bridge_cells: Set[Cell2] = set()
    if rockery_node is not None and rockery_node.cells:
        # Find the narrowest water crossing: the row (constant z) with the fewest
        # water cells between a dry shore cell and the rockery island.
        island = set(rockery_node.cells)
        water = set(water_cells)
        best_span: Optional[List[Cell2]] = None
        best_len = 1 << 30
        island_zs = sorted({z for _, z in island})
        # Walk a horizontal (constant-z) bridge from the rockery's nearest edge
        # outward toward a dry shore cell, across water.
        for iz in (min(island_zs), max(island_zs)):
            for (ix, _iz) in ((x, z) for (x, z) in island if z == iz):
                for step in (1, -1):
                    span: List[Cell2] = []
                    cx = ix + step
                    while 1 <= cx <= lot_w - 2:
                        if (cx, iz) in island:
                            break
                        if (cx, iz) in water:
                            span.append((cx, iz))
                            cx += step
                            continue
                        # Dry shore reached — this span is a valid crossing.
                        if span and len(span) < best_len:
                            best_len = len(span)
                            best_span = list(span)
                            best_span.append((cx, iz))  # shore anchor
                        break
        if best_span:
            water_y = -1  # pond water surface y (garden ground is at y=-1)
            slab = style.slot_entry("PATH_WATERSIDE", "_slab",
                                    "minecraft:oak_slab")
            stair_base = style.slot_entry("PATH_WATERSIDE", "_stairs",
                                          "minecraft:stone_brick_stairs")
            stair_base = stair_base.split("[", 1)[0]
            for (bx, bz) in best_span:
                if (bx, bz) in water:
                    # Slab at the water surface — flat walkable plank.
                    compound.grid.set((bx, water_y, bz), slab,
                                      ["DETAIL", "GROUND", "PROTECTED"],
                                      PRIORITY["DETAIL"], "PATH_WATERSIDE",
                                      force=True)
                    bridge_cells.add((bx, bz))
                else:
                    # Shore anchor: a stone_brick_stairs descending to the
                    # waterline (the step down onto the bridge).
                    compound.grid.set((bx, water_y, bz),
                                      f"{stair_base}[facing=north,half=bottom]",
                                      ["DETAIL", "GROUND", "PROTECTED"],
                                      PRIORITY["DETAIL"], "PATH_WATERSIDE",
                                      force=True)
                    bridge_cells.add((bx, bz))
    if bridge_cells:
        bridge_clear = bridge_cells | (_chebyshev_ring(bridge_cells) & set(water_cells))
        _clear_lily_pads(compound, bridge_clear)
        compound.parcel_nodes.append(
            ParcelNode("waterside_bridge", "waterside_bridge", bridge_cells, {
                "span_count": len(bridge_cells),
                "floor_slot": "PATH_WATERSIDE",
                "form": "slab_bridge",
            }))

    # 水边廊 (shoreside gallery): a short, straight covered_gallery run on one
    # clean bank. Earlier versions converted every dry noise-shore cell into a
    # 3D gallery, which made ragged roof/column clutter and even caught the
    # rockery island as "shore". Keep the freeform pond, but make the gallery a
    # composed architectural strip.
    gallery_blocked = (
        set(rockery_node.cells if rockery_node is not None else set())
        | _chebyshev_ring(set(rockery_node.cells if rockery_node is not None else set()))
        | compound.building_cells()
        | compound.node_cells("perimeter_wall", "screen_wall", "garden_pavilion")
        | bridge_cells
        | _chebyshev_ring(bridge_cells)
    )
    shore, water_side, water_edge = _select_waterside_gallery_run(
        lot_w, gy0, gy1, set(water_cells), gallery_blocked,
        preferred_side="north", max_len=7)
    if shore and water_side:
        base_y = min(_natural_surface_y(compound, c) for c in shore)
        # For a two-cell-deep waterside strip, using the water side as the
        # post-side places posts on the dry back row; the front row stays open
        # to the pond with a supported low railing.
        _place_covered_gallery_3d(compound, style, shore, base_y,
                                  open_side=water_side, post_side=water_side,
                                  rail_on_footprint=True)
        clear = water_edge | (_chebyshev_ring(water_edge) & set(water_cells))
        _clear_lily_pads(compound, clear)
        compound.parcel_nodes.append(
            ParcelNode("waterside_gallery", "covered_gallery", shore, {
                "form": "shoreside", "along": "pond_straight_shore",
                "floor_slot": "PATH_GALLERY", "water_side": water_side,
                "water_edge_cells": [list(c) for c in sorted(water_edge)],
                "rendered_as": "3d_covered_gallery",
            }))

    garden_cells = {(x, z) for x in range(1, lot_w - 1)
                    for z in range(gy0, gy1 + 1)}
    compound.parcel_nodes.append(
        ParcelNode("garden_band", "garden", garden_cells,
                   {"garden_scale": variant.garden_scale,
                    "bbox": [1, gy0, lot_w - 2, gy1]}))


def generate_mansion(seed: int, style: Optional[Style] = None,
                     variant: Optional["MansionVariant"] = None) -> CompoundGraph:
    """Generate a 3-进 江南大宅 via the enclosure-planning model.

    Replaces the z-band-slice layout (rebuild-mansion-enclosure-plan): the
    placement manifest is planned first (oriented buildings against anchor walls
    per the form rule), then realized (buildings + gate_house through-building +
    inner gates + 照壁 + 主院 plinth), then ground/garden/path are layered on.
    """
    style = style or load_style("chinese_mansion")
    variant = variant or select_mansion_variant(seed)
    lot_w, lot_d = MANSION_SIZE[variant.courtyard_size]
    axis = lot_w // 2

    # 1) Plan the enclosure manifest (form-rule facings + anchor walls + origins).
    placements, gates, gate_inner_z = _plan_mansion_enclosure(variant, lot_w, lot_d, axis)
    gate_zs = sorted(z for _, z in gates)

    # 后院/花园 split (Arc 6): previously both bands were [ermen_z+1, lot_d-2]
    # (identical), so the 绣楼 sat the full depth of its would-be 后院 straight
    # into the 花园. Apply the already-computed back_d so the 后院 (绣楼 home) and
    # the 花园 (garden features) get distinct z-intervals, separated by the
    # 月洞门 screen wall at garden_band[0].
    _front_d, _main_d, back_d, _garden_d = _mansion_yard_depths(
        lot_d, variant.garden_scale)
    if len(gate_zs) >= 2:
        ermen_z = gate_zs[1]
        back_yard_band = [ermen_z + 1, ermen_z + back_d]
        garden_band = [ermen_z + back_d + 1, lot_d - 2]
    else:
        back_yard_band = [1, lot_d - 2]
        garden_band = [1, lot_d - 2]

    compound = CompoundGraph(
        style.style_id, seed, variant, (lot_w, lot_d), axis,
        meta={
            "layout_strategy": "mansion_enclosure",
            "gate_side": "south",
            "front_yard_band": [1, gate_zs[0] - 1] if gate_zs else [1, lot_d - 2],
            "yimen_band": [gate_zs[0], gate_zs[0]] if gate_zs else [1, 1],
            "main_yard_band": [gate_zs[0] + 1, gate_zs[1] - 1] if len(gate_zs) >= 2 else [1, lot_d - 2],
            "ermen_band": [gate_zs[1], gate_zs[1]] if len(gate_zs) >= 2 else [1, 1],
            "back_yard_band": back_yard_band,
            "garden_band": garden_band,
            "outer_yard_band": [1, gate_zs[0] - 1] if gate_zs else [1, lot_d - 2],
            "inner_gate_band": [gate_zs[0], gate_zs[0]] if gate_zs else [1, 1],
        })

    # 2) Perimeter wall, gapped around the gate_house (Task 2.2). The gate_house
    #    placement is the first manifest entry; its x-extent becomes the gap.
    gh = next(p for p in placements if p.archetype == "gate_house")
    gh_fp = dict(gh.extra_overrides).get("footprint", _MANSION_GATE_FOOTPRINT[variant.gate_type])
    _add_chinese_perimeter(compound, style,
                           gate_house_gap=(gh.x0, gh.x0 + gh_fp[0] - 1))

    # 3) Realize the manifest: oriented buildings + gate_house + inner gates +
    #    照壁 + 主院 plinth. facing flows through form_overrides per placement.
    _realize_mansion_enclosure(compound, style, variant, placements, gates, gate_inner_z)
    # 3b) 主院 抄手游廊 (Arc 5): east + west 3D galleries tying the 仪门 flanks
    #     to the 敞厅 flanks. Placed after the buildings + plinth so the gallery
    #     base sits on PLATFORM_STONE and clears the realized footprints.
    _layout_main_yard_galleries(compound, style)

    # 4) Layer ground + garden + path + transition stairs on the realized layout.
    #    Ground before garden so the 假山 foot / 水池 surface stamp on top of the
    #    y=-1 fill; path last so it routes through the realized yard space and
    #    treats every door-cell as a mandatory endpoint (path-as-input, D4).
    _place_yard_ground(compound, style)
    bands_for_garden = {"garden_band": compound.meta["garden_band"]}
    _layout_garden(compound, style, bands_for_garden, seed, variant)
    _route_complete_path(compound, style)
    # 4b) 月洞门 passage + tour route (path-surface-zoning task 2.3/2.4). The
    #     screen wall sits at the near edge of the 花园 band (the 后院↔花园
    #     boundary), with a 圆洞门 opening on the axis; the tour route then winds
    #     from the passage inner cell through the garden waypoints. Placed after
    #     _route_complete_path so the formal backbone is established and the tour
    #     can assert it shares no cell with the formal route (design D6).
    garden_z0, _garden_z1 = compound.meta["garden_band"]
    lot_w_m, _lot_d_m = compound.lot_size
    passage_node, passage_inner = place_moon_gate_screen(
        compound, style, axis, garden_z0, 1, lot_w_m - 2)
    _route_tour_path(compound, style, passage_inner)
    _place_band_transition_stairs(compound, style)
    return compound


def _small_courtyard_variant(seed: int, size: str) -> CompoundVariant:
    rng = random.Random(seed)
    return CompoundVariant(
        courtyard_size=size,
        water_form=rng.choice(WATER_FORMS),
        planting_layout=rng.choice(PLANTING_LAYOUTS),
        roof_grade="town_roof_mix",
        gate_type=rng.choice(("plain_gate", "plain_gate", "lantern_gate")),
        symmetry="mild_asymmetry",
    )


def _town_roster(roster: Sequence[str]) -> Tuple[str, ...]:
    # The cultivation_town group roster now also contains civic-core vertical
    # landmarks (pagoda/pavilion/bell_drum_tower); those are skyline elements,
    # not courtyard street-block tissue, so filter the roster down to the
    # courtyard-compatible archetypes the small-block generator understands.
    values = tuple(a for a in (roster or ()) if a in DEFAULT_TOWN_ROSTER)
    return values or DEFAULT_TOWN_ROSTER


def _select_courtyard_archetypes(seed: int, roster: Sequence[str],
                                 preferred_focal: Optional[str] = None) -> Tuple[str, str, str]:
    roster = _town_roster(roster)
    rng = random.Random(seed + 313)
    compact = tuple(a for a in roster if a in ("cultivation_house", "cultivation_shop", "cultivation_market"))
    side_pool = compact or roster
    focal_pool = tuple(a for a in roster if a in (
        "cultivation_market", "cultivation_inn", "town_shrine",
        "cultivation_shop", "cultivation_house",
    ))
    if preferred_focal in roster:
        focal = preferred_focal
    else:
        focal = rng.choice(focal_pool or roster)
    return focal, rng.choice(side_pool), rng.choice(side_pool)


def generate_small_courtyard(seed: int, style: Optional[Style] = None,
                             roster: Sequence[str] = DEFAULT_TOWN_ROSTER,
                             size: str = "town_small",
                             gate_side: str = "south",
                             preferred_focal: Optional[str] = None) -> CompoundGraph:
    """Generate a compact walled town courtyard for street-block tiling."""
    style = style or load_style("cultivation_town")
    if size not in COURTYARD_SIZE:
        raise ValueError(f"unknown courtyard size {size!r}")
    if gate_side not in ("south", "north"):
        raise ValueError(f"unsupported small courtyard gate side {gate_side!r}")

    lot_w, lot_d = COURTYARD_SIZE[size]
    axis = lot_w // 2
    group_id = "cultivation_town" if style.style_id == "cultivation_town" else None
    last_error = None
    for attempt in range(12):
        attempt_seed = seed + attempt * 7919
        variant = _small_courtyard_variant(attempt_seed, size)
        compound = CompoundGraph(
            style.style_id,
            attempt_seed,
            variant,
            (lot_w, lot_d),
            axis,
            meta={
                "layout_strategy": "small_courtyard_unit",
                "gate_side": gate_side,
            },
        )
        focal, west, east = _select_courtyard_archetypes(
            attempt_seed, roster, preferred_focal)
        slot_seed = attempt_seed * 1009
        focal_ctx = generate_subbuilding(style, focal, slot_seed + 1, None, group_id)
        west_ctx = generate_subbuilding(style, west, slot_seed + 2, None, group_id)
        east_ctx = generate_subbuilding(style, east, slot_seed + 3, None, group_id)

        focal_x0, focal_z0, focal_x1, focal_z1 = _context_bounds_2d(focal_ctx)
        west_x0, west_z0, west_x1, west_z1 = _context_bounds_2d(west_ctx)
        east_x0, east_z0, east_x1, east_z1 = _context_bounds_2d(east_ctx)
        focal_w = focal_x1 - focal_x0 + 1
        focal_d = focal_z1 - focal_z0 + 1
        west_w = west_x1 - west_x0 + 1
        west_d = west_z1 - west_z0 + 1
        east_w = east_x1 - east_x0 + 1
        east_d = east_z1 - east_z0 + 1

        focal_min_x = max(2, min(lot_w - 3 - focal_w, axis - focal_w // 2))
        west_min_x = 2
        east_min_x = lot_w - 2 - east_w
        if gate_side == "north":
            focal_min_z = 3
            side_min_z = lot_d - 4 - max(west_d, east_d)
        else:
            focal_min_z = lot_d - 3 - focal_d
            side_min_z = 4
        if focal_min_z <= 1 or side_min_z <= 1:
            last_error = "building_depth_exceeds_town_small"
            continue
        if east_min_x - (west_min_x + west_w) < 5:
            last_error = "side_buildings_leave_no_tianjing_width"
            continue

        focal_slot = _translate_context_to_min(
            compound, f"focal_{focal}", focal_ctx, focal_min_x, focal_min_z)
        west_slot = _translate_context_to_min(
            compound, f"west_{west}", west_ctx, west_min_x, side_min_z)
        east_slot = _translate_context_to_min(
            compound, f"east_{east}", east_ctx, east_min_x, side_min_z)
        building_overlap = (
            focal_slot.footprint & west_slot.footprint |
            focal_slot.footprint & east_slot.footprint |
            west_slot.footprint & east_slot.footprint
        )
        if building_overlap:
            last_error = f"small_courtyard_building_overlap: {sorted(building_overlap)[:8]}"
            continue

        _add_perimeter(compound, style)
        if gate_side == "north":
            courtyard = (
                max(x for x, _ in west_slot.footprint) + 2,
                max(z for _, z in focal_slot.footprint) + 2,
                min(x for x, _ in east_slot.footprint) - 2,
                min(z for _, z in west_slot.footprint) - 2,
            )
        else:
            courtyard = (
                max(x for x, _ in west_slot.footprint) + 2,
                max(z for _, z in west_slot.footprint) + 2,
                min(x for x, _ in east_slot.footprint) - 2,
                min(z for _, z in focal_slot.footprint) - 2,
            )
        if courtyard[0] > courtyard[2] or courtyard[1] > courtyard[3]:
            last_error = f"invalid_tianjing_bounds: {courtyard}"
            continue
        tianjing = _bounded(_rect(*courtyard), lot_w, lot_d,
                            compound.building_cells() | compound.node_cells("perimeter_wall"))
        if len(tianjing) < 9:
            last_error = f"tianjing_too_small: {len(tianjing)}"
            continue
        compound.parcel_nodes.append(
            ParcelNode("tianjing", "tianjing", tianjing, {"bounds": list(courtyard)}))
        try:
            _place_landscape(compound, style, courtyard)
            _place_yard_ground(compound, style)
            _route_complete_path(compound, style)
        except ValueError as exc:
            last_error = str(exc)
            continue
        compound.meta["courtyard"] = list(courtyard)
        compound.meta["roster_slots"] = [slot.archetype for slot in compound.building_slots]

        report = validate_small_courtyard(compound)
        if report["passed"]:
            return compound
        last_error = report["errors"]
    raise ValueError(f"failed to generate valid small courtyard: {last_error}")


def _shift_cells(cells: Iterable[Cell2], dx: int, dz: int) -> Set[Cell2]:
    return {(x + dx, z + dz) for x, z in cells}


def _shift_gate_opening(meta: dict, dx: int, dz: int) -> dict:
    shifted = dict(meta)
    opening = shifted.get("gate_opening")
    if opening:
        shifted["gate_opening"] = [
            opening[0] + dx,
            opening[1] + dz,
            opening[2] + dx,
            opening[3] + dz,
        ]
    return shifted


def _copy_compound_into(parent: CompoundGraph, child: CompoundGraph,
                        prefix: str, dx: int, dz: int,
                        wall_seen: Set[Cell2]) -> None:
    for (x, y, z), cell in list(child.grid.iter_cells()):
        parent.grid.set((x + dx, y, z + dz), cell.state, cell.tags,
                        cell.priority, cell.slot)
    for entity in child.grid.entities:
        parent.grid.add_entity(entity, (dx, 0, dz))
    for node in child.parcel_nodes:
        cells = _shift_cells(node.cells, dx, dz)
        if node.type == "perimeter_wall":
            fresh = cells - wall_seen
            wall_seen.update(fresh)
            cells = fresh
        if not cells:
            continue
        parent.parcel_nodes.append(
            ParcelNode(f"{prefix}_{node.id}", node.type, cells,
                       _shift_gate_opening(node.meta, dx, dz)))
    for slot in child.building_slots:
        shifted_graph = MassingGraph(
            meta={**slot.graph.meta, "town_block_offset": [dx, 0, dz]},
            nodes=list(slot.graph.nodes),
        )
        parent.building_slots.append(
            BuildingSlot(
                f"{prefix}_{slot.id}",
                slot.archetype,
                (slot.origin[0] + dx, slot.origin[1], slot.origin[2] + dz),
                shifted_graph,
                _shift_cells(slot.footprint, dx, dz),
                slot.quality,
            ))


def _town_column_origins(variant: TownBlockVariant, lot_w: int) -> Tuple[List[int], Set[Cell2]]:
    origins: List[int] = []
    lane_after = 0 if variant.lane and variant.courtyards_per_row >= 3 else None
    x = 0
    for col in range(variant.courtyards_per_row):
        origins.append(x)
        if col == variant.courtyards_per_row - 1:
            break
        if lane_after == col:
            lane_x0 = x + lot_w
            lane_x1 = lane_x0 + TOWN_LANE_WIDTH - 1
            x = lane_x1 + 1
        else:
            x += lot_w - 1
    return origins, set()


def _town_row_layout(variant: TownBlockVariant, lot_d: int) -> Tuple[List[Tuple[int, str]], Set[Cell2], int]:
    if variant.rows == 1:
        row_layout = [(variant.street_width, "south")]
        block_d = variant.street_width + lot_d
        street = _rect(0, 0, 0, variant.street_width - 1)
    elif variant.rows == 2:
        row_layout = [(0, "north"), (lot_d + variant.street_width, "south")]
        block_d = lot_d * 2 + variant.street_width
        street = _rect(0, lot_d, 0, lot_d + variant.street_width - 1)
    else:
        raise ValueError(f"unsupported town-block row count {variant.rows}")
    return row_layout, street, block_d


def generate_town_block(seed: int, style: Optional[Style] = None,
                        roster: Sequence[str] = DEFAULT_TOWN_ROSTER,
                        variant: Optional[TownBlockVariant] = None) -> CompoundGraph:
    """Generate a flattened cultivation-town street block from small courtyards."""
    style = style or load_style("cultivation_town")
    variant = variant or select_town_block_variant(seed)
    lot_w, lot_d = COURTYARD_SIZE[variant.courtyard_size]
    col_origins, _ = _town_column_origins(variant, lot_w)
    row_layout, street_template, block_d = _town_row_layout(variant, lot_d)
    block_w = col_origins[-1] + lot_w
    street_cells = {
        (x, z)
        for x in range(block_w)
        for _, z in street_template
    }
    lane_cells: Set[Cell2] = set()
    if variant.lane and variant.courtyards_per_row >= 3:
        lane_x0 = col_origins[0] + lot_w
        lane_x1 = lane_x0 + TOWN_LANE_WIDTH - 1
        lane_cells = {
            (x, z)
            for x in range(lane_x0, lane_x1 + 1)
            for z in range(block_d)
        }

    compound = CompoundGraph(
        style.style_id,
        seed,
        variant,
        (block_w, block_d),
        block_w // 2,
        meta={
            "layout_strategy": "courtyard_street_block",
            "variant_axes": variant.to_dict(),
            "courtyards": [],
            "street_width": variant.street_width,
            "lane_width": TOWN_LANE_WIDTH if variant.lane else 0,
        },
    )

    wall_seen: Set[Cell2] = set()
    for row, (origin_z, gate_side) in enumerate(row_layout):
        for col, origin_x in enumerate(col_origins):
            child_seed = seed * 1009 + row * 101 + col * 17
            preferred = None
            if variant.corner_frontage and col in (0, variant.courtyards_per_row - 1):
                preferred = "cultivation_market" if "cultivation_market" in roster else "cultivation_shop"
            child = generate_small_courtyard(
                child_seed,
                style,
                roster,
                variant.courtyard_size,
                gate_side=gate_side,
                preferred_focal=preferred,
            )
            prefix = f"court_r{row + 1}c{col + 1}"
            _copy_compound_into(compound, child, prefix, origin_x, origin_z, wall_seen)
            wall_meta = next(n.meta for n in child.parcel_nodes if n.type == "perimeter_wall")
            gate = wall_meta["gate_opening"]
            compound.meta["courtyards"].append({
                "id": prefix,
                "row": row,
                "col": col,
                "origin": [origin_x, origin_z],
                "lot_size": [lot_w, lot_d],
                "gate_side": gate_side,
                "gate_opening": [
                    gate[0] + origin_x,
                    gate[1] + origin_z,
                    gate[2] + origin_x,
                    gate[3] + origin_z,
                ],
                "roster_slots": child.meta.get("roster_slots", []),
            })

    circulation = street_cells | lane_cells
    occupied = (compound.building_cells() |
                compound.node_cells("perimeter_wall", "water_feature", "planting", "tianjing"))
    overlap = circulation & occupied
    if overlap:
        raise ValueError(f"town circulation overlaps courtyard cells: {sorted(overlap)[:8]}")
    _put_cells(compound, "street_network", "street", street_cells,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
               slot="GROUND_PATH")
    if lane_cells:
        _put_cells(compound, "lane_network", "lane", lane_cells,
                   style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
                   slot="GROUND_PATH")

    report = validate_town_block(compound)
    if not report["passed"]:
        raise ValueError(f"invalid town block: {report['errors']}")
    return compound


def _slot_bounds(slot: BuildingSlot) -> Tuple[int, int, int, int]:
    return (
        min(x for x, _ in slot.footprint),
        min(z for _, z in slot.footprint),
        max(x for x, _ in slot.footprint),
        max(z for _, z in slot.footprint),
    )


def _mark_sect_slot(slot: BuildingSlot, level: int, importance_tier: int,
                    role: str) -> None:
    slot.graph.meta["terrace_level"] = level
    slot.graph.meta["importance_tier"] = importance_tier
    slot.graph.meta["compound_role"] = role


def _place_terrace(compound: CompoundGraph, style: Style, node_id: str,
                   level: int, base_y: int,
                   bounds: Tuple[int, int, int, int]) -> Set[Cell2]:
    cells = _rect(*bounds)
    state = style.primary("BASE_STONE")
    for x, z in cells:
        for y in range(-1, base_y):
            compound.grid.set((x, y, z), state, ["STRUCTURE", "GROUND"],
                              PRIORITY["STRUCTURE"], "BASE_STONE")
    compound.parcel_nodes.append(
        ParcelNode(node_id, "terrace", cells, {
            "level": level,
            "relative_y": base_y,
            "bounds": list(bounds),
        }))
    return cells


def _place_sect_courtyard(compound: CompoundGraph, style: Style, level: int,
                          base_y: int, bounds: Tuple[int, int, int, int]) -> Set[Cell2]:
    blocked = compound.building_cells() | compound.node_cells("circulation", "link")
    cells = _bounded(_rect(*bounds), compound.lot_size[0], compound.lot_size[1], blocked)
    for x, z in cells:
        compound.grid.set((x, base_y - 1, z), style.primary("GROUND_PATH"),
                          ["DETAIL", "GROUND"], PRIORITY["DETAIL"],
                          "GROUND_PATH")
    compound.parcel_nodes.append(
        ParcelNode(f"courtyard_level_{level}", "courtyard", cells, {
            "level": level,
            "relative_y": base_y,
            "bounds": list(bounds),
        }))
    return cells


def _place_monumental_stair(compound: CompoundGraph, style: Style, node_id: str,
                            from_level: int, to_level: int,
                            from_y: int, to_y: int,
                            cells: Set[Cell2]) -> None:
    if to_level != from_level + 1:
        raise ValueError(f"stairway must connect adjacent levels: {from_level}->{to_level}")
    z0 = min(z for _, z in cells)
    z1 = max(z for _, z in cells)
    span = max(1, z1 - z0)
    slab = style.slot_entry("ROOF_DARK", "_slab")
    for x, z in sorted(cells):
        t = (z - z0) / span
        y = round(from_y + (to_y - from_y) * t) - 1
        compound.grid.set((x, y, z), slab, ["DETAIL", "GROUND"],
                          PRIORITY["DETAIL"], "ROOF_DARK")
        if x in (min(px for px, _ in cells), max(px for px, _ in cells)):
            compound.grid.set((x, y + 1, z), style.primary("BASE_STONE"),
                              ["STRUCTURE"], PRIORITY["STRUCTURE"], "BASE_STONE")
    compound.parcel_nodes.append(
        ParcelNode(node_id, "circulation", cells, {
            "kind": "monumental_stair",
            "from_level": from_level,
            "to_level": to_level,
            "from_relative_y": from_y,
            "to_relative_y": to_y,
        }))


def _place_sect_link(compound: CompoundGraph, style: Style, node_id: str,
                     kind: str, endpoints: Tuple[str, str],
                     cells: Set[Cell2], base_y: int,
                     over: Optional[str] = None) -> None:
    floor = style.primary("GROUND_PATH")
    roof = style.slot_entry("ROOF_DARK", "_slab")
    rail = style.optional_slot_entry("DETAIL_WOOD", "fence",
                                     style.primary("FRAME_WOOD"))
    xs = [x for x, _ in cells]
    zs = [z for _, z in cells]
    min_x, max_x = min(xs), max(xs)
    min_z, max_z = min(zs), max(zs)
    for index, (x, z) in enumerate(sorted(cells)):
        compound.grid.set((x, base_y, z), floor, ["DETAIL", "GROUND"],
                          PRIORITY["DETAIL"], "GROUND_PATH")
        edge = x in (min_x, max_x) if max_x - min_x < max_z - min_z else z in (min_z, max_z)
        if kind == "covered_gallery":
            if index % 3 == 0:
                compound.grid.set((x, base_y + 1, z), rail, ["STRUCTURE"],
                                  PRIORITY["STRUCTURE"], "DETAIL_WOOD")
                compound.grid.set((x, base_y + 2, z), rail, ["STRUCTURE"],
                                  PRIORITY["STRUCTURE"], "DETAIL_WOOD")
            compound.grid.set((x, base_y + 3, z), roof, ["ROOF"],
                              PRIORITY["ROOF"], "ROOF_DARK")
        elif edge or index % 4 == 0:
            compound.grid.set((x, base_y + 1, z), rail, ["STRUCTURE"],
                              PRIORITY["STRUCTURE"], "DETAIL_WOOD")
    meta = {
        "kind": kind,
        "endpoints": list(endpoints),
        "circulation": True,
        "structural": True,
        "relative_y": base_y,
    }
    if over:
        meta["over"] = over
    compound.parcel_nodes.append(ParcelNode(node_id, "link", cells, meta))


def generate_sect_compound(seed: int, style: Optional[Style] = None) -> CompoundGraph:
    style = style or load_style("cultivation_sect")
    variant = CompoundVariant(
        courtyard_size="sect_mountain_four_level",
        water_form="water_front_cloud_sea",
        planting_layout="mountain_cliff_edges",
        roof_grade="tiered_eave_roof",
        gate_type="moon_gate_axis",
        symmetry="axial",
    )
    lot_w, lot_d = (77, 96)
    axis = lot_w // 2
    terrace_defs = [
        {"id": "foot_terrace", "level": 0, "relative_y": 0,
         "bounds": (3, 1, lot_w - 4, 18), "courtyard": (22, 8, 54, 17)},
        {"id": "disciple_terrace", "level": 1, "relative_y": 2,
         "bounds": (6, 19, lot_w - 7, 38), "courtyard": (27, 23, 50, 35)},
        {"id": "alchemy_terrace", "level": 2, "relative_y": 4,
         "bounds": (9, 39, lot_w - 10, 57), "courtyard": (20, 42, 51, 55)},
        {"id": "summit_terrace", "level": 3, "relative_y": 6,
         "bounds": (12, 58, lot_w - 19, lot_d - 3), "courtyard": (20, 63, 56, 76)},
    ]
    siting_context = {
        "mountain_slope": "south_to_north_ascent",
        "cliff_back": {"edge": "north", "z": lot_d - 2},
        "water_front": {"edge": "south", "cells": [[5, 2], [20, 12], [lot_w - 21, 2], [lot_w - 6, 12]]},
        "cloud_sea": {"edge": "east", "void": "east_cloud_gap"},
    }
    compound = CompoundGraph(
        style.style_id,
        seed,
        variant,
        (lot_w, lot_d),
        axis,
        meta={
            "layout_strategy": "sect_terraced_axial_compound",
            "level_count": len(terrace_defs),
            "terraces": [
                {**t, "bounds": list(t["bounds"]), "courtyard": list(t["courtyard"])}
                for t in terrace_defs
            ],
            "siting_context": siting_context,
        },
    )

    for terrace in terrace_defs:
        _place_terrace(compound, style, terrace["id"], terrace["level"],
                       terrace["relative_y"], terrace["bounds"])

    slot_seed = seed * 1009
    gate_ctx = generate_subbuilding(style, "sect_gate", slot_seed + 1, None,
                                    "cultivation_sect", importance_tier=0)
    quarters_ctx = generate_subbuilding(style, "disciple_quarters", slot_seed + 2, None,
                                        "cultivation_sect", importance_tier=1)
    alchemy_ctx = generate_subbuilding(style, "alchemy_room", slot_seed + 3, None,
                                       "cultivation_sect", importance_tier=1)
    scripture_ctx = generate_subbuilding(style, "scripture_pavilion", slot_seed + 4, None,
                                         "cultivation_sect", importance_tier=3)
    main_ctx = generate_subbuilding(style, "sect_main_hall", slot_seed + 5, None,
                                    "cultivation_sect", importance_tier=3)

    gate = gate_ctx.graph.get("main")
    quarters = quarters_ctx.graph.get("main")
    alchemy = alchemy_ctx.graph.get("main")
    scripture = scripture_ctx.graph.get("main")
    main = main_ctx.graph.get("main")

    gate_slot = _translate_context(
        compound, "sect_gate", gate_ctx,
        (axis - gate.size[0] // 2, 0, 5))
    quarters_slot = _translate_context(
        compound, "disciple_quarters", quarters_ctx,
        (8, 2, 23))
    alchemy_slot = _translate_context(
        compound, "alchemy_room", alchemy_ctx,
        (lot_w - 10 - alchemy.size[0], 4, 43))
    scripture_slot = _translate_context(
        compound, "scripture_pavilion", scripture_ctx,
        (axis - scripture.size[0] // 2, 6, 62))
    main_slot = _translate_context(
        compound, "sect_main_hall", main_ctx,
        (axis - main.size[0] // 2, 6, 77))

    slot_levels = {
        gate_slot.id: 0,
        quarters_slot.id: 1,
        alchemy_slot.id: 2,
        scripture_slot.id: 3,
        main_slot.id: 3,
    }
    importance_tiers = {
        gate_slot.id: 0,
        quarters_slot.id: 1,
        alchemy_slot.id: 1,
        scripture_slot.id: 3,
        main_slot.id: 3,
    }
    _mark_sect_slot(gate_slot, 0, 0, "mountain_gate")
    _mark_sect_slot(quarters_slot, 1, 1, "disciple_court")
    _mark_sect_slot(alchemy_slot, 2, 1, "alchemy_court")
    _mark_sect_slot(scripture_slot, 3, 3, "summit_pagoda")
    _mark_sect_slot(main_slot, 3, 3, "principal_hall")

    summit_front = min(z for _, z in main_slot.footprint)
    path_cells = {(axis, z) for z in range(1, summit_front)}
    path_cells |= {(axis - 1, z) for z in range(7, summit_front)}
    path_cells |= {(axis + 1, z) for z in range(7, summit_front)}
    for x, z in sorted(path_cells):
        base_y = 0
        for terrace in terrace_defs:
            _x0, z0, _x1, z1 = terrace["bounds"]
            if z0 <= z <= z1:
                base_y = terrace["relative_y"]
        compound.grid.set((x, base_y - 1, z), style.primary("GROUND_PATH"),
                          ["DETAIL", "GROUND"], PRIORITY["DETAIL"],
                          "GROUND_PATH")
    compound.parcel_nodes.append(
        ParcelNode("central_axis_path", "path", path_cells, {
            "kind": "ritual_axis",
            "from": "sect_gate",
            "to": "sect_main_hall",
        }))

    stair_specs = [
        ("stair_level_0_1", 0, 1, 0, 2, 16, 20),
        ("stair_level_1_2", 1, 2, 2, 4, 36, 40),
        ("stair_level_2_3", 2, 3, 4, 6, 56, 61),
    ]
    for node_id, from_level, to_level, from_y, to_y, z0, z1 in stair_specs:
        stair_cells = {(axis + dx, z) for dx in (-2, -1, 0, 1, 2)
                       for z in range(z0, z1 + 1)}
        _place_monumental_stair(compound, style, node_id, from_level, to_level,
                                from_y, to_y, stair_cells)

    cloud_gap = _rect(lot_w - 18, 66, lot_w - 14, 72)
    for x, z in sorted(cloud_gap):
        compound.grid.set((x, 3, z), "minecraft:white_stained_glass",
                          ["DETAIL"], PRIORITY["DETAIL"], "WATER")
    compound.parcel_nodes.append(
        ParcelNode("east_cloud_gap", "cloud_sea", cloud_gap, {
            "kind": "cloud_sea_gap",
            "level": 3,
            "relative_y": 3,
        }))
    overlook = _rect(lot_w - 13, 65, lot_w - 5, 72)
    _put_cells(compound, "east_cantilever_overlook", "cantilever_terrace",
               overlook, style.primary("BASE_STONE"), ["STRUCTURE", "GROUND"],
               y=5, slot="BASE_STONE", meta={
                   "level": 3,
                   "relative_y": 6,
                   "cantilevered": True,
                   "over": "east_cloud_gap",
               })

    gallery_z = max(z for _, z in scripture_slot.footprint) + 1
    gallery_cells = {(axis + dx, gallery_z) for dx in (-1, 0, 1)}
    gallery_cells |= {(axis + dx, gallery_z + 1) for dx in (-1, 0, 1)}
    _place_sect_link(compound, style, "covered_gallery_scripture_hall",
                     "covered_gallery", (scripture_slot.id, main_slot.id),
                     gallery_cells, 6)

    scripture_east = max(x for x, _ in scripture_slot.footprint) + 1
    bridge_z = (min(z for _, z in scripture_slot.footprint) +
                max(z for _, z in scripture_slot.footprint)) // 2
    bridge_cells = {(x, bridge_z) for x in range(scripture_east, lot_w - 12)}
    bridge_cells |= {(x, bridge_z + 1) for x in range(scripture_east, lot_w - 12)}
    _place_sect_link(compound, style, "flying_bridge_scripture_overlook",
                     "flying_bridge", (scripture_slot.id, "east_cantilever_overlook"),
                     bridge_cells, 7, over="east_cloud_gap")

    water_front = (_rect(5, 2, 20, 12) |
                   _rect(lot_w - 21, 2, lot_w - 6, 12))
    _clear_cells(compound, water_front, y0=-1, y1=0)
    _put_cells(compound, "water_front", "water_feature", water_front,
               "minecraft:water", ["DETAIL", "GROUND"], y=-1,
               slot="WATER", meta={"siting": "water_front", "level": 0})
    planting = (_rect(3, 14, 9, 18) | _rect(lot_w - 10, 14, lot_w - 4, 18) |
                _rect(12, lot_d - 10, 17, lot_d - 3))
    plant_states = ("minecraft:moss_block", "minecraft:azalea_leaves",
                    "minecraft:flowering_azalea_leaves")
    for i, (x, z) in enumerate(sorted(planting)):
        compound.grid.set((x, 0, z), plant_states[i % len(plant_states)],
                          ["DETAIL", "GROUND"], PRIORITY["DETAIL"], "PLANTING")
    compound.parcel_nodes.append(
        ParcelNode("mountain_edge_planting", "planting", planting,
                   {"siting": "mountain_edges"}))

    for terrace in terrace_defs:
        courtyard_cells = _place_sect_courtyard(
            compound, style, terrace["level"], terrace["relative_y"], terrace["courtyard"])
        terrace["courtyard_cells"] = len(courtyard_cells)

    compound.meta["hierarchy"] = [
        gate_slot.id,
        quarters_slot.id,
        alchemy_slot.id,
        scripture_slot.id,
        main_slot.id,
    ]
    compound.meta["terrace_levels"] = slot_levels
    compound.meta["importance_tiers"] = importance_tiers
    compound.meta["summit_slots"] = [scripture_slot.id, main_slot.id]
    compound.meta["siting_context"]["cliff_back"]["principal_hall_slot"] = main_slot.id
    compound.meta["siting_context"]["cloud_sea"]["cantilever_slot"] = "east_cantilever_overlook"
    return compound


def validate_compound(compound: CompoundGraph) -> dict:
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_side = compound.meta.get("gate_side", "south")
    buildings = compound.building_cells()
    landscape = compound.node_cells("water_feature", "planting", "water_jar")
    path = compound.node_cells("path")
    perimeter = compound.node_cells("perimeter_wall")
    galleries = [n for n in compound.parcel_nodes if n.type == "covered_gallery"]
    gallery_cells = compound.node_cells("covered_gallery")
    moon = compound.node_cells("moon_platform")
    screen = compound.node_cells("screen_wall")
    tree = compound.node_cells("courtyard_tree")
    inner_gates = [n for n in compound.parcel_nodes if n.type == "inner_gate"]

    outside = [(x, z) for x, z in buildings
               if not (0 < x < lot_w - 1 and 0 < z < lot_d - 1)]
    if outside:
        errors.append(f"building_outside_perimeter: {outside[:8]}")
    overlap = buildings & landscape
    if overlap:
        errors.append(f"building_landscape_overlap: {sorted(overlap)[:8]}")

    gate_z = 0 if gate_side == "south" else lot_d - 1
    gate_wall = {(x, gate_z) for x in range(lot_w)}
    openings = sorted(gate_wall - perimeter)
    if not openings or not any(x == axis for x, _ in openings):
        errors.append("gate_opening_missing_on_axis")
    elif openings != [(x, gate_z) for x in range(openings[0][0], openings[-1][0] + 1)]:
        errors.append(f"multiple_gate_openings: {openings}")

    # Two-yard split invariants (the 一进 definition). The screen wall stands
    # OFF the central axis (照壁侧立 form, per design D2 + the
    # courtyard-voxel-walkability fix): the old on-axis 影壁 sealed the axis
    # and was the root "堵住" defect. The validator now enforces the inverse —
    # no screen-wall cell may lie on the axis.
    outer_band = compound.meta.get("outer_yard_band")
    if not screen:
        errors.append("missing_screen_wall")
    elif any(x == axis for x, _ in screen):
        errors.append("screen_wall_on_axis")
    elif (outer_band and
          not all(outer_band[0] <= z <= outer_band[1] for _, z in screen)):
        errors.append("screen_wall_not_inside_street_gate")
    if len(inner_gates) != 1:
        errors.append(f"inner_gate_count: {len(inner_gates)}")
    else:
        ig_band = compound.meta.get("inner_gate_band")
        oy_band = compound.meta.get("outer_yard_band")
        my_band = compound.meta.get("main_yard_band")
        if ig_band and oy_band and my_band and not (
                oy_band[1] < ig_band[0] and ig_band[1] < my_band[0]):
            errors.append("inner_gate_not_between_yards")
    if len(galleries) != 2:
        errors.append(f"covered_gallery_count: {len(galleries)}")
    else:
        sides = {n.meta.get("side") for n in galleries}
        if sides != {"east", "west"}:
            errors.append(f"covered_gallery_sides: {sorted(sides)}")
        main = next((s for s in compound.building_slots if s.id == "main_hall"), None)
        inner_gate_cells = compound.node_cells("inner_gate")
        for gallery in galleries:
            if not _cells_touch(gallery.cells, inner_gate_cells):
                errors.append(f"covered_gallery_not_connected_to_inner_gate: {gallery.id}")
            if main is not None and not _cells_touch(gallery.cells, main.footprint):
                errors.append(f"covered_gallery_not_connected_to_main_hall: {gallery.id}")
    if not moon:
        errors.append("missing_moon_platform")
    else:
        main = next((s for s in compound.building_slots if s.id == "main_hall"), None)
        if main is None or not _cells_touch(moon, main.footprint):
            errors.append("moon_platform_not_adjacent_to_main_hall")
    if tree and tree & (buildings | gallery_cells | moon | landscape):
        errors.append(f"courtyard_tree_overlap: {sorted(tree & (buildings | gallery_cells | moon | landscape))[:8]}")

    # No perimeter wall cell floats over air.
    floating = [(x, z) for x, z in perimeter
                if compound.grid.is_empty((x, 0, z))]
    if floating:
        errors.append(f"perimeter_wall_floats: {floating[:8]}")

    if path & landscape:
        errors.append("path_overlaps_landscape")
    if not path:
        errors.append("missing_central_path")
    else:
        # courtyard-path-network invariants (replaces the old gate→hall check).
        endpoints = _collect_path_endpoints(compound)
        blocked = _path_blocked_cells(compound)
        dist = _multi_source_bfs(endpoints, blocked, lot_w, lot_d)
        for cell in endpoints:
            if cell not in dist:
                errors.append(f"endpoint_unreachable: {cell}")
        # Path must not be written on any building door-front cell (the door's
        # own step block owns that cell).
        door_fronts = _door_front_cells(compound)
        path_nodes = [n for n in compound.parcel_nodes
                      if n.type == "path"
                      and n.meta.get("algorithm") == "multi_source_bfs"]
        path_written = set().union(*(n.cells for n in path_nodes)) if path_nodes else path
        door_overlap = sorted(path_written & door_fronts)
        if door_overlap:
            errors.append(f"path_overlaps_building_door: {door_overlap[:8]}")

    # courtyard-ground-layer invariants.
    plinth_h = compound.meta.get("plinth_height", 0)
    ground_kinds = _derive_ground_kinds(compound)
    skip_ground = (buildings |
                   compound.node_cells("water_feature", "water_jar", "planting",
                                       "courtyard_tree"))
    holes: List[Cell2] = []
    kind_mismatches: List[str] = []
    open_primary = _style_ground_primary(compound, "GROUND_YARD_OPEN")
    eave_primary = _style_ground_primary(compound, "GROUND_YARD_UNDER_EAVE")
    for cell in _lot_interior_cells(compound):
        if cell in skip_ground:
            continue
        y = _natural_surface_y(compound, cell)
        cell_state = compound.grid.state_at((cell[0], y, cell[1]))
        if cell_state == AIR or not cell_state:
            holes.append(cell)
            continue
        kind = ground_kinds.get(cell, "open_sky")
        if kind == "interior":
            continue
        # ground_kind_mismatch: an under-eave cell must not carry the open-sky
        # block (grass) and vice-versa. The path overlay (GROUND_PATH) and
        # stairs are allowed to overwrite the ground tile, so only flag a
        # mismatch when the cell's block matches the *other* kind's primary.
        if open_primary and eave_primary:
            if kind == "under_eave" and cell_state == open_primary:
                kind_mismatches.append(f"{cell}:under_eave_has_open_block")
            elif kind == "open_sky" and cell_state == eave_primary:
                kind_mismatches.append(f"{cell}:open_sky_has_eave_block")
    if holes:
        errors.append(f"ground_layer_hole: {holes[:8]}")
    if kind_mismatches:
        errors.append(f"ground_kind_mismatch: {kind_mismatches[:8]}")

    # Plinth-edge stair invariant (one-jin compounds only — small-courtyard has
    # no plinth). Every path cell at y = plinth_h - 1 with a 4-neighbour path
    # cell at y = -1 must have a stair block bridging the boundary. Δy = 1 is a
    # free Minecraft auto-step, so we only enforce stairs for plinth_h ≥ 2.
    if plinth_h >= 2:
        path_nodes = [n for n in compound.parcel_nodes
                      if n.type == "path"
                      and n.meta.get("algorithm") == "multi_source_bfs"]
        path_written = set().union(*(n.cells for n in path_nodes)) if path_nodes else set()
        stair_cells = compound.node_cells("plinth_stairs")
        missing_stairs: List[Cell2] = []
        for cell in path_written:
            if _natural_surface_y(compound, cell) != plinth_h - 1:
                continue
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nbr = (cell[0] + dx, cell[1] + dz)
                if nbr not in path_written:
                    continue
                if _natural_surface_y(compound, nbr) != -1:
                    continue
                # The stair sits on the outer (-1) side of the boundary.
                if nbr not in stair_cells:
                    missing_stairs.append(nbr)
                break
        if missing_stairs:
            errors.append(f"plinth_edge_missing_stair: {sorted(set(missing_stairs))[:8]}")

    slot_ids = {s.id for s in compound.building_slots}
    required = {"west_side_wing", "east_side_wing", "main_hall"}
    if compound.variant.layout_type == "standard":
        required.add("front_row")
    missing = sorted(required - slot_ids)
    if missing:
        errors.append(f"missing_slots: {missing}")

    failed_quality = [
        s.id for s in compound.building_slots
        if s.quality is not None and not s.quality.get("passed")
    ]
    if failed_quality:
        errors.append(f"subbuilding_quality_failed: {failed_quality}")

    # 3D voxel-walkability checks (courtyard-voxel-walkability spec). These
    # replace the implicit assumption behind the 2D multi-source BFS — that a
    # 2D-reached cell is walkable in 3D — with an actual standable + auto-step
    # probe. Any voxel_* error here is a real "堵住" defect the 2D check missed.
    voxel_errors, voxel_stats = _voxel_walk_check(compound)
    errors.extend(voxel_errors)

    ground_cells = len(_lot_interior_cells(compound) - skip_ground)
    endpoint_count = len(_collect_path_endpoints(compound))
    stair_count = len(compound.node_cells("plinth_stairs"))
    stats = {
        "lot_size": list(compound.lot_size),
        "building_slots": len(compound.building_slots),
        "gallery_cells": len(gallery_cells),
        "moon_cells": len(moon),
        "tree_cells": len(tree),
        "path_cells": len(path),
        "ground_cells": ground_cells,
        "endpoint_count": endpoint_count,
        "stair_cells": stair_count,
        "silhouette_score": compound_silhouette_score(compound),
    }
    stats.update(voxel_stats)
    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": stats,
    }


# Form-rule facing per mansion building slot (building-orientation-variants):
# 正房/open_hall→south, 倒座/front_row→north, 西厢/west_side_wing→east,
# 东厢/east_side_wing→west, gate_house→inward(north), 楼阁/tower_house→north
# (faces its enclosing 后院). Facing is *form*, not variation, so it is fixed
# per role — a 倒座 facing south (door onto the street) is the defect this checks.
MANSION_FORM_FACING = {
    "gate_house": "north",
    "front_row": "north",
    "open_hall": "south",
    "west_side_wing": "east",
    "east_side_wing": "west",
    "tower_house_1": "north",
    "tower_house_2": "north",
}
# Inverse of archetypes.FACING_TO_WALL: door wall → facing direction.
WALL_TO_FACING = {"front": "south", "back": "north", "east": "east", "west": "west"}


def _check_surface_zone_materials(compound: CompoundGraph) -> List[str]:
    """surface_zone_material assertion (path-surface-zoning task 4.2).

    Classifies each non-building ground/path cell into one of the surface zones
    and checks the resolved block id against the zone's declared slot primary.
    A cell whose block does not match its zone's slot primary fails with
    ``surface_zone_material:<zone>:<cell>``. This is a spot-check on the zone
    model; it samples cells (not an exhaustive scan) to keep the validator fast,
    and it only fires when the style carries the relevant slot.
    """
    errors: List[str] = []
    try:
        style = load_style(compound.style_id)
    except FileNotFoundError:
        return errors
    if not style.has_slot("GROUND_YARD_OPEN"):
        return errors
    kinds = _derive_ground_kinds(compound)
    zone_slot = {
        "open_sky": "GROUND_YARD_OPEN",
        "heart": "GROUND_YARD_HEART",
        "gallery": "PATH_GALLERY",
        "alley": "PATH_ALLEY",
        "under_eave": "GROUND_YARD_UNDER_EAVE",
    }
    zone_primaries: Dict[str, str] = {}
    for zone, slot in zone_slot.items():
        if style.has_slot(slot):
            zone_primaries[zone] = style.primary(slot)
    # The path overlays (PATH_FORMAL / PATH_TOUR / PATH_WATERSIDE) take priority
    # over the ground zone at their cells — a formal-backbone cell on grass is
    # not a mismatch because the path overlay (PATH_FORMAL) owns that cell. The
    # GROUND_PATH slot's blocks are also overlays (a building's porch/colonnade/
    # path-patch decoration may stamp a GROUND_PATH block on a heart-ring cell),
    # so they are skipped too.
    path_overlay_slots = ("PATH_FORMAL", "PATH_TOUR", "PATH_WATERSIDE",
                          "GROUND_PATH")
    path_overlay_blocks: Set[str] = set()
    for slot in path_overlay_slots:
        if style.has_slot(slot):
            for entry in style.material_slots[slot]:
                path_overlay_blocks.add(entry.split("[", 1)[0])
    buildings = compound.building_cells()
    # Cells covered by a non-ground overlay must be skipped: the surface-zone
    # model places the base ground tile per zone, then water / planting /
    # rockery / pavilion / gallery / tour-path / waterside-bridge layers stamp
    # their own block on top. Those cells are NOT a zone mismatch — their block
    # is owned by the overlay, not the ground zone.
    overlay_nodes = ("water_feature", "water_jar", "planting", "courtyard_tree",
                     "garden_rockery", "garden_pond", "garden_pavilion",
                     "covered_gallery", "tour_path", "waterside_bridge",
                     "moon_platform", "platform")
    overlay_cells = compound.node_cells(*overlay_nodes)
    checked = 0
    for cell in _lot_interior_cells(compound):
        if checked >= 200:  # spot-check cap
            break
        if cell in buildings or cell in overlay_cells:
            continue
        kind = kinds.get(cell, "open_sky")
        expected = zone_primaries.get(kind)
        if expected is None:
            continue
        y = _natural_surface_y(compound, cell)
        state = compound.grid.state_at((cell[0], y, cell[1]))
        if not state or state == AIR:
            continue  # ground-layer-hole check owns this case
        block_id = state.split("[", 1)[0]
        # Allow the formal-path overlay (PATH_FORMAL) block to sit on any ground
        # zone — the formal backbone owns those cells, not the ground zone.
        if block_id in path_overlay_blocks:
            checked += 1
            continue
        # Skip cells whose block is neither the expected ground zone primary nor
        # any known ground/path material: these are building overhangs (a roof
        # stair/slab from ROOF_DARK dripping past a building footprint onto an
        # open-sky cell, or a column/balustrade post). They are not a ground-zone
        # defect — the cell is shadowed by a structure above, not mis-zoned.
        known_ground = set(zone_primaries.values()) | path_overlay_blocks
        if block_id not in known_ground:
            continue
        if block_id != expected:
            errors.append(f"surface_zone_material:{zone_slot[kind]}:{cell}")
            checked += 1
            continue
        checked += 1
    return errors


def _check_tour_segment_connectivity(compound: CompoundGraph) -> List[str]:
    """tour_segment_disconnected assertion (path-surface-zoning task 4.3).

    Verifies each tour-route waypoint-to-waypoint segment is a connected
    single-source tree: every cell on the segment is 4-neighbor-connected to the
    segment's source waypoint. A disconnected segment fails with
    ``tour_segment_disconnected:<from>-><to>``. A no-op for compounds without a
    tour route (small-courtyard / garden-less courtyard).
    """
    errors: List[str] = []
    tour_nodes = [n for n in compound.parcel_nodes if n.type == "tour_path"]
    if not tour_nodes:
        return errors  # no garden → no tour route
    waypoints = [tuple(w) for w in tour_nodes[0].meta.get("waypoints", [])]
    if len(waypoints) < 2:
        return errors
    cells = tour_nodes[0].cells
    cell_set = set(cells)
    for src, dst in zip(waypoints, waypoints[1:]):
        if src == dst:
            continue
        # Both endpoints must be tour cells (the segment connects them).
        if src not in cell_set or dst not in cell_set:
            errors.append(f"tour_segment_disconnected:{src}->{dst}")
            continue
        # 4-neighbour BFS from src within the tour cell set; dst must be reached.
        seen = {src}
        q: deque = deque([src])
        reached_dst = False
        while q:
            x, z = q.popleft()
            if (x, z) == dst:
                reached_dst = True
                break
            for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if (nx, nz) in cell_set and (nx, nz) not in seen:
                    seen.add((nx, nz))
                    q.append((nx, nz))
        if not reached_dst:
            errors.append(f"tour_segment_disconnected:{src}->{dst}")
    return errors


def _check_waterside_bridge_span(compound: CompoundGraph) -> List[str]:
    """waterside_bridge_incomplete assertion (path-surface-zoning task 4.4).

    Verifies the PATH_WATERSIDE slab bridge spans the pond from one shore to the
    other (or to the 亭/island rockery): the first bridge cell must be
    4-adjacent to a non-water shore cell, and the last must be adjacent to the
    opposite shore or the rockery island. A bridge that does not reach both ends
    fails with ``waterside_bridge_incomplete:<first|last>``. A no-op for compounds
    without a waterside_bridge (e.g. a garden-less courtyard).
    """
    errors: List[str] = []
    bridge_nodes = [n for n in compound.parcel_nodes if n.type == "waterside_bridge"]
    if not bridge_nodes:
        return errors
    bridge = bridge_nodes[0]
    if not bridge.cells:
        errors.append("waterside_bridge_incomplete:empty")
        return errors
    water = compound.node_cells("garden_pond", "water_feature")
    rockery = compound.node_cells("garden_rockery")
    cells_sorted = sorted(bridge.cells)

    def _adjacent_anchor(cell: Cell2) -> bool:
        x, z = cell
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            nbr = (nx, nz)
            if nbr in bridge.cells:
                continue
            # An anchor is a non-water cell (dry shore) or the rockery island.
            if nbr in rockery:
                return True
            if nbr not in water and nbr not in bridge.cells:
                return True
        return False

    if not _adjacent_anchor(cells_sorted[0]):
        errors.append("waterside_bridge_incomplete:first")
    if not _adjacent_anchor(cells_sorted[-1]):
        errors.append("waterside_bridge_incomplete:last")
    return errors


def _check_waterside_visual_composition(compound: CompoundGraph) -> List[str]:
    """Low-angle clutter guard for the mansion pond composition.

    Structural checks can pass while the pond reads as a single tangled mass of
    roof, posts, planks, lily pads, and rockery. This keeps the water, bridge,
    rockery island, and 水边廊 as separate readable elements.
    """
    errors: List[str] = []
    water = compound.node_cells("garden_pond", "water_feature")
    if not water:
        return errors
    rockery = compound.node_cells("garden_rockery")
    bridge = compound.node_cells("waterside_bridge")
    pavilions = [n for n in compound.parcel_nodes if n.type == "garden_pavilion"]
    if not pavilions:
        errors.append("garden_pavilion_detached_from_pond:missing")
    for pavilion in pavilions:
        cells = set(pavilion.cells)
        if cells & water:
            errors.append(f"garden_pavilion_detached_from_pond:overlaps_water:{sorted(cells & water)[:4]}")
            continue
        touches_water = any(
            (x + dx, z + dz) in water
            for x, z in cells
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
        )
        if not touches_water:
            errors.append(
                f"garden_pavilion_detached_from_pond:{pavilion.meta.get('center')}")
    galleries = [n for n in compound.parcel_nodes if n.id == "waterside_gallery"]
    if not galleries:
        errors.append("waterside_gallery_clutter:missing")
        return errors
    if len(galleries) != 1:
        errors.append(f"waterside_gallery_clutter:count:{len(galleries)}")

    for gallery in galleries:
        cells = set(gallery.cells)
        if cells & water:
            errors.append(f"waterside_gallery_clutter:overlaps_water:{sorted(cells & water)[:4]}")
        if cells & rockery:
            errors.append(f"waterside_gallery_clutter:overlaps_rockery:{sorted(cells & rockery)[:4]}")
        if cells & bridge:
            errors.append(f"waterside_gallery_clutter:overlaps_bridge:{sorted(cells & bridge)[:4]}")
        if not (6 <= len(cells) <= 14):
            errors.append(f"waterside_gallery_clutter:size:{len(cells)}")
        if cells:
            xs = [x for x, _ in cells]
            zs = [z for _, z in cells]
            width = max(xs) - min(xs) + 1
            depth = max(zs) - min(zs) + 1
            if min(width, depth) != 2 or width * depth != len(cells):
                errors.append(f"waterside_gallery_clutter:not_straight:{sorted(cells)[:8]}")
        water_edge = {tuple(c) for c in gallery.meta.get("water_edge_cells", [])}
        if not water_edge or not water_edge <= cells:
            errors.append("waterside_gallery_clutter:missing_water_edge")
        for gx, gz in sorted(water_edge):
            nbrs = {(gx + 1, gz), (gx - 1, gz), (gx, gz + 1), (gx, gz - 1)}
            if not (nbrs & water):
                errors.append(f"waterside_gallery_clutter:not_shore:{(gx, gz)}")

    clear_lanes = set(bridge)
    for gallery in galleries:
        water_edge = {tuple(c) for c in gallery.meta.get("water_edge_cells", [])}
        clear_lanes |= water_edge
        clear_lanes |= _chebyshev_ring(water_edge) & water
    if bridge:
        clear_lanes |= _chebyshev_ring(bridge) & water
    for pos, cell in compound.grid.iter_cells():
        if cell.state.split("[", 1)[0] != "minecraft:lily_pad":
            continue
        lily = (pos[0], pos[2])
        if lily in clear_lanes:
            errors.append(f"pond_lily_clutter:{lily}")
    return errors


def validate_mansion(compound: CompoundGraph) -> dict:
    """Validation for chinese_mansion 3-进 compounds.

    Runs the general grid-only structural checks verbatim (perimeter floats,
    ground-layer holes, voxel-walkability, silhouette) and adds the enclosure-
    model invariants (rebuild-mansion-enclosure-plan, D6): a gate_house
    straddling the south perimeter, form-rule facing per slot, every door-cell
    on the path, and the 进 sequence (仪门 borders 前院+主院; 二门 borders 主院+
    后院) verified by *derived-yard adjacency*, NOT z-band tuple comparison.
    """
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    buildings = compound.building_cells()
    axis = compound.axis_x

    # --- Perimeter wall ---
    perimeter_node = next(
        (n for n in compound.parcel_nodes if n.type == "perimeter_wall"), None)
    perimeter = perimeter_node.cells if perimeter_node else set()
    floating = [(x, z) for x, z in perimeter if compound.grid.is_empty((x, 0, z))]
    if floating:
        errors.append(f"perimeter_wall_floats: {floating[:8]}")

    gate_side = compound.meta.get("gate_side", "south")
    gate_z = 0 if gate_side == "south" else lot_d - 1
    gate_wall = {(x, gate_z) for x in range(lot_w)}
    openings = sorted(gate_wall - perimeter)
    if not openings or not any(x == axis for x, _ in openings):
        errors.append("gate_opening_missing_on_axis")

    # --- Screen wall off-axis ---
    screen_nodes = [n for n in compound.parcel_nodes if n.type == "screen_wall"]
    if not screen_nodes:
        errors.append("missing_screen_wall")
    elif any(x == axis for x, _ in screen_nodes[0].cells):
        errors.append("screen_wall_on_axis")

    # --- Inner gates: mansion must have exactly 2 (仪门 + 二门) ---
    inner_gates = [n for n in compound.parcel_nodes if n.type == "inner_gate"]
    if len(inner_gates) != 2:
        errors.append(f"mansion_inner_gate_count: {len(inner_gates)}")
    for gate in inner_gates:
        passage = gate.meta.get("passage", [])
        if len(passage) < 3:
            errors.append(f"inner_gate_passage_too_narrow: {gate.id} ({len(passage)} cells)")

    # --- open_hall required instead of main_hall ---
    slot_ids = {s.id for s in compound.building_slots}
    required_mansion = {"west_side_wing", "east_side_wing", "open_hall"}
    missing = sorted(required_mansion - slot_ids)
    if missing:
        errors.append(f"missing_slots: {missing}")

    # --- Garden parcels ---
    has_rockery = any(n.type in ("garden_rockery", "rockery") for n in compound.parcel_nodes)
    has_pond = any(n.type in ("garden_pond", "water_feature") for n in compound.parcel_nodes)
    if not has_rockery:
        errors.append("garden_missing_rockery")
    if not has_pond:
        errors.append("garden_missing_pond")

    # --- Ground layer holes ---
    skip_ground = (buildings | compound.node_cells(
        "water_feature", "water_jar", "planting", "courtyard_tree"))
    holes: List[Cell2] = []
    for cell in _lot_interior_cells(compound):
        if cell in skip_ground:
            continue
        y = _natural_surface_y(compound, cell)
        cell_state = compound.grid.state_at((cell[0], y, cell[1]))
        if cell_state == AIR or not cell_state:
            holes.append(cell)
    if holes:
        errors.append(f"ground_layer_hole: {holes[:8]}")

    # --- Voxel walkability ---
    path = compound.node_cells("path")
    voxel_stats: dict = {}
    if path:
        endpoints = _collect_path_endpoints(compound)
        blocked = _path_blocked_cells(compound)
        dist_2d = _multi_source_bfs(endpoints, blocked, lot_w, lot_d)
        for cell in endpoints:
            if cell not in dist_2d:
                errors.append(f"endpoint_unreachable: {cell}")
        start = _gate_entry_standable(compound)
        if start:
            visited_3d = _voxel_walk_bfs(compound, start, lot_w, lot_d)
            unreachable = []
            for s in compound.building_slots:
                if s.door_info and s.door_info.get("front"):
                    df = s.door_info["front"]
                    dfx, dfz = df[0], df[2]
                    standable = _standable_ys(compound, dfx, dfz)
                    if standable:
                        dfy = standable[0]
                        if (dfx, dfy, dfz) not in visited_3d:
                            errors.append(f"voxel_unreachable_door:{s.id}:({dfx},{dfz})")
                            unreachable.append((dfx, dfz))
            for cell in endpoints:
                if cell in unreachable:
                    continue
                sx, sz = cell
                sy_list = _standable_ys(compound, sx, sz)
                if sy_list and (sx, sy_list[0], sz) not in visited_3d:
                    errors.append(f"voxel_unreachable_endpoint:{cell}")
            voxel_stats["voxel_reachability"] = {
                "visited": len(visited_3d),
                "unreachable": len(unreachable),
                "cliff": 0,
            }

    # --- Enclosure-model invariants (D6) ------------------------------------
    # These replace the band-coupled shape checks. (No z-band tuple comparison
    # remains in this validator; band ordering stays in validate_compound for
    # the unchanged chinese_courtyard family.)
    slots_by_id = {s.id: s for s in compound.building_slots}

    # (a) gate_house present and straddling the south perimeter line (z=0).
    gate_house = slots_by_id.get("gate_house")
    if (gate_house is None or not gate_house.footprint
            or min(z for _, z in gate_house.footprint) != 0):
        errors.append("gate_house_missing")

    # (b) form-rule facing per slot (door wall faces the yard it serves) and a
    #     facing_per_slot report map (read from the slot's recorded door wall).
    facing_per_slot: Dict[str, str] = {}
    for slot in compound.building_slots:
        wall = slot.door_info.get("wall") if slot.door_info else None
        if wall is None:
            continue
        facing = WALL_TO_FACING.get(wall, wall)
        facing_per_slot[slot.id] = facing
        expected = MANSION_FORM_FACING.get(slot.id)
        if expected is not None and facing != expected:
            errors.append(f"enclosure_facing_violation:{slot.id}")

    # (c) every door-cell on the path (path-as-input guarantee). The backbone
    #     stops one cell short of each door, so a door-cell is "on path" if it
    #     is a path cell or 4-adjacent to one.
    door_total = 0
    door_on_path = 0
    for slot in compound.building_slots:
        if not (slot.door_info and slot.door_info.get("front")):
            continue
        df = slot.door_info["front"]
        cell = (df[0], df[2])
        door_total += 1
        near = [cell] + [(cell[0] + dx, cell[1] + dz)
                         for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if any(c in path for c in near):
            door_on_path += 1
        else:
            errors.append(f"door_off_path:{slot.id}")
    door_reachable_rate = (door_on_path / door_total) if door_total else 1.0

    # (d) 进-sequence adjacency via DERIVED yards (not z-band tuples): 仪门
    #     borders 前院+主院; 二门 borders 主院+后院.
    gate_nodes = {n.meta.get("kind"): n for n in inner_gates}
    gates_for_yards = [(n.meta.get("kind", "gate"), n.meta["band"][0])
                       for n in inner_gates if n.meta.get("band")]
    yards = _derive_mansion_yards(compound, [], gates_for_yards, lot_w, lot_d)

    def _gate_borders(node: ParcelNode, yard: Set[Cell2]) -> bool:
        return any((gx + dx, gz + dz) in yard
                   for gx, gz in node.cells
                   for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    front_yard = yards.get("front_yard", set())
    main_yard = yards.get("main_yard", set())
    back_yard = yards.get("back_yard", set())
    yimen = gate_nodes.get("yimen_gate")
    ermen = gate_nodes.get("ermen_gate")
    if yimen is None or not (_gate_borders(yimen, front_yard)
                             and _gate_borders(yimen, main_yard)):
        errors.append("jin_sequence_violation:yimen")
    if ermen is None or not (_gate_borders(ermen, main_yard)
                             and _gate_borders(ermen, back_yard)):
        errors.append("jin_sequence_violation:ermen")

    # --- Surface-zone invariants (path-surface-zoning tasks 4.2/4.3/4.4/7.3) ---
    surface_errors = _check_surface_zone_materials(compound)
    errors.extend(surface_errors)
    errors.extend(_check_tour_segment_connectivity(compound))
    errors.extend(_check_waterside_bridge_span(compound))
    errors.extend(_check_waterside_visual_composition(compound))

    # --- Layout invariants (Arc 6: 后院/花园 split + 绣楼 bounds) ---------------
    # back_yard_band and garden_band must NOT share a z-interval (the v0.16-v0.17
    # bug had them identical, so the 绣楼 sat inside the 花园).
    back_band = compound.meta.get("back_yard_band")
    garden_band = compound.meta.get("garden_band")
    if (back_band and garden_band
            and back_band[1] >= garden_band[0]):
        errors.append(f"back_yard_garden_overlap: back={back_band} garden={garden_band}")
    # No 绣楼 (tower_house) footprint cell may coincide with a 花园 feature cell.
    tower_cells = set()
    for s in compound.building_slots:
        if s.archetype == "tower_house":
            tower_cells |= set(s.footprint)
    garden_feature_cells = compound.node_cells(
        "garden_rockery", "garden_pond", "garden_pavilion", "waterside_bridge")
    tower_garden_overlap = tower_cells & garden_feature_cells
    if tower_garden_overlap:
        errors.append(
            f"tower_overlaps_garden: {sorted(tower_garden_overlap)[:8]}")

    path_cells = len(path)
    ground_cells = sum(1 for cell in _lot_interior_cells(compound)
                       if cell not in buildings and cell not in perimeter)
    stats = {
        "lot_size": list(compound.lot_size),
        "building_slots": len(compound.building_slots),
        "inner_gate_count": len(inner_gates),
        "path_cells": path_cells,
        "ground_cells": ground_cells,
        "silhouette_score": compound_silhouette_score(compound),
        "facing_per_slot": facing_per_slot,
        "door_reachable_rate": round(door_reachable_rate, 4),
    }
    stats.update(voxel_stats)
    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": stats,
    }


def _style_ground_primary(compound: CompoundGraph, slot: str) -> Optional[str]:
    """Look up a style slot's primary block without re-loading the style.

    The compound graph doesn't carry the Style object, so resolve lazily from
    the style JSON. Returns ``None`` if the slot is absent (older compounds).
    """
    try:
        style = load_style(compound.style_id)
    except FileNotFoundError:
        return None
    entries = style.material_slots.get(slot)
    if not entries:
        return None
    return entries[0]


def compound_silhouette_score(compound: CompoundGraph) -> int:
    """Stable whole-compound silhouette metric used by the shipped-library gate.

    It rewards vertical extent, the number of distinct building masses, and the
    main-hall bay span.  Unlike sub-building quality scores this measures the
    parcel skyline, so three-sided/standard and 3/5/7-bay templates separate.
    """
    (_, y0, _), (_, y1, _) = compound.grid.bounds()
    height = y1 - min(0, y0) + 1
    score = height * 2 + len(compound.building_slots) * 10 + compound.variant.main_bays * 2
    return max(0, min(100, int(score)))


def _compound_grid_sha256(compound: CompoundGraph) -> str:
    digest = hashlib.sha256()
    for pos, cell in sorted(compound.grid.iter_cells()):
        digest.update(repr((pos, cell.state, sorted(cell.tags), cell.slot)).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()


def _gate_opening_cells(opening: Sequence[int]) -> Set[Cell2]:
    x0, z0, x1, z1 = opening
    return _rect(x0, z0, x1, z1)


def _boundary_cells(lot_w: int, lot_d: int) -> Set[Cell2]:
    cells = {(x, 0) for x in range(lot_w)}
    cells |= {(x, lot_d - 1) for x in range(lot_w)}
    cells |= {(0, z) for z in range(lot_d)}
    cells |= {(lot_w - 1, z) for z in range(lot_d)}
    return cells


def _is_single_line(cells: Set[Cell2]) -> bool:
    if not cells:
        return False
    xs = sorted({x for x, _ in cells})
    zs = sorted({z for _, z in cells})
    if len(zs) == 1:
        return xs == list(range(xs[0], xs[-1] + 1))
    if len(xs) == 1:
        return zs == list(range(zs[0], zs[-1] + 1))
    return False


def validate_small_courtyard(compound: CompoundGraph) -> dict:
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    buildings = compound.building_cells()
    perimeter = compound.node_cells("perimeter_wall")
    tianjing = compound.node_cells("tianjing")
    landscape = compound.node_cells("water_feature", "planting")
    path = compound.node_cells("path")
    corridors = compound.node_cells("corridor")

    if compound.meta.get("layout_strategy") != "small_courtyard_unit":
        errors.append(f"bad_layout_strategy: {compound.meta.get('layout_strategy')}")
    if not 2 <= len(compound.building_slots) <= 4:
        errors.append(f"building_slot_count: {len(compound.building_slots)}")
    if not tianjing:
        errors.append("missing_tianjing")
    if buildings & tianjing:
        errors.append(f"building_tianjing_overlap: {sorted(buildings & tianjing)[:8]}")
    if buildings & landscape:
        errors.append(f"building_landscape_overlap: {sorted(buildings & landscape)[:8]}")
    if buildings & perimeter:
        errors.append(f"building_wall_overlap: {sorted(buildings & perimeter)[:8]}")

    outside = [(x, z) for x, z in buildings
               if not (0 < x < lot_w - 1 and 0 < z < lot_d - 1)]
    if outside:
        errors.append(f"building_outside_perimeter: {outside[:8]}")

    openings = _boundary_cells(lot_w, lot_d) - perimeter
    wall_nodes = [n for n in compound.parcel_nodes if n.type == "perimeter_wall"]
    if len(wall_nodes) != 1:
        errors.append(f"perimeter_wall_node_count: {len(wall_nodes)}")
    elif set(map(tuple, _gate_opening_cells(wall_nodes[0].meta["gate_opening"]))) != openings:
        errors.append(f"gate_opening_meta_mismatch: {sorted(openings)}")
    if not _is_single_line(openings):
        errors.append(f"multiple_gate_openings: {sorted(openings)}")

    one_courtyard_area = COURTYARD_SIZE["small"][0] * COURTYARD_SIZE["small"][1]
    if lot_w * lot_d >= one_courtyard_area:
        errors.append(
            f"small_courtyard_not_compact: {lot_w * lot_d} >= {one_courtyard_area}")
    if not path:
        errors.append("missing_central_path")
    # The legacy side-corridor requirement is satisfied by the multi-source BFS
    # path network reaching every wing; small-courtyard no longer emits a
    # separate ``corridor`` parcel node. Keep the corridor-overlap check for any
    # legacy caller that still emits one.
    if corridors and corridors & landscape:
        errors.append("corridor_overlaps_landscape")
    if path & landscape:
        errors.append("path_overlaps_landscape")

    # courtyard-ground-layer + courtyard-path-network invariants (small-courtyard
    # has no plinth, so the plinth-stair check is skipped per task 3.2).
    endpoints = _collect_path_endpoints(compound)
    blocked = _path_blocked_cells(compound)
    dist = _multi_source_bfs(endpoints, blocked, lot_w, lot_d)
    for cell in endpoints:
        if cell not in dist:
            errors.append(f"endpoint_unreachable: {cell}")
    skip_ground = (buildings |
                   compound.node_cells("water_feature", "water_jar", "planting",
                                       "courtyard_tree"))
    holes: List[Cell2] = []
    for cell in _lot_interior_cells(compound):
        if cell in skip_ground:
            continue
        y = _natural_surface_y(compound, cell)
        if compound.grid.state_at((cell[0], y, cell[1])) == AIR:
            holes.append(cell)
    if holes:
        errors.append(f"ground_layer_hole: {holes[:8]}")

    ground_cells = len(_lot_interior_cells(compound) - skip_ground)
    endpoint_count = len(endpoints)

    # 3D voxel-walkability checks (courtyard-voxel-walkability spec). Small-
    # courtyard has no plinth, so _natural_surface_y is -1 everywhere and the
    # voxel_step_cliff branch never fires — the door / endpoint / solid-blockage
    # checks are the ones that matter here.
    voxel_errors, voxel_stats = _voxel_walk_check(compound)
    errors.extend(voxel_errors)

    stats = {
        "lot_size": list(compound.lot_size),
        "building_slots": len(compound.building_slots),
        "tianjing_cells": len(tianjing),
        "water_cells": len(compound.node_cells("water_feature")),
        "planting_cells": len(compound.node_cells("planting")),
        "path_cells": len(path),
        "corridor_cells": len(corridors),
        "ground_cells": ground_cells,
        "endpoint_count": endpoint_count,
    }
    stats.update(voxel_stats)
    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": stats,
    }


def _gate_network_entries(opening: Sequence[int], gate_side: str,
                          circulation: Set[Cell2]) -> Set[Cell2]:
    gates = _gate_opening_cells(opening)
    entries: Set[Cell2] = set()
    for x, z in gates:
        if gate_side == "south":
            entries.add((x, z - 1))
        elif gate_side == "north":
            entries.add((x, z + 1))
    return entries & circulation


def _reachable_cells(start: Cell2, cells: Set[Cell2]) -> Set[Cell2]:
    q = deque([start])
    seen = {start}
    while q:
        x, z = q.popleft()
        for nxt in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if nxt in cells and nxt not in seen:
                seen.add(nxt)
                q.append(nxt)
    return seen


def validate_town_block(compound: CompoundGraph) -> dict:
    errors: List[str] = []
    if compound.meta.get("layout_strategy") != "courtyard_street_block":
        errors.append(f"bad_layout_strategy: {compound.meta.get('layout_strategy')}")
    courtyards = compound.meta.get("courtyards", [])
    if len(courtyards) < 2:
        errors.append(f"too_few_courtyards: {len(courtyards)}")

    buildings_seen: Set[Cell2] = set()
    for slot in compound.building_slots:
        overlap = buildings_seen & slot.footprint
        if overlap:
            errors.append(f"building_footprint_overlap: {slot.id}: {sorted(overlap)[:8]}")
            break
        buildings_seen.update(slot.footprint)

    wall = compound.node_cells("perimeter_wall")
    landscape = compound.node_cells("water_feature", "planting", "tianjing")
    circulation = compound.node_cells("street", "lane")
    streets = compound.node_cells("street")
    lanes = compound.node_cells("lane")
    if not streets:
        errors.append("missing_street_network")
    circulation_overlap = circulation & (compound.building_cells() | wall | landscape)
    if circulation_overlap:
        errors.append(f"circulation_overlap: {sorted(circulation_overlap)[:8]}")

    gate_entries: List[Set[Cell2]] = []
    for court in courtyards:
        opening = court["gate_opening"]
        gate_cells = _gate_opening_cells(opening)
        if gate_cells & wall:
            errors.append(f"gate_blocked_by_wall: {court['id']}")
        entries = _gate_network_entries(opening, court["gate_side"], circulation)
        if not entries:
            errors.append(f"gate_not_adjacent_to_street: {court['id']}")
        gate_entries.append(entries)

    if gate_entries and gate_entries[0]:
        reachable = _reachable_cells(next(iter(gate_entries[0])), circulation)
        for court, entries in zip(courtyards, gate_entries):
            if entries and not (entries & reachable):
                errors.append(f"gate_unreachable_from_street_network: {court['id']}")

    courts_by_row: Dict[int, List[dict]] = {}
    for court in courtyards:
        courts_by_row.setdefault(court["row"], []).append(court)
        origin_x, origin_z = court["origin"]
        lot_w, lot_d = court["lot_size"]
        expected_wall = _shift_cells(_boundary_cells(lot_w, lot_d), origin_x, origin_z)
        expected_wall -= _gate_opening_cells(court["gate_opening"])
        missing = expected_wall - wall
        if missing:
            errors.append(f"outer_wall_gap: {court['id']}: {sorted(missing)[:8]}")
    for row, row_courts in courts_by_row.items():
        ordered = sorted(row_courts, key=lambda c: c["col"])
        for left, right in zip(ordered, ordered[1:]):
            left_x, left_z = left["origin"]
            left_w, left_d = left["lot_size"]
            right_x, _ = right["origin"]
            shared_x = left_x + left_w - 1
            if right_x == shared_x:
                shared = {(shared_x, z) for z in range(left_z, left_z + left_d)}
                missing = shared - wall
                if missing:
                    errors.append(
                        f"party_wall_gap_row_{row}: {sorted(missing)[:8]}")
            elif right_x <= shared_x:
                errors.append(f"courtyard_x_overlap_row_{row}: {left['id']} {right['id']}")

    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": {
            "lot_size": list(compound.lot_size),
            "courtyards": len(courtyards),
            "building_slots": len(compound.building_slots),
            "street_cells": len(streets),
            "lane_cells": len(lanes),
            "wall_cells": len(wall),
        },
    }


def _cells_touch(a: Set[Cell2], b: Set[Cell2]) -> bool:
    if a & b:
        return True
    for x, z in a:
        if ((x + 1, z) in b or (x - 1, z) in b or
                (x, z + 1) in b or (x, z - 1) in b):
            return True
    return False


def validate_sect_compound(compound: CompoundGraph) -> dict:
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    slot_ids = {s.id for s in compound.building_slots}
    slots = {slot.id: slot for slot in compound.building_slots}
    required = {
        "sect_gate", "disciple_quarters", "alchemy_room",
        "scripture_pavilion", "sect_main_hall",
    }
    missing = sorted(required - slot_ids)
    if missing:
        errors.append(f"missing_slots: {missing}")
    if compound.meta.get("layout_strategy") != "sect_terraced_axial_compound":
        errors.append(f"bad_layout_strategy: {compound.meta.get('layout_strategy')}")
    outside = [
        (x, z) for x, z in compound.building_cells()
        if not (0 < x < lot_w - 1 and 0 < z < lot_d - 1)
    ]
    if outside:
        errors.append(f"building_outside_perimeter: {outside[:8]}")
    landscape = compound.node_cells("water_feature", "planting")
    if compound.building_cells() & landscape:
        errors.append(f"building_landscape_overlap: {sorted(compound.building_cells() & landscape)[:8]}")
    path = compound.node_cells("path")
    circulation = compound.node_cells("circulation")
    if not path or (axis, 1) not in path:
        errors.append("missing_axis_path_from_gate")
    if not circulation:
        errors.append("missing_terrace_circulation")
    if not missing:
        gate_z = min(z for _, z in slots["sect_gate"].footprint)
        scripture_z = min(z for _, z in slots["scripture_pavilion"].footprint)
        hall_z = min(z for _, z in slots["sect_main_hall"].footprint)
        if not gate_z < scripture_z < hall_z:
            errors.append(
                f"hierarchy_not_axial: gate={gate_z} scripture={scripture_z} hall={hall_z}")
        hall_goal = (axis, min(z for _, z in slots["sect_main_hall"].footprint) - 1)
        if hall_goal not in path:
            errors.append(f"axis_path_does_not_reach_summit: {hall_goal}")

    terrace_nodes = [n for n in compound.parcel_nodes if n.type == "terrace"]
    terrace_levels = compound.meta.get("terrace_levels", {})
    level_defs = compound.meta.get("terraces", [])
    levels = sorted({int(t.get("level")) for t in level_defs if "level" in t})
    if len(levels) < 3:
        errors.append(f"too_few_terrace_levels: {levels}")
    if levels and levels != list(range(levels[0], levels[-1] + 1)):
        errors.append(f"non_contiguous_terrace_levels: {levels}")
    if len(terrace_nodes) < len(levels):
        errors.append(f"missing_terrace_nodes: {len(terrace_nodes)} < {len(levels)}")
    for level in levels:
        level_slots = [slot_id for slot_id, slot_level in terrace_levels.items()
                       if int(slot_level) == level]
        if not level_slots:
            errors.append(f"terrace_level_has_no_slots: {level}")
        courtyard = [n for n in compound.parcel_nodes
                     if n.type == "courtyard" and n.meta.get("level") == level]
        if not courtyard:
            errors.append(f"terrace_level_missing_courtyard: {level}")

    stairways = [
        n for n in compound.parcel_nodes
        if n.type == "circulation" and n.meta.get("kind") == "monumental_stair"
    ]
    stair_pairs = {
        (int(n.meta.get("from_level")), int(n.meta.get("to_level")))
        for n in stairways
        if "from_level" in n.meta and "to_level" in n.meta
    }
    for lower, upper in zip(levels, levels[1:]):
        if (lower, upper) not in stair_pairs:
            errors.append(f"missing_interlevel_stair: {lower}->{upper}")

    siting = compound.meta.get("siting_context", {})
    for key in ("mountain_slope", "cliff_back", "water_front", "cloud_sea"):
        if key not in siting:
            errors.append(f"missing_siting_context: {key}")
    if not missing and siting.get("cliff_back"):
        hall_back = max(z for _, z in slots["sect_main_hall"].footprint)
        cliff_z = int(siting.get("cliff_back", {}).get("z", lot_d - 2))
        if hall_back < cliff_z - 5:
            errors.append(f"principal_hall_not_against_cliff: hall_back={hall_back} cliff={cliff_z}")

    importance = compound.meta.get("importance_tiers", {})
    if importance:
        for lower, upper in zip(levels, levels[1:]):
            lower_tiers = [int(importance[s]) for s, lvl in terrace_levels.items()
                           if int(lvl) == lower and s in importance]
            upper_tiers = [int(importance[s]) for s, lvl in terrace_levels.items()
                           if int(lvl) == upper and s in importance]
            if lower_tiers and upper_tiers and min(upper_tiers) < max(lower_tiers):
                errors.append(f"importance_not_graded_by_level: {lower}->{upper}")
        top = max(importance.values()) if importance else None
        for summit in ("sect_main_hall", "scripture_pavilion"):
            if importance.get(summit) != top:
                errors.append(f"summit_slot_not_top_tier: {summit}")
    else:
        errors.append("missing_importance_tiers")

    node_ids = {node.id for node in compound.parcel_nodes}
    link_nodes = [n for n in compound.parcel_nodes if n.type == "link"]
    link_kinds = {n.meta.get("kind") for n in link_nodes}
    for required_kind in ("covered_gallery", "flying_bridge"):
        if required_kind not in link_kinds:
            errors.append(f"missing_link_kind: {required_kind}")
    endpoint_cells: Dict[str, Set[Cell2]] = {slot.id: slot.footprint for slot in compound.building_slots}
    endpoint_cells.update({node.id: node.cells for node in compound.parcel_nodes})
    for node in link_nodes:
        endpoints = node.meta.get("endpoints", [])
        if len(endpoints) != 2:
            errors.append(f"link_bad_endpoint_count: {node.id}: {endpoints}")
            continue
        for endpoint in endpoints:
            if endpoint not in slot_ids and endpoint not in node_ids:
                errors.append(f"link_unknown_endpoint: {node.id}: {endpoint}")
                continue
            if not _cells_touch(node.cells, endpoint_cells.get(endpoint, set())):
                errors.append(f"link_endpoint_not_connected: {node.id}: {endpoint}")
        if not node.meta.get("circulation") or not node.meta.get("structural"):
            errors.append(f"link_not_structural_circulation: {node.id}")
        if node.meta.get("kind") == "flying_bridge":
            crosses_axis = any(x == axis for x, _ in node.cells)
            spans_gap = bool(node.meta.get("over"))
            if not crosses_axis and not spans_gap:
                errors.append(f"flying_bridge_spans_nothing: {node.id}")

    failed_quality = [
        s.id for s in compound.building_slots
        if s.quality is not None and not s.quality.get("passed")
    ]
    if failed_quality:
        errors.append(f"subbuilding_quality_failed: {failed_quality}")
    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": {
            "lot_size": list(compound.lot_size),
            "building_slots": len(compound.building_slots),
            "path_cells": len(path),
            "circulation_cells": len(circulation),
            "terrace_levels": terrace_levels,
            "terrace_level_count": len(levels),
            "link_count": len(link_nodes),
            "siting_context": siting,
        },
    }


def sample_compound_library(count: int = 6, base_seed: int = 20260614,
                            style: Optional[Style] = None) -> List[CompoundGraph]:
    style = style or load_style("chinese_courtyard")
    compounds: List[CompoundGraph] = []
    seen: Set[Tuple[str, str, str, str, str, str]] = set()
    attempt = 0
    while len(compounds) < count and attempt < count * 20:
        seed = base_seed + attempt * 101
        variant = select_variant(seed)
        attempt += 1
        if variant.key() in seen:
            continue
        compound = generate_compound(seed, style, variant)
        report = validate_compound(compound)
        if not report["passed"]:
            continue
        seen.add(variant.key())
        compounds.append(compound)
    if len(compounds) < count:
        raise ValueError(f"only generated {len(compounds)}/{count} valid compounds")
    return compounds


def sample_sect_compound_library(count: int = 2, base_seed: int = 20260616,
                                 style: Optional[Style] = None) -> List[CompoundGraph]:
    style = style or load_style("cultivation_sect")
    compounds: List[CompoundGraph] = []
    attempt = 0
    while len(compounds) < count and attempt < count * 12:
        seed = base_seed + attempt * 101
        attempt += 1
        compound = generate_sect_compound(seed, style)
        report = validate_sect_compound(compound)
        if not report["passed"]:
            continue
        compounds.append(compound)
    if len(compounds) < count:
        raise ValueError(f"only generated {len(compounds)}/{count} valid sect compounds")
    return compounds


def sample_town_block_library(count: int = 6, base_seed: int = 20260617,
                              style: Optional[Style] = None,
                              roster: Sequence[str] = DEFAULT_TOWN_ROSTER) -> List[CompoundGraph]:
    style = style or load_style("cultivation_town")
    compounds: List[CompoundGraph] = []
    seen: Set[Tuple[int, int, int, bool, bool, str]] = set()
    attempt = 0
    while len(compounds) < count and attempt < count * 30:
        seed = base_seed + attempt * 101
        variant = select_town_block_variant(seed)
        attempt += 1
        if variant.key() in seen:
            continue
        try:
            compound = generate_town_block(seed, style, roster, variant)
        except ValueError:
            continue
        report = validate_town_block(compound)
        if not report["passed"]:
            continue
        seen.add(variant.key())
        compounds.append(compound)
    if len(compounds) < count:
        raise ValueError(f"only generated {len(compounds)}/{count} valid town blocks")
    return compounds


def validate_town_block_library(compounds: List[CompoundGraph],
                                min_distinct: int = 6) -> dict:
    results = [validate_town_block(c) for c in compounds]
    distinct = {c.variant.key() for c in compounds}
    errors = []
    if len(distinct) < min_distinct:
        errors.append(f"too_few_distinct_town_variants: {len(distinct)} < {min_distinct}")
    failed = [r for r in results if not r["passed"]]
    if failed:
        errors.append(f"failed_town_blocks: {[r['seed'] for r in failed]}")
    return {
        "passed": not errors,
        "errors": errors,
        "distinct_variants": len(distinct),
        "results": results,
    }


def validate_compound_library(compounds: List[CompoundGraph],
                              min_distinct: int = 6,
                              structure_dir: Optional[str] = None,
                              structure_names: Optional[Sequence[str]] = None,
                              min_silhouette_spread: int = 15) -> dict:
    if compounds and compounds[0].meta.get("layout_strategy") == "courtyard_street_block":
        return validate_town_block_library(compounds, min_distinct)
    results = [validate_compound(c) for c in compounds]
    distinct = {c.variant.key() for c in compounds}
    errors = []
    if len(distinct) < min_distinct:
        errors.append(f"too_few_distinct_variants: {len(distinct)} < {min_distinct}")
    silhouettes = [compound_silhouette_score(c) for c in compounds]
    silhouette_spread = max(silhouettes) - min(silhouettes) if silhouettes else 0
    if silhouette_spread < min_silhouette_spread:
        errors.append(
            f"compound_silhouette_spread_too_low: {silhouette_spread} "
            f"< {min_silhouette_spread} ({silhouettes})")
    hashes = [_compound_grid_sha256(c) for c in compounds]
    if structure_dir is not None and structure_names is not None:
        exported = []
        for name in structure_names:
            path = os.path.join(structure_dir, f"{name}.nbt")
            try:
                with open(path, "rb") as handle:
                    exported.append(hashlib.sha256(handle.read()).hexdigest())
            except OSError:
                exported.append("")
        hashes = exported
    duplicate_hashes = sorted({h for h in hashes if h and hashes.count(h) > 1})
    if duplicate_hashes:
        errors.append(f"byte_identical_compounds: {len(duplicate_hashes)} duplicate hashes")
    failed = [r for r in results if not r["passed"]]
    if failed:
        errors.append(f"failed_compounds: {[r['seed'] for r in failed]}")
    # courtyard-ground-layer / courtyard-path-network acceptance (task 3.3):
    # every shipped compound reports non-empty ground + endpoint stats and the
    # new error codes (endpoint_unreachable / ground_layer_hole /
    # ground_kind_mismatch / plinth_edge_missing_stair /
    # path_overlaps_building_door) do not fire.
    new_error_prefixes = (
        "endpoint_unreachable", "ground_layer_hole", "ground_kind_mismatch",
        "plinth_edge_missing_stair", "path_overlaps_building_door")
    for r in results:
        stats = r.get("stats", {})
        if not stats.get("ground_cells"):
            errors.append(f"empty_ground_cells: {r['seed']}")
        if not stats.get("endpoint_count"):
            errors.append(f"empty_endpoint_count: {r['seed']}")
        for err in r.get("errors", []):
            if any(err.startswith(p) for p in new_error_prefixes):
                errors.append(f"compound_{r['seed']}: {err}")
                break
    return {
        "passed": not errors,
        "errors": errors,
        "distinct_variants": len(distinct),
        "silhouette_scores": silhouettes,
        "silhouette_spread": silhouette_spread,
        "min_silhouette_spread": min_silhouette_spread,
        "nbt_sha256": hashes,
        "results": results,
    }
