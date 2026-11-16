"""千机阁屋面必须遮住室内；覆盖奇偶尺寸、长宽方向和八角坡面的斜向接缝。"""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from qianji.model import hip, octagonal


class RoofModel:
    def __init__(self):
        self.material = {"roof": "minecraft:deepslate_tiles", "roof_slab": "minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]"}
        self.blocks = {}

    def put(self, x, y, z, state, layer):
        self.blocks[x, y, z] = self.material.get(state, state)

    def box(self, x, y, z, w, h, d, state, layer):
        for px in range(x, x + w):
            for py in range(y, y + h):
                for pz in range(z, z + d):
                    self.put(px, py, pz, state, layer)


class QianjiRoofTests(unittest.TestCase):
    def check_weather_cover(self, shape, w, d):
        m = RoofModel()
        x, y, z = 8, 20, 8
        (hip if shape == "hip" else octagonal)(m, x, y, z, w, d)
        covered = {(px, pz) for px, py, pz in m.blocks if py >= y}
        cut = (min(w, d) + 4) // 5
        expected = set()
        for px in range(x - 2, x + w + 2):
            for pz in range(z - 2, z + d + 2):
                edge_x = min(px - (x - 2), x + w + 1 - px)
                edge_z = min(pz - (z - 2), z + d + 1 - pz)
                if shape == "hip" or edge_x + edge_z >= cut:
                    expected.add((px, pz))
        self.assertFalse(expected - covered, (shape, w, d, sorted(expected - covered)[:24]))
        self.assertLessEqual(max(py for px, py, pz in m.blocks), y + (min(w, d) + 3) // 4)

    def test_rectangular_roofs_cover_even_odd_and_rotated_room_sizes(self):
        for w, d in [(18, 30), (30, 18), (17, 29), (29, 17), (18, 18), (17, 17)]:
            with self.subTest(w=w, d=d):
                self.check_weather_cover("hip", w, d)

    def test_octagonal_roofs_close_diagonal_seams_and_the_centre(self):
        for w, d in [(18, 30), (30, 18), (17, 29), (29, 17), (18, 18), (17, 17)]:
            with self.subTest(w=w, d=d):
                self.check_weather_cover("octagonal", w, d)

    def test_actual_branch_room_top_floors_have_complete_weather_cover(self):
        root = Path(__file__).resolve().parents[2]
        variants = set(json.loads((root / "blueprints/qianji/placement.json").read_text())["regions"].values())
        for variant in sorted(variants - {"zhongzhou_headquarters"}):
            spec = json.loads((root / "blueprints/qianji" / (variant + ".json")).read_text())
            for room in spec["rooms"]:
                level = room["levels"][-1]
                w, d = (v - 2 * level["inset"] for v in room["box"][2:])
                shape = "octagonal" if room["roof"] == "pyramidal_roof" else "hip"
                with self.subTest(variant=variant, room=room["id"]):
                    self.check_weather_cover(shape, w, d)


if __name__ == "__main__":
    unittest.main()
