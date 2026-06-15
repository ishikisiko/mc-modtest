#!/usr/bin/env python3
"""Validate generated tavern and lord manor civic resources."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from buildgen.modset import load_modset
from validate_generated_structures import validate_file

PROJECT_ROOT = Path(__file__).resolve().parents[1]
MOD_ID = "myvillage"
RES = PROJECT_ROOT / "src" / "main" / "resources" / "data" / MOD_ID
OUT_REPORT = PROJECT_ROOT / "reports" / "civic_library_validation.json"
EXPECTED_NAMES = [*(f"tavern_{i:03d}" for i in range(1, 6)),
                  *(f"lord_manor_{i:03d}" for i in range(1, 4))]


def validate_functions(names: list[str]) -> list[str]:
    errors: list[str] = []
    gallery = RES / "function" / "gallery" / "civic.mcfunction"
    if not gallery.is_file():
        errors.append(f"missing_gallery: {gallery.relative_to(PROJECT_ROOT)}")
    else:
        text = gallery.read_text(encoding="utf-8")
        positions = []
        for name in names:
            needle = f"place template {MOD_ID}:{name} "
            if needle not in text:
                errors.append(f"gallery_missing_structure: {name}")
        pat = re.compile(r"^place template myvillage:(tavern_\d{3}|lord_manor_\d{3}) ~(-?\d+)? ~(-?\d+)? ~(-?\d+)?$", re.MULTILINE)
        for match in pat.finditer(text):
            positions.append((match.group(1), int(match.group(2) or 0), int(match.group(4) or 0)))
        ordered = [name for name, _, _ in positions]
        if ordered[:len(names)] != names:
            errors.append(f"gallery_order: {ordered} != {names}")
        for (_, x1, z1), (_, x2, z2) in zip(positions, positions[1:]):
            if abs(x1 - x2) < 60 and abs(z1 - z2) < 60:
                errors.append(f"gallery_spacing: {(x1, z1)} vs {(x2, z2)}")
    place_pat = re.compile(r"^place template myvillage:\w+ ~ ~-1 ~$")
    for name in names:
        path = RES / "function" / "place" / f"{name}.mcfunction"
        if not path.is_file():
            errors.append(f"missing_place_function: {name}")
            continue
        line = path.read_text(encoding="utf-8").strip()
        if not place_pat.match(line):
            errors.append(f"bad_place_function: {name}: {line}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--structure-dir", default=str(RES / "structure"))
    parser.add_argument("--report", default=str(OUT_REPORT))
    parser.add_argument("--profile", default="full", choices=("vanilla", "full"),
                        help="modset profile: 'vanilla' forbids all mod ids, 'full' allows confirmed catalog ids")
    args = parser.parse_args()
    modset = load_modset(args.profile)

    structure_dir = Path(args.structure_dir)
    if not structure_dir.is_absolute():
        structure_dir = (PROJECT_ROOT / structure_dir).resolve()
    report_path = Path(args.report)
    if not report_path.is_absolute():
        report_path = (PROJECT_ROOT / report_path).resolve()

    results = []
    errors: list[str] = []
    for name in EXPECTED_NAMES:
        path = structure_dir / f"{name}.nbt"
        if not path.is_file():
            result = {"path": f"{name}.nbt", "passed": False,
                      "errors": ["missing_file"], "warnings": []}
        else:
            result = validate_file(path, structure_dir, modset)
        results.append(result)
        if not result["passed"]:
            errors.extend(f"{result['path']}: {message}" for message in result["errors"])

    fn_errors = validate_functions(EXPECTED_NAMES)
    errors.extend(fn_errors)

    for result in results:
        status = "OK  " if result["passed"] else "FAIL"
        print(f"{status} {result['path']:24s} size={result.get('size')} blocks={result.get('block_count')}")
        for error in result["errors"]:
            print(f"     - {error}")
    for error in fn_errors:
        print(f"FAIL functions: {error}")

    summary = {
        "passed": not errors,
        "expected": EXPECTED_NAMES,
        "total": len(results),
        "errors": errors,
        "results": results,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"\n{sum(1 for r in results if r['passed'])}/{len(results)} civic structures passed")
    print(f"report: {report_path.relative_to(PROJECT_ROOT)}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
