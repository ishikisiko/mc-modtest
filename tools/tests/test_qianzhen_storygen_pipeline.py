"""真实六插件 → 组合 → 生产 NBT → 固定来源 → SCHEM/viewer 的离线集成。"""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from qianzhen import build
from qianzhen.storygen import pipeline


# 无 seed 生产字节基线；2026-10-24 已核对棚顶支承、12格药圃和设施表的三项合法变化。
# 其余四栋模板必须保留原33e96fe0字节，不因工具接线漂移。
AUTHORED_HASHES = {
    'story/qianzhen_scene.json': '77cc9bc8c28fdd63f7bd2969ca8ff298a49a61adfb980a18d7d87ad00a46993a',
    'structure/qianzhen/apothecary.nbt': '42366a7c8dea7951671d41bb125ceb26deaa1cca61bbccc3782a15a809fa6ae5',
    'structure/qianzhen/canal_gardens.nbt': '966c50abb96b89d05a373fda931a792aa853da5c8f021b487b726dbc92907df5',
    'structure/qianzhen/initiation_court.nbt': '1b9116576de5ea3e7d10dea0e78297c4e1986edd0e59acba76e3371842048e71',
    'structure/qianzhen/old_relay.nbt': '29ed3c359641919ee6060d27fd15ec5672de708f4c9598fcfaaa5b8d38bf5495',
    'structure/qianzhen/relay_inn.nbt': 'b987f373af81f5051123b217961fc47a19f4b2e97fde2580a0927ae9a9b2c47b',
    'structure/qianzhen/spring_array.nbt': '73bcdf8965807f4c301b5dc7a13641c90d555767a9e1193a2cd077259e771300',
}


def run(*args):
    result = subprocess.run([sys.executable, *map(str, args)], cwd=ROOT,
                            capture_output=True, text=True, timeout=300)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return result


def files(root):
    return {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob('*') if p.is_file()}


