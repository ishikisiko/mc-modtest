import json
import tempfile
import unittest
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import voxel_dump_to_structures as v2s  # noqa: E402
from buildgen.nbtread import read_gzipped_nbt, state_string  # noqa: E402
from validate_blueprint import validate_blueprint  # noqa: E402

STAIRS = "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"
WALL = "supplementaries:blackstone_tile_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]"

# World coords; the kept part spans x 10..12, y -60..-59, z 5..6 -> size [3, 2, 2].
DUMP = [
    [10, -60, 5, "minecraft:stone_bricks"],
    [12, -60, 6, STAIRS],
    [11, -59, 5, WALL],
    [12, -59, 6, "myvillage:plaque"],
    [11, -60, 6, "minecraft:grass_block[snowy=false]"],  # natural, dropped by default
    [13, -60, 7, "minecraft:dirt"],                      # natural, would widen the box
    [10, -61, 5, "minecraft:stone"],                     # below min y
    [10, -64, 5, "minecraft:bedrock"],
]


class VoxelDumpToStructuresTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.dump = self.dir / "dump.json"
        self.dump.write_text(json.dumps(DUMP), encoding="utf-8")

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def convert(self, **kwargs) -> dict:
        return v2s.convert(self.dump, self.dir / "out" / "site", min_y=-60, **kwargs)

    def test_blueprint(self) -> None:
        report = self.convert()
        self.assertEqual(report["size"], [3, 2, 2])
        self.assertEqual(report["blocks"], 4)
        self.assertEqual(report["world_min"], [10, -60, 5])
        data = json.loads(Path(report["paths"]["blueprint"]).read_text(encoding="utf-8"))
        self.assertEqual(validate_blueprint(data), [])
        self.assertEqual(data["schema_version"], 1)
        self.assertEqual(data["id"], "myvillage:site")
        self.assertEqual(data["origin"], [0, 0, 0])
        self.assertEqual(sorted(data["palette"].values()), [
            "minecraft:stone_brick_stairs", "minecraft:stone_bricks",
            "myvillage:plaque", "supplementaries:blackstone_tile_wall",
        ])
        by_pos = {tuple(b["pos"]): b for b in data["blocks"]}
        stairs = by_pos[(2, 0, 1)]
        self.assertEqual(data["palette"][stairs["palette"]], "minecraft:stone_brick_stairs")
        self.assertEqual(stairs["state"], {"facing": "west", "half": "bottom",
                                           "shape": "straight", "waterlogged": "false"})
        wall = by_pos[(1, 1, 0)]
        self.assertEqual(data["palette"][wall["palette"]], "supplementaries:blackstone_tile_wall")
        self.assertEqual(wall["state"]["east"], "tall")
        self.assertNotIn("state", by_pos[(0, 0, 0)])

    def test_nbt(self) -> None:
        report = self.convert()
        name, root = read_gzipped_nbt(report["paths"]["nbt"])
        self.assertEqual(name, "")
        self.assertEqual(root["size"], [3, 2, 2])
        self.assertEqual(root["DataVersion"], v2s.DEFAULT_DATA_VERSION)
        self.assertEqual(root["entities"], [])
        self.assertEqual(len(root["blocks"]), 4)
        cells = {tuple(b["pos"]): state_string(root["palette"][b["state"]]) for b in root["blocks"]}
        self.assertEqual(cells[(2, 0, 1)], STAIRS)
        self.assertEqual(cells[(1, 1, 0)], WALL)
        self.assertEqual(cells[(2, 1, 1)], "myvillage:plaque")
        self.assertEqual(cells[(0, 0, 0)], "minecraft:stone_bricks")

    def test_schem(self) -> None:
        report = self.convert()
        name, root = read_gzipped_nbt(report["paths"]["schem"])
        self.assertEqual(name, "Schematic")
        self.assertEqual(root["Version"], 2)
        self.assertEqual(root["DataVersion"], v2s.DEFAULT_DATA_VERSION)
        self.assertEqual((root["Width"], root["Height"], root["Length"]), (3, 2, 2))
        self.assertEqual(root["Offset"], [0, 0, 0])
        self.assertEqual(root["BlockEntities"], [])
        palette = root["Palette"]
        self.assertEqual(root["PaletteMax"], len(palette))
        self.assertEqual(palette["minecraft:air"], 0)
        self.assertEqual(sorted(palette.values()), list(range(len(palette))))
        self.assertIn(STAIRS, palette)
        self.assertIn(WALL, palette)
        ids = v2s.decode_varints(root["BlockData"])
        self.assertEqual(len(ids), 3 * 2 * 2)
        inverse = {v: k for k, v in palette.items()}

        def at(x, y, z):
            return inverse[ids[v2s.schem_index(x, y, z, 3, 2)]]

        # index = (y * Length + z) * Width + x
        self.assertEqual(v2s.schem_index(2, 1, 1, 3, 2), (1 * 2 + 1) * 3 + 2)
        self.assertEqual(ids[0], palette["minecraft:stone_bricks"])  # x=0,y=0,z=0
        self.assertEqual(ids[1], 0)                                    # x=1,y=0,z=0 is air
        self.assertEqual(ids[5], palette[STAIRS])                      # x=2,y=0,z=1
        self.assertEqual(ids[7], palette[WALL])                        # x=1,y=1,z=0
        self.assertEqual(at(2, 1, 1), "myvillage:plaque")
        self.assertEqual(sum(1 for i in ids if i != 0), 4)
        self.assertEqual(report["schem_normalised"], [])

    def test_include_natural(self) -> None:
        report = self.convert(include_natural=True)
        self.assertEqual(report["size"], [4, 2, 3])
        self.assertEqual(report["blocks"], 6)
        _, root = read_gzipped_nbt(report["paths"]["nbt"])
        cells = {tuple(b["pos"]): state_string(root["palette"][b["state"]]) for b in root["blocks"]}
        self.assertEqual(cells[(1, 0, 1)], "minecraft:grass_block[snowy=false]")
        self.assertEqual(cells[(3, 0, 2)], "minecraft:dirt")

    def test_varint_round_trip(self) -> None:
        buf = bytearray()
        values = [0, 1, 127, 128, 300, 16384]
        for value in values:
            v2s.encode_varint(value, buf)
        self.assertEqual(buf[:3], bytearray([0, 1, 127]))
        self.assertEqual(buf[3:5], bytearray([0x80, 0x01]))
        self.assertEqual(v2s.decode_varints(buf), values)


if __name__ == "__main__":
    unittest.main()
