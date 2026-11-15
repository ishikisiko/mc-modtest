#!/usr/bin/env python3
"""Focused resource links for the exploration slice; no game or world generation."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def validate(root=ROOT):
    base = root / 'src/main/resources'
    data = json.loads((base / 'data/yuanxian/exploration/rules.json').read_text())
    languages = [json.loads((base / f'assets/yuanxian/lang/{lang}.json').read_text()) for lang in ('zh_cn', 'en_us')]
    errors = []
    required = set()
    for location in data['locations']:
        required.update('exploration.yuanxian.' + part + '.' + location['id'] for part in ('location', 'prepare'))
    for stage in data['milestones']:
        required.add('exploration.yuanxian.guide.' + stage['id'])
        required.update('exploration.yuanxian.tip.' + tip for tip in stage['tips'])
    for route in data['side_routes']:
        required.add('exploration.yuanxian.side.' + route['id'])
        required.update('exploration.yuanxian.tip.' + tip for tip in route['tips'])
    required.update('exploration.yuanxian.' + suffix for suffix in
                    ('portal.denied', 'guide.side', 'guide.side_hint'))
    bosses = {p['entity']: p for p in json.loads((base / 'data/yuanxian/exploration/bosses.json').read_text())['profiles']}
    for route in data['side_routes']:
        if route['entity'] not in bosses:
            errors.append('unknown side-route boss ' + route['entity'])
    if any('warden' in flag or 'wither' in flag or 'elder' in flag
           for step in data['milestones'] for flag in step['flags']):
        errors.append('optional boss must not gate main route')
    for location in data['locations']:
        if location['dimension'] != 'minecraft:overworld' and location['regional']:
            errors.append('non-Overworld location uses regional floor')
    if data['loot_tables'].get('minecraft:chests/end_city_treasure') != 'endgame':
        errors.append('End city must use its dedicated finite reward pool')
    end_pool = data['pools'].get('endgame', {})
    if not 1 <= end_pool.get('rolls', 0) <= 4:
        errors.append('End city rewards must be bounded')
    for lang in languages:
        errors.extend('missing translation ' + key for key in sorted(required - lang.keys()))
    for pool in data['pools'].values():
        for reward in pool['entries']:
            if reward['item'].startswith('yuanxian:') and 'item.' + reward['item'].replace(':', '.') not in languages[0]:
                errors.append('unknown reward item ' + reward['item'])
            if reward['technique']:
                technique = json.loads((base / ('data/yuanxian/yuanxian/technique/' + reward['technique'].split(':')[1] + '.json')).read_text())
                expected = 'yuanxian:manual_' + technique['category'] + '_' + {1: 'huang', 2: 'xuan', 3: 'di', 4: 'tian'}[technique['grade']]
                if reward['item'] != expected:
                    errors.append('unreadable manual ' + reward['technique'])
    modifiers = json.loads((base / 'data/neoforge/loot_modifiers/global_loot_modifiers.json').read_text())
    if modifiers.get('replace') is not False or modifiers['entries'].count('yuanxian:exploration') != 1:
        errors.append('modifier must append exactly once')
    if any(not table.startswith(('minecraft:chests/', 'minecraft:archaeology/')) for table in data['loot_tables']):
        errors.append('repeatable/non-exploration loot target')
    layouts = json.loads((base / 'data/yuanxian/npc_life/places.json').read_text())['layouts']
    town = next(layout for layout in layouts if layout['id'] == 'yunshan_planned_v4')
    for key, kind in [('home', 'rest'), ('work', 'social')]:
        if not any(place['id'] == data['guide_station'][key] and place['kind'] == kind for place in town['places']):
            errors.append('guide station missing from real town: ' + key)
    recipe = json.loads((base / 'data/yuanxian/recipe/chitong_from_blaze.json').read_text())
    if recipe['result'] != {'id': 'yuanxian:chitong_ingot', 'count': 1}:
        errors.append('incorrect Nether conversion result')
    return errors


if __name__ == '__main__':
    problems = validate()
    for problem in problems:
        print(problem)
    if problems:
        raise SystemExit(1)
    print('Exploration resource links match')
