#!/usr/bin/env python3
"""Validate the Java runtime town planner's fixed ritual-axis layout.

This validator checks the shrine-terminating parcel/open-space rectangles,
vertical template ground-layer convention, and the actual shipped template
dimensions, so parcel templates do not spill into streets or leave a one-block
hollow under the realized buildings after placement.
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


def street_room_fixture_cells(lane_z: int, streets: Set[Cell]) -> Dict[str, Set[Cell]]:
    cells: Dict[str, Set[Cell]] = {}
    for x in range(CENTER_X - SPINE_HALF_WIDTH - 13, CENTER_X - SPINE_HALF_WIDTH - 4, 4):
        fixture = {(x, lane_z + 1), (x + 1, lane_z + 1)}
        if fixture <= streets:
            cells[f"west_stall_{x}"] = fixture
    for x in range(CENTER_X + SPINE_HALF_WIDTH + 5, CENTER_X + SPINE_HALF_WIDTH + 14, 4):
        fixture = {(x, lane_z + 1), (x + 1, lane_z + 1)}
        if fixture <= streets:
            cells[f"east_stall_{x}"] = fixture
    for z in range(8, DEPTH - 8, 10):
        x = CENTER_X - SPINE_HALF_WIDTH if (z // 10) % 2 == 0 else CENTER_X + SPINE_HALF_WIDTH
        fixture = {(x, z)}
        if fixture <= streets:
            cells[f"spine_lamp_{z}"] = fixture
    return cells


def ritual_fixture_cells() -> Dict[str, Set[Cell]]:
    plaza_z0 = DEPTH - 3 - 20 + 1 - 9
    fixtures: Dict[str, Set[Cell]] = {
        "paifang_gate": rect(CENTER_X - 6, plaza_z0 - 1, CENTER_X + 6, plaza_z0 - 1),
    }
    lanterns = set()
    for z in range(8, plaza_z0 - 2, 5):
        lanterns.add((CENTER_X - 5, z))
        lanterns.add((CENTER_X + 5, z))
    fixtures["lantern_approach"] = lanterns
    return fixtures


def plan(lane_z: int) -> Tuple[Dict[str, ParcelData], Dict[str, Rect], Set[Cell]]:
    shrine_z1 = DEPTH - 3
    shrine_z0 = shrine_z1 - 20 + 1
    shrine_x0 = CENTER_X - 27 // 2
    shrine_x1 = shrine_x0 + 27 - 1
    plaza = (CENTER_X - 16, shrine_z0 - 9, CENTER_X + 16, shrine_z0 - 1)
    spine = rect(CENTER_X - SPINE_HALF_WIDTH, 0, CENTER_X + SPINE_HALF_WIDTH, plaza[3])
    spine |= rect(*plaza)
    spine |= rect(CENTER_X - 6, plaza[1] - 1, CENTER_X + 6, plaza[1] - 1)
    lanes = set()
    lanes |= rect(8, lane_z - 1, WIDTH - 9, lane_z + 1)
    lanes |= rect(8, 16, WIDTH - 9, 18)
    lanes |= rect(8, shrine_z0 - 1, WIDTH - 9, shrine_z0 - 1)
    lanes -= spine
    streets = spine | lanes
    parcels = {
        "town_shrine": ((shrine_x0, shrine_z0, shrine_x1, shrine_z1), "town_shrine_001", "civic", True),
        "west_core_shop": ((20, 20, 42, lane_z - 2), "cultivation_shop_002", "market", False),
        "east_core_shop": ((WIDTH - 42, 20, WIDTH - 20, lane_z - 2), "cultivation_shop_003", "market", False),
        "west_market": ((12, lane_z + 2, 31, shrine_z0 - 2), "cultivation_market_001", "market", False),
        "east_market": ((WIDTH - 31, lane_z + 2, WIDTH - 9, shrine_z0 - 2), "cultivation_market_001", "market", False),
        "west_outer_south": ((16, 1, 36, 15), "cultivation_house_001", "housing", False),
        "east_outer_south": ((WIDTH - 36, 1, WIDTH - 16, 15), "cultivation_house_002", "housing", False),
        "west_outer_north": ((8, shrine_z0, 31, shrine_z1 - 1), "cultivation_house_003", "housing", False),
        "east_outer_north": ((WIDTH - 31, shrine_z0, WIDTH - 9, shrine_z1 - 1), "cultivation_market_002", "defense", False),
    }
    open_regions = {
        "market_mouth_square": (CENTER_X - 16, lane_z + 2, CENTER_X - 5, min(lane_z + 7, plaza[1] - 1)),
        "well_court": (8, lane_z - 11, 16, lane_z - 5),
        "back_lane_yard": (WIDTH - 18, lane_z - 13, WIDTH - 9, lane_z - 7),
    }
    return parcels, open_regions, streets


def main() -> int:
    errors = []
    for lane_z in (DEPTH // 2 - 2,):
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
            if ground_layer_blocks < max(1, under_layer_blocks // 2):
                errors.append(
                    f"lane_z={lane_z}: template_ground_layer_too_sparse:"
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
        for fixture_id, cells in street_room_fixture_cells(lane_z, streets).items():
            if not cells <= streets:
                errors.append(f"lane_z={lane_z}: street_room_not_on_street:{fixture_id}")
            for parcel_id, template in template_footprints.items():
                if intersects(cells, template):
                    errors.append(f"lane_z={lane_z}: street_room_template_overlap:{fixture_id}:{parcel_id}")
        for fixture_id, cells in ritual_fixture_cells().items():
            for parcel_id, template in template_footprints.items():
                if intersects(cells, template):
                    errors.append(f"lane_z={lane_z}: ritual_fixture_template_overlap:{fixture_id}:{parcel_id}")
            if intersects(cells, parcel_cells):
                errors.append(f"lane_z={lane_z}: ritual_fixture_parcel_overlap:{fixture_id}")
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
        "OK runtime town ritual-axis plan keeps streets, parcels, open spaces, "
        "template footprints, ground-layer support, ground details, and "
        "street-room fixtures disjoint"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
