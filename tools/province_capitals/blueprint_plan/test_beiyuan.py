"""北原正式重排：保留服务、暖巷私院与原尺寸雪府。"""
from .test_formal_support import FormalCityCase


class BeiyuanPlanTests(FormalCityCase):
    region='beiyuan'

    def test_east_forge_garden_keeps_its_original_owner(self):
        gardens={g['id']:g for g in self.data['gardens']}
        for gid,owner in (('beiyuan_garden_20','se_n_01'),('beiyuan_garden_22','se_s_01')):
            self.assertEqual(owner,gardens[gid]['owner']);self.assertEqual('private',gardens[gid]['access'])

    def test_old_south_gate_stays_at_its_declared_floor(self):
        p=self.planner
        self.assertEqual(12,p.road_field[116,270]);self.assertTrue(p.walkable((116,12,270)))
