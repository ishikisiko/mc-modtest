"""日魄乌金拳套的附着动效。UV 区域与贴图上的动画帧共用一份坐标定义。"""

from __future__ import annotations

from dataclasses import dataclass
import math

from .geometry import encode_png

WIDTH = HEIGHT = 128
FRAMES = 40
FRAME_TICKS = 2
AMBER_MATERIALS = {"日斑", "日斑心", "焦橙过渡", "磨亮日斑"}


@dataclass(frozen=True)
class Patch:
    row: int
    columns: int
    rows: int
    x: tuple[float, float]
    z: tuple[float, float]

    def uv(self, x: float, z: float) -> tuple[float, float]:
        u = (x - self.x[0]) / (self.x[1] - self.x[0])
        v = (self.z[1] - z) / (self.z[1] - self.z[0])
        return ((1.5 + u * (self.columns - 3)) / WIDTH,
                (self.row + 1.5 + v * (self.rows - 3)) / HEIGHT)

    def points(self):
        for j in range(1, self.rows - 1):
            z = self.z[1] - (j - 1) / (self.rows - 3) * (self.z[1] - self.z[0])
            for i in range(1, self.columns - 1):
                x = self.x[0] + (i - 1) / (self.columns - 3) * (self.x[1] - self.x[0])
                yield i, self.row + j, x, z


FRONT = Patch(0, 128, 48, (-4.62, 4.62), (1.30, 4.74))
EMBERS = Patch(48, 128, 48, (-4.62, 4.62), (2.30, 4.90))
CORE = Patch(96, 64, 32, (-2.15, 1.29), (-2.32, 1.20))
# Local positions, delays and lifetimes from the approved front-view preview.
SPARKS = (
    (-1.37, 3.47, 1.02, .54, -.52, .64, .103),
    (1.86, 3.33, 1.13, .57, .52, .82, .088),
    (-2.55, 3.07, 1.30, .49, -.43, .52, .070),
    (2.92, 2.96, 1.41, .48, .48, .65, .076),
)
CLEAR = (0, 0, 0, 0)


def overlay_meshes(meshes: list[dict]) -> list[dict]:
    overlays = []
    for mesh in meshes:
        if mesh["part"] == "宽拳面折边":
            patch, offset, axes = FRONT, (0, .023, 0), (0, 2)
        elif mesh["part"] == "内凹日斑护手" and mesh["material"] in AMBER_MATERIALS:
            patch, offset, axes = CORE, (0, 0, .023), (0, 1)
        else:
            continue

        indic = mesh["indices"]
        indices = []
        for start in range(0, len(indic), 3):
            tri = indic[start:start + 3]
            if patch is CORE or mesh["normals"][tri[0]][1] > .32:
                indices.extend(tri)
        if not indices:
            continue
        overlays.append({
            "part": mesh["part"] + "动效", "glow": True,
            "vertices": [tuple(v + d for v, d in zip(point, offset)) for point in mesh["vertices"]],
            "normals": mesh["normals"], "indices": indices,
            "uvs": [patch.uv(point[axes[0]], point[axes[1]]) for point in mesh["vertices"]],
        })
    # The transparent sheet is parallel to the punch face and follows the item transform.
    x0, x1 = EMBERS.x
    z0, z1 = EMBERS.z
    positions = [(x0, 5.81, z0), (x0, 5.81, z1), (x1, 5.81, z1), (x1, 5.81, z0)]
    overlays.append({
        "part": "拳面火星", "glow": True, "vertices": positions,
        "normals": [(0, 1, 0)] * 4, "indices": [0, 1, 2, 0, 2, 3],
        "uvs": [EMBERS.uv(x, z) for x, _, z in positions],
    })
    return overlays


def smooth(a: float, b: float, value: float) -> float:
    t = max(0.0, min(1.0, (value - a) / (b - a)))
    return t * t * (3 - 2 * t)


def front_profile() -> list[tuple[int, int, float, float, float]]:
    profile = []
    for i, j, x, z in FRONT.points():
        along = abs(x) / 3.52
        arch = 2.69 + .89 * (1 - along ** 1.87)
        distance = abs(z - arch)
        if along >= 1.04 or distance >= .22:
            continue
        end = 1 - smooth(.91, 1.04, along)
        core = (1 - smooth(.035, .137, distance)) * end
        warmth = (1 - smooth(.065, .22, distance)) * end
        profile.append((i, j, .77 + .71 * min(along, 1.0), core, warmth))
    return profile


def animated_texture() -> tuple[bytes, dict]:
    """Four seconds per loop. Reuse identical idle frames in the sprite sheet."""
    face = front_profile()
    core = list(CORE.points())
    ember = list(EMBERS.points())
    frames = []
    shared: dict[bytes, int] = {}
    sequence = []
    for step in range(FRAMES):
        time = step * FRAME_TICKS / 20
        frame = [[CLEAR] * WIDTH for _ in range(HEIGHT)]
        for i, j, arrival, ridge, halo in face:
            pulse = smooth(arrival - .35, arrival - .09, time) * (1 - smooth(arrival + .03, arrival + .40, time))
            light = (.03 + .97 * pulse) * (.83 * ridge + .17 * halo)
            if light >= .14:
                frame[j][i] = (round(190 + 65 * light), round(103 + 126 * light), round(28 + 110 * light), 255)
        charge = smooth(.05, .72, time) * (1 - smooth(.75, 1.93, time))
        if charge > .15:
            for i, j, x, y in core:
                distance = math.hypot((x + .57) / 1.24, (y + .49) / 1.34)
                light = max(0, 1 - distance * .73) * charge
                if light >= .32:
                    frame[j][i] = (round(207 + 48 * light), round(124 + 104 * light), round(46 + 98 * light), 255)
        for x, z, start, life, dx, dz, size in SPARKS:
            age = (time - start) / life
            if not 0 < age < 1:
                continue
            fade = smooth(0, .19, age) * (1 - smooth(.29, 1, age))
            if fade < .12:
                continue
            rx, rz = x + dx * age, z + dz * age
            radius = size * (1.12 - age * .38)
            for i, j, px, pz in ember:
                distance = (abs(px - rx) + abs(pz - rz)) / radius
                if distance < 1.3:
                    light = fade * (1 - .50 * distance)
                    if light >= .12:
                        frame[j][i] = (255, round(138 + 85 * light), round(52 + 68 * light), 255)
        key = bytes(c for row in frame for pixel in row for c in pixel)
        if key not in shared:
            shared[key] = len(frames)
            frames.append(frame)
        sequence.append(shared[key])
    data = encode_png([row for frame in frames for row in frame])
    metadata = {"animation": {"width": WIDTH, "height": HEIGHT, "frametime": FRAME_TICKS, "frames": sequence}}
    return data, metadata
