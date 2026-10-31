"""东荒正式重排：长谷、双关和晒作院。"""
from .test_formal_support import FormalCityCase


class DonghuangBlueprintTests(FormalCityCase):
    region='donghuang'

    def test_both_gate_halls_keep_their_first_storey_and_work_yards(self):
        p=self.planner
        self.assertEqual({'gate','north_gate'},{o['owner'] for o in p.public_passages})
        for opening in p.public_passages:self.assertEqual(p.summaries[opening['owner']]['position'][1],opening['y'])
        yards=[g for g in p.gardens if g['id'] not in {c['id'] for c in p.courtyards}]
        self.assertTrue(yards)
        for g in yards:self.assertEqual({'drying_rack','workbench'},{f['kind'] for f in g['fixtures']})
