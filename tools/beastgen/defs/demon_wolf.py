"""Demon Wolf (妖狼): a lean charcoal predator with a teal mane ridge and pale-cyan eyes.

Design space (see builder.py): ground y = 0, up -Y, front -Z, the wolf's left +X; one unit is one
texel (1/16 block). Withers about 21 up, nose to rump about 36, feet on the ground, and the trunk
kept inside the 1.3 x 1.45 block hitbox (head, mane tips and tail overhang).
"""
from __future__ import annotations

import math

from .. import anim, quadruped as quad
from ..builder import Builder
from ..paint import pick, ramp, value_noise

ID = "myvillage:demon_wolf"
NAME = "demon_wolf"
HITBOX = (1.3, 1.45)  # width, height in blocks (server registration)

# ------------------------------------------------------------------------------------ palette
COAT = ramp("#262B33", "#363C45", "#47505A", "#525B66", "#616B77", "#6E7884")
ASH = ramp("#636B73", "#757D85", "#878F96", "#9AA1A7", "#AEB4B9")
TEAL = ramp("#103A41", "#1A5F68", "#1F6F78", "#2E9097", "#45B7B4", "#58D6D0")
EYE = ("#286E69", "#1C504E", "#18464A")  # base texels under the additive glow
GLOW = ("#82DCD3", "#5AC6BE", "#3E9E9A")
NOSE = ramp("#0C0D10", "#16181C", "#23262B")
CLAW = "#121418"
TOOTH = ramp("#A9A491", "#CFCAB7", "#E8E4D4")
MOUTH = ramp("#2E0A0E", "#4A1116", "#6B1C22", "#8A2C33")

PLATE_BURIED = 2  # plate units below the pivot
PLATE_BONES = ("mane_0", "mane_1", "mane_2", "mane_3", "mane_4", "mane_5", "mane_6", "mane_7")
LEGS = (
    # name, upper, lower, paw, knee
    ("front_right", "front_right_upper", "front_right_lower", "front_right_paw", "back"),
    ("front_left", "front_left_upper", "front_left_lower", "front_left_paw", "back"),
    ("hind_right", "hind_right_upper", "hind_right_lower", "hind_right_paw", "forward"),
    ("hind_left", "hind_left_upper", "hind_left_lower", "hind_left_paw", "forward"),
)


# ------------------------------------------------------------------------------------ model
def _leg_pair(b, side, x, front):
    """One leg; side 'right' (-X) owns the UV islands, 'left' mirrors them."""
    s = -1.0 if side == "right" else 1.0
    own = side == "right"
    kw = {} if own else {"mirror": True}

    def uvf(n):
        return None if own else n.replace("_left_", "_right_")

    if front:
        up = b.bone(f"front_{side}_upper", "chest", at=(s * x, -17.0, -8.5), rot=(10.0, 0.0, 0.0))
        b.box_local(up, f"front_{side}_upper_cube", (-1.5, -3.0, -2.5), (3, 6, 5),
                    uv_from=uvf(f"front_{side}_upper_cube"), **kw)
        b.box_local(up, f"front_{side}_arm_cube", (-1.5, 2.0, -1.5), (3, 7, 3),
                    uv_from=uvf(f"front_{side}_arm_cube"), **kw)
        lo = b.bone(f"front_{side}_lower", up, at_local=(0.0, 8.0, 0.0), rot=(-18.0, 0.0, 0.0))
        b.box_local(lo, f"front_{side}_lower_cube", (-1.0, -1.0, -1.5), (2, 9, 3),
                    uv_from=uvf(f"front_{side}_lower_cube"), **kw)
        paw = b.bone(f"front_{side}_paw", lo, at_local=(0.0, 7.0, 0.0), rot=(8.0, 0.0, 0.0))
    else:
        up = b.bone(f"hind_{side}_upper", "hip", at=(s * x, -16.5, 7.0), rot=(-22.0, 0.0, 0.0))
        b.box_local(up, f"hind_{side}_upper_cube", (-2.0, -2.0, -3.0), (4, 6, 6),
                    uv_from=uvf(f"hind_{side}_upper_cube"), **kw)
        b.box_local(up, f"hind_{side}_thigh_cube", (-1.5, 3.0, -2.0), (3, 6, 4),
                    uv_from=uvf(f"hind_{side}_thigh_cube"), **kw)
        lo = b.bone(f"hind_{side}_lower", up, at_local=(0.0, 7.5, 0.0), rot=(62.0, 0.0, 0.0))
        b.box_local(lo, f"hind_{side}_lower_cube", (-1.0, -1.0, -1.5), (2, 8, 3),
                    uv_from=uvf(f"hind_{side}_lower_cube"), **kw)
        paw = b.bone(f"hind_{side}_paw", lo, at_local=(0.0, 6.5, 0.0), rot=(-40.0, 0.0, 0.0))
    # the paw bone is level at rest: put its pad on the ground
    py = b.design_point(paw, (0.0, 0.0, 0.0))[1]
    ground = -py  # paw-local y of the ground plane
    if not front:
        b.box_local(paw, f"hind_{side}_meta_cube", (-1.0, -1.0, -1.0), (2, round(ground - 0.5), 2),
                    uv_from=uvf(f"hind_{side}_meta_cube"), **kw)
    b.box_local(paw, f"{'front' if front else 'hind'}_{side}_paw_cube", (-1.5, ground - 2.0, -2.5), (3, 2, 4),
                uv_from=uvf(f"{'front' if front else 'hind'}_{side}_paw_cube"), **kw)


def _plate(b, name, parent, at_local, h, d, rot=-38.0):
    """A plate: 1 thick, h above its pivot (2 more buried so a long plate's lower corner stays under the
    surface when it leans), d long, leaning back by rot."""
    b.bone(name, parent, at_local=at_local, rot=(rot, 0.0, 0.0))
    b.box_local(name, f"{name}_cube", (-0.5, -float(h), -d / 2.0), (1, h + PLATE_BURIED, d))


