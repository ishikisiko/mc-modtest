"""Deterministic terraced axial sect-compound plan and validators.

The runtime Java sect command (``SectGenerator.java``) mirrors this planner
structurally, with no shared RNG: both derive every cell from ``seed`` xor cell
coordinates so the same seed + site yields the same terrace stack, axis, slots,
links, and feature selection. The Python version exists for offline validation,
JSON dumps, top-down previews, and to export the terrace profile that
``add-sect-worldgen`` consumes to derive the mountain (反推山形).

Coordinate convention (shared with the Java realizer):
- ``x`` is the cross-slope (left/right) axis; ``z`` is the fall-line (foot→summit).
- The mountain gate sits at the lowest ``z`` (foot/south); the principal hall
  backs the cliff at the highest ``z`` (summit/north).
- Terrace ``i`` occupies a contiguous z-band; its platform elevation is
  ``site.base_y + i * terrace_rise``. Terrace width (x-extent) is non-increasing
  from foot to summit per the taper parameter.
- An on-axis stair band of ``axis_stair_w`` cells wide and ``terrace_rise`` cells
  deep climbs the fall-line between adjacent terrace platforms.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

Cell2 = Tuple[int, int]
Rect = Tuple[int, int, int, int]  # x0, z0, x1, z1 (inclusive)

# --- scale constants -------------------------------------------------------
DEFAULT_TERRACE_COUNT = 5
MIN_TERRACE_COUNT = 4
MAX_TERRACE_COUNT = 6
DEFAULT_TERRACE_RISE = 8
DEFAULT_TERRACE_DEPTH = 28
DEFAULT_TERRACE_WIDTH = 58          # foot-terrace x-extent (fits gate + flanks)
DEFAULT_SUMMIT_TAPER = 4            # summit narrows by this many blocks (x)
DEFAULT_AXIS_STAIR_W = 5
DEFAULT_CLIFF_BACK_HEIGHT = 12
Z_MARGIN = 4                        # margin before gate / behind summit cliff

# Default site dimensions (must hold the default-parameter footprint).
DEFAULT_SITE_WIDTH = DEFAULT_TERRACE_WIDTH + 6
DEFAULT_SITE_DEPTH = (2 * Z_MARGIN + DEFAULT_TERRACE_COUNT * DEFAULT_TERRACE_DEPTH
                      + (DEFAULT_TERRACE_COUNT - 1) * DEFAULT_TERRACE_RISE)

MAX_IMPORTANCE_TIER = 3

# Default ordered skeleton; non-5 counts insert/drop a middle tier so the gate
# stays at the foot and the principal hall stays at the summit.
SKELETON_NAMES_5: Tuple[str, ...] = ("gate", "disciple", "assembly", "scripture", "summit")

# Importance tier assigned to each archetype (non-decreasing up the stack).
ARCHETYPE_IMPORTANCE: Dict[str, int] = {
    "sect_gate": 0,
    "bell_drum_tower": 1,
    "disciple_quarters": 1,
    "alchemy_room": 1,
    "scripture_pavilion": 2,
    "pavilion": 2,
    "pagoda": 3,                    # co-dominant skyline landmark with the hall
    "sect_main_hall": 3,
}

# Shipped .nbt variants for each abstract archetype (mirrors the Java realizer).
TEMPLATE_VARIANTS: Dict[str, Tuple[str, ...]] = {
    "sect_gate": ("sect_gate_001", "sect_gate_002"),
    "sect_main_hall": ("sect_main_hall_001", "sect_main_hall_002"),
    "scripture_pavilion": ("scripture_pavilion_001", "scripture_pavilion_002"),
    "alchemy_room": ("alchemy_room_001", "alchemy_room_002"),
    "disciple_quarters": ("disciple_quarters_001", "disciple_quarters_002"),
    "pagoda": ("pagoda_001", "pagoda_002", "pagoda_003"),
    "pavilion": ("pavilion_001", "pavilion_002", "pavilion_003"),
    "bell_drum_tower": ("bell_drum_tower_001", "bell_drum_tower_002", "bell_drum_tower_003"),
}

# Template footprints (x_width, z_depth) of the shipped .nbt pieces. MUST stay
# in sync with the Java realizer's templateFootprint() and with the regenerated
# .nbt sizes (re-measure with tools/buildgen/nbtread when templates change).
TEMPLATE_FOOTPRINT: Dict[str, Tuple[int, int]] = {
    "sect_gate": (21, 16),
    "sect_gate_001": (21, 16),
    "sect_gate_002": (21, 16),
    "sect_main_hall": (27, 25),
    "sect_main_hall_001": (27, 25),
    "sect_main_hall_002": (25, 25),
    "scripture_pavilion": (17, 19),
    "scripture_pavilion_001": (17, 19),
    "scripture_pavilion_002": (17, 19),
    "alchemy_room": (19, 17),
    "alchemy_room_001": (19, 17),
    "alchemy_room_002": (19, 17),
    "disciple_quarters": (21, 18),
    "disciple_quarters_001": (21, 18),
    "disciple_quarters_002": (21, 18),
    "pagoda": (17, 19),
    "pagoda_001": (17, 19),
    "pagoda_002": (19, 21),
    "pagoda_003": (19, 21),
    "pavilion": (23, 21),
    "pavilion_001": (23, 21),
    "pavilion_002": (21, 21),
    "pavilion_003": (23, 21),
    "bell_drum_tower": (17, 19),
    "bell_drum_tower_001": (17, 19),
    "bell_drum_tower_002": (17, 21),
    "bell_drum_tower_003": (17, 19),
}


def template_footprint(template_id: str) -> Tuple[int, int]:
    return TEMPLATE_FOOTPRINT.get(template_id, (15, 15))


def max_archetype_footprint(archetype: str) -> Tuple[int, int]:
    """Largest (x, z) footprint across an archetype's shipped variants.

    Slot bounds are sized to this so any per-seed variant selection fits the slot
    (e.g. bell_drum_tower_002 is deeper than _001). The Java realizer mirrors it.
    """
    variants = TEMPLATE_VARIANTS.get(archetype, (archetype,))
    widths = [template_footprint(v)[0] for v in variants]
    depths = [template_footprint(v)[1] for v in variants]
    return (max(widths), max(depths))


def skeleton_names(count: int) -> Tuple[str, ...]:
    """Resolve the ordered terrace name stack for a given terrace count."""
    if count < MIN_TERRACE_COUNT or count > MAX_TERRACE_COUNT:
        raise ValueError(f"terrace_count must be in {MIN_TERRACE_COUNT}..{MAX_TERRACE_COUNT}, got {count}")
    if count == 5:
        return SKELETON_NAMES_5
    if count == 4:
        # drop the assembly tier
        return ("gate", "disciple", "scripture", "summit")
    # count == 6: add a second (inner) disciple tier
    return ("gate", "disciple", "disciple", "assembly", "scripture", "summit")


def archetype_importance(archetype: str) -> int:
    return ARCHETYPE_IMPORTANCE.get(archetype, 0)


# --- geometry helpers ------------------------------------------------------


def _rect(x0: int, z0: int, x1: int, z1: int) -> List[Cell2]:
    if x1 < x0 or z1 < z0:
        return []
    return [(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]


def _rect_set(x0: int, z0: int, x1: int, z1: int) -> set:
    return set(_rect(x0, z0, x1, z1))


def _cells_to_json(cells: Iterable[Cell2]) -> List[List[int]]:
    return [[x, z] for x, z in sorted(cells)]


# --- plan dataclasses ------------------------------------------------------


@dataclass(frozen=True)
class SectSite:
    width: int = DEFAULT_SITE_WIDTH            # cross-slope extent
    depth: int = DEFAULT_SITE_DEPTH            # fall-line extent
    base_y: int = 64
    max_slope: int = 8                          # sects tolerate more relief than towns

    def contains(self, cell: Cell2) -> bool:
        x, z = cell
        return 0 <= x < self.width and 0 <= z < self.depth


@dataclass(frozen=True)
class SectGeometry:
    terrace_count: int
    terrace_rise: int
    terrace_depth: int
    terrace_width: int
    summit_taper: int
    axis_stair_w: int
    cliff_back_height: int


@dataclass(frozen=True)
class Terrace:
    index: int
    name: str
    elevation: int
    bounds: Rect
    width: int
    depth: int
    cliff_back: bool = False


@dataclass(frozen=True)
class Slot:
    id: str
    terrace_index: int
    terrace_name: str
    role: str                    # on_axis / flank_left / flank_right
    archetype: str
    template_id: str
    importance_tier: int
    bounds: Rect
    against_cliff_back: bool = False

    @property
    def center(self) -> Cell2:
        x0, z0, x1, z1 = self.bounds
        return ((x0 + x1) // 2, (z0 + z1) // 2)


@dataclass(frozen=True)
class GalleryLink:
    id: str
    kind: str                         # "covered_gallery" | "flying_bridge"
    from_slot: str
    to_slot: str
    from_cell: Cell2
    to_cell: Cell2
    terrace_indices: Tuple[int, ...]


@dataclass(frozen=True)
class RetainingFace:
    id: str
    lower_terrace: int
    upper_terrace: int
    bounds: Rect
    height: int


@dataclass(frozen=True)
class AxisStair:
    id: str
    lower_terrace: int
    upper_terrace: int
    bounds: Rect


@dataclass(frozen=True)
class FlyingBridgeFeature:
    variant: str                     # one of FEATURE_VARIANTS keys
    detached_archetype: str
    detached_template: str
    detached_slot_id: str
    detached_bounds: Rect
    spire_offset: Cell2              # detached offset relative to main compound
    bearing: str                     # N / NE / E / SE / S / SW / W / NW
    bridge_span: int
    bridge_shape: str                # straight / arched / angled
    bridge_link: GalleryLink


# Detached-spire flying-bridge feature variants. Each pair MUST differ on >=2 of
# (detached volume, bridge span/shape, spire offset/bearing) per the layout spec.
FEATURE_VARIANTS: Dict[str, Dict[str, object]] = {
    "pavilion_short_straight_east": {
        "detached_archetype": "pavilion",
        "spire_offset": (12, 0),
        "bearing": "E",
        "bridge_span": 6,
        "bridge_shape": "straight",
    },
    "pagoda_long_arched_west": {
        "detached_archetype": "pagoda",
        "spire_offset": (-14, -2),
        "bearing": "W",
        "bridge_span": 10,
        "bridge_shape": "arched",
    },
    "disciple_medium_angled_north": {
        "detached_archetype": "disciple_quarters",
        "spire_offset": (4, 14),
        "bearing": "N",
        "bridge_span": 8,
        "bridge_shape": "angled",
    },
}

# Per-seed absence probability: one in FEATURE_PERIOD seeds yields no feature.
FEATURE_PERIOD = 4


@dataclass
class SectPlan:
    seed: int
    site: SectSite
    geometry: SectGeometry
    terraces: List[Terrace]
    axis_cells: List[Cell2]
    slots: List[Slot]
    gallery_links: List[GalleryLink]
    retaining_faces: List[RetainingFace]
    axis_stairs: List[AxisStair]
    feature: Optional[FlyingBridgeFeature]
    # Explicit terrace-profile export for add-sect-worldgen (反推山形 contract).
    terrace_profile: Dict[str, object] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "seed": self.seed,
            "site": {
                "width": self.site.width,
                "depth": self.site.depth,
                "base_y": self.site.base_y,
                "max_slope": self.site.max_slope,
            },
            "geometry": {
                "terrace_count": self.geometry.terrace_count,
                "terrace_rise": self.geometry.terrace_rise,
                "terrace_depth": self.geometry.terrace_depth,
                "terrace_width": self.geometry.terrace_width,
                "summit_taper": self.geometry.summit_taper,
                "axis_stair_w": self.geometry.axis_stair_w,
                "cliff_back_height": self.geometry.cliff_back_height,
            },
            "terraces": [
                {
                    "index": t.index,
                    "name": t.name,
                    "elevation": t.elevation,
                    "bounds": list(t.bounds),
                    "width": t.width,
                    "depth": t.depth,
                    "cliff_back": t.cliff_back,
                }
                for t in self.terraces
            ],
            "axis_cells": _cells_to_json(self.axis_cells),
            "slots": [
                {
                    "id": s.id,
                    "terrace_index": s.terrace_index,
                    "terrace_name": s.terrace_name,
                    "role": s.role,
                    "archetype": s.archetype,
                    "template_id": s.template_id,
                    "importance_tier": s.importance_tier,
                    "bounds": list(s.bounds),
                    "against_cliff_back": s.against_cliff_back,
                }
                for s in self.slots
            ],
            "gallery_links": [
                {
                    "id": g.id,
                    "kind": g.kind,
                    "from_slot": g.from_slot,
                    "to_slot": g.to_slot,
                    "from_cell": list(g.from_cell),
                    "to_cell": list(g.to_cell),
                    "terrace_indices": list(g.terrace_indices),
                }
                for g in self.gallery_links
            ],
            "retaining_faces": [
                {
                    "id": r.id,
                    "lower_terrace": r.lower_terrace,
                    "upper_terrace": r.upper_terrace,
                    "bounds": list(r.bounds),
                    "height": r.height,
                }
                for r in self.retaining_faces
            ],
            "axis_stairs": [
                {
                    "id": a.id,
                    "lower_terrace": a.lower_terrace,
                    "upper_terrace": a.upper_terrace,
                    "bounds": list(a.bounds),
                }
                for a in self.axis_stairs
            ],
            "feature": _feature_to_dict(self.feature),
            "terrace_profile": self.terrace_profile,
        }


def _feature_to_dict(feature: Optional[FlyingBridgeFeature]) -> Optional[dict]:
    if feature is None:
        return None
    return {
        "variant": feature.variant,
        "detached_archetype": feature.detached_archetype,
        "detached_template": feature.detached_template,
        "detached_slot_id": feature.detached_slot_id,
        "detached_bounds": list(feature.detached_bounds),
        "spire_offset": list(feature.spire_offset),
        "bearing": feature.bearing,
        "bridge_span": feature.bridge_span,
        "bridge_shape": feature.bridge_shape,
        "bridge_link": {
            "id": feature.bridge_link.id,
            "kind": feature.bridge_link.kind,
            "from_slot": feature.bridge_link.from_slot,
            "to_slot": feature.bridge_link.to_slot,
            "from_cell": list(feature.bridge_link.from_cell),
            "to_cell": list(feature.bridge_link.to_cell),
            "terrace_indices": list(feature.bridge_link.terrace_indices),
        },
    }


# --- slot roster per terrace name -----------------------------------------


@dataclass(frozen=True)
class _SlotSpec:
    archetype: str
    role: str           # on_axis / flank_left / flank_right
    align: str          # front / center / back


def _slot_roster(terrace_name: str) -> Tuple[_SlotSpec, ...]:
    """Volume roster placed on a terrace of ``terrace_name``.

    Building-piece selection is driven by terrace level (not random), per the
    layout spec. Each terrace carries either a single on-axis volume, a mirrored
    flank pair, or both.
    """
    if terrace_name == "gate":
        return (
            _SlotSpec("sect_gate", "on_axis", "front"),
            _SlotSpec("bell_drum_tower", "flank_left", "back"),
            _SlotSpec("bell_drum_tower", "flank_right", "back"),
        )
    if terrace_name == "disciple":
        return (
            _SlotSpec("disciple_quarters", "flank_left", "center"),
            _SlotSpec("disciple_quarters", "flank_right", "center"),
        )
    if terrace_name == "assembly":
        return (
            _SlotSpec("alchemy_room", "flank_left", "center"),
            _SlotSpec("alchemy_room", "flank_right", "center"),
        )
    if terrace_name == "scripture":
        return (
            _SlotSpec("scripture_pavilion", "on_axis", "center"),
            _SlotSpec("pagoda", "flank_left", "back"),
            _SlotSpec("pagoda", "flank_right", "back"),
        )
    if terrace_name == "summit":
        # Principal hall pinned on-axis against the cliff-back edge.
        return (_SlotSpec("sect_main_hall", "on_axis", "back"),)
    return (_SlotSpec("disciple_quarters", "flank_left", "center"),
            _SlotSpec("disciple_quarters", "flank_right", "center"))


def _template_for(archetype: str, seed: int, terrace_index: int) -> str:
    """Deterministic variant pick: seed xor terrace index (no shared RNG with Java).

    Java mirrors this exact expression.
    """
    variants = TEMPLATE_VARIANTS.get(archetype, (archetype,))
    idx = (seed ^ (terrace_index * 341873128712)) % len(variants)
    if idx < 0:
        idx += len(variants)
    return variants[idx]


def _z_span(terrace_depth: int, td: int, align: str, z0: int, z1: int) -> Tuple[int, int]:
    if align == "front":
        sz0 = z0
    elif align == "back":
        sz0 = z1 - td + 1
    else:  # center
        sz0 = z0 + max(0, (terrace_depth - td) // 2)
    return sz0, sz0 + td - 1


def _slot_bounds(
    terrace: Terrace,
    spec: _SlotSpec,
    axis_half: int,
    on_axis_span: Optional[Tuple[int, int]],
) -> Rect:
    """Compute a slot's footprint bounds on its terrace.

    ``on_axis_span`` is the (x0, x1) of the terrace's on-axis volume when one is
    present, so flanks clear it (footprints must not overlap). When the terrace
    has no on-axis volume, flanks clear the axis-stair column instead.
    """
    x0, z0, x1, z1 = terrace.bounds
    cx = (x0 + x1) // 2
    tw, td = max_archetype_footprint(spec.archetype)
    tw = min(tw, x1 - x0 + 1)
    td = min(td, z1 - z0 + 1)

    if spec.role == "on_axis":
        sx0 = cx - tw // 2
        sx1 = sx0 + tw - 1
    else:
        # flank: sit outside the on-axis volume (or the axis stair), mirrored.
        if spec.role == "flank_left":
            inner = (on_axis_span[0] if on_axis_span else cx - axis_half) - 1
            sx1 = inner
            sx0 = sx1 - tw + 1
        else:  # flank_right
            inner = (on_axis_span[1] if on_axis_span else cx + axis_half) + 1
            sx0 = inner
            sx1 = sx0 + tw - 1
        sx0 = max(sx0, x0)
        sx1 = min(sx1, x1)

    sz0, sz1 = _z_span(terrace.depth, td, spec.align, z0, z1)
    return (sx0, sz0, sx1, sz1)


def _on_axis_x_span(terrace: Terrace, archetype: str) -> Tuple[int, int]:
    """The (x0, x1) an on-axis volume of ``archetype`` would occupy, centered."""
    x0, _, x1, _ = terrace.bounds
    cx = (x0 + x1) // 2
    tw, _ = max_archetype_footprint(archetype)
    tw = min(tw, x1 - x0 + 1)
    sx0 = cx - tw // 2
    return (sx0, sx0 + tw - 1)


def _base_archetype_template(archetype: str) -> str:
    """First shipped variant id of an archetype (for footprint sizing)."""
    variants = TEMPLATE_VARIANTS.get(archetype, (archetype,))
    return variants[0]


# --- plan assembly --------------------------------------------------------


def _feature_choice(seed: int) -> Optional[str]:
    """Per-seed feature variant selection, or None when absent.

    Deterministic; Java mirrors ``Math.floorMod(seed, FEATURE_PERIOD)``.
    """
    roll = seed % FEATURE_PERIOD
    if roll < 0:
        roll += FEATURE_PERIOD
    if roll == FEATURE_PERIOD - 1:
        return None
    names = tuple(FEATURE_VARIANTS.keys())
    return names[roll % len(names)]


def generate_sect_plan(
    seed: int,
    site: Optional[SectSite] = None,
    params: Optional[Mapping[str, int]] = None,
) -> SectPlan:
    p = dict(params or {})
    count = int(p.get("terrace_count", DEFAULT_TERRACE_COUNT))
    geometry = SectGeometry(
        terrace_count=count,
        terrace_rise=int(p.get("terrace_rise", DEFAULT_TERRACE_RISE)),
        terrace_depth=int(p.get("terrace_depth", DEFAULT_TERRACE_DEPTH)),
        terrace_width=int(p.get("terrace_width", DEFAULT_TERRACE_WIDTH)),
        summit_taper=int(p.get("summit_taper", DEFAULT_SUMMIT_TAPER)),
        axis_stair_w=int(p.get("axis_stair_w", DEFAULT_AXIS_STAIR_W)),
        cliff_back_height=int(p.get("cliff_back_height", DEFAULT_CLIFF_BACK_HEIGHT)),
    )
    names = skeleton_names(geometry.terrace_count)
    axis_half = geometry.axis_stair_w // 2

    # footprint depth along the fall-line
    footprint_depth = 2 * Z_MARGIN + geometry.terrace_count * geometry.terrace_depth \
        + (geometry.terrace_count - 1) * geometry.terrace_rise
    # default site auto-fits the requested params; an explicit site must fit too.
    if site is None:
        site = SectSite(width=geometry.terrace_width + 6, depth=footprint_depth)
    if site.depth < footprint_depth or site.width < geometry.terrace_width:
        raise ValueError(
            f"site {site.width}x{site.depth} too small for sect footprint "
            f"{geometry.terrace_width}x{footprint_depth}")

    # center the compound cross-slope
    x_anchor = (site.width - geometry.terrace_width) // 2

    terraces: List[Terrace] = []
    z = Z_MARGIN
    for i in range(geometry.terrace_count):
        # floor division so the Java realizer mirrors width exactly via
        # Math.floorDiv(summit_taper * i, count - 1).
        width = geometry.terrace_width - (geometry.summit_taper * i) // (geometry.terrace_count - 1)
        x0 = x_anchor + (geometry.terrace_width - width) // 2
        x1 = x0 + width - 1
        z0 = z
        z1 = z + geometry.terrace_depth - 1
        elevation = site.base_y + i * geometry.terrace_rise
        terraces.append(Terrace(
            index=i, name=names[i], elevation=elevation,
            bounds=(x0, z0, x1, z1), width=width, depth=geometry.terrace_depth,
            cliff_back=(i == geometry.terrace_count - 1)))
        z = z1 + 1 + geometry.terrace_rise   # platform + stair band to next terrace

    # slots (on-axis placed first so flanks can clear its x-extent)
    slots: List[Slot] = []
    for terrace in terraces:
        specs = _slot_roster(terrace.name)
        on_axis_spec = next((s for s in specs if s.role == "on_axis"), None)
        on_axis_span: Optional[Tuple[int, int]] = None
        if on_axis_spec is not None:
            on_axis_span = _on_axis_x_span(terrace, on_axis_spec.archetype)
        for spec in specs:
            bounds = _slot_bounds(terrace, spec, axis_half, on_axis_span)
            template = _template_for(spec.archetype, seed, terrace.index)
            slots.append(Slot(
                id=f"slot_{terrace.name}_{spec.role}_{terrace.index}",
                terrace_index=terrace.index,
                terrace_name=terrace.name,
                role=spec.role,
                archetype=spec.archetype,
                template_id=template,
                importance_tier=archetype_importance(spec.archetype),
                bounds=bounds,
                against_cliff_back=(terrace.name == "summit" and spec.role == "on_axis"),
            ))

    # ritual axis: cross-slope centerline of width axis_stair_w, foot→summit
    cx = (terraces[0].bounds[0] + terraces[0].bounds[2]) // 2
    axis_x0 = cx - axis_half
    axis_x1 = cx + axis_half
    axis_z0 = terraces[0].bounds[1]
    axis_z1 = terraces[-1].bounds[3]
    axis_cells = _rect(axis_x0, axis_z0, axis_x1, axis_z1)

    # retaining faces + on-axis stairs between adjacent terraces
    retaining_faces: List[RetainingFace] = []
    axis_stairs: List[AxisStair] = []
    for i in range(len(terraces) - 1):
        lower = terraces[i]
        upper = terraces[i + 1]
        # the stair band spans lower.z1+1 .. upper.z0-1
        stair_z0 = lower.bounds[3] + 1
        stair_z1 = upper.bounds[1] - 1
        stair_bounds = (axis_x0, stair_z0, axis_x1, stair_z1)
        axis_stairs.append(AxisStair(
            id=f"stair_{i}_{i+1}",
            lower_terrace=i, upper_terrace=i + 1,
            bounds=stair_bounds))
        # retaining face: the full width of the upper terrace's front edge,
        # height = inter-terrace rise, minus the stair opening
        retaining_faces.append(RetainingFace(
            id=f"retain_{i}_{i+1}",
            lower_terrace=i, upper_terrace=i + 1,
            bounds=(upper.bounds[0], stair_z0, upper.bounds[2], stair_z1),
            height=geometry.terrace_rise))

    # covered galleries: link flanks to their on-axis volume on the same terrace,
    # and left↔right where there is no on-axis volume. Endpoints rest on the
    # facing slot edges so the gallery is a circulation link, not decoration.
    gallery_links: List[GalleryLink] = _build_galleries(terraces, slots, axis_half)

    # detached-spire flying-bridge feature (per-seed, may be absent)
    feature = _build_feature(seed, terraces, slots, geometry)

    terrace_profile = _export_terrace_profile(
        site, geometry, terraces, footprint_depth)

    return SectPlan(
        seed=seed,
        site=site,
        geometry=geometry,
        terraces=terraces,
        axis_cells=axis_cells,
        slots=slots,
        gallery_links=gallery_links,
        retaining_faces=retaining_faces,
        axis_stairs=axis_stairs,
        feature=feature,
        terrace_profile=terrace_profile,
    )


def _slot_by_role(slots: Sequence[Slot], terrace_index: int, role: str) -> Optional[Slot]:
    for s in slots:
        if s.terrace_index == terrace_index and s.role == role:
            return s
    return None


def _edge_facing(slot: Slot, toward: Cell2) -> Cell2:
    """A cell on ``slot``'s edge closest to ``toward`` (the other volume)."""
    x0, z0, x1, z1 = slot.bounds
    tx, tz = toward
    ex = min(x1, max(x0, tx))
    ez = min(z1, max(z0, tz))
    return (ex, ez)


