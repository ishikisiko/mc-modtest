import unittest

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


if __name__ == "__main__":
    unittest.main()
