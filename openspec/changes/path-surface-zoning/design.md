## Context

The courtyard/mansion compounds ship a **single-material path** (`GROUND_PATH` =
`minecraft:gravel`) routed as a single-source shortest-path tree from the street
gate, overlaid on a two-zone ground layer (`open_sky` → grass, `under_eave` →
stone). This is walkable (it passes `_voxel_walk_bfs`) but **reads as no real
road**: every formal axis, garden approach, and waterside step is the same flat
gravel, and there is no visual or narrative difference between the ritual 正路,
the back-of-house 夹道, and a 游赏曲径.

The user's vision is a readable path vocabulary of six surface kinds and three
routes:

- **Surfaces (6):** 规整青石路 (formal bluestone), 天井灰砖铺地 (courtyard grey
  brick), 廊下木石地面 (gallery wood-stone), 白墙夹道 (white-walled alley),
  苔石曲径 (mossy winding path), 水边石阶与小桥 (waterside steps + bridge).
- **Routes (3):**
  - 正式 — 窄巷 → 门 → 影壁 → 前厅 → 天井 → 大厅 → 内宅
  - 生活 — 侧门/夹道 → 厨房/仓库/仆役房
  - 游赏 — 月洞门 → 曲径 → 假山 → 水边廊 → 亭桥 → 花厅

Three constraints from the codebase shape everything below:

1. **Two layers already exist.** `_place_yard_ground` (`compound.py:761`) owns the
   ground tile per cell; `_route_complete_path` (`compound.py:2052`) owns the
   path overlay per cell. The vision does **not** require a new layer — it
   requires zoning both existing layers into more than one material each.
2. **AGENTS.md forbids hardcoding.** Every new ground/path block goes through a
   style slot addition in the style JSON — no `compound.py` string dispatch.
3. **The current path algorithm is shortest-path.** A single-source BFS produces
   a *straight* line between two points. This is correct for the formal axis
   (礼制要求直) but **fundamentally cannot produce the "曲" (winding) of a 苔石
   曲径** — winding is the deliberate absence of shortest-path.
4. **The 水边廊 is a floor-only placeholder (discovered in the in-game review).**
   `_layout_garden` registers `waterside_gallery` as a `covered_gallery` parcel
   whose comment says it "reads as a roofed walkway", but the code only lays one
   `PATH_GALLERY` floor block per shore cell (`compound.py` ≈ `:3607-3613`). No
   column, no roof, no balustrade. The mansion 主院 has no 抄手游廊 at all. A
   surface-zone change that turns gravel into oak_planks on that one floor tile
   does not make it a 廊 — the user's review was explicit that this change must
   also build the real 3D gallery. (Arc 5, elevated after review.)
5. **The 后院 and 花园 share one z-interval (discovered in the in-game review).**
   `generate_mansion` defines both bands as `[ermen_z+1, lot_d-2]`
   (`compound.py:3723-3724`); the `_mansion_yard_depths` `back_d`/`garden_d`
   split is computed but unused. So the 绣楼 (`tower_house`, depth 13, placed at
   `ermen_z+1`) sits the full depth of its would-be 后院 straight into the 花园
   band, overlapping 假山/水池/亭, and a single tower is pinned hard-west
   (`t1_x = axis-1-tw`). The 主院 台基 is meanwhile full-width solid
   PLATFORM_STONE. The mansion has read as broken since v0.16. (Arc 6, elevated
   after review.)

## Goals / Non-Goals

**Goals**

- Six readable surface kinds, each resolved through a style slot, distinguished
  by **zone** (the space a cell belongs to), not by route.
- Three readable routes, distinguished by the **sequence of zones** they pass
  through, plus a shape axis: formal/service stay straight (BFS), tour winds.
- The tour path winds — it does not collapse to a shortest-path diagonal — via
  waypoint routing with obstacle-avoidance fallback.
- Missing path termini exist as real parcels/archetypes so the routes have
  endpoints: 月洞门 passage (穿墙通道), 水边廊 (沿岸 covered_gallery variant),
  仆役房/厨房 (service archetype).
- Waterside steps + a real bridge (not the removed spike-row 汀步) cross the
  garden pond and reach the 亭.
- All three compound families covered: `chinese_mansion`, `chinese_courtyard`,
  embedded `small_courtyard` — each with its vocabulary subset.
- **(Arc 5)** The 水边廊 is a real 3D covered gallery (floor + columns +
  single-slope roof + water-side balustrade), and the mansion 主院 gains 抄手游廊
  — both via one shared `_place_covered_gallery_3d()` renderer. A 廊 is no longer
  a single floor tile.
- **(Arc 6)** The 绣楼 stands in its own 后院, separated from the 花园 by the
  `back_d` depth split; the 主院 台基 shrinks to the building footprints so the
  主院 heart is grass. The 中轴主骨 is preserved; the 花园 stays free-form.

**Non-Goals (deferred)**

- 4-进 mansion (already FUTURE in `chinese-mansion-compound`).
- 徽派天井大屋 (still design-retention only).
- Per-roof-form eave projection (the 1-cell under-eave ring stays the
  approximation, per `fix-courtyard-ground-walkability` D4).
