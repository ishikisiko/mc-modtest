"""Deterministic districted town-plan model and validators.

The runtime Java town command mirrors this planner. The Python version exists
for offline validation, JSON dumps, and top-down previews.

The plan partitions a ~160x160 footprint into named districts (gate / market /
residential / civic_core / fringe), each carrying a density target, storey band,
and material register supplied by the settlement group's district brief. Parcels
are subdivided within a district and inherit its brief; importance tier derives
from district kind (civic_core highest). The ritual axis (plaza / paifang /
lanterns) is expressed *inside* the civic_core district, with the shrine as the
sole dominant landmark. Market/residential parcels carry a street frontage edge
and share gable walls with neighbors; leftover narrow gaps become typed alleys.
"""

from __future__ import annotations

import json
import random
from collections import deque
from dataclasses import asdict, dataclass, field, replace
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple

from . import town_curves, town_hash  # sibling buildgen modules

Cell2 = Tuple[int, int]
Rect = Tuple[int, int, int, int]  # x0, z0, x1, z1 (inclusive)

# --- Scale constants -------------------------------------------------------
DEFAULT_FOOTPRINT_W = 160
DEFAULT_FOOTPRINT_D = 160
MIN_FOOTPRINT_AXIS = 96          # below this the district grammar does not fit
MAX_FOOTPRINT_AXIS = 160         # mirrors TownGenerator.MAX_FOOTPRINT_AXIS
BLOCK_BUDGET_CEILING = 140_000   # reviewable upper bound for a mid-size fair
INTERIOR_LANE_WIDTH = 2

MAX_IMPORTANCE_TIER = 3

# Seed-driven grid jitter bounds (design D4). These keep every district
# rectangular, the spine intersecting the south gate, and the civic_core clear
# of the precinct wall. Exported through parity_constants so the Java realizer
# hardcodes the same bounds and derives per-seed values via TownHash.
CENTER_X_JITTER = 4        # cx ∈ [w//2 − 4, w//2 + 4]
LANE_JITTER = 2            # each cross-lane z-band shifts by ±2
DISTRICT_WIDTH_JITTER = 3  # residential inner edges shift by ±3
DEFAULT_GRID_SEED = 20260618  # reference seed for parity_constants() layout

# --- Perimeter shape vocabulary (design D1, D2) ---------------------------
# The town wall is selected from a composable family + modifier set. A seed
# picks a base family from PERIMETER_FAMILIES and a modifier from
# PERIMETER_MODIFIERS independently via the shared town_hash module. Each base
# family is a pure integer-arithmetic function of (site, seed, family_id);
# each modifier composes by set algebra over the bitten-cells set. Every
# perimeter stays a single closed loop and seats the south gate on a straight
# run (curvy families union a fixed gate_band on the south edge). See
# docs/ai-kb/12_town_shape_vocabulary.md.
PERIMETER_FAMILIES: Tuple[str, ...] = (
    "square", "circle", "oval", "dshape", "octagon", "trapezoid")
PERIMETER_MODIFIERS: Tuple[str, ...] = ("none", "barbican", "bastion")
PERIMETER_DEFAULT_FAMILY = "square"

# Curvy-family parameters (mirrored by TownGenerator.java; the perimeter-cell
# counts in parity_constants() catch any drift).
OVAL_RX_MIN = 60
OVAL_RX_MAX = 74
OVAL_RZ_MIN = 50
OVAL_RZ_MAX = 64
OCTAGON_K = 44              # chamfer leg for the true-45° octagon (>= 40)
TRAPEZOID_SLANT = 12        # east/west edges slant inward by this many cells

# Modifier geometry (bit-algebra; touches the site boundary so the perimeter
# stays a single closed loop, and excludes the gate x-range so the south gate
# stays on its straight run).
BARBICAN_OFFSET = 9         # x-offset east of cx where the barbican bay starts
BARBICAN_WIDTH = 8
BARBICAN_DEPTH = 6
BASTION_HALF_W = 10         # north-center bastion indent half-width
BASTION_DEPTH = 8

# Importance tier contributed by each district kind. The planner never branches
# on style_id or district-name strings; it maps the generic ``kind`` token that
# the district brief supplies.
DISTRICT_IMPORTANCE: Dict[str, int] = {
    "civic_core": 3,
    "market": 2,
    "residential": 1,
    "gate": 1,
    "fringe": 0,
}

# Template footprints (x, z) used to size frontage modules so the shipped
# structure templates butt into continuous party-wall rows. Central civic
# landmarks use their own parcel sizing. These MUST stay in sync with the
# shipped .nbt sizes (including ancillary 厢房/后罩房/后院 volumes, which extend
# the built footprint beyond the main hall) so the planner allocates parcels
# the realized templates actually fit; regenerate then re-measure with
# tools/preview_structure.py / read_gzipped_nbt when templates change.
TEMPLATE_FOOTPRINT: Dict[str, Tuple[int, int]] = {
    "cultivation_house": (15, 15),
    "cultivation_house_001": (15, 15),
    "cultivation_house_002": (23, 15),
    "cultivation_house_003": (29, 19),
    "cultivation_shop": (15, 17),
    "cultivation_shop_001": (15, 18),
    "cultivation_shop_002": (22, 17),
    "cultivation_shop_003": (22, 23),
    "cultivation_market": (17, 17),
    "cultivation_market_001": (17, 17),
    "cultivation_market_002": (23, 19),
    "cultivation_market_003": (31, 18),
    "cultivation_inn": (21, 19),
    "cultivation_inn_001": (21, 19),
    "cultivation_inn_002": (27, 20),
    "cultivation_inn_003": (27, 24),
    "town_shrine": (23, 22),
    "town_shrine_001": (23, 22),
    "pagoda": (17, 19),
    "pagoda_001": (17, 19),
    "pagoda_002": (19, 21),
    "pagoda_003": (19, 21),
    "pavilion": (23, 21),
    "pavilion_001": (23, 21),
    "pavilion_002": (23, 21),
    "pavilion_003": (23, 21),
    "bell_drum_tower": (17, 19),
    "bell_drum_tower_001": (17, 21),
    "bell_drum_tower_002": (17, 19),
    "bell_drum_tower_003": (17, 19),
}

# Vertical-landmark archetypes (pagoda/pavilion/bell_drum_tower) registered as
# roof forms. The civic-core skyline rule requires at least one of these in the
# core district so the silhouette rises above the surrounding roofline.
VERTICAL_LANDMARK_ARCHETYPES = ("pagoda", "pavilion", "bell_drum_tower")

# Skyline relief thresholds: the civic core must carry at least this many
# above-threshold volumes, with at least one being a vertical landmark.
SKYLINE_TALL_STOREY = 2
SKYLINE_MIN_TALL_VOLUMES = 3


def _base_archetype(template_id: str) -> str:
    """'pagoda_001' -> 'pagoda'; 'town_shrine' -> 'town_shrine'."""
    if not template_id:
        return template_id
    parts = template_id.rsplit("_", 1)
    if len(parts) == 2 and parts[1].isdigit():
        return parts[0]
    return template_id


def is_vertical_landmark_template(template_id: str) -> bool:
    return _base_archetype(template_id) in VERTICAL_LANDMARK_ARCHETYPES


@dataclass(frozen=True)
class TownSite:
    width: int = DEFAULT_FOOTPRINT_W
    depth: int = DEFAULT_FOOTPRINT_D
    base_y: int = 64
    max_slope: int = 5

    def contains(self, cell: Cell2) -> bool:
        x, z = cell
        return 0 <= x < self.width and 0 <= z < self.depth


@dataclass(frozen=True)
class TownGate:
    id: str
    side: str
    cells: Tuple[Cell2, ...]


@dataclass(frozen=True)
class TownDistrict:
    id: str
    kind: str
    bounds: Rect
    density: int
    storey_band: Tuple[int, int]
    material_register: str
    archetype_roster: Tuple[str, ...]
    # Optional explicit shape (e.g. a chamfered fringe). When empty, ``cells``
    # falls back to the bounding rect; ``bounds`` is always the axis-aligned
    # bounding box used for spatial queries and the civic-precinct derivation.
    cells_override: Tuple[Cell2, ...] = ()

    @property
    def importance_tier(self) -> int:
        return DISTRICT_IMPORTANCE.get(self.kind, 0)

    @property
    def cells(self) -> Set[Cell2]:
        if self.cells_override:
            return set(self.cells_override)
        return _rect(*self.bounds)


@dataclass(frozen=True)
class TownParcel:
    id: str
    role: str
    bounds: Rect
    importance_tier: int
    ground_ref: int
    roof_grade_hint: str
    height_hint: str
    dominant_landmark: bool = False
    template_id: str = ""
    district_id: str = ""
    district_kind: str = ""
    material_register: str = ""
    storey_hint: int = 0
    # "" or one of N/S/E/W: the edge that faces the street. When set, the
    # building is placed aligned to that edge and neighbors butt at gable lines.
    frontage_edge: str = ""

    @property
    def cells(self) -> Set[Cell2]:
        x0, z0, x1, z1 = self.bounds
        return _rect(x0, z0, x1, z1)

    @property
    def center(self) -> Cell2:
        x0, z0, x1, z1 = self.bounds
        return ((x0 + x1) // 2, (z0 + z1) // 2)

    @property
    def frontage_cells(self) -> Set[Cell2]:
        if not self.frontage_edge:
            return set()
        x0, z0, x1, z1 = self.bounds
        if self.frontage_edge == "S":
            return _rect(x0, z0, x1, z0)
        if self.frontage_edge == "N":
            return _rect(x0, z1, x1, z1)
        if self.frontage_edge == "E":
            return _rect(x1, z0, x1, z1)
        if self.frontage_edge == "W":
            return _rect(x0, z0, x0, z1)
        return set()


@dataclass(frozen=True)
class NegativeSpace:
    id: str
    kind: str
    bounds: Rect
    density_rank: int
    district_kind: str = ""
    archetype: str = ""
    # Optional explicit shape (e.g. a triangular moat). When empty, ``cells``
    # falls back to the bounding rect; the Java realizer mirrors only rect
    # regions, so non-rect negative space is a Python/preview nicety.
    cells_override: Tuple[Cell2, ...] = ()

    @property
    def cells(self) -> Set[Cell2]:
        if self.cells_override:
            return set(self.cells_override)
        return _rect(*self.bounds)


@dataclass
class TownPlan:
    seed: int
    site: TownSite
    perimeter: Set[Cell2]
    wall_cells: Set[Cell2]
    gates: List[TownGate]
    spine: Set[Cell2]
    lane_cells: Set[Cell2]
    parcels: List[TownParcel]
    negative_spaces: List[NegativeSpace]
    districts: List[TownDistrict] = field(default_factory=list)
    alleys: List[NegativeSpace] = field(default_factory=list)
    district_brief: List[Dict[str, object]] = field(default_factory=list)
    ritual_axis: Dict[str, object] = field(default_factory=dict)
    shape_family: str = PERIMETER_DEFAULT_FAMILY
    shape_modifiers: Tuple[str, ...] = ("none",)
    shape_id: str = PERIMETER_DEFAULT_FAMILY

    @property
    def street_cells(self) -> Set[Cell2]:
        return set(self.spine) | set(self.lane_cells)

    @property
    def parcel_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for parcel in self.parcels:
            cells.update(parcel.cells)
        return cells

    @property
    def negative_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for region in self.negative_spaces:
            cells.update(region.cells)
        return cells

    @property
    def alley_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for region in self.alleys:
            cells.update(region.cells)
        return cells

    @property
    def district_cells(self) -> Set[Cell2]:
        cells: Set[Cell2] = set()
        for district in self.districts:
            cells |= (district.cells - self.street_cells)
        return cells

    def to_dict(self) -> dict:
        return {
            "seed": self.seed,
            "site": asdict(self.site),
            "shape_id": self.shape_id,
            "shape_family": self.shape_family,
            "shape_modifiers": list(self.shape_modifiers),
            "perimeter": _cells_to_json(self.perimeter),
            "wall_cells": _cells_to_json(self.wall_cells),
            "gates": [
                {"id": g.id, "side": g.side, "cells": _cells_to_json(g.cells)}
                for g in self.gates
            ],
            "spine": _cells_to_json(self.spine),
            "lane_cells": _cells_to_json(self.lane_cells),
            "districts": [asdict(d) for d in self.districts],
            "parcels": [
                {
                    **asdict(parcel),
                    "cells": _cells_to_json(parcel.cells),
                }
                for parcel in self.parcels
            ],
            "negative_spaces": [_negative_to_dict(r) for r in self.negative_spaces],
            "alleys": [_negative_to_dict(r) for r in self.alleys],
            "district_brief": list(self.district_brief),
            "ritual_axis": self.ritual_axis,
        }


# --- geometry helpers ------------------------------------------------------


def _rect(x0: int, z0: int, x1: int, z1: int) -> Set[Cell2]:
    if x1 < x0 or z1 < z0:
        return set()
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}


