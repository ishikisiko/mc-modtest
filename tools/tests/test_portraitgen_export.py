"""The exported part maps plus the shared rules (replay.py) reproduce render.render exactly, for
the base people and a spread of random ledger-like people. This is what the Java compositor ports."""
import json
import os
import random
import unittest

try:
    from PIL import Image  # noqa: F401
    HAVE_PIL = True
except ImportError:  # pragma: no cover
    HAVE_PIL = False

MAPS = "src/main/resources/assets/myvillage/portrait"
GOLDENS = "src/test/resources/portrait_goldens"


@unittest.skipUnless(HAVE_PIL and os.path.exists(os.path.join(MAPS, "manifest.json")), "exported maps")
class ExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from tools.portraitgen.replay import Maps
        cls.maps = Maps(MAPS)

    def check(self, p, ch):
        from tools.portraitgen.render import render
        from tools.portraitgen.replay import render_replay
        a = render(p, ch).load()
        b = render_replay(self.maps, p, ch).load()
        diff = [(x, y, a[x, y], b[x, y]) for y in range(64) for x in range(64) if a[x, y] != b[x, y]]
        self.assertEqual(diff[:6], [], f"{len(diff)} cells differ for {p.gender} {ch.summary()}")

    def test_base_people(self):
        from tools.portraitgen.preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
        self.check(BASE_F, CHOICE_F)
        self.check(BASE_M, CHOICE_M)

    def test_every_layer_variant_on_both_bases(self):
        from dataclasses import replace
        from tools.portraitgen.preview_layer import rows_for
        for title, items in rows_for("all"):
            for p, ch, label in items:
                self.check(p, ch)

    def test_random_people(self):
        from tools.portraitgen.person import Person, REALMS, RANKS, assign
        rnd = random.Random(7)
        for i in range(80):
            realm = rnd.choice(REALMS[1:])
            rank = rnd.choice(RANKS)
            p = Person(rnd.randrange(1, 9999), "r", rnd.choice("mf"), realm, rank, -1 if rank == "rogue" else rnd.randrange(1, 9),
                       rnd.uniform(16, 900), [rnd.randrange(300, 4000) for _ in range(5)],
                       [rnd.randrange(10, 95) for _ in range(5)], rnd.choice([0, 0, 25, 60]), rnd.random() > 0.1)
            self.check(p, assign(p))

    def test_manifest_lists_every_file_and_goldens_match(self):
        m = self.maps.manifest
        for layer, names in m["layers"].items():
            for n in names:
                self.assertTrue(os.path.exists(os.path.join(MAPS, layer, n + ".png")), (layer, n))
        self.assertEqual(set(m["clears_face"]), {"short", "ponytail", "twin_buns", "bun"})
        idx = json.load(open(os.path.join(GOLDENS, "portrait_goldens.json"), encoding="utf-8"))
        self.assertGreaterEqual(len(idx["cases"]), 8)
        from PIL import Image
        sample = Image.open("tools/portraitgen/old/sample.png").convert("RGBA").load()
        g0 = Image.open(os.path.join(GOLDENS, "0.png")).convert("RGBA").load()
        self.assertEqual([(x, y) for y in range(64) for x in range(64) if (sample[x, y][3] == 0) != (g0[x, y][3] == 0)], [])


if __name__ == "__main__":
    unittest.main()
