#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the Lingxiao spear (凌霄枪) 3D item model, its textures, icon and geometry contract.

Outputs (all under src/main/resources/assets/myvillage/):

* ``models/item/lingxiao_spear_3d.json``  element model of the spear with every display
  transform except ``gui``.
* ``models/item/lingxiao_spear.json``     ``neoforge:separate_transforms`` wrapper: ``base`` is
  the 3D model, the ``gui`` perspective keeps the 2D ``item/lingxiao_spear`` icon on
  ``minecraft:item/handheld``.
* ``textures/item/lingxiao_spear_model.png`` 128x128 texture of the 3D model (procedural).
* ``textures/item/lingxiao_spear.png``    64x64 inventory icon: the model's flat view laid on
  the diagonal like vanilla tools (procedural, anti-aliased alpha).
* ``combat/lingxiao_spear_geometry.json`` geometry contract read at runtime (model pixels).

Model frame (fixed by openspec/changes/add-lingxiao-spear/design.md): the spear runs along +Y
on the axis x = 8, z = 8, the spearhead's flat normal is X and its edge Z; butt end y = -16,
tip y = 32 (the full vanilla element range).  Contract names keep the sword's: ``pommel`` is
the butt cap, ``handle`` the shaft, ``guard`` the socket with its star ornament,
``blade_base``/``blade_tip`` the spearhead.  ``off_hand_grip_center`` is the leading hand.

The pennant, the 道 plaque and the tassels hang on the +Z side: in the third-person hold
derived below +Z points at the ground and -Y back toward the player, so a piece rotated 45°
from -Y toward +Z hangs down and back.  Contexts that show the item like the inventory sprite
(first person, ground, item frame, head) roll the model 180° about its axis so the same pieces
hang down there too.  The spearhead, the cyan inlays and gems carry NeoForge element light data
(``neoforge_data`` block/sky light 15), which NeoForge 21.1 bakes into the quad lightmap and
the item renderer keeps (max of baked and world light), so they stay lit in the dark.

Display transforms are derived like the Qingfeng sword's (tools/gen_qingfeng_sword_model.py,
whose helpers are imported): the weapon axis takes the direction the old sprite blade had, the
flat normal stays the arm's lateral axis, and the grip centre lands in the third-person fist.

Usage: python3 tools/gen_lingxiao_spear_model.py [--check] [--report]
  --check   exit non-zero if a committed output differs from what would be generated
  --report  print the display derivation, the grip fit and the element count
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
MODEL_PATH = ASSETS / "models/item/lingxiao_spear.json"
MODEL_3D_PATH = ASSETS / "models/item/lingxiao_spear_3d.json"
TEXTURE_PATH = ASSETS / "textures/item/lingxiao_spear_model.png"
ICON_PATH = ASSETS / "textures/item/lingxiao_spear.png"
GEOMETRY_PATH = ASSETS / "combat/lingxiao_spear_geometry.json"

MODEL_3D_ID = "myvillage:item/lingxiao_spear_3d"
TEXTURE_ID = "myvillage:item/lingxiao_spear_model"
ICON_TEXTURE_ID = "myvillage:item/lingxiao_spear"
TEXTURE_SIZE = 128
TEXELS_PER_UV = TEXTURE_SIZE / 16.0
ICON_SIZE = 64

_r = qf._r
_mix = qf._mix
_hex = qf._hex
_jitter = qf._jitter
_hash = qf._hash

# ---------------------------------------------------------------------------------------------
# Geometry (model pixels).  Axis x = 8, z = 8; +Y runs from the butt to the tip.
# ---------------------------------------------------------------------------------------------
CX = CZ = 8.0
BUTT_Y = -16.0
TIP_Y = 32.0
POMMEL_Y = (BUTT_Y, -13.4)            # butt cap 枪鐏
HANDLE_Y = (POMMEL_Y[1], 16.0)        # bare shaft between the butt cap and the socket
GUARD_Y = (HANDLE_Y[1], 23.4)         # socket collars, star ornament, blade root
BLADE_BASE_Y = GUARD_Y[1]
GRIP_CENTER_Y = -2.0                  # main (rear, right) hand
OFF_HAND_GRIP_Y = 11.0                # leading (left) hand
SHAFT_HALF = 0.5                      # 1 px shaft: 0.9 px in the 4 px fist
SHAFT_Y = (-13.6, 18.4)               # the element runs into the butt cap and the socket
# A hand needs this much bare shaft on each side of its grip point (fist half-depth / scale).
BARE_SHAFT_HALF = 2.4

# Gold collars with cyan inlays: three near the butt, one mid-shaft (handle), two below the head.
SHAFT_COLLARS = (("butt_collar_1", -11.7, -10.5), ("butt_collar_2", -8.7, -7.5),
                 ("butt_collar_3", -6.1, -4.9), ("mid_collar", 4.2, 5.4))
COLLAR_HALF, COLLAR_INLAY_HALF, COLLAR_INLAY_WIDTH, COLLAR_INLAY_HEIGHT = 0.8, 0.85, 0.3, 0.28
HEAD_COLLARS = (("head_collar_1", 16.0, 17.1, 0.9), ("head_collar_2", 18.2, 19.1, 0.84))

# Socket and star ornament, seen on the flat as an eight-pointed star: a stack of squares square
# to the axis (gold, glowing cyan ring, gold, glowing gem; on the diagonal views they read as the
# art's diamond), two stepped diagonal rays from its corners (boxes along Y rotated +-45 degrees)
# and stepped side points on Z.  The socket below and the blade root above are the axial points.
SOCKET_Y, SOCKET_HALF = (18.9, 20.2), 0.68
STAR_CENTER_Y = 21.3
STAR_LAYERS = (  # name, half size (Y and Z), half thickness (X), texture, glows
    ("star", 0.95, 0.5, "star_face", False),
    ("star_ring", 0.72, 0.56, "cyan_ring", True),
    ("star_inner", 0.5, 0.62, "gold_plain", False),
    ("star_gem", 0.3, 0.68, "cyan_gem", True),
)
STAR_RAYS = (  # name suffix, half length along the diagonal, half width, half thickness
    ("inner", 1.7, 0.36, 0.46),
    ("tip", 2.2, 0.17, 0.4),
)
STAR_SIDE_POINTS = (  # name suffix, half extent on Z, half height on Y, half thickness
    ("inner", 1.75, 0.42, 0.46),
    ("tip", 2.35, 0.19, 0.4),
)
BLADE_ROOT = dict(half_x=0.42, half_z=0.36, y=(22.5, BLADE_BASE_Y))

