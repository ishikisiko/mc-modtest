"""从游戏资源导出源仙工作台的功法目录。"""
from __future__ import annotations

import json
from collections import defaultdict
from math import ceil
from pathlib import Path

CATEGORIES = {
    'core': '攻、防守和灵力与修炼各装备一门，按心法记录的固定点数生效。',
    'active': '学会绝技后可在技能页分配按键，实际效果取决于招式。',
    'movement': '身法决定闪避的位移距离、无敌时间、耗灵和冷却。',
    'body': '炼体功法的效果字段包括属性加值和抗硬直。',
}
CORE_GRADES = ('mortal', 'huang', 'xuan', 'di', 'tian')
SHAPES = {
    'line': '直线', 'fan': '扇形', 'ring': '环形', 'bolt': '单体', 'ray': '射线',
    'self': '自身护体', 'burst': '落点爆发', 'cone': '前方扇形', 'field': '落点领域',
}
GROWTH_EFFECTS = {'cultivation': '修为', 'recovery': '回灵', 'damage': '伤害', 'qi_cost': '耗灵',
                  'flat_normal': '每批普通修为', 'flat_stone': '每批灵石修为', 'recovery_quarters': '自然回灵'}
HEART_SLOTS = {'attack': '攻', 'defense': '防守', 'spirit': '灵力与修炼'}
HEART_DIRECTIONS = {'sword': '剑', 'spear': '枪', 'fist': '拳掌', 'spell': '法术', 'general': '通用'}


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def registry(data, name):
    return {
        f'yuanxian:{path.stem}': read_json(path)
        for path in sorted((data / 'yuanxian' / name).glob('*.json'))
    }


def seconds(ticks):
    return f'{ticks / 20:g} 秒（{ticks} tick）'


def blocks(value):
    return f'{value:g} 格'


def multiplier(value):
    return f'×{value:g}'


def percent(value):
    return f'{value:.4g}%'


def element_name(labels, id):
    return labels['cultivation.element.' + id.replace(':', '.')]


def core_stats(definition, effects, world_rules, element_names):
    if (heart := effects.get('heart')) is not None:
        stats = [('装备栏位', HEART_SLOTS[heart['slot']]), ('适用方向', HEART_DIRECTIONS[heart['direction']])]
        def add(label, value, unit='点'):
            if value:
                stats.append((label, f'{value:+g} {unit}'))
        if heart['slot'] == 'attack':
            if heart['direction'] == 'spell':
                add('、'.join(element_names) + '系法术直接伤害基数', heart['art'])
            else:
                add('对应普攻基础伤害', heart['basic'])
                add('耗灵绝技基础伤害' if heart.get('paid_only') else '对应绝技基础伤害', heart['art'])
        elif heart['slot'] == 'defense':
            scope = '持对应武器时' if heart['direction'] in ('sword', 'spear', 'fist') else ''
            if heart['direction'] == 'spell':
                scope = '受到' + ('、'.join(element_names) or '任意五行') + '伤害时'
            add(scope + '肉身防御', heart['base'])
            if (guard := heart.get('guard')) in ('melee', 'ranged'):
                incoming = '直接近战攻击' if guard == 'melee' else '投射物或五行法术'
                add('持对应武器且受到' + incoming + '时本书加值', heart['reaction'])
        else:
            add('灵力上限', heart['capacity'])
            add('自然回灵', heart['recovery_quarters'] / 4, '点/秒')
            add('每批普通打坐修为', heart['normal'])
            add('每批灵石打坐修为', heart['stone'])
        if effects.get('manual_price'):
            stats.append(('游戏内换取秘籍', f"{effects['manual_price']:,} 颗下品灵石"))
        return stats
    grade = definition['grade']
    rules = world_rules['techniques']
    base = rules['grades'][CORE_GRADES[grade]]['cultivation'] if grade else 1
    stats = [
        ('打坐修为基础倍率', multiplier(base)),
        ('自然回灵基础倍率', multiplier(base)),
    ]
    if grade and element_names:
        threshold = world_rules['roots']['element_threshold']
        names = '、'.join(element_names)
        matched = multiplier(base + rules['element_match_bonus'])
        stats.append(('五行匹配后的倍率', f'{matched}（{names}中任一对应资质达到 {threshold} 点）'))
    route = effects.get('meditation_route', 'xiaozhoutian')
    return [('行气路线', {'xiaozhoutian': '小周天'}.get(route, route)), *stats]


