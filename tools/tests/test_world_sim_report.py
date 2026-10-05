from __future__ import annotations

import contextlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

from tools import world_sim_report as rep

LANG = {
    "world_sim.date.era": "启元%1$s年",
    "world_sim.date.before_era": "启元前%1$s年",
    "world_sim.realm.qi_refining": "炼气",
    "world_sim.realm.foundation_establishment": "筑基",
    "world_sim.realm.golden_core": "金丹",
    "world_sim.realm.nascent_soul": "元婴",
    "world_sim.stage.golden_core.2": "金丹中期",
    "world_sim.root.heaven": "天灵根",
    "world_sim.root.penta": "五灵根",
    "world_sim.event.genesis.sect": "%1$s立于%2$s，掌门%3$s，%4$s修为。",
    "world_sim.event.death.old_age.master": "%2$s掌门%1$s寿元已尽，坐化。",
    "world_sim.event.succession": "%3$s既逝，长老%1$s承继%2$s掌门之位。",
    "world_sim.event.breakthrough.golden_core": "%1$s结成金丹，自号%2$s。",
    "world_sim.event.recruit": "%1$s拜入%2$s，为外门弟子。",
    "world_sim.event.kill": "%1$s斩杀%2$s。",
    "world_sim.event.revenge": "%1$s为报师仇，诛杀%2$s。",
}

REALMS = [{"id": "qi_refining", "lifespan_years": 120}, {"id": "foundation_establishment", "lifespan_years": 240},
          {"id": "golden_core", "lifespan_years": 500, "title_suffix": "真人"},
          {"id": "nascent_soul", "lifespan_years": 1000, "title_suffix": "真君"}]


def ev(eid, day, typ, imp, actors, key, params, cause=-1, sects=(1,), text=None):
    e = {"id": eid, "day": day, "type": typ, "importance": imp, "actors": list(actors), "sects": list(sects),
         "region": "zhongzhou", "cause": cause, "key": key, "params": list(params)}
    if text is not None:
        e["text"] = text
    return e


