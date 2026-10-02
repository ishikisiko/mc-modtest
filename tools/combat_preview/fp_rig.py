"""Offline first-person preview of a combat weapon's first-person rig (numpy rasteriser; light).

    python3 -m tools.combat_preview fp --weapon myvillage:qingfeng_sword [--rig <rig.json>] \
        --move 2 --key-ticks --out out/preview/combat_preview/fp.png

Renders a sheet (rows = moves, columns = ticks) of 960x540-proportioned first-person frames: the
weapon's item model and the skin arm (upper arm, forearm, fist) where the mod draws them, plus the
cut trail / thrust streak, over a flat sky/ground backdrop with a crosshair and the HUD outline.
Prints per tick the screen position (960x540 game pixels) of the grip centre, head tip and
off-hand grip point, the frame coverage of weapon and arm, and whether tip and fist are on screen.

Tick selection: --ticks 0,3.3,4.9 | --all-keys (every key of the rig's move) | --key-ticks (the five
capture ticks of tools/combat_capture: idle 0, strike_start = strike[0], contact, strike_end =
strike[1], recovery = the last key before the final neutral key).  --move takes 1-based move
numbers (as the capture's m<n>), a comma list, or 'all'.

Transform chain (ported from client/combat in the mod and vanilla 1.21.1 / NeoForge 21.1):

  screen  = P(fov 70, 960:540, near 0.05) * H                    GameRenderer.renderItemInHand: the
            hand pass uses its own projection with FOV 70 (getFov(.., false)) whatever the game
            FOV; H = inverse camera rotation on the pose stack, re-applied by the model-view
            stack (net identity); bobHurt/bobView and the head-turn arm sway
            (ItemInHandRenderer.renderHandsWithItems, (xRot - xBob) * 0.1) are zero for a still,
            unhurt player.  View space: camera at the origin looking down -Z, +X right, +Y up.
  grip G  = T(side*(shoulder.x + x), shoulder.y + y - equip*0.6, shoulder.z + z)
            * Rz(side*plane) * Ry(side*sweep) * T(0, 0, -reach)
            * Ry(side*lead) * Rx(lift) * Ry(side*twist)              FirstPersonWeaponTransform.applyGripFrame
  item    = G * S(weapon_scale) * T(0.5 - grip/16) * D^-1            FirstPersonWeaponTransform.itemToGrip
            * D * T(-0.5) * px/16                                     ItemRenderer (D = the model's own
            firstperson display transform via BakedModel.applyTransform; it cancels exactly)
          = G * S(weapon_scale) * T(-grip_center/16) * px/16
  arm     = FirstPersonArmIk.solve (grip_diagonal, grip_roll, elbow swivel, two-bone solve with
            reach clamp 0.30..0.97, wrist-limit lag bisection) with FirstPersonArmLag.offset at the
            displayed tick, drawn as FirstPersonArmRenderer/FirstPersonArmModel boxes (skin layer
            entitySolid + sleeve layer inflated 0.25 px).
  off arm = FirstPersonArmIk.solveOffHand (only with rig.off_hand, off-hand slot empty): the main arm's
            solve mirrored, fist on the shaft at off_hand_grip_center + off_hand_slide (slid along the
            shaft into reach when needed), auto roll + off_hand_roll, off_hand_elbow swivel, no lag,
            off_hand_hold < 1 blends toward a rest below the view; drawn with the off arm's skin/sleeve.
  trail   = FirstPersonWeaponTrail: thrust -> one streak (alpha 0.8 fading over 3 ticks from
            strike start), cut -> 24-segment ribbon over the last 1.2 ticks of the strike window,
            fading 2.4 ticks after it.  Drawn over the contract's trail span (WeaponGeometry.trailBase/
            trailTip): the optional "trail": {"base", "tip"} block, else head_base..head_tip.

What is shown is what the capture tool's freeze probe shows (FirstPersonWeaponAnimator.probe):
the rig sampled at the tick, no breathing, no hit-stop shake, no chain blend, equip progress 0,
arm lag applied (it is a pure function of move and tick).  --no-lag drops the lag.

Validated 2026-10-02 against in-game freeze-probe stills from tools/combat_capture: 25 Qingfeng
stills (5 moves x 5 key ticks) agree to 0.0003-0.005 silhouette XOR/union with 0 px tip, fist and
grip offset, and 25 Lingxiao spear stills to at most 0.005; the residual pixels are keying noise
(horizon row, dark handle pixels within the threshold of the grass), not geometry.  With --no-lag
the Qingfeng arm misses by up to 0.127 XOR (m5 contact), so the freeze probe does draw the lag.
The trail is drawn but excluded from the metric.  Re-run: --compare-capture <capture dir> --out
<dir> (game | render | overlay | diff per still).

The solver port (rig parsing and sampling, grip frame, arm lag, main and off arm) is pinned to the
Java by the parity golden src/test/resources/first_person_preview_parity.json, which
FirstPersonPreviewParityTest (JUnit) and tools/tests/test_combat_preview_parity.py both check.
--off-hand-occupied draws the frame as with an item in the off hand (no off arm; the item is not drawn).
Not modelled: breathing, hit-stop shake, chain/blend-out cross-fades, resync slews, equip drop
animation, view bobbing / head-turn sway (non-still player), FOV-changing fluids, other HUD states.
"""
import argparse
import hashlib
import io
import json
import math
import sys
import time
from pathlib import Path

from .. import combat_data
from . import item_model as pim
from .env import DEFAULT_ROOT, note_missing_jar

np = pim.np
Image = pim.Image
ImageDraw = pim.ImageDraw
sheets = pim.sheets

FOV = 70.0
NEAR = 0.05
GAME_W, GAME_H = 960, 540
EQUIP_DROP = 0.60
DEFAULT_WEAPON_SCALE = 0.60
# FirstPersonSwing.OffHand defaults for the released off hand's rest (off arm frame, +x outward).
DEFAULT_REST_DIRECTION = (0.15, -1.0, -0.2)
DEFAULT_REST_REACH = 0.9
LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
# The capture HUD (GUI scale 2 at 960x540): hearts/food row and hotbar, bottom centre.
HUD_BOXES = ((296, 458, 664, 540),)

FAIL = []  # loud messages: things the rig asks for that the game (and this port) does not do


def loud(msg):
    FAIL.append(msg)
    print(f"!!! {msg}", file=sys.stderr)


# ============================================================================ mod data
class ModData:
    def __init__(self, roots):
        self.roots = [Path(r) for r in roots]

    def find(self, rel):
        for r in self.roots:
            p = r / rel
            if p.is_file():
                return p
        return None

    def json(self, rel, what):
        p = self.find(rel)
        if p is None:
            raise SystemExit(f"ERROR: {what} not found: {rel} (roots: {', '.join(map(str, self.roots))})")
        return json.loads(p.read_text(encoding="utf-8")), p


def split_id(ref, default_ns="minecraft"):
    return tuple(ref.split(":", 1)) if ":" in ref else (default_ns, ref)


def load_weapon(data, weapon_id):
    ns, path = split_id(weapon_id)
    weapon, _ = data.json(f"data/{ns}/combat/weapon/{path}.json", f"weapon {weapon_id}")
    sns, spath = split_id(weapon["style"])
    style, _ = data.json(f"data/{sns}/combat/style/{spath}.json", f"style {weapon['style']}")
    rns, rpath = split_id(weapon["first_person_rig"])
    gns, gpath = split_id(weapon["geometry"])
    ins, ipath = split_id(weapon["item"])
    return {
        "weapon": weapon, "style": style,
        "rig_rel": f"assets/{rns}/{rpath}", "geometry_rel": f"assets/{gns}/{gpath}",
        "model": f"{ins}:item/{ipath}",
    }


# ============================================================================ rig (FirstPersonSwing port)
OFF_POSE_FIELDS = ("off_hand_slide", "off_hand_roll", "off_hand_elbow", "off_hand_hold")
POSE_FIELDS = ("plane", "sweep", "reach", "lead", "lift", "twist", "x", "y", "z", "grip_roll", "elbow") + OFF_POSE_FIELDS
POSE_KEYS = {"plane", "sweep", "reach", "lead", "lift", "twist", "offset", "grip_roll", "elbow", *OFF_POSE_FIELDS}
KEY_KEYS = POSE_KEYS | {"tick", "ease", "pose"}
MOVE_KEYS = {"strike", "contact", "keys"}
RIG_KEYS = {"shoulder", "weapon_scale", "arm", "off_hand"}
OFF_HAND_KEYS = {"shoulder_offset", "grip_diagonal", "thickness", "upper_arm", "forearm", "rest_direction", "rest_reach"}
ARM_KEYS = {"shoulder_offset", "upper_arm", "forearm", "thickness", "grip_diagonal", "follow_through"}
TOP_KEYS = {"rig", "neutral", "moves"}
BACK_OVERSHOOT = 1.9


def ease(name, t):
    t = max(0.0, min(1.0, t))
    if name == "linear":
        return t
    if name == "in":
        return t * t
    if name == "out":
        return 1.0 - (1.0 - t) ** 2
    if name == "in_out":
        return t * t * (3.0 - 2.0 * t)
    if name == "in_cubic":
        return t ** 3
    if name == "out_cubic":
        return 1.0 - (1.0 - t) ** 3
    if name == "in_out_cubic":
        return 4.0 * t ** 3 if t < 0.5 else 1.0 - (-2.0 * t + 2.0) ** 3 * 0.5
    if name == "out_back":
        s = t - 1.0
        return 1.0 + (BACK_OVERSHOOT + 1.0) * s ** 3 + BACK_OVERSHOOT * s * s
    raise ValueError(name)


EASES = ("linear", "in", "out", "in_out", "in_cubic", "out_cubic", "in_out_cubic", "out_back")


class Pose(tuple):
    def __new__(cls, vals):
        return super().__new__(cls, tuple(float(v) for v in vals))

    def __getattr__(self, name):
        try:
            return self[POSE_FIELDS.index(name)]
        except ValueError:
            raise AttributeError(name)

    @staticmethod
    def lerp(a, b, p):
        if p <= 0.0:
            return a
        if p >= 1.0:
            return b
        return Pose([x + (y - x) * p for x, y in zip(a, b)])

    def describe(self):
        return (f"plane {self.plane:.1f} sweep {self.sweep:.1f} reach {self.reach:.3f} lead {self.lead:.1f} "
                f"lift {self.lift:.1f} twist {self.twist:.1f}",
                f"offset ({self.x:.3f}, {self.y:.3f}, {self.z:.3f}) grip_roll {self.grip_roll:.1f} elbow {self.elbow:.1f}")

    def describe_off(self):
        return (f"off hand slide {self.off_hand_slide:.1f} px roll {self.off_hand_roll:.1f} "
                f"elbow {self.off_hand_elbow:.1f} hold {self.off_hand_hold:.2f}")


ZERO = Pose([0.0] * 14 + [1.0])  # Pose.ZERO: off hand at its default point, holding the shaft


class RigError(Exception):
    pass