def _cells_to_json(cells: Iterable[Cell2]) -> List[List[int]]:
    return [[x, z] for x, z in sorted(cells)]


def _negative_to_dict(region: NegativeSpace) -> dict:
    # Serialize explicitly so the optional ``cells_override`` field (a tuple of
    # pairs, possibly non-rectangular like a moat triangle) is not leaked into
    # the JSON output alongside the resolved ``cells``.
    return {
        "id": region.id,
        "kind": region.kind,
        "bounds": list(region.bounds),
        "density_rank": region.density_rank,
        "district_kind": region.district_kind,
        "archetype": region.archetype,
        "cells": _cells_to_json(region.cells),
    }


def _family_interior(site: TownSite, seed: int, family_id: str) -> Set[Cell2]:
    """Interior cell set of a base family, clipped to the site square.

    Pure integer arithmetic for every family (design D2). Curvy families union
    the fixed south gate_band so the south gate seats on a straight run.
    """
    w, d = site.width, site.depth
    cx, cz = w // 2, d // 2
    if family_id == "circle":
        r = min(w, d) // 2 - town_curves.CIRCLE_MARGIN
        return town_curves.circle_interior(w, d, cx, cz, r)
    if family_id == "oval":
        rx = town_hash.range64(seed, "oval_rx", OVAL_RX_MIN, OVAL_RX_MAX)
        rz = town_hash.range64(seed, "oval_rz", OVAL_RZ_MIN, OVAL_RZ_MAX)
        # Seat the ellipse against the south gate instead of centering a short
        # rz around mid-site (which would leave the gate band as a detached
        # island). ``rz + 1`` makes the curve begin at z=1, adjacent to the
        # fixed gate band, while its north lobe overlaps the protected core.
        return town_curves.ellipse_interior(w, d, cx, rz + 1, rx, rz)
    if family_id == "dshape":
        return town_curves.dshape_interior(w, d, cx)
    if family_id == "octagon":
        return town_curves.octagon_interior(w, d, OCTAGON_K)
    if family_id == "trapezoid":
        return town_curves.trapezoid_interior(w, d, TRAPEZOID_SLANT)
    # square (and any unknown id) -> full site rectangle
    return {(x, z) for x in range(w) for z in range(d)}


def _modifier_bitten(
    site: TownSite, seed: int, modifier_id: str
) -> Set[Cell2]:
    """Cells a modifier removes from the site square (design D1 set algebra).

    Every modifier bite touches the site boundary so the perimeter stays a
    single closed loop, and avoids the gate x-range so the south gate stays on
    its straight segment. Returns ``{}`` for ``none`` (and unknown ids).
    """
    if modifier_id == "none":
        return set()
    w, d = site.width, site.depth
    cx = w // 2
    bitten: Set[Cell2] = set()
    if modifier_id == "barbican":
        # Recessed bay on the south wall, east of the gate band.
        x0 = max(0, cx + BARBICAN_OFFSET)
        x1 = min(w - 1, x0 + BARBICAN_WIDTH - 1)
        for x in range(x0, x1 + 1):
            for z in range(0, BARBICAN_DEPTH + 1):
                bitten.add((x, z))
    elif modifier_id == "bastion":
        # Rectangular notches on the east and west edge midpoints (a stepped
        # lateral silhouette). Sited at z~cz so it never overlaps the civic_core
        # AABB (z >= 111) or the south gate band; touches x=0 / x=w-1 so the
        # perimeter stays a single closed loop.
        cz = d // 2
        z0_b = max(0, cz - BASTION_HALF_W)
        z1_b = min(d - 1, cz + BASTION_HALF_W)
        for x in range(0, BASTION_DEPTH):
            for z in range(z0_b, z1_b + 1):
                bitten.add((x, z))                         # west notch
                bitten.add((w - 1 - x, z))                 # east notch
    return bitten


def shape_bitten(
    site: TownSite,
    seed: int,
    family_id: str,
    modifiers: Sequence[str],
    protected_rect: Optional[Rect] = None,
) -> Set[Cell2]:
    """Bitten cells = (site square − family interior) ∪ every modifier bite.

    Any cell inside ``protected_rect`` (the civic_core AABB) is never bitten, so
    the un-clipped rectangular civic precinct stays fully inside the wall even
    for inscribed-circle families whose disk would otherwise clip its corners.
    """
    site_cells = {(x, z) for x in range(site.width) for z in range(site.depth)}
    interior = _family_interior(site, seed, family_id)
    bitten = site_cells - interior
    for mod in modifiers:
        bitten |= _modifier_bitten(site, seed, mod)
    if protected_rect is not None:
        px0, pz0, px1, pz1 = protected_rect
        protected = _rect(px0, pz0, px1, pz1)
        bitten -= protected
    return bitten


def _civic_core_aabb(site: TownSite, seed: int = 0) -> Rect:
    """The civic_core AABB the layout emits (parity-coupled, seed-driven).

    The perimeter masks every bite against this rect so the rectangular civic
    precinct — whose bounds derive from the seed-driven cx + lane_n + core
    half-width — stays fully inside the wall for every vocabulary entry. The
    formula mirrors ``_layout`` exactly so Python and Java agree cell-for-cell.
    """
    cx = _center_x(site, seed)
    _, _, lane_n = _lane_bands(seed)
    return (cx - 36, lane_n[1] + 1, cx + 36, site.depth - 2)


def select_perimeter_shape(seed: int) -> Tuple[str, Tuple[str, ...]]:
    """Deterministic (family, modifiers) selection via town_hash (design D1/D3).

    Family and modifier are picked independently. ``square`` remains in the
    family roster so it is selected ~1/6 of the time (recognizable variant).
    Returns ``(family_id, (modifier_id,))`` — exactly one modifier per plan,
    which may be ``none``.
    """
    family = town_hash.pick(seed, "family", PERIMETER_FAMILIES)
    modifier = town_hash.pick(seed, "modifier", PERIMETER_MODIFIERS)
    return family, (modifier,)


def shape_id_str(family_id: str, modifiers: Sequence[str]) -> str:
    """Display id: ``"family"`` or ``"family+mod1+mod2"`` (``none`` omitted)."""
    mods = [m for m in modifiers if m and m != "none"]
    return family_id if not mods else family_id + "+" + "+".join(mods)


def _perimeter_interior(
    site: TownSite,
    seed: int,
    family_id: str,
    modifiers: Sequence[str],
    protected_rect: Optional[Rect] = None,
) -> Set[Cell2]:
    """Interior cell set of the composed shape = site square − bitten cells."""
    site_cells = {(x, z) for x in range(site.width) for z in range(site.depth)}
    return site_cells - shape_bitten(site, seed, family_id, modifiers, protected_rect)


def _boundary(
    site: TownSite,
    family_id: str = PERIMETER_DEFAULT_FAMILY,
    modifiers: Sequence[str] = ("none",),
    seed: int = 0,
    protected_rect: Optional[Rect] = None,
) -> Set[Cell2]:
    """The town wall: the cell-boundary of the composed perimeter interior.

    For ``square`` (no bites) this is exactly the site's outer ring. For the
    vocabulary families it is the single closed boundary of (site minus bitten
    cells), which never leaves the site. The south-gate segment stays straight
    because curvy families union the gate_band and modifier bites avoid the
    gate x-range. ``protected_rect`` (civic_core AABB) is never bitten so the
    rectangular precinct stays inside the wall.
    """
    w, d = site.width, site.depth
    bitten = shape_bitten(site, seed, family_id, modifiers, protected_rect)
    if not bitten:
        cells = {(x, 0) for x in range(w)}
        cells |= {(x, d - 1) for x in range(w)}
        cells |= {(0, z) for z in range(d)}
        cells |= {(w - 1, z) for z in range(d)}
        return cells
    interior = {(x, z) for x in range(w) for z in range(d)
                if (x, z) not in bitten}
    perimeter: Set[Cell2] = set()
    for x, z in interior:
        if ((x + 1, z) not in interior
                or (x - 1, z) not in interior
                or (x, z + 1) not in interior
                or (x, z - 1) not in interior):
            perimeter.add((x, z))
    return perimeter


def _fringe_cells_override(district: TownDistrict, family_id: str) -> Tuple[Cell2, ...]:
    """Optional non-rectangular cell set for a fringe district.

    Stage α (wall-only vocabulary): all districts stay rectangular regardless of
    the perimeter family, so the wall↔district gap is reserved moat negative
    space (kept inward of the perimeter). The general clip-to-shape for outer
    districts lands in stage β (task 5). Returns ``()`` (rectangular) for now.
    """
    return ()


def _importance_hint(tier: int) -> Tuple[str, str]:
    if tier >= 3:
        return "dominant", "landmark"
    if tier == 2:
        return "tall", "fine"
    if tier == 1:
        return "base", "standard"
    return "low", "plain"


def _brief_index(brief: Sequence[Mapping[str, object]]) -> Dict[str, Dict[str, object]]:
    out: Dict[str, Dict[str, object]] = {}
    for entry in brief or ():
        kind = str(entry.get("kind", ""))
        out[kind] = dict(entry)
    return out


# --- district + street layout ---------------------------------------------


def _lane_bands(seed: int) -> Tuple[Tuple[int, int], Tuple[int, int], Tuple[int, int]]:
    """The three cross-lane z-bands as a pure function of seed (design D4).

    Each band is a 3-wide run jittered by ±LANE_JITTER. The south lane's lower
    bound is constrained to ``[base, base + LANE_JITTER]`` (never below base)
    because the gate district's depth is ``lane_s[0] − 1`` and must stay >= the
    15-deep gate-house template — a downward jitter would shrink it below fit.
    Centralized so the planner, the civic_core AABB derivation, and the parity
    export all agree.
    """
    def band(base_lo: int, tag: str, lo_jitter: int = -LANE_JITTER) -> Tuple[int, int]:
        j = town_hash.range64(seed, tag, lo_jitter, LANE_JITTER)
        return (base_lo + j, base_lo + 2 + j)
    return band(16, "lane_s", 0), band(60, "lane_m"), band(108, "lane_n")


def _center_x(site: TownSite, seed: int) -> int:
    """Seed-driven spine centerline (cx = w//2 ± CENTER_X_JITTER)."""
    return site.width // 2 + town_hash.range64(seed, "cx", -CENTER_X_JITTER, CENTER_X_JITTER)


def _district_width_jitters(seed: int) -> Dict[str, int]:
    """Independent bounded width offsets for every paired outer district.

    The semantic ids are stable across sites (unlike the coordinate-bearing
    ``TownDistrict.id``), which makes them suitable town-hash tags and parity
    keys. Positive values widen the named west/east pair toward the site edge;
    the central street/spine edges remain fixed and therefore cannot be cut.
    """
    return {
        key: town_hash.range64(
            seed, f"district_width_{key}",
            -DISTRICT_WIDTH_JITTER, DISTRICT_WIDTH_JITTER)
        for key in (
            "south_res_w", "south_res_e", "market_w", "market_e",
            "mid_res_w", "mid_res_e", "fringe_w", "fringe_e")
    }


@dataclass(frozen=True)
class _Layout:
    districts: List[TownDistrict]
    spine: Set[Cell2]
    lanes: Set[Cell2]
    plaza: Rect
    paifang: Set[Cell2]
    lanterns: Set[Cell2]
    shrine_bounds: Rect
    shrine_parcel: Rect
    spine_x0: int
    spine_x1: int
    west_civic_bounds: Rect
    east_civic_bounds: Rect
    west_landmark_bounds: Rect
    east_landmark_bounds: Rect
    # Civic-precinct framing (intra-core reserved structure). All derived
    # deterministically from cx / spine / plaza / shrine / landmark bounds so
    # the Java realizer can mirror them without a shared RNG.
    precinct_gate_cells: Set[Cell2] = field(default_factory=set)
    spirit_way_band: Set[Cell2] = field(default_factory=set)
    side_hall_west_bounds: Rect = (0, 0, -1, -1)
    side_hall_east_bounds: Rect = (0, 0, -1, -1)
    colonnade_west_cells: Set[Cell2] = field(default_factory=set)
    colonnade_east_cells: Set[Cell2] = field(default_factory=set)
    precinct_wall_cells: Set[Cell2] = field(default_factory=set)
    precinct_side_gate_cells: Set[Cell2] = field(default_factory=set)


