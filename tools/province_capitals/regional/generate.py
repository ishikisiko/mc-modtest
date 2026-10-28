#!/usr/bin/env python3
"""生成东海／灵岳审阅样板；本命令只交付几何与 HTML，不运行检查或测试。"""
from __future__ import annotations

import base64
import gzip
import hashlib
import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
AUTHOR = ROOT / "blueprints/province_capitals/regional/cities.json"
CATALOG = ROOT / "blueprints/province_capitals/catalog.json"
OUT = ROOT / "out/preview/province-capitals/regional"
FACING = {"south": 0, "east": 3, "north": 2, "west": 1}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def lerp_table(table, value):
    for (a, va), (b, vb) in zip(table, table[1:]):
        if a <= value <= b:
            return va + (vb - va) * (value - a) / (b - a)
    return table[0][1] if value < table[0][0] else table[-1][1]


def closest_on_segment(x, z, a, b):
    dx, dz = b[0] - a[0], b[2] - a[2]
    t = max(0, min(1, ((x - a[0]) * dx + (z - a[2]) * dz) / (dx * dx + dz * dz or 1)))
    point = [a[i] + (b[i] - a[i]) * t for i in range(3)]
    return math.hypot(x - point[0], z - point[2]), point


def rotated(x, z, quarters):
    # exchange 正向：+X → +Z。绕作者原点旋转，绝不自动归零。
    return ((x, z), (-z, x), (-x, -z), (z, -x))[quarters % 4]


class Geometry:
    """预览几何的唯一生产者：实体方柱、连续网格、材质和语义标签。"""

    def __init__(self, city):
        self.city = city
        self.boxes = []
        self.meshes = []
        self.labels = []
        self.p = (0, 0, 0)
        self.q = 0
        self.tag = ""
        self.layer = "building"
        self.detail = "detailed"
        self.minimum = [math.inf] * 3
        self.maximum = [-math.inf] * 3
        p = city["palette"]
        self.materials = {k: {"color": v, "texture": k} for k, v in p.items()}
        self.materials.update({
            "dark": {"color": "#353b37"}, "window": {"color": "#b8b394"},
            "paper": {"color": "#d8cfab"}, "leaf": {"color": "#486c4d"},
            "leaf_light": {"color": "#6e8960"}, "sand": {"color": "#c6b795"},
            "foam": {"color": "#c7dcd3"}, "cloth": {"color": p["accent"]},
            "skin": {"color": "#cba47d"}, "robe": {"color": "#bc6547"},
            "iron": {"color": "#4d5553"}, "fire": {"color": "#d39343"},
            "shell": {"color": "#a2a79d"}, "herb": {"color": "#6e8b49"},
        })

    def context(self, position=(0, 0, 0), quarters=0, tag="", layer="building", detail="detailed"):
        self.p, self.q, self.tag, self.layer, self.detail = position, quarters, tag, layer, detail

    def world(self, x, y, z):
        rx, rz = rotated(x, z, self.q)
        return [round(rx + self.p[0], 4), round(y + self.p[1], 4), round(rz + self.p[2], 4)]

    def box(self, x, y, z, w, h, d, material, layer=None):
        position = self.world(x, y, z)
        size = [round(w, 4), round(h, 4), round(d, 4)]
        envelope_size = [size[2], size[1], size[0]] if self.q % 2 else size
        for axis in range(3):
            self.minimum[axis] = min(self.minimum[axis], position[axis] - envelope_size[axis] / 2)
            self.maximum[axis] = max(self.maximum[axis], position[axis] + envelope_size[axis] / 2)
        self.boxes.append({"p": position, "s": size,
                           "q": self.q, "m": material, "layer": layer or self.layer,
                           "tag": self.tag, "detail": self.detail})

    def mesh(self, vertices, faces, material, layer=None):
        points = [self.world(*p) for p in vertices]
        for point in points:
            for axis in range(3):
                self.minimum[axis] = min(self.minimum[axis], point[axis])
                self.maximum[axis] = max(self.maximum[axis], point[axis])
        self.meshes.append({"v": points, "f": faces, "m": material,
                            "layer": layer or self.layer, "tag": self.tag, "detail": self.detail})

    def label(self, text, position, kind="place", target=None):
        self.labels.append({"text": text, "position": position, "kind": kind, "target": target})


def expand_buildings(city):
    buildings = []
    count = 0
    for row_index, row in enumerate(city["house_rows"]):
        for i, x in enumerate(row["xs"]):
            count += 1
            detail = "detailed" if row_index in city["detailed_house_rows"] else "envelope"
            road_id = row["road_id"]
            if city["region_id"] == "lingyue" and row_index in (2, 3) and x > 120:
                road_id = "middle_east"
            buildings.append({
                "id": f"house_{count:03d}", "name": f"民居 {count:02d}", "role": "house",
                "template": ("coastal_veranda_house" if city["region_id"] == "donghai" else "mountain_stonebase_house"),
                "position": [x, row["y"], row["z"]], "size": [8 if count % 3 else 9, 8 if count % 4 else 9],
                "stories": 2 if count % 6 == 0 else 1, "facing": row["facing"],
                "road_id": road_id, "detail": detail,
                "note": "外墙、屋面、入口和入巷支路已建；" + ("代表性立面与檐廊已细化，室内未完成。" if detail == "detailed" else "其余院落与室内尚未细化。"),
            })
    buildings.extend(json.loads(json.dumps(city["buildings"])))
    for building in buildings:
        building["rotation_quarters"] = FACING[building["facing"]]
        w, d = building["size"]
        q = building["rotation_quarters"]
        dx, dz = rotated(0, d / 2 + 1.9, q)
        building["entrance"] = [building["position"][0] + dx, building["position"][1], building["position"][2] + dz]
        building["footprint"] = [d, w] if q % 2 else [w, d]
    return buildings


