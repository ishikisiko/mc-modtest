"""Integer-only perimeter-curve predicates shared by the town planner and the
runtime validator (design D2: curves use integer arithmetic so Python and Java
reproduce identical cell sets without float drift).

Every predicate returns the *interior* cell set of the shape clipped to the
site rectangle. The planner derives bitten = ``site_rect − interior``; the
runtime validator mirrors the same predicates bit-for-bit. A fixed
``gate_band`` rectangle on the south edge is unioned into every curvy interior
so the south gate seats on a straight horizontal run regardless of family
(spec: the gate-facing segment is a chord clipped to a straight run).

Coordinates mirror ``town.py``: x in [0, w), z in [0, d); z=0 is the south
edge (where the gate lives).
"""

from __future__ import annotations

from typing import Set, Tuple

Cell2 = Tuple[int, int]

# Gate-facing straight run: a rectangle on the south edge centered on cx that
# is ALWAYS interior. This guarantees the south-gate cells (cx-2..cx+2 at z=0)
# sit on a straight horizontal perimeter segment for every vocabulary entry.
GATE_RUN_HALF = 8     # 17-wide straight south face (>= the 5-wide gate footprint)
GATE_BAND_DEPTH = 2   # depth of the straight south band; blends into the curve

# Inset keeping curve interiors strictly inside the site so the perimeter cell
# set is well-defined and never clips the absolute site edge midpoints.
CIRCLE_MARGIN = 1
OCTAGON_K_MIN = 40    # true 45° octagon (replaces the 0.12.0 K=8 micro-chamfer)
DEFAULT_OCTAGON_K = 44
TRAPEZOID_SLANT = 12  # cells slanted inward on the east/west edges


def _site_cells(w: int, d: int) -> Set[Cell2]:
    return {(x, z) for x in range(w) for z in range(d)}


def gate_band(w: int, d: int, cx: int) -> Set[Cell2]:
    """Straight south-edge run [cx-GATE_RUN_HALF, cx+GATE_RUN_HALF] x [0, depth].

    Kept interior for every shape so the gate (cx-2..cx+2 @ z=0) seats on a
    straight segment. The depth blends into the curve because the curve at
    x=cx reaches z=cz-r <= GATE_BAND_DEPTH for inscribed shapes.
    """
    x0 = max(0, cx - GATE_RUN_HALF)
    x1 = min(w - 1, cx + GATE_RUN_HALF)
    return {(x, z) for x in range(x0, x1 + 1) for z in range(0, GATE_BAND_DEPTH + 1)}


def circle_interior(w: int, d: int, cx: int, cz: int, r: int) -> Set[Cell2]:
    """(x−cx)² + (z−cz)² ≤ r² (pure integer) ∪ gate_band."""
    band = gate_band(w, d, cx)
    r2 = r * r
    out: Set[Cell2] = set()
    # bounding box of the circle, clipped to the site
    x_lo, x_hi = max(0, cx - r), min(w - 1, cx + r)
    z_lo, z_hi = max(0, cz - r), min(d - 1, cz + r)
    for x in range(x_lo, x_hi + 1):
        dx = x - cx
        dx2 = dx * dx
        for z in range(z_lo, z_hi + 1):
            dz = z - cz
            if dx2 + dz * dz <= r2:
                out.add((x, z))
    out |= band
    return out


def ellipse_interior(w: int, d: int, cx: int, cz: int,
                     rx: int, rz: int) -> Set[Cell2]:
    """Integer cross-multiply ellipse: rz²·(x−cx)² + rx²·(z−cz)² ≤ (rx·rz)².

    No float division, so Python and Java agree bit-for-bit. Unioned with the
    gate band so the south gate seats straight.
    """
    band = gate_band(w, d, cx)
    rx2 = rx * rx
    rz2 = rz * rz
    rhs = rx2 * rz2
    out: Set[Cell2] = set()
    x_lo, x_hi = max(0, cx - rx), min(w - 1, cx + rx)
    z_lo, z_hi = max(0, cz - rz), min(d - 1, cz + rz)
    for x in range(x_lo, x_hi + 1):
        dx = x - cx
        dx2 = dx * dx
        for z in range(z_lo, z_hi + 1):
            dz = z - cz
            if rz2 * dx2 + rx2 * (dz * dz) <= rhs:
                out.add((x, z))
    out |= band
    return out


def dshape_interior(w: int, d: int, cx: int) -> Set[Cell2]:
    """半月 D-shape: semicircle on the north joined to a rectangle on the south.

    The south rectangle keeps the gate on a straight south edge; the north
    semicircle (centered on cx, bulging north) rounds the north silhouette.
    Bitten cells are the NW/NE corners outside the semicircle. The semicircle
    radius is sized so it spans the full width at its widest (the rectangle's
    north edge).
    """
    cz_north = d // 2          # center of the semicircle = mid-depth
    r = (w // 2) - CIRCLE_MARGIN
    r2 = r * r
    rect: Set[Cell2] = {(x, z) for x in range(w) for z in range(0, cz_north + 1)}
    semicircle: Set[Cell2] = set()
    x_lo, x_hi = max(0, cx - r), min(w - 1, cx + r)
    z_lo, z_hi = cz_north, min(d - 1, cz_north + r)
    for x in range(x_lo, x_hi + 1):
        dx = x - cx
        dx2 = dx * dx
        for z in range(z_lo, z_hi + 1):
            dz = z - cz_north
            if dx2 + dz * dz <= r2:
                semicircle.add((x, z))
    return rect | semicircle


def octagon_interior(w: int, d: int, k: int) -> Set[Cell2]:
    """Large octagon: site square minus 4 triangular corner bites of leg k.

    ``k`` is the chamfer leg (>= OCTAGON_K_MIN for a true 45° read). The south
    edge stays straight (the corner bites touch z=0 only at the corners, far
    from the cx-centered gate), so the gate seats straight without a gate band.
    """
    site = _site_cells(w, d)
    bitten: Set[Cell2] = set()
    for x in range(k):
        for z in range(k):
            if x + z < k:
                bitten.add((x, z))                          # NW
                bitten.add((w - 1 - x, z))                  # NE
                bitten.add((x, d - 1 - z))                  # SW
                bitten.add((w - 1 - x, d - 1 - z))          # SE
    return site - bitten


def trapezoid_interior(w: int, d: int, slant: int) -> Set[Cell2]:
    """Trapezoid: north and south edges straight, east/west edges slanted inward.

    The east and west walls slant inward by ``slant`` cells from south to north
    (west edge moves east as z increases; east edge moves west). Bitten cells
    are the two triangular wedges on the west and east. The south edge stays
    straight so the gate seats cleanly.
    """
    site = _site_cells(w, d)
    bitten: Set[Cell2] = set()
    for z in range(d):
        # inward offset grows linearly from 0 (south) to slant (north)
        offset = (slant * z) // max(1, d - 1)
        for off in range(offset):
            bitten.add((off, z))                            # west wedge
            bitten.add((w - 1 - off, z))                    # east wedge
    return site - bitten
