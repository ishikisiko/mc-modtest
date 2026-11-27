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
    """Metal-specific relief and inclusions, in repeating texture coordinates."""
    slow = np.sin(u * np.pi / 16 + .33 * np.cos(v * np.pi / 8))
    flow = np.cos(v * np.pi / 16 + .55 * np.sin(u * np.pi / 16))
    disturbance = .6 * slow + .48 * flow + .26 * np.sin((u + v) * np.pi / 8)

    def hammered():
        pit = np.zeros_like(slow)
        for x, y, width, length in (
            (5, 9, 4, 2), (12, 7, 4, 3), (11, 24, 4, 2), (25, 10, 3, 2),
            (27, 23, 4, 4), (20, 27, 2, 3), (17, 16, 4, 2), (6, 18, 2, 4),
        ):
            if end:
                x, y = y, x
            strike = ((u - x) / width) ** 2 + ((v - y) / length) ** 2
            pit += np.clip(1 - strike, 0, 1)
        return -.34 * pit + .19 * disturbance, np.clip(pit - .42, 0, .5)

    def welded():
        seam = (u + (v / 6 if end else v / 11) + .9 * flow + 8) % 23
        groove = np.maximum(0, 1 - np.minimum(seam, 23 - seam) / 1.65)
        oxide = np.clip(slow + .38, 0, 1) * np.maximum(0, 1 - np.abs(seam - 4) / 5)
        return .23 * disturbance - .30 * groove, oxide

    def veined():
        x, y = (v, u) if end else (u, v)
        core = np.abs(x - (5.2 + .58 * y + 1.31 * np.sin(y * .38)))
        fork = np.abs(x - (15.3 + .19 * y + .8 * np.cos(y * .51)))
        veins = np.maximum(0, 1 - core / 1.4) * np.clip(flow + .73, .10, 1)
        veins += np.maximum(0, 1 - fork / 1.2) * (y > 14) * .4
        return .25 * disturbance - .22 * veins, veins

    def folds():
        shear = (u + v * (1 if end else .5) + 1.55 * flow + 2.4 * slow) * np.pi / 7
        folds = np.sin(shear) * (.51 + .46 * flow)
        return .27 * folds + .15 * disturbance, np.clip(folds - .51, 0, 1)

    def patina():
        oxide = np.clip(1.6 * disturbance - .41, 0, 1)
        return .18 * disturbance - .28 * oxide, oxide

    def strata():
        grain = (v + (u / (3 if end else 12)) + 1.5 * slow + .9 * flow) / 1.51
        strata = np.sin(grain) + .39 * np.sin(grain * 2.46) * slow
        return .27 * strata + .17 * disturbance, np.clip(strata - .65, 0, .9)

    def cloud():
        mist = np.sin(u * np.pi / 16 + .78 * flow + v * np.pi / (16 if end else 32))
        return .19 * mist + .15 * disturbance, np.clip(mist - .25, 0, 1)

    def satin():
        gradient = np.sin((v + u / (2 if end else 5)) * np.pi / 14 + .6 * slow)
        return .16 * gradient + .16 * disturbance, np.clip(flow + slow - 1.1, 0, .65)

    def scored():
        score = (u - v * (1 if end else .45) + 1.9 * flow) % 18
        crack = np.maximum(0, 1 - np.minimum(score, 18 - score) / 1.6)
        crack *= np.clip(slow + .37, 0, 1)
        return .19 * disturbance - .31 * crack, np.clip(crack - .4, 0, .45)

    def tempered():
        temper = np.cos((v if end else u) * np.pi / 16 + .7 * flow) * .63 + .37 * slow
        return .22 * temper + .14 * disturbance, np.clip(temper - .38, 0, 1)

    def brushed():
        along_grain = u + v / (4 if end else 23)
        fine = np.sin(along_grain * 2.28 + .8 * flow)
        streak = np.sin(along_grain * 1.16 - .5 * slow) * (.57 + .43 * flow)
        return .06 * fine + .05 * streak + .18 * flow + .12 * disturbance, np.clip(slow - .64, 0, 1)

    def lightning():
        x, y = (v, u) if end else (u, v)
        vein = 7.6 + y * .51 + 1.8 * np.sin(y / 3) + np.sin(y * 1.21)
        crack = np.clip(1 - np.abs(x - vein) / 1.35, 0, 1) * np.clip(flow + .55, .19, 1)
        return .29 * disturbance - .26 * crack, crack

    def stars():
        marks = (u * 19 + v * 37 + u * v * 7 + 13 * int(end)) % 97 == 0
        nebula = np.sin((u - v / 2) * np.pi / 16 + 1.1 * slow)
        return .17 * disturbance + .13 * nebula, .85 * marks + .12 * np.clip(nebula, 0, 1)

    def spectrum():
        streak = np.sin((v - u / (2 if end else 4)) * np.pi / 13 + .51 * slow)
        return .19 * streak + .14 * disturbance, .65 * np.clip(.64 + disturbance * .33, 0, 1)

    def woodgrain():
        x, y = (v, u) if end else (u, v)
        grain = x + 1.9 * np.sin(y * np.pi / 16) + .6 * np.cos(x / 3 + y * np.pi / 8)
        grain = np.sin(grain * 1.27) * (.5 + .5 * flow) + .37 * np.sin(grain * .45)
        return .29 * grain + .15 * disturbance, np.clip(grain - .75, 0, .8)

    def contours():
        polish = np.sin((v + u / (1.7 if end else 7)) * np.pi / 13 + .53 * slow)
        return .20 * polish + .18 * disturbance, np.clip(polish - .56, 0, 1)

    def inclusions():
        alloy = np.cos(u * np.pi / 16 + 1.1 * flow + .61 * slow)
        flecks = ((u * 43 + v * 23 + u * v * 3 + 17 * int(end)) % 83 == 0) & (alloy > -.2)
        return .19 * alloy + .17 * disturbance, flecks

    def cooling_flow():
        cooling = np.sin((u / (1.8 if end else 3.4) + v) / 3.3 + 1.39 * slow + .93 * flow)
        return .22 * cooling + .16 * disturbance, np.clip(cooling - .28, 0, 1)

    return {fn.__name__: fn for fn in (hammered, welded, veined, folds, patina, strata, cloud, satin,
            scored, tempered, brushed, lightning, stars, spectrum, woodgrain, contours, inclusions, cooling_flow)}


