"""tools/npcgen, female novice cultivator look (`f_novice`): the shared art rules of test_npcgen (schema,
atlas, cut-outs, face, planted-foot walk, determinism) plus what makes the look female and a novice.

Standard library only:  /usr/bin/python3 -m unittest tools.tests.test_npcgen_f_novice
"""
from __future__ import annotations

import contextlib
import io
import json
import math
import unittest

from tools.beastgen import anim, cuboid
from tools.npcgen import build, shade
from tools.npcgen.defs import cultivator as male
from tools.npcgen.defs import cultivator_f_novice as novice

NAME = "cultivator_f_novice"
GROUND = 24.0
# The only cubes whose texture is cut out are the declared shells.
CUT_OUT = set(novice.HOLLOW)
BODY_BONES = {"root", "body", "head", "arm_right", "arm_left", "forearm_right", "forearm_left",
              "leg_right", "leg_left", "hair_back"}


def corners(cube):
    return [tuple(cube.origin[k] + cube.size[k] * ((i >> k) & 1) for k in range(3)) for i in range(8)]


def luma(rgb):
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def shoulder_width(model):
    """Across the shoulders: twice the right arm pivot's distance from the centre plus the furthest
    reach of the arm's shoulder cubes (on the arm bone and the bones hung from it, not the forearm),
    measured in the arm's own frame so a posed arm does not change it."""
    arm = model.bone("arm_right")
    pivot_x = model.rest_matrices()["body"][0][3] + arm.pivot[0]
    reach = max(-c.origin[0] for c in arm.cubes) if arm.cubes else 0.0
    for b in model.bones:
        if b.parent == "arm_right" and not b.name.startswith("forearm"):
            m = b.rest_matrix()
            for c in b.cubes:
                reach = max(reach, max(-cuboid.mat_apply(m, p)[0] for p in corners(c)))
    return 2.0 * (abs(pivot_x) + reach)


class FemaleNoviceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.built = build.Built(NAME)
        cls.outputs = cls.built.outputs()
        p = build.paths(NAME)
        cls.model_doc = json.loads(cls.outputs[p["model"]])
        cls.anim_doc = json.loads(cls.outputs[p["animations"]])
        cls.model = cuboid.model_from_json(cls.model_doc)
        cls.clips = anim.clips_from_json(cls.anim_doc)
        cls.texture = cls.built.texture
        cls.by_cube = {}
        for t in cls.built.texels:
            cls.by_cube.setdefault(t.cube, []).append(t)
        cls.male = build.Built("cultivator")

    def offsets(self, clip, seconds):
        length, loop, chans = self.clips[clip]
        return anim.clip_offsets(chans, length, loop, seconds)

    def box(self, name, built=None):
        """Rest-pose design-space bounds of a cube: (x0, x1), (bottom, top), (z0, z1)."""
        built = built or self.built
        bone, cube = built.model.cube(name)
        m = built.model.rest_matrices()[bone.name]
        pts = [cuboid.mat_apply(m, c) for c in corners(cube)]
        xs, ys, zs = ([p[k] for p in pts] for k in range(3))
        return (min(xs), max(xs)), (GROUND - max(ys), GROUND - min(ys)), (min(zs), max(zs))

    def width(self, name, built=None):
        (x0, x1), _, _ = self.box(name, built)
        return x1 - x0

    def pixel(self, t):
        return self.texture[t.v][t.u]

    # -------------------------------------------------------------- schema
    def test_model_matches_schema(self):
        d = self.model_doc
        self.assertEqual(1, d["schema"])
        self.assertEqual("myvillage:cultivator", d["id"])
        self.assertEqual(0.5, d["scale"])
        self.assertEqual({"bone": "head", "max_yaw": 60.0, "max_pitch": 35.0}, d["look"])
        names = [b["name"] for b in d["bones"]]
        self.assertEqual(len(names), len(set(names)))
        for i, b in enumerate(d["bones"]):
            self.assertTrue(b["parent"] is None or b["parent"] in names[:i], b["name"])

    def test_same_entity_as_the_male_look(self):
        self.assertEqual(male.ID, novice.ID)
        self.assertEqual(male.ENTITY, novice.ENTITY)
        self.assertEqual("f_novice", novice.LOOK)
        self.assertEqual(male.HITBOX, novice.HITBOX)
        self.assertEqual(male.SCALE, novice.SCALE)
        self.assertEqual(self.male.model_doc["look"], self.model_doc["look"])
        self.assertEqual(self.male.model.shadow_radius, self.built.model.shadow_radius)
        mine = {b["name"] for b in self.model_doc["bones"]}
        theirs = {b.name for b in self.male.model.bones}
        self.assertLessEqual(BODY_BONES, theirs)
        self.assertLessEqual(BODY_BONES, mine)

    def test_clips_loop_and_name_model_bones(self):
        bones = {b["name"] for b in self.model_doc["bones"]}
        self.assertEqual({"idle", "walk"}, set(self.anim_doc["clips"]))
        for name, clip in self.anim_doc["clips"].items():
            self.assertTrue(clip["loop"], name)
            for channel in clip["channels"]:
                self.assertIn(channel["bone"], bones)
                first, last = channel["keyframes"][0], channel["keyframes"][-1]
                self.assertEqual(0.0, first["time"])
                self.assertAlmostEqual(clip["length"], last["time"], places=6)
                self.assertEqual(first["value"], last["value"], f"{name}/{channel['bone']} does not close its loop")

    # -------------------------------------------------------------- atlas
    def test_uv_islands_inside_atlas_and_disjoint(self):
        tw, th = self.model.texture_size
        seen = {}
        for bone, cube in self.built.model.cubes():
            if cube.uv_from:
                continue
            for u, v, w, h in cuboid.uv_regions(cube).values():
                self.assertTrue(0 <= u and u + w <= tw and 0 <= v and v + h <= th, cube.name)
                for x in range(u, u + w):
                    for y in range(v, v + h):
                        self.assertNotIn((x, y), seen, f"{cube.name} overlaps {seen.get((x, y))}")
                        seen[(x, y)] = cube.name

    def test_mirrored_twins_share_the_right_side(self):
        cubes = {c.name: c for _, c in self.built.model.cubes()}
        twins = [c for c in cubes.values() if c.uv_from]
        self.assertGreater(len(twins), 8)
        for twin in twins:
            source = cubes[twin.uv_from]
            self.assertTrue(twin.mirror and not source.mirror, twin.name)
            self.assertEqual(source.size, twin.size)
            self.assertEqual(twin.name.replace("left", "right"), source.name)

    def test_alpha_is_binary_and_only_shells_are_cut_out(self):
        for row in self.texture:
            for r, g, b, a in row:
                self.assertIn(a, (0, 255))
        for name, texels in self.by_cube.items():
            holes = sum(1 for t in texels if self.pixel(t)[3] == 0)
            if name in CUT_OUT:
                self.assertGreater(holes, 0, name)
                self.assertLess(holes, len(texels), name)
            else:
                self.assertEqual(0, holes, f"{name} has unpainted texels")

    def test_cloth_is_not_painted_flat(self):
        """The large cloth faces carry shading and weave: several tones each, not one fill."""
        # the narrow upper sleeve is 4 texels wide and a third under the short sleeve, so it gets 5
        for name, face, least in (("sleeve_upper_right", "WEST", 5), ("sleeve_lower_right", "WEST", 6),
                                  ("skirt_hem_right", "NORTH", 10), ("skirt_knee_right", "WEST", 7),
                                  ("skirt_hip_right", "NORTH", 7), ("jacket", "SOUTH", 8),
                                  ("torso", "NORTH", 6), ("hair", "SOUTH", 5), ("hair_back_main", "SOUTH", 5)):
            colours = {self.pixel(t)[:3] for t in self.by_cube[name] if t.face == face and self.pixel(t)[3]}
            self.assertGreaterEqual(len(colours), least, f"{name} {face}: {len(colours)} colours")
        self.assertGreaterEqual(len(set(self.face().values())), 8, "face")

    def test_skirt_has_vertical_pleats_and_a_pale_hem(self):
        """Across the front of the hem tier the cloth alternates light and dark in vertical bands (the
        pleats), the same at two heights; the bottom row is a paler edge."""
        front = [t for t in self.by_cube["skirt_hem_right"] if t.face == "NORTH"]

        def column_luma(h):
            row = {int(math.floor(t.p[0])): luma(self.pixel(t)) for t in front if int(GROUND - t.p[1]) == h}
            return [row[k] for k in sorted(row)]

        for h in (5, 8):
            cols = column_luma(h)
            mean = sum(cols) / len(cols)
            signs = [c > mean for c in cols]
            flips = sum(1 for a, b in zip(signs, signs[1:]) if a != b)
            self.assertGreaterEqual(flips, 2, f"no pleats at h {h}: {cols}")
        bottom = [luma(self.pixel(t)) for t in front if int(GROUND - t.p[1]) == int(novice.HEM)]
        above = [luma(self.pixel(t)) for t in front if int(GROUND - t.p[1]) == int(novice.HEM) + 2]
        self.assertGreater(sum(bottom) / len(bottom), sum(above) / len(above) + 10)

    def test_tier_seams_are_not_painted_as_edges(self):
        """Where a tier widens, its up-facing ledge is painted about as dark as the lit cloth beside it
        looks (up faces draw at full brightness, sides at about 60 %), not as a pale or black line."""
        for name in ("skirt_knee_right", "skirt_hem_right"):
            ledge = [luma(self.pixel(t)) for t in self.by_cube[name] if t.face == "DOWN"]
            side = [luma(self.pixel(t)) for t in self.by_cube[name] if t.face == "NORTH"]
            self.assertAlmostEqual(0.6 * sum(side) / len(side), sum(ledge) / len(ledge), delta=18.0, msg=name)
        knee = {self.pixel(t)[:3] for t in self.by_cube["skirt_knee_right"] if t.face == "NORTH"}
        self.assertFalse(knee & {novice._tone(novice.EDGE, k / 2.0) for k in range(0, 8)},
                         "only the hem carries the pale edge")

    # -------------------------------------------------------------- layers
    def test_layers_stack_outward_on_the_chest(self):
        front = {name: self.box(name)[2][0] for name in
                 ("torso", "collar_under_band", "collar_over_band", "jacket", "jacket_edge_right_band")}
        order = ["torso", "collar_under_band", "collar_over_band", "jacket", "jacket_edge_right_band"]
        for inner, outer in zip(order, order[1:]):
            self.assertLess(front[outer], front[inner] - 0.05, f"{outer} must lie in front of {inner}")
        (x0, x1), _, (z0, z1) = self.box("jacket")
        (tx0, tx1), _, (tz0, tz1) = self.box("torso")
        self.assertTrue(x0 < tx0 and x1 > tx1 and z0 < tz0 and z1 > tz1)

    def test_jacket_opening_shows_the_crossed_collar(self):
        """The half jacket is open down the front (对襟) onto the ru's crossed collar; sides and back are
        whole, and it has no sloped shoulder caps."""
        def holes(face):
            return [t for t in self.by_cube["jacket"] if t.face == face and self.pixel(t)[3] == 0]

        opening = holes("NORTH")
        self.assertGreater(len(opening), 50)
        self.assertTrue(all(abs(t.p[0]) < novice.OPENING for t in opening))
        for face in ("WEST", "EAST", "SOUTH"):
            self.assertEqual([], holes(face))
        for band in ("collar_over_band", "collar_under_band"):
            (x0, x1), (bottom, top), _ = self.box(band)
            self.assertLess(max(abs(x0), abs(x1)), novice.OPENING + 0.5, band)
            self.assertGreater(bottom, novice.JACKET[0], band)
        self.assertFalse([c for _, c in self.built.model.cubes() if "cap" in c.name and "hair" not in c.name])

    def test_occluders_skip_hollow_shells(self):
        occ = shade.Occluders(self.built.model, novice.HOLLOW)
        names = {box[0] for box in occ.boxes}
        self.assertFalse(names & set(novice.HOLLOW))

    # -------------------------------------------------------------- female geometry
    def test_shoulders_two_texels_narrower_than_the_male(self):
        self.assertAlmostEqual(shoulder_width(self.male.model) - 2.0, shoulder_width(self.built.model), delta=0.3)

    def test_waist_drawn_in_at_the_band(self):
        band, jacket, torso = self.width("band"), self.width("jacket"), self.width("torso")
        self.assertGreaterEqual(jacket - band, 1.0)
        self.assertLessEqual(jacket - band, 2.0)
        self.assertLessEqual(band, torso)
        self.assertLess(self.width("skirt_top"), band)

    def test_skirt_flares_in_small_tucked_steps(self):
        """Hips flare past the waist, and each tier is one texel wider a side (and half a texel deeper a
        side) than the one above, its top tucked one to two texels up inside the tier above, so the skirt
        reads as one cloth flaring out, not stacked boxes: 14 / 16 / 18 across."""
        tiers = [self.box(f"skirt_{tier}_right") for tier in ("hip", "knee", "hem")]
        across = [2.0 * -x0 for (x0, _), _, _ in tiers]   # the right half's outer edge, mirrored
        self.assertEqual([14.0, 16.0, 18.0], across)
        self.assertGreater(across[0], self.width("skirt_top"))
        self.assertGreater(across[0], self.width("band"))
        self.assertGreaterEqual(across[-1] - self.width("band"), 6.0)
        for (_, (upper_bottom, _), (uz0, _)), (_, (_, lower_top), (lz0, _)) in zip(tiers, tiers[1:]):
            self.assertTrue(1.0 <= lower_top - upper_bottom <= 2.0, (upper_bottom, lower_top))
            self.assertAlmostEqual(0.5, uz0 - lz0)
        _, (bottom, _), _ = self.box("skirt_hem_right")
        _, (_, shoe_top), (shoe_z0, _) = self.box("shoe_right")
        self.assertLess(bottom, shoe_top, "the skirt reaches the ankle")
        self.assertLess(shoe_z0, self.box("skirt_hem_right")[2][0], "the shoe's toe peeks out")

    def test_head_lower_and_jaw_narrower_than_the_male(self):
        _, (_, top), _ = self.box("skull")
        self.assertAlmostEqual(58.0, top)
        self.assertAlmostEqual(60.0, self.box("skull", self.male)[1][1])
        widths = [self.width(n) for n in ("skull", "jaw", "jaw_low", "chin")]
        self.assertEqual([11.0, 9.0, 7.0, 5.0], widths)
        self.assertEqual([w + 2.0 for w in widths], [self.width(n, self.male) for n in ("skull", "jaw", "jaw_low", "chin")])
        skull = self.box("skull")
        self.assertLess(skull[0][1] - skull[0][0], skull[2][1] - skull[2][0], "the cranium is longer than wide")

    def test_hand_smaller_and_half_covered_by_the_sleeve(self):
        def volume(built, name):
            return math.prod(built.model.cube(name)[1].size)

        self.assertLess(volume(self.built, "hand_right"), volume(self.male, "hand_right") / 2.0)
        _, sleeve = self.built.model.cube("sleeve_lower_right")
        _, hand = self.built.model.cube("hand_right")
        sleeve_end = sleeve.origin[1] + sleeve.size[1]
        covered = sleeve_end - hand.origin[1]
        self.assertGreaterEqual(covered, hand.size[1] / 2.0 - 0.01)
        self.assertLess(covered, hand.size[1])

    def test_hands_folded_at_the_belly(self):
        """In the rest pose both hands sit in front of the belly near the centre line, above the skirt's
        band, the right hand in front of the left."""
        centres = {}
        for side in ("right", "left"):
            (x0, x1), (b, t), (z0, z1) = self.box(f"hand_{side}")
            centres[side] = ((x0 + x1) / 2.0, (b + t) / 2.0, (z0 + z1) / 2.0)
            self.assertLess(abs(centres[side][0]), 3.0, side)
            self.assertTrue(novice.BAND[1] <= centres[side][1] <= novice.CHEST[0] + 4.0, side)
            self.assertLess(centres[side][2], self.box("jacket")[2][0] - 1.5, side)
        self.assertLess(centres["right"][0], 0.0)
        self.assertGreater(centres["left"][0], 0.0)
        self.assertLess(centres["right"][2], centres["left"][2], "the right hand lies over the left")

    # -------------------------------------------------------------- face
    FACE_CUBES = ("skull", "jaw", "jaw_low", "chin")

    def face(self):
        """Front texels of the cranium and the three jaw steps by (column from the centre, row from the
        top of the cranium)."""
        out = {}
        for name in self.FACE_CUBES:
            for t in self.by_cube[name]:
                if t.face == "NORTH":
                    key = (int(round(t.p[0])), int(novice.HEAD_TOP - (GROUND - t.p[1])))
                    self.assertNotIn(key, out, f"{name} repeats face texel {key}")
                    out[key] = self.pixel(t)[:3]
        return out

    def test_face_has_a_centre_column_and_is_symmetric(self):
        face = self.face()
        self.assertEqual(11 * 8 + 9 * 2 + 7 + 5, len(face))
        for (col, row), colour in face.items():
            self.assertEqual(face[(-col, row)], colour, f"face is not symmetric at {(col, row)}")

    def test_face_rows_narrow_toward_the_chin(self):
        face = self.face()
        for row, half in ((0, 5), (7, 5), (8, 4), (9, 4), (10, 3), (11, 2)):
            cols = sorted(col for col, r in face if r == row)
            self.assertEqual(list(range(-half, half + 1)), cols, f"row {row}")

    def test_jaw_tapers_in_steps_flush_with_the_face(self):
        boxes = [self.box(name) for name in self.FACE_CUBES]
        widths = [x1 - x0 for (x0, x1), _, _ in boxes]
        self.assertEqual(sorted(widths, reverse=True), widths)
        self.assertEqual(len(set(widths)), len(widths))
        depths = [z1 - z0 for _, _, (z0, z1) in boxes]
        self.assertEqual(len(set(depths)), len(depths))
        self.assertEqual(sorted(depths, reverse=True), depths)
        for _, _, (z0, _) in boxes:
            self.assertAlmostEqual(boxes[0][2][0], z0)
        for (_, (bottom, _), _), (_, (_, top), _) in zip(boxes, boxes[1:]):
            self.assertAlmostEqual(bottom, top)
        self.assertAlmostEqual(novice.NECK, boxes[-1][1][0])

    def test_eyes_are_two_rows_with_a_bright_iris_and_an_upswept_lash(self):
        face = self.face()
        white = novice._rgb(novice.EYE_WHITE)
        for sign in (1, -1):
            for row in (5, 6):
                self.assertEqual(white, face[(2 * sign, row)])
                self.assertEqual(white, face[(4 * sign, row)])
            self.assertEqual(novice._rgb(novice.PUPIL), face[(3 * sign, 5)])
            self.assertEqual(novice._rgb(novice.IRIS), face[(3 * sign, 6)])
            self.assertEqual(novice._rgb(novice.LASH), face[(5 * sign, 5)], "lash at the outer corner")
            self.assertNotEqual(novice._rgb(novice.LASH), face[(5 * sign, 6)], "the lash sweeps up, not down")
        self.assertGreater(luma(novice._rgb(novice.IRIS)), luma(male._rgb(male.IRIS)))

    def test_willow_brow_lies_on_the_eye_and_its_tail_fades_on_the_same_row(self):
        face = self.face()
        brow = novice._rgb(novice.BROW)
        plain = face[(1, 4)]
        for sign in (1, -1):
            self.assertEqual(brow, face[(3 * sign, 4)])
            self.assertEqual(brow, face[(4 * sign, 4)])
            head, tail = face[(2 * sign, 4)], face[(5 * sign, 4)]
            for end in (head, tail):
                self.assertNotEqual(brow, end)
                self.assertNotEqual(plain, end)
                self.assertGreater(luma(end), luma(brow), "the ends are thinner (lighter) than the middle")
        row3 = {face[(col, 3)] for col in range(-5, 6)}
        self.assertEqual(1, len(row3), "nothing of the brow on the row above it: no stairs")
        self.assertNotIn(brow, row3)

    def test_mouth_is_three_warm_texels_the_middle_deeper(self):
        face = self.face()
        mid, side = novice._rgb(novice.MOUTH_MID), novice._rgb(novice.MOUTH)
        self.assertEqual(mid, face[(0, 10)])
        self.assertEqual(side, face[(1, 10)])
        self.assertEqual(side, face[(-1, 10)])
        self.assertEqual({(-1, 10), (0, 10), (1, 10)}, {k for k, v in face.items() if v in (mid, side)})
        self.assertLess(luma(mid), luma(side))
        for c in (mid, side):
            self.assertGreater(c[0], c[1] + 40, "warm lips")
            self.assertGreater(c[0], c[2] + 40)
        self.assertGreater(luma(side), luma(male._rgb(male.MOUTH)), "pale lips for the novice")

    def test_no_forehead_mark(self):
        face = self.face()
        self.assertEqual(face[(0, 2)], face[(0, 3)])
        self.assertEqual(face[(0, 2)], face[(0, 4)])
        self.assertFalse(hasattr(novice, "MARK"))

    def test_open_forehead_and_sideburn_to_the_cranium_bottom(self):
        face = self.face()
        hair = novice._tone(novice.HAIR, 1.6)
        for col in range(-5, 6):
            self.assertEqual(hair, face[(col, 0)], f"r0 a={col}")
        for col in (5, -5):
            self.assertEqual(hair, face[(col, 1)])
            for row in range(2, 8):
                self.assertNotEqual(hair, face[(col, row)], f"r{row} a={col}")
        for row, half in ((1, 4), (2, 5), (5, 5), (7, 5), (8, 4), (10, 3), (11, 2)):
            for col in range(-half, half + 1):
                self.assertNotEqual(hair, face[(col, row)], f"r{row} a={col}")
        shell = {}
        for t in self.by_cube["hair"]:
            if t.face == "NORTH":
                shell[(int(round(t.p[0])), int(novice.HEAD_TOP - (GROUND - t.p[1])))] = self.pixel(t)[3]
        for col in range(-4, 5):
            self.assertEqual(0, shell[(col, 1)], f"fringe at a={col}")
        for col in (5, 6, -5, -6):
            self.assertEqual(255, shell[(col, 1)])
        for row in range(0, 8):
            self.assertEqual(255, shell[(6, row)], f"sideburn r{row}")
            self.assertEqual(255, shell[(-6, row)], f"sideburn r{row}")
        self.assertEqual(0, shell[(6, 9)], "the sideburn stops at the cranium's bottom")

    def test_nose_stands_proud_and_its_top_is_dark(self):
        face_z = self.box("skull")[2][0]
        self.assertLess(self.box("nose")[2][0], face_z - 0.9)
        (x0, x1), (bottom, top), _ = self.box("nose")
        self.assertAlmostEqual(0.0, (x0 + x1) / 2.0)
        self.assertAlmostEqual(1.0, top - bottom)
        texels = {t.face: self.pixel(t)[:3] for t in self.by_cube["nose"]}
        # up-facing faces draw at full brightness, the front at about 60 %: paint the top that dark
        self.assertLess(luma(texels["DOWN"]), 0.7 * luma(texels["NORTH"]))

    def test_hair_shell_is_cut_away_round_the_face_and_whole_behind(self):
        front = [t for t in self.by_cube["hair"] if t.face == "NORTH"]
        open_ = [t for t in front if self.pixel(t)[3] == 0]
        self.assertGreater(len(open_), 80)
        self.assertTrue(all(GROUND - t.p[1] < 57.0 for t in open_), "the top row of hair must stay")
        back = [t for t in self.by_cube["hair"] if t.face == "SOUTH"]
        self.assertTrue(all(self.pixel(t)[3] == 255 for t in back))

    def test_low_ponytail_hangs_down_the_back_with_a_crimson_ribbon(self):
        (x0, x1), (bottom, top), (z0, _) = self.box("hair_back_main")
        self.assertLessEqual(x1 - x0, 4.5, "a narrow tail")
        self.assertGreaterEqual(top - bottom, 14.0, "a long tail")
        self.assertLess(top, novice.NECK + 2.0, "tied low, at the nape")
        self.assertGreater(z0, self.box("jacket")[2][1], "behind the back")
        (_, _), (rb, rt), _ = self.box("ribbon")
        self.assertTrue(rb < novice.NECK + 3.5 and rt > novice.NECK)
        ribbon = [self.pixel(t)[:3] for t in self.by_cube["ribbon"] if t.face == "SOUTH"]
        self.assertTrue(all(r > g + 50 and r > b + 50 for r, g, b in ribbon), "crimson")

    def test_one_accent_only(self):
        """Plain cloth, no jewellery: the only strongly saturated colour on the figure is the ribbon."""
        ribbon_cubes = {"ribbon", "ribbon_right_tail"}
        face_cubes = set(self.FACE_CUBES) | {"nose", "hand_right", "neck"}
        for name, texels in self.by_cube.items():
            if name in ribbon_cubes or name in face_cubes:
                continue
            for t in texels:
                r, g, b, a = self.pixel(t)
                if a:
                    self.assertLess(max(r, g, b) - min(r, g, b), 70, f"{name} has a saturated texel {(r, g, b)}")

    # -------------------------------------------------------------- stance and walk
    def sole(self, mats, side):
        bone, cube = self.built.model.cube(f"shoe_{side}")
        pts = [cuboid.mat_apply(mats[bone.name], c) for c in corners(cube)]
        low = max(p[1] for p in pts)
        return low, [p[2] for p in pts if p[1] > low - 0.05]

    def test_rest_feet_on_ground(self):
        mats = anim.posed_matrices(self.built.model)
        for side in ("right", "left"):
            self.assertAlmostEqual(GROUND, self.sole(mats, side)[0], places=6)

    def test_walk_keeps_one_foot_planted_and_lifts_the_other(self):
        length = self.clips["walk"][0]
        for i in range(64):
            mats = anim.posed_matrices(self.built.model, self.offsets("walk", length * i / 64))
            right, left = self.sole(mats, "right")[0], self.sole(mats, "left")[0]
            self.assertLessEqual(max(right, left), GROUND + 0.25, f"a foot sinks at step {i}")
            self.assertGreaterEqual(max(right, left), GROUND - 0.25, f"both feet off the ground at step {i}")

    def test_planted_foot_travels_at_an_even_speed(self):
        length = self.clips["walk"][0]
        zs = []
        for i in range(-8, 9):
            angle = self.offsets("walk", (length * i / 48) % length)["leg_right"]["rot"][0]
            zs.append(novice.HIP * math.sin(angle))
        steps = [b - a for a, b in zip(zs, zs[1:])]
        self.assertGreater(min(steps), 0.0)
        self.assertLess(max(steps) / min(steps), 1.15)

    def test_short_stride_and_a_little_hip_sway(self):
        length = self.clips["walk"][0]
        legs, rolls = [], []
        for i in range(32):
            o = self.offsets("walk", length * i / 32)
            legs.append(abs(math.degrees(o["leg_right"]["rot"][0])))
            rolls.append(math.degrees(o["body"]["rot"][2]))
        self.assertAlmostEqual(20.0, max(legs), delta=0.6)
        self.assertLess(max(legs), male.WALK_SWING)
        self.assertTrue(1.0 <= max(rolls) <= 2.0 and -2.0 <= min(rolls) <= -1.0, rolls)

    def test_ponytail_and_ties_swing_late(self):
        """The ponytail and both ties have rotation channels in the walk, and their sway peaks a quarter
        beat after the hips'."""
        walk = self.anim_doc["clips"]["walk"]
        rotated = {c["bone"] for c in walk["channels"] if c["target"] == "rotation"}
        self.assertLessEqual({"hair_back", "tie_right", "tie_left", "body"}, rotated)
        length = self.clips["walk"][0]
        samples = [self.offsets("walk", length * i / 64) for i in range(64)]
        hips = [o["body"]["rot"][2] for o in samples]
        hair = [-o["hair_back"]["rot"][2] for o in samples]
        lag = (hair.index(max(hair)) - hips.index(max(hips))) % 64
        self.assertTrue(4 <= lag <= 12, f"ponytail lags the hips by {lag}/64 of a stride")

    def test_ties_ride_the_forward_leg(self):
        length = self.clips["walk"][0]
        for i in range(32):
            o = self.offsets("walk", length * i / 32)
            forward = min(math.degrees(o["leg_right"]["rot"][0]), math.degrees(o["leg_left"]["rot"][0]))
            if forward < -2.0:
                for tie in ("tie_right", "tie_left"):
                    self.assertLessEqual(math.degrees(o[tie]["rot"][0]), 0.9 * forward + 0.5, f"{tie} at step {i}")

    def test_idle_moves_no_leg_and_breathes_half_a_texel(self):
        bones = {chan[0] for chan in self.clips["idle"][2]}
        self.assertFalse({b for b in bones if b.startswith("leg_") or b.startswith("skirt_") or b == "root"})
        length = self.clips["idle"][0]
        ys = [self.offsets("idle", length * i / 32)["body"]["pos"][1] for i in range(32)]
        self.assertAlmostEqual(0.5, max(ys) - min(ys), delta=0.06)

    # -------------------------------------------------------------- build
    def test_outputs_deterministic(self):
        again = build.Built(NAME).outputs()
        self.assertEqual(self.outputs, again)

    def test_build_check_passes(self):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(0, build.run(NAME, check=True), out.getvalue())

    def test_files_sit_beside_the_default_look(self):
        p = build.paths(NAME)
        self.assertTrue(str(p["model"]).endswith("assets/myvillage/npc/cultivator_f_novice_model.json"))
        self.assertTrue(str(p["animations"]).endswith("assets/myvillage/npc/cultivator_f_novice_animations.json"))
        self.assertTrue(str(p["texture"]).endswith("textures/entity/cultivator/cultivator_f_novice.png"))


if __name__ == "__main__":
    unittest.main()
