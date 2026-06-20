"""Unit tests for ``tools/buildgen/town_hash.py``.

Run with::

    python3 -m tools.buildgen.tests.test_town_hash
    # or
    python3 tools/buildgen/tests/test_town_hash.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

# Allow running as a script (``python3 tools/buildgen/tests/test_town_hash.py``)
# by inserting the ``tools/`` dir onto ``sys.path`` so ``buildgen.town_hash``
# resolves the same way the validators import it.
_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.town_hash import hash64, pick, range64  # noqa: E402

MASK64 = (1 << 64) - 1


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def test_hash64_deterministic_same_pair() -> None:
    """hash64 is bit-identical for the same (seed, tag) across calls."""
    for seed in (0, 1, 42, 20260618, 2**63, MASK64):
        for tag in ("cx", "lane_s", "family"):
            h1 = hash64(seed, tag)
            h2 = hash64(seed, tag)
            _assert(h1 == h2, f"non-deterministic for ({seed}, {tag}): {h1} != {h2}")


def test_hash64_differs_across_tags() -> None:
    """Different tags with the same seed hash to unrelated outputs."""
    seed = 20260618
    seen = {hash64(seed, t) for t in ("a", "b", "c", "d", "e")}
    _assert(len(seen) == 5, "tags collided unexpectedly")
    # cross-seed/cross-tag pairs should not collide across a broad sweep
    broad = {hash64(s, f"t{s2}") for s in range(50) for s2 in range(3)}
    _assert(len(broad) > 100, f"too many collisions: {len(broad)}")


def test_hash64_in_unsigned_64bit_range() -> None:
    for seed in (-1, 0, 1, 2**40, MASK64):
        h = hash64(seed, "tag")
        _assert(0 <= h <= MASK64, f"out of u64 range: {h}")
        # negative seed masks to MASK64 (unsigned), same as the Java mirror
        _assert(h == hash64(seed & MASK64, "tag"), "negative-seed masking drift")


def test_range64_bounds_inclusive() -> None:
    for seed in range(200):
        for lo, hi in ((-4, 4), (-2, 2), (-3, 3), (0, 0), (5, 5), (0, 5), (-1, 1)):
            v = range64(seed, "tag", lo, hi)
            _assert(lo <= v <= hi, f"{v} not in [{lo},{hi}] for seed={seed}")
            # lo == hi collapses to lo
            if lo == hi:
                _assert(v == lo, "degenerate range did not collapse")


def test_range64_rejects_bad_bounds() -> None:
    try:
        range64(0, "tag", 5, 4)
    except ValueError:
        return
    _assert(False, "range64 should reject lo > hi")


def test_pick_deterministic_and_in_options() -> None:
    opts = ("square", "circle", "oval", "dshape", "octagon", "trapezoid")
    for seed in range(200):
        v = pick(seed, "family", opts)
        _assert(v in opts, f"pick returned {v}")
        _assert(pick(seed, "family", opts) == v, "pick non-deterministic")


def test_pick_rejects_empty() -> None:
    try:
        pick(0, "tag", ())
    except ValueError:
        return
    _assert(False, "pick should reject empty options")


def main() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    failures = 0
    for t in tests:
        try:
            t()
            print(f"PASS {t.__name__}")
        except AssertionError as e:
            failures += 1
            print(f"FAIL {t.__name__}: {e}")
    print(f"{len(tests) - failures}/{len(tests)} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
