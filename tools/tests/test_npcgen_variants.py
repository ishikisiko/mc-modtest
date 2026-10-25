"""正式组合、已确认基准和运行时目录之间的回归检查。"""
import hashlib
import json
import unittest
from pathlib import Path

from tools.npcgen import build, catalog, recolour, roles

ROOT = Path(__file__).resolve().parents[2]


class FemaleVariantsTest(unittest.TestCase):
    def test_retained_wardrobe_matches_archived_authored_outputs(self):
        from tools.npcgen.female import definition
        archived = json.loads((ROOT / 'src/test/resources/npc_retained_outfits.json').read_text())
        self.assertEqual({'01', '02', '03', '05'}, set(archived['outfits']))
        for outfit, record in archived['outfits'].items():
            name = 'cultivator_f_novice_02_a_' + outfit
            built = build.Built(name, authored=definition(name, motion_cover=False))
            for path, output in built.outputs().items():
                old_name = path.name.replace(name, 'cultivator_f_novice')
                self.assertEqual(record['output_sha256'][old_name], hashlib.sha256(output).hexdigest(), (outfit, old_name))

    def test_approved_outfits_remain_byte_identical(self):
        # 来自用户已确认的 a_after / b_retained；不会由当前生成器自动更新。
        expected = {
            'pants': {'model': '9961ad8fe6e4af7fe5cd833b68cd6ca8652cafc868e716242ac08573d8463184',
                      'texture': 'e954029d09280c8a7a601e136496b63a8b14a551fcd14c6612d8dfb2bf104c60',
                      'roles': '74e83cf06c9a00f7c52a01e6d4d63da53df64cb6635e7170d1ca8979d041f8a6'},
            'robe': {'model': '94baaa9ca81687027cddd78f0439da67259dd8477b0f378a146b4909da6942f7',
                     'texture': '418a34897dd37ce7134d746cb72e182dc00390e46528636681c6ffff3f77d8eb',
                     'roles': '9960899d2ead4077cd897756a787d0b45540874654c397360de0ff3404d78f79'}}
        for outfit, hashes in expected.items():
            name = 'cultivator_f_novice_02_a_' + outfit
            from tools.npcgen.female import definition
            # 回放迁入的已确认静止原型；正式版另有不改变静止轮廓的内藏步态遮盖。
            built = build.Built(name, authored=definition(name, motion_cover=False))
            outputs = built.outputs()
            for kind, path in build.paths(name).items():
                digest = hashes.get(kind, '73da2211302322bb069256c722141df6205f13117e13f1757c42e273590a442d')
                self.assertEqual(digest, hashlib.sha256(outputs[path]).hexdigest(), (outfit, kind))

    def test_all_combinations_are_generated_recolourable_and_keep_runtime_skeleton(self):
        base = build.Built('cultivator_f_novice')
        base_bones = {b.name: (b.parent, b.pivot, b.rotation) for b in base.model.bones
                      if b.name in ('root', 'body', 'head', 'arm_right', 'arm_left', 'forearm_right',
                                    'forearm_left', 'leg_right', 'leg_left', 'hair_back')}
        for spec in catalog.variants():
            name = 'cultivator_' + spec['look']
            with self.subTest(look=spec['look']):
                built = build.Built(name)
                self.assertEqual([], built.model.problems())
                self.assertEqual(base.model.scale, built.model.scale)
                self.assertEqual(base.d.HITBOX, built.d.HITBOX)
                self.assertEqual(base.anim_doc, built.anim_doc)
                for bone in built.model.bones:
                    if bone.name in base_bones:
                        self.assertEqual(base_bones[bone.name], (bone.parent, bone.pivot, bone.rotation))
                self.assertTrue(all(p[3] in (0, 255) for row in built.texture for p in row))
                if spec['outfit'] == 'pants':
                    for side in ('right', 'left'):
                        cube = built.model.cube('leg_inner_' + side)[1]
                        self.assertEqual(-2, cube.origin[1])
                        self.assertEqual(13, cube.origin[1] + cube.size[1])
                else:
                    hidden = [t for t in built.texels if t.cube == 'leg_inner_right']
                    self.assertTrue(hidden and all(built.texture[t.v][t.u][3] == 0 for t in hidden))
                for path, expected in built.outputs().items():
                    self.assertEqual(expected, path.read_bytes(), str(path))
                for hair, eye in [('silver', 'water'), ('grey_ink_violet', 'wood')]:
                    self.assertEqual(roles.repaint(built, recolour.hair_ramp7(recolour.hair_ramp5(hair)),
                                                   recolour.eye_ramp(eye)[:3]),
                                     recolour.compose(built.texture, built.roles, hair, eye))
                # 刘海 NORTH 面的最低边必须在相应眼睛/睫毛上方。
                face = catalog.load()['faces'][spec['face']]['pixels']
                for _, cube in built.model.cubes():
                    if not cube.name.startswith('fringe_study_'):
                        continue
                    bottom = 46 - cube.origin[1] - cube.size[1]
                    left = int(cube.origin[0] + 7)
                    for x in range(left, left + int(cube.size[0])):
                        for y, row in enumerate(face):
                            if row[x] in 'LWCDG':
                                self.assertGreaterEqual(bottom, 60 - y, (spec, x, y))

    def test_catalogue_is_complete_and_rejects_removed_faces(self):
        data = catalog.load()
        self.assertEqual({'01', '02', '03'}, set(data['faces']))
        self.assertEqual(72, len(data['variants']))
        self.assertEqual({'01', '02', '03', '05', 'pants', 'robe'}, set(data['outfits']))
        self.assertEqual(catalog.runtime_document(), json.loads((ROOT / catalog.RUNTIME).read_text()))
        catalog.write(check=True)

    def test_production_factory_has_no_report_dependency(self):
        source = (ROOT / catalog.FACTORY).read_text()
        self.assertNotIn('reports/', source)
        self.assertNotIn('build.definition =', source)


if __name__ == '__main__':
    unittest.main()
