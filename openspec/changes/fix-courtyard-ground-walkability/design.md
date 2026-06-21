## Context

The `rebuild-chinese-courtyard` change shipped a structurally-correct 一进 plan (outer yard + 垂花门 + main yard + plinth + 抄手游廊 + 月台 + 院中树 + 鱼缸) but did not address the **ground layer** or the **path network**. As a result, the 6 shipped `chinese_courtyard_*.nBT` files have three walkability defects a reviewer notices immediately:

1. The path is a 1-cell-wide gravel strip floating in an otherwise-AIR yard floor.
2. The path stops at the main hall — side wings, front row, well, fish jar, and planting beds are unreachable through the path.
3. The 倒座 (front_row) sits at compound y = 0 (no plinth) while the main hall sits at compound y = `plinth_h` (on the plinth); the height gap is unbridged — no stairs, just a jump.

Two pieces of prior art make this fix cheap:

1. **`cultivation-mountain-siting` / `sect-compound-layout` (existing)** already model "link endpoints" (`gate_opening`, `door_info.front`) as first-class reachability targets with explicit "reachable through link" semantics. The new path-network spec reuses the same endpoint pattern at courtyard scale.
2. **`town-realization` (existing)** already has a `validate_town_block` reachability rule (`every parcel SHALL remain reachable from the street network`). The new validator codes mirror that wording.

Constraints:

- **No new external-mod dependency**: `chinese_courtyard` is the vanilla-cleanest compound family. The new `GROUND_YARD_OPEN` / `GROUND_YARD_UNDER_EAVE` slots must resolve to vanilla blocks under both `vanilla` and `full` profiles.
- **No `/myvillage` command-surface change**: filenames preserved, command list unchanged.
- **Python-only**: no Java realizer change (this is a static-NBT-library fix, same as `rebuild-chinese-courtyard`).
- **Spec-compatible additions**: existing `courtyard-compound` "corridors connect wings" requirement is replaced (not removed) — the new path-network spec subsumes it. All other invariants (perimeter closed, gate on perimeter, water/planting structural, 垂花门 between yards, plinth + moon platform adjacent to main hall) are honored.
- **Keep the plinth**: per user direction (D-confirmed), the `platform_tier` axis (`none` / `stone_2` / `xumi_3`) and the plinth fill are kept as-is. The fix is about the *transition* between plinth and non-plinth ground, not about removing elevation.

## Goals / Non-Goals

**Goals:**

- A reviewer walks into a placed `chinese_courtyard_NNN.nbt` from the street gate and **every cell is walkable** — no falling through, no jumping up, no dead ends.
- The path explicitly connects to every door (正房 / 东厢房 / 西厢房 / 倒座), every landscape feature (well / fish jar), and at least one cell of every planting bed. Multi-source BFS makes this trivial to verify.
- The yard reads as two ground kinds: 露天 (open-sky grass / paver) and 屋檐下 (covered stone_bricks). The boundary between them follows real architectural lines — gallery edge, building eave ring, moon platform outline.
- The plinth edge is traversable with a single step (`stone_brick_stairs` placed at the boundary).
- The same fix applies to the small-courtyard units embedded in `cultivation_town_*` NBTs (D7 — 顺手扩).
- The change is reusable: a future 二进/三进 change extends the ground+path treatment by running the same pass per yard band.

**Non-Goals (explicitly deferred, see `docs/ai-kb/14_deferred_roadmap.md` §E):**

