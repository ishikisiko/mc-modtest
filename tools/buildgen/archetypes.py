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
from typing import List, Optional, Tuple

from .massing import MassingGraph, Node
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
}

ARCHETYPES = ("small_house", "medium_house", "blacksmith")

# scale tier used for each gallery seed index (1-based), per archetype
TIER_PLAN = {
    "small_house": {i: "small" for i in range(1, 11)},
    "medium_house": {**{i: "medium" for i in range(1, 8)},
                     **{i: "large_lite" for i in range(8, 11)}},
    "blacksmith": {1: "small", 2: "small",
                   **{i: "medium" for i in range(3, 9)},
                   9: "large_lite", 10: "large_lite"},
}


def roof_peak_y(wall_top: int, span: int, overhang: int) -> int:
    """Highest roof y for a gable over `span` blocks with symmetric overhang."""
    total = span + 2 * overhang
    return wall_top + 1 + (total - 1) // 2


def _rects_overlap(a: Node, b: Node, margin: int = 0) -> bool:
    return not (a.x1 + margin < b.x0 or b.x1 + margin < a.x0 or
                a.z1 + margin < b.z0 or b.z1 + margin < a.z0)


def _free_spot(graph: MassingGraph, node: Node, margin: int = 0) -> bool:
    return all(not _rects_overlap(node, other, margin) for other in graph.nodes
               if other.type != "interior_zone")


def _main_volume(graph: MassingGraph, style: Style, rng: random.Random,
                 tier: str, wall_h: int, fh: int) -> Node:
    w, d = rng.choice(SCALE_TIERS[tier]["footprints"])
    main = Node(
        id="main", type="main_volume", origin=(0, 0, 0), size=(w, fh + wall_h, d),
        orientation="front", priority=20, tags=["STRUCTURE"],
        meta={
            "foundation_h": fh, "wall_h": wall_h,
            "wall_type": rng.choice(style.allowed_wall_types),
            "roof": {"type": "gable_roof", "ridge_axis": "x",
                     "overhang": rng.choice([0, 1, 1])},
        })
    graph.add(main)
    return main


def _door(graph: MassingGraph, main: Node, rng: random.Random,
          avoid: Tuple[int, int] = None) -> int:
    """Pick the front door x; off-center on purpose, away from corners."""
    lo, hi = main.x0 + 2, main.x1 - 2
    center = (lo + hi) // 2
    options = [x for x in range(lo, hi + 1) if abs(x - center) <= 2]
    if avoid:
        options = [x for x in options if not (avoid[0] <= x <= avoid[1])] or options
    door_x = rng.choice(options)
    graph.meta["door"] = {"volume": "main", "wall": "front", "x": door_x}
    return door_x


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
    cx = main.x0 - 1 if side == "west" else main.x1 + 1
    cz = main.z0 + main.size[2] // 2 - 1 + rng.choice([-1, 0, 1])
    cz = max(main.z0 + 1, min(cz, main.z1 - 2))
    chimney = Node(
        id=node_id, type="chimney", origin=(cx, 0, cz),
        size=(1, peak + 3, 2), attach_to="main", side=side,
        priority=20, tags=["STRUCTURE", "DETAIL", "PROTECTED"])
    graph.add(chimney)
    return chimney


def _path_patch(graph: MassingGraph, main: Node, door_x: int,
                rng: random.Random, front_z: int) -> Node:
    pw, pd = rng.choice([2, 3]), rng.choice([2, 3])
    node = Node(
        id="path_front", type="path_patch",
        origin=(door_x - pw // 2, 0, front_z - pd), size=(pw, 1, pd),
        attach_to="main", side="front", priority=70, tags=["DETAIL", "GROUND"])
    graph.add(node)
    return node


def _zone(graph: MassingGraph, vol: Node, kind: str, region: Tuple[int, int, int, int]) -> Node:
    fh = vol.meta.get("foundation_h", 1)
    x0, z0, x1, z1 = region
    node = Node(
        id=f"zone_{kind}_{vol.id}", type="interior_zone",
        origin=(x0, fh, z0), size=(x1 - x0 + 1, 2, z1 - z0 + 1),
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
        node = Node(
            id=f"deco_{placed}", type="decoration_patch",
            origin=(cx, 0, cz), size=(2, 2, 2),
            attach_to="main", priority=70, tags=["DETAIL"],
            meta={"motif": motif})
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
          (work.x0, work.z0, work.x1, work.z1))

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


BUILDERS = {
    "small_house": build_small_house,
    "medium_house": build_medium_house,
    "blacksmith": build_blacksmith,
}


def build_massing(archetype: str, style: Style, rng: random.Random, tier: str) -> MassingGraph:
    graph = BUILDERS[archetype](style, rng, tier)
    graph.meta["style_id"] = style.style_id
    # tier composition guarantee
    min_volumes = SCALE_TIERS[tier]["min_volumes"]
    n_vol = len(graph.volumes())
    if n_vol < min_volumes:
        raise AssertionError(
            f"{archetype}/{tier}: {n_vol} volumes < required {min_volumes}")
    return graph
