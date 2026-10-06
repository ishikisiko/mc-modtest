"""One-off seeding of ``catalogue.json`` from the ledger's ``world_sim/techniques.json``.

``catalogue.json`` is the source of truth once it exists; this step only ran to create it and refuses to
overwrite it without ``--force``. Each technique keeps its id, Chinese name and order; the sim grade maps
through ``rules.json`` ``grades``; category and school come from the first ``classification`` rule whose
``name_contains`` fragment occurs in the name (the default otherwise); ``previous`` starts empty.

Usage: python3 -m tools.technique_catalogue.importer [--force]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import generator

CATALOGUE_KEYS = ("id", "zh", "category", "grade", "element", "school", "previous")


def classify(name: str, rules: dict) -> tuple[str, str | None]:
    """(category, school) for a technique name under ``rules.json`` ``classification``."""
    table = rules["classification"]
    for rule in table["rules"]:
        if any(fragment in name for fragment in rule["name_contains"]):
            return rule["category"], rule["school"]
    return table["default"]["category"], table["default"]["school"]


def rows_from_world_sim(doc: dict, rules: dict) -> list[dict]:
    rows = []
    for t in doc["techniques"]:
        category, school = classify(t["name"], rules)
        rows.append({"id": t["id"], "zh": t["name"], "category": category, "grade": rules["grades"][t["grade"]],
                     "element": t["element"], "school": school, "previous": None})
    return rows


def catalogue_text(rows: list[dict]) -> str:
    """The catalogue's layout: one row per line, fields in ``CATALOGUE_KEYS`` order, then the optional
    hand-set ``effects`` override when the row has one."""
    ordered = [{k: row[k] for k in CATALOGUE_KEYS} | ({"effects": row["effects"]} if "effects" in row else {})
               for row in rows]
    return generator._listing("techniques", ordered)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--force", action="store_true", help="overwrite an existing catalogue.json")
    parser.add_argument("--root", type=Path, default=generator.ROOT)
    parser.add_argument("--source-dir", type=Path, default=generator.SOURCE_DIR)
    args = parser.parse_args(argv)
    target = args.source_dir / "catalogue.json"
    if target.exists() and not args.force:
        print(f"{target} exists and is the source of truth; pass --force to re-seed it", file=sys.stderr)
        return 1
    rules = json.loads((args.source_dir / "rules.json").read_text(encoding="utf-8"))
    sim = json.loads((args.root / generator.WORLD_SIM_REL / "techniques.json").read_text(encoding="utf-8"))
    rows = rows_from_world_sim(sim, rules)
    target.write_text(catalogue_text(rows), encoding="utf-8")
    print(f"wrote {target} ({len(rows)} techniques)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