def _layout(
    site: TownSite,
    brief_by_kind: Dict[str, Dict[str, object]],
    seed: int = DEFAULT_GRID_SEED,
) -> _Layout:
    """Partition the footprint into districts and the street network.

    District rectangles are parameterized by site size and seed. The seed
    drives bounded grid jitter (design D4) via ``town_hash``: the spine
    centerline ``cx``, the three cross-lane z-bands, and the residential inner
    edges each move within tight bounds that keep every district rectangular,
    the spine intersecting the south gate, and the civic_core clear of the
    precinct wall. The grid stays strictly orthogonal.
    """
    w, d = site.width, site.depth
    if w < MIN_FOOTPRINT_AXIS or d < MIN_FOOTPRINT_AXIS:
        raise ValueError(
            f"site too small for districted town: {w}x{d} "
            f"(minimum {MIN_FOOTPRINT_AXIS}x{MIN_FOOTPRINT_AXIS})")
    if w > MAX_FOOTPRINT_AXIS or d > MAX_FOOTPRINT_AXIS:
        raise ValueError(
            f"site exceeds footprint cap: {w}x{d} "
            f"(maximum {MAX_FOOTPRINT_AXIS}x{MAX_FOOTPRINT_AXIS})")

    # Seed-driven grid jitter (design D4). Bounds keep the spine intersecting
    # the south gate, districts rectangular, and the civic_core clear of the
    # precinct wall.
    cx = _center_x(site, seed)
    spine_half = 3
    spine_x0, spine_x1 = cx - spine_half, cx + spine_half

    # z-bands separated by 3-wide cross lanes, each jittered by ±LANE_JITTER.
    lane_s, lane_m, lane_n = _lane_bands(seed)
    # Each paired outer district gets an independent width offset. Only the
    # edge away from the spine moves, so no district can sever the axis.
    width_jitter = _district_width_jitters(seed)

    def b(kind: str, x0: int, z0: int, x1: int, z1: int) -> TownDistrict:
        entry = brief_by_kind.get(kind, {})
        density = int(entry.get("density", 1))
        band = entry.get("storey_band", [1, 1])
        storey_band = (int(band[0]), int(band[1])) if isinstance(band, (list, tuple)) else (1, 1)
        material = str(entry.get("material_register", ""))
        roster = tuple(str(a) for a in entry.get("archetypes", ()) or ())
        did = f"{kind}_{x0}_{z0}_{x1}_{z1}"
        return TownDistrict(
            id=did,
            kind=kind,
            bounds=(x0, z0, x1, z1),
            density=density,
            storey_band=storey_band,
            material_register=material,
            archetype_roster=roster,
        )

    districts: List[TownDistrict] = [
        b("gate", cx - 24, 1, cx + 24, lane_s[0] - 1),
        b("residential", 8 - width_jitter["south_res_w"], 1,
          cx - 25, lane_s[0] - 1),
        b("residential", cx + 25, 1,
          w - 9 + width_jitter["south_res_e"], lane_s[0] - 1),
        b("market", 8 - width_jitter["market_w"], lane_s[1] + 1,
          spine_x0 - 1, lane_m[0] - 1),
        b("market", spine_x1 + 1, lane_s[1] + 1,
          w - 9 + width_jitter["market_e"], lane_m[0] - 1),
        b("residential", 8 - width_jitter["mid_res_w"], lane_m[1] + 1,
          spine_x0 - 1, lane_n[0] - 1),
        b("residential", spine_x1 + 1, lane_m[1] + 1,
          w - 9 + width_jitter["mid_res_e"], lane_n[0] - 1),
        b("civic_core", cx - 36, lane_n[1] + 1, cx + 36, d - 2),
        b("fringe", 8 - width_jitter["fringe_w"], lane_n[1] + 1,
          cx - 37, d - 2),
        b("fringe", cx + 37, lane_n[1] + 1,
          w - 9 + width_jitter["fringe_e"], d - 2),
    ]

    # Ritual axis, expressed entirely inside the civic_core band. The shrine is
    # the sole dominant landmark; plaza + paifang + lanterns live in the core.
    # shrine_d sizes the parcel depth to fit the shipped town_shrine template
    # (plus 1 cell of margin); keep it in sync with the regenerated .nbt depth.
    shrine_w, shrine_d = 23, 21
    shrine_x0 = cx - shrine_w // 2 - 1
    shrine_x1 = shrine_x0 + shrine_w + 1        # 25 wide parcel, 23 template centered
    shrine_z1 = d - 2
    shrine_z0 = shrine_z1 - shrine_d             # 22 deep parcel, fits 22-deep shrine
    shrine_parcel = (shrine_x0, shrine_z0, shrine_x1, shrine_z1)
    plaza = (cx - 16, shrine_z0 - 9, cx + 16, shrine_z0 - 1)
    paifang = set(_rect(cx - 6, plaza[1] - 1, cx + 6, plaza[1] - 1))
    lanterns: Set[Cell2] = set()
    for z in range(lane_n[1] + 2, plaza[1] - 1, 5):
        lanterns.add((cx - 5, z))
        lanterns.add((cx + 5, z))

    spine = set(_rect(spine_x0, 0, spine_x1, plaza[3]))
    spine |= set(_rect(*plaza))
    spine |= paifang

    lanes: Set[Cell2] = set()
    for lz in (lane_s, lane_m, lane_n):
        lanes |= set(_rect(8, lz[0], w - 9, lz[1]))
    lanes -= spine

    west_civic = (cx - 33, plaza[1] - 10, plaza[0] - 1, plaza[3])
    east_civic = (plaza[2] + 1, plaza[1] - 10, cx + 33, plaza[3])

    # Vertical landmarks (pagoda + bell/drum tower) flank the shrine inside the
    # civic core so the skyline rises above the surrounding roofline. Generous
    # parcels fit the 17-19 wide / 19-21 deep landmark templates with margin.
    # landmark_d sizes the parcel depth to the deepest shipped landmark template
    # (bell_drum_tower at depth 21); keep it in sync with the regenerated .nbt.
    landmark_w = 19
    landmark_d = 21
    landmark_w0 = shrine_x0 - 3 - landmark_w
    landmark_w1 = shrine_x0 - 3
    landmark_e0 = shrine_x1 + 3
    landmark_e1 = shrine_x1 + 3 + landmark_w
    landmark_z0 = shrine_z0
    landmark_z1 = min(d - 2, shrine_z0 + landmark_d - 1)
    west_landmark = (landmark_w0, landmark_z0, landmark_w1, landmark_z1)
    east_landmark = (landmark_e0, landmark_z0, landmark_e1, landmark_z1)

    # --- Civic-precinct framing ------------------------------------------
    # Stage a processional approach (precinct gate + dressed spirit way),
    # enclose the plaza (side halls + lateral colonnade), and wrap the core in
    # a precinct wall with gates that doubles as the core<->fringe edge. All
    # geometry derives from the bounds above; no fresh RNG.
    core = next(dist for dist in districts if dist.kind == "civic_core")
    core_x0, core_z0, core_x1, core_z1 = core.bounds

    # 1.2 Precinct gate: a paifang-style run on the spine at the core's
    # gate-facing edge (z-min), reusing the cx/spine half-width. The gate
    # cells stay spine (passable); the wall opens around them.
    precinct_gate_cells = set(_rect(spine_x0, core_z0, spine_x1, core_z0))

    # 1.3 Spirit-way band: flanking statue/stele cells along the spine between
    # the precinct gate and the plaza, masked off the spine walking width and
    # the existing lantern cells. Every-other-row cadence keeps props sparse.
    spirit_z0 = core_z0 + 1
    spirit_z1 = plaza[1] - 1
    spirit_way_band: Set[Cell2] = set()
    for z in range(spirit_z0, spirit_z1 + 1):
        if (z - spirit_z0) % 2:
            continue
        for fx in (cx - 5, cx + 5):
            cell = (fx, z)
            if cell in spine or cell in lanterns:
                continue
            spirit_way_band.add(cell)

    # 1.4 Side-hall bounds fill the forecourt gaps between the two civic halls
    # (each gap runs from the plaza edge to the spine approach), stopping short
    # of the paifang row and clearing the lantern columns. Colonnade edge
    # runs consume the lateral core slivers; the outermost sliver column is
    # reserved for the precinct wall, the inner columns carry the covered walk.
    # A sliver narrower than 2 degrades to wall-only (no colonnade).
    side_hall_west_bounds = (plaza[0], west_civic[1], plaza[0] + 10, plaza[1] - 2)
    side_hall_east_bounds = (plaza[2] - 10, east_civic[1], plaza[2], plaza[1] - 2)

    _civic_rect_cells = (set(_rect(*west_civic)) | set(_rect(*east_civic))
                         | set(_rect(*shrine_parcel))
                         | set(_rect(*west_landmark)) | set(_rect(*east_landmark)))
    _approach_cells = spine | set(_rect(*plaza)) | lanterns | paifang

    def _colonnade_run(inner_x0: int, inner_x1: int) -> Set[Cell2]:
        if inner_x1 - inner_x0 + 1 < 2:
            return set()  # degraded to wall-only
        raw = set(_rect(inner_x0, core_z0, inner_x1, core_z1))
        return raw - _civic_rect_cells - _approach_cells

    colonnade_west_cells = _colonnade_run(core_x0 + 1, core_x0 + 2)
    colonnade_east_cells = _colonnade_run(core_x1 - 2, core_x1 - 1)

    # 1.5 Precinct wall along the gate-facing (south) and lateral edges (the
    # back/north edge is left open per D4). Lateral cells lie on the core<->
    #fringe boundary. The spine gate opens at the precinct gate; one 2-cell
    # side gate per lateral edge sits at the edge midpoint.
    south_edge = _rect(core_x0, core_z0, core_x1, core_z0)
    west_edge = _rect(core_x0, core_z0, core_x0, core_z1)
    east_edge = _rect(core_x1, core_z0, core_x1, core_z1)
    precinct_wall_cells = (south_edge | west_edge | east_edge)
    precinct_wall_cells -= spine  # open the spine gate (precinct gate is spine)
    side_z = (core_z0 + core_z1) // 2
    precinct_side_gate_cells = {
        (core_x0, side_z), (core_x0, side_z + 1),
        (core_x1, side_z), (core_x1, side_z + 1),
    }
    precinct_wall_cells -= precinct_side_gate_cells
    # Wall takes priority over the colonnade at the south corners so the
    # boundary reads as a continuous wall line.
    colonnade_west_cells -= precinct_wall_cells
    colonnade_east_cells -= precinct_wall_cells

    return _Layout(
        districts=districts,
        spine=spine,
        lanes=lanes,
        plaza=plaza,
        paifang=paifang,
        lanterns=lanterns,
        shrine_bounds=(shrine_x0 + 1, shrine_z0 + 1, shrine_x1 - 1, shrine_z1 - 1),
        shrine_parcel=shrine_parcel,
        spine_x0=spine_x0,
        spine_x1=spine_x1,
        west_civic_bounds=west_civic,
        east_civic_bounds=east_civic,
        west_landmark_bounds=west_landmark,
        east_landmark_bounds=east_landmark,
        precinct_gate_cells=precinct_gate_cells,
        spirit_way_band=spirit_way_band,
        side_hall_west_bounds=side_hall_west_bounds,
        side_hall_east_bounds=side_hall_east_bounds,
        colonnade_west_cells=colonnade_west_cells,
        colonnade_east_cells=colonnade_east_cells,
        precinct_wall_cells=precinct_wall_cells,
        precinct_side_gate_cells=precinct_side_gate_cells,
    )


