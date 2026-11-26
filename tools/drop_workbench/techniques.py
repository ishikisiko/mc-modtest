"""从游戏资源导出源仙工作台的功法目录。"""
from __future__ import annotations

import json
from collections import defaultdict
from math import ceil
from pathlib import Path

CATEGORIES = {
    'core': '攻、防守和灵力与修炼各装备一门。详情列出常驻加值和额外效果的触发条件。',
    'active': '学会绝技后可在技能页分配按键，实际效果取决于招式。',
    'movement': '身法决定闪避的位移距离、无敌时间、耗灵和冷却。',
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


def fraction_percent(value):
    return percent(value * 100)


def degrees(value):
    return f'{value:g}°'


def multiplier_bonus(value):
    return f'{value:+g}'


def element_name(labels, id):
    return labels['cultivation.element.' + id.replace(':', '.')]


def heart_effect(heart, price, elements):
    base, extra, stats = [], [], []
    direction = heart['direction']
    school = HEART_DIRECTIONS[direction]
    element = '、'.join(elements) or '五行'
    weapon = direction in ('sword', 'spear', 'fist')
    trigger = heart.get('extra')

    def add(rows, label, value, unit='点'):
        if value:
            rows.append((label, f'{value:+g} {unit}'))

    if heart['slot'] == 'attack':
        add(base, '攻击', heart['attack'])
        add(base, '固定破防', heart['flat_penetration'])
        if trigger:
            if direction == 'spell':
                action = element + '法术'
            elif trigger.get('paid_only'):
                action = (school + '系' if weapon else '') + '耗灵绝技'
            elif trigger.get('art_only'):
                action = (school + '系' if weapon else '') + '绝技'
            else:
                action = (school + '系' if weapon else '') + '攻击'
            add(extra, action + '命中时的伤害基数', trigger['amount'])
    elif heart['slot'] == 'defense':
        add(base, '生命上限', heart['max_health'])
        add(base, '肉身防御', heart['defense'])
        attributes = heart.get('attributes', {})
        add(base, '护甲', attributes.get('armor', 0))
        add(base, '护甲韧性', attributes.get('armor_toughness', 0))
        add(base, '物理抗性', attributes.get('physical_resistance', 0) * 100, '个百分点')
        add(base, '击退抗性', attributes.get('knockback_resistance', 0) * 100, '个百分点')
        if trigger:
            incoming = element + '伤害' if direction == 'spell' else {
                'melee': '近战攻击', 'ranged': '投射物或五行法术', 'none': '攻击',
            }[trigger.get('guard', 'none')]
            hold = '持' + school if weapon else ''
            add(extra, hold + '受到' + incoming + '时的肉身防御', trigger['amount'])
        if shield := heart.get('shield'):
            threshold = fraction_percent(shield['health_threshold'])
            extra.extend([
                ('护盾触发', f'敌方命中使生命从高于 {threshold} 降至 {threshold} 或以下且存活'),
                ('护盾容量', f"{shield['amount']:g} 点"),
                ('护盾持续时间', seconds(shield['duration_ticks'])),
                ('护盾冷却', seconds(shield['cooldown_ticks'])),
            ])
    else:
        add(base, '灵力上限', heart['capacity'])
        add(base, '自然回灵', heart['recovery_quarters'] / 4, '点/秒')
        add(base, '每批修炼修为', heart['cultivation'])
        add(extra, '每批灵石修炼额外修为', heart.get('stone_extra', 0))
    if price:
        stats.append(('游戏内换取秘籍', f'{price:,} 颗下品灵石'))
    return {'name': '秘籍', 'stats': stats, 'base': base, 'extra': extra}


def sacred_heart_effect(stages, realm_names):
    return {
        'name': '分境界加值',
        'note': '装备这门心法时，按当前大境界增加生命上限和肉身防御。',
        'stats': [
            (realm_names[stage['realm']], f"生命上限 +{stage['health']:g} 点，肉身防御 +{stage['defense']:g} 点")
            for stage in stages
        ],
    }


def legacy_core_stats(definition, effects, world_rules, element_names):
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
        ('地面闪避耗灵', '0 点'),
        ('空中闪避耗灵', f"{effects['qi_cost']} 点（需有空中闪避资格）"),
        ('地面与空中共享冷却', seconds(effects['cooldown_ticks'])),
    ]