def _vec(arr, label, n=3):
    if not isinstance(arr, list) or len(arr) != n:
        raise RigError(f"{label} must have {n} numbers")
    return [float(v) for v in arr]


def _unknown(obj, allowed, where):
    for k in obj:
        if k not in allowed:
            loud(f"{where}: field '{k}' is not read by FirstPersonSwing (the game ignores it; nothing drawn for it)")


def parse_pose(obj, fallback, where):
    if obj is None:
        raise RigError("Missing pose object")
    off = _vec(obj["offset"], f"{where}.offset") if "offset" in obj else [fallback.x, fallback.y, fallback.z]
    vals = [float(obj.get(k, getattr(fallback, k))) for k in ("plane", "sweep", "reach", "lead", "lift", "twist")]
    vals += off + [float(obj.get("grip_roll", fallback.grip_roll)), float(obj.get("elbow", fallback.elbow))]
    hold = float(obj.get("off_hand_hold", fallback.off_hand_hold))
    if not 0.0 <= hold <= 1.0:
        raise RigError(f"{where}: off_hand_hold must be within 0..1")
    vals += [float(obj.get(k, getattr(fallback, k))) for k in OFF_POSE_FIELDS[:3]] + [hold]
    if not all(math.isfinite(v) for v in vals):
        raise RigError(f"{where}: swing pose values must be finite")
    return Pose(vals)


class Move:
    def __init__(self, mid, kind, total, active, keys, strike, contact):
        self.id, self.kind, self.total, self.active = mid, kind, total, active
        self.keys, self.strike, self.contact = keys, strike, contact
        self.short = mid.split(":", 1)[-1].split("_", 2)[-1] if "_" in mid else mid

    def sample(self, tick):
        b = max(0.0, min(float(self.total), tick))
        for i in range(1, len(self.keys)):
            t1, e1, p1 = self.keys[i]
            if b <= t1:
                t0, _, p0 = self.keys[i - 1]
                return Pose.lerp(p0, p1, ease(e1, (b - t0) / (t1 - t0)))
        return self.keys[-1][2]

    def capture_ticks(self):
        """tools/combat_capture key ticks: idle, strike_start, contact, strike_end, recovery."""
        return [("idle", 0.0), ("strike_start", self.strike[0]), ("contact", self.contact),
                ("strike_end", self.strike[1]), ("recovery", self.keys[-2][0])]


class Rig:
    def __init__(self, doc, style, where="rig", geo=None):
        _unknown(doc, TOP_KEYS, where)
        rj = doc.get("rig")
        if rj is None:
            raise RigError("First-person swing file has no rig")
        renamed = combat_data.rig_format_problems(doc)
        if renamed:
            raise RigError("; ".join(renamed))
        _unknown(rj, RIG_KEYS, f"{where}.rig")
        self.shoulder = _vec(rj.get("shoulder"), "rig.shoulder")
        self.weapon_scale = float(rj.get("weapon_scale", DEFAULT_WEAPON_SCALE))
        if not (0.2 <= self.weapon_scale <= 1.5):
            raise RigError("rig.weapon_scale must be within 0.2..1.5")
        aj = rj.get("arm", {})
        _unknown(aj, ARM_KEYS, f"{where}.rig.arm")
        self.shoulder_offset = _vec(aj["shoulder_offset"], "rig.arm.shoulder_offset") if "shoulder_offset" in aj \
            else [0.03, -0.02, 0.0]
        self.upper_arm = float(aj.get("upper_arm", 0.33))
        self.forearm = float(aj.get("forearm", 0.33))
        self.thickness = float(aj.get("thickness", 0.5))
        self.grip_diagonal = float(aj.get("grip_diagonal", 40.0))
        self.follow_through = float(aj.get("follow_through", 1.0))
        if not (0.1 <= self.upper_arm <= 0.6 and 0.1 <= self.forearm <= 0.6):
            raise RigError("rig.arm bone lengths must be within 0.1..0.6")
        if not (0.2 <= self.thickness <= 1.2):
            raise RigError("rig.arm.thickness must be within 0.2..1.2")
        if not (0.0 <= self.grip_diagonal <= 50.0):
            raise RigError("rig.arm.grip_diagonal must be within 0..50")
        if not (0.0 <= self.follow_through <= 2.0):
            raise RigError("rig.arm.follow_through must be within 0..2")
        self.off_hand = None  # FirstPersonSwing.OffHand
        if "off_hand" in rj:
            oj = rj["off_hand"]
            if not isinstance(oj, dict):
                raise RigError("rig.off_hand must be an object")
            if geo is None or geo.off_hand is None:
                raise RigError("rig.off_hand needs a weapon geometry with off_hand_grip_center")
            _unknown(oj, OFF_HAND_KEYS, f"{where}.rig.off_hand")
            rest = _vec(oj["rest_direction"], "rig.off_hand.rest_direction") if "rest_direction" in oj \
                else list(DEFAULT_REST_DIRECTION)
            self.off_hand = {
                "shoulder_offset": _vec(oj["shoulder_offset"], "rig.off_hand.shoulder_offset")
                if "shoulder_offset" in oj else list(self.shoulder_offset),
                "grip_diagonal": float(oj.get("grip_diagonal", self.grip_diagonal)),
                "thickness": float(oj.get("thickness", self.thickness)),
                "upper_arm": float(oj.get("upper_arm", self.upper_arm)),
                "forearm": float(oj.get("forearm", self.forearm)),
                "rest_reach": float(oj.get("rest_reach", DEFAULT_REST_REACH)),
            }
            if not 0.0 <= self.off_hand["grip_diagonal"] <= 50.0:
                raise RigError("rig.off_hand.grip_diagonal must be within 0..50")
            if not 0.2 <= self.off_hand["thickness"] <= 1.2:
                raise RigError("rig.off_hand.thickness must be within 0.2..1.2")
            if not (0.1 <= self.off_hand["upper_arm"] <= 0.6 and 0.1 <= self.off_hand["forearm"] <= 0.6):
                raise RigError("rig.off_hand bone lengths must be within 0.1..0.6")
            if not all(math.isfinite(v) for v in rest) or math.sqrt(sum(v * v for v in rest)) < 1e-3:
                raise RigError("rig.off_hand.rest_direction must be a finite, non-zero direction")
            if not MIN_REACH_FRACTION <= self.off_hand["rest_reach"] <= REACH_FRACTION:
                raise RigError(f"rig.off_hand.rest_reach must be within {MIN_REACH_FRACTION}..{REACH_FRACTION}")
            self.off_hand["rest_direction"] = _norm(np.array(rest, float))
        self.geo = geo
        nj = doc.get("neutral")
        _unknown(nj or {}, POSE_KEYS, f"{where}.neutral")
        self.neutral = parse_pose(nj, ZERO, "neutral")
        self._check_off_hand(self.neutral, "neutral")
        mj = doc.get("moves")
        if mj is None:
            raise RigError("First-person swing file has no moves")
        self.moves = []
        for d in style["moves"]:
            mid = d["id"]
            if mid not in mj:
                raise RigError(f"Missing first-person swing for {mid}")
            mv = self._move(d, mj[mid], f"{where}.moves.{mid}")
            for t, _, p in mv.keys:
                self._check_off_hand(p, f"{mid} key {t}")
            self.moves.append(mv)
        if len(mj) != len(self.moves):
            raise RigError("First-person swing file declares unknown moves: "
                           + ", ".join(k for k in mj if k not in {d['id'] for d in style['moves']}))

    def _check_off_hand(self, pose, where):
        if self.off_hand is None:
            return
        y = self.geo.off_hand[1] + pose.off_hand_slide
        if not self.geo.handle[0] <= y <= self.geo.handle[1]:
            raise RigError(f"{where} off_hand_slide moves the off hand off the handle")

    def _move(self, d, mj, where):
        _unknown(mj, MOVE_KEYS, where)
        mid, total = d["id"], int(d["total_ticks"])
        a0, a1 = d["active_ticks"]
        kj = mj.get("keys")
        if not isinstance(kj, list) or len(kj) < 3:
            raise RigError(f"{mid} needs at least three keys")
        keys, prev = [], self.neutral
        for i, k in enumerate(kj):
            _unknown(k, KEY_KEYS, f"{where}.keys[{i}]")
            tick = float(k["tick"])
            e = k.get("ease", "linear")
            if e not in EASES:
                raise RigError(f"Unknown swing ease: {e}")
            if "pose" in k and k["pose"] != "neutral":
                loud(f"{where}.keys[{i}]: pose '{k['pose']}' is not a named pose the game knows (only 'neutral'); "
                     "the key's own fields are used")
            pose = self.neutral if k.get("pose") == "neutral" else parse_pose(k, prev, f"{where}.keys[{i}]")
            if keys and tick <= keys[-1][0]:
                raise RigError(f"{mid} key ticks must increase")
            keys.append((tick, e, pose))
            prev = pose
        if keys[0][0] != 0.0 or keys[-1][0] != total:
            raise RigError(f"{mid} keys must span 0..{total} ticks")
        if keys[0][2] != self.neutral or keys[-1][2] != self.neutral:
            raise RigError(f"{mid} must start and end at the neutral hold")
        strike = _vec(mj.get("strike"), f"{mid}.strike", 2)
        if not (strike[0] < strike[1]) or strike[0] > a0 or strike[1] < a1 or strike[1] > total:
            raise RigError(f"{mid} strike window must cover active ticks {a0}..{a1}")
        contact = float(mj.get("contact", strike[0]))
        if not (strike[0] <= contact <= strike[1]):
            raise RigError(f"{mid} contact tick must lie inside the strike window")
        return Move(mid, d.get("kind", "cut"), total, (a0, a1), keys, strike, contact)


