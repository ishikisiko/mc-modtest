"""Real gzipped NBT regressions for the schema2 opening receipt (no read mocks)."""
from __future__ import annotations

import base64
import copy
import gzip
import json
import os
import shutil
import subprocess
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
import unittest

from tools.qianzhen_opening_evidence import digest
from tools.qianzhen_opening_flow import entrance_receipt, saved_geometry_expected, frozen_geometry
from tools.qianzhen.story_deployment import saved_deployment

ROOT = Path(__file__).resolve().parents[2]


class Tag:
    def __init__(self, kind, value):
        self.kind, self.value = kind, value


def utf(text):
    encoded = text.encode('utf8')
    return struct.pack('>H', len(encoded)) + encoded


def payload(kind, value):
    if kind == 1:
        return struct.pack('>b', value)
    if kind == 3:
        return struct.pack('>i', value)
    if kind == 8:
        return utf(value)
    if kind == 11:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>i', v) for v in value)
    if kind == 9:
        return bytes([3]) + struct.pack('>i', len(value)) + b''.join(struct.pack('>i', v) for v in value)
    if kind == 10:
        return b''.join(named(k, v) for k, v in value.items()) + b'\0'
    raise AssertionError(kind)


def named(key, value):
    if isinstance(value, Tag):
        kind, value = value.kind, value.value
    else:
        kind = 10 if isinstance(value, dict) else 8 if isinstance(value, str) else 11 if isinstance(value, list) else 3
    return bytes([kind]) + utf(key) + payload(kind, value)


def write_nbt(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(named('', {'data': data}), mtime=0))


def freeze(layout):
    return 'gzip:' + base64.b64encode(gzip.compress(json.dumps(layout).encode(), mtime=0)).decode()


