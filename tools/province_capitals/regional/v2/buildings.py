"""显式翼体 -> 连续墙壳/楼板/屋盖/楼梯 -> 同源安全生活设施。"""
from __future__ import annotations

from collections import deque
import hashlib
import json
import random

from .common import AIR, DIR, FACING, LIBRARY, point, props, read_json, rotated_state
from buildgen.grid import BlockGrid
from buildgen.massing import Node
from buildgen.ops import roof_handler


def adjacent(p):
    x, z = p
    return ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))


def path_between(start, goal, cells):
    queue = deque([start]); previous = {start: None}
    while queue:
        at = queue.popleft()
        if at == goal:
            path = []
            while at is not None:
                path.append(at); at = previous[at]
            return list(reversed(path))
        for nxt in adjacent(at):
            if nxt in cells and nxt not in previous:
                previous[nxt] = at; queue.append(nxt)
    return []


class Building:
    """只保存一栋建筑的局部格；城市施工保持按 tile 的操作索引。"""
    def __init__(self, source, style, construction=None, library=None):
        self.source = json.loads(json.dumps(source))
        self.style = style
        self.library = library or read_json(LIBRARY)
        self.family = self.library["families"][source["family"]]
        if type(source["variant"]) is not int or not 0 <= source["variant"] <= 3:
            raise ValueError(f'{source["id"]}: variant 必须为 0..3')
        self.variant = self.family["variants"][source["variant"]]
        self.w, self.d = source["size"]
        if any(type(n) is not int for n in source["size"] + source["position"]):
            raise ValueError(f'{source["id"]}: size/position 必须保留整数真值')
        if self.w < 7 or self.d < 7 or source["stories"] < 1:
            raise ValueError(f'{source["id"]}: 建筑不足以容纳安全内室')
        self.geometry = {**self.library["construction"], **(construction or {}), **source.get("construction", {})}
        self.storey = self.geometry["storey_height"]
        self.wall_height = self.geometry["wall_height"]
        self.wall_thickness = self.geometry.get("wall_thickness", 1)
        if type(self.wall_thickness) is not int or self.wall_thickness not in (1, 2):
            raise ValueError("v2 外墙厚度只支持1或2格，不能静默忽略")
        if self.storey < self.wall_height + 1 or self.wall_height < 3:
            raise ValueError("楼层高度必须容纳连续墙壳和至少两格净空")
        self.cells = {}
        self.rooms = []
        self.coverage = {}
        self.interiors = {}
        self.reserved = set()
        self.fixtures = []
        self.places = []
        self.stairs = []
        self.doors = []
        self.guest_rooms = []
        self.south_door = None
        self.make_massing()

    def mat(self, slot, suffix=None):
        return self.style.slot_entry(slot, suffix) if suffix else self.style.primary(slot)

    def set(self, p, state, layer="building"):
        x, y, z = p
        if not (0 <= x < self.w and 0 <= z < self.d):
            raise ValueError(f'{self.source["id"]}: 局部几何越出实际size: {p}')
        self.cells[p] = (state, self.source["id"], layer)

    def box(self, a, b, state, layer="building"):
        for y in range(a[1], b[1] + 1):
            for z in range(a[2], b[2] + 1):
                for x in range(a[0], b[0] + 1):
                    self.set((x, y, z), state, layer)

    def clear(self, p):
        self.set(p, AIR, "clearance")

    def make_massing(self):
        for i, spec in enumerate(self.variant["rooms"]):
            xa, za, xb, zb = spec["bounds"]
            x0, x1 = [1 + round(n * (self.w - 3) / 1000) for n in (xa, xb)]
            z0, z1 = [1 + round(n * (self.d - 3) / 1000) for n in (za, zb)]
            # 小地块的翼体仍有至少三格内宽；只向本 lot 内扩展。
            if x1 - x0 < 4:
                x0 = max(1, min(x0, self.w - 6)); x1 = min(self.w - 2, max(x1, x0 + 4))
            if z1 - z0 < 4:
                z0 = max(1, min(z0, self.d - 6)); z1 = min(self.d - 2, max(z1, z0 + 4))
            levels = self.source["stories"] if spec["upper"] else 1
            room = {**spec, "id": f'wing_{i}', "bounds_cells": [x0, z0, x1, z1], "stories": levels}
            self.rooms.append(room)
            for level in range(levels):
                self.coverage.setdefault(level, set()).update((x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))
        for level, footprint in self.coverage.items():
            interior = footprint
            for _ in range(self.wall_thickness):
                interior = {p for p in interior if all(n in interior for n in adjacent(p))}
            self.interiors[level] = interior
            if not self.interiors[level]:
                raise ValueError(f'{self.source["id"]}: {level}层没有真实安全内部')

    def shell(self):
        floor = self.mat("FLOOR_WOOD")
        # 院落和檐廊是实际连续地坪，全部包含在 authored size 内。
        self.box((0, -1, 0), (self.w - 1, -1, self.d - 1), floor, "floor")
        for level, footprint in self.coverage.items():
            y = level * self.storey
            windows = []
            for x, z in footprint:
                self.set((x, y - 1, z), floor, "floor")
                inside = (x, z) in self.interiors[level]
                for yy in range(y, y + self.wall_height):
                    self.set((x, yy, z), AIR if inside else self.mat("WALL_MAIN"), "clearance" if inside else "building")
                if not inside:
                    n = sum(p in footprint for p in adjacent((x, z)))
                    if n <= 2:
                        for yy in range(y, y + self.wall_height):
                            self.set((x, yy, z), self.mat("FRAME_WOOD"), "frame")
                    elif n == 3 and (x + z) % 3 == 1:
                        outward = next(p for p in adjacent((x, z)) if p not in footprint)
                        dx, dz = x - outward[0], z - outward[1]
                        ew = bool(dz)
                        window = self.mat("WINDOW").split("[", 1)[0]
                        if window.endswith("_pane"):
                            window = props(window, north=not ew, south=not ew, east=ew, west=ew, waterlogged=False)
                        for depth in range(self.wall_thickness):
                            for yy in (y + 1, y + 2):
                                windows.append(((x + dx * depth, yy, z + dz * depth), window))
            for pos, state in windows:
                self.set(pos, state, "window")
            # 顶梁闭合墙与屋盖间的一格，楼板不会随机断层。
            for x, z in footprint - self.interiors[level]:
                self.set((x, y + self.wall_height - 1, z), self.mat("FRAME_WOOD"), "frame")

    def roofs(self):
        roof_name = self.source.get("roof", self.geometry.get("roof", self.style.allowed_roof_types[0]))
        if roof_name not in self.style.allowed_roof_types:
            raise ValueError(f'{self.source["id"]}: style 未允许屋顶 {roof_name}')
        for room in self.rooms:
            x0, z0, x1, z1 = room["bounds_cells"]
            top = (room["stories"] - 1) * self.storey + self.wall_height
            grid = BlockGrid()
            node = Node(room["id"], "main_volume", (x0, 0, z0), (x1 - x0 + 1, top, z1 - z0 + 1),
                        meta={"foundation_h": 0, "wall_h": top,
                              "roof": {"ridge_axis": room["ridge_axis"], "overhang": self.geometry["roof_overhang"], "gable_overhang": 1}})
            roof_handler(roof_name)(grid, self.style, random.Random(0), node)
            for (x, y, z), cell in sorted(grid.iter_cells()):
                if not 0 <= x < self.w or not 0 <= z < self.d:
                    continue
                # 低翼屋顶不能进入高翼室内；同层十字屋盖保持真实交接。
                blocked = False
                for other in self.rooms:
                    a, b, c, d = other["bounds_cells"]
                    other_top = (other["stories"] - 1) * self.storey + self.wall_height
                    if other_top > top and a <= x <= c and b <= z <= d and y < other_top:
                        blocked = True; break
                if not blocked:
                    self.set((x, y, z), cell.state, "building" if "FACADE" in cell.tags else "roof")

    def clear_at(self, x, y, z):
        return self.cells.get((x, y, z), (AIR, "", ""))[0] == AIR

    def free(self, p):
        x, y, z = p
        support = self.cells.get((x, y - 1, z), (AIR, "", ""))[0]
        return support != AIR and self.clear_at(x, y, z) and self.clear_at(x, y + 1, z)

    def opening(self, x, z, facing="south", door=True, floor=0):
        for y in range(floor, floor + 3):
            self.clear((x, y, z))
        if door:
            wood = self.mat("FRAME_WOOD").split(":", 1)[1].split("[", 1)[0].replace("stripped_", "")
            for suffix in ("_log", "_wood", "_planks"):
                wood = wood.removesuffix(suffix)
            base = self.mat("DOOR") if self.style.material_slots.get("DOOR") else "minecraft:" + wood + "_door"
            for half, y in (("lower", floor), ("upper", floor + 1)):
                self.set((x, y, z), props(base, facing=facing, half=half, hinge="left", open=False, powered=False), "door")
        self.doors.append([x, floor, z])

    def entrances(self):
        footprint = self.coverage[0]
        thickness = self.wall_thickness
        candidates = [(x, z) for x, z in footprint
                      if (x, z - thickness) in self.interiors[0] and (x, z + 1) not in footprint
                      and (x - 1, z) in footprint and (x + 1, z) in footprint]
        if not candidates:
            raise ValueError(f'{self.source["id"]}: {thickness}格厚墙没有可贯通安全门洞')
        target = 1 + round(self.variant["entry_x"] * (self.w - 3) / 1000)
        x, z = min(candidates, key=lambda p: (abs(p[0] - target), -p[1]))
        self.south_door = (x, z)
        for depth in range(1, thickness):
            for y in range(3):
                self.clear((x, y, z - depth))
            self.reserved.add((x, 0, z - depth))
        self.opening(x, z, door=not self.variant.get("open_hall") and not self.variant.get("passage"))
        self.entrance = (x, 0, self.d - 1)
        for zz in range(z + 1, self.d):
            self.set((x, -1, zz), self.mat("GROUND_PATH"), "floor")
            for yy in range(3):
                self.clear((x, yy, zz))
            self.reserved.add((x, 0, zz))
        self.reserved.add((x, 0, z))
        self.reserved.add((x, 0, z - thickness))
        inside = self.interiors[0]
        start = (x, z - thickness)
        for room in self.rooms:
            a, b, c, d = room["bounds_cells"]
            targets = [(xx, zz) for xx, zz in inside if a < xx < c and b < zz < d]
            if targets:
                center = min(targets, key=lambda p: abs(p[0] - (a + c) / 2) + abs(p[1] - (b + d) / 2))
                path = path_between(start, center, inside)
                if not path:
                    raise ValueError(f'{self.source["id"]}: 翼体与主入口断开 {room["id"]}')
                self.reserved.update((xx, 0, zz) for xx, zz in path)
        if self.variant.get("partition"):
            middle = (self.w - 1) // 2
            cells = [(middle, zz) for xx, zz in inside if xx == middle]
            if cells:
                door_z = min(cells, key=lambda p: abs(p[1] - self.d // 2))[1]
                for _, zz in cells:
                    if zz == door_z:
                        continue
                    for yy in range(self.wall_height):
                        if (middle, 0, zz) not in self.reserved:
                            self.set((middle, yy, zz), self.mat("WALL_MAIN"))
        if self.variant.get("open_hall"):
            # 连续柱廊，保留背墙与后部储藏；开口由真实外墙推导。
            for xx, zz in footprint - inside:
                if (xx, zz - thickness) in inside and (xx, zz + 1) not in footprint and (xx + zz) % 4:
                    for depth in range(thickness):
                        for yy in range(3):
                            self.clear((xx, yy, zz - depth))
        if self.variant.get("passage"):
            # 城门真正贯通整座门楼；两侧楼梯与上层关楼仍独立保留。
            cx = self.w // 2
            for xx in range(cx - 1, cx + 2):
                for zz in range(self.d):
                    self.set((xx, -1, zz), self.mat("GROUND_PATH"), "floor")
                    for yy in range(3):
                        self.clear((xx, yy, zz))
                    self.reserved.add((xx, 0, zz))
            self.entrance = (cx, 0, self.d - 1)

    def staircase(self):
        for level in range(1, max(self.coverage) + 1):
            usable = self.interiors[level] & self.interiors[level - 1]
            # 3×3 回折楼梯井，细宅也能有真楼梯，最多五阶后接上层楼板。
            squares = [(x, z) for x, z in sorted(usable) if all((x + dx, z + dz) in usable for dx in range(3) for dz in range(3))]
            if squares:
                x, z = min(squares, key=lambda p: (sum((p[0] + dx, 0, p[1] + dz) in self.reserved for dx in range(3) for dz in range(3)), p))
                ring = [(x, z), (x + 1, z), (x + 2, z), (x + 2, z + 1), (x + 2, z + 2), (x + 1, z + 2), (x, z + 2), (x, z + 1)]
                offset = ((level - 1) * self.storey) % len(ring)
                walk = [ring[(offset + i) % len(ring)] for i in range(self.storey + 1)]
            else:
                # 双格厚墙的窄后楼可用真实直跑/折跑，仍需五阶加独立落地格。
                def search(path):
                    if len(path) == self.storey + 1:
                        return path
                    for nxt in adjacent(path[-1]):
                        if nxt in usable and nxt not in path:
                            result = search(path + [nxt])
                            if result:
                                return result
                    return None
                walk = next((result for start in sorted(usable) if (result := search([start]))), None)
                if walk is None:
                    raise ValueError(f'{self.source["id"]}: {self.wall_thickness}格厚墙侵蚀后{level}层不足五阶及落地格')
            low = (level - 1) * self.storey
            for step, (xx, zz) in enumerate(walk[:-1]):
                nx, nz = walk[step + 1]
                facing = next(name for name, vector in DIR.items() if vector == (nx - xx, nz - zz))
                yy = low + step
                for head in range(1, 4):
                    self.clear((xx, yy + head, zz))
                for fill in range(low, yy):
                    self.set((xx, fill, zz), self.mat("FLOOR_WOOD"), "stair_support")
                stair = props(self.mat("PATH_STAIRS"), facing=facing, half="bottom", shape="straight", waterlogged=False)
                self.set((xx, yy, zz), stair, "stairs")
                self.reserved.update({(xx, low, zz), (xx, level * self.storey, zz)})
            lx, lz = walk[-1]
            self.reserved.add((lx, level * self.storey, lz))
            self.stairs.append({"from_level": level - 1, "to_level": level,
                                "steps": [[xx, low + i, zz] for i, (xx, zz) in enumerate(walk[:-1])],
                                "landing": [lx, level * self.storey, lz]})

    def candidates(self, levels=None):
        levels = levels if levels is not None else sorted(self.interiors, reverse=True)
        for level in levels:
            y = level * self.storey
            cells = self.interiors[level]
            # 沿内墙优先，家具永远不使用外包围盒角落。
            for x, z in sorted(cells, key=lambda p: (sum(n in cells for n in adjacent(p)), p[1], p[0])):
                p = (x, y, z)
                if p not in self.reserved and self.free(p):
                    yield p

    def divide_guest_rooms(self):
        if self.source["role"] != "inn":
            return
        for room in self.rooms:
            if room["use"] != "bedroom":
                continue
            x0, z0, x1, z1 = room["bounds_cells"]
            axis_x = x1 - x0 >= z1 - z0
            for level in range(room["stories"]):
                y = level * self.storey
                lo, hi = (x0 + 1, x1 - 1) if axis_x else (z0 + 1, z1 - 1)
                stride = self.library["construction"]["guest_room_bay"]
                for split in range(lo + stride, hi - 2, stride):
                    wall = [(split, z) for z in range(z0 + 1, z1)] if axis_x else [(x, split) for x in range(x0 + 1, x1)]
                    if any((x, y, z) in self.reserved for x, z in wall):
                        continue
                    door = wall[-2] if len(wall) > 2 else wall[-1]
                    for x, z in wall:
                        for yy in range(y, y + self.wall_height):
                            self.set((x, yy, z), self.mat("WALL_MAIN"), "partition")
                    self.opening(*door, facing="east" if axis_x else "south", floor=y)
                    self.guest_rooms.append({"level": level, "partition": [[x, y, z] for x, z in wall], "door": [door[0], y, door[1]]})

    def place(self, pid, kind, stand, fixture=None, capacity=1):
        if not self.free(stand):
            raise ValueError(f'{self.source["id"]}: 岗位站立格非空气 {pid}: {stand}')
        block = ""; block_pos = list(point(self.source, *stand))
        if fixture is not None:
            block_pos = list(point(self.source, *fixture))
            block = self.cells[fixture][0].split("[", 1)[0]
        self.reserved.add(stand)
        self.places.append({"id": pid, "kind": kind, "pos": list(point(self.source, *stand)), "capacity": capacity,
                            "block": block, "block_pos": block_pos, "building_id": self.source["id"]})

    def fixture(self, fid, pos, state):
        if pos in self.reserved or not self.free(pos):
            raise ValueError(f'{self.source["id"]}: 家具穿墙或侵占通道 {fid}: {pos}')
        self.set(pos, state, "interior")
        self.fixtures.append({"id": self.source["id"] + ":" + fid, "building_id": self.source["id"],
                              "block": state.split("[", 1)[0], "block_pos": list(point(self.source, *pos))})
        return pos

    def stand_near(self, p, exclude=()):
        x, y, z = p
        inside = self.interiors[y // self.storey]
        candidates = [(xx, y, zz) for xx, zz in adjacent((x, z))
                      if (xx, zz) in inside and (xx, y, zz) not in exclude and self.free((xx, y, zz))]
        return min(candidates, key=lambda v: (v not in self.reserved, v)) if candidates else None

    def bed(self, fid, pid=None):
        base = self.mat("FURNITURE", "_bed").split("[", 1)[0]
        for foot in self.candidates():
            x, y, z = foot
            for facing, (dx, dz) in DIR.items():
                head = (x + dx, y, z + dz)
                if (head[0], head[2]) not in self.interiors[y // self.storey] or head in self.reserved or not self.free(head):
                    continue
                stand = self.stand_near(foot, (head,))
                if stand is None:
                    continue
                self.fixture(fid, foot, props(base, facing=facing, part="foot", occupied=False))
                self.fixture(fid + "_head", head, props(base, facing=facing, part="head", occupied=False))
                if pid:
                    self.place(pid, "rest", stand, foot)
                return foot
        raise ValueError(f'{self.source["id"]}: 没有完整双格床和相邻安全站位')

    def work(self, fid, state, pid=None, levels=(0,)):
        for pos in self.candidates(levels):
            stand = self.stand_near(pos)
            if stand:
                self.fixture(fid, pos, state)
                if pid:
                    self.place(pid, "work", stand, pos)
                return pos
        raise ValueError(f'{self.source["id"]}: 没有安全工作设施位置 {fid}')

    def social(self, pid, kind="social", capacity=2):
        pool = [(x, 0, z) for x, z in self.interiors[0] if self.free((x, 0, z))]
        if not pool:
            raise ValueError(f'{self.source["id"]}: 无安全公共站位 {pid}')
        p = min(pool, key=lambda p: (p in {tuple(self.local(q["pos"])) for q in self.places}, abs(p[0] - self.w / 2) + abs(p[2] - self.d / 2)))
        self.place(pid, kind, p, capacity=capacity)

    def local(self, world):
        # 仅用于小集合比较，与 common.point 使用同一整数锚点。
        from .common import rotate
        x, y, z = [a - b for a, b in zip(world, self.source["position"])]
        x, z = rotate(x, z, -FACING[self.source["facing"]])
        return x + self.w // 2, y, z + self.d // 2

    def furnish(self):
        b = self.source
        primary = b.get("primary_service", True)
        prefix = "" if primary else b["id"] + "_"
        role = b["role"]
        storage = props(self.mat("INTERIOR_STORAGE"), facing="south", open=False)
        if role in ("house", "inn", "apothecary", "smith"):
            rest = {"house": b["id"] + "_rest", "inn": prefix + "inn_rest", "apothecary": prefix + "herbalist_rest", "smith": prefix + "smith_rest"}[role]
            self.bed("resident_bed", rest)
        if role == "house":
            self.work("crafting", self.mat("INTERIOR_WORK", "crafting_table"))
            self.work("house_storage", storage)
            if b["family"] == "row_pair":
                self.bed("second_household_bed")
            if b["family"] == "shop_house":
                self.work("shop_counter", storage)
            self.social(b["id"] + "_social")
        elif role == "inn":
            self.bed("guest_west_bed", prefix + "guest_west")
            self.bed("guest_east_bed", prefix + "guest_east")
            self.work("counter", self.mat("FLOOR_WOOD"), prefix + "inn_work")
            self.work("kitchen", props(self.mat("INTERIOR_WORK", "furnace"), facing="south", lit=False))
            self.work("pantry", storage)
            self.social(prefix + "inn_social", capacity=4)
        elif role == "apothecary":
            self.work("brewing", props(self.mat("INTERIOR_CIVIC", "brewing_stand"), has_bottle_0=False, has_bottle_1=False, has_bottle_2=False), prefix + "herbalist_work")
            self.work("herb_shelf", self.mat("INTERIOR_CIVIC", "bookshelf"))
            self.work("cauldron", self.mat("INTERIOR_CIVIC", "cauldron"))
        elif role == "smith":
            self.work("anvil", props(self.mat("INTERIOR_WORK", ":anvil"), facing="north"), prefix + "smith_work")
            self.work("forge", props(self.mat("INTERIOR_WORK", "furnace"), facing="south", lit=False))
            self.work("smithing", self.mat("INTERIOR_WORK", "smithing_table"))
            self.work("fuel", storage)
        elif role == "market":
            for i in range(min(8, max(3, self.w // 4))):
                self.work(f"stall_{i}", storage, prefix + "market_work" if i == 0 else None)
            self.social(prefix + "market_social", "market", capacity=6)
        elif role == "guide":
            self.work("guide_lectern", props(self.mat("INTERIOR_CIVIC", "lectern"), facing="south", has_book=False, powered=False), prefix + "guide_work")
        elif role == "landmark":
            self.work("library", self.mat("INTERIOR_CIVIC", "bookshelf"))
            self.social(b["id"] + "_cultivation", "cultivate", capacity=4)
        elif role == "warehouse":
            for i in range(min(10, max(3, self.w // 3))):
                self.work(f"cargo_{i}", storage)
        elif role == "gate":
            # 通道与安全守望台共同属于同一门楼。
            self.place(b["id"] + "_entry", "entry", self.entrance, capacity=4)
        # 室内灯有真实楼板支撑，候选同样避开门/楼梯/岗位。
        for pos in self.candidates((0,)):
            self.fixture("lantern", pos, self.mat("LIGHTING")); break

    def build(self):
        self.shell(); self.roofs(); self.entrances(); self.staircase(); self.divide_guest_rooms(); self.furnish()
        for place in self.places:
            local = tuple(self.local(place["pos"]))
            if not self.free(local):
                raise ValueError(f'{self.source["id"]}: 后续家具破坏站位 {place["id"]}')
            x, y, z = local
            below = self.cells.get((x, y - 1, z), (AIR, "", ""))[0]
            if below == AIR:
                raise ValueError(f'{self.source["id"]}: 岗位缺实际地板 {place["id"]}')
        return self

    def summary(self):
        b = dict(self.source)
        corners = [point(b, x, y, z) for x in (0, self.w - 1) for y in (-1, max(p[1] for p in self.cells)) for z in (0, self.d - 1)]
        silhouette = [[*p, value[2]] for p, value in sorted(self.cells.items()) if value[0] != AIR and value[2] in ("building", "floor", "roof", "frame")]
        b.update(rotation_quarters=FACING[b["facing"]], source_size=list(b["size"]), source_position=list(b["position"]),
                 entrance=list(point(b, *self.entrance)), variant_name=self.variant["name"], volumes=self.rooms,
                 wall_thickness=self.wall_thickness,
                 bounds={"min": [min(p[i] for p in corners) for i in range(3)], "max": [max(p[i] for p in corners) for i in range(3)]},
                 geometry_sha256=hashlib.sha256(json.dumps(silhouette, separators=(",", ":")).encode()).hexdigest(),
                 guest_room_partitions=self.guest_rooms,
                 stairs=[{**s, "steps": [list(point(b, *p)) for p in s["steps"]], "landing": list(point(b, *s["landing"]))} for s in self.stairs],
                 door_positions=[list(point(b, *p)) for p in self.doors], fixture_count=len(self.fixtures),
                 note=f'{self.family["name"]} · {self.variant["name"]} · 实际{self.w}×{self.d}格 · 完整翼体、门窗屋盖和真实设施；原生游戏未验收。')
        return b

    def emit(self, construction):
        b = self.source
        q = FACING[b["facing"]]
        for pos, (state, tag, layer) in sorted(self.cells.items()):
            construction.cell(point(b, *pos), rotated_state(state, q), tag, layer)