# ============================================================================ geometry contract (WeaponGeometry port)
class Geometry:
    def __init__(self, g):
        problems = combat_data.geometry_format_problems(g)
        if problems:
            raise RigError("Weapon geometry: " + "; ".join(problems))
        if g.get("units") != "model_pixels":
            raise RigError("Weapon geometry units must be model_pixels")
        ax = g.get("axes") or {}
        if ax.get("length") != "+y" or ax.get("flat_normal") != "x" or ax.get("edge") != "z":
            raise RigError("Weapon geometry axes must be length +y, flat_normal x, edge z")
        self.raw = g
        self.grip = np.array(_vec(g.get("grip_center"), "grip_center"))
        hy, cy, by = (_vec(g[k]["y"], f"{k}.y", 2) for k in ("handle", "collar", "butt"))
        for k in ("handle", "collar"):
            for f in ("half_width", "half_thickness"):
                if not float(g[k].get(f, 0)) > 0:
                    raise RigError(f"{k}.{f} must be positive")
        self.base = np.array(_vec(g.get("head_base"), "head_base"))
        self.tip = np.array(_vec(g.get("head_tip"), "head_tip"))
        if not (by[1] <= hy[0] + 1e-3 and hy[1] <= cy[0] + 1e-3 and cy[1] <= self.base[1] + 1e-3
                and self.base[1] < self.tip[1]):
            raise RigError("Weapon geometry must stack butt, handle, collar, head base and tip along +Y")
        if not (hy[0] < self.grip[1] < hy[1]):
            raise RigError("Weapon grip_center must lie on the handle")
        ax_ = (self.tip - self.base) / np.linalg.norm(self.tip - self.base)
        if ax_[1] < 0.999 or abs(self.base[0] - self.grip[0]) > 0.05 or abs(self.base[2] - self.grip[2]) > 0.05:
            raise RigError("Weapon head must run along +Y through the grip axis")
        self.handle = hy
        self.off_hand = None
        if "off_hand_grip_center" in g:
            oh = np.array(_vec(g["off_hand_grip_center"], "off_hand_grip_center"))
            if abs(oh[0] - self.grip[0]) > 0.05 or abs(oh[2] - self.grip[2]) > 0.05:
                raise RigError("Weapon off_hand_grip_center must lie on the handle axis")
            if not hy[0] < oh[1] < hy[1]:
                raise RigError("Weapon off_hand_grip_center must lie on the handle")
            if not oh[1] > self.grip[1]:
                raise RigError("Weapon off_hand_grip_center must lie ahead of grip_center")
            self.off_hand = oh
        self.overall = g.get("overall_y")
        # Optional trail span (WeaponGeometry: on the axis, base below tip, butt bottom..head tip).
        self.trail_base, self.trail_tip = self.base, self.tip
        if "trail" in g:
            tr = g["trail"]
            if not isinstance(tr, dict):
                raise RigError("Weapon geometry needs a trail object")
            tb = np.array(_vec(tr.get("base"), "trail.base"))
            tt = np.array(_vec(tr.get("tip"), "trail.tip"))
            for q in (tb, tt):
                if abs(q[0] - self.grip[0]) > 0.05 or abs(q[2] - self.grip[2]) > 0.05:
                    raise RigError("Weapon trail must lie on the weapon axis")
            if not tb[1] < tt[1]:
                raise RigError("Weapon trail base must lie below its tip")
            if tb[1] < by[0] - 1e-3 or tt[1] > self.tip[1] + 1e-3:
                raise RigError("Weapon trail must lie on the weapon, from the butt to the head tip")
            self.trail_base, self.trail_tip = tb, tt

    def to_grip(self, p, scale):
        return (np.asarray(p, float) - self.grip) * (scale / 16.0)


# ============================================================================ transforms
def R(axis, deg):
    return pim.axis_rot(axis, deg)


def A(M=None, t=None):
    return pim.affine(M, t)


def grip_frame(side, rig, pose, equip=0.0):
    s = rig.shoulder
    return (A(t=(side * (s[0] + pose.x), s[1] + pose.y - equip * EQUIP_DROP, s[2] + pose.z))
            @ A(R("z", side * pose.plane)) @ A(R("y", side * pose.sweep)) @ A(t=(0, 0, -pose.reach))
            @ A(R("y", side * pose.lead)) @ A(R("x", pose.lift)) @ A(R("y", side * pose.twist)))


def item_matrix(G, geo, scale):
    return G @ A(np.eye(3) * scale) @ A(t=-geo.grip / 16.0) @ A(np.eye(3) / 16.0)


def pt(M, p):
    return M[:3, :3] @ np.asarray(p, float) + M[:3, 3]


def weapon_point(side, rig, geo, pose, model_px):
    return pt(grip_frame(side, rig, pose), geo.to_grip(model_px, rig.weapon_scale))


# ============================================================================ arm lag (FirstPersonArmLag port)
LAG_OMEGA, LAG_DAMPING, LAG_GAIN, LAG_CAP, LAG_DT, LAG_N, LAG_END_FADE = 2.1, 0.45, 0.45, 0.07, 0.25, 18, 2.0
_damped = LAG_OMEGA * math.sqrt(1 - LAG_DAMPING ** 2)
LAG_W = [math.exp(-LAG_DAMPING * LAG_OMEGA * ((i + 0.5) * LAG_DT)) * math.sin(_damped * (i + 0.5) * LAG_DT)
         for i in range(LAG_N)]


def grip_pos(rig, pose):
    return grip_frame(1.0, rig, pose)[:3, 3]


def arm_lag(rig, move, tick):
    gain = LAG_GAIN * rig.follow_through
    if gain <= 0:
        return np.zeros(3)
    f = np.zeros(3)
    for i, w in enumerate(LAG_W):
        f += grip_pos(rig, move.sample(tick - (i + 0.5) * LAG_DT)) * w
    f /= sum(LAG_W)
    rem = max(0.0, min(1.0, (move.total - tick) / LAG_END_FADE))
    fade = rem * rem * (3 - 2 * rem)
    lag = (f - grip_pos(rig, move.sample(tick))) * gain * fade
    n = np.linalg.norm(lag)
    return lag * (LAG_CAP / n) if n > LAG_CAP else lag


# ============================================================================ arm IK (FirstPersonArmIk port)
FLEX_LIMIT, RADIAL_LIMIT, ULNAR_LIMIT = 45.0, 30.0, 45.0
REACH_FRACTION, MIN_REACH_FRACTION = 0.97, 0.30
FIST_LENGTH_PX, FIST_OVERLAP_PX = 4.0, 0.75
GRIP_ALONG_PX = FIST_LENGTH_PX * 0.5 - FIST_OVERLAP_PX
DEPTH_PX = 4.0
LAG_SHOULDER_SHARE, LAG_SWIVEL_LIMIT = 0.6, 30.0
RIGHT_POLE = np.array([0.5, -1.0, 0.3]) / np.linalg.norm([0.5, -1.0, 0.3])


def _norm(v):
    return v / np.linalg.norm(v)


def perp_raw(v, axis):
    return v - axis * (v @ axis)


def perp(v, axis):
    r = perp_raw(v, axis)
    n = np.linalg.norm(r)
    return None if n < 1e-3 else r / n


def rotate_axis(v, rad, axis):
    """JOML Vector3f.rotateAxis: right-handed rotation of v about unit axis."""
    c, s = math.cos(rad), math.sin(rad)
    return v * c + np.cross(axis, v) * s + axis * (axis @ v) * (1 - c)


def basis(x_hint, y_axis):
    x = perp(x_hint, y_axis)
    if x is None:
        x = perp(np.array([1.0, 0, 0]), y_axis)
    if x is None:
        x = perp(np.array([0, 0, 1.0]), y_axis)
    z = _norm(np.cross(x, y_axis))
    return np.c_[x, y_axis, z]


def wrist_to_grip(rig):
    return GRIP_ALONG_PX * rig.thickness / 16.0


def _candidate(fr, hand_roll, swivel, lag, rig):
    d = math.radians(rig.grip_diagonal)
    blade = fr["blade"]
    thumb = blade * math.cos(d) - hand_roll * math.sin(d)
    hand = blade * math.sin(d) + hand_roll * math.cos(d)
    palm = np.cross(thumb, hand)
    wrist = fr["grip"] - hand * wrist_to_grip(rig)
    return _bones(fr["shoulder"], rig, wrist, thumb, hand, palm, swivel, lag)


def _bones(req_shoulder, rig, wrist, thumb, hand, palm, swivel, lag):
    shoulder = req_shoulder.copy()
    if lag is not None:
        shoulder = shoulder + lag * LAG_SHOULDER_SHARE
    U, F = rig.upper_arm, rig.forearm
    to_wrist = wrist - shoulder
    dist = float(np.linalg.norm(to_wrist))
    axis = to_wrist / dist if dist > 1e-4 else np.array([0, 0, -1.0])
    reach_limit, min_reach = REACH_FRACTION * (U + F), MIN_REACH_FRACTION * (U + F)
    c = max(min_reach, min(reach_limit, dist))
    solved_shoulder = wrist - axis * c
    along = (U * U - F * F + c * c) / (2 * c)
    out = math.sqrt(max(0.0, U * U - along * along))
    bend = perp(RIGHT_POLE, axis)
    if bend is None:
        bend = perp(np.array([0, -1.0, 0]), axis)
    if bend is None:
        bend = perp(np.array([1.0, 0, 0]), axis)
    bend = rotate_axis(bend, math.radians(-swivel), axis)
    if lag is not None:
        dragged = perp(bend * out + perp_raw(lag, axis), axis)
        if dragged is not None:
            turn = math.degrees(math.atan2(np.cross(bend, dragged) @ axis, bend @ dragged))
            if abs(turn) > LAG_SWIVEL_LIMIT:
                dragged = rotate_axis(bend, math.radians(math.copysign(LAG_SWIVEL_LIMIT, turn)), axis)
            bend = dragged
    elbow = solved_shoulder + axis * along + bend * out
    fa = _norm(wrist - elbow)
    ah = fa @ hand
    flex = -math.degrees(math.atan2(fa @ palm, ah))
    dev = -math.degrees(math.atan2(fa @ thumb, ah))
    return dict(shoulder=solved_shoulder, elbow=elbow, wrist=wrist, fa=fa, thumb=thumb, hand=hand, palm=palm,
                flex=flex, dev=dev, clamped=dist > reach_limit or dist < min_reach, reach=dist)


def _within(c):
    return abs(c["flex"]) <= FLEX_LIMIT and c["dev"] <= RADIAL_LIMIT and c["dev"] >= -ULNAR_LIMIT


def solve_arm(side, rig, pose, lag):
    G = grip_frame(1.0, rig, pose)
    s = rig.shoulder
    pivot = np.array([s[0] + pose.x, s[1] + pose.y, s[2] + pose.z])
    fr = {"grip": G[:3, 3].copy(), "blade": _norm(G[:3, :3] @ [0, 1.0, 0]),
          "shoulder": pivot + np.array(rig.shoulder_offset)}
    r = math.radians(pose.grip_roll)
    hand_roll = _norm(G[:3, :3] @ [0, 0, 1.0]) * math.cos(r) + _norm(G[:3, :3] @ [1.0, 0, 0]) * math.sin(r)
    still = _candidate(fr, hand_roll, pose.elbow, None, rig)
    solved, lag_scale = still, 0.0
    if lag is not None and lag @ lag > 1e-10:
        full = _candidate(fr, hand_roll, pose.elbow, lag, rig)
        if _within(full) or not _within(still):
            solved, lag_scale = full, 1.0
        else:
            lo, hi = 0.0, 1.0
            for _ in range(5):
                mid = (lo + hi) * 0.5
                if _within(_candidate(fr, hand_roll, pose.elbow, lag * mid, rig)):
                    lo = mid
                else:
                    hi = mid
            if lo > 0:
                solved, lag_scale = _candidate(fr, hand_roll, pose.elbow, lag * lo, rig), lo
    c = solved
    upper_axis = _norm(c["elbow"] - c["shoulder"])
    fore_R = basis(c["thumb"], c["fa"])
    sol = dict(shoulder=c["shoulder"], elbow=c["elbow"], wrist=c["wrist"], grip=fr["grip"],
               upper_R=basis(fore_R[:, 0], upper_axis), fore_R=fore_R, fist_R=basis(c["thumb"], c["hand"]),
               flex=c["flex"], dev=c["dev"], lag_scale=lag_scale, clamped=c["clamped"], reach=c["reach"],
               lag=None if lag is None else lag.copy())
    if side < 0:  # Solution.mirrored(): x -> -x, rotations conjugated by the reflection
        M = np.diag([-1.0, 1.0, 1.0])
        for k in ("shoulder", "elbow", "wrist", "grip"):
            sol[k] = M @ sol[k]
        for k in ("upper_R", "fore_R", "fist_R"):
            sol[k] = M @ sol[k] @ M
    return sol


