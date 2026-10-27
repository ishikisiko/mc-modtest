import io
import json
import tempfile
from pathlib import Path
import unittest

from PIL import Image

from tools import gen_alchemy_assets, gen_wilderness_roster, scroll_art
from tools.pill_art import formulas

ROOT = Path(__file__).resolve().parents[2]


class FormulaScrollTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules = json.loads((ROOT/'src/main/resources/data/myvillage/alchemy/rules.json').read_text())
        cls.config = scroll_art.load(ROOT)

    def test_both_owners_emit_disjoint_complete_formula_resources(self):
        emitted = []
        seen, reduced = set(), set()
        for owner in (gen_alchemy_assets, gen_wilderness_roster):
            files = owner.outputs()
            formulas_out = {p:data for p,data in files.items() if p.parent.name == 'item' and p.name.startswith('formula_') and p.suffix == '.png'}
            emitted.append(set(formulas_out))
            for path,data in formulas_out.items():
                self.assertEqual(data, path.read_bytes())
                image = Image.open(io.BytesIO(data)).convert('RGBA')
                self.assertEqual((64,64), image.size)
                self.assertEqual((0,255),image.getchannel('A').getextrema())
                self.assertEqual(0,image.getpixel((0,0))[3])
                seen.add(image.tobytes())
                reduced.add(image.resize((16,16),Image.Resampling.NEAREST).tobytes())
        self.assertFalse(emitted[0] & emitted[1])
        expected = {ROOT/'src/main/resources/assets/myvillage/textures/item'/f"{r['formula'].split(':')[1]}.png" for r in self.rules['recipes']}
        self.assertEqual(expected, emitted[0] | emitted[1])
        self.assertEqual(14,len(seen));self.assertEqual(14,len(reduced))

    def test_bamboo_and_linen_have_a_distinct_outline_from_forging(self):
        forge = scroll_art.support(self.config)
        alchemy = scroll_art.support(self.config, style='alchemy')
        self.assertNotEqual(forge.getchannel('A').tobytes(),alchemy.getchannel('A').tobytes())
        self.assertEqual(0,forge.getpixel((29,9))[3])
        self.assertEqual(255,alchemy.getpixel((29,9))[3])

    def test_fixed_quality_changes_edging_without_recoloring_the_medicine(self):
        scale = self.config['texture_size']//self.config['grid']
        box = tuple(v*scale for v in self.config['styles']['alchemy']['emblem_box'])
        for kinds in (('zhuji_low','zhuji_middle','zhuji_high'),('buji_middle','buji_high')):
            images = [formulas.icon(kind) for kind in kinds]
            self.assertEqual(len(kinds),len({im.tobytes() for im in images}))
            self.assertEqual(1,len({im.crop(box).tobytes() for im in images}))

    def test_grade_is_read_from_recipe_not_the_formula_name(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            style = root/'tools/scroll_art.json';style.parent.mkdir(parents=True)
            style.write_text(json.dumps(self.config))
            rules = json.loads(json.dumps(self.rules))
            recipe = next(r for r in rules['recipes'] if r['id']=='zhuji_high')
            recipe.pop('fixed_quality')
            target = root/'src/main/resources/data/myvillage/alchemy/rules.json';target.parent.mkdir(parents=True)
            target.write_text(json.dumps(rules))
            neutral = formulas.icon('zhuji_high',root)
            ordinary = formulas.icon('huiling',root)
            # Upper roller bindings use the ordinary cloth colors despite the '_high' ID.
            self.assertEqual(neutral.crop((8,4,14,12)).tobytes(),ordinary.crop((8,4,14,12)).tobytes())
            self.assertNotEqual(neutral.tobytes(),formulas.icon('zhuji_high').tobytes())


if __name__ == '__main__':
    unittest.main()
