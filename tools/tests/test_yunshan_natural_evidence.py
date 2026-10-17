"""Bounded real-world capture contracts; no local Minecraft or cloud launch."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch
import zipfile

from tools.modal_runner.remote import commands_for
from tools.yunshan.natural_evidence import (
    NaturalEvidence, digest, generation_batches, operation_bounds,
    probe_groups, snapshot_regions, terrain_batches, refine_candidate,
)
from tools.yunshan.district_game_evidence import package_world
from tools.yunshan.game_export import write_typed
import tools.json_to_nbt as nbt


class NaturalBatchTests(unittest.TestCase):
    def test_chunk_batches_cover_real_windows_once_with_bounded_tickets(self):
        for window in ([-928, -192, -608, 192], [-257, -385, 255, 223], [16, 16, 17, 17]):
            seen = set()
            for x0, z0, x1, z1 in generation_batches(window):
                chunks = {(x, z) for x in range(x0//16, x1//16+1) for z in range(z0//16, z1//16+1)}
                self.assertLessEqual(len(chunks), 256)
                self.assertFalse(seen & chunks); seen |= chunks
            expected = {(x, z) for x in range(window[0]//16, (window[2]-1)//16+1)
                        for z in range(window[1]//16, (window[3]-1)//16+1)}
            self.assertEqual(seen, expected)

    def test_invalid_window_cannot_trigger_unbounded_generation(self):
        for window in ([0, 0, 0, 10], [0, 0, 1025, 20], [False, 0, 10, 10]):
            with self.assertRaises(ValueError): list(generation_batches(window))

    def test_terrain_commands_keep_order_and_respect_chunk_and_fill_limits(self):
        operations = [{'command': f'fill {x} 60 0 {x} 65 0 minecraft:dirt', 'probe': {}}
                      for x in list(range(520))+[2000, 2001]]
        result = list(terrain_batches(operations))
        self.assertEqual([item for _, _, batch in result for item in batch], operations)
        for low, high, batch in result:
            self.assertLessEqual(len(batch), 512)
            self.assertLessEqual((high[0]//16-low[0]//16+1)*(high[2]//16-low[2]//16+1), 256)
        self.assertEqual(operation_bounds('setblock -3 62 -8 minecraft:grass_block'), ([-3, 62, -8], [-3, 62, -8]))
        for command in ('op someone', 'fill 0 -65 0 0 0 0 minecraft:dirt',
                        'fill 0 0 0 40 40 40 minecraft:dirt', 'setblock 0 0 0 '+'x'*1200):
            with self.assertRaises(ValueError): operation_bounds(command)

    def test_category_contract_accepts_grouped_and_flat_probes(self):
        probe = {'pos': [1, 64, 3], 'state': 'minecraft:water'}
        self.assertEqual(probe_groups({'shore': [probe]}), [('shore', probe)])
        self.assertEqual(probe_groups([{**probe, 'category': 'shore'}])[0][0], 'shore')


class NaturalSnapshotTests(unittest.TestCase):
    def test_natural_zip_uses_distinct_folder_and_rejects_nested_export_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root/'world'; world.mkdir()
            write_typed(world/'level.dat', '', nbt.Compound({'Data': nbt.Compound({'LevelName': nbt.String('fixture')})}))
            result = package_world(world, root/'natural.zip', level_name='云山自然', folder_name='Yunshan Natural Preview')
            self.assertEqual(result['folder_name'], 'Yunshan Natural Preview')
            with zipfile.ZipFile(root/'natural.zip') as archive:
                self.assertEqual(archive.namelist(), ['Yunshan Natural Preview/level.dat'])
            for name in ('../escape', '..\\escape', '.', ''):
                with self.assertRaisesRegex(ValueError, 'single folder'):
                    package_world(world, root/'bad.zip', folder_name=name)

    def test_actual_region_snapshot_keeps_only_intersecting_files_and_exact_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root/'world'; (world/'region').mkdir(parents=True)
            (world/'level.dat').write_bytes(b'actual typed world metadata')
            expected = {'r.-1.-1.mca', 'r.-1.0.mca', 'r.0.-1.mca', 'r.0.0.mca'}
            for name in expected | {'r.4.4.mca'}: (world/'region'/name).write_bytes(name.encode())
            hashes = snapshot_regions(world, [-1, -1, 1, 1], root/'snapshot')
            self.assertEqual(set(hashes), {'level.dat'} | {'region/'+name for name in expected})
            for relative, metadata in hashes.items():
                self.assertEqual((root/'snapshot'/relative).read_bytes(), (world/relative).read_bytes())
                self.assertEqual(metadata['sha256'], digest(world/relative))
            (world/'region/r.0.0.mca').write_bytes(b'later fit mutation')
            self.assertEqual((root/'snapshot/region/r.0.0.mca').read_bytes(), b'r.0.0.mca')

    def test_missing_real_region_is_not_replaced_with_synthetic_terrain(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root/'level.dat').write_bytes(b'world')
            with self.assertRaisesRegex(FileNotFoundError, 'Actual saved survey file missing'):
                snapshot_regions(root, [0, 0, 32, 32], root/'snapshot')


class NaturalLifecycleTests(unittest.TestCase):
    def test_refined_scores_reorder_selection_without_repeating_survey_or_losing_failed_before(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); evidence = NaturalEvidence(root)
            candidates = [{'id': name, 'origin': [index*768, 0], 'size': [210, 265],
                           'rotation_quarters': 0, 'water_surface': 63, 'eligible': True,
                           'score': index+1, 'rank': index+1, 'metrics': {'original': name}}
                          for index, name in enumerate(('a', 'b', 'c'))]
            settings = {'seed': 20261002, 'candidate_refinements': {'c': {
                'origin': [1536, 16], 'rotation_quarters': 0, 'why': 'use sampled lower earthwork position'}}}
            evaluated = {**candidates[2], 'id': 'site-0-evaluated', 'origin': [1536, 16],
                         'score': .5, 'rank': 1, 'metrics': {'recomputed': True}}
            cameras = [{'name': 'matched', 'pos': [900, 120, 100], 'yaw': 90, 'pitch': 20}]
            def preview(survey, candidate, out):
                if candidate['id'] == 'a': raise ValueError('outgoing road has no acceptable route')
                return {'validation': {'passed': True}, 'fixture_cameras': cameras}
            def compile_site(survey, candidate, out):
                self.assertEqual(candidate['id'], 'c')
                result = {'validation': {'passed': True}, 'fixture_cameras': cameras}
                out.mkdir(parents=True); (out/'fit.json').write_text(json.dumps(result))
                return result
            def capture(candidate, camera_array, stage):
                evidence.report['screenshots'].extend({'candidate': candidate, 'stage': stage,
                    'camera': camera, 'file': candidate+'-'+stage+'.png'} for camera in camera_array)
            with (patch('tools.yunshan.natural_terrain.read_settings', return_value=settings),
                  patch('tools.yunshan.natural_terrain.coarse_windows', return_value=[{'id': 'actual', 'window': [0, 0, 32, 32]}]),
                  patch('tools.yunshan.natural_terrain.rank_sites', return_value=candidates),
                  patch('tools.yunshan.natural_terrain.expanded_window', return_value=[0, 0, 32, 32]),
                  patch('tools.yunshan.natural_terrain.evaluate_site', return_value=evaluated) as evaluate,
                  patch('tools.yunshan.fit_natural.preview_fit', side_effect=preview),
                  patch('tools.yunshan.fit_natural.compile_fit', side_effect=compile_site),
                  patch.object(evidence, 'start'), patch.object(evidence, 'survey', return_value={}) as survey,
                  patch.object(evidence, 'capture', side_effect=capture), patch.object(evidence, 'apply_fit'),
                  patch.object(evidence, 'verify_fit'), patch.object(evidence, 'finish_world')):
                evidence.run()
            self.assertEqual(evidence.report['selected_id'], 'c')
            self.assertEqual(evidence.report['selection_order'], ['c', 'a', 'b'])
            self.assertEqual(survey.call_count, 4, 'One coarse and three expanded surveys; refinement must reuse its survey')
            evaluate.assert_called_once_with({}, [1536, 16], 0, settings=settings)
            refined = evidence.report['candidates'][2]
            self.assertEqual(refined['rank'], 3)
            self.assertEqual(refined['refined_from']['score'], 3)
            self.assertEqual(refined['refined_from']['origin'], [1536, 0])
            self.assertEqual(refined['score'], .5)
            self.assertEqual(refined['metrics'], {'recomputed': True})
            self.assertFalse(candidates[0]['fit_validation']['passed'])
            self.assertIn('no acceptable route', candidates[0]['fit_validation']['reason'])
            self.assertTrue((root/'sites/a/fit-preview.json').is_file())
            self.assertEqual({shot['candidate'] for shot in evidence.report['screenshots'] if shot['stage'] == 'before'}, {'a', 'b', 'c'})
            self.assertEqual({shot['candidate'] for shot in evidence.report['screenshots'] if shot['stage'] == 'after'}, {'c'})
            self.assertEqual(evidence.report['fit_manifest']['path'], 'fit/fit.json')

    def test_refinement_eligibility_is_fresh_and_missing_actual_coverage_remains_failure(self):
        original = {'id': 'center', 'origin': [-96, -112], 'rotation_quarters': 0,
                    'score': 9, 'eligible': True, 'metrics': {'old': 1}}
        settings = {'candidate_refinements': {'center': {
            'origin': [-96, -96], 'rotation_quarters': 0, 'reason': 'real survey refinement'}}}
        evaluated = {**original, 'id': 'new-point', 'origin': [-96, -96],
                     'score': 11, 'eligible': False, 'metrics': {'new': 1}, 'limitations': ['water mismatch']}
        with patch('tools.yunshan.natural_terrain.evaluate_site', return_value=evaluated):
            refined = refine_candidate({'actual': True}, original, settings)
        self.assertFalse(refined['eligible'])
        self.assertEqual(refined['limitations'], ['water mismatch'])
        self.assertEqual(original['origin'], [-96, -112])
        with patch('tools.yunshan.natural_terrain.evaluate_site', side_effect=ValueError('missing surveyed footprint')):
            with self.assertRaisesRegex(ValueError, 'missing surveyed footprint'):
                refine_candidate({}, original, settings)

    def test_failed_sparse_placement_always_unfreezes_before_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = NaturalEvidence(Path(directory)); commands = []
            evidence.rc = lambda command: commands.append(command)
            with patch.object(evidence, '_apply_fit', side_effect=ValueError('fixture failure')):
                with self.assertRaisesRegex(ValueError, 'fixture failure'): evidence.apply_fit({}, Path(directory))
            self.assertEqual(commands, ['tick freeze', 'tick unfreeze'])

    def test_local_start_is_refused_before_any_game_process(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = NaturalEvidence(Path(directory))
            with (patch('tools.yunshan.natural_evidence.ROOT', Path('/local-only-test')),
                    patch('tools.yunshan.natural_evidence.cs.start') as start):
                with self.assertRaisesRegex(RuntimeError, 'Modal disposable'): evidence.start()
                start.assert_not_called()

    def test_cloud_start_requests_real_normal_world_and_keeps_fixed_render_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = NaturalEvidence(Path(directory))
            with (patch('tools.yunshan.natural_evidence.ROOT', Path('/workspace/repo')),
                    patch('tools.yunshan.natural_evidence.cs.merge_options'),
                    patch('tools.yunshan.natural_evidence.cs.start', side_effect=RuntimeError('probe stop')) as start):
                with self.assertRaisesRegex(RuntimeError, 'probe stop'): evidence.start()
            config = start.call_args.args[0]
            self.assertTrue(config.natural_terrain)
            self.assertEqual(config.view_distance, 16)
            self.assertFalse(config.reuse_world)

    def test_rcon_logs_stream_without_unbounded_report_copy_and_rejects_oversized_packets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); evidence = NaturalEvidence(root)
            evidence.s = MagicMock()
            client = evidence.s.rcon.return_value.__enter__.return_value
            def reply(command):
                self.assertEqual(client.timeout, 45)
                client.sock.settimeout.assert_called_once_with(45)
                return 'actual server reply'
            client.cmd.side_effect = reply
            self.assertEqual(evidence.rc('seed'), 'actual server reply')
            self.assertEqual(json.loads((root/'commands.jsonl').read_text())['command'], 'seed')
            self.assertEqual(evidence.report['command_count'], 1)
            self.assertEqual(evidence.report['command_attempt_count'], 1)
            self.assertEqual(evidence.report['command_success_count'], 1)
            evidence.s.rcon.assert_called_once_with(retries=1)
            self.assertEqual(evidence.report['commands'], [])
            with self.assertRaisesRegex(ValueError, '1200-byte'): evidence.rc('x'*1201)
            self.assertEqual(evidence.report['command_attempt_count'], 1)

    def test_timed_out_edit_is_logged_once_and_never_replayed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); evidence = NaturalEvidence(root); evidence.s = MagicMock()
            client = evidence.s.rcon.return_value.__enter__.return_value
            client.cmd.side_effect = TimeoutError('server reply timed out')
            command = 'forceload add -928 -192 -673 63'
            with self.assertRaisesRegex(TimeoutError, 'server reply timed out'):
                evidence.rc(command)
            evidence.s.rcon.assert_called_once_with(retries=1)
            client.cmd.assert_called_once_with(command)
            client.sock.settimeout.assert_called_once_with(45)
            self.assertEqual(client.timeout, 45)
            record = json.loads((root/'commands.jsonl').read_text())
            self.assertEqual(record['command'], command)
            self.assertIn('server reply timed out', record['error'])
            self.assertTrue(record['outcome_unknown'])
            self.assertEqual(record['attempt'], 1)
            self.assertEqual(evidence.report['command_count'], 1)
            self.assertEqual(evidence.report['command_attempt_count'], 1)
            self.assertEqual(evidence.report['command_failure_count'], 1)
            self.assertEqual(evidence.report['command_success_count'], 0)

    def test_natural_preset_excludes_flat_game_capture_and_full_release(self):
        commands = commands_for('yunshan-natural')
        self.assertEqual(commands[-1], ['python3', 'tools/yunshan/natural_evidence.py'])
        gradle = commands[1]
        self.assertIn('jar', gradle); self.assertIn('-x', gradle); self.assertIn('generateAllStructures', gradle)
        joined = ' '.join(' '.join(command) for command in commands)
        for excluded in ('release_gate', 'run_chunky_acceptance', 'district_game_evidence.py', 'runClient'):
            self.assertNotIn(excluded, joined)


if __name__ == '__main__': unittest.main()
