"""Focused checks for the Ganlan stilted-house reference slice.

Run from the repository root:
    python3 tools/buildgen/tests/test_ganlan_stilted_house.py
"""

from __future__ import annotations

import sys
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.compound import (  # noqa: E402
    generate_ganlan_stilted_house,
    sample_ganlan_stilted_house_library,
    validate_ganlan_stilted_house,
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
    group = get_group("ganlan_stilted_house")
    _assert(group.style_id == "ganlan_stilted_house",
            "Ganlan group does not bind the Ganlan style profile")
    _assert(group.layout_strategy == "ganlan_stilted_reference_slice",
            "Ganlan group does not use the stilted-house layout strategy")
    _assert("ganlan_small_house" in group.archetype_roster,
            "Ganlan group roster does not contain the small house archetype")
    validate_group_archetype(
        "ganlan_stilted_house", "ganlan_stilted_house", "ganlan_small_house")
    _expect_value_error(
        lambda: validate_group_archetype(
            "ganlan_stilted_house", "ganlan_stilted_house", "small_house"),
        "Ganlan group accepted a default medieval house archetype")


def test_generated_sample_passes_validation() -> None:
    style = load_style("ganlan_stilted_house")
    compound = generate_ganlan_stilted_house(20260708, style)
    report = validate_ganlan_stilted_house(compound)
    _assert(report["passed"], f"Ganlan sample failed validation: {report['errors']}")
    _assert(report["stats"]["height_above_support"] >= 2,
            "Ganlan sample floor is not raised enough")
    _assert(report["stats"]["support_post_count"] >= 10,
            "Ganlan sample lacks a structural post rhythm")
    _assert(report["stats"]["underfloor_beam_blocks"] >= 30,
            "Ganlan sample lacks underfloor tie beams")
    _assert(report["stats"]["underside_open_ratio"] >= 0.65,
            f"Ganlan underside is too filled: {report['stats']['underside_open_ratio']}")
    _assert(report["stats"]["veranda_cells"] >= 20,
            "Ganlan veranda cue is too small")
    _assert(report["stats"]["entry_transition_length"] >= 3,
            "Ganlan stair does not produce a veranda transition")
    _assert(report["stats"]["veranda_canopy_projection"] >= 3,
            "Ganlan veranda is not sheltered by a projecting canopy")
    _assert(report["stats"]["veranda_canopy_support_count"] >= 4,
            "Ganlan veranda canopy lacks structural supports")
    _assert(report["stats"]["roof_overhang"] >= 2,
            "Ganlan roof overhang is not deep enough")
    _assert(report["stats"]["frame_post_count"] >= 8,
            "Ganlan wall frame is not legible as a bay system")
    _assert(report["stats"]["tie_beam_blocks"] >= 40,
            "Ganlan wall frame lacks horizontal tie beams")
    _assert(report["stats"]["water_cells_under_floor"] >= 8,
            "Ganlan wet context does not pass under the raised floor")
    _assert(report["stats"]["ganlan_typology"] == "humid_fully_elevated",
            "Ganlan sample does not name its narrow typology")
    _assert(report["original_generated"] is True,
            "Ganlan sample did not preserve original-generated provenance")
    _assert(report["copied_source_assets"] is False,
            "Ganlan sample incorrectly reports copied source assets")


def test_validation_rejects_bad_support_and_provenance() -> None:
    style = load_style("ganlan_stilted_house")
    compound = generate_ganlan_stilted_house(20260708, style)
    compound.meta["reference_candidate"] = "candidate_003"
    bad_ref = validate_ganlan_stilted_house(compound)
    _assert(not bad_ref["passed"], "validator accepted wrong reference candidate")
    _assert(any("ganlan_reference_candidate_missing" in err for err in bad_ref["errors"]),
            f"wrong-reference error not reported: {bad_ref['errors']}")

    unsupported = generate_ganlan_stilted_house(20260708, style)
    posts = next(node for node in unsupported.parcel_nodes if node.id == "stilt_support_grid")
    x, z = next(iter(posts.cells))
    unsupported.grid.carve_air((x, unsupported.meta["support_plane_y"], z))
    post_report = validate_ganlan_stilted_house(unsupported)
    _assert(not post_report["passed"], "validator accepted an unsupported stilt post")
    _assert(any("ganlan_support_posts_not_connected" in err for err in post_report["errors"]),
            f"unsupported-post error not reported: {post_report['errors']}")

    uncovered = generate_ganlan_stilted_house(20260708, style)
    canopy = next(node for node in uncovered.parcel_nodes
                   if node.id == "veranda_rain_canopy")
    canopy.meta["projection"] = 1
    canopy_report = validate_ganlan_stilted_house(uncovered)
    _assert(not canopy_report["passed"], "validator accepted an uncovered veranda")
    _assert(any("ganlan_veranda_canopy_too_shallow" in err
                for err in canopy_report["errors"]),
            f"canopy error not reported: {canopy_report['errors']}")


def test_sample_library_has_distinct_variants() -> None:
    compounds = sample_ganlan_stilted_house_library(2, 20260708,
                                                    load_style("ganlan_stilted_house"))
    _assert(len(compounds) == 2, "Ganlan library did not generate two samples")
    _assert(len({compound.variant.key() for compound in compounds}) == 2,
            "Ganlan library did not generate distinct small/medium variants")
    _assert({compound.lot_size for compound in compounds} == {(25, 28), (31, 32)},
            f"Ganlan sample lot sizes drifted: {[compound.lot_size for compound in compounds]}")


def main() -> int:
    test_group_binding()
    test_generated_sample_passes_validation()
    test_validation_rejects_bad_support_and_provenance()
    test_sample_library_has_distinct_variants()
    print("OK Ganlan stilted-house reference slice")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
