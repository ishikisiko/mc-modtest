"""男性组合：已确认原型、部件独立选择、材质和旧骨架保护。"""
import hashlib
import json
import unittest
from pathlib import Path
from tools.npcgen import build, male, male_catalog, recolour, roles

ROOT = Path(__file__).resolve().parents[2]


class MaleVariantsTest(unittest.TestCase):
    def test_twenty_approved_pairings_replay_exactly(self):
        archive = json.loads((ROOT/'src/test/resources/npc_male_approved.json').read_text())
        self.assertEqual(20,len(archive['checks']))
        for item in archive['checks']:
            name=f"cultivator_m_{item['head']}_{item['head']}_{item['outfit'].lower()}"
            b=build.Built(name,authored=male.definition(name,motion_cover=False))
            for path,data in b.outputs().items():
                key=path.name.replace(name,'cultivator')
                self.assertEqual(item['source_outputs'][key],hashlib.sha256(data).hexdigest(),(name,key))

    def test_all_independent_combinations_load_and_recolour_without_skeleton_drift(self):
        baseline=build.Built('cultivator')
        for item in male_catalog.variants():
            name='cultivator_'+item['look']
            with self.subTest(look=item['look']):
                b=build.Built(name)
                self.assertEqual([],b.model.problems())
                self.assertEqual(.5,b.model.scale)
                self.assertEqual(baseline.d.HITBOX,b.d.HITBOX)
                self.assertEqual(baseline.anim_doc,b.anim_doc)
                for bone in baseline.model.bones:
                    actual=b.model.bone(bone.name)
                    self.assertEqual((bone.parent,bone.pivot,bone.rotation),(actual.parent,actual.pivot,actual.rotation))
                self.assertTrue(all(p[3] in (0,255) for row in b.texture for p in row))
                for path,data in b.outputs().items():
                    self.assertEqual(data,path.read_bytes(),path)
                for hair,eye in [('chestnut','wood'),('silver','water'),('grey_ink_violet','fire')]:
                    self.assertEqual(roles.repaint(b,recolour.hair_ramp7(recolour.hair_ramp5(hair)),recolour.eye_ramp(eye)[:3]),
                                     recolour.compose(b.texture,b.roles,hair,eye))
                head=male.compose_head(item['face'],item['hair'])
                for y,row in enumerate(head['pixels']):
                    for x,pixel in enumerate(row):
                        if pixel in 'LWCDG': self.assertLessEqual(head['fringe'][x],y)
                if item['outfit'] in ('A','C'):
                    for side in ('right','left'):
                        self.assertEqual('vest_front_'+side,b.model.bone('male_robe_front_'+side).parent)
                        self.assertIsNotNone(b.model.cube('male_inner_trouser_'+side))

    def test_catalogue_is_cartesian_and_identity_has_no_gender_crossover(self):
        data=male_catalog.load()
        self.assertEqual(100,len(data['variants']))
        expected={(f,h,o) for f in data['faces'] for h in data['hair'] for o in data['outfits']}
        self.assertEqual(expected,{(v['face'],v['hair'],v['outfit']) for v in data['variants']})
        looks={v['look'] for v in data['variants']}
        vectors=json.loads((ROOT/'src/test/resources/npc_male_identity.json').read_text())
        for v in vectors:
            self.assertEqual(v['look'],male_catalog.for_identity(v['person_id'],v['birth_day']))
            self.assertIn(v['look'],looks)

    def test_production_sources_do_not_import_studies(self):
        for source in ('male.py','male_clothing.py','male_catalog.py'):
            text=(ROOT/'tools/npcgen'/source).read_text()
            self.assertNotIn('reports/',text)
            self.assertNotIn('make_heads',text)


if __name__=='__main__': unittest.main()
