## Why

The courtyard/mansion compounds ship a **single-material path** (`GROUND_PATH` =
`minecraft:gravel`) routed as a single-source shortest-path tree from the street
gate, overlaid on a two-zone ground layer (`open_sky` → grass, `under_eave` →
stone). It is walkable (it passes `_voxel_walk_bfs`), but it **reads as no real
road**: every formal axis, garden approach, and waterside step is the same flat
gravel. There is no visual or narrative difference between the ritual 正路, the
back-of-house 夹道, and a 游赏曲径 — so the player sees "a gravel stripe", not
"three routes through a 江南大宅".

The user's vision is a readable path vocabulary of six surface kinds and three
routes, and the gap is structural, not cosmetic:

1. **The path carries no material vocabulary.** Six surface kinds — 规整青石路,
   天井灰砖铺地, 廊下木石地面, 白墙夹道, 苔石曲径, 水边石阶与小桥 — are wanted;
   one (`gravel`) is shipped. There is no way to tell the formal axis from a
   garden meander.
2. **The path algorithm is straight, but the tour route must wind.**
   `_route_complete_path` runs a single-source shortest-path BFS. A shortest path
   is a straight/diagonal line by definition. The 苔石**曲**径 is the *deliberate
   absence* of shortest-path — the algorithm cannot produce the "曲" the user
   named.
3. **Two of the three routes have no endpoints.** The 游赏 route (月洞门 → 曲径 →
   假山 → 水边廊 → 亭桥 → 花厅) and the 生活 route (侧门/夹道 → 厨房/仓库/仆役房)
   reference parcels/archetypes that **do not exist**: there is no 月洞门 passage
   (only a decorative wall motif), no 水边廊 (covered galleries are yard-routed,
   not shore-routed), and no 仆役房 archetype. A route with no destination is not
   a route.
4. **The waterside crossing was removed.** The garden 汀步 (stepping stones) were
   deleted in `compound.py:3177` because `myvillage:rockery_block` renders each
   cell as an independent mini-mountain — "一列小尖刺". The 亭-reachable crossing
   the user wants ("水边石阶与小桥") needs a different block (a flat slab bridge),
   not a restoration of the spike row.
5. **The 水边廊 is a fake building.** The `waterside_gallery` parcel (Arc 3)
   registers as a `covered_gallery` and its comment claims it "reads as a roofed
   walkway" — but the code only `grid.set`s one `PATH_GALLERY` floor block per
   shore cell (`_layout_garden` ≈ `compound.py:3607-3613`). There is **no column,
   no roof, no balustrade**. The mansion 主院 likewise has no 抄手游廊 at all
   (only the 1-进 `chinese_courtyard` has them, via `_place_covered_galleries`). A
   "廊" that is a single floor tile is not a building; surface-zoning the floor
   from gravel to oak_planks does not make it one.
6. **The 后院 and 花园 share one z-interval, so the 绣楼 sits inside the garden.**
   `generate_mansion` defines both `back_yard_band` and `garden_band` as
   `[ermen_z+1, lot_d-2]` — identical (`compound.py:3723-3724`). The
   `_mansion_yard_depths` split (`back_d` / `garden_d`) is computed but **never
   used**. The 绣楼 (`tower_house`, depth 13, placed at `ermen_z+1`) therefore
   extends the full depth of what should be its own 后院 straight into the garden
   band, overlapping the 假山 / 水池 / 亭; with a single tower it is also pinned
   to the west (`t1_x = axis-1-tw`) so the layout reads as off-center. The 主院
   台基 is meanwhile full-width solid PLATFORM_STONE across the whole yard, so the
   主院 reads as an empty stone slab.

**Root cause, single sentence:** the path layer is a *material-agnostic, shape-
agnostic overlay* — it neither classifies the surface a cell belongs to (so six
kinds collapse to one) nor varies its geometry (so it cannot wind), and the
routes that would give it meaning point at spatial nodes that were either never
built (Arc 3) or built as floor-only placeholders (Arc 5); meanwhile the
enclosure planner never separated 后院 from 花园, so the 绣楼 has nowhere lawful
to stand (Arc 6).

## What Changes

A **surface-zone layering** of the existing ground + path passes — no new layer,
no rewrite of the planning model — plus the three spatial termini that make the
routes real. Scope: `chinese_mansion`, `chinese_courtyard`, and embedded
`small_courtyard`, each realizing the vocabulary subset it has space for.

