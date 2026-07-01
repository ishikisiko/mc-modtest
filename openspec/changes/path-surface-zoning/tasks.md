## 1. Material-zoning wiring (Stage 1 — no shape change, no new nodes)

- [x] 1.1 Add the six new style slots to `tools/buildgen/styles/chinese_mansion.json`: `PATH_FORMAL`, `GROUND_YARD_HEART`, `PATH_GALLERY`, `PATH_ALLEY`, `PATH_TOUR`, `PATH_WATERSIDE` — each vanilla-clean, with `PATH_TOUR` resolving to `minecraft:mossy_stone_bricks` (no cobblestone).
- [x] 1.2 Add the four ground-layer slots (`PATH_FORMAL`, `GROUND_YARD_HEART`, `PATH_GALLERY`, `PATH_ALLEY`) to `tools/buildgen/styles/chinese_courtyard.json`; the tour/waterside slots are mansion-only.
- [x] 1.3 Extend `_place_yard_ground` (`tools/buildgen/compound.py`) zone derivation: split the current `under_eave` classification so the eave-drip ring resolves to `GROUND_YARD_HEART`, `covered_gallery` cells to `PATH_GALLERY`, and 倒座 `side_alay` cells to `PATH_ALLEY`; remaining `under_eave` keeps `GROUND_YARD_UNDER_EAVE`.
- [x] 1.4 Change `_route_complete_path` to resolve the formal backbone overlay through `PATH_FORMAL` instead of `GROUND_PATH`; keep the single-source shortest-path tree unchanged.
- [x] 1.5 Update `tools/check_style_policy.py` to recognize the six new slots; run the linter and confirm it is green on both style JSONs.
- [x] 1.6 Regenerate `chinese_mansion_*`, `chinese_courtyard_*`, and embedded `small_courtyard` (`cultivation_town_*`) NBTs under `--profile vanilla` and `--profile full`; confirm the yards now read as grass + grey-brick ring + wood-stone gallery + brick alley + bluestone formal axis (still straight everywhere).

## 2. Tour route winds (Stage 2 — waypoint routing + material boundary)

- [x] 2.1 Implement `_route_tour_path` in `tools/buildgen/compound.py`: a waypoint polyline (rockery south face → nearest pond shore → 亭), each segment a single-source shortest-path tree, with an obstacle set forcing any segment that would cut through the rockery/pond to route around it.
- [x] 2.2 Implement the `moon_gate_passage` parcel renderer: a voxel-walkable 穿墙通道 through the garden screen wall, with the `moon_gate` motif applied to the surrounding wall cells.
- [x] 2.3 Place the garden screen wall + 月洞门 passage between 后院 and 花园 in `generate_mansion`; decide the screen-wall placement (resolve open question: new screen wall vs existing perimeter — default new screen wall).
- [x] 2.4 Wire the tour polyline to resolve through `PATH_TOUR` (mossy stone bricks); confirm the first tour waypoint is on the 花园 side of the passage so the formal/tour cell intersection is empty.
- [x] 2.5 Add a unit test asserting the tour route visibly turns at each waypoint and that no tour cell coincides with a rockery or pond cell.
- [x] 2.6 Regenerate `chinese_mansion_*` NBTs; confirm the garden path visibly winds (mossy-stone-brick) and is distinct from the straight formal axis (bluestone).

## 3. Path termini (Stage 3 — 月洞门 / 水边廊 / 仆役房)

- [x] 3.1 Add the shoreside `covered_gallery` placement variant (水边廊) in `_layout_garden`: route a `covered_gallery` along the pond shore, its floor resolving through `PATH_GALLERY`.
- [x] 3.2 Add the `service_house` archetype to `tools/buildgen/archetypes.py` (small, plain, no decoration tier, reuses sub-building machinery); place it along the 倒座 `side_alley` in `generate_mansion` when the lot has room.
- [x] 3.3 Wire `service_house.door_info["front"]` into the path endpoint collector (`_collect_path_endpoints`) so the formal/service BFS reaches it through the alley.
- [x] 3.4 Add a unit test asserting the service house is a path endpoint reachable through the alley, and that the 水边廊 lines the pond shore.
- [x] 3.5 Regenerate `chinese_mansion_*` NBTs; confirm all three routes have real endpoints (月洞门, 水边廊, 仆役房).

## 4. Waterside crossing + full coverage + validation (Stage 4)

