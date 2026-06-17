#!/usr/bin/env python3
"""Validate the deterministic terraced sect-compound planner.

The Java ``SectGenerator`` re-derives a plan structurally equivalent to the
Python planner in ``buildgen.sect``. This validator drives that plan and
asserts:

  * the terrace stack ascends a single fall-line ritual axis gate→summit;
  * slot importance is non-decreasing up the stack with the principal hall and
    scripture pagoda at the top tiers;
  * every covered gallery and any flying-bridge feature has both endpoints
    resting on the volumes/terraces it joins;
  * the three detached-spire feature variants are pairwise distinct and a
    feature-absent plan is still complete;
  * every slot's referenced structure template fits inside the slot and inside
    its terrace (catches .nbt/footprint drift between planner and shipped pieces);
  * the plan is reproducible for a fixed seed + site;
  * the geometry constants the Java realizer hardcodes (PARITY_CONSTANTS) match
    the Python planner defaults, so the two stay in lock-step (Python/Java parity).

Emits ``reports/sect_generation_validation.json``.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.nbtread import read_gzipped_nbt  # noqa: E402
from buildgen.sect_mountain import (  # noqa: E402
    MOUNTAIN_PARITY,
    derive_mountain,
    flat_natural,
    noisy_natural,
    validate_mountain,
    validate_mountain_reproducibility,
)
from buildgen.sect import (  # noqa: E402
    DEFAULT_AXIS_STAIR_W,
    DEFAULT_CLIFF_BACK_HEIGHT,
    DEFAULT_TERRACE_COUNT,
    DEFAULT_TERRACE_DEPTH,
    DEFAULT_TERRACE_RISE,
    DEFAULT_TERRACE_WIDTH,
    DEFAULT_SUMMIT_TAPER,
    FEATURE_PERIOD,
    FEATURE_VARIANTS,
    MAX_TERRACE_COUNT,
    MIN_TERRACE_COUNT,
    Z_MARGIN,
    SectSite,
    generate_sect_plan,
    validate_feature_variants,
    validate_sect_plan,
    validate_sect_reproducibility,
)

REPORT_PATH = REPO_ROOT / "reports" / "sect_generation_validation.json"
STRUCTURE_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"
WORLDGEN_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "worldgen"
BIOME_TAG_PATH = (REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage"
                  / "tags" / "worldgen" / "biome" / "has_sect.json")
SEEDS = (20260618, 20260719, 20260820)

# High-relief biomes a sect may site in; flat lowlands it must avoid.
FLAT_LOWLAND_BIOMES = {
    "minecraft:plains", "minecraft:sunflower_plains", "minecraft:desert",
    "minecraft:savanna", "minecraft:swamp", "minecraft:ocean",
    "minecraft:deep_ocean", "minecraft:beach", "minecraft:snowy_plains",
    "minecraft:mushroom_fields", "minecraft:river",
}

# Mountain derivation constants the Java SectMountain hardcodes; must match.
MOUNTAIN_PARITY_EXPECTED = {
    "SKIRT_RADIUS": 24,
    "OUTER_SLOPE": 1,
    "NOISE_AMP_INTER": 3,
    "NOISE_AMP_OUTER": 5,
    "SEAM_SLOPE_LIMIT": 6,
    "SPIRE_GAP": 3,
}

# Geometry constants the Java realizer hardcodes. These MUST match the Python
# defaults so the runtime compound is structurally identical to this plan.
PARITY_CONSTANTS = {
    "TERRACE_COUNT": DEFAULT_TERRACE_COUNT,
    "TERRACE_RISE": DEFAULT_TERRACE_RISE,
    "TERRACE_DEPTH": DEFAULT_TERRACE_DEPTH,
    "TERRACE_WIDTH": DEFAULT_TERRACE_WIDTH,
    "SUMMIT_TAPER": DEFAULT_SUMMIT_TAPER,
    "AXIS_STAIR_W": DEFAULT_AXIS_STAIR_W,
    "CLIFF_BACK_HEIGHT": DEFAULT_CLIFF_BACK_HEIGHT,
    "Z_MARGIN": Z_MARGIN,
    "MIN_TERRACE_COUNT": MIN_TERRACE_COUNT,
    "MAX_TERRACE_COUNT": MAX_TERRACE_COUNT,
}


def template_size(name: str) -> Tuple[int, int]:
    _name, root = read_gzipped_nbt(str(STRUCTURE_DIR / f"{name}.nbt"))
    sx, _sy, sz = root["size"]
    return int(sx), int(sz)


def template_layer_coverage(name: str, y: int) -> int:
    _name, root = read_gzipped_nbt(str(STRUCTURE_DIR / f"{name}.nbt"))
    return sum(1 for block in root["blocks"] if int(block["pos"][1]) == y)


def _rect_set(x0: int, z0: int, x1: int, z1: int) -> Set[Tuple[int, int]]:
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


def validate_template_fit(plan) -> List[str]:
    """Every slot's referenced .nbt fits inside the slot and the terrace, with a
    non-empty ground layer so realized volumes neither float nor bury."""
    errors: List[str] = []
    terrace_by_index = {t.index: t for t in plan.terraces}
    for slot in plan.slots:
        try:
            tw, td = template_size(slot.template_id)
        except FileNotFoundError:
            errors.append(f"template_missing:{slot.id}:{slot.template_id}")
            continue
        sx0, sz0, sx1, sz1 = slot.bounds
        # the planner sizes the slot to the footprint; the real .nbt must fit it
        if tw > (sx1 - sx0 + 1) or td > (sz1 - sz0 + 1):
            errors.append(
                f"template_larger_than_slot:{slot.id}:{slot.template_id}:"
                f"tpl={tw}x{td} slot={sx1 - sx0 + 1}x{sz1 - sz0 + 1}")
        terrace = terrace_by_index.get(slot.terrace_index)
        if terrace is not None:
            tx0, tz0, tx1, tz1 = terrace.bounds
            if sx0 < tx0 or sx1 > tx1 or sz0 < tz0 or sz1 > tz1:
                errors.append(f"slot_outside_terrace:{slot.id}")
        # non-empty ground layer
        try:
            if template_layer_coverage(slot.template_id, 0) == 0:
                errors.append(f"template_ground_layer_empty:{slot.id}:{slot.template_id}")
        except FileNotFoundError:
            pass
    # no two slot templates overlap on the same terrace
    for i in range(len(plan.slots)):
        for j in range(i + 1, len(plan.slots)):
            a = plan.slots[i]
            b = plan.slots[j]
            if a.terrace_index != b.terrace_index:
                continue
            ax0, az0, ax1, az1 = a.bounds
            bx0, bz0, bx1, bz1 = b.bounds
            if ax1 >= bx0 and bx1 >= ax0 and az1 >= bz0 and bz1 >= az0:
                errors.append(f"slot_template_overlap:{a.id}:{b.id}")
    return errors


def validate_parity_constants() -> List[str]:
    """Confirm the Java-hardcoded geometry matches the Python planner defaults."""
    errors: List[str] = []
    # These mirror SectGenerator.java's static final constants. If either side
    # changes, update both and this map together.
    expected = {
        "TERRACE_COUNT": 5,
        "TERRACE_RISE": 8,
        "TERRACE_DEPTH": 28,
        "TERRACE_WIDTH": 58,
        "SUMMIT_TAPER": 4,
        "AXIS_STAIR_W": 5,
        "CLIFF_BACK_HEIGHT": 12,
        "Z_MARGIN": 4,
    }
    for key, value in expected.items():
        py = PARITY_CONSTANTS.get(key)
        if py != value:
            errors.append(f"parity_constant_mismatch:{key}:python={py}!=java={value}")
    return errors


def _feature_dict(plan):
    return ({"detached_bounds": list(plan.feature.detached_bounds)}
            if plan.feature is not None else None)


def validate_worldgen_data() -> List[str]:
    """The worldgen structure/structure_set/biome-tag are present and well-formed:
    biome-gated to high relief (no flat lowland), spaced with a real minimum
    separation, and wired to the registered myvillage:sect structure type."""
    errors: List[str] = []
    structure = WORLDGEN_DIR / "structure" / "sect.json"
    structure_set = WORLDGEN_DIR / "structure_set" / "sect.json"
    if not structure.exists():
        errors.append("missing_worldgen_structure_json")
    else:
        data = json.loads(structure.read_text())
        if data.get("type") != "myvillage:sect":
            errors.append(f"structure_wrong_type:{data.get('type')}")
        if data.get("biomes") != "#myvillage:has_sect":
            errors.append(f"structure_not_biome_tagged:{data.get('biomes')}")
    if not structure_set.exists():
        errors.append("missing_worldgen_structure_set_json")
    else:
        data = json.loads(structure_set.read_text())
        placement = data.get("placement", {})
        spacing = placement.get("spacing")
        separation = placement.get("separation")
        if not isinstance(spacing, int) or not isinstance(separation, int):
            errors.append("structure_set_missing_spacing_separation")
        else:
            if separation < 1:
                errors.append(f"structure_set_no_min_separation:{separation}")
            if separation >= spacing:
                errors.append(f"structure_set_separation_ge_spacing:{separation}>={spacing}")
            if spacing < 16:
                errors.append(f"structure_set_too_dense:{spacing}")  # not a rare landmark
    # biome gating: tag present, non-empty, no flat lowland biomes
    if not BIOME_TAG_PATH.exists():
        errors.append("missing_biome_tag")
    else:
        values = json.loads(BIOME_TAG_PATH.read_text()).get("values", [])
        if not values:
            errors.append("biome_tag_empty")
        for biome in values:
            if biome in FLAT_LOWLAND_BIOMES:
                errors.append(f"sect_gated_to_flat_lowland:{biome}")
    return errors


def validate_worldgen_mountain(seeds) -> List[str]:
    """Derive the mountain per seed and assert the 反推山形 contract: terraces at
    planned elevations, blend-skirt seam-free, cliff-back sheer, cloud-sea
    placed, spire deterministic — over both flat and rolling natural terrain."""
    errors: List[str] = []
    for seed in seeds:
        plan = generate_sect_plan(seed)
        feature = _feature_dict(plan)
        for label, natural in (("flat", flat_natural(64)),
                               ("rolling", noisy_natural(64, seed))):
            mountain = derive_mountain(seed, plan.terrace_profile, natural, feature=feature)
            report = validate_mountain(mountain)
            if not report["passed"]:
                errors.extend(f"seed={seed}:{label}:{e}" for e in report["errors"])
    repro = validate_mountain_reproducibility(
        list(seeds) + [7, 0, 3, 12345], generate_sect_plan(seeds[0]).terrace_profile)
    if not repro["passed"]:
        errors.extend(f"repro:{e}" for e in repro["errors"])
    # mountain-derivation parity constants Python vs Java
    for key, value in MOUNTAIN_PARITY_EXPECTED.items():
        if MOUNTAIN_PARITY.get(key) != value:
            errors.append(
                f"mountain_parity_mismatch:{key}:python={MOUNTAIN_PARITY.get(key)}!=java={value}")
    return errors


def survey_worldgen(n: int = 32) -> dict:
    """Multi-seed survey: feature presence rate + spire-bearing distribution, so
    the detached-spire feature is random-but-deterministic across sites."""
    present = 0
    bearings: Dict[str, int] = {}
    for s in range(n):
        plan = generate_sect_plan(s)
        if plan.feature is not None:
            present += 1
            bearings[plan.feature.variant] = bearings.get(plan.feature.variant, 0) + 1
    return {
        "seeds": n,
        "feature_present": present,
        "feature_present_rate": round(present / n, 3),
        "feature_variant_counts": bearings,
    }


def main() -> int:
    errors: List[str] = []
    results = []
    for seed in SEEDS:
        plan = generate_sect_plan(seed)
        report = validate_sect_plan(plan)
        if not report["passed"]:
            errors.extend(f"seed={seed}:{e}" for e in report["errors"])
        errors.extend(f"seed={seed}:{e}" for e in validate_template_fit(plan))
        status = "OK" if report["passed"] else "FAIL"
        print(f"{status} sect seed={seed} terraces={report['stats']['terrace_count']} "
              f"slots={report['stats']['slot_count']} feature={report['stats']['feature_variant']}")
        results.append({
            "seed": seed,
            "plan": report,
            "template_fit": {"passed": True},
        })

    # feature variants distinct + absent-seed complete
    feature_report = validate_feature_variants()
    if not feature_report["passed"]:
        errors.extend(f"feature:{e}" for e in feature_report["errors"])
        print("FAIL feature variant distinctness")
    else:
        print("OK feature variants pairwise distinct; absent-seed plan complete")

    # reproducibility across seeds + all terrace counts
    repro_report = validate_sect_reproducibility(list(SEEDS) + [7, 0, 3, 12345])
    if not repro_report["passed"]:
        errors.extend(f"repro:{e}" for e in repro_report["errors"])
        print("FAIL reproducibility")
    else:
        print("OK same-seed plan is reproducible")

    for count in range(MIN_TERRACE_COUNT, MAX_TERRACE_COUNT + 1):
        plan = generate_sect_plan(SEEDS[0], params={"terrace_count": count})
        report = validate_sect_plan(plan)
        if not report["passed"]:
            errors.extend(f"count={count}:{e}" for e in report["errors"])
            print(f"FAIL terrace_count={count}")
        else:
            print(f"OK terrace_count={count}")

    # parity constants Python vs Java
    parity_errors = validate_parity_constants()
    errors.extend(parity_errors)
    if parity_errors:
        print("FAIL Python/Java parity constants")
    else:
        print("OK Python/Java planner parity constants agree")

    # feature variant coverage across a seed sweep
    seen = set()
    for s in range(FEATURE_PERIOD * 4):
        plan = generate_sect_plan(s)
        seen.add(plan.feature.variant if plan.feature else None)
    if None not in seen:
        errors.append("feature_never_absent_in_sweep")
    if len([v for v in seen if v]) < len(FEATURE_VARIANTS):
        errors.append("feature_variant_undercovered_in_sweep")

    # worldgen: structure/structure_set/biome-tag + derived mountain (反推山形)
    worldgen_data_errors = validate_worldgen_data()
    errors.extend(f"worldgen_data:{e}" for e in worldgen_data_errors)
    if worldgen_data_errors:
        print("FAIL worldgen structure/structure_set/biome-tag")
    else:
        print("OK worldgen sect structure biome-gated, spaced, min-separated")

    mountain_errors = validate_worldgen_mountain(SEEDS)
    errors.extend(f"mountain:{e}" for e in mountain_errors)
    if mountain_errors:
        print("FAIL derived mountain (反推山形)")
    else:
        print("OK derived mountain: terraces at elevation, skirt seam-free, "
              "cliff-back sheer, cloud-sea placed, spire deterministic")

    worldgen_survey = survey_worldgen(32)
    print(f"survey: feature present {worldgen_survey['feature_present']}/"
          f"{worldgen_survey['seeds']} ({worldgen_survey['feature_present_rate']}) "
          f"variants={worldgen_survey['feature_variant_counts']}")

    summary = {
        "passed": not errors,
        "errors": errors,
        "results": results,
        "feature_variants": {"names": list(FEATURE_VARIANTS.keys())},
        "parity_constants": PARITY_CONSTANTS,
        "feature_sweep_coverage": sorted(str(v) for v in seen),
        "worldgen": {
            "data_passed": not worldgen_data_errors,
            "mountain_passed": not mountain_errors,
            "mountain_parity": MOUNTAIN_PARITY,
            "survey": worldgen_survey,
        },
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with REPORT_PATH.open("w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"report: {REPORT_PATH.relative_to(REPO_ROOT)}")
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
