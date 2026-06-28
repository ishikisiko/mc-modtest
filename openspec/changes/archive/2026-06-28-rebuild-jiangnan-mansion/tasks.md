## 1. Voxel-walkability validator + generalized stair pass (`courtyard-voxel-walkability` spec)

- [x] 1.1 In `tools/buildgen/compound.py`, add a `NON_SOLID_STATES` set and `is_solid(state) -> bool` helper covering vanilla passable blocks (air, water, lava, torches, signs, buttons, levers, rails, carpets, flowers, saplings, tall grass, vines, lanterns).
- [x] 1.2 Add `_standable_ys(compound, x, z, y_lo=-3, y_hi=12) -> List[int]` returning every y where the autostep STANDABLE rule holds at `(x, z)`.
- [x] 1.3 Add `_voxel_walk_bfs(compound, start_xyz, lot_w, lot_d) -> Set[Tuple[int,int,int]]` running the 3D STEP-ADJACENT BFS (autostep up 1, free-fall any) from the gate-entry STANDABLE column.
- [x] 1.4 Add `_gate_entry_standable(compound) -> Optional[Tuple[int,int,int]]` finding the lowest STANDABLE y at `(axis_x, z=1)` (just inside the perimeter gate).
- [x] 1.5 Rename `_place_plinth_stairs` to `_place_band_transition_stairs` and extend it: walk every 4-neighbor path-cell pair, where `|Δy| ≥ 2` place N `stone_brick_stairs[facing=<uphill>, half=bottom]` blocks bridging the gap; skip pairs where either cell is in `building_cells()` or is a `door_info["front"]` cell.
- [x] 1.6 Extend `validate_compound` with the new error codes: `voxel_unreachable_door:<archetype>`, `voxel_unreachable_endpoint:<cell>`, `voxel_step_cliff:<cell_a>-><cell_b>`, `voxel_blocked_by_solid:<cell>`. Add `voxel_reachability` stat (visited count, unreachable count, cliff count).
- [x] 1.7 Extend `validate_small_courtyard` with the same voxel checks (skip plinth-cliff checks; small-courtyard has no plinth).
- [x] 1.8 Add a probe script `tools/buildgen/probes/voxel_walk_probe.py` (read-only diagnostic, NOT shipped) that prints the visited set and unreachable endpoints for a given compound — for debugging the validator.

## 2. `chinese_courtyard` regeneration (照壁侧立 + 倒座 side alley + 垂花门 multi-cell + transition stairs)

- [x] 2.1 In `_layout_outer_yard` (compound.py), change the 影壁 placement: place the screen wall off-axis (e.g. at `axis_x + 2` or `axis_x - 2`, decided by seed), 1-2 cells wide, never on the axis. Rename parcel-node type from `screen_wall` to `screen_wall` (kept; `meta.form ∈ {jingbi, zhaobi}` distinguishes 北京 / 江南).
- [x] 2.2 In `_layout_outer_yard`, when placing `front_row` (倒座), leave a 1-2 cell alley between the footprint and the perimeter wall on at least one side (east or west). The alley SHALL connect the gate area to the 仪门 / 垂花门 area.
- [x] 2.3 In `_layout_inner_gate`, expand the `passage` set to at least `{(axis_x-1, z), (axis_x, z), (axis_x+1, z)}` for each z in the inner-gate band (currently only the axis cell is open).
- [x] 2.4 Regenerate `src/main/resources/data/myvillage/structure/chinese_courtyard_001..006.nbt`. Record new SHA-256 in `reports/compound_library_validation.json`.
- [x] 2.5 Run `validate_compound` on all 6 regenerated compounds; confirm no `voxel_*` errors fire. Run the byte-stability guard: `cultivation_sect_*` and `medieval_*` SHA-256 unchanged.

## 3. `mod-decor-block-family` protocol spec + first Java registration scaffolding

