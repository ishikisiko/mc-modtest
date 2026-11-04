"""从正式资源回读矿场，检查空手开局材料、完整地下空间和往返路线。"""
from collections import deque
import gzip
import json
from pathlib import Path
import tempfile
import unittest

from tools.starter_mine import generate as gen


class StarterMineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.out=Path(cls.temp.name)
        cls.reference,cls.model=gen.export(cls.out)
        cls.root=cls.out/'data/myvillage/starter_mine'
        cls.definition=json.loads((cls.root/'layout.json').read_text())
        cls.body=json.loads(gzip.decompress((cls.root/'blocks.json.gz').read_bytes()))
        cls.blocks={(x,y,z):cls.body['palette'][state] for x,y,z,state in cls.body['cells']}

    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()

    def test_resource_identity_and_generation_are_repeatable(self):
        before={p.name:p.read_bytes() for p in self.root.iterdir()}
        reference,_=gen.export(self.out)
        self.assertEqual(self.reference,reference)
        self.assertEqual(before,{p.name:p.read_bytes() for p in self.root.iterdir()})
        self.assertEqual(self.reference['sha256'],gen.digest(before['layout.json']))
        self.assertEqual(self.definition['blocks_sha256'],gen.digest(before['blocks.json.gz']))
        self.assertEqual(len(self.blocks),len(self.body['cells']))

    def test_existing_style_resolution_is_not_hijacked_by_the_mine(self):
        from buildgen.groups import group_for_style,get_group
        self.assertEqual('starter_mine',get_group('starter_mine').group_id)
        self.assertNotEqual('starter_mine',group_for_style('chinese_courtyard').group_id)

    def test_all_landmarks_have_a_return_path_even_when_native_underground_is_empty(self):
        blocks=self.blocks;w,h,d=self.definition['size']
        # 不补写作者生成时的稠密岩体；只凭实际导出的地板、岩壳与台阶验证。
        def solid(x,y,z):
            state=blocks.get((x,y,z),'minecraft:air').split('[',1)[0]
            return state not in {'minecraft:air','minecraft:wall_torch','minecraft:oak_sign','minecraft:oak_leaves'}
        def walkable(p):
            x,y,z=p
            return 0<=x<w and 1<=y<h-1 and 0<=z<d and solid(x,y-1,z) and not solid(x,y,z) and not solid(x,y+1,z)
        def goal(pos):
            x,y,z=pos
            return next((p for dy in [0,1,-1,2,-2] if walkable(p:=(x,y+dy,z))),None)
        start=goal(self.definition['entry']);self.assertIsNotNone(start)
        seen={start};queue=deque([start])
        while queue:
            x,y,z=queue.popleft()
            for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
                for dy in [0,1,-1]:
                    nxt=x+dx,y+dy,z+dz
                    if nxt in seen or not walkable(nxt):continue
                    if dy==1 and solid(x,y+2,z) or dy==-1 and solid(nxt[0],nxt[1]+2,nxt[2]):continue
                    seen.add(nxt);queue.append(nxt)
        for marker in self.definition['landmarks']:self.assertIn(goal(marker['pos']),seen,marker['id'])
        self.assertGreater(sum(y<self.definition['ground_y']-8 for x,y,z in seen),300)

    def test_real_trees_can_drop_logs_and_saplings_without_forced_persistent_leaves(self):
        roots=self.definition['trees'];self.assertEqual(12,len(roots))
        for root in roots:self.assertTrue(self.blocks[tuple(root)].startswith('minecraft:oak_log'))
        leaves={p:s for p,s in self.blocks.items() if s.startswith('minecraft:oak_leaves')}
        self.assertGreater(len(leaves),400)
        for state in leaves.values():
            props=dict(v.split('=') for v in state.split('[',1)[1].rstrip(']').split(','))
            self.assertEqual('false',props['persistent']);self.assertIn(int(props['distance']),range(1,7))
        for x,y,z in leaves:
            self.assertTrue(any(abs(x-a)+abs(y-b)+abs(z-c)<=6 for (a,b,c),state in self.blocks.items() if state.startswith('minecraft:oak_log')))

    def test_mining_and_workstations_use_real_vanilla_blocks_without_free_equipment(self):
        ids=[state.split('[',1)[0] for state in self.blocks.values()]
        self.assertEqual(36,ids.count('minecraft:coal_ore'));self.assertEqual(36,ids.count('minecraft:iron_ore'));self.assertEqual(18,ids.count('minecraft:copper_ore'))
        self.assertIn('minecraft:crafting_table',ids);self.assertIn('minecraft:furnace',ids)
        self.assertFalse(set(ids)&{'minecraft:chest','minecraft:diamond_ore','minecraft:spawner','minecraft:bedrock'})
        self.assertTrue(all(v.startswith('minecraft:') for v in ids))
        entities={tuple(v['pos']):v['nbt'] for v in self.body['block_entities']}
        self.assertEqual('minecraft:furnace',entities[61,32,22]['id'])
        self.assertTrue(any(v['id']=='minecraft:sign' for v in entities.values()))


if __name__=='__main__':unittest.main()
