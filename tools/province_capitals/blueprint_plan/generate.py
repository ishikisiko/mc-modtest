#!/usr/bin/env python3
"""省城整城蓝图：同源空间清单、连接方案和预览，不改默认世界生成。"""
from __future__ import annotations

import argparse
import base64
from collections import Counter, deque
import copy
import gzip
import hashlib
from html import escape
import json
import math
import re
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from province_capitals.regional.v2.city import Capital
from province_capitals.regional.v2.buildings import Building
from province_capitals.regional.v2.common import AIR, DIR, point
from province_capitals.regional.v2.preview import SceneBuilder
from province_capitals.environment_design.geometry import Scene
from province_capitals.blueprint_plan.regional_roads import elevation_plan

HERE = Path(__file__).resolve().parent
DEFAULT = ROOT / "blueprints/province_capitals/blueprint_plan/lingyue.json"


def region_id(spec):
    city_id = spec.get("city_id", "")
    if not re.fullmatch(r"capital_[a-z]+", city_id):
        raise ValueError("蓝图 city_id 无效")
    return city_id.removeprefix("capital_")


def landmark_scene(environment, catalog):
    landmark = next(s for s in catalog["landmarks"] if s["region"] == environment["region"])
    rotation = environment.get("landmark_rotation_quarters", 0)
    if type(rotation) is not int or rotation not in range(4):
        raise ValueError("主地标朝向必须为0–3个直角")
    scale = environment.get("landmark_scale", 1)
    if not isinstance(scale, (int, float)) or isinstance(scale, bool) or scale <= 0:
        raise ValueError("主地标比例必须为正数")
    scene = Scene(catalog["materials"])
    scene.recipe(landmark["recipe"], catalog["recipes"], environment["landmark_origin"],
                 scale, "landmark", "landmark", rotation)
    if not scene.boxes:
        raise ValueError("主地标配方没有实体几何")
    return scene


def environment_input(spec, environment, catalog=None):
    """新蓝图的明确庭院方案；不回写在用环境源。"""
    result = copy.deepcopy(environment)
    if result.get("region") != region_id(spec):
        raise ValueError("蓝图与环境城市身份不一致")
    if catalog is None:
        catalog = read(ROOT / spec["sources"]["landmarks"])
    lo, hi = landmark_scene(result, catalog).bounds()
    actual = [math.floor(lo[0]), math.floor(lo[2]), math.ceil(hi[0])-1, math.ceil(hi[2])-1]
    reserve = copy.deepcopy(spec.get("reserve", result.get("reserve", actual)))
    if (not isinstance(reserve, list) or len(reserve) != 4 or any(type(x) is not int for x in reserve)
            or reserve[0] > reserve[2] or reserve[1] > reserve[3]):
        raise ValueError("地标预留须为有效整数 XZ 闭区间")
    if not (reserve[0] <= actual[0] and reserve[1] <= actual[1]
            and reserve[2] >= actual[2] and reserve[3] >= actual[3]):
        raise ValueError("地标预留不能小于实际旋转后几何：" + str(actual))
    result["reserve"] = reserve
    if "gardens" in spec:
        result["gardens"] = copy.deepcopy(spec["gardens"])
    overrides = spec.get("garden_overrides", {})
    indexed = {g["id"]: g for g in result["gardens"]}
    if overrides.keys() - indexed.keys():
        raise ValueError("庭院调整引用了不存在的 ID")
    for gid, changes in overrides.items():
        if changes.keys() - {"bounds", "y", "kind", "near_house", "name"}:
            raise ValueError("庭院调整含未支持字段：" + gid)
        indexed[gid].update(copy.deepcopy(changes))
    return result


def make_planner(spec, city, environment, catalog, *, block_world=None):
    backend = spec.get("backend", "regional")
    if backend == "manor_relayout":
        from province_capitals.blueprint_plan.manor_relayout import ManorPlanner
        return ManorPlanner(spec,city,environment,catalog)
    if backend == "regional":
        return Planner(spec, city, environment, catalog, block_world=block_world)
    if backend == "central":
        from province_capitals.blueprint_plan.central import create_planner
        return create_planner(spec, city, environment, catalog, block_world=block_world)
    raise ValueError("未知蓝图生成器：" + str(backend))


def read(path):
    return json.loads(Path(path).read_text())


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(",", ":")).encode()).hexdigest()


def rect(bounds):
    x0, z0, x1, z1 = bounds
    return {(x, z) for z in range(z0, z1 + 1) for x in range(x0, x1 + 1)}