def build_model():
    b = Builder(ID, look={"bone": "head", "max_yaw": 50.0, "max_pitch": 30.0}, shadow_radius=0.9)
    b.bone("body", "root", at=(0.0, -16.5, -1.0))
    b.bone("chest", "body", at=(0.0, -16.5, -1.0))
    b.bone("ribcage", "chest", at=(0.0, -21.0, -6.0))
    b.box("ribcage", "barrel", (-4.5, -21.0, -11.0), (9, 9, 11))
    b.box("ribcage", "brisket", (-3.5, -13.0, -12.0), (7, 3, 7))
    # hips sit only a little below the withers; the waist is tucked well up from the brisket
    b.bone("hip", "body", at=(0.0, -16.5, -1.0))
    b.box("hip", "waist", (-3.0, -20.5, -1.0), (6, 5, 6))
    b.box("hip", "haunch", (-4.0, -20.0, 3.0), (8, 7, 7))
    b.box("hip", "rump", (-3.5, -19.5, 9.0), (7, 5, 3))

    b.bone("neck", "chest", at=(0.0, -16.5, -9.0), rot=(-10.0, 0.0, 0.0))
    b.box_local("neck", "neck_cube", (-3.0, -3.5, -6.0), (6, 7, 8))
    b.box_local("neck", "neck_ruff", (-2.5, -4.5, -5.0), (5, 2, 6))
    # head: a tall skull with a stop down to a narrow, low muzzle; cheek fur makes the face widest
    b.bone("head", "neck", at_local=(0.0, -0.5, -5.5), rot=(10.0, 0.0, 0.0))
    b.box_local("head", "skull", (-3.5, -4.0, -5.0), (7, 6, 5))
    b.box_local("head", "cheek", (-4.5, -1.0, -4.0), (9, 4, 4))
    b.box_local("head", "brow", (-3.5, -3.0, -5.5), (7, 1, 1))
    # muzzle + jaw: 4 of the skull's 6 units tall and 4 of its 7 wide, below a 3-unit stop
    b.box_local("head", "muzzle", (-2.0, -1.0, -10.0), (4, 2, 5))
    b.box_local("head", "nose", (-1.0, -1.25, -10.5), (2, 1, 1))
    b.box_local("head", "fang_right", (-2.25, 0.75, -9.5), (1, 1, 1), inflate=-0.25)  # a half-texel canine
    b.box_local("head", "fang_left", (1.25, 0.75, -9.5), (1, 1, 1), inflate=-0.25, mirror=True, uv_from="fang_right")
    b.bone("jaw", "head", at_local=(0.0, 1.0, -3.5))
    b.box_local("jaw", "jaw_cube", (-1.5, 0.0, -5.5), (3, 2, 5))
    # ears: squat triangles stepping in width and depth (3 to 1 thick), broad face forward, on the rear
    # corners of the skull, so they read as triangles from the front and from the side
    for side, s in (("right", -1.0), ("left", 1.0)):
        b.bone(f"ear_{side}", "head", at_local=(2.5 * s, -3.5, -1.5), rot=(-15.0, 0.0, 12.0 * s))
        kw = {} if side == "right" else {"mirror": True}
        for part, origin, size in (("cube", (-2.0, -2.0, -1.5), (4, 2, 3)), ("mid", (-1.5, -3.0, -0.5), (3, 1, 2)),
                                   ("tip", (-1.0, -4.0, 0.5), (2, 1, 1))):
            b.box_local(f"ear_{side}", f"ear_{side}_{part}", origin, size,
                        uv_from=None if side == "right" else f"ear_right_{part}", **kw)

    # mane ridge: a teal sawtooth of long, one-unit-thin plates rising out of the neck ruff, tallest over
    # the withers and fading along the back; heights and lean vary a little from plate to plate
    _plate(b, "mane_0", "head", (0.0, -3.5, -0.5), 1, 2, -44.0)
    _plate(b, "mane_1", "neck", (0.0, -4.0, -4.0), 3, 3, -38.0)
    _plate(b, "mane_2", "neck", (0.0, -4.0, -0.5), 3, 3, -32.0)
    _plate(b, "mane_3", "chest", b.local_point("chest", (0.0, -20.5, -8.5)), 5, 4, -36.0)
    _plate(b, "mane_4", "chest", b.local_point("chest", (0.0, -20.5, -5.0)), 4, 3, -40.0)
    _plate(b, "mane_5", "chest", b.local_point("chest", (0.0, -20.5, -2.0)), 4, 3, -34.0)
    _plate(b, "mane_6", "hip", b.local_point("hip", (0.0, -20.0, 1.5)), 2, 3, -42.0)
    _plate(b, "mane_7", "hip", b.local_point("hip", (0.0, -19.5, 5.0)), 1, 3, -46.0)

    for side in ("right", "left"):
        _leg_pair(b, side, 3.5, front=True)
    for side in ("right", "left"):
        _leg_pair(b, side, 3.5, front=False)

    # tail carried back clear of the hind legs: slim root, bushy middle, tapering to a teal tip
    b.bone("tail_0", "hip", at=(0.0, -18.5, 11.0), rot=(-18.0, 0.0, 0.0))
    b.box_local("tail_0", "tail_0_cube", (-1.5, -1.5, -1.0), (3, 3, 5))
    b.bone("tail_1", "tail_0", at_local=(0.0, 0.0, 4.0), rot=(-8.0, 0.0, 0.0))
    b.box_local("tail_1", "tail_1_cube", (-2.5, -2.5, -0.5), (5, 5, 6))
    b.bone("tail_2", "tail_1", at_local=(0.0, 0.0, 5.5), rot=(-8.0, 0.0, 0.0))
    b.box_local("tail_2", "tail_2_cube", (-2.0, -2.0, -0.5), (4, 4, 4))
    b.box_local("tail_2", "tail_tip", (-1.0, -1.0, 3.5), (2, 2, 2))
    return b.model


