from __future__ import annotations

import argparse
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.combat_capture import capture, cli, crop, data, page, procs, scene, sheets, xgame
from tools.combat_capture.session import (CLIENT_DIR_REL, client_extra, gradle_argv, merge_options,
                                          server_properties)

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
QINGFENG = "myvillage:qingfeng_sword"


def rig_move(strike, contact, ticks, last_pose="neutral"):
    keys = [{"tick": 0, "pose": "neutral"}] + [{"tick": t} for t in ticks[:-1]]
    keys.append({"tick": ticks[-1], "pose": last_pose} if last_pose else {"tick": ticks[-1]})
    return {"strike": strike, "contact": contact, "keys": keys}


class IdResolutionTest(unittest.TestCase):
    def test_ids_map_to_bundled_paths(self):
        self.assertEqual(data.style_rel("myvillage:basic_sword"), "data/myvillage/combat/style/basic_sword.json")
        self.assertEqual(data.weapon_rel("myvillage:qingfeng_sword"),
                         "data/myvillage/combat/weapon/qingfeng_sword.json")
        self.assertEqual(data.asset_rel("myvillage:combat/qingfeng_first_person.json"),
                         "assets/myvillage/combat/qingfeng_first_person.json")
        self.assertEqual(data.style_rel("other:dir/x"), "data/other/combat/style/dir/x.json")

    def test_bad_ids_are_rejected(self):
        for bad in ("noslash", "a:b:c", ":x", "x:", "ns:../escape", "ns:/abs", None):
            with self.subTest(bad=bad):
                with self.assertRaises(data.DataError):
                    data.split_id(bad)

    def test_unlisted_weapon_is_rejected(self):
        with self.assertRaises(data.DataError):
            data.load_weapon(RESOURCES, "myvillage:not_a_weapon")


class KeyTickTest(unittest.TestCase):
    def test_key_ticks_follow_the_rig(self):
        ticks = data.key_ticks(rig_move([2.5, 4.8], 3.8, [2.0, 2.5, 3.8, 4.8, 7.5, 11]))
        self.assertEqual(ticks, [("idle", 0.0), ("strike_start", 2.5), ("contact", 3.8),
                                 ("strike_end", 4.8), ("recovery", 7.5)])
        self.assertEqual([k for k, _ in ticks], list(data.KEY_NAMES))

    def test_recovery_without_final_neutral_is_last_key(self):
        ticks = data.key_ticks(rig_move([1, 2], 1.5, [1, 2, 6], last_pose=None))
        self.assertEqual(ticks[-1], ("recovery", 6.0))

    def test_missing_fields_raise(self):
        with self.assertRaises(data.DataError):
            data.key_ticks({"keys": [{"tick": 0}]})
        with self.assertRaises(data.DataError):
            data.key_ticks({"strike": [1, 2], "contact": 1, "keys": []})

    def test_bundled_qingfeng_moves_and_ticks(self):
        w = data.load_weapon(RESOURCES, QINGFENG)
        self.assertEqual(w.style_id, "myvillage:basic_sword")
        self.assertEqual([m.index for m in w.moves], [1, 2, 3, 4, 5])
        self.assertEqual(w.moves[0].short, "01_thrust")
        style = json.loads((RESOURCES / w.files["style"]).read_text(encoding="utf-8"))
        rig = json.loads((RESOURCES / w.files["rig"]).read_text(encoding="utf-8"))
        for m, sm in zip(w.moves, style["moves"]):
            r = rig["moves"][sm["id"]]
            ticks = dict(m.keys)
            self.assertEqual(m.id, sm["id"])
            self.assertEqual(ticks["strike_start"], r["strike"][0])
            self.assertEqual(ticks["strike_end"], r["strike"][1])
            self.assertEqual(ticks["contact"], r["contact"])
            self.assertEqual(ticks["recovery"], r["keys"][-2]["tick"])
            self.assertLess(ticks["recovery"], m.total_ticks + 1e-9)

    def test_short_name(self):
        self.assertEqual(data.short_name("myvillage:basic_sword_05_lunge_thrust"), "05_lunge_thrust")
        self.assertEqual(data.short_name("ns:plain"), "plain")


def fake_weapon():
    moves = [data.Move(index=i, id=f"ns:style_0{i}_m", kind="cut", total_ticks=t, active_ticks=[a, a + 1],
                       buffer_start_tick=a, chain_tick=c,
                       keys=[("idle", 0.0), ("strike_start", 1.0), ("contact", 1.5), ("strike_end", 2.0),
                             ("recovery", 3.0)])
             for i, (t, a, c) in enumerate([(11, 3, 7), (13, 4, 8), (20, 7, 20)], start=1)]
    return data.Weapon(id="ns:w", item="ns:w_item", style_id="ns:style", rig_location="ns:combat/rig.json",
                       files={}, moves=moves, combo_timeout_ticks=14)


