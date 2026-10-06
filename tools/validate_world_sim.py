#!/usr/bin/env python3
"""Validate the world-sim data, its language keys and the purity of its core.

Checks, each failure printed as ``<check>: <file>: <detail>``:

- data: every file under ``data/myvillage/world_sim/`` parses and has ``"schema": 1``; the seven known files
  (rules, realms, encounters, names, techniques, heritages, lore) have their required fields, enum values, positive
  weights and unique ids, and agree with each other (realm ids and stages named by rules and encounters,
  site kinds named by encounters exist in lore, every technique/artifact grade an encounter or genesis can
  grant has at least one entry, sim realm lifespans equal the player realm files' for shared ids). The
  runtime's config tiers (small, medium, large) must exist in rules. Heritages have unique ids and names,
  a non-empty technique list of ledger techniques that share one element, a school that is ``none`` or a
  datapack school, and no technique in two heritages.
- datapack: every ledger technique has ``data/myvillage/myvillage/technique/<id>.json`` whose integer grade
  and elements agree with the ledger's grade and element; every ``lineage.previous`` names a datapack
  technique and no chain is a cycle; each heritage's techniques carry its school (none: no school) and
  follow one another by ``lineage.previous``, the first having none. ``tools/gen_technique_catalogue.py``
  writes both sides from one catalogue.
- keys: every ``world_sim.`` key the core can emit exists in both ``en_us`` and ``zh_cn``: string literals and
  ``String`` constants built from literals in ``sim/**`` (a family base is satisfied by its ``.1`` variant),
  plus the data-derived keys (realm, stage, rank, root grade, technique grade, the breakthrough lines of every
  realm with a breakthrough, the fortune line of every encounter). Every ``world_sim.*`` key in either file is
  in the other with the same ``%n$s`` slots, numbered 1..n; a family's variants run .1..n in both files.
  The runtime's ``commands.myvillage.world.*`` / ``message.myvillage.world.*`` keys used in ``sim/runtime/**``
  exist in both files with the same slots.
- purity: ``sim/**`` outside ``sim/runtime/**`` imports and mentions nothing from ``net.minecraft``,
  ``net.neoforged``, ``com.mojang`` or ``org.slf4j``.

Param counts per key are pinned by the Java ``TextKeyCoverageTest`` (it reads the engine's own key table).
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DATA_REL = Path("src/main/resources/data/myvillage/world_sim")
PLAYER_REALM_REL = Path("src/main/resources/data/myvillage/myvillage/realm")
TECHNIQUE_REL = Path("src/main/resources/data/myvillage/myvillage/technique")
SCHOOL_REL = Path("src/main/resources/data/myvillage/myvillage/school")
DATAPACK_NAMESPACE = "myvillage"
LANG_REL = Path("src/main/resources/assets/myvillage/lang")
SIM_REL = Path("src/main/java/com/example/myvillage/sim")

SCHEMA = 1
KNOWN_FILES = ("rules.json", "realms.json", "encounters.json", "names.json", "techniques.json", "heritages.json",
               "lore.json")
GRADES = ("huang", "xuan", "di", "tian")
GRADE_NUMBERS = {"huang": 1, "xuan": 2, "di": 3, "tian": 4}  # the player registry's integer grade
ELEMENTS_OR_NONE = ("metal", "wood", "water", "fire", "earth", "none")
SITE_KINDS = ("ruin", "cave", "secret_realm", "battlefield", "tomb")
EFFECT_KINDS = ("progress", "technique", "breakthrough_pill", "lifespan", "root", "artifact", "injury", "death",
                "heritage")
STATUSES = ("at_sect", "travelling", "secluded")
RANKS = ("sect_master", "elder", "inner", "outer", "rogue")
RUNTIME_TIERS = ("small", "medium", "large")  # WorldSimServerConfig.TIERS
SECT_STATES = ("active", "destroyed")
SECT_RELATION_STATES = ("none", "feud", "war")
FORBIDDEN_PACKAGES = ("net.minecraft", "net.neoforged", "com.mojang", "org.slf4j")
RUNTIME_KEY = "commands.myvillage.world."
RUNTIME_MESSAGE = "message.myvillage.world."
LANGS = ("en_us", "zh_cn")
SLOT = re.compile(r"%(?:(\d+)\$)?([sd%])")


# ---------------------------------------------------------------------------------------------- helpers

def is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def is_number(value: Any) -> bool:
    return (isinstance(value, (int, float))) and not isinstance(value, bool)


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.notes: list[str] = []

    def error(self, check: str, where: str, detail: str) -> None:
        self.errors.append(f"{check}: {where}: {detail}")


def load_json(path: Path, report: Report, check: str = "data") -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        report.error(check, path.name, f"cannot be read as JSON ({exc})")
        return None


def require(obj: Any, key: str, file: str, where: str, report: Report) -> Any:
    if not isinstance(obj, dict) or key not in obj:
        report.error("data", file, f"{where}{key} is missing")
        return None
    return obj[key]


def positive_int(value: Any) -> bool:
    return is_int(value) and value > 0


def check_unique(items: list[Any], file: str, where: str, report: Report) -> None:
    seen: set[Any] = set()
    for item in items:
        if item in seen:
            report.error("data", file, f"{where} has the duplicate {item!r}")
        seen.add(item)


# ---------------------------------------------------------------------------------------------- data files

def check_realms(realms_json: Any, report: Report) -> dict[str, dict]:
    f = "realms.json"
    realms = require(realms_json, "realms", f, "", report)
    out: dict[str, dict] = {}
    if not isinstance(realms, list) or not realms:
        report.error("data", f, "realms must be a non-empty list")
        return out
    check_unique([r.get("id") for r in realms if isinstance(r, dict)], f, "realms[].id", report)
    last_lifespan = 0
    for i, r in enumerate(realms):
        where = f"realms[{i}]."
        if not isinstance(r, dict):
            report.error("data", f, f"realms[{i}] must be an object")
            continue
        rid = require(r, "id", f, where, report)
        if not isinstance(rid, str) or not re.fullmatch(r"[a-z_]+", rid or ""):
            report.error("data", f, f"{where}id must match [a-z_]+")
            continue
        life = require(r, "lifespan_years", f, where, report)
        if not positive_int(life):
            report.error("data", f, f"{where}lifespan_years must be a positive integer")
        elif life < last_lifespan:
            report.error("data", f, f"{where}lifespan_years {life} is below the previous realm's {last_lifespan}")
        else:
            last_lifespan = life
        stages = require(r, "stages", f, where, report)
        if not isinstance(stages, list) or not stages:
            report.error("data", f, f"{where}stages must be a non-empty list")
            stages = []
        last_cap = 0.0
        for j, s in enumerate(stages):
            cap = s.get("cap") if isinstance(s, dict) else None
            power = s.get("power") if isinstance(s, dict) else None
            if not is_number(cap) or cap <= last_cap:
                report.error("data", f, f"{where}stages[{j}].cap must be a number above the previous stage's")
            else:
                last_cap = cap
            if not is_number(power) or power <= 0:
                report.error("data", f, f"{where}stages[{j}].power must be positive")
        bt = r.get("breakthrough", "<missing>")
        if bt == "<missing>":
            report.error("data", f, f"{where}breakthrough is missing (null for the first realm)")
        elif i == 0 and bt is not None:
            report.error("data", f, f"{where}breakthrough must be null for the entry realm")
        elif i > 0:
            if not isinstance(bt, dict):
                report.error("data", f, f"{where}breakthrough must be an object")
            else:
                for key in ("base_chance", "death_chance"):
                    v = bt.get(key)
                    if not is_number(v) or not 0 <= v <= 1:
                        report.error("data", f, f"{where}breakthrough.{key} must be a number in [0, 1]")
        suffix = r.get("title_suffix")
        if suffix is not None and (not isinstance(suffix, str) or not suffix):
            report.error("data", f, f"{where}title_suffix must be null or a non-empty string")
        out[rid] = r
    return out


def check_player_realms(realms: dict[str, dict], root: Path, report: Report) -> None:
    directory = root / PLAYER_REALM_REL
    if not directory.is_dir():
        report.error("data", str(PLAYER_REALM_REL), "player realm directory is missing")
        return
    for path in sorted(directory.glob("*.json")):
        player = load_json(path, report)
        if not isinstance(player, dict):
            continue
        sim = realms.get(path.stem)
        years = player.get("maximum_lifespan_years")
        if sim is not None and sim.get("lifespan_years") != years:
            report.error("data", "realms.json",
                         f"{path.stem} lifespan_years {sim.get('lifespan_years')} disagrees with the player realm "
                         f"file's maximum_lifespan_years {years}")


def realm_stage_ok(realms: dict[str, dict], realm: Any, stage: Any) -> bool:
    r = realms.get(realm) if isinstance(realm, str) else None
    return r is not None and is_int(stage) and 0 <= stage < len(r.get("stages") or [])


def check_rules(rules: Any, realms: dict[str, dict], report: Report) -> dict:
    f = "rules.json"
    out: dict = {"tiers": [], "root_grades": [], "signature_grades": []}
    if not isinstance(rules, dict):
        return out
    time = require(rules, "time", f, "", report)
    if isinstance(time, dict):
        for key in ("prehistory_years", "default_days_per_year"):
            if not positive_int(time.get(key)):
                report.error("data", f, f"time.{key} must be a positive integer")
    tiers = require(rules, "tiers", f, "", report)
    if isinstance(tiers, dict):
        out["tiers"] = list(tiers)
        for tid in RUNTIME_TIERS:
            if tid not in tiers:
                report.error("data", f, f"tiers.{tid} is missing (a value of the runtime config's tier)")
        for tid, tier in tiers.items():
            for key in ("population", "sects"):
                if not positive_int(tier.get(key) if isinstance(tier, dict) else None):
                    report.error("data", f, f"tiers.{tid}.{key} must be a positive integer")
    scheduler = require(rules, "scheduler", f, "", report)
    if isinstance(scheduler, dict) and not positive_int(scheduler.get("max_pending_days")):
        report.error("data", f, "scheduler.max_pending_days must be a positive integer")
    roots = require(rules, "roots", f, "", report)
    grades = roots.get("grades") if isinstance(roots, dict) else None
    if not isinstance(grades, list) or not grades:
        report.error("data", f, "roots.grades must be a non-empty list")
    else:
        ids = [g.get("id") for g in grades if isinstance(g, dict)]
        out["root_grades"] = [i for i in ids if isinstance(i, str)]
        check_unique(ids, f, "roots.grades[].id", report)
        check_unique([g.get("max_elements") for g in grades if isinstance(g, dict)], f,
                     "roots.grades[].max_elements", report)
        for i, g in enumerate(grades):
            if not isinstance(g, dict):
                continue
            if not positive_int(g.get("weight")):
                report.error("data", f, f"roots.grades[{i}].weight must be a positive integer")
            me = g.get("max_elements")
            if not is_int(me) or not 1 <= me <= 5:
                report.error("data", f, f"roots.grades[{i}].max_elements must be 1..5")
    techniques = require(rules, "techniques", f, "", report)
    tg = techniques.get("grades") if isinstance(techniques, dict) else None
    if not isinstance(tg, dict) or sorted(tg) != sorted(GRADES):
        report.error("data", f, f"techniques.grades must have exactly the grades {list(GRADES)}")
    cultivation = rules.get("cultivation")
    if isinstance(cultivation, dict):
        for key, allowed in (("status", STATUSES), ("rank", RANKS)):
            table = cultivation.get(key)
            if isinstance(table, dict):
                for k in table:
                    if k not in allowed:
                        report.error("data", f, f"cultivation.{key}.{k} is not one of {list(allowed)}")
    for k in (rules.get("death_importance_by_rank") or {}):
        if k not in RANKS:
            report.error("data", f, f"death_importance_by_rank.{k} is not a rank")
    sects = rules.get("sects")
    if isinstance(sects, dict):
        for rank, promo in (sects.get("promotion") or {}).items():
            if not isinstance(promo, dict) or not realm_stage_ok(realms, promo.get("realm"), promo.get("stage")):
                report.error("data", f, f"sects.promotion.{rank} names an unknown realm/stage")
        for realm in (sects.get("pills") or {}):
            if realm not in realms or realms[realm].get("breakthrough") is None:
                report.error("data", f, f"sects.pills.{realm} is not a realm with a breakthrough")
    genesis = rules.get("genesis")
    if isinstance(genesis, dict):
        for i, m in enumerate(genesis.get("masters") or []):
            if not isinstance(m, dict) or not realm_stage_ok(realms, m.get("realm"), m.get("stage")):
                report.error("data", f, f"genesis.masters[{i}] names an unknown realm/stage")
        for realm in (genesis.get("ages") or {}):
            if realm not in realms:
                report.error("data", f, f"genesis.ages.{realm} is not a realm")
        chance = genesis.get("heritage_chance")
        if not is_number(chance) or not 0 <= chance <= 1:
            report.error("data", f, "genesis.heritage_chance must be a number in 0..1")
        sig = genesis.get("signature_grades") or []
        for g in sig:
            if g not in GRADES:
                report.error("data", f, f"genesis.signature_grades has the unknown grade {g!r}")
        out["signature_grades"] = [g for g in sig if g in GRADES]
    return out


def check_techniques(doc: Any, report: Report) -> set[str]:
    f = "techniques.json"
    items = require(doc, "techniques", f, "", report)
    grades: set[str] = set()
    if not isinstance(items, list) or not items:
        report.error("data", f, "techniques must be a non-empty list")
        return grades
    check_unique([t.get("id") for t in items if isinstance(t, dict)], f, "techniques[].id", report)
    check_unique([t.get("name") for t in items if isinstance(t, dict)], f, "techniques[].name", report)
    for i, t in enumerate(items):
        if not isinstance(t, dict):
            report.error("data", f, f"techniques[{i}] must be an object")
            continue
        for key in ("id", "name"):
            if not isinstance(t.get(key), str) or not t.get(key):
                report.error("data", f, f"techniques[{i}].{key} must be a non-empty string")
        if t.get("grade") not in GRADES:
            report.error("data", f, f"techniques[{i}].grade must be one of {list(GRADES)}")
        else:
            grades.add(t["grade"])
        if t.get("element") not in ELEMENTS_OR_NONE:
            report.error("data", f, f"techniques[{i}].element must be one of {list(ELEMENTS_OR_NONE)}")
    return grades


def datapack_id(path: str) -> str:
    return f"{DATAPACK_NAMESPACE}:{path}"


def load_datapack_dir(root: Path, rel: Path, report: Report) -> dict[str, dict] | None:
    directory = root / rel
    if not directory.is_dir():
        report.error("datapack", str(rel), "directory is missing")
        return None
    out: dict[str, dict] = {}
    for path in sorted(directory.glob("*.json")):
        doc = load_json(path, report, "datapack")
        if isinstance(doc, dict):
            out[path.stem] = doc
    return out


def lineage_previous(doc: dict) -> Any:
    lineage = doc.get("lineage")
    return lineage.get("previous") if isinstance(lineage, dict) else None


def check_datapack_techniques(sim_items: Any, datapack: dict[str, dict], report: Report) -> int:
    """Ledger techniques against their datapack files; lineage.previous references and cycles."""
    matched = 0
    for t in sim_items if isinstance(sim_items, list) else []:
        if not isinstance(t, dict) or not isinstance(t.get("id"), str):
            continue
        tid, where = t["id"], f"technique/{t['id']}.json"
        doc = datapack.get(tid)
        if doc is None:
            report.error("datapack", "techniques.json", f"{tid} has no datapack file {where}")
            continue
        expected_grade = GRADE_NUMBERS.get(t.get("grade"))
        grade = doc.get("grade")
        if expected_grade is not None and (not is_int(grade) or grade != expected_grade):
            report.error("datapack", where, f"grade {grade!r} disagrees with the ledger's {t.get('grade')} "
                                            f"({expected_grade})")
        element = t.get("element")
        expected_elements = [] if element == "none" else [datapack_id(element)]
        if element in ELEMENTS_OR_NONE and doc.get("elements") != expected_elements:
            report.error("datapack", where, f"elements {doc.get('elements')!r} disagree with the ledger's element "
                                            f"{element} ({expected_elements})")
        matched += 1
    previous: dict[str, str] = {}
    for tid, doc in datapack.items():
        prev = lineage_previous(doc)
        if prev is None:
            continue
        name = prev[len(DATAPACK_NAMESPACE) + 1:] if isinstance(prev, str) and prev.startswith(
            DATAPACK_NAMESPACE + ":") else None
        if name is None or name not in datapack:
            report.error("datapack", f"technique/{tid}.json", f"lineage.previous {prev!r} is not a datapack technique")
            continue
        previous[tid] = name
    reported: set[frozenset[str]] = set()
    for start in sorted(previous):
        seen: list[str] = []
        node: str | None = start
        while node is not None and node not in seen:
            seen.append(node)
            node = previous.get(node)
        if node is not None:
            cycle = frozenset(seen[seen.index(node):])
            if cycle not in reported:
                reported.add(cycle)
                report.error("datapack", f"technique/{min(cycle)}.json",
                             f"lineage.previous cycle through {sorted(cycle)}")
    return matched


def check_heritages(doc: Any, sim_items: Any, datapack: dict[str, dict] | None, schools: dict[str, dict] | None,
                    report: Report) -> int:
    f = "heritages.json"
    items = require(doc, "heritages", f, "", report)
    if not isinstance(items, list):
        report.error("data", f, "heritages must be a list")
        return 0
    sim = {t["id"]: t for t in sim_items if isinstance(t, dict) and isinstance(t.get("id"), str)} \
        if isinstance(sim_items, list) else {}
    check_unique([h.get("id") for h in items if isinstance(h, dict)], f, "heritages[].id", report)
    check_unique([h.get("name") for h in items if isinstance(h, dict)], f, "heritages[].name", report)
    owner: dict[str, str] = {}
    for i, h in enumerate(items):
        where = f"heritages[{i}]"
        if not isinstance(h, dict):
            report.error("data", f, f"{where} must be an object")
            continue
        for key in ("id", "name", "school"):
            if not isinstance(h.get(key), str) or not h.get(key):
                report.error("data", f, f"{where}.{key} must be a non-empty string")
        school = h.get("school")
        if isinstance(school, str) and school != "none" and schools is not None and school not in schools:
            report.error("data", f, f"{where}.school {school!r} is not none or a datapack school")
        chain = h.get("techniques")
        if not isinstance(chain, list) or not chain or not all(isinstance(t, str) for t in chain):
            report.error("data", f, f"{where}.techniques must be a non-empty list of technique ids")
            continue
        unknown = [t for t in chain if t not in sim]
        for t in unknown:
            report.error("data", f, f"{where}.techniques names {t!r}, which is not in techniques.json")
        for t in chain:
            if t in owner:
                report.error("data", f, f"{where}.techniques: {t} already belongs to heritage {owner[t]}")
            owner.setdefault(t, str(h.get("id")))
        elements = sorted({sim[t].get("element") for t in chain if t in sim})
        if len(elements) > 1:
            report.error("data", f, f"{where} mixes the elements {elements}; a heritage shares one element")
        if datapack is None:
            continue
        expected_school = None if school == "none" else datapack_id(str(school))
        for n, t in enumerate(chain):
            doc = datapack.get(t)
            if doc is None:
                continue  # reported against techniques.json
            if doc.get("school") != expected_school:
                report.error("datapack", f"technique/{t}.json",
                             f"school {doc.get('school')!r} is not heritage {h.get('id')}'s {school!r}")
            expected_prev = datapack_id(chain[n - 1]) if n else None
            if lineage_previous(doc) != expected_prev:
                report.error("datapack", f"technique/{t}.json",
                             f"lineage.previous {lineage_previous(doc)!r} breaks heritage {h.get('id')} "
                             f"(expected {expected_prev!r})")
    return len(items)


def check_lore(doc: Any, report: Report) -> tuple[set[str], set[str]]:
    f = "lore.json"
    artifact_grades: set[str] = set()
    site_kinds: set[str] = set()
    specs = (("artifacts", "grade", GRADES), ("sites", "kind", SITE_KINDS), ("beasts", "rank", (1, 2, 3, 4)))
    for list_key, field, allowed in specs:
        items = require(doc, list_key, f, "", report)
        if not isinstance(items, list) or not items:
            report.error("data", f, f"{list_key} must be a non-empty list")
            continue
        check_unique([x.get("id") for x in items if isinstance(x, dict)], f, f"{list_key}[].id", report)
        for i, x in enumerate(items):
            if not isinstance(x, dict):
                report.error("data", f, f"{list_key}[{i}] must be an object")
                continue
            for key in ("id", "name"):
                if not isinstance(x.get(key), str) or not x.get(key):
                    report.error("data", f, f"{list_key}[{i}].{key} must be a non-empty string")
            value = x.get(field)
            if isinstance(value, bool) or value not in allowed:
                report.error("data", f, f"{list_key}[{i}].{field} must be one of {list(allowed)}")
            elif list_key == "artifacts":
                artifact_grades.add(value)
            elif list_key == "sites":
                site_kinds.add(value)
    return artifact_grades, site_kinds


def check_names(doc: Any, report: Report) -> None:
    f = "names.json"
    for key in ("surnames", "given_male", "given_female", "given_neutral", "dao_titles", "sect_prefixes"):
        items = require(doc, key, f, "", report)
        if not isinstance(items, list) or not items:
            report.error("data", f, f"{key} must be a non-empty list")
            continue
        if any(not isinstance(x, str) or not x.strip() for x in items):
            report.error("data", f, f"{key} must hold non-empty strings")
        check_unique(items, f, key, report)
    suffixes = require(doc, "sect_suffixes", f, "", report)
    if not isinstance(suffixes, list) or not suffixes:
        report.error("data", f, "sect_suffixes must be a non-empty list")
        return
    check_unique([s.get("text") for s in suffixes if isinstance(s, dict)], f, "sect_suffixes[].text", report)
    for i, s in enumerate(suffixes):
        if not isinstance(s, dict) or not isinstance(s.get("text"), str) or not s.get("text"):
            report.error("data", f, f"sect_suffixes[{i}].text must be a non-empty string")
        elif not positive_int(s.get("weight")):
            report.error("data", f, f"sect_suffixes[{i}].weight must be a positive integer")


def check_encounters(doc: Any, realms: dict[str, dict], technique_grades: set[str], artifact_grades: set[str],
                     site_kinds: set[str], report: Report) -> list[dict]:
    f = "encounters.json"
    items = require(doc, "encounters", f, "", report)
    if not isinstance(items, list) or not items:
        report.error("data", f, "encounters must be a non-empty list")
        return []
    check_unique([e.get("id") for e in items if isinstance(e, dict)], f, "encounters[].id", report)
    good: list[dict] = []
    for i, e in enumerate(items):
        where = f"encounters[{i}]"
        if not isinstance(e, dict):
            report.error("data", f, f"{where} must be an object")
            continue
        if not isinstance(e.get("id"), str) or not e.get("id"):
            report.error("data", f, f"{where}.id must be a non-empty string")
        if not positive_int(e.get("weight")):
            report.error("data", f, f"{where}.weight must be a positive integer")
        if not is_int(e.get("rarity")) or not 0 <= e["rarity"] <= 3:
            report.error("data", f, f"{where}.rarity must be 0..3")
        if not is_int(e.get("importance")) or not 1 <= e["importance"] <= 3:
            report.error("data", f, f"{where}.importance must be 1..3")
        text = e.get("text")
        if not isinstance(text, str) or not re.fullmatch(r"[a-z0-9_.]+", text):
            report.error("data", f, f"{where}.text must match [a-z0-9_.]+")
        site = e.get("site_kind", "<missing>")
        if site == "<missing>":
            report.error("data", f, f"{where}.site_kind is missing (null for none)")
        elif site is not None:
            if site not in SITE_KINDS:
                report.error("data", f, f"{where}.site_kind must be null or one of {list(SITE_KINDS)}")
            elif site not in site_kinds:
                report.error("data", f, f"{where}.site_kind {site} has no site of that kind in lore.json")
        lo, hi = e.get("min_danger"), e.get("max_danger")
        if not is_int(lo) or not is_int(hi) or not 0 <= lo <= hi <= 10:
            report.error("data", f, f"{where}.min_danger/max_danger must satisfy 0 <= min <= max <= 10")
        if not is_int(e.get("min_tier")) or e["min_tier"] < 0:
            report.error("data", f, f"{where}.min_tier must be a non-negative integer")
        for realm in e.get("realms") or []:
            if realm not in realms:
                report.error("data", f, f"{where}.realms names the unknown realm {realm!r}")
        for status in e.get("statuses") or []:
            if status not in STATUSES:
                report.error("data", f, f"{where}.statuses names the unknown status {status!r}")
        effects = e.get("effects")
        if not isinstance(effects, list) or not effects:
            report.error("data", f, f"{where}.effects must be a non-empty list")
            effects = []
        items_granted = 0
        for j, fx in enumerate(effects):
            fw = f"{where}.effects[{j}]"
            kind = fx.get("kind") if isinstance(fx, dict) else None
            if kind not in EFFECT_KINDS:
                report.error("data", f, f"{fw}.kind must be one of {list(EFFECT_KINDS)}")
                continue
            if kind in ("technique", "artifact"):
                items_granted += 1
                grade = fx.get("grade")
                if grade not in GRADES:
                    report.error("data", f, f"{fw}.grade must be one of {list(GRADES)}")
                elif kind == "technique" and grade not in technique_grades:
                    report.error("data", f, f"{fw} grants a {grade} technique but techniques.json has none")
                elif kind == "artifact" and grade not in artifact_grades:
                    report.error("data", f, f"{fw} grants a {grade} artifact but lore.json has none")
                if "amount" in fx:
                    report.error("data", f, f"{fw}.amount is not used by {kind}")
            elif kind == "heritage":
                items_granted += 1
                for key in ("amount", "grade"):
                    if key in fx:
                        report.error("data", f, f"{fw}.{key} is not used by heritage")
            else:
                amount = fx.get("amount")
                if not is_number(amount) or amount <= 0:
                    report.error("data", f, f"{fw}.amount must be a positive number")
                elif kind in ("death", "breakthrough_pill") and amount > 1:
                    report.error("data", f, f"{fw}.amount is a chance/bonus and must be at most 1")
                if "grade" in fx:
                    report.error("data", f, f"{fw}.grade is only used by technique and artifact")
        if items_granted > 1:
            report.error("data", f, f"{where}.effects may grant at most one technique, artifact or heritage")
        good.append(e)
    return good


def check_data(root: Path, report: Report) -> dict:
    directory = root / DATA_REL
    facts: dict = {"realms": {}, "encounters": [], "root_grades": [], "tiers": []}
    if not directory.is_dir():
        report.error("data", str(DATA_REL), "directory is missing")
        return facts
    docs: dict[str, Any] = {}
    for path in sorted(directory.rglob("*")):
        if path.is_dir():
            continue
        rel = path.relative_to(directory).as_posix()
        if path.suffix != ".json":
            report.error("data", rel, "only JSON files belong in the world-sim data directory")
            continue
        doc = load_json(path, report)
        if doc is None:
            continue
        if not isinstance(doc, dict) or doc.get("schema") != SCHEMA:
            report.error("data", rel, f"schema must be {SCHEMA}")
        docs[rel] = doc
        if rel not in KNOWN_FILES:
            report.notes.append(f"{rel}: parsed and schema-checked only (not a file this validator cross-checks)")
    for name in KNOWN_FILES:
        if name not in docs and (directory / name).is_file() is False:
            report.error("data", name, "is missing")
    realms = check_realms(docs.get("realms.json"), report) if "realms.json" in docs else {}
    check_player_realms(realms, root, report)
    rules = check_rules(docs.get("rules.json"), realms, report) if "rules.json" in docs else {}
    technique_grades = check_techniques(docs.get("techniques.json"), report) if "techniques.json" in docs else set()
    artifact_grades, site_kinds = (check_lore(docs.get("lore.json"), report)
                                   if "lore.json" in docs else (set(), set()))
    if "names.json" in docs:
        check_names(docs["names.json"], report)
    for g in rules.get("signature_grades", []):
        if g not in technique_grades:
            report.error("data", "rules.json", f"genesis.signature_grades needs a {g} technique; techniques.json has none")
    encounters = (check_encounters(docs.get("encounters.json"), realms, technique_grades, artifact_grades,
                                   site_kinds, report) if "encounters.json" in docs else [])
    sim_techniques = (docs.get("techniques.json") or {}).get("techniques")
    datapack = load_datapack_dir(root, TECHNIQUE_REL, report)
    schools = load_datapack_dir(root, SCHOOL_REL, report)
    matched = check_datapack_techniques(sim_techniques, datapack, report) if datapack is not None else 0
    heritages = (check_heritages(docs["heritages.json"], sim_techniques, datapack, schools, report)
                 if "heritages.json" in docs else 0)
    if not heritages and any(isinstance(fx, dict) and fx.get("kind") == "heritage"
                             for e in encounters for fx in (e.get("effects") or [])):
        report.error("data", "encounters.json", "a heritage effect needs at least one heritage in heritages.json")
    facts.update(realms=realms, encounters=encounters, root_grades=rules.get("root_grades", []),
                 tiers=rules.get("tiers", []), files=len(docs), heritages=heritages, datapack_matched=matched)
    return facts


# ---------------------------------------------------------------------------------------------- Java sources

def java_tokens(text: str) -> list[tuple[str, str]]:
    """Tokens of a Java source: ("str", value), ("id", dotted.name), ("op", char); comments dropped."""
    out: list[tuple[str, str]] = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c == '"':
            if text.startswith('"""', i):  # text block: not a key source; skip it whole
                j = text.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            j, buf = i + 1, []
            while j < n and text[j] != '"':
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j:j + 2])
                    j += 2
                else:
                    buf.append(text[j])
                    j += 1
            raw = "".join(buf)
            try:
                value = json.loads('"' + raw + '"')
            except ValueError:
                value = raw
            out.append(("str", value))
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            i = j + 1
        elif c.isalpha() or c == "_":
            j = i
            while j < n and (text[j].isalnum() or text[j] in "_."):
                j += 1
            out.append(("id", text[i:j].rstrip(".")))
            i = j
        elif c.isspace():
            i += 1
        else:
            out.append(("op", c))
            i += 1
    return out


