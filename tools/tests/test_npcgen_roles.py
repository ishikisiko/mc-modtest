"""tools/npcgen role maps and the per-person recolour (roles.py, recolour.py, the goldens).

Standard library only:  /usr/bin/python3 -m unittest tools.tests.test_npcgen_roles
"""
from __future__ import annotations

import re
import unittest

from tools import gen_qingfeng_sword_model as pngio
from tools.beastgen.build import REPO, same_file
from tools.npcgen import build, recolour, roles
from tools.npcgen.humanoid import _rgb
from tools.portraitgen import roles as portrait_roles

JAVA = REPO / "src/main/java/com/example/myvillage/portrait"

# Cubes painted in hair, per look: every opaque texel of them is a hair texel. The skull (the cranium)
# is hair only on its top, back and the underside behind the jaw, so it is checked on its own.
HAIR_CUBES = {
    "cultivator": ("hair", "hair_cap", "bun", "strand_right_lock", "hair_back_main", "hair_back_tip"),
    "cultivator_f_novice": ("hair", "hair_cap", "hair_back_root", "hair_back_main", "hair_back_tip", "hair_back_end"),
    "cultivator_f_adept": ("hair", "hair_cap", "bun", "bun_top", "loop_right", "hair_back_main", "hair_back_tip"),
}
# Iris texels from each def's face map: rows r 7..8..9 at columns a = 2, 3 on both sides of the centre
# column -> 4 texels per row (index 0, 1, 2). The default look also mixes IRIS_LOW into the white
# column's bottom texel (a = 4, r = 9) on each side -> 2 texels of index 3; the female looks paint a
# blush there instead.
IRIS_COUNTS = {
    "cultivator": {0: 4, 1: 4, 2: 4, 3: 2},
    "cultivator_f_novice": {0: 4, 1: 4, 2: 4},
    "cultivator_f_adept": {0: 4, 1: 4, 2: 4},
}


class RoleMapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.built = {name: build.Built(name) for name in build.DEFINITIONS}

    def role(self, b, t):
        return b.roles[t.v][t.u]

    def test_role_map_on_disk_is_fresh(self):
        for name, b in self.built.items():
            with self.subTest(name):
                path = build.paths(name)["roles"]
                self.assertTrue(same_file(path, pngio.encode_png(b.roles)), f"run: python3 -m tools.npcgen build {name}")

    def test_role_map_has_the_texture_size_and_only_known_roles(self):
        for name, b in self.built.items():
            with self.subTest(name):
                w, h = b.model.texture_size
                self.assertEqual((h, w), (len(b.roles), len(b.roles[0])))
                for y, row in enumerate(b.roles):
                    for x, (r, g, bl, a) in enumerate(row):
                        if a == 0:
                            self.assertEqual((0, 0, 0, 0), (r, g, bl, a))
                            continue
                        self.assertEqual(255, a)
                        self.assertEqual(0, bl)
                        self.assertEqual(255, b.texture[y][x][3], "a role texel over a hole")
                        if r == roles.MATERIAL_HAIR:
                            self.assertLess(g, roles.HAIR_TONES)
                        else:
                            self.assertEqual(roles.MATERIAL_IRIS, r)
                            self.assertLessEqual(g, roles.IRIS_WHITE_MIX)

    def test_own_ramps_rebuild_the_baked_texture(self):
        """Recolouring with the def's own HAIR ramp and iris rows gives back the texture pixel for
        pixel: the role map holds every hair and iris texel at the right tone."""
        for name, b in self.built.items():
            with self.subTest(name):
                d = b.d
                eye = [_rgb(d.IRIS_TOP), _rgb(d.IRIS), _rgb(d.IRIS_LOW)]
                self.assertEqual(b.texture, recolour.compose_with(b.texture, b.roles, list(d.HAIR), eye))

    def test_recolour_equals_a_fresh_paint(self):
        """The recolour through the role map equals the painter run afresh with the portrait's ramps in
        place of the def's: no hair or iris texel is missing from the map, none is extra."""
        for name, hair, eye in (("cultivator", "ink_blue", "water"), ("cultivator_f_novice", "chestnut", "fire"),
                                ("cultivator_f_adept", "grey_ink_violet", "earth")):
            with self.subTest(name):
                b = self.built[name]
                ramp7 = recolour.hair_ramp7(recolour.hair_ramp5(hair))
                eyes = recolour.eye_ramp(eye)
                self.assertEqual(roles.repaint(b, ramp7, eyes[:3]), recolour.compose(b.texture, b.roles, hair, eye))

    def test_hair_cubes_are_hair(self):
        for name, b in self.built.items():
            with self.subTest(name):
                hair = HAIR_CUBES[name]
                seen = set()
                for t in b.texels:
                    r = self.role(b, t)
                    if t.cube in hair and b.texture[t.v][t.u][3]:
                        self.assertEqual(roles.MATERIAL_HAIR, r[0], f"{t.cube} {t.face} ({t.u}, {t.v})")
                        seen.add(t.cube)
                    elif r[3] and r[0] == roles.MATERIAL_HAIR:
                        self.assertEqual("skull", t.cube, f"hair texel on {t.cube}")
                self.assertEqual(set(hair), seen)
                skull = [t for t in b.texels if t.cube == "skull"]
                for face in ("DOWN", "SOUTH"):   # the scalp: the top, and the back under the hair shell
                    on = [t for t in skull if t.face == face]
                    self.assertTrue(on and all(self.role(b, t)[0] == roles.MATERIAL_HAIR for t in on), face)
                self.assertTrue(any(self.role(b, t)[0] == roles.MATERIAL_HAIR for t in skull if t.face == "UP"))
                self.assertTrue(any(self.role(b, t)[0] == roles.MATERIAL_HAIR for t in skull if t.face == "NORTH"))

    def test_iris_texels_match_the_face_map(self):
        for name, b in self.built.items():
            with self.subTest(name):
                iris = [t for t in b.texels if self.role(b, t)[3] and self.role(b, t)[0] == roles.MATERIAL_IRIS]
                self.assertTrue(all(t.cube == "skull" and t.face == "NORTH" for t in iris))
                found = {}
                for t in iris:
                    found[self.role(b, t)[1]] = found.get(self.role(b, t)[1], 0) + 1
                self.assertEqual(IRIS_COUNTS[name], found)

    def test_hair_uses_most_of_the_ramp(self):
        for name, b in self.built.items():
            with self.subTest(name):
                tones = {i for (m, i) in roles.counts(b.roles) if m == roles.MATERIAL_HAIR}
                self.assertGreaterEqual(len(tones), 9)

    def test_export_restores_the_constants(self):
        b = self.built["cultivator"]
        d = b.d
        before = (d.HAIR, d._Paint.HAIR, d.IRIS_TOP, d.IRIS, d.IRIS_LOW)
        roles.export(b)
        self.assertEqual(before, (d.HAIR, d._Paint.HAIR, d.IRIS_TOP, d.IRIS, d.IRIS_LOW))
        self.assertNotIn("HAIR", vars(d.painter(b.model)))

    def test_eye_white_is_the_composer_constant(self):
        java = (JAVA / "NpcSkinComposer.java").read_text(encoding="utf-8")
        self.assertIn("EYE_WHITE = 0xFF%02X%02X%02X;" % recolour.EYE_WHITE, java)
        for b in self.built.values():
            self.assertEqual(recolour.EYE_WHITE, _rgb(b.d.EYE_WHITE))


