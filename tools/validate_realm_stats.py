#!/usr/bin/env python3
"""Validate bundled realm growth, stage aliases and regional encounter bounds (stdlib only)."""
from __future__ import annotations

import argparse
import json
import math
import re
import tomllib
import zipfile
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


JAVA_ROOT = Path('src/main/java/com/example/myvillage')
MIXIN_CONFIG = Path('src/main/resources/myvillage.mixins.json')
MOD_METADATA = Path('src/main/resources/META-INF/neoforge.mods.toml')
ARMOR_DESCRIPTOR = 'getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F'
CLAMP_DESCRIPTOR = 'Lnet/minecraft/util/Mth;clamp(FFF)F'
JAVA_TOKENS = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')


def java_text(text: str, *, mask_strings: bool = False) -> str:
    """Preserve offsets while ignoring comments and, for code scans, string/char literals."""
    def replace(match):
        token = match.group()
        if token.startswith(('//', '/*')) or mask_strings:
            return ''.join('\n' if c == '\n' else ' ' for c in token)
        return token
    return JAVA_TOKENS.sub(replace, text)


def closing(text: str, start: int) -> int:
    pairs = {'(': ')', '[': ']', '{': '}'}
    if start >= len(text) or text[start] not in pairs:
        return start
    stack = []
    for pos in range(start, len(text)):
        char = text[pos]
        if char in pairs:
            stack.append(pairs[char])
        elif stack and char == stack[-1]:
            stack.pop()
            if not stack:
                return pos
    return len(text)


def java_args(text: str) -> list[str]:
    code = java_text(text, mask_strings=True)
    result = []
    start = pos = 0
    while pos < len(code):
        if code[pos] in '([{':
            pos = closing(code, pos)
        elif code[pos] == ',':
            result.append(text[start:pos].strip())
            start = pos + 1
        pos += 1
    if text[start:].strip():
        result.append(text[start:].strip())
    return result


def java_annotations(text: str, name: str) -> list[tuple[int, int, dict[str, str]]]:
    raw = java_text(text)
    code = java_text(text, mask_strings=True)
    found = []
    for match in re.finditer(r'@(?:[\w.]+\.)?' + re.escape(name) + r'\s*\(', code):
        start = match.end() - 1
        end = closing(code, start)
        values = {}
        for arg in java_args(raw[start + 1:end]):
            key, sep, value = arg.partition('=')
            values[key.strip() if sep else 'value'] = value.strip() if sep else arg
        found.append((match.start(), end + 1, values))
    return found


def java_methods(text: str) -> list[tuple[str, list[tuple[str, str]], str, int]]:
    raw = java_text(text)
    code = java_text(text, mask_strings=True)
    # The contract uses ordinary Java methods. Local identifiers and whitespace are deliberately free.
    pattern = re.compile(r'\b(?:(?:public|protected|private)\s+)?(?:(?:static|final|synchronized)\s+)*'
                         r'[\w.<>?,\[\]]+\s+([\w$]+)\s*\(([^)]*)\)\s*\{')
    methods = []
    for match in pattern.finditer(code):
        parameters = []
        for parameter in java_args(raw[match.start(2):match.end(2)]):
            parts = parameter.replace('final ', '').split()
            if len(parts) >= 2:
                parameters.append((' '.join(parts[:-1]), parts[-1]))
        start = match.end() - 1
        end = closing(code, start)
        methods.append((match.group(1), parameters, raw[start + 1:end], match.start()))
    return methods


def calls(text: str, receiver: str, method: str) -> list[list[str]]:
    raw = java_text(text)
    code = java_text(text, mask_strings=True)
    found = []
    for match in re.finditer(r'\b' + re.escape(receiver) + r'\s*\.\s*' + re.escape(method) + r'\s*\(', code):
        start = match.end() - 1
        end = closing(code, start)
        found.append(java_args(raw[start + 1:end]))
    return found


def compact(text: str) -> str:
    return re.sub(r'\s+', '', text)


def annotation_string(value: str | None) -> str | None:
    if value is None:
        return None
    found = re.findall(r'"((?:\\.|[^"\\])*)"', value)
    return found[0] if len(found) == 1 else None


def resolve_java_alias(expression: str, body: str) -> str:
    """Resolve simple local snapshots without depending on their chosen names."""
    aliases = {name: expr for name, expr in re.findall(
        r"\b(?:float|double|var|LivingEntity|DamageSource|CompoundTag|RealmKey)\s+([\w$]+)\s*=\s*([^;]+);", java_text(body))}
    value = compact(expression)
    visited = set()
    while value in aliases and value not in visited:
        visited.add(value)
        value = compact(aliases[value])
    return value


