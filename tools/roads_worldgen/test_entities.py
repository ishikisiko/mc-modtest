"""检查空实体分区、截断证据和真实物品记录的读取边界。"""
import struct
import sys
from pathlib import Path
import tempfile
import unittest
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from roads_worldgen.entities import saved_items


class EntityRegionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.world = Path(self.temporary.name)
        (self.world / 'entities').mkdir()
        self.path = self.world / 'entities/r.0.0.mca'

    def tearDown(self):
        self.temporary.cleanup()

    def test_empty_region_placeholder_is_explicit(self):
        self.path.write_bytes(b'')
        regions, items = saved_items(self.world)
        self.assertEqual([], items)
        self.assertEqual(0, regions[0]['chunks'])
        self.assertTrue(regions[0]['empty_region_placeholder'])

    def test_nonempty_truncated_header_is_not_ignored(self):
        for size in [1, 4096, 8191]:
            with self.subTest(size=size):
                self.path.write_bytes(bytes(size))
                with self.assertRaisesRegex(ValueError, 'truncated Anvil header'):
                    saved_items(self.world)

    def test_stored_item_record_cannot_be_silently_dropped(self):
        def text(value):
            data = value.encode()
            return struct.pack('>H', len(data)) + data
        entity = b'\x08' + text('id') + text('minecraft:item') + b'\x00'
        nbt = b'\x0a\x00\x00\x09' + text('Entities') + b'\x0a' + struct.pack('>i', 1) + entity + b'\x00'
        compressed = zlib.compress(nbt)
        header = struct.pack('>I', (2 << 8) | 1) + bytes(8192 - 4)
        body = struct.pack('>I', len(compressed) + 1) + b'\x02' + compressed
        self.path.write_bytes(header + body.ljust(4096, b'\x00'))
        regions, items = saved_items(self.world)
        self.assertEqual([{'id': 'minecraft:item'}], items)
        self.assertEqual(1, regions[0]['chunks'])
        self.assertFalse(regions[0]['empty_region_placeholder'])


if __name__ == '__main__':
    unittest.main()
