"""Read-only release contract for the shipped player progression graph.

Realm JSON owns tuning. This helper owns stage identity, adjacency, the release boundary and
retained first-four regression anchors; it never synthesizes runtime progression rules.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

REALMS = Path('src/main/resources/data/myvillage/myvillage/realm')
STATS = Path('src/main/resources/data/myvillage/stats/rules.json')
SIM_REALMS = Path('src/main/resources/data/myvillage/world_sim/realms.json')
MAX_INT = 2 ** 31 - 1
MAX_LONG = 2 ** 63 - 1
REALM_LIFESPANS = {'myvillage:mortal': 80, 'myvillage:qi_refining': 120,
                   'myvillage:foundation_establishment': 240, 'myvillage:golden_core': 500}
REALM_STAGE_IDS = {
    'myvillage:mortal': ('myvillage:mortal_unawakened', 'myvillage:mortal_qi_sensed'),
    'myvillage:qi_refining': tuple(f'myvillage:qi_refining_{i}' for i in range(1, 10)),
    'myvillage:foundation_establishment': ('myvillage:foundation_early', 'myvillage:foundation_middle', 'myvillage:foundation_late'),
    'myvillage:golden_core': ('myvillage:golden_early', 'myvillage:golden_middle', 'myvillage:golden_late'),
}
STAGE_OWNERS = {stage: realm for realm, stages in REALM_STAGE_IDS.items() for stage in stages}
UNAWAKENED = 'myvillage:mortal_unawakened'
PLAYABLE_CHAIN = tuple(stage for stage in STAGE_OWNERS if stage != UNAWAKENED)
TERMINAL = PLAYABLE_CHAIN[-1]
TARGETS = dict(zip(PLAYABLE_CHAIN, PLAYABLE_CHAIN[1:]))
BOTTLENECKS = frozenset({'myvillage:qi_refining_3', 'myvillage:qi_refining_6',
                         'myvillage:qi_refining_9', 'myvillage:foundation_late'})
# Accepted legacy tuning only. New stage tuning is read from authored realm JSON.
BASELINE_ROWS = (
    ('myvillage:mortal_qi_sensed', 1000, 1, 'ordinary', 100, 0),
    ('myvillage:qi_refining_1', 1100, 1, 'ordinary', 100, 0),
    ('myvillage:qi_refining_2', 1200, 2, 'ordinary', 120, 0),
    ('myvillage:qi_refining_3', 1300, 3, 'bottleneck', 200, 5),
)
BASELINE_CAPS = {row[0]: row[1] for row in BASELINE_ROWS}
BASELINE_COSTS = {row[0]: row[2] for row in BASELINE_ROWS}
BASELINE_ADVANCEMENTS = {
    stage: {'target_realm': STAGE_OWNERS[TARGETS[stage]], 'target_stage': TARGETS[stage],
            'kind': kind, 'duration_ticks': duration, 'required_stability': cap // 2,
            'stability_cost': cap // 2 - (cap // 2) // 2, 'interruption_stability_loss': loss}
    for stage, cap, _cost, kind, duration, loss in BASELINE_ROWS
}
BASELINE_POWER_CAPS = {UNAWAKENED: 0, 'myvillage:mortal_qi_sensed': 20,
                       **{f'myvillage:qi_refining_{i}': 20 + 10 * i for i in range(1, 10)},
                       'myvillage:foundation_early': 200}
ADVANCEMENT_FIELDS = frozenset(next(iter(BASELINE_ADVANCEMENTS.values())))


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate JSON key {key!r}')
        result[key] = value
    return result


def integer(value: Any, lo: int, hi: int) -> bool:
    return type(value) is int and lo <= value <= hi


def validate_progression(root: Path, *, cross_files: bool = False) -> tuple[list[tuple[Path, str]], set[Path]]:
    """Return actionable errors and inspected paths; shared by the three focused gates."""
    errors: list[tuple[Path, str]] = []
    checked: set[Path] = set()

    def error(path, code, message):
        errors.append((path, f'{code}: {message}'))

    def load(path):
        checked.add(root / path)
        try:
            obj = json.loads((root / path).read_text(encoding='utf-8'), object_pairs_hook=unique_object)
            if not isinstance(obj, dict):
                raise ValueError('expected JSON object')
            return obj
        except (OSError, UnicodeError, ValueError) as exc:
            error(path, 'PROGRESSION_FILE', str(exc))
            return {}

    realm_values: dict[str, dict] = {}
    stage_values: dict[str, tuple[Path, dict]] = {}
    paths = sorted((root / REALMS).glob('*.json'))
    if not paths:
        error(REALMS, 'PROGRESSION_REALM', 'missing realm definitions')
    for absolute in paths:
        path = absolute.relative_to(root)
        realm_id = f'{path.parent.parent.name}:{path.stem}'
        obj = load(path)
        realm_values[realm_id] = obj
        stages = obj.get('stages')
        if not isinstance(stages, list):
            error(path, 'PROGRESSION_STAGE', 'stages must be a list')
            continue
        ids = []
        for index, stage in enumerate(stages):
            if not isinstance(stage, dict) or not isinstance(stage.get('id'), str):
                error(path, 'PROGRESSION_STAGE', 'stage must have a string id')
                continue
            sid = stage['id']
            ids.append(sid)
            if sid in stage_values:
                error(path, 'PROGRESSION_STAGE', f'duplicate stage {sid}')
            stage_values[sid] = (path, stage)
            if STAGE_OWNERS.get(sid) != realm_id:
                error(path, 'PROGRESSION_OWNER', f'{sid} must belong to {STAGE_OWNERS.get(sid)}, not {realm_id}')
            if not integer(stage.get('sort_order'), 0, MAX_INT) or stage['sort_order'] != index:
                error(path, 'PROGRESSION_ORDER', f'{sid} sort_order must equal its zero-based release position {index}')
        if tuple(ids) != REALM_STAGE_IDS.get(realm_id):
            error(path, 'PROGRESSION_STAGE', f'{realm_id} stages must exactly match the complete ordered release ladder')
    if set(realm_values) != set(REALM_STAGE_IDS):
        error(REALMS, 'PROGRESSION_REALM', f'missing={sorted(set(REALM_STAGE_IDS)-set(realm_values))}, unknown={sorted(set(realm_values)-set(REALM_STAGE_IDS))}')
    ordered_realms = tuple(REALM_STAGE_IDS)
    for index, realm_id in enumerate(ordered_realms):
        path = REALMS / (realm_id.split(':')[1] + '.json')
        realm = realm_values.get(realm_id)
        if realm is None:
            continue
        if not integer(realm.get('sort_order'), 0, MAX_INT) or realm['sort_order'] != index:
            error(path, 'PROGRESSION_ORDER', f'{realm_id} must have realm sort_order {index}')
        expected_next = ordered_realms[index + 1] if index + 1 < len(ordered_realms) else None
        if realm.get('next_realm') != expected_next:
            error(path, 'PROGRESSION_NEXT_REALM', f'{realm_id} next_realm must be {expected_next}')
        if not integer(realm.get('maximum_lifespan_years'), 1, MAX_INT) or realm['maximum_lifespan_years'] != REALM_LIFESPANS[realm_id]:
            error(path, 'PROGRESSION_LIFESPAN', f'{realm_id} maximum_lifespan_years must be {REALM_LIFESPANS[realm_id]}')

    edges: dict[str, str] = {}
    sinks = []
    last_power = 0
    for sid in (UNAWAKENED, *PLAYABLE_CHAIN):
        entry = stage_values.get(sid)
        if entry is None:
            error(REALMS, 'PROGRESSION_STAGE', f'missing required stage {sid}')
            continue
        path, stage = entry
        power = stage.get('spiritual_power_cap')
        if not integer(power, 0 if sid == UNAWAKENED else 1, MAX_LONG):
            error(path, 'PROGRESSION_POWER', f'{sid} spiritual_power_cap must be an explicit bounded integer')
        else:
            if power < last_power:
                error(path, 'PROGRESSION_POWER', f'{sid} power capacity must not decrease across the release ladder')
            last_power = power
            if sid in BASELINE_POWER_CAPS and power != BASELINE_POWER_CAPS[sid]:
                error(path, 'PROGRESSION_POWER', f'{sid} must preserve existing power capacity {BASELINE_POWER_CAPS[sid]}')
        rule = stage.get('advancement')
        cap = stage.get('cultivation_cap')
        if sid in {UNAWAKENED, TERMINAL}:
            if 'cultivation_cap' in stage or 'advancement' in stage:
                error(path, 'PROGRESSION_TERMINAL', f'{sid} must omit cultivation_cap and advancement')
            if sid == TERMINAL:
                sinks.append(sid)
            continue
        if rule is None:
            sinks.append(sid)
        if not integer(cap, 2, 2 * MAX_INT + 1):
            error(path, 'PROGRESSION_CAP', f'{sid} cultivation_cap must be >=2 with cap/2 in Java integer range')
        cost = stage.get('spirit_stone_cost')
        if not integer(cost, 1, MAX_INT):
            error(path, 'PROGRESSION_COST', f'cultivatable stage {sid} must declare positive spirit_stone_cost within Java integer range')
        if sid in BASELINE_CAPS and cap != BASELINE_CAPS[sid]:
            error(path, 'PROGRESSION_BASELINE', f'{sid} cultivation_cap must be integer {BASELINE_CAPS[sid]}, got {cap!r}')
        if sid in BASELINE_COSTS and cost != BASELINE_COSTS[sid]:
            error(path, 'PROGRESSION_BASELINE', f'stage {sid} spirit_stone_cost must be {BASELINE_COSTS[sid]}, got {cost!r}')
        if not isinstance(rule, dict):
            error(path, 'PROGRESSION_RULE', f'{sid} must declare advancement metadata')
            continue
        if set(rule) != ADVANCEMENT_FIELDS:
            error(path, 'PROGRESSION_RULE', f'{sid} advancement fields must be exactly {sorted(ADVANCEMENT_FIELDS)}')
        target = rule.get('target_stage')
        expected_target = TARGETS[sid]
        if (rule.get('target_realm'), target) != (STAGE_OWNERS[expected_target], expected_target):
            error(path, 'PROGRESSION_ADJACENCY', f'{sid} must target immediate next stage {STAGE_OWNERS[expected_target]}/{expected_target}; no skipped or reversed stages')
        if isinstance(target, str):
            edges[sid] = target
        expected_kind = 'bottleneck' if sid in BOTTLENECKS else 'ordinary'
        if rule.get('kind') != expected_kind:
            error(path, 'PROGRESSION_KIND', f'{sid} kind must be {expected_kind}')
        loss = rule.get('interruption_stability_loss')
        if not integer(loss, 1 if expected_kind == 'bottleneck' else 0, MAX_INT) or (expected_kind == 'ordinary' and loss != 0):
            error(path, 'PROGRESSION_LOSS', f'{sid} interruption loss must be positive for a bottleneck and zero for ordinary advancement')
        for key, lo in (('duration_ticks', 1), ('required_stability', 0), ('stability_cost', 0)):
            if not integer(rule.get(key), lo, MAX_INT):
                error(path, 'PROGRESSION_RULE', f'{sid} {key} must be an integer in [{lo},{MAX_INT}]')
        if integer(cap, 2, 2 * MAX_INT + 1):
            required = cap // 2
            if rule.get('required_stability') != required:
                error(path, 'PROGRESSION_STABILITY', f'{sid} required_stability must equal half of cultivation_cap')
            if rule.get('stability_cost') != required - required // 2:
                error(path, 'PROGRESSION_RETENTION', f'{sid} stability_cost must retain half of required stability')
        if sid in BASELINE_ADVANCEMENTS and rule != BASELINE_ADVANCEMENTS[sid]:
            error(path, 'PROGRESSION_BASELINE', f'{sid} advancement must be {BASELINE_ADVANCEMENTS[sid]!r}, got {rule!r}')
    if sinks != [TERMINAL]:
        error(REALMS, 'PROGRESSION_TERMINAL', f'only {TERMINAL} may terminate the playable chain; found {sinks}')
    visited: set[str] = set()
    current = PLAYABLE_CHAIN[0]
    while current in stage_values:
        if current in visited:
            error(REALMS, 'PROGRESSION_CYCLE', f'cycle at {current}')
            break
        visited.add(current)
        if current not in edges:
            break
        current = edges[current]
    if visited != set(PLAYABLE_CHAIN):
        error(REALMS, 'PROGRESSION_REACHABILITY', f'missing={sorted(set(PLAYABLE_CHAIN)-visited)}, unknown={sorted(visited-set(PLAYABLE_CHAIN))}')

    if cross_files:
        stats = load(STATS)
        aliases = stats.get('player_stage_aliases')
        expected = {sid: (rid, i) for rid, ids in REALM_STAGE_IDS.items() for i, sid in enumerate(ids)}
        actual = {}
        if not isinstance(aliases, list):
            error(STATS, 'PROGRESSION_ALIAS', 'player_stage_aliases must be a list')
            aliases = []
        for alias in aliases:
            if not isinstance(alias, dict) or not isinstance(alias.get('id'), str):
                error(STATS, 'PROGRESSION_ALIAS', 'invalid player alias')
                continue
            sid = alias['id']
            if sid in actual:
                error(STATS, 'PROGRESSION_ALIAS', f'duplicate player alias {sid}')
            actual[sid] = (alias.get('realm'), alias.get('stage'))
            if not integer(alias.get('stage'), 0, MAX_INT) or actual[sid] != expected.get(sid):
                error(STATS, 'PROGRESSION_ALIAS', f'{sid} alias must match its exact realm and zero-based stage')
        if set(actual) != set(expected):
            error(STATS, 'PROGRESSION_ALIAS', f'exact stage coverage required; missing={sorted(set(expected)-set(actual))}, unknown={sorted(set(actual)-set(expected))}')
        sim = load(SIM_REALMS)
        sim_rows = sim.get('realms', [])
        sim_by_id = {row['id']: row for row in sim_rows if isinstance(row, dict) and isinstance(row.get('id'), str)} if isinstance(sim_rows, list) else {}
        for rid in ordered_realms[1:]:
            row = sim_by_id.get(rid.split(':')[1])
            if row is None or row.get('lifespan_years') != realm_values.get(rid, {}).get('maximum_lifespan_years'):
                error(SIM_REALMS, 'PROGRESSION_LIFESPAN', f'{rid} lifespan must agree with the ledger')
            if row is None or not isinstance(row.get('stages'), list) or len(row['stages']) != len(REALM_STAGE_IDS[rid]):
                error(SIM_REALMS, 'PROGRESSION_STAGE_MAPPING', f'{rid} player and ledger stage counts must agree')
    return errors, checked
