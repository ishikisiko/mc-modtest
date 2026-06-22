"""Building Archetype layer + Scale Tier layer.

Each archetype builds a MassingGraph with its own functional logic:
  small_house  - single volume + porch + chimney/window detail + living/storage
  medium_house - main + side wing, two roofs, >=2 interior zones, asymmetric
  blacksmith   - work shed / open work area, furnace cluster, chimney, barrels

Scale tiers decide composition, not uniform scaling:
  small      - single volume + porch + chimney/window detail
  medium     - main + wing/shed + secondary roof + interior zones
  large_lite - >=3 attached volumes (main + wing + rear shed) + courtyard
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

from .massing import MassingGraph, Node, OUTWARD_FACING
from .style import Style

SCALE_TIERS = {
    "small": {
        "footprints": [(9, 7), (11, 9)],
        "min_volumes": 1,
    },
    "medium": {
        "footprints": [(11, 9), (13, 9)],   # odd depth keeps a clean ridge line
        "min_volumes": 2,
    },
    "large_lite": {
        "footprints": [(13, 9), (15, 11)],
        "min_volumes": 3,
    },
    "small_shop": {
        "footprints": [(9, 7), (11, 7), (11, 9)],
        "min_volumes": 1,
    },
    "medium_shop": {
        "footprints": [(11, 9), (13, 9), (13, 11)],
        "min_volumes": 1,
    },
    "big_house": {
        "footprints": [(13, 9), (13, 11), (15, 11)],
        "min_volumes": 1,
    },
    "main_hall": {
        "footprints": [(15, 11), (17, 11), (17, 13)],
        "min_volumes": 1,
    },
    "side_wing": {
        "footprints": [(7, 15), (7, 17), (9, 15)],
        "min_volumes": 1,
    },
    "front_row": {
        "footprints": [(15, 7), (17, 7), (19, 7)],
        "min_volumes": 1,
    },
    "gate_house": {
        "footprints": [(9, 5), (11, 5), (11, 7)],
        "min_volumes": 1,
    },
    "open_hall": {
        "footprints": [(15, 11), (17, 11), (17, 13)],
        "min_volumes": 1,
    },
    "tower_house": {
        "footprints": [(9, 11), (11, 11), (11, 13)],
        "min_volumes": 1,
    },
    "flower_hall": {
        "footprints": [(11, 9), (13, 9), (13, 11)],
        "min_volumes": 1,
    },
    "tavern": {
        "footprints": [(15, 11), (17, 11), (17, 13)],
        "min_volumes": 1,
    },
    "lord_manor": {
        "footprints": [(17, 13), (19, 13), (19, 15)],
        "min_volumes": 2,
    },
    "cultivation_house": {
        "footprints": [(11, 9), (13, 9), (13, 11)],
        "min_volumes": 1,
    },
    "cultivation_shop": {
        "footprints": [(11, 9), (13, 9), (13, 11)],
        "min_volumes": 1,
    },
    "cultivation_inn": {
        "footprints": [(15, 11), (17, 11), (17, 13)],
        "min_volumes": 1,
    },
    "cultivation_market": {
        "footprints": [(13, 9), (13, 11), (15, 11)],
        "min_volumes": 1,
    },
    "town_shrine": {
        "footprints": [(17, 13), (19, 13), (19, 15)],
        "min_volumes": 1,
    },
    "pagoda": {
        "footprints": [(13, 13), (15, 15)],
        "min_volumes": 1,
    },
    "pavilion": {
        "footprints": [(15, 13), (17, 13)],
        "min_volumes": 1,
    },
    "bell_drum_tower": {
        "footprints": [(13, 13), (13, 15)],
        "min_volumes": 1,
    },
    "sect_gate": {
        "footprints": [(15, 9), (17, 9)],
        "min_volumes": 1,
    },
    "sect_main_hall": {
        "footprints": [(19, 15), (21, 15), (23, 17)],
        "min_volumes": 1,
    },
    "scripture_pavilion": {
        "footprints": [(13, 13), (15, 15)],
        "min_volumes": 1,
    },
    "alchemy_room": {
        "footprints": [(13, 11), (15, 11)],
        "min_volumes": 1,
    },
    "disciple_quarters": {
        "footprints": [(15, 11), (17, 11)],
        "min_volumes": 1,
    },
}

ARCHETYPES = ("small_house", "medium_house", "blacksmith",
              "small_shop", "medium_shop", "big_house")
CHINESE_ARCHETYPES = ("main_hall", "side_wing", "front_row", "gate_house")
MANSION_ARCHETYPES = (
    "main_hall", "open_hall", "side_wing", "flower_hall",
    "front_row", "tower_house", "garden_pavilion",
)
CIVIC_ARCHETYPES = ("tavern", "lord_manor")
CULTIVATION_TOWN_ARCHETYPES = (
    "cultivation_house", "cultivation_shop", "cultivation_inn",
    "cultivation_market", "town_shrine",
    "pagoda", "pavilion", "bell_drum_tower",
)
CULTIVATION_SECT_ARCHETYPES = (
    "sect_gate", "sect_main_hall", "scripture_pavilion",
    "alchemy_room", "disciple_quarters",
)
NEW_ARCHETYPE_COUNTS = {"small_shop": 5, "medium_shop": 5, "big_house": 5}
NEW_ARCHETYPE_COUNTS.update({archetype: 3 for archetype in CULTIVATION_TOWN_ARCHETYPES})
NEW_ARCHETYPE_COUNTS.update({archetype: 2 for archetype in CULTIVATION_SECT_ARCHETYPES})
CIVIC_ARCHETYPE_COUNTS = {"tavern": 5, "lord_manor": 3}

# scale tier used for each gallery seed index (1-based), per archetype
TIER_PLAN = {
    "small_house": {i: "small" for i in range(1, 11)},
    "medium_house": {**{i: "medium" for i in range(1, 8)},
                     **{i: "large_lite" for i in range(8, 11)}},
    "blacksmith": {1: "small", 2: "small",
                   **{i: "medium" for i in range(3, 9)},
                   9: "large_lite", 10: "large_lite"},
    "small_shop": {i: f"small_shop_v{i}" for i in range(1, 6)},
    "medium_shop": {i: f"medium_shop_v{i}" for i in range(1, 6)},
    "big_house": {i: f"big_house_v{i}" for i in range(1, 6)},
    "cultivation_house": {i: f"cultivation_house_v{i}" for i in range(1, 4)},
    "cultivation_shop": {i: f"cultivation_shop_v{i}" for i in range(1, 4)},
    "cultivation_inn": {i: f"cultivation_inn_v{i}" for i in range(1, 4)},
    "cultivation_market": {i: f"cultivation_market_v{i}" for i in range(1, 4)},
    "town_shrine": {i: f"town_shrine_v{i}" for i in range(1, 4)},
    "pagoda": {i: f"pagoda_v{i}" for i in range(1, 4)},
    "pavilion": {i: f"pavilion_v{i}" for i in range(1, 4)},
    "bell_drum_tower": {i: f"bell_drum_tower_v{i}" for i in range(1, 4)},
    "sect_gate": {i: f"sect_gate_v{i}" for i in range(1, 3)},
    "sect_main_hall": {i: f"sect_main_hall_v{i}" for i in range(1, 3)},
    "scripture_pavilion": {i: f"scripture_pavilion_v{i}" for i in range(1, 3)},
    "alchemy_room": {i: f"alchemy_room_v{i}" for i in range(1, 3)},
    "disciple_quarters": {i: f"disciple_quarters_v{i}" for i in range(1, 3)},
}

SHOP_VARIANTS: Dict[str, List[dict]] = {
    "small_shop": [
        {"footprint": (9, 7), "roof_axis": "x", "overhang": 1, "storefront_w": 5,
         "awning": "slab", "signage": "beam", "entrance_shift": -1},
        {"footprint": (11, 7), "roof_axis": "x", "overhang": 0, "storefront_w": 7,
         "awning": "eave", "signage": "post", "entrance_shift": 1},
        {"footprint": (9, 9), "roof_axis": "z", "overhang": 1, "storefront_w": 5,
         "awning": "slab", "signage": "beam", "entrance_shift": 0},
        {"footprint": (11, 9), "roof_axis": "x", "overhang": 1, "storefront_w": 7,
         "awning": "eave", "signage": "post", "entrance_shift": -2},
        {"footprint": (13, 7), "roof_axis": "x", "overhang": 0, "storefront_w": 7,
         "awning": "slab", "signage": "beam", "entrance_shift": 2},
    ],
    "medium_shop": [
        {"footprint": (11, 9), "roof_axis": "x", "overhang": 1, "storefront_w": 7,
         "awning": "slab", "signage": "beam", "entrance_shift": -1},
        {"footprint": (13, 9), "roof_axis": "x", "overhang": 0, "storefront_w": 7,
         "awning": "eave", "signage": "post", "entrance_shift": 1},
        {"footprint": (11, 11), "roof_axis": "z", "overhang": 1, "storefront_w": 5,
         "awning": "slab", "signage": "beam", "entrance_shift": 0},
        {"footprint": (13, 11), "roof_axis": "x", "overhang": 1, "storefront_w": 9,
         "awning": "eave", "signage": "post", "entrance_shift": -2},
        {"footprint": (15, 9), "roof_axis": "x", "overhang": 0, "storefront_w": 9,
         "awning": "slab", "signage": "beam", "entrance_shift": 2},
    ],
}

BIG_HOUSE_VARIANTS = [
    {"stories": 2, "footprint": (13, 9), "roof_axis": "x", "overhang": 1,
     "wing": None},
    {"stories": 2, "footprint": (13, 11), "roof_axis": "x", "overhang": 1,
     "wing": "west"},
    {"stories": 3, "footprint": (11, 9), "roof_axis": "x", "overhang": 0,
     "wing": None},
    {"stories": 2, "footprint": (15, 11), "roof_axis": "z", "overhang": 1,
     "wing": "east"},
    {"stories": 3, "footprint": (13, 11), "roof_axis": "x", "overhang": 1,
     "wing": "west"},
]

TAVERN_VARIANTS: List[dict] = [
    {"footprint": (15, 11), "mezzanine": "west", "stable": True,
     "roof_axis": "x", "overhang": 1, "door_shift": 2},
    {"footprint": (17, 11), "mezzanine": "east", "stable": False,
     "roof_axis": "x", "overhang": 0, "door_shift": -2},
    {"footprint": (17, 13), "mezzanine": "west", "stable": True,
     "roof_axis": "z", "overhang": 1, "door_shift": 0},
    {"footprint": (15, 11), "mezzanine": "east", "stable": True,
     "roof_axis": "x", "overhang": 1, "door_shift": -1},
    {"footprint": (17, 13), "mezzanine": "west", "stable": False,
     "roof_axis": "z", "overhang": 0, "door_shift": 1},
]

LORD_MANOR_VARIANTS: List[dict] = [
    {"footprint": (17, 13), "tower_side": "east", "tower_stories_over_main": 1,
     "tower_footprint": (5, 5), "wing": None, "roof_axis": "x", "overhang": 1},
    {"footprint": (19, 13), "tower_side": "west", "tower_stories_over_main": 2,
     "tower_footprint": (5, 7), "wing": "east", "roof_axis": "x", "overhang": 1},
    {"footprint": (19, 15), "tower_side": "east", "tower_stories_over_main": 2,
     "tower_footprint": (7, 5), "wing": "west", "roof_axis": "z", "overhang": 0},
]


# ---------------------------------------------------------------------------
# Cultivation town variant templates (deterministic 形制 ladder)
#
# Each town archetype's three variants (`_v1/_v2/_v3`) are explicit massing
# templates selected by variant_index, not random rolls. The backbone is a
# 1 -> 2 -> 3 silhouette-volume ladder (each ancillary `side_wing`/`rear_shed`
# is worth +15 silhouette), and each rung also shifts footprint, platform
# height (fh), wall height, and ridge axis so variants differ on >=2 axes.
# The walled rear courtyard (`courtyard`) is a plan-level element only and is
# NOT a silhouette volume, so templates that use it always pair it with a
# rear_shed. See openspec/changes/differentiate-cultivation-variants/design.md.
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class CultivationAncillary:
    """One attached massing element selected by a cultivation template.

    kind:
      "side_wing" - 厢房/摊廊: sticks out `out` cells on `side` (west/east),
                    breadth along z derived from main depth (or `breadth`).
      "rear_shed" - 后罩房/作坊: attaches to the back, `out` deep, `breadth` wide.
      "courtyard" - 后院: walled rear courtyard (plan element, no roof volume).
    """
    kind: str
    side: str = "west"
    out: int = 5
    breadth: int = 0
    wall_h: int = 3
    zone: str = "storage"


@dataclass(frozen=True)
class CultivationTemplate:
    footprint: Tuple[int, int]
    fh: int
    wall_h: int
    ridge_axis: str
    roof_type: str
    stories: int = 1
    story_wall_h: Optional[int] = None
    roof_overhang: int = 2
    ancillaries: Tuple[CultivationAncillary, ...] = ()


# variant_index -> CultivationTemplate, per town archetype.
CULTIVATION_TEMPLATES: Dict[str, Dict[int, CultivationTemplate]] = {
    "cultivation_house": {
        # v1 单进平房: low single volume
        1: CultivationTemplate(
            footprint=(11, 9), fh=1, wall_h=3, ridge_axis="x",
            roof_type="sweeping_eave_roof"),
        # v2 带厢房: taller + one 厢房 (cross-gable double ridge)
        2: CultivationTemplate(
            footprint=(13, 9), fh=2, wall_h=4, ridge_axis="x",
            roof_type="sweeping_eave_roof",
            ancillaries=(
                CultivationAncillary("side_wing", side="west", out=5, zone="living"),
            )),
        # v3 三合后院: 东西厢房 ×2 + walled 后院, hip roof
        3: CultivationTemplate(
            footprint=(13, 11), fh=2, wall_h=4, ridge_axis="x",
            roof_type="hip_roof",
            ancillaries=(
                CultivationAncillary("side_wing", side="west", out=5, zone="living"),
                CultivationAncillary("side_wing", side="east", out=5, zone="storage"),
                CultivationAncillary("courtyard", side="back"),
            )),
    },
    "cultivation_shop": {
        # v1 单开间窄店: narrow & deep, transverse ridge (瘦高山墙脸)
        1: CultivationTemplate(
            footprint=(9, 11), fh=1, wall_h=4, ridge_axis="z",
            roof_type="sweeping_eave_roof"),
        # v2 双开间带廊: wider + 摊廊 side_wing, eave-front
        2: CultivationTemplate(
            footprint=(13, 9), fh=2, wall_h=4, ridge_axis="x",
            roof_type="sweeping_eave_roof",
            ancillaries=(
                CultivationAncillary("side_wing", side="east", out=4, zone="work"),
            )),
        # v3 前店后坊: 后罩房(作坊) + side store + walled 后院
        3: CultivationTemplate(
            footprint=(13, 9), fh=2, wall_h=4, ridge_axis="x",
            roof_type="sweeping_eave_roof",
            ancillaries=(
                CultivationAncillary("rear_shed", side="back", out=4, breadth=7,
                                     zone="work"),
                CultivationAncillary("side_wing", side="east", out=4, zone="storage"),
                CultivationAncillary("courtyard", side="back"),
            )),
    },
    "cultivation_market": {
        # v1 单棚市: single open-front shed
        1: CultivationTemplate(
            footprint=(13, 9), fh=1, wall_h=4, ridge_axis="x",
            roof_type="sweeping_eave_roof"),
        # v2 L 形摊廊: + 摊廊 side_wing
        2: CultivationTemplate(
            footprint=(13, 11), fh=1, wall_h=4, ridge_axis="x",
            roof_type="sweeping_eave_roof",
            ancillaries=(
                CultivationAncillary("side_wing", side="west", out=5, zone="work"),
            )),
        # v3 回廊内院: 东西摊廊 ×2 around an inner court
        3: CultivationTemplate(
            footprint=(15, 11), fh=2, wall_h=4, ridge_axis="x",
            roof_type="hip_roof",
            ancillaries=(
                CultivationAncillary("side_wing", side="west", out=5, zone="work"),
                CultivationAncillary("side_wing", side="east", out=5, zone="storage"),
            )),
    },
    "cultivation_inn": {
        # v1 阔面客栈: wide two-story hall
        1: CultivationTemplate(
            footprint=(17, 13), fh=2, wall_h=8, ridge_axis="x",
            roof_type="hip_roof", stories=2, story_wall_h=4),
        # v2 高台重檐: raised platform + 重檐 (tiered eave) + 厢房
        2: CultivationTemplate(
            footprint=(17, 13), fh=3, wall_h=8, ridge_axis="x",
            roof_type="tiered_eave_roof", stories=2, story_wall_h=4,
            ancillaries=(
                CultivationAncillary("side_wing", side="west", out=5, wall_h=4,
                                     zone="living"),
            )),
        # v3 后院客栈: 后罩客房 rear_shed + 马厩 side_wing + walled 后院
        3: CultivationTemplate(
            footprint=(17, 11), fh=2, wall_h=8, ridge_axis="x",
            roof_type="hip_roof", stories=2, story_wall_h=4,
            ancillaries=(
                CultivationAncillary("rear_shed", side="back", out=4, breadth=9,
                                     wall_h=4, zone="living"),
                CultivationAncillary("side_wing", side="east", out=5, wall_h=4,
                                     zone="storage"),
                CultivationAncillary("courtyard", side="back"),
            )),
    },
}


def cultivation_template(archetype: str, tier: str) -> CultivationTemplate:
    """Resolve the deterministic variant template for a cultivation archetype."""
    table = CULTIVATION_TEMPLATES[archetype]
    vindex = variant_index(tier)
    return table.get(vindex, table[max(table)])


def tier_base(tier: str) -> str:
    return tier.rsplit("_v", 1)[0] if "_v" in tier else tier


def variant_index(tier: str) -> int:
    if "_v" not in tier:
        return 1
    return max(1, int(tier.rsplit("_v", 1)[1]))


def roof_peak_y(wall_top: int, span: int, overhang: int) -> int:
    """Highest roof y for a gable over `span` blocks with symmetric overhang."""
    total = span + 2 * overhang
    return wall_top + 1 + (total - 1) // 2


FRONTAGE_SIDES = ("front", "back", "west", "east")
IMPORTANCE_TIERS = (0, 1, 2, 3)
IMPORTANCE_HINTS = {
    0: {"height": "low", "roof_grade": "plain"},
    1: {"height": "base", "roof_grade": "standard"},
    2: {"height": "tall", "roof_grade": "fine"},
    3: {"height": "dominant", "roof_grade": "landmark"},
}


def clamp_importance_tier(importance_tier: int) -> int:
    return max(min(int(importance_tier), max(IMPORTANCE_TIERS)), min(IMPORTANCE_TIERS))


def _side_span(vol: Node, side: str) -> int:
    if side in ("front", "back"):
        return vol.size[0]
    if side in ("west", "east"):
        return vol.size[2]
    raise ValueError(f"unknown frontage side {side!r}")


def _attached_occlusion(parent: Node, child: Node, side: str) -> int:
    if child.attach_to != parent.id or child.side != side:
        return 0
    if side in ("front", "back"):
        lo = max(parent.x0, child.x0)
        hi = min(parent.x1, child.x1)
    else:
        lo = max(parent.z0, child.z0)
        hi = min(parent.z1, child.z1)
    return max(0, hi - lo + 1)


def _largest_open_side(graph: MassingGraph, vol: Node) -> str:
    """Choose the longest side not occupied by an attached volume."""
    scores = {}
    for side in FRONTAGE_SIDES:
        blocked = sum(_attached_occlusion(vol, other, side) for other in graph.nodes)
        scores[side] = max(0, _side_span(vol, side) - blocked)
    return max(FRONTAGE_SIDES, key=lambda side: (scores[side], -FRONTAGE_SIDES.index(side)))


def _opening_cells(vol: Node, side: str, along: int, width: int = 1) -> List[List[int]]:
    width = max(1, int(width))
    half = width // 2
    cells: List[List[int]] = []
    if side in ("front", "back"):
        x0 = max(vol.x0 + 1, along - half)
        x1 = min(vol.x1 - 1, x0 + width - 1)
        x0 = max(vol.x0 + 1, x1 - width + 1)
        z = vol.z0 if side == "front" else vol.z1
        cells = [[x, z] for x in range(x0, x1 + 1)]
    else:
        z0 = max(vol.z0 + 1, along - half)
        z1 = min(vol.z1 - 1, z0 + width - 1)
        z0 = max(vol.z0 + 1, z1 - width + 1)
        x = vol.x0 if side == "west" else vol.x1
        cells = [[x, z] for z in range(z0, z1 + 1)]
    return cells


def _ensure_frontage(graph: MassingGraph, declared: Optional[dict] = None) -> None:
    """Attach the town-frontage contract to graph metadata.

    The vanilla structure NBT has no custom metadata channel, so reports and
    town-planner sidecars consume this graph-level metadata.
    """
    if graph.meta.get("frontage"):
        return
    main = graph.get("main")
    door = graph.meta.get("door", {})
    side = (declared or {}).get("side") or _largest_open_side(graph, main)
    if side not in FRONTAGE_SIDES:
        raise ValueError(f"unknown declared frontage side {side!r}")
    source = "declared" if declared else "largest_open_side"
    door_vol = graph.get(door.get("volume", "main")) if door else main
    door_on_side = door and door.get("volume", "main") == door_vol.id and door.get("wall") == side
    if side in ("front", "back"):
        along = int(door.get("x", (main.x0 + main.x1) // 2)) if door_on_side else (main.x0 + main.x1) // 2
    else:
        along = (main.z0 + main.z1) // 2
    storefront = door_vol.meta.get("storefront", {}) if door_on_side else {}
    opening_width = int((declared or {}).get("width") or storefront.get("width") or 1)
    graph.meta["frontage"] = {
        "side": side,
        "facing": OUTWARD_FACING[side],
        "volume": main.id,
        "kind": "shopfront" if storefront else "entry",
        "opening_cells": _opening_cells(main, side, along, opening_width),
        "opening_width": opening_width,
        "source": source,
    }


def _apply_importance(graph: MassingGraph, style: Style, importance_tier: Optional[int]) -> None:
    if importance_tier is None:
        return
    tier = clamp_importance_tier(importance_tier)
    hint = dict(IMPORTANCE_HINTS[tier])
    graph.meta["importance"] = {
        "tier": tier,
        "height_hint": hint["height"],
        "roof_grade_hint": hint["roof_grade"],
        "dominant_landmark": tier == max(IMPORTANCE_TIERS),
    }

    main = graph.get("main")
    desired_stories = 1
    if tier == 2:
        desired_stories = 2
    elif tier >= 3:
        desired_stories = 3
    current_stories = int(main.meta.get("stories", 1))
    if desired_stories > current_stories:
        story_wall_h = int(main.meta.get("story_wall_h", main.meta["wall_h"]))
        fh = int(main.meta.get("foundation_h", 1))
        main.meta["stories"] = desired_stories
        main.meta["story_wall_h"] = story_wall_h
        main.meta["wall_h"] = desired_stories * story_wall_h
        main.size = (main.size[0], fh + main.meta["wall_h"], main.size[2])

    roof = main.meta.get("roof")
    if roof:
        if tier >= 2:
            roof["overhang"] = max(int(roof.get("overhang", 0)), 1)
        if tier >= 3:
            roof["grade"] = style.allowed_roof_types[-1]
            roof["landmark"] = True


def _rects_overlap(a: Node, b: Node, margin: int = 0) -> bool:
    return not (a.x1 + margin < b.x0 or b.x1 + margin < a.x0 or
                a.z1 + margin < b.z0 or b.z1 + margin < a.z0)


def _free_spot(graph: MassingGraph, node: Node, margin: int = 0) -> bool:
    return all(not _rects_overlap(node, other, margin) for other in graph.nodes
               if other.type != "interior_zone")


def _main_volume(graph: MassingGraph, style: Style, rng: random.Random,
                 tier: str, wall_h: int, fh: int, stories: int = 1,
                 story_wall_h: Optional[int] = None,
                 footprint: Optional[Tuple[int, int]] = None,
                 roof_axis: str = "x",
                 roof_overhang: Optional[int] = None) -> Node:
    story_wall_h = story_wall_h or wall_h
    total_wall_h = stories * story_wall_h
    w, d = footprint or rng.choice(SCALE_TIERS[tier_base(tier)]["footprints"])
    meta = {
        "foundation_h": fh, "wall_h": total_wall_h,
        "wall_type": rng.choice(style.allowed_wall_types),
        "roof": {"type": "gable_roof", "ridge_axis": roof_axis,
                 "overhang": roof_overhang if roof_overhang is not None
                 else rng.choice([0, 1, 1])},
    }
    if stories > 1:
        meta["stories"] = stories
        meta["story_wall_h"] = story_wall_h
    main = Node(
        id="main", type="main_volume", origin=(0, 0, 0),
        size=(w, fh + total_wall_h, d),
        orientation="front", priority=20, tags=["STRUCTURE"],
        meta=meta)
    graph.add(main)
    return main


def _door(graph: MassingGraph, main: Node, rng: random.Random,
          avoid: Tuple[int, int] = None) -> int:
    """Pick the front door x; off-center unless the volume asks to center it."""
    lo, hi = main.x0 + 2, main.x1 - 2
    center = (lo + hi) // 2
    if main.meta.get("door_centered"):
        door_x = center
    else:
        options = [x for x in range(lo, hi + 1) if abs(x - center) <= 2]
        if avoid:
            options = [x for x in options if not (avoid[0] <= x <= avoid[1])] or options
        door_x = rng.choice(options)
    graph.meta["door"] = {"volume": main.id, "wall": "front", "x": door_x}
    return door_x


def _reserve_stairwell(graph: MassingGraph, vol: Node,
                       door_x: Optional[int] = None,
                       preferred_side: Optional[str] = None) -> dict:
    stories = vol.meta.get("stories", 1)
    if stories <= 1:
        return {}
    story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
    # Pagoda stories step inward by their story inset, so the shaft must clear
    # the deepest inset; otherwise the upper-story perimeter walls land on the
    # stair shaft and the protected stair void leaves holes in the outer shell.
    margin = max(vol.meta.get("story_insets", [0]) or [0])
    interior_z0 = vol.z0 + 1 + margin
    interior_z1 = vol.z1 - 1 - margin
    landing_z = interior_z1
    z1 = max(interior_z0, landing_z - 1)
    run = min(story_wall_h, z1 - interior_z0 + 1)
    z0 = max(vol.z0 + 1 + margin, z1 - run + 1)
    west = (vol.x0 + 1 + margin, min(vol.x0 + 2 + margin, vol.x1 - 1 - margin),
            "west")
    east = (max(vol.x1 - 2 - margin, vol.x0 + 1 + margin), vol.x1 - 1 - margin,
            "east")
    choices = [west, east]
    if preferred_side in ("west", "east"):
        choices = [c for c in choices if c[2] == preferred_side] or choices
    if door_x is not None:
        clear = [c for c in choices if not (c[0] - 1 <= door_x <= c[1] + 1)]
        choices = clear or choices
        x0, x1, _ = max(choices, key=lambda c: min(abs(door_x - c[0]), abs(door_x - c[1])))
    else:
        x0, x1, _ = choices[0]
    stair = {"volume": vol.id, "x0": x0, "x1": x1, "z0": z0, "z1": z1,
             "landing_z": landing_z,
             "direction": "south", "stories": stories, "story_wall_h": story_wall_h}
    graph.meta.setdefault("stairwells", {})[vol.id] = stair
    if vol.id == "main":
        graph.meta["stairwell"] = stair
    vol.meta["stairwell"] = stair
    return stair


def reserved_stair_footprint(graph: MassingGraph,
                             volume_id: str = "main") -> Optional[dict]:
    stairwells = graph.meta.get("stairwells", {})
    stair = stairwells.get(volume_id)
    if stair:
        return stair
    stair = graph.meta.get("stairwell")
    if stair and stair.get("volume") == volume_id:
        return stair
    return None


def _mezzanine_meta(main: Node, covers: str, depth: int) -> dict:
    meta = {"covers": covers, "depth": depth, "y_offset": 0}
    main.meta["mezzanine"] = meta
    main.meta["mezzanine_story"] = 1
    main.meta.setdefault("story_meta", {})["1"] = {"mezzanine_story": True}
    return meta


def _tower_volume(graph: MassingGraph, main: Node, side: str,
                  stories_over_main: int, footprint: Tuple[int, int]) -> Node:
    main_stories = main.meta.get("stories", 1)
    stories = main_stories + stories_over_main
    story_wall_h = main.meta.get("story_wall_h", 4)
    fh = main.meta["foundation_h"]
    total_wall_h = stories * story_wall_h
    tw, td = footprint
    if side == "west":
        origin = (main.x0 - tw, 0, main.z0 + max(1, (main.size[2] - td) // 2))
        attached_side = "east"
        roof_axis = "z"
    elif side == "east":
        origin = (main.x1 + 1, 0, main.z0 + max(1, (main.size[2] - td) // 2))
        attached_side = "west"
        roof_axis = "z"
    elif side == "back":
        origin = (main.x0 + max(1, (main.size[0] - tw) // 2), 0, main.z1 + 1)
        attached_side = "front"
        roof_axis = "x"
    else:
        origin = (main.x0 + max(1, (main.size[0] - tw) // 2), 0, main.z0 - td)
        attached_side = "back"
        roof_axis = "x"
    tower = Node(
        id="tower", type="tower_volume", origin=origin,
        size=(tw, fh + total_wall_h, td), attach_to=main.id, side=side,
        priority=20, tags=["STRUCTURE"],
        meta={
            "foundation_h": fh,
            "wall_h": total_wall_h,
            "stories": stories,
            "story_wall_h": story_wall_h,
            "wall_type": main.meta["wall_type"],
            "belfry": True,
            "roof": {"type": "gable_roof", "ridge_axis": roof_axis,
                     "overhang": 1, "attached_side": attached_side},
        })
    graph.add(tower)
    return tower


def _porch(graph: MassingGraph, main: Node, door_x: int, rng: random.Random) -> Node:
    pw = rng.choice([3, 5])
    pd = rng.choice([2, 3])
    x0 = max(main.x0, min(door_x - pw // 2, main.x1 - pw + 1))
    porch = Node(
        id="porch_front", type="porch", origin=(x0, main.y0, main.z0 - pd),
        size=(pw, 4, pd), attach_to="main", side="front",
        priority=70, tags=["DETAIL", "PROTECTED"],
        meta={"door_x": door_x})
    graph.add(porch)
    return porch


def _chimney(graph: MassingGraph, main: Node, rng: random.Random,
             side: Optional[str] = None, node_id: str = "chimney") -> Node:
    fh, wall_h = main.meta["foundation_h"], main.meta["wall_h"]
    wall_top = fh + wall_h - 1
    peak = roof_peak_y(wall_top, main.size[2], main.meta["roof"]["overhang"])
    side = side or rng.choice(["west", "east"])
    cz = main.z0 + main.size[2] // 2 - 1 + rng.choice([-1, 0, 1])
    cz = max(main.z0 + 1, min(cz, main.z1 - 2))

    def wall_clash(cx: int) -> bool:
        """True if column cx sits on another closed volume's wall plane."""
        for other in graph.volumes():
            if other.id in (main.id, node_id):
                continue
            if other.meta.get("open"):
                continue
            if cx not in (other.x0, other.x1):
                continue
            if not (other.z0 <= cz <= other.z1 or other.z0 <= cz + 1 <= other.z1):
                continue
            return True
        return False

    # Place the chimney on the chosen side; if that wall is occupied by an
    # abutting volume, flip to the opposite side, then offset outward as a
    # last resort. The chimney never force-overwrites another volume's wall.
    chosen_side = side
    chosen_cx = None
    for cand_side in (side, "west" if side == "east" else "east"):
        base_cx = main.x0 - 1 if cand_side == "west" else main.x1 + 1
        outward = -1 if cand_side == "west" else 1
        cx = base_cx
        ok = False
        for _ in range(3):
            if not wall_clash(cx):
                ok = True
                break
            cx += outward
        if ok:
            chosen_side = cand_side
            chosen_cx = cx
            break
    if chosen_cx is None:
        chosen_cx = main.x0 - 1 if chosen_side == "west" else main.x1 + 1
    cx = chosen_cx
    chimney = Node(
        id=node_id, type="chimney", origin=(cx, 0, cz),
        size=(1, peak + 3, 2), attach_to="main", side=chosen_side,
        priority=20, tags=["STRUCTURE", "DETAIL", "PROTECTED"])
    graph.add(chimney)
    return chimney


