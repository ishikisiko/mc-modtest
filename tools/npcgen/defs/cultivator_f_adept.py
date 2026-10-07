"""Cultivator, female adept look (女修 · 小有所成, `f_adept`): an inner-sect woman in silk.

The same entity (`myvillage:cultivator`, hitbox 0.6 x 1.9, scale 0.5, look bone `head` 60/35) as the
default look in `cultivator.py`, with every body bone of it (root, body, head, arm_*, forearm_*, leg_*,
hair_back) and more: the deep hanging sleeves, the shawl's ends, the hairpin's drop, the earrings,
the jade pendant. Palette A: 绛紫 silk coat, 月白 skirt, gold, a pale-gold shawl (披帛). "Rich" lives in
the number of layers and ornaments, not in colour: from the neck down a stand collar (中衣, one line
above the coat), the open coat with gold-bordered edges, the skirt tied high on the chest under a
gold band (齐胸), the waist cord with a jade pendant and tassel, two skirts (outer short with a woven
gold fret, inner long and wider with a wave-scroll band), the shawl in a flat V across the back and
down the outer backs of the sleeves, opening 3 degrees and turning back in below, to the knees; on the head a low wide bun set back on the crown, a gold flower, the hairpin (步摇) on the right
with a three-texel drop, hair loops behind the ears (垂鬟), jade earrings, hair down to the waist.

Design space and units are the default look's: ground h = 0, front -Z, the figure's left +X, one unit
1/32 block, h = units above the ground.

Female geometry against the default look (texels):

    part                       default (male)            f_adept
    shoulders (sleeve to       28 (arm pivots +-11,      26 (pivots +-10, sleeves 6), square: the
      sleeve)                  sleeves 6), sloped caps   coat's shoulder line level with the sleeves
    outer trunk layer          vest 16 wide              coat 14 wide (h 33.25..44.25)
    waist                      belt 18 over the vest     cord 12 (coat 14 -> waist 12 -> outer skirt
                                                         16 -> inner skirt 20: narrow above, wide below)
    hip / knee                 HIP 29, robe tiers 8 / 9  HIP 28, outer skirt 8 per leg (h 9..28),
                               per leg                   inner skirt 10 per leg (h 0.75..8.75)
    head top                   60 (hair 61, crown 64)    58 (hair cap 59; bun 7 x 3 x 6 at h 59..62,
                                                         z 1.5..7.5, cap 5 x 1 x 4 to h 63)
    neck (chin underside)      48                        46; shoulder pivots 4 below as before
    head                       one cube 13 wide,         one cube 11 wide, 12 tall, 12 deep (h 46..58),
                               12 tall (h 48..60)        as the anime skins' heads: the face shape
                                                         comes from the fringe and the side hair
    hand                       4 x 5 x 4                 3 x 4 x 2, half of it inside the cuff
    leg swing                  26 deg                    20 deg, hips sway 1.5 deg (body z)
    arms                       hanging                   hands folded before the belly, right over
                                                         left, in the rest pose itself

Face (columns a from the centre column, rows r down from the cranium's top at h 58), copied from the
anime skins (二次元皮肤) as the default look's is: pale flat skin, no brow, no nose, no contour marks.
    r0-r3 the fringe, parted in the middle but covering the top half in strands of alternating tone:
    r0..r3 at the parting (a = 0), to r4 at a = 2 and 4, the strand tips to r5 on the odd columns;
    the side hair a = 6 (the hair shell's edge column) down to the chin: the head is one plain cube
    (11 x 12 face) like the skins' heads, its shape framed by the fringe and the side hair.
    r5 the lash's inner corner a = 2, one row up.   r6 the lash line a = 2..4, its tail a = 5 fainter.
    r7-9 the eye: a violet iris a = 2..3 running dark to light downward, a white column a = 4
    outside it (r7..r8), a faint blush under it at r9.
    r10 a small rouged mouth a = 0..1.

Motion: idle 4 s (breathing +-0.25, the drops swing about a texel at their ends, the shawl drifts,
legs untouched); walk with the planted foot travelling evenly (`_leg_angle`/`_sole_low`), the
hanging sleeves and the pendant riding the leg under them, and the back hair and the shawl's two
segments on their own rotation channels a quarter step behind the hips' bob and sway.
"""
from __future__ import annotations

import math

from ...beastgen.builder import Builder
from ...beastgen.cuboid import mat_inverse_rigid, mat_mul, mat_rot_zyx
from ...beastgen.paint import ramp
from ..humanoid import (HumanoidPaint, _box, _clamp, _form, _fret, _hash, _leg_angle, _loop, _mix, _pair,
                        _rgb, _rx, _sole_low, _tone, _weave, _xhz)

ID = "myvillage:cultivator"
ENTITY = "cultivator"            # the entity's texture directory
NAME = "cultivator_f_adept"      # file name prefix of the three generated files
LOOK = "f_adept"                 # NpcEntity's look id this definition draws
HITBOX = (0.6, 1.9)              # width, height in blocks (the server registration; same as the default look)
SCALE = 0.5                      # one model unit is 1/32 block

HIP = 28.0             # leg pivots and the body bone
SHOULDER = 42.0        # arm pivots
ARM_X = 10.0           # arm pivots' distance from the centre line (the default look: 11)
NECK = 46.0            # head pivot, the chin's underside
HEAD_TOP = 58.0        # the cranium's top; the hair cap adds one, the high bun more
COAT = (33.25, 44.25)  # the wide-sleeved coat (大袖衫): bottom, top
CORD = (31.0, 33.0)    # the thin waist cord (腰间细带) the coat ends over
CHEST_BAND = (38.25, 40.25)   # the skirt's band tied high on the chest (齐胸)
BUN = (59.0, 63.0, 1.5)  # the bun's bottom, the top of its cap, the front of the bun (back half of the crown)
DRAPE_TOP = (14.0, 40.0, 2.0)  # the shawl ends' pivots: |x| just outside the sleeve, h, z behind the arm
DRAPE_FLARE = 3.0        # degrees the shawl ends open outward
DRAPE_TUCK = 8.0         # degrees their lower segments turn back in, so the ends stay close to the skirt
OUTER_HEM = 9.0        # the outer skirt's hem; the inner skirt hangs below it
INNER_HEM = 0.75
FRET_ROWS = 4          # the woven gold band above the outer hem
CLOUD_BAND = 2.0       # lower edge of the inner skirt's cloud band

