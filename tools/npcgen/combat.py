"""Retarget the authored player sword poses to each NPC skeleton.

Timing and pose intent remain owned by combat_data/gen_sword_pal_anims. This adapter owns
NPC joint rest-pose removal, elbow bend, grounded feet and grip bones, not attack balance.
"""
from __future__ import annotations

import math
from .. import gen_sword_pal_anims as sword
from ..beastgen import anim, cuboid

ELBOW = {"right": -8.0, "left": -40.0}
OFF_HAND_SHOULDER_WEIGHT = 0.55


def add_grips(model):
    for side in ("right", "left"):
        bone, hand = model.cube(f"hand_{side}")
        center = tuple(hand.origin[i] + hand.size[i] / 2 for i in range(3))
        model.bones.append(cuboid.Bone(f"grip_{side}", bone.name, center))
    shoulder = model.bone("arm_right").pivot
    model.bones.append(cuboid.Bone("sword_stowed", "body", (shoulder[0] * .65, -4.0, 7.5), (180, 0, -18)))


def _euler(matrix):
    # ModelPart rotationZYX. Clamp roundoff at gimbal poles.
    y = math.asin(max(-1.0, min(1.0, -matrix[2][0])))
    if abs(math.cos(y)) > 1e-7:
        x, z = math.atan2(matrix[2][1], matrix[2][2]), math.atan2(matrix[1][0], matrix[0][0])
    else:
        x, z = math.atan2(-matrix[1][2], matrix[1][1]), 0
    return tuple(math.degrees(v) for v in (x, y, z))


def _rotation(values):
    return cuboid.mat_rot_zyx(*(math.radians(v) for v in values))


def _offset(model, name, rotation):
    return {"rotation": tuple(rotation[i] - model.bone(name).rotation[i] for i in range(3))}


def pose(model, player_pose):
    p = player_pose
    hip = model.bone("body").pivot
    rotated_hip = cuboid.mat_apply_dir(_rotation(p["body"]), hip)
    # Rotate around the hip instead of the NPC root's feet pivot.
    delta = tuple(hip[i] - rotated_hip[i] for i in range(3))
    x, y, z = p["body_pos"]
    out = {
        "root": {"rotation": tuple(p["body"]),
                 "position": (delta[0] + x / model.scale, -delta[1] + y / model.scale, delta[2] + z / model.scale)},
        "body": _offset(model, "body", (0, 0, 0)),
        "head": _offset(model, "head", p["head"]),
    }
    for side in ("right", "left"):
        shoulder = list(p[f"{side}_arm"])
        if side == "left": shoulder[0] *= OFF_HAND_SHOULDER_WEIGHT
        out[f"arm_{side}"] = _offset(model, f"arm_{side}", shoulder)
        out[f"forearm_{side}"] = _offset(model, f"forearm_{side}", (ELBOW[side], 0, 0))
        out[f"leg_{side}"] = _offset(model, f"leg_{side}", p[f"{side}_leg"])
    # Blade orientation is the same authored item orientation, about the actual hand centre.
    ix, iy, iz = p["right_item"]
    rig = sword.weapon_rig(sword.BASIC_SWORD)
    display_rotation = sword._chain(*rig.display)
    for i in range(3):
        for j in range(3):
            display_rotation[i][j] /= rig.display_scale
        display_rotation[i][3] = 0
    wrist = sword._chain(sword._r4("x", -ELBOW["right"]), sword._r4("x", -90), sword._r4("y", 180),
                         sword._r4("z", -iy), sword._r4("y", -iz), sword._r4("x", -ix), display_rotation)
    out["grip_right"] = {"rotation": _euler(wrist)}
    # Correct the longer NPC legs using their real cube corners, independently for each look.
    offsets = {}
    temp = anim.Clip("pose", 1, False, [anim.Key(0, out, "linear")])
    for name, target, frames in temp.channels():
        anim.clip_offsets([(name, target, frames)], 1, False, 0, into=offsets)
    matrices = anim.posed_matrices(model, offsets)
    lowest = max(cuboid.mat_apply(matrices[bone.name], (cube.origin[0] + dx * cube.size[0],
                       cube.origin[1] + dy * cube.size[1], cube.origin[2] + dz * cube.size[2]))[1]
                 for bone, cube in model.cubes() if bone.name.startswith("leg_")
                 for dx in (0, 1) for dy in (0, 1) for dz in (0, 1))
    px, py, pz = out["root"]["position"]
    out["root"]["position"] = (px, py + lowest - 24, pz)
    return out


def _clip(model, name, keys, ticks, loop=False, notes=None):
    resolved = sword.resolve(keys, sword.BASIC_SWORD)
    frames = []
    previous = None
    for half_tick in range(ticks * 2 + 1):
        tick = half_tick / 2
        current = pose(model, sword.sample(keys, resolved, tick))
        # Continuous Euler branches avoid an artificial 360-degree spin between samples.
        if previous:
            for bone, channels in current.items():
                if "rotation" in channels and bone in previous:
                    old = previous[bone].get("rotation", (0, 0, 0))
                    channels["rotation"] = tuple(v + 360 * round((old[i] - v) / 360)
                                                 for i, v in enumerate(channels["rotation"]))
        frames.append(anim.Key(tick / 20, current, "linear"))
        previous = current
    return anim.Clip(name, ticks / 20, loop, frames, notes or {})


def clips(model):
    table = sword.BASIC_SWORD
    moves = sword.bind(table, sword.combat_styles()[table.style_id])
    result = [_clip(model, "sword_ready", table.ready_idle, table.ready_idle_ticks, True)]
    for move in moves:
        result.append(_clip(model, move.move_id.split(":", 1)[1], move.keys, move.total,
                            notes={"active": (move.active_start, move.active_end), "chain": move.chain_tick}))
    return result
