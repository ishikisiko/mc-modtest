from __future__ import annotations

import unittest

from tools import world_sim_tasks_evidence as ev

SERVER = "[07Oct2026 12:00:01.000] [Server thread/INFO] [com.example.myvillage.sim.runtime.player.SectTasks/]: "
CLIENT = "[07Oct2026 12:00:04.000] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: "


class LogLineTest(unittest.TestCase):
    def test_sect_task(self):
        p = ev.parse_sect_task(SERVER + "SECT_TASK player=CaptureDev kind=patrol progress=2/3")
        self.assertEqual(p, {"player": "CaptureDev", "kind": "patrol", "progress": 2, "count": 3})
        p = ev.parse_sect_task("SECT_TASK player=CaptureDev kind=courier progress=1/1")
        self.assertEqual((p["kind"], p["progress"], p["count"]), ("courier", 1, 1))
        self.assertIsNone(ev.parse_sect_task("SECT_TASK player=CaptureDev kind=patrol"))
        self.assertIsNone(ev.parse_sect_task("SectTasks: patrol step of CaptureDev failed"))

    def test_sect_entry_new_intents(self):
        for intent in ("TASK_ACCEPT", "TASK_TURN_IN", "APPRENTICE", "JOIN"):
            p = ev.parse_sect_entry(SERVER + f"SECT_ENTRY player=CaptureDev intent={intent} sect=4 result=ok")
            self.assertEqual(p, {"player": "CaptureDev", "intent": intent, "sect": 4, "result": "ok"})
        p = ev.parse_sect_entry("SECT_ENTRY player=CaptureDev intent=TASK_TURN_IN sect=4 result=tribute_short")
        self.assertEqual(p["result"], "tribute_short")
        self.assertIsNone(ev.parse_sect_entry("SECT_ENTRY player=CaptureDev intent=STAY sect=4 result=ok"))

    def test_dialogue_options(self):
        for opt in ("TASK_ACCEPT", "TASK_TURN_IN", "APPRENTICE", "FAREWELL"):
            p = ev.parse_dialogue_option(f"SECT_DIALOGUE option={opt} x=380 y=420.5 w=200 h=40")
            self.assertEqual(p, {"option": opt, "x": 380.0, "y": 420.5, "w": 200.0, "h": 40.0})
        self.assertIsNone(ev.parse_dialogue_option("SECT_DIALOGUE option=BOW x=1 y=2 w=3 h=4"))


class RecordTest(unittest.TestCase):
    REC = ("CaptureDev: inner disciple of 玄黄阁\nJoined: Year 1 of Qiyuan, day 2\nMaster: 李青云\n"
           "Contribution: 15\nStanding with 青木宗: +3\n")

    def test_contribution(self):
        self.assertEqual(ev.contribution_of(self.REC), 15)
        self.assertEqual(ev.contribution_of("Contribution: -2"), -2)
        self.assertIsNone(ev.contribution_of("CaptureDev has not joined a sect"))

    def test_master(self):
        self.assertEqual(ev.master_of(self.REC), "李青云")
        self.assertIsNone(ev.master_of("Master: none\nContribution: 0"))
        self.assertIsNone(ev.master_of("Master: 无"))
        self.assertIsNone(ev.master_of("Contribution: 0"))

    def test_progress(self):
        self.assertEqual(ev.progress_of("Cultivation profile for CaptureDev\ncultivation progress: 1234\n"), 1234)
        self.assertIsNone(ev.progress_of("Unknown command"))

    def test_task_lines_skip_header_and_standing(self):
        rec = "CaptureDev: inner disciple of 巡山派\nMaster: none\nContribution: 0\nStanding with 供奉堂: +1\n"
        self.assertEqual(ev.task_lines_of_record(rec), "")
        self.assertIn("Patrol", ev.task_lines_of_record(rec + "Task: Patrol the Hills 1/3\n"))


