"""tools/portraitgen/parts_body: robes cover the shoulders and leave the neck, court_f keeps the sample's
coverage, every headwear and mark paints something and stays off the eyes."""
import unittest
from dataclasses import replace

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False


def _bases():
    from tools.portraitgen.preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
    return (BASE_F, CHOICE_F, "_f"), (BASE_M, CHOICE_M, "_m")


EYES = [(x, y) for x in list(range(19, 28)) + list(range(36, 45)) for y in range(29, 38)]


@unittest.skipUnless(HAVE_PIL, "PIL")
class BodyTest(unittest.TestCase):
    def test_every_robe_covers_the_bottom_row_and_leaves_the_neck(self):
        from tools.portraitgen.parts_body import ROBES
        from tools.portraitgen.render import compose
        for base, choice, side in _bases():
            for tier in ("plain", "dyed", "brocade", "court"):
                robe = tier + side
                self.assertIn(robe, ROBES)
                g = compose(base, replace(choice, robe=robe, headwear="none", marks=[]))
                self.assertTrue(all(g.get(x, 63) != "." for x in range(4, 60)), robe)
                self.assertIn(g.get(31, 50), "SsTKL", robe)
                self.assertIn(g.get(31, 63), "OcCQgGuNZzVX", robe)

    def test_robe_tiers_differ(self):
        from tools.portraitgen.render import compose
        for base, choice, side in _bases():
            grids = [compose(base, replace(choice, robe=t + side, headwear="none")).cells
                     for t in ("plain", "dyed", "brocade", "court")]
            for i in range(4):
                for j in range(i + 1, 4):
                    self.assertNotEqual(grids[i], grids[j], (side, i, j))

    def test_court_f_keeps_the_sample_coverage(self):
        from tools.portraitgen import parts_body
        from tools.portraitgen.preview_layer import BASE_F, CHOICE_F
        from tools.portraitgen.render import compose

        def opaque(g):
            return {(x, y) for y in range(64) for x in range(64) if g.get(x, y) != "."}
        court = compose(BASE_F, CHOICE_F)
        saved = parts_body.ROBES["court_f"]
        parts_body.ROBES["court_f"] = parts_body.coat_crossed
        try:
            sample = compose(BASE_F, CHOICE_F)
        finally:
            parts_body.ROBES["court_f"] = saved
        self.assertEqual(opaque(court), opaque(sample))

    def test_headwear_and_marks_paint_and_keep_off_the_eyes(self):
        from tools.portraitgen.parts_body import HEADWEAR
        from tools.portraitgen.render import compose
        for base, choice, side in _bases():
            bare = compose(base, replace(choice, headwear="none", marks=[])).cells
            for hw in HEADWEAR:
                g = compose(base, replace(choice, headwear=hw, marks=[]))
                if hw != "none":
                    self.assertNotEqual(g.cells, bare, (side, hw))
                for x, y in EYES:
                    self.assertEqual(g.cells[y][x], bare[y][x], (side, hw, x, y))
            for marks, role in ((["bandage"], "F"), (["scar"], "Y")):
                g = compose(base, replace(choice, headwear="none", marks=marks))
                self.assertTrue(any(role in row for row in g.cells), (side, marks))
                for x, y in EYES:
                    self.assertEqual(g.cells[y][x], bare[y][x], (side, marks, x, y))


if __name__ == "__main__":
    unittest.main()
