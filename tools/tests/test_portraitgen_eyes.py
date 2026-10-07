"""tools/portraitgen/parts_eyes.py: every eye shape and brow stays inside the contract box, and each eye
has exactly one 2x2 highlight block at the top-left of its iris in canvas terms."""
import unittest

from tools.portraitgen import parts_eyes
from tools.portraitgen.pix import Grid

EYE_ROLES = set("WwEe1234PXx")


def _eye_grid(shape: str, female: bool) -> Grid:
    g = Grid()
    fn = parts_eyes.EYE_SHAPES[shape]
    fn(g, parts_eyes.L_OUTER, False, female)
    fn(g, parts_eyes.R_OUTER, True, female)
    return g


def _cells(g: Grid, roles: str, x0: int, x1: int):
    return {(x, y) for y in range(64) for x in range(x0, x1 + 1) if g.get(x, y) in roles}


class EyeShapesTest(unittest.TestCase):
    def test_every_shape_stays_in_the_box(self):
        for shape in parts_eyes.EYE_SHAPES:
            for female in (True, False):
                g = _eye_grid(shape, female)
                for y in range(64):
                    for x in range(64):
                        r = g.get(x, y)
                        if r == ".":
                            continue
                        # the approved round woman's flick reaches x 17 / 46, under the side locks
                        lo, hi = (17, 46) if (shape == "round" and female) else (19, 44)
                        self.assertTrue(28 <= y <= 38 and lo <= x <= hi, (shape, female, x, y, r))

    def test_one_highlight_block_per_eye_at_the_top_left_of_the_iris(self):
        for shape in parts_eyes.EYE_SHAPES:
            for female in (True, False):
                g = _eye_grid(shape, female)
                for x0, x1 in ((19, 31), (32, 44)):
                    hl = _cells(g, "X", x0, x1)
                    self.assertEqual(len(hl), 4, (shape, female, x0))
                    xs = sorted({x for x, _ in hl})
                    ys = sorted({y for _, y in hl})
                    self.assertEqual((len(xs), len(ys)), (2, 2), (shape, female, x0))
                    self.assertEqual(xs[1] - xs[0], 1)
                    self.assertEqual(ys[1] - ys[0], 1)
                    iris = _cells(g, "1234PX", x0, x1)
                    ix = sorted(x for x, _ in iris)
                    mid = (ix[0] + ix[-1]) / 2
                    self.assertLess(xs[1], mid + 1, (shape, female, x0, "highlight not on the canvas-left of the iris"))
                    self.assertLessEqual(ys[0], min(y for _, y in iris) + 2, (shape, female, x0))
                    self.assertEqual(len(_cells(g, "x", x0, x1)), 1, (shape, female, x0))

    def test_men_have_a_one_row_lash_and_shorter_eyes(self):
        for shape in parts_eyes.EYE_SHAPES:
            f, m = _eye_grid(shape, True), _eye_grid(shape, False)

            def white_rows(g):
                return {y for _, y in _cells(g, "Ww1234PXx", 19, 27)}

            self.assertLess(len(white_rows(m)), len(white_rows(f)), shape)
            self.assertLess(len(_cells(m, "E", 19, 27)), len(_cells(f, "E", 17, 27)), shape)

    def test_eyes_are_mirrored_except_the_light(self):
        for shape in parts_eyes.EYE_SHAPES:
            for female in (True, False):
                g = _eye_grid(shape, female)
                for y in range(28, 39):
                    for x in range(19, 28):
                        a, b = g.get(x, y), g.get(63 - x, y)
                        if a in "WwEes" or b in "WwEes":
                            self.assertEqual(a in "Ees", b in "Ees", (shape, female, x, y, a, b))


class BrowsTest(unittest.TestCase):
    def test_brows_stay_in_rows_25_to_27_and_men_are_heavier(self):
        for table in (parts_eyes.BROWS, parts_eyes.BROWS_M):
            for name, pts in table.items():
                for x, y in pts:
                    self.assertTrue(25 <= y <= 27 and 19 <= x <= 28, (name, x, y))
        for name in parts_eyes.BROWS:
            self.assertGreater(len(parts_eyes.BROWS_M[name]), len(parts_eyes.BROWS[name]), name)

    def test_angry_and_worried_slope_opposite_ways(self):
        for table in (parts_eyes.BROWS, parts_eyes.BROWS_M):
            def inner_minus_outer(pts):
                outer = min(pts)[0]
                inner = max(pts)[0]
                yo = sum(y for x, y in pts if x == outer) / sum(1 for x, _ in pts if x == outer)
                yi = sum(y for x, y in pts if x == inner) / sum(1 for x, _ in pts if x == inner)
                return yi - yo
            self.assertGreater(inner_minus_outer(table["angry"]), 0)     # inner end lower (larger y)
            self.assertLess(inner_minus_outer(table["worried"]), 0)


if __name__ == "__main__":
    unittest.main()
