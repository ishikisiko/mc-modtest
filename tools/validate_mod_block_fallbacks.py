#!/usr/bin/env python3
"""Validate runtime mod-block fallbacks against shipped structure palettes."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_STRUCTURE_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"
DEFAULT_FALLBACK_MAP = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "mod_block_fallbacks.json"
DEFAULT_REPORT = REPO_ROOT / "reports" / "mod_block_fallback_validation.json"
BLOCKS_121 = REPO_ROOT / "docs" / "ai-kb" / "references" / "blocks_121.json"

if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.modset import SELF_NAMESPACE, load_modset  # noqa: E402
from buildgen.nbtread import read_gzipped_nbt, state_string  # noqa: E402
from buildgen.style import VANILLA_NAMESPACE, _block_id, _namespace  # noqa: E402
from json_to_nbt import parse_block_state  # noqa: E402


def load_vanilla_blocks() -> set[str]:
    with BLOCKS_121.open("r", encoding="utf-8") as f:
        return {f"minecraft:{name}" for name in json.load(f)}


def iter_palette_states(root: dict) -> Iterable[str]:
    for entry in root.get("palette", []):
        yield state_string(entry)
    for palette in root.get("palettes", []):
        for entry in palette:
            yield state_string(entry)


def shipped_mod_blocks(structure_dir: Path, profile: str) -> tuple[set[str], set[str], set[str], list[str]]:
    """Scan shipped structures for non-vanilla palette ids.

    Returns ``(external_blocks, self_blocks, hero_variants, errors)``:

    - ``external_blocks`` — third-party mod block ids (need a fallback);
    - ``self_blocks`` — ``myvillage:`` self-namespace block ids (the mod ships
      these natively, so they are fallback-exempt under both profiles);
    - ``hero_variants`` — the ``hero_taihu_*`` ``rockery_block`` variant values
      seen, so the hero 假山 cluster's acceptance is explicit (add-hero-rockery
      task 4.4).
    """
    modset = load_modset(profile)
    blocks: set[str] = set()
    self_blocks: set[str] = set()
    hero_variants: set[str] = set()
    errors: list[str] = []
    for path in sorted(structure_dir.rglob("*.nbt")):
        rel = path.relative_to(structure_dir).as_posix()
        try:
            _, root = read_gzipped_nbt(str(path))
        except Exception as exc:
            errors.append(f"{rel}: nbt_parse: {exc}")
            continue
        palette = list(iter_palette_states(root))
        for message in modset.palette_block_errors(palette):
            errors.append(f"{rel}: {message}")
        for state in palette:
            block = _block_id(state)
            namespace = _namespace(block)
            if namespace == SELF_NAMESPACE:
                self_blocks.add(block)
                if block == f"{SELF_NAMESPACE}:rockery_block":
                    _, props = parse_block_state(state)
                    variant = props.get("variant", "")
                    if variant.startswith("hero_taihu_"):
                        hero_variants.add(variant)
            elif namespace != VANILLA_NAMESPACE:
                blocks.add(block)
    return blocks, self_blocks, hero_variants, errors


def validate_fallback_value(block: str, fallback: object, vanilla_blocks: set[str]) -> list[str]:
    errors: list[str] = []
    if not isinstance(fallback, str):
        return [f"{block}: fallback is not a string: {fallback!r}"]
    try:
        fallback_block, _props = parse_block_state(fallback)
    except ValueError as exc:
        return [f"{block}: invalid fallback state {fallback!r}: {exc}"]
    if _namespace(fallback_block) != VANILLA_NAMESPACE:
        errors.append(f"{block}: fallback is not minecraft namespaced: {fallback!r}")
    if fallback_block == "minecraft:air":
        errors.append(f"{block}: fallback must not be minecraft:air")
    if fallback_block not in vanilla_blocks:
        errors.append(f"{block}: fallback block id is not in Minecraft 1.21.1 reference: {fallback_block}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("structure_dir", nargs="?", default=str(DEFAULT_STRUCTURE_DIR))
    parser.add_argument("--fallback-map", default=str(DEFAULT_FALLBACK_MAP))
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    parser.add_argument("--profile", default="full", choices=("full",),
                        help="fallback coverage is checked for the shipped full profile")
    args = parser.parse_args()

    structure_dir = (REPO_ROOT / args.structure_dir).resolve() if not Path(args.structure_dir).is_absolute() else Path(args.structure_dir)
    fallback_path = (REPO_ROOT / args.fallback_map).resolve() if not Path(args.fallback_map).is_absolute() else Path(args.fallback_map)
    report_path = (REPO_ROOT / args.report).resolve() if not Path(args.report).is_absolute() else Path(args.report)

    errors: list[str] = []
    if not structure_dir.is_dir():
        errors.append(f"missing_structure_dir: {structure_dir}")
        used_mod_blocks: set[str] = set()
        self_blocks: set[str] = set()
        hero_variants: set[str] = set()
    else:
        used_mod_blocks, self_blocks, hero_variants, palette_errors = shipped_mod_blocks(
            structure_dir, args.profile)
        errors.extend(palette_errors)

    try:
        fallback_map = json.loads(fallback_path.read_text(encoding="utf-8"))
    except Exception as exc:
        fallback_map = {}
        errors.append(f"fallback_map_parse: {exc}")

    if not isinstance(fallback_map, dict):
        fallback_map = {}
        errors.append("fallback_map_not_object")

    # The self-namespace decor (rockery_block hero variants + rockery_cascade,
    # plaques) ships natively, so it must be accepted — fallback-exempt — under
    # BOTH modset profiles (add-hero-rockery task 4.4). Probe the modset directly
    # so the invariant is guarded regardless of which profile's NBTs are scanned.
    self_decor_states = sorted(self_blocks) + [
        f"{SELF_NAMESPACE}:rockery_block[variant={v}]" for v in sorted(hero_variants)]
    for probe_profile in ("full", "vanilla"):
        rejected = load_modset(probe_profile).palette_block_errors(self_decor_states)
        if rejected:
            errors.append(f"self_decor_rejected_under_{probe_profile}: {rejected}")

    vanilla_blocks = load_vanilla_blocks()
    missing = sorted(block for block in used_mod_blocks if block not in fallback_map)
    for block in missing:
        errors.append(f"missing_fallback: {block}")
    for block in sorted(used_mod_blocks):
        if block in fallback_map:
            errors.extend(validate_fallback_value(block, fallback_map[block], vanilla_blocks))

    report = {
        "structure_dir": str(structure_dir.relative_to(REPO_ROOT)),
        "fallback_map": str(fallback_path.relative_to(REPO_ROOT)),
        "profile": args.profile,
        "passed": not errors,
        "used_mod_block_count": len(used_mod_blocks),
        "fallback_entry_count": len(fallback_map),
        "used_mod_blocks": sorted(used_mod_blocks),
        "missing_fallbacks": missing,
        # Self-namespace decor (fallback-exempt): the mod ships these natively
        # under both profiles. hero_variant_count makes the hero 假山 cluster's
        # acceptance explicit (add-hero-rockery task 4.4).
        "self_namespace_blocks": sorted(self_blocks),
        "hero_variant_count": len(hero_variants),
        "errors": errors,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(f"validated mod block fallbacks: {len(used_mod_blocks)} used mod block ids")
    print(f"fallback entries: {len(fallback_map)}")
    print(f"self-namespace decor (fallback-exempt): {len(self_blocks)} block ids, "
          f"{len(hero_variants)} hero_taihu variants")
    print(f"report: {report_path.relative_to(REPO_ROOT)}")
    if errors:
        for error in errors:
            print(f"- {error}")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
