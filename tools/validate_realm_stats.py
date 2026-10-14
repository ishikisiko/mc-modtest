#!/usr/bin/env python3
"""Validate bundled realm growth, stage aliases and regional encounter bounds (stdlib only)."""
from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DATA = Path('src/main/resources/data/myvillage')
NATIVE_MAX_HEALTH = 1024
NATIVE_MAX_ATTACK = 2048
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
LIMIT_FIELDS = {'max_base_health', 'max_base_attack', 'max_health', 'max_attack', 'min_resistance',
                'max_resistance', 'max_bonus', 'max_flat_damage', 'max_move_multiplier',
                'max_total_multiplier', 'max_penetration', 'max_damage'}
ROGUE_BOUNDS = {'interval_ticks': (20, 1200), 'spawn_radius': (16, 128), 'withdraw_radius': (16, 192),
                'min_distance': (1, 127), 'max_per_region': (1, 32), 'max_total': (1, 128),
                'attempts_per_pass': (1, 128), 'probes_per_person': (1, 32)}


def number(value: Any) -> bool:
    if type(value) not in (int, float):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:
        return False


def integer(value: Any) -> bool:
    return type(value) is int


def fields(value: Any, expected: set[str], path: str, errors: list[str]) -> bool:
    if not isinstance(value, dict):
        errors.append(f'REALM_SCHEMA:{path}:expected object')
        return False
    for key in sorted(expected - value.keys()):
        errors.append(f'REALM_SCHEMA:{path}.{key}:missing')
    for key in sorted(value.keys() - expected):
        errors.append(f'REALM_SCHEMA:{path}.{key}:unknown')
    return True


