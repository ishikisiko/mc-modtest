import unittest
from collections import defaultdict

from tools.qianji import bookcases
from tools.qianji.components import ConstructionScene
from tools.qianji.headquarters import Scene


class BookcasesTest(unittest.TestCase):
    def test_all_four_orientations_keep_the_existing_wood_frame_exactly(self):
        roles=set(bookcases.RULES['book_roles'])
        for yaw in (0,90,180,270):
            old=Scene();new=ConstructionScene()
            for scene in (old,new):
                scene.front_module(21.5,32.25,yaw,lambda s=scene:s.books(.5,4.8,2.3,8,4.5))
            self.assertEqual([b for b in old.boxes if b[6]not in roles],new.boxes)
            self.assertEqual(1,len(new.bookcases));self.assertEqual(yaw,new.bookcases[0]['yaw'])
            raw=defaultdict(list);bookcases.add_regions(raw,new.bookcases)
            self.assertTrue(raw)
            for rows in raw.values():
                for b in rows:
                    self.assertEqual(bookcases.ROLE,b[6]);self.assertEqual(9,len(b))
                    self.assertTrue(all(0<=b[i]<b[i+3]<=16 for i in range(3)))

    def test_two_vertical_fragments_generate_matching_spines(self):
        bottom=[0,12,2,16,16,9,0,12]
        top=[0,0,2,16,10,9,0,-4]
        for x in range(-16,16):
            a=bookcases.pattern(x,25,12);b=bookcases.pattern(x,26,-4)
            self.assertEqual(a,b)
            low=list(bookcases.books(bottom,a));high=list(bookcases.books(top,b))
            self.assertEqual([(v[0],v[2],v[3],v[5],v[6])for v in low],[(v[0],v[2],v[3],v[5],v[6])for v in high])

    def test_generated_books_stay_within_the_region_without_overlap(self):
        for facing in range(4):
            region=[2,3,4,15,16,14,facing,3]
            axis=0 if facing in(0,2)else 2
            for seed in range(bookcases.RULES['patterns']):
                previous=-1
                for b in bookcases.books(region,seed):
                    self.assertTrue(all(region[i]<=b[i]<b[i+3]<=region[i+3]for i in range(3)))
                    self.assertGreater(b[axis],previous);previous=b[axis+3]
                    self.assertIn(b[6],bookcases.RULES['book_roles'])

    def test_books_are_deterministic_and_positions_provide_variation(self):
        region=[0,0,0,16,14,7,0,0]
        seeds={bookcases.pattern(x,100,0)for x in range(1024)}
        self.assertEqual(set(range(bookcases.RULES['patterns'])),seeds)
        variants={tuple(bookcases.books(region,s))for s in seeds}
        self.assertGreater(len(variants),1)
        self.assertEqual(list(bookcases.books(region,3)),list(bookcases.books(region,3)))

    def test_bare_book_region_exports_parameters_without_expanded_book_faces(self):
        profile=[(0,0,0,16,14,7,bookcases.ROLE,0,0)]
        result=bookcases.model(profile,{},'qianji')
        self.assertEqual('myvillage:procedural_books',result['loader'])
        self.assertEqual([],result['base_model']['elements'])
        self.assertEqual([[0,0,0,16,14,7,0,0]],result['book_regions'])


if __name__=='__main__':unittest.main()
