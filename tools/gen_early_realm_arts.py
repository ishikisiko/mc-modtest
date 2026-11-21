#!/usr/bin/env python3
"""生成炼气与筑基绝技、秘籍、配方和玩家指南。"""
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

from gen_advanced_arts import ASSETS, DATA, ROOT, dump, number, read
from gen_golden_core_arts import CATALOGUE_RULES, ID, LABELS, SOURCE as ROUTE_SOURCE, WEAPONS
from language_assets import language_text, load_language

SOURCE = Path('tools/early_realm_arts/arts.json')
GUIDE = Path('guidebook/getting_started/early_realm_arts.md')
QI, FOUNDATION = 'qi_refining', 'foundation_establishment'
TIERS = {QI: '炼气', FOUNDATION: '筑基'}
EXPECTED = Counter({(tier, route): 2 if tier == FOUNDATION and route in {'spear', 'fist'} else 1
                    for tier in TIERS for route in LABELS})
COMMON = {'id', 'name', 'route', 'tier', 'mechanic', 'qi', 'cooldown', 'range', 'targets', 'description'}
SHAPE = {'length', 'width', 'arc', 'radius', 'height', 'secondary_range'}
MAGNITUDES = {'damage', 'secondary', 'step', 'push', 'threshold', 'potency'}
TIMING = {'windup', 'duration', 'delay', 'warning', 'status_ticks'}
GROUNDED = {'sidestep_slash', 'shoulder_ram', 'stepping_spear', 'back_guard'}


def recipe_outputs(identifier, material):
    recipe = 'yuanxian:manual_' + identifier
    stone = {'tag': 'c:spirit_stones/low_grade'}
    return {
        DATA / f'recipe/manual_{identifier}.json': dump({
            'type': 'minecraft:crafting_shapeless', 'category': 'misc',
            'ingredients': [{'item': 'minecraft:book'}, {'item': 'minecraft:ink_sac'}, stone, stone, {'item': material}],
            'result': {'id': 'yuanxian:manual_active_huang', 'count': 1,
                       'components': {'yuanxian:technique': 'yuanxian:' + identifier}},
        }),
        DATA / f'advancement/recipes/manual_{identifier}.json': dump({
            'parent': 'minecraft:recipes/root',
            'criteria': {'has_book': {'trigger': 'minecraft:inventory_changed',
                         'conditions': {'items': [{'items': 'minecraft:book'}]}},
                         'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': recipe}}},
            'requirements': [['has_book', 'has_the_recipe']], 'rewards': {'recipes': [recipe]},
        }),
    }


