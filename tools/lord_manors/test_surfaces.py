import unittest

from tools.lord_manors.check_models import audit_model
from tools.lord_manors.generate import model
from tools.lord_manors.surfaces import FACES,cropped_uv


DEFS={role:{'id':'manor_test_'+role,'role':role,'opaque':role!='window'}
      for role in ('wall','wood','plaque_0','window')}


class SurfaceTest(unittest.TestCase):
    def build(self,profile):
        result=model(profile,DEFS,'test')
        check=audit_model(result,profile,{'wall','wood','plaque_0'})
        self.assertEqual(0,check['overlap_units'])
        self.assertEqual(0,check['opaque_coverage_errors'])
        return result

    def test_simple_cube_keeps_six_original_faces(self):
        result=self.build([(0,0,0,16,16,16,'wall')])
        self.assertEqual(1,len(result['elements']))
        self.assertEqual(6,len(result['elements'][0]['faces']))
        self.assertEqual([0,0,16,16],result['elements'][0]['faces']['up']['uv'])

    def test_real_wall_and_local_wood_strip_have_one_surface_per_pixel(self):
        result=self.build([(0,0,0,1,16,16,'wood'),(0,0,0,16,16,16,'wall')])
        areas={role:0 for role in ('#wall','#wood')}
        for e in result['elements']:
            if 'up' in e['faces']:
                areas[e['faces']['up']['texture']]+=(e['to'][0]-e['from'][0])*(e['to'][2]-e['from'][2])
        self.assertEqual({'#wall':240,'#wood':16},areas)

    def test_different_material_enclosed_box_has_no_rendered_faces(self):
        result=self.build([(0,0,0,16,16,16,'wall'),(4,4,4,12,12,12,'wood')])
        self.assertEqual({'#wall'},{f['texture'] for e in result['elements'] for f in e['faces'].values()})

    def test_touching_boxes_drop_the_shared_internal_plane(self):
        result=self.build([(0,0,0,8,16,16,'wall'),(8,0,0,16,16,16,'wood')])
        for e in result['elements']:
            self.assertFalse('east' in e['faces'] and e['to'][0]==8)
            self.assertFalse('west' in e['faces'] and e['from'][0]==8)

    def test_offset_floor_and_beam_keep_the_exact_opaque_union(self):
        self.build([(0,0,0,16,8,16,'wall'),(4,6,2,12,16,14,'wood')])

    def test_glass_keeps_opaque_surfaces_visible_inside_it(self):
        result=self.build([(0,0,0,16,16,16,'window'),(4,4,4,12,12,12,'wood')])
        self.assertEqual(6,sum(len(e['faces']) for e in result['elements'] if any(f['texture']=='#wood' for f in e['faces'].values())))
        self.build([(0,0,0,16,16,16,'window'),(0,0,0,16,16,16,'wall')])

    def test_crop_respects_all_six_vanilla_face_uv_orientations(self):
        expected={'west':[0,8,8,16],'east':[8,8,16,16],'down':[0,8,8,16],
                  'up':[0,0,8,8],'north':[8,8,16,16],'south':[0,8,8,16]}
        for face in FACES:
            with self.subTest(face=face[0]):
                self.assertEqual(expected[face[0]],cropped_uv((0,0,0,16,16,16),face,(0,0,8,8),(0,0,16,16)))

    def test_engraved_uv_is_cropped_without_stretching_the_inscription(self):
        face=next(f for f in FACES if f[0]=='north')
        self.assertEqual([6,6,10,9],cropped_uv((0,0,0,16,16,16),face,(0,0,8,8),(2,3,10,9)))
        profile=[(0,0,0,16,16,16,'wall'),(0,0,0,8,16,2,'plaque_0',2,3,10,9)]
        result=self.build(profile)
        north=[f['uv'] for e in result['elements'] for name,f in e['faces'].items() if name=='north' and f['texture']=='#plaque_0']
        self.assertEqual([[2,3,10,9]],north)


if __name__=='__main__':unittest.main()
