## Why

The `chinese_courtyard` family shipped by `rebuild-chinese-courtyard` is a real 一进 北京四合院, but two ceilings have been hit:

1. **可行走性在 3D 体素层断裂 (the path network is unreachable in 3D voxel space).** `fix-courtyard-ground-walkability` rebuilt the ground + path layers with a 2D-cell multi-source BFS and validated it with "every endpoint reached" — but the validation is graph-only. A real Minecraft walkability probe (`tools/buildgen/_explore_walk.py` throwaway, since deleted) run on `chinese_courtyard_001` (seed 20260614) shows: the player enters at `(axis=23, z=1)` standing on y=0; the 影壁 at `z=2` spans x=21..25 (5 wide, 7 tall) covering the axis, so the only standable y at z=2 is y=7 (on the screen-wall roof) — unreachable. The detour the path router claims to route must cross `(22, 3)` which carries a 5-tall COLUMN; and even past it `z=5..11` is the `front_row` footprint crossing the axis with no side alley. The 整个 main yard (z=17..33) and the 正房门 at `(23, 33)` are **never reached** — visited set is 576 cells, all in the outer-yard ring. Validator says green; the player says "堵住了". The fix is a 3D voxel-walkability validator that replaces the 2D graph check.

2. **一进四合院是民居规格,不是古代大户府邸.** The user has confirmed this is the wrong target. The `rebuild-chinese-courtyard` design explicitly deferred multi-jin compounds (`docs/ai-kb/14_deferred_roadmap.md` §E.2) with a sketch of `jin_count ∈ {1, 2, 3}`; the z-band abstraction (`outer_yard_band` / `inner_gate_band` / `main_yard_band`) was deliberately written to generalize. The follow-up was always planned. The user wants **江南大宅** (Suzhou-style deep mansion): 照壁→大门→门厅→仪门→主院敞厅→后院楼阁→花园山水, 3 进 minimum, with 楼阁 and 真·山水.

In parallel, the project needs a **mod-owned decorative block family** so the 假山 (rockery) can be sub-block-precision without depending on Chisels & Bits or falling back to vanilla `minecraft:stone`. The `myvillage:wall_plaque` precedent (`add-custom-plaque-blocks`) already proves the architecture: register a `DeferredRegister.Blocks` id, ship a `blockstate` + `models/` + `textures/` tree, place from the Python generator. 假山 is the first instance of this pattern beyond plaques; pond-edge stones / garden lattice / pavement / ridge ornaments are natural follow-ups that should plug into the same protocol.

## What Changes

This is one change with three intertwined arcs, captured together so the 3D-walkability validator and the decor-block protocol are designed against a real instance (the mansion + its rockery), not in the abstract.

### Arc 1 — `chinese_mansion` family: 3-进 江南大宅

New compound family, new NBT library. `chinese_courtyard` stays untouched (it remains the small-lot / town-block one-进 courtyard; the small-courtyard town unit still uses `generate_small_courtyard`).

- **3-进 z-band sequence** (generalizes the one-进 `outer/main` split via the planned `jin_count` axis): 照壁 → 大门 → 前院 (轿厅/倒座) → 仪门 → 主院 (敞厅 + 厢/花厅) → 二门 → 后院 (绣楼 + 藏书楼) → 花园. Each 进 carries its own `(z0, z1)` band plus exactly one inner gate parcel between consecutive yards.
- **江南特色 parcels (new parcel-node types)**:
  - `open_hall` (敞厅) — the 正房 front facade is open (no front wall), resolving through a `FACADE_OPEN` slot; the roof is carried by standoff columns. This is the single biggest "this is 江南 not 北京" signal.
  - `tower_house` (楼阁, 2 stories) — placed in the 后院. Reuses the existing `multi-story-massing` capability (`stories=2` + floor slab + stairwell + per-story facade band); the `main_hall` already allowed `stories=2` so this is a new archetype not a new mechanism.
  - `garden` (花园) — a dedicated parcel zone behind the 后院, non-axis (sits across the lot width), containing `garden_pond` + `garden_rockery` + `garden_pavilion` + 曲径.