def fixture() -> dict:
    """Day 60 is the era start (10 prehistory years at 6 days/year); the run ends on day 120."""
    events = [
        ev(1, 0, "genesis", 3, [1], "world_sim.event.genesis.sect", ["青云宗", "中州", "甲长老", "@world_sim.stage.golden_core.2"]),
        ev(2, 30, "recruit", 1, [2], "world_sim.event.recruit", ["乙弟子", "青云宗"]),
        ev(3, 40, "recruit", 1, [3], "world_sim.event.recruit", ["丙散人", "青云宗"]),
        ev(4, 59, "death", 3, [1], "world_sim.event.death.old_age.master", ["甲长老", "青云宗"]),
        ev(5, 60, "succession", 3, [2], "world_sim.event.succession", ["乙弟子", "青云宗", "甲长老"], cause=4),
        ev(6, 70, "kill", 2, [3, 2], "world_sim.event.kill", ["丙散人", "乙弟子"]),
        ev(7, 80, "recruit", 1, [4], "world_sim.event.recruit", ["丁少年", "青云宗"]),
        ev(8, 95, "revenge", 3, [4, 3], "world_sim.event.revenge", ["丁少年", "丙散人"], cause=6),
        ev(9, 100, "breakthrough", 3, [4], "world_sim.event.breakthrough.golden_core", ["丁少年", "玄明"]),
        # A type and key the language file does not know yet: renders through the dump's own text.
        ev(10, 101, "omen_comet", 2, [4], "world_sim.event.omen.comet", ["丁少年"], text="天降彗星，丁少年见之。"),
        ev(11, 102, "omen_rain", 2, [], "world_sim.event.omen.rain", ["血雨"]),
        ev(12, 103, "fortune_herb", 1, [4], "world_sim.event.fortune.herb", ["丁少年", "中州"], cause=9),
        ev(13, 104, "breakthrough", 2, [5], "world_sim.event.breakthrough.golden_core", ["戊同名", "甲"]),
    ]
    return {
        "config": {"seed": 7, "tier": "small", "years": 10, "days_per_year": 6, "prehistory_years": 10,
                   "prehistory_days": 60, "final_day": 120, "future_field": {"ignored": True}},
        "final_state": {
            "format": "myvillage:world_sim", "version": 1, "seed": 7, "tier": "small", "day": 120,
            "prehistory_days": 60,
            "sects": [{"id": 1, "name": "青云宗", "region": "zhongzhou", "founder": 1, "founded_day": -300,
                       "master": 4, "signature_technique": "tj", "state": "active", "destroyed_day": -1}],
            "persons": [
                {"id": 4, "surname": "丁", "given": "少年", "birth": -10, "root": [10000, 0, 0, 0, 0],
                 "realm": "golden_core", "stage": 0, "sect": 1, "rank": "sect_master", "master": 2,
                 "technique": "tj", "dao_name": "玄明", "relations": [[3, "enemy", 90, 6]], "unknown_field": 1},
                {"id": 5, "surname": "戊", "given": "同名", "birth": -20, "root": [2000, 2000, 2000, 2000, 2000],
                 "realm": "golden_core", "stage": 0, "sect": 1, "rank": "elder", "master": -1},
                {"id": 6, "surname": "戊", "given": "同名", "birth": 0, "root": [2000, 2000, 2000, 2000, 2000],
                 "realm": "qi_refining", "stage": 0, "sect": -1, "rank": "rogue", "master": -1},
            ],
            "tombstones": [
                {"id": 1, "name": "甲长老", "title": "", "sect": 1, "rank": "sect_master", "root_grade": "triple",
                 "realm": "golden_core", "stage": 1, "birth": -2900, "death": 59, "cause": "old_age",
                 "killer": -1, "death_event": 4, "master": -1},
                {"id": 2, "name": "乙弟子", "sect": 1, "rank": "sect_master", "root_grade": "heaven",
                 "realm": "foundation_establishment", "stage": 0, "birth": -60, "death": 70, "cause": "killed",
                 "killer": 3, "death_event": 6, "master": 1},
                {"id": 3, "name": "丙散人", "sect": 1, "rank": "inner", "root_grade": "penta",
                 "realm": "foundation_establishment", "stage": 2, "birth": -50, "death": 95, "cause": "killed",
                 "killer": 4, "death_event": 8, "master": -1},
            ],
            "chronicle": [],
        },
        "census": [
            {"day": 0, "year": -10, "population": 4, "realms": {"qi_refining": 2, "golden_core": 2}, "active_sects": 1,
             "sects": [{"id": 1, "name": "青云宗", "members": 3}]},
            {"day": 60, "year": 1, "population": 3, "realms": {"qi_refining": 2, "foundation_establishment": 1},
             "active_sects": 1, "sects": [{"id": 1, "name": "青云宗", "members": 3}]},
            {"day": 120, "year": 11, "population": 3, "realms": {"qi_refining": 1, "golden_core": 2},
             "active_sects": 1, "sects": [{"id": 1, "name": "青云宗", "members": 2}]},
        ],
        "events": events,
    }


def content() -> rep.Content:
    return rep.Content(REALMS, {"tj": {"id": "tj", "name": "太极玄功", "grade": "tian", "element": "none"}})


def render(data=None) -> tuple[rep.Run, str]:
    run = rep.Run(data or fixture(), rep.Lang(dict(LANG)), content())
    return run, rep.render_run(run)


class DateRuleTest(unittest.TestCase):
    def test_era_boundaries(self):
        self.assertEqual(rep.era_year(600, 600, 6), (False, 1))
        self.assertEqual(rep.era_year(605, 600, 6), (False, 1))
        self.assertEqual(rep.era_year(606, 600, 6), (False, 2))
        self.assertEqual(rep.era_year(599, 600, 6), (True, 1))
        self.assertEqual(rep.era_year(594, 600, 6), (True, 1))
        self.assertEqual(rep.era_year(593, 600, 6), (True, 2))
        self.assertEqual(rep.era_year(0, 600, 6), (True, 100))
        self.assertEqual(rep.era_year(2400, 600, 6), (False, 301))
        self.assertEqual(rep.era_year(-1, 600, 6), (True, 101))  # pre-genesis births
        self.assertEqual(rep.signed_year(10, 60, 6), -9)

    def test_dates_render_through_the_language_keys(self):
        run = rep.Run(fixture(), rep.Lang(dict(LANG)), content())
        self.assertEqual(run.date(59), "启元前1年")
        self.assertEqual(run.date(60), "启元1年")
        self.assertEqual(run.date(0), "启元前10年")