def access_roads(city, buildings):
    roads = json.loads(json.dumps(city["roads"]))
    by_id = {r["id"]: r for r in roads}
    for building in buildings:
        if building["role"] == "gate":
            continue
        front = building["entrance"]
        road = by_id[building["road_id"]]
        choices = [closest_on_segment(front[0], front[2], a, b) for a, b in zip(road["points"], road["points"][1:])]
        _, target = min(choices, key=lambda v: v[0])
        roads.append({"id": "access_" + building["id"], "name": building["name"] + "入巷支路", "kind": "street",
                      "width": 1.6 if building["role"] == "house" else 2.4,
                      "points": [front, target], "building_id": building["id"]})
    if city["region_id"] == "donghai":
        for pier in city["piers"]:
            shore = lerp_table(city["terrain"]["shore_x_by_z"], pier["z"])
            roads.append({"id": pier["id"], "name": "卸货木栈桥", "kind": "bridge", "width": pier["width"],
                          "points": [[round(shore - 10, 3), 8, pier["z"]], [pier["end_x"], 8, pier["z"]]]})
    return roads


class Terrain:
    def __init__(self, city, buildings, roads):
        self.city, self.buildings, self.roads = city, buildings, roads
        self.cfg = city["terrain"]

    def natural(self, x, z):
        if self.city["region_id"] == "donghai":
            shore = lerp_table(self.cfg["shore_x_by_z"], z)
            delta = shore - x
            if delta >= 2:
                return self.cfg["land_y"]
            if delta > -4:
                return self.cfg["bed_y"] + (delta + 4) / 6 * (self.cfg["land_y"] - self.cfg["bed_y"])
            return self.cfg["bed_y"]
        h = lerp_table(self.cfg["shelves"], z)
        # 侧坡与北岭保留山体起伏；住区只在各自地块整地。
        edge = max(0, (28 - x) / 28, (x - 199) / 25)
        h += edge * (8 + 3 * math.sin(z / 29))
        h += (math.sin(x / 25 + z / 37) * 0.7 + math.cos(z / 21) * 0.5)
        ravine = self.cfg["ravine"]
        z0, z1 = ravine["z_range"]
        if z0 <= z <= z1:
            center = ravine["center_x"] + 1.1 * math.sin(z / 12)
            influence = max(0, 1 - abs(x - center) / ravine["half_width"])
            end = min(1, (z - z0) / 6, (z1 - z) / 6)
            h -= ravine["depth"] * influence * max(0, end)
        return h

    def height(self, x, z):
        h = self.natural(x, z)
        for building in self.buildings:
            px, py, pz = building["position"]
            fw, fd = building["footprint"]
            if abs(x - px) <= fw / 2 + 1.2 and abs(z - pz) <= fd / 2 + 1.2:
                h = py
        for road in self.roads:
            if road["kind"] == "bridge":
                continue
            for a, b in zip(road["points"], road["points"][1:]):
                dist, point = closest_on_segment(x, z, a, b)
                if dist <= road["width"] / 2 + 0.6:
                    if a[1] != b[1]:
                        length = math.hypot(b[0] - a[0], b[2] - a[2])
                        count = max(1, math.ceil(length / 0.75))
                        fraction = (point[1] - a[1]) / (b[1] - a[1])
                        tread = min(count - 1, max(0, math.floor(fraction * count)))
                        h = a[1] + (b[1] - a[1]) * (tread + 0.5) / count - 0.02
                    else:
                        h = point[1] - 0.04
        return round(max(0.5, h), 3)

    def surface(self, g):
        step = self.cfg["resolution"]
        nx, nz = 224 // step + 1, 272 // step + 1
        vertices = [[x * step, self.height(x * step, z * step), z * step] for z in range(nz) for x in range(nx)]
        faces = []
        for z in range(nz - 1):
            for x in range(nx - 1):
                a = z * nx + x
                faces.extend([[a, a + nx, a + 1], [a + 1, a + nx, a + nx + 1]])
        g.context(tag="continuous_terrain", layer="terrain")
        g.mesh(vertices, faces, "ground")
        perimeter = []
        perimeter.extend([[x * step, self.height(x * step, 0), 0] for x in range(nx)])
        perimeter.extend([[224, self.height(224, z * step), z * step] for z in range(1, nz)])
        perimeter.extend([[x * step, self.height(x * step, 272), 272] for x in reversed(range(nx - 1))])
        perimeter.extend([[0, self.height(0, z * step), z * step] for z in reversed(range(1, nz - 1))])
        sides, sf = [], []
        for i, p in enumerate(perimeter):
            b = perimeter[(i + 1) % len(perimeter)]
            n = len(sides)
            sides.extend([p, [p[0], 0, p[2]], b, [b[0], 0, b[2]]])
            sf.extend([[n, n + 1, n + 2], [n + 2, n + 1, n + 3]])
        g.mesh(sides, sf, "earth")
        g.box(112, 0.25, 136, 224, 0.5, 272, "earth")
        # 路边与地块挡土面落到原坡面，保持真实支撑。
        if self.city["region_id"] == "lingyue":
            for building in self.buildings:
                px, py, pz = building["position"]
                fw, fd = building["footprint"]
                for xx, zz, ww, dd in [(px, pz + fd / 2 + 0.8, fw + 1.6, 0.45),
                                        (px - fw / 2 - 0.8, pz, 0.45, fd + 1.6),
                                        (px + fw / 2 + 0.8, pz, 0.45, fd + 1.6)]:
                    bottom = max(0.4, min(py, self.natural(xx, zz)) - 0.3)
                    if py - bottom > 0.6:
                        g.box(xx, (py + bottom) / 2, zz, ww, py - bottom, dd, "stone")
        return {"resolution": step, "size": [nx, nz], "heights": [v[1] for v in vertices], "base_y": 0}