# Spearhead: a barbed leaf built from stacked steps (y0, y1, half width on Z).  Where a step is
# wider than the one below it, its flat underside is a backward-pointing barb; two barbs on each
# edge, the widest point about a third of the way up, then a long stepped needle to the tip.
# Each step is a thin pale-edge plate with a thicker saturated-cyan body plate inside it; a
# dark-gold spine (wide at the socket, narrowing to the tip) and dark navy fill beside it near the
# base sit on top.  Steps of the needle are near-white.
BLADE_STEPS = (
    (22.0, 23.1, 0.8),     # root, mostly inside the star
    (23.1, 23.9, 1.45),    # lower barb step
    (23.9, 24.8, 1.9),     # main barb: the widest point
    (24.8, 25.3, 1.45),
    (25.3, 25.7, 1.0),     # notch
    (25.7, 26.5, 1.7),     # second barb
    (26.5, 27.3, 1.2),
    (27.3, 28.4, 0.78),
    (28.4, 29.7, 0.5),     # needle
    (29.7, 31.0, 0.3),
    (31.0, TIP_Y, 0.14),
)
BLADE_EDGE = 0.28                      # pale rim width on the flat
BLADE_EDGE_HALF_X, BLADE_BODY_HALF_X = 0.1, 0.15
BLADE_NEEDLE_HALF = 0.5                # steps this narrow are near-white
BLADE_NAVY = ((22.0, 25.3, 0.72, 0.24), (25.3, 26.5, 0.46, 0.24))   # y0, y1, half width, half x
BLADE_SPINE = ((22.0, 25.3, 0.3, 0.33), (25.3, 27.3, 0.21, 0.33), (27.3, 29.7, 0.13, 0.33),
               (29.7, 31.3, 0.07, 0.33))

# Butt cap 枪鐏: a gold block with a hooked flange on -Z (out, down, curling back with a notch)
# and a stepped foot on +Z under the tassel.
CAP_PARTS = (  # name, half (X and Z), y range, side texture, cap texture
    ("butt_cap_rim", 1.0, (-13.95, POMMEL_Y[1]), "gold_band", "gold_top"),
    ("butt_cap", 0.88, (-15.5, -13.95), "cap_face", "gold_top"),
    ("butt_cap_foot_rim", 1.0, (-15.78, -15.5), "gold_band", "gold_top"),
    ("butt_cap_foot", 0.62, (BUTT_Y, -15.78), "gold_dark", "gold_dark"),
)
CAP_PROFILE = (  # name, half x, (z0, z1) relative to the axis, (y0, y1)
    ("butt_hook_arm", 0.55, (-1.75, -0.85), (-14.35, -13.75)),
    ("butt_hook_drop", 0.55, (-1.75, -1.3), (-15.3, -14.35)),
    ("butt_hook_curl", 0.55, (-1.75, -1.1), (-15.3, -14.95)),
    ("butt_step_low", 0.6, (0.85, 1.3), (-15.78, -15.1)),
    ("butt_step_high", 0.6, (0.85, 1.12), (-15.1, -14.7)),
)
CAP_GEM = dict(half_out=0.93, half_in=0.38, y=(-15.1, -14.45))

# Contract values handed to the pose and first-person workers (frozen 2026-10-02; the hook and
# star points are ornament outside the pommel/guard half sizes, which describe the cap body and
# the socket's star extent).
CONTRACT_POMMEL_HALF = 1.0
CONTRACT_GUARD_HALF_WIDTH, CONTRACT_GUARD_HALF_THICKNESS = 2.35, 0.68
CONTRACT_BLADE_HALF_WIDTH, CONTRACT_BLADE_HALF_THICKNESS = 1.9, 0.35
CONTRACT_TAPER_START_Y = 25.6

# Hanging pieces (+Z side).  Each hangs from an anchor point (x, y, z) and is built along -Y
# (or along +Z for the butt tassel), then rotated about X around the anchor.
PENNANT_ANCHOR = (7.9, 16.4, 9.4)
PENNANT_SIZE = dict(length=9.5, half_width=1.5, half_thickness=0.06)
PENNANT_ANGLE = -45.0                 # -Y toward +Z
PENNANT_CORD = dict(z=(8.85, 9.45), y=(16.3, 16.6), half_x=0.12)
PLAQUE_ANCHOR = (8.75, 20.4, 8.9)
PLAQUE_ANGLE = -45.0
PLAQUE_CHAIN, PLAQUE_SIZE = 1.3, dict(length=1.9, half_width=0.75, half_thickness=0.15)
BUTT_TASSEL_ANCHOR = (CX, -14.1, CZ + 1.0)
BUTT_TASSEL_ANGLE = 22.5              # +Z toward -Y

TAN_22_5 = math.tan(math.radians(22.5))
FACES = qf.FACES
GLOW = {"block_light": 15, "sky_light": 15}

# ---------------------------------------------------------------------------------------------
# Texture layout: name -> (x0, y0, x1, y1) texel rectangle on the 128x128 sheet.
# ---------------------------------------------------------------------------------------------
REGIONS = {
    "shaft": (0, 0, 4, 128),
    "blade_edge": (8, 0, 12, 16),
    "blade_body": (12, 0, 20, 16),
    "blade_needle": (20, 0, 24, 16),
    "blade_navy": (24, 0, 30, 16),
    "blade_spine": (30, 0, 34, 16),
    "plaque_icon": (24, 54, 34, 66),
    "gold_band": (8, 20, 16, 28),
    "gold_top": (16, 20, 24, 28),
    "gold_plain": (24, 20, 32, 28),
    "gold_dark": (32, 20, 36, 24),
    "cap_face": (36, 20, 44, 28),
    "cyan_inlay": (44, 20, 48, 24),
    "cyan_gem": (48, 20, 54, 26),
    "cyan_ring": (54, 20, 60, 26),
    "star_face": (60, 20, 74, 34),
    "pennant": (8, 40, 23, 88),
    "plaque": (24, 40, 34, 52),
    "tassel_v": (36, 40, 42, 52),
    "tassel_h": (44, 40, 56, 46),
    "chain": (58, 40, 60, 46),
    "cloth_edge": (62, 40, 64, 42),
}

NAVY = {"black": _hex("#0d111b"), "dark": _hex("#151b2a"), "base": _hex("#1d2538"), "mid": _hex("#2a3550"),
        "hi": _hex("#43527a"), "sheen": _hex("#5d6f9c")}
GOLD = {"hi": _hex("#fbeebb"), "light": _hex("#ecd283"), "base": _hex("#d4ad5a"), "shade": _hex("#a8803d"),
        "dark": _hex("#6b4a1d")}
CYAN = {"white": _hex("#f1fdff"), "pale": _hex("#c4f3fd"), "light": _hex("#8fe6fa"), "base": _hex("#55d0f2"),
        "mid": _hex("#2fb0e3"), "deep": _hex("#1a86c2"), "dark": _hex("#0f4f7c")}
CLOTH = {"white": _hex("#f5f7f9"), "shade": _hex("#d8dee8"), "fold": _hex("#c2cbd9"), "blue": _hex("#3d6db4"),
         "blue_dark": _hex("#22447d"), "blue_light": _hex("#9fc4ec")}
CREAM, INK = _hex("#efdcaa"), _hex("#1b2131")


