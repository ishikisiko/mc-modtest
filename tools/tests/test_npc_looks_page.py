import tempfile
import unittest
from pathlib import Path

from tools import npc_looks_page as page
from tools.combat_capture import npc


def touch(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"x")


class NpcLooksPageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.preview = Path(self.tmp.name) / "preview"
        self.page_dir = self.preview / "cultivator" / "looks"
        touch(self.preview / "cultivator" / "turnaround.png")
        touch(self.preview / "cultivator_f_novice" / "turnaround.png")
        touch(self.preview / "cultivator_f_novice" / "walk_side.gif")
        touch(self.preview / "cultivator" / "ingame" / "stills" / "idle_front.png")
        touch(self.preview / "cultivator" / "ingame_f_adept" / "video" / "walk_front.mp4")

    def tearDown(self):
        self.tmp.cleanup()

    def row(self, table, title):
        return next(r for r in table if r["title"] == title)

    def test_columns_and_rows(self):
        self.assertEqual(["default", "f_novice", "f_adept"], [look for look, _, _ in page.LOOKS])
        table = page.rows(self.preview, self.page_dir)
        self.assertEqual(["离线六视图", "面部页", "离线特写", "走路 GIF", "无头四面立绘", "无头特写", "步行视频"],
                         [r["title"] for r in table])
        for r in table:
            self.assertEqual(3, len(r["cells"]))
        self.assertEqual(4, len(self.row(table, "无头四面立绘")["cells"][0]))
        self.assertEqual("video", self.row(table, "步行视频")["kind"])

    def test_paths_are_relative_to_the_page(self):
        table = page.rows(self.preview, self.page_dir)
        six = self.row(table, "离线六视图")["cells"]
        self.assertEqual("../turnaround.png", six[0][0]["src"])  # the default look's renders sit in cultivator/
        self.assertEqual("../../cultivator_f_novice/turnaround.png", six[1][0]["src"])
        self.assertEqual("../../cultivator_f_adept/turnaround.png", six[2][0]["src"])
        idle = self.row(table, "无头四面立绘")["cells"]
        self.assertEqual("../ingame/stills/idle_front.png", idle[0][0]["src"])
        self.assertEqual("../ingame_f_novice/stills/idle_front.png", idle[1][0]["src"])
        self.assertEqual("../ingame_f_adept/stills/idle_front.png", idle[2][0]["src"])

    def test_missing_files_are_marked_not_captured(self):
        table = page.rows(self.preview, self.page_dir)
        six = self.row(table, "离线六视图")["cells"]
        self.assertEqual([True, True, False], [c[0]["present"] for c in six])
        walk = self.row(table, "走路 GIF")["cells"][1]
        self.assertEqual([False, True, False], [i["present"] for i in walk])
        html = page.render(table)
        # a cell with nothing at all, and an item missing inside a partly filled cell
        self.assertIn('<td class="none">未采集</td>', html)
        self.assertIn('<figure class="missing"><div>未采集</div><figcaption>左前</figcaption></figure>', html)
        self.assertIn('src="../../cultivator_f_novice/walk_side.gif"', html)
        self.assertNotIn("cultivator_f_adept/turnaround.png", html)
        self.assertIn('<video src="../ingame_f_adept/video/walk_front.mp4"', html)

    def test_page_has_no_absolute_paths_and_states_the_open_questions(self):
        out = Path(self.tmp.name) / "elsewhere"
        written, table = page.write(out, self.preview)
        text = written.read_text(encoding="utf-8")
        self.assertNotIn(self.tmp.name, text)
        self.assertNotIn(str(page.REPO), text)
        for needle in ("default", "f_novice", "f_adept", "not_verified", "配色 A", "低马尾", "自动换装"):
            self.assertIn(needle, text)
        self.assertIn('src="../preview/cultivator/turnaround.png"', text)

    def test_empty_preview_renders_every_cell_as_not_captured(self):
        empty = Path(self.tmp.name) / "empty"
        table = page.rows(empty, empty / "cultivator" / "looks")
        self.assertEqual(7 * 3, page.render(table).count('<td class="none">未采集</td>'))

    def test_ingame_folder_rule_matches_the_capture(self):
        for look in npc.LOOKS:
            self.assertEqual(npc.ingame_dir_name(look), page.ingame_dir_name(look))
        self.assertEqual(list(npc.LOOKS), [look for look, _, _ in page.LOOKS])


if __name__ == "__main__":
    unittest.main()
