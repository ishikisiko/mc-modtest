from __future__ import annotations

import unittest

from tools import world_sim_entry_evidence as ev

SERVER = "[07Oct2026 12:00:{s:02d}.000] [Server thread/INFO] [com.example.myvillage.sim.runtime.avatar.GateRealizer/]: "


def sline(sec: int, msg: str) -> str:
    return SERVER.format(s=sec) + msg


class LogLineTest(unittest.TestCase):
    def test_gate_realize_states(self):
        self.assertEqual(ev.parse_gate_realize(sline(1, "GATE_REALIZE sect=3 state=queued distance=140")),
                         {"sect": 3, "state": "queued", "rest": "distance=140"})
        clip = ev.parse_gate_realize(sline(2, "GATE_REALIZE sect=3 state=clip 4/36"))
        self.assertEqual((clip["state"], clip["clip"], clip["clips"]), ("clip", 4, 36))
        self.assertEqual(ev.parse_gate_realize("GATE_REALIZE sect=12 state=done")["state"], "done")
        self.assertEqual(ev.parse_gate_realize("GATE_REALIZE sect=12 state=failed error=x")["state"], "failed")
        self.assertIsNone(ev.parse_gate_realize("GATE_REALIZE sect=12 state=building"))
        self.assertIsNone(ev.parse_gate_realize("unrelated line"))

    def test_sect_entry(self):
        p = ev.parse_sect_entry(sline(3, "SECT_ENTRY player=CaptureDev intent=JOIN sect=4 result=ok"))
        self.assertEqual(p, {"player": "CaptureDev", "intent": "JOIN", "sect": 4, "result": "ok"})
        p = ev.parse_sect_entry("SECT_ENTRY player=CaptureDev intent=JOIN sect=4 result=rejoin_cooldown")
        self.assertEqual(p["result"], "rejoin_cooldown")
        p = ev.parse_sect_entry("SECT_ENTRY player=CaptureDev intent=LEAVE sect=-1 result=not_member")
        self.assertEqual((p["intent"], p["sect"]), ("LEAVE", -1))
        self.assertIsNone(ev.parse_sect_entry("SECT_ENTRY player=CaptureDev intent=STAY sect=4 result=ok"))

    def test_dialogue_option(self):
        line = "[07Oct2026 12:00:04.000] [Render thread/INFO] [SectDialogueScreen/]: SECT_DIALOGUE option=JOIN x=380 y=420 w=200 h=40"
        p = ev.parse_dialogue_option(line)
        self.assertEqual(p, {"option": "JOIN", "x": 380.0, "y": 420.0, "w": 200.0, "h": 40.0})
        self.assertEqual(ev.parse_dialogue_option("SECT_DIALOGUE option=FAREWELL x=10.5 y=2 w=3 h=4")["x"], 10.5)
        self.assertIsNone(ev.parse_dialogue_option("SECT_DIALOGUE option=JOIN x=1 y=2"))

    def test_button_center(self):
        opt = {"x": 380.0, "y": 420.0, "w": 200.0, "h": 40.0}
        self.assertEqual(ev.button_center(opt), (480, 440))
        self.assertEqual(ev.button_center({"x": 190, "y": 210, "w": 100, "h": 20}, scale=2), (480, 440))
        self.assertEqual(ev.button_center({"x": 0, "y": 0, "w": 5, "h": 5}), (2, 2))  # 2.5 rounds to even

    def test_realize_summary(self):
        lines = [sline(0, "GATE_REALIZE sect=3 state=queued distance=140"),
                 sline(1, "GATE_REALIZE sect=5 state=started"),
                 sline(2, "GATE_REALIZE sect=3 state=started anchor=1 -60 2"),
                 sline(3, "GATE_REALIZE sect=3 state=clip 1/2"),
                 sline(4, "GATE_REALIZE sect=3 state=clip 2/2"),
                 sline(12, "GATE_REALIZE sect=3 state=done")]
        s = ev.realize_summary(lines, 3)
        self.assertEqual(s["seconds"], 10.0)
        self.assertEqual((s["clips_logged"], s["clips_total"]), (2, 2))
        self.assertEqual(s["started"], "GATE_REALIZE sect=3 state=started anchor=1 -60 2")
        self.assertIsNone(s["failed"])
        self.assertIsNone(ev.realize_summary(lines[:3], 3)["seconds"])


