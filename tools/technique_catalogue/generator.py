"""Build every technique, school and heritage output from the catalogue source tables.

``plan(root)`` computes the full set of outputs without touching the tree; ``write(root)`` applies it and
``check(root)`` lists every output that differs. Owned outputs:

- ``data/myvillage/myvillage/technique/<id>.json`` for every catalogue row (hand-written techniques named
  in ``rules.json`` ``hand_written_techniques`` are never written or removed); any other JSON file in that
  directory is stale and removed.
- ``data/myvillage/myvillage/school/<id>.json`` and ``heritage/<id>.json``: the whole directories.
- ``data/myvillage/world_sim/techniques.json`` and ``heritages.json``.
- In ``assets/myvillage/lang/{en_us,zh_cn}.json``: the keys ``cultivation.technique.myvillage.<id>``,
  ``cultivation.school.myvillage.<id>`` and ``cultivation.heritage.myvillage.<id>``. Existing keys are
  updated in place, new keys appended as a group at the end, stale ones removed; every other key and the
  file's order stay as they are. Both languages carry the Chinese name.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

SOURCE_DIR = Path(__file__).resolve().parent
ROOT = SOURCE_DIR.parents[1]

DATAPACK_REL = Path("src/main/resources/data/myvillage/myvillage")
TECHNIQUE_REL = DATAPACK_REL / "technique"
SCHOOL_REL = DATAPACK_REL / "school"
HERITAGE_REL = DATAPACK_REL / "heritage"
REALM_REL = DATAPACK_REL / "realm"
WORLD_SIM_REL = Path("src/main/resources/data/myvillage/world_sim")
LANG_REL = Path("src/main/resources/assets/myvillage/lang")
LANGS = ("en_us", "zh_cn")

SCHEMA = 1
CATEGORIES = ("core", "active", "movement", "body")
ELEMENTS = ("metal", "wood", "water", "fire", "earth")
ELEMENTS_OR_NONE = ELEMENTS + ("none",)
SCHOOL_KINDS = ("weapon", "special")
SCHOOL_RUNTIMES = ("flying_sword", "spell", "talisman")
GRADE_RANGE = range(1, 5)
HERITAGE_LENGTH = range(2, 5)
ID = re.compile(r"[a-z0-9_]+")
LANG_SECTIONS = ("technique", "school", "heritage")


class CatalogueError(Exception):
    """The source tables (or a file the generator must merge into) are not usable; lists every problem."""

    def __init__(self, problems: list[str]) -> None:
        super().__init__("\n".join(problems))
        self.problems = problems


@dataclass
class Sources:
    rules: dict
    techniques: list[dict]
    schools: list[dict]
    heritages: list[dict]

    @property
    def namespace(self) -> str:
        return self.rules["namespace"]

    def rid(self, path: str) -> str:
        return f"{self.namespace}:{path}"


@dataclass
class Plan:
    files: dict[Path, str] = field(default_factory=dict)  # repo-relative path -> full text
    deletions: list[Path] = field(default_factory=list)   # repo-relative stale outputs


# ---------------------------------------------------------------------------------------------- sources

def _unique_pairs(pairs: list[tuple[str, Any]]) -> dict:
    out: dict = {}
    for key, value in pairs:
        if key in out:
            raise ValueError(f"duplicate key {key!r}")
        out[key] = value
    return out


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_unique_pairs)


def load_sources(source_dir: Path = SOURCE_DIR) -> Sources:
    problems: list[str] = []
    docs: dict[str, Any] = {}
    for name in ("rules.json", "catalogue.json", "schools.json", "heritages.json"):
        try:
            doc = _read_json(source_dir / name)
        except (OSError, ValueError) as exc:
            problems.append(f"{name}: cannot be read ({exc})")
            continue
        if not isinstance(doc, dict) or doc.get("schema") != SCHEMA:
            problems.append(f"{name}: schema must be {SCHEMA}")
            continue
        docs[name] = doc
    if problems:
        raise CatalogueError(problems)
    sources = Sources(
        rules=docs["rules.json"],
        techniques=docs["catalogue.json"].get("techniques") or [],
        schools=docs["schools.json"].get("schools") or [],
        heritages=docs["heritages.json"].get("heritages") or [],
    )
    problems.extend(validate_sources(sources))
    if problems:
        raise CatalogueError(problems)
    return sources


def _check_ids(rows: list[dict], file: str, problems: list[str]) -> dict[str, dict]:
    by_id: dict[str, dict] = {}
    for i, row in enumerate(rows):
        rid = row.get("id") if isinstance(row, dict) else None
        if not isinstance(rid, str) or not ID.fullmatch(rid):
            problems.append(f"{file}: row {i} needs an id of [a-z0-9_]+")
            continue
        if rid in by_id:
            problems.append(f"{file}: duplicate id {rid!r}")
            continue
        if not isinstance(row.get("zh"), str) or not row["zh"]:
            problems.append(f"{file}: {rid}: zh must be a non-empty string")
        by_id[rid] = row
    names = [r.get("zh") for r in by_id.values()]
    for name in sorted({n for n in names if isinstance(n, str) and names.count(n) > 1}):
        problems.append(f"{file}: duplicate name {name!r}")
    return by_id


def validate_sources(s: Sources) -> list[str]:
    problems: list[str] = []
    rules = s.rules
    if not isinstance(rules.get("namespace"), str) or not ID.fullmatch(rules["namespace"]):
        problems.append("rules.json: namespace must be [a-z0-9_]+")
    grades = rules.get("grades")
    if not isinstance(grades, dict) or sorted(grades.values()) != list(GRADE_RANGE):
        problems.append("rules.json: grades must map four sim grades onto 1..4")
    by_grade = (rules.get("requirements") or {}).get("by_grade")
    if not isinstance(by_grade, dict) or sorted(by_grade) != [str(g) for g in GRADE_RANGE]:
        problems.append("rules.json: requirements.by_grade needs exactly the grades 1..4")
    affinity = (rules.get("requirements") or {}).get("element_affinity")
    if not isinstance(affinity, dict) or not isinstance(affinity.get("minimum_grade"), int) \
            or not isinstance(affinity.get("basis_points"), int) or not 0 <= affinity["basis_points"] <= 10000:
        problems.append("rules.json: requirements.element_affinity needs minimum_grade and basis_points 0..10000")
    if not isinstance(rules.get("effects"), dict) or any(k not in CATEGORIES for k in rules["effects"]):
        problems.append(f"rules.json: effects keys must be categories {list(CATEGORIES)}")
    hand_written = rules.get("hand_written_techniques")
    if not isinstance(hand_written, list) or not all(isinstance(h, str) for h in hand_written):
        problems.append("rules.json: hand_written_techniques must be a list of ids")
        hand_written = []
    if problems:
        return problems

    schools = _check_ids(s.schools, "schools.json", problems)
    for sid, row in schools.items():
        kind = row.get("kind")
        if kind not in SCHOOL_KINDS:
            problems.append(f"schools.json: {sid}: kind must be one of {list(SCHOOL_KINDS)}")
        if kind == "weapon" and (not isinstance(row.get("weapon_family"), str) or not row["weapon_family"]):
            problems.append(f"schools.json: {sid}: a weapon school needs a weapon_family")
        if kind == "special" and row.get("runtime") not in SCHOOL_RUNTIMES:
            problems.append(f"schools.json: {sid}: a special school needs a runtime in {list(SCHOOL_RUNTIMES)}")
        lean = row.get("element_lean")
        if not isinstance(lean, list) or any(e not in ELEMENTS for e in lean) or len(set(lean)) != len(lean):
            problems.append(f"schools.json: {sid}: element_lean must be a list of distinct elements")

    techniques = _check_ids(s.techniques, "catalogue.json", problems)
    for tid, row in techniques.items():
        where = f"catalogue.json: {tid}"
        if tid in hand_written:
            problems.append(f"{where}: is hand-written (rules.json hand_written_techniques); keep it out of the catalogue")
        if row.get("category") not in CATEGORIES:
            problems.append(f"{where}: category must be one of {list(CATEGORIES)}")
        grade = row.get("grade")
        if not isinstance(grade, int) or isinstance(grade, bool) or grade not in GRADE_RANGE:
            problems.append(f"{where}: grade must be an integer 1..4")
        if row.get("element") not in ELEMENTS_OR_NONE:
            problems.append(f"{where}: element must be one of {list(ELEMENTS_OR_NONE)}")
        if row.get("school") is not None and row.get("school") not in schools:
            problems.append(f"{where}: school {row.get('school')!r} is not in schools.json")
        prev = row.get("previous")
        if prev is not None and (prev not in techniques or prev == tid):
            problems.append(f"{where}: previous {prev!r} is not another catalogue technique")
        unknown = set(row) - {"id", "zh", "category", "grade", "element", "school", "previous"}
        if unknown:
            problems.append(f"{where}: unknown fields {sorted(unknown)}")
    problems.extend(lineage_cycles({t: r.get("previous") for t, r in techniques.items()}, "catalogue.json"))

    heritages = _check_ids(s.heritages, "heritages.json", problems)
    owner: dict[str, str] = {}
    for hid, row in heritages.items():
        where = f"heritages.json: {hid}"
        school = row.get("school")
        if school is not None and school not in schools:
            problems.append(f"{where}: school {school!r} is not in schools.json")
        if not isinstance(row.get("exclusive"), bool):
            problems.append(f"{where}: exclusive must be true or false")
        chain = row.get("techniques")
        if not isinstance(chain, list) or len(chain) not in HERITAGE_LENGTH:
            problems.append(f"{where}: techniques must list 2 to 4 ids")
            continue
        missing = [t for t in chain if t not in techniques]
        if missing:
            problems.append(f"{where}: unknown techniques {missing}")
            continue
        for t in chain:
            if t in owner:
                problems.append(f"{where}: {t} already belongs to heritage {owner[t]}")
            owner[t] = hid
        if len({techniques[t]["element"] for t in chain}) != 1:
            problems.append(f"{where}: techniques do not share one element")
        for t in chain:
            if techniques[t].get("school") != school:
                problems.append(f"{where}: {t} has school {techniques[t].get('school')!r}, the heritage {school!r}")
        expected = [None] + chain[:-1]
        for t, prev in zip(chain, expected):
            if techniques[t].get("previous") != prev:
                problems.append(f"{where}: {t} must have previous {prev!r}, has {techniques[t].get('previous')!r}")
    return problems


def lineage_cycles(previous: dict[str, str | None], file: str) -> list[str]:
    """Every ``previous`` chain must end; names each cycle once, by its smallest id."""
    problems: list[str] = []
    reported: set[frozenset[str]] = set()
    for start in sorted(previous):
        seen: list[str] = []
        node: str | None = start
        while node is not None and node in previous and node not in seen:
            seen.append(node)
            node = previous[node]
        if node is not None and node in seen:
            cycle = frozenset(seen[seen.index(node):])
            if cycle not in reported:
                reported.add(cycle)
                problems.append(f"{file}: lineage.previous cycle through {sorted(cycle)}")
    return problems


# ---------------------------------------------------------------------------------------------- outputs

def dump(doc: Any) -> str:
    return json.dumps(doc, ensure_ascii=False, indent=2) + "\n"


def resolve_requirements(root: Path, s: Sources) -> dict[int, tuple[str, str]]:
    """Grade -> (realm id, stage id), the stage taken by position from the player realm file."""
    out: dict[int, tuple[str, str]] = {}
    problems: list[str] = []
    for grade_key, spec in sorted(s.rules["requirements"]["by_grade"].items()):
        realm = spec.get("realm")
        number = spec.get("stage_number")
        path = root / REALM_REL / f"{realm}.json"
        try:
            doc = _read_json(path)
        except (OSError, ValueError) as exc:
            problems.append(f"rules.json: requirements.by_grade.{grade_key}: realm {realm!r} unreadable ({exc})")
            continue
        stages = sorted(doc.get("stages") or [], key=lambda st: st.get("sort_order", 0))
        if not isinstance(number, int) or not 1 <= number <= len(stages):
            problems.append(f"rules.json: requirements.by_grade.{grade_key}: realm {realm} has no stage {number!r}")
            continue
        out[int(grade_key)] = (s.rid(realm), stages[number - 1]["id"])
    if problems:
        raise CatalogueError(problems)
    return out


def technique_doc(row: dict, s: Sources, requirements: dict[int, tuple[str, str]]) -> dict:
    grade = row["grade"]
    element = row["element"]
    realm, stage = requirements[grade]
    req: dict[str, Any] = {"minimum_realm": realm, "minimum_stage": stage}
    affinity = s.rules["requirements"]["element_affinity"]
    if element != "none" and grade >= affinity["minimum_grade"]:
        req["minimum_element_affinity"] = {s.rid(element): affinity["basis_points"]}
    doc: dict[str, Any] = {
        "translation_key": f"cultivation.technique.{s.namespace}.{row['id']}",
        "category": row["category"],
        "grade": grade,
        "elements": [] if element == "none" else [s.rid(element)],
    }
    if row.get("school") is not None:
        doc["school"] = s.rid(row["school"])
    doc["requirements"] = req
    if row.get("previous") is not None:
        doc["lineage"] = {"previous": s.rid(row["previous"])}
    effects = s.rules["effects"].get(row["category"])
    if effects:
        doc["effects"] = effects
    return doc


def school_doc(row: dict, s: Sources) -> dict:
    return {
        "translation_key": f"cultivation.school.{s.namespace}.{row['id']}",
        "kind": row["kind"],
        "weapon_family": row.get("weapon_family"),
        "runtime": row.get("runtime"),
        "element_lean": [s.rid(e) for e in row["element_lean"]],
    }


def heritage_doc(row: dict, s: Sources) -> dict:
    doc: dict[str, Any] = {"translation_key": f"cultivation.heritage.{s.namespace}.{row['id']}"}
    if row.get("school") is not None:
        doc["school"] = s.rid(row["school"])
    doc["techniques"] = [s.rid(t) for t in row["techniques"]]
    doc["exclusive"] = row["exclusive"]
    return doc


def _listing(key: str, items: list[dict]) -> str:
    """The ledger's house style: one compact object per line."""
    lines = ",\n".join("    " + json.dumps(item, ensure_ascii=False) for item in items)
    return f'{{\n  "schema": {SCHEMA},\n  "{key}": [\n{lines}\n  ]\n}}\n'


