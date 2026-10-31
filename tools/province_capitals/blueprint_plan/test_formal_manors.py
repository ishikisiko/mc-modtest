"""西漠与魔域正式府城的地域边界。"""
from .test_formal_support import FormalCityCase


class XimoFormalTests(FormalCityCase):
    region='ximo'

    def test_original_entry_and_oasis_courts_remain_public(self):
        p=self.planner
        self.assertEqual(10,p.road_field[98,270]);self.assertTrue(p.walkable((98,10,270)))
        self.assertTrue(all(c['access']=='public' for c in p.courtyards))


class MoyuFormalTests(FormalCityCase):
    region='moyu'

    def test_full_high_manor_and_three_rift_bridges(self):
        p=self.planner
        self.assertEqual(128,p.height);self.assertEqual(104,p.manor_manifest['size'][1])
        self.assertEqual(3,sum(r['kind']=='bridge' for r in p.layout['roads']))
        self.assertEqual(2,len(p.manor_scene['edict_roots']))
