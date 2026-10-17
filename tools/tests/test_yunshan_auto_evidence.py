"""The automatic collector must fail closed before world writes."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.yunshan.auto_site_evidence import AutomaticEvidence, run_matrix
from tools.modal_runner.remote import commands_for


class AutomaticEvidenceTests(unittest.TestCase):
    def test_rejected_search_cannot_compile_or_apply_even_when_placement_requested(self):
        with tempfile.TemporaryDirectory() as temporary:
            evidence = AutomaticEvidence(Path(temporary), -482113)
            report = {'status': 'no_suitable_site_within_budget', 'selected': None,
                      'algorithm': 'fixture', 'manual_refinements_used': False, 'coarse_candidates': 4,
                      'coarse_rejections': {'deep_water_fraction': 4}, 'expanded_surveys': 0, 'fit_attempts': 0}
            def reject(*args, **kwargs):
                kwargs['progress'](report); return report, None, None
            with (patch.object(evidence, 'start'), patch.object(evidence, 'stop'),
                  patch('tools.yunshan.auto_site_evidence.select_site', side_effect=reject),
                  patch('tools.yunshan.fit_natural.compile_fit') as compile_call,
                  patch.object(evidence, 'apply_fit') as apply, patch.object(evidence, 'verify_fit') as verify):
                self.assertFalse(evidence.automatic({}, place=True))
                compile_call.assert_not_called(); apply.assert_not_called(); verify.assert_not_called()
            self.assertEqual(evidence.report['status'], 'pass')
            self.assertEqual(evidence.report['outcome'], 'no_suitable_site_within_budget')
            self.assertNotIn('playable_world_export', evidence.report['completed'])

    def test_arbitrary_seed_reaches_real_session_config_before_start(self):
        with tempfile.TemporaryDirectory() as temporary:
            evidence = AutomaticEvidence(Path(temporary), -482113)
            with (patch('tools.yunshan.natural_evidence.ROOT', Path('/workspace/repo')),
                  patch('tools.yunshan.natural_evidence.cs.merge_options'),
                  patch('tools.yunshan.natural_evidence.cs.start', side_effect=RuntimeError('stop fixture')) as start):
                with self.assertRaisesRegex(RuntimeError, 'stop fixture'): evidence.start()
            cfg = start.call_args.args[0]
            self.assertEqual(cfg.world_seed, -482113)
            self.assertFalse(cfg.reuse_world)
            self.assertTrue(cfg.natural_terrain)

    def test_local_matrix_refuses_before_start_and_preset_uses_new_collector(self):
        with tempfile.TemporaryDirectory() as temporary:
            with (patch('tools.yunshan.auto_site_evidence.ROOT', Path('/local/repo')),
                  patch('tools.yunshan.auto_site_evidence.AutomaticEvidence') as factory):
                with self.assertRaisesRegex(RuntimeError, 'Modal'): run_matrix(Path(temporary), [0], {})
                factory.assert_not_called()
        self.assertEqual(commands_for('yunshan-auto-sites')[-1], ['python3', 'tools/yunshan/auto_site_evidence.py'])

    def test_matrix_refuses_invalid_seed_set_before_any_session(self):
        with tempfile.TemporaryDirectory() as temporary:
            with (patch('tools.yunshan.auto_site_evidence.ROOT', Path('/workspace/repo')),
                  patch('tools.yunshan.auto_site_evidence.AutomaticEvidence') as factory):
                for seeds in ([], [1, 1], [True], [1 << 63], list(range(9))):
                    with self.subTest(seeds=seeds), self.assertRaises(ValueError):
                        run_matrix(Path(temporary), seeds, {})
                factory.assert_not_called()


if __name__ == '__main__':
    unittest.main()