def string_constants(files: dict[str, list[tuple[str, str]]]) -> dict[str, dict[str, str]]:
    """``final String NAME = "lit" + OTHER + ...;`` per class, resolved across files where possible."""
    raw: dict[str, dict[str, list[tuple[str, str]]]] = {}
    for cls, toks in files.items():
        defs: dict[str, list[tuple[str, str]]] = {}
        for k in range(len(toks) - 3):
            if toks[k] == ("id", "final") and toks[k + 1] == ("id", "String") and toks[k + 2][0] == "id" \
                    and toks[k + 3] == ("op", "="):
                expr, m = [], k + 4
                while m < len(toks) and toks[m] != ("op", ";"):
                    expr.append(toks[m])
                    m += 1
                defs[toks[k + 2][1]] = expr
        raw[cls] = defs
    resolved: dict[str, dict[str, str]] = {cls: {} for cls in raw}
    for _ in range(6):
        for cls, defs in raw.items():
            for name, expr in defs.items():
                if name in resolved[cls]:
                    continue
                parts, ok = [], bool(expr)
                for idx, (kind, value) in enumerate(expr):
                    if idx % 2 == 1:
                        ok = ok and (kind, value) == ("op", "+")
                        continue
                    if kind == "str":
                        parts.append(value)
                    elif kind == "id":
                        owner, _, simple = value.rpartition(".")
                        owner = owner.rsplit(".", 1)[-1] if owner else cls
                        v = resolved.get(owner, {}).get(simple)
                        ok = ok and v is not None
                        parts.append(v or "")
                    else:
                        ok = False
                if ok:
                    resolved[cls][name] = "".join(parts)
    return resolved


