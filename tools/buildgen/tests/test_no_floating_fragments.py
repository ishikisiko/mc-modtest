"""Guard: sect-family templates carry no floating fragments.

A floating fragment is a non-air block none of whose six faces touches another
non-air block of the template: the unsupported corner wing slabs, diagonal
stair runs touching only at their edges, shutters left on a carved wall,
lanterns and bells hanging from nothing. The bottom layer (y=0) replaces the
terrain block when a template is placed, so it is always supported from below.

Run from the repository root (after regenerating the structures):
    python3 tools/buildgen/tests/test_no_floating_fragments.py
    python3 tools/buildgen/tests/test_no_floating_fragments.py --all
``--all`` also lists the count for every other template (informational only).
"""

from __future__ import annotations

import random
import sys
from pathlib import Path
from typing import Dict, List, Tuple

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen import ops  # noqa: E402
from buildgen.grid import BlockGrid  # noqa: E402
from buildgen.massing import Node  # noqa: E402
from buildgen.nbtread import read_gzipped_nbt, state_string  # noqa: E402
from buildgen.style import load_style  # noqa: E402

STRUCTURE_DIR = _TOOLS_DIR.parent / "src/main/resources/data/myvillage/structure"
SECT_FAMILY = (
    "sect_gate", "sect_main_hall", "scripture_pavilion", "alchemy_room",
    "disciple_quarters", "bell_drum_tower", "pagoda", "pavilion",
    "cultivation_sect",
)
AIR_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
              "minecraft:structure_void"}
FACES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))

Pos = Tuple[int, int, int]


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def floating_cells(cells: Dict[Pos, str]) -> List[Tuple[Pos, str]]:
    """Non-air cells above the terrain layer with no non-air face neighbour."""
    out = []
    for (x, y, z), state in cells.items():
        if y == 0:
            continue
        if not any((x + dx, y + dy, z + dz) in cells for dx, dy, dz in FACES):
            out.append(((x, y, z), state))
    return sorted(out)


def template_cells(path: Path) -> Dict[Pos, str]:
    _, nbt = read_gzipped_nbt(str(path))
    palette = nbt.get("palette") or nbt["palettes"][0]
    cells: Dict[Pos, str] = {}
    for block in nbt["blocks"]:
        state = state_string(palette[block["state"]])
        if state.split("[", 1)[0] in AIR_BLOCKS:
            continue
        cells[tuple(block["pos"])] = state
    return cells


def is_sect_family(stem: str) -> bool:
    return stem.startswith(SECT_FAMILY)


def test_sweeping_eave_has_no_unsupported_corner_pieces() -> None:
    """The eave ends straight: no cap stair or wing slab above the corner."""
    style = load_style("cultivation_sect")
    for size in ((9, 5, 9), (11, 5, 11), (13, 5, 9)):  # odd spans hit the centre dip
        grid = BlockGrid()
        vol = Node(id="main", type="main_volume", origin=(0, 0, 0), size=size,
                   meta={"foundation_h": 1, "wall_h": 4,
                         "roof": {"type": "sweeping_eave_roof",
                                  "ridge_axis": "x", "overhang": 2}})
        ops.hollow_box(grid, style, vol)  # the bracket course mounts on these walls
        info = ops.roof_handler("sweeping_eave_roof")(
            grid, style, random.Random(7), vol, None)
        cells = {pos: cell.state for pos, cell in grid.iter_cells()
                 if not cell.is_air}
        floating = floating_cells(cells)
        _assert(not floating, f"sweeping eave {size} leaves floating cells: {floating}")
        corners = info.get("upturned_corners", [])
        _assert(len(corners) == 4, f"sweeping eave {size} corners: {corners}")
        for x, y, z in corners:
            above = grid.get((x, y + 1, z))
            _assert(above is None or above.is_air,
                    f"sweeping eave {size} still caps the corner at {(x, y + 1, z)}")


def test_sect_family_templates_have_no_floating_fragments(show_all: bool = False) -> None:
    paths = sorted(STRUCTURE_DIR.glob("*.nbt"))
    family = [p for p in paths if is_sect_family(p.stem)]
    _assert(len(family) >= 19, f"sect-family templates missing: {[p.stem for p in family]}")
    failures = []
    for path in paths:
        floating = floating_cells(template_cells(path))
        in_family = is_sect_family(path.stem)
        if show_all:
            mark = "" if in_family else "  (outside the sect family)"
            print(f"{path.stem}: {len(floating)}{mark}")
            if not in_family:
                for pos, state in floating:
                    print(f"    {pos} {state}")
        if in_family and floating:
            failures.append(f"{path.stem}: {len(floating)} e.g. {floating[:3]}")
    _assert(not failures, "floating fragments in sect-family templates:\n  "
            + "\n  ".join(failures))


def main(argv: List[str]) -> int:
    test_sweeping_eave_has_no_unsupported_corner_pieces()
    test_sect_family_templates_have_no_floating_fragments(show_all="--all" in argv)
    print("OK sect-family templates have no floating fragments")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
