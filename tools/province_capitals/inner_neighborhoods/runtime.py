"""将街坊施工结果、设施坐标和生活点导出到正式七城资源。"""
from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import shutil
from tempfile import TemporaryDirectory

from buildgen.modset import load_modset
from province_capitals.game.compile import life_inputs, resource_file, sha
from .blocks import BlockCity
from .export import load_source
from .plan import DESIGNS, DIR, FACES, HOUSES, Neighborhood, entry_face, read, rotate, write
from .readback import check, market_reach
from .structures import emit
from .town_gates import connect_gate

ROOT = Path(__file__).resolve().parents[3]
PLACEMENT = "enclosed_town_ward"


def state_at(city, pos):
    x, y, z = pos
    if 0 <= x < city.w and 0 <= z < city.d and 0 <= y < city.h:
        return city.states[city.blocks[y, z, x]].partition("[")[0]
    return "minecraft:air"


def standing(city, pos):
    x, y, z = pos
    support = state_at(city, (x, y-1, z))
    return (0 <= x < city.w and 0 <= z < city.d and 1 <= y < city.h-1
            and not support.endswith(("_fence", "_wall", "_door", "_trapdoor", "_bed"))
            and support not in ("minecraft:air", "minecraft:water", "minecraft:lava")
            and state_at(city, pos) == state_at(city, (x, y+1, z)) == "minecraft:air")


def move_point(obj, point):
    a, b, A, B = obj["source"]
    x, y, z = point
    dx, dz = rotate(x-a, z-b, A-a, B-b, obj.get("turn", 0))
    return [obj["box"][0]+dx, y+obj["delta"], obj["box"][1]+dz]


def move_facing(obj, facing):
    return FACES[(FACES.index(facing)+obj.get("turn", 0)) % 4]


def move_box(obj, bounds):
    a, b, A, B = bounds  # 源清单使用包含末点的边界。
    points = [move_point(obj, (x, 0, z)) for x in (a, A) for z in (b, B)]
    return [min(p[0] for p in points), min(p[2] for p in points),
            max(p[0] for p in points), max(p[2] for p in points)]


def street_position(city, obj):
    x, z = obj["outside"]
    y = int(city.street_height[z, x])+1
    pos = [x, y, z]
    if not standing(city, pos):
        raise ValueError((city.region, obj["id"], "公共街面站位缺净空", pos))
    return pos


def place_row(place_id, kind, pos, building, *, block="", block_pos=None, capacity=1):
    return {"id": place_id, "kind": kind, "pos": pos, "capacity": capacity, "block": block,
            "block_pos": pos if block_pos is None else block_pos, "building_id": building}


def original_places(city, objects, gates):
    places = []
    for row in city.city["life"]["places"]:
        target = row["building_id"]
        if row["id"] == "jianghu_board" or target == "npc_courtyard":
            continue  # 任务榜和院宅床位在本次终态重新采集
        obj = objects.get({"lord_manor": "manor", "main_landmark": "landmark"}.get(target, target))
        if obj is None:
            raise ValueError((city.region, row["id"], "原服务生活点缺所属建筑"))
        if row["kind"] == "entry" and target in gates:
            pos = gates[target]["entry"]
            places.append({**row, "pos": pos, "block_pos": pos})
            continue
        new = {**row, "pos": move_point(obj, row["pos"]), "block_pos": move_point(obj, row["block_pos"])}
        if row["block"] and state_at(city, new["block_pos"]) != row["block"]:
            raise ValueError((city.region, row["id"], "生活设施与实际城市方块不符", new["block_pos"]))
        if not standing(city, new["pos"]):
            # 原脚点可落在楼梯或布置改变的门前。就近找同一栋内的落脚点。
            x, y, z = new["pos"]
            a, b, A, B = obj["box"]
            radius=8 if row["kind"]=="cultivate" else 2
            candidates=sorted(
                ([px,py,pz] for px in range(max(a,x-radius),min(A,x+radius+1))
                 for pz in range(max(b,z-radius),min(B,z+radius+1))
                 for py in range(max(1,y-3),min(city.h-1,y+4))),
                key=lambda p:abs(p[0]-x)+abs(p[2]-z)+abs(p[1]-y)*2)
            position = next((p for p in candidates if standing(city, p)), None)
            if position is None:
                raise ValueError((city.region, row["id"], "原生活点周围无可站立方块", new["pos"]))
            new["pos"] = position
            if not row["block"]:
                new["block_pos"] = position
        places.append(new)
    return places


