"""Offline preview of a PAL (Player Animation Library 1.1.4) player pose holding an item model.

    python3 -m tools.combat_preview pose \
        --animations src/main/resources/assets/myvillage/player_animations/sword_combat.json \
        --animation basic_sword_02_horizontal_cut --ticks 0,3,4,4.9,6,9 \
        --item myvillage:item/qingfeng_sword --views back,front,side,top --out out/preview/combat_preview/pose.png

Renders a sheet (rows = views, columns = ticks) of a vanilla-proportioned wide-arm player box
figure posed by the animation at each tick, with the item in the right hand.  Light work: numpy
software rasteriser, no Minecraft.

Transform chain (decompiled from the PAL 1.1.4 jar and vanilla 1.21.1; world = Minecraft world
coordinates, player at the origin facing south (+Z), body yaw 0, so the player's right is -X):

  entity  = Ry(180 - bodyYaw)                                   LivingEntityRenderer.setupRotations
          * S(body.scale) * T(-bpx/16, bpy/16 + 0.75, bpz/16)   PlayerRendererMixin.applyBodyTransforms
          * Rz(bz) Ry(-by) Rx(-bx) * T(0, -0.75, 0)              (JOML rotateZYX)
          * S(-1, -1, 1) * S(0.9375) * T(0, -1.501, 0)          LivingEntityRenderer / PlayerRenderer.scale
  part    = entity * T(part.xyz/16) * Rz(z) Ry(y) Rx(x)          ModelPart.translateAndRotate
            part.rot = PAL value in radians, same sign (no Bedrock negation in PAL's loader);
            part.x = posX + init.x, part.y = -posY + init.y, part.z = posZ + init.z when the
            bone has a position channel, otherwise the vanilla values.  Bone 'torso' drives the
            vanilla body part (pivot at the neck); arms/head are not its children.
  item    = rightArm(part) * T(ipx/16, -ipy/16, ipz/16)          ItemInHandLayerMixin.changeItemLocation
          * Rx(-90) Ry(180) * T(1/16, 2/16, -10/16)              ItemInHandLayer.renderArmWithItem
          * Rz(-iy) Ry(-iz) Rx(-ix) * S(item scale)              ItemInHandLayerMixin.changeItemRotationAndScale
          * display(thirdperson_righthand) * T(-0.5) * px/16    ItemTransform.apply + ItemRenderer

Sampling: per axis, keyframes in JSON order; keyframe i spans (t[i-1], t[i]] and is shaped by
keyframe i's easing (PAL AnimationLoader.buildKeyframeStack); past the last key the last value
holds.  Channels the animation does not animate keep the vanilla pose (head pitch = camera pitch,
right arm -18 deg item pose, no idle arm bob).

Views: back / front reproduce tools/combat_capture's F5 cameras (eye at 1.62, barrier wall 2.4
blocks from the eye, vanilla Camera.getMaxZoom clip, back pitch 30, front pitch 15, vertical FOV 70,
16:9).  By default the window is cropped to the figure + item over all ticks of the row (same
projection, so the view is the game's); --frame game shows the full 960x540 game frame instead.
side (from the player's right, facing = screen right) and top (facing = screen up, the player's
right = screen right) are orthographic.

Validated 2026-10-02 against the after-data-infra capture (0.27.1-fix1 build): 22 back/front frames of all five moves agree to 0.1-1.4 % silhouette XOR (the rest is keying
noise), with --arms slim because that capture's default skin is slim.  With the default wide arms
the arms sit 1 model px further out than in those frames.  Not modelled: skin outer layers, cape,
armour, vanilla idle arm bob on undriven channels, bend, molang values, catmullrom/bezier easing.
"""
import argparse
import json
import math
import os
import time
from pathlib import Path

from . import item_model as pim
from .env import DEFAULT_ROOT, note_missing_jar

np = pim.np
Image = pim.Image
ImageDraw = pim.ImageDraw
sheets = pim.sheets

TICKS_PER_SECOND = 20.0
MODEL_SCALE = 0.9375
EYE_HEIGHT = 1.62
PX = 1.0 / 16.0
PLAYER_PX = PX * MODEL_SCALE  # one model-part pixel in blocks (the fist is 4 of these wide)

# ------------------------------------------------------------------------------------- easing
EASE_BASE = {
    "sine": lambda t, a: 1.0 - math.cos(t * math.pi / 2.0),
    "quad": lambda t, a: t * t,
    "cubic": lambda t, a: t * t * t,
    "quart": lambda t, a: t ** 4,
    "quint": lambda t, a: t ** 5,
    "expo": lambda t, a: 2.0 ** (10.0 * (t - 1.0)),
    "circ": lambda t, a: 1.0 - math.sqrt(max(0.0, 1.0 - t * t)),
    "back": lambda t, a: t * t * (((1.70158 if a is None else a * 1.70158) + 1.0) * t
                                  - (1.70158 if a is None else a * 1.70158)),
    "elastic": lambda t, a: 1.0 - math.cos(t * math.pi / 2.0) ** 3 * math.cos(t * (1.0 if a is None else a) * math.pi),
    "bounce": lambda t, a: _bounce(t, 0.5 if a is None else a),
}


def _bounce(t, n):
    return min(7.5625 * t * t, 30.25 * n * (t - 0.54545456) ** 2 + 1 - n,
               121.0 * n * n * (t - 0.8181818) ** 2 + 1 - n * n,
               484.0 * n ** 3 * (t - 0.95454544) ** 2 + 1 - n ** 3)