def world_sim_techniques(s: Sources) -> str:
    sim_grade = {v: k for k, v in s.rules["grades"].items()}
    return _listing("techniques", [
        {"id": t["id"], "name": t["zh"], "grade": sim_grade[t["grade"]], "element": t["element"]}
        for t in s.techniques
    ])


def world_sim_heritages(s: Sources) -> str:
    return _listing("heritages", [
        {"id": h["id"], "name": h["zh"], "school": h["school"] if h.get("school") is not None else "none",
         "techniques": list(h["techniques"])}
        for h in s.heritages
    ])


def lang_entries(s: Sources) -> dict[str, str]:
    out: dict[str, str] = {}
    for section, rows in (("technique", s.techniques), ("school", s.schools), ("heritage", s.heritages)):
        for row in rows:
            out[f"cultivation.{section}.{s.namespace}.{row['id']}"] = row["zh"]
    return out


def managed_key(key: str, namespace: str) -> bool:
    """``cultivation.<technique|school|heritage>.<namespace>.<id>``: the keys this generator owns."""
    sections = "|".join(LANG_SECTIONS)
    return re.fullmatch(rf"cultivation\.(?:{sections})\.{re.escape(namespace)}\.[a-z0-9_]+", key) is not None


def merge_lang(text: str, entries: dict[str, str], namespace: str, preserved: set[str], file: str) -> str:
    """Update ``entries`` in place, append the new ones at the end, drop stale managed keys.

    Refuses a file that is not exactly 2-space-indented JSON with unique keys, so no other key is ever
    reformatted, reordered or lost.
    """
    try:
        doc = json.loads(text, object_pairs_hook=_unique_pairs)
    except ValueError as exc:
        raise CatalogueError([f"{file}: cannot be read ({exc})"]) from exc
    if dump(doc) != text:
        raise CatalogueError([f"{file}: is not 2-space-indented JSON as written by json.dumps; refusing to rewrite"])
    merged: dict[str, str] = {}
    for key, value in doc.items():
        if key in entries:
            merged[key] = entries[key]
        elif managed_key(key, namespace) and key not in preserved:
            continue
        else:
            merged[key] = value
    for key, value in entries.items():
        if key not in merged:
            merged[key] = value
    return dump(merged)


