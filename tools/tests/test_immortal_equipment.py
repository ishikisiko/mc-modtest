"""独立验证器故障注入；真实资源检查由validate_immortal_equipment执行。"""
from __future__ import annotations

import copy
import struct
import tempfile
from pathlib import Path
import unittest
import zipfile
import zlib

from tools import validate_immortal_equipment as check


def fixture():
    components = [
        {'id': 'cap', 'role': 'head', 'material': 'silk'},
        {'id': 'coat', 'role': 'chest', 'material': 'silk'},
        {'id': 'sleeve', 'role': 'chest', 'material': 'silk'},
        {'id': 'pants', 'role': 'legs', 'material': 'silk'},
        {'id': 'boot', 'role': 'boots', 'material': 'silk'},
    ]
    design = {'id': 'test_set', 'materials': {'silk': {'opacity': 1}}, 'components': components}
    parts = []
    for component, bone in zip(components, ('head', 'body', 'right_arm', 'left_leg', 'left_leg')):
        parts.append({'id': component['id'], 'slot': 'feet' if component['role'] == 'boots' else component['role'],
                      'bone': bone, 'material': 'silk', 'side': 'double',
                      'vertices': [[0, 0, 0, 0, 0, 0, 0, 1], [1, 0, 0, 1, 0, 0, 0, 1], [0, 1, 0, 0, 1, 0, 0, 1]],
                      'indices': [0, 1, 2]})
    profile = {'bone_origins': {k: [0, 0, 0] for k in check.BONES}, 'parts': parts}
    effects = {'level': 1, 'flows': [{'role': 'chest'}], 'arcs': [],
               'motions': [{'targets': ['coat']}], 'glows': [{'targets': ['cap']}]}
    data = {'version': 1, 'set_id': 'test_set', 'materials': {'silk': {'texture': 'myvillage:test.png', 'opacity': 1}},
            'profiles': {'standard': copy.deepcopy(profile), 'slim': copy.deepcopy(profile)},
            'effects': effects, 'effect_bindings': {'flows': ['body'], 'arcs': []}}
    return data, design