def check_armor_packaging(root: Path, jar: Path | None = None) -> list[str]:
    errors: list[str] = []
    config = read_json(root, MIXIN_CONFIG, errors)
    version = config.get('minVersion')
    if not isinstance(version, str) or not re.fullmatch(r'\d+(?:\.\d+)+', version) or tuple(map(int, version.split('.'))) < (0, 8):
        errors.append('ARMOR_MIXIN_CONFIG:Mixin minVersion must support the required 0.8 contract')
    if config.get('required') is not True or config.get('compatibilityLevel') != 'JAVA_21':
        errors.append('ARMOR_MIXIN_CONFIG:required common Java-21 hook configuration missing')
    injectors = config.get('injectors')
    if not isinstance(injectors, dict) or type(injectors.get('defaultRequire')) is not int or injectors['defaultRequire'] != 1:
        errors.append('ARMOR_MIXIN_CONFIG:defaultRequire must equal one')
    classes = config.get('mixins')
    required = {'CombatRulesMixin', 'LivingDamageContainerAccess'}
    if not isinstance(classes, list) or any(not isinstance(c, str) for c in classes) or not required <= set(classes) or len(classes) != len(set(classes)):
        errors.append('ARMOR_MIXIN_CONFIG:both unique common armor hook and container accessor are required')
    if any(required & set(config.get(side, [])) for side in ('client', 'server') if isinstance(config.get(side), list)):
        errors.append('ARMOR_MIXIN_SIDE:armor hook and accessor must be common mixins')
    package = config.get('package')
    if not isinstance(package, str) or not re.fullmatch(r'[a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)+', package):
        errors.append('ARMOR_MIXIN_CONFIG:invalid package')
        package = 'com.example.myvillage.mixin'
    try:
        metadata = tomllib.loads((root / MOD_METADATA).read_text(encoding='utf-8'))
    except (OSError, ValueError) as exc:
        errors.append(f'ARMOR_MIXIN_METADATA:{exc}')
        metadata = {}
    entries = metadata.get('mixins', [])
    if not isinstance(entries, list) or sum(isinstance(entry, dict) and entry.get('config') == MIXIN_CONFIG.name for entry in entries) != 1:
        errors.append('ARMOR_MIXIN_METADATA:exactly one registered common configuration required')
    for name in sorted(required):
        path = Path('src/main/java') / package.replace('.', '/') / (name + '.java')
        if not (root / path).is_file():
            errors.append(f'ARMOR_MIXIN_SOURCE:{path}:missing')
    if jar is not None:
        try:
            with zipfile.ZipFile(jar) as archive:
                names = archive.namelist()
                resources = {MIXIN_CONFIG.name: MIXIN_CONFIG, 'META-INF/neoforge.mods.toml': MOD_METADATA}
                for entry, source in resources.items():
                    if names.count(entry) != 1:
                        errors.append(f'ARMOR_JAR_ENTRY:{entry}:must occur exactly once')
                    elif archive.read(entry) != (root / source).read_bytes():
                        errors.append(f'ARMOR_JAR_DRIFT:{entry}:differs from authored source')
                class_entries = [package.replace('.', '/') + '/' + name + '.class' for name in sorted(required)]
                class_entries.extend('com/example/myvillage/' + name + '.class' for name in (
                    'stats/ArmorPressure', 'stats/runtime/RealmArmorRuntime',
                    'stats/runtime/RealmArmorRuntime$Scale', 'stats/runtime/RealmArmorRuntime$ArmorTrace'))
                for entry in class_entries:
                    if names.count(entry) != 1:
                        errors.append(f'ARMOR_JAR_ENTRY:{entry}:must occur exactly once')
                    elif not archive.read(entry).startswith(b'\xca\xfe\xba\xbe'):
                        errors.append(f'ARMOR_JAR_CLASS:{entry}:invalid class resource')
        except (OSError, ValueError, zipfile.BadZipFile) as exc:
            errors.append(f'ARMOR_JAR:{jar}:{exc}')
    return errors


