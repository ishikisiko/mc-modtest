"""Shared parts for humanoid NPC definitions: the pieces that do not depend on one set of clothes.

A definition in `defs/` (one look of one NPC) imports what it needs from here and keeps its own
geometry, palette, garments, face, hairstyle and pose tables. Standard library only.

Design space (tools/beastgen/builder.py): ground y = 0, up -Y, front -Z, the figure's left +X; heights
are written as h = units above the ground. The helpers keep their leading underscore so a painter can
still use `tone`, `form` and the like as local variable names.

- Model: `_box` (a design-space box), `_pair` (a right bone and its mirrored left twin), `_rgb`.
- Paint maths: `_clamp`, `_mix`, `_tone` (a ramp sampled in half steps), `_hash`, `_xhz` (a texel's
  rest-pose x, h, z), `_form` (a box lit as if round), `_weave` (cloth noise), `_fret` (a woven band).
- Clips: `_rx`, `_loop`, and the gait `_leg_angle(phase, swing)` / `_sole_low(angle, hip, sole_z)`.
- `HumanoidPaint`: the painter base with the skin materials (light, skin, neck, hand, nose, cranium,
  jaw); a look subclasses it, sets its ramps and head measurements, and adds the face and the rest.
"""
from __future__ import annotations

import math

from ..beastgen import anim
from ..beastgen.paint import value_noise
from .shade import Occluders

__all__ = (
    "_box", "_pair", "_rgb",
    "_clamp", "_mix", "_tone", "_hash", "_xhz", "_form", "_weave", "_fret",
    "_rx", "_loop", "_leg_angle", "_sole_low",
    "HumanoidPaint",
)


def _rgb(hex_colour):
    s = hex_colour.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


# ------------------------------------------------------------------------------------ model
def _box(b, bone, name, x0, h0, z0, w, h, d, **kw):
    """A design-space box from its right (-X), bottom, front corner."""
    b.box(bone, name, (x0, -(h0 + h), z0), (w, h, d), **kw)


def _pair(b, bone_fmt, parent, at, rot=(0.0, 0.0, 0.0), boxes=(), local=False):
    """A right (-X) bone and its mirrored left twin. `at` and `rot` are the right side's; boxes are
    (name_fmt, origin, size): design-space (x0, h0, z0) corners, or bone-local origins with local=True.
    The right side owns the UV islands and the left reuses them mirrored."""
    for side, s in (("right", -1.0), ("left", 1.0)):
        own = side == "right"
        bone = bone_fmt.format(side)
        par = parent.format(side)
        b.bone(bone, par, at=(at[0] if own else -at[0], at[1], at[2]),
               rot=(rot[0], rot[1] if own else -rot[1], rot[2] if own else -rot[2]))
        for name_fmt, origin, size in boxes:
            kw = {} if own else {"mirror": True, "uv_from": name_fmt.format("right")}
            x0 = origin[0] if own else -(origin[0] + size[0])
            if local:
                b.box_local(bone, name_fmt.format(side), (x0, origin[1], origin[2]), size, **kw)
            else:
                _box(b, bone, name_fmt.format(side), x0, origin[1], origin[2], *size, **kw)


# ------------------------------------------------------------------------------------ paint helpers
def _clamp(x, lo=0.0, hi=1.0):
    return lo if x < lo else hi if x > hi else x


def _mix(a, b, f):
    return tuple(int(round(a[k] + (b[k] - a[k]) * f)) for k in range(3))


def _tone(ramp_, tone):
    """A ramp sampled at a fractional index, in half steps so shading stays banded, not airbrushed."""
    tone = _clamp(round(tone * 2.0) / 2.0, 0.0, len(ramp_) - 1.0)
    i = int(math.floor(tone))
    if i >= len(ramp_) - 1:
        return ramp_[-1]
    return _mix(ramp_[i], ramp_[i + 1], tone - i)


