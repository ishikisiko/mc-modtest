"""tools/portraitgen/parts_hair_back.py: every back style keeps the contract the other parts rely on."""
import unittest
from dataclasses import replace

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False


@unittest.skipUnless(HAVE_PIL, "PIL")
class HairBackTest(unittest.TestCase):
    def people(self):
        from tools.portraitgen.preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
        from tools.portraitgen.parts_hair_back import BACKS
        for back in BACKS:
            for p, ch in ((BASE_F, CHOICE_F), (BASE_M, CHOICE_M)):
                yield back, p, replace(ch, back=back, headwear="none")

    def test_dome_is_solid_where_the_bangs_hang(self):
        from tools.portraitgen import parts_hair_back
        from tools.portraitgen.pix import Grid
        for back, p, ch in self.people():
            mask = parts_hair_back.behind(Grid(), ch, {"male": not p.female})
            missing = [(x, y) for y in range(10, 14) for x in range(19, 45) if (x, y) not in mask]
            self.assertEqual(missing, [], back)

    def test_locks_leave_the_eyes_free(self):
        from tools.portraitgen import parts_face, parts_hair_back
        from tools.portraitgen.pix import Grid
        eyes = {(x, y) for y in range(28, 39) for x in list(range(19, 28)) + list(range(36, 45))}
        for back, p, ch in self.people():
            g = Grid()
            ctx = {"male": not p.female}
            ctx["hair_back"] = parts_hair_back.behind(g, ch, ctx)
            ctx["face"] = parts_face.draw(g, ch, p)
            locks = parts_hair_back.locks(g, ch, ctx)
            self.assertEqual(sorted(locks & eyes), [], back)

    def test_no_hair_outline_across_the_jaw(self):
        from tools.portraitgen.render import compose
        for back, p, ch in self.people():
            g = compose(p, ch)
            for y in range(39, 46):
                for x in range(27, 37):
                    self.assertNotEqual(g.get(x, y), "D", (back, x, y))

    def test_long_is_half_up_without_the_knot(self):
        from tools.portraitgen.render import compose
        from tools.portraitgen.preview_layer import BASE_F, CHOICE_F
        a = compose(BASE_F, replace(CHOICE_F, back="long", headwear="none"))
        b = compose(BASE_F, replace(CHOICE_F, back="half_up", headwear="none"))
        diff = [(x, y) for y in range(64) for x in range(64) if (a.get(x, y) == ".") != (b.get(x, y) == ".")]
        self.assertTrue(diff and all(y <= 1 for _, y in diff), diff[:10])

    def test_styles_show_what_makes_them(self):
        from tools.portraitgen.render import compose
        from tools.portraitgen.preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
        f = lambda back: compose(BASE_F, replace(CHOICE_F, back=back, headwear="none"))
        m = lambda back: compose(BASE_M, replace(CHOICE_M, back=back, headwear="none"))
        for back in ("short", "bun"):                     # ears in skin roles on both sides
            for g in (f(back), m(back)):
                self.assertIn(g.get(14, 30), "SsTK", back)
                self.assertIn(g.get(49, 30), "SsTK", back)
        tail = f("ponytail")                              # the tail beside the head, past the shoulder, a tie
        self.assertIn(tail.get(58, 30), "HhIJD")
        self.assertEqual(tail.get(5, 30), ".")
        self.assertTrue(any(tail.get(x, y) in "aA" for x in range(40, 56) for y in range(0, 8)))
        buns = f("twin_buns")                             # two buns above the dome's shoulders, ribbons
        self.assertIn(buns.get(18, 1), "HhIJD")
        self.assertIn(buns.get(45, 1), "HhIJD")
        self.assertTrue(any(buns.get(x, y) in "aA" for x in range(8, 17) for y in range(5, 14)))
        knot = m("bun")                                   # a knot in the knot area, nothing hanging below the jaw
        self.assertIn(knot.get(31, 0), "HhIJD")
        self.assertEqual(knot.get(15, 48), ".")
        self.assertNotIn(f("short").get(12, 52), "HhIJD")  # short ends at the jaw


if __name__ == "__main__":
    unittest.main()
