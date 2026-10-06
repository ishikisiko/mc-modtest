"""Cultivator (修仙者): a young sect disciple in a pale robe under a long sleeveless indigo vest.

Design space (tools/beastgen/builder.py): ground y = 0, up -Y, front -Z, the figure's left +X. One
unit is HALF a vanilla model unit (1/32 block): the model file carries scale 0.5, so geometry and
texels are twice as fine as a vanilla mob. Heights below are written as h = units above the ground.

The look is built from real layers instead of one painted box: a crossed robe collar and the vest's
border bands lie on the chest, the belt wraps over both, sleeves come out from under shoulder wings and
widen to an open cuff with a hanging drape, the skirt flares in two tiers, vest panels, sash ends and a
jade pendant hang from the belt, and the hair is a cut-out shell with a bun, a crown, a pin, ribbons
and two loose strands. The head is a cranium over a jaw that steps in twice toward a narrow chin, so
the face is taller than wide; the hair leaves the forehead open under a straight hairline, with
sideburns down to the cranium's bottom, and the face is a plain symmetric texel map: a brow lying
directly on a one-row eye (white either side of the iris) whose tail fades into the sideburn on the
same row, a jaw contour and a small muted mouth. The painter then shades every texel from where it sits in 3D (form light,
shadows cast by the layer above, crevices beside raised bands) and adds cloth folds, dye and trim.
"""
from __future__ import annotations

import math

from ...beastgen.builder import Builder
from ...beastgen.paint import ramp, value_noise
from ..humanoid import (HumanoidPaint, _box, _clamp, _form, _fret, _hash, _leg_angle, _loop, _mix, _pair,
                        _rgb, _rx, _sole_low, _tone, _weave, _xhz)

ID = "myvillage:cultivator"
ENTITY = "cultivator"  # the entity's texture directory
NAME = "cultivator"    # file name prefix of the three generated files
LOOK = "default"       # NpcEntity's look id this definition draws
HITBOX = (0.6, 1.9)   # width, height in blocks (server registration)
SCALE = 0.5           # one model unit is 1/32 block

HIP = 29.0            # leg pivots and the body bone
SHOULDER = 44.0
NECK = 48.0           # head pivot, the chin's underside
HEAD_TOP = 60.0       # the cranium's top; hair adds one more
BELT = (30.0, 35.0)
HEM = 3.0
TIER = 16.0           # where the skirt's lower tier begins
KNEE_BAND = 7.0       # lower edge of the skirt's woven band
COLLAR_TOP = 47.5     # where the collar bands and the vest's border bands start
CAP_SLOPE = 12.0      # degrees the vest's shoulder caps fall away from the neck

# ------------------------------------------------------------------------------------ palette
# Entity faces are drawn at 50 to 74 % brightness unless they face up, so the base tones sit high.
ROBE = ramp("#7888A0", "#93A3B8", "#ADBBCC", "#C6D1DE", "#DBE3EC", "#EBF0F5", "#F8FAFC")
DYE = ramp("#1B4A60", "#27687F", "#35869E", "#52A3B8", "#7FC0CF", "#AEDAE3", "#D6EEF2")
INNER = ramp("#B4BBC5", "#CFD4DB", "#E6E9ED", "#F5F6F8", "#FFFFFF")
VEST = ramp("#111830", "#182243", "#202D58", "#2A3A6E", "#364A86", "#445C9E", "#5670B4")
BROCADE = ramp("#30466E", "#405C8C", "#5373A8", "#698CC0", "#84A6D6", "#A4C0E6")
COLLAR = ramp("#174354", "#205D74", "#2C7892", "#3D95AE", "#5FB2C6")
GOLD = ramp("#7A6432", "#9E8444", "#C4A75C", "#E2C97F", "#F6E6AC")
JADE = ramp("#22665A", "#338C7A", "#52B09A", "#84D6BA", "#BCF0DB", "#E6FCF1")
SILVER = ramp("#7A8492", "#9BA5B2", "#BEC7D1", "#DDE3EA", "#F6F8FA")
BELT_CLOTH = ramp("#16121E", "#221D2D", "#302A3D", "#40394F", "#534B63")
SKIN = ramp("#A87C64", "#C4977A", "#DDB394", "#EEC9AC", "#F8DAC2", "#FFE9D6", "#FFF4EA")
HAIR = ramp("#0B0C13", "#141823", "#1E2433", "#2B3346", "#3B465E", "#525F7B", "#6C7A97")
BOOT = ramp("#14151B", "#20222A", "#2E313B", "#40444F")
SOLE = ramp("#A8A8A0", "#CECEC5", "#ECECE4")
TASSEL = ramp("#6A1A1A", "#9C2A2A", "#C8413A", "#E36354")
CORD = ramp("#665030", "#8A6E3E", "#B08E50")
MOUTH = "#B98377"
EYE_WHITE = "#F4F5F7"
IRIS = "#2B5F7A"
BROW = "#262733"

# The hair and the vest are shells whose texture is partly cut out, so they cast no baked shadow.
HOLLOW = ("hair", "vest")


