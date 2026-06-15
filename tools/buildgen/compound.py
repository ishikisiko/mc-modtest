"""Chinese courtyard compound parcel layer.

The compound graph sits above individual MassingGraphs: it generates each
sub-building through the existing pass pipeline, then translates the resulting
voxel grids into a walled parcel with structural landscape and circulation.
"""

from __future__ import annotations

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
ROOF_GRADES = ("硬山", "悬山", "歇山")
GATE_STYLES = ("plain_gate", "lantern_gate", "double_eave_gate")
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
    gate_style: str
    symmetry: str = "mild_asymmetry"

    def key(self) -> Tuple[str, str, str, str, str, str]:
        return (
            self.courtyard_size,
            self.water_form,
            self.planting_layout,
            self.roof_grade,
            self.gate_style,
            self.symmetry,
        )

    def to_dict(self) -> dict:
        return {
            "courtyard_size": self.courtyard_size,
            "water_form": self.water_form,
            "planting_layout": self.planting_layout,
            "roof_grade": self.roof_grade,
            "gate_style": self.gate_style,
            "symmetry": self.symmetry,
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


def select_variant(seed: int) -> CompoundVariant:
    rng = random.Random(seed)
    return CompoundVariant(
        courtyard_size=rng.choice(COURTYARD_VARIANT_SIZES),
        water_form=rng.choice(WATER_FORMS),
        planting_layout=rng.choice(PLANTING_LAYOUTS),
        roof_grade=rng.choice(ROOF_GRADES),
        gate_style=rng.choice(GATE_STYLES),
        symmetry=rng.choice(("mild_asymmetry", "mild_asymmetry", "strict_mirror")),
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
    ctx.graph.meta["roof_grade"] = roof_grade
    for vol in ctx.graph.volumes():
        roof = vol.meta.get("roof")
        if roof:
            roof["grade"] = roof_grade
            if roof_grade == "悬山":
                roof["overhang"] = max(roof.get("overhang", 1), 2)


def generate_subbuilding(style: Style, archetype: str, seed: int,
                         roof_grade: Optional[str],
                         group_id: Optional[str] = None,
                         importance_tier: Optional[int] = None) -> BuildContext:
    ctx = BuildContext(style=style, archetype=archetype, scale_tier=archetype,
                       seed=seed, rng=random.Random(seed), group_id=group_id,
                       importance_tier=importance_tier)
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
               y: int = 0, height: int = 1, slot: Optional[str] = None) -> None:
    for x, z in cells:
        for yy in range(y, y + height):
            compound.grid.set((x, yy, z), state, tags, PRIORITY["DETAIL"], slot)
    compound.parcel_nodes.append(ParcelNode(node_id, node_type, cells))


def _clear_cells(compound: CompoundGraph, cells: Set[Cell2],
                 y0: int = 0, y1: int = 2) -> None:
    for x, z in cells:
        for y in range(y0, y1 + 1):
            compound.grid.set((x, y, z), AIR, ["AIR_CARVE"],
                              PRIORITY["AIR_CARVE"], force=True)


def _add_perimeter(compound: CompoundGraph, style: Style) -> None:
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    gate_half = {"plain_gate": 1, "lantern_gate": 2,
                 "double_eave_gate": 2}[compound.variant.gate_style]
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


def generate_compound(seed: int, style: Optional[Style] = None,
                      variant: Optional[CompoundVariant] = None) -> CompoundGraph:
    style = style or load_style("chinese_courtyard")
    variant = variant or select_variant(seed)
    lot_w, lot_d = COURTYARD_SIZE[variant.courtyard_size]
    axis = lot_w // 2
    compound = CompoundGraph(style.style_id, seed, variant, (lot_w, lot_d), axis)

    slot_seed = seed * 1009
    main_ctx = generate_subbuilding(style, "main_hall", slot_seed + 1,
                                    variant.roof_grade, "chinese_courtyard")
    gate_ctx = generate_subbuilding(style, "gate_house", slot_seed + 2,
                                    variant.roof_grade, "chinese_courtyard")
    front_ctx = generate_subbuilding(style, "front_row", slot_seed + 3,
                                     variant.roof_grade, "chinese_courtyard")
    side_seed = slot_seed + 4
    west_ctx = generate_subbuilding(style, "side_wing", side_seed,
                                    variant.roof_grade, "chinese_courtyard")
    east_ctx = generate_subbuilding(
        style, "side_wing",
        side_seed if variant.symmetry == "strict_mirror" else slot_seed + 5,
        variant.roof_grade, "chinese_courtyard")

    main = main_ctx.graph.get("main")
    gate = gate_ctx.graph.get("main")
    front = front_ctx.graph.get("main")
    west = west_ctx.graph.get("main")
    east = east_ctx.graph.get("main")

    gate_slot = _translate_context(
        compound, "gate_house", gate_ctx,
        (axis - gate.size[0] // 2, 0, 2))
    front_slot = _translate_context(
        compound, "front_row", front_ctx,
        (axis - front.size[0] // 2, 0, 8))
    main_z = lot_d - 3 - main.size[2]
    main_slot = _translate_context(
        compound, "main_hall", main_ctx,
        (axis - main.size[0] // 2, 0, main_z))

    inner_z0 = max(z for _, z in front_slot.footprint) + 3
    inner_z1 = min(z for _, z in main_slot.footprint) - 3
    wing_z = inner_z0 + max(0, (inner_z1 - inner_z0 + 1 - west.size[2]) // 2)
    if variant.symmetry == "mild_asymmetry":
        wing_z += random.Random(seed + 77).choice([-1, 0, 1])
    west_slot = _translate_context(compound, "west_side_wing", west_ctx,
                                   (3, 0, wing_z))
    east_slot = _translate_context(compound, "east_side_wing", east_ctx,
                                   (lot_w - 3 - east.size[0], 0, wing_z))

    _add_perimeter(compound, style)
    courtyard = (
        max(x for x, _ in west_slot.footprint) + 2,
        inner_z0,
        min(x for x, _ in east_slot.footprint) - 2,
        inner_z1,
    )
    _place_landscape(compound, style, courtyard)
    _route_circulation(compound, style, main_slot, west_slot, east_slot)
    compound.meta["courtyard"] = list(courtyard)
    compound.meta["gate_house_slot"] = gate_slot.id
    return compound


def _small_courtyard_variant(seed: int, size: str) -> CompoundVariant:
    rng = random.Random(seed)
    return CompoundVariant(
        courtyard_size=size,
        water_form=rng.choice(WATER_FORMS),
        planting_layout=rng.choice(PLANTING_LAYOUTS),
        roof_grade="town_roof_mix",
        gate_style=rng.choice(("plain_gate", "plain_gate", "lantern_gate")),
        symmetry="mild_asymmetry",
    )


def _town_roster(roster: Sequence[str]) -> Tuple[str, ...]:
    values = tuple(roster) or DEFAULT_TOWN_ROSTER
    unknown = [a for a in values if a not in DEFAULT_TOWN_ROSTER]
    if unknown:
        raise ValueError(f"small courtyard roster contains unsupported archetypes: {unknown}")
    return values


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


def generate_sect_compound(seed: int, style: Optional[Style] = None) -> CompoundGraph:
    style = style or load_style("cultivation_sect")
    variant = CompoundVariant(
        courtyard_size="sect_terraced",
        water_form="spirit_court",
        planting_layout="mountain_edges",
        roof_grade="tiered_eave_roof",
        gate_style="moon_gate_axis",
        symmetry="axial",
    )
    lot_w, lot_d = (63, 63)
    axis = lot_w // 2
    compound = CompoundGraph(style.style_id, seed, variant, (lot_w, lot_d), axis,
                             meta={"layout_strategy": "sect_terraced_axial_compound"})

    slot_seed = seed * 1009
    gate_ctx = generate_subbuilding(style, "sect_gate", slot_seed + 1, None,
                                    "cultivation_sect")
    quarters_ctx = generate_subbuilding(style, "disciple_quarters", slot_seed + 2, None,
                                        "cultivation_sect")
    alchemy_ctx = generate_subbuilding(style, "alchemy_room", slot_seed + 3, None,
                                       "cultivation_sect")
    scripture_ctx = generate_subbuilding(style, "scripture_pavilion", slot_seed + 4, None,
                                         "cultivation_sect")
    main_ctx = generate_subbuilding(style, "sect_main_hall", slot_seed + 5, None,
                                    "cultivation_sect")

    gate = gate_ctx.graph.get("main")
    quarters = quarters_ctx.graph.get("main")
    alchemy = alchemy_ctx.graph.get("main")
    scripture = scripture_ctx.graph.get("main")
    main = main_ctx.graph.get("main")

    lower_platform = _rect(2, 1, lot_w - 3, 29)
    upper_platform = _rect(8, 30, lot_w - 9, lot_d - 3)
    platform_state = style.primary("BASE_STONE")
    _put_cells(compound, "lower_terrace", "terrace", lower_platform,
               platform_state, ["STRUCTURE", "GROUND"], y=-1, slot="BASE_STONE")
    _put_cells(compound, "upper_terrace", "terrace", upper_platform,
               platform_state, ["STRUCTURE", "GROUND"], y=0, slot="BASE_STONE")

    gate_slot = _translate_context(
        compound, "sect_gate", gate_ctx,
        (axis - gate.size[0] // 2, 0, 3))
    quarters_slot = _translate_context(
        compound, "disciple_quarters", quarters_ctx,
        (6, 0, 20))
    alchemy_slot = _translate_context(
        compound, "alchemy_room", alchemy_ctx,
        (lot_w - 6 - alchemy.size[0], 0, 21))
    scripture_slot = _translate_context(
        compound, "scripture_pavilion", scripture_ctx,
        (axis - scripture.size[0] // 2, 1, 34))
    main_slot = _translate_context(
        compound, "sect_main_hall", main_ctx,
        (axis - main.size[0] // 2, 2, lot_d - main.size[2] - 4))

    path_cells = {(axis, z) for z in range(1, min(z for _, z in main_slot.footprint))}
    path_cells |= {(axis - 1, z) for z in range(6, min(z for _, z in main_slot.footprint))}
    path_cells |= {(axis + 1, z) for z in range(6, min(z for _, z in main_slot.footprint))}
    _put_cells(compound, "central_axis_path", "path", path_cells,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
               slot="GROUND_PATH")
    stair_cells = {(axis + dx, z) for dx in (-1, 0, 1) for z in (29, 30, 31)}
    _put_cells(compound, "terrace_steps", "circulation", stair_cells,
               style.slot_entry("ROOF_DARK", "_slab"), ["DETAIL", "GROUND"],
               y=0, slot="ROOF_DARK")

    compound.meta["hierarchy"] = [
        gate_slot.id,
        quarters_slot.id,
        alchemy_slot.id,
        scripture_slot.id,
        main_slot.id,
    ]
    compound.meta["terrace_levels"] = {
        gate_slot.id: 0,
        quarters_slot.id: 0,
        alchemy_slot.id: 0,
        scripture_slot.id: 1,
        main_slot.id: 2,
    }
    return compound


def validate_compound(compound: CompoundGraph) -> dict:
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    buildings = compound.building_cells()
    landscape = compound.node_cells("water_feature", "planting")
    path = compound.node_cells("path")
    corridors = compound.node_cells("corridor")
    perimeter = compound.node_cells("perimeter_wall")

    outside = [(x, z) for x, z in buildings
               if not (0 < x < lot_w - 1 and 0 < z < lot_d - 1)]
    if outside:
        errors.append(f"building_outside_perimeter: {outside[:8]}")
    overlap = buildings & landscape
    if overlap:
        errors.append(f"building_landscape_overlap: {sorted(overlap)[:8]}")

    south_wall = {(x, 0) for x in range(lot_w)}
    openings = sorted(south_wall - perimeter)
    if not openings or not any(x == axis for x, _ in openings):
        errors.append("gate_opening_missing_on_axis")
    if openings != [(x, 0) for x in range(openings[0][0], openings[-1][0] + 1)]:
        errors.append(f"multiple_gate_openings: {openings}")

    if path & landscape:
        errors.append("path_overlaps_landscape")
    if corridors & landscape:
        errors.append("corridor_overlaps_landscape")
    if not path:
        errors.append("missing_central_path")
    else:
        main = next(s for s in compound.building_slots if s.id == "main_hall")
        goal = (axis, min(z for _, z in main.footprint) - 1)
        if (axis, 1) not in path or goal not in path:
            errors.append("gate_to_hall_path_not_connected")

    slot_ids = {s.id for s in compound.building_slots}
    required = {"gate_house", "front_row", "west_side_wing",
                "east_side_wing", "main_hall"}
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
            "water_cells": len(compound.node_cells("water_feature")),
            "planting_cells": len(compound.node_cells("planting")),
            "path_cells": len(path),
            "corridor_cells": len(corridors),
        },
    }


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


def validate_sect_compound(compound: CompoundGraph) -> dict:
    errors: List[str] = []
    lot_w, lot_d = compound.lot_size
    axis = compound.axis_x
    slot_ids = {s.id for s in compound.building_slots}
    required = {
        "sect_gate", "disciple_quarters", "alchemy_room",
        "scripture_pavilion", "sect_main_hall",
    }
    missing = sorted(required - slot_ids)
    if missing:
        errors.append(f"missing_slots: {missing}")
    outside = [
        (x, z) for x, z in compound.building_cells()
        if not (0 < x < lot_w - 1 and 0 < z < lot_d - 1)
    ]
    if outside:
        errors.append(f"building_outside_perimeter: {outside[:8]}")
    path = compound.node_cells("path")
    circulation = compound.node_cells("circulation")
    if not path or (axis, 1) not in path:
        errors.append("missing_axis_path_from_gate")
    if not circulation:
        errors.append("missing_terrace_circulation")
    if not missing:
        slots = {slot.id: slot for slot in compound.building_slots}
        gate_z = min(z for _, z in slots["sect_gate"].footprint)
        scripture_z = min(z for _, z in slots["scripture_pavilion"].footprint)
        hall_z = min(z for _, z in slots["sect_main_hall"].footprint)
        if not gate_z < scripture_z < hall_z:
            errors.append(
                f"hierarchy_not_axial: gate={gate_z} scripture={scripture_z} hall={hall_z}")
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
            "terrace_levels": compound.meta.get("terrace_levels", {}),
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
                              min_distinct: int = 6) -> dict:
    if compounds and compounds[0].meta.get("layout_strategy") == "courtyard_street_block":
        return validate_town_block_library(compounds, min_distinct)
    results = [validate_compound(c) for c in compounds]
    distinct = {c.variant.key() for c in compounds}
    errors = []
    if len(distinct) < min_distinct:
        errors.append(f"too_few_distinct_variants: {len(distinct)} < {min_distinct}")
    failed = [r for r in results if not r["passed"]]
    if failed:
        errors.append(f"failed_compounds: {[r['seed'] for r in failed]}")
    return {
        "passed": not errors,
        "errors": errors,
        "distinct_variants": len(distinct),
        "results": results,
    }