# 道 on the plaque (8 x 10, '#' = ink).
DAO_GLYPH = (
    "....#.#.",
    "#..#####",
    ".#...#..",
    "...#####",
    "##.#...#",
    ".#.#####",
    ".#.#...#",
    ".#.#####",
    "#.#.....",
    "...#####",
)

# Butt cap face: gold with a 回 key pattern.
HUI_PATTERN = (
    "########",
    "#......#",
    "#.####.#",
    "#.#..#.#",
    "#.#..#.#",
    "#.####.#",
    "#......#",
    "########",
)


def _shaft_ornament(y: float) -> bool:
    """Shaft heights with the black-and-cyan checker (beside the butt collars and the socket)."""
    spans = ((POMMEL_Y[1], SHAFT_COLLARS[2][2] + 0.6), (HEAD_COLLARS[0][2], HEAD_COLLARS[1][1]))
    return any(a <= y <= b for a, b in spans)


def _pennant_alpha(i: int, j: int, w: int, h: int) -> bool:
    c = (w - 1) / 2.0
    if j < 3:
        half = 1.0
    elif j < 9:
        half = 1.0 + (j - 3) * (c - 1.0) / 6.0
    else:
        half = c + 0.01
    if abs(i - c) > half + 0.01:
        return False
    if j >= h - 6:  # swallow tail with a stepped notch
        notch = (j - (h - 6)) * 0.9
        if abs(i - c) < notch:
            return False
    return True


def _yin_yang(dx: float, dy: float, r: float) -> str | None:
    """'dark' / 'light' / 'rim' / None for a point relative to the emblem centre (y down)."""
    d = math.hypot(dx, dy)
    if d > r + 0.5:
        return None
    if d > r - 0.6:
        return "rim"
    up, down = math.hypot(dx, dy + r / 2), math.hypot(dx, dy - r / 2)
    if up < r * 0.18:
        return "dark"
    if down < r * 0.18:
        return "light"
    if up < r / 2:
        return "light"
    if down < r / 2:
        return "dark"
    return "dark" if dx < 0 else "light"


def paint_region(name: str, i: int, j: int, w: int, h: int, x: int, y: int):
    """RGBA of texel (i, j) inside region ``name`` of size w x h (sheet position x, y)."""
    if name == "shaft":
        my = SHAFT_Y[1] - (j + 0.5) * (SHAFT_Y[1] - SHAFT_Y[0]) / h
        base = (NAVY["mid"], NAVY["hi"], NAVY["base"], NAVY["dark"])[i]
        if _shaft_ornament(my) and (3 * i + j) % 5 == 0:
            base = CYAN["mid"] if (i + j) % 2 else CYAN["deep"]
        elif _hash(x * 7, y * 3) > 0.8:
            base = _mix(base, NAVY["sheen"], 0.4)
        return (*_jitter(base, 2.0, x, y), 255)
    if name == "blade_edge":
        return (*_jitter(CYAN["white"] if i in (0, w - 1) else CYAN["pale"], 2.0, x, y), 255)
    if name == "blade_body":
        return (*_jitter(_mix(CYAN["base"], CYAN["mid"], 0.35), 4.0, x, y), 255)
    if name == "blade_needle":
        return (*_jitter(CYAN["pale"] if i in (0, w - 1) else CYAN["white"], 2.0, x, y), 255)
    if name == "blade_navy":
        c = NAVY["base"] if (i + 2 * j) % 5 else NAVY["hi"]
        return (*_jitter(c, 2.0, x, y), 255)
    if name == "blade_spine":
        return (*_jitter(GOLD["shade"] if i in (1, 2) else GOLD["dark"], 2.0, x, y), 255)
    if name == "plaque_icon":  # the plaque as the 64 px icon sees it: gold frame, cream, one ink mark
        if i in (0, 1, w - 2, w - 1) or j in (0, 1, h - 2, h - 1):
            return (*GOLD["light"], 255)
        return (*(INK if 4 <= i <= 5 and 4 <= j <= 7 else CREAM), 255)
    if name == "gold_band":
        rows = (GOLD["hi"], GOLD["light"], GOLD["base"], GOLD["base"], GOLD["base"], GOLD["base"], GOLD["shade"],
                GOLD["dark"])
        return (*_jitter(rows[j], 2.0, x, y), 255)
    if name == "gold_top":
        edge = i in (0, w - 1) or j in (0, h - 1)
        return (*_jitter(GOLD["light"] if edge else GOLD["base"], 2.0, x, y), 255)
    if name == "gold_plain":
        return (*_jitter(GOLD["hi"] if (i + j) < 3 else GOLD["light"], 2.0, x, y), 255)
    if name == "gold_dark":
        return (*GOLD["shade"], 255)
    if name == "cap_face":
        c = GOLD["shade"] if HUI_PATTERN[j][i] == "#" else GOLD["light"]
        return (*_jitter(c, 2.0, x, y), 255)
    if name == "cyan_inlay":
        return (*(CYAN["pale"] if (i, j) == (1, 1) else CYAN["base"]), 255)
    if name == "cyan_gem":
        d = abs(i - 2.5) + abs(j - 2.5)
        c = CYAN["white"] if (i, j) in ((2, 2), (2, 1)) else CYAN["light"] if d < 2 else CYAN["mid"]
        return (*c, 255)
    if name == "cyan_ring":
        return (*(CYAN["light"] if (i + j) % 2 else CYAN["base"]), 255)
    if name == "star_face":
        ring = min(i, j, w - 1 - i, h - 1 - j)
        c = GOLD["dark"] if ring == 0 else GOLD["hi"] if ring == 1 else GOLD["light"] if ring < 3 else GOLD["base"]
        return (*_jitter(c, 2.0, x, y), 255)
    if name == "pennant":
        if not _pennant_alpha(i, j, w, h):
            return (0, 0, 0, 0)
        c = CLOTH["white"]
        if i in (2, w - 3) and j > 8:
            c = CLOTH["shade"]
        part = _yin_yang(i - (w - 1) / 2.0, j - 19.0, 5.0)
        if part == "rim" or part == "dark":
            c = CLOTH["blue"]
        if 31 <= j <= 33 and (i + j) % 2 == 0:
            c = CLOTH["blue_light"]
        if 25 <= j <= 28 and abs(i - (w - 1) / 2.0) <= (1 if j < 28 else 2):
            c = CLOTH["blue"]  # tasselled knot below the emblem
        if j >= h - 6:
            c = _mix(c, CLOTH["blue_light"], 0.55)
        return (*c, 255)
    if name == "plaque":
        if i in (0, w - 1) or j in (0, h - 1):
            return (*(GOLD["light"] if j == 0 or i == 0 else GOLD["shade"]), 255)
        return (*(INK if DAO_GLYPH[j - 1][i - 1] == "#" else CREAM), 255)
    if name == "tassel_v":  # strands run down (j); fringe at the bottom
        if j >= h - 2 and (i + j) % 2:
            return (0, 0, 0, 0)
        c = CYAN["light"] if i % 2 == 0 else CYAN["mid"]
        if j < 2:
            c = CYAN["deep"]
        return (*c, 255)
    if name == "tassel_h":  # strands run along i; fringe at the far (right) end
        if i >= w - 2 and (i + j) % 2:
            return (0, 0, 0, 0)
        c = CYAN["light"] if j % 2 == 0 else CYAN["mid"]
        if i < 2:
            c = CYAN["deep"]
        return (*c, 255)
    if name == "chain":
        return (*(GOLD["light"] if j % 2 == 0 else GOLD["dark"]), 255)
    if name == "cloth_edge":
        return (*CLOTH["shade"], 255)
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

