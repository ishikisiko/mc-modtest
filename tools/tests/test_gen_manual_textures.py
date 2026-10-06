from __future__ import annotations

import contextlib
import io
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from tools import gen_manual_textures as gen

MODEL_DIR = gen.ROOT / "src" / "main" / "resources" / "assets" / "myvillage" / "models" / "item"
GRADES = ("huang", "xuan", "di", "tian")


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
    stride = width * 4 + 1
    rows = []
    for y in range(height):
        line = raw[y * stride:(y + 1) * stride]
        assert line[0] == 0
        rows.append([tuple(line[1 + x * 4:5 + x * 4]) for x in range(width)])
    return width, height, rows


class ManualTexturesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.files = gen.outputs()

    def layer(self, name: str) -> list[list[tuple[int, int, int, int]]]:
        width, height, rows = decode_rgba(self.files[gen.TEXTURE_DIR / f"{name}.png"])
        self.assertEqual((width, height), (16, 16))
        return rows

    def test_eight_files_two_per_category(self) -> None:
        expected = {gen.TEXTURE_DIR / f"manual_{c}{suffix}.png" for c in gen.CATEGORIES for suffix in ("", "_tint")}
        self.assertEqual(set(self.files), expected)
        self.assertEqual(gen.CATEGORIES, ("core", "active", "movement", "body"))

    def test_layers_never_overlap_and_alpha_is_binary(self) -> None:
        for category in gen.CATEGORIES:
            detail = self.layer(f"manual_{category}")
            tint = self.layer(f"manual_{category}_tint")
            for y in range(16):
                for x in range(16):
                    self.assertIn(detail[y][x][3], (0, 255))
                    self.assertIn(tint[y][x][3], (0, 255))
                    self.assertFalse(detail[y][x][3] and tint[y][x][3], f"{category} {x},{y} is in both layers")

    def test_tint_mask_is_grey_and_mostly_full_white(self) -> None:
        for category in gen.CATEGORIES:
            tint = [p for row in self.layer(f"manual_{category}_tint") for p in row if p[3]]
            self.assertTrue(all(p[0] == p[1] == p[2] for p in tint), category)
            self.assertGreaterEqual(len(tint), 25, f"{category}: too little tinted area")
            self.assertGreaterEqual(max(p[0] for p in tint), 228, category)

    def test_each_icon_is_an_outlined_shape_with_detail(self) -> None:
        outline = gen.DETAIL["o"] + (255,)
        for category in gen.CATEGORIES:
            detail = self.layer(f"manual_{category}")
            tint = self.layer(f"manual_{category}_tint")
            filled = {(x, y) for y in range(16) for x in range(16) if detail[y][x][3] or tint[y][x][3]}
            self.assertGreater(len(filled), 60, f"{category}: too small")
            self.assertLess(len(filled), 230, f"{category}: a flat square, not a silhouette")
            colours = {detail[y][x] for (x, y) in filled if detail[y][x][3]}
            self.assertIn(outline, colours, category)
            self.assertGreaterEqual(len(colours), 3, f"{category}: needs detail strokes besides the outline")
            # every tinted pixel sits inside the outline: none touches the canvas edge or transparency
            for (x, y) in filled:
                if tint[y][x][3]:
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        self.assertIn((nx, ny), filled, f"{category}: tinted {x},{y} is on the silhouette edge")

    def test_the_four_silhouettes_differ(self) -> None:
        shapes = []
        for category in gen.CATEGORIES:
            detail = self.layer(f"manual_{category}")
            tint = self.layer(f"manual_{category}_tint")
            shapes.append(frozenset((x, y) for y in range(16) for x in range(16)
                                    if detail[y][x][3] or tint[y][x][3]))
        self.assertEqual(len(set(shapes)), 4)

    def test_generation_is_deterministic_and_check_detects_drift(self) -> None:
        self.assertEqual(gen.outputs(), self.files)
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stdout(io.StringIO()), \
                contextlib.redirect_stderr(io.StringIO()):
            directory = Path(temp)
            self.assertEqual(gen.main(["--texture-dir", str(directory)]), 0)
            self.assertEqual(gen.main(["--check", "--texture-dir", str(directory)]), 0)
            self.assertEqual(gen.main(["--texture-dir", str(directory)]), 0)
            target = directory / "manual_body_tint.png"
            target.write_bytes(target.read_bytes() + b"\0")
            self.assertEqual(gen.main(["--check", "--texture-dir", str(directory)]), 1)
            target.unlink()
            self.assertEqual(gen.main(["--check", "--texture-dir", str(directory)]), 1)

    def test_committed_textures_match_the_generator(self) -> None:
        self.assertEqual(gen.main(["--check"]), 0)

    def test_models_stack_detail_under_tint_and_items_share_them(self) -> None:
        for category in gen.CATEGORIES:
            model = json.loads((MODEL_DIR / f"manual_{category}.json").read_text(encoding="utf-8"))
            self.assertEqual(model["parent"], "minecraft:item/generated")
            self.assertEqual(model["textures"], {"layer0": f"myvillage:item/manual_{category}",
                                                 "layer1": f"myvillage:item/manual_{category}_tint"})
            for grade in GRADES:
                item = json.loads((MODEL_DIR / f"manual_{category}_{grade}.json").read_text(encoding="utf-8"))
                self.assertEqual(item, {"parent": f"myvillage:item/manual_{category}"})


if __name__ == "__main__":
    unittest.main()
