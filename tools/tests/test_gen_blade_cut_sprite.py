from __future__ import annotations

import struct
import unittest
import zlib

from tools import gen_blade_cut_sprite as sprite


def decode_rgba(data: bytes) -> tuple[int, int, list[list[tuple[int, int, int, int]]]]:
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    offset = 8
    idat = b""
    width = height = 0
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + length]
        crc = struct.unpack(">I", data[offset + 8 + length:offset + 12 + length])[0]
        assert zlib.crc32(kind + body) & 0xFFFFFFFF == crc
        if kind == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
            assert (depth, colour) == (8, 6)
        elif kind == b"IDAT":
            idat += body
        offset += 12 + length
    raw = zlib.decompress(idat)
    rows = []
    stride = width * 4 + 1
    for y in range(height):
        line = raw[y * stride:(y + 1) * stride]
        assert line[0] == 0
        rows.append([tuple(line[1 + x * 4:5 + x * 4]) for x in range(width)])
    return width, height, rows


class BladeCutSpriteTest(unittest.TestCase):
    def test_sprite_is_a_square_white_slash_on_transparency(self) -> None:
        width, height, rows = decode_rgba(sprite.generate())
        self.assertEqual((width, height), (32, 32))
        self.assertTrue(all(pixel[:3] == (255, 255, 255) for row in rows for pixel in row))
        self.assertEqual(rows[0][0][3], 0)
        self.assertEqual(rows[31][31][3], 0)
        opaque = [(x, y) for y, row in enumerate(rows) for x, pixel in enumerate(row) if pixel[3] >= 200]
        self.assertTrue(opaque)
        # A thin horizontal line: long, and at most about 3 px thick anywhere.
        xs = [x for x, _ in opaque]
        self.assertGreaterEqual(max(xs) - min(xs), 16)
        for column in set(xs):
            self.assertLessEqual(sum(1 for x, _ in opaque if x == column), 3)

    def test_ends_taper_to_nothing(self) -> None:
        _, _, rows = decode_rgba(sprite.generate())
        column_alpha = [max(row[x][3] for row in rows) for x in range(32)]
        self.assertEqual(column_alpha[0], 0)
        self.assertEqual(column_alpha[31], 0)
        self.assertLess(column_alpha[2], column_alpha[16])

    def test_committed_sprite_matches_the_generator(self) -> None:
        self.assertEqual(sprite.main(["--check"]), 0)


if __name__ == "__main__":
    unittest.main()
