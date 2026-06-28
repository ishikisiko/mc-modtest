"""Regression tests for the mansion enclosure-planning rewrite.

Run from the repository root:
    python3 tools/buildgen/tests/test_mansion_enclosure_plan.py
"""

from __future__ import annotations

import sys
from collections import deque
from pathlib import Path
from typing import Set, Tuple

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.compound import (  # noqa: E402
    MANSION_TEMPLATES,
    _derive_mansion_yards,
    _gate_entry_standable,
    _voxel_walk_bfs,
    generate_mansion,
    generate_subbuilding,
    select_mansion_variant,
)
from buildgen.style import load_style  # noqa: E402

Cell2 = Tuple[int, int]

SEED = 20260618
ROOF = "chinese_round_ridge"


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def _connected(cells: Set[Cell2]) -> bool:
    if not cells:
        return False
    start = next(iter(cells))
    seen = {start}
    q = deque([start])
    while q:
        x, z = q.popleft()
        for nxt in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if nxt not in cells or nxt in seen:
                continue
            seen.add(nxt)
            q.append(nxt)
    return seen == cells


def _gate_borders(node, yard: Set[Cell2]) -> bool:
    return any(
        (gx + dx, gz + dz) in yard
        for gx, gz in node.cells
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
    )


def test_orientation_detail() -> None:
    style = load_style("chinese_mansion")

    south_wing = generate_subbuilding(style, "side_wing", SEED, ROOF, None)
    west_wing = generate_subbuilding(
        style, "side_wing", SEED, ROOF, None,
        form_overrides={"facing": "west"})
    south_main = south_wing.graph.get("main")
    west_main = west_wing.graph.get("main")
    _assert(west_wing.door_info["wall"] == "west",
            "west-facing side_wing did not place its door on the west wall")
    front = west_wing.door_info["front"]
    _assert(front == (west_main.x0 - 1, front[1], west_wing.door_info["along"]),
            f"west-facing side_wing front cell is wrong: {front}")
    _assert(west_main.size == south_main.size,
            "side_wing facing changed the main volume size")
    _assert(west_main.meta["roof"]["ridge_axis"] == south_main.meta["roof"]["ridge_axis"] == "z",
            "side_wing facing changed the roof axis")

    north_front = generate_subbuilding(
        style, "front_row", SEED, ROOF, None,
        form_overrides={"facing": "north"})
    _assert(north_front.door_info["wall"] == "back",
            "north-facing front_row did not place its door on the back wall")


def test_mansion_enclosure_variants() -> None:
    for i in range(len(MANSION_TEMPLATES)):
        seed = SEED + i
        compound = generate_mansion(seed)
        lot_w, lot_d = compound.lot_size
        axis = compound.axis_x
        slots = {slot.id: slot for slot in compound.building_slots}

        gate_house = slots.get("gate_house")
        _assert(gate_house is not None, f"seed {seed}: missing gate_house")
        gh_xs = {x for x, z in gate_house.footprint if z == 0}
        _assert(gh_xs and min(z for _, z in gate_house.footprint) == 0,
                f"seed {seed}: gate_house does not straddle z=0")
        _assert(min(gh_xs) <= axis <= max(gh_xs),
                f"seed {seed}: gate_house is not centered on the axis")

        perimeter = next(n for n in compound.parcel_nodes
                         if n.type == "perimeter_wall").cells
        south_gap = {(x, 0) for x in range(lot_w)} - perimeter
        _assert(south_gap == {(x, 0) for x in gh_xs},
                f"seed {seed}: south wall gap does not match gate_house footprint")

        start = _gate_entry_standable(compound)
        _assert(start is not None, f"seed {seed}: no standable gate entry")
        visited = _voxel_walk_bfs(compound, start, lot_w, lot_d)
        _assert(any((axis, y, compound.meta["gate_inner_z"]) in visited
                    for y in range(-3, 13)),
                f"seed {seed}: voxel walk did not pass through the gate_house")

        inner_gates = [n for n in compound.parcel_nodes if n.type == "inner_gate"]
        gates_for_yards = [(n.meta.get("kind", "gate"), n.meta["band"][0])
                           for n in inner_gates if n.meta.get("band")]
        yards = _derive_mansion_yards(compound, [], gates_for_yards, lot_w, lot_d)
        building_cells = compound.building_cells()
        for name in ("front_yard", "main_yard", "back_yard"):
            cells = yards.get(name, set())
            _assert(cells, f"seed {seed}: {name} is empty")
            _assert(_connected(cells), f"seed {seed}: {name} is disconnected")
            _assert(not (cells & building_cells),
                    f"seed {seed}: {name} overlaps a building footprint")

        gate_nodes = {n.meta.get("kind"): n for n in inner_gates}
        yimen = gate_nodes.get("yimen_gate")
        ermen = gate_nodes.get("ermen_gate")
        _assert(yimen is not None and _gate_borders(yimen, yards["front_yard"])
                and _gate_borders(yimen, yards["main_yard"]),
                f"seed {seed}: yimen does not border front_yard + main_yard")
        _assert(ermen is not None and _gate_borders(ermen, yards["main_yard"])
                and _gate_borders(ermen, yards["back_yard"]),
                f"seed {seed}: ermen does not border main_yard + back_yard")


def main() -> int:
    # Check the selected template set explicitly, then run the behavior tests.
    _assert(len({select_mansion_variant(SEED + i).key()
                 for i in range(len(MANSION_TEMPLATES))}) == len(MANSION_TEMPLATES),
            "canonical mansion seeds do not cover every mansion template")
    test_orientation_detail()
    test_mansion_enclosure_variants()
    print(f"OK mansion enclosure plan: {len(MANSION_TEMPLATES)} variants")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
