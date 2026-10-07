"""tools/portraitgen: deterministic part choice, the rules the preview page states, and sane renders."""
import unittest
from dataclasses import replace

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False

from tools.portraitgen.person import Person, assign, mix


def person(**kw):
    base = dict(id=7, name="x", gender="m", realm="qi_refining", rank="outer", sect_id=1, age_years=20.0)
    base.update(kw)
    return Person(**base)


class AssignTest(unittest.TestCase):
    def test_mix_is_stable(self):
        self.assertEqual(mix(1, 1), mix(1, 1))
        self.assertNotEqual(mix(1, 1), mix(2, 1))
        self.assertNotEqual(mix(1, 1), mix(1, 2))
        self.assertEqual(mix(12345, 6), 0x4ef140464405e670)      # the value a Java port must reproduce

    def test_same_record_same_choice(self):
        a, b = assign(person()), assign(person())
        self.assertEqual(a, b)

    def test_rank_drives_headwear_and_men_of_standing_wear_a_bun(self):
        self.assertEqual(assign(person(rank="sect_master")).headwear, "crown")
        self.assertEqual(assign(person(rank="sect_master")).back, "bun")
        self.assertEqual(assign(person(rank="elder")).headwear, "headband")
        self.assertEqual(assign(person(rank="elder")).back, "bun")
        self.assertEqual(assign(person(rank="inner")).headwear, "ribbon")
        self.assertEqual(assign(person(rank="outer")).headwear, "none")
        self.assertEqual(assign(person(gender="f", rank="sect_master")).headwear, "phoenix_pin")
        self.assertEqual(assign(person(gender="f", rank="elder")).headwear, "jade_pin")

    def test_realm_drives_the_robe(self):
        for realm, robe in (("qi_refining", "plain"), ("foundation_establishment", "dyed"), ("golden_core", "brocade"),
                            ("nascent_soul", "court")):
            self.assertEqual(assign(person(realm=realm)).robe, robe + "_m")
            self.assertEqual(assign(person(realm=realm, gender="f")).robe, robe + "_f")

    def test_root_drives_the_eye_colour(self):
        for i, element in enumerate(("metal", "wood", "water", "fire", "earth")):
            root = [1000] * 5
            root[i] = 6000
            self.assertEqual(assign(person(root=root)).eye_colour, element)
        self.assertEqual(assign(person(realm="nascent_soul")).eye_colour, "spirit")

    def test_age_greys_then_whitens_the_hair(self):
        young = assign(person(age_years=30))
        self.assertNotIn("grey", young.hair_colour)
        self.assertFalse(young.old)
        greying = assign(person(age_years=80))          # 80 / 120 = .67
        self.assertTrue(greying.hair_colour.startswith("grey_"))
        old = assign(person(age_years=110))             # .92
        self.assertEqual(old.hair_colour, "silver")
        self.assertTrue(old.old)
        self.assertFalse(assign(person(realm="golden_core", age_years=200)).old)   # 200 / 500

    def test_mood_drives_brow_and_mouth(self):
        self.assertEqual((assign(person(traits=[30, 80, 30, 30, 30])).brow, assign(person(traits=[30, 80, 30, 30, 30])).mouth),
                         ("angry", "frown"))
        self.assertEqual(assign(person(traits=[30, 80, 30, 30, 30])).eye_shape, "sharp")
        self.assertEqual(assign(person(traits=[80, 30, 30, 30, 30])).mouth, "smirk")
        self.assertEqual(assign(person(traits=[30, 30, 80, 30, 30])).brow, "worried")
        self.assertEqual(assign(person(traits=[30, 30, 30, 80, 30])).mouth, "open")
        self.assertEqual(person(traits=[50, 50, 50, 50, 50]).mood, "calm")

    def test_injury_and_death_marks(self):
        self.assertEqual(assign(person(injury=0)).marks, [])
        self.assertEqual(assign(person(injury=25)).marks, ["bandage"])
        self.assertEqual(assign(person(injury=60)).marks, ["bandage", "scar"])
        self.assertTrue(assign(person(alive=False)).dead)

    def test_sect_colours_the_accent_and_rogues_are_grey(self):
        self.assertEqual(assign(person(sect_id=3)).accent, 3)
        self.assertEqual(assign(person(sect_id=-1, rank="rogue")).accent, 6)


@unittest.skipUnless(HAVE_PIL, "PIL")
class RenderTest(unittest.TestCase):
    SAMPLE = "tools/portraitgen/old/sample.png"

    def test_base_woman_matches_the_approved_sample(self):
        """The default parts are the hand-drawn sample the owner approved, cut into modules."""
        import os
        from PIL import Image
        from tools.portraitgen.preview_layer import BASE_F, CHOICE_F
        from tools.portraitgen.render import render
        if not os.path.exists(self.SAMPLE):
            self.skipTest("sample png not present")
        a = render(BASE_F, CHOICE_F).load()
        b = Image.open(self.SAMPLE).convert("RGBA").load()
        diff = [(x, y) for y in range(64) for x in range(64)
                if (a[x, y][3] == 0) != (b[x, y][3] == 0)]
        self.assertEqual(diff[:10], [], f"{len(diff)} cells differ in coverage")

    def test_every_variant_renders(self):
        from tools.portraitgen.preview_layer import rows_for
        from tools.portraitgen.render import render
        for title, items in rows_for("all"):
            for p, ch, label in items:
                im = render(p, ch)
                self.assertEqual(im.size, (64, 64), (title, label))
                px = im.load()
                self.assertEqual(px[0, 0][3], 0, (title, label))
                self.assertEqual(px[31, 33][3], 255, (title, label))     # the face is always there
                colours = {px[x, y] for x in range(64) for y in range(64) if px[x, y][3]}
                self.assertLessEqual(len(colours), 48, (title, label, len(colours)))

    def test_render_is_deterministic_and_differs_between_people(self):
        from tools.portraitgen.render import render
        a = render(person()).tobytes()
        self.assertEqual(a, render(person()).tobytes())
        self.assertNotEqual(a, render(person(id=8)).tobytes())
        self.assertNotEqual(a, render(person(gender="f")).tobytes())

    def test_dead_render_is_grey(self):
        from tools.portraitgen.render import render
        px = render(person(alive=False)).load()
        for x, y in ((31, 33), (31, 62), (30, 8)):
            r, g, b, a = px[x, y]
            self.assertLess(max(r, g, b) - min(r, g, b), 12)

    def test_hair_never_covers_the_eyes(self):
        from tools.portraitgen.preview_layer import rows_for
        from tools.portraitgen.render import compose
        for title, items in rows_for("all"):
            if "bangs" not in title and "back" not in title:
                continue
            for p, ch, label in items:
                g = compose(p, ch)
                for x in list(range(21, 26)) + list(range(38, 43)):
                    for y in range(31, 37):
                        self.assertNotIn(g.get(x, y), "HhDIJ", (title, label, x, y))


if __name__ == "__main__":
    unittest.main()
