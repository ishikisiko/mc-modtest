"""Cuboid model description, the vanilla box-UV unwrap, a UV packer and the schema-1 model JSON.

Standard library only, so `build --check` and the tests run without numpy.

Everything here mirrors vanilla 1.21.1 exactly (net/minecraft/client/model/geom/ModelPart.java):

* a bone is `parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(pivot, rotation))`
  and is drawn with `translateAndRotate`: T(pivot) * Rz * Ry * Rx (Quaternionf.rotationZYX) * S;
* a cube is `texOffs(u, v).mirror(m).addBox(origin, size, CubeDeformation(inflate))`; `cube_polygons`
  ports `ModelPart.Cube` (vertex order, box unwrap, mirror swap and reversal) polygon for polygon.

Model space is the Java entity-model space: +Y down, ground plane y = 24, front of the animal -Z,
the animal's left +X (its right -X, like the player model's right arm at x = -5).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

# Direction names as vanilla uses them. In model space (+Y down) DOWN is the face at minY, which the
# renderer's scale(-1, -1, 1) turns into the visual top; VISUAL names what a viewer sees.
DIRECTIONS = ("DOWN", "UP", "WEST", "NORTH", "EAST", "SOUTH")
VISUAL = {"DOWN": "top", "UP": "bottom", "WEST": "right", "NORTH": "front", "EAST": "left", "SOUTH": "back"}
NORMALS = {"DOWN": (0.0, -1.0, 0.0), "UP": (0.0, 1.0, 0.0), "WEST": (-1.0, 0.0, 0.0),
           "EAST": (1.0, 0.0, 0.0), "NORTH": (0.0, 0.0, -1.0), "SOUTH": (0.0, 0.0, 1.0)}
NAME_OK = set("abcdefghijklmnopqrstuvwxyz0123456789_")


# ------------------------------------------------------------------------------------ 4x4 math
def mat_identity():
    return [[1.0, 0.0, 0.0, 0.0], [0.0, 1.0, 0.0, 0.0], [0.0, 0.0, 1.0, 0.0], [0.0, 0.0, 0.0, 1.0]]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_translate(x, y, z):
    m = mat_identity()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def mat_scale(x, y, z):
    m = mat_identity()
    m[0][0], m[1][1], m[2][2] = x, y, z
    return m


def mat_rot_zyx(xr, yr, zr):
    """Quaternionf().rotationZYX(zr, yr, xr) as a matrix: Rz * Ry * Rx (radians)."""
    cx, sx = math.cos(xr), math.sin(xr)
    cy, sy = math.cos(yr), math.sin(yr)
    cz, sz = math.cos(zr), math.sin(zr)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    m3 = _m3(rz, _m3(ry, rx))
    m = mat_identity()
    for i in range(3):
        for j in range(3):
            m[i][j] = float(m3[i][j])
    return m


def _m3(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_apply(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


def mat_apply_dir(m, v):
    x, y, z = v
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z,
            m[1][0] * x + m[1][1] * y + m[1][2] * z,
            m[2][0] * x + m[2][1] * y + m[2][2] * z)


def mat_inverse_rigid(m):
    """Inverse of rotation * uniform-free translation matrices (no scale), as rest poses are."""
    r = [[m[j][i] for j in range(3)] for i in range(3)]
    t = (m[0][3], m[1][3], m[2][3])
    out = mat_identity()
    for i in range(3):
        for j in range(3):
            out[i][j] = r[i][j]
        out[i][3] = -(r[i][0] * t[0] + r[i][1] * t[1] + r[i][2] * t[2])
    return out


def bone_matrix(pivot, rotation_rad, scale=(1.0, 1.0, 1.0)):
    """ModelPart.translateAndRotate in model units: T(pivot) * Rz Ry Rx * S."""
    m = mat_translate(*pivot)
    if any(rotation_rad):
        m = mat_mul(m, mat_rot_zyx(*rotation_rad))
    if tuple(scale) != (1.0, 1.0, 1.0):
        m = mat_mul(m, mat_scale(*scale))
    return m


# ------------------------------------------------------------------------------------ model
@dataclass
class Cube:
    name: str
    origin: tuple
    size: tuple
    inflate: float = 0.0
    mirror: bool = False
    uv_from: str | None = None  # another cube (by name) whose island this cube reuses, normally mirrored
    uv: tuple | None = None     # assigned by the packer

    def __post_init__(self):
        self.origin = tuple(float(v) for v in self.origin)
        self.size = tuple(float(v) for v in self.size)


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple
    rotation: tuple = (0.0, 0.0, 0.0)  # degrees
    cubes: list = field(default_factory=list)

    def __post_init__(self):
        self.pivot = tuple(float(v) for v in self.pivot)
        self.rotation = tuple(float(v) for v in self.rotation)

    def rest_matrix(self):
        return bone_matrix(self.pivot, tuple(math.radians(r) for r in self.rotation))


@dataclass
class Model:
    id: str
    bones: list
    look: dict
    shadow_radius: float
    texture_size: tuple | None = None
    scale: float = 1.0  # renderer scale: one model unit is scale / 16 block

    def bone(self, name):
        for b in self.bones:
            if b.name == name:
                return b
        raise KeyError(name)

    def children(self, name):
        return [b for b in self.bones if b.parent == name]

    def cubes(self):
        """(bone, cube) in file order."""
        return [(b, c) for b in self.bones for c in b.cubes]

    def cube(self, name):
        for b, c in self.cubes():
            if c.name == name:
                return b, c
        raise KeyError(name)

    def rest_matrices(self):
        """Bone name -> model-space matrix of the rest pose."""
        out = {}
        for b in self.bones:
            m = b.rest_matrix()
            out[b.name] = m if b.parent is None else mat_mul(out[b.parent], m)
        return out

    def problems(self):
        out, seen = [], set()
        for b in self.bones:
            if not b.name or set(b.name) - NAME_OK:
                out.append(f"bone name {b.name!r} is not [a-z0-9_]+")
            if b.name in seen:
                out.append(f"duplicate bone {b.name}")
            if b.parent is not None and b.parent not in seen:
                out.append(f"bone {b.name} listed before its parent {b.parent}")
            seen.add(b.name)
        names = set()
        for b, c in self.cubes():
            if c.name in names:
                out.append(f"duplicate cube name {c.name}")
            names.add(c.name)
            if any(abs(s - round(s)) > 1e-9 for s in c.size):
                out.append(f"cube {c.name}: size {c.size} is not whole texels")
        for b, c in self.cubes():
            if c.uv_from:
                src = self.cube(c.uv_from)[1]
                if src.size != c.size or src.uv_from:
                    out.append(f"cube {c.name}: uv_from {c.uv_from} must be an owning cube of the same size")
        return out


# ------------------------------------------------------------------------------------ vanilla cube port
def cube_polygons(cube, tex_w, tex_h):
    """ModelPart.Cube: [(direction, [(x, y, z, u, v)] * 4)] with u, v normalised to 0..1, in cube-local
    (bone) coordinates. Vertex order and the mirror reversal follow the Java exactly."""
    u0, v0 = int(cube.uv[0]), int(cube.uv[1])
    x, y, z = cube.origin
    w, h, d = cube.size
    g = cube.inflate
    f, f1, f2 = x + w, y + h, z + d
    x, y, z = x - g, y - g, z - g
    f, f1, f2 = f + g, f1 + g, f2 + g
    if cube.mirror:
        x, f = f, x
    v7 = (x, y, z)
    v0_ = (f, y, z)
    v1 = (f, f1, z)
    v2 = (x, f1, z)
    v3 = (x, y, f2)
    v4 = (f, y, f2)
    v5 = (f, f1, f2)
    v6 = (x, f1, f2)
    f4 = u0
    f5 = u0 + d
    f6 = u0 + d + w
    f7 = u0 + d + w + w
    f8 = u0 + d + w + d
    f9 = u0 + d + w + d + w
    f10 = v0
    f11 = v0 + d
    f12 = v0 + d + h
    spec = [
        ("DOWN", (v4, v3, v7, v0_), f5, f10, f6, f11),
        ("UP", (v1, v2, v6, v5), f6, f11, f7, f10),
        ("WEST", (v7, v3, v6, v2), f4, f11, f5, f12),
        ("NORTH", (v0_, v7, v2, v1), f5, f11, f6, f12),
        ("EAST", (v4, v0_, v1, v5), f6, f11, f8, f12),
        ("SOUTH", (v3, v4, v5, v6), f8, f11, f9, f12),
    ]
    out = []
    for direction, verts, a0, b0, a1, b1 in spec:
        uvs = [(a1 / tex_w, b0 / tex_h), (a0 / tex_w, b0 / tex_h), (a0 / tex_w, b1 / tex_h), (a1 / tex_w, b1 / tex_h)]
        vs = [verts[i] + uvs[i] for i in range(4)]
        if cube.mirror:
            vs.reverse()
        out.append((direction, vs))
    return out


def uv_regions(cube):
    """Direction -> (u, v, width, height) texel rectangle of the box unwrap (before any mirror)."""
    u, v = int(cube.uv[0]), int(cube.uv[1])
    w, h, d = (int(round(s)) for s in cube.size)
    return {
        "DOWN": (u + d, v, w, d), "UP": (u + d + w, v, w, d),
        "WEST": (u, v + d, d, h), "NORTH": (u + d, v + d, w, h),
        "EAST": (u + d + w, v + d, d, h), "SOUTH": (u + d + w + d, v + d, w, h),
    }


def island_size(cube):
    w, h, d = (int(round(s)) for s in cube.size)
    return 2 * (d + w), d + h


# ------------------------------------------------------------------------------------ packer
def _skyline_pack(rects, width):
    """Bottom-left skyline packing. rects: [(key, w, h)] in placement order. Returns ({key: (x, y)},
    height) or None when a rect is wider than the atlas."""
    sky = [(0, width, 0)]  # segments (x, w, y)
    placed = {}
    for key, w, h in rects:
        if w > width:
            return None
        best = None
        for i in range(len(sky)):
            x = sky[i][0]
            if x + w > width:
                break
            y, span, j = 0, 0, i
            while span < w:
                y = max(y, sky[j][2])
                span += sky[j][1]
                j += 1
            if best is None or (y + h, x) < (best[0] + h, best[1]):
                best = (y, x)
        y, x = best
        placed[key] = (x, y)
        new = []
        for sx, sw, sy in sky:
            a, b = sx, sx + sw
            if b <= x or a >= x + w:
                new.append((sx, sw, sy))
                continue
            if a < x:
                new.append((a, x - a, sy))
            if b > x + w:
                new.append((x + w, b - x - w, sy))
        new.append((x, w, y + h))
        new.sort()
        merged = []
        for seg in new:
            if merged and merged[-1][2] == seg[2] and merged[-1][0] + merged[-1][1] == seg[0]:
                merged[-1] = (merged[-1][0], merged[-1][1] + seg[1], seg[2])
            else:
                merged.append(seg)
        sky = merged
    height = max((placed[k][1] + h for k, _, h in rects), default=0)
    return placed, height


def pack(model, widths=(64, 128, 256, 512)):
    """Assign every owning cube a box-UV island; mirrored twins (uv_from) reuse their source's island.
    Picks the smallest power-of-two atlas (area, then closest to 2:1) that holds every island."""
    own = [(c.name, *island_size(c)) for _, c in model.cubes() if not c.uv_from]
    own.sort(key=lambda r: (-r[2], -r[1], r[0]))
    best = None
    for width in widths:
        res = _skyline_pack(own, width)
        if res is None:
            continue
        placed, h = res
        height = 1
        while height < h:
            height *= 2
        if height > 2 * width:
            continue
        aspect = abs(math.log2(width / height) - 1.0)  # prefer vanilla's 2:1, then square
        cand = (width * height, aspect, width, height, placed)
        if best is None or cand[:2] < best[:2]:
            best = cand
    if best is None:
        raise ValueError("islands do not fit a 512-wide atlas")
    _, _, width, height, placed = best
    for _, c in model.cubes():
        c.uv = placed[c.uv_from or c.name]
    model.texture_size = (width, height)
    return width, height


# ------------------------------------------------------------------------------------ export
def _num(v, nd=4):
    r = round(float(v), nd)
    return 0.0 if r == 0 else r


def model_json(model):
    w, h = model.texture_size
    bones = []
    for b in model.bones:
        bones.append({
            "name": b.name,
            "parent": b.parent,
            "pivot": [_num(v) for v in b.pivot],
            "rotation": [_num(v) for v in b.rotation],
            "cubes": [{"origin": [_num(v) for v in c.origin], "size": [_num(v) for v in c.size],
                       "uv": [int(c.uv[0]), int(c.uv[1])], "inflate": _num(c.inflate), "mirror": bool(c.mirror)}
                      for c in b.cubes],
        })
    doc = {"schema": 1, "id": model.id, "texture": {"width": w, "height": h}, "look": model.look,
           "shadow_radius": _num(model.shadow_radius)}
    if model.scale != 1.0:  # optional in the file; the loader reads a missing scale as 1
        doc["scale"] = _num(model.scale)
    doc["bones"] = bones
    return doc


def model_from_json(doc):
    """Back from the schema-1 JSON (used by tests and the previewer to read what was written)."""
    bones = []
    for b in doc["bones"]:
        cubes = [Cube(f"{b['name']}#{i}", c["origin"], c["size"], c.get("inflate", 0.0), c.get("mirror", False),
                      uv=tuple(c["uv"])) for i, c in enumerate(b["cubes"])]
        bones.append(Bone(b["name"], b["parent"], b["pivot"], b["rotation"], cubes))
    m = Model(doc["id"], bones, doc.get("look"), doc.get("shadow_radius", 0.5),
              (doc["texture"]["width"], doc["texture"]["height"]), doc.get("scale", 1.0))
    return m
