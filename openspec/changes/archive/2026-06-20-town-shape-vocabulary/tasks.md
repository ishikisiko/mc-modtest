## 1. Shared hash primitive (design D3)

- [x] 1.1 Create `tools/buildgen/town_hash.py` exposing `hash64(seed, tag) -> int`, `range64(seed, tag, lo, hi) -> int`, `pick(seed, tag, options) -> T` using a splitmix64-style finalizer over a tagged hash of the seed bytes (integer-only, no float).
- [x] 1.2 Add unit tests asserting `hash64` is bit-identical for the same `(seed, tag)` across runs and differs across tags.
- [x] 1.3 Create the Java mirror `src/main/java/com/example/myvillage/town/TownHash.java` with identical primitives; port the unit tests to JUnit.
- [x] 1.4 Add a parity cross-check: a Python test that emits 50 `(seed, tag)` cases to a JSON fixture, and a JUnit test that reads the fixture and asserts bit-identical output from the Java mirror.

## 2. Perimeter shape vocabulary — Lever A (design D1, D2; spec `town-plan` ADDED "Perimeter shape is selected from a vocabulary" and "Composable modifiers extend a base shape")

- [x] 2.1 Refactor `tools/buildgen/town.py` perimeter code: split `_bitten_cells(site, shape_id)` into a base-family predicate dispatched on `family_id ∈ {square, circle, oval, D-shape, octagon, trapezoid}`, each a pure integer-arithmetic function of `(site, family_id)`.
- [x] 2.2 Implement the circle predicate as `(x−cx)² + (z−cz)² ≤ r²` with `r = min(w,d)//2 − margin`; gate-facing segment is a chord clipped to a straight run wide enough for the gate footprint.
- [x] 2.3 Implement the oval predicate as the integer cross-multiply `rz²·(x−cx)² + rx²·(z−cz)² ≤ (rx·rz)²` (no float division); parameterize `(rx, rz)` via `town_hash`.
- [x] 2.4 Implement D-shape (semicircle north + rectangle south, gate on the south straight edge), large octagon (chamfer leg `K ≥ 40` via `town_hash`, replacing the current `K=8`), and trapezoid (two opposite edges slanted by integer offsets).
- [x] 2.5 Implement modifiers `barbican` (small attached enclosure at the gate, composed by set algebra) and `bastion` (rectangular edge bulges), each `(site, base_bitten, modifier_id) -> modified_bitten`.
- [x] 2.6 Replace `select_perimeter_shape(seed) = variants[seed % 3]` with `town_hash.pick` for base family and modifier set independently; keep `square` as the family selected when the hash lands in a reserved range (preserves a recognizable square variant in the vocabulary).
- [x] 2.7 Update `_boundary(site, shape_id)` to consume the new `(family_id, modifiers)` tuple; ensure it still returns a single closed loop for every vocabulary entry (add a single-loop assertion to `validate_town_plan`).
- [x] 2.8 Regenerate `out/preview/town_plan_*` for the probe-seed set and eyeball each family's wall line; confirm the gate still seats on a straight segment in every family.

## 3. Seed-driven internal grid (design D4; spec `town-plan` ADDED "The internal macro grid is seed-derived")

- [x] 3.1 In `tools/buildgen/town.py::_layout`, replace the hardcoded `cx = w//2` with `cx = w//2 + town_hash.range64(seed, "cx", -4, 4)`; export the chosen `cx` through `parity_constants()`.
- [x] 3.2 Replace the fixed lane z-bands `(16,18)/(60,62)/(108,110)` with `town_hash.range64(seed, "lane_s", -2, 2)` etc., keeping the 3-wide lane structure and the south-gate clear zone.
- [x] 3.3 Jitter per-district widths by `town_hash.range64(seed, "<dist_id>", -3, 3)` within bounds that keep districts rectangular and the spine intersecting the gate; export each width through `parity_constants()`.
- [x] 3.4 Add validator assertions: spine centerline intersects the south gate's straight segment; spine extends unbroken to the civic core; no district bound severs the spine (spec scenario "Grid bounds preserve the spine-to-gate invariant").
- [x] 3.5 Regenerate probe-seed previews; confirm different seeds now produce visibly different grids while every district stays rectangular.

## 4. Validator distinctness gate (design D6; spec `town-plan` ADDED "Town plans meet a distinctness gate across probe seeds")

