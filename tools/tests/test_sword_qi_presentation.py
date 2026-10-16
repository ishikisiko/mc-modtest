import json
import math
from pathlib import Path
import unittest
from tools.gen_sword_pal_anims import BASIC_SWORD_MOVES, resolve, skeleton, shaft_body_gaps
ROOT=Path(__file__).resolve().parents[2]

class SwordQiPresentationTest(unittest.TestCase):
    def test_release_uses_horizontal_pose_at_forward_crossing(self):
        skill=json.loads((ROOT/'src/main/resources/data/myvillage/combat/skill/jianqi_jue.json').read_text())
        poses=BASIC_SWORD_MOVES[skill['feedback_move']]
        self.assertEqual(poses.cut_path,'left_to_right')
        keys=poses.keys;release=next(k for k in keys if k.tick==skill['release_ticks'])
        self.assertLess(abs(release.blade[0]),15,'Release when blade points through the forward plane')
        self.assertLess(abs(release.blade[1]),10,'Horizontal elevation, not a descending cut')
        before=max((k for k in keys if k.tick<release.tick),key=lambda k:k.tick)
        after=min((k for k in keys if k.tick>release.tick),key=lambda k:k.tick)
        self.assertLess(before.blade[0],0);self.assertGreater(after.blade[0],0)
        rig=json.loads((ROOT/'src/main/resources/assets/myvillage/combat/qingfeng_first_person.json').read_text())
        fp=rig['moves'][skill['feedback_move']]
        self.assertLessEqual(abs(fp['contact']-skill['release_ticks']),.2,
                             'First-person contact and third-person forward crossing must release together')
        planes=[k['plane'] for k in fp['keys'] if 'plane' in k]
        self.assertTrue(planes);self.assertTrue(all(p==0 for p in planes))
    def test_charge_holds_left_before_the_horizontal_strike(self):
        skill=json.loads((ROOT/'src/main/resources/data/myvillage/combat/skill/jianqi_jue.json').read_text())
        pose=next(k for k in BASIC_SWORD_MOVES[skill['feedback_move']].keys if k.tick==skill['charge_pose_ticks'])
        self.assertEqual(pose.phase,'coil')
        keys=BASIC_SWORD_MOVES[skill['feedback_move']].keys
        held=resolve(keys)[keys.index(pose)];points=skeleton(held)
        grip,tip=points['grip'],points['tip']
        self.assertLess(grip[0],-.15, 'Grip stays on the left of the body')
        self.assertTrue(.8 < grip[1] < 1.1, 'Grip stays at waist height')
        self.assertLess(tip[2]-grip[2],-.8, 'Blade points behind the player')
        self.assertLess(abs(tip[1]-grip[1]),.08, 'Held blade stays nearly horizontal')
        self.assertGreater(shaft_body_gaps(held,points)['torso'],.5, 'Blade clears the torso')
        self.assertLess(skill['charge_pose_ticks'],skill['release_ticks'])
        rig=json.loads((ROOT/'src/main/resources/assets/myvillage/combat/qingfeng_first_person.json').read_text())
        fp=rig['moves'][skill['feedback_move']]
        self.assertLess(skill['charge_pose_ticks'],fp['strike'][0])
        self.assertLess(fp['strike'][0],fp['contact'])
        # A long hold samples between these two keys. Check the actual blade axis,
        # not the visible screen diagonal (the tip should be behind the camera).
        pose=dict(rig['neutral'])
        for key in fp['keys'][1:3]:
            pose.update(key)
            elevation=math.radians(pose['lift'])
            forward=-math.sin(elevation)*math.cos(math.radians(pose['sweep']+pose['lead']))
            self.assertLess(forward,-.8)
            self.assertLess(abs(math.cos(elevation)),.1)

    def test_art_profile_has_a_thin_rim_and_separated_short_trails(self):
        p=json.loads((ROOT/'src/main/resources/assets/myvillage/combat/sword_qi_wave.json').read_text())
        self.assertTrue(all(b['thickness']<=.2 for b in p['bands']))
        self.assertGreater(p['bands'][0]['color'][3],220)
        self.assertEqual(p['bands'][-1]['color'][3],0)
        intervals=sorted((w['start'],w['end']) for w in p['wisps'])
        self.assertTrue(all(a[1]<b[0] for a,b in zip(intervals,intervals[1:])))
        self.assertTrue(all(w['depth']>p['bands'][-1]['depth'] for w in p['wisps']))
if __name__=='__main__':unittest.main()