# ------------------------------------------------------------------------------------ model
def build_model():
    b = Builder(ID, look={"bone": "head", "max_yaw": 60.0, "max_pitch": 35.0}, shadow_radius=0.4)
    b.model.scale = SCALE

    # ---- trunk: pale robe under a dark vest shell that is cut open down the front
    b.bone("body", "root", at=(0.0, -HIP, 0.0))
    _box(b, "body", "torso", -7, 29, -4, 14, 17, 8)
    _box(b, "body", "vest", -8, 29, -5, 16, 18, 10)
    _box(b, "body", "neck", -3, 46, -3, 6, 3, 6)
    _box(b, "body", "belt", -9, BELT[0], -6, 18, 5, 12)
    _box(b, "body", "buckle", -2, 30.5, -7, 4, 4, 1)
    # crossed robe collar: the left lapel's band runs over the right one (右衽)
    b.bone("collar_over", "body", at=(3.5, -COLLAR_TOP, -4.1), rot=(0.0, 0.0, 27.0))
    b.box_local("collar_over", "collar_over_band", (-1.0, -1.5, -0.5), (2, 15, 1))
    b.bone("collar_under", "body", at=(-3.5, -COLLAR_TOP, -3.9), rot=(0.0, 0.0, -27.0))
    b.box_local("collar_under", "collar_under_band", (-1.0, -1.5, -0.5), (2, 9, 1))
    # the vest's border bands down both edges of its opening, and its caps over the sleeve heads
    _pair(b, "vest_edge_{}", "body", (-5.0, -COLLAR_TOP, -5.5), rot=(0.0, 0.0, -7.0),
          boxes=[("vest_edge_{}_band", (-1.0, -0.5, -0.5), (2, 13, 1))], local=True)
    _pair(b, "vest_cap_{}", "body", (-8.0, -47.0, 0.0), rot=(0.0, 0.0, -CAP_SLOPE),
          boxes=[("vest_cap_{}_shell", (-6.5, 0.0, -3.5), (7, 3, 7))], local=True)

    # ---- head, an odd number of texels wide so the face has a centre column: the cranium, then a jaw
    # in three steps that narrow toward the chin from the front and rise toward the ear from the side
    # (all flush with the face plane), cut-out hair shell with a stepped-in top, nose, bun with crown
    # and pin, ribbons, two loose strands
    b.bone("head", "body", at=(0.0, -NECK, 0.0))
    _box(b, "head", "skull", -6.5, 52, -6.5, 13, 8, 13)
    _box(b, "head", "jaw", -5.5, 50, -6.5, 11, 2, 9)
    _box(b, "head", "jaw_low", -4.5, 49, -6.5, 9, 1, 8)
    _box(b, "head", "chin", -3.5, 48, -6.5, 7, 1, 6)
    _box(b, "head", "hair", -7.5, 48, -7.5, 15, 12, 15)
    _box(b, "head", "hair_cap", -6.5, 60, -6.5, 13, 1, 13)
    _box(b, "head", "nose", -0.5, 51, -7.5, 1, 2, 1)
    _box(b, "head", "bun", -2.5, 61, -1.5, 5, 4, 5)
    _box(b, "head", "crown", -3.5, 62, -2.5, 7, 2, 7)
    _box(b, "head", "pin", -6.5, 62.5, 0.5, 13, 1, 1)
    _box(b, "head", "pin_knob", -8.5, 62, 0, 2, 2, 2)
    for side, x0 in (("right", -2.5), ("left", 0.5)):
        kw = {} if side == "right" else {"mirror": True, "uv_from": "ribbon_root_right"}
        _box(b, "head", f"ribbon_root_{side}", x0, 61, 3.5, 2, 1, 5, **kw)
    _pair(b, "ribbon_{}", "head", (-1.5, -61.5, 8.1), rot=(3.0, 0.0, 4.0),
          boxes=[("ribbon_{}_tail", (-1.0, -0.5, -0.5), (2, 15, 1))], local=True)
    _pair(b, "strand_{}", "head", (-7.0, -57.0, -8.0),
          boxes=[("strand_{}_lock", (-0.5, 0.0, -0.5), (1, 12, 1))], local=True)

    # ---- long hair down the back, hung from the trunk so a turning head does not drag it
    b.bone("hair_back", "body", at=(0.0, -49.5, 6.1))
    b.box_local("hair_back", "hair_back_main", (-4.5, -0.5, -1.0), (9, 15, 2))
    b.box_local("hair_back", "hair_back_tip", (-2.5, 14.5, -1.0), (5, 3, 2))

    # ---- arms: sleeve from under the cap; the lower sleeve is as wide but deeper to the back, with an
    # open cuff, a hanging drape and the hand
    _pair(b, "arm_{}", "body", (-11.0, -SHOULDER, 0.0), rot=(0.0, 0.0, 4.0),
          boxes=[("sleeve_upper_{}", (-3.0, -1.0, -3.0), (6, 11, 6))], local=True)
    for side in ("right", "left"):
        own = side == "right"
        fore = b.bone(f"forearm_{side}", f"arm_{side}", at_local=(0.0, 9.5, 0.0), rot=(-10.0, 0.0, 0.0))
        for name, origin, size, inflate in (("sleeve_lower_{}", (-3.0, -1.5, -3.0), (6, 12, 8), 0.25),
                                            ("sleeve_drape_{}", (-2.0, 10.5, 1.0), (4, 7, 4), 0.0),
                                            ("hand_{}", (-2.0, 9.5, -2.5), (4, 5, 4), 0.0)):
            kw = {} if own else {"mirror": True, "uv_from": name.format("right")}
            b.box_local(fore, name.format(side), origin, size, inflate=inflate, **kw)

    # ---- hung from the belt: vest panels, sash ends, jade pendant on the left hip
    _pair(b, "vest_front_{}", "body", (-5.5, -30.5, -5.25), rot=(-5.0, 0.0, 0.0),
          boxes=[("vest_front_{}_panel", (-3.0, -0.5, -0.5), (6, 17, 1))], local=True)
    b.bone("vest_back", "body", at=(0.0, -30.5, 5.25), rot=(5.0, 0.0, 0.0))
    b.box_local("vest_back", "vest_back_panel", (-8.0, -0.5, -0.5), (16, 19, 1))
    b.bone("sash", "body", at=(0.0, -31.0, -6.4), rot=(-6.0, 0.0, 0.0))
    b.box_local("sash", "sash_long", (-2.5, -0.5, -0.5), (2, 14, 1))
    b.box_local("sash", "sash_short", (0.5, -0.5, -0.5), (2, 11, 1))
    b.bone("pendant", "body", at=(5.5, -30.2, -6.6), rot=(-4.0, 0.0, 0.0))
    b.box_local("pendant", "pendant_cord", (-0.5, 0.0, -0.5), (1, 5, 1))
    b.box_local("pendant", "pendant_jade", (-1.5, 5.0, -0.5), (3, 3, 1))
    b.box_local("pendant", "pendant_tassel", (-1.0, 8.0, -0.5), (2, 6, 1))

    # ---- legs: each carries its half of the two-tier skirt and a boot whose toe shows under the hem
    _pair(b, "leg_{}", "root", (-3.5, -HIP, 0.0),
          boxes=[("robe_upper_{}", (-8.0, TIER, -5.0), (8, 13, 10)),
                 ("robe_lower_{}", (-9.0, HEM, -6.0), (9, 13, 12)),
                 ("boot_{}", (-6.5, 0.0, -7.0), (6, 5, 10))])
    return b.model