def legs(model):
    out = []
    for name, up, lo, paw, knee in LEGS:
        cube = [c for c in model.bone(paw).cubes if c.name.endswith("_paw_cube")][0]
        sole = (0.0, cube.origin[1] + cube.size[1], cube.origin[2] + cube.size[2] / 2.0)
        out.append(quad.Leg(name, up, lo, paw, sole, knee))
    return out


# ------------------------------------------------------------------------------------ painter
def _clamp(x, lo=0.0, hi=1.0):
    return lo if x < lo else hi if x > hi else x


def _shade(t, flat=False):
    """Light from above: tops +, undersides -, back-facing faces a little down, creases darker."""
    ny, nz = t.n[1], t.n[2]
    s = 0.0
    if ny < -0.5:
        s += 0.6
    elif ny > 0.5:
        s -= 1.0
    elif nz > 0.5:
        s -= 0.3
    if not flat and t.crease < 0.7:
        s -= 0.8
    return s


def _cluster(t, cell=1.5, amount=1.5, seed=1):
    """Tone clusters two to three texels across, stretched along Y so they read as fur tufts."""
    x, y, z = t.p
    return (value_noise((x, y * 0.55, z), cell, seed) - 0.5) * amount


def _ragged(t, amount, cell=1.3, seed=7):
    return (value_noise(t.p, cell, seed) - 0.5) * 2.0 * amount


def _coat_or_ash(t):
    """Material and base tone for fur texels from where they sit on the wolf."""
    x, y, z = t.p
    h = t.height
    lx, ly, lz = t.local
    ny, nz = t.n[1], t.n[2]
    bone = t.bone
    if bone in ("ribcage", "hip"):
        # ash belly and chest meet the coat along a ragged line high enough to read from the side
        line = 15.0 + 2.2 * _clamp((z + 4.0) / 5.0) - 1.6 * _clamp((z - 4.0) / 4.0)
        if ny > 0.5 or h + _ragged(t, 1.6) < line:
            return ASH, 2.4
        if nz < -0.5 and h + _ragged(t, 1.4) < 19.0 and abs(x) < 3.6:
            return ASH, 2.8
        if ny < -0.5:  # dark stripe down the spine
            return COAT, 3.4 - 2.8 * _clamp(1.3 - abs(x) / 2.6)
        depth = t.local[1] - t.cube_top
        return COAT, 3.3 - 1.0 * _clamp(1.0 - depth / 1.6)
    if bone == "neck":
        if t.cube == "neck_ruff":
            return COAT, 1.2 if ny < -0.5 else 2.0
        if ly + _ragged(t, 0.9) > 0.3 or ny > 0.5:
            return ASH, 2.6
        return COAT, 2.9 - 1.6 * _clamp((-ly - 1.0) / 2.0)
    if bone == "head":
        return _head_fur(t)
    if bone == "jaw":
        return ASH, 2.4 if ny < 0.5 else 1.4
    if bone.startswith("ear"):
        part = t.cube.rsplit("_", 1)[1]
        if t.face == "NORTH":  # lighter inner ear with a dark rim
            if part == "cube" and abs(lx) < 1.5:
                return ASH, 3.2 if abs(lx) < 1.0 else 2.4
            if part == "mid" and abs(lx) < 1.0:
                return ASH, 2.2
            return COAT, 1.0
        return COAT, {"cube": 2.8, "mid": 2.0, "tip": 0.8}[part]
    if bone.startswith("tail"):
        if ny > 0.3 or t.face == "UP":
            return ASH, 2.0
        return COAT, 3.2 - (0.8 if bone == "tail_0" else 0.0)
    if "_upper" in bone or "_lower" in bone or "_paw" in bone:
        inner = (t.n[0] * (1 if x > 0 else -1)) < -0.5
        if "_paw" in bone:
            return COAT, 2.2
        if inner:
            return ASH, 2.6
        front_leg = bone.startswith("front")
        if nz > 0.5 and not front_leg:
            return ASH, 2.2  # pale back of the hind leg
        if nz < -0.5 and front_leg and "_lower" in bone:
            return COAT, 1.8  # dark stripe down the front of the foreleg
        return COAT, 3.3 - 0.8 * _clamp((7.0 - h) / 7.0)
    return COAT, 3.0


def _head_fur(t):
    lx, ly, lz = t.local
    ny = t.n[1]
    c = t.cube
    if c == "muzzle":
        if ny < -0.5:
            return COAT, 2.0 - 0.8 * _clamp((lz + 8.0) / 3.0)  # nose bridge darkening toward the stop
        return COAT, 2.4
    if c == "cheek":
        if ny > 0.5 or t.face == "NORTH" or ly + _ragged(t, 0.6) > 0.8:
            return ASH, 3.0
        return COAT, 2.8
    if c == "brow":
        return COAT, 0.0
    # skull: dark mask over the forehead and around the eyes, coat on the sides, pale under the chin
    if ny < -0.5:
        return COAT, 2.6 - 2.0 * _clamp(1.2 - abs(lx) / 2.0)
    if ny > 0.5:
        return ASH, 2.4
    if ly > 0.8:
        return ASH, 2.8
    return COAT, 2.8 - 1.8 * _clamp((-lz - 2.5) / 2.0) * _clamp(1.0 - abs(ly + 1.5) / 2.0)


def paint(t):
    """Base colour of one texel (see paint.Texel)."""
    detail = _detail(t)
    if detail is not None:
        return detail
    if t.bone in PLATE_BONES:
        return _plate_colour(t)
    if t.cube == "tail_tip" or (t.bone == "tail_2" and t.local[2] > 1.5):
        tone = 5.0 if t.cube == "tail_tip" else 2.6
        return pick(TEAL, tone + _shade(t, True) * 0.6 + (value_noise(t.p, 1.2, 13) - 0.5) * 0.8)
    rmp, tone = _coat_or_ash(t)
    tone += _shade(t) + _cluster(t)
    return pick(rmp, tone)


