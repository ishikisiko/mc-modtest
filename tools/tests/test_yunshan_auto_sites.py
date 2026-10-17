"""Siting refusal/determinism contracts; synthetic fixtures are labelled as tests."""
import copy
import tempfile
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from tools.combat_capture.session import SessionConfig, server_properties
from tools.yunshan.auto_sites import (fit_rejections, search_windows, select_site,
                                      validate_survey)
from tools.yunshan.fit_natural import UnsuitableTerrain
from tools.yunshan.natural_terrain import read_settings, sample_chunk, sample_window
from tools.tests import test_yunshan_natural_terrain as terrain_fixtures
from tools.tests.test_yunshan_natural_terrain import terrain_chunk, packed


def settings():
    cfg = read_settings()
    cfg.update(sample_stride=2, origin_stride=4, candidate_count=1, expanded_size=[32, 32], minimum_margin=4)
    cfg['automatic'].update(window_size=[16, 16], max_coarse_windows=1, max_expanded_surveys=1,
                            max_fit_attempts=3, max_attempts_per_area=3, shortlist_per_window=8)
    return cfg


CORE = {'minimum': [0, 62, 0], 'size': [4, 4, 4], 'ground_y': [68] * 16, 'water_surface': 68}


def survey(window, seed=9, height=63, water=None):
    x0, z0, x1, z1 = window; n = (x1-x0)*(z1-z0)
    return {'window': window, 'width': x1-x0, 'depth': z1-z0, 'ground_y': [height]*n,
            'surface_y': [max(height, water or height)]*n, 'water_y': [water]*n,
            'ground_palette': ['minecraft:grass_block'], 'ground_index': [0]*n,
            'artificial': [False]*n, 'hazard': [False]*n, 'surface_structure': [False]*n,
            'provenance': {'seed': seed, 'status': 'saved_anvil_snapshot',
                           'overworld_generator': 'minecraft:noise', 'region_sha256': {'fixture': 'a'*64},
                           'level_dat_sha256': 'b'*64}}


def valid_fit(data, candidate):
    return {'survey': SimpleNamespace(data=data), 'changed': [True]*len(data['ground_y']),
            'validation': {'passed': True, 'checks': {'water_contained': True, 'dry_exit': True}},
            'metrics': {'cut_blocks': 100, 'fill_blocks': 100, 'maximum_cut_or_fill': 2,
                        'modified_columns': len(data['ground_y'])}}


