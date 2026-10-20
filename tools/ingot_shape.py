"""Canonical flat-ingot silhouette for every 锭 item.

All ingot items (玄铁锭, 赤铜锭, 血灵锭 and any future 锭) must use these three
polygons. Shape studies such as bar/cake/block are historical only and must not
be wired into a shipped ingot.

Keep this module dependency-free: generators import the constants and draw
their own palette and surface details.
"""
from __future__ import annotations

FLAT_SIDE = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 23), (7, 26), (3, 22)]
FLAT_FRONT = [(3, 17), (23, 15), (29, 14), (25, 22), (7, 26), (3, 22)]
FLAT_TOP = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 18), (7, 22)]


def draw(c, side, front, top) -> None:
    """Fill the three canonical faces in back-to-front order."""
    c.poly(FLAT_SIDE, side)
    c.poly(FLAT_FRONT, front)
    c.poly(FLAT_TOP, top)


def base_mask() -> tuple[bool, ...]:
    """Alpha mask of the bare flat ingot, used by tests to reject other shapes."""
    from tools.gen_alchemy_assets import Canvas

    c = Canvas()
    draw(c, (0, 0, 0), (0, 0, 0), (0, 0, 0))
    c.outline((0, 0, 0))
    return tuple(p[3] > 0 for row in c.rows for p in row)