def _path_patch(graph: MassingGraph, main: Node, door_x: int,
                rng: random.Random, front_z: int) -> Node:
    pw, pd = rng.choice([2, 3]), rng.choice([2, 3])
    z0 = front_z - pd
    z1 = front_z - 1
    node = Node(
        id="path_front", type="path_patch",
        origin=(door_x - pw // 2, 0, z0), size=(pw, 1, pd),
        attach_to="main", side="front", priority=70, tags=["DETAIL", "GROUND"],
        meta={
            "door_x": door_x,
            "step_z": main.z0 - 1,
            "step_y": main.meta.get("foundation_h", 1) - 1,
            "entry_z0": z1,
            "entry_z1": main.z0 - 1,
        })
    graph.add(node)
    return node


def _zone(graph: MassingGraph, vol: Node, kind: str,
          region: Tuple[int, int, int, int], y: Optional[int] = None,
          suffix: Optional[str] = None) -> Node:
    fh = vol.meta.get("foundation_h", 1)
    x0, z0, x1, z1 = region
    zone_id = f"zone_{kind}_{vol.id}" if suffix is None else f"zone_{kind}_{vol.id}_{suffix}"
    node = Node(
        id=zone_id, type="interior_zone",
        origin=(x0, y if y is not None else fh, z0),
        size=(x1 - x0 + 1, 2, z1 - z0 + 1),
        attach_to=vol.id, priority=60, tags=["INTERIOR"],
        meta={"kind": kind})
    graph.add(node)
    return node


def _decoration_patches(graph: MassingGraph, main: Node, rng: random.Random,
                        count: int, motifs: List[str]) -> None:
    placed = 0
    candidates = []
    # candidate spots around the building footprint at ground level
    for dx, dz in [(-3, 1), (main.size[0] + 1, 1), (-3, main.size[2] - 3),
                   (main.size[0] + 1, main.size[2] - 3),
                   (1, -4), (main.size[0] - 3, -4),
                   (1, main.size[2] + 1), (main.size[0] - 3, main.size[2] + 1)]:
        candidates.append((main.x0 + dx, main.z0 + dz))
    rng.shuffle(candidates)
    pool = list(motifs)
    rng.shuffle(pool)
    for cx, cz in candidates:
        if placed >= count:
            break
        motif = pool[placed % len(pool)]
        if cz < main.z0:
            facing = "north"
        elif cz > main.z1:
            facing = "south"
        elif cx < main.x0:
            facing = "west"
        else:
            facing = "east"
        node = Node(
            id=f"deco_{placed}", type="decoration_patch",
            origin=(cx, 0, cz), size=(2, 2, 2),
            attach_to="main", priority=70, tags=["DETAIL"],
            meta={"motif": motif, "facing": facing})
        if _free_spot(graph, node, margin=0):
            graph.add(node)
            placed += 1
    graph.meta["decoration_count"] = placed


# ---------------------------------------------------------------------------
# Archetypes
# ---------------------------------------------------------------------------

def build_small_house(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = MassingGraph(meta={"archetype": "small_house", "scale_tier": tier})
    wall_h = rng.choice([3, 4])
    fh = rng.choice([1, 1, 2])
    main = _main_volume(graph, style, rng, tier, wall_h, fh)
    door_x = _door(graph, main, rng)
    porch = _porch(graph, main, door_x, rng)

    if rng.random() < 0.7:
        _chimney(graph, main, rng)
    else:
        graph.meta["window_detail"] = True  # extra shutters instead of chimney

    _path_patch(graph, main, door_x, rng, porch.z0)

    # interior: living zone (larger half) + storage corner
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = ix0 + (ix1 - ix0) * 2 // 3
    if rng.random() < 0.5:
        _zone(graph, main, "living", (ix0, iz0, split, iz1))
        _zone(graph, main, "storage", (split + 1, iz0, ix1, iz1))
    else:
        _zone(graph, main, "living", (split - 1, iz0, ix1, iz1))
        _zone(graph, main, "storage", (ix0, iz0, split - 2, iz1))

    _decoration_patches(graph, main, rng, 2,
                        ["woodpile", "lantern_post", "fence_patch", "barrel_cluster"])
    return graph


def build_medium_house(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = MassingGraph(meta={"archetype": "medium_house", "scale_tier": tier})
    wall_h = rng.choice([4, 4, 5])
    fh = rng.choice([1, 2])
    main = _main_volume(graph, style, rng, tier, wall_h, fh)

    # side wing with its own (perpendicular-ridge) roof => cross gable look
    wing_side = rng.choice(["west", "east"])
    ow = rng.choice([4, 5])               # how far the wing sticks out
    bw = max(5, main.size[2] - rng.choice([2, 3]))  # wing breadth along z
    wing_wall_h = wall_h - 1
    wx0 = main.x0 - ow if wing_side == "west" else main.x1 + 1
    wing = Node(
        id="side_wing", type="side_wing", origin=(wx0, 0, main.z0),
        size=(ow, fh + wing_wall_h, bw), attach_to="main", side=wing_side,
        priority=20, tags=["STRUCTURE"],
        meta={
            "foundation_h": fh, "wall_h": wing_wall_h,
            "wall_type": main.meta["wall_type"],
            "roof": {"type": "gable_roof", "ridge_axis": "z", "overhang": 1,
                     "attached_side": "east" if wing_side == "west" else "west"},
        })
    graph.add(wing)
    main.meta["roof"]["type"] = "cross_gable_roof"

    # door kept on the main volume, away from the wing side
    avoid = None
    door_x = _door(graph, main, rng, avoid)
    porch = _porch(graph, main, door_x, rng)
    _chimney(graph, main, rng,
             side="east" if wing_side == "west" else "west")
    _path_patch(graph, main, door_x, rng, porch.z0)

    # interior: living + work in main, storage in wing
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "living", (ix0, iz0, split, iz1))
    _zone(graph, main, "work", (split + 1, iz0, ix1, iz1))
    _zone(graph, wing, "storage",
          (wing.x0 + 1, wing.z0 + 1, wing.x1 - 1, wing.z1 - 1))

    if tier == "large_lite":
        # rear shed (own lean-to roof) + courtyard patch => >= 3 volumes
        sd = rng.choice([3, 4])
        sw = max(5, main.size[0] // 2)
        sx0 = main.x0 + rng.choice([1, main.size[0] - sw - 1])
        shed = Node(
            id="rear_shed", type="rear_shed", origin=(sx0, 0, main.z1 + 1),
            size=(sw, fh + 3, sd), attach_to="main", side="back",
            priority=20, tags=["STRUCTURE"],
            meta={"foundation_h": fh, "wall_h": 3, "open": False,
                  "roof": {"type": "lean_to_roof", "low_side": "back"}})
        graph.add(shed)
        _zone(graph, shed, "storage",
              (shed.x0 + 1, shed.z0 + 1, shed.x1 - 1, shed.z1 - 1))
        court_x = main.x1 + 2 if wing_side == "west" else main.x0 - 6
        court = Node(
            id="courtyard", type="courtyard_patch",
            origin=(court_x, 0, main.z0 + 2), size=(4, 2, main.size[2] - 3),
            attach_to="main", priority=70, tags=["DETAIL", "GROUND"])
        graph.add(court)

    _decoration_patches(graph, main, rng, 2,
                        ["woodpile", "barrel_cluster", "fence_patch", "lantern_post"])
    return graph


def build_blacksmith(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = MassingGraph(meta={"archetype": "blacksmith", "scale_tier": tier})
    wall_h = 4
    fh = 1
    main = _main_volume(graph, style, rng, tier, wall_h, fh)
    door_x = _door(graph, main, rng)

    shed_side = rng.choice(["west", "east"])
    if tier == "small":
        # small variant: open work area pad in front, no separate shed volume
        ww = rng.choice([3, 4])
        wx0 = main.x0 if door_x > (main.x0 + main.x1) // 2 else main.x1 - ww + 1
        work = Node(
            id="open_work_area", type="shed", origin=(wx0, 0, main.z0 - 4),
            size=(ww, fh + 3, 4), attach_to="main", side="front",
            priority=20, tags=["STRUCTURE", "DETAIL"],
            meta={"foundation_h": fh, "wall_h": 3, "open": True,
                  "work_floor": True,
                  "roof": {"type": "lean_to_roof", "low_side": "front"}})
        graph.add(work)
    else:
        sw = rng.choice([4, 5])
        sd = max(5, main.size[2] - 3)
        sx0 = main.x0 - sw if shed_side == "west" else main.x1 + 1
        work = Node(
            id="work_shed", type="shed", origin=(sx0, 0, main.z0 + 1),
            size=(sw, fh + 3, sd), attach_to="main", side=shed_side,
            priority=20, tags=["STRUCTURE", "DETAIL"],
            meta={"foundation_h": fh, "wall_h": 3, "open": True,
                  "work_floor": True,
                  "roof": {"type": "lean_to_roof",
                           "low_side": shed_side,
                           "attached_side": "east" if shed_side == "west" else "west"}})
        graph.add(work)

    chimney_side = "east" if shed_side == "west" else "west"
    _chimney(graph, main, rng, side=chimney_side)

    porch = _porch(graph, main, door_x, rng) if tier != "small" else None
    front_z = porch.z0 if porch else main.z0
    _path_patch(graph, main, door_x, rng, front_z)

    # interior: forge zone (furnace cluster near chimney) + storage
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    if chimney_side == "west":
        _zone(graph, main, "forge", (ix0, iz0, ix0 + 2, iz1))
        _zone(graph, main, "storage", (ix1 - 1, iz0, ix1, iz1))
    else:
        _zone(graph, main, "forge", (ix1 - 2, iz0, ix1, iz1))
        _zone(graph, main, "storage", (ix0, iz0, ix0 + 1, iz1))
    _zone(graph, work, "smithy",
          (work.x0 + 1, work.z0 + 1, work.x1 - 1, work.z1 - 1))

    if tier == "large_lite":
        sd = 3
        sw = max(4, main.size[0] // 2)
        shed = Node(
            id="rear_shed", type="rear_shed",
            origin=(main.x0 + 1, 0, main.z1 + 1), size=(sw, fh + 3, sd),
            attach_to="main", side="back", priority=20, tags=["STRUCTURE"],
            meta={"foundation_h": fh, "wall_h": 3, "open": False,
                  "roof": {"type": "lean_to_roof", "low_side": "back"}})
        graph.add(shed)
        _zone(graph, shed, "storage",
              (shed.x0 + 1, shed.z0 + 1, shed.x1 - 1, shed.z1 - 1))

    _decoration_patches(graph, main, rng, 2,
                        ["barrel_cluster", "woodpile", "lantern_post"])
    return graph


def build_shop(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    base = tier_base(tier)
    vindex = variant_index(tier)
    variant = SHOP_VARIANTS[base][(vindex - 1) % len(SHOP_VARIANTS[base])]
    stories = 2 if base == "medium_shop" else 1
    graph = MassingGraph(meta={"archetype": base, "scale_tier": tier,
                               "variant_index": vindex})
    fh = 1
    story_wall_h = 4
    main = _main_volume(
        graph, style, rng, base, story_wall_h, fh, stories=stories,
        story_wall_h=story_wall_h, footprint=variant["footprint"],
        roof_axis=variant["roof_axis"], roof_overhang=variant["overhang"])
    main.meta["industry"] = None
    main.meta["storefront"] = {
        "width": variant["storefront_w"],
        "awning": variant["awning"],
        "signage": variant["signage"],
    }

    center = (main.x0 + main.x1) // 2
    requested = center + variant["entrance_shift"]
    avoid = None
    door_x = max(main.x0 + 2, min(requested, main.x1 - 2))
    graph.meta["door"] = {"volume": "main", "wall": "front", "x": door_x}
    _reserve_stairwell(graph, main, door_x)
    _path_patch(graph, main, door_x, rng, main.z0)

    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "work", (ix0, iz0, split, iz1))
    _zone(graph, main, "storage", (split + 1, iz0, ix1, iz1))
    if stories > 1:
        _zone(graph, main, "living", (ix0, iz0 + 1, ix1, iz1))

    shop_motifs = ["barrel_cluster", "lantern_post", "fence_patch"]
    if "market_stall" in style.allowed_motifs and style.has_external_blocks():
        shop_motifs = ["market_stall", "barrel_cluster"]
    _decoration_patches(graph, main, rng, 2, shop_motifs)
    return graph


def build_big_house(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    vindex = variant_index(tier)
    variant = BIG_HOUSE_VARIANTS[(vindex - 1) % len(BIG_HOUSE_VARIANTS)]
    graph = MassingGraph(meta={"archetype": "big_house", "scale_tier": tier,
                               "variant_index": vindex})
    fh = rng.choice([1, 2])
    story_wall_h = 4
    main = _main_volume(
        graph, style, rng, "big_house", story_wall_h, fh,
        stories=variant["stories"], story_wall_h=story_wall_h,
        footprint=variant["footprint"], roof_axis=variant["roof_axis"],
        roof_overhang=variant["overhang"])
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x)
    porch = _porch(graph, main, door_x, rng)
    _path_patch(graph, main, door_x, rng, porch.z0)

    wing_side = variant["wing"]
    if wing_side:
        ow = rng.choice([4, 5])
        bw = max(5, main.size[2] - rng.choice([2, 3]))
        wx0 = main.x0 - ow if wing_side == "west" else main.x1 + 1
        wing = Node(
            id="side_wing", type="side_wing", origin=(wx0, 0, main.z0 + 1),
            size=(ow, fh + story_wall_h, bw), attach_to="main", side=wing_side,
            priority=20, tags=["STRUCTURE"],
            meta={
                "foundation_h": fh, "wall_h": story_wall_h,
                "wall_type": main.meta["wall_type"],
                "roof": {"type": "gable_roof", "ridge_axis": "z", "overhang": 1,
                         "attached_side": "east" if wing_side == "west" else "west"},
            })
        graph.add(wing)
        main.meta["roof"]["type"] = "cross_gable_roof"
        _zone(graph, wing, "storage",
              (wing.x0 + 1, wing.z0 + 1, wing.x1 - 1, wing.z1 - 1))

    _chimney(graph, main, rng, side=rng.choice(["west", "east"]))

    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "living", (ix0, iz0, split, iz1))
    _zone(graph, main, "work", (split + 1, iz0, ix1, iz1))
    _zone(graph, main, "storage", (ix0, iz1 - 1, ix1, iz1))
    _decoration_patches(graph, main, rng, 3,
                        ["woodpile", "barrel_cluster", "fence_patch", "lantern_post"])
    return graph


def _half_plane_regions(main: Node, covers: str, depth: int) -> Tuple[Tuple[int, int, int, int], Tuple[int, int, int, int]]:
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    if covers == "west":
        covered = (ix0, iz0, min(ix1, main.x0 + depth), iz1)
        open_region = (min(ix1, main.x0 + depth) + 1, iz0, ix1, iz1)
    else:
        covered = (max(ix0, main.x1 - depth), iz0, ix1, iz1)
        open_region = (ix0, iz0, max(ix0, main.x1 - depth) - 1, iz1)
    if open_region[0] > open_region[2]:
        mid = (ix0 + ix1) // 2
        covered = (ix0, iz0, mid, iz1) if covers == "west" else (mid + 1, iz0, ix1, iz1)
        open_region = (mid + 1, iz0, ix1, iz1) if covers == "west" else (ix0, iz0, mid, iz1)
    return covered, open_region


def build_tavern(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    vindex = variant_index(tier)
    variant = TAVERN_VARIANTS[(vindex - 1) % len(TAVERN_VARIANTS)]
    graph = MassingGraph(meta={"archetype": "tavern", "scale_tier": tier,
                               "variant_index": vindex,
                               "civic_family": True})
    fh = 1
    story_wall_h = 5
    main = _main_volume(
        graph, style, rng, "tavern", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=variant["footprint"], roof_axis=variant["roof_axis"],
        roof_overhang=variant["overhang"])
    main.type = "great_hall_volume"
    main.meta["wall_type"] = "timber_frame_wall"
    main.meta["entry_signage"] = True

    covers = variant["mezzanine"]
    depth = max(4, main.size[0] // 2)
    _mezzanine_meta(main, covers, depth)
    center = (main.x0 + 2 + main.x1 - 2) // 2
    door_x = max(main.x0 + 2, min(center + variant.get("door_shift", 0), main.x1 - 2))
    graph.meta["door"] = {"volume": main.id, "wall": "front", "x": door_x}
    preferred_stair = "east" if covers == "west" else "west"
    _reserve_stairwell(graph, main, door_x, preferred_side=preferred_stair)
    _path_patch(graph, main, door_x, rng, main.z0)
    _chimney(graph, main, rng, side="west" if covers == "east" else "east")

    covered, open_region = _half_plane_regions(main, covers, depth)
    upper_y = fh + story_wall_h + 1
    _zone(graph, main, "tavern_hall", open_region, suffix="ground")
    _zone(graph, main, "tavern_inn", covered, y=upper_y, suffix="mezzanine")

    if variant.get("stable"):
        sw = 5
        sd = max(6, main.size[2] - 3)
        side = "west" if covers == "west" else "east"
        sx0 = main.x0 - sw if side == "west" else main.x1 + 1
        sz0 = main.z0 + 2
        stable = Node(
            id="stable_annex", type="shed", origin=(sx0, 0, sz0),
            size=(sw, fh + 3, sd), attach_to="main", side=side,
            priority=20, tags=["STRUCTURE", "DETAIL"],
            meta={"foundation_h": fh, "wall_h": 3, "open": True,
                  "stable": True, "hay_floor": True,
                  "roof": {"type": "lean_to_roof", "low_side": side,
                           "attached_side": "east" if side == "west" else "west"}})
        if _free_spot(graph, stable, margin=0):
            graph.add(stable)
            _zone(graph, stable, "stable",
                  (stable.x0 + 1, stable.z0 + 1, stable.x1 - 1, stable.z1 - 1),
                  suffix="annex")
    _decoration_patches(graph, main, rng, 3,
                        ["barrel_cluster", "lantern_post", "fence_patch", "woodpile"])
    return graph


def build_lord_manor(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    vindex = variant_index(tier)
    variant = LORD_MANOR_VARIANTS[(vindex - 1) % len(LORD_MANOR_VARIANTS)]
    graph = MassingGraph(meta={"archetype": "lord_manor", "scale_tier": tier,
                               "variant_index": vindex,
                               "civic_family": True})
    fh = 1
    story_wall_h = 4
    main = _main_volume(
        graph, style, rng, "lord_manor", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=variant["footprint"], roof_axis=variant["roof_axis"],
        roof_overhang=variant["overhang"])
    main.meta["wall_type"] = "timber_frame_wall"
    main.meta["door_centered"] = True
    main.meta["entry_signage"] = True
    main.meta["entry_heraldry"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x)
    _path_patch(graph, main, door_x, rng, main.z0)

    tower = _tower_volume(
        graph, main, variant["tower_side"], variant["tower_stories_over_main"],
        variant["tower_footprint"])
    _reserve_stairwell(graph, tower, preferred_side="west")

    wing_side = variant.get("wing")
    if wing_side:
        ow = 4
        bw = max(7, main.size[2] - 4)
        wx0 = main.x0 - ow if wing_side == "west" else main.x1 + 1
        wing = Node(
            id="side_wing", type="side_wing", origin=(wx0, 0, main.z0 + 2),
            size=(ow, fh + story_wall_h, bw), attach_to="main", side=wing_side,
            priority=20, tags=["STRUCTURE"],
            meta={
                "foundation_h": fh, "wall_h": story_wall_h,
                "wall_type": main.meta["wall_type"],
                "roof": {"type": "gable_roof", "ridge_axis": "z", "overhang": 1,
                         "attached_side": "east" if wing_side == "west" else "west"},
            })
        if _free_spot(graph, wing, margin=0):
            graph.add(wing)
            _zone(graph, wing, "storage",
                  (wing.x0 + 1, wing.z0 + 1, wing.x1 - 1, wing.z1 - 1))

    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    upper_y = fh + story_wall_h + 1
    _zone(graph, main, "town_foyer", (ix0, iz0, ix1, iz1), suffix="ground")
    _zone(graph, main, "town_chamber", (ix0, iz0, split, iz1),
          y=upper_y, suffix="upper")
    quarters = _zone(graph, main, "living", (split + 1, iz0, ix1, iz1),
                     y=upper_y, suffix="quarters")
    quarters.meta["private_quarters"] = True
    _decoration_patches(graph, main, rng, 3,
                        ["lantern_post", "fence_patch", "small_path_patch", "woodpile"])
    return graph


def build_small_shop(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    return build_shop(style, rng, tier)


def build_medium_shop(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    return build_shop(style, rng, tier)


def _chinese_graph(archetype: str, tier: str) -> MassingGraph:
    return MassingGraph(meta={
        "archetype": archetype,
        "scale_tier": tier,
        "style_family": "chinese_courtyard",
    })


# Vernacular bay (开间) naming, outward from the central 明间.
CHINESE_BAY_NAMES = ("明间", "次间", "梢间", "尽间")


def _set_chinese_roof(main: Node, form: str, axis: str = "x") -> None:
    """Route a Chinese sub-building's roof through the form registry by name.

    ``form`` is one of the four registered vernacular forms; the variant-level
    ``roof_grade`` (compound.CHINESE_ROOF_FORMS) overrides this later via
    ``_apply_roof_grade``. No string-prefix dispatch: the form name IS the
    registry key consumed by ``passes.roof_pass``.
    """
    roof = {"type": form, "ridge_axis": axis, "overhang": 1, "grade": form}
    if form == "chinese_overhang_gable":
        roof["gable_overhang"] = 2
    main.meta["roof"] = roof


def _chinese_bay_count(span: int) -> int:
    """开间 count from the facade span — 3 / 5 / 7, always odd."""
    if span >= 15:
        return 7
    if span >= 11:
        return 5
    return 3


def _chinese_bay_zones(graph: MassingGraph, main: Node, center_kind: str,
                       flank_kind: str, axis: str = "x",
                       bays: Optional[int] = None,
                       y: Optional[int] = None) -> int:
    """Lay the 3/5/7-bay interior zone pattern (明间 / 次间 / 梢间 / 尽间).

    The bays are placed as furnishing zones inside the single open volume (not a
    separately-traversable timber frame); the central 明间 carries the principal
    function, flanking bays the subordinate one. ``axis`` follows the ridge so
    the bays run along the facade.
    """
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    lo, hi = (ix0, ix1) if axis == "x" else (iz0, iz1)
    span = hi - lo + 1
    if bays is None:
        bays = _chinese_bay_count(span)
    bays = max(1, min(bays, span))
    edges = [lo + round(i * span / bays) for i in range(bays + 1)]
    mid = bays // 2
    for i in range(bays):
        b0, b1 = edges[i], edges[i + 1] - 1
        if b1 < b0:
            continue
        dist = abs(i - mid)
        kind = center_kind if dist == 0 else flank_kind
        name = CHINESE_BAY_NAMES[min(dist, len(CHINESE_BAY_NAMES) - 1)]
        region = (b0, iz0, b1, iz1) if axis == "x" else (ix0, b0, ix1, b1)
        zone = _zone(graph, main, kind, region, y=y, suffix=f"bay{i}")
        zone.meta["bay_name"] = name
    graph.meta["bay_count"] = bays
    return bays


# 开间 (main_bays) -> main-hall footprint (width, depth). The width widens with
# the bay count so the silhouette reads the requested 形制; odd widths keep a
# clean central 明间 and ridge line.
MAIN_HALL_BAY_FOOTPRINT = {3: (13, 11), 5: (15, 11), 7: (17, 13)}


def build_main_hall(style: Style, rng: random.Random, tier: str,
                    overrides: Optional[dict] = None) -> MassingGraph:
    graph = _chinese_graph("main_hall", tier)
    fh = 2  # 台基: a 2-tall stone plinth raises the principal hall
    story_wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    bays = (overrides or {}).get("main_bays")
    if bays in MAIN_HALL_BAY_FOOTPRINT:
        footprint = MAIN_HALL_BAY_FOOTPRINT[bays]
    else:
        footprint = rng.choice(SCALE_TIERS["main_hall"]["footprints"])
        bays = None
    main = _main_volume(
        graph, style, rng, "main_hall", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=footprint,
        roof_axis="x", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    main.meta["door_centered"] = True
    main.meta["entry_signage"] = True  # central-bay 匾 (see plaque_bindings.json)
    _set_chinese_roof(main, roof_grade, axis="x")
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x)
    _cultivation_platform(graph, main, door_x, pad=2, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "work", "living", axis="x", bays=bays)
    graph.meta["roof_grade"] = roof_grade
    return graph


def build_side_wing(style: Style, rng: random.Random, tier: str,
                    overrides: Optional[dict] = None) -> MassingGraph:
    graph = _chinese_graph("side_wing", tier)
    fh = 1
    wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    main = _main_volume(
        graph, style, rng, "side_wing", wall_h, fh,
        footprint=rng.choice(SCALE_TIERS["side_wing"]["footprints"]),
        roof_axis="z", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    _set_chinese_roof(main, roof_grade, axis="z")
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "living", "storage", axis="z")
    graph.meta["roof_grade"] = roof_grade
    return graph


def build_front_row(style: Style, rng: random.Random, tier: str,
                    overrides: Optional[dict] = None) -> MassingGraph:
    graph = _chinese_graph("front_row", tier)
    fh = 1
    wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    main = _main_volume(
        graph, style, rng, "front_row", wall_h, fh,
        footprint=rng.choice(SCALE_TIERS["front_row"]["footprints"]),
        roof_axis="x", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    _set_chinese_roof(main, roof_grade, axis="x")
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "work", "storage", axis="x")
    graph.meta["roof_grade"] = roof_grade
    return graph


def build_gate_house(style: Style, rng: random.Random, tier: str,
                     overrides: Optional[dict] = None) -> MassingGraph:
    graph = _chinese_graph("gate_house", tier)
    fh = 1
    wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    main = _main_volume(
        graph, style, rng, "gate_house", wall_h, fh,
        footprint=rng.choice(SCALE_TIERS["gate_house"]["footprints"]),
        roof_axis="x", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    main.meta["door_centered"] = True
    _set_chinese_roof(main, roof_grade, axis="x")
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "storage", "storage", axis="x")
    graph.meta["roof_grade"] = roof_grade
    return graph


def build_open_hall(style: Style, rng: random.Random, tier: str,
                    overrides: Optional[dict] = None) -> MassingGraph:
    """敞厅 (open hall): front facade open — columns + eave, no full-height wall.

    Marks ``open_fronted=True`` and ``facade_slot="FACADE_OPEN"`` on the main
    volume so the compound layout pass can strip the front wall row and replace
    it with standoff columns via the FACADE_OPEN style slot.
    """
    graph = _chinese_graph("open_hall", tier)
    fh = 1
    story_wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    bays = (overrides or {}).get("open_hall_bays")
    if bays in MAIN_HALL_BAY_FOOTPRINT:
        footprint = MAIN_HALL_BAY_FOOTPRINT[bays]
    else:
        footprint = rng.choice(SCALE_TIERS["open_hall"]["footprints"])
        bays = None
    main = _main_volume(
        graph, style, rng, "open_hall", story_wall_h, fh,
        stories=1, story_wall_h=story_wall_h,
        footprint=footprint, roof_axis="x", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    main.meta["door_centered"] = True
    main.meta["open_fronted"] = True
    main.meta["facade_slot"] = "FACADE_OPEN"
    main.meta["entry_signage"] = True
    _set_chinese_roof(main, roof_grade, axis="x")
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "work", "living", axis="x", bays=bays)
    graph.meta["roof_grade"] = roof_grade
    graph.meta["open_fronted"] = True
    return graph


def build_tower_house(style: Style, rng: random.Random, tier: str,
                      overrides: Optional[dict] = None) -> MassingGraph:
    """绣楼 / 藏书楼 (tower house): 2-story off-axis rear building.

    Reuses ``multi-story-massing``: stories=2 + floor slab + stairwell per
    the ``_reserve_stairwell`` helper. The footprint is narrower than
    main_hall so it fits off-axis in the 后院.
    """
    graph = _chinese_graph("tower_house", tier)
    fh = 1
    story_wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    footprint = rng.choice(SCALE_TIERS["tower_house"]["footprints"])
    main = _main_volume(
        graph, style, rng, "tower_house", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=footprint, roof_axis="z", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    main.meta["off_axis"] = True
    _set_chinese_roof(main, roof_grade, axis="z")
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x)
    _cultivation_platform(graph, main, door_x, pad=1, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "living", "storage", axis="z")
    graph.meta["roof_grade"] = roof_grade
    graph.meta["stories"] = 2
    return graph


def build_flower_hall(style: Style, rng: random.Random, tier: str,
                      overrides: Optional[dict] = None) -> MassingGraph:
    """花厅 (flower hall): smaller secondary hall for garden-side entertaining."""
    graph = _chinese_graph("flower_hall", tier)
    fh = 1
    wall_h = 4
    roof_grade = rng.choice(style.allowed_roof_types)
    main = _main_volume(
        graph, style, rng, "flower_hall", wall_h, fh,
        footprint=rng.choice(SCALE_TIERS["flower_hall"]["footprints"]),
        roof_axis="x", roof_overhang=1)
    main.meta["wall_type"] = "white_plaster_timber_wall"
    _set_chinese_roof(main, roof_grade, axis="x")
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1, height=fh)
    _cultivation_colonnade(graph, main, door_x, sides=("front",))
    _path_patch(graph, main, door_x, rng, main.z0)
    _chinese_bay_zones(graph, main, "work", "living", axis="x")
    graph.meta["roof_grade"] = roof_grade
    return graph


def _retag_graph(graph: MassingGraph, archetype: str, tier: str,
                 style_family: str) -> MassingGraph:
    graph.meta["archetype"] = archetype
    graph.meta["scale_tier"] = tier
    graph.meta["style_family"] = style_family
    return graph


def _cultivation_graph(archetype: str, tier: str, family: str) -> MassingGraph:
    return MassingGraph(meta={
        "archetype": archetype,
        "scale_tier": tier,
        "style_family": family,
        "cultivation_form": True,
    })


def _sect_graph(archetype: str, tier: str) -> MassingGraph:
    return _cultivation_graph(archetype, tier, "cultivation_sect")


def _town_cultivation_graph(archetype: str, tier: str) -> MassingGraph:
    return _cultivation_graph(archetype, tier, "cultivation_town")


def _set_roof(vol: Node, roof_type: str, axis: str = "x", overhang: int = 2,
              footprint_inset: int = 0) -> None:
    vol.meta["roof"] = {
        "type": roof_type,
        "ridge_axis": axis,
        "overhang": overhang,
    }
    if footprint_inset:
        vol.meta["roof"]["footprint_inset"] = footprint_inset


def _cultivation_main(graph: MassingGraph, style: Style, rng: random.Random,
                      archetype: str, wall_h: int, fh: int,
                      stories: int = 1, story_wall_h: Optional[int] = None,
                      footprint: Optional[Tuple[int, int]] = None,
                      roof_type: str = "sweeping_eave_roof",
                      roof_axis: str = "x", roof_overhang: int = 2,
                      wall_type: str = "white_plaster_timber_wall") -> Node:
    main = _main_volume(
        graph, style, rng, archetype, wall_h, fh,
        stories=stories, story_wall_h=story_wall_h,
        footprint=footprint or rng.choice(SCALE_TIERS[archetype]["footprints"]),
        roof_axis=roof_axis, roof_overhang=roof_overhang)
    main.meta["wall_type"] = wall_type
    _set_roof(main, roof_type, roof_axis, roof_overhang)
    return main


def _cultivation_platform(graph: MassingGraph, main: Node, door_x: int,
                          pad: int = 2, height: Optional[int] = None) -> Node:
    # The 台基 apron must not rise above the building's own foundation, or it
    # would cap the interior floor (zone y0 == foundation_h) with platform
    # stone and block furniture. Clamp to the foundation height.
    height = height if height is not None else max(1, int(main.meta["foundation_h"]))
    node = Node(
        id=f"{main.id}_platform",
        type="platform",
        origin=(main.x0 - pad, 0, main.z0 - pad),
        size=(main.size[0] + pad * 2, height, main.size[2] + pad * 2),
        attach_to=main.id,
        priority=15,
        tags=["FOUNDATION", "STRUCTURE"],
        meta={"height": height, "door_x": door_x, "facing": "south"},
    )
    graph.add(node)
    graph.meta["has_platform"] = True
    return node


def _cultivation_colonnade(graph: MassingGraph, main: Node, door_x: int,
                           sides: Tuple[str, ...] = ("front",)) -> Node:
    node = Node(
        id=f"{main.id}_colonnade",
        type="colonnade",
        origin=(main.x0 - 2, 0, main.z0 - 2),
        size=(main.size[0] + 4, main.size[1], main.size[2] + 4),
        attach_to=main.id,
        priority=70,
        tags=["DETAIL", "STRUCTURE"],
        meta={
            "sides": list(sides),
            "door_x": door_x,
            "base_y": max(0, main.meta["foundation_h"] - 1),
        },
    )
    graph.add(node)
    graph.meta["has_colonnade"] = True
    return node


def _cultivation_balustrade(graph: MassingGraph, main: Node,
                            story: int = 1) -> Optional[Node]:
    if main.meta.get("stories", 1) <= story:
        return None
    y = main.meta["foundation_h"] + story * main.meta.get("story_wall_h", main.meta["wall_h"])
    node = Node(
        id=f"{main.id}_balustrade_{story}",
        type="balustrade",
        origin=(main.x0 - 1, y, main.z0 - 1),
        size=(main.size[0] + 2, 1, main.size[2] + 2),
        attach_to=main.id,
        priority=70,
        tags=["DETAIL", "STRUCTURE"],
        meta={"gap_wall": "front", "gap_center": (main.x0 + main.x1) // 2},
    )
    graph.add(node)
    graph.meta["has_balustrade"] = True
    return node


def _cultivation_path(graph: MassingGraph, main: Node, door_x: int,
                      rng: random.Random) -> None:
    _path_patch(graph, main, door_x, rng, main.z0)


def _cultivation_deco(graph: MassingGraph, motif: str,
                      origin: Tuple[int, int, int],
                      size: Tuple[int, int, int],
                      facing: Optional[str] = None) -> Node:
    index = len(graph.by_type("decoration_patch"))
    node = Node(
        id=f"cultivation_deco_{motif}_{index}",
        type="decoration_patch",
        origin=origin,
        size=size,
        attach_to="main",
        priority=70,
        tags=["DETAIL"],
        meta={"motif": motif},
    )
    if facing:
        node.meta["facing"] = facing
    graph.add(node)
    return node


def _cultivation_side_wing(graph: MassingGraph, main: Node, anc: "CultivationAncillary",
                           index: int, wall_type: str) -> Node:
    """厢房/摊廊: a subordinate volume on the west/east side carrying its own
    transverse sweeping eave (双脊 read), reusing the build_medium_house side
    attachment but staying within the cultivation roof-form grammar."""
    fh = main.meta["foundation_h"]
    out = anc.out
    breadth = anc.breadth or max(5, main.size[2] - 2)
    breadth = min(breadth, main.size[2])
    wx0 = main.x0 - out if anc.side == "west" else main.x1 + 1
    wz0 = main.z0 + max(0, (main.size[2] - breadth) // 2)
    wing = Node(
        id=f"side_wing_{index}", type="side_wing", origin=(wx0, 0, wz0),
        size=(out, fh + anc.wall_h, breadth), attach_to="main", side=anc.side,
        priority=20, tags=["STRUCTURE"],
        meta={
            "foundation_h": fh, "wall_h": anc.wall_h, "wall_type": wall_type,
            "roof": {"type": "sweeping_eave_roof", "ridge_axis": "z", "overhang": 1,
                     "attached_side": "east" if anc.side == "west" else "west"},
        })
    graph.add(wing)
    _zone(graph, wing, anc.zone,
          (wing.x0 + 1, wing.z0 + 1, wing.x1 - 1, wing.z1 - 1))
    return wing


def _cultivation_rear_shed(graph: MassingGraph, main: Node, anc: "CultivationAncillary",
                           index: int, wall_type: str) -> Node:
    """后罩房/作坊: an enclosed annex across the back, reusing the
    build_medium_house rear_shed attachment but carrying its own low sweeping
    eave (ridge along x) to stay within the cultivation roof-form grammar."""
    fh = main.meta["foundation_h"]
    width = anc.breadth or max(5, main.size[0] - 2)
    width = min(width, main.size[0])
    depth = anc.out
    sx0 = main.x0 + max(0, (main.size[0] - width) // 2)
    shed = Node(
        id=f"rear_shed_{index}", type="rear_shed",
        origin=(sx0, 0, main.z1 + 1),
        size=(width, fh + anc.wall_h, depth), attach_to="main", side="back",
        priority=20, tags=["STRUCTURE"],
        meta={"foundation_h": fh, "wall_h": anc.wall_h, "wall_type": wall_type,
              "open": False,
              "roof": {"type": "sweeping_eave_roof", "ridge_axis": "x",
                       "overhang": 1, "attached_side": "back"}})
    graph.add(shed)
    _zone(graph, shed, anc.zone,
          (shed.x0 + 1, shed.z0 + 1, shed.x1 - 1, shed.z1 - 1))
    return shed


def _cultivation_courtyard(graph: MassingGraph, main: Node, depth: int = 5) -> Node:
    """后院: a walled rear courtyard (院墙 ring + courtyard ground patch) behind
    the deepest back element. A plan-level element only -- it adds no silhouette
    volume, so callers pair it with a rear_shed. The 院墙 wall ring is rendered
    by ops.courtyard_enclosure; the floor reuses the courtyard_patch ground op."""
    back_z = main.z1
    for shed in graph.by_type("rear_shed"):
        back_z = max(back_z, shed.z1)
    # span the main footprint width, inset one cell from each side
    cx0 = main.x0 + 1
    width = max(5, main.size[0] - 2)
    cz0 = back_z + 1
    gate_x = cx0 + width // 2
    patch = Node(
        id="rear_courtyard", type="courtyard_patch",
        origin=(cx0, 0, cz0), size=(width, 2, depth),
        attach_to="main", priority=70, tags=["DETAIL", "GROUND"])
    graph.add(patch)
    enclosure = Node(
        id="rear_courtyard_wall", type="courtyard_enclosure",
        origin=(cx0, 0, cz0), size=(width, 2, depth),
        attach_to="main", priority=68, tags=["DETAIL", "STRUCTURE"],
        meta={"gate_wall": "front", "gate_center": gate_x, "height": 2})
    graph.add(enclosure)
    graph.meta["has_rear_courtyard"] = True
    return enclosure


def _apply_cultivation_ancillaries(graph: MassingGraph, main: Node,
                                   template: "CultivationTemplate",
                                   wall_type: str) -> None:
    """Materialise a template's ordered ancillary volumes / 后院 onto the graph."""
    for index, anc in enumerate(template.ancillaries):
        if anc.kind == "side_wing":
            _cultivation_side_wing(graph, main, anc, index, wall_type)
        elif anc.kind == "rear_shed":
            _cultivation_rear_shed(graph, main, anc, index, wall_type)
        elif anc.kind == "courtyard":
            _cultivation_courtyard(graph, main)
        else:
            raise ValueError(f"unknown cultivation ancillary kind {anc.kind!r}")


def _alchemy_furnace_node(graph: MassingGraph, main: Node) -> Node:
    cx = (main.x0 + main.x1) // 2 - 1
    cz = (main.z0 + main.z1) // 2 - 1
    node = Node(
        id="alchemy_furnace",
        type="alchemy_furnace",
        origin=(cx, main.meta["foundation_h"], cz),
        size=(3, 3, 3),
        attach_to=main.id,
        priority=70,
        tags=["INTERIOR", "DETAIL", "PROTECTED"],
    )
    graph.add(node)
    graph.meta["has_alchemy_furnace"] = True
    return node


def build_cultivation_house(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _town_cultivation_graph("cultivation_house", tier)
    tmpl = cultivation_template("cultivation_house", tier)
    wall_type = "white_plaster_timber_wall"
    main = _cultivation_main(
        graph, style, rng, "cultivation_house",
        wall_h=tmpl.wall_h, fh=tmpl.fh,
        footprint=tmpl.footprint,
        roof_type=tmpl.roof_type, roof_axis=tmpl.ridge_axis,
        roof_overhang=tmpl.roof_overhang, wall_type=wall_type)
    main.meta["door_centered"] = True
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1)
    _cultivation_colonnade(graph, main, door_x)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "living", (ix0, iz0, split, iz1))
    _zone(graph, main, "storage", (split + 1, iz0, ix1, iz1))
    _apply_cultivation_ancillaries(graph, main, tmpl, wall_type)
    _cultivation_deco(graph, "lantern_post", (main.x1 - 1, 0, main.z0 - 2), (1, 3, 1))
    return graph


def build_cultivation_shop(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _town_cultivation_graph("cultivation_shop", tier)
    tmpl = cultivation_template("cultivation_shop", tier)
    wall_type = "mixed_stone_wood_wall"
    main = _cultivation_main(
        graph, style, rng, "cultivation_shop",
        wall_h=tmpl.wall_h, fh=tmpl.fh,
        footprint=tmpl.footprint,
        roof_type=tmpl.roof_type, roof_axis=tmpl.ridge_axis,
        roof_overhang=tmpl.roof_overhang, wall_type=wall_type)
    main.meta["door_centered"] = True
    main.meta["storefront"] = {"width": min(7, main.size[0] - 4), "signage": "beam"}
    main.meta["entry_signage"] = True
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=1)
    _cultivation_colonnade(graph, main, door_x)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    _zone(graph, main, "work", (ix0, iz0, (ix0 + ix1) // 2, iz1))
    _zone(graph, main, "storage", ((ix0 + ix1) // 2 + 1, iz0, ix1, iz1))
    _apply_cultivation_ancillaries(graph, main, tmpl, wall_type)
    _cultivation_deco(graph, "market_stall", (main.x0 + 2, 0, main.z0 - 5), (2, 3, 2), "north")
    return graph


def build_cultivation_inn(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _town_cultivation_graph("cultivation_inn", tier)
    tmpl = cultivation_template("cultivation_inn", tier)
    wall_type = "white_plaster_timber_wall"
    main = _cultivation_main(
        graph, style, rng, "cultivation_inn",
        wall_h=tmpl.wall_h, fh=tmpl.fh,
        stories=tmpl.stories, story_wall_h=tmpl.story_wall_h,
        footprint=tmpl.footprint,
        roof_type=tmpl.roof_type, roof_axis=tmpl.ridge_axis,
        roof_overhang=tmpl.roof_overhang, wall_type=wall_type)
    main.meta["door_centered"] = True
    main.meta["entry_signage"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="east")
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x, sides=("front", "back"))
    _cultivation_balustrade(graph, main)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "living", (ix0, iz0, split, iz1), suffix="hall")
    _zone(graph, main, "storage", (split + 1, iz0, ix1, iz1), suffix="stores")
    upper = _zone(graph, main, "living", (ix0, iz0, ix1, iz1),
                  y=main.meta["foundation_h"] + main.meta["story_wall_h"] + 1,
                  suffix="upper")
    upper.meta["private_quarters"] = True
    _apply_cultivation_ancillaries(graph, main, tmpl, wall_type)
    _cultivation_deco(graph, "lantern_post", (main.x1 - 1, 0, main.z0 - 3), (1, 3, 1))
    return graph


def build_cultivation_market(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _town_cultivation_graph("cultivation_market", tier)
    tmpl = cultivation_template("cultivation_market", tier)
    wall_type = "mixed_stone_wood_wall"
    main = _cultivation_main(
        graph, style, rng, "cultivation_market",
        wall_h=tmpl.wall_h, fh=tmpl.fh,
        footprint=tmpl.footprint,
        roof_type=tmpl.roof_type, roof_axis=tmpl.ridge_axis,
        roof_overhang=tmpl.roof_overhang, wall_type=wall_type)
    main.meta["door_centered"] = True
    main.meta["storefront"] = {"width": min(9, main.size[0] - 4), "signage": "beam"}
    main.meta["entry_signage"] = True
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    _zone(graph, main, "work", (ix0, iz0, (ix0 + ix1) // 2, iz1))
    _zone(graph, main, "storage", ((ix0 + ix1) // 2 + 1, iz0, ix1, iz1))
    _apply_cultivation_ancillaries(graph, main, tmpl, wall_type)
    _cultivation_deco(graph, "market_stall", (main.x0 + 1, 0, main.z0 - 5), (2, 3, 2), "north")
    _cultivation_deco(graph, "market_stall", (main.x1 - 2, 0, main.z0 - 5), (2, 3, 2), "north")
    return graph


def build_town_shrine(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _town_cultivation_graph("town_shrine", tier)
    graph.meta["requires_platform_colonnade"] = True
    main = _cultivation_main(
        graph, style, rng, "town_shrine", wall_h=5, fh=2,
        stories=2, story_wall_h=5,
        footprint=rng.choice(SCALE_TIERS["town_shrine"]["footprints"]),
        roof_type="tiered_eave_roof", roof_axis="x", roof_overhang=2,
        wall_type="white_plaster_timber_wall")
    main.meta["door_centered"] = True
    main.meta["entry_heraldry"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="west")
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x, sides=("front", "back"))
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "town_foyer", (ix0, iz0, split, iz1), suffix="foyer")
    _zone(graph, main, "town_chamber", (split + 1, iz0, ix1, iz1),
          y=main.meta["foundation_h"] + main.meta["story_wall_h"] + 1,
          suffix="upper")
    _cultivation_deco(graph, "incense_altar", ((main.x0 + main.x1) // 2 - 1, 0, main.z0 - 3), (3, 2, 1))
    _cultivation_deco(graph, "spirit_array", ((main.x0 + main.x1) // 2 - 2, 0, main.z0 - 8), (5, 1, 5))
    return graph


def build_pagoda(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    """Tall cultivation pagoda landmark (塔): a 3-storey stepped tower crowned by
    the registered ``pagoda`` roof form (tiered flying eaves + finial spire)."""
    graph = _town_cultivation_graph("pagoda", tier)
    graph.meta["requires_platform_colonnade"] = True
    graph.meta["vertical_landmark"] = True
    fh = 2
    story_wall_h = 4
    main = _cultivation_main(
        graph, style, rng, "pagoda", story_wall_h, fh,
        stories=3, story_wall_h=story_wall_h,
        footprint=rng.choice(SCALE_TIERS["pagoda"]["footprints"]),
        roof_type="pagoda", roof_axis="x", roof_overhang=2,
        wall_type="white_plaster_timber_wall")
    main.type = "tower_volume"
    main.meta["story_insets"] = [0, 1, 2]
    main.meta["roof"]["footprint_inset"] = 2
    main.meta["roof"]["spire_height"] = 4
    graph.meta["pagoda_story_insets"] = [0, 1, 2]
    main.meta["door_centered"] = True
    main.meta["entry_heraldry"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="west")
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x, sides=("front", "back"))
    _cultivation_balustrade(graph, main)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    _zone(graph, main, "work", (ix0, iz0, ix1, iz1), suffix="meditation")
    upper = _zone(graph, main, "living", (ix0, iz0, ix1, iz1),
                  y=fh + story_wall_h + 1, suffix="relics")
    upper.meta["private_quarters"] = True
    _cultivation_deco(graph, "incense_altar",
                      ((main.x0 + main.x1) // 2 - 1, 0, main.z0 - 3), (3, 2, 1))
    return graph


def build_pavilion(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    """Cultivation pavilion landmark (楼阁): a 2-storey viewing hall crowned by
    the registered ``pavilion`` roof form (wide sweeping eave)."""
    graph = _town_cultivation_graph("pavilion", tier)
    graph.meta["requires_platform_colonnade"] = True
    graph.meta["vertical_landmark"] = True
    fh = 2
    story_wall_h = 4
    main = _cultivation_main(
        graph, style, rng, "pavilion", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=rng.choice(SCALE_TIERS["pavilion"]["footprints"]),
        roof_type="pavilion", roof_axis="x", roof_overhang=3,
        wall_type="white_plaster_timber_wall")
    main.meta["door_centered"] = True
    main.meta["entry_signage"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="east")
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x, sides=("front", "back"))
    _cultivation_balustrade(graph, main)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    _zone(graph, main, "living", (ix0, iz0, ix1, iz1), suffix="hall")
    upper = _zone(graph, main, "living", (ix0, iz0, ix1, iz1),
                  y=fh + story_wall_h + 1, suffix="view")
    upper.meta["private_quarters"] = True
    _cultivation_deco(graph, "lantern_post", (main.x1 + 2, 0, main.z0 + 1), (1, 3, 1))
    return graph


def build_bell_drum_tower(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    """Cultivation bell/drum tower landmark (钟鼓楼): a 3-storey tower crowned by
    the registered ``bell_drum_tower`` roof form (tiered eaves + belfry bell)."""
    graph = _town_cultivation_graph("bell_drum_tower", tier)
    graph.meta["requires_platform_colonnade"] = True
    graph.meta["vertical_landmark"] = True
    fh = 2
    story_wall_h = 4
    main = _cultivation_main(
        graph, style, rng, "bell_drum_tower", story_wall_h, fh,
        stories=3, story_wall_h=story_wall_h,
        footprint=rng.choice(SCALE_TIERS["bell_drum_tower"]["footprints"]),
        roof_type="bell_drum_tower", roof_axis="x", roof_overhang=2,
        wall_type="white_plaster_timber_wall")
    main.type = "tower_volume"
    main.meta["story_insets"] = [0, 1, 1]
    main.meta["roof"]["footprint_inset"] = 1
    main.meta["belfry"] = True
    graph.meta["pagoda_story_insets"] = [0, 1, 1]
    main.meta["door_centered"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="west")
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x, sides=("front", "back"))
    _cultivation_balustrade(graph, main)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    _zone(graph, main, "work", (ix0, iz0, ix1, iz1), suffix="chime")
    upper = _zone(graph, main, "living", (ix0, iz0, ix1, iz1),
                  y=fh + story_wall_h + 1, suffix="watch")
    upper.meta["private_quarters"] = True
    _cultivation_deco(graph, "lantern_post", (main.x1 + 2, 0, main.z0 + 1), (1, 3, 1))
    _cultivation_deco(graph, "incense_altar",
                      ((main.x0 + main.x1) // 2 - 1, 0, main.z0 - 3), (3, 2, 1))
    return graph


def _sect_deco(graph: MassingGraph, motif: str, origin: Tuple[int, int, int],
               size: Tuple[int, int, int]) -> Node:
    index = len(graph.by_type("decoration_patch"))
    node = Node(
        id=f"sect_deco_{motif}_{index}",
        type="decoration_patch",
        origin=origin,
        size=size,
        attach_to="main",
        priority=70,
        tags=["DETAIL"],
        meta={"motif": motif},
    )
    graph.add(node)
    return node


def build_sect_gate(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _sect_graph("sect_gate", tier)
    graph.meta["requires_platform_colonnade"] = True
    fh = 2
    wall_h = 5
    main = _cultivation_main(
        graph, style, rng, "sect_gate", wall_h, fh,
        footprint=rng.choice(SCALE_TIERS["sect_gate"]["footprints"]),
        roof_type="tiered_eave_roof", roof_axis="x", roof_overhang=2,
        wall_type="white_plaster_timber_wall")
    main.meta["door_centered"] = True
    main.meta["entry_signage"] = True
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x)
    _path_patch(graph, main, door_x, rng, main.z0)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "work", (ix0, iz0, split, iz1), suffix="gate")
    _zone(graph, main, "storage", (split + 1, iz0, ix1, iz1), suffix="stores")
    gate = Node(
        id="mountain_gate",
        type="mountain_gate",
        origin=(main.x0, 0, main.z0 - 2),
        size=(main.size[0], main.size[1], 2),
        attach_to=main.id,
        priority=70,
        tags=["DETAIL", "STRUCTURE"],
    )
    graph.add(gate)
    graph.meta["has_mountain_gate"] = True
    _sect_deco(graph, "cloud_rail", (main.x0 + 2, 1, main.z1 + 1), (5, 2, 1))
    return graph


def build_sect_main_hall(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _sect_graph("sect_main_hall", tier)
    graph.meta["requires_platform_colonnade"] = True
    fh = 3
    story_wall_h = 5
    main = _cultivation_main(
        graph, style, rng, "sect_main_hall", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=rng.choice(SCALE_TIERS["sect_main_hall"]["footprints"]),
        roof_type="tiered_eave_roof", roof_axis="x", roof_overhang=2,
        wall_type="white_plaster_timber_wall")
    main.meta["door_centered"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="east")
    _cultivation_platform(graph, main, door_x, pad=3, height=3)
    _cultivation_colonnade(graph, main, door_x, sides=("front", "back"))
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "work", (ix0, iz0, split, iz1), suffix="ritual")
    _zone(graph, main, "living", (split + 1, iz0, ix1, iz1), y=fh + story_wall_h + 1,
          suffix="upper")
    _sect_deco(graph, "spirit_array", ((main.x0 + main.x1) // 2 - 2, 0, main.z0 - 7), (5, 1, 5))
    _sect_deco(graph, "incense_altar", ((main.x0 + main.x1) // 2 - 1, 0, main.z0 - 2), (3, 2, 1))
    return graph


def build_scripture_pavilion(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _sect_graph("scripture_pavilion", tier)
    graph.meta["requires_platform_colonnade"] = True
    graph.meta["requires_pagoda_insets"] = True
    fh = 2
    story_wall_h = 4
    main = _cultivation_main(
        graph, style, rng, "scripture_pavilion", story_wall_h, fh,
        stories=3, story_wall_h=story_wall_h,
        footprint=rng.choice(SCALE_TIERS["scripture_pavilion"]["footprints"]),
        roof_type="pyramidal_roof", roof_axis="x", roof_overhang=1,
        wall_type="white_plaster_timber_wall")
    main.type = "tower_volume"
    main.meta["story_insets"] = [0, 1, 2]
    main.meta["roof"]["footprint_inset"] = 2
    graph.meta["pagoda_story_insets"] = [0, 1, 2]
    main.meta["door_centered"] = True
    main.meta["entry_signage"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x, preferred_side="west")
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x)
    _cultivation_balustrade(graph, main)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    _zone(graph, main, "work", (ix0, iz0, ix1, iz1), suffix="reading")
    _zone(graph, main, "storage", (ix0, iz1 - 2, ix1, iz1), y=fh + story_wall_h + 1,
          suffix="stacks")
    _sect_deco(graph, "cloud_rail", (main.x0 + 2, 1, main.z1 + 1), (5, 2, 1))
    return graph


def build_alchemy_room(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _sect_graph("alchemy_room", tier)
    fh = 2
    wall_h = 5
    main = _cultivation_main(
        graph, style, rng, "alchemy_room", wall_h, fh,
        footprint=rng.choice(SCALE_TIERS["alchemy_room"]["footprints"]),
        roof_type="hip_roof", roof_axis="x", roof_overhang=2,
        wall_type="mixed_stone_wood_wall")
    main.meta["door_centered"] = True
    door_x = _door(graph, main, rng)
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    _zone(graph, main, "forge", (ix0, iz0, split, iz1))
    _zone(graph, main, "storage", (split + 1, iz0, ix1, iz1))
    _alchemy_furnace_node(graph, main)
    _sect_deco(graph, "incense_altar", (main.x0 + 2, 0, main.z0 - 2), (3, 2, 1))
    return graph


def build_disciple_quarters(style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = _sect_graph("disciple_quarters", tier)
    fh = 2
    story_wall_h = 4
    main = _cultivation_main(
        graph, style, rng, "disciple_quarters", story_wall_h, fh,
        stories=2, story_wall_h=story_wall_h,
        footprint=rng.choice(SCALE_TIERS["disciple_quarters"]["footprints"]),
        roof_type="sweeping_eave_roof", roof_axis="x", roof_overhang=2,
        wall_type="timber_frame_wall")
    main.meta["door_centered"] = True
    door_x = _door(graph, main, rng)
    _reserve_stairwell(graph, main, door_x)
    _cultivation_platform(graph, main, door_x, pad=2)
    _cultivation_colonnade(graph, main, door_x)
    _cultivation_balustrade(graph, main)
    _cultivation_path(graph, main, door_x, rng)
    ix0, ix1 = main.x0 + 1, main.x1 - 1
    iz0, iz1 = main.z0 + 1, main.z1 - 1
    split = (ix0 + ix1) // 2
    quarters = _zone(graph, main, "living", (ix0, iz0, split, iz1), suffix="lower")
    quarters.meta["private_quarters"] = True
    upper = _zone(graph, main, "living", (split + 1, iz0, ix1, iz1),
                  y=fh + story_wall_h + 1, suffix="upper")
    upper.meta["private_quarters"] = True
    _sect_deco(graph, "cloud_rail", (main.x0 + 2, 1, main.z1 + 1), (5, 2, 1))
    return graph


BUILDERS = {
    "small_house": build_small_house,
    "medium_house": build_medium_house,
    "blacksmith": build_blacksmith,
    "small_shop": build_small_shop,
    "medium_shop": build_medium_shop,
    "big_house": build_big_house,
    "tavern": build_tavern,
    "lord_manor": build_lord_manor,
    "main_hall": build_main_hall,
    "side_wing": build_side_wing,
    "front_row": build_front_row,
    "gate_house": build_gate_house,
    "open_hall": build_open_hall,
    "tower_house": build_tower_house,
    "flower_hall": build_flower_hall,
    "cultivation_house": build_cultivation_house,
    "cultivation_shop": build_cultivation_shop,
    "cultivation_inn": build_cultivation_inn,
    "cultivation_market": build_cultivation_market,
    "town_shrine": build_town_shrine,
    "pagoda": build_pagoda,
    "pavilion": build_pavilion,
    "bell_drum_tower": build_bell_drum_tower,
    "sect_gate": build_sect_gate,
    "sect_main_hall": build_sect_main_hall,
    "scripture_pavilion": build_scripture_pavilion,
    "alchemy_room": build_alchemy_room,
    "disciple_quarters": build_disciple_quarters,
}


def build_massing(archetype: str, style: Style, rng: random.Random, tier: str,
                  group_id: Optional[str] = None,
                  importance_tier: Optional[int] = None,
                  overrides: Optional[dict] = None) -> MassingGraph:
    if group_id:
        from .groups import validate_group_archetype
        validate_group_archetype(group_id, style.style_id, archetype)
    _overrideable = CHINESE_ARCHETYPES + ("open_hall", "tower_house", "flower_hall")
    if overrides and archetype in _overrideable:
        graph = BUILDERS[archetype](style, rng, tier, overrides=overrides)
    else:
        graph = BUILDERS[archetype](style, rng, tier)
    graph.meta["style_id"] = style.style_id
    if group_id:
        graph.meta["group_id"] = group_id
    _apply_importance(graph, style, importance_tier)
    _ensure_frontage(graph)
    # tier composition guarantee
    min_volumes = SCALE_TIERS[tier_base(tier)]["min_volumes"]
    n_vol = len(graph.volumes())
    if n_vol < min_volumes:
        raise AssertionError(
            f"{archetype}/{tier}: {n_vol} volumes < required {min_volumes}")
    return graph