class ManifestTest(unittest.TestCase):
    def test_manifest_shape(self):
        w = data.load_weapon(RESOURCES, QINGFENG)
        m = capture.new_manifest("lbl", w, RESOURCES, ["fp", "tp_back"], [1, 3])
        for k in ("schema", "kind", "label", "created", "weapon", "item", "style", "rig", "source", "capture",
                  "moves", "frames", "stills_complete", "sheets", "combo", "notes"):
            self.assertIn(k, m)
        self.assertEqual(m["kind"], "capture")
        self.assertEqual([mv["index"] for mv in m["moves"]], [1, 3])
        self.assertEqual([k["key"] for k in m["moves"][0]["keys"]], list(data.KEY_NAMES))
        self.assertEqual(set(m["source"]["files"]), {"index", "weapon", "style", "rig", "geometry"})
        self.assertEqual(len(m["source"]["files"]["rig"]["sha256"]), 64)
        text = json.dumps(m)
        self.assertNotIn(str(ROOT), text, "manifest must not carry absolute host paths")
        with tempfile.TemporaryDirectory() as td:
            capture.write_manifest(Path(td), m)
            self.assertEqual(capture.load_manifest(Path(td))["label"], "lbl")

    def test_frame_file_and_f5(self):
        self.assertEqual(capture.frame_file("tp_back", 2, "contact"), "frames/tp_back/m2_contact.png")
        self.assertEqual(capture.f5_presses("first", "back"), 1)
        self.assertEqual(capture.f5_presses("first", "front"), 2)
        self.assertEqual(capture.f5_presses("front", "first"), 1)
        self.assertEqual(capture.f5_presses("back", "back"), 0)

    def test_click_schedule_hits_chain_windows(self):
        w = fake_weapon()
        clicks, total = capture.click_schedule(w.moves)
        self.assertEqual([c["move"] for c in clicks], [1, 2, 3])
        self.assertEqual(clicks[0]["tick"], 0.0)
        # move 2's click lands midway between move 1's buffer start (3) and chain tick (7)
        self.assertEqual(clicks[1]["tick"], 5.0)
        # move 2 starts at tick 7; its click is at 7 + (4 + 8) / 2
        self.assertEqual(clicks[2]["tick"], 13.0)
        self.assertAlmostEqual(clicks[2]["seconds"], 0.65)
        self.assertEqual(total, 7 + 8 + 20)
        start = 0
        for cur, click in zip(w.moves, clicks[1:]):
            self.assertGreaterEqual(click["tick"] - start, cur.buffer_start_tick)
            self.assertLess(click["tick"] - start, cur.chain_tick)
            start += cur.chain_tick

    def test_ffmpeg_argv(self):
        argv = capture.ffmpeg_record_argv(":93", Path("x.mp4"), 7.5)
        self.assertIn(":93.0+0,0", argv)
        self.assertEqual(argv[argv.index("-t") + 1], "7.5")
        self.assertIn("cfr", argv)


def simulate_body(body, yaws, limit=50.0):
    """Vanilla LivingEntity.tickHeadTurn for a standing, not swinging player:
    the body moves only by the excess over `limit` (one client tick per yaw)."""
    def wrap(a):
        a = a % 360.0
        return a - 360.0 if a >= 180.0 else a
    for yaw in yaws:
        off = wrap(yaw - body)
        if abs(off) > limit:
            body += off - (limit if off > 0 else -limit)
    return wrap(body)