def movement_stats(effects):
    if not effects:
        return []
    return [
        ('最大闪避距离', blocks(effects['dash_distance'])),
        ('无敌时间', seconds(effects['invulnerable_ticks'])),
        ('基础耗灵', f"{effects['qi_cost']} 点"),
        ('冷却', seconds(effects['cooldown_ticks'])),
    ]


def wave_stats(wave, cost):
    scale = wave['charge_scale']
    return [
        ('基础耗灵', f'{cost} 点（满蓄力 {ceil(cost * scale)} 点）'),
        ('满蓄力时间', seconds(wave['charge_ticks'])),
        ('剑气射程', f"{blocks(wave['range'])}（满蓄力 {blocks(wave['range'] * scale)}）"),
        ('伤害倍率', f"{multiplier(wave['damage_multiplier'])}（满蓄力 {multiplier(wave['damage_multiplier'] * scale)}）"),
        ('剑气飞完后的恢复', seconds(wave['recovery_ticks'])),
        ('最多命中目标', str(wave['maximum_targets'])),
    ]


def weapon_stats(art, cost):
    stats = [('基础耗灵', f'{cost} 点'), ('冷却', seconds(art['cooldown_ticks']))]
    if art['requires_ground']:
        stats.append(('施放姿态', '站在地面'))
    if art['guard_reduction']:
        stats.extend([
            ('格挡减伤', percent(art['guard_reduction'] * 100)),
            ('格挡角度', f"{art['guard_arc_degrees']:g}°"),
            ('反击倍率', multiplier(art['counter_multiplier'])),
        ])
    return stats


def locked_sword_stats(art):
    lock = art['lock']
    stats = [
        ('锁定距离', blocks(lock['range'])),
        ('锁定角度', f"{lock['acquire_arc_degrees']:g}°"),
        ('首击等待', seconds(lock['first_strike_ticks'])),
        ('打击次数', str(lock['strikes'])),
        ('每段伤害倍率', multiplier(art['phases'][0]['damage_multiplier'])),
    ]
    if lock['strikes'] > 1:
        stats.extend([
            ('打击间隔', seconds(lock['strike_interval_ticks'])),
            ('末次伤害附加倍率', multiplier(lock['final_multiplier'])),
        ])
    if lock['mode'] == 'array' or lock['splash_multiplier'] > 0:
        stats.append(('范围半径', blocks(lock['radius'])))
    if lock['splash_multiplier'] > 0:
        stats.append(('范围追加伤害倍率', multiplier(lock['splash_multiplier'])))
    if lock['armor_break_fraction'] > 0:
        stats.append(('护甲削弱', f"{percent(lock['armor_break_fraction'] * 100)}，持续 {seconds(lock['armor_break_ticks'])}"))
    if lock['slow_ticks'] > 0:
        stats.append(('迟滞时间', seconds(lock['slow_ticks'])))
    return stats


def elemental_stats(art, rules):
    spell = art['elemental']
    shape = spell['shape']
    stats = [('效果形状', SHAPES[shape]), ('起手时间', seconds(spell['windup_ticks']))]
    if shape == 'self':
        stats.extend([
            ('护体减伤', percent(spell['ward_fraction'] * 100)),
            ('护体基础时间', seconds(spell['ward_ticks'])),
            ('每层本系势延长', seconds(spell['ward_ticks_per_charge'])),
            ('五系护体共享冷却', seconds(rules['ward_shared_cooldown_ticks'])),
        ])
        if spell['heal_per_charge'] > 0:
            stats.append(('每层本系势治疗倍率', multiplier(spell['heal_per_charge'])))
        if spell['speed_bonus'] > 0:
            stats.append(('移动速度加成', percent(spell['speed_bonus'] * 100)))
        if spell['push'] > 0:
            stats.append(('击退范围半径', blocks(spell['radius'])))
    else:
        stats.extend([
            ('作用距离' if shape == 'cone' else '最远施法距离',
             blocks(spell['radius'] * 2 if shape == 'cone' else spell['range'])),
            ('单段伤害倍率', multiplier(spell['damage_multiplier'])),
            ('单段最多目标', str(spell['maximum_targets'])),
        ])
        if shape in ('burst', 'field'):
            stats.append(('范围半径', blocks(spell['radius'])))
        if spell['pulses'] > 1:
            stats.append(('段数与间隔', f"{spell['pulses']} 段，每段间隔 {seconds(spell['pulse_interval_ticks'])}"))
    if spell['slow_ticks'] > 0:
        stats.append(('迟滞时间', seconds(spell['slow_ticks'])))
    if spell['armor_break_fraction'] > 0:
        stats.append(('护甲削弱', f"{percent(spell['armor_break_fraction'] * 100)}，持续 {seconds(spell['armor_break_ticks'])}"))
    if spell['push'] > 0:
        stats.append(('击退强度', f"{spell['push']:g}"))
    if spell['dot']:
        stats.append(('持续效果', '留下本系持续伤害'))
    if spell['harvest']:
        stats.append(('持续效果', '结算自身施加的本系持续伤害'))
    return stats


