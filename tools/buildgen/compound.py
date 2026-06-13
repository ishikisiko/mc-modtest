"""Chinese courtyard compound parcel layer.

The compound graph sits above individual MassingGraphs: it generates each
sub-building through the existing pass pipeline, then translates the resulting
voxel grids into a walled parcel with structural landscape and circulation.
"""

from __future__ import annotations

import random
from collections import deque
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Set, Tuple

from .grid import AIR, BlockGrid, PRIORITY
from .massing import MassingGraph
from .passes import BuildContext, PIPELINE
from .quality import quality_check
from .style import Style, load_style

Cell2 = Tuple[int, int]
Pos = Tuple[int, int, int]


COURTYARD_SIZE = {
    "small": (39, 45),
    "medium": (43, 47),
    "large": (47, 47),
}
WATER_FORMS = ("pool", "channel", "well")
PLANTING_LAYOUTS = ("corner_beds", "side_beds", "asymmetric_beds")
ROOF_GRADES = ("硬山", "悬山", "歇山")
GATE_STYLES = ("plain_gate", "lantern_gate", "double_eave_gate")
SYMMETRY_MODES = ("mild_asymmetry", "strict_mirror")


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
        courtyard_size=rng.choice(tuple(COURTYARD_SIZE)),
        water_form=rng.choice(WATER_FORMS),
        planting_layout=rng.choice(PLANTING_LAYOUTS),
        roof_grade=rng.choice(ROOF_GRADES),
        gate_style=rng.choice(GATE_STYLES),
        symmetry=rng.choice(("mild_asymmetry", "mild_asymmetry", "strict_mirror")),
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
                         roof_grade: str) -> BuildContext:
    ctx = BuildContext(style=style, archetype=archetype, scale_tier=archetype,
                       seed=seed, rng=random.Random(seed))
    for pass_fn in PIPELINE:
        pass_fn(ctx)
        if pass_fn.__name__ == "massing_pass":
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
    if ctx.archetype in ("main_hall", "side_wing", "front_row"):
        quality = quality_check(ctx, f"{compound.style_id}/{slot_id}")
    slot = BuildingSlot(slot_id, ctx.archetype, main_origin, shifted_graph,
                        footprint, quality)
    compound.building_slots.append(slot)
    return slot


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
    gate = {(x, 0) for x in range(axis - gate_half, axis + gate_half + 1)}
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
                   {"gate_opening": [axis - gate_half, 0, axis + gate_half, 0]}))


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
    blocked = (compound.building_cells() |
               compound.node_cells("water_feature", "planting", "perimeter_wall"))
    main_front_z = min(z for _, z in main_slot.footprint) - 1
    central = set(_bfs((axis, 1), (axis, main_front_z), lot_w, lot_d, blocked))
    _put_cells(compound, "central_path", "path", central,
               style.primary("GROUND_PATH"), ["DETAIL", "GROUND"], y=-1,
               slot="GROUND_PATH")

    corridor_cells: Set[Cell2] = set()
    west_start = (max(x for x, _ in west_slot.footprint) + 1,
                  (min(z for _, z in west_slot.footprint) + max(z for _, z in west_slot.footprint)) // 2)
    east_start = (min(x for x, _ in east_slot.footprint) - 1,
                  (min(z for _, z in east_slot.footprint) + max(z for _, z in east_slot.footprint)) // 2)
    for start, goal in ((west_start, (axis - 2, main_front_z)),
                        (east_start, (axis + 2, main_front_z))):
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
                                    variant.roof_grade)
    gate_ctx = generate_subbuilding(style, "gate_house", slot_seed + 2,
                                    variant.roof_grade)
    front_ctx = generate_subbuilding(style, "front_row", slot_seed + 3,
                                     variant.roof_grade)
    side_seed = slot_seed + 4
    west_ctx = generate_subbuilding(style, "side_wing", side_seed,
                                    variant.roof_grade)
    east_ctx = generate_subbuilding(
        style, "side_wing",
        side_seed if variant.symmetry == "strict_mirror" else slot_seed + 5,
        variant.roof_grade)

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


def validate_compound_library(compounds: List[CompoundGraph],
                              min_distinct: int = 6) -> dict:
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