- [x] 3.1 In `src/main/java/com/example/myvillage/block/ModBlocks.java`, register `myvillage:rockery_block` via `DeferredRegister.Blocks` with properties `variant` (String / Enum), `facing` (N/S/E/W), `moss_level` (none/light/heavy). Wire into `MyVillageMod` constructor + mod event bus.
- [x] 3.2 Add `src/main/java/com/example/myvillage/block/RockeryBlock.java` (a `Block` subclass or `BlockBehaviour` config) overriding `getShape` / `getCollisionShape` to return a per-variant `VoxelShape` from a class-specific lookup table (populated by task 4.4).
- [x] 3.3 Create `assets/myvillage/blockstates/rockery_block.json` (placeholder; populated by task 4.5) and `assets/myvillage/textures/block/rockery_block/` directory.
- [x] 3.4 Update `tools/validate_mod_block_fallback.py` (or equivalent) to accept `myvillage:rockery_block` under both `vanilla` and `full` profiles per the `myvillage:` self-namespace exemption.

## 4. 假山 model catalog generation (`garden-rockery` spec)

- [x] 4.1 In `tools/buildgen/rockery_models.py` (new, offline), implement `derive_variant_voxels(variant_id, role, seed) -> 16x16x16 solid/air array` using a heightfield + value-noise paradigm (reuse `sect_mountain._hash2` / `_noise`).
- [x] 4.2 Implement `voxels_to_model_json(voxels, variant_id) -> dict` converting solid voxels to a model JSON (cube elements with merged regions; stone / andesite / moss-overlay textures). Keep element count ≤ 32 via greedy merge.
- [x] 4.3 Implement `voxels_to_voxelshape_java(voxels, variant_id) -> str` emitting the Java `VoxelShape` table entry (merged AABBs, ≤ 32).
- [x] 4.4 Run the generator for the variant catalog: ~40 variants across 5 roles (peak 8-10, slope 8-10, base 6-8, corner 4-6, standalone 4-6). Verify `base` / `slope` variants expose a flat standable top face; `peak` variants do not. Hand-tune 5 hero variants (主峰, 副峰, 孤赏石, 池畔石, 门道石) for visual anchors.
- [x] 4.5 Generate `assets/myvillage/blockstates/rockery_block.json` from the variant manifest (every `(variant, facing, moss_level)` tuple → model).
- [x] 4.6 Generate `assets/myvillage/models/block/rockery_block/<variant>.json` per variant.
- [x] 4.7 Generate `assets/myvillage/textures/block/rockery_block/<variant>_<moss>.png` per variant × moss (3 base textures + 3 moss overlays = 6 PNGs per variant; or shared base textures across variants if visually acceptable).
- [x] 4.8 Update `RockeryBlock.java`'s VoxelShape table with the generated entries from 4.3.

## 5. 假山 placement + 水池 freeform + 亭 (`garden-rockery` runtime side)

- [x] 5.1 In `tools/buildgen/rockery.py` (new, runtime), implement `derive_rockery(seed, bbox, params) -> Dict[Cell2, Tuple[variant, moss_level]]` using a heightfield over the bbox. Role assignment by height decile (peak / slope / base / corner / standalone). Moss by height (low→none, mid→light, peak→heavy).
- [x] 5.2 Add a `garden_rockery` parcel-node renderer in `compound.py` that calls `derive_rockery` and writes `myvillage:rockery_block[variant=..., facing=..., moss_level=...]` at each cell's standable y.
- [x] 5.3 Implement `_freeform_pond(compound, bbox, seed) -> Set[Cell2]` using 2D value-noise binalization over the bbox; fill isolated 1-2 cell pockets (water pocket → land, land pocket → water). Write `minecraft:water` at y=-1 inside the shoreline.
- [x] 5.4 Add a `garden_pond` parcel-node renderer calling `_freeform_pond`. Add 山脚入水 composition: when `garden_rockery` meets `garden_pond`, place `base`-role variants at boundary cells on top of water at y=0+.
- [x] 5.5 Add a `garden_pavilion` parcel-node renderer: 4 standoff columns at `COLUMN` slot, `chinese_round_ridge` roof, no walls. Support standalone-on-ground and on-rockery-peak placement.
- [x] 5.6 Add 汀步 (stepping stones) across `garden_pond`: place `myvillage:rockery_block[variant=standalone]` cells at standable y connecting two shore points.