# ============================================================================ off arm (FirstPersonArmIk.solveOffHand port)
OFF_HAND_GAP_PX, OFF_HAND_END_PX, OFF_REACH_SAMPLES, OFF_REACH_BISECTIONS = 6.0, 3.0, 48, 10
MIRROR = np.diag([-1.0, 1.0, 1.0])


class _OffArmRig:
    """FirstPersonSwing.Rig.offArm(): rig.off_hand's shoulder offset, grip diagonal, thickness and
    bones (each defaulting to the main arm's), plus its released rest."""

    def __init__(self, rig):
        oh = rig.off_hand
        self.upper_arm, self.forearm, self.thickness = oh["upper_arm"], oh["forearm"], oh["thickness"]
        self.grip_diagonal = oh["grip_diagonal"]
        self.shoulder_offset = oh["shoulder_offset"]
        self.rest_direction, self.rest_reach = oh["rest_direction"], oh["rest_reach"]


def _quat_from(Rm):
    """Rotation matrix -> unit quaternion (x, y, z, w)."""
    t = np.trace(Rm)
    if t > 0:
        s = math.sqrt(t + 1.0) * 2
        return np.array([(Rm[2, 1] - Rm[1, 2]) / s, (Rm[0, 2] - Rm[2, 0]) / s, (Rm[1, 0] - Rm[0, 1]) / s, 0.25 * s])
    i = int(np.argmax(np.diag(Rm)))
    j, k = (i + 1) % 3, (i + 2) % 3
    s = math.sqrt(1.0 + Rm[i, i] - Rm[j, j] - Rm[k, k]) * 2
    q = np.zeros(4)
    q[i] = 0.25 * s
    q[j] = (Rm[j, i] + Rm[i, j]) / s
    q[k] = (Rm[k, i] + Rm[i, k]) / s
    q[3] = (Rm[k, j] - Rm[j, k]) / s
    return q


def _quat_rot(q):
    x, y, z, w = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def _slerp(a, b, t):
    """JOML Quaternionf.slerp (shortest path)."""
    cos = float(a @ b)
    absc = abs(cos)
    if 1.0 - absc > 1e-6:
        sin_sqr = 1.0 - absc * absc
        sin = 1.0 / math.sqrt(sin_sqr)
        om = math.atan2(sin_sqr * sin, absc)
        s0, s1 = math.sin((1.0 - t) * om) * sin, math.sin(t * om) * sin
    else:
        s0, s1 = 1.0 - t, t
    s1 = -s1 if cos < 0 else s1
    return a * s0 + b * s1


def solve_off_arm(side, rig, geo, pose, equip=0.0):
    """FirstPersonArmIk.solveOffHand: None without rig.off_hand or at off_hand_hold 0."""
    hold = max(0.0, min(1.0, pose.off_hand_hold))
    if rig.off_hand is None or geo.off_hand is None or hold <= 0.0:
        return None
    arm = _OffArmRig(rig)
    G = grip_frame(1.0, rig, pose, equip)
    blade = MIRROR @ _norm(G[:3, :3] @ [0, 1.0, 0])
    edge = MIRROR @ _norm(G[:3, :3] @ [0, 0, 1.0])
    so = arm.shoulder_offset
    s = rig.shoulder
    shoulder = np.array([s[0] + so[0] - pose.x, s[1] + pose.y - equip * EQUIP_DROP + so[1], s[2] + pose.z + so[2]])
    roll = math.radians(pose.off_hand_roll)
    d = math.radians(arm.grip_diagonal)

    def grasp(y):
        grip = MIRROR @ pt(G, geo.to_grip([geo.grip[0], y, geo.grip[2]], rig.weapon_scale))
        reach = perp(grip - shoulder, blade)
        if reach is None:
            reach = perp(edge, blade)
        hand_roll = rotate_axis(reach, roll, blade)
        thumb = blade * math.cos(d) - hand_roll * math.sin(d)
        hand = blade * math.sin(d) + hand_roll * math.cos(d)
        return grip - hand * wrist_to_grip(arm), thumb, hand, np.cross(thumb, hand)

    total = arm.upper_arm + arm.forearm

    def overreach(y):
        dist = float(np.linalg.norm(grasp(y)[0] - shoulder))
        return max(dist - REACH_FRACTION * total, MIN_REACH_FRACTION * total - dist)

    k = arm.thickness / rig.weapon_scale
    lo = geo.grip[1] + OFF_HAND_GAP_PX * k
    hi = geo.handle[1] - OFF_HAND_END_PX * k
    if lo > hi:
        lo = hi = max(geo.handle[0], min(geo.handle[1], geo.off_hand[1]))
    wanted = max(lo, min(hi, geo.off_hand[1] + pose.off_hand_slide))
    y = wanted
    if overreach(wanted) > 0 and hi > lo:
        step = (hi - lo) / OFF_REACH_SAMPLES
        best, least, least_at = None, math.inf, wanted
        for i in range(OFF_REACH_SAMPLES + 1):
            yi = lo + step * i
            o = overreach(yi)
            if o <= 0 and (best is None or abs(yi - wanted) < abs(best - wanted)):
                best = yi
            if o < least:
                least, least_at = o, yi
        if best is None:
            y = least_at
        else:
            reached, missed = best, best + math.copysign(min(step, abs(wanted - best)), wanted - best)
            for _ in range(OFF_REACH_BISECTIONS):
                mid = (reached + missed) * 0.5
                if overreach(mid) <= 0:
                    reached = mid
                else:
                    missed = mid
            y = reached
    wrist, thumb, hand, palm = grasp(y)
    if hold < 1.0:
        rest_hand = arm.rest_direction.copy()
        rest_wrist = shoulder + rest_hand * arm.rest_reach * total
        rest_thumb = perp(np.array([0, 0, -1.0]), rest_hand)
        q = _slerp(_quat_from(basis(rest_thumb, rest_hand)), _quat_from(basis(thumb, hand)), hold)
        Rq = _quat_rot(q)
        wrist = rest_wrist + (wrist - rest_wrist) * hold
        thumb, hand, palm = Rq[:, 0], Rq[:, 1], Rq[:, 2]
    c = _bones(shoulder, arm, wrist, thumb, hand, palm, pose.off_hand_elbow, None)
    grip = wrist + hand * wrist_to_grip(arm)
    upper_axis = _norm(c["elbow"] - c["shoulder"])
    fore_R = basis(c["thumb"], c["fa"])
    sol = dict(shoulder=c["shoulder"], elbow=c["elbow"], wrist=c["wrist"], grip=grip,
               upper_R=basis(fore_R[:, 0], upper_axis), fore_R=fore_R, fist_R=basis(c["thumb"], c["hand"]),
               flex=c["flex"], dev=c["dev"], lag_scale=0.0, clamped=c["clamped"], reach=c["reach"], lag=None,
               grip_y=y, wanted_y=wanted, slid=abs(y - wanted) > 1e-3, hold=hold)
    if side > 0:  # mirrored back into the right-handed scene; a left main arm keeps the mirror image
        for key in ("shoulder", "elbow", "wrist", "grip"):
            sol[key] = MIRROR @ sol[key]
        for key in ("upper_R", "fore_R", "fist_R"):
            sol[key] = MIRROR @ sol[key] @ MIRROR
    return sol


# ============================================================================ arm mesh (FirstPersonArmModel / Renderer port)
FIST_WIDTH, FOREARM_WIDTH = 1.1, 0.8
ELBOW_OVERLAP_PX = WRIST_OVERLAP_PX = 1.0
SHOULDER_TO_ELBOW_OVERLAP_PX = 0.5
SLEEVE_INFLATION_PX = 0.25
UPPER_TEX_PX, FOREARM_FIRST_ROW, FOREARM_TEX_PX, FIST_FIRST_ROW, WRIST_CAP_ROW = 6.0, 4, 5.0, 8, 12
WRIST_CAP_INSET = 0.1
SIDES = ("north", "south", "east", "west")


def cube_polys(u, v, x, y, z, w, h, d, faces, tex_w=64.0, tex_h=64.0):
    """Vanilla ModelPart.Cube polygons: [(4x3 px positions, 4 uv in 0..1)] for the listed faces."""
    X, Y, Z = x + w, y + h, z + d
    v7, v0, v1, v2 = (x, y, z), (X, y, z), (X, Y, z), (x, Y, z)
    v3, v4, v5, v6 = (x, y, Z), (X, y, Z), (X, Y, Z), (x, Y, Z)
    f4, f5, f6 = u, u + d, u + d + w
    f7, f8, f9 = u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    table = {
        "down": ([v4, v3, v7, v0], f5, f10, f6, f11), "up": ([v1, v2, v6, v5], f6, f11, f7, f10),
        "west": ([v7, v3, v6, v2], f4, f11, f5, f12), "north": ([v0, v7, v2, v1], f5, f11, f6, f12),
        "east": ([v4, v0, v1, v5], f6, f11, f8, f12), "south": ([v3, v4, v5, v6], f8, f11, f9, f12),
    }
    out = []
    for face in ("down", "up", "west", "north", "east", "south"):
        if face not in faces:
            continue
        vs, u1, v1_, u2, v2_ = table[face]
        uv = [(u2 / tex_w, v1_ / tex_h), (u1 / tex_w, v1_ / tex_h), (u1 / tex_w, v2_ / tex_h), (u2 / tex_w, v2_ / tex_h)]
        out.append((np.array(vs, float), uv))
    return out


def arm_parts(width_px, side, skin):
    """FirstPersonArmModel.create: {part: [(pos px, uv)]} for skin (sleeve=False) or sleeve."""
    right = side > 0
    if skin:
        u, v = (40, 16) if right else (32, 48)
    else:
        u, v = (40, 32) if right else (48, 48)
    mx, mz = -width_px / 2.0, -DEPTH_PX / 2.0
    parts = {
        "upper": cube_polys(u, v, mx, 0, mz, width_px, UPPER_TEX_PX, DEPTH_PX, SIDES),
        "forearm": cube_polys(u, v + FOREARM_FIRST_ROW, mx, 0, mz, width_px, FOREARM_TEX_PX, DEPTH_PX, SIDES),
    }
    fs = -FIST_OVERLAP_PX
    fist = cube_polys(u, v + FIST_FIRST_ROW, mx, fs, mz, width_px, FIST_LENGTH_PX, DEPTH_PX, SIDES)
    fist += cube_polys(u, v, mx, fs, mz, width_px, FIST_LENGTH_PX, DEPTH_PX, ("up",))
    if skin:
        i = WRIST_CAP_INSET
        fist += cube_polys(u, v + WRIST_CAP_ROW, mx + i, fs + i, mz + i, width_px - 2 * i, FIST_LENGTH_PX - 2 * i,
                           DEPTH_PX - 2 * i, ("down",))
    parts["fist"] = fist
    return parts