def legacy_stats(art):
    stats = [
        ('闪身距离' if art['mode'] == 'shadow' else '最远作用距离', blocks(art['range'])),
        ('最长持续时间', seconds(art['duration_ticks'])),
    ]
    if art['mode'] != 'shadow':
        stats.extend([
            ('剑身判定半径' if art['mode'] == 'sword' else '作用半径', blocks(art['radius'])),
            ('单次伤害倍率', multiplier(art['damage'])),
        ])
    if art['mode'] == 'sword':
        stats.extend([
            ('出剑速度', f"{art['speed']:g} 格/tick"),
            ('召回速度', f"{art['return_speed']:g} 格/tick"),
        ])
    return stats


def active_effect(id, effects, arts, waves, legacy, elemental_rules, labels, weapon_name):
    active = effects.get('active')
    if not active:
        return {'name': '效果待配置', 'stats': []}
    cost = active['qi_cost']
    art = arts.get(id)
    if art is None:
        wave = waves.get(active['skill'])
        if wave is None:
            return {'name': '效果待配置', 'stats': []}
        return {'name': '蓄力剑气', 'stats': [('施放武器', weapon_name), *wave_stats(wave, cost)]}
    if 'elemental' in art:
        return {
            'name': '五行法术',
            'stats': [('施放武器', '不限'), *weapon_stats(art, cost), *elemental_stats(art, elemental_rules)],
        }
    if art['family'] == 'legacy':
        special = legacy[id]
        item = special.get('item')
        weapon = '已接入剑式的近战剑' if special['mode'] == 'sword' else (
            labels.get(f'item.yuanxian.{item}', item) if item else '不限'
        )
        return {
            'name': '遗迹绝技', 'note': special['description'],
            'stats': [('施放武器', weapon), *weapon_stats(art, cost), *legacy_stats(special)],
        }
    stats = [('施放武器', weapon_name), *weapon_stats(art, cost)]
    if 'lock' in art:
        return {'name': '锁定剑诀', 'stats': [*stats, *locked_sword_stats(art)]}
    phases = [
        (f'第 {number} 段', ' · '.join([
            SHAPES[phase['shape']], blocks(phase['reach']),
            f"伤害 {multiplier(phase['damage_multiplier'])}", f"最多 {phase['maximum_targets']} 个目标",
        ]))
        for number, phase in enumerate(art['phases'], start=1)
    ]
    return {'name': '多段武技', 'stats': stats, 'phases': phases}


def learning_stats(definition, labels, schools, aptitudes, names, growth):
    if definition['grade'] == 0:
        return [('学习方式', '觉醒灵根后，通过传承碑习得')]
    req = definition['requirements']
    minimum = aptitudes['learning_requirements'][str(definition['grade'])]
    thresholds = dict(req.get('minimum_element_aptitude', {}))
    for element in definition['elements']:
        thresholds[element] = max(minimum, thresholds.get(element, 0))
    stats = [
        (f'{element_name(labels, element)}系资质', f'至少 {points} 点')
        for element, points in thresholds.items() if points
    ]
    school = schools.get(definition.get('school'), {})
    if minimum and school.get('weapon_family') in aptitudes['weapons']:
        stats.append((f"{labels[school['translation_key']]}类资质", f'至少 {minimum} 点'))
    previous = definition.get('lineage', {}).get('previous')
    if previous:
        prior_gate = '已学'
        if growth.get('require_previous_mastery'):
            prior_gate = '圆满并完成参悟' if growth.get('require_previous_insight') else '圆满'
        stats.append(('前置功法', f'《{names[previous]}》{prior_gate}'))
    study = definition.get('study')
    if study:
        stats.append(('研读所需点数', f"{study['points']:,} 点"))
        if study['gates']:
            stats.append(('研读关卡', f"{study['gates']} 道，每道消耗 {study['gate_stability_cost']} 点稳定度"))
    return stats


