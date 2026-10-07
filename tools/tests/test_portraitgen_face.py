"""tools/portraitgen/parts_face: every face shape keeps the geometry contract the other parts rely on."""
import unittest
from dataclasses import replace

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False

SAMPLE_FACE = {14: (24, 39), 15: (22, 41), 16: (21, 42), 17: (20, 43), 18: (19, 44), 19: (18, 45), 20: (18, 45),
               21: (17, 46), 22: (17, 46), 23: (17, 46), 39: (17, 46), 40: (17, 46), 41: (18, 45), 42: (19, 44),
               43: (20, 43), 44: (21, 42), 45: (23, 40), 46: (24, 39), 47: (26, 37), 48: (28, 35), 49: (29, 34),
               50: (31, 32)}
SAMPLE_FACE.update({y: (16, 47) for y in range(24, 39)})


@unittest.skipUnless(HAVE_PIL, "PIL")
class FaceShapeTest(unittest.TestCase):
    def shapes(self):
        from tools.portraitgen.parts_face import SHAPES, shape_rows
        for name in SHAPES:
            for male in (False, True):
                yield name, male, shape_rows(name, male)

    def test_the_default_woman_is_the_sample(self):
        from tools.portraitgen.parts_face import shape_rows
        self.assertEqual(shape_rows("oval", False), SAMPLE_FACE)

    def test_chin_tip_at_50_centred_and_rows_symmetric(self):
        for name, male, rows in self.shapes():
            self.assertEqual(max(rows), 50, (name, male))
            self.assertEqual(min(rows), 14, (name, male))
            for y, (x0, x1) in rows.items():
                self.assertEqual(x0 + x1, 63, (name, male, y))
                self.assertGreaterEqual(x0, 16, (name, male, y))

    def test_jaw_only_narrows_and_men_are_not_narrower(self):
        from tools.portraitgen.parts_face import shape_rows
        for name, male, rows in self.shapes():
            for y in range(39, 50):
                self.assertGreaterEqual(rows[y][1] - rows[y][0], rows[y + 1][1] - rows[y + 1][0], (name, male, y))
            if not male:
                m = shape_rows(name, True)
                self.assertGreaterEqual(m[50][1] - m[50][0], 3, name)                       # a flat chin
                for y in range(39, 51):
                    self.assertGreaterEqual(m[y][1] - m[y][0], rows[y][1] - rows[y][0], (name, y))
                width = lambda r: sum(r[y][1] - r[y][0] for y in range(44, 51))
                self.assertGreater(width(m), width(rows), name)                               # wider at the jaw

    def test_shapes_differ(self):
        from tools.portraitgen.parts_face import SHAPES, SHAPES_M
        for table in (SHAPES, SHAPES_M):
            jaws = {tuple(rows[y] for y in range(39, 51)) for rows in table.values()}
            self.assertEqual(len(jaws), len(table))

    def test_eye_box_nose_and_mouth_rows_are_skin(self):
        from tools.portraitgen.parts_face import MOUTHS, draw, draw_mouth, draw_nose
        from tools.portraitgen.pix import Grid
        from tools.portraitgen.preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
        for p, base in ((BASE_F, CHOICE_F), (BASE_M, CHOICE_M)):
            for face in ("oval", "round", "sharp"):
                for old in (False, True):
                    ch = replace(base, face=face, old=old)
                    g = Grid()
                    draw(g, ch, p)
                    for x in range(17, 47):
                        for y in range(28, 39):
                            self.assertIn(g.get(x, y), "Ss", (face, old, x, y))
                    for mouth in MOUTHS:
                        g2 = Grid()
                        draw(g2, ch, p)
                        draw_nose(g2, ch, p)
                        draw_mouth(g2, replace(ch, mouth=mouth), p)
                        marks = {(x, y) for y in range(64) for x in range(64) if g2.get(x, y) in "Mm"}
                        self.assertTrue(marks, (face, mouth))
                        self.assertTrue(all(44 <= y <= 46 and 28 <= x <= 35 for x, y in marks), (face, mouth, marks))


if __name__ == "__main__":
    unittest.main()
