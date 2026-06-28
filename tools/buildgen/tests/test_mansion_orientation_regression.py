"""Orientation byte-stability regression (rebuild-mansion-enclosure-plan task 6.2).

The orientation mechanism (task 1) added a ``wall`` argument to ``_door`` and a
``facing`` form-override threaded through the build chain. The default
(``facing=south``) MUST reproduce the pre-change output byte-for-byte, proving
the parameter is purely additive.

This test regenerates each mansion archetype with the default facing and
compares the canonical grid hash against the committed baseline fixture (which
was captured at the pre-change revision ``d948124^`` and verified byte-identical
to the current tree). It also asserts that passing ``facing="south"``
explicitly is identical to the default — the additive guarantee from both sides.

Run with::

    python3 -m tools.buildgen.tests.test_mansion_orientation_regression
    # or
    python3 tools/buildgen/tests/test_mansion_orientation_regression.py
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen import export  # noqa: E402
from buildgen.compound import generate_subbuilding  # noqa: E402
from buildgen.style import load_style  # noqa: E402

_BASELINE = (Path(__file__).resolve().parent / "fixtures"
             / "mansion_orientation_baseline_hashes.txt")

# Canonical config — must match the fixture header. group_id=None isolates the
# door mechanism at the archetype level (independent of the mansion roster).
ARCHETYPES = ("gate_house", "front_row", "open_hall", "side_wing", "tower_house")
SEED = 20260618
ROOF = "chinese_round_ridge"


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def _grid_hash(ctx) -> str:
    data = export.grid_to_structure_data(ctx.grid)
    blob = json.dumps(data["blocks"], sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(blob).hexdigest()


def _baseline() -> dict[str, str]:
    out: dict[str, str] = {}
    for line in _BASELINE.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, digest = line.split()
        out[name] = digest
    return out


def main() -> int:
    style = load_style("chinese_mansion")
    baseline = _baseline()
    _assert(set(baseline) == set(ARCHETYPES),
            f"fixture archetype set drift: {sorted(baseline)} vs {sorted(ARCHETYPES)}")

    drift = []
    for arch in ARCHETYPES:
        # Default facing == pre-change baseline (the additive guarantee).
        default_ctx = generate_subbuilding(style, arch, SEED, ROOF, None)
        default_hash = _grid_hash(default_ctx)
        if default_hash != baseline[arch]:
            drift.append(f"{arch}: {baseline[arch]} -> {default_hash}")
        # Explicit facing="south" == default (additive from both sides).
        south_ctx = generate_subbuilding(style, arch, SEED, ROOF, None,
                                         form_overrides={"facing": "south"})
        _assert(_grid_hash(south_ctx) == default_hash,
                f"{arch}: explicit facing=south differs from the default path")

    if drift:
        raise AssertionError(
            "default-south output drifted from the pre-change baseline "
            "(the `wall`/`facing` param is no longer additive):\n  "
            + "\n  ".join(drift))
    print(f"OK orientation byte stability: {len(ARCHETYPES)} mansion archetypes "
          "byte-identical to the pre-change baseline (default == facing=south)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