def roof(g, w, d, y, rise, overhang=1.15):
    """一整张闭合硬边曲面屋盖；不是以分离瓦片堆砌体量。"""
    half_w, half_d = w / 2 + overhang, d / 2 + overhang
    profile = [(-1, 0.22), (-0.78, 0.03), (-0.48, rise * 0.30), (0, rise), (0.48, rise * 0.30), (0.78, 0.03), (1, 0.22)]
    vertices = []
    for x in [-half_w, half_w]:
        for t, h in profile:
            vertices.append([x, y + h, t * half_d])
        for t, h in profile:
            vertices.append([x, y + h - 0.34, t * half_d])
    faces = []
    n = len(profile)
    for i in range(n - 1):
        faces.extend([[i, i + 1, 2 * n + i], [i + 1, 2 * n + i + 1, 2 * n + i]])
        faces.extend([[n + i, 3 * n + i, n + i + 1], [n + i + 1, 3 * n + i, 3 * n + i + 1]])
        faces.extend([[i, n + i, i + 1], [i + 1, n + i, n + i + 1]])
        faces.extend([[2 * n + i, 2 * n + i + 1, 3 * n + i], [2 * n + i + 1, 3 * n + i + 1, 3 * n + i]])
    for i in [0, n - 1]:
        faces.extend([[i, 2 * n + i, n + i], [n + i, 2 * n + i, 3 * n + i]])
    g.mesh(vertices, faces, "roof", "roof")
    g.box(0, y + rise + 0.06, 0, w + overhang * 1.9, 0.26, 0.30, "roof_edge", "roof")
    g.box(0, y + 0.08, half_d - 0.05, w + overhang * 2, 0.22, 0.22, "roof_edge", "roof")
    g.box(0, y + 0.08, -half_d + 0.05, w + overhang * 2, 0.22, 0.22, "roof_edge", "roof")
    # 山花整体封闭，侧面不会透空。
    for x in [-w / 2, w / 2]:
        verts = [[x, y - 0.2, -d / 2], [x, y - 0.2, d / 2], [x, y + rise - 0.15, 0]]
        g.mesh(verts, [[0, 1, 2], [2, 1, 0]], "wall")


def window(g, x, y, z, width=1.25, height=1.4):
    g.box(x, y, z, width, height, 0.17, "wood")
    g.box(x, y, z + 0.10, width - 0.22, height - 0.20, 0.10, "paper")
    for offset in [-width / 4, 0, width / 4]:
        g.box(x + offset, y, z + 0.19, 0.075, height - 0.10, 0.08, "wood")
    g.box(x, y, z + 0.20, width - 0.12, 0.09, 0.08, "wood")