class OpeningSchema2Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Compile only the real pure Java production classes; no Gradle or MC.
        cls.java_tmp = tempfile.TemporaryDirectory(prefix='opening-schema2-java-')
        cls.addClassCleanup(cls.java_tmp.cleanup)
        folder = Path(cls.java_tmp.name)
        candidates = sorted((Path.home() / '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1').glob('*/*.jar'))
        if not shutil.which('javac') or not candidates:
            raise RuntimeError('schema2真实Java回归需javac及本地Gson2.10.1；不联网/不回退mock')
        gson = candidates[0]
        runner = folder / 'FreezeProbe.java'
        runner.write_text("""package com.example.myvillage.story.scene;
import java.nio.file.*;
import com.google.gson.*;
public final class FreezeProbe {
 public static void main(String[] args) throws Exception {
  var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
  var parsed=SceneLayout.parse(input);
  String encoded=SceneGeometryArchive.freeze(parsed);
  var out=new JsonObject(); out.addProperty("encoded",encoded);
  out.add("geometry",JsonParser.parseString(com.example.myvillage.worldplan.FrozenRulesEncoding.decode(encoded)));
  Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(out));
 }
}""")
        relative = ['story/scene/SceneLayout.java', 'story/scene/SceneGeometryArchive.java', 'worldplan/FrozenRulesEncoding.java']
        sources = [ROOT / 'src/main/java/com/example/myvillage' / p for p in relative]
        compile_cmd = ['javac', '-encoding', 'UTF-8', '-cp', str(gson), '-d', str(folder), *map(str, sources), str(runner)]
        subprocess.run(compile_cmd, check=True, timeout=45, capture_output=True)
        from tools.qianzhen.story_assets import assemble, write_scene
        layout, authored, report = assemble(ROOT / 'blueprints/qianzhen')
        write_scene(folder / 'authored', layout, authored, report)
        old_path = ROOT / 'src/main/resources/data/myvillage/story/entrance_legacy/1-93b7ddbc5c59392e.json.gz'
        legacy = json.loads(gzip.decompress(old_path.read_bytes()))
        cls.java_cases = {}
        evidence_env = os.environ.get('QIANZHEN_SCHEMA2_EVIDENCE')
        evidence = Path(evidence_env) if evidence_env else None
        if evidence:
            evidence.mkdir(parents=True, exist_ok=False)
        for name, data in [('new_facilities', layout), ('legacy_no_facilities', legacy)]:
            scene_path, output_path = folder / (name + '-scene.json'), folder / (name + '-freeze.json')
            scene_path.write_text(json.dumps(data, ensure_ascii=False))
            run_cmd = ['java', '-cp', os.pathsep.join((str(folder), str(gson), str(ROOT / 'src/main/resources'))),
                       'com.example.myvillage.story.scene.FreezeProbe', str(scene_path), str(output_path)]
            subprocess.run(run_cmd, check=True, timeout=20, capture_output=True)
            cls.java_cases[name] = (data, json.loads(output_path.read_text()))
            if evidence:
                shutil.copy2(scene_path, evidence / scene_path.name)
                shutil.copy2(output_path, evidence / output_path.name)
        if evidence:
            (evidence / 'provenance.json').write_text(json.dumps({
                'compile_argv': compile_cmd, 'gson': {'path': str(gson), 'sha256': digest(gson)},
                'production_sources': {str(p.relative_to(ROOT)): digest(p) for p in sources},
                'runner': runner.read_text(), 'legacy_source_sha256': digest(old_path),
                'kind': 'offline_actual_Java_freeze_and_real_gzipped_NBT_no_Minecraft',
                'cases': {k: {'root_fields': sorted(v[1]['geometry']), 'site_fields': sorted(v[1]['geometry']['sites'][0])} for k,v in cls.java_cases.items()}
            }, ensure_ascii=False, indent=2) + '\n')

    def install_java_case(self, case):
        layout, output = self.java_cases[case]
        self.layout = copy.deepcopy(layout)
        self.candidate.layout = self.layout
        rules = json.loads(self.entrance['rules'])
        bounds = self.layout['bounds']
        margin, half = rules['scene_margin'], rules['road_half_width']
        rect = [100 + bounds['min'][0] - margin, -50 + bounds['min'][2] - margin,
                100 + bounds['max'][0] + margin, -50 + bounds['max'][2] + margin]
        route = [(8,9)]
        approach = rect[0] - half - 1
        anchor = (100 + rules['road_anchor'][0], -50 + rules['road_anchor'][1])
        for x,z in ((approach,9), (approach,anchor[1]), anchor):
            while route[-1] != (x,z):
                a,b = route[-1]
                route.append((a + (x>a) - (x<a), b + (z>b) - (z<b)))
        self.entrance['plan']['rect'] = rect
        self.entrance['plan']['route'] = [v for p in route for v in p]
        self.deployment.update(version=layout['version'], id=layout['id'], geometry=output['encoded'])
        self.write()
        return layout, output

    def test_actual_java_new_and_legacy_freeze_exact_root_and_site_contract(self):
        for name in self.java_cases:
            with self.subTest(case=name):
                layout, output = self.install_java_case(name)
                self.assertEqual(saved_geometry_expected(layout), output['geometry'])
                self.assertEqual(output['geometry'], frozen_geometry(self.read()['deployment']['geometry']))
                self.assertNotIn('inputs', output['geometry'])
                self.assertNotIn('bounds', output['geometry']['sites'][0])
                self.assertEqual({'id','template','origin','size','sha256'}, set(output['geometry']['sites'][0]))
                expected_fields = {'schema','id','version','sites','bounds','positions','targets','roads'}
                if name == 'new_facilities':
                    self.assertIn('inputs', layout)
                    expected_fields.add('facilities')
                self.assertEqual(expected_fields, set(output['geometry']))

    def test_actual_java_geometry_extra_source_metadata_is_rejected_in_save(self):
        layout, output = self.install_java_case('new_facilities')
        changed = copy.deepcopy(output['geometry'])
        changed['inputs'] = layout['inputs']
        self.deployment['geometry'] = freeze(changed)
        self.write()
        with self.assertRaisesRegex(ValueError, '完整冻结几何'):
            self.read()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.world = Path(self.temp.name)
        self.entrance_path = self.world / 'data/myvillage_qianzhen_entrance.dat'
        self.deployment_path = self.world / 'data/myvillage_qianzhen_scene.dat'
        self.layout = {'schema': 1, 'id': 'qianzhen_chapter_1', 'version': '1-real-fixture',
                       'sites': [{'id': 'apothecary', 'template': 'myvillage:qianzhen/apothecary',
                                  'origin': [0, 0, 0], 'size': [30, 9, 30], 'sha256': 'a' * 64}],
                       'bounds': {'min': [0, 0, 0], 'max': [80, 10, 140]},
                       'positions': {'guide': {'pos': [3, 1, 4], 'approach': [4, 1, 4]}},
                       'targets': [{'id': 'water_sample', 'block': 'minecraft:water', 'state': 'minecraft:water[level=0]',
                                    'pos': [5, 1, 4], 'approach': [6, 1, 4]}],
                       'roads': [{'pos': [4, 0, 4], 'state': 'minecraft:smooth_sandstone'}],
                       'facilities': [{'id': 'testing_stele', 'block': 'myvillage:spirit_testing_stele',
                                       'state': 'myvillage:spirit_testing_stele', 'pos': [8, 1, 4], 'approach': [9, 1, 4]}]}
        self.candidate = SimpleNamespace(layout=self.layout)
        rules = json.loads((ROOT / 'src/main/resources/data/myvillage/story/qianzhen_entrance.json').read_text())
        # Real EntrancePlan geometry: saved start -> approach -> town road anchor.
        self.origin = [100, 70, -50]
        margin, half = rules['scene_margin'], rules['road_half_width']
        rect = [100 - margin, -50 - margin, 180 + margin, 90 + margin]
        approach, anchor = rect[0] - half - 1, [100 + rules['road_anchor'][0], -50 + rules['road_anchor'][1]]
        route = [(8, 9)]
        for x, z in ((approach, 9), (approach, anchor[1]), tuple(anchor)):
            while route[-1] != (x, z):
                a, b = route[-1]
                route.append((a + (x > a) - (x < a), b + (z > b) - (z < b)))
        self.route = route
        self.entrance = {'schema': 1, 'phase': 'COMPLETE', 'rules': json.dumps(rules, ensure_ascii=False),
                         'plan': {'origin': self.origin, 'route': [v for p in route for v in p], 'notice': [8, 9 + half], 'rect': rect},
                         'notice': [8, 71, 9 + half]}
        self.layout['inputs'] = {'authored.json': 'c' * 64}
        self.deployment = {'schema': 2, 'phase': 'COMPLETE', 'id': self.layout['id'], 'version': self.layout['version'],
                           'dimension': 'minecraft:overworld', 'x': 100, 'y': 70, 'z': -50, 'regulated': Tag(1, 0),
                           'geometry': freeze(saved_geometry_expected(self.layout))}
        self.write()

    def write(self):
        write_nbt(self.entrance_path, self.entrance)
        write_nbt(self.deployment_path, self.deployment)

    def read(self):
        return entrance_receipt(self.world, self.candidate)

    def test_real_schema2_is_read_and_fully_bound_without_legacy_helper(self):
        with self.assertRaisesRegex(ValueError, 'identity differs'):
            saved_deployment(self.world, self.layout, self.origin)
        value = self.read()
        self.assertEqual([(x, 71, z) for x, z in self.route], value['route'])
        self.assertEqual([8, 71, 11], value['notice'])
        self.assertEqual(2, value['deployment']['schema'])
        self.assertEqual(digest(self.deployment_path), value['deployment']['saved_data_sha256'])
        self.assertEqual(digest(self.entrance_path), value['receipt_sha256'])

    def test_legacy_schema1_remains_accepted_only_by_original_helper(self):
        self.deployment['schema'] = 1
        self.deployment.pop('geometry')
        self.write()
        self.assertEqual(1, saved_deployment(self.world, self.layout, self.origin)['schema'])
        with self.assertRaises(ValueError):
            self.read()

    def test_every_deployment_identity_field_is_strict(self):
        original = copy.deepcopy(self.deployment)
        for field, bad in [('schema', 1), ('phase', 'PLACING'), ('id', 'other'), ('version', 'other'),
                           ('dimension', 'minecraft:the_nether'), ('x', 101), ('y', 71), ('z', -49)]:
            with self.subTest(field=field):
                self.deployment = copy.deepcopy(original)
                self.deployment[field] = bad
                self.write()
                with self.assertRaisesRegex(ValueError, '身份不符'):
                    self.read()

    def test_full_frozen_geometry_rejects_missing_extra_and_nested_tampering(self):
        bad_layouts = []
        expected_geometry = saved_geometry_expected(self.layout)
        for field in expected_geometry:
            changed = copy.deepcopy(expected_geometry)
            changed.pop(field)
            bad_layouts.append((field, changed))
        for field, value in [('schema', True), ('unexpected', 'injected')]:
            changed = copy.deepcopy(expected_geometry)
            changed[field] = value
            bad_layouts.append((field, changed))
        for field, key, value in [('sites', 'sha256', 'b' * 64), ('roads', 'state', 'minecraft:air'),
                                  ('facilities', 'pos', [99, 1, 4]), ('targets', 'approach', [88, 1, 4])]:
            changed = copy.deepcopy(expected_geometry)
            changed[field][0][key] = value
            bad_layouts.append((field + '.' + key, changed))
        for label, layout in bad_layouts:
            with self.subTest(field=label):
                self.deployment['geometry'] = freeze(layout)
                self.write()
                with self.assertRaisesRegex(ValueError, '完整冻结几何'):
                    self.read()

    def test_saved_notice_must_match_plan_and_actual_route_start(self):
        for target in ('saved', 'planned', 'both'):
            with self.subTest(target=target):
                original = copy.deepcopy(self.entrance)
                if target in ('saved', 'both'):
                    self.entrance['notice'][0] += 1
                if target in ('planned', 'both'):
                    self.entrance['plan']['notice'][0] += 1
                self.write()
                with self.assertRaisesRegex(ValueError, '木牌'):
                    self.read()
                self.entrance = original

    def test_route_rejects_discontinuous_truncated_and_continuous_detour(self):
        raw = self.entrance['plan']['route'][:]
        detour = raw[:2] + [raw[0], raw[1] - 1, raw[0] + 1, raw[1] - 1, raw[0] + 1, raw[1]] + raw[4:]
        for bad in [raw[:-1], raw[:-2], [raw[0] + 2] + raw[1:], detour]:
            with self.subTest(route=bad[:8]):
                self.entrance['plan']['route'] = bad
                self.write()
                with self.assertRaises(ValueError):
                    self.read()
        self.entrance['plan']['route'] = raw

    def test_origin_and_scene_rect_must_bind_same_candidate(self):
        for field in ('origin', 'rect'):
            with self.subTest(field=field):
                self.entrance['plan'][field][0] += 1
                self.write()
                with self.assertRaises(ValueError):
                    self.read()
                self.entrance['plan'][field][0] -= 1

    def test_nbt_field_types_do_not_coerce_byte_int_list_or_string(self):
        original = copy.deepcopy(self.deployment)
        for field, bad in [('schema', Tag(1, 2)), ('x', '100'), ('geometry', Tag(3, 1)), ('regulated', 0), ('phase', Tag(3, 1))]:
            with self.subTest(field=field):
                self.deployment = copy.deepcopy(original)
                self.deployment[field] = bad
                self.write()
                with self.assertRaisesRegex(ValueError, '错型'):
                    self.read()
        self.deployment = original
        for field, bad in [('origin', Tag(9, [100, 70, -50])), ('route', '8 9'), ('notice', [8]), ('rect', [1, 2])]:
            with self.subTest(entrance_field=field):
                original_plan = copy.deepcopy(self.entrance['plan'])
                self.entrance['plan'][field] = bad
                self.write()
                with self.assertRaises(ValueError):
                    self.read()
                self.entrance['plan'] = original_plan

    def test_rule_types_and_duplicate_json_fields_fail_closed(self):
        original = self.entrance['rules']
        for field, bad in [('max_route_length', True), ('road_half_width', '2'), ('scene_margin', -1), ('road_anchor', [1, False])]:
            with self.subTest(field=field):
                rules = json.loads(original)
                rules[field] = bad
                self.entrance['rules'] = json.dumps(rules)
                self.write()
                with self.assertRaises(ValueError):
                    self.read()
        self.entrance['rules'] = original
        duplicate = json.dumps(self.layout)[:-1] + ', "schema": 1}'
        self.deployment['geometry'] = 'gzip:' + base64.b64encode(gzip.compress(duplicate.encode())).decode()
        self.write()
        with self.assertRaisesRegex(ValueError, '重复字段'):
            self.read()

    def test_truncated_outer_gzip_footer_and_inner_gzip_are_rejected(self):
        for path in (self.entrance_path, self.deployment_path):
            for missing in (1, 8, 20):
                with self.subTest(path=path.name, missing=missing):
                    self.write()
                    path.write_bytes(path.read_bytes()[:-missing])
                    with self.assertRaises(ValueError):
                        self.read()
        self.write()
        compressed = gzip.compress(json.dumps(self.layout).encode())
        for bad in [compressed[:-1], compressed[:-8], b'not gzip']:
            self.deployment['geometry'] = 'gzip:' + base64.b64encode(bad).decode()
            self.write()
            with self.assertRaises(ValueError):
                self.read()

    def test_noncomplete_entrance_and_nonobject_geometry_are_rejected(self):
        for phase in ('BUILDING', 'UNAVAILABLE', 'REUSED', 'FAILED'):
            self.entrance['phase'] = phase
            self.write()
            with self.assertRaisesRegex(ValueError, '未 COMPLETE'):
                self.read()
        self.entrance['phase'] = 'COMPLETE'
        self.deployment['geometry'] = freeze([])
        self.write()
        with self.assertRaisesRegex(ValueError, '必须为对象'):
            self.read()


if __name__ == '__main__':
    unittest.main()