def _hash(a, b, seed=0):
    h = (int(a) * 374761393 + int(b) * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def _xhz(t):
    """Rest-pose design coordinates of a texel: x (left +), height above the ground, z (front -)."""
    return t.p[0], 24.0 - t.p[1], t.p[2]


def _form(t, roundness=1.0):
    """Light from above on a box shaded as if it were round: tops bright, undersides dark, vertical
    faces falling off toward their side edges and a little toward the back."""
    ny = t.n[1]
    if ny < -0.5:
        return 0.6
    if ny > 0.5:
        return -2.0
    edge = abs(2.0 * t.fu - 1.0)
    s = -roundness * edge * edge
    if t.n[2] > 0.5:
        s -= 0.3
    elif abs(t.n[0]) > 0.5:
        s -= 0.2
    return s


def _weave(t, amount=0.5, cell=1.1, seed=3):
    return (value_noise(t.p, cell, seed) - 0.5) * 2.0 * amount


def _fret(row, across):
    """A woven band four rows deep (row 0 at one edge): a line, two offset rows that step into a
    fret, a line. Returns "line", "fret" or None (the ground between the fret's steps)."""
    if row in (0, 3):
        return "line"
    a = int(math.floor(across)) + (1 if row == 2 else 0)
    return "fret" if a % 3 != 0 else None


# ------------------------------------------------------------------------------------ painter base
class HumanoidPaint:
    """The painter base: one method per material, dispatched by cube name through `by_cube`.

    A look subclasses it and sets, as class attributes:
      SKIN, HAIR     the skin and hair ramps (tuples of RGB from `beastgen.paint.ramp`)
      NOSE_TOP       height of the nose cube's top face (rows count down from it: 0 bridge, 1 tip)
      FINGER_ROW     bone-local y past which the hand's sides break into darker finger gaps
      HAND_EXCLUDE   cube names `Occluders` ignores when shading the hand (a drape hanging over it)
      JAW_BACK       z behind which the cranium's underside sits under the back hair, not the sideburn
    and defines `face(t)` (the head's front, also called for the jaw's front), `head_side(t, z, h)`
    (skin where the hair is cut away on the cranium's side) and `hair_on_side(z, h)` (whether the hair
    shell covers the cranium's side there). The tone numbers below are the cultivator's; override a
    method when a look's skin needs other values.
    """

    SKIN = None
    HAIR = None
    NOSE_TOP = None
    FINGER_ROW = None
    HAND_EXCLUDE = ()
    JAW_BACK = None

    def __init__(self, model, hollow, by_cube):
        self.occ = Occluders(model, hollow)
        self.by_cube = by_cube

    def __call__(self, t):
        return self.by_cube[t.cube](t)

    # ---- what a look supplies
    def face(self, t):
        raise NotImplementedError

    def head_side(self, t, z, h):
        raise NotImplementedError

    def hair_on_side(self, z, h):
        raise NotImplementedError

    # ---- shared light
    def light(self, t, roundness=1.0, cast=2.0, crevice=1.1, exclude=()):
        return (_form(t, roundness) - cast * self.occ.overhang(t, exclude)
                - crevice * self.occ.contact(t, exclude))

    # ---- skin
    def skin(self, t, tone=4.4, roundness=0.8, **kw):
        return _tone(self.SKIN, tone + self.light(t, roundness, cast=1.5, crevice=0.8, **kw) + _weave(t, 0.12, 1.6, 21))

    def neck(self, t):
        return self.skin(t, 3.4)

    def hand(self, t):
        lx, ly, lz = t.local
        tone = 4.3
        if t.face in ("NORTH", "SOUTH", "WEST", "EAST") and ly > self.FINGER_ROW:
            # fingers: the last row breaks into darker gaps
            along = lx if t.face in ("NORTH", "SOUTH") else lz + 0.5
            if int(math.floor(along)) % 2 == 0:
                tone -= 1.0
        if t.face == "UP":
            tone -= 0.6
        return self.skin(t, tone, exclude=self.HAND_EXCLUDE)

    def nose(self, t):
        row = int(self.NOSE_TOP - (24.0 - t.p[1]))  # 0 bridge, 1 tip
        if t.face == "NORTH":
            return _tone(self.SKIN, (4.9, 4.7)[row])
        if t.face == "UP":
            return _tone(self.SKIN, 2.0)  # under the tip
        if t.face == "DOWN":
            return _tone(self.SKIN, 1.0)  # faces up, so it draws at full brightness: as dark as the lit front
        return _tone(self.SKIN, 3.4)

    def skull(self, t):
        """The cranium: the face in front, temple and ear on the sides, a ring of underside round the jaw."""
        x, h, z = _xhz(t)
        f = t.face
        if f == "NORTH":
            return self.face(t)
        if f in ("WEST", "EAST") and not self.hair_on_side(z, h):
            return self.head_side(t, z, h)
        if f == "UP":
            # the underside beside and behind the jaw sits under the sideburn and the hair
            return _tone(self.HAIR, 1.4) if z < self.JAW_BACK else _tone(self.HAIR, 1.2)
        return _tone(self.HAIR, 1.6)      # scalp under the hair shell

    def jaw(self, t):
        """The jaw's steps and the chin: the face in front, sides in the cranium's shadow, under-chin below."""
        f = t.face
        if f == "NORTH":
            return self.face(t)
        if f in ("WEST", "EAST"):
            return self.skin(t, 4.0)
        if f == "UP":
            return _tone(self.SKIN, 3.6)  # under the chin and the jaw steps
        return _tone(self.SKIN, 3.4)      # hidden: the top under the cranium, the back inside the hair


# ------------------------------------------------------------------------------------ clips
def _rx(deg):
    return {"rotation": (deg, 0.0, 0.0)}


def _leg_angle(phase, swing):
    """Leg x rotation (positive = back) through a stride swinging `swing` degrees either way: the foot
    travels at an even speed while it is planted, so a body moving at a steady speed does not slide
    over it."""
    tri = 2.0 / math.pi * math.asin(max(-1.0, min(1.0, math.sin(phase))))
    return math.degrees(math.asin(math.sin(math.radians(swing)) * tri))


def _sole_low(angle, hip, sole_z):
    """Height of the sole's lowest corner above the ground for a leg turned by `angle` about a hip
    `hip` units up, hip unmoved; `sole_z` holds the sole's toe and heel z from the leg's axis."""
    a = math.radians(angle)
    return min(hip * (1.0 - math.cos(a)) + z * math.sin(a) for z in sole_z)


def _loop(name, length, pose_at, steps):
    """A looping clip of `steps` evenly spaced keys over one cycle of `pose_at(phase)`, phase 0..2π."""
    keys = [anim.Key(length * i / steps, pose_at(2.0 * math.pi * i / steps)) for i in range(steps)]
    keys.append(anim.Key(length, pose_at(0.0)))
    return anim.Clip(name, length, True, keys)
