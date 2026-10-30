import json
import math
import unittest
from .generate import ASSETS, DATA, IDENTITY, appearance, faces, generate, parts


class DemonPactPillarResourcesTest(unittest.TestCase):
    def test_cut_geometry_preserves_every_form_and_connected_occupancy(self):
        a=appearance();cells=parts(a)
        self.assertEqual([0,0,0],cells[0]['offset'])
        for index,form in enumerate(a['forms']):
            expected=math.prod(hi-lo for lo,hi in zip(form['from'],form['to']))
            fragments=[b for cell in cells for b in cell['boxes'] if b['form']==index]
            self.assertAlmostEqual(expected,sum(math.prod(hi-lo for lo,hi in zip(b['from'],b['to']))/16**3 for b in fragments))
        occupied={tuple(c['offset']) for c in cells};visited={next(iter(occupied))};todo=list(visited)
        while todo:
            p=todo.pop()
            for axis in range(3):
                for sign in (-1,1):
                    n=tuple(p[i]+(sign if i==axis else 0) for i in range(3))
                    if n in occupied and n not in visited:visited.add(n);todo.append(n)
        self.assertEqual(occupied,visited)

    def test_inscription_uses_one_complete_canvas_and_no_internal_cut_faces(self):
        a=appearance();uvs=[]
        for p in parts(a):
            for box in p['boxes']:
                form=a['forms'][box['form']]
                lo=[o+n/16 for o,n in zip(p['offset'],box['from'])];hi=[o+n/16 for o,n in zip(p['offset'],box['to'])]
                fs=faces(form,lo,hi)
                if form['material']=='shaft' and 'north' in fs:uvs.append(fs['north']['uv'])
                for side,axis,positive in [('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]:
                    if side in fs:self.assertEqual((form['to'] if positive else form['from'])[axis],(hi if positive else lo)[axis])
        self.assertGreater(len(uvs),2)
        self.assertAlmostEqual(256,sum((r-l)*(b-t) for l,t,r,b in uvs),places=4)
        self.assertEqual(0,min(u[1] for u in uvs));self.assertEqual(16,max(u[3] for u in uvs))

    def test_four_facings_are_complete_and_only_root_drops(self):
        files=generate();states=json.loads(files[ASSETS/f'blockstates/{IDENTITY}.json'])['variants']
        self.assertEqual(len(parts(appearance()))*4,len(states))
        for ref in states.values():self.assertIn(ASSETS/('models/'+ref['model'].split(':',1)[1]+'.json'),files)
        pools=json.loads(files[DATA/f'loot_table/blocks/{IDENTITY}.json'])['pools']
        self.assertEqual(1,len(pools));self.assertEqual(1,pools[0]['rolls'])
        self.assertIn({'condition':'minecraft:block_state_property','block':'myvillage:'+IDENTITY,'properties':{'part':'0'}},pools[0]['conditions'])


if __name__=='__main__':unittest.main()