class FormatTest(unittest.TestCase):
    def test_positional_and_sequential_slots(self):
        self.assertEqual(rep.format_template("%2$s见%1$s，100%%", ["甲", "乙"], False), "乙见甲，100%")
        self.assertEqual(rep.format_template("%s与%s", ["甲", "乙"], False), "甲与乙")
        self.assertEqual(rep.format_template("<%1$s>", ["<b>"], True), "&lt;<b>&gt;")
        with self.assertRaises(IndexError):
            rep.format_template("%3$s", ["甲"], False)

    def test_chinese_numerals_match_the_cli(self):
        cases = {0: "零", 7: "七", 10: "十", 12: "十二", 41: "四十一", 101: "一百零一", 110: "一百一十",
                 313: "三百一十三", 1005: "一千零五", 2000: "二千", 10010: "一万零十", 123456: "十二万三千四百五十六"}
        for n, text in cases.items():
            self.assertEqual(rep.chinese_numeral(n), text, n)
        self.assertTrue(rep.is_plain_number("313"))
        self.assertFalse(rep.is_plain_number("-3"))
        self.assertFalse(rep.is_plain_number("1234567890"))
        lang = rep.Lang({"k": "坐镇%1$s已%2$s年"})
        self.assertEqual(lang.param("313"), "三百一十三")
        self.assertEqual(rep.Lang({}, chinese_numerals=False).param("313"), "313")
        data = fixture()
        data["events"].append(ev(14, 110, "genesis", 2, [4], "world_sim.event.genesis.sect",
                                 ["青云宗", "中州", "丁少年", "@world_sim.stage.golden_core.2"],
                                 text="青云宗立于中州，掌门丁少年，金丹中期修为。"))
        data["events"].append(ev(15, 111, "x", 2, [4], "world_sim.event.age", ["丁少年", "41"], text="丁少年年四十一。"))
        lang_entries = dict(LANG, **{"world_sim.event.age": "%1$s年%2$s。"})
        run = rep.Run(data, rep.Lang(lang_entries), content())
        run.check_texts()
        self.assertNotIn("文本不一致", {k for k, _ in run.oddities})
        self.assertEqual(run.event_plain(run.ev[15]), "丁少年年四十一。")

    def test_variant_suffixes_fold_together(self):
        self.assertEqual(rep.base_key("world_sim.event.stage_up.2"), "world_sim.event.stage_up")
        self.assertEqual(rep.base_key("world_sim.event.stage_up.insight"), "world_sim.event.stage_up.insight")

    def test_at_params_are_translated(self):
        run, page = render()
        self.assertIn("金丹中期修为", page)