class KindTest(unittest.TestCase):
    def test_kind_from_text(self):
        self.assertEqual(ev.kind_from_text("Task: Patrol the Hills 0/3"), "patrol")
        self.assertEqual(ev.kind_from_text("事务：供奉灵石 0/5"), "tribute")
        self.assertEqual(ev.kind_from_text("Carry a Letter to 青木宗"), "courier")
        self.assertEqual(ev.kind_from_text("传信他宗"), "courier")
        self.assertEqual(ev.kind_from_text("task courier_letter"), "courier")
        self.assertIsNone(ev.kind_from_text("patrol or tribute"))  # ambiguous
        self.assertIsNone(ev.kind_from_text("Contribution: 0"))
        self.assertIsNone(ev.kind_from_text(""))

    def test_detect_from_record_first(self):
        rec = "CaptureDev: inner disciple of 玄黄阁\nTask: Spirit Stone Tribute 0/5\n"
        lines = [SERVER + "SECT_TASK player=CaptureDev kind=patrol progress=1/3"]
        d = ev.detect_task_kind(rec, lines, "CaptureDev")
        self.assertEqual((d["kind"], d["source"]), ("tribute", "world player"))

    def test_detect_from_sect_task_line(self):
        rec = "CaptureDev: inner disciple of 玄黄阁\nMaster: none\nContribution: 0\n"
        lines = [SERVER + "SECT_TASK player=Other kind=tribute progress=1/1",
                 SERVER + "SECT_TASK player=CaptureDev kind=patrol progress=1/3",
                 SERVER + "SECT_TASK player=CaptureDev kind=courier progress=1/1",
                 SERVER + "SECT_TASK player=Other kind=patrol progress=2/3"]
        d = ev.detect_task_kind(rec, lines, "CaptureDev")
        self.assertEqual((d["kind"], d["source"]), ("courier", "SECT_TASK"))
        self.assertTrue(d["evidence"].startswith("SECT_TASK player=CaptureDev kind=courier"))

    def test_detect_from_dialogue_then_chat(self):
        rec = "CaptureDev: inner disciple of 玄黄阁\n"
        speaks = (SERVER + "SectDialogue: CaptureDev speaks with 李青云 (steward of sect 3): "
                  "[steward.greet, steward.task.progress.patrol] -> options [FAREWELL]")
        d = ev.detect_task_kind(rec, [speaks], "CaptureDev")
        self.assertEqual((d["kind"], d["source"]), ("patrol", "SectDialogue"))
        plain = speaks.replace(".patrol", "")
        chat = CLIENT + "[System] [CHAT] CaptureDev takes a sect task from 玄黄阁: Carry a Letter."
        d = ev.detect_task_kind(rec, [plain], "CaptureDev", [chat])
        self.assertEqual((d["kind"], d["source"]), ("courier", "chat"))
        self.assertIn("Carry a Letter", d["evidence"])

    def test_detect_unknown(self):
        d = ev.detect_task_kind("CaptureDev: inner disciple of 巡山派\n", [], "CaptureDev",
                                [CLIENT + "[System] [CHAT] The gate of 玄黄阁 stands complete."])
        self.assertEqual(d, {"kind": "unknown", "source": None, "evidence": None})


class ElderTest(unittest.TestCase):
    SECT = ("#3 玄黄阁 (active), seated in 青岚山\nFounded Year -40 by 张道一\nMaster: 王守真\n"
            "Mountain gate at x=120 z=340 (built)\n"
            "At the sect (5): 王守真 剑痴 (Foundation Establishment stage 2, 玄黄阁 sect master), "
            "李青云 (Qi Refining stage 9, 玄黄阁 elder), 赵明 (Qi Refining stage 7, 玄黄阁 inner disciple), "
            "孙静 (Qi Refining stage 8, 玄黄阁 elder), 钱一 (Mortal, rogue cultivator)\n")

    def test_split_top(self):
        self.assertEqual(ev.split_top("A (x, y), B (z), C"), ["A (x, y)", "B (z)", "C"])
        self.assertEqual(ev.split_top(""), [])

    def test_members(self):
        m = ev.parse_members(self.SECT)
        self.assertEqual([x["name"] for x in m], ["王守真", "李青云", "赵明", "孙静", "钱一"])
        self.assertEqual(m[0]["rank"], "玄黄阁 sect master")
        self.assertEqual(m[4]["rank"], "rogue cultivator")
        self.assertEqual(ev.parse_members("Master: none\n"), [])

    def test_elder_names(self):
        self.assertEqual(ev.elder_names(self.SECT), ["李青云", "孙静", "王守真"])
        zh = "Master: 无\nAt the sect (2): 周甲 (练气九层, 青木宗 长老), 吴乙 (练气二层, 青木宗 外门弟子)\n"
        self.assertEqual(ev.elder_names(zh), ["周甲"])

    def test_avatar_names(self):
        tag = ('{{"translate":"entity.myvillage.cultivator.avatar{s}","with":["{n}",'
               '{{"translate":"world_sim.realm.qi_refining"}},"玄黄阁"]}}')
        reply = (f"Cultivator has the following entity data: '{tag.format(s='.steward', n='赵明')}'"
                 f"李青云 · Qi Refining · 玄黄阁 has the following entity data: '{tag.format(s='', n='李青云')}'"
                 "Other has the following entity data: '{\"text\":\"plain\"}'")
        a = ev.avatar_names(reply)
        self.assertEqual(a[0], {"name": "赵明", "sect": "玄黄阁", "steward": True})
        self.assertEqual(a[1], {"name": "李青云", "sect": "玄黄阁", "steward": False})
        self.assertEqual(a[2], {"name": None, "sect": None, "steward": False})

    def test_rank_candidates(self):
        avatars = [{"name": "赵明", "sect": "玄黄阁", "steward": True},
                   {"name": "钱二", "sect": "玄黄阁", "steward": False},
                   {"name": "孙静", "sect": "玄黄阁", "steward": False},
                   {"name": "李青云", "sect": "玄黄阁", "steward": False},
                   {"name": "周甲", "sect": "青木宗", "steward": False},
                   {"name": None, "sect": None, "steward": False}]
        self.assertEqual(ev.rank_elder_candidates(avatars, ["李青云", "孙静", "王守真"], "玄黄阁"), [3, 2, 1])
        self.assertEqual(ev.rank_elder_candidates(avatars, [], "玄黄阁"), [1, 2, 3])


