#!/usr/bin/env python3
"""Generate only forging-array assets; --check detects drift without writing.

The independent 64px atlas has sixteen 16px materials in a 4x4 grid. All models,
including unformed components, use this sheet. No alchemy generator dependency.
Shared Chinese names/tags are merged immediately before each individual write.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.forging_array.formed_model import build

KINDS = ("controller", "foundation", "casing", "hearth", "pylon")
MATERIALS = ("iron", "edge", "dark", "bronze", "patina", "jade", "fire", "soot",
             "stone", "stone_edge", "copper", "ash", "rivet", "rune", "ember", "gold")
DIRECTIONS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def encode(obj):
    return (json.dumps(obj, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def block_id(kind):
    return "forging_array_" + kind


def config(root=ROOT):
    cfg = json.loads((Path(root)/"tools/forging_array/visual.json").read_text())
    if cfg["version"] != 1 or set(cfg["palette"]) != set(MATERIALS):
        raise ValueError("Forging array: expected sixteen RGB materials")
    if set(cfg["names"]) != set(KINDS) or set(cfg["recipes"]) != set(KINDS):
        raise ValueError("Forging array: names/recipes must close all five part IDs")
    for color in cfg["palette"].values():
        if len(color) != 3 or any(type(x) is not int or not 0 <= x <= 255 for x in color):
            raise ValueError("Forging array: invalid RGB")
    for recipe in cfg["recipes"].values():
        pattern = recipe["pattern"]
        if (not 1 <= len(pattern) <= 3 or not 1 <= len(pattern[0]) <= 3 or
                len(set(map(len, pattern))) != 1 or
                set("".join(pattern)) - {" "} != set(recipe["key"]) or
                type(recipe["count"]) is not int or not 1 <= recipe["count"] <= 64):
            raise ValueError("Forging array: invalid shaped recipe")
    return cfg


def texture(cfg):
    def chunk(kind, data):
        return struct.pack(">I",len(data))+kind+data+struct.pack(">I",zlib.crc32(kind+data)&0xffffffff)
    rows = []
    for y in range(64):
        row = bytearray()
        for x in range(64):
            index = (y//16)*4+x//16
            part = MATERIALS[index]
            base = cfg["palette"][part]
            u,v = x%16,y%16
            n = ((u*37+v*19+u*v*7+index*13)%7)-3
            if part in ("iron", "edge", "bronze", "stone_edge"):
                n += 10 if v == 0 else -9 if v == 15 else 0
            if part == "stone":
                n += 6 if (u*11+v*5)%29 < 2 else 0
            if part in ("bronze", "copper") and (u//4*7+v//5*11)%17 == 0:
                base = [int(a*.8+b*.2) for a,b in zip(base,cfg["palette"]["patina"])]
            if part in ("jade", "rune"):
                n += 12 if u == 2 or v == 2 else 0
            if part in ("fire", "ember"):
                n += 16 if (u*3+v*7)%13 < 5 else -4
            row.extend(max(0,min(255,c+n)) for c in base)
            row.append(255)
        rows.append(b"\0"+bytes(row))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR",struct.pack(">IIBBBBB",64,64,8,6,0,0,0)) +
            chunk(b"IDAT",zlib.compress(b"".join(rows),9)) + chunk(b"IEND",b""))


def uv(material):
    index = MATERIALS.index(material)
    x,y = index%4*4,index//4*4
    return [x,y,x+4,y+4]


def geometry(kind, lit=False):
    elements = []
    def box(name, lo, hi, material="iron", glow=False):
        faces = {f:{"texture":"#atlas","uv":uv(material)}
                 for f in ("north","south","east","west","up","down")}
        if glow:
            for face in faces.values():
                face["neoforge_data"] = {"block_light":15,"sky_light":15,"ambient_occlusion":False}
        elements.append({"name":name,"from":lo,"to":hi,"shade":not glow,"faces":faces})

    if kind == "foundation":
        # An unformed structural component remains a clear full block for assembly.
        box("stone_base",[0,0,0],[16,16,16],"stone")
        box("iron_cap",[0,14,0],[16,16,16],"edge")
        box("spirit_seat",[7.3,16,1],[8.7,16.08,15],"dark")
        box("fine_inlay",[7.8,16.09,1],[8.2,16.15,15],"jade")
    elif kind == "pylon":
        box("base",[0,0,0],[16,3,16],"bronze")
        box("column",[3,3,3],[13,12,13])
        box("crown",[1,11,1],[15,13,15],"bronze")
        box("jade_core",[5,13,5],[11,16,11],"jade",True)
        for x in (2,12):
            for z in (2,12):
                box("crown_prong",[x,13,z],[x+2,16,z+2],"bronze")
        box("front_inlay",[7.7,4,2.85],[8.3,10,3],"jade",True)
    else:
        box("iron_wall",[0,0,0],[16,16,16])
        box("upper_binding",[0,14,-.06],[16,15.2,16.06],"bronze")
        box("lower_binding",[0,1,-.06],[16,2.2,16.06],"edge")
        for x in (1,14):
            box("front_strap",[x,2.2,-.08],[x+1,14,0],"bronze")
        if kind == "controller":
            box("seal_seat",[4.5,4,-.16],[11.5,12,-.09],"dark")
            box("seal_spine",[7.7,5,-.24],[8.3,11,-.17],"jade",True)
            for y in (6.2,9.2):
                box("seal_cross",[6,y,-.24],[10,y+.5,-.17],"jade",True)
        elif kind == "hearth":
            box("fire_bezel",[3,3,-.25],[13,13,-.10],"bronze")
            box("fire_window",[4,4,-.31],[12,12,-.26],"fire" if lit else "soot",lit)
            for x in (5.5,9.5):
                box("fire_grate",[x,4,-.38],[x+.75,12,-.32],"dark")
        else:
            box("wall_recess",[4,5,-.13],[12,11,-.09],"dark")
            box("sparse_seal",[7.7,6,-.19],[8.3,10,-.14],"bronze")
    return elements


def outputs(root=ROOT):
    root = Path(root)
    cfg = config(root)
    assets = root/"src/main/resources/assets/myvillage"
    data = root/"src/main/resources/data/myvillage"
    files = {
        assets/"textures/block/forging_array_formed.png": texture(cfg),
        assets/"forging_array/formed_mesh.json": (json.dumps(build(),separators=(",",":"))+"\n").encode(),
    }
    for kind in KINDS:
        bid = block_id(kind)
        for lit in ((False,True) if kind == "hearth" else (False,)):
            suffix = "_lit" if lit else ""
            files[assets/f"models/block/{bid}{suffix}.json"] = encode({
                "ambientocclusion":True,
                "textures":{"atlas":"myvillage:block/forging_array_formed","particle":"myvillage:block/forging_array_formed"},
                "elements":geometry(kind,lit)})
        # Current Java state is FACING/LIT only. Runtime must suppress these baked
        # parts when formed before the controller BER draws the shared mesh.
        # Multipart conditions deliberately ignore any future extra properties.
        parts = []
        for facing,angle in DIRECTIONS:
            if kind == "hearth":
                for lit in (False,True):
                    parts.append({"when":{"facing":facing,"lit":str(lit).lower()},
                                  "apply":{"model":f"myvillage:block/{bid}"+("_lit" if lit else ""),"y":angle}})
            else:
                parts.append({"when":{"facing":facing},
                              "apply":{"model":f"myvillage:block/{bid}","y":angle}})
        files[assets/f"blockstates/{bid}.json"] = encode({"multipart":parts})
        files[assets/f"models/item/{bid}.json"] = encode({"parent":f"myvillage:block/{bid}","display":{
            "gui":{"rotation":[25,225,0],"scale":[.6,.6,.6]},
            "ground":{"translation":[0,3,0],"scale":[.3,.3,.3]},
            "fixed":{"rotation":[0,180,0],"scale":[.5,.5,.5]},
            "firstperson_righthand":{"rotation":[0,135,0],"scale":[.4,.4,.4]},
            "thirdperson_righthand":{"rotation":[75,45,0],"scale":[.35,.35,.35]}}})
        files[data/f"loot_table/blocks/{bid}.json"] = encode({"type":"minecraft:block","pools":[{
            "rolls":1,"entries":[{"type":"minecraft:item","name":f"myvillage:{bid}"}],
            "conditions":[{"condition":"minecraft:survives_explosion"}]}],"random_sequence":f"myvillage:blocks/{bid}"})
        recipe = cfg["recipes"][kind]
        files[data/f"recipe/{bid}.json"] = encode({"type":"minecraft:crafting_shaped","category":"misc",
            "pattern":recipe["pattern"],"key":{k:{"tag" if v.startswith("#") else "item":v.removeprefix("#")}
            for k,v in recipe["key"].items()},"result":{"id":f"myvillage:{bid}","count":recipe["count"]}})
    return files


def shared_patches(root=ROOT):
    """Return only owned entries; never keep a stale snapshot of a shared document."""
    root = Path(root)
    cfg = config(root)
    yield root/"src/main/resources/assets/myvillage/lang/zh_cn.json", {
        f"{prefix}.myvillage.{block_id(k)}":name
        for k,name in cfg["names"].items() for prefix in ("block","item")}
    for tag in ("mineable/pickaxe","needs_stone_tool"):
        yield root/f"src/main/resources/data/minecraft/tags/block/{tag}.json", [f"myvillage:{block_id(k)}" for k in KINDS]


def merge_shared(path, patch):
    current = json.loads(path.read_text()) if path.exists() else ({} if isinstance(patch,dict) else {"replace":False,"values":[]})
    if isinstance(patch,dict):
        current.update(patch)
    else:
        for value in patch:
            if value not in current["values"]:
                current["values"].append(value)
    return current


def generate(root=ROOT, check=False):
    root = Path(root)
    stale = []
    files = outputs(root)
    for path,data in files.items():
        if check:
            if not path.is_file() or path.read_bytes() != data:
                stale.append(str(path.relative_to(root)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(data)
    for path,patch in shared_patches(root):
        # Read shared JSON at the point of the merge, preserving all foreign keys.
        merged = merge_shared(path,patch)
        if check:
            if not path.exists() or json.loads(path.read_text()) != merged:
                stale.append(str(path.relative_to(root)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(encode(merged))
    if stale:
        print("Stale forging array resources:\n"+"\n".join(stale))
        return 1
    print(f"Forging array resources {'match' if check else 'generated'} ({len(files)} owned + 3 merged files).")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,default=ROOT)
    parser.add_argument("--check",action="store_true")
    args = parser.parse_args(argv)
    return generate(args.root,args.check)


if __name__ == "__main__":
    raise SystemExit(main())