class PageTest(unittest.TestCase):
    def test_sections_in_order(self):
        _, page = render()
        ids = ["top", "population", "sects", "lives", "drivers", "chronicle", "tombs", "checks"]
        positions = [page.find(f'id="{i}"') for i in ids]
        self.assertTrue(all(p >= 0 for p in positions), dict(zip(ids, positions)))
        self.assertEqual(positions, sorted(positions))
        self.assertIn("<footer>", page)
        self.assertIn("world_sim_cli.py run --seed 7 --tier small --years 10", page)
        self.assertIn('href="data/small_7.json"', page)
        self.assertIn("<svg", page)
        self.assertNotRegex(page, r"(src|href)=\"https?://")

    def test_every_internal_link_resolves(self):
        _, page = render()
        anchors = set(re.findall(r'id="([^"]+)"', page))
        links = re.findall(r'href="#([^"]+)"', page)
        self.assertTrue(links)
        missing = sorted({l for l in links if l not in anchors})
        self.assertEqual(missing, [])

    def test_cause_link_points_at_the_cause_with_its_text(self):
        _, page = render()
        li = re.search(r'<li id="e5".*?</li>', page, re.S).group(0)
        self.assertIn('href="#e4"', li)
        self.assertRegex(li, r'title="启元前1年：青云宗掌门甲长老寿元已尽，坐化。"')
        self.assertIn('class="because"', li)
        # A minor event that is the cause of a shown one is itself shown so the link lands.
        self.assertIn('<li id="e9"', page)
        revenge = re.search(r'<li id="e8".*?</li>', page, re.S).group(0)
        self.assertIn('href="#e6"', revenge)

    def test_prehistory_is_collapsed_and_dated(self):
        _, page = render()
        chron = page[page.find('id="chronicle"'):page.find('id="tombs"')]
        details = re.search(r"<details class=\"chron\">.*?</details>", chron, re.S).group(0)
        self.assertIn('id="e1"', details)
        self.assertIn("启元前10年", details)
        self.assertNotIn('id="e5"', details)
        self.assertIn('<div class="year" id="y1">启元1年</div>', chron)

    def test_major_events_are_heavier(self):
        _, page = render()
        self.assertRegex(page, r'<li id="e5" class="i3">')
        self.assertRegex(page, r'<li id="e6" class="i2">')

    def test_dead_names_link_to_tombstones_and_ambiguous_names_do_not(self):
        run, page = render()
        self.assertIn('<a class="pn" href="#p2">乙弟子</a>', page)
        self.assertIn('<tr id="p2">', page)
        self.assertNotIn(">戊同名</a>", page)
        self.assertNotIn("戊同名", run.name_links)

    def test_unknown_event_types_still_render(self):
        run, page = render()
        self.assertIn("天降彗星，丁少年见之。", page)
        self.assertIn("[world_sim.event.omen.rain] 血雨", page)
        kinds = {k for k, _ in run.oddities}
        self.assertIn("文本键未解析", kinds)

    def test_oddities_are_reported(self):
        data = fixture()
        data["events"].append(ev(14, 110, "succession", 2, [4], "world_sim.event.succession", ["丁少年", "青云宗", "某"],
                                 cause=999))
        run, page = render(data)
        details = [d for k, d in run.oddities if k == "因由悬空"]
        self.assertTrue(any("999" in d for d in details))
        self.assertIn("因（失载）", page)
        self.assertIn("重名", {k for k, _ in run.oddities})

    def test_biographies_and_drivers(self):
        run, page = render()
        chosen = [c["id"] for c in run.bios]
        self.assertIn(4, chosen)  # the avenger with a heaven root and a 金丹 breakthrough
        self.assertIn('id="bio4"', page)
        bio = re.search(r'<article class="bio" id="bio4">.*?</article>', page, re.S).group(0)
        self.assertIn("天灵根", bio)
        self.assertIn("为报师仇", bio)
        tables = rep.outcome_tables(run)
        golden = next(t for t in tables if t["realm"] == "golden_core")
        self.assertEqual(golden["yes"], 1)
        row = next(r for r in golden["rows"] if r["key"] == "heaven")
        self.assertEqual(row["yes"], (1, 1))

    def test_thin_run_degrades_gracefully(self):
        data = fixture()
        data["events"] = []
        data["census"] = []
        data["final_state"]["tombstones"] = []
        data["final_state"]["sects"] = []
        run, page = render(data)
        self.assertIn("尚无人陨落", page)
        self.assertIn("没有逐年人口记录", page)
        self.assertIn("没有要事以上的纪事", page)

    def test_deterministic(self):
        self.assertEqual(render()[1], render()[1])


class CliTest(unittest.TestCase):
    def test_render_only_writes_pages_and_index(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "data").mkdir()
            (tmp / "data" / "small_7.json").write_text(json.dumps(fixture(), ensure_ascii=False), encoding="utf-8")
            (tmp / "lang.json").write_text(json.dumps(LANG, ensure_ascii=False), encoding="utf-8")
            sim = tmp / "sim"
            sim.mkdir()
            (sim / "realms.json").write_text(json.dumps({"schema": 1, "realms": REALMS}), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                rc = rep.main(["--render-only", "--out-dir", str(tmp), "--lang", str(tmp / "lang.json"),
                               "--sim-data", str(sim)])
            self.assertEqual(rc, 0)
            index = (tmp / "index.html").read_text(encoding="utf-8")
            self.assertIn('href="small_7.html"', index)
            self.assertIn("小档·种子7", index)
            self.assertTrue((tmp / "small_7.html").exists())

    def test_parse_runs(self):
        self.assertEqual(rep.parse_runs(["small:1,2", "medium:3"]), [("small", 1), ("small", 2), ("medium", 3)])


if __name__ == "__main__":
    unittest.main()