def wave_stats(wave, cost):
    scale = wave['charge_scale']
    release = wave['release_ticks']
    full_release = release - wave.get('charge_pose_ticks', 0)
    width, height = wave['half_width'] * 2, wave['half_height'] * 2
    return [
        ('基础耗灵', f'{cost} 点（满蓄力 {ceil(cost * scale)} 点）'),
        ('满蓄力时间', seconds(wave['charge_ticks'])),
        ('松键到出剑气', f'{seconds(release)}（满蓄力 {seconds(full_release)}）'),
        ('剑气射程', f"{blocks(wave['range'])}（满蓄力 {blocks(wave['range'] * scale)}）"),
        ('伤害倍率', f"{multiplier(wave['damage_multiplier'])}（满蓄力 {multiplier(wave['damage_multiplier'] * scale)}）"),
        ('剑气速度', f"{wave['speed']:g} 格/tick"),
        ('命中宽度', f'{blocks(width)}（满蓄力 {blocks(width * scale)}）'),
        ('命中高度', f'{blocks(height)}（满蓄力 {blocks(height * scale)}）'),
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
    phase = art['phases'][0]
    stats = [
        ('锁定距离', blocks(lock['range'])),
        ('锁定角度', degrees(lock['acquire_arc_degrees'])),
        ('首击等待', seconds(lock['first_strike_ticks'])),
        ('打击次数', str(lock['strikes'])),
        ('每段伤害倍率', multiplier(phase['damage_multiplier'])),
        ('收招时间', seconds(lock['recovery_ticks'])),
    ]
    if lock['strikes'] > 1:
        stats.extend([
            ('打击间隔', seconds(lock['strike_interval_ticks'])),
            ('末次伤害附加倍率', multiplier(lock['final_multiplier'])),
        ])
    if lock['mode'] == 'array' or lock['splash_multiplier'] > 0:
        stats.append(('范围半径', blocks(lock['radius'])))
    if lock['splash_multiplier'] > 0:
        stats.extend([
            ('最多命中目标', f"{phase['maximum_targets']} 个（含主目标）"),
            ('次级目标伤害系数', f"主目标系数的 {fraction_percent(lock['splash_multiplier'])}"),
        ])
    elif lock['mode'] == 'array':
        stats.append(('单段最多命中目标', f"{phase['maximum_targets']} 个"))
    if lock['armor_break_fraction'] > 0:
        stats.append(('护甲削弱', f"{percent(lock['armor_break_fraction'] * 100)}，持续 {seconds(lock['armor_break_ticks'])}"))
    if lock['slow_ticks'] > 0:
        stats.append(('阵内迟缓', f"{lock['slow_amplifier'] + 1} 级，每次持续 {seconds(lock['slow_ticks'])}"))
    return stats


def elemental_stats(art, rules):
    spell = art['elemental']
    shape = spell['shape']
    stats = [
        ('效果形状', SHAPES[shape]),
        ('起手时间', seconds(spell['windup_ticks'])),
        ('收招时间', seconds(spell['recovery_ticks'])),
    ]
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
        stats.extend([
            ('持续伤害', f"每跳 {multiplier(rules['dot_multiplier'])} 法术伤害基数"),
            ('持续伤害间隔', seconds(rules['dot_interval_ticks'])),
            ('持续伤害时长', seconds(rules['dot_lifetime_ticks'])),
        ])
    if spell['harvest']:
        stats.append(('持续效果', '结算自身施加的本系持续伤害'))
    return stats


# 执行器复用参数名，同一个字段在不同招式里可能代表伤害、治疗或减伤。
REALM_ART_EFFECTS = {
    'brace_spear': (('secondary', '满足逼近条件时的枪锋倍率', multiplier),
                    ('threshold', '触发所需的目标逼近距离', blocks)),
    'vine_line': (('potency', '命中后减速', fraction_percent),),
    'earth_spike': (('push', '击飞速度', lambda value: f'{value:g} 格/tick'),),
    'lingering_slash': (('secondary', '留风斩伤害倍率', multiplier),),
    'sweeping_spear': (('secondary', '枪锋伤害倍率', multiplier),),
    'back_guard': (('potency', '挡下一次正面命中的减伤', fraction_percent),),
    'armor_line': (('secondary', '贯穿后第二个目标的伤害倍率', multiplier),
                   ('threshold', '触发贯穿所需的首个目标护甲', str)),
    'healing_branch': (('potency', '治疗量（双方较低生命上限的比例）', fraction_percent),),
    'reflected_wave': (('secondary', '反射后伤害倍率', multiplier),),
    'mature_flame': (('secondary', '成熟火种的伤害倍率', multiplier),),
    'piercing_sword': (('bonus', '每消耗一层剑意增加倍率', multiplier_bonus),),
    'crossing_traces': (('bonus', '两道剑痕交会额外增加倍率', multiplier_bonus),),
    'tip_charge': (('secondary', '非枪锋伤害倍率', multiplier),),
    'retreating_thrust': (('bonus', '对追近的敌人额外增加倍率', multiplier_bonus),),
    'stored_punch': (('bonus', '每消耗一层拳势增加倍率', multiplier_bonus),),
    'armor_lock': (('status', '削弱护甲', fraction_percent),),
    'metal_chain': (('secondary', '第一次弹跳伤害倍率', multiplier),
                    ('bonus', '后续弹跳伤害倍率', multiplier)),
    'wood_trail': (('status', '命中后减速', fraction_percent),),
    'frost_shatter': (('secondary', '引爆自身施加的迟滞时的伤害倍率', multiplier),),
    'fire_fuse': (('secondary', '到期爆炸伤害倍率', multiplier),
                  ('bonus', '提前引爆伤害倍率', multiplier)),
    'stone_stamp': (('secondary', '外圈伤害倍率', multiplier),),
    'twin_blade': (('secondary', '剑影反斩伤害倍率', multiplier),),
    'sword_gate': (('control', '穿过剑线时减速', fraction_percent),),
    'sword_points': (('count', '剑点数量上限', str),
                     ('bonus', '剑光交会额外增加倍率', multiplier_bonus)),
    'opposing_spears': (('secondary', '枪锋伤害倍率', multiplier),),
    'spear_posts': (('count', '枪桩数量', str),),
    'cleaving_charge': (('secondary', '其余命中目标的伤害倍率', multiplier),),
    'spear_array': (('count', '枪阵出击次数', str),
                    ('per_target', '每个敌人最多受击次数', str)),
    'guarding_echo': (('count', '每名友方可触发的挡击次数', str),
                      ('control', '每次挡击吸收比例', fraction_percent),
                      ('budget', '总挡击上限（双方较低生命上限的比例）', fraction_percent)),
    'piercing_fist': (('secondary', '透劲伤害倍率', multiplier),),
    'dragon_stance': (('control', '架势期间正面减伤', fraction_percent),
                      ('budget', '每档承伤（自身生命上限的比例）', fraction_percent),
                      ('bonus', '每档承伤增加反击倍率', multiplier_bonus),
                      ('secondary', '反击伤害倍率上限', multiplier)),
    'falling_palm': (('secondary', '外圈伤害倍率', multiplier),),
    'armor_star': (('secondary', '目标已破甲时的伤害倍率', multiplier),),
    'gold_plates': (('budget', '挡击上限（双方较低生命上限的比例）', fraction_percent),),
    'armor_stamp': (('secondary', '外侧伤害倍率', multiplier),
                    ('bonus', '每层金势增加倍率', multiplier_bonus),
                    ('control', '内圈首个命中削弱护甲', fraction_percent)),
    'linked_roots': (('count', '治疗人数上限', str),
                     ('budget', '每人治疗量（双方较低生命上限的比例）', fraction_percent)),
    'rescue_vine': (('budget', '护盾量（双方较低生命上限的比例）', fraction_percent),),
    'wood_harvest': (('secondary', '剩余持续伤害转为治疗的比例', fraction_percent),
                     ('count', '治疗人数上限', str),
                     ('control', '每人治疗上限（双方较低生命上限的比例）', fraction_percent),
                     ('budget', '总治疗上限（自身生命上限的比例）', fraction_percent)),
    'ancient_shade': (('secondary', '树荫内圈伤害倍率', multiplier),),
    'meeting_tides': (('secondary', '两股水流会合时的伤害倍率', multiplier),),
    'flow_lane': (('control', '水道内敌方减速', fraction_percent),
                  ('bonus', '水道内友方加速', fraction_percent)),
    'returning_sea': (('secondary', '退潮内圈伤害倍率', multiplier),
                      ('bonus', '退潮水线伤害倍率', multiplier)),
    'freezing_crest': (('control', '触碰冰线时减速', fraction_percent),),
    'fire_escape': (('control', '受敌方近战命中触发加速', fraction_percent),),
    'fire_harvest': (('secondary', '扩散伤害倍率', multiplier),
                     ('bonus', '首个目标引燃增加倍率上限', multiplier_bonus),
                     ('control', '其余目标引燃增加倍率上限', multiplier_bonus)),
    'moving_wall': (('count', '最多移动次数', str),),
    'steady_platform': (('control', '台上友方击退减免', fraction_percent),),
    'folding_earth': (('secondary', '合拢时中心地脉的伤害倍率', multiplier),),
    'mountain_shell': (('control', '每次命中吸收比例', fraction_percent),
                       ('budget', '总吸收上限（自身生命上限的比例）', fraction_percent)),
}

REALM_ART_LABELS = {
    'arrow_ring': {'duration': '截矢窗口'},
    'back_guard': {'duration': '护背窗口'},
    'healing_branch': {'duration': '等待治疗触发的时间'},
    'lingering_slash': {'delay': '剑影出斩延迟'},
    'waiting_flame': {'delay': '火线爆发延迟'},
    'stone_screen': {'targets': '最多拦截投射物', 'duration': '石屏持续时间'},
    'mature_flame': {'duration': '火种持续时间', 'delay': '可以引爆前的等待',
                     'warning': '引爆预警时间', 'status_ticks': '火种成熟时间'},
    'crossing_traces': {'duration': '放置第二道剑痕的窗口', 'interval': '剑痕交会预警时间'},
    'fire_fuse': {'duration': '火引信自然爆炸前的时间'},
    'wood_trail': {'interval': '记录藤径的时长', 'duration': '藤径持续时间', 'status_ticks': '减速持续时间'},
    'armor_lock': {'status_ticks': '护甲削弱持续时间'},
    'vine_line': {'status_ticks': '减速持续时间'},
    'twin_blade': {'delay': '剑影反斩延迟'},
    'evade_echo': {'duration': '可触发回影斩的闪避窗口', 'delay': '闪避成功后出斩延迟'},
    'sword_points': {'delay': '再次施放后剑点出斩延迟'},
    'spear_posts': {'gap': '枪桩间距'},
    'dragon_stance': {'duration': '架势持续时间'},
    'gold_mirror': {'delay': '折光攻击延迟'},
    'armor_stamp': {'status_ticks': '护甲削弱持续时间'},
    'linked_roots': {'window': '放置第二根藤的窗口', 'duration': '连根持续时间'},
    'rescue_vine': {'step': '靠近友方的最大位移', 'status_ticks': '护盾持续时间'},
    'ancient_shade': {'delay': '敌方在树荫内滞留的触发时间'},
    'meeting_tides': {'delay': '两股水流会合延迟'},
    'freezing_crest': {'status_ticks': '减速持续时间', 'interval': '冰线推进间隔'},
    'paired_furnaces': {'delay': '触发后火线爆发延迟'},
    'fire_escape': {'duration': '等待受击触发的时间', 'status_ticks': '加速持续时间'},
    'fire_enclosure': {'gap': '火场缺口宽度', 'arc': '再次施放时缺口转动角度'},
    'moving_wall': {'step': '每次推动石墙的最大距离'},
    'folding_earth': {'delay': '地脉合拢延迟'},
}


def realm_art_stats(spec, golden_rules):
    mechanic = spec['mechanic']
    labels = REALM_ART_LABELS.get(mechanic, {})
    windup = spec['windup'] if 'windup' in spec else golden_rules['windup']
    recovery = golden_rules['recovery'] if golden_rules else spec['recovery']
    stats = [
        ('起手时间', seconds(windup)),
        ('收招时间', seconds(recovery)),
        ('最远作用距离', blocks(spec['range'])),
        (labels.get('targets', '目标数量上限'), str(spec['targets'])),
    ]
    fields = (
        ('damage', '基础伤害倍率', multiplier),
        ('length', '攻击长度', blocks),
        ('width', '命中宽度', blocks),
        ('radius', '作用半径', blocks),
        ('height', '高度', blocks),
        ('inner', '内圈或中心线尺寸', blocks),
        ('reach', '延伸距离', blocks),
        ('secondary_range', '二次命中距离', blocks),
        ('gap', '间距', blocks),
        ('arc', '扇形角度', degrees),
        ('step', '最大位移', blocks),
        ('delay', '触发延迟', seconds),
        ('duration', '效果持续时间', seconds),
        ('status_ticks', '状态持续时间', seconds),
        ('interval', '分段间隔', seconds),
        ('window', '再次施放窗口', seconds),
        ('warning', '预警时间', seconds),
    )
    stats.extend(
        (labels.get(key, label), format_value(spec[key]))
        for key, label, format_value in (*fields, *REALM_ART_EFFECTS.get(mechanic, ()))
        if spec.get(key)
    )
    if spec.get('push') and mechanic != 'earth_spike':
        stats.append(('位移距离', blocks(spec['push'])))
    return stats


def legacy_stats(art, rules):
    mode = art['mode']
    stats = [
        ('闪身距离' if mode == 'shadow' else '最远作用距离', blocks(art['range'])),
        ('最长持续时间', seconds(art['duration_ticks'])),
        ('触发后续效果时耗灵', '0 点'),
    ]
    if mode != 'shadow':
        stats.extend([
            ('剑身判定半径' if mode == 'sword' else '作用半径', blocks(art['radius'])),
            ('单次伤害倍率', multiplier(art['damage'])),
        ])
    if mode == 'sword':
        stats.extend([
            ('出剑速度', f"{art['speed']:g} 格/tick"),
            ('召回速度', f"{art['return_speed']:g} 格/tick"),
            ('单程最多命中目标', str(rules['projection_targets'])),
        ])
    elif mode == 'ruler':
        stats.extend([
            ('震击间隔', seconds(rules['ruler_interval_ticks'])),
            ('迟缓', f"{rules['ruler_slow_amplifier'] + 1} 级，持续 {seconds(rules['ruler_slow_ticks'])}"),
            ('每次震击最多命中目标', str(rules['max_targets'])),
        ])
    elif mode == 'fire':
        stats.extend([
            ('同时存在的火种上限', str(rules['max_fire_seeds'])),
            ('单个火种最多命中目标', str(rules['max_targets'])),
        ])
    return stats


def weapon_phase_summary(phase, move):
    shape = phase['shape']
    details = [
        SHAPES[shape], blocks(phase['reach']),
        f"伤害 {multiplier(phase['damage_multiplier'])}",
        f"最多 {phase['maximum_targets']} 个目标",
    ]
    if shape == 'line':
        details.append(f"命中宽度 {blocks(phase['width'])}")
    elif shape == 'fan':
        details.append(f"扇形角度 {degrees(phase['arc_degrees'])}")
    step = phase['step_distance'] or move.get('step', {}).get('maximum_distance')
    if step:
        details.append(f'最大位移 {blocks(step)}')
    if hitstun := phase['reaction']['hitstun_ticks']:
        details.append(f'命中硬直 {seconds(hitstun)}')
    start, end = move['active_ticks']
    details.extend([
        f'起手 {seconds(start)}',
        f'命中窗口 {seconds(end - start + 1)}',
        f"收招 {seconds(move['total_ticks'] - end - 1)}",
    ])
    return ' · '.join(details)


def active_effect(id, effects, arts, waves, legacy, combat_rules, moves, labels, weapon_name):
    active = effects.get('active')
    if not active:
        return {'name': '效果待配置', 'stats': []}
    art = arts.get(id)
    if art is None:
        wave = waves.get(active['skill'])
        if wave is None:
            return {'name': '效果待配置', 'stats': []}
        return {'name': '蓄力剑气', 'stats': [('施放武器', weapon_name), *wave_stats(wave, active['qi_cost'])]}
    cost = active['qi_cost'] if 'lock' in art else art['qi_cost']
    spec = next((art[key] for key in ('golden_core', 'early_realm', 'ascendant') if key in art), None)
    if spec is not None:
        name = {'qi_refining': '炼气绝技', 'foundation_establishment': '筑基绝技',
                'nascent_soul': '元婴绝技', 'spirit_transformation': '化神绝技'}.get(spec.get('tier'), '金丹绝技')
        rules = combat_rules['golden_core'] if 'golden_core' in art else {}
        return {
            'name': name, 'note': labels[f"combat.yuanxian.art.{id.split(':', 1)[1]}.description"],
            'stats': [('施放武器', weapon_name), *weapon_stats(art, cost), *realm_art_stats(spec, rules)],
        }
    if 'elemental' in art:
        return {
            'name': '五行法术',
            'stats': [('施放武器', '不限'), *weapon_stats(art, cost), *elemental_stats(art, combat_rules['elemental'])],
        }
    if art['family'] == 'legacy':
        special = legacy[id]
        item = special.get('item')
        weapon = '已接入剑式的近战剑' if special['mode'] == 'sword' else (
            labels.get(f'item.yuanxian.{item}', item) if item else '不限'
        )
        return {
            'name': '遗迹绝技', 'note': special['description'],
            'stats': [('施放武器', weapon), *weapon_stats(art, cost), *legacy_stats(special, combat_rules['legacy'])],
        }
    stats = [('施放武器', weapon_name), *weapon_stats(art, cost)]
    if 'lock' in art:
        return {'name': '锁定剑诀', 'stats': [*stats, *locked_sword_stats(art)]}
    phases = [
        (f'第 {number} 段', weapon_phase_summary(phase, moves[phase['move']]))
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
    realm_names['yuanxian:mortal'] = '凡人启蒙'
    realm_order = sorted(realms, key=lambda key: realms[key]['sort_order'])
    stage_names = {
        (id, stage['id']): labels[stage['translation_key']]
        for id, realm in realms.items() for stage in realm['stages']
    }

    heritage_names = defaultdict(list)
    for heritage in registry(data, 'heritage').values():
        chain = heritage['techniques']
        for index, id in enumerate(chain, start=1):
            heritage_names[id].append(f"{labels[heritage['translation_key']]}（第 {index} / {len(chain)} 篇）")

    world_rules = read_json(data / 'world_sim/rules.json')
    aptitudes = read_json(data / 'aptitudes/rules.json')
    growth = read_json(data / 'cultivation/technique_growth.json')
    combat = data / 'combat'
    art_files = ('elemental', 'advanced', 'locked_sword', 'legacy', 'golden_core', 'early_realm', 'ascendant')
    art_docs = {family: read_json(combat / f'{family}_arts.json') for family in art_files}
    arts = {art['id']: art for document in art_docs.values() for art in document['arts']}
    waves = {row['id']: row for row in map(read_json, (combat / 'skill').glob('*.json'))}
    legacy_document = read_json(data / 'legacy/encounters.json')
    legacy = {f"yuanxian:{row['id']}": row for row in legacy_document['arts']}
    combat_rules = {
        'elemental': art_docs['elemental']['rules'],
        'golden_core': art_docs['golden_core']['rules'],
        'legacy': legacy_document['runtime'],
    }
    moves = {
        move['id']: move
        for style in read_json(combat / 'index.json')['styles']
        for move in read_json(combat / 'style' / f"{style.split(':', 1)[1]}.json")['moves']
    }
    entries = []
    for id, definition in definitions.items():
        category = definition['category']
        if category not in CATEGORIES:
            continue
        grade = definition['grade']
        elements = [element_name(labels, element) for element in definition['elements']]
        school = schools.get(definition.get('school'))
        school_name = labels[school['translation_key']] if school else None
        effects = definition.get('effects', {})
        specialized = growth['techniques'].get(id, {})
        minimum = definition['requirements']
        realm = minimum.get('minimum_realm')
        stage = minimum.get('minimum_stage')
        minimum_name = stage_names[(realm, stage)] if stage else realm_names.get(realm, '不限')
        entry = {
            'id': id, 'name': names[id], 'grade': grade, 'category': category, 'elements': elements,
            'realm': minimum_name, 'realm_id': realm, 'school': school_name,
            'heritages': heritage_names[id],
            'learning': learning_stats(definition, labels, schools, aptitudes, names, specialized),
        }
        if category == 'core':
            core = effects.get('core', {})
            if (heart := core.get('heart')) is not None:
                entry['heart'] = {'slot': HEART_SLOTS[heart['slot']], 'direction': HEART_DIRECTIONS[heart['direction']]}
                if id == 'yuanxian:sacred_earth_heart':
                    stages = read_json(data / 'fates/rework.json')['stages']
                    effect = sacred_heart_effect(stages, realm_names)
                    entry['learning'].insert(0, ('传授方式', f"天命「{labels['fate.yuanxian.sacred_earth_embryo']}」传授"))
                else:
                    effect = heart_effect(heart, core.get('manual_price'), elements)
            else:
                effect = {'name': '心法效果', 'stats': legacy_core_stats(definition, core, world_rules, elements)}
        elif category == 'movement':
            stats = movement_stats(effects.get('movement'))
            effect = {'name': '闪避效果' if stats else '效果待配置', 'stats': stats}
        else:
            weapon_name = f'{school_name}类' if school and school.get('weapon_family') else '不限'
            effect = active_effect(id, effects, arts, waves, legacy, combat_rules, moves, labels, weapon_name)
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
        'realms': [[id, realm_names[id]] for id in realm_order],
        'techniques': sorted(entries, key=lambda row: (row['grade'], row['id'])),
    }
