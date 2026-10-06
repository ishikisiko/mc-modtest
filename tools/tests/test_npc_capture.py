import json
import tempfile
import unittest
from pathlib import Path

from tools.combat_capture import beast, cli, npc


class FakeGame:
    def __init__(self):
        self.shots = []

    def key(self, name):
        pass

    def shot(self, path, max_wait=6.0):
        self.shots.append(path.name)
        return {"stable": True}


class FakeSession:
    username = "CaptureDev"
    view = "first"
    state = {"display": ":99"}

    def __init__(self):
        self.game = FakeGame()
        self.commands = []

    def run(self, *cmds, warn=True):
        self.commands.extend(cmds)
        return ["CaptureDev has the following entity data: [0.5d, -60.0d, 8.5d]"]


class NpcCaptureTest(unittest.TestCase):
    def test_close_ups_use_known_views_and_look_at_the_figure(self):
        names = [c[0] for c in npc.CLOSEUPS]
        self.assertEqual(len(names), len(set(names)))
        for name, title, view, distance, eye_up, aim_up in npc.CLOSEUPS:
            self.assertIn(view, beast.VIEW_OFFSETS, name)
            self.assertTrue(0.8 <= distance <= 2.5, name)
            self.assertTrue(0.0 <= aim_up <= 2.0 and 0.0 <= eye_up <= 2.2, name)

    def test_needs_no_beast_data_and_never_uses_beast_commands(self):
        session = FakeSession()
        capture = npc.NpcCapture(session, "myvillage:cultivator", out=None)
        self.assertEqual({}, capture.data)
        self.assertEqual("myvillage:cultivator", capture.manifest["beast"])
        capture.setup_world()
        self.assertFalse([c for c in session.commands if "myvillage beast" in c])
        self.assertIn("summon myvillage:cultivator", beast.summon(capture.beast, beast.WOLF_POS, 0.0))

    def test_cli_has_the_npc_command(self):
        args = cli.build_parser().parse_args(["npc", "--parts", "idle"])
        self.assertEqual("myvillage:cultivator", args.npc)
        self.assertEqual("idle", args.parts)
        self.assertEqual(("idle", "walk"), npc.NPC_PARTS)

    def test_cli_parses_look_and_defaults_to_default(self):
        self.assertEqual("default", cli.build_parser().parse_args(["npc"]).look)
        args = cli.build_parser().parse_args(["npc", "--look", "f_novice", "--parts", "idle"])
        self.assertEqual("f_novice", args.look)
        self.assertEqual("idle", args.parts)
        self.assertEqual(("default", "f_novice", "f_adept"), npc.LOOKS)

    def test_cli_refuses_an_unknown_look_before_starting_a_session(self):
        args = cli.build_parser().parse_args(["npc", "--look", "f_nonsense"])
        orig = cli.require_programs
        cli.require_programs = lambda: None
        try:
            with self.assertRaises(cli.UsageError):
                cli.cmd_npc(args)
        finally:
            cli.require_programs = orig

    def test_default_output_dir_is_unchanged_and_a_look_gets_its_own(self):
        self.assertEqual("ingame", npc.ingame_dir_name())
        self.assertEqual("ingame", npc.ingame_dir_name("default"))
        self.assertEqual("ingame_f_novice", npc.ingame_dir_name("f_novice"))
        self.assertEqual("ingame_f_adept", npc.ingame_dir_name("f_adept"))

    def test_default_summon_is_byte_identical_and_a_look_adds_the_tag(self):
        plain = beast.summon("myvillage:cultivator", beast.WOLF_POS, 0.0)
        self.assertEqual(plain, npc.summon_npc("myvillage:cultivator", beast.WOLF_POS, 0.0))
        self.assertNotIn("Look", plain)
        cmd = npc.summon_npc("myvillage:cultivator", (0.5, -60, 8.5), 90.0, tag="npc_1", look="f_adept")
        self.assertTrue(cmd.startswith("summon myvillage:cultivator 0.5 -60 8.5 {"), cmd)
        self.assertTrue(cmd.endswith(',Look:"f_adept"}'), cmd)
        self.assertIn('Tags:["npc_1"]', cmd)

    def test_capture_summons_with_its_look_and_records_it(self):
        session = FakeSession()
        with tempfile.TemporaryDirectory() as tmp:
            capture = npc.NpcCapture(session, "myvillage:cultivator", out=Path(tmp), look="f_novice")
            capture.fresh_beast()
            summons = [c for c in session.commands if c.startswith("summon ")]
            self.assertEqual(1, len(summons))
            self.assertIn('Look:"f_novice"', summons[0])
            capture.finish()
            manifest = json.loads((Path(tmp) / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("f_novice", manifest["look"])
            self.assertEqual("myvillage:cultivator", manifest["beast"])
            page = (Path(tmp) / "index.html").read_text(encoding="utf-8")
            self.assertIn("<title>myvillage:cultivator · f_novice in game</title>", page)

    def test_default_capture_summons_without_a_look(self):
        session = FakeSession()
        capture = npc.NpcCapture(session, "myvillage:cultivator", out=None)
        self.assertEqual("default", capture.manifest["look"])
        capture.fresh_beast()
        self.assertFalse([c for c in session.commands if "Look" in c])


if __name__ == "__main__":
    unittest.main()