class RecolourTest(unittest.TestCase):
    def test_mix_rounds_half_to_even(self):
        self.assertEqual((2, 2, 2), recolour.mix((0, 1, 3), (5, 4, 0), 0.5))      # 2.5, 2.5, 1.5
        self.assertEqual((4, 4, 0), recolour.mix((7, 1, 0), (0, 8, 0), 0.5))      # 3.5, 4.5

    def test_hair_ramp7_by_hand(self):
        # ink_blue D h H I J = #121A3A #223060 #324A8A #5A74BC #A0B4E8 sampled at 0, 2/3, 4/3, 2, 8/3, 10/3, 4
        expected = ["#121A3A", "#1D2953", "#27396E", "#324A8A", "#4D66AB", "#7189CB", "#A0B4E8"]
        got = ["#%02X%02X%02X" % c for c in recolour.hair_ramp7(recolour.hair_ramp5("ink_blue"))]
        self.assertEqual(expected, got)

    def test_hair_tone_halves_and_ends(self):
        ramp7 = [(10 * i, 0, 0) for i in range(7)]
        self.assertEqual((0, 0, 0), recolour.hair_tone(ramp7, 0))
        self.assertEqual((5, 0, 0), recolour.hair_tone(ramp7, 1))
        self.assertEqual((60, 0, 0), recolour.hair_tone(ramp7, 12))
        with self.assertRaises(ValueError):
            recolour.hair_tone(ramp7, 13)

    def test_iris_white_mix(self):
        eye = recolour.eye_ramp("water")
        self.assertEqual(5, len(eye))
        self.assertEqual(recolour.mix((0x8C, 0xCC, 0xF4), (0xF8, 0xFA, 0xFF), 0.5), recolour.iris(eye, 3))
        self.assertEqual((0x1E, 0x3A, 0x8A), recolour.iris(eye, 0))

    def test_portrait_tables_are_reused(self):
        self.assertEqual([c[:3] for c in portrait_roles.HAIRS["grey_chestnut"]], recolour.hair_ramp5("grey_chestnut"))
        with self.assertRaises(ValueError):
            recolour.hair_ramp5("grey_silver")    # roles.py has it, the Java enum does not

    def test_colour_names_follow_the_java_enums(self):
        java = (JAVA / "PortraitSpec.java").read_text(encoding="utf-8")

        def enum(name):
            body = re.search(r"enum " + name + r"\s*\{([^;}]*)", java).group(1)
            return tuple(s.strip().lower() for s in body.split(",") if s.strip())

        self.assertEqual(enum("HairColour"), recolour.HAIR_COLOURS)
        self.assertEqual(enum("EyeColour"), recolour.EYE_COLOURS)

    def test_goldens_cover_every_colour(self):
        self.assertEqual(set(recolour.HAIR_COLOURS), {h for _, h, _ in recolour.GOLDEN_CASES})
        self.assertEqual(set(recolour.EYE_COLOURS), {e for _, _, e in recolour.GOLDEN_CASES})

    def test_recolour_changes_only_role_texels(self):
        b = build.Built("cultivator_f_novice")
        out = recolour.compose(b.texture, b.roles, "chestnut", "fire")
        changed = 0
        for y, row in enumerate(out):
            for x, c in enumerate(row):
                if b.roles[y][x][3] == 0:
                    self.assertEqual(b.texture[y][x], c)
                elif c != b.texture[y][x]:
                    changed += 1
        self.assertGreater(changed, 1000)

    def test_goldens_on_disk_are_fresh(self):
        files = recolour.golden_files()
        self.assertEqual(13, len(files))
        for path, data in files.items():
            with self.subTest(path):
                self.assertTrue(same_file(REPO / path, data), "run: python3 -m tools.npcgen goldens")
        self.assertLess(sum(len(d) for d in files.values()), 400_000)


if __name__ == "__main__":
    unittest.main()