def arm_polys(sol, rig, width_px, side, sleeve, inflation, prefix=""):
    """FirstPersonArmRenderer.drawArm: [(4x3 view-space, uv, tag)]."""
    t = rig.thickness
    pixel = t / 16.0
    width = t * (width_px + 2 * inflation) / width_px
    depth = t * (DEPTH_PX + 2 * inflation) / DEPTH_PX
    extra = inflation * pixel
    parts = arm_parts(width_px, side, not sleeve)
    out = []

    def seg(name, joint, Rm, along, sw, sl, sd, tag):
        M = A(Rm, joint) @ A(t=(0, along, 0)) @ A(np.diag([sw, sl, sd])) @ A(np.eye(3) / 16.0)
        for pos, uv in parts[name]:
            out.append((pos @ M[:3, :3].T + M[:3, 3], uv, prefix + tag))

    upper_len = rig.upper_arm + SHOULDER_TO_ELBOW_OVERLAP_PX * pixel + extra
    seg("upper", sol["shoulder"], sol["upper_R"], 0.0, width, upper_len * 16.0 / UPPER_TEX_PX, depth, "upper")
    back = ELBOW_OVERLAP_PX * pixel + extra
    fore_len = back + rig.forearm + WRIST_OVERLAP_PX * pixel + extra
    seg("forearm", sol["elbow"], sol["fore_R"], -back, width * FOREARM_WIDTH, fore_len * 16.0 / FOREARM_TEX_PX,
        depth * FOREARM_WIDTH, "forearm")
    fist_scale = t * (FIST_LENGTH_PX + 2 * inflation) / FIST_LENGTH_PX
    seg("fist", sol["wrist"], sol["fist_R"], 0.0, width * FIST_WIDTH, fist_scale, depth * FIST_WIDTH, "fist")
    return out


def fist_center(sol, rig):
    along = (-FIST_OVERLAP_PX + FIST_LENGTH_PX / 2.0) * rig.thickness / 16.0
    return sol["wrist"] + sol["fist_R"][:, 1] * along


# ============================================================================ skins
DEFAULT_SKINS = ("alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri")


def offline_skin(name):
    """DefaultPlayerSkin.get(offline UUID of name): (model, skin name)."""
    b = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    b[6] = (b[6] & 0x0F) | 0x30
    b[8] = (b[8] & 0x3F) | 0x80
    hilo = int.from_bytes(b[:8], "big") ^ int.from_bytes(b[8:], "big")
    h = ((hilo >> 32) ^ hilo) & 0xFFFFFFFF
    h = h - (1 << 32) if h >= 1 << 31 else h
    i = h % 18
    return ("slim" if i < 9 else "wide"), DEFAULT_SKINS[i % 9]


def placeholder_skin():
    """Two-tone 64x64: skin tone arm, darker fist rows and hand caps; empty sleeve layer."""
    t = np.zeros((64, 64, 4), np.float32)
    tone, dark, cap = (0.80, 0.60, 0.45, 1.0), (0.60, 0.40, 0.28, 1.0), (0.50, 0.32, 0.22, 1.0)
    for (u, v) in ((40, 16), (32, 48)):
        t[v:v + 16, u:u + 16] = tone
        t[v + 4 + 8:v + 16, u:u + 16] = dark  # strip rows 8..11 (fist)
        t[v:v + 4, u:u + 16] = cap
    return t


def load_skin(spec, arms, vanilla_jar):
    """Returns (texture HxWx4 float, arms 'slim'|'wide', label)."""
    if spec == "placeholder":
        return placeholder_skin(), arms or "slim", "placeholder two-tone"
    model, name = None, spec
    if spec.startswith("offline:"):
        model, name = offline_skin(spec.split(":", 1)[1])
    elif spec.endswith(".png"):
        im = Image.open(spec).convert("RGBA")
        return np.asarray(im, np.float32) / 255.0, arms or "wide", Path(spec).name
    if name in DEFAULT_SKINS:
        model = arms or model or "slim"
        try:
            import zipfile
            with zipfile.ZipFile(vanilla_jar) as z:
                data = z.read(f"assets/minecraft/textures/entity/player/{model}/{name}.png")
            return np.asarray(Image.open(io.BytesIO(data)).convert("RGBA"), np.float32) / 255.0, model, \
                f"default {model}/{name}"
        except (OSError, KeyError):
            print(f"WARN: vanilla skin {model}/{name} not readable; using the placeholder", file=sys.stderr)
            return placeholder_skin(), model, "placeholder two-tone"
    raise SystemExit(f"ERROR: --skin {spec!r}: give placeholder, a default skin name ({', '.join(DEFAULT_SKINS)}), "
                     "offline:<username> or a .png")


# ============================================================================ trail (FirstPersonWeaponTrail port)
TRAIL_TICKS, TRAIL_FADE_TICKS, TRAIL_SEGMENTS, STREAK_TICKS = 1.2, 2.4, 24, 3.0
STREAK_HALF_WIDTH, STREAK_START, STREAK_OVERSHOOT = 0.012, 0.35, 0.15
INNER_NEW, INNER_OLD, EDGE_FRACTION, PEAK_ALPHA, BODY_SHARE, TIP_SHARE = 0.55, 0.97, 0.85, 0.7, 0.5, 0.9
EDGE_RGB = np.array([235, 248, 255]) / 255.0
BODY_RGB = np.array([160, 215, 255]) / 255.0


def _c01(v):
    return max(0.0, min(1.0, v))


def trail_quads(side, rig, geo, move, now):
    """[(4x3 view-space points, 4x4 rgba)] like FirstPersonWeaponTrail.onRenderHand."""
    def blade(pose):
        return weapon_point(side, rig, geo, pose, geo.trail_base), weapon_point(side, rig, geo, pose, geo.trail_tip)

    out = []
    if move.kind == "thrust":
        if now < move.strike[0]:
            return out
        alpha = 0.8 * _c01(1 - (now - move.strike[0]) / STREAK_TICKS)
        if alpha <= 0:
            return out
        b, t = blade(move.sample(min(now, move.strike[1])))
        L = t - b
        start, end = b + L * STREAK_START, t + L * STREAK_OVERSHOOT
        ax, mid = end - start, (start + end) * 0.5
        sd = np.cross(ax, -mid)
        if sd @ sd < 1e-10:
            return out
        sd = _norm(sd)
        bs, ts = sd * STREAK_HALF_WIDTH * 0.35, sd * STREAK_HALF_WIDTH
        out.append((np.array([start + bs, end + ts, end - ts, start - bs]),
                    np.array([[*EDGE_RGB, 0], [*EDGE_RGB, alpha], [*EDGE_RGB, alpha], [*EDGE_RGB, 0]])))
        return out
    newest, oldest = min(now, move.strike[1]), max(move.strike[0], now - TRAIL_TICKS)
    if newest <= oldest:
        return out
    fade = 1.0 if now <= move.strike[1] else _c01(1 - (now - move.strike[1]) / TRAIL_FADE_TICKS)
    if fade <= 0:
        return out

    def inner(age):
        t = _c01(age)
        return INNER_NEW + (INNER_OLD - INNER_NEW) * t * t * (3 - 2 * t)

    prev = None
    for i in range(TRAIL_SEGMENTS + 1):
        tick = newest - (newest - oldest) * i / TRAIL_SEGMENTS
        age = (now - tick) / TRAIL_TICKS
        alpha = PEAK_ALPHA * (1 - _c01(age)) ** 2 * _c01(fade)
        b, t = blade(move.sample(tick))
        if prev is not None:
            (nb, nt, na, nal), (ob, ot, oa, oal) = prev, (b, t, age, alpha)
            ni, ne = nb + (nt - nb) * inner(na), nb + (nt - nb) * max(inner(na), EDGE_FRACTION)
            oi, oe = ob + (ot - ob) * inner(oa), ob + (ot - ob) * max(inner(oa), EDGE_FRACTION)
            out.append((np.array([ni, ne, oe, oi]), np.array([[*BODY_RGB, 0], [*BODY_RGB, nal * BODY_SHARE],
                                                             [*BODY_RGB, oal * BODY_SHARE], [*BODY_RGB, 0]])))
            out.append((np.array([ne, nt, ot, oe]), np.array([[*EDGE_RGB, nal], [*EDGE_RGB, nal * TIP_SHARE],
                                                             [*EDGE_RGB, oal * TIP_SHARE], [*EDGE_RGB, oal]])))
        prev = (b, t, age, alpha)
    return out


# ============================================================================ raster
TAGS = {"bg": 0, "weapon": 1, "upper": 2, "forearm": 3, "fist": 4, "off_upper": 5, "off_forearm": 6, "off_fist": 7}