def growth_details(growth, stage_names):
    stages = []
    for i, name in enumerate(stage_names):
        bonuses = []
        for key, values in growth['bonuses'].items():
            value = values[i]
            if value == 0:
                continue
            if key == 'recovery_quarters':
                amount = f'{value / 4:+g} 点/秒'
            elif key in ('flat_normal', 'flat_stone'):
                amount = f'{value:+g} 点'
            else:
                amount = f'{value / 100:+.4g}%'
            bonuses.append(f'{GROWTH_EFFECTS[key]} {amount}')
        threshold = f"{growth['thresholds'][i]} 点修习"
        stages.append((name, ' · '.join([threshold, *bonuses])))
    return {
        'stages': stages,
        'choices': [(row['name'], row['description']) for row in growth['insights']],
    }


def build(root: Path):
    resources = root / 'src/main/resources'
    data = resources / 'data/yuanxian'
    labels = read_json(resources / 'assets/yuanxian/lang/zh_cn.json')
    definitions = registry(data, 'technique')
    names = {id: labels[row['translation_key']] for id, row in definitions.items()}
    schools = registry(data, 'school')
    realms = registry(data, 'realm')
    realm_names = {id: labels[realm['translation_key']] for id, realm in realms.items()}
    stages = {}
    for id, realm in realms.items():
        realm_name = realm_names[id]
        for stage in realm['stages']:
            name = labels[stage['translation_key']]
            stages[stage['id']] = name if name.startswith(realm_name) else f'{realm_name} · {name}'

    heritage_names = defaultdict(list)
    for heritage in registry(data, 'heritage').values():
        chain = heritage['techniques']
        for index, id in enumerate(chain, start=1):
            heritage_names[id].append(f"{labels[heritage['translation_key']]}（第 {index} / {len(chain)} 篇）")

    world_rules = read_json(data / 'world_sim/rules.json')
    aptitudes = read_json(data / 'aptitudes/rules.json')
    growth = read_json(data / 'cultivation/technique_growth.json')
    elemental = read_json(data / 'combat/elemental_arts.json')
    arts = {art['id']: art for art in elemental['arts']}
    arts.update({
        art['id']: art
        for filename in ('advanced_arts', 'locked_sword_arts', 'legacy_arts')
        for art in read_json(data / f'combat/{filename}.json')['arts']
    })
    waves = {row['id']: row for row in map(read_json, (data / 'combat/skill').glob('*.json'))}
    legacy = {f"yuanxian:{row['id']}": row for row in read_json(data / 'legacy/encounters.json')['arts']}
    entries = []
    for id, definition in definitions.items():
        grade = definition['grade']
        category = definition['category']
        elements = [element_name(labels, element) for element in definition['elements']]
        school = schools.get(definition.get('school'))
        school_name = labels[school['translation_key']] if school else None
        effects = definition.get('effects', {})
        specialized = growth['techniques'].get(id, {})
        minimum = definition['requirements']
        realm = stages.get(minimum.get('minimum_stage')) or realm_names.get(minimum.get('minimum_realm'), '不限')
        entry = {
            'id': id, 'name': names[id], 'grade': grade, 'category': category, 'elements': elements,
            'realm': realm, 'school': school_name,
            'heritages': heritage_names[id],
            'learning': learning_stats(definition, labels, schools, aptitudes, names, specialized),
        }
        if category == 'core':
            effect = {'name': '心法效果', 'stats': core_stats(definition, effects.get('core', {}), world_rules, elements)}
        elif category == 'movement':
            stats = movement_stats(effects.get('movement'))
            effect = {'name': '闪避效果' if stats else '效果待配置', 'stats': stats}
        elif category == 'active':
            weapon_name = f'{school_name}类' if school and school.get('weapon_family') else '不限'
            effect = active_effect(id, effects, arts, waves, legacy, elemental['rules'], labels, weapon_name)
        else:
            body = effects.get('body', {})
            stats = [(labels.get(f"attribute.name.{key.split(':')[1]}", key), f'{amount:+g}')
                     for key, amount in body.get('attributes', {}).items()]
            if 'stagger_resistance' in body:
                stats.append(('抗硬直值', f"{body['stagger_resistance']:g}"))
            effect = {'name': '炼体配置' if stats else '效果待配置', 'stats': stats}
        entry['effect'] = effect
        if specialized:
            entry['growth'] = growth_details(specialized, growth['stage_names'])
        entries.append(entry)
    version = next(line.partition('=')[2].strip() for line in
                   (root / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    return {
        'version': version,
        'categories': [
            {'id': key, 'name': labels[f'screen.yuanxian.cultivation.category.{key}'], 'description': text}
            for key, text in CATEGORIES.items()
        ],
        'grades': [labels[f'screen.yuanxian.cultivation.grade_name.{i}'] for i in range(len(CORE_GRADES))],
        'techniques': sorted(entries, key=lambda row: (row['grade'], row['id'])),
    }
