#!/usr/bin/env python3
"""Generate the technique, school and heritage resources from tools/technique_catalogue/.

Usage: python3 tools/gen_technique_catalogue.py [--check] [--root PATH] [--source-dir PATH]

  (no flag)  write every output that differs; a second run changes nothing.
  --check    exit non-zero, listing each output that is missing, differs or is stale.

Outputs (never hand-edit them): data/myvillage/myvillage/{technique,school,heritage}/*.json (the
hand-written techniques named in rules.json are left alone), data/myvillage/world_sim/techniques.json and
heritages.json, and the cultivation.{technique,school,heritage}.myvillage.* keys in en_us and zh_cn.
Edit catalogue.json, schools.json, heritages.json or rules.json instead; see tools/technique_catalogue/.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.technique_catalogue import generator  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--root", type=Path, default=generator.ROOT, help="repository root (default: this checkout)")
    parser.add_argument("--source-dir", type=Path, default=generator.SOURCE_DIR)
    args = parser.parse_args(argv)
    try:
        if args.check:
            stale = generator.check(args.root, args.source_dir)
            for line in stale:
                print(line, file=sys.stderr)
            if stale:
                print(f"{len(stale)} technique catalogue output(s) out of date; run tools/gen_technique_catalogue.py",
                      file=sys.stderr)
                return 1
            print("technique catalogue outputs are current")
            return 0
        changed = generator.write(args.root, args.source_dir)
    except generator.CatalogueError as exc:
        for problem in exc.problems:
            print(problem, file=sys.stderr)
        print(f"technique catalogue: {len(exc.problems)} problem(s) in the sources", file=sys.stderr)
        return 2
    for line in changed:
        print(f"wrote {line}")
    print(f"technique catalogue: {len(changed)} output(s) changed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
