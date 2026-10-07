"""Cultivator, female novice look (女·刚入门, `f_novice`): an outer-sect disciple in a plain waist-high
ru skirt (齐腰襦裙) under a short-sleeved half jacket (半臂), hair in a low ponytail tied with a crimson
ribbon.

Same entity as `cultivator.py` (the male default look) and the same design space: ground y = 0, up -Y,
front -Z, the figure's left +X, one unit 1/32 block (`scale 0.5`), heights written as h = units above
the ground. Same body skeleton (root, body, head, arm_*, forearm_*, leg_*, hair_back), hitbox, look bone
and scale; this look adds bones for the collar bands, the jacket's edges, the skirt ties
(`tie_right/left`), the ribbon's tails and the lower skirt (`skirt_low_right/left`).

Design facts (texels; one texel = 1/32 block), against the male default:

| Part           | Male default                          | Female novice                                        |
|----------------|---------------------------------------|------------------------------------------------------|
| Shoulders      | 28 (arm pivots +-11, sleeve 6), sloped vest caps | 26 at the arm (pivots +-10.5, 4 sleeve; 5 x 3 short sleeve hinged at its inner top edge, outer side tipped down 12 deg), no caps |
| Chest / waist  | robe 14, vest 16, belt 18 (proud)     | ru 12, half jacket 13, skirt band 11 (in 1 a side), skirt top 10 |
| Skirt          | two tiers 16 / 18 on the legs: a column | three tiers 14 / 16 / 18, depth 9 / 10 / 11 (hip 18..28, knee 9..20, hem 2..11): one texel wider a side per tier, each top tucked two up inside the tier above, ledges painted as the cloth, pleats running through |
| Height         | cranium top 60, hair 61, neck at 48   | cranium top 58, hair cap 59, chin at 46              |
| Head           | one cube 13 x 12 x 13, chin to crown  | one cube 11 x 12 x 12: narrower, longer; like the skins' heads, framed by the fringe and the side hair to the chin |
| Neck           | 6 wide                                | 4 wide, 2.5 showing over the collar                  |
| Face rows      | fringe r0..5, lash r5..6, eye r7..9, mouth r10 | fringe r0..4 (strand tips to r5), lash r5..6, eye r7..9, blush r9, mouth r10, chin r11; skin a 0..5, side hair a 6 |
| Eye            | blue iris a 2..3 over r7..9, white a 4 | amber iris a 2..3 over r7..9 dark to light, white a 4 on r7..8, blush under it on r9 |
| Mouth          | three pink texels, the middle deeper  | three pink texels, the middle one deeper             |
| Hand           | 4 x 5 x 4 under an open cuff          | 3 x 4 x 3, the narrow sleeve covering half of it     |
| Hair           | shell, bun, crown, pin, ribbons, strands, back slab | smooth shell over the ears, striped fringe to mid-face, side hair a 6 down to the chin, low ponytail 4 wide hanging 22 to the waist, crimson ribbon with two tails |
| Shoes          | black boots                           | cloth shoes with a pale sole; the toe peeks under the hem |
| Stance         | arms hanging                          | hands folded at the belly in the rest pose, right over left |
| Walk           | swing 26, arms swing                  | swing 20, hip roll +-1.5 deg, lower skirt turns back 45 % of the leg, ponytail and ties a quarter beat late |
| Idle           | breath 0.24                           | breath 0.5 from low to high                          |
| Accent         | gold, jade, silver, red tassel        | none but the crimson hair ribbon                     |

The face is drawn as the anime skins (二次元皮肤) draw theirs, like the male's: the fringe covers the top
half in strands of alternating tone, the eyes sit in the lower half (an amber iris two columns wide
shading dark to light downward, a white column outside it, a lash line on top rising at the inner
corner), no brow, no nose, a blush texel under the white column, a small pink mouth on pale flat skin.
The head is one cube from the chin to the crown, as the skins' heads are (the stepped jaw to a pointed
chin read as uncanny); the face's shape comes from the fringe and the side hair that frame it down to
the chin.

Palette: moon-white ru (月白), lotus-mauve skirt (藕荷) with a darker band, knot and ties, low-saturation
slate-indigo half jacket (靛青), crimson ribbon (绛红). Cloth is plain weave, not silk: a coarser `_weave`,
pale narrow edges at the cuffs, the short sleeves, the jacket's hem, the ties and the skirt's hem, and
four soft vertical pleats down the front of the skirt that run on through all three tiers.
"""
from __future__ import annotations

import math

from ...beastgen.builder import Builder
from ...beastgen.paint import ramp
from ..humanoid import (HumanoidPaint, _box, _clamp, _form, _hash, _leg_angle, _loop, _mix, _pair,
                        _rgb, _rx, _sole_low, _tone, _weave, _xhz)