class Frame:
    def __init__(self, W, H, fov=FOV):
        self.W, self.H = W, H
        self.k = (H / 2.0) / math.tan(math.radians(fov) / 2.0)
        self.img = np.empty((H, W, 3), np.float32)
        self.z = np.full((H, W), -np.inf, np.float32)
        self.id = np.zeros((H, W), np.uint8)
        self.trail = np.zeros((H, W), np.float32)

    def project(self, P):
        P = np.atleast_2d(P)
        w = -P[:, 2]
        with np.errstate(divide="ignore", invalid="ignore"):
            return np.c_[self.W / 2 + P[:, 0] / w * self.k, self.H / 2 - P[:, 1] / w * self.k, w]

    def backdrop(self, eye_height=1.62, ground_extent=78.0):
        """Sky gradient over a flat grass plane eye_height below the eye (level look, like the capture).
        The capture's superflat terrain ends about 78 blocks out, so its horizon sits ~8 px below centre."""
        H, W = self.H, self.W
        ys = (H / 2 - (np.arange(H) + 0.5)) / self.k  # tan of the elevation per row
        sky_top, sky_hor = np.array([0.58, 0.73, 1.0]), np.array([0.69, 0.81, 1.0])
        t = np.clip(ys / 0.75, 0, 1)[:, None]
        rows = sky_hor * (1 - t) + sky_top * t
        self.img[:] = rows[:, None, :].astype(np.float32)
        below = ys < -eye_height / ground_extent
        if below.any():
            yy = np.where(below)[0]
            dist = eye_height / -ys[yy]
            xs = (np.arange(W) + 0.5 - W / 2) / self.k
            gx = xs[None, :] * dist[:, None]
            chk = ((np.floor(gx) + np.floor(dist[:, None])) % 2 == 0)
            g1, g2 = np.array([0.32, 0.42, 0.20], np.float32), np.array([0.29, 0.38, 0.18], np.float32)
            self.img[yy] = np.where(chk[..., None], g1, g2)

    def clip(self, P, attrs):
        w = -P[:, 2]
        if (w >= NEAR).all():
            return P, attrs
        if (w < NEAR).all():
            return None, None
        n = len(P)
        op, oa = [], []
        for i in range(n):
            j = (i + 1) % n
            if w[i] >= NEAR:
                op.append(P[i])
                oa.append(attrs[i])
            if (w[i] >= NEAR) != (w[j] >= NEAR):
                a = (NEAR - w[i]) / (w[j] - w[i])
                op.append(P[i] + a * (P[j] - P[i]))
                oa.append(attrs[i] + a * (attrs[j] - attrs[i]))
        if len(op) < 3:
            return None, None
        return np.array(op), np.array(oa)

    def poly(self, P, attrs, mode, tex=None, shade=1.0, tag=0, cull=True):
        """Convex polygon in view space; attrs per vertex (uv for 'tex', rgba for 'blend')."""
        if cull:
            n = np.cross(P[1] - P[0], P[2] - P[0])
            if n @ n < 1e-20:
                n = np.cross(P[2] - P[0], P[3] - P[0]) if len(P) > 3 else n
            if n @ (-P[0]) <= 0:
                return
        P, attrs = self.clip(np.asarray(P, float), np.asarray(attrs, float))
        if P is None:
            return
        S = self.project(P)
        for k in range(1, len(P) - 1):
            self._tri(S[[0, k, k + 1]], attrs[[0, k, k + 1]], mode, tex, shade, tag)

    def _tri(self, S, at, mode, tex, shade, tag):
        H, W = self.H, self.W
        x0 = max(int(math.floor(S[:, 0].min())), 0)
        x1 = min(int(math.ceil(S[:, 0].max())), W - 1)
        y0 = max(int(math.floor(S[:, 1].min())), 0)
        y1 = min(int(math.ceil(S[:, 1].max())), H - 1)
        if x0 > x1 or y0 > y1:
            return
        (ax, ay, _), (bx, by, _), (cx, cy, _) = S
        area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if abs(area) < 1e-12:
            return
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((bx - X) * (cy - Y) - (by - Y) * (cx - X)) / area
        w1 = ((cx - X) * (ay - Y) - (cy - Y) * (ax - X)) / area
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-7) & (w1 >= -1e-7) & (w2 >= -1e-7)
        if not inside.any():
            return
        iw = 1.0 / S[:, 2]
        Z = w0 * iw[0] + w1 * iw[1] + w2 * iw[2]  # 1/w: linear in screen space; bigger = nearer
        zs = self.z[y0:y1 + 1, x0:x1 + 1]
        m = inside & (Z > zs + 1e-9) if mode != "blend" else inside & (Z >= zs - 1e-6)
        if not m.any():
            return
        b0, b1, b2 = w0 * iw[0] / Z, w1 * iw[1] / Z, w2 * iw[2] / Z  # perspective-correct weights
        img = self.img[y0:y1 + 1, x0:x1 + 1]
        if mode == "blend":
            rgba = b0[..., None] * at[0] + b1[..., None] * at[1] + b2[..., None] * at[2]
            a = np.clip(rgba[..., 3], 0, 1)[..., None]
            img[m] = (rgba[..., :3] * a + img * (1 - a))[m]
            tr = self.trail[y0:y1 + 1, x0:x1 + 1]
            tr[m] = np.maximum(tr[m], a[..., 0][m])
            return
        U = b0 * at[0][0] + b1 * at[1][0] + b2 * at[2][0]
        V = b0 * at[0][1] + b1 * at[1][1] + b2 * at[2][1]
        th, tw = tex.shape[:2]
        texel = tex[np.clip(np.floor(V * th).astype(int), 0, th - 1), np.clip(np.floor(U * tw).astype(int), 0, tw - 1)]
        if mode == "cutout":
            m &= texel[..., 3] >= 0.1
        rgb = texel[..., :3] * shade
        if mode == "cutout_blend":  # entityTranslucent sleeve: discard < 0.1, blend the rest
            m &= texel[..., 3] >= 0.1
            a = texel[..., 3:4]
            rgb = rgb * a + img * (1 - a)
        img[m] = rgb[m]
        zs[m] = Z[m]
        self.id[y0:y1 + 1, x0:x1 + 1][m] = tag


def shade_of(P):
    """Entity shader lighting, normals turned to world axes (camera facing south, level)."""
    n = np.cross(P[1] - P[0], P[2] - P[0])
    nn = np.linalg.norm(n)
    if nn < 1e-15:
        return 1.0
    n = n / nn
    nw = np.array([-n[0], n[1], -n[2]])
    return min(1.0, 0.4 + 0.6 * (max(0.0, nw @ LIGHT0) + max(0.0, nw @ LIGHT1)))


# ============================================================================ scene
class Scene:
    def __init__(self, a):
        self.a = a
        self.data = ModData(a.root)
        w = load_weapon(self.data, a.weapon)
        self.weapon_info = w
        self.style = w["style"]
        if a.rig:
            self.rig_path = Path(a.rig)
            if not self.rig_path.is_file():
                raise SystemExit(f"ERROR: --rig {a.rig} not found")
        else:
            self.rig_path = self.data.find(w["rig_rel"])
            if self.rig_path is None:
                raise SystemExit(f"ERROR: the weapon's first_person_rig {w['rig_rel']} does not exist in the roots; "
                                 "pass --rig <file>. (In game the weapon would keep the vanilla held-item pose.)")
        gpath = Path(a.geometry) if a.geometry else self.data.find(w["geometry_rel"])
        if gpath is None or not gpath.is_file():
            raise SystemExit(f"ERROR: geometry contract {a.geometry or w['geometry_rel']} not found")
        self.geo_path = gpath
        rig_bytes = self.rig_path.read_bytes()
        self.rig_sha = hashlib.sha256(rig_bytes).hexdigest()
        try:
            self.geo = Geometry(json.loads(gpath.read_text(encoding="utf-8")))
            self.rig = Rig(json.loads(rig_bytes), self.style, self.rig_path.name, self.geo)
        except (RigError, KeyError, TypeError, ValueError) as e:
            raise SystemExit(f"ERROR: the game would REJECT this rig/geometry and keep the vanilla hold: {e}")
        if self.geo.off_hand is not None and self.rig.off_hand is None:
            print("note: the geometry has off_hand_grip_center but the rig has no rig.off_hand: one arm is drawn "
                  "(the magenta marker is the off-hand point)", file=sys.stderr)
        self.assets = pim.Assets(a.root, None if a.no_vanilla else a.vanilla_jar)
        self.model = pim.ItemModel(self.assets, a.model or w["model"])
        self.variant = self.model.variant("firstperson_righthand" if a.main_arm == "right" else "firstperson_lefthand")
        disp = self.variant.display("firstperson_righthand")
        if disp is not None:
            D = pim.display_matrix(disp)
            if abs(np.linalg.det(D[:3, :3])) < 1e-9 * (1 / 16.0) ** 3:
                loud("the model's firstperson display transform is singular (zero scale): the game skips the "
                     "inverse and the item collapses; this preview draws it as if the display cancelled")
        for msg in self.model.messages():
            if msg.startswith("ERROR"):
                loud(f"item model: {msg}")
        self.side = 1.0 if a.main_arm == "right" else -1.0
        self.skin, self.arms, self.skin_label = load_skin(a.skin, a.arms, a.vanilla_jar)
        self.width_px = 3.0 if self.arms == "slim" else 4.0
        self.markers = []
        for s in a.marker:
            if s in self.geo.raw and isinstance(self.geo.raw[s], list) and len(self.geo.raw[s]) == 3:
                self.markers.append((s, np.array(self.geo.raw[s], float)))
                continue
            try:
                v = [float(x) for x in s.split(",")]
            except ValueError:
                raise SystemExit(f"ERROR: marker {s!r}: give x,y,z in item-model px or a point of the contract")
            if len(v) != 3:
                raise SystemExit(f"ERROR: marker {s!r} needs 3 values")
            self.markers.append((f"m{len(self.markers) + 1}({s})", np.array(v)))

    def move(self, n):
        if not 1 <= n <= len(self.rig.moves):
            raise SystemExit(f"ERROR: --move {n}: the style has moves 1..{len(self.rig.moves)}")
        return self.rig.moves[n - 1]

    def state(self, move, tick, lag=True):
        pose = move.sample(tick)
        lagv = arm_lag(self.rig, move, tick) if lag else None
        sol = solve_arm(self.side, self.rig, pose, lagv)
        G = grip_frame(self.side, self.rig, pose)
        M = item_matrix(G, self.geo, self.rig.weapon_scale)
        off = None if self.a.off_hand_occupied else solve_off_arm(self.side, self.rig, self.geo, pose)
        sol["off"] = off
        return pose, sol, G, M

    def draw(self, frame, move, tick, lag=True, trail=True):
        pose, sol, G, M = self.state(move, tick, lag)
        off = sol["off"]
        if off is not None:
            off_rig = _OffArmRig(self.rig)
            for P, uv, tag in arm_polys(off, off_rig, self.width_px, -self.side, False, 0.0, "off_"):
                frame.poly(P, np.array(uv), "solid", self.skin, shade_of(P), TAGS[tag])
            if self.a.sleeve:
                for P, uv, tag in arm_polys(off, off_rig, self.width_px, -self.side, True, SLEEVE_INFLATION_PX, "off_"):
                    frame.poly(P, np.array(uv), "cutout_blend", self.skin, shade_of(P), TAGS[tag])
        for q in self.variant.quads:
            P = q.pos @ M[:3, :3].T + M[:3, 3]
            uv = np.array([(u / 16.0, v / 16.0) for u, v in q.uv]) if q.uv else np.zeros((4, 2))
            tex = q.tex if q.tex is not None else np.array([[[*q.color, 1.0]]], np.float32)
            frame.poly(P, uv, "cutout", tex, shade_of(P) if q.shade else 1.0, TAGS["weapon"])
        for P, uv, tag in arm_polys(sol, self.rig, self.width_px, self.side, False, 0.0):
            frame.poly(P, np.array(uv), "solid", self.skin, shade_of(P), TAGS[tag])
        if self.a.sleeve:
            for P, uv, tag in arm_polys(sol, self.rig, self.width_px, self.side, True, SLEEVE_INFLATION_PX):
                frame.poly(P, np.array(uv), "cutout_blend", self.skin, shade_of(P), TAGS[tag])
        if trail:
            for P, rgba in trail_quads(self.side, self.rig, self.geo, move, tick):
                frame.poly(P, rgba, "blend", cull=False)
        return pose, sol, M

    def points(self, M, sol):
        pts = {"grip_center": pt(M, self.geo.grip), "head_tip": pt(M, self.geo.tip),
               "head_base": pt(M, self.geo.base), "fist": fist_center(sol, self.rig)}
        if self.geo.off_hand is not None:
            pts["off_hand_grip"] = pt(M, self.geo.off_hand)
        if sol.get("off") is not None:
            pts["off_fist"] = fist_center(sol["off"], _OffArmRig(self.rig))
        for name, p in self.markers:
            pts[name] = pt(M, p)
        return pts


def screen_info(P, W=GAME_W, H=GAME_H):
    k = (H / 2.0) / math.tan(math.radians(FOV) / 2.0)
    w = -P[2]
    if w < NEAR:
        return None, False, w
    x, y = W / 2 + P[0] / w * k, H / 2 - P[1] / w * k
    return (x, y), (0 <= x < W and 0 <= y < H), w


