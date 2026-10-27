import copy
import io
import json
from pathlib import Path
import unittest

from PIL import Image

from tools.forging import patterns

ROOT = Path(__file__).resolve().parents[2]


class ForgingPatternArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.art = patterns.PatternArt(ROOT)
        cls.rules = json.loads((ROOT / 'src/main/resources/data/myvillage/forging/rules.json').read_text())['recipes']
        cls.resources = patterns.outputs(ROOT, cls.art.catalogue)

    def test_every_registered_pattern_uses_its_own_generated_scroll(self):
        base = ROOT / 'src/main/resources/assets/myvillage'
        seen, small = set(), set()
        for recipe in self.rules:
            item = recipe['pattern'].split(':')[1]
            model_path = base / f'models/item/{item}.json'
            texture_path = base / f'textures/item/{item}.png'
            model = json.loads(self.resources[model_path])
            self.assertEqual('minecraft:item/generated', model['parent'])
            self.assertEqual({'layer0': 'myvillage:item/' + item}, model['textures'])
            self.assertEqual(self.resources[model_path], model_path.read_bytes())
            self.assertEqual(self.resources[texture_path], texture_path.read_bytes())
            image = Image.open(io.BytesIO(self.resources[texture_path]))
            self.assertEqual('RGBA', image.mode)
            self.assertEqual((64, 64), image.size)
            self.assertEqual((0, 255), image.getchannel('A').getextrema())
            self.assertEqual(0, image.getpixel((0, 0))[3])
            seen.add(image.tobytes())
            small.add(image.resize((16,16), Image.Resampling.NEAREST).tobytes())
        self.assertEqual(len(self.rules), len(seen))
        self.assertEqual(len(self.rules), len(small))

    def test_all_items_share_the_same_support_outside_the_emblem(self):
        config = self.art.config
        scale = config['texture_size'] // config['grid']
        x0, y0, x1, y1 = [v*scale for v in config['layout']['emblem_box']]
        size = config['texture_size']
        base = patterns.support(config).resize((size,size), Image.Resampling.NEAREST)
        for recipe in self.rules:
            icon = self.art.icon(recipe['id'])
            for y in range(size):
                for x in range(size):
                    if not (x0 <= x < x1 and y0 <= y < y1):
                        self.assertEqual(base.getpixel((x,y)), icon.getpixel((x,y)), recipe['id'])

    def test_quality_changes_only_the_support_and_preserves_the_equipment(self):
        config = self.art.config
        scale = config['texture_size'] // config['grid']
        box = tuple(v*scale for v in config['layout']['emblem_box'])
        icons = [self.art.icon('xuantie_helmet', quality) for quality in config['qualities']]
        self.assertEqual(5, len({im.tobytes() for im in icons}))
        self.assertEqual(1, len({im.crop(box).tobytes() for im in icons}))
        self.assertEqual(1, len({im.getchannel('A').tobytes() for im in icons}))
        with self.assertRaises(ValueError):
            self.art.icon('xuantie_helmet', 'not_a_quality')

    def test_paired_illustrations_do_not_mutate_source_equipment_geometry(self):
        art = patterns.PatternArt(ROOT)
        before = copy.deepcopy(art.catalogue.sources)
        art.icon('xuantie_leggings')
        art.icon('xuantie_boots')
        self.assertEqual(before, art.catalogue.sources)


if __name__ == '__main__':
    unittest.main()