## 6. `chinese_mansion` 3-进 layout (`chinese-mansion-compound` spec)

- [x] 6.1 In `tools/buildgen/compound.py`, generalize `_compute_yard_bands(layout_type, lot_d)` to `_compute_yard_bands(jin_count, layout_type, lot_d)` returning the ordered z-bands for `jin_count ∈ {1, 3, 4}` (1 = current behavior; 3 = shipped; 4 = sketch). The band sequence for `jin_count=3`: 前院 → 仪门 → 主院 → 二门 → 后院 → 花园.
- [x] 6.2 Add `_layout_front_yard`, `_layout_main_yard_mansion`, `_layout_back_yard`, `_layout_garden` functions (parallel to `_layout_outer_yard` / `_layout_main_yard`) for the 江南 parcel vocabulary.
- [x] 6.3 Implement `open_hall` archetype: front facade resolves through `FACADE_OPEN` slot (columns + open eave, no full-height front wall). Add `FACADE_OPEN` to `chinese_mansion.json` style profile.
- [x] 6.4 Implement `tower_house` archetype: `stories=2`, reuses `multi-story-massing` (floor slab + stairwell + per-story facade band). Place off-axis in 后院. `tower_count ∈ {1, 2}` per variant.
- [x] 6.5 Add `select_mansion_variant(seed)` deterministic template table (6 rows). Variant axes: `jin_count=3`, `gate_form`, `garden_scale`, `tower_count`, `roof_grade`, `open_hall_bays`.
- [x] 6.6 In `tools/buildgen/groups.py`, add `chinese_mansion` group binding the new style profile, archetype roster (`main_hall` / `open_hall` / `side_wing` / `flower_hall` / `front_row` / `tower_house` / `garden_pavilion`), layout strategy, scale parameters.
- [x] 6.7 Create `tools/buildgen/styles/chinese_mansion.json` with `FACADE_OPEN`, `GARDEN_PATH`, `ROCKERY_STONE`, `GARDEN_PAVEMENT`, `POND_STONE` slots (vanilla-clean fallbacks; the `myvillage:` decor ids are exempt).

## 7. 照壁 off-axis + 倒座 side alley + 仪门 multi-cell for `chinese_mansion` (shared with task 2)

- [x] 7.1 Ensure the 照壁 off-axis placement (task 2.1) works for both `chinese_courtyard` and `chinese_mansion`. Add `meta.form` to distinguish 北京 (jingbi) vs 江南 (zhaobi) where the form differs.
- [x] 7.2 Ensure the 倒座 side alley (task 2.2) applies to `chinese_mansion`'s 前院 倒座.
- [x] 7.3 Ensure the inner gates (仪门, 二门) in `chinese_mansion` open ≥ 3 cells (task 2.3 generalization).

## 8. Library generation + reports

- [x] 8.1 Generate `src/main/resources/data/myvillage/structure/chinese_mansion_001..006.nbt` (3-进 江南大宅, 6 visibly distinct variants).
- [x] 8.2 Regenerate `reports/compound_library_report.json` and `reports/compound_library_validation.json` with `chinese_mansion` entries (path_cells, ground_cells, endpoint_count, stair_cells, voxel_reachability stats) and updated `chinese_courtyard` entries.
- [x] 8.3 Add the `chinese_mansion` silhouette-score acceptance rule: 6 NBTs differ on silhouette_score ≥ 15.

## 9. Specs and docs

