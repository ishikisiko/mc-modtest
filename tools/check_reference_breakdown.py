"""Lightweight validator for Reference Breakdown Contract cards.

This is the fixture required by change `add-visual-reference-structure-pipeline`
(task 2.3). It is intentionally dependency-free (no `jsonschema` package): it
enforces the contract invariants the visual-reference-structure-pipeline spec
cares about, prints a readable report, and exits non-zero on any violation so
it can be wired into a gate later.

The full JSON Schema lives at
`research/source_structures/_schema/reference_breakdown.schema.json` and is the
authoritative shape; this script is the runtime check that the schema's
behavioural invariants hold for a given breakdown card.

Run from the repo root:

    python tools/check_reference_breakdown.py
    python tools/check_reference_breakdown.py research/source_structures/candidate_003/breakdown.json

Without a positional argument it scans every
`research/source_structures/*/breakdown.json` and reports per-card status.

Invariants enforced
-------------------
- Card has the required top-level keys.
- `source_facts.usage_decision` matches the candidate's `import_manifest.json`
  `usage_decision` verbatim (the breakdown SHALL NOT upgrade/downgrade it).
- Every non-empty entry in `direct_component`, `atomic_component`, and
  `generative_grammar` has a non-empty `downstream_route`.
- No `calibration_only` entry carries a prefab/form/planner route (it carries a
  `visual_question` instead).
- `verdict_state` is one of the allowed enum values.

Exit code is 0 iff every inspected card passed.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_TOP_KEYS = (
    "candidate_id",
    "source_facts",
    "observations",
    "direct_component",
    "atomic_component",
    "generative_grammar",
    "calibration_only",
    "verdict_state",
)
REQUIRED_SOURCE_FACT_KEYS = (
    "id",
    "title",
    "source_url",
    "license",
    "usage_decision",
    "attribution_path",
)
ACTIONABLE_BUCKETS = ("direct_component", "atomic_component", "generative_grammar")
ACTIONABLE_REQUIRED_KEYS = ("cue", "rationale", "downstream_route", "review_needed")
CALIBRATION_REQUIRED_KEYS = ("cue", "visual_question", "review_needed")
VERDICT_STATES = ("pending", "accepted", "rejected", "accepted_with_changes")


def _load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _check_card(card_path: Path) -> list[str]:
    errors: list[str] = []
    try:
        card = _load_json(card_path)
    except json.JSONDecodeError as exc:
        return [f"{card_path}: invalid JSON: {exc}"]
    if not isinstance(card, dict):
        return [f"{card_path}: top-level value must be an object"]

    for key in REQUIRED_TOP_KEYS:
        if key not in card:
            errors.append(f"{card_path}: missing required key {key!r}")

    source_facts = card.get("source_facts", {})
    if not isinstance(source_facts, dict):
        errors.append(f"{card_path}: source_facts must be an object")
        source_facts = {}
    for key in REQUIRED_SOURCE_FACT_KEYS:
        if key not in source_facts:
            errors.append(f"{card_path}: source_facts missing key {key!r}")

    # usage_decision must match the candidate manifest verbatim.
    candidate_id = card.get("candidate_id", "")
    if candidate_id:
        manifest_path = ROOT / "research/source_structures" / candidate_id / "import_manifest.json"
        if manifest_path.exists():
            try:
                manifest = _load_json(manifest_path)
                manifest_decision = manifest.get("usage_decision")
                card_decision = source_facts.get("usage_decision")
                if manifest_decision is not None and card_decision != manifest_decision:
                    errors.append(
                        f"{card_path}: source_facts.usage_decision={card_decision!r} "
                        f"does not match manifest usage_decision={manifest_decision!r}"
                    )
            except json.JSONDecodeError as exc:
                errors.append(f"{card_path}: cannot read manifest {manifest_path}: {exc}")
        else:
            errors.append(f"{card_path}: candidate manifest not found at {manifest_path}")

    for bucket in ACTIONABLE_BUCKETS:
        entries = card.get(bucket, [])
        if not isinstance(entries, list):
            errors.append(f"{card_path}: {bucket} must be a list")
            continue
        for i, entry in enumerate(entries):
            if not isinstance(entry, dict):
                errors.append(f"{card_path}: {bucket}[{i}] must be an object")
                continue
            for key in ACTIONABLE_REQUIRED_KEYS:
                if key not in entry:
                    errors.append(f"{card_path}: {bucket}[{i}] missing key {key!r}")
            route = entry.get("downstream_route", "")
            if isinstance(route, str) and not route.strip():
                errors.append(
                    f"{card_path}: {bucket}[{i}] has empty downstream_route "
                    f"(cue={entry.get('cue', '?')!r})"
                )

    calib = card.get("calibration_only", [])
    if not isinstance(calib, list):
        errors.append(f"{card_path}: calibration_only must be a list")
        calib = []
    for i, entry in enumerate(calib):
        if not isinstance(entry, dict):
            errors.append(f"{card_path}: calibration_only[{i}] must be an object")
            continue
        for key in CALIBRATION_REQUIRED_KEYS:
            if key not in entry:
                errors.append(f"{card_path}: calibration_only[{i}] missing key {key!r}")
        if "downstream_route" in entry:
            errors.append(
                f"{card_path}: calibration_only[{i}] SHALL NOT declare a downstream_route "
                f"(cue={entry.get('cue', '?')!r})"
            )

    verdict = card.get("verdict_state")
    if verdict not in VERDICT_STATES:
        errors.append(
            f"{card_path}: verdict_state={verdict!r} not in {VERDICT_STATES}"
        )

    return errors


def _discover_cards(explicit: list[Path]) -> list[Path]:
    if explicit:
        return [p.resolve() for p in explicit]
    root = ROOT / "research/source_structures"
    return sorted(root.glob("*/breakdown.json"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "cards",
        nargs="*",
        help="Optional breakdown.json paths; defaults to every research/source_structures/*/breakdown.json",
    )
    args = parser.parse_args(argv or sys.argv[1:])
    cards = _discover_cards([Path(p) for p in args.cards])

    if not cards:
        print("check_reference_breakdown: no breakdown cards found.")
        print("Expected at least research/source_structures/candidate_003/breakdown.json.")
        return 1

    all_errors: list[str] = []
    for card in cards:
        errors = _check_card(card)
        if errors:
            all_errors.extend(errors)
            print(f"FAIL  {card}")
            for err in errors:
                print(f"      - {err}")
        else:
            print(f"OK    {card}")

    if all_errors:
        print(f"\n{len(all_errors)} error(s) across {len(cards)} card(s).")
        return 1
    print(f"\nAll {len(cards)} card(s) passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
