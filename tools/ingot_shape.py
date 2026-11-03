"""Canonical flat-ingot silhouette for every 锭 item.

All ingot items (玄铁锭, 赤铜锭, 血灵锭 and any future 锭) must use these three
polygons. Shape studies such as bar/cake/block are historical only and must not
be wired into a shipped ingot.

Keep this module dependency-free: generators import the constants and draw
their own palette and surface details.
"""
from __future__ import annotations

import math

FLAT_SIDE = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 23), (7, 26), (3, 22)]
FLAT_FRONT = [(3, 17), (23, 15), (29, 14), (25, 22), (7, 26), (3, 22)]
FLAT_TOP = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 18), (7, 22)]

# The lower contour follows the same authored sprite faces. The two hidden rear
# points continue the five-pixel side; the visible points come from the faces above.
FLAT_BOTTOM = [FLAT_FRONT[-1], (9, 15), (24, 13), FLAT_FRONT[3],
               FLAT_SIDE[4], FLAT_FRONT[4]]
FLAT_GUI_ROTATION = (45, 11, 0)


def model() -> dict:
    """Lift the canonical sprite contours into one closed, flat 3D ingot.

    The top stays at the atlas authors' existing .0325 m surface, so their
    material inlays remain in place. The intended GUI view projects both rings
    back onto the original 32px contour. No exporter owns another ingot shape.
    """
    width, height = .38, .065
    pitch, yaw = (math.radians(v) for v in FLAT_GUI_ROTATION[:2])
    cp, sp, cy, sy = math.cos(pitch), math.sin(pitch), math.cos(yaw), math.sin(yaw)

    def ring(points, y):
        result = []
        for px, py in points:
            sx = (px - 16) * width / 26
            vertical = ((15 - py) * width / 26 - cp * y) / sp
            result.append([cy * sx + sy * vertical, sy * sx - cy * vertical])
        return result

    top, bottom = ring(FLAT_TOP, height / 2), ring(FLAT_BOTTOM, -height / 2)
    # Centre the top face under the existing authored X/Z material patterns.
    centre = [(min(p[a] for p in top) + max(p[a] for p in top)) / 2 for a in (0, 1)]
    top, bottom = ([[p[a] - centre[a] for a in (0, 1)] for p in outline]
                   for outline in (top, bottom))
    size = [2 * max(abs(p[0]) for p in top + bottom), height,
            2 * max(abs(p[1]) for p in top + bottom)]
    normalize = lambda outline: [[p[0] / size[0], p[1] / size[2]] for p in outline]
    return {"size": size, "profile": {"top": normalize(top), "bottom": normalize(bottom),
            "sideMaterials": [2, 0, 2, 0, 0, 2]}}


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
