#!/usr/bin/env python3
"""Bounded, deterministic Yunshan siting on actual saved terrain.

No world writes, hardcoded site corrections or synthetic terrain fallbacks.
The caller supplies flushed surveys; errors in that evidence are fatal, while
unsuitable terrain is an ordinary, explicitly reported search outcome.
"""
from __future__ import annotations

import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.yunshan.natural_terrain import (
    candidate_pool, evaluate_site, expanded_window, read_settings, sample_window,
    source_core,
)


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def search_windows(settings):
    cfg = settings['automatic']
    center = cfg['center']; width, depth = cfg['window_size']
    integer_fields = ('ring_step', 'ring_count', 'max_coarse_windows', 'max_expanded_surveys',
                      'max_fit_attempts', 'max_attempts_per_area', 'shortlist_per_window', 'area_separation')
    if any(type(cfg[key]) is not int or cfg[key] < 1 for key in integer_fields):
        raise ValueError('Automatic search limits must be positive integers')
    if len(center) != 2 or any(type(v) is not int for v in center):
        raise ValueError('Automatic search center must contain two integers')
    if any(type(v) is not int or not 16 <= v <= 1024 for v in (width, depth)):
        raise ValueError('Automatic search windows must fit bounded generation batches')
    # A stable center-out spiral. Ties do not depend on hash/set/input order.
    offsets = [(0, 0)]
    for ring in range(1, cfg['ring_count'] + 1):
        offsets.extend(sorted(((x, z) for z in range(-ring, ring + 1)
                               for x in range(-ring, ring + 1) if max(abs(x), abs(z)) == ring),
                              key=lambda p: (abs(p[0]) + abs(p[1]), p[1], p[0])))
    result = []
    for index, (dx, dz) in enumerate(offsets[:cfg['max_coarse_windows']]):
        x = center[0] + dx * cfg['ring_step'] - width // 2
        z = center[1] + dz * cfg['ring_step'] - depth // 2
        result.append({'id': f'coarse-{index}', 'window': [x, z, x + width, z + depth]})
    return result


def validate_survey(survey, seed):
    provenance = survey.get('provenance', {})
    if (provenance.get('seed') != seed or provenance.get('status') != 'saved_anvil_snapshot'
            or provenance.get('overworld_generator') != 'minecraft:noise'
            or not provenance.get('region_sha256') or not provenance.get('level_dat_sha256')):
        raise ValueError('Automatic siting requires hashed actual normal-world evidence for the requested seed')
    x0, z0, x1, z1 = survey['window']
    if (survey['width'], survey['depth']) != (x1 - x0, z1 - z0) or x1 <= x0 or z1 <= z0:
        raise ValueError('Invalid automatic survey extent')
    count = (x1 - x0) * (z1 - z0)
    for name in ('ground_y', 'surface_y', 'water_y', 'ground_index', 'artificial', 'hazard', 'surface_structure'):
        if len(survey.get(name, [])) != count:
            raise ValueError('Missing or incomplete automatic survey array: ' + name)
    for name in ('artificial', 'hazard', 'surface_structure'):
        if any(type(value) is not bool for value in survey[name]):
            raise ValueError('Invalid automatic survey flag: ' + name)
    for name in ('ground_y', 'surface_y'):
        if any(type(value) is not int or not -63 <= value <= 320 for value in survey[name]):
            raise ValueError('Invalid automatic survey height: ' + name)


def footprint_indices(survey, candidate):
    x0, z0, x1, z1 = survey['window']; x, z = candidate['origin']; width, depth = candidate['size']
    if not (x0 <= x and z0 <= z and x + width <= x1 and z + depth <= z1):
        raise ValueError('Candidate is outside its actual survey')
    return [(row - z0) * survey['width'] + col - x0
            for row in range(z, z + depth) for col in range(x, x + width)]