class _Paint(HumanoidPaint):
    """The painter: one method per material, dispatched by cube name. Skin, hands, nose, cranium and jaw
    come from `HumanoidPaint`; the face, the hair and the clothes are the cultivator's own."""

    SKIN = SKIN
    HAIR = HAIR
    NOSE_TOP = 53.0                          # the nose box spans h 51..53
    FINGER_ROW = 13.5                        # the hand's last row, bone-local
    HAND_EXCLUDE = ("sleeve_drape_right",)
    JAW_BACK = 2.5                           # the jaw box's back face

    def __init__(self, model):
        super().__init__(model, HOLLOW, {
            "torso": self.torso, "vest": self.vest, "neck": self.neck, "belt": self.belt, "buckle": self.buckle,
            "collar_over_band": self.collar, "collar_under_band": self.collar,
            "vest_edge_right_band": self.vest_edge, "vest_cap_right_shell": self.vest_cap,
            "skull": self.skull, "jaw": self.jaw, "jaw_low": self.jaw, "chin": self.jaw, "hair": self.hair, "hair_cap": self.hair, "nose": self.nose, "bun": self.bun,
            "crown": self.crown, "pin": self.pin, "pin_knob": self.pin,
            "ribbon_root_right": self.ribbon, "ribbon_right_tail": self.ribbon,
            "strand_right_lock": self.strand, "hair_back_main": self.hair_back, "hair_back_tip": self.hair_back,
            "sleeve_upper_right": self.sleeve_upper, "sleeve_lower_right": self.sleeve_lower,
            "sleeve_drape_right": self.sleeve_drape, "hand_right": self.hand,
            "vest_front_right_panel": self.panel, "vest_back_panel": self.panel,
            "sash_long": self.sash, "sash_short": self.sash,
            "pendant_cord": self.cord, "pendant_jade": self.jade_ring, "pendant_tassel": self.tassel,
            "robe_upper_right": self.skirt, "robe_lower_right": self.skirt, "boot_right": self.boot,
        })

    def face(self, t):
        """The front of the head as a symmetric texel map, keyed on (a, r): a columns from the centre
        column, r rows down from the cranium's top. Skin is plain apart from the hairline's shadow and
        the baked occlusion beside and under the nose."""
        x, h, _ = _xhz(t)
        a = abs(int(round(x)))            # column from the centre column, 0..6
        r = int(HEAD_TOP - h)             # row from the top of the cranium, 0..11
        if h >= _hairline(a):
            return _tone(HAIR, 1.6)
        tone = 4.6 - 0.8 * _clamp(1.5 - (_hairline(a) - h))   # the hairline's shadow
        skin = lambda d=0.0: _tone(SKIN, tone + d)  # noqa: E731  (no cloth noise: the face stays symmetric)
        if r == 4 and 2 <= a <= 4:
            return _rgb(BROW)                           # the brow lies directly on the eye
        if r == 4 and a == 5:
            return _mix(_rgb(BROW), skin(), 0.5)        # its tail fades into the sideburn (剑眉入鬓)
        if r == 5 and 2 <= a <= 4:
            return _rgb(IRIS) if a == 3 else _rgb(EYE_WHITE)
        if a == 5 and r in (8, 9):
            return skin(-0.5)                           # the jaw's contour
        if r == 10:
            if a <= 1:
                return _rgb(MOUTH)
            if a == 4:
                return skin(-0.5)
        if r == 11 and a == 3:
            return skin(-0.5)
        return skin(-0.7 * self.occ.contact(t) - 1.1 * self.occ.overhang(t))

    def hair_on_side(self, z, h):
        return _hair_on_side(z, h)

    def head_side(self, t, z, h):
        """Skin where the hair shell is cut away on the cranium's side: temple and ear."""
        zc = int(round(z))
        tone = 4.0 - 0.4 * _clamp((z + 4.0) / 4.0) - 0.7 * _clamp((52.0 - h) / 4.0)
        if 52.0 < h < 55.0:
            if zc == -2:
                tone = 4.9                        # the ear's rim
            elif zc == -1:
                tone = 2.6 if 53.0 < h < 54.0 else 4.4
            elif zc == -3:
                tone -= 0.5
        return _tone(SKIN, tone)

    # ---- hair
    def hair_tone(self, t, base=2.6):
        x, h, z = _xhz(t)
        if t.n[1] < -0.5:
            # combed toward the bun: streaks run radially on the crown
            ang = math.atan2(x, z - 1.0)
            streak = _hash(int(math.floor(ang * 7.0)), 0, 5)
            ring = math.hypot(x, z - 1.0)
            return base + 0.3 + (streak - 0.5) * 2.0 + 1.2 * _clamp(1.0 - abs(ring - 5.0) / 1.2)
        along = x if abs(t.n[2]) > 0.5 else z
        streak = _hash(int(round(along)), int(h * 0.12), 7)
        tone = base + (streak - 0.5) * 1.9 + _form(t, 0.8)
        tone += 1.7 * _clamp(1.0 - abs(h - 57.2 - 0.7 * math.sin(along * 0.9)) / 1.2)  # the sheen band
        tone -= 0.8 * _clamp((52.0 - h) / 5.0)
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
        if f == "NORTH":
            tone -= 0.7 * _clamp(1.5 - (h - _hairline(a)))  # darker at the root line over the forehead
        if f == "DOWN":
            tone -= 1.4 * self.occ.contact(t)
        return _tone(HAIR, tone)

    def bun(self, t):
        x, h, z = _xhz(t)
        tone = 3.0 + _form(t, 1.2) + (_hash(int(h * 2.0), int(round(x + z)), 9) - 0.5) * 1.4
        tone -= 1.0 * self.occ.contact(t)
        return _tone(HAIR, tone)

    def strand(self, t):
        lx, ly, lz = t.local
        tone = 3.4 - 1.2 * _clamp((ly - 7.0) / 5.0) + (_hash(int(ly * 0.5), 0, 11) - 0.5) * 1.0
        if t.face != "NORTH":
            tone -= 0.8
        return _tone(HAIR, tone)

    def hair_back(self, t):
        x, h, z = _xhz(t)
        tone = 2.6 + (_hash(int(round(x)), int(h * 0.1), 13) - 0.5) * 1.9 + _form(t, 0.9)
        tone += 1.5 * _clamp(1.0 - abs(h - 43.0 - 0.6 * math.sin(x * 1.1)) / 1.2)
        tone -= 0.9 * _clamp((37.0 - h) / 5.0)
        if t.cube == "hair_back_tip" and h < 33.0 and (abs(x) > 1.0 or t.face in ("WEST", "EAST")):
            return None  # the end thins to a point
        return _tone(HAIR, tone - 1.0 * self.occ.overhang(t))

    # ---- head ornaments
    def crown(self, t):
        x, h, z = _xhz(t)
        if t.face in ("DOWN", "UP"):
            return _tone(SILVER, 3.2 - 1.2 * self.occ.contact(t))
        if h < 63.0:
            return _tone(GOLD, 2.6 + _form(t, 1.0))
        if t.face == "NORTH" and abs(x) < 0.6:
            return _tone(JADE, 3.8)
        return _tone(SILVER, 3.0 + _form(t, 1.4))

    def pin(self, t):
        x, h, z = _xhz(t)
        if t.cube == "pin_knob":
            return _tone(JADE, 3.2 + (1.6 if t.face in ("DOWN", "NORTH") else 0.0) + _form(t, 0.4))
        tone = 2.8 + (1.4 if t.face == "DOWN" else -0.6 if t.face == "UP" else 0.0)
        if x > 4.5:
            tone -= 0.8  # the plain point
        return _tone(JADE, tone)

    def ribbon(self, t):
        lx, ly, lz = t.local
        if t.cube == "ribbon_root_right":
            return _tone(ROBE, 5.2 + _form(t, 0.2))
        tone = 5.2 + _form(t, 0.5) - 0.4 * math.sin(ly * 0.9)
        if ly > 12.5:
            return _tone(GOLD, 2.6 if ly < 13.5 else 3.4)
        dye = _clamp((ly - 7.0) / 5.0)
        return _mix(_tone(ROBE, tone), _tone(DYE, tone - 1.6), dye * 0.85)

    # ---- trunk
    def torso(self, t):
        x, h, z = _xhz(t)
        f = t.face
        tone = 5.3 + _form(t, 0.5) + _weave(t, 0.35)
        if f == "DOWN":
            # around the neck: the white inner collar, then the robe's own collar
            if max(abs(x), abs(z) * 1.2) < 4.6:
                return _tone(INNER, 3.2 - 1.4 * self.occ.contact(t))
            return _tone(COLLAR, 2.4)
        if f != "NORTH":
            return _tone(ROBE, tone - 1.0)
        over = 3.5 - (COLLAR_TOP - h) * math.tan(math.radians(27.0))   # centre lines of the collar bands
        under = -over
        shadow = 1.2 * self.occ.contact(t) + 1.8 * self.occ.overhang(t)
        shadow += 1.0 * _clamp(1.0 - (_vest_opening(h) - abs(x)) / 1.4)  # under the vest's edge
        if under < x < over:
            return _tone(INNER, 3.8 - shadow - 0.5 * _clamp((47.0 - h) / 5.0))
        # the lapel that lies on top has diagonal pull folds toward the belt
        tone += 0.5 * math.sin(x * 0.9 + (COLLAR_TOP - h) * 0.55)
        return _tone(ROBE, tone - shadow)

    def collar(self, t):
        lx, ly, lz = t.local
        over = t.cube == "collar_over_band"
        piping = (lx < 0.0) if over else (lx > 0.0)
        shade = 1.0 * self.occ.contact(t) + 1.5 * self.occ.overhang(t)
        if t.face == "NORTH" and piping:
            return _tone(INNER, 3.8 - shade)
        tone = 3.0 + (0.4 if t.face == "NORTH" else -0.8) - shade
        if t.face == "NORTH" and int(ly + 2.0) % 3 == 0:
            tone += 1.0  # a stitched pattern along the band
        return _tone(COLLAR, tone)

    def vest_cloth(self, t, tone=3.4, roundness=1.0, **kw):
        x, h, z = _xhz(t)
        along = x if abs(t.n[2]) > 0.5 else z
        tone += self.light(t, roundness, cast=1.8, crevice=1.0, **kw)
        tone += 0.2 if int(math.floor(along)) % 2 == 0 else -0.2  # a fine vertical rib
        return tone + _weave(t, 0.35, 1.3, 4)

    def vest(self, t):
        x, h, z = _xhz(t)
        f = t.face
        ax = abs(x)
        if f == "NORTH" and ax < _vest_opening(h):
            return None
        if f == "DOWN":
            if ax < 4.5 and z < 3.5:
                return None  # the neck opening
            return _tone(VEST, 4.0 + _weave(t, 0.3, 1.3, 4) - 0.9 * _clamp(1.0 - max(ax - 4.5, z - 3.5) / 1.5))
        if f == "UP":
            return _tone(VEST, 0.6)
        tone = self.vest_cloth(t)
        tone += 0.9 * _clamp((h - 44.5) / 2.5)   # light catching the shoulders
        tone -= 0.6 * _clamp((37.0 - h) / 4.0)   # gathered and darker toward the belt
        return _tone(VEST, tone)

    def vest_cap(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "DOWN":
            return _tone(VEST, 3.6 + _weave(t, 0.3, 1.3, 4) + (0.8 if lx < -5.5 else 0.0))
        if f == "UP":
            return _tone(VEST, 0.6)
        row = int(ly)  # 0 at the shoulder .. 2 at the cap's lower edge
        along = lz if f in ("WEST", "EAST") else lx
        if row == 2:
            return _tone(GOLD, 2.6 + _form(t, 0.8))
        if row == 1:
            return _tone(BROCADE, 2.2 + (1.6 if int(math.floor(along + 8.0)) % 2 == 0 else 0.0) + _form(t, 0.6))
        return _tone(VEST, self.vest_cloth(t, 3.8, 0.7))

    def vest_edge(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f != "NORTH":
            return _tone(BROCADE, 1.4 + (1.0 if f == "DOWN" else 0.0))
        shade = 1.4 * self.occ.overhang(t) + 0.5 * _clamp((ly - 8.0) / 4.0)
        step = int(ly + 1.0) % 4
        if lx > 0.0:  # the edge toward the opening carries a gold line
            return _tone(GOLD, 2.8 + (0.8 if step == 1 else 0.0) - shade)
        return _tone(BROCADE, 2.8 + (1.8 if step == 1 else 0.5 if step in (0, 2) else 0.0) - shade)

    def belt(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "DOWN":
            return _tone(BELT_CLOTH, 3.4 - 1.6 * self.occ.contact(t))
        if f == "UP":
            return _tone(BELT_CLOTH, 0.4)
        row = int(h - BELT[0])  # 0 bottom .. 4 top
        if row in (0, 4):
            return _tone(GOLD, 2.2 + _form(t, 1.4) + (0.7 if row == 4 else 0.0))
        along = x if abs(t.n[2]) > 0.5 else z
        if row == 2 and int(math.floor(along)) % 3 == 0:
            return _tone(BROCADE, 2.8 + _form(t, 1.2))  # a row of studs woven into the wrap
        return _tone(BELT_CLOTH, 2.4 + _form(t, 1.3) - 1.0 * self.occ.contact(t) + (0.6 if row == 3 else 0.0))

    def buckle(self, t):
        if t.face != "NORTH":
            return _tone(GOLD, 1.6 + (1.2 if t.face == "DOWN" else 0.0))
        x, h, _ = _xhz(t)
        col, row = int(x + 2.0), int(h - 30.5)
        if col in (0, 3) or row in (0, 3):
            return _tone(GOLD, 3.4 if row == 3 or col == 0 else 2.4)
        return _tone(JADE, 4.6 if (col, row) == (1, 2) else 3.0)

    # ---- sleeves
    def sleeve_cloth(self, t, tone, dye=0.0, roundness=1.1, **kw):
        tone += self.light(t, roundness, **kw) + _weave(t, 0.4)
        if dye <= 0.0:
            return _tone(ROBE, tone)
        return _mix(_tone(ROBE, tone), _tone(DYE, tone - 1.6), _clamp(dye))

    def sleeve_upper(self, t):
        lx, ly, lz = t.local
        if t.face == "DOWN":
            return _tone(ROBE, 2.4)
        tone = 5.4 - 0.25 * _clamp((ly - 2.0) / 8.0)
        along = lx if t.face in ("NORTH", "SOUTH") else lz
        # creases gathering toward the elbow
        crease = math.sin(ly * 1.5 + 0.5 * math.sin((lx + lz) * 0.8))
        tone += (0.7 * crease - (0.7 if crease < -0.8 else 0.0)) * _clamp((ly - 3.0) / 4.0)
        band = int(math.floor(ly)) - 4
        if 0 <= band <= 3:  # 袖襕: a woven band round the upper sleeve
            kind = _fret(band, along + 12.0)
            if kind:
                shade = _form(t, 1.0) - 1.2 * self.occ.overhang(t)
                return _tone(GOLD, 2.6 + shade) if kind == "line" else _tone(DYE, 2.8 + shade)
        return self.sleeve_cloth(t, tone, exclude=("sleeve_lower_right",))

    def sleeve_lower(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "UP":
            # the open cuff: a pale lining rim around the dark inside of the sleeve
            edge = min(lx + 3.0, 3.0 - lx, lz + 3.0, 5.0 - lz)
            if edge < 1.0:
                return _tone(INNER, 2.6)
            return _tone(VEST, 0.4 if edge > 2.0 else 1.2)
        if f == "DOWN":
            return _tone(ROBE, 2.2)
        tone = 5.3
        along = lx if f in ("NORTH", "SOUTH") else lz
        fold = math.sin(along * 1.25 + 0.35 * ly)
        tone += (0.7 * fold - (0.7 if fold < -0.8 else 0.0)) * _clamp((ly - 1.0) / 5.0)  # hanging folds
        tone += 0.6 * math.sin(ly * 1.6 + along * 0.4) * _clamp(1.0 - ly / 3.5)          # bunching at the elbow
        if ly > 9.5:
            return _tone(GOLD, 2.8 + _form(t, 1.0))                                   # cuff edge
        if ly > 8.5:
            return _tone(INNER, 3.4 + _form(t, 1.0))
        dye = _clamp((ly - 3.0 + 1.2 * (value_noise(t.p, 1.4, 6) - 0.5)) / 5.0)
        return self.sleeve_cloth(t, tone, dye * 0.9, exclude=("sleeve_upper_right",))

    def sleeve_drape(self, t):
        lx, ly, lz = t.local
        f = t.face
        if f == "UP":
            return _tone(DYE, 0.8)
        if ly > 16.5:
            return _tone(GOLD, 2.6 + _form(t, 1.0))
        tone = 4.6 + 0.6 * math.sin((lx if f in ("NORTH", "SOUTH") else lz) * 1.3 + 0.4 * ly)
        return self.sleeve_cloth(t, tone, 0.9, exclude=("sleeve_lower_right", "hand_right"))

    # ---- skirt, boots
    def skirt(self, t):
        x, h, z = _xhz(t)
        f = t.face
        upper = t.cube == "robe_upper_right"
        if f == "UP":
            return _tone(VEST, 0.3)
        if f == "DOWN":
            return _tone(ROBE, 2.0)  # the lower tier's ledge faces the light, so it is painted darker
        inner = f == "EAST"  # the face between the legs: the same cloth in shadow, seen mid-stride
        # pleats run round the figure and deepen toward the hem
        ang = math.atan2(x, -z if abs(z) > 0.01 else -0.01)
        pleat = math.sin(z * 1.1 + 0.5 * math.sin(h * 0.33)) if inner \
            else math.sin(ang * 9.0 + 0.5 * math.sin(h * 0.33 + x * 0.2))
        depth = _clamp((HIP - h) / 9.0)
        tone = 5.3 + _weave(t, 0.4) + (-1.3 - 0.9 * _clamp((h - 20.0) / 8.0) if inner else self.light(t, 0.9, cast=2.4))
        tone += depth * (0.8 * pleat - (1.2 if pleat < -0.72 else 0.0) + (0.5 if pleat > 0.82 else 0.0))
        band = int(math.floor(h - KNEE_BAND))
        if not upper and 0 <= band <= 3:  # 膝襕: a woven band round the skirt, pale fret on a dyed ground
            kind = _fret(band, (z if abs(t.n[0]) > 0.5 else x) + 60.0)
            shade = (-1.4 if inner else _form(t, 0.9)) + 0.5 * pleat
            if kind == "line":
                return _tone(GOLD, 2.4 + shade)
            return _tone(ROBE, 5.0 + shade) if kind else _tone(DYE, 1.6 + shade)
        if f in ("NORTH", "SOUTH") and x > -1.0:
            tone -= 1.0 if f == "NORTH" else 0.6  # the slit down the middle
        if upper and h < TIER + 1.0:
            tone -= 0.5
        row = int(h - HEM)
        if not upper:
            if row == 0:
                return _tone(GOLD, 1.4 if inner else 2.6 + _form(t, 0.9) + 0.4 * pleat)
            if row == 1:
                return _tone(DYE, 0.2 if inner else 0.8 + 0.4 * pleat + _form(t, 0.6))
        dye = _clamp((HEM + 15.0 - h + 2.0 * (value_noise(t.p, 1.6, 8) - 0.5) + 1.2 * pleat) / 8.0)
        return _mix(_tone(ROBE, tone), _tone(DYE, tone - 1.8), dye * 0.92)

    def boot(self, t):
        x, h, z = _xhz(t)
        f = t.face
        if f == "UP":
            return _tone(BOOT, 0.0)
        if f == "DOWN":
            return _tone(BOOT, 2.8 if z < -5.0 else 1.2)
        if h < 1.0:
            return _tone(SOLE, 1.6 + _form(t, 0.6))
        return _tone(BOOT, 1.8 + _form(t, 0.8) + (0.9 if z < -5.5 and h > 3.0 else 0.0) - 1.0 * self.occ.overhang(t))

    # ---- hung from the belt
    def panel(self, t):
        lx, ly, lz = t.local
        f = t.face
        back = t.cube == "vest_back_panel"
        width, length = (16.0, 19.0) if back else (6.0, 17.0)
        if f in ("WEST", "EAST", "UP", "DOWN"):
            return _tone(VEST, 1.6)
        if f != ("SOUTH" if back else "NORTH"):
            return _tone(VEST, 1.0)  # the lining, seen when the panel swings
        u = lx + width / 2.0              # across, from the figure's right
        v = ly + 0.5                      # down from the belt
        from_hem = length - v
        tone = 3.5 + _weave(t, 0.35, 1.3, 4) - 2.0 * self.occ.overhang(t) - 0.5 * _clamp(1.0 - v / 3.0)
        tone += 0.4 * math.sin(u * (0.8 if back else 1.4) + 0.25 * v) * _clamp(v / 6.0)  # soft hanging folds
        if from_hem < 1.0:
            return _tone(GOLD, 2.8)
        if from_hem < 3.0:
            return _tone(BROCADE, 2.4 + (1.6 if int(u + from_hem) % 3 == 0 else 0.0))  # woven band at the hem
        if from_hem < 4.0:
            return _tone(GOLD, 2.2)
        if back:
            emblem = _emblem(u - 8.0, 8.5 - v)
            if emblem == "gold":
                return _tone(GOLD, 2.8 + (0.6 if 8.5 - v > 0.0 else 0.0) - 1.2 * self.occ.overhang(t))
            if emblem == "dark":
                return _tone(VEST, 1.6)
            tone -= 0.5 * _clamp(1.0 - min(u, width - u))
        elif 4.0 <= u < 5.0:
            return _tone(GOLD, 2.4 - 1.2 * self.occ.overhang(t))  # a line inside the edge toward the opening
        return _tone(VEST, tone)

    def sash(self, t):
        lx, ly, lz = t.local
        length = 14.0 if t.cube == "sash_long" else 11.0
        v = ly + 0.5
        if t.face != "NORTH":
            return _tone(COLLAR, 0.8)
        if length - v < 1.0:
            return _tone(GOLD, 3.4 if int(math.floor(lx * 2.0)) % 2 == 0 else 2.0)  # fringe
        if length - v < 2.0:
            return _tone(GOLD, 2.6)
        left_half = (lx - math.floor(lx / 2.0 + 0.25) * 2.0) < 0.5
        tone = 2.8 + 0.5 * math.sin(v * 0.9) - 1.8 * self.occ.overhang(t) + (0.7 if left_half else 0.0)
        return _tone(COLLAR, tone)

    def cord(self, t):
        return _tone(CORD, 1.4 + (0.6 if int(t.local[1]) % 2 == 0 else 0.0))

    def jade_ring(self, t):
        lx, ly, lz = t.local
        col, row = int(lx + 1.5), int(ly - 5.0)
        if t.face in ("NORTH", "SOUTH"):
            if (col, row) == (1, 1):
                return None  # the ring's hole
            return _tone(JADE, 4.8 if (col, row) == (0, 0) else 3.6 if row == 0 or col == 0 else 2.4)
        return _tone(JADE, 2.0)

    def tassel(self, t):
        lx, ly, lz = t.local
        v = ly - 8.0
        if v < 1.0:
            return _tone(GOLD, 3.0)
        return _tone(TASSEL, 2.4 + (0.7 if lx < 0.0 else -0.3) - 0.6 * _clamp((v - 3.0) / 3.0))


# ------------------------------------------------------------------------------------ shapes
def _hairline(a):
    """Height of the front hairline `a` columns from the centre: a straight hairline one row down over
    an open forehead, the temple corners one row lower, and the sideburn down to the cranium's
    bottom at the edge (also the shell's edge column, a = 7)."""
    if a >= 6:
        return 52.0
    if a == 5:
        return 58.0
    return 59.0


def _hair_on_side(z, h):
    """Hair on the side of the head: sideburn, over the ear, and everything behind it down to the nape."""
    if z < -5.5:
        return h >= 52.0
    if z < -0.5:
        return h >= 56.0
    if z < 1.5:
        return h >= 52.0
    return True


def _vest_opening(h):
    """Half width of the vest's front opening at height h (the centre line of its border bands)."""
    return 5.0 - 1.5 * _clamp((COLLAR_TOP - h) / 12.4)


def _emblem(dx, dy):
    """The back panel's taiji at (dx across, dy up) from its centre: "gold", "dark", or None outside
    its ring. Ten texels across, so it sits on the panel's even grid."""
    r = math.hypot(dx, dy)
    if r >= 5.0:
        return None
    if r >= 4.0:
        return "gold"
    upper, lower = math.hypot(dx, dy - 2.0), math.hypot(dx, dy + 2.0)
    if upper < 0.9:
        return "dark"
    if lower < 0.9:
        return "gold"
    return "gold" if (dx < 0.0 or upper < 2.0) and lower >= 2.0 else "dark"


def painter(model):
    return _Paint(model)


# ------------------------------------------------------------------------------------ clips
WALK_LENGTH = 1.0     # seconds per stride cycle in the file; animateWalk rescales it to the distance walked
WALK_SWING = 26.0     # degrees each leg swings either way
IDLE_LENGTH = 4.0


SOLE_Z = (-7.0, 3.0)  # the boot sole's toe and heel, from the leg's axis
SWING_LIFT = 2.0      # how far the swinging foot clears the ground


def _walk_pose(phase):
    right = _leg_angle(phase, WALK_SWING)             # negative x rotation brings a limb forward
    left = -right
    right_plants = math.cos(phase) >= 0.0             # the leg moving back carries the weight
    stance, swing = (right, left) if right_plants else (left, right)
    drop = _sole_low(stance, HIP, SOLE_Z)             # the hips ride on the planted foot
    lift = SWING_LIFT * abs(math.cos(phase)) + max(0.0, drop - _sole_low(swing, HIP, SOLE_Z))
    forward_right, forward_left = max(0.0, -right), max(0.0, -left)
    back = max(0.0, right, left)
    s = right / WALK_SWING
    lag = math.sin(2.0 * phase - 0.9)
    pose = {
        "root": {"position": (0.0, -drop, 0.0)},
        "leg_right": {"rotation": (right, 0.0, 0.0), "position": (0.0, 0.0 if right_plants else lift, 0.0)},
        "leg_left": {"rotation": (left, 0.0, 0.0), "position": (0.0, lift if right_plants else 0.0, 0.0)},
        "arm_right": _rx(-0.5 * right),
        "arm_left": _rx(-0.5 * left),
        "forearm_right": _rx(-9.0 * _clamp(s)),
        "forearm_left": _rx(-9.0 * _clamp(-s)),
        "body": {"rotation": (2.0, 3.0 * s, 0.0)},
        "head": {"rotation": (-1.5, -2.5 * s, 0.0)},
        # the vest panels and the sash ride the leg under them so the skirt never comes through
        "vest_front_right": _rx(-0.95 * forward_right - 1.0),
        "vest_front_left": _rx(-0.95 * forward_left - 1.0),
        "vest_back": _rx(0.92 * back + 1.0),
        "sash": {"rotation": (-0.75 * max(forward_right, forward_left) - 2.0, 0.0, 2.0 * lag)},
        "pendant": {"rotation": (-0.9 * forward_left - 2.0, 0.0, 3.0 * math.sin(phase - 0.8))},
        # hair and ribbons trail behind and settle twice a stride
        "hair_back": _rx(3.0 + 2.0 * lag),
        "ribbon_right": {"rotation": (9.0 + 5.0 * lag, 0.0, 2.5 * math.sin(phase - 0.6))},
        "ribbon_left": {"rotation": (9.0 + 5.0 * math.sin(2.0 * phase - 1.5), 0.0, 2.5 * math.sin(phase - 1.0))},
        "strand_right": {"rotation": (0.0, 0.0, 2.0 * math.sin(phase - 0.5))},
        "strand_left": {"rotation": (0.0, 0.0, 2.0 * math.sin(phase - 0.9))},
    }
    return pose


def _idle_pose(phase):
    breath = math.sin(phase)
    slow = math.sin(phase - 0.9)
    return {
        "body": {"position": (0.0, 0.12 * breath, 0.0)},
        "head": _rx(0.7 * slow),
        "arm_right": {"rotation": (0.0, 0.0, 0.8 * breath)},
        "arm_left": {"rotation": (0.0, 0.0, -0.8 * breath)},
        "forearm_right": _rx(-1.0 * slow),
        "forearm_left": _rx(-1.0 * slow),
        "hair_back": _rx(0.6 * slow),
        "ribbon_right": {"rotation": (2.0 * slow, 0.0, 1.6 * math.sin(phase - 1.7))},
        "ribbon_left": {"rotation": (2.0 * math.sin(phase - 1.4), 0.0, 1.6 * math.sin(phase - 2.2))},
        "strand_right": {"rotation": (0.0, 0.0, 0.9 * math.sin(phase - 1.2))},
        "strand_left": {"rotation": (0.0, 0.0, 0.9 * math.sin(phase - 1.9))},
        "sash": {"rotation": (0.8 * slow, 0.0, 1.2 * math.sin(phase - 2.0))},
        "pendant": {"rotation": (0.6 * slow, 0.0, 2.2 * math.sin(phase - 2.4))},
        "vest_front_right": _rx(0.5 * slow),
        "vest_front_left": _rx(0.5 * math.sin(phase - 1.3)),
        "vest_back": _rx(-0.5 * slow),
    }


def clips(model):
    return [_loop("idle", IDLE_LENGTH, _idle_pose, 8), _loop("walk", WALK_LENGTH, _walk_pose, 32)]
