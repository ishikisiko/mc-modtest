#!/usr/bin/env python3
"""Generate a region (洲) topology graph for a world seed.

Emits the region graph as JSON (regions with tier/role/position + the typed
edge list with separator types). Constructive and seed-deterministic: the same
seed always yields the identical graph, with no re-roll or global backtracking.

This layer is offline-only — it writes no world blocks, takes over no biomes,
and runs during no chunk generation. See
``openspec/changes/add-region-topology/proposal.md``.

Usage::

    tools/generate_region_topology.py [seed] [--out PATH]
    tools/generate_region_topology.py 12345 --out reports/region_topology_12345.json

If ``seed`` is omitted a fixed default is used. ``--check-determinism`` runs an
internal self-check (regenerate twice, assert byte-identical).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.region_topology import (  # noqa: E402
    DEFAULT_CATALOG_DIR,
    DEFAULT_RULESET_PATH,
    UnsatisfiableRuleset,
    assert_deterministic,
    generate,
    load_catalog_dir,
    load_ruleset,
)

DEFAULT_SEED = 20260620


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "seed",
        nargs="?",
        type=int,
        default=DEFAULT_SEED,
        help="world seed (default: %(default)s)",
    )
    parser.add_argument(
        "--ruleset",
        type=Path,
        default=DEFAULT_RULESET_PATH,
        help="region_topology.json path (default: shipped ruleset)",
    )
    parser.add_argument(
        "--catalog",
        type=Path,
        default=DEFAULT_CATALOG_DIR,
        help="region_profile/ catalog dir (default: shipped catalog)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        help="write JSON to this path (default: stdout)",
    )
    parser.add_argument(
        "--check-determinism",
        action="store_true",
        help="regenerate twice and assert an identical graph before emitting",
    )
    args = parser.parse_args()

    ruleset = load_ruleset(args.ruleset)
    catalog = load_catalog_dir(args.catalog)

    try:
        if args.check_determinism:
            assert_deterministic(args.seed, ruleset, catalog)
        graph = generate(args.seed, ruleset, catalog)
    except UnsatisfiableRuleset as exc:
        print(f"UNSATISFIABLE RULESET: {exc}", file=sys.stderr)
        return 2

    payload = graph.to_dict()
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True)
    if args.out is not None:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text + "\n", encoding="utf-8")
        print(
            f"wrote {args.out} (seed={graph.seed}, count={graph.count}, "
            f"regions={len(graph.regions)}, edges={len(graph.edges)})",
            file=sys.stderr,
        )
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
