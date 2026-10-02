#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate the third-person PAL combat animations, one file per style (sword_combat.json, ...).

Each combat style has a pose table (``POSE_TABLES``) keyed by *server tick*
and phase (guard, anticipation, coil, contact, sweep, through, hold,
recovery).  The moves themselves, their order, and their timing (total, active
window, chain tick, step tick) and kind come from the style file under
``data/myvillage/combat/style/`` through ``tools/combat_data.py``; the pose
table holds only the authored poses, and the generator fails when the table and
the style disagree on the set of moves.  Each key stores readable intent
instead of raw Euler soup:

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
All three cover every table in ``POSE_TABLES``; each table writes its own file.
The script is deterministic and uses only the Python standard library.

Adding a pose table for another weapon (e.g. the two-handed spear)
-------------------------------------------------------------------
1. Write the poses (a guard dict, mode-enter and ready-idle keys, one
   ``MovePoses`` per style move) and append a ``PoseTable`` to ``POSE_TABLES``:

   ``style_id``/``output``   the style in ``index.json`` and its own PAL file
                             (``player_animations/<name>.json``);
   ``geometry``/``item_model`` the weapon's geometry contract and the 3D item
                             model whose ``thirdperson_righthand`` display the
                             game uses; grip compensation, forward kinematics,
                             blade and reach checks all use this weapon;
   ``rules``                 ``PoseRules`` thresholds that depend on the weapon
                             or the stance (``SWORD_RULES`` is the sword's; use
                             ``dataclasses.replace(SWORD_RULES, ...)``, ``None``
                             switches a sword-only rule off);
   ``cut_paths``             the named ``CutPath`` specs its moves may use
                             (``SWORD_CUT_PATHS`` holds 横/撩/斜; a spear can
                             define e.g. ``right_to_left`` with its own tip
                             thresholds, or ``side_finish=False`` for a cut
                             that ends forward);
   ``two_handed=True``       the weapon is held in both hands.

