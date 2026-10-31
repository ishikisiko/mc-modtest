"""汇总完整声明路幅的原始实物读数；保留全部失败坐标，不使用通过率阈值。"""
from __future__ import annotations

import argparse
from collections import Counter, deque
import json
from pathlib import Path


def summarize(probe: dict, roads: list, columns: list) -> dict:
    failures = []
    usable = {}
    counts = Counter()
    surface_errors = []
    for column in columns:
        position = [column['x'], column['target_y'], column['z']]
        candidates = [b for b in column['blocks'] if b['pavement']]
        pavement = min(candidates, key=lambda b: abs(b['y'] - column['target_y']), default=None)
        counts['reserved_columns' if column['reserved'] else 'outside_columns'] += 1
        if pavement is None:
            failures.append({'position': position, 'reason': 'missing_pavement', 'reserved': column['reserved']})
            counts['missing_pavement'] += 1
            continue
        counts['pavement_columns'] += 1
        if not pavement['solid'] or pavement['fluid'] or not pavement['clear']:
            failures.append({'position': position, 'reason': 'blocked_or_liquid', 'observed': pavement})
            counts['blocked_or_liquid'] += 1
            continue
        counts['clear_pavement_columns'] += 1
        usable[column['x'], column['z']] = pavement['y']
        surface_errors.append(abs(pavement['y'] + 1 - column['actual_surface']))

    # A geometric developer check only: four-neighbour paths with at most one block height change.
    # It cannot establish actual player traversal, slab collision detail, support or appearance acceptance.
    remaining = set(usable)
    components = []
    while remaining:
        start = min(remaining)
        remaining.remove(start)
        queue = deque([start])
        size = 0
        while queue:
            x, z = queue.popleft()
            size += 1
            for other in [(x-1, z), (x+1, z), (x, z-1), (x, z+1)]:
                if other in remaining and abs(usable[other] - usable[x, z]) <= 1:
                    remaining.remove(other)
                    queue.append(other)
        components.append(size)

    def gate_gap(key):
        if key not in probe or not usable:
            return None
        x, y, z = probe[key]['pos']
        return min(((px-x)**2 + (py+1-y)**2 + (pz-z)**2)**.5 for (px, pz), py in usable.items())

    height_deltas = [abs(s['fast'] - s['generator']) for s in probe.get('height_samples', [])]
    states = [c['status'] for c in probe.get('connections', [])]
    expected_positions = {(p[0], segment['target_y'], p[2]) for road in roads
                          for segment in road.get('segments', []) for p in segment['positions']}
    observed = [(c['x'], c['target_y'], c['z']) for c in columns]
    observed_positions = set(observed)
    duplicates = sorted(p for p, count in Counter(observed).items() if count > 1)
    missing_positions = sorted(expected_positions-observed_positions)
    unexpected_positions = sorted(observed_positions-expected_positions)
    complete = bool(expected_positions) and len(columns) == probe.get('declared_columns') \
        and len(expected_positions) == len(columns) and not duplicates and not missing_positions and not unexpected_positions
    from_gap, to_gap = gate_gap('from'), gate_gap('to')
    return {
        'phase': probe.get('phase'), 'mode': probe.get('mode'),
        'full_width_readback_complete': complete,
        'connection_states': states, 'road_count': len(roads),
        'generation_seconds': probe.get('generation_seconds'), 'bake_seconds': probe.get('bake_seconds'),
        'declared_columns': probe.get('declared_columns', 0), 'sampled_columns': len(columns),
        'missing_readback_positions': missing_positions, 'unexpected_readback_positions': unexpected_positions,
        'duplicate_readback_positions': duplicates,
        'counts': dict(counts), 'failed_columns': failures,
        'clear_pavement_components': sorted(components, reverse=True),
        'from_gate_pavement_distance': from_gap, 'to_gate_pavement_distance': to_gap,
        'fast_vs_generator_max_difference': max(height_deltas, default=None),
        'fast_vs_generator_mean_difference': sum(height_deltas)/len(height_deltas) if height_deltas else None,
        'forbidden_crossings': probe.get('forbidden_crossings', []),
        'span_types': dict(Counter(s['type'] for r in roads for s in r.get('spans', []))),
        'pavement_connectivity_pass': bool(complete and not failures and len(components) == 1
            and not probe.get('forbidden_crossings') and states and all(s == 'COMPLETED' for s in states)
            and from_gap is not None and to_gap is not None and from_gap <= 1 and to_gap <= 1),
        'evidence_limits': {
            'pavement_detection': 'block id matches the generated road record; reserved-city paving may pre-exist',
            'height_search': 'only the configured vertical interval is inspected',
            'structural_support': 'not_verified', 'actual_player_traversal': 'not_verified',
            'owner_appearance': 'not_verified', 'fps': 'not_verified', 'multiplayer': 'not_verified',
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    def read(name, default):
        path = args.directory/name
        return json.loads(path.read_text()) if path.exists() else default
    result = summarize(read('probe.json', {}), read('roads.json', []), read('columns.json', []))
    (args.directory/'audit.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'failed_columns'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