def building_geometry(g, building, city):
    p = building["position"]
    q = building["rotation_quarters"]
    g.context(p, q, building["id"], detail=building["detail"])
    w, d = building["size"]
    role = building["role"]
    detailed = building["detail"] == "detailed"
    if role == "gate":
        for x in [-w / 2 + 1.75, w / 2 - 1.75]:
            g.box(x, 3.0, 0, 3.5, 6, d, "stone")
        g.box(0, 6.5, 0, w, 1.4, d, "stone")
        for x in [-w / 2 + 0.8, w / 2 - 0.8]:
            g.box(x, 8.2, 0, 0.6, 2.5, d - 0.8, "wood")
        roof(g, w + 0.6, d + 0.5, 9.4, 2.3)
        g.box(0, 7.9, d / 2 + 0.15, w * 0.42, 0.9, 0.20, "wood")
        return
    if role == "guide":
        g.box(0, 0.18, 0, w, 0.36, d, "stone")
        for x in [-w / 2 + 0.6, w / 2 - 0.6]:
            for z in [-d / 2 + 0.6, d / 2 - 0.6]:
                g.box(x, 2, z, 0.35, 3.7, 0.35, "wood")
        roof(g, w, d, 3.9, 1.6, 0.75)
        g.box(0, 1.0, -1, 2.6, 0.25, 1.2, "wood_light")
        g.box(0, 1.45, -1.4, 1.8, 0.8, 0.12, "paper")
        return
    stories = building["stories"]
    story_h = 4.0 if role == "house" else (4.4 if role == "warehouse" else 4.7)
    plinth = 0.35 if city["region_id"] == "donghai" else 0.60
    height = stories * story_h
    g.box(0, plinth / 2, 0, w + 0.3, plinth, d + 0.3, "stone")
    # 四面连续墙体，前墙留出真实门洞；檐廊始终有落地柱。
    door_w = 1.5 if role == "house" else 2.8
    door_h = 2.6 if role == "house" else 3.2
    front = d / 2
    if role != "market":
        g.box(0, plinth + height / 2, -d / 2 + 0.18, w, height, 0.36, "wall")
        for x in [-w / 2 + 0.18, w / 2 - 0.18]:
            g.box(x, plinth + height / 2, 0, 0.36, height, d, "wall")
        side_w = (w - door_w) / 2
        for x in [-door_w / 2 - side_w / 2, door_w / 2 + side_w / 2]:
            g.box(x, plinth + height / 2, front - 0.18, side_w, height, 0.36, "wall")
        g.box(0, plinth + door_h + (height - door_h) / 2, front - 0.18, door_w, height - door_h, 0.36, "wall")
    else:
        g.box(0, plinth + height / 2, -d / 2 + 0.18, w, height, 0.36, "wall")
    g.box(0, plinth + 0.06, 0, w - 0.5, 0.12, d - 0.5, "wood_light")
    for x in [-w / 2 + 0.25, w / 2 - 0.25]:
        for z in [-d / 2 + 0.25, d / 2 - 0.25]:
            g.box(x, plinth + height / 2, z, 0.36, height, 0.36, "wood")
    for s in range(stories):
        sy = plinth + s * story_h
        g.box(0, sy + story_h - 0.24, front + 0.05, w, 0.3, 0.25, "wood")
        if s:
            g.box(0, sy, 0, w - 0.2, 0.24, d - 0.2, "wood")
        if detailed:
            for x in [-w * 0.30, w * 0.30]:
                window(g, x, sy + story_h * 0.52, front + 0.08, min(2.1, w / 5), 1.5)
                window(g, x, sy + story_h * 0.52, -front - 0.14, min(1.7, w / 5), 1.3)
    if detailed:
        depth = 1.8 if role != "house" else 1.2
        g.box(0, plinth / 2, front + depth / 2, w + 0.4, plinth, depth, "stone")
        for x in [-w / 2 + 0.55, w / 2 - 0.55]:
            g.box(x, plinth + height / 2, front + depth - 0.2, 0.30, height - 0.15, 0.30, "wood")
        # 连续向前延伸的主屋面覆盖通风廊。
        roof(g, w, d + depth, plinth + height, min(4.2, d * 0.28), 1.05)
        g.box(0, plinth + door_h + 0.35, front + 0.18, min(w * 0.5, 5.4), 0.65, 0.22, "accent")
        if stories > 1:
            by = plinth + story_h + 0.12
            g.box(0, by, front + depth * 0.5, w + 0.3, 0.25, depth, "wood")
            g.box(0, by + 1.0, front + depth - 0.1, w + 0.1, 0.16, 0.18, "wood_light")
            for i in range(max(3, int(w / 1.4))):
                x = -w / 2 + 0.4 + i * (w - 0.8) / (max(3, int(w / 1.4)) - 1)
                g.box(x, by + 0.52, front + depth - 0.1, 0.12, 1.0, 0.12, "wood")
        for i in range(2):
            g.box(0, plinth * (0.25 + i * 0.25), front + depth + (1 - i) * 0.30, door_w + 0.4, plinth * (0.5 + i * 0.5), 0.55, "stone")
    else:
        roof(g, w, d, plinth + height, d * 0.28, 0.9)
        # 未细化住区仍有真实入口，不是方框占位。
        g.box(0, plinth / 2, front + 0.5, 1.9, plinth, 1.0, "stone")
    if role == "smith":
        g.box(-w * 0.24, plinth + 1.1, front + 0.7, 2.2, 2.2, 1.7, "stone")
        g.box(-w * 0.24, plinth + 1.2, front + 1.58, 1.15, 0.9, 0.04, "dark")
        g.box(w * 0.22, plinth + 0.7, front + 0.7, 0.9, 1.4, 0.9, "wood")
        g.box(w * 0.22, plinth + 1.48, front + 0.7, 1.4, 0.30, 0.65, "iron")
    if role == "apothecary":
        for i in range(4):
            g.box(-w / 2 + 1.5 + i * 1.6, plinth + 0.6, front + 0.65, 1.2, 0.6, 0.65, "wood_light")
            g.box(-w / 2 + 1.5 + i * 1.6, plinth + 0.98, front + 0.65, 1.0, 0.16, 0.48, "herb")
    if role in ("warehouse", "market"):
        for i in range(4):
            g.box(-w / 2 + 1.3 + i * 1.5, plinth + 0.55, -d / 2 + 1.5, 1.1, 1.1, 1.1, "wood_light")
        if role == "market":
            for x in [-w * 0.28, w * 0.28]:
                g.box(x, plinth + 1, 0.4, w * 0.25, 0.32, 1.4, "wood_light")
    if role == "inn":
        g.box(-w * 0.28, plinth + 1.0, 1, 3.8, 0.20, 1.8, "wood_light")
        for i in range(9):
            sy = plinth + (i + 0.5) * story_h / 9
            g.box(w * 0.30, sy / 2 + plinth / 2, -d / 2 + 1.2 + i * 0.40, 1.5, sy - plinth, 0.42, "wood")
    if role == "landmark":
        # 地标是完整楼体与单一主屋盖；不用层层悬浮楼板制造轮廓。
        for x in [-w / 2 + 0.7, w / 2 - 0.7]:
            g.box(x, plinth + height / 2, front + 1.1, 0.55, height, 0.55, "wood_light")