def check_armor_hook(text: str) -> list[str]:
    """Guard only the first-clamp arithmetic argument, independent of local names or formatting."""
    errors = []
    targets = java_annotations(text, 'Mixin')
    if len(targets) != 1 or not re.fullmatch(r'(?:value\s*=\s*)?(?:[\w.]+\.)?CombatRules\.class', compact(targets[0][2].get('value', ''))):
        errors.append('ARMOR_HOOK_TARGET:the common hook must target CombatRules')
    hooks = java_annotations(text, 'ModifyArgs')
    other = sum(len(java_annotations(text, kind)) for kind in ('Inject', 'Redirect', 'Overwrite', 'ModifyArg', 'ModifyVariable', 'ModifyExpressionValue', 'ModifyReturnValue'))
    if len(hooks) != 1 or other:
        errors.append('ARMOR_HOOK_SCOPE:exactly one ModifyArgs and no broader damage interception allowed')
        return errors
    _start, end, values = hooks[0]
    if annotation_string(values.get('method')) != ARMOR_DESCRIPTOR:
        errors.append('ARMOR_HOOK_DESCRIPTOR:exact CombatRules damage-absorb descriptor required')
    if not targets or compact(targets[0][2].get('remap', '')) != 'false':
        errors.append('ARMOR_HOOK_REMAP:CombatRules target must retain Mojmap names')
    at = java_annotations(values.get('at', ''), 'At')
    if len(at) != 1 or annotation_string(at[0][2].get('value')) != 'INVOKE' or annotation_string(at[0][2].get('target')) != CLAMP_DESCRIPTOR or compact(at[0][2].get('ordinal', '')) != '0':
        errors.append('ARMOR_HOOK_CLAMP:first Mth.clamp(float,float,float) invocation required')
    if at and compact(at[0][2].get('remap', '')) != 'false':
        errors.append('ARMOR_HOOK_REMAP:first-clamp target must retain Mojmap names')
    for key in ('require', 'allow'):
        if compact(values.get(key, '')) != '1':
            errors.append(f'ARMOR_HOOK_COUNT:{key} must equal one')
    if compact(values.get('remap', '')) != 'false':
        errors.append('ARMOR_HOOK_REMAP:pinned Mojmap descriptor must not be remapped')
    methods = [method for method in java_methods(text) if method[3] >= end]
    if not methods:
        errors.append('ARMOR_HOOK_ARGUMENT:no injection handler found')
        return errors
    _name, params, body, _offset = methods[0]
    types = [kind.split('.')[-1] for kind, _local in params]
    if types != ['Args', 'LivingEntity', 'float', 'DamageSource', 'float', 'float']:
        errors.append('ARMOR_HOOK_ARGUMENT:handler must capture Args and the unmodified outer-method inputs')
        return errors
    arg_name, defender, entering_damage, source, armor, _toughness = [local for _kind, local in params]
    sets = calls(body, arg_name, 'set')
    if len(sets) != 1 or len(sets[0]) != 2 or compact(sets[0][0]) != '0':
        errors.append('ARMOR_HOOK_ARGUMENT:only argument zero of the first clamp may change')
    else:
        transform = calls(sets[0][1], 'RealmArmorRuntime', 'normalizeUnclamped')
        expected = [defender, source, entering_damage, armor,
                    f'{arg_name}.get(0)', f'{arg_name}.get(1)', f'{arg_name}.get(2)']
        if len(transform) != 1 or [resolve_java_alias(arg, body) for arg in transform[0]] != expected:
            errors.append('ARMOR_HOOK_FLOW:pass defender, actual entering damage, armor and original clamp bounds unchanged')
    code = java_text(body, mask_strings=True)
    for local in (defender, entering_damage, source, armor):
        if re.search(r'\b' + re.escape(local) + r'\s*(?:[+*/-]?=(?!=)|\+\+|--)', code):
            errors.append('ARMOR_HOOK_FLOW:outer method inputs must not be reassigned')
    if re.search(r'\b' + re.escape(arg_name) + r'\s*\.\s*(?:setAll|setValue)\s*\(', code):
        errors.append('ARMOR_HOOK_ARGUMENT:bulk argument mutation is forbidden')
    if re.search(r'\b(?:CombatRules\s*\.\s*getDamageAfterAbsorb|EnchantmentHelper\s*\.)', code):
        errors.append('ARMOR_LAYER_DUPLICATION:do not recalculate armor or weapon enchantments in the hook')
    return errors



