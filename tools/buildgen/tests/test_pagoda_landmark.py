"""Focused checks for the rebuilt pagoda landmark family.

Run from the repository root:
    python3 tools/buildgen/tests/test_pagoda_landmark.py
"""

from __future__ import annotations

import sys
import tempfile
import re
from pathlib import Path

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.archetypes import PAGODA_PROFILES  # noqa: E402
from buildgen.nbtread import read_gzipped_nbt  # noqa: E402
from buildgen.passes import generate_building  # noqa: E402
from buildgen.quality import pagoda_variant_distinctness, quality_check  # noqa: E402
from buildgen.sect import TEMPLATE_FOOTPRINT as SECT_TEMPLATE_FOOTPRINT  # noqa: E402
from buildgen.style import load_style  # noqa: E402
from buildgen.town import TEMPLATE_FOOTPRINT as TOWN_TEMPLATE_FOOTPRINT  # noqa: E402


def _assert(cond: bool, msg: str) -> None:
    if not cond:
        raise AssertionError(msg)


def _generate(index: int):
    ctx = generate_building(
        load_style("cultivation_town"), "pagoda", f"pagoda_v{index}",
        20260720 + index * 101, "cultivation_town")
    return ctx, quality_check(ctx, f"cultivation_town/pagoda_{index:03d}")


def test_profiles_are_deliberately_distinct() -> None:
    _assert(sorted(PAGODA_PROFILES) == [1, 2, 3],
            "pagoda profile table does not define v1..v3")
    _assert([PAGODA_PROFILES[i].stories for i in (1, 2, 3)] == [5, 5, 7],
            "pagoda profiles lost the accepted five/five/seven storey sequence")
    signatures = {profile.signature() for profile in PAGODA_PROFILES.values()}
    _assert(len(signatures) == 3, "pagoda profiles are not pairwise distinct")
    _assert(min(min(profile.footprint) for profile in PAGODA_PROFILES.values()) >= 15,
            "pagoda body expansion fell below the 15x15 minimum")


def test_generated_profiles_pass_pagoda_quality() -> None:
    reports = []
    for index in (1, 2, 3):
        _, report = _generate(index)
        _assert(report["passed"],
                f"pagoda v{index} failed quality: {report['errors']}")
        stats = report["stats"]
        _assert(stats["pagoda_stories"] == PAGODA_PROFILES[index].stories,
                f"pagoda v{index} storey count drifted")
        _assert(len(stats["pagoda_eave_levels"]) == stats["pagoda_stories"] - 1,
                f"pagoda v{index} is missing intermediate eaves")
        _assert(stats["pagoda_inset_reductions"] >= 2,
                f"pagoda v{index} does not visibly taper")
        _assert(stats["pagoda_lifted_corners"] >= 4 * (stats["pagoda_stories"] - 1),
                f"pagoda v{index} lacks lifted eave corners")
        _assert(stats["pagoda_crown_type"] == "pyramidal_roof",
                f"pagoda v{index} crown is not pyramidal")
        _assert(stats["pagoda_finial_cells"] >= 5,
                f"pagoda v{index} finial is too short")
        _assert(stats["pagoda_reference_usage"] == "calibration_only",
                f"pagoda v{index} reference usage drifted")
        _assert(stats["original_generated"] is True,
                f"pagoda v{index} lacks original-generated provenance")
        _assert(stats["copied_source_assets"] is False,
                f"pagoda v{index} reports copied source assets")
        reports.append(report)

    heights = [report["stats"]["pagoda_height"] for report in reports]
    ratios = [report["stats"]["pagoda_height_width_ratio"] for report in reports]
    _assert(max(heights) - min(heights) >= 8,
            f"pagoda height spread is too small: {heights}")
    _assert(max(ratios) >= 2.0,
            f"pagoda height/width ratios are too low: {ratios}")


