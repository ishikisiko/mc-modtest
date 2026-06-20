"""Generate the Python expectations consumed by TownGeometryParityTest."""

from __future__ import annotations

import json
from pathlib import Path

from buildgen.town import (TownSite, _boundary, _family_interior, _layout,
                           _perimeter_interior, _civic_core_aabb,
                           select_perimeter_shape)

SEEDS = (20260618, 20260719, 20260820, 20260921, 20261022)


def snapshot(seed: int) -> dict:
    site = TownSite()
    family, modifiers = select_perimeter_shape(seed)
    layout = _layout(site, {}, seed)
    protected = _civic_core_aabb(site, seed)
    interior = _perimeter_interior(site, seed, family, modifiers, protected)
    districts = []
    for district in layout.districts:
        rect = district.cells
        cells = rect if district.kind == "civic_core" else rect & interior
        districts.append(len(cells))
    return {
        "seed": seed,
        "family": family,
        "modifier": modifiers[0],
        "center_x": (layout.spine_x0 + layout.spine_x1) // 2,
        "lanes": [list(x) for x in __import__(
            "buildgen.town", fromlist=["_lane_bands"])._lane_bands(seed)],
        "perimeter_cells": len(_boundary(site, family, modifiers, seed, protected)),
        "interior_cells": len(interior),
        "district_widths": [d.bounds[2] - d.bounds[0] + 1 for d in layout.districts],
        "district_cells": districts,
    }


def main() -> None:
    site = TownSite()
    payload = {
        "snapshots": [snapshot(seed) for seed in SEEDS],
        "curves": [
            {"seed": seed, "family": family,
             "interior_cells": len(_family_interior(site, seed, family)),
             "perimeter_cells": len(_boundary(
                 site, family, ("none",), seed, _civic_core_aabb(site, seed)))}
            for seed in SEEDS for family in (
                "square", "circle", "oval", "dshape", "octagon", "trapezoid")
        ],
    }
    target = Path(__file__).resolve().parents[3] / "src/test/resources/town_geometry_parity.json"
    target.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