def check_armor_runtime(runtime: str, stats: str, accessor: str, pressure: str) -> list[str]:
    """Static authority/order guards; numerical Java tests and game startup remain separate gates."""
    errors = []
    methods = {name: (params, body) for name, params, body, _offset in java_methods(runtime)}
    normalize = methods.get('normalizeUnclamped')
    defender_scale = methods.get('defenderScale')
    resolve = methods.get('resolveScale')
    if normalize is None or defender_scale is None or resolve is None:
        errors.append('ARMOR_DEFENDER_API:missing normalizer or validated defender scale resolver')
        return errors
    normal_params, normal_body = normalize
    if [kind.split('.')[-1] for kind, _name in normal_params] != ['LivingEntity', 'DamageSource', 'float', 'float', 'float', 'float', 'float']:
        errors.append('ARMOR_DEFENDER_API:normalizer must receive defender and the actual vanilla armor-step values')
        return errors
    defender, _source, _damage, _armor, original, _lower, _upper = [name for _kind, name in normal_params]
    if not re.search(r'if\s*\(\s*' + re.escape(defender) + r'\s*\.\s*level\s*\(\s*\)\s*\.\s*isClientSide\s*\(\s*\)\s*\)\s*\{?\s*return\s+' + re.escape(original) + r'\s*;', java_text(normal_body)):
        errors.append('ARMOR_SERVER_AUTHORITY:client calls must return the untouched original pressure')
    if not re.search(r'\bdefenderScale\s*\(\s*' + re.escape(defender) + r'\s*\)', java_text(normal_body, mask_strings=True)):
        errors.append('ARMOR_DEFENDER_FLOW:scale must resolve from the actual defender')
    scale_local = re.search(r'\bScale\s+([\w$]+)\s*=\s*defenderScale\s*\(\s*' + re.escape(defender) + r'\s*\)', java_text(normal_body, mask_strings=True))
    transforms = calls(normal_body, 'ArmorPressure', 'normalizeUnclamped')
    expected_pressure = [_armor, original, f'{scale_local.group(1)}.factor()'] if scale_local else []
    if len(transforms) != 1 or [compact(arg) for arg in transforms[0]] != expected_pressure:
        errors.append('ARMOR_DEFENDER_FLOW:pressure helper must receive this defender scale, not a fixed or attacker scale')
    pressure_local = re.search(r'\bfloat\s+([\w$]+)\s*=\s*ArmorPressure\s*\.\s*normalizeUnclamped\s*\(', java_text(normal_body, mask_strings=True))
    returns = re.findall(r'\breturn\s+([^;]+);', java_text(normal_body, mask_strings=True))
    if pressure_local is None or not returns or compact(returns[-1]) != pressure_local.group(1):
        errors.append('ARMOR_PRESSURE_OUTPUT:return only the unclamped normalized armor value; native bounds and full damage stay outside')
    scale_params, scale_body = defender_scale
    if len(scale_params) != 1:
        errors.append('ARMOR_DEFENDER_FLOW:defender scale must receive exactly one defender')
    else:
        scale_defender = scale_params[0][1]
        if not re.search(r'\bresolveScale\s*\(\s*' + re.escape(scale_defender) + r'\s*\.\s*getPersistentData\s*\(\s*\)\s*,\s*StatsRules\s*\.\s*bundled\s*\(\s*\)\s*\)', java_text(scale_body, mask_strings=True)):
            errors.append('ARMOR_DEFENDER_FLOW:scale must use defender persistent data and bundled growth rules')
    params, body = resolve
    if [kind.split('.')[-1] for kind, _name in params] != ['CompoundTag', 'StatsRules']:
        errors.append('ARMOR_ASSIGNMENT:resolver must validate persisted assignment against existing rules')
        return errors
    persistent, rules = [name for _kind, name in params]
    assignment = re.search(r'\bCompoundTag\s+([\w$]+)\s*=\s*' + re.escape(persistent) + r'\s*\.\s*getCompound\s*\(\s*([\w$]+)\s*\)', java_text(body))
    if assignment is None:
        errors.append('ARMOR_ASSIGNMENT:read the defender compound assignment, never infer it')
    else:
        assigned, key = assignment.groups()
        contains = [[compact(arg) for arg in args] for args in calls(body, persistent, 'contains')]
        assigned_contains = [[compact(arg) for arg in args] for args in calls(body, assigned, 'contains')]
        if [key, 'Tag.TAG_COMPOUND'] not in contains or ['"realm"', 'Tag.TAG_STRING'] not in assigned_contains or ['"stage"', 'Tag.TAG_INT'] not in assigned_contains:
            errors.append('ARMOR_ASSIGNMENT_TYPES:require exact compound/string/int NBT types before coercing reads')
        expected_growth = f'{rules}.realmKey({assigned}.getString("realm"),{assigned}.getInt("stage"))'
        growth = calls(body, rules, 'growth')
        if len(growth) != 1 or len(growth[0]) != 1 or resolve_java_alias(growth[0][0], body) != expected_growth:
            errors.append('ARMOR_ASSIGNMENT_GROWTH:use existing growth of the validated defender realm/stage exactly once')
        reader_key = re.search(r'\bString\s+' + re.escape(key) + r'\s*=\s*"([^"]+)"', java_text(runtime))
        writer = re.search(r'getPersistentData\s*\(\s*\)\s*\.\s*put\s*\(\s*([\w$]+)\s*,', java_text(stats))
        writer_key = re.search(r'\bString\s+' + re.escape(writer.group(1)) + r'\s*=\s*"([^"]+)"', java_text(stats)) if writer else None
        if reader_key is None or writer_key is None or reader_key.group(1) != writer_key.group(1):
            errors.append('ARMOR_ASSIGNMENT_KEY:read the same saved assignment written by StatsRuntime')
        for receiver in (persistent, assigned):
            if re.search(r'\b' + re.escape(receiver) + r'\s*\.\s*(?:put\w*|remove|merge)\s*\(', java_text(body, mask_strings=True)):
                errors.append('ARMOR_ASSIGNMENT_MUTATION:damage resolution must not rewrite the saved assignment')
    code = java_text(body, mask_strings=True)
    factor_match = re.search(r'\bdouble\s+([\w$]+)\s*=\s*' + re.escape(rules) + r'\s*\.\s*growth\s*\(', code)
    factor = factor_match.group(1) if factor_match else ''
    if not factor or not re.search(r'Double\s*\.\s*isFinite\s*\(\s*' + re.escape(factor) + r'\s*\)', code) or not re.search(r'\b' + re.escape(factor) + r'\s*>=\s*1\b', code):
        errors.append('ARMOR_SCALE_FINITE:usable defender growth must be finite and at least one')
    constructors = re.findall(r'new\s+Scale\s*\(\s*([^,]+),', java_text(body))
    baseline_constructors = [value for value in constructors if compact(value) in {'1', '1.0', '1D', '1.0D', '1d', '1.0d'}]
    if len(baseline_constructors) < 3 or any(compact(value) != factor and value not in baseline_constructors for value in constructors) or not re.search(r'catch\s*\(\s*(?:[\w.]+\.)?IllegalArgumentException\b', code):
        errors.append('ARMOR_ASSIGNMENT_FALLBACK:missing, malformed or invalid realm/stage must fall back to exactly one')
    policy = java_text(runtime, mask_strings=True)
    if re.search(r'\b(?:getMaxHealth|getHealth|getBaseValue|getEntity|getDirectEntity|mappedType)\s*\(', policy):
        errors.append('ARMOR_DEFENDER_INFERENCE:never derive armor scale from attacker, health, equipment or source channel')
    if re.search(r'\b(?:setNewDamage|setDamage|setAmount|setOriginalDamage)\s*\(|\bLivingIncomingDamageEvent\b', policy):
        errors.append('ARMOR_WHOLE_DAMAGE:normalization must not replace whole incoming damage')
    if re.search(r'\b(?:CombatRules\s*\.\s*getDamageAfterAbsorb|EnchantmentHelper\s*\.)', java_text(runtime + '\n' + stats + '\n' + pressure, mask_strings=True)):
        errors.append('ARMOR_LAYER_DUPLICATION:vanilla armor and enchantments must not be recalculated')
    for text in (runtime, stats, accessor, pressure):
        code = java_text(text, mask_strings=True)
        if re.search(r'\bset(?:Max|Min)Value\s*\(', code):
            errors.append('ARMOR_NATIVE_LIMIT:do not alter native attribute ceilings')
        for _name, _params, method_body, _at in java_methods(text):
            method_code = java_text(method_body, mask_strings=True)
            if re.search(r'\bAttributes\s*\.\s*ARMOR(?:_TOUGHNESS)?\b', method_code) and re.search(r'\b(?:setBaseValue|addPermanentModifier|addTransientModifier|addOrReplacePermanentModifier|replace)\s*\(', method_code):
                errors.append('ARMOR_INNATE_GRANT:realm scaling must not grant armor or toughness')
    accessor_targets = java_annotations(accessor, 'Mixin')
    accessor_fields = java_annotations(accessor, 'Accessor')
    if len(accessor_targets) != 1 or compact(accessor_targets[0][2].get('value', '')) != 'LivingEntity.class' or len(accessor_fields) != 1 or annotation_string(accessor_fields[0][2].get('value')) != 'damageContainers' or not re.search(r'\bStack\s*<\s*DamageContainer\s*>\s+[\w$]+\s*\(\s*\)\s*;', java_text(accessor)):
        errors.append('ARMOR_CONTAINER_ACCESS:access the actual LivingEntity DamageContainer stack')
    if not re.search(r'\bMap\s*<\s*DamageContainer\s*,\s*ArmorTrace\s*>\s+[\w$]+\s*=\s*new\s+WeakHashMap\s*<', java_text(runtime)):
        errors.append('ARMOR_TRACE_IDENTITY:weak damage-container keys are required for armor-stage facts')
    if not re.search(r'\bMap\s*<\s*DamageContainer\s*,\s*DamageTrace\s*>\s+[\w$]+\s*=\s*new\s+WeakHashMap\s*<', java_text(stats)):
        errors.append('ARMOR_TRACE_IDENTITY:pending final traces must use weak damage-container keys')
    stats_methods = {name: (params, body) for name, params, body, _at in java_methods(stats)}
    before = stats_methods.get('beforeDamage')
    if before is None or len(before[0]) != 1:
        errors.append('ARMOR_TRACE_BRIDGE:missing pre-damage invocation bridge')
    else:
        event = before[0][0][1]
        pre = before[1]
        takes = calls(pre, 'RealmArmorRuntime', 'take')
        if takes != [[f'{event}.getContainer()']]:
            errors.append('ARMOR_TRACE_BRIDGE:consume only this Pre event damage container')
        armor_local = re.search(r'\bArmorTrace\s+([\w$]+)\s*=\s*RealmArmorRuntime\s*\.\s*take', java_text(pre, mask_strings=True))
        completed = calls(pre, armor_local.group(1), 'completed') if armor_local else []
        expected_output = (f'{armor_local.group(1)}.damageAtArmor()-{event}.getContainer().getReduction(DamageContainer.Reduction.ARMOR)') if armor_local else ''
        if len(completed) != 1 or len(completed[0]) != 1 or compact(completed[0][0]) != expected_output:
            errors.append('ARMOR_TRACE_OUTPUT:complete armor output must use actual armor input minus this container armor reduction')
        mutations = calls(pre, event, 'setNewDamage')
        if len(mutations) != 1 or not re.fullmatch(r'\(float\)[\w$]+\.damage\(\)', compact(mutations[0][0]) if mutations and len(mutations[0]) == 1 else '') or len(re.findall(r'\bDamageMath\s*\.\s*mitigate\s*\(', java_text(pre, mask_strings=True))) != 1:
            errors.append('ARMOR_WHOLE_DAMAGE:Pre may apply typed resistance once, never armor normalization again')
    return errors


