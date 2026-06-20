"""Emit the Python⇄Java parity fixture for ``town_hash``.

Writes ``src/test/resources/town_hash_parity.json`` containing 50
``(seed, tag)`` cases plus range/pick probes. The JUnit test
``TownHashParityTest`` reads this file and asserts the Java mirror produces
bit-identical output. Regenerate after changing ``town_hash``::

    python3 tools/buildgen/tests/generate_town_hash_fixture.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.town_hash import hash64, pick, range64  # noqa: E402

FIXTURE_PATH = (Path(__file__).resolve().parents[4]
                / "src" / "test" / "resources"
                / "town_hash_parity.json")

TAGS = ("cx", "lane_s", "lane_m", "lane_n", "family", "modifier",
        "market", "residential", "fringe", "circle_rx")
FAMILIES = ("square", "circle", "oval", "dshape", "octagon", "trapezoid")
RANGES = (("cx", -4, 4), ("lane_s", -2, 2), ("market", -3, 3),
          ("circle_rx", 40, 70), ("octagon_k", 40, 60))


def build_fixture() -> dict:
    # 50 deterministic (seed, tag) hash cases spanning positive probe seeds,
    # edge values (0, 1, u64 boundary), and the documented geometry tags.
    seeds = [20260618 + i * 101 for i in range(10)] + [0, 1, 42, (1 << 63)]
    hash_cases = []
    for seed in seeds:
        for tag in TAGS:
            hash_cases.append({"seed": seed, "tag": tag, "hash": hash64(seed, tag)})
    # pad to >= 50 by sweeping a few extra seeds
    extra = 0
    s = 1_000_000
    while len(hash_cases) < 50:
        hash_cases.append({"seed": s, "tag": "tag", "hash": hash64(s, "tag")})
        s += 1
        extra += 1

    range_cases = []
    for seed in seeds:
        for tag, lo, hi in RANGES:
            range_cases.append({
                "seed": seed, "tag": tag, "lo": lo, "hi": hi,
                "value": range64(seed, tag, lo, hi),
            })

    pick_cases = []
    for seed in seeds:
        pick_cases.append({
            "seed": seed, "tag": "family", "options": list(FAMILIES),
            "index": FAMILIES.index(pick(seed, "family", FAMILIES)),
        })

    return {
        "generator": "tools/buildgen/tests/generate_town_hash_fixture.py",
        "note": "Bit-identical Python/Java parity vectors for town_hash.",
        "hash_cases": hash_cases,
        "range_cases": range_cases,
        "pick_cases": pick_cases,
    }


def main() -> int:
    fixture = build_fixture()
    FIXTURE_PATH.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE_PATH.write_text(json.dumps(fixture, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {FIXTURE_PATH} "
          f"({len(fixture['hash_cases'])} hash, "
          f"{len(fixture['range_cases'])} range, "
          f"{len(fixture['pick_cases'])} pick)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
