"""Focused giant tiger art invariants: geometry, native UV, server clock and planted paws."""
from __future__ import annotations

import hashlib
import json
import unittest

from tools.beastgen import anim, build, cuboid, quadruped
from tools.beastgen.defs import thunder_tiger
from tools.gen_qingfeng_sword_model import decode_png_rgba


class ThunderTigerArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.built = build.Built('thunder_tiger')
        cls.model = cls.built.model
        cls.clips = {c.name: c for c in cls.built.clips}
        cls.boss = json.loads((build.RESOURCES / 'data/myvillage/beast/thunder_tiger_boss.json').read_text())

    def test_owned_native_source_and_runtime_pixel_parity(self):
        p = thunder_tiger.ART / 'source_texture.png'
        source = decode_png_rgba(p.read_bytes())
        target = decode_png_rgba(self.built.outputs()[build.paths('thunder_tiger')['texture']])
        self.assertEqual(source, target)
        self.assertEqual((256, 128), source[:2])
        self.assertEqual([], self.model.problems())
        self.assertEqual(2.0, self.model.scale)
        self.assertEqual(39, len(self.model.cubes()))

    def test_six_block_feline_body_and_three_block_shoulders(self):
        mats = self.model.rest_matrices()
        points = []
        for bone, cube in self.model.cubes():
            if bone.name.startswith(('tail', 'horn', 'ear')):
                continue
            points += [cuboid.mat_apply(mats[bone.name], tuple(cube.origin[k]+cube.size[k]*((i>>k)&1) for k in range(3))) for i in range(8)]
        length=(max(p[2] for p in points)-min(p[2] for p in points))/8
        self.assertGreater(length,5.8)
        self.assertLess(length,6.4)
        _, chest = self.model.cube('barrel')
        self.assertEqual((22.0, 16.0, 20.0), chest.size)
        _, skull = self.model.cube('skull')
        _, muzzle = self.model.cube('muzzle')
        self.assertGreater(skull.size[0], skull.size[2])
        self.assertLess(muzzle.size[2], muzzle.size[0]/2)
        self.assertEqual('horn', self.model.bone('horn_tip').parent)
        self.assertEqual('horn', self.model.bone('horn_base').parent)

    def test_uv_truth_faces_alpha_and_no_warm_coat(self):
        truth = json.loads((thunder_tiger.ART/'uv_truth.json').read_text())
        faces = json.loads((thunder_tiger.ART/'texture_faces.json').read_text())
        self.assertEqual(len(truth['parts'])*6,len(faces['faces']))
        used = {(t.u,t.v) for t in self.built.texels}
        opaque = {(x,y) for y,row in enumerate(self.built.texture) for x,p in enumerate(row) if p[3]}
        self.assertEqual(used,opaque)
        for row in self.built.texture:
            for r,g,b,a in row:
                self.assertIn(a,(0,255))
                if a:
                    self.assertLessEqual(r-g,30)
                else:
                    self.assertEqual((0,0,0),(r,g,b))

    def test_all_attack_clips_follow_actual_server_ticks(self):
        self.assertEqual({'idle','walk','run','claw','pounce','thunder_stomp','roar','stagger','hurt','horn_exposed','horn_break','phase_change','death'},set(self.clips))
        names={b.name for b in self.model.bones}
        for move in self.built.server['moves']:
            clip=self.clips[move['animation']]
            self.assertFalse(clip.loop)
            self.assertAlmostEqual(move['total_ticks']/20,clip.length)
        for clip in self.clips.values():
            for bone,target,frames in clip.channels():
                self.assertIn(bone,names)
                self.assertEqual(len(frames),len({f[0] for f in frames}))
                self.assertTrue(all(0<=f[0]<=clip.length for f in frames))
        for name in ('horn_break','phase_change','death'):
            self.assertAlmostEqual(self.boss['presentation'][name+'_ticks']/20,self.clips[name].length)

    def test_gait_reachable_and_paws_do_not_sink(self):
        pounce=next(m for m in self.built.server['moves'] if m['animation']=='pounce')
        flight=quadruped.move_flight(pounce)
        for name,clip in self.clips.items():
            if name in ('walk','run'):
                self.assertLess(clip.notes['ik_residual'],0.001,name)
            for i in range(61):
                t=clip.length*i/60
                mats=anim.posed_matrices(self.model,anim.clip_offsets(clip.channels(),clip.length,clip.loop,t))
                lift=flight.at(t*20)[1] if name=='pounce' else 0
                for bone,cube in self.model.cubes():
                    if not cube.name.endswith('_paw_cube'):
                        continue
                    bottom=max(cuboid.mat_apply(mats[bone.name],tuple(cube.origin[k]+cube.size[k]*((j>>k)&1) for k in range(3)))[1] for j in range(8))
                    height=(24-bottom)/8+lift
                    # Small cuboid toe rotation between samples stays below one native half texel.
                    self.assertGreater(height,-0.055,(name,t,bone.name,height))

    def test_exposed_head_lowering_matches_real_hit_part(self):
        clip=self.clips['horn_exposed']
        frame=next(frames for bone,target,frames in clip.channels() if bone=='head' and target=='position')
        delta=-frame[0][1][1]*self.model.scale/16
        self.assertAlmostEqual(self.boss['horn']['head_center_y']-self.boss['horn']['exposed_head_center_y'],delta)

    def test_limited_phase_two_emission(self):
        lit=[t for t in self.built.texels if thunder_tiger.phase2_glow(t)]
        self.assertGreater(len(lit),0)
        self.assertLess(len(lit),len(self.built.texels)*0.03)
        self.assertTrue(all(t.cube in ('barrel','haunch') for t in lit))

    def test_intersecting_joint_surfaces_are_not_coplanar(self):
        # Wider cuffs overlap the shin, avoiding identical X planes during pitch animation.
        for side in ('right','left'):
            shin=self.model.cube('front_'+side+'_shin')[1]
            wrist=self.model.cube('front_'+side+'_wrist')[1]
            self.assertGreater(wrist.size[0],shin.size[0])
        mats=self.model.rest_matrices()
        jaw_bone,jaw=self.model.cube('jaw_cube')
        muzzle_bone,muzzle=self.model.cube('muzzle')
        jaw_z=cuboid.mat_apply(mats[jaw_bone.name],jaw.origin)[2]
        muzzle_z=cuboid.mat_apply(mats[muzzle_bone.name],muzzle.origin)[2]
        self.assertGreater(jaw_z,muzzle_z+0.1)

    def test_shared_pipeline_preserves_wolf(self):
        wolf=build.Built('demon_wolf')
        self.assertEqual(4,len(wolf.outputs()))
        for path,data in wolf.outputs().items():
            self.assertTrue(build.same_file(path,data),str(path))


if __name__=='__main__':
    unittest.main()