def check_armor_contract(root: Path, jar: Path | None = None) -> list[str]:
    errors = check_armor_packaging(root, jar)
    sources = {}
    paths = {
        'hook': JAVA_ROOT / 'mixin/CombatRulesMixin.java',
        'accessor': JAVA_ROOT / 'mixin/LivingDamageContainerAccess.java',
        'runtime': JAVA_ROOT / 'stats/runtime/RealmArmorRuntime.java',
        'stats': JAVA_ROOT / 'stats/runtime/StatsRuntime.java',
        'pressure': JAVA_ROOT / 'stats/ArmorPressure.java',
    }
    for name, path in paths.items():
        try:
            sources[name] = (root / path).read_text(encoding='utf-8')
        except (OSError, ValueError) as exc:
            errors.append(f'ARMOR_SOURCE:{path}:{exc}')
    if set(sources) == set(paths):
        errors.extend(check_armor_hook(sources['hook']))
        errors.extend(check_armor_runtime(sources['runtime'], sources['stats'], sources['accessor'], sources['pressure']))
    return errors



PENETRATION_IDS = {'PHYSICAL': 'resistance_penetration', **{
    element.upper(): f'{element}_resistance_penetration' for element in ('metal', 'wood', 'water', 'fire', 'earth')}}


def check_penetration_sources(attributes: str, runtime: str) -> list[str]:
    """Small structural checks reuse the existing source reader; numerical behavior stays in Java tests."""
    errors = []
    raw = java_text(attributes)
    registrations = re.findall(r'DeferredHolder\s*<\s*Attribute\s*,\s*Attribute\s*>\s+([\w$]+)\s*=\s*([\w$]+)\s*\(\s*"([^"]+)"\s*\)\s*;', raw)
    registrations = [(field, factory, rid) for field, factory, rid in registrations
                     if rid == 'resistance_penetration' or rid.endswith('_resistance_penetration')]
    by_id = {rid: field for field, _factory, rid in registrations}
    if len(registrations) != 6 or set(by_id) != set(PENETRATION_IDS.values()) or len(set(by_id.values())) != 6:
        errors.append('PENETRATION_IDS:six independent IDs required; legacy resistance_penetration remains physical-only')
    all_match = re.search(r'\bALL\s*=\s*List\s*\.\s*of\s*\(', raw)
    members = java_args(raw[all_match.end():closing(java_text(attributes, mask_strings=True), all_match.end() - 1)]) if all_match else []
    if any(members.count(field) != 1 for field in by_id.values()) or not all_match:
        errors.append('PENETRATION_ALL:every penetration holder must appear exactly once in synchronized diagnostics/attachment list')
    methods = {name: (params, body) for name, params, body, _at in java_methods(attributes)}
    factories = {factory for _field, factory, _rid in registrations}
    factory = methods.get(next(iter(factories))) if len(factories) == 1 else None
    leaf = None
    if factory and len(factory[0]) == 1:
        id_name = factory[0][0][1]
        registry = re.search(r'\b([\w$]+)\s*\.\s*register\s*\(', java_text(factory[1], mask_strings=True))
        registered = calls(factory[1], registry.group(1), 'register') if registry else []
        supplier = re.fullmatch(r'\(\)->([\w$]+)\(' + re.escape(id_name) + r'\)', compact(registered[0][1])) if len(registered) == 1 and len(registered[0]) == 2 and compact(registered[0][0]) == id_name else None
        leaf = methods.get(supplier.group(1)) if supplier else None
    if leaf is None or len(leaf[0]) != 1:
        errors.append('PENETRATION_FACTORY:all six independent registrations must use one data-bounded factory without alias copying')
    else:
        leaf_body = leaf[1]
        constructor = re.search(r'\bnew\s+RangedAttribute\s*\(', java_text(leaf_body, mask_strings=True))
        args = java_args(leaf_body[constructor.end():closing(java_text(leaf_body, mask_strings=True), constructor.end() - 1)]) if constructor else []
        limits = re.search(r'StatsRules\s*\.\s*Limits\s+([\w$]+)\s*=\s*StatsRules\s*\.\s*bundled\s*\(\s*\)\s*\.\s*limits\s*\(\s*\)', raw)
        numeric_zero = lambda value: compact(value) in {'0', '0.0', '0D', '0d', '0.0D', '0.0d'}
        if len(args) != 4 or not numeric_zero(args[1]) or not numeric_zero(args[2]) or not limits or compact(args[3]) != f'{limits.group(1)}.maxPenetration()':
            errors.append('PENETRATION_BOUNDS:independent defaults/minimum are zero and maximum must use bundled limits.maxPenetration')
        if len(args) != 4 or compact(args[0]) != f'"attribute.myvillage."+{leaf[0][0][1]}':
            errors.append('PENETRATION_TRANSLATION_FLOW:registered labels must use the independent registry ID')
        local = re.search(r'\bRangedAttribute\s+([\w$]+)\s*=\s*new\s+RangedAttribute', java_text(leaf_body, mask_strings=True))
        if local:
            sync = calls(leaf_body, local.group(1), 'setSyncable')
            returns = re.findall(r'\breturn\s+([^;]+);', java_text(leaf_body, mask_strings=True))
            synchronized = sync == [['true']] and bool(returns) and compact(returns[-1]) == local.group(1)
        else:
            synchronized = bool(re.search(r'\.\s*setSyncable\s*\(\s*true\s*\)', java_text(leaf_body, mask_strings=True)))
        if not synchronized:
            errors.append('PENETRATION_SYNC:the registered bounded attribute must be synchronized')
    attach = methods.get('attach')
    attached = re.search(r'for\s*\(\s*var\s+([\w$]+)\s*:\s*ALL\s*\)', java_text(attach[1], mask_strings=True)) if attach else None
    added = calls(attach[1], attach[0][0][1], 'add') if attach and len(attach[0]) == 1 else []
    if attached is None or not any(len(args) == 2 and compact(args[1]) == attached.group(1) for args in added):
        errors.append('PENETRATION_ATTACH:attach every ALL entry rather than a physical-only subset')
    for source in (attributes, runtime):
        for _name, _params, body, _at in java_methods(source):
            code = java_text(body, mask_strings=True)
            if re.search(r'\b[\w$]*RESISTANCE_PENETRATION\b', code) and re.search(r'\b(?:setBaseValue|addPermanentModifier|addTransientModifier|addOrReplacePermanentModifier)\s*\(', code):
                errors.append('PENETRATION_COPY:do not migrate or duplicate saved physical penetration into elemental attributes')
    runtime_methods = {name: (params, body) for name, params, body, _at in java_methods(runtime)}
    selector = runtime_methods.get('penetration')
    cases = re.findall(r'\bcase\s+([A-Z_]+)\s*->\s*ModStatsAttributes\s*\.\s*([\w$]+)\s*;', java_text(selector[1], mask_strings=True)) if selector else []
    expected = {kind: by_id.get(rid) for kind, rid in PENETRATION_IDS.items()}
    if len(cases) != 6 or dict(cases) != expected or any(value is None for value in expected.values()) or (selector and re.search(r'\bdefault\b', java_text(selector[1], mask_strings=True))):
        errors.append('PENETRATION_CHANNEL:each classified DamageType must select only its own independent holder; no shared/default fallback')
    single_read = runtime_methods.get('selectedPenetration')
    if not single_read or len(single_read[0]) != 2:
        errors.append('PENETRATION_SINGLE_READ:one holder read per classified hit required')
    else:
        damage_type, reader = [name for _kind, name in single_read[0]]
        body = compact(java_text(single_read[1], mask_strings=True))
        if body != f'return{reader}.applyAsDouble(penetration({damage_type}));':
            errors.append('PENETRATION_SINGLE_READ:read only the selected holder once without shared addition or cross-channel reads')
    pre = runtime_methods.get('beforeDamage')
    pre_body = pre[1] if pre else ''
    selected = re.search(r'\bdouble\s+([\w$]+)\s*=\s*([\w$]+)\s*\.\s*getEntity\s*\(\s*\)\s+instanceof\s+LivingEntity\s+([\w$]+)\s*\?\s*selectedPenetration\s*\(', java_text(pre_body, mask_strings=True))
    typed = re.search(r'Optional\s*<\s*DamageType\s*>\s+([\w$]+)\s*=', java_text(pre_body, mask_strings=True))
    if not selected or not typed:
        errors.append('PENETRATION_DAMAGE_FLOW:living attacker must use the classified channel and nonliving sources must use zero')
    else:
        start = selected.end() - 1
        end = closing(java_text(pre_body, mask_strings=True), start)
        args = java_args(pre_body[start + 1:end])
        reader = re.fullmatch(r'([\w$]+)->value\(' + re.escape(selected.group(3)) + r',([\w$]+)\)', compact(args[1])) if len(args) == 2 else None
        if len(args) != 2 or compact(args[0]) != f'{typed.group(1)}.get()' or reader is None or reader.group(1) != reader.group(2) or not re.match(r'\s*:\s*0(?:\.0)?\s*;', pre_body[end + 1:]):
            errors.append('PENETRATION_DAMAGE_FLOW:one matching attacker holder contributes; do not combine generic or unrelated penetration')
        mitigation_calls = calls(pre_body, 'DamageMath', 'mitigate')
        reads = re.findall(r'\bselectedPenetration\s*\(', java_text(pre_body, mask_strings=True))
        if len(reads) != 1 or len(mitigation_calls) != 1 or len(mitigation_calls[0]) != 4 or compact(mitigation_calls[0][2]) != selected.group(1):
            errors.append('PENETRATION_DAMAGE_FLOW:apply exactly the selected scalar once, without additions or a substituted value')
    if re.search(r'\bModStatsAttributes\s*\.\s*RESISTANCE_PENETRATION\b', java_text(runtime, mask_strings=True)):
        errors.append('PENETRATION_LEGACY_SHARED:the old registry ID is physical-only, never a shared damage scalar')
    return errors