- [x] 4.1 Implement the `PATH_WATERSIDE` writer: `stone_brick_stairs` descending to the waterline + a slab bridge (`oak_slab`/`spruce_slab` at the water surface y) spanning the pond's narrowest crossing to the 亭/island. Do NOT restore the deleted stepping-stone `rockery_block` cells.
- [x] 4.2 Extend `validate_mansion` / `validate_compound` with the `surface_zone_material:<zone>:<cell>` assertion (classify each ground/path cell into one of the six zones and check the resolved block against the zone's slot).
- [x] 4.3 Extend `validate_mansion` with the `tour_segment_disconnected:<from>-><to>` check (each tour segment a connected single-source tree); make it a no-op for compounds without a garden.
- [x] 4.4 Extend `validate_mansion` with the `waterside_bridge_incomplete:<first|last>` check (bridge spans both shores / 亭-island).
- [x] 4.5 Extend the byte-stability guard in `tools/buildgen/tests/test_chinese_courtyard_regression.py` to assert the surface-zone families (`chinese_mansion_*`, `chinese_courtyard_*`, `cultivation_town_*`) change, while `cultivation_sect_*` and `medieval_*` stay byte-identical.
- [x] 4.6 Run `validate_compound_library` green under both `--profile vanilla` and `--profile full`; regenerate all reports.

## 5. Acceptance, docs, and version bump

> **Deferred until Stage 6 lands.** Stage 5 was first run against the
> Arc 1-4-only NBTs (surface materials). The user's in-game review elevated
> Arc 5 (连廊建筑化) and Arc 6 (房屋布局修复) into scope, so the mansion NBTs
> regenerate again in Stage 6 — the acceptance/docs/version bump below must be
> re-run against those final NBTs. The checkbox state below reflects the
> Arc 1-4 pass and is reset to pending.

- [x] 5.1 Land the regenerated structures into `src/main/resources/data/myvillage/structure/` and pack into the mod jar.
- [x] 5.2 Update `AGENTS.md`: replace the path-block paragraph with the six-zone surface model; note the tour waypoint router and the 月洞门 material boundary; note the 3D 连廊 renderer and the 后院/花园 split (Arc 5/6).
- [x] 5.3 Add a `docs/ai-kb/` note "Path surface zoning" (list it in `docs/ai-kb/INDEX.md`, with see-also links to `courtyard-ground-layer`, `courtyard-path-network`, and the new `path-surface-zoning` spec). Cover the 连廊建筑化 + layout-fix arcs.
- [x] 5.4 Update `README.md` command/usage instructions if any `/myvillage` surface changes are user-visible (the `/place` ids are unchanged; add a note that paths now read as three routes with six surfaces, the 水边廊/抄手游廊 are real 3D galleries, and the 绣楼 sits in its own 后院).
- [x] 5.5 Bump the mod version (large feature) and update the four files together per `openspec/config.yaml` `rules.tasks`: `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, `README.md` jar-name examples, `CHANGELOG.md`.
- [x] 5.6 Staged manual acceptance: generate the aggregate preview `out/preview/index.html`, serve `out/preview/` over HTTP, report the host address as the review URL, and place ≥2 NBTs per family in-game for the user's "feels right" review (重点复核: 水边廊有柱有顶有栏, 主院抄手游廊, 绣楼在后院不在花园, 主院院心是草).

## 6. 连廊建筑化 + 房屋布局修复 (Arc 5 + Arc 6 — elevated after in-game review)

- [x] 6.1 Add the `BALUSTRADE` slot to `tools/buildgen/styles/chinese_mansion.json` (`minecraft:dark_oak_fence` / `minecraft:spruce_fence`, vanilla-clean). Already in `OPTIONAL_MATERIAL_SLOTS`; confirm `check_style_policy` stays green.
- [x] 6.2 Write `_place_covered_gallery_3d(compound, style, cells, base_y, water_side, roof_form="single_slope")` in `tools/buildgen/compound.py`: per cell, a `PATH_GALLERY` floor + a `COLUMN` post every other cell (2 tall) + a `BALUSTRADE` fence row 密排 on the open side + a single-slope `ROOF_DARK` roof (low toward `water_side`, high toward yard). Reuse the column pattern from `_place_covered_galleries` (`:1783`) and the 密排 balustrade pattern from `ops.balustrade` (`:1786`).
- [x] 6.3 Upgrade the 水边廊 (`_layout_garden` ≈ `:3607`): replace the one-floor-block loop with a `_place_covered_gallery_3d()` call, open/balustrade side facing the water. Keep the parcel `type="covered_gallery"` and the 4-adjacent-to-water invariant (locked by `test_path_termini`).
- [x] 6.4 Add the mansion 主院 抄手游廊 (east + west) tying the 仪门 flanks to the 敞厅 flanks (reuse the 抄手 return geometry from `_place_covered_galleries` `:1764-1778`), balustrade facing the yard, at the 台基 height. Skip a side whose 主院 strip is < 3 wide clear of the 厢房 (not mandatory). Mansion-only.
- [x] 6.5 Split 后院/花园: in `generate_mansion` call `_mansion_yard_depths(lot_d, garden_scale)` and cut `back_yard_band = [ermen_z+1, ermen_z+back_d]` / `garden_band = [ermen_z+back_d+1, lot_d-2]` (replacing the identical-band bug at `:3723-3724`). The 月洞门 screen wall moves to `garden_band[0]`.
- [x] 6.6 Constrain the 绣楼 to the 后院: in `_plan_mansion_enclosure` require `tz0 + td - 1 < garden_band[0]`; relax the hard-west single-tower pin (`t1_x = axis-1-tw`) so the tower centers/mirrors when the 后院 has room, while staying off-axis as a valid 江南 form.
- [x] 6.7 Shrink the 主院 台基: in `_realize_mansion_enclosure` cover only the 敞厅 + 厢房 + 抄手游廊 footprints (±1-cell skirt) with PLATFORM_STONE instead of full-width-filling the 主院; the heart falls through to `_place_yard_ground` → `GROUND_YARD_OPEN` grass.
- [x] 6.8 Add two layout guards to `validate_mansion`: `back_yard_garden_overlap` (`back_yard_band[1] < garden_band[0]`) and `tower_overlaps_garden` (no `tower_house` footprint cell coincides with a 花园 feature cell).
- [x] 6.9 Update `test_path_termini.py`: assert the 水边廊 has columns + roof + balustrade blocks (not just a floor). Extend the byte-stability guard to keep `cultivation_sect_*` / `medieval_*` unchanged while `chinese_mansion_*` regenerates.
- [x] 6.10 Regenerate `chinese_mansion_001..006.nbt` (`--profile full`, then `--profile vanilla` proof); run `validate_mansion` + byte-stability + the two new layout guards green. Confirm the 绣楼 sits in the 后院 (not the 花园), the 主院 heart is grass, and the 水边廊/抄手游廊 are 3D.
