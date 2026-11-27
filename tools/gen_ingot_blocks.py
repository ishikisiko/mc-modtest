"""Export storage blocks for all registered Yuanxian ingots from their authored finishes."""
from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import re

import numpy as np
from PIL import Image

try:
    from tools.language_assets import language_text, load_language
except ModuleNotFoundError:
    from language_assets import language_text, load_language


ROOT = Path(__file__).resolve().parents[1]
AUTHOR = Path("equipment/ingot_blocks.json")
ASSETS = Path("src/main/resources/assets/yuanxian")
DATA = Path("src/main/resources/data/yuanxian")
NAMESPACE = "yuanxian:"
SIZE = 32


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def registered_ingots(root):
    source = (root / "src/main/java/com/example/myvillage/item/ModItems.java").read_text(encoding="utf-8")
    direct = re.findall(r'ITEMS\.register(?:Item|SimpleItem)\(\s*"([a-z0-9_]+_ingot)"', source)
    generated = [entry["ingot"]["id"].removeprefix(NAMESPACE)
                 for entry in read(root / DATA / "immortal_equipment/catalogue.json")["sets"]]
    ids = direct + generated
    if len(ids) != len(set(ids)) or any(not ingot.endswith("_ingot") for ingot in ids):
        raise ValueError("锭的登记重复或命名不合法")
    return ids


def finishes(root=ROOT):
    rows = read(root / AUTHOR)["materials"]
    ids = [name.removesuffix("_ingot") for name in registered_ingots(root)]
    if set(ids) != rows.keys():
        raise ValueError(f"锭块材质缺失或多余：{sorted(set(ids) ^ rows.keys())}")
    return {key: rows[key] for key in ids}


def rgb(text):
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", text):
        raise ValueError(text)
    return np.array([int(text[i:i + 2], 16) for i in (1, 3, 5)], dtype=float)


