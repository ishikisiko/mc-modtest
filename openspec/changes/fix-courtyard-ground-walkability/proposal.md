## Why

The shipped `chinese_courtyard` compound (`chinese_courtyard_001..006.nbt`) is the "outer-yard + inner-gate + main-yard + plinth + 抄手游廊" plan rebuilt by `rebuild-chinese-courtyard`, but the **地面层 (ground layer)** and **可达路径 (path network)** never got the same treatment. The result is structurally a 四合院 but practically unwalkable — and a reviewer notices within two steps.

Three independent root causes:

1. **院子地面是踩空的 (the yard floor is hollow).** `_route_central_path` writes `style.primary("GROUND_PATH")` (gravel) at `compound y = -1` in the outer yard and `compound y = plinth_h - 1` in the main yard — a 1-cell thin strip along the BFS route. Every cell that is *not* on that strip keeps its default AIR at the same y. After the NBT is exported and placed at player-feet offset, the player's foot stands at NBT y = 1 and the walkable block underneath is NBT y = 0. Outside the strip, NBT y = 0 is AIR, so the player falls through; only along the path itself is the gravel visible and walkable. The user reads "院子中央是空的" — they are correct.
2. **Path BFS 只到正房 (the path only reaches the main hall).** `_route_central_path` (`compound.py:983`) hard-codes `start = (axis, 1)` and `goal = (axis, hall_front - 1)`. The west wing, east wing, front_row (倒座), well, fish jar, and every planting bed are **unreachable** by the central path. The 厢房 / 倒座 doors are connected to the central axis only by the player's imagination. The old `_route_circulation` side-corridor logic exists in the codebase but is only invoked by `generate_small_courtyard`; the `generate_compound` flow never branches to the side wings or the landscape features.
3. **正房/厢房 vs 倒座 落地高度不一致 (building heights are inconsistent).** `main_hall` / `side_wing` are translated to `origin_y = plinth_h`, so their floor (foundation_h=2) sits at `compound y = plinth_h + 2`. `front_row` is translated to `origin_y = 0`, so its floor sits at `compound y = fh`. With `plinth_h = 2` and `fh = 2`, the main hall's door is at `compound y = 4`, the front row's door is at `compound y = 2`. The user sees "高了一格" — the path doesn't reach the door, and there's no stairs to bridge the gap. Even if the player reaches the front_row's door from the path, climbing the 2-block plinth onto the main-yard platform is a jump, not a step.

Together: a 形制 (plan/structure) that reads correctly but a 行走 (walking) experience that fails. The fix rebuilds the ground + path layers to match the plan.

## What Changes

### Step 1 — Ground layer model (露天 vs 屋檐下)

