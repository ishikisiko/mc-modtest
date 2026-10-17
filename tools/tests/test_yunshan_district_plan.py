"""Checks for the new town layout, including deliberate invalid placements."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'yunshan'))
from plan_district import ROOT, prepare, validate, rotated


class DistrictPlanTest(unittest.TestCase):
    def setUp(self):
        self.args=list(prepare(ROOT/'blueprints/yunshan/district_plan_v2.json'))

    def test_layout_connects_all_four_entrances_and_market(self):
        result=validate(*self.args)
        self.assertTrue(result['passed'],result['checks'])
        self.assertEqual(result['counts']['retained_lots'],70)
        self.assertEqual(len(result['routes']),5)
        self.assertTrue(all(result['routes'].values()))

    def test_footprint_on_main_street_is_rejected(self):
        building=self.args[3][0]
        building['rect']=[-40,339,-22,361]
        result=validate(*self.args)
        self.assertTrue(any(not c['passed'] and 'street or plaza' in c['name'] for c in result['checks']))

    def test_unstepped_entrance_height_is_rejected(self):
        self.args[3][0]['level']+=4
        result=validate(*self.args)
        self.assertTrue(any(not c['passed'] and 'level entrance' in c['name'] for c in result['checks']))

    def test_unremoved_old_lot_is_rejected(self):
        self.args[-1].append(copy.deepcopy(next(l for l in self.args[1]['lots'] if l['id']=='gateN-1')))
        result=validate(*self.args)
        self.assertTrue(any(not c['passed'] and 'retained lots' in c['name'] for c in result['checks']))

    def test_rotation_preserves_cells_and_front_direction(self):
        expected=[(9,0),(22,9),(9,22),(0,9)]
        for q in range(4):
            self.assertEqual(rotated(9,0,19,23,q),expected[q])
            cells={rotated(x,z,19,23,q) for x in range(19) for z in range(23)}
            self.assertEqual(len(cells),19*23)


if __name__=='__main__':
    unittest.main()