def parity_constants(
    seed: int = DEFAULT_GRID_SEED,
    family_id: Optional[str] = None,
    modifiers: Optional[Sequence[str]] = None,
) -> Dict[str, object]:
    """Geometry shared with ``TownGenerator.java`` for a Python/Java parity check.

    Computed from the default-site ``_layout`` at a fixed reference seed so it
    stays in lock-step with the planner; the runtime validator compares these
    against the Java-hardcoded values to catch drift. Covers the seed-driven
    grid jitter bounds (Java hardcodes the bounds and derives per-seed values
    via TownHash), the reference-seed ritual-axis + civic-precinct geometry,
    and the perimeter-vocabulary family/modifier cell counts.
    """
    site = TownSite()
    reference_seed = seed
    ly = _layout(site, {}, reference_seed)
    core = next(d for d in ly.districts if d.kind == "civic_core")
    fringes = [d for d in ly.districts if d.kind == "fringe"]
    protected_rect = _civic_core_aabb(site, reference_seed)
    selected_family, selected_modifiers = select_perimeter_shape(reference_seed)
    if family_id is not None:
        selected_family = family_id
    if modifiers is not None:
        selected_modifiers = tuple(modifiers)
    selected_interior = _perimeter_interior(
        site, reference_seed, selected_family, selected_modifiers, protected_rect)
    selected_perimeter = _boundary(
        site, selected_family, selected_modifiers, reference_seed, protected_rect)

    def _fringe_cells_rect(d: TownDistrict) -> Set[Cell2]:
        ov = _fringe_cells_override(d, "square")
        return set(ov) if ov else _rect(*d.bounds)

    # Per-family perimeter + interior cell counts. square/circle/dshape/octagon/
    # trapezoid are seed-independent; oval is seed-derived, so its radii are
    # exported alongside the reference-seed counts.
    family_cells: Dict[str, Dict[str, int]] = {}
    for fam in PERIMETER_FAMILIES:
        mods = ("none",)
        perim = _boundary(site, fam, mods, reference_seed, protected_rect)
        interior = _perimeter_interior(site, reference_seed, fam, mods, protected_rect)
        family_cells[fam] = {"perimeter": len(perim), "interior": len(interior)}

    oval_rx = town_hash.range64(reference_seed, "oval_rx", OVAL_RX_MIN, OVAL_RX_MAX)
    oval_rz = town_hash.range64(reference_seed, "oval_rz", OVAL_RZ_MIN, OVAL_RZ_MAX)

    # Per-modifier bitten-cell counts at the reference seed on a square base.
    modifier_cells: Dict[str, int] = {}
    for mod in PERIMETER_MODIFIERS:
        modifier_cells[mod] = len(_modifier_bitten(site, reference_seed, mod))

    out: Dict[str, object] = {
        "WIDTH": DEFAULT_FOOTPRINT_W,
        "DEPTH": DEFAULT_FOOTPRINT_D,
        "BASE_CENTER_X": DEFAULT_FOOTPRINT_W // 2,
        "CENTER_X_JITTER": CENTER_X_JITTER,
        "LANE_JITTER": LANE_JITTER,
        "DISTRICT_WIDTH_JITTER": DISTRICT_WIDTH_JITTER,
        "GRID_REFERENCE_SEED": reference_seed,
        "SELECTED_FAMILY": selected_family,
        "SELECTED_MODIFIERS": list(selected_modifiers),
        "SELECTED_PERIMETER_CELLS": len(selected_perimeter),
        "SELECTED_INTERIOR_CELLS": len(selected_interior),
        "CENTER_X": _center_x(site, reference_seed),
        "SPINE_HALF_WIDTH": 3,
        "LANE_S": list(_lane_bands(reference_seed)[0]),
        "LANE_M": list(_lane_bands(reference_seed)[1]),
        "LANE_N": list(_lane_bands(reference_seed)[2]),
        "SHRINE_W": 23,
        "SHRINE_D": 21,
        "LANDMARK_W": 19,
        "LANDMARK_D": 21,
        "CORE_BOUNDS": list(core.bounds),
        "PLAZA": list(ly.plaza),
        "SIDE_HALL_WIDTH": 11,
        "SIDE_HALL_PARCELS": 2,
        "COLONNADE_SLIVER_WIDTH": 2,
        "PERIMETER_FAMILIES": list(PERIMETER_FAMILIES),
        "PERIMETER_MODIFIERS": list(PERIMETER_MODIFIERS),
        "PERIMETER_REFERENCE_SEED": reference_seed,  # vocabulary cell counts use this seed
        "GATE_RUN_HALF": town_curves.GATE_RUN_HALF,
        "GATE_BAND_DEPTH": town_curves.GATE_BAND_DEPTH,
        "CIRCLE_MARGIN": town_curves.CIRCLE_MARGIN,
        "OVAL_RX_MIN": OVAL_RX_MIN,
        "OVAL_RX_MAX": OVAL_RX_MAX,
        "OVAL_RZ_MIN": OVAL_RZ_MIN,
        "OVAL_RZ_MAX": OVAL_RZ_MAX,
        "OVAL_RX_REF": oval_rx,
        "OVAL_RZ_REF": oval_rz,
        "OCTAGON_K": OCTAGON_K,
        "TRAPEZOID_SLANT": TRAPEZOID_SLANT,
        "BARBICAN_OFFSET": BARBICAN_OFFSET,
        "BARBICAN_WIDTH": BARBICAN_WIDTH,
        "BARBICAN_DEPTH": BARBICAN_DEPTH,
        "BASTION_HALF_W": BASTION_HALF_W,
        "BASTION_DEPTH": BASTION_DEPTH,
        "FRINGE_CELL_COUNT_SQUARE": sum(len(_fringe_cells_rect(d)) for d in fringes),
        "PRECINCT_WALL_CELLS": len(ly.precinct_wall_cells),
        "COLONNADE_CELLS": len(ly.colonnade_west_cells) + len(ly.colonnade_east_cells),
        "SPIRIT_WAY_CELLS": len(ly.spirit_way_band),
        "PRECINCT_GATE_CELLS": len(ly.precinct_gate_cells),
        "SIDE_GATE_CELLS": len(ly.precinct_side_gate_cells),
    }
    width_jitter = _district_width_jitters(reference_seed)
    for key, value in width_jitter.items():
        out[f"DISTRICT_WIDTH_JITTER_{key.upper()}"] = value
    for index, district in enumerate(ly.districts):
        x0, _z0, x1, _z1 = district.bounds
        out[f"DISTRICT_WIDTH_{index}_{district.kind.upper()}"] = x1 - x0 + 1
        rect_cells = _rect(*district.bounds)
        clipped = (rect_cells & selected_interior
                   if district.kind != "civic_core" else rect_cells)
        out[f"DISTRICT_CELL_COUNT_{index}_{district.kind.upper()}"] = len(clipped)
        out[f"DISTRICT_CLIPPED_{index}_{district.kind.upper()}"] = clipped != rect_cells
    for fam, counts in family_cells.items():
        out[f"PERIMETER_CELLS_{fam.upper()}"] = counts["perimeter"]
        out[f"INTERIOR_CELLS_{fam.upper()}"] = counts["interior"]
    for mod, count in modifier_cells.items():
        out[f"MODIFIER_BITTEN_CELLS_{mod.upper()}"] = count
    return out


# --- parcel subdivision ----------------------------------------------------


def _template_size(template_id: str) -> Tuple[int, int]:
    if template_id in TEMPLATE_FOOTPRINT:
        return TEMPLATE_FOOTPRINT[template_id]
    # compound-library tissue templates are wide; default to a generous module.
    return (15, 15)


def _district_module(kind: str) -> int:
    """Smallest building-module width a frontage district subdivides into."""
    roster = {
        "market": ("cultivation_shop", "cultivation_market"),
        "residential": ("cultivation_house",),
        "gate": ("cultivation_house",),
    }.get(kind, ("cultivation_house",))
    widths = [TEMPLATE_FOOTPRINT.get(a, (15, 15))[0] for a in roster]
    return min(widths) if widths else 15


def _storey_for(district: TownDistrict, rng: random.Random, idx: int) -> int:
    # Deterministic storey within the band (no rng) so the Java realizer, which
    # cannot share Python's Random sequence, mirrors the plan exactly. ``rng``
    # is accepted for forward compatibility with future stochastic phases.
    lo, hi = district.storey_band
    if hi <= lo:
        return lo
    return lo + idx % (hi - lo + 1)


# Shipped .nbt variants for each abstract archetype. The plan references a
# concrete numbered template so the runtime realizer and the NBT-reading
# validator resolve real structure files.
TEMPLATE_VARIANTS: Dict[str, Tuple[str, ...]] = {
    "cultivation_house": (
        "cultivation_house_001", "cultivation_house_002", "cultivation_house_003"),
    "cultivation_shop": (
        "cultivation_shop_001", "cultivation_shop_002", "cultivation_shop_003"),
    "cultivation_market": (
        "cultivation_market_001", "cultivation_market_002", "cultivation_market_003"),
    "cultivation_inn": (
        "cultivation_inn_001", "cultivation_inn_002", "cultivation_inn_003"),
    "town_shrine": ("town_shrine_001",),
    "pagoda": ("pagoda_001", "pagoda_002", "pagoda_003"),
    "pavilion": ("pavilion_001", "pavilion_002", "pavilion_003"),
    "bell_drum_tower": ("bell_drum_tower_001", "bell_drum_tower_002", "bell_drum_tower_003"),
}


def _template_for(district: TownDistrict, rng: random.Random, variant_idx: int = 0) -> str:
    roster = district.archetype_roster
    base = roster[0] if roster else "cultivation_house"
    variants = TEMPLATE_VARIANTS.get(base, (base,))
    return variants[variant_idx % len(variants)]


def _variant_that_fits(base: str, rng: random.Random, max_w: int, max_d: int) -> str:
    """Pick a shipped variant of ``base`` whose footprint fits max_w x max_d.

    Deterministic (first fitting) so Java mirrors the plan. ``rng`` is accepted
    for forward compatibility.
    """
    variants = TEMPLATE_VARIANTS.get(base, (base,))
    for v in variants:
        tw, td = _template_size(v)
        if tw <= max_w and td <= max_d:
            return v
    return min(variants, key=lambda v: _template_size(v))


def _opposite_edge(side: str) -> str:
    """Return the opposite cardinal edge (N↔S, E↔W)."""
    return {"N": "S", "S": "N", "E": "W", "W": "E"}.get(side, "")


def _subdivide_frontage(
    district: TownDistrict,
    side: str,
    along_start: int,
    along_end: int,
    band_lo: int,
    band_hi: int,
    base_archetype: str,
    parcels: List[TownParcel],
    alleys: List[NegativeSpace],
    idx: int,
    seed: int,
    base_y: int,
    rng: random.Random,
) -> int:
    """Subdivide one frontage edge into contiguous party-wall parcels.

    ``along_start/along_end`` are the (inclusive) range along the frontage edge;
    ``band_lo/hi`` are the (inclusive) perpendicular bounds of the parcel band.
    ``base_archetype`` is the abstract archetype (e.g. "cultivation_house");
    variants are cycled per segment using seed + position for variety while
    keeping party-wall butt lines aligned through width-aware sizing.

    Narrower-than-min-variant leftovers close as typed alleys; every
    ``alley_every`` run also inserts a deliberate 1-wide alley gap.

    Returns updated *idx* (next parcel/alley counter).
    """
    kind = district.kind
    variants = TEMPLATE_VARIANTS.get(base_archetype, (base_archetype,))
    min_width = min(TEMPLATE_FOOTPRINT.get(v, (15, 15))[0] for v in variants)

    alley_every = 3
    run = 0
    i = along_start
    parcel_counter = 0

    while i <= along_end:
        remaining = (along_end - i + 1)
        if remaining < min_width:
            if side in ("N", "S"):
                bounds = (i, band_lo, along_end, band_hi)
            else:
                bounds = (band_lo, i, band_hi, along_end)
            alleys.append(NegativeSpace(
                id=f"alley_{kind}_{idx}", kind="alley", bounds=bounds,
                density_rank=0, district_kind=kind, archetype=base_archetype))
            idx += 1
            break

        # Boundary-aware retry/shift path. At a clipped curve, advance to the
        # first coordinate where a complete minimum footprint fits instead of
        # consuming a phantom segment and leaving a thin emitted fragment.
        fitting: List[Tuple[str, int, Rect]] = []
        reserve = max(0, 2 - run) * min_width  # preserve room for a 3-house row
        for variant in variants:
            width = TEMPLATE_FOOTPRINT.get(variant, (15, 15))[0]
            if width > remaining or width > remaining - reserve:
                continue
            end = i + width - 1
            candidate = ((i, band_lo, end, band_hi) if side in ("N", "S")
                         else (band_lo, i, band_hi, end))
            if _parcel_fits(district, candidate):
                fitting.append((variant, width, candidate))
        if not fitting:
            i += 1
            continue
        choice = town_hash.range64(
            seed, f"frontage_variant:{district.id}:{side}:{i}",
            0, len(fitting) - 1)
        chosen_variant, seg_width, bounds = fitting[choice]

        seg_end = i + seg_width - 1
        parcels.append(_make_parcel(
            f"parcel_{kind}_{idx}", district, bounds, side, chosen_variant,
            _storey_for(district, rng, parcel_counter), base_y, rng))
        idx += 1
        parcel_counter += 1
        i = seg_end + 1
        run += 1

        if run >= alley_every and i <= along_end:
            if side in ("N", "S"):
                gap_bounds = (i, band_lo, i, band_hi)
            else:
                gap_bounds = (band_lo, i, band_hi, i)
            alleys.append(NegativeSpace(
                id=f"alley_{kind}_{idx}", kind="alley", bounds=gap_bounds,
                density_rank=0, district_kind=kind, archetype=base_archetype))
            idx += 1
            i += 1
            run = 0

    return idx