def _build_galleries(
    terraces: Sequence[Terrace],
    slots: Sequence[Slot],
    axis_half: int,
) -> List[GalleryLink]:
    links: List[GalleryLink] = []
    for terrace in terraces:
        on_axis = _slot_by_role(slots, terrace.index, "on_axis")
        left = _slot_by_role(slots, terrace.index, "flank_left")
        right = _slot_by_role(slots, terrace.index, "flank_right")
        if on_axis is not None:
            cx_on = on_axis.center
            for flank in (left, right):
                if flank is None:
                    continue
                links.append(GalleryLink(
                    id=f"gallery_{terrace.name}_{flank.role}_{terrace.index}",
                    kind="covered_gallery",
                    from_slot=on_axis.id,
                    to_slot=flank.id,
                    from_cell=_edge_facing(on_axis, flank.center),
                    to_cell=_edge_facing(flank, cx_on),
                    terrace_indices=(terrace.index, terrace.index),
                ))
        elif left is not None and right is not None:
            # no on-axis volume: covered cross-gallery joining the two flanks
            links.append(GalleryLink(
                id=f"gallery_{terrace.name}_cross_{terrace.index}",
                kind="covered_gallery",
                from_slot=left.id,
                to_slot=right.id,
                from_cell=_edge_facing(left, right.center),
                to_cell=_edge_facing(right, left.center),
                terrace_indices=(terrace.index, terrace.index),
            ))
    return links


