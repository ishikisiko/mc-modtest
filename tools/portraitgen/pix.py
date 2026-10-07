"""A 64x64 grid of colour ROLES (single characters) that the person's palette resolves to RGB at
the end. Parts paint roles, never colours, so one drawing serves every hair colour, skin tone and
robe. '.' is transparent. Helpers mirror tools/portraitgen/old/hand.py, the hand-drawn sample."""
from __future__ import annotations

W = 64
H = 64
CX = 31.5


class Grid:
    def __init__(self):
        self.cells = [["."] * W for _ in range(H)]
        self.journal = None     # when a dict, every put is recorded (the exporter captures a step's writes)

    def put(self, x: int, y: int, role: str) -> None:
        if 0 <= x < W and 0 <= y < H:
            self.cells[y][x] = role
            if self.journal is not None:
                self.journal[(x, y)] = role

    def get(self, x: int, y: int) -> str:
        if 0 <= x < W and 0 <= y < H:
            return self.cells[y][x]
        return "."

    def span(self, y: int, x0: int, x1: int, role: str) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            self.put(x, y, role)

    def spans(self, rows: dict, role: str) -> None:
        for y, (x0, x1) in rows.items():
            self.span(y, x0, x1, role)

    def paint(self, mask, role: str) -> None:
        for x, y in mask:
            self.put(x, y, role)

    def put_if(self, x: int, y: int, role: str, only: str) -> None:
        """Paint only over cells currently holding one of the roles in `only`."""
        if self.get(x, y) in only:
            self.put(x, y, role)

    def replace(self, mask, old: str, new: str) -> None:
        for x, y in mask:
            if self.get(x, y) in old:
                self.put(x, y, new)


def rng(y0: int, y1: int, x0: int, x1: int) -> dict:
    """Rows y0..y1 all spanning x0..x1."""
    return {y: (x0, x1) for y in range(y0, y1 + 1)}


def mask_of(rows: dict) -> set:
    return {(x, y) for y, (x0, x1) in rows.items() for x in range(x0, x1 + 1)}


def mirror(mask) -> set:
    return {(63 - x, y) for x, y in mask}


def mirror_rows(rows: dict) -> dict:
    return {y: (63 - x1, 63 - x0) for y, (x0, x1) in rows.items()}


def outline(mask, skip=frozenset()) -> set:
    """Cells of mask with a 4-neighbour outside mask (and not in skip)."""
    out = set()
    for x, y in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in mask and q not in skip:
                out.add((x, y))
                break
    return out


def bottom_edge(mask) -> set:
    return {(x, y) for x, y in mask if (x, y + 1) not in mask}


def side_edge(mask, dx: int) -> set:
    return {(x, y) for x, y in mask if (x + dx, y) not in mask}


def dither(x: int, y: int) -> bool:
    return (x + y) % 2 == 0
