"""Texel sampling and painting helpers. Standard library only.

`texels(model)` walks every owned UV island and yields, for each texel, where it lies on the model:
bone, cube, face direction, the texel's centre in bone-local and in rest-pose model space, the face
normal in model space, and the distance to the nearest other cube (crease and buried texels). A
definition's painter turns that into a colour, so shading and markings are functions of 3D position
and run on across cube seams; mirrored twins (uv_from) show their source's texels mirrored.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from .cuboid import NORMALS, VISUAL, cube_polygons, mat_apply, mat_apply_dir, mat_inverse_rigid, uv_regions


@dataclass
class Texel:
    u: int
    v: int
    bone: str
    cube: str
    face: str         # vanilla Direction of the polygon (DOWN = visual top)
    local: tuple      # bone-local point (model units)
    p: tuple          # rest-pose model-space point (+Y down, ground y = 24)
    n: tuple          # rest-pose model-space outward normal
    fu: float         # 0..1 across the face's island, left to right in the atlas
    fv: float         # 0..1 down the face's island
    crease: float     # distance to the nearest other cube's box (negative inside it)
    box_lo: tuple     # the cube's bone-local corners
    box_hi: tuple

    @property
    def visual(self):
        return VISUAL[self.face]

    @property
    def height(self):
        return 24.0 - self.p[1]

    @property
    def cube_top(self):
        return self.box_lo[1]

    @property
    def cube_bottom(self):
        return self.box_hi[1]

    @property
    def cube_height(self):
        return self.box_hi[1] - self.box_lo[1]


def _box_distance(q, lo, hi):
    d_out = 0.0
    inside = True
    d_in = float("inf")
    for k in range(3):
        if q[k] < lo[k]:
            d_out += (lo[k] - q[k]) ** 2
            inside = False
        elif q[k] > hi[k]:
            d_out += (q[k] - hi[k]) ** 2
            inside = False
        else:
            d_in = min(d_in, q[k] - lo[k], hi[k] - q[k])
    return -d_in if inside else math.sqrt(d_out)


def _face_map(verts):
    """Affine (u, v) -> point for one polygon: corner with min u/min v, and the u and v edge vectors."""
    us = sorted({round(v[3], 9) for v in verts})
    vs = sorted({round(v[4], 9) for v in verts})

    def find(u, v):
        for p in verts:
            if abs(p[3] - u) < 1e-9 and abs(p[4] - v) < 1e-9:
                return p
        raise ValueError("degenerate face")

    a = find(us[0], vs[0])
    b = find(us[-1], vs[0])
    c = find(us[0], vs[-1])
    return a, b, c


def texels(model, skip_crease=()):
    """Every texel of every owned island, in atlas order per island."""
    tw, th = model.texture_size
    rest = model.rest_matrices()
    inv = {name: mat_inverse_rigid(m) for name, m in rest.items()}
    boxes = []
    for b, c in model.cubes():
        lo = tuple(c.origin[k] - c.inflate for k in range(3))
        hi = tuple(c.origin[k] + c.size[k] + c.inflate for k in range(3))
        boxes.append((c.name, b.name, lo, hi))
    out = []
    for b, c in model.cubes():
        if c.uv_from:
            continue
        regions = uv_regions(c)
        lo_c = c.origin
        hi_c = tuple(c.origin[k] + c.size[k] for k in range(3))
        for direction, verts in cube_polygons(c, tw, th):
            ru, rv, rw, rh = regions[direction]
            a, bb, cc = _face_map(verts)
            n_model = mat_apply_dir(rest[b.name], NORMALS[direction])
            for j in range(rh):
                for i in range(rw):
                    u = (ru + i + 0.5) / tw
                    v = (rv + j + 0.5) / th
                    fu = (u - a[3]) / (bb[3] - a[3])
                    fv = (v - a[4]) / (cc[4] - a[4])
                    local = tuple(a[k] + fu * (bb[k] - a[k]) + fv * (cc[k] - a[k]) for k in range(3))
                    p = mat_apply(rest[b.name], local)
                    crease = float("inf")
                    if c.name not in skip_crease:
                        for name, bone, lo, hi in boxes:
                            if name == c.name or name in skip_crease:
                                continue
                            crease = min(crease, _box_distance(mat_apply(inv[bone], p), lo, hi))
                    out.append(Texel(ru + i, rv + j, b.name, c.name, direction, local, p, n_model,
                                     (i + 0.5) / rw, (j + 0.5) / rh, crease, lo_c, hi_c))
    return out


# ------------------------------------------------------------------------------------ colour helpers
def hex_rgb(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hexes):
    return [hex_rgb(h) for h in hexes]


def pick(ramp_, tone):
    i = int(math.floor(tone + 0.5))
    return ramp_[max(0, min(len(ramp_) - 1, i))]


def _hash3(x, y, z, seed):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def value_noise(p, cell, seed=0):
    """Smooth 3D value noise in [0, 1] with lattice spacing `cell` model units."""
    x, y, z = (c / cell for c in p)
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    sx, sy, sz = (f * f * (3 - 2 * f) for f in (fx, fy, fz))
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (sx if dx else 1 - sx) * (sy if dy else 1 - sy) * (sz if dz else 1 - sz)
                acc += w * _hash3(ix + dx, iy + dy, iz + dz, seed)
    return acc


def image(model, painter, texel_list):
    """rows of RGBA tuples; painter(texel) -> (r, g, b) / (r, g, b, a) / None (left transparent)."""
    tw, th = model.texture_size
    rows = [[(0, 0, 0, 0)] * tw for _ in range(th)]
    for t in texel_list:
        c = painter(t)
        if c is None:
            continue
        rows[t.v][t.u] = tuple(c) + (255,) if len(c) == 3 else tuple(c)
    return rows
