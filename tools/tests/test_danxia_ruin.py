import gzip
import json
from collections import deque
import unittest
from tools import gen_danxia_ruin as gen


class DanxiaRuinTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules, cls.data, cls.bes, cls.entities = gen.blueprint()
        cls.blocks = {tuple(b['pos']): b['state'] for b in cls.data['blocks']}

    def test_deterministic_native_template_and_bounded_entities(self):
        a=gen.template_bytes(self.data,self.bes,self.entities)
        r,d,b,e=gen.blueprint()
        self.assertEqual(a,gen.template_bytes(d,b,e))
        self.assertEqual(gzip.decompress(a)[0],10)
        for pos in self.blocks:
            self.assertTrue(all(0<=v<limit for v,limit in zip(pos,self.rules['size'])),pos)
        self.assertEqual(len(self.entities),len(self.rules['guardians']))
        for entity in self.entities:
            p=tuple(entity['blockPos'])
            self.assertTrue(self.walkable(p),p)

    def test_rewards_require_core_and_original_targets(self):
        self.assertEqual(self.bes[tuple(self.rules['core'])]['Choice'],'CHARGED')
        self.assertEqual(self.bes[tuple(self.rules['vault'])],{'id':'minecraft:barrel'})
        for plant in self.rules['garden']:
            x,y,z=plant['pos']
            self.assertEqual(self.blocks[x,y-1,z],'minecraft:coarse_dirt')
            self.assertEqual(self.blocks[x,y,z],'minecraft:dead_bush')
        self.assertEqual(len(set(self.bes)),len(self.bes))

    def empty(self,p):
        state=self.blocks.get(p,'minecraft:air').split('[')[0]
        return state in {'minecraft:air','minecraft:wall_torch','minecraft:torch','minecraft:short_grass',
                         'minecraft:vine','minecraft:dead_bush','minecraft:oak_sign'}

    def walkable(self,p):
        x,y,z=p
        return self.empty(p) and self.empty((x,y+1,z)) and not self.empty((x,y-1,z))

    def test_gate_garden_stair_core_and_archive_are_connected_without_mining(self):
        start=(20,10,11); queue=deque([start]); seen={start}
        while queue:
            x,y,z=queue.popleft()
            for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                for dy in [0,1,-1]:
                    q=(x+dx,y+dy,z+dz)
                    if q not in seen and all(0<=v<limit for v,limit in zip(q,self.rules['size'])) and self.walkable(q):
                        # Enough headroom across a rising step or a falling step.
                        if dy==1 and not self.empty((x,y+2,z)): continue
                        if dy==-1 and not self.empty((q[0],y+1,q[2])): continue
                        seen.add(q); queue.append(q)
        for goal in [(10,10,20),(20,3,28),(29,3,33),(30,10,18)]:
            self.assertIn(goal,seen,f'Unreachable {goal}')

    def test_worldgen_and_structure_resource_closure(self):
        files=gen.outputs()
        structure=json.loads(files[gen.DATA/'worldgen/structure/danxia_ruin.json'])
        self.assertEqual(structure['type'],'minecraft:jigsaw')
        # Vanilla JigsawPlacement adds even the root piece only when depth > 0.
        self.assertGreater(structure['size'],0)
        self.assertEqual(structure['start_height']['absolute'],-self.rules['surface_y'])
        self.assertIn(gen.DATA/'worldgen/template_pool/danxia_ruin.json',files)
        self.assertIn(gen.DATA/'structure/danxia_ruin.nbt',files)
        self.assertIn(gen.ASSETS/'blockstates/ruin_core.json',files)


if __name__=='__main__': unittest.main()