KNOWN_EASINGS = {"linear", "constant", "step"} | {p + b for p in ("easein", "easeout", "easeinout") for b in EASE_BASE}


def ease(name, t, arg=None):
    """PAL EasingType: easeIn = f, easeOut = 1 - f(1 - t), easeInOut split at 0.5."""
    if name == "linear":
        return t
    if name == "constant":
        return 0.0
    if name == "step":
        steps = int(arg or 2)
        if t < 0:
            return 0.0
        return min(math.floor(t * steps), steps - 1) / steps
    for prefix in ("easeinout", "easeout", "easein"):
        if name.startswith(prefix) and name[len(prefix):] in EASE_BASE:
            f = EASE_BASE[name[len(prefix):]]
            if prefix == "easein":
                return f(t, arg)
            if prefix == "easeout":
                return 1.0 - f(1.0 - t, arg)
            return f(t * 2.0, arg) / 2.0 if t < 0.5 else 1.0 - f((1.0 - t) * 2.0, arg) / 2.0
    return t  # PAL: unknown names load as LINEAR


# ------------------------------------------------------------------------------------- PAL file
class Track:
    """One axis of one channel: PAL Keyframe list (length ticks, start, end, easing, arg)."""

    def __init__(self, frames):
        self.frames = frames

    def value(self, tick):
        total = 0.0
        for length, a, b, e, arg in self.frames:
            total += length
            if total > tick:
                return self._lerp(a, b, e, arg, tick - (total - length), length)
        length, a, b, e, arg = self.frames[-1]
        return self._lerp(a, b, e, arg, tick, length)  # PAL: KeyframeLocation(last, ageInTicks)

    @staticmethod
    def _lerp(a, b, e, arg, cur, length):
        r = cur / length if length > 0 else (math.inf if cur > 0 else math.nan)
        if r >= 1.0:
            return b
        if math.isnan(r):
            return a
        return a + (b - a) * ease(e, r, arg)


def _num(v, warnings, where):
    try:
        return float(v)
    except (TypeError, ValueError):
        warnings.append(f"{where}: non-constant value {v!r} (molang) read as 0")
        return 0.0


def _bedrock_vec(el):
    if isinstance(el, list):
        return el
    if isinstance(el, (int, float, str)):
        return [el, 0, 0]
    if "vector" in el:
        return el["vector"]
    return el["pre"] if "pre" in el else el["post"]


def channel_entries(el):
    """AnimationLoader.getKeyframes: [(time_s, vector, easing_object_or_None)] in JSON order."""
    if el is None:
        return []
    if isinstance(el, (int, float, str)):
        return [(0.0, [el, el, el], None)]
    if isinstance(el, list):
        return [(0.0, el, None)]
    if "vector" in el:
        return [(0.0, el["vector"], el)]
    if "value" in el:
        return [(0.0, [el["value"], 0, 0], el)]
    out = []
    for k, v in el.items():
        try:
            ts = float(k)
        except ValueError:
            ts = 0.0
        if isinstance(v, dict):
            if "value" in v:
                out.append((ts, [v["value"], 0, 0], v))
            elif "vector" in v:
                out.append((ts, v["vector"], v))
            else:  # Bedrock pre/post
                if "pre" in v:
                    ez = {"easing": v["easing"]} if "easing" in v else None
                    if ez and "easingArgs" in v:
                        ez["easingArgs"] = v["easingArgs"]
                    out.append((ts - 0.001 if ts else ts, _bedrock_vec(v["pre"]), ez))
                if "post" in v:
                    out.append((ts, _bedrock_vec(v["post"]), {"easing": v["lerp_mode"]} if "lerp_mode" in v else None))
        else:
            out.append((ts, v, None))
    return out


def build_tracks(entries, rotation, warnings, where):
    if not entries:
        return None
    tracks = []
    for ai, axis in enumerate("XYZ"):
        frames = []
        prev_t, prev_v = 0.0, None
        for ts, vec, obj in entries:
            v = _num(vec[ai] if ai < len(vec) else 0, warnings, where)
            if rotation:
                v = math.radians(v)
            e = "linear"
            arg = None
            if obj:
                e = str(obj.get("easing" + axis, obj.get("easing", "linear"))).lower()
                args = obj.get("easingArgs" + axis, obj.get("easingArgs"))
                if args:
                    arg = float(args[0])
            if e not in KNOWN_EASINGS:
                warnings.append(f"{where}: easing {e!r} not modelled (catmullrom/bezier/unknown) - drawn linear")
                e = "linear"
            frames.append(((ts - prev_t) * TICKS_PER_SECOND, v if prev_v is None else prev_v, v, e, arg))
            prev_t, prev_v = ts, v
        if len(frames) == 1:
            frames = [(frames[0][0], frames[0][1], frames[0][2], "linear", None)]
        tracks.append(Track(frames))
    return tracks


