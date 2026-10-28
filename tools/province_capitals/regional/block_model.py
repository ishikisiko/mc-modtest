"""Authored capital plans -> integer construction operations, streamed by tile.

The terrain array owns earth/rock.  The operation index owns only construction,
water and deliberate clearance.  A full-city voxel dictionary is never made.
"""
from __future__ import annotations

from collections import defaultdict, deque
from dataclasses import dataclass
import json
import math
from pathlib import Path
import random
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from buildgen.groups import get_group
from buildgen.grid import BlockGrid
from buildgen.massing import Node
from buildgen.modset import load_modset
from buildgen.ops import roof_handler
from buildgen.orientation import orient_block
from buildgen.style import Style, _filter_material_slots

AIR = "minecraft:air"
FACING = {"south": 0, "west": 1, "north": 2, "east": 3}
DIRECTIONS = ["south", "west", "north", "east"]
ARCHETYPES = {"house": "cultivation_house", "inn": "cultivation_inn",
              "apothecary": "cultivation_shop", "smith": "cultivation_shop",
              "market": "cultivation_market", "guide": "pavilion",
              "landmark": "town_shrine", "gate": "bell_drum_tower",
              "warehouse": "cultivation_shop"}


def lerp(table, value):
    for (a, va), (b, vb) in zip(table, table[1:]):
        if a <= value <= b:
            return va + (vb - va) * (value - a) / (b - a)
    return table[0][1] if value < table[0][0] else table[-1][1]


def rotate(x, z, q):
    return ((x, z), (-z, x), (-x, -z), (z, -x))[q % 4]


def rotated_state(state, q):
    if not q or "[" not in state:
        return state
    name, text = state[:-1].split("[", 1)
    props = dict(item.split("=", 1) for item in text.split(","))
    if props.get("facing") in FACING:
        props["facing"] = DIRECTIONS[(FACING[props["facing"]] + q) % 4]
    if q % 2 and props.get("axis") in ("x", "z"):
        props["axis"] = "z" if props["axis"] == "x" else "x"
    # Directional glass/fences are authored with explicit joins, not inferred
    # from surrounding unloaded chunks.
    old = dict(props)
    for face in DIRECTIONS:
        if face in old:
            props[DIRECTIONS[(FACING[face] + q) % 4]] = old[face]
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def with_props(state, **props):
    name = state.split("[", 1)[0]
    return name + "[" + ",".join(f"{k}={str(v).lower()}" for k, v in sorted(props.items())) + "]"