def slabs(u, v, end):
    """The casting profiles use their own relief and inlay masks."""
    empty = np.zeros(u.shape)
    edge = np.minimum(u, v)
    center = np.maximum(0, 1 - edge / 12)

    def hammered():
        pit = np.zeros_like(empty)
        for x, y, scale in ((6, 9, 3), (11, 23, 3), (25, 11, 4), (23, 26, 3), (16, 15, 4)):
            pit += np.clip(1 - (np.abs(u - x) * .8 + np.abs(v - y)) / scale, 0, 1)
        return -.48 * pit, (u % 25 == 3) & (v % 25 == 3)

    def welded():
        seam = (u + (v // 6 if end else v // 9)) % 16
        return np.where(seam == 0, -.50, np.where(seam == 1, .38, 0)), (seam == 0) & (v % 8 < 3)

    def veined():
        crack = np.abs(u - (9 + (v * (7 if end else 3) // 9) % 17))
        branch = np.abs(v - (11 + (u * 2 // 3) % 17))
        return -.48 * ((crack <= 1) | (branch == 0)), (crack == 0) | ((branch == 0) & (u > 13))

    def folds():
        band = (u // 8 + v // 8) % 2
        cut = (u % 8) + np.where(band == 0, v % 8, 7 - v % 8)
        return np.where(cut <= 2, .42, np.where(cut >= 12, -.39, .08 * band)), cut == 9

    def lattice():
        edge = np.minimum(u % 11, v % 11)
        return np.where(edge == 0, -.55, np.where(edge == 1, .30, 0)), (u % 11 == 2) & (v % 11 < 4)

    def strata():
        field = (v + (u // (8 if end else 15))) % 11
        return ((field < 3) - (field > 8).astype(int)) * .42, (field == 3) & (u % 16 > 5)

    def cloud():
        bend = (u + 3 * np.abs((v % 16) - 8) // 4) % 14
        return np.where(bend == 1, -.30, np.where(bend == 2, .30, .14 * np.sin(u / 3 + v / 4))), bend == 2

    def frame():
        rim = np.minimum(edge, SIZE - 1 - np.maximum(u, v))
        band = (rim == 5) | ((rim == 9) & ((u + v) % 12 < 7))
        return np.where(band, -.36, np.where(rim == 6, .41, 0)), band & ((u % 30 >= 6) & (v % 30 >= 6))

    def dovetail():
        stagger = (u + 4 * (v // 8 % 2)) % 16
        groove = (v % 8 == 0) | (np.abs(stagger - 7) == v % 8)
        return np.where(groove, -.49, .09 * ((u // 8 + v // 8) % 2)), groove & (v % 8 == 0) & (u % 16 < 3)

    def sun():
        x, y = u - 15.5, v - 15.5
        ring = np.hypot(x, y)
        crest = (ring > 7.1) & (ring < 8.6 if end else ring < 9.4)
        ray = (ring > 10) & (ring < 13) & ((np.abs(x) < 1) | (np.abs(y) < 1) | (np.abs(np.abs(x) - np.abs(y)) < .8))
        return np.where(crest | ray, .39, np.where(ring < 6, -.22, 0)), crest | ray

    def moon():
        r = (u - 15) ** 2 + (v - 16) ** 2
        off = (u - (20 if end else 19)) ** 2 + (v - 13) ** 2
        cut = (r < 100) & (off > 64) & (r > 46)
        return np.where(cut, -.39, .16 * np.sin(u / 2 + v / 5)), cut & (r > 72)

    def lightning():
        vein = 6 + (v // 4) * 2 + (v % 7 < 3) * 3
        cracks = (u == vein) | ((v > 12) & (v < 20) & (u == vein + v - 13))
        if end:
            cracks |= (v == 11 + u // 4) & (u > 12)
        return np.where(cracks, -.53, .16 * np.cos((u - v) / 4)), cracks

    def stars():
        marks = ((u * 19 + v * 23 + u * v * 7) % 37 == 0) & (edge > 2)
        lane = np.abs((u * (2 if end else 1) - v) % 12 - 5) <= 1
        return .28 * marks + .15 * lane, marks | (lane & ((u + v * 3) % 23 == 0))

    def spectrum():
        streak = (v - u // (3 if end else 7)) % 14
        return .19 * (streak < 5) - .22 * (streak >= 11), (streak < 5) & (u % 12 > 4)

    def woodgrain():
        branch = np.abs(u - 16 - (v - 16) // 4)
        vein = (branch < 2) | ((branch < 10) & ((v + (branch * 2)) % 10 < 2))
        return np.where(vein, -.41, .14 * np.sin(u * .9 + v / 11)), vein & (v % 8 <= 2)

    def contours():
        r = np.abs(u - 15.5) + (1 if end else .72) * np.abs(v - 15.5)
        contour = (r % 6 > 4.8) & (r < 24)
        return np.where(contour, -.30, .14 * (r % 6 < 2)), contour

    def insets():
        rim = np.minimum(edge, SIZE - 1 - np.maximum(u, v))
        marks = (rim == 4) & (((u + v + (3 if end else 0)) % 14) < 6)
        marks |= (v % 13 == 6) & (u % 12 >= 4) & (u % 12 < 8)
        return np.where(marks, -.40, .14 * center), marks

    def rings():
        r = np.hypot(u - (16 if end else 13), v - 16)
        waves = (np.floor(r).astype(int) + (u // 10)) % 5
        return .20 * np.cos(r * 1.3) - .25 * (waves == 0), (waves == 1) & ((u + v) % 13 < 5)

    return {fn.__name__: fn for fn in (hammered, welded, veined, folds, lattice, strata, cloud, frame,
            dovetail, sun, moon, lightning, stars, spectrum, woodgrain, contours, insets, rings)}


def surface(spec, end):
    """Return game albedo and a height field for the independently lit preview."""
    v, u = np.mgrid[:SIZE, :SIZE]
    shade, base, light, accent = (rgb(value) for value in spec["colors"])
    relief, inlay = slabs(u, v, end)[spec["pattern"]]()
    grain = ((u * 17 + v * 29 + u * v * 11 + int(end) * 7) % 29 - 14) / 240
    brush = .06 * np.sin((u + v / (3 if end else 14)) * .94) + .045 * np.cos((v // 2) * .53)
    inset = np.minimum.reduce([u, v, SIZE - 1 - u, SIZE - 1 - v])
    ridge = np.where(inset == 0, -.43, np.where(inset == 1, .36, 0))
    height = .55 + .36 * relief + .43 * ridge + grain * .55
    side_light = (height - np.roll(height, 1, axis=0)) + (height - np.roll(height, 1, axis=1)) * .42
    tone = np.clip(.48 + grain + brush + .39 * relief + side_light * .76 + ridge * .24, .04, .98)
    col = np.where(tone[..., None] < .5,
                   shade + (base - shade) * (tone * 2)[..., None],
                   base + (light - base) * ((tone - .5) * 2)[..., None])
    weight = np.where(inlay, .35 if spec["pattern"] == "contours" else .55, 0)[..., None]
    if spec["pattern"] == "spectrum":
        stripe = ((v - u // 4) // 4) % 3
        tint = np.array([[173, 136, 154], [132, 157, 158], [190, 171, 122]])
        col = col * (1 - weight) + tint[stripe] * weight
    else:
        col = col * (1 - weight) + accent * weight
    return Image.fromarray(np.rint(np.clip(col, 0, 255)).astype(np.uint8), "RGB"), height


def png(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def outputs(root=ROOT):
    generated = {}
    assets, data = root / ASSETS, root / DATA
    lang = assets / "lang/zh_cn.json"
    original = load_language(lang.read_text(encoding="utf-8"))
    strings = dict(original)
    block_ids = []

    def put(path, value):
        generated[path] = encoded(value)

    for stem, spec in finishes(root).items():
        ingot = f"{stem}_ingot"
        block = f"{stem}_block"
        block_ids.append(NAMESPACE + block)
        strings[f"block.yuanxian.{block}"] = strings[f"item.yuanxian.{ingot}"].removesuffix("锭") + "块"
        for face, end in (("side", False), ("end", True)):
            generated[assets / f"textures/block/ingot_blocks/{stem}_{face}.png"] = png(surface(spec, end)[0])
        put(assets / f"blockstates/{block}.json", {"variants": {"": {"model": f"{NAMESPACE}block/{block}"}}})
        top = f"{NAMESPACE}block/ingot_blocks/{stem}_end"
        put(assets / f"models/block/{block}.json", {"parent": "minecraft:block/cube_bottom_top",
            "textures": {"bottom": top, "top": top, "side": f"{NAMESPACE}block/ingot_blocks/{stem}_side"}})
        put(assets / f"models/item/{block}.json", {"parent": f"{NAMESPACE}block/{block}"})
        put(data / f"loot_table/blocks/{block}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": NAMESPACE + block}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        unpack = f"{ingot}_from_{block}"
        put(data / f"recipe/{block}.json", {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": ["III", "III", "III"], "key": {"I": {"item": NAMESPACE + ingot}},
            "result": {"id": NAMESPACE + block, "count": 1}})
        put(data / f"recipe/{unpack}.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": NAMESPACE + block}], "result": {"id": NAMESPACE + ingot, "count": 9}})
        for recipe, ingredient, category in ((block, ingot, "building_blocks"), (unpack, block, "misc")):
            recipe_id = NAMESPACE + recipe
            put(data / f"advancement/recipes/{category}/{recipe}.json", {"parent": "minecraft:recipes/root",
                "criteria": {"has_material": {"trigger": "minecraft:inventory_changed",
                    "conditions": {"items": [{"items": NAMESPACE + ingredient}]}},
                    "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe_id}}},
                "requirements": [["has_material", "has_recipe"]], "rewards": {"recipes": [recipe_id]}})

    for kind in ("block", "item"):
        put(data / f"tags/{kind}/ingot_blocks.json", {"replace": False, "values": block_ids})
        put(root / f"src/main/resources/data/c/tags/{kind}/storage_blocks.json",
            {"replace": False, "values": ["#yuanxian:ingot_blocks"]})
    generated[lang] = language_text(original, strings).encode()
    return generated


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    changed = []
    for path, content in outputs().items():
        if args.check:
            if not path.is_file() or path.read_bytes() != content:
                changed.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if changed:
        raise SystemExit("锭块资源漂移\n" + "\n".join(changed))
    print(f"{len(finishes())} 种锭块资源已检查" if args.check else f"{len(finishes())} 种锭块资源已生成")


if __name__ == "__main__":
    main()
