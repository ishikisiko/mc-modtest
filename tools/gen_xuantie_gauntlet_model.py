#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the Xuantie gauntlet (玄铁拳套) 3D item model, its textures, icon and geometry contract.

Outputs (all under src/main/resources/assets/myvillage/):

* ``models/item/xuantie_gauntlet_3d.json``  element model of an articulated iron fist with every
  display transform except ``gui``.
* ``models/item/xuantie_gauntlet.json``     ``neoforge:separate_transforms`` wrapper: ``base`` is
  the 3D model, the ``gui`` perspective keeps the 2D ``item/xuantie_gauntlet`` icon on
  ``minecraft:item/handheld``.
* ``textures/item/xuantie_gauntlet_model.png`` 128x128 texture of the 3D model: one region per
  visible face at two texels per model pixel (packed, with a bled one-texel gutter), painted texel
  by texel from each texel's place on its face: form light across every plate, bevelled and
  lightly worn edges, lame seams, a cold blue sheen on the dark 玄铁 iron, plain leather.
* ``textures/item/xuantie_gauntlet.png``    64x64 inventory icon: hand-authored pixel art on a
  32x32 grid (each logical pixel a 2x2 block), an angled three-quarter fist from the model's
  palettes, binary alpha, dark outline.
* ``combat/xuantie_gauntlet_geometry.json`` geometry contract read at runtime (model pixels).

