"""tools/beastgen: schema, model, atlas, clips and server timing, ground contact, determinism.

Standard library only:  /usr/bin/python3 -m unittest tools.tests.test_beastgen
"""
from __future__ import annotations

import contextlib
import io
import json
import math
import re
import unittest

from tools.beastgen import anim, build, cuboid
from tools.beastgen.defs import demon_wolf

NAME = "demon_wolf"
REQUIRED = {"idle": True, "walk": True, "run": True, "bite": False, "pounce": False, "stagger": False}
GROUND = 24.0


def paw_bottom_y(model, mats):
    """Lowest model-space y (largest, +Y down) over every paw bone's cube corners."""
    out = {}
    for b in model.bones:
        if not b.name.endswith("_paw"):
            continue
        m = mats[b.name]
        ys = []
        for c in b.cubes:
            for i in range(8):
                p = tuple(c.origin[k] + c.size[k] * ((i >> k) & 1) for k in range(3))
                ys.append(cuboid.mat_apply(m, p)[1])
        out[b.name] = max(ys)
    return out


class BeastgenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.built = build.Built(NAME)
        cls.outputs = cls.built.outputs()
        p = build.paths(NAME)
        cls.model_doc = json.loads(cls.outputs[p["model"]])
        cls.anim_doc = json.loads(cls.outputs[p["animations"]])
        cls.model = cuboid.model_from_json(cls.model_doc)
        cls.clips = anim.clips_from_json(cls.anim_doc)
        cls.server = build.server_data(NAME)

    def offsets(self, clip, seconds):
        length, loop, chans = self.clips[clip]
        return anim.clip_offsets(chans, length, loop, seconds)

    # -------------------------------------------------------------- schema
    def test_model_matches_schema(self):
        d = self.model_doc
        self.assertEqual(1, d["schema"])
        self.assertEqual("myvillage:demon_wolf", d["id"])
        self.assertEqual({"width", "height"}, set(d["texture"]))
        self.assertEqual({"bone", "max_yaw", "max_pitch"}, set(d["look"]))
        self.assertIsInstance(d["shadow_radius"], float)
        for b in d["bones"]:
            self.assertEqual({"name", "parent", "pivot", "rotation", "cubes"}, set(b))
            self.assertEqual(3, len(b["pivot"]))
            self.assertEqual(3, len(b["rotation"]))
            for c in b["cubes"]:
                self.assertEqual({"origin", "size", "uv", "inflate", "mirror"}, set(c))
                self.assertTrue(all(isinstance(v, int) for v in c["uv"]))
                self.assertIsInstance(c["mirror"], bool)
        self.assertIn(d["look"]["bone"], {b["name"] for b in d["bones"]})

    def test_animations_match_schema(self):
        d = self.anim_doc
        self.assertEqual(1, d["schema"])
        self.assertEqual("myvillage:demon_wolf", d["id"])
        self.assertEqual(set(REQUIRED), set(d["clips"]))
        for name, c in d["clips"].items():
            self.assertEqual({"length", "loop", "channels"}, set(c))
            self.assertEqual(REQUIRED[name], c["loop"], name)
            for ch in c["channels"]:
                self.assertEqual({"bone", "target", "keyframes"}, set(ch))
                self.assertIn(ch["target"], ("rotation", "position", "scale"))
                times = [k["time"] for k in ch["keyframes"]]
                self.assertEqual(sorted(times), times, f"{name}/{ch['bone']}")
                self.assertEqual(len(set(times)), len(times))
                self.assertGreaterEqual(times[0], 0.0)
                self.assertLessEqual(times[-1], c["length"] + 1e-9)
                for k in ch["keyframes"]:
                    self.assertEqual({"time", "value", "interp"}, set(k))
                    self.assertIn(k["interp"], ("linear", "catmullrom"))
                    self.assertEqual(3, len(k["value"]))

    def test_bones_parents_first_unique_names(self):
        seen = set()
        for b in self.model_doc["bones"]:
            self.assertRegex(b["name"], r"^[a-z0-9_]+$")
            self.assertNotIn(b["name"], seen)
            if b["parent"] is not None:
                self.assertIn(b["parent"], seen, f"{b['name']} before its parent")
            seen.add(b["name"])
        self.assertEqual([], self.built.model.problems())

    def test_every_animated_bone_exists(self):
        names = {b["name"] for b in self.model_doc["bones"]}
        for clip, c in self.anim_doc["clips"].items():
            for ch in c["channels"]:
                self.assertIn(ch["bone"], names, f"{clip} animates missing bone {ch['bone']}")

    # -------------------------------------------------------------- atlas
    def test_uv_islands_inside_atlas_and_disjoint(self):
        w, h = self.model_doc["texture"]["width"], self.model_doc["texture"]["height"]
        self.assertEqual(0, w & (w - 1))
        self.assertEqual(0, h & (h - 1))
        rects = {}
        for b in self.model_doc["bones"]:
            for c in b["cubes"]:
                sx, sy, sz = (int(round(v)) for v in c["size"])
                for v in c["size"]:
                    self.assertAlmostEqual(v, round(v))
                rect = (c["uv"][0], c["uv"][1], 2 * (sz + sx), sz + sy)
                self.assertGreaterEqual(rect[0], 0)
                self.assertGreaterEqual(rect[1], 0)
                self.assertLessEqual(rect[0] + rect[2], w, b["name"])
                self.assertLessEqual(rect[1] + rect[3], h, b["name"])
                rects.setdefault(tuple(c["uv"]), set()).add((rect, c["mirror"]))
        # islands that share a uv are mirrored twins of one size; distinct islands never overlap
        islands = []
        for uv, users in rects.items():
            self.assertEqual(1, len({r for r, _ in users}), f"cubes at uv {uv} differ in size")
            islands.append(next(iter(users))[0])
        for i, a in enumerate(islands):
            for b in islands[i + 1:]:
                overlap = (a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3])
                self.assertFalse(overlap, f"islands {a} and {b} overlap")

    def test_vanilla_box_unwrap(self):
        # vanilla wolf head: texOffs(0, 0).addBox(-2, -3, -2, 6, 6, 4): face at u 4..10, v 4..10
        c = cuboid.Cube("h", (-2, -3, -2), (6, 6, 4), uv=(0, 0))
        regions = cuboid.uv_regions(c)
        self.assertEqual((4, 4, 6, 6), regions["NORTH"])
        self.assertEqual((4, 0, 6, 4), regions["DOWN"])
        polys = dict(cuboid.cube_polygons(c, 64, 32))
        north = polys["NORTH"]
        self.assertTrue(all(abs(v[2] + 2.0) < 1e-9 for v in north))  # z = minZ
        us = sorted({round(v[3] * 64) for v in north})
        self.assertEqual([4, 10], us)
        # mirror swaps x and keeps each vertex's uv: the minX corner gets the maxX corner's uv
        m = cuboid.Cube("m", (-2, -3, -2), (6, 6, 4), mirror=True, uv=(0, 0))
        mn = dict(cuboid.cube_polygons(m, 64, 32))["NORTH"]
        uv_at = {(round(v[0], 3), round(v[1], 3)): (round(v[3] * 64), round(v[4] * 32)) for v in north}
        uv_m = {(round(v[0], 3), round(v[1], 3)): (round(v[3] * 64), round(v[4] * 32)) for v in mn}
        self.assertEqual(uv_at[(-2.0, -3.0)], uv_m[(4.0, -3.0)])

    def test_mirrored_twins_mirror_their_source(self):
        model = self.built.model
        rest = model.rest_matrices()
        tw, th = model.texture_size
        for b, c in model.cubes():
            if not c.uv_from:
                continue
            sb, sc = model.cube(c.uv_from)
            self.assertTrue(c.mirror)
            src = {}
            for d, verts in cuboid.cube_polygons(sc, tw, th):
                for v in verts:
                    src[(round(v[3], 6), round(v[4], 6), d)] = cuboid.mat_apply(rest[sb.name], v[:3])
            for d, verts in cuboid.cube_polygons(c, tw, th):
                for v in verts:
                    p = cuboid.mat_apply(rest[b.name], v[:3])
                    q = [src[k] for k in src if k[:2] == (round(v[3], 6), round(v[4], 6))]
                    self.assertTrue(any(abs(p[0] + s[0]) < 1e-6 and abs(p[1] - s[1]) < 1e-6
                                        and abs(p[2] - s[2]) < 1e-6 for s in q),
                                    f"{c.name} texel corner {v[3:]} is not the mirror of {c.uv_from}")

    def test_glow_layer_eyes_mane_tips_and_tail_tip(self):
        tex, glow = self.built.texture, self.built.glow
        lit = [(x, y) for y, row in enumerate(glow) for x, px in enumerate(row) if px[3]]
        self.assertTrue(lit)
        self.assertEqual(len(tex), len(glow))
        self.assertEqual(len(tex[0]), len(glow[0]))
        by_uv = {(t.u, t.v): t for t in self.built.texels}
        eyes = 0
        for x, y in lit:
            t = by_uv[(x, y)]
            self.assertTrue(t.cube == "skull" or t.bone.startswith("mane_") or t.cube == "tail_tip", t.cube)
            if t.cube == "skull":
                eyes += 1
                self.assertIn(t.face, ("NORTH", "WEST", "EAST"))
                self.assertLess(t.local[1], 0.0)  # above the muzzle line
        self.assertEqual(6, eyes)  # two texels on the face and one wrapped onto each side

    def test_eyes_nose_teeth_on_the_right_faces(self):
        by = {}
        for t in self.built.texels:
            by.setdefault((t.cube, t.face), []).append(self.built.texture[t.v][t.u])
        eye = demon_wolf._hexc(demon_wolf.EYE[0])
        self.assertIn(eye + (255,), by[("skull", "NORTH")])
        self.assertNotIn(eye + (255,), by[("skull", "SOUTH")])
        tooth = set(c + (255,) for c in demon_wolf.TOOTH)
        self.assertTrue(tooth & set(by[("jaw_cube", "WEST")]))  # the closed lip shows a glint of tooth
        self.assertTrue(tooth & set(by[("muzzle", "UP")]))  # full teeth on the palate rim, seen open
        self.assertTrue(set(by[("fang_right", "NORTH")]) <= tooth)
        nose = set(c + (255,) for c in demon_wolf.NOSE)
        self.assertTrue(set(by[("nose", "NORTH")]) <= nose)

    # -------------------------------------------------------------- clips and server timing
    def test_move_clips_follow_server_ticks(self):
        for mv in self.server["moves"]:
            name = mv["animation"]
            length, loop, chans = self.clips[name]
            self.assertFalse(loop)
            self.assertAlmostEqual(mv["total_ticks"] / 20.0, length, places=6)
            times = {round(k[0] * 20.0, 4) for _, _, frames in chans for k in frames}
            for tick in (0, mv["turn_lock_tick"], mv["lunge"]["tick"], mv["active_ticks"][0], mv["active_ticks"][1],
                         mv["total_ticks"]):
                self.assertIn(float(tick), times, f"{name}: no key pose on tick {tick}")
            self.assertTrue(all(len(frames) == len(chans[0][2]) for _, _, frames in chans))

    def test_jaw_opens_before_and_clamps_on_first_active_tick(self):
        mv = next(m for m in self.server["moves"] if m["animation"] == "bite")
        jaw = lambda tick: self.offsets("bite", tick / 20.0).get("jaw", {"rot": [0, 0, 0]})["rot"][0]  # noqa: E731
        self.assertGreater(math.degrees(jaw(mv["turn_lock_tick"])), 25.0)
        self.assertGreater(math.degrees(jaw(mv["lunge"]["tick"])), 25.0)
        self.assertLess(math.degrees(jaw(mv["active_ticks"][0])), 3.0)

    def test_non_looping_clips_start_and_end_at_rest(self):
        for name, (length, loop, chans) in self.clips.items():
            for bone, target, frames in chans:
                default = anim.DEFAULT[target]
                if loop:
                    self.assertEqual(frames[0][1], frames[-1][1], f"{name}/{bone} loop seam")
                    self.assertAlmostEqual(0.0, frames[0][0])
                    self.assertAlmostEqual(length, frames[-1][0])
                else:
                    self.assertEqual(tuple(default), tuple(frames[0][1]), f"{name}/{bone} start")
                    self.assertEqual(tuple(default), tuple(frames[-1][1]), f"{name}/{bone} end")

    def test_idle_has_no_leg_or_body_channels(self):
        _, _, chans = self.clips["idle"]
        for bone, _, _ in chans:
            self.assertFalse(bone in ("root", "body", "chest", "hip") or "_upper" in bone or "_lower" in bone
                             or bone.endswith("_paw"), bone)

    def test_no_forward_root_translation_in_moves(self):
        for clip in ("bite", "pounce", "stagger"):
            for bone, target, frames in self.clips[clip][2]:
                if bone == "root":
                    self.fail(f"{clip} animates root")
                if target == "position" and bone == "body":
                    self.assertLessEqual(max(abs(f[1][2]) for f in frames), 2.5, clip)

    # -------------------------------------------------------------- ground contact
    def test_rest_feet_on_ground(self):
        mats = anim.posed_matrices(self.model)
        feet = paw_bottom_y(self.model, mats)
        self.assertEqual(4, len(feet))
        for name, y in feet.items():
            self.assertAlmostEqual(GROUND, y, delta=1e-3, msg=name)
        lowest = max(cuboid.mat_apply(mats[b.name], tuple(c.origin[k] + c.size[k] * ((i >> k) & 1) for k in range(3)))[1]
                     for b in self.model.bones for c in b.cubes for i in range(8))
        self.assertLessEqual(lowest, GROUND + 1e-3)

    def _feet_over(self, clip, n):
        length = self.clips[clip][0]
        for i in range(n + 1):
            sec = length * i / n
            yield sec, paw_bottom_y(self.model, anim.posed_matrices(self.model, self.offsets(clip, sec)))

    def test_gaits_keep_feet_on_the_ground(self):
        for clip, tol in (("walk", 0.15), ("run", 0.25)):
            for sec, feet in self._feet_over(clip, 240):
                self.assertLessEqual(max(feet.values()), GROUND + tol, f"{clip} {sec:.3f}s foot below ground")
            if clip == "walk":  # a trot always has a foot down
                for sec, feet in self._feet_over(clip, 240):
                    self.assertGreaterEqual(max(feet.values()), GROUND - tol, f"walk {sec:.3f}s all feet up")

    def test_move_clips_keep_feet_out_of_the_ground(self):
        for clip in ("bite", "pounce", "stagger", "idle"):
            for sec, feet in self._feet_over(clip, int(self.clips[clip][0] * 80)):
                self.assertLessEqual(max(feet.values()), GROUND + 0.3, f"{clip} {sec:.3f}s foot below ground")

    def test_pounce_lands_on_the_server_landing_tick(self):
        from tools.beastgen import quadruped as quad
        mv = next(m for m in self.server["moves"] if m["animation"] == "pounce")
        heights, land = quad.jump_arc(mv["lunge"]["tick"], mv["lunge"]["up"])
        self.assertEqual(land, demon_wolf.landing_tick(mv))
        self.assertGreater(land, mv["lunge"]["tick"])
        self.assertAlmostEqual(1.057, max(heights.values()), places=2)  # apex for up 0.38 under vanilla gravity
        length, loop, chans = self.clips["pounce"]
        times = {round(k[0] * 20.0, 4) for _, _, frames in chans for k in frames}
        self.assertIn(float(land), times, "no key pose on the landing tick")
        n = int((mv["total_ticks"] - land) * 4)
        for i in range(n + 1):
            tick = land + i / 4.0
            feet = paw_bottom_y(self.model, anim.posed_matrices(self.model, self.offsets("pounce", tick / 20.0)))
            for name, y in feet.items():
                self.assertAlmostEqual(GROUND, y, delta=0.3, msg=f"pounce tick {tick}: {name} off the ground")

    def test_lunge_flight_matches_server_motion(self):
        # the same numbers as BeastMotionTest, and the server's measured pounce: lunge on move tick 18,
        # apex about 1.06, back on the ground on move tick 28
        from tools.beastgen import quadruped as quad
        self.assertAlmostEqual(1.0 / (1.0 - 0.546), quad.travel_per_unit_speed(0.0), places=3)
        self.assertAlmostEqual(1.2113, quad.lunge_flight(0, 0.55, 0.0).rest_distance, places=3)
        f = quad.lunge_flight(0, 1.0, 0.38)
        self.assertAlmostEqual(1.057, f.apex, places=3)
        self.assertAlmostEqual(4.7042, f.positions[f.landing][0], places=3)
        self.assertAlmostEqual(5.1724, f.rest_distance, places=3)
        self.assertEqual(10, f.landing)  # ten airborne ticks (the lunge tick and nine more), down on the next
        mv = next(m for m in self.server["moves"] if m["animation"] == "pounce")
        for aim in (5.0, 6.0, 7.5):
            fl = quad.move_flight(mv, aim)
            self.assertEqual(mv["lunge"]["tick"] + 10, fl.landing)
            self.assertAlmostEqual(aim - sum(mv["hit"]["forward"]) / 2.0, fl.rest_distance, places=6)
        self.assertEqual(quad.move_flight(mv).landing, demon_wolf.landing_tick(mv))
        self.assertEqual(0.0, quad.move_flight(mv).at(mv["lunge"]["tick"] - 1)[1])

    def test_pounce_absorbs_after_touch_down(self):
        mv = next(m for m in self.server["moves"] if m["animation"] == "pounce")
        land = demon_wolf.landing_tick(mv)
        body_y = lambda tick: -self.offsets("pounce", tick / 20.0)["body"]["pos"][1]  # noqa: E731 (+ up)
        self.assertLess(body_y(land + 1), body_y(land) - 1.0, "the body should keep sinking after contact")
        self.assertGreater(body_y(mv["total_ticks"] - 4), body_y(land + 1))

    def test_pounce_flight_adds_no_body_height(self):
        mv = next(m for m in self.server["moves"] if m["animation"] == "pounce")
        for bone, target, frames in self.clips["pounce"][2]:
            if bone == "body" and target == "position":
                for time, value, _ in frames:
                    if mv["lunge"]["tick"] <= time * 20.0 < demon_wolf.landing_tick(mv):
                        self.assertLessEqual(value[1], 0.25, f"body raised at tick {time * 20.0}")

    def test_bite_paws_hold_their_world_point_in_stance(self):
        # the server slides the beast forward through the bite; a planted paw must stay where it is in the
        # world (root travel plus clip), and a stepping paw must land on the point it then holds
        from tools.beastgen import quadruped as quad
        mv = next(m for m in self.server["moves"] if m["animation"] == "bite")
        flight = quad.move_flight(mv)
        self.assertGreater(flight.at(mv["total_ticks"])[0], 1.0)  # the slide this test is about
        steps = demon_wolf.bite_steps(mv)
        legs = {leg.name: leg for leg in demon_wolf.legs(self.built.model)}
        rest = quad.rest_soles(self.model, list(legs.values()))
        worst = 0.0
        for i in range(mv["total_ticks"] * 8 + 1):
            tick = i / 8.0
            mats = anim.posed_matrices(self.model, self.offsets("bite", tick / 20.0))
            travel = flight.at(tick)[0] * 16.0
            for name, leg in legs.items():
                if any(t0 < tick < t1 for t0, t1, *_ in steps[name]):
                    continue
                anchor = max([at for t0, t1, at, *_ in steps[name] if tick >= t1], default=0.0) * 16.0
                p = cuboid.mat_apply(mats[leg.paw], leg.sole)
                drift = max(abs(p[2] - travel - (rest[name][2] - anchor)), abs(p[1] - rest[name][1]))
                worst = max(worst, drift)
                self.assertLess(drift, 0.25, f"bite tick {tick}: {name} slides {drift:.3f} units in stance")
        for name in legs:  # every paw ends where the beast comes to rest
            self.assertAlmostEqual(flight.at(mv["total_ticks"])[0], steps[name][-1][2], places=6)

    # -------------------------------------------------------------- vanilla sampler port
    def test_sampler_matches_keyframe_animations(self):
        frames = [(0.0, (0.0, 0.0, 0.0), "linear"), (1.0, (10.0, 0.0, 0.0), "catmullrom"), (2.0, (0.0, 0.0, 0.0), "linear")]
        self.assertEqual((10.0, 0.0, 0.0), anim.sample_channel(frames, 1.0, 2.0, False))  # on a key: that key
        mid = anim.sample_channel(frames, 0.5, 2.0, False)[0]
        self.assertAlmostEqual(anim.catmullrom(0.5, 0.0, 0.0, 10.0, 0.0), mid)  # destination key's interp
        self.assertAlmostEqual(5.0, anim.sample_channel(frames, 1.5, 2.0, False)[0])  # linear into key 2
        self.assertEqual((0.0, 0.0, 0.0), anim.sample_channel(frames, 3.0, 2.0, False))  # past the end: last
        self.assertAlmostEqual(mid, anim.sample_channel(frames, 2.5, 2.0, True)[0])  # looping wraps
        self.assertEqual(-2.0, anim.clip_offsets([("b", "position", [(0.0, (0.0, 2.0, 0.0), "linear")])],
                                                 1.0, False, 0.0)["b"]["pos"][1])  # posVec negates y

    # -------------------------------------------------------------- determinism and --check
    def test_outputs_deterministic(self):
        again = build.Built(NAME).outputs()
        self.assertEqual(list(self.outputs), list(again))
        for p in self.outputs:
            self.assertEqual(self.outputs[p], again[p], p.name)

    def test_build_check_passes(self):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            code = build.run(NAME, check=True)
        self.assertEqual(0, code, out.getvalue())

    def test_written_json_is_what_the_generator_evaluates(self):
        self.assertEqual(self.built.model_doc, self.model_doc)
        text = self.outputs[build.paths(NAME)["model"]].decode()
        self.assertTrue(text.endswith("}\n"))
        self.assertIsNone(re.search(r"-0\.0\b", text))


if __name__ == "__main__":
    unittest.main()
