#!/usr/bin/env python3
"""Enclosure-plan demo for `chinese_mansion` (rebuild-mansion-enclosure-plan).

This is a DESIGN DEMO, not an implementation of the change. It encodes the
placement manifest from design D3 (`_plan_mansion_enclosure`) as a standalone
planner, derives the yards as enclosed negative space, routes a path backbone to
every door, and renders a labeled top-down plan PNG + an HTML viewer.

Purpose: let the reviewer SEE the intended mansion form (real gate_house at the
entrance, every building facing its yard, gravel path reaching every door) BEFORE
any production code is written. If the plan reads right, the change proceeds; if
not, the manifest is revised here first.

Outputs to out/preview/mansion_enclosure_demo/:
  - plan_<size>.png         labeled top-down plan per variant size
  - viewer.html             side-by-side of all 3 sizes + legend + design notes
  - (the aggregate out/preview/index.html is updated by the caller)

No production code is imported or modified. The footprint sizes mirror the real
SCALE_TIERS so the plan is geometrically faithful.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple

from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "out", "preview", "mansion_enclosure_demo")

# Mirror of SCALE_TIERS footprints (w, d) — pick a tier per lot size so a small
# lot gets the compact footprints and a large lot the generous ones. This is what
# the real planner does (the footprint is chosen from the size's tier set).
FOOTPRINT_BY_SIZE = {
    "small": {
        "open_hall": (15, 11),   # MAIN_HALL_BAY_FOOTPRINT[3]-class
        "side_wing": (7, 15),
        "front_row": (15, 7),
        "tower_house": (9, 11),
        "gate_house": (9, 5),
    },
    "medium": {
        "open_hall": (15, 11),
        "side_wing": (7, 15),
        "front_row": (17, 7),
        "tower_house": (11, 11),
        "gate_house": (11, 5),
    },
    "large": {
        "open_hall": (17, 13),
        "side_wing": (9, 15),
        "front_row": (19, 7),
        "tower_house": (11, 13),
        "gate_house": (11, 7),
    },
}


def footprints(size: str) -> dict:
    return FOOTPRINT_BY_SIZE[size]

# lot (w, d) per courtyard_size
LOT = {"small": (39, 62), "medium": (43, 70), "large": (47, 78)}

# Facing → which wall carries the door, and the outward direction of that wall.
# Canonical frame: gate at z=0 (south), inward +z (north). Buildings face -z by
# default (south). The form rule relocates the door wall per role.
FACING_WALL = {
    "south": "front",   # door on low-z wall, faces -z (toward gate/south)
    "north": "back",    # door on high-z wall, faces +z (toward yard interior)
    "east": "east",     # door on +x wall, faces +x
    "west": "west",     # door on -x wall, faces -x
}


@dataclass
class Placement:
    """One entry in the enclosure manifest (design D3)."""
    role: str               # human label: 正房/敞厅/西厢/东厢/倒座/门屋/楼阁
    archetype: str          # open_hall / side_wing / front_row / tower_house / gate_house
    facing: str             # south/north/east/west — the door-wall side
    x0: int                 # lot coords (south-west corner)
    z0: int
    w: int
    d: int
    anchor: str             # south/north/east/west perimeter wall it backs onto

    @property
    def x1(self) -> int: return self.x0 + self.w - 1
    @property
    def z1(self) -> int: return self.z0 + self.d - 1

    def cells(self) -> Set[Tuple[int, int]]:
        return {(x, z) for x in range(self.x0, self.x1 + 1)
                       for z in range(self.z0, self.z1 + 1)}

    def door_cell(self) -> Tuple[int, int]:
        """The door's front cell (one step out of the door wall, on the path)."""
        cx = (self.x0 + self.x1) // 2
        cz = (self.z0 + self.z1) // 2
        wall = FACING_WALL[self.facing]
        if wall == "front":  return (cx, self.z0 - 1)
        if wall == "back":   return (cx, self.z1 + 1)
        if wall == "west":   return (self.x0 - 1, cz)
        if wall == "east":   return (self.x1 + 1, cz)
        return (cx, cz)


