#!/usr/bin/env python3
"""Original bronze alchemy array resources; --check checks drift, --preview renders models.

Authored palette/recipes live in tools/alchemy_array/visual.json. This generator
owns its six resource families and only merges its own Chinese names and tag members.
No external model, image, or running Minecraft is used by the offline preview.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import Canvas, encode, png

PARTS = ("bronze", "edge", "patina", "dark", "fire", "jade", "ceramic", "rivet")
KINDS = ("controller", "casing", "input", "output", "fuel", "vent")
DIRECTIONS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def config(root=ROOT):
    cfg = json.loads((Path(root) / "tools/alchemy_array/visual.json").read_text())
    if cfg["version"] != 1 or set(cfg["palette"]) != set(PARTS):
        raise ValueError("Alchemy array visual palette schema")
    if set(cfg["names"]) != set(KINDS) or set(cfg["recipes"]) != set(KINDS):
        raise ValueError("Alchemy array names/recipes must close all six block IDs")
    for color in cfg["palette"].values():
        if len(color) != 3 or any(type(v) is not int or not 0 <= v <= 255 for v in color):
            raise ValueError("Alchemy array RGB")
    return cfg


def block_id(kind):
    return "alchemy_array_" + kind


def texture(cfg):
    canvas = Canvas(64)
    for y in range(64):
        for x in range(64):
            part = PARTS[((y // 16) * 4 + x // 16) % len(PARTS)]
            u, v = x % 16, y % 16
            color = cfg["palette"][part]
            noise = ((u * 37 + v * 19 + u * v * 3) % 9) - 4
            if part in ("bronze", "edge"):
                noise += 8 if v in (1, 2) else -6 if v == 15 else 0
            elif part == "patina":
                noise += 12 if (u + v * 2) % 13 < 3 else 0
            elif part == "fire":
                color = (250, 95 + v * 7, 25 + v * 2)
                noise = 0
            elif part == "jade":
                noise += 12 if u == v or u + v == 15 else 0
            canvas.put(x, y, tuple(max(0, min(255, c + noise)) for c in color))
    return png(canvas.rows)


def uv(part):
    i = PARTS.index(part)
    x, y = (i % 4) * 4, (i // 4) * 4
    return [x, y, x + 4, y + 4]


def geometry(kind, lit=False):
    els = []

    def box(name, lo, hi, part="bronze", glow=False, rotate=None):
        faces = {f: {"texture": "#atlas", "uv": uv(part)}
                 for f in ("north", "south", "east", "west", "up", "down")}
        if glow:
            for face in faces.values():
                face["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        el = {"name": name, "from": lo, "to": hi, "faces": faces}
        if glow:
            el["shade"] = False
        if rotate:
            el["rotation"] = rotate
        els.append(el)

    # A nearly full tile with bevels and continuous green-gold bindings. Tiny
    # front ornaments do not add collision; the vent has its own raised cover.
    box("bronze_tile", [.35, .35, .35], [15.65, 15.65, 15.65])
    box("horizontal_bevel", [0, .8, .8], [16, 15.2, 15.2])
    box("depth_bevel", [.8, .8, 0], [15.2, 15.2, 16])
    box("vertical_bevel", [.8, 0, .8], [15.2, 16, 15.2])
    # Four faces share the same forged framing, including rotated port pieces.
    for z, sign in ((0, -1), (16, 1)):
        a, b = sorted((z, z + sign * .08))
        box("green_band_" + str(z), [.8, 10.25, a], [15.2, 12.1, b], "patina")
        for y in (1.1, 14.1):
            box(f"binding_{z}_{y}", [.8, y, a], [15.2, y + .65, b], "edge")
        for x in (1.2, 13.7):
            for y in (2.3, 12.7):
                ra, rb = sorted((z, z + sign * .18))
                box(f"rivet_{z}_{x}_{y}", [x, y, ra], [x + 1.1, y + 1.1, rb], "rivet")
    for x, sign in ((0, -1), (16, 1)):
        a, b = sorted((x, x + sign * .08))
        box("green_side_" + str(x), [a, 10.25, .8], [b, 12.1, 15.2], "patina")
        for y in (1.1, 14.1):
            box(f"side_binding_{x}_{y}", [a, y, .8], [b, y + .65, 15.2], "edge")
        for z in (1.2, 13.7):
            for y in (2.3, 12.7):
                ra, rb = sorted((x, x + sign * .18))
                box(f"side_rivet_{x}_{z}_{y}", [ra, y, z], [rb, y + 1.1, z + 1.1], "rivet")

    if kind == "casing":
        return els
    # Functional panels cover the front bronze face; surfaces sit at successively
    # different depths to avoid coplanar overlap. Relief is at most .65 model px.
    if kind != "vent":
        box("face_mount", [3, 3.4, -.1], [13, 12.6, .3], "edge")
        box("recess", [3.7, 4.1, -.12], [12.3, 11.9, -.105], "dark")
    if kind == "controller":
        box("hearth_window", [4.6, 4.6, -.16], [11.4, 9.9, -.125], "fire" if lit else "dark", lit)
        for x in (5, 7.6, 10.2):
            box("hearth_bar_" + str(x), [x, 4.5, -.35], [x + .8, 10.1, -.08], "bronze")
        box("hearth_sill", [3.4, 3.6, -.4], [12.6, 4.5, -.05], "edge")
        box("hearth_header", [3.4, 10, -.4], [12.6, 11, -.05], "edge")
        box("jade_status", [7.2, 11.2, -.42], [8.8, 12.5, -.1], "jade" if lit else "patina", lit)
    elif kind == "input":
        # Mouth and tray: herb-green vertical slots above a pale receiving lip.
        for x in (4.4, 7.2, 10):
            box("herb_grille_" + str(x), [x, 6, -.35], [x + 1.3, 11, -.1], "patina")
        box("herb_tray", [3.7, 4.1, -.65], [12.3, 5.2, -.1], "edge")
        box("herb_tray_lining", [4.4, 5.2, -.6], [11.6, 5.6, -.1], "ceramic")
        box("herb_stem", [7.65, 11, -.44], [8.35, 13.8, -.1], "jade")
        box("herb_left_leaf", [5.8, 12, -.4], [7.65, 12.8, -.1], "jade")
        box("herb_right_leaf", [8.35, 12.8, -.4], [10.2, 13.6, -.1], "jade")
    elif kind == "output":
        box("pill_mouth", [5, 8.7, -.3], [11, 10.5, -.07], "ceramic")
        box("pill_trough", [4, 5, -.65], [12, 6.1, -.1], "patina")
        box("pill_left_lip", [3.7, 5, -.65], [4.5, 7.5, -.1], "edge")
        box("pill_right_lip", [11.5, 5, -.65], [12.3, 7.5, -.1], "edge")
        for x in (6, 9):
            box("pill_seal_" + str(x), [x, 11.3, -.3], [x + 1, 12.3, -.1], "ceramic")
    elif kind == "fuel":
        box("stone_slot", [5.2, 5.8, -.3], [10.8, 7, -.08], "edge")
        box("spirit_stone_emblem", [6.3, 8.3, -.35], [9.7, 11.7, -.1], "jade",
            rotate={"origin": [8, 10, 0], "axis": "z", "angle": 45})
        box("stone_lower_seal", [7.3, 4.4, -.3], [8.7, 5.3, -.08], "patina")
    elif kind == "vent":
        # The cover rises from the full-cube tile. Runtime defines the same upper
        # collision box; the horizontal FACING state does not rotate this model.
        box("vent_neck", [4, 16, 4], [12, 18, 12], "dark")
        for x in (4, 7.5, 11):
            box("vent_bar_" + str(x), [x, 16, 4], [x + 1, 18.8, 12], "edge")
        box("lower_lid", [3, 18.5, 3], [13, 19.3, 13], "edge")
        box("jade_lid", [3.5, 19.3, 3.5], [12.5, 20.1, 12.5], "patina")
        box("lid_step", [5, 20.1, 5], [11, 20.8, 11], "patina")
        box("knob_stem", [7, 20.8, 7], [9, 21.3, 9], "edge")
        box("knob", [6.3, 21.3, 6.3], [9.7, 22, 9.7], "jade" if lit else "patina", lit)
        for x in (3.6, 11.7):
            box("vent_ember_" + str(x), [x, 16.3, 6], [x + .6, 17.6, 10], "fire" if lit else "dark", lit)
    return els


def outputs(root=ROOT):
    root = Path(root)
    cfg = config(root)
    assets = root / "src/main/resources/assets/myvillage"
    data = root / "src/main/resources/data"
    result = {assets / "textures/block/alchemy_array.png": texture(cfg)}
    for kind in KINDS:
        bid = block_id(kind)
        for lit in (False, True) if kind in ("controller", "vent") else (False,):
            suffix = "_lit" if lit else ""
            result[assets / f"models/block/{bid}{suffix}.json"] = encode({
                "ambientocclusion": True,
                "textures": {"atlas": "myvillage:block/alchemy_array", "particle": "myvillage:block/alchemy_array"},
                "elements": geometry(kind, lit)})
        variants = {}
        for lit in (False, True):
            for direction, angle in DIRECTIONS:
                suffix = "_lit" if lit and kind in ("controller", "vent") else ""
                variants[f"facing={direction},lit={str(lit).lower()}"] = {
                    "model": f"myvillage:block/{bid}{suffix}", "y": 0 if kind == "vent" else angle}
        result[assets / f"blockstates/{bid}.json"] = encode({"variants": variants})
        result[assets / f"models/item/{bid}.json"] = encode({"parent": f"myvillage:block/{bid}", "display": {
            "gui": {"rotation": [25, 225, 0], "translation": [0, -1, 0], "scale": [.6, .6, .6]},
            "ground": {"translation": [0, 3, 0], "scale": [.3, .3, .3]},
            "fixed": {"rotation": [0, 180, 0], "scale": [.5, .5, .5]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "scale": [.4, .4, .4]},
            "firstperson_lefthand": {"rotation": [0, 45, 0], "scale": [.4, .4, .4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "scale": [.35, .35, .35]}}})
        result[data / f"myvillage/loot_table/blocks/{bid}.json"] = encode({"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"myvillage:{bid}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}], "random_sequence": f"myvillage:blocks/{bid}"})
        recipe = cfg["recipes"][kind]
        result[data / f"myvillage/recipe/{bid}.json"] = encode({"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": recipe["pattern"], "key": {k: {"tag" if v.startswith("#") else "item": v.removeprefix("#")}
            for k, v in recipe["key"].items()}, "result": {"id": f"myvillage:{bid}", "count": recipe["count"]}})
    result[data / "myvillage/tags/block/alchemy_array_components.json"] = encode({
        "replace": False, "values": [f"myvillage:{block_id(k)}" for k in KINDS]})
    result[data / "myvillage/tags/item/alchemy_array_components.json"] = result[data / "myvillage/tags/block/alchemy_array_components.json"]
    return result


def merged_outputs(root=ROOT):
    root = Path(root)
    cfg = config(root)
    lang_path = root / "src/main/resources/assets/myvillage/lang/zh_cn.json"
    lang = json.loads(lang_path.read_text()) if lang_path.exists() else {}
    lang.update({f"block.myvillage.{block_id(k)}": v for k, v in cfg["names"].items()})
    result = {lang_path: encode(lang)}
    for tag in ("mineable/pickaxe", "needs_stone_tool"):
        path = root / f"src/main/resources/data/minecraft/tags/block/{tag}.json"
        obj = json.loads(path.read_text()) if path.exists() else {"replace": False, "values": []}
        for kind in KINDS:
            value = f"myvillage:{block_id(kind)}"
            if value not in obj["values"]:
                obj["values"].append(value)
        result[path] = encode(obj)
    return result


def structure(root=ROOT):
    """Read the runtime's only authored structure, preserving its coordinates."""
    path = Path(root) / "src/main/resources/data/myvillage/alchemy/array.json"
    if not path.exists():
        raise ValueError("Runtime alchemy/array.json is required for the assembly preview")
    return json.loads(path.read_text())