def under_hud(xy):
    return xy is not None and any(x0 <= xy[0] < x1 and y0 <= xy[1] < y1 for x0, y0, x1, y1 in HUD_BOXES)


# ============================================================================ overlays
MARK_COL = {"grip_center": (230, 30, 30), "head_tip": (0, 200, 230), "off_hand_grip": (230, 40, 220),
            "fist": (255, 140, 0), "off_fist": (255, 200, 0)}


def draw_frame_overlays(im, frame_w, frame_h, pts, marks=True, hud=True):
    d = ImageDraw.Draw(im)
    sx, sy = frame_w / GAME_W, frame_h / GAME_H
    cx, cy = frame_w / 2, frame_h / 2
    L = max(5, round(9 * sx))
    d.line((cx - L, cy, cx + L, cy), fill=(30, 30, 30), width=max(1, round(2 * sx)))
    d.line((cx, cy - L, cx, cy + L), fill=(30, 30, 30), width=max(1, round(2 * sx)))
    if hud:
        for x0, y0, x1, y1 in HUD_BOXES:
            d.rectangle((x0 * sx, y0 * sy, x1 * sx - 1, y1 * sy - 1), outline=(255, 255, 255), width=1)
            d.text((x0 * sx + 3, y0 * sy + 2), "HUD", fill=(255, 255, 255), font=sheets.font(max(9, round(11 * sx))))
    if not marks:
        return
    f = sheets.font(max(9, round(12 * sx)))
    for name, P in pts.items():
        if name == "head_base":
            continue
        xy, on, w = screen_info(P, frame_w, frame_h)
        col = MARK_COL.get(name, (250, 230, 40))
        if xy is None:
            continue
        x, y = xy
        r = max(3, round(5 * sx))
        if on:
            d.ellipse((x - r, y - r, x + r, y + r), outline=col, width=2)
        else:  # off screen: arrow head at the border toward it
            ex, ey = min(max(x, 4), frame_w - 5), min(max(y, 4), frame_h - 5)
            d.polygon([(ex - r, ey - r), (ex + r, ey - r), (ex + r, ey + r), (ex - r, ey + r)], outline=col)
            x, y = ex, ey
        if on and name not in ("grip_center", "head_tip", "fist", "off_fist"):
            d.text((x + r + 2, y - r), name, fill=col, font=f)


# ============================================================================ measurement
def measure(scene, frame, pts, sol, move, tick, pose):
    tot = frame.W * frame.H
    ids = frame.id
    out = {"move": scene.rig.moves.index(move) + 1, "move_id": move.id, "tick": tick,
           "pose": dict(zip(POSE_FIELDS, [round(v, 4) for v in pose])),
           "cover_weapon": float((ids == 1).sum()) / tot, "cover_arm": float((ids >= 2).sum()) / tot,
           "cover_fist": float((ids == 4).sum()) / tot,
           "ik": {"wrist_flexion": round(sol["flex"], 1), "wrist_deviation": round(sol["dev"], 1),
                  "lag_scale": round(sol["lag_scale"], 3), "shoulder_clamped": bool(sol["clamped"]),
                  "shoulder_to_wrist": round(sol["reach"], 4),
                  "lag": None if sol["lag"] is None else [round(float(v), 4) for v in sol["lag"]]},
           "points": {}}
    off = sol.get("off")
    if off is not None:
        out["off_ik"] = {"grip_y_px": round(off["grip_y"], 3), "wanted_grip_y_px": round(off["wanted_y"], 3),
                         "slid": bool(off["slid"]), "hold": round(off["hold"], 3),
                         "wrist_flexion": round(off["flex"], 1), "wrist_deviation": round(off["dev"], 1),
                         "shoulder_clamped": bool(off["clamped"]), "shoulder_to_wrist": round(off["reach"], 4)}
        out["cover_off_fist"] = float((ids == 7).sum()) / tot
    for name, P in pts.items():
        xy, on, w = screen_info(P)
        out["points"][name] = {"xy": None if xy is None else [round(xy[0], 1), round(xy[1], 1)],
                               "on_screen": bool(on), "depth": round(float(w), 3),
                               "behind_near_plane": xy is None, "under_hud": under_hud(xy) if on else False}
    hb = HUD_BOXES[0]
    sx, sy = frame.W / GAME_W, frame.H / GAME_H
    hud = np.zeros_like(ids, bool)
    hud[int(hb[1] * sy):int(hb[3] * sy), int(hb[0] * sx):int(hb[2] * sx)] = True
    fist_px = (ids == 4).sum()
    out["fist_visible_fraction_outside_hud"] = float(((ids == 4) & ~hud).sum() / fist_px) if fist_px else 0.0
    return out


def fmt_point(p):
    if p["xy"] is None:
        return "behind camera"
    s = f"({p['xy'][0]:.0f},{p['xy'][1]:.0f})"
    if not p["on_screen"]:
        s += " OFF"
    elif p["under_hud"]:
        s += " HUD"
    return s


# ============================================================================ rendering entry points
def render_image(scene, move, tick, W, H, ss=2, lag=True, trail=True, marks=True, hud=True):
    fr = Frame(W * ss, H * ss)
    fr.backdrop()
    pose, sol, M = scene.draw(fr, move, tick, lag=lag, trail=trail)
    im = Image.fromarray((np.clip(fr.img, 0, 1) * 255).astype(np.uint8))
    if ss > 1:
        im = im.resize((W, H), Image.BOX)
    pts = scene.points(M, sol)
    draw_frame_overlays(im, W, H, pts, marks=marks, hud=hud)
    return im, fr, pose, sol, pts


def tick_list(a, move):
    if a.key_ticks:
        return move.capture_ticks()
    if a.all_keys:
        return [(f"key{i}", k[0]) for i, k in enumerate(move.keys)]
    return [("", float(x)) for x in a.ticks.split(",") if x.strip()]


