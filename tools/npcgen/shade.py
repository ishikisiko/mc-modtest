"""Baked contact shading for layered models. Standard library only.

A painter asks an `Occluders` built from the rest-pose model how much geometry sits around a texel:
`overhang` looks straight up from just outside the face (light comes from above, so a layer that
sticks out above casts a short shadow down the layer under it) and `contact` looks sideways and down
(the crevice beside a raised band). Cubes named `hollow` are shells whose texture is partly cut out;
they cast nothing here, and their painter shades what shows through them.
"""
from __future__ import annotations

import math

from ..beastgen.cuboid import mat_apply, mat_inverse_rigid


class Occluders:
    def __init__(self, model, hollow=()):
        rest = model.rest_matrices()
        self.boxes = []
        for b, c in model.cubes():
            if c.name in hollow:
                continue
            lo = tuple(c.origin[k] - c.inflate for k in range(3))
            hi = tuple(c.origin[k] + c.size[k] + c.inflate for k in range(3))
            m = rest[b.name]
            corners = [mat_apply(m, (x, y, z)) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
            mn = tuple(min(p[k] for p in corners) for k in range(3))
            mx = tuple(max(p[k] for p in corners) for k in range(3))
            self.boxes.append((c.name, mat_inverse_rigid(m), lo, hi, mn, mx))

    def hit(self, p, exclude=()):
        """Name of a cube whose box holds the rest-pose model-space point p, or None."""
        for name, inv, lo, hi, mn, mx in self.boxes:
            if name in exclude:
                continue
            if p[0] < mn[0] or p[0] > mx[0] or p[1] < mn[1] or p[1] > mx[1] or p[2] < mn[2] or p[2] > mx[2]:
                continue
            q = mat_apply(inv, p)
            if lo[0] <= q[0] <= hi[0] and lo[1] <= q[1] <= hi[1] and lo[2] <= q[2] <= hi[2]:
                return name
        return None

    def overhang(self, t, exclude=(), lifts=((0.3, ((1, 0.45), (2, 0.3), (3, 0.15))), (1.3, ((1, 0.3), (2, 0.2))))):
        """0..1: geometry above the texel that sticks out past its face."""
        skip = (t.cube, *exclude)
        total = 0.0
        for lift, steps in lifts:
            base = tuple(t.p[k] + t.n[k] * lift for k in range(3))
            if self.hit(base, skip):
                continue  # the texel itself is buried under a layer; its painter decides
            for up, weight in steps:
                if self.hit((base[0], base[1] - up, base[2]), skip):
                    total += weight
        return min(1.0, total)

    def contact(self, t, exclude=(), lifts=((0.3, 1.0, 0.5), (1.3, 1.5, 0.3))):
        """0..1: raised geometry beside the texel (either side along the face) or right under it."""
        skip = (t.cube, *exclude)
        n = t.n
        up = (0.0, -1.0, 0.0) if abs(n[1]) < 0.5 else (0.0, 0.0, -1.0)
        side = (n[1] * up[2] - n[2] * up[1], n[2] * up[0] - n[0] * up[2], n[0] * up[1] - n[1] * up[0])
        norm = math.sqrt(sum(v * v for v in side)) or 1.0
        side = tuple(v / norm for v in side)
        total = 0.0
        for lift, reach, weight in lifts:
            base = tuple(t.p[k] + n[k] * lift for k in range(3))
            if self.hit(base, skip):
                continue
            for sign in (-1.0, 1.0):
                if self.hit(tuple(base[k] + side[k] * reach * sign for k in range(3)), skip):
                    total += weight
            if self.hit(tuple(base[k] - up[k] * reach for k in range(3)), skip):
                total += weight * 0.6
        return min(1.0, total)