def core_sources(root: Path) -> list[Path]:
    base = root / SIM_REL
    return sorted(p for p in base.rglob("*.java") if not p.relative_to(base).parts[0] == "runtime")


def runtime_sources(root: Path) -> list[Path]:
    base = root / SIM_REL / "runtime"
    return sorted(base.rglob("*.java")) if base.is_dir() else []


def check_purity(root: Path, report: Report) -> int:
    files = core_sources(root)
    if not files:
        report.error("purity", str(SIM_REL), "no core sources found")
    for path in files:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(root).as_posix()
        for match in re.finditer(r"^\s*import\s+(?:static\s+)?([\w.]+)", text, re.MULTILINE):
            if match.group(1).startswith(FORBIDDEN_PACKAGES):
                report.error("purity", rel, f"imports {match.group(1)}")
        code = " ".join(v for k, v in java_tokens(text) if k == "id")
        for pkg in FORBIDDEN_PACKAGES:
            if re.search(r"(^|\s)" + re.escape(pkg) + r"(\.|\s|$)", code) and f"import {pkg}" not in text:
                report.error("purity", rel, f"mentions {pkg}")
    return len(files)


# ---------------------------------------------------------------------------------------------- language keys

def slots(template: str) -> list[int] | None:
    """Positional slot numbers of a template, or None when it mixes %s with %n$s."""
    numbered, plain = [], 0
    for m in SLOT.finditer(template):
        if m.group(2) == "%":
            continue
        if m.group(1):
            numbered.append(int(m.group(1)))
        else:
            plain += 1
    if numbered and plain:
        return None
    return sorted(numbered) if numbered else list(range(1, plain + 1))