class StewardTest(unittest.TestCase):
    REPLY = ("Li Qing · Qi Refining · Azure Cloud has the following entity data: "
             "'{\"translate\":\"entity.myvillage.cultivator.avatar\",\"with\":[\"Li Qing\",{\"translate\":"
             "\"world_sim.realm.qi_refining\"},\"Azure Cloud\"]}'"
             "Wang Ming · Qi Refining · Azure Cloud · Gate Steward has the following entity data: "
             "'{\"translate\":\"entity.myvillage.cultivator.avatar.steward\",\"with\":[\"Wang Ming\",{\"translate\":"
             "\"world_sim.realm.qi_refining\"},\"Azure Cloud\"]}'\n"
             "Zhao Yun has the following entity data: '{\"translate\":\"entity.myvillage.cultivator.avatar\"}'")

    def test_split_and_find(self):
        self.assertEqual(len(ev.split_entities(self.REPLY)), 3)
        self.assertEqual(ev.find_steward(self.REPLY), 1)
        self.assertIsNone(ev.find_steward(self.REPLY.replace(".avatar.steward", ".avatar")))
        self.assertIsNone(ev.find_steward("No entity was found"))

    def test_uuid_from_ints(self):
        self.assertEqual(ev.uuid_from_ints([0, 0, 0, 1]), "00000000-0000-0000-0000-000000000001")
        self.assertEqual(ev.uuid_from_ints((-1, 305419896, -2023406815, 4660)),
                         "ffffffff-1234-5678-8765-432100001234")

    def test_front_candidates(self):
        c = ev.front_candidates((10.0, -60.0, 20.0), 0.0)
        self.assertAlmostEqual(c[0][0], 10.0)
        self.assertAlmostEqual(c[0][2], 23.0)  # yaw 0 faces +z
        c = ev.front_candidates((10.0, -60.0, 20.0), 90.0)
        self.assertAlmostEqual(c[0][0], 7.0)  # yaw 90 faces -x
        self.assertEqual(len(ev.front_candidates((0, 0, 0), None)), 4)


class PanelAndTextTest(unittest.TestCase):
    def test_world_tab(self):
        # GUI 480x270: panel 472x246 at (4, 12); world tab y = 12 + 36 + 3 * 21 .. +18
        self.assertEqual(ev.panel_tab_center("world"), (54, 240))
        self.assertEqual(ev.panel_tab_center("profile"), (54, 114))

    def test_mspt(self):
        self.assertEqual(ev.parse_mspt("Average time per tick: 12.5ms (Target: 50.0ms)"), 12.5)
        self.assertIsNone(ev.parse_mspt("Unknown command"))

    def test_mentions(self):
        text = "Chronicle:\nCaptureDev joined Azure Cloud as an outer disciple.\nLi Qing joined Red Peak."
        self.assertEqual(ev.mentions(text, "CaptureDev", r"join|拜入"),
                         ["CaptureDev joined Azure Cloud as an outer disciple."])
        self.assertEqual(ev.mentions("CaptureDev拜入青云宗，为外门弟子。", "CaptureDev", r"join|拜入"),
                         ["CaptureDev拜入青云宗，为外门弟子。"])


class IndexTest(unittest.TestCase):
    def test_render_has_no_paths_and_escapes(self):
        evidence = {
            "finished": "2026-10-07T12:00:00+00:00", "commit": "abc1234",
            "checks": [{"check": "join_ok", "pass": True, "detail": "{\"result\": \"ok\"}"},
                       {"check": "session_completed", "pass": False,
                        "detail": f"server not ready; see {ev.REPO}/run-combat-capture/logs/server.log <x>"}],
            "facts": {"sect_id": 3, "sect_name": "Azure Cloud", "at_sect": 9,
                      "realize": {"seconds": 42.0, "clips_logged": 36},
                      "notes": ["fallback in /home/someone/x"],
                      "shots": [{"file": "dialogue_open.png", "title": "the dialogue", "stable": False},
                                {"file": None, "title": "panel", "error": "screen open"}]},
        }
        page = ev.render_index(evidence)
        self.assertNotIn(str(ev.REPO), page)
        self.assertNotIn("/home/", page)
        self.assertNotIn("<x>", page)
        self.assertIn("&lt;x&gt;", page)
        self.assertIn("dialogue_open.png", page)
        self.assertIn("[UNSTABLE]", page)
        self.assertIn("1 pass, 1 fail", page)
        self.assertNotIn("rcon", page.lower().replace("every rcon command", ""))
        self.assertNotIn("password", page.lower())


if __name__ == "__main__":
    unittest.main()