class ViewTest(unittest.TestCase):
    def test_body_ends_aligned_from_any_start(self):
        for view in ("tp_back", "tp_front", "tp_back_right", "tp_back_left", "tp_front_right", "tp_front_left"):
            look = capture.VIEWS[view]["camera"]["look_yaw"]
            yaws = capture.body_align_yaws(look)
            self.assertEqual(yaws[-1], look)
            for start in range(-180, 180, 5):
                with self.subTest(view=view, start=start):
                    self.assertAlmostEqual(simulate_body(float(start), yaws), capture.BODY_YAW, places=4)

    def test_quarter_views_turn_the_head_the_full_limit(self):
        self.assertEqual(capture.VIEWS["tp_back_right"]["camera"]["look_yaw"], -50.0)  # right side is -x
        self.assertEqual(capture.VIEWS["tp_front_right"]["camera"]["look_yaw"], 50.0)
        self.assertEqual(capture.VIEWS["tp_back_left"]["camera"]["look_yaw"], 50.0)
        self.assertEqual(capture.VIEWS["tp_front_left"]["camera"]["look_yaw"], -50.0)
        with self.assertRaises(ValueError):
            capture.body_align_yaws(60.0)

    def test_straight_views_keep_their_old_camera(self):
        back, front = capture.camera_record("tp_back"), capture.camera_record("tp_front")
        self.assertEqual((back["wall_distance"], back["pitch"], back["look_yaw"]), (2.4, 30.0, 0.0))
        self.assertEqual((front["wall_distance"], front["pitch"], front["look_yaw"]), (2.4, 15.0, 0.0))
        self.assertEqual(back["player_pos"], [0.5, -60, -0.6])
        self.assertEqual(front["player_pos"], [0.5, -60, 1.6])
        q = capture.camera_record("tp_front_left")
        self.assertIsNone(q["wall_distance"])
        self.assertEqual(q["camera_distance"], 4.0)
        self.assertEqual(q["player_pos"], [0.5, -60, 0.5])

    def test_tp_command(self):
        self.assertEqual(capture.tp_command("P", -0.6000000000000001, -50.0, 30.0), "tp P 0.5 -60 -0.6 -50 30")

    def test_wall_commands(self):
        self.assertEqual(scene.wall_commands("back"), [scene.wall_fill(-4, "minecraft:barrier"),
                                                       scene.wall_fill(4, "minecraft:air")])
        self.assertEqual(scene.wall_commands(None), [scene.wall_fill(-4, "minecraft:air"),
                                                     scene.wall_fill(4, "minecraft:air")])
        with self.assertRaises(scene.SceneError):
            scene.wall_commands("side")

    def test_default_views_by_held_length(self):
        sword = data.load_weapon(RESOURCES, QINGFENG)
        self.assertAlmostEqual(sword.held_length, 24.5 * 0.8 / 16, places=3)
        self.assertEqual(capture.default_views(sword), ["fp", "tp_back", "tp_front"])
        w = fake_weapon()
        self.assertEqual(capture.default_views(w), ["fp", "tp_back", "tp_front"])  # no geometry
        w.held_length = capture.LONG_WEAPON_BLOCKS
        self.assertEqual(capture.default_views(w), list(capture.LONG_WEAPON_VIEWS))
        for v in capture.LONG_WEAPON_VIEWS:
            self.assertIn(v, capture.ALL_VIEWS)

    def test_bundled_spear_is_long_and_has_its_rig(self):
        """The spear is bundled with its final rig and geometry contract: it
        must resolve fully (rig required) and get the long-weapon views."""
        spear_id = "myvillage:lingxiao_spear"
        index = json.loads((RESOURCES / data.INDEX_REL).read_text(encoding="utf-8"))
        self.assertIn(spear_id, index["weapons"])
        spear = data.load_weapon(RESOURCES, spear_id)  # require_rig=True: a missing rig fails here
        self.assertIn("rig", spear.files)
        self.assertIn("geometry", spear.files)
        self.assertIsNotNone(spear.held_length)
        self.assertGreaterEqual(spear.held_length, capture.LONG_WEAPON_BLOCKS)
        self.assertEqual(capture.default_views(spear), list(capture.LONG_WEAPON_VIEWS))
        self.assertTrue(all(len(m.keys) == len(data.KEY_NAMES) for m in spear.moves))

    def test_held_length(self):
        g = {"overall_y": [-16.0, 32.0]}
        self.assertEqual(data.held_length_blocks(g, None), 3.0)
        self.assertEqual(data.held_length_blocks(g, {"display": {"thirdperson_righthand": {"scale": [1, 0.5, 1]}}}),
                         1.5)
        with self.assertRaises(data.DataError):
            data.held_length_blocks({}, None)
        self.assertEqual(data.model_rel("ns:item/x_3d"), "assets/ns/models/item/x_3d.json")

    def test_missing_rig_is_allowed_only_on_request(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for rel in (data.INDEX_REL, "data/myvillage/combat/weapon/qingfeng_sword.json",
                        "data/myvillage/combat/style/basic_sword.json",
                        "assets/myvillage/combat/qingfeng_sword_geometry.json"):
                (root / rel).parent.mkdir(parents=True, exist_ok=True)
                (root / rel).write_bytes((RESOURCES / rel).read_bytes())
            with self.assertRaises(data.DataError):
                data.load_weapon(root, QINGFENG)
            w = data.load_weapon(root, QINGFENG, require_rig=False)
            self.assertNotIn("rig", w.files)
            self.assertEqual(w.moves[0].keys, [])
            self.assertAlmostEqual(w.held_length, 24.5 / 16, places=3)  # no item model: scale 1

    def test_camera_mismatches(self):
        old = {"capture": {"third_person_camera": {"back": {"wall_distance": 2.4, "pitch": 30.0}}}}
        new = {"capture": {"third_person_camera": {"back": capture.camera_record("tp_back")}}}
        self.assertEqual(capture.camera_mismatches(old, new, ["fp", "tp_back"]), [])
        moved = {"capture": {"third_person_camera": {"back": dict(capture.camera_record("tp_back"), pitch=20.0)}}}
        self.assertEqual(capture.camera_mismatches(old, moved, ["tp_back"]), ["tp_back: pitch 30.0 vs 20.0"])
        turned = {"capture": {"third_person_camera": {"back": dict(capture.camera_record("tp_back"), look_yaw=-50.0)}}}
        self.assertEqual(capture.camera_mismatches(old, turned, ["tp_back"]), ["tp_back: look_yaw 0.0 vs -50.0"])

    def test_manifest_records_cameras_of_the_views(self):
        w = data.load_weapon(RESOURCES, QINGFENG)
        m = capture.new_manifest("lbl", w, RESOURCES, ["fp", "tp_back_right", "tp_front_left"], [1])
        cams = m["capture"]["third_person_camera"]
        self.assertEqual(set(cams), {"back", "front", "back_right", "front_left"})
        self.assertEqual(cams["back_right"]["look_yaw"], -50.0)
        self.assertEqual(m["capture"]["weapon_held_length_blocks"], w.held_length)
        html = page.capture_page(dict(m, sheets={}, combo=None))
        self.assertIn("tp_back_right: F5 back, vanilla F5 distance 4 blocks, pitch 30, look yaw -50", html)


class StabilityTest(unittest.TestCase):
    def test_exact_and_noise(self):
        a = bytes([100] * 30)
        self.assertEqual(xgame.settled([a, a]), ("exact", 0))
        b = bytes([101] * 15 + [98] * 15)
        self.assertEqual(xgame.settled([a, b]), (None, 2))  # noise needs three grabs
        c = bytes([99] * 30)
        self.assertEqual(xgame.settled([a, b, c]), ("noise", 2))

    def test_motion_and_drift_are_not_settled(self):
        a = bytes([100] * 30)
        moved = bytes([100] * 29 + [160])  # one edge pixel moved
        self.assertEqual(xgame.settled([a, a, moved]), (None, 60))
        drift = [bytes([100] * 30), bytes([102] * 30), bytes([104] * 30)]  # 2 per grab, 4 overall
        self.assertEqual(xgame.settled(drift), (None, 4))

    def test_max_level_diff(self):
        self.assertEqual(xgame.max_level_diff(b"\x00\x05", b"\x03\x01"), 4)
        self.assertEqual(xgame.max_level_diff(b"\x00", b"\x00\x00"), 255)


def frame(view, move, key, tick, stable=True, short="m"):
    return {"view": view, "move": move, "move_id": f"ns:style_0{move}_{short}", "move_short": f"0{move}_{short}",
            "key": key, "tick": tick, "file": capture.frame_file(view, move, key), "stable": stable}


class PairingTest(unittest.TestCase):
    def test_pairs_by_weapon_move_key_view(self):
        a = {"weapon": "ns:w", "frames": [frame("fp", 1, "idle", 0), frame("fp", 1, "contact", 3.8),
                                          frame("tp_back", 1, "idle", 0)]}
        b = {"weapon": "ns:w", "frames": [frame("fp", 1, "contact", 4.0), frame("fp", 1, "idle", 0),
                                          frame("tp_front", 1, "idle", 0)]}
        p = sheets.pair_frames(a, b)
        self.assertEqual([(e["view"], e["key"]) for e in p["pairs"]], [("fp", "idle"), ("fp", "contact")])
        self.assertEqual(p["pairs"][1]["a"]["tick"], 3.8)
        self.assertEqual(p["pairs"][1]["b"]["tick"], 4.0)
        self.assertEqual([(e["view"], e["key"]) for e in p["only_a"]], [("tp_back", "idle")])
        self.assertEqual([(e["view"], e["key"]) for e in p["only_b"]], [("tp_front", "idle")])
        self.assertIsNone(p["only_a"][0]["b"])

    def test_different_weapons_do_not_pair(self):
        a = {"weapon": "ns:a", "frames": [frame("fp", 1, "idle", 0)]}
        b = {"weapon": "ns:b", "frames": [frame("fp", 1, "idle", 0)]}
        p = sheets.pair_frames(a, b)
        self.assertEqual((len(p["pairs"]), len(p["only_a"]), len(p["only_b"])), (0, 1, 1))

    def test_cell_label_names_weapon_move_key_tick_view(self):
        text = sheets.cell_label("ns:w", frame("tp_front", 2, "strike_end", 6.25), "tp_front")
        for part in ("ns:w", "m2", "02_m", "strike_end", "t=6.25", "tp_front"):
            self.assertIn(part, text)
        self.assertIn("UNSTABLE", sheets.cell_label("ns:w", frame("fp", 1, "idle", 0, stable=False), "fp"))


def capture_manifest(label="before"):
    w = fake_weapon()
    m = {"schema": 1, "kind": "capture", "label": label, "created": "2026-10-02T10:00:00+08:00", "weapon": w.id,
         "item": w.item, "style": w.style_id, "rig": w.rig_location,
         "source": {"head": "0123456789abcdef", "describe": "0123456 subject <b>", "dirty": False,
                    "mod_version": "0.27.1-fix1", "files": {}},
         "capture": {"size": [960, 540], "fov": 70, "views": ["fp", "tp_back"]},
         "moves": [{"index": mv.index, "id": mv.id, "short": mv.short, "kind": mv.kind,
                    "total_ticks": mv.total_ticks, "keys": [{"key": k, "tick": t} for k, t in mv.keys]}
                   for mv in w.moves],
         "frames": [frame("fp", 1, "idle", 0), frame("fp", 1, "contact", 1.5, stable=False)],
         "stills_complete": True, "sheets": {"fp": "sheet_fp.png"}, "notes": [],
         "combo": {"targets": [{"tag": "t1", "kind": "dummy", "pos": [0.5, -60, 3.0], "health_before": 80.0,
                                "health_after": 41.5, "damage": 38.5}],
                   "total_damage": 38.5, "clicks_planned": [{"move": 1, "tick": 0.0, "seconds": 0.0}],
                   "clicks_sent_s": [0.0], "expected_combo_ticks": 35, "video": "combo/combo.mp4",
                   "video_seconds": 7.5, "lead_seconds": 1.5, "combat_mode": "cultivation",
                   "player_pos_before": "[0.5d, -60.0d, 0.5d]", "player_pos_after": "[0.5d, -60.0d, 1.3d]",
                   "client_animation_starts": [{"animation": "ns:style_01_m", "elapsed_ticks": "0",
                                                "accepted": True, "kept_prediction": False}]}}
    return m


VERDICT_WORDS = ("pass", "fail", "accepted", "approved", "verdict: ", "looks good", "owner said", "sign-off")


class PageTest(unittest.TestCase):
    def assert_plain(self, html):
        low = html.lower()
        for w in VERDICT_WORDS:
            self.assertNotIn(w, low.replace("does not record any review outcome", ""), w)
        self.assertNotIn("/home/", html)
        self.assertNotIn("rcon_password", html)
        self.assertNotIn("<b>", html)  # escaped

    def test_capture_page(self):
        html = page.capture_page(capture_manifest())
        self.assertTrue(html.startswith("<!doctype html>"))
        self.assertIn("sheet_fp.png", html)
        self.assertIn("combo/combo.mp4", html)
        self.assertIn("80.0", html)
        self.assertIn("41.5", html)
        self.assertIn("1 not stable", html)
        self.assertIn("No frames for this view", html)  # tp_back has no sheet
        self.assert_plain(html)

    def test_capture_page_with_back_combo_and_motion(self):
        m = capture_manifest()
        m["combo"]["camera"] = capture.combo_camera_record("back", None)
        m["motion"] = [{"view": "tp_back_right", "video": "motion/tp_back_right.mp4", "seconds": 12.0,
                        "camera": capture.camera_record("tp_back_right"),
                        "events": [{"event": "move 1 01_m", "s": 2.5}], "client_animation_starts": []}]
        html = page.capture_page(m)
        self.assertIn("F5 back at 4.0 blocks, pitch 30.0", html)
        self.assertIn("motion/tp_back_right.mp4", html)
        self.assertIn("move 1 01_m at 2.5s", html)
        self.assert_plain(html)

    def test_capture_page_without_combo(self):
        m = capture_manifest()
        m["combo"] = None
        self.assertIn("No combo run", page.capture_page(m))

    def test_comparison_page(self):
        a, b = capture_manifest("before"), capture_manifest("after")
        cmp = {"label": "before-vs-after", "a": a, "b": b, "counts": {"pairs": 1, "only_a": 1, "only_b": 0},
               "unpaired": [{"view": "fp", "move_id": "ns:style_01_m", "key": "contact", "in": "A (before)"}],
               "sheets": {"fp": "compare_fp.png"}, "links": [("A: before", "../before/index.html")]}
        html = page.comparison_page(cmp)
        self.assertIn("compare_fp.png", html)
        self.assertIn("Present in one set only", html)
        self.assertIn("a/combo/combo.mp4", html)
        self.assertIn("b/combo/combo.mp4", html)
        self.assert_plain(html)


class ArgumentTest(unittest.TestCase):
    def test_labels(self):
        self.assertEqual(cli.check_label("before-0.27.1-fix1"), "before-0.27.1-fix1")
        for bad in ("", "../x", "a b", "a/b", ".hidden", "x" * 81):
            with self.subTest(bad=bad):
                with self.assertRaises(argparse.ArgumentTypeError):
                    cli.check_label(bad)

    def test_views(self):
        self.assertEqual(cli.parse_views("tp_front,fp"), ["fp", "tp_front"])
        self.assertEqual(cli.parse_views("tp_front_left,tp_back_right,fp"), ["fp", "tp_back_right", "tp_front_left"])
        with self.assertRaises(argparse.ArgumentTypeError):
            cli.parse_views("fp,side")

    def test_view_and_shot_commands_parse(self):
        a = cli.build_parser().parse_args(["view", "tp_back_right"])
        self.assertIs(a.func, cli.cmd_view)
        a = cli.build_parser().parse_args(["shot", "out/x.png", "--max-wait", "2"])
        self.assertIs(a.func, cli.cmd_shot)
        self.assertEqual(a.max_wait, 2.0)
        with self.assertRaises(SystemExit):
            with mock.patch("sys.stderr"):
                cli.build_parser().parse_args(["view", "tp_side"])

    def test_shot_refuses_while_a_screen_is_open(self):
        with tempfile.TemporaryDirectory() as td:
            g = FakeGame(Path(td) / "latest.log", [False] * 100)
            with mock.patch.object(g, "wait_ingame", return_value=False), \
                    mock.patch.object(g, "grab_raw") as grab:
                with self.assertRaises(xgame.GameInputError):
                    g.shot(Path(td) / "x.png")
                grab.assert_not_called()

    def test_combo_camera_and_motion_parse(self):
        a = cli.build_parser().parse_args(["combo", "--label", "x", "--camera", "back", "--pitch", "20"])
        self.assertEqual((a.camera, a.pitch), ("back", 20.0))
        self.assertEqual(cli.build_parser().parse_args(["combo", "--label", "x"]).camera, "first")
        self.assertEqual(capture.combo_camera_record("back", 20.0)["pitch"], 20.0)
        self.assertEqual(capture.combo_camera_record("first", None)["f5"], "first")
        with self.assertRaises(ValueError):
            capture.combo_camera_record("back_right", None)
        a = cli.build_parser().parse_args(["motion", "--label", "x", "--views", "tp_back_right,tp_front_left",
                                           "--enter", "--idle", "3"])
        self.assertIs(a.func, cli.cmd_motion)
        self.assertEqual((a.views, a.enter, a.idle), (["tp_back_right", "tp_front_left"], True, 3.0))

    def test_moves(self):
        self.assertEqual(cli.parse_moves(None, [1, 2, 3]), [1, 2, 3])
        self.assertEqual(cli.parse_moves("3,1,1", [1, 2, 3]), [1, 3])
        with self.assertRaises(cli.UsageError):
            cli.parse_moves("4", [1, 2, 3])
        with self.assertRaises(cli.UsageError):
            cli.parse_moves("x", [1, 2, 3])

    def test_run_parser_defaults(self):
        a = cli.build_parser().parse_args(["run", "--label", "x"])
        self.assertEqual(a.weapon, QINGFENG)
        self.assertIsNone(a.views)  # resolved from the weapon's held length
        self.assertEqual(cli.resolve_views(a, data.load_weapon(RESOURCES, QINGFENG)), ["fp", "tp_back", "tp_front"])
        self.assertEqual(a.targets, "dummy")
        self.assertIs(a.func, cli.cmd_run)
        with self.assertRaises(SystemExit):
            with mock.patch("sys.stderr"):
                cli.build_parser().parse_args(["run"])  # --label is required

    def test_missing_program_stops_before_any_process(self):
        a = cli.build_parser().parse_args(["run", "--label", "x", "--out-root", "/nonexistent-root"])
        with mock.patch.object(procs.shutil, "which", side_effect=lambda n: None if n == "xdotool" else "/bin/x"), \
                mock.patch("tools.combat_capture.session.start") as start, \
                mock.patch("subprocess.Popen") as popen:
            with self.assertRaises(cli.UsageError) as ctx:
                cli.cmd_run(a)
            start.assert_not_called()
            popen.assert_not_called()
        self.assertIn("xdotool", str(ctx.exception))

    def test_main_reports_missing_program(self):
        with mock.patch.object(procs, "missing_programs", return_value=["Xvfb"]), \
                mock.patch("sys.stderr") as err:
            rc = cli.main(["session", "start"])
        self.assertEqual(rc, 2)
        self.assertIn("Xvfb", "".join(str(c) for c in err.write.call_args_list))


class SessionPureTest(unittest.TestCase):
    def test_gradle_argv_uses_repository_run_tasks(self):
        server = gradle_argv("runAcceptanceServer", "-Xmx1g")
        self.assertEqual(server[0], "./gradlew")
        self.assertIn("runAcceptanceServer", server)
        self.assertIn("generateAllStructures", server)  # excluded with -x
        client = gradle_argv("runClient", "-Xmx1g", client_extra(25611, "CaptureDev"))
        self.assertIn("-Pcombat_smoke_server=127.0.0.1:25611", client)
        self.assertIn(f"-Pcombat_smoke_game_dir={CLIENT_DIR_REL}", client)
        self.assertIn("-Pcombat_smoke_username=CaptureDev", client)

    def test_server_properties(self):
        props = server_properties(25611, 25612, "pw")
        self.assertEqual((props["server-port"], props["rcon.port"], props["enable-rcon"]), ("25611", "25612", "true"))
        self.assertEqual(props["broadcast-rcon-to-ops"], "false")
        self.assertNotEqual(props["difficulty"], "peaceful")  # husk targets despawn on peaceful

    def test_merge_options(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "options.txt"
            p.write_text("fov:0.5\nkeep:1\n", encoding="utf-8")
            merge_options(p, {"fov": "0.0", "renderClouds": '"false"'})
            self.assertEqual(p.read_text(encoding="utf-8").splitlines(), ["fov:0.0", "keep:1", 'renderClouds:"false"'])

    def test_free_ports_skip_reserved_and_busy(self):
        ports = procs.free_ports(2, start=8764, stop=8800, check=lambda p: p != 8766)
        self.assertEqual(ports, [8764, 8767])
        with self.assertRaises(RuntimeError):
            procs.free_ports(1, start=1, stop=3, check=lambda p: False)

    def test_free_display(self):
        self.assertEqual(procs.free_display(90, 95, check=lambda n: n == 92), 92)

    def test_missing_programs(self):
        self.assertEqual(procs.missing_programs(("a", "b"), which=lambda n: None if n == "b" else "/x"), ["b"])

    def test_process_identity(self):
        import os
        me = os.getpid()
        self.assertTrue(procs.is_alive(me, procs.start_time(me)))
        self.assertFalse(procs.is_alive(me, procs.start_time(me) + 1))
        self.assertNotIn(me, procs.descendants(me))


class SceneTest(unittest.TestCase):
    def test_targets_and_parsing(self):
        cmds = scene.target_summons("dummy")
        self.assertEqual(len(cmds), 3)
        self.assertTrue(all(c.startswith("summon minecraft:husk") for c in cmds))
        self.assertIn("generic.knockback_resistance", cmds[0])
        self.assertTrue(all("iron_golem" in c for c in scene.target_summons("golem")))
        with self.assertRaises(scene.SceneError):
            scene.target_summons("zombie")
        self.assertEqual(scene.parse_float_data("Husk has the following entity data: 41.5f"), 41.5)
        self.assertIsNone(scene.parse_float_data("No entity was found"))
        self.assertEqual(scene.ints_to_uuid([0, 0, 0, 1]), "00000000-0000-0000-0000-000000000001")
        self.assertEqual(scene.parse_combat_mode('{"myvillage:combat_preference": {combat_mode: "cultivation"}}'),
                         "cultivation")
        self.assertEqual(scene.parse_combat_mode("Player has the following entity data: {}"), "vanilla")

    def test_scene_commands(self):
        setup = scene.player_setup("P", "ns:item")
        self.assertIn("item replace entity P weapon.mainhand with ns:item", setup)
        self.assertIn("gamemode survival P", setup)
        self.assertIn("experience set P 0 points", setup)  # the XP bar is part of every frame
        # regeneration bounces the heart row, which keeps frames from settling
        self.assertFalse(any("regeneration" in c for c in setup))
        rules = scene.world_rules()
        self.assertIn("time set 6000", rules)
        self.assertTrue(any(r.startswith("weather clear") for r in rules))
        self.assertEqual(scene.wall_fill(-4, "minecraft:barrier"), "fill -4 -60 -4 5 -54 -4 minecraft:barrier")


class FakeGame(xgame.Game):
    """Game whose X side is scripted: grabbed is a list of pointer states."""

    def __init__(self, log_path, grabbed, confirm_on_enter=True):
        super().__init__(":0", log_path, log=lambda m: None)
        self.grabbed = list(grabbed)
        self.sent = []
        self.confirm_on_enter = confirm_on_enter

    def pointer_grabbed(self):
        return self.grabbed.pop(0) if self.grabbed else True

    def xdo(self, *args):
        self.sent.append(args)
        if args[:2] == ("key", "Return") and self.confirm_on_enter:
            with open(self.client_log, "a") as f:
                f.write("[x] PAL_SMOKE first_person move=1 tick=0.0\n")
        return ""


class ChatRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.td = tempfile.TemporaryDirectory()
        self.log = Path(self.td.name) / "latest.log"
        self.log.write_text("")
        self.sleep = mock.patch("tools.combat_capture.xgame.time.sleep").start()
        self.addCleanup(mock.patch.stopall)
        self.addCleanup(self.td.cleanup)

    def test_lost_grab_is_repaired_with_a_middle_click(self):
        # in game before typing, then never re-grabbed until the middle click
        g = FakeGame(self.log, [True] + [False] * 200 + [True])
        with mock.patch.object(g, "wait_ingame", side_effect=[True, False]):
            m, _ = g.chat("/myvillage_pal_smoke first_person 1 0", confirm=r"PAL_SMOKE first_person move=1")
        self.assertIsNotNone(m)
        self.assertIn(("click", "2"), g.sent)
        self.assertNotIn(("key", "Escape"), g.sent)

    def test_missing_confirmation_is_retried_once(self):
        g = FakeGame(self.log, [], confirm_on_enter=False)
        with mock.patch.object(xgame.LogTail, "wait", return_value=(None, None)):
            with self.assertRaises(xgame.GameInputError):
                g.chat("/x", confirm="never", timeout=0.01)
        self.assertEqual(sum(1 for s in g.sent if s[:2] == ("key", "Return")), 2)

    def test_probe_rejection_fails_fast_with_the_reason(self):
        g = FakeGame(self.log, [], confirm_on_enter=False)
        line = ("[02Oct2026 08:39:35.763] [Render thread/INFO] [x/]: PAL_SMOKE third_person rejected "
                "reason=tick_out_of_range move=1 tick=12.0 total_ticks=12\n")
        orig = g.xdo

        def xdo(*args):
            if args[:2] == ("key", "Return"):
                with open(self.log, "a") as f:
                    f.write(line)
            return orig(*args)
        g.xdo = xdo
        with self.assertRaises(xgame.ProbeRejected) as ctx:
            g.chat("/myvillage_pal_smoke third_person 1 12", confirm=r"PAL_SMOKE third_person move=1 tick=(\S+)",
                   reject=capture.probe_reject_pattern("third_person"), timeout=30)
        self.assertEqual(ctx.exception.reason, "tick_out_of_range")
        self.assertIn("tick_out_of_range", str(ctx.exception))
        self.assertEqual(sum(1 for s in g.sent if s[:2] == ("key", "Return")), 1)  # no retry

    def test_reject_patterns(self):
        import re
        for probe in ("first_person", "third_person", "move"):
            m = re.search(capture.probe_reject_pattern(probe),
                          f"PAL_SMOKE {probe} rejected reason=no_weapon")
            self.assertEqual(m.group(1), "no_weapon")
        self.assertIsNone(re.search(capture.probe_reject_pattern("first_person"),
                                    "PAL_SMOKE third_person rejected reason=no_weapon"))
        self.assertIsNone(re.search(capture.probe_reject_pattern("third_person"),
                                    "PAL_SMOKE third_person release held=false"))

    def test_no_keys_when_a_screen_is_open(self):
        g = FakeGame(self.log, [False] * 1000)
        with mock.patch.object(g, "wait_ingame", return_value=False):
            with self.assertRaises(xgame.GameInputError):
                g.key("r")
        self.assertEqual(g.sent, [])


class ReloadTest(unittest.TestCase):
    def test_changed_resources(self):
        with tempfile.TemporaryDirectory() as td:
            src, dst = Path(td) / "src", Path(td) / "dst"
            for root in (src, dst):
                (root / "assets/ns/combat").mkdir(parents=True)
            (src / "assets/ns/combat/rig.json").write_text("{\"a\": 1}")
            (dst / "assets/ns/combat/rig.json").write_text("{\"a\": 0}")
            (src / "assets/ns/combat/same.json").write_text("{}")
            (dst / "assets/ns/combat/same.json").write_text("{}")
            (src / "assets/ns/combat/new.json").write_text("{}")
            self.assertEqual(cli.changed_resources(src, dst, ("assets/*/combat/**/*.json",)),
                             ["assets/ns/combat/new.json", "assets/ns/combat/rig.json"])


class CropTest(unittest.TestCase):
    QUARTER = {"view": "tp_back_right", "f5": "back", "wall_distance": None, "camera_distance": 4.0,
               "pitch": 30.0, "look_yaw": -50.0, "body_yaw": 0.0}

    def manifest(self, views):
        return {"capture": {"size": [960, 540], "fov": 70, "views": views,
                            "third_person_camera": {capture.camera_key(v): capture.camera_record(v)
                                                    for v in views if v != "fp"}}, "frames": []}

    def test_croppable_views(self):
        m = self.manifest(["fp", "tp_back", "tp_front", "tp_back_right", "tp_front_left"])
        self.assertEqual([v for v in m["capture"]["views"] if crop.croppable(m, v)],
                         ["tp_back_right", "tp_front_left"])  # wall views and fp keep full frames

    def test_projection(self):
        for view in ("tp_back_right", "tp_back_left", "tp_front_right", "tp_front_left"):
            cam = capture.camera_record(view)
            x, y = crop.project((0.0, 0.0, 0.0), cam)
            self.assertAlmostEqual(x, 480.0, places=6)  # the eye is the screen centre
            self.assertAlmostEqual(y, 270.0, places=6)
            fx, fy = crop.project((0.0, -crop.EYE_HEIGHT, 0.0), cam)
            self.assertGreater(fy, 330.0)  # feet below the eye
            self.assertAlmostEqual(fx, 480.0, places=6)
        # behind the right shoulder: a point to the player's right (-x) is on the right of the screen
        rx, _ = crop.project((-1.0, 0.0, 0.0), capture.camera_record("tp_back_right"))
        self.assertGreater(rx, 480.0)
        # front: the player's right appears on the left
        lx, _ = crop.project((-1.0, 0.0, 0.0), capture.camera_record("tp_front"))
        self.assertLess(lx, 480.0)
        box = crop.body_box(self.QUARTER)
        self.assertTrue(box[0] < 480 < box[2] and box[1] < 270 < box[3])

    def test_boxes(self):
        self.assertEqual(crop.parse_bbox("164x180+414+244"), (414, 244, 578, 424))
        self.assertIsNone(crop.parse_bbox("0x0+960+540"))
        self.assertIsNone(crop.parse_bbox(""))
        self.assertEqual(crop.union([(1, 2, 3, 4), None, (0, 5, 2, 9)]), (0, 2, 3, 9))
        self.assertIsNone(crop.union([None]))
        self.assertEqual(crop.finish((100, 100, 300, 300), margin=10), [90, 90, 220, 220])
        # minimum size grows about the centre; clamped to the frame
        self.assertEqual(crop.finish((480, 270, 490, 280), margin=0, min_size=(160, 120)), [405, 215, 160, 120])
        self.assertEqual(crop.finish((0, 500, 50, 540), margin=16, min_size=(160, 120)), [0, 420, 160, 120])
        self.assertEqual(crop.finish((-50, -50, 2000, 2000)), [0, 0, 960, 540])
        self.assertEqual(crop.box_union_xywh([10, 10, 100, 50], [0, 20, 50, 100]), [0, 10, 110, 110])
        self.assertEqual(crop.box_union_xywh(None, [1, 2, 3, 4]), [1, 2, 3, 4])
        self.assertEqual(crop.zoom_for([0, 0, 319, 256], 720), 2)
        self.assertEqual(crop.zoom_for([0, 0, 533, 280], 720), 1)
        self.assertEqual(crop.zoom_for([0, 0, 160, 120], 720), 3)
        self.assertEqual(crop.crop_spec("a/b.png", [1, 2, 3, 4]), "a/b.png[3x4+1+2]")

    def test_view_crop_needs_frames(self):
        m = self.manifest(["tp_back_right"])
        m["frames"] = [{"view": "tp_back_right", "file": "frames/tp_back_right/m1_idle.png"}]
        with tempfile.TemporaryDirectory() as td:
            self.assertIsNone(crop.view_crop(Path(td), m, "tp_back_right"))  # missing files / too few frames
            self.assertIsNone(crop.view_crop(Path(td), m, "fp"))

    def test_compare_uses_one_box_for_both_sides(self):
        m = self.manifest(["tp_back_right", "tp_back"])
        with mock.patch.object(crop, "view_crop", side_effect=[{"box": [10, 10, 100, 100]},
                                                               {"box": [50, 0, 100, 100]}]):
            self.assertEqual(cli.compare_crop(Path("a"), m, Path("b"), m, "tp_back_right"), [10, 0, 140, 110])
        with mock.patch.object(crop, "view_crop") as vc:
            self.assertIsNone(cli.compare_crop(Path("a"), m, Path("b"), m, "tp_back"))
            vc.assert_not_called()


class ComboRunTest(unittest.TestCase):
    def test_layouts(self):
        import math
        self.assertEqual(scene.TARGET_LAYOUT, scene.TARGET_LAYOUTS["default"])
        for name, tl in scene.TARGET_LAYOUTS.items():
            self.assertEqual([t for t, _, _ in tl], ["t1", "t2", "t3"])
            self.assertEqual(len(scene.target_summons("dummy", name)), 3)
        sweep = {t: (dx, dz) for t, dx, dz in scene.TARGET_LAYOUTS["sweep"]}
        for t in ("t1", "t2", "t3"):
            self.assertAlmostEqual(math.hypot(*sweep[t]), 2.5, places=2)
        self.assertAlmostEqual(math.degrees(math.atan2(sweep["t2"][0], sweep["t2"][1])), 45.0, places=1)
        self.assertAlmostEqual(math.degrees(math.atan2(sweep["t3"][0], sweep["t3"][1])), -45.0, places=1)
        self.assertTrue(all(dx == 0.0 for _, dx, _ in scene.TARGET_LAYOUTS["line"]))
        with self.assertRaises(scene.SceneError):
            scene.target_summons("dummy", "circle")

    def test_bundled_spear_reaches_the_layouts(self):
        """The spear's sweep samples pass within reach of every sweep-layout
        target and the lunge's range covers the whole line (static check of
        the placement, not of the game)."""
        import math
        style = json.loads((RESOURCES / "data/myvillage/combat/style/basic_spear.json").read_text())
        moves = {m["id"].split(":")[1]: m for m in style["moves"]}

        def seg_dist(p, a, b):
            ax, az, bx, bz = a[0], a[2], b[0], b[2]
            vx, vz = bx - ax, bz - az
            k = max(0.0, min(1.0, ((p[0] - ax) * vx + (p[1] - az) * vz) / (vx * vx + vz * vz)))
            return math.hypot(p[0] - ax - k * vx, p[1] - az - k * vz)
        sweep = moves["basic_spear_02_sweep"]["hitbox"]["samples"]
        for tag, dx, dz in scene.TARGET_LAYOUTS["sweep"]:
            best = min(seg_dist((dx, dz), smp["start"], smp["end"]) for smp in sweep)
            self.assertLess(best, 0.3 + sweep[0]["horizontal_radius"], tag)
        lunge = moves["basic_spear_05_dragon_lunge"]
        self.assertGreaterEqual(lunge["hitbox"]["samples"]["first_range"],
                                max(dz for _, _, dz in scene.TARGET_LAYOUTS["line"]) - 0.3)
        self.assertGreaterEqual(lunge["maximum_targets"], 3)

    def test_slow_click_schedule_scales(self):
        w = data.load_weapon(RESOURCES, QINGFENG)
        normal, ticks = capture.click_schedule(w.moves)
        slow, ticks4 = capture.click_schedule(w.moves, 0.2)
        self.assertEqual(ticks, ticks4)
        for a, b in zip(normal, slow):
            self.assertEqual(a["tick"], b["tick"])
            self.assertAlmostEqual(b["seconds"], a["seconds"] * 4, places=2)

    def test_hit_attribution(self):
        starts = capture.move_starts([(10.0, "ns:a"), (10.1, "ns:a"), (10.5, "ns:b"), (10.52, "ns:b"),
                                      (11.2, "ns:a")], merge_seconds=0.6)
        self.assertEqual([(st["t"], st["animation"]) for st in starts], [(10.0, "ns:a"), (10.5, "ns:b"), (11.2, "ns:a")])
        samples = [(9.9, "t1", 80.0), (9.9, "t2", 80.0), (10.3, "t1", 72.0), (10.3, "t2", 80.0),
                   (10.7, "t1", 64.0), (10.7, "t2", 75.5), (10.8, "t2", None), (11.4, "t2", 70.0)]
        drops = capture.health_drops(samples)
        self.assertEqual([(d["tag"], d["damage"]) for d in drops], [("t1", 8.0), ("t1", 8.0), ("t2", 4.5), ("t2", 5.5)])
        Mv = data.Move
        moves = [Mv(1, "ns:a", "cut", 10, [3, 4], 4, 8), Mv(2, "ns:b", "cut", 10, [3, 4], 4, 8)]
        hits = capture.attribute_hits(drops, starts, moves)
        self.assertEqual([h["move"] for h in hits], [1, 2, 2, 1])
        self.assertEqual(capture.hits_by_move(hits), {"m1": {"t1": 8.0, "t2": 5.5}, "m2": {"t1": 8.0, "t2": 4.5}})
        early = capture.attribute_hits([{"tag": "t1", "t": 1.0, "before": 2, "after": 1, "damage": 1}], starts)
        self.assertIsNone(early[0]["animation"])
        self.assertEqual(capture.hits_by_move(early), {"before any move": {"t1": 1}})

    def test_fp_log_pattern(self):
        m = capture.FP_LINE.search("[x] [Render thread/INFO] [y/]: PAL_SMOKE fp_hit_stop animation=ns:a "
                                   "tick_reading=6.2 present=5.9 contact=6.0 start=12")
        self.assertEqual(m.group(1), "fp_hit_stop")
        self.assertTrue(capture.FP_LINE.search("PAL_SMOKE fp_resync animation=ns:a shift_ticks=-0.4 slewed=true"))

    def test_combo_options_parse_and_page(self):
        a = cli.build_parser().parse_args(["combo", "--label", "x", "--layout", "sweep", "--tick-rate", "5"])
        self.assertEqual((a.layout, a.tick_rate), ("sweep", 5))
        with self.assertRaises(SystemExit):
            with mock.patch("sys.stderr"):
                cli.build_parser().parse_args(["combo", "--label", "x", "--layout", "circle"])
        m = capture_manifest()
        m["combo"].update({"layout": "sweep", "layout_offsets": [["t2", 1.768, 1.768]], "tick_rate": 5,
                           "hits_by_move": {"m2": {"t1": 7.5, "t2": 7.5}}, "fp_log": [{"t": 1, "kind": "fp_hit_stop"}]})
        html = page.capture_page(m)
        self.assertIn("Tick rate during the run: 5", html)
        self.assertIn("t2 -7.5", html)
        self.assertIn("1 first-person hit-stop/resync log lines", html)
        PageTest.assert_plain(self, html)


class RigRecordTest(unittest.TestCase):
    def test_off_hand_is_read_from_the_loaded_file(self):
        with tempfile.TemporaryDirectory() as td:
            build, src = Path(td) / "build", Path(td) / "src"
            rel = "assets/ns/combat/rig.json"
            for root, rig in ((build, {"arm": {}}), (src, {"arm": {}, "off_hand": {"grip_diagonal": 30}})):
                (root / rel).parent.mkdir(parents=True)
                (root / rel).write_text(json.dumps({"rig": rig, "moves": {}}))
            r = capture.rig_record(build, rel, src)
            self.assertFalse(r["off_hand"])
            self.assertFalse(r["same_as_source"])
            r2 = capture.rig_record(src, rel, src)
            self.assertTrue(r2["off_hand"])
            self.assertTrue(r2["same_as_source"])
            self.assertFalse(capture.rig_record(build, "missing.json")["present"])

    def test_bundled_rigs(self):
        spear = capture.rig_record(RESOURCES, "assets/myvillage/combat/lingxiao_spear_first_person.json")
        sword = capture.rig_record(RESOURCES, "assets/myvillage/combat/qingfeng_first_person.json")
        self.assertTrue(spear["off_hand"])
        self.assertFalse(sword["off_hand"])

    def test_page_shows_the_loaded_rig_and_crops(self):
        m = capture_manifest()
        html = page.capture_page(m)
        self.assertIn("loaded rig: not recorded", html)
        m["rig_loaded"] = {"path": "x", "read_from": "build/resources/main", "present": True, "sha256": "ab" * 32,
                           "off_hand": True, "same_as_source": False}
        m["crops"] = {"fp": {"box": [1, 2, 300, 200], "rule": "test rule", "frames": 2}}
        html = page.capture_page(m)
        self.assertIn("declares an off hand", html)
        self.assertIn("differs from the source tree", html)
        self.assertIn("300x200 at (1, 2)", html)
        PageTest.assert_plain(self, html)


if __name__ == "__main__":
    unittest.main()