- [x] 9.1 New specs (in this change's `specs/`): `chinese-mansion-compound`, `mod-decor-block-family`, `garden-rockery`, `courtyard-voxel-walkability`, `huipai-tianjing-mansion`. (Already drafted.)
- [x] 9.2 Delta `openspec/specs/courtyard-compound/spec.md`: update the "Chinese one-courtyard axial layout" requirement to note 照壁侧立 / 倒座 side alley / 垂花门 multi-cell passage (inherited from the voxel-walkability fix).
- [x] 9.3 Delta `openspec/specs/multi-story-massing/spec.md`: note that `tower_house` (绣楼 / 藏书楼) is a new archetype consuming `stories=2`.
- [x] 9.4 Delta `openspec/specs/cultivation-form-vocabulary/spec.md`: note `chinese_round_ridge` (卷棚) is now also used for `garden_pavilion`.
- [x] 9.5 Delta `openspec/specs/validation/spec.md`: add `chinese_mansion` library entry + voxel-walkability error codes.
- [x] 9.6 Delta `openspec/specs/style-profile/spec.md`: note `chinese_mansion` profile's new slots (`FACADE_OPEN`, `GARDEN_PATH`, `ROCKERY_STONE`, `GARDEN_PAVEMENT`, `POND_STONE`).
- [x] 9.7 Update `docs/ai-kb/10_civic_family.md`: add a 江南大宅 section + a "mod decor blocks" subsection (pointing to `mod-decor-block-family`).
- [x] 9.8 Update `docs/ai-kb/14_deferred_roadmap.md`: §E.2 marked ✅ partially realized (3-进 shipped; 4-进 still deferred); §E.3 added for 徽派天井大屋 design retention; §E note that `courtyard-voxel-walkability` covers the ground+path per-jin caveat.
- [x] 9.9 Update `AGENTS.md`: paragraph on the `chinese_mansion` family and the mod-decor-block protocol; note voxel-walkability validator replaces the 2D graph check.
- [x] 9.10 Update `README.md`: add `/myvillage place chinese_mansion_001..006` and `/function myvillage:gallery/chinese_mansion` to the command list.

## 10. Version bump + acceptance

- [x] 10.1 Bump the mod version per `openspec/config.yaml` `rules.tasks` — large-feature bump `0.15.0 → 0.16.0` (new family + new capability class + breaking NBT regeneration + new mod block), updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together. Note the `chinese_courtyard_*` regeneration and the new `chinese_mansion_*` library in `CHANGELOG.md`.
- [x] 10.2 Run the validation checklist from `docs/ai-kb/09_validation_checklist.md`: generate, validate (incl. voxel-walkability on all compound families), preview (regenerate `out/preview/chinese_courtyard_*` + new `out/preview/chinese_mansion_*` + aggregate `out/preview/index.html`), build the jar.
- [ ] 10.3 Staged manual acceptance: place ≥2 `chinese_mansion_*` NBTs in-game and confirm (a) the entry sequence reads 江南 (照壁侧立 + 敞厅 + 楼阁 + 山水花园), (b) the player walks end-to-end from gate to 正房 to 楼阁 second story to 假山 mid-mountain to 水池 shore — no 影壁封轴, no 3-block cliffs, no dead ends, (c) 假山 variants render with sub-block detail (皱褶 / 孔洞), (d) 水池 has an irregular shoreline, (e) the 6 NBTs visibly differ on plan / roofline / 花园 scale. Capture screenshots; serve the preview aggregate over HTTP per `AGENTS.md` and report the host URL.
- [ ] 10.4 Staged manual acceptance: place ≥1 regenerated `chinese_courtyard_*` NBT in-game and confirm the 影壁封轴 defect is gone (player walks from gate past the side-standing 照壁 into the main yard without detour-through-column). Capture screenshots.
- [ ] 10.5 Staged manual acceptance: place a `chinese_mansion_*` with `garden_scale=large` and confirm the 假山 is partially climbable (player can stand on `slope` / `base` variant tops via autostep), the 汀步 crosses the pond, and the 亭 on the rockery peak is reachable. Capture screenshots.