def uv(region: str, flip_u: bool = False, sub: tuple[float, float, float, float] | None = None):
    x0, y0, x1, y1 = REGIONS[region]
    if sub:
        fx0, fy0, fx1, fy1 = sub
        x0, x1 = x0 + (x1 - x0) * fx0, x0 + (x1 - x0) * fx1
        y0, y1 = y0 + (y1 - y0) * fy0, y0 + (y1 - y0) * fy1
    u0, v0, u1, v1 = (_r(c / TEXELS_PER_UV) for c in (x0, y0, x1, y1))
    return [u1, v0, u0, v1] if flip_u else [u0, v0, u1, v1]


def box(name: str, lo, hi, faces: dict, rotation: dict | None = None, glow: bool = False) -> dict:
    element = {"name": name, "from": [_r(c) for c in lo], "to": [_r(c) for c in hi]}
    if rotation:
        element["rotation"] = {"angle": rotation["angle"], "axis": rotation["axis"],
                               "origin": [_r(c) for c in rotation["origin"]]}
    if glow:
        element["neoforge_data"] = dict(GLOW)
    element["faces"] = {face: {"uv": faces[face], "texture": "#spear"} for face in FACES if face in faces}
    return element


def centered(name, half_x, half_z, y0, y1, faces, rotation=None, glow=False, cx=CX, cz=CZ):
    return box(name, (cx - half_x, y0, cz - half_z), (cx + half_x, y1, cz + half_z), faces, rotation, glow)


def all_faces(side: str, cap: str | None = None, side_z: str | None = None) -> dict:
    out = {"east": uv(side), "west": uv(side), "north": uv(side_z or side), "south": uv(side_z or side)}
    if cap:
        out.update(up=uv(cap), down=uv(cap))
    return out


def blade_half_width(y: float) -> float:
    """Half-width of the stepped spearhead outline at height ``y`` (0 outside it)."""
    for y0, y1, w in BLADE_STEPS:
        if y0 <= y < y1 or (y == TIP_Y and y1 == TIP_Y):
            return w
    return 0.0


def build_blade() -> list[dict]:
    els = []
    for k, (y0, y1, w) in enumerate(BLADE_STEPS):
        below = BLADE_STEPS[k - 1][2] if k else 0.0
        above = BLADE_STEPS[k + 1][2] if k + 1 < len(BLADE_STEPS) else 0.0
        needle = w <= BLADE_NEEDLE_HALF
        edge = uv("blade_edge")
        faces = {"east": edge, "west": edge, "north": edge, "south": edge}
        if above < w:
            faces["up"] = edge
        if below < w and k:
            faces["down"] = edge
        els.append(centered(f"blade_step_{k}", BLADE_EDGE_HALF_X, w, y0, y1, faces, glow=True))
        body_w = w - BLADE_EDGE if not needle else w * 0.45
        body = uv("blade_needle" if needle else "blade_body")
        els.append(centered(f"blade_body_{k}", BLADE_BODY_HALF_X, body_w, y0, y1,
                            {"east": body, "west": body}, glow=True))
    for k, (y0, y1, w, t) in enumerate(BLADE_NAVY):
        side = uv("blade_navy")
        els.append(centered(f"blade_navy_{k}", t, w, y0, y1, {"east": side, "west": side, "up": side}))
    for k, (y0, y1, w, t) in enumerate(BLADE_SPINE):
        side = uv("blade_spine")
        els.append(centered(f"blade_spine_{k}", t, w, y0, y1,
                            {"east": side, "west": side, "north": side, "south": side, "up": side}))
    return els


def build_socket() -> list[dict]:
    els = []
    for name, y0, y1, half in HEAD_COLLARS:
        els += collar(name, y0, y1, half, half + 0.05)
    els.append(centered("socket", SOCKET_HALF, SOCKET_HALF, SOCKET_Y[0], SOCKET_Y[1],
                        all_faces("gold_band", "gold_top")))
    origin = (CX, STAR_CENTER_Y, CZ)
    for name, half, half_x, tex, glow in STAR_LAYERS:
        faces = {"east": uv(tex), "west": uv(tex), "north": uv("gold_band"), "south": uv("gold_band"),
                 "up": uv("gold_top"), "down": uv("gold_band")}
        els.append(centered(name, half_x, half, STAR_CENTER_Y - half, STAR_CENTER_Y + half, faces, glow=glow))
    for suffix, half_len, half_w, half_x in STAR_RAYS:
        for angle, side in ((45, "a"), (-45, "b")):
            els.append(centered(f"star_ray_{side}_{suffix}", half_x, half_w, STAR_CENTER_Y - half_len,
                                STAR_CENTER_Y + half_len, all_faces("gold_plain", "gold_top", "gold_band"),
                                rotation={"angle": angle, "axis": "x", "origin": origin}))
    for suffix, half_z, half_y, half_x in STAR_SIDE_POINTS:
        els.append(centered(f"star_points_{suffix}", half_x, half_z, STAR_CENTER_Y - half_y,
                            STAR_CENTER_Y + half_y, all_faces("gold_plain", "gold_top", "gold_dark")))
    r = BLADE_ROOT
    els.append(centered("blade_root", r["half_x"], r["half_z"], r["y"][0], r["y"][1],
                        all_faces("gold_plain", "gold_top")))
    return els


def collar(name: str, y0: float, y1: float, half: float = COLLAR_HALF, inlay_half: float = COLLAR_INLAY_HALF):
    mid, ih = (y0 + y1) / 2.0, COLLAR_INLAY_HEIGHT
    inlay = uv("cyan_inlay")
    return [
        centered(name, half, half, y0, y1, all_faces("gold_band", "gold_top")),
        centered(f"{name}_inlay_x", inlay_half, COLLAR_INLAY_WIDTH, mid - ih, mid + ih,
                 {"east": inlay, "west": inlay}, glow=True),
        centered(f"{name}_inlay_z", COLLAR_INLAY_WIDTH, inlay_half, mid - ih, mid + ih,
                 {"north": inlay, "south": inlay}, glow=True),
    ]


def build_shaft() -> list[dict]:
    side = uv("shaft")
    els = [centered("shaft", SHAFT_HALF, SHAFT_HALF, SHAFT_Y[0], SHAFT_Y[1],
                    {"east": side, "west": side, "north": side, "south": side})]
    for name, y0, y1 in SHAFT_COLLARS:
        els += collar(name, y0, y1)
    return els


