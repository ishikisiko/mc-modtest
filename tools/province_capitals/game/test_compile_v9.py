"""V9 聚合边界的轻量 fixture；切片字节仅测试摘要，不替代最终 NBT/游戏检查。"""
from __future__ import annotations

import base64
import copy
import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from tools.province_capitals.game import compile_v9 as compiler


class CompileV9Test(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='capital-v9-')
        self.addCleanup(self.tmp.cleanup)
        self.legacy = Path(self.tmp.name) / 'legacy'
        self.out = Path(self.tmp.name) / 'v9'
        self.spec = json.loads(compiler.RULES.read_text(encoding='utf-8'))
        v8 = json.loads((compiler.ROOT / 'blueprints/worldplan/rules_v8.json').read_text(encoding='utf-8'))
        refs = []
        for definition in self.spec['assets']['cities']:
            old = {**definition, 'resource_version': 'v1'}
            if old['region_id'] != 'zhongzhou':
                old['layout_family_id'] = 'province_capitals/' + old['region_id'] + '_preview_v1'
                old['life_layout_id'] = old['city_id'] + '_blocks_v1'
            self.bundle(self.legacy, old)
            refs.append(compiler.inspect_city(self.legacy, old, 192)[0])
            if definition['resource_version'] == 'v2':
                self.bundle(self.out, definition)
        self.base = {'schema': 1, 'algorithm': 'myvillage:worldplan_v8', **v8['world'],
                     'profiles': [{'id': c['region_id']} for c in self.spec['assets']['cities']],
                     'capitals': {'schema': 1, **v8['planning'], 'bundles': refs}}
        compiler.write(self.legacy / 'data/myvillage/worldplan/frozen_v8.json', self.base)
        compiler.write(self.legacy / 'data/myvillage/worldgen/world_preset/cultivation.json',
                       {'dimensions': {'minecraft:overworld': {'generator': {'type': 'myvillage:planned_v8', 'rules': '旧预设', 'plan_hash': '旧指纹'}}}})

    @staticmethod
    def bundle(root, definition):
        city, region, version = (definition[key] for key in ('city_id', 'region_id', 'resource_version'))
        terrain_id = f'myvillage:capitals/{version}/{city}/terrain.bin.gz'
        terrain = gzip.compress(struct.pack('<256h', *([1] * 256)), mtime=0)
        terrain_path = compiler.resource_file(root, terrain_id)
        terrain_path.parent.mkdir(parents=True, exist_ok=True); terrain_path.write_bytes(terrain)
        tile_id = f'myvillage:province_capitals/{version}/{city}/tile_0_0'
        tile = gzip.compress(b'unit-test-opaque-tile-bytes', mtime=0)
        tile_path = compiler.resource_file(root, tile_id, template=True)
        tile_path.parent.mkdir(parents=True, exist_ok=True); tile_path.write_bytes(tile)
        slots = {key: 'rest' for key in ('inn_work', 'inn_rest', 'inn_social', 'guest_west', 'herbalist_work', 'herbalist_rest', 'smith_work', 'smith_rest')}
        slots.update(entry='entry', market='market', cultivate='cultivate')
        manifest = {'schema': 1, 'format': 'capital_game_bundle', 'city_id': city, 'region_id': region,
                    'display_name': '边界回归城', 'layout_family_id': definition['layout_family_id'],
                    'size': [16, 8, 16], 'source_offset': [0, 0, 0],
                    'placement': {'vertical_mode': 'absolute', 'anchor_y': 0, 'rotation_quarters': 0, 'blend_margin': 16},
                    'terrain': {'resource': terrain_id, 'sha256': compiler.sha(terrain), 'encoding': 'i16le_z_major_top_solid',
                                'width': 16, 'depth': 16, 'top_block': 'minecraft:stone', 'fill_block': 'minecraft:stone',
                                'subsurface_block': 'minecraft:dirt', 'subsurface_depth': 1},
                    'tiles': [{'x': 0, 'z': 0, 'template': tile_id, 'sha256': compiler.sha(tile)}],
                    'entries': [{'id': 'main_gate', 'position': [8, 2, 8], 'facing': 'south'}],
                    'life': {'layout_id': definition['life_layout_id'], 'places': [
                        {'id': key, 'kind': kind, 'pos': [8, 2, 8], 'capacity': 1, 'block': '', 'block_pos': [0, 0, 0]}
                        for key, kind in slots.items()]},
                    'source': {'hashes': {'tools/buildgen/groups.py': '0' * 64, 'tools/buildgen/ops.py': '1' * 64}}}
        compiler.write(root / f'data/myvillage/capitals/{version}/{city}/city.json', manifest)

    def manifest(self, root=None, version='v2', city='capital_beiyuan'):
        path = (root or self.out) / f'data/myvillage/capitals/{version}/{city}/city.json'
        return path, json.loads(path.read_text(encoding='utf-8'))

    def test_aggregate_keeps_all_legacy_bytes_and_changes_only_versioned_inputs(self):
        before = {str(p.relative_to(self.legacy)): p.read_bytes() for p in self.legacy.rglob('*') if p.is_file()}
        report = compiler.aggregate(self.out, self.legacy)
        after = {str(p.relative_to(self.legacy)): p.read_bytes() for p in self.legacy.rglob('*') if p.is_file()}
        self.assertEqual(before, after)
        frozen = json.loads((self.out / 'data/myvillage/worldplan/frozen_v9.json').read_text())
        self.assertEqual(7, sum(c['resource_version'] == 'v2' for c in report['cities']))
        self.assertEqual(192, frozen['capitals']['life_places_limit'])
        expected = copy.deepcopy(self.base); expected['algorithm'] = self.spec['algorithm']
        expected['capitals']['assets'] = self.spec['assets']
        expected['capitals']['bundles'] = [{key: c[key] for key in ('city_id', 'region_id', 'resource', 'manifest_sha256', 'bundle_sha256')} for c in report['cities']]
        self.assertEqual(expected, frozen)
        preset = json.loads((self.out / 'data/myvillage/worldgen/world_preset/cultivation.json').read_text())
        generator = preset['dimensions']['minecraft:overworld']['generator']
        self.assertEqual('myvillage:planned_v9', generator['type']); self.assertNotIn('plan_hash', generator)
        self.assertEqual(frozen, json.loads(gzip.decompress(base64.b64decode(generator['rules'][5:]))))
        self.assertFalse((self.out / 'data/myvillage/capitals/v1').exists())

    def test_source_hash_diagnostics_change_raw_json_but_not_runtime_identity(self):
        definition = {**self.spec['assets']['cities'][0], 'resource_version': 'v1'}
        definition.pop('layout_family_id'); definition.pop('life_layout_id')
        ref, before = compiler.inspect_city(self.legacy, definition, 192)
        path, city = self.manifest(self.legacy, 'v1')
        city['source']['hashes']['tools/buildgen/groups.py'] = '2' * 64
        city['source']['hashes']['tools/buildgen/ops.py'] = '3' * 64
        compiler.write(path, city)
        changed_ref, after = compiler.inspect_city(self.legacy, definition, 192)
        self.assertEqual(ref, changed_ref)
        self.assertNotEqual(before['raw_city_json_sha256'], after['raw_city_json_sha256'])
        compiler.aggregate(self.out, self.legacy)
        city['life']['places'][0]['capacity'] += 1
        compiler.write(path, city)
        with self.assertRaisesRegex(ValueError, '旧 V8 资源已漂移'):
            compiler.aggregate(self.out, self.legacy)

    def test_cross_version_and_cross_city_resource_paths_are_rejected(self):
        definition = self.spec['assets']['cities'][0]
        path, original = self.manifest()
        for key, value in [('terrain', 'myvillage:capitals/v1/capital_beiyuan/terrain.bin.gz'),
                           ('terrain', 'myvillage:capitals/v2/capital_ximo/terrain.bin.gz'),
                           ('tile', 'myvillage:province_capitals/v1/capital_beiyuan/tile_0_0'),
                           ('tile', 'myvillage:province_capitals/v2/capital_ximo/tile_0_0')]:
            city = copy.deepcopy(original)
            if key == 'terrain': city['terrain']['resource'] = value
            else: city['tiles'][0]['template'] = value
            compiler.write(path, city)
            with self.assertRaisesRegex(ValueError, '城 ID 和资源版本一致'):
                compiler.inspect_city(self.out, definition, 192)

    def test_missing_city_never_publishes_incomplete_v9(self):
        self.manifest()[0].unlink()
        with self.assertRaises(FileNotFoundError): compiler.aggregate(self.out, self.legacy)
        for relative in ('worldplan/frozen_v9.json', 'worldgen/world_preset/cultivation.json', 'capitals/v2/index.json'):
            self.assertFalse((self.out / 'data/myvillage' / relative).exists())
        with self.assertRaisesRegex(ValueError, '不能覆盖旧 cultivation.json'):
            compiler.aggregate(self.legacy, self.legacy)

    def test_existing_inflation_and_life_limits_are_not_relaxed(self):
        definition = self.spec['assets']['cities'][0]
        path, city = self.manifest()
        terrain = compiler.resource_file(self.out, city['terrain']['resource'])
        terrain.write_bytes(gzip.compress(b'\0' * 513, mtime=0))
        with self.assertRaisesRegex(ValueError, 'heightfield 长度不符'):
            compiler.inspect_city(self.out, definition, 192)
        self.bundle(self.out, definition); path, city = self.manifest()
        for i in range(193 - len(city['life']['places'])):
            city['life']['places'].append({'id': f'extra_{i}', 'kind': 'rest', 'pos': [8, 2, 8], 'capacity': 1, 'block': '', 'block_pos': [0, 0, 0]})
        compiler.write(path, city)
        with self.assertRaises(ValueError): compiler.inspect_city(self.out, definition, 192)

    def test_legacy_audit_names_source_diagnostics_and_rejects_binary_changes(self):
        baseline = Path(self.tmp.name) / 'baseline.json'
        records = {}
        for relative in ('worldplan', 'capitals/v1', 'structure/province_capitals/v1'):
            for path in (self.legacy / 'data/myvillage' / relative).rglob('*'):
                if not path.is_file(): continue
                records[str(path)] = compiler.sha(path.read_bytes())
                if path.suffix == '.json':
                    snapshot = baseline.parent / 'legacy-json-before' / path.relative_to(self.legacy)
                    snapshot.parent.mkdir(parents=True, exist_ok=True); snapshot.write_bytes(path.read_bytes())
        compiler.write(baseline, records)
        path, city = self.manifest(self.legacy, 'v1')
        for source in city['source']['hashes']:
            city['source']['hashes'][source] = compiler.sha((compiler.ROOT / source).read_bytes())
        compiler.write(path, city)
        report = compiler.audit_legacy(self.legacy, baseline)
        self.assertEqual('unchanged', report['runtime_identity'])
        self.assertEqual(1, len(report['diagnostic_changed_files']))
        self.assertEqual({'source.hashes[tools/buildgen/groups.py]', 'source.hashes[tools/buildgen/ops.py]'},
                         {item['field'] for item in report['diagnostic_changed_files'][0]['fields']})
        tile = next((self.legacy / 'data/myvillage/structure/province_capitals/v1').rglob('*.nbt'))
        tile.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, '字节漂移'):
            compiler.audit_legacy(self.legacy, baseline)

    def catalogue_audit_fixture(self):
        """Model the v1 producer's optional provenance, including its index byte totals."""
        source_root = Path(self.tmp.name) / 'source'
        catalogue = source_root / compiler.OPTIONAL_CATALOGUE
        catalogue.parent.mkdir(parents=True)
        catalogue.write_text('{"fixture": "verified catalogue"}\n')
        digest = compiler.sha(catalogue.read_bytes())
        path, city = self.manifest(self.legacy, 'v1')
        city['source']['modset'] = {'profile': 'vanilla', 'resolver': 'tools/buildgen/modset.py',
                                    'external_catalogue': None, 'external_catalogue_required': False}
        compiler.write(path, city)
        index_path = self.legacy / 'data/myvillage/capitals/v1/index.json'

        def make_index():
            cities = []
            for manifest in sorted(index_path.parent.glob('*/city.json')):
                data = json.loads(manifest.read_text())
                files = [manifest, compiler.resource_file(self.legacy, data['terrain']['resource'])]
                files.extend(compiler.resource_file(self.legacy, t['template'], template=True) for t in data['tiles'])
                cities.append({'city_id': data['city_id'], 'raw_city_json_sha256': compiler.sha(manifest.read_bytes()),
                               'bytes': sum(file.stat().st_size for file in files)})
            compiler.write(index_path, {'cities': cities, 'total_bytes': sum(c['bytes'] for c in cities)})

        make_index()
        baseline = Path(self.tmp.name) / 'baseline.json'
        records = {}
        for relative in ('worldplan', 'capitals/v1', 'structure/province_capitals/v1'):
            for file in (self.legacy / 'data/myvillage' / relative).rglob('*'):
                if not file.is_file(): continue
                records[str(file)] = compiler.sha(file.read_bytes())
                if file.suffix == '.json':
                    original = baseline.parent / 'legacy-json-before' / file.relative_to(self.legacy)
                    original.parent.mkdir(parents=True, exist_ok=True); original.write_bytes(file.read_bytes())
        compiler.write(baseline, records)
        city['source']['hashes'][compiler.OPTIONAL_CATALOGUE] = digest
        city['source']['modset']['external_catalogue'] = compiler.OPTIONAL_CATALOGUE
        compiler.write(path, city); make_index()
        return source_root, baseline, path, city, index_path, digest

    def test_optional_catalogue_requires_explicit_digest_and_recomputes_index_bytes(self):
        source, baseline, path, city, index_path, digest = self.catalogue_audit_fixture()
        with patch.object(compiler, 'ROOT', source):
            with self.assertRaisesRegex(ValueError, '明确摘要'):
                compiler.audit_legacy(self.legacy, baseline)
            with self.assertRaisesRegex(ValueError, '实际文件不符'):
                compiler.audit_legacy(self.legacy, baseline, catalogue_sha256='0' * 64)
            report = compiler.audit_legacy(self.legacy, baseline, catalogue_sha256=digest)
        self.assertEqual('unchanged', report['runtime_identity'])
        self.assertEqual('byte_identical', report['frozen_terrain_nbt'])
        self.assertEqual(2, len(report['diagnostic_changed_files']))
        fields = {field['field'] for item in report['diagnostic_changed_files'] for field in item['fields']}
        self.assertEqual({'source.modset.external_catalogue', 'source.hashes[exmod/mod_block_catalog.json]',
                          'cities[0].raw_city_json_sha256', 'cities[0].bytes', 'total_bytes'}, fields)

    def test_catalogue_allowance_cannot_hide_other_changes_or_forged_byte_totals(self):
        source, baseline, path, original, index_path, digest = self.catalogue_audit_fixture()
        original_index = json.loads(index_path.read_text())
        for fault in ('unknown_source', 'removed_source', 'missing_marker', 'foreign_marker', 'required_catalogue',
                      'wrong_digest', 'other_source_field', 'runtime_capacity', 'row_bytes', 'total_bytes', 'catalogue_bytes'):
            with self.subTest(fault=fault), patch.object(compiler, 'ROOT', source):
                city = copy.deepcopy(original); index = copy.deepcopy(original_index)
                catalogue = source / compiler.OPTIONAL_CATALOGUE
                catalogue.write_text('{"fixture": "verified catalogue"}\n')
                if fault == 'unknown_source': city['source']['hashes']['arbitrary.json'] = digest
                elif fault == 'removed_source': city['source']['hashes'].pop('tools/buildgen/groups.py')
                elif fault == 'missing_marker': city['source']['modset']['external_catalogue'] = None
                elif fault == 'foreign_marker': city['source']['modset']['external_catalogue'] = '../catalogue.json'
                elif fault == 'required_catalogue': city['source']['modset']['external_catalogue_required'] = True
                elif fault == 'wrong_digest': city['source']['hashes'][compiler.OPTIONAL_CATALOGUE] = '0' * 64
                elif fault == 'other_source_field': city['source']['unrelated'] = 'must not be ignored'
                elif fault == 'runtime_capacity': city['life']['places'][0]['capacity'] += 1
                elif fault == 'row_bytes':
                    index['cities'][0]['bytes'] += 1; index['total_bytes'] += 1
                elif fault == 'total_bytes': index['total_bytes'] += 1
                elif fault == 'catalogue_bytes': catalogue.write_text('changed catalogue')
                compiler.write(path, city); compiler.write(index_path, index)
                with self.assertRaises(ValueError):
                    compiler.audit_legacy(self.legacy, baseline, catalogue_sha256=digest)


if __name__ == '__main__':
    unittest.main()