class ImmortalMeshValidationTest(unittest.TestCase):
    def test_minimal_real_triangle_fixture_is_accepted(self):
        data, design = fixture()
        self.assertEqual(set(check.validate_mesh(data, design)), {'standard', 'slim'})

    def rejected(self, mutate, message):
        data, design = fixture();mutate(data)
        with self.assertRaisesRegex(ValueError, message):
            check.validate_mesh(data, design)

    def test_identity_and_missing_profiles_fail(self):
        self.rejected(lambda d: d.update(set_id='other'), '身份')
        self.rejected(lambda d: d['profiles'].pop('slim'), '宽/细臂')

    def test_nonfinite_vertices_and_normal_fail(self):
        for index, value in ((0, float('nan')), (1, float('inf')), (4, True), (7, 0)):
            with self.subTest(index=index, value=value):
                self.rejected(lambda d: d['profiles']['standard']['parts'][0]['vertices'][0].__setitem__(index, value), '有限|单位化')

    def test_out_of_range_uv_and_indices_fail(self):
        self.rejected(lambda d: d['profiles']['standard']['parts'][0]['vertices'][0].__setitem__(3, 1.01), 'UV')
        for bad in ([-1, 1, 2], [0, 1, 3], [0, 1], [False, 1, 2]):
            with self.subTest(indices=bad):
                self.rejected(lambda d: d['profiles']['standard']['parts'][0].update(indices=bad), '索引|三角')

    def test_degenerate_triangles_fail(self):
        self.rejected(lambda d: d['profiles']['standard']['parts'][0].update(indices=[0, 0, 1]), '退化')

    def test_wrong_bone_missing_and_extra_mannequin_components_fail(self):
        self.rejected(lambda d: d['profiles']['standard']['parts'][0].update(bone='body'), '骨骼')
        self.rejected(lambda d: d['profiles']['standard']['parts'].pop(), '组件')
        self.rejected(lambda d: d['profiles']['standard']['parts'].append(copy.deepcopy(d['profiles']['standard']['parts'][0])), '组件')
        self.rejected(lambda d: d['profiles']['standard']['parts'][0].update(id='avatar_face'), '人台')

    def test_wrong_material_and_authored_opacity_fail(self):
        self.rejected(lambda d: d['profiles']['standard']['parts'][0].update(material='missing'), '材质')
        self.rejected(lambda d: d['materials']['silk'].update(opacity=.5), '透明度')
        self.rejected(lambda d: d['profiles']['standard']['parts'][0].update(side='front'), '双面')

    def test_slim_only_changes_arm_geometry(self):
        data, design = fixture()
        data['profiles']['slim']['parts'][2]['vertices'][1][0] = .75
        check.validate_mesh(data, design)
        self.rejected(lambda d: d['profiles']['slim']['parts'][1]['vertices'][1].__setitem__(0, .75), '非手臂')

    def test_effect_binding_count_and_illegal_bone_fail(self):
        self.rejected(lambda d: d['effect_bindings'].update(flows=[]), '绑定数量')
        self.rejected(lambda d: d['effect_bindings'].update(flows=['left_leg']), '效果骨骼')
        self.rejected(lambda d: d['effects']['glows'][0].update(targets=['missing']), '未知效果')

    def test_motion_group_cannot_cross_bones(self):
        self.rejected(lambda d: d['effects']['motions'][0].update(targets=['coat', 'sleeve']), '跨骨骼')

    def test_effect_configuration_must_equal_author(self):
        data, design = fixture()
        expected = copy.deepcopy(data['effects']);expected['level'] = 5
        with self.assertRaisesRegex(ValueError, '作者效果'):
            check.validate_mesh(data, design, expected)

    def test_resource_reference_cannot_escape_namespace(self):
        for identity in ('other:models/test.json', 'myvillage:../private', 'myvillage:/../private', 'bad', 'myvillage:C:\\private'):
            with self.subTest(identity=identity), self.assertRaisesRegex(ValueError, '非法资源'):
                check.resource(check.ROOT, identity)

    def test_authored_source_is_fifteen_sets_and_exactly_seventy_five_ids(self):
        specs = check.source_designs(check.ROOT)
        self.assertEqual(len(specs), 15)
        self.assertEqual({level: sum(s['level'] == level for s in specs.values()) for level in range(1, 6)}, dict.fromkeys(range(1, 6), 3))
        ids = {f'{identity}_{suffix}' for identity in specs for suffix in (*check.SLOTS.values(), 'ingot')}
        self.assertEqual(len(ids), 75)

    def test_geometry_summary_is_invariant_to_triangle_subdivision(self):
        original = check.geometry_summary([[0, 0, 0], [2, 0, 0], [0, 2, 0]], [0, 1, 2])
        split = check.geometry_summary([[0, 0, 0], [2, 0, 0], [0, 2, 0], [1, 0, 0]], [0, 3, 2, 3, 1, 2])
        self.assertEqual(original['bounds'], split['bounds'])
        self.assertAlmostEqual(original['area'], split['area'])
        for a, b in zip(original['centroid'], split['centroid']):
            self.assertAlmostEqual(a, b)

    def test_uv_mapping_checks_source_including_vertical_flip(self):
        source = {'vertices': [[0, 0, 0, 0, 0], [1, 0, 0, 1, 0], [0, 1, 0, 0, 1]], 'indices': [0, 1, 2]}
        good = [[0, 0, 0, 0, 1], [1, 0, 0, 1, 1], [0, 1, 0, 0, 0]]
        check.validate_uv_source(good, [0, 1, 2], source, False)
        with self.assertRaisesRegex(ValueError, 'UV'):
            check.validate_uv_source(source['vertices'], [0, 1, 2], source, False)

    def test_png_pixels_are_lossless_and_opacity_is_not_premultiplied(self):
        art = {'pixelArt': {'rows': ['0'], 'palette': ['#112233']}, 'opacity': .4}
        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
        def png(alpha):
            return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes([0, 17, 34, 51, alpha]))) + chunk(b'IEND', b'')
        check.validate_png(png(255), art)
        with self.assertRaisesRegex(ValueError, 'alpha'):
            check.validate_png(png(102), art)

    def test_offline_native_plan_cannot_claim_completion(self):
        from tools.immortal_equipment_evidence import plan
        report = plan(['qi_moqing'])
        self.assertEqual(report['completed'], [])
        self.assertEqual(report['selected'], report['unrun'])
        self.assertEqual(report['runtime'], 'not_verified')
        self.assertIn('two_observers', report['unrun'])

    def test_native_launch_classes_must_match_recorded_jar(self):
        from tools.immortal_equipment_evidence import verify_launch_artifacts
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary);(root / 'build/libs').mkdir(parents=True)
            name = 'com/example/myvillage/Example.class'
            actual = root / 'build/classes/java/main' / name
            actual.parent.mkdir(parents=True);actual.write_bytes(b'fixed-class')
            with zipfile.ZipFile(root / 'build/libs/example.jar', 'w') as jar:
                jar.writestr(name, b'fixed-class')
            self.assertEqual(verify_launch_artifacts(root), 1)
            actual.write_bytes(b'changed-class')
            with self.assertRaisesRegex(RuntimeError, '不一致'):
                verify_launch_artifacts(root)


if __name__ == '__main__':
    unittest.main()
