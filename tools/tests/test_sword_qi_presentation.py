import json
from pathlib import Path
import unittest
from tools.gen_sword_pal_anims import BASIC_SWORD_MOVES
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
    def test_art_profile_has_a_thin_rim_and_separated_short_trails(self):
        p=json.loads((ROOT/'src/main/resources/assets/myvillage/combat/sword_qi_wave.json').read_text())
        self.assertTrue(all(b['thickness']<=.2 for b in p['bands']))
        self.assertGreater(p['bands'][0]['color'][3],220)
        self.assertEqual(p['bands'][-1]['color'][3],0)
        intervals=sorted((w['start'],w['end']) for w in p['wisps'])
        self.assertTrue(all(a[1]<b[0] for a,b in zip(intervals,intervals[1:])))
        self.assertTrue(all(w['depth']>p['bands'][-1]['depth'] for w in p['wisps']))
if __name__=='__main__':unittest.main()
