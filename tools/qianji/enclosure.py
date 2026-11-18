"""整座千机阁分阁共用一处外墙与门楼，保留原建筑、院路、水面和标高。"""
from functools import lru_cache
import json
from pathlib import Path

from qianji.model import AIR, build

ROOT = Path(__file__).resolve().parents[2]
RULES = ROOT / "blueprints/qianji/studies/urban_enclosure.json"


@lru_cache(maxsize=1)
def rules():
    return json.loads(RULES.read_text())


@lru_cache(maxsize=1)
def variants():
    return json.loads((ROOT / "blueprints/qianji/placement.json").read_text())["regions"]


def perimeter(width, depth):
    for x in range(width):
        yield x, 0
        yield x, depth - 1
    for z in range(1, depth - 1):
        yield 0, z
        yield width - 1, z


def opening(model):
    cx, feet, _ = model.spec["paths"][0]
    gate = rules()["gate"]
    radius = gate["width"] // 2
    return cx, feet, range(cx-radius, cx+radius+1)


def pave_court(model, town):
    """只给原图中未施工的草面落材。台基和既有院路、水院均使用原方块。"""
    cx, _, _ = opening(model)
    for (x,y,z),state in list(model.blocks.items()):
        if y==0 and state==model.material["ground"] and model.layers[x,y,z]=="ground":
            model.put(x,0,z,town["paving"],"forecourt")
    # 入口两侧的树池有固定的石沿，与门前通道分开。
    for left,right in ((max(4,cx-20),cx-8),(cx+8,min(model.w-4,cx+20))):
        for z in range(3,8):
            for x in range(left,right):
                if model.layers.get((x,0,z))!="forecourt":continue
                if any(model.blocks.get((x,y,z),AIR)!=AIR for y in (1,2,3)):continue
                edge=x in (left,right-1) or z in (3,7)
                model.put(x,0,z,model.material["paving"] if edge else "minecraft:grass_block","court_bed")
                if not edge and z==5 and (x-left)%4==2:
                    model.put(x,1,z,"minecraft:azalea_leaves[distance=1,persistent=true,waterlogged=false]","court_bed")


def build_wall(model):
    wall=rules()["wall"]
    gate=rules()["gate"]
    cx,feet,aperture=opening(model)
    for x,z in perimeter(model.w,model.d):
        if z==0 and x in aperture:continue
        # 高台邻墙时，墙身从实体台基向上收口。
        inside_x=min(max(x,1),model.w-2)
        inside_z=min(max(z,1),model.d-2)
        sill=max(0,model.terrain[inside_z*model.w+inside_x])
        model.box(x,0,z,1,sill+2,1,wall["plinth"],"enclosure")
        post=(x in (0,model.w-1) and z in (0,model.d-1)) or (z if x in (0,model.w-1) else x)%wall["pier_spacing"]==0
        for y in range(sill+2,sill+wall["height"]+1):
            model.put(x,y,z,wall["post"] if post else wall["body"],"enclosure")
        if post:
            model.put(x,sill+wall["height"]+1,z,"roof","enclosure")
        model.put(x,sill+wall["height"]+1+int(post),z,wall["cap"],"enclosure")
    lintel=feet+gate["clear_height"]
    west,east=aperture.start-1,aperture.stop
    for x in (west,east):
        for z in (0,gate["depth"]-2):
            model.put(x,feet-1,z,wall["plinth"],"gate")
            model.put(x,feet,z,wall["plinth"],"gate")
            model.box(x,feet+1,z,1,lintel-feet-1,1,wall["post"],"gate")
        model.box(x,lintel,0,1,1,gate["depth"],"minecraft:dark_oak_log[axis=z]","gate")
    for z in (0,gate["depth"]-2):
        model.box(west,lintel,z,east-west+1,1,1,"beam","gate")
    for z in range(gate["depth"]+1):
        rise=min(z,gate["depth"]-z)
        model.box(west-2,lintel+1+rise//2,z,east-west+5,1,1,
                  "roof" if rise%2 else "roof_slab","gate")
    model.floor(aperture.start,feet,0,gate["width"],gate["depth"]+2,gate["paving"],"threshold")
    for x in (west-1,east+1):
        model.put(x,lintel,2,"minecraft:lantern[hanging=true,waterlogged=false]","gate")
    model.target("court_gate",(cx,feet,0))


def check_wall(model):
    cx,feet,gap=opening(model);wall=rules()["wall"];gate=rules()["gate"]
    material={wall["body"],wall["plinth"],wall["post"]}
    edges=0
    for x,z in perimeter(model.w,model.d):
        if x in gap and z==0:continue
        edges+=1
        for y in range(feet,feet+wall["height"]):
            if model.blocks.get((x,y,z)) not in material:
                raise ValueError((model.spec["id"],"围墙缺口",x,y,z))
    for z in range(gate["depth"]):
        for x in gap:
            if model.blocks.get((x,feet-1,z))!=gate["paving"]:
                raise ValueError((model.spec["id"],"门槛缺地坪",x,z))
            if any(model.blocks.get((x,y,z),AIR)!=AIR for y in range(feet,feet+gate["clear_height"])):
                raise ValueError((model.spec["id"],"院门净空不足",x,z))
    return {"wall_columns":edges,"gate_columns":len(gap),"gate_clear_height":gate["clear_height"],"local_gate":[cx,feet,0]}


def build_enclosed(region,validate=True):
    town=rules()["towns"][region]
    model,_=build(variants()[region],validate=False)
    for x,z in perimeter(model.w,model.d):
        if any(model.blocks.get((x,y,z),AIR)!=AIR for y in range(1,model.h)):
            raise ValueError((region,"外墙压到原有楼体",x,z))
    pave_court(model,town)
    build_wall(model)
    checks=check_wall(model)
    if validate:
        checks.update(model.connected())
    return model,checks
