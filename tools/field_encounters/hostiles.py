"""Generate the registry, spawn modifiers, items and loot for provincial enemies."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from tools.field_encounters.items import formatted
from tools.field_encounters.provincial import catalogue as provincial_biomes, loot, spawner
from tools.language_assets import language_text, load_language

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'blueprints/beasts/provincial_enemies'
RESOURCES = ROOT / 'src/main/resources'


def catalogue():
    document = json.loads((SOURCE / 'catalogue.json').read_text(encoding='utf-8'))
    if document['schema'] != 1:
        raise ValueError('州域敌人的目录格式不受支持')
    return document['enemies']


def namespaced(name):
    return f'yuanxian:{name}'


def combat(row):
    key = namespaced(row['id'])
    return {'schema': 1, 'entity': key, 'attributes': row['attributes'], 'chase': row['chase'],
            'stagger': {'animation': 'stagger'}, 'moves': [
                {'id': key + '_' + move['key'], **{name: value for name, value in move.items()
                                                   if name not in ('key', 'context')}}
                for move in row['moves']]}


def outputs():
    regions = {biome['id']: biome['region'] for biome in provincial_biomes()['biomes']}
    # The two boss biomes keep their existing geography and each receive one regular enemy.
    regions |= {'three_tidal_flats': 'donghai', 'red_gravel_windcuts': 'ximo'}
    registry = []
    ids = set()
    material_ids = set()
    translations = {}
    for row in catalogue():
        name, label, biome = row['id'], row['name'], row['biome']
        if name in ids or biome not in regions or row['region'] != regions[biome] or len(row['drops']) != 2:
            raise ValueError(f'州域敌人的标识、群系或材料规则有误 {name}')
        ids.add(name)
        key = namespaced(name)
        moves = combat(row)
        registry.append({
            **{field: row[field] for field in ('id', 'region', 'behavior', 'habitat', 'activity', 'size', 'egg_color')},
            'biome': namespaced(biome), 'palette': [f'minecraft:{block}' for block in row['palette']],
            'materials': [drop['id'] for drop in row['drops']],
            'move_contexts': {move['id']: authored['context'] for move, authored in zip(moves['moves'], row['moves'], strict=True)},
        })
        translations[f'entity.yuanxian.{name}'] = label
        translations[f'item.yuanxian.{name}_spawn_egg'] = label + '生成蛋'
        yield f'data/yuanxian/beast/{name}.json', formatted(moves)
        yield f'data/yuanxian/neoforge/biome_modifier/{name}.json', formatted({
            'type': 'neoforge:add_spawns', 'biomes': namespaced(biome), 'spawners': spawner(key, row['weight'], *row['group']),
        })
        yield f'data/yuanxian/loot_table/entities/{name}.json', formatted(loot({'loot': row['drops']}))
        yield f'assets/yuanxian/models/item/{name}_spawn_egg.json', formatted({'parent': 'minecraft:item/template_spawn_egg'})
        for item in row['drops']:
            item_id = item['id']
            if item_id in material_ids:
                raise ValueError(f'重复的州域敌人材料 {item_id}')
            material_ids.add(item_id)
            translations[f'item.yuanxian.{item_id}'] = item['name']
            yield f'assets/yuanxian/models/item/{item_id}.json', formatted({
                'parent': 'minecraft:item/generated', 'textures': {'layer0': f'yuanxian:item/{item_id}'},
            })
            yield f'assets/yuanxian/textures/item/{item_id}.png', (SOURCE / 'icons' / (item_id + '.png')).read_bytes()

    if sorted(row['biome'] for row in registry) != sorted(map(namespaced, regions)):
        raise ValueError('每个州域群系必须配置一个对应的敌人')

    yield 'data/yuanxian/beast/provincial_enemies.json', formatted({'schema': 1, 'enemies': registry})
    yield 'data/yuanxian/tags/item/storage_bag_materials/provincial_enemies.json', formatted({
        'replace': False, 'values': sorted(map(namespaced, material_ids)),
    })
    for relative, slot, expected in (
        ('data/yuanxian/beast/index.json', 'beasts', [namespaced(row['id']) for row in registry]),
        ('data/yuanxian/tags/item/storage_bag_materials/extra.json', 'values', ['#yuanxian:storage_bag_materials/provincial_enemies']),
    ):
        data = json.loads((RESOURCES / relative).read_text(encoding='utf-8'))
        data[slot].extend(key for key in expected if key not in data[slot])
        yield relative, formatted(data)
    relative = 'assets/yuanxian/lang/zh_cn.json'
    current = load_language((RESOURCES / relative).read_text(encoding='utf-8'))
    yield relative, language_text(current, current | translations).encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    arguments = parser.parse_args()
    for relative, content in outputs():
        path = RESOURCES / relative
        if arguments.check:
            if not path.is_file() or path.read_bytes() != content:
                raise SystemExit(f'需要重新生成 {relative}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)


if __name__ == '__main__':
    main()
