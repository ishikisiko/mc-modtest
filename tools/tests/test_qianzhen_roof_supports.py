"""真实渠圃屋架回归：完整坡瓦不能代替柱基、木构连接和瓦底承托。"""
import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from qianzhen import story_assets as assets
from qianzhen.build import check_timber_roof_frames, validate
from qianzhen.storygen import GenerationContext, load_contract
from qianzhen.storygen.canal_gardens import generate


AIR = 'minecraft:air'


class TimberRoofSupportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.contract = load_contract().site('canal_gardens')
        cls.authored = cls.contract.blueprint()
        cls.seeded = generate(GenerationContext(cls.contract, 20261024)).to_blueprint()
        cls.blocks = assets.nbt.expand_structure(cls.seeded)[2]
        cls.frames = cls.seeded['timber_roof_frames']
        cls.pavilion = next(f for f in cls.frames if f['id'] == 'sample_pavilion')

    def test_authored_and_actual_seed_cover_pavilion_and_bridge(self):
        for mode, data in (('authored', self.authored), ('seed20261024', self.seeded)):
            with self.subTest(mode=mode):
                _, _, checks = validate(data, self.contract.max_size)
                frames = {f['id']: f for f in checks['timber_roof_frames']}
                self.assertGreaterEqual(set(frames), {'sample_pavilion', 'inspection_bridge'})
                self.assertEqual(frames['sample_pavilion']['posts'], 4)
                self.assertGreater(frames['sample_pavilion']['roof_contacts'], 20)
                self.assertTrue(all(checks['walks']['targets'].values()))
        self.assertIn('canal_gallery', {f['id'] for f in self.frames})

    def test_original_two_layer_gap_fails_with_intact_roof(self):
        broken = dict(self.blocks)
        for post in self.pavilion['posts']:
            x, _, z = post['foot']
            for y in (6, 7):
                broken[x, y, z] = AIR
        with self.assertRaisesRegex(ValueError, 'timber post gap'):
            check_timber_roof_frames(broken, [self.pavilion])

    def test_disconnected_ridge_is_not_supported_by_nearby_roof_tiles(self):
        broken = dict(self.blocks)
        left, right = self.pavilion['bounds'][0][0], self.pavilion['bounds'][1][0]
        for x in (left, right):
            broken[x, 8, 11] = AIR
        with self.assertRaisesRegex(ValueError, 'bearing unsupported'):
            check_timber_roof_frames(broken, [self.pavilion])

    def test_missing_midspan_purlin_fails_even_with_connected_end_frames(self):
        broken = dict(self.blocks)
        broken[38, 7, 9] = AIR
        with self.assertRaisesRegex(ValueError, 'bearing unsupported'):
            check_timber_roof_frames(broken, [self.pavilion])

    def test_water_or_furniture_is_not_a_post_foundation(self):
        x, foot, z = self.pavilion['posts'][0]['foot']
        for state in ('minecraft:air', 'minecraft:water[level=0]', 'minecraft:lantern'):
            broken = {**self.blocks, (x, foot - 1, z): state}
            with self.subTest(state=state), self.assertRaisesRegex(ValueError, 'foundation missing'):
                check_timber_roof_frames(broken, [self.pavilion])

    def test_unrelated_timbers_outside_frame_cannot_bridge_missing_posts(self):
        frame = copy.deepcopy(self.pavilion)
        frame['bounds'][1][0] -= 1
        with self.assertRaisesRegex(ValueError, 'timber post gap'):
            check_timber_roof_frames(self.blocks, [frame])

    def test_normal_validate_catches_a_mutated_export_not_only_plugin_generation(self):
        data = copy.deepcopy(self.seeded)
        x, _, z = self.pavilion['posts'][0]['foot']
        data['ops'].append({'op': 'set', 'pos': [x, 6, z], 'state': AIR})
        with self.assertRaisesRegex(ValueError, 'timber post gap'):
            validate(data, self.contract.max_size)

    def test_cauldron_and_existing_player_headroom_remain_clear(self):
        self.assertEqual(self.blocks[39, 2, 11], 'minecraft:water_cauldron[level=3]')
        for x in range(34, 43):
            for z in (10, 11, 12):
                # Work aisle remains three blocks high; the existing furniture
                # sits at z=10, while the cauldron is the original target cell.
                for y in (4,):
                    self.assertEqual(self.blocks.get((x, y, z), AIR), AIR)

    def test_bank_gallery_retains_its_existing_full_lower_tile_layer(self):
        # The stepped gallery has a solid bottom tile layer, unlike the gabled
        # pavilion. Raising its purlins must not silently replace those tiles.
        for x in range(19, 26):
            for z in range(18, 30):
                self.assertEqual(self.blocks[x, 10, z], 'minecraft:deepslate_tiles')


if __name__ == '__main__':
    unittest.main()
