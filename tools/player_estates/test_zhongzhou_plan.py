"""保留生活巷端点时，不能把撤并区内的旧巷尽头接入新建筑。"""
import unittest

import numpy as np

from .zhongzhou_plan import affected_lanes


class PlannedLaneConnectionsTest(unittest.TestCase):
    def routes(self, start, end):
        feature={'t':2,'kind':3,'pid':23,'ax':start[0],'az':start[1],
                 'bx':end[0],'bz':end[1],'hw':1.5}
        return affected_lanes([feature],{'plot':[20,20,60,60]},[0,0,0],np.zeros((90,90),int),3)[0]

    def test_old_dead_end_inside_parcel_is_removed(self):
        route=self.routes([75,40],[35,40])
        self.assertTrue(route['old_internal_dead_end_removed'])
        self.assertEqual(len(route['connection_spurs_xz']),1)
        # The old end at X35 would penetrate the new estate at X23..57.
        self.assertTrue(all(x>=58.5 for spur in route['connection_spurs_xz']for x,z in spur))

    def test_crossing_lane_retains_both_external_connections(self):
        route=self.routes([75,40],[5,40])
        self.assertFalse(route['old_internal_dead_end_removed'])
        self.assertEqual(len(route['connection_spurs_xz']),2)
        self.assertEqual(len(route['endpoint_feet_y']),2)


if __name__=='__main__':unittest.main()
