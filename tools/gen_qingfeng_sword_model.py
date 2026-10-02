#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the Qingfeng 3D jian (straight sword) item model, its texture and geometry contract.

Outputs (all under src/main/resources/assets/myvillage/):

* ``models/item/qingfeng_sword_3d.json``  element model of the jian with every display
  transform except ``gui``.
* ``models/item/qingfeng_sword.json``     ``neoforge:separate_transforms`` wrapper: ``base``
  is the 3D model, the ``gui`` perspective keeps the existing 2D ``item/qingfeng_sword``
  sprite on ``minecraft:item/handheld``.
* ``textures/item/qingfeng_sword_model.png`` 64x64 texture for the 3D model (procedural,
  palette continued from ``qingfeng_sword.png``; written with zlib + struct only).
* ``combat/qingfeng_sword_geometry.json`` geometry contract read at runtime by the
  first-person grip (units: model pixels, 16 = one block).

Model frame: the blade runs along +Y, the flat normal (thickness) is X, the edge (width) is
Z, and the sword axis is the line x = 8, z = 8.  Pommel bottom y = 0, tip y = 23.

Display transforms are *derived*, not hand-tuned: the old sprite sword on
``minecraft:item/handheld`` defined where the blade points in every context; ``derive``
maps the new model's axes onto the old sprite's axes (blade direction, flat normal) and
solves the translation so that the new grip centre lands on the target point (in third
person the centre of the player's fist).  ItemTransform.apply order is translate, then
rotation Quaternionf.rotationXYZ (matrix Rx * Ry * Rz), then scale; ItemRenderer then
translates by -0.5 block.

Usage: python3 tools/gen_qingfeng_sword_model.py [--check] [--report]
  --check   exit non-zero if a committed output differs from what would be generated
  --report  print the display derivation and the third-person grip fit
The script is deterministic and uses only the Python standard library.
"""

from __future__ import annotations

import argparse
import json
import math
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/myvillage"
MODEL_PATH = ASSETS / "models/item/qingfeng_sword.json"
MODEL_3D_PATH = ASSETS / "models/item/qingfeng_sword_3d.json"
TEXTURE_PATH = ASSETS / "textures/item/qingfeng_sword_model.png"
GEOMETRY_PATH = ASSETS / "combat/qingfeng_sword_geometry.json"

MODEL_3D_ID = "myvillage:item/qingfeng_sword_3d"
TEXTURE_ID = "myvillage:item/qingfeng_sword_model"
ICON_TEXTURE_ID = "myvillage:item/qingfeng_sword"
TEXTURE_SIZE = 64
TEXELS_PER_UV = TEXTURE_SIZE / 16.0

# ---------------------------------------------------------------------------------------------
# Geometry (model pixels).  Axis x = 8, z = 8; +Y runs from the pommel to the tip.
# ---------------------------------------------------------------------------------------------
CX = CZ = 8.0
POMMEL_Y = (0.0, 1.2)
LOWER_FERRULE_Y = (1.2, 1.55)
WRAP_Y = (1.55, 7.05)
UPPER_FERRULE_Y = (7.05, 7.4)
GUARD_Y = (7.4, 8.6)
TONGUE_TOP = 8.8
BLADE_BASE_Y = GUARD_Y[1]
TIP_Y = 24.5
GRIP_CENTER_Y = 4.6

BLADE_HALF_WIDTH = 0.9        # Z, edge to axis
BLADE_HALF_THICKNESS = 0.2    # X, flat to axis
RIDGE_HALF_DIAGONAL = 0.35    # 45-degree ridge bar: protrudes 0.15 past each flat
RIDGE_TOP = 21.8               # full ridge; a lower ridge continues into the point
RIDGE_TIP_HALF_DIAGONAL = 0.3
RIDGE_TIP_TOP = TIP_Y - 1.0
TIP_DIAMOND_HALF = 0.45       # 45-degree square at the point (90-degree apex)
TIP_SLAB_WIDTH = 0.45         # 22.5-degree edge slabs from full width to the diamond
TAN_22_5 = math.tan(math.radians(22.5))
# Straight edge lines at 22.5 degrees meet the diamond's side corners.
TIP_START_Y = TIP_Y - TIP_DIAMOND_HALF - (BLADE_HALF_WIDTH - TIP_DIAMOND_HALF) / TAN_22_5
TIP_CORE_HALF_WIDTH = 0.43
# The core must reach where the slabs' inner faces cross and keep its corners inside the slabs.
TIP_CORE_TOP = TIP_START_Y + 1.05

WRAP_HALF_X, WRAP_HALF_Z, WRAP_DIAMOND_HALF = 0.48, 0.64, 0.62
FERRULE_HALF_X, FERRULE_HALF_Z = 0.72, 0.8
GUARD_HALF_X, GUARD_HALF_Z = 0.85, 1.45
WING_SIZE_Z, WING_SIZE_Y, WING_HALF_X, WING_CENTER_DZ, WING_CENTER_Y = 0.8, 0.75, 0.72, 1.65, 8.15
BOSS_HALF_DIAGONAL, BOSS_HALF_X, BOSS_CENTER_Y = 0.55, 0.95, 8.0
TONGUE_HALF_X, TONGUE_HALF_Z = 0.42, 1.1
POMMEL_BODY = dict(half_x=0.72, half_z=0.95, y=(0.35, 1.2))
POMMEL_RIM = dict(half_x=0.78, half_z=1.05, y=(0.1, 0.35))
POMMEL_BUTTON = dict(half_x=0.5, half_z=0.7, y=(0.0, 0.1))
POMMEL_GEM_HALF, POMMEL_GEM_Y = 0.92, (0.4, 1.15)

FACES = ("north", "south", "east", "west", "up", "down")

# ---------------------------------------------------------------------------------------------
# Texture layout: name -> (x0, y0, x1, y1) texel rectangle on the 64x64 sheet.
# ---------------------------------------------------------------------------------------------
REGIONS = {
    "blade_flat": (0, 0, 8, 60),
    "blade_edge": (8, 0, 10, 60),
    "ridge": (10, 0, 12, 60),
    "tip_slab": (12, 0, 14, 6),
    "tip_core": (14, 0, 18, 5),
    "tip_point": (18, 0, 21, 3),
    "guard_face": (24, 0, 36, 5),
    "guard_side": (24, 6, 31, 11),
    "guard_top": (24, 12, 36, 19),
    "wing": (37, 0, 41, 4),
    "boss": (42, 0, 46, 4),
    "boss_rim": (47, 0, 50, 3),
    "tongue": (37, 6, 47, 8),
    "ferrule": (48, 9, 54, 11),
    "ferrule_top": (48, 12, 54, 18),
    "wrap": (24, 20, 29, 42),
    "pommel_face": (32, 20, 40, 24),
    "pommel_side": (32, 25, 38, 29),
    "pommel_bottom": (40, 20, 48, 26),
    "pommel_gem": (48, 20, 54, 24),
    "pommel_rim": (32, 30, 41, 31),
    "pommel_rim_top": (42, 27, 51, 34),
}


def _hex(value: str) -> tuple[int, int, int]:
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))


# Palette continued from textures/item/qingfeng_sword.png (bluish silver, gold, dark cord, jade).
STEEL = {"edge": _hex("#f4f6fa"), "bevel": _hex("#d6d9e1"), "flat": _hex("#b9bdc8"),
         "core": _hex("#a3a8b5"), "shadow": _hex("#8e93a2"), "edge2": _hex("#dfe3ea"),
         "valley": _hex("#8d93a3")}
GOLD = {"hi": _hex("#f6e3a6"), "light": _hex("#e4c274"), "base": _hex("#c99f52"),
        "shade": _hex("#a57e3e"), "dark": _hex("#6e4a1f")}
BRONZE = {"hi": _hex("#d8b870"), "base": _hex("#a88242"), "dark": _hex("#6b4a1e")}
CORD = {"gap": _hex("#1b181c"), "mid": _hex("#48414d"), "hi": _hex("#6e6677"), "dark": _hex("#252023"),
        "skin": _hex("#35605a")}
JADE = {"hi": _hex("#b0dedf"), "light": _hex("#8bcfd0"), "base": _hex("#5fadae"),
        "shade": _hex("#3f8a86"), "dark": _hex("#2e5f55")}


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _hash(x: int, y: int) -> float:
    """Deterministic value noise in [-1, 1]."""
    h = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 32767.5 - 1.0


def _jitter(color, amount: float, x: int, y: int):
    d = _hash(x, y) * amount
    return tuple(max(0, min(255, int(round(c + d)))) for c in color)


def paint_region(name: str, i: int, j: int, w: int, h: int, x: int, y: int):
    """Colour of texel (i, j) inside region ``name`` of size w x h (sheet position x, y)."""
    if name == "blade_flat":
        d = abs(i + 0.5 - w / 2) / (w / 2)       # 0 at the ridge, 1 at the edge
        # ridge (hidden) | dark valley beside the ridge | polished bevel | bright edge
        base = (STEEL["core"] if d < 0.3 else STEEL["valley"] if d < 0.55
                else _mix(STEEL["bevel"], STEEL["edge"], 0.35) if d < 0.8 else STEEL["edge"])
        if j >= h - 3 and d < 0.8:              # a touch darker where the blade leaves the guard
            base = _mix(base, STEEL["shadow"], 0.45)
        return _jitter(base, 1.5 if d < 0.8 else 1.0, x, y)
    if name == "blade_edge":
        return STEEL["edge"] if i == 0 else STEEL["edge2"]
    if name == "ridge":
        return _jitter(_mix(STEEL["bevel"], STEEL["edge"], 0.4 if i == 0 else 0.8), 1.5, x, y)
    if name == "tip_slab":                       # column 0 = outer edge
        return STEEL["edge"] if i == 0 else STEEL["bevel"]
    if name == "tip_core":
        return STEEL["flat"] if i in (0, w - 1) else STEEL["core"]
    if name == "tip_point":
        return _mix(STEEL["bevel"], STEEL["edge"], 0.5)
    if name in ("guard_face", "guard_side", "wing"):
        rows = [GOLD["hi"], GOLD["light"], GOLD["base"], GOLD["shade"], GOLD["dark"]]
        c = rows[min(len(rows) - 1, int(j * len(rows) / h))]
        if name == "guard_face" and i in (0, w - 1):
            c = _mix(c, GOLD["dark"], 0.35)
        if name == "guard_face" and 1 <= j <= h - 2 and i in (2, w - 3):  # engraved cloud stops
            c = _mix(c, GOLD["dark"], 0.5)
        return _jitter(c, 2.0, x, y)
    if name == "guard_top":
        edge = i in (0, w - 1) or j in (0, h - 1)
        return _jitter(GOLD["light"] if edge else GOLD["base"], 2.0, x, y)
    if name == "boss":                           # gold rim, jade inlay
        rim = i in (0, w - 1) or j in (0, h - 1)
        if rim:
            return GOLD["hi"] if (i + j) < w else GOLD["shade"]
        return JADE["hi"] if (i, j) == (1, 1) else JADE["base"]
    if name == "boss_rim":
        return GOLD["light"]
    if name == "tongue":
        return GOLD["hi"] if j == 0 else GOLD["light"]
    if name == "ferrule":
        return BRONZE["hi"] if j == 0 else BRONZE["base"]
    if name == "ferrule_top":
        edge = i in (0, w - 1) or j in (0, h - 1)
        return BRONZE["base"] if edge else BRONZE["dark"]
    if name == "wrap":                           # 菱形缠: crossing cords with diamond windows
        d = abs(i - w // 2) + abs(j % 4 - 2)
        c = CORD["skin"] if d <= 1 else CORD["hi"] if (d == 2 and j % 4 < 2) else CORD["mid"] if d == 2 else CORD["gap"]
        return _jitter(c, 1.5, x, y)
    if name in ("pommel_face", "pommel_side"):
        rows = [JADE["hi"], JADE["light"], JADE["base"], JADE["shade"]]
        c = rows[min(len(rows) - 1, int(j * len(rows) / h))]
        if _hash(x * 3, y * 5) > 0.55:          # faint jade cloud
            c = _mix(c, JADE["hi"], 0.35)
        return c
    if name == "pommel_gem":
        c = JADE["light"] if j < h // 2 else JADE["base"]
        return JADE["hi"] if (i, j) == (1, 0) else c
    if name == "pommel_bottom":
        edge = i in (0, w - 1) or j in (0, h - 1)
        return JADE["shade"] if edge else JADE["base"]
    if name == "pommel_rim":
        return JADE["shade"]
    if name == "pommel_rim_top":
        edge = i in (0, w - 1) or j in (0, h - 1)
        return JADE["base"] if edge else JADE["shade"]
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
                r, g, b = paint_region(name, x - x0, y - y0, w, h, x, y)
                pixels[y][x] = (r, g, b, 255)
    return pixels


def encode_png(pixels) -> bytes:
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def decode_png_rgba(data: bytes) -> tuple[int, int, bytes]:
    """Decode an 8-bit RGBA non-interlaced PNG (enough to compare generator output)."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, width = 8, b"", 0
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if (depth, ctype, interlace) != (8, 6, 0):
                raise ValueError("only 8-bit RGBA non-interlaced PNG is supported")
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    raw = zlib.decompress(idat)
    stride = width * 4
    out, prev = bytearray(), bytearray(stride)
    for row in range(height):
        f = raw[row * (stride + 1)]
        line = bytearray(raw[row * (stride + 1) + 1:(row + 1) * (stride + 1)])
        for k in range(stride):
            a = line[k - 4] if k >= 4 else 0
            b = prev[k]
            c = prev[k - 4] if k >= 4 else 0
            if f == 1:
                line[k] = (line[k] + a) & 0xFF
            elif f == 2:
                line[k] = (line[k] + b) & 0xFF
            elif f == 3:
                line[k] = (line[k] + (a + b) // 2) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[k] = (line[k] + pred) & 0xFF
        out += line
        prev = line
    return width, height, bytes(out)


# ---------------------------------------------------------------------------------------------
# Elements
# ---------------------------------------------------------------------------------------------

def _r(v: float) -> float:
    out = round(v, 4)
    return 0.0 if out == 0 else out


def uv(region: str, flip_u: bool = False, sub: tuple[float, float, float, float] | None = None):
    """UV rectangle (in 0-16 units) of a texture region, optionally a fraction of it."""
    x0, y0, x1, y1 = REGIONS[region]
    if sub:
        fx0, fy0, fx1, fy1 = sub
        x0, x1 = x0 + (x1 - x0) * fx0, x0 + (x1 - x0) * fx1
        y0, y1 = y0 + (y1 - y0) * fy0, y0 + (y1 - y0) * fy1
    u0, v0, u1, v1 = (_r(c / TEXELS_PER_UV) for c in (x0, y0, x1, y1))
    return [u1, v0, u0, v1] if flip_u else [u0, v0, u1, v1]


def box(name: str, lo, hi, faces: dict, rotation: dict | None = None) -> dict:
    element = {"name": name, "from": [_r(c) for c in lo], "to": [_r(c) for c in hi]}
    if rotation:
        element["rotation"] = {"angle": rotation["angle"], "axis": rotation["axis"],
                               "origin": [_r(c) for c in rotation["origin"]]}
    element["faces"] = {face: {"uv": faces[face], "texture": "#sword"} for face in FACES if face in faces}
    return element


def centered(name, half_x, half_z, y0, y1, faces, rotation=None, cx=CX, cz=CZ):
    return box(name, (cx - half_x, y0, cz - half_z), (cx + half_x, y1, cz + half_z), faces, rotation)


def all_faces(side_x: str, side_z: str, cap: str) -> dict:
    return {"east": uv(side_x), "west": uv(side_x), "north": uv(side_z), "south": uv(side_z),
            "up": uv(cap), "down": uv(cap)}


def build_elements() -> list[dict]:
    els: list[dict] = []
    # --- blade ---------------------------------------------------------------------------
    blade_len_share = (TIP_START_Y - BLADE_BASE_Y) / 15.0
    flat = uv("blade_flat", sub=(0, 1 - blade_len_share, 1, 1))
    edge = uv("blade_edge", sub=(0, 1 - blade_len_share, 1, 1))
    els.append(centered("blade", BLADE_HALF_THICKNESS, BLADE_HALF_WIDTH, BLADE_BASE_Y - 0.1, TIP_START_Y,
                        {"east": flat, "west": flat, "north": edge, "south": edge,
                         "up": uv("blade_edge", sub=(0, 0, 1, 0.02))}))
    ridge_half = RIDGE_HALF_DIAGONAL / math.sqrt(2.0)
    ridge_share = (RIDGE_TOP - BLADE_BASE_Y) / 15.0
    ridge = uv("ridge", sub=(0, 1 - ridge_share, 1, 1))
    els.append(centered("ridge", ridge_half, ridge_half, BLADE_BASE_Y, RIDGE_TOP,
                        {"east": ridge, "west": ridge, "north": ridge, "south": ridge,
                         "up": uv("ridge", sub=(0, 0, 1, 0.04))},
                        rotation={"angle": 45, "axis": "y", "origin": (CX, BLADE_BASE_Y, CZ)}))
    tip_ridge_half = RIDGE_TIP_HALF_DIAGONAL / math.sqrt(2.0)
    els.append(centered("ridge_point", tip_ridge_half, tip_ridge_half, RIDGE_TOP - 0.2, RIDGE_TIP_TOP,
                        {"east": ridge, "west": ridge, "north": ridge, "south": ridge,
                         "up": uv("ridge", sub=(0, 0, 1, 0.04))},
                        rotation={"angle": 45, "axis": "y", "origin": (CX, RIDGE_TOP, CZ)}))
    # --- tip: core, two 22.5-degree edge slabs, 45-degree point ------------------------------
    core_t = BLADE_HALF_THICKNESS - 0.01
    els.append(centered("tip_core", core_t, TIP_CORE_HALF_WIDTH, TIP_START_Y - 0.05, TIP_CORE_TOP,
                        {"east": uv("tip_core"), "west": uv("tip_core"), "up": uv("tip_core", sub=(0, 0, 1, 0.2))}))
    corner_y = TIP_Y - TIP_DIAMOND_HALF
    length = (corner_y - TIP_START_Y) / math.cos(math.radians(22.5))
    u_dir = (math.sin(math.radians(22.5)), math.cos(math.radians(22.5)))       # (z, y) along the edge
    n_dir = (math.cos(math.radians(22.5)), -math.sin(math.radians(22.5)))      # (z, y) inward
    slab_t = BLADE_HALF_THICKNESS - 0.03
    for side, sign in (("left", -1), ("right", 1)):
        # Outer edge starts at the blade edge (z = 8 -/+ 1, y = TIP_START_Y); mirror for the right.
        az, ay = -BLADE_HALF_WIDTH, TIP_START_Y
        cz_rel = az + u_dir[0] * length / 2 + n_dir[0] * TIP_SLAB_WIDTH / 2
        cy = ay + u_dir[1] * length / 2 + n_dir[1] * TIP_SLAB_WIDTH / 2
        cz = CZ - sign * cz_rel  # the right slab mirrors the left one across z = 8
        # Unrotated slab: length along Y, width along Z; the outer face is toward -Z on the left.
        lo = (CX - slab_t, cy - length / 2, cz - TIP_SLAB_WIDTH / 2)
        hi = (CX + slab_t, cy + length / 2, cz + TIP_SLAB_WIDTH / 2)
        outer_face = "north" if sign < 0 else "south"
        inner_face = "south" if sign < 0 else "north"
        # Columns of tip_slab: 0 = outer edge.  East is seen with +Z on the left, west with -Z.
        faces = {"east": uv("tip_slab", flip_u=sign < 0), "west": uv("tip_slab", flip_u=sign > 0),
                 outer_face: uv("blade_edge", sub=(0, 0, 1, 0.12)),
                 inner_face: uv("tip_core", sub=(0, 0, 0.5, 1)),
                 "down": uv("blade_edge", sub=(0, 0.2, 1, 0.22))}
        els.append(box(f"tip_edge_{side}", lo, hi, faces,
                       rotation={"angle": 22.5 if sign < 0 else -22.5, "axis": "x", "origin": (CX, cy, cz)}))
    point_half = TIP_DIAMOND_HALF / math.sqrt(2.0)
    point_t = BLADE_HALF_THICKNESS - 0.06
    els.append(centered("tip_point", point_t, point_half, corner_y - point_half, corner_y + point_half,
                        {"east": uv("tip_point"), "west": uv("tip_point"),
                         "north": uv("blade_edge", sub=(0, 0, 1, 0.06)), "south": uv("blade_edge", sub=(0, 0, 1, 0.06)),
                         "up": uv("blade_edge", sub=(0, 0, 1, 0.06)), "down": uv("blade_edge", sub=(0, 0, 1, 0.06))},
                        rotation={"angle": 45, "axis": "x", "origin": (CX, corner_y, CZ)}))
    # --- guard -------------------------------------------------------------------------------
    els.append(centered("guard_tongue", TONGUE_HALF_X, TONGUE_HALF_Z, GUARD_Y[1] - 0.1, TONGUE_TOP,
                        {"east": uv("tongue"), "west": uv("tongue"), "north": uv("tongue", sub=(0, 0, 0.25, 1)),
                         "south": uv("tongue", sub=(0, 0, 0.25, 1)), "up": uv("guard_top", sub=(0.2, 0.2, 0.8, 0.8))}))
    els.append(centered("guard", GUARD_HALF_X, GUARD_HALF_Z, GUARD_Y[0], GUARD_Y[1],
                        all_faces("guard_face", "guard_side", "guard_top")))
    for side, sign in (("left", -1), ("right", 1)):
        wz = CZ + sign * WING_CENTER_DZ
        els.append(box(f"guard_wing_{side}", (CX - WING_HALF_X, WING_CENTER_Y - WING_SIZE_Y / 2, wz - WING_SIZE_Z / 2),
                       (CX + WING_HALF_X, WING_CENTER_Y + WING_SIZE_Y / 2, wz + WING_SIZE_Z / 2),
                       all_faces("wing", "wing", "guard_top"),
                       rotation={"angle": 22.5 if sign < 0 else -22.5, "axis": "x", "origin": (CX, WING_CENTER_Y, wz)}))
    boss_half = BOSS_HALF_DIAGONAL / math.sqrt(2.0)
    els.append(centered("guard_boss", BOSS_HALF_X, boss_half, BOSS_CENTER_Y - boss_half, BOSS_CENTER_Y + boss_half,
                        {"east": uv("boss"), "west": uv("boss"), "north": uv("boss_rim"), "south": uv("boss_rim"),
                         "up": uv("boss_rim"), "down": uv("boss_rim")},
                        rotation={"angle": 45, "axis": "x", "origin": (CX, BOSS_CENTER_Y, CZ)}))
    # --- handle ------------------------------------------------------------------------------
    for name, (y0, y1) in (("upper_ferrule", UPPER_FERRULE_Y), ("lower_ferrule", LOWER_FERRULE_Y)):
        els.append(centered(name, FERRULE_HALF_X, FERRULE_HALF_Z, y0, y1, all_faces("ferrule", "ferrule", "ferrule_top")))
    wrap_faces = {f: uv("wrap") for f in ("east", "west", "north", "south")}
    els.append(centered("grip_wrap", WRAP_HALF_X, WRAP_HALF_Z, WRAP_Y[0], WRAP_Y[1], dict(wrap_faces)))
    wrap_diag = WRAP_DIAMOND_HALF / math.sqrt(2.0)
    els.append(centered("grip_wrap_facets", wrap_diag, wrap_diag, WRAP_Y[0], WRAP_Y[1], dict(wrap_faces),
                        rotation={"angle": 45, "axis": "y", "origin": (CX, WRAP_Y[0], CZ)}))
    # --- pommel ------------------------------------------------------------------------------
    pb, pr, pt = POMMEL_BODY, POMMEL_RIM, POMMEL_BUTTON
    els.append(centered("pommel", pb["half_x"], pb["half_z"], pb["y"][0], pb["y"][1],
                        all_faces("pommel_face", "pommel_side", "pommel_bottom")))
    gem = POMMEL_GEM_HALF / math.sqrt(2.0)
    els.append(centered("pommel_facets", gem, gem, POMMEL_GEM_Y[0], POMMEL_GEM_Y[1],
                        {f: uv("pommel_gem") for f in ("east", "west", "north", "south")},
                        rotation={"angle": 45, "axis": "y", "origin": (CX, POMMEL_GEM_Y[0], CZ)}))
    els.append(centered("pommel_rim", pr["half_x"], pr["half_z"], pr["y"][0], pr["y"][1],
                        all_faces("pommel_rim", "pommel_rim", "pommel_rim_top")))
    els.append(centered("pommel_button", pt["half_x"], pt["half_z"], pt["y"][0], pt["y"][1],
                        all_faces("pommel_rim", "pommel_rim", "pommel_bottom")))
    return els


# ---------------------------------------------------------------------------------------------
# Display transforms
# ---------------------------------------------------------------------------------------------

def _rot(axis: str, degrees: float):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    if axis == "x":
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == "y":
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return tuple(sum(a[i][k] * v[k] for k in range(3)) for i in range(3))


def euler_xyz(rotation):
    """Matrix of Quaternionf.rotationXYZ(x, y, z) = Rx * Ry * Rz (degrees)."""
    x, y, z = rotation
    return mm(mm(_rot("x", x), _rot("y", y)), _rot("z", z))


def to_euler_xyz(m):
    y = math.degrees(math.asin(max(-1.0, min(1.0, m[0][2]))))
    x = math.degrees(math.atan2(-m[1][2], m[2][2]))
    z = math.degrees(math.atan2(-m[0][1], m[0][0]))
    if abs(x) > 90.0:  # Rx(a) Ry(b) Rz(c) == Rx(a + 180) Ry(180 - b) Rz(c + 180): prefer the small pitch
        x, y, z = x + 180.0, 180.0 - y, z + 180.0

    def wrap(a: float) -> float:
        a = (a + 180.0) % 360.0 - 180.0
        return 180.0 if abs(a + 180.0) < 1e-9 else a
    return (wrap(x), wrap(y), wrap(z))


def normalize(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def display_point(transform: dict, p) -> tuple[float, float, float]:
    """Model pixel -> display-frame pixel for one ItemTransform (right-hand, no mirroring)."""
    r = euler_xyz(transform["rotation"])
    s = transform["scale"][0]
    q = mv(r, tuple(s * (p[i] - 8.0) for i in range(3)))
    return tuple(transform["translation"][i] + q[i] for i in range(3))


def display_vector(transform: dict, v) -> tuple[float, float, float]:
    return mv(euler_xyz(transform["rotation"]), v)


# Old sprite (textures/item/qingfeng_sword.png on item/handheld, generated sprite in the z = 8
# plane).  Blade direction as used by tools/gen_sword_pal_anims.py (grip (4.5, 4.5) -> tip
# (15, 15.3)); the dark wrapped handle's centre measured on the 64x64 sprite is (2.4, 2.4).
OLD_BLADE_DIR = normalize((10.5, 10.8, 0.0))
OLD_FLAT_NORMAL = (0.0, 0.0, 1.0)
OLD_HANDLE_CENTER = (2.4, 2.4, 8.0)
OLD_SPRITE_CENTER = (8.0, 8.0, 8.0)
OLD_SWORD_LENGTH = 20.6       # pommel bottom to tip on the sprite diagonal, model pixels
NEW_SWORD_LENGTH = TIP_Y - POMMEL_Y[0]

VANILLA = {  # minecraft:item/handheld (1.21.1) and its parent minecraft:item/generated
    "thirdperson_righthand": {"rotation": (0, -90, 55), "translation": (0, 4.0, 0.5), "scale": (0.85,) * 3},
    "firstperson_righthand": {"rotation": (0, -90, 25), "translation": (1.13, 3.2, 1.13), "scale": (0.68,) * 3},
    "ground": {"rotation": (0, 0, 0), "translation": (0, 2, 0), "scale": (0.5,) * 3},
    "head": {"rotation": (0, 180, 0), "translation": (0, 13, 7), "scale": (1,) * 3},
    "fixed": {"rotation": (0, 180, 0), "translation": (0, 0, 0), "scale": (1,) * 3},
}

# Third person: ItemInHandLayer places the item frame at arm-space (-1, 10, -2) px with
# item +X = arm -X, item +Y = arm -Z (forward), item +Z = arm -Y (toward the shoulder).  The
# fist is the last 4 px of the 4x4 arm, centred on arm (-1, 8, 0) -> item frame (0, -2, 2).
# The front face of the fist is item y = 0 and the back face y = -4.
FIST_CENTER_ITEM = (0.0, -2.0, 2.0)
THIRD_PERSON_SCALE = 0.8
GRIP_CENTER = (CX, GRIP_CENTER_Y, CZ)


def axis_map():
    """Rotation taking new-model axes to old-sprite axes (flat X -> sprite normal, +Y -> blade)."""
    ex, ey = OLD_FLAT_NORMAL, OLD_BLADE_DIR
    ez = cross(ex, ey)
    return [[ex[i], ey[i], ez[i]] for i in range(3)]


def derive(context: str, anchor_new, anchor_target=None, anchor_old=None, scale=None) -> dict:
    old = VANILLA[context]
    r_new = mm(euler_xyz(old["rotation"]), axis_map())
    rotation = tuple(round(a, 2) + 0.0 for a in to_euler_xyz(r_new))
    s = round(scale if scale is not None else old["scale"][0] * OLD_SWORD_LENGTH / NEW_SWORD_LENGTH, 3)
    target = anchor_target if anchor_target is not None else display_point(old, anchor_old)
    offset = mv(euler_xyz(rotation), tuple(s * (anchor_new[i] - 8.0) for i in range(3)))
    translation = tuple(round(target[i] - offset[i], 3) + 0.0 for i in range(3))
    for t in translation:
        if abs(t) > 80.0:
            raise ValueError(f"{context} translation {translation} exceeds the 5-block clamp")
    return {"rotation": rotation, "translation": translation, "scale": (s, s, s)}


def mirror(transform: dict) -> dict:
    x, y, z = transform["rotation"]
    def neg(a: float) -> float:
        return 180.0 if abs(a) == 180.0 else -a + 0.0
    return {"rotation": (x, neg(y), neg(z)), "translation": transform["translation"], "scale": transform["scale"]}


def build_display() -> dict:
    third = derive("thirdperson_righthand", GRIP_CENTER, anchor_target=FIST_CENTER_ITEM, scale=THIRD_PERSON_SCALE)
    first = derive("firstperson_righthand", GRIP_CENTER, anchor_old=OLD_HANDLE_CENTER)
    center = (CX, NEW_SWORD_LENGTH / 2.0, CZ)
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": mirror(third),
        "firstperson_righthand": first,
        "firstperson_lefthand": mirror(first),
        "ground": derive("ground", center, anchor_old=OLD_SPRITE_CENTER),
        "head": derive("head", center, anchor_old=OLD_SPRITE_CENTER),
        "fixed": derive("fixed", center, anchor_old=OLD_SPRITE_CENTER),
    }


# ---------------------------------------------------------------------------------------------
# Documents
# ---------------------------------------------------------------------------------------------

def _json_display(display: dict) -> dict:
    return {k: {"rotation": [_r(c) for c in v["rotation"]], "translation": [_r(c) for c in v["translation"]],
                "scale": [_r(c) for c in v["scale"]]} for k, v in display.items()}


def build_model_3d() -> dict:
    return {
        "credit": "MyVillage original procedural jian (tools/gen_qingfeng_sword_model.py)",
        "texture_size": [TEXTURE_SIZE, TEXTURE_SIZE],
        "textures": {"sword": TEXTURE_ID, "particle": ICON_TEXTURE_ID},
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


def geometry_contract() -> dict:
    guard_wing_extent = (WING_CENTER_DZ + (WING_SIZE_Z / 2) * math.cos(math.radians(22.5))
                         + (WING_SIZE_Y / 2) * math.sin(math.radians(22.5)))
    return {
        "format": 2,
        "units": "model_pixels",
        "generator": "tools/gen_qingfeng_sword_model.py",
        "model": MODEL_3D_ID,
        "axes": {"length": "+y", "flat_normal": "x", "edge": "z", "center_x": CX, "center_z": CZ},
        "grip_center": [CX, GRIP_CENTER_Y, CZ],
        "handle": {"y": [LOWER_FERRULE_Y[0], UPPER_FERRULE_Y[1]], "half_width": WRAP_HALF_Z,
                   "half_thickness": WRAP_DIAMOND_HALF, "wrap_y": list(WRAP_Y)},
        # Contract names are weapon-neutral (format 2): the sword's guard is the collar, its pommel
        # the butt, and its blade the head.
        "collar": {"y": list(GUARD_Y), "half_width": _r(guard_wing_extent),
                   "half_thickness": _r(BOSS_HALF_X)},
        "butt": {"y": list(POMMEL_Y), "half_width": POMMEL_RIM["half_z"], "half_thickness": POMMEL_RIM["half_x"]},
        "head": {"half_width": BLADE_HALF_WIDTH, "half_thickness": BLADE_HALF_THICKNESS,
                 "ridge_half_thickness": RIDGE_HALF_DIAGONAL, "taper_start_y": _r(TIP_START_Y)},
        "head_base": [CX, BLADE_BASE_Y, CZ],
        "head_tip": [CX, TIP_Y, CZ],
        "edge_axis": [0.0, 0.0, 1.0],
        "flat_axis": [1.0, 0.0, 0.0],
        "overall_y": [POMMEL_Y[0], TIP_Y],
    }


def render_model_json(doc: dict) -> str:
    """Stable JSON: two-space indent, one element / display entry per line."""
    lines = ["{"]
    items = list(doc.items())
    for index, (key, value) in enumerate(items):
        comma = "," if index < len(items) - 1 else ""
        if key == "elements":
            lines.append('  "elements": [')
            for e_index, element in enumerate(value):
                tail = "," if e_index < len(value) - 1 else ""
                lines.append("    " + json.dumps(element, separators=(", ", ": ")) + tail)
            lines.append("  ]" + comma)
        elif key in ("display", "perspectives") and isinstance(value, dict):
            lines.append(f'  "{key}": {{')
            sub = list(value.items())
            for s_index, (name, entry) in enumerate(sub):
                tail = "," if s_index < len(sub) - 1 else ""
                lines.append(f"    {json.dumps(name)}: " + json.dumps(entry, separators=(", ", ": ")) + tail)
            lines.append("  }" + comma)
        else:
            lines.append(f"  {json.dumps(key)}: " + json.dumps(value, separators=(", ", ": ")) + comma)
    lines.append("}")
    return "\n".join(lines) + "\n"


def outputs() -> dict[Path, bytes]:
    return {
        MODEL_3D_PATH: render_model_json(build_model_3d()).encode(),
        MODEL_PATH: render_model_json(build_model()).encode(),
        GEOMETRY_PATH: render_model_json(geometry_contract()).encode(),
        TEXTURE_PATH: encode_png(render_texture()),
    }


# ---------------------------------------------------------------------------------------------
# Self-checks
# ---------------------------------------------------------------------------------------------

def check_elements(elements: list[dict]) -> list[str]:
    errors = []
    for e in elements:
        for c in e["from"] + e["to"]:
            if not -16.0 <= c <= 32.0:
                errors.append(f"{e['name']}: coordinate {c} outside [-16, 32]")
        for i in range(3):
            if e["from"][i] > e["to"][i]:
                errors.append(f"{e['name']}: from > to on axis {i}")
        rot = e.get("rotation")
        if rot and rot["angle"] not in (-45, -22.5, 0, 22.5, 45):
            errors.append(f"{e['name']}: rotation {rot['angle']} not allowed")
        for face, data in e["faces"].items():
            if any(not 0.0 <= c <= 16.0 for c in data["uv"]):
                errors.append(f"{e['name']}.{face}: uv out of range")
    return errors


def element_corners(e: dict):
    lo, hi = e["from"], e["to"]
    corners = [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    rot = e.get("rotation")
    if not rot:
        return corners
    m = _rot(rot["axis"], rot["angle"])
    o = rot["origin"]
    return [tuple(o[i] + mv(m, tuple(p[k] - o[k] for k in range(3)))[i] for i in range(3)) for p in corners]


def silhouette_errors(elements: list[dict]) -> list[str]:
    """The blade outline seen on the flat (YZ plane) must match the ideal straight-edged jian."""
    blade_names = {"blade", "tip_core", "tip_edge_left", "tip_edge_right", "tip_point"}
    polys = []
    for e in elements:
        if e["name"] in blade_names:
            c = element_corners(e)
            # face at x = max: 4 corners with the larger x, in (z, y)
            xs = sorted(set(round(p[0], 6) for p in c))
            pts = [(p[2], p[1]) for p in c if round(p[0], 6) == xs[-1]]
            cz, cy = sum(p[0] for p in pts) / 4, sum(p[1] for p in pts) / 4
            pts.sort(key=lambda p: math.atan2(p[1] - cy, p[0] - cz))
            polys.append(pts)

    def inside(poly, z, y):
        sign = 0
        for i in range(4):
            (z0, y0), (z1, y1) = poly[i], poly[(i + 1) % 4]
            c = (z1 - z0) * (y - y0) - (y1 - y0) * (z - z0)
            if abs(c) < 1e-9:
                continue
            s = 1 if c > 0 else -1
            if sign == 0:
                sign = s
            elif s != sign:
                return False
        return True

    def ideal_half_width(y):
        if y <= TIP_START_Y:
            return BLADE_HALF_WIDTH
        if y <= TIP_Y - TIP_DIAMOND_HALF:
            return BLADE_HALF_WIDTH - (y - TIP_START_Y) * TAN_22_5
        return max(0.0, TIP_Y - y)

    errors, worst_out, worst_gap = [], 0.0, 0.0
    step = 0.01
    y = BLADE_BASE_Y + step / 2
    while y < TIP_Y + 0.2:
        hw = ideal_half_width(y) if y <= TIP_Y else -1.0
        z = 8.0 - 1.3
        while z < 8.0 + 1.3:
            covered = any(inside(p, z, y) for p in polys)
            dist = abs(z - 8.0) - hw
            if covered and dist > worst_out:
                worst_out = dist
            if not covered and dist < -worst_gap:
                worst_gap = -dist
            z += step
        y += step
    if worst_out > 0.02:
        errors.append(f"blade outline pokes {worst_out:.3f} px past the straight edge")
    if worst_gap > 0.02:
        errors.append(f"blade outline has a {worst_gap:.3f} px hole inside the straight edge")
    return errors


def fist_fit() -> dict:
    """Third-person grip fit in the neutral item frame (PAL right_item = 0)."""
    t = build_display()["thirdperson_righthand"]
    grip = display_point(t, GRIP_CENTER)
    direction = normalize(display_vector(t, (0.0, 1.0, 0.0)))
    guard_bottom = display_point(t, (CX, GUARD_Y[0], CZ))
    pommel_top = display_point(t, (CX, POMMEL_Y[1], CZ))
    tip = display_point(t, (CX, TIP_Y, CZ))
    old = VANILLA["thirdperson_righthand"]
    old_dir = normalize(display_vector(old, OLD_BLADE_DIR))
    return {
        "grip": grip, "grip_error": math.dist(grip, FIST_CENTER_ITEM),
        "blade_dir": direction, "old_blade_dir": old_dir,
        "blade_angle_error": math.degrees(math.acos(max(-1.0, min(1.0, sum(direction[i] * old_dir[i] for i in range(3)))))),
        "flat_normal": display_vector(t, (1.0, 0.0, 0.0)),
        "guard_front_gap": guard_bottom[1] - 0.0,
        "pommel_behind_fist": -4.0 - pommel_top[1],
        "tip": tip,
        "old_handle_center": display_point(old, OLD_HANDLE_CENTER),
        "old_tip": display_point(old, (15.0, 15.3, 8.0)),
    }


def self_check() -> list[str]:
    errors = check_elements(build_elements())
    errors += silhouette_errors(build_elements())
    fit = fist_fit()
    if fit["grip_error"] > 0.01:
        errors.append(f"third-person grip {fit['grip_error']:.3f} px from the fist centre")
    if fit["blade_angle_error"] > 0.2:
        errors.append(f"third-person blade turned {fit['blade_angle_error']:.2f} deg from the PAL design")
    if abs(abs(fit["flat_normal"][0]) - 1.0) > 0.01:
        errors.append("third-person flat normal is no longer the arm's lateral axis")
    if not 0.0 < fit["guard_front_gap"] < 1.0:
        errors.append(f"guard sits {fit['guard_front_gap']:.2f} px from the fist front face")
    if not 0.2 < fit["pommel_behind_fist"] < 1.5:
        errors.append(f"pommel sits {fit['pommel_behind_fist']:.2f} px behind the fist")
    geo = geometry_contract()
    if not (geo["butt"]["y"][1] <= geo["handle"]["y"][0] and geo["handle"]["y"][1] <= geo["collar"]["y"][0]
            and geo["collar"]["y"][1] <= geo["head_base"][1] < geo["head_tip"][1]):
        errors.append("geometry contract parts are out of order")
    if not geo["handle"]["y"][0] < GRIP_CENTER_Y < geo["handle"]["y"][1]:
        errors.append("grip centre outside the handle")
    return errors


def report() -> None:
    display = build_display()
    for name, t in display.items():
        print(f"{name:22s} rot {t['rotation']} tr {t['translation']} scale {t['scale'][0]}")
    fit = fist_fit()
    for key, value in fit.items():
        if isinstance(value, tuple):
            value = "(" + ", ".join(f"{v:+.3f}" for v in value) + ")"
        elif isinstance(value, float):
            value = f"{value:.3f}"
        print(f"  {key:20s} {value}")
    print(f"  tip starts at y={TIP_START_Y:.3f}; total length {NEW_SWORD_LENGTH} px;"
          f" third-person length {NEW_SWORD_LENGTH * THIRD_PERSON_SCALE * 0.9375 / 16:.3f} block")


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
                    same = decode_png_rgba(current) == decode_png_rgba(data)
                except ValueError as exc:
                    errors.append(f"{rel}: {exc}")
                    continue
            else:
                same = current == data
            if not same:
                errors.append(f"{rel} differs from the generator; run python3 tools/gen_qingfeng_sword_model.py")
        if errors:
            for e in errors:
                print("ERROR:", e, file=sys.stderr)
            return 1
        print("OK Qingfeng sword model, texture and geometry contract match the generator and pass all self-checks")
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