def build_butt() -> list[dict]:
    els = []
    for name, half, (y0, y1), side, cap in CAP_PARTS:
        els.append(centered(name, half, half, y0, y1, all_faces(side, cap)))
    for name, half_x, (z0, z1), (y0, y1) in CAP_PROFILE:
        els.append(box(name, (CX - half_x, y0, CZ + z0), (CX + half_x, y1, CZ + z1),
                       all_faces("gold_band", "gold_top", "gold_plain")))
    g = CAP_GEM
    gem = uv("cyan_gem")
    els.append(centered("butt_gem_x", g["half_out"], g["half_in"], g["y"][0], g["y"][1],
                        {"east": gem, "west": gem}, glow=True))
    els.append(centered("butt_gem_z", g["half_in"], g["half_out"], g["y"][0], g["y"][1],
                        {"north": gem, "south": gem}, glow=True))
    # Short cyan tassel on a chain, hanging from the cap's +Z face (built along +Z).
    ax, ay, az = BUTT_TASSEL_ANCHOR
    rot = {"angle": BUTT_TASSEL_ANGLE, "axis": "x", "origin": BUTT_TASSEL_ANCHOR}
    parts = (("butt_chain", 0.0, 0.9, 0.1, "chain", False), ("butt_bead", 0.9, 1.45, 0.28, "cyan_gem", True),
             ("butt_tassel_cap", 1.45, 1.8, 0.34, "gold_band", False), ("butt_tassel", 1.8, 3.9, 0.4, "tassel_h", False))
    for name, z0, z1, half, tex, glow in parts:
        if tex == "tassel_h":
            faces = {"east": uv(tex, flip_u=True), "west": uv(tex), "up": uv(tex), "down": uv(tex),
                     "south": uv("tassel_v", sub=(0, 0.8, 1, 1))}
        else:
            faces = all_faces(tex, tex)
        els.append(box(name, (ax - half, ay - half, az + z0), (ax + half, ay + half, az + z1), faces, rot, glow))
    return els


def hanging(name: str, anchor, angle: float, y_from: float, y_to: float, half_x: float, half_z: float,
            faces: dict, glow: bool = False, dx: float = 0.0) -> dict:
    """A piece hanging from ``anchor``: built along -Y between anchor y - y_to and y - y_from."""
    ax, ay, az = anchor
    return box(name, (ax + dx - half_x, ay - y_to, az - half_z), (ax + dx + half_x, ay - y_from, az + half_z),
               faces, {"angle": angle, "axis": "x", "origin": anchor}, glow)


def build_hanging() -> list[dict]:
    els = []
    c = PENNANT_CORD
    els.append(box("pennant_cord", (PENNANT_ANCHOR[0] - c["half_x"], c["y"][0], c["z"][0]),
                   (PENNANT_ANCHOR[0] + c["half_x"], c["y"][1], c["z"][1]), all_faces("chain", "gold_top")))
    p = PENNANT_SIZE
    cloth = uv("pennant")
    els.append(hanging("pennant", PENNANT_ANCHOR, PENNANT_ANGLE, 0.0, p["length"], p["half_thickness"],
                       p["half_width"], {"east": cloth, "west": cloth}))
    els.append(hanging("pennant_cap", PENNANT_ANCHOR, PENNANT_ANGLE, -0.15, 0.45, 0.2, 0.32,
                       all_faces("gold_band", "gold_top")))
    # 道 plaque on a chain with a bead and a cyan tassel.
    q = PLAQUE_SIZE
    y = 0.0
    parts = [("plaque_chain", PLAQUE_CHAIN, 0.09, 0.09, all_faces("chain"), False),
             ("plaque", q["length"], q["half_thickness"], q["half_width"],
              {"east": uv("plaque"), "west": uv("plaque"), "north": uv("gold_dark"), "south": uv("gold_dark"),
               "up": uv("gold_top"), "down": uv("gold_top")}, False),
             ("plaque_bead", 0.5, 0.25, 0.25, all_faces("cyan_gem", "cyan_gem"), True),
             ("plaque_tassel_cap", 0.3, 0.3, 0.3, all_faces("gold_band", "gold_top"), False),
             ("plaque_tassel", 2.1, 0.38, 0.38,
              {**all_faces("tassel_v"), "down": uv("tassel_v", sub=(0, 0.8, 1, 1))}, False)]
    for name, length, half_x, half_z, faces, glow in parts:
        els.append(hanging(name, PLAQUE_ANCHOR, PLAQUE_ANGLE, y, y + length, half_x, half_z, faces, glow))
        y += length
    return els


def build_elements() -> list[dict]:
    return build_shaft() + build_butt() + build_socket() + build_blade() + build_hanging()


# ---------------------------------------------------------------------------------------------
# Display transforms
# ---------------------------------------------------------------------------------------------
THIRD_PERSON_SCALE = 0.9
FIRST_PERSON_SCALE = 0.45
FIRST_PERSON_TILT = 20.0               # lean the head toward the crosshair so it stays on screen
GROUND_SCALE = 0.34
FIXED_SCALE = 0.45
HEAD_SCALE = 0.5
GRIP_CENTER = (CX, GRIP_CENTER_Y, CZ)
OFF_HAND_GRIP_CENTER = (CX, OFF_HAND_GRIP_Y, CZ)
AXIS_CENTER = (CX, (BUTT_Y + TIP_Y) / 2.0, CZ)
ROLL_180 = [[-1, 0, 0], [0, 1, 0], [0, 0, -1]]  # half turn about the weapon axis (model Y)


def derive(context: str, anchor_new, scale: float, anchor_target=None, anchor_old=None, roll: bool = False,
           tilt: float = 0.0) -> dict:
    """``qf.derive`` with an explicit scale, an optional half turn about the spear axis and an
    optional tilt (degrees about the display Z axis, applied after the derived rotation)."""
    if not roll and not tilt:
        return qf.derive(context, anchor_new, anchor_target=anchor_target, anchor_old=anchor_old, scale=scale)
    old = qf.VANILLA[context]
    r_new = qf.mm(qf.euler_xyz(old["rotation"]), qf.axis_map())
    if roll:
        r_new = qf.mm(r_new, ROLL_180)
    if tilt:
        r_new = qf.mm(qf._rot("z", tilt), r_new)
    rotation = tuple(round(a, 2) + 0.0 for a in qf.to_euler_xyz(r_new))
    target = anchor_target if anchor_target is not None else qf.display_point(old, anchor_old)
    offset = qf.mv(qf.euler_xyz(rotation), tuple(scale * (anchor_new[i] - 8.0) for i in range(3)))
    translation = tuple(round(target[i] - offset[i], 3) + 0.0 for i in range(3))
    if any(abs(t) > 80.0 for t in translation):
        raise ValueError(f"{context} translation {translation} exceeds the 5-block clamp")
    return {"rotation": rotation, "translation": translation, "scale": (scale, scale, scale)}


