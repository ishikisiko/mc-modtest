import copy
import unittest
from tools.gen_undead_roster import catalogue,validate,beast_data
class UndeadRosterTest(unittest.TestCase):
    def test_whole_region_family_matrix(self):
        c=validate(catalogue());self.assertEqual(18,len(c['variants']))
        for v in c['variants']:
            d=beast_data(v,c);self.assertEqual(2,len(d['moves']))
            for original,move in zip(v['moves'],d['moves']):
                self.assertLess(move['turn_lock_tick'],original['strike_ticks'][0])
                self.assertEqual([original['strike_ticks'][0],original['strike_ticks'][-1]],move['active_ticks'])
                self.assertNotIn('projectile',move)
    def test_missing_region_and_duplicate_are_rejected(self):
        for mutate in [lambda c:c['variants'].pop(),lambda c:c['variants'].append(copy.deepcopy(c['variants'][0]))]:
            c=catalogue();mutate(c)
            with self.assertRaises(AssertionError):validate(c)
    def test_damage_during_windup_and_missing_recovery_are_rejected(self):
        c=catalogue();c['variants'][0]['moves'][0]['strike_ticks']=[1]
        with self.assertRaises(AssertionError):validate(c)
        c=catalogue();m=c['variants'][0]['moves'][0];m['total_ticks']=m['strike_ticks'][-1]+1
        with self.assertRaises(AssertionError):validate(c)

    def test_torso_stays_attached_to_hips_during_attacks(self):
        from tools.beastgen.build import Built
        from tools.beastgen.anim import clips_from_json,clip_offsets,posed_matrices
        from tools.beastgen.cuboid import mat_apply
        from math import dist
        for v in catalogue()['variants']:
            b=Built(v['id']);torso=b.model.cube('torso')[1]
            clips=clips_from_json(b.anim_doc)
            for action in v['moves']:
                length,loop,channels=clips[action['animation']]
                for tick in [action['turn_lock_tick'],*action['strike_ticks'],action['total_ticks']-1]:
                    m=posed_matrices(b.model,clip_offsets(channels,length,loop,tick/20))
                    waist=mat_apply(m['body'],(0,torso.origin[1]+torso.size[1],0))
                    hips=[mat_apply(m[n],(0,0,0)) for n in ['leg_left','leg_right']]
                    center=tuple((a+c)/2 for a,c in zip(*hips))
                    self.assertLess(dist(waist,center),.001,(v['id'],action['animation'],tick))

    def test_bow_stays_vertical_at_every_release(self):
        from tools.beastgen.build import Built
        from tools.beastgen.anim import clips_from_json,clip_offsets,posed_matrices
        from tools.beastgen.cuboid import mat_apply_dir
        for v in catalogue()['variants']:
            if v['family']!='archer':continue
            b=Built(v['id']);length,loop,channels=clips_from_json(b.anim_doc)['shot']
            for tick in v['moves'][0]['strike_ticks']:
                m=posed_matrices(b.model,clip_offsets(channels,length,loop,tick/20))
                self.assertGreater(mat_apply_dir(m['bow'],(0,1,0))[1],.99,(v['id'],tick))