def _parcel_fits(district: TownDistrict, bounds: Rect) -> bool:
    """A parcel's rect must lie wholly within the district's cell set.

    For the default rectangular districts this is always true. When a district
    is non-rectangular (e.g. a chamfered fringe), parcels crossing the cut
    cells are rejected so no parcel spills outside its district. Parcel-bearing
    districts are currently unstepped, so this guard is forward-looking and a
    no-op for the shipped plans.
    """
    if not district.cells_override:
        return True
    return _rect(*bounds) <= set(district.cells_override)


def _make_parcel(
    pid: str,
    district: TownDistrict,
    bounds: Rect,
    frontage_edge: str,
    template_id: str,
    storey: int,
    base_y: int,
    rng: random.Random,
    *,
    role: str = "",
    importance: Optional[int] = None,
    dominant: bool = False,
) -> TownParcel:
    tier = district.importance_tier if importance is None else importance
    height, roof = _importance_hint(tier)
    resolved_role = role or _role_for_kind(district.kind)
    return TownParcel(
        id=pid,
        role=resolved_role,
        bounds=bounds,
        importance_tier=tier,
        ground_ref=base_y,
        roof_grade_hint=roof,
        height_hint=height,
        dominant_landmark=dominant,
        template_id=template_id,
        district_id=district.id,
        district_kind=district.kind,
        material_register=district.material_register,
        storey_hint=storey,
        frontage_edge=frontage_edge,
    )


def _role_for_kind(kind: str) -> str:
    return {
        "gate": "housing",
        "market": "market",
        "residential": "housing",
        "civic_core": "civic",
        "fringe": "housing",
    }.get(kind, "housing")


def _subdivide_district(
    district: TownDistrict,
    layout: _Layout,
    streets: Set[Cell2],
    rng: random.Random,
    base_y: int,
    start_index: int,
    seed: int,
    interior_lanes: Set[Cell2],
) -> Tuple[List[TownParcel], List[NegativeSpace], List[NegativeSpace], int]:
    """Subdivide a district into (parcels, alleys, yard_negatives, next_idx)."""
    x0, z0, x1, z1 = district.bounds
    parcels: List[TownParcel] = []
    alleys: List[NegativeSpace] = []
    negatives: List[NegativeSpace] = []
    idx = start_index
    kind = district.kind

    if kind == "civic_core":
        # civic_core holds the dominant shrine (handled by caller) plus two
        # auxiliary civic halls flanking the plaza and two vertical landmarks
        # (pagoda + bell/drum tower) flanking the shrine, satisfying the skyline
        # relief rule; no party-wall frontage.
        for aux_id, aux_bounds, base in (
            ("civic_west_hall", layout.west_civic_bounds, "cultivation_shop"),
            ("civic_east_hall", layout.east_civic_bounds, "cultivation_shop"),
        ):
            ax0, az0, ax1, az1 = aux_bounds
            if ax1 < ax0 or az1 < az0:
                continue
            tpl = _variant_that_fits(base, rng, ax1 - ax0 + 1, az1 - az0 + 1)
            parcels.append(_make_parcel(
                aux_id, district, aux_bounds, "", tpl,
                _storey_for(district, rng, idx), base_y, rng,
                importance=min(MAX_IMPORTANCE_TIER, district.importance_tier)))
            idx += 1
        for lm_id, lm_bounds, lm_tpl in (
            ("pagoda_west", layout.west_landmark_bounds, "pagoda_001"),
            ("bell_drum_tower_east", layout.east_landmark_bounds, "bell_drum_tower_001"),
        ):
            lx0, lz0, lx1, lz1 = lm_bounds
            if lx1 < lx0 or lz1 < lz0:
                continue
            parcels.append(_make_parcel(
                lm_id, district, lm_bounds, "", lm_tpl,
                district.storey_band[1], base_y, rng,
                importance=MAX_IMPORTANCE_TIER, role="civic"))
            idx += 1
        # Side halls (配殿) enclose the plaza forecourt: low-tier civic parcels
        # placed in the forecourt gaps between the civic halls, capped below the
        # dominant-landmark tier and within the storey band so the skyline and
        # importance hierarchy are preserved. The gaps are narrower than any
        # shipped template, so these are block-built civic fill (empty
        # template_id); the realizer dresses them from the archetype roster.
        SIDE_HALL_TIER = MAX_IMPORTANCE_TIER - 1
        for sh_id, sh_bounds in (
            ("civic_side_hall_west", layout.side_hall_west_bounds),
            ("civic_side_hall_east", layout.side_hall_east_bounds),
        ):
            sx0, sz0, sx1, sz1 = sh_bounds
            if sx1 < sx0 or sz1 < sz0:
                continue
            parcels.append(_make_parcel(
                sh_id, district, sh_bounds, "", "",
                district.storey_band[0], base_y, rng,
                importance=SIDE_HALL_TIER, role="civic"))
            idx += 1
        return parcels, alleys, negatives, idx

    if kind == "gate":
        # Two gate-approach parcels split around the spine (so neither overlaps
        # the street), each fronting the spine. Skip a parcel that would cross
        # a clipped district edge (curve corners) so no gate parcel spills
        # outside the wall.
        for gi, (gx0, gx1, edge) in enumerate(
            ((x0, layout.spine_x0 - 1, "E"),
             (layout.spine_x1 + 1, x1, "W"))
        ):
            if gx1 < gx0:
                continue
            bounds = (gx0, z0, gx1, z1)
            if not _parcel_fits(district, bounds):
                continue
            tpl = _variant_that_fits("cultivation_house", rng, gx1 - gx0 + 1, z1 - z0 + 1)
            parcels.append(_make_parcel(
                f"parcel_gate_{idx}", district, bounds, edge,
                tpl, district.storey_band[0], base_y, rng))
            idx += 1
        return parcels, alleys, negatives, idx

    if kind == "fringe":
        # Fringe is spirit-field negative space (灵田/药圃); the caller emits the
        # field region. No building parcels: fields are the point of the fringe.
        return parcels, alleys, negatives, idx

    # market / residential: pick the district edge that actually touches a
    # street as the frontage edge (no style/name branching).
    side, _, _, _, perp_extent = _choose_frontage_edge(
        district, streets, prefer=("S", "N", "E", "W"))

    if side == "" or perp_extent < 12:
        return parcels, alleys, negatives, idx

    base_archetype = district.archetype_roster[0] if district.archetype_roster else "cultivation_house"
    variants = TEMPLATE_VARIANTS.get(base_archetype, (base_archetype,))
    max_td = 0
    min_tw = 999999
    min_td = 999999
    for v in variants:
        w, t = _template_size(v)
        max_td = max(max_td, t)
        min_tw = min(min_tw, w)
        min_td = min(min_td, t)

    yard = 8
    # Decide densification against the FULL district depth (perp_extent), not a
    # leftover already shrunk by the padded primary band. When the district is
    # deep enough for primary + lane + secondary + residual, trim the primary
    # band to exact template depth so a back row can be carved.
    has_secondary = perp_extent >= 2 * max_td + INTERIOR_LANE_WIDTH
    depth = max_td if has_secondary else min(max_td + yard, perp_extent)
    if depth < max_td:
        return parcels, alleys, negatives, idx

    # compute band positions
    if side in ("E", "W"):
        if side == "E":
            band_lo = x1 - depth + 1
            band_hi = x1
            yard_start = x0
            yard_end = band_lo - 1
        else:  # W
            band_lo = x0
            band_hi = x0 + depth - 1
            yard_start = band_hi + 1
            yard_end = x1
    elif side == "S":
        band_lo = z0
        band_hi = z0 + depth - 1
        yard_start = band_hi + 1
        yard_end = z1
    else:  # N
        band_lo = z1 - depth + 1
        band_hi = z1
        yard_start = z0
        yard_end = band_lo - 1

    if side in ("E", "W"):
        along_start = z0
        along_end = z1
    else:
        along_start = x0
        along_end = x1

    idx = _subdivide_frontage(
        district, side, along_start, along_end, band_lo, band_hi,
        base_archetype, parcels, alleys, idx, seed, base_y, rng)

    if has_secondary:
        sec_side = _opposite_edge(side)
        if side == "S":
            sec_band_lo = band_hi + INTERIOR_LANE_WIDTH + 1
            sec_band_hi = sec_band_lo + max_td - 1
            idx = _subdivide_frontage(
                district, sec_side, along_start, along_end,
                sec_band_lo, sec_band_hi, base_archetype,
                parcels, alleys, idx, seed, base_y, rng)
            interior_lanes.update(_rect(along_start, band_hi + 1, along_end, sec_band_lo - 1))
            yard_bounds = (along_start, sec_band_hi + 1, along_end, z1)
        elif side == "N":
            sec_band_lo = band_lo - INTERIOR_LANE_WIDTH - max_td
            sec_band_hi = band_lo - INTERIOR_LANE_WIDTH - 1
            idx = _subdivide_frontage(
                district, sec_side, along_start, along_end,
                sec_band_lo, sec_band_hi, base_archetype,
                parcels, alleys, idx, seed, base_y, rng)
            interior_lanes.update(_rect(along_start, sec_band_hi + 1, along_end, band_lo - 1))
            yard_bounds = (along_start, z0, along_end, sec_band_lo - 1)
        elif side == "E":
            sec_band_lo = band_lo - INTERIOR_LANE_WIDTH - max_td
            sec_band_hi = band_lo - INTERIOR_LANE_WIDTH - 1
            idx = _subdivide_frontage(
                district, sec_side, along_start, along_end,
                sec_band_lo, sec_band_hi, base_archetype,
                parcels, alleys, idx, seed, base_y, rng)
            interior_lanes.update(_rect(sec_band_hi + 1, along_start, band_lo - 1, along_end))
            yard_bounds = (x0, along_start, sec_band_lo - 1, along_end)
        else:  # W
            sec_band_lo = band_hi + INTERIOR_LANE_WIDTH + 1
            sec_band_hi = sec_band_lo + max_td - 1
            idx = _subdivide_frontage(
                district, sec_side, along_start, along_end,
                sec_band_lo, sec_band_hi, base_archetype,
                parcels, alleys, idx, seed, base_y, rng)
            interior_lanes.update(_rect(band_hi + 1, along_start, sec_band_lo - 1, along_end))
            yard_bounds = (sec_band_hi + 1, along_start, x1, along_end)

        yx0, yz0, yx1, yz1 = yard_bounds
        if yx1 >= yx0 and yz1 >= yz0 and (yx1 - yx0 + 1) * (yz1 - yz0 + 1) >= 8:
            negatives.append(NegativeSpace(
                id=f"yard_{kind}_{idx}", kind="courtyard_yard",
                bounds=yard_bounds, density_rank=1))
            idx += 1
        return parcels, alleys, negatives, idx

    # single-band path: compute yard bounds behind the primary frontage
    if side in ("E", "W"):
        yard_bounds = (yard_start, z0, yard_end, z1)
    else:
        yard_bounds = (x0, yard_start, x1, yard_end)

    yx0, yz0, yx1, yz1 = yard_bounds
    if yx1 >= yx0 and yz1 >= yz0 and (yx1 - yx0 + 1) * (yz1 - yz0 + 1) >= 8:
        negatives.append(NegativeSpace(
            id=f"yard_{kind}_{idx}", kind="courtyard_yard",
            bounds=yard_bounds, density_rank=1))
        idx += 1
    return parcels, alleys, negatives, idx


