"""双扇镜像、开门通道及整件预留空间的回归。"""
import json
import unittest
from pathlib import Path
from tools.player_homes.door_model import door_parts,transformed_bounds,check_design
from tools.player_homes.model import build_home,walk_check
from tools.reference_courtyard.assembly_shapes import geometry

ROOT=Path(__file__).resolve().parents[2]

class DoorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.design=json.loads((ROOT/'blueprints/player_homes/door_study.json').read_text())
        cls.catalog=json.loads((ROOT/'blueprints/player_homes/catalog.json').read_text())
        kit=json.loads((ROOT/cls.catalog['kit']).read_text())
        cls.family=next(f for f in kit['families']if f['id']==cls.catalog['door']['family'])

    def test_mirror_does_not_mutate_the_x_loop_variable(self):
        part={'bounds':[0,.25,0,2,4,.1875]}
        self.assertEqual([2.5,.25,.625,4.5,4,.8125],transformed_bounds(part,[4.5,0,.625],0,True))

    def test_approved_swing_and_runtime_voxels_leave_the_full_portal_clear(self):
        frame,leaf=door_parts(self.design)
        self.assertEqual(0,check_design(self.design,frame,leaf)['fully_open_intrusions'])
        v=self.family['variants'][0];closed=geometry(v);opened=geometry(v,True)
        self.assertTrue(any(16<=x<64 and 3<=y<32 and 9<=z<14 for x,y,z in closed))
        self.assertFalse(any(16<=x<64 and 2<=y<66 for x,y,z in opened))
        self.assertTrue(all(0<=x<80 and 0<=y<80 and 0<=z<48 for x,y,z in closed.keys()|opened.keys()))

    def test_all_ten_doors_own_their_full_swing_cells_and_rooms_are_reachable(self):
        count=0
        for spec in self.catalog['templates']:
            site=build_home(ROOT,self.catalog,spec)
            doors=[a for a in site.assemblies if a['family']==self.family['id']]
            count+=len(doors)
            for a in doors:
                self.assertEqual(75,len(a['parts']))
                p=tuple(a['parts'][-1])
                with self.assertRaisesRegex(AssertionError,'split whole item'):
                    site.put(p,'minecraft:stone')
            self.assertEqual('pass',walk_check(ROOT,self.catalog,site)['status'])
            self.assertFalse(any('minecraft:dark_oak_door['in b for b in site.blocks.values()))
        self.assertEqual(10,count)

if __name__=='__main__':unittest.main()
