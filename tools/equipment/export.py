"""Compile authored armor into the shared runtime/preview format and assets."""
from __future__ import annotations

import copy
import hashlib
import json
import math
from pathlib import Path

from .catalogue import ROOT, PROFILES, load
from .geometry import encode_png


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def rgb(color):
    return tuple(int(color[i:i+2], 16) for i in (1, 3, 5))


def surface(material, x, y, width, height, seed):
    base, light, dark = (rgb(material[k]) for k in ("base", "light", "dark"))
    border = min(x, y, width-1-x, height-1-y)
    noise = ((x * 17 + y * 37 + seed * 13) % 13 - 6) * 0.65
    pattern = material["pattern"]
    if pattern == "metal":
        color = light if (y == 0 or x == 0) else dark if border == 0 else base
        if width >= 7 and height >= 7 and x in (2, width-3) and y in (2, height-3):
            color = light
    elif pattern == "cloth":
        color = dark if border == 0 else base
        noise += 3 if (x + y) % 2 == 0 else -3
    elif pattern == "leather":
        color = light if border == 1 and (x + y) % 3 == 0 else dark if border == 0 else base
    else:
        color = light if x + y < max(2, width // 2) else dark if border == 0 else base
    return tuple(max(0, min(255, round(c + noise))) for c in color) + (255,)


def cube_faces(size, uv, density=1):
    """ModelPart.Cube's exact vertex order and UV rectangles (1.21.1)."""
    w, h, d = (v*density for v in size)
    u, v = uv
    return [
        ([(1,0,1),(0,0,1),(0,0,0),(1,0,0)], [u+d,v,u+d+w,v+d]),
        ([(1,1,0),(0,1,0),(0,1,1),(1,1,1)], [u+d+w,v+d,u+d+2*w,v]),
        ([(0,0,0),(0,0,1),(0,1,1),(0,1,0)], [u,v+d,u+d,v+d+h]),
        ([(1,0,0),(0,0,0),(0,1,0),(1,1,0)], [u+d,v+d,u+d+w,v+d+h]),
        ([(1,0,1),(1,0,0),(1,1,0),(1,1,1)], [u+d+w,v+d,u+2*d+w,v+d+h]),
        ([(0,0,1),(1,0,1),(1,1,1),(0,1,1)], [u+2*d+w,v+d,u+2*d+2*w,v+d+h]),
    ]


def fit_box(source, profile):
    box = copy.deepcopy(source)
    if profile == "slim" and box.get("fit") == "arm":
        anchor = 1 if box["bone"] == "right_arm" else -1
        box["from"][0] = anchor + (box["from"][0] - anchor) * .75
        box["size"][0] *= .75
        if "pivot" in box:
            box["pivot"][0] = anchor + (box["pivot"][0] - anchor) * .75
    return box


def compile_armor(spec, materials):
    size, density = spec["texture_size"], spec["texel_density"]
    pixels = [[(0,0,0,0)] * size for _ in range(size)]
    x = y = 1
    row_h = 0
    profiles = {}
    packed = {}
    # One atlas for both arm widths; allocation and textures are deterministic.
    for profile in PROFILES:
        pieces = []
        for item in spec["items"]:
            boxes = []
            for source in item["boxes"]:
                box = fit_box(source, profile)
                key = (box["name"], tuple(box["size"]), box["material"])
                if key in packed:
                    box["uv"] = packed[key]
                    boxes.append({k: box[k] for k in ("name","bone","from","size","uv","pivot","rotation") if k in box})
                    continue
                w,h,d = (v*density for v in box["size"])
                width, height = math.ceil(2*(w+d))+2, math.ceil(h+d)+2
                if width + 2 > size or height + 2 > size:
                    raise ValueError(f"{item['id']}/{box['name']}: UV 大于贴图")
                if x + width >= size:
                    x, y, row_h = 1, y + row_h + 1, 0
                if y + height >= size:
                    raise ValueError(f"{spec['id']}: UV 图集已满，请增大 texture_size")
                box["uv"] = [x, y]
                packed[key] = box["uv"]
                seed = int.from_bytes(hashlib.sha256(box["name"].encode()).digest()[:2], "big")
                for _, rect in cube_faces(box["size"], box["uv"], density):
                    u0,v0,u1,v1 = rect
                    left,top = math.floor(min(u0,u1)), math.floor(min(v0,v1))
                    right,bottom = math.ceil(max(u0,u1)), math.ceil(max(v0,v1))
                    for py in range(top, bottom):
                        for px in range(left, right):
                            pixels[py][px] = surface(materials[box["material"]], px-left, py-top,
                                                     right-left,bottom-top,seed)
                # Fit/material are authored semantics; runtime needs only baked geometry.
                boxes.append({k: box[k] for k in ("name","bone","from","size","uv","pivot","rotation") if k in box})
                x += width + 1
                row_h = max(row_h, height)
            pieces.append({k:item[k] for k in ("id","name","slot")} | {"boxes": boxes})
        profiles[profile] = pieces
    model = {"version":1,"id":spec["id"],"texture_size":size,"texel_density":density,
             "texture":f"myvillage:textures/equipment/{spec['id'].split(':')[1]}.png", "profiles":profiles}
    return model, pixels


def outputs(root: Path = ROOT):
    specs, materials = load(root)
    resources = root / "src/main/resources"
    assets = resources / "assets/myvillage"
    out, entries, names = {}, [], {}
    for spec in specs:
        if spec["kind"] != "armor_set" or not spec.get("enabled", True):
            continue
        model, pixels = compile_armor(spec, materials)
        name = spec["id"].split(":")[1]
        model_ref = f"myvillage:equipment/{name}.json"
        out[assets / f"equipment/{name}.json"] = json_bytes(model)
        out[assets / f"textures/equipment/{name}.png"] = encode_png(pixels)
        # Icons are rendered from the compiled geometry, not separately authored silhouettes.
        from .preview import render_icon
        for item in spec["items"]:
            item_name = item["id"].split(":")[1]
            entries.append({k:item[k] for k in ("id","slot")} | {
                "material":spec["material"],"durability_multiplier":spec["durability_multiplier"],
                "model":model_ref,"texture":model["texture"]})
            names[f"item.myvillage.{item_name}"] = item["name"]
            out[assets / f"textures/item/{item_name}.png"] = render_icon(model, pixels, item["id"])
            out[assets / f"models/item/{item_name}.json"] = json_bytes({
                "parent":"minecraft:item/generated", "textures":{"layer0":f"myvillage:item/{item_name}"}})
        for slot, tag in (("head","head_armor"),("chest","chest_armor"),("legs","leg_armor"),("feet","foot_armor")):
            path = resources / f"data/minecraft/tags/item/{tag}.json"
            current = json.loads(out.get(path, b'{"replace":false,"values":[]}'))
            current["values"] += [i["id"] for i in spec["items"] if i["slot"] == slot]
            out[path] = json_bytes(current)
    out[resources / "data/myvillage/equipment/catalogue.json"] = json_bytes({"version":1,"items":entries})
    # Vanilla trimmable_armor includes the four slot tags transitively. NeoForge's remove
    # entries keep ordinary slot/enchantment compatibility without applying vanilla trim UV
    # to our independent atlas. Never replace the vanilla tag or remove other armor.
    out[resources / "data/minecraft/tags/item/trimmable_armor.json"] = json_bytes({
        "replace":False,"values":[],"remove":[entry["id"] for entry in entries]})
    # Patch owned keys only, keeping all existing translations and other generators' ownership.
    lang = assets / "lang/zh_cn.json"
    translations = json.loads(lang.read_text())
    translations.update(names)
    out[lang] = json_bytes(translations)
    return out


def write_or_check(root=ROOT, check=False):
    rendered = outputs(root)
    catalogue = root / "src/main/resources/data/myvillage/equipment/catalogue.json"
    if catalogue.is_file():
        old_ids = {item['id'] for item in json.loads(catalogue.read_text())['items']}
        new_ids = {item['id'] for item in json.loads(rendered[catalogue])['items']}
        if old_ids - new_ids:
            raise ValueError("不能通过关闭草稿或改名删除已注册装备 ID：" + ', '.join(sorted(old_ids-new_ids)))
    changed = []
    for path, data in rendered.items():
        if not path.is_file() or path.read_bytes() != data:
            changed.append(str(path.relative_to(root)))
            if not check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    return changed
