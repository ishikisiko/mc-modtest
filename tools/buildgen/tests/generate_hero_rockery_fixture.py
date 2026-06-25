"""Regenerate the hero 假山 byte-stability baseline (add-hero-rockery task 4.3).

Writes the committed fixture beside this test module from the current generator
output. Run this only after an *intended* change to the hero placement or assets;
``test_hero_rockery_hash.py`` guards against unintended drift.

    python3 tools/buildgen/tests/generate_hero_rockery_fixture.py
"""

from __future__ import annotations

import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.tests.test_hero_rockery_hash import (  # noqa: E402
    _BASELINE, fragment_placement_hash, hero_asset_rollup_hash)

_HEADER = """\
# Byte-stability baseline for add-hero-rockery (task 4.3)
#
# The hero 假山 is derived deterministically from a fixed source sculpt
# (docs/rockery_compressed.json) with no per-seed noise, so its placement and
# generated assets must be byte-stable. Regenerate with:
#
#   python3 tools/generate_compound_library.py --group chinese_mansion \\
#       --count 6 --base-seed 20260618 --profile full
#   python3 tools/buildgen/rockery_models.py    # regenerates hero assets
#
# Then refresh these hashes with:
#
#   python3 tools/buildgen/tests/generate_hero_rockery_fixture.py
#
# Guarded by tools/buildgen/tests/test_hero_rockery_hash.py.

# Canonical SHA-256 of the standalone hero_rockery fragment block list
# (export.grid_to_structure_data(...)['blocks'], JSON-canonicalized).
fragment_placement  {fragment}

# Rollup SHA-256 over the hero-specific generated assets (hero_taihu_*.json
# models + swatch_stone/swatch_mossy PNGs + legacy cascade blockstate/model),
# sorted by repo-relative path, each contributing path\\0sha256(bytes).
hero_assets         {assets}
"""


def main() -> int:
    content = _HEADER.format(
        fragment=fragment_placement_hash(),
        assets=hero_asset_rollup_hash(),
    )
    _BASELINE.parent.mkdir(parents=True, exist_ok=True)
    _BASELINE.write_text(content, encoding="utf-8")
    print(f"wrote {_BASELINE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