def _edge_touches_street(
    district_bounds: Rect, side: str, streets: Set[Cell2]
) -> bool:
    x0, z0, x1, z1 = district_bounds
    if side == "S":
        edge = _rect(x0, z0, x1, z0)
        nbr = _rect(x0, z0 - 1, x1, z0 - 1)
    elif side == "N":
        edge = _rect(x0, z1, x1, z1)
        nbr = _rect(x0, z1 + 1, x1, z1 + 1)
    elif side == "E":
        edge = _rect(x1, z0, x1, z1)
        nbr = _rect(x1 + 1, z0, x1 + 1, z1)
    else:  # W
        edge = _rect(x0, z0, x0, z1)
        nbr = _rect(x0 - 1, z0, x0 - 1, z1)
    return bool(nbr & streets)


def _choose_frontage_edge(
    district: TownDistrict, streets: Set[Cell2], prefer: Sequence[str]
) -> Tuple[str, int, int, range, int]:
    """Return (side, band_lo, band_hi, along, perpendicular_extent).

    ``band_lo/hi`` are placeholder zeros (the caller recomputes the band from
    ``side`` + depth); only ``side`` and ``perp_extent`` are authoritative.
    """
    x0, z0, x1, z1 = district.bounds
    for side in prefer:
        if _edge_touches_street(district.bounds, side, streets):
            if side in ("E", "W"):
                return side, 0, 0, range(z0, z1 + 1), x1 - x0 + 1
            return side, 0, 0, range(x0, x1 + 1), z1 - z0 + 1
    return "", 0, 0, range(0), 0


# --- plan assembly ---------------------------------------------------------


def generate_town_plan(
    seed: int,
    site: Optional[TownSite] = None,
    district_brief: Optional[Sequence[Mapping[str, object]]] = None,
) -> TownPlan:
    site = site or TownSite()
    if district_brief is None:
        district_brief = []

    brief_by_kind = _brief_index(district_brief)
    layout = _layout(site, brief_by_kind, seed)

    rng = random.Random(seed)
    shape_family, shape_modifiers = select_perimeter_shape(seed)
    shape_id = shape_id_str(shape_family, shape_modifiers)
    # The civic_core AABB is never bitten so the rectangular precinct (whose
    # bounds derive from cx/lane_n/core-half-width) stays fully inside the wall
    # for every vocabulary entry, including inscribed-circle families.
    protected_rect = _civic_core_aabb(site, seed)
    perimeter = _boundary(site, shape_family, shape_modifiers, seed, protected_rect)
    shape_interior = _perimeter_interior(
        site, seed, shape_family, shape_modifiers, protected_rect)
    w, d = site.width, site.depth
    cx = _center_x(site, seed)
    south_gate_cells = tuple((x, 0) for x in range(cx - 2, cx + 3))
    gates = [TownGate("south_gate", "south", south_gate_cells)]
    gate_cells = set(south_gate_cells)
    wall_cells = perimeter - gate_cells

    parcels: List[TownParcel] = []
    alleys: List[NegativeSpace] = []
    negative_spaces: List[NegativeSpace] = []

    # Deformed-wall moat: the cells bitten out of the site square (corner
    # triangles / curve corners / wall notches) become a named negative-space
    # region so the preview can dress them and the validator accounts for them.
    # They are exterior to the wall, so after clip-to-shape they never overlap
    # parcels or streets.
    bitten = shape_bitten(site, seed, shape_family, shape_modifiers, protected_rect)
    if bitten:
        bx0 = min(c[0] for c in bitten)
        bz0 = min(c[1] for c in bitten)
        bx1 = max(c[0] for c in bitten)
        bz1 = max(c[1] for c in bitten)
        negative_spaces.append(NegativeSpace(
            id="wall_moat", kind="moat",
            bounds=(bx0, bz0, bx1, bz1), density_rank=0,
            cells_override=tuple(sorted(bitten))))

    # Clip-to-shape (Lever B, design D5): whenever the composed shape bites any
    # cells (a non-square family OR a modifier on any base), every outer district
    # (gate / market / residential / fringe) whose AABB reaches the perimeter
    # curve gets cells = AABB ∩ perimeter_interior so its parcels follow the
    # curve. The civic_core stays rectangular (cells = AABB) because the
    # civic-precinct derivation is coupled to core.bounds; the protected_rect
    # above guarantees it sits fully inside the wall.
    # The normative outer set is market/residential/fringe. Gate is clipped as
    # well when a modifier bites into its AABB, otherwise a barbican/bastion
    # moat can overlap a gate-house parcel near the south wall.
    outer_kinds = ("gate", "market", "residential", "fringe")
    clip_outer = bool(bitten)
    districts_out: List[TownDistrict] = []
    for dist in layout.districts:
        rect_cells = _rect(*dist.bounds)
        clipped_cells = rect_cells & shape_interior
        if (clip_outer and dist.kind in outer_kinds
                and clipped_cells != rect_cells):
            districts_out.append(replace(
                dist, cells_override=tuple(sorted(clipped_cells))))
        else:
            districts_out.append(dist)

    # Civic core shrine first (dominant landmark, sole top tier).
    core = next(dist for dist in districts_out if dist.kind == "civic_core")
    shrine_tpl = "town_shrine_001"
    shrine_parcel = _make_parcel(
        "town_shrine", core, layout.shrine_parcel, "", shrine_tpl,
        core.storey_band[1], site.base_y, rng,
        importance=MAX_IMPORTANCE_TIER, dominant=True, role="civic")
    parcels.append(shrine_parcel)

    # Subdivide every (clipped) district. The civic_core also gets auxiliary
    # halls. Streets are clipped to the perimeter interior so cross-lane ends
    # that would exit the wall are trimmed; the spine<->gate<->core connection
    # is preserved because the spine runs through the cx column (always inside
    # the gate band and the disk). ``_parcel_fits`` rejects any parcel that
    # would cross a clipped district edge.
    idx = 100
    raw_streets = (set(layout.spine) | set(layout.lanes)) & shape_interior
    streets = set(raw_streets)
    interior_lanes: Set[Cell2] = set()
    for district in districts_out:
        sub_parcels, sub_alleys, sub_negatives, idx = _subdivide_district(
            district, layout, streets, rng, site.base_y, idx, seed, interior_lanes)
        parcels.extend(sub_parcels)
        alleys.extend(sub_alleys)
        negative_spaces.extend(sub_negatives)
    # Trim interior lanes to the wall as well, then finalize the street set.
    interior_lanes &= shape_interior
    streets = (set(layout.spine) | set(layout.lanes) | interior_lanes) & shape_interior

    # Fringe spirit-field negative spaces (灵田 / 药圃), confined to each fringe
    # district's clipped cells so the field stays inside the wall.
    for district in districts_out:
        if district.kind != "fringe":
            continue
        d_cells = district.cells
        fx0, fz0, fx1, fz1 = district.bounds
        field_bounds = (max(fx0, fx0 + 1), max(fz0, fz0 + 6),
                        min(fx1, fx1 - 1), min(fz1, fz1 - 1))
        field_cells = (_rect(*field_bounds) & d_cells) if district.cells_override \
            else _rect(*field_bounds)
        negative_spaces.append(NegativeSpace(
            id=f"spirit_field_{district.id}", kind="spirit_field",
            bounds=field_bounds, density_rank=0,
            cells_override=tuple(sorted(field_cells))))

    # Civic-precinct reserved structures (wall / colonnade / spirit way) are
    # masked against the final parcel + street set so nothing overlaps. The
    # layout already masks them against the known civic rectangles; this second
    # pass accounts for the side halls emitted above. The precinct gate stays
    # on the spine (the wall opens around it) so gate -> shrine stays connected.
    parcel_cell_set: Set[Cell2] = set()
    for parcel in parcels:
        parcel_cell_set |= parcel.cells
    precinct_colonnade_cells = (
        (set(layout.colonnade_west_cells) | set(layout.colonnade_east_cells))
        - parcel_cell_set - streets)
    precinct_wall_cells = (set(layout.precinct_wall_cells)
                           - parcel_cell_set - streets)
    precinct_gate_cells = set(layout.precinct_gate_cells)
    precinct_side_gate_cells = set(layout.precinct_side_gate_cells)
    spirit_way_cells = set(layout.spirit_way_band) - parcel_cell_set

    ritual_axis = {
        "kind": "cultivation_town_ritual_axis",
        "from_gate": "south_gate",
        "terminus_parcel": "town_shrine",
        "terminus_district": "civic_core",
        "plaza_bounds": list(layout.plaza),
        "paifang_gate_cells": _cells_to_json(layout.paifang),
        "lantern_cells": _cells_to_json(layout.lanterns),
        "axis_center_x": cx,
        "approach_z_range": [0, layout.plaza[3]],
        # Civic-precinct framing: staged approach (gate + spirit way) and
        # enclosure (colonnade + wall + side gates), recorded as reserved cell
        # sets the realizer dresses and the validator checks.
        "precinct_gate_cells": _cells_to_json(precinct_gate_cells),
        "spirit_way_cells": _cells_to_json(spirit_way_cells),
        "colonnade_cells": _cells_to_json(precinct_colonnade_cells),
        "precinct_wall_cells": _cells_to_json(precinct_wall_cells),
        "precinct_side_gate_cells": _cells_to_json(precinct_side_gate_cells),
    }

    return TownPlan(
        seed=seed,
        site=site,
        perimeter=perimeter,
        wall_cells=wall_cells,
        gates=gates,
        spine=set(layout.spine) & shape_interior,
        lane_cells=(set(layout.lanes) | interior_lanes) & shape_interior,
        parcels=parcels,
        negative_spaces=negative_spaces,
        districts=districts_out,
        alleys=alleys,
        district_brief=[dict(e) for e in district_brief],
        ritual_axis=ritual_axis,
        shape_family=shape_family,
        shape_modifiers=shape_modifiers,
        shape_id=shape_id,
    )


def _reachable(start: Cell2, cells: Set[Cell2]) -> Set[Cell2]:
    q = deque([start])
    seen = {start}
    while q:
        x, z = q.popleft()
        for nxt in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if nxt in cells and nxt not in seen:
                seen.add(nxt)
                q.append(nxt)
    return seen


def _count_closed_loops(perimeter: Set[Cell2]) -> int:
    """Number of 8-connected components in the perimeter cell set.

    A single closed boundary reads as exactly one component. 8-connectivity is
    used (not 4) because a rasterized curve has diagonal pinch points where
    consecutive boundary cells touch only diagonally; those are still a single
    visual loop. A bite fully enclosed by interior (a hole) introduces a second
    inner boundary component, which 8-connectivity still distinguishes from the
    outer loop.
    """
    remaining = set(perimeter)
    loops = 0
    while remaining:
        start = next(iter(remaining))
        comp = _reachable8(start, remaining)
        remaining -= comp
        loops += 1
    return loops


def _reachable8(start: Cell2, cells: Set[Cell2]) -> Set[Cell2]:
    """8-connected (king-move) flood fill."""
    q = deque([start])
    seen = {start}
    while q:
        x, z = q.popleft()
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                nxt = (x + dx, z + dz)
                if nxt in cells and nxt not in seen:
                    seen.add(nxt)
                    q.append(nxt)
    return seen


def _gate_on_straight_run(gate_cells: Iterable[Cell2], perimeter: Set[Cell2]) -> bool:
    """The gate cells SHALL be collinear (a straight horizontal/vertical run)."""
    cells = list(gate_cells)
    if not cells:
        return False
    xs = {c[0] for c in cells}
    zs = {c[1] for c in cells}
    on_straight = len(xs) == 1 or len(zs) == 1
    if not on_straight:
        return False
    # The wall must actually continue the straight line through the gate (no
    # curve pinches within the footprint): at least one perimeter cell abuts the
    # run along the straight axis on each side.
    if len(zs) == 1:
        z = next(iter(zs))
        x_lo, x_hi = min(xs), max(xs)
        has_west = (x_lo - 1, z) in perimeter
        has_east = (x_hi + 1, z) in perimeter
    else:
        x = next(iter(xs))
        z_lo, z_hi = min(zs), max(zs)
        has_west = (x, z_lo - 1) in perimeter
        has_east = (x, z_hi + 1) in perimeter
    return has_west or has_east


def _parcel_border(parcel: TownParcel) -> Set[Cell2]:
    x0, z0, x1, z1 = parcel.bounds
    return _rect(x0, z0, x1, z0) | _rect(x0, z1, x1, z1) | _rect(x0, z0, x0, z1) | _rect(x1, z0, x1, z1)


