from __future__ import annotations

import re
import unittest

from tools import world_sim_scripture_evidence as ev

SERVER = "[07Oct2026 12:00:01.000] [Server thread/INFO] [com.example.myvillage.sim.runtime.avatar.ScriptureShelves/]: "
CLIENT = "[07Oct2026 12:00:04.000] [Render thread/INFO] [com.example.myvillage.client.sim.ScriptureHallScreen/]: "


class ShelfLineTest(unittest.TestCase):
    def test_coords(self):
        self.assertEqual(ev.parse_coords("1 -59 2;3 -59 4"), [(1, -59, 2), (3, -59, 4)])
        self.assertEqual(ev.parse_coords(" 10 -55 -20 ; ;x y z;7 8"), [(10, -55, -20)])
        self.assertEqual(ev.parse_coords(""), [])

    def test_shelf_line(self):
        p = ev.parse_shelf(SERVER + "SCRIPTURE_SHELF sect=3 placed=2/2 at=120 -55 340;160 -55 340")
        self.assertEqual(p, {"sect": 3, "placed": 2, "sites": 2, "at": [(120, -55, 340), (160, -55, 340)]})
        p = ev.parse_shelf("SCRIPTURE_SHELF sect=3 placed=0/2 at=")
        self.assertEqual((p["placed"], p["at"]), (0, []))
        self.assertIsNone(ev.parse_shelf("SCRIPTURE_SHELF sect=3 no floor above 1 2 3 (scanned y 1..14); skipped"))

    def test_shelves_reply(self):
        reply = ("Scripture shelves of sect 3: 2 of 2 sites hold a shelf.\n"
                 "  shelf at 120 -55 340, owned by sect 3\n  site 160 -56 340: missing\n")
        self.assertEqual(ev.shelves_from_reply(reply), [{"pos": (120, -55, 340), "sect": 3}])


class HallLineTest(unittest.TestCase):
    def test_open(self):
        p = ev.parse_hall(SERVER + "SCRIPTURE_HALL player=CaptureDev intent=OPEN sect=3 member=true entries=1")
        self.assertEqual(p, {"player": "CaptureDev", "intent": "OPEN", "sect": 3, "member": True, "entries": 1})
        p = ev.parse_hall("SCRIPTURE_HALL player=CaptureDev intent=OPEN sect=3 member=false reason=not_member entries=0")
        self.assertEqual((p["member"], p["reason"], p["entries"]), (False, "not_member", 0))
        p = ev.parse_hall("SCRIPTURE_HALL player=CaptureDev intent=OPEN sect=3 entries=2")
        self.assertNotIn("member", p)
        self.assertEqual(p["entries"], 2)

    def test_borrow(self):
        p = ev.parse_hall("SCRIPTURE_HALL player=CaptureDev intent=BORROW sect=3 technique=azure_breath result=ok")
        self.assertEqual((p["intent"], p["technique"], p["result"]), ("BORROW", "azure_breath", "ok"))
        p = ev.parse_hall("SCRIPTURE_HALL player=CaptureDev intent=BORROW sect=3 technique=myvillage:x "
                          "result=already_borrowed")
        self.assertEqual((ev.tech_path(p["technique"]), p["result"]), ("x", "already_borrowed"))
        self.assertIsNone(ev.parse_hall("SCRIPTURE_HALL player=CaptureDev intent=STEAL sect=3"))
        self.assertIsNone(ev.parse_hall("SCRIPTURE_HALL_UI technique=a x=1 y=2 w=3 h=4"))

    def test_ui(self):
        p = ev.parse_hall_ui(CLIENT + "SCRIPTURE_HALL_UI technique=myvillage:azure_breath x=600 y=180 w=80 h=40")
        self.assertEqual(p, {"technique": "azure_breath", "x": 600.0, "y": 180.0, "w": 80.0, "h": 40.0})
        self.assertEqual(ev.parse_hall_ui("SCRIPTURE_HALL_UI technique=b x=1.5 y=2 w=3 h=4")["x"], 1.5)
        self.assertIsNone(ev.parse_hall_ui("SCRIPTURE_HALL_UI technique=b x=1 y=2"))
        self.assertIsNone(ev.HALL.search("SCRIPTURE_HALL_UI technique=b x=1 y=2 w=3 h=4"))
        self.assertEqual(ev.button_center(p), (640, 200))