- Town-scale (`cultivation_town`) street paving vocabulary — this change is
  compound-scale. Town street surfaces are a separate capability.

## Decisions

### D1: Material is a property of the zone, NOT of the route — the two axes are orthogonal

**Choice.** Each path/ground cell is classified along **two independent axes**:

- **Axis 1 — material (space property):** which zone the cell belongs to → looks
  up the surface slot. The three routes (formal/service/tour) do **not** carry
  material. A formal cell and a tour cell in the *same* zone get the *same*
  material.
- **Axis 2 — shape (geometry property):** which route the cell belongs to →
  decides straight (BFS) vs winding (waypoint). Material does not participate.

This is the resolution of the user's directive that material and route "不该分离"
(should not be separate): they are not separate *axes*, but material is owned by
**space** while route owns only **sequence + shape**. A route is the ordered list
of zones it passes through; the material of each step is decided by that step's
zone.

Concretely, the six surfaces are the materials of six zones:

| surface (用户面层)        | zone (空间)            | layer  | slot (new/changed)      |
|--------------------------|------------------------|--------|-------------------------|
| 规整青石路 (formal bluestone) | 中轴通途 (formal axis)  | path   | `PATH_FORMAL` (new)     |
| 天井灰砖铺地 (grey brick)     | 天井/院心 (yard heart)  | ground | `GROUND_YARD_HEART` (new) |
| 廊下木石地面 (wood-stone)     | 廊道 (gallery)          | ground | `PATH_GALLERY` (new)    |
| 白墙夹道 (alley)             | 夹道 (service alley)    | ground | `PATH_ALLEY` (new)      |
| 苔石曲径 (mossy winding)     | 游园 (tour garden)      | path   | `PATH_TOUR` (new)       |
| 水边石阶与小桥 (steps+bridge) | 水岸 (waterside)        | path   | `PATH_WATERSIDE` (new)  |

The three routes are zone sequences (brackets mark zone, material follows the
zone):

```
正式: 街 → 门屋 → [中轴] → 影壁 → [中轴] → [天井] → 敞厅 → [天井] → 二门 → [天井] → 内宅
生活: 侧门 → [夹道] → 厨房/仓库/仆役房
游赏: 月洞门 → [游园] → 假山 → [水岸] → 水边廊 → 亭桥 → [游园] → 花厅
```

**Alternatives considered**