def test_missing_eave_level_is_rejected() -> None:
    ctx, _ = _generate(1)
    main = ctx.graph.get("main")
    main.meta["pagoda_eave_levels"] = main.meta["pagoda_eave_levels"][:-1]
    report = quality_check(ctx, "cultivation_town/pagoda_001")
    _assert(not report["passed"], "validator accepted a missing pagoda eave")
    _assert(any("pagoda_eave_levels_missing" in error
                for error in report["errors"]),
            f"missing-eave error was not reported: {report['errors']}")


def test_pagoda_variant_gate() -> None:
    fake_reports = []
    with tempfile.TemporaryDirectory() as tmp:
        for index, (height, span, ratio) in enumerate(
                ((38, 21, 1.81), (46, 27, 1.70), (52, 23, 2.26)), start=1):
            name = f"pagoda_{index:03d}"
            Path(tmp, f"{name}.nbt").write_bytes(f"pagoda-{index}".encode("ascii"))
            fake_reports.append({
                "archetype": "pagoda",
                "scale_tier": f"pagoda_v{index}",
                "structure_id": f"cultivation_town/{name}",
                "stats": {
                    "pagoda_profile": PAGODA_PROFILES[index].signature(),
                    "pagoda_stories": PAGODA_PROFILES[index].stories,
                    "pagoda_max_span": span,
                    "pagoda_height_width_ratio": ratio,
                },
                "export": {"size": [span, height, span]},
            })
        report = pagoda_variant_distinctness(fake_reports, tmp)
        _assert(report["passed"], f"valid pagoda family failed: {report['errors']}")
        fake_reports[1]["stats"]["pagoda_profile"] = (
            fake_reports[0]["stats"]["pagoda_profile"])
        duplicate = pagoda_variant_distinctness(fake_reports, tmp)
        _assert(not duplicate["passed"],
                "pagoda distinctness gate accepted a duplicate profile")


def _java_pagoda_footprint(path: Path, structure_id: str) -> tuple[int, int]:
    source = path.read_text(encoding="utf-8")
    pattern = re.compile(
        rf'case "{re.escape(structure_id)}" -> new int\[\]\{{(\d+), (\d+)\}};')
    match = pattern.search(source)
    if not match:
        raise AssertionError(f"missing Java footprint mirror for {structure_id} in {path}")
    return int(match.group(1)), int(match.group(2))


def test_resource_footprints_fit_python_and_java_mirrors() -> None:
    root = _TOOLS_DIR.parent
    structure_dir = root / "src/main/resources/data/myvillage/structure"
    java_files = (
        root / "src/main/java/com/example/myvillage/town/TownGenerator.java",
        root / "src/main/java/com/example/myvillage/sect/SectGenerator.java",
    )
    for index in (1, 2, 3):
        structure_id = f"pagoda_{index:03d}"
        _, nbt = read_gzipped_nbt(str(structure_dir / f"{structure_id}.nbt"))
        width, _, depth = [int(value) for value in nbt["size"]]
        for label, table in (
                ("town", TOWN_TEMPLATE_FOOTPRINT),
                ("sect", SECT_TEMPLATE_FOOTPRINT)):
            planned = table[structure_id]
            _assert(planned[0] >= width and planned[1] >= depth,
                    f"{label} footprint {planned} does not contain "
                    f"{structure_id} {width}x{depth}")
        for java_file in java_files:
            planned = _java_pagoda_footprint(java_file, structure_id)
            _assert(planned[0] >= width and planned[1] >= depth,
                    f"{java_file.name} footprint {planned} does not contain "
                    f"{structure_id} {width}x{depth}")


def main() -> int:
    test_profiles_are_deliberately_distinct()
    test_generated_profiles_pass_pagoda_quality()
    test_missing_eave_level_is_rejected()
    test_pagoda_variant_gate()
    test_resource_footprints_fit_python_and_java_mirrors()
    print("OK rebuilt pagoda landmark family")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