def _adjacent(cell: Cell2, cells: Set[Cell2]) -> bool:
    x, z = cell
    return any(n in cells for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))


# --- validation -----------------------------------------------------------


def validate_town_plan(plan: TownPlan) -> dict:
    errors: List[str] = []
    protected_rect = _civic_core_aabb(plan.site, plan.seed)
    expected_boundary = _boundary(
        plan.site, plan.shape_family, plan.shape_modifiers, plan.seed,
        protected_rect)
    if plan.perimeter != expected_boundary:
        missing = sorted(expected_boundary - plan.perimeter)[:8]
        extra = sorted(plan.perimeter - expected_boundary)[:8]
        errors.append(f"perimeter_not_boundary: missing={missing} extra={extra}")
    # The perimeter SHALL be exactly one closed loop (spec: town-plan). A bite
    # fully enclosed by interior (a hole) would yield a second loop; the flood
    # fill below catches it.
    loops = _count_closed_loops(plan.perimeter)
    if loops != 1:
        errors.append(f"perimeter_not_single_loop: loops={loops}")
    if not plan.gates:
        errors.append("missing_gate")
    for gate in plan.gates:
        off_wall = [cell for cell in gate.cells if cell not in plan.perimeter]
        if off_wall:
            errors.append(f"gate_off_perimeter: {gate.id}: {off_wall[:8]}")
        # The gate SHALL seat on a straight perimeter run (spec: town-plan).
        if gate.cells and not _gate_on_straight_run(gate.cells, plan.perimeter):
            errors.append(f"gate_not_on_straight_run: {gate.id}")

    # District partition invariants.
    kinds_present = {d.kind for d in plan.districts}
    for required in ("gate", "market", "residential", "civic_core", "fringe"):
        if required not in kinds_present:
            errors.append(f"missing_district_kind: {required}")
    district_overlap: Set[Cell2] = set()
    district_cell_map: Dict[str, Set[Cell2]] = {}
    covered: Set[Cell2] = set()
    for district in plan.districts:
        cells = district.cells
        overlap = covered & cells
        if overlap:
            district_overlap |= overlap
            errors.append(
                f"district_overlap: {district.id}: {sorted(overlap)[:8]}")
        covered |= cells
        district_cell_map[district.id] = cells
    if district_overlap:
        pass  # already reported

    # core-outranks-fringe hierarchy.
    cores = [d for d in plan.districts if d.kind == "civic_core"]
    fringes = [d for d in plan.districts if d.kind == "fringe"]
    if cores:
        core_band_max = max(d.storey_band[1] for d in cores)
        for d in plan.districts:
            if d.kind != "civic_core" and d.storey_band[1] > core_band_max:
                errors.append(
                    f"district_overtops_core: {d.id}: band={d.storey_band} "
                    f"core_max={core_band_max}")
    if cores and fringes:
        core_density = max(d.density for d in cores)
        fringe_density = max(d.density for d in fringes)
        if fringe_density >= core_density:
            errors.append(
                f"fringe_not_loosest: fringe_density={fringe_density} "
                f"core_density={core_density}")

    # Every non-street/non-reserved cell belongs to exactly one district and
    # every parcel sits inside its district.
    streets = plan.street_cells
    for parcel in plan.parcels:
        d_id = parcel.district_id
        if not d_id:
            errors.append(f"parcel_without_district: {parcel.id}")
            continue
        d_cells = district_cell_map.get(d_id, set())
        outside = parcel.cells - d_cells
        if outside:
            errors.append(
                f"parcel_outside_district: {parcel.id}: {sorted(outside)[:8]}")
        if parcel.district_kind and parcel.district_kind != "":
            expected_tier = DISTRICT_IMPORTANCE.get(parcel.district_kind, 0)
            # shrine may be pinned to top tier regardless of base kind band
            if parcel.importance_tier > MAX_IMPORTANCE_TIER:
                errors.append(f"bad_importance_tier: {parcel.id}: {parcel.importance_tier}")
        # storey hint must stay within the district band
        dist = next((d for d in plan.districts if d.id == d_id), None)
        if dist is not None and not (dist.storey_band[0] <= parcel.storey_hint <= dist.storey_band[1]) \
                and not parcel.dominant_landmark:
            errors.append(
                f"parcel_storey_out_of_band: {parcel.id}: "
                f"{parcel.storey_hint} not in {dist.storey_band}")

    # parcels/streets/negatives/alleys disjoint and inside site
    if plan.parcel_cells & streets:
        errors.append(f"parcel_street_overlap: {sorted(plan.parcel_cells & streets)[:8]}")
    if plan.negative_cells & streets:
        errors.append(f"negative_space_street_overlap: {sorted(plan.negative_cells & streets)[:8]}")
    if plan.negative_cells & plan.parcel_cells:
        errors.append(
            f"negative_space_parcel_overlap: {sorted(plan.negative_cells & plan.parcel_cells)[:8]}")
    if plan.alley_cells & plan.parcel_cells:
        errors.append(f"alley_parcel_overlap: {sorted(plan.alley_cells & plan.parcel_cells)[:8]}")
    if plan.alley_cells & streets:
        errors.append(f"alley_street_overlap: {sorted(plan.alley_cells & streets)[:8]}")

    all_plan_cells = plan.perimeter | streets | plan.parcel_cells | plan.negative_cells | plan.alley_cells
    outside_site = sorted(c for c in all_plan_cells if not plan.site.contains(c))
    if outside_site:
        errors.append(f"plan_outside_site: {outside_site[:8]}")

    # spine connectivity to gate and core (spec: grid bounds preserve the
    # spine-to-gate invariant — spine centerline intersects the south gate's
    # straight segment, extends unbroken to the civic core, and no district
    # bound severs it).
    if not plan.spine:
        errors.append("missing_spine")
    else:
        gate_cells = {cell for gate in plan.gates for cell in gate.cells}
        if not (gate_cells & plan.spine):
            errors.append("spine_not_connected_to_gate")
        center_x = _center_x(plan.site, plan.seed)
        centerline = {(center_x, z) for z in range(plan.site.depth)}
        if not (gate_cells & centerline):
            errors.append("spine_centerline_misses_south_gate")
        core_cells: Set[Cell2] = set()
        for d in cores:
            core_cells |= district_cell_map.get(d.id, set())
        if not (plan.spine & core_cells):
            errors.append("spine_not_connected_to_civic_core")
        # The spine SHALL extend unbroken from a gate to the civic core: a
        # 4-connected flood fill of the spine starting at the gate must reach
        # a core cell. A district bound severing the spine fails this.
        spine_start = next(iter(gate_cells & plan.spine), None)
        if spine_start is not None and core_cells:
            reach = _reachable(spine_start, plan.spine)
            if not (reach & core_cells):
                errors.append("spine_severed_from_civic_core")

    # single dominant landmark = shrine, top tier
    landmarks = [p for p in plan.parcels if p.dominant_landmark]
    if len(landmarks) != 1:
        errors.append(f"dominant_landmark_count: {len(landmarks)}")
    elif landmarks[0].importance_tier != MAX_IMPORTANCE_TIER:
        errors.append("dominant_landmark_not_top_tier")
    elif landmarks[0].id != "town_shrine":
        errors.append(f"dominant_landmark_not_town_shrine: {landmarks[0].id}")

    # ritual axis lives inside the civic core
    axis = plan.ritual_axis
    if not axis:
        errors.append("missing_ritual_axis")
    else:
        if axis.get("terminus_parcel") != "town_shrine":
            errors.append(f"ritual_axis_wrong_terminus: {axis.get('terminus_parcel')}")
        shrine = next((p for p in plan.parcels if p.id == "town_shrine"), None)
        plaza_bounds = axis.get("plaza_bounds", [])
        if shrine is None:
            errors.append("ritual_axis_missing_town_shrine_parcel")
        elif len(plaza_bounds) != 4:
            errors.append(f"bad_ritual_plaza_bounds: {plaza_bounds}")
        else:
            plaza_cells = _rect(*plaza_bounds)
            shrine_front = _rect(shrine.bounds[0], shrine.bounds[1] - 1,
                                 shrine.bounds[2], shrine.bounds[1] - 1)
            if not (shrine_front & plaza_cells):
                errors.append("town_shrine_not_fronted_by_plaza")
            if not (plaza_cells & plan.spine):
                errors.append("ritual_plaza_not_on_axis_spine")
            # plaza + paifang must be inside the civic core district
            core_cells = set()
            for d in cores:
                core_cells |= district_cell_map.get(d.id, set())
            if not plaza_cells <= core_cells:
                errors.append("ritual_plaza_outside_civic_core")
            paifang = {tuple(c) for c in axis.get("paifang_gate_cells", [])}
            if not paifang or not paifang <= plan.spine:
                errors.append("paifang_gate_not_on_axis")
            if not paifang <= core_cells:
                errors.append("paifang_outside_civic_core")
            lanterns = {tuple(c) for c in axis.get("lantern_cells", [])}
            if len(lanterns) < 4:
                errors.append("lantern_approach_too_sparse")
            elif not lanterns <= core_cells:
                errors.append("lantern_approach_outside_civic_core")

    # frontage continuity (party-wall rows, no centered-lot gaps)
    errors.extend(_validate_frontage(plan))

    # skyline relief: the civic core must rise above the surrounding roofline.
    errors.extend(_validate_skyline(plan))

    # civic-precinct framing: walled compound, staged approach, enclosure,
    # fringe separation, and baseline-relative emptiness (tasks 3.1-3.3).
    errors.extend(_validate_precinct(plan, cores, district_cell_map))

    if not plan.negative_spaces and not plan.alleys:
        errors.append("missing_negative_space")

    for parcel in plan.parcels:
        if parcel.ground_ref is None:
            errors.append(f"missing_ground_ref: {parcel.id}")

    return {
        "passed": not errors,
        "errors": errors,
        "stats": {
            "parcel_count": len(plan.parcels),
            "district_count": len(plan.districts),
            "gate_count": len(plan.gates),
            "street_cells": len(plan.street_cells),
            "negative_spaces": len(plan.negative_spaces),
            "alleys": len(plan.alleys),
            "dominant_landmarks": len(landmarks),
            "precinct": _precinct_stats(plan),
        },
    }


def _frontage_runs(plan: TownPlan) -> Dict[str, List[TownParcel]]:
    """Group frontage parcels into contiguous runs sharing an edge + district."""
    runs: Dict[str, List[TownParcel]] = {}
    by_key: Dict[str, List[TownParcel]] = {}
    for p in plan.parcels:
        if not p.frontage_edge:
            continue
        # runs are along the perpendicular axis; group by edge + district + the
        # fixed coordinate of the frontage band.
        x0, z0, x1, z1 = p.bounds
        if p.frontage_edge in ("N", "S"):
            key = (p.district_id, p.frontage_edge, z0)  # same z-band
        else:
            key = (p.district_id, p.frontage_edge, x0)  # same x-band
        by_key.setdefault(key, []).append(p)
    for key, group in by_key.items():
        group.sort(key=lambda q: (q.bounds[0], q.bounds[1]))
        runs[f"{key[1]}:{key[0]}:{key[2]}"] = group
    return runs


def _validate_skyline(plan: TownPlan) -> List[str]:
    """Civic core skyline relief: >= N above-threshold volumes, >= 1 a tall archetype."""
    errors: List[str] = []
    core_parcels = [p for p in plan.parcels if p.district_kind == "civic_core"]
    if not core_parcels:
        return errors  # missing district is reported elsewhere
    tall = [p for p in core_parcels if p.storey_hint >= SKYLINE_TALL_STOREY]
    if len(tall) < SKYLINE_MIN_TALL_VOLUMES:
        errors.append(
            f"skyline_too_flat: tall_volumes={len(tall)} "
            f"< minimum {SKYLINE_MIN_TALL_VOLUMES}")
    landmark_templates = sorted(
        {p.template_id for p in core_parcels
         if is_vertical_landmark_template(p.template_id)})
    if not landmark_templates:
        errors.append("skyline_no_vertical_landmark")
    return errors


def _precinct_sets(plan: TownPlan) -> Dict[str, Set[Cell2]]:
    """Return the civic-precinct reserved cell sets recorded in ritual_axis."""
    axis = plan.ritual_axis or {}
    keys = ("precinct_gate_cells", "spirit_way_cells", "colonnade_cells",
            "precinct_wall_cells", "precinct_side_gate_cells")
    return {k: {tuple(c) for c in axis.get(k, [])} for k in keys}


