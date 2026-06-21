"""Chinese courtyard compound parcel layer.

The compound graph sits above individual MassingGraphs: it generates each
sub-building through the existing pass pipeline, then translates the resulting
voxel grids into a walled parcel with structural landscape and circulation.
"""

from __future__ import annotations

import hashlib
import os
import random
from collections import deque
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

from .grid import AIR, BlockGrid, PRIORITY
from .massing import MassingGraph
from .passes import BuildContext, PIPELINE
from .quality import quality_check
from .style import Style, load_style

Cell2 = Tuple[int, int]
Pos = Tuple[int, int, int]


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


@dataclass
class BuildingSlot:
    id: str
    archetype: str
    origin: Pos
    graph: MassingGraph
    footprint: Set[Cell2]
    quality: Optional[dict] = None

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "archetype": self.archetype,
            "origin": list(self.origin),
            "footprint": [list(c) for c in sorted(self.footprint)],
            "massing_graph": self.graph.to_dict(),
            "quality": self.quality,
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
        "sect_gate", "sect_main_hall", "scripture_pavilion",
        "alchemy_room", "disciple_quarters",
    ):
        quality = quality_check(ctx, f"{compound.style_id}/{slot_id}")
    slot = BuildingSlot(slot_id, ctx.archetype, main_origin, shifted_graph,
                        footprint, quality)
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
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_side = compound.meta.get("gate_side", "south")
    blocked = (compound.building_cells() |
               compound.node_cells("water_feature", "planting", "perimeter_wall"))
    if gate_side == "north":
        gate_entry = (axis, lot_d - 2)
        main_approach_z = max(z for _, z in main_slot.footprint) + 1
    else:
        gate_entry = (axis, 1)
        main_approach_z = min(z for _, z in main_slot.footprint) - 1
    central = set(_bfs(gate_entry, (axis, main_approach_z), lot_w, lot_d, blocked))
    _put_cells(compound, "central_path", "path", central,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
               slot="GROUND_PATH")

    corridor_cells: Set[Cell2] = set()
    west_start = (max(x for x, _ in west_slot.footprint) + 1,
                  (min(z for _, z in west_slot.footprint) + max(z for _, z in west_slot.footprint)) // 2)
    east_start = (min(x for x, _ in east_slot.footprint) - 1,
                  (min(z for _, z in east_slot.footprint) + max(z for _, z in east_slot.footprint)) // 2)
    for start, goal in ((west_start, (axis - 2, main_approach_z)),
                        (east_start, (axis + 2, main_approach_z))):
        corridor_cells.update(_bfs(start, goal, lot_w, lot_d, blocked | central))
    _put_cells(compound, "side_corridors", "corridor", corridor_cells,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
               slot="GROUND_PATH")


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


def _compute_yard_bands(layout_type: str, lot_d: int) -> dict:
    """Depth bands in the canonical south frame (gate at z=0, inward +z).

    Generalizes cleanly to a future `jin_count` axis: each 进 is one more
    (yard band, inner-gate band) pair appended before the main yard.
    """
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


def _add_chinese_perimeter(compound: CompoundGraph, style: Style) -> None:
    """Walled perimeter with a cap ridge, 墙垛 corner/interval piers, and
    optional 漏窗 cutouts. Built from y=0 up so it never floats over a raised
    main-yard platform."""
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_side = compound.meta.get("gate_side", "south")
    gate_half = GATE_HALF[compound.variant.gate_type]
    gate_z = 0 if gate_side == "south" else lot_d - 1
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


def _free_screen_axis(axis: int, z: int) -> Set[Cell2]:
    return {(x, z) for x in range(axis - 2, axis + 3)}


def _layout_outer_yard(compound: CompoundGraph, style: Style, bands: dict,
                       contexts: Dict[str, BuildContext]) -> None:
    """影壁 (screen wall) on the axis inside the gate + 倒座 (front_row) along
    the south wall (omitted for 三合院)."""
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    oy0, oy1 = bands["outer_yard_band"]

    # 影壁: free-standing screen wall facing the gate, on the central axis,
    # blocking the sightline to the 垂花门 / main hall.
    screen_z = oy0 + 1
    screen_cells = _free_screen_axis(axis, screen_z)
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
            "height": 6, "on_axis": True, "facing_gate": True}))

    # 倒座 (front_row): a set-back outer-yard range facing the gate. Only the
    # `standard` plan carries it — 三合院 omits it (wings extend forward to close
    # the U), and the 目字 (mu) plan's narrow outer band has no room for it.
    if compound.variant.layout_type == "standard":
        front_ctx = contexts["front_row"]
        front = front_ctx.graph.get("main")
        _translate_context(compound, "front_row", front_ctx,
                           (axis - front.size[0] // 2, 0, oy0 + 4))


def _layout_inner_gate(compound: CompoundGraph, style: Style,
                       bands: dict) -> None:
    """垂花门 (inner gate) on the axis between the two yards, with 垂莲柱
    (hanging-lotus column) corner posts and a central passage."""
    axis = compound.axis_x
    ig0, ig1 = bands["inner_gate_band"]
    base = style.primary("PLATFORM_STONE")
    column = style.primary("COLUMN")
    wall_main = style.primary("WALL_MAIN")
    roof = style.slot_entry("ROOF_DARK", "_slab")

    cells: Set[Cell2] = set()
    passage = {(axis, z) for z in range(ig0, ig1 + 1)}
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
        ParcelNode("inner_gate", "inner_gate", cells, {
            "kind": "chuihua_gate",
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


def _route_central_path(compound: CompoundGraph, style: Style,
                        bands: dict) -> None:
    """中轴路: street gate → around the 影壁 → through the 垂花门 passage →
    up to the main-hall 月台. Galleries and the moon platform are walkable, so
    they are not obstacles."""
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    plinth_h = compound.meta.get("plinth_height", 0)
    hall_front = compound.meta["hall_front_z"]

    blocked = (compound.building_cells() |
               compound.node_cells("perimeter_wall", "screen_wall",
                                   "water_feature", "planting", "water_jar",
                                   "courtyard_tree"))
    # The 垂花门 solid flanks block, but its central passage stays open.
    inner_gate = next((n for n in compound.parcel_nodes
                       if n.type == "inner_gate"), None)
    if inner_gate:
        passage = {tuple(c) for c in inner_gate.meta.get("passage", [])}
        blocked |= (inner_gate.cells - passage)

    start = (axis, 1)
    goal = (axis, hall_front - 1)
    path = set(_bfs(start, goal, lot_w, lot_d, blocked))
    _put_cells(compound, "central_path", "path", path,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"],
               y=plinth_h - 1 if plinth_h else -1, slot="GROUND_PATH")


def generate_compound(seed: int, style: Optional[Style] = None,
                      variant: Optional[CompoundVariant] = None) -> CompoundGraph:
    style = style or load_style("chinese_courtyard")
    variant = variant or select_variant(seed)
    gate_side = _resolve_gate_side(variant.main_orientation)
    lot_w, lot_d = COURTYARD_SIZE[variant.courtyard_size]
    axis = lot_w // 2
    bands = _compute_yard_bands(variant.layout_type, lot_d)
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
    _route_central_path(compound, style, bands)
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
            _route_circulation(compound, style, focal_slot, west_slot, east_slot)
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

    # Two-yard split invariants (the 一进 definition).
    outer_band = compound.meta.get("outer_yard_band")
    if not screen:
        errors.append("missing_screen_wall")
    elif not any(x == axis for x, _ in screen):
        errors.append("screen_wall_off_axis")
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
        main = next(s for s in compound.building_slots if s.id == "main_hall")
        if gate_side == "south":
            entry, goal = (axis, 1), (axis, min(z for _, z in main.footprint) - 1)
        else:
            entry, goal = (axis, lot_d - 2), (axis, max(z for _, z in main.footprint) + 1)
        if entry not in path or goal not in path:
            errors.append("gate_to_hall_path_not_connected")

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

    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": {
            "lot_size": list(compound.lot_size),
            "building_slots": len(compound.building_slots),
            "gallery_cells": len(gallery_cells),
            "moon_cells": len(moon),
            "tree_cells": len(tree),
            "path_cells": len(path),
            "silhouette_score": compound_silhouette_score(compound),
        },
    }


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
    if not corridors:
        errors.append("missing_side_corridors")
    if path & landscape:
        errors.append("path_overlaps_landscape")
    if corridors & landscape:
        errors.append("corridor_overlaps_landscape")

    return {
        "seed": compound.seed,
        "variant": compound.variant.to_dict(),
        "passed": not errors,
        "errors": errors,
        "stats": {
            "lot_size": list(compound.lot_size),
            "building_slots": len(compound.building_slots),
            "tianjing_cells": len(tianjing),
            "water_cells": len(compound.node_cells("water_feature")),
            "planting_cells": len(compound.node_cells("planting")),
            "path_cells": len(path),
            "corridor_cells": len(corridors),
        },
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