# ------------------------------------------------------------------------------------ palette (A)
# Entity faces are drawn at 50 to 74 % brightness unless they face up, so the base tones sit high.
COAT_SILK = ramp("#2A0E1E", "#40162E", "#561F3E", "#6E2950", "#873563", "#A04677", "#BA5E8E", "#D07EA6")
LINING = ramp("#3B2A3A", "#58425A", "#7A6280", "#9E88A6")
SKIRT = ramp("#7E8BA0", "#98A6BA", "#B2BED0", "#C9D3E2", "#DCE4EE", "#EAF0F6", "#F6F9FC")
SKIRT_DEEP = ramp("#5D6A82", "#74829A", "#8C9AB2", "#A6B3C8")
INNER = ramp("#B6ADA4", "#D0C8BF", "#E6E0D8", "#F4F0EA", "#FFFCF7")
GOLD = ramp("#6E5328", "#957333", "#BE9A48", "#DDBE6C", "#F2DB97", "#FFF1C4")
DRAPE = ramp("#A88C58", "#C2A770", "#D8C08C", "#E8D5A8", "#F4E6C6", "#FCF5E4")
JADE = ramp("#22665A", "#338C7A", "#52B09A", "#84D6BA", "#BCF0DB", "#E6FCF1")
PEARL = ramp("#A9A39C", "#CFC9C2", "#ECE8E2", "#FFFFFF")
CORAL = ramp("#6E1A26", "#9A2834", "#C23D44", "#DF6460")
SKIN = ramp("#B88A70", "#D4A68A", "#EAC3A8", "#F6D8C2", "#FDE6D4", "#FFF0E3", "#FFF7EE")
HAIR = ramp("#0A0A11", "#13151F", "#1C202D", "#282E40", "#373F56", "#4C5672", "#66718F")
SHOE = ramp("#2E1222", "#45192F", "#5E2441", "#7A3256")
LIP = "#C46A6E"
LIP_DEEP = "#A44F5A"
BLUSH = "#F2A0A0"        # the cheek texel under the eye's white column
EYE_WHITE = "#F8FAFF"    # the eye's outer column, white as in the skins
IRIS_TOP = "#4A1F6B"     # the iris, dark violet at the top ...
IRIS = "#8E4FC4"         # ... through the mid violet (the coat's dye family) ...
IRIS_LOW = "#D9A8F0"     # ... to light at the bottom (the skins' vertical gradient)
LASH = "#171522"         # the lash line over the eye and its rising inner corner

# Shells whose texture is partly cut out: they cast no baked shadow, and only they have holes.
HOLLOW = ("hair", "coat", "loop_right", "loop_left", "hair_back_tip")


