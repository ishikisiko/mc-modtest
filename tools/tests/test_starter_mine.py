"""检查地面作者资源；地下随机洞窟的规模、连通和存档由 Java 与原生检查覆盖。"""
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

    def test_surface_export_has_no_authored_caves_routes_or_ore_rooms(self):
        rules=json.loads(gen.SOURCE.read_text())
        self.assertFalse(set(rules)&{'caves','routes','cave_noise','ores'})
        base=self.definition['ground_y']
        self.assertTrue(all(y>=base-1 for x,y,z in self.blocks))
        self.assertEqual(0,self.definition['checks']['authored_underground_blocks'])
        self.assertEqual('minecraft_cave_carver_v1',self.definition['underground']['algorithm'])
        self.assertGreaterEqual(self.definition['underground']['minimum_air_blocks'],1200)
        self.assertGreaterEqual(self.definition['underground']['minimum_landing_stances'],49)
        self.assertEqual(['entrance'],[p['id'] for p in self.definition['landmarks']])

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
        self.assertFalse(any(v.endswith('_ore') for v in ids))
        self.assertEqual({'coal':12,'iron':12,'copper':8},self.definition['underground']['entry_ores'])
        self.assertIn('minecraft:crafting_table',ids);self.assertIn('minecraft:furnace',ids)
        self.assertFalse(set(ids)&{'minecraft:chest','minecraft:diamond_ore','minecraft:spawner','minecraft:bedrock'})
        self.assertTrue(all(v.startswith('minecraft:') for v in ids))
        entities={tuple(v['pos']):v['nbt'] for v in self.body['block_entities']}
        self.assertEqual('minecraft:furnace',entities[61,32,22]['id'])
        self.assertTrue(any(v['id']=='minecraft:sign' for v in entities.values()))


if __name__=='__main__':unittest.main()
