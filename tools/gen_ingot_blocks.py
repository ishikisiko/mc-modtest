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
ART = AUTHOR.with_suffix("")
ASSETS = Path("src/main/resources/assets/yuanxian")
DATA = Path("src/main/resources/data/yuanxian")
NAMESPACE = "yuanxian:"
SIZE = 16


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
    if sorted(row["row"] for row in rows.values()) != list(range(len(rows))):
        raise ValueError("锭块图集行号必须连续且唯一")
    return {key: rows[key] for key in ids}


def load_atlases(root, rows):
    atlases = []
    for name in ("albedo", "normal", "surface"):
        path = root / ART / f"{name}.png"
        with Image.open(path) as image:
            if image.mode != "RGB" or image.size != (2 * SIZE, rows * SIZE):
                raise ValueError(f"锭块图集尺寸或通道格式错误：{path}")
            atlases.append(np.array(image))
    return atlases


def pbr_textures(albedo, tangent_normal, material):
    """Convert the reviewed RGB, tangent normals, and roughness/metalness to LabPBR."""
    normal = np.full_like(albedo, 255)
    normal[..., 0] = tangent_normal[..., 0]
    # The reviewed normals are Y-up, while LabPBR stores the Y-down component.
    normal[..., 1] = 255 - tangent_normal[..., 1]

    specular = np.zeros_like(albedo)
    # The preview's GGX shader squares its roughness, so invert the stored
    # perceptual roughness directly. Use the albedo as F0 for the custom alloys.
    specular[..., 0] = 255 - material[..., 0]
    specular[..., 1] = np.where(material[..., 1] >= 128, 255, 10)
    # RGB-only maps supply LabPBR's default alpha (no displacement or emission).
    return (("", albedo), ("_n", normal), ("_s", specular))


def png(pixels):
    buffer = io.BytesIO()
    Image.fromarray(pixels).save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def outputs(root=ROOT):
    generated = {}
    assets, data = root / ASSETS, root / DATA
    lang = assets / "lang/zh_cn.json"
    original = load_language(lang.read_text(encoding="utf-8"))
    strings = dict(original)
    block_ids = []
    materials = finishes(root)
    atlases = load_atlases(root, len(materials))

    def put(path, value):
        generated[path] = encoded(value)

    for stem, spec in materials.items():
        ingot = f"{stem}_ingot"
        block = f"{stem}_block"
        block_ids.append(NAMESPACE + block)
        strings[f"block.yuanxian.{block}"] = strings[f"item.yuanxian.{ingot}"].removesuffix("锭") + "块"
        start = spec["row"] * SIZE
        for column, face in enumerate(("side", "end")):
            x = column * SIZE
            tiles = [atlas[start:start + SIZE, x:x + SIZE] for atlas in atlases]
            for suffix, pixels in pbr_textures(*tiles):
                path = assets / f"textures/block/ingot_blocks/{stem}_{face}{suffix}.png"
                generated[path] = png(pixels)
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