The gauntlet is worn, not held.  Model frame: the hand runs along +Y from the wrist to the
knuckles on the axis x = 8, z = 8; the palm faces +X (the contract's flat normal), the back of
the hand -X, the thumb -Z and the little finger +Z (the edge axis).  The skin fist the gauntlet
wraps is the 8 px cube x, y, z in 4..12 (grip_center (8, 8, 8) at its centre); the model is built
at two model pixels per player-model pixel and displayed at scale 0.5 in third person, so that
cube is the player's 4x4x4 hand.  Contract reading (format 2 names) for a gauntlet:

* ``butt``    the wrist cuff, the end away from the blow;
* ``handle``  the hand inside the glove: ``grip_center`` is the centre of the fist, so the fist,
  not a handle, is what the first-person arm and the PAL grip compensation lock to;
* ``collar``  the knuckle ridge on the back of the hand (the four knuckle caps' lower part);
* ``head``    the striking face, the four finger plates and the tops of the knuckle caps:
  ``head_base``..``head_tip`` along +Y;
* ``axes.length`` the punch direction (wrist to knuckles), 12.5 px from cuff to knuckles;
* ``trail``   from the knuckle line (the collar's bottom) to the knuckle tops: fist trails are
  short and start at the knuckles.

The model is a pair's right hand: with the weapon's ``paired`` flag the game draws the same model
mirrored on an empty left hand (presentation only, client/combat), so nothing here is left-handed.

Third person: the display maps model +Y onto the arm's long axis (toward the hand), +X onto the
arm's inner side and -Z onto its front, so with PAL ``right_item`` at rest the gauntlet sits over
the fist and moves with the arm; the pose table keeps ``right_item`` at zero (it never turns in the
hand).  First person (combat): the rig's ``grip_diagonal`` 90 lays the hand along +Y, so the
first-person fist sits inside the same shell.  Outside combat the first-person and the other
display contexts show it like the old sprite's hold.

Usage: python3 tools/gen_xuantie_gauntlet_model.py [--check] [--report]
  --check   exit non-zero if a committed output differs from what would be generated
  --report  print the display derivation, the fist fit and the element count
The script is deterministic and uses only the Python standard library.
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

try:
    from tools import gen_qingfeng_sword_model as qf
except ImportError:  # run as a script: tools/ itself is on sys.path
    import gen_qingfeng_sword_model as qf

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/myvillage"
MODEL_PATH = ASSETS / "models/item/xuantie_gauntlet.json"
MODEL_3D_PATH = ASSETS / "models/item/xuantie_gauntlet_3d.json"
TEXTURE_PATH = ASSETS / "textures/item/xuantie_gauntlet_model.png"
ICON_PATH = ASSETS / "textures/item/xuantie_gauntlet.png"
GEOMETRY_PATH = ASSETS / "combat/xuantie_gauntlet_geometry.json"

MODEL_3D_ID = "myvillage:item/xuantie_gauntlet_3d"
TEXTURE_ID = "myvillage:item/xuantie_gauntlet_model"
ICON_TEXTURE_ID = "myvillage:item/xuantie_gauntlet"
TEXTURE_SIZE = 128
TEXELS_PER_UV = TEXTURE_SIZE / 16.0
ICON_SIZE = 64
ICON_GRID = 32

_r = qf._r
_mix = qf._mix
_hex = qf._hex
_hash = qf._hash
FACES = qf.FACES

# ---------------------------------------------------------------------------------------------
# Geometry (model pixels).  Axis x = 8, z = 8; +Y runs from the cuff to the knuckles.
#
# 0.39.1: an articulated iron fist instead of the 0.39.0 mitten.  The glove hugs the fist (0.1 px
# outside the skin's outer layer), the cuff hugs the forearm (0.2 px beyond the glove, rim 0.4),
# the back of the hand is three overlapping lames no thicker than 0.5 px, each finger is its own
# plate over the striking face with a curl down the palm side and a raised knuckle cap on the
# back, the thumb folds across the palm side, and the palm itself is plain leather.  Overall
# 12.5 x 11.0 x 10.25 px (length, back-to-palm, thumb-to-little-finger) against 0.39.0's
# 16.5 x 12.8 x 11.9: 24 % shorter and 14 % slimmer both ways (the bounding box 44 % smaller).
# ---------------------------------------------------------------------------------------------
CX = CZ = 8.0
GRIP_Y = 8.0
FIST = (4.0, 12.0)                 # the skin fist cube on every axis (player hand at scale 0.5)
SLEEVE = 0.5                       # the skin's outer layer, 0.25 player px
SHELL = 4.6                        # the glove's surface from the axis (> fist + sleeve = 4.5)
GLOVE_TOP = FIST[1] + SLEEVE + 0.1  # the glove's end over the knuckles of the skin fist
CUFF_END_Y = 1.0
CUFF_Y = (CUFF_END_Y, 4.4)         # butt: the wrist cuff
HAND_Y = (CUFF_Y[1], 11.4)         # handle: the hand inside the glove
KNUCKLE_Y = (HAND_Y[1], GLOVE_TOP)  # collar: the knuckle ridge on the back of the hand
FINGER_TOP = 13.2                  # the finger plates over the striking face
KNUCKLE_TOP = 13.5                 # the knuckle caps stand a little proud of the fingers
HEAD_Y = (GLOVE_TOP, KNUCKLE_TOP)  # head: fingers and knuckle caps, the striking face
TRAIL_Y = (KNUCKLE_Y[0], KNUCKLE_TOP)
THIRD_PERSON_SCALE = 0.5
# Fingers on the striking face, index (thumb side, -Z) to little finger (+Z), with 0.6 px gaps.
FINGER_Z = ((3.5, 5.3), (5.9, 7.7), (8.3, 10.1), (10.7, 12.5))
BACK_X = CX - SHELL                 # the glove's back face (x 3.4); plates stand on it
PALM_X = CX + SHELL                 # the glove's palm face (x 12.6)

# Dark 玄铁 iron with a cold blue sheen, bronze trim, blackened leather.
IRON = {"black": _hex("#0a0c10"), "deep": _hex("#11141a"), "dark": _hex("#191d24"), "base": _hex("#232932"),
        "mid": _hex("#303844"), "light": _hex("#434d5c"), "edge": _hex("#677488"), "sheen": _hex("#7c91b2"),
        "bare": _hex("#5a616b"), "glint": _hex("#a7b6cc")}
BRONZE = {"dark": _hex("#3d2c17"), "base": _hex("#6e5530"), "light": _hex("#9c7d48"), "hi": _hex("#c4a669")}
LEATHER = {"black": _hex("#0d0b0b"), "dark": _hex("#161212"), "base": _hex("#211b1a"), "mid": _hex("#2c2422"),
           "light": _hex("#3b302c"), "stitch": _hex("#5e5048")}


# ---------------------------------------------------------------------------------------------
# Parts.  Each part is a box with a material; every visible face gets its own texture region at
# TEXEL_DENSITY texels per model pixel (no stretched texels), packed onto the 128x128 sheet, and is
# painted from each texel's place on that face.  Faces that lie on or inside the glove's surface
# and face the axis are never visible and get no face.
# ---------------------------------------------------------------------------------------------
TEXEL_DENSITY = 2


class Part:
    __slots__ = ("name", "lo", "hi", "mat", "opts")

    def __init__(self, name, lo, hi, mat, **opts):
        self.name, self.lo, self.hi, self.mat, self.opts = name, tuple(lo), tuple(hi), mat, opts


def build_parts() -> list[Part]:
    s = SHELL
    parts = [
        # Cuff: the open rim, the band round the wrist, a bronze trim line toward the hand.
        Part("cuff_rim", (CX - 4.85, CUFF_END_Y, CZ - 4.85), (CX + 4.85, 1.6, CZ + 4.85), "cuff_rim"),
        Part("cuff", (CX - s, 1.6, CZ - s), (CX + s, CUFF_Y[1], CZ + s), "cuff"),
        Part("cuff_trim", (CX - 4.75, 3.7, CZ - 4.75), (CX + 4.75, 4.05, CZ + 4.75), "bronze"),
        # The glove under the plates: closes every side of the fist and its end; its palm face is plain.
        Part("glove", (CX - s, CUFF_Y[1], CZ - s), (CX + s, GLOVE_TOP, CZ + s), "glove"),
        # Back of the hand: two lames toward the wrist under the main plate, each overlapping the next.
        Part("back_lame_0", (3.1, 4.4, 3.7), (BACK_X, 6.1, 12.3), "iron", across="v", rows=False, sheen=0.2),
        Part("back_lame_1", (3.0, 5.9, 3.6), (BACK_X, 7.6, 12.4), "iron", across="v", sheen=0.3),
        Part("back_plate", (2.9, 7.4, 3.5), (BACK_X, KNUCKLE_Y[0], 12.5), "back_plate"),
        # Little-finger edge (+Z): two lames.
        Part("little_lame_0", (4.2, 4.8, PALM_X), (11.4, 7.9, 12.9), "iron", across="u", sheen=0.5),
        Part("little_lame_1", (4.0, 7.7, PALM_X), (11.6, 11.2, 13.0), "iron", across="u", sheen=0.4),
        # Thumb edge (-Z): a plate behind the thumb, the thenar plate, the thumb's knuckle at the
        # palm-side corner and the thumb folded across the palm under the index and middle fingers.
        Part("thumb_side", (4.0, 4.8, 3.05), (9.4, 11.2, CZ - s), "iron", across="u", sheen=0.45),
        Part("thenar", (9.2, 5.0, 2.85), (12.9, 9.8, CZ - s), "iron", across="u", sheen=0.3, base="mid"),
        Part("thumb_knuckle_side", (11.2, 8.0, 2.75), (13.6, 10.4, CZ - s), "knuckle"),
        Part("thumb_knuckle_palm", (PALM_X, 8.0, 2.75), (13.6, 10.4, 4.8), "knuckle"),
        Part("thumb_tip", (PALM_X, 8.3, 4.8), (13.5, 9.9, 8.6), "finger", across="v"),
    ]
    for k, (z0, z1) in enumerate(FINGER_Z):
        # The first finger segment over the striking face, its curl down the palm side, and the
        # raised knuckle cap on the back: four separate fingers with gaps between them.
        parts.append(Part(f"finger_{k}_top", (BACK_X, GLOVE_TOP, z0), (13.1, FINGER_TOP, z1), "finger", across="v"))
        parts.append(Part(f"finger_{k}_curl", (PALM_X, 9.0, z0), (13.1, GLOVE_TOP, z1), "finger", across="u",
                          rows=1))
        parts.append(Part(f"knuckle_{k}", (2.6, 11.2, z0 + 0.1), (BACK_X, KNUCKLE_TOP, z1 - 0.1), "knuckle"))
    return parts


def face_dims(part: Part, face: str) -> tuple[float, float]:
    dx, dy, dz = (part.hi[i] - part.lo[i] for i in range(3))
    if face in ("north", "south"):
        return dx, dy
    if face in ("east", "west"):
        return dz, dy
    return dx, dz


def face_normal(face: str) -> tuple[int, int, int]:
    return {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
            "up": (0, 1, 0), "down": (0, -1, 0)}[face]


def face_plane(part: Part, face: str) -> float:
    axis = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[face]
    return part.lo[axis] if face in ("north", "west", "down") else part.hi[axis]


def face_rect(part: Part, face: str) -> tuple[tuple[float, ...], tuple[float, ...]]:
    """The face's rectangle as a flat box (lo, hi)."""
    n = face_normal(face)
    axis = next(i for i in range(3) if n[i])
    plane = face_plane(part, face)
    lo = tuple(plane if i == axis else part.lo[i] for i in range(3))
    hi = tuple(plane if i == axis else part.hi[i] for i in range(3))
    return lo, hi


def _within(rect, solid: Part) -> bool:
    lo, hi = rect
    return all(solid.lo[i] - 1e-6 <= lo[i] and hi[i] <= solid.hi[i] + 1e-6 for i in range(3))


def visible_faces(part: Part) -> list[str]:
    """The faces a part shows: none whose whole rectangle lies on or inside the glove or the cuff
    band (both opaque), and not the glove's wrist end (inside the cuff)."""
    solids = [p for p in PARTS_SOLID if p is not part]
    out = []
    for face in FACES:
        if part.mat == "glove" and face == "down":
            continue
        rect = face_rect(part, face)
        if any(_within(rect, solid) for solid in solids):
            continue
        out.append(face)
    return out


def _texels(extent: float) -> int:
    return max(1, int(round(extent * TEXEL_DENSITY)))


def pack_regions(parts: list[Part]) -> dict[str, tuple[int, int, int, int]]:
    """Shelf-pack one region per visible face (tallest first, one texel apart)."""
    faces = []
    for part in parts:
        for face in visible_faces(part):
            w, h = face_dims(part, face)
            faces.append((f"{part.name}.{face}", _texels(w), _texels(h)))
    faces.sort(key=lambda f: (-f[2], -f[1], f[0]))
    regions, x, y, shelf = {}, 0, 0, 0
    for name, w, h in faces:
        if x + w > TEXTURE_SIZE:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > TEXTURE_SIZE:
            raise ValueError("the gauntlet's faces do not fit on the texture sheet")
        regions[name] = (x, y, x + w, y + h)
        x += w + 1
        shelf = max(shelf, h)
    return regions


PARTS = build_parts()
PART_BY_NAME = {p.name: p for p in PARTS}
PARTS_SOLID = [PART_BY_NAME["glove"], PART_BY_NAME["cuff"], PART_BY_NAME["cuff_rim"]]
REGIONS = pack_regions(PARTS)
# The glove's region under its plain name (tests and probes refer to it).
REGION_ALIASES = {"glove": "glove.north"}


def _clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def _shade(c, k):
    return _clamp(tuple(v * k for v in c))


def _noise(x, y, amount):
    return _hash(x, y) * amount


def _plate(i, j, w, h, x, y, base_key="base", across="v", sheen_at=0.32, wear=0.4, rows=0):
    """A curved iron plate: form light across ``across`` (brighter in the middle, darker toward the
    edges and on the far side), a lit top-left bevel and a dark bottom-right one, a little worn
    bare metal near the edges, a cold diagonal sheen and fine noise.  ``rows`` paints that many lame
    seams across the plate."""
    u = (i + 0.5) / w
    v = (j + 0.5) / h
    t = v if across == "v" else u
    curve = 1.0 - (2.0 * t - 1.0) ** 2           # 0 at the edges, 1 in the middle
    light = 0.7 + 0.45 * curve ** 0.8 + 0.14 * (1.0 - t)   # convex plate lit from its top-left edge
    c = _shade(IRON[base_key], light)
    sheen = math.exp(-((u + 0.55 * v - sheen_at - 0.25) ** 2) / 0.03)
    c = _mix(c, IRON["sheen"], 0.3 * sheen * max(curve, 0.3))
    if rows:
        length = w if across == "v" else h
        pos = (i if across == "v" else j) + 0.5
        for r in range(1, rows + 1):
            seam = length * r / (rows + 1)
            if abs(pos - seam) < 0.5:
                c = _mix(c, IRON["black"], 0.75)        # a lame seam across the plate
            elif 0.5 <= pos - seam < 1.5:
                c = _mix(c, IRON["light"], 0.35)        # the next lame's lit lip
    edge = min(i, j, w - 1 - i, h - 1 - j)
    lit_side = (i == edge or j == edge) and not (w - 1 - i == edge or h - 1 - j == edge)
    if min(w, h) >= 3 and edge == 0:
        c = _mix(c, IRON["edge"], 0.55) if lit_side else _mix(c, IRON["black"], 0.7)
        if lit_side and _hash(x * 3, y * 5) > 0.82:
            c = _mix(c, IRON["glint"], 0.5)
    if min(w, h) >= 4 and 0 < edge <= 1 and _hash(x * 11, y * 7) > 1.0 - wear * 0.5:
        c = _mix(c, IRON["bare"], 0.35)                 # worn bare iron near the edges
    if _hash(x * 5 + 1, y * 13 + 7) > 0.985:
        c = _mix(c, IRON["edge"], 0.25)                 # a scratch catching the light
    return _clamp(tuple(c[k] + _noise(x, y, 1.0) for k in range(3)))


def _edge(face, i, j, w, h, x, y, base="dark"):
    """The narrow side of a plate: lighter on top, darker underneath, brightest in the middle of
    its length (the plate's form light carried round its edge), a lit outer line on the sides."""
    k = {"up": 1.35, "down": 0.55}.get(face, 0.9)
    along = (i + 0.5) / w if w >= h else (j + 0.5) / h
    k *= 0.82 + 0.36 * (1.0 - (2.0 * along - 1.0) ** 2)
    c = _shade(IRON[base], k)
    if face not in ("up", "down") and j == 0 and h > 1:
        c = _mix(c, IRON["edge"], 0.45)
    return _clamp(tuple(c[n] + _noise(x, y, 2.5) for n in range(3)))


def _leather(i, j, w, h, x, y, stitch_rows=(), stitch_cols=()):
    u = (i + 0.5) / w
    v = (j + 0.5) / h
    light = 0.82 + 0.32 * (1.0 - (2.0 * u - 1.0) ** 2) + 0.12 * (1.0 - v)
    c = _shade(LEATHER["base"], light)
    grain = _hash(x * 17, y * 19)
    if grain > 0.82:
        c = _mix(c, LEATHER["light"], 0.3)
    elif grain < -0.82:
        c = _mix(c, LEATHER["black"], 0.35)
    if (j in stitch_rows and i % 3 != 2) or (i in stitch_cols and j % 3 != 2):
        c = _mix(c, LEATHER["stitch"], 0.6)
    if min(i, j, w - 1 - i, h - 1 - j) == 0:
        c = _mix(c, LEATHER["black"], 0.55)
    return _clamp(c)


def _thin(part: Part, face: str) -> bool:
    return min(face_dims(part, face)) < 1.0


def paint_face(part: Part, face: str, i: int, j: int, w: int, h: int, x: int, y: int):
    """RGB of texel (i, j) (from the face's top left as seen from outside) of one part's face."""
    mat, o = part.mat, part.opts
    if mat == "glove":
        if face == "east":     # the palm: plain leather, a stitched seam round it
            return _leather(i, j, w, h, x, y, stitch_rows=(1, h - 2), stitch_cols=(1, w - 2))
        return _leather(i, j, w, h, x, y)
    if mat == "bronze":
        # A thin bronze trim line, stamped every few texels.
        c = _mix(BRONZE["base"], BRONZE["light"], 0.35 + 0.4 * (1.0 - j / max(1, h - 1)))
        if face in ("north", "south", "east", "west") and i % 6 == 0:
            c = BRONZE["dark"]
        if face == "up":
            c = BRONZE["light"]
        elif face == "down":
            c = BRONZE["dark"]
        if _hash(x * 7, y * 3) > 0.7:
            c = BRONZE["hi"]
        return _clamp(tuple(c[n] + _noise(x, y, 4.0) for n in range(3)))
    if mat == "cuff_rim":
        if face == "down":     # the open end: an iron rim round the dark lining
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            d = max(abs(i - cx), abs(j - cy)) / ((w - 1) / 2.0)
            if d > 0.9:
                return _plate(i, j, w, h, x, y, across="u", base_key="mid")
            if d > 0.84:
                return IRON["black"]
            c = _shade(IRON["dark"], 0.4 + 0.5 * d)
            return _clamp(tuple(c[n] + _noise(x, y, 2.0) for n in range(3)))
        if face == "up":       # the ledge where the rim steps in to the band (only its edge shows)
            return _plate(i, j, w, h, x, y, base_key="mid", across="u", sheen_at=0.2, wear=0.9)
        c = _plate(i, j, w, h, x, y, base_key="mid", across="v", sheen_at=0.1, wear=0.8)
        return c
    if mat == "cuff":
        if face == "up":       # the ring round the glove at the wrist
            return _edge("up", i, j, w, h, x, y, base="base")
        c = _plate(i, j, w, h, x, y, across="u", sheen_at=0.15, wear=0.45)
        if j == 1 and 2 <= i <= w - 3 and i % 5 == 2:
            c = _mix(BRONZE["base"], c, 0.3)   # small rivet heads near the rim
        return c
    if mat == "back_plate":
        if _thin(part, face):
            return _edge(face, i, j, w, h, x, y)
        c = _plate(i, j, w, h, x, y, across="u", sheen_at=0.35, wear=0.4)
        # A low ridge down the middle of the plate: a lit crest with a shadowed far side.
        mid = (w - 1) / 2.0
        if 0 < j < h - 1:
            if abs(i - (mid - 0.5)) < 0.5:
                c = _mix(c, IRON["edge"], 0.45)
            elif abs(i - (mid + 0.5)) < 0.5:
                c = _mix(c, IRON["deep"], 0.45)
        return c
    if mat == "iron":
        if _thin(part, face):
            return _edge(face, i, j, w, h, x, y)
        return _plate(i, j, w, h, x, y, base_key=o.get("base", "base"), across=o.get("across", "v"),
                      sheen_at=o.get("sheen", 0.3), wear=0.5)
    if mat == "finger":
        if _thin(part, face):
            return _edge(face, i, j, w, h, x, y, base="base")
        # Two lame seams across each finger plate: a finger reads as jointed, not as a block.
        c = _plate(i, j, w, h, x, y, base_key="base", across=o.get("across", "v"), sheen_at=0.25, wear=0.5,
                   rows=2 if face == "up" else o.get("rows", 0) if face == "east" else 0)
        return c
    if mat == "knuckle":
        # Polished by use: brighter iron, worn bare at the striking corner.
        if _thin(part, face):
            return _edge(face, i, j, w, h, x, y, base="mid")
        c = _plate(i, j, w, h, x, y, base_key="mid", across="u" if face == "west" else "v", sheen_at=0.05,
                   wear=0.9)
        if face == "up":
            c = _mix(c, IRON["edge"], 0.3)
        return c
    raise KeyError(mat)


def render_texture() -> list[list[tuple[int, int, int, int]]]:
    pixels = [[(0, 0, 0, 0)] * TEXTURE_SIZE for _ in range(TEXTURE_SIZE)]
    occupied: dict[tuple[int, int], str] = {}
    for name, (x0, y0, x1, y1) in REGIONS.items():
        part_name, face = name.split(".")
        part = PART_BY_NAME[part_name]
        w, h = x1 - x0, y1 - y0
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x, y) in occupied:
                    raise ValueError(f"texture regions {occupied[(x, y)]} and {name} overlap at {x},{y}")
                occupied[(x, y)] = name
                pixels[y][x] = (*paint_face(part, face, x - x0, y - y0, w, h, x, y), 255)
    # Bleed every region's edge into the one-texel gutter round it, so a face sampled exactly on its
    # UV edge (thin faces at a grazing angle) never picks up a transparent texel.
    bled = [row[:] for row in pixels]
    for y in range(TEXTURE_SIZE):
        for x in range(TEXTURE_SIZE):
            if pixels[y][x][3]:
                continue
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < TEXTURE_SIZE and 0 <= ny < TEXTURE_SIZE and pixels[ny][nx][3]:
                    bled[y][x] = pixels[ny][nx]
                    break
    return bled


# ---------------------------------------------------------------------------------------------
# Elements
# ---------------------------------------------------------------------------------------------

def uv(region: str, flip_u: bool = False, flip_v: bool = False):
    x0, y0, x1, y1 = REGIONS[REGION_ALIASES.get(region, region)]
    u0, v0, u1, v1 = (_r(c / TEXELS_PER_UV) for c in (x0, y0, x1, y1))
    if flip_u:
        u0, u1 = u1, u0
    if flip_v:
        v0, v1 = v1, v0
    return [u0, v0, u1, v1]


def box(name: str, lo, hi, faces: dict) -> dict:
    element = {"name": name, "from": [_r(c) for c in lo], "to": [_r(c) for c in hi]}
    element["faces"] = {face: {"uv": faces[face], "texture": "#gauntlet"} for face in FACES if face in faces}
    return element


def build_elements() -> list[dict]:
    return [box(p.name, p.lo, p.hi, {face: uv(f"{p.name}.{face}") for face in visible_faces(p)}) for p in PARTS]


# ---------------------------------------------------------------------------------------------
# Display transforms
# ---------------------------------------------------------------------------------------------
GRIP_CENTER = (CX, GRIP_Y, CZ)
MODEL_CENTER = (CX, (CUFF_END_Y + KNUCKLE_TOP) / 2.0, CZ)
# Third person, in ItemInHandLayer's item frame (see qf.FIST_CENTER_ITEM: item +X = arm -X,
# item +Y = arm -Z (forward), item +Z = arm -Y (toward the shoulder)): model +X (palm) onto the
# arm's inner side (item -X), model +Y (wrist to knuckles) down the arm toward the hand (item -Z),
# model -Z (thumb) onto the arm's front (item +Y), i.e. model +Z onto item -Y.
WORN_AXES = ((-1.0, 0.0, 0.0), (0.0, 0.0, -1.0), (0.0, -1.0, 0.0))   # images of model X, Y, Z
FIRST_PERSON_SCALE = 0.7
GROUND_SCALE = 0.5
FIXED_SCALE = 0.75
HEAD_SCALE = 0.6


def worn_rotation():
    """Euler XYZ (degrees) of the rotation whose columns are the images of model X, Y and Z."""
    m = [[WORN_AXES[c][r] for c in range(3)] for r in range(3)]
    return tuple(round(a, 2) + 0.0 for a in qf.to_euler_xyz(m))


def placed(rotation, scale: float, anchor, target) -> dict:
    offset = qf.mv(qf.euler_xyz(rotation), tuple(scale * (anchor[i] - 8.0) for i in range(3)))
    translation = tuple(round(target[i] - offset[i], 3) + 0.0 for i in range(3))
    if any(abs(t) > 80.0 for t in translation):
        raise ValueError(f"translation {translation} exceeds the 5-block clamp")
    return {"rotation": tuple(rotation), "translation": translation, "scale": (scale, scale, scale)}


def build_display() -> dict:
    third = placed(worn_rotation(), THIRD_PERSON_SCALE, GRIP_CENTER, qf.FIST_CENTER_ITEM)
    # Outside combat the first-person hold shows the fist knuckles-forward where the vanilla
    # sprite's handle sat: the punch axis takes the old blade direction, the palm its normal.
    first = qf.derive("firstperson_righthand", GRIP_CENTER, anchor_old=qf.OLD_HANDLE_CENTER, scale=FIRST_PERSON_SCALE)
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": dict(third),   # the game mirrors a left-hand entry itself (see the spear generator)
        "firstperson_righthand": first,
        "firstperson_lefthand": dict(first),
        "ground": qf.derive("ground", MODEL_CENTER, anchor_old=qf.OLD_SPRITE_CENTER, scale=GROUND_SCALE),
        "head": qf.derive("head", MODEL_CENTER, anchor_old=qf.OLD_SPRITE_CENTER, scale=HEAD_SCALE),
        "fixed": qf.derive("fixed", MODEL_CENTER, anchor_old=qf.OLD_SPRITE_CENTER, scale=FIXED_SCALE),
    }


# ---------------------------------------------------------------------------------------------
# Inventory icon: pixel art on a 32x32 logical grid (each logical pixel written as a 2x2 block of
# the 64x64 file the item validator requires), so it stays crisp in a 32-px slot at GUI scale 2.
#
# 0.39.1: an angled (斜着) three-quarter fist, hand-authored.  The design is the fist seen from the
# striking side: the four finger plates leaning out of the hand with their lit knuckle edges, the
# curled finger ends, the thumb up the far side and folded across the curls, the dark leather palm,
# and the cuff with its bronze trim and lit rim; the whole fist is turned 26.57 degrees (a 2:1
# pixel slope, so every edge is a regular staircase) with the knuckles up and to the right, lit
# from the upper left.  It was first laid out as flat upright shapes rotated onto the grid, then
# finished by hand here; every colour comes from the model's palettes (ICON_LEGEND).
# ---------------------------------------------------------------------------------------------
OUTLINE = _hex("#07080b")
ICON_LEGEND = {
    "O": OUTLINE,
    "k": IRON["black"], "d": IRON["deep"], "D": IRON["dark"], "b": IRON["base"], "m": IRON["mid"],
    "l": IRON["light"], "e": IRON["edge"], "s": IRON["sheen"], "g": IRON["glint"],
    "z": BRONZE["dark"], "y": BRONZE["base"], "Y": BRONZE["light"], "H": BRONZE["hi"],
    "n": LEATHER["black"], "r": LEATHER["base"],
}
ICON_ROWS = (
    "................................",
    "................................",
    "................................",
    "...........OOO..................",
    "..........OgggOOO...............",
    "..........OeelkggOOO............",
    ".........OellmkeglOgOO..........",
    ".........OellkelemkgggOOO.......",
    "........OellmkellkeeelkglO......",
    "........OellkellmkellkeeelO.....",
    ".......OellmkellkellmkelmkO.....",
    ".......OellkellmkellkellkkO.....",
    "......ODdemkellkellmkelmkO......",
    "......OmmdkellmkellkellkkmO.....",
    ".....OmbbDkDdmkellmkelmkmmmO....",
    ".....OrmDklmDdkellkellkkmbDO....",
    "....OrrrremelkDDdmkelmkmbbO.....",
    "....OnnrrrremelmDkdemkkmbDO.....",
    ".....OnnnrrrremelkDDdkmbbO......",
    ".....OYynnnrrrremelDkkmbDO......",
    "....OmmmYynnnrrrremelmbbO.......",
    "....OmbbmmYynnnrrrremelDO.......",
    "...OlmbbbbmmYynnnrrrremO........",
    "..OlmllmbbbbmmYynnnrrrnO........",
    "...OOlmllmbbbbmmYynnnnO.........",
    ".....OOlmllmbbbbmmYynnO.........",
    ".......OOlmllmbbbbmOOO..........",
    ".........OOlmllmbbDO............",
    "...........OOlmllDO.............",
    ".............OOlmlO.............",
    "...............OOOO.............",
    "................................",
)


def icon_grid() -> dict[tuple[int, int], tuple[int, int, int]]:
    """Logical 32x32 icon as {(x, y): rgb} ('.' is transparent)."""
    if len(ICON_ROWS) != ICON_GRID or any(len(row) != ICON_GRID for row in ICON_ROWS):
        raise ValueError(f"the icon grid must be {ICON_GRID}x{ICON_GRID}")
    return {(x, y): ICON_LEGEND[ch] for y, row in enumerate(ICON_ROWS) for x, ch in enumerate(row) if ch != "."}


def render_icon() -> list[list[tuple[int, int, int, int]]]:
    grid = icon_grid()
    k = ICON_SIZE // ICON_GRID
    return [[(*grid[(x // k, y // k)], 255) if (x // k, y // k) in grid else (0, 0, 0, 0)
             for x in range(ICON_SIZE)] for y in range(ICON_SIZE)]


# ---------------------------------------------------------------------------------------------
# Documents
# ---------------------------------------------------------------------------------------------

def _json_display(display: dict) -> dict:
    return {k: {"rotation": [_r(c) for c in v["rotation"]], "translation": [_r(c) for c in v["translation"]],
                "scale": [_r(c) for c in v["scale"]]} for k, v in display.items()}


def build_model_3d() -> dict:
    return {
        "credit": "MyVillage original procedural gauntlet (tools/gen_xuantie_gauntlet_model.py)",
        "texture_size": [TEXTURE_SIZE, TEXTURE_SIZE],
        "textures": {"gauntlet": TEXTURE_ID, "particle": ICON_TEXTURE_ID},
        "elements": build_elements(),
        "display": _json_display(build_display()),
    }


def build_model() -> dict:
    icon = {"parent": "minecraft:item/handheld", "textures": {"layer0": ICON_TEXTURE_ID}}
    return {
        "loader": "neoforge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": MODEL_3D_ID},
        "perspectives": {"gui": icon},
        "textures": {"particle": ICON_TEXTURE_ID},
    }


def _extent(names, axis: int, centre: float) -> float:
    els = [e for e in build_elements() if any(e["name"].startswith(n) for n in names)]
    return _r(max(max(abs(e["from"][axis] - centre), abs(e["to"][axis] - centre)) for e in els))


def geometry_contract() -> dict:
    return {
        "format": 2,
        "units": "model_pixels",
        "generator": "tools/gen_xuantie_gauntlet_model.py",
        "model": MODEL_3D_ID,
        "axes": {"length": "+y", "flat_normal": "x", "edge": "z", "center_x": CX, "center_z": CZ},
        "grip_center": [CX, GRIP_Y, CZ],
        "handle": {"y": list(HAND_Y), "half_width": _extent(("glove",), 2, CZ),
                   "half_thickness": _extent(("glove",), 0, CX)},
        "collar": {"y": list(KNUCKLE_Y), "half_width": _extent(("knuckle_",), 2, CZ),
                   "half_thickness": _extent(("knuckle_",), 0, CX)},
        "butt": {"y": list(CUFF_Y), "half_width": _extent(("cuff_rim",), 2, CZ),
                 "half_thickness": _extent(("cuff_rim",), 0, CX)},
        "head": {"half_width": _extent(("finger_",), 2, CZ), "half_thickness": _extent(("knuckle_", "finger_"), 0, CX),
                 "ridge_half_thickness": _extent(("knuckle_",), 0, CX), "taper_start_y": FINGER_TOP},
        "head_base": [CX, HEAD_Y[0], CZ],
        "head_tip": [CX, HEAD_Y[1], CZ],
        "trail": {"base": [CX, TRAIL_Y[0], CZ], "tip": [CX, TRAIL_Y[1], CZ]},
        "edge_axis": [0.0, 0.0, 1.0],
        "flat_axis": [1.0, 0.0, 0.0],
        "overall_y": [CUFF_END_Y, KNUCKLE_TOP],
    }


def outputs() -> dict[Path, bytes]:
    return {
        MODEL_3D_PATH: qf.render_model_json(build_model_3d()).encode(),
        MODEL_PATH: qf.render_model_json(build_model()).encode(),
        GEOMETRY_PATH: qf.render_model_json(geometry_contract()).encode(),
        TEXTURE_PATH: qf.encode_png(render_texture()),
        ICON_PATH: qf.encode_png(render_icon()),
    }


# ---------------------------------------------------------------------------------------------
# Self-checks
# ---------------------------------------------------------------------------------------------

def check_elements(elements: list[dict]) -> list[str]:
    errors = qf.check_elements(elements)
    names = set()
    for e in elements:
        if e["name"] in names:
            errors.append(f"{e['name']}: duplicate element name")
        names.add(e["name"])
        if "rotation" in e:
            errors.append(f"{e['name']}: element rotation (the icon rasteriser handles none)")
        for face, data in e["faces"].items():
            if data["texture"] != "#gauntlet":
                errors.append(f"{e['name']}.{face}: texture {data['texture']}")
        if any(hi - lo < 0.1 for lo, hi in zip(e["from"], e["to"])):
            errors.append(f"{e['name']}: thinner than 0.1 px")
    return errors


def fist_clearance_errors(elements: list[dict]) -> list[str]:
    """No side face lies inside the arm (the skin box with its outer layer, from the fist's end up
    the forearm): the gauntlet wraps the hand instead of cutting into it.  Up and down faces that
    span the arm are caps inside it and are hidden."""
    lo, hi, end = FIST[0] - SLEEVE, FIST[1] + SLEEVE, FIST[1] + SLEEVE
    errors = []
    for e in elements:
        for face in e["faces"]:
            axis = {"west": 0, "east": 0, "north": 2, "south": 2}.get(face)
            if axis is None:
                continue
            plane = e["from"][axis] if face in ("west", "north") else e["to"][axis]
            other = 2 - axis
            if (lo + 1e-6 < plane < hi - 1e-6 and e["from"][other] < hi - 1e-6 and e["to"][other] > lo + 1e-6
                    and e["from"][1] < end - 1e-6):
                errors.append(f"{e['name']}.{face} lies inside the arm")
    return errors


def fist_coverage_errors(elements: list[dict]) -> list[str]:
    """Every side of the fist, and the forearm under the cuff, is covered by some element face."""
    errors = []
    probes = []
    for y in (4.5, 6.0, 8.0, 10.0, 11.5):
        for t in (4.5, 8.0, 11.5):
            probes += [((FIST[0] - SLEEVE - 0.01, y, t), 0, -1), ((FIST[1] + SLEEVE + 0.01, y, t), 0, 1),
                       ((t, y, FIST[0] - SLEEVE - 0.01), 2, -1), ((t, y, FIST[1] + SLEEVE + 0.01), 2, 1)]
    for a in (4.5, 8.0, 11.5):
        for b in (4.5, 8.0, 11.5):
            probes.append(((a, FIST[1] + SLEEVE + 0.01, b), 1, 1))
    for point, axis, sign in probes:
        covered = False
        for e in elements:
            inside = all(e["from"][k] - 1e-6 <= point[k] <= e["to"][k] + 1e-6 for k in range(3) if k != axis)
            beyond = e["to"][axis] >= point[axis] if sign > 0 else e["from"][axis] <= point[axis]
            if inside and beyond:
                covered = True
                break
        if not covered:
            errors.append(f"fist side uncovered at {tuple(round(c, 2) for c in point)}")
    return errors


def _span(axis: int) -> float:
    els = build_elements()
    return max(e["to"][axis] for e in els) - min(e["from"][axis] for e in els)


def fist_fit() -> dict:
    """Third-person fit in the neutral item frame (PAL right_item = 0)."""
    t = build_display()["thirdperson_righthand"]
    grip = qf.display_point(t, GRIP_CENTER)
    along = qf.normalize(qf.display_vector(t, (0.0, 1.0, 0.0)))
    palm = qf.display_vector(t, (1.0, 0.0, 0.0))
    thumb = qf.display_vector(t, (0.0, 0.0, -1.0))
    knuckles = qf.display_point(t, (CX, KNUCKLE_TOP, CZ))
    cuff = qf.display_point(t, (CX, CUFF_END_Y, CZ))
    return {
        "grip": grip, "grip_error": math.dist(grip, qf.FIST_CENTER_ITEM),
        "punch_axis_item": along, "palm_item": palm, "thumb_item": thumb,
        "knuckles_item": knuckles, "cuff_item": cuff,
        # item frame z = 0 is the end of the arm, z = 4 the back of the 4 px fist (toward the shoulder)
        "studs_past_hand_px": -knuckles[2], "cuff_up_arm_px": cuff[2],
        "width_player_px": _span(0) * THIRD_PERSON_SCALE,
        "depth_player_px": _span(2) * THIRD_PERSON_SCALE,
        "length_player_px": (KNUCKLE_TOP - CUFF_END_Y) * THIRD_PERSON_SCALE,
    }


def self_check() -> list[str]:
    elements = build_elements()
    errors = check_elements(elements)
    errors += fist_clearance_errors(elements)
    errors += fist_coverage_errors(elements)
    fit = fist_fit()
    if fit["grip_error"] > 0.01:
        errors.append(f"third-person grip {fit['grip_error']:.3f} px from the fist centre")
    if math.dist(fit["punch_axis_item"], (0.0, 0.0, -1.0)) > 1e-6:
        errors.append(f"third-person punch axis {fit['punch_axis_item']} does not run down the arm (item -Z)")
    if math.dist(fit["palm_item"], (-1.0, 0.0, 0.0)) > 1e-6:
        errors.append("third-person palm does not face the arm's inner side (item -X)")
    if math.dist(fit["thumb_item"], (0.0, 1.0, 0.0)) > 1e-6:
        errors.append("third-person thumb does not face forward (item +Y)")
    if not 0.3 <= fit["studs_past_hand_px"] <= 1.5:
        errors.append(f"knuckle studs {fit['studs_past_hand_px']:.2f} px past the hand (0.3..1.5)")
    geo = geometry_contract()
    if not (geo["butt"]["y"][1] <= geo["handle"]["y"][0] and geo["handle"]["y"][1] <= geo["collar"]["y"][0]
            and geo["collar"]["y"][1] <= geo["head_base"][1] < geo["head_tip"][1]):
        errors.append("geometry contract parts are out of order")
    if not geo["handle"]["y"][0] < geo["grip_center"][1] < geo["handle"]["y"][1]:
        errors.append("grip_center outside the handle")
    if any(abs((FIST[0] + FIST[1]) / 2.0 - c) > 1e-9 for c in geo["grip_center"]):
        errors.append("grip_center is not the centre of the fist")
    trail = geo["trail"]
    if any((p[0], p[2]) != (CX, CZ) for p in (trail["base"], trail["tip"])):
        errors.append("trail span is off the weapon axis")
    if not (KNUCKLE_Y[0] <= trail["base"][1] < trail["tip"][1] <= geo["head_tip"][1]):
        errors.append("trail must run from the knuckle line to the stud tips")
    ys = [c for e in elements for c in (e["from"][1], e["to"][1])]
    if abs(min(ys) - CUFF_END_Y) > 1e-6 or abs(max(ys) - KNUCKLE_TOP) > 1e-6:
        errors.append(f"model spans y {min(ys):.3f}..{max(ys):.3f}, expected {CUFF_END_Y}..{KNUCKLE_TOP}")
    return errors


def report() -> None:
    elements = build_elements()
    print(f"elements {len(elements)}, texture {TEXTURE_SIZE}x{TEXTURE_SIZE}, icon {ICON_SIZE}x{ICON_SIZE}")
    for name, t in build_display().items():
        print(f"{name:22s} rot {t['rotation']} tr {t['translation']} scale {t['scale'][0]}")
    for key, value in fist_fit().items():
        if isinstance(value, tuple):
            value = "(" + ", ".join(f"{v:+.3f}" for v in value) + ")"
        elif isinstance(value, float):
            value = f"{value:.3f}"
        print(f"  {key:22s} {value}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify committed outputs and self-checks")
    parser.add_argument("--report", action="store_true", help="print derivation numbers")
    args = parser.parse_args(argv)
    if args.report:
        report()
        return 0
    errors = self_check()
    generated = outputs()
    if args.check:
        for path, data in generated.items():
            rel = path.relative_to(ROOT)
            if not path.exists():
                errors.append(f"{rel} is missing")
                continue
            current = path.read_bytes()
            if path.suffix == ".png":
                try:
                    same = qf.decode_png_rgba(current) == qf.decode_png_rgba(data)
                except ValueError as exc:
                    errors.append(f"{rel}: {exc}")
                    continue
            else:
                same = current == data
            if not same:
                errors.append(f"{rel} differs from the generator; run python3 tools/gen_xuantie_gauntlet_model.py")
        if errors:
            for e in errors:
                print("ERROR:", e, file=sys.stderr)
            return 1
        print("OK Xuantie gauntlet model, textures and geometry contract match the generator and pass all self-checks")
        return 0
    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        return 1
    for path, data in generated.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print(f"wrote {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
