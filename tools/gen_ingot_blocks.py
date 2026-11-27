"""Export storage blocks for all registered Yuanxian ingots from their authored finishes."""
from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import re

import numpy as np
from PIL import Image, ImageDraw

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


MARKS = {
    # Albedo offset, highlight on the lower lip, and accent blend.
    "brush": (.045, 0, 0),
    "nick": (-.125, .074, 0),
    "seam": (-.128, .115, .05),
    "pit": (-.104, .138, 0),
    "oxide": (-.034, 0, .52),
    "inclusion": (.060, 0, .56),
}


def mark_mask(kind, points):
    """Rasterize the authored path or chipped area at the actual texture size."""
    mask = Image.new("1", (SIZE, SIZE))
    draw = ImageDraw.Draw(mask)
    coords = [tuple(point) for point in points]
    if len(coords) == 1:
        draw.point(coords[0], fill=1)
    elif kind in ("pit", "oxide"):
        draw.polygon(coords, fill=1)
    else:
        draw.line(coords, fill=1)
    return np.asarray(mask, dtype=bool)


def surface(spec, end):
    """Build the pixel albedo from the prepared metal face and its authored marks."""
    v, u = np.mgrid[:SIZE, :SIZE]
    shade, base, light, accent = (rgb(value) for value in spec["colors"])
    smooth = spec["roughness"] <= .42
    stepped = (v // 8) * .013
    # Randomness is confined to small-scale texture. Highlights and worked marks
    # have explicit placements and keep their shape in the inventory or on a roof.
    seed = int(np.sum(base)) * 2 + int(end)
    random = np.random.default_rng(seed)
    surface_noise = random.integers(-2, 3, size=(SIZE, SIZE)) * (.003 if smooth else .012)
    row_grain = random.integers(-2, 3, size=(SIZE, 8)).repeat(4, axis=1) * (.005 if smooth else .009)
    tone = (.526 if smooth else .49) - stepped + surface_noise + row_grain
    pigments = np.zeros((SIZE, SIZE))
    # The cut rim is one texel wide and disappears as it turns away from the light.
    if smooth:
        tone[0, 1:-2] += .165
        tone[1, 2:-4] += .045
        tone[-1, 2:-1] -= .068
        if end:
            tone[2:-3, 0] += .060
    else:
        tone[0, 2:14] += .069
        tone[0, 19:27] += .045
        tone[-1, 7:29] -= .045

    for kind, *points in spec["end" if end else "side"]:
        shift, rim, pigment = MARKS[kind]
        mark = mark_mask(kind, points)
        below = np.zeros_like(mark)
        below[1:] = mark[:-1] & ~mark[1:]
        tone[mark] += shift
        tone[below] += rim
        pigments[mark] = np.maximum(pigments[mark], pigment)

    tone = np.clip(tone, .08, .95)[..., None]
    color = np.where(tone < .5, shade + (base - shade) * (tone * 2),
                     base + (light - base) * ((tone - .5) * 2))
    weight = pigments[..., None]
    color = color * (1 - weight) + accent * weight
    return Image.fromarray(np.rint(color).astype(np.uint8), "RGB")


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
            generated[assets / f"textures/block/ingot_blocks/{stem}_{face}.png"] = png(surface(spec, end))
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
