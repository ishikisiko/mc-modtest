from __future__ import annotations

import argparse
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.combat_capture import capture, cli, data, page, procs, scene, sheets, xgame
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
        self.assertEqual(set(m["source"]["files"]), {"index", "weapon", "style", "rig"})
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
        with self.assertRaises(argparse.ArgumentTypeError):
            cli.parse_views("fp,side")

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
        self.assertEqual(a.views, ["fp", "tp_back", "tp_front"])
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


if __name__ == "__main__":
    unittest.main()
