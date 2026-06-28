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

- [ ] 4.1 Implement the `PATH_WATERSIDE` writer: `stone_brick_stairs` descending to the waterline + a slab bridge (`oak_slab`/`spruce_slab` at the water surface y) spanning the pond's narrowest crossing to the 亭/island. Do NOT restore the deleted stepping-stone `rockery_block` cells.
- [ ] 4.2 Extend `validate_mansion` / `validate_compound` with the `surface_zone_material:<zone>:<cell>` assertion (classify each ground/path cell into one of the six zones and check the resolved block against the zone's slot).
- [ ] 4.3 Extend `validate_mansion` with the `tour_segment_disconnected:<from>-><to>` check (each tour segment a connected single-source tree); make it a no-op for compounds without a garden.
- [ ] 4.4 Extend `validate_mansion` with the `waterside_bridge_incomplete:<first|last>` check (bridge spans both shores / 亭-island).
- [ ] 4.5 Extend the byte-stability guard in `tools/buildgen/tests/test_chinese_courtyard_regression.py` to assert the surface-zone families (`chinese_mansion_*`, `chinese_courtyard_*`, `cultivation_town_*`) change, while `cultivation_sect_*` and `medieval_*` stay byte-identical.
- [ ] 4.6 Run `validate_compound_library` green under both `--profile vanilla` and `--profile full`; regenerate all reports.

## 5. Acceptance, docs, and version bump

- [ ] 5.1 Land the regenerated structures into `src/main/resources/data/myvillage/structure/` and pack into the mod jar.
- [ ] 5.2 Update `AGENTS.md`: replace the path-block paragraph with the six-zone surface model; note the tour waypoint router and the 月洞门 material boundary.
- [ ] 5.3 Add a `docs/ai-kb/` note "Path surface zoning" (list it in `docs/ai-kb/INDEX.md`, with see-also links to `courtyard-ground-layer`, `courtyard-path-network`, and the new `path-surface-zoning` spec).
- [ ] 5.4 Update `README.md` command/usage instructions if any `/myvillage` surface changes are user-visible (the `/place` ids are unchanged; add a note that paths now read as three routes with six surfaces).
- [ ] 5.5 Bump the mod version (large feature) and update the four files together per `openspec/config.yaml` `rules.tasks`: `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, `README.md` jar-name examples, `CHANGELOG.md`.
- [ ] 5.6 Staged manual acceptance: generate the aggregate preview `out/preview/index.html`, serve `out/preview/` over HTTP, report the host address as the review URL, and place ≥2 NBTs per family in-game for the user's "feels right" review.
