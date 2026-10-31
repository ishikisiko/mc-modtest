"""从 authored 用途标签生成玩家名称；不参与几何归一化、排序或状态编号。"""
from __future__ import annotations
from tools.lord_manors.geometry import FULL
from tools.lord_manors.construction import bounds


def source_boxes(scene, overlay_roles):
    """保留分格来源，独立于几何生成；整格替换和覆盖材质沿用 cells 的规则。"""
    result = {}
    for box in scene['boxes']:
        if box[7] == 'edict_stone':
            continue
        extent = bounds(box)
        low, high = extent[:3], extent[3:]
        for y in range(low[1] // 16, (high[1] + 15) // 16):
            for z in range(low[2] // 16, (high[2] + 15) // 16):
                for x in range(low[0] // 16, (high[0] + 15) // 16):
                    offset = (x * 16, y * 16, z * 16)
                    piece = tuple(max(0, low[i] - offset[i]) for i in range(3)) + tuple(min(16, high[i] - offset[i]) for i in range(3))
                    if piece == FULL and box[6] not in overlay_roles:
                        result[x, y, z] = [box]
                    else:
                        result.setdefault((x, y, z), []).append(box)
    return result


def key(part):
    return 'manor_part.myvillage.' + part


def source_part(box, rules):
    role, kind = box[6], box[8]
    for rule in rules['rules']:
        if 'roles' in rule and role not in rule['roles']:
            continue
        if 'kinds' in rule and kind not in rule['kinds']:
            continue
        if 'axis' in rule:
            vertical = box[4] > max(box[3], box[5])
            horizontal = max(box[3], box[5]) > box[4]
            if not (vertical if rule['axis'] == 'vertical' else horizontal):
                continue
        return rule['part']
    return rules['role_fallbacks'][role]


def candidates(profile, sources, rules):
    roles = {box[6] for box in profile}
    result = {source_part(box, rules) for box in sources if box[6] in roles}
    # 题匾由 add_plaques 独立写入，不混入原盒体的造型身份。
    if any(role.startswith('plaque_') for role in roles):
        result.add('plaque')
    if not result:
        raise ValueError('雕构缺少用途来源')
    return result


def choose(parts, rules):
    if len(parts) == 1:
        return next(iter(parts))
    groups = {rules['parts'][part]['group'] for part in parts}
    for assembly in rules['assemblies']:
        if assembly['part'] in parts and groups <= set(assembly['groups']):
            return assembly['part']
    if len(groups) == 1:
        return rules['group_fallbacks'].get(next(iter(groups)), rules['fallback'])
    for fallback in rules['mixed_fallbacks']:
        if groups == set(fallback['groups']):
            return fallback['part']
    return rules['fallback']


def profile_info(profile, parts, rules):
    roles = sorted({box[6] for box in profile})
    return {'name': key(choose(parts, rules)),
            'materials': ['manor_material.myvillage.' + ('plaque' if role.startswith('plaque_') else role) for role in roles]}
