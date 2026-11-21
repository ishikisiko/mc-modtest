#!/usr/bin/env python3
"""Generate Nascent Soul and Spirit Transformation arts and their manuals."""
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

from gen_advanced_arts import ASSETS, DATA, ROOT, dump, number, read
from gen_golden_core_arts import CATALOGUE_RULES, ID, LABELS, SOURCE as ROUTE_SOURCE, WEAPONS
from language_assets import language_text, load_language

SOURCE = Path('tools/ascendant_arts/arts.json')
GUIDE = Path('guidebook/getting_started/ascendant_arts.md')
NASCENT, SPIRIT = 'nascent_soul', 'spirit_transformation'
TIER_NAMES = {NASCENT: '元婴', SPIRIT: '化神'}
GEOMETRY = {'range': 64, 'length': 48, 'width': 24, 'radius': 16, 'inner': 16,
            'reach': 48, 'height': 16, 'gap': 16, 'step': 12, 'arc': 180}
TIMING = {'windup': 40, 'duration': 240, 'delay': 120, 'window': 120, 'status_ticks': 200, 'interval': 40}
EFFECTS = {'damage': 8, 'secondary': 8, 'bonus': 8, 'control': 1, 'budget': 1, 'push': 6}
COUNTS = {'targets': 12, 'count': 12, 'per_target': 4}
META = {'id', 'name', 'route', 'tier', 'mechanic', 'description', 'qi', 'cooldown'}
REQUIRE_GROUND = {'cleaving_charge', 'spear_posts', 'folding_earth'}


