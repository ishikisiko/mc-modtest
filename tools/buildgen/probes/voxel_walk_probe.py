"""Read-only voxel-walkability diagnostic for a single compound.

NOT shipped — a debugging aid for the courtyard-voxel-walkability validator.
Prints the gate-entry STANDABLE column, the 3D BFS visited set size, the set
of unreachable endpoints, and per-error-code breakdown so a "堵住" defect can
be triaged without re-running the full validator.

Run from the repo root:

    python tools/buildgen/probes/voxel_walk_probe.py chinese_courtyard 20260614
    python tools/buildgen/probes/voxel_walk_probe.py small_courtyard 20260617
    python tools/buildgen/probes/voxel_walk_probe.py sect 20260616

The first positional arg is the family (chinese_courtyard / small_courtyard /
sect); the second is the seed. Output is plain text on stdout — no files
written, no grid mutations.
"""

from __future__ import annotations

import argparse
import os
import sys
from collections import Counter
from typing import Set, Tuple

# Allow running as a script from the repo root without installing the package.
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))))
sys.path.insert(0, _REPO_ROOT)

from tools.buildgen import compound as C  # noqa: E402


def _build_compound(family: str, seed: int):
    if family == "chinese_courtyard":
        return C.generate_compound(seed)
    if family in ("small_courtyard", "cultivation_town"):
        return C.generate_small_courtyard(seed)
    if family == "sect":
        from tools.buildgen.compound import sample_sect_compound_library
        compounds = sample_sect_compound_library(count=1, base_seed=seed)
        return compounds[0]
    raise ValueError(f"unknown family {family!r} "
                     f"(try chinese_courtyard / small_courtyard / sect)")


def _probe(compound) -> None:
    lot_w, lot_d = compound.lot_size
    start = C._gate_entry_standable(compound)
    print(f"compound: style={compound.style_id} seed={compound.seed} "
          f"lot={lot_w}x{lot_d} axis_x={compound.axis_x}")
    print(f"gate_side: {compound.meta.get('gate_side', 'south')}")
    if start is None:
        print("gate-entry: NO standable cell at (axis_x, z=1) — entry blocked")
        visited: Set[Tuple[int, int, int]] = set()
    else:
        print(f"gate-entry STANDABLE: {start}")
        visited = C._voxel_walk_bfs(compound, start, lot_w, lot_d)
    print(f"BFS visited cells: {len(visited)}")
    visited_cols = {(x, z) for (x, _, z) in visited}
    print(f"BFS visited columns (x,z): {len(visited_cols)}")

    # Per-column standable-y coverage, to spot interior cells the BFS never
    # reached even though a standable y exists there.
    unreached_standable = []
    for x in range(1, lot_w - 1):
        for z in range(1, lot_d - 1):
            ys = C._standable_ys(compound, x, z)
            if not ys:
                continue
            if (x, z) in visited_cols:
                continue
            unreached_standable.append(((x, z), ys))
    print(f"unreached STANDABLE columns: {len(unreached_standable)}")
    for cell, ys in unreached_standable[:20]:
        print(f"   {cell}: standable_ys={ys}")
    if len(unreached_standable) > 20:
        print(f"   ... ({len(unreached_standable) - 20} more)")

    # Run the full validator and categorise errors.
    if compound.meta.get("layout_strategy") == "small_courtyard_unit":
        report = C.validate_small_courtyard(compound)
    elif compound.style_id == "cultivation_sect":
        report = C.validate_sect_compound(compound)
    else:
        report = C.validate_compound(compound)
    print(f"\nvalidator passed: {report['passed']}")
    print(f"voxel_reachability stat: {report['stats'].get('voxel_reachability')}")
    cats = Counter(e.split(":", 1)[0] for e in report["errors"])
    if cats:
        print("error breakdown:")
        for cat, n in sorted(cats.items()):
            print(f"   {cat}: {n}")
        print("first 15 errors:")
        for e in report["errors"][:15]:
            print(f"   {e}")
    else:
        print("no errors.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("family",
                        choices=("chinese_courtyard", "small_courtyard",
                                 "cultivation_town", "sect"))
    parser.add_argument("seed", type=int)
    args = parser.parse_args()
    compound = _build_compound(args.family, args.seed)
    _probe(compound)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
