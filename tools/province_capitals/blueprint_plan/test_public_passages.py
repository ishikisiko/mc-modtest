"""正式门楼开口须有界，保留首层、两侧门墩和原家具。"""
import copy
from .test_formal_support import FormalCityCase


class PublicPassageTests(FormalCityCase):
    region='lingyue'

    def test_declared_openings_have_actual_changes_and_keep_their_ceiling(self):
        p=self.planner;self.assertTrue(p.public_passages)
        for opening in p.public_passages:
            self.assertTrue(opening['changes'])
            for x,y,z in opening['cells']:
                self.assertEqual('minecraft:air',p.get((x,y,z)))
                self.assertIn((x,z),p.road_field)
                self.assertLess(y,opening['y']+opening['height'])
            owner=opening['owner'];template=p.templates[owner]
            self.assertTrue(any(y>2 and layer=='roof' for (_,y,_),(_,_,layer) in template.cells.items()))

    def test_wrong_owner_storey_or_headroom_rejects(self):
        for change,message in (({'owner':'house_001'},'现有门楼'),({'y':20},'首层'),({'height':8},'净空高度')):
            p=copy.copy(self.planner);p.layout=copy.deepcopy(p.layout);p.layout['public_passages'][0].update(change)
            with self.subTest(change=change),self.assertRaisesRegex(ValueError,message):p.validate_passages()

    def test_opening_cannot_remove_side_piers(self):
        p=copy.copy(self.planner);p.layout=copy.deepcopy(p.layout);opening=p.layout['public_passages'][0]
        opening['bounds'][0]=min(x for x,z in p.model.footprints[opening['owner']])
        with self.assertRaisesRegex(ValueError,'两侧门墩'):p.validate_passages()

    def test_constructed_doors_use_complete_native_door_halves(self):
        p=self.planner;self.assertTrue(p.doors)
        for door in p.doors:
            x,y,z=door['cells'][-1];lower=p.get((x,y,z));upper=p.get((x,y+1,z))
            self.assertIn('_door[',lower);self.assertIn('half=lower',lower);self.assertIn('half=upper',upper)
            self.assertEqual(lower.split('[',1)[0],upper.split('[',1)[0])