ID = "myvillage:cultivator"
ENTITY = "cultivator"          # the entity's texture directory
NAME = "cultivator_f_novice"   # file name prefix of the three generated files
LOOK = "f_novice"              # NpcEntity's look id this definition draws
HITBOX = (0.6, 1.9)            # width, height in blocks (same entity as the default look)
SCALE = 0.5                    # one model unit is 1/32 block

HIP = 28.0              # leg pivots and the body bone
SHOULDER = 41.5         # arm pivots
ARM_X = 10.5            # arm pivots' distance from the centre line
NECK = 46.0             # head pivot, the chin's underside
HEAD_TOP = 58.0         # the cranium's top; the hair cap adds one more
CHEST = (33.0, 43.0)    # the ru's body, under the half jacket
JACKET = (33.5, 43.5)   # the half jacket's shell
BAND = (31.0, 33.0)     # the skirt's cloth band at the waist
SKIRT_TOP = 26.0        # the skirt's top piece on the body runs from here up under the band
HIP_TIER = 19.0         # seams between the skirt's tiers on the legs; each lower tier's top tucks two
KNEE_TIER = 10.0        # units up inside the tier above: hip 18..28, knee 9..20, hem 2..11
TUCK = 1.0              # how far either side of a seam the tiers overlap
HEM = 2.0
COLLAR_TOP = 43.0       # where the crossed collar bands start
OPENING = 3.5           # half width of the half jacket's front opening

# ------------------------------------------------------------------------------------ palette
# Entity faces are drawn at 50 to 74 % brightness unless they face up, so the base tones sit high.
RU = ramp("#8392A8", "#9DABBF", "#B6C3D3", "#CCD7E3", "#DEE6EE", "#ECF1F6", "#F8FAFC")      # 月白
INNER = ramp("#B4BBC5", "#CFD4DB", "#E6E9ED", "#F5F6F8", "#FFFFFF")
JACKET_CLOTH = ramp("#1E2531", "#283242", "#334055", "#404F67", "#4F607B", "#617391", "#7688A6")  # 靛青
SKIRT = ramp("#6A5562", "#836D7B", "#9C8695", "#B49FAD", "#C9B6C3", "#DACAD4", "#E9DFE6")      # 藕荷
SASH = ramp("#45343F", "#5A4653", "#705968", "#876E7E", "#9E8696")                              # band, ties
EDGE = ramp("#C9C6BE", "#DEDBD3", "#EEEBE4", "#F8F6F1")                                         # pale edges
SHOE = ramp("#181920", "#23252F", "#30333F", "#404452", "#525767")
SOLE = ramp("#A8A49A", "#CAC6BC", "#E8E4DA")
RIBBON = ramp("#561317", "#781D22", "#9C2A2E", "#BC3C3B", "#D45A55")                           # 绛红
SKIN = ramp("#BA8A74", "#D6A690", "#ECC3AE", "#F8D8C2", "#FEE6DA", "#FFF0E8", "#FFF8F2")  # very pale and flat, a touch pinker than the male's
HAIR = ramp("#0C0B10", "#16141C", "#211E29", "#2E2A38", "#3E394B", "#544E64", "#6D6680")
EYE_WHITE = "#F8FAFF"    # the eye's outer column, white as in the skins
IRIS_TOP = "#5A2E1A"     # the iris column, dark at the top ...
IRIS = "#B2702F"         # ... through a warm amber ...
IRIS_LOW = "#F0C27A"     # ... to light at the bottom (the skins' vertical gradient)
LASH = "#171522"         # the lash line over the eye and its rising inner corner
BLUSH = "#F2A0A0"        # the cheek texel under the eye's white column
MOUTH = "#DA918A"        # pale warm lips
MOUTH_MID = "#C47670"    # the middle texel a little deeper

SKIRT_TIERS = tuple(f"skirt_{tier}_{side}" for tier in ("hip", "knee", "hem") for side in ("right", "left"))

# The hair and the half jacket are shells whose texture is partly cut out, so they cast no baked shadow.
HOLLOW = ("hair", "jacket")


