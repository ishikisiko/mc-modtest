#!/usr/bin/env python3
"""Batch-export all stage-4 structure templates to Minecraft 1.21.1 NBT.

Scans examples/buildings/, examples/props/, examples/roads/ for *.json,
validates each structure (including metadata), exports out/1_21_1/<name>.nbt,
and writes a summary report to out/export_report.json.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from collections import OrderedDict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from json_to_nbt import (  # noqa: E402
    SUPPORTED_MC_VERSION,
    parse_block_state,
    structure_json_to_root_nbt,
    write_gzipped_nbt,
)
from validate_structure_json import ValidationError, validate_structure  # noqa: E402

SCAN_DIRS = ("examples/buildings", "examples/props", "examples/roads")
OUTPUT_DIR = "out/1_21_1"
REPORT_PATH = "out/export_report.json"


def export_one(json_path: str, output_dir: str) -> OrderedDict:
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    validate_structure(data)

    metadata = data.get("metadata")
    if not isinstance(metadata, dict) or "id" not in metadata:
        raise ValidationError("batch export requires a metadata block with an id")

    structure_name = metadata["id"].split(":", 1)[1].split("/")[-1]
    output_path = os.path.join(output_dir, f"{structure_name}.nbt")

    root = structure_json_to_root_nbt(data, SUPPORTED_MC_VERSION, None)
    write_gzipped_nbt(root, output_path)

    block_ids = sorted({
        parse_block_state(state_tag.value["Name"].value)[0]
        for state_tag in root.value["palette"].value
    })

    return OrderedDict([
        ("source", json_path.replace(os.sep, "/")),
        ("id", metadata["id"]),
        ("category", metadata["category"]),
        ("size", metadata["size"]),
        ("block_count", len(root.value["blocks"].value)),
        ("palette_count", len(root.value["palette"].value)),
        ("output_path", output_path.replace(os.sep, "/")),
        ("entrances", metadata.get("entrances", [])),
        ("connections", metadata.get("connections", [])),
        ("block_ids", block_ids),
    ])


def main() -> int:
    parser = argparse.ArgumentParser(description="Batch export structure templates to 1.21.1 NBT")
    parser.add_argument(
        "--mc-version",
        default=SUPPORTED_MC_VERSION,
        help=f"Target Minecraft version (only {SUPPORTED_MC_VERSION} is supported)",
    )
    parser.add_argument("--root", default=REPO_ROOT, help="Project root containing examples/ and out/")
    args = parser.parse_args()

    if args.mc_version != SUPPORTED_MC_VERSION:
        print(f"ERROR: unsupported --mc-version {args.mc_version!r}; only {SUPPORTED_MC_VERSION} is supported", file=sys.stderr)
        return 1

    output_dir = os.path.join(args.root, OUTPUT_DIR)
    structures = []
    failures = []

    for rel_dir in SCAN_DIRS:
        scan_dir = os.path.join(args.root, rel_dir)
        if not os.path.isdir(scan_dir):
            continue
        for filename in sorted(os.listdir(scan_dir)):
            if not filename.endswith(".json"):
                continue
            json_path = os.path.join(scan_dir, filename)
            rel_path = os.path.relpath(json_path, args.root).replace(os.sep, "/")
            try:
                entry = export_one(json_path, output_dir)
                entry["source"] = rel_path
                entry["output_path"] = os.path.relpath(
                    os.path.join(output_dir, os.path.basename(entry["output_path"])), args.root
                ).replace(os.sep, "/")
                structures.append(entry)
                print(f"OK    {rel_path} -> {entry['output_path']} "
                      f"(blocks={entry['block_count']}, palette={entry['palette_count']})")
            except (ValidationError, ValueError, KeyError, OSError, json.JSONDecodeError) as exc:
                failures.append(OrderedDict([("source", rel_path), ("error", str(exc))]))
                print(f"FAIL  {rel_path}: {exc}", file=sys.stderr)

    report = OrderedDict([
        ("mc_version", SUPPORTED_MC_VERSION),
        ("data_version", 3955),
        ("success_count", len(structures)),
        ("failure_count", len(failures)),
        ("structures", structures),
        ("failures", failures),
    ])
    report_path = os.path.join(args.root, REPORT_PATH)
    os.makedirs(os.path.dirname(report_path), exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
        f.write("\n")

    print(f"\nexported: {len(structures)}, failed: {len(failures)}")
    print(f"report: {os.path.relpath(report_path, args.root)}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
