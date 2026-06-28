## 1. Orientation mechanism — door-wall relocation (`building-orientation-variants` spec)

- [ ] 1.1 In `tools/buildgen/archetypes.py`, extend `_door(graph, main, rng, wall="front", avoid=None)` to accept a `wall ∈ {front, back, west, east}` argument. For `wall != "front"`, pick the door position along the wall's long axis (z for west/east walls; x for back), honor the existing `avoid` reservation, and record `graph.meta["door"] = {"volume": main.id, "wall": wall, "pos": <pos>}`.
- [ ] 1.2 Thread a `facing ∈ {south, north, east, west}` (default `south`) through `generate_subbuilding` → `BuildContext` → the archetype builders. Default `south` MUST reproduce byte-identical output (regression guard).
- [ ] 1.3 In `tools/buildgen/passes.py` `facade_detail_pass`, read `graph.meta["door"]["wall"]` and carve the doorway on the selected wall (extend the `ops.doorway` call site; it already accepts a wall argument).
- [ ] 1.4 Compute `door_info["front"]` from the door wall's outward direction: south→`(door_x, y, z0-1)`; north→`(door_x, y, z1+1)`; west→`(x0-1, y, door_z)`; east→`(x1+1, y, door_z)`. Extend the existing front-cell arithmetic in `archetypes.py`/`passes.py`.
- [ ] 1.5 Unit-test: build a `side_wing` with `facing=west`; assert the door is on the west wall, `door_info["front"]` is at `(x0-1, y, door_z)`, and the volume/footprint/`roof_axis` are identical to a `south`-facing wing. Build a `front_row` with `facing=north`; assert the door is on the back wall.

## 2. Gate-house entrance (`mansion-gate-house` spec)

- [ ] 2.1 In `tools/buildgen/compound.py`, add a `_place_gate_house(compound, style, axis, gate_type)` that builds a `gate_house` sub-building (existing `build_gate_house`) and places it straddling the south perimeter (south wall on z=0 line, body projecting inward), centered on the axis. Record a `gate_house` `BuildingSlot` with `facing=inward`.
- [ ] 2.2 Modify `_add_chinese_perimeter` to accept the gate_house footprint and gap the south-perimeter wall to match it exactly, so the gate_house side walls seal the gap. Remove the carved-air `gate` set for the mansion path (keep it for the unchanged `chinese_courtyard` path).
- [ ] 2.3 Wire `_place_gate_house` into `generate_mansion` as the first enclosure step (replaces the implicit hole-in-wall gate). The player must walk through the gate_house to reach the 前院.
- [ ] 2.4 Unit-test: generate a mansion; assert a `gate_house` slot straddles z=0, the perimeter is sealed except through it, and `_voxel_walk_bfs` reaches the 前院 by passing through the gate_house.

## 3. Enclosure planner (`compound-enclosure-planning` spec)

- [ ] 3.1 In `tools/buildgen/compound.py`, add a `_plan_mansion_enclosure(variant, lot_w, lot_d, axis, seed) -> List[Placement]` returning the placement manifest: each `Placement = (archetype, facing, anchor_wall, offset_along_wall, importance)`, encoding the form rule (正房-south/倒座-north/西厢-east/东厢-west/gate_house-inward) with anchor walls per design D3.
- [ ] 3.2 Add `_derive_yards(compound, manifest) -> Dict[str, Set[Cell2]]` computing each 进 (前院/主院/后院/花园) as the enclosed negative space of its facing-buildings (flood-fill the interior cells not under any building footprint, partitioned by inner-gate boundaries).
- [ ] 3.3 Add `_realize_manifest(compound, style, manifest, contexts)` placing each building against its anchor wall with its form-rule facing via `_translate_context` (using the facing-aware `door_info` from task 1). Insert inner gates (仪门/二门) at the derived yard-adjacency boundaries.
- [ ] 3.4 Replace the body of `generate_mansion`'s layout calls (`_layout_front_yard`/`_layout_main_yard_mansion`/`_layout_back_yard`) with: `_plan_mansion_enclosure` → `_realize_manifest` → `_derive_yards`. Keep `_layout_garden` (garden realization unchanged). Keep `_place_yard_ground`, `_route_complete_path`, `_place_band_transition_stairs` as post-realization passes.
- [ ] 3.5 Unit-test: generate all 6 mansion variants; assert each 进 is a contiguous enclosed region with no building footprint cells; assert 仪门 borders 前院+主院 and 二门 borders 主院+后院 (by adjacency, not z-band).

## 4. Path-as-input routing (`compound-enclosure-planning` spec, D4)

- [ ] 4.1 After manifest realization, collect every `BuildingSlot.door_info["front"]` cell as a mandatory path endpoint (in addition to the existing `_collect_path_endpoints` landscape endpoints).
- [ ] 4.2 Route the gravel backbone in the derived yard space from the gate-house inner opening to every door endpoint. Reuse the existing single-source BFS `_route_complete_path` but seed it with the door endpoints and constrain it to the derived-yard cell set.
- [ ] 4.3 Wire `_voxel_walk_bfs` as the acceptance gate in `_realize_manifest`: if any door-cell is unreachable, raise `EnclosurePlanRejected` and the planner retries (in practice never triggers for a well-formed manifest). Document that the derived yard is contiguous by construction.

