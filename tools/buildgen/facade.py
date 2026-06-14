"""Facade Grammar layer.

Walls are split into bays bounded by timber posts; bay contents (door bay,
window bay, plain bay with beam/material change) are planned per facade with
per-face detail density:

    front_facade: high, side_facade: medium, back_facade: low,
    side_wing_facade: medium, shed_facade: low

Rules enforced here:
  - posts every <=3 blocks => no flat wall run wider than the style maximum
  - openings never sit on building corners (kept >=2 cells away)
  - the entrance is always on the front facade
  - window jitter keeps facades from being mechanically symmetric
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

from .massing import MassingGraph, Node
from .style import Style

DENSITY_P = {"high": 0.9, "medium": 0.6, "low": 0.3}

MAIN_FACADE_KIND = {"front": ("front_facade", "high"),
                    "back": ("back_facade", "low"),
                    "west": ("side_facade", "medium"),
                    "east": ("side_facade", "medium")}

FACADE_KIND = {
    "main_volume": MAIN_FACADE_KIND,
    "great_hall_volume": MAIN_FACADE_KIND,
    "tower_volume": {"front": ("tower_facade", "medium"),
                     "back": ("tower_facade", "low"),
                     "west": ("tower_facade", "medium"),
                     "east": ("tower_facade", "medium")},
    "side_wing": {"front": ("side_wing_facade", "medium"),
                  "back": ("side_wing_facade", "low"),
                  "west": ("side_wing_facade", "medium"),
                  "east": ("side_wing_facade", "medium")},
    "rear_shed": {"front": ("shed_facade", "low"),
                  "back": ("shed_facade", "low"),
                  "west": ("shed_facade", "low"),
                  "east": ("shed_facade", "low")},
}


@dataclass
class WallPlan:
    volume_id: str
    wall: str
    facade: str
    density: str
    post_positions: List[int] = field(default_factory=list)
    door_along: Optional[int] = None
    windows: List[Tuple[int, str]] = field(default_factory=list)  # (along, opening_style)
    story_index: int = 0
    y_base: Optional[int] = None


def _occlusions(graph: MassingGraph, vol: Node, wall: str) -> List[Tuple[int, int]]:
    """Along-axis intervals of this wall hidden by an attached volume."""
    out = []
    for other in graph.volumes():
        if other.id == vol.id:
            continue
        if wall == "east" and other.x0 == vol.x1 + 1:
            out.append((max(vol.z0, other.z0), min(vol.z1, other.z1)))
        elif wall == "west" and other.x1 == vol.x0 - 1:
            out.append((max(vol.z0, other.z0), min(vol.z1, other.z1)))
        elif wall == "back" and other.z0 == vol.z1 + 1:
            out.append((max(vol.x0, other.x0), min(vol.x1, other.x1)))
        elif wall == "front" and other.z1 == vol.z0 - 1:
            out.append((max(vol.x0, other.x0), min(vol.x1, other.x1)))
    stair = vol.meta.get("stairwell") or graph.meta.get("stairwells", {}).get(vol.id) or graph.meta.get("stairwell")
    if stair and stair.get("volume") == vol.id:
        if wall == "east" and stair["x1"] >= vol.x1 - 2:
            out.append((stair["z0"], stair["z1"]))
        elif wall == "west" and stair["x0"] <= vol.x0 + 2:
            out.append((stair["z0"], stair["z1"]))
        elif wall == "back" and stair["z1"] >= vol.z1 - 2:
            out.append((stair["x0"], stair["x1"]))
        elif wall == "front" and stair["z0"] <= vol.z0 + 2:
            out.append((stair["x0"], stair["x1"]))
    return [iv for iv in out if iv[0] <= iv[1]]


def _attached_wall(graph: MassingGraph, vol: Node) -> Optional[str]:
    """For an attached volume, the wall facing its parent."""
    if not vol.attach_to:
        return None
    side = vol.side
    return {"west": "east", "east": "west", "front": "back", "back": "front"}.get(side)


def _in_occlusion(along: int, occlusions, pad: int = 0) -> bool:
    return any(lo - pad <= along <= hi + pad for lo, hi in occlusions)


def plan_wall(graph: MassingGraph, style: Style, rng: random.Random, vol: Node,
              wall: str, facade: str, density: str,
              door_along: Optional[int], opening_style: str,
              story_index: int = 0, y_base: Optional[int] = None) -> WallPlan:
    plan = WallPlan(vol.id, wall, facade, density, door_along=door_along,
                    story_index=story_index, y_base=y_base)
    if wall in ("front", "back"):
        a0, a1 = vol.x0, vol.x1
    else:
        a0, a1 = vol.z0, vol.z1
    occl = _occlusions(graph, vol, wall)

    # posts every ~3 cells, nudged off the door frame
    max_flat = style.prop("max_flat_wall_width")
    step = min(3, max_flat - 1)
    pos = a0 + step
    posts: List[int] = []
    while pos <= a1 - 2:
        p = pos
        if door_along is not None and abs(p - door_along) <= 1:
            p = door_along + 2 if p <= door_along else p + 1
        if a0 + 1 < p < a1 - 1:
            posts.append(p)
        pos += step + (1 if rng.random() < 0.3 else 0)
    plan.post_positions = sorted(set(posts))

    # window candidates: centers of segments between posts
    boundaries = [a0] + plan.post_positions + [a1]
    p_window = DENSITY_P[density]
    for left, right in zip(boundaries, boundaries[1:]):
        if right - left < 2:
            continue
        center = (left + right) // 2
        along = center + rng.choice([-1, 0, 0, 1])
        along = max(a0 + 2, min(along, a1 - 2))       # never on a corner
        if door_along is not None and abs(along - door_along) <= 1:
            continue
        if _in_occlusion(along, occl, pad=0):
            continue
        if along in plan.post_positions:
            continue
        if any(abs(along - w) <= 1 for w, _ in plan.windows):
            continue
        if rng.random() < p_window:
            ostyle = opening_style
            if density == "low" and rng.random() < 0.6:
                ostyle = "small_high_window"
            plan.windows.append((along, ostyle))
    return plan


def plan_building_facades(graph: MassingGraph, style: Style,
                          rng: random.Random) -> List[WallPlan]:
    plans: List[WallPlan] = []
    door = graph.meta["door"]
    opening_style = rng.choice(
        [s for s in style.allowed_opening_styles if s.startswith("window")])
    graph.meta["opening_style"] = opening_style

    for vol in graph.volumes():
        if vol.meta.get("open"):
            continue  # open sheds have no facades
        kinds = FACADE_KIND.get(vol.type, FACADE_KIND["rear_shed"])
        attached = _attached_wall(graph, vol)
        for wall in ("front", "back", "west", "east"):
            if wall == attached:
                continue
            facade, density = kinds[wall]
            door_along = door["x"] if (vol.id == door["volume"] and
                                       wall == door["wall"]) else None
            plans.append(plan_wall(graph, style, rng, vol, wall, facade,
                                   density, door_along, opening_style))
            stories = vol.meta.get("stories", 1)
            if stories > 1:
                story_wall_h = vol.meta.get("story_wall_h", vol.meta["wall_h"])
                base_plan = plans.pop()
                base_plan.y_base = vol.meta["foundation_h"]
                plans.append(base_plan)
                for story in range(1, stories):
                    upper = WallPlan(
                        base_plan.volume_id, base_plan.wall, base_plan.facade,
                        base_plan.density,
                        post_positions=list(base_plan.post_positions),
                        door_along=None,
                        windows=list(base_plan.windows),
                        story_index=story,
                        y_base=vol.meta["foundation_h"] + story * story_wall_h,
                    )
                    plans.append(upper)

    # guarantee the style's minimum window count
    min_windows = style.prop("window_min_count")
    total = sum(len(p.windows) for p in plans)
    if total < min_windows:
        for plan in plans:
            if total >= min_windows:
                break
            if plan.density in ("high", "medium") and not plan.windows:
                vol = graph.get(plan.volume_id)
                a0, a1 = (vol.x0, vol.x1) if plan.wall in ("front", "back") \
                    else (vol.z0, vol.z1)
                along = (a0 + a1) // 2 + 1
                along = max(a0 + 2, min(along, a1 - 2))
                if plan.door_along is None or abs(along - plan.door_along) > 1:
                    plan.windows.append((along, opening_style))
                    total += 1
    return plans
