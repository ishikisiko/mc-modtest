#!/usr/bin/env python3
"""Renders the world-sim CLI's JSON dumps into static HTML chronicle pages.

    /usr/bin/python3 tools/world_sim_report.py --runs small:1,2,3 medium:1,2,3 --years 300

Each requested (tier, seed) is simulated with ``tools/world_sim_cli.py`` unless its data file already
exists for the same year count (``--force`` reruns, ``--render-only`` never runs the CLI). Every
``out/preview/world_sim/data/*.json`` then gets a ``<tier>_<seed>.html`` page and the index lists them all.

The pages are self-contained (inline CSS, inline SVG charts, a few lines of inline script that only
opens collapsed blocks when a link points into them) and deterministic: the same JSON gives the same
HTML. Unknown event types render through their text key (falling back to the dump's own ``text``) and
unknown fields are ignored, so the renderer keeps working while the core grows new mechanics.
"""
from __future__ import annotations

import argparse
import html
import json
import math
import re
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OUT_DIR = REPO / "out" / "preview" / "world_sim"
LANG_FILE = REPO / "src" / "main" / "resources" / "assets" / "myvillage" / "lang" / "zh_cn.json"
SIM_DATA_DIR = REPO / "src" / "main" / "resources" / "data" / "myvillage" / "world_sim"
CLI = REPO / "tools" / "world_sim_cli.py"
PUBLIC_BASE = "http://43.156.135.198:8766/world_sim/"

DEFAULT_REALMS = ["qi_refining", "foundation_establishment", "golden_core", "nascent_soul"]
REALM_FALLBACK_ZH = {"qi_refining": "炼气", "foundation_establishment": "筑基", "golden_core": "金丹",
                     "nascent_soul": "元婴"}
REALM_VERB = {"foundation_establishment": "筑基", "golden_core": "结丹", "nascent_soul": "成婴"}
ROOT_ORDER = ["heaven", "dual", "triple", "quad", "penta"]
ROOT_FALLBACK_ZH = {"heaven": "天灵根", "dual": "双灵根", "triple": "三灵根", "quad": "四灵根", "penta": "五灵根"}
# Realm index a root grade "should" reach (0 炼气 .. 3 元婴); reaching above it is a story.
ROOT_EXPECTED = {"heaven": 2.0, "dual": 1.5, "triple": 1.0, "quad": 0.5, "penta": 0.0}
GRADE_ZH = {"huang": "黄阶", "xuan": "玄阶", "di": "地阶", "tian": "天阶"}
TIER_ZH = {"small": "小档", "medium": "中档", "large": "大档"}
TIER_ORDER = {"small": 0, "medium": 1, "large": 2}
RANK_FALLBACK_ZH = {"sect_master": "掌门", "elder": "长老", "inner": "内门弟子", "outer": "外门弟子",
                    "rogue": "散修"}
DEATH_CAUSE_ZH = {"old_age": "坐化", "qi_deviation": "走火入魔", "killed": "被杀", "slain": "死于人手", "beast": "丧于妖兽",
                  "misadventure": "遭逢不测", "battle": "战殁", "trap": "触机关而亡", "revenge": "为仇家所杀"}
# Event type prefix -> chronicle tag. Longest prefix wins; unknown types get no tag.
TYPE_TAGS = [
    ("breakthrough_fail", "冲关"), ("breakthrough_death", "陨落"), ("breakthrough", "突破"),
    ("death", "陨落"), ("succession", "继位"), ("recruit", "收徒"), ("genesis", "宗门"),
    ("fortune", "奇遇"), ("revenge", "复仇"), ("avenge", "复仇"), ("kill", "仇杀"), ("fight", "争斗"),
    ("duel", "争斗"), ("rob", "劫掠"), ("war", "战事"), ("truce", "战事"), ("feud", "结怨"),
    ("enmity", "结怨"), ("found", "开宗"), ("split", "分裂"), ("schism", "分裂"), ("sect_extinct", "覆灭"),
    ("sect_destroy", "覆灭"), ("destroy", "覆灭"), ("annex", "吞并"), ("decline", "衰落"), ("sect", "宗门"),
    ("disciple", "师承"), ("promotion", "擢升"), ("entrant", "入道"), ("slain", "仇杀"), ("battle", "战事"),
    ("beast", "兽劫"), ("friendship", "结交"), ("quarrel", "口角"), ("travel", "游历"), ("seclusion", "闭关"),
    ("desertion", "叛离"), ("rogue_join", "投门"), ("sect_found", "开宗"), ("sect_split", "分裂"),
    ("sect_decline", "衰落"), ("sect_revival", "中兴"), ("sect_destroy", "覆灭"), ("war_end", "罢战"),
]
# Sect-timeline marker kinds, matched against the event type.
SECT_MARKERS = [
    ("succession", "succ", "◆", "掌门更替"), ("war", "war", "▲", "战事"), ("truce", "war", "▲", "战事"),
    ("feud", "feud", "▲", "结怨"), ("split", "split", "⑂", "分裂"), ("schism", "split", "⑂", "分裂"),
    ("found", "found", "●", "开宗"), ("sect_extinct", "end", "✕", "覆灭"), ("sect_destroy", "end", "✕", "覆灭"),
    ("destroy", "end", "✕", "覆灭"), ("annex", "end", "✕", "覆灭"), ("decline", "decl", "▼", "衰落"),
    ("battle", "war", "▲", "战事"), ("sect_found", "found", "●", "开宗"), ("sect_split", "split", "⑂", "分裂"),
    ("sect_decline", "decl", "▼", "衰落"), ("sect_revival", "found", "●", "中兴"),
    ("heritage_lost", "end", "◇", "传承失落"), ("heritage_rekindled", "found", "◇", "传承重续"),
]


def esc(s) -> str:
    return html.escape(str(s), quote=True)


# --------------------------------------------------------------------------------------------- dates