def check_rules(data: Any, player_stages: set[str], sim_realms: Any,
                beast_attributes: list[dict[str, Any]],
                player_stage_keys: dict[str, tuple[str, int]] | None = None) -> list[str]:
    errors: list[str] = []
    if not fields(data, {'schema', 'growth', 'realms', 'player_stage_aliases', 'damage_types', 'limits'}, 'rules', errors):
        return errors
    if type(data.get('schema')) is not int or data['schema'] != 1:
        errors.append('REALM_SCHEMA:rules.schema:expected 1')
    growth = data.get('growth')
    valid_growth = fields(growth, {'entry_multiplier', 'major_ratio', 'minor_slope'}, 'growth', errors)
    if valid_growth:
        for key in ('entry_multiplier', 'major_ratio', 'minor_slope'):
            value = growth.get(key)
            if not number(value) or value <= (0 if key == 'minor_slope' else 1):
                errors.append(f'REALM_GROWTH:growth.{key}:invalid')
                valid_growth = False
    limits = data.get('limits')
    valid_limits = fields(limits, LIMIT_FIELDS, 'limits', errors)
    if valid_limits:
        for key in sorted(LIMIT_FIELDS):
            value = limits.get(key)
            if not number(value) or (value < 0 if key in {'min_resistance', 'max_resistance'} else value <= 0):
                errors.append(f'REALM_LIMIT:limits.{key}:invalid')
                valid_limits = False
        if valid_limits:
            if limits['max_health'] > NATIVE_MAX_HEALTH or limits['max_attack'] > NATIVE_MAX_ATTACK:
                errors.append('REALM_NATIVE_LIMIT:configured ceilings exceed Minecraft native limits')
            if limits['min_resistance'] != 0 or not 0 <= limits['max_resistance'] < 1:
                errors.append('REALM_RESISTANCE:limits.resistance:expected min = 0 and 0 <= max < 1')
            if not 0 < limits['max_penetration'] <= 1:
                errors.append('REALM_RESISTANCE:limits.max_penetration:expected 0 < value <= 1')
            if min(limits['max_move_multiplier'], limits['max_total_multiplier']) < 1:
                errors.append('REALM_LIMIT:neutral move/total multipliers must be supported')
            for attr, key, baseline in (('max_health', 'max_base_health', 20), ('attack_damage', 'max_base_attack', 1)):
                bases = [baseline, *[b.get(attr) for b in beast_attributes]]
                if any(not number(v) or v < 0 for v in bases):
                    errors.append(f'REALM_BASE:beast.{attr}:invalid')
                elif limits[key] < max(bases):
                    errors.append(f'REALM_BASE:limits.{key}:below shipped base {max(bases)}')
    realms = data.get('realms')
    if not isinstance(realms, list) or not realms:
        errors.append('REALM_SCHEMA:realms:expected nonempty list')
        realms = []
    by_id: dict[str, dict] = {}
    by_alias: dict[str, dict] = {}
    valid_realms = True
    mortal_count = 0
    cultivated_orders: list[int] = []
    for i, realm in enumerate(realms):
        path = f'realms[{i}]'
        if not fields(realm, {'id', 'order', 'stages', 'mortal', 'aliases'}, path, errors):
            valid_realms = False
            continue
        rid = realm.get('id')
        if not isinstance(rid, str) or not ID.fullmatch(rid) or rid in by_id:
            errors.append(f'REALM_ID:{path}.id:invalid or duplicate')
            valid_realms = False
        else:
            by_id[rid] = realm
        mortal, order, stages = realm.get('mortal'), realm.get('order'), realm.get('stages')
        if type(mortal) is not bool or not integer(order) or not integer(stages) or not 1 <= stages <= 256:
            errors.append(f'REALM_STAGE:{path}:invalid mortal/order/stages')
            valid_realms = False
        elif mortal:
            mortal_count += 1
            if order != -1:
                errors.append(f'REALM_ORDER:{path}:mortal order must be -1')
                valid_realms = False
        elif order < 0:
            errors.append(f'REALM_ORDER:{path}:cultivated order must be nonnegative')
            valid_realms = False
        else:
            cultivated_orders.append(order)
        aliases = realm.get('aliases')
        if not isinstance(aliases, list) or not aliases:
            errors.append(f'REALM_ALIAS:{path}:expected nonempty aliases')
        else:
            for alias in aliases:
                if not isinstance(alias, str) or not alias or alias in by_alias:
                    errors.append(f'REALM_ALIAS:{path}:invalid or duplicate alias')
                else:
                    by_alias[alias] = realm
    if mortal_count != 1 or sorted(cultivated_orders) != list(range(len(cultivated_orders))):
        errors.append('REALM_ORDER:one mortal and contiguous unique cultivated orders required')
        valid_realms = False
    if valid_realms and (not cultivated_orders or [r['order'] for r in realms] != list(range(-1, len(realms) - 1))):
        errors.append('REALM_ORDER:realms must be listed mortal first, then ascending cultivated order')
        valid_realms = False
    if valid_realms and valid_growth and valid_limits:
        previous = 1.0
        for realm in sorted(realms, key=lambda r: r['order']):
            try:
                first = 1.0 if realm['mortal'] else growth['entry_multiplier'] * growth['major_ratio'] ** realm['order']
                last = first if realm['mortal'] else first * (1 + growth['minor_slope'] * (realm['stages'] - 1))
            except OverflowError:
                first = last = math.inf
            if not realm['mortal'] and first <= previous:
                errors.append(f"REALM_BOUNDARY:{realm['id']}:first stage must exceed previous realm's last")
            previous = last
            for base, cap, native in (('max_base_health', 'max_health', NATIVE_MAX_HEALTH),
                                      ('max_base_attack', 'max_attack', NATIVE_MAX_ATTACK)):
                scaled = last * limits[base]
                if not math.isfinite(scaled) or scaled > min(limits[cap], native):
                    errors.append(f"REALM_NATIVE_OVERFLOW:{realm['id']}:{cap}:{scaled}")
    aliases = data.get('player_stage_aliases')
    seen: set[str] = set()
    if not isinstance(aliases, list):
        errors.append('REALM_PLAYER_ALIAS:expected list')
        aliases = []
    for i, alias in enumerate(aliases):
        if not fields(alias, {'id', 'realm', 'stage'}, f'player_stage_aliases[{i}]', errors):
            continue
        sid = alias.get('id')
        if not isinstance(sid, str) or not ID.fullmatch(sid) or sid in seen:
            errors.append(f'REALM_PLAYER_ALIAS:{sid}:invalid or duplicate')
        else:
            seen.add(sid)
        check_endpoint(alias, by_id, f'player_stage_aliases[{i}]', errors)
        if player_stage_keys and isinstance(sid, str) and sid in player_stage_keys:
            if (alias.get('realm'), alias.get('stage')) != player_stage_keys[sid]:
                errors.append(f'REALM_PLAYER_ALIAS_MAPPING:{sid}:differs from cultivation realm/stage order')
    if seen != player_stages:
        errors.append(f'REALM_PLAYER_ALIAS_COVERAGE:missing={sorted(player_stages-seen)}:unknown={sorted(seen-player_stages)}')
    if isinstance(sim_realms, dict) and isinstance(sim_realms.get('realms'), list):
        for realm in sim_realms['realms']:
            if not isinstance(realm, dict):
                errors.append('REALM_SIM_MAPPING:invalid ledger realm')
                continue
            sim_id = realm.get('id')
            target = by_alias.get(sim_id) if isinstance(sim_id, str) else None
            stages = realm.get('stages')
            if target is None or not isinstance(stages, list) or not integer(target.get('stages')) or target['stages'] != len(stages):
                errors.append(f"REALM_SIM_MAPPING:{realm.get('id')}:missing alias or different stage count")
    else:
        errors.append('REALM_SIM_MAPPING:missing ledger realms')
    damage_types = data.get('damage_types')
    if not isinstance(damage_types, list) or not damage_types or any(not isinstance(t, str) or not re.fullmatch(r'[a-z_]+', t) for t in damage_types):
        errors.append('REALM_DAMAGE_TYPES:expected named types')
    elif len(set(damage_types)) != len(damage_types) or set(damage_types) != {'physical', 'metal', 'wood', 'water', 'fire', 'earth'}:
        errors.append('REALM_DAMAGE_TYPES:every supported channel required exactly once')
    return errors