class SeededPipelineIntegration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='qianzhen-six-sites-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.out = Path(cls.temp.name)
        cls.first, cls.repeat, cls.other = [cls.out / p for p in ('first', 'repeat', 'other')]
        pipeline.generate(cls.first, 20261024)
        # Validate the durable JSON artifact, including tuple -> JSON array conversion.
        cls.report = json.loads((cls.first / 'generation-report.json').read_text())
        # Exercise the documented CLI in a fresh interpreter/output, not a cached result.
        run('-m', 'tools.qianzhen.storygen', '--seed', 20261024, '--out', cls.repeat)
        pipeline.generate(cls.other, 17)
        cls.other_report = json.loads((cls.other / 'generation-report.json').read_text())
        cls.preview = cls.out / 'preview'
        run(ROOT / 'tools/qianzhen/build.py', '--source', cls.first / 'source',
            '--out', cls.preview, '--source-id', 'integration-seed-20261024')
        cls.layout = json.loads((cls.first / 'resources/data/myvillage/story/qianzhen_scene.json').read_text())

    def test_same_seed_is_byte_identical_including_report_and_materialized_source(self):
        self.assertEqual(files(self.first), files(self.repeat))

    def test_all_six_plugins_have_durable_identity_and_real_geometry_differences(self):
        generation = self.report['generation']
        self.assertEqual(generation['root_seed'], 20261024)
        self.assertEqual(generation['completed'], generation['selected'])
        self.assertEqual(generation['unrun'], [])
        self.assertEqual(len(generation['sites']), 6)
        self.assertEqual(generation['authored_input_fingerprint'], pipeline.digest(generation['authored_inputs']))
        for name, sha in generation['authored_inputs'].items():
            self.assertEqual(assets.sha(assets.SOURCE / name), sha)
        for name, sha in generation['generator_sources'].items():
            self.assertEqual(assets.sha(ROOT / name), sha)
        differences = []
        for site, row in generation['sites'].items():
            data = json.loads((self.first / 'source' / (site + '.json')).read_text())
            self.assertEqual(pipeline.geometry_sha256(data), row['geometry_sha256'])
            self.assertNotEqual(row['geometry_sha256'], row['authored_geometry_sha256'])
            self.assertTrue(row['semantics']['spaces'])
            self.assertTrue(row['semantics']['adjacencies'])
            if row['geometry_sha256'] != self.other_report['generation']['sites'][site]['geometry_sha256']:
                differences.append(site)
        self.assertEqual(set(differences), set(generation['sites']))
        self.assertNotEqual(self.report['scene_version'], self.other_report['scene_version'])

    def test_metadata_alone_cannot_claim_geometry_or_change_resource_identity(self):
        data = json.loads((self.first / 'source/relay_inn.json').read_text())
        changed = copy.deepcopy(data)
        changed['description'] = '仅改变说明'
        changed['root_seed'] = 999
        self.assertEqual(pipeline.geometry_sha256(data), pipeline.geometry_sha256(changed))
        blueprints = {s['id']: json.loads((self.first / 'source' / (s['id'] + '.json')).read_text())
                      for s in self.layout['sites']}
        for seed in (1, 999):
            report = assets.write_scene(self.out / str(seed), copy.deepcopy(self.layout), blueprints,
                                        {'generation': {'root_seed': seed}})
            self.assertEqual(report['scene_version'], self.report['scene_version'])
            self.assertEqual(report['resource_sha256'], self.report['resource_sha256'])

    def test_readme_changes_authored_provenance_without_changing_scene_content(self):
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder)/'authored', Path(folder)/'generated'
            shutil.copytree(assets.SOURCE, source)
            readme = source/'README.md'
            readme.write_bytes(readme.read_bytes()+b'\n')
            pipeline.generate(out, 20261024, source)
            report = json.loads((out/'generation-report.json').read_text())
            before, after = self.report['generation'], report['generation']
            self.assertIn('README.md', before['authored_inputs'])
            expected_inputs = {**before['authored_inputs'], 'README.md':assets.sha(readme)}
            self.assertEqual(after['authored_inputs'], expected_inputs)
            self.assertNotEqual(after['authored_input_fingerprint'], before['authored_input_fingerprint'])
            self.assertEqual(after['authored_input_fingerprint'], pipeline.digest(expected_inputs))
            self.assertEqual(after['materialized_inputs'], before['materialized_inputs'])
            self.assertNotEqual((out/'generation-report.json').read_bytes(),
                                (self.first/'generation-report.json').read_bytes())
            self.assertEqual((out/'source/generation-report.json').read_bytes(),
                             (out/'generation-report.json').read_bytes())
            self.assertEqual((out/'source/README.md').read_bytes(), readme.read_bytes())
            self.assertEqual(report['scene_version'], self.report['scene_version'])
            self.assertEqual(report['resource_sha256'], self.report['resource_sha256'])
            self.assertEqual(files(out/'resources'), files(self.first/'resources'))

    def test_scene_contract_props_and_npc_reserved_routes_survive_composition(self):
        authored, _, _ = assets.assemble()
        # Target identity is its stable ID. Canonical source JSON sorts object keys,
        # so compare every complete record in ID order, without discarding fields.
        self.assertEqual(sorted(self.layout['targets'], key=lambda t: t['id']),
                         sorted(authored['targets'], key=lambda t: t['id']))
        self.assertEqual(sorted(self.layout['facilities'], key=lambda t: t['id']),
                         sorted(authored['facilities'], key=lambda t: t['id']))
        self.assertEqual(self.layout['positions'], authored['positions'])
        self.assertEqual(self.layout['roads'], authored['roads'])
        self.assertEqual({t['id'] for t in self.layout['targets']}, assets.TARGETS)
        self.assertTrue(self.report['all_player_approaches_reachable_with_npcs'])
        # One new authored walk target and three facility approaches; none become NPC feet.
        self.assertEqual(self.report['connected_stations'], 157)
        self.assertEqual(self.report['road_building_overlap'], 0)
        self.assertEqual(json.loads((self.first / 'source/story_scene.json').read_text())['props'], [])
        # Source reuse must validate without attempting to replace the water cauldron twice.
        _, _, reassembled = assets.assemble(self.first / 'source')
        self.assertEqual(reassembled['connected_stations'], self.report['connected_stations'])

    def test_six_nbt_schem_roundtrips_viewers_and_package_use_generated_source(self):
        batch = json.loads((self.preview / 'batch-report.json').read_text())
        self.assertEqual(set(batch['completed']), set(self.report['generation']['selected']))
        self.assertEqual(batch['generation'], self.report['generation'])
        self.assertIn('根种子 20261024', (self.preview / 'index.html').read_text())
        for row in batch['buildings']:
            folder = self.preview / row['id']
            self.assertEqual(row['export_roundtrip'], 'every cell and sign matches NBT and Sponge v2')
            stem = 'qianzhen_' + row['id'] + '_v1'
            self.assertTrue((folder / (stem + '.schem')).is_file())
            self.assertIn('window', (folder / 'index.html').read_text())
            exported = folder / (stem + '.nbt')
            production = self.first / 'resources/data/myvillage/structure/qianzhen' / (row['id'] + '.nbt')
            self.assertEqual(exported.read_bytes(), production.read_bytes())
        with zipfile.ZipFile(self.preview / 'qianzhen_blueprints.zip') as archive:
            self.assertEqual(archive.read('authored/manifest.json'), (self.first / 'source/manifest.json').read_bytes())
            self.assertEqual(json.loads(archive.read('authored/story_scene.json'))['props'], [])
            self.assertEqual(json.loads(archive.read('generation-report.json')), self.report)
        for name, sha in self.report['resource_sha256'].items():
            self.assertEqual(assets.sha(self.first / 'resources' / name), sha)
        self.assertEqual(len(self.report['resource_sha256']), 7)

    def test_native_candidate_and_preview_share_content_identity_not_source_id(self):
        from tools.qianzhen.evidence_candidate import Candidate
        candidate = Candidate(self.first)
        record = build.validate_output(self.preview, candidate.identity['export_source_sha256'])
        self.assertEqual(record['source_inputs'], candidate.identity['export_source_sha256'])
        self.assertEqual(pipeline.digest(record['source_inputs']), candidate.identity['export_source_fingerprint'])
        self.assertEqual(record['source_inputs']['generation-report.json'],
                         candidate.identity['generation_report_sha256'])
        self.assertEqual(candidate.identity['source_sha256'], self.report['generation']['materialized_inputs'])
        self.assertEqual(candidate.identity['source_fingerprint'], pipeline.digest(candidate.identity['source_sha256']))
        for row in self.report['generation']['selected']:
            name = f'data/myvillage/structure/qianzhen/{row}.nbt'
            exported = self.preview / row / f'qianzhen_{row}_v1.nbt'
            self.assertEqual(assets.sha(exported), candidate.identity['resource_sha256'][name])
        # Same caller label cannot authorize a different source, even if its own
        # source/report/resource hashes are internally coherent.
        before = files(self.preview)
        with self.assertRaisesRegex(ValueError, 'source content differs'):
            build.validate_output(self.preview, build.source_identity(self.other / 'source'))
        self.assertEqual(files(self.preview), before)

    def test_default_authored_production_is_byte_identical(self):
        out = self.out / 'authored'
        run(ROOT / 'tools/qianzhen/story_assets.py', '--out', out)
        self.assertEqual({p.relative_to(out / 'data/myvillage').as_posix(): assets.sha(p)
                          for p in out.rglob('*') if p.is_file()}, AUTHORED_HASHES)

    def test_seeded_production_cli_check_reuses_same_content_and_keeps_output_read_only(self):
        before = files(self.first / 'resources')
        run(ROOT / 'tools/qianzhen/story_assets.py', '--seed', 20261024, '--out', self.first / 'resources',
            '--report', self.out / 'checked.json', '--check')
        checked = json.loads((self.out / 'checked.json').read_text())
        self.assertEqual(checked['resource_sha256'], self.report['resource_sha256'])
        self.assertEqual(files(self.first / 'resources'), before)

    def test_seed_requires_explicit_output_and_report(self):
        command = [sys.executable, str(ROOT / 'tools/qianzhen/story_assets.py'), '--seed', '1']
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('explicit --out', result.stderr)
        result = subprocess.run(command + ['--out', str(self.out / 'unused')], capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('requires --report', result.stderr)
        self.assertFalse((self.out / 'unused').exists())

    def test_changed_materialized_source_is_rejected_before_export(self):
        source = self.first / 'source/relay_inn.json'
        before = source.read_bytes()
        try:
            source.write_bytes(before + b'\n')
            result = subprocess.run([sys.executable, str(ROOT / 'tools/qianzhen/build.py'),
                                     '--source', str(source.parent), '--out', str(self.out / 'stale')],
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('seeded source snapshot changed: relay_inn.json', result.stderr)
            self.assertFalse((self.out / 'stale').exists())
        finally:
            source.write_bytes(before)

    def test_mixed_seed_reuse_is_rejected_before_any_output_write(self):
        out = self.out / 'mixed-seed'
        run(ROOT / 'tools/qianzhen/build.py', '--source', self.other / 'source',
            '--out', out, '--only', 'relay_inn', '--source-id', 'same-label')
        for captured in (False, True):
            if captured:
                # Offline fixture for capture.py's successful attestation boundary.
                record = build.validate_output(out)
                (out / 'relay_inn/full.png').write_bytes(b'old capture')
                (out / 'browser-check.json').write_text('{}')
                build.record_capture(out, record, ['relay_inn/full.png', 'browser-check.json'])
            for extra in ([], ['--only', 'apothecary'], ['--package-only']):
                with self.subTest(captured=captured, extra=extra):
                    before = files(out)
                    result = subprocess.run([sys.executable, str(ROOT / 'tools/qianzhen/build.py'),
                                             '--source', str(self.first / 'source'), '--out', str(out),
                                             '--source-id', 'same-label', *extra],
                                            capture_output=True, text=True)
                    self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertIn('source content differs', result.stderr)
                    self.assertEqual(files(out), before)

    def test_same_content_partial_build_accumulates_verified_sites_despite_label_change(self):
        out = self.out / 'incremental'
        for site in ('relay_inn', 'apothecary'):
            run(ROOT / 'tools/qianzhen/build.py', '--source', self.first / 'source',
                '--out', out, '--only', site, '--source-id', site)
        batch = json.loads((out / 'batch-report.json').read_text())
        self.assertEqual(batch['completed'], ['apothecary'])
        self.assertEqual([r['id'] for r in batch['buildings']], ['relay_inn', 'apothecary'])
        with zipfile.ZipFile(out / 'qianzhen_blueprints.zip') as archive:
            for site in ('relay_inn', 'apothecary'):
                self.assertEqual(archive.read(f'{site}/qianzhen_{site}_v1.nbt'),
                                 (self.first / f'resources/data/myvillage/structure/qianzhen/{site}.nbt').read_bytes())
            self.assertFalse(any(n.startswith('old_relay/') for n in archive.namelist()))

    def test_package_rejects_tampered_or_untracked_exports_and_capture_without_writing(self):
        for name in ('relay_inn/qianzhen_relay_inn_v1.nbt', 'relay_inn/qianzhen_relay_inn_v1.schem',
                     'relay_inn/authored.json', 'relay_inn/report.json', 'generation-report.json',
                     'batch-report.json', 'relay_inn/full.png', 'browser-check.json', 'unrelated.json'):
            with self.subTest(name=name):
                out = self.out / 'tampered'
                shutil.copytree(self.preview, out)
                try:
                    (out / name).write_bytes(b'tampered or stale')
                    before = files(out)
                    with self.assertRaisesRegex(ValueError, 'files changed or untracked'):
                        build.package(out, self.first / 'source')
                    self.assertEqual(files(out), before)
                finally:
                    shutil.rmtree(out)

    def test_attested_capture_packages_then_rejects_rebuild_or_tampering(self):
        out = self.out / 'captured'
        shutil.copytree(self.preview, out)
        record = build.validate_output(out)
        (out / 'relay_inn/full.png').write_bytes(b'offline capture fixture, not visual evidence')
        (out / 'browser-check.json').write_text('{}')
        build.record_capture(out, record, ['relay_inn/full.png', 'browser-check.json'])
        run(ROOT / 'tools/qianzhen/build.py', '--source', self.first / 'source', '--out', out, '--package-only')
        with zipfile.ZipFile(out / 'qianzhen_blueprints.zip') as archive:
            self.assertEqual(archive.read('relay_inn/full.png'), (out / 'relay_inn/full.png').read_bytes())
        before = files(out)
        result = subprocess.run([sys.executable, str(ROOT / 'tools/qianzhen/build.py'),
                                 '--source', str(self.first / 'source'), '--out', str(out), '--only', 'relay_inn'],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('has captures', result.stderr)
        self.assertEqual(files(out), before)
        (out / 'relay_inn/full.png').write_bytes(b'changed capture')
        before = files(out)
        with self.assertRaisesRegex(ValueError, 'files changed or untracked'):
            build.package(out, self.first / 'source')
        # CLI must fail before importing Playwright, starting HTTP or writing files.
        result = subprocess.run([sys.executable, str(ROOT / 'tools/qianzhen/capture.py'), '--out', str(out)],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('files changed or untracked', result.stderr)
        self.assertEqual(files(out), before)

    def test_authored_content_changes_and_legacy_outputs_are_rejected_read_only(self):
        source = self.out / 'authored-source'
        shutil.copytree(build.SOURCE, source)
        out = self.out / 'authored-preview'
        run(ROOT / 'tools/qianzhen/build.py', '--source', source, '--out', out, '--only', 'relay_inn')
        with (source / 'apothecary.json').open('a') as stream:
            stream.write('\n')
        for extra in (['--only', 'apothecary'], ['--package-only']):
            before = files(out)
            result = subprocess.run([sys.executable, str(ROOT / 'tools/qianzhen/build.py'),
                                     '--source', str(source), '--out', str(out), *extra],
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('source content differs', result.stderr)
            self.assertEqual(files(out), before)
        legacy = self.out / 'legacy'
        legacy.mkdir()
        (legacy / 'user.json').write_text('preserve me')
        before = files(legacy)
        result = subprocess.run([sys.executable, str(ROOT / 'tools/qianzhen/build.py'), '--out', str(legacy)],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('no verified provenance', result.stderr)
        self.assertEqual(files(legacy), before)


if __name__ == '__main__':
    unittest.main()
