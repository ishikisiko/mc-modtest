"""Leg solving and gait cycles for four-legged beasts.

A leg is three bones (upper, lower, paw) that bend about X only, plus the sole point in paw-local
units. `solve_legs` fills a pose's leg rotations so each sole lands on a target (planar two-bone IK
in the leg's Y-Z plane, the paw bone held at a chosen pitch); `gait_cycle` samples a looping walk or
run from foot-trajectory functions. Body bones (the legs' parents) must only pitch (rotate about X)
in any pose that is leg-solved, so the legs stay planar.
"""
from __future__ import annotations

import cmath
import math
from dataclasses import dataclass

from .anim import Key, pose_offsets, posed_matrices
from .cuboid import mat_apply

GROUND_Y = 24.0


@dataclass(frozen=True)
class Leg:
    name: str
    upper: str
    lower: str
    paw: str
    sole: tuple      # paw-local point that touches the ground at rest
    knee: str        # "back" (elbow behind the hip-ankle line) or "forward" (stifle in front)


def _wrap(a):
    return (a + math.pi) % (2 * math.pi) - math.pi


def _yz(p):
    return complex(p[1], p[2])


def frame_angle(m):
    """Pitch of a pitch-only model matrix: Rx(a) maps +Y to (0, cos a, sin a)."""
    return math.atan2(m[2][1], m[1][1])


def rest_soles(model, legs):
    mats = posed_matrices(model)
    return {leg.name: mat_apply(mats[leg.paw], leg.sole) for leg in legs}


def rest_paw_angles(model, legs):
    mats = posed_matrices(model)
    return {leg.name: frame_angle(mats[leg.paw]) for leg in legs}


def leg_ik(model, mats, leg, sole_target, paw_angle):
    """(theta_upper, theta_lower, theta_paw) in degrees, offsets from rest, and the residual distance
    between the reachable sole and the target (model units)."""
    up, lo, paw = model.bone(leg.upper), model.bone(leg.lower), model.bone(leg.paw)
    P = mats[up.parent]
    a_p = frame_angle(P)
    H = _yz(mat_apply(P, up.pivot))
    r1, r2, r3 = (math.radians(b.rotation[0]) for b in (up, lo, paw))
    k, a, s = _yz(lo.pivot), _yz(paw.pivot), _yz(leg.sole)
    L1, L2 = abs(k), abs(a)
    st = _yz(sole_target)
    at = st - s * cmath.exp(1j * paw_angle)
    D = at - H
    d = abs(D)
    dc = min(max(d, abs(L1 - L2) + 1e-6), L1 + L2 - 1e-6)
    beta = math.acos(max(-1.0, min(1.0, (L1 * L1 + dc * dc - L2 * L2) / (2 * L1 * dc))))
    best = None
    for sign in (1.0, -1.0):
        kdir = cmath.phase(D) + sign * beta
        K = H + L1 * cmath.exp(1j * kdir)
        kn = K - H
        cross = D.real * kn.imag - D.imag * kn.real
        want = cross > 0 if leg.knee == "back" else cross < 0
        a1 = kdir - cmath.phase(k)
        A = H + dc / d * D if d > 1e-9 else at
        a2 = cmath.phase(A - K) - cmath.phase(a)
        th = (_wrap(a1 - a_p - r1), _wrap(a2 - a1 - r2), _wrap(paw_angle - a2 - r3))
        cand = (0 if want else 1, abs(th[0]) + abs(th[1]), th, A)
        if best is None or cand[:2] < best[:2]:
            best = cand
    th, A = best[2], best[3]
    reached = A + s * cmath.exp(1j * paw_angle)
    return tuple(math.degrees(t) for t in th), abs(reached - st)


def solve_legs(model, pose, legs, targets):
    """targets: leg name -> (sole target in model space, paw pitch in radians, absolute in model space).
    Writes the leg rotations into `pose` (file convention) and returns the worst residual."""
    mats = posed_matrices(model, pose_offsets(pose))
    worst = 0.0
    for leg in legs:
        if leg.name not in targets:
            continue
        tgt, ang = targets[leg.name]
        th, err = leg_ik(model, mats, leg, tgt, ang)
        worst = max(worst, err)
        for bone, t in zip((leg.upper, leg.lower, leg.paw), th):
            old = pose.get(bone, {}).get("rotation", (0.0, 0.0, 0.0))
            pose.setdefault(bone, {})["rotation"] = (t, old[1], old[2])
    return worst


def planted(model, legs, shift=None, lift=None, pitch=None):
    """Targets for every leg: rest sole moved by shift[leg] = (dz) along Z, lifted by lift[leg] units,
    paw pitched by pitch[leg] degrees from its rest angle."""
    soles = rest_soles(model, legs)
    angles = rest_paw_angles(model, legs)
    out = {}
    for leg in legs:
        x, y, z = soles[leg.name]
        dz = (shift or {}).get(leg.name, 0.0)
        up = (lift or {}).get(leg.name, 0.0)
        out[leg.name] = ((x, y - up, z + dz), angles[leg.name] + math.radians((pitch or {}).get(leg.name, 0.0)))
    return out


def smooth(w):
    return w * w * (3 - 2 * w)


def foot_track(u, duty, stride, lift, flex):
    """Foot path for cycle fraction u (0 = touch-down): (dz, lift, paw pitch deg). Stance carries the
    foot from -stride/2 (front) to +stride/2 (back) at constant speed; the swing returns it on an arc."""
    if u < duty:
        return -stride / 2 + stride * (u / duty), 0.0, 0.0
    w = (u - duty) / (1 - duty)
    return stride / 2 - stride * smooth(w), lift * math.sin(math.pi * w) ** 1.2, flex * math.sin(math.pi * w)