def era_year(day: int, prehistory_days: int, days_per_year: int) -> tuple[bool, int]:
    """(before_era, N): 启元 N 年 from the prehistory end on, 启元前 N 年 before it (design §3.1)."""
    if day >= prehistory_days:
        return False, (day - prehistory_days) // days_per_year + 1
    return True, -(-(prehistory_days - day) // days_per_year)


def signed_year(day: int, prehistory_days: int, days_per_year: int) -> int:
    before, n = era_year(day, prehistory_days, days_per_year)
    return -n if before else n


# ---------------------------------------------------------------------------------------------- lang

_FMT = re.compile(r"%(?:(\d+)\$)?([sd%])")


def format_template(template: str, args: list[str], escape_literal: bool) -> str:
    """Minecraft-style positional formatting; ``args`` are inserted as given. Raises IndexError on a
    missing slot so the caller can fall back."""
    out, pos, seq = [], 0, 0
    for m in _FMT.finditer(template):
        lit = template[pos:m.start()]
        out.append(esc(lit) if escape_literal else lit)
        pos = m.end()
        if m.group(2) == "%":
            out.append("%")
            continue
        if m.group(1):
            idx = int(m.group(1)) - 1
        else:
            idx = seq
            seq += 1
        out.append(args[idx])
    lit = template[pos:]
    out.append(esc(lit) if escape_literal else lit)
    return "".join(out)


_CN_DIGITS = "零一二三四五六七八九"
_CN_UNITS = ("", "十", "百", "千")


def chinese_numeral(n: int) -> str:
    """41 → 四十一, 105 → 一百零五, 12 → 十二; a port of the CLI's ChineseNumerals.of."""
    if n < 0:
        return "负" + chinese_numeral(-n)
    if n < 10:
        return _CN_DIGITS[n]
    if n >= 10_000:
        high, low = divmod(n, 10_000)
        return chinese_numeral(high) + "万" + ("" if low == 0 else ("零" if low < 1000 else "") + chinese_numeral(low))
    out, pending_zero, digits = [], False, str(n)
    for i, ch in enumerate(digits):
        d, unit = int(ch), len(digits) - 1 - i
        if d == 0:
            pending_zero = bool(out)
            continue
        if pending_zero:
            out.append("零")
            pending_zero = False
        if not (d == 1 and unit == 1 and not out):
            out.append(_CN_DIGITS[d])
        out.append(_CN_UNITS[unit])
    return "".join(out)


def is_plain_number(s: str) -> bool:
    """Same test as ChineseNumerals.isNumber: 1–9 ASCII digits."""
    return 0 < len(s) <= 9 and all("0" <= c <= "9" for c in s)


class Lang:
    """A language file formatted the way the CLI's Lang does it: in a Chinese file every all-digit
    literal param (ages, years, counts) is written as a Chinese numeral."""

    def __init__(self, entries: dict, chinese_numerals: bool = True):
        self.entries = {k: v for k, v in entries.items() if isinstance(v, str)}
        self.chinese_numerals = chinese_numerals
        self.missing: Counter = Counter()

    @classmethod
    def load(cls, path: Path) -> "Lang":
        return cls(json.loads(path.read_text(encoding="utf-8")), chinese_numerals=path.name.startswith("zh"))

    def get(self, key: str):
        value = self.entries.get(key)
        if value is None and key:
            self.missing[key] += 1
        return value

    def tr(self, key: str, *params, default: str | None = None) -> str:
        tpl = self.get(key)
        if tpl is None:
            return default if default is not None else f"[{key}]"
        try:
            return format_template(tpl, [str(p) for p in params], escape_literal=False)
        except IndexError:
            return tpl

    def param(self, p: str) -> str:
        if p.startswith("@"):
            v = self.get(p[1:])
            return v if v is not None else f"[{p[1:]}]"
        if self.chinese_numerals and is_plain_number(p):
            return chinese_numeral(int(p))
        return p


# ------------------------------------------------------------------------------------------- content

class Content:
    """Static sim data the report needs: realm order and lifespans, technique grades, root rules, heritages."""

    def __init__(self, realms=None, techniques=None, root_threshold=1500, root_grades=None, tiers=None,
                 heritages=None):
        self.realms = realms or [{"id": r} for r in DEFAULT_REALMS]
        self.techniques = techniques or {}
        self.heritages = heritages or {}
        self.root_threshold = root_threshold
        self.root_grades = root_grades or ROOT_ORDER
        self.tiers = tiers or {}

    @classmethod
    def load(cls, directory: Path) -> "Content":
        def read(name):
            try:
                return json.loads((directory / name).read_text(encoding="utf-8"))
            except (OSError, ValueError):
                return {}
        realms = read("realms.json").get("realms")
        techniques = {t["id"]: t for t in read("techniques.json").get("techniques", []) if "id" in t}
        rules = read("rules.json")
        roots = rules.get("roots", {})
        grades = [g.get("id") for g in roots.get("grades", [])] or None
        heritages = {h["id"]: h for h in read("heritages.json").get("heritages", []) if "id" in h}
        return cls(realms, techniques, roots.get("element_threshold_bp", 1500), grades, rules.get("tiers"),
                   heritages)


# -------------------------------------------------------------------------------------------- model

class Run:
    """One JSON dump, indexed for the page."""

    def __init__(self, data: dict, lang: Lang, content: Content, stem: str | None = None):
        self.lang, self.content = lang, content
        cfg = data.get("config") or {}
        fs = data.get("final_state") or {}
        self.seed = cfg.get("seed", fs.get("seed", 0))
        self.tier = str(cfg.get("tier", fs.get("tier", "small")))
        self.dpy = int(cfg.get("days_per_year") or fs.get("genesis_days_per_year") or 6)
        self.pre = int(cfg.get("prehistory_days", fs.get("prehistory_days", 0)) or 0)
        self.pre_years = int(cfg.get("prehistory_years", self.pre // self.dpy))
        self.final_day = int(cfg.get("final_day", fs.get("day", 0)) or 0)
        self.years = int(cfg.get("years", max(0, (self.final_day - self.pre) // self.dpy)))
        self.stem = stem or f"{self.tier}_{self.seed}"
        self.region_names = {}
        for r in (cfg.get("regions") or {}).get("regions", []) if isinstance(cfg.get("regions"), dict) else []:
            if isinstance(r, dict) and "id" in r:
                self.region_names[r["id"]] = r.get("display_name", r["id"])
        self.oddities: list[tuple[str, str]] = []

        self.realm_ids = [r.get("id") for r in content.realms]
        self.lifespan = {r.get("id"): r.get("lifespan_years") for r in content.realms}
        self.title_suffix = {r.get("id"): r.get("title_suffix") for r in content.realms}

        self.events = sorted((e for e in data.get("events") or [] if isinstance(e, dict) and "id" in e),
                             key=lambda e: e["id"])
        if not self.events:  # an older dump without the full stream: use the retained chronicle
            self.events = sorted((e for e in fs.get("chronicle") or [] if "id" in e), key=lambda e: e["id"])
        self.ev = {e["id"]: e for e in self.events}
        self.census = [c for c in data.get("census") or [] if isinstance(c, dict) and "day" in c]
        self.census.sort(key=lambda c: c["day"])

        self.sects = {}
        for s in fs.get("sects") or []:
            if "id" in s:
                self.sects[s["id"]] = s
        for c in self.census:
            for s in c.get("sects") or []:
                if s.get("id") not in self.sects:
                    self.sects[s["id"]] = {"id": s["id"], "name": s.get("name", f"宗门{s['id']}"),
                                           "founded_day": c["day"], "state": "active"}

        self.people = {}
        for p in fs.get("persons") or []:
            if "id" not in p:
                continue
            self.people[p["id"]] = {
                "id": p["id"], "name": p.get("name") or (p.get("surname", "") + p.get("given", "")),
                "alive": True, "gender": p.get("gender"), "birth": p.get("birth", 0),
                "realm": p.get("realm", "qi_refining"), "stage": p.get("stage", 0),
                "root_grade": p.get("root_grade") or self._root_grade(p.get("root")),
                "sect": p.get("sect", -1), "rank": p.get("rank", "rogue"), "master": p.get("master", -1),
                "dao_name": p.get("dao_name") or "", "title": p.get("title") or "",
                "technique": p.get("technique") or "", "relations": p.get("relations") or [],
                "kills_field": p.get("kills", 0) or 0, "death": None, "cause": None, "killer": -1,
                "death_event": -1, "region": p.get("region", ""),
            }
        for t in fs.get("tombstones") or []:
            if "id" not in t:
                continue
            if t["id"] in self.people:
                self.odd("重复人物", f"#{t['id']} 同时出现在在世名单和墓录中")
            self.people[t["id"]] = {
                "id": t["id"], "name": t.get("name", f"#{t['id']}"), "alive": False, "gender": t.get("gender"),
                "birth": t.get("birth", 0), "realm": t.get("realm", "qi_refining"), "stage": t.get("stage", 0),
                "root_grade": t.get("root_grade") or self._root_grade(t.get("root")),
                "sect": t.get("sect", -1), "rank": t.get("rank", "rogue"), "master": t.get("master", -1),
                "dao_name": t.get("dao_name") or "", "title": t.get("title") or "",
                "technique": t.get("technique") or "", "technique_grade": t.get("technique_grade") or "",
                "relations": t.get("relations") or [],
                "kills_field": t.get("kills", 0) or 0, "death": t.get("death"), "cause": t.get("cause"),
                "killer": t.get("killer", -1), "death_event": t.get("death_event", -1), "region": "",
            }
        for p in self.people.values():
            if not p["title"] and p["dao_name"]:
                suffix = self.title_suffix.get(p["realm"])
                if suffix:
                    p["title"] = p["dao_name"] + suffix

        self._index_events()
        self._check()
        self.name_links: dict[str, str] = {}
        self.bios: list[dict] = []

    # ---- helpers
    def odd(self, kind: str, detail: str):
        self.oddities.append((kind, detail))

    def _root_grade(self, root) -> str:
        if not isinstance(root, list) or not root:
            return ""
        n = max(1, sum(1 for bp in root if bp >= self.content.root_threshold))
        grades = self.content.root_grades
        return grades[min(n, len(grades)) - 1]

    def realm_idx(self, realm: str) -> int:
        return self.realm_ids.index(realm) if realm in self.realm_ids else len(self.realm_ids)

    def realm_zh(self, realm: str) -> str:
        return self.lang.entries.get(f"world_sim.realm.{realm}") or REALM_FALLBACK_ZH.get(realm, realm)

    def stage_zh(self, realm: str, stage: int) -> str:
        return self.lang.entries.get(f"world_sim.stage.{realm}.{int(stage) + 1}") or self.realm_zh(realm)

    def root_zh(self, grade: str) -> str:
        if not grade:
            return "灵根不详"
        return self.lang.entries.get(f"world_sim.root.{grade}") or ROOT_FALLBACK_ZH.get(grade, grade)

    def rank_zh(self, rank: str) -> str:
        return self.lang.entries.get(f"world_sim.rank.{rank}") or RANK_FALLBACK_ZH.get(rank, rank)

    def sect_name(self, sid) -> str:
        s = self.sects.get(sid)
        return s.get("name", f"宗门{sid}") if s else ""

    def date(self, day: int) -> str:
        before, n = era_year(day, self.pre, self.dpy)
        key = "world_sim.date.before_era" if before else "world_sim.date.era"
        return self.lang.tr(key, n, default=("启元前%d年" if before else "启元%d年") % n)

    def age(self, pid: int, day: int) -> int:
        return (day - self.people[pid]["birth"]) // self.dpy

    def person_name(self, pid) -> str:
        p = self.people.get(pid)
        return p["name"] if p else ""

    def technique_grade(self, tid: str) -> str:
        t = self.content.techniques.get(tid)
        return t.get("grade", "") if t else ""

    # ---- indexes
    def _index_events(self):
        self.by_actor = defaultdict(list)
        self.subject_of = defaultdict(list)
        self.reach_day = defaultdict(dict)  # pid -> realm idx -> day
        self.entry_day = {}
        self.fortunes = defaultdict(list)
        self.disciple_links = defaultdict(list)  # disciple -> [(day, master)]
        self.revenge_events = defaultdict(list)
        technique_names = {t.get("name"): tid for tid, t in self.content.techniques.items() if t.get("name")}
        self.fortune_techniques = defaultdict(set)
        for e in self.events:
            actors = e.get("actors") or []
            typ = str(e.get("type", ""))
            key = str(e.get("key") or e.get("textKey") or "")
            for a in dict.fromkeys(actors):
                self.by_actor[a].append(e)
            if not actors:
                continue
            subj = actors[0]
            self.subject_of[subj].append(e)
            if typ == "breakthrough" or (key.startswith("world_sim.event.breakthrough.") and "fail" not in typ
                                         and "death" not in typ):
                parts = key.split(".")
                realm = next((x for x in parts[3:] if x in self.realm_ids), None)
                if realm is not None:
                    self.reach_day[subj].setdefault(self.realm_idx(realm), e["day"])
            if typ in ("recruit", "entrant") and subj not in self.entry_day:
                self.entry_day[subj] = e["day"]
            if typ.startswith("fortune"):
                self.fortunes[subj].append(e)
                for p in e.get("params") or []:
                    if p in technique_names:
                        self.fortune_techniques[subj].add(technique_names[p])
            if typ == "disciple" and len(actors) > 1:
                self.disciple_links[subj].append((e["day"], actors[1]))
            if "revenge" in typ or "avenge" in typ or "revenge" in key:
                for a in actors:
                    self.revenge_events[a].append(e)
        self.victims = defaultdict(list)
        for p in self.people.values():
            if not p["alive"] and p["killer"] not in (-1, None):
                self.victims[p["killer"]].append(p["id"])
        self.founded = defaultdict(list)
        for s in self.sects.values():
            f = s.get("founder", -1)
            if f in self.people and s.get("founded_day", -10**9) >= 0:
                self.founded[f].append(s["id"])
        for e in self.events:
            typ = str(e.get("type", ""))
            if (typ.startswith("found") or typ.startswith("sect_found")) and e.get("actors") and e.get("sects"):
                for sid in e["sects"][:1]:
                    if sid not in self.founded[e["actors"][0]]:
                        self.founded[e["actors"][0]].append(sid)

    def realm_at(self, pid: int, day: int) -> int:
        p = self.people.get(pid)
        if p is None:
            return -1
        final = self.realm_idx(p["realm"])
        reach = self.reach_day.get(pid, {})
        for idx in range(final, 0, -1):
            if idx not in reach or reach[idx] <= day:
                return idx
        return 0

    def masters_of(self, pid: int) -> list[tuple[int, int]]:
        links = list(self.disciple_links.get(pid, []))
        m = self.people.get(pid, {}).get("master", -1)
        if m not in (-1, None) and all(mm != m for _, mm in links):
            links.append((self.entry_day.get(pid, 0), m))
        return links

    # ---- data checks (reported on the page and on stdout)
    def _check(self):
        names = Counter(p["name"] for p in self.people.values())
        for n, c in sorted(names.items()):
            if c > 1:
                ids = sorted(p["id"] for p in self.people.values() if p["name"] == n)
                self.odd("重名", f"{n} ×{c}（#{', #'.join(map(str, ids))}）")
        for p in sorted(self.people.values(), key=lambda p: p["id"]):
            end = p["death"] if p["death"] is not None else self.final_day
            age = (end - p["birth"]) / self.dpy
            life = self.lifespan.get(p["realm"])
            if age < 0:
                self.odd("年龄不合", f"{p['name']}（#{p['id']}）卒年早于生年")
            elif life and age > life + 0.01 and not self._has_bonus(p):
                self.odd("年龄不合", f"{p['name']}（#{p['id']}）{self.realm_zh(p['realm'])}寿限{life}年，"
                                     f"却活到{age:.1f}岁且无延寿记录")
            if p["death"] is not None and p["death"] > self.final_day:
                self.odd("年龄不合", f"{p['name']}（#{p['id']}）卒于模拟结束之后")
            e = self.entry_day.get(p["id"])
            if e is not None:
                ea = (e - p["birth"]) / self.dpy
                if ea < 10 or ea > 20:
                    self.odd("入道年龄", f"{p['name']}（#{p['id']}）{ea:.1f}岁入道")
            de = p["death_event"]
            if not p["alive"] and de not in (-1, None) and de not in self.ev:
                self.odd("墓录事件缺失", f"{p['name']}（#{p['id']}）的 death_event {de} 不在事件流中")
            if not p["alive"] and p["killer"] not in (-1, None) and p["killer"] not in self.people:
                self.odd("凶手不明", f"{p['name']}（#{p['id']}）的 killer #{p['killer']} 查无此人")
        last_day = None
        for e in self.events:
            c = e.get("cause", -1)
            if c not in (-1, None) and c not in self.ev:
                self.odd("因由悬空", f"事件 {e['id']}（{e.get('type')}）的 cause {c} 不存在")
            elif c not in (-1, None) and c >= e["id"]:
                self.odd("因果倒置", f"事件 {e['id']} 的 cause {c} 不早于自身")
            for a in e.get("actors") or []:
                if a not in self.people:
                    self.odd("人物不明", f"事件 {e['id']}（{e.get('type')}）的 actor #{a} 查无此人")
                    break
            for s in e.get("sects") or []:
                if s not in self.sects:
                    self.odd("宗门不明", f"事件 {e['id']} 的 sect {s} 查无此宗")
                    break
            if last_day is not None and e["day"] < last_day:
                self.odd("时序错乱", f"事件 {e['id']} 的日子 {e['day']} 早于前一事件的 {last_day}")
            last_day = e["day"]
            if e.get("actors") and e["actors"][0] in self.people:
                p = self.people[e["actors"][0]]
                if p["death"] is not None and e["day"] > p["death"] and e.get("type") not in ("succession",):
                    self.odd("死后仍有事", f"{p['name']}（#{p['id']}）卒于第{p['death']}日，"
                                        f"事件 {e['id']}（{e.get('type')}）在第{e['day']}日")

    def _has_bonus(self, p) -> bool:
        return any(str(e.get("type", "")).startswith("fortune") for e in self.by_actor.get(p["id"], []))

    # ---- text rendering
    def _param_html(self, p: str, link: bool) -> str:
        if p.startswith("@") or is_plain_number(p):
            return esc(self.lang.param(p))
        target = self.name_links.get(p) if link else None
        if target:
            return f'<a class="pn" href="{target}">{esc(p)}</a>'
        return esc(p)

    def event_html(self, e: dict, link: bool = True) -> str:
        key = e.get("key") or e.get("textKey") or ""
        params = [str(x) for x in e.get("params") or []]
        tpl = self.lang.get(key)
        if tpl is not None:
            try:
                return format_template(tpl, [self._param_html(p, link) for p in params], escape_literal=True)
            except IndexError:
                pass
        if e.get("text"):
            return esc(e["text"])
        return esc(f"[{key}] " + "、".join(self.lang.param(p) for p in params))

    def event_plain(self, e: dict) -> str:
        key = e.get("key") or e.get("textKey") or ""
        params = [str(x) for x in e.get("params") or []]
        tpl = self.lang.entries.get(key)
        if tpl is not None:
            try:
                return format_template(tpl, [self.lang.param(p) for p in params], escape_literal=False)
            except IndexError:
                pass
        if e.get("text"):
            return str(e["text"])
        return f"[{key}] " + "、".join(self.lang.param(p) for p in params)

    def check_texts(self):
        """Unresolved keys and texts that differ from the engine's own rendering."""
        bad_keys, slot_errors, mismatches = Counter(), Counter(), Counter()
        for e in self.events:
            key = e.get("key") or e.get("textKey") or ""
            tpl = self.lang.entries.get(key)
            if tpl is None:
                bad_keys[key] += 1
                continue
            params = [str(x) for x in e.get("params") or []]
            try:
                mine = format_template(tpl, [self.lang.param(p) for p in params], escape_literal=False)
            except IndexError:
                slot_errors[key] += 1
                continue
            if "[" in mine and "]" in mine and any(p.startswith("@") and p[1:] not in self.lang.entries
                                                   for p in params):
                bad_keys["(param) " + key] += 1
            if e.get("text") and e["text"] != mine:
                mismatches[key] += 1
        for k, n in sorted(bad_keys.items()):
            self.odd("文本键未解析", f"{k or '(空)'} ×{n}")
        for k, n in sorted(slot_errors.items()):
            self.odd("参数不足", f"{k} ×{n}：模板槽位多于参数")
        for k, n in sorted(mismatches.items()):
            self.odd("文本不一致", f"{k} ×{n}：按语言文件渲染与转储里的 text 不同")


# ----------------------------------------------------------------------------------- derived stats

def run_totals(run: Run) -> dict:
    era = [e for e in run.events if e["day"] >= run.pre]
    deaths = [p for p in run.people.values() if not p["alive"] and p["death"] is not None and p["death"] >= run.pre]
    causes = Counter(p["cause"] for p in deaths)
    reached = {}
    for idx in range(1, len(run.realm_ids)):
        reached[idx] = sorted(pid for pid, r in run.reach_day.items() if idx in r and r[idx] >= run.pre)
    ever = {}
    for idx in range(1, len(run.realm_ids)):
        ever[idx] = sorted(pid for pid, r in run.reach_day.items() if idx in r)
    sects_founded = [s for s in run.sects.values() if s.get("founded_day", -1) >= run.pre]
    sects_destroyed = [s for s in run.sects.values() if s.get("state") == "destroyed"
                       or (s.get("destroyed_day", -1) or -1) >= 0]
    last = run.census[-1] if run.census else {}
    years = max(1, run.years)
    return {
        "era_events": len(era),
        "imp3": sum(1 for e in era if e.get("importance") == 3),
        "imp2": sum(1 for e in era if e.get("importance") == 2),
        "imp3_per_year": sum(1 for e in era if e.get("importance") == 3) / years,
        "entrants": sum(1 for pid, d in run.entry_day.items() if d >= run.pre),
        "deaths": len(deaths), "causes": causes, "reached": reached, "ever": ever,
        "sects_founded": len(sects_founded), "sects_destroyed": len(sects_destroyed),
        "successions": sum(1 for e in era if e.get("type") == "succession"),
        "population": last.get("population", sum(1 for p in run.people.values() if p["alive"])),
        "active_sects": last.get("active_sects", sum(1 for s in run.sects.values() if s.get("state") != "destroyed")),
        "kills": sum(1 for p in deaths if p["killer"] not in (-1, None)),
        "fortunes": sum(1 for e in era if str(e.get("type", "")).startswith("fortune")),
        "final_realms": last.get("realms", {}),
    }


def story_candidates(run: Run) -> list[dict]:
    out = []
    for pid, p in run.people.items():
        evs = run.by_actor.get(pid, [])
        subj = run.subject_of.get(pid, [])
        if len(evs) < 2:
            continue
        ri = run.realm_idx(p["realm"])
        root = p["root_grade"]
        score = 0.0
        tags = []
        imp3 = sum(1 for e in subj if e.get("importance") == 3)
        imp2 = sum(1 for e in subj if e.get("importance") == 2)
        score += 3 * imp3 + 1 * imp2
        kills = max(len(run.victims.get(pid, [])), p["kills_field"])
        if kills:
            score += 2.5 * kills
            tags.append(f"手刃{kills}人")
        if not p["alive"] and p["killer"] not in (-1, None):
            score += 3
            tags.append("死于人手")
        rev = len(run.revenge_events.get(pid, []))
        enemies = sum(1 for r in p["relations"] if isinstance(r, list) and len(r) > 1 and r[1] == "enemy")
        if rev or enemies:
            score += 4 * rev + 1.5 * enemies
            tags.append("血仇")
        over = ri - ROOT_EXPECTED.get(root, 1.0)
        underdog = root in ("quad", "penta") and ri >= 2 or root == "triple" and ri >= 3
        if over > 0:
            score += 3 * over
        if underdog:
            score += 6
            tags.append(f"{run.root_zh(root)}而至{run.realm_zh(p['realm'])}")
        fallen = False
        if root in ("heaven", "dual") and not p["alive"] and ri <= ROOT_EXPECTED[root]:
            age = (p["death"] - p["birth"]) / run.dpy
            life = run.lifespan.get(p["realm"]) or 120
            if p["cause"] != "old_age" and age < 0.6 * life:
                fallen = True
                score += 7
                tags.append("天骄早陨")
        nf = len(run.fortunes.get(pid, []))
        if nf:
            score += nf + sum(1 for e in run.fortunes[pid] if e.get("importance", 1) >= 2) * 2
            tags.append(f"奇遇{nf}次")
        if run.founded.get(pid):
            score += 5
            tags.append("开宗立派")
        if any(e.get("type") == "succession" for e in subj):
            tags.append("执掌门户")
        if ri >= 2 and run.realm_idx(p["realm"]) >= 3:
            tags.append("元婴")
        # People whose whole arc is inside the record read better than genesis elders.
        if pid in run.entry_day:
            score += 1.5
        out.append({"id": pid, "score": round(score, 3), "tags": tags, "underdog": underdog,
                    "fallen": fallen, "founder": bool(run.founded.get(pid)), "killer": kills > 0 or rev > 0})
    out.sort(key=lambda c: (-c["score"], c["id"]))
    return out


def choose_bios(run: Run, lo: int = 6, hi: int = 10) -> list[dict]:
    cands = story_candidates(run)
    chosen, seen = [], set()

    def take(c):
        if c["id"] not in seen:
            seen.add(c["id"])
            chosen.append(c)

    # One slot reserved for each kind of arc the owner looks for, then fill by score.
    for flag in ("underdog", "fallen", "founder", "killer"):
        best = next((c for c in cands if c[flag] and c["id"] not in seen), None)
        if best:
            take(best)
    sect_count = Counter()
    for c in chosen:
        sect_count[run.people[c["id"]]["sect"]] += 1
    for c in cands:
        if len(chosen) >= hi:
            break
        if c["id"] in seen or c["score"] <= 0:
            continue
        sid = run.people[c["id"]]["sect"]
        if len(chosen) >= lo and sect_count[sid] >= 2:
            continue
        if sect_count[sid] >= 3:
            continue
        take(c)
        sect_count[sid] += 1
    for c in cands:  # not enough variety: relax the per-sect cap
        if len(chosen) >= lo:
            break
        take(c)
    chosen.sort(key=lambda c: (-c["score"], c["id"]))
    return chosen


def outcome_tables(run: Run) -> list[dict]:
    """Shares of drivers among those who reached a realm vs those who did not (entrants after genesis)."""
    pool = [pid for pid in run.entry_day if pid in run.people]
    feats = {}
    for pid in pool:
        p = run.people[pid]
        root = p["root_grade"]
        techs = set(run.fortune_techniques.get(pid, set()))
        if p["technique"]:
            techs.add(p["technique"])
        grades = {run.technique_grade(t) for t in techs if run.technique_grade(t)}
        if p.get("technique_grade"):
            grades.add(p["technique_grade"])
        sect = run.sects.get(p["sect"]) or {}
        sig = run.technique_grade(sect.get("signature_technique", "")) if sect else ""
        golden_master = any(run.realm_at(m, d) >= 2 for d, m in run.masters_of(pid))
        feats[pid] = {
            "heaven": root == "heaven",
            "top_root": root in ("heaven", "dual"),
            "poor_root": root in ("quad", "penta"),
            "fortune": bool(run.fortunes.get(pid)),
            "major_fortune": any(e.get("importance", 1) >= 2 for e in run.fortunes.get(pid, [])),
            "tech": (grades & {"di", "tian"} and True) if grades else None,
            "sig": (sig in ("di", "tian")) if sig else None,
            "golden_master": golden_master,
            "rogue": p["sect"] in (-1, None),
        }
    rows = [
        ("heaven", "天灵根"), ("top_root", "天灵根或双灵根"), ("poor_root", "四灵根或五灵根"),
        ("fortune", "得过奇遇"), ("major_fortune", "得过重大奇遇（要事以上）"),
        ("tech", "所修功法为地阶/天阶"), ("sig", "所属宗门镇派功法为地阶/天阶"),
        ("golden_master", "拜过金丹以上的师父"), ("rogue", "终为散修"),
    ]
    tables = []
    for idx in range(1, len(run.realm_ids)):
        yes = [pid for pid in pool if run.realm_idx(run.people[pid]["realm"]) >= idx]
        no = [pid for pid in pool if run.realm_idx(run.people[pid]["realm"]) < idx]
        if not yes and idx > 2:
            continue
        table = {"realm": run.realm_ids[idx], "yes": len(yes), "no": len(no), "rows": []}
        for key, label in rows:
            def share(group):
                vals = [feats[pid][key] for pid in group if feats[pid][key] is not None]
                return (sum(1 for v in vals if v), len(vals))
            table["rows"].append({"key": key, "label": label, "yes": share(yes), "no": share(no)})
        tables.append(table)
    return tables


# ---------------------------------------------------------------------------------------------- svg

REALM_CLASSES = 4


def nice_step(maxv: float, n: int = 5) -> float:
    if maxv <= 0:
        return 1
    raw = maxv / n
    mag = 10 ** math.floor(math.log10(raw))
    for m in (1, 2, 2.5, 5, 10):
        if raw <= m * mag:
            return m * mag
    return 10 * mag


def fmt_num(v: float) -> str:
    return str(int(v)) if float(v).is_integer() else f"{v:.1f}"


class Axis:
    """Shared x geometry for the census charts: x is years since the era start (negative = prehistory)."""

    def __init__(self, run: Run, width=760, left=52, right=70):
        self.run, self.width, self.left, self.right = run, width, left, right
        self.x0 = -run.pre / run.dpy
        self.x1 = max(self.x0 + 1, (run.final_day - run.pre) / run.dpy)

    def xpos(self, day: int) -> float:
        return (day - self.run.pre) / self.run.dpy

    def sx(self, x: float) -> float:
        return self.left + (x - self.x0) / (self.x1 - self.x0) * (self.width - self.left - self.right)

    def ticks(self) -> list[tuple[float, str]]:
        out = []
        pre = -self.x0
        if pre > 0:
            step = 50 if pre >= 100 else (25 if pre >= 50 else 10)
            k = int(pre // step) * step
            while k > 0:
                if k >= step / 2 or k == pre:
                    out.append((-k, f"前{int(k)}"))
                k -= step
        span = self.x1
        step = 50 if span >= 150 else (25 if span >= 60 else 10)
        out.append((0, "1"))
        y = step
        while y - 1 <= span:
            out.append((y - 1, str(int(y))))
            y += step
        return out


def svg_open(w, h, label) -> str:
    return (f'<svg class="chart" viewBox="0 0 {w} {h}" role="img" aria-label="{esc(label)}" '
            f'preserveAspectRatio="xMinYMin meet">')


def census_charts(run: Run) -> str:
    if len(run.census) < 2:
        return '<p class="empty">此次运行没有逐年人口记录。</p>'
    ax = Axis(run)
    W, H, top, bottom = ax.width, 250, 14, 40
    ch = H - top - bottom
    realms = list(run.realm_ids)
    for c in run.census:
        for r in (c.get("realms") or {}):
            if r not in realms:
                realms.append(r)
    pops = [c.get("population", sum((c.get("realms") or {}).values())) for c in run.census]
    target = (run.content.tiers.get(run.tier) or {}).get("population")
    ymax = max(pops + ([target] if target else [])) * 1.1
    step = nice_step(ymax)
    ymax = math.ceil(ymax / step) * step

    def sy(v):
        return top + ch - v / ymax * ch

    def frame(title):
        parts = [svg_open(W, H, title)]
        if ax.x0 < 0:
            parts.append(f'<rect class="pre" x="{ax.sx(ax.x0):.1f}" y="{top}" width="{ax.sx(0) - ax.sx(ax.x0):.1f}" '
                         f'height="{ch}"/>')
            parts.append(f'<text class="pre-label" x="{(ax.sx(ax.x0) + ax.sx(0)) / 2:.1f}" y="{top + 16}" '
                         f'text-anchor="middle">史前</text>')
        v = 0
        while v <= ymax + 1e-9:
            parts.append(f'<line class="grid" x1="{ax.left}" x2="{W - ax.right}" y1="{sy(v):.1f}" y2="{sy(v):.1f}"/>')
            parts.append(f'<text class="tick" x="{ax.left - 6}" y="{sy(v) + 4:.1f}" text-anchor="end">{fmt_num(v)}</text>')
            v += step
        for x, label in ax.ticks():
            parts.append(f'<line class="xtick" x1="{ax.sx(x):.1f}" x2="{ax.sx(x):.1f}" y1="{top + ch}" y2="{top + ch + 5}"/>')
            parts.append(f'<text class="tick" x="{ax.sx(x):.1f}" y="{top + ch + 19}" text-anchor="middle">{label}</text>')
        parts.append(f'<text class="axis-title" x="{(ax.left + W - ax.right) / 2:.1f}" y="{H - 4}" '
                     f'text-anchor="middle">启元纪年（“前”为史前）</text>')
        parts.append(f'<text class="axis-title" x="12" y="{top + ch / 2:.1f}" text-anchor="middle" '
                     f'transform="rotate(-90 12 {top + ch / 2:.1f})">人数</text>')
        return parts

    def hover(parts):
        n = len(run.census)
        for i, c in enumerate(run.census):
            x = ax.sx(ax.xpos(c["day"]))
            xl = ax.sx(ax.xpos(run.census[i - 1]["day"])) if i else x
            xr = ax.sx(ax.xpos(run.census[i + 1]["day"])) if i + 1 < n else x
            a, b = (x + xl) / 2, (x + xr) / 2
            rs = c.get("realms") or {}
            tip = f"{run.date(c['day'])}　在世{pops[i]}人　" + "　".join(
                f"{run.realm_zh(r)}{rs.get(r, 0)}" for r in realms if rs.get(r, 0)) + \
                f"　宗门{c.get('active_sects', '?')}家"
            parts.append(f'<rect class="hit" x="{a:.1f}" y="{top}" width="{max(0.5, b - a):.2f}" height="{ch}">'
                         f'<title>{esc(tip)}</title></rect>')

    # population
    p1 = frame("人口曲线")
    if target:
        p1.append(f'<line class="target" x1="{ax.left}" x2="{W - ax.right}" y1="{sy(target):.1f}" y2="{sy(target):.1f}"/>')
        p1.append(f'<text class="direct" x="{W - ax.right + 4}" y="{sy(target) + 4:.1f}">目标{target}</text>')
    pts = " ".join(f"{ax.sx(ax.xpos(c['day'])):.1f},{sy(v):.1f}" for c, v in zip(run.census, pops))
    p1.append(f'<polyline class="pop-line" points="{pts}"/>')
    p1.append(f'<text class="direct strong" x="{W - ax.right + 4}" y="{sy(pops[-1]) - 6:.1f}">在世{pops[-1]}</text>')
    hover(p1)
    p1.append("</svg>")

    # stacked realms
    p2 = frame("各境界在世人数")
    lower = [0.0] * len(run.census)
    labels = []
    for ri, r in enumerate(realms):
        vals = [(c.get("realms") or {}).get(r, 0) for c in run.census]
        if not any(vals):
            lower = [lo + v for lo, v in zip(lower, vals)]
            continue
        upper = [lo + v for lo, v in zip(lower, vals)]
        xs = [ax.sx(ax.xpos(c["day"])) for c in run.census]
        d = "M" + " L".join(f"{x:.1f},{sy(u):.1f}" for x, u in zip(xs, upper))
        d += " L" + " L".join(f"{x:.1f},{sy(lo):.1f}" for x, lo in reversed(list(zip(xs, lower))))
        cls = f"realm-{min(ri, REALM_CLASSES)}"
        p2.append(f'<path class="area {cls}" d="{d} Z"/>')
        mid = (sy(upper[-1]) + sy(lower[-1])) / 2
        labels.append((mid, f"{run.realm_zh(r)}{vals[-1]}", vals[-1]))
        lower = upper
    last_y = -100
    for mid, text, v in sorted(labels, key=lambda t: -t[0]):  # bottom band first
        y = min(mid + 4, last_y - 13) if last_y > 0 else mid + 4
        p2.append(f'<text class="direct" x="{W - ax.right + 4}" y="{y:.1f}">{esc(text)}</text>')
        last_y = y
    hover(p2)
    p2.append("</svg>")

    legend = '<div class="legend">' + "".join(
        f'<span><i class="sw realm-{min(ri, REALM_CLASSES)}"></i>{esc(run.realm_zh(r))}</span>'
        for ri, r in enumerate(realms)) + '<span><i class="sw pre"></i>史前（创世后百年）</span></div>'
    # table view of the same numbers, one row per 25 years
    rows = []
    for c in run.census:
        x = ax.xpos(c["day"])
        if abs(x - round(x)) > 1e-9 or (int(round(x)) % 25 != 0 and c is not run.census[-1]):
            continue
        rs = c.get("realms") or {}
        rows.append("<tr><td>" + esc(run.date(c["day"])) + f"</td><td>{c.get('population', '')}</td>" +
                    "".join(f"<td>{rs.get(r, 0)}</td>" for r in realms) +
                    f"<td>{c.get('active_sects', '')}</td></tr>")
    table = ('<details class="tableview"><summary>数字表（每二十五年一行）</summary><div class="scroll"><table>'
             '<thead><tr><th>年份</th><th>在世</th>' + "".join(f"<th>{esc(run.realm_zh(r))}</th>" for r in realms) +
             '<th>宗门</th></tr></thead><tbody>' + "".join(rows) + '</tbody></table></div></details>')
    return (f'<figure><figcaption>人口曲线（虚线为本档目标人数）</figcaption><div class="scroll">{"".join(p1)}</div></figure>'
            f'<figure><figcaption>各境界在世人数（堆叠）</figcaption>{legend}<div class="scroll">{"".join(p2)}</div></figure>'
            f'{table}')


def sect_timeline(run: Run) -> str:
    if not run.sects:
        return '<p class="empty">此次运行没有宗门。</p>'
    ax = Axis(run, width=760, left=118, right=16)
    members = defaultdict(dict)
    for c in run.census:
        for s in c.get("sects") or []:
            members[s.get("id")][c["day"]] = s.get("members", 0)
    maxm = max([v for m in members.values() for v in m.values()] + [1])
    order = sorted(run.sects.values(), key=lambda s: (s.get("founded_day", 0), s["id"]))
    markers = defaultdict(list)
    for e in run.events:
        typ = str(e.get("type", ""))
        kind = next((m for m in SECT_MARKERS if typ.startswith(m[0])), None)
        if kind is None or e.get("importance", 1) < 2:
            continue
        for sid in e.get("sects") or []:
            markers[sid].append((e, kind))
    row_h, top = 34, 30
    H = top + row_h * len(order) + 34
    W = ax.width
    parts = [svg_open(W, H, "宗门兴衰时间线")]
    if ax.x0 < 0:
        parts.append(f'<rect class="pre" x="{ax.sx(ax.x0):.1f}" y="{top - 6}" width="{ax.sx(0) - ax.sx(ax.x0):.1f}" '
                     f'height="{row_h * len(order) + 6}"/>')
    for x, label in ax.ticks():
        parts.append(f'<line class="grid" x1="{ax.sx(x):.1f}" x2="{ax.sx(x):.1f}" y1="{top - 6}" '
                     f'y2="{top + row_h * len(order)}"/>')
        parts.append(f'<text class="tick" x="{ax.sx(x):.1f}" y="{top - 12}" text-anchor="middle">{label}</text>')
    for i, s in enumerate(order):
        sid = s["id"]
        cy = top + row_h * i + row_h / 2
        destroyed = s.get("state") == "destroyed" or (s.get("destroyed_day", -1) or -1) >= 0
        cls = "sect-dead" if destroyed else "sect-live"
        fd = s.get("founded_day", 0)
        end = s.get("destroyed_day", -1) if destroyed and (s.get("destroyed_day", -1) or -1) >= 0 else run.final_day
        start = max(fd, 0)
        x0, x1 = ax.sx(ax.xpos(start)), ax.sx(ax.xpos(end))
        name = s.get("name", f"宗门{sid}")
        info = f"{name}：" + (f"创于{run.date(fd)}" if fd >= 0 else f"创派早于记载（{run.date(fd)}）") + \
            (f"，{run.date(end)}覆灭" if destroyed else "，至今存续")
        if s.get("heritage"):
            heritage = run.content.heritages.get(s["heritage"], {}).get("name") or s["heritage"]
            info += f"，传承{heritage}"
        parts.append(f'<g class="{cls}"><title>{esc(info)}</title>')
        parts.append(f'<text class="row-label" x="{ax.left - 8}" y="{cy + 5:.1f}" text-anchor="end">{esc(name)}</text>')
        parts.append(f'<line class="bar" x1="{x0:.1f}" x2="{x1:.1f}" y1="{cy:.1f}" y2="{cy:.1f}"/>')
        m = members.get(sid, {})
        if m:
            days = sorted(d for d in m if start <= d <= end)
            if days:
                top_pts = [(ax.sx(ax.xpos(d)), cy - 1 - m[d] / maxm * (row_h / 2 - 4)) for d in days]
                bot_pts = [(x, 2 * cy - y) for x, y in top_pts]
                d_attr = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in top_pts) + " L" + \
                    " L".join(f"{x:.1f},{y:.1f}" for x, y in reversed(bot_pts)) + " Z"
                parts.append(f'<path class="band" d="{d_attr}"/>')
        if fd < 0:
            parts.append(f'<text class="row-note" x="{x0 + 3:.1f}" y="{cy - row_h / 2 + 9:.1f}">◀ 创于{esc(run.date(fd))}</text>')
        parts.append("</g>")
        for e, (prefix, mcls, glyph, zh) in markers.get(sid, []):
            mx = ax.sx(ax.xpos(e["day"]))
            tip = f"{run.date(e['day'])}　{zh}：{run.event_plain(e)}"
            parts.append(f'<a href="#e{e["id"]}"><text class="mk mk-{mcls}" x="{mx:.1f}" y="{cy + 5:.1f}" '
                         f'text-anchor="middle">{glyph}<title>{esc(tip)}</title></text></a>')
    ly = top + row_h * len(order) + 22
    parts.append(f'<text class="axis-title" x="{(ax.left + W - ax.right) / 2:.1f}" y="{ly}" text-anchor="middle">'
                 f'启元纪年（“前”为史前）　带宽＝门人数（最多{maxm}人）</text>')
    parts.append("</svg>")
    kinds = []
    for prefix, mcls, glyph, zh in SECT_MARKERS:
        if (mcls, glyph, zh) not in kinds:
            kinds.append((mcls, glyph, zh))
    legend = '<div class="legend"><span><i class="sw sect-live-sw"></i>存续</span><span><i class="sw sect-dead-sw"></i>已覆灭</span>' + \
        "".join(f'<span><b class="mk-{c}">{g}</b> {z}</span>' for c, g, z in kinds) + '</div>'
    return f'<figure><figcaption>宗门时间线（按创派先后；标记可点，跳到纪事原文）</figcaption>{legend}<div class="scroll">{"".join(parts)}</div></figure>'


# --------------------------------------------------------------------------------------------- page

CSS = """
:root{color-scheme:light;--bg:#fcfcfb;--surface:#ffffff;--ink:#1d1c1a;--ink2:#52514e;--muted:#8a8984;
--line:#e4e2dc;--grid:#ecebe6;--accent:#8c2f1e;--accent-soft:#f6ece8;--link:#1f5fa8;--pre:#f1efe9;
--realm-0:#1baf7a;--realm-1:#2a78d6;--realm-2:#eda100;--realm-3:#4a3aa7;--realm-4:#9b9a95;
--war:#e34948;--split:#eb6834;--found:#1baf7a;--live:#2a78d6;--dead:#b9b7b0;--imp3-bg:#fbf4ee}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){color-scheme:dark;--bg:#1a1a19;--surface:#222220;
--ink:#f2f1ec;--ink2:#c3c2b7;--muted:#8f8e86;--line:#3a3a36;--grid:#2e2e2b;--accent:#e38b6f;--accent-soft:#3a2924;
--link:#7fb0ee;--pre:#262624;--realm-0:#199e70;--realm-1:#3987e5;--realm-2:#c98500;--realm-3:#9085e9;--realm-4:#6f6e69;
--war:#e66767;--split:#d95926;--found:#199e70;--live:#3987e5;--dead:#55544f;--imp3-bg:#2b2421}}
:root[data-theme="dark"]{color-scheme:dark;--bg:#1a1a19;--surface:#222220;--ink:#f2f1ec;--ink2:#c3c2b7;--muted:#8f8e86;
--line:#3a3a36;--grid:#2e2e2b;--accent:#e38b6f;--accent-soft:#3a2924;--link:#7fb0ee;--pre:#262624;--realm-0:#199e70;
--realm-1:#3987e5;--realm-2:#c98500;--realm-3:#9085e9;--realm-4:#6f6e69;--war:#e66767;--split:#d95926;--found:#199e70;
--live:#3987e5;--dead:#55544f;--imp3-bg:#2b2421}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.75 "PingFang SC","Hiragino Sans GB","Noto Sans CJK SC",
"Source Han Sans SC","Microsoft YaHei",sans-serif}
main{max-width:980px;margin:0 auto;padding:20px 16px 48px}
h1,h2,h3{font-family:"Noto Serif CJK SC","Source Han Serif SC","Songti SC","STSong",serif;line-height:1.35}
h1{font-size:28px;margin:8px 0 4px}h2{font-size:22px;margin:40px 0 10px;padding-bottom:6px;border-bottom:1px solid var(--line)}
h3{font-size:18px;margin:22px 0 6px}
a{color:var(--link);text-decoration:none}a:hover{text-decoration:underline}
.sub{color:var(--ink2);margin:0}.lead{font-size:17px}
nav.toc{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:14px;margin:14px 0 0}
.empty{color:var(--muted);font-style:italic}
figure{margin:18px 0}figcaption{font-weight:600;color:var(--ink2);font-size:15px;margin-bottom:4px}
.scroll{overflow-x:auto;-webkit-overflow-scrolling:touch}
svg.chart{display:block;width:100%;min-width:560px;height:auto;font-family:inherit}
svg .grid{stroke:var(--grid);stroke-width:1}svg .xtick{stroke:var(--muted)}
svg .tick{fill:var(--muted);font-size:12px}svg .axis-title{fill:var(--ink2);font-size:12px}
svg .pre{fill:var(--pre)}svg .pre-label{fill:var(--muted);font-size:12px}
svg .pop-line{fill:none;stroke:var(--realm-1);stroke-width:2;stroke-linejoin:round}
svg .target{stroke:var(--muted);stroke-dasharray:4 4;stroke-width:1}
svg .direct{fill:var(--ink2);font-size:12px}svg .direct.strong{fill:var(--ink);font-weight:600}
svg .area{stroke:var(--surface);stroke-width:1}
svg .hit{fill:transparent}svg .hit:hover{fill:var(--ink);fill-opacity:.07}
.realm-0{fill:var(--realm-0);background:var(--realm-0)}.realm-1{fill:var(--realm-1);background:var(--realm-1)}
.realm-2{fill:var(--realm-2);background:var(--realm-2)}.realm-3{fill:var(--realm-3);background:var(--realm-3)}
.realm-4{fill:var(--realm-4);background:var(--realm-4)}
.legend{display:flex;flex-wrap:wrap;gap:4px 14px;font-size:13px;color:var(--ink2);margin:2px 0 6px}
.legend .sw{display:inline-block;width:12px;height:12px;border-radius:3px;margin-right:5px;vertical-align:-1px}
.sw.pre{background:var(--pre);border:1px solid var(--line)}.sect-live-sw{background:var(--live)}.sect-dead-sw{background:var(--dead)}
svg .row-label{font-size:14px;fill:var(--ink)}svg .row-note{font-size:10px;fill:var(--muted)}
svg .sect-live .bar{stroke:var(--live);stroke-width:2}svg .sect-live .band{fill:var(--live);fill-opacity:.35}
svg .sect-dead .bar{stroke:var(--dead);stroke-width:2}svg .sect-dead .band{fill:var(--dead);fill-opacity:.45}
svg .sect-dead .row-label{fill:var(--muted)}
svg .mk{font-size:14px;cursor:pointer;paint-order:stroke;stroke:var(--bg);stroke-width:3px}
.mk-succ{fill:var(--ink);color:var(--ink)}.mk-war,.mk-feud{fill:var(--war);color:var(--war)}
.mk-split{fill:var(--split);color:var(--split)}.mk-found{fill:var(--found);color:var(--found)}
.mk-end{fill:var(--muted);color:var(--muted)}.mk-decl{fill:var(--muted);color:var(--muted)}
table{border-collapse:collapse;font-size:14px;width:100%}
th,td{border-bottom:1px solid var(--line);padding:5px 8px;text-align:left;vertical-align:top}
th{color:var(--ink2);font-weight:600;white-space:nowrap}
td.num,th.num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
details>summary{cursor:pointer;color:var(--ink2);font-weight:600;margin:8px 0}
.tableview table{font-size:13px}
.bio{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:14px 16px;margin:14px 0}
.bio h3{margin:0 0 4px}.bio .facts{font-size:14px;color:var(--ink2);margin:0 0 8px}
.tag{display:inline-block;font-size:12px;line-height:1.6;padding:0 7px;border-radius:9px;background:var(--accent-soft);
color:var(--accent);margin:0 4px 2px 0}
.bio p.story{margin:6px 0}
.evlist{list-style:none;padding:0;margin:4px 0 0;font-size:14px}
.evlist li{padding:2px 0 2px 7.5em;text-indent:-7.5em}
.evlist .d{display:inline-block;width:7.5em;text-indent:0;color:var(--muted);font-variant-numeric:tabular-nums}
.evlist li.i3{font-weight:600}.evlist li.i1{color:var(--ink2)}
.bars{display:inline-block;width:70px;height:8px;background:var(--grid);border-radius:4px;vertical-align:1px;margin-left:6px;overflow:hidden}
.bars i{display:block;height:100%;background:var(--realm-1);border-radius:4px}
.bars.no i{background:var(--muted)}
.chron .year{margin:18px 0 4px;font-family:"Noto Serif CJK SC","Source Han Serif SC","Songti SC",serif;
font-size:17px;color:var(--accent);border-bottom:1px dashed var(--line)}
.chron ol{list-style:none;padding:0;margin:0}
.chron li{padding:5px 0 5px 10px;border-left:3px solid transparent;margin:2px 0}
.chron li.i3{font-weight:600;font-size:17px;border-left-color:var(--accent);background:var(--imp3-bg);border-radius:0 6px 6px 0}
.chron li.i1{color:var(--ink2);font-size:14px}
.chron li:target{outline:2px solid var(--link);outline-offset:2px}
.chron .t{font-size:11px;font-weight:400;color:var(--muted);border:1px solid var(--line);border-radius:4px;padding:0 4px;
margin-right:6px;vertical-align:2px;white-space:nowrap}
.chron .yin{font-size:12px;font-weight:600;color:var(--accent);border:1px solid var(--accent);border-radius:4px;padding:0 4px;
margin-left:6px;vertical-align:2px}
.chron .because{display:block;font-size:12.5px;font-weight:400;color:var(--muted);line-height:1.6;margin-top:1px}
.chron .because a{color:var(--muted);text-decoration:underline dotted}
a.pn{color:inherit;text-decoration:underline dotted;text-underline-offset:3px}
a.pn:hover{color:var(--link)}
.tomb tr:target{background:var(--accent-soft)}
.tomb td{font-size:13px}
.checks li{font-size:13px;color:var(--ink2)}
footer{margin-top:48px;padding-top:12px;border-top:1px solid var(--line);font-size:13px;color:var(--ink2)}
code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:12.5px;background:var(--grid);padding:1px 4px;border-radius:3px;
word-break:break-all}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px;margin:18px 0}
.card{display:block;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:14px 16px;color:var(--ink)}
.card:hover{border-color:var(--link);text-decoration:none}
.card h3{margin:0 0 6px}.card dl{display:grid;grid-template-columns:auto 1fr;gap:2px 12px;margin:8px 0 0;font-size:14px}
.card dt{color:var(--ink2)}.card dd{margin:0;font-variant-numeric:tabular-nums}
.card svg{width:100%;height:40px;display:block;margin-top:8px}
.card svg polyline{fill:none;stroke:var(--realm-1);stroke-width:1.5}
@media (max-width:600px){body{font-size:15px}h1{font-size:23px}h2{font-size:19px}.chron li.i3{font-size:16px}
.evlist li{padding-left:0;text-indent:0}.evlist .d{display:block;width:auto}
.drivers .bars{display:none}.drivers td,.drivers th{padding:5px 4px}.drivers td:first-child{min-width:7.5em}}
"""

SCRIPT = """<script>
(function(){function show(id){var t=document.getElementById(id);if(!t)return;
for(var p=t.parentElement;p;p=p.parentElement){if(p.tagName==='DETAILS')p.open=true;}t.scrollIntoView({block:'center'});}
function fromHash(){if(location.hash.length>1)show(decodeURIComponent(location.hash.slice(1)));}
document.addEventListener('click',function(ev){var a=ev.target.closest&&ev.target.closest('a[href^="#"]');
if(!a)return;var id=a.getAttribute('href').slice(1);if(!document.getElementById(id))return;
ev.preventDefault();history.pushState(null,'','#'+id);show(id);});
window.addEventListener('hashchange',fromHash);fromHash();})();
</script>"""


def page(title: str, body: str) -> str:
    return ("<!doctype html>\n<html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            f"<title>{esc(title)}</title><style>{CSS}</style></head><body><main>\n{body}\n</main>{SCRIPT}</body></html>\n")


def tier_zh(run_or_tier) -> str:
    tier = run_or_tier.tier if isinstance(run_or_tier, Run) else run_or_tier
    return TIER_ZH.get(tier, tier)


def cli_command(tier: str, seed, years: int) -> str:
    return (f"/usr/bin/python3 tools/world_sim_cli.py run --seed {seed} --tier {tier} --years {years} "
            f"--out out/preview/world_sim/data/{tier}_{seed}.json")


def report_command(tier: str, seed, years: int) -> str:
    return f"/usr/bin/python3 tools/world_sim_report.py --runs {tier}:{seed} --years {years}"


def header_section(run: Run, tot: dict) -> str:
    title = run.lang.entries.get("world_sim.report.title", "命簿·天下纪事")
    causes = tot["causes"]
    cause_txt = "、".join(f"{DEATH_CAUSE_ZH.get(c, c)}{n}" for c, n in sorted(causes.items(), key=lambda t: (-t[1], str(t[0]))))
    reach = []
    for idx, pids in tot["reached"].items():
        if idx >= 2:
            reach.append(f"{REALM_VERB.get(run.realm_ids[idx], '晋' + run.realm_zh(run.realm_ids[idx]))}{len(pids)}人")
    fe = tot["reached"].get(1, [])
    final = tot["final_realms"]
    final_txt = "、".join(f"{run.realm_zh(r)}{final[r]}" for r in run.realm_ids if final.get(r))
    sentence = (f"启元以来{run.years}年，共记事{tot['era_events']}条，其中大事{tot['imp3']}条（每年{tot['imp3_per_year']:.2f}条）、"
                f"要事{tot['imp2']}条。新入道者{tot['entrants']}人，陨落{tot['deaths']}人"
                + (f"（{cause_txt}）" if cause_txt else "") + "；"
                f"筑基{len(fe)}人" + ("，" + "，".join(reach) if reach else "") + "；"
                f"掌门更替{tot['successions']}次，新立宗门{tot['sects_founded']}家，覆灭{tot['sects_destroyed']}家"
                + (f"；奇遇{tot['fortunes']}次" if tot['fortunes'] else "")
                + (f"；死于人手者{tot['kills']}人" if tot['kills'] else "") + "。"
                f"终局在世{tot['population']}人" + (f"（{final_txt}）" if final_txt else "") +
                f"，宗门存续{tot['active_sects']}家。")
    cfg = (f"{tier_zh(run)}　种子{run.seed}　一年{run.dpy}日　史前{run.pre_years}年（{run.pre}日），其后{run.years}年"
           f"　终于第{run.final_day}日（{run.date(run.final_day)}）")
    toc = ('<nav class="toc"><a href="index.html">← 全部运行</a><a href="#population">人口与境界</a><a href="#sects">宗门兴衰</a>'
           '<a href="#lives">列传</a><a href="#drivers">成败之由</a><a href="#chronicle">纪事</a><a href="#tombs">陨落名录</a>'
           '<a href="#checks">数据自检</a></nav>')
    return (f'<header id="top"><h1>{esc(title)}　<small>{esc(tier_zh(run))}·种子{esc(run.seed)}</small></h1>'
            f'<p class="sub">{esc(cfg)}</p><p class="lead">{esc(sentence)}</p>{toc}</header>')


def bio_section(run: Run) -> str:
    if not run.bios:
        return '<p class="empty">此次运行事件太少，挑不出代表人物。</p>'
    out = ['<p class="sub">按“故事分”挑选：大事数、仇杀与复仇、境界高出灵根预期、天骄早陨、开宗立派。'
           '每人一段由事件生成的摘要，下附全部纪事。</p>']
    for c in run.bios:
        out.append(bio_card(run, c))
    return "".join(out)


def _link_person(run: Run, pid: int) -> str:
    p = run.people.get(pid)
    if not p:
        return ""
    target = run.name_links.get(p["name"])
    return f'<a class="pn" href="{target}">{esc(p["name"])}</a>' if target else esc(p["name"])


def cn(n: int) -> str:
    """Counts and ages inside biography prose, written like the engine's own chronicle text."""
    return chinese_numeral(int(n))


def _sentence(text: str) -> str:
    return text if text.endswith(("。", "！", "？", "」", "”")) else text + "。"


def bio_card(run: Run, cand: dict) -> str:
    pid = cand["id"]
    p = run.people[pid]
    evs = run.by_actor.get(pid, [])
    subj = run.subject_of.get(pid, [])
    s = []  # sentences, already HTML
    name = esc(p["name"]) + (f"，号{esc(p['title'])}" if p["title"] else "")
    s.append(f"{name}，{esc(run.root_zh(p['root_grade']))}，生于{esc(run.date(p['birth']))}。")
    beats = []  # (day, order, html) told in date order

    def when(day):
        return f"{esc(run.date(day))}（{cn(run.age(pid, day))}岁）"

    entry = run.entry_day.get(pid)
    first_sect = next((e for e in subj if e.get("type") == "recruit"), None)
    if first_sect is not None:
        sect = first_sect.get("sects") or [-1]
        beats.append((first_sect["day"], 0, f"{when(first_sect['day'])}拜入{esc(run.sect_name(sect[0]))}。"))
    elif entry is not None:
        beats.append((entry, 0, f"{when(entry)}以散修之身踏入仙途。"))
    else:
        s.append("创世之时已在世间修行。")
    for day, m in run.masters_of(pid)[:2]:
        if m in run.people:
            mr = run.realm_at(m, day)
            mz = run.realm_zh(run.realm_ids[mr]) if 0 <= mr < len(run.realm_ids) else ""
            beats.append((day, 1, f"拜{_link_person(run, m)}为师（时为{esc(mz)}）。"))
    for e in run.fortunes.get(pid, [])[:4]:
        beats.append((e["day"], 2, f"{esc(run.date(e['day']))}，{run.event_html(e)}"))
    for e in subj:
        typ = str(e.get("type", ""))
        key = str(e.get("key", ""))
        if typ == "breakthrough":
            realm = next((x for x in key.split(".")[3:] if x in run.realm_ids), None)
            if realm is None:
                continue
            how = ""
            variant = set(key.split(".")[3:])
            if "sect_pill" in variant:
                how = "，得宗门赐丹之助"
            elif "desperate" in variant:
                how = "，乃寿元将尽强行冲关而成"
            elif any(v.startswith("fortune") for v in variant):
                how = "，全凭机缘"
            if e.get("cause", -1) not in (-1, None) and e["cause"] in run.ev:
                how += f"（起因：{esc(run.event_plain(run.ev[e['cause']]).rstrip('。'))}）"
            beats.append((e["day"], 3, f"{when(e['day'])}{REALM_VERB.get(realm, '晋' + run.realm_zh(realm))}{how}。"))
        elif typ == "succession":
            beats.append((e["day"], 4, f"{esc(run.date(e['day']))}，{run.event_html(e)}"))
    for sid in run.founded.get(pid, []):
        fd = (run.sects.get(sid) or {}).get("founded_day", 0)
        beats.append((fd, 4, f"{esc(run.date(fd))}开创{esc(run.sect_name(sid))}。"))
    for e in [e for e in run.revenge_events.get(pid, []) if e["id"] != p["death_event"]][:3]:
        beats.append((e["day"], 5, f"{esc(run.date(e['day']))}，{run.event_html(e)}"))
    for v in run.victims.get(pid, [])[:4]:
        vp = run.people[v]
        de = run.ev.get(vp["death_event"])
        text = run.event_html(de) if de else f"手刃{_link_person(run, v)}。"
        beats.append((vp["death"] or 0, 5, f"{esc(run.date(vp['death'] or 0))}，{text}"))
    beats.sort(key=lambda b: (b[0], b[1]))
    seen_text = set()
    for b in beats:  # a revenge kill can arrive both as a revenge event and as a victim's death
        if b[2] not in seen_text:
            seen_text.add(b[2])
            s.append(_sentence(b[2]))
    if len(run.fortunes.get(pid, [])) > 4:
        s.append(f"一生奇遇凡{cn(len(run.fortunes[pid]))}次。")
    victims = run.victims.get(pid, [])
    if len(victims) > 4:
        s.append(f"手下亡魂共{cn(len(victims))}人。")
    fails = sum(1 for e in subj if str(e.get("type", "")).startswith("breakthrough_fail"))
    if fails:
        s.append(f"一生冲关受挫凡{cn(fails)}次。")
    disciples = sorted({e["actors"][0] for e in evs if e.get("type") == "disciple" and len(e.get("actors") or []) > 1
                        and e["actors"][1] == pid})
    if disciples:
        s.append(f"门下亲传弟子{cn(len(disciples))}人。")
    if p["alive"]:
        sect = run.sect_name(p["sect"])
        role = (sect + run.rank_zh(p["rank"])) if sect else run.rank_zh("rogue")
        s.append(f"至今在世，为{esc(role)}，{esc(run.stage_zh(p['realm'], p['stage']))}修为，"
                 f"年{cn(run.age(pid, run.final_day))}。")
    else:
        de = run.ev.get(p["death_event"])
        how = run.event_html(de) if de else esc(DEATH_CAUSE_ZH.get(p["cause"], p["cause"] or "卒"))
        killer = ""
        if p["killer"] not in (-1, None) and p["killer"] in run.people and not de:
            killer = f"，死于{_link_person(run, p['killer'])}之手"
        # The engine's death texts often state the age already (年四十一, 寿五百); do not repeat it.
        told_age = de is not None and any(is_plain_number(str(x)) for x in de.get("params") or [])
        tail = "" if told_age else f"享年{cn(run.age(pid, p['death']))}。"
        if tail and not how.endswith("。"):
            tail = "，" + tail
        s.append(_sentence(f"{esc(run.date(p['death']))}，{how}{killer}{tail}"))
    facts = (f"{esc(run.root_zh(p['root_grade']))} → {esc(run.stage_zh(p['realm'], p['stage']))}"
             f"　{esc(run.sect_name(p['sect']) or '散修')}"
             f"　{'在世' if p['alive'] else '已故'}　故事分 {cand['score']:g}")
    tags = "".join(f'<span class="tag">{esc(t)}</span>' for t in cand["tags"])
    items = collapsed_events(run, evs)
    anchor = f' id="bio{pid}"'
    return (f'<article class="bio"{anchor}><h3>{esc(p["name"])}'
            + (f' <small>{esc(p["title"])}</small>' if p["title"] else "") +
            f'</h3><p class="facts">{facts}</p><div>{tags}</div><p class="story">{"".join(s)}</p>'
            f'<details><summary>生平纪事 {len(evs)} 条</summary><ul class="evlist">{"".join(items)}</ul></details></article>')


def base_key(key) -> str:
    """A text key without its trailing wording-variant number (``...stage_up.2`` → ``...stage_up``)."""
    return re.sub(r"\.\d+$", "", str(key or ""))


def collapsed_events(run: Run, evs: list[dict]) -> list[str]:
    """Runs of three or more routine events with the same text key fold into one line."""
    out, i = [], 0
    tpl = run.lang.entries.get("world_sim.report.bio.repeat")
    while i < len(evs):
        e = evs[i]
        j = i
        key = base_key(e.get("key"))
        while j + 1 < len(evs) and base_key(evs[j + 1].get("key")) == key and evs[j + 1].get("importance", 1) == 1 \
                and e.get("importance", 1) == 1:
            j += 1
        anchor = f'<a href="#e{e["id"]}">' if e.get("importance", 1) >= 2 else ""
        if j - i >= 2:
            last = evs[j]
            text = run.event_html(last)
            if tpl:
                line = format_template(tpl, [esc(run.date(e["day"])), esc(run.date(last["day"])), text, cn(j - i + 1)],
                                       escape_literal=True)
            else:
                line = f"{esc(run.date(e['day']))}至{esc(run.date(last['day']))}　{text}（如是者凡{cn(j - i + 1)}次）"
            out.append(f'<li class="i1"><span class="d">…</span>{line}</li>')
        else:
            for k in range(i, j + 1):
                ek = evs[k]
                imp = ek.get("importance", 1)
                date = esc(run.date(ek["day"]))
                d = f'<a href="#e{ek["id"]}">{date}</a>' if imp >= 2 else date
                out.append(f'<li class="i{imp}"><span class="d">{d}</span>{run.event_html(ek)}</li>')
        i = j + 1
    return out


def drivers_section(run: Run) -> str:
    tables = outcome_tables(run)
    if not tables or not any(t["yes"] or t["no"] for t in tables):
        return '<p class="empty">创世之后无人入道，无从比较。</p>'
    out = ['<p class="sub">只计创世之后入道的人（他们的出身、师承和机缘都有记录）。左列为达到该境界者，右列为未达到者；'
           '每格是“具备该条件的人数／有记录的人数”。尚在世、仍有机会的人也算在“未达到”里。'
           '“拜过金丹以上的师父”按拜师当时师父的境界计；功法按在世者现修、逝者临终所修以及奇遇所得的功法计。</p>']
    for t in tables:
        realm = run.realm_zh(t["realm"])
        out.append(f'<h3>达到{esc(realm)}：{t["yes"]}人　未达到：{t["no"]}人</h3>')
        if not t["yes"]:
            out.append(f'<p class="empty">无人达到{esc(realm)}。</p>')
            continue
        rows = []
        for r in t["rows"]:
            cells = []
            ratios = []
            for side in ("yes", "no"):
                k, n = r[side]
                if n == 0:
                    cells.append('<td class="num">—</td>')
                    ratios.append(None)
                    continue
                pct = k / n * 100
                ratios.append(pct)
                cells.append(f'<td class="num">{pct:.0f}%　<small>{k}/{n}</small>'
                             f'<span class="bars{" no" if side == "no" else ""}"><i style="width:{pct:.0f}%"></i></span></td>')
            a, b = ratios
            if a is None or b is None:
                lift = "—"
            elif b == 0:
                lift = "∞" if a > 0 else "—"
            else:
                lift = f"{a / b:.1f}×"
            rows.append(f'<tr><td>{esc(r["label"])}</td>{"".join(cells)}<td class="num">{lift}</td></tr>')
        out.append('<div class="scroll"><table class="drivers"><thead><tr><th>条件</th>'
                   f'<th class="num">达到{esc(realm)}者</th><th class="num">未达到者</th><th class="num">倍数</th>'
                   f'</tr></thead><tbody>{"".join(rows)}</tbody></table></div>')
    return "".join(out)


def tag_for(typ: str) -> str:
    best = ""
    tag = ""
    for prefix, zh in TYPE_TAGS:
        if typ.startswith(prefix) and len(prefix) > len(best):
            best, tag = prefix, zh
    return tag


def chronicle_section(run: Run) -> str:
    shown = {e["id"] for e in run.events if e.get("importance", 1) >= 2}
    # Causes of shown events are shown too (as minor lines) so every 因 link lands somewhere.
    frontier = list(shown)
    while frontier:
        nxt = []
        for eid in frontier:
            c = run.ev[eid].get("cause", -1)
            if c in run.ev and c not in shown:
                shown.add(c)
                nxt.append(c)
        frontier = nxt
    if not shown:
        return '<p class="empty">此次运行没有要事以上的纪事。</p>'
    by_year = defaultdict(list)
    for eid in sorted(shown):
        e = run.ev[eid]
        by_year[signed_year(e["day"], run.pre, run.dpy)].append(e)

    def li(e):
        imp = e.get("importance", 1)
        tag = tag_for(str(e.get("type", "")))
        parts = [f'<li id="e{e["id"]}" class="i{min(max(imp, 1), 3)}">']
        if tag:
            parts.append(f'<span class="t">{esc(tag)}</span>')
        parts.append(run.event_html(e))
        c = e.get("cause", -1)
        if c not in (-1, None):
            ce = run.ev.get(c)
            if ce is not None:
                ctext = run.event_plain(ce)
                parts.append(f'<a class="yin" href="#e{c}" title="{esc(run.date(ce["day"]) + "：" + ctext)}">因</a>')
                parts.append(f'<span class="because">因：<a href="#e{c}">{esc(run.date(ce["day"]))}</a>　{esc(ctext)}</span>')
            else:
                parts.append('<span class="yin" title="起因事件未见于记录">因（失载）</span>')
        if imp < 2:
            parts.append('<span class="because">（前因，寻常小事）</span>')
        parts.append("</li>")
        return "".join(parts)

    def block(years):
        out = []
        for y in years:
            evs = by_year[y]
            label = run.date(evs[0]["day"])
            out.append(f'<div class="year" id="y{y}">{esc(label)}</div><ol>' + "".join(li(e) for e in evs) + "</ol>")
        return "".join(out)

    pre_years = sorted(y for y in by_year if y < 0)
    era_years = sorted(y for y in by_year if y > 0)
    out = ['<p class="sub">要事（重要度2）与大事（重要度3，加粗）按年排列。带“因”的事件可点击跳到起因，'
           '起因原文也以小字列在下方；已故者的名字可点，跳到陨落名录。</p>']
    if pre_years:
        n = sum(len(by_year[y]) for y in pre_years)
        out.append(f'<details class="chron"><summary>史前（{esc(run.date(0))}至{esc(run.date(run.pre - 1))}，{n}条）</summary>'
                   f'{block(pre_years)}</details>')
    if era_years:
        out.append(f'<div class="chron">{block(era_years)}</div>')
    else:
        out.append('<p class="empty">启元以来尚无要事。</p>')
    return "".join(out)


def tomb_section(run: Run) -> str:
    dead = sorted((p for p in run.people.values() if not p["alive"]), key=lambda p: (p["death"] or 0, p["id"]))
    if not dead:
        return '<p class="empty">尚无人陨落。</p>'
    rows = []
    for p in dead:
        de = run.ev.get(p["death_event"])
        if de is not None:
            how = run.event_html(de, link=False)
            if de.get("importance", 1) >= 2:
                how = f'<a href="#e{de["id"]}">{how}</a>'
        else:
            how = esc(DEATH_CAUSE_ZH.get(p["cause"], p["cause"] or ""))
        killer = _link_person(run, p["killer"]) if p["killer"] not in (-1, None) else ""
        name = esc(p["name"]) + (f"<br><small>{esc(p['title'])}</small>" if p["title"] else "")
        if run.name_links.get(p["name"], "").startswith("#bio"):
            name = f'<a href="#bio{p["id"]}">{name}</a>'
        elif any(b["id"] == p["id"] for b in run.bios):
            name = f'<a href="#bio{p["id"]}">{name}</a>'
        rows.append(f'<tr id="p{p["id"]}"><td>{name}</td><td>{esc(run.sect_name(p["sect"]) or "散修")}'
                    f'<br><small>{esc(run.rank_zh(p["rank"]))}</small></td><td>{esc(run.root_zh(p["root_grade"]))}</td>'
                    f'<td>{esc(run.stage_zh(p["realm"], p["stage"]))}</td>'
                    f'<td class="num">{esc(run.date(p["birth"]))}<br>{esc(run.date(p["death"]))}</td>'
                    f'<td class="num">{run.age(p["id"], p["death"])}</td><td>{how}</td><td>{killer}</td></tr>')
    return (f'<details open><summary>共{len(dead)}人，按卒年排列</summary><div class="scroll"><table class="tomb">'
            '<thead><tr><th>姓名</th><th>宗门</th><th>灵根</th><th>境界</th><th class="num">生／卒</th>'
            '<th class="num">享年</th><th>死因</th><th>凶手</th></tr></thead><tbody>' + "".join(rows) +
            '</tbody></table></div></details>')


def checks_section(run: Run) -> str:
    if not run.oddities:
        return '<p class="sub">未发现异常：年龄、重名、因由、文本键都对得上。</p>'
    groups = defaultdict(list)
    for kind, detail in run.oddities:
        groups[kind].append(detail)
    out = ['<details><summary>发现 %d 处可疑（给开发者看的）</summary><ul class="checks">' % len(run.oddities)]
    for kind in sorted(groups):
        items = groups[kind]
        shown = items[:12]
        more = f"……另有{len(items) - 12}条" if len(items) > 12 else ""
        out.append(f"<li><b>{esc(kind)}</b>（{len(items)}）：" + "；".join(esc(x) for x in shown) + esc(more) + "</li>")
    out.append("</ul></details>")
    return "".join(out)


def build_name_links(run: Run):
    dead_names = Counter(p["name"] for p in run.people.values() if not p["alive"])
    all_names = Counter(p["name"] for p in run.people.values())
    links = {}
    for p in run.people.values():
        if all_names[p["name"]] != 1:
            continue  # ambiguous: no link
        if not p["alive"] and dead_names[p["name"]] == 1:
            links[p["name"]] = f"#p{p['id']}"
    for c in run.bios:
        p = run.people[c["id"]]
        if p["alive"] and all_names[p["name"]] == 1:
            links[p["name"]] = f"#bio{p['id']}"
    run.name_links = links


def render_run(run: Run) -> str:
    run.check_texts()
    run.bios = choose_bios(run)
    build_name_links(run)
    tot = run_totals(run)
    body = [
        header_section(run, tot),
        '<section id="population"><h2>人口与境界</h2>', census_charts(run), "</section>",
        '<section id="sects"><h2>宗门兴衰</h2>', sect_timeline(run), "</section>",
        '<section id="lives"><h2>列传·代表人物</h2>', bio_section(run), "</section>",
        '<section id="drivers"><h2>成败之由</h2>', drivers_section(run), "</section>",
        '<section id="chronicle"><h2>纪事</h2>', chronicle_section(run), "</section>",
        '<section id="tombs"><h2>陨落名录</h2>', tomb_section(run), "</section>",
        '<section id="checks"><h2>数据自检</h2>', checks_section(run), "</section>",
        f'<footer><p>本页由以下命令生成：<br><code>{esc(cli_command(run.tier, run.seed, run.years))}</code><br>'
        f'<code>{esc(report_command(run.tier, run.seed, run.years))}</code></p>'
        f'<p>数据文件：<a href="data/{esc(run.stem)}.json">data/{esc(run.stem)}.json</a>　'
        f'<a href="index.html">返回全部运行</a></p></footer>',
    ]
    return page(f"命簿·{tier_zh(run)}·种子{run.seed}", "".join(body))


def render_index(runs: list[Run]) -> str:
    cards = []
    for run in runs:
        tot = run_totals(run)
        gold = len(tot["ever"].get(2, [])) if len(run.realm_ids) > 2 else 0
        soul = len(tot["ever"].get(3, [])) if len(run.realm_ids) > 3 else 0
        pops = [c.get("population", 0) for c in run.census]
        spark = ""
        if len(pops) > 1:
            mx = max(pops) or 1
            pts = " ".join(f"{i / (len(pops) - 1) * 300:.1f},{38 - v / mx * 34:.1f}" for i, v in enumerate(pops))
            spark = f'<svg viewBox="0 0 300 40" preserveAspectRatio="none" aria-label="人口曲线"><polyline points="{pts}"/></svg>'
        cards.append(
            f'<a class="card" href="{esc(run.stem)}.html"><h3>{esc(tier_zh(run))}·种子{esc(run.seed)}</h3>'
            f'<div class="sub">{run.years}年（史前{run.pre_years}年）　一年{run.dpy}日</div><dl>'
            f'<dt>终局人口</dt><dd>{tot["population"]}</dd>'
            f'<dt>宗门</dt><dd>存续{tot["active_sects"]}　新立{tot["sects_founded"]}　覆灭{tot["sects_destroyed"]}</dd>'
            f'<dt>曾至金丹／元婴</dt><dd>{gold}人／{soul}人</dd>'
            f'<dt>大事／年</dt><dd>{tot["imp3_per_year"]:.2f}（共{tot["imp3"]}条）</dd>'
            f'<dt>掌门更替</dt><dd>{tot["successions"]}次</dd></dl>{spark}</a>')
    if not cards:
        cards.append('<p class="empty">data/ 目录下还没有运行数据。</p>')
    body = (
        '<header><h1>命簿·天下纪事</h1><p class="lead">世界模拟三百年的推演记录。每张卡片是一次运行（规模档×随机种子），'
        '点开看这一方天下的兴衰。</p></header>'
        f'<div class="cards">{"".join(cards)}</div>'
        '<h2>每页有什么</h2><ul>'
        '<li><b>人口与境界</b>：逐年在世人数（灰底为史前百年）和各境界人数堆叠图，鼠标停留可看当年数字。</li>'
        '<li><b>宗门兴衰</b>：每个宗门一行，带宽是门人数；◆掌门更替、▲战事／结怨、⑂分裂、●开宗、✕覆灭，点标记跳到纪事。</li>'
        '<li><b>列传</b>：按故事分挑出六到十人，一段摘要加全部纪事，标出灵根与最终境界，看“灵根不是命”。</li>'
        '<li><b>成败之由</b>：达到筑基／金丹的人与未达到的人相比，灵根、奇遇、功法、师承各占几成。</li>'
        '<li><b>纪事</b>：要事与大事逐年排列，大事加粗；“因”字可点，跳到起因，起因原文以小字附在下方。</li>'
        '<li><b>陨落名录</b>：所有逝者，纪事里的名字可点到这里。</li>'
        '<li><b>数据自检</b>：给开发者看的异常清单（重名、年龄、悬空因由、未解析的文本键）。</li></ul>'
        f'<footer><p>生成命令：<code>/usr/bin/python3 tools/world_sim_report.py --runs small:1,2,3 medium:1,2,3 --years 300</code></p>'
        f'<p>公开地址：<a href="{PUBLIC_BASE}index.html">{PUBLIC_BASE}index.html</a></p></footer>'
    )
    return page("命簿·天下纪事", body)


# ---------------------------------------------------------------------------------------------- cli

def parse_runs(specs: list[str]) -> list[tuple[str, int]]:
    out = []
    for spec in specs:
        tier, _, seeds = spec.partition(":")
        if not tier or not seeds:
            raise SystemExit(f"bad --runs entry {spec!r}; expected tier:seed[,seed...]")
        for s in seeds.split(","):
            if s.strip():
                out.append((tier.strip(), int(s)))
    return out


def run_sort_key(path: Path):
    tier, _, seed = path.stem.rpartition("_")
    return (TIER_ORDER.get(tier, 9), tier, int(seed) if seed.isdigit() else 0, path.stem)


def ensure_data(tier: str, seed: int, years: int, data_dir: Path, force: bool) -> Path:
    out = data_dir / f"{tier}_{seed}.json"
    if out.exists() and not force:
        try:
            cfg = json.loads(out.read_text(encoding="utf-8")).get("config", {})
            if int(cfg.get("years", years)) == years:
                print(f"reuse {out}")
                return out
        except (OSError, ValueError):
            pass
    data_dir.mkdir(parents=True, exist_ok=True)
    cmd = [sys.executable, str(CLI), "run", "--seed", str(seed), "--tier", tier, "--years", str(years), "--out", str(out)]
    print("$ " + " ".join(cmd))
    subprocess.run(cmd, check=True, cwd=REPO)
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--runs", nargs="+", default=["small:1,2,3", "medium:1,2,3"], help="tier:seed[,seed...]")
    ap.add_argument("--years", type=int, default=300)
    ap.add_argument("--force", action="store_true", help="rerun the CLI even when the data file exists")
    ap.add_argument("--render-only", action="store_true", help="never run the CLI; render existing data files")
    ap.add_argument("--out-dir", type=Path, default=OUT_DIR)
    ap.add_argument("--lang", type=Path, default=LANG_FILE)
    ap.add_argument("--sim-data", type=Path, default=SIM_DATA_DIR)
    args = ap.parse_args(argv)

    data_dir = args.out_dir / "data"
    if not args.render_only:
        for tier, seed in parse_runs(args.runs):
            ensure_data(tier, seed, args.years, data_dir, args.force)
    content = Content.load(args.sim_data)
    lang_entries = json.loads(args.lang.read_text(encoding="utf-8"))
    runs = []
    for path in sorted(data_dir.glob("*.json"), key=run_sort_key):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            print(f"skip {path}: {e}", file=sys.stderr)
            continue
        run = Run(data, Lang(lang_entries, chinese_numerals=args.lang.name.startswith("zh")), content, stem=path.stem)
        html_text = render_run(run)
        target = args.out_dir / f"{path.stem}.html"
        target.write_text(html_text, encoding="utf-8")
        runs.append(run)
        kinds = Counter(k for k, _ in run.oddities)
        print(f"wrote {target} ({len(html_text) // 1024} KiB; {len(run.bios)} lives; "
              f"oddities: {dict(sorted(kinds.items())) or 'none'})")
    index = args.out_dir / "index.html"
    index.write_text(render_index(runs), encoding="utf-8")
    print(f"wrote {index}")
    print(f"public: {PUBLIC_BASE}index.html")
    return 0


if __name__ == "__main__":
    sys.exit(main())
