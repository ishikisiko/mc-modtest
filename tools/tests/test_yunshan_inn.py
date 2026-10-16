"""Regression checks for the hand-authored inn's stair and entrance routes."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from yunshan.build_inn import build, check_walks
from json_to_nbt import expand_structure


class InnAccessTests(unittest.TestCase):
    def setUp(self):
        self.source = ROOT / 'blueprints/yunshan/south_gate_inn_v1.json'
        self.data = json.loads(self.source.read_text())
        self.size, _, self.blocks, _ = expand_structure(self.data)

    def test_closed_entrance_is_rejected(self):
        for x in range(5, 8):
            for y in range(1, 4):
                self.blocks[x, y, 3] = 'minecraft:stone_bricks'
        with self.assertRaisesRegex(AssertionError, 'unreachable'):
            check_walks(self.blocks, self.data['walk_targets'], self.size)

    def test_filled_stairwell_is_rejected(self):
        for z in range(6, 11):
            self.blocks[6, 5, z] = 'minecraft:spruce_planks'
        with self.assertRaisesRegex(AssertionError, 'unreachable'):
            check_walks(self.blocks, self.data['walk_targets'], self.size)

    def test_wrong_stair_direction_is_rejected(self):
        self.blocks[6, 3, 8] = 'minecraft:spruce_stairs[facing=north,half=bottom]'
        with self.assertRaisesRegex(AssertionError, 'unreachable'):
            check_walks(self.blocks, self.data['walk_targets'], self.size)

    def test_reproducible_exports_and_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp)/'a', Path(tmp)/'b'
            build(self.source, a)
            build(self.source, b)
            for suffix in ('nbt', 'schem', 'blueprint.json'):
                name = f'yunshan_south_gate_inn_v1.{suffix}'
                self.assertEqual((a/name).read_bytes(), (b/name).read_bytes())


if __name__ == '__main__':
    unittest.main()
