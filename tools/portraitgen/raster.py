"""Tiny pixel toolkit over a PIL RGBA image: put/get, spans, masks, outlines, mirroring."""
from __future__ import annotations

from PIL import Image

W = 64
H = 64
CX = 31.5  # the bust's centre column (between 31 and 32)


class Canvas:
    def __init__(self, w: int = W, h: int = H):
        self.w = w
        self.h = h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()

    def put(self, x: int, y: int, c) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def get(self, x: int, y: int):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return (0, 0, 0, 0)

    def hspan(self, x0: int, x1: int, y: int, c) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            self.put(x, y, c)

    def vspan(self, x: int, y0: int, y1: int, c) -> None:
        for y in range(min(y0, y1), max(y0, y1) + 1):
            self.put(x, y, c)

    def rect(self, x0: int, y0: int, x1: int, y1: int, c) -> None:
        for y in range(min(y0, y1), max(y0, y1) + 1):
            self.hspan(x0, x1, y, c)

    def paint(self, mask: set, c) -> None:
        for x, y in mask:
            self.put(x, y, c)

    def paint_fn(self, mask: set, fn) -> None:
        """fn(x, y) -> colour or None."""
        for x, y in mask:
            c = fn(x, y)
            if c is not None:
                self.put(x, y, c)

    def blit(self, other: "Canvas") -> None:
        self.img.alpha_composite(other.img)
        self.px = self.img.load()


def span_mask(rows) -> set:
    """rows: iterable of (y, x0, x1) inclusive spans -> set of (x, y)."""
    out = set()
    for y, x0, x1 in rows:
        for x in range(int(min(x0, x1)), int(max(x0, x1)) + 1):
            out.add((x, y))
    return out


def mirror_x(x: float) -> float:
    return 2 * CX - x


def mirror_mask(mask: set) -> set:
    return {(int(round(mirror_x(x))), y) for x, y in mask}


def both_sides(mask: set) -> set:
    return mask | mirror_mask(mask)


def outline(mask: set, diagonals: bool = False) -> set:
    """Pixels of mask that touch a pixel outside it (4-connected unless diagonals)."""
    steps = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diagonals:
        steps += [(1, 1), (-1, 1), (1, -1), (-1, -1)]
    out = set()
    for x, y in mask:
        for dx, dy in steps:
            if (x + dx, y + dy) not in mask:
                out.add((x, y))
                break
    return out


def edge_side(mask: set, dx: int, dy: int) -> set:
    """Pixels of mask whose neighbour in direction (dx, dy) is outside: a one-sided rim."""
    return {(x, y) for x, y in mask if (x + dx, y + dy) not in mask}


def below(mask: set, rows: int = 1) -> set:
    """Pixels just under the mask's bottom edge (not in the mask), up to `rows` deep."""
    out = set()
    bottom = edge_side(mask, 0, 1)
    for x, y in bottom:
        for k in range(1, rows + 1):
            if (x, y + k) not in mask:
                out.add((x, y + k))
    return out


def interp_hw(points, y: float) -> float:
    """Piecewise-linear half-width profile from (y, hw) keypoints; outside -> nearest end."""
    if y <= points[0][0]:
        return points[0][1]
    if y >= points[-1][0]:
        return points[-1][1]
    for (y0, h0), (y1, h1) in zip(points, points[1:]):
        if y0 <= y <= y1:
            t = (y - y0) / (y1 - y0) if y1 > y0 else 0.0
            return h0 + (h1 - h0) * t
    return points[-1][1]


def profile_mask(points, y0: int, y1: int, dx: float = 0.0, grow: float = 0.0) -> set:
    """Symmetric blob about CX from a half-width profile, rows y0..y1 inclusive."""
    rows = []
    for y in range(y0, y1 + 1):
        hw = interp_hw(points, y) + grow
        if hw <= 0:
            continue
        rows.append((y, int(round(CX + dx - hw + 0.5)), int(round(CX + dx + hw - 0.5))))
    return span_mask(rows)


def dither(x: int, y: int) -> bool:
    return (x + y) % 2 == 0