def home_places(city, home):
    template = read(HOUSES/(home["id"]+".json"))
    a, b, A, B = home["box"]
    w, _, d = template["size"]
    turn = home["turn"]
    floor = home["grade"]+1

    def at(point):
        x, y, z = point
        dx, dz = rotate(x, z, w, d, turn)
        return [a+dx, floor+y, b+dz]

    building_id = f"npc_courtyard_{home['id']}"
    home_rows = []
    for number, bed in enumerate(template["beds"]):
        fixture = at(bed)
        block = state_at(city, fixture)
        if not block.endswith("_bed"):
            raise ValueError((city.region, home["id"], "床位未生成", fixture))
        x, y, z = fixture
        position = next(([x+dx, y, z+dz] for dx,dz in DIR.values() if standing(city, (x+dx,y,z+dz))), None)
        if position is None:
            raise ValueError((city.region, building_id, "床边无站立点", fixture))
        home_rows.append(place_row(f"{building_id}_rest_{number}", "rest", position, building_id, block=block, block_pos=fixture))
    if not home_rows:
        raise ValueError((city.region, building_id, "院宅需要实际床位"))
    street = street_position(city, home)
    social = place_row(f"{building_id}_social", "social", street, building_id, capacity=len(home_rows))
    # 每座院子先绑定一个住户，其余床位依原有生活分配使用。
    household = {"home": home_rows[0]["id"], "work": social["id"]}
    row = {"id": building_id, "template": home["id"], "name": home["name"], "bounds": home["box"],
           "entry": at(template["entry"]), "outside": street, "facing": home["front"],
           "rest_places": [p["id"] for p in home_rows], "variant": city.variants[home["id"]]}
    return home_rows+[social], household, row


def plot_definition(city, plot, reached):
    x,z,X,Z=plot["box"]
    front=city.profile["plot_front"]
    # 正式入口在宅田净面积之外。沿已接通街面选最靠近这一侧中点的位置。
    dx,dz=DIR[front]
    edge_x=x-1 if dx==-1 else X if dx==1 else (x+X)//2
    edge_z=z-1 if dz==-1 else Z if dz==1 else (z+Z)//2
    candidates=[]
    for distance in range(6):
        for side in range(-10,11):
            a,b=edge_x+dx*distance-dz*side,edge_z+dz*distance+dx*side
            if not (0 <= a < city.w and 0 <= b < city.d) or city.major_lots[b,a]:
                continue
            if not reached[b,a]:
                continue
            y=int(city.street_height[b,a] if city.road[b,a] else city.ground[b,a])+1
            if standing(city,(a,y,b)):
                candidates.append((distance+abs(side),abs(side),a,y,b))
    if len(candidates)<2:
        raise ValueError((city.region, "宅田临街入口或执事站位不可达"))
    candidates.sort()
    entry=list(candidates[0][2:])
    side_positions=[r for r in candidates[1:] if (r[2]-entry[0])*dx+(r[4]-entry[2])*dz==0]
    steward=list((side_positions or candidates[1:])[0][2:])
    return {"id":f"{city.region}_player_land","name":"玩家宅田","bounds":plot["box"],
            "floor_y":plot["grade"]+1,"entry":entry,"steward":steward}