def check_penetration_languages(languages: dict[str, Any]) -> list[str]:
    errors = []
    for locale in ('en_us', 'zh_cn'):
        table = languages.get(locale)
        for rid in PENETRATION_IDS.values():
            key = f'attribute.myvillage.{rid}'
            value = table.get(key) if isinstance(table, dict) else None
            if not isinstance(value, str) or not value.strip():
                errors.append(f'PENETRATION_LANG:{locale}:{key}:missing nonempty channel label')
        physical = table.get('attribute.myvillage.resistance_penetration', '') if isinstance(table, dict) else ''
        label = 'physical' if locale == 'en_us' else '物理'
        if not isinstance(physical, str) or label not in physical.lower():
            errors.append(f'PENETRATION_PHYSICAL_LABEL:{locale}:legacy registry ID must be labeled explicitly physical')
    return errors


def check_penetration_contract(root: Path, jar: Path | None = None) -> list[str]:
    errors = []
    paths = [JAVA_ROOT / 'stats/runtime/ModStatsAttributes.java', JAVA_ROOT / 'stats/runtime/StatsRuntime.java']
    try:
        attributes, runtime = [(root / path).read_text(encoding='utf-8') for path in paths]
        errors.extend(check_penetration_sources(attributes, runtime))
    except (OSError, ValueError) as exc:
        errors.append(f'PENETRATION_SOURCE:{exc}')
    languages = {locale: read_json(root, Path(f'src/main/resources/assets/myvillage/lang/{locale}.json'), errors)
                 for locale in ('en_us', 'zh_cn')}
    errors.extend(check_penetration_languages(languages))
    if jar is not None:
        try:
            with zipfile.ZipFile(jar) as archive:
                for locale in ('en_us', 'zh_cn'):
                    entry = f'assets/myvillage/lang/{locale}.json'
                    if archive.namelist().count(entry) != 1:
                        errors.append(f'PENETRATION_JAR:{entry}:must occur exactly once')
                        continue
                    packed = json.loads(archive.read(entry), object_pairs_hook=unique_object)
                    for rid in PENETRATION_IDS.values():
                        key = f'attribute.myvillage.{rid}'
                        if not isinstance(packed, dict) or packed.get(key) != languages[locale].get(key):
                            errors.append(f'PENETRATION_JAR:{entry}:{key}:missing or stale channel label')
                entry = 'com/example/myvillage/stats/runtime/ModStatsAttributes.class'
                if entry not in archive.namelist():
                    errors.append(f'PENETRATION_JAR:{entry}:missing')
                else:
                    registry = archive.read(entry)
                    if not registry.startswith(b'\xca\xfe\xba\xbe') or any(rid.encode() not in registry for rid in PENETRATION_IDS.values()):
                        errors.append('PENETRATION_JAR:compiled registry is missing independent penetration IDs')
                entry = 'com/example/myvillage/stats/runtime/StatsRuntime.class'
                if archive.namelist().count(entry) != 1:
                    errors.append(f'PENETRATION_JAR:{entry}:must occur exactly once')
                else:
                    runtime = archive.read(entry)
                    required_symbols = ['selectedPenetration', *(kind + '_RESISTANCE_PENETRATION' for kind in PENETRATION_IDS)]
                    if not runtime.startswith(b'\xca\xfe\xba\xbe') or any(symbol.encode() not in runtime for symbol in required_symbols):
                        errors.append('PENETRATION_JAR:compiled damage runtime is missing independent typed selection')
        except (OSError, ValueError, zipfile.BadZipFile) as exc:
            errors.append(f'PENETRATION_JAR:{jar}:{exc}')
    return errors


def validate(root: Path = ROOT, jar: Path | None = None) -> dict[str, Any]:
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
    errors.extend(check_armor_contract(root, jar))
    errors.extend(check_penetration_contract(root, jar))
    return {'schema_version': 1, 'status': 'fail' if errors else 'pass', 'errors': errors,
            'armor_jar': str(jar) if jar is not None else 'not_checked'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--report', type=Path, default=Path('reports/realm_stats_validation.json'))
    parser.add_argument('--jar', type=Path, help='also inspect packaged armor hooks and typed penetration registry/lang resources in this jar')
    args = parser.parse_args()
    root = args.root.resolve()
    jar = args.jar if args.jar is None or args.jar.is_absolute() else root / args.jar
    report = validate(root, jar)
    path = args.report if args.report.is_absolute() else root / args.report
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return int(report['status'] != 'pass')


if __name__ == '__main__':
    raise SystemExit(main())
