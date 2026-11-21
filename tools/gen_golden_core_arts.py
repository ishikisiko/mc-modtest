#!/usr/bin/env python3
"""生成金丹绝技、通用秘籍定义与指南。"""
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import re

from gen_advanced_arts import ASSETS, DATA, ROOT, dump, number, read
from language_assets import language_text, load_language

SOURCE = Path('tools/golden_core_arts/arts.json')
CATALOGUE_RULES = Path('tools/technique_catalogue/rules.json')
ROUTES = {
    'sword': ('piercing_sword', 'crossing_traces'),
    'spear': ('tip_charge', 'retreating_thrust'),
    'fist': ('stored_punch', 'ground_rings'),
    'metal': ('armor_lock', 'metal_chain'),
    'wood': ('wood_transfer', 'wood_trail'),
    'water': ('side_tide', 'frost_shatter'),
    'fire': ('fire_fuse', 'fire_rows'),
    'earth': ('earth_rows', 'stone_stamp'),
}
WEAPONS = {'sword', 'spear', 'fist'}
LABELS = dict(zip(ROUTES, ('剑', '枪', '拳', '金', '木', '水', '火', '土')))
ID = re.compile(r'[a-z][a-z0-9_]{2,63}')
GOLDEN = 'yuanxian:golden_core'
GEOMETRY = {'length', 'radius', 'inner', 'width', 'secondary_range'}
EXTRA = {'secondary', 'bonus', 'step', 'status', 'status_ticks', 'push', 'windup', 'duration', 'interval'}
COMMON = {'id', 'name', 'route', 'mechanic', 'qi', 'cooldown', 'range', 'targets', 'damage', 'description'}


