"""从灵膳配置和已选定图稿导出厨房资源。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import random
import shutil

from PIL import Image, ImageDraw

from tools.language_assets import language_text, load_language

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
RULES = ROOT / "src/main/resources/data/yuanxian/kitchen/rules.json"
NAMESPACE = "yuanxian"

MODEL_IDS = {
    "mill": "kitchen_mill",
    "board": "kitchen_board",
    "knife": "kitchen_knife",
    "stove": "kitchen_stove",
    "stove_lit": "kitchen_stove_lit",
}
FACES = ("down", "up", "north", "south", "west", "east")
TOOLTIPS = {
    "beef_noodles": "脱战时地面移速 +%s%%，持续 %s 分钟",
    "beef_stew": "用镐挖石材与矿石的速度 +%s%%，持续 %s 分钟",
    "pumpkin_pancake": "潜行移速 +%s%%，持续 %s 分钟",
    "carrot_eggs": "手动催熟原版作物时，%s%% 概率保留骨粉，持续 %s 分钟",
    "salmon_soup": "水下空气消耗 −%s%%，持续 %s 分钟",
    "cod_cakes": "鱼咬钩前的等待时间 −%s%%，持续 %s 分钟",
    "honey_pork": "受到的近战命中伤害 −%s%%，持续 %s 分钟",
    "dew_lamb": "脱战自然回血速度 +%s%%，持续 %s 分钟",
    "spirit_mushrooms": "打坐时自然回灵速度 +%s%%，持续 %s 分钟",
    "honey_apples": "御剑普通巡航耗灵 −%s%%，持续 %s 分钟",
}
TEXT = {
    "itemGroup.yuanxian.kitchen": "灵膳与厨房",
    "item.yuanxian.kitchen_knife": "菜刀",
    "block.yuanxian.kitchen_mill": "石磨",
    "block.yuanxian.kitchen_board": "砧板",
    "block.yuanxian.kitchen_stove": "砖灶",
    "block.yuanxian.kitchen_ginger_crop": "鲜姜",
    "tooltip.yuanxian.kitchen.food": "饱食度 +%s 点    饱和度 +%s 点",
    "message.yuanxian.kitchen.occupied": "台上还有%s，潜行空手右键可以取回",
    "message.yuanxian.kitchen.loaded_mill": "已放入%s ×%s，空手右键研磨",
    "message.yuanxian.kitchen.loaded_board": "已放上%s ×%s，手持菜刀右键处理",
    "message.yuanxian.kitchen.mill_help": "手持小麦右键上料，再空手右键研磨",
    "message.yuanxian.kitchen.board_help": "手持原料右键上料，再持刀右键切配",
    "message.yuanxian.kitchen.prepared": "得到%s ×%s",
    "gui.yuanxian.kitchen.start": "开始烹饪",
    "gui.yuanxian.kitchen.pause": "暂停烹饪",
    "gui.yuanxian.kitchen.input": "投料",
    "gui.yuanxian.kitchen.output": "出锅与容器",
    "gui.yuanxian.kitchen.bowls": "空碗",
    "gui.yuanxian.kitchen.fuel": "木炭",
    "gui.yuanxian.kitchen.status.0": "待开锅",
    "gui.yuanxian.kitchen.status.1": "烹饪中",
    "gui.yuanxian.kitchen.status.2": "等待食材",
    "gui.yuanxian.kitchen.status.3": "等待空碗",
    "gui.yuanxian.kitchen.status.4": "等待木炭",
    "gui.yuanxian.kitchen.status.5": "请取走成品或空容器",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_png(path, image):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def palette_texture(name, material):
    base, spread, kind = material
    rng = random.Random(name)
    t = Image.new("RGB", (16, 16))
    bands = [rng.uniform(-spread * 0.8, spread * 0.8) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            jitter = rng.uniform(-spread, spread)
            color = base
            if kind == "brick":
                row = y // 4
                if y % 4 == 0 or (x + 3 * (row % 2)) % 8 == 0:
                    color = (105, 81, 68)
                    jitter *= 0.35
                else:
                    jitter = jitter * 0.42 + bands[(row * 2 + x // 8) % 16]
            if kind == "wood":
                jitter = jitter * 0.4 + bands[y] + (4 if (x + y * 3) % 11 == 0 else 0)
            if kind == "iron":
                jitter = jitter * 0.35 + bands[y // 2] * 0.7
            t.putpixel((x, y), tuple(round(min(255, max(0, channel + jitter))) for channel in color))
    return t


def visible_axes(cells, box):
    x, y, z, x1, y1, z1, _ = box
    return (
        any((a, y - 1, b) not in cells for a in range(x, x1) for b in range(z, z1)),
        any((a, y1, b) not in cells for a in range(x, x1) for b in range(z, z1)),
        any((a, b, z1) not in cells for a in range(x, x1) for b in range(y, y1)),
        any((a, b, z - 1) not in cells for a in range(x, x1) for b in range(y, y1)),
        any((x - 1, a, b) not in cells for a in range(y, y1) for b in range(z, z1)),
        any((x1, a, b) not in cells for a in range(y, y1) for b in range(z, z1)),
    )


def export_model(shape, key):
    low, high = shape["bounds"]
    scale = 16 / max(b - a for a, b in zip(low, high))
    if key.startswith("stove"):
        # 火焰可见状态共享外形和定位。
        scale = 16 / 30
    cx = (low[0] + high[0]) / 2
    cz = (low[2] + high[2]) / 2
    cells = {(a, b, c) for x, y, z, x1, y1, z1, _ in shape["boxes"]
             for a in range(x, x1) for b in range(y, y1) for c in range(z, z1)}
    textures = {elem[6]: f"{NAMESPACE}:block/kitchen/{elem[6]}" for elem in shape["boxes"]}
    textures["particle"] = textures[shape["boxes"][0][6]]
    elements = []
    for box in shape["boxes"]:
        x, y, z, x1, y1, z1, material = box
        origin = [(x - cx) * scale + 8, y * scale, (cz - z1) * scale + 8]
        end = [(x1 - cx) * scale + 8, y1 * scale, (cz - z) * scale + 8]
        width, height, depth = x1 - x, y1 - y, z1 - z
        sizes = [(width, depth), (width, depth), (width, height), (width, height), (depth, height), (depth, height)]
        enabled = visible_axes(cells, box)
        faces = {
            face: {"uv": [0, 0, min(16, a), min(16, b)], "texture": "#" + material}
            for face, (a, b), show in zip(FACES, sizes, enabled) if show
        }
        if faces:
            elements.append({"from": [round(v, 4) for v in origin], "to": [round(v, 4) for v in end], "faces": faces})
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if key == "knife":
        model["display"] = {
            "gui": {"rotation": [45, -25, 15], "translation": [0, 2, 0], "scale": [1.0, 1.0, 1.0]},
            "thirdperson_righthand": {"rotation": [10, -90, 75], "translation": [0, 5, -1], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_lefthand": {"rotation": [10, 90, -75], "translation": [0, 5, -1], "scale": [0.8, 0.8, 0.8]},
            "firstperson_righthand": {"rotation": [0, -90, 65], "translation": [0, 4, 0], "scale": [0.85, 0.85, 0.85]},
            "firstperson_lefthand": {"rotation": [0, 90, -65], "translation": [0, 4, 0], "scale": [0.85, 0.85, 0.85]},
        }
    return model


def generate_equipment(assets, data):
    design = json.loads((HERE / "geometry.json").read_text())
    for name, material in design["materials"].items():
        write_png(assets / "textures/block/kitchen" / f"{name}.png", palette_texture(name, material))
    for key, shape in design["models"].items():
        item = key == "knife"
        write_json(assets / "models" / ("item" if item else "block") / f"{MODEL_IDS[key]}.json", export_model(shape, key))
    for key in ("mill", "board", "stove"):
        model_id = MODEL_IDS[key]
        variants = {}
        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for lit in (False, True):
                model = f"{NAMESPACE}:block/{model_id}{'_lit' if key == 'stove' and lit else ''}"
                state = {"model": model}
                if rotation:
                    state["y"] = rotation
                variants[f"facing={facing},lit={str(lit).lower()}"] = state
        write_json(assets / "blockstates" / f"{model_id}.json", {"variants": variants})
        write_json(assets / "models/item" / f"{model_id}.json", {"parent": f"{NAMESPACE}:block/{model_id}"})
        write_json(data / "loot_table/blocks" / f"{model_id}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NAMESPACE}:{model_id}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })


def generate_ginger(assets, data, name):
    for stage in range(3):
        sprite = Image.new("RGBA", (16, 16))
        draw = ImageDraw.Draw(sprite)
        stalks = [(5, 7 - stage * 2), (8, 6 - stage * 2), (10, 8 - stage * 2)]
        for index, (x, top) in enumerate(stalks):
            draw.line((8, 15, x, top), fill="#637e49", width=1)
            draw.line((x, top + 4, x - 3, top + 2), fill="#829a52", width=1)
            draw.line((x, top + 3, x + 2, top), fill="#527644", width=1)
            if stage > 0:
                center = (x + 1, top + 4 + index % 2)
                draw.polygon([center, (x + 4, center[1] - 3), (x + 3, center[1] + 1)], fill="#859e51")
        if stage == 2:
            draw.point([(7, 14), (8, 14), (9, 15)], fill="#af8156")
        texture = f"{name}_{stage}"
        write_png(assets / "textures/block/kitchen" / f"{texture}.png", sprite)
        write_json(assets / "models/block" / f"{texture}.json", {
            "parent": "minecraft:block/crop", "render_type": "minecraft:cutout",
            "textures": {"crop": f"{NAMESPACE}:block/kitchen/{texture}"},
        })
    write_json(assets / "blockstates/kitchen_ginger_crop.json", {"variants": {
        f"age={age}": {"model": f"{NAMESPACE}:block/{name}_{0 if age < 3 else 1 if age < 7 else 2}"}
        for age in range(8)
    }})
    crop = f"{NAMESPACE}:kitchen_ginger_crop"
    drop = {"type": "minecraft:item", "name": f"{NAMESPACE}:{name}"}
    write_json(data / "loot_table/blocks/kitchen_ginger_crop.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {**drop, "conditions": [{"condition": "minecraft:block_state_property", "block": crop,
                                    "properties": {"age": "7"}}],
             "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}]},
            drop,
        ]}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


def generate_recipes(data, rules):
    shaped = {
        "kitchen_mill": (["III", " P ", "PPP"], {"I": "minecraft:stone", "P": "minecraft:cobblestone"}),
        "kitchen_board": (["PPP", "  S"], {"P": "#minecraft:planks", "S": "minecraft:stick"}),
        "kitchen_knife": (["II", "IS"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}),
        "kitchen_stove": (["III", "BFB", "BBB"], {"I": "minecraft:iron_ingot", "B": "minecraft:bricks", "F": "minecraft:furnace"}),
    }
    for id_, (pattern, keys) in shaped.items():
        write_json(data / "recipe" / f"{id_}.json", {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {key: {"tag": id_[1:]} if id_.startswith("#") else {"item": id_} for key, id_ in keys.items()},
            "result": {"id": f"{NAMESPACE}:{id_}", "count": 1},
        })
    for recipe in rules["prep"]:
        if recipe["station"] == "table":
            write_json(data / "recipe" / f"{recipe['id']}.json", {
                "type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [{"item": part["item"]} for part in recipe["ingredients"] for _ in range(part["count"])],
                "result": {"id": f"{NAMESPACE}:{recipe['id']}", "count": recipe["count"]},
            })


def generate(output):
    rules = json.loads(RULES.read_text())
    assets, data = output / "assets" / NAMESPACE, output / "data" / NAMESPACE
    entries = [rules["ginger"], *rules["prep"], *rules["meals"]]
    words = dict(TEXT)
    for entry in entries:
        name = entry["id"]
        icon = HERE / "icons" / f"{name}.png"
        texture = assets / "textures/item/kitchen" / f"{name}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(icon, texture)
        write_json(assets / "models/item" / f"{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{NAMESPACE}:item/kitchen/{name}"},
        })
        words[f"item.{NAMESPACE}.{name}"] = entry["name"]
        if "nutrition" in entry:
            words[f"effect.{NAMESPACE}.{name}"] = entry["name"]
            words[f"tooltip.{NAMESPACE}.{name}"] = TOOLTIPS[name.removeprefix("kitchen_")]
            effect = assets / "textures/mob_effect" / f"{name}.png"
            with Image.open(icon) as image:
                write_png(effect, image.resize((18, 18), Image.Resampling.NEAREST))
    generate_equipment(assets, data)
    generate_ginger(assets, data, rules["ginger"]["id"])
    generate_recipes(data, rules)
    write_json(data / "tags/item/kitchen_ingredients.json", {
        "replace": False,
        "values": [f"{NAMESPACE}:{entry['id']}" for entry in [rules["ginger"], *rules["prep"]]],
    })
    write_json(data / "tags/block/kitchen_mining.json", {
        "replace": False,
        "values": [{"id": "#c:ores", "required": False}, "#minecraft:base_stone_overworld",
                   "#minecraft:base_stone_nether", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
                   "minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:polished_diorite",
                   "minecraft:polished_granite", "minecraft:polished_deepslate"],
    })
    for locale in ("zh_cn", "en_us"):
        lang = ROOT / "src/main/resources/assets" / NAMESPACE / "lang" / f"{locale}.json"
        before = lang.read_text(encoding="utf-8")
        original = load_language(before)
        after = language_text(original, original | words)
        if before != after:
            lang.write_text(after, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ROOT / "build/generated/kitchen/resources")
    generate(parser.parse_args().out)


if __name__ == "__main__":
    main()