def surface(spec, end):
    """Return game albedo and a height field for the independently lit preview."""
    v, u = np.mgrid[:SIZE, :SIZE]
    shade, base, light, accent = (rgb(value) for value in spec["colors"])
    relief, inlay = slabs(u, v, end)[spec["pattern"]]()
    smooth = spec["roughness"] <= .42
    grain = ((u * 17 + v * 29 + u * v * 11 + int(end) * 7) % 29 - 14) / (1020 if smooth else 490)
    brushing = np.sin((u + v / (4 if end else 18)) * 1.31 + .42 * np.sin(v / 4))
    large = np.cos((v + .31 * u) * np.pi / 16) * np.sin(u * np.pi / 17)
    height = .54 + (.20 if smooth else .56) * relief + grain * (.48 if smooth else 1.3)
    slope = (height - np.roll(height, 1, axis=0)) + (height - np.roll(height, 1, axis=1)) * .47
    tone = np.clip(.51 + grain + (.026 if smooth else .047) * brushing + .028 * large
                   + (.40 if smooth else .58) * relief + .42 * slope, .08, .95)
    col = np.where(tone[..., None] < .5,
                   shade + (base - shade) * (tone * 2)[..., None],
                   base + (light - base) * ((tone - .5) * 2)[..., None])
    weight = (np.clip(inlay, 0, 1) * (.14 if smooth else .30))[..., None]
    if spec["pattern"] == "spectrum":
        wave = np.sin(v * np.pi / 13 - u * np.pi / 31 + np.sin(u / 5))
        radiance = np.stack((170 + 19 * wave, 155 - 9 * wave, 162 + 12 * np.cos(wave * 2)), axis=-1)
        col = col * (1 - weight) + radiance * weight
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