# ------------------------------------------------------------------------------------ model
def build_model():
    b = Builder(ID, look={"bone": "head", "max_yaw": 60.0, "max_pitch": 35.0}, shadow_radius=0.4)
    b.model.scale = SCALE

    # ---- trunk: the ru's body, the skirt's top under a cloth band at the waist, the half jacket's
    # shell over the chest, cut open down the front onto the crossed collar
    b.bone("body", "root", at=(0.0, -HIP, 0.0))
    _box(b, "body", "torso", -6, CHEST[0], -3.5, 12, CHEST[1] - CHEST[0], 7)
    _box(b, "body", "skirt_top", -5, SKIRT_TOP, -3.5, 10, BAND[1] - SKIRT_TOP, 7)
    _box(b, "body", "band", -5.5, BAND[0], -4, 11, BAND[1] - BAND[0], 8)
    _box(b, "body", "knot", -1.5, 31.0, -6, 3, 2, 2)
    _box(b, "body", "jacket", -6.5, JACKET[0], -4.5, 13, JACKET[1] - JACKET[0], 9)
    _box(b, "body", "neck", -2, 42, -2, 4, 5, 4)
    # crossed collar: the left lapel's band runs over the right one (右衽)
    b.bone("collar_over", "body", at=(2.5, -COLLAR_TOP, -3.9), rot=(0.0, 0.0, 30.0))
    b.box_local("collar_over", "collar_over_band", (-1.0, -0.5, -0.5), (2, 10, 1))
    b.bone("collar_under", "body", at=(-2.5, -COLLAR_TOP, -3.7), rot=(0.0, 0.0, -30.0))
    b.box_local("collar_under", "collar_under_band", (-1.0, -0.5, -0.5), (2, 6, 1))
    # the half jacket's straight front edges (对襟), a pale band each side of the opening
    _pair(b, "jacket_edge_{}", "body", (-OPENING - 0.5, -JACKET[1], -4.5),
          boxes=[("jacket_edge_{}_band", (-0.5, 0.0, -0.5), (1, 10, 1))], local=True)

    # ---- the skirt's ties: two strips of the band's cloth hang from the knot and swing on their own
    b.bone("tie_right", "body", at=(-0.8, -31.6, -5.3), rot=(-3.0, 0.0, -3.0))
    b.box_local("tie_right", "tie_right_band", (-1.0, -0.4, -0.5), (2, 10, 1))
    b.bone("tie_left", "body", at=(0.8, -31.6, -5.3), rot=(-3.0, 0.0, 4.0))
    b.box_local("tie_left", "tie_left_band", (-1.0, -0.4, -0.5), (2, 8, 1))

    # ---- head, an odd number of texels wide so the face has a centre column: one cube from the chin
    # to the crown, as the anime skins' heads are; the face shape comes from the fringe and the side
    # hair that frame it; a smooth cut-out hair shell with a stepped-in top
    b.bone("head", "body", at=(0.0, -NECK, 0.0))
    _box(b, "head", "skull", -5.5, 46, -6, 11, 12, 12)
    _box(b, "head", "hair", -6.5, 46, -7, 13, 12, 14)
    _box(b, "head", "hair_cap", -5.5, 58, -6, 11, 1, 12)

    # ---- the low ponytail, hung from the trunk so a turning head does not drag it: the gathered root,
    # the crimson ribbon round it with two short tails, and a narrow tail of hair down the back
    b.bone("hair_back", "body", at=(0.0, -48.5, 6.5), rot=(4.0, 0.0, 0.0))
    b.box_local("hair_back", "hair_back_root", (-1.5, -1.5, -1.5), (3, 3, 3))
    b.box_local("hair_back", "ribbon", (-2.0, -0.5, -2.0), (4, 1, 4))
    b.box_local("hair_back", "hair_back_main", (-2.0, 1.5, -1.0), (4, 16, 2))
    b.box_local("hair_back", "hair_back_tip", (-1.5, 17.5, -1.0), (3, 3, 2))
    b.box_local("hair_back", "hair_back_end", (-1.0, 20.5, -0.75), (2, 2, 1))
    _pair(b, "ribbon_{}", "hair_back", (-1.0, -48.0, 8.8), rot=(6.0, 0.0, 10.0),
          boxes=[("ribbon_{}_tail", (-0.5, 0.0, -0.5), (1, 7, 1))], local=True)

    # ---- arms: a narrow sleeve to the wrist under the half jacket's short sleeve; the forearm's sleeve
    # covers half the small hand. The rest pose folds the hands at the belly, the right over the left.
    _pair(b, "arm_{}", "body", (-ARM_X, -SHOULDER, 0.0), rot=ARM_ROT,
          boxes=[("sleeve_upper_{}", (-2.0, 0.0, -2.0), (4, 9, 4))], local=True)
    # the half jacket's short sleeve: three units deep, on a bone of its own that tips its outer side
    # down so the shoulder line runs on from the jacket instead of squaring off like a pad
    for side, sign in (("right", 1.0), ("left", -1.0)):
        kw = {} if side == "right" else {"mirror": True, "uv_from": "short_sleeve_right"}
        b.bone(f"short_sleeve_{side}_bone", f"arm_{side}", at_local=(2.5 * sign, -1.25, 0.0),
               rot=(0.0, 0.0, -SLEEVE_TIP * sign))     # hinged at its inner top edge, by the jacket
        b.box_local(f"short_sleeve_{side}_bone", f"short_sleeve_{side}", (-5.0 if sign > 0 else 0.0, 0.0, -2.5),
                    (5, 3, 5), **kw)
    for side, rot in (("right", FOREARM_ROT_RIGHT), ("left", FOREARM_ROT_LEFT)):
        own = side == "right"
        fore = b.bone(f"forearm_{side}", f"arm_{side}", at_local=(0.0, 9.0, 0.0), rot=rot)
        for name, origin, size, inflate in (("sleeve_lower_{}", (-2.0, -1.0, -2.0), (4, 10, 4), 0.25),
                                            ("hand_{}", (-1.5, 7.0, -1.5), (3, 4, 3), 0.0)):
            kw = {} if own else {"mirror": True, "uv_from": name.format("right")}
            b.box_local(fore, name.format(side), origin, size, inflate=inflate, **kw)

    # ---- legs: each carries its half of the skirt in three tiers that widen toward the hem, and a cloth
    # shoe whose toe shows under it. The lower two tiers hang from a bone of their own at the hip tier's
    # hem, which the walk turns back against the leg, so the skirt drapes instead of splitting like
    # trousers and the shoe steps out from under it. The knee tier reaches one unit up inside the hip
    # tier so no gap opens at the seam when it turns.
    _pair(b, "leg_{}", "root", (-3.5, -HIP, 0.0),
          boxes=[("skirt_hip_{}", (-7.0, HIP_TIER - TUCK, -4.5), (7, HIP - HIP_TIER + TUCK, 9)),
                 ("shoe_{}", (-6.0, 0.0, -7.0), (5, 3, 9))])
    _pair(b, "skirt_low_{}", "leg_{}", (-3.5, -(HIP_TIER + 0.5), 0.0),
          boxes=[("skirt_knee_{}", (-8.0, KNEE_TIER - TUCK, -5.0), (8, HIP_TIER - KNEE_TIER + 2 * TUCK, 10)),
                 ("skirt_hem_{}", (-9.0, HEM, -5.5), (9, KNEE_TIER + TUCK - HEM, 11))])
    return b.model