def occupancy_rejections(survey, indices, cfg, stage):
    reasons = []
    for field, code in (('artificial', 'existing_construction'), ('hazard', 'surface_hazard'),
                        ('surface_structure', 'surface_structure_reservation')):
        affected = [k for k in indices if survey[field][k]]
        threshold_name = {'artificial': 'maximum_modified_artificial_columns',
                          'hazard': 'maximum_modified_hazard_columns',
                          'surface_structure': 'maximum_modified_structure_columns'}[field]
        limit = cfg['gates'][threshold_name]
        if len(affected) > limit:
            x0, z0, _, _ = survey['window']; width = survey['width']
            reasons.append({'code': code, 'stage': stage, 'measured': len(affected), 'limit': limit,
                            'examples': [[x0 + k % width, z0 + k // width] for k in affected[:5]]})
    return reasons


def fit_rejections(result, cfg):
    reasons = [{'code': name, 'stage': 'full_fit'} for name, passed
               in result['validation']['checks'].items() if not passed]
    if result['validation'].get('passed') is not True and not reasons:
        reasons.append({'code': 'fit_validation_failed', 'stage': 'full_fit'})
    # The fitter, not a coarse rectangle, is the authority on the write mask.
    survey = result['survey'].data
    indices = [k for k, changed in enumerate(result['changed']) if changed]
    reasons.extend(occupancy_rejections(survey, indices, cfg, 'full_write_mask'))
    metrics = result['metrics']
    values = {'maximum_total_earthwork': metrics['cut_blocks'] + metrics['fill_blocks'],
              'maximum_peak_cut_or_fill': metrics['maximum_cut_or_fill'],
              'maximum_earthwork_per_modified_column': (metrics['cut_blocks'] + metrics['fill_blocks'])
                  / max(1, metrics['modified_columns'])}
    for key, value in values.items():
        if value > cfg['gates'][key]:
            reasons.append({'code': key, 'stage': 'full_fit', 'measured': value, 'limit': cfg['gates'][key]})
    return reasons


def select_site(seed, survey_provider, settings=None, *, core=None, fit_function=None, progress=None):
    """Return (report, selected_survey, selected_fit), or an explicit refusal.

    survey_provider(window, label, settings) must return immutable flushed data.
    Progress receipts are durable even when a provider or fitter raises; an
    infrastructure/invalid-data exception is never reclassified as bad terrain.
    """
    if type(seed) is not int or not -(1 << 63) <= seed < (1 << 63):
        raise ValueError('seed must be a signed 64-bit integer')
    settings = copy.deepcopy(settings or read_settings()); cfg = settings['automatic']
    windows = search_windows(settings)
    core = core or source_core(settings)
    if fit_function is None:
        from tools.yunshan.fit_natural import plan_fit
        fit_function = plan_fit
    report = {'algorithm': cfg['algorithm'], 'seed': seed, 'status': 'searching', 'selected': None,
              'settings_sha256': fingerprint(settings), 'windows': windows, 'surveys': [],
              'attempts': [], 'coarse_rejections': {}, 'coarse_rejection_examples': {}, 'coarse_candidates': 0,
              'gates': {'coarse': settings['gates'], 'automatic': cfg['gates']},
              'budgets': {key: cfg[key] for key in ('max_coarse_windows', 'max_expanded_surveys',
                  'max_fit_attempts', 'max_attempts_per_area', 'shortlist_per_window')},
              'expanded_surveys': 0, 'fit_attempts': 0, 'manual_refinements_used': False}
    def emit():
        report['reason_counts'] = dict(sorted(Counter(reason['code'] for attempt in report['attempts']
                                           for reason in attempt['reasons']).items()))
        if progress: progress(copy.deepcopy(report))
    def survey(window, label):
        data = survey_provider(window, label, settings)
        validate_survey(data, seed)
        if data['window'] != window:
            raise ValueError('Survey provider returned a different requested window')
        report['surveys'].append({'label': label, 'window': window,
                                  'provenance': data['provenance'], 'sha256': fingerprint(data)})
        emit()
        return data
    shortlist = []; coarse = []; rejected = Counter()
    for item in windows:
        data = survey(item['window'], item['id']); coarse.append(data)
        pool = candidate_pool([data], core, settings)
        kept = 0
        for candidate in pool:
            report['coarse_candidates'] += 1
            candidate['id'] = f"auto-q{candidate['rotation_quarters']}-{candidate['origin'][0]}-{candidate['origin'][1]}"
            candidate['survey_index'] = len(coarse) - 1
            reasons = list(candidate['limitations'])
            if not reasons:
                reasons.extend(reason['code'] for reason in occupancy_rejections(
                    data, footprint_indices(data, candidate), cfg, 'coarse_full_footprint'))
            low = core.get('minimum', [0, 62, 0])[1] + candidate['dy']
            if low < settings['world_min_y'] + 1 or low + core['size'][1] > settings['world_min_y'] + settings['world_height']:
                reasons.append('world_height_limit')
            if reasons:
                rejected.update(reasons)
                for reason in reasons:
                    examples = report['coarse_rejection_examples'].setdefault(reason, [])
                    if len(examples) < 3:
                        examples.append({'origin': candidate['origin'], 'rotation_quarters': candidate['rotation_quarters'],
                                         'metrics': candidate['metrics']})
            elif kept < cfg['shortlist_per_window']:
                shortlist.append(candidate); kept += 1
        report['coarse_rejections'] = dict(sorted(rejected.items())); emit()
    shortlist.sort(key=lambda c: (c['score'], c['origin'], c['rotation_quarters']))
    report['shortlist'] = [{'id': c['id'], 'origin': c['origin'], 'rotation_quarters': c['rotation_quarters'],
                            'score': c['score'], 'survey_index': c['survey_index']} for c in shortlist]
    emit()
    # Choose spatially different areas; fully try a bounded local ranked pool
    # before paying to generate another expanded terrain window.
    areas = []
    for candidate in shortlist:
        if all(sum((candidate['origin'][i] - area['origin'][i]) ** 2 for i in (0, 1))
               >= cfg['area_separation'] ** 2 for area in areas):
            areas.append(candidate)
        if len(areas) == cfg['max_expanded_surveys']: break
    for area in areas:
        if report['fit_attempts'] >= cfg['max_fit_attempts']: break
        data = survey(expanded_window(area, settings), f"expanded-{area['id']}")
        report['expanded_surveys'] += 1
        local = [candidate for candidate in shortlist if candidate['survey_index'] == area['survey_index']]
        # Expanded extent is centered on this area's best coarse candidate.
        x0, z0, x1, z1 = data['window']
        local = [c for c in local if x0 <= c['origin'][0] and z0 <= c['origin'][1]
                 and c['origin'][0] + c['size'][0] <= x1 and c['origin'][1] + c['size'][1] <= z1]
        rescored = []
        for candidate in local:
            fresh = evaluate_site(data, candidate['origin'], candidate['rotation_quarters'], core, settings)
            fresh['id'] = candidate['id']; fresh['survey_index'] = candidate['survey_index']
            rescored.append(fresh)
        rescored.sort(key=lambda c: (c['score'], c['origin'], c['rotation_quarters']))
        for candidate in rescored[:cfg['max_attempts_per_area']]:
            if report['fit_attempts'] >= cfg['max_fit_attempts']: break
            reasons = [{'code': name, 'stage': 'expanded_rescore'} for name in candidate['limitations']]
            reasons.extend(occupancy_rejections(data, footprint_indices(data, candidate), cfg, 'expanded_full_footprint'))
            attempt = {'candidate': candidate, 'survey_label': f"expanded-{area['id']}",
                       'status': 'rejected', 'reasons': reasons}
            if not reasons:
                report['fit_attempts'] += 1
                from tools.yunshan.fit_natural import UnsuitableTerrain
                try:
                    result = fit_function(data, candidate)
                except UnsuitableTerrain as error:
                    reasons.append({'code': 'terrain_fit_conflict', 'stage': 'full_fit', 'detail': str(error)})
                    report['attempts'].append(attempt); emit()
                    continue
                reasons.extend(fit_rejections(result, cfg))
                attempt['fit_validation'] = result['validation']; attempt['fit_metrics'] = result['metrics']
                if not reasons:
                    attempt['status'] = 'selected'; report['selected'] = candidate
                    report['status'] = 'selected'; report['attempts'].append(attempt); emit()
                    return report, data, result
            report['attempts'].append(attempt); emit()
    report['status'] = 'no_suitable_site_within_budget'
    report['reason_counts'] = dict(sorted(Counter(reason['code'] for attempt in report['attempts']
                                       for reason in attempt['reasons']).items()))
    emit()
    return report, None, None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True, help='Stopped or immutable flushed NORMAL world snapshot')
    parser.add_argument('--seed', type=int, required=True)
    parser.add_argument('--settings', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); args.out.mkdir(parents=True, exist_ok=True)
    def save(report):
        (args.out / 'selection.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    from tools.yunshan.flat_fallback import select_with_fallback
    report, _, _ = select_with_fallback(args.seed, lambda window, label, settings: sample_window(
        args.world, window, args.seed, settings), read_settings(args.settings), progress=save)
    print(json.dumps({'status': report['status'], 'selected': report['selected']}, ensure_ascii=False))
    return 0 if report['selected'] else 2


if __name__ == '__main__':
    raise SystemExit(main())