class AutomaticSitingTests(unittest.TestCase):
    def test_seed_is_forwarded_exactly_and_default_capture_is_unchanged(self):
        self.assertEqual(SessionConfig('test').world_seed, 20261002)
        for value in (0, -482113, -(1 << 63), (1 << 63)-1):
            self.assertEqual(server_properties(1, 2, 'fixture', world_seed=value)['level-seed'], str(value))
        for value in (True, '12', 1 << 63, -(1 << 63)-1):
            with self.assertRaises(ValueError): server_properties(1, 2, 'fixture', world_seed=value)

    def test_generated_search_is_bounded_and_handles_negative_centers(self):
        cfg = settings(); cfg['automatic'].update(center=[-100, -200], max_coarse_windows=5)
        windows = search_windows(cfg)
        self.assertEqual(len(windows), 5)
        self.assertEqual(windows[0]['window'], [-108, -208, -92, -192])
        self.assertEqual(windows, search_windows(copy.deepcopy(cfg)))
        for field in ('max_fit_attempts', 'max_coarse_windows'):
            bad = copy.deepcopy(cfg); bad['automatic'][field] = 0
            with self.assertRaises(ValueError): search_windows(bad)

    def test_selection_is_repeatable_and_never_uses_authored_coordinate_overrides(self):
        cfg = settings(); cfg['candidate_refinements'] = {'trap': {'origin': [99999, 99999]}}
        reads = []
        def provider(window, label, _):
            reads.append(label); return survey(window)
        first, _, _ = select_site(9, provider, cfg, core=CORE, fit_function=valid_fit)
        second, _, _ = select_site(9, provider, cfg, core=CORE, fit_function=valid_fit)
        self.assertEqual(first, second)
        self.assertEqual(first['status'], 'selected')
        self.assertFalse(first['manual_refinements_used'])
        self.assertEqual(first['fit_attempts'], 1)
        self.assertEqual(len(reads), 4)
        self.assertEqual(cfg['candidate_refinements']['trap']['origin'], [99999, 99999])

    def test_deep_ocean_high_plateau_and_rough_cliffs_refuse_without_full_fit(self):
        for kind in ('ocean', 'plateau', 'rough'):
            def provider(window, label, _, kind=kind):
                data = survey(window, height=20 if kind == 'ocean' else 150 if kind == 'plateau' else 63,
                              water=63 if kind == 'ocean' else None)
                if kind == 'rough':
                    data['ground_y'] = [63 + (k // 2 % 2)*55 for k in range(len(data['ground_y']))]
                return data
            with self.subTest(kind=kind), patch('tools.yunshan.fit_natural.plan_fit') as fitter:
                report, data, result = select_site(9, provider, settings(), core=CORE)
                fitter.assert_not_called()
                self.assertEqual(report['status'], 'no_suitable_site_within_budget')
                self.assertIsNone(data); self.assertIsNone(result)
                self.assertTrue(report['coarse_rejections'])

    def test_single_unsampled_hazard_or_building_in_full_write_mask_is_rejected(self):
        for field, code in (('hazard', 'surface_hazard'), ('artificial', 'existing_construction'),
                            ('surface_structure', 'surface_structure_reservation')):
            def provider(window, label, _, field=field):
                data = survey(window)
                # Outside the core footprint but inside this fixture's full fit write mask.
                if label.startswith('expanded'): data[field][1] = True
                return data
            report, _, _ = select_site(9, provider, settings(), core=CORE, fit_function=valid_fit)
            self.assertEqual(report['status'], 'no_suitable_site_within_budget')
            self.assertEqual(report['fit_attempts'], 3)
            self.assertEqual(report['reason_counts'][code], 3)
            self.assertEqual(report['attempts'][0]['reasons'][0]['stage'], 'full_write_mask')

    def test_unchanged_surrounding_structure_is_not_erased_or_a_false_conflict(self):
        data = survey([-8, -8, 8, 8]); data['surface_structure'][0] = True
        result = valid_fit(data, {}); result['changed'][0] = False
        self.assertEqual(fit_rejections(result, settings()['automatic']), [])

    def test_fit_failures_keep_reasons_and_try_next_candidate_with_finite_budget(self):
        count = 0
        def fitter(data, candidate):
            nonlocal count
            count += 1
            if count == 1: raise UnsuitableTerrain('road width enters retained water')
            result = valid_fit(data, candidate)
            if count == 2:
                result['validation'] = {'passed': False, 'checks': {'water_contained': False}}
            return result
        report, _, _ = select_site(9, lambda w, *_: survey(w), settings(), core=CORE, fit_function=fitter)
        self.assertEqual(report['status'], 'selected')
        self.assertEqual([a['status'] for a in report['attempts']], ['rejected', 'rejected', 'selected'])
        self.assertEqual(report['attempts'][0]['reasons'][0]['code'], 'terrain_fit_conflict')
        self.assertEqual(report['attempts'][1]['reasons'][0]['code'], 'water_contained')
        self.assertEqual(report['fit_attempts'], 3)

    def test_excessive_fill_cut_and_peak_are_explicit_hard_rejections(self):
        result = valid_fit(survey([-8, -8, 8, 8]), {})
        result['metrics'].update(cut_blocks=900001, maximum_cut_or_fill=49)
        codes = {reason['code'] for reason in fit_rejections(result, settings()['automatic'])}
        self.assertEqual(codes, {'maximum_total_earthwork', 'maximum_peak_cut_or_fill',
                                 'maximum_earthwork_per_modified_column'})

    def test_invalid_evidence_or_unexpected_fitter_errors_are_not_terrain_refusals(self):
        for mutation in ('seed', 'missing_flag', 'flat'):
            data = survey([-8, -8, 8, 8])
            if mutation == 'seed': data['provenance']['seed'] = 10
            if mutation == 'missing_flag': data.pop('hazard')
            if mutation == 'flat': data['provenance']['overworld_generator'] = 'minecraft:flat'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): validate_survey(data, 9)
        with self.assertRaisesRegex(ValueError, 'broken implementation'):
            select_site(9, lambda w, *_: survey(w), settings(), core=CORE,
                        fit_function=lambda *_: (_ for _ in ()).throw(ValueError('broken implementation')))

    def test_sampler_detects_lava_and_surface_structure_references(self):
        chunk = terrain_chunk()
        palette = chunk['sections'][0]['block_states']['palette']; palette.append({'Name': 'minecraft:lava'})
        cells = [1 if y <= 3 else (6 if y == 4 and x == 3 and z == 0 else 0)
                 for y in range(16) for z in range(16) for x in range(16)]
        chunk['sections'][0]['block_states']['data'] = packed(cells, 4)
        chunk.pop('Heightmaps')
        self.assertTrue(sample_chunk(chunk)[0][3]['hazard'])
        chunk['structures'] = {'References': {'minecraft:village_plains': [1]}}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); terrain_fixtures.NaturalTerrainTest().write_world(root, chunk)
            data = sample_window(root, [0, 0, 16, 16], 20261002)
            self.assertTrue(all(data['surface_structure']))
            self.assertTrue(data['hazard'][3])
            chunk['structures'] = {'References': {'minecraft:mineshaft': [1]}}
            terrain_fixtures.NaturalTerrainTest().write_world(root, chunk)
            self.assertFalse(any(sample_window(root, [0, 0, 16, 16], 20261002)['surface_structure']))


if __name__ == '__main__':
    unittest.main()
