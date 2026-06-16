#!/usr/bin/env python3
"""Generate all NeoForge-verifiable structures into the mod resources tree.

This is the canonical mod validation entrypoint. It writes the hand-authored
JSON DSL smoke-test structure plus generated building, compound, and civic
libraries into:

    src/main/resources/data/myvillage/structure/
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_OUTPUT = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"
DEFAULT_FUNCTIONS = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "function"
DEFAULT_SETTLEMENT_META = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "settlement_meta"

if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from json_to_nbt import SUPPORTED_MC_VERSION, structure_json_to_root_nbt, write_gzipped_nbt  # noqa: E402
from validate_structure_json import ValidationError, validate_structure  # noqa: E402


def clean_nbt_tree(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for path in sorted(output_dir.rglob("*.nbt")):
        path.unlink()
    for path in sorted((p for p in output_dir.rglob("*") if p.is_dir()), reverse=True):
        try:
            path.rmdir()
        except OSError:
            pass


def clean_generated_functions() -> None:
    for folder in (DEFAULT_FUNCTIONS / "gallery", DEFAULT_FUNCTIONS / "place"):
        if not folder.is_dir():
            continue
        for path in sorted(folder.glob("*.mcfunction")):
            path.unlink()


def clean_settlement_metadata() -> None:
    if not DEFAULT_SETTLEMENT_META.is_dir():
        return
    for path in sorted(DEFAULT_SETTLEMENT_META.glob("*.json")):
        path.unlink()


def generate_test_house(mc_version: str, output_dir: Path) -> Path:
    source = REPO_ROOT / "examples" / "test_house_03.json"
    with source.open("r", encoding="utf-8") as f:
        data = json.load(f)
    validate_structure(data)
    root = structure_json_to_root_nbt(data, mc_version)
    target = output_dir / "test_house_03.nbt"
    write_gzipped_nbt(root, str(target))
    return target


def generate_building_library(style: str, count: int, base_seed: int, profile: str) -> None:
    cmd = [
        sys.executable,
        str(SCRIPT_DIR / "generate_building_library.py"),
        "--style",
        style,
        "--count",
        str(count),
        "--base-seed",
        str(base_seed),
        "--profile",
        profile,
    ]
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def generate_compound_library(count: int, base_seed: int, profile: str) -> None:
    cmd = [
        sys.executable,
        str(SCRIPT_DIR / "generate_compound_library.py"),
        "--count",
        str(count),
        "--base-seed",
        str(base_seed),
        "--profile",
        profile,
    ]
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def generate_civic_library(style: str, base_seed: int, profile: str) -> None:
    cmd = [
        sys.executable,
        str(SCRIPT_DIR / "generate_civic_library.py"),
        "--style",
        style,
        "--base-seed",
        str(base_seed),
        "--profile",
        profile,
    ]
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def generate_group_building_library(group: str, count: int, base_seed: int, profile: str) -> None:
    cmd = [
        sys.executable,
        str(SCRIPT_DIR / "generate_building_library.py"),
        "--group",
        group,
        "--count",
        str(count),
        "--base-seed",
        str(base_seed),
        "--profile",
        profile,
    ]
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def generate_group_compound_library(group: str, count: int, base_seed: int, profile: str) -> None:
    cmd = [
        sys.executable,
        str(SCRIPT_DIR / "generate_compound_library.py"),
        "--group",
        group,
        "--count",
        str(count),
        "--base-seed",
        str(base_seed),
        "--profile",
        profile,
    ]
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def generate_mod_block_fallbacks() -> None:
    cmd = [
        sys.executable,
        str(SCRIPT_DIR / "generate_mod_block_fallbacks.py"),
    ]
    subprocess.run(cmd, cwd=REPO_ROOT, check=True)


def copy_default_output(output_dir: Path) -> None:
    if output_dir.resolve() == DEFAULT_OUTPUT.resolve():
        return
    for source in DEFAULT_OUTPUT.rglob("*.nbt"):
        relative = source.relative_to(DEFAULT_OUTPUT)
        target = output_dir / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mc-version", default=SUPPORTED_MC_VERSION)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--style", default="medieval_village")
    parser.add_argument("--profile", default="full", choices=("vanilla", "full"),
                        help="modset profile passed to every sub-generator")
    parser.add_argument("--count", type=int, default=10)
    parser.add_argument("--base-seed", type=int, default=20260612)
    parser.add_argument("--compound-count", type=int, default=6)
    parser.add_argument("--compound-base-seed", type=int, default=20260614)
    parser.add_argument("--civic-base-seed", type=int, default=20260615)
    parser.add_argument("--cultivation-town-building-count", type=int, default=3)
    parser.add_argument("--cultivation-town-building-base-seed", type=int, default=20260613)
    parser.add_argument("--cultivation-town-count", type=int, default=6)
    parser.add_argument("--cultivation-town-base-seed", type=int, default=20260617)
    parser.add_argument("--cultivation-sect-count", type=int, default=2)
    parser.add_argument("--cultivation-sect-base-seed", type=int, default=20260612)
    parser.add_argument("--cultivation-sect-compound-count", type=int, default=2)
    parser.add_argument("--cultivation-sect-compound-base-seed", type=int, default=20260616)
    args = parser.parse_args()

    if args.mc_version != SUPPORTED_MC_VERSION:
        parser.error(f"unsupported --mc-version {args.mc_version!r}; only {SUPPORTED_MC_VERSION} is supported")

    output_dir = (REPO_ROOT / args.output).resolve() if not Path(args.output).is_absolute() else Path(args.output)
    try:
        clean_nbt_tree(output_dir)
        clean_generated_functions()
        clean_settlement_metadata()
        test_house = generate_test_house(args.mc_version, output_dir)
        generate_building_library(args.style, args.count, args.base_seed, args.profile)
        generate_compound_library(args.compound_count, args.compound_base_seed, args.profile)
        generate_civic_library(args.style, args.civic_base_seed, args.profile)
        generate_group_building_library(
            "cultivation_town", args.cultivation_town_building_count,
            args.cultivation_town_building_base_seed, args.profile)
        generate_group_compound_library(
            "cultivation_town", args.cultivation_town_count,
            args.cultivation_town_base_seed, args.profile)
        generate_group_building_library(
            "cultivation_sect", args.cultivation_sect_count,
            args.cultivation_sect_base_seed, args.profile)
        generate_group_compound_library(
            "cultivation_sect", args.cultivation_sect_compound_count,
            args.cultivation_sect_compound_base_seed, args.profile)
        if args.profile == "full":
            generate_mod_block_fallbacks()
        copy_default_output(output_dir)
    except (OSError, ValueError, ValidationError, subprocess.CalledProcessError) as exc:
        print(f"GENERATION FAILED: {exc}", file=sys.stderr)
        return 1

    structures = sorted(path.relative_to(output_dir).as_posix() for path in output_dir.rglob("*.nbt"))
    print(f"generated structure root: {output_dir.relative_to(REPO_ROOT)}")
    print(f"generated smoke test: {test_house.relative_to(REPO_ROOT)}")
    print(f"generated NBT count: {len(structures)}")
    for structure in structures:
        print(f"- {structure}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
