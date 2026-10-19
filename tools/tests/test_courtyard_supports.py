"""Actual 1/16-model contacts, rather than neighboring occupied block cells."""
from functools import lru_cache
import json
from pathlib import Path
import re
import unittest

from tools.reference_courtyard.assembly_scene import build_scene
from tools.reference_courtyard.assembly_shapes import geometry


class CourtyardSupportsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config=json.loads((Path(__file__).parents[1]/'reference_courtyard/assemblies.json').read_text())
        cls.families={f['id']:f for f in cls.config['families']}
        cls.site=build_scene(cls.config)

    @classmethod
    @lru_cache(None)
    def cells(cls,root):
        state=cls.site.blocks[root]
        family=state.split('[',1)[0].split(':',1)[1]
        props=dict(re.findall(r'(\w+)=([^,\]]+)',state))
        if 'part' in props:
            assert props['part']=='0',(root,state)
        v=cls.families[family]['variants'][int(props['variant'])]
        cells=set(geometry(v))
        for _ in range(['north','east','south','west'].index(props['facing'])):
            cells={(15-z,y,x) for x,y,z in cells}
        return {(root[0]*16+x,root[1]*16+y,root[2]*16+z) for x,y,z in cells}

    def contact(self,lower,upper,direction=(0,1,0)):
        a,b=self.cells(lower),self.cells(upper)
        return sum((x+direction[0],y+direction[1],z+direction[2]) in b for x,y,z in a)

    def test_every_column_bears_on_the_deck_and_actually_meets_a_beam(self):
        for z in [18,23]:
            for x in [30,38]:
                with self.subTest(column=(x,5,z)):
                    self.assertGreater(self.contact((x,4,z),(x,5,z)),0)
                    self.assertGreater(self.contact((x,5,z),(30 if x==30 else 36,9,z)),0)
        for z in [26,35]:
            for x,beam_x in [(26,26),(32,32),(36,32),(42,40)]:
                with self.subTest(column=(x,5,z)):
                    self.assertGreater(self.contact((x,4,z),(x,5,z)),0)
                    self.assertGreater(self.contact((x,5,z),(beam_x,9,z)),0)

    def test_both_side_beams_join_front_and_rear_transverse_members(self):
        for x,end_x in [(30,30),(38,36)]:
            self.assertGreater(self.contact((end_x,9,18),(x,9,19),(0,0,1)),0)
            self.assertGreater(self.contact((x,9,19),(end_x,9,23),(0,0,1)),0)

    def test_all_four_hanging_lantern_chains_touch_real_wood(self):
        for z in [18,23]:
            for x,beam_x in [(32,30),(36,36)]:
                self.assertGreater(self.contact((x,8,z),(beam_x,9,z)),0)

    def test_transverse_beams_form_continuous_runs(self):
        for z,roots in [(18,[30,33,36]),(23,[30,33,36]),
                        (26,[26,29,32,37,40]),(35,[26,29,32,37,40])]:
            for a,b in zip(roots,roots[1:]):
                self.assertGreater(self.contact((a,9,z),(b,9,z),(1,0,0)),0)

    def test_pavilion_gable_struts_reach_the_transverse_beams(self):
        for z in [18,23]:
            for x in [30,36]:
                self.assertGreater(self.contact((x,9,z),(26,10,z)),0)

    def test_front_and_rear_hall_frames_rest_on_their_beams(self):
        for z in [26,35]:
            for root in [26,29,32,37,40]:
                width=self.families['courtyard_beam']['variants'][
                    int(re.search(r'variant=(\d+)',self.site.blocks[root,9,z])[1])]['size'][0]
                for x in range(root,root+width):
                    self.assertGreater(self.contact((root,9,z),(x,10,z)),0)

    def purlin_foot(self,column,z):
        # The lowest eight pixels are below every lower roof shell at these
        # four axes. Restricting checks to this band cannot pass accidentally
        # because neighboring tile skins happen to meet higher up.
        return {(a,b,c) for a,b,c in self.cells((22,11,z))
                if column*16+5<=a<column*16+11 and 11*16<=b<11*16+8}

    def test_rear_hall_frame_actually_bears_on_each_longitudinal_purlin(self):
        for x in [26,32,36,42]:
            frame=self.cells((x,10,35));foot=self.purlin_foot(x,35)
            contact={(a,b+1,c) for a,b,c in frame}&foot
            self.assertEqual(36,len(contact),x)

    def test_purlins_are_solid_across_all_ten_roof_strips(self):
        for x in [26,32,36,42]:
            for z in range(27,37):
                foot=self.purlin_foot(x,z)
                expected={(a,b,c) for a in range(x*16+5,x*16+11)
                          for b in range(11*16,11*16+8)
                          for c in range(z*16,(z+1)*16)}
                self.assertEqual(expected,foot,(x,z))
                if z<36:
                    adjacent=self.purlin_foot(x,z+1)
                    self.assertEqual(48,sum((a,b,c+1) in adjacent for a,b,c in foot),(x,z))

    def test_each_continuous_purlin_has_front_and_rear_gable_joints(self):
        for x in [26,32,36,42]:
            for z,gable_z,direction in [(27,26,-1),(36,37,1)]:
                foot=self.purlin_foot(x,z);gable=self.cells((22,11,gable_z))
                self.assertGreater(sum((a,b,c+direction) in gable for a,b,c in foot),0,(x,z))

    def test_every_purlin_vertical_slice_reaches_the_lower_roof_shell(self):
        v=next(v for v in self.families['courtyard_roof']['variants'] if v['key']=='curve_lower_sides')
        cells=geometry(v)
        slices={}
        for (x,y,z),material in cells.items():
            if material=='timber' and y<32:
                slices.setdefault((x,z),[]).append(y)
        for column in v['purlin_columns']:
            for x in range(column*16+5,column*16+11):
                for z in range(16):
                    timber=sorted(slices.get((x,z),[]))
                    self.assertTrue(timber,(column,x,z))
                    self.assertEqual(list(range(max(timber)+1)),timber,(column,x,z))
                    self.assertIn((x,max(timber)+1,z),cells,(column,x,z))

    def test_standing_lanterns_reach_their_bridge_plinths(self):
        for x in [6,33]:
            self.assertGreater(self.contact((x,2,10),(x,3,10)),0)
            # The plinth is one full timber block; an ordinary bottom slab
            # leaves the observed eight-pixel unsupported gap.
            self.assertEqual(15,max(b-2*16 for a,b,c in self.cells((x,2,10))))

    def test_railing_posts_reach_the_floor_and_adjacent_panels_join(self):
        for x in [29,39]:
            for z in [18,21]:
                rail=self.cells((x,5,z))
                feet={(a,b-1,c) for a,b,c in rail if b==5*16}
                floors=set().union(*(self.cells((x,4,n)) for n in range(z,z+3)))
                self.assertTrue(feet&floors)
            self.assertGreater(self.contact((x,5,18),(x,5,21),(0,0,1)),0)

    def test_main_hall_column_and_beam_runs_are_mirrored(self):
        for family,y in [('courtyard_column',5),('courtyard_beam',9)]:
            for z in [26,35]:
                roots=[tuple(a['root']) for a in self.site.assemblies
                       if a['family']==family and a['root'][1:]==[y,z]]
                cells=set().union(*(self.cells(p) for p in roots))
                self.assertEqual(cells,{(69*16-1-x,b,c) for x,b,c in cells})


if __name__=='__main__':
    unittest.main()