- **Variant axes**: `jin_count` (`3` shipped; `4` deferred), `gate_form` (门厅形制), `garden_scale` (none / small / large), `tower_count` (1 / 2 — 绣楼 / 绣楼+藏书楼), `roof_grade` (reuse the four `chinese_*` forms). Deterministic template table keyed on `seed % len(TEMPLATES)` (same pattern as `rebuild-chinese-courtyard`).
- **江南形制 fixes inherited from the user's "堵住" complaint** (these are forced by the new layout, not a separate courtyard-fix change):
  - **照壁侧立, not on the axis** — the screen wall sits to one side of the gate, blocking the sightline to the main axis without blocking the passage. (Real 江南 照壁 practice; opposite of the 北京 courtyard's axis-centered 影壁.)
  - **倒座 does not cross the axis without a side alley** — the `front_row` footprint leaves a 1-2 cell alley between itself and the perimeter wall, so off-axis circulation exists.
  - **仪门 has 2-3 passage cells, not 1** — opens at least `axis-1`, `axis`, `axis+1` so the detour routes through.

### Arc 2 — `mod-decor-block-family`: mod-owned decorative block protocol

A new capability spec defining the common contract for any future `myvillage:` decorative block. 假山 is the first instance; pond-edge / lattice / pavement / ridge follow the same protocol.

- **Per-class block id** (architecture B from the design discussion): `myvillage:rockery_block`, with future `myvillage:pond_stone` / `myvillage:garden_lattice` / `myvillage:garden_pavement` / `myvillage:ridge_ornament` as separate ids. NOT one `myvillage:decor_block` super-id (would explode the blockstate space and entangle unrelated decor classes).
- **Common blockstate shape**: `variant` ∈ {class-specific catalog} × `facing` ∈ {north, south, east, west} × `moss_level` ∈ {none, light, heavy} (the last two are protocol-level — every decor class SHALL expose them even if a class only uses one value).
- **Common asset layout**: `assets/myvillage/blockstates/<id>.json`, `assets/myvillage/models/block/<id>/<variant>_<facing>_<moss>.json`, `assets/myvillage/textures/block/<id>/<variant>_<moss>.png`. The blockstate file is generated from a manifest by a Python build helper so the asset tree is reproducible (same pattern as `plaque-block-family`'s frame manifest).
- **Common registration path**: `DeferredRegister.Blocks` in `ModBlocks.java`, one `Block` subclass (or one `BlockBehaviour` config) per id with a class-specific `VoxelShape` lookup.
- **Profile exemption** (carried over from existing rule): the `myvillage:` self-namespace is always legal under both `vanilla` and `full` profiles — no `minecraft:` fallback is required because the mod ships the assets.

### Arc 3 — `garden-rockery`: 假山 as the first decor block instance + 水池 as a freeform water parcel

- **`myvillage:rockery_block`** — depth-2 implementation (sub-block-precision model + VoxelShape, NOT a runtime BlockEntity). Reuses the `sect_mountain.py` `DerivedMountain` heightfield + value-noise + skirt paradigm at 1/4 scale to derive a 16×16×16 voxel field per variant offline, then converts solid voxels to a `.json` block model + a merged `VoxelShape`.
- **Variants (角色化, ~30-50 total)**: `peak` (峰顶, 8-10 variants) / `slope` (山腰, 8-10) / `base` (山脚, 6-8) / `corner` (转角, 4-6) / `standalone` (孤赏石, 4-6). `base` + `slope` variants expose a standable top face (玩家可登 mid-mountain); `peak` variants do not (陡峭). No 蹬道 (climbing-path) variant in this change — deferred.
- **`garden_pond` parcel** — freeform water body, NOT the current strict `_rect` rectangle. Uses a 2D value-noise binalization over the pond bbox (seed-driven, deterministic) to produce an irregular shoreline, then writes `minecraft:water` at y=-1 inside the shoreline. Optional 汀步 (stepping stones) cross the pond as a 1-cell path of `myvillage:rockery_block[variant=standalone]`.
- **`garden_rockery` parcel** — places `myvillage:rockery_block` variants driven by a `tools/buildgen/rockery.py` heightfield (seed + bbox → which cells are rockery, which variant each cell gets, which moss_level). Composes with `garden_pond` so the rockery meets the water (山脚入水).
- **`garden_pavilion` parcel** — small open-sided 亭, reuses `cultivation-form-vocabulary`'s `COLUMN` slot + a `chinese_round_ridge` (卷棚) roof. Standalone, in the garden or on a rockery peak.

### Arc 4 — `courtyard-voxel-walkability`: replace the 2D-cell BFS reachability check with a 3D voxel-walkability BFS

The validator-level fix for the "堵住" complaint. Applies to **all** courtyard compounds (`chinese_courtyard`, `chinese_mansion`, small-courtyard) — this is the single change that turns "validator says reachable, player says blocked" into "validator catches the block".

- **Voxel walkability rule (standard MC autostep on)**: a cell `(x, y, z)` is STANDABLE iff the block at `y-1` is solid (foot support), and blocks at `y` and `y+1` are non-solid (body + head clearance). Two STANDABLE cells are STEP-ADJACENT iff they're 4-neighbors in (x, z) AND `|y_a - y_b| ≤ 1` (auto-step up 1, free-fall any).
- **Validator contract**: `validate_compound` / `validate_small_courtyard` SHALL run a 3D BFS from the gate-entry STANDABLE column over STEP-ADJACENT cells, and SHALL assert every door-front column, water/jar adjacent cell, planting edge cell, and moon-platform cell has at least one STANDABLE cell in the visited set.
- **New error codes**: `voxel_unreachable_door:<archetype>`, `voxel_unreachable_endpoint:<cell>`, `voxel_step_cliff:<cell_a>-><cell_b>` (two adjacent path cells with `|Δy| ≥ 2` and no stair), `voxel_blocked_by_solid:<cell>` (a path cell whose body or head plane is occupied by a STRUCTURE/ROOF block).
- **Stair placement pass generalization**: `_place_plinth_stairs` (currently axis-only) is renamed `_place_band_transition_stairs` and extended to bridge **every** pair of adjacent path cells where `|Δy| ≥ 2` with N ascending `stone_brick_stairs`. This eliminates the 3-block cliffs at the inner-gate / plinth boundary that the current axis-only pass misses (the 41 dy=±3 transitions found in the explore probe).
- **Backwards-compat for `chinese_courtyard`**: the 6 existing `chinese_courtyard_*.nbt` regenerate with (a) the 照壁 moved off-axis (江南 form adopted retroactively — the user's "大户府邸" intent overrides the original 北京 one-进 fidelity), (b) the 倒座 leaving a side alley, (c) the 垂花门 opening 2-3 cells, (d) transition stairs at every band boundary. This is a **breaking NBT regeneration**, documented as a fix.

### Arc 5 — 徽派天井大屋 design retention (no implementation)

A `huipai-tianjing-mansion` spec is written as a **design sketch only** — it captures the form vocabulary (天井 model replacing 院子, 堂 sequence 门堂→享堂→寝堂, 四水归堂 roof-water collection, 马头墙 stepped gable) so a future change does not redesign from scratch. The spec is explicitly marked `Status: design retention, not implemented`; it has Requirements but they are all prefixed with `FUTURE:` and SHALL NOT be validated by any current validator. The deferred-roadmap §E gains an E.3 entry pointing here.

## Capabilities

### New Capabilities

- `chinese-mansion-compound`: the 3-进 江南大宅 compound family — z-band `jin_count` master axis, 江南特色 parcels (`open_hall`, `tower_house`, `garden`, `garden_pond`, `garden_rockery`, `garden_pavilion`), variant axes, the entry sequence (照壁侧立 → 大门 → 前院 → 仪门 → 主院敞厅 → 后院楼阁 → 花园).
- `mod-decor-block-family`: the common contract for `myvillage:` decorative blocks — per-class id, common `variant × facing × moss_level` blockstate, common asset layout, common registration path, profile exemption. 假山 is the first instance.
- `garden-rockery`: 假山 as the first decor block instance — `myvillage:rockery_block` depth-2 (model + VoxelShape, no BlockEntity), variant catalog by role, heightfield-driven placement via `tools/buildgen/rockery.py`, plus the `garden_pond` freeform water parcel and `garden_pavilion` 亭.
- `courtyard-voxel-walkability`: 3D voxel-walkability BFS replacing the 2D-cell reachability check, applied to all courtyard compounds; the `_place_band_transition_stairs` generalized stair pass; the new voxel error codes.
- `huipai-tianjing-mansion` (design retention only): form vocabulary for 徽派天井大屋 as a future capability, with `FUTURE:`-prefixed requirements and no current validator coverage.

### Modified Capabilities

- `courtyard-compound`: the "Chinese one-courtyard axial layout" requirement gains the 照壁侧立 / 倒座 side alley / 垂花门 multi-cell-passage mandates (inherited from the voxel-walkability fix; the 北京 axis-centered 影壁 is dropped in favor of the 江南 side-standing 照壁 — see design D2).
- `multi-story-massing`: explicitly notes that `tower_house` (绣楼 / 藏书楼) is a new archetype consuming the existing `stories=2` + floor slab + stairwell mechanism; no mechanism change.
- `cultivation-form-vocabulary`: notes that `chinese_round_ridge` (卷棚) is now also used for `garden_pavilion` (亭), not just 书房-class buildings.
- `validation`: gains the `chinese_mansion` library entry + the voxel-walkability error codes.
- `style-profile`: `chinese_mansion` profile gains `FACADE_OPEN` (敞厅 front), `GARDEN_PATH` (曲径), and the `ROCKERY_STONE` / `GARDEN_PAVEMENT` / `POND_STONE` slots (all vanilla-clean — `myvillage:` ids are exempt; the slot entries are fallback blocks used only when the decor block is unavailable, which under normal mod load never happens).

## Impact

- **Code (form)**:
  - `tools/buildgen/ops.py` — 敞厅 facade renderer (open front, standoff columns, no front wall); 楼阁 archetype builder (`build_tower_house`, `stories=2`); 亭 builder (reuses cultivation pavilion geometry).
  - `tools/buildgen/rockery.py` (new) — `derive_rockery(seed, bbox, params) → heightfield + variant assignment`, mirroring `sect_mountain.DerivedMountain` at 1/4 scale.
  - `tools/buildgen/rockery_models.py` (new, offline) — converts a 16×16×16 voxel field to `.json` block model + merged `VoxelShape` Java code; emits the variant catalog.
- **Code (layout)**:
  - `tools/buildgen/compound.py` — `_compute_yard_bands(jin_count, lot_d)` generalization (one-进 path preserved via `jin_count=1`); `_layout_front_yard` / `_layout_main_yard` / `_layout_back_yard` / `_layout_garden` split; new `_place_band_transition_stairs` replacing `_place_plinth_stairs`; `_collect_path_endpoints` per-进 seeding; voxel-walkability checks in `validate_compound` / `validate_small_courtyard`.
  - `tools/buildgen/groups.py` — new `chinese_mansion` group binding the style profile, archetype roster (`main_hall`, `open_hall`, `side_wing`, `flower_hall`, `front_row`, `tower_house`, `garden_pavilion`), and scale parameters.
- **Java**:
  - `src/main/java/com/example/myvillage/block/ModBlocks.java` — register `myvillage:rockery_block` (first decor-block id); the `Block` subclass reads `variant` / `facing` / `moss_level` and dispatches to a `VoxelShape` table.
  - `src/main/java/com/example/myvillage/block/RockeryBlock.java` (new) — `BlockBehaviour` with class-specific `getShape` returning the per-variant `VoxelShape`.
- **Assets**:
  - `assets/myvillage/blockstates/rockery_block.json` — variant × facing × moss_level entries, generated from manifest.
  - `assets/myvillage/models/block/rockery_block/*.json` — one per (variant, facing, moss) combination, generated offline by `rockery_models.py`. ~40 variants × 4 facing (rotation handled by vanilla model x/y rotation) × 3 moss = ~40 model JSONs (facing + moss via texture overlay, not separate geometry).
  - `assets/myvillage/textures/block/rockery_block/*.png` — base stone / andesite / moss-overlay textures at 16×16.
- **Structures**:
  - `src/main/resources/data/myvillage/structure/chinese_mansion_001..006.nbt` — new library (3-进 江南大宅, 6 visibly distinct variants).
  - `src/main/resources/data/myvillage/structure/chinese_courtyard_001..006.nbt` — **breaking regeneration** with 照壁侧立 / 倒座 side alley / 垂花门 multi-cell / transition stairs.
- **Reports**:
  - `reports/compound_library_report.json` / `compound_library_validation.json` — gain `chinese_mansion` entries; `chinese_courtyard` entries update for the regeneration.
  - New `voxel_reachability` stat per compound (visited-cell count, unreachable-endpoint count, cliff-count).
- **Specs**:
  - New: `chinese-mansion-compound`, `mod-decor-block-family`, `garden-rockery`, `courtyard-voxel-walkability`, `huipai-tianjing-mansion`.
  - Delta: `courtyard-compound`, `multi-story-massing`, `cultivation-form-vocabulary`, `validation`, `style-profile`.
- **Docs**:
  - `docs/ai-kb/10_civic_family.md` — gains a 江南大宅 section + a "mod decor blocks" subsection.
  - `docs/ai-kb/14_deferred_roadmap.md` — §E.2 marked ✅ partially realized (3-进 shipped; 4-进 still deferred); §E.3 added for 徽派天井大屋 design retention; §E notes the `courtyard-voxel-walkability` capability covers the previously-implicit "ground+path per-jin" caveat.
  - `AGENTS.md` — gains a paragraph on the `chinese_mansion` family and the mod-decor-block protocol; notes the voxel-walkability validator replaces the 2D graph check.
  - `README.md` — adds `/myvillage place chinese_mansion_001..006` and `/function myvillage:gallery/chinese_mansion`.
  - `CHANGELOG.md` — large-feature bump `0.15.0 → 0.16.0` (new family + new capability class + breaking NBT regeneration + new mod block).
- **Compatibility**:
  - `cultivation_sect_*`, `cultivation_town_*`, `medieval_*` libraries stay byte-stable (the voxel-walkability validator runs on them too, but they are already walkable; if any defects surface they are fixed in a separate small change).
  - Vanilla-profile output: every `myvillage:` decor block is legal (self-namespace exemption); every `minecraft:` block resolves normally.
  - Pre-1.0 mod; no world migration promised for placed structures.
- **Out of scope (tracked in `docs/ai-kb/14_deferred_roadmap.md` §E)**:
  - 4-进 compounds (含 第四进 花园深化 / 跨院) — sketch captured in `chinese-mansion-compound` spec as a future extension.
  - 假山 蹬道 (climbing-path variant) — `garden-rockery` spec notes this as a future variant.
  - 徽派天井大屋 implementation — `huipai-tianjing-mansion` spec is design retention only.
  - Additional decor block classes (pond-edge stones / garden lattice / pavement / ridge ornaments) — `mod-decor-block-family` spec is the checklist for each; first follow-up is `add-pond-stone-decor`.