- **Derive a `ground_kind` for every parcel cell** from the coverage relation between the cell and the structure on top of it. Three values:
  - `open_sky` — exposed to the sky (the central lawn area between 影壁 and 垂花门, the open areas around the courtyard tree and planting beds).
  - `under_eave` — covered by a roof or eave projection (covered_gallery cells, moon_platform cells, the 1-cell band outside each building's footprint that sits under the eave).
  - `interior` — inside a building (handled by the existing INTERIOR pass; not regenerated here).
- **Simplified under-eave detection (D4)** — the under-eave band is the union of three geometric sources: (a) `covered_gallery.cells` exactly, (b) `moon_platform.cells` exactly, (c) the 1-cell-wide outer ring around each `BuildingSlot.footprint`. No roof-overhang projection is computed — the 1-cell ring approximates a typical 1–2-cell Chinese eave overhang without the algorithmic complexity of a per-building roof-cast.
- **New yard-fill pass**: `_place_yard_ground(compound, style)` writes a solid ground block at every non-building, non-water, non-planting, non-tree parcel cell, at the cell's natural surface y:
  - `open_sky` cells in the **outer yard** → `minecraft:grass_block` (D2 — 草地).
  - `open_sky` cells in the **main yard** (on the plinth) → `minecraft:stone_bricks` (matches plinth material; reads as courtyard pavers set into the plinth surface).
  - `open_sky` cells in the **inner gate band** → `minecraft:stone_bricks` (continuity with the plinth apron).
  - `under_eave` cells → `minecraft:stone_bricks` (D3 — 青砖).
  - The path block then overwrites the ground tile along the routed path.

### Step 2 — Complete path network (井/鱼缸/种植区都连)

- **Endpoint registry**: every reachable goal in the courtyard registers as an endpoint cell. The full set is:
  - The street-gate entry cell (the gate opening minus 1 z-step).
  - Each `BuildingSlot`'s `door_info.front` cell — one endpoint per door.
  - Each `water_feature` cell (well) — one endpoint at the well's nearest non-blocked neighbor.
  - Each `water_jar` cell — same rule.
  - Each `planting` parcel — one endpoint at a cell adjacent to the planting boundary (so the player can step off the path onto the bed).
  - The `moon_platform` "step" (the front-most cell of the moon platform apron, where the player steps off the main-yard ground onto the moon platform).
- **Multi-source BFS**: a single pass that starts from every endpoint simultaneously, expanding 4-neighbor cells with `blocked = building_footprint ∪ water_feature ∪ water_jar ∪ planting ∪ courtyard_tree ∪ inner_gate_solid_flanks ∪ perimeter_wall`. The result is a shortest-path-tree connecting all endpoints — paths naturally overlap on shared segments, forming one continuous walkable surface.
- **台基高度处理 (D1-a, single stairs)**: where the path crosses the plinth boundary (a path cell that is *not* on the plinth immediately adjacent to a path cell that *is* on the plinth, with no path-cell transition on the same side), the renderer places one `minecraft:stone_brick_stairs[facing=<toward the lower side>]` block at the boundary, replacing the ground tile at that cell. This guarantees the player can walk from the outer-yard path up onto the plinth surface with a single step, no jumping.
- **Replace `_route_central_path`**: the existing function is removed. `_route_circulation` is kept (used by `generate_small_courtyard` and now by the upgraded small-courtyard ground+path in Step 3) but refactored to share the multi-source BFS helper.

### Step 3 — Small-courtyard also upgraded (D7 — 顺手扩)

- The `generate_small_courtyard` flow (used by `cultivation_town` street blocks) gets the same treatment at smaller scale:
  - Ground fill: `open_sky` = `coarse_dirt` (more urban, less garden), `under_eave` = `stone_bricks`.
  - Multi-source BFS for path: the same endpoint registry applies (drop endpoints that don't exist in a small courtyard, e.g. no moon platform, no front row).
  - Stairs: small courtyards don't have a plinth, so the stair pass is a no-op there.
- This is the same fix applied at half the lot size — it costs ~30 extra lines and rescues the cultivation_town street-block courtyards from the same problem.

### Compatibility and out of scope

- **Breaking (NBT regeneration)**: the 6 shipped `chinese_courtyard_*.nbt` files regenerate with different ground layers and different path footprints. Same filenames, different content.
- **Breaking (small-courtyard NBT regeneration)**: every shipped small-courtyard inside `cultivation_town_*` NBTs regenerates with the new ground + path. Since the existing output already has the same walkability bug, this is a fix, not a regression.
- **No `/myvillage` command-surface change**. `/myvillage place chinese_courtyard_001..006` and `/function myvillage:gallery/chinese_courtyard` keep working.
- **Out of scope**: 二进/三进 compounds (deferred per `docs/ai-kb/14_deferred_roadmap.md` §E); the `ground_kind` derivation does not generalize to multi-jin (would need per-yard ground fill); keep this change strictly to one-jin and small-courtyard.
- **Out of scope**: full roof-overhang eave projection (the simplified 1-cell ring is intentional; a real roof-cast is a separate capability).
- **Out of scope**: 假山 / 自由曲线水池 / 花园 / 自由铺地 (these would all break the orthogonal grid).

## Capabilities

### New Capabilities

- `courtyard-ground-layer`: every courtyard compound (one-jin `chinese_courtyard` and small-courtyard unit) SHALL emit a solid ground block at every non-building parcel cell, classified into `open_sky` (露天) and `under_eave` (屋檐下) by the simplified coverage rule; the ground tile SHALL sit at the cell's natural surface y (NBT y = 0 in the outer yard, NBT y = `plinth_h` on the main-yard plinth); the path block SHALL overwrite the ground tile along the routed path; vanilla-profile output SHALL resolve every ground block to its `minecraft:` fallback.
- `courtyard-path-network`: every courtyard compound SHALL route a single multi-source BFS path network whose endpoint set includes the street-gate entry, every building's `door_info.front`, every `water_feature`, every `water_jar`, and one entry per `planting` parcel; the network SHALL be connected (every endpoint reachable from every other); path cells SHALL be written at the cell's natural surface y and SHALL sit on top of the ground tile; where the path crosses the plinth boundary the renderer SHALL place a single `minecraft:stone_brick_stairs` block at the boundary so the player can step up/down without jumping; vanilla-profile output SHALL resolve the stair block to its `minecraft:` fallback.

### Modified Capabilities

- `courtyard-compound`: the path-network requirement (existing §"Corridors connect wings along the courtyard") is replaced by the `courtyard-path-network` requirement; the compound SHALL additionally verify that every endpoint reachable from the street gate through the multi-source BFS; small-courtyard SHALL additionally verify the same endpoint set minus the elements it doesn't have (moon platform, front row); "compound variants are combinatorial" is unchanged.

## Impact

- **Code**:
  - `tools/buildgen/compound.py`:
    - New: `_derive_ground_kinds(compound) → dict[Cell2, str]` returning a `ground_kind` per parcel cell using the simplified rule (covered_gallery + moon_platform + 1-cell ring around each footprint).
    - New: `_place_yard_ground(compound, style)` writing the yard-fill pass.
    - New: `_collect_path_endpoints(compound) → list[Cell2]` returning the endpoint set.
    - New: `_multi_source_bfs(endpoints, blocked, lot) → dict[Cell2, int]` running the unified BFS.
    - New: `_place_plinth_stairs(compound, style)` walking the path-plinth boundary and emitting stairs.
    - New: `_route_complete_path(compound, style)` orchestrating the three above and writing the path block.
    - Removed: `_route_central_path` (its only caller was `generate_compound`).
    - Refactored: `_route_circulation` now delegates to `_multi_source_bfs` (small-courtyard flow).
    - Modified: `generate_compound` calls `_place_yard_ground` before `_route_complete_path`; the order ensures path overwrites ground tile but plinth-overwrite pass runs after (path is at `plinth_h - 1` and the plinth is at `0..plinth_h-1`, so path sits on top).
    - Modified: `_add_chinese_perimeter` unchanged (perimeter is on the boundary, doesn't conflict with yard ground).
    - Modified: `_layout_main_yard` plinth fill unchanged.
    - Modified: `validate_compound` adds 5 new error codes (see `tasks.md` §3).
    - Modified: `generate_small_courtyard` invokes `_place_yard_ground` and `_route_complete_path` with small-courtyard-specific style slot values.
  - `tools/buildgen/styles/chinese_courtyard.json`: adds two new slots `GROUND_YARD_OPEN` (vanilla: `minecraft:grass_block` only, since outer-yard 露天 is grass) and `GROUND_YARD_UNDER_EAVE` (vanilla: `minecraft:stone_bricks`, `minecraft:polished_andesite`).
  - `tools/buildgen/styles/cultivation_town.json`: adds the same two slots with different values (small-courtyard is more urban — `GROUND_YARD_OPEN` = `minecraft:coarse_dirt`, `GROUND_YARD_UNDER_EAVE` = `minecraft:stone_bricks`).
- **Assets**:
  - `src/main/resources/data/myvillage/structure/chinese_courtyard_001..006.nbt` regenerate (breaking — every cell at NBT y = 0 / `plinth_h` is now a solid block instead of AIR).
  - Every `cultivation_town_*.nbt` containing small-courtyard units regenerates the embedded small-courtyard ground layer.
  - `reports/compound_library_report.json` / `reports/compound_library_validation.json` regenerate (path_cell counts change, ground_cell counts appear).
  - `reports/compound_library_validation.json` gains a new `ground_cells` stat per compound.
- **Specs**:
  - New: `courtyard-ground-layer/spec.md`.
  - New: `courtyard-path-network/spec.md`.
  - Delta: `courtyard-compound/spec.md` (replaces the corridor-connectivity requirement with a reference to the new path-network spec).
- **Docs**:
  - `docs/ai-kb/10_civic_family.md` adds a "Ground + path layer" section pointing to the two new specs.
  - `docs/ai-kb/14_deferred_roadmap.md` §E (Courtyard-form expansions) gets a note that 二进/三进 needs the same ground+path treatment re-derived per-yard (deferred).
  - `AGENTS.md` adds a one-paragraph note that courtyard ground+path is data-driven through the two new specs.
  - `CHANGELOG.md` notes the NBT regeneration as a fix (the previous output was unwalkable).
- **Compatibility**:
  - `cultivation_sect_*` and `medieval_*` libraries stay byte-stable (this change touches only `generate_compound` + `generate_small_courtyard` + the two style JSONs).
  - Vanilla-profile output resolves every new ground / stair block to its `minecraft:` fallback.
  - Pre-1.0 mod; no world migration promised for placed structures.