def plan(root: Path = ROOT) -> dict[Path, str]:
    doc = read(root / SOURCE)
    tiers = doc['tiers']
    if doc['schema'] != 1 or tiers.keys() != TIERS.keys():
        raise ValueError('缺少炼气或筑基绝技定义')
    trade = read(root / DATA / 'world_sim/tianheng_trade.json')
    if set(tiers[FOUNDATION]['shelf_regions']) != {r for r, k in trade['regions'].items() if k == FOUNDATION}:
        raise ValueError('筑基秘籍上架地与交易档位不符')
    routes = read(root / ROUTE_SOURCE)['routes']
    materials = {school['element']: school['material']
                 for school in read(root / 'tools/elemental_arts/arts.json')['schools']}
    materials.update(doc['materials'])
    if materials.keys() != LABELS.keys() or len(set(materials.values())) != len(materials):
        raise ValueError('八条路线需要可区分的工作台配方')
    reserved = set(read(root / CATALOGUE_RULES)['hand_written_techniques'])
    moves = {m['id'] for style in (root / DATA / 'combat/style').glob('*.json') for m in read(style)['moves']}
    lang_path = ASSETS / 'lang/zh_cn.json'
    before = load_language((root / lang_path).read_text(encoding='utf-8'))
    names = dict(before)
    outputs, mechanics, ids, counts, arts = {}, set(), set(), Counter(), []
    tables = {tier: [] for tier in TIERS}
    for tier in tiers.values():
        number(tier, 'grade', 1, 2, True)
        number(tier, 'points', 1, 100000, True)
        number(tier, 'windup', 1, 40, True)
        number(tier, 'recovery', 2, 40, True)
    for row in doc['arts']:
        identifier, route, tier_id, mechanic = (row[k] for k in ('id', 'route', 'tier', 'mechanic'))
        if not COMMON <= row.keys() or row.keys() - COMMON - SHAPE - MAGNITUDES - TIMING - {'move'}:
            raise ValueError(f'{identifier} 的绝技字段无效')
        if not ID.fullmatch(identifier) or identifier not in reserved or identifier in ids or mechanic in mechanics:
            raise ValueError(f'{identifier} 的 ID 或执行器无效')
        if not row['name'].strip() or not row['description'].strip():
            raise ValueError(f'{identifier} 缺少中文说明')
        counts[tier_id, route] += 1
        ids.add(identifier)
        mechanics.add(mechanic)
        number(row, 'qi', 1, 1000, True)
        number(row, 'cooldown', 1, 60, True)
        number(row, 'range', 1, 64)
        number(row, 'targets', 1, 12, True)
        for key in SHAPE & row.keys():
            number(row, key, .1, 180 if key == 'arc' else 48 if key in {'length', 'secondary_range'} else 16)
        for key in MAGNITUDES & row.keys():
            number(row, key, 0, 1 if key == 'potency' else 8)
        for key in TIMING & row.keys():
            number(row, key, 1, 200, True)
        tier = tiers[tier_id]
        route_data = routes[route]
        elemental = route not in WEAPONS
        school = f'yuanxian:{route}_arts' if elemental else f'yuanxian:{route}'
        previous = route_data['previous']
        move = 'yuanxian:' + row.get('move', route_data['move'])
        if move not in moves or read(root / DATA / f'yuanxian/technique/{previous}.json')['school'] != school:
            raise ValueError(f'{identifier} 的动作或前置功法不匹配')
        full_id = 'yuanxian:' + identifier
        name_key = 'cultivation.technique.yuanxian.' + identifier
        names[name_key] = row['name']
        names[f'combat.yuanxian.art.{identifier}.description'] = row['description']
        outputs[DATA / f'yuanxian/technique/{identifier}.json'] = dump({
            'translation_key': name_key, 'category': 'active', 'grade': tier['grade'],
            'elements': ['yuanxian:' + route] if elemental else [], 'school': school,
            'requirements': {'minimum_realm': 'yuanxian:' + tier_id},
            'effects': {'active': {'skill': full_id, 'qi_cost': row['qi'], 'slots': 1}},
            'study': {'points': tier['points'], 'gates': 0, 'gate_stability_cost': 0},
            'lineage': {'previous': 'yuanxian:' + previous},
        })
        if tier_id == QI:
            outputs.update(recipe_outputs(identifier, materials[route]))
        special = {key: value for key, value in row.items()
                   if key in SHAPE | MAGNITUDES | TIMING | {'mechanic', 'range', 'targets'}}
        special.update({'tier': tier_id, 'windup': row.get('windup', tier['windup']), 'recovery': tier['recovery']})
        if elemental:
            special['element'] = route
        arts.append({
            'id': full_id, 'school': school, 'family': 'elemental' if elemental else route, 'slot': -1,
            'qi_cost': row['qi'], 'cooldown_ticks': row['cooldown'] * 20,
            'guard_reduction': 0, 'guard_arc_degrees': 90, 'counter_multiplier': 1,
            'requires_ground': mechanic in GROUNDED, 'interrupt_on_damage': mechanic != 'back_guard',
            'fx_lifetime_ticks': 12, 'cast_pitch': 1.1, 'color': route_data['color'], 'particle_count': 16,
            'phases': [{
                'move': move, 'shape': 'line', 'reach': min(row['range'], 16), 'arc_degrees': 60,
                'width': 1, 'height': 1, 'half_height': 1, 'damage_multiplier': 1, 'maximum_targets': row['targets'],
                'step_distance': 0, 'reaction': {'hitstun_ticks': 0, 'slide_distance': 0, 'lift': 0, 'lateral_bias': 0},
                'fx': 'arc',
            }],
            'early_realm': special,
        })
        footprint = (f'半径 {row["radius"]:g} 格' if 'radius' in row else
                     f'{row["arc"]:g}° 扇面' if 'arc' in row else f'宽 {row["width"]:g} 格')
        if mechanic == 'arrow_ring':
            footprint += f'，正面 {row["arc"]:g}°'
        if mechanic == 'stone_screen':
            footprint += f'，高 {row["height"]:g} 格'
        tables[tier_id].append(f'| {LABELS[route]} | {row["name"]} | {row["range"]:g} 格 | {footprint} | '
                                f'{row["qi"]} / {row["cooldown"]} 秒 | {row["description"]} |')
    if counts != EXPECTED:
        raise ValueError('炼气应有八招，筑基应有十招')
    outputs[DATA / 'combat/early_realm_arts.json'] = dump({
        'schema': 1, 'shelf_regions': tiers[FOUNDATION]['shelf_regions'], 'arts': arts,
    })
    outputs[lang_path] = language_text(before, names)
    outputs[DATA / 'function/early_realm_arts_books.mcfunction'] = (
        '# 发放空进度秘籍。玩家按各自条件研读。\n'
        + ''.join(f'yuanxian cultivation manual @s {art["id"]}\n' for art in arts))
    outputs[GUIDE] = player_guide(tables, tiers, trade)
    return outputs


def player_guide(tables, tiers, trade):
    price = trade['realms'][FOUNDATION]['fallback_price']
    lines = [
        '---\nnavigation:\n  title: 炼气与筑基绝技\n  parent: index.md\n  position: 36\n'
        '  icon: yuanxian:manual_active_xuan\n---\n# 炼气与筑基绝技',
        '八条路线都可以在炼气和筑基修习新的绝技。先学同路线的入门功法，再右键秘籍研读。'
        '学成后在 H → 技能分配快捷键，按 R 进入修炼战斗模式。剑、枪、拳技需要手持对应武器，五行可以空手施展。',
        f'炼气秘籍用书、墨囊、两枚下品灵石和路线材料在工作台制作，'
        f'每本需要 {tiers[QI]["points"]:,} 点研读。筑基秘籍由灵岳和北原州城天衡殿琳琅阁常备，'
        f'每本 {price} 下品灵石，需要对应资质 40 点和 {tiers[FOUNDATION]["points"]:,} 点研读。',
        '定向招式按地面粒子提示判定。选敌技会优先选中准星上的可见敌人，准星偏开时也能在正面选敌。'
        '过肩摔的抓取距离较短，铁肩靠则把贴地进身的距离算在总距离中。',
    ]
    for tier, rows in tables.items():
        lines.append(f'## {TIERS[tier]}\n\n'
                     '| 路线 | 绝技 | 距离 | 判定范围 | 耗灵 / 冷却 | 说明 |\n'
                     '|---|---|---:|---|---:|---|\n' + '\n'.join(rows))
    return '\n\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    drift = []
    for path, contents in plan().items():
        target = ROOT / path
        if target.is_file() and target.read_text(encoding='utf-8') == contents:
            continue
        if args.check:
            drift.append(str(path))
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(contents, encoding='utf-8')
    for path in drift:
        print(f'生成资源待更新 {path}')
    return bool(drift)


if __name__ == '__main__':
    raise SystemExit(main())