class SectReplyTest(unittest.TestCase):
    def test_heritage(self):
        reply = ("#3 Azure Cloud (active), seated in East\nSignature technique: Azure Breath\n"
                 "Heritage: Azure Cloud Scriptures\nMountain gate at x=100 z=200 (not built)\n")
        self.assertEqual(ev.heritage_of(reply), "Azure Cloud Scriptures")
        self.assertIsNone(ev.heritage_of(reply.replace("Azure Cloud Scriptures", "none")))
        self.assertIsNone(ev.heritage_of("#3 Azure Cloud (active)"))
        self.assertEqual(ev.heritage_of("Heritage: 青云真传Mountain gate at x=1 z=2 (built)"), "青云真传")


class InventoryTest(unittest.TestCase):
    REPLY = ('CaptureDev has the following entity data: [{count: 1, Slot: 0b, components: '
             '{"minecraft:custom_name": \'{"text":"a } [ odd name"}\', "myvillage:technique": '
             '"myvillage:azure_breath"}, id: "myvillage:manual_breathing_huang"}, '
             '{count: 64, Slot: 1b, id: "minecraft:stone"}, '
             '{count: 1, Slot: 2b, components: {"myvillage:technique": "myvillage:cloud_step"}, '
             'id: "myvillage:manual_movement_xuan"}]')

    def test_split(self):
        items = ev.split_items(self.REPLY)
        self.assertEqual(len(items), 3)
        self.assertIn("minecraft:stone", items[1])
        self.assertEqual(ev.split_items("CaptureDev has the following entity data: []"), [])
        self.assertEqual(ev.split_items("No entity was found"), [])

    def test_manuals(self):
        self.assertEqual(ev.manuals_in_inventory(self.REPLY),
                         [{"manual": "manual_breathing_huang", "technique": "azure_breath"},
                          {"manual": "manual_movement_xuan", "technique": "cloud_step"}])
        self.assertEqual(ev.manual_count(self.REPLY, "myvillage:azure_breath"), 1)
        self.assertEqual(ev.manual_count(self.REPLY, "cloud_step"), 1)
        self.assertEqual(ev.manual_count(self.REPLY, "iron_body"), 0)
        blank = 'CaptureDev has the following entity data: [{count: 1, Slot: 0b, id: "myvillage:manual_body_huang"}]'
        self.assertEqual(ev.manuals_in_inventory(blank), [{"manual": "manual_body_huang", "technique": None}])


class StandTest(unittest.TestCase):
    def test_candidates(self):
        c = ev.stand_candidates((10, -55, 20), 3.0)
        self.assertEqual([x["dir"] for x in c], [(0, 1), (0, -1), (1, 0), (-1, 0)])
        self.assertEqual(c[0]["feet"], (10.5, -55.0, 23.5))
        self.assertEqual(c[0]["cell"], (10, -55, 23))
        self.assertEqual(c[0]["between"], [(10, -55, 21), (10, -55, 22)])
        self.assertEqual(c[1]["cell"], (10, -55, 17))
        self.assertEqual(c[3]["between"], [(9, -55, 20), (8, -55, 20)])
        self.assertEqual(ev.shelf_center((10, -55, 20)), (10.5, -54.5, 20.5))


class IndexTest(unittest.TestCase):
    def test_render_has_no_paths_and_escapes(self):
        evidence = {
            "finished": "2026-10-07T12:00:00+00:00", "commit": "abc1234",
            "checks": [{"check": "borrow_ok", "pass": True, "detail": "{\"result\": \"ok\"}"},
                       {"check": "session_completed", "pass": False,
                        "detail": f"server not ready; see {ev.REPO}/run-acceptance/logs/latest.log <x>"}],
            "facts": {"sect_id": 3, "sect_name": "Azure Cloud", "heritage": "Azure <Scriptures>",
                      "shelves": [[120, -55, 340], [160, -55, 340]], "borrowed": ["azure_breath"],
                      "notes": ["fallback in /home/someone/x"],
                      "shots": [{"file": "hall_outer.png", "title": "outer", "stable": False},
                                {"file": None, "title": "refused", "error": "screen open"}]},
        }
        page = ev.render_index(evidence)
        self.assertNotIn(str(ev.REPO), page)
        self.assertNotIn("/home/", page)
        self.assertNotIn("<x>", page)
        self.assertIn("&lt;x&gt;", page)
        self.assertIn("Azure &lt;Scriptures&gt;", page)
        self.assertIn("120 -55 340; 160 -55 340", page)
        self.assertIn("azure_breath", page)
        self.assertIn("hall_outer.png", page)
        self.assertIn("[UNSTABLE]", page)
        self.assertIn("1 pass, 1 fail", page)
        self.assertNotIn("rcon", page.lower())
        self.assertNotIn("password", page.lower())
        self.assertIsNone(re.search(r"port\W{0,3}\d|localhost|127\.0\.0\.1", page.lower()))


if __name__ == "__main__":
    unittest.main()