## 5. Validator rewrite (`validation` spec delta)

- [ ] 5.1 Rewrite `validate_mansion` to add the enclosure invariants: `gate_house_missing` (no gate_house slot straddling south), `enclosure_facing_violation:<slot>` (facing ≠ form rule), `door_off_path:<slot>` (door-cell not on path), and 进-sequence adjacency (仪门 borders 前院+主院; 二门 borders 主院+后院) via derived-yard adjacency, NOT z-band comparison.
- [ ] 5.2 Keep all grid-only checks in `validate_mansion` verbatim (perimeter floats, ground holes, voxel-walkability error codes, silhouette). Remove the z-band-coupled mansion checks (the old `oy_band < ig_band < my_band` style comparisons if any).
- [ ] 5.3 Add `facing_per_slot` map and `door_reachable_rate` stat to the mansion report.
- [ ] 5.4 Leave `validate_compound` (band-coupled, governs `chinese_courtyard`) **unchanged**. Confirm `validate_small_courtyard` is unchanged.

## 6. Byte-stability guard + regression

- [ ] 6.1 Extend the byte-stability guard in `tools/buildgen/tests/test_chinese_courtyard_regression.py` (or add a sibling test) to assert `chinese_courtyard_*`, `cultivation_sect_*`, `cultivation_town_*`, `medieval_*` NBT SHA-256 are unchanged by this change (they must not be regenerated).
- [ ] 6.2 Add an orientation regression test: for each mansion archetype, `facing=south` (default) produces byte-identical output to the pre-change baseline (proves the `wall` param is additive).
- [ ] 6.3 Run the full validation checklist from `docs/ai-kb/09_validation_checklist.md` (generate, validate incl. voxel-walkability on all families, preview, build jar).

## 7. Library regeneration + reports

- [ ] 7.1 Regenerate `src/main/resources/data/myvillage/structure/chinese_mansion_001..006.nbt` via `generate_compound_library.py --group chinese_mansion --count 6 --base-seed 20260618 --profile vanilla` (and `--profile full` for the shipped tree).
- [ ] 7.2 Regenerate `reports/compound_library_report.json` and `reports/compound_library_validation.json` mansion entries (new `facing_per_slot`, `door_reachable_rate=1.0`). Confirm `chinese_courtyard_*` entries unchanged.
- [ ] 7.3 Confirm the 6 mansions remain visibly distinct (silhouette spread ≥ 15) AND now each pass the enclosure invariants (gate_house, form-rule facings, every door on path).

## 8. Specs and docs

- [ ] 8.1 Specs in this change (`compound-enclosure-planning`, `building-orientation-variants`, `mansion-gate-house` new; `chinese-mansion-compound`, `validation` delta) — already drafted in this proposal.
- [ ] 8.2 Update `docs/ai-kb/10_civic_family.md`: rewrite the 江南大宅 section to the enclosure model; add a "compound planning skeleton" note (enclosure model + facing variants; band model retained for `chinese_courtyard` pending propagation).
- [ ] 8.3 Update `docs/ai-kb/14_deferred_roadmap.md`: add an entry "propagate enclosure + orientation skeleton to `chinese_courtyard` + `small_courtyard`" as the immediate next change.
- [ ] 8.4 Update `AGENTS.md`: replace the band-model mansion paragraph with the enclosure-model paragraph; note the facing-variant mechanism and that `chinese_courtyard`/`small_courtyard` still use the old planner pending propagation.
- [ ] 8.5 Update `README.md`: command list unchanged (same `/place chinese_mansion_*` ids); the in-game form change is noted in CHANGELOG, not README commands.

## 9. Version bump + acceptance

- [ ] 9.1 Bump the mod version per `openspec/config.yaml` `rules.tasks` — large-feature bump `0.16.2-fix1 → 0.17.0` (new planning skeleton + 3 new capabilities + mansion NBT regeneration), updating `gradle.properties`, `src/main/resources/META-INF/neoforge.mods.toml`, README jar-name examples, and `CHANGELOG.md` together. Note the mansion enclosure-model rewrite and the (unchanged) other families in `CHANGELOG.md`.
- [ ] 9.2 Build the mod jar; confirm `chinese_mansion_001..006` load and place in-game.
- [ ] 9.3 Staged manual acceptance: place ≥2 `chinese_mansion_*` NBTs in-game and confirm (a) the entrance reads as a real gate (门楼 + 门框 + through-passage, not a hole), (b) every building's door faces its yard (倒座 door toward 前院, 厢 doors inward, 正房 south), (c) the gravel path reaches every door, (d) the player walks end-to-end gate → 正房 → 楼阁 with no dead ends. Capture screenshots; serve the preview aggregate over HTTP per `AGENTS.md` and report the host URL.
- [ ] 9.4 Staged manual acceptance: place the remaining mansion variants and confirm the 6 read as visibly distinct (plan / roofline / 花园 scale) AND all share the corrected entrance + facing + path form. Capture screenshots.