class PalAnimation:
    def __init__(self, path, anim_id):
        doc = json.loads(Path(path).read_text(encoding="utf-8"))
        anims = doc.get("animations", {})
        key = anim_id.split(":", 1)[1] if ":" in anim_id else anim_id
        if key not in anims:
            raise SystemExit(f"ERROR: animation {key!r} not in {path}; it has: {', '.join(anims)}")
        self.id = key
        a = anims[key]
        self.length = float(a.get("animation_length", 0)) * TICKS_PER_SECOND
        self.loop = a.get("loop")
        self.warnings = []
        self.bones = {}
        self.key_times = set()
        for bone, chans in a.get("bones", {}).items():
            name = bone if bone.islower() else "".join("_" + c.lower() if c.isupper() else c for c in bone)
            b = {}
            for ch in ("rotation", "position", "scale", "bend"):
                entries = channel_entries(chans.get(ch))
                if ch == "bend" and entries:
                    self.warnings.append(f"{name}: bend channel ignored")
                    continue
                for ts, _, _ in entries:
                    self.key_times.add(round(ts * TICKS_PER_SECOND, 4))
                b[ch] = build_tracks(entries, ch == "rotation", self.warnings, f"{name}.{ch}")
            self.bones[name] = b
        for name in self.bones:
            if name not in KNOWN_BONES:
                self.warnings.append(f"bone {name!r} is not drawn by this preview")

    def sample(self, bone, channel, tick):
        """[x, y, z] with None for axes the animation does not drive (rotation in radians)."""
        tr = self.bones.get(bone, {}).get(channel)
        if not tr:
            return [None, None, None]
        return [t.value(tick) for t in tr]


KNOWN_BONES = ("body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item")

# ------------------------------------------------------------------------------------- figure
# name: (initial pivot, vanilla part pos, boxes [(lo, hi, role)]) in model px (y down, -z front)
PARTS = {
    "head": ((0, 0, 0), (0, 0, 0), [((-4, -8, -4), (4, 0, 4), "head")]),
    "torso": ((0, 0, 0), (0, 0, 0), [((-4, 0, -2), (4, 12, 2), "torso")]),
    "right_arm": ((-5, 2, 0), (-5, 2, 0), [((-3, -2, -2), (1, 6, 2), "rarm"), ((-3, 6, -2), (1, 10, 2), "rfist")]),
    "left_arm": ((5, 2, 0), (5, 2, 0), [((-1, -2, -2), (3, 6, 2), "larm"), ((-1, 6, -2), (3, 10, 2), "lfist")]),
    "right_leg": ((-1.9, 12, 0), (-1.9, 12, 0.1), [((-2, 0, -2), (2, 10, 2), "rleg"), ((-2, 10, -2), (2, 12, 2), "rshoe")]),
    "left_leg": ((1.9, 12, 0), (1.9, 12, 0.1), [((-2, 0, -2), (2, 10, 2), "lleg"), ((-2, 10, -2), (2, 12, 2), "lshoe")]),
}
# Slim (Alex-type) arms: 3 px wide, initial pivot y 2.5 (HumanoidModel.setupAnim still sets y 2), and
# PlayerModel.translateToHand moves the hand 0.5 px toward the body before the item chain.
SLIM_ARMS = {
    "right_arm": ((-5, 2.5, 0), (-5, 2, 0), [((-2, -2, -2), (1, 6, 2), "rarm"), ((-2, 6, -2), (1, 10, 2), "rfist")]),
    "left_arm": ((5, 2.5, 0), (5, 2, 0), [((-1, -2, -2), (2, 6, 2), "larm"), ((-1, 6, -2), (2, 10, 2), "lfist")]),
}
# role -> (side colour, front colour (model north = the player's front), back colour)
COLORS = {
    "head": ((0.86, 0.69, 0.55), (0.93, 0.77, 0.63), (0.33, 0.22, 0.14)),
    "torso": ((0.24, 0.55, 0.60), (0.55, 0.82, 0.86), (0.13, 0.33, 0.37)),
    "rarm": ((0.95, 0.55, 0.25), (1.00, 0.68, 0.40), (0.72, 0.38, 0.16)),
    "rfist": ((0.72, 0.30, 0.10), (0.80, 0.38, 0.16), (0.55, 0.22, 0.08)),
    "larm": ((0.50, 0.50, 0.92), (0.66, 0.66, 1.00), (0.34, 0.34, 0.70)),
    "lfist": ((0.30, 0.30, 0.70), (0.38, 0.38, 0.80), (0.22, 0.22, 0.52)),
    "rleg": ((0.28, 0.30, 0.62), (0.40, 0.42, 0.76), (0.20, 0.21, 0.45)),
    "rshoe": ((0.16, 0.16, 0.30), (0.20, 0.20, 0.38), (0.12, 0.12, 0.22)),
    "lleg": ((0.52, 0.48, 0.40), (0.64, 0.60, 0.50), (0.38, 0.35, 0.29)),
    "lshoe": ((0.30, 0.27, 0.22), (0.36, 0.33, 0.27), (0.22, 0.20, 0.16)),
}
HAIR = (0.33, 0.22, 0.14)


class Tri:
    __slots__ = ("p", "uv", "tex", "color", "shade", "n")

    def __init__(self, p, uv, tex, color, shade=True, flip=False):
        self.p, self.uv, self.tex, self.color, self.shade = p, uv, tex, color, shade
        a, b = p[1] - p[0], p[2] - p[0]
        n = np.array([a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]])
        nn = math.sqrt(n @ n)
        self.n = None if nn < 1e-12 else (-n if flip else n) / nn  # outward normal (FACE_VERTS winding)


def box_faces(lo, hi, role):
    side, front, back = COLORS[role]
    out = []
    for face, sels in pim.FACE_VERTS.items():
        pts = [[hi[a] if s[a] else lo[a] for a in range(3)] for s in sels]
        col = front if face == "north" else back if face == "south" else side
        if role == "head" and face in ("up", "south"):
            col = HAIR
        out.append((np.array(pts, float), col))
    if role == "head":  # eyes on the face (model north, z = -4), the player's right eye at -x
        for x0 in (-3.0, 1.0):
            z = lo[2] - 0.05
            out.append((np.array([[x0, -4, z], [x0, -3, z], [x0 + 2, -3, z], [x0 + 2, -4, z]], float), (0.1, 0.1, 0.15)))
        z = lo[2] - 0.05
        out.append((np.array([[-1, -2, z], [-1, -1.4, z], [1, -1.4, z], [1, -2, z]], float), (0.55, 0.25, 0.22)))
    return out


