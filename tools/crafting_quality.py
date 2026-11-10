"""读取炼丹与炼器共用配置，并计算五种结果的理论概率。"""
from __future__ import annotations

import json
import math
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = Path('src/main/resources/data/myvillage/crafting/quality.json')
MAX_SCORE = 2_147_483_647


def load_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'重复 JSON 字段：{key}')
            result[key] = value
        return result
    def invalid(value):
        raise ValueError(f'非有限数值：{value}')
    return json.loads(raw, object_pairs_hook=unique, parse_constant=invalid)


def parse(data):
    def fields(value, names):
        if not isinstance(value, dict) or set(value) != set(names):
            raise ValueError(f'字段必须为：{sorted(names)}')
    def number(value, low=None, high=None, integer=False, positive=False):
        valid = type(value) is int if integer else type(value) in (int, float)
        try:
            valid = (valid and math.isfinite(value) and (low is None or value >= low)
                     and (high is None or value <= high) and (not positive or value > 0))
        except OverflowError:
            valid = False
        if not valid:
            raise ValueError(f'无效数值：{value!r}')
    fields(data, ('version', 'initial_roll', 'standard_deviation', 'maximum_difficulty',
                  'difficulty_threshold_step', 'tiers'))
    number(data['version'], 1, 1, integer=True)
    fields(data['initial_roll'], ('min', 'max'))
    number(data['initial_roll']['min'], 0, MAX_SCORE, integer=True)
    number(data['initial_roll']['max'], data['initial_roll']['min'], MAX_SCORE - 1, integer=True)
    number(data['standard_deviation'], positive=True)
    number(data['maximum_difficulty'], 0, 64, integer=True)
    number(data['difficulty_threshold_step'], positive=True)
    if not isinstance(data['tiers'], list) or not data['tiers']:
        raise ValueError('至少需要一个品阶')
    seen = set()
    for tier in data['tiers']:
        fields(tier, ('id', 'baseline', 'span', 'thresholds'))
        if (not isinstance(tier['id'], str) or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+', tier['id'])
                or tier['id'] in seen):
            raise ValueError('品阶 ID 无效或重复')
        seen.add(tier['id'])
        number(tier['baseline'], 0, MAX_SCORE, integer=True)
        number(tier['span'], positive=True)
        cuts = tier['thresholds']
        if not isinstance(cuts, list) or len(cuts) != 4:
            raise ValueError('每个品阶需要四个阈值')
        for cut in cuts:
            number(cut)
        if any(a >= b for a, b in zip(cuts, cuts[1:])):
            raise ValueError('阈值必须严格递增')
    return data


def load(root=ROOT):
    return parse(load_json((Path(root) / RESOURCE).read_bytes()))


def tier(rules, tier_id):
    for value in rules['tiers']:
        if value['id'] == tier_id:
            return value
    raise ValueError(f'未知品阶：{tier_id}')


def thresholds(rules, tier_id, difficulty):
    if type(difficulty) is not int or not 0 <= difficulty <= rules['maximum_difficulty']:
        raise ValueError('丹方难度超出配置范围')
    return [cut + difficulty * rules['difficulty_threshold_step'] for cut in tier(rules, tier_id)['thresholds']]


def probabilities(rules, aptitude, tier_id, difficulty):
    if type(aptitude) is not int or not 0 <= aptitude <= MAX_SCORE:
        raise ValueError('资质必须为非负整数')
    value = tier(rules, tier_id)
    mean = 100 * (aptitude - value['baseline']) / value['span']
    cuts = thresholds(rules, tier_id, difficulty)
    cdf = lambda x: .5 * math.erfc(-x / math.sqrt(2))
    cumulative = [cdf((cut - mean) / rules['standard_deviation']) for cut in cuts]
    return [cumulative[0], *(b - a for a, b in zip(cumulative, cumulative[1:])),
            cdf((mean - cuts[-1]) / rules['standard_deviation'])]
