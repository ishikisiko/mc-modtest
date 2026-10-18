#!/usr/bin/env python3
"""Modal-only, independently generated multi-seed automatic siting evidence."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.yunshan.flat_fallback import select_with_fallback as select_site
from tools.yunshan.natural_terrain import read_settings
from tools.yunshan.natural_evidence import NaturalEvidence, digest


class AutomaticEvidence(NaturalEvidence):
    def __init__(self, out, seed):
        super().__init__(out, seed=seed)
        # This collector emits selection.json, not the legacy top3 manifest.
        self.report.pop('candidate_manifest', None)
        self.report.update(kind='yunshan_automatic_site', scope='bounded_multi_seed_search',
                           selected=['automatic_actual_terrain_search'], completed=[],
                           not_selected=['authored_coordinate_refinements'],
                           method='actual normal-world snapshots and bounded automatic selection; '
                                  'placement and mapped W only for explicitly completed stages')
        self.save()

    def automatic(self, settings, place=False):
        from tools.yunshan.fit_natural import compile_fit
        self.start()
        def progress(report):
            (self.out/'selection.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
            self.report['selection_progress'] = {key: report[key] for key in
                ('status', 'coarse_candidates', 'coarse_rejections', 'expanded_surveys', 'fit_attempts')}
            self.report['selection_progress']['variant_phase']=report.get('variant_phase','terraced')
            self.save()
            print(json.dumps({'seed': self.seed, **self.report['selection_progress']}, ensure_ascii=False), flush=True)
        with self.phase('automatic_search'):
            selection, survey, fit = select_site(self.seed, self.survey, settings, progress=progress)
        self.report['selection'] = {'file': 'selection.json', 'sha256': digest(self.out/'selection.json'),
                                    'status': selection['status'], 'candidate': selection['selected']}
        self.report['source_variant']=selection.get('selected_variant','terraced')
        if selection.get('variant_phase')=='flat_fallback':
            self.check('flat_fallback_follows_completed_primary_refusal',selection['primary']['selected'] is None
                       and selection['primary']['status']=='no_suitable_site_within_budget',selection['trigger'])
        self.report['completed'].append('automatic_actual_terrain_search')
        self.check('no_manual_coordinate_refinements', selection['manual_refinements_used'] is False, selection['algorithm'])
        if selection['selected'] is None:
            self.check('unsuitable_search_has_no_placement', self.report.get('terrain_commands_applied', 0) == 0
                       and not self.report.get('placed_tiles'), selection['status'])
            self.report['outcome'] = 'no_suitable_site_within_budget'
            self.report['status'] = 'pass'; self.stop(); self.save()
            return False
        candidate = selection['selected']
        (self.out/'selected_survey.json').write_text(json.dumps(survey,ensure_ascii=False)+'\n')
        self.report['candidates'] = [candidate]; self.report['selected_id'] = candidate['id']
        self.check('selected_full_fit_passed', fit['validation']['passed'], fit['validation'])
        self.report['outcome'] = 'site_selected'
        if not place:
            self.report['not_selected'].extend(['placement', 'mapped_traversal', 'world_export'])
            self.report['status'] = 'pass'; self.stop(); self.save()
            return True
        self.report['selected'].extend(['before_frames', 'sparse_natural_fit', 'support_shore_boundary_readback',
            'mapped_outgoing_paths', 'mapped_rotated_core_entry', 'matched_after_frames', 'playable_world_export'])
        directory = self.out/'fit'
        with self.phase('compile_automatically_selected_site'):
            manifest = compile_fit(survey, candidate, directory,config=fit['cfg'])
        self.check('compiled_fit_matches_selected_plan', manifest['validation']['passed']
                   and manifest['metrics'] == fit['metrics'], manifest['validation'])
        self.report['fit_manifest'] = {'path': 'fit/fit.json', 'sha256': digest(directory/'fit.json')}
        cameras = manifest.get('matched_cameras', manifest['fixture_cameras'])
        self.capture(candidate['id'], cameras, 'before')
        self.report['completed'].append('before_frames')
        self.apply_fit(manifest, directory); self.verify_fit()
        if manifest.get('source_variant')=='flat_fallback_v1':
            self.report['selected'].append('mapped_all_four_flat_entries')
            self.check('flat_streets_and_approaches_share_one_height',
                       manifest['street_height_range']==[manifest['flat_ground_y']]*2
                       and manifest['building_approach_heights']==[manifest['flat_ground_y']]*4,
                       {'street_range':manifest['street_height_range'],'approaches':manifest['building_approach_heights']})
            for installation in self.spec['buildings'][1:]:
                data=json.loads((ROOT/'blueprints/yunshan'/installation['source']).read_text())
                data['_installation']=installation
                self.building_walk(data,next(trial for trial in data['walk_trials'] if trial['name']=='main_entry'))
            self.report['completed'].append('mapped_all_four_flat_entries');self.save()
        self.capture(candidate['id'], cameras, 'after')
        self.report['completed'].append('matched_after_frames')
        if manifest.get('source_variant')=='flat_fallback_v1':
            self.finish_world(level_name='云山 · 平地后备预览',folder_name='Yunshan Flat Fallback Preview',
                              filename='yunshan_flat_fallback_world.zip')
        else:self.finish_world()
        self.report['outcome'] = 'site_placed_and_walked'; self.report['status'] = 'pass'; self.save()
        return True


def run_matrix(out, seeds, settings, *, place_first=True, require_flat=False):
    if ROOT != Path('/workspace/repo'):
        raise RuntimeError('Actual multi-seed tests are restricted to Modal /workspace/repo')
    if not seeds or len(seeds) != len(set(seeds)) or len(seeds) > 8:
        raise ValueError('Provide 1..8 distinct seeds')
    if any(type(seed) is not int or not -(1 << 63) <= seed < (1 << 63) for seed in seeds):
        raise ValueError('Each seed must be a signed 64-bit integer')
    if require_flat and not place_first:raise ValueError('Required flat fallback must include actual placement')
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    report = {'kind': 'yunshan_automatic_multi_seed', 'status': 'running', 'seeds': seeds,
              'runs': [], 'placement_requested': place_first, 'placement_completed': False,'require_flat_fallback':require_flat,
              'scope': 'automatic tool pipeline; not a registered runtime world-generation structure'}
    def save():
        (out/'matrix.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    save()
    try:
        for seed in seeds:
            evidence = AutomaticEvidence(out/f'seed-{seed}', seed)
            began = time.monotonic()
            try:
                selected = evidence.automatic(settings, place=place_first and not report['placement_completed'])
                report['placement_completed'] |= evidence.report.get('outcome') == 'site_placed_and_walked'
                report['runs'].append({'seed': seed, 'status': evidence.report['status'], 'selected': selected,
                    'outcome': evidence.report['outcome'], 'candidate': evidence.report.get('selection', {}).get('candidate'),
                    'variant': evidence.report.get('source_variant'),
                    'wall_s': round(time.monotonic()-began, 3), 'checks': len(evidence.report['checks']),
                    'evidence': f'seed-{seed}/evidence.json', 'selection': f'seed-{seed}/selection.json'})
                save()
            except BaseException as error:
                evidence.report['status'] = 'fail'; evidence.report['failures'].append(repr(error)); evidence.save()
                raise
            finally:
                if evidence.s:
                    try: evidence.s.game.xdo('keyup', 'w')
                    finally: evidence.stop()
                evidence.save()
        if place_first and not report['placement_completed']:
            raise AssertionError('No seed produced a site suitable for the requested mapped placement check')
        if require_flat and not any(run.get('variant')=='flat_fallback_v1' and run['outcome']=='site_placed_and_walked' for run in report['runs']):
            raise AssertionError('Required flat backup was not actually selected, placed and walked')
        report['selected_seeds'] = [run['seed'] for run in report['runs'] if run['selected']]
        report['refused_seeds'] = [run['seed'] for run in report['runs'] if not run['selected']]
        report['status'] = 'pass'; save()
    except BaseException as error:
        report['status'] = 'fail'; report['failure'] = repr(error); save(); raise
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT/'reports/yunshan-auto-sites')
    parser.add_argument('--seeds', type=int, nargs='+', help='Defaults to authored independent validation seeds')
    parser.add_argument('--settings', type=Path)
    parser.add_argument('--selection-only', action='store_true')
    parser.add_argument('--require-flat-fallback',action='store_true',help='Require a real primary refusal followed by a placed flat backup')
    args = parser.parse_args(); settings = read_settings(args.settings)
    print(json.dumps(run_matrix(args.out, args.seeds or settings['automatic']['test_seeds'], settings,
                                place_first=not args.selection_only,require_flat=args.require_flat_fallback), ensure_ascii=False))


if __name__ == '__main__':
    main()