def road_geometry(g, roads, terrain):
    for road in roads:
        g.context(tag=road["id"], layer="road")
        bridge = road["kind"] == "bridge"
        material = "wood_light" if bridge and terrain.city["region_id"] == "donghai" else "road"
        for a, b in zip(road["points"], road["points"][1:]):
            dx, dz = b[0] - a[0], b[2] - a[2]
            length = math.hypot(dx, dz)
            if length < 0.05:
                continue
            nx, nz = -dz / length, dx / length
            width = road["width"]
            # 所有上坡段都是带实心落地台阶的几何，而非悬在坡上的斜线。
            segments = max(1, math.ceil(length / (0.75 if a[1] != b[1] else 4)))
            for i in range(segments):
                t0, t1 = i / segments, (i + 1) / segments
                p0 = [a[j] + (b[j] - a[j]) * t0 for j in range(3)]
                p1 = [a[j] + (b[j] - a[j]) * t1 for j in range(3)]
                if a[1] != b[1]:
                    y = (p0[1] + p1[1]) / 2 + 0.06
                    p0[1] = p1[1] = y
                else:
                    p0[1] += 0.08
                    p1[1] += 0.08
                verts = [[p0[0] + nx * width / 2, p0[1], p0[2] + nz * width / 2],
                         [p0[0] - nx * width / 2, p0[1], p0[2] - nz * width / 2],
                         [p1[0] + nx * width / 2, p1[1], p1[2] + nz * width / 2],
                         [p1[0] - nx * width / 2, p1[1], p1[2] - nz * width / 2]]
                g.mesh(verts, [[0, 2, 1], [1, 2, 3]], material)
                if a[1] != b[1]:
                    bottom = min(terrain.natural(p0[0], p0[2]), terrain.natural(p1[0], p1[2]), p0[1]) - 0.3
                    # 一段实体步阶盒，几何量小且每段真正支承到坡面。
                    if abs(dx) < 0.01:
                        g.box(p0[0], (p0[1] + bottom) / 2, (p0[2] + p1[2]) / 2, width, p0[1] - bottom, abs(p1[2] - p0[2]) + 0.04, "stone")
                    elif abs(dz) < 0.01:
                        g.box((p0[0] + p1[0]) / 2, (p0[1] + bottom) / 2, p0[2], abs(p1[0] - p0[0]) + 0.04, p0[1] - bottom, width, "stone")
            if bridge:
                # 桥面、连续桥身和桥台；木栈桥桩穿过海水到海床。
                eastwest = abs(dx) > abs(dz)
                if eastwest:
                    g.box((a[0] + b[0]) / 2, a[1] - 0.35, a[2], length, 0.70, width, material)
                for i in range(max(2, int(length / 6)) + 1):
                    t = i / max(2, int(length / 6))
                    x, z = a[0] + dx * t, a[2] + dz * t
                    foot = min(a[1] - 0.75, terrain.natural(x, z))
                    for side in [-1, 1]:
                        xx, zz = x + nx * width * 0.42 * side, z + nz * width * 0.42 * side
                        g.box(xx, (a[1] + foot) / 2, zz, 0.5 if material == "wood_light" else 0.8, a[1] - foot, 0.5 if material == "wood_light" else 0.8, "wood" if material == "wood_light" else "stone")
                        if terrain.city["region_id"] == "lingyue":
                            g.box(xx, a[1] + 0.65, zz, 0.3, 1.3, 0.3, "stone")
                if terrain.city["region_id"] == "lingyue" and eastwest:
                    for side in [-1, 1]:
                        g.box((a[0] + b[0]) / 2, a[1] + 1.2, a[2] + side * width * 0.46, length, 0.25, 0.25, "stone")


def tree(g, x, y, z, size=1, mountain=False):
    g.context((x, y, z), tag="tree", layer="landscape")
    g.box(0, 2.0 * size, 0, 0.65 * size, 4.0 * size, 0.65 * size, "wood")
    if mountain:
        # 完整块状树冠，不用密集叶片遮蔽城市。
        g.box(0, 4.7 * size, 0, 4.4 * size, 3.4 * size, 3.8 * size, "leaf")
        g.box(0.4 * size, 6.6 * size, 0, 2.6 * size, 1.4 * size, 2.7 * size, "leaf_light")
    else:
        g.box(0, 4.1 * size, 0, 4.5 * size, 3.5 * size, 4.2 * size, "leaf")
        g.box(-0.5 * size, 5.5 * size, 0.2 * size, 3.1 * size, 1.9 * size, 3.4 * size, "leaf_light")


def person(g, x, y, z):
    g.context((x, y, z), tag="human_scale", layer="scale")
    # 1.8 格，原版方块人体比例，只有几何尺度参照。
    for lx in [-0.14, 0.14]:
        g.box(lx, 0.34, 0, 0.23, 0.68, 0.24, "dark")
    g.box(0, 1.02, 0, 0.48, 0.68, 0.28, "robe")
    for lx in [-0.36, 0.36]:
        g.box(lx, 1.02, 0, 0.20, 0.68, 0.24, "robe")
        g.box(lx, 0.72, 0, 0.20, 0.16, 0.24, "skin")
    g.box(0, 1.57, 0, 0.46, 0.46, 0.46, "skin")
    g.box(0, 1.74, -0.04, 0.48, 0.12, 0.42, "wood")


