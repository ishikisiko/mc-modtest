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

    def test_formal_approach_reaches_real_gate_and_uses_half_block_steps(self):
        from .manor_access import source_column
        p=self.planner;route=p.manor_approach;config=p.layout['manor']['access']
        x,z=source_column(p,*config['portal_source'][::2])
        self.assertEqual([x,config['portal_source'][1]+p.manor_ground_delta,z],route['portal'])
        self.assertEqual(route['portal'],route['points'][0])
        self.assertEqual('replan_03',route['road_id'])
        self.assertTrue(any(y%2 for _,y,_ in route['feet2_points']))
        self.assertTrue(all(abs(a[1]-b[1])<=1 for a,b in zip(route['feet2_points'],route['feet2_points'][1:])))
        from collections import Counter
        widths=Counter(z for x,y,z in route['surface_columns'])
        self.assertGreaterEqual(widths[source_column(p,0,-19)[1]],7)
        self.assertEqual(12,widths[source_column(p,0,-10)[1]])

    def test_missing_outer_tread_is_rejected_even_with_intact_centerline(self):
        from .manor_access import audit,source_column
        p=self.planner;route=p.manor_approach
        x,z=source_column(p,3,-19);feet2=39+p.manor_ground_delta*2
        position=(x,(feet2-1)//2,z);original=p.get(position)
        try:
            p.set(position,'minecraft:air','检查故障注入','road')
            with self.assertRaisesRegex(ValueError,'整幅路线'):
                audit(p,route)
        finally:
            p.set(position,original,'恢复检查现场','road')

    def test_old_two_block_gap_cannot_pass_the_new_approach_audit(self):
        from .manor_access import audit,source_column
        import copy
        route=copy.deepcopy(self.planner.manor_approach)
        x,z=source_column(self.planner,0,-11)
        index=next(i for i,p in enumerate(route['feet2_points']) if p[0]==x and p[2]==z)
        route['feet2_points'][index][1]-=4
        with self.assertRaisesRegex(ValueError,'高坎'):
            audit(self.planner,route)