def gait_cycle(model, legs, length, samples, body, feet, label=""):
    """Looping keys: body(phase) -> pose for non-leg bones; feet(leg name, phase) -> (dz, lift, pitch deg).
    Sample i sits at i / samples of the cycle; the last key repeats the first so the loop closes."""
    keys, worst = [], 0.0
    for i in range(samples + 1):
        ph = (i % samples) / samples
        pose = body(ph)
        shift, lift, pitch = {}, {}, {}
        for leg in legs:
            shift[leg.name], lift[leg.name], pitch[leg.name] = feet(leg.name, ph)
        worst = max(worst, solve_legs(model, pose, legs, planted(model, legs, shift, lift, pitch)))
        keys.append(Key(length * i / samples, pose, "catmullrom", label))
    return keys, worst


# Vanilla mob motion on flat ground with no movement input (LivingEntity.travel), as the server's
# BeastMotion runs it: each tick the entity moves by its velocity, then the horizontal speed is
# multiplied by 0.6 * 0.91 if it was on the ground at the start of that tick (0.91 if airborne) and
# the vertical speed becomes (vy - 0.08) * 0.98. A lunge is set on the ground, so its first tick
# takes ground friction even when it lifts off.
GRAVITY = 0.08
VERTICAL_DRAG = 0.98
GROUND_RETAIN = 0.6 * 0.91
AIR_RETAIN = 0.91


@dataclass(frozen=True)
class Flight:
    """One lunge from flat ground, per server tick from the lunge tick on: positions[t] is the feet
    (forward blocks along the locked yaw, height blocks) at the end of tick t; before the lunge tick the
    beast holds still at (0, 0). landing is the first tick whose move ends on the ground (the lunge
    tick itself for a ground lunge), apex the highest end-of-tick height."""
    lunge_tick: int
    forward_speed: float
    up: float
    positions: dict
    landing: int
    apex: float
    apex_tick: int
    rest_distance: float

    def at(self, tick):
        """(forward, height) at a fractional move tick: linear between end-of-tick positions, as the
        renderer interpolates an entity between its last two ticks."""
        t0 = math.floor(tick)
        a = self._pos(t0)
        b = self._pos(t0 + 1)
        f = tick - t0
        return a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f

    def _pos(self, t):
        if t < self.lunge_tick:
            return 0.0, 0.0
        last = max(self.positions)
        return self.positions[min(t, last)]


def lunge_flight(lunge_tick, forward_speed, up, ticks=200):
    """BeastMotion.simulate, tick by tick: the feet path of a lunge set at lunge_tick with horizontal
    speed forward_speed and vertical speed up (blocks per tick), for `ticks` ticks or until it lands,
    whichever is later."""
    speed, vertical = max(0.0, float(forward_speed)), max(0.0, float(up))
    travelled = height = 0.0
    grounded = True
    landing = None if vertical > 0 else int(lunge_tick)
    positions = {}
    t = int(lunge_tick)
    while t < lunge_tick + ticks or landing is None:
        if t > lunge_tick + 400:
            raise ValueError("the lunge never lands")
        travelled += speed
        speed *= GROUND_RETAIN if grounded else AIR_RETAIN
        if vertical > 0.0 or height > 0.0:
            height += vertical
            vertical = (vertical - GRAVITY) * VERTICAL_DRAG
            if height <= 0.0:
                height = vertical = 0.0
        grounded = height <= 0.0
        if grounded and landing is None:
            landing = t
        positions[t] = (travelled, height)
        t += 1
    apex_tick = max(positions, key=lambda k: positions[k][1])
    rest = travelled + speed / (1.0 - GROUND_RETAIN) if grounded else travelled
    return Flight(int(lunge_tick), float(forward_speed), float(up), positions, landing,
                  positions[apex_tick][1], apex_tick, rest)


def travel_per_unit_speed(up):
    """BeastMotion.horizontalTravelPerUnitSpeed: blocks travelled to rest per unit of start speed."""
    return lunge_flight(0, 1.0, up, ticks=1).rest_distance


def lunge_forward_speed(lunge, desired_travel):
    """BeastMoveDefinition.Lunge.forwardSpeed: the speed that comes to rest after desired_travel blocks,
    clamped to [forward_min, forward_max]."""
    speed = max(0.0, desired_travel) / travel_per_unit_speed(lunge["up"])
    return max(lunge["forward_min"], min(lunge["forward_max"], speed))


def move_flight(move, aim_distance=None):
    """The flight of a move's lunge toward an aim point aim_distance blocks ahead at the lock (default:
    the middle of use_range, the server's choice with no target), coming to rest with the aim point in
    the middle of the hit box's reach, as BeastEntity.lunge does."""
    lunge, hit = move["lunge"], move["hit"]
    if aim_distance is None:
        aim_distance = sum(move["use_range"]) / 2.0
    standoff = sum(hit["forward"]) / 2.0
    speed = lunge_forward_speed(lunge, aim_distance - standoff)
    return lunge_flight(lunge["tick"], speed, lunge["up"], ticks=move["total_ticks"] - lunge["tick"])


def jump_arc(lunge_tick, up):
    """Height (blocks) of the feet at the end of each server tick from lunge_tick until the landing tick
    (0 there), and the landing tick: the vertical part of lunge_flight."""
    if up <= 0:
        return {}, int(lunge_tick)
    f = lunge_flight(lunge_tick, 0.0, up, ticks=1)
    return {t: f.positions[t][1] for t in range(int(lunge_tick), f.landing + 1)}, f.landing


def arc_height(heights, tick):
    """Feet height at a fractional move tick: linear between the server's end-of-tick positions."""
    t0 = int(tick // 1)
    a = heights.get(t0, 0.0)
    b = heights.get(t0 + 1, 0.0)
    return a + (b - a) * (tick - t0)
