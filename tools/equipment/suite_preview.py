"""读取实际穿戴JSON、OBJ和GUI分支，按固定比例生成整套审阅图。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from .catalogue import ROOT
from .preview import TO_WORLD, rotation, mannequin, scene
from ..combat_preview import item_model as im

SLOTS = ("helmet", "chestplate", "leggings", "boots")
VIEWS = {"front": (0, .05, -1), "three-quarter": (-.7, .16, -1),
         "side": (-1, .05, 0), "back": (0, .05, 1)}
FONT = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"


def designs(root=ROOT):
    return [{"id": "xuantie", "name": "玄铁墨行"}] + [d
        for n in range(1, 6) for d in json.loads((root /
        f"art/immortal-armor-atlas/levels/level{n}.json").read_text())]


def texture(assets, ref):
    return np.asarray(Image.open(assets / ref.split(":", 1)[1]).convert("RGBA"), float) / 255


def wearing(assets, set_id, profile="standard", pose="standing", root=ROOT):
    if set_id == "xuantie":
        model = json.loads((assets / "equipment/xuantie_armor.json").read_text())
        pixels = Image.open(assets / model["texture"].split(":", 1)[1]).convert("RGBA")
        return scene(model, pixels, profile, pose, root=root)
    model = json.loads((assets / f"immortal_equipment/{set_id}.json").read_text())
    config = json.loads((root / "equipment/poses.json").read_text())
    transforms = {}
    for bone, origin in model["profiles"][profile]["bone_origins"].items():
        override = config["poses"][pose]["bones"].get(bone, {})
        transforms[bone] = im.affine(rotation(override.get("rotation", [0, 0, 0])),
                                    override.get("position", origin))
    quads = mannequin(profile, transforms)
    textures = {key: texture(assets, mat["texture"]) for key, mat in model["materials"].items()}
    for part in model["profiles"][profile]["parts"]:
        vertices = np.asarray(part["vertices"])
        mat = TO_WORLD @ transforms[part["bone"]]
        points = vertices[:, :3] @ mat[:3, :3].T + mat[:3, 3]
        for i in range(0, len(part["indices"]), 3):
            ix = part["indices"][i:i+3]
            ix = ix + [ix[-1]]
            quads.append(im.Quad(points[ix], (vertices[ix, 3:5]*16).tolist(),
                                 textures[part["material"]], tag=part["id"]))
    return quads


def read_obj(assets, model):
    base = model.get("base", model)
    obj = assets / base["model"].split(":", 1)[1]
    materials = {}
    for line in obj.with_suffix(".mtl").read_text().splitlines():
        bits = line.split()
        if not bits:
            continue
        if bits[0] == "newmtl":
            current = bits[1]
        elif bits[0] == "map_Kd":
            materials[current] = texture(assets, "myvillage:textures/" + bits[1].split(":", 1)[1] + ".png")
    vertices, uv, quads = [], [], []
    for line in obj.read_text().splitlines():
        bits = line.split()
        if not bits:
            continue
        if bits[0] == "v":
            vertices.append([float(v)*16 for v in bits[1:]])
        elif bits[0] == "vt":
            uv.append([float(v)*16 for v in bits[1:]])
        elif bits[0] == "usemtl":
            current = bits[1]
        elif bits[0] == "f":
            ix = [[int(v)-1 for v in bit.split("/")] for bit in bits[1:]]
            if len(ix) == 3:
                ix.append(ix[-1])
            quads.append(im.Quad([vertices[v[0]] for v in ix], [uv[v[1]] for v in ix],
                                 materials[current], shade=base.get("shade_quads", True)))
    return base, quads


def gui(assets, item, size):
    model = json.loads((assets / f"models/item/{item}.json").read_text())
    variant = model.get("perspectives", {}).get("gui")
    if variant:
        ref = variant["textures"]["layer0"]
        pic = Image.open(assets / ("textures/"+ref.split(":", 1)[1]+".png")).convert("RGBA")
        bg = Image.new("RGBA", (size, size), (126, 126, 126, 255))
        bg.alpha_composite(pic.resize((size, size), Image.Resampling.NEAREST))
        return bg.convert("RGB")
    base, quads = read_obj(assets, model)
    quads = im.transform_quads(quads, im.display_matrix(base["display"]["gui"]))
    return im.render(quads, im.Camera((0, 0, 1)), size, scale=size, center=(0, 0, 0),
                     bg=(146, 146, 146), ss=1, cull=False)[0]


def models(assets, out, views, size=288):
    out.mkdir(parents=True, exist_ok=True)
    ds = designs()
    font = ImageFont.truetype(FONT, 18)
    contact = Image.new("RGB", (len(views)*size*2, 8*(size+42)), "#ebe9e2")
    draw = ImageDraw.Draw(contact)
    for i, spec in enumerate(ds):
        quads = wearing(assets, spec["id"])
        x, y = (i % 2)*size*len(views), (i//2)*(size+42)
        draw.text((x+12, y+10), spec["name"], font=font, fill="#242b31")
        for j, view in enumerate(views):
            pic = im.render(quads, im.Camera(VIEWS[view]), size, scale=size/44,
                            center=(0, 19, 0), bg=(199, 204, 207), ss=2, cull=False)[0]
            pic.save(out/f"{spec['id']}-{view}.png")
            contact.paste(pic, (x+j*size, y+40))
        print(spec["id"], "实际整套穿戴", flush=True)
    contact.save(out/"all16.png")


def gui_comparisons(before, after, out, root=ROOT):
    """实际GUI分支的16/32格分别取样，16格再最近邻放大六倍。"""
    out.mkdir(parents=True, exist_ok=True)
    font = ImageFont.truetype(FONT, 17)
    small = ImageFont.truetype(FONT, 14)
    contact = Image.new("RGB", (1000, 16*144+48), "#eeece5")
    cd = ImageDraw.Draw(contact)
    cd.text((12, 12), "16套四槽GUI：上排为旧资源，下排为候选。固定16格取样后最近邻放大。", font=font, fill="#222b31")
    for i, spec in enumerate(designs(root)):
        sheet = Image.new("RGB", (928, 440), "#eeece5")
        d = ImageDraw.Draw(sheet)
        d.text((16, 10), spec["name"]+"：四件GUI新旧对照", font=font, fill="#222b31")
        for j, (suffix, label) in enumerate(zip(SLOTS, ("头部", "胸部", "下装", "靴子"))):
            x = 24+j*228
            d.text((x, 42), label, font=font, fill="#222b31")
            for phase, assets, y in (("旧资源", before, 70), ("候选", after, 250)):
                d.text((x, y), phase, font=small, fill="#414950")
                item = f"{spec['id']}_{suffix}"
                native16, native32 = gui(assets, item, 16), gui(assets, item, 32)
                for pic, dx, dy, text in ((native16, 4, 50, "16格"), (native32, 50, 42, "32格"),
                                          (native16.resize((96, 96), Image.Resampling.NEAREST), 110, 26, "16格×6")):
                    sheet.paste(pic, (x+dx, y+dy))
                    d.text((x+dx, y+128), text, font=small, fill="#414950")
                native16.resize((64, 64), Image.Resampling.NEAREST).save(out/f"{item}-{'before' if assets==before else 'after'}-zoom.png")
                cx, cy = 170+j*180, 48+i*144+(0 if assets==before else 68)
                contact.paste(native16.resize((64, 64), Image.Resampling.NEAREST), (cx, cy))
                contact.paste(native32, (cx+74, cy+16))
                contact.paste(native16, (cx+120, cy+24))
        cd.text((12, 48+i*144+44), spec["name"], font=font, fill="#222b31")
        sheet.save(out/f"{spec['id']}-gui.png")
        print(spec["id"], "四槽16/32格新旧对照", flush=True)
    contact.save(out/"all16-gui-old-new.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=ROOT/"src/main/resources/assets/myvillage")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--views", default="front,three-quarter,side,back")
    parser.add_argument("--gui-before", type=Path, help="仅生成四槽GUI对照，指定旧资源目录")
    args = parser.parse_args()
    if args.gui_before:
        gui_comparisons(args.gui_before, args.assets, args.out)
    else:
        models(args.assets, args.out, args.views.split(","))


if __name__ == "__main__":
    main()