- 二进/三进 compounds. Multi-jin needs per-yard ground fill (the current one-pass `_place_yard_ground` doesn't generalize); deferred until the multi-jin change is proposed.
- Full roof-overhang eave projection. The simplified 1-cell ring around each footprint approximates a 1–2 cell Chinese eave; a real roof-cast (per-building, per-roof-form) is a separate capability and a much larger code change.
- 假山 / 自由曲线水池 / 自由铺地 (these all break the orthogonal grid and would need a continuous-coverage algorithm; deferred).
- Per-`chinese_*` roof-form under-eave variation (e.g. 歇山 has a 抱厦 skirt that projects further than 悬山). Currently the 1-cell ring is identical for all roof forms; the per-form overhang variant is left as a follow-up.
- Mod-decor floor tiles (asphalt paths, lantern-lit paving). Deferred to a separate aesthetic change.

## Decisions

### D1: Single stairs at plinth boundary, not a stair_pass

**Choice:** When a path cell at compound y = `plinth_h - 1` is adjacent to a path cell at compound y = -1 (outer yard) along the same x or z, place a single `minecraft:stone_brick_stairs[facing=away_from_plinth]` block at the boundary cell, replacing the ground tile at that cell. No full stair_pass.

**Alternatives considered:**

- *Full stair_pass (3–4 stairs ascending).* Rejected: the plinth is 1 cell of plinth_h=1 or 2 cells of plinth_h=2; a full stair_pass would visually dominate the plinth edge and require re-arranging the building's foundation_h to land at the top of the stairs. The single-stair solution is enough to make the transition step-able; the plinth's other cells keep the cleaner flush-paver look.
- *Slab ramp (no stairs).* Rejected: visually weak and doesn't read as "step".
- *No transition; let the player jump.* Rejected: this is exactly the bug the user reported.

**Rationale:** Single stair at the boundary is the smallest change that makes the transition walkable. The `stone_brick_stairs` block is from `PLATFORM_STONE` slot (already in the style), so no new slot needed for the stair itself; the stair is rendered with a `facing` derived from the boundary normal.

### D2: Outer-yard 露天 = `grass_block` (草地)

**Choice:** All `open_sky` cells in the **outer yard band** resolve to `minecraft:grass_block` (D2 — 草地). Main-yard `open_sky` (which sits on the plinth) resolves to `minecraft:stone_bricks` (paver) because the plinth is already stone.

**Alternatives considered:**

- *All `open_sky` = `coarse_dirt`.* Rejected: the outer yard is the "前院迎客" zone — a grass courtyard reads as a real Beijing courtyard, not a bare village plot. The main-yard pavers reinforce the "内院肃穆" hierarchy.
- *All `open_sky` = `dirt`.* Rejected: too plain for a 一进府邸.
- *Random mix of grass / coarse_dirt / dirt per cell.* Rejected: adds noise without meaning; the outer/main distinction is the meaningful axis.

**Rationale:** Outer/main distinction is a deliberate aesthetic hierarchy that the user already accepted (the plinth is the architectural signal that you crossed from outer to main yard). The ground-block distinction reinforces that signal visually.

### D3: `under_eave` = `stone_bricks` (青砖)

**Choice:** Every `under_eave` cell resolves to `minecraft:stone_bricks`.

**Alternatives considered:**

- *`polished_andesite`.* Rejected: reads colder, more urban; stone_bricks matches the rest of the plinth / 垂花门 material register.
- *Random mix with `smooth_stone`.* Rejected: adds noise; under-eave should read as one continuous 铺地.

**Rationale:** Continuity with the plinth material. A viewer walking under the 抄手游廊 sees the same brick as the plinth they just stepped off, which makes the gallery feel attached rather than added.

### D4: Simplified under-eave detection = covered_gallery ∪ moon_platform ∪ 1-cell ring around footprint

**Choice:** A cell is `under_eave` iff it lies in any of these three sets:

1. `covered_gallery.cells` — the 抄手游廊 footprint, exactly.
2. `moon_platform.cells` — the 月台 apron, exactly.
3. The 1-cell-wide Chebyshev ring around any `BuildingSlot.footprint` — i.e., for every cell `(x, z)` where `max(|x - fx|, |z - fz|) == 1` for some footprint cell `(fx, fz)`.

The 1-cell ring is a deliberate approximation: real Chinese eaves overhang 1–2 cells, and the eave shadow falls roughly one cell beyond the wall. We don't model overhang per roof form.

**Alternatives considered:**

- *Full roof-overhang projection per roof form.* Rejected: requires per-form eave geometry (different overhangs for 硬山/悬山/歇山/卷棚), per-form orientation (front vs side vs back), and edge-case handling for non-rectangular footprints. The 1-cell ring approximates the visual outcome without the complexity. A future "real eave projection" capability can replace this when the per-form geometry stabilizes.
- *Infer `under_eave` from a top-down Y scan (does any block above this cell have a roof tag?).* Rejected: the data model doesn't carry a top-down coverage map; computing it on the fly would duplicate the roof-overlay logic that's currently scattered across passes.

**Rationale:** Three explicit sources, deterministic, O(N) where N is the lot area. Easy to extend with new sources (e.g. a future "门廊 overhang" parcel type just adds another set).

### D5: Multi-source BFS replaces single-source BFS

**Choice:** Replace `_route_central_path` (single-source BFS from gate to main hall) with a multi-source BFS where every endpoint is a seed. The BFS produces a `dict[Cell2, int]` of distance-from-nearest-endpoint; cells with finite distance are path cells.

**Alternatives considered:**

- *Steiner tree (minimum spanning tree over endpoints + Steiner points).* Rejected: overkill for 6–10 endpoints; multi-source BFS naturally produces a near-optimal tree with much less code.
- *Sequential single-source BFS (gate → door1, then door1 → door2, ...).* Rejected: order-dependent (different order produces different trees); harder to verify.
- *Connect each endpoint to a single "spine" cell (the central axis), then route each endpoint's segment via single-source BFS.* Considered: this is essentially what the current code does, but it requires deciding the spine and endpoint-segment order. Multi-source BFS skips that decision.

**Rationale:** Multi-source BFS is the canonical "make everything reachable from everything" algorithm for grid graphs with unit edge cost. The implementation is < 30 lines, the proof of correctness is one-paragraph textbook, and the validation is trivial: every endpoint must have finite distance.

### D6: Path overwrites ground tile (same y, different block)

**Choice:** The path is written at the same y as the surrounding ground tile (`y = -1` in outer yard, `y = plinth_h - 1` on main-yard plinth). The path block has `["DETAIL", "GROUND", "PROTECTED"]` tags with `priority = DETAIL (70)`. Since the yard-fill pass runs first (with `DETAIL (70)` too, but without PROTECTED), the path's PROTECTED wins against the material_variation pass but doesn't block the yard fill from being placed — the order is "yard fill, then path" so the path block overwrites the ground tile at the same cell.

**Alternatives considered:**

- *Place path at NBT y = 1 (the cell under the player's feet in NBT space) and ground tile at NBT y = 0.* Rejected: the player walks on top of NBT y = 0; a path block at NBT y = 1 means the player walks on the path *block* but the path *block* sits one cell above the ground. Visually OK but the gravel is "floating" 1 cell above the lawn. The user's complaint was "和地面同层" — path and ground at the same y is the right interpretation.
- *Make the path block a layer above the ground tile.* Rejected: same as above.

**Rationale:** Same-y path + ground is the cleanest interpretation of "和地面同层". The path's PROTECTED tag prevents the material_variation pass from randomly re-coloring the gravel.

### D7: Small-courtyard also upgraded in the same change

**Choice:** `generate_small_courtyard` (the path used by `cultivation_town` street blocks) gets the same ground fill + multi-source BFS + (no stairs, since no plinth) treatment.

**Alternatives considered:**

- *Defer small-courtyard to a follow-up change.* Rejected: the small-courtyard has the *same bug* (path doesn't reach doors, ground is hollow). Fixing only the one-jin would leave `cultivation_town` courtyards with the same defect. Doing both at once is ~30 extra lines and unifies the path-routing code through one multi-source BFS helper.
- *Replace small-courtyard's `_route_circulation` with a thin wrapper that delegates to the new multi-source BFS.* This is what we do (D5).

**Rationale:** Same bug, same fix, same code path. The small-courtyard's endpoint set is just the subset of the one-jin's endpoint set (no moon platform, no front row, no 影壁).

### D8: Endpoint registry is data-driven, not hard-coded by name

**Choice:** Endpoint collection iterates over `compound.parcel_nodes` and `compound.building_slots` and applies a small per-type rule:

| source | rule |
|---|---|
| perimeter_wall.gate_opening | first cell of gate minus 1 z-step |
| building_slot (any) | `door_info.front` (if present) |
| water_feature | every cell of the water parcel |
| water_jar | every cell of the jar parcel |
| planting | first cell adjacent to the planting boundary (alphabetical) |
| moon_platform | front-most cell of the moon platform (closest to gate) |

This is implemented as one `_collect_path_endpoints(compound) → list[Cell2]` function with an internal per-type switch. Adding a new endpoint source (e.g. a future 假山 parcel) is one extra branch.

**Alternatives considered:**

- *Hard-code the endpoint list inside `generate_compound`.* Rejected: tight coupling between the lot layout and the path router; a future layout change (二进) would have to update both. Data-driven via the parcel-node collection lets the same endpoint logic apply at any layout.
- *Endpoints live on `CompoundGraph.meta`.* Considered: this would make the endpoint set explicit and reviewable, but adding a `meta["path_endpoints"]` field duplicates information already on the parcel nodes. The function is cheap to call and the result is debuggable.

**Rationale:** Function-based collection is the smallest change that makes the path router layout-agnostic.

### D9: Style slots are vanilla-only and short

**Choice:** Two new slots, both vanilla-only:

```json
"GROUND_YARD_OPEN": ["minecraft:grass_block"],
"GROUND_YARD_UNDER_EAVE": ["minecraft:stone_bricks", "minecraft:polished_andesite"]
```

For `cultivation_town.json`, `GROUND_YARD_OPEN` is `["minecraft:coarse_dirt", "minecraft:dirt"]` instead of grass (more urban).

**Alternatives considered:**

- *Add the slots to the shared style.* Rejected: the slot semantics are courtyard-specific (other styles don't need ground fill). Keep them on the per-style JSON.

**Rationale:** Two slots, vanilla-only, narrow value lists. The `style.primary(slot)` lookup falls through to the first entry, so the value is deterministic.

## Risks / Trade-offs

- **[NBT byte-stability for worlds that placed old `chinese_courtyard_*`]** → Every cell at NBT y = 0 (or `plinth_h`) is now a solid block instead of AIR. Old placements keep old blocks because they're already in chunk NBT. **Mitigation:** document in `CHANGELOG.md` as a fix-NBT-regeneration; the mod is pre-1.0.
- **[Visual regression: a 1-cell ring around each building may overlap the building's facade (e.g. doorstep, column) on small footprints]** → The ring is 1-cell wide Chebyshev, so it includes the cell directly adjacent to the wall. If a building has a 1-cell-deep porch (檐廊) the ring overlaps the porch floor. **Mitigation:** the path / yard ground is written at DETAIL(70) priority, lower than OPENING(40) but higher than the building's INTERIOR(60); a conflict at a doorstep is fine because the path's PROTECTED tag prevents material_variation from re-coloring the doorstep. If a future aesthetic review shows an over-paved look, the ring can shrink to `max(|dx|, |dz|) <= 1` and exclude the cell directly adjacent to `door_info.front` — leave that as a follow-up tuning knob.
- **[The plinth stair block replaces a ground tile at the boundary, which might be the player's preferred approach cell to a door]** → If the stair block replaces a path cell, the player walks on the stair (stone_brick_stairs is a half-block; the player walks on the lower step). **Mitigation:** the stair is placed at the boundary cell, which is *outside* the door cell; the door cell is still plain ground/path.
- **[Multi-source BFS may produce a tree where the same physical path segment is the unique path for two endpoints, hiding a connectivity bug]** → Validator runs the BFS once, computes the tree, and checks every endpoint has finite distance. The tree's edge-count is reported in `validate_compound` stats; if edge_count < endpoint_count - 1 the validator flags `path_network_not_a_tree`.
- **[Small-courtyard endpoint set is smaller than one-jin's; if the same validator runs on both, it must skip missing endpoint types]** → The validator reads the parcel-node collection at validation time, so a small-courtyard without `moon_platform` simply has no moon-platform endpoint. The reachability check is "every endpoint has finite distance from every other endpoint" — vacuously true for an empty endpoint set.
- **[Existing `_route_central_path` had subtle "raise ValueError on no route" semantics that the multi-source BFS doesn't replicate]** → The multi-source BFS doesn't raise on no-route; instead, the validator's `endpoint_unreachable` error reports which endpoints are isolated. **Mitigation:** add an `assert_path_network_connected` precondition that `generate_compound` calls before writing the path block; raises `ValueError` if any endpoint is isolated, preserving the old "fail fast" behavior at generation time. The validator catches the slow-fail case at validation time.
- **[Per-cell grass_block vs stone_bricks boundary may look noisy at the lot edge]** → The yard-fill pass respects `lot_bounds`; cells outside the lot are never written. The boundary between in-lot `grass_block` and outside-lot (whatever the world has there) is a clean edge.

## Migration Plan

1. **Step 1 first** (ground fill only): implement `_place_yard_ground`, regenerate the 6 NBTs, diff previews. The ground layer should now read as a continuous lawn under the path; the path is still a 1-cell strip but the player no longer falls off it.
2. **Step 2 second** (path network): implement `_collect_path_endpoints`, `_multi_source_bfs`, `_route_complete_path`, `_place_plinth_stairs`. Regenerate 6 NBTs. The path should now visibly connect every door to every other door.
3. **Step 3 third** (small-courtyard): apply the same code path to `generate_small_courtyard`. Regenerate `cultivation_town_*.nbt`. Verify byte-stable on `cultivation_sect_*` and `medieval_*`.
4. **Validate and close**: `validate_compound_library` green; cultivation / medieval regression guards green; `CHANGELOG.md` notes the fix; staged manual acceptance review places ≥2 NBTs in-game to confirm.

No runtime migration (no saved-world NBT migration is performed or promised).

## Open Questions

- **Should the grass_block outer yard use a leaf-block or tall grass decoration in the central lawn area?** Default: no (the open_sky cells are pure grass_block, no decoration). A future aesthetic change could add 苔藓 carpet under the 院中树. Defer.
- **Should the `coarse_dirt` open_sky variant for small-courtyard also support random `dirt` patches?** Default: yes, via the slot's second entry. The `material_variation` pass picks one per cell; the result is a stochastic mix. Reviewer can request a fixed mix in a follow-up.
- **Should the `_place_plinth_stairs` block be drawn as `stairs[facing=...]` with `half=bottom` (default) or with `half=top` (so the player walks up to a half-block rather than onto a full block)?** Default: `half=bottom` (the standard "step up" orientation). If the visual review shows the step as too abrupt, switch to `half=top` and add a fence-rail behind it. Defer to visual review.