def _plate_colour(t):
    lx, ly, lz = t.local
    h = t.cube_height - PLATE_BURIED  # height above the pivot
    frac = _clamp(-ly / max(h, 1.0))
    tone = 1.0 + 3.4 * frac
    if t.face in ("NORTH", "SOUTH"):
        tone -= 0.6
    if t.face == "DOWN":  # the blade's top edge
        tone = 4.6
    tone += (value_noise(t.p, 1.3, 11) - 0.5) * 0.9
    return pick(TEAL, tone)


def _detail(t):
    """Face-pinned details in bone-local coordinates: eyes, mask, nose, mouth, teeth, claws."""
    lx, ly, lz = t.local
    c, f = t.cube, t.face
    if c == "skull":
        ax = abs(lx)
        if f == "NORTH":
            if -2.0 < ly < -1.0:
                if ax > 1.5:
                    return _hexc(EYE[0] if ax < 2.5 else EYE[1])
                return pick(COAT, 0.0)  # the stop between the eyes
            if ly < -2.0:
                return pick(COAT, 0.4 if ax < 1.5 else 1.2)
            if ly > 1.0 and ax < 1.5:
                return pick(NOSE, 0.0)  # back of the mouth, hidden by the jaw until it opens
            if ly > -1.0 and ax > 1.5:
                return pick(COAT, 0.6) if ly < 0.0 else pick(ASH, 2.2)
        if f in ("WEST", "EAST"):
            if -2.0 < ly < -1.0 and lz < -4.0:
                return _hexc(EYE[2])
            if -3.0 < ly < -2.0 and lz < -3.0:
                return pick(COAT, 0.0)
            if -2.0 < ly < -1.0 and lz < -2.5:
                return pick(TEAL, 1.0)  # a dark teal tear-line running back from the eye
            if -1.0 < ly < 0.0 and lz < -3.5:
                return pick(COAT, 0.8)
    if c == "nose":
        return pick(NOSE, 2.0 if f == "DOWN" else 1.0)
    if c == "muzzle":
        if f == "NORTH":
            if ly < 0.0:
                return pick(NOSE, 0.0) if abs(lx) < 1.0 else pick(COAT, 1.0)
            return pick(COAT, 0.0)  # closed lips
        if f in ("WEST", "EAST") and ly > 0.0:
            return pick(ASH, 1.4 if lz < -8.0 else 2.0)  # pale upper lip
        if f == "UP":  # palate, seen only with the jaw open
            if abs(lx) > 1.0 and lz < -6.0:
                return pick(TOOTH, 1.0)
            return pick(MOUTH, 2.0 if int(math.floor(lz)) % 2 else 1.0)
    if c == "jaw_cube":
        if f == "DOWN":  # tongue and lower teeth, seen only with the jaw open
            if lz < -4.5:
                return pick(TOOTH, 2.0)
            if abs(lx) > 0.5 and lz < -2.5:
                return pick(TOOTH, 1.0)
            return pick(MOUTH, 3.0 if abs(lx) < 0.5 else 1.0)
        if f in ("WEST", "EAST", "NORTH") and ly < 1.0:  # closed lips: a dark line, a glint of tooth in front
            return pick(TOOTH, 0.0) if (f != "NORTH" and lz < -4.5) else pick(COAT, 0.0)
    if c.startswith("fang"):
        return pick(TOOTH, 2.0 if f in ("UP", "NORTH") else 1.0)
    if c.endswith("_paw_cube"):
        bottom = ly > t.cube_bottom - 1.0
        if f == "NORTH" and bottom:
            col = int(math.floor(lx + 1.5))
            return _hexc(CLAW) if col in (0, 2) else pick(COAT, 0.8)
        if f == "DOWN" and lz < -1.5:
            col = int(math.floor(lx + 1.5))
            return pick(COAT, 1.0) if col == 1 else None
    return None


def _hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


MANE_GLOW = "#15504B"  # faint additive light on the blade tips and the tail tip, so the outline reads at dusk


def glow(t):
    """Emissive layer: the eyes, plus a faint glow on the mane blade tips and the tail tip."""
    lx, ly, lz = t.local
    if t.bone in PLATE_BONES:
        return _hexc(MANE_GLOW) + (255,) if t.face == "DOWN" and t.cube_height - PLATE_BURIED >= 3 else None
    if t.cube == "tail_tip":
        return _hexc(MANE_GLOW) + (255,) if t.face == "SOUTH" else None
    if t.cube != "skull":
        return None
    if t.face == "NORTH" and -2.0 < ly < -1.0 and abs(lx) > 1.5:
        return _hexc(GLOW[0] if abs(lx) < 2.5 else GLOW[1]) + (255,)
    if t.face in ("WEST", "EAST") and -2.0 < ly < -1.0 and lz < -4.0:
        return _hexc(GLOW[2]) + (255,)
    return None


SKIP_CREASE = ("brow", "nose", "fang_right", "fang_left", "tail_tip") + tuple(f"{p}_cube" for p in PLATE_BONES)


# ------------------------------------------------------------------------------------ clips
# Poses are offsets from rest in the file's convention (degrees; position +y up). Rotation signs in
# model space: +X pitches forward-pointing parts down (head, chest front), back-pointing parts up
# (tail, rump) and swings a leg's foot backward.

def _merge(*parts):
    out = {}
    for p in parts:
        for bone, targets in p.items():
            e = out.setdefault(bone, {})
            for t, v in targets.items():
                old = e.get(t)
                if old is None:
                    e[t] = tuple(v)
                elif t == "scale":
                    e[t] = tuple(a * b for a, b in zip(old, v))
                else:
                    e[t] = tuple(a + b for a, b in zip(old, v))
    return out


def rot(bone, x=0.0, y=0.0, z=0.0):
    return {bone: {"rotation": (x, y, z)}}


def pos(bone, x=0.0, y=0.0, z=0.0):
    return {bone: {"position": (x, y, z)}}


def bristle(amount, sweep=0.0):
    """Mane plates: amount 1 raises them toward upright and lengthens them; sweep lays them back."""
    out = {}
    for i, name in enumerate(PLATE_BONES):
        w = 1.0 - 0.06 * abs(i - 3)
        out[name] = {"rotation": (amount * 26.0 * w - sweep, 0.0, 0.0)}
        if amount:
            out[name]["scale"] = (1.0, 1.0 + 0.32 * amount * w, 1.0 + 0.1 * amount)
    return out


def ears(x, spread=0.0):
    return _merge(rot("ear_right", x, 0.0, -spread), rot("ear_left", x, 0.0, spread))


def tail(a0, a1=0.0, a2=0.0, yaw=0.0):
    return _merge(rot("tail_0", a0, yaw), rot("tail_1", a1, yaw * 0.8), rot("tail_2", a2, yaw * 0.6))


def _sides(d):
    """{'front': v, 'hind_left': w, ...} -> per leg."""
    out = {}
    for name, *_ in LEGS:
        for key in (name, name.split("_")[0], "all"):
            if key in (d or {}):
                out[name] = d[key]
                break
    return out


def stance(model, pose, shift=None, lift=None, pitch=None, report=None):
    """Solve every leg of `pose` so its sole lands at the rest sole moved by shift (+Z back), lifted by
    lift units, with the paw pitched by pitch degrees."""
    lg = legs(model)
    err = quad.solve_legs(model, pose, lg, quad.planted(model, lg, _sides(shift), _sides(lift), _sides(pitch)))
    if report is not None:
        report.append(err)
    return pose


def _tick(t):
    return t / 20.0


def clip_idle(model):
    """3 s loop: breathing in the ribcage, small head and ear movement, tail sway. No leg or body
    channels, so it adds cleanly under every other clip."""
    length, n = 3.0, 12
    keys = []
    for i in range(n + 1):
        ph = (i % n) / n
        breath = 0.5 - 0.5 * math.cos(4 * math.pi * ph)
        flick = math.exp(-((ph - 0.62) / 0.05) ** 2)
        pose = _merge(
            {"ribcage": {"scale": (1.0 + 0.03 * breath, 1.0 + 0.035 * breath, 1.0)}},
            rot("neck", 1.5 * math.sin(2 * math.pi * ph)),
            rot("head", 2.0 * math.sin(2 * math.pi * ph + 0.6), 5.0 * math.sin(2 * math.pi * ph + 1.2)),
            rot("jaw", 1.5 * breath),
            rot("ear_right", 10.0 * flick), rot("ear_left", 3.0 * math.sin(2 * math.pi * ph)),
            tail(2.0 * math.sin(4 * math.pi * ph), 0.0, 0.0, 7.0 * math.sin(2 * math.pi * ph)),
            rot("tail_2", 0.0, 6.0 * math.sin(2 * math.pi * ph - 1.4)),
        )
        keys.append(anim.Key(length * i / n, pose))
    return anim.Clip("idle", length, True, keys)


# Gaits: (cycle seconds, samples, duty, stride, lift front/hind, paw flex front/hind, touch-down phases)
WALK = {"length": 1.0, "samples": 16, "duty": 0.6, "stride": 9.0, "lift": (2.6, 2.2), "flex": (40.0, 25.0),
        "phase": {"front_left": 0.0, "hind_right": 0.0, "front_right": 0.5, "hind_left": 0.5}}
RUN = {"length": 0.6, "samples": 16, "duty": 0.36, "stride": 11.0, "lift": (4.5, 3.6), "flex": (60.0, 35.0),
       "phase": {"hind_left": 0.0, "hind_right": 0.05, "front_left": 0.5, "front_right": 0.56}}


def _gait_feet(g):
    def feet(leg, ph):
        front = leg.startswith("front")
        u = (ph - g["phase"][leg]) % 1.0
        return quad.foot_track(u, g["duty"], g["stride"], g["lift"][0 if front else 1], g["flex"][0 if front else 1])
    return feet


def clip_walk(model):
    """Trot: diagonal pairs, body dipping a little at each footfall, head nodding with it."""
    g = WALK

    def body(ph):
        dip = 0.5 + 0.5 * math.cos(4 * math.pi * ph)
        return _merge(
            pos("body", 0.0, -0.7 * dip, 0.0),
            rot("neck", 2.5 * dip), rot("head", -1.5 * dip),
            ears(-6.0),
            tail(4.0, 0.0, 0.0, 7.0 * math.sin(2 * math.pi * ph)),
        )
    keys, err = quad.gait_cycle(model, legs(model), g["length"], g["samples"], body, _gait_feet(g))
    return anim.Clip("walk", g["length"], True, keys, {"ik_residual": err})


def clip_run(model):
    """Bounding gallop: hind pair then front pair; the spine extends in the hind push and arches while
    the front pair is down; tail streams, ears pinned, head low and forward."""
    g = RUN

    def body(ph):
        flex = math.cos(2 * math.pi * (ph - 0.75))
        pitch = -5.0 * math.cos(2 * math.pi * (ph - 0.3))
        rise = 0.5 - 0.5 * math.cos(4 * math.pi * (ph - 0.19))
        return _merge(
            pos("body", 0.0, -1.6 + 1.8 * rise, 0.0),
            rot("body", pitch),
            rot("chest", 6.0 * flex), rot("hip", -6.0 * flex),
            rot("neck", 10.0 - 0.6 * pitch + 3.0 * flex), rot("head", -8.0),
            rot("jaw", 10.0 + 4.0 * rise),
            ears(-22.0),
            bristle(0.0, 12.0),
            tail(12.0 - 4.0 * flex, 8.0 * math.sin(2 * math.pi * (ph - 0.2)), 10.0 * math.sin(2 * math.pi * (ph - 0.35))),
        )
    keys, err = quad.gait_cycle(model, legs(model), g["length"], g["samples"], body, _gait_feet(g))
    return anim.Clip("run", g["length"], True, keys, {"ik_residual": err})


def _move(server, clip):
    for m in server["moves"]:
        if m["animation"] == clip:
            return m
    raise KeyError(f"no move uses animation {clip!r}")


LEG_BONES = tuple(b for leg in LEGS for b in leg[1:4])


def _sampler(keys, length):
    """pose(time in ticks) along authored keys, read back through the vanilla sampler."""
    chans = anim.Clip("tmp", length, False, keys).channels()

    def pose(tick):
        out = {}
        for bone, target, frames in chans:
            out.setdefault(bone, {})[target] = anim.sample_channel(frames, _tick(tick), length, False)
        return out
    return pose


def bite_steps(move):
    """Footwork that carries the bite's ground lunge (the server slides the beast lunge.forward_* blocks
    under ground friction, quadruped.move_flight): leg -> [(lift-off tick, landing tick, landing point
    in blocks of travel from the start, lift units, paw flex degrees)]. Between steps a paw is planted:
    fixed in the world, so it moves back under the body exactly as the body travels. The forepaws leave
    as the lunge fires and land on the strike where the beast comes to rest; the hind feet push off,
    land short and step up under the body during the recovery, one after the other."""
    f = quad.move_flight(move)
    lunge, a0 = move["lunge"]["tick"], move["active_ticks"][0]
    end = f.at(move["total_ticks"])[0]
    if end <= 0:
        return {name: [] for name, *_ in LEGS}
    rec0 = move["active_ticks"][1] + 4
    short = end - 5.0 / 16.0  # where the hind feet land: as far back as they comfortably reach at rest
    return {
        "front_right": [(lunge - 1, a0, end, 3.0, 40.0)],
        "front_left": [(lunge - 1, a0 + 0.25, end, 3.0, 40.0)],
        "hind_right": [(lunge - 0.5, a0 + 0.75, short, 2.4, 30.0), (rec0, rec0 + 3, end, 2.2, 25.0)],
        "hind_left": [(lunge - 0.5, a0 + 1.0, short, 2.4, 30.0), (rec0 + 3, rec0 + 6, end, 2.2, 25.0)],
    }


def _foot(steps, travel, tick):
    """(shift +Z back, lift, paw pitch) of one paw at a move tick, in model units, from its steps and the
    beast's travel (blocks) at that tick."""
    world = 0.0
    for t0, t1, at, h, flex in steps:
        if tick <= t0:
            break
        if tick < t1:
            w = (tick - t0) / (t1 - t0)
            pos_ = world + (at - world) * quad.smooth(w)
            return (travel - pos_) * 16.0, h * math.sin(math.pi * w) ** 1.2, flex * math.sin(math.pi * w)
        world = at
    return (travel - world) * 16.0, 0.0, 0.0