def mat_t(x, y, z):
    return pim.affine(t=(x, y, z))


def mat_s(x, y, z):
    return pim.affine(np.diag([x, y, z]))


def mat_r(axis, rad):
    return pim.affine(pim.axis_rot(axis, math.degrees(rad)))


def mat_zyx(z, y, x):
    return mat_r("z", z) @ mat_r("y", y) @ mat_r("x", x)


def vanilla_rot(bone, pitch_deg):
    if bone == "head":
        return [math.radians(pitch_deg), 0.0, 0.0]
    if bone == "right_arm":
        return [-math.pi / 10, 0.0, 0.0]  # ArmPose.ITEM at rest (idle bob ignored)
    return [0.0, 0.0, 0.0]


def _fill(vals, default):
    return [default[i] if v is None else v for i, v in enumerate(vals)]


class Rig:
    """Posed figure + item at one tick: world-space triangles and measurement points."""

    def __init__(self, anim, tick, item, geo, markers, pitch_deg=0.0, slim=False):
        self.tick = tick
        parts = dict(PARTS, **SLIM_ARMS) if slim else PARTS
        hand_shift = 0.5 if slim else 0.0
        bpos = _fill(anim.sample("body", "position", tick), [0, 0, 0])
        brot = _fill(anim.sample("body", "rotation", tick), [0, 0, 0])
        bscl = _fill(anim.sample("body", "scale", tick), [1, 1, 1])
        root = (mat_r("y", math.pi)  # Ry(180 - bodyYaw), bodyYaw 0
                @ mat_s(*bscl) @ mat_t(-bpos[0] / 16, bpos[1] / 16 + 0.75, bpos[2] / 16)
                @ mat_zyx(brot[2], -brot[1], -brot[0]) @ mat_t(0, -0.75, 0)
                @ mat_s(-1, -1, 1) @ mat_s(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE) @ mat_t(0, -1.501, 0))
        self.root = root
        self.parts = {}
        self.tris = []
        self.values = {"body": {"rot_deg": [math.degrees(v) for v in brot], "pos": bpos}}
        for name, (init, van, boxes) in parts.items():
            rot = _fill(anim.sample(name, "rotation", tick), vanilla_rot(name, pitch_deg))
            pos = anim.sample(name, "position", tick)
            scl = _fill(anim.sample(name, "scale", tick), [1, 1, 1])
            px = [van[0] if pos[0] is None else pos[0] + init[0],
                  van[1] if pos[1] is None else -pos[1] + init[1],
                  van[2] if pos[2] is None else pos[2] + init[2]]
            M = root @ mat_t(px[0] / 16, px[1] / 16, px[2] / 16) @ mat_zyx(rot[2], rot[1], rot[0])
            self.parts[name] = (M, scl, px, rot)
            self.values[name] = {"rot_deg": [round(math.degrees(v), 2) for v in rot]}
            Mr = M @ mat_s(*scl) @ mat_s(PX, PX, PX)
            for lo, hi, role in boxes:
                for pts, col in box_faces(lo, hi, role):
                    self._add_quad(_apply(Mr, pts), None, None, col, flip=np.linalg.det(Mr[:3, :3]) < 0)
        # item: PlayerModelMixin.translateToHand + ItemInHandLayer(+Mixin)
        Marm, ascl, apx, arot = self.parts["right_arm"]
        hand = (root @ mat_t((apx[0] + hand_shift) / 16, apx[1] / 16, apx[2] / 16) @ mat_zyx(arot[2], arot[1], arot[0])
                @ mat_t(0, (ascl[1] - 1) * 0.609375, (ascl[2] - 1) * 0.0625))
        ipos = _fill(anim.sample("right_item", "position", tick), [0, 0, 0])
        irot = _fill(anim.sample("right_item", "rotation", tick), [0, 0, 0])
        iscl = _fill(anim.sample("right_item", "scale", tick), [1, 1, 1])
        self.values["right_item"] = {"rot_deg": [round(math.degrees(v), 2) for v in irot], "pos": ipos}
        item_m = (hand @ mat_t(ipos[0] / 16, -ipos[1] / 16, ipos[2] / 16)
                  @ mat_r("x", -math.pi / 2) @ mat_r("y", math.pi) @ mat_t(1 / 16, 0.125, -0.625)
                  @ mat_r("z", -irot[1]) @ mat_r("y", -irot[2]) @ mat_r("x", -irot[0]) @ mat_s(*iscl))
        self.item_m = None
        if item is not None:
            v = item.variant("thirdperson_righthand")
            self.item_m = item_m @ pim.display_matrix(v.display("thirdperson_righthand"))
            flip = np.linalg.det(self.item_m[:3, :3]) < 0
            for q in v.quads:
                self._add_quad(_apply(self.item_m, q.pos), q.uv, q.tex, q.color, q.shade, flip)
        # measurement points (world)
        Mr_arm = Marm @ mat_s(PX, PX, PX)
        Ml_arm = self.parts["left_arm"][0] @ mat_s(PX, PX, PX)
        self.pts = {
            "right_fist": _apply(Mr_arm, [[-1 + hand_shift, 8, 0]])[0],
            "left_fist": _apply(Ml_arm, [[1 - hand_shift, 8, 0]])[0],
            "hip": _apply(root @ mat_s(PX, PX, PX), [[0, 12, 0]])[0],
        }
        feet, soles = {}, {}
        for leg in ("right_leg", "left_leg"):
            M, scl = self.parts[leg][:2]
            corners = [[x, 12, z] for x in (-2, 2) for z in (-2, 2)] + [[0, 12, 0]]
            ys = _apply(M @ mat_s(*scl) @ mat_s(PX, PX, PX), corners)[:, 1]
            feet[leg], soles[leg] = float(ys[:4].min()), float(ys[4])
        self.feet, self.soles = feet, soles
        self.marks = {}
        self.axis = None
        if self.item_m is not None:
            if geo and "grip_center" in geo:
                self.marks["grip_center"] = _apply(self.item_m, [geo["grip_center"]])[0]
            for i, (label, p) in enumerate(markers):
                self.marks[label] = _apply(self.item_m, [p])[0]
            cx, cz = 8.0, 8.0
            if geo and "axes" in geo:
                cx, cz = geo["axes"].get("center_x", 8.0), geo["axes"].get("center_z", 8.0)
            a = _apply(self.item_m, [[cx, 0.0, cz], [cx, 16.0, cz]])
            self.axis = (a[0], (a[1] - a[0]) / 16.0)  # origin at model y = 0, direction per model px

    def _add_quad(self, P, uv, tex, color, shade=True, flip=False):
        for tri in ((0, 1, 2), (0, 2, 3)):
            self.tris.append(Tri(P[list(tri)], [uv[i] for i in tri] if uv else None, tex, color, shade, flip))

    def measure(self):
        out = {"tick": self.tick,
               "feet_y_px": {k: round(v / PLAYER_PX, 2) for k, v in self.feet.items()},
               "sole_centre_y_px": {k: round(v / PLAYER_PX, 2) for k, v in self.soles.items()},
               "hip_y_blocks": round(float(self.pts["hip"][1]), 3)}
        if self.item_m is not None and "grip_center" in self.marks:
            out["grip_to_right_fist_px"] = round(float(np.linalg.norm(self.marks["grip_center"] - self.pts["right_fist"]) / PLAYER_PX), 2)
        if self.axis is not None:
            o, d = self.axis
            lf = self.pts["left_fist"]
            s = float((lf - o) @ d / (d @ d))  # model-px coordinate along the axis
            foot = o + s * d
            out["left_fist_to_axis_px"] = round(float(np.linalg.norm(lf - foot)) / PLAYER_PX, 2)
            out["left_fist_axis_y_model_px"] = round(s, 2)
            self.axis_foot = foot
            for k, p in self.marks.items():
                if k != "grip_center":
                    out[f"left_fist_to_{k}_px"] = round(float(np.linalg.norm(lf - p)) / PLAYER_PX, 2)
        return out


