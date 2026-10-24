"""Driver offline regression: isolation, real UI geometry, route, lifecycle and failure accounting."""
import json
import ast
import base64
import gzip
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.qianzhen_opening_run import (build_argv, copy_fixture, tree_hashes, validate_identity,
                                        remote_bootstrap, single_flush, verify_owned_session)
from tools.qianzhen_opening_flow import (selected_scenarios, gui_geometry, awakening_controls,
    fate_triple, bounded_path, meditation_button, entrance_receipt, build_scene, FACT_ACTIONS)
from tools.qianzhen_opening_evidence import readonly_rcon, fingerprint


class DriverTests(unittest.TestCase):
    def test_fixed_commit_and_unique_run_are_mandatory(self):
        validate_identity('a' * 40, 'opening-fixed-123')
        for commit, run in [('main', 'opening-123'), ('a' * 40, '../oldworld'), ('a' * 40, 'short')]:
            with self.assertRaises(ValueError):
                validate_identity(commit, run)

    def test_all_eleven_are_selectable_without_claiming_implemented(self):
        from tools.qianzhen_opening_evidence import fixture
        ids = [s['id'] for s in fixture()['stages']]
        self.assertEqual(11, len(selected_scenarios(ids)))
        for bad in [[], ['fake'], ['natural_spawn', 'natural_spawn']]:
            with self.assertRaises(ValueError):
                selected_scenarios(bad)

    def test_read_only_predicates_cannot_run_or_store(self):
        for command in ['execute if block 1 2 -3 minecraft:air',
                        'execute if block 1 2 3 minecraft:oak_door[open=true]',
                        'execute if block 1 2 3 minecraft:air if block 1 3 3 minecraft:air',
                        'execute as OpeningA at @s anchored eyes positioned ^ ^ ^ if entity @s[distance=1.615..1.625]',
                        'execute if entity OpeningA[gamemode=survival]', 'myvillage cultivation fate status OpeningA']:
            self.assertTrue(readonly_rcon(command), command)
        for command in ['execute if block 1 2 3 minecraft:air run give OpeningA minecraft:bread',
                        'execute store result score OpeningA x run list', 'execute as OpeningA run qianzhen recruitment track',
                        'myvillage cultivation fate confirm OpeningA 7', 'execute if block 1 2 3 minecraft:air\nstop']:
            self.assertFalse(readonly_rcon(command), command)

    def test_build_is_focused_and_includes_runtime_codec_client_scene_entrance(self):
        argv = build_argv()
        self.assertIn('test', argv)
        self.assertIn('jar', argv)
        self.assertIn('com.example.myvillage.story.*', argv)
        self.assertIn('com.example.myvillage.client.story.*', argv)
        self.assertFalse(any('release' in s or 'Chunky' in s for s in argv))

    def test_bootstrap_never_reuses_world_or_stops_vm(self):
        value = dict(run_id='opening-unit-123', commit='b' * 40, source_repo='/content/qianzhen',
                     dependency_root='/content/qianzhen', pal_sha256='c' * 64, timeout=600, scenarios=['natural_spawn'])
        code = remote_bootstrap(value)
        compile(code, '<bootstrap>', 'exec')
        nested = next(node for node in ast.walk(ast.parse(code)) if isinstance(node, ast.Call)
                      and isinstance(node.func, ast.Attribute) and node.func.attr == 'write_text')
        payload = ast.literal_eval(nested.args[0])
        compile(payload, '<remote-bootstrap>', 'exec')
        self.assertIn('exist_ok=False', code)
        self.assertIn('--no-hardlinks', code)
        self.assertIn("'/content/qianzhen-gradle-cache'", payload)
        self.assertIn('shutil.copy2(pal,r/pal.name)', payload)
        self.assertNotIn("/'libs'/", payload)
        self.assertNotIn('rmtree', code)
        self.assertNotIn('colab stop', code)
        self.assertNotIn('deploy', code)

    def test_gui_scale_uses_actual_framebuffer_and_unicode(self):
        self.assertEqual((2, (480, 270)), gui_geometry((960, 540), {'guiScale': '0'}))
        self.assertEqual((1, (960, 540)), gui_geometry((960, 540), {'guiScale': '1'}))
        self.assertEqual((3, (640, 360)), gui_geometry((1920, 1080), {'guiScale': '3'}))
        self.assertEqual((4, (480, 270)), gui_geometry((1920, 1080), {'guiScale': '3', 'forceUnicodeFont': 'true'}))

    def test_awakening_only_newest_correct_size_session_rectangles(self):
        old = 'AWAKENING_UI gui=480x270 step=root revision=1 selection=0 confirming=false confirmed=false token=1\nAWAKENING_UI control=continue x=10 y=10 w=50 h=18\n'
        new = 'AWAKENING_UI gui=480x270 step=fates revision=2 selection=3 confirming=false confirmed=false token=9\nAWAKENING_UI control=confirm x=100 y=200 w=80 h=18\n'
        state, controls = awakening_controls(old + new, (960, 540), {'guiScale': '0'})
        self.assertEqual(9, state['token'])
        self.assertEqual({'confirm': (280, 418)}, controls)
        with self.assertRaises(ValueError):
            awakening_controls(new, (1920, 1080), {'guiScale': '3'})
        with self.assertRaises(ValueError):
            awakening_controls(new.replace('x=100', 'x=999'), (960, 540), {})

    def test_fates_choose_legal_existing_three_without_reroll(self):
        ids = list('abcdef')
        definitions = {k: {'cost': 1, 'group': k} for k in ids}
        definitions['b']['group'] = 'a'
        self.assertEqual((0, 2, 3), fate_triple({'candidates': ids, 'budget': 3}, definitions))
        for row in definitions.values():
            row['cost'] = 4
        with self.assertRaises(ValueError):
            fate_triple({'candidates': ids, 'budget': 3}, definitions)

    def test_real_pixel_button_recognizer_rejects_missing_and_uses_current_scale(self):
        from PIL import Image, ImageDraw
        image = Image.new('RGB', (960, 540), 'black')
        with self.assertRaises(ValueError):
            meditation_button(image, 2)
        draw = ImageDraw.Draw(image)
        draw.rectangle((170, 440, 350, 475), outline=(156, 121, 60), width=1)
        draw.rectangle((360, 440, 540, 475), outline=(156, 121, 60), width=1)
        self.assertEqual((260, 458), meditation_button(image, 2))

    def test_route_cannot_teleport_or_exceed_search_budget(self):
        path = bounded_path((0, 0, 0), (2, 0, 0), lambda p: [(p[0] + 1, 0, 0)])
        self.assertEqual([(0, 0, 0), (1, 0, 0), (2, 0, 0)], path)
        with self.assertRaises(ValueError):
            bounded_path((0, 0, 0), (3, 0, 0), lambda p: [(p[0] + 3, 0, 0)])
        with self.assertRaises(TimeoutError):
            bounded_path((0, 0, 0), (10, 0, 0), lambda p: [(p[0] + 1, 0, 0)], budget=3)

    def test_fixture_copy_is_hash_bound_and_original_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            original = root / 'old'
            (original / 'playerdata').mkdir(parents=True)
            (original / 'level.dat').write_bytes(b'actual-old-level')
            (original / 'playerdata/person.dat').write_bytes(b'actual-old-accepted')
            before = tree_hashes(original)
            spec = dict(world=str(original), files=before, sha256=fingerprint(before), quiescent=True,
                        provenance={'source_commit': 'd' * 40, 'kind': 'real_saved_world'})
            receipt = copy_fixture(spec, root / 'copy')
            self.assertTrue(receipt['original_unchanged'])
            self.assertEqual(before, tree_hashes(original))
            self.assertEqual(before, tree_hashes(root / 'copy'))
            with self.assertRaises(ValueError):
                copy_fixture(spec, root / 'copy')
            spec['sha256'] = '0' * 64
            with self.assertRaises(ValueError):
                copy_fixture(spec, root / 'other')
            self.assertFalse((root / 'other').exists())

    def test_fake_legacy_fixture_and_symlinks_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'old').mkdir()
            (root / 'old/level.dat').write_text('x')
            with self.assertRaises(ValueError):
                copy_fixture({'world': str(root / 'old')}, root / 'new')
            (root / 'old/leak').symlink_to(root / 'old/level.dat')
            with self.assertRaises(ValueError):
                tree_hashes(root / 'old')

    def test_stop_rejects_other_session_before_killing(self):
        with self.assertRaises(ValueError):
            verify_owned_session({'session_id': 'shared-old', 'procs': {}}, 'new-run', Path('/content/new-run/repo'))

    def test_flush_timeout_is_sent_once_and_failure_retained(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            log = root / 'run-acceptance/logs/latest.log'
            log.parent.mkdir(parents=True)
            log.write_text('old All dimensions are saved\n')
            with patch('tools.qianzhen.story_lifecycle.lifecycle_command', side_effect=TimeoutError('indeterminate')) as send:
                with self.assertRaises(TimeoutError):
                    single_flush({}, root, root)
                self.assertEqual(1, send.call_count)
            receipt = json.loads((root / 'flush.json').read_text())
            self.assertEqual('failed', receipt['status'])
            self.assertEqual(1, receipt['requests'])

    def test_entrance_receipt_refuses_noncomplete_and_binds_deployment(self):
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder)
            (world / 'data').mkdir()
            (world / 'data/myvillage_qianzhen_entrance.dat').write_bytes(b'not parsed in mock')
            candidate = Mock()
            candidate.layout = {field: [] for field in ('id', 'version', 'sites', 'bounds', 'positions', 'targets', 'roads', 'facilities')}
            receipt = {'phase': 'COMPLETE', 'rules': json.dumps({'max_route_length': 3}),
                       'plan': {'origin': [100, 70, -50], 'route': [8, 9, 9, 9]}, 'notice': [8, 71, 11]}
            deployed = {'schema': 2, 'phase': 'COMPLETE', 'geometry': 'gzip:' + base64.b64encode(gzip.compress(json.dumps(candidate.layout).encode())).decode()}
            with patch('tools.buildgen.nbtread.read_gzipped_nbt', return_value=('', {'data': receipt})), \
                    patch('tools.qianzhen.story_deployment.saved_deployment', return_value=deployed) as verify:
                value = entrance_receipt(world, candidate)
            self.assertEqual([(8, 71, 9), (9, 71, 9)], value['route'])
            verify.assert_called_once_with(world, candidate.layout, [100, 70, -50])
            receipt['phase'] = 'UNAVAILABLE'
            with patch('tools.buildgen.nbtread.read_gzipped_nbt', return_value=('', {'data': receipt})):
                with self.assertRaises(ValueError):
                    entrance_receipt(world, candidate)

    def test_facilities_are_not_mistaken_for_legacy_investigation_targets(self):
        candidate = Mock()
        candidate.layout = {'sites': [], 'roads': [], 'positions': {}, 'targets': [],
                            'facilities': [{'id': 'testing_stele', 'pos': [1, 2, 3], 'approach': [1, 2, 1], 'block': 'myvillage:spirit_testing_stele'}]}
        candidate.blueprints = {}
        _, _, _, targets = build_scene(candidate, [10, 60, 20], [])
        self.assertEqual((11, 62, 23), targets['testing_stele']['pos'])

    def test_fact_ids_are_never_dispatched_as_player_dialogue(self):
        from tools.qianzhen_opening_flow import OpeningFlow
        flow = OpeningFlow.__new__(OpeningFlow)
        flow.walk = Mock(side_effect=AssertionError('must reject before walking'))
        for action in FACT_ACTIONS:
            with self.assertRaises(ValueError):
                flow.actor('pharmacist', action)
        flow.walk.assert_not_called()


