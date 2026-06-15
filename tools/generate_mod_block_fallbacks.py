#!/usr/bin/env python3
"""Generate runtime fallbacks for optional mod block ids.

Precedence is deterministic: styles are scanned in settlement-group order from
`tools/buildgen/groups.py`, with unreferenced style files appended by filename;
slots are scanned in JSON/material-slot order. If the same mod block id appears
with multiple trailing minecraft fallbacks, the earliest style/slot wins and a
generation-time note is emitted.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_OUTPUT = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "mod_block_fallbacks.json"
DEFAULT_LAST_RESORT = "minecraft:cobblestone"

if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.groups import GROUPS  # noqa: E402
from buildgen.modset import load_modset  # noqa: E402
from buildgen.style import STYLE_DIR, VANILLA_NAMESPACE, _block_id, _namespace, load_style  # noqa: E402


@dataclass(frozen=True)
class FallbackChoice:
    fallback: str
    style_id: str
    slot: str


def style_order() -> list[str]:
    ordered: list[str] = []
    seen: set[str] = set()
    for group in GROUPS.values():
        if group.style_id not in seen:
            ordered.append(group.style_id)
            seen.add(group.style_id)
    for path in sorted(Path(STYLE_DIR).glob("*.json")):
        style_id = path.stem
        if style_id not in seen:
            ordered.append(style_id)
            seen.add(style_id)
    return ordered


def trailing_minecraft_fallback(entries: list[str]) -> str | None:
    if not entries:
        return None
    fallback = entries[-1]
    if _namespace(fallback) != VANILLA_NAMESPACE:
        raise ValueError(f"slot entries must end with a minecraft fallback, got {fallback!r}")
    return fallback


def build_fallback_map(profile: str = "full") -> tuple[OrderedDict[str, str], list[str]]:
    modset = load_modset(profile)
    choices: dict[str, FallbackChoice] = {}
    notes: list[str] = []

    for style_id in style_order():
        style = load_style(style_id, available_namespaces=modset.namespaces)
        for slot, entries in style.material_slots.items():
            fallback = trailing_minecraft_fallback(entries)
            if fallback is None:
                continue
            for state in entries:
                block = _block_id(state)
                namespace = _namespace(block)
                if namespace == VANILLA_NAMESPACE:
                    continue
                if block not in modset.mod_block_ids:
                    continue
                previous = choices.get(block)
                if previous is None:
                    choices[block] = FallbackChoice(fallback, style_id, slot)
                elif previous.fallback != fallback:
                    notes.append(
                        "collision: "
                        f"{block} uses {previous.fallback} from {previous.style_id}.{previous.slot}; "
                        f"ignored {fallback} from {style_id}.{slot}"
                    )

    return OrderedDict((block, choices[block].fallback) for block in sorted(choices)), notes


def encoded_json(mapping: OrderedDict[str, str]) -> str:
    return json.dumps(mapping, indent=2, ensure_ascii=False) + "\n"


def write_output(output: Path, mapping: OrderedDict[str, str], check: bool) -> bool:
    payload = encoded_json(mapping)
    if check:
        if not output.is_file():
            print(f"missing fallback map: {output.relative_to(REPO_ROOT)}", file=sys.stderr)
            return False
        existing = output.read_text(encoding="utf-8")
        if existing != payload:
            print(f"fallback map is not up to date: {output.relative_to(REPO_ROOT)}", file=sys.stderr)
            return False
        return True

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(payload, encoding="utf-8")
    return True


def print_notes(notes: Iterable[str]) -> None:
    for note in notes:
        print(f"NOTE {note}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT),
                        help="generated runtime fallback JSON path")
    parser.add_argument("--profile", default="full", choices=("full",),
                        help="profile to export; runtime map is generated from the shipped full profile")
    parser.add_argument("--check", action="store_true",
                        help="verify the output is byte-identical instead of writing it")
    args = parser.parse_args()

    output = (REPO_ROOT / args.output).resolve() if not Path(args.output).is_absolute() else Path(args.output)
    mapping, notes = build_fallback_map(args.profile)
    if not mapping:
        print("no mod block fallbacks were generated", file=sys.stderr)
        return 1
    print_notes(notes)
    if not write_output(output, mapping, args.check):
        return 1
    action = "validated" if args.check else "generated"
    print(f"{action} mod block fallback map: {output.relative_to(REPO_ROOT)}")
    print(f"fallback entries: {len(mapping)}")
    print(f"default last resort: {DEFAULT_LAST_RESORT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