def perimeter(bounds):
    cells = rect(bounds)
    return sorted(p for p in cells if any((p[0]+dx, p[1]+dz) not in cells for dx, dz in DIR.values()))


def runs(cells):
    rows = []
    for z in sorted({z for x, z in cells}):
        xs = sorted(x for x, zz in cells if zz == z)
        for x in xs:
            if rows and rows[-1][0] == z and rows[-1][2] == x - 1:
                rows[-1][2] = x
            else:
                rows.append([z, x, x])
    return rows


def passable(state):
    return state in (None, AIR) or "_door[" in state


def search_path(starts, goals, valid, max_length):
    """有界三维搜索。端点在有效集合中，且相邻只能跨一格高度。"""
    queue = deque(sorted(starts)); previous = {p: None for p in starts}
    distance = {p: 0 for p in starts}
    while queue:
        at = queue.popleft()
        if at in goals:
            path = []
            while at is not None:
                path.append(at); at = previous[at]
            return list(reversed(path))
        if distance[at] >= max_length:
            continue
        x, y, z = at
        for dx, dz in DIR.values():
            for dy in (0, -1, 1):
                nxt = (x + dx, y + dy, z + dz)
                if nxt in previous or not valid(nxt):
                    continue
                previous[nxt] = at; distance[nxt] = distance[at] + 1; queue.append(nxt)
    return []


