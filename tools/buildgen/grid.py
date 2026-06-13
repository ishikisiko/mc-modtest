"""Tagged voxel grid with pass priorities and PROTECTED-cell semantics.

Every block written by a build op carries:
  - state:    full blockstate string ("minecraft:oak_planks", with [props] if any)
  - tags:     frozenset of semantic tags (STRUCTURE / ROOF / OPENING / ...)
  - priority: the pass priority that wrote it
  - slot:     the style material slot it came from (for the variation pass)

Write rule: a PROTECTED cell is never overwritten by a normal write.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, FrozenSet, Iterable, Iterator, Optional, Tuple

Pos = Tuple[int, int, int]

AIR = "minecraft:air"

# Pass priorities (see v0.3.0 spec section 8)
PRIORITY = {
    "AIR_CARVE": 0,
    "FOUNDATION": 10,
    "STRUCTURE": 20,
    "FACADE": 30,
    "OPENING": 40,
    "ROOF": 50,
    "INTERIOR": 60,
    "DETAIL": 70,
    "PROTECTED": 90,
}

KNOWN_TAGS = {
    "STRUCTURE", "ROOF", "FACADE", "OPENING", "INTERIOR",
    "DETAIL", "AIR_CARVE", "PROTECTED", "FOUNDATION", "GROUND",
}


@dataclass(frozen=True)
class Cell:
    state: str
    tags: FrozenSet[str]
    priority: int
    slot: Optional[str] = None

    @property
    def protected(self) -> bool:
        return "PROTECTED" in self.tags

    @property
    def is_air(self) -> bool:
        return self.state == AIR


class BlockGrid:
    def __init__(self) -> None:
        self.cells: Dict[Pos, Cell] = {}

    def set(self, pos: Pos, state: str, tags: Iterable[str], priority: int,
            slot: Optional[str] = None, force: bool = False) -> bool:
        """Write a block. Returns False when blocked by a PROTECTED cell."""
        tags = frozenset(tags)
        unknown = tags - KNOWN_TAGS
        if unknown:
            raise ValueError(f"unknown tags {sorted(unknown)} at {pos}")
        existing = self.cells.get(pos)
        if existing is not None and existing.protected and not force:
            return False
        self.cells[pos] = Cell(state, tags, priority, slot)
        return True

    def carve_air(self, pos: Pos, tags: Iterable[str] = ("AIR_CARVE",),
                  priority: int = PRIORITY["AIR_CARVE"]) -> bool:
        return self.set(pos, AIR, tags, priority)

    def get(self, pos: Pos) -> Optional[Cell]:
        return self.cells.get(pos)

    def state_at(self, pos: Pos) -> str:
        cell = self.cells.get(pos)
        return cell.state if cell else AIR

    def is_empty(self, pos: Pos) -> bool:
        """True when nothing was written there, or only air."""
        cell = self.cells.get(pos)
        return cell is None or cell.is_air

    def replace_state(self, pos: Pos, state: str, slot: Optional[str] = None) -> bool:
        """Swap blockstate keeping tags/priority (material variation)."""
        cell = self.cells.get(pos)
        if cell is None or cell.protected:
            return False
        self.cells[pos] = Cell(state, cell.tags, cell.priority, slot or cell.slot)
        return True

    def iter_cells(self) -> Iterator[Tuple[Pos, Cell]]:
        return iter(self.cells.items())

    def bounds(self) -> Tuple[Pos, Pos]:
        if not self.cells:
            return (0, 0, 0), (0, 0, 0)
        xs = [p[0] for p in self.cells]
        ys = [p[1] for p in self.cells]
        zs = [p[2] for p in self.cells]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def shift(self, dx: int, dy: int, dz: int) -> None:
        self.cells = {(x + dx, y + dy, z + dz): c for (x, y, z), c in self.cells.items()}

    def normalized(self) -> Tuple[int, int, int]:
        """Shift so min corner is (0,0,0); returns structure size."""
        (x0, y0, z0), (x1, y1, z1) = self.bounds()
        self.shift(-x0, -y0, -z0)
        return (x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
