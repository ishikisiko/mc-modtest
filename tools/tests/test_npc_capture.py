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
    def test_prebuilt_capture_skips_resource_generation_and_fails_without_build(self):
        from tools.combat_capture import session
        default=session.gradle_argv('runAcceptanceServer','-Xmx1g')
        self.assertNotIn('processResources',default)
        for task in ('runAcceptanceServer','runClient'):
            args=session.gradle_argv(task,'-Xmx1g',prebuilt_resources=True)
            self.assertEqual('-x',args[args.index('processResources')-1])
            self.assertIn(task,args)
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            with self.assertRaises(RuntimeError):session.require_prebuilt_resources(root)
            for name in ('build/resources/main/META-INF/neoforge.mods.toml',
                         'build/resources/main/data/myvillage/story/qianzhen_scene.json',
                         'build/classes/java/main/com/example/myvillage/MyVillageMod.class'):
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'fixture')
            session.require_prebuilt_resources(root)
        with self.assertRaises(ValueError):session.SessionConfig(session_id='invalid',prebuilt_resources='yes')

    def test_npc_build_keeps_all_bootstrap_resource_tasks(self):
        from tools.modal_runner.remote import PRESETS
        command=PRESETS['npc-variants-build'][1]
        excluded={command[i+1] for i,v in enumerate(command[:-1]) if v=='-x'}
        self.assertEqual({'generateAllStructures'},excluded)
        self.assertIn('jar',command)

    def test_native_readback_requires_exact_look_field(self):
        from tools.npc_variant_evidence import parse_look
        self.assertEqual('f_novice_02_a_pants', parse_look('修仙者 has data: {UUID: [I; 1, 2, 3, 4], Look: "f_novice_02_a_pants"}'))
        self.assertEqual('default', parse_look('修仙者 has data: {UUID: [I; 1, 2, 3, 4]}'))

    def test_colours_are_bounded_written_and_read_back_independently(self):
        from tools.npc_variant_evidence import parse_colours
        cmd=npc.summon_npc('myvillage:cultivator',beast.WOLF_POS,0,look='m_02_04_b',colours=66)
        self.assertIn(',Look:"m_02_04_b",Colours:66}',cmd)
        self.assertEqual(66,parse_colours('{Look:"m_02_04_b",Colours:66}'))
        self.assertEqual(-1,parse_colours('{CustomName:"Colours:66"}'))
        for value in (-1,6,144):
            with self.assertRaises(ValueError):npc.summon_npc('myvillage:cultivator',beast.WOLF_POS,0,colours=value)

    def test_native_fixture_fits_loaded_chunks_and_covers_all_colours(self):
        from tools.npc_variant_evidence import fixture_colours
        self.assertEqual({-1,50,66,82},{fixture_colours(i) for i in range(len(npc.LOOKS))})
        self.assertTrue(all(-48 <= (i % 15)*3-21 <= 48 and -48 <= (i//15)*3+3 <= 48 for i in range(len(npc.LOOKS))))

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
        from tools.npcgen import catalog
        self.assertEqual(tuple(catalog.load()['legacy_looks']) + tuple(v['look'] for v in catalog.runtime_document()['variants']), npc.LOOKS)

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
