#!/usr/bin/env python3
"""独立宗门试点：复用城镇施工索引、NBT写入和方块预览，正式生成器不变。"""
from __future__ import annotations

import argparse
import base64
from collections import deque
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from buildgen.groups import get_group
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt, state_string
from buildgen.sect import TEMPLATE_VARIANTS
from province_capitals.regional.v2.common import AIR, Construction, write_json
from province_capitals.regional.v2.preview import SceneBuilder
from province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from province_capitals.blueprint_plan.atomic_blocks import parse

HERE = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def line(a, b):
    """有序四邻接整数路径；每步只改一个XZ轴，Y按里程插值。"""
    dx, dz = b[0] - a[0], b[2] - a[2]
    length = abs(dx) + abs(dz)
    if not length:
        if a != b:
            raise ValueError("路径不能原地竖直升降")
        return [tuple(a)]
    if abs(b[1] - a[1]) > length:
        raise ValueError("路径高差超过步长")
    x, z = a[0], a[2]
    result = [tuple(a)]
    for i in range(1, length + 1):
        if abs(x - a[0]) < round(abs(dx) * i / length):
            x += 1 if dx > 0 else -1
        else:
            z += 1 if dz > 0 else -1
        result.append((x, round(a[1] + (b[1] - a[1]) * i / length), z))
    if result[-1] != tuple(b):
        raise ValueError("路径插值没有抵达端点")
    return result


def route_cells(points):
    result = []
    for a, b in zip(points, points[1:]):
        result.extend(line(a, b)[:-1])
    return result + [tuple(points[-1])]


def passable(state):
    return state == AIR or ("_door[" in state and "trapdoor" not in state)


def inside_polygon(x,z,points):
    inside = False
    for a,b in zip(points,points[1:]+points[:1]):
        if (a[1]>z)!=(b[1]>z) and x<(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0]:
            inside = not inside
    return inside