def has_key(lang: dict[str, str], key: str) -> bool:
    return key in lang or f"{key}.1" in lang


def check_keys(root: Path, facts: dict, report: Report) -> int:
    langs: dict[str, dict[str, str]] = {}
    for code in LANGS:
        path = root / LANG_REL / f"{code}.json"
        doc = load_json(path, report, check="keys")
        langs[code] = doc if isinstance(doc, dict) else {}
    en, zh = langs["en_us"], langs["zh_cn"]

    required: dict[str, str] = {}  # key (or family base) -> where it comes from

    def need(key: str, source: str) -> None:
        required.setdefault(key, source)

    # 1. literal keys and constants in the core
    files = {p.stem: java_tokens(p.read_text(encoding="utf-8")) for p in core_sources(root)}
    families: set[str] = set()
    for cls, toks in files.items():
        for kind, value in toks:
            if kind == "str" and value.lstrip("@").startswith("world_sim.") and not value.endswith("."):
                need(value.lstrip("@"), f"{cls}.java literal")
    for cls, consts in string_constants(files).items():
        for name, value in consts.items():
            value = value.lstrip("@")
            if value.startswith("world_sim.") and not value.endswith("."):
                need(value, f"{cls}.{name}")
                families.add(value)

    # 2. keys derived from the data
    for rid, realm in facts.get("realms", {}).items():
        need(f"world_sim.realm.{rid}", "realms.json")
        for s in range(len(realm.get("stages") or [])):
            need(f"world_sim.stage.{rid}.{s + 1}", "realms.json")
        if realm.get("breakthrough") is not None:
            for base in (f"world_sim.event.breakthrough.{rid}", f"world_sim.event.breakthrough_fail.{rid}",
                         f"world_sim.event.breakthrough_death.{rid}"):
                need(base, "realms.json breakthrough")
                families.add(base)
    for rank in RANKS:
        need(f"world_sim.rank.{rank}", "ranks")
    for grade in facts.get("root_grades", []):
        need(f"world_sim.root.{grade}", "rules.json roots.grades")
    for grade in GRADES:
        need(f"world_sim.grade.{grade}", "technique grades")
    for e in facts.get("encounters", []):
        if isinstance(e.get("text"), str):
            base = f"world_sim.event.fortune.{e['text']}"
            need(base, f"encounters.json {e.get('id')}")
            families.add(base)

    # 3. runtime keys
    runtime_files = runtime_sources(root)
    for path in runtime_files:
        toks = java_tokens(path.read_text(encoding="utf-8"))
        for k, (kind, value) in enumerate(toks):
            if kind == "str" and value.startswith((RUNTIME_KEY, RUNTIME_MESSAGE)) and not value.endswith("."):
                need(value, f"{path.stem}.java literal")
            if kind == "id" and value.split(".")[-1] == "line" and k + 2 < len(toks) \
                    and toks[k + 1] == ("op", "(") and toks[k + 2][0] == "str" \
                    and not toks[k + 2][1].endswith("."):
                need(RUNTIME_KEY + toks[k + 2][1], f"{path.stem}.java line()")
    if runtime_files:
        for tier in facts.get("tiers", []):
            need(f"{RUNTIME_KEY}tier.{tier}", "rules.json tiers (info)")
        for status in STATUSES:
            need(f"{RUNTIME_KEY}status.{status}", "statuses (person)")
        for state in SECT_STATES:
            need(f"{RUNTIME_KEY}sect_state.{state}", "sect states")
        for state in SECT_RELATION_STATES:
            need(f"{RUNTIME_KEY}relation.{state}", "sect relation states")

    for key, source in sorted(required.items()):
        for code, lang in langs.items():
            if not has_key(lang, key):
                report.error("keys", f"{code}.json", f"{key} is missing (needed by {source})")

    # 4. both files agree on every world-sim and runtime key, with well-formed slots
    prefixes = ("world_sim.", RUNTIME_KEY, RUNTIME_MESSAGE)
    ours = sorted({k for lang in langs.values() for k in lang if k.startswith(prefixes)})
    for key in ours:
        if key not in en or key not in zh:
            missing = "en_us" if key not in en else "zh_cn"
            report.error("keys", f"{missing}.json", f"{key} is in the other language file only")
            continue
        se, sz = slots(en[key]), slots(zh[key])
        for code, s in (("en_us", se), ("zh_cn", sz)):
            if s is None:
                report.error("keys", f"{code}.json", f"{key} mixes %s with numbered %n$s slots")
            elif s and sorted(set(s)) != list(range(1, max(s) + 1)):
                report.error("keys", f"{code}.json", f"{key} slots {sorted(set(s))} do not run 1..n")
        if se is not None and sz is not None and sorted(set(se)) != sorted(set(sz)):
            report.error("keys", "zh_cn.json", f"{key} slots {sorted(set(sz))} differ from en_us {sorted(set(se))}")

    # 5. family variants run .1..n, the same n in both files
    for base in sorted(families):
        counts = []
        for code, lang in langs.items():
            variants = sorted(int(k[len(base) + 1:]) for k in lang
                              if k.startswith(base + ".") and k[len(base) + 1:].isdigit())
            if variants and variants != list(range(1, len(variants) + 1)):
                report.error("keys", f"{code}.json", f"{base} variants {variants} do not run 1..n")
            counts.append(len(variants))
        if counts[0] != counts[1]:
            report.error("keys", "zh_cn.json", f"{base} has {counts[1]} variants, en_us has {counts[0]}")
    return len(required)


# ---------------------------------------------------------------------------------------------- main

def validate(root: Path = ROOT) -> Report:
    report = Report()
    facts = check_data(root, report)
    facts["keys_checked"] = check_keys(root, facts, report)
    facts["core_sources"] = check_purity(root, report)
    report.facts = facts  # type: ignore[attr-defined]
    return report


def main(argv: list[str] | None = None) -> int:
    report = validate(ROOT)
    for note in report.notes:
        print(f"note: {note}")
    if report.errors:
        for error in report.errors:
            print(error)
        print(f"world-sim validation failed: {len(report.errors)} problem(s)")
        return 1
    f = report.facts  # type: ignore[attr-defined]
    print(f"world-sim validation passed: {f.get('files', 0)} data files, {len(f.get('realms', {}))} realms, "
          f"{len(f.get('encounters', []))} encounters, {f.get('heritages', 0)} heritages, "
          f"{f.get('datapack_matched', 0)} techniques agree with the datapack, {f['keys_checked']} required language keys in both files, "
          f"{f['core_sources']} core sources pure")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
