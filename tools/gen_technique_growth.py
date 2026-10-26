#!/usr/bin/env python3
"""Own the four-technique growth rules and three survival manual recipes."""
from __future__ import annotations
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path("tools/technique_growth/sample.json")


def outputs(root: Path = ROOT) -> dict[Path, str]:
    source = json.loads((root / SOURCE).read_text())
    rules = source["rules"]
    result = {Path("src/main/resources/data/myvillage/cultivation/technique_growth.json"): rules}
    for manual in source["manuals"]:
        key = "myvillage:" + manual["technique"]
        if key not in rules["techniques"]:
            raise ValueError("manual outside growth sample: " + key)
        path = Path("src/main/resources/data/myvillage/recipe/manual_" + manual["technique"] + ".json")
        if path in result:
            raise ValueError("duplicate manual: " + key)
        result[path] = {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in manual["ingredients"]],
            "result": {"id": "myvillage:" + manual["item"], "count": 1, "components": {"myvillage:technique": key}},
        }
        recipe_id = "myvillage:manual_" + manual["technique"]
        result[Path("src/main/resources/data/myvillage/advancement/recipes/manual_" + manual["technique"] + ".json")] = {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_book": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "minecraft:book"}]}},
                "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe_id}},
            },
            "requirements": [["has_book", "has_the_recipe"]],
            "rewards": {"recipes": [recipe_id]},
        }
    # New content is Chinese in either locale; existing compatibility translations are untouched.
    for locale in ("zh_cn", "en_us"):
        path = Path(f"src/main/resources/assets/myvillage/lang/{locale}.json")
        language = json.loads((root / path).read_text())
        language.update(source["messages"])
        result[path] = language
    return {p: json.dumps(v, ensure_ascii=False, indent=2) + "\n" for p, v in result.items()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for path, text in outputs().items():
        target = ROOT / path
        if target.exists() and target.read_text() == text:
            continue
        stale.append(str(path))
        if not args.check:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
    print("technique growth: " + ("current" if not stale else ", ".join(stale)))
    return int(args.check and bool(stale))


if __name__ == "__main__":
    raise SystemExit(main())
