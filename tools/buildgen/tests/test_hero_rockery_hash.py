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
from buildgen.rockery import derive_hero_rockery  # noqa: E402
from buildgen import rockery_models as rm  # noqa: E402

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


def test_summit_vegetation_stays_micro_scaled() -> None:
    field = rm.decode_hero_json(rm.HERO_JSON_PATH)
    vegetation = {p: ch for p, ch in field.cells.items() if ch in {"g", "t", "l"}}
    _assert(vegetation, "hero sculpt has no summit vegetation")
    _assert(all((x // 16, y // 16, z // 16) == (1, 2, 1)
                for (x, y, z) in vegetation),
            "summit vegetation escapes its single micro-model cell")
    wood = [p for p, ch in vegetation.items() if ch == "t"]
    leaves = [p for p, ch in vegetation.items() if ch == "l"]
    _assert(max(x for x, _y, _z in wood) - min(x for x, _y, _z in wood) >= 8,
            "bonsai trunk/branches do not have a readable lateral spread")
    _assert(len({y for _x, y, _z in leaves}) >= 4,
            "bonsai foliage lacks layered canopy pads")

    plan = derive_hero_rockery()
    forbidden = ("oak_", "grass_block", "sapling", "rockery_cascade")
    _assert(all(not any(token in state for token in forbidden)
                for _offset, state in plan.dressing),
            "full-block tree/grass or external cascade remains in dressing")


def test_water_is_one_mountain_fed_micro_path() -> None:
    field = rm.decode_hero_json(rm.HERO_JSON_PATH)
    water = {p for p, ch in field.cells.items() if ch == "w"}
    _assert(water, "hero sculpt has no water")

    remaining = set(water)
    start = remaining.pop()
    reached = {start}
    queue = [start]
    while queue:
        p = queue.pop()
        for axis in range(3):
            for delta in (-1, 1):
                n = list(p)
                n[axis] += delta
                neighbor = tuple(n)
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    reached.add(neighbor)
                    queue.append(neighbor)
    _assert(len(reached) == len(water),
            f"water is split into disconnected components ({len(reached)}/{len(water)})")
    _assert(min(y for _x, y, _z in water) <= 2
            and max(y for _x, y, _z in water) >= 25,
            "water path does not span grotto to foot pool")

    top_y = max(y for _x, y, _z in water)
    rock = {p for p, ch in field.cells.items() if ch in {"s", "m"}}
    top_water = [p for p in water if p[1] == top_y]
    _assert(any(any(tuple(p[i] + (d if i == axis else 0) for i in range(3)) in rock
                    for axis in range(3) for d in (-1, 1))
                for p in top_water),
            "grotto source is not adjacent to the mountain body")

    rm.from_voxel_json()
    water_cells = {(x // 16, y // 16, z // 16) for x, y, z in water}
    _assert(water_cells <= set(rm.HERO_CELL.values()),
            "a source-water cell has no generated hero model")


def test_generated_models_carry_foliage_and_water_materials() -> None:
    model_dir = _RES / "assets/myvillage/models/block/rockery_block"
    summit = json.loads((model_dir / "hero_taihu_b2_c11.json").read_text())
    summit_refs = {next(iter(e["faces"].values()))["texture"]
                   for e in summit["elements"]}
    _assert({"#grass", "#wood", "#leaves"} <= summit_refs,
            f"summit model misses micro vegetation materials: {summit_refs}")
    _assert(summit.get("render_type") == "minecraft:cutout",
            "summit foliage model is not rendered in the cutout pass")

    water_models = []
    for path in sorted(model_dir.glob("hero_taihu_*.json")):
        model = json.loads(path.read_text())
        refs = {next(iter(e["faces"].values()))["texture"]
                for e in model["elements"]}
        if "#water" in refs:
            water_models.append(model)
    _assert(len(water_models) >= 3, "grotto/cascade/pool water masks were not baked")
    _assert(all(m.get("render_type") == "minecraft:translucent"
                for m in water_models),
            "water-bearing hero models are not translucent")


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