def build_display() -> dict:
    third = derive("thirdperson_righthand", GRIP_CENTER, THIRD_PERSON_SCALE, anchor_target=qf.FIST_CENTER_ITEM)
    first = derive("firstperson_righthand", GRIP_CENTER, FIRST_PERSON_SCALE, anchor_old=qf.OLD_HANDLE_CENTER,
                   roll=True, tilt=FIRST_PERSON_TILT)
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": qf.mirror(third),
        "firstperson_righthand": first,
        "firstperson_lefthand": qf.mirror(first),
        "ground": derive("ground", AXIS_CENTER, GROUND_SCALE, anchor_old=qf.OLD_SPRITE_CENTER, roll=True),
        "head": derive("head", AXIS_CENTER, HEAD_SCALE, anchor_old=qf.OLD_SPRITE_CENTER, roll=True),
        "fixed": derive("fixed", AXIS_CENTER, FIXED_SCALE, anchor_old=qf.OLD_SPRITE_CENTER, roll=True),
    }


# ---------------------------------------------------------------------------------------------
# Inventory icon: the flat view (from -X, so the +Z hanging side is down) on the diagonal.
# ---------------------------------------------------------------------------------------------
ICON_BUTT = (3.6, 60.4)        # icon pixel coordinates (x right, y down)
ICON_TIP = (60.6, 3.4)
ICON_WIDTH_GAIN = 1.45         # widen the profile across the axis so a 1 px shaft still reads
ICON_SAMPLES = 4               # supersamples per axis
ICON_MIN_COVER = 6             # of 16 samples for an opaque pixel
ICON_FACE_OVERRIDES = {"plaque": "plaque_icon"}   # the glyph would read as a dark blob at 64 px


def _icon_faces(elements: list[dict]):
    """West faces as (depth x, inverse rotation, rect (y0, y1, z0, z1), uv, tex image)."""
    faces = []
    for e in elements:
        face = e["faces"].get("west")
        if face is None:
            continue
        rot = e.get("rotation")
        if rot and rot["axis"] != "x":
            continue
        lo, hi = e["from"], e["to"]
        angle = math.radians(rot["angle"]) if rot else 0.0
        origin = rot["origin"] if rot else (CX, 0.0, CZ)
        face_uv = uv(ICON_FACE_OVERRIDES[e["name"]]) if e["name"] in ICON_FACE_OVERRIDES else face["uv"]
        faces.append((lo[0], angle, origin, (lo[1], hi[1], lo[2], hi[2]), face_uv))
    faces.sort(key=lambda f: f[0])  # smallest x is nearest to a viewer on -X
    return faces


def _texel_index(c: float, a: float, b: float) -> int:
    """Texel column/row of UV coordinate ``c`` on a face spanning UV a..b (either order)."""
    lo, hi = min(a, b) * TEXELS_PER_UV, max(a, b) * TEXELS_PER_UV
    return int(min(max(math.floor(c * TEXELS_PER_UV), math.floor(lo)), math.ceil(hi) - 1))


def _icon_sample(faces, pixels, y: float, z: float):
    for depth, angle, origin, (y0, y1, z0, z1), face_uv in faces:
        if angle:
            dy, dz = y - origin[1], z - origin[2]
            c, s = math.cos(-angle), math.sin(-angle)
            py, pz = origin[1] + dy * c - dz * s, origin[2] + dy * s + dz * c
        else:
            py, pz = y, z
        if not (y0 <= py <= y1 and z0 <= pz <= z1):
            continue
        # west face: u runs with +Z, v runs from the top (y1) down.
        fu = (pz - z0) / (z1 - z0) if z1 > z0 else 0.0
        fv = (y1 - py) / (y1 - y0) if y1 > y0 else 0.0
        u = face_uv[0] + (face_uv[2] - face_uv[0]) * fu
        v = face_uv[1] + (face_uv[3] - face_uv[1]) * fv
        tx = _texel_index(u, face_uv[0], face_uv[2])
        ty = _texel_index(v, face_uv[1], face_uv[3])
        texel = pixels[ty][tx]
        if texel[3] < 26:
            continue
        return texel
    return None


def render_icon(elements: list[dict] | None = None, texture=None) -> list[list[tuple[int, int, int, int]]]:
    elements = elements if elements is not None else build_elements()
    texture = texture if texture is not None else render_texture()
    faces = _icon_faces(elements)
    bx, by = ICON_BUTT
    ux, uy = ICON_TIP[0] - bx, ICON_TIP[1] - by
    length = math.hypot(ux, uy)
    ux, uy = ux / length, uy / length
    nx, ny = -uy, ux                                   # screen down-right = model +Z (seen from -X)
    along = length / (TIP_Y - BUTT_Y)                  # icon px per model px along the axis
    across = along * ICON_WIDTH_GAIN
    out = []
    n = ICON_SAMPLES
    # Crisp pixel art: a pixel is opaque when enough of it is covered and takes the colour of
    # its most central covered sample (averaging would blur the ornaments into mud).
    order = sorted(((si, sj) for sj in range(n) for si in range(n)),
                   key=lambda s: ((s[0] + 0.5 - n / 2) ** 2 + (s[1] + 0.5 - n / 2) ** 2, s[1], s[0]))
    for py in range(ICON_SIZE):
        row = []
        for px in range(ICON_SIZE):
            colour, hits = None, 0
            for si, sj in order:
                sx, sy = px + (si + 0.5) / n - bx, py + (sj + 0.5) / n - by
                y = BUTT_Y + (sx * ux + sy * uy) / along
                z = CZ + (sx * nx + sy * ny) / across
                if y < BUTT_Y - 0.5 or y > TIP_Y + 0.5:
                    continue
                texel = _icon_sample(faces, texture, y, z)
                if texel is not None:
                    hits += 1
                    colour = colour or texel[:3]
            row.append((*colour, 255) if hits >= ICON_MIN_COVER else (0, 0, 0, 0))
        out.append(row)
    return _icon_outline(out)


