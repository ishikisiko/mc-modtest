"""Only new regional forms; existing buildgen roof handlers are unchanged."""
from buildgen.grid import PRIORITY
from buildgen.ops import register_roof


def oasis_terrace(grid, style, rng, vol, graph=None):
    """A continuous earthen/sandstone roof with a low enclosing parapet."""
    del rng, graph
    y = vol.meta["foundation_h"] + vol.meta["wall_h"]
    roof = style.primary("ROOF_TERRACE")
    parapet = style.primary("ROOF_PARAPET")
    cells = []
    for z in range(vol.z0, vol.z1 + 1):
        for x in range(vol.x0, vol.x1 + 1):
            grid.set((x, y, z), roof, ["ROOF"], PRIORITY["ROOF"], "ROOF_TERRACE")
            cells.append((x, y, z))
            if x in (vol.x0, vol.x1) or z in (vol.z0, vol.z1):
                grid.set((x, y + 1, z), parapet, ["ROOF"], PRIORITY["ROOF"], "ROOF_PARAPET")
                cells.append((x, y + 1, z))
    return {"roof_type": "province_oasis_terrace", "roof_cells": cells,
            "peak_y": y + 1, "roofed_volume_ids": [vol.id]}


register_roof("province_oasis_terrace", oasis_terrace)