def boat(g, x, z, q=0):
    g.context((x, 5.85, z), q, tag="harbour_boat", layer="landscape")
    verts = [[-2.6,0.5,-5], [2.6,0.5,-5], [-3.2,0.5,3.5], [3.2,0.5,3.5], [0,0.5,7],
             [-1.8,-0.5,-4], [1.8,-0.5,-4], [-2,-0.5,3.2], [2,-0.5,3.2], [0,-0.4,5.4]]
    g.mesh(verts, [[0,5,1],[1,5,6],[0,2,5],[2,7,5],[1,6,3],[3,6,8],[2,4,7],[4,9,7],[3,8,4],[4,8,9]], "wood")
    g.box(0, 0.32, 0, 4.5, 0.20, 8, "wood_light")
    g.box(0, 5.1, 0, 0.27, 10, 0.27, "wood")
    g.mesh([[0,2.5,0.1],[0,9.7,0.1],[4.4,3.0,0.1]], [[0,1,2],[2,1,0]], "paper")


def landscape(g, terrain, city):
    g.context(layer="landscape")
    if city["region_id"] == "donghai":
        g.box(112, 5.74, 136, 224, 0.14, 272, "water", "water")
        table = city["terrain"]["shore_x_by_z"]
        for z in range(4, 269, 4):
            x = lerp_table(table, z)
            g.box(x + 0.7, 5.87, z, 1.0, 0.025, 3.2, "foam", "water")
        for x, z in [(19,16),(42,16),(77,16),(17,258),(65,258),(114,258),(147,257),(169,236),(147,155)]:
            tree(g, x, terrain.height(x, z), z, 0.75)
        boat(g, 207, 108)
        boat(g, 208, 163, 2)
        # 仓院货堆与码头系桩，不挡转运街。
        for x, z in [(134,88),(135,96),(134,118),(135,127),(180,111),(181,83)]:
            g.context((x, 8.1, z), tag="cargo", layer="landscape")
            g.box(0, 0.55, 0, 1.15, 1.1, 1.15, "wood_light")
            g.box(0, 0.58, 0, 1.18, 0.09, 1.18, "wood")
        g.label("外海 · 东侧水口开放", [214,7,55], "water")
        g.label("卸货栈桥 → 仓院", [167,11,104], "place")
        g.label("内陆民居组 · 部分仅外壳", [53,18,46], "district")
        g.label("滨水商街", [159,14,172], "district")
        scales = [(101,8.1,152),(146,8.1,108),(117,8.1,214),(170,8.1,207)]
    else:
        # 山涧沿连续谷底下降，谷桥明确跨在其上。
        vertices, faces = [], []
        for i, z in enumerate(range(134, 181, 2)):
            x = 119 + 1.1 * math.sin(z / 12)
            y = terrain.natural(x, z) + 0.35
            vertices.extend([[x - 1.3, y, z], [x + 1.3, y, z]])
            if i:
                a = (i - 1) * 2
                faces.extend([[a,a+2,a+1],[a+1,a+2,a+3]])
        g.mesh(vertices, faces, "water", "water")
        for x, z in [(12,39),(212,46),(8,87),(215,98),(12,122),(216,127),(12,174),(212,213),(10,239),(41,269),(177,268),(84,18),(153,17)]:
            tree(g, x, terrain.height(x, z), z, 0.9, True)
        g.label("下城 · 客栈与铁匠", [88,30,240], "district")
        g.label("谷桥 · 下方保留真实谷地", [118,39,153], "place")
        g.label("上城 · 沿等高线排列", [94,58,117], "district")
        g.label("连续山体 · 局部地块整地", [21,51,80], "district")
        scales = [(141,32.1,152),(97,32.1,152),(100,20.1,232),(149,44.1,108),(113,56.1,27)]
    for x, y, z in scales:
        person(g, x, y, z)
    g.label("人物 1.8 格 · 仅尺度参照", [scales[0][0], scales[0][1] + 3.1, scales[0][2]], "scale")
    # 真实长 10 格的地面标尺，可与人物、门和街宽同时比较。
    x, y, z = (99, 8.15, 217) if city["region_id"] == "donghai" else (80, 20.15, 235)
    g.context(tag="ten_block_scale", layer="scale")
    g.box(x + 5, y, z, 10, 0.08, 0.13, "dark")
    for i in range(11):
        g.box(x + i, y, z, 0.09, 0.08, 0.65 if i % 5 == 0 else 0.35, "dark")
    g.label("10 格", [x + 5, y + 1, z], "scale")


def walls(g, city, terrain):
    for index, line in enumerate(city["walls"]):
        g.context(tag=f"boundary_wall_{index}", layer="landscape")
        for a, b in zip(line, line[1:]):
            length = math.hypot(b[0] - a[0], b[2] - a[2])
            steps = max(1, int(length / 4))
            for i in range(steps):
                t = (i + 0.5) / steps
                x, z = a[0] + (b[0] - a[0]) * t, a[2] + (b[2] - a[2]) * t
                y = terrain.height(x, z)
                wx, wz = (length / steps + 0.08, 1.0) if abs(b[0] - a[0]) > abs(b[2] - a[2]) else (1.0, length / steps + 0.08)
                g.box(x, y + 1.45, z, wx, 2.9, wz, "stone")
                g.box(x, y + 3, z, wx + 0.12, 0.18, wz + 0.12, "roof_edge")