def _apply(M, pts):
    P = np.asarray(pts, float)
    return P @ M[:3, :3].T + M[:3, 3]


# ------------------------------------------------------------------------------------- cameras
class View:
    """pos/forward in world; persp = vertical FOV in degrees or None for orthographic."""

    def __init__(self, name, pos, forward, up=(0, 1, 0), fov=None, pitch=0.0, title=""):
        self.name, self.title, self.fov, self.pitch = name, title, fov, pitch
        self.pos = np.asarray(pos, float)
        f = np.asarray(forward, float)
        self.f = f / np.linalg.norm(f)
        r = np.cross(self.f, np.asarray(up, float))
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)

    def project(self, P):
        """(N,3) world -> (N,3): plane x, plane y (tan units or blocks), raster depth key (bigger = nearer)."""
        Q = np.atleast_2d(P) - self.pos
        d = Q @ self.f
        x, y = Q @ self.r, Q @ self.u
        if self.fov:
            d = np.maximum(d, 1e-4)
            return np.c_[x / d, y / d, 1.0 / d]
        return np.c_[x, y, d * -1.0]


def mc_forward(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])


def f5_camera(yaw, pitch, wall_axis_z, max_zoom=4.0):
    """Camera.setup + getMaxZoom against the capture's barrier wall (plane z = wall_axis_z, eye at z 0)."""
    eye = np.array([0.0, EYE_HEIGHT, 0.0])
    f = mc_forward(yaw, pitch)
    zoom = max_zoom
    for i in range(8):
        o = np.array([((i & 1) * 2 - 1) * 0.1, ((i >> 1 & 1) * 2 - 1) * 0.1, ((i >> 2 & 1) * 2 - 1) * 0.1])
        start = eye + o
        dz = -f[2] * max_zoom
        if abs(dz) < 1e-9:
            continue
        t = (wall_axis_z - start[2]) / dz
        if 0.0 <= t <= 1.0:
            hit = start - f * max_zoom * t
            dist = float(np.linalg.norm(hit - eye))
            if dist < zoom:
                zoom = dist
    return eye - f * zoom, f, zoom


def make_view(name, cap):
    if name == "back":
        pos, f, zoom = f5_camera(0.0, cap["back_pitch"], -cap["back_wall"])
        return View(name, pos, f, fov=cap["fov"], pitch=cap["back_pitch"],
                    title=f"F5 back · pitch {cap['back_pitch']:g} · cam {zoom:.2f} from eye")
    if name == "front":
        pos, f, zoom = f5_camera(180.0, -cap["front_pitch"], cap["front_wall"])
        return View(name, pos, f, fov=cap["fov"], pitch=cap["front_pitch"],
                    title=f"F5 front · pitch {cap['front_pitch']:g} · cam {zoom:.2f} from eye")
    if name == "side":
        return View(name, (-20.0, 1.0, 0.0), (1, 0, 0), title="side (ortho, from the player's right; facing → right)")
    if name == "top":
        return View(name, (0.0, 20.0, 0.0), (0, -1, 0), up=(0, 0, 1),
                    title="top (ortho; facing ↑, player's right →)")
    raise SystemExit(f"ERROR: unknown view {name}; choose from back, front, side, top")