class RepairDriverTests(unittest.TestCase):
    def test_repair_write_authority_is_separate_and_narrow(self):
        import io
        from tools.qianzhen_opening_repair import RepairFixtureAPI
        stream, send = io.StringIO(), Mock(return_value='preview')
        api = RepairFixtureAPI(send, stream)
        self.assertEqual('preview', api.command('myvillage qianzhen repair_roof preview'))
        for cmd in ['myvillage story accept OpeningA', 'give OpeningA minecraft:bread',
                    'tp OpeningA 0 80 0', 'setblock 1 2 3 minecraft:air']:
            with self.assertRaises(ValueError):
                api.command(cmd)
        self.assertEqual(1, send.call_count)
        self.assertIn('admin_fixture', stream.getvalue())

    def test_non_targets_and_exact_before_after_are_verified(self):
        from tools.qianzhen_opening_repair import require_only_patch_changes
        expected = [{'pos': [1, 2, 3], 'before': 'minecraft:air', 'after': 'minecraft:spruce_log[axis=y]'}]
        require_only_patch_changes(expected, expected)
        for actual in [[], expected + [{'pos': [5, 2, 3], 'before': 'minecraft:dirt', 'after': 'minecraft:air'}],
                       [{'pos': [1, 2, 3], 'before': 'minecraft:chest', 'after': 'minecraft:spruce_log[axis=y]'}]]:
            with self.assertRaises(AssertionError):
                require_only_patch_changes(actual, expected)

    def test_fixture_dispatch_preview_apply_idempotency_and_cleanup(self):
        from types import SimpleNamespace
        from tools.qianzhen_opening_repair import run_repair
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            root, out, old = base / 'repo', base / 'evidence', base / 'old'
            out.mkdir()
            (old / 'playerdata').mkdir(parents=True)
            (old / 'level.dat').write_bytes(b'old-level')
            (old / 'playerdata/old.dat').write_bytes(b'old-player')
            files = tree_hashes(old)
            source = base / 'fixture.json'
            source.write_text(json.dumps(dict(world=str(old), quiescent=True, files=files,
                sha256=fingerprint(files), provenance={'kind': 'real_saved_world'})))
            patch = root / 'blueprints/qianzhen_roof_repair/sample_pavilion.json'
            patch.parent.mkdir(parents=True)
            site = {'id': 'canal_gardens', 'origin': [10, 0, 20]}
            patch.write_text(json.dumps({'supported': [{'version': 'old-version', 'site': site}], 'site_id': 'canal_gardens',
                'targets': [{'pos': [1, 2, 3], 'before': 'minecraft:air', 'after': 'minecraft:spruce_log[axis=y]'}], 'guards': []}))
            jar = base / 'test.jar'
            jar.write_bytes(b'jar')
            saved = {'phase': 'COMPLETE', 'id': 'chapter', 'version': 'old-version', 'dimension': 'minecraft:overworld', 'x': 100, 'y': 70, 'z': 200}
            changed = [{'pos': [111, 72, 223], 'before': 'minecraft:air', 'after': 'minecraft:spruce_log[axis=y]'}]
            commands, applied = [], []
            def send(state, cmd, timeout):
                commands.append(cmd)
                if cmd.startswith('myvillage story status'):
                    return 'QIANZHEN_STORY_STATUS ' + json.dumps({'npc': {'guide': {'personId': 42, 'epoch': 2}}})
                if cmd.endswith('preview'):
                    return '只读预览通过：待改 ' + ('0' if applied else '1') + ' 格'
                if cmd.endswith('apply'):
                    applied.append(1)
                    return '阡镇取样亭屋架修复完成：新增 ' + ('1' if len(applied) == 1 else '0') + ' 格支架'
                if cmd.startswith('execute'):
                    return 'Test failed' if applied and cmd.endswith('minecraft:air') else 'Test passed'
                return 'Game frozen'
            args = SimpleNamespace(roof_repair_fixture=str(source), run_id='repair-test-123', commit='a' * 40)
            with patch_module('tools.qianzhen_opening_repair.saved_geometry', return_value=(saved, {'sites': [site]})), \
                 patch_module('tools.combat_capture.session.start', return_value={'session_id': args.run_id}), \
                 patch_module('tools.qianzhen_opening_run.verify_owned_session'), \
                 patch_module('tools.qianzhen.story_lifecycle.lifecycle_command', side_effect=send), \
                 patch_module('tools.qianzhen_opening_run.single_flush', return_value={'requests': 1, 'status': 'confirmed'}) as flush, \
                 patch_module('tools.qianzhen_opening_run.stop_owned', return_value={'status': 'terminated'}) as stop, \
                 patch_module('tools.qianzhen_opening_run.archive_world', return_value={'sha256': 'x'}), \
                 patch_module('tools.qianzhen_opening_repair.block_changes', return_value=changed):
                result = run_repair(args, root, out, jar)
            self.assertEqual('captured_not_accepted', result['status'])
            self.assertEqual([], result['unrun'])
            self.assertEqual([], result['gameplay_scenarios_completed'])
            self.assertEqual(2, len(applied))
            self.assertEqual(2, sum(c.endswith('preview') for c in commands))
            self.assertTrue(result['original_unchanged'])
            flush.assert_called_once()
            self.assertEqual(2, stop.call_count)
            self.assertEqual(files, tree_hashes(old))
            self.assertFalse(any('deploy' in c for c in commands))


# Alias avoids the local patch-manifest Path shadowing unittest.mock.patch.
patch_module = patch

if __name__ == '__main__':
    unittest.main()