class Planner:
    def __init__(self, spec, city, environment, catalog, *, block_world=None):
        environment = environment_input(spec, environment, catalog)
        self.spec, self.env, self.catalog = spec, environment, catalog
        self.block_world = block_world
        self.rules = spec["rules"]
        if spec["schema"] != 1 or spec["city_id"] != city["city_id"]:
            raise ValueError("蓝图 schema 或城市身份不一致")
        if self.rules["door_width"] != 1 or self.rules["path_width"] != 1:
            raise ValueError("本试验只支持一格门洞与一格庭院支路；不能忽略配置宽度")
        if any(g["access"] not in ("private", "public") for g in spec["garden_bindings"].values()):
            raise ValueError("庭院访问方式必须为 private/public")
        city = copy.deepcopy(city)
        self.removed = environment["remove_buildings"]
        self.model = Capital(city).construct()
        # 与现有环境流程一致：先完整构造、校验城市，再应用明确的环境替换。
        for b in self.model.buildings:
            if b["id"] in self.removed:
                lo, hi = b["bounds"]["min"], b["bounds"]["max"]
                self.model.construction.box((lo[0], b["position"][1], lo[2]), tuple(hi), AIR, b["id"], "clearance")
        self.model.buildings = [b for b in self.model.buildings if b["id"] not in self.removed]
        self.model.access = [a for a in self.model.access if a["building_id"] not in self.removed]
        self.model.occupied = {p: bid for p, bid in self.model.occupied.items() if bid not in self.removed}
        self.model.footprints = {bid: cells for bid, cells in self.model.footprints.items() if bid not in self.removed}
        if environment.get("clear_road"):
            rid = environment["clear_road"]["id"]
            self.model.roads = [r for r in self.model.roads if r["id"] != rid] + copy.deepcopy(environment.get("retained_roads", []))
        if environment.get("remove_roads"):
            self.model.roads = [r for r in self.model.roads if r["id"] not in environment["remove_roads"]]
        self.model.road_columns = {}; self.model.road_goals = {}
        self.model.make_road_columns()
        self.original_terrain = self.model.terrain[:]
        self.road_elevation_changes,self.elevation_cells=elevation_plan(self.model,spec)
        self.buildings = {b["id"]: b for b in self.model.buildings}
        self.tiles = {}
        self.paths, self.doors, self.earthwork, self.fixtures = [], [], {}, []
        self.gardens, self.issues = [], []
        garden_ids = [g["id"] for g in environment["gardens"]]
        if len(garden_ids) != len(set(garden_ids)):
            raise ValueError("庭院 ID 重复")
        self.garden_columns = {p: g["id"] for g in environment["gardens"] for p in rect(g["bounds"])}
        if len(self.garden_columns) != sum(len(rect(g["bounds"])) for g in environment["gardens"]):
            raise ValueError("庭院范围互相重叠")
        if self.garden_columns.keys() & self.model.occupied.keys():
            raise ValueError("庭院范围侵入既有建筑地块")
        if set(spec["garden_bindings"]) != {g["id"] for g in environment["gardens"]}:
            raise ValueError("庭院归属必须完整覆盖环境输入，不允许漏项或多余绑定")
        self.reserve = rect(environment["reserve"])
        self.proposed_columns = {}
        self.context = Scene(catalog["materials"])
        self.context_objects = [o for o in environment.get("public_objects", []) + environment.get("frontages", [])
                                if o.get("house_id") not in self.removed]
        for o in self.context_objects:
            self.context.recipe(o["recipe"], catalog["recipes"], o["position"], o["scale"], "context", o["name"], o.get("q", 0))
        self.context_cells = set()
        for b in self.context.boxes:
            low = [math.floor(p-s/2) for p,s in zip(b["p"],b["s"])]
            high = [math.ceil(p+s/2)-1 for p,s in zip(b["p"],b["s"])]
            self.context_cells.update((x,y,z) for x in range(low[0],high[0]+1)
                                      for y in range(low[1],high[1]+1) for z in range(low[2],high[2]+1))
        self.public_passages = []
        self.public_passage_cells = set()
        self.prepare_public_passages()

    def prepare_public_passages(self):
        """只允许在实名门楼和实名道路交集内打开首层通道。"""
        seen = set()
        for source in self.spec.get("public_passages", []):
            item = copy.deepcopy(source)
            if not item.get("id") or item["id"] in seen or not item.get("reason"):
                raise ValueError("公共开口须有唯一 ID 和明确设计理由")
            seen.add(item["id"])
            owner = self.buildings.get(item.get("owner"))
            if owner is None or owner["role"] != "gate":
                raise ValueError("公共开口必须属于现有门楼")
            bounds = item.get("bounds", [])
            if (len(bounds) != 4 or any(type(v) is not int for v in bounds)
                    or bounds[0] > bounds[2] or bounds[1] > bounds[3]):
                raise ValueError("公共开口范围必须为整数 XZ 闭区间")
            y, height = item.get("y"), item.get("height")
            if (type(y) is not int or type(height) is not int or y != owner["position"][1]
                    or height != self.rules["headroom"]):
                raise ValueError("公共开口只允许门楼首层及声明净空高度")
            cells = rect(bounds)
            footprint = self.model.footprints[owner["id"]]
            if not cells <= footprint:
                raise ValueError("公共开口越出所属门楼实际足迹")
            along_z = owner["facing"] in ("north", "south")
            cross = 0 if along_z else 1
            if (min(p[cross] for p in cells) <= min(p[cross] for p in footprint)
                    or max(p[cross] for p in cells) >= max(p[cross] for p in footprint)):
                raise ValueError("公共开口必须保留两侧门墩")
            clearance = []
            changes = []
            for x,z in sorted(cells):
                road = self.model.road_columns.get((x,z))
                if road is None or road[4] != item.get("road_id") or road[0] != y:
                    raise ValueError("公共开口必须处于同高的声明道路内")
                if self.model.occupied.get((x,z)) != owner["id"]:
                    raise ValueError("公共开口碰到其他建筑归属")
                for dy in range(height):
                    p = (x,y+dy,z)
                    if p in self.public_passage_cells:
                        raise ValueError("公共开口声明互相重叠")
                    before = self.state(p)
                    if (self.block_world is not None and p in self.block_world.protected
                            and before not in (None,AIR)):
                        raise ValueError("公共开口不能清除受保护岗位或设施")
                    clearance.append(list(p))
                    if before not in (None,AIR):
                        changes.append({"position":list(p),"before":before,"after":AIR})
            item.update(cells=clearance, changes=changes)
            self.public_passages.append(item)
            self.public_passage_cells.update(tuple(p) for p in clearance)

    def state(self, p):
        if p in getattr(self, "public_passage_cells", ()):
            return AIR
        if self.block_world is None and p in self.elevation_cells:
            return self.elevation_cells[p]
        x, y, z = p; key = (x // 16, z // 16)
        if self.block_world is not None:
            return self.block_world.tile(key).get((x % 16, y, z % 16))
        if key not in self.tiles:
            self.tiles[key] = self.model.construction.tile(*key)
        return self.tiles[key].get((x % 16, y, z % 16), (None, "", ""))[0]

    def clear(self, p, height):
        x,y,z = p
        return all((self.block_world is not None or (x,y+dy,z) not in self.context_cells)
                   and passable(self.state((x,y+dy,z))) for dy in range(height))

    def fixture_clear(self, p, height):
        x,y,z=p
        return self.clear(p,height) and (self.block_world is None or
               all((x,y+dy,z) not in self.block_world.protected for dy in range(height)))

    def door_material(self, owner):
        """新门沿用所属建筑实际生成的门，样式不必额外提供 DOOR 槽。"""
        b = self.buildings[owner]
        plan = Building(b, self.model.style, self.model.city["construction"], self.model.library).build()
        for pos in sorted(plan.cells):
            state = plan.cells[pos][0]
            if state.split("[", 1)[0].endswith("_door"):
                return state.split("[", 1)[0]
        # 开敞厅堂可能没有现有门；仍让 owning Building.opening 决定门材。
        plan.opening(*plan.south_door)
        return next(state.split("[", 1)[0] for state, _, _ in plan.cells.values()
                    if state.split("[", 1)[0].endswith("_door"))

    def door_candidates(self, owner):
        """门洞必须一侧连接现有可达室内，一侧连接无障碍檐廊/外部。"""
        b = self.buildings[owner]
        plan = Building(b, self.model.style, self.model.city["construction"], self.model.library).build()
        allowed = set()
        for x in range(plan.w):
            for z in range(plan.d):
                p = (x, z)
                if all(passable(plan.cells.get((x, y, z), (AIR,))[0]) for y in (0, 1)):
                    allowed.add(p)
        start = (plan.entrance[0], plan.entrance[2])
        reachable = {start}; queue = deque([start])
        while queue:
            x, z = queue.popleft()
            for dx, dz in DIR.values():
                nxt = (x+dx, z+dz)
                if nxt in allowed and nxt not in reachable:
                    reachable.add(nxt); queue.append(nxt)
        candidates = {}
        for x, z in sorted(plan.coverage[0] - plan.interiors[0]):
            state, _, layer = plan.cells.get((x, 0, z), (AIR, "", ""))
            if layer not in ("wall", "building", "door") or state == AIR:
                continue
            for face, (dx, dz) in DIR.items():
                inside = (x - dx, z - dz); outside = (x + dx, z + dz)
                if inside not in plan.interiors[0] or inside not in reachable or outside in plan.coverage[0]:
                    continue
                wp = point(b, x, 0, z); out = point(b, outside[0], 0, outside[1])
                if not self.clear(out, self.rules["headroom"]):
                    continue
                if any(plan.cells.get((x, dy, z), (AIR, "", ""))[2] == "frame" for dy in range(2)):
                    continue
                wdx, wdz = out[0]-wp[0], out[2]-wp[2]
                facing = next(k for k, v in DIR.items() if v == (wdx, wdz))
                candidates[out] = {"owner": owner, "position": list(wp), "outside": list(out),
                                   "inside": list(point(b, *[inside[0], 0, inside[1]])),
                                   "facing": facing, "action": "retain_door" if "_door[" in state else "open_door",
                                   "width": self.rules["door_width"], "headroom": self.rules["headroom"]}
        return candidates

    def garden_path(self, garden, binding):
        gid, height = garden["id"], garden["y"]
        private = binding["access"] == "private"
        owner = binding.get("owner")
        if private:
            if owner not in self.buildings:
                raise ValueError(f"{gid}: 私院缺有效房屋归属")
            if self.buildings[owner]["position"][1] != height:
                raise ValueError(f"{gid}: 私院与房屋标高不同，须显式设计台阶后再归属")
            if rect(garden["bounds"]) & self.model.court_columns.keys():
                raise ValueError(f"{gid}: 私院不能占用既有公共前场")
            doors = self.door_candidates(owner)
            goals = set(doors)
        else:
            doors = {}
            goals = {(x, v[0], z) for (x, z), v in self.model.road_columns.items()}
        starts = {(x, height, z) for x, z in perimeter(garden["bounds"])}
        limit = self.rules["private_access_max_length" if private else "public_access_max_length"]
        earth_limit = self.rules["local_earthwork_limit"]
        def valid(p):
            x, y, z = p; column = (x, z)
            if not (0 <= x < self.model.width and 0 <= z < self.model.depth and 2 <= y < self.model.height-3):
                return False
            if private and y != height:
                return False
            if column in self.reserve or self.garden_columns.get(column, gid) != gid:
                return False
            if column in self.model.occupied and self.model.occupied[column] != owner:
                return False
            if private and column in self.model.road_columns:
                return False
            if private and column in self.model.court_columns:
                return False
            if column in self.proposed_columns and self.proposed_columns[column] != gid:
                return False
            if column in self.model.road_columns:
                return p in goals and self.clear(p, self.rules["headroom"])
            if p not in starts and column in rect(garden["bounds"]):
                return False
            if abs(y - (self.model.ground(x, z)+1)) > earth_limit and column not in self.model.occupied:
                return False
            if not self.clear((x,y,z), self.rules["headroom"]):
                return False
            return True
        starts = {p for p in starts if valid(p)}
        path = search_path(starts, {p for p in goals if valid(p)}, valid, limit)
        if not path:
            self.issues.append({"id": gid, "kind": "unconnected_garden", "message": "在既定距离、净空与整地边界内未找到接路", "bounds": garden["bounds"]})
            return [], None
        return path, doors.get(path[-1])

    def program(self, g, path):
        x0, z0, x1, z1 = g["bounds"]; y = g["y"]
        center = ((x0+x1)//2, (z0+z1)//2)
        cells = rect(g["bounds"])
        starts = {tuple(path[0])} if path else {(x0, y, z0)}
        gate = next(iter(starts))
        edge = set(perimeter(g["bounds"]))
        free = {(x,z) for x,z in cells if self.clear((x,y,z),self.rules["headroom"])
                and ((x,z) not in edge or abs(x-gate[0])+abs(z-gate[2])<=1)}
        if not free:
            self.issues.append({"id":g["id"],"kind":"garden_blocked","message":"庭院内没有可站立空间","bounds":g["bounds"]})
            return [], []
        target = min(free, key=lambda p: (abs(p[0]-center[0])+abs(p[1]-center[1]),p))
        internal = search_path(starts, {(target[0], y, target[1])}, lambda p: (p[0],p[2]) in free and p[1] == y, len(cells))
        if not internal:
            self.issues.append({"id":g["id"],"kind":"garden_inside_unreachable","message":"院门无法连接庭院内部","bounds":g["bounds"]})
        reserved = {(x,z) for x,_,z in internal}
        fixtures = []
        for n, source in enumerate(self.spec["garden_programs"][g["kind"]]["fixtures"]):
            w, h, d = source["size"]
            candidates = []
            for z in range(z0+1, z1-d+1):
                for x in range(x0+1, x1-w+1):
                    footprint = rect([x, z, x+w-1, z+d-1])
                    if not footprint & reserved and all(self.fixture_clear((xx,y,zz),h) for xx,zz in footprint):
                        candidates.append((abs(x+w/2-center[0])+abs(z+d/2-center[1]), x, z, footprint))
            if not candidates:
                self.issues.append({"id": g["id"], "kind": "fixture_no_room", "message": f"{source['name']}与院内通路冲突", "bounds": g["bounds"]})
                continue
            _, x, z, footprint = min(candidates, key=lambda c: c[:3]); reserved |= footprint
            fixtures.append({**source, "id": f"{g['id']}_{n}", "garden_id": g["id"], "position": [x,y,z]})
        return internal, fixtures

    def compile(self):
        for source in self.env["gardens"]:
            binding = self.spec["garden_bindings"][source["id"]]
            g = {k: copy.deepcopy(source[k]) for k in ("id", "bounds", "y", "kind")}
            g.update(binding)
            path, door = self.garden_path(g, binding)
            g["path"] = [list(p) for p in path]; g["gate"] = list(path[0]) if path else None
            g["status"] = "planned_connected" if path else "unresolved"
            g["door"] = door
            internal, fixtures = self.program(g, path)
            g["internal_path"] = [list(p) for p in internal]; g["fixtures"] = fixtures
            self.fixtures.extend(fixtures)
            if door:
                self.doors.append({"garden_id": g["id"], **door})
            if path:
                self.paths.append({"id": g["id"], "access": g["access"], "owner": g.get("owner"),
                                   "width": self.rules["path_width"], "points": g["path"],
                                   "road_id": self.model.road_columns[(path[-1][0],path[-1][2])][4] if g["access"] == "public" else None})
                self.proposed_columns.update({(x,z): g["id"] for x,_,z in path})
            self.gardens.append(g)
        for g in self.gardens:
            if g["status"] != "planned_connected":
                continue
            points = {(x,g["y"],z) for x,z in rect(g["bounds"])} | {tuple(p) for p in g["path"]}
            for x,y,z in sorted(points):
                if (x,z) in self.model.road_columns or (x,z) in self.model.occupied:
                    continue
                before = self.original_terrain[z*self.model.width+x] + 1
                if abs(y-before) > self.rules["local_earthwork_limit"]:
                    self.issues.append({"id": g["id"], "kind": "earthwork_limit", "message": f"({x},{z})地面需从{before}调至{y}", "bounds": [x,z,x,z]})
                    continue
                self.earthwork[x,z] = {"column": [x,z], "before_air_y": before, "air_y": y, "owner": g["id"]}
        return self.export()

    def export(self):
        plots = []
        for b in self.model.buildings:
            cells = set(self.model.footprints[b["id"]])
            gardens = [g for g in self.gardens if g.get("owner") == b["id"]]
            for g in gardens:
                cells |= rect(g["bounds"])
                cells |= {(x,z) for x,y,z in g["path"]}
            plots.append({"id": b["id"], "name": b["name"], "role": b["role"], "district_id": b["district_id"],
                          "building": b, "garden_ids": [g["id"] for g in gardens], "cell_runs": runs(cells),
                          "entrance": b["entrance"], "road_id": b["road_id"]})
        owners = {}
        for plot in plots:
            for z,x0,x1 in plot["cell_runs"]:
                for x in range(x0,x1+1):
                    if (x,z) in owners:
                        self.issues.append({"id":plot["id"],"kind":"plot_overlap","other":owners[x,z],"message":"地块归属重叠","bounds":[x,z,x,z]})
                    owners[x,z]=plot["id"]
        counts = Counter(b["role"] for b in self.model.buildings)
        courtyards = []
        bindings = self.spec.get("courtyard_bindings", {})
        if bindings.keys() - {c["id"] for c in self.model.city["courtyards"]}:
            raise ValueError("公共前场归属引用了不存在的 ID")
        for source in self.model.city["courtyards"]:
            binding = bindings.get(source["id"], {"access":source.get("access","public"), "owner":source.get("owner","public"),
                "reason":source.get("reason","沿用原城市声明的公共前场，保留公共道路与设施通行。")})
            if (binding.keys() - {"access","owner","steward","reason","name"}
                    or binding.get("access") != "public" or not binding.get("owner") or not binding.get("reason")):
                raise ValueError("既有公共前场须保持公共开放并明确维护归属")
            courtyards.append({**copy.deepcopy(source),**copy.deepcopy(binding)})
        landmark = next(s for s in self.catalog["landmarks"] if s["region"] == self.env["region"])
        return {"schema": 1, "city_id": self.spec["city_id"], "region": region_id(self.spec),
                "name": self.spec["name"], "scope": self.spec["scope"],
                "dimensions": [self.model.width,self.model.height,self.model.depth],
                "terrain": self.original_terrain, "terrain_policy": self.spec.get("terrain_policy", "沿用当前城市地形；局部整地仅在庭院与接路范围内"),
                "districts": self.model.city["districts"], "terraces": self.model.city.get("terrain_patches", []),
                "roads": self.model.roads, "building_access": self.model.access,
                "plots": plots, "gardens": self.gardens, "courtyards": courtyards,
                "landmark": {"name":landmark["name"], "bounds":self.env["reserve"],"origin":self.env["landmark_origin"]},
                "wall": self.env["wall"], "paths":self.paths, "doors":self.doors,
                "public_passages":getattr(self,"public_passages",[]),
                "road_elevation_changes":getattr(self,"road_elevation_changes",{}),
                "retained_environment":self.context_objects, "landmark_paths":self.env["game_paths"],
                "earthwork": list(self.earthwork.values()), "fixtures":self.fixtures,
                "removed_source_buildings":self.removed, "design_notes":self.spec["design_notes"],
                "checks": {"status":"pass" if not self.issues else "needs_design", "issues":self.issues,
                           "houses": counts["house"], "public_buildings":len(plots)-counts["house"],
                           "plots":len(plots), "gardens":len(self.gardens), "connected_gardens":len(self.paths),
                           "new_doors":sum(d["action"]=="open_door" for d in self.doors),
                           "private_gardens":sum(g["access"]=="private" for g in self.gardens),
                           "public_gardens":sum(g["access"]=="public" for g in self.gardens),
                           "public_passages":len(getattr(self,"public_passages",[])),
                           "game_runtime":"not_verified", "owner_acceptance":"not_verified"}}


def preview_scene(planner, data):
    if planner.spec.get("backend") == "manor_relayout":
        from province_capitals.blueprint_plan.manor_relayout_preview import preview_scene as manor_preview
        return manor_preview(planner,data)
    if planner.spec.get("backend") == "central":
        from province_capitals.blueprint_plan.central import preview_scene as central_preview
        return central_preview(planner, data)
    model = planner.model
    # 预览只消费已经编译的施工记录，基础生成器和 authored 城市输入保持原样。
    for e in data["earthwork"]:
        x,z=e["column"]; model.set_ground(x,z,e["air_y"]-1)
    for e in data.get("road_elevation_changes",{}).get("earthwork",[]):
        x,z=e["column"];model.set_ground(x,z,e["air_y"]-1)
    builder = SceneBuilder(model)
    holes = {tuple(d["position"]):d for d in data["doors"] if d["action"]=="open_door"}
    for tx,tz in sorted(model.construction.by_tile):
        tile = model.construction.tile(tx,tz)
        for (x,y,z),state in planner.elevation_cells.items():
            if (x//16,z//16)==(tx,tz):tile[x%16,y,z%16]=(state,"road_elevation","road")
        for (x,y,z),d in holes.items():
            if (x//16,z//16)==(tx,tz):
                for dy in range(d["headroom"]):
                    tile[x%16,y+dy,z%16]=(AIR,d["owner"],"clearance")
        for opening in data.get("public_passages", []):
            for x,y,z in opening["cells"]:
                if (x//16,z//16)==(tx,tz):tile[x%16,y,z%16]=(AIR,opening["owner"],"clearance")
        builder.add_tile(tx,tz,tile)
    materials = {**planner.catalog["materials"], **{k:{"color":v} for k,v in builder.palette.items()},
                 "private":{"color":"#90b48c"},"public":{"color":"#d4b685"},"route":{"color":"#c7ddbd"},
                 "door_new":{"color":"#e2b960"}}
    scene = Scene(materials); scene.boxes = builder.boxes + planner.context.boxes; scene.meshes = builder.meshes
    for g in data["gardens"]:
        x0,z0,x1,z1=g["bounds"]; y=g["y"]
        scene.box(x0,y,z0,x1-x0+1,.12,z1-z0+1,g["access"],"gardens",g["id"])
        if g["gate"]:
            gx,_,gz=g["gate"]
            for x,z in perimeter(g["bounds"]):
                if abs(x-gx)+abs(z-gz)<=1:
                    continue
                scene.box(x,y+.12,z,1,.55,1,"stone_light","gardens",g["id"])
        for x,py,z in g["path"]+g["internal_path"]:
            scene.box(x,py+.13,z,1,.12,1,"route","paths",g["id"])
    for d in data["doors"]:
        if d["action"]=="open_door":
            x,y,z=d["position"]
            scene.box(x+.15,y,z+.15,.7,2,.7,"door_new","doors",d.get("garden_id",d["owner"]))
    for f in data["fixtures"]:
        x,y,z=f["position"]; w,h,d=f["size"]
        kind=f["kind"]
        if kind=="tree":
            scene.box(x+w/2-.3,y,z+d/2-.3,.6,h-2,.6,"wood","fixtures",f["garden_id"])
            scene.box(x,y+h-3,z,w,3,d,"leaf","fixtures",f["garden_id"])
        elif kind=="drying_rack":
            for xx in [x,x+w-.2]:scene.box(xx,y,z,.2,h,.2,"wood","fixtures",f["garden_id"])
            scene.box(x,y+h*.6,z,w,.3,d,"wood_light","fixtures",f["garden_id"])
        elif kind=="well":
            for xx,zz,ww,dd in [(x,z,w,1),(x,z+d-1,w,1),(x,z,1,d),(x+w-1,z,1,d)]:
                scene.box(xx,y,zz,ww,h*.5,dd,"stone","fixtures",f["garden_id"])
            scene.box(x+1,y+.2,z+1,w-2,.2,d-2,"water","fixtures",f["garden_id"])
        else:
            scene.box(x,y,z,w,min(h,.5) if kind=="herb_beds" else h,d,"leaf" if kind=="herb_beds" else "wood_light","fixtures",f["garden_id"])
    scene.boxes.extend(landmark_scene(planner.env, planner.catalog).boxes)
    for stairs in planner.env.get("landmark_stairs",[]):
        x,y,z=stairs["start"];dx,dz=stairs["direction"];w=stairs["width"]
        for i in range(stairs["steps"]):
            scene.box(x+dx*i-w/2,y,z+dz*i-.5,w,(i+1)*stairs["rise"],1,"stone_light","landmark","landmark")
    for a,b in zip(data["wall"]["points"],data["wall"]["points"][1:]):
        n=max(abs(b[0]-a[0]),abs(b[1]-a[1]))
        for i in range(n):
            x=round(a[0]+(b[0]-a[0])*i/n);z=round(a[1]+(b[1]-a[1])*i/n)
            if any(x0<=x<=x1 and z0<=z<=z1 for x0,z0,x1,z1 in (g["bounds"] for g in data["wall"]["gates"])):continue
            scene.box(x,model.ground(x,z)+1,z,1,planner.spec["rules"]["wall_context_height"],1,"slate","walls","wall")
    return {"boxes":scene.boxes,"meshes":scene.meshes,"materials":materials,
            "terrain":model.terrain}


def html(data, scene):
    scene=copy.copy(scene); boxes=scene.pop("boxes")
    mats=list(scene["materials"]);layers=list(dict.fromkeys(b["layer"] for b in boxes));tags=list(dict.fromkeys(b["tag"] for b in boxes))
    mi,li,ti=({v:i for i,v in enumerate(a)} for a in (mats,layers,tags))
    packed=bytearray()
    for component in range(6):
        previous=0
        for b in boxes:
            value=round((b["p"]+b["s"])[component]*1000)
            packed.extend(struct.pack("<i",value-previous));previous=value
    packed.extend(bytes(mi[b["m"]] for b in boxes));packed.extend(bytes(li[b["layer"]] for b in boxes))
    packed.extend(struct.pack("<"+"H"*len(boxes),*[ti[b["tag"]] for b in boxes]))
    scene["packed"]={"count":len(boxes),"materials":mats,"layers":layers,"tags":tags,"data":base64.b64encode(packed).decode()}
    scene["meshes"]=[m for m in scene["meshes"] if m["tag"] not in {"heightfield","heightfield_sides"}]
    visual_data={k:v for k,v in data.items() if k!="source_hashes"}
    payload={"plan":visual_data,"scene":scene,"three":(ROOT/"tools/web/three.min.js").read_text(),
             "orbit":(ROOT/"tools/web/OrbitControls.js").read_text(),"viewer":(HERE/"viewer.js").read_text()}
    packed=base64.b64encode(gzip.compress(json.dumps(payload,ensure_ascii=False,separators=(",",":")).encode(),mtime=0,compresslevel=9)).decode()
    foot=('正式 authored 蓝图：原尺寸建筑与府邸、庭院、道路、城防均来自同一次实际方块构造；三维展开既有府邸构件。默认世界生成与 NPC 登记尚未接入；Minecraft 运行与玩家接受未验证。'
          if data.get('relayout') else '蓝图与三维读取同一组编译数据。建筑沿用现有生成器；庭院、门洞和设施为本次方案。城墙在三维中仅作边界体量。尚未接入正式世界生成，游戏运行与玩家接受未验证。')
    return ((HERE/"template.html").read_text().replace("__CITY_TITLE__", escape(data["name"])).replace('__FOOT__',escape(foot))
            .replace("__PAYLOAD__",packed))


def generate(source=DEFAULT, out=None):
    source=Path(source).resolve()
    spec=read(source); paths={k:ROOT/v for k,v in spec["sources"].items()}
    planner=make_planner(spec,read(paths["city"]),read(paths["environment"]),read(paths["landmarks"]))
    data=planner.compile()
    if out is None:return planner,data
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    sources=[Path(source),*paths.values(),ROOT/"blueprints/province_capitals/regional/v2/building_families.json",
             ROOT/"tools/province_capitals/environment_design/geometry.py",ROOT/"tools/web/three.min.js",ROOT/"tools/web/OrbitControls.js"]
    for folder in [HERE,ROOT/"tools/province_capitals/regional/v2",ROOT/"tools/buildgen"]:
        sources.extend(p for p in folder.rglob("*") if p.suffix in {".py",".json",".js",".html"})
    sources.extend(ROOT/name for name in data.get("source_geometry",{}).get("hashes",{}))
    identity={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(sources))}
    data["source_hashes"]=identity;data["source_fingerprint"]=digest(identity)
    jobs={k:data[k] for k in ("source_fingerprint","paths","doors","public_passages","earthwork","fixtures","wall","landmark","landmark_paths","retained_environment")}
    jobs.update(terrain_source=spec["sources"]["city"],building_jobs=[p["building"] for p in data["plots"]],road_jobs=data["roads"],
                scope="独立蓝图施工清单；原生结构导出读取同一 authored 输入，尚未接入默认世界生成")
    jobs.update({k:data[k] for k in ("road_elevation_changes","road_adjustments","entry_doors","native_material_changes","atomic_placements") if k in data})
    for name,value in [("plan.json",data),("checks.json",data["checks"]),("construction.json",jobs)]:
        (out/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+"\n")
    scene=preview_scene(planner,data)
    (out/"index.html").write_text(html(data,scene))
    print(json.dumps({"output":str(out),**data["checks"]},ensure_ascii=False),flush=True)
    return planner,data


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input",type=Path,default=DEFAULT)
    parser.add_argument("--out",type=Path,default=ROOT/"out/preview/lingyue-blueprint-plan-20261030")
    args=parser.parse_args();_,data=generate(args.input,args.out)
    return 0 if data["checks"]["status"]=="pass" else 1


if __name__=="__main__":
    raise SystemExit(main())