def compile_city(city, source_hashes, definition):
    buildings = expand_buildings(city)
    roads = access_roads(city, buildings)
    terrain = Terrain(city, buildings, roads)
    g = Geometry(city)
    heightfield = terrain.surface(g)
    road_geometry(g, roads, terrain)
    for building in buildings:
        building_geometry(g, building, city)
        if building["role"] != "house":
            label_y = building["position"][1] + (15 if building["role"] == "landmark" else 11)
            g.label(building["name"], [building["position"][0], label_y, building["position"][2]], "service" if building["role"] in ("inn","apothecary","smith","market","guide") else "place", building["id"])
    landscape(g, terrain, city)
    walls(g, city, terrain)
    core = [{"id": building["id"], "role": building["role"], "building_id": building["id"], "position": building["entrance"],
             "state": "geometry_and_anchor_only", "npc_runtime": "not_verified"}
            for building in buildings if building["role"] in ("inn","apothecary","smith","market","guide")]
    manifest = {
        "schema": 1, "city_id": city["city_id"], "region_id": city["region_id"], "name": city["name"],
        "stage": "preview", "units": "block",
        "bounds": {"min": [math.floor(v * 10000) / 10000 for v in g.minimum],
                   "max": [math.ceil(v * 10000) / 10000 for v in g.maximum]},
        "bounds_semantics": "closed_geometric_envelope",
        "authored_urban_frame": city["bounds"],
        "rotation_convention": "+X -> +Z; about authored origin, then translate; Three.js rotation.y=-q*PI/2",
        "profile_id": city["city_id"] + "_regional_authored_v1",
        "height_profile": "regional_authored_no_height_adaptation",
        "layout_identity": hashlib.sha256(json.dumps({"city": city, "roads": roads, "buildings": buildings,
                            "profile": "regional_authored_v1"}, sort_keys=True, ensure_ascii=False).encode()).hexdigest(),
        "coordinate_system": "+X east, +Y up, +Z south; origin local; 1 authored metre = 1 horizontal block",
        "source": {"kind": "authored", "ref": "blueprints/province_capitals/regional/cities.json",
                   "generator": "tools/province_capitals/regional/generate.py", "sha256": source_hashes, "paths_base": "repository_root",
                   "scale_reference": {"name": "当前云山 D1", "size": [224,82,272], "houses": 70, "functional_buildings": 4},
                   "language_reference_only": ["web/yunshan-city/src/voxel/roof.js", "web/yunshan-city/src/voxel/buildings.js"],
                   "not_derived_from_full_city_plan": True},
        "terrain": {"kind": "heightfield", "data": "scene.json", "data_pointer": "/terrain_heightfield", "path_base": "manifest_directory", "authored_profile": city["terrain"],
                    "support": "连续地形到 Y=0；局部地块台基与挡土面；桥柱到谷底或海床。"},
        "districts": city["districts"], "roads": roads, "buildings": buildings,
        "entries": [{"id": building["id"] + "_entry", "position": building["entrance"], "facing": building["facing"],
                     "building_id": building["id"], "road_id": building["road_id"]} for building in buildings],
        "services": core,
        "coverage": {"houses": sum(b["role"] == "house" for b in buildings),
                     "detailed_houses": sum(b["role"] == "house" and b["detail"] == "detailed" for b in buildings),
                     "envelope_houses": sum(b["role"] == "house" and b["detail"] == "envelope" for b in buildings),
                     "service_anchors": len(core), "landmarks": 1, "gates": 1,
                     "description": "完整城形、全部住宅外壳与入口、代表性详细街区；不是最终模型。"},
        "artifacts": {"path_base": "manifest_directory", "html": "index.html", "geometry": "scene.json", "obj": "model.obj", "mtl": "model.mtl"},
        "unimplemented": city["unimplemented"],
        "evidence": {"generation": "delivery_generation_only", "tests": "not_run", "unit_tests": "not_run",
                     "validator": "not_run", "lint_check": "not_run", "gradle": "not_run", "minecraft": "not_run", "cloud": "not_run",
                     "browser_runtime": "not_run", "owner_appearance": "not_verified", "npc_runtime": "not_verified",
                     "collision_walkability": "not_verified", "fps": "not_verified", "multiplayer": "not_verified"},
        "integration": {"production_enabled": False, "schema_dependency": "仅依据 coordination-contract.json schema=1；没有导入公共 schema.py。",
                        "id_status": "采纳 coordination-addendum.md：capital_donghai / capital_lingyue。",
                        "source_rotation": "两城独立 authored；未使用原云山 rot，不存在直接复制原始旋转语义。",
                        "deviations": []},
    }
    manifest["layout_family_id"] = definition["layout_id"]
    manifest["appearance_id"] = definition["appearance_id"]
    manifest["layout_sha256"] = manifest["layout_identity"]
    manifest["layout_id"] = city["city_id"] + "/regional_authored/" + manifest["layout_sha256"][:16]
    scene = {"schema": 1, "city_id": city["city_id"], "name": city["name"], "summary": city["summary"],
             "materials": g.materials, "boxes": g.boxes, "meshes": g.meshes, "labels": g.labels,
             "terrain_heightfield": heightfield, "cameras": city["cameras"], "manifest": manifest,
             "presentation_context": {"open_east_sea": city["region_id"] == "donghai", "sea_y": 5.8,
                                      "starts_x": 224, "kind": "viewer_backdrop_excluded_from_authored_geometry_bounds"}}
    return scene, manifest