# ------------------------------------------------------------------------------------- raster
LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
SKY = np.array([0.66, 0.78, 0.95], np.float32)


class Window:
    """Plane-coordinate window -> pixels."""

    def __init__(self, cx, cy, k, W, H):
        self.cx, self.cy, self.k, self.W, self.H = cx, cy, k, W, H

    def px(self, plane):
        return np.c_[(plane[:, 0] - self.cx) * self.k + self.W / 2, self.H / 2 - (plane[:, 1] - self.cy) * self.k]


def background(view, win):
    H, W = win.H, win.W
    img = np.empty((H, W, 3), np.float32)
    zb = np.full((H, W), -np.inf, np.float32)
    xs = (np.arange(W) + 0.5 - W / 2) / win.k + win.cx
    ys = (H / 2 - (np.arange(H) + 0.5)) / win.k + win.cy
    X, Y = np.meshgrid(xs, ys)
    if view.fov:
        D = view.f[None, None, :] + X[..., None] * view.r + Y[..., None] * view.u
        with np.errstate(divide="ignore", invalid="ignore"):
            t = -view.pos[1] / D[..., 1]
        ground = (t > 0) & np.isfinite(t)
        img[:] = SKY
        gx = view.pos[0] + t * D[..., 0]
        gz = view.pos[2] + t * D[..., 2]
        chk = ((np.floor(gx * 2) + np.floor(gz * 2)) % 2 == 0)
        col = np.where(chk[..., None], np.array([0.47, 0.60, 0.36], np.float32), np.array([0.42, 0.55, 0.32], np.float32))
        line = (np.abs(gx) < 0.012 * np.maximum(t, 1)) | (np.abs(gz) < 0.012 * np.maximum(t, 1))
        col = np.where(line[..., None], np.array([0.25, 0.32, 0.20], np.float32), col)
        img[ground] = col[ground]
        zb[ground] = (1.0 / t[ground]).astype(np.float32)
    else:
        grad = np.linspace(1.0, 0.9, H, dtype=np.float32)[:, None, None]
        img[:] = np.array([0.84, 0.86, 0.89], np.float32) * grad
        if view.name == "top":
            g = (np.abs(X * 2 - np.round(X * 2)) < 0.02) | (np.abs(Y * 2 - np.round(Y * 2)) < 0.02)
            img[g] = np.array([0.72, 0.74, 0.78], np.float32)
            ax = (np.abs(X) < 0.012) | (np.abs(Y) < 0.012)
            img[ax] = np.array([0.55, 0.58, 0.62], np.float32)
        else:
            below = Y < 0
            img[below] = np.array([0.66, 0.72, 0.60], np.float32)
    return img, zb


_BG_CACHE = {}
NEAR = 0.05  # GameRenderer near plane (blocks)


def clip_near(view, P, uv):
    """Sutherland-Hodgman clip of one triangle against the near plane (perspective only)."""
    if not view.fov:
        return [(P, uv)]
    d = (P - view.pos) @ view.f
    if (d >= NEAR).all():
        return [(P, uv)]
    if (d < NEAR).all():
        return []
    U = np.asarray(uv, float) if uv is not None else np.zeros((3, 2))
    pts, uvs = [], []
    for i in range(3):
        j = (i + 1) % 3
        if d[i] >= NEAR:
            pts.append(P[i])
            uvs.append(U[i])
        if (d[i] >= NEAR) != (d[j] >= NEAR):
            a = (NEAR - d[i]) / (d[j] - d[i])
            pts.append(P[i] + a * (P[j] - P[i]))
            uvs.append(U[i] + a * (U[j] - U[i]))
    out = []
    for k in range(1, len(pts) - 1):
        tri_uv = [tuple(uvs[0]), tuple(uvs[k]), tuple(uvs[k + 1])] if uv is not None else None
        out.append((np.array([pts[0], pts[k], pts[k + 1]]), tri_uv))
    return out


def render_cell(rig, view, win, ss=2, cull=True):
    W, H = win.W * ss, win.H * ss
    win2 = Window(win.cx, win.cy, win.k * ss, W, H)
    key = (id(view), win.cx, win.cy, win.k, W, H)
    if key not in _BG_CACHE:
        _BG_CACHE.clear()
        _BG_CACHE[key] = background(view, win2)
    img, zb = (a.copy() for a in _BG_CACHE[key])
    for t in rig.tris:
        n = t.n
        if n is None:
            continue
        to_cam = (view.pos - t.p[0]) if view.fov else -view.f
        if n @ to_cam < 0:
            if cull:
                continue
            n = -n
        sh = min(1.0, 0.4 + 0.6 * (max(0.0, n @ LIGHT0) + max(0.0, n @ LIGHT1))) if t.shade else 1.0
        for P, uv in clip_near(view, t.p, t.uv):
            pr = view.project(P)
            S = np.c_[win2.px(pr[:, :2]), pr[:, 2]]
            pim.raster_tri(img, zb, S, uv, t.tex, t.color, sh)
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).resize((win.W, win.H), Image.LANCZOS)
    if not view.fov:
        d = ImageDraw.Draw(im)
        if view.name == "side":
            y0 = win.px(np.array([[0.0, 0.0]]))[0][1]
            d.line((0, y0, win.W, y0), fill=(60, 80, 50), width=1)
    return im