def clip_bite(model, server):
    """Rear back and open by turn_lock_tick, snap forward and down at lunge.tick, clamp on the first
    active tick, worry through the active ticks, then a slow head-low recovery. The legs carry the
    server's lunge (bite_steps): no paw slides while it is down."""
    m = _move(server, "bite")
    lock, lunge = m["turn_lock_tick"], m["lunge"]["tick"]
    a0, a1 = m["active_ticks"]
    total = m["total_ticks"]
    flight = quad.move_flight(m)
    steps = bite_steps(m)
    errs = []

    def rear(k):  # the wind-up, k = 0..1
        return _merge(
            pos("body", 0.0, -1.0 * k, 1.4 * k), rot("body", -6.0 * k),
            rot("neck", -26.0 * k), rot("head", -10.0 * k), rot("jaw", 34.0 * k),
            ears(-24.0 * k), bristle(1.0 * k), tail(12.0 * k),
        )

    # body, head and jaw: the authored telegraph and strike (legs are solved per key below)
    body = [
        (0, {}, "catmullrom", "rest"),
        (lock / 2, rear(0.6), "catmullrom", ""),
        (lock, rear(1.0), "catmullrom", "turn_lock"),
        (lunge - 1, _merge(rear(1.0), rot("neck", -4.0), rot("jaw", 6.0), rot("head", -3.0)), "catmullrom", "held"),
        (lunge, _merge(
            pos("body", 0.0, -0.4, -1.2), rot("body", 3.0),
            rot("neck", 8.0), pos("neck", 0.0, 0.0, -1.5), rot("head", 4.0), rot("jaw", 40.0),
            ears(-30.0), bristle(1.0, 6.0), tail(14.0)), "linear", "lunge"),
        (a0, _merge(
            pos("body", 0.0, -0.8, -2.0), rot("body", 5.0),
            rot("neck", 18.0), pos("neck", 0.0, 0.0, -2.5), rot("head", 10.0), rot("jaw", -2.0),
            ears(-30.0), bristle(0.8, 6.0), tail(10.0)), "linear", "active_first"),
    ]
    if a1 > a0:
        body.append(((a0 + a1) / 2, _merge(
            pos("body", 0.0, -0.8, -2.0), rot("body", 5.0),
            rot("neck", 19.0, 6.0), pos("neck", 0.0, 0.0, -2.5), rot("head", 11.0, 4.0, 9.0), rot("jaw", -2.0),
            ears(-30.0), bristle(0.8, 6.0), tail(10.0)), "catmullrom", "worry"))
        body.append((a1, _merge(
            pos("body", 0.0, -0.8, -1.8), rot("body", 5.0),
            rot("neck", 18.0, -5.0), pos("neck", 0.0, 0.0, -2.2), rot("head", 11.0, -3.0, -8.0), rot("jaw", 0.0),
            ears(-28.0), bristle(0.7, 4.0), tail(8.0)), "catmullrom", "active_last"))
    rec = total - a1
    body += [
        (a1 + rec * 0.2, _merge(
            pos("body", 0.0, -1.0, -1.0), rot("body", 4.0),
            rot("neck", 22.0), pos("neck", 0.0, 0.0, -1.2), rot("head", 12.0), rot("jaw", 8.0),
            ears(-14.0), bristle(0.4), tail(2.0)), "catmullrom", "recovery_low"),
        (a1 + rec * 0.55, _merge(
            pos("body", 0.0, -0.5, -0.3), rot("body", 2.0),
            rot("neck", 14.0), rot("head", 7.0), rot("jaw", 5.0),
            ears(-6.0), bristle(0.15), tail(0.0)), "catmullrom", "recovery"),
        (a1 + rec * 0.82, _merge(rot("neck", 4.0), rot("head", 2.0), rot("jaw", 1.5)), "catmullrom", "settle"),
        (total, {}, "catmullrom", "end"),
    ]
    authored = {t: (interp, label) for t, _, interp, label in body}
    body_at = _sampler([anim.Key(_tick(t), p, i, lb) for t, p, i, lb in body], _tick(total))

    # extra keys wherever the feet move: every quarter tick through the lunge (the slide is fastest and
    # the forepaws swing), every half tick through the slide's tail and the later steps
    moving = {t / 4.0 for t in range(4 * (lunge - 1), 4 * (a0 + 1) + 1)}
    moving |= {t / 2.0 for t in range(2 * (a0 + 1), 2 * (a1 + 4) + 1)}
    for leg_steps in steps.values():
        for t0, t1, *_ in leg_steps:
            moving |= {t / 2.0 for t in range(int(math.floor(2 * t0)), int(math.ceil(2 * t1)) + 1)}
    times = sorted({t for t, *_ in body} | {t for t in moving if 0 < t < total})
    keys = []
    for t in times:
        if t in (0, total):
            keys.append(anim.Key(_tick(t), {}, "catmullrom", authored[t][1]))
            continue
        pose = {b: v for b, v in body_at(t).items() if b not in LEG_BONES}
        travel = flight.at(t)[0]
        feet = {leg: _foot(steps[leg], travel, t) for leg in steps}
        stance(model, pose, shift={k: v[0] for k, v in feet.items()}, lift={k: v[1] for k, v in feet.items()},
               pitch={k: v[2] for k, v in feet.items()}, report=errs)
        interp, label = authored.get(t, ("linear" if t <= a1 + 4 else "catmullrom", ""))
        keys.append(anim.Key(_tick(t), pose, interp, label))
    return anim.Clip("bite", _tick(total), False, keys, {"ik_residual": max(errs)})


