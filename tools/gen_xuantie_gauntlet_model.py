#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the Xuantie gauntlet (玄铁拳套) 3D item model, its textures, icon and geometry contract.

Outputs (all under src/main/resources/assets/myvillage/):

* ``models/item/xuantie_gauntlet_3d.json``  element model of a plated gauntlet with every display
  transform except ``gui``.
* ``models/item/xuantie_gauntlet.json``     ``neoforge:separate_transforms`` wrapper: ``base`` is
  the 3D model, the ``gui`` perspective keeps the 2D ``item/xuantie_gauntlet`` icon on
  ``minecraft:item/handheld``.
* ``textures/item/xuantie_gauntlet_model.png`` 128x128 texture of the 3D model, painted texel by
  texel from each texel's place on its face: form light across every plate, bevelled and worn
  edges, a cold blue sheen on the dark 玄铁 iron, stitched leather.
* ``textures/item/xuantie_gauntlet.png``    64x64 inventory icon: pixel art on a 32x32 grid (each
  logical pixel a 2x2 block) from the model's palettes, binary alpha, dark outline.
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
* ``collar``  the knuckle bar over the row of knuckles;
* ``head``    the four knuckle studs, the striking face: ``head_base``..``head_tip`` along +Y;
* ``axes.length`` the punch direction (wrist to knuckles), only 17 px from cuff to studs;
* ``trail``   from the knuckle line (the collar's bottom) to the stud tips: fist trails are short
  and start at the knuckles.

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
# Geometry (model pixels).  Axis x = 8, z = 8; +Y runs from the cuff to the knuckle studs.
# ---------------------------------------------------------------------------------------------
CX = CZ = 8.0
GRIP_Y = 8.0
FIST = (4.0, 12.0)                 # the skin fist cube on every axis (player hand at scale 0.5)
SLEEVE = 0.5                       # the skin's outer layer, 0.25 player px
SHELL = 4.75                       # inner surface of the glove from the axis (> fist + sleeve)
CUFF_END_Y = -1.6
CUFF_Y = (CUFF_END_Y, 4.0)         # butt: the wrist cuff
HAND_Y = (CUFF_Y[1], 11.0)         # handle: the hand inside the glove
KNUCKLE_Y = (HAND_Y[1], 13.3)      # collar: the knuckle bar and its cap
STUD_Y = (KNUCKLE_Y[1], 14.3)      # head: the knuckle studs
STUD_CAP_TOP = 14.9
TRAIL_Y = (KNUCKLE_Y[0], STUD_CAP_TOP)
THIRD_PERSON_SCALE = 0.5
# Fingers on the striking face, thumb (-Z) to little finger (+Z).
FINGER_Z = ((3.3, 5.45), (5.7, 7.85), (8.1, 10.25), (10.5, 12.65))

# ---------------------------------------------------------------------------------------------
# Texture layout: name -> (x0, y0, x1, y1) texel rectangle on the 128x128 sheet.  About two
# texels per model pixel; each face maps one whole region, so a region's border is the face's
# edge (bevel and wear are painted there).
# ---------------------------------------------------------------------------------------------
REGIONS = {
    # cuff
    "cuff_side": (0, 0, 24, 14),
    "cuff_flare": (24, 0, 48, 4),
    "cuff_band": (24, 4, 48, 8),
    "cuff_end": (48, 0, 72, 24),
    "cuff_top": (72, 0, 96, 24),
    "strap": (24, 8, 48, 11),
    # hand
    "back_lame": (0, 16, 22, 22),
    "back_plate": (0, 24, 22, 32),
    "ridge": (24, 12, 32, 22),
    "plate_edge": (32, 12, 36, 22),
    "side_plate": (0, 34, 16, 48),
    "palm": (16, 34, 36, 48),
    "glove": (36, 12, 46, 22),
    "thumb": (36, 24, 48, 30),
    "thumb_end": (48, 24, 54, 30),
    # knuckles and fingers
    "knuckle_bar": (0, 50, 24, 56),
    "knuckle_front": (0, 58, 24, 68),
    "finger_front": (24, 50, 30, 62),
    "finger_side": (30, 50, 34, 62),
    "finger_curl": (34, 50, 40, 58),
    "stud_side": (40, 50, 46, 54),
    "stud_face": (46, 50, 52, 56),
    "rivet": (52, 50, 54, 52),
}

# Dark 玄铁 iron with a cold blue sheen, bronze rivets and trim, blackened leather.
IRON = {"black": _hex("#0b0d11"), "deep": _hex("#12151b"), "dark": _hex("#1a1e25"), "base": _hex("#242a33"),
        "mid": _hex("#323a46"), "light": _hex("#46505f"), "edge": _hex("#6b778a"), "sheen": _hex("#7f93b3"),
        "bare": _hex("#5d636c"), "glint": _hex("#a9b7cc")}
BRONZE = {"dark": _hex("#3d2c17"), "base": _hex("#6e5530"), "light": _hex("#9c7d48"), "hi": _hex("#c4a669")}
LEATHER = {"black": _hex("#120d0b"), "dark": _hex("#1d1512"), "base": _hex("#2b201a"), "mid": _hex("#3a2b22"),
           "light": _hex("#4d3a2d"), "stitch": _hex("#7d6650")}


def _clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def _shade(c, k):
    return _clamp(tuple(v * k for v in c))


def _noise(x, y, amount):
    return _hash(x, y) * amount


def _plate(i, j, w, h, x, y, base_key="base", across="v", sheen_at=0.32, wear=0.55, rows=False):
    """A curved iron plate: form light across ``across`` (brighter in the middle, darker toward the
    edges and on the far side), a lit top-left bevel and a dark bottom-right one, worn bare metal
    near the edges, a cold diagonal sheen and fine noise.  ``rows`` paints overlapping lames."""
    u = (i + 0.5) / w
    v = (j + 0.5) / h
    t = v if across == "v" else u
    curve = 1.0 - (2.0 * t - 1.0) ** 2           # 0 at the edges, 1 in the middle
    light = 0.62 + 0.55 * curve ** 0.7 + 0.18 * (1.0 - t)   # convex plate lit from its top edge
    if rows:
        band = (v * 3.0) % 1.0
        light *= 0.78 + 0.32 * band ** 0.6         # each lame darkens where it tucks under the next
    c = _shade(IRON[base_key], light)
    sheen = math.exp(-((u + 0.55 * v - sheen_at - 0.25) ** 2) / 0.02)
    c = _mix(c, IRON["sheen"], 0.42 * sheen * curve)
    edge = min(i, j, w - 1 - i, h - 1 - j)
    lit_side = (i == edge or j == edge) and not (w - 1 - i == edge or h - 1 - j == edge)
    if edge == 0:
        c = _mix(IRON["edge"], IRON["light"], 0.5) if lit_side else IRON["black"]
        if lit_side and _hash(x * 3, y * 5) > 0.6:
            c = IRON["glint"]
    elif edge == 1:
        c = _mix(c, IRON["light"] if lit_side else IRON["deep"], 0.35)
    if 0 < edge <= 2 and _hash(x * 11, y * 7) > 1.0 - wear * 0.6:
        c = _mix(c, IRON["bare"], 0.45)             # chipped, bare iron near the edges
    if _hash(x * 5 + 1, y * 13 + 7) > 0.975:
        c = _mix(c, IRON["edge"], 0.3)              # a scratch catching the light
    return _clamp(tuple(c[k] + _noise(x, y, 2.0) for k in range(3)))


def _leather(i, j, w, h, x, y, stitch_rows=(), stitch_cols=()):
    u = (i + 0.5) / w
    v = (j + 0.5) / h
    light = 0.8 + 0.35 * (1.0 - (2.0 * u - 1.0) ** 2) + 0.12 * (1.0 - v)
    c = _shade(LEATHER["base"], light)
    grain = _hash(x * 17, y * 19)
    if grain > 0.82:
        c = _mix(c, LEATHER["light"], 0.3)
    elif grain < -0.82:
        c = _mix(c, LEATHER["black"], 0.35)
    if (j in stitch_rows and i % 3 != 2) or (i in stitch_cols and j % 3 != 2):
        c = _mix(c, LEATHER["stitch"], 0.65)
    if min(i, j, w - 1 - i, h - 1 - j) == 0:
        c = _mix(c, LEATHER["black"], 0.6)
    return _clamp(c)


def paint_region(name: str, i: int, j: int, w: int, h: int, x: int, y: int):
    """RGBA of texel (i, j) inside region ``name`` of size w x h (sheet position x, y)."""
    if name == "cuff_side":
        c = _plate(i, j, w, h, x, y, across="u", sheen_at=0.15)
        if j in (h - 3, h - 2):                   # rolled rim toward the hand
            c = _mix(c, IRON["light"], 0.4) if j == h - 3 else _mix(c, IRON["deep"], 0.5)
        if j == 4 and 3 <= i <= w - 4 and i % 5 == 1:
            c = BRONZE["light"]                   # rivet heads along the band below
        return (*c, 255)
    if name == "cuff_flare":
        c = _plate(i, j, w, h, x, y, across="v", sheen_at=0.1)
        return (*c, 255)
    if name == "cuff_band":
        # Bronze band with a 回 key pattern stamped into it.
        k = i % 6
        pattern = j in (0, h - 1) or (k in (0, 4) and j in (1, 2)) or (k in (1, 2, 3) and j == 1 + (k == 2))
        c = BRONZE["dark"] if pattern else _mix(BRONZE["base"], BRONZE["light"], 0.5 + 0.5 * ((i // 6) % 2) * 0.4)
        if j == 0:
            c = BRONZE["hi"] if _hash(x, y) > 0.0 else BRONZE["light"]
        return (*_clamp(tuple(c[k2] + _noise(x, y, 5.0) for k2 in range(3))), 255)
    if name == "cuff_end":
        # The open end of the cuff: an iron rim around the dark lining.
        cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
        d = max(abs(i - cx), abs(j - cy)) / ((w - 1) / 2.0)
        if d > 0.86:
            c = _plate(i, j, w, h, x, y, across="u")
        elif d > 0.8:
            c = IRON["deep"]
        else:
            c = _shade(IRON["dark"], 0.45 + 0.55 * d)   # the dark iron inside of the cuff
            c = _clamp(tuple(c[k] + _noise(x, y, 2.0) for k in range(3)))
        return (*c, 255)
    if name == "cuff_top":
        cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
        d = max(abs(i - cx), abs(j - cy)) / ((w - 1) / 2.0)
        c = _shade(IRON["mid"], 1.0 - 0.3 * d) if d > 0.8 else IRON["dark"]
        return (*_clamp(tuple(c[k] + _noise(x, y, 3.0) for k in range(3))), 255)
    if name == "strap":
        c = _leather(i, j, w, h, x, y, stitch_rows=(1,))
        return (*c, 255)
    if name == "back_lame":
        return (*_plate(i, j, w, h, x, y, across="v", sheen_at=0.25, rows=True), 255)
    if name == "back_plate":
        c = _plate(i, j, w, h, x, y, across="v", sheen_at=0.4, wear=0.4)
        # Engraved cloud scroll (云纹): a dark groove with a lit lower lip, mirrored about the ridge.
        u = (i + 0.5) / w
        v = (j + 0.5) / h
        du = abs(u - 0.5)
        swirl = abs(math.hypot(du - 0.28, v - 0.5) - 0.17)
        if 0.12 < du < 0.46 and swirl < 0.035:
            c = _mix(c, IRON["black"], 0.7)
        elif 0.12 < du < 0.46 and 0.035 <= swirl < 0.07 and v > 0.5:
            c = _mix(c, IRON["edge"], 0.35)
        return (*c, 255)
    if name == "ridge":
        c = _plate(i, j, w, h, x, y, base_key="mid", across="u", sheen_at=0.2, wear=0.7)
        return (*c, 255)
    if name == "plate_edge":
        c = _shade(IRON["dark"], 0.9 + 0.2 * ((j % 3) == 0))
        if i == 0:
            c = IRON["edge"]
        return (*_clamp(tuple(c[k] + _noise(x, y, 3.0) for k in range(3))), 255)
    if name == "side_plate":
        # Three finger-edge lames: each lit along its top, a dark seam where it tucks under the next.
        c = _plate(i, j, w, h, x, y, base_key="base", across="u", sheen_at=0.5)
        k = (j * 3) // h
        local = j - (k * h) // 3
        if local in (0, 1) and k > 0:
            c = IRON["black"] if local == 0 else _mix(c, IRON["black"], 0.5)
        elif local == 2 and k > 0:
            c = _mix(c, IRON["edge"], 0.45)
        return (*c, 255)
    if name == "palm":
        return (*_leather(i, j, w, h, x, y, stitch_rows=(2, h - 3), stitch_cols=(2, w - 3)), 255)
    if name == "glove":
        return (*_leather(i, j, w, h, x, y), 255)
    if name == "thumb":
        return (*_plate(i, j, w, h, x, y, across="v", sheen_at=0.3, rows=True), 255)
    if name == "thumb_end":
        return (*_plate(i, j, w, h, x, y, base_key="mid", across="u"), 255)
    if name == "knuckle_bar":
        c = _plate(i, j, w, h, x, y, base_key="mid", across="v", sheen_at=0.1, wear=0.8)
        return (*c, 255)
    if name == "knuckle_front":
        c = _plate(i, j, w, h, x, y, base_key="mid", across="v", sheen_at=0.45, wear=0.8)
        return (*c, 255)
    if name == "finger_front":
        c = _plate(i, j, w, h, x, y, across="u", sheen_at=0.2, wear=0.5)
        if j % 4 == 3:
            c = _mix(c, IRON["black"], 0.6)      # joints between the finger lames
        return (*c, 255)
    if name == "finger_side":
        c = _shade(IRON["dark"], 0.85 + 0.3 * ((j % 4) < 2))
        return (*_clamp(tuple(c[k] + _noise(x, y, 3.0) for k in range(3))), 255)
    if name == "finger_curl":
        c = _plate(i, j, w, h, x, y, base_key="dark", across="u", sheen_at=0.6)
        if j % 4 == 3:
            c = _mix(c, IRON["black"], 0.6)
        return (*c, 255)
    if name == "stud_side":
        c = _plate(i, j, w, h, x, y, base_key="light", across="v", sheen_at=0.0, wear=0.9)
        return (*c, 255)
    if name == "stud_face":
        cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
        d = math.hypot(i - cx, j - cy) / (w / 2.0)
        c = _mix(IRON["glint"], IRON["light"], min(1.0, d * 1.2))   # polished by use
        if d > 0.85:
            c = IRON["mid"]
        return (*_clamp(tuple(c[k] + _noise(x, y, 4.0) for k in range(3))), 255)
    if name == "rivet":
        return (*(BRONZE["hi"] if (i, j) == (0, 0) else BRONZE["base"]), 255)
    raise KeyError(name)


def render_texture() -> list[list[tuple[int, int, int, int]]]:
    pixels = [[(0, 0, 0, 0)] * TEXTURE_SIZE for _ in range(TEXTURE_SIZE)]
    occupied: dict[tuple[int, int], str] = {}
    for name, (x0, y0, x1, y1) in REGIONS.items():
        w, h = x1 - x0, y1 - y0
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x, y) in occupied:
                    raise ValueError(f"texture regions {occupied[(x, y)]} and {name} overlap at {x},{y}")
                occupied[(x, y)] = name
                pixels[y][x] = paint_region(name, x - x0, y - y0, w, h, x, y)
    return pixels


# ---------------------------------------------------------------------------------------------
# Elements
# ---------------------------------------------------------------------------------------------

def uv(region: str, flip_u: bool = False, flip_v: bool = False):
    x0, y0, x1, y1 = REGIONS[region]
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


def faces_of(side: str, cap: str | None = None, side_x: str | None = None, cap_down: str | None = None) -> dict:
    """Same region on the four sides (``side_x`` for east/west), ``cap`` on up and ``cap_down``
    (default ``cap``) on down."""
    out = {"north": uv(side), "south": uv(side), "east": uv(side_x or side), "west": uv(side_x or side)}
    if cap:
        out["up"] = uv(cap)
        out["down"] = uv(cap_down or cap)
    return out


def build_cuff() -> list[dict]:
    """A short flared cuff: the rim at the open end, a lower and an upper course stepping in toward
    the wrist, a bronze band with rivets, and the leather strap at the narrow wrist."""
    els = []
    flare, low, high, band, strap = 5.9, 5.45, 5.1, 5.6, SHELL + 0.1
    els.append(box("cuff_flare", (CX - flare, CUFF_END_Y, CZ - flare), (CX + flare, CUFF_END_Y + 1.1, CZ + flare),
                   faces_of("cuff_flare", "cuff_top", cap_down="cuff_end")))
    els.append(box("cuff_lower", (CX - low, CUFF_END_Y + 1.1, CZ - low), (CX + low, 1.5, CZ + low),
                   faces_of("cuff_side", "cuff_top")))
    els.append(box("cuff_upper", (CX - high, 1.5, CZ - high), (CX + high, CUFF_Y[1] - 0.6, CZ + high),
                   faces_of("cuff_side", "cuff_top")))
    els.append(box("cuff_band", (CX - band, 1.2, CZ - band), (CX + band, 1.9, CZ + band),
                   faces_of("cuff_band", "cuff_top")))
    els.append(box("wrist_strap", (CX - strap, CUFF_Y[1] - 0.6, CZ - strap), (CX + strap, CUFF_Y[1] + 0.3, CZ + strap),
                   faces_of("strap", "glove")))
    # Bronze rivets on the band, two per side.
    for side, (dx, dz) in (("back", (-1, 0)), ("palm", (1, 0)), ("thumb", (0, -1)), ("little", (0, 1))):
        for k, offset in enumerate((-2.7, 2.7)):
            if dx:
                x0 = CX + band if dx > 0 else CX - band - 0.35
                lo, hi = (x0, 1.35, CZ + offset - 0.25), (x0 + 0.35, 1.75, CZ + offset + 0.25)
            else:
                z0 = CZ + band if dz > 0 else CZ - band - 0.35
                lo, hi = (CX + offset - 0.25, 1.35, z0), (CX + offset + 0.25, 1.75, z0 + 0.35)
            els.append(box(f"cuff_rivet_{side}_{k}", lo, hi, faces_of("rivet", "rivet")))
    return els


def build_hand() -> list[dict]:
    els = []
    s = SHELL
    top = FIST[1] + SLEEVE + 0.05
    # The glove under the plates: closes every side of the fist and its end.
    els.append(box("glove", (CX - s, HAND_Y[0], CZ - s), (CX + s, top, CZ + s), faces_of("glove", "glove")))
    # Back of the hand: two overlapping lames toward the wrist, then the main plate with its ridge.
    for k, (y0, y1, out) in enumerate(((4.5, 6.6, 0.7), (6.3, 8.3, 0.95))):
        els.append(box(f"back_lame_{k}", (CX - s - out, y0, CZ - s + 0.2), (CX - s, y1, CZ + s - 0.2),
                       {"west": uv("back_lame"), "north": uv("plate_edge"), "south": uv("plate_edge"),
                        "up": uv("plate_edge"), "down": uv("plate_edge")}))
    els.append(box("back_plate", (CX - s - 1.25, 8.0, CZ - s - 0.25), (CX - s, 10.8, CZ + s + 0.25),
                   {"west": uv("back_plate"), "north": uv("plate_edge"), "south": uv("plate_edge"),
                    "down": uv("plate_edge"), "up": uv("plate_edge")}))
    els.append(box("back_ridge", (CX - s - 1.75, 8.3, CZ - 0.75), (CX - s - 1.25, 10.6, CZ + 0.75),
                   faces_of("plate_edge", "plate_edge", side_x="ridge")))
    # Side plates on the thumb (-Z) and little-finger (+Z) edges of the hand.
    for name, z0, z1 in (("thumb_side", CZ - s - 0.6, CZ - s), ("little_side", CZ + s, CZ + s + 0.6)):
        els.append(box(f"{name}_plate", (CX - s + 0.2, HAND_Y[0] + 0.4, z0), (CX + s - 0.35, KNUCKLE_Y[0] + 0.9, z1),
                       {"north": uv("side_plate"), "south": uv("side_plate"), "up": uv("plate_edge"),
                        "down": uv("plate_edge"), "east": uv("plate_edge"), "west": uv("plate_edge")}))
    # Leather palm with stitched edges.
    els.append(box("palm", (CX + s, HAND_Y[0] + 0.4, CZ - s + 0.5), (CX + s + 0.35, 8.8, CZ + s - 0.5),
                   {"east": uv("palm"), "north": uv("glove"), "south": uv("glove"), "up": uv("glove"),
                    "down": uv("glove")}))
    # Thumb: the thenar plate along the thumb edge, the joint at the corner, and the thumb folded
    # across the curled fingers on the palm side.
    els.append(box("thumb_base", (CX + 1.4, 5.0, CZ - s - 0.7), (CX + s + 0.3, 9.6, CZ - s),
                   faces_of("thumb", "thumb_end")))
    els.append(box("thumb_joint", (CX + s, 8.2, CZ - s - 0.7), (CX + s + 1.55, 10.4, CZ - s + 0.35),
                   faces_of("thumb_end", "thumb_end")))
    els.append(box("thumb", (CX + s + 0.55, 9.6, CZ - s - 0.5), (CX + s + 1.55, 11.8, CZ + 0.5),
                   {"east": uv("thumb"), "west": uv("thumb"), "up": uv("thumb_end"), "down": uv("thumb_end"),
                    "north": uv("thumb_end"), "south": uv("thumb_end")}))
    return els


def build_knuckles() -> list[dict]:
    els = []
    s = SHELL
    front = FIST[1] + SLEEVE + 0.05
    # Knuckle bar round the back edge of the striking face, and its cap over the row of knuckles.
    els.append(box("knuckle_bar", (CX - s - 1.3, KNUCKLE_Y[0], CZ - s - 0.4), (CX - s, front + 0.35, CZ + s + 0.4),
                   {"west": uv("knuckle_bar"), "up": uv("plate_edge"), "down": uv("plate_edge"),
                    "north": uv("plate_edge"), "south": uv("plate_edge"), "east": uv("plate_edge")}))
    els.append(box("knuckle_cap", (CX - s - 1.3, front, CZ - s - 0.4), (CX - 0.4, KNUCKLE_Y[1], CZ + s + 0.4),
                   {"up": uv("knuckle_front"), "west": uv("plate_edge"), "north": uv("plate_edge"),
                    "south": uv("plate_edge"), "east": uv("plate_edge"), "down": uv("plate_edge")}))
    for k, (z0, z1) in enumerate(FINGER_Z):
        # The first finger segment on the striking face, then the curled middle segment on the palm side.
        els.append(box(f"finger_{k}_front", (CX - 0.4, front, z0), (CX + s + 0.2, KNUCKLE_Y[1] - 0.15, z1),
                       {"up": uv("finger_front"), "north": uv("finger_side"), "south": uv("finger_side"),
                        "east": uv("finger_side"), "down": uv("finger_side")}))
        els.append(box(f"finger_{k}_curl", (CX + s, 9.0, z0 + 0.1), (CX + s + 0.55, KNUCKLE_Y[1] - 0.3, z1 - 0.1),
                       {"east": uv("finger_curl"), "north": uv("finger_side"), "south": uv("finger_side"),
                        "up": uv("finger_side"), "down": uv("finger_side")}))
        # The knuckle stud of that finger: the striking face (head).
        els.append(box(f"knuckle_stud_{k}", (CX - s - 0.85, STUD_Y[0], z0 + 0.15), (CX - 1.1, STUD_Y[1], z1 - 0.15),
                       {"up": uv("stud_face"), "north": uv("stud_side"), "south": uv("stud_side"),
                        "east": uv("stud_side"), "west": uv("stud_side")}))
        els.append(box(f"knuckle_stud_{k}_cap", (CX - s - 0.35, STUD_Y[1], z0 + 0.5), (CX - 1.6, STUD_CAP_TOP, z1 - 0.5),
                       {"up": uv("stud_face"), "north": uv("stud_side"), "south": uv("stud_side"),
                        "east": uv("stud_side"), "west": uv("stud_side")}))
    return els


def build_elements() -> list[dict]:
    return build_cuff() + build_hand() + build_knuckles()


# ---------------------------------------------------------------------------------------------
# Display transforms
# ---------------------------------------------------------------------------------------------
GRIP_CENTER = (CX, GRIP_Y, CZ)
MODEL_CENTER = (CX, (CUFF_END_Y + STUD_CAP_TOP) / 2.0, CZ)
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
# The gauntlet stands upright with its back toward the viewer, lit from the upper left: the row of
# four knuckle studs on top (the thing that says "fist"), the knuckle bar, the back plate with its
# ridge and lame seams, the thumb's edge on the right, the leather strap at the wrist, and the
# flared cuff with its bronze band and rivets.  Every colour comes from the model's palettes.
# ---------------------------------------------------------------------------------------------
ICON_STUDS = (9, 13, 17, 21)          # left column of each 3-px knuckle stud
OUTLINE = _hex("#07080b")


def _ramp(palette: dict, keys: tuple, t: float):
    """Colour at ``t`` in 0..1 along the named palette keys (dark to light)."""
    t = max(0.0, min(1.0, t)) * (len(keys) - 1)
    k = min(len(keys) - 2, int(t))
    return _mix(palette[keys[k]], palette[keys[k + 1]], t - k)


def icon_grid() -> dict[tuple[int, int], tuple[int, int, int]]:
    """Logical 32x32 icon as {(x, y): rgb}."""
    px: dict[tuple[int, int], tuple[int, int, int]] = {}
    iron = ("black", "deep", "dark", "base", "mid", "light", "edge", "sheen")

    def put(x, y, colour):
        if 0 <= x < ICON_GRID and 0 <= y < ICON_GRID:
            px[(x, y)] = colour

    def lit(x, y, x0, x1, y0, y1, bias=0.0):
        """Form light of a convex face spanning x0..x1, y0..y1: from the upper left."""
        u = (x - x0 + 0.5) / (x1 - x0 + 1)
        v = (y - y0 + 0.5) / (y1 - y0 + 1)
        return 0.75 - 0.55 * u - 0.25 * v + 0.25 * (1.0 - (2.0 * u - 0.6) ** 2) + bias

    # Cuff: flare rim, lower and upper courses, bronze band with rivets.
    for y in range(21, 30):
        half = 8 if y < 24 else 9 if y < 28 else 10
        for x in range(16 - half, 16 + half + 1):
            t = lit(x, y, 16 - half, 16 + half, 21, 29)
            if y in (28, 29):
                t += 0.15 if y == 28 else -0.2
            put(x, y, _ramp(IRON, iron, t))
        if y == 24:
            for x in range(16 - half, 16 + half + 1):
                put(x, y, _ramp(BRONZE, ("dark", "base", "light", "hi"), lit(x, y, 7, 25, 24, 24) + 0.2))
            for x in (10, 16, 22):
                put(x, y, BRONZE["hi"])
        if y == 27:
            for x in range(16 - half, 16 + half + 1):
                put(x, y, IRON["black"])
    # Wrist strap.
    for y in (19, 20):
        for x in range(9, 24):
            put(x, y, _ramp(LEATHER, ("black", "dark", "base", "mid", "light"), lit(x, y, 9, 23, 19, 20) + 0.1))
        put(12 + (y - 19) * 6, y, LEATHER["stitch"])
    # Back plate with a ridge and two lame seams.
    for y in range(9, 19):
        for x in range(8, 24):
            t = lit(x, y, 8, 23, 9, 18)
            if x in (15, 16):
                t += 0.3 if x == 15 else 0.1
            put(x, y, _ramp(IRON, iron, t))
        if y in (12, 15):
            for x in range(8, 24):
                put(x, y, IRON["black"])
        if y in (13, 16):
            for x in range(8, 15):
                put(x, y, _mix(px[(x, y)], IRON["edge"], 0.5))
    # Thumb side and the thumb folded across the fist, on the right.
    for y in range(9, 19):
        for x in (24, 25):
            put(x, y, _ramp(IRON, iron, 0.25 - 0.08 * (x - 24) - 0.01 * y))
    for y in range(10, 15):
        for x in range(24, 28 if y in (11, 12, 13) else 27):
            put(x, y, _ramp(IRON, iron, 0.55 - 0.1 * (x - 24) - 0.04 * (y - 10)))
    # Knuckle bar and the finger tops behind the studs.
    for y in (7, 8):
        for x in range(8, 25):
            put(x, y, _ramp(IRON, iron, lit(x, y, 8, 24, 7, 8) + (0.25 if y == 7 else 0.05)))
    for y in (5, 6):
        for x in range(9, 25):
            put(x, y, _ramp(IRON, iron, lit(x, y, 9, 24, 5, 6) - 0.1))
        for x0 in ICON_STUDS:
            put(x0 + 3, y, IRON["black"])            # seams between the fingers
    # The four knuckle studs: polished faces catching the light.
    for x0 in ICON_STUDS:
        for y in (3, 4, 5):
            for x in range(x0, x0 + 3):
                t = 0.95 - 0.25 * (x - x0) - 0.18 * (y - 3)
                put(x, y, _ramp(IRON, iron + ("glint",), t))
        put(x0, 3, IRON["glint"])
    # Dark outline round the whole shape.
    for (x, y) in list(px):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in px and 0 <= n[0] < ICON_GRID and 0 <= n[1] < ICON_GRID:
                px[n] = OUTLINE
    return px


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
        "collar": {"y": list(KNUCKLE_Y), "half_width": _extent(("knuckle_bar",), 2, CZ),
                   "half_thickness": _extent(("knuckle_bar",), 0, CX)},
        "butt": {"y": list(CUFF_Y), "half_width": _extent(("cuff_flare",), 2, CZ),
                 "half_thickness": _extent(("cuff_flare",), 0, CX)},
        "head": {"half_width": _extent(("knuckle_stud",), 2, CZ), "half_thickness": _extent(("knuckle_stud",), 0, CX),
                 "ridge_half_thickness": _extent(("knuckle_stud",), 0, CX), "taper_start_y": STUD_Y[1]},
        "head_base": [CX, STUD_Y[0], CZ],
        "head_tip": [CX, STUD_CAP_TOP, CZ],
        "trail": {"base": [CX, TRAIL_Y[0], CZ], "tip": [CX, TRAIL_Y[1], CZ]},
        "edge_axis": [0.0, 0.0, 1.0],
        "flat_axis": [1.0, 0.0, 0.0],
        "overall_y": [CUFF_END_Y, STUD_CAP_TOP],
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


def fist_fit() -> dict:
    """Third-person fit in the neutral item frame (PAL right_item = 0)."""
    t = build_display()["thirdperson_righthand"]
    grip = qf.display_point(t, GRIP_CENTER)
    along = qf.normalize(qf.display_vector(t, (0.0, 1.0, 0.0)))
    palm = qf.display_vector(t, (1.0, 0.0, 0.0))
    thumb = qf.display_vector(t, (0.0, 0.0, -1.0))
    knuckles = qf.display_point(t, (CX, STUD_CAP_TOP, CZ))
    cuff = qf.display_point(t, (CX, CUFF_END_Y, CZ))
    return {
        "grip": grip, "grip_error": math.dist(grip, qf.FIST_CENTER_ITEM),
        "punch_axis_item": along, "palm_item": palm, "thumb_item": thumb,
        "knuckles_item": knuckles, "cuff_item": cuff,
        # item frame z = 0 is the end of the arm, z = 4 the back of the 4 px fist (toward the shoulder)
        "studs_past_hand_px": -knuckles[2], "cuff_up_arm_px": cuff[2],
        "width_player_px": 2 * (SHELL + 1.45) * THIRD_PERSON_SCALE,
        "in_hand_length_blocks": (STUD_CAP_TOP - CUFF_END_Y) * THIRD_PERSON_SCALE * 0.9375 / 16.0,
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
    if abs(min(ys) - CUFF_END_Y) > 1e-6 or abs(max(ys) - STUD_CAP_TOP) > 1e-6:
        errors.append(f"model spans y {min(ys):.3f}..{max(ys):.3f}, expected {CUFF_END_Y}..{STUD_CAP_TOP}")
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