def plan(root: Path = ROOT) -> dict[Path, str]:
    doc = read(root / SOURCE)
    if doc['schema'] != 1 or set(doc['routes']) != ROUTES.keys():
        raise ValueError('金丹绝技需要八条路线')
    rules = doc['rules']
    number(rules, 'aim_arc', 1, 180)
    number(rules, 'line_assist_arc', 1, 180)
    number(rules, 'windup', 1, 40, True)
    number(rules, 'recovery', 2, 40, True)
    number(rules, 'spear_tip_min', 0, 8)
    number(rules, 'spear_tip_max', rules['spear_tip_min'], 8)
    number(rules, 'pvp_damage', 0.01, 1)
    number(rules, 'pvp_displacement', 0, 1)
    bands = read(root / DATA / 'world_sim/tianheng_trade.json')
    if set(rules['shelf_regions']) != {k for k, v in bands['regions'].items() if v == 'golden_core'}:
        raise ValueError('金丹秘籍需要在金丹档州城上架')
    number(doc['study'], 'points', 1, 1000000, True)
    number(doc['study'], 'gates', 0, 10, True)
    number(doc['study'], 'gate_stability_cost', 0, 10000, True)
    reserved = set(read(root / CATALOGUE_RULES)['hand_written_techniques'])
    moves = {m['id'] for style in (root / DATA / 'combat/style').glob('*.json') for m in read(style)['moves']}
    path_lang = ASSETS / 'lang/zh_cn.json'
    old_lang = load_language((root / path_lang).read_text(encoding='utf-8'))
    names = dict(old_lang)
    outputs = {}
    arts = []
    ids = set()
    mechanics = Counter()
    guide = [
        '---\nnavigation:\n  title: 金丹绝技\n  parent: index.md\n  position: 37\n'
        '  icon: yuanxian:manual_active_di\n---\n# 金丹绝技\n',
        '东海或西漠州城的天衡殿琳琅阁常备这十六本秘籍。每本价格依照金丹档的州城报价。达到金丹、对应兵器或灵根资质达到 100 点并学会前置功法后，右键秘籍研读。',
        '学成后在 H → 技能分配快捷键。三种武技要求手持对应兵器，五行绝技可以空手使用。按 R 进入修炼战斗模式。',
        '## 招式\n\n| 路线 | 绝技 | 用法 | 最远作用距离 | 基础耗灵 | 冷却 |\n|---|---|---|---:|---:|---:|',
    ]
    for row in doc['arts']:
        rid, route, mechanic = row['id'], row['route'], row['mechanic']
        if not COMMON <= row.keys() or row.keys() - COMMON - GEOMETRY - EXTRA - {'move'}:
            raise ValueError(f'{rid} 的绝技字段无效')
        if not ID.fullmatch(rid) or rid in ids or rid not in reserved or mechanic not in ROUTES[route]:
            raise ValueError(f'{rid} 的 ID、归属或执行器无效')
        if not row['name'].strip() or not row['description'].strip():
            raise ValueError(f'{rid} 缺少中文说明')
        ids.add(rid)
        mechanics[mechanic] += 1
        number(row, 'qi', 1, 1000, True)
        number(row, 'cooldown', 1, 60, True)
        number(row, 'range', 1, 64)
        number(row, 'targets', 1, 12, True)
        number(row, 'damage', 0.01, 6)
        for key in GEOMETRY & row.keys():
            number(row, key, 0.1, 48 if key in {'length', 'secondary_range'} else 16)
        for key in {'secondary', 'bonus', 'step', 'status', 'push'} & row.keys():
            number(row, key, 0, 6 if key != 'status' else 1)
        for key in {'windup', 'duration', 'interval', 'status_ticks'} & row.keys():
            number(row, key, 1, 200, True)
        info = doc['routes'][route]
        elemental = route not in WEAPONS
        school = f'yuanxian:{route}_arts' if elemental else f'yuanxian:{route}'
        previous = f'yuanxian:{info["previous"]}'
        old = read(root / DATA / f'yuanxian/technique/{info["previous"]}.json')
        if old['school'] != school:
            raise ValueError(f'{rid} 的前置功法所属路线错误')
        move = 'yuanxian:' + row.get('move', info['move'])
        if move not in moves:
            raise ValueError(f'{rid} 的动作未注册')
        full_id = 'yuanxian:' + rid
        name_key = 'cultivation.technique.yuanxian.' + rid
        names[name_key] = row['name']
        names[f'combat.yuanxian.art.{rid}.description'] = row['description']
        outputs[DATA / f'yuanxian/technique/{rid}.json'] = dump({
            'translation_key': name_key, 'category': 'active', 'grade': 3,
            'elements': ['yuanxian:' + route] if elemental else [], 'school': school,
            'requirements': {'minimum_realm': GOLDEN},
            'effects': {'active': {'skill': full_id, 'qi_cost': row['qi'], 'slots': 1}},
            'study': doc['study'], 'lineage': {'previous': previous},
        })
        special = {key: value for key, value in row.items()
                   if key in GEOMETRY | EXTRA | {'mechanic', 'range', 'damage', 'targets'}}
        if elemental:
            special['element'] = route
        arts.append({
            'id': full_id, 'school': school, 'family': 'elemental' if elemental else route, 'slot': -1,
            'qi_cost': row['qi'], 'cooldown_ticks': row['cooldown'] * 20,
            'guard_reduction': 0, 'guard_arc_degrees': 90, 'counter_multiplier': 1,
            'requires_ground': mechanic in {'tip_charge', 'retreating_thrust', 'ground_rings', 'earth_rows'},
            'interrupt_on_damage': True, 'fx_lifetime_ticks': 12, 'cast_pitch': 1.05,
            'color': info['color'], 'particle_count': 16,
            'phases': [{
                'move': move, 'shape': 'line', 'reach': min(16, row.get('length', row['range'])),
                'arc_degrees': 60, 'width': 1, 'height': 1, 'half_height': 1,
                'damage_multiplier': 1, 'maximum_targets': row['targets'], 'step_distance': 0,
                'reaction': {'hitstun_ticks': 0, 'slide_distance': 0, 'lift': 0, 'lateral_bias': 0},
                'fx': 'arc',
            }],
            'golden_core': special,
        })
        guide[-1] += (f'\n| {LABELS[route]} | {row["name"]} | {row["description"]} | '
                      f'{row["range"]:g} 格 | {row["qi"]} | {row["cooldown"]} 秒 |')
    if mechanics != Counter(mechanic for pair in ROUTES.values() for mechanic in pair):
        raise ValueError('每条路线需要两门对应的绝技')
    outputs[DATA / 'combat/golden_core_arts.json'] = dump({'schema': 1, 'rules': rules, 'arts': arts})
    outputs[path_lang] = language_text(old_lang, names)
    outputs[DATA / 'function/golden_core_arts_books.mcfunction'] = (
        '# 发放空进度秘籍。玩家按各自的学习条件研读。\n'
        + ''.join(f'yuanxian cultivation manual @s {art["id"]}\n' for art in arts))
    outputs[Path('guidebook/getting_started/golden_core_arts.md')] = '\n\n'.join(guide) + '\n'
    return outputs


def main() -> int:
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
