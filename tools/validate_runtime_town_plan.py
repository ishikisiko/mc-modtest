#!/usr/bin/env python3
"""Validate the Java runtime town planner's fixed layout variants.

The runtime planner chooses one of three central-lane offsets from the seed.
This validator checks all three variants against the same parcel/open-space
rectangles, vertical template ground-layer convention, and the actual shipped
template dimensions, so parcel templates do not spill into streets or leave a
one-block hollow under the realized buildings after placement.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Dict, Set, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.nbtread import read_gzipped_nbt  # noqa: E402

Cell = Tuple[int, int]
Rect = Tuple[int, int, int, int]
ParcelData = Tuple[Rect, str, str, bool]

WIDTH = 96
DEPTH = 80
CENTER_X = WIDTH // 2
SPINE_HALF_WIDTH = 3
SOUTH_LANE_Z = 21
NORTH_LANE_Z = DEPTH - 19
TEMPLATE_GROUND_LAYER = 1
STRUCTURE_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"


def rect(x0: int, z0: int, x1: int, z1: int) -> Set[Cell]:
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


def intersects(a: Set[Cell], b: Set[Cell]) -> bool:
    return any(cell in b for cell in a)


def touches(a: Set[Cell], b: Set[Cell]) -> bool:
    for x, z in a:
        if any(n in b for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))):
            return True
    return False


def template_root(name: str) -> dict:
    _name, root = read_gzipped_nbt(str(STRUCTURE_DIR / f"{name}.nbt"))
    return root


def template_size(name: str) -> Tuple[int, int]:
    root = template_root(name)
    sx, _sy, sz = root["size"]
    return int(sx), int(sz)


def template_layer_coverage(name: str, y: int) -> int:
    root = template_root(name)
    return sum(1 for block in root["blocks"] if int(block["pos"][1]) == y)


def template_cells(bounds: Rect, template_id: str) -> Set[Cell]:
    x0, z0, x1, z1 = bounds
    sx, sz = template_size(template_id)
    px = x0 + max(0, ((x1 - x0 + 1) - sx) // 2)
    pz = z0 + max(0, ((z1 - z0 + 1) - sz) // 2)
    return rect(px, pz, px + sx - 1, pz + sz - 1)


def free_parcel_cells(bounds: Rect, streets: Set[Cell], template: Set[Cell]) -> Set[Cell]:
    return rect(*bounds) - streets - template


def street_room_fixture_cells(lane_z: int) -> Dict[str, Set[Cell]]:
    cells: Dict[str, Set[Cell]] = {}
    for x in range(CENTER_X - SPINE_HALF_WIDTH - 13, CENTER_X - SPINE_HALF_WIDTH - 4, 4):
        cells[f"west_stall_{x}"] = {(x, lane_z + 1), (x + 1, lane_z + 1)}
    for x in range(CENTER_X + SPINE_HALF_WIDTH + 5, CENTER_X + SPINE_HALF_WIDTH + 14, 4):
        cells[f"east_stall_{x}"] = {(x, lane_z + 1), (x + 1, lane_z + 1)}
    for z in range(8, DEPTH - 8, 10):
        x = CENTER_X - SPINE_HALF_WIDTH if (z // 10) % 2 == 0 else CENTER_X + SPINE_HALF_WIDTH
        cells[f"spine_lamp_{z}"] = {(x, z)}
    return cells


def plan(lane_z: int) -> Tuple[Dict[str, ParcelData], Dict[str, Rect], Set[Cell]]:
    spine = rect(CENTER_X - SPINE_HALF_WIDTH, 0, CENTER_X + SPINE_HALF_WIDTH, DEPTH - 1)
    lanes = set()
    lanes |= rect(8, lane_z - 1, WIDTH - 9, lane_z + 1)
    lanes |= rect(8, SOUTH_LANE_Z - 1, WIDTH - 9, SOUTH_LANE_Z + 1)
    lanes |= rect(8, NORTH_LANE_Z - 1, WIDTH - 9, NORTH_LANE_Z + 1)
    lanes -= spine
    streets = spine | lanes
    parcels = {
        "landmark_temple": ((16, lane_z + 2, 44, lane_z + 17), "lord_manor_001", "civic", True),
        "west_core_shop": ((20, lane_z - 15, 44, lane_z - 2), "medium_shop_001", "market", False),
        "east_core_shop": ((52, lane_z - 15, 76, lane_z - 2), "medium_shop_002", "market", False),
        "east_market_inn": ((52, lane_z + 2, 76, lane_z + 17), "tavern_002", "market", False),
        "west_outer_south": ((20, 3, 44, 19), "medium_house_001", "housing", False),
        "east_outer_south": ((52, 3, 76, 19), "small_house_001", "housing", False),
        "west_outer_north": ((20, DEPTH - 17, 44, DEPTH - 4), "medium_house_002", "housing", False),
        "east_outer_north": ((52, DEPTH - 17, 76, DEPTH - 4), "blacksmith_001", "defense", False),
    }
    open_regions = {
        "market_mouth_square": (8, lane_z + 2, 15, lane_z + 8),
        "well_court": (8, lane_z - 9, 16, lane_z - 4),
        "back_lane_yard": (79, 29, 87, 35),
    }
    return parcels, open_regions, streets


def main() -> int:
    errors = []
    for lane_z in (DEPTH // 2 - 2, DEPTH // 2, DEPTH // 2 + 2):
        parcels, open_regions, streets = plan(lane_z)
        parcel_cells: Set[Cell] = set()
        template_footprints: Dict[str, Set[Cell]] = {}
        for parcel_id, (bounds, template_id, role, dominant) in parcels.items():
            cells = rect(*bounds)
            template = template_cells(bounds, template_id)
            template_footprints[parcel_id] = template
            ground_layer_blocks = template_layer_coverage(template_id, TEMPLATE_GROUND_LAYER)
            under_layer_blocks = template_layer_coverage(template_id, TEMPLATE_GROUND_LAYER - 1)
            if ground_layer_blocks == 0:
                errors.append(f"lane_z={lane_z}: template_ground_layer_empty:{parcel_id}:{template_id}")
            if ground_layer_blocks <= under_layer_blocks:
                errors.append(
                    f"lane_z={lane_z}: template_ground_layer_not_dominant:"
                    f"{parcel_id}:{template_id}:ground={ground_layer_blocks}:under={under_layer_blocks}"
                )
            if intersects(cells, streets):
                errors.append(f"lane_z={lane_z}: parcel_street_overlap:{parcel_id}")
            if intersects(template, streets):
                errors.append(f"lane_z={lane_z}: template_street_overlap:{parcel_id}:{template_id}")
            if not touches(cells, streets):
                errors.append(f"lane_z={lane_z}: parcel_not_reachable:{parcel_id}")
            overlap = parcel_cells & cells
            if overlap:
                errors.append(f"lane_z={lane_z}: parcel_overlap:{parcel_id}")
            parcel_cells |= cells
            free_cells = free_parcel_cells(bounds, streets, template)
            if role == "housing" and len(free_cells) < 2:
                errors.append(f"lane_z={lane_z}: housing_detail_space_short:{parcel_id}:{len(free_cells)}")
            if dominant and len(free_cells) < 2:
                errors.append(f"lane_z={lane_z}: landmark_detail_space_short:{parcel_id}:{len(free_cells)}")
        for fixture_id, cells in street_room_fixture_cells(lane_z).items():
            if not cells <= streets:
                errors.append(f"lane_z={lane_z}: street_room_not_on_street:{fixture_id}")
            for parcel_id, template in template_footprints.items():
                if intersects(cells, template):
                    errors.append(f"lane_z={lane_z}: street_room_template_overlap:{fixture_id}:{parcel_id}")
        for region_id, bounds in open_regions.items():
            cells = rect(*bounds)
            if intersects(cells, streets):
                errors.append(f"lane_z={lane_z}: negative_space_street_overlap:{region_id}")
            if intersects(cells, parcel_cells):
                errors.append(f"lane_z={lane_z}: negative_space_parcel_overlap:{region_id}")
            for parcel_id, template in template_footprints.items():
                if intersects(cells, template):
                    errors.append(f"lane_z={lane_z}: negative_space_template_overlap:{region_id}:{parcel_id}")
    for error in errors:
        print(f"FAIL {error}")
    if errors:
        return 1
    print(
        "OK runtime town plan variants keep streets, parcels, open spaces, "
        "template footprints, ground-layer support, ground details, and "
        "street-room fixtures disjoint"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
