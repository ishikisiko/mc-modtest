#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the Qingfeng third-person PAL animations (sword_combat.json).

The five combo moves are authored as a pose table keyed by *server tick* and
phase (guard, anticipation, coil, contact, sweep, through, hold, recovery).
Each key stores readable intent instead of raw Euler soup:

* ``body``   whole-figure rotation in PAL degrees (x+ lean forward, y+ turn
  right, z+ tilt left); PAL applies it to the whole model around the hips.
* ``pos``    whole-figure offset (x, z) in pixels; z < 0 moves the figure
  forward (PlayerRendererMixin.applyBodyTransforms translates +posZ/16 in a
  frame whose +Z is behind the player).  The hip drop (pos y) is computed from
  the stance so that both feet stay on the ground.
* ``stance`` (front foot, splay degrees, stance direction degrees).  The legs
  are solved so the front foot points ``splay`` degrees forward along the
  stance direction and the back foot the same amount backward, in the world,
  whatever the body turn is.  That is the leg counter-rotation that keeps the
  feet planted while the hips turn.
* ``rarm`` / ``larm`` world direction of each arm as (yaw, elevation) in
  degrees relative to the action facing (yaw+ = player's right).
* ``item``   raw PAL ``right_item`` rotation that points the blade along
  ``blade`` = (yaw, elevation); the forward-kinematics self-check verifies it.

Head and torso are derived: the head counter-rotates so the eyes stay on the
target and the chest (``torso``) adds a small extra twist in the direction of
the body turn, capped at 8 degrees because PAL does not carry the arms with a
torso yaw.

Run ``python3 tools/gen_sword_pal_anims.py`` to regenerate the JSON, or
``--check`` to verify that the committed file matches and all self-checks
pass.  ``--report`` prints the forward-kinematics measurements per key.
The script is deterministic and uses only the Python standard library.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/myvillage/player_animations/sword_combat.json"
FORMAT_VERSION = "1.8.0"
TICKS_PER_SECOND = 20

# Every easing name below exists in PAL 1.1.4 EasingType (lower-case names).  In PAL the easing
# stored on a keyframe shapes the segment that ARRIVES at that keyframe (AnimationLoader
# .buildKeyframeStack builds Keyframe(prev -> this, this.easing)).
PAL_EASINGS = {
    "linear", "easeinsine", "easeoutsine", "easeinoutsine", "easeinquad", "easeoutquad",
    "easeinoutquad", "easeincubic", "easeoutcubic", "easeinoutcubic", "easeinquart",
    "easeoutquart", "easeinoutquart",
}
STRIKE_EASINGS = {"easeoutcubic", "easeoutquart", "easeoutquad"}
RECOVERY_EASINGS = {"easeinoutsine", "easeinoutquad", "easeinoutcubic"}

BONE_ORDER = ("body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg", "right_item")
REQUIRED_BONES = ("body", "head", "right_arm", "left_arm", "right_leg", "left_leg")

LEG_LENGTH_PX = 12.0 * 0.9375  # vanilla leg length after PlayerRenderer's 0.9375 scale
HEAD_YAW_LIMIT = 70.0
TORSO_TWIST_LIMIT = 8.0


@dataclass(frozen=True)
class Key:
    tick: int
    phase: str
    easing: str
    body: tuple[float, float, float]
    stance: tuple[str, float, float]
    rarm: tuple[float, float]
    larm: tuple[float, float]
    item: tuple[float, float, float]
    blade: tuple[float, float]
    pos: tuple[float, float] = (0.0, 0.0)


@dataclass(frozen=True)
class Move:
    animation_id: str
    kind: str  # "thrust" or "cut"
    total: int
    active_start: int
    active_end: int
    chain_tick: int
    step_tick: int
    keys: tuple[Key, ...] = field(default_factory=tuple)


def key(tick, phase, easing, body, stance, rarm, larm, item, blade, pos=(0.0, 0.0)) -> Key:
    return Key(tick, phase, easing, tuple(body), tuple(stance), tuple(rarm), tuple(larm),
               tuple(item), tuple(blade), tuple(pos))


# ---------------------------------------------------------------------------------------------
# Pose table.  Directions in the player's frame: yaw 0 = action facing, +90 = player's right,
# -90 = player's left; elevation + = up.  Cut directions follow the server hitboxes:
# 横 sweeps the player's left -> right, 撩 rises right-low -> left-high, 斜 descends
# left-high -> right-low.
# ---------------------------------------------------------------------------------------------

# Guard: slightly bladed stance (right foot forward, right shoulder a little forward), sword
# low-centre with the tip at the opponent's throat, left hand 剑指 held out low-left.
GUARD = dict(body=(4, -15, 0), stance=("R", 24, 0), rarm=(12, -45), larm=(-50, -45),
             item=(9, -4, -22), blade=(-3, 25))
BREATH = dict(body=(6, -14, 0), stance=("R", 25, 0), rarm=(12, -43), larm=(-50, -43),
              item=(9, -4, -22), blade=(-3, 27))


def guard(tick: int, phase: str, easing: str) -> Key:
    return key(tick, phase, easing, **GUARD)


MOVES: tuple[Move, ...] = (
    Move("basic_sword_01_thrust", "thrust", 11, 3, 4, 7, 2, (
        guard(0, "guard", "linear"),
        key(1, "anticipation", "easeinoutsine", (-2, 15, 0), ("R", 28, 0), (-165, -72), (-8, 0),
            (-32, -16, -4), (0, 5)),
        key(2, "coil", "linear", (-3, 19, 0), ("R", 28, 0), (-161, -72), (-8, 2),
            (-36, -14, 14), (1, 6)),
        key(3, "contact", "easeoutcubic", (8, -45, 0), ("R", 38, 0), (14, 2), (-125, 5),
            (81, -12, -3), (10, 0), pos=(0, -1)),
        key(4, "through", "easeoutsine", (10, -50, 0), ("R", 40, 0), (13, 0), (-130, 6),
            (80, 17, -2), (10, -1), pos=(0, -1.5)),
        key(7, "hold", "linear", (10, -52, 0), ("R", 40, 0), (13, -2), (-131, 3),
            (80, 17, -2), (10, -3), pos=(0, -1.5)),
        guard(11, "recovery", "easeinoutsine"),
    )),
    Move("basic_sword_02_horizontal_cut", "cut", 13, 4, 6, 8, 3, (
        guard(0, "guard", "linear"),
        key(2, "anticipation", "easeinoutsine", (4, -45, 0), ("R", 30, 10), (-40, 5), (-100, -30),
            (17, -54, -63), (-120, 10)),
        key(3, "coil", "linear", (5, -50, 0), ("R", 32, 10), (-45, 6), (-102, -30),
            (13, -59, -65), (-128, 10)),
        key(4, "contact", "easeoutcubic", (6, -25, 0), ("R", 36, 10), (-35, 0), (-120, -20),
            (71, -32, -17), (-55, 0)),
        key(5, "sweep", "linear", (7, 10, 0), ("R", 38, 10), (10, -3), (-140, -10),
            (68, -66, -4), (0, -2)),
        key(6, "through", "easeoutsine", (8, 45, 0), ("R", 40, 10), (75, -5), (-170, 0),
            (99, -76, 1), (95, -5)),
        key(9, "hold", "linear", (8, 48, 0), ("R", 40, 10), (78, -6), (-172, 0),
            (99, -76, 1), (98, -6)),
        guard(13, "recovery", "easeinoutsine"),
    )),
    Move("basic_sword_03_rising_cut", "cut", 15, 5, 7, 10, 4, (
        guard(0, "guard", "linear"),
        key(3, "anticipation", "easeinoutsine", (12, 36, 0), ("R", 38, 0), (140, -50), (-40, -10),
            (55, 42, 8), (160, -30)),
        key(4, "coil", "linear", (13, 38, 0), ("R", 38, 0), (142, -50), (-40, -8),
            (54, 48, 6), (162, -30)),
        key(5, "contact", "easeoutcubic", (6, 5, 0), ("R", 38, 0), (40, -35), (-80, -10),
            (69, 96, 0), (50, -35)),
        key(6, "sweep", "linear", (0, -30, 0), ("R", 36, 0), (-20, 5), (-120, -20),
            (70, 63, -23), (-20, 30)),
        key(7, "through", "easeoutsine", (-6, -58, 0), ("R", 36, 0), (-102, 17), (-150, -30),
            (66, 73, -26), (-100, 45)),
        key(10, "hold", "linear", (-7, -60, 0), ("R", 36, 0), (-103, 16), (-152, -30),
            (66, 73, -26), (-101, 46)),
        guard(15, "recovery", "easeinoutsine"),
    )),
    Move("basic_sword_04_diagonal_cut", "cut", 17, 6, 8, 13, 5, (
        guard(0, "guard", "linear"),
        key(4, "anticipation", "easeinoutsine", (-6, -35, 0), ("R", 28, 0), (-80, 55), (-120, -40),
            (74, -19, -25), (-115, 45)),
        key(5, "coil", "linear", (-7, -37, 0), ("R", 28, 0), (-81, 56), (-122, -40),
            (70, -28, -23), (-117, 46)),
        key(6, "contact", "easeoutcubic", (8, -10, 0), ("R", 36, 0), (-30, 35), (-120, -20),
            (75, -8, -23), (-60, 35)),
        key(7, "sweep", "linear", (18, 15, 0), ("R", 40, 0), (10, -5), (-140, -5),
            (92, -3, -5), (5, -20)),
        key(8, "through", "easeoutsine", (24, 42, 0), ("R", 44, 0), (65, -45), (-160, 5),
            (88, -20, 23), (95, -35)),
        key(12, "hold", "linear", (25, 44, 0), ("R", 44, 0), (66, -46), (-161, 5),
            (88, -20, 23), (97, -36)),
        guard(17, "recovery", "easeinoutsine"),
    )),
    Move("basic_sword_05_lunge_thrust", "thrust", 20, 7, 9, 20, 6, (
        guard(0, "guard", "linear"),
        key(4, "anticipation", "easeinoutsine", (-8, 38, 0), ("R", 30, 0), (-142, -70), (-5, 5),
            (-32, -32, 4), (5, 5), pos=(0, 1)),
        key(6, "coil", "linear", (-9, 42, 0), ("R", 32, 0), (-138, -70), (-5, 6),
            (-39, -25, 32), (5, 6), pos=(0, 1.5)),
        key(7, "contact", "easeoutquart", (20, -55, 0), ("R", 45, 0), (18, 0), (-150, 10),
            (76, -32, -5), (12, 0), pos=(0, -6)),
        key(9, "through", "easeoutsine", (22, -58, 0), ("R", 46, 0), (17, -1), (-152, 10),
            (79, 23, -6), (12, -2), pos=(0, -7)),
        key(13, "hold", "linear", (22, -58, 0), ("R", 46, 0), (17, -3), (-153, 8),
            (79, 23, -6), (12, -4), pos=(0, -7)),
        guard(20, "recovery", "easeinoutsine"),
    )),
)

READY_IDLE_ID = "sword_ready_idle"
READY_IDLE_LENGTH_TICKS = 24  # 1.2 s loop
MODE_ENTER_ID = "sword_mode_enter"
MODE_ENTER_LENGTH_TICKS = 16  # 0.8 s


def idle_keys() -> tuple[Key, ...]:
    return (
        guard(0, "guard", "linear"),
        key(12, "breath", "easeinoutsine", **BREATH),
        guard(24, "guard", "easeinoutsine"),
    )


def enter_keys() -> tuple[Key, ...]:
    # Draw from the vanilla pose, raise the blade in a short salute, settle into the guard.
    return (
        key(0, "vanilla", "linear", (0, 0, 0), ("R", 0, 0), (0, -90), (0, -90), (0, 0, 0), (0, -11)),
        key(4, "salute", "easeoutcubic", (-4, 20, 0), ("R", 14, 0), (10, 0), (20, 20),
            (0, 0, 0), (6, 79)),
        key(10, "settle", "easeinoutsine", (6, -20, 0), ("R", 26, 0), (12, -40), (-55, -45),
            (9, -4, -22), (-4, 29)),
        guard(16, "guard", "easeinoutsine"),
    )


# ---------------------------------------------------------------------------------------------
# Small 3D helpers (pure python, deterministic).
# ---------------------------------------------------------------------------------------------

def _rad(value: float) -> float:
    return math.radians(value)


def _mat3(axis: str, degrees: float) -> list[list[float]]:
    c, s = math.cos(_rad(degrees)), math.sin(_rad(degrees))
    if axis == "x":
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == "y":
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mm3(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _zyx3(x: float, y: float, z: float):
    return _mm3(_mat3("z", z), _mm3(_mat3("y", y), _mat3("x", x)))


def world_direction(yaw: float, elevation: float) -> tuple[float, float, float]:
    """(right, up, forward) unit vector."""
    return (math.sin(_rad(yaw)) * math.cos(_rad(elevation)), math.sin(_rad(elevation)),
            math.cos(_rad(yaw)) * math.cos(_rad(elevation)))


def _limb_candidates(body_rot, direction):
    """PAL limb rotations (x, y) with z = 0 that point a hanging limb along a world direction.

    Model space: +Y down the limb, -Z forward, -X the player's right.  The limb vector is
    body * Ry(y) * Rx(x) * (0, 1, 0) = body * (sin x sin y, cos x, sin x cos y).
    """
    d = [-direction[0], -direction[1], -direction[2]]
    b = _zyx3(*body_rot)
    u = [sum(b[k][i] * d[k] for k in range(3)) for i in range(3)]
    cos_x = max(-1.0, min(1.0, u[1]))
    result = []
    for sign in (-1.0, 1.0):
        x = sign * math.degrees(math.acos(cos_x))
        sx = math.sin(_rad(x))
        y = math.degrees(math.atan2(u[0] / sx, u[2] / sx)) if abs(sx) > 1e-9 else 0.0
        result.append((x, y))
    return result


def limb_direction(body_rot, limb_rot) -> tuple[float, float, float]:
    """World (right, up, forward) direction of a limb for PAL body and limb rotations (z = 0)."""
    x, y = limb_rot[0], limb_rot[1]
    local = (math.sin(_rad(x)) * math.sin(_rad(y)), math.cos(_rad(x)), math.sin(_rad(x)) * math.cos(_rad(y)))
    b = _zyx3(*body_rot)
    model = [sum(b[i][k] * local[k] for k in range(3)) for i in range(3)]
    return (-model[0], -model[1], -model[2])


def solve_limb(body_rot, yaw, elevation, previous=None, previous_body=None):
    """Limb rotation for a world direction.

    Without a previous key the solution with the smallest yaw is used.  Otherwise, of the
    equivalent Euler solutions, the one whose linear interpolation from the previous key stays
    closest to the direct (great-circle) path at the segment midpoint is used, so limbs never
    swing the long way round between keys.
    """
    target = world_direction(yaw, elevation)
    candidates = []
    for x, y in _limb_candidates(body_rot, target):
        for wrap in (-360.0, 0.0, 360.0):
            candidates.append((x, y + wrap))
    if previous is None:
        return min(candidates, key=lambda c: (abs(c[1]), abs(c[0])))
    start = limb_direction(previous_body, previous)
    mid_body = [(previous_body[i] + body_rot[i]) / 2.0 for i in range(3)]
    ideal = [start[i] + target[i] for i in range(3)]
    norm = math.sqrt(sum(v * v for v in ideal)) or 1.0
    ideal = [v / norm for v in ideal]

    def deviation(c):
        mid = limb_direction(mid_body, ((previous[0] + c[0]) / 2.0, (previous[1] + c[1]) / 2.0))
        dot = max(-1.0, min(1.0, sum(mid[i] * ideal[i] for i in range(3))))
        return (round(math.degrees(math.acos(dot)), 3), abs(c[0] - previous[0]) + abs(c[1] - previous[1]))

    return min(candidates, key=deviation)


def solve_stance(body_rot, front, splay, direction):
    """Legs for a planted stance, plus the hip drop (negative px) that keeps both feet grounded.

    Of the two Euler solutions per leg the one with the smaller yaw is used, so a back leg
    swings backward (x > 0) instead of swinging forward and twisting 180 degrees.
    """
    down = -(90.0 - splay)

    def untwisted(yaw):
        options = _limb_candidates(body_rot, world_direction(yaw, down))
        return min(options, key=lambda c: (abs(c[1]), c[0]))

    front_leg = untwisted(direction)
    back_leg = untwisted(direction + 180.0)
    drop = -LEG_LENGTH_PX * (1.0 - math.cos(_rad(splay)))
    right, left = (front_leg, back_leg) if front == "R" else (back_leg, front_leg)
    return right, left, drop


def _r(value: float, digits: int = 1) -> float:
    value = round(value + 0.0, digits)
    return 0.0 if value == 0 else value


def _is_guard(k: Key) -> bool:
    return (k.body, k.stance, k.rarm, k.larm, k.item, k.pos) == (
        GUARD["body"], GUARD["stance"], GUARD["rarm"], GUARD["larm"], GUARD["item"], (0.0, 0.0))


def _resolve_key(k: Key, previous) -> dict[str, list[float]]:
    right_leg, left_leg, drop = solve_stance(k.body, *k.stance)
    previous_body = None if previous is None else previous["body"]
    rarm = solve_limb(k.body, *k.rarm, previous=None if previous is None else previous["right_arm"][:2],
                      previous_body=previous_body)
    larm = solve_limb(k.body, *k.larm, previous=None if previous is None else previous["left_arm"][:2],
                      previous_body=previous_body)
    pose = {
        "body": [k.body[0], k.body[1], k.body[2]],
        "body_pos": [k.pos[0], drop, k.pos[1]],
        "torso": [0.0, max(-TORSO_TWIST_LIMIT, min(TORSO_TWIST_LIMIT, 0.15 * k.body[1])), 0.0],
        "head": [-0.6 * k.body[0], max(-HEAD_YAW_LIMIT, min(HEAD_YAW_LIMIT, -k.body[1])), 0.0],
        "right_arm": [rarm[0], rarm[1], 0.0],
        "left_arm": [larm[0], larm[1], 0.0],
        "right_leg": [right_leg[0], right_leg[1], 0.0],
        "left_leg": [left_leg[0], left_leg[1], 0.0],
        "right_item": [k.item[0], k.item[1], k.item[2]],
    }
    return {bone: [_r(v, 2 if bone == "body_pos" else 1) for v in values] for bone, values in pose.items()}


def canonical_guard() -> dict[str, list[float]]:
    return _resolve_key(guard(0, "guard", "linear"), None)


def resolve(keys: tuple[Key, ...]) -> list[dict[str, list[float]]]:
    """Turn intent keys into per-bone PAL values ({bone: [x, y, z]} plus 'body_pos').

    Guard keys always resolve to the one canonical guard pose; every other key picks the
    limb solution closest to the previous key so no channel flips between equivalent angles.
    """
    poses = []
    previous = None
    for k in keys:
        pose = canonical_guard() if _is_guard(k) else _resolve_key(k, previous)
        poses.append(pose)
        previous = pose
    return poses


# ---------------------------------------------------------------------------------------------
# JSON emission.
# ---------------------------------------------------------------------------------------------

def _timestamp(tick: int) -> str:
    return repr(round(tick / TICKS_PER_SECOND, 4))


def _vector(values: list[float], easing: str):
    vector = [int(v) if float(v).is_integer() else v for v in values]
    if easing == "linear":
        return vector
    return {"vector": vector, "easing": easing}


def build_animation(keys: tuple[Key, ...], length_ticks: int, loop: bool = False) -> dict:
    poses = resolve(keys)
    bones: dict[str, dict[str, dict]] = {}
    for bone in BONE_ORDER:
        rotation = {}
        for k, pose in zip(keys, poses):
            rotation[_timestamp(k.tick)] = _vector(pose[bone], k.easing)
        bones[bone] = {"rotation": rotation}
        if bone == "body":
            bones[bone]["position"] = {
                _timestamp(k.tick): _vector(pose["body_pos"], k.easing) for k, pose in zip(keys, poses)}
    animation: dict = {}
    if loop:
        animation["loop"] = True
    animation["animation_length"] = round(length_ticks / TICKS_PER_SECOND, 4)
    animation["bones"] = bones
    return animation


def build_document() -> dict:
    animations = {
        MODE_ENTER_ID: build_animation(enter_keys(), MODE_ENTER_LENGTH_TICKS),
        READY_IDLE_ID: build_animation(idle_keys(), READY_IDLE_LENGTH_TICKS, loop=True),
    }
    for move in MOVES:
        animations[move.animation_id] = build_animation(move.keys, move.total)
    return {"format_version": FORMAT_VERSION, "animations": animations}


def render(document: dict) -> str:
    """Stable, diff-friendly JSON: one line per bone channel."""
    lines = ["{", f'  "format_version": {json.dumps(document["format_version"])},', '  "animations": {']
    animation_items = list(document["animations"].items())
    for a_index, (name, animation) in enumerate(animation_items):
        lines.append(f"    {json.dumps(name)}: {{")
        if animation.get("loop"):
            lines.append('      "loop": true,')
        lines.append(f'      "animation_length": {json.dumps(animation["animation_length"])},')
        lines.append('      "bones": {')
        bone_items = list(animation["bones"].items())
        for b_index, (bone, channels) in enumerate(bone_items):
            lines.append(f"        {json.dumps(bone)}: {{")
            channel_items = list(channels.items())
            for c_index, (channel, frames) in enumerate(channel_items):
                body = json.dumps(frames, ensure_ascii=False, separators=(", ", ": "))
                comma = "," if c_index < len(channel_items) - 1 else ""
                lines.append(f"          {json.dumps(channel)}: {body}{comma}")
            lines.append("        }" + ("," if b_index < len(bone_items) - 1 else ""))
        lines.append("      }")
        lines.append("    }" + ("," if a_index < len(animation_items) - 1 else ""))
    lines.append("  }")
    lines.append("}")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------------------------
# Forward kinematics (mirrors PlayerRendererMixin, PlayerModelMixin.translateToHand,
# ItemInHandLayer + ItemInHandLayerMixin and the vanilla handheld thirdperson_righthand display).
# World frame of the results: (right, up, forward) in blocks from the feet, facing = +forward.
# ---------------------------------------------------------------------------------------------

def _m4(rows):
    return [list(r) for r in rows]


def _t(x, y, z):
    return _m4([[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]])


def _s(x, y, z):
    return _m4([[x, 0, 0, 0], [0, y, 0, 0], [0, 0, z, 0], [0, 0, 0, 1]])


def _r4(axis, degrees):
    m = _mat3(axis, degrees)
    return _m4([m[0] + [0], m[1] + [0], m[2] + [0], [0, 0, 0, 1]])


def _chain(*matrices):
    out = matrices[0]
    for m in matrices[1:]:
        out = [[sum(out[i][k] * m[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
    return out


def _apply(m, p):
    v = (p[0], p[1], p[2], 1.0)
    x, y, z = (sum(m[i][k] * v[k] for k in range(4)) for i in range(3))
    return (x, y, -z)  # local +Z is behind the player -> forward = -z


def skeleton(pose: dict[str, list[float]]) -> dict[str, tuple[float, float, float]]:
    bx, by, bz = pose["body"]
    px, py, pz = pose["body_pos"]
    root = _chain(_t(-px / 16, py / 16 + 0.75, pz / 16), _r4("z", bz), _r4("y", -by), _r4("x", -bx),
                  _t(0, -0.75, 0), _s(-1, -1, 1), _s(0.9375, 0.9375, 0.9375), _t(0, -1.501, 0))

    def part(bone, pivot):
        x, y, z = pose[bone]
        return _chain(root, _t(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16), _r4("z", z), _r4("y", y), _r4("x", x))

    right_arm = part("right_arm", (-5, 2, 0))
    left_arm = part("left_arm", (5, 2, 0))
    right_leg = part("right_leg", (-1.9, 12, 0))
    left_leg = part("left_leg", (1.9, 12, 0))
    ix, iy, iz = pose["right_item"]
    item = _chain(right_arm, _r4("x", -90), _r4("y", 180), _t(1 / 16, 0.125, -0.625),
                  _r4("z", -iy), _r4("y", -iz), _r4("x", -ix),
                  _t(0, 0.25, 0.03125), _r4("y", -90), _r4("z", 55), _s(0.85, 0.85, 0.85), _t(-0.5, -0.5, -0.5))
    return {
        "hip": _apply(root, (0, 0.75, 0)),
        "right_shoulder": _apply(right_arm, (0, 0, 0)),
        "right_hand": _apply(right_arm, (-1 / 16, 10 / 16, 0)),
        "left_hand": _apply(left_arm, (1 / 16, 10 / 16, 0)),
        "right_foot": _apply(right_leg, (0, 12 / 16, 0)),
        "left_foot": _apply(left_leg, (0, 12 / 16, 0)),
        "grip": _apply(item, (4.5 / 16, 4.5 / 16, 0.5)),
        "tip": _apply(item, (15 / 16, 15.3 / 16, 0.5)),
    }


def direction_angles(a, b) -> tuple[float, float]:
    d = [b[i] - a[i] for i in range(3)]
    return (math.degrees(math.atan2(d[0], d[2])), math.degrees(math.atan2(d[1], math.hypot(d[0], d[2]))))


def angle_between(a, b) -> float:
    va, vb = world_direction(*a), world_direction(*b)
    dot = max(-1.0, min(1.0, sum(va[i] * vb[i] for i in range(3))))
    return math.degrees(math.acos(dot))


# PAL easing functions (EasingType: easeIn = f, easeOut = 1 - f(1 - t), easeInOut split).
def _base(name: str):
    if name.endswith("sine"):
        return lambda t: 1.0 - math.cos(t * math.pi / 2.0)
    if name.endswith("quad"):
        return lambda t: t * t
    if name.endswith("cubic"):
        return lambda t: t * t * t
    if name.endswith("quart"):
        return lambda t: t ** 4
    return lambda t: t


def ease(name: str, t: float) -> float:
    if name == "linear":
        return t
    f = _base(name)
    if name.startswith("easeinout"):
        return f(t * 2.0) / 2.0 if t < 0.5 else 1.0 - f((1.0 - t) * 2.0) / 2.0
    if name.startswith("easeout"):
        return 1.0 - f(1.0 - t)
    return f(t)


def sample(keys: tuple[Key, ...], poses, tick: float) -> dict[str, list[float]]:
    if tick <= keys[0].tick:
        return poses[0]
    for i in range(1, len(keys)):
        if tick <= keys[i].tick:
            span = keys[i].tick - keys[i - 1].tick
            u = ease(keys[i].easing, (tick - keys[i - 1].tick) / span)
            a, b = poses[i - 1], poses[i]
            return {bone: [a[bone][j] + (b[bone][j] - a[bone][j]) * u for j in range(3)] for bone in a}
    return poses[-1]


# ---------------------------------------------------------------------------------------------
# Self-checks.
# ---------------------------------------------------------------------------------------------

def _key_at(move: Move, phase: str) -> int:
    for index, k in enumerate(move.keys):
        if k.phase == phase:
            return index
    raise AssertionError(f"{move.animation_id}: missing {phase} key")


def measure(move: Move) -> dict:
    poses = resolve(move.keys)
    info = {"poses": poses, "skeletons": [skeleton(p) for p in poses]}
    samples = []
    tick = 0.0
    while tick <= move.total + 1e-9:
        pose = sample(move.keys, poses, tick)
        samples.append((tick, pose, skeleton(pose)))
        tick += 0.25
    info["samples"] = samples
    return info


def check_keys(name: str, keys: tuple[Key, ...]) -> list[str]:
    """Checks shared by every animation: grounded feet, blade intent, smooth limbs and blade."""
    errors: list[str] = []
    poses = resolve(keys)
    skeletons = [skeleton(p) for p in poses]

    def fail(message):
        errors.append(f"{name}: {message}")

    # Feet stay planted (lowest foot on the ground) at every key.
    for k, sk in zip(keys, skeletons):
        low = min(sk["right_foot"][1], sk["left_foot"][1]) * 16
        if abs(low) > 0.6:
            fail(f"feet off the ground by {low:.2f} px at tick {k.tick}")

    # The authored blade direction is what the rig actually produces.
    for k, sk in zip(keys, skeletons):
        actual = direction_angles(sk["grip"], sk["tip"])
        if angle_between(actual, k.blade) > 8.0:
            fail(f"blade at tick {k.tick} points {actual[0]:.0f}/{actual[1]:.0f}, intended {k.blade}")

    # No Euler flip inside a segment (a limb channel sweeping more than 190 degrees).
    for i in range(1, len(poses)):
        for bone in ("right_arm", "left_arm", "right_item", "right_leg", "left_leg"):
            jump = max(abs(poses[i][bone][j] - poses[i - 1][bone][j]) for j in range(3))
            if jump > 190.0:
                fail(f"{bone} jumps {jump:.0f} deg between ticks {keys[i - 1].tick} and {keys[i].tick}")

    # Limbs follow the short way between keys (Euler midpoint close to the great-circle midpoint).
    for i in range(1, len(poses)):
        a, b = poses[i - 1], poses[i]
        mid_body = [(a["body"][j] + b["body"][j]) / 2.0 for j in range(3)]
        for bone in ("right_arm", "left_arm", "right_leg", "left_leg"):
            start = limb_direction(a["body"], a[bone])
            end = limb_direction(b["body"], b[bone])
            middle = limb_direction(mid_body, [(a[bone][j] + b[bone][j]) / 2.0 for j in range(3)])
            ideal = [start[j] + end[j] for j in range(3)]
            norm = math.sqrt(sum(v * v for v in ideal))
            if norm < 1e-6:
                continue
            dot = sum(middle[j] * ideal[j] / norm for j in range(3))
            deviation = math.degrees(math.acos(max(-1.0, min(1.0, dot))))
            if deviation > 30.0:
                fail(f"{bone} swings the long way ({deviation:.0f} deg off) between ticks "
                     f"{keys[i - 1].tick} and {keys[i].tick}")

    # The blade turns smoothly: its sampled path is not much longer than the direct turn.
    for i in range(1, len(keys)):
        start_tick, end_tick = keys[i - 1].tick, keys[i].tick
        steps = max(4, (end_tick - start_tick) * 8)
        directions = []
        for s_index in range(steps + 1):
            sk = skeleton(sample(keys, poses, start_tick + (end_tick - start_tick) * s_index / steps))
            directions.append(direction_angles(sk["grip"], sk["tip"]))
        path = sum(angle_between(directions[j], directions[j + 1]) for j in range(steps))
        waste = path - angle_between(directions[0], directions[-1])
        if waste > 30.0:
            fail(f"blade wobbles {waste:.0f} deg between ticks {start_tick} and {end_tick}")

    return errors


def check_move(move: Move) -> list[str]:
    errors: list[str] = []
    name = move.animation_id
    keys = move.keys
    ticks = [k.tick for k in keys]
    info = measure(move)
    poses, skeletons = info["poses"], info["skeletons"]

    def fail(message):
        errors.append(f"{name}: {message}")

    # Structure and validator rules.
    if ticks[0] != 0 or ticks[-1] != move.total:
        fail("keys must start at tick 0 and end at the total")
    if ticks != sorted(set(ticks)):
        fail("key ticks must be strictly increasing")
    if not any(move.active_start <= t <= move.active_end for t in ticks):
        fail("no key inside the active window")
    for k in keys:
        if k.easing not in PAL_EASINGS:
            fail(f"unknown PAL easing {k.easing}")
    canonical = canonical_guard()
    if poses[0] != canonical or poses[-1] != canonical:
        fail("moves must start and end on the canonical guard pose")
    errors.extend(check_keys(name, keys))

    # Phase timing.
    contact = _key_at(move, "contact")
    through = _key_at(move, "through")
    hold = _key_at(move, "hold")
    recovery = len(keys) - 1
    if keys[contact].tick != move.active_start:
        fail(f"contact key at {keys[contact].tick}, expected activeStart {move.active_start}")
    strike_ticks = keys[contact].tick - keys[contact - 1].tick
    if strike_ticks > 2:
        fail(f"strike segment lasts {strike_ticks} ticks (> 2)")
    if keys[contact].easing not in STRIKE_EASINGS:
        fail(f"strike segment easing {keys[contact].easing} is not an ease-out")
    if keys[through].tick != move.active_end:
        fail(f"follow-through key at {keys[through].tick}, expected activeEnd {move.active_end}")
    hold_ticks = keys[hold].tick - keys[through].tick
    if not 3 <= hold_ticks <= 5:
        fail(f"follow-through hold lasts {hold_ticks} ticks (3-5)")
    for bone in ("body", "right_arm", "left_arm", "right_item", "right_leg", "left_leg", "head", "torso"):
        drift = max(abs(poses[hold][bone][j] - poses[through][bone][j]) for j in range(3))
        if drift > 5.0 + 1e-6:
            fail(f"hold drift on {bone} is {drift:.1f} deg (> 5)")
    if keys[recovery].easing not in RECOVERY_EASINGS:
        fail(f"recovery easing {keys[recovery].easing} is not ease-in-out")
    anticipation_ticks = keys[contact - 1].tick
    if anticipation_ticks < strike_ticks + 1:
        fail("anticipation must be longer than the strike")

    # Hip drop on strikes.
    for index in (contact, through, hold):
        drop = -poses[index]["body_pos"][1]
        if not 2.0 - 1e-6 <= drop <= 3.5 + 1e-6:
            fail(f"hip drop {drop:.2f} px at tick {keys[index].tick} (2-3.5)")

    # Chest twist stays within what PAL can show without detaching the arms.
    for pose in poses:
        if abs(pose["torso"][1]) > TORSO_TWIST_LIMIT + 1e-6:
            fail("torso twist exceeds the PAL-safe limit")

    body_yaws = [p["body"][1] for p in poses]
    through_sk = skeletons[through]
    hold_sk = skeletons[hold]
    if move.kind == "cut":
        turn = max(body_yaws) - min(body_yaws)
        if not 70.0 <= turn <= 110.0:
            fail(f"whole-figure turn {turn:.0f} deg peak-to-peak (70-110)")
        for index in (through, hold):
            sk = skeletons[index]
            hand_lateral = sk["right_hand"][0]
            blade_yaw, _ = direction_angles(sk["grip"], sk["tip"])
            if abs(hand_lateral) < 0.35:
                fail(f"hand only {hand_lateral:+.2f} block lateral at tick {keys[index].tick} (>= 0.35)")
            if not 80.0 <= abs(blade_yaw) <= 110.0:
                fail(f"blade yaw {blade_yaw:+.0f} at tick {keys[index].tick} (+-80..110)")
            if abs(sk["tip"][0]) < 0.6:
                fail(f"blade tip only {sk['tip'][0]:+.2f} block outside the silhouette at tick {keys[index].tick}")
    else:
        for index in (contact, through, hold):
            sk = skeletons[index]
            blade_yaw, blade_elevation = direction_angles(sk["grip"], sk["tip"])
            if poses[index]["body"][1] > -40.0:
                fail(f"thrust body is not bladed at tick {keys[index].tick}")
            if abs(blade_yaw) > 20.0 or abs(blade_elevation) > 15.0:
                fail(f"thrust blade not in line at tick {keys[index].tick}: {blade_yaw:.0f}/{blade_elevation:.0f}")
            if sk["left_hand"][0] > -0.45:
                fail(f"left 剑指 arm does not open the T-shape at tick {keys[index].tick}")
            right_dir = direction_angles(sk["right_shoulder"], sk["right_hand"])
            if abs(right_dir[1]) > 20.0:
                fail(f"thrust arm not level at tick {keys[index].tick}")
            if sk["tip"][2] < (1.9 if move.step_tick >= 6 else 1.5):
                fail(f"thrust reach {sk['tip'][2]:.2f} too short at tick {keys[index].tick}")

    # Cut directions shared with the server hitboxes.
    wind = skeletons[contact - 1]
    if name.endswith("horizontal_cut"):
        if not (wind["tip"][0] < -0.4 and hold_sk["tip"][0] > 0.6):
            fail("horizontal cut must sweep the player's left -> right")
        mid = next(s for s in info["samples"] if abs(s[0] - (move.active_start + move.active_end) / 2) < 1e-9)
        mid_yaw, _ = direction_angles(mid[2]["grip"], mid[2]["tip"])
        if abs(mid_yaw) > 25.0:
            fail(f"blade crosses the centre at yaw {mid_yaw:.0f} on the middle active tick")
    if name.endswith("rising_cut"):
        contact_sk = skeletons[contact]
        if not (contact_sk["tip"][0] > 0.4 and contact_sk["tip"][1] < 0.9):
            fail("rising cut must start right-low")
        if not (hold_sk["tip"][0] < -0.6 and hold_sk["tip"][1] > 1.8):
            fail("rising cut must finish left-high")
    if name.endswith("diagonal_cut"):
        if not (wind["tip"][0] < -0.3 and wind["tip"][1] > 2.0):
            fail("diagonal cut must wind up left-high")
        if not (hold_sk["tip"][0] > 0.6 and hold_sk["tip"][1] < 0.6):
            fail("diagonal cut must finish right-low")

    # Lunge: 弓步 and forward drive timed to the server step.
    if move.step_tick >= 6:
        coil = keys[contact - 1]
        if coil.tick != move.step_tick:
            fail("lunge must leave the coiled hold on the step tick")
        if keys[contact].pos[1] > -4.0 or keys[through].pos[1] > -4.0:
            fail("lunge must drive the hips forward (pos z <= -4 px)")
        if not any(v != 0 for p in poses for v in p["body_pos"]):
            fail("lunge needs a non-zero body position")
        coil_ticks = keys[contact - 1].tick - keys[contact - 2].tick
        if coil_ticks < 2:
            fail("the finisher needs a readable 2-tick coiled hold")
    return errors


def check_document(document: dict) -> list[str]:
    errors: list[str] = []
    animations = document.get("animations", {})
    expected = {MODE_ENTER_ID, READY_IDLE_ID, *(m.animation_id for m in MOVES)}
    if document.get("format_version") != FORMAT_VERSION:
        errors.append("format_version must be 1.8.0")
    if set(animations) != expected:
        errors.append(f"animation ids {sorted(animations)} != {sorted(expected)}")
    idle = animations.get(READY_IDLE_ID, {})
    if idle.get("loop") is not True or idle.get("animation_length") != 1.2:
        errors.append("sword_ready_idle must loop with length 1.2")
    for move in MOVES:
        animation = animations.get(move.animation_id, {})
        if abs(animation.get("animation_length", -1) - move.total / TICKS_PER_SECOND) > 1e-6:
            errors.append(f"{move.animation_id}: length must be total/20")
        bones = animation.get("bones", {})
        for bone in REQUIRED_BONES:
            times = [float(t) * TICKS_PER_SECOND for t in bones.get(bone, {}).get("rotation", {})]
            if 0.0 not in times or not any(abs(t - move.total) < 1e-6 for t in times):
                errors.append(f"{move.animation_id}:{bone} needs rotation keys at 0 and total")
            if not any(move.active_start <= t <= move.active_end for t in times):
                errors.append(f"{move.animation_id}:{bone} needs a rotation key in the active window")
    lunge = animations.get("basic_sword_05_lunge_thrust", {}).get("bones", {}).get("body", {}).get("position", {})
    if not any(isinstance(v, list) and any(x != 0 for x in v) for v in lunge.values()):
        errors.append("basic_sword_05_lunge_thrust body.position needs a non-zero plain vector")
    # Idle and mode entry end in the guard so PAL hands over without a pop.
    guard_pose = canonical_guard()
    idle_poses = resolve(idle_keys())
    if idle_poses[0] != guard_pose or idle_poses[-1] != guard_pose:
        errors.append("sword_ready_idle must start and end on the canonical guard")
    if resolve(enter_keys())[-1] != guard_pose:
        errors.append("sword_mode_enter must end on the canonical guard")
    errors.extend(check_keys(READY_IDLE_ID, idle_keys()))
    errors.extend(check_keys(MODE_ENTER_ID, enter_keys()))
    for move in MOVES:
        errors.extend(check_move(move))
    return errors


def report() -> None:
    for move in MOVES:
        info = measure(move)
        print(f"== {move.animation_id} (total {move.total}, active {move.active_start}-{move.active_end}, chain {move.chain_tick})")
        for k, pose, sk in zip(move.keys, info["poses"], info["skeletons"]):
            yaw, elevation = direction_angles(sk["grip"], sk["tip"])
            fmt = lambda v: "(%+.2f,%+.2f,%+.2f)" % v
            print(f"  t{k.tick:>2} {k.phase:<12} {k.easing:<13} body y{pose['body'][1]:+5.0f} drop{pose['body_pos'][1]:+5.2f}"
                  f" hand{fmt(sk['right_hand'])} tip{fmt(sk['tip'])} blade {yaw:+4.0f}/{elevation:+3.0f}"
                  f" lhand{fmt(sk['left_hand'])}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify the committed JSON and self-checks")
    parser.add_argument("--report", action="store_true", help="print forward-kinematics measurements")
    args = parser.parse_args(argv)
    document = build_document()
    errors = check_document(document)
    if args.report:
        report()
    if errors:
        for error in errors:
            print(f"SELF_CHECK {error}", file=sys.stderr)
        return 1
    text = render(document)
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != text:
            print(f"DRIFT {OUTPUT.relative_to(ROOT)} differs from the generator output", file=sys.stderr)
            return 1
        print("OK sword_combat.json matches the generator and passes all self-checks")
        return 0
    if not args.report:
        OUTPUT.write_text(text, encoding="utf-8")
        print(f"wrote {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
