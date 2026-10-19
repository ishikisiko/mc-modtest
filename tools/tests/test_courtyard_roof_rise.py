"""Pavilion roof lift: preserve the eaves, tile skin and accepted main-house roof."""
import hashlib
import json
from pathlib import Path
import unittest

from tools.reference_courtyard.assembly_shapes import geometry


class PavilionRoofRiseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        config = json.loads((Path(__file__).parents[1] / 'reference_courtyard/assemblies.json').read_text())
        cls.variants = next(f['variants'] for f in config['families'] if f['id'] == 'courtyard_roof')
        cls.roofs = {v['key']: v for v in cls.variants}
        cls.cells = {key: geometry(cls.roofs[key]) for key in ('curve_pavilion', 'curve_gable')}

    @staticmethod
    def tile_columns(cells, z):
        columns = {}
        for (x, y, depth), material in cells.items():
            if depth == z and material == 'tile':
                columns.setdefault(x, []).append(y)
        return {x: sorted(ys) for x, ys in columns.items()}

    def test_lift_crest_one_block_with_same_eave_ends(self):
        for key in self.cells:
            variant = self.roofs[key]
            old = geometry(dict(variant, rise=34))
            self.assertEqual(50, variant['rise'])
            for z in range(16):
                current = self.tile_columns(self.cells[key], z)
                previous = self.tile_columns(old, z)
                for x in (135, 136):
                    self.assertEqual(16, current[x][-1] - previous[x][-1], (key, x, z))
                # Keep the outer block of each eave within one voxel of its old height.
                for x in [*range(16), *range(256, 272)]:
                    self.assertLessEqual(abs(current[x][-1] - previous[x][-1]), 1, (key, x, z))

    def test_tile_skin_is_shared_symmetric_continuous_and_inside_four_blocks(self):
        pavilion, gable = (self.cells[key] for key in ('curve_pavilion', 'curve_gable'))
        for z in range(16):
            body_columns = self.tile_columns(pavilion, z)
            gable_columns = self.tile_columns(gable, z)
            self.assertEqual({x: ys[-1] for x, ys in body_columns.items()},
                             {x: ys[-1] for x, ys in gable_columns.items()})
        for key, cells in self.cells.items():
            variant = self.roofs[key]
            old_cells = geometry(dict(variant, rise=34))
            self.assertEqual([17, 4, 1], variant['size'])
            self.assertEqual(64, max(y + 1 for x, y, z in cells))
            self.assertTrue(all(0 <= x < 272 and 0 <= y < 64 and 0 <= z < 16 for x, y, z in cells))
            self.assertEqual(cells, {(271 - x, y, z): m for (x, y, z), m in cells.items()})
            for z in range(16):
                columns = self.tile_columns(cells, z)
                previous = self.tile_columns(old_cells, z)
                self.assertEqual(set(range(272)), set(columns))
                for x, ys in columns.items():
                    self.assertEqual(list(range(ys[0], ys[-1] + 1)), ys, (key, x, z))
                    # The gable fascia already replaces one bottom tile row where
                    # the rolled and plain depth rows meet; retain that skin.
                    self.assertEqual(len(previous[x]), len(ys), (key, x, z))
                    if x < 271:
                        next_ys = columns[x + 1]
                        self.assertLessEqual(abs(ys[-1] - next_ys[-1]), 1, (key, x, z))
                        self.assertTrue(set(ys) & set(next_ys), (key, x, z))
            # Stable thickness/material/curve parameters retain the accepted style.
            for name, expected in {'material': 'tile', 'base_height': 13, 'curve_spread': .46,
                                   'eave_lift': 13, 'eave_power': 12, 'roof_thickness': 5,
                                   'fascia_depth': 2}.items():
                self.assertEqual(expected, variant[name], (key, name))
        self.assertEqual('curve_pavilion', self.variants[10]['key'])
        self.assertEqual('curve_gable', self.variants[11]['key'])

    def test_main_house_retains_accepted_low_two_tier_configuration(self):
        # Freeze the c5dc7e8d profiles while allowing the new internal purlins.
        main = {key: dict(self.roofs[key]) for key in
                ('curve_lower', 'curve_lower_sides', 'curve_lower_gable', 'curve_upper')}
        self.assertEqual([4, 10, 14, 20], main['curve_lower_sides'].pop('purlin_columns'))
        self.assertEqual(6, main['curve_lower_sides'].pop('purlin_width'))
        serialized = json.dumps(main, sort_keys=True, separators=(',', ':')).encode()
        self.assertEqual('e6ea5a7307785941ca0f3cb8c60764a08a3a1a957bb8873e5d2e1480738d87c2',
                         hashlib.sha256(serialized).hexdigest())
        for key, baseline_profile in main.items():
            baseline_tiles = {p for p, material in geometry(baseline_profile).items() if material == 'tile'}
            current_tiles = {p for p, material in geometry(self.roofs[key]).items() if material == 'tile'}
            self.assertEqual(baseline_tiles, current_tiles, key)


if __name__ == '__main__':
    unittest.main()
