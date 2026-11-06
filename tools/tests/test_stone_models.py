"""检查实际导出的连续表面、骨骼绑定、UV 和物品 OBJ 回读。"""
import json
import math
import unittest
from pathlib import Path

from tools.legacy_encounters import stone_models as model
from tools.beastgen.build import Built
from tools.equipment.mesh import read_obj


class StoneModelTest(unittest.TestCase):
    def test_closed_surfaces_have_volume_and_no_zero_area_faces(self):
        volumes = {}
        for bone, _, vertices in model.guardian_surface().triangles():
            a,b,c=[v[:3] for v in vertices]
            normal=model._cross(a,b,c)
            self.assertGreater(sum(v*v for v in normal),1e-10)
            volumes[bone]=volumes.get(bone,0)+sum(a[k]*model._cross((0,0,0),b,c)[k] for k in range(3))/6
        self.assertEqual(8,len(volumes))
        self.assertTrue(all(v>100 for v in volumes.values()),volumes)

    def test_shape_is_tapered_and_stays_inside_registered_height_and_width(self):
        surface=model.guardian_surface();points=[p for f in surface.faces for p in f.points]
        self.assertEqual(-64,min(p[1] for p in points));self.assertEqual(0,max(p[1] for p in points))
        self.assertLessEqual(max(p[0] for p in points)-min(p[0] for p in points),2.8*16)
        torso=[p for f in surface.faces if f.bone=='body' for p in f.points]
        widths={y:max(p[0] for p in torso if p[1]==y)-min(p[0] for p in torso if p[1]==y) for y in {p[1] for p in torso}}
        self.assertGreater(len(set(widths.values())),4)

    def test_runtime_mesh_coordinates_match_authored_bone_local_vertices(self):
        b=Built('stele_guardian');expected=model.guardian_document(b.model)
        actual=json.loads((model.RES/'assets/myvillage/beast/stele_guardian_surface.json').read_text())
        self.assertEqual(expected,actual)
        for p in actual['parts']:
            self.assertIn(p['bone'],{b.name for b in b.model.bones})
            for tri in p['triangles']:
                self.assertEqual(15,len(tri));self.assertTrue(all(math.isfinite(v) for v in tri))
                self.assertTrue(all(0<=tri[i]<=1 for i in (3,4,8,9,13,14)))

    def test_ruler_obj_round_trip_keeps_every_authored_face(self):
        outputs=model.ruler_outputs();base=model.RES/'assets/myvillage/models/item'
        faces=read_obj(outputs[base/'zhenyue_ruler.obj'],outputs[base/'zhenyue_ruler.mtl'])
        self.assertEqual(len(model.ruler_surface().faces),len(faces))
        for actual,authored in zip(faces,model.ruler_surface().faces):
            expected=[(x+8,y,z+8) for x,y,z in authored.points]
            if len(expected)==3:expected.append(expected[-1])
            self.assertEqual(4,len(actual['points']))
            for p,q in zip(actual['points'],expected):
                for a,b in zip(p,q):self.assertAlmostEqual(a,b,places=5)
            self.assertEqual('myvillage:item/zhenyue_ruler',actual['texture'])

    def test_item_preview_reads_the_actual_obj(self):
        from tools.combat_preview.item_model import Assets,ItemModel
        asset=ItemModel(Assets([model.RES],None),'myvillage:item/zhenyue_ruler')
        self.assertEqual([],asset.base.res.errors)
        self.assertEqual(len(model.ruler_surface().faces),len(asset.base.quads))
        points=[p for q in asset.base.quads for p in q.pos]
        self.assertAlmostEqual(29,max(p[1] for p in points),places=5)
        self.assertAlmostEqual(0,min(p[1] for p in points),places=5)

    def test_ruler_inscription_is_not_mirrored_on_either_broad_face(self):
        for face in model.ruler_surface().faces:
            if face.panel not in ('ruler.right','ruler.left'):continue
            z=[p[2] for p in face.points];u=[t[0] for t in face.uv]
            correlation=sum((a-sum(z)/len(z))*(b-sum(u)/len(u)) for a,b in zip(z,u))
            self.assertLess(correlation,0) if face.panel=='ruler.right' else self.assertGreater(correlation,0)

    def test_uv_panels_do_not_overlap_and_have_pixel_gutters(self):
        for surface in (model.guardian_surface(),model.ruler_surface()):
            boxes=[]
            for p in surface.panels.values():
                x,y,w,h=p.rect
                self.assertGreaterEqual(x,2);self.assertGreaterEqual(y,2)
                self.assertLessEqual(x+w+2,surface.texture_size[0]);self.assertLessEqual(y+h+2,surface.texture_size[1])
                box=(x-2,y-2,x+w+2,y+h+2)
                for a,b,c,d in boxes:self.assertTrue(box[2]<=a or c<=box[0] or box[3]<=b or d<=box[1],p.name)
                boxes.append(box)

    def test_attack_motion_keeps_the_torso_connected_to_the_hips(self):
        from tools.beastgen import anim,cuboid
        built=Built('stele_guardian')
        points={part['bone']:[tri[i:i+3] for tri in part['triangles'] for i in (0,5,10)] for part in built.surface['parts']}
        for clip in built.clips:
            if clip.name not in ('quake','slam'):continue
            channels=clip.channels()
            for tick in range(round(clip.length*20)+1):
                offsets=anim.clip_offsets(channels,clip.length,clip.loop,tick/20)
                matrices=anim.posed_matrices(built.model,offsets)
                torso_bottom=max(cuboid.mat_apply(matrices['body'],p)[1] for p in points['body'])
                hip_top=min(cuboid.mat_apply(matrices[bone],p)[1] for bone in ('leg_right','leg_left') for p in points[bone])
                self.assertGreaterEqual(torso_bottom+1.0,hip_top,(clip.name,tick,torso_bottom,hip_top))


if __name__=='__main__':unittest.main()