def _build_feature(
    seed: int,
    terraces: Sequence[Terrace],
    slots: Sequence[Slot],
    geometry: SectGeometry,
) -> Optional[FlyingBridgeFeature]:
    chosen = _feature_choice(seed)
    if chosen is None:
        return None
    spec = FEATURE_VARIANTS[chosen]
    archetype = str(spec["detached_archetype"])
    template = _template_for(archetype, seed, len(terraces))
    tw, td = template_footprint(template)

    # anchor the detached volume off the summit terrace, offset by spire_offset
    summit = terraces[-1]
    sx, sz = spec["spire_offset"]
    bearing = str(spec["bearing"])
    cx = (summit.bounds[0] + summit.bounds[2]) // 2
    cz = summit.bounds[1]
    dx0 = cx + int(sx) - tw // 2
    dz0 = cz + int(sz) - td // 2
    detached_bounds = (dx0, dz0, dx0 + tw - 1, dz0 + td - 1)

    detached_slot_id = f"slot_detached_{archetype}_feature"
    detached_slot = Slot(
        id=detached_slot_id,
        terrace_index=summit.index,
        terrace_name="detached_spire",
        role="detached",
        archetype=archetype,
        template_id=template,
        importance_tier=archetype_importance(archetype),
        bounds=detached_bounds,
    )
    # bridge endpoints: nearest edges of summit on-axis slot and detached volume
    on_axis = _slot_by_role(slots, summit.index, "on_axis") or summit
    on_cell = _edge_facing(on_axis if isinstance(on_axis, Slot) else _summit_proxy_slot(summit), detached_slot.center)
    detached_cell = _edge_facing(detached_slot, on_cell)
    bridge_link = GalleryLink(
        id="flying_bridge_feature",
        kind="flying_bridge",
        from_slot=on_axis.id if isinstance(on_axis, Slot) else "summit_terrace",
        to_slot=detached_slot_id,
        from_cell=on_cell,
        to_cell=detached_cell,
        terrace_indices=(summit.index, summit.index),
    )
    return FlyingBridgeFeature(
        variant=chosen,
        detached_archetype=archetype,
        detached_template=template,
        detached_slot_id=detached_slot_id,
        detached_bounds=detached_bounds,
        spire_offset=(int(sx), int(sz)),
        bearing=bearing,
        bridge_span=int(spec["bridge_span"]),
        bridge_shape=str(spec["bridge_shape"]),
        bridge_link=bridge_link,
    )