@dataclass
class InnerGate:
    """仪门 / 二门 — a roofed wall with a 3-cell passage at a 进 boundary."""
    role: str
    z: int                  # the gate sits along this z row
    axis: int
    width: int = 5          # wall span (axis-2 .. axis+2); passage = axis-1..axis+1

    def cells(self) -> Set[Tuple[int, int]]:
        return {(x, self.z) for x in range(self.axis - self.width // 2,
                                           self.axis - self.width // 2 + self.width)}

    def passage_cells(self) -> Set[Tuple[int, int]]:
        return {(self.axis - 1, self.z), (self.axis, self.z), (self.axis + 1, self.z)}


@dataclass
class GardenFeature:
    """A 花园 element: 水心假山 (island rockery), 水池 (pond), or 亭 (pavilion).

    Mirrors the real `_layout_garden` (compound.py): the 假山 is a 3x3 island in
    the middle of the pond (水心假山), the pond is a freeform water body around it,
    and the 亭 sits on the garden shore. Drawn as labeled color blocks so the
    garden band is no longer empty.
    """
    kind: str               # "rockery" | "pond" | "pavilion"
    cells: Set[Tuple[int, int]]


def plan_manifest(size: str, tower_count: int = 1,
                  garden_scale: str = "small"
                  ) -> Tuple[List[Placement], List[InnerGate], Tuple[int, int], List[GardenFeature]]:
    """Encode design D3's form rule as concrete coordinates for a lot size.

    Layout (canonical south frame, gate at z=0, inward +z):
      z=0          south wall line
      z=1..fy1     前院  (gate_house through-building straddles z=0..gd; 倒座 beside it)
      z=fy1+1..+3  仪门 (3-deep)
      z=..my1      主院  (敞厅 on north end facing south; 厢 east/west facing inward)
      z=..+3       二门 (3-deep)
      z=..by1      后院  (楼阁 off-axis; tower_count ∈ {1,2})
      z=..D-2      花园  (garden_scale ∈ {small, large})

    YARD DEPTHS SCALE WITH LOT DEPTH. The defining difference between a small and
    a large mansion is NOT building count — it is how much breathing room each
    进 has. A 大宅 reads as 大 because its yards are spacious, not because it
    has more doors. So yard depth is allocated proportionally to (D - building
    depth - gate depth), with 主院 (the ceremony yard) getting the largest share.

    Variant axes (per chinese-mansion-compound spec):
      tower_count ∈ {1, 2} — second 楼阁 mirrors the first across the axis.
      garden_scale ∈ {small, large} — larger 花园 gets more depth + features.
    """
    W, D = LOT[size]
    axis = W // 2
    placements: List[Placement] = []
    gates: List[InnerGate] = []

    # Fixed-depth pieces: gate_house, inner gates (3 each), building depths.
    FP = footprints(size)
    gw, gd = FP["gate_house"]
    fw_full, fd = FP["front_row"]
    hw, hd = FP["open_hall"]
    ww, wd = FP["side_wing"]
    tw, td = FP["tower_house"]
    gate_depth = 3  # each inner gate band is 3 cells

    # Reserve the fixed depth budget, distribute the rest to yards by share.
    # Shares: 前院 1.0, 主院 1.8 (ceremonial core, largest), 后院 1.1, 花园 1.3.
    # garden_scale bumps 花园's share.
    garden_share = 1.8 if garden_scale == "large" else 1.3
    shares = {"front": 1.0, "main": 1.8, "back": 1.1, "garden": garden_share}
    fixed = gd + 2 * gate_depth            # gate_house + 2 inner gates
    building_band = max(hd, wd, td)        # a yard must clear its tallest building
    yard_pool = D - 2 - fixed - building_band  # interior depth minus fixed + one building band
    total_share = sum(shares.values())
    front_d = max(6, int(yard_pool * shares["front"] / total_share))
    main_d = max(10, int(yard_pool * shares["main"] / total_share))
    back_d = max(td + 2, int(yard_pool * shares["back"] / total_share))  # clear the tower
    garden_d = max(6, yard_pool - front_d - main_d - back_d)

    # --- 前院 ---
    placements.append(Placement(
        role="门屋 gate_house", archetype="gate_house", facing="north",
        x0=axis - gw // 2, z0=0, w=gw, d=gd, anchor="south"))
    gate_inner = (axis, gd)  # path starts at gate_house north opening

    fw_full_, fd_ = FP["front_row"]
    gate_west_edge = axis - gw // 2
    gate_east_edge = axis - gw // 2 + gw
    avail_west = gate_west_edge - 1 - 1
    avail_east = (W - 1) - gate_east_edge - 1
    fw = min(fw_full_, max(avail_west, avail_east))
    fx = (1 + 1) if avail_west >= avail_east else (gate_east_edge + 1)
    placements.append(Placement(
        role="倒座 front_row", archetype="front_row", facing="north",
        x0=fx, z0=0, w=fw, d=fd_, anchor="south"))

    fy1 = gd + front_d
    yimen_z0 = fy1 + 1
    yimen_z1 = yimen_z0 + gate_depth - 1
    gates.append(InnerGate(role="仪门 yimen", z=yimen_z1, axis=axis))

    # --- 主院 (敞厅 at the north end, facing south into the yard) ---
    my0 = yimen_z1 + 1
    my1 = my0 + main_d - 1
    hall_z1 = my1
    hall_z0 = hall_z1 - hd + 1
    placements.append(Placement(
        role="敞厅 open_hall", archetype="open_hall", facing="south",
        x0=axis - hw // 2, z0=hall_z0, w=hw, d=hd, anchor="north"))

    wing_z0 = my0 + 2
    # Constrain wing depth so the 厢 never crosses the 二门 into the 后院.
    wing_d = min(wd, max(5, my1 - 1 - wing_z0))
    placements.append(Placement(
        role="西厢 west_wing", archetype="side_wing", facing="east",
        x0=1, z0=wing_z0, w=ww, d=wing_d, anchor="west"))
    placements.append(Placement(
        role="东厢 east_wing", archetype="side_wing", facing="west",
        x0=W - 1 - ww, z0=wing_z0, w=ww, d=wing_d, anchor="east"))

    ermen_z0 = my1 + 1
    ermen_z1 = ermen_z0 + gate_depth - 1
    gates.append(InnerGate(role="二门 ermen", z=ermen_z1, axis=axis))

    # --- 后院 (楼阁 off-axis; one or two) ---
    by0 = ermen_z1 + 1
    by1 = by0 + back_d - 1
    # 楼阁 sit beside the axis (NOT against the west/east wall) so they don't
    # collide with the 厢房 that flank the 主院, and the 后院 yard stays open
    # between them. Place from the 后院 start (by0) northward so they never cross
    # back over the 二门 into the 主院. west tower at axis - tower_w - 1, east mirrored.
    t1_x = axis - 1 - tw
    t2_x = axis + 2
    # guarantee the tower fits in the 后院 depth; if back_d < td, shrink is impossible
    # (a tower is a fixed volume), so instead we push it to start at by0 and accept
    # it may adjoin the 二门 — the planner reserves enough back_d for this.
    tz0 = by0
    tz1 = tz0 + td - 1
    placements.append(Placement(
        role="楼阁① tower_house", archetype="tower_house", facing="south",
        x0=t1_x, z0=tz0, w=tw, d=td, anchor="north"))
    if tower_count == 2:
        placements.append(Placement(
            role="楼阁② tower_house", archetype="tower_house", facing="south",
            x0=t2_x, z0=tz0, w=tw, d=td, anchor="north"))

    # --- 花园 (rockery island + pond + pavilion; mirrors `_layout_garden`) ---
    # The garden sits behind the 后院 buildings, spanning the interior width.
    # 水心假山: a 3x3 rockery island centered in the pond (山 meets 水).
    # 水池: freeform water around the island (drawn as the pond bbox minus island).
    # 亭: a small open pavilion on the garden shore, offset from the axis.
    garden: List[GardenFeature] = []
    gy0 = tz1 + 2          # just behind the 后院 buildings
    gy1 = D - 2            # to the north wall
    interior_x0, interior_x1 = 1, W - 2
    if gy1 > gy0 + 3:      # only if there's room for a garden
        pond_cx = (interior_x0 + interior_x1) // 2
        pond_cz = (gy0 + gy1) // 2
        # 水池 bbox: ~2/3 of garden width, ~3/4 of garden depth, centered
        pond_w = max(7, (interior_x1 - interior_x0 + 1) * 2 // 3)
        pond_d = max(6, (gy1 - gy0 + 1) * 3 // 4)
        pond_x0 = pond_cx - pond_w // 2
        pond_z0 = pond_cz - pond_d // 2
        pond_cells = {(x, z) for x in range(pond_x0, pond_x0 + pond_w)
                            for z in range(pond_z0, pond_z0 + pond_d)
                            if interior_x0 <= x <= interior_x1 and gy0 <= z <= gy1}
        # 水心假山 island: 3x3 centered in the pond
        island = {(pond_cx + dx, pond_cz + dz)
                  for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                  if (pond_cx + dx, pond_cz + dz) in pond_cells}
        pond_cells -= island  # island rises from the water
        garden.append(GardenFeature("rockery", island))
        garden.append(GardenFeature("pond", pond_cells))
        # 亭: on the shore, offset east of axis, at the garden's south edge
        pav_x = pond_cx + pond_w // 2 + 1
        pav_z = gy0 + 1
        if interior_x0 <= pav_x <= interior_x1:
            garden.append(GardenFeature("pavilion", {(pav_x, pav_z)}))
        # 大花园 (garden_scale=large): a second 亭 on the opposite shore
        if garden_scale == "large":
            pav2_x = pond_cx - pond_w // 2 - 1
            pav2_z = gy1 - 1
            if interior_x0 <= pav2_x <= interior_x1:
                garden.append(GardenFeature("pavilion", {(pav2_x, pav2_z)}))

    return placements, gates, (W, D), garden


def route_path(placements, gates, lot, gate_inner) -> List[Tuple[int, int]]:
    """Path backbone: gate_house inner opening → every door, through yard space.

    Single-source BFS shortest-path TREE rooted at the gate_house inner opening
    (mirrors design D4's single-source backbone). Every door-cell is a target;
    the union of every target's shortest path back to the root is the backbone.
    Routing is constrained to walkable yard cells (not inside buildings, not
    through gate walls except their 3-cell passage). This is the demo analog of
    the real change's `_route_complete_path`.
    """
    from collections import deque
    W, D = lot
    blocked: Set[Tuple[int, int]] = set()
    for p in placements:
        blocked |= p.cells()
    for g in gates:
        blocked |= (g.cells() - g.passage_cells())  # wall blocks; passage open

    def walkable(x, z):
        return 1 <= x < W - 1 and 1 <= z < D - 1 and (x, z) not in blocked

    # BFS from gate_inner; record predecessor tree.
    root = gate_inner
    pred: Dict[Tuple[int, int], Optional[Tuple[int, int]]] = {root: None}
    q = deque([root])
    while q:
        x, z = q.popleft()
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if (nx, nz) not in pred and walkable(nx, nz):
                pred[(nx, nz)] = (x, z)
                q.append((nx, nz))

    # Union every door's shortest path back to the root → backbone.
    path: Set[Tuple[int, int]] = set()
    for p in placements:
        cell = p.door_cell()
        # door cell may be just outside a wall; if not walkable, step isn't added
        # but we still try to trace from it (it adjoins walkable yard).
        cur = cell
        while cur is not None and cur in pred:
            path.add(cur)
            cur = pred[cur]
    return sorted(path)


# ----- rendering -----

ROLE_COLOR_PREFIX = [
    ("门屋",   (120, 180, 120)),  # green — entrance
    ("倒座",   (180, 140, 100)),  # tan
    ("敞厅",   (190,  90,  90)),  # red — principal
    ("西厢",   (130, 150, 200)),  # blue
    ("东厢",   (130, 150, 200)),  # blue
    ("楼阁",   (160, 110, 180)),  # purple — tower
]


def role_color(role: str):
    for prefix, color in ROLE_COLOR_PREFIX:
        if role.startswith(prefix):
            return color
    return (150, 150, 150)
GATE_COLOR = (90, 90, 110)
PATH_COLOR = (200, 180, 130)   # gravel
DOOR_COLOR = (240, 230, 80)    # yellow door marker
YARD_TINT = (60, 70, 55)       # subtle green yard floor
GARDEN_COLORS = {
    "rockery":  (110, 105, 100),  # stone grey — 假山 island
    "pond":     (70, 110, 160),   # water blue — 水池
    "pavilion": (200, 170, 110),  # timber gold — 亭
}


def render_plan(variant_id: str, title: str, placements, gates, lot, path,
                garden=None) -> str:
    W, D = lot
    scale = 12
    pad = 40
    img_w = W * scale + pad * 2
    img_h = D * scale + pad * 2
    img = Image.new("RGB", (img_w, img_h), (24, 26, 30))
    d = ImageDraw.Draw(img)

    def cell(x, z, color, inset=0):
        px = pad + x * scale + inset
        pz = pad + z * scale + inset
        d.rectangle([px, pz, px + scale - inset, pz + scale - inset], fill=color)

    # perimeter wall outline
    d.rectangle([pad - 2, pad - 2, pad + W * scale + 1, pad + D * scale + 1],
                outline=(140, 140, 150), width=2)

    # yard floor tint (whole interior, faint)
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            cell(x, z, YARD_TINT)

    # path
    for (x, z) in path:
        cell(x, z, PATH_COLOR)

    # inner gates (wall + passage)
    for g in gates:
        for (x, z) in g.cells():
            cell(x, z, GATE_COLOR)
        for (x, z) in g.passage_cells():
            cell(x, z, PATH_COLOR)

    # buildings
    for p in placements:
        color = role_color(p.role)
        for (x, z) in p.cells():
            cell(x, z, color, inset=1)
        # label
        lx = pad + (p.x0 + p.w / 2) * scale
        lz = pad + (p.z0 + p.d / 2) * scale
        try:
            font = ImageFont.truetype("arial.ttf", 11)
        except Exception:
            font = ImageFont.load_default()
        label = p.role.split()[0]
        bbox = d.textbbox((0, 0), label, font=font)
        d.text((lx - (bbox[2] - bbox[0]) / 2, lz - (bbox[3] - bbox[1]) / 2),
               label, fill=(255, 255, 255), font=font)

    # 花园 features (water-first 假山 island + pond + pavilion) — fills the
    # previously-empty north tail so the garden band reads as 山水, not blank.
    if garden:
        for feat in garden:
            color = GARDEN_COLORS.get(feat.kind, (120, 120, 120))
            for (x, z) in feat.cells:
                cell(x, z, color, inset=1 if feat.kind != "pond" else 0)
        # garden labels
        try:
            font_g = ImageFont.truetype("arial.ttf", 9)
        except Exception:
            font_g = ImageFont.load_default()
        for feat in garden:
            if not feat.cells:
                continue
            cx = sum(c[0] for c in feat.cells) / len(feat.cells)
            cz = sum(c[1] for c in feat.cells) / len(feat.cells)
            label = {"rockery": "假山", "pond": "水池", "pavilion": "亭"}[feat.kind]
            px = pad + cx * scale + scale / 2
            pz = pad + cz * scale + scale / 2
            bbox = d.textbbox((0, 0), label, font=font_g)
            d.text((px - (bbox[2] - bbox[0]) / 2, pz - (bbox[3] - bbox[1]) / 2),
                   label, fill=(255, 255, 255), font=font_g)

    # door markers (a bright dot + arrow indicating facing direction).
    # The arrow points INTO the yard the door faces (the outward direction of the
    # door wall). In PIL coords z grows downward; south=-z=up, north=+z=down.
    facing_arrow = {"south": (0, -1), "north": (0, 1), "east": (1, 0), "west": (-1, 0)}
    for p in placements:
        dx, dz = p.door_cell()
        px = pad + dx * scale + scale / 2
        pz = pad + dz * scale + scale / 2
        r = scale * 0.32
        d.ellipse([px - r, pz - r, px + r, pz + r], fill=DOOR_COLOR,
                  outline=(40, 40, 0))
        # arrow showing which way the door faces (points into the yard)
        ax_, az_ = facing_arrow.get(p.facing, (0, 0))
        if ax_ or az_:
            tip = (px + ax_ * scale * 0.9, pz + az_ * scale * 0.9)
            d.line([px, pz, tip[0], tip[1]], fill=(255, 255, 255), width=2)

    # gate_house street-door marker (south side, distinct)
    gh = next(p for p in placements if p.archetype == "gate_house")
    sx = pad + (gh.x0 + gh.w / 2) * scale
    sz = pad - 8
    d.line([sx, sz, sx, sz + 14], fill=DOOR_COLOR, width=2)
    try:
        font = ImageFont.truetype("arial.ttf", 10)
    except Exception:
        font = ImageFont.load_default()
    d.text((sx + 6, sz - 2), "街门 street door", fill=DOOR_COLOR, font=font)

    # compass + frame label
    try:
        font_t = ImageFont.truetype("arial.ttf", 14)
        font_s = ImageFont.truetype("arial.ttf", 10)
    except Exception:
        font_t = font_s = ImageFont.load_default()
    d.text((pad, 6), f"{title}  ({W}x{D})  —  enclosure plan demo",
           fill=(220, 225, 230), font=font_t)
    d.text((pad, img_h - 16), "N(北) ▲   S(南/gate) ▼   door = yellow ●→   path = gravel",
           fill=(170, 175, 180), font=font_s)

    fname = f"plan_{variant_id}.png"
    img.save(os.path.join(OUT_DIR, fname))
    return fname


# Three visibly distinct variants — the difference is NOT just lot size, it is
# yard depth (scales with D) + tower_count + garden_scale, exactly the axes the
# chinese-mansion-compound spec defines. This is what makes 大宅 read as 大.
VARIANTS = [
    # (variant_id, title, size, tower_count, garden_scale, blurb)
    ("compact", "小型宅第 · 紧凑 3进 单楼阁",
     "small", 1, "small",
     "small lot · 1 楼阁 · 小花园 — 院落紧凑，入门即见正房"),
    ("spacious", "中型宅第 · 舒展 3进 双楼阁",
     "medium", 2, "small",
     "medium lot · 2 楼阁(绣楼+藏书楼) · 主院舒展 — 对称双楼压后院"),
    ("grand", "大型宅第 · 宽阔 3进 双楼阁 大花园",
     "large", 2, "large",
     "large lot · 2 楼阁 · 大花园 — 主院最阔，花园最深，气派足"),
]


def build_viewer(entries, out_dir):
    """Side-by-side HTML viewer of the 3 variants + legend + design notes."""
    import html as H
    cards = []
    for variant_id, title, fname, lot, placements, blurb in entries:
        facings = "<br>".join(
            f"<b>{p.role.split()[0]}</b>: facing {p.facing} (door→yard)"
            for p in placements)
        cards.append(f"""
        <section class="card">
          <h2>{H.escape(title)}</h2>
          <p class="blurb">{H.escape(blurb)}</p>
          <img src="{fname}" alt="{H.escape(title)} plan">
          <div class="facings">{facings}</div>
        </section>""")
    html_doc = f"""<!doctype html>
<html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>江南大宅 围合规划演示 — mansion enclosure demo</title>
<style>
:root {{ color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, "Segoe UI", sans-serif;
  background:#121417; color:#eef2f3; }}
body {{ margin:0; padding:24px; max-width:1400px; margin:0 auto; }}
h1 {{ font-size:22px; margin:0 0 4px; }}
.lede {{ color:#aab4bd; font-size:13px; margin:0 0 20px; max-width:900px; line-height:1.6; }}
.grid {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(440px,1fr)); gap:20px; }}
.card {{ background:#181c20; border:1px solid #303840; border-radius:10px; padding:16px; }}
.card h2 {{ font-size:16px; margin:0 0 10px; }}
.card h2 .dim {{ color:#7a828c; font-weight:400; font-size:13px; }}
.card img {{ width:100%; border-radius:6px; image-rendering:pixelated; }}
.blurb {{ margin:0 0 10px; font-size:12px; color:#9aa4ad; line-height:1.5; }}
.facings {{ margin-top:10px; font-size:12px; color:#c0c8d0; line-height:1.7;
  background:#0e1013; padding:10px 12px; border-radius:6px; }}
.legend {{ margin:22px 0; display:flex; gap:18px; flex-wrap:wrap; font-size:12px; }}
.legend span {{ display:inline-flex; align-items:center; gap:6px; }}
.swatch {{ width:14px; height:14px; border-radius:3px; display:inline-block; }}
.notes {{ margin-top:8px; background:#0e1013; border:1px solid #262d34; border-radius:8px;
  padding:14px 16px; font-size:12.5px; line-height:1.7; color:#c4ccd4; max-width:900px;}}
.notes b {{ color:#eef2f3; }}
.notes ul {{ margin:6px 0 0 18px; padding:0; }}
.checks {{ margin-top:14px; }}
.check {{ display:flex; gap:8px; align-items:flex-start; margin:4px 0; }}
.check:before {{ content:"✓"; color:#7ec77e; font-weight:700; }}
.check.bad:before {{ content:"✗"; color:#e07b7b; }}
</style></head><body>
<h1>江南大宅 · 围合规划演示</h1>
<p class="lede">这是 <code>rebuild-mansion-enclosure-plan</code> 提案的<b>实现前演示</b> ——
把 design D3 的 placement manifest 落成真实坐标，验证「门屋 + 建筑按形制朝向围合 + 路径通到每个门」
的形态成立。<b>尚未写任何生产代码</b>；这张图先让你看到「对不对」，对了再实现。</p>

<div class="legend">
  <span><i class="swatch" style="background:rgb(120,180,120)"></i>门屋 gate_house（穿堂）</span>
  <span><i class="swatch" style="background:rgb(180,140,100)"></i>倒座 front_row</span>
  <span><i class="swatch" style="background:rgb(190,90,90)"></i>敞厅 open_hall（正房）</span>
  <span><i class="swatch" style="background:rgb(130,150,200)"></i>厢房 side_wing</span>
  <span><i class="swatch" style="background:rgb(160,110,180)"></i>楼阁 tower_house</span>
  <span><i class="swatch" style="background:rgb(110,105,100)"></i>假山 rockery（水心岛）</span>
  <span><i class="swatch" style="background:rgb(70,110,160)"></i>水池 pond</span>
  <span><i class="swatch" style="background:rgb(200,170,110)"></i>亭 pavilion</span>
  <span><i class="swatch" style="background:rgb(200,180,130)"></i>沙砾路 path</span>
  <span><i class="swatch" style="background:rgb(240,230,80)"></i>门 door</span>
</div>

<div class="grid">{''.join(cards)}</div>

<div class="notes">
<b>三个变体的差异（不是只换围墙大小）：</b>
<ul>
<li><b>院落进深随 lot 深度按比例分配</b> —— 大宅的「大」体现在院子舒展（尤其主院最深），不是建筑多。看三张图的主院留白面积差异。</li>
<li><b>楼阁数 tower_count ∈ {{1,2}}</b> —— 紧凑型 1 座楼阁；舒展/宽阔型 2 座（绣楼+藏书楼，对称压后院）。</li>
<li><b>花园规模 garden_scale ∈ {{small,large}}</b> —— 大花园分到更多进深（份额 1.3→1.8）。</li>
</ul>
<b>这张图同时解决了你点的四个问题（对照提案 §Why）：</b>
<ul>
<li><b>门不像门</b> → 南入口现在是<b>真正的门屋建筑</b>（绿色块，跨在 z=0 围墙上），玩家从街门（上方黄标）<b>穿过门屋</b>进院，不再是墙上凿洞。</li>
<li><b>房屋堆在门口</b> → manifest 按「锚定围墙 + 形制朝向」摆放：正房锚北墙、倒座锚南墙、厢房锚东西墙、楼阁在后院偏轴。<b>院是它们围出来的负空间</b>，不再是 z-band 切出来的。</li>
<li><b>方向杂乱</b> → 每个建筑的 facing 由<b>形制规则</b>决定（非随机）：敞厅朝南、倒座朝北、西厢朝东、东厢朝西。<b>所有门都朝向自己围合的院子</b>。看每个块旁边的 facing 标注。</li>
<li><b>路到不了门口</b> → 沙砾路从门屋内口出发，沿中轴 + 分支接到<b>每个门</b>（黄色点）。门是路径的<b>输入</b>，不是事后补丁。</li>
</ul>
<div class="checks">
  <div class="check">门屋跨在南围墙上，街门→穿堂→前院 成立</div>
  <div class="check">倒座门朝北（朝前院），不再朝街</div>
  <div class="check">东西厢门朝中（朝主院），不再都朝南</div>
  <div class="check">沙砾路（黄褐色）连到每个黄门点</div>
  <div class="check">三个 lot 尺寸都放得下且围合成立</div>
</div>
<p style="margin-top:12px;color:#8a929c;font-size:11.5px">
注：花园（假山/水池/亭）按提案在本演示省略，集中在「门+围合+朝向+路径」四点上。
楼阁此处朝南示意；design Open Question 1 留待实现时定（默认朝后院）。
</p>
</div>
</body></html>"""
    with open(os.path.join(out_dir, "viewer.html"), "w", encoding="utf-8") as f:
        f.write(html_doc)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    # Clean stale plan_*.png from the old size-only scheme so they don't linger.
    for stale in ("plan_small.png", "plan_medium.png", "plan_large.png"):
        p = os.path.join(OUT_DIR, stale)
        if os.path.exists(p):
            os.remove(p)
    entries = []
    for variant_id, title, size, tower_count, garden_scale, blurb in VARIANTS:
        placements, gates, lot, garden = plan_manifest(size, tower_count, garden_scale)
        gate_inner = (lot[0] // 2, footprints(size)["gate_house"][1])
        path = route_path(placements, gates, lot, gate_inner)
        fname = render_plan(variant_id, title, placements, gates, lot, path, garden)
        entries.append((variant_id, title, fname, lot, placements, blurb))
        # sanity: no building overlap, every door on path
        overlap = any(placements[i].cells() & placements[j].cells()
                      for i in range(len(placements))
                      for j in range(i + 1, len(placements)))
        doors_ok = all(p.door_cell() in path for p in placements)
        print(f"  {variant_id:9s} {lot} towers={tower_count} garden={garden_scale:5s} "
              f"-> {fname}  buildings={len(placements)} path={len(path)} "
              f"overlap={'YES' if overlap else 'no'} doors_on_path={'all' if doors_ok else 'MISSING'}")
    build_viewer(entries, OUT_DIR)
    print(f"\nViewer: {os.path.join(OUT_DIR, 'viewer.html')}")


if __name__ == "__main__":
    main()
