"""按大境界展开掉落模板，生成工作台可以直接编辑的统一表。"""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

from .rules import Invalid, MAX_BYTES, decode, quantity_limit, validate
from .server import Conflict, Store


ROOT = Path(__file__).resolve().parents[2]
NUMERIC_FIELDS = ('min', 'max', 'chance')
DROP_FIELDS = {*NUMERIC_FIELDS, 'item', 'factory', 'looting', 'chance_looting', 'rarity_weights', 'quality', 'rarity', 'scale_by_realm'}
SCOPES = {'scenes', 'regions', 'player_kill', 'fire_captured'}
MATERIAL_NAMES = {'beast_core': '妖丹', 'beast_hide': '妖兽皮', 'beast_bone': '妖兽骨'}


@dataclass(frozen=True)
class Plan:
    config: dict
    additions: list
    rows: list
    replaced: list


def fields(value, path, allowed, required=()):
    if not isinstance(value, dict):
        raise Invalid(f'{path}应为对象')
    if unknown := value.keys() - allowed:
        raise Invalid(f'{path}有未知字段 {", ".join(sorted(unknown))}')
    if missing := set(required) - value.keys():
        raise Invalid(f'{path}缺少字段 {", ".join(sorted(missing))}')
    return value


def selection(value, path, options, *, required=False):
    if not isinstance(value, list) or required and not value:
        raise Invalid(f'{path}应为{"非空" if required else ""}列表')
    if any(not isinstance(id, str) or id not in options for id in value) or len(set(value)) != len(value):
        raise Invalid(f'{path}包含无效或重复的选项')
    return value


def specs(value, path, realms):
    if not isinstance(value, dict):
        raise Invalid(f'{path}应为按掉落项名称组织的对象')
    for name, spec in value.items():
        if spec is None:
            continue
        label = f'{path}.{name}'
        fields(spec, label, DROP_FIELDS | {'by_realm'})
        tiers = fields(spec.get('by_realm', {}), label + '.by_realm', realms)
        for realm, patch in tiers.items():
            if patch is not None:
                fields(patch, f'{label}.by_realm.{realm}', DROP_FIELDS)
    return value


def merge_drop(shared, individual, realm):
    if shared is None or individual is None:
        return None
    shared_tier = shared.get('by_realm', {}).get(realm, {})
    individual_tiers = individual.get('by_realm', {})
    individual_tier = individual_tiers.get(realm, {})
    if individual_tier is None or shared_tier is None and realm not in individual_tiers:
        return None
    drop = {**shared, **(shared_tier or {}), **individual, **individual_tier}
    drop.pop('by_realm', None)
    return drop


def evaluate_formula(value, offset, upper, path):
    params = fields(value, path, {'base', 'per_realm'}, ('base', 'per_realm'))
    if any(type(number) not in (int, float) or not Decimal(str(number)).is_finite()
           for number in params.values()):
        raise Invalid(f'{path}的基值和每境增量应为有限数值')
    result = Decimal(str(params['base'])) + offset * Decimal(str(params['per_realm']))
    if not 0 <= result <= upper:
        raise Invalid(f'{path}的计算结果应在 0 至 {upper} 之间')
    return int(result) if result == result.to_integral_value() else float(result)