def load_inputs():
    path = ROOT / "blueprints/province_capitals/regional/game-cities.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    result = []
    for config in data["cities"]:
        city = dict(config)
        if "layout_source" in config:
            source = json.loads((ROOT / config["layout_source"]).read_text(encoding="utf-8"))
            original = next(c for c in source["cities"] if c["region_id"] == config["region_id"])
            city = {**original, **config}
            houses = []
            for row_index, row in enumerate(original["house_rows"]):
                for x in row["xs"]:
                    n = len(houses) + 1
                    route = row["road_id"]
                    if city["region_id"] == "lingyue" and row_index in (2, 3) and x > 120:
                        route = "middle_east"
                    houses.append({"id": f"house_{n:03d}", "name": f"民居{n:02d}", "role": "house",
                                   "position": [x, row["y"], row["z"]], "size": [8 if n % 3 else 9, 8 if n % 4 else 9],
                                   "stories": 2 if n % 6 == 0 else 1,
                                   "facing": row["facing"], "road_id": route})
            city["houses"] = houses
        city["city_id"] = "capital_" + city["region_id"]
        city["name"] = city.get("name", city["region_id"])
        result.append(city)
    return data, result


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
        lo = tuple(max(0, min(a, b)) for a, b in zip(low, high))
        hi = tuple(min(limit - 1, max(a, b)) for a, b, limit in zip(low, high, self.size))
        if any(a > b for a, b in zip(lo, hi)):
            return
        op = Op(lo, hi, state, tag, layer)
        for tz in range(lo[2] // 16, hi[2] // 16 + 1):
            for tx in range(lo[0] // 16, hi[0] // 16 + 1):
                self.by_tile[(tx, tz)].append(op)
        self.operations += 1

    def cell(self, pos, state, tag="", layer="building"):
        self.box(pos, pos, state, tag, layer)

    def tile(self, tx, tz):
        result = {}
        for op in self.by_tile[(tx, tz)]:
            x0, y0, z0 = op.low
            x1, y1, z1 = op.high
            for y in range(y0, y1 + 1):
                for z in range(max(z0, tz * 16), min(z1, tz * 16 + 15) + 1):
                    for x in range(max(x0, tx * 16), min(x1, tx * 16 + 15) + 1):
                        result[(x % 16, y, z % 16)] = (op.state, op.tag, op.layer)
        return result


class Capital:
    def __init__(self, city, modset):
        self.city = city
        self.width, self.height, self.depth = city["dimensions"]
        self.group = get_group(city["group_id"])
        self.modset = load_modset(modset)
        style_path = ROOT / "tools/buildgen/styles" / (self.group.style_id + ".json")
        style_data = json.loads(style_path.read_text(encoding="utf-8"))
        style_data["material_slots"] = _filter_material_slots(self.group.style_id, style_data["material_slots"], set(self.modset.namespaces))
        self.style = Style(style_data)
        self.construction = Construction(city["dimensions"])
        self.buildings = []
        self.places = []
        self.fixtures = []
        self.roads = json.loads(json.dumps(city["roads"]))
        self.road_columns = {}
        self.entries = json.loads(json.dumps(city.get("external_entries", [])))
        self.geometry = city["construction"]
        for src in city["houses"] + city["buildings"]:
            b = dict(src)
            b["source_position"] = list(src["position"])
            b["source_size"] = list(src["size"])
            b["size"] = [int(v) // 2 * 2 + 1 for v in src["size"]]
            b["position"] = [int(round(v)) for v in b["position"]]
            b["ground_air_y"] = b["position"][1]
            b["position"][1] += self.geometry["stilt_raise"]
            b["rotation_quarters"] = FACING[b["facing"]]
            b["archetype"] = ARCHETYPES[b["role"]]
            b["detail"] = "detailed"
            b["roof"] = city["landmark_roof"] if b["role"] == "landmark" else city["roof"]
            b["entrance"] = list(self.point(b, 0, 0, b["size"][1] // 2 + self.geometry["porch_depth"] + 1))
            b["note"] = "整数方块墙体、完整屋盖、可进入门洞、楼板与实际家具；通行、服务与玩家外观未验收。"
            self.buildings.append(b)
        self.add_access()
        self.terrain = [int(round(self.natural_air(x, z))) - 1 for z in range(self.depth) for x in range(self.width)]
        self.make_road_columns()
        self.shape_ground()

    def material(self, slot):
        return self.style.primary(slot)

    def point(self, b, x, y, z):
        rx, rz = rotate(x, z, b["rotation_quarters"])
        return (b["position"][0] + rx, b["position"][1] + y, b["position"][2] + rz)

    def box(self, b, low, high, state, layer="building"):
        a, c = self.point(b, *low), self.point(b, *high)
        self.construction.box(a, c, rotated_state(state, b["rotation_quarters"]), b["id"], layer)

    def cell(self, b, pos, state, layer="building"):
        self.box(b, pos, pos, state, layer)

    def ground(self, x, z):
        return self.terrain[z * self.width + x]

    def set_ground(self, x, z, y):
        if 0 <= x < self.width and 0 <= z < self.depth:
            self.terrain[z * self.width + x] = y

    def natural_air(self, x, z):
        c = self.city["terrain"]
        kind = c["kind"]
        if kind == "coastal_heightfield":
            delta = lerp(c["shore_x_by_z"], z) - x
            t = max(0, min(1, (delta + 4) / 6))
            return c["bed_y"] + t * (c["land_y"] - c["bed_y"])
        if kind == "connected_mountain_heightfield":
            h = lerp(c["shelves"], z)
            h += max(0, (28 - x) / 28, (x - 199) / 25) * (8 + 3 * math.sin(z / 29))
            h += math.sin(x / 25 + z / 37) * .7 + math.cos(z / 21) * .5
            r = c["ravine"]
            z0, z1 = r["z_range"]
            if z0 <= z <= z1:
                influence = max(0, 1 - abs(x - r["center_x"] - 1.1 * math.sin(z / 12)) / r["half_width"])
                h -= r["depth"] * influence * max(0, min(1, (z - z0) / 6, (z1 - z) / 6))
            return max(2, h)
        if kind == "oasis_basin":
            cx, cz = c["center"]
            rx, rz = c["radius"]
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
            center = lerp(c["rift_x_by_z"], z)
            delta = abs(x - center)
            bank_air = c["west_air_y"] if x < center else c["east_air_y"]
            t = max(0, min(1, (delta - c["half_width"]) / c["bank_width"]))
            return c["bed_y"] + 1 + t * (bank_air - c["bed_y"] - 1)
        raise ValueError("Unknown authored terrain kind: " + kind)

    def add_access(self):
        by_id = {r["id"]: r for r in self.roads}
        occupied = set()
        for b in self.buildings:
            hx, hz = [v // 2 for v in b["size"]]
            for z in range(-hz - 1, hz + self.geometry["porch_depth"] + 1):
                for x in range(-hx - 1, hx + 2):
                    xx, _, zz = self.point(b, x, 0, z)
                    occupied.add((xx, zz))
        for b in self.buildings:
            if b["role"] == "gate":
                continue
            e = b["entrance"]
            route = by_id[b["road_id"]]
            goals = {}
            for a, c in zip(route["points"], route["points"][1:]):
                dx, dz = c[0] - a[0], c[2] - a[2]
                length = max(abs(dx), abs(dz), 1)
                for step in range(length + 1):
                    p = [round(a[i] + (c[i] - a[i]) * step / length) for i in range(3)]
                    if (p[0], p[2]) not in occupied and 0 <= p[0] < self.width and 0 <= p[2] < self.depth:
                        goals[(p[0], p[2])] = p[1]
            # Route an actual approach around all room footprints.  The nearest
            # projected street point can be behind a house; drawing a straight
            # segment there would cut through its own wall.
            start = (e[0], e[2])
            queue = deque([start])
            parent = {start: None}
            found = None
            while queue:
                at = queue.popleft()
                if at in goals:
                    found = at
                    break
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    p = (at[0] + dx, at[1] + dz)
                    if p not in parent and p not in occupied and 0 <= p[0] < self.width and 0 <= p[1] < self.depth:
                        parent[p] = at
                        queue.append(p)
            if found is None:
                raise ValueError(self.city["city_id"] + ": 无法构造入口支路 " + b["id"])
            path = []
            p = found
            while p is not None:
                path.append(p)
                p = parent[p]
            path.reverse()
            points = [[x, round(e[1] + (goals[found] - e[1]) * i / max(1, len(path) - 1)), z] for i, (x, z) in enumerate(path)]
            if len(points) == 1:
                points.append(list(points[0]))
            self.roads.append({"id": "access_" + b["id"], "name": b["name"] + "入口支路", "width": 2,
                               "kind": "boardwalk" if self.geometry["stilt_raise"] else "street",
                               "points": points, "building_id": b["id"]})
        for pier in self.city.get("piers", []):
            shore = lerp(self.city["terrain"]["shore_x_by_z"], pier["z"])
            self.roads.append({"id": pier["id"], "name": "卸货栈桥", "width": pier["width"], "kind": "bridge",
                               "points": [[round(shore - 10), 8, pier["z"]], [pier["end_x"], 8, pier["z"]]]})

    def make_road_columns(self):
        for road in self.roads:
            for a, b in zip(road["points"], road["points"][1:]):
                dx, dz = b[0] - a[0], b[2] - a[2]
                length = max(abs(dx), abs(dz), 1)
                major_x = abs(dx) >= abs(dz)
                radius = max(0, int(road["width"]) // 2)
                for step in range(length + 1):
                    t = step / length
                    x, z = round(a[0] + dx * t), round(a[2] + dz * t)
                    y = round(a[1] + (b[1] - a[1]) * t)
                    stairs = a[1] != b[1]
                    uphill = ("east" if dx > 0 else "west") if major_x else ("south" if dz > 0 else "north")
                    if b[1] < a[1]:
                        uphill = DIRECTIONS[(FACING[uphill] + 2) % 4]
                    for cross in range(-radius, radius + 1):
                        xx, zz = (x, z + cross) if major_x else (x + cross, z)
                        if 0 <= xx < self.width and 0 <= zz < self.depth:
                            self.road_columns[(xx, zz)] = (y, road["kind"], stairs, uphill, road["id"], cross, step)

    def shape_ground(self):
        # Local building lots; raised houses do not fill their open undercroft.
        if not self.geometry["stilt_raise"]:
            for b in self.buildings:
                hx, hz = [v // 2 for v in b["size"]]
                for z in range(-hz, hz + self.geometry["porch_depth"] + 1):
                    for x in range(-hx, hx + 1):
                        xx, _, zz = self.point(b, x, 0, z)
                        self.set_ground(xx, zz, b["ground_air_y"] - 1)
        for (x, z), (y, kind, stairs, _, _, _, _) in self.road_columns.items():
            if kind not in ("bridge", "boardwalk"):
                self.set_ground(x, z, y - (2 if stairs else 1))

    def water_and_drainage(self):
        c = self.city["terrain"]
        top = self.city.get("water_top_y")
        if top is None:
            top = c.get("water_top_y")
        if top is not None:
            for z in range(self.depth):
                for x in range(self.width):
                    bed = self.ground(x, z)
                    if bed < top:
                        self.construction.cell((x, bed, z), self.material("RIVER_BED"), "watercourse", "riverbed")
                        self.construction.box((x, bed + 1, z), (x, top, z), self.material("WATER"), "watercourse", "water")
        for drain in self.city.get("drainage", []):
            a, b = drain["points"]
            for z in range(a[2], b[2] + 1):
                for x in range(a[0], a[0] + drain["width"]):
                    bed = max(self.ground(x, z) - 2, c.get("water_top_y", 0))
                    self.set_ground(x, z, bed)
                    self.construction.cell((x, bed, z), self.material("BASE_STONE"), drain["id"], "road")
                    self.construction.cell((x, bed + 1, z), self.material("WATER"), drain["id"], "water")

    def roads_and_bridges(self):
        for (x, z), (y, kind, stairs, uphill, rid, cross, step) in self.road_columns.items():
            raised = kind in ("bridge", "boardwalk")
            state = self.material("GROUND_PATH")
            if stairs:
                state = orient_block("vanilla_stairs", self.material("PATH_STAIRS"), "stair", facing=uphill)
            self.construction.cell((x, y - 1, z), state, rid, "road")
            self.construction.box((x, y, z), (x, y + self.geometry["headroom"] - 1, z), AIR, rid, "clearance")
            if raised and cross in (-2, 2) and step % 6 == 0:
                if self.ground(x, z) + 1 <= y - 2:
                    self.construction.box((x, self.ground(x, z) + 1, z), (x, y - 2, z), self.material("FRAME_WOOD"), rid, "support")
            if kind == "bridge":
                # Rails stay outside the walkable corridor; the central city
                # junctions remain open where another route occupies the edge.
                r = next(r for r in self.roads if r["id"] == rid)
                edge = max(0, int(r["width"]) // 2)
                if abs(cross) == edge and step > 2 and self.ground(x, z) < y - 4:
                    self.construction.cell((x, y, z), with_props(self.material("FENCE"), north=True, south=True, east=True, west=True, waterlogged=False), rid, "bridge_rail")

    def fixture(self, b, fid, local, state):
        self.cell(b, local, state, "interior")
        pos = list(self.point(b, *local))
        self.fixtures.append({"id": b["id"] + ":" + fid, "building_id": b["id"], "block": state.split("[", 1)[0], "block_pos": pos})
        return pos

    def place(self, b, pid, kind, local, block="", block_pos=None, capacity=1):
        p = {"id": pid, "kind": kind, "pos": list(self.point(b, *local)), "capacity": capacity,
             "block": block, "block_pos": block_pos or list(self.point(b, *local)), "building_id": b["id"]}
        self.places.append(p)

    def bed(self, b, name, x, floor, z, place_id=None):
        # Two real, unoccupied bed halves; rest position is an adjacent clear
        # standing cell, not the occupied mattress itself.
        base = self.style.slot_entry("FURNITURE", "_bed").split("[", 1)[0]
        state = with_props(base, facing="north", occupied=False, part="foot")
        foot = self.fixture(b, name, (x, floor, z), state)
        self.cell(b, (x, floor, z - 1), with_props(base, facing="north", occupied=False, part="head"), "interior")
        if place_id:
            self.place(b, place_id, "rest", (x - 1, floor, z), base, foot)

    def room_fixtures(self, b):
        hx, hz = [v // 2 for v in b["size"]]
        role = b["role"]
        upper = self.geometry["storey_height"] if b["stories"] > 1 else 0
        if role in ("house", "inn", "apothecary", "smith"):
            rest = {"house": b["id"] + "_rest", "inn": "inn_rest", "apothecary": "herbalist_rest", "smith": "smith_rest"}[role]
            self.bed(b, "bed", hx - 1, upper, -hz + 2, rest)
            self.fixture(b, "storage", (0, 0, -hz + 1), with_props(self.material("INTERIOR_STORAGE"), facing="south", open=False))
        if role == "house":
            self.fixture(b, "crafting", (-hx + 1, 0, -hz + 1), self.style.slot_entry("INTERIOR_WORK", "crafting_table"))
            self.place(b, b["id"] + "_social", "social", (0, 0, 0), capacity=2)
        elif role == "inn":
            for i, x in enumerate([-hx + 4, 0, hx - 4]):
                self.bed(b, "guest_bed_" + str(i), x, upper, -hz + 2, ["guest_west", "guest_center", "guest_east"][i])
            counter = self.fixture(b, "counter", (hx - 2, 0, hz - 2), self.material("FLOOR_WOOD"))
            self.place(b, "inn_work", "work", (hx - 3, 0, hz - 2), self.material("FLOOR_WOOD").split("[")[0], counter)
            self.place(b, "inn_social", "social", (0, 0, hz - 2), capacity=4)
            self.fixture(b, "kitchen", (-hx + 1, 0, -hz + 1), with_props(self.style.slot_entry("INTERIOR_WORK", "furnace"), facing="south", lit=False))
            self.fixture(b, "pantry", (-hx + 2, 0, -hz + 1), self.material("INTERIOR_STORAGE"))
        elif role == "apothecary":
            work = self.fixture(b, "brewing", (-hx + 1, 0, 0), with_props(self.style.slot_entry("INTERIOR_CIVIC", "brewing_stand"), has_bottle_0=False, has_bottle_1=False, has_bottle_2=False))
            self.place(b, "herbalist_work", "work", (-hx + 2, 0, 0), "minecraft:brewing_stand", work)
            self.fixture(b, "herb_shelf", (-hx + 1, 0, -hz + 1), self.style.slot_entry("INTERIOR_CIVIC", "bookshelf"))
            self.fixture(b, "cauldron", (hx - 1, 0, 0), self.style.slot_entry("INTERIOR_CIVIC", "cauldron"))
            self.place(b, "herbalist_social", "social", (0, 0, hz - 1), capacity=2)
        elif role == "smith":
            anvil = self.fixture(b, "anvil", (-hx + 1, 0, 0), with_props(self.style.slot_entry("INTERIOR_WORK", ":anvil"), facing="north"))
            self.place(b, "smith_work", "work", (-hx + 2, 0, 0), "minecraft:anvil", anvil)
            self.fixture(b, "forge", (-hx + 1, 0, -hz + 1), with_props(self.style.slot_entry("INTERIOR_WORK", "furnace"), facing="south", lit=False))
            self.fixture(b, "smithing", (hx - 1, 0, 0), self.style.slot_entry("INTERIOR_WORK", "smithing_table"))
        elif role == "market":
            for x in (-hx + 1, hx - 1):
                for z in range(-hz + 1, hz - 1, 3):
                    self.fixture(b, f"market_{x}_{z}", (x, 0, z), with_props(self.material("INTERIOR_STORAGE"), facing="south", open=False))
            self.place(b, "market_work", "work", (0, 0, -hz + 2))
            self.place(b, "market_social", "market", (0, 0, hz - 2), capacity=5)
        elif role == "guide":
            lectern = self.fixture(b, "lectern", (hx - 1, 0, -hz + 1), with_props(self.style.slot_entry("INTERIOR_CIVIC", "lectern"), facing="south", has_book=False, powered=False))
            self.place(b, "guide_work", "work", (0, 0, 0), "minecraft:lectern", lectern)
            self.place(b, "entry_social", "entry", (0, 0, hz + 1), capacity=3)
        elif role == "landmark":
            self.place(b, "cultivation", "cultivate", (0, 0, 0), capacity=4)
            self.place(b, "civic_social", "social", (0, 0, hz - 2), capacity=4)
            self.fixture(b, "library", (hx - 1, 0, -hz + 1), self.style.slot_entry("INTERIOR_CIVIC", "bookshelf"))
            if upper:
                self.place(b, "landmark_upper", "social", (0, upper, 0), capacity=3)
        elif role == "warehouse":
            for x in range(-hx + 1, hx, 3):
                self.fixture(b, "cargo_" + str(x), (x, 0, -hz + 1), self.material("INTERIOR_STORAGE"))

    def building(self, b):
        hx, hz = [v // 2 for v in b["size"]]
        floor = self.material("FLOOR_WOOD")
        wall = self.material("WALL_MAIN")
        frame = self.material("FRAME_WOOD")
        total_h = (b["stories"] - 1) * self.geometry["storey_height"] + self.geometry["wall_height"]
        porch = self.geometry["porch_depth"]
        # Interior clearance is bounded to actual rooms, never the city envelope.
        self.box(b, (-hx, 0, -hz), (hx, total_h - 1, hz), AIR, "clearance")
        self.box(b, (-hx, -1, -hz), (hx, -1, hz + porch), floor, "floor")
        if self.geometry["stilt_raise"]:
            for x in (-hx, hx):
                for z in (-hz, 0, hz, hz + porch):
                    xx, _, zz = self.point(b, x, 0, z)
                    if self.ground(xx, zz) + 1 <= b["position"][1] - 2:
                        self.construction.box((xx, self.ground(xx, zz) + 1, zz), (xx, b["position"][1] - 2, zz), frame, b["id"], "support")
            # Clear only the occupied undercroft; leave the valley terrain intact.
            self.box(b, (-hx + 1, -self.geometry["stilt_raise"], -hz + 1), (hx - 1, -2, hz - 1), AIR, "clearance")
        for z in (-hz, hz):
            self.box(b, (-hx, 0, z), (hx, total_h - 1, z), wall)
        for x in (-hx, hx):
            self.box(b, (x, 0, -hz), (x, total_h - 1, hz), wall)
        for x in (-hx, hx):
            for z in (-hz, hz, hz + porch):
                self.box(b, (x, 0, z), (x, total_h - 1, z), frame)
        for level in range(b["stories"]):
            yy = level * self.geometry["storey_height"]
            if level:
                self.box(b, (-hx + 1, yy - 1, -hz + 1), (hx - 1, yy - 1, hz - 1), floor, "floor")
            for x in (-hx, hx):
                for z in (-max(1, hz // 2), max(1, hz // 2)):
                    self.box(b, (x, yy + 1, z), (x, yy + 2, z), with_props(self.material("WINDOW"), north=True, south=True, east=False, west=False, waterlogged=False), "window")
            for x in range(-hx + 2, hx, 4):
                self.box(b, (x, yy + 1, -hz), (x + 1, yy + 2, -hz), with_props(self.material("WINDOW"), north=False, south=False, east=True, west=True, waterlogged=False), "window")
        # Planned, two-wide portal.  Gates get a genuine through passage.
        self.box(b, (0, 0, hz), (self.geometry["door_width"] - 1, 2, hz + porch), AIR, "clearance")
        if b["role"] == "gate":
            self.box(b, (-1, 0, -hz), (2, 3, hz + porch), AIR, "clearance")
            self.entries.append({"id": "main_gate", "position": list(self.point(b, 0, 0, hz + porch)), "facing": b["facing"], "building_id": b["id"]})
            self.place(b, "main_entry", "entry", (0, 0, hz + porch), capacity=4)
        if b["stories"] > 1:
            sx = -hx + 1
            for step in range(self.geometry["storey_height"]):
                z = hz - 1 - step
                self.box(b, (sx, step, z), (sx + 1, step + self.geometry["headroom"], z), AIR, "clearance")
                self.box(b, (sx, step, z), (sx + 1, step, z), orient_block("vanilla_stairs", self.material("PATH_STAIRS"), "stair", facing="north"), "interior")
        roof_grid = BlockGrid()
        node = Node(b["id"], "main_volume", (-hx, 0, -hz), (2 * hx + 1, total_h, 2 * hz + 1),
                    meta={"foundation_h": 0, "wall_h": total_h,
                          "roof": {"ridge_axis": "x", "overhang": max(porch, self.geometry["roof_overhang"]), "gable_overhang": 1}})
        roof_handler(b["roof"])(roof_grid, self.style, random.Random(0), node)
        for pos, cell in sorted(roof_grid.iter_cells()):
            self.cell(b, pos, cell.state, "roof" if "FACADE" not in cell.tags else "building")
            if self.city.get("roof_snow") and cell.state.split("[")[0] == self.material("FLOOR_WOOD"):
                self.cell(b, (pos[0], pos[1] + 1, pos[2]), self.material("SNOW"), "roof")
        if b["roof"] == "province_oasis_terrace":
            # A solid continuous shade awning at the room edge, borne by posts.
            self.box(b, (-hx, total_h, hz + 1), (hx, total_h, hz + porch), self.material("SHADE"), "roof")
        self.cell(b, (hx - 1, 0, hz + porch), self.material("LIGHTING"), "detail")
        self.room_fixtures(b)

    def courtyards_and_edges(self):
        for index, court in enumerate(self.city.get("courtyards", [])):
            cx, y, cz = court["center"]
            hx, hz = [v // 2 for v in court["size"]]
            if court.get("shade"):
                # One broad, continuous fabric canopy; its supports reach earth.
                for x in (cx - hx + 2, cx + hx - 2):
                    for z in (cz - hz + 2, cz + hz - 2):
                        self.construction.box((x, self.ground(x, z) + 1, z), (x, y + 3, z), self.material("FRAME_WOOD"), f"court_{index}", "support")
                self.construction.box((cx - hx + 2, y + 4, cz - hz + 2), (cx + hx - 2, y + 4, cz + hz - 2), self.material("SHADE"), f"court_{index}", "roof")
            if court.get("wind_wall"):
                for x in (cx - hx, cx + hx):
                    for z in range(cz - hz, cz + hz + 1):
                        if abs(z - cz) > 3:
                            self.construction.box((x, y - 1, z), (x, y + 1, z), self.material("BASE_STONE"), f"court_{index}", "wall")
        for wi, poly in enumerate(self.city.get("walls", [])):
            for a, b in zip(poly, poly[1:]):
                dx, dz = b[0] - a[0], b[2] - a[2]
                length = max(abs(dx), abs(dz), 1)
                for step in range(length + 1):
                    x, z = round(a[0] + dx * step / length), round(a[2] + dz * step / length)
                    if 0 <= x < self.width and 0 <= z < self.depth:
                        y = self.ground(x, z)
                        self.construction.box((x, y, z), (x, y + 3, z), self.material("BASE_STONE"), f"wall_{wi}", "wall")
        for index, (x, z) in enumerate(self.city.get("trees", [])):
            y = self.ground(x, z) + 1
            self.construction.box((x, y, z), (x, y + 5, z), self.material("FRAME_WOOD"), f"tree_{index}", "tree")
            self.construction.box((x - 2, y + 4, z - 2), (x + 2, y + 7, z + 2), self.material("LEAF"), f"tree_{index}", "tree")

    def construct(self):
        self.water_and_drainage()
        self.roads_and_bridges()
        self.courtyards_and_edges()
        for b in self.buildings:
            self.building(b)
        # Local fixtures do not register residents, professions or commerce.
        return self
