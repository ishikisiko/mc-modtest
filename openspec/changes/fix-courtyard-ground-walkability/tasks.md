## 1. Ground layer (`courtyard-ground-layer` spec)

- [ ] 1.1 In `tools/buildgen/compound.py`, add `Cell2 = Tuple[int, int]` and `GroundKind = Literal["open_sky", "under_eave", "interior"]` (use `typing.Literal`; python 3.9+).
- [ ] 1.2 Add `_derive_ground_kinds(compound: CompoundGraph) -> Dict[Cell2, str]` that returns a `ground_kind` per parcel cell. Default value is `open_sky`; then mark cells in `covered_gallery.cells` ∪ `moon_platform.cells` as `under_eave`; then for each `BuildingSlot.footprint`, mark the 1-cell Chebyshev ring around the footprint as `under_eave` (skip cells already marked interior, which we don't track at this layer — leave them as under_eave and let the building pass overwrite).
- [ ] 1.3 In `tools/buildgen/styles/chinese_courtyard.json`, add `GROUND_YARD_OPEN` (`["minecraft:grass_block"]`) and `GROUND_YARD_UNDER_EAVE` (`["minecraft:stone_bricks", "minecraft:polished_andesite"]`). Vanilla-profile resolves both to `minecraft:` ids; no external-mod dependency.
- [ ] 1.4 In `tools/buildgen/styles/cultivation_town.json`, add the same two slots with different values: `GROUND_YARD_OPEN` (`["minecraft:coarse_dirt", "minecraft:dirt"]`), `GROUND_YARD_UNDER_EAVE` (`["minecraft:stone_bricks"]`). Small-courtyard is more urban.
- [ ] 1.5 Add `_place_yard_ground(compound, style) -> None`: iterate every cell in the lot interior (1 ≤ x ≤ lot_w - 2, 1 ≤ z ≤ lot_d - 2) that is *not* in `building_cells()` ∪ `water_feature` ∪ `water_jar` ∪ `planting` ∪ `courtyard_tree`. Look up `ground_kind` from `_derive_ground_kinds`. Place the ground block at the cell's natural surface y (`y = -1` in outer yard; `y = plinth_h - 1` on main-yard plinth; `y = -1` in inner gate band). Tags `["DETAIL", "GROUND"]`, priority `DETAIL (70)`.
- [ ] 1.6 Wire `_place_yard_ground` into `generate_compound` after `_layout_main_yard` (so plinth is in place before yard fill) and before `_route_complete_path`. Wire into `generate_small_courtyard` after `_place_landscape` and before the routing call.

## 2. Path network (`courtyard-path-network` spec)

- [ ] 2.1 Add `_collect_path_endpoints(compound: CompoundGraph) -> List[Cell2]` returning the endpoint set:
  - perimeter_wall `gate_opening` → first cell minus 1 z-step (toward the yard interior).
  - every `BuildingSlot` whose `door_info is not None` → `door_info["front"]`.
  - every cell of every `water_feature` parcel node (well).
  - every cell of every `water_jar` parcel node (fish jar).
  - every `planting` parcel node → first cell adjacent to the planting boundary (sort cells, pick the one whose 4-neighbor count in `tianjing_open` is highest).
  - the front-most cell of `moon_platform` (closest to gate; min z if gate is south, max z if gate is north).
- [ ] 2.2 Add `_multi_source_bfs(endpoints: List[Cell2], blocked: Set[Cell2], lot_w: int, lot_d: int) -> Dict[Cell2, int]` running a 4-neighbor BFS from all endpoints simultaneously. Returns a `dict` mapping every reached cell to its distance from the nearest endpoint. Cells in `blocked` are excluded from the search (they remain unreached). Endpoints start at distance 0.
- [ ] 2.3 Add `_route_complete_path(compound, style) -> None`: collect endpoints; compute `blocked = building_footprint ∪ water_feature ∪ water_jar ∪ planting ∪ courtyard_tree ∪ inner_gate_solid_flanks ∪ perimeter_wall`; run multi-source BFS; assert every endpoint has finite distance (raise `ValueError("endpoint_unreachable: {cell}")` otherwise — the slow-fail is caught by the validator at §4); write the path block at every reached cell, at the cell's natural surface y (`y = -1` outer; `y = plinth_h - 1` main-yard). Path block = `style.primary("GROUND_PATH")`, tags `["DETAIL", "GROUND", "PROTECTED"]`, priority `DETAIL (70)`.
- [ ] 2.4 Add `_place_plinth_stairs(compound, style) -> None`: walk every cell where path crosses the plinth boundary (path cell at `y = plinth_h - 1` adjacent to a path cell at `y = -1` along the same x or z, where the outer cell's z or x is < or > the plinth cell's correspondingly). For each boundary cell, write `minecraft:stone_brick_stairs[facing=<normal from plinth to outer>, half=bottom]` at the boundary cell, replacing the path block. The facing is derived from the boundary normal: if the outer cell has smaller z than the plinth cell, facing = north (because the stairs "rise" from south to north); etc. Tags `["DETAIL", "STRUCTURE"]`, priority `STRUCTURE (20)` so the stair is visible above the path / ground.
- [ ] 2.5 Remove `_route_central_path` from `compound.py`. The only caller was `generate_compound`; replace the call with `_route_complete_path`.
- [ ] 2.6 Refactor `_route_circulation` to delegate to `_multi_source_bfs`. Keep its current callers (small-courtyard) byte-stable on the path footprint by using the same endpoint set as `_collect_path_endpoints` does (without the building-slot door_info, since the small-courtyard builds don't always populate it; fall back to `building_slot.footprint.center()` as the endpoint for each slot).
- [ ] 2.7 Wire `_route_complete_path` + `_place_plinth_stairs` into `generate_compound` after `_place_yard_ground`. Wire `_route_complete_path` (without `_place_plinth_stairs` — small-courtyard has no plinth) into `generate_small_courtyard` after `_place_yard_ground`.

## 3. Validation

- [ ] 3.1 Extend `validate_compound` with new error codes:
  - `endpoint_unreachable:<cell>` — endpoint not in the BFS-reached set.
  - `ground_layer_hole:<cell>` — a lot-interior cell that has no block at the natural surface y after `_place_yard_ground` (the player would fall through).
  - `ground_kind_mismatch:<cell>` — a cell whose `ground_kind` differs from the block actually written (e.g. an under-eave cell got grass_block). Cross-check by re-running `_derive_ground_kinds` on the grid.
  - `plinth_edge_missing_stair:<cell>` — a path cell at `y = plinth_h - 1` whose 4-neighbor at `y = -1` is also a path cell, but the boundary cell has no stair block.
  - `path_overlaps_building_door:<cell>` — a path cell whose 4-neighbor is a `door_info.front` cell. The path should stop one cell short of the door.
- [ ] 3.2 Extend `validate_small_courtyard` with the same `endpoint_unreachable` and `ground_layer_hole` checks (skip the plinth-stair check — small-courtyard has no plinth).
- [ ] 3.3 Add `validate_compound_library` acceptance rule: every shipped `chinese_courtyard_*.nbt` reports non-empty `ground_cells` and `endpoint_count` stats, and the new error codes don't fire.
- [ ] 3.4 Add regression guard: every `cultivation_sect_*` and `medieval_*` NBT SHA-256 stays byte-stable; every `cultivation_town_*` NBT SHA-256 changes only in cells inside small-courtyard parcels.

## 4. Resource regeneration

- [ ] 4.1 Regenerate `src/main/resources/data/myvillage/structure/chinese_courtyard_001..006.nbt` with the new ground + path layers. Record new NBT SHA-256 hashes in `reports/compound_library_validation.json` (replace the existing sha256 list).
- [ ] 4.2 Regenerate `src/main/resources/data/myvillage/structure/cultivation_town_001..006.nbt` with the upgraded small-courtyard ground + path. (The town block outer geometry and street network stay byte-stable; only the small-courtyard cells change.)
- [ ] 4.3 Regenerate `out/preview/chinese_courtyard_001..006/` (slices, isometric, viewer); regenerate `out/preview/cultivation_town_001..006/`. Confirm aggregate `out/preview/index.html` is up to date.
- [ ] 4.4 Regenerate `reports/compound_library_report.json` and `reports/compound_library_validation.json` with the new `ground_cells`, `endpoint_count`, and `stair_cells` stats.

## 5. Docs, version, and staged acceptance

- [ ] 5.1 Update `docs/ai-kb/10_civic_family.md` with a "Ground + path layer" section: the `open_sky` vs `under_eave` distinction, the multi-source BFS path network, the single-stair plinth transition. See-also to `courtyard-ground-layer`, `courtyard-path-network`.
- [ ] 5.2 Update `docs/ai-kb/14_deferred_roadmap.md` §E (Courtyard-form expansions) with a note that the multi-jin follow-up needs the same ground+path treatment re-derived per yard band.
- [ ] 5.3 Update `AGENTS.md`: one paragraph noting the courtyard ground+path is data-driven through the two new specs (`courtyard-ground-layer`, `courtyard-path-network`); adding a new ground layer block goes through `style.ground_yard_open` / `style.ground_yard_under_eave` slot additions, not hardcoded ids.
- [ ] 5.4 Bump the mod version per the `openspec/config.yaml` rule — fix bump `0.15.0 → 0.15.0-fix1` (the change is a fix for the prior version's broken walkability, not a new feature), updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together. Note the `chinese_courtyard_NNN.nbt` regeneration as a fix in `CHANGELOG.md`.
- [ ] 5.5 Run the validation checklist from `docs/ai-kb/09_validation_checklist.md` (Acceptance / preview command checklist): generate, validate, preview, build the jar.
- [ ] 5.6 Staged manual acceptance: place ≥2 of the rebuilt `chinese_courtyard_*` NBTs in-game and confirm (a) the yard floor is no longer hollow — player walks on solid ground in every non-building cell, (b) the path connects every door (正房 / 厢房 / 倒座) and every landscape feature (well / fish jar / planting) — no dead ends, (c) the plinth transition is step-able with a single stairs block, no jumping, (d) the outer-yard 草地 + main-yard 石砖 distinction reads as the outdoor-lawn vs covered-paver semantic, (e) the 6 NBTs visibly differ on plan and roofline (regression of `rebuild-chinese-courtyard`'s silhouette acceptance). Capture screenshots; serve the preview aggregate over HTTP per `AGENTS.md` and report the host URL.
- [ ] 5.7 Staged manual acceptance: place ≥1 of the rebuilt `cultivation_town_*` NBTs and confirm the embedded small-courtyards are walkable end-to-end (every courtyard door reachable, no falling through). Capture screenshots.