- *Route carries material (a slot per route: FORMAL_PATH/SERVICE_PATH/TOUR_PATH).*
  Rejected: this was the original framing the user pushed back on. It produces
  the conflict "is the formal-axis cell crossing the 天井 made of 青石 (because
  formal) or 灰砖 (because it's a 天井)?" — an unresolvable ambiguity. Zone-owned
  material dissolves the conflict: the cell is 灰砖 because it is a 天井; the
  route only decided that the formal axis passes *through* this 天井.
- *Zone owns material, route owns nothing (pure zone model).* Rejected: this
  loses the winding shape of the tour route. The tour route must own *shape*
  even if it does not own *material*. Hence two axes, not one.

**Rationale.** The user's "不分离" is satisfied by making material a *function of
space* and route a *sequence of spaces*. Nothing is duplicated; every cell gets
exactly one material (from its zone) and at most one shape (from its route).

### D2: The six surfaces split across the two existing layers — no new layer

**Choice.** The six zones map onto the two existing passes, three each:

```
GROUND layer (_place_yard_ground, owns the floor a player stands on):
  open_sky     → grass_block            [unchanged, 院子空地留草]
  天井/院心    → GROUND_YARD_HEART      [new sub-zone, grey brick]
  廊道         → PATH_GALLERY           [covered_gallery re-routed, wood+stone mix]
  夹道         → PATH_ALLEY             [倒座 side_alley, brick]

PATH layer (_route_complete_path, owns the overlay on the floor):
  中轴 backbone → PATH_FORMAL           [青石, straight, reuses BFS]
  游园 waypoint → PATH_TOUR             [苔石, winding, new algorithm per D4]
  水岸 segment → PATH_WATERSIDE         [stairs + slab bridge, per D5]
```

This is the user's "草+砖结合" (D5 of the earlier discussion): the yard keeps its
grass (`open_sky`), and only the defined zones — 天井心, 廊下, 夹道, path overlay —
flip to brick/wood-stone/stone. The compound is not paved wholesale.

**Alternatives considered**

- *A single new "path-surface" pass that owns all six.* Rejected: the
  ground/path split is load-bearing for walkability (the ground guarantees "no
  hole", the path guarantees "the overlay reaches doors"). Folding them into one
  pass reopens the ground-layer-hole defect class. Keep the split; zone each.

**Rationale.** Two passes already exist and each owns a distinct contract
(walkable floor vs readable road). Zoning each is additive; neither contract
changes.

### D3: 天井/院心 is the eave-drip ring, not the whole yard (material impact is bounded)

**Choice.** The 天井灰砖 zone is **not** the entire yard. Per the user-accepted
default (方案 ii), it is the **eave-drip ring** — the 1-cell Chebyshev ring
around each building footprint that is *already* classified `under_eave` today
(see `fix-courtyard-ground-walkability` D4). The change is only that this ring
resolves to grey brick (`GROUND_YARD_HEART`) instead of the current
`GROUND_YARD_UNDER_EAVE` stone, and the **yard interior stays grass**.

```
方案 ii (chosen)              方案 i (rejected)           方案 iii (rejected)
┌──────────┐                  ┌──────────┐                ┌──────────┐
│░░░░░░░░░░│ 砖(檐下)         │░░░░░░░░░░│ 全砖           │░░░░░░░░░░│ 砖(檐下)
│▓▓▓▓▓▓▓▓▓▓│                  │░░░░░░░░░░│                │▓▓▓░░░▓▓▓│
│▓▓▓ 草 ▓▓▓│ 砖(檐下)→其实檐下 │░░░░░░░░░░│                │▓▓▓░青░▓▓▓│ 中轴青石带
│▓▓▓▓▓▓▓▓▓▓│                  │░░░░░░░░░░│                │▓▓▓░石░▓▓▓│
│░░░░░░░░░░│                  │░░░░░░░░░░│                │▓▓▓░░░▓▓▓│
└──────────┘                  └──────────┘                └──────────┘
院子心=草,视觉冲击小           整片翻砖,冲击大             礼制感强,但天井语义弱
```

Note: in `chinese_mansion` there is currently no `tianjing` parcel (only
`small_courtyard` has one). D3 does **not** require a new `tianjing` parcel in
the mansion — the mansion's "天井" zone is derived from the existing
`under_eave` ring + `covered_gallery` cells, both already computed by
`_ground_kind_for`.

**Alternatives considered**

- *方案 i (whole yard brick).* Rejected by the user: visual impact too large,
  and historically wrong — a 江南 天井 is the eave-drip strip, not the whole
  courtyard.
- *方案 iii (formal-axis brick band + grass elsewhere).* Rejected: it makes the
  天井 zone == the formal path, collapsing axis 1 (material) back onto axis 2
  (route), which D1 forbids. The 天井 is a *space*, not the formal route.

**Rationale.** The eave-drip ring is already the `under_eave` classification; it
is the architecturally correct "天井" boundary, and reusing it bounds the
material change to a ring rather than re-paving the yard.

### D4: The tour route winds via waypoint routing (C) with obstacle-avoidance fallback (A)

**Choice.** The formal and service routes reuse the existing single-source BFS
(straight — 礼制要求直). The **tour** route does not: it is routed as a **polyline
through scenic waypoints**, each segment a shortest path, and the waypoints
themselves are the garden's scenic anchors (假山南, 水岸拐点, 亭). A segment that
would still cut straight through a rockery/pond is forced around it by an
obstacle set (the same `blocked` cells the path BFS uses).

```
waypoint routing (弯法 C) + obstacle fallback (弯法 A):

   月洞门 ·─·
            ·  ·                   waypoint 序列:
              · ─· ─ 假山南          月洞门 → 假山南 → 水岸拐点 → 亭
                  ·    ·            每段 P_i→P_{i+1} 是最短路径,
                    ·    ·─水边廊     转折点制造"曲".
                      ·             障碍集 {假山, 水池, 亭} 兜底,
                       亭桥          防止某段仍直插障碍.
```

**Why C (waypoint) not B (pure wander)?** Two reasons:

1. **Determinism & validator friendliness.** Each segment is a shortest path, so
   connectivity is provable by the same `single-source BFS` machinery the formal
   route uses. A pure-wander tour would need a separate "is it actually
   connected" proof and would risk self-intersection / dead-ends. The
   `courtyard-path-network` spec's "single-source tree" contract is preserved
   *per segment*.
2. **Semantic fit.** The waypoints *are* the scenic stops (借景停顿). A garden
   曲径 is not random wandering; it is a designed sequence of views. Routing
   through the scenic anchors makes the path readable as a tour, not a meander.

**Why A (obstacle fallback) on top of C?** Two waypoints might still admit a
straight shortest path that cuts through the rockery. The obstacle set forces
such a segment to route around, guaranteeing the "曲" the user asked for even
when waypoints happen to line up.

**Alternatives considered**

- *B (pure wander: random walk with goal-pull).* Rejected: hardest to keep
  deterministic, non-self-intersecting, and connected; conflicts with the
  shortest-path-tree contract; reads as "lost" not "designed".
- *A alone (only obstacle avoidance, no waypoints).* Rejected: when the garden
  has few obstacles the path is still straight — not enough winding.
- *C alone (waypoints, no obstacle fallback).* Rejected: a waypoint pair can
  still line up with the rockery and cut through it, breaking the winding.

**Rationale.** Waypoints give designed, readable bends; obstacle-avoidance
guarantees the bends even in sparse gardens; per-segment shortest-path keeps the
validator tractable. This is the smallest mechanism that delivers "曲" without
breaking the existing path contract.

### D5: Waterside steps + a real bridge, replacing the removed spike-row 汀步

**Choice.** The waterside zone gets `PATH_WATERSIDE`: a sequence of
`stone_brick_stairs` descending to the waterline, then a **slab bridge**
(oak/spruce slabs at the water surface y) crossing the pond to the 亭. The 汀步
(stepping stones) that were removed in `compound.py:3177` (they rendered as "一列
小尖刺" — a row of independent mini-mountains) are **not** restored. The bridge
replaces them: a slab is a flat, walkable, water-surface block — exactly what
汀步 wanted to be but couldn't as `rockery_block`.

**Why a slab bridge reads correctly when stepping stones didn't.**
`myvillage:rockery_block` carries a mountain-shaped block model that does not
fuse across cells, so a row of them reads as a row of spikes. A
`minecraft:oak_slab` / `spruce_slab` is a flat half-block sitting at the water
surface — it reads as a plank bridge, which is the historical form of a 园林亭桥
approach anyway.

**Alternatives considered**

- *Restore stepping stones as flat `minecraft:stone`.* This was the earlier
  attempt (`compound.py:3181` note) and was removed because "an unrelated row of
  mossy stones cutting across the pond read as clutter rather than water." A
  *bridge* (slabs, with implied posts) reads as intentional structure, where a
  *stone row* reads as clutter.
- *No crossing; reach the 亭 from the shore only.* Rejected by the user (D5 of
  the discussion: 小桥 must be included). With the 假山 now an island (水心假山),
  the 亭 is on the shore but the *bridge to the island 假山* is the scenic
  centerpiece of the tour.

**Rationale.** The user explicitly wants the bridge. A slab bridge is the
correct implementation (flat, walkable, reads as structure) where both prior
汀步 attempts failed. The stairs handle the waterline descent; the slab handles
the crossing.

### D6: 月洞门 is the material boundary — a passage parcel, not a wall motif

**Choice.** The 月洞门 becomes a real **passage parcel**: a穿墙通道 through a
garden wall, with the property that **the material boundary lives at the 月洞门**.
Cells before the 月洞门 (including the formal-axis approach segment) are formal
material (青石); cells after the 月洞门 are tour material (苔石). The 月洞门 is
physically both a space-gate and a material-gate — crossing it enters the garden
world.

This requires a new `moon_gate_passage` parcel node (distinct from the
`moon_gate` *motif* in `ops.py:2463`, which is just a decorative wall pattern).
The passage is voxel-walkable (it is a hole through a wall with the motif carved
around it), and the motif `moon_gate` is applied to the surrounding wall cells
so the hole reads as a 圆洞门.

**Alternatives considered**

- *Material boundary at the garden-band start, no 月洞门 parcel.* Rejected: the
  月洞门 is the culturally-correct threshold (穿过去就是另一个世界), and without a
  real passage the tour route has no defined *start* — the winding path would
  bleed into the formal yard with no gate.
- *Keep 月洞门 as motif only, no passage.* Rejected: a motif is paint on a wall;
  the player cannot walk through it. The tour route needs a physical entry.

**Rationale.** The tour route's readability depends on a defined start. The
月洞门 is that start both physically (passage) and semantically (material
boundary). It is the smallest change that makes "穿过去就进游径" real.

### D7: 水边廊 is a shoreside `covered_gallery` layout variant

**Choice.** The 水边廊 (waterside gallery) is not a new parcel *type* — it reuses
the existing `covered_gallery` parcel, placed along the pond shore. The new
behavior is a *placement* rule (a gallery variant routed along the waterline),
not a new geometry. Its floor resolves through `PATH_GALLERY` (wood-stone mix,
D2), and it is a waypoint / endpoint of the tour route.

**Alternatives considered**

- *A new `waterside_gallery` parcel type.* Rejected: the geometry (roofed
  walkway on posts/columns) is exactly `covered_gallery`; only the placement
  (along shore vs along yard) differs. A new type duplicates the renderer.

**Rationale.** The 游赏 route needs a 水边廊 stop. `covered_gallery` already
renders a roofed walkway; adding a shore-routing placement variant is the
minimal way to get the stop without a new parcel type.

### D8: 仆役房/厨房 is a new service archetype — the life route's endpoint

**Choice.** A new `service_house` archetype (仆役房/厨房/仓库) is added as the
endpoint of the 生活 route, placed along the service alley (`side_alley`). It is
a small, plain building (no decoration tier) reusing the existing sub-building
machinery; its `door_info["front"]` becomes a mandatory path endpoint, so the
formal BFS reaches it through the 夹道.

**Alternatives considered**

- *Reuse `front_row` (倒座) as the service house.* Rejected: the 倒座 is the
  formal 前院 building; overloading it loses the formal/service distinction the
  life route is meant to express. A separate `service_house` keeps the
  vocabulary clean.
- *Defer the service house.* Rejected: without it, the life route has no
  endpoint and is not a route — the user named it explicitly.

**Rationale.** The life route's readability depends on a real destination. A
plain `service_house` archetype is the minimal addition.

### D9: Coverage is all three families; vocabulary is a per-family subset

**Choice.** The surface-zone model applies to `chinese_mansion`,
`chinese_courtyard`, and embedded `small_courtyard`. Each family realizes only
the zones it has space for:

| zone \ family            | mansion | courtyard (1-进) | small_courtyard |
|--------------------------|---------|------------------|-----------------|
| 中轴通途 (PATH_FORMAL)    | ✅      | ✅               | ✅              |
| 天井/院心 (HEART)         | ✅      | ✅               | ✅ (has parcel) |
| 廊道 (GALLERY)            | ✅      | ✅               | ❌ (no gallery) |
| 夹道 (ALLEY)              | ✅      | ⚠️ (if 倒座)     | ❌              |
| 游园 (TOUR)               | ✅      | ❌               | ❌              |
| 水岸 (WATERSIDE)          | ✅      | ❌               | ❌              |
| 月洞门 passage            | ✅ (new)| ❌               | ❌              |
| 水边廊                    | ✅ (new)| ❌               | ❌              |
| 仆役房                    | ✅ (new)| ⚠️ (optional)    | ❌              |

This mirrors the existing `enclosure-planning` propagation note: the mansion is
the richest form and gets the full vocabulary; the courtyard gets the formal +
heart + gallery subset; the small-courtyard gets formal + heart only. A zone a
family does not have is simply absent — the model is layout-agnostic per
`fix-courtyard-ground-walkability` D8.

**Alternatives considered**

- *Mansion-only, propagate later.* Rejected by the user (D4 of the discussion:
  三个都做). The propagation of the enclosure model to courtyard is *already*
  deferred; doing path-surface-zoning for all three now means the surface layer
  is uniform even if the enclosure layer is not yet.

**Rationale.** User decision (scope). The per-family subset keeps each family on
only the zones it has, with no forcing.

### D10: `forbidden_blocks` re-tuning — mossy_stone_bricks over mossy_cobblestone

**Choice.** The 苔石 surface (`PATH_TOUR`) resolves to `minecraft:mossy_stone_bricks`,
**not** `minecraft:mossy_cobblestone` or `minecraft:cobblestone`. The
`chinese_mansion.json` and `chinese_courtyard.json` `forbidden_blocks` lists
already forbid both cobblestone variants (`chinese_mansion.json:174`). Rather
than loosen that ban, use `mossy_stone_bricks` (not forbidden, visually
continuous with the stone-brick courtyard register).

**Alternatives considered**

- *Allow `mossy_cobblestone` for the tour path.* Rejected: loosens a style ban
  for one slot; cobblestone reads as European/vernacular, off-register for a
  江南 garden. `mossy_stone_bricks` keeps the material register consistent while
  still reading as "mossy/old".

**Rationale.** Respect the existing style ban; pick the unbanned, on-register
alternative.

### D11: 连廊建筑化 — a 廊 is a 3D renderer, not a floor tile (Arc 5)

**Choice.** Factor the existing gallery geometry into one shared
`_place_covered_gallery_3d(compound, style, cells, base_y, water_side,
roof_form="single_slope")` renderer, and route both the 水边廊 upgrade and the
new 主院 抄手游廊 through it. Per gallery cell it writes four layers:

- **Floor** — `PATH_GALLERY` (the surface-zone material is preserved; the 廊 zone
  still resolves to oak_planks).
- **Columns** — `COLUMN` (stripped_dark_oak_log), one post every other cell,
  2 tall (`base_y+1` .. `base_y+2`). Reused verbatim from
  `_place_covered_galleries` (`compound.py:1783-1786`).
- **Balustrade** — a new `BALUSTRADE` slot (`minecraft:dark_oak_fence` /
  `minecraft:spruce_fence`), 密排 single-row on the open side (the side
  `water_side` points at). The 密排 pattern is reused from
  `ops.balustrade` (`ops.py:1786`); the slot is `style.primary("BALUSTRADE")`.
- **Roof** — a **single-slope** roof: `ROOF_DARK` stairs tilted low toward the
  open/water side (`base_y+3`) and high toward the yard (`base_y+4`), so it
  drains away from the buildings and reads as a lean-to 水廊 rather than a flat
  slab. (The 亭's `chinese_round_ridge` slab cap is the flat-roof precedent we
  are deliberately *not* copying for the gallery — a gallery wants a slope.)

The `waterside_gallery` parcel keeps its `covered_gallery` type and its
4-adjacent-to-water invariant (locked by `test_path_termini`); only the rendered
geometry changes from "one floor block" to the four-layer renderer. The 主院
抄手游廊 is mansion-only and optional (skipped on a variant whose 主院 is too
narrow for a 3-wide gallery clear of the 厢房).

**Slot:** `BALUSTRADE` is added to `chinese_mansion.json` only (not courtyard —
it has no 3D gallery). It is already in `OPTIONAL_MATERIAL_SLOTS`
(`style.py:29`), so `check_style_policy` stays green with no code change.
Vanilla-clean.

**Alternatives considered**

- *Keep the gallery as a floor tile, ship Arc 1-4 only.* Rejected by the user's
  in-game review: a 水边廊 that is one oak_planks block "完全不对". The surface
  zoning is correct but the building is absent.
- *Reuse `ops.balustrade` directly.* Rejected: it targets a massing-graph
  `Node`, not a compound parcel. Arc 5 copies its 密排 *pattern* into the
  compound-grid renderer, keeping one rendering path per layer.
- *Flat slab roof (like the 亭).* Rejected: a flat cap reads as a lid, not a
  水廊; the single-slope reads as a real lean-to gallery roof.
- *Add the 抄手游廊 to courtyard too.* Rejected: courtyard already has
  `_place_covered_galleries` (correct); Arc 5 only adds the mansion 主院 gallery,
  which is currently absent.

**Rationale.** The geometry already exists in `compound.py`; Arc 5 factors it
into one renderer rather than re-inventing, and upgrades the fake 水边廊 to a
real building. Single-slope + fence balustrade reads as a 江南 水廊; vanilla-only.

### D12: 房屋布局修复 — split 后院/花园 by back_d, bound the 绣楼, shrink the 台基 (Arc 6)

**Choice.** Apply the already-computed `_mansion_yard_depths` split to the
enclosure bands and the 绣楼 bounds:

- **Bands.** `generate_mansion` calls `_mansion_yard_depths(lot_d,
  variant.garden_scale)` and cuts `back_yard_band = [ermen_z+1, ermen_z+back_d]`
  / `garden_band = [ermen_z+back_d+1, lot_d-2]`. The 月洞门 screen wall (the
  后院↔花园 material boundary) moves to `garden_band[0]`. `_layout_garden` and
  `place_moon_gate_screen` key off `garden_band[0]` relative-ly, so the garden
  features shift north as a block — placement stays valid while `garden_d ≥ 6`
  (verified: medium lot garden_d ≈ 15).
- **绣楼 bounds.** `_plan_mansion_enclosure` requires `tz0 + td - 1 <
  garden_band[0]` so the 绣楼 stays in the 后院. `back_d ≥ 15`, `td = 13` → ≥1
  cell clearance. A single tower keeps its off-axis placement (a common 江南
  form) but the hard-west pin (`t1_x = axis-1-tw`) is relaxed so the tower can
  center or mirror when the 后院 has room.
- **台基 shrink.** `_realize_mansion_enclosure` stops full-width-filling the
  主院; the plinth covers only the 敞厅 + 厢房 + 抄手游廊 footprints (±1-cell
  skirt). The 主院 heart falls through to `_place_yard_ground`, which resolves it
  to `GROUND_YARD_OPEN` grass — so the 主院 reads as 草+砖结合, not an empty
  stone slab.

**Guards.** `validate_mansion` adds `back_yard_garden_overlap`
(`back_yard_band[1] < garden_band[0]`) and `tower_overlaps_garden` (no 绣楼
footprint cell coincides with a 花园 feature cell). Both prevent the regression
from recurring.

**Layout principle (confirmed with the user).** 中轴主骨 (门屋 → 前院 → 仪门 →
主院 → 二门 → 后院 → 花园) with the 花园 free-form (苏州园林-style asymmetry);
the 绣楼 just needs its own 后院, not symmetry. Only the 后院/花园 split, the
绣楼 bounds, and the 台基 footprint change — the building roster, facings, and
the 中轴 itself are untouched.

**Alternatives considered**

- *Roll back to the pre-`rebuild-mansion-enclosure-plan` z-band layout.*
  Rejected: the enclosure model is the intended direction; the bug is that
  `back_d` was computed but unused, not that the model is wrong.
- *Force the single tower to center.* Rejected by the user: an off-axis 绣楼 is
  a valid 江南 form; the requirement is only that it leaves the 花园.
- *Leave the 台基 full-width.* Rejected: the 主院 reads as an empty stone slab;
  the user's "院心留草" direction is explicit.

**Rationale.** The depth split already exists; applying it is a localized
fix that gives the 绣楼 a lawful home and the 主院 a heart, without touching the
enclosure model's design.

### D13: 水岸低视角修复 — 水边廊 is one composed run, not the whole noisy shore (Arc 7)

**Choice.** The Chunky/first-person review exposed a visual failure that the
structural checks missed: the freeform pond shoreline was being converted almost
cell-for-cell into a 3D 水边廊, so roof stairs, posts, railings, bridge slabs,
lily pads, and the island 假山 collapsed into one low-angle mass. The fix is to
keep the pond freeform but make the gallery architectural:

- Select exactly one short, straight, two-cell-deep gallery run on a clean bank
  (water-edge row + one dry back row), capped at 7 water-edge cells.
- Exclude pond water, the island rockery, the bridge, and their clearance rings
  from gallery placement.
- Place waterside balustrades on the gallery edge itself (supported by the
  gallery floor), while main-yard gallery balustrades stay outside the footprint
  so door-front walkways remain clear.
- Count post cadence along the post line, not the whole footprint, so a short
  3×2 water gallery still gets columns.
- Reduce lily-pad density and clear lily pads from the bridge/gallery visual
  lanes.

**Alternatives considered**

- *Keep all freeform-shore cells as gallery cells.* Rejected: this is the exact
  clutter failure seen in review. A noisy pond edge is good for water, not for a
  roofed building footprint.
- *Delete the 水边廊.* Rejected: the route vocabulary needs a real waterside
  stop; the problem is placement scope, not the concept.
- *Make the gallery one cell deep only.* Rejected: a one-cell strip cannot carry
  a readable post line, railing, and walkable floor without blocking itself. A
  two-cell strip is the smallest stable gallery footprint.

**Guards.** `validate_mansion` now fails `waterside_gallery_clutter:*` when the
gallery is missing, oversized, not a straight two-cell-deep strip, not shore
adjacent, or overlaps water/rockery/bridge. It fails `pond_lily_clutter:<cell>`
when lily pads enter the bridge/gallery clear-water lanes. `test_path_termini.py`
locks the same invariants for the six shipped mansion seeds.

### D14: 水亭定位修复 — the pavilion must sit on a pond bank (Arc 8)

**Choice.** Focused water-court review showed the previous visual evidence was
not just compressed or poorly angled: `garden_pavilion` could be placed at the
far west end of the garden while the pond/rockery/bridge composition lived on
the east side. The cause was stale placement math: after the rockery moved into
the pond as an island, the pavilion still used the old west-rockery band.

The fix is to select the pavilion from dry 3x3 pond-bank candidates:

- Candidate footprint must be inside the garden band and dry.
- Candidate footprint must avoid pond water, the island rockery, perimeter wall,
  screen wall, existing buildings, and other blocked cells.
- At least one footprint cell must be 4-adjacent to pond water.
- Prefer the west pond bank so the pavilion faces across the water instead of
  hiding behind the north waterside gallery or perimeter wall.

**Rendering guard.** `tools/render_structure.py` now accepts `--target X Y Z` so
focused review can aim the camera at the water pavilion / pond center. The bbox
scan still selects chunks and reports framing diagnostics, but the look-at point
is no longer forced to the whole cluster center.

**Guards.** `validate_mansion` fails
`garden_pavilion_detached_from_pond:*` when the pavilion is missing, overlaps
water, or has no footprint cell 4-adjacent to pond water. `test_path_termini.py`
locks this for all six shipped mansion seeds.

## Risks / Trade-offs

- **[Tour waypoint routing may produce a path that overlaps the formal backbone
  on the approach to the 月洞门]** → The 月洞门 approach segment is formal
  material (青石) by D6; if the tour path's first waypoint sits before the gate,
  the cell belongs to two routes. **Mitigation:** the tour route's waypoint list
  *starts inside* the 月洞门 (D6 material boundary), so the formal/tour overlap
  is exactly zero cells by construction. Acceptance: no cell is in both the
  formal BFS tree and the tour waypoint set.
- **[Zone derivation for 天井心 reuses the under_eave ring, which is already
  stone — the material change is just stone→grey-brick, subtle]** → The
  visual delta between `stone_bricks` and a grey-brick block may be small.
  **Mitigation:** `GROUND_YARD_HEART` lists a distinctly-grey entry (e.g.
  `minecraft:stone_bricks` is current; the new slot's primary can be a darker
  grey block if a vanilla one reads clearly). Defer exact block to visual
  review; the *zone* is correct regardless of block choice.
- **[Slab bridge may not span if the pond is wider than the slab row]** → The
  bridge is one slab-row wide; a wide pond needs a longer row. **Mitigation:**
  the bridge length equals the pond's narrow-axis span at the crossing cell; the
  crossing cell is chosen at the pond's narrowest point (亭-to-shore shortest
  water crossing). Acceptance: the bridge reaches both shores.
- **[The 月洞门 passage through a garden wall requires a wall segment that does
  not exist yet in every mansion]** → The garden band currently borders the 后院
  with no wall between them (`chinese-mansion-compound` "花园 band opens directly
  off 后院"). A 月洞门 needs a wall to pierce. **Mitigation:** add a low garden
  screen wall between 后院 and 花园 with the 月洞门 opening, OR site the 月洞门
  in an existing perimeter wall if the tour starts from a side yard. Open
  question (see below) — needs a placement decision during implementation.
- **[Service house in the small courtyard family has no room]** → A
  `small_courtyard` lot may be too small for a service house. **Mitigation:** the
  service house is optional in courtyard/small-courtyard (D9 table ⚠️); it is
  mandatory only in mansion. The model tolerates its absence.
- **[Regenerating all three families' NBTs is a large byte-stability
  surface]** → The byte-stability guard in
  `test_chinese_courtyard_regression.py` pins `cultivation_sect_*` and
  `medieval_*`. **Mitigation:** the guard is extended to assert the surface-zone
  families *change* (expected) while the untouched families stay byte-identical.
- **[Adding 6 new style slots to three style JSONs is config churn]** →
  `chinese_mansion.json`, `chinese_courtyard.json`, and possibly
  `cultivation_town.json` each gain slots. **Mitigation:** slots are cheap
  (list of block ids); the `tools/check_style_policy.py` linter catches
  undefined-slot references. Acceptance: linter green.
- **[Arc 5: the 抄手游廊 may not fit a narrow 主院]** → A 3-wide gallery along the
  主院 edge could collide with a 厢房 footprint on a small `courtyard_size`.
  **Mitigation:** the gallery is optional per side; if the clear strip between
  the 厢房 and the 内门/敞厅 flank is < 3 wide, that side is skipped (not
  mandatory). Acceptance: the mansion still validates; voxel-walkability is the
  backstop.
- **[Arc 6: 台基 shrink may leave door cells unsupported or punch holes]** →
  Shrinking the plinth to the building footprints means 主院 heart cells are no
  longer PLATFORM_STONE; `_place_yard_ground` must cover them (grass) at y=0, and
  door/path cells must remain walkable. **Mitigation:** `_place_yard_ground`
  already runs on every non-building parcel cell and resolves open_sky → grass;
  the existing `ground_layer_hole` + voxel-walkability checks catch any gap.
  Acceptance: no `ground_layer_hole` / `voxel_*` error after the shrink.
- **[Arc 6: garden_d shrinks after the back_d split, the 亭 may not place]** →
  `pav_cz = gy0 + feature_d + 2`; a small garden_d could push `pav_cz > gy1` and
  the 亭 is silently dropped (no error, just absent). **Mitigation:** verified
  medium/large lots give garden_d ≈ 15 (亭 places); small lots keep garden_d ≥ 6
  and the 亭 may drop — acceptable (the 亭 has no mandatory check). Acceptance:
  at least the medium/large variants place the 亭; the small variant still
  validates.
- **[Arc 6: the relaxed single-tower pin could re-introduce overlap on edge
  lots]** → Loosening the hard-west pin lets the tower center, but a careless
  center could still touch a 花园 feature if back_d is tight. **Mitigation:** the
  `tower_overlaps_garden` + `back_yard_garden_overlap` validators (D12) are the
  hard gate; the placement change is bounded by `tz0+td-1 < garden_band[0]`
  regardless of x.

## Migration Plan

Per the discussion's staging, the change is decomposed into four independently
testable stages (mirroring the `rebuild-mansion-enclosure-plan` task structure):

1. **Stage 1 — Material zoning wiring (no shape change, no new nodes).** Add the
   six new style slots; extend `_place_yard_ground` zone derivation (天井心 ring,
   gallery, alley); extend `_route_complete_path` to resolve the formal overlay
   through `PATH_FORMAL` instead of `GROUND_PATH`. The tour path temporarily
   reuses `PATH_FORMAL` straight. **Acceptance:** mansion/courtyard yards read
   as grass + grey-brick ring + wood-stone gallery + brick alley + bluestone
   formal axis; still straight everywhere.
2. **Stage 2 — Tour route winds.** Implement waypoint routing (D4) + obstacle
   fallback; site the 月洞门 as material boundary (D6); resolve the tour overlay
   through `PATH_TOUR`. **Acceptance:** the garden path visibly winds through
   假山南 → 水岸拐点 → 亭, mossy-stone-brick, distinct from the straight formal
   axis.
3. **Stage 3 — Path termini.** Add the `moon_gate_passage` parcel (D6), the
   shoreside `covered_gallery` 水边廊 variant (D7), and the `service_house`
   archetype (D8). Wire their `door_info`/endpoints into the route collectors.
   **Acceptance:** all three routes have real endpoints; the life route reaches
   a service house; the tour route starts at a 月洞门.
4. **Stage 4 — Waterside + full coverage + validation.** Implement
   `PATH_WATERSIDE` stairs + slab bridge (D5); extend validators for the new
   zones (material assertions, tour connectivity, bridge span); extend the
   byte-stability guard; regenerate all three families' NBTs under both
   `--profile vanilla` and `--profile full`. **Acceptance:** validators green,
   untouched families byte-identical, staged manual review of ≥2 NBTs per
   family.

No runtime world migration (pre-1.0 mod).

## Open Questions

- **Where does the 月洞门 wall live?** The garden band currently opens directly
  off the 后院 with no wall. Does the tour route pierce (a) a new low garden
  screen wall between 后院 and 花园, or (b) an existing side perimeter wall?
  Default: (a) a new screen wall — it makes the 月洞门 a real threshold into the
  garden. Defer to layout implementation.
- **Is the `service_house` present in `chinese_courtyard` (1-进) or mansion
  only?** D9 marks it ⚠️ for courtyard. Default: mansion only this change;
  courtyard gets it if the lot has room, else absent. Defer.
- **Exact block for `GROUND_YARD_HEART`?** `stone_bricks` (current) is barely
  distinguishable from `GROUND_YARD_UNDER_EAVE`. Default: try a darker vanilla
  grey block; if none reads clearly, accept the subtle delta (the *zone* is
  correct). Defer to visual review.
- **Does the slab bridge need posts/rails, or is a bare slab row enough?** A
  bare slab row reads as a plank bridge; posts would read as a proper 亭桥.
  Default: bare slab row for this change; add posts/rails in a follow-up if the
  review wants more structure. Defer.
- **Waypoint selection: hand-authored per variant, or derived from garden
  features?** Default: derived — waypoints are the centroids of
  `garden_rockery` (south face), `garden_pond` (nearest shore corner), and
  `garden_pavilion`. This keeps it layout-agnostic. Confirm during
  implementation.