def _icon_outline(pixels):
    """Darken the outermost opaque pixels a little so the spear reads on light slots."""
    out = [list(row) for row in pixels]
    for y in range(ICON_SIZE):
        for x in range(ICON_SIZE):
            r, g, b, a = pixels[y][x]
            if a == 0:
                continue
            edge = any(not (0 <= x + dx < ICON_SIZE and 0 <= y + dy < ICON_SIZE) or pixels[y + dy][x + dx][3] == 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                out[y][x] = (int(r * 0.72), int(g * 0.72), int(b * 0.78), a)
    return out


# ---------------------------------------------------------------------------------------------
# Documents
# ---------------------------------------------------------------------------------------------

def _json_display(display: dict) -> dict:
    return {k: {"rotation": [_r(c) for c in v["rotation"]], "translation": [_r(c) for c in v["translation"]],
                "scale": [_r(c) for c in v["scale"]]} for k, v in display.items()}


def build_model_3d() -> dict:
    return {
        "credit": "MyVillage original procedural spear (tools/gen_lingxiao_spear_model.py)",
        "texture_size": [TEXTURE_SIZE, TEXTURE_SIZE],
        "textures": {"spear": TEXTURE_ID, "particle": ICON_TEXTURE_ID},
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
    return {
        "format": 1,
        "units": "model_pixels",
        "generator": "tools/gen_lingxiao_spear_model.py",
        "model": MODEL_3D_ID,
        "axes": {"blade": "+y", "flat_normal": "x", "edge": "z", "center_x": CX, "center_z": CZ},
        "grip_center": [CX, GRIP_CENTER_Y, CZ],
        "off_hand_grip_center": [CX, OFF_HAND_GRIP_Y, CZ],
        "handle": {"y": list(HANDLE_Y), "half_width": SHAFT_HALF, "half_thickness": SHAFT_HALF},
        "guard": {"y": list(GUARD_Y), "half_width": CONTRACT_GUARD_HALF_WIDTH,
                  "half_thickness": CONTRACT_GUARD_HALF_THICKNESS},
        "pommel": {"y": list(POMMEL_Y), "half_width": CONTRACT_POMMEL_HALF, "half_thickness": CONTRACT_POMMEL_HALF},
        "blade": {"half_width": CONTRACT_BLADE_HALF_WIDTH, "half_thickness": CONTRACT_BLADE_HALF_THICKNESS,
                  "ridge_half_thickness": CONTRACT_BLADE_HALF_THICKNESS, "taper_start_y": CONTRACT_TAPER_START_Y},
        "blade_base": [CX, BLADE_BASE_Y, CZ],
        "blade_tip": [CX, TIP_Y, CZ],
        "edge_axis": [0.0, 0.0, 1.0],
        "flat_axis": [1.0, 0.0, 0.0],
        "overall_y": [BUTT_Y, TIP_Y],
    }


def outputs() -> dict[Path, bytes]:
    texture = render_texture()
    return {
        MODEL_3D_PATH: qf.render_model_json(build_model_3d()).encode(),
        MODEL_PATH: qf.render_model_json(build_model()).encode(),
        GEOMETRY_PATH: qf.render_model_json(geometry_contract()).encode(),
        TEXTURE_PATH: qf.encode_png(texture),
        ICON_PATH: qf.encode_png(render_icon(build_elements(), texture)),
    }


# ---------------------------------------------------------------------------------------------
# Self-checks
# ---------------------------------------------------------------------------------------------

def check_elements(elements: list[dict]) -> list[str]:
    """Vanilla BlockElement limits plus this model's own: rotated corners stay in range too."""
    errors = qf.check_elements(elements)
    names = set()
    for e in elements:
        if e["name"] in names:
            errors.append(f"{e['name']}: duplicate element name")
        names.add(e["name"])
        rot = e.get("rotation")
        if rot and rot["angle"] == 0:
            errors.append(f"{e['name']}: zero rotation")
        if rot and rot["axis"] != "x":
            errors.append(f"{e['name']}: rotation axis {rot['axis']} (the icon renderer handles x only)")
        for p in qf.element_corners(e):
            if any(not -16.0 <= c <= 32.0 for c in p):
                errors.append(f"{e['name']}: rotated corner {tuple(round(c, 3) for c in p)} outside [-16, 32]")
                break
        for face, data in e["faces"].items():
            if data["texture"] != "#spear":
                errors.append(f"{e['name']}.{face}: texture {data['texture']}")
    return errors


def element_bounds(e: dict):
    corners = qf.element_corners(e)
    return tuple(min(p[i] for p in corners) for i in range(3)), tuple(max(p[i] for p in corners) for i in range(3))


def element_samples(e: dict, n: int = 6):
    """Points filling an element's box (after its rotation), n per axis."""
    lo, hi = e["from"], e["to"]
    rot = e.get("rotation")
    m = qf._rot(rot["axis"], rot["angle"]) if rot else None
    out = []
    for i in range(n):
        for j in range(n):
            for k in range(n):
                p = tuple(lo[a] + (hi[a] - lo[a]) * (idx / (n - 1)) for a, idx in zip(range(3), (i, j, k)))
                if m is not None:
                    o = rot["origin"]
                    p = tuple(o[a] + qf.mv(m, tuple(p[b] - o[b] for b in range(3)))[a] for a in range(3))
                out.append(p)
    return out


def bare_shaft_errors(elements: list[dict]) -> list[str]:
    """Only the shaft may occupy the space a hand needs around each grip point."""
    errors = []
    for label, gy in (("grip_center", GRIP_CENTER_Y), ("off_hand_grip_center", OFF_HAND_GRIP_Y)):
        for e in elements:
            if e["name"] == "shaft":
                continue
            if any(abs(p[0] - CX) < 2.0 and abs(p[2] - CZ) < 2.0 and abs(p[1] - gy) < BARE_SHAFT_HALF
                   for p in element_samples(e)):
                errors.append(f"{e['name']} crowds the {label} hand at y={gy}")
    return errors


def blade_outline_errors(elements: list[dict]) -> list[str]:
    """The spearhead stays inside the contract's blade box and keeps its barbs and needle."""
    errors = []
    blade = [e for e in elements if e["name"].startswith("blade_") and e["name"] != "blade_root"]
    for e in blade:
        lo, hi = element_bounds(e)
        if max(abs(lo[2] - CZ), abs(hi[2] - CZ)) > CONTRACT_BLADE_HALF_WIDTH + 1e-6:
            errors.append(f"{e['name']} is wider than the contract blade half width")
        if max(abs(lo[0] - CX), abs(hi[0] - CX)) > CONTRACT_BLADE_HALF_THICKNESS + 1e-6:
            errors.append(f"{e['name']} is thicker than the contract blade half thickness")
        if hi[1] > TIP_Y + 1e-6:
            errors.append(f"{e['name']} passes the tip")
    widths = [w for _, _, w in BLADE_STEPS]
    barbs = sum(1 for a, b in zip(widths, widths[1:]) if b > a)
    if barbs < 2:
        errors.append(f"spearhead has {barbs} barbs, expected at least 2 per edge")
    widest = max(range(len(BLADE_STEPS)), key=lambda k: widths[k])
    mid = (BLADE_STEPS[widest][0] + BLADE_STEPS[widest][1]) / 2.0
    if not BLADE_BASE_Y <= mid <= BLADE_BASE_Y + (TIP_Y - BLADE_BASE_Y) / 2.0:
        errors.append(f"widest point at y={mid:.2f} is not in the lower half of the blade")
    for (a0, a1, _), (b0, _, _) in zip(BLADE_STEPS, BLADE_STEPS[1:]):
        if abs(a1 - b0) > 1e-9:
            errors.append(f"spearhead steps leave a gap at y={a1}")
    if BLADE_STEPS[-1][1] != TIP_Y:
        errors.append("spearhead does not reach the tip")
    order = (BLADE_EDGE_HALF_X, BLADE_BODY_HALF_X, BLADE_NAVY[0][3], BLADE_SPINE[0][3])
    if list(order) != sorted(set(order)):
        errors.append("blade plates must get thicker toward the spine")
    guard = [e for e in elements if e["name"].startswith("star")]
    for e in guard:
        lo, hi = element_bounds(e)
        if max(abs(lo[2] - CZ), abs(hi[2] - CZ)) > CONTRACT_GUARD_HALF_WIDTH + 1e-6:
            errors.append(f"{e['name']} is wider than the contract guard half width")
        if max(abs(lo[0] - CX), abs(hi[0] - CX)) > CONTRACT_GUARD_HALF_THICKNESS + 1e-6:
            errors.append(f"{e['name']} is thicker than the contract guard half thickness")
    for e in elements:
        if e["name"].startswith("butt_cap") or e["name"].startswith("butt_hook") or e["name"].startswith("butt_step"):
            lo, hi = element_bounds(e)
            if lo[1] < POMMEL_Y[0] - 1e-6 or hi[1] > POMMEL_Y[1] + 1e-6:
                errors.append(f"{e['name']} leaves the pommel range")
    return errors


def fist_fit() -> dict:
    """Third-person grip fit in the neutral item frame (PAL right_item = 0)."""
    t = build_display()["thirdperson_righthand"]
    grip = qf.display_point(t, GRIP_CENTER)
    direction = qf.normalize(qf.display_vector(t, (0.0, 1.0, 0.0)))
    old = qf.VANILLA["thirdperson_righthand"]
    old_dir = qf.normalize(qf.display_vector(old, qf.OLD_BLADE_DIR))
    fist_lo = tuple(qf.FIST_CENTER_ITEM[i] - 2.0 for i in range(3))
    fist_hi = tuple(qf.FIST_CENTER_ITEM[i] + 2.0 for i in range(3))
    intruders = []
    for e in build_elements():
        if e["name"] == "shaft":
            continue
        pts = [qf.display_point(t, p) for p in qf.element_corners(e)]
        lo = tuple(min(p[i] for p in pts) for i in range(3))
        hi = tuple(max(p[i] for p in pts) for i in range(3))
        if all(lo[i] < fist_hi[i] and hi[i] > fist_lo[i] for i in range(3)):
            intruders.append(e["name"])
    hang = qf.display_vector(t, qf.normalize((0.0, -1.0, 1.0)))
    return {
        "grip": grip, "grip_error": math.dist(grip, qf.FIST_CENTER_ITEM),
        "axis_dir": direction, "old_blade_dir": old_dir,
        "axis_angle_error": math.degrees(math.acos(max(-1.0, min(1.0, sum(direction[i] * old_dir[i]
                                                                          for i in range(3)))))),
        "flat_normal": qf.display_vector(t, (1.0, 0.0, 0.0)),
        "fist_intruders": intruders,
        "tip": qf.display_point(t, (CX, TIP_Y, CZ)),
        "butt": qf.display_point(t, (CX, BUTT_Y, CZ)),
        "hang_dir_item": hang,
        "in_hand_length_blocks": (TIP_Y - BUTT_Y) * THIRD_PERSON_SCALE * 0.9375 / 16.0,
    }


def fixed_extent() -> float:
    """Largest distance (display px from the centre) of the model in the item frame.  The frame
    draws the item at half size and turns it in 45-degree steps; its opening is 12 world px, so
    staying within 12 display px keeps every turn inside the frame."""
    t = build_display()["fixed"]
    worst = 0.0
    for e in build_elements():
        for p in qf.element_corners(e):
            q = qf.display_point(t, p)
            worst = max(worst, math.hypot(q[0], q[1]))
    return worst


def self_check() -> list[str]:
    elements = build_elements()
    errors = check_elements(elements)
    errors += bare_shaft_errors(elements)
    errors += blade_outline_errors(elements)
    ys = [p[1] for e in elements for p in qf.element_corners(e)]
    if abs(min(ys) - BUTT_Y) > 1e-6 or abs(max(ys) - TIP_Y) > 1e-6:
        errors.append(f"model spans y {min(ys):.3f}..{max(ys):.3f}, expected {BUTT_Y}..{TIP_Y}")
    fit = fist_fit()
    if fit["grip_error"] > 0.01:
        errors.append(f"third-person grip {fit['grip_error']:.3f} px from the fist centre")
    if fit["axis_angle_error"] > 0.2:
        errors.append(f"third-person axis turned {fit['axis_angle_error']:.2f} deg from the PAL design")
    if abs(abs(fit["flat_normal"][0]) - 1.0) > 0.01:
        errors.append("third-person flat normal is no longer the arm's lateral axis")
    if fit["fist_intruders"]:
        errors.append(f"third-person fist overlaps {', '.join(fit['fist_intruders'])}")
    if not 0.85 <= THIRD_PERSON_SCALE <= 0.9:
        errors.append("third-person scale outside the design range 0.85..0.9")
    if fixed_extent() > 12.0:
        errors.append(f"item-frame view reaches {fixed_extent():.2f} px from the centre (opening is 12)")
    geo = geometry_contract()
    if not (geo["pommel"]["y"][1] <= geo["handle"]["y"][0] and geo["handle"]["y"][1] <= geo["guard"]["y"][0]
            and geo["guard"]["y"][1] <= geo["blade_base"][1] < geo["blade_tip"][1]):
        errors.append("geometry contract parts are out of order")
    for key in ("grip_center", "off_hand_grip_center"):
        if not geo["handle"]["y"][0] < geo[key][1] < geo["handle"]["y"][1]:
            errors.append(f"{key} outside the handle")
    if not geo["grip_center"][1] < geo["off_hand_grip_center"][1]:
        errors.append("the off hand must lead the main hand")
    return errors


def report() -> None:
    elements = build_elements()
    print(f"elements {len(elements)} (glowing {sum('neoforge_data' in e for e in elements)}),"
          f" texture {TEXTURE_SIZE}x{TEXTURE_SIZE}, icon {ICON_SIZE}x{ICON_SIZE}")
    for name, t in build_display().items():
        print(f"{name:22s} rot {t['rotation']} tr {t['translation']} scale {t['scale'][0]}")
    for key, value in fist_fit().items():
        if isinstance(value, tuple):
            value = "(" + ", ".join(f"{v:+.3f}" for v in value) + ")"
        elif isinstance(value, float):
            value = f"{value:.3f}"
        print(f"  {key:22s} {value}")
    print(f"  item frame extent {fixed_extent():.2f} display px of 12")


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
                errors.append(f"{rel} differs from the generator; run python3 tools/gen_lingxiao_spear_model.py")
        if errors:
            for e in errors:
                print("ERROR:", e, file=sys.stderr)
            return 1
        print("OK Lingxiao spear model, textures and geometry contract match the generator and pass all self-checks")
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
