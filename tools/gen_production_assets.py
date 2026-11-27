#!/usr/bin/env python3
"""Author the mineral rig and the three attached condensation modules."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import Canvas, png
from tools.language_assets import language_text, load_language

NAMESPACE = 'yuanxian:'
MEDIUM_STONE = '#c:spirit_stones/medium_grade'
EYES = {'iron_mine_eye': '天然铁矿眼', 'xuantie_mine_eye': '天然玄铁矿眼'}
ITEMS = {'condensation_core': '凝晶芯', 'jade_condensation_core': '青玉凝晶芯'}
NAMES = {
    'mine_station': '矿站控制器', 'mine_column': '采掘立柱', 'mine_intake': '引脉座', 'mine_head': '采掘头',
    'mine_outlet': '出矿口', 'mine_beam': '采掘横梁', 'condenser_intake': '引灵座', 'bronze_condenser': '青铜凝石匣',
    'jade_crystal_seat': '青玉凝晶座', 'jade_condenser': '汇晶匣', 'golden_crystal_intake': '导灵座',
    'crystal_channel': '导晶槽', 'crystal_press_base': '合晶台座', 'golden_condenser': '紫金合晶匣', 'crystal_outlet': '出石口',
}
STATUS = {
    'stopped': '已暂停', 'running': '正在生产', 'full': '矿仓已达备货量或已满', 'tool': '缺少适用的矿镐',
    'depleted': '等待矿眼回补', 'unloaded': '结构区块尚未加载', 'anchor': '找不到对应矿眼或阵心',
    'original_array': '原聚灵阵尚未成型', 'part': '构件缺失或朝向错误', 'support': '地基缺少完整顶面',
    'shared': '该处已放置另一座控制器', 'border': '矿站需要搭在矿眼区块内',
}
MINE_COLORS = {
    'rockDark': (59, 67, 74), 'slate': (83, 94, 101), 'cutSlate': (104, 119, 128), 'iron': (72, 80, 86),
    'edge': (120, 134, 139), 'patina': (83, 108, 100), 'bronze': (156, 119, 81), 'rim': (201, 158, 104),
    'darkBronze': (99, 73, 54), 'pit': (32, 42, 45), 'quartz': (128, 201, 205),
    'vein': (83, 139, 156), 'ore': (151, 170, 181),
}
SIDES = ('up', 'down', 'north', 'east', 'south', 'west')
ORIENTATIONS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def shaped(pattern, keys, count=1):
    return pattern, keys, count


def loose(*ingredients):
    return ingredients, None, 1


M, Y = 'minecraft:', NAMESPACE
RECIPES = {
    'mine_station': shaped(['CIC', 'RGR', 'SSS'], {'C': M+'copper_ingot', 'I': M+'comparator', 'R': M+'redstone', 'G': M+'glass_pane', 'S': M+'polished_deepslate'}),
    'mine_column': shaped(['ICI', 'DSD', 'ICI'], {'I': M+'iron_ingot', 'C': M+'copper_ingot', 'D': M+'deepslate_bricks', 'S': M+'polished_deepslate'}, 4),
    'mine_beam': shaped(['III', 'CCC', 'DDD'], {'I': M+'iron_ingot', 'C': M+'copper_ingot', 'D': M+'deepslate_bricks'}, 3),
    'mine_intake': shaped([' C ', 'CDC', 'SSS'], {'C': M+'copper_ingot', 'D': M+'quartz', 'S': M+'polished_deepslate'}),
    'mine_head': shaped(['III', 'CRC', ' Q '], {'I': M+'iron_ingot', 'C': M+'copper_ingot', 'R': M+'redstone', 'Q': M+'quartz'}),
    'mine_outlet': shaped(['C C', ' I ', ' S '], {'C': M+'copper_ingot', 'I': M+'iron_ingot', 'S': M+'stone_bricks'}),
    'condensation_core': shaped([' S ', 'SCS', ' S '], {'S': MEDIUM_STONE, 'C': M+'copper_ingot'}),
    'jade_condensation_core': loose(*[Y+'condensation_core']*5, M+'prismarine_crystals', M+'quartz'),
    'condenser_intake': shaped([' C ', 'CQC', 'SSS'], {'C': M+'copper_ingot', 'Q': M+'quartz', 'S': M+'stone_bricks'}),
    'bronze_condenser': shaped(['CCC', 'GKG', 'SSS'], {'C': M+'copper_ingot', 'G': M+'glass_pane', 'K': Y+'condensation_core', 'S': M+'stone_bricks'}),
    'jade_crystal_seat': shaped(['P P', 'QCQ', ' S '], {'P': M+'prismarine_crystals', 'Q': M+'quartz', 'C': M+'copper_ingot', 'S': M+'prismarine_bricks'}, 2),
    'jade_condenser': shaped(['QCQ', 'PKP', 'SSS'], {'Q': M+'quartz', 'C': M+'copper_ingot', 'P': M+'prismarine_crystals', 'K': Y+'jade_condensation_core', 'S': M+'prismarine_bricks'}),
    'golden_crystal_intake': shaped([' A ', 'GAG', 'O O'], {'A': M+'amethyst_shard', 'G': M+'gold_ingot', 'O': M+'obsidian'}, 2),
    'crystal_channel': shaped(['GGG', 'OOO'], {'G': M+'gold_ingot', 'O': M+'polished_deepslate_slab'}, 8),
    'crystal_press_base': shaped(['GGG', 'OAO', 'OOO'], {'G': M+'gold_ingot', 'O': M+'obsidian', 'A': M+'amethyst_shard'}),
    'golden_condenser': loose(*[Y+'jade_condensation_core']*5, M+'gold_block', M+'obsidian', M+'amethyst_shard'),
    'crystal_outlet': shaped(['C C', 'PCP', ' S '], {'C': M+'copper_ingot', 'P': M+'quartz', 'S': M+'polished_deepslate'}),
}


def ingredients(recipe):
    rows, keys, _ = recipe
    return [keys[slot] for row in rows for slot in row if slot != ' '] if keys is not None else list(rows)


def check_layout_costs(rules):
    def cost(item, amount):
        if item == MEDIUM_STONE:
            return amount
        if not item.startswith(Y) or item[len(Y):] not in RECIPES:
            return 0
        recipe = RECIPES[item[len(Y):]]
        crafts = (amount + recipe[2] - 1) // recipe[2]
        return sum(cost(child, count * crafts) for child, count in Counter(ingredients(recipe)).items())

    price = {}
    for tier, layout in rules['layouts'].items():
        price[tier] = sum(cost(Y+part, count) for part, count in Counter(row[3] for row in layout).items())
    if price != {'mine': 0, 'low': 4, 'medium': 20, 'high': 100}:
        raise ValueError(f'整套凝晶构件的中品灵石成本发生变动 {price}')


def model(boxes, textures):
    elements = []
    for material, low, high, *rotation in boxes:
        if not all(0 <= a < b <= 16 for a, b in zip(low, high)):
            raise ValueError((material, low, high))
        faces = {side: {'texture': '#'+material, 'uv': [0, 0, 16, 16]} for side in SIDES}
        element = {'from': low, 'to': high, 'faces': faces}
        if rotation:
            element['rotation'] = rotation[0]
        elements.append(element)
    return {'parent': 'minecraft:block/block', 'textures': {**textures, 'particle': next(iter(textures.values()))}, 'elements': elements}


def texture(color, *, joint=False):
    canvas = Canvas(16)
    for y in range(16):
        for x in range(16):
            noise = (x*17 + y*23 + x*y*3) % 19 - 9
            noise += 3*((x//4 + y//4) % 2) if joint else 0
            canvas.put(x, y, tuple(max(0, min(255, channel + noise)) for channel in color))
    return png(canvas.rows)


def eye_texture(xuantie, top):
    canvas = Canvas(16)
    stone = (66, 69, 80) if xuantie else (119, 116, 110)
    mineral = (139, 172, 199) if xuantie else (196, 155, 121)
    for y in range(16):
        for x in range(16):
            seam = abs(y - (7 + (x-8)//4)) <= 1
            jewel = top and abs(x-7) + abs(y-8) < 4
            color = mineral if (seam and (y*7+x*3) % 5 < 3) or jewel else stone
            noise = (x*13+y*23+x*y*3)%15-7
            canvas.put(x, y, tuple(max(0, min(255, value+noise)) for value in color))
    return png(canvas.rows)


def core_texture(jade):
    canvas = Canvas(16)
    colors = [(48, 69, 71), (118, 191, 180), (178, 224, 216)] if jade else [(80, 58, 43), (167, 119, 72), (219, 166, 101)]
    for y in range(16):
        for x in range(16):
            radius = max(abs(x-7.5), abs(y-7.5), (abs(x-7.5)+abs(y-7.5))*.72)
            if radius <= 5.5:
                index = 0 if radius > 4.5 else 1 if radius > 2.5 else 2
                canvas.put(x, y, colors[index])
    return png(canvas.rows)


def outputs():
    resources = ROOT/'src/main/resources'
    assets = resources/'assets/yuanxian'
    data = resources/'data/yuanxian'
    geometry = json.loads((ROOT/'tools/production/geometry.json').read_text())
    rules = json.loads((data/'production/rules.json').read_text())
    gathering = json.loads((ROOT/'tools/gathering/visual.json').read_text())
    check_layout_costs(rules)
    result = {}

    def put(path, content):
        result[path] = (json.dumps(content, ensure_ascii=False, indent=2)+'\n').encode()

    def materials(family, boxes):
        palettes = {'bronze': gathering['palette'], 'jade': gathering['tiers']['medium']['palette'], 'gold': gathering['tiers']['high']['palette']}
        colors = MINE_COLORS if family == 'mine' else {
            **palettes[family],
            'frame': (60, 65, 65), 'newCopper': (186, 155, 105) if family == 'jade' else (179, 138, 79),
            'newDark': (63, 53, 78) if family == 'gold' else (62, 85, 80),
            'light': (190, 139, 222) if family == 'gold' else (144, 207, 192),
            'crystal': {'gold': (156, 96, 210), 'jade': (103, 194, 196), 'bronze': (124, 170, 145)}[family],
        }
        names = {}
        for key in dict.fromkeys(box[0] for box in boxes):
            identifier = f'production_{family}_{key.lower()}'
            result[assets/f'textures/block/{identifier}.png'] = texture(colors[key], joint='stone' in key.lower() or 'slate' in key.lower())
            names[key] = f'{Y}block/{identifier}'
        return names

    def block_item(name):
        put(assets/f'models/item/{name}.json', {'parent': f'{Y}block/{name}'})

    for name, row in geometry['blocks'].items():
        boxes = row['boxes']
        put(assets/f'models/block/{name}.json', model(boxes, materials(row['family'], boxes)))
        put(assets/f'blockstates/{name}.json', {'variants': {
            f'facing={direction}': {'model': f'{Y}block/{name}', 'y': turns} for direction, turns in ORIENTATIONS.items()}})
        block_item(name)

    for shape, boxes in geometry['channel'].items():
        put(assets/f'models/block/crystal_channel_{shape}.json', model(boxes, materials('gold', boxes)))
    center = {'model': Y+'block/crystal_channel_center'}
    put(assets/'blockstates/crystal_channel.json', {'multipart': [
        {'apply': center}, *[{'when': {side: 'true'}, 'apply': {'model': Y+'block/crystal_channel_arm', 'y': turns}} for side, turns in ORIENTATIONS.items()]]})
    put(assets/'models/item/crystal_channel.json', {'parent': Y+'block/crystal_channel_center'})

    for name in EYES:
        for surface in ('top', 'side'):
            result[assets/f'textures/block/{name}_{surface}.png'] = eye_texture(name.startswith('xuantie'), surface == 'top')
        put(assets/f'models/block/{name}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'bottom': 'minecraft:block/deepslate' if name.startswith('xuantie') else 'minecraft:block/stone',
            'side': f'{Y}block/{name}_side', 'top': f'{Y}block/{name}_top'}})
        put(assets/f'blockstates/{name}.json', {'variants': {'': {'model': f'{Y}block/{name}'}}})

    for name in ITEMS:
        result[assets/f'textures/item/{name}.png'] = core_texture(name.startswith('jade'))
        put(assets/f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{Y}item/{name}'}})

    def ingredient(value):
        return {'tag' if value.startswith('#') else 'item': value.removeprefix('#')}

    for name, recipe in RECIPES.items():
        pattern, keys, count = recipe
        output = {'id': Y+name, 'count': count}
        if keys is None:
            body = {'type': 'minecraft:crafting_shapeless', 'ingredients': [ingredient(item) for item in pattern]}
        else:
            body = {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': {slot: ingredient(item) for slot, item in keys.items()}}
        put(data/f'recipe/{name}.json', {**body, 'category': 'misc', 'result': output})
        unlock = next((item for item in ingredients(recipe) if item.startswith(Y) or item.startswith('#')), ingredients(recipe)[0])
        predicate = {'items': Y+'medium_grade_spirit_stone' if unlock == MEDIUM_STONE else unlock}
        put(data/f'advancement/recipes/misc/{name}.json', {
            'parent': 'minecraft:recipes/root', 'criteria': {
                'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [predicate]}},
                'has_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': Y+name}}},
            'requirements': [['has_material', 'has_recipe']], 'rewards': {'recipes': [Y+name]}})

    for name in NAMES:
        put(data/f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': Y+name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    pickaxe = resources/'data/minecraft/tags/block/mineable/pickaxe.json'
    tags = json.loads(pickaxe.read_text())
    tags['values'] = list(dict.fromkeys([*tags['values'], *[Y+name for name in NAMES]]))
    put(pickaxe, tags)

    put(data/'worldgen/configured_feature/natural_mine_eye.json', {'type': Y+'natural_mine_eye', 'config': {}})
    put(data/'worldgen/placed_feature/natural_mine_eye.json', {'feature': Y+'natural_mine_eye',
        'placement': [{'type': 'minecraft:rarity_filter', 'chance': 8}, {'type': 'minecraft:biome'}]})
    put(data/'neoforge/biome_modifier/add_natural_mine_eyes.json', {'type': 'neoforge:add_features',
        'biomes': '#yuanxian:has_spirit_stone_ore', 'features': Y+'natural_mine_eye', 'step': 'underground_decoration'})

    lang = assets/'lang/zh_cn.json'
    translation = {**{f'block.yuanxian.{key}': value for key, value in {**NAMES, **EYES}.items()},
                   **{f'item.yuanxian.{key}': value for key, value in ITEMS.items()},
                   **{f'menu.yuanxian.production.status.{key}': value for key, value in STATUS.items()},
                   'message.yuanxian.mine_eye': '把引脉座放在矿眼正上方，再按矿站控制器的蓝图搭建。',
                   'itemGroup.yuanxian.passive_production': '矿站与凝晶'}
    original = load_language(lang.read_text())
    result[lang] = language_text(original, {**original, **translation}).encode()
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    check = parser.parse_args().check
    drift = []
    for path, content in outputs().items():
        if check:
            if not path.is_file() or path.read_bytes() != content:
                drift.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if drift:
        raise SystemExit('矿站与凝晶资源漂移\n'+'\n'.join(drift))
    print('矿站与凝晶资源检查通过' if check else '矿站与凝晶资源已生成')