def plan(root: Path = ROOT) -> dict[Path, str]:
    doc = read(root / SOURCE)
    tiers = doc['tiers']
    routes = read(root / ROUTE_SOURCE)['routes']
    trade = read(root / DATA / 'world_sim/tianheng_trade.json')
    reserved = set(read(root / CATALOGUE_RULES)['hand_written_techniques'])
    moves = {m['id'] for st in (root / DATA / 'combat/style').glob('*.json') for m in read(st)['moves']}
    if doc['schema'] != 1 or set(tiers) != {NASCENT, SPIRIT}:
        raise ValueError('需要元婴和化神的绝技配置')
    if set(tiers[NASCENT]['shelf_regions']) != {r for r, band in trade['regions'].items() if band == NASCENT}:
        raise ValueError('元婴秘籍上架地不对应交易档位')
    if trade['regions'].get(tiers[SPIRIT]['expedition_region']) != SPIRIT:
        raise ValueError('化神秘籍来源不对应境界')
    lang_path = ASSETS / 'lang/zh_cn.json'
    original = load_language((root / lang_path).read_text(encoding='utf-8'))
    lang = dict(original)
    output, runtime = {}, []
    ids, mechanisms, tally = set(), set(), Counter()
    rows = {kind: [] for kind in tiers}
    for row in doc['arts']:
        ident, route, tier_id = (row[k] for k in ('id', 'route', 'tier'))
        if not META <= row.keys() or row.keys() - META - GEOMETRY.keys() - TIMING.keys() - EFFECTS.keys() - COUNTS.keys() - {'move'}:
            raise ValueError(f'{ident} 的绝技字段无效')
        if not ID.fullmatch(ident) or ident not in reserved or ident in ids or row['mechanic'] in mechanisms:
            raise ValueError(f'{ident} 的功法标识或执行器重复')
        ids.add(ident)
        mechanisms.add(row['mechanic'])
        tally[tier_id, route] += 1
        for fields, whole, minimum in ((GEOMETRY, False, .1), (TIMING, True, 1), (EFFECTS, False, 0), (COUNTS, True, 1)):
            for field in fields.keys() & row.keys():
                number(row, field, minimum, fields[field], whole)
        number(row, 'qi', 1, 1000, True)
        number(row, 'cooldown', 1, 60, True)
        tier = tiers[tier_id]
        school = f'yuanxian:{route}' if route in WEAPONS else f'yuanxian:{route}_arts'
        move = 'yuanxian:' + row.get('move', routes[route]['move'])
        previous = routes[route]['previous']
        if move not in moves or read(root / DATA / f'yuanxian/technique/{previous}.json')['school'] != school:
            raise ValueError(f'{ident} 的动作或前置功法与路线不符')
        key = f'cultivation.technique.yuanxian.{ident}'
        full_id = f'yuanxian:{ident}'
        elemental = route not in WEAPONS
        lang[key] = row['name']
        lang[f'combat.yuanxian.art.{ident}.description'] = row['description']
        output[DATA / f'yuanxian/technique/{ident}.json'] = dump({
            'translation_key': key, 'category': 'active', 'grade': tier['grade'],
            'elements': [f'yuanxian:{route}'] if elemental else [], 'school': school,
            'requirements': {'minimum_realm': f'yuanxian:{tier_id}'},
            'effects': {'active': {'skill': full_id, 'qi_cost': row['qi'], 'slots': 1}},
            'study': {'points': tier['points'], 'gates': 0, 'gate_stability_cost': 0},
            'lineage': {'previous': f'yuanxian:{previous}'},
        })
        special = {k: v for k,v in row.items() if k not in META | {'move'}}
        special.update({'mechanic': row['mechanic'], 'tier': tier_id, 'windup': row.get('windup', tier['windup']),
                        'recovery': tier['recovery']})
        if elemental:
            special['element'] = route
        runtime.append({
            'id': full_id, 'school': school, 'family': 'elemental' if elemental else route, 'slot': -1,
            'qi_cost': row['qi'], 'cooldown_ticks': row['cooldown'] * 20,
            'guard_reduction': 0, 'guard_arc_degrees': 90, 'counter_multiplier': 1,
            'requires_ground': row['mechanic'] in REQUIRE_GROUND,
            'interrupt_on_damage': row['mechanic'] not in {'dragon_stance'},
            'fx_lifetime_ticks': 12, 'cast_pitch': 1.04, 'color': routes[route]['color'], 'particle_count': 20,
            'phases': [{
                'move': move, 'shape': 'line', 'reach': min(row['range'], 16), 'arc_degrees': 60,
                'width': 1, 'height': 1, 'half_height': 1, 'damage_multiplier': 1, 'maximum_targets': row['targets'],
                'step_distance': 0, 'reaction': {'hitstun_ticks': 0, 'slide_distance': 0, 'lift': 0, 'lateral_bias': 0},
                'fx': 'arc',
            }],
            'ascendant': special,
        })
        extents = [f'{label} {row[param]:g} 格' for param, label in
                   [('length','长'),('width','宽'),('radius','半径'),('inner','内圈'),('height','高')]
                   if param in row]
        rows[tier_id].append(f'| {LABELS[route]} | {row["name"]} | {row["range"]:g} 格 | { "，".join(extents) or "见说明"} | '
                             f'{row["qi"]} / {row["cooldown"]} 秒 | {row["description"]} |')
    if tally != Counter({(tier, route): 2 for tier in tiers for route in LABELS}):
        raise ValueError('元婴和化神的八条路线各需要两招')
    output[DATA / 'combat/ascendant_arts.json'] = dump({
        'schema': 1, 'shelf_regions': tiers[NASCENT]['shelf_regions'],
        'expedition_region': tiers[SPIRIT]['expedition_region'], 'arts': runtime,
    })
    output[lang_path] = language_text(original, lang)
    output[DATA / 'function/ascendant_arts_books.mcfunction'] = (
        '# 发放空进度秘籍。玩家按各自条件研读。\n'
        + ''.join(f'yuanxian cultivation manual @s {art["id"]}\n' for art in runtime))
    price = trade['realms'][NASCENT]['fallback_price']
    header = [
        '---\nnavigation:\n  title: 元婴与化神绝技\n  parent: index.md\n  position: 38\n'
        '  icon: yuanxian:manual_active_tian\n---\n# 元婴与化神绝技',
        f'元婴秘籍在南疆和魔域州城的天衡殿琳琅阁常备，每本 {price} 下品灵石。'
        f'对应资质达到 100 点并学会同路线入门后，研读 {tiers[NASCENT]["points"]:,} 点习得。',
        '化神秘籍来自中州千机阁江湖榜签发的山寨清剿或兽巢讨伐。击败化神首领并在出口验核后，'
        '有参战记录的玩家回江湖榜领取报酬时各自选一本。'
        f'对应资质达到 200 点并学会同路线入门后，研读 {tiers[SPIRIT]["points"]:,} 点习得。',
        '学成后按 H，在「技能」里设置快捷键，再按 R 进入修炼战斗模式。五行可以空手施展。'
        '有后续操作的招式用同一键继续，费用和冷却在第一次施放时结算。'
        '多数落点招式可以直接朝前施放，服务器会优先使用准星指向的地面或附近目标。',
    ]
    for tier, body in rows.items():
        header.append(f'## {TIER_NAMES[tier]}\n\n| 路线 | 绝技 | 距离 | 判定范围 | 耗灵 / 冷却 | 说明 |\n'
                      '|---|---|---:|---|---:|---|\n' + '\n'.join(body))
    output[GUIDE] = '\n\n'.join(header) + '\n'
    return output


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