# The rest pose: upper arms drawn in and a little forward, forearms raised and turned toward the middle
# so the hands meet at the belly, the right one in front.
ARM_ROT = (-12.0, 0.0, -9.0)
SLEEVE_TIP = 12.0   # degrees the short sleeve's outer side tips down from the arm
FOREARM_ROT_RIGHT = (-88.0, -58.0, 0.0)
FOREARM_ROT_LEFT = (-83.0, 61.0, 0.0)


class _Paint(HumanoidPaint):
    """The painter: one method per material, dispatched by cube name. Skin, hands and the head cube's
    sides come from `HumanoidPaint`; the face, the hair and the clothes are this look's own."""

    SKIN = SKIN
    HAIR = HAIR
    FINGER_ROW = 10.0                        # the hand's last row, bone-local
    HAND_EXCLUDE = ("sleeve_lower_right",)

    def __init__(self, model):
        super().__init__(model, HOLLOW, {
            "torso": self.torso, "skirt_top": self.skirt_top, "band": self.band, "knot": self.knot,
            "jacket": self.jacket, "neck": self.neck,
            "collar_over_band": self.collar, "collar_under_band": self.collar,
            "jacket_edge_right_band": self.jacket_edge,
            "tie_right_band": self.tie, "tie_left_band": self.tie,
            "skull": self.skull,
            "hair": self.hair, "hair_cap": self.hair,
            "hair_back_root": self.ponytail, "hair_back_main": self.ponytail, "hair_back_tip": self.ponytail,
            "hair_back_end": self.ponytail, "ribbon": self.ribbon, "ribbon_right_tail": self.ribbon,
            "sleeve_upper_right": self.sleeve_upper, "short_sleeve_right": self.short_sleeve,
            "sleeve_lower_right": self.sleeve_lower, "hand_right": self.hand,
            "skirt_hip_right": self.skirt, "skirt_knee_right": self.skirt, "skirt_hem_right": self.skirt,
            "shoe_right": self.shoe,
        })

    # ---- face
    def face(self, t):
        """The front of the head as a symmetric texel map copied from the anime skins (二次元皮肤) the
        owner pointed at, keyed on (a, r): a columns from the centre column, r rows down from the
        cranium's top. The fringe covers the top half in strands; the eyes sit in the lower half, each
        an amber iris two columns wide that runs dark to light downward with a white column outside it,
        a lash line on top whose inner corner rises a row; no brow (under the fringe), no nose, a blush
        texel under the white column, and a small pink mouth on pale flat skin."""
        x, h, _ = _xhz(t)
        a = abs(int(round(x)))            # column from the centre column, 0..5
        r = int(HEAD_TOP - h)             # row from the top of the cranium, 0..11
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
                return _mix(_rgb(LASH), skin(), 0.45)       # its tail toward the side hair
        if r in (7, 8, 9):
            if a in (2, 3):
                return _rgb((IRIS_TOP, IRIS, IRIS_LOW)[r - 7])   # the iris, dark to light downward
            if a == 4:
                if r < 9:
                    return _rgb(EYE_WHITE)                  # the white column outside the iris
                return _mix(skin(), _rgb(BLUSH), 0.45)      # the blush under it
        if r == 10:
            if a == 0:
                return _mix(skin(), _rgb(MOUTH_MID), 0.9)
            if a == 1:
                return _mix(skin(), _rgb(MOUTH), 0.5)
        return skin(-0.5 * self.occ.contact(t) - 0.8 * self.occ.overhang(t))

    def hair_on_side(self, z, h):
        return _hair_on_side(z, h)

    def skull(self, t):
        if t.face == "UP":
            # the underside: under the chin in front, in shadow; under the ponytail's hair behind
            _, _, z = _xhz(t)
            return _tone(SKIN, 3.4) if z < 1.5 else _tone(HAIR, 1.2)
        return super().skull(t)

    def head_side(self, t, z, h):
        """Skin where the hair shell is cut away on the cranium's side: the temple and a small ear."""
        zc = int(round(z))
        tone = 4.1 - 0.4 * _clamp((z + 4.0) / 4.0) - 0.7 * _clamp((50.0 - h) / 4.0)
        if 50.0 < h < 53.0:
            if zc == -2:
                tone = 4.9                        # the ear's rim
            elif zc == -1:
                tone = 2.8 if 51.0 < h < 52.0 else 4.4
            elif zc == -3:
                tone -= 0.4
        return _tone(SKIN, tone)

    # ---- hair
    def hair_tone(self, t, base=2.5):
        x, h, z = _xhz(t)
        if t.n[1] < -0.5:
            # combed straight back over the crown toward the ponytail: streaks run front to back
            streak = _hash(int(round(x)), 0, 5)
            return base + 0.3 + (streak - 0.5) * 1.6 + 1.3 * _clamp(1.0 - abs(z + 1.5 + 0.4 * math.sin(x)) / 1.4)
        if t.n[2] > 0.5:
            # the back of the head: streaks converge toward the ponytail's root at the nape
            along = x * 8.0 / (h - 42.0)
        elif abs(t.n[0]) > 0.5:
            along = 0.7 * z - 0.4 * (h - 50.0)        # the sides comb back and down
        else:
            along = x
        streak = _hash(int(round(along)), 0, 7)
        tone = base + (streak - 0.5) * 1.1 + _form(t, 0.8)
        tone += 1.5 * _clamp(1.0 - abs(h - 55.6 - 0.5 * math.sin(along * 0.9)) / 1.2)  # the sheen band
        tone -= 0.8 * _clamp((50.0 - h) / 4.0)
        return tone

    def hair(self, t):
        x, h, z = _xhz(t)
        f = t.face
        a = abs(int(round(x)))
        if t.cube == "hair":
            if f == "UP":
                return None  # open underneath, like the cut-outs
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
        if f == "DOWN" and t.cube == "hair":
            tone -= 1.4 * self.occ.contact(t)
        return _tone(HAIR, tone)

    def ponytail(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f in ("DOWN", "UP"):
            return _tone(HAIR, 1.4)
        if t.cube == "hair_back_root":
            return _tone(HAIR, 2.6 + _form(t, 1.0) + (_hash(int(round(lx)), int(ly), 3) - 0.5) * 1.2)
        along = lx if f in ("NORTH", "SOUTH") else lz
        tone = 2.5 + (_hash(int(round(along * 1.5)), int(ly * 0.15), 13) - 0.5) * 1.6 + _form(t, 0.9)
        tone += 1.4 * _clamp(1.0 - abs(ly - 5.0 - 0.6 * math.sin(along * 1.3)) / 1.3)   # the sheen band
        tone -= 1.0 * _clamp((ly - 12.0) / 9.0)                                            # darker to the tip
        return _tone(HAIR, tone - 1.0 * self.occ.overhang(t))

    def ribbon(self, t):
        lx, ly, lz = t.local
        if t.cube == "ribbon":
            if t.face in ("DOWN", "UP"):
                return _tone(RIBBON, 1.6)
            return _tone(RIBBON, 3.0 + _form(t, 0.6) + (0.5 if int(math.floor(lx + lz)) % 2 == 0 else 0.0))
        tone = 2.8 + _form(t, 0.5) - 0.5 * _clamp((ly - 3.0) / 4.0)
        if t.face not in ("NORTH", "SOUTH"):
            tone -= 0.6
        return _tone(RIBBON, tone)

    # ---- cloth
    def cloth(self, t, ramp_, tone, roundness=1.0, weave=0.5, **kw):
        """Plain-woven cloth: form light, baked shadows and a coarse weave."""
        tone += self.light(t, roundness, **kw) + _weave(t, weave, 1.0, 3)
        return _tone(ramp_, tone)

    def torso(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "DOWN":
            # around the neck: the white inner collar, then the ru's own collar
            if max(abs(x), abs(z) * 1.2) < 3.2:
                return _tone(INNER, 3.0 - 1.4 * self.occ.contact(t))
            return _tone(RU, 3.4)
        if f != "NORTH":
            return self.cloth(t, RU, 4.6, 0.5)
        over = 2.5 - (COLLAR_TOP - h) * math.tan(math.radians(30.0))   # centre lines of the collar bands
        under = -over
        shadow = 1.2 * self.occ.contact(t) + 1.8 * self.occ.overhang(t)
        shadow += 1.0 * _clamp(1.0 - (OPENING - abs(x)) / 1.2)          # under the jacket's edge
        if under < x < over:
            return _tone(INNER, 3.6 - shadow)
        tone = 5.0 + _weave(t, 0.5) + _form(t, 0.5) + 0.4 * math.sin(x * 0.9 + (COLLAR_TOP - h) * 0.55)
        return _tone(RU, tone - shadow)

    def collar(self, t):
        lx, ly, lz = t.local
        over = t.cube == "collar_over_band"
        piping = (lx < 0.0) if over else (lx > 0.0)
        shade = 1.0 * self.occ.contact(t) + 1.5 * self.occ.overhang(t)
        if t.face == "NORTH" and piping:
            return _tone(EDGE, 2.8 - shade)
        tone = 3.4 + (0.4 if t.face == "NORTH" else -0.8) - shade
        return _tone(RU, tone + _weave(t, 0.3))

    def jacket(self, t):
        x, h, z = _xhz(t)
        f = t.face
        ax = abs(x)
        if f == "NORTH" and ax < OPENING:
            return None
        if f == "DOWN":
            if ax < 2.8 and z < 2.8:
                return None  # the neck opening
            return _tone(JACKET_CLOTH, 4.4 + _weave(t, 0.4) - 0.9 * _clamp(1.0 - max(ax - 2.8, z - 2.8) / 1.5))
        if f == "UP":
            return _tone(JACKET_CLOTH, 0.8)
        tone = 3.6 + self.light(t, 1.0, cast=1.8, crevice=1.0) + _weave(t, 0.5, 1.0, 4)
        tone += 0.8 * _clamp((h - 41.0) / 2.5)               # light on the shoulders
        tone += 0.5 * math.sin((x if abs(t.n[2]) > 0.5 else z) * 1.1 + 0.3 * h) * _clamp((41.0 - h) / 4.0)
        if h < JACKET[0] + 1.0:
            return _tone(EDGE, 2.0 + _form(t, 0.8))          # a pale narrow edge at the hem
        return _tone(JACKET_CLOTH, tone)

    def jacket_edge(self, t):
        lx, ly, lz = t.local
        if t.face != "NORTH":
            return _tone(EDGE, 1.0)
        return _tone(EDGE, 2.8 - 1.4 * self.occ.overhang(t) - 0.4 * _clamp((ly - 6.0) / 4.0) + _weave(t, 0.3))

    def short_sleeve(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "UP":
            # the short sleeve's opening: a pale edge round the dark inside, the sleeve under it
            edge = min(lx + 5.0, -lx, lz + 2.5, 2.5 - lz)
            return _tone(EDGE, 1.6) if edge < 0.5 else _tone(JACKET_CLOTH, 0.4)
        if f == "DOWN":
            return _tone(JACKET_CLOTH, 4.0 + _weave(t, 0.4))
        if ly > 2.0:
            return _tone(EDGE, 2.2 + _form(t, 0.8))       # pale edge at the short sleeve's end
        return self.cloth(t, JACKET_CLOTH, 3.9, 1.0, exclude=("sleeve_upper_right",))

    def sleeve_upper(self, t):
        lx, ly, lz = t.local
        if t.face == "DOWN":
            return _tone(RU, 2.4)
        crease = math.sin(ly * 1.4 + 0.5 * math.sin((lx + lz) * 0.8))
        tone = 4.6 + 0.8 * crease * _clamp((ly - 3.0) / 3.0) - 0.6 * _clamp((ly - 2.0) / 7.0)  # creases at the elbow
        return self.cloth(t, RU, tone, 1.1, exclude=("short_sleeve_right", "sleeve_lower_right"))

    def sleeve_lower(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "UP":
            # the cuff's opening round the wrist
            edge = min(lx + 2.25, 2.25 - lx, lz + 2.25, 2.25 - lz)
            return _tone(EDGE, 1.8) if edge < 0.75 else _tone(RU, 0.6)
        if f == "DOWN":
            return _tone(RU, 2.2)
        if ly > 8.25:
            return _tone(EDGE, 2.4 + _form(t, 0.8))        # the cuff's pale narrow edge
        along = lx if f in ("NORTH", "SOUTH") else lz
        fold = math.sin(along * 1.3 + 0.4 * ly)
        tone = 4.9 + 0.5 * fold * _clamp((ly - 1.0) / 4.0)
        return self.cloth(t, RU, tone, 1.1, exclude=("sleeve_upper_right", "hand_right"))

    def skirt_top(self, t):
        if t.face in ("DOWN", "UP"):
            return _tone(SKIRT, 2.4)
        return self.cloth(t, SKIRT, 4.2, 0.8)

    def band(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "DOWN":
            return _tone(SASH, 3.2 - 1.6 * self.occ.contact(t))
        if f == "UP":
            return _tone(SASH, 0.6)
        tone = 2.8 + _form(t, 1.2) - 1.2 * self.occ.overhang(t) + _weave(t, 0.5)
        if int(h - BAND[0]) == 1:
            tone += 0.4
        return _tone(SASH, tone)

    def knot(self, t):
        x, h, z = _xhz(t)
        if t.face in ("DOWN", "UP"):
            return _tone(SASH, 1.0 if t.face == "UP" else 3.4)
        tone = 3.0 + _form(t, 1.3) + (0.6 if abs(x) < 0.6 else -0.2)   # the knot's middle pinched up
        return _tone(SASH, tone - 0.6 * self.occ.overhang(t))

    def tie(self, t):
        lx, ly, lz = t.local
        length = 10.0 if t.cube == "tie_right_band" else 8.0
        v = ly + 0.4
        if t.face not in ("NORTH",):
            return _tone(SASH, 1.2)
        if length - v < 1.0:
            return _tone(EDGE, 1.6)                       # a pale edge at the tie's end
        tone = 3.0 + 0.4 * math.sin(v * 0.9) - 1.6 * self.occ.overhang(t) + (0.5 if lx < 0.0 else 0.0)
        return _tone(SASH, tone + _weave(t, 0.3))

    def skirt(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "UP":
            return _tone(SKIRT, 0.4)
        inner = f == "EAST"  # the face between the legs: the same cloth in shadow, seen mid-stride
        # four soft vertical pleats across the front and back, two down each side, the same columns on
        # every tier so they run on from the hip to the hem
        if f == "DOWN":
            # the one-unit ledge where a tier widens faces up, so it draws at full brightness: paint it
            # as dark as the cloth beside it looks, pleats and all, so the seam does not read as a step
            front = abs(z) > 4.4
            pleat = math.cos(((x + 0.5) if front else z) * 2.0 * math.pi / (4.5 if front else 5.5))
            return _tone(SKIRT, 1.2 + 0.6 * math.tanh(2.2 * pleat))
        if f in ("NORTH", "SOUTH"):
            pleat = math.cos((x + 0.5) * 2.0 * math.pi / 4.5)
        else:
            pleat = math.cos(z * 2.0 * math.pi / 5.5)
        crest = pleat
        pleat = math.tanh(2.2 * pleat)                                    # soft-edged bands, not a ripple
        depth = 0.6 + 0.4 * _clamp((HIP - h) / 14.0)
        tone = 4.8 + _weave(t, 0.6, 0.9, 3)
        # the tiers do not shade each other: the skirt is one cloth flaring out, not stacked boxes
        tone += (-1.3 - 0.8 * _clamp((h - 16.0) / 8.0)) if inner else self.light(t, 0.4, cast=2.2, exclude=SKIRT_TIERS)
        tone += depth * (0.8 * pleat + 0.45 * crest)                    # each fold rounds to a crest
        tone -= 0.9 * _clamp((HEM + 7.0 - h) / 7.0)                     # a little darker toward the hem
        if int(h - HEM) == 0:
            return _tone(EDGE, (1.2 if inner else 2.2 + _form(t, 0.7)) + 0.3 * pleat)   # pale hem edge
        return _tone(SKIRT, tone)

    def shoe(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "UP":
            return _tone(SOLE, 0.0)
        if f == "DOWN":
            return _tone(SHOE, 3.0 if z < -6.0 else 1.2)
        if h < 1.0:
            return _tone(SOLE, 1.6 + _form(t, 0.6))
        return _tone(SHOE, 2.0 + _form(t, 0.8) + (0.8 if z < -6.0 else 0.0) - 1.0 * self.occ.overhang(t))


# ------------------------------------------------------------------------------------ shapes
def _hairline(a):
    """Height of the front hairline `a` columns from the centre: the fringe covers the top half of the
    face as in the anime skins, its strand tips one row deeper on the odd columns (h 52, the sixth row)
    than on the even ones (h 53); the side hair runs down to the chin at the shell's edge column
    (a = 6)."""
    if a >= 6:
        return 46.0                       # the side hair frames the face down to the chin
    return 52.0 if a % 2 == 1 else 53.0


def _hair_on_side(z, h):
    """Hair on the side of the head: the side lock at the front down to the chin, combed down over the
    ears to h 50 and back to the ponytail; below that it is cut away beside the cheek and hangs behind."""
    if z < -5.0:
        return True                       # the side lock, the shell's and the head cube's front column
    if h >= 50.0:
        return True
    return z >= 0.0


def painter(model):
    return _Paint(model)


# ------------------------------------------------------------------------------------ clips
WALK_LENGTH = 1.0     # seconds per stride cycle in the file; animateWalk rescales it to the distance walked
WALK_SWING = 20.0     # degrees each leg swings either way: a shorter stride than the male's 26
IDLE_LENGTH = 4.0
SOLE_Z = (-7.0, 2.0)  # the shoe sole's toe and heel, from the leg's axis
SWING_LIFT = 1.5      # how far the swinging foot clears the ground
HIP_SWAY = 1.5        # degrees the body rolls toward the planted leg
SKIRT_DRAPE = 0.45     # share of the leg's swing the lower skirt turns back
LAG = math.pi / 4.0   # the ponytail and the ties trail a quarter beat (a beat is half a stride, pi)


def _walk_pose(phase):
    right = _leg_angle(phase, WALK_SWING)             # negative x rotation brings a limb forward
    left = -right
    right_plants = math.cos(phase) >= 0.0             # the leg moving back carries the weight
    stance, swing = (right, left) if right_plants else (left, right)
    drop = _sole_low(stance, HIP, SOLE_Z)             # the hips ride on the planted foot
    lift = SWING_LIFT * abs(math.cos(phase)) + max(0.0, drop - _sole_low(swing, HIP, SOLE_Z))
    forward = max(0.0, -right, -left)
    s = right / WALK_SWING
    sway = HIP_SWAY * math.cos(phase)                 # toward the planted leg
    trail = math.cos(phase - LAG)                     # the same sway a quarter beat late
    bob = math.sin(2.0 * (phase - LAG))               # the step's bounce a quarter beat late
    return {
        "root": {"position": (0.0, -drop, 0.0)},
        "leg_right": {"rotation": (right, 0.0, 0.0), "position": (0.0, 0.0 if right_plants else lift, 0.0)},
        "leg_left": {"rotation": (left, 0.0, 0.0), "position": (0.0, lift if right_plants else 0.0, 0.0)},
        "body": {"rotation": (1.0, 2.0 * s, sway)},
        "head": {"rotation": (-1.0, -1.5 * s, -0.7 * sway)},
        "skirt_low_right": _rx(-SKIRT_DRAPE * right),
        "skirt_low_left": _rx(-SKIRT_DRAPE * left),
        "arm_right": _rx(-1.2 * s),
        "arm_left": _rx(-1.2 * s),
        # the ties ride the leg in front of them so the skirt never comes through, and sway late
        "tie_right": {"rotation": (-0.9 * forward - 1.5, 0.0, -2.5 * trail)},
        "tie_left": {"rotation": (-0.9 * forward - 1.5, 0.0, -2.0 * math.cos(phase - LAG - 0.3))},
        # the ponytail and its ribbon trail behind and settle twice a stride
        "hair_back": {"rotation": (2.5 + 2.0 * bob, 0.0, -3.0 * trail)},
        "ribbon_right": {"rotation": (5.0 + 4.0 * bob, 0.0, -2.5 * trail)},
        "ribbon_left": {"rotation": (5.0 + 4.0 * math.sin(2.0 * (phase - LAG) - 0.5), 0.0, -2.5 * trail)},
    }


def _idle_pose(phase):
    breath = math.sin(phase)
    slow = math.sin(phase - 0.9)
    return {
        "body": {"position": (0.0, 0.25 * breath, 0.0)},      # half a texel from low to high
        "head": _rx(0.7 * slow),
        "hair_back": _rx(0.6 * slow),
        "ribbon_right": {"rotation": (1.5 * slow, 0.0, 1.2 * math.sin(phase - 1.7))},
        "ribbon_left": {"rotation": (1.5 * math.sin(phase - 1.4), 0.0, 1.2 * math.sin(phase - 2.2))},
        "tie_right": {"rotation": (0.6 * slow, 0.0, 1.0 * math.sin(phase - 2.0))},
        "tie_left": {"rotation": (0.6 * math.sin(phase - 1.1), 0.0, 1.0 * math.sin(phase - 2.6))},
    }


def clips(model):
    return [_loop("idle", IDLE_LENGTH, _idle_pose, 8), _loop("walk", WALK_LENGTH, _walk_pose, 32)]