class SectModel:
    def __init__(self, spec):
        if spec.get("schema") != 1 or spec.get("status") not in ("design_review","approved"):
            raise ValueError("只接受schema1宗门设计")
        group = get_group(spec["group_id"])
        if group.group_id != "cultivation_sect":
            raise ValueError("试点须使用现有宗门家族")
        self.spec = spec
        self.width, self.height, self.depth = spec["dimensions"]
        if any(type(n) is not int or n<=0 for n in spec["dimensions"]):
            raise ValueError("尺寸须为正整数")
        for key in ("buildings","routes","anchors"):
            ids = [row["id"] for row in spec[key]]
            if len(ids)!=len(set(ids)):
                raise ValueError(key+" 存在重复ID")
        self.city = {"palette": spec["palette"]}
        self.style_materials = json.loads((ROOT/"tools/buildgen/styles"/(group.style_id+".json")).read_text())["material_slots"]
        self.construction = Construction(spec["dimensions"])
        self.terrain = []
        self.buildings = []
        self.routes = []
        self.entities = {}
        self.template_hashes = {}
        self.materials = {}
        for key,state in spec["materials"].items():
            name,values = parse(state)
            self.materials[key] = name + ("["+",".join(k+"="+v for k,v in sorted(values.items()))+"]" if values else "")
        self.surface_columns = {}
        self.protected = {}
        self.route_conflicts = []
        self.bridge_supports = []

    def material(self, key):
        return self.style_materials[key][0]

    def ground(self, x, z):
        return self.terrain[z * self.width + x]

    def set_ground(self, x, z, y):
        if not (0 <= x < self.width and 0 <= z < self.depth and 0 <= y < self.height):
            raise ValueError("地形越界")
        self.terrain[z * self.width + x] = y

    def get(self, p):
        x, y, z = p
        state = self.construction.at(p)
        if state is not None:
            return state
        if 0 <= x < self.width and 0 <= z < self.depth and 0 <= y <= self.ground(x,z):
            return self.materials["rock"]
        return AIR

    def walkable(self, p):
        x, y, z = p
        return not passable(self.get((x,y-1,z))) and all(
            passable(self.get((x,y+dy,z))) for dy in range(self.spec["rules"]["headroom"]))

    def build_terrain(self):
        cfg = self.spec["terrain"]
        for z in range(self.depth):
            for x in range(self.width):
                h = cfg["base"]
                for peak in cfg["massifs"]:
                    cx, cz = peak["center"]; rx, rz = peak["radii"]
                    xx,zz = (x-cx)/rx,(z-cz)/rz
                    r = math.hypot(xx,zz)
                    r *= 1+peak.get("ridge_amplitude",0)*math.cos(math.atan2(zz,xx)*peak.get("ridge_count",1)+peak.get("ridge_phase",0))
                    if r < 1:
                        h = max(h, cfg["base"] + (peak["top"]-cfg["base"])*(1-r)**peak["power"])
                self.terrain.append(round(h))
        for shelf in cfg["shelves"]:
            x0,z0,x1,z1 = shelf["bounds"]
            for z in range(z0,z1):
                for x in range(x0,x1):
                    if "outline" not in shelf or inside_polygon(x+.5,z+.5,shelf["outline"]):
                        self.set_ground(x,z,shelf["walk_y"]-1)

    def place_buildings(self):
        for b in self.spec["buildings"]:
            if b["template"] not in TEMPLATE_VARIANTS[b["archetype"]]:
                raise ValueError("组件未在宗门模板表注册："+b["template"])
            source = ROOT/"src/main/resources/data/myvillage/structure"/(b["template"]+".nbt")
            self.template_hashes[str(source.relative_to(ROOT))] = digest(source)
            _, root = read_gzipped_nbt(source)
            if digest(source)!=self.template_hashes[str(source.relative_to(ROOT))]:
                raise ValueError("读取期间模板改变："+str(source))
            if root.get("entities"):
                raise ValueError("试点尚不支持实体搬移")
            palette = [state_string(s) for s in root["palette"]]
            ox,oy,oz = b["origin"]
            for z in range(oz,oz+root["size"][2]):
                for x in range(ox,ox+root["size"][0]):
                    if self.ground(x,z)>=oy:
                        self.set_ground(x,z,oy-1)
            for cell in root["blocks"]:
                x,y,z = cell["pos"]; p = (ox+x,oy+y,oz+z)
                state = palette[cell["state"]]
                self.construction.cell(p,state,b["id"],"building")
                if state != AIR:
                    if p in self.protected:
                        raise ValueError("建筑相交："+b["id"])
                    self.protected[p] = (b["id"],state)
                if y==0 and state!=AIR and self.ground(ox+x,oz+z)<oy-1:
                    self.construction.box((ox+x,self.ground(ox+x,oz+z)+1,oz+z),(ox+x,oy-1,oz+z),
                                          self.materials["foundation"],b["id"],"support")
            for p, entity in block_entities(source).items():
                self.entities[tuple(b["origin"][i]+p[i] for i in range(3))] = entity
            self.buildings.append({**b,"size":root["size"],"source_sha256":digest(source)})
        for opening in self.spec["openings"]:
            self.construction.box(opening["low"],opening["high"],AIR,opening["id"],"opening")
            x0,_,z0 = opening["low"]; x1,_,z1 = opening["high"]
            self.construction.box((x0,opening["floor_y"],z0),(x1,opening["floor_y"],z1),
                                  self.materials["path"],opening["id"],"opening")

    def build_courts(self):
        for court in self.spec.get("courts",[]):
            x0,z0,x1,z1 = court["bounds"]; y = court["walk_y"]
            for z in range(z0,z1):
                for x in range(x0,x1):
                    if self.ground(x,z)>=y:
                        self.set_ground(x,z,y-1)
                    bottom = self.ground(x,z)+1
                    if bottom<=y-2:
                        self.construction.box((x,bottom,z),(x,y-2,z),self.materials["foundation"],court["id"],"support")
                    edge = x in (x0,x1-1) or z in (z0,z1-1)
                    self.construction.cell((x,y-1,z),self.materials["edge" if edge else "path"],court["id"],"court")
            for inlay in court.get("inlays",[]):
                for x,yy,z in route_cells(inlay["points"]):
                    if not (x0<=x<x1 and z0<=z<z1 and yy==y-1):
                        raise ValueError("庭院嵌纹越界")
                    self.construction.cell((x,yy,z),self.materials[inlay["material"]],court["id"],"court")

    def connect_entries(self):
        for connection in self.spec.get("entry_connections",[]):
            cells=route_cells(connection['points'])
            for i,(x,y,z) in enumerate(cells):
                self.support_column(x,z,self.ground(x,z)+1,y-2,connection['owner'])
                state=self.materials['path']
                if i+1<len(cells) and cells[i+1][1]>y:
                    dx,dz=cells[i+1][0]-x,cells[i+1][2]-z
                    face='east' if dx>0 else 'west' if dx<0 else 'south' if dz>0 else 'north'
                    state=self.materials['stairs']+f'[facing={face},half=bottom,shape=straight,waterlogged=false]'
                self.construction.cell((x,y-1,z),state,connection['owner'],'threshold')

    def plan_routes(self):
        for route in self.spec["routes"]:
            centers = route_cells(route["points"])
            width = route["width"]
            if width < 1 or width % 2 != 1:
                raise ValueError("路径须为正奇数宽度")
            columns = {}
            for i,(x,y,z) in enumerate(centers):
                width = route["width"]
                for limit in route.get("width_constraints",[]):
                    x0,z0,x1,z1 = limit["bounds"]
                    if x0<=x<x1 and z0<=z<z1:
                        width = limit["width"]
                a = centers[max(0,i-1)]; b = centers[min(i+1,len(centers)-1)]
                along_x = abs(b[0]-a[0]) > abs(b[2]-a[2])
                for offset in range(-width//2+1,width//2+1):
                    p = (x,z+offset) if along_x else (x+offset,z)
                    # 最近中心点负责该列，中心线自身最后覆盖，避免弯道吞掉踏步。
                    if p not in columns or abs(offset) < columns[p][1]:
                        columns[p] = (y,abs(offset),i)
            for i,(x,y,z) in enumerate(centers):
                columns[x,z] = (y,0,i)
            self.routes.append({**route,"centers":centers,"columns":columns})
        # 路口共享唯一高度；相交路线必须在同一标高相接。
        for route in self.routes:
            for p,(y,d,i) in route["columns"].items():
                if p in self.surface_columns and self.surface_columns[p][0] != y:
                    old = self.surface_columns[p]
                    if d == 0 and old[1] == 0:
                        self.route_conflicts.append([list(p),old[0],y])
                    if d >= old[1]:
                        continue
                self.surface_columns[p] = (y,d,route["id"],i)
        if self.route_conflicts:
            raise ValueError("路径中心线标高冲突："+str(self.route_conflicts[:8]))

    def support_column(self,x,z,bottom,top,tag):
        """补实道路基础，保留组件已经存在的实心台基。"""
        start = bottom
        for y in range(bottom,top+1):
            if (x,y,z) in self.protected:
                if start<y:
                    self.construction.box((x,start,z),(x,y-1,z),self.materials["foundation"],tag,"support")
                start = y+1
        if start<=top:
            self.construction.box((x,start,z),(x,top,z),self.materials["foundation"],tag,"support")

    def build_routes(self):
        by_id = {r["id"]:r for r in self.routes}
        for (x,z),(y,offset,rid,index) in self.surface_columns.items():
            r = by_id[rid]
            is_bridge = r["kind"] == "arch_bridge"
            if self.ground(x,z) >= y:
                self.set_ground(x,z,y-1)
            bottom = self.ground(x,z)+1
            if is_bridge:
                t = index/max(1,len(r["centers"])-1)
                bottom = max(bottom,y-2-round(r["arch_depth"]*(2*t-1)**2))
            if bottom <= y-2:
                self.support_column(x,z,bottom,y-2,rid)
            state = self.materials["path"]
            centers = r["centers"]; at = centers[index]
            neighbor = next((n for n in centers[max(0,index-1):index+2] if n[1]>at[1]),None)
            if neighbor:
                dx,dz = neighbor[0]-at[0],neighbor[2]-at[2]
                face = "east" if dx>0 else "west" if dx<0 else "south" if dz>0 else "north"
                state = self.materials["stairs"]+f"[facing={face},half=bottom,shape=straight,waterlogged=false]"
            self.construction.cell((x,y-1,z),state,rid,"road")
            self.construction.box((x,y,z),(x,y+self.spec["rules"]["route_clearance"]-1,z),AIR,rid,"clearance")
        for r in self.routes:
            if r["kind"] != "arch_bridge":
                continue
            centers = r["centers"]
            for index in (0,len(centers)-1):
                x,y,z = centers[index]
                if self.ground(x,z)+1 <= y-2:
                    self.construction.box((x-1,self.ground(x,z)+1,z-1),(x+1,y-2,z+1),
                                          self.materials["foundation"],r["id"],"support")
                self.bridge_supports.append([x,self.ground(x,z),y-1,z])
            for i,(x,y,z) in enumerate(centers):
                a = centers[max(0,i-1)]; b = centers[min(i+1,len(centers)-1)]
                along_x = abs(b[0]-a[0]) > abs(b[2]-a[2])
                for offset in (-r["width"]//2,r["width"]//2+1):
                    xx,zz = (x,z+offset) if along_x else (x+offset,z)
                    if (xx,zz) not in self.surface_columns:
                        self.construction.box((xx,y-2,zz),(xx,y+self.spec["rules"]["bridge_rail_height"]-1,zz),
                                              self.materials["edge"],r["id"],"parapet")

    def plant_trees(self):
        for tree in self.spec["trees"]:
            x,y,z = tree["position"]; h = tree["height"]; radius = tree["radius"]
            ground = self.ground(x,z)
            self.construction.box((x,ground+1,z),(x,y+h,z),self.materials["tree_log"],"pine","landscape")
            for dy in range(radius,h+1):
                r = max(0,round(radius*(h-dy)/(h-radius)))
                for dz in range(-r,r+1):
                    for dx in range(-r,r+1):
                        p = x+dx,y+dy,z+dz
                        if (dx or dz) and abs(dx)+abs(dz)<=r+1 and (p[0],p[2]) not in self.surface_columns:
                            self.construction.cell(p,self.materials["tree_leaves"],"pine","landscape")

    def construct(self):
        self.build_terrain(); self.place_buildings(); self.build_courts(); self.plan_routes(); self.build_routes(); self.connect_entries(); self.plant_trees()
        return self

    def audit(self):
        losses = []
        altered = []
        for p,(owner,state) in self.protected.items():
            if self.get(p)==state:
                continue
            x,y,z = p
            declared = any(o["owner"]==owner and o["low"][0]<=x<=o["high"][0]
                and o["floor_y"]<=y<=o["high"][1] and o["low"][2]<=z<=o["high"][2]
                for o in self.spec["openings"])
            floor = (x,z) in self.surface_columns and self.surface_columns[x,z][0]-1==y
            floor = floor or any(c['owner']==owner and (x,y+1,z) in route_cells(c['points']) for c in self.spec.get('entry_connections',[]))
            if declared or floor:
                altered.append(list(p))
            else:
                losses.append({"kind":"building_overwritten","owner":owner,"position":list(p),"state":state})
        for (x,z),(y,*_) in self.surface_columns.items():
            if not self.walkable((x,y,z)):
                losses.append({"kind":"blocked_route","position":[x,y,z]})
        for r in self.routes:
            for a,b in zip(r["centers"],r["centers"][1:]):
                if abs(a[0]-b[0])+abs(a[2]-b[2])!=1 or abs(a[1]-b[1])>self.spec["rules"]["max_step"]:
                    losses.append({"kind":"invalid_step","route":r["id"],"from":a,"to":b})
        valid = {(x,y,z) for (x,z),(y,*_) in self.surface_columns.items() if self.walkable((x,y,z))}
        start = tuple(self.spec["anchors"][0]["position"])
        reached = {start} if start in valid else set(); queue = deque(reached)
        while queue:
            x,y,z = queue.popleft()
            for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                for dy in range(-self.spec["rules"]["max_step"],self.spec["rules"]["max_step"]+1):
                    p = x+dx,y+dy,z+dz
                    if p in valid and p not in reached:
                        reached.add(p); queue.append(p)
        for anchor in self.spec["anchors"]:
            if tuple(anchor["position"]) not in reached:
                losses.append({"kind":"unreachable_anchor","id":anchor["id"],"position":anchor["position"]})
        for b in self.buildings:
            if tuple(b["entry"]) not in reached:
                losses.append({"kind":"unreachable_entry","id":b["id"],"position":b["entry"]})
        bridge_ids = {r["id"] for r in self.routes if r["kind"]=="arch_bridge"}
        bridge_cells = set()
        for tx,tz in self.construction.by_tile:
            if not any(op.tag in bridge_ids for op in self.construction.by_tile[tx,tz]):
                continue
            for (x,y,z),(state,tag,layer) in self.construction.tile(tx,tz).items():
                if tag in bridge_ids and state!=AIR:
                    bridge_cells.add((tx*16+x,y,tz*16+z))
        connected = {p for p in bridge_cells if p[1]<=self.ground(p[0],p[2])+1}
        queue = deque(connected)
        while queue:
            x,y,z = queue.popleft()
            for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
                p = x+dx,y+dy,z+dz
                if p in bridge_cells and p not in connected:
                    connected.add(p); queue.append(p)
        for p in sorted(bridge_cells-connected):
            losses.append({"kind":"unsupported_bridge_cell","position":list(p)})
        return {"status":"pass" if not losses else "fail","losses":losses,
                "road_columns":len(self.surface_columns),"reachable_columns":len(reached),
                "anchors":len(self.spec["anchors"]),"buildings":len(self.buildings),
                "bank_connected_bridge_cells":len(connected),
                "declared_building_edits":len(altered),
                "runtime":"not_run","player_acceptance":"not_verified","visual_direction":"awaiting_owner"}


class SectScene(SceneBuilder):
    def material(self,state,layer):
        base = state.split("[",1)[0]
        if base in {"supplementaries:blackstone_tile","supplementaries:blackstone_tile_wall"}:
            return "dark_stone"
        if base in {"supplementaries:stone_tile_stairs","supplementaries:stone_tile_slab","minecraft:dark_prismarine","minecraft:dark_prismarine_stairs","minecraft:dark_prismarine_slab"}:
            return "roof"
        if base in {"minecraft:calcite","minecraft:quartz_block","minecraft:polished_diorite"}:
            return "wall"
        return super().material(state,layer)


def source_hashes(spec_path,model):
    files = [spec_path,*HERE.glob("*.py"),*HERE.glob("*.html"),*HERE.glob("*.js"),
             ROOT/"tools/province_capitals/regional/v2/common.py",
             ROOT/"tools/province_capitals/regional/v2/preview.py",
             ROOT/"tools/province_capitals/blueprint_plan/nbt_entities.py",
             ROOT/"tools/province_capitals/blueprint_plan/atomic_blocks.py",
             ROOT/"tools/buildgen/nbtread.py",ROOT/"tools/buildgen/groups.py",
             ROOT/"tools/buildgen/sect.py",ROOT/"tools/buildgen/modset.py",
             ROOT/"tools/buildgen/style.py",ROOT/"tools/json_to_nbt.py",
             ROOT/"tools/buildgen/styles/cultivation_sect.json",
             ROOT/"tools/web/three.min.js",ROOT/"tools/web/OrbitControls.js",
             ROOT/"exmod/mod_block_catalog.json"]
    files += [ROOT/p for p in model.template_hashes]
    return {str(p.relative_to(ROOT)):digest(p) for p in files}


def final_tiles(model):
    """预览和正式导出共享的最终分块；地形与显式空气也逐格保留。"""
    for tz in range((model.depth+15)//16):
        for tx in range((model.width+15)//16):
            overlay = model.construction.tile(tx,tz)
            cells = {}
            for z in range(16):
                for x in range(16):
                    wx,wz = tx*16+x,tz*16+z
                    if wx>=model.width or wz>=model.depth:
                        continue
                    h = model.ground(wx,wz)
                    for y in range(h+1):
                        cells[x,y,z] = (model.materials["surface"] if y==h else model.materials["rock"],"terrain","terrain")
            cells.update(overlay)
            entities = {(x%16,y,z%16):v for (x,y,z),v in model.entities.items() if x//16==tx and z//16==tz}
            yield tx,tz,cells,entities,overlay


def export(spec_path,out):
    spec_path = Path(spec_path).resolve(); out = Path(out).resolve()
    input_hash = digest(spec_path)
    spec = json.loads(spec_path.read_text()); model = SectModel(spec).construct()
    hashes = source_hashes(spec_path,model)
    if digest(spec_path)!=input_hash or any(hashes[p]!=h for p,h in model.template_hashes.items()):
        raise ValueError("构造期间输入改变，拒绝混合来源设计")
    out.mkdir(parents=True,exist_ok=True)
    checks = model.audit(); write_json(out/"checks.json",checks)
    if checks["status"]!="pass":
        raise ValueError("宗门离线路径检查失败："+json.dumps(checks["losses"][:12],ensure_ascii=False))
    scene = SectScene(model); tiles = []; palette = set()
    for tx,tz,cells,entities,overlay in final_tiles(model):
            scene.add_tile(tx,tz,overlay)
            path = out/"minecraft"/f"tile_{tx}_{tz}.nbt"
            count,_,entity_count = write_tile(path,cells,model.height,entities)
            _,nbt = read_gzipped_nbt(path); states = [state_string(s) for s in nbt["palette"]]
            actual = {tuple(row["pos"]):states[row["state"]] for row in nbt["blocks"]}
            if actual!={p:v[0] for p,v in cells.items()}:
                raise ValueError("NBT逐块回读不一致："+str(path))
            palette.update(states)
            tiles.append({"file":str(path.relative_to(out)),"origin":[tx*16,0,tz*16],
                          "sha256":digest(path),"blocks":count,"block_entities":entity_count})
    errors = load_modset("full").palette_block_errors(sorted(palette))
    if errors:
        raise ValueError("; ".join(errors))
    checks.update(nbt_readback="pass",exported_tiles=len(tiles),modset="pass")
    write_json(out/"checks.json",checks)
    terrain_raw = struct.pack("<"+"h"*len(model.terrain),*model.terrain)
    terrain_path = out/"terrain.bin.gz"; terrain_path.write_bytes(gzip.compress(terrain_raw,mtime=0))
    inventory = {"schema":1,"kind":"sect_design_trial","id":spec["id"],"dimensions":spec["dimensions"],
                 "status":"design_review","scope":"离线设计试点；未接正式世界生成、命簿和NPC绑定。",
                 "tiles":tiles,"terrain_sha256":digest(terrain_path),"source_hashes":hashes,
                 "anchors":spec["anchors"],"checks":checks}
    write_json(out/"inventory.json",inventory)
    routes = [{k:v for k,v in r.items() if k!="columns"} for r in model.routes]
    write_json(out/"construction.json",{"buildings":model.buildings,"routes":routes,"openings":spec["openings"],
               "bridge_supports":model.bridge_supports,"anchors":spec["anchors"],"source_hashes":hashes})
    payload = {"name":spec["name"],"design":spec["design"],"dimensions":spec["dimensions"],
               "palette":scene.palette,"boxes":[[b["p"],b["s"],b["m"],b["layer"]] for b in scene.boxes],
               "terrain":base64.b64encode(terrain_raw).decode(),"buildings":model.buildings,
               "views":spec["views"],"routes":routes,"anchors":spec["anchors"],"checks":checks}
    packed_data = {"data":payload,"three":(ROOT/"tools/web/three.min.js").read_text(),
                   "orbit":(ROOT/"tools/web/OrbitControls.js").read_text(),"viewer":(HERE/"viewer.js").read_text()}
    packed = base64.b64encode(gzip.compress(json.dumps(packed_data,ensure_ascii=False,separators=(",",":")).encode(),mtime=0)).decode()
    html = (HERE/"template.html").read_text().replace("__PAYLOAD__",packed)
    (out/"index.html").write_text(html,encoding="utf-8")
    if source_hashes(spec_path,model)!=hashes:
        raise ValueError("生成期间输入改变，保留本轮产物，拒绝混合来源交付")
    print(json.dumps({"out":str(out),"tiles":len(tiles),"blocks":sum(t["blocks"] for t in tiles),"checks":checks},ensure_ascii=False))
    return inventory


if __name__=="__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input",type=Path,default=ROOT/"blueprints/sects/cloud_gate.json")
    parser.add_argument("--out",type=Path,required=True)
    args = parser.parse_args()
    try:
        export(args.input,args.out)
    except Exception:
        import traceback
        write_json(args.out/"failure.json",{"status":"failed","traceback":traceback.format_exc()})
        raise
