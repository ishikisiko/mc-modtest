"""tools/npcgen: schema, layered model, cut-out atlas, face details, planted-foot walk, determinism.

Standard library only:  /usr/bin/python3 -m unittest tools.tests.test_npcgen
"""
from __future__ import annotations

import contextlib
import io
import json
import math
import unittest

from tools.beastgen import anim, cuboid
from tools.npcgen import build, shade
from tools.npcgen.defs import cultivator

NAME = "cultivator"
GROUND = 24.0
# Cubes whose texture is partly cut out on purpose; every other island is fully opaque.
CUT_OUT = {"hair", "vest", "pendant_jade", "hair_back_tip"}


def corners(cube):
    return [tuple(cube.origin[k] + cube.size[k] * ((i >> k) & 1) for k in range(3)) for i in range(8)]


class NpcgenTest(unittest.TestCase):
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

    def offsets(self, clip, seconds):
        length, loop, chans = self.clips[clip]
        return anim.clip_offsets(chans, length, loop, seconds)

    def box(self, name):
        """Rest-pose design-space bounds of a cube: (x0, x1), (bottom, top), (z0, z1)."""
        bone, cube = self.built.model.cube(name)
        m = self.built.model.rest_matrices()[bone.name]
        pts = [cuboid.mat_apply(m, c) for c in corners(cube)]
        xs, ys, zs = ([p[k] for p in pts] for k in range(3))
        return (min(xs), max(xs)), (GROUND - max(ys), GROUND - min(ys)), (min(zs), max(zs))

    def pixel(self, t):
        return self.texture[t.v][t.u]

    # -------------------------------------------------------------- schema
    def test_model_matches_schema(self):
        d = self.model_doc
        self.assertEqual(1, d["schema"])
        self.assertEqual("myvillage:cultivator", d["id"])
        self.assertEqual(0.5, d["scale"])
        self.assertEqual("head", d["look"]["bone"])
        names = [b["name"] for b in d["bones"]]
        self.assertEqual(len(names), len(set(names)))
        for i, b in enumerate(d["bones"]):
            self.assertTrue(b["parent"] is None or b["parent"] in names[:i], b["name"])

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
        self.assertGreater(len(twins), 10)
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
        """The large cloth faces carry shading and pattern: several tones each, not one fill."""
        for name, face, least in (("sleeve_upper_right", "WEST", 6), ("sleeve_lower_right", "WEST", 8),
                                  ("robe_lower_right", "NORTH", 10), ("vest", "SOUTH", 5),
                                  ("vest_back_panel", "SOUTH", 6), ("hair", "SOUTH", 5)):
            colours = {self.pixel(t)[:3] for t in self.by_cube[name] if t.face == face and self.pixel(t)[3]}
            self.assertGreaterEqual(len(colours), least, f"{name} {face}: {len(colours)} colours")
        # the face spans the cranium and the three jaw steps, so it is counted across all four
        self.assertGreaterEqual(len(set(self.face().values())), 8, "face")

    # -------------------------------------------------------------- layers
    def test_layers_stack_outward_on_the_chest(self):
        front = {name: self.box(name)[2][0] for name in
                 ("torso", "collar_under_band", "collar_over_band", "vest", "vest_edge_right_band", "belt", "buckle")}
        order = ["torso", "collar_under_band", "collar_over_band", "vest", "vest_edge_right_band", "buckle"]
        for inner, outer in zip(order, order[1:]):
            self.assertLess(front[outer], front[inner] - 0.05, f"{outer} must lie in front of {inner}")
        self.assertLess(front["belt"], front["vest"])
        for name in ("vest", "belt"):
            (x0, x1), _, (z0, z1) = self.box(name)
            (tx0, tx1), _, (tz0, tz1) = self.box("torso")
            self.assertTrue(x0 < tx0 and x1 > tx1 and z0 < tz0 and z1 > tz1, name)

    def test_vest_opening_shows_the_robe_and_collar(self):
        """The vest's front is cut open down the middle; its sides and back are whole."""
        def holes(face):
            return [t for t in self.by_cube["vest"] if t.face == face and self.pixel(t)[3] == 0]

        opening = holes("NORTH")
        self.assertGreater(len(opening), 60)
        self.assertTrue(all(abs(t.p[0]) < 5.0 for t in opening))
        for face in ("WEST", "EAST", "SOUTH"):
            self.assertEqual([], holes(face))

    def test_cast_shadows_come_from_real_occluders(self):
        """The skirt just under the belt is darker than the same cloth further down: the belt sticks
        out past it, and that shadow is baked from the geometry."""
        occ = shade.Occluders(self.built.model, cultivator.HOLLOW)
        side = [t for t in self.by_cube["robe_upper_right"] if t.face == "WEST"]
        shadowed = [t for t in side if occ.overhang(t) > 0.4 and GROUND - t.p[1] > 27.0]
        clear = [t for t in side if occ.overhang(t) == 0.0 and occ.contact(t) == 0.0 and GROUND - t.p[1] < 27.0]
        self.assertTrue(shadowed and clear)
        luma = lambda ts: sum(sum(self.pixel(t)[:3]) for t in ts) / len(ts)  # noqa: E731
        self.assertLess(luma(shadowed), luma(clear) - 20)

    def test_occluders_skip_hollow_shells(self):
        occ = shade.Occluders(self.built.model, cultivator.HOLLOW)
        names = {box[0] for box in occ.boxes}
        self.assertFalse(names & set(cultivator.HOLLOW))
        self.assertEqual("belt", occ.hit((0.0, GROUND - 32.0, -5.9)))
        self.assertIsNone(occ.hit((0.0, GROUND - 32.0, -5.9), exclude=("belt",)))

    # -------------------------------------------------------------- face
    FACE_CUBES = ("skull", "jaw", "jaw_low", "chin")

    def face(self):
        """Front texels of the cranium and the three jaw steps by (column from the centre, row from the
        top of the cranium)."""
        out = {}
        for name in self.FACE_CUBES:
            for t in self.by_cube[name]:
                if t.face == "NORTH":
                    key = (int(round(t.p[0])), int(cultivator.HEAD_TOP - (GROUND - t.p[1])))
                    self.assertNotIn(key, out, f"{name} repeats face texel {key}")
                    out[key] = self.pixel(t)[:3]
        return out

    def test_face_has_a_centre_column_and_symmetric_eyes(self):
        face = self.face()
        self.assertEqual(13 * 8 + 11 * 2 + 9 + 7, len(face))
        for (col, row), colour in face.items():
            self.assertEqual(face[(-col, row)], colour, f"face is not symmetric at {(col, row)}")
        white, iris = cultivator._rgb(cultivator.EYE_WHITE), cultivator._rgb(cultivator.IRIS)
        for col in (2, 4):
            self.assertEqual(white, face[(col, 5)])
            self.assertEqual(white, face[(-col, 5)])
        self.assertEqual(iris, face[(3, 5)])
        self.assertEqual(iris, face[(-3, 5)])

    def test_face_rows_narrow_toward_the_chin(self):
        face = self.face()
        for row, half in ((0, 6), (7, 6), (8, 5), (9, 5), (10, 4), (11, 3)):
            cols = sorted(col for col, r in face if r == row)
            self.assertEqual(list(range(-half, half + 1)), cols, f"row {row}")

    def test_visible_skin_widths(self):
        """The sideburn (a = 6) is hair from the top of the cranium to its bottom; skin runs to a = 5
        from row r1 down to r9, a = 4 on r10, a = 3 on r11."""
        face = self.face()
        hair = cultivator._tone(cultivator.HAIR, 1.6)
        for col in range(-6, 7):
            self.assertEqual(hair, face[(col, 0)], f"r0 a={col}")
        for row in range(0, 8):
            self.assertEqual(hair, face[(6, row)], f"r{row}")
        for row, half in ((1, 4), (2, 5), (5, 5), (7, 5), (8, 5), (9, 5), (10, 4), (11, 3)):
            for col in range(-half, half + 1):
                self.assertNotEqual(hair, face[(col, row)], f"r{row} a={col}")
        self.assertEqual(hair, face[(5, 1)])

    def test_mouth_is_three_texels_of_one_colour(self):
        face = self.face()
        mouth = cultivator._rgb(cultivator.MOUTH)
        for col in (-1, 0, 1):
            self.assertEqual(mouth, face[(col, 10)])
        self.assertEqual({(-1, 10), (0, 10), (1, 10)}, {k for k, v in face.items() if v == mouth})

    def test_brow_lies_on_the_eye_and_its_tail_fades_on_the_same_row(self):
        face = self.face()
        brow = cultivator._rgb(cultivator.BROW)
        for col in (2, 3, 4):
            self.assertEqual(brow, face[(col, 4)])
            self.assertEqual(brow, face[(-col, 4)])
        plain = face[(1, 4)]
        for col in (5, -5):
            tail = face[(col, 4)]
            self.assertNotEqual(brow, tail)
            self.assertNotEqual(plain, tail)
        row3 = {face[(col, 3)] for col in range(-5, 6)}
        self.assertNotIn(brow, row3)
        self.assertNotIn(face[(5, 4)], row3, "no trace of the brow on the row above it")

    def test_no_mark_lash_or_lip(self):
        old_mark = cultivator._rgb("#3FC4C0")
        self.assertNotIn(old_mark, set(self.face().values()))
        for name in ("MARK", "LIP", "LASH"):
            self.assertFalse(hasattr(cultivator, name), name)

    def test_jaw_tapers_in_steps_flush_with_the_face(self):
        boxes = [self.box(name) for name in self.FACE_CUBES]
        widths = [x1 - x0 for (x0, x1), _, _ in boxes]
        self.assertEqual(sorted(widths, reverse=True), widths)
        self.assertEqual(len(set(widths)), len(widths), "each step must be narrower than the one above")
        depths = [z1 - z0 for _, _, (z0, z1) in boxes]
        self.assertEqual(len(set(depths)), len(depths))
        self.assertEqual(sorted(depths, reverse=True), depths)
        for _, _, (z0, _) in boxes:
            self.assertAlmostEqual(boxes[0][2][0], z0)
        # stacked without gaps: each step's top is the bottom of the one above
        for (_, (bottom, _), _), (_, (_, top), _) in zip(boxes, boxes[1:]):
            self.assertAlmostEqual(bottom, top)
        self.assertAlmostEqual(cultivator.NECK, boxes[-1][1][0])

    def test_nose_and_strands_stand_proud_of_the_face(self):
        face_z = self.box("skull")[2][0]
        self.assertLess(self.box("nose")[2][0], face_z - 0.9)
        self.assertLess(self.box("strand_right_lock")[2][0], self.box("hair")[2][0] - 0.9)
        (x0, x1), (bottom, top), _ = self.box("nose")
        self.assertAlmostEqual(0.0, (x0 + x1) / 2.0)
        self.assertAlmostEqual(2.0, top - bottom)

    def test_hair_shell_is_cut_away_round_the_face(self):
        front = [t for t in self.by_cube["hair"] if t.face == "NORTH"]
        open_ = [t for t in front if self.pixel(t)[3] == 0]
        self.assertGreater(len(open_), 90)
        self.assertTrue(all(GROUND - t.p[1] < 59.0 for t in open_), "the top row of hair must stay")
        back = [t for t in self.by_cube["hair"] if t.face == "SOUTH"]
        self.assertTrue(all(self.pixel(t)[3] == 255 for t in back))

    def test_forehead_is_open_under_a_straight_hairline(self):
        """Row r1 of the hair shell's front: open over the whole forehead, hair from the temples out."""
        row = {}
        for t in self.by_cube["hair"]:
            if t.face == "NORTH" and int(cultivator.HEAD_TOP - (GROUND - t.p[1])) == 1:
                row[int(round(t.p[0]))] = self.pixel(t)[3]
        for col in range(-4, 5):
            self.assertEqual(0, row[col], col)
        for col in (5, 6, 7):
            self.assertEqual(255, row[col], col)
            self.assertEqual(255, row[-col], -col)

    # -------------------------------------------------------------- stance and walk
    def sole(self, mats, side):
        bone, cube = self.built.model.cube(f"boot_{side}")
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
            # a quarter of a model unit is 1/128 block: the spline between keys may round that much
            self.assertLessEqual(max(right, left), GROUND + 0.25, f"a foot sinks at step {i}")
            self.assertGreaterEqual(max(right, left), GROUND - 0.25, f"both feet off the ground at step {i}")

    def test_planted_foot_travels_at_an_even_speed(self):
        """While the right leg carries the weight its hip joint moves back evenly, so a body walking at
        a steady speed does not slide over the foot."""
        length = self.clips["walk"][0]
        # the right leg plants while cos(phase) >= 0; the middle two thirds of that, clear of the turns
        zs = []
        for i in range(-8, 9):
            angle = math.radians(self.offsets("walk", (length * i / 48) % length)["leg_right"]["rot"][0] * 180.0 / math.pi)
            zs.append(cultivator.HIP * math.sin(angle))
        steps = [b - a for a, b in zip(zs, zs[1:])]
        self.assertGreater(min(steps), 0.0)
        self.assertLess(max(steps) / min(steps), 1.15)

    def test_panels_ride_the_forward_leg(self):
        length = self.clips["walk"][0]
        for i in range(32):
            o = self.offsets("walk", length * i / 32)
            for side in ("right", "left"):
                leg = math.degrees(o[f"leg_{side}"]["rot"][0])
                panel = math.degrees(o[f"vest_front_{side}"]["rot"][0])
                if leg < -2.0:
                    self.assertLessEqual(panel, 0.9 * leg + 0.5, f"{side} panel lags its leg at step {i}")
            back = math.degrees(o["vest_back"]["rot"][0])
            swing = max(math.degrees(o["leg_right"]["rot"][0]), math.degrees(o["leg_left"]["rot"][0]))
            self.assertGreaterEqual(back, 0.85 * swing - 0.5)

    def test_idle_moves_no_leg(self):
        bones = {chan[0] for chan in self.clips["idle"][2]}
        self.assertFalse({b for b in bones if b.startswith("leg_") or b == "root"})

    # -------------------------------------------------------------- build
    def test_outputs_deterministic(self):
        again = build.Built(NAME).outputs()
        self.assertEqual(self.outputs, again)

    def test_build_check_passes(self):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(0, build.run(NAME, check=True), out.getvalue())

    def test_unscaled_models_omit_scale(self):
        model = cuboid.Model("myvillage:x", [cuboid.Bone("root", None, (0, 24, 0), cubes=[cuboid.Cube("c", (0, 0, 0), (1, 1, 1))])],
                             {"bone": "root", "max_yaw": 0.0, "max_pitch": 0.0}, 0.5)
        cuboid.pack(model)
        self.assertNotIn("scale", cuboid.model_json(model))
        self.assertEqual(1.0, cuboid.model_from_json(cuboid.model_json(model)).scale)


if __name__ == "__main__":
    unittest.main()
