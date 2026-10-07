"""tools/portraitgen/parts_hair_front: every bang style keeps the eyes free, hangs from the dome, and
hime keeps the approved sample's coverage."""
import unittest
from dataclasses import replace

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False


def _bangs(base, choice, front):
    from tools.portraitgen.render import compose
    from tools.portraitgen import parts_hair_front
    seen = {}
    real = parts_hair_front.draw

    def spy(g, ch, ctx):
        seen["mask"] = real(g, ch, ctx)
        return seen["mask"]

    parts_hair_front.draw = spy
    try:
        g = compose(base, replace(choice, front=front))
    finally:
        parts_hair_front.draw = real
    return g, seen["mask"]


@unittest.skipUnless(HAVE_PIL, "PIL")
class HairFrontTest(unittest.TestCase):
    def people(self):
        from tools.portraitgen.preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
        return ((BASE_F, CHOICE_F), (BASE_M, CHOICE_M))

    def test_eye_box_stays_free(self):
        from tools.portraitgen.parts_hair_front import FRONTS
        for base, choice in self.people():
            for front in FRONTS:
                g, mask = _bangs(base, choice, front)
                for x in list(range(21, 26)) + list(range(38, 43)):
                    for y in range(31, 37):
                        self.assertNotIn((x, y), mask, (front, x, y))
                        self.assertNotIn(g.get(x, y), "HhDIJ", (front, x, y))

    def test_every_style_hangs_from_the_dome(self):
        from tools.portraitgen.parts_hair_front import FRONTS
        for base, choice in self.people():
            for front in FRONTS:
                _, mask = _bangs(base, choice, front)
                for y in range(10, 14):
                    row = {x for x, yy in mask if yy == y}
                    self.assertTrue(set(range(21, 43)) <= row, (front, y))
                self.assertGreaterEqual(max(y for _, y in mask), 23, front)

    def test_styles_differ(self):
        from tools.portraitgen.parts_hair_front import FRONTS
        base, choice = self.people()[0]
        masks = {f: frozenset(_bangs(base, choice, f)[1]) for f in FRONTS}
        self.assertEqual(len(set(masks.values())), len(FRONTS))

    def test_hime_keeps_the_sample_coverage(self):
        from tools.portraitgen.parts_hair_front import HIME, _strand_rows
        base, choice = self.people()[0]
        _, mask = _bangs(base, choice, "hime")
        sample = {(x, y) for x0, x1, tip in HIME for y, (a, b) in _strand_rows(x0, x1, tip).items()
                  for x in range(a, b + 1)}
        self.assertEqual(mask, sample)
        self.assertEqual(len(sample), 434)

    def test_curtain_opens_the_forehead_and_reaches_past_the_eyes(self):
        base, choice = self.people()[0]
        _, mask = _bangs(base, choice, "curtain")
        for y in range(18, 26):
            self.assertFalse({(x, y) for x in range(28, 36)} & mask, y)
        # the long locks hang over the side locks down beside the eyes (those cells are painted but
        # left out of the mask so the finish casts no forehead shadow onto the side locks)
        from tools.portraitgen.parts_hair_front import _CURTAIN_LONG
        self.assertGreaterEqual(max(_CURTAIN_LONG), 35)
        self.assertLessEqual(max(b for y, (a, b) in _CURTAIN_LONG.items() if y >= 28), 19)


if __name__ == "__main__":
    unittest.main()
