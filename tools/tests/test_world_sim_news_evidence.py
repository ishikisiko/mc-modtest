from __future__ import annotations

import unittest

from tools import world_sim_news_evidence as ev

SERVER = "[07Oct2026 12:00:01.000] [Server thread/INFO] [com.example.myvillage.sim.runtime.player.SectDialogue/]: "
CLIENT = "[07Oct2026 12:00:04.000] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: "


class ChatTest(unittest.TestCase):
    def test_news_prefix_zh_and_en(self):
        self.assertEqual(ev.parse_news_chat(CLIENT + "[CHAT] 【宗门】玄黄阁 (掌门 李青云) 向青木宗宣战。"),
                         "玄黄阁 (掌门 李青云) 向青木宗宣战。")
        self.assertEqual(ev.parse_news_chat(CLIENT + "[CHAT] [Sect] 李青云 (sect master 玄黄阁) declares war on 青木宗."),
                         "李青云 (sect master 玄黄阁) declares war on 青木宗.")
        self.assertEqual(ev.parse_news_chat("[CHAT] 【宗门】 x  "), "x")

    def test_news_prefix_rejects_other_lines(self):
        for line in (CLIENT + "[CHAT] [Task] Patrol the Hills",
                     CLIENT + "[CHAT] <CaptureDev> [Sect] typed by hand? no: a player line",
                     CLIENT + "【宗门】 not a chat line",
                     SERVER + "SECT_NEWS player=CaptureDev event=12 type=war sects=[1, 2]"):
            self.assertIsNone(ev.parse_news_chat(line), line)

    def test_gone_chat(self):
        self.assertTrue(ev.is_gone_chat(CLIENT + "[CHAT] 玄黄阁 is no more; CaptureDev goes on as a rogue cultivator."))
        self.assertTrue(ev.is_gone_chat(CLIENT + "[CHAT] [Sect] 玄黄阁 in 青州, last held by 李青云, falls apart."))
        self.assertTrue(ev.is_gone_chat(CLIENT + "[CHAT] 【宗门】玄黄阁覆灭。"))
        self.assertFalse(ev.is_gone_chat(CLIENT + "[CHAT] [Task] Patrol the Hills"))
        self.assertFalse(ev.is_gone_chat(SERVER + "玄黄阁 is no more"))


class ReplyTest(unittest.TestCase):
    def test_war_reply(self):
        self.assertTrue(ev.war_reply_ok("玄黄阁 declared war on 青木宗 (admin)"))
        self.assertTrue(ev.war_reply_ok("玄黄阁向青木宗宣战（管理）"))
        self.assertTrue(ev.war_reply_ok("commands.myvillage.world.sect_war.done"))
        self.assertFalse(ev.war_reply_ok("Declaring war failed: already_at_war"))
        self.assertFalse(ev.war_reply_ok("Unknown or incomplete command, see below for error"))
        self.assertFalse(ev.war_reply_ok(""))

    def test_destroy_reply(self):
        self.assertTrue(ev.destroy_reply_ok("玄黄阁 is destroyed (admin); its people are now rogue cultivators"))
        self.assertTrue(ev.destroy_reply_ok("玄黄阁已覆灭（管理）"))
        self.assertFalse(ev.destroy_reply_ok("Destroying the sect failed: sect_inactive"))
        self.assertFalse(ev.destroy_reply_ok("Incorrect argument for command"))

    def test_rogue_record(self):
        self.assertTrue(ev.is_rogue("CaptureDev: rogue cultivator\nLeft 玄黄阁: Year 3, day 4\n"))
        self.assertTrue(ev.is_rogue("CaptureDev: 散修\n"))
        self.assertFalse(ev.is_rogue("CaptureDev: outer disciple of 玄黄阁\nStanding with 青木宗: rogue -3\n"))
        self.assertFalse(ev.is_rogue(""))

    def test_gone_lines(self):
        chron = ("Year 3, day 4: 玄黄阁 in 青州, last held by 李青云, falls apart.\n"
                 "Year 3, day 4: 玄黄阁 is no more; CaptureDev goes on as a rogue cultivator.\n"
                 "Year 3, day 3: 王五 joins 青木宗.\n")
        self.assertEqual(len(ev.gone_lines(chron)), 2)
        self.assertEqual(ev.gone_lines("Year 1: 王五 joins 青木宗."), [])

    def test_count(self):
        self.assertEqual(ev.count_of("Test passed, count: 7"), 7)
        self.assertEqual(ev.count_of("Test failed"), 0)


