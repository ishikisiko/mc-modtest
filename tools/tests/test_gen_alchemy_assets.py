"""Deterministic alchemy art must remain distinct and checkable without a renderer."""
from __future__ import annotations

import contextlib
import io
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from tools import gen_alchemy_assets as gen
from tools import validate_alchemy as validator


def decode_rgba(raw):
    if raw[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('not PNG')
    offset, width, height, compressed = 8, 0, 0, b''
    while offset < len(raw):
        length = struct.unpack('>I', raw[offset:offset + 4])[0]
        kind, body = raw[offset + 4:offset + 8], raw[offset + 8:offset + 8 + length]
        crc = struct.unpack('>I', raw[offset + 8 + length:offset + 12 + length])[0]
        if zlib.crc32(kind + body) & 0xffffffff != crc:
            raise ValueError('corrupt PNG CRC')
        if kind == b'IHDR':
            width, height, depth, color = struct.unpack('>IIBB', body[:10])
            if (depth, color) != (8, 6):
                raise ValueError('expected RGBA8')
        elif kind == b'IDAT':
            compressed += body
        offset += length + 12
    data = zlib.decompress(compressed)
    rows = []
    stride = width * 4 + 1
    for y in range(height):
        row = data[y * stride:(y + 1) * stride]
        if len(row) != stride or row[0] != 0:
            raise ValueError('expected complete unfiltered rows')
        rows.append([tuple(row[1 + x * 4:5 + x * 4]) for x in range(width)])
    return width, height, rows


class AlchemyAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.files = gen.outputs(self.root)
        self.assets = self.root / validator.ASSETS

    def test_exact_thirteen_models_and_twelve_dedicated_icons(self):
        models = {p.name for p in self.files if p.parent == self.assets / 'models/item'}
        textures = {p.name for p in self.files if p.parent == self.assets / 'textures/item'}
        self.assertEqual({f'{item}.json' for item in validator.ITEMS}, models)
        self.assertEqual({f'{item}.png' for item in validator.ITEMS if item != 'alchemy_furnace'}, textures)
        for item in validator.ITEMS:
            model = json.loads(self.files[self.assets / f'models/item/{item}.json'])
            if item == 'alchemy_furnace':
                self.assertEqual('myvillage:block/alchemy_furnace', model['parent'])
            else:
                self.assertEqual({'layer0': f'myvillage:item/{item}'}, model['textures'])

    def test_formula_acquisition_has_no_ambiguous_ingredient_multisets(self):
        recipes = [json.loads(raw) for path,raw in self.files.items() if path.name.startswith('formula_') and path.parent.name=='recipe']
        self.assertEqual(8,len(recipes))
        signatures = [tuple(sorted(json.dumps(i,sort_keys=True) for i in recipe['ingredients'])) for recipe in recipes]
        self.assertEqual(len(recipes),len(set(signatures)))

    def test_sprites_have_transparency_binary_alpha_and_multiple_colors(self):
        for item in validator.ITEMS[1:]:
            with self.subTest(item=item):
                width, height, rows = decode_rgba(self.files[self.assets / f'textures/item/{item}.png'])
                self.assertEqual((32, 32), (width, height))
                pixels = [pixel for row in rows for pixel in row]
                self.assertTrue(all(p[3] in (0, 255) for p in pixels))
                filled = [p for p in pixels if p[3]]
                self.assertGreater(len(filled), 80)
                self.assertLess(len(filled), width * height * .85)
                self.assertGreater(len(set(filled)), 5)

    def test_six_herb_silhouettes_and_all_twenty_one_icons_differ(self):
        masks = []
        for herb in validator.HERBS:
            _, _, rows = decode_rgba(self.files[self.assets / f'textures/item/{herb}.png'])
            masks.append(tuple(p[3] for row in rows for p in row))
        self.assertEqual(6, len(set(masks)))
        self.assertEqual(21, len({self.files[self.assets / f'textures/item/{item}.png'] for item in validator.ITEMS[1:]}))

    def test_furnace_parts_and_lit_geometry_keep_same_shape(self):
        normal = json.loads(self.files[self.assets / 'models/block/alchemy_furnace.json'])
        lit = json.loads(self.files[self.assets / 'models/block/alchemy_furnace_lit.json'])
        self.assertGreaterEqual(len(normal['elements']), 8)
        geometry = lambda model: [(e['from'], e['to']) for e in model['elements']]
        self.assertEqual(geometry(normal), geometry(lit))
        self.assertNotEqual(normal, lit, 'burning must visibly change the fire vent')
        self.assertNotIn(([0, 0, 0], [16, 16, 16]), geometry(normal))
        self.assertTrue(any(e['from'][1] == 0 for e in normal['elements']), 'legs must reach the ground')
        self.assertTrue(any(e['to'][1] == 16 for e in normal['elements']), 'knob must reach the modeled top')

    def test_generation_is_deterministic_and_check_detects_corruption_and_missing_output(self):
        self.assertEqual(self.files, gen.outputs(self.root))
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(0, gen.main(['--root', str(self.root)]))
            self.assertEqual(0, gen.main(['--root', str(self.root), '--check']))
            target = self.assets / 'textures/item/pill_huiling.png'
            target.write_bytes(target.read_bytes() + b'corrupt')
            self.assertEqual(1, gen.main(['--root', str(self.root), '--check']))
            self.assertEqual(0, gen.main(['--root', str(self.root)]))
            target.unlink()
            self.assertEqual(1, gen.main(['--root', str(self.root), '--check']))

    def test_merges_preserve_unrelated_lang_and_mining_entries_without_duplicates(self):
        lang = self.assets / 'lang/zh_cn.json'
        lang.parent.mkdir(parents=True)
        lang.write_text(json.dumps({'test.unrelated': '保留'}))
        tag = self.root / validator.RESOURCES / 'data/minecraft/tags/block/mineable/pickaxe.json'
        tag.parent.mkdir(parents=True)
        tag.write_text(json.dumps({'replace': False, 'values': ['myvillage:scripture_shelf']}))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gen.main(['--root', str(self.root)]))
            self.assertEqual(0, gen.main(['--root', str(self.root)]))
        self.assertEqual('保留', json.loads(lang.read_text())['test.unrelated'])
        values = json.loads(tag.read_text())['values']
        self.assertIn('myvillage:scripture_shelf', values)
        self.assertEqual(1, values.count('myvillage:alchemy_furnace'))

    def test_shipped_assets_have_no_generator_drift(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gen.main(['--check']))


if __name__ == '__main__':
    unittest.main()