def write_obj(path, scene):
    """输出普通 OBJ 三角网格，便于后续独立导入；不转换 Minecraft NBT。"""
    mats = scene["materials"]
    mtl = ["# 预览材质；玩家外观待确认"]
    for key, value in mats.items():
        color = value["color"].lstrip("#")
        rgb = [int(color[i:i+2], 16) / 255 for i in (0, 2, 4)]
        mtl.extend(["newmtl " + key, "Kd " + " ".join(f"{v:.4f}" for v in rgb), "d 1", ""])
    path.with_suffix(".mtl").write_text("\n".join(mtl), encoding="utf-8")
    cube_faces = [[0,2,1],[0,3,2],[4,5,6],[4,6,7],[0,1,5],[0,5,4],[3,7,6],[3,6,2],[1,2,6],[1,6,5],[0,4,7],[0,7,3]]
    offset = 1
    with path.open("w", encoding="utf-8") as handle:
        handle.write("# " + scene["name"] + "；1 单位=1 格；待确认三维样板\nmtllib model.mtl\n")
        for index, box in enumerate(scene["boxes"]):
            w,h,d = box["s"]
            vertices = [[-w/2,-h/2,-d/2],[w/2,-h/2,-d/2],[w/2,h/2,-d/2],[-w/2,h/2,-d/2],
                        [-w/2,-h/2,d/2],[w/2,-h/2,d/2],[w/2,h/2,d/2],[-w/2,h/2,d/2]]
            handle.write(f"o box_{index}\nusemtl {box['m']}\n")
            for x,y,z in vertices:
                rx,rz = rotated(x,z,box["q"])
                handle.write(f"v {rx+box['p'][0]:.4f} {y+box['p'][1]:.4f} {rz+box['p'][2]:.4f}\n")
            for face in cube_faces:
                handle.write("f " + " ".join(str(v+offset) for v in face) + "\n")
            offset += 8
        for index, mesh in enumerate(scene["meshes"]):
            handle.write(f"o mesh_{index}\nusemtl {mesh['m']}\n")
            for vertex in mesh["v"]:
                handle.write("v " + " ".join(f"{v:.4f}" for v in vertex) + "\n")
            for face in mesh["f"]:
                handle.write("f " + " ".join(str(v+offset) for v in face) + "\n")
            offset += len(mesh["v"])


def make_html(scenes, vendor, viewer, template):
    # 高度场的网格索引是规则拓扑，HTML 中据高度值还原，避免重复携带数万个索引。
    # scene.json / OBJ 仍保留全部显式几何；这不是简化城形或减少房屋。
    compact = []
    for scene in scenes:
        slim = dict(scene)
        slim["meshes"] = [m for m in scene["meshes"] if not (m["tag"] == "continuous_terrain" and m["m"] == "ground")]
        slim["terrain_mesh_from_heightfield"] = True
        compact.append(slim)
    payload = json.dumps({"three": vendor, "viewer": viewer, "scenes": compact}, ensure_ascii=False, separators=(",", ":")).encode()
    compressed = base64.b64encode(gzip.compress(payload, mtime=0, compresslevel=9)).decode()
    return template.replace("__REGIONAL_PAYLOAD_GZIP_BASE64__", compressed)


def main():
    data = json.loads(AUTHOR.read_text(encoding="utf-8"))
    definitions = {row["city_id"]: row for row in json.loads(CATALOG.read_text(encoding="utf-8"))["capitals"]}
    sources = [AUTHOR, CATALOG, HERE/"generate.py", HERE/"viewer.js", HERE/"template.html",
               ROOT/"web/yunshan-city/vendor/three.module.min.js", ROOT/"blueprints/province_capitals/regional/remaining-regions.json"]
    hashes = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    scenes = []
    vendor = (ROOT/"web/yunshan-city/vendor/three.module.min.js").read_text(encoding="utf-8")
    viewer = (HERE/"viewer.js").read_text(encoding="utf-8")
    template = (HERE/"template.html").read_text(encoding="utf-8")
    for city in data["cities"]:
        scene, manifest = compile_city(city, hashes, definitions[city["city_id"]])
        dest = OUT/city["region_id"]
        write_json(dest/"manifest.json", manifest)
        write_json(dest/"scene.json", scene)
        write_obj(dest/"model.obj", scene)
        (dest/"index.html").write_text(make_html([scene], vendor, viewer, template), encoding="utf-8")
        scenes.append(scene)
    (OUT/"index.html").write_text(make_html(scenes, vendor, viewer, template), encoding="utf-8")
    write_json(OUT/"delivery.json", {"schema": 1, "stage": "preview", "source_sha256": hashes,
               "html": "index.html", "cities": [{"city_id": s["city_id"], "manifest": s["manifest"]["region_id"]+"/manifest.json",
               "html": s["manifest"]["region_id"]+"/index.html", "coverage": s["manifest"]["coverage"]} for s in scenes],
               "remaining_regions": "blueprints/province_capitals/regional/remaining-regions.json",
               "generation": "delivery_generation_only", "all_tests": "not_run", "browser_runtime": "not_run",
               "owner_appearance": "not_verified", "production_assets_modified": False})
    print("已生成交付产物（不是测试结果）：")
    for path in [OUT/"index.html", *[OUT/s["manifest"]["region_id"]/"manifest.json" for s in scenes]]:
        print(f"{path} ({path.stat().st_size} bytes)")
    print("所有测试 not_run；浏览器运行 not_run；玩家外观 not_verified。")


if __name__ == "__main__":
    main()
