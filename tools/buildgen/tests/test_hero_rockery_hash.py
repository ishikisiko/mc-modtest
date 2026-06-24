"""Byte-stability regression for the hero 假山 (add-hero-rockery task 4.3).

The hero specimen is derived from a fixed source sculpt
(``docs/rockery_compressed.json``) with no per-seed noise, so both its
placement and its generated assets must be deterministic and byte-stable. This
test:

  1. regenerates the standalone fragment twice and asserts the two block lists
     are identical (determinism);
  2. compares the canonical fragment hash + the hero-asset rollup hash against
     the committed fixture beside this test module.

Run with::

    python3 -m tools.buildgen.tests.test_hero_rockery_hash
    # or
    python3 tools/buildgen/tests/test_hero_rockery_hash.py
"""

from __future__ import annotations

import glob
import hashlib
import json
import os
import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen import export  # noqa: E402
from buildgen.compound import generate_hero_rockery_fragment  # noqa: E402

_REPO_ROOT = _TOOLS_DIR.parent
_RES = _REPO_ROOT / "src" / "main" / "resources"
_BASELINE = Path(__file__).resolve().parent / "fixtures" / "hero_rockery_baseline_hashes.txt"

# Hero-specific generated assets (task 2.3/2.6). The blockstate file
# rockery_block.json is intentionally excluded — it interleaves the 36 generic
# variants, so it is not hero-stable; the hero models + swatches + cascade
# assets are.
_ASSET_PATTERNS = (
    "assets/myvillage/models/block/rockery_block/hero_taihu_*.json",
    "assets/myvillage/textures/block/rockery_block/swatch_stone.png",
    "assets/myvillage/textures/block/rockery_block/swatch_mossy.png",
    "assets/myvillage/blockstates/rockery_cascade.json",
    "assets/myvillage/models/block/rockery_cascade.json",
)


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def fragment_placement_hash() -> str:
    """Canonical SHA-256 of the standalone fragment block list."""
    grid = generate_hero_rockery_fragment().grid
    data = export.grid_to_structure_data(grid)
    blob = json.dumps(data["blocks"], sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(blob).hexdigest()


def hero_asset_rollup_hash() -> str:
    """Rollup SHA-256 over the hero-specific assets, sorted by rel path."""
    files = []
    for pattern in _ASSET_PATTERNS:
        files.extend(glob.glob(str(_RES / pattern)))
    roll = hashlib.sha256()
    for path in sorted(files):
        rel = os.path.relpath(path, _RES).replace(os.sep, "/")
        with open(path, "rb") as fh:
            data = fh.read()
        roll.update(rel.encode())
        roll.update(b"\0")
        roll.update(hashlib.sha256(data).digest())
    return roll.hexdigest()


def _load_baseline() -> dict:
    out = {}
    with open(_BASELINE, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            key, _, value = line.partition(" ")
            out[key.strip()] = value.strip()
    return out


def test_fragment_placement_is_deterministic() -> None:
    _assert(fragment_placement_hash() == fragment_placement_hash(),
            "fragment placement is non-deterministic across two runs")


def test_fragment_placement_matches_baseline() -> None:
    baseline = _load_baseline()
    got = fragment_placement_hash()
    _assert(got == baseline["fragment_placement"],
            f"fragment placement drift: {got} != {baseline['fragment_placement']} "
            "(regenerate with generate_hero_rockery_fixture.py if intended)")


def test_hero_assets_match_baseline() -> None:
    baseline = _load_baseline()
    got = hero_asset_rollup_hash()
    _assert(got == baseline["hero_assets"],
            f"hero asset drift: {got} != {baseline['hero_assets']} "
            "(regenerate with generate_hero_rockery_fixture.py if intended)")


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