class InventoryAndPlacesTest(unittest.TestCase):
    def test_inventory_count(self):
        reply = ('CaptureDev has the following entity data: [{count: 3, Slot: 0b, id: "myvillage:low_grade_spirit_stone"}, '
                 '{count: 1, Slot: 1b, id: "minecraft:stone"}, '
                 '{Slot: 2b, id: "myvillage:low_grade_spirit_stone", count: 2}]')
        self.assertEqual(ev.inventory_count(reply, ev.STONE), 5)
        self.assertEqual(ev.inventory_count(reply, "minecraft:stone"), 1)
        self.assertEqual(ev.inventory_count("CaptureDev has the following entity data: []", ev.STONE), 0)

    def test_parse_sects(self):
        reply = ("Active sects (2):\n"
                 "#3 玄黄阁 | 青岚山 | master 王守真 | 12 members | top Foundation | prestige 40 | gate 120, 340 (built)\n"
                 "#7 青木宗 | 东林 | master none | 4 members | top none | prestige 2 | gate -50, 900 (not built)\n")
        self.assertEqual(ev.parse_sects(reply), [
            {"id": 3, "name": "玄黄阁", "gate": [120, 340], "built": True},
            {"id": 7, "name": "青木宗", "gate": [-50, 900], "built": False}])

    def test_courier_order(self):
        others = [{"id": 7, "name": "青木宗"}, {"id": 8, "name": "玄黄"}, {"id": 9, "name": "玄黄阁"}]
        self.assertEqual([s["id"] for s in ev.order_courier_targets(others, "Carry a Letter to 玄黄阁")], [9, 7, 8])
        self.assertEqual([s["id"] for s in ev.order_courier_targets(others, "")], [7, 8, 9])

    def test_patrol_spots(self):
        spots = ev.patrol_spots((10.5, -60.0, -3.5))
        self.assertEqual(spots[0], (12, -60, -4))
        self.assertEqual(spots[1], (8, -60, -4))
        self.assertEqual(ev.pick_spaced(spots, 3), spots[:3])
        self.assertEqual(ev.pick_spaced([(0, 0, 0), (1, 0, 0), (2, 0, 0), (0, 0, 2)], 3),
                         [(0, 0, 0), (2, 0, 0), (0, 0, 2)])


class IndexTest(unittest.TestCase):
    def test_index_has_no_paths_and_escapes(self):
        evidence = {"finished": "now", "commit": "abc",
                    "checks": [{"check": "task_accepted", "pass": True, "detail": f"{ev.REPO}/x <b>"},
                               {"check": "master_recorded", "pass": False, "detail": "/home/someone/y"}],
                    "facts": {"sect_id": 3, "sect_name": "玄黄阁", "task": {"kind": "patrol", "source": "chat"},
                              "contribution_before": 0, "contribution_after": 10, "master": "李青云",
                              "meditation": {"ratio": 1.15},
                              "shots": [{"file": "task_offer.png", "title": "offer"},
                                        {"file": None, "title": "panel", "error": f"{ev.REPO}/out"}],
                              "notes": [f"wrote {ev.REPO}/out/preview"]}}
        page = ev.render_index(evidence)
        self.assertNotIn(str(ev.REPO), page)
        self.assertNotIn("/home/", page)
        self.assertIn("&lt;b&gt;", page)
        self.assertIn("task_offer.png", page)
        self.assertIn("1.15", page)
        self.assertIn("1 pass, 1 fail", page)


if __name__ == "__main__":
    unittest.main()