def building_metadata(city, objects, homes, gates):
    rows = []
    replaced_qianji={room["id"] for room in city.city["qianji"]["rooms"]}
    for b in city.city["buildings"]:
        if b["id"] in replaced_qianji or b["family"]=="housing_residences":
            continue
        obj = objects.get("manor" if b["id"]=="lord_manor" else b["id"])
        if obj is None:
            raise ValueError((city.region,b["id"],"保留建筑没有正式落位"))
        lo,hi=b["bounds"]["min"],b["bounds"]["max"]
        box=move_box(obj,[lo[0],lo[2],hi[0],hi[2]])
        entrance=gates[b["id"]]["entry"] if b["id"] in gates else move_point(obj,b["entrance"])
        facing=b.get("facing") or obj.get("facing") or entry_face(obj["source"],b["entrance"])
        rows.append({**b,"bounds":{"min":[box[0],lo[1]+obj["delta"],box[1]],"max":[box[2],hi[1]+obj["delta"],box[3]]},
                     "position":move_point(obj,b["position"]),"entrance":entrance,
                     "facing":move_facing(obj,facing)})
    for h in homes:
        x,z,X,Z=h["bounds"]
        row=objects[h["template"]];foot=row["grade"]+1;size=read(HOUSES/(h["template"]+".json"))["size"]
        rows.append({"id":h["id"],"name":h["name"],"role":"house","family":"housing_residences",
                     "bounds":{"min":[x,foot-1,z],"max":[X-1,foot+size[1]-1,Z-1]},
                     "position":[x,foot,z],"entrance":h["entry"],"facing":h["facing"]})
    return rows


def runtime_manifest(city, gates, checks, coord_source):
    source=city.city
    out=deepcopy(source)
    objects={o["id"]:o for o in city.objects}
    gate_by_owner={p["owner"]:p for p in gates}
    qianji={**city.qianji,"group":"qianji","scale":city.qianji_model.spec["scale"],"placement":PLACEMENT}
    road=(city.road!=0)|city.public_paths
    surface=city.street_height.copy()
    district_y=city.public_paths & (city.road==0)
    surface[district_y]=city.ground[district_y]
    reached=market_reach(city,road,surface)
    for gate in gates:
        x,_,z=gate["entry"]
        if not reached[z,x]:
            raise ValueError((city.region,gate["id"],"城门到不了城内市集"))
    land=plot_definition(city,objects["player_land"],reached)
    places=original_places(city,objects,gate_by_owner)
    households=[];homes=[]
    for o in city.objects:
        if o["type"]!="home":
            continue
        more,pair,h=home_places(city,o)
        places.extend(more);households.append(pair);homes.append(h)
    b=qianji["board"]
    places.append(place_row("jianghu_board","entry",b["reader"],"qianji",block="yuanxian:jianghu_board",block_pos=b["position"]))
    buildings=building_metadata(city,objects,homes,gate_by_owner)
    old_entries={v["id"]:v for v in source["entries"]}
    entries=[{"id":p["owner"],"position":p["entry"],"facing":p["facing"]} for p in gates]
    for key in ("lord_manor","watch_nw"):
        if key in old_entries:
            old=old_entries[key];obj=objects["manor" if key=="lord_manor" else key]
            entries.append({**old,"position":move_point(obj,old["position"]),"facing":move_facing(obj,old["facing"])})
    entries.extend([{"id":"qianji","position":qianji["entry"],"facing":qianji["facing"]},
                    {"id":"player_land","position":land["entry"],"facing":city.profile["plot_front"]}])
    stones=[]
    for row in source["waystones"]:
        obj=objects[row["id"]]
        pos=move_point(obj,row["position"])
        x,y,z=pos
        # 石碑由可选模组加载后放置。正式 NBT 记录带地坪的 5×5 到达区。
        if any(not standing(city,(x+dx,y,z+dz)) or state_at(city,(x+dx,y+3,z+dz))!="minecraft:air"
               for dx in range(-2,3) for dz in range(-2,3)):
            raise ValueError((city.region,row["id"],"传送亭缺完整到达区",pos))
        stones.append({**row,"position":pos,"facing":move_facing(obj,row["facing"])})
    out.update(size=[city.w,city.h,city.d],tiles=[],entries=entries,buildings=buildings,waystones=stones,
               qianji=qianji,player_housing=[land],npc_housing=homes,npc_households=households,
               palette_states=sorted({city.states[int(code)] for code in city.used_states}),
               housing_landscape={"variant_note":city.profile["variant_note"],"public_courts":city.ground_sites,"variants":city.variants},
               roads=[{"id":g["id"],"width":g["width"],"points":[g["entry"]],"surface_columns":g["surface_columns"]} for g in gates],
               access_paths=[],waystone_reservations=[],
               town_wall={"points":city.plan["wall"],"height":source["town_wall"]["height"],"material":source["town_wall"]["material"]})
    out["terrain"].update(width=city.w,depth=city.d)
    out["life"].update(places=places,fixtures=[])
    out["coverage"].update(houses=sum(b["role"]=="house" for b in buildings),buildings=len(buildings),
                           life_places=len(places))
    for k in ("manor","landmark"):
        row=out["blueprint"][k];obj=objects[k]
        row["bounds"]=move_box(obj,row["bounds"])
        if "origin" in row:
            row["origin"]=move_point(obj,row["origin"])
    out["blueprint"]["public_passages"]=gates
    out["blueprint"]["gardens"]=[{"id":o["id"],"name":o["name"],"access":"public","bounds":[o["box"][0],o["box"][1],o["box"][2]-1,o["box"][3]-1],"y":o["grade"]+1}
                                for o in city.objects if o["type"]=="garden"]
    out.pop("player_economy",None)
    out["source"]={"generator":str(Path(__file__).relative_to(ROOT)),"geometry_inputs":coord_source,"donor_manifest":city.source_hash}
    out["status"].update(generation="completed",nbt_roundtrip="pass",street_readback=checks)
    life_inputs(out,city.w,city.h,city.d,192)
    return out