def preview(root=ROOT):
    import math
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    from tools.combat_preview import item_model as pim
    from tools.beastgen.preview import Frame, render

    root = Path(root)
    out = root / "out/preview/alchemy_array"
    out.mkdir(parents=True, exist_ok=True)
    assets = pim.Assets([root / "src/main/resources"], None)
    cfg = config(root)
    cache = {}

    def quads(kind, lit=False, angle=0, offset=(0, 0, 0)):
        key = (kind, lit and kind in ("controller", "vent"))
        if key not in cache:
            ref = f"myvillage:block/{block_id(kind)}" + ("_lit" if key[1] else "")
            model = pim.resolve_model(assets, assets.model_json(ref), ref)
            if model.errors or model.warnings:
                raise ValueError(model.errors + model.warnings)
            qs = [q for i, el in enumerate(model.elements) for q in pim.element_quads(assets, model, el, i)]
            if model.errors:
                raise ValueError(model.errors)
            cache[key] = qs
        a = math.radians(angle)
        rot = np.array([[math.cos(a), 0, -math.sin(a)], [0, 1, 0], [math.sin(a), 0, math.cos(a)]])
        return [(((q.pos - np.array([8, 0, 8])) / 16) @ rot.T + np.array(offset), q.uv, q.tex, None)
                for q in cache[key]]

    font = ImageFont.truetype("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 23)
    sheet = Image.new("RGB", (1200, 860), (25, 34, 31))
    draw = ImageDraw.Draw(sheet)
    for i, kind in enumerate(KINDS):
        x, y = i % 3 * 400, i // 3 * 430
        im = render(quads(kind, kind in ("controller", "vent")), Frame((-.8, .6, -1.3), 400, 390, 245, (0, .65, 0), ss=1),
                    bg=(25, 34, 31), body_rot=180)
        im.save(out / f"component_{kind}.png")
        sheet.paste(im, (x, y))
        draw.text((x + 200, y + 397), cfg["names"][kind], font=font, fill=(232, 214, 171), anchor="mm")
    sheet.save(out / "components.png")
    # array.json schema is shared with the Java runtime (no duplicate layout).
    spec = structure(root)
    cells = spec["cells"]
    whole = []
    for cell in cells:
        kind = cell["role"]
        if kind == "air":
            continue
        offset = (cell["x"], cell["y"] + 1, cell["z"] - 1)
        # Outward local orientations match the runtime role semantics. Layout
        # positions themselves come only from the runtime's cells list.
        angle = {"input": 270, "output": 90, "fuel": 180}.get(kind, 0)
        whole.extend(quads(kind, True, angle, offset))
    for name, camera in (("front", (-1, .75, -1.3)), ("rear", (1, .75, 1.3)), ("top", (-.65, 1.6, -1.0))):
        render(whole, Frame(camera, 1000, 900, 200, (0, 1.7, 0), ss=1), bg=(25, 34, 31), body_rot=180).save(out / f"assembly_{name}.png")
    blue = Image.new("RGB", (1200, 510), (25, 34, 31))
    d = ImageDraw.Draw(blue)
    labels = {"casing": "炉壁", "controller": "炉心↓", "input": "←进药", "output": "出丹→", "fuel": "灵石↑", "vent": "排气盖", "air": "空气"}
    for column, level in enumerate((-1, 0, 1)):
        ox = column * 400 + 45
        d.text((column * 400 + 200, 30), ("底层", "中层", "顶层")[column], font=font, fill=(232, 214, 171), anchor="mm")
        for cell in cells:
            if cell["y"] != level:
                continue
            x, y = ox + (cell["x"] + 1) * 103, 90 + (2 - cell["z"]) * 103
            k = cell["role"]
            color = (53, 86, 73) if k not in ("casing", "air") else (84, 70, 46) if k == "casing" else (22, 27, 25)
            d.rectangle((x, y, x + 98, y + 98), fill=color, outline=(166, 143, 90), width=2)
            d.text((x + 49, y + 49), labels[k], font=font, fill=(236, 220, 181), anchor="mm")
        d.text((column * 400 + 200, 435), "前方 / 面向炉心 ↑", font=font, fill=(170, 192, 177), anchor="mm")
    blue.save(out / "blueprint.png")
    (out / "index.html").write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>青铜丹炉阵 · 模型预览</title><style>body{background:#19221f;color:#e8d6ab;font:17px system-ui;max-width:1200px;margin:28px auto;padding:0 16px}p{line-height:1.8;color:#b7c6b9}img{width:100%}.views{display:grid;grid-template-columns:1fr 1fr}figure{margin:8px}figcaption{padding:8px}a{color:#d5bd7e}</style><h1>青铜丹炉阵</h1><p>三乘三乘三 · 青铜铆接炉壁、铜绿箍带、火窗炉心、药叶进药口、承丹嘴、菱形灵石印、阶式排气盖。以下读取正式游戏模型与贴图离线渲染，结构读取运行时同一 array.json。不是游戏截图；粒子、光环旋转、实机光照、玩家视觉及手感保持 not_verified。</p><div class="views"><figure><img src="assembly_front.png"><figcaption>正面与进药口</figcaption></figure><figure><img src="assembly_rear.png"><figcaption>背面灵石口与出丹口</figcaption></figure></div><img src="assembly_top.png"><h2>六种独立组件</h2><img src="components.png"><h2>三层蓝图</h2><p>每层从上方看；上方为炉后，下方为炉前。中层中心保持空气，端口箭头向外。炉壁共二十一块，其余五个专用组件各一块。</p><img src="blueprint.png"></html>''', encoding="utf-8")
    print(f"Preview: {out / 'index.html'}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args(argv)
    files = {**outputs(args.root), **merged_outputs(args.root)}
    stale = []
    for path, data in files.items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                stale.append(str(path.relative_to(args.root)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if stale:
        print("Stale alchemy array resources:\n" + "\n".join(stale))
        return 1
    print(f"Alchemy array resources {'match' if args.check else 'generated'} ({len(files)} files).")
    if args.preview:
        preview(args.root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