def build_sheet(scene, a):
    t0 = time.time()
    moves = list(range(1, len(scene.rig.moves) + 1)) if a.move == "all" else [int(x) for x in a.move.split(",")]
    cw = a.cell
    ch = round(cw * GAME_H / GAME_W)
    rows, report = [], {"weapon": a.weapon, "rig": scene.rig_path.name, "rig_sha256": scene.rig_sha,
                        "geometry": scene.geo_path.name, "skin": scene.skin_label, "arms": scene.arms,
                        "lag": not a.no_lag, "frames": [], "loud": FAIL}
    for n in moves:
        mv = scene.move(n)
        cells = []
        for key, tick in tick_list(a, mv):
            im, fr, pose, sol, pts = render_image(scene, mv, tick, cw, ch, ss=a.ss, lag=not a.no_lag,
                                                  trail=not a.no_trail, marks=not a.no_marks)
            m = measure(scene, fr, pts, sol, mv, tick, pose)
            m["key"] = key
            report["frames"].append(m)
            p = m["points"]
            l1, l2 = pose.describe()
            lines = [f"m{n} {mv.short} · t {tick:g}" + (f" [{key}]" if key else ""), l1, l2,
                     f"grip {fmt_point(p['grip_center'])} tip {fmt_point(p['head_tip'])} "
                     f"fist {m['fist_visible_fraction_outside_hud'] * 100:.0f}% visible"
                     + (f" off-hand {fmt_point(p['off_hand_grip'])}" if "off_hand_grip" in p else ""),
                     f"cover weapon {m['cover_weapon'] * 100:.1f}% arm {m['cover_arm'] * 100:.1f}% · wrist flex "
                     f"{sol['flex']:.0f} dev {sol['dev']:.0f} · lag {sol['lag_scale'] * 100:.0f}%"
                     + (" · SHOULDER CLAMPED" if sol["clamped"] else "")]
            if "off_ik" in m:
                o = m["off_ik"]
                lines.append(f"{pose.describe_off()} · held at y {o['grip_y_px']:.1f}"
                             + (f" (SLID from {o['wanted_grip_y_px']:.1f})" if o["slid"] else "")
                             + f" · wrist flex {o['wrist_flexion']:.0f} dev {o['wrist_deviation']:.0f}"
                             + (" · OFF SHOULDER CLAMPED" if o["shoulder_clamped"] else ""))
            elif scene.rig.off_hand is not None:
                lines.append("off hand: " + ("not drawn (off-hand slot occupied)" if a.off_hand_occupied
                                             else f"released ({pose.describe_off()})"))
            cells.append(sheets.labelled(im, lines, cw))
            pr = (f"m{n} t {tick:6g} {key:13s} grip {fmt_point(p['grip_center']):>14s}  tip {fmt_point(p['head_tip']):>14s}"
                  + (f"  off-hand {fmt_point(p['off_hand_grip']):>14s}" if "off_hand_grip" in p else "")
                  + f"  fist {fmt_point(p['fist']):>14s}  cover weapon {m['cover_weapon'] * 100:5.1f}% arm "
                  f"{m['cover_arm'] * 100:5.1f}%  tip on screen {'yes' if p['head_tip']['on_screen'] else 'NO'}"
                  f"  fist on screen {'yes' if p['fist']['on_screen'] else 'NO'}"
                  f"{' (under HUD)' if p['fist']['under_hud'] else ''}"
                  f" ({m['fist_visible_fraction_outside_hud'] * 100:.0f}% of the drawn fist outside the HUD)")
            if "off_ik" in m:
                o = m["off_ik"]
                pr += (f"  off fist {fmt_point(p['off_fist'])} at y {o['grip_y_px']:.2f}"
                       + (f" SLID from {o['wanted_grip_y_px']:.2f}" if o["slid"] else "")
                       + f" flex {o['wrist_flexion']:.0f} dev {o['wrist_deviation']:.0f}"
                       + (" OFF SHOULDER CLAMPED" if o["shoulder_clamped"] else ""))
            for name in p:
                if name not in ("grip_center", "head_tip", "off_hand_grip", "fist", "off_fist", "head_base"):
                    pr += f"  {name} {fmt_point(p[name])}"
            print(pr)
        rows.append(cells)
    title = [f"first-person rig preview · {a.weapon} · rig {scene.rig_path.name} (sha {scene.rig_sha[:10]}) · "
             f"geometry {scene.geo_path.name} · model {scene.model.name}",
             f"hand pass FOV 70, 960x540 frame, still player, freeze-probe semantics (no breathing/hit-stop/blend) · "
             f"arm lag {'off' if a.no_lag else 'on'} · {scene.arms} arm, skin {scene.skin_label} · "
             "red = grip centre, cyan = blade tip, orange = fist centre, magenta = off-hand grip (contract), "
             "gold = off fist centre, yellow = --marker; "
             "square at the border = off screen in that direction"]
    title += [f"!!! {m}" for m in FAIL[:5]]
    sheet = sheets.grid(rows, title)
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    out.with_suffix(".json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    print(f"wrote {out} ({sheet.width}x{sheet.height}) + {out.with_suffix('.json').name} in {time.time() - t0:.2f}s")
    return 0


# ============================================================================ validation against a capture set
def capture_background(frames):
    A_ = np.stack([np.asarray(f, np.int16) for f in frames])
    r, g, b = A_[..., 0], A_[..., 1], A_[..., 2]
    ok = ((g > r + 6) & (g > b + 20)) | ((b >= 250) & (b > r + 40) & (g > r + 20))
    bg = np.median(A_, 0).astype(np.float32)
    with np.errstate(invalid="ignore"), __import__("warnings").catch_warnings():
        __import__("warnings").simplefilter("ignore")
        for c in range(3):
            X = np.where(ok, A_[..., c].astype(np.float32), np.nan)
            med = np.nanmedian(X, 0)
            bg[..., c] = np.where(np.isnan(med), bg[..., c], med)
    return bg


def hud_mask(W=GAME_W, H=GAME_H):
    m = np.zeros((H, W), bool)
    for x0, y0, x1, y1 in HUD_BOXES:
        m[y0:y1, x0:x1] = True
    return m


def shift_fit(game, rend, box, search=24):
    """Shift (dx, dy) of the render mask that best matches the game mask inside box: (dx, dy, iou0, iou)."""
    x0, y0, x1, y1 = box
    g = game[y0:y1, x0:x1]
    best = (0, 0, -1.0)
    pad = search
    Rp = np.zeros((rend.shape[0] + 2 * pad, rend.shape[1] + 2 * pad), bool)
    Rp[pad:-pad, pad:-pad] = rend
    iou0 = None
    for dy in range(-search, search + 1):
        for dx in range(-search, search + 1):
            r = Rp[y0 + pad - dy:y1 + pad - dy, x0 + pad - dx:x1 + pad - dx]
            u = (g | r).sum()
            iou = (g & r).sum() / u if u else 0.0
            if dx == 0 and dy == 0:
                iou0 = iou
            if iou > best[2] + 1e-9 or (abs(iou - best[2]) <= 1e-9 and dx * dx + dy * dy < best[0] ** 2 + best[1] ** 2):
                best = (dx, dy, iou)
    return best[0], best[1], iou0, best[2]


def compare_capture(scene, a):
    t0 = time.time()
    cap = Path(a.compare_capture)
    man = json.loads((cap / "manifest.json").read_text(encoding="utf-8"))
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    cap_sha = man.get("source", {}).get("files", {}).get("rig", {}).get("sha256")
    if cap_sha and cap_sha != scene.rig_sha:
        loud(f"capture {cap.name} was made with rig sha {cap_sha[:10]}, this rig is {scene.rig_sha[:10]}: "
             "render the rig of that commit (git show <commit>:<rig path> > file; --rig file)")
    fp = [f for f in man["frames"] if f["view"] == "fp"]
    bg = capture_background([Image.open(cap / f["file"]).convert("RGB") for f in fp])
    hud = hud_mask()
    want = None
    if a.pairs:
        want = {(int(s.split(":")[0]), s.split(":")[1]) for s in a.pairs.split(",")}
    summary = []
    for f in fp:
        if want is not None and (f["move"], f["key"]) not in want:
            continue
        mv = scene.move(f["move"])
        tick = float(f["tick"])
        game = Image.open(cap / f["file"]).convert("RGB")
        G = np.asarray(game, np.float32)
        gmask = (np.abs(G - bg).sum(-1) > a.key_threshold) & ~hud
        # 1x render for masks: the game rasterises at 1x without AA.
        fr = Frame(GAME_W, GAME_H)
        fr.backdrop()
        pose, sol, M = scene.draw(fr, mv, tick, lag=not a.no_lag, trail=True)
        pts = scene.points(M, sol)
        rmask = (fr.id > 0) & ~hud
        trail_only = (fr.trail > 0.05) & (fr.id == 0)
        valid = ~trail_only
        g_, r_ = gmask & valid, rmask & valid
        union = (g_ | r_).sum()
        xor = float((g_ ^ r_).sum() / union) if union else 0.0
        # local residuals
        res = {"move": f["move"], "key": f["key"], "tick": tick, "silhouette_xor_over_union": round(xor, 4),
               "game_px": int(g_.sum()), "render_px": int(r_.sum())}
        for name, tagsel, half in (("tip", None, 34), ("fist", 4, 40), ("grip", None, 34)):
            P = pts["head_tip" if name == "tip" else "fist" if name == "fist" else "grip_center"]
            xy, on, _ = screen_info(P)
            if not on or under_hud(xy):
                res[f"{name}_offset_px"] = None
                continue
            x, y = int(round(xy[0])), int(round(xy[1]))
            box = (max(0, x - half), max(0, y - half), min(GAME_W, x + half), min(GAME_H, y + half))
            dx, dy, i0, i1 = shift_fit(g_, r_, box)
            res[f"{name}_offset_px"] = [dx, dy]
            res[f"{name}_iou"] = [round(float(i0), 3), round(float(i1), 3)]
        summary.append(res)
        # visual: 2x supersampled render without marks, with marks copy
        rend, _, _, _, _ = render_image(scene, mv, tick, GAME_W, GAME_H, ss=2, lag=not a.no_lag, marks=True, hud=False)
        blend = Image.blend(game, render_image(scene, mv, tick, GAME_W, GAME_H, ss=2, lag=not a.no_lag,
                                               marks=False, hud=False)[0], 0.5)
        diff = np.full((GAME_H, GAME_W, 3), 24, np.uint8)
        diff[g_ & r_] = (200, 200, 200)
        diff[g_ & ~r_] = (230, 40, 40)
        diff[r_ & ~g_] = (40, 210, 60)
        diff[trail_only] = np.maximum(diff[trail_only], 60)
        diff[hud] = (50, 50, 70)
        diff = Image.fromarray(diff)
        d = ImageDraw.Draw(diff)
        for name in ("head_tip", "fist", "grip_center"):
            xy, on, _ = screen_info(pts[name])
            if on:
                d.ellipse((xy[0] - 5, xy[1] - 5, xy[0] + 5, xy[1] + 5), outline=MARK_COL[name], width=2)
        cw = 480

        def off(k):
            v = res.get(f"{k}_offset_px")
            return "n/a" if v is None else f"{v[0]:+d},{v[1]:+d} px"

        cells = [sheets.labelled(game, [f"game · m{f['move']} {mv.short} · {f['key']} · t {tick:g}",
                                           "freeze probe still (capture set " + cap.name + ")"], cw),
                 sheets.labelled(rend, ["offline render (tools.combat_preview fp)",
                                           "red grip · cyan tip · orange fist centre"], cw),
                 sheets.labelled(blend, ["50 % overlay", ""], cw),
                 sheets.labelled(diff, [f"silhouette XOR/union {xor:.3f}",
                                           f"tip {off('tip')} · fist {off('fist')} · grip {off('grip')}"
                                           " (render→game shift; n/a = off screen/under HUD)",
                                           "grey both · red game only · green render only · dim = trail-only (excluded)"
                                           ], cw)]
        sheet = sheets.grid([cells[:2], cells[2:]],
                               [f"first-person validation · {scene.weapon_info['weapon']['item']} · m{f['move']} "
                                f"{f['key']} t {tick:g} · arm lag {'off' if a.no_lag else 'on'}",
                                "game | render | overlay | silhouette diff (HUD excluded; game keyed against the "
                                "median background of all fp stills of the set)"])
        sheet.save(out / f"m{f['move']}_{f['key']}{'_nolag' if a.no_lag else ''}.png")
        print(f"m{f['move']} {f['key']:13s} t {tick:5g}: XOR/union {xor:.3f}  tip {off('tip'):>12s}  "
              f"fist {off('fist'):>12s}  grip {off('grip'):>12s}")
    (out / f"summary{'_nolag' if a.no_lag else ''}.json").write_text(json.dumps(
        {"capture": cap.name, "rig_sha256": scene.rig_sha, "capture_rig_sha256": cap_sha, "lag": not a.no_lag,
         "skin": scene.skin_label, "frames": summary}, indent=1), encoding="utf-8")
    print(f"wrote {len(summary)} pairs to {out} in {time.time() - t0:.1f}s")
    return 0


# ============================================================================ main
def main(argv=None):
    ap = argparse.ArgumentParser(prog="python3 -m tools.combat_preview fp", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--weapon", required=True, help="weapon id (data/<ns>/combat/weapon/<path>.json)")
    ap.add_argument("--root", action="append", default=[],
                    help="resources root (repeatable, first wins; default src/main/resources of this repository)")
    ap.add_argument("--rig", help="first-person rig JSON overriding the weapon file's first_person_rig")
    ap.add_argument("--geometry", help="geometry contract JSON overriding the weapon file's")
    ap.add_argument("--model", help="item model id/path overriding myvillage:item/<item>")
    ap.add_argument("--move", default="1", help="1-based move number(s), comma list, or 'all'")
    sel = ap.add_mutually_exclusive_group()
    sel.add_argument("--ticks", default="0", help="comma list of ticks (fractional allowed)")
    sel.add_argument("--all-keys", action="store_true", help="every key tick of the rig's move")
    sel.add_argument("--key-ticks", action="store_true", help="the capture tool's five ticks per move")
    ap.add_argument("--marker", action="append", default=[],
                    help="x,y,z in item-model px, or a point name of the geometry contract; repeatable")
    ap.add_argument("--main-arm", choices=("right", "left"), default="right")
    ap.add_argument("--arms", choices=("slim", "wide"), help="arm width (default: the skin's; capture skin is slim)")
    ap.add_argument("--skin", default="offline:CaptureDev",
                    help="placeholder | default skin name (zuri, steve, ...) | offline:<username> | file.png "
                         "(default offline:CaptureDev = the capture client's default skin, slim zuri)")
    ap.add_argument("--no-sleeve", dest="sleeve", action="store_false", help="skip the sleeve layer")
    ap.add_argument("--no-lag", action="store_true", help="drop the presentation arm lag")
    ap.add_argument("--off-hand-occupied", action="store_true",
                    help="as with an item in the off hand: the rig's off arm is not drawn")
    ap.add_argument("--no-trail", action="store_true")
    ap.add_argument("--no-marks", action="store_true")
    ap.add_argument("--cell", type=int, default=640, help="frame width in the sheet (16:9)")
    ap.add_argument("--ss", type=int, default=2, help="supersampling")
    ap.add_argument("--vanilla-jar", default=str(pim.VANILLA_JAR))
    ap.add_argument("--no-vanilla", action="store_true")
    ap.add_argument("--compare-capture", help="capture set dir (tools/combat_capture): write game|render|overlay|diff "
                                              "pairs for its fp stills into --out (a directory)")
    ap.add_argument("--pairs", help="with --compare-capture: only these move:key pairs, e.g. 1:idle,2:contact")
    ap.add_argument("--key-threshold", type=float, default=36.0, help="game keying threshold (sum of |RGB diff|)")
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)
    if not a.root:
        a.root = [str(DEFAULT_ROOT)]
    note_missing_jar(a.vanilla_jar, a.no_vanilla)
    scene = Scene(a)
    rc = compare_capture(scene, a) if a.compare_capture else build_sheet(scene, a)
    if FAIL:
        print(f"!!! {len(FAIL)} loud warning(s) above (also in the sheet header and the JSON)", file=sys.stderr)
    return rc