def compile_plan(config, blueprint, catalogue):
    validate(config, catalogue)
    options = fields(blueprint, '掉落模板', {'id', 'realms', 'when', 'template', 'entities', 'replace_additions'},
                     ('id', 'realms', 'entities'))
    identifier = options['id']
    if not isinstance(identifier, str) or not re.fullmatch(r'[a-z0-9_]{1,48}', identifier):
        raise Invalid('模板 ID 应为 1 至 48 位小写字母、数字或下划线')
    realm_data = {row['id']: row for row in catalogue['realms']}
    realms = sorted(selection(options['realms'], '模板境界', realm_data, required=True),
                    key=lambda id: realm_data[id]['order'])
    first_order = realm_data[realms[0]]['order']
    scope = fields(options.get('when', {}), '适用条件', SCOPES)
    entity_names = {row['id']: row['name'] for row in catalogue['entities']}
    targets = fields(options['entities'], '生物配置', entity_names.keys())
    if not targets:
        raise Invalid('至少选择一种生物')
    shared = specs(options.get('template', {}), '共用掉落', realms)
    item_data = {row['id']: row for row in catalogue['items']}

    grouped = defaultdict(list)
    rows = []
    for entity, target in targets.items():
        fields(target, entity, {'realms', 'drops'})
        enabled = selection(target.get('realms', realms), entity + '境界', realms, required=True)
        individual = specs(target.get('drops', {}), entity, realms)
        for realm in realms:
            if realm not in enabled:
                continue
            rank = realm_data[realm]
            drops = []
            for slot in dict.fromkeys([*shared, *individual]):
                patch = individual.get(slot, {})
                drop = merge_drop(shared.get(slot, {}), patch, realm)
                if not drop:
                    continue
                formulas = {key: drop[key] for key in NUMERIC_FIELDS if isinstance(drop.get(key), dict)}
                for key, value in formulas.items():
                    upper = 1 if key == 'chance' else quantity_limit(drop.get('item'), drop.get('scale_by_realm', False), catalogue)
                    drop[key] = evaluate_formula(value, rank['order'] - first_order, upper, f'{entity}.{slot}.{realm}.{key}')
                drops.append(drop)
                authored = patch.keys() - {'by_realm'} or realm in patch.get('by_realm', {})
                source = '单独配置' if authored else '共用公式' if formulas else '共用模板'
                rows.append({'entity_id': entity, 'entity': entity_names[entity], 'realm': rank['name'],
                             'drop': drop, 'source': source})
            if drops:
                grouped[(realm, json.dumps(drops, sort_keys=True, ensure_ascii=False))].append(entity)

    additions = []
    for (realm, encoded), entities in grouped.items():
        # 数值变化时只要生物组和境界相同，生成的规则 ID 就保持相同。
        group_key = '\n'.join([realm, *sorted(entities)])
        suffix = hashlib.sha256(group_key.encode()).hexdigest()[:12]
        when = {
            **scope,
            'entities': sorted(entities),
            'min_realm': {'realm': realm, 'stage': 0},
            'max_realm': {'realm': realm, 'stage': realm_data[realm]['stages'] - 1},
        }
        additions.append({'id': f'{identifier}_{suffix}', 'when': when, 'drops': json.loads(encoded)})

    existing = {entry['id']: entry for entry in config['additions']}
    replaced = selection(options.get('replace_additions', []), '替换的追加规则', existing)
    retained = [entry for entry in config['additions'] if entry['id'] not in replaced]
    by_id = {entry['id']: entry for entry in retained}
    for entry in additions:
        if entry['id'] not in by_id:
            retained.append(entry)
        elif entry != by_id[entry['id']]:
            raise Invalid(f'生成规则 {entry["id"]} 和现有规则不同，请将要替换的规则列入 replace_additions')
    result = {**config, 'additions': retained}
    validate(result, catalogue)
    if len(encoded_config(result).encode()) > MAX_BYTES:
        raise Invalid('生成后的统一表超过 1 MiB')

    for row in rows:
        item = item_data[row['drop']['item']]
        if row['drop'].get('factory') == 'beast_material':
            row['item'] = MATERIAL_NAMES[item['beast_material']['family']] + '（随境界）'
        else:
            row['item'] = item['name']
    return Plan(result, additions, rows, replaced)


def encoded_config(data):
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def render_html(plan, blueprint):
    payload = json.dumps({'rows': plan.rows, 'additions': plan.additions, 'replaced': plan.replaced, 'input': blueprint},
                         ensure_ascii=False).replace('<', '\\u003c')
    template = ROOT / 'web/drop-workbench/realm-drops-preview.html'
    return template.read_text(encoding='utf-8').replace('__REALM_DROP_DATA__', payload)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=ROOT)
    parser.add_argument('--state', type=Path, default=Path.home() / '.local/state/myvillage-drop-workbench')
    parser.add_argument('--catalogue', type=Path, required=True)
    commands = parser.add_subparsers(dest='command', required=True)
    preview = commands.add_parser('preview', help='生成预览和候选统一表')
    preview.add_argument('blueprint', type=Path)
    preview.add_argument('--out', type=Path, required=True)
    preview.add_argument('--html', type=Path)
    apply = commands.add_parser('apply', help='将已查看的候选表写入工作台')
    apply.add_argument('candidate', type=Path)
    apply.add_argument('--revision', required=True)
    args = parser.parse_args()

    store = Store(args.repo, json.loads(args.catalogue.read_text(encoding='utf-8')), args.state)
    try:
        if args.command == 'apply':
            result = store.save(decode(args.candidate.read_bytes()), args.revision)
            print(f'统一表已保存 {result["revision"]}')
            return
        files = [store.file, args.catalogue, args.blueprint, args.out]
        if args.html:
            files.append(args.html)
        if len({p.resolve() for p in files}) != len(files):
            raise Invalid('输入和输出请使用不同文件，统一表通过 apply 写入')
        with (args.state / 'write.lock').open('a+b') as lock:
            fcntl.flock(lock, fcntl.LOCK_SH)
            snapshot = store.read()
        blueprint = decode(args.blueprint.read_bytes())
        plan = compile_plan(snapshot['config'], blueprint, store.catalogue)
        args.out.write_text(encoded_config(plan.config), encoding='utf-8')
        if args.html:
            args.html.write_text(render_html(plan, blueprint), encoding='utf-8')
        print(f'已生成 {len(plan.additions)} 条追加规则，统一表版本 {snapshot["revision"]}')
    except (Invalid, Conflict, OSError) as failure:
        parser.exit(1, str(failure) + '\n')


if __name__ == '__main__':
    main()
