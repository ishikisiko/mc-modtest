"""独立 v2 authored 城市：高度场、真实建筑、短距入口和稀疏施工。"""
from __future__ import annotations

from collections import Counter, deque
import copy
import math

from .common import (AIR, CITY_INPUT, DIR, FACING, LIBRARY, ROOT, Construction,
                     lerp, line, point, props, read_json)
from .buildings import Building
from buildgen.groups import get_group
from buildgen.modset import load_modset
from buildgen.ops import validate_style_vocabulary
from buildgen.style import Style, _filter_material_slots


def load_city(region):
    path = CITY_INPUT / (region + ".json")
    city = read_json(path)
    expected = (region, "capital_" + region, "province_capital_" + region + "_v2")
    if tuple(city[k] for k in ("region_id", "city_id", "group_id")) != expected:
        raise ValueError(f"城市、州域和 group 身份不一致：{path}")
    return city


class Capital:
    def __init__(self, city):
        self.city = copy.deepcopy(city)
        self.width, self.height, self.depth = city["dimensions"]
        if self.width % 16 or self.depth % 16 or not 16 <= self.width <= 2048 or not 16 <= self.depth <= 2048 or not 8 <= self.height <= 320:
            raise ValueError("城市尺寸须保持16格对齐及既有消费者边界")
        self.group = get_group(city["group_id"])
        self.modset = load_modset(city.get("modset", "vanilla"))
        data = read_json(ROOT / "tools/buildgen/styles" / (self.group.style_id + ".json"))
        data["material_slots"] = _filter_material_slots(self.group.style_id, data["material_slots"], set(self.modset.namespaces))
        self.style = Style(data)
        validate_style_vocabulary(self.style)
        self.library = read_json(LIBRARY)
        self.geometry = {**self.library["construction"], **city["construction"]}
        self.terrain = [max(0, round(self.natural_air(x, z)) - 1) for z in range(self.depth) for x in range(self.width)]
        self.apply_patches()
        self.construction = Construction(city["dimensions"])
        self.buildings = []; self.places = []; self.fixtures = []
        self.entries = copy.deepcopy(city.get("external_entries", []))
        self.roads = copy.deepcopy(city["roads"])
        self.road_columns = {}; self.road_goals = {}; self.access = []
        self.court_columns = {}
        self.occupied = {}; self.footprints = {}
        self.diagnostics = {"access_failures": [], "geometry_conflicts": [], "skipped_trees": [], "courtyards": []}
        self.make_road_columns()

    def material(self, slot):
        return self.style.primary(slot)

    def ground(self, x, z):
        return self.terrain[z * self.width + x]

    def set_ground(self, x, z, y):
        if not 0 <= x < self.width or not 0 <= z < self.depth or not 0 <= y < self.height:
            raise ValueError(f"地形越界 {(x,y,z)}")
        self.terrain[z * self.width + x] = y

    def natural_air(self, x, z):
        c = self.city["terrain"]; kind = c["kind"]
        if kind in ("flat", "flat_plain"):
            return c["ground_air_y"]
        if kind == "coastal_heightfield":
            delta = lerp(c["shore_x_by_z"], z) - x
            t = max(0, min(1, (delta + 4) / 6))
            return c["bed_y"] + t * (c["land_y"] - c["bed_y"])
        if kind == "connected_mountain_heightfield":
            h = lerp(c["shelves"], z)
            h += max(0, (28 - x) / 28, (x - (self.width - 25)) / 25) * (8 + 3 * math.sin(z / 29))
            h += math.sin(x / 25 + z / 37) * .7 + math.cos(z / 21) * .5
            r = c.get("ravine")
            if r:
                z0, z1 = r["z_range"]
                if z0 <= z <= z1:
                    influence = max(0, 1 - abs(x - r["center_x"] - 1.1 * math.sin(z / 12)) / r["half_width"])
                    h -= r["depth"] * influence * max(0, min(1, (z - z0) / 6, (z1 - z) / 6))
            return max(2, h)
        if kind == "oasis_basin":
            cx, cz = c["center"]; rx, rz = c["radius"]
            d = math.hypot((x - cx) / rx, (z - cz) / rz)
            if d < 1:
                return c["bed_y"] + 1 + (c["ground_air_y"] - c["bed_y"] - 1) * max(0, (d - .72) / .28)
            edge = max(0, (c["dune_margin"] - min(x, z, self.width - x - 1, self.depth - z - 1)) / c["dune_margin"])
            return c["ground_air_y"] + edge * c["dune_height"] * (.7 + .3 * math.cos(z / 24))
        if kind == "wooded_stream_valley":
            delta = abs(x - lerp(c["stream_x_by_z"], z))
            t = max(0, min(1, (delta - c["half_width"]) / c["bank_width"]))
            return c["bed_y"] + 1 + t * (c["ground_air_y"] - c["bed_y"] - 1)
        if kind == "sheltered_snowplain":
            edge = max(0, (18 - min(x, z, self.width - x - 1, self.depth - z - 1)) / 18)
            return c["ground_air_y"] + c["edge_rise"] * edge
        if kind == "long_valley_gate":
            edge = max(0, (c["cliff_margin"] - min(x, self.width - x - 1)) / c["cliff_margin"])
            return lerp(c["shelves"], z) + c["cliff_height"] * edge
        if kind == "connected_supported_rift_banks":
            center = lerp(c["rift_x_by_z"], z); delta = abs(x - center)
            bank_air = c["west_air_y"] if x < center else c["east_air_y"]
            t = max(0, min(1, (delta - c["half_width"]) / c["bank_width"]))
            return c["bed_y"] + 1 + t * (bank_air - c["bed_y"] - 1)
        raise ValueError(f"未知 authored 地形 {kind}")

    def apply_patches(self):
        for patch in self.city.get("terrain_patches", []):
            x0, z0, x1, z1 = patch["bounds"]; blend = patch.get("blend", 0)
            for z in range(max(0, z0 - blend), min(self.depth - 1, z1 + blend) + 1):
                for x in range(max(0, x0 - blend), min(self.width - 1, x1 + blend) + 1):
                    distance = max(x0 - x, x - x1, z0 - z, z - z1, 0)
                    t = max(0, 1 - distance / (blend + 1))
                    top = round(self.ground(x, z) * (1 - t) + (patch["air_y"] - 1) * t)
                    self.set_ground(x, z, top)

    def make_road_columns(self):
        seen = set()
        for road in self.roads:
            if road["id"] in seen or len(road["points"]) < 2:
                raise ValueError(f'道路重复或缺连接点 {road["id"]}')
            seen.add(road["id"])
            goals = self.road_goals.setdefault(road["id"], {})
            width = int(road["width"])
            for a, b in zip(road["points"], road["points"][1:]):
                samples = line(a, b)
                if abs(a[1] - b[1]) > len(samples) - 1:
                    raise ValueError(f'道路坡度超过逐格楼梯 {road["id"]}')
                dx, dz = b[0] - a[0], b[2] - a[2]
                major_x = abs(dx) >= abs(dz)
                up = ("east" if dx > 0 else "west") if major_x else ("south" if dz > 0 else "north")
                if b[1] < a[1]:
                    up = tuple(FACING)[(FACING[up] + 2) % 4]
                for step, (x, y, z) in enumerate(samples):
                    previous_y = samples[max(0, step - 1)][1]
                    next_y = samples[min(len(samples) - 1, step + 1)][1]
                    stair = previous_y != y or next_y != y
                    for cross in range(-(width // 2), -(width // 2) + width):
                        xx, zz = (x, z + cross) if major_x else (x + cross, z)
                        if not 0 <= xx < self.width or not 0 <= zz < self.depth:
                            continue
                        goals[xx, zz] = y
                        self.road_columns[xx, zz] = (y, road["kind"], stair, up, road["id"], cross, step)

    def water(self):
        top = self.city.get("water_top_y", self.city["terrain"].get("water_top_y"))
        if top is None:
            return
        for z in range(self.depth):
            for x in range(self.width):
                bed = self.ground(x, z)
                if bed < top:
                    self.construction.cell((x, bed, z), self.material("RIVER_BED"), "watercourse", "riverbed")
                    self.construction.box((x, bed + 1, z), (x, top, z), self.material("WATER"), "watercourse", "water")

    def roads_and_bridges(self):
        for (x, z), (y, kind, stairs, up, rid, cross, step) in self.road_columns.items():
            raised = kind in ("bridge", "boardwalk", "pier")
            material = props(self.material("PATH_STAIRS"), facing=up, half="bottom", shape="straight", waterlogged=False) if stairs else self.material("GROUND_PATH")
            if not raised:
                self.set_ground(x, z, y - 2)
            ground = self.ground(x, z)
            if ground + 1 <= y - 2 and (not raised or step % self.geometry["stilt_support_spacing"] == 0):
                self.construction.box((x, ground + 1, z), (x, y - 2, z), self.material("FRAME_WOOD") if raised else self.material("BASE_STONE"), rid, "support")
            self.construction.cell((x, y - 1, z), material, rid, "road")
            self.construction.box((x, y, z), (x, y + self.geometry["headroom"] - 1, z), AIR, rid, "clearance")

    def prepare_building(self, source):
        src = copy.deepcopy(source)
        src["roof"] = src.get("roof", self.city.get("landmark_roof") if src["role"] == "landmark" else self.city.get("roof")) or self.style.allowed_roof_types[0]
        if src["family"] not in self.group.archetype_roster:
            raise ValueError(f'建筑族未在 group 注册 {src["family"]}')
        if src["road_id"] not in self.road_goals:
            raise ValueError(f'{src["id"]} 引用不存在道路 {src["road_id"]}')
        plan = Building(src, self.style, self.city["construction"], self.library).build()
        if self.city.get("roof_snow"):
            highest = {}
            for (x, y, z), (state, _, layer) in plan.cells.items():
                if state != AIR and layer == "roof" and y > highest.get((x, z), (-999, ""))[0]:
                    highest[x, z] = y, state
            for (x, z), (y, state) in highest.items():
                # 雪只落在实际完整顶面，避免在下半楼梯/半砖上悬空。
                if "_stairs" not in state and "_slab" not in state and plan.clear_at(x, y + 1, z):
                    plan.set((x, y + 1, z), self.material("SNOW"), "roof")
        footprint = {point(src, x, 0, z)[::2] for x in range(plan.w) for z in range(plan.d)}
        # 所有建筑共享同一锚点；重叠只诊断，绝不默默裁去别人的地块。
        for key in footprint:
            if key in self.occupied:
                self.diagnostics["geometry_conflicts"].append({"building": src["id"], "other": self.occupied[key], "column": key})
            self.occupied[key] = src["id"]
        self.footprints[src["id"]] = footprint
        return plan

    def foundation(self, plan, emit=True):
        src = plan.source; floor_y = src["position"][1] - 1
        stilt = self.library["families"][src["family"]].get("stilt", False)
        for z in range(plan.d):
            for x in range(plan.w):
                xx, _, zz = point(src, x, 0, z)
                ground = self.ground(xx, zz)
                if ground >= floor_y:
                    self.set_ground(xx, zz, floor_y - 1)
                    ground = floor_y - 1
                if stilt:
                    support = x % self.geometry["stilt_support_spacing"] == 0 and z % self.geometry["stilt_support_spacing"] == 0
                    support |= x in (0, plan.w - 1) and z in (0, plan.d - 1)
                    state = self.material("FRAME_WOOD")
                else:
                    support = True; state = self.material("BASE_STONE")
                if emit and support and ground + 1 <= floor_y - 1:
                    self.construction.box((xx, ground + 1, zz), (xx, floor_y - 1, zz), state, src["id"], "support")

    def connect(self, b):
        start = (b["entrance"][0], b["entrance"][2]); start_y = b["entrance"][1]
        goals = self.road_goals[b["road_id"]]
        maximum = self.geometry.get("max_access_length", self.geometry["access_max_distance"])
        queue = deque([start]); previous = {start: None}; distance = {start: 0}; found = None
        while queue and len(previous) <= self.geometry["access_max_nodes"]:
            at = queue.popleft(); steps = distance[at]
            if at in goals and abs(goals[at] - start_y) <= steps:
                found = at; break
            if at == start and at in goals and goals[at] == start_y:
                found = at; break
            if steps >= maximum:
                continue
            for dx, dz in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                nxt = at[0] + dx, at[1] + dz
                if nxt in previous or not 0 <= nxt[0] < self.width or not 0 <= nxt[1] < self.depth:
                    continue
                if nxt in self.occupied and nxt != start:
                    # 城门通道和本院前场可达自己的街路；实体墙仍不可穿过。
                    if self.occupied[nxt] != b["id"] or any(self.construction.at((nxt[0], start_y + yy, nxt[1])) not in (AIR, None) for yy in (0, 1)):
                        continue
                walk_y = self.court_columns.get(nxt, self.road_columns.get(nxt, (start_y,))[0])
                record = self.construction.record_at((nxt[0], walk_y, nxt[1]))
                if record and record[0] != AIR and record[2] in ("wall", "support", "interior", "frame", "building", "door", "tree"):
                    continue
                # 只在眼前有限地块走；不跨河床或把长路径变成新的主干道。
                if self.ground(*nxt) < min(start_y, goals.get(nxt, start_y)) - 5 and nxt not in goals:
                    continue
                previous[nxt] = at; distance[nxt] = steps + 1; queue.append(nxt)
        if found is None:
            self.diagnostics["access_failures"].append({"building_id": b["id"], "entrance": b["entrance"], "road_id": b["road_id"], "maximum": maximum})
            return
        path = []; at = found
        while at is not None:
            path.append(at); at = previous[at]
        path.reverse(); end_y = goals[found]
        fixed = {0: start_y, len(path) - 1: end_y}
        for i, p in enumerate(path):
            if p in self.road_columns:
                fixed[i] = self.road_columns[p][0]
            elif p in self.court_columns:
                fixed[i] = self.court_columns[p]
        heights = [start_y] * len(path)
        ordered = sorted(fixed.items())
        for (a, ya), (c, yc) in zip(ordered, ordered[1:]):
            if abs(yc - ya) > c - a:
                self.diagnostics["access_failures"].append({"building_id": b["id"], "reason": "既有街阶/前坪与入口间距离不足逐格升降", "road_id": b["road_id"]})
                return
            for i in range(a, c + 1):
                heights[i] = round(ya + (yc - ya) * (i - a) / (c - a))
        if len(path) == 1:
            heights[0] = start_y
        points = [[x, heights[i], z] for i, (x, z) in enumerate(path)]
        rid = b["id"] + "_approach"
        for i, (x, y, z) in enumerate(points):
            if i == 0:
                continue
            if (x, z) in self.footprints[b["id"]] or (x, z) in self.road_columns or (x, z) in self.court_columns:
                # 本楼已有同源地板，尤其关楼贯通道，不重复清空内部。
                continue
            dy = y - points[i - 1][1]
            state = self.material("GROUND_PATH")
            if dy:
                dx, dz = x - points[i - 1][0], z - points[i - 1][2]
                up = next(name for name, vector in DIR.items() if vector == (dx, dz))
                if dy < 0:
                    up = tuple(FACING)[(FACING[up] + 2) % 4]
                state = props(self.material("PATH_STAIRS"), facing=up, half="bottom", shape="straight", waterlogged=False)
            ground = self.ground(x, z)
            if ground >= y - 1:
                self.set_ground(x, z, y - 2); ground = y - 2
            if ground + 1 <= y - 2:
                self.construction.box((x, ground + 1, z), (x, y - 2, z), self.material("BASE_STONE"), rid, "support")
            self.construction.cell((x, y - 1, z), state, rid, "road")
            self.construction.box((x, y, z), (x, y + 2, z), AIR, rid, "clearance")
        self.access.append({"building_id": b["id"], "road_id": b["road_id"], "length": len(points) - 1, "points": points})

    def edges_and_trees(self):
        approaches = {(p[0], p[2]) for route in self.access for p in route["points"]}
        for i, poly in enumerate(self.city.get("walls", [])):
            points = poly.get("points", []) if isinstance(poly, dict) else poly
            for a, b in zip(points, points[1:]):
                for x, y, z in line(a, b):
                    if not 0 <= x < self.width or not 0 <= z < self.depth or (x, z) in self.road_columns or (x, z) in self.occupied:
                        continue
                    bottom = self.ground(x, z) + 1
                    self.construction.box((x, bottom, z), (x, bottom + self.geometry["court_wall_height"], z), self.material("BASE_STONE"), f"wall_{i}", "wall")
        for i, tree in enumerate(self.city.get("trees", [])):
            x, z = tree[:2] if isinstance(tree, list) else tree["position"][::2]
            if any((xx, zz) in self.occupied or (xx, zz) in self.road_columns or (xx, zz) in approaches for xx in range(x - 2, x + 3) for zz in range(z - 2, z + 3)) or x < 2 or z < 2 or x + 2 >= self.width or z + 2 >= self.depth:
                self.diagnostics["skipped_trees"].append(i); continue
            y = self.ground(x, z) + 1
            self.construction.box((x, y, z), (x, y + 4, z), self.material("FRAME_WOOD"), f"tree_{i}", "tree")
            self.construction.box((x - 2, y + 3, z - 2), (x + 2, y + 6, z + 2), self.material("LEAF"), f"tree_{i}", "tree")

    def courtyards(self):
        if not hasattr(self, "court_columns"):
            self.court_columns = {}
        for i, court in enumerate(self.city.get("courtyards", [])):
            cx, y, cz = court["center"]; w, d = court["size"]
            x0, z0 = cx - w // 2, cz - d // 2; x1, z1 = x0 + w - 1, z0 + d - 1
            tag = court.get("id", f"court_{i}")
            kind = court.get("kind", self.geometry["default_court_kind"])
            if kind not in self.geometry["court_material_slots"]:
                raise ValueError(f"未知前坪 kind {kind}: {tag}")
            slot = self.geometry["court_material_slots"][kind]
            material = self.material(slot) if self.style.material_slots.get(slot) else self.material("GROUND_PATH")
            info = {"id": tag, "kind": kind, "actual_size": [w, d], "deck_columns": 0, "pier_columns": 0, "preserved_road_columns": 0, "preserved_building_columns": 0}
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    if (x, z) in self.occupied:
                        info["preserved_building_columns"] += 1
                        continue
                    if (x, z) in self.road_columns:
                        # 作者街阶/桥自身有精确标高；前坪不能将它压回单一Y。
                        info["preserved_road_columns"] += 1
                        continue
                    if not 0 <= x < self.width or not 0 <= z < self.depth:
                        raise ValueError(f"共用前场越界 {tag}")
                    ground = self.ground(x, z)
                    raised = kind == "quay" or y - 2 - ground > self.geometry["court_max_solid_fill"]
                    if ground >= y - 1:
                        self.set_ground(x, z, y - 2); ground = y - 2
                    pier = (x - x0) % self.geometry["stilt_support_spacing"] == 0 and (z - z0) % self.geometry["stilt_support_spacing"] == 0
                    if ground + 1 <= y - 2 and (not raised or pier):
                        self.construction.box((x, ground + 1, z), (x, y - 2, z), self.material("FRAME_WOOD") if raised else self.material("BASE_STONE"), tag, "support")
                        info["pier_columns"] += int(raised)
                    self.construction.cell((x, y - 1, z), material, tag, "road")
                    self.construction.box((x, y, z), (x, y + 2, z), AIR, tag, "clearance")
                    self.court_columns[x, z] = y
                    info["deck_columns"] += 1
            if court.get("shade"):
                corners = [(x, z) for x in (x0 + 1, x1 - 1) for z in (z0 + 1, z1 - 1)]
                safe_corners = all((x, z) not in self.occupied and (x, z) not in self.road_columns for x, z in corners)
                safe_span = not any((x, z) in self.occupied or self.road_columns.get((x, z), (-999,))[0] + 2 > y + 4 for x in range(x0 + 1, x1) for z in range(z0 + 1, z1))
                # 一张连续遮阳顶，有真实支柱；不堆叠拼板制造轮廓。
                if safe_corners and safe_span:
                    for x, z in corners:
                        self.construction.box((x, y, z), (x, y + 3, z), self.material("FRAME_WOOD"), tag, "support")
                    self.construction.box((x0 + 1, y + 4, z0 + 1), (x1 - 1, y + 4, z1 - 1), self.material("SHADE"), tag, "roof")
                    info["shade"] = "built"
                else:
                    info["shade"] = "blocked_by_authored_road_or_building"
            if court.get("wind_wall"):
                for x in (x0, x1):
                    for z in range(z0, z1 + 1):
                        if abs(z - cz) <= 1 or (x, z) in self.road_columns or (x, z) in self.occupied:
                            continue
                        self.construction.box((x, y, z), (x, y + self.geometry["court_wall_height"] - 1, z), self.material("BASE_STONE"), tag, "wall")
            self.diagnostics["courtyards"].append(info)

    def validate_places(self):
        ids = [p["id"] for p in self.places]
        required = {"inn_work", "inn_rest", "inn_social", "guest_west", "herbalist_work", "herbalist_rest", "smith_work", "smith_rest"}
        if len(ids) > 192 or len(ids) != len(set(ids)) or not required <= set(ids):
            raise ValueError(f"life 岗位超限/重复/缺主服务: count={len(ids)}, missing={required-set(ids)}")
        if not {"entry", "market", "cultivate"} <= {p["kind"] for p in self.places}:
            raise ValueError("life 缺入口/市场/修炼点")
        for place in self.places:
            x, y, z = place["pos"]
            if not 0 <= x < self.width or not 1 <= y < self.height - 1 or not 0 <= z < self.depth:
                raise ValueError(f'生活位置越界 {place["id"]}')
            if self.construction.at((x, y, z)) not in (AIR, None) or self.construction.at((x, y + 1, z)) not in (AIR, None):
                raise ValueError(f'最终施工覆盖站立空气 {place["id"]}: {place["pos"]}')
            if place["block"] and (self.construction.at(tuple(place["block_pos"])) or "").split("[", 1)[0] != place["block"]:
                raise ValueError(f'最终 fixture 与生活位置声明不一致 {place["id"]}')
        for entry in self.entries:
            x, y, z = entry["position"]
            below = self.construction.at((x, y - 1, z))
            support = below is not None and below != AIR or below is None and self.ground(x, z) == y - 1
            clear = all(self.construction.at((x, y + dy, z)) in (AIR, None) for dy in (0, 1))
            if not support or not clear:
                raise ValueError(f'实际入口缺地板或两格净空 {entry["id"]}: {entry["position"]}')

    def construct(self):
        plans = [self.prepare_building(b) for b in self.city["buildings"]]
        if self.diagnostics["geometry_conflicts"]:
            pairs = Counter(tuple(sorted((c["building"], c["other"]))) for c in self.diagnostics["geometry_conflicts"])
            raise ValueError("author地块重叠：" + str(dict(pairs)))
        # 地形先让路，水深与最终道路/建筑基础使用同一地表。
        for plan in plans:
            self.foundation(plan, emit=False)
        self.water(); self.roads_and_bridges(); self.courtyards()
        for plan in plans:
            # 水先写、柱脚后写，保留河床而不让水覆盖架空屋的水下支柱。
            self.foundation(plan)
            plan.emit(self.construction)
            b = plan.summary(); self.buildings.append(b)
            self.places.extend(plan.places); self.fixtures.extend(plan.fixtures)
            if b["role"] == "gate":
                self.entries.append({"id": b["id"], "position": b["entrance"], "facing": b["facing"], "building_id": b["id"]})
        for b in self.buildings:
            self.connect(b)
        self.edges_and_trees(); self.validate_places()
        if self.diagnostics["access_failures"]:
            raise ValueError("短距入口连接失败：" + str(self.diagnostics["access_failures"]))
        if self.diagnostics["geometry_conflicts"]:
            raise ValueError("author地块重叠：" + str(self.diagnostics["geometry_conflicts"][:12]))
        return self