2. In a two-handed table a key asks for the left hand on the shaft with
   ``larm=ON_SHAFT`` (the contract's ``off_hand_grip_center``) or
   ``larm=OnShaft(at=8.0)`` (another preferred contract y, in model px).  The
   generator solves the left arm from the resolved pose so the left fist (the
   centre of the arm's bottom 4x4x4 cube, mirroring the right fist where the
   grip sits) lands on the shaft there, or at the nearest shaft point inside the
   contract's ``handle`` range that the rigid arm reaches, keeping clear of the
   right fist.  An explicit ``larm=(yaw, elevation)`` is a deliberate release.
   Example: ``key(5, "contact", "easeoutcubic", (8, 40, 0), ("L", 36, 0),
   (20, -30), ON_SHAFT, (75, -20, -10), (5, 0))``.

3. Run ``--check``.  ``SELF_CHECK <animation>: left hand ... off the shaft at
   tick N`` means the shaft is out of the left arm's reach at that key; ``left
   hand leaves the shaft ... between keys A and B; add a key ... at tick T``
   names the move and the tick to add an ``ON_SHAFT`` key at.  ``--report``
   prints, for a two-handed table, the shaft y of the left hand and its offset
   from the shaft in px for every key (``free`` for a release).
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass, field, replace
from pathlib import Path

try:
    from tools import combat_data
except ImportError:  # run as a script: tools/ itself is on sys.path
    import combat_data

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/myvillage/player_animations/sword_combat.json"
# The Qingfeng 3D jian: its thirdperson_righthand display transform and the grip / tip points come
# from the committed model and geometry contract written by tools/gen_qingfeng_sword_model.py, so
# the rig of the basic_sword table measures the sword that the game renders.
SWORD_MODEL_3D = ROOT / "src/main/resources/assets/myvillage/models/item/qingfeng_sword_3d.json"
SWORD_GEOMETRY = ROOT / "src/main/resources/assets/myvillage/combat/qingfeng_sword_geometry.json"
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

PLAYER_SCALE = 0.9375  # PlayerRenderer's model scale
LEG_LENGTH_PX = 12.0 * PLAYER_SCALE  # vanilla leg length after PlayerRenderer's 0.9375 scale
HEAD_YAW_LIMIT = 70.0
TORSO_TWIST_LIMIT = 8.0

# Vanilla arm boxes (wide arms): right addBox(-3, -2, -2, 4, 12, 4) about the pivot (-5, 2, 0), left
# addBox(-1, -2, -2, 4, 12, 4) about (5, 2, 0).  The fist is the centre of the bottom 4x4x4 cube of
# the box, on the box's centre line (x -1 / +1), 8 px down from the pivot; grip compensation puts
# the weapon's grip centre exactly on the right fist, and the off-hand solve puts the left fist on
# the shaft.  Values in blocks, arm-local.
RIGHT_ARM_PIVOT = (-5.0, 2.0, 0.0)
LEFT_ARM_PIVOT = (5.0, 2.0, 0.0)
RIGHT_FIST = (-1 / 16, 8 / 16, 0.0)
LEFT_FIST = (1 / 16, 8 / 16, 0.0)
FIST_WIDTH = 4 / 16 * PLAYER_SCALE  # blocks: two fists on one shaft keep their centres this far apart
# Off-hand on the shaft: distance of the left fist centre from the shaft axis in player-model px
# (the same unit as the grip check).  The fist is 4 px wide, so with the axis within 1 px of its
# centre the hand still closes round the shaft at a key; between keys the axis must stay inside the
# fist (half-width 2 px, the 1.9 px of the grip-drift check).
OFF_HAND_KEY_TOLERANCE_PX = 1.0
OFF_HAND_BETWEEN_TOLERANCE_PX = 1.9


@dataclass(frozen=True)
class OnShaft:
    """Left-arm request of a two-handed table: solve the left fist onto the weapon's shaft.

    ``at`` is the preferred contract y (model px) of the left hand; ``None`` is the contract's
    ``off_hand_grip_center``.
    """

    at: float | None = None


ON_SHAFT = OnShaft()


@dataclass(frozen=True)
class Key:
    tick: int
    phase: str
    easing: str
    body: tuple[float, float, float]
    stance: tuple[str, float, float]
    rarm: tuple[float, float]
    larm: tuple[float, float] | OnShaft
    item: tuple[float, float, float]
    blade: tuple[float, float]
    pos: tuple[float, float] = (0.0, 0.0)


@dataclass(frozen=True)
class MovePoses:
    """Authored third-person poses of one move.

    Timing (total, active window, chain and step ticks) and kind come from the style file; the
    pose table only says what the body does.  ``lunge`` marks the 弓步 finisher whose hips drive
    forward on the server step tick; ``cut_path`` names the blade path that must agree with the
    server hitbox, one of the table's ``cut_paths`` (the sword's: ``left_to_right``, ``rising``
    right-low -> left-high, ``descending`` left-high -> right-low).
    """

    keys: tuple[Key, ...]
    lunge: bool = False
    cut_path: str | None = None


@dataclass(frozen=True)
class PathRule:
    """One cut-path rule: every condition must hold, else ``message`` (formatted with the measured
    values in condition order) is reported.

    A condition is ``(where, measure, op, limit)``: ``where`` is ``wind`` (the key before contact),
    ``contact``, ``through``, ``hold`` or ``mid_active`` (the pose sampled at the middle of the active
    window); ``measure`` is ``tip_x`` / ``tip_y`` / ``tip_z`` (blocks, right / up / forward from the
    feet) or ``blade_yaw`` / ``blade_elevation`` (degrees); ``op`` is ``<``, ``>``, ``<=``, ``>=`` or
    ``abs<=``.
    """

    message: str
    conditions: tuple[tuple[str, str, str, float], ...]


@dataclass(frozen=True)
class CutPath:
    """The blade path of a cut, shared with the server hitbox.

    ``side_finish``: the table's ``cut_end_*`` rules (the blade finishes out to the side) apply.
    ``trail_tolerance``: max distance in blocks from the posed weapon tip to the head of the world
    trail (``CombatWorldTrails``) while the trail is moving; ``None`` only reports the distances.
    """

    rules: tuple[PathRule, ...]
    side_finish: bool = True
    trail_tolerance: float | None = None


@dataclass(frozen=True)
class PoseRules:
    """Self-check thresholds that depend on the weapon or the stance; ``None`` switches a rule off.

    The defaults are the basic_sword (Qingfeng, one hand, right foot forward) values.
    """

    hip_drop_px: tuple[float, float] = (2.0, 3.5)       # at contact, through and hold
    cut_turn_deg: tuple[float, float] = (70.0, 110.0)   # whole-figure yaw peak-to-peak of a cut
    cut_end_hand_lateral: float | None = 0.35           # min |right hand x| (blocks) at through/hold
    cut_end_blade_yaw: tuple[float, float] | None = (80.0, 110.0)  # |blade yaw| at through/hold
    cut_end_tip_lateral: float | None = 0.6             # min |tip x| (blocks) at through/hold
    thrust_body_yaw: tuple[float | None, float | None] = (None, -40.0)  # bladed body yaw (min, max)
    lunge_body_yaw: tuple[float | None, float | None] | None = None   # the lunge's; None = thrust_body_yaw
    thrust_left_hand_lateral: float | None = -0.45      # 剑指 T-shape: max left hand x (blocks)
    thrust_arm_level: float | None = 20.0               # max |elevation| shoulder -> right hand
    thrust_reach: tuple[float, float] = (1.5, 1.9)      # min tip z (blocks): plain thrust, lunge
    # Long-weapon rules (off for the sword), checked at every key and sampled between keys of every
    # animation of the table (moves, idle, mode entry):
    weapon_ground_clearance: float | None = None   # min height (blocks) of the weapon model's lowest corner
    min_hand_separation_px: float | None = None    # min contract px from the grip to an ON_SHAFT left hand
    shaft_body_clearance_px: float | None = None   # min player-model px from the weapon axis to torso, head, legs


SWORD_RULES = PoseRules()


@dataclass(frozen=True)
class PoseTable:
    """Third-person animation source of one combat style and weapon, written to one PAL file."""

    style_id: str
    output: Path
    guard: dict
    mode_enter: tuple[Key, ...]
    mode_enter_ticks: int
    ready_idle: tuple[Key, ...]
    ready_idle_ticks: int
    moves: dict[str, MovePoses]
    geometry: Path    # the weapon's geometry contract (grip, handle, tip, off-hand grip)
    item_model: Path  # the 3D item model whose thirdperson_righthand display the game applies
    rules: PoseRules
    cut_paths: dict[str, CutPath]
    two_handed: bool = False


@dataclass(frozen=True)
class Move:
    """A style move bound to its poses: timing and kind from the style file, keys from the table."""

    move_id: str
    animation_id: str
    kind: str  # "thrust" or "cut"
    total: int
    active_start: int
    active_end: int
    chain_tick: int
    step_tick: int | None
    keys: tuple[Key, ...] = field(default_factory=tuple)
    lunge: bool = False
    cut_path: str | None = None
    samples: str = "[]"  # the style's hitbox samples as JSON (a list or one generator object)


class PoseTableError(ValueError):
    """The pose tables and the combat data disagree."""


# Cut paths of the sword, matching the server hitboxes (thresholds are tip positions of the
# Qingfeng blade in blocks).
SWORD_CUT_PATHS: dict[str, CutPath] = {
    "left_to_right": CutPath((
        PathRule("horizontal cut must sweep the player's left -> right",
                 (("wind", "tip_x", "<", -0.4), ("hold", "tip_x", ">", 0.6))),
        PathRule("blade crosses the centre at yaw {0:.0f} on the middle active tick",
                 (("mid_active", "blade_yaw", "abs<=", 25.0),)),
    )),
    "rising": CutPath((
        PathRule("rising cut must start right-low", (("contact", "tip_x", ">", 0.4), ("contact", "tip_y", "<", 0.9))),
        PathRule("rising cut must finish left-high", (("hold", "tip_x", "<", -0.6), ("hold", "tip_y", ">", 1.8))),
    )),
    "descending": CutPath((
        PathRule("diagonal cut must wind up left-high", (("wind", "tip_x", "<", -0.3), ("wind", "tip_y", ">", 2.0))),
        PathRule("diagonal cut must finish right-low", (("hold", "tip_x", ">", 0.6), ("hold", "tip_y", "<", 0.6))),
    )),
}


def _larm(value):
    return value if isinstance(value, OnShaft) else tuple(value)


def key(tick, phase, easing, body, stance, rarm, larm, item, blade, pos=(0.0, 0.0)) -> Key:
    return Key(tick, phase, easing, tuple(body), tuple(stance), tuple(rarm), _larm(larm),
               tuple(item), tuple(blade), tuple(pos))


# ---------------------------------------------------------------------------------------------
# Pose table of myvillage:basic_sword (the Qingfeng jian).  Directions in the player's frame:
# yaw 0 = action facing, +90 = player's right, -90 = player's left; elevation + = up.  Cut
# directions follow the server hitboxes: 横 sweeps the player's left -> right, 撩 rises
# right-low -> left-high, 斜 descends left-high -> right-low.  Key ticks are authored against
# the style's timing, and check_move verifies them against the style file.
# ---------------------------------------------------------------------------------------------

# Guard: slightly bladed stance (right foot forward, right shoulder a little forward), sword
# low-centre with the tip at the opponent's throat, left hand 剑指 held out low-left.
GUARD = dict(body=(4, -15, 0), stance=("R", 24, 0), rarm=(12, -45), larm=(-50, -45),
             item=(9, -4, -22), blade=(-3, 25))
BREATH = dict(body=(6, -14, 0), stance=("R", 25, 0), rarm=(12, -43), larm=(-50, -43),
              item=(9, -4, -22), blade=(-3, 27))


def guard(tick: int, phase: str, easing: str) -> Key:
    return key(tick, phase, easing, **GUARD)


BASIC_SWORD_MOVES: dict[str, MovePoses] = {
    "myvillage:basic_sword_01_thrust": MovePoses((
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
    "myvillage:basic_sword_02_horizontal_cut": MovePoses((
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
    ), cut_path="left_to_right"),
    "myvillage:basic_sword_03_rising_cut": MovePoses((
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
    ), cut_path="rising"),
    "myvillage:basic_sword_04_diagonal_cut": MovePoses((
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
    ), cut_path="descending"),
    "myvillage:basic_sword_05_lunge_thrust": MovePoses((
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
    ), lunge=True),
}


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


BASIC_SWORD = PoseTable(
    style_id="myvillage:basic_sword",
    output=OUTPUT,
    guard=GUARD,
    mode_enter=enter_keys(),
    mode_enter_ticks=16,  # 0.8 s
    ready_idle=idle_keys(),
    ready_idle_ticks=24,  # 1.2 s loop
    moves=BASIC_SWORD_MOVES,
    geometry=SWORD_GEOMETRY,
    item_model=SWORD_MODEL_3D,
    rules=SWORD_RULES,
    cut_paths=SWORD_CUT_PATHS,
)

# ---------------------------------------------------------------------------------------------
# Pose table of myvillage:basic_spear (the Lingxiao spear, two-handed).  The item rides the right
# (rear) hand at the contract's grip_center; every ON_SHAFT key solves the left (leading) hand onto
# the shaft.  With rigid arms the left fist only reaches a shaft that points roughly 45-120
# degrees to the left of the chest, so the guard is bladed (body turned right, left foot and left
# hand leading) and a two-handed spear cannot point ahead of the chest without its butt entering
# the body.  The moves therefore release the leading hand where holding on would cripple them
# (an explicit larm is a deliberate release, and the free arm counterbalances or reaches):
#   中平扎  both hands throughout; the shaft slides through the leading hand.
#   横扫    both hands for the wind-up; released as the spear is pushed out to full reach for the
#           one-handed level arc; regripped in the recovery.
#   上挑    released after the drop; one-handed flick from right-low to left-high; regripped in
#           the recovery.
#   劈枪    released as the rear arm raises the spear overhead; one-handed smash into the ground
#           in front; regripped in the recovery (both hands at impact would put the butt in the
#           chest).
#   游龙突刺 both hands through the coil; released as the rear arm drives; one-handed finish at
#           full depth; regripped in the recovery.
# Cut senses follow the style's hit samples: 横扫 sweeps the player's left -> right, 上挑 rises
# right-low -> left-high and 劈枪 descends left-high -> right-low, both through the centre line.
# ---------------------------------------------------------------------------------------------

SPEAR_GEOMETRY = ROOT / "src/main/resources/assets/myvillage/combat/lingxiao_spear_geometry.json"
SPEAR_MODEL_3D = ROOT / "src/main/resources/assets/myvillage/models/item/lingxiao_spear_3d.json"
SPEAR_OUTPUT = ROOT / "src/main/resources/assets/myvillage/player_animations/spear_combat.json"

SPEAR_RULES = replace(
    SWORD_RULES,
    # The sweep is the spear's only side-finishing cut: the server sweep ends 70 degrees right
    # (not past 90 like the sword's), with the rear hand pulled back by the right hip.
    cut_end_hand_lateral=0.3,
    cut_end_blade_yaw=(45.0, 100.0),
    # Two-handed, the tip cannot get much past 1.2 blocks out to the side: the rear hand swings
    # behind the body as the shaft turns right, so the grip moves left as fast as the tip moves right.
    cut_end_tip_lateral=1.0,
    # Bladed with the LEFT shoulder leading (body turned right) instead of the sword's right.
    thrust_body_yaw=(40.0, None),
    # The one-handed lunge drives the rear (right) shoulder through: square to the target or
    # turned a little left, never bladed the guard's way.
    lunge_body_yaw=(-45.0, 20.0),
    # The left hand is on the shaft (checked by the off-hand rules), not out in a 剑指 T-shape.
    thrust_left_hand_lateral=None,
    # The rear arm drives forward to the belly line, so it may point a little lower than the
    # sword arm.
    thrust_arm_level=30.0,
    # A 2.6-block spear: the visual tip reaches about 60 % of the server range, as the sword's does.
    thrust_reach=(2.1, 2.85),
    # Measured on the model's real corners (barbs, star, pennant, plaque, tassels), not the axis:
    # the lowest corner keeps just under a pixel of grass clearance.
    weapon_ground_clearance=0.05,
    min_hand_separation_px=5.0,
    shaft_body_clearance_px=0.5,
)

# Tip thresholds in blocks for a tip about 2.2 blocks from the rear hand (the sword's are ~1 block).
SPEAR_CUT_PATHS: dict[str, CutPath] = {
    "left_to_right": CutPath((
        PathRule("sweep must travel the player's left -> right",
                 (("wind", "tip_x", "<", -0.8), ("hold", "tip_x", ">", 1.0))),
        PathRule("sweep must start on the left at activeStart (blade yaw {0:.0f})",
                 (("contact", "blade_yaw", "<=", -40.0),)),
        PathRule("shaft crosses the centre at yaw {0:.0f} on the middle active tick",
                 (("mid_active", "blade_yaw", "abs<=", 25.0),)),
        PathRule("sweep must stay level at the hit height (tip y {0:.2f}, elevation {2:.0f})",
                 (("mid_active", "tip_y", ">=", 0.6), ("mid_active", "tip_y", "<=", 1.5),
                  ("mid_active", "blade_elevation", "abs<=", 15.0))),
    ), trail_tolerance=0.48),  # one drawn trail length: the spearhead stays inside the ribbon it draws
    # The world trail of the flick and the smash follows the FAR ends of their samples, which run
    # level (head height left -> right for the flick, knee height right -> left for the smash), so
    # a vertical technique cannot stay on it: their trail distances are reported, not enforced.
    "rising": CutPath((
        PathRule("flick must start right-low", (("contact", "tip_x", ">", 0.3), ("contact", "tip_y", "<", 0.8))),
        PathRule("flick must finish left-high", (("hold", "tip_x", "<", -0.15), ("hold", "tip_y", ">", 1.8))),
        PathRule("flick crosses the centre line at yaw {0:.0f} on the middle active tick",
                 (("mid_active", "blade_yaw", "abs<=", 25.0),)),
    ), side_finish=False),
    "descending": CutPath((
        PathRule("smash must wind up left-high", (("wind", "tip_x", "<", -0.4), ("wind", "tip_y", ">", 2.2))),
        PathRule("smash must finish right-low", (("hold", "tip_x", ">", 0.4), ("hold", "tip_y", "<", 0.6))),
        PathRule("smash crosses the centre line at yaw {0:.0f} on the middle active tick",
                 (("mid_active", "blade_yaw", "abs<=", 25.0),)),
    ), side_finish=False),
}

SPEAR_GUARD = dict(body=(10, 56, 0), stance=("L", 30, 0), rarm=(142, -72), larm=ON_SHAFT,
                   item=(-37.5, -116.6, -10.5), blade=(-2, 12))


def spear_guard(tick: int, phase: str, easing: str) -> Key:
    return key(tick, phase, easing, **SPEAR_GUARD)


BASIC_SPEAR_MOVES: dict[str, MovePoses] = {
    "myvillage:basic_spear_01_mid_thrust": MovePoses((
        spear_guard(0, "guard", "linear"),
        key(2, "coil", "easeinoutsine", (4, 72, 0), ("L", 30, 0), (145, -55), OnShaft(at=14),
            (-50.9, -144.6, -18.7), (-1, 10), pos=(0, 1.2)),
        key(3, "drive", "easeinquad", (8, 64, 0), ("L", 34, 0), (60, -82), OnShaft(at=8),
            (-15, -63.8, -7.1), (-2, 8), pos=(0, -0.5)),
        key(4, "contact", "easeoutcubic", (16, 52, 0), ("L", 38, 0), (-5, -18), OnShaft(at=5),
            (56.2, -13.5, 0), (-5, 5), pos=(0, -2.5)),
        key(5, "through", "easeoutsine", (18, 52, 0), ("L", 40, 0), (-5, -15), OnShaft(at=5),
            (60.2, -15.1, 0), (-5, 4), pos=(0, -3)),
        key(8, "hold", "linear", (18, 52, 0), ("L", 40, 0), (-5, -15), OnShaft(at=5),
            (61.2, -15.1, 0), (-5, 3), pos=(0, -3)),
        key(9, "recoil", "easeinoutsine", (13, 55, 0), ("L", 36, 0), (-5, -35), OnShaft(at=7),
            (39.2, -12.2, 0.8), (-4, 5), pos=(0, -1.6)),
        key(10, "return", "easeinoutsine", (9, 60, 0), ("L", 33, 0), (60, -85), OnShaft(at=9),
            (-17.5, -61.9, -4.4), (-2, 9), pos=(0, -0.8)),
        spear_guard(12, "recovery", "easeinoutsine"),
    )),
    "myvillage:basic_spear_02_sweep": MovePoses((
        spear_guard(0, "guard", "linear"),
        key(2, "wind", "easeinoutsine", (6, 30, 0), ("L", 31, -5), (50, -70), ON_SHAFT,
            (-24.2, -101.1, -19.3), (-55, 8)),
        key(3, "anticipation", "easeinoutsine", (4, 8, 0), ("L", 32, -10), (5, -50), ON_SHAFT,
            (-25.1, -103.2, -39.3), (-95, 6)),
        key(4, "coil", "linear", (20, -48, 18), ("L", 36, -10), (-60, -10), (-165, -10),
            (66.3, -3.7, -38.3), (-99, 0)),
        key(5, "contact", "easeoutquad", (24, -24, 16), ("L", 38, -10), (-45, -20), (-150, -5),
            (52.2, -11.8, -32.6), (-80, 3), pos=(0, -3)),
        key(6, "sweep", "linear", (18, 25, 0), ("L", 40, -10), (5, -15), (-128, 0),
            (58.5, -10.9, -17.4), (-13, 5), pos=(0, -4)),
        key(7, "through", "linear", (15, 58, -8), ("L", 40, -10), (60, -5), (-105, 5),
            (71.1, -3.9, -5), (55, 3), pos=(0, -4)),
        key(10, "hold", "linear", (15, 58, -8), ("L", 40, -10), (60, -5), (-104, 5),
            (71.1, -3.9, -5), (55, 3), pos=(0, -4)),
        key(12, "return", "easeinoutsine", (10, 62, 0), ("L", 34, -5), (150, -80), ON_SHAFT,
            (-27.1, -101.9, -5.6), (4, 8), pos=(0, -1)),
        spear_guard(15, "recovery", "easeinoutsine"),
    ), cut_path="left_to_right"),
    "myvillage:basic_spear_03_rising_flick": MovePoses((
        spear_guard(0, "guard", "linear"),
        key(2, "drop", "easeinoutsine", (10, 72, 0), ("L", 32, 0), (165, -40), ON_SHAFT,
            (-53.8, -153.1, -15.9), (6, -5)),
        key(3, "anticipation", "easeinoutsine", (14, 88, 0), ("L", 34, 0), (40, -50), (-60, 20),
            (39.3, -29.9, -13.3), (19, -12)),
        key(4, "coil", "linear", (15, 92, 0), ("L", 34, 0), (45, -55), (-70, 25),
            (36.4, -32.6, -11.9), (24, -14)),
        key(5, "contact", "easeoutcubic", (10, 70, 0), ("L", 36, 0), (20, -20), (-90, 10),
            (68.1, -10.3, -6.6), (13, -9), pos=(0, -1)),
        key(6, "sweep", "linear", (2, 45, 0), ("L", 38, 0), (15, 10), (-140, -20),
            (75.3, 4, -26.6), (-12, 15), pos=(0, -2)),
        key(7, "through", "easeoutsine", (-6, 18, 0), ("L", 38, 0), (5, 40), (-170, -35),
            (90.8, 44.2, -38.9), (-50, 44), pos=(0, -2)),
        key(10, "hold", "linear", (-6, 18, 0), ("L", 38, 0), (5, 40), (-170, -35),
            (90.8, 44.2, -38.9), (-50, 44), pos=(0, -2)),
        key(12, "gather", "easeinoutsine", (4, 35, 0), ("L", 36, 0), (15, -50), (-50, -60),
            (20.8, -9.7, -6.4), (5, 8), pos=(0, -1)),
        key(13, "return", "easeinoutsine", (12, 59, 0), ("L", 34, 0), (-20, -80), OnShaft(at=9),
            (-10.3, -26.6, 3.1), (-2, 9), pos=(0, -1)),
        spear_guard(15, "recovery", "easeinoutsine"),
    ), cut_path="rising"),
    "myvillage:basic_spear_04_overhead_smash": MovePoses((
        spear_guard(0, "guard", "linear"),
        key(2, "lift", "easeinoutsine", (-6, 22, 0), ("L", 30, 0), (10, -30), OnShaft(at=9),
            (-55.9, -88.5, -60), (-80, 45)),
        key(3, "raise", "easeinoutsine", (-10, 12, 0), ("L", 30, 0), (0, 40), (-10, 10),
            (-62.9, -70.2, -150.6), (-70, 60)),
        key(4, "anticipation", "easeinoutsine", (-14, -14, 0), ("L", 30, 0), (100, 60), (-5, 25),
            (-39.9, 13, -201.1), (-126, 51)),
        key(6, "coil", "linear", (-12, 0, 0), ("L", 30, 0), (0, 55), (-10, 30),
            (-86.7, -130.5, -152), (-55, 54)),
        key(7, "contact", "easeoutcubic", (-4, 20, 0), ("L", 36, 0), (10, 45), (0, 40),
            (-89.5, -159.3, -161.3), (-17, 37)),
        key(8, "sweep", "linear", (18, 48, 0), ("L", 40, 0), (10, 0), (-20, 0),
            (-103.8, -191.3, -160), (-10, 3), pos=(0, -2)),
        key(9, "through", "easeoutsine", (30, 45, 0), ("L", 44, 0), (20, -40), (-150, 10),
            (-132.8, -206.4, -164), (-1, -10), pos=(0, -4)),
        key(13, "hold", "linear", (30, 45, 0), ("L", 44, 0), (20, -40), (-150, 10),
            (-132.8, -206.4, -164), (-1, -10), pos=(0, -4)),
        key(15, "gather", "easeinoutsine", (20, 54, 0), ("L", 38, 0), (15, -50), (-30, -35),
            (-114.7, -136, -130.7), (5, 0), pos=(0, -3)),
        key(17, "return", "easeinoutsine", (12, 59, 0), ("L", 36, 0), (-20, -80), OnShaft(at=9),
            (-9.4, -25.3, -66.9), (-2, 9), pos=(0, -2)),
        spear_guard(19, "recovery", "easeinoutsine"),
    ), cut_path="descending"),
    "myvillage:basic_spear_05_dragon_lunge": MovePoses((
        spear_guard(0, "guard", "linear"),
        key(4, "anticipation", "easeinoutsine", (-12, 86, 0), ("L", 32, 0), (105, -30), OnShaft(at=10),
            (-45, -123.4, -56.8), (0, 10), pos=(0, 2.5)),
        key(7, "coil", "linear", (-14, 89, 0), ("L", 33, 0), (100, -30), OnShaft(at=10),
            (-36.5, -113.1, -58.5), (0, 9), pos=(0, 3)),
        key(8, "contact", "easeoutquart", (28, -10, 0), ("L", 46, 0), (0, -10), (-140, -60),
            (59.1, 3.1, -9.8), (-10, 10), pos=(0, -9)),
        key(10, "through", "easeoutsine", (30, -12, 0), ("L", 46, 0), (0, -10), (-145, -40),
            (58, 4.6, -9.8), (-10, 11), pos=(0, -10)),
        key(14, "hold", "linear", (30, -12, 0), ("L", 46, 0), (0, -10), (-145, -40),
            (58, 4.6, -9.8), (-10, 11), pos=(0, -10)),
        key(16, "recoil", "easeinoutsine", (22, 20, 0), ("L", 42, 0), (20, -30), (-60, -60),
            (34.6, -18, -28.1), (-13, 10), pos=(0, -6)),
        key(18, "return", "easeinoutsine", (12, 59, 0), ("L", 36, 0), (-20, -80), OnShaft(at=9),
            (-10.3, -26.6, 3.1), (-2, 9), pos=(0, -2)),
        spear_guard(22, "recovery", "easeinoutsine"),
    ), lunge=True),
}


def spear_idle_keys() -> tuple[Key, ...]:
    return (
        spear_guard(0, "guard", "linear"),
        key(12, "breath", "easeinoutsine", (12, 55, 0), ("L", 31, 0), (142, -70), ON_SHAFT,
            (-40.2, -114.7, -11.6), (-2, 13)),
        spear_guard(24, "guard", "easeinoutsine"),
    )


def spear_enter_keys() -> tuple[Key, ...]:
    return (
        key(0, "vanilla", "linear", (0, 0, 0), ("L", 0, 0), (0, -90), (0, -90),
            (0, 0, 0), (0, -11)),
        key(4, "raise", "easeoutcubic", (-4, 15, 0), ("L", 14, 0), (20, -40), (-25, -30),
            (-41.8, -10.2, -11.4), (5, 80)),
        key(8, "catch", "easeinoutsine", (6, 40, 0), ("L", 24, 0), (100, -80), ON_SHAFT,
            (-43.9, -86.3, -9.5), (-8, 30)),
        key(12, "settle", "easeinoutsine", (12, 60, 0), ("L", 32, 0), (142, -72), ON_SHAFT,
            (-33.5, -113.4, -10.5), (-2, 8)),
        spear_guard(16, "guard", "easeinoutsine"),
    )


BASIC_SPEAR = PoseTable(
    style_id="myvillage:basic_spear",
    output=SPEAR_OUTPUT,
    guard=SPEAR_GUARD,
    mode_enter=spear_enter_keys(),
    mode_enter_ticks=16,  # 0.8 s
    ready_idle=spear_idle_keys(),
    ready_idle_ticks=24,  # 1.2 s loop
    moves=BASIC_SPEAR_MOVES,
    geometry=SPEAR_GEOMETRY,
    item_model=SPEAR_MODEL_3D,
    rules=SPEAR_RULES,
    cut_paths=SPEAR_CUT_PATHS,
    two_handed=True,
)


# One pose table per combat style.  A second style is an added table with its own output file and
# weapon rig (see the module docstring); its guard must be the guard its keys are built from,
# because guard keys resolve to it exactly.
POSE_TABLES: tuple[PoseTable, ...] = (BASIC_SWORD, BASIC_SPEAR)


# ---------------------------------------------------------------------------------------------
# Binding the pose tables to the combat data (tools/combat_data.py).
# ---------------------------------------------------------------------------------------------

def bind(table: PoseTable, style: dict) -> tuple[Move, ...]:
    """The style's moves, in combo order, with their pose keys.

    Raises :class:`PoseTableError` naming every move that the style defines without poses and
    every posed move that the style does not define.
    """
    style_ids = [move["id"] for move in style["moves"]]
    problems = [f"style {table.style_id} defines {move_id} but its pose table has no poses for it"
                for move_id in style_ids if move_id not in table.moves]
    problems += [f"pose table of {table.style_id} poses {move_id}, which the style does not define"
                 for move_id in table.moves if move_id not in style_ids]
    namespace = table.output.parent.parent.name
    for move_id in style_ids:
        if combat_data.split_id(move_id)[0] != namespace:
            problems.append(f"{move_id} is outside the namespace of {table.output.name} ({namespace})")
    for move_id, poses in table.moves.items():
        if poses.cut_path is not None and poses.cut_path not in table.cut_paths:
            problems.append(f"{move_id}: unknown cut_path {poses.cut_path!r}")
    problems += _hand_problems(table)
    if problems:
        raise PoseTableError("; ".join(problems))
    bound = []
    for data in style["moves"]:
        poses = table.moves[data["id"]]
        step = data.get("step")
        bound.append(Move(
            move_id=data["id"],
            animation_id=combat_data.split_id(data["id"])[1],
            kind=data["kind"],
            total=data["total_ticks"],
            active_start=data["active_ticks"][0],
            active_end=data["active_ticks"][1],
            chain_tick=data["chain_tick"],
            step_tick=None if step is None else step["tick"],
            keys=poses.keys,
            lunge=poses.lunge,
            cut_path=poses.cut_path,
            samples=json.dumps(data["hitbox"]["samples"], sort_keys=True),
        ))
    return tuple(bound)


def _hand_problems(table: PoseTable) -> list[str]:
    """ON_SHAFT keys need a two-handed table, and a two-handed table needs an off-hand grip."""
    keys = (*table.mode_enter, *table.ready_idle, *(k for poses in table.moves.values() for k in poses.keys))
    asks = isinstance(table.guard["larm"], OnShaft) or any(isinstance(k.larm, OnShaft) for k in keys)
    if asks and not table.two_handed:
        return [f"pose table of {table.style_id} puts the left hand on the shaft but is not two_handed"]
    if table.two_handed and weapon_rig(table).off_hand_y is None:
        return [f"pose table of {table.style_id} is two_handed but {table.geometry.name} has no off_hand_grip_center"]
    return []


def check_tables(styles: dict[str, dict], tables: tuple[PoseTable, ...] = POSE_TABLES) -> None:
    """Every listed style has exactly one pose table, every table belongs to a listed style, and
    every table writes its own file."""
    table_styles = [table.style_id for table in tables]
    problems = [f"style {style_id} has no pose table" for style_id in styles if style_id not in table_styles]
    problems += [f"pose table for {style_id}, which the combat index does not list"
                 for style_id in table_styles if style_id not in styles]
    problems += [f"two pose tables for {style_id}" for style_id in sorted(set(table_styles))
                 if table_styles.count(style_id) > 1]
    outputs = [table.output for table in tables]
    problems += [f"two pose tables write {output.name}" for output in sorted(set(outputs))
                 if outputs.count(output) > 1]
    if problems:
        raise PoseTableError("; ".join(problems))


_STYLES: dict[Path, dict[str, dict]] = {}


def combat_styles(root: Path = ROOT) -> dict[str, dict]:
    """Validated styles of the combat data; raises combat_data.CombatDataError if it is invalid."""
    if root not in _STYLES:
        _STYLES[root] = combat_data.load_strict(root).styles
    return _STYLES[root]


def moves(table: PoseTable = BASIC_SWORD) -> tuple[Move, ...]:
    """The bound moves of a pose table, read from the committed style file."""
    return bind(table, combat_styles()[table.style_id])


def animation_names(style: dict) -> tuple[str, str]:
    """PAL keys of the style's (mode enter, ready idle) animations."""
    animations = style["animations"]
    return combat_data.split_id(animations["mode_enter"])[1], combat_data.split_id(animations["ready_idle"])[1]


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


def solve_stance(body_rot, front, splay, direction, previous=None):
    """Legs for a planted stance, plus the hip drop (negative px) that keeps both feet grounded.

    Without a previous key, of the two Euler solutions per leg the one with the smaller yaw is
    used, so a back leg swings backward (x > 0) instead of swinging forward and twisting 180
    degrees.  With a previous key (``previous`` = (body, right leg, left leg) of the resolved
    previous pose) each leg takes the solution that stays on the previous key's branch, as
    :func:`solve_limb` does for the arms: a body turned more than about 90 degrees off the stance
    line (the bladed spear) would otherwise flip a leg between the branches between two keys.
    """
    down = -(90.0 - splay)

    def untwisted(yaw, before):
        if before is not None:
            return solve_limb(body_rot, yaw, down, previous=before, previous_body=previous[0])
        options = _limb_candidates(body_rot, world_direction(yaw, down))
        return min(options, key=lambda c: (abs(c[1]), c[0]))

    before_right, before_left = (None, None) if previous is None else (previous[1], previous[2])
    before_front, before_back = (before_right, before_left) if front == "R" else (before_left, before_right)
    front_leg = untwisted(direction, before_front)
    back_leg = untwisted(direction + 180.0, before_back)
    drop = -LEG_LENGTH_PX * (1.0 - math.cos(_rad(splay)))
    right, left = (front_leg, back_leg) if front == "R" else (back_leg, front_leg)
    return right, left, drop


def _r(value: float, digits: int = 1) -> float:
    value = round(value + 0.0, digits)
    return 0.0 if value == 0 else value


def _is_guard(k: Key, guard_spec: dict = GUARD) -> bool:
    return (k.body, k.stance, k.rarm, k.larm, k.item, k.pos) == (
        tuple(guard_spec["body"]), tuple(guard_spec["stance"]), tuple(guard_spec["rarm"]),
        _larm(guard_spec["larm"]), tuple(guard_spec["item"]), (0.0, 0.0))


def _resolve_key(k: Key, previous, table: PoseTable = BASIC_SWORD) -> dict[str, list[float]]:
    previous_body = None if previous is None else previous["body"]
    right_leg, left_leg, drop = solve_stance(k.body, *k.stance, previous=None if previous is None else (
        previous_body, previous["right_leg"][:2], previous["left_leg"][:2]))
    previous_larm = None if previous is None else previous["left_arm"][:2]
    rarm = solve_limb(k.body, *k.rarm, previous=None if previous is None else previous["right_arm"][:2],
                      previous_body=previous_body)
    # An ON_SHAFT left arm is solved below, from the rounded pose; this placeholder is replaced.
    larm = (0.0, 0.0) if isinstance(k.larm, OnShaft) else solve_limb(
        k.body, *k.larm, previous=previous_larm, previous_body=previous_body)
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
    pose = {bone: [_r(v, 2 if bone == "body_pos" else 1) for v in values] for bone, values in pose.items()}
    pose["right_item_pos"] = [_r(v, 2) for v in grip_compensation(pose["right_item"], table)]
    if isinstance(k.larm, OnShaft):
        larm = solve_off_hand(pose, table, k.larm.at, previous_larm, previous_body)
        pose["left_arm"] = [_r(larm[0]), _r(larm[1]), 0.0]
    return pose


def canonical_guard(table: PoseTable = BASIC_SWORD) -> dict[str, list[float]]:
    return _resolve_key(key(0, "guard", "linear", **table.guard), None, table)


def resolve(keys: tuple[Key, ...], table: PoseTable = BASIC_SWORD) -> list[dict[str, list[float]]]:
    """Turn intent keys into per-bone PAL values ({bone: [x, y, z]} plus 'body_pos').

    Guard keys always resolve to the one canonical guard pose of the table; every other key picks
    the limb solution closest to the previous key so no channel flips between equivalent angles.
    """
    poses = []
    previous = None
    for k in keys:
        pose = canonical_guard(table) if _is_guard(k, table.guard) else _resolve_key(k, previous, table)
        poses.append(pose)
        previous = pose
    return _settle_arm_branches(keys, poses, table)


def _segment_deviation(a_body, a, b_body, b) -> float:
    """Degrees between the Euler-midpoint direction of a limb and the great-circle midpoint."""
    start, end = limb_direction(a_body, a), limb_direction(b_body, b)
    ideal = [start[j] + end[j] for j in range(3)]
    norm = math.sqrt(sum(v * v for v in ideal))
    if norm < 1e-6:
        return 0.0
    mid = limb_direction([(a_body[j] + b_body[j]) / 2.0 for j in range(3)], [(a[j] + b[j]) / 2.0 for j in range(2)])
    return math.degrees(math.acos(max(-1.0, min(1.0, sum(mid[j] * ideal[j] / norm for j in range(3))))))


def _long_way(a, b, bone) -> bool:
    return (_segment_deviation(a["body"], a[bone][:2], b["body"], b[bone][:2]) > 30.0
            or max(abs(a[bone][j] - b[bone][j]) for j in range(2)) > 190.0)


def _settle_arm_branches(keys: tuple[Key, ...], poses, table: PoseTable):
    """Backward pass over the arms: guard keys are fixed, so a key resolved against its previous
    key alone can sit on the Euler branch opposite the next key's (an arm released behind the
    back that comes forward again into the guard) and swing the long way.  Only for such a
    segment, walking back from the end, the earlier key takes the equivalent solution ((x, y) or
    (-x, y + 180), y wrapped by 360) whose two segments are shortest; a held left hand must keep
    its fist on the shaft.  Animations without a long-way segment are returned unchanged."""
    poses = [dict(p) for p in poses]
    for i in range(len(keys) - 2, 0, -1):
        if _is_guard(keys[i], table.guard):
            continue
        for bone in ("left_arm",):  # the right arm carries the item; its twist is part of the item pose
            if not _long_way(poses[i], poses[i + 1], bone):
                continue
            x, y = poses[i][bone][:2]
            best, best_cost = poses[i], None
            solutions = [(x, y), (-x, y + 180.0)]
            if isinstance(keys[i].larm, OnShaft):  # re-solve the fist onto the shaft on the next key's branch
                solutions.append(solve_off_hand(poses[i], table, keys[i].larm.at,
                                                poses[i + 1][bone][:2], poses[i + 1]["body"]))
            for cx, cy in solutions:
                for wrap in (-360.0, 0.0, 360.0):
                    trial = dict(poses[i], **{bone: [_r(cx), _r(cy + wrap), 0.0]})
                    if isinstance(keys[i].larm, OnShaft) and \
                            off_hand_offset(skeleton(trial, table), table)[0] > OFF_HAND_KEY_TOLERANCE_PX:
                        continue
                    cost = (_long_way(trial, poses[i + 1], bone) + _long_way(poses[i - 1], trial, bone),
                            trial is not None and trial[bone][:2] != poses[i][bone][:2],
                            round(_segment_deviation(trial["body"], trial[bone][:2], poses[i + 1]["body"],
                                                     poses[i + 1][bone][:2])
                                  + _segment_deviation(poses[i - 1]["body"], poses[i - 1][bone][:2],
                                                       trial["body"], trial[bone][:2]), 3))
                    if best_cost is None or cost < best_cost:
                        best, best_cost = trial, cost
            poses[i] = best
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


def build_animation(keys: tuple[Key, ...], length_ticks: int, loop: bool = False,
                    table: PoseTable = BASIC_SWORD) -> dict:
    poses = resolve(keys, table)
    bones: dict[str, dict[str, dict]] = {}
    for bone in BONE_ORDER:
        rotation = {}
        for k, pose in zip(keys, poses):
            rotation[_timestamp(k.tick)] = _vector(pose[bone], k.easing)
        bones[bone] = {"rotation": rotation}
        if bone == "body":
            bones[bone]["position"] = {
                _timestamp(k.tick): _vector(pose["body_pos"], k.easing) for k, pose in zip(keys, poses)}
        if bone == "right_item" and any(any(v != 0 for v in pose["right_item_pos"]) for pose in poses):
            bones[bone]["position"] = {
                _timestamp(k.tick): _vector(pose["right_item_pos"], k.easing) for k, pose in zip(keys, poses)}
    animation: dict = {}
    if loop:
        animation["loop"] = True
    animation["animation_length"] = round(length_ticks / TICKS_PER_SECOND, 4)
    animation["bones"] = bones
    return animation


def build_document(table: PoseTable = BASIC_SWORD, style: dict | None = None) -> dict:
    """The PAL document of one pose table; ``style`` defaults to the committed style file."""
    style = combat_styles()[table.style_id] if style is None else style
    enter_id, idle_id = animation_names(style)
    bound = bind(table, style)
    animations = {
        enter_id: build_animation(table.mode_enter, table.mode_enter_ticks, table=table),
        idle_id: build_animation(table.ready_idle, table.ready_idle_ticks, loop=True, table=table),
    }
    for move in bound:
        animations[move.animation_id] = build_animation(move.keys, move.total, table=table)
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
# ItemInHandLayer + ItemInHandLayerMixin and the table weapon's thirdperson_righthand display).
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


@dataclass(frozen=True)
class WeaponRig:
    """The held weapon of a pose table, read from its geometry contract and 3D item model.

    Points are in item-model block units (model px / 16); ``handle``, ``grip_y`` and ``off_hand_y``
    are contract y values in model px along the weapon axis (+Y at ``axis`` x/z).
    """

    display: tuple            # thirdperson_righthand ItemTransform + ItemRenderer matrices
    grip: tuple[float, float, float]
    tip: tuple[float, float, float]
    butt: tuple[float, float, float]
    axis: tuple[float, float]
    handle: tuple[float, float]
    grip_y: float
    off_hand_y: float | None
    trail_tip_y: float = 0.0    # contract y of the trail's tip (``trail.tip``, else ``blade_tip``)
    display_scale: float = 1.0  # thirdperson_righthand scale along the weapon axis
    corners: tuple = ()         # (element name, corner) of every model element, rotated, in model blocks

    def point(self, y: float) -> tuple[float, float, float]:
        """Axis point at contract y (model px)."""
        return (self.axis[0], y / 16.0, self.axis[1])


_RIGS: dict[tuple[Path, Path], WeaponRig] = {}


def _element_corners(elements) -> tuple:
    """(name, corner) of every cuboid of a block model, its element rotation applied, in model blocks."""
    out = []
    for index, element in enumerate(elements):
        low, high = element["from"], element["to"]
        rotation = element.get("rotation")
        for corner in [(x, y, z) for x in (low[0], high[0]) for y in (low[1], high[1]) for z in (low[2], high[2])]:
            point = list(corner)
            if rotation and rotation.get("angle"):
                origin = rotation["origin"]
                m = _mat3(rotation["axis"], rotation["angle"])
                rel = [point[i] - origin[i] for i in range(3)]
                point = [origin[i] + sum(m[i][j] * rel[j] for j in range(3)) for i in range(3)]
            out.append((element.get("name", f"element {index}"), tuple(c / 16.0 for c in point)))
    return tuple(out)


def weapon_lowest_point(pose: dict[str, list[float]], table: PoseTable) -> tuple[float, str]:
    """(height in blocks of the weapon model's lowest corner, that element's name); the axis ends
    when the model has no elements."""
    rig = weapon_rig(table)
    item = _item_frame(_part_frame(_root_frame(pose), RIGHT_ARM_PIVOT, pose["right_arm"]), pose, rig)
    if not rig.corners:
        sk = skeleton(pose, table)
        return min((sk["butt"][1], "butt"), (sk["tip"][1], "tip"))
    return min((_apply(item, corner)[1], name) for name, corner in rig.corners)


def load_rig(geometry_path: Path, model_path: Path) -> WeaponRig:
    """The weapon rig of a geometry contract and item model; raises PoseTableError if unusable."""
    cache_key = (geometry_path, model_path)
    if cache_key in _RIGS:
        return _RIGS[cache_key]
    try:
        geometry = json.loads(geometry_path.read_text(encoding="utf-8"))
        model = json.loads(model_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PoseTableError(f"weapon rig unreadable: {exc}") from exc
    named = geometry.get("model")
    if named is not None:
        namespace, path = combat_data.split_id(named)
        if not model_path.as_posix().endswith(f"assets/{namespace}/models/{path}.json"):
            raise PoseTableError(f"{geometry_path.name} describes {named}, not {model_path.name}")
    try:
        display = model["display"]["thirdperson_righthand"]
        tx, ty, tz = (c / 16.0 for c in display["translation"])
        rx, ry, rz = display["rotation"]
        sx, sy, sz = display["scale"]
        grip_center = geometry["grip_center"]
        axes = geometry.get("axes", {})
        axis = (axes.get("center_x", grip_center[0]) / 16.0, axes.get("center_z", grip_center[2]) / 16.0)
        butt_y = geometry["pommel"]["y"][0] if "pommel" in geometry else geometry["overall_y"][0]
        off_hand = geometry.get("off_hand_grip_center")
        rig = WeaponRig(
            # ItemTransform.apply: translate, rotationXYZ (Rx * Ry * Rz), scale; then ItemRenderer -0.5.
            display=(_t(tx, ty, tz), _r4("x", rx), _r4("y", ry), _r4("z", rz), _s(sx, sy, sz), _t(-0.5, -0.5, -0.5)),
            grip=tuple(c / 16.0 for c in grip_center),
            tip=tuple(c / 16.0 for c in geometry["blade_tip"]),
            butt=(axis[0], butt_y / 16.0, axis[1]),
            axis=axis,
            handle=(float(geometry["handle"]["y"][0]), float(geometry["handle"]["y"][1])),
            grip_y=float(grip_center[1]),
            off_hand_y=None if off_hand is None else float(off_hand[1]),
            trail_tip_y=float(geometry["trail"]["tip"][1] if "trail" in geometry else geometry["blade_tip"][1]),
            display_scale=float(sy),
            corners=_element_corners(model.get("elements", [])),
        )
    except (KeyError, IndexError, TypeError, ValueError) as exc:
        raise PoseTableError(f"weapon rig {geometry_path.name} / {model_path.name}: missing {exc}") from exc
    _RIGS[cache_key] = rig
    return rig


def weapon_rig(table: PoseTable = BASIC_SWORD) -> WeaponRig:
    return load_rig(table.geometry, table.item_model)


def grip_compensation(item_rotation, table: PoseTable = BASIC_SWORD) -> tuple[float, float, float]:
    """PAL right_item position that keeps the weapon's grip centre in the fist for a right_item rotation.

    PAL rotates the item about the layer's item origin (the front-bottom edge of the fist), so a
    large right_item rotation swings the handle out of the hand.  Translating the item by
    K (I - R) g, with K the layer's Rx(-90) Ry(180) and g the grip centre after the display
    transform, turns that into a rotation about the grip centre.
    """
    ix, iy, iz = item_rotation
    rig = weapon_rig(table)
    g = _apply_raw(_chain(*rig.display), rig.grip)
    r = _chain(_r4("z", -iy), _r4("y", -iz), _r4("x", -ix))
    rg = _apply_raw(r, g)
    k = _chain(_r4("x", -90), _r4("y", 180))
    offset = _apply_raw(k, tuple(g[i] - rg[i] for i in range(3)))
    return (offset[0] * 16, -offset[1] * 16, offset[2] * 16)


def _apply_raw(m, p):
    v = (p[0], p[1], p[2], 1.0)
    return tuple(sum(m[i][k] * v[k] for k in range(4)) for i in range(3))


def _root_frame(pose):
    bx, by, bz = pose["body"]
    px, py, pz = pose["body_pos"]
    return _chain(_t(-px / 16, py / 16 + 0.75, pz / 16), _r4("z", bz), _r4("y", -by), _r4("x", -bx),
                  _t(0, -0.75, 0), _s(-1, -1, 1), _s(0.9375, 0.9375, 0.9375), _t(0, -1.501, 0))


def _part_frame(root, pivot, rotation):
    x, y, z = rotation
    return _chain(root, _t(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16), _r4("z", z), _r4("y", y), _r4("x", x))


def _item_frame(right_arm, pose, rig: WeaponRig):
    ix, iy, iz = pose["right_item"]
    qx, qy, qz = pose.get("right_item_pos", (0.0, 0.0, 0.0))
    # ItemInHandLayerMixin.changeItemLocation: translate(posX, -posY, posZ) / 16 before the layer's rotations.
    return _chain(right_arm, _t(qx / 16, -qy / 16, qz / 16), _r4("x", -90), _r4("y", 180), _t(1 / 16, 0.125, -0.625),
                  _r4("z", -iy), _r4("y", -iz), _r4("x", -ix), *rig.display)


def skeleton(pose: dict[str, list[float]], table: PoseTable = BASIC_SWORD) -> dict[str, tuple[float, float, float]]:
    """World points of a resolved pose with the table's weapon in the right hand."""
    rig = weapon_rig(table)
    root = _root_frame(pose)
    right_arm = _part_frame(root, RIGHT_ARM_PIVOT, pose["right_arm"])
    left_arm = _part_frame(root, LEFT_ARM_PIVOT, pose["left_arm"])
    right_leg = _part_frame(root, (-1.9, 12, 0), pose["right_leg"])
    left_leg = _part_frame(root, (1.9, 12, 0), pose["left_leg"])
    item = _item_frame(right_arm, pose, rig)
    return {
        "hip": _apply(root, (0, 0.75, 0)),
        "right_shoulder": _apply(right_arm, (0, 0, 0)),
        "left_shoulder": _apply(left_arm, (0, 0, 0)),
        "right_hand": _apply(right_arm, (-1 / 16, 10 / 16, 0)),
        "left_hand": _apply(left_arm, (1 / 16, 10 / 16, 0)),
        "right_foot": _apply(right_leg, (0, 12 / 16, 0)),
        "left_foot": _apply(left_leg, (0, 12 / 16, 0)),
        "fist": _apply(right_arm, RIGHT_FIST),
        "left_fist": _apply(left_arm, LEFT_FIST),
        "grip": _apply(item, rig.grip),
        "tip": _apply(item, rig.tip),
        "butt": _apply(item, rig.butt),
        "handle_start": _apply(item, rig.point(rig.handle[0])),
        "handle_end": _apply(item, rig.point(rig.handle[1])),
    }


def off_hand_offset(sk: dict, table: PoseTable = BASIC_SWORD) -> tuple[float, float]:
    """(distance of the left fist from the shaft in player-model px, contract y of the nearest shaft point).

    The shaft is the weapon axis over the contract's ``handle`` range.
    """
    h0, h1 = weapon_rig(table).handle
    a, b, p = sk["handle_start"], sk["handle_end"], sk["left_fist"]
    ab = [b[i] - a[i] for i in range(3)]
    t = sum((p[i] - a[i]) * ab[i] for i in range(3)) / (sum(v * v for v in ab) or 1.0)
    t = max(0.0, min(1.0, t))
    nearest = [a[i] + ab[i] * t for i in range(3)]
    return math.dist(p, nearest) * 16 / PLAYER_SCALE, h0 + (h1 - h0) * t


def _unit(v):
    norm = math.sqrt(sum(c * c for c in v))
    return [c / norm for c in v] if norm > 1e-12 else [0.0, -1.0, 0.0]


def shaft_target(pose: dict[str, list[float]], table: PoseTable, at: float | None = None) -> tuple[float, float]:
    """(contract y, residual in blocks) of the shaft point the rigid left arm puts its fist on.

    The left fist moves on a sphere of radius |LEFT_FIST| * 0.9375 about the left shoulder pivot.
    Of the handle points on that sphere (keeping one fist width clear of the right fist), the one
    closest to the preferred y (``at``, else the contract's off-hand grip) is used.  When no handle
    point is on the sphere, the handle point whose distance from the sphere is smallest is used and
    that distance is the residual.
    """
    rig = weapon_rig(table)
    preferred = rig.off_hand_y if at is None else at
    if preferred is None:
        raise PoseTableError(f"{table.geometry.name} has no off_hand_grip_center for an ON_SHAFT left hand")
    root = _root_frame(pose)
    item = _item_frame(_part_frame(root, RIGHT_ARM_PIVOT, pose["right_arm"]), pose, rig)
    origin, step = _apply(item, rig.point(0.0)), _apply(item, rig.point(1.0))
    u = [step[i] - origin[i] for i in range(3)]  # world blocks per contract px
    shoulder = _apply(root, (LEFT_ARM_PIVOT[0] / 16, LEFT_ARM_PIVOT[1] / 16, LEFT_ARM_PIVOT[2] / 16))
    reach = math.hypot(*LEFT_FIST) * PLAYER_SCALE
    w = [origin[i] - shoulder[i] for i in range(3)]
    uu = sum(v * v for v in u)
    uw = sum(u[i] * w[i] for i in range(3))
    ww = sum(v * v for v in w)
    gap = FIST_WIDTH / math.sqrt(uu)
    h0, h1 = rig.handle
    ranges = [(lo, hi) for lo, hi in ((h0, min(h1, rig.grip_y - gap)), (max(h0, rig.grip_y + gap), h1)) if lo <= hi]
    closest = -uw / uu
    candidates = []
    for lo, hi in ranges:
        candidates += [lo, hi, max(lo, min(hi, closest))]
        disc = uw * uw - uu * (ww - reach * reach)
        if disc >= 0.0:
            candidates += [s for s in ((-uw - math.sqrt(disc)) / uu, (-uw + math.sqrt(disc)) / uu) if lo <= s <= hi]

    def residual(s):
        return abs(math.sqrt(uu * s * s + 2 * uw * s + ww) - reach)

    best = min(candidates, key=lambda s: (round(residual(s), 9), abs(s - preferred)))
    return best, residual(best)


def solve_off_hand(pose: dict[str, list[float]], table: PoseTable, at: float | None = None,
                   previous=None, previous_body=None) -> tuple[float, float]:
    """Left-arm rotation (x, y) that puts the left fist on the shaft point of :func:`shaft_target`.

    The fist sits 1 px off the arm's pivot axis, on the side set by the arm's twist, so each of the
    two Euler twists (z = 0) aims its axis at the target minus that offset, refined by fixed-point
    iteration.  Of the twists that put the fist closest to the target, the one whose interpolation
    from the previous key stays nearest the direct path is used (as :func:`solve_limb` does).
    """
    rig = weapon_rig(table)
    body = pose["body"]
    s, _ = shaft_target(pose, table, at)
    root = _root_frame(pose)
    item = _item_frame(_part_frame(root, RIGHT_ARM_PIVOT, pose["right_arm"]), pose, rig)
    target = _apply(item, rig.point(s))
    shoulder = _apply(root, (LEFT_ARM_PIVOT[0] / 16, LEFT_ARM_PIVOT[1] / 16, LEFT_ARM_PIVOT[2] / 16))
    reach = math.hypot(*LEFT_FIST) * PLAYER_SCALE
    toward = _unit([target[i] - shoulder[i] for i in range(3)])
    branches = []
    for branch in (0, 1):
        aim = toward
        for _ in range(12):
            x, y = _limb_candidates(body, _unit(aim))[branch]
            arm = _part_frame(root, LEFT_ARM_PIVOT, (x, y, 0.0))
            on_axis = _apply(arm, (0.0, LEFT_FIST[1], 0.0))
            fist = _apply(arm, LEFT_FIST)
            aim = [toward[i] * reach - (fist[i] - on_axis[i]) for i in range(3)]
        branches.append(((x, y), math.dist(fist, target)))
    best = min(error for _, error in branches)
    options = [(x, y + wrap) for (x, y), error in branches if error <= best + 0.01 / 16
               for wrap in (-360.0, 0.0, 360.0)]
    if previous is None:
        return min(options, key=lambda c: (abs(c[1]), abs(c[0])))
    start = limb_direction(previous_body, previous)
    mid_body = [(previous_body[i] + body[i]) / 2.0 for i in range(3)]

    def deviation(c):
        end = limb_direction(body, c)
        ideal = _unit([start[i] + end[i] for i in range(3)])
        mid = limb_direction(mid_body, ((previous[0] + c[0]) / 2.0, (previous[1] + c[1]) / 2.0))
        dot = max(-1.0, min(1.0, sum(mid[i] * ideal[i] for i in range(3))))
        return (round(math.degrees(math.acos(dot)), 3), abs(c[0] - previous[0]) + abs(c[1] - previous[1]))

    return min(options, key=deviation)


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
# The world trail (剑光) other players see, ported from client/combat/CombatWorldTrails.java and
# combat/definition/HitboxGenerators.java, so the poses can be checked against it.  The trail is
# drawn from a pivot PIVOT_HEIGHT above the feet on the body's centre line toward the FAR end of
# each hit sample, its head at min(sample length, tip radius) from the pivot; the tip radius is
# ARM_REACH plus the weapon's grip-to-trail-tip length at its third-person display scale.  Samples
# of one tick sit evenly through it (sampleTime) and are interpolated in polar form (polarLerp).
# Server samples use +x = the player's LEFT; the trail head is returned in the rig frame
# (+x = right, y up, z forward), relative to the feet like the skeleton.
# ---------------------------------------------------------------------------------------------

TRAIL_PIVOT_HEIGHT = 1.3
TRAIL_ARM_REACH = 0.705
TRAIL_STREAK_OVERSHOOT = 1.15


def hit_samples(move: Move) -> list[tuple[int, tuple, tuple]]:
    """(tick, start, end) of the move's hitbox samples in the server frame, generators expanded."""
    data = json.loads(move.samples)
    if isinstance(data, list):
        return [(int(d["tick"]), tuple(d["start"]), tuple(d["end"])) for d in data]
    start, end = move.active_start, move.active_end
    out = []
    for tick in range(start, end + 1):
        progress = (tick - start) / max(1, end - start)
        if data["generator"] == "thrust":
            reach = data["first_range"] + (data["final_range"] - data["first_range"]) * progress
            out.append((tick, (0.0, 1.05, 0.55), (0.0, 1.20, reach)))
        elif data["generator"] == "arc":
            angle = math.radians(data["start_angle"] + (data["end_angle"] - data["start_angle"]) * progress)
            h, r = data["height"], data["range"]
            out.append((tick, (math.sin(angle) * 0.45, h, math.cos(angle) * 0.45),
                        (math.sin(angle) * r, h, math.cos(angle) * r)))
        else:  # diagonal
            side, opposite = -0.75 + 1.50 * progress, 0.95 - 1.90 * progress
            low, high = 0.45 + 0.30 * progress, 1.90 - 0.15 * progress
            out.append((tick, (-side, high, 0.55), (-opposite, low, 2.75)) if data["descending"]
                       else (tick, (side, low, 0.55), (opposite, high, 2.55)))
    return out


def trail_tip_radius(table: PoseTable) -> float:
    rig = weapon_rig(table)
    return TRAIL_ARM_REACH + (rig.trail_tip_y - rig.grip_y) * rig.display_scale / 16.0


def trail_sample_times(samples) -> list[float]:
    """sampleTime: the n samples of tick t sit at t + (i + 0.5) / n - 0.5."""
    times = []
    for index, (tick, _, _) in enumerate(samples):
        same = [i for i, s in enumerate(samples) if s[0] == tick]
        times.append(tick + (same.index(index) + 0.5) / len(same) - 0.5)
    return times


def _polar_lerp(a, b, u):
    angle_a, angle_b = math.atan2(a[0], a[2]), math.atan2(b[0], b[2])
    angle = angle_a + (angle_b - angle_a) * u
    radius_a = math.hypot(a[0], a[2])
    radius = radius_a + (math.hypot(b[0], b[2]) - radius_a) * u
    return (math.sin(angle) * radius, a[1] + (b[1] - a[1]) * u, math.cos(angle) * radius)


def trail_head(samples, time: float, radius: float, tip_scale: float = 1.0) -> tuple[float, float, float]:
    """The drawn trail's head (tip of the newest blade) at action time ``time``, in the rig frame."""
    times = trail_sample_times(samples)
    before, after = 0, len(samples) - 1
    for index, t in enumerate(times):
        if t <= time:
            before = index
        if t >= time:
            after = index
            break
    end = samples[before][2]
    if before != after and times[after] > times[before]:
        u = max(0.0, min(1.0, (time - times[before]) / (times[after] - times[before])))
        end = _polar_lerp(samples[before][2], samples[after][2], u)
    x, y, z = end[0], end[1] - TRAIL_PIVOT_HEIGHT, end[2]
    length = math.sqrt(x * x + y * y + z * z)
    scale = min(length, radius) * tip_scale / length
    return (-x * scale, TRAIL_PIVOT_HEIGHT + y * scale, z * scale)


def trail_residuals(move: Move, table: PoseTable, steps_per_tick: int = 6) -> list[tuple[float, float]]:
    """(action time, distance in blocks from the posed weapon tip to the trail head) while the trail
    head moves, from the first to the last sample's time.  A thrust's streak head is drawn at
    TRAIL_STREAK_OVERSHOOT times the radius and is compared over the active ticks."""
    samples = hit_samples(move)
    times = trail_sample_times(samples)
    radius = trail_tip_radius(table)
    scale = TRAIL_STREAK_OVERSHOOT if move.kind == "thrust" else 1.0
    first, last = times[0], times[-1]
    if last - first < 1e-9:
        first, last = move.active_start, move.active_end
    poses = resolve(move.keys, table)
    count = max(1, round((last - first) * steps_per_tick))
    out = []
    for i in range(count + 1):
        time = first + (last - first) * i / count
        tip = skeleton(sample(move.keys, poses, time), table)["tip"]
        out.append((time, math.dist(tip, trail_head(samples, time, radius, scale))))
    return out


# ---------------------------------------------------------------------------------------------
# Self-checks.
# ---------------------------------------------------------------------------------------------

def _key_at(move: Move, phase: str) -> int:
    for index, k in enumerate(move.keys):
        if k.phase == phase:
            return index
    raise AssertionError(f"{move.animation_id}: missing {phase} key")


def measure(move: Move, table: PoseTable = BASIC_SWORD) -> dict:
    poses = resolve(move.keys, table)
    return {"poses": poses, "skeletons": [skeleton(p, table) for p in poses]}


def _check_off_hand(name: str, keys: tuple[Key, ...], poses, skeletons, table: PoseTable) -> list[str]:
    """Two-handed tables: an ON_SHAFT left hand is on the shaft at its keys and stays on it between
    two ON_SHAFT keys (sampled like the grip drift).  A key with an explicit larm is a release."""
    errors: list[str] = []
    rig = weapon_rig(table)
    on_shaft = [isinstance(k.larm, OnShaft) for k in keys]
    for k, sk, held in zip(keys, skeletons, on_shaft):
        if not held:
            continue
        off, at = off_hand_offset(sk, table)
        if off > OFF_HAND_KEY_TOLERANCE_PX:
            wanted = rig.off_hand_y if k.larm.at is None else k.larm.at
            errors.append(
                f"{name}: left hand {off:.2f} px off the shaft at tick {k.tick} (> {OFF_HAND_KEY_TOLERANCE_PX:g}; "
                f"nearest reachable shaft y {at:.1f}, wanted {wanted:g}): the shaft is out of the left arm's reach, "
                f"bring the weapon closer to the left shoulder or release the hand with an explicit larm")
    for i in range(1, len(keys)):
        if not (on_shaft[i - 1] and on_shaft[i]):
            continue
        start, end = keys[i - 1].tick, keys[i].tick
        worst, worst_tick = 0.0, start
        for step in range(1, 8):
            tick = start + (end - start) * step / 8
            off, _ = off_hand_offset(skeleton(sample(keys, poses, tick), table), table)
            if off > worst:
                worst, worst_tick = off, tick
        if worst > OFF_HAND_BETWEEN_TOLERANCE_PX:
            suggestion = min(max(round(worst_tick), start + 1), end - 1)
            hint = (f"add a key with larm=ON_SHAFT at tick {suggestion}" if start < suggestion < end
                    else "the keys are one tick apart; reduce the change between them")
            errors.append(f"{name}: left hand leaves the shaft by {worst:.2f} px near tick {worst_tick:.2f} "
                          f"between keys {start} and {end} (> {OFF_HAND_BETWEEN_TOLERANCE_PX:g}); {hint}")
    return errors


# Vanilla body boxes (label, bone, pivot, box min, box max in model px) that a long weapon must not
# pass through, as rendered: each box carries its skin's outer layer (hat +0.5 px, jacket, sleeves
# and trousers +0.25 px).  The head box also closes the 2 px notch under the jaw and the back of
# the head, where the 8 px deep head overhangs the 4 px deep torso: a shaft lodged there clears
# both boxes by about a pixel but reads as a yoke across the neck and jaw.  The arms are tested as
# their shoulder half (the lower 6 px are the hand cube and the wrist that close round the shaft):
# the right upper arm against the shaft from the grip to the tip only (the butt side may lie along
# a forearm driven along the shaft, as in a thrust; the tip side crossing back through the arm is
# the arm and the spear folded against each other), the left upper arm against the whole shaft
# except where the shaft runs within ARM_ALONG_SHAFT_DEG of the arm.  A released left hand is
# tested with its hand cube.
SKIN_LAYER = 0.25
HAT_LAYER = 0.5
NECK_NOTCH = 2.0
BODY_BOXES = (
    ("torso", "torso", (0.0, 0.0, 0.0), (-4.0 - SKIN_LAYER, -SKIN_LAYER, -2.0 - SKIN_LAYER),
     (4.0 + SKIN_LAYER, 12.0 + SKIN_LAYER, 2.0 + SKIN_LAYER)),
    ("head and neck", "head", (0.0, 0.0, 0.0), (-4.0 - HAT_LAYER, -8.0 - HAT_LAYER, -4.0 - HAT_LAYER),
     (4.0 + HAT_LAYER, NECK_NOTCH, 4.0 + HAT_LAYER)),
    ("right leg", "right_leg", (-1.9, 12.0, 0.0), (-2.0 - SKIN_LAYER, -SKIN_LAYER, -2.0 - SKIN_LAYER),
     (2.0 + SKIN_LAYER, 12.0 + SKIN_LAYER, 2.0 + SKIN_LAYER)),
    ("left leg", "left_leg", (1.9, 12.0, 0.0), (-2.0 - SKIN_LAYER, -SKIN_LAYER, -2.0 - SKIN_LAYER),
     (2.0 + SKIN_LAYER, 12.0 + SKIN_LAYER, 2.0 + SKIN_LAYER)),
)
RIGHT_UPPER_ARM = ((-3.0 - SKIN_LAYER, -2.0 - SKIN_LAYER, -2.0 - SKIN_LAYER), (1.0 + SKIN_LAYER, 4.0, 2.0 + SKIN_LAYER))
LEFT_UPPER_ARM = ((-1.0 - SKIN_LAYER, -2.0 - SKIN_LAYER, -2.0 - SKIN_LAYER), (3.0 + SKIN_LAYER, 4.0, 2.0 + SKIN_LAYER))
LEFT_WHOLE_ARM = ((-1.0 - SKIN_LAYER, -2.0 - SKIN_LAYER, -2.0 - SKIN_LAYER), (3.0 + SKIN_LAYER, 10.0 + SKIN_LAYER, 2.0 + SKIN_LAYER))
# A shaft through the fist centre (8 px down the arm) at less than atan(2 / 4) = 27 degrees to the
# arm grazes a 4 px wide box ending 4 px above it however it is held; such a shaft lies along the arm.
ARM_ALONG_SHAFT_DEG = 30.0
SHAFT_SAMPLES = 48


def _affine_inverse(m):
    """Inverse of an affine 4x4 matrix (rotation * uniform scale + translation)."""
    a = [row[:3] for row in m[:3]]
    det = (a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
           + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]))
    inv = [[(a[(j + 1) % 3][(i + 1) % 3] * a[(j + 2) % 3][(i + 2) % 3]
             - a[(j + 1) % 3][(i + 2) % 3] * a[(j + 2) % 3][(i + 1) % 3]) / det for j in range(3)] for i in range(3)]
    t = [m[i][3] for i in range(3)]
    return [inv[i] + [-sum(inv[i][k] * t[k] for k in range(3))] for i in range(3)] + [[0.0, 0.0, 0.0, 1.0]]


def _box_gap(frame, points, low, high) -> float:
    """Smallest distance in model px from world points (skeleton frame) to a box of a part frame."""
    inverse = _affine_inverse(frame)
    best = math.inf
    for p in points:
        raw = (p[0], p[1], -p[2], 1.0)  # skeleton points are (right, up, forward); frames use -z forward
        local = [sum(inverse[i][k] * raw[k] for k in range(4)) * 16.0 for i in range(3)]
        best = min(best, math.sqrt(sum(max(low[i] - local[i], 0.0, local[i] - high[i]) ** 2 for i in range(3))))
    return best


def _segment(a, b, samples):
    return [[a[j] + (b[j] - a[j]) * i / samples for j in range(3)] for i in range(samples + 1)]


def shaft_body_clearance(pose: dict[str, list[float]], sk: dict, samples: int = SHAFT_SAMPLES,
                         left_free: bool = False) -> tuple[float, str]:
    """(smallest distance in player-model px from the weapon axis to a body part; that part's name).

    The torso, head and legs are tested against the whole axis, butt to tip; the arms as described
    at :data:`BODY_BOXES`.  ``left_free``: the left hand is off the shaft (tested with its fist).
    """
    gaps = shaft_body_gaps(pose, sk, samples, left_free)
    where = min(gaps, key=gaps.get)
    return gaps[where], where


def shaft_body_gaps(pose: dict[str, list[float]], sk: dict, samples: int = SHAFT_SAMPLES,
                    left_free: bool = False) -> dict[str, float]:
    """Distance in player-model px from the weapon axis to each body part (see :func:`shaft_body_clearance`)."""
    root = _root_frame(pose)
    shaft = _segment(sk["butt"], sk["tip"], samples)
    gaps = {label: _box_gap(_part_frame(root, pivot, pose[bone]), shaft, low, high)
            for label, bone, pivot, low, high in BODY_BOXES}
    right_arm = _part_frame(root, RIGHT_ARM_PIVOT, pose["right_arm"])
    gaps["right upper arm"] = _box_gap(right_arm, _segment(sk["grip"], sk["tip"], samples), *RIGHT_UPPER_ARM)
    left_arm = _part_frame(root, LEFT_ARM_PIVOT, pose["left_arm"])
    if left_free:
        gaps["left arm"] = _box_gap(left_arm, shaft, *LEFT_WHOLE_ARM)
    else:
        arm_axis = _unit([sk["left_fist"][i] - sk["left_shoulder"][i] for i in range(3)])
        shaft_axis = _unit([sk["tip"][i] - sk["butt"][i] for i in range(3)])
        along = abs(sum(arm_axis[i] * shaft_axis[i] for i in range(3))) >= math.cos(math.radians(ARM_ALONG_SHAFT_DEG))
        gaps["left upper arm"] = math.inf if along else _box_gap(left_arm, shaft, *LEFT_UPPER_ARM)
    return gaps


def _check_long_weapon(name: str, keys: tuple[Key, ...], poses, table: PoseTable) -> list[str]:
    """The table's long-weapon rules at every key and at eight samples between keys: the butt and
    the tip stay above the ground, the hands stay apart on the shaft, and the shaft stays out of
    the body."""
    rules = table.rules
    if (rules.weapon_ground_clearance, rules.min_hand_separation_px, rules.shaft_body_clearance_px) == (None,) * 3:
        return []
    grip_y = weapon_rig(table).grip_y
    found: dict[str, tuple[float, float, str]] = {}  # rule -> (worst value, tick, detail)

    def note(rule, value, tick, detail="", low=True):
        if rule not in found or (value < found[rule][0] if low else value > found[rule][0]):
            found[rule] = (value, tick, detail)

    held = [isinstance(k.larm, OnShaft) for k in keys]
    for i, k in enumerate(keys):
        steps = [(k.tick, poses[i], held[i], held[i])]
        if i + 1 < len(keys):
            # Between a held and a released key the left fist leaves (or reaches) the shaft: test
            # it like a held hand (upper arm only), but do not require the hands apart.
            steps += [(k.tick + (keys[i + 1].tick - k.tick) * s / 8, None, held[i] and held[i + 1],
                       held[i] or held[i + 1]) for s in range(1, 8)]
        for tick, pose, on_shaft, touching in steps:
            pose = sample(keys, poses, tick) if pose is None else pose
            sk = skeleton(pose, table)
            if rules.weapon_ground_clearance is not None:
                height, part = weapon_lowest_point(pose, table)
                note("ground", height, tick, part)
            if rules.min_hand_separation_px is not None and on_shaft:
                note("hands", off_hand_offset(sk, table)[1] - grip_y, tick)
            if rules.shaft_body_clearance_px is not None:
                for box, gap in shaft_body_gaps(pose, sk, left_free=not touching).items():
                    note("body:" + box, gap, tick, box)
    errors = []
    if "ground" in found and found["ground"][0] < rules.weapon_ground_clearance:
        value, tick, end = found["ground"]
        errors.append(f"{name}: weapon {end} {value:.3f} block above the ground near tick {tick:.2f} "
                      f"(< {rules.weapon_ground_clearance:g}; the model's lowest corner)")
    if "hands" in found and found["hands"][0] < rules.min_hand_separation_px:
        value, tick, _ = found["hands"]
        errors.append(f"{name}: hands only {value:.1f} px apart on the shaft near tick {tick:.2f} "
                      f"(< {rules.min_hand_separation_px:g})")
    for rule, (value, tick, box) in found.items():
        if rule.startswith("body:") and value < rules.shaft_body_clearance_px:
            errors.append(f"{name}: shaft {value:.2f} px from the {box} near tick {tick:.2f} "
                          f"(< {rules.shaft_body_clearance_px:g}; inside it at 0)")
    return errors


def long_weapon_margins(keys: tuple[Key, ...], table: PoseTable) -> dict[str, float]:
    """Smallest butt/tip height (blocks), hand separation (contract px) and shaft-body clearance
    (player-model px) over the keys and eight samples between keys, for --report."""
    poses = resolve(keys, table)
    grip_y = weapon_rig(table).grip_y
    out = {"ground": math.inf, "hands": math.inf, "body": math.inf}
    for i, k in enumerate(keys):
        both = isinstance(k.larm, OnShaft)
        ticks = [(k.tick, both, both)]
        if i + 1 < len(keys):
            other = isinstance(keys[i + 1].larm, OnShaft)
            ticks += [(k.tick + (keys[i + 1].tick - k.tick) * s / 8, both and other, both or other) for s in range(1, 8)]
        for tick, on_shaft, touching in ticks:
            pose = sample(keys, poses, tick)
            sk = skeleton(pose, table)
            out["ground"] = min(out["ground"], weapon_lowest_point(pose, table)[0])
            if on_shaft:
                out["hands"] = min(out["hands"], off_hand_offset(sk, table)[1] - grip_y)
            out["body"] = min(out["body"], shaft_body_clearance(pose, sk, left_free=not touching)[0])
    return out


def check_keys(name: str, keys: tuple[Key, ...], table: PoseTable = BASIC_SWORD) -> list[str]:
    """Checks shared by every animation: grounded feet, blade intent, smooth limbs and blade, and
    for a two-handed table the left hand on the shaft."""
    errors: list[str] = []
    poses = resolve(keys, table)
    skeletons = [skeleton(p, table) for p in poses]

    def fail(message):
        errors.append(f"{name}: {message}")

    # Feet stay planted (lowest foot on the ground) at every key.
    for k, sk in zip(keys, skeletons):
        low = min(sk["right_foot"][1], sk["left_foot"][1]) * 16
        if abs(low) > 0.6:
            fail(f"feet off the ground by {low:.2f} px at tick {k.tick}")

    # The weapon's grip centre stays in the fist (the fist is 4 px wide): exact at every key thanks
    # to the right_item position compensation, and within the fist between keys.
    for k, sk in zip(keys, skeletons):
        off = math.dist(sk["grip"], sk["fist"]) * 16 / 0.9375
        if off > 0.1:
            fail(f"grip {off:.2f} px from the fist centre at tick {k.tick}")
    for i in range(1, len(keys)):
        for step in range(1, 8):
            tick = keys[i - 1].tick + (keys[i].tick - keys[i - 1].tick) * step / 8
            sk = skeleton(sample(keys, poses, tick), table)
            off = math.dist(sk["grip"], sk["fist"]) * 16 / 0.9375
            if off > 1.9:
                fail(f"grip leaves the fist by {off:.2f} px near tick {tick:.2f}")

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
            sk = skeleton(sample(keys, poses, start_tick + (end_tick - start_tick) * s_index / steps), table)
            directions.append(direction_angles(sk["grip"], sk["tip"]))
        path = sum(angle_between(directions[j], directions[j + 1]) for j in range(steps))
        waste = path - angle_between(directions[0], directions[-1])
        if waste > 30.0:
            fail(f"blade wobbles {waste:.0f} deg between ticks {start_tick} and {end_tick}")

    if table.two_handed:
        errors.extend(_check_off_hand(name, keys, poses, skeletons, table))
    errors.extend(_check_long_weapon(name, keys, poses, table))
    return errors


_PATH_OPS = {
    "<": lambda v, limit: v < limit,
    ">": lambda v, limit: v > limit,
    "<=": lambda v, limit: v <= limit,
    ">=": lambda v, limit: v >= limit,
    "abs<=": lambda v, limit: abs(v) <= limit,
}


def _path_measure(sk: dict, measure_name: str) -> float:
    if measure_name.startswith("tip_"):
        return sk["tip"]["xyz".index(measure_name[-1])]
    yaw, elevation = direction_angles(sk["grip"], sk["tip"])
    return {"blade_yaw": yaw, "blade_elevation": elevation}[measure_name]


def check_move(move: Move, table: PoseTable = BASIC_SWORD) -> list[str]:
    """Pose checks of one move against its style timing (active window, step tick) and kind."""
    errors: list[str] = []
    name = move.animation_id
    keys = move.keys
    ticks = [k.tick for k in keys]
    rules = table.rules
    info = measure(move, table)
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
    canonical = canonical_guard(table)
    if poses[0] != canonical or poses[-1] != canonical:
        fail("moves must start and end on the canonical guard pose")
    errors.extend(check_keys(name, keys, table))

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
    low_drop, high_drop = rules.hip_drop_px
    for index in (contact, through, hold):
        drop = -poses[index]["body_pos"][1]
        if not low_drop - 1e-6 <= drop <= high_drop + 1e-6:
            fail(f"hip drop {drop:.2f} px at tick {keys[index].tick} ({low_drop:g}-{high_drop:g})")

    # Chest twist stays within what PAL can show without detaching the arms.
    for pose in poses:
        if abs(pose["torso"][1]) > TORSO_TWIST_LIMIT + 1e-6:
            fail("torso twist exceeds the PAL-safe limit")

    body_yaws = [p["body"][1] for p in poses]
    path = None if move.cut_path is None else table.cut_paths[move.cut_path]
    if move.kind == "cut":
        turn = max(body_yaws) - min(body_yaws)
        low_turn, high_turn = rules.cut_turn_deg
        if not low_turn <= turn <= high_turn:
            fail(f"whole-figure turn {turn:.0f} deg peak-to-peak ({low_turn:g}-{high_turn:g})")
        for index in (through, hold) if path is None or path.side_finish else ():
            sk = skeletons[index]
            hand_lateral = sk["right_hand"][0]
            blade_yaw, _ = direction_angles(sk["grip"], sk["tip"])
            if rules.cut_end_hand_lateral is not None and abs(hand_lateral) < rules.cut_end_hand_lateral:
                fail(f"hand only {hand_lateral:+.2f} block lateral at tick {keys[index].tick} "
                     f"(>= {rules.cut_end_hand_lateral:g})")
            if rules.cut_end_blade_yaw is not None:
                low_yaw, high_yaw = rules.cut_end_blade_yaw
                if not low_yaw <= abs(blade_yaw) <= high_yaw:
                    fail(f"blade yaw {blade_yaw:+.0f} at tick {keys[index].tick} (+-{low_yaw:g}..{high_yaw:g})")
            if rules.cut_end_tip_lateral is not None and abs(sk["tip"][0]) < rules.cut_end_tip_lateral:
                fail(f"blade tip only {sk['tip'][0]:+.2f} block outside the silhouette at tick {keys[index].tick}")
    else:
        low_body, high_body = rules.lunge_body_yaw if move.lunge and rules.lunge_body_yaw else rules.thrust_body_yaw
        for index in (contact, through, hold):
            sk = skeletons[index]
            blade_yaw, blade_elevation = direction_angles(sk["grip"], sk["tip"])
            body_yaw = poses[index]["body"][1]
            if (low_body is not None and body_yaw < low_body) or (high_body is not None and body_yaw > high_body):
                fail(f"thrust body is not bladed at tick {keys[index].tick}")
            if abs(blade_yaw) > 20.0 or abs(blade_elevation) > 15.0:
                fail(f"thrust blade not in line at tick {keys[index].tick}: {blade_yaw:.0f}/{blade_elevation:.0f}")
            if rules.thrust_left_hand_lateral is not None and sk["left_hand"][0] > rules.thrust_left_hand_lateral:
                fail(f"left 剑指 arm does not open the T-shape at tick {keys[index].tick}")
            right_dir = direction_angles(sk["right_shoulder"], sk["right_hand"])
            if rules.thrust_arm_level is not None and abs(right_dir[1]) > rules.thrust_arm_level:
                fail(f"thrust arm not level at tick {keys[index].tick}")
            if sk["tip"][2] < (rules.thrust_reach[1] if move.lunge else rules.thrust_reach[0]):
                fail(f"thrust reach {sk['tip'][2]:.2f} too short at tick {keys[index].tick}")

    # Cut directions shared with the server hitboxes.
    if path is not None:
        where = {"wind": skeletons[contact - 1], "contact": skeletons[contact], "through": skeletons[through],
                 "hold": skeletons[hold]}
        for rule in path.rules:
            measured, held = [], True
            for place, measure_name, op, limit in rule.conditions:
                if place not in where:  # mid_active
                    mid = (move.active_start + move.active_end) / 2
                    where[place] = skeleton(sample(keys, poses, mid), table)
                value = _path_measure(where[place], measure_name)
                measured.append(value)
                held = held and _PATH_OPS[op](value, limit)
            if not held:
                fail(rule.message.format(*measured))

    # The spearhead stays with the world trail other players see.
    if path is not None and path.trail_tolerance is not None:
        worst_time, worst = max(trail_residuals(move, table), key=lambda r: r[1])
        if worst > path.trail_tolerance:
            fail(f"weapon tip {worst:.2f} block from the world trail head near tick {worst_time:.2f} "
                 f"(> {path.trail_tolerance:g})")

    # Lunge: 弓步 and forward drive timed to the server step.
    if move.lunge:
        coil = keys[contact - 1]
        if move.step_tick is None:
            fail("lunge needs a server step in the style file")
        elif coil.tick != move.step_tick:
            fail("lunge must leave the coiled hold on the step tick")
        if keys[contact].pos[1] > -4.0 or keys[through].pos[1] > -4.0:
            fail("lunge must drive the hips forward (pos z <= -4 px)")
        if not any(v != 0 for p in poses for v in p["body_pos"]):
            fail("lunge needs a non-zero body position")
        coil_ticks = keys[contact - 1].tick - keys[contact - 2].tick
        if coil_ticks < 2:
            fail("the finisher needs a readable 2-tick coiled hold")
    return errors


def check_document(document: dict, table: PoseTable = BASIC_SWORD, style: dict | None = None) -> list[str]:
    """Structure of the generated document against the style, plus every pose self-check."""
    style = combat_styles()[table.style_id] if style is None else style
    bound = bind(table, style)
    enter_id, idle_id = animation_names(style)
    errors: list[str] = []
    animations = document.get("animations", {})
    expected = {enter_id, idle_id, *(m.animation_id for m in bound)}
    if document.get("format_version") != FORMAT_VERSION:
        errors.append("format_version must be 1.8.0")
    if set(animations) != expected:
        errors.append(f"animation ids {sorted(animations)} != {sorted(expected)}")
    idle = animations.get(idle_id, {})
    idle_length = round(table.ready_idle_ticks / TICKS_PER_SECOND, 4)
    if idle.get("loop") is not True or idle.get("animation_length") != idle_length:
        errors.append(f"{idle_id} must loop with length {idle_length}")
    for move in bound:
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
        if move.lunge:
            position = bones.get("body", {}).get("position", {})
            if not any(isinstance(v, list) and any(x != 0 for x in v) for v in position.values()):
                errors.append(f"{move.animation_id} body.position needs a non-zero plain vector")
    # Idle and mode entry end in the guard so PAL hands over without a pop.
    guard_pose = canonical_guard(table)
    idle_poses = resolve(table.ready_idle, table)
    if idle_poses[0] != guard_pose or idle_poses[-1] != guard_pose:
        errors.append(f"{idle_id} must start and end on the canonical guard")
    if resolve(table.mode_enter, table)[-1] != guard_pose:
        errors.append(f"{enter_id} must end on the canonical guard")
    errors.extend(check_keys(idle_id, table.ready_idle, table))
    errors.extend(check_keys(enter_id, table.mode_enter, table))
    for move in bound:
        errors.extend(check_move(move, table))
    return errors


def report(table: PoseTable = BASIC_SWORD, style: dict | None = None) -> None:
    style = combat_styles()[table.style_id] if style is None else style
    fmt = lambda v: "(%+.2f,%+.2f,%+.2f)" % v
    print(f"## {table.style_id} -> {table.output.name} ({table.geometry.name}"
          f"{', two-handed' if table.two_handed else ''})")
    for move in bind(table, style):
        info = measure(move, table)
        print(f"== {move.animation_id} (total {move.total}, active {move.active_start}-{move.active_end}, chain {move.chain_tick})")
        for k, pose, sk in zip(move.keys, info["poses"], info["skeletons"]):
            yaw, elevation = direction_angles(sk["grip"], sk["tip"])
            line = (f"  t{k.tick:>2} {k.phase:<12} {k.easing:<13} body y{pose['body'][1]:+5.0f} drop{pose['body_pos'][1]:+5.2f}"
                    f" hand{fmt(sk['right_hand'])} tip{fmt(sk['tip'])} blade {yaw:+4.0f}/{elevation:+3.0f}"
                    f" lhand{fmt(sk['left_hand'])}")
            if table.two_handed:
                off, at = off_hand_offset(sk, table)
                line += f" shaft y{at:+5.1f} off {off:.2f}px" if isinstance(k.larm, OnShaft) else " free"
            print(line)
        rules = table.rules
        if (rules.weapon_ground_clearance, rules.min_hand_separation_px, rules.shaft_body_clearance_px) != (None,) * 3:
            margins = long_weapon_margins(move.keys, table)
            print(f"  margins (keys and between): weapon >= {margins['ground']:.2f} block above the ground, hands >= "
                  f"{margins['hands']:.1f} px apart, shaft >= {margins['body']:.2f} px from the body")
            residuals = trail_residuals(move, table)
            worst_time, worst = max(residuals, key=lambda r: r[1])
            mean = sum(r[1] for r in residuals) / len(residuals)
            print(f"  trail: tip to trail head max {worst:.2f} block near tick {worst_time:.2f}, mean {mean:.2f} "
                  f"(radius {trail_tip_radius(table):.3f})")


def _shown(path: Path) -> Path:
    try:
        return path.relative_to(ROOT)
    except ValueError:
        return path


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="verify the committed JSON and self-checks")
    parser.add_argument("--report", action="store_true", help="print forward-kinematics measurements")
    args = parser.parse_args(argv)
    try:
        styles = combat_styles()
        check_tables(styles, POSE_TABLES)
        documents = [(table, styles[table.style_id], build_document(table, styles[table.style_id]))
                     for table in POSE_TABLES]
    except combat_data.CombatDataError as exc:
        for issue in exc.issues:
            print(f"COMBAT_DATA {issue}", file=sys.stderr)
        return 1
    except PoseTableError as exc:
        print(f"POSE_TABLE {exc}", file=sys.stderr)
        return 1
    status = 0
    for table, style, document in documents:
        errors = check_document(document, table, style)
        if args.report:
            report(table, style)
        if errors:
            for error in errors:
                print(f"SELF_CHECK {error}", file=sys.stderr)
            status = 1
            continue
        text = render(document)
        relative = _shown(table.output)
        if args.check:
            if not table.output.exists() or table.output.read_text(encoding="utf-8") != text:
                print(f"DRIFT {relative} differs from the generator output", file=sys.stderr)
                status = 1
            else:
                print(f"OK {table.output.name} matches the generator and passes all self-checks")
        elif not args.report:
            table.output.write_text(text, encoding="utf-8")
            print(f"wrote {relative}")
    return status


if __name__ == "__main__":
    raise SystemExit(main())
