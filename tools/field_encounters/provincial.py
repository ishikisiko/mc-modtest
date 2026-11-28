"""Build the native biome, habitat, and material resources from the eight-province catalogue."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from tools.field_encounters.items import formatted
from tools.gen_qingfeng_sword_model import encode_png
from tools.language_assets import language_text, load_language

ROOT = Path(__file__).resolve().parents[2]
CATALOGUE = ROOT / 'blueprints/worldplan/provincial_biomes.json'
RESOURCES = ROOT / 'src/main/resources'

# The common overworld ore sequence follows BiomeDefaultFeatures.addDefaultOres in 1.21.1.
ORES = '''ore_coal_upper ore_coal_lower ore_iron_upper ore_iron_middle ore_iron_small
          ore_gold ore_gold_lower ore_redstone ore_redstone_lower ore_diamond ore_diamond_medium
          ore_diamond_large ore_diamond_buried ore_lapis ore_lapis_buried ore_copper underwater_magma
          disk_sand disk_clay disk_gravel'''.split()
MAIN_ICON = ('.....11....','....1231...','...123321..','..1233211..',
             '.123321....','.23321.....','..221......','...1.......')
CROSS_ICON = ('....121....','...12321...','..1233321..','..1232321..',
              '..1223321..','...23321...','....121....','.....1.....')


def catalogue():
    return json.loads(CATALOGUE.read_text(encoding='utf-8'))


def geography():
    value = catalogue()
    return {**{k: value[k] for k in ('schema', 'vanilla_weight', 'survey_step')}, 'biomes': [
        {'biome': f'yuanxian:{group["id"]}', **{k: group[k] for k in ('region', 'window', 'site')},
         'terrain': {**group['terrain'], 'palette': [f'minecraft:{block}' for block in group['terrain']['palette']]}}
        for group in value['biomes']]}


def category(fauna):
    return {'swim': 'water_ambient', 'fly': 'ambient'}.get(fauna['behavior'], 'creature')


def spawner(entity, weight, low, high):
    return {'type': entity, 'weight': weight, 'minCount': low, 'maxCount': high}


def biome(group):
    f = group['fauna']
    color = int(f['material_color'], 16)
    temp, rain = group['climate']
    fluid = group['terrain']['fluid']
    features = [[] for _ in range(11)]
    features[6] = [f'minecraft:{item}' for item in ORES]
    features[9] = [f'minecraft:{item}' for item in group['decoration']]
    spawns = {'monster': [spawner(f'minecraft:{mob}', weight, 1, 4) for mob, weight in
                           (('zombie', 70), ('skeleton', 65), ('spider', 48), ('creeper', 32), ('enderman', 8))]}
    if fluid == 'sea':
        spawns['water_creature'] = [spawner('minecraft:squid', 5, 1, 3)]
        spawns['water_ambient'] = [spawner('minecraft:tropical_fish', 7, 2, 4)]
    elif group['terrain']['palette'][0] == 'grass_block':
        spawns['creature'] = [spawner('minecraft:sheep', 3, 2, 3), spawner('minecraft:chicken', 3, 1, 2)]
    return {'has_precipitation': rain >= .29, 'temperature': round(temp, 3), 'downfall': rain,
            'effects': {'fog_color': 12638437, 'sky_color': 8103167,
                        'water_color': 3850663 if fluid == 'sea' else 4159204,
                        'water_fog_color': 2894971 if fluid == 'sea' else 329011,
                        **({'grass_color': color, 'foliage_color': color} if group['terrain']['palette'][0] == 'grass_block' else {})},
            'carvers': {'air': ['minecraft:cave', 'minecraft:cave_extra_underground', 'minecraft:canyon']},
            'features': features, 'spawners': spawns, 'spawn_costs': {}}


def loot(fauna):
    first, second = (f'yuanxian:{item["id"]}' for item in fauna['loot'])
    player = {'condition': 'minecraft:killed_by_player'}
    return {'type': 'minecraft:entity', 'pools': [
        {'rolls': 1, 'conditions': [player], 'entries': [{'type': 'minecraft:item', 'name': first,
         'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}},
                       {'function': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting',
                        'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}}]}]},
        {'rolls': 1, 'conditions': [player, {'condition': 'minecraft:random_chance', 'chance': .38}],
         'entries': [{'type': 'minecraft:item', 'name': second}]}]}


def beast(fauna):
    entity = f'yuanxian:{fauna["id"]}'
    reach = round(fauna['size'][0] * .75 + .95, 2)
    return {'schema': 1, 'entity': entity,
            'attributes': {'max_health': fauna['health'], 'attack_damage': 6 if fauna['behavior'] == 'retaliate' else 2,
                           'movement_speed': fauna['speed'], 'follow_range': 16, 'armor': 2,
                           'knockback_resistance': .08, 'step_height': .85},
            'chase': {'speed_modifier': 1.06, 'move_gap_ticks': 15, 'cancelled_cooldown_ticks': 22},
            'stagger': {'animation': 'stagger'},
            'moves': [{'id': entity + '_ward', 'animation': 'ward', 'total_ticks': 22, 'windup_ticks': 8,
                       'turn_lock_tick': 6, 'active_ticks': [8, 8], 'immune_ticks': [5, 9],
                       'damage_multiplier': 1, 'maximum_targets': 1, 'use_range': [0, reach + .3],
                       'cooldown_ticks': 28, 'weight': 1, 'lunge': {'tick': 7, 'forward_min': 0, 'forward_max': 0, 'up': 0},
                       'hit': {'forward': [0, reach], 'half_width': reach * .6, 'height': [-.4, fauna['size'][1] + .24]},
                       'knockback': {'strength': .25, 'lift': .035}}]}


def icon(color, rare):
    base = tuple(bytes.fromhex(color))
    colors = {'0': (0, 0, 0, 0), '.': (0, 0, 0, 0), '1': (*(c * 3 // 5 for c in base), 255),
              '2': (*base, 255), '3': (*(int(c + (255 - c) * .45) for c in base), 255)}
    pixels = [[colors['.']] * 16 for _ in range(16)]
    for y, row in enumerate(CROSS_ICON if rare else MAIN_ICON, 4):
        for x, char in enumerate(row, 2):
            pixels[y][x] = colors[char]
    return encode_png(pixels)


def outputs():
    all_groups = catalogue()['biomes']
    registry = []
    block_ids = set()
    item_ids = set()
    translations = {}
    biome_ids = []
    beast_ids = []
    for group in all_groups:
        fauna = group['fauna']
        biome_id, mob = group['id'], fauna['id']
        block_ids.update(group['terrain']['palette'])
        biome_ids.append(f'yuanxian:{biome_id}')
        beast_ids.append(f'yuanxian:{mob}')
        registry.append({'id': mob, 'biome': biome_ids[-1], 'region': group['region'], 'category': category(fauna),
                         **{k: fauna[k] for k in ('size', 'habitat', 'activity', 'behavior', 'material_color')},
                         'palette': [f'minecraft:{block}' for block in group['terrain']['palette']],
                         'materials': [item['id'] for item in fauna['loot']]})
        translations.update({f'biome.yuanxian.{biome_id}': group['name'], f'entity.yuanxian.{mob}': fauna['name'],
                             f'item.yuanxian.{mob}_spawn_egg': fauna['name'] + '生成蛋'})
        yield f'data/yuanxian/worldgen/biome/{biome_id}.json', formatted(biome(group))
        yield f'data/yuanxian/neoforge/biome_modifier/{mob}.json', formatted({
            'type': 'neoforge:add_spawns', 'biomes': biome_ids[-1],
            'spawners': spawner(beast_ids[-1], fauna['weight'], *fauna['group'])})
        yield f'data/yuanxian/beast/{mob}.json', formatted(beast(fauna))
        yield f'data/yuanxian/loot_table/entities/{mob}.json', formatted(loot(fauna))
        yield f'assets/yuanxian/models/item/{mob}_spawn_egg.json', formatted({'parent': 'minecraft:item/template_spawn_egg'})
        for i, item in enumerate(fauna['loot']):
            key = item['id']
            if key in item_ids:
                raise ValueError(f'重复的群系掉落 {key}')
            item_ids.add(key)
            translations[f'item.yuanxian.{key}'] = item['name']
            yield f'assets/yuanxian/textures/item/{key}.png', icon(fauna['material_color'], i == 1)
            yield f'assets/yuanxian/models/item/{key}.json', formatted({
                'parent': 'minecraft:item/generated', 'textures': {'layer0': f'yuanxian:item/{key}'}})
    if len(set(biome_ids)) != 14 or len(set(beast_ids)) != 14 or len(block_ids) < 12:
        raise ValueError('州域群系目录存在重复或缺少地貌材料')
    yield 'data/yuanxian/beast/provincial_fauna.json', formatted({'schema': 1, 'fauna': registry})
    yield 'data/minecraft/tags/worldgen/biome/is_overworld.json', formatted({'replace': False, 'values': biome_ids})
    yield 'data/minecraft/tags/entity_type/can_breathe_under_water.json', formatted({'replace': False, 'values': [
        f'yuanxian:{group["fauna"]["id"]}' for group in all_groups if group['fauna']['behavior'] in ('swim', 'amphibious', 'hop')]})
    yield 'data/yuanxian/tags/item/storage_bag_materials/provincial_fauna.json', formatted({
        'replace': False, 'values': [f'yuanxian:{key}' for key in sorted(item_ids)]})
    extra_path = 'data/yuanxian/tags/item/storage_bag_materials/extra.json'
    extra = json.loads((RESOURCES / extra_path).read_text())
    group_tag = '#yuanxian:storage_bag_materials/provincial_fauna'
    if group_tag not in extra['values']:
        extra['values'].append(group_tag)
    yield extra_path, formatted(extra)
    index_path = 'data/yuanxian/beast/index.json'
    index = json.loads((RESOURCES / index_path).read_text())
    index['beasts'].extend(key for key in beast_ids if key not in index['beasts'])
    yield index_path, formatted(index)
    language_path = 'assets/yuanxian/lang/zh_cn.json'
    old = load_language((RESOURCES / language_path).read_text())
    yield language_path, language_text(old, old | translations).encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for relative, data in outputs():
        path = RESOURCES / relative
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                raise SystemExit(f'需要重新生成 {relative}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)


if __name__ == '__main__':
    main()
