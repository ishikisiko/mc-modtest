import gzip
import json
import unittest
from collections import deque

from tools import gen_thunder_tiger_lair as gen


class ThunderTigerLairTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules, cls.data, cls.bes, cls.entities = gen.blueprint()
        cls.blocks = {tuple(b['pos']): b['state'] for b in cls.data['blocks']}

    def test_native_resource_closure_is_deterministic_and_has_one_empty_anchor(self):
        files = gen.outputs()
        blob = files[gen.DATA / f'structure/{gen.IDENTITY}.nbt']
        self.assertEqual(blob, gen.template_bytes(self.data, self.bes, self.entities))
        self.assertEqual(10, gzip.decompress(blob)[0])
        self.assertFalse(self.entities, 'A template must not embed untracked boss copies')
        self.assertEqual({tuple(self.rules['anchor']): {
            'id': 'myvillage:thunder_tiger_lair_anchor', 'LairState': 'READY'}}, self.bes)
        self.assertFalse(any('chest' in b or 'barrel' in b for b in self.blocks.values()))
        self.assertNotIn('rewards', self.rules)
        structure = json.loads(files[gen.DATA / f'worldgen/structure/{gen.IDENTITY}.json'])
        pool = json.loads(files[gen.DATA / f'worldgen/template_pool/{gen.IDENTITY}.json'])
        self.assertEqual('myvillage:thunder_tiger_lair', structure['start_pool'])
        self.assertEqual(-self.rules['surface_y'], structure['start_height']['absolute'])
        self.assertEqual('ignore_waterlogging', pool['elements'][0]['element']['override_liquid_settings'])
        for rel in [f'worldgen/structure_set/{gen.IDENTITY}.json', f'tags/worldgen/biome/has_{gen.IDENTITY}.json']:
            self.assertIn(gen.DATA / rel, files)
        self.assertIn('gen_thunder_tiger_lair.py', (gen.ROOT / 'tools/generate_all_structures.py').read_text())

    def test_entry_arena_and_all_pillar_approaches_are_walkable(self):
        r, b = self.rules, self.blocks
        y = r['surface_y'] + 1
        def walkable(x, z):
            return b.get((x, y, z)) == b.get((x, y + 1, z)) == 'minecraft:air' and b.get((x, y - 1, z)) not in (None, 'minecraft:air')
        entry = tuple(r['entry'])
        self.assertEqual(y, entry[1])
        self.assertTrue(walkable(entry[0], entry[2]))
        queue = deque([(entry[0], entry[2])])
        seen = set(queue)
        while queue:
            x, z = queue.popleft()
            for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                target = x + dx, z + dz
                if target not in seen and walkable(*target):
                    seen.add(target)
                    queue.append(target)
        for target in [r['spawn'], r['trigger']]:
            self.assertIn((target[0], target[2]), seen)
        for x, py, z in r['pillars']:
            for dx, dz in [(2, 0), (-2, 0), (0, 2), (0, -2)]:
                self.assertIn((x + dx, z + dz), seen)

    def test_boss_spawn_volume_is_dry_clear_and_fully_supported(self):
        x, y, z = self.rules['spawn']
        horn = json.loads((gen.DATA / 'beast/thunder_tiger_boss.json').read_text())['horn']
        # Cover the head/body occupied union in any cardinal facing, before a first entity tick.
        import math
        radius = math.ceil(max(horn['head_forward'] + horn['head_width'] / 2,
                               abs(horn['body_back']), horn['body_front']))
        height = math.ceil(max(horn['body_height'], horn['head_center_y'] + horn['head_height'] / 2))
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                self.assertNotEqual('minecraft:air', self.blocks[x + dx, y - 1, z + dz])
                for dy in range(height):
                    self.assertEqual('minecraft:air', self.blocks[x + dx, y + dy, z + dz])
        for px, py, pz in self.rules['pillars']:
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    for dy in range(5):
                        self.assertIn(self.blocks[px + dx, py + dy, pz + dz], {'minecraft:stone', 'minecraft:andesite'})
        self.assertEqual(1, self.rules['pillar_radius'])
        self.assertEqual(5, self.rules['pillar_height'])

    def test_mountain_and_arena_foundations_are_filled_and_all_coordinates_bounded(self):
        w, h, d = self.rules['size']
        for (x, y, z), state in self.blocks.items():
            self.assertTrue(0 <= x < w and 0 <= y < h and 0 <= z < d)
            if state != 'minecraft:air' and y:
                self.assertNotEqual('minecraft:air', self.blocks.get((x, y - 1, z)))
        self.assertGreater(max(y for (x, y, z), state in self.blocks.items() if state != 'minecraft:air'), self.rules['surface_y'] + 10)

    def test_all_four_rotations_preserve_home_and_pillar_offsets(self):
        anchor = self.rules['anchor']
        def rotate(x, z, turn):
            return [(x, z), (-z, x), (-x, -z), (z, -x)][turn]
        for target in [self.rules['spawn'], *self.rules['pillars']]:
            dx, dz = target[0] - anchor[0], target[2] - anchor[2]
            for turn in range(4):
                ax, az = rotate(anchor[0], anchor[2], turn)
                tx, tz = rotate(target[0], target[2], turn)
                self.assertEqual((tx - ax, tz - az), rotate(dx, dz, turn))

    def test_runtime_pillar_collision_volume_and_authored_stone_palette_match(self):
        boss = json.loads((gen.DATA / 'beast/thunder_tiger_boss.json').read_text())['horn']
        self.assertEqual(self.rules['pillar_radius'], boss['pillar_half_width'])
        self.assertEqual(self.rules['pillar_height'], boss['pillar_height'])
        self.assertLessEqual(len(self.rules['pillars']), boss['maximum_pillars'])
        self.assertTrue({self.rules['palette']['rock'], self.rules['palette']['pillar']} <= set(boss['allowed_blocks']))


if __name__ == '__main__':
    unittest.main()