# ------------------------------------------------------------------------------------ model helpers
def _euler_zyx(m):
    """Degrees (x, y, z) of a rotation matrix's Rz * Ry * Rx factors (the bone rotation order)."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    x = math.atan2(m[2][1], m[2][2])
    z = math.atan2(m[1][0], m[0][0])
    return tuple(math.degrees(v) for v in (x, y, z))


def _plumb(b, parent, heading=0.0):
    """Local rotation that makes a child of `parent` hang plumb, turned `heading` degrees about the
    vertical: a hanging sleeve stays vertical under a raised forearm."""
    world = mat_rot_zyx(0.0, math.radians(heading), 0.0)
    parent_m = b._design(parent)
    return _euler_zyx(mat_mul(mat_inverse_rigid(parent_m), world))


# ------------------------------------------------------------------------------------ model
# The arms hold the hands folded before the belly in the rest pose (right hand over left), so the
# right and left chains are written out instead of mirrored rotations: the right hand rests a texel
# higher and a little in front of the left, so the two overlap seen from the front without passing
# through each other (the cuffs, cloth on cloth, do overlap).
HANG = (6.0, 29.5, -8.0)     # hanging sleeve pivot: |x|, h, z (its back face a texel clear of the skirt)
ARM_ROT = (-24.0, 0.0, -4.0)            # right upper arm (left: z mirrored)
FORE_ROT = {"right": (-48.0, -66.0, 0.0), "left": (-40.0, 70.0, 0.0)}


def build_model():
    b = Builder(ID, look={"bone": "head", "max_yaw": 60.0, "max_pitch": 35.0}, shadow_radius=0.4)
    b.model.scale = SCALE

    # ---- trunk: inner robe, the skirt tied high on the chest, the waist cord, the open coat shell
    b.bone("body", "root", at=(0.0, -HIP, 0.0))
    _box(b, "body", "torso", -5, 28, -3.5, 10, 15, 7)
    _box(b, "body", "skirt_top", -5.5, 28.25, -4, 11, 12, 8)
    _box(b, "body", "chest_band", -6, CHEST_BAND[0], -4.5, 12, 2, 9)
    _box(b, "body", "cord", -6, CORD[0], -4.5, 12, 2, 9)
    _box(b, "body", "coat", -7, COAT[0], -5, 14, 11, 10)
    _box(b, "body", "neck", -2, 42, -2, 4, 5, 4)
    _box(b, "body", "collar", -3, 43.5, -3, 6, 2, 6)
    # the coat's gold-bordered front edges, opening a little wider toward the waist
    _pair(b, "coat_edge_{}", "body", (-3.5, -(COAT[1] - 0.25), -5.5), rot=(0.0, 0.0, -5.0),
          boxes=[("coat_edge_{}_band", (-1.0, -0.5, -0.5), (2, 11, 1))], local=True)
    # the shawl (披帛) across the upper back; its two ends hang on their own bones
    _pair(b, "drape_sag_{}", "body", (-12.5, -40.5, 5.6), rot=(0.0, 0.0, -62.0),
          boxes=[("drape_sag_{}_band", (-1.0, -0.5, -0.5), (2, 13, 1))], local=True)

    # ---- head: one cube 11 texels wide from the chin to the crown, as the anime skins' heads are (the
    # stepped jaw to a pointed chin read as uncanny); the face shape comes from the fringe and the side
    # hair that frame it; a cut-out hair shell with a stepped top, a high bun set back, a gold comb, the hairpin (步摇) with its drop, two side
    # loops (垂鬟), earrings
    b.bone("head", "body", at=(0.0, -NECK, 0.0))
    _box(b, "head", "skull", -5.5, 46, -5.5, 11, 12, 12)
    _box(b, "head", "hair", -6.5, 46, -6.5, 13, 12, 14)
    _box(b, "head", "hair_cap", -5.5, 58, -5.5, 11, 1, 12)
    # a low, wide bun over the back half of the crown, rounded by a smaller cap
    _box(b, "head", "bun", -3.5, BUN[0], BUN[2], 7, 3, 6)
    _box(b, "head", "bun_top", -2.5, BUN[0] + 3, BUN[2] + 1, 5, 1, 4)
    _box(b, "head", "flower", 1.0, 59.5, BUN[2] - 0.75, 2, 2, 1)
    for side, x0 in (("right", -7.25), ("left", 6.25)):
        kw = {} if side == "right" else {"mirror": True, "uv_from": "loop_right"}
        _box(b, "head", f"loop_{side}", x0, 46, 2.5, 1, 6, 4, **kw)
    _pair(b, "earring_{}", "head", (-5.5, -50.5, -1.0),
          boxes=[("earring_{}_drop", (-0.5, 0.0, -0.5), (1, 3, 1))], local=True)
    b.bone("pin", "head", at=(-2.5, -61.0, BUN[2] + 2.5), rot=(0.0, 24.0, 22.0))
    b.box_local("pin", "pin_rod", (-5.0, -0.5, -0.5), (5, 1, 1))
    b.box_local("pin", "pin_head", (-7.0, -1.0, -1.0), (2, 2, 2))
    b.bone("pin_drop", "pin", at_local=(-6.0, 1.0, 0.0), rot=_plumb(b, "pin"))
    b.box_local("pin_drop", "pin_bead", (-0.5, 0.0, -0.5), (1, 3, 1))

    # ---- long hair down the back to the waist, hung from the trunk so a turning head does not drag it
    b.bone("hair_back", "body", at=(0.0, -47.5, 7.5))
    b.box_local("hair_back", "hair_back_main", (-4.5, -0.5, -1.0), (9, 16, 2))
    b.box_local("hair_back", "hair_back_tip", (-3.5, 15.5, -1.0), (7, 3, 2))

    # ---- arms: a fitted upper sleeve, a wide lower sleeve with its own cuff, the hand half under the
    # cuff, and the deep sleeve (垂袖) hanging plumb under the raised forearm
    for side, s in (("right", -1.0), ("left", 1.0)):
        own = side == "right"

        def kw(name):
            return {} if own else {"mirror": True, "uv_from": name.format("right")}

        def x0(lo, w):
            return lo if own else -(lo + w)

        b.bone(f"arm_{side}", "body", at=(s * ARM_X, -SHOULDER, 0.0),
               rot=(ARM_ROT[0], 0.0, ARM_ROT[2] if own else -ARM_ROT[2]))
        b.box_local(f"arm_{side}", f"sleeve_upper_{side}", (x0(-3.0, 6), -1.0, -3.0), (6, 11, 6), **kw("sleeve_upper_{}"))
        fore = b.bone(f"forearm_{side}", f"arm_{side}", at_local=(0.0, 10.0, 0.0), rot=FORE_ROT[side])
        for name, origin, size in (("sleeve_lower_{}", (-3.5, -1.0, -3.5), (7, 10, 7)),
                                   ("cuff_{}", (-4.0, 9.0, -4.0), (8, 2, 8)),
                                   ("hand_{}", (-1.5, 9.0, -1.0), (3, 4, 2))):
            b.box_local(fore, name.format(side), (x0(origin[0], size[0]), origin[1], origin[2]), size, **kw(name))
        # plumb and square to the figure, so a keyed x rotation swings it straight forward over the leg
        hang = b.bone(f"sleeve_hang_{side}", fore, at=(s * HANG[0], -HANG[1], HANG[2]), rot=_plumb(b, fore))
        b.box_local(hang, f"sleeve_hang_{side}_panel", (x0(-4.0, 8), 0.0, -1.0), (8, 12, 2), **kw("sleeve_hang_{}_panel"))

    # ---- hung from the cord: the jade pendant with its tassel on the left front
    b.bone("pendant", "body", at=(0.0, -31.2, -6.6), rot=(-3.0, 0.0, 0.0))
    b.box_local("pendant", "pendant_cord", (-0.5, 0.0, -0.5), (1, 8, 1))
    b.box_local("pendant", "pendant_jade", (-1.5, 8.0, -0.5), (3, 3, 1))
    b.box_local("pendant", "pendant_tassel", (-1.0, 11.0, -0.5), (2, 5, 1))

    # ---- the shawl's two ends, hanging behind the arms to the knees
    _pair(b, "drape_{}", "body", (-DRAPE_TOP[0], -DRAPE_TOP[1], DRAPE_TOP[2]), rot=(4.0, 0.0, DRAPE_FLARE),
          boxes=[("drape_{}_strip", (-1.0, -0.5, -0.5), (2, 14, 1))], local=True)
    for side, s in (("right", 1.0), ("left", -1.0)):
        b.bone(f"drape_{side}_low", f"drape_{side}", at_local=(0.0, 13.0, 0.0), rot=(-3.0, 0.0, -DRAPE_TUCK * s))
        kw = {} if side == "right" else {"mirror": True, "uv_from": "drape_right_low_strip"}
        b.box_local(f"drape_{side}_low", f"drape_{side}_low_strip", (-1.0 if side == "right" else -2.0, 0.0, -0.5),
                    (3, 16, 1), **kw)

    # ---- legs: each carries half of the two skirts (outer short, inner long and wider) and a shoe
    _pair(b, "leg_{}", "root", (-3.0, -HIP, 0.0),
          boxes=[("skirt_outer_{}", (-8.0, OUTER_HEM, -6.0), (8, 19, 12)),
                 ("skirt_inner_{}", (-10.0, INNER_HEM, -7.0), (10, 8, 14)),
                 ("shoe_{}", (-5.5, 0.0, -8.0), (5, 3, 9)),
                 ("shoe_tip_{}", (-4.5, 1.0, -9.0), (3, 2, 1))])
    return b.model


class _Paint(HumanoidPaint):
    """The painter: one method per material, dispatched by cube name (right-side cubes only)."""

    SKIN = SKIN
    HAIR = HAIR
    FINGER_ROW = 12.0                # the hand's last row, bone-local
    HAND_EXCLUDE = ("cuff_right", "sleeve_lower_right")

    def __init__(self, model):
        super().__init__(model, HOLLOW, {
            "torso": self.torso, "skirt_top": self.skirt_top, "chest_band": self.chest_band, "cord": self.cord,
            "coat": self.coat, "neck": self.neck, "collar": self.collar, "coat_edge_right_band": self.coat_edge,
            "drape_sag_right_band": self.drape, "drape_right_strip": self.drape, "drape_right_low_strip": self.drape,
            "skull": self.skull,
            "hair": self.hair, "hair_cap": self.hair, "bun": self.bun, "bun_top": self.bun, "flower": self.flower,
            "loop_right": self.loop, "earring_right_drop": self.earring,
            "pin_rod": self.pin, "pin_head": self.pin, "pin_bead": self.bead,
            "hair_back_main": self.hair_back, "hair_back_tip": self.hair_back,
            "sleeve_upper_right": self.sleeve_upper, "sleeve_lower_right": self.sleeve_lower,
            "cuff_right": self.cuff, "hand_right": self.hand, "sleeve_hang_right_panel": self.sleeve_hang,
            "pendant_cord": self.pendant_cord, "pendant_jade": self.jade, "pendant_tassel": self.tassel,
            "skirt_outer_right": self.skirt, "skirt_inner_right": self.skirt,
            "shoe_right": self.shoe, "shoe_tip_right": self.shoe,
        })

    # ---- face
    def face(self, t):
        """The front of the head as a symmetric texel map copied from the anime skins (二次元皮肤),
        keyed on (a, r): a columns from the centre column, r rows down from the cranium's top. The
        parted fringe covers the top half in strands; the eyes sit in the lower half, each a violet
        iris two columns wide running dark to light downward with a white column outside it, a lash
        line on top whose inner corner rises a row; no brow (under the fringe), no nose, a faint blush
        under the white column, and a small rouged mouth on pale flat skin."""
        x, h, _ = _xhz(t)
        a = abs(int(round(x)))            # 0..5
        r = int(HEAD_TOP - h)             # 0..11
        if h >= _hairline(a):
            return _tone(HAIR, 1.6)
        tone = 5.5 - 0.4 * _clamp(1.5 - (_hairline(a) - h))   # the fringe's shadow, soft
        skin = lambda d=0.0: _tone(SKIN, tone + d)  # noqa: E731  (no cloth noise: the face stays symmetric)
        if r == 5 and a == 2:
            return _rgb(LASH)                               # the lash's inner corner, one row up
        if r == 6:
            if 2 <= a <= 4:
                return _rgb(LASH)                           # the lash line
            if a == 5:
                return _mix(_rgb(LASH), skin(), 0.45)       # its tail toward the sideburn
        if r in (7, 8, 9):
            if a in (2, 3):
                return _rgb((IRIS_TOP, IRIS, IRIS_LOW)[r - 7])   # the iris, dark to light downward
            if a == 4:
                return _rgb(EYE_WHITE) if r < 9 else _mix(skin(), _rgb(BLUSH), 0.3)   # white, a blush under it
        if r == 10:
            if a == 0:
                return _rgb(LIP_DEEP)                       # a small rouged mouth
            if a == 1:
                return _mix(skin(), _rgb(LIP), 0.7)
        return skin(-0.5 * self.occ.contact(t) - 0.8 * self.occ.overhang(t))

    def hair_on_side(self, z, h):
        return _hair_on_side(z, h)

    def skull(self, t):
        if t.face == "UP":
            # the underside: under the chin in front, in shadow; under the back hair behind
            _, _, z = _xhz(t)
            return _tone(SKIN, 3.4) if z < 2.5 else _tone(HAIR, 1.2)
        return super().skull(t)

    def head_side(self, t, z, h):
        """Skin where the hair shell is cut away on the cranium's side: temple and ear."""
        zc = int(math.floor(z))
        tone = 4.0 - 0.4 * _clamp((z + 3.0) / 4.0) - 0.7 * _clamp((51.0 - h) / 4.0)
        if 51.0 < h < 54.0:
            if zc == -1:
                tone = 4.9                         # the ear's rim
            elif zc == 0:
                tone = 2.6 if 52.0 < h < 53.0 else 4.4
            elif zc == -2:
                tone -= 0.5
        return _tone(SKIN, tone)

    # ---- hair
    def hair_tone(self, t, base=2.6):
        x, h, z = _xhz(t)
        if t.n[1] < -0.5:
            # combed back toward the bun: streaks run radially on the crown
            ang = math.atan2(x, z - 2.0)
            streak = _hash(int(math.floor(ang * 7.0)), 0, 5)
            ring = math.hypot(x, z - 2.0)
            return base + 0.3 + (streak - 0.5) * 2.0 + 1.2 * _clamp(1.0 - abs(ring - 4.5) / 1.2)
        along = x if abs(t.n[2]) > 0.5 else z
        streak = _hash(int(round(along)), int(h * 0.12), 7)
        tone = base + (streak - 0.5) * 1.8 + _form(t, 0.8)
        tone += 1.7 * _clamp(1.0 - abs(h - 55.6 - 0.7 * math.sin(along * 0.9)) / 1.1)  # the sheen band
        tone -= 0.8 * _clamp((50.0 - h) / 4.0)
        return tone

    def hair(self, t):
        x, h, z = _xhz(t)
        f = t.face
        a = abs(int(round(x)))
        if t.cube == "hair":
            if f == "UP":
                return None  # open underneath
            if f == "NORTH" and h < _hairline(a):
                return None
            if f in ("WEST", "EAST") and not _hair_on_side(z, h):
                return None
        tone = self.hair_tone(t)
        if f == "NORTH" and t.cube == "hair" and a <= 5:
            # the fringe in strands: a tone per column as the skins stripe their bangs, lighter at the tips
            tone += (1.1, -0.3, 0.7, -0.5, 1.0, -0.1)[a] + 0.4 * _clamp(1.5 - (h - _hairline(a)))
        elif f == "NORTH":
            tone -= 0.7 * _clamp(1.5 - (h - _hairline(a)))  # darker at the root line beside the face
        if f == "DOWN":
            tone -= 1.2 * self.occ.contact(t)
        return _tone(HAIR, tone)

    def bun(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f in ("NORTH", "SOUTH", "WEST", "EAST"):
            # wound hair: diagonal coils with a sheen high on the bun
            coil = math.sin((h + (x if abs(t.n[2]) > 0.5 else z) * 0.8) * 1.7)
            top = BUN[0] + 3.0 if t.cube == "bun" else BUN[1]
            tone = 2.6 + 0.6 * coil + _form(t, 1.3) + 1.4 * _clamp(1.0 - abs(h - top + 1.2) / 0.8)
        else:
            tone = 3.0 + _form(t, 1.0) + (_hash(int(round(x)), int(round(z)), 9) - 0.5) * 1.2
        return _tone(HAIR, tone - 1.0 * self.occ.contact(t) - 0.8 * self.occ.overhang(t))

    def loop(self, t):
        """垂鬟: a loop of hair behind each ear, its side faces cut through in the middle."""
        x, h, z = _xhz(t)
        f = t.face
        if f in ("WEST", "EAST"):
            if 48.0 < h < 50.0 and 3.5 < z < 5.5:
                return None
            sheen = 1.4 * _clamp(1.0 - abs(h - 50.5) / 0.8)
            return _tone(HAIR, 2.8 + sheen + 0.4 * math.sin(z * 1.7) - 0.6 * self.occ.contact(t))
        return _tone(HAIR, 2.2 + _form(t, 0.8))

    def hair_back(self, t):
        x, h, z = _xhz(t)
        tone = 2.6 + (_hash(int(round(x)), int(h * 0.1), 13) - 0.5) * 1.8 + _form(t, 0.9)
        tone += 1.5 * _clamp(1.0 - abs(h - 41.5 - 0.6 * math.sin(x * 1.1)) / 1.2)
        tone -= 0.9 * _clamp((36.0 - h) / 5.0)
        if t.cube == "hair_back_tip":
            # the end thins to a point
            if h < 30.5 and (abs(x) > 1.0 or t.face in ("WEST", "EAST")):
                return None
            if h < 31.5 and (abs(x) > 2.0 or t.face in ("WEST", "EAST")):
                return None
        return _tone(HAIR, tone - 1.0 * self.occ.overhang(t))

    # ---- ornaments
    def flower(self, t):
        """A small gold hair flower (花钿) on the front of the bun with a coral heart."""
        x, h, z = _xhz(t)
        if t.face == "NORTH":
            col, row = int(math.floor(x - 1.5)), int(math.floor(h - 59.5))
            return _tone(CORAL, 2.8) if (col, row) == (0, 1) else _tone(GOLD, 3.6 if row == 1 else 2.8)
        return _tone(GOLD, 2.2 + _form(t, 0.8))

    def pin(self, t):
        if t.cube == "pin_head":
            # a small gold flower: bright petals round a coral heart
            if t.face in ("NORTH", "SOUTH") and t.fu > 0.0:
                lx, ly, lz = t.local
                if ly > 0.0 and lx > -6.0:
                    return _tone(CORAL, 2.6)
            return _tone(GOLD, 3.4 + _form(t, 0.8))
        tone = 3.0 + (1.0 if t.face == "DOWN" else -0.8 if t.face == "UP" else 0.0)
        return _tone(GOLD, tone)

    def bead(self, t):
        lx, ly, lz = t.local
        row = int(ly)  # 0 gold cap, 1 pearl, 2 coral drop
        if row == 0:
            return _tone(GOLD, 3.2)
        if row == 1:
            return _tone(PEARL, 2.6 + (0.4 if t.face == "NORTH" else 0.0))
        return _tone(CORAL, 2.8 if t.face in ("NORTH", "WEST") else 2.0)

    def earring(self, t):
        lx, ly, lz = t.local
        row = int(ly)  # 0 gold hook, 1..2 a jade drop
        if row == 0:
            return _tone(GOLD, 3.4)
        return _tone(JADE, (3.6 if row == 1 else 2.6) + (0.4 if t.face == "NORTH" else 0.0))

    def pendant_cord(self, t):
        return _tone(CORAL, 1.6 + (0.6 if int(t.local[1]) % 2 == 0 else 0.0))

    def jade(self, t):
        lx, ly, lz = t.local
        col, row = int(lx + 1.5), int(ly - 8.0)
        if t.face in ("NORTH", "SOUTH"):
            if (col, row) == (1, 1):
                return _tone(JADE, 1.4)           # the disc's hole, carved but not cut through
            return _tone(JADE, 4.6 if (col, row) == (0, 0) else 3.6 if row == 0 or col == 0 else 2.6)
        return _tone(JADE, 2.0)

    def tassel(self, t):
        lx, ly, lz = t.local
        v = ly - 11.0
        if v < 1.0:
            return _tone(GOLD, 3.2)
        return _tone(CORAL, 2.4 + (0.7 if lx < 0.0 else -0.2) - 0.6 * _clamp((v - 3.0) / 3.0))

    # ---- silk
    def silk(self, t, ramp_, tone, roundness=1.6, sheen=1.6, **kw):
        """Silk: a strongly rounded form with a narrow bright band on the lit side of each vertical face."""
        tone += self.light(t, roundness, **kw) + _weave(t, 0.18, 1.6, 4)
        if abs(t.n[1]) < 0.5:
            tone += sheen if abs(t.fu - 0.3) < 0.1 else 0.0      # one texel column wide
        return _tone(ramp_, tone)

    # ---- trunk
    def torso(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "DOWN":
            return _tone(INNER, 2.6)
        if f != "NORTH":
            return _tone(INNER, 2.4)
        shade = 1.2 * self.occ.contact(t) + 1.8 * self.occ.overhang(t)
        shade += 1.0 * _clamp(1.0 - (_coat_opening(h) - abs(x)) / 1.4)   # under the coat's edge
        tone = 4.2 + 0.3 * math.sin(x * 1.3 + h * 0.4) - shade
        return _tone(INNER, tone)

    def collar(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "DOWN":
            return _tone(INNER, 2.8)
        if f == "UP":
            return _tone(INNER, 1.0)
        row = int(h - 43.5)  # 0 lower, 1 upper
        return _tone(INNER, 3.6 + 0.4 * row + _form(t, 0.9) - 0.8 * self.occ.contact(t))

    def skirt_top(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f in ("UP", "DOWN"):
            return _tone(SKIRT, 2.4)
        # gathered under the chest band: fine vertical pleats fanning out toward the hip
        along = x if abs(t.n[2]) > 0.5 else z
        pleat = math.sin(along * 1.9)
        tone = 5.0 + 0.6 * pleat + self.light(t, 1.2, cast=2.0) + _weave(t, 0.2, 1.5, 6)
        return _tone(SKIRT, tone)

    def chest_band(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f in ("UP", "DOWN"):
            return _tone(GOLD, 1.6)
        row = int(h - CHEST_BAND[0])  # 0 lower, 1 upper
        along = x if abs(t.n[2]) > 0.5 else z
        shade = _form(t, 1.2) - 1.0 * self.occ.overhang(t)
        if row == 1:
            return _tone(GOLD, 3.2 + shade)
        # a row of small purple lozenges woven into the gold
        return _tone(COAT_SILK, 4.6 + shade) if int(math.floor(along + 0.5)) % 2 == 0 else _tone(GOLD, 2.4 + shade)

    def cord(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "DOWN":
            return _tone(COAT_SILK, 3.0 - 1.2 * self.occ.contact(t))
        if f == "UP":
            return _tone(COAT_SILK, 1.0)
        row = int(h - CORD[0])  # 0 lower, 1 upper
        shade = _form(t, 1.2) - 0.8 * self.occ.contact(t)
        if row == 1:
            return _tone(GOLD, 2.8 + shade)
        return _tone(COAT_SILK, 4.4 + shade + (0.6 if int(math.floor(x if abs(t.n[2]) > 0.5 else z)) % 2 else 0.0))

    def coat(self, t):
        x, h, z = _xhz(t)
        f = t.face
        ax = abs(x)
        if f == "NORTH" and ax < _coat_opening(h):
            return None
        if f == "DOWN":
            if ax < 3.5 and abs(z) < 3.5:
                return None  # the neck opening
            # the shoulders: light catches the silk across the top
            return _tone(COAT_SILK, 5.4 + _weave(t, 0.2, 1.5, 4) - 1.0 * _clamp(1.0 - (max(ax, abs(z)) - 3.5) / 1.5))
        if f == "UP":
            return _tone(COAT_SILK, 0.8)
        tone = 4.6 + 0.8 * _clamp((h - 41.5) / 2.5) - 0.5 * _clamp((36.0 - h) / 3.0)
        if h < COAT[0] + 1.0:
            return _tone(GOLD, 2.8 + _form(t, 1.2))     # a gold hem line
        return self.silk(t, COAT_SILK, tone, cast=1.6, crevice=0.9)

    def coat_edge(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f != "NORTH":
            return _tone(GOLD, 1.6 + (1.0 if f == "DOWN" else 0.0))
        shade = 1.2 * self.occ.overhang(t) + 0.4 * _clamp((ly - 7.0) / 4.0)
        step = int(ly + 1.0) % 3
        if lx > 0.0:  # the inner half: gold
            return _tone(GOLD, 3.0 + (0.8 if step == 0 else 0.0) - shade)
        return _tone(COAT_SILK, 5.6 + (1.0 if step == 0 else 0.0) - shade)   # a stitched purple border

    def drape(self, t):
        """披帛: sheer apricot silk, pale at the edges and a little deeper down the middle."""
        lx, ly, lz = t.local
        f = t.face
        if f in ("UP", "DOWN"):
            return _tone(DRAPE, 2.6)
        if f in ("WEST", "EAST"):
            return _tone(DRAPE, 4.4)
        width = 3.0 if t.cube == "drape_right_low_strip" else 2.0
        u = lx + width / 2.0                             # across the strip, 0..width
        edge = min(u, width - u)                         # 0.5 at an edge texel
        tone = 3.9 + (1.2 if edge < 0.6 else 0.0) + 0.3 * math.sin(ly * 0.6) - 0.7 * self.occ.overhang(t)
        if edge > 0.6 and int(math.floor(ly)) % 4 == 1:
            return _tone(GOLD, 3.6)                      # a sparse woven gold dot down the middle
        if t.cube == "drape_right_low_strip" and ly > 13.5:
            return _tone(GOLD, 3.0 + (0.8 if int(math.floor(u)) % 2 == 0 else 0.0))   # a gold-tipped end
        return _tone(DRAPE, tone)

    # ---- sleeves
    def sleeve_upper(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "DOWN":
            return _tone(COAT_SILK, 4.8)
        crease = math.sin(ly * 1.4 + 0.5 * math.sin((lx + lz) * 0.8))
        tone = 4.7 + 0.5 * crease * _clamp((ly - 3.0) / 4.0)
        return self.silk(t, COAT_SILK, tone, exclude=("sleeve_lower_right",))

    def sleeve_lower(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "DOWN":
            return _tone(COAT_SILK, 3.6)
        along = lx if f in ("NORTH", "SOUTH") else lz
        fold = math.sin(along * 1.2 + 0.4 * ly)
        tone = 4.6 + 0.6 * fold - (0.6 if fold < -0.8 else 0.0)
        return self.silk(t, COAT_SILK, tone, exclude=("sleeve_upper_right", "cuff_right"))

    def cuff(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "UP":
            # the open cuff: a rim of mauve lining round the dark inside, the hand coming out of it
            edge = min(lx + 4.0, 4.0 - lx, lz + 4.0, 4.0 - lz)
            if edge < 1.0:
                return _tone(LINING, 2.4)
            return _tone(LINING, 0.3 if edge > 2.0 else 1.0)
        if f == "DOWN":
            return _tone(COAT_SILK, 2.6)
        row = int(ly - 9.0)  # 0 toward the elbow, 1 at the opening
        if row == 0:
            return _tone(GOLD, 2.6 + _form(t, 1.0))      # a gold line round the cuff
        return self.silk(t, COAT_SILK, 5.6, roundness=1.0, sheen=0.6)

    def sleeve_hang(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f in ("UP", "DOWN"):
            return _tone(LINING, 1.4 if f == "UP" else 0.6)
        # deep folds running down the hanging sleeve, a gold edge at its bottom
        along = lz if f in ("WEST", "EAST") else lx
        if ly > 10.5:
            return _tone(GOLD, 2.8 + _form(t, 1.0))
        if ly > 9.5:
            return _tone(COAT_SILK, 6.0 + _form(t, 0.6))
        fold = math.sin(along * 1.3 + 0.25 * ly)
        tone = 4.4 + 0.7 * fold
        return self.silk(t, COAT_SILK, tone, roundness=1.0, sheen=0.8, exclude=("sleeve_lower_right", "cuff_right"))

    # ---- skirt, shoes
    def skirt(self, t):
        x, h, z = _xhz(t)
        f = t.face
        outer = t.cube == "skirt_outer_right"
        if f == "UP":
            return _tone(SKIRT_DEEP, 0.4)
        if f == "DOWN":
            return _tone(SKIRT, 2.2 if outer else 1.8)   # the ledges face the light, so they are darker
        inner_face = f == "EAST"   # the face between the legs, seen mid-stride
        ang = math.atan2(x, -z if abs(z) > 0.01 else -0.01)
        pleat = math.sin(z * 1.1 + 0.5 * math.sin(h * 0.33)) if inner_face \
            else math.sin(ang * 8.0 + 0.4 * math.sin(h * 0.3 + x * 0.2))
        depth = _clamp((HIP - h) / 10.0)
        tone = 5.4 + _weave(t, 0.25, 1.5, 8)
        tone += (-1.3 if inner_face else self.light(t, 1.2, cast=2.2))
        tone += depth * (0.8 * pleat - (1.0 if pleat < -0.75 else 0.0) + (0.6 if pleat > 0.85 else 0.0))
        tone -= 1.3 * _clamp((22.0 - h) / 22.0)          # the silk deepens toward the hem
        across = z if abs(t.n[0]) > 0.5 else x
        if f in ("NORTH", "SOUTH") and x > -0.6:
            tone -= 0.8 if f == "NORTH" else 0.5           # the parting between the two halves
        shade = (-1.2 if inner_face else _form(t, 0.9)) + 0.4 * pleat
        if outer:
            band = int(math.floor(h - OUTER_HEM))
            if band == 0:
                return _tone(COAT_SILK, 3.0 + shade)       # the outer skirt's purple hem
            if 1 <= band <= FRET_ROWS:                     # 回纹: a woven gold fret on a deep ground
                kind = _fret(band - 1, across + 60.0)
                if kind == "line":
                    return _tone(GOLD, 2.4 + shade)
                return _tone(GOLD, 3.4 + shade) if kind else _tone(COAT_SILK, 3.4 + shade)
        else:
            band = int(math.floor(h - CLOUD_BAND))
            if band in (0, 1, 2):                          # a woven cloud band: a row of linked scrolls
                if _cloud(band, across + 60.0):
                    return _tone(COAT_SILK, 5.6 + shade)
                return _tone(SKIRT, 4.4 + shade)
            if band == -1:
                return _tone(GOLD, 2.4 + shade)            # the inner skirt's hem line
        return _tone(SKIRT, tone)

    def shoe(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if t.cube == "shoe_tip_right":
            return _tone(GOLD, 2.6 + _form(t, 0.8)) if f != "UP" else _tone(SHOE, 0.4)
        if f == "UP":
            return _tone(SHOE, 0.0)
        if f == "DOWN":
            return _tone(SHOE, 2.6)
        if h < 1.0:
            return _tone(INNER, 1.0 + _form(t, 0.6))
        return _tone(SHOE, 2.0 + _form(t, 0.8) - 1.0 * self.occ.overhang(t))


# ------------------------------------------------------------------------------------ shapes
def _hairline(a):
    """Height of the front hairline `a` columns from the centre: a fringe parted in the middle but
    covering the top half of the face as in the anime skins, four rows deep at the parting (a = 0),
    five at a = 2 and 4 and its strand tips six rows deep on the odd columns (so the lash's raised
    inner corner shows at a = 2 between two tips); the side hair runs down to the chin at the hair
    shell's edge column (a = 6), framing the face."""
    if a >= 6:
        return 46.0                       # the side hair frames the face down to the chin
    return {0: 54.0, 1: 52.0, 2: 53.0, 3: 52.0, 4: 53.0, 5: 52.0}[a]


def _hair_on_side(z, h):
    """Hair on the side of the head: the side lock's strip in front down to the chin, the window over
    temple and ear, the strip behind the ear, and everything behind that down to the nape."""
    if z < -4.5:
        return True
    if z < 0.5:
        return h >= 55.0
    if z < 2.5:
        return h >= 50.0
    return True


def _coat_opening(h):
    """Half width of the coat's front opening at height h (the centre line of its border bands)."""
    return 3.5 + 1.0 * _clamp((COAT[1] - h) / 11.0)


def _cloud(row, across):
    """A woven wave-scroll band three rows deep on a six-texel repeat: the line rises and falls one row
    a step. True where the line is drawn."""
    return (0, 1, 2, 2, 1, 0)[int(math.floor(across)) % 6] == row


def painter(model):
    return _Paint(model)


# ------------------------------------------------------------------------------------ clips
WALK_LENGTH = 1.0     # seconds per stride cycle in the file; animateWalk rescales it to the distance walked
WALK_SWING = 20.0     # degrees each leg swings either way (the default look: 26)
IDLE_LENGTH = 4.0
SOLE_Z = (-8.0, 1.0)  # the shoe sole's toe and heel, from the leg's axis
SWING_LIFT = 1.5      # how far the swinging foot clears the ground
QUARTER = math.pi / 4.0  # a quarter of a step (half a stride cycle is one step)


def _walk_pose(phase):
    right = _leg_angle(phase, WALK_SWING)             # negative x rotation brings a limb forward
    left = -right
    right_plants = math.cos(phase) >= 0.0             # the leg moving back carries the weight
    stance, swing = (right, left) if right_plants else (left, right)
    drop = _sole_low(stance, HIP, SOLE_Z)             # the hips ride on the planted foot
    lift = SWING_LIFT * abs(math.cos(phase)) + max(0.0, drop - _sole_low(swing, HIP, SOLE_Z))
    s = right / WALK_SWING
    sway = math.sin(phase)                            # the hips sway toward the planted leg
    # secondary motion a quarter step behind its driver: the hips' bob (twice a stride, lowest with the
    # legs spread) and their sway (once a stride)
    trail = math.sin(2.0 * (phase - QUARTER) - math.pi / 2.0)
    trail_side = math.sin(phase - QUARTER)
    forward_right, forward_left = max(0.0, -right), max(0.0, -left)
    return {
        "root": {"position": (0.0, -drop, 0.0)},
        "leg_right": {"rotation": (right, 0.0, 0.0), "position": (0.0, 0.0 if right_plants else lift, 0.0)},
        "leg_left": {"rotation": (left, 0.0, 0.0), "position": (0.0, lift if right_plants else 0.0, 0.0)},
        "body": {"rotation": (1.5, 2.0 * s, 1.5 * sway)},
        "head": {"rotation": (-1.0, -1.5 * s, -1.2 * sway)},
        "arm_right": _rx(-1.0 * s),
        "arm_left": _rx(1.0 * s),
        # the hanging sleeves ride the leg under them so the skirt never comes through
        "sleeve_hang_right": _rx(-0.95 * forward_right - 1.0 + 1.0 * trail),
        "sleeve_hang_left": _rx(-0.95 * forward_left - 1.0 + 1.0 * trail),
        "hair_back": _rx(3.0 + 2.5 * trail),
        "drape_right": {"rotation": (5.0 + 4.0 * trail, 0.0, -2.0 * trail_side)},
        "drape_left": {"rotation": (5.0 + 4.0 * math.sin(2.0 * (phase - QUARTER) - math.pi / 2.0 - 0.3), 0.0,
                                    -2.0 * trail_side)},
        "drape_right_low": {"rotation": (4.0 * math.sin(2.0 * (phase - 2.0 * QUARTER) - math.pi / 2.0), 0.0,
                                         -1.5 * trail_side)},
        "drape_left_low": {"rotation": (4.0 * math.sin(2.0 * (phase - 2.0 * QUARTER) - math.pi / 2.0 - 0.3), 0.0,
                                        1.5 * trail_side)},
        "pendant": {"rotation": (-0.9 * max(forward_right, forward_left) - 2.0, 0.0, 4.0 * trail_side)},
        "pin_drop": {"rotation": (6.0 * trail, 0.0, 8.0 * trail_side)},
        "earring_right": {"rotation": (8.0 * trail, 0.0, 0.0)},
        "earring_left": {"rotation": (8.0 * trail, 0.0, 0.0)},
    }


def _idle_pose(phase):
    breath = math.sin(phase)
    slow = math.sin(phase - 0.9)
    drift = math.sin(phase - QUARTER)
    return {
        "body": {"position": (0.0, 0.25 * breath, 0.0)},
        "head": _rx(0.6 * slow),
        "forearm_right": _rx(-0.8 * slow),
        "forearm_left": _rx(-0.8 * slow),
        "hair_back": _rx(0.6 * slow),
        "drape_right": {"rotation": (1.5 * drift, 0.0, 1.0 * math.sin(phase - 1.9))},
        "drape_left": {"rotation": (1.5 * math.sin(phase - 2.0), 0.0, -1.0 * math.sin(phase - 2.6))},
        "drape_right_low": _rx(1.5 * math.sin(phase - 2.4)),
        "drape_left_low": _rx(1.5 * math.sin(phase - 2.9)),
        "sleeve_hang_right": _rx(0.8 * drift),
        "sleeve_hang_left": _rx(0.8 * drift),
        # the drops swing about a texel at their lower end
        "pin_drop": {"rotation": (10.0 * drift, 0.0, 6.0 * math.sin(phase - 2.2))},
        "earring_right": _rx(10.0 * math.sin(phase - 1.2)),
        "earring_left": _rx(10.0 * math.sin(phase - 1.7)),
        "pendant": {"rotation": (0.8 * slow, 0.0, 3.0 * math.sin(phase - 2.4))},
    }


def clips(model):
    return [_loop("idle", IDLE_LENGTH, _idle_pose, 8), _loop("walk", WALK_LENGTH, _walk_pose, 32)]