def _precinct_stats(plan: TownPlan) -> Dict[str, int]:
    sets = _precinct_sets(plan)
    side_halls = [p for p in plan.parcels if p.id.startswith("civic_side_hall")]
    return {
        "wall_cells": len(sets["precinct_wall_cells"]),
        "colonnade_cells": len(sets["colonnade_cells"]),
        "spirit_way_cells": len(sets["spirit_way_cells"]),
        "gate_cells": len(sets["precinct_gate_cells"]),
        "side_gate_cells": len(sets["precinct_side_gate_cells"]),
        "side_hall_parcels": len(side_halls),
    }


def _validate_precinct(
    plan: TownPlan,
    cores: List[TownDistrict],
    district_cell_map: Dict[str, Set[Cell2]],
) -> List[str]:
    """Civic-precinct framing invariants (spec: civic-precinct-framing).

    - wall on the gate-facing + lateral core edges, with a spine-axis gate;
    - a non-empty spirit-way approach the plaza is reachable through;
    - baseline-relative emptiness (core void shrinks vs. unframed);
    - fringe separation (wall on shared core<->fringe edges, fields outside).
    """
    errors: List[str] = []
    if not cores:
        return errors  # missing core district is reported elsewhere
    core = cores[0]
    cx0, cz0, cx1, cz1 = core.bounds
    core_cells = district_cell_map.get(core.id, set())
    sets = _precinct_sets(plan)
    wall = sets["precinct_wall_cells"]
    gate = sets["precinct_gate_cells"]
    spirit = sets["spirit_way_cells"]
    colonnade = sets["colonnade_cells"]
    side_gate = sets["precinct_side_gate_cells"]

    if not wall:
        errors.append("precinct_wall_missing")
        return errors

    # --- 3.1 Spine-axis precinct gate at the core's gate-facing edge --------
    if not gate:
        errors.append("precinct_gate_missing")
    else:
        if not gate <= plan.spine:
            errors.append("precinct_gate_not_on_spine")
        if not gate <= core_cells:
            errors.append("precinct_gate_outside_civic_core")
        gate_zs = {z for _, z in gate}
        if len(gate_zs) != 1 or cz0 not in gate_zs:
            errors.append("precinct_gate_not_on_gate_facing_edge")

    # --- 3.1 Wall on the gate-facing (south) + lateral (west/east) edges ----
    south_edge = _rect(cx0, cz0, cx1, cz0)
    west_edge = _rect(cx0, cz0, cx0, cz1)
    east_edge = _rect(cx1, cz0, cx1, cz1)
    for name, edge in (("gate_facing", south_edge),
                       ("lateral_west", west_edge),
                       ("lateral_east", east_edge)):
        if not (wall & edge):
            errors.append(f"precinct_wall_missing_on_edge: {name}")
    # wall must sit on the core boundary, disjoint from parcels/streets (the
    # gate openings are spine, not wall).
    boundary = south_edge | west_edge | east_edge
    off_boundary = wall - boundary
    if off_boundary:
        errors.append(f"precinct_wall_off_boundary: {sorted(off_boundary)[:8]}")
    if wall & plan.parcel_cells:
        errors.append(
            f"precinct_wall_overlaps_parcel: {sorted(wall & plan.parcel_cells)[:8]}")
    if wall & plan.street_cells:
        errors.append(
            f"precinct_wall_overlaps_street: {sorted(wall & plan.street_cells)[:8]}")

    # --- 3.1 Non-empty spirit-way band, inside the core, off the spine ------
    if not spirit:
        errors.append("precinct_spirit_way_empty")
    else:
        if not spirit <= core_cells:
            errors.append("precinct_spirit_way_outside_civic_core")
        if spirit & plan.spine:
            errors.append("precinct_spirit_way_overlaps_spine")

    # --- 3.1 Plaza reachable through the precinct gate (spine continuity) ---
    plaza_bounds = (plan.ritual_axis or {}).get("plaza_bounds", [])
    if gate and len(plaza_bounds) == 4 and plan.spine:
        plaza_cells = _rect(*plaza_bounds)
        start = next(iter(gate & plan.spine), None) or next(iter(plan.spine))
        reach = _reachable(start, plan.spine)
        if not (reach & plaza_cells):
            errors.append("precinct_plaza_not_reachable_through_gate")

    # --- 3.2 Baseline-relative emptiness -----------------------------------
    # Precinct elements occupy only previously-empty cells (verified disjoint
    # from pre-existing structures), so the unframed baseline is recovered by
    # dropping the precinct-introduced side halls and the reserved wall /
    # colonnade / spirit-way / gate cells from the occupied set.
    occupied = (plan.parcel_cells | plan.negative_cells
                | plan.alley_cells | plan.street_cells)
    precinct_reserved = (wall | colonnade | spirit | gate | side_gate) & core_cells
    side_hall_cells: Set[Cell2] = set()
    for p in plan.parcels:
        if p.id.startswith("civic_side_hall"):
            side_hall_cells |= p.cells
    current_empty = core_cells - (occupied | precinct_reserved)
    baseline_empty = core_cells - (occupied - side_hall_cells)
    if not (len(current_empty) < len(baseline_empty)):
        errors.append(
            f"precinct_emptiness_not_reduced: current={len(current_empty)} "
            f"baseline={len(baseline_empty)}")

    # --- 3.3 Fringe separation ---------------------------------------------
    fringes = [d for d in plan.districts if d.kind == "fringe"]
    fringe_cells: Set[Cell2] = set()
    for fd in fringes:
        fringe_cells |= district_cell_map.get(fd.id, set())
    shared_west = {(cx0, z) for z in range(cz0, cz1 + 1)
                   if (cx0 - 1, z) in fringe_cells}
    shared_east = {(cx1, z) for z in range(cz0, cz1 + 1)
                   if (cx1 + 1, z) in fringe_cells}
    for name, shared in (("fringe_west", shared_west), ("fringe_east", shared_east)):
        if shared and not (wall & shared):
            errors.append(f"precinct_wall_missing_on_fringe_edge: {name}")
    spirit_field_cells: Set[Cell2] = set()
    for neg in plan.negative_spaces:
        if neg.kind == "spirit_field":
            spirit_field_cells |= neg.cells
    inside_precinct = spirit_field_cells & core_cells
    if inside_precinct:
        errors.append(
            f"precinct_spirit_field_inside: {sorted(inside_precinct)[:8]}")

    return errors


def _validate_frontage(plan: TownPlan) -> List[str]:
    errors: List[str] = []
    runs = _frontage_runs(plan)
    alley_cells = plan.alley_cells
    frontage_districts = {d.kind for d in plan.districts if d.kind in ("market", "residential")}
    saw_long_run = False
    for run_id, group in runs.items():
        group.sort(key=lambda q: (q.bounds[0], q.bounds[1]))
        contiguous_chain = 1
        for a, b in zip(group, group[1:]):
            if _parcels_share_gable(a, b):
                contiguous_chain += 1
                if contiguous_chain >= 3:
                    saw_long_run = True
                continue
            # run break is allowed only if a typed alley fills the gap
            gap = _gap_cells(a, b)
            if gap and gap <= alley_cells:
                contiguous_chain = 1  # alley breaks the run; restart chain
                continue
            errors.append(f"frontage_gap_in_run: {run_id}: {a.id}->{b.id}")
        if contiguous_chain >= 3:
            saw_long_run = True
        for p in group:
            if not p.frontage_edge:
                errors.append(f"frontage_parcel_missing_edge: {p.id}")
    # Sparsity invariant: a frontage district must present at least one
    # continuous party-wall row (>=3 parcels) rather than centered-lot gaps.
    if frontage_districts and not saw_long_run and runs:
        errors.append("frontage_too_sparse: no party-wall run of >=3 parcels")
    return errors


def _gap_cells(a: TownParcel, b: TownParcel) -> Set[Cell2]:
    """Cells strictly between two same-band parcels along the run axis."""
    ax0, az0, ax1, az1 = a.bounds
    bx0, bz0, bx1, bz1 = b.bounds
    if ax0 == bx0 and ax1 == bx1:
        # stacked along z (E/W frontage run varies in z)
        return _rect(ax0, min(az1, bz1) + 1, ax1, max(az0, bz0) - 1)
    if az0 == bz0 and az1 == bz1:
        # row along x (N/S frontage run varies in x)
        return _rect(min(ax1, bx1) + 1, az0, max(ax0, bx0) - 1, az1)
    return set()


def _parcels_share_gable(a: TownParcel, b: TownParcel) -> bool:
    ax0, az0, ax1, az1 = a.bounds
    bx0, bz0, bx1, bz1 = b.bounds
    # share a vertical gable wall: a.x1+1 == b.x0 (or vice versa) with z overlap
    if (ax1 + 1 == bx0 or bx1 + 1 == ax0):
        return not (az1 < bz0 or bz1 < az0)
    # share a horizontal party line: a.z1+1 == b.z0 with x overlap
    if (az1 + 1 == bz0 or bz1 + 1 == az0):
        return not (ax1 < bx0 or bx1 < ax0)
    return False


def validate_realized_town(plan: TownPlan) -> dict:
    """Validate structural invariants after parcel/street realization."""
    errors: List[str] = []
    plan_report = validate_town_plan(plan)
    errors.extend(plan_report["errors"])
    streets = plan.street_cells
    if streets and plan.spine:
        reachable = _reachable(next(iter(plan.spine)), streets)
        for parcel in plan.parcels:
            border = _parcel_border(parcel)
            if not any(_adjacent(cell, reachable) for cell in border):
                errors.append(f"parcel_not_reachable_from_spine: {parcel.id}")
    for parcel in plan.parcels:
        if parcel.cells & streets:
            errors.append(f"building_footprint_overlaps_street: {parcel.id}")
    for gate in plan.gates:
        if not set(gate.cells) <= plan.perimeter:
            errors.append(f"gate_not_on_wall: {gate.id}")
    # alleys must carry no building plinth: they are disjoint from parcels by
    # construction; here we also assert each alley stays narrower than the
    # module of the frontage run that produced it so it cannot hold a building.
    for alley in plan.alleys:
        if alley.kind != "alley":
            continue
        ax0, az0, ax1, az1 = alley.bounds
        narrow = min(ax1 - ax0 + 1, az1 - az0 + 1)
        if alley.archetype:
            module = _template_size(alley.archetype)[0]
        else:
            module = _district_module(alley.district_kind)
        if narrow >= module:
            errors.append(
                f"alley_too_wide: {alley.id}: narrow={narrow} module={module}")
        if alley.cells & plan.parcel_cells:
            errors.append(f"alley_overlaps_parcel: {alley.id}")
    return {"passed": not errors, "errors": errors, "plan": plan_report}


def estimate_block_budget(plan: TownPlan) -> dict:
    wall_blocks = len(plan.wall_cells) * 5
    road_blocks = len(plan.street_cells)
    parcel_blocks = sum(
        len(parcel.cells) * (5 + max(0, parcel.importance_tier) * 2 + parcel.storey_hint)
        for parcel in plan.parcels)
    prop_blocks = len(plan.negative_cells) // 2 + len(plan.parcel_cells) // 4 + len(plan.parcels) * 8
    # Civic-precinct reserved structures: the precinct wall (low block line,
    # like the perimeter wall), the colonnade (covered walk: posts + roof), and
    # spirit-way guardian/stele props. The side gates are openings (no blocks).
    precinct = _precinct_sets(plan)
    precinct_wall_blocks = len(precinct["precinct_wall_cells"]) * 5
    colonnade_blocks = len(precinct["colonnade_cells"]) * 3
    spirit_way_blocks = (len(precinct["spirit_way_cells"])
                         + len(precinct["precinct_gate_cells"])) * 4
    precinct_blocks = precinct_wall_blocks + colonnade_blocks + spirit_way_blocks
    total = wall_blocks + road_blocks + parcel_blocks + prop_blocks + precinct_blocks
    within = plan.site.width <= MAX_FOOTPRINT_AXIS and plan.site.depth <= MAX_FOOTPRINT_AXIS
    return {
        "wall_blocks": wall_blocks,
        "road_blocks": road_blocks,
        "parcel_budget": parcel_blocks,
        "prop_budget": prop_blocks,
        "precinct_blocks": precinct_blocks,
        "total_budget": total,
        "bounded": within and total <= BLOCK_BUDGET_CEILING,
        "ceiling": BLOCK_BUDGET_CEILING,
    }


def write_plan_json(plan: TownPlan, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(plan.to_dict(), f, indent=2, ensure_ascii=False)
