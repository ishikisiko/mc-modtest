"""Runtime 假山 placement driver (`garden-rockery` spec).

The companion to :mod:`tools.buildgen.rockery_models` (which bakes the
per-variant models + VoxelShapes offline). This module decides WHICH variant
goes WHERE in a garden parcel: a heightfield over the parcel bbox assigns each
cell a role by height decile (peak / slope / base / corner / standalone), and
within a role a deterministic per-cell seed picks one variant from the catalog.
The moss level is assigned by cell height (low → none, mid → light, peak →
heavy). The result is a ``{cell: (variant_id, moss_level)}`` map the compound
renderer turns into placed ``myvillage:rockery_block`` blocks.

Determinism contract (spec): the same ``(seed, bbox, params)`` yields an
identical placement, reusing :mod:`tools.buildgen.sect_mountain`'s ``_hash2`` /
``_noise`` so the derivation is reproducible across runs and (eventually) the
Java mirror.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

try:
    from .sect_mountain import _hash2, _noise  # package import (tools.buildgen.rockery)
    from .rockery_models import (VARIANT_CATALOG, VARIANT_BY_ID,
                                 ROLE_PEAK, ROLE_SLOPE, ROLE_BASE, ROLE_CORNER,
                                 ROLE_STANDALONE)
except ImportError:  # pragma: no cover - direct-run mode (cwd = tools/buildgen)
    from sect_mountain import _hash2, _noise
    from rockery_models import (VARIANT_CATALOG, VARIANT_BY_ID,
                                ROLE_PEAK, ROLE_SLOPE, ROLE_BASE, ROLE_CORNER,
                                ROLE_STANDALONE)

Cell2 = Tuple[int, int]
RockeryAssignment = Tuple[str, str]  # (variant_id, moss_level)


@dataclass(frozen=True)
class RockeryParams:
    """Tunable 假山 placement parameters (spec defaults)."""
    # Heightfield amplitude (cells) — how tall the 假山 is at its peak cell.
    max_height: int = 3
    # Height decile thresholds (fraction of max_height) for role assignment.
    # Cells above peak_threshold → peak; below base_threshold → base; between
    # → slope. Corners are detected by bbox-edge position; standalone is a
    # special role assigned by the caller (e.g. 汀步) not by the heightfield.
    peak_threshold: float = 0.75
    base_threshold: float = 0.30
    # Moss level thresholds (fraction of max_height).
    moss_light_threshold: float = 0.30
    moss_heavy_threshold: float = 0.70


def _heightfield(seed: int, x: int, z: int, bbox: Tuple[int, int, int, int],
                 params: RockeryParams) -> int:
    """Deterministic integer height (0..max_height) at parcel cell (x, z).

    A radial mound centered on the bbox center: peak at the center, falling to
    ~0 at the edges, textured with value noise so the surface isn't a clean
    cone. The heightfield is what the role + moss deciles read from.
    """
    x0, z0, x1, z1 = bbox
    cx = (x0 + x1) / 2
    cz = (z0 + z1) / 2
    half_w = max(1, (x1 - x0) / 2)
    half_d = max(1, (z1 - z0) / 2)
    # Normalized radial distance (0 at center, ~1 at the bbox edge).
    dx = abs(x - cx) / half_w
    dz = abs(z - cz) / half_d
    dist = max(dx, dz)  # chebyshev-like so corners read as edges
    # Falloff: center = max_height, edge = 0.
    base_h = params.max_height * (1.0 - min(1.0, dist))
    # Surface noise (±1 cell) for natural mound texture.
    noise = _noise(seed, x, z, 1)
    h = int(round(base_h + noise))
    return max(0, min(h, params.max_height))


def _is_corner_cell(x: int, z: int, bbox: Tuple[int, int, int, int]) -> bool:
    """True iff (x, z) is at a bbox corner cell (within 1 of both edges)."""
    x0, z0, x1, z1 = bbox
    on_x_edge = (x <= x0 + 1 or x >= x1 - 1)
    on_z_edge = (z <= z0 + 1 or z >= z1 - 1)
    return on_x_edge and on_z_edge


def _moss_for_height(h: int, params: RockeryParams) -> str:
    """Moss level by cell height (spec: low→none, mid→light, peak→heavy)."""
    if h <= 0:
        return "none"
    frac = h / max(1, params.max_height)
    if frac >= params.moss_heavy_threshold:
        return "heavy"
    if frac >= params.moss_light_threshold:
        return "light"
    return "none"


def _variant_for_role(role: str, seed: int, x: int, z: int) -> str:
    """Deterministic variant id within a role for cell (x, z).

    Picks one of the role's catalog variants using a per-cell hash so the same
    cell always gets the same variant (no shared RNG state). Hero variants
    (first in each role) are slightly favored so the visual anchors appear
    often enough to read.
    """
    role_variants = [v.variant_id for v in VARIANT_CATALOG if v.role == role]
    if not role_variants:
        # fall back to standalone (smallest catalog) if an unknown role slips in
        role_variants = [v.variant_id for v in VARIANT_CATALOG if v.role == ROLE_STANDALONE]
    # Hash to an index; bias toward index 0 (the hero) ~30% of the time so the
    # 主峰/副峰/孤赏石/池畔石/门道石 anchors appear regularly.
    h = _hash2(seed ^ 0xBADA55, x, z)
    if h % 10 < 3:
        return role_variants[0]
    return role_variants[h % len(role_variants)]


def _role_for_cell(height: int, x: int, z: int, bbox: Tuple[int, int, int, int],
                   params: RockeryParams, max_h_observed: int) -> str:
    """Assign a role to a cell by its height decile + corner position.

    Per spec: top height-decile → peak; bottom → base; middle → slope; bbox
    corners → corner. The deciles are computed against the observed max height
    in the bbox (not the param cap) so a flat 假山 doesn't label everything
    'peak'.
    """
    if _is_corner_cell(x, z, bbox):
        return ROLE_CORNER
    if max_h_observed <= 0:
        return ROLE_BASE
    frac = height / max_h_observed
    if frac >= params.peak_threshold:
        return ROLE_PEAK
    if frac <= params.base_threshold:
        return ROLE_BASE
    return ROLE_SLOPE


def derive_rockery(seed: int, bbox: Tuple[int, int, int, int],
                   params: Optional[RockeryParams] = None
                   ) -> Dict[Cell2, RockeryAssignment]:
    """Derive a 假山 placement over the parcel bbox (task 5.1).

    Returns ``{cell: (variant_id, moss_level)}`` for every cell in the bbox
    whose heightfield is > 0 (flat / ground cells are omitted — they stay the
    garden ground, not rockery). Deterministic given ``(seed, bbox, params)``.
    """
    params = params or RockeryParams()
    x0, z0, x1, z1 = bbox
    # 1) Compute the heightfield over the bbox, track the observed max.
    heights: Dict[Cell2, int] = {}
    max_h = 0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = _heightfield(seed, x, z, bbox, params)
            heights[(x, z)] = h
            if h > max_h:
                max_h = h
    # 2) Assign role + variant + moss per cell; drop flat (h==0) cells.
    out: Dict[Cell2, RockeryAssignment] = {}
    for (x, z), h in heights.items():
        if h <= 0:
            continue
        role = _role_for_cell(h, x, z, bbox, params, max_h)
        variant = _variant_for_role(role, seed, x, z)
        moss = _moss_for_height(h, params)
        out[(x, z)] = (variant, moss)
    return out


def derive_stepping_stones(seed: int, shore_a: Cell2, shore_b: Cell2,
                           ) -> Dict[Cell2, RockeryAssignment]:
    """汀步 (stepping stones) across a pond: a 1-cell path of ``standalone``
    variants connecting two shore points (task 5.6).

    The path is a simple L-shape (axis-aligned) between the two shores; each
    step cell gets a deterministic standalone variant + light moss (stepping
    stones read as water-worn, not peak-class). Deterministic given
    ``(seed, shore_a, shore_b)``.
    """
    out: Dict[Cell2, RockeryAssignment] = {}
    ax, az = shore_a
    bx, bz = shore_b
    # L-path: walk x first, then z (or whichever axis differs).
    xs = list(range(min(ax, bx), max(ax, bx) + 1))
    zs = list(range(min(az, bz), max(az, bz) + 1))
    standalone_variants = [v.variant_id for v in VARIANT_CATALOG
                           if v.role == ROLE_STANDALONE]
    if not standalone_variants:
        return out
    step = 0
    for x in xs:
        cell = (x, az)
        variant = standalone_variants[(_hash2(seed, x, az) + step) % len(standalone_variants)]
        out[cell] = (variant, "light")
        step += 1
    for z in zs:
        cell = (bx, z)
        if cell in out:
            continue
        variant = standalone_variants[(_hash2(seed, bx, z) + step) % len(standalone_variants)]
        out[cell] = (variant, "light")
        step += 1
    return out


def validate_rockery_placement(placement: Dict[Cell2, RockeryAssignment]) -> List[str]:
    """Sanity-check a derived placement (returns error strings; empty = OK).

    Verifies every variant id is in the catalog and every moss level is legal.
    Used by the compound validator's 假山 reachability pass.
    """
    errors: List[str] = []
    legal_moss = {"none", "light", "heavy"}
    for cell, (variant, moss) in placement.items():
        if variant not in VARIANT_BY_ID:
            errors.append(f"unknown_variant:{cell}:{variant}")
        if moss not in legal_moss:
            errors.append(f"bad_moss:{cell}:{moss}")
    return errors