def plan(root: Path = ROOT, source_dir: Path = SOURCE_DIR) -> Plan:
    s = load_sources(source_dir)
    requirements = resolve_requirements(root, s)
    result = Plan()
    hand_written = set(s.rules["hand_written_techniques"])
    owned = (
        (TECHNIQUE_REL, {t["id"]: technique_doc(t, s, requirements) for t in s.techniques}, hand_written),
        (SCHOOL_REL, {r["id"]: school_doc(r, s) for r in s.schools}, set()),
        (HERITAGE_REL, {r["id"]: heritage_doc(r, s) for r in s.heritages}, set()),
    )
    for rel_dir, docs, keep in owned:
        for rid, doc in docs.items():
            result.files[rel_dir / f"{rid}.json"] = dump(doc)
        directory = root / rel_dir
        if directory.is_dir():
            for path in sorted(directory.rglob("*.json")):
                stem = path.relative_to(directory).with_suffix("").as_posix()
                if stem not in docs and stem not in keep:
                    result.deletions.append(path.relative_to(root))
    result.files[WORLD_SIM_REL / "techniques.json"] = world_sim_techniques(s)
    result.files[WORLD_SIM_REL / "heritages.json"] = world_sim_heritages(s)
    entries = lang_entries(s)
    preserved = {f"cultivation.technique.{s.namespace}.{h}" for h in hand_written}
    for code in LANGS:
        rel = LANG_REL / f"{code}.json"
        try:
            text = (root / rel).read_text(encoding="utf-8")
        except OSError as exc:
            raise CatalogueError([f"{rel}: cannot be read ({exc})"]) from exc
        result.files[rel] = merge_lang(text, entries, s.namespace, preserved, rel.as_posix())
    return result


def check(root: Path = ROOT, source_dir: Path = SOURCE_DIR) -> list[str]:
    """Every output that differs from what ``write`` would produce, as ``<path>: <reason>``."""
    p = plan(root, source_dir)
    out: list[str] = []
    for rel, text in sorted(p.files.items()):
        path = root / rel
        if not path.is_file():
            out.append(f"{rel.as_posix()}: missing")
        elif path.read_text(encoding="utf-8") != text:
            out.append(f"{rel.as_posix()}: differs from the generated content")
    for rel in p.deletions:
        out.append(f"{rel.as_posix()}: stale (not in the catalogue)")
    return out


def write(root: Path = ROOT, source_dir: Path = SOURCE_DIR) -> list[str]:
    """Apply the plan; returns the changed paths (empty when everything was current)."""
    p = plan(root, source_dir)
    changed: list[str] = []
    for rel, text in sorted(p.files.items()):
        path = root / rel
        if path.is_file() and path.read_text(encoding="utf-8") == text:
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        changed.append(rel.as_posix())
    for rel in p.deletions:
        (root / rel).unlink()
        changed.append(f"{rel.as_posix()} (removed)")
    return changed