def to_px(view, win, P):
    return win.px(view.project(np.atleast_2d(P))[:, :2])


def draw_overlays(im, rig, view, win, show_axis):
    d = ImageDraw.Draw(im)

    def ring(p, col, r=4, fill=None):
        x, y = to_px(view, win, p)[0]
        d.ellipse((x - r, y - r, x + r, y + r), outline=col, width=2, fill=fill)
        return x, y

    if show_axis and rig.axis is not None and hasattr(rig, "axis_foot"):
        a = to_px(view, win, rig.pts["left_fist"])[0]
        b = to_px(view, win, rig.axis_foot)[0]
        d.line((a[0], a[1], b[0], b[1]), fill=(250, 210, 40), width=2)
    for k, p in rig.marks.items():
        ring(p, (220, 30, 30) if k == "grip_center" else (230, 40, 220), 4 if k == "grip_center" else 5)
    ring(rig.pts["left_fist"], (30, 60, 220), 3, fill=(30, 60, 220))


# ------------------------------------------------------------------------------------- framing
FIT_MIN_DEPTH = 0.6  # perspective fit ignores geometry closer than this to the camera (it fills the frame)


def fit_window(view, rigs, cw, ch, margin=0.07):
    pts = [np.vstack([t.p for t in r.tris]) for r in rigs]
    P = np.vstack(pts + [np.array([[x, 0.0, z] for x in (-0.4, 0.4) for z in (-0.4, 0.4)])])
    if view.fov:
        P = P[(P - view.pos) @ view.f > FIT_MIN_DEPTH]
    pr = view.project(P)[:, :2]
    lo, hi = pr.min(0), pr.max(0)
    span = np.maximum(hi - lo, 1e-6)
    k = min(cw * (1 - 2 * margin) / span[0], ch * (1 - 2 * margin) / span[1])
    c = (lo + hi) / 2
    return Window(c[0], c[1], k, cw, ch)


def game_window(view, cw, cap):
    ch = round(cw * cap["height"] / cap["width"])
    k = (ch / 2) / math.tan(math.radians(cap["fov"]) / 2)
    return Window(0.0, 0.0, k, cw, ch)


# ------------------------------------------------------------------------------------- main
def parse_ticks(s):
    return [float(x) for x in s.split(",") if x.strip()]


def fmt_tick(t):
    return f"{t:g}"


def load_geometry(assets, arg, item_ref):
    if arg == "none" or not item_ref:
        return None, None
    if arg:
        return pim.checked_geometry(json.loads(Path(arg).read_text(encoding="utf-8")), arg)
    ns, path = pim.Assets.split(item_ref) if ":" in item_ref else ("minecraft", item_ref)
    rel = f"assets/{ns}/combat/{Path(path).name}_geometry.json"
    data = assets.read(rel)
    return pim.checked_geometry(json.loads(data), rel) if data else (None, None)


def parse_markers(specs, geo):
    out = []
    for s in specs:
        if s in (geo or {}) and isinstance(geo[s], list):
            out.append((s, [float(v) for v in geo[s]]))
            continue
        try:
            v = [float(x) for x in s.split(",")]
        except ValueError:
            raise SystemExit(f"ERROR: marker {s!r}: give x,y,z in item-model px or a point name from the geometry contract")
        if len(v) != 3:
            raise SystemExit(f"ERROR: marker {s!r} needs 3 values")
        out.append((f"marker{len(out) + 1}", v))
    return out


