"""v2 唯一整数锚点与有界施工索引；不创建整城三维字典。"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[4]
if str(ROOT / "tools") not in sys.path:
    sys.path.insert(0, str(ROOT / "tools"))

AIR = "minecraft:air"
REGIONS = ("donghai", "ximo", "beiyuan", "lingyue", "nanjiang", "donghuang", "moyu")
FACING = {"south": 0, "west": 1, "north": 2, "east": 3}
DIRECTIONS = tuple(FACING)
DIR = {"south": (0, 1), "west": (-1, 0), "north": (0, -1), "east": (1, 0)}
LIBRARY = ROOT / "blueprints/province_capitals/regional/v2/building_families.json"
CITY_INPUT = ROOT / "blueprints/province_capitals/regional/v2/cities"


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, data, compact=False):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=None if compact else 2,
                               separators=(",", ":") if compact else None) + "\n", encoding="utf-8")


def rotate(x, z, q):
    return ((x, z), (-z, x), (-x, -z), (z, -x))[q % 4]


def point(building, x, y, z):
    """未旋转 lot [0,w)×[0,d)，整数中心锚点 [-floor(w/2),ceil(w/2))。"""
    w, d = building["size"]
    xx, zz = rotate(x - w // 2, z - d // 2, FACING[building["facing"]])
    px, py, pz = building["position"]
    return px + xx, py + y, pz + zz


def props(state, **values):
    base = state.split("[", 1)[0]
    return base + "[" + ",".join(f"{k}={str(v).lower()}" for k, v in sorted(values.items())) + "]"


def rotated_state(state, q):
    if not q or "[" not in state:
        return state
    base, raw = state[:-1].split("[", 1)
    values = dict(item.split("=", 1) for item in raw.split(","))
    if values.get("facing") in FACING:
        values["facing"] = DIRECTIONS[(FACING[values["facing"]] + q) % 4]
    if q % 2 and values.get("axis") in ("x", "z"):
        values["axis"] = "z" if values["axis"] == "x" else "x"
    old = dict(values)
    for face in DIRECTIONS:
        if face in old:
            values[DIRECTIONS[(FACING[face] + q) % 4]] = old[face]
    return props(base, **values)


def lerp(table, value):
    for (a, va), (b, vb) in zip(table, table[1:]):
        if a <= value <= b:
            return va + (vb - va) * (value - a) / (b - a)
    return table[0][1] if value < table[0][0] else table[-1][1]


def line(a, b):
    length = max(abs(b[0] - a[0]), abs(b[2] - a[2]), 1)
    return [tuple(round(a[j] + (b[j] - a[j]) * i / length) for j in range(3))
            for i in range(length + 1)]


@dataclass(frozen=True)
class Op:
    low: tuple
    high: tuple
    state: str
    tag: str
    layer: str


class Construction:
    def __init__(self, dimensions):
        self.size = dimensions
        self.by_tile = defaultdict(list)
        self.operations = 0

    def box(self, low, high, state, tag="", layer="building"):
        lo = tuple(min(a, b) for a, b in zip(low, high))
        hi = tuple(max(a, b) for a, b in zip(low, high))
        if any(a < 0 or b >= limit for a, b, limit in zip(lo, hi, self.size)):
            raise ValueError(f"施工越界 {tag}/{layer}: {lo}..{hi}, size={self.size}")
        op = Op(lo, hi, state, tag, layer)
        for tz in range(lo[2] // 16, hi[2] // 16 + 1):
            for tx in range(lo[0] // 16, hi[0] // 16 + 1):
                self.by_tile[tx, tz].append(op)
        self.operations += 1

    def cell(self, pos, state, tag="", layer="building"):
        self.box(pos, pos, state, tag, layer)

    def tile(self, tx, tz):
        cells = {}
        for op in self.by_tile.get((tx, tz), ()):
            x0, y0, z0 = op.low
            x1, y1, z1 = op.high
            for y in range(y0, y1 + 1):
                for z in range(max(z0, tz * 16), min(z1, tz * 16 + 15) + 1):
                    for x in range(max(x0, tx * 16), min(x1, tx * 16 + 15) + 1):
                        cells[x % 16, y, z % 16] = (op.state, op.tag, op.layer)
        return cells

    def at(self, pos):
        result = self.record_at(pos)
        return result[0] if result else None

    def record_at(self, pos):
        x, y, z = pos
        for op in reversed(self.by_tile.get((x // 16, z // 16), ())):
            if all(a <= p <= b for a, p, b in zip(op.low, pos, op.high)):
                return op.state, op.tag, op.layer
        return None