class ServerLineTest(unittest.TestCase):
    def test_speaks(self):
        p = ev.parse_speaks(SERVER + "SectDialogue: CaptureDev speaks with 张三 (steward of sect 2): "
                                     "[steward.greet, steward.refuse.at_war] -> options [FAREWELL]")
        self.assertEqual(p, {"player": "CaptureDev", "speaker": "张三", "role": "steward", "sect": 2,
                             "lines": ["steward.greet", "steward.refuse.at_war"], "options": ["FAREWELL"]})
        p = ev.parse_speaks("SectDialogue: CaptureDev speaks with 李四 (elder of sect 5): [elder.greet] "
                            "-> options [APPRENTICE, FAREWELL]")
        self.assertEqual(p["options"], ["APPRENTICE", "FAREWELL"])
        self.assertIsNone(ev.parse_speaks("SectDialogue: no dialogue for CaptureDev: gone"))

    def test_sect_news(self):
        p = ev.parse_sect_news(SERVER + "SECT_NEWS player=CaptureDev event=41 type=war sects=[1, 2]")
        self.assertEqual(p, {"player": "CaptureDev", "event": 41, "type": "war", "sects": [1, 2]})
        self.assertIsNone(ev.parse_sect_news("SECT_ENTRY player=CaptureDev intent=JOIN sect=1 result=ok"))


class PickTest(unittest.TestCase):
    ROWS = [{"id": 1, "name": "玄黄阁", "gate": [0, 0], "built": False, "at": 5},
            {"id": 2, "name": "青木宗", "gate": [300, 0], "built": False, "at": 3},
            {"id": 3, "name": "天剑门", "gate": [900, 0], "built": True, "at": 2},
            {"id": 4, "name": "空山派", "gate": [2000, 0], "built": False, "at": 0}]

    def test_pair(self):
        a, b = ev.choose_pair(self.ROWS)
        self.assertEqual((a["id"], b["id"]), (1, 3))  # A: not built, most members; B: built, has a steward

    def test_pair_pinned(self):
        a, b = ev.choose_pair(self.ROWS, want_a=2, want_b=4)
        self.assertEqual((a["id"], b["id"]), (2, 4))
        self.assertIsNone(ev.choose_pair(self.ROWS, want_a=4))
        self.assertIsNone(ev.choose_pair(self.ROWS[:1]))

    def test_steward_index(self):
        avatars = [{"name": "甲", "sect": "玄黄阁", "steward": True},
                   {"name": "乙", "sect": None, "steward": True},
                   {"name": "丙", "sect": "青木宗", "steward": True},
                   {"name": "丁", "sect": "青木宗", "steward": False}]
        self.assertEqual(ev.steward_index(avatars, "青木宗"), 2)
        self.assertEqual(ev.steward_index(avatars, "天剑门"), 1)
        self.assertEqual(ev.steward_index(avatars[:1], "天剑门"), None)
        self.assertEqual(ev.avatars_of(avatars, "青木宗"), 2)

    def test_site(self):
        self.assertEqual(ev.site_box([100, 200]), (68, 110, 64, 180))
        self.assertEqual(ev.court_of([100, 200]), (99.5, -60.0, 135.5))


class IndexTest(unittest.TestCase):
    def test_index_has_no_paths(self):
        evidence = {
            "finished": "2026-10-07 12:00", "commit": "abc1234",
            "checks": [{"check": "sect_news_delivered", "pass": True,
                        "detail": f"read from {ev.REPO}/run-acceptance/logs/latest.log"},
                       {"check": "hostile_steward_refuses", "pass": False, "detail": "/home/someone/x <b>"}],
            "facts": {"sect_a": {"id": 1, "name": "玄黄阁"}, "sect_b": {"id": 3, "name": "天剑门"},
                      "news_lines": ["玄黄阁 declares war on 天剑门."], "destroy_lines": ["玄黄阁 is no more"],
                      "notes": [f"note {ev.REPO}"],
                      "shots": [{"file": "news_chat.png", "title": "chat", "stable": True},
                                {"file": None, "title": "missing", "error": f"{ev.REPO}/x failed"}]},
        }
        page = ev.render_index(evidence)
        self.assertNotIn(str(ev.REPO), page)
        self.assertNotIn("/home/", page)
        self.assertNotIn("<b>", page)
        self.assertIn("news_chat.png", page)
        self.assertIn("1 pass, 1 fail", page)
        self.assertIn("玄黄阁 declares war on 天剑门.", page)


if __name__ == "__main__":
    unittest.main()