- [x] 4.1 Define the fixed probe-seed set in `tools/validate_runtime_town_plan.py` (existing 3 seeds `20260618`/`20260719`/`20260820` plus 2 additional, e.g. `20260921`/`20261022`).
- [x] 4.2 Implement perimeter Jaccard distance between every probe-seed pair, and a silhouette descriptor (bounding-box aspect + corner/curve cell fraction).
- [x] 4.3 Implement the distinctness gate that fails when any pair falls below either floor; on failure, name the colliding pair and the missed metric.
- [x] 4.4 Calibrate the floors against the 0.12.0 baseline (must fail) and the post-change probe set (must pass); record chosen floors in `reports/town_distinctness_calibration.json`.
- [x] 4.5 Confirm the gate fails on the current `main` (pre-change) and passes after tasks 2 + 3 land.

## 5. Clip-to-shape outer districts — Lever B complete (design D5; spec `town-districts` ADDED "Outer district cells may be clipped to the perimeter shape")

- [x] 5.1 In `tools/buildgen/town.py::generate_town_plan`, compute the perimeter interior cell set once per plan and expose it to the district-emission path.
- [x] 5.2 For every outer district (`market`, `residential`, `fringe`) whose AABB reaches the perimeter curve and whose vocabulary entry is not `square`, set `cells_override = tuple(sorted(AABB ∩ perimeter_interior))`; leave `civic_core.cells_override` empty (stays rectangular).
- [x] 5.3 Confirm `_subdivide_district` already operates on `district.cells` (cell-set-aware from 0.12.0); widen its retry/shift path so boundary fragments thinner than the minimum footprint are dropped or merged rather than emitted.
- [x] 5.4 Add validator assertions: every emitted parcel lies within its district's clipped `cells`; no parcel crosses the perimeter boundary; civic-precinct cells still derive from `civic_core.bounds` and stay inside `civic_core.cells`.
- [x] 5.5 Regenerate probe-seed previews for circle/oval/D-shape/octagon; confirm parcels follow the curve and `civic_core` stays rectangular.

## 6. Parity contract expansion (design D8)

- [x] 6.1 Expand `tools/buildgen/town.py::parity_constants()` to export: vocabulary family id, modifier set, every grid-parameter value (`cx`, lane z-bands, district widths), per-family perimeter cell count, per-family interior cell count, and per-district clipped cell count.
- [x] 6.2 Update `tools/validate_runtime_town_plan.py::validate_parity_constants` to compare every new constant against the Java-hardcoded values across all probe seeds and vocabulary entries.
- [x] 6.3 Add an explicit Python⇄Java cross-check sweep for circle/oval at multiple `(rx, rz)` radii (design D2 risk mitigation).

## 7. Java runtime mirror

- [x] 7.1 Port the new base-family predicates and modifiers to `TownGenerator.java` (mirror of `town.py` §2), using integer-only arithmetic matching the Python predicates bit-for-bit.
- [x] 7.2 Port the seed-driven grid parameters to `TownGenerator.plan`, routed through `TownHash` (task 1.3).
- [x] 7.3 Port the clip-to-shape district cell-set computation; ensure `placePerimeter` and parcel realization honor non-rect outer district cells.
- [x] 7.4 Update the hardcoded parity values in `TownGenerator.java` to match the expanded `parity_constants()` (task 6.1).
- [x] 7.5 Run `validate_runtime_town_plan.py` end-to-end; confirm green across all probe seeds and vocabulary entries.

## 8. Docs, KB, README, CHANGELOG

- [x] 8.1 Update `docs/ai-kb/11_town_shape_irregularity.md` status note to point at this change as the realized follow-up.
- [x] 8.2 Create `docs/ai-kb/12_town_shape_vocabulary.md` covering the vocabulary + grid-jitter + clip-to-shape design with ASCII sketches; list it in `docs/ai-kb/INDEX.md` with see-also to `town-plan`, `town-districts`, and `11_town_shape_irregularity.md`.
- [x] 8.3 Update `README.md` `/myvillage town [seed]` section: note that seed now selects both a perimeter silhouette (incl. 天圆 circle) and an internal grid, with example seeds per family.
- [x] 8.4 Add a `CHANGELOG.md` entry under the next version (large increment per `openspec/config.yaml` `rules.tasks`: planner + Java + parity + validator + docs move together).

## 9. Acceptance preview & archive prep (per `docs/ai-kb/09_validation_checklist.md`)

- [x] 9.1 Run the full acceptance checklist: Python parity, Java mirror, validator distinctness gate, probe-seed preview regen, mod jar build.
- [x] 9.2 Confirm `out/preview/index.html` aggregates the new probe-seed towns; serve `out/preview/` over HTTP and report the host URL for visual review.
- [x] 9.3 Confirm the README command list, this change's specs, and `AGENTS.md` town-composition note all reflect the new vocabulary + grid behavior.
- [x] 9.4 Keep the preview server running until the change is accepted or archived.