def clip_pounce(model, server):
    """Deep crouch that keeps sinking through the wind-up, full extension at lunge.tick, flight posture
    (forelegs reaching, hind legs trailing; the server supplies the arc), touch-down and compression on
    the tick the server lands (derived from lunge.tick and lunge.up), then a skid back to rest."""
    m = _move(server, "pounce")
    lock, lunge = m["turn_lock_tick"], m["lunge"]["tick"]
    a0, a1 = m["active_ticks"]
    total = m["total_ticks"]
    land = landing_tick(m)
    errs = []

    def crouch(k, coil):
        return stance(model, _merge(
            pos("body", 0.0, -5.6 * k, 1.6 * k), rot("body", 5.0 * k), rot("chest", 2.0 * k),
            rot("neck", 2.0 * k), rot("head", -8.0 * k), rot("jaw", 12.0 * k),
            ears(10.0 * k), bristle(1.0 * k), tail(10.0 * k, -4.0 * k, -3.0 * k),
        ), shift={"front": -1.6 * k, "hind": -3.4 * coil}, report=errs)

    rec = total - a1
    keys = [
        anim.Key(0.0, {}, "catmullrom", "rest"),
        anim.Key(_tick(round(lock * 0.15)), crouch(0.12, 0.08)),
        anim.Key(_tick(round(lock * 0.35)), crouch(0.42, 0.32)),
        anim.Key(_tick(round(lock * 0.65)), crouch(0.7, 0.65)),
        anim.Key(_tick(lock), crouch(0.85, 0.85), "catmullrom", "turn_lock"),
        anim.Key(_tick(lunge - 2), _merge(crouch(1.0, 1.0), rot("tail_2", 0.0, 10.0)), "catmullrom", "deepest"),
        anim.Key(_tick(lunge - 1), _merge(crouch(1.05, 1.05), rot("tail_2", 0.0, -8.0)), "catmullrom", "coiled"),
        anim.Key(_tick(lunge - 0.5), stance(model, _merge(
            pos("body", 0.0, -2.6, -0.2), rot("body", -12.0), rot("chest", -2.0), rot("hip", 3.0),
            rot("neck", -2.0), rot("head", 0.0), rot("jaw", 24.0),
            ears(-20.0), bristle(0.8, 8.0), tail(12.0, 0.0, 0.0)),
            shift={"front": -6.0, "hind": -3.4}, lift={"front": 4.0, "hind": 0.5}, pitch={"front": 30.0, "hind": 10.0},
            report=None), "linear", "push"),
        # flight: the server supplies the whole arc, so these keys carry posture only (no body height)
        anim.Key(_tick(lunge), stance(model, _merge(
            pos("body", 0.0, 0.0, -1.0), rot("body", -20.0), rot("chest", -4.0), rot("hip", 6.0),
            rot("neck", -4.0), rot("head", 4.0), rot("jaw", 30.0),
            ears(-30.0), bristle(0.6, 14.0), tail(14.0, 4.0, 4.0)),
            shift={"front": -9.0, "hind": 7.0}, lift={"front": 7.0, "hind": 3.6}, pitch={"front": 40.0, "hind": 30.0},
            report=None), "linear", "lunge"),
        anim.Key(_tick(a0), stance(model, _merge(
            pos("body", 0.0, 0.0, -1.0), rot("body", -12.0), rot("chest", -3.0), rot("hip", 5.0),
            rot("neck", 2.0), rot("head", 6.0), rot("jaw", 40.0),
            ears(-32.0), bristle(0.5, 16.0), tail(14.0, 6.0, 6.0)),
            shift={"front": -10.0, "hind": 8.0}, lift={"front": 6.0, "hind": 3.0}, pitch={"front": 30.0, "hind": 60.0},
            report=None), "catmullrom", "active_first"),
        anim.Key(_tick((a0 + land) / 2.0), stance(model, _merge(
            pos("body", 0.0, 0.0, -0.5), rot("body", 0.0), rot("chest", 2.0),
            rot("neck", 6.0), rot("head", 6.0), rot("jaw", 40.0),
            ears(-30.0), bristle(0.5, 14.0), tail(10.0, 8.0, 10.0)),
            shift={"front": -8.0, "hind": 3.0}, lift={"front": 4.0, "hind": 5.0}, pitch={"front": 20.0, "hind": 30.0},
            report=None), "catmullrom", "airborne"),
        anim.Key(_tick(land - 2), stance(model, _merge(
            pos("body", 0.0, 0.0, 0.0), rot("body", 9.0), rot("chest", 3.0),
            rot("neck", 12.0), rot("head", 8.0), rot("jaw", 6.0),
            ears(-24.0), bristle(0.5, 8.0), tail(16.0, 6.0, 6.0)),
            shift={"front": -5.0, "hind": -2.0}, lift={"front": 0.5, "hind": 4.0}, pitch={"front": 0.0, "hind": 20.0},
            report=None), "catmullrom", "reach_down"),
        # touch-down on the tick the server lands: all four paws planted, nose-down, the legs only
        # starting to give; the body keeps sinking for a tick after contact (absorb), then the brake
        anim.Key(_tick(land), stance(model, _merge(
            pos("body", 0.0, -1.8, -0.5), rot("body", 8.0), rot("chest", 3.0),
            rot("neck", 14.0), rot("head", 2.0), rot("jaw", 0.0),
            ears(-18.0), bristle(0.4), tail(14.0, -6.0)),
            shift={"front": -4.0, "hind": -1.8}, report=errs), "linear", "landing"),
        anim.Key(_tick(land + 1), stance(model, _merge(
            pos("body", 0.0, -3.6, -0.2), rot("body", 7.0), rot("chest", 4.0),
            rot("neck", 17.0), rot("head", 1.0), rot("jaw", 2.0),
            ears(-17.0), bristle(0.4), tail(13.0, -7.0)),
            shift={"front": -4.0, "hind": -1.8}, report=errs), "linear", "absorb"),
        anim.Key(_tick(max(a1, land + 2)), stance(model, _merge(
            pos("body", 0.0, -3.0, 0.5), rot("body", 2.0), rot("chest", 2.0), rot("hip", 1.0),
            rot("neck", 10.0), rot("jaw", 3.0),
            ears(-14.0), bristle(0.35), tail(11.0, -5.0)),
            shift={"front": -4.0, "hind": -1.8}, report=errs), "catmullrom", "brake"),
        anim.Key(_tick(a1 + rec * 0.45), stance(model, _merge(
            pos("body", 0.0, -1.6, 1.2), rot("body", -5.0), rot("hip", 2.0),
            rot("neck", 4.0), rot("head", -2.0), rot("jaw", 6.0),
            ears(-10.0), bristle(0.3), tail(8.0, -4.0)),
            shift={"front": -4.0, "hind": -1.8}, report=errs), "catmullrom", "skid"),
        anim.Key(_tick(a1 + rec * 0.75), stance(model, _merge(
            pos("body", 0.0, -0.5, 0.4), rot("body", -1.5),
            rot("neck", 2.0), rot("jaw", 2.0), ears(-3.0), bristle(0.1), tail(3.0)),
            shift={"front": -1.2, "hind": -0.6}, report=errs), "catmullrom", "settle"),
        anim.Key(_tick(total), {}, "catmullrom", "end"),
    ]
    return anim.Clip("pounce", _tick(total), False, keys, {"ik_residual": max(errs)})


def landing_tick(move):
    """The server tick the beast is back on flat ground after the move's lunge (vanilla gravity)."""
    return quad.jump_arc(move["lunge"]["tick"], move["lunge"]["up"])[1]


def clip_stagger(model):
    """Half-second flinch: head thrown aside, body recoiling back, then home."""
    errs = []
    keys = [
        anim.Key(0.0, {}, "catmullrom", "rest"),
        anim.Key(_tick(2), stance(model, _merge(
            pos("body", 0.0, -0.7, 1.4), rot("body", -5.0),
            rot("neck", -8.0, 20.0, -8.0), rot("head", -6.0, 16.0, -14.0), rot("jaw", 16.0),
            ears(-26.0, 6.0), bristle(0.0, 10.0), tail(-10.0, -6.0)), report=errs), "catmullrom", "flinch"),
        anim.Key(_tick(5), stance(model, _merge(
            pos("body", 0.0, -0.2, 0.6), rot("body", -1.0),
            rot("neck", 3.0, -6.0, 3.0), rot("head", 2.0, -5.0, 4.0), rot("jaw", 6.0),
            ears(-10.0), tail(-3.0)), report=errs), "catmullrom", "rebound"),
        anim.Key(_tick(10), {}, "catmullrom", "end"),
    ]
    return anim.Clip("stagger", _tick(10), False, keys, {"ik_residual": max(errs)})


def clips(model, server):
    return [clip_idle(model), clip_walk(model), clip_run(model), clip_bite(model, server),
            clip_pounce(model, server), clip_stagger(model)]
