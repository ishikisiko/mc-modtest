"""真实六插件 → 组合 → 生产 NBT → 固定来源 → SCHEM/viewer 的离线集成。"""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from qianzhen.storygen import pipeline


# 33e96fe0 的无 seed 生产命令实测字节基线；工具接线不得让默认资源漂移。
AUTHORED_HASHES = {
    'story/qianzhen_scene.json': 'c12ddbd1c407b0cd3d66a3467f660f7af2981b9741f70b9916bd1f988e89f29d',
    'structure/qianzhen/apothecary.nbt': 'bf4dd639a6fc36aec8c583bb6927a8a282395a4ea436c9a6b88d9d2c9e2b454e',
    'structure/qianzhen/canal_gardens.nbt': 'ad91e9c41e82d45e7e624a9b0ac1690e8d6b1b8a669825079df3170da2337573',
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

    def test_scene_contract_props_and_npc_reserved_routes_survive_composition(self):
        authored, _, _ = assets.assemble()
        # Target identity is its stable ID. Canonical source JSON sorts object keys,
        # so compare every complete record in ID order, without discarding fields.
        self.assertEqual(sorted(self.layout['targets'], key=lambda t: t['id']),
                         sorted(authored['targets'], key=lambda t: t['id']))
        self.assertEqual(self.layout['positions'], authored['positions'])
        self.assertEqual(self.layout['roads'], authored['roads'])
        self.assertEqual({t['id'] for t in self.layout['targets']}, assets.TARGETS)
        self.assertTrue(self.report['all_player_approaches_reachable_with_npcs'])
        self.assertEqual(self.report['connected_stations'], 153)
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


if __name__ == '__main__':
    unittest.main()