def export_city(donors, out, region, plan_dir):
    import numpy as np

    source=load_source(donors,region)
    config=read(DESIGNS)[region]
    Neighborhood(region,config,source[0]).export(plan_dir)
    write(plan_dir/"inputs.json",{"manifest":source[1]})
    city=BlockCity(source,plan_dir).assemble()
    binding=next(p for p in read(ROOT/"config/roads/prototype.json")["ports"] if p["region"]==region)
    gates=connect_gate(city,binding)
    checks=check(city)
    if not all([checks["market_front"], checks["qianji_front"], *checks["home_fronts"].values(),
                *checks["plot_fronts"].values(), *checks["public_courts"].values()]):
        raise ValueError((region,"最终街坊的宅院、宅田或广场缺少公共通路"))
    city.used_states=np.unique(city.blocks)
    errors=load_modset("full").palette_block_errors([city.states[int(s)] for s in city.used_states])
    if errors:
        raise ValueError((region,"街坊实际方块未注册",errors))
    source_paths=(DESIGNS, Path(__file__), Path(__file__).with_name("town_gates.py"))
    provenance={str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in source_paths}
    manifest=runtime_manifest(city,gates,checks,provenance)
    geometry=emit(city,plan_dir/"chunks",donors,standalone=False)
    manifest["terrain"]["sha256"]=geometry["terrain"]["sha256"]
    prefix=f"yuanxian:province_capitals/v4/{manifest['city_id']}/"
    manifest["tiles"]=[{"x":row["x"],"z":row["z"],"template":prefix+Path(row["file"]).stem,
                        "sha256":row["sha256"],"bytes":row["bytes"],"block_count":row["blocks"],
                        "palette_count":row["palette_count"]} for row in geometry["tiles"]]
    manifest["coverage"].update(templates=len(manifest["tiles"]),recorded_blocks=geometry["tile_blocks"])
    manifest_path=out/f"data/yuanxian/capitals/v4/{manifest['city_id']}/city.json"
    terrain_path=resource_file(out,manifest["terrain"]["resource"])
    terrain_path.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(plan_dir/"chunks"/geometry["terrain"]["file"],terrain_path)
    for row,tile in zip(geometry["tiles"],manifest["tiles"],strict=True):
        path=resource_file(out,tile["template"],template=True)
        path.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(plan_dir/"chunks"/row["file"],path)
    write(manifest_path,manifest)
    print(f"{region} 排入 {len(manifest['npc_housing'])} 座 NPC 院宅，{len(manifest['tiles'])} 个切片已回读",flush=True)
    return manifest


def integrate(donors,out):
    for region in read(DESIGNS):
        with TemporaryDirectory(prefix=f"capital-{region}-",dir=out.parent) as folder:
            export_city(donors,out,region,Path(folder))
