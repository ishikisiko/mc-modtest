"""tools/npcgen, the female adept look (f_adept): schema, atlas, face, female geometry, garments,
ornaments, gait, determinism.

Standard library only:  /usr/bin/python3 -m unittest tools.tests.test_npcgen_f_adept
"""
from __future__ import annotations

import contextlib
import io
import json
import math
import unittest

from tools.beastgen import anim, cuboid
from tools.npcgen import build
from tools.npcgen.defs import cultivator as male
from tools.npcgen.defs import cultivator_f_adept as fa

NAME = "cultivator_f_adept"
GROUND = 24.0
# the body bones every cultivator look shares, so the runtime and the avatar code see one skeleton
BODY_BONES = {"root", "body", "head", "arm_right", "arm_left", "forearm_right", "forearm_left",
              "leg_right", "leg_left", "hair_back"}
ORNAMENTS = (("pin_bead", "pin_drop"), ("pendant_jade", "pendant"), ("earring_right_drop", "earring_right"))


def corners(cube):
    return [tuple(cube.origin[k] + cube.size[k] * ((i >> k) & 1) for k in range(3)) for i in range(8)]


def luma(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def bounds(model, name, mats=None):
    """Design-space bounds of a cube: (x0, x1), (bottom, top), (z0, z1)."""
    bone, cube = model.cube(name)
    m = (mats or model.rest_matrices())[bone.name]
    pts = [cuboid.mat_apply(m, c) for c in corners(cube)]
    xs, ys, zs = ([p[k] for p in pts] for k in range(3))
    return (min(xs), max(xs)), (GROUND - max(ys), GROUND - min(ys)), (min(zs), max(zs))


def inside_depth(model, mats, a, b, n=4):
    """How deep (model units) any sample of cube `a` lies inside cube `b` in the given pose; 0 when apart."""
    ba, ca = model.cube(a)
    bb, cb = model.cube(b)
    inv = cuboid.mat_inverse_rigid(mats[bb.name])
    depth = 0.0
    for i in range(n + 1):
        for j in range(n + 1):
            for k in range(n + 1):
                p = tuple(ca.origin[q] + ca.size[q] * (i, j, k)[q] / n for q in range(3))
                loc = cuboid.mat_apply(inv, cuboid.mat_apply(mats[ba.name], p))
                depth = max(depth, min(min(loc[q] - cb.origin[q], cb.origin[q] + cb.size[q] - loc[q]) for q in range(3)))
    return depth


class FemaleAdeptTest(unittest.TestCase):
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
        cls.male = male.build_model()

    def offsets(self, clip, seconds):
        length, loop, chans = self.clips[clip]
        return anim.clip_offsets(chans, length, loop, seconds)

    def box(self, name, mats=None):
        return bounds(self.built.model, name, mats)

    def pixel(self, t):
        return self.texture[t.v][t.u]

    def channel(self, clip, bone, target="rotation"):
        for b, tgt, frames in self.clips[clip][2]:
            if b == bone and tgt == target:
                return frames
        return None

    # -------------------------------------------------------------- schema and skeleton
    def test_paths_follow_the_look(self):
        p = build.paths(NAME)
        self.assertTrue(str(p["model"]).endswith("assets/myvillage/npc/cultivator_f_adept_model.json"))
        self.assertTrue(str(p["animations"]).endswith("assets/myvillage/npc/cultivator_f_adept_animations.json"))
        self.assertTrue(str(p["texture"]).endswith("textures/entity/cultivator/cultivator_f_adept.png"))
        self.assertEqual(("myvillage:cultivator", "cultivator", "f_adept"), (fa.ID, fa.ENTITY, fa.LOOK))

    def test_model_matches_schema_and_the_default_look(self):
        d = self.model_doc
        self.assertEqual(1, d["schema"])
        self.assertEqual("myvillage:cultivator", d["id"])
        self.assertEqual(male.SCALE, d["scale"])
        self.assertEqual(male.HITBOX, fa.HITBOX)
        self.assertEqual({"bone": "head", "max_yaw": 60.0, "max_pitch": 35.0}, d["look"])
        self.assertEqual(self.male.look, d["look"])
        self.assertAlmostEqual(0.4, d["shadow_radius"])
        names = [b["name"] for b in d["bones"]]
        self.assertEqual(len(names), len(set(names)))
        for i, b in enumerate(d["bones"]):
            self.assertTrue(b["parent"] is None or b["parent"] in names[:i], b["name"])

    def test_body_bones_include_the_default_looks(self):
        mine = {b.name for b in self.built.model.bones}
        theirs = {b.name for b in self.male.bones}
        self.assertLessEqual(BODY_BONES, theirs)
        self.assertLessEqual(BODY_BONES, mine)
        parents = {b.name: b.parent for b in self.built.model.bones}
        male_parents = {b.name: b.parent for b in self.male.bones}
        for name in BODY_BONES:
            self.assertEqual(male_parents[name], parents[name], name)

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

    def test_alpha_is_binary_and_only_hollow_shells_are_cut_out(self):
        for row in self.texture:
            for r, g, b, a in row:
                self.assertIn(a, (0, 255))
        owned_hollow = {name for name in fa.HOLLOW if name in self.by_cube}
        self.assertEqual({"hair", "coat", "loop_right", "hair_back_tip"}, owned_hollow)
        for name, texels in self.by_cube.items():
            holes = sum(1 for t in texels if self.pixel(t)[3] == 0)
            if name in fa.HOLLOW:
                self.assertGreater(holes, 0, name)
                self.assertLess(holes, len(texels), name)
            else:
                self.assertEqual(0, holes, f"{name} has unpainted texels")

    def test_cloth_is_not_painted_flat(self):
        """The large cloth faces carry shading and pattern: several tones each, not one fill."""
        for name, face, least in (("coat", "SOUTH", 6), ("sleeve_upper_right", "WEST", 6),
                                  ("sleeve_lower_right", "WEST", 6), ("sleeve_hang_right_panel", "NORTH", 6),
                                  ("skirt_outer_right", "NORTH", 10), ("skirt_inner_right", "NORTH", 6),
                                  ("skirt_top", "NORTH", 4), ("hair", "SOUTH", 5), ("hair_back_main", "SOUTH", 5),
                                  ("drape_right_low_strip", "SOUTH", 3)):
            colours = {self.pixel(t)[:3] for t in self.by_cube[name] if t.face == face and self.pixel(t)[3]}
            self.assertGreaterEqual(len(colours), least, f"{name} {face}: {len(colours)} colours")
        self.assertGreaterEqual(len(set(self.face().values())), 10, "face")

    def test_silk_carries_a_narrow_highlight(self):
        """The coat's silk: a bright band a few texels wide on each vertical face, much brighter than
        the rest of the face."""
        side = [t for t in self.by_cube["sleeve_upper_right"] if t.face == "NORTH"]
        lumas = sorted(luma(self.pixel(t)) for t in side)
        bright = [v for v in lumas if v > lumas[len(lumas) // 2] + 25]
        self.assertTrue(0 < len(bright) < len(lumas) // 3, f"{len(bright)} of {len(lumas)} texels in the sheen")

    def test_skirt_deepens_toward_the_hem(self):
        front = [t for t in self.by_cube["skirt_outer_right"] if t.face == "WEST"]
        high = [luma(self.pixel(t)) for t in front if 22.0 < t.height < 26.0]
        low = [luma(self.pixel(t)) for t in front if 14.0 < t.height < 18.0]
        self.assertLess(sum(low) / len(low), sum(high) / len(high) - 5)

    def test_drape_is_pale_at_the_edges(self):
        """披帛: the edge texels of the strip are paler than its middle (a sheer look with binary alpha)."""
        back = [t for t in self.by_cube["drape_right_low_strip"] if t.face == "SOUTH" and t.local[1] < 11.0]
        edge = [luma(self.pixel(t)) for t in back if abs(t.local[0]) > 0.9]
        middle = [luma(self.pixel(t)) for t in back if abs(t.local[0]) < 0.6 and self.pixel(t)[:3] != fa._tone(fa.GOLD, 3.6)]
        self.assertGreater(sum(edge) / len(edge), sum(middle) / len(middle) + 8)

    # -------------------------------------------------------------- female geometry
    def shoulder_width(self, model):
        """Outer width across the upper sleeves at the shoulder joints."""
        mats = model.rest_matrices()
        right = cuboid.mat_apply(mats["arm_right"], (0.0, 0.0, 0.0))[0]
        _, sleeve = model.cube("sleeve_upper_right")
        return 2.0 * (abs(right) + sleeve.size[0] / 2.0)

    def test_shoulders_two_texels_narrower_and_square(self):
        self.assertAlmostEqual(self.shoulder_width(self.male) - 2.0, self.shoulder_width(self.built.model))
        (mx0, mx1), _, _ = bounds(self.male, "vest")
        (cx0, cx1), (_, coat_top), _ = self.box("coat")
        self.assertAlmostEqual((mx1 - mx0) - 2.0, cx1 - cx0)
        # no sloped caps: the coat's shoulder line runs level out to the sleeve heads
        self.assertFalse(any(b.name.startswith("vest_cap") for b in self.built.model.bones))
        _, (_, sleeve_top), _ = self.box("sleeve_upper_right")
        self.assertLess(abs(coat_top - sleeve_top), 1.0)

    def test_waist_drawn_in_and_skirt_flares(self):
        """Narrow at the waist cord, wider at the hip and wider again at the hem: 上窄下宽 below the coat."""
        def width(name, pair=False):
            if pair:
                (x0, _), _, _ = self.box(name.format("right"))
                (_, x1), _, _ = self.box(name.format("left"))
                return x1 - x0
            (x0, x1), _, _ = self.box(name)
            return x1 - x0
        coat, cord = width("coat"), width("cord")
        outer, inner = width("skirt_outer_{}", True), width("skirt_inner_{}", True)
        self.assertTrue(1.0 <= coat - cord <= 2.0, (coat, cord))
        self.assertGreater(outer, cord)
        self.assertGreater(inner, outer)

    def test_head_is_lower_and_narrower(self):
        _, (_, top), _ = self.box("skull")
        _, (_, male_top), _ = bounds(self.male, "skull")
        self.assertAlmostEqual(58.0, top)
        self.assertAlmostEqual(male_top - 2.0, top)
        # a low, wide bun over the back half of the crown, rounded by a cap two texels smaller
        (x0, x1), (bottom, bun_high), (z0, z1) = self.box("bun")
        (cx0, cx1), (_, cap_high), (cz0, cz1) = self.box("bun_top")
        _, _, (sz0, sz1) = self.box("skull")
        self.assertTrue(3.0 <= cap_high - bottom <= 4.0, "a bun 3 to 4 texels high")
        self.assertTrue(6.0 <= x1 - x0 <= 7.0 and 5.0 <= z1 - z0 <= 6.0)
        self.assertAlmostEqual((x1 - x0) - 2.0, cx1 - cx0)
        self.assertAlmostEqual((z1 - z0) - 2.0, cz1 - cz0)
        self.assertAlmostEqual(bun_high, self.box("bun_top")[1][0])
        self.assertGreaterEqual(z0, (sz0 + sz1) / 2.0 + 1.0, "its front behind the crown's centre")
        self.assertTrue(0.0 < z1 - sz1 <= 2.0, "set back a little past the cranium")
        self.assertTrue(x0 <= self.box("flower")[0][0] and self.box("flower")[0][1] <= x1)
        self.assertTrue(x0 - 0.5 <= self.box("pin_rod")[0][1], "the hairpin goes into the bun")

    def test_hands_smaller_and_half_under_the_cuff(self):
        _, hand = self.built.model.cube("hand_right")
        _, male_hand = self.male.cube("hand_right")
        self.assertLess(hand.size[0], male_hand.size[0])
        self.assertLess(hand.size[1], male_hand.size[1])
        _, cuff = self.built.model.cube("cuff_right")
        covered = min(hand.origin[1] + hand.size[1], cuff.origin[1] + cuff.size[1]) - hand.origin[1]
        self.assertAlmostEqual(hand.size[1] / 2.0, covered)

    def test_hands_folded_before_the_belly_without_passing_through(self):
        mats = self.built.model.rest_matrices()
        right, left = self.box("hand_right"), self.box("hand_left")
        for (x0, x1), (bottom, top), (z0, z1) in (right, left):
            self.assertTrue(x0 < 2.5 and x1 > -2.5, "hands meet at the centre line")
            self.assertTrue(fa.HIP - 4.0 < bottom and top < fa.CORD[1] + 3.0, "hands before the belly")
            self.assertLess(z1, self.box("cord")[2][0] + 0.5, "hands in front of the waist")
        self.assertLess(sum(right[2]), sum(left[2]), "the right hand lies over the left")
        self.assertEqual(0.0, inside_depth(self.built.model, mats, "hand_right", "hand_left"))
        self.assertEqual(0.0, inside_depth(self.built.model, mats, "hand_left", "hand_right"))

    # -------------------------------------------------------------- garments and ornaments
    def test_layers_stack_outward_on_the_chest(self):
        front = {name: self.box(name)[2][0] for name in
                 ("torso", "skirt_top", "chest_band", "coat", "coat_edge_right_band")}
        order = ["torso", "skirt_top", "chest_band", "coat", "coat_edge_right_band"]
        for inner, outer in zip(order, order[1:]):
            self.assertLess(front[outer], front[inner] - 0.05, f"{outer} must lie in front of {inner}")
        # the stand collar shows a line above the coat
        _, (_, collar_top), _ = self.box("collar")
        _, (_, coat_top), _ = self.box("coat")
        self.assertTrue(0.5 <= collar_top - coat_top <= 1.5)

    def test_coat_open_down_the_front_only(self):
        def holes(face):
            return [t for t in self.by_cube["coat"] if t.face == face and self.pixel(t)[3] == 0]
        opening = holes("NORTH")
        self.assertGreater(len(opening), 40)
        self.assertTrue(all(abs(t.p[0]) < 5.0 for t in opening))
        for face in ("WEST", "EAST", "SOUTH"):
            self.assertEqual([], holes(face))

    def test_two_skirts_outer_short_inner_long(self):
        _, (outer_bottom, outer_top), (oz0, oz1) = self.box("skirt_outer_right")
        _, (inner_bottom, inner_top), (iz0, iz1) = self.box("skirt_inner_right")
        self.assertGreater(outer_bottom, inner_bottom + 5.0)
        self.assertGreater(outer_top, inner_top)
        self.assertLessEqual(inner_top, outer_bottom - 0.2, "no shared plane between the tiers")
        self.assertGreater(inner_bottom, 0.0)
        self.assertGreater(oz1 - oz0, 0.0)
        # the outer skirt's hem carries a woven gold fret (several gold and purple texels on its band)
        band = [self.pixel(t)[:3] for t in self.by_cube["skirt_outer_right"]
                if t.face == "NORTH" and fa.OUTER_HEM + 1.0 < t.height < fa.OUTER_HEM + 1.0 + fa.FRET_ROWS]
        golds = sum(1 for c in band if c[0] > c[2] + 30 and c[1] > c[2] + 10)
        self.assertTrue(0.3 * len(band) < golds < 0.9 * len(band), (golds, len(band)))

    def test_ornaments_hang_on_their_own_bones(self):
        for cube, bone in ORNAMENTS:
            owner, c = self.built.model.cube(cube)
            self.assertEqual(bone, owner.name)
            self.assertLessEqual(c.size[0] * c.size[1] * c.size[2], 9.0, cube)
        self.assertIn("earring_left", {b.name for b in self.built.model.bones})
        # the hairpin's drop hangs plumb under the tilted rod
        mats = self.built.model.rest_matrices()
        down = cuboid.mat_apply(mats["pin_drop"], (0.0, 1.0, 0.0))
        origin = cuboid.mat_apply(mats["pin_drop"], (0.0, 0.0, 0.0))
        self.assertAlmostEqual(1.0, down[1] - origin[1], places=6)

    def test_drops_swing_about_a_texel_in_idle(self):
        length = self.clips["idle"][0]
        for cube, bone in ORNAMENTS:
            _, c = self.built.model.cube(cube)
            reach = c.origin[1] + c.size[1]          # from the pivot to the drop's lower end
            frames = self.channel("idle", bone)
            self.assertIsNotNone(frames, bone)
            swing = max(math.hypot(v[0], v[2]) for _, v, _ in frames)
            moved = reach * math.sin(math.radians(swing))
            self.assertTrue(0.4 <= moved <= 1.6, f"{bone}: {moved:.2f} texels")
        self.assertGreater(length, 0.0)

    def test_drapes_hang_outside_the_sleeves_and_flare(self):
        """The shawl's ends hang from behind the upper arms, just outside the sleeves, opening outward
        a few degrees, down to the knees; the band across the back is a flat V close to the back."""
        (sx0, _), _, (_, sz1) = self.box("sleeve_upper_right")
        (dx0, dx1), (_, dtop), (dz0, _) = self.box("drape_right_strip")
        self.assertLessEqual(dx1, sx0 + 0.5, "outside the sleeve's outer face")
        self.assertGreater(dz0, 0.0, "behind the arm's axis")
        self.assertTrue(6.0 <= fa.DRAPE_FLARE <= 8.0)
        _, (bottom, _), _ = self.box("drape_right_low_strip")
        self.assertTrue(9.0 <= bottom <= 13.0, bottom)
        _, (vb, vt), (vz0, _) = self.box("drape_sag_right_band")
        _, _, (_, coat_back) = self.box("coat")
        self.assertTrue(0.0 < vz0 - coat_back <= 0.25, "lying on the back")
        self.assertLess(vt - vb, 9.0, "a flat V")

    def test_drapes_and_hair_trail_the_walk(self):
        """The shawl's ends and the back hair have their own rotation channels in walk, a quarter beat
        behind the legs (they peak after the leg does)."""
        bones = {b.name for b in self.built.model.bones}
        self.assertLessEqual({"drape_right", "drape_left"}, bones)
        length = self.clips["walk"][0]
        steps = 64
        leg = [math.degrees(self.offsets("walk", length * i / steps)["leg_right"]["rot"][0]) for i in range(steps)]
        for bone in ("drape_right", "drape_left", "hair_back"):
            frames = self.channel("walk", bone)
            self.assertIsNotNone(frames, bone)
            xs = [math.degrees(self.offsets("walk", length * i / steps)[bone]["rot"][0]) for i in range(steps)]
            self.assertGreater(max(xs) - min(xs), 3.0, bone)
            # not in step with the leg: the swing peaks away from the leg's turning points
            peak = xs.index(max(xs))
            self.assertGreater(min(abs(peak - leg.index(max(leg))), abs(peak - leg.index(min(leg)))), 2, bone)

    def test_hanging_sleeves_ride_the_legs(self):
        """The deep sleeves hang over the skirt; the leg under each never comes through it."""
        length = self.clips["walk"][0]
        m = self.built.model
        for i in range(32):
            mats = anim.posed_matrices(m, self.offsets("walk", length * i / 32))
            for sleeve in ("sleeve_hang_right_panel", "sleeve_hang_left_panel", "pendant_jade", "pendant_tassel"):
                for skirt in ("skirt_outer_right", "skirt_outer_left"):
                    self.assertLessEqual(inside_depth(m, mats, sleeve, skirt), 0.25, f"{sleeve} in {skirt} at {i}")

    # -------------------------------------------------------------- face
    FACE_CUBES = ("skull", "jaw", "jaw_low", "chin")

    def face(self):
        out = {}
        for name in self.FACE_CUBES:
            for t in self.by_cube[name]:
                if t.face == "NORTH":
                    key = (int(round(t.p[0])), int(fa.HEAD_TOP - (GROUND - t.p[1])))
                    self.assertNotIn(key, out, f"{name} repeats face texel {key}")
                    out[key] = self.pixel(t)[:3]
        return out

    def test_face_is_symmetric_about_a_centre_column(self):
        face = self.face()
        self.assertEqual(11 * 8 + 9 * 2 + 7 + 5, len(face))
        for (col, row), colour in face.items():
            self.assertEqual(face[(-col, row)], colour, f"face is not symmetric at {(col, row)}")

    def test_jaw_narrows_in_steps_to_a_pointed_chin(self):
        face = self.face()
        for row, half in ((0, 5), (7, 5), (8, 4), (9, 4), (10, 3), (11, 2)):
            cols = sorted(col for col, r in face if r == row)
            self.assertEqual(list(range(-half, half + 1)), cols, f"row {row}")
        boxes = [self.box(name) for name in self.FACE_CUBES]
        widths = [x1 - x0 for (x0, x1), _, _ in boxes]
        self.assertEqual([11.0, 9.0, 7.0, 5.0], widths)
        male_widths = [x1 - x0 for (x0, x1), _, _ in (bounds(self.male, n) for n in self.FACE_CUBES)]
        self.assertEqual([w - 2.0 for w in male_widths], widths)
        depths = [z1 - z0 for _, _, (z0, z1) in boxes]
        self.assertEqual(sorted(set(depths), reverse=True), depths)
        for _, _, (z0, _) in boxes:
            self.assertAlmostEqual(boxes[0][2][0], z0)
        for (_, (bottom, _), _), (_, (_, top), _) in zip(boxes, boxes[1:]):
            self.assertAlmostEqual(bottom, top)
        self.assertAlmostEqual(fa.NECK, boxes[-1][1][0])
        # taller than wide: a narrow, long face
        (x0, x1), _, _ = boxes[0]
        self.assertGreater(fa.HEAD_TOP - fa.NECK, x1 - x0)

    def test_eyes_two_rows_with_a_lifted_outer_lash(self):
        face = self.face()
        white, lash = fa._rgb(fa.EYE_WHITE), fa._rgb(fa.LASH)
        irises = {fa._rgb(fa.IRIS), fa._rgb(fa.IRIS_TOP)}
        for row in (4, 5):
            self.assertEqual(white, face[(2, row)], row)
            self.assertIn(face[(3, row)], irises, row)
        self.assertEqual(lash, face[(4, 4)], "the lash lifts at the outer corner of the upper row")
        self.assertEqual(white, face[(4, 5)])
        self.assertEqual(1, sum(1 for v in face.values() if v == lash) // 2, "one lash texel per eye")
        # a brighter iris than the default look's
        self.assertGreater(luma(fa._rgb(fa.IRIS)), luma(male._rgb(male.IRIS)) + 15)

    def test_willow_brow_lies_on_the_eye_and_fades_on_its_row(self):
        face = self.face()
        brow = fa._rgb(fa.BROW)
        plain = face[(2, 2)]
        self.assertEqual(brow, face[(3, 3)], "the brow's middle")
        # thin at both ends: the head and the tail are paler than the middle, darker than skin
        for col in (1, 2, 4):
            self.assertGreater(luma(face[(col, 3)]), luma(brow), col)
            self.assertLess(luma(face[(col, 3)]), luma(plain) - 5, col)
        self.assertLess(luma(face[(2, 3)]), luma(face[(1, 3)]), "fuller toward the middle")
        # no step: nothing of the brow on the row above, and the eye's top row is directly under it
        for col in range(-3, 4):
            self.assertGreater(luma(face[(col, 2)]), luma(plain) - 3, f"r2 a={col}")
        self.assertEqual(fa._rgb(fa.EYE_WHITE), face[(2, 4)])

    def test_lips_three_warm_texels_darker_in_the_middle(self):
        face = self.face()
        lips = [face[(col, 9)] for col in (-1, 0, 1)]
        for r, g, b in lips:
            self.assertGreater(r, g + 30)
            self.assertGreater(r, b + 25)
        self.assertEqual(lips[0], lips[2])
        self.assertLess(luma(lips[1]), luma(lips[0]))
        self.assertGreater(luma(male._rgb(male.MOUTH)), luma(lips[0]), "a deeper lip than the default mouth")
        reds = {k for k, (r, g, b) in face.items() if r > g + 65}
        self.assertEqual({(-1, 9), (0, 9), (1, 9)}, reds)

    def test_open_forehead_straight_hairline_no_mark(self):
        face = self.face()
        hair = fa._tone(fa.HAIR, 1.6)
        for col in range(-5, 6):
            self.assertEqual(hair, face[(col, 0)])
        for row in range(0, 8):
            self.assertEqual(hair, face[(5, row)], f"sideburn r{row}")
        self.assertEqual(hair, face[(4, 1)])
        for row in (1, 2):
            colours = {face[(col, row)] for col in range(-3, 4)}
            self.assertNotIn(hair, colours)
            self.assertLessEqual(max(luma(c) for c in colours) - min(luma(c) for c in colours), 12,
                                 f"r{row}: nothing drawn on the forehead")
        row = {}
        for t in self.by_cube["hair"]:
            if t.face == "NORTH" and int(fa.HEAD_TOP - (GROUND - t.p[1])) == 1:
                row[int(round(t.p[0]))] = self.pixel(t)[3]
        for col in range(-3, 4):
            self.assertEqual(0, row[col], col)
        for col in (4, 5, 6):
            self.assertEqual(255, row[col], col)
            self.assertEqual(255, row[-col], -col)

    def test_nose_stands_proud_and_its_top_is_dark(self):
        face_z = self.box("skull")[2][0]
        (x0, x1), (bottom, top), (z0, _) = self.box("nose")
        self.assertLess(z0, face_z - 0.9)
        self.assertAlmostEqual(0.0, (x0 + x1) / 2.0)
        top_face = [self.pixel(t)[:3] for t in self.by_cube["nose"] if t.face == "DOWN"]
        front = [self.pixel(t)[:3] for t in self.by_cube["nose"] if t.face == "NORTH"]
        self.assertLess(luma(top_face[0]), 0.7 * luma(front[0]))

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

    def test_walk_short_stride_keeps_one_foot_planted(self):
        length = self.clips["walk"][0]
        swing = 0.0
        for i in range(64):
            o = self.offsets("walk", length * i / 64)
            swing = max(swing, abs(math.degrees(o["leg_right"]["rot"][0])))
            mats = anim.posed_matrices(self.built.model, o)
            right, left = self.sole(mats, "right")[0], self.sole(mats, "left")[0]
            self.assertLessEqual(max(right, left), GROUND + 0.25, f"a foot sinks at step {i}")
            self.assertGreaterEqual(max(right, left), GROUND - 0.25, f"both feet off the ground at step {i}")
        self.assertAlmostEqual(fa.WALK_SWING, swing, delta=0.5)
        self.assertLess(fa.WALK_SWING, male.WALK_SWING)

    def test_planted_foot_travels_at_an_even_speed(self):
        length = self.clips["walk"][0]
        zs = []
        for i in range(-8, 9):
            angle = self.offsets("walk", (length * i / 48) % length)["leg_right"]["rot"][0]
            zs.append(fa.HIP * math.sin(angle))
        steps = [b - a for a, b in zip(zs, zs[1:])]
        self.assertGreater(min(steps), 0.0)
        self.assertLess(max(steps) / min(steps), 1.15)

    def test_hips_sway_a_degree_or_two(self):
        length = self.clips["walk"][0]
        zs = [math.degrees(self.offsets("walk", length * i / 32)["body"]["rot"][2]) for i in range(32)]
        self.assertTrue(1.0 <= max(zs) <= 2.0 and -2.0 <= min(zs) <= -1.0, (min(zs), max(zs)))

    def test_idle_breathes_and_moves_no_leg(self):
        bones = {chan[0] for chan in self.clips["idle"][2]}
        self.assertFalse({b for b in bones if b.startswith("leg_") or b == "root"})
        frames = self.channel("idle", "body", "position")
        ys = [v[1] for _, v, _ in frames]
        self.assertAlmostEqual(0.5, max(ys) - min(ys), delta=0.05)

    # -------------------------------------------------------------- build
    def test_outputs_deterministic(self):
        self.assertEqual(self.outputs, build.Built(NAME).outputs())

    def test_build_check_passes(self):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(0, build.run(NAME, check=True), out.getvalue())


if __name__ == "__main__":
    unittest.main()