The six defects map to six arcs. Arcs 1–4 are the surface-zoning core; Arcs 5–6
(elevated in scope after the user's in-game review) make the garden's "廊" real
buildings and give the 绣楼 a lawful 后院 to stand in.

### Arc 1 — Surface zoning: material is a property of the zone, not the route

Solve "no material vocabulary" at the root. Each path/ground cell is classified
along two orthogonal axes:

- **Axis 1 (material, space property):** which zone the cell belongs to → looks
  up a surface slot. The three routes do **not** carry material.
- **Axis 2 (shape, geometry property):** which route the cell belongs to →
  straight (BFS) vs winding (waypoint). Material does not participate.

The six surface kinds become the materials of six zones:

| surface                  | zone          | layer  | slot (new)            |
|--------------------------|---------------|--------|-----------------------|
| 规整青石路 (formal)       | 中轴通途       | path   | `PATH_FORMAL`         |
| 天井灰砖铺地              | 天井/院心      | ground | `GROUND_YARD_HEART`   |
| 廊下木石地面              | 廊道           | ground | `PATH_GALLERY`        |
| 白墙夹道                  | 夹道           | ground | `PATH_ALLEY`          |
| 苔石曲径                  | 游园           | path   | `PATH_TOUR`           |
| 水边石阶与小桥            | 水岸           | path   | `PATH_WATERSIDE`      |

The three routes become zone sequences (material follows the zone each step sits
in). The yard keeps its grass (`open_sky`); only the defined zones flip to
brick/wood-stone/stone — the user's "草+砖结合".

### Arc 2 — The tour route winds via waypoint routing + obstacle fallback

Solve "the algorithm cannot curve". The formal and service routes keep the
existing single-source BFS (礼制要求直). The **tour** route is routed as a
polyline through scenic waypoints (假山南, 水岸拐点, 亭), each segment a shortest
path, with an obstacle set forcing any segment that would cut through a
rockery/pond to route around it. This is deterministic (per-segment shortest
path), validator-friendly (connectivity provable per segment), and semantically
fit (waypoints *are* the scenic stops). The `courtyard-path-network`
single-source-tree contract is preserved *per segment*.

### Arc 3 — Path termini: 月洞门 passage, 水边廊, 仆役房

Solve "routes with no destinations". Three new spatial nodes:

- **`moon_gate_passage` parcel** — a real 穿墙通道 through a garden screen wall,
  distinct from the decorative `moon_gate` motif (`ops.py`). It is the
  **material boundary**: cells before it are formal (青石), cells after are tour
  (苔石). The tour route's waypoints start *inside* the 月洞门, so formal/tour
  overlap is zero cells by construction.
- **Shoreside `covered_gallery` variant** — the 水边廊 reuses the existing
  `covered_gallery` geometry but is placed along the pond shore (a placement
  rule, not a new parcel type). Its floor resolves through `PATH_GALLERY`.
- **`service_house` archetype** — the 仆役房/厨房/仓库 endpoint of the 生活 route,
  a small plain building placed along the 夹道; its `door_info["front"]` becomes
  a mandatory path endpoint.

### Arc 4 — Waterside stairs + slab bridge, replacing the spike-row 汀步

Solve "crossing reads as spikes". `PATH_WATERSIDE` writes `stone_brick_stairs`
descending to the waterline, then a **slab bridge** (`oak_slab`/`spruce_slab` at
the water surface y) crossing to the 亭/island. A slab is a flat, walkable,
water-surface block — it reads as a plank bridge, where `rockery_block` read as
spikes. The deleted 汀步 are **not** restored.

### Arc 5 — 连廊建筑化: a 廊 is floor + columns + roof + balustrade, not a floor tile

Solve "the 水边廊 is a fake building". The current `waterside_gallery` only lays
one `PATH_GALLERY` floor block per shore cell. Arc 5 upgrades it (and adds the
missing 主院 抄手游廊) to a real covered gallery via one shared renderer:

- **New `BALUSTRADE` slot** in `chinese_mansion.json` (`minecraft:dark_oak_fence`
  / `minecraft:spruce_fence`, vanilla-clean). Registered in
  `OPTIONAL_MATERIAL_SLOTS` already, so no linter change.
- **New `_place_covered_gallery_3d()` renderer** in `compound.py` — a shared
  primitive that, per gallery cell, writes: a `PATH_GALLERY` floor (preserving
  the surface-zone material), a `COLUMN` post every other cell (2 tall, reused
  from `_place_covered_galleries`), a `BALUSTRADE` fence row on the open side
  (密排, reused from `ops.balustrade`), and a **single-slope roof**
  (`ROOF_DARK` stairs tilted low-toward-water / high-toward-yard) capping the
  columns.
- **Upgrade 水边廊**: the pond-shore `waterside_gallery` calls
  `_place_covered_gallery_3d()` with the open/balustrade side facing the water.
  Parcel type stays `covered_gallery` (the tour-route + 4-adjacent-to-water
  invariants stay).
- **Add 主院 抄手游廊** (mansion-only): east + west galleries tying the 仪门
  flanks to the 敞厅 flanks along the 主院 edges (reusing the 抄手 return geometry
  from `_place_covered_galleries`), balustrade facing the yard. Skipped on a
  variant whose 主院 is too narrow (not mandatory).

The 三件套 geometry (floor + column + roof) and the fence-rail pattern already
exist in `compound.py` (`_place_covered_galleries` ≈ `:1737`,
`_place_sect_link` ≈ `:4178`) — Arc 5 factors them into one 3D renderer rather
than re-inventing. No new external-mod id; `BALUSTRADE` resolves to vanilla
fences under both profiles.

### Arc 6 — 房屋布局修复: give the 绣楼 a 后院 and the 主院 some grass

Solve "后院 = 花园, 绣楼 sits in the garden, 主院 is a stone slab". The
enclosure planner computed a depth split but never applied it; Arc 6 applies it:

- **Separate 后院 from 花园.** `generate_mansion` calls
  `_mansion_yard_depths(lot_d, garden_scale)` and uses `back_d` to cut the
  bands: `back_yard_band = [ermen_z+1, ermen_z+back_d]`,
  `garden_band = [ermen_z+back_d+1, lot_d-2]`. The 月洞门 screen wall (the
  后院↔花园 boundary) moves to `garden_band[0]`.
- **Constrain the 绣楼 to the 后院.** `_plan_mansion_enclosure` requires
  `tower_house` north edge (`tz0 + td - 1`) `< garden_band[0]`, so the 绣楼
  stays in its own 后院. A single tower keeps its off-axis placement (a common
  江南 form) but is no longer pinned hard-west when symmetry has room.
- **Shrink the 主院 台基.** The plinth stops full-width-filling the 主院; it
  covers only the 敞厅 + 厢房 + 抄手游廊 footprints (±1-cell skirt). The 主院
  heart becomes `GROUND_YARD_OPEN` grass (resolved by `_place_yard_ground`), so
  the 主院 reads as 草+砖结合 rather than an empty stone slab.

The 中轴主骨 is preserved (门屋 → 前院 → 仪门 → 主院 → 二门 → 后院 → 花园); only
the 后院/花园 split, the 绣楼 bounds, and the 台基 footprint change. The 花园
itself stays free-form (苏州园林-style asymmetry) per the user's direction.

### Arc 11 — 参考亭复刻: replace the light water pavilion and delete the pond-side shed

After focused reference-image review, the user rejected the Arc 10 light-pavilion
compromise and requested a near-replica of the supplied heavy Minecraft scenic
pavilion. Arc 11 therefore replaces the dry-bank `garden_pavilion` with a
reference-style pavilion: raised stone base, dark wood deck, thick timber posts,
railings, trapdoor/lattice bracket details, hanging lanterns, broad dark-oak
double eaves, and grey stone roof ornaments. The pond layout now reserves a
centered dry south-bank approach so the pavilion faces the viewer with water
behind it, instead of reading as an east-wall corner object or a side-corridor
object between walls. The perimeter wall behind that approach, the side
sightlines, and the moon-gate screen behind the water are opened down to ground
level around the pavilion frontage/backdrop so Chunky review no longer sees the
pavilion through a wall corridor, over a low wall, or against a blank white
wall.

The separate right-side `waterside_gallery` / 水边廊 shed is removed from the pond
composition. Waterside semantics remain on the slab bridge, stone water edge,
and pond-adjacent pavilion rather than an extra roofed strip. To match the
reference image's setting rather than a bare mansion lawn, the pavilion scene
also adds visible side water, a small bamboo cluster, foreground flowers/grass,
and a soft stone/dirt-path approach around the platform, plus a green backdrop
behind the water to suppress the mansion-wall read in focused Chunky review.

### Why zone-owned material, not route-owned

The original framing ("a slot per route") produced an unresolvable conflict: is
the formal-axis cell crossing the 天井 made of 青石 (because formal) or 灰砖
(because it is a 天井)? Zone-owned material dissolves it — the cell is 灰砖
because it is a 天井; the formal route only decided to pass *through* this 天井.
This is the user's directive that material and route "不该分离": not two axes
merged, but material owned by **space** while route owns only **sequence +
shape**.

### Why all three families in one change

The user chose all three (`chinese_mansion`, `chinese_courtyard`,
`small_courtyard`). Each realizes only the zones it has space for (mansion gets
the full vocabulary; courtyard gets formal + heart + gallery; small-courtyard
gets formal + heart). The surface layer is uniform even though the enclosure
model is not yet propagated to courtyard (still deferred per
`compound-enclosure-planning`). A zone a family lacks is simply absent — the
model is layout-agnostic.

## Capabilities

### New Capabilities

- `path-surface-zoning`: the two-axis surface model — material follows the zone
  (six zones, six style slots), shape follows the route (formal/service straight
  via BFS, tour winding via waypoint routing + obstacle fallback). Cross-cuts
  `courtyard-ground-layer` and `courtyard-path-network`; owns the zone→slot
  table, the tour waypoint router, and the material-boundary (月洞门) rule.

### Modified Capabilities

- `courtyard-ground-layer`: the yard-fill pass resolves through **four** zone
  slots instead of two — `GROUND_YARD_OPEN` (grass, unchanged), the new
  `GROUND_YARD_HEART` (天井/院心 eave-drip ring, grey brick), the new
  `PATH_GALLERY` (廊道, wood-stone mix), and the new `PATH_ALLEY` (夹道, brick).
  The `under_eave` ring that is currently `GROUND_YARD_UNDER_EAVE` stone splits:
  天井心 ring → `GROUND_YARD_HEART`; gallery cells → `PATH_GALLERY`; remaining
  eave ring keeps `GROUND_YARD_UNDER_EAVE`.
- `courtyard-path-network`: the path overlay resolves through **three** route
  slots instead of one — `PATH_FORMAL` (中轴, straight BFS, replaces the current
  `GROUND_PATH` overlay on the formal backbone), `PATH_TOUR` (游园, winding,
  routed through waypoints), and `PATH_WATERSIDE` (水岸, stairs + slab bridge).
  The single-source-tree contract is preserved for the formal/service backbone;
  the tour route is a separate waypoint-polyline whose segments are each
  single-source trees.
- `chinese-mansion-compound`: the 花园 gains the 月洞门 passage (material
  boundary + tour start), a reference-style heavy `garden_pavilion` (Arc 11),
  and — when the lot has room — the 仆役房 along the 夹道. The separate pond-side
  `waterside_gallery` shed is deleted (Arc 11). The garden pond crossing becomes
  the slab bridge. **The enclosure sequence is repaired (Arc 6):** the
  后院 and 花园 bands are split by the previously-unused `back_d` depth, the
  绣楼 is constrained to the 后院 (no longer overlaps the 花园), the 主院 台基
  shrinks to the building footprints so the 主院 heart is grass, and the 主院
  gains 抄手游廊 (east + west, Arc 5).
- `validation`: `validate_mansion` / `validate_compound` gain zone-material
  assertions (the four ground zones and three path routes resolve to their
  declared slots), a tour-connectivity check (every tour waypoint segment is a
  connected single-source tree), and a bridge-span check (the slab bridge
  reaches both shores). `validate_mansion` also gains two layout guards (Arc 6):
  `back_yard_garden_overlap` (the 后院/花园 bands do not share a z-interval) and
  `tower_overlaps_garden` (no 绣楼 footprint cell coincides with a 花园 feature
  cell). The existing voxel-walkability and ground-hole checks are preserved.

## Impact

- **Code (planning layer, additive zoning):**
  - `tools/buildgen/compound.py` — `_place_yard_ground` zone derivation extends
    to four zones (天井心 ring, gallery, alley); `_route_complete_path` resolves
    the formal overlay through `PATH_FORMAL` and gains a `_route_tour_path`
    waypoint router + obstacle fallback for the tour overlay; a new
    `PATH_WATERSIDE` stairs+bridge writer; new `moon_gate_passage` parcel
    renderer; `service_house` archetype wiring into the endpoint collector.
    **(Arc 11)** the pond `garden_pavilion` becomes a reference-style heavy
    pavilion and the separate pond-side `waterside_gallery` shed is removed.
    **(Arc 5)** a new
    `_place_covered_gallery_3d()` renderer (floor + columns + single-slope roof
    + balustrade) used by the new 主院 抄手游廊. **(Arc 6)**
    `generate_mansion` applies `_mansion_yard_depths.back_d` to split
    `back_yard_band` / `garden_band`; `_plan_mansion_enclosure` constrains the
    绣楼 to the 后院 and relaxes the hard-west single-tower pin;
    `_realize_mansion_enclosure` shrinks the 主院 台基 to the building footprints
    (院心 → grass).
  - `tools/buildgen/archetypes.py` — the `service_house` archetype (plain, small,
    reuses sub-building machinery).
  - `tools/buildgen/styles/chinese_mansion.json`, `chinese_courtyard.json` — six
    new surface slots (`PATH_FORMAL`, `GROUND_YARD_HEART`, `PATH_GALLERY`,
    `PATH_ALLEY`, `PATH_TOUR`, `PATH_WATERSIDE`), vanilla-clean entries; plus
    (Arc 5) the `BALUSTRADE` slot and (Arc 11) the `RIDGE_ORNAMENT` slot in
    `chinese_mansion.json` only.
- **Code (preserved, untouched):**
  - `generate_subbuilding`, the `passes.PIPELINE`, `ops.py` roof/wall/door
    renderers, `rockery.py`/hero sculpt, `_voxel_walk_bfs`, `export.py` NBT
    writer (grid-only), sect/town generators. The 1-进 `_place_covered_galleries`
    is untouched (Arc 5 reuses its geometry via a shared renderer, not by
    editing it).
- **Structures:**
  - `chinese_mansion_001..006.nbt` regenerate with the six-zone surface layer +
    月洞门 + 水边廊 (real 3D gallery, Arc 5) + 主院 抄手游廊 (Arc 5) + 仆役房 +
    slab bridge + the repaired 后院/花园/绣楼 layout and the grass 主院 heart
    (Arc 6).
  - `chinese_courtyard_*` regenerate with the four-zone ground + formal/heart
    path subset.
  - `cultivation_town_*` (embedded `small_courtyard`) regenerate with the
    formal + heart subset.
- **Reports:**
  - `compound_library_report.json` / `_validation.json` gain per-zone surface
    counts + tour-waypoint + bridge-span stats.
- **Specs:** new `path-surface-zoning`; delta `courtyard-ground-layer`,
  `courtyard-path-network`, `chinese-mansion-compound`, `validation`.
- **Docs:**
  - `docs/ai-kb/` — add a "Path surface zoning" note (see-also the new spec and
    `courtyard-ground-layer` / `courtyard-path-network`).
  - `AGENTS.md` — replace the path-block paragraph with the six-zone surface
    model; note the tour waypoint router and the 月洞门 material boundary.
  - `README.md` — command list unchanged (same `/place` ids); CHANGELOG note that
    paths now read as three routes with six surfaces.
  - `CHANGELOG.md` — large-feature bump per `openspec/config.yaml` rules.tasks.
- **Compatibility:**
  - `cultivation_sect_*`, `medieval_*` NBTs stay byte-stable (byte-stability
    guard extended to assert the surface-zone families *change* while the
    untouched families stay byte-identical).
  - Vanilla-profile output unchanged (no new external ids; `mossy_stone_bricks`
    is vanilla and not in any style's `forbidden_blocks`).
  - Pre-1.0 mod; no world migration promised for placed structures.
- **Out of scope (tracked in deferred roadmap):**
  - 4-进 mansion (FUTURE in `chinese-mansion-compound`).
  - 徽派天井大屋 (still design-retention only).
  - Per-roof-form eave projection (the 1-cell under-eave ring stays the
    approximation).
  - Town-scale (`cultivation_town`) street paving vocabulary — this change is
    compound-scale.
  - 月洞门 screen-wall placement (new wall vs existing perimeter) is an open
    question for implementation, not a deferred capability.
