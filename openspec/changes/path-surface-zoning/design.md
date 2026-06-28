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