@dataclass(frozen=True)
class _ProxySlot:
    id: str
    bounds: Rect

    @property
    def center(self) -> Cell2:
        x0, z0, x1, z1 = self.bounds
        return ((x0 + x1) // 2, (z0 + z1) // 2)


def _summit_proxy_slot(summit: Terrace) -> _ProxySlot:
    return _ProxySlot(id="summit_terrace", bounds=summit.bounds)


def _export_terrace_profile(
    site: SectSite,
    geometry: SectGeometry,
    terraces: Sequence[Terrace],
    footprint_depth: int,
) -> Dict[str, object]:
    """Export the terrace skeleton + geometry parameters for add-sect-worldgen.

    This is the 反推山形 contract: worldgen derives the man-made mountain from
    exactly these values (terrace elevations as skeleton, rise/depth/taper for
    slopes, axis_stair_w for the processional cut, cliff_back_height for the
    summit sheer face). Do not re-derive these downstream.
    """
    return {
        "contract_version": 1,
        "seed_note": "elevations are absolute world Y given site.base_y; worldgen sites the compound then derives slopes beneath",
        "footprint": {
            "width": geometry.terrace_width,
            "depth": footprint_depth,
            "z_margin": Z_MARGIN,
            "base_y": site.base_y,
        },
        "geometry": {
            "terrace_count": geometry.terrace_count,
            "terrace_rise": geometry.terrace_rise,
            "terrace_depth": geometry.terrace_depth,
            "terrace_width_foot": geometry.terrace_width,
            "summit_taper": geometry.summit_taper,
            "axis_stair_w": geometry.axis_stair_w,
            "cliff_back_height": geometry.cliff_back_height,
        },
        "terraces": [
            {
                "index": t.index,
                "name": t.name,
                "elevation": t.elevation,
                "bounds": list(t.bounds),
                "width": t.width,
                "depth": t.depth,
                "cliff_back": t.cliff_back,
            }
            for t in terraces
        ],
        "fall_line_axis": "z",
        "foot_edge": "low_z",
        "summit_edge": "high_z",
    }


# --- validation -----------------------------------------------------------


def _terraces_ascend(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    if not plan.terraces:
        errors.append("missing_terraces")
        return errors
    prev_elev = None
    for t in plan.terraces:
        if prev_elev is not None and t.elevation <= prev_elev:
            errors.append(f"terrace_not_ascending:{t.index}:{t.elevation}<={prev_elev}")
        prev_elev = t.elevation
    return errors


def _validate_axis(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    if not plan.axis_cells:
        errors.append("missing_axis")
        return errors
    gate = plan.terraces[0]
    summit = plan.terraces[-1]
    axis = set(plan.axis_cells)
    # axis touches the gate terrace and the summit terrace
    if not (axis & _rect_set(*gate.bounds)):
        errors.append("axis_not_on_gate_terrace")
    if not (axis & _rect_set(*summit.bounds)):
        errors.append("axis_not_on_summit_terrace")
    # axis is traversable foot→summit along z (contiguous column)
    zs = sorted({z for _, z in plan.axis_cells})
    if zs:
        gaps = [z for a, b in zip(zs, zs[1:]) if b - a > 1]
        if gaps:
            errors.append(f"axis_not_contiguous:gap_after_z={gaps[0]}")
    # gate terrace name and summit hall
    if gate.name != "gate":
        errors.append(f"foot_terrace_not_gate:{gate.name}")
    if summit.name != "summit":
        errors.append(f"top_terrace_not_summit:{summit.name}")
    return errors


def _validate_importance(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    # non-decreasing up the stack
    by_terrace: Dict[int, List[Slot]] = {}
    for s in plan.slots:
        by_terrace.setdefault(s.terrace_index, []).append(s)
    max_tier = -1
    for t in plan.terraces:
        tiers = [s.importance_tier for s in by_terrace.get(t.index, [])]
        if not tiers:
            continue
        floor = min(tiers)
        if floor < max_tier:
            errors.append(
                f"importance_decreases_at_terrace:{t.name}:{floor}<preceding_max_{max_tier}")
        max_tier = max(max_tier, max(tiers))
    # principal hall + scripture pagoda at top tiers
    hall = next((s for s in plan.slots if s.archetype == "sect_main_hall"), None)
    pagoda = next((s for s in plan.slots if s.archetype == "pagoda"), None)
    if hall is None:
        errors.append("missing_principal_hall")
    elif hall.importance_tier < MAX_IMPORTANCE_TIER:
        errors.append(f"principal_hall_not_top_tier:{hall.importance_tier}")
    # pagoda may be absent only on the 4-terrace skeleton (no scripture tier)
    if pagoda is not None and pagoda.importance_tier < 2:
        errors.append(f"pagoda_not_top_tier:{pagoda.importance_tier}")
    return errors


def _validate_symmetry(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    by_terrace: Dict[int, List[Slot]] = {}
    for s in plan.slots:
        by_terrace.setdefault(s.terrace_index, []).append(s)
    for t in plan.terraces:
        flanks = [s for s in by_terrace.get(t.index, []) if s.role.startswith("flank")]
        on_axis = [s for s in by_terrace.get(t.index, []) if s.role == "on_axis"]
        if len(on_axis) > 1:
            errors.append(f"multiple_on_axis_volumes:{t.name}")
        if flanks:
            lefts = [s for s in flanks if s.role == "flank_left"]
            rights = [s for s in flanks if s.role == "flank_right"]
            if len(lefts) != len(rights):
                errors.append(f"flank_pair_unbalanced:{t.name}:L{len(lefts)}R{len(rights)}")
            # bell/drum flank the gate symmetrically
    return errors


def _validate_no_slot_overlap(plan: SectPlan) -> List[str]:
    """Slots on a terrace must not overlap (volumes share the platform cleanly)."""
    errors: List[str] = []
    for i in range(len(plan.slots)):
        for j in range(i + 1, len(plan.slots)):
            a = plan.slots[i]
            b = plan.slots[j]
            if a.terrace_index != b.terrace_index:
                continue
            ax0, az0, ax1, az1 = a.bounds
            bx0, bz0, bx1, bz1 = b.bounds
            if ax1 >= bx0 and bx1 >= ax0 and az1 >= bz0 and bz1 >= az0:
                errors.append(f"slot_overlap:{a.id}:{b.id}")
    # every slot must lie inside its terrace bounds
    by_index = {t.index: t for t in plan.terraces}
    for s in plan.slots:
        t = by_index.get(s.terrace_index)
        if t is None:
            errors.append(f"slot_without_terrace:{s.id}")
            continue
        tx0, tz0, tx1, tz1 = t.bounds
        sx0, sz0, sx1, sz1 = s.bounds
        if sx0 < tx0 or sx1 > tx1 or sz0 < tz0 or sz1 > tz1:
            errors.append(f"slot_outside_terrace:{s.id}")
    return errors


def _endpoint_on_slot_or_terrace(cell: Cell2, plan: SectPlan, slot_id: str) -> bool:
    if slot_id == "summit_terrace":
        summit = plan.terraces[-1]
        x0, z0, x1, z1 = summit.bounds
        return x0 <= cell[0] <= x1 and z0 <= cell[1] <= z1
    target = next((s for s in plan.slots if s.id == slot_id), None)
    if target is None:
        # detached feature volume
        if plan.feature is not None and plan.feature.detached_slot_id == slot_id:
            x0, z0, x1, z1 = plan.feature.detached_bounds
            return x0 <= cell[0] <= x1 and z0 <= cell[1] <= z1
        return False
    x0, z0, x1, z1 = target.bounds
    return x0 <= cell[0] <= x1 and z0 <= cell[1] <= z1


def _validate_links(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    all_links: List[GalleryLink] = list(plan.gallery_links)
    if plan.feature is not None:
        all_links.append(plan.feature.bridge_link)
    for g in all_links:
        if not _endpoint_on_slot_or_terrace(g.from_cell, plan, g.from_slot):
            errors.append(f"link_from_endpoint_off_volume:{g.id}")
        if not _endpoint_on_slot_or_terrace(g.to_cell, plan, g.to_slot):
            errors.append(f"link_to_endpoint_off_volume:{g.id}")
    # at least one covered gallery
    if not any(g.kind == "covered_gallery" for g in plan.gallery_links):
        errors.append("missing_covered_gallery")
    return errors


def _validate_retaining_and_stairs(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    if len(plan.retaining_faces) != len(plan.terraces) - 1:
        errors.append("retaining_face_count_mismatch")
    if len(plan.axis_stairs) != len(plan.terraces) - 1:
        errors.append("axis_stair_count_mismatch")
    for r in plan.retaining_faces:
        if r.height != plan.geometry.terrace_rise:
            errors.append(f"retaining_height_not_rise:{r.id}:{r.height}")
    # summit declares cliff-back
    if not plan.terraces[-1].cliff_back:
        errors.append("summit_missing_cliff_back")
    hall = next((s for s in plan.slots if s.archetype == "sect_main_hall"), None)
    if hall is not None and not hall.against_cliff_back:
        errors.append("principal_hall_not_against_cliff_back")
    return errors


def _validate_width_taper(plan: SectPlan) -> List[str]:
    errors: List[str] = []
    widths = [t.width for t in plan.terraces]
    for a, b in zip(widths, widths[1:]):
        if b > a:
            errors.append(f"terrace_width_increases_up_stack:{b}>{a}")
            break
    for required in ("terrace_rise", "terrace_depth", "summit_taper",
                     "axis_stair_w", "cliff_back_height"):
        if required not in plan.terrace_profile.get("geometry", {}):
            errors.append(f"terrace_profile_missing_param:{required}")
    return errors


def _variants_pairwise_distinct() -> List[str]:
    """The 3 feature variants differ on >=2 of volume/span/shape/offset/bearing."""
    errors: List[str] = []
    names = list(FEATURE_VARIANTS.keys())
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            a = FEATURE_VARIANTS[names[i]]
            b = FEATURE_VARIANTS[names[j]]
            diffs = 0
            if a["detached_archetype"] != b["detached_archetype"]:
                diffs += 1
            if a["bridge_span"] != b["bridge_span"]:
                diffs += 1
            if a["bridge_shape"] != b["bridge_shape"]:
                diffs += 1
            if a["spire_offset"] != b["spire_offset"]:
                diffs += 1
            if a["bearing"] != b["bearing"]:
                diffs += 1
            if diffs < 2:
                errors.append(f"feature_variants_not_distinct:{names[i]}:{names[j]}:diffs={diffs}")
    return errors


def validate_sect_plan(plan: SectPlan) -> dict:
    errors: List[str] = []
    # count range
    if not (MIN_TERRACE_COUNT <= plan.geometry.terrace_count <= MAX_TERRACE_COUNT):
        errors.append(f"terrace_count_out_of_range:{plan.geometry.terrace_count}")
    errors.extend(_terraces_ascend(plan))
    errors.extend(_validate_axis(plan))
    errors.extend(_validate_importance(plan))
    errors.extend(_validate_symmetry(plan))
    errors.extend(_validate_no_slot_overlap(plan))
    errors.extend(_validate_links(plan))
    errors.extend(_validate_retaining_and_stairs(plan))
    errors.extend(_validate_width_taper(plan))
    errors.extend(_variants_pairwise_distinct())
    # terrace profile export present
    if not plan.terrace_profile or "terraces" not in plan.terrace_profile:
        errors.append("missing_terrace_profile_export")
    stats = {
        "terrace_count": len(plan.terraces),
        "slot_count": len(plan.slots),
        "gallery_count": len(plan.gallery_links),
        "retaining_count": len(plan.retaining_faces),
        "axis_stair_count": len(plan.axis_stairs),
        "feature_present": plan.feature is not None,
        "feature_variant": plan.feature.variant if plan.feature else None,
        "axis_cells": len(plan.axis_cells),
    }
    return {"passed": not errors, "errors": errors, "stats": stats}


def validate_feature_variants() -> dict:
    """Standalone check that the 3 feature variants are pairwise distinct and
    that feature-absent plans are still complete."""
    errors: List[str] = list(_variants_pairwise_distinct())
    # find a seed with feature absent and confirm the plan still validates
    absent_seed = None
    for s in range(FEATURE_PERIOD * 4):
        if _feature_choice(s) is None:
            absent_seed = s
            break
    if absent_seed is None:
        errors.append("no_absent_feature_seed_found")
    else:
        plan = generate_sect_plan(absent_seed)
        report = validate_sect_plan(plan)
        if not report["passed"]:
            errors.append(f"feature_absent_plan_invalid:{absent_seed}:{report['errors']}")
        if plan.feature is not None:
            errors.append("absent_seed_produced_feature")
    return {"passed": not errors, "errors": errors}


def validate_sect_reproducibility(seeds: Sequence[int]) -> dict:
    """Same seed + site yields identical terraces/axis/slots/links/feature."""
    errors: List[str] = []
    for seed in seeds:
        a = generate_sect_plan(seed)
        b = generate_sect_plan(seed)
        if [(t.index, t.name, t.elevation, t.bounds) for t in a.terraces] != \
                [(t.index, t.name, t.elevation, t.bounds) for t in b.terraces]:
            errors.append(f"terraces_not_reproducible:{seed}")
        if a.axis_cells != b.axis_cells:
            errors.append(f"axis_not_reproducible:{seed}")
        if [(s.id, s.archetype, s.template_id, s.bounds, s.importance_tier) for s in a.slots] != \
                [(s.id, s.archetype, s.template_id, s.bounds, s.importance_tier) for s in b.slots]:
            errors.append(f"slots_not_reproducible:{seed}")
        if [(g.id, g.from_slot, g.to_slot, g.from_cell, g.to_cell) for g in a.gallery_links] != \
                [(g.id, g.from_slot, g.to_slot, g.from_cell, g.to_cell) for g in b.gallery_links]:
            errors.append(f"gallery_links_not_reproducible:{seed}")
        fa = a.feature.variant if a.feature else None
        fb = b.feature.variant if b.feature else None
        if fa != fb:
            errors.append(f"feature_not_reproducible:{seed}:{fa}!={fb}")
    return {"passed": not errors, "errors": errors}