def check_endpoint(endpoint: Any, realms: dict[str, dict], path: str, errors: list[str]) -> tuple[int, int] | None:
    if not isinstance(endpoint, dict):
        errors.append(f'REALM_ENDPOINT:{path}:expected object')
        return None
    rid, stage = endpoint.get('realm'), endpoint.get('stage')
    realm = realms.get(rid) if isinstance(rid, str) else None
    if realm is None or not integer(stage) or not integer(realm.get('stages')) or not 0 <= stage < realm['stages'] or not integer(realm.get('order')):
        errors.append(f'REALM_ENDPOINT:{path}:unknown realm or invalid stage')
        return None
    return realm['order'], stage


def read_json(root: Path, relative: Path, errors: list[str]) -> Any:
    try:
        result = json.loads((root / relative).read_text(encoding='utf-8'), object_pairs_hook=unique_object)
        if not isinstance(result, dict):
            raise ValueError('expected a JSON object')
        return result
    except (OSError, ValueError) as exc:
        errors.append(f'REALM_FILE:{relative}:{exc}')
        return {}


def unique_object(pairs: list[tuple[str, Any]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate JSON field {key}')
        result[key] = value
    return result


def check_wilderness(data: Any, rules: Any, region_ids: set[str]) -> list[str]:
    errors: list[str] = []
    if not fields(data, {'schema', 'rogues', 'regions'}, 'wilderness', errors):
        return errors
    if type(data.get('schema')) is not int or data['schema'] != 1:
        errors.append('REALM_SCHEMA:wilderness.schema:expected 1')
    settings = data.get('rogues')
    names = {'interval_ticks', 'spawn_radius', 'withdraw_radius', 'min_distance',
             'max_per_region', 'max_total', 'attempts_per_pass', 'probes_per_person'}
    valid_settings = fields(settings, names, 'rogues', errors)
    if valid_settings:
        for key in sorted(names):
            value = settings.get(key)
            low, high = ROGUE_BOUNDS[key]
            if not integer(value) or not low <= value <= high:
                errors.append(f'WILDERNESS_SETTING:rogues.{key}:expected positive bounded number')
                valid_settings = False
        if valid_settings:
            if settings['interval_ticks'] % 20 != 0:
                errors.append('WILDERNESS_SETTING:rogues.interval_ticks:must be a multiple of 20')
            if not settings['min_distance'] < settings['spawn_radius'] < settings['withdraw_radius']:
                errors.append('WILDERNESS_DISTANCE:min_distance < spawn_radius < withdraw_radius required')
            if settings['max_per_region'] > settings['max_total']:
                errors.append('WILDERNESS_CAP:regional cap exceeds global cap')
    regions = data.get('regions')
    if not isinstance(regions, dict):
        errors.append('WILDERNESS_REGION:expected region map')
        return errors
    if set(regions) != region_ids:
        errors.append(f'WILDERNESS_REGION_COVERAGE:missing={sorted(region_ids-set(regions))}:unknown={sorted(set(regions)-region_ids)}')
    realm_list = rules.get('realms', []) if isinstance(rules, dict) else []
    realms = {r['id']: r for r in realm_list if isinstance(r, dict) and isinstance(r.get('id'), str)} if isinstance(realm_list, list) else {}
    for rid, region in regions.items():
        if not fields(region, {'beast', 'rogue'}, f'regions.{rid}', errors):
            continue
        for kind in ('beast', 'rogue'):
            span = region.get(kind)
            path = f'regions.{rid}.{kind}'
            if not fields(span, {'min', 'max'}, path, errors):
                continue
            endpoints = []
            for key in ('min', 'max'):
                endpoint = span.get(key)
                fields(endpoint, {'realm', 'stage'}, f'{path}.{key}', errors)
                endpoints.append(check_endpoint(endpoint, realms, f'{path}.{key}', errors))
            if all(e is not None for e in endpoints) and endpoints[0] > endpoints[1]:
                errors.append(f'WILDERNESS_RANGE_ORDER:{path}:min exceeds max')
    return errors


def validate(root: Path = ROOT) -> dict[str, Any]:
    errors: list[str] = []
    rules = read_json(root, DATA / 'stats/rules.json', errors)
    player_stages = set()
    player_stage_keys = {}
    for path in sorted((root / DATA / 'myvillage/realm').glob('*.json')):
        realm = read_json(root, path.relative_to(root), errors)
        stages = realm.get('stages')
        if not isinstance(stages, list) or any(not isinstance(s, dict) or not integer(s.get('sort_order')) for s in stages):
            errors.append(f'REALM_FILE:{path.relative_to(root)}:invalid cultivation stages')
            continue
        player_stages.update(s['id'] for s in stages if isinstance(s.get('id'), str))
        stages = sorted(stages, key=lambda s: s['sort_order'])
        for index, stage in enumerate(stages):
            if isinstance(stage, dict) and isinstance(stage.get('id'), str):
                player_stage_keys[stage['id']] = (f'{path.parent.parent.name}:{path.stem}', index)
    if not player_stages:
        errors.append('REALM_PLAYER_ALIAS_COVERAGE:no cultivation stages found')
    sim_realms = read_json(root, DATA / 'world_sim/realms.json', errors)
    beasts = []
    index = read_json(root, DATA / 'beast/index.json', errors)
    for entity in index.get('beasts', []):
        namespace, name = entity.split(':', 1)
        beast = read_json(root, Path(f'src/main/resources/data/{namespace}/beast/{name}.json'), errors)
        beasts.append(beast.get('attributes', {}))
    errors.extend(check_rules(rules, player_stages, sim_realms, beasts, player_stage_keys))
    wilderness = read_json(root, DATA / 'wilderness/rules.json', errors)
    region_ids = set()
    for path in sorted((root / DATA / 'worldgen/region_profile').glob('*.json')):
        region = read_json(root, path.relative_to(root), errors)
        if isinstance(region, dict) and isinstance(region.get('id'), str):
            region_ids.add(region['id'])
    if not region_ids:
        errors.append('WILDERNESS_REGION_COVERAGE:no region profiles found')
    errors.extend(check_wilderness(wilderness, rules, region_ids))
    return {'schema_version': 1, 'status': 'fail' if errors else 'pass', 'errors': errors}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--report', type=Path, default=Path('reports/realm_stats_validation.json'))
    args = parser.parse_args()
    root = args.root.resolve()
    report = validate(root)
    path = args.report if args.report.is_absolute() else root / args.report
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return int(report['status'] != 'pass')


if __name__ == '__main__':
    raise SystemExit(main())
