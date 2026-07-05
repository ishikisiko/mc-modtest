"""Focused checks for the Hui-style reference slice.

Run from the repository root:
    python3 tools/buildgen/tests/test_huipai_reference_slice.py
"""

from __future__ import annotations

import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.compound import (  # noqa: E402
    HUIPAI_TIANJING_SEQUENCE,
    ParcelNode,
    generate_huipai_mansion,
    sample_huipai_mansion_library,
    validate_huipai_mansion,
)
from buildgen.groups import get_group, validate_group_archetype  # noqa: E402
from buildgen.style import load_style  # noqa: E402


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def _expect_value_error(fn, msg: str) -> None:
    try:
        fn()
    except ValueError:
        return
    raise AssertionError(msg)


def test_group_binding() -> None:
    group = get_group("chinese_huipai_mansion")
    _assert(group.style_id == "chinese_huipai_mansion",
            "Hui group does not bind the Hui style profile")
    _assert(group.layout_strategy == "huipai_tianjing_reference_slice",
            "Hui group does not use the Tianjing reference layout strategy")
    _assert("huipai_family_hall" in group.archetype_roster,
            "Hui group roster does not contain the family hall archetype")
    validate_group_archetype(
        "chinese_huipai_mansion", "chinese_huipai_mansion", "huipai_family_hall")
    _expect_value_error(
        lambda: validate_group_archetype(
            "chinese_huipai_mansion", "chinese_huipai_mansion", "main_hall"),
        "Hui group accepted a Jiangnan mansion archetype")


def test_generated_sample_passes_validation() -> None:
    style = load_style("chinese_huipai_mansion")
    compound = generate_huipai_mansion(20260619, style)
    report = validate_huipai_mansion(compound)
    _assert(report["passed"], f"Hui sample failed validation: {report['errors']}")
    _assert(report["stats"]["sequence"] == list(HUIPAI_TIANJING_SEQUENCE),
            "Hui sample does not report the required hall/sky-well sequence")
    _assert(report["stats"]["min_sequence_gap"] >= 3,
            f"Hui hall/sky-well sequence is too cramped: {report['stats']['sequence_gaps']}")
    _assert(report["stats"]["lot_size"] == [47, 76],
            f"Hui wide review lot changed unexpectedly: {report['stats']['lot_size']}")
    _assert(report["stats"]["min_hall_area"] >= 250,
            f"Hui hall mass is too small: {report['stats']['hall_dims']}")
    _assert(report["stats"]["structure_height"] >= 16,
            f"Hui building height is too low: {report['stats']['structure_height']}")
    _assert(report["stats"]["stepped_gable_visual_thickness"] >= 2,
            "Hui stepped gable still reads as a single thin white plate")
    _assert(report["stats"]["stepped_gable_dark_cap"] is True,
            "Hui stepped gable is missing dark coping/cap blocks")
    _assert(report["stats"]["stepped_gable_short_returns"] is True,
            "Hui stepped gable is missing short return walls")
    for dims in report["stats"]["tianjing_dims"].values():
        _assert(max(dims) <= 6, f"Tianjing footprint too large: {dims}")
    _assert(report["stats"]["side_wing_count"] >= 4,
            "Hui sample still reads as unflanked serial halls")
    _assert(report["stats"]["side_wing_pairs"] >= 2,
            "Hui sample does not provide paired side wings")
    _assert(report["stats"]["enclosed_tianjing_count"] == 2,
            "Hui sky-wells are not flanked on both sides")
    _assert(report["stats"]["max_side_wing_width"] <= 8,
            "Hui side wings overfill the lot and crowd the Tianjing slice")
    _assert(report["stats"]["min_side_wing_width"] >= 8,
            "Hui side wings are too slight for the expanded lot")
    _assert(min(report["stats"]["lot_size"]) >= 43,
            f"Hui expanded review lot is too small: {report['stats']['lot_size']}")
    _assert(report["stats"]["footprint_mode"] == "expanded_review_lot",
            "Hui sample does not report the expanded footprint review mode")
    _assert(report["original_generated"] is True,
            "Hui sample did not preserve original-generated provenance")
    _assert(report["copied_source_assets"] is False,
            "Hui sample incorrectly reports copied source assets")


def test_validation_rejects_sequence_and_garden_drift() -> None:
    compound = generate_huipai_mansion(20260619, load_style("chinese_huipai_mansion"))
    compound.meta["tianjing_sequence"] = ["mentang", "xiangtang", "qintang"]
    sequence_report = validate_huipai_mansion(compound)
    _assert(not sequence_report["passed"], "validator accepted missing Tianjing sequence")
    _assert(any("huipai_sequence_missing" in err for err in sequence_report["errors"]),
            f"missing-sequence error not reported: {sequence_report['errors']}")

    garden = generate_huipai_mansion(20260619, load_style("chinese_huipai_mansion"))
    garden.parcel_nodes.append(ParcelNode("garden_pond", "garden_pond", {(2, 2)}))
    garden_report = validate_huipai_mansion(garden)
    _assert(not garden_report["passed"], "validator accepted Jiangnan garden drift")
    _assert(any("huipai_garden_drift" in err for err in garden_report["errors"]),
            f"garden-drift error not reported: {garden_report['errors']}")


def test_sample_library_has_distinct_variants() -> None:
    compounds = sample_huipai_mansion_library(2, 20260619,
                                              load_style("chinese_huipai_mansion"))
    _assert(len(compounds) == 2, "Hui sample library did not generate two samples")
    _assert(len({compound.variant.key() for compound in compounds}) == 2,
            "Hui sample library did not generate distinct narrow/wide variants")
    _assert({compound.lot_size for compound in compounds} == {(47, 76), (43, 72)},
            f"Hui sample lot sizes drifted: {[compound.lot_size for compound in compounds]}")


def main() -> int:
    test_group_binding()
    test_generated_sample_passes_validation()
    test_validation_rejects_sequence_and_garden_drift()
    test_sample_library_has_distinct_variants()
    print("OK Hui-style reference slice")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
