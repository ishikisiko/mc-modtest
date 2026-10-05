"""Keyframe clips, the schema-1 animations JSON, and a port of vanilla's sampler and pose chain.

Standard library only. `sample_channel` is net/minecraft/client/animation/KeyframeAnimations.animate
for one channel (binary search, the destination keyframe's interpolation, Mth.catmullrom with the
neighbours clamped at the ends, looping by `time % length`); `posed_matrices` applies channels as
ModelPart.offsetPos/offsetRotation/offsetScale on top of the rest pose and chains translateAndRotate.

A clip is authored as keys: (time in seconds, pose, interpolation), where a pose maps
bone -> {"rotation" | "position" | "scale": (x, y, z)} offsets from rest in the file's convention
(degrees; position +y up as KeyframeAnimations.posVec negates it; scale 1 = unchanged). On export
every channel gets a keyframe at every key time of the clip, so the file says exactly what the
generator evaluated.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

from .cuboid import bone_matrix, mat_mul, _num

TARGETS = ("rotation", "position", "scale")
DEFAULT = {"rotation": (0.0, 0.0, 0.0), "position": (0.0, 0.0, 0.0), "scale": (1.0, 1.0, 1.0)}


@dataclass
class Key:
    time: float
    pose: dict
    interp: str = "catmullrom"
    label: str = ""


@dataclass
class Clip:
    name: str
    length: float
    loop: bool
    keys: list = field(default_factory=list)
    notes: dict = field(default_factory=dict)  # preview-only facts (phase ticks etc.), not exported

    def channels(self):
        """[(bone, target, [(time, value, interp)])] in a stable order."""
        order = []
        for k in self.keys:
            for bone, targets in k.pose.items():
                for t in targets:
                    if (bone, t) not in order:
                        order.append((bone, t))
        out = []
        for bone, t in order:
            frames = []
            for k in sorted(self.keys, key=lambda k: k.time):
                v = k.pose.get(bone, {}).get(t, DEFAULT[t])
                frames.append((k.time, tuple(float(x) for x in v), k.interp))
            if all(_same(f[1], DEFAULT[t]) for f in frames):
                continue
            out.append((bone, t, frames))
        return out


def _same(a, b, eps=1e-6):
    return all(abs(x - y) <= eps for x, y in zip(a, b))


# ------------------------------------------------------------------------------------ export
def clips_json(model_id, clips):
    doc = {"schema": 1, "id": model_id, "clips": {}}
    for c in clips:
        doc["clips"][c.name] = {
            "length": _num(c.length),
            "loop": bool(c.loop),
            "channels": [{"bone": bone, "target": t,
                          "keyframes": [{"time": _num(time), "value": [_num(x, 3) for x in v], "interp": interp}
                                        for time, v, interp in frames]}
                         for bone, t, frames in c.channels()],
        }
    return doc


def clips_from_json(doc):
    """name -> (length, loop, [(bone, target, [(time, value, interp)])]) as the Java loader reads it."""
    out = {}
    for name, c in doc["clips"].items():
        chans = [(ch["bone"], ch["target"], [(k["time"], tuple(k["value"]), k["interp"]) for k in ch["keyframes"]])
                 for ch in c["channels"]]
        out[name] = (c["length"], c["loop"], chans)
    return out


# ------------------------------------------------------------------------------------ vanilla sampler
def catmullrom(f, a, b, c, d):
    """Mth.catmullrom."""
    return 0.5 * (2.0 * b + (c - a) * f + (2.0 * a - 5.0 * b + 4.0 * c - d) * f * f + (3.0 * b - a - 3.0 * c + d) * f * f * f)


def sample_channel(frames, seconds, length, loop, weight=1.0):
    """KeyframeAnimations.animate for one channel; returns the file-convention value * weight
    (for scale: (value - 1) * weight + 1, as scaleVec stores value - 1)."""
    f = seconds % length if loop else seconds
    n = len(frames)
    lo, hi = 0, n
    while lo < hi:  # Mth.binarySearch: first index with f <= timestamp
        mid = (lo + hi) // 2
        if f <= frames[mid][0]:
            hi = mid
        else:
            lo = mid + 1
    i = max(0, lo - 1)
    j = min(n - 1, i + 1)
    t0, t1 = frames[i][0], frames[j][0]
    f2 = min(1.0, max(0.0, (f - t0) / (t1 - t0))) if j != i else 0.0
    interp = frames[j][2]
    if interp == "linear":
        a, b = frames[i][1], frames[j][1]
        return tuple((a[k] + (b[k] - a[k]) * f2) * weight for k in range(3))
    p0 = frames[max(0, i - 1)][1]
    p1 = frames[i][1]
    p2 = frames[j][1]
    p3 = frames[min(n - 1, j + 1)][1]
    return tuple(catmullrom(f2, p0[k], p1[k], p2[k], p3[k]) * weight for k in range(3))


def clip_offsets(chans, length, loop, seconds, weight=1.0, into=None):
    """Accumulate one clip's offsets at `seconds` into {bone: {"rot": [rad], "pos": [model px], "scale": [d]}}
    in ModelPart terms (degreeVec to radians, posVec negates y, scaleVec subtracts 1)."""
    acc = into if into is not None else {}
    for bone, target, frames in chans:
        v = sample_channel(frames, seconds, length, loop, 1.0)
        e = acc.setdefault(bone, {"rot": [0.0, 0.0, 0.0], "pos": [0.0, 0.0, 0.0], "scale": [0.0, 0.0, 0.0]})
        if target == "rotation":
            for k in range(3):
                e["rot"][k] += math.radians(v[k]) * weight
        elif target == "position":
            vec = (v[0], -v[1], v[2])
            for k in range(3):
                e["pos"][k] += vec[k] * weight
        else:
            for k in range(3):
                e["scale"][k] += (v[k] - 1.0) * weight
    return acc


def posed_matrices(model, offsets=None, look=None):
    """Bone -> model-space matrix with offsets applied (rest pose when offsets is None).
    look: (yaw_deg, pitch_deg) set on the look bone's yRot/xRot before the clips, like setupAnim."""
    offsets = offsets or {}
    out = {}
    for b in model.bones:
        rot = [math.radians(r) for r in b.rotation]
        if look and model.look and b.name == model.look["bone"]:
            rot[0] += math.radians(look[1])
            rot[1] += math.radians(look[0])
        piv = list(b.pivot)
        scl = [1.0, 1.0, 1.0]
        e = offsets.get(b.name)
        if e:
            for k in range(3):
                rot[k] += e["rot"][k]
                piv[k] += e["pos"][k]
                scl[k] += e["scale"][k]
        m = bone_matrix(piv, rot, scl)
        out[b.name] = m if b.parent is None else mat_mul(out[b.parent], m)
    return out


def pose_offsets(pose):
    """An authored pose (file convention) as ModelPart offsets, for evaluating a key directly."""
    acc = {}
    for bone, targets in pose.items():
        e = acc.setdefault(bone, {"rot": [0.0, 0.0, 0.0], "pos": [0.0, 0.0, 0.0], "scale": [0.0, 0.0, 0.0]})
        for t, v in targets.items():
            if t == "rotation":
                e["rot"] = [math.radians(x) for x in v]
            elif t == "position":
                e["pos"] = [v[0], -v[1], v[2]]
            else:
                e["scale"] = [x - 1.0 for x in v]
    return acc
