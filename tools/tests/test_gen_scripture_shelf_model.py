from __future__ import annotations

import contextlib
import copy
import io
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from tools import gen_scripture_shelf_model as gen


class ScriptureShelfModelTest(unittest.TestCase):
    def test_baked_vertices_fit_runtime_shape_and_reach_contract_dimensions(self):
        points = [point for element in gen.elements() for point in gen.vertices(element)]
        expected_min, expected_max = ((.4, 0, 2.4), (15.6, 27.52, 13.6))
        for axis in range(3):
            self.assertAlmostEqual(min(p[axis] for p in points), expected_min[axis], places=5)
            self.assertAlmostEqual(max(p[axis] for p in points), expected_max[axis], places=5)
        for element in gen.elements():
            self.assertTrue(all(-16 <= v <= 32 for key in ("from", "to") for v in element[key]))
            self.assertIn(element.get("rotation", {}).get("angle", 0), (0, -22.5, 22.5, -45, 45))
            for face in element["faces"].values():
                self.assertTrue(all(0 <= v <= 16 for v in face["uv"]))
                self.assertEqual(face["texture"], "#atlas")

    def test_two_halves_reassemble_exactly_without_gap_or_duplicate_volume(self):
        whole = gen.elements()
        fragments = {}
        for half in ("lower", "upper"):
            for element in gen.split_elements(whole, half):
                self.assertGreaterEqual(min(p[1] for p in gen.vertices(element)), -1e-5)
                self.assertLessEqual(max(p[1] for p in gen.vertices(element)), 16 + 1e-5)
                reconstructed = copy.deepcopy(element)
                if half == "upper":
                    for key in ("from", "to"):
                        reconstructed[key][1] = round(reconstructed[key][1] + 16, 5)
                    if "rotation" in reconstructed:
                        reconstructed["rotation"]["origin"][1] = round(reconstructed["rotation"]["origin"][1] + 16, 5)
                fragments.setdefault(element["name"], []).append(reconstructed)
        self.assertEqual(set(fragments), {element["name"] for element in whole})
        for original in whole:
            parts = fragments[original["name"]]
            if len(parts) == 1:
                self.assertEqual(parts[0], original)
            else:
                self.assertEqual(len(parts), 2)
                lower, upper = parts
                self.assertEqual(lower["from"], original["from"])
                self.assertEqual(upper["to"], original["to"])
                self.assertEqual(lower["to"][1], upper["from"][1])
                self.assertEqual(lower["to"][1], 16)
                self.assertEqual(lower["faces"], upper["faces"])
                self.assertEqual(lower["faces"], original["faces"])
                for axis in (0, 2):
                    self.assertEqual(lower["to"][axis], original["to"][axis])
                    self.assertEqual(upper["from"][axis], original["from"][axis])

    def test_rotated_seam_crossing_is_rejected_rather_than_clipped(self):
        element = copy.deepcopy(gen.elements()[0])
        element["rotation"] = {"axis": "x", "angle": -22.5, "origin": [8, 16, 8]}
        for half in ("lower", "upper"):
            with self.assertRaisesRegex(ValueError, "Rotated element crosses"):
                gen.split_elements([element], half)

    def test_book_pages_use_upper_written_faces_and_scroll_ends_face_north(self):
        elements = {element["name"]: element for element in gen.elements()}
        for index, material in enumerate(("page_left", "page_right")):
            book = elements[f"open_page_{index}"]
            self.assertEqual(book["faces"]["up"]["uv"], gen.uv(material))
            self.assertEqual(book["rotation"]["angle"], -22.5)
            self.assertGreater(min(p[1] for p in gen.vertices(book)), 16)
        for index in range(5):
            scroll = elements[f"scroll_end_{index}"]
            self.assertEqual(scroll["faces"]["north"]["uv"], gen.uv("scroll_end"))

    def test_texture_is_valid_opaque_pixel_atlas_with_material_detail(self):
        data = gen.png(gen.texture_rows())
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        offset, idat = 8, b""
        while offset < len(data):
            length = struct.unpack(">I", data[offset:offset + 4])[0]
            kind, body = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + length]
            crc = struct.unpack(">I", data[offset + 8 + length:offset + 12 + length])[0]
            self.assertEqual(zlib.crc32(kind + body) & 0xffffffff, crc)
            if kind == b"IHDR":
                self.assertEqual(struct.unpack(">IIBBBBB", body), (128, 128, 8, 6, 0, 0, 0))
            elif kind == b"IDAT":
                idat += body
            offset += 12 + length
        raw = zlib.decompress(idat)
        self.assertEqual(len(raw), 128 * (128 * 4 + 1))
        for y in range(128):
            line = raw[y * 513:(y + 1) * 513]
            self.assertEqual(line[0], 0)
            self.assertEqual(set(line[4::4]), {255})
        rows = gen.texture_rows()
        palettes = []
        for name, (x0, y0, x1, y1) in gen.REGIONS.items():
            colours = frozenset(rows[y][x] for y in range(y0, y1) for x in range(x0, x1))
            self.assertGreater(len(colours), 2, name)
            palettes.append(colours)
        # The two written page tiles intentionally share a material palette.
        self.assertGreaterEqual(len(set(palettes)), len(palettes) - 1)

    def test_blockstate_all_directions_split_models_and_full_inventory_parent(self):
        files = gen.outputs()
        state = json.loads(files[gen.ASSETS / "blockstates/scripture_shelf.json"])
        expected = {f"facing={facing},half={half}" for facing in ("north", "east", "south", "west")
                    for half in ("lower", "upper")}
        self.assertEqual(set(state["variants"]), expected)
        for key, variant in state["variants"].items():
            half = key.split("half=")[1]
            self.assertEqual(variant["model"], "myvillage:block/scripture_shelf_" + half)
        item = json.loads(files[gen.ASSETS / "models/item/scripture_shelf.json"])
        self.assertEqual(item["parent"], "myvillage:block/scripture_shelf")
        self.assertEqual(len(files), 6)
        for path, data in files.items():
            if path.suffix == ".json" and "models/block" in str(path):
                obj = json.loads(data)
                self.assertEqual(obj["textures"]["atlas"], "myvillage:block/scripture_shelf")

    def test_deterministic_generation_and_missing_or_drifted_output_detection(self):
        self.assertEqual(gen.outputs(), gen.outputs())
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stdout(io.StringIO()):
            assets = Path(temp)
            args = ["--assets-dir", str(assets)]
            self.assertEqual(gen.main(args), 0)
            self.assertEqual(gen.main(["--check"] + args), 0)
            path = assets / "models/block/scripture_shelf_upper.json"
            path.write_bytes(path.read_bytes() + b" ")
            self.assertEqual(gen.main(["--check"] + args), 1)
            self.assertEqual(gen.main(args), 0)
            path.unlink()
            self.assertEqual(gen.main(["--check"] + args), 1)

    def test_committed_resources_match(self):
        self.assertEqual(gen.main(["--check"]), 0)


if __name__ == "__main__":
    unittest.main()