def build(a):
    t0 = time.time()
    anim = PalAnimation(a.animations, a.animation)
    ticks = sorted(anim.key_times) if a.all_keys else parse_ticks(a.ticks)
    if not ticks:
        raise SystemExit("ERROR: no ticks")
    assets = pim.Assets(a.root, None if a.no_vanilla else a.vanilla_jar)
    item = None
    if a.item and a.item != "none":
        item = pim.ItemModel(assets, a.item)
    geo, geo_src = load_geometry(assets, a.geometry, a.item if a.item != "none" else None)
    markers = parse_markers(a.marker, geo)
    cap = {"fov": a.fov, "back_pitch": a.back_pitch, "front_pitch": a.front_pitch, "back_wall": a.wall,
           "front_wall": a.wall, "width": 960, "height": 540}
    views = [v for v in a.views.split(",") if v]
    cw, ch = (int(x) for x in a.cell.lower().split("x"))
    rows, row_labels, report = [], [], {"animation": anim.id, "length_ticks": anim.length, "ticks": ticks,
                                        "item": a.item, "geometry": geo_src, "views": {}, "measure": []}
    measured = False
    notes = []
    for vname in views:
        view = make_view(vname, cap)
        rigs = [Rig(anim, t, item, geo, markers, pitch_deg=view.pitch, slim=a.arms == "slim") for t in ticks]
        if view.fov and item is not None:
            near = min(float((np.vstack([t.p for t in r.tris]) - view.pos).dot(view.f).min()) for r in rigs)
            if near < FIT_MIN_DEPTH:
                notes.append(f"{vname}: the item comes within {near:.2f} blocks of the F5 camera (clipped at the "
                             f"0.05 near plane like the game; ignored by the fit)")
        if a.frame == "game" and view.fov:
            win = game_window(view, cw, cap)
        else:
            win = fit_window(view, rigs, cw, ch)
        cells = []
        for rig in rigs:
            m = rig.measure()
            im = render_cell(rig, view, win)
            draw_overlays(im, rig, view, win, show_axis=bool(markers) or a.show_axis)
            if not measured:
                report["measure"].append(m)
            lines = [f"t {fmt_tick(rig.tick)} · {vname}",
                     f"soles R {m['sole_centre_y_px']['right_leg']:+.1f} L {m['sole_centre_y_px']['left_leg']:+.1f} px"
                     f" (low {m['feet_y_px']['right_leg']:+.1f}/{m['feet_y_px']['left_leg']:+.1f})"]
            if "left_fist_to_axis_px" in m:
                extra = [f"{k[13:-3]} {v:.1f}" for k, v in m.items()
                         if k.startswith("left_fist_to_") and k != "left_fist_to_axis_px"]
                lines.append(f"L fist→axis {m['left_fist_to_axis_px']:.1f} px @y{m['left_fist_axis_y_model_px']:.1f}"
                             + (" · →" + ", ".join(extra) if extra else ""))
            cells.append(sheets.labelled(im, lines, win.W))
        measured = True
        rows.append(cells)
        row_labels.append(vname)
        report["views"][vname] = {"title": view.title, "camera": [round(float(x), 3) for x in view.pos],
                                  "px_per_unit": round(win.k, 2)}
    def short(x):
        return Path(x).name if x and (os.sep in str(x) or str(x).endswith(".json")) else x

    title = [f"PAL pose · {anim.id} · {Path(a.animations).name} · length {anim.length:g} ticks"
             + (f" · {a.arms} arms" if a.arms != "wide" else ""),
             f"item {short(a.item)} · geometry {short(geo_src) or 'none'} · views: " +
             " | ".join(f"{v}: {report['views'][v]['title']}" for v in views),
             "red ring = grip_center · magenta = marker · blue dot = left fist centre · yellow = left fist → weapon axis"
             " · soles = sole-centre height, low = lowest sole corner (player px, 0 = ground)"]
    msgs = list(dict.fromkeys(anim.warnings)) + (item.messages() if item else []) + notes
    title += msgs[:4]
    sheet = sheets.grid(rows, title)
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    report["warnings"] = msgs
    out.with_suffix(".json").write_text(json.dumps(report, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"wrote {out} ({sheet.width}x{sheet.height}) + {out.with_suffix('.json').name} in {time.time() - t0:.2f}s")
    for msg in msgs:
        print("  " + msg)
    for m in report["measure"]:
        line = (f"  t {fmt_tick(m['tick']):>6}: sole centre R {m['sole_centre_y_px']['right_leg']:+.2f}"
                f" L {m['sole_centre_y_px']['left_leg']:+.2f} px (lowest corner {m['feet_y_px']['right_leg']:+.2f}"
                f"/{m['feet_y_px']['left_leg']:+.2f})")
        if "grip_to_right_fist_px" in m:
            line += f" · grip→R fist {m['grip_to_right_fist_px']:.2f} px"
        if "left_fist_to_axis_px" in m:
            line += f" · L fist→axis {m['left_fist_to_axis_px']:.2f} px at axis y {m['left_fist_axis_y_model_px']:.1f}"
        for k, v in m.items():
            if k.startswith("left_fist_to_") and k != "left_fist_to_axis_px":
                line += f" · L fist→{k[13:-3]} {v:.2f} px"
        print(line)
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(prog="python3 -m tools.combat_preview pose", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--animations", required=True, help="PAL player_animations JSON file")
    ap.add_argument("--animation", required=True, help="animation key (namespace optional)")
    ap.add_argument("--ticks", default="0", help="comma list, fractional allowed (e.g. 0,3,4.9)")
    ap.add_argument("--all-keys", action="store_true", help="use every keyframe time of the animation")
    ap.add_argument("--item", default="myvillage:item/qingfeng_sword", help="item model id or .json path; 'none'")
    ap.add_argument("--root", action="append", default=[],
                    help="resources root (repeatable, first wins; default src/main/resources of this repository)")
    ap.add_argument("--vanilla-jar", default=str(pim.VANILLA_JAR))
    ap.add_argument("--no-vanilla", action="store_true")
    ap.add_argument("--geometry", help="geometry contract JSON (default: assets/<ns>/combat/<item>_geometry.json "
                                       "in the roots); 'none' disables")
    ap.add_argument("--marker", action="append", default=[],
                    help="x,y,z in item-model px, or a point name of the contract (e.g. off_hand_grip_center); "
                         "repeatable. Prints the left-fist distance to it and to the weapon axis per tick")
    ap.add_argument("--show-axis", action="store_true", help="draw left fist → weapon axis even without --marker")
    ap.add_argument("--views", default="back,front,side,top")
    ap.add_argument("--frame", choices=("fit", "game"), default="fit",
                    help="back/front: crop to figure+item (fit) or the full 960x540 game frame (game)")
    ap.add_argument("--cell", default="360x360", help="cell size WxH (game frame: width only, 16:9)")
    ap.add_argument("--arms", choices=("wide", "slim"), default="wide",
                    help="player model arms (the after-data-infra capture skin is slim)")
    ap.add_argument("--fov", type=float, default=70.0)
    ap.add_argument("--back-pitch", type=float, default=30.0)
    ap.add_argument("--front-pitch", type=float, default=15.0)
    ap.add_argument("--wall", type=float, default=2.4, help="barrier wall distance from the eye (blocks)")
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)
    if not a.root:
        a.root = [str(DEFAULT_ROOT)]
    note_missing_jar(a.vanilla_jar, a.no_vanilla)
    return build(a)
