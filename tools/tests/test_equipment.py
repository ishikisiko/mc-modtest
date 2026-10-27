"""Authored identity, attachment/UV boundaries and reusable second-set authoring."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from tools.equipment import catalogue, export, geometry
from tools.equipment.__main__ import scaffold


class EquipmentAuthoringTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.specs, cls.materials = catalogue.load()
        cls.armor = next(s for s in cls.specs if s['kind'] == 'armor_set')

    def test_unrelated_bone_rejected_before_export(self):
        data=copy.deepcopy(self.armor)
        data['items'][0]['boxes'][0]['bone']='left_leg'
        with self.assertRaisesRegex(ValueError,'不允许挂到'):catalogue.validate(data,self.materials)

    def test_invalid_geometry_and_material_rejected(self):
        for field,value in [('size',[1,0,1]),('from',[float('nan'),0,0]),('material','missing')]:
            with self.subTest(field=field):
                data=copy.deepcopy(self.armor);data['items'][0]['boxes'][0][field]=value
                with self.assertRaises(ValueError):catalogue.validate(data,self.materials)

    def test_duplicate_slot_and_box_rejected(self):
        data=copy.deepcopy(self.armor);data['items'][1]['slot']='head'
        with self.assertRaises(ValueError):catalogue.validate(data,self.materials)
        data=copy.deepcopy(self.armor);data['items'][0]['boxes'].append(data['items'][0]['boxes'][0])
        with self.assertRaises(ValueError):catalogue.validate(data,self.materials)

    def test_texture_overflow_is_an_error(self):
        data=copy.deepcopy(self.armor);data['texture_size']=64
        with self.assertRaisesRegex(ValueError,'UV'):export.compile_armor(data,self.materials)

    def test_slim_fit_keeps_inner_arm_edge_and_has_its_own_uv(self):
        box={'bone':'right_arm','from':[-3,-2,-2],'size':[4,12,4],'fit':'arm'}
        slim=export.fit_box(box,'slim')
        self.assertEqual(slim['from'],[-2,-2,-2]);self.assertEqual(slim['size'],[3,12,4])
        self.assertEqual(box['size'],[4,12,4])
        model,_=export.compile_armor(self.armor,self.materials)
        a=model['profiles']['standard'][1]['boxes'];b=model['profiles']['slim'][1]['boxes']
        arms=[(x,y) for x,y in zip(a,b) if x['bone'].endswith('_arm')]
        self.assertTrue(arms)
        self.assertTrue(all(x['uv'] != y['uv'] and x['size'][0]*.75 == y['size'][0] for x,y in arms))

    def test_modelpart_uv_rectangles_use_texel_density(self):
        faces=export.cube_faces([4,8,2],[2,3],2)
        self.assertEqual(faces[3][1],[6,7,14,23])
        self.assertEqual(faces[5][1],[18,7,26,23])
        from tools.equipment.preview import box_quads
        box={'name':'probe','from':[0,0,0],'size':[4,8,2],'uv':[2,3]}
        front=box_quads(box,None,64,2)[3]
        self.assertEqual(front.uv[0],(14/4,7/4))

    def test_shared_helpers_preserve_shipped_weapon_outputs(self):
        from tools import gen_qingfeng_sword_model,gen_lingxiao_spear_model,gen_xuantie_gauntlet_model
        for generator in (gen_qingfeng_sword_model,gen_lingxiao_spear_model,gen_xuantie_gauntlet_model):
            for path,data in generator.outputs().items():
                with self.subTest(path=path.name):self.assertEqual(path.read_bytes(),data)
        self.assertIs(gen_qingfeng_sword_model.display_point,geometry.display_point)

    def test_second_set_needs_no_java_edits_and_starts_as_draft(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            shutil.copytree(catalogue.ROOT/'equipment',root/'equipment')
            lang=root/'src/main/resources/assets/myvillage/lang/zh_cn.json'
            lang.parent.mkdir(parents=True);lang.write_text('{}')
            dest=scaffold(root,self.specs,self.armor['id'],'jade_trial','青玉')
            spec=json.loads((dest/'equipment.json').read_text())
            self.assertFalse(spec['enabled'])
            before=export.outputs(root)
            path=root/'src/main/resources/data/myvillage/equipment/catalogue.json'
            self.assertEqual(len(json.loads(before[path])['items']),4)
            spec['enabled']=True
            (dest/'equipment.json').write_text(json.dumps(spec))
            after=export.outputs(root)
            self.assertEqual(len(json.loads(after[path])['items']),8)
            self.assertIn(root/'src/main/resources/assets/myvillage/models/item/jade_trial_helmet.json',after)
            with self.assertRaisesRegex(ValueError,'目标已存在'):scaffold(root,self.specs,self.armor['id'],'jade_trial','青玉')

    def test_disabling_a_registered_set_cannot_remove_saved_item_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            shutil.copytree(catalogue.ROOT/'equipment',root/'equipment')
            lang=root/'src/main/resources/assets/myvillage/lang/zh_cn.json'
            lang.parent.mkdir(parents=True);lang.write_text('{}')
            export.write_or_check(root)
            path=root/'equipment/xuantie_armor/equipment.json'
            spec=json.loads(path.read_text());spec['enabled']=False;path.write_text(json.dumps(spec))
            with self.assertRaisesRegex(ValueError,'不能通过'):export.write_or_check(root)

    def test_custom_uv_armor_is_excluded_from_transitive_vanilla_trim_tag(self):
        outputs=export.outputs()
        tag=json.loads(outputs[catalogue.ROOT/'src/main/resources/data/minecraft/tags/item/trimmable_armor.json'])
        self.assertFalse(tag['replace']);self.assertEqual(tag['values'],[])
        self.assertEqual(set(tag['remove']),{i['id'] for i in self.armor['items']})


if __name__=='__main__':unittest.main()
