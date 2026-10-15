import gzip
import json
from collections import deque
import unittest
from tools import gen_combat_ruins as gen

class CombatRuinsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.sites={k:gen.blueprint(k) for k in gen.IDS}
    def test_native_template_determinism_and_closed_resources(self):
        files=gen.outputs()
        for name,(r,d,b,e) in self.sites.items():
            blob=gen.template_bytes(d,b,e)
            self.assertEqual(blob,files[gen.DATA/f'structure/{name}.nbt'])
            self.assertEqual(gzip.decompress(blob)[0],10)
            self.assertFalse(e,'Guardians must be bound at runtime, not untracked template entities')
            cache=b[tuple(r['cache'])];self.assertEqual(cache['BattleState'],'READY');self.assertNotIn('Items',cache)
            for block in d['blocks']:self.assertTrue(all(0<=v<limit for v,limit in zip(block['pos'],r['size'])))
            structure=json.loads(files[gen.DATA/f'worldgen/structure/{name}.json'])
            pool=json.loads(files[gen.DATA/f'worldgen/template_pool/{name}.json'])
            self.assertEqual(pool['elements'][0]['element']['override_liquid_settings'],'ignore_waterlogging')
            self.assertGreater(structure['size'],0);self.assertEqual(structure['start_height']['absolute'],-r['surface_y'])
            for path in [f'worldgen/template_pool/{name}.json',f'worldgen/structure_set/{name}.json',f'tags/worldgen/biome/has_{name}.json']:self.assertIn(gen.DATA/path,files)
    def test_entry_every_spawn_and_cache_reachable_without_breaking_blocks(self):
        for name,(r,d,b,e) in self.sites.items():
            blocks={tuple(v['pos']):v['state'] for v in d['blocks']}
            def empty(p):return blocks.get(p,'minecraft:air').split('[')[0] in {'minecraft:air','minecraft:oak_sign','minecraft:wall_torch','minecraft:soul_wall_torch'}
            def walkable(p):
                x,y,z=p
                return empty(p) and empty((x,y+1,z)) and not empty((x,y-1,z))
            start=tuple(r['entry']);self.assertTrue(walkable(start),(name,start));seen={start};q=deque([start])
            while q:
                x,y,z=q.popleft()
                for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                    for dy in [0,-1,1]:
                        v=(x+dx,y+dy,z+dz)
                        if v in seen or not all(0<=a<lim for a,lim in zip(v,r['size'])) or not walkable(v):continue
                        if dy==1 and not empty((x,y+2,z)):continue
                        if dy==-1 and not empty((v[0],y+1,v[2])):continue
                        seen.add(v);q.append(v)
            goals=[tuple(w['trigger']) for w in r['waves']]+[tuple(g['pos']) for w in r['waves'] for g in w['guards']]
            x,y,z=r['cache'];goals.append((x,y,z-1))
            for goal in goals:self.assertIn(goal,seen,(name,'unreachable',goal))
            for wave in r['waves']:
                for guard in wave['guards']:
                    x,y,z=guard['pos']
                    # Wolf bodies are wider than one block; the surrounding floor must be free.
                    if guard['entity']=='myvillage:demon_wolf':
                        for dx,dz in [(0,0),(1,0),(-1,0),(0,1),(0,-1)]:self.assertTrue(empty((x+dx,y,z+dz)),(name,guard['pos']))
    def test_authored_rewards_resolve_and_generated_closure_hook_exists(self):
        lang=json.loads((gen.ASSETS/'lang/zh_cn.json').read_text())
        for r,_,_,_ in self.sites.values():
            self.assertIn('structure.myvillage.'+r['id'],lang)
            self.assertTrue((gen.DATA/'myvillage/technique'/f'{r["manual"].split(":")[1]}.json').exists())
        self.assertIn('gen_combat_ruins.py',(gen.ROOT/'tools/generate_all_structures.py').read_text())
if __name__=='__main__':unittest.main()
