## 1. Layout regions (planner geometry)

- [x] 1.1 Extend `_Layout` in `tools/buildgen/town.py` with precinct fields: `precinct_gate_cells`, `spirit_way_band`, `side_hall_west_bounds`, `side_hall_east_bounds`, `colonnade_west_cells`, `colonnade_east_cells`, `precinct_wall_cells`, `precinct_side_gate_cells`.
- [x] 1.2 In `_layout`, derive the precinct gate as a paifang-style run on the spine at the core's gate-facing edge (`civic_core` z-min), reusing the `cx`/spine half-width.
- [x] 1.3 Derive the spirit-way band between the precinct gate and the plaza: flanking statue/lantern/stele cells along the spine, masked off the spine walking width and existing lantern cells.
- [x] 1.4 Derive side-hall bounds in the forecourt gaps between the two civic halls, and colonnade edge cell-sets along the lateral core edges (consuming the x44–46 / x114–116 slivers); degrade a sliver narrower than 2 to wall-only.
- [x] 1.5 Derive precinct-wall cells along the gate-facing and lateral core edges with a spine gate (coincident with the precinct gate) and one side gate per lateral edge; ensure wall cells fall on the core↔fringe boundary where shared.

## 2. Civic-core subdivision + axis metadata (planner)

- [x] 2.1 In the `civic_core` branch of `_subdivide_district` ([town.py:703-733](tools/buildgen/town.py#L703-L733)), emit side-hall parcels as low-tier `civic` parcels capped below the dominant-landmark tier and within the storey band, after the existing halls + landmarks.
- [x] 2.2 Emit the colonnade and precinct wall as reserved structures, masked against `street_cells`, `plaza`, `lantern_cells`, and every landmark/shrine/hall parcel so nothing overlaps.
- [x] 2.3 Extend `ritual_axis` metadata with `precinct_gate_cells` and `spirit_way_cells`; keep `terminus_parcel == town_shrine`, plaza, paifang, and lanterns unchanged.
- [x] 2.4 Confirm the spine stays continuous through the precinct gate (gate cells remain street/spine, not wall) so gate→precinct-gate→shrine connectivity holds.

## 3. Plan validation (Python)

- [x] 3.1 Add a precinct check to `validate_town_plan`: require wall on gate-facing + lateral edges, a spine-axis precinct gate, a non-empty spirit-way band, and plaza reachable through the gate; report a `precinct_*` invariant on failure.
- [x] 3.2 Add the baseline-relative emptiness assertion: unoccupied non-street `civic_core` cells strictly fewer than the same seed/site generated without precinct framing.
- [x] 3.3 Add the fringe-separation check: a wall run lies on each shared core↔fringe edge and no spirit-field cell falls inside the walled precinct.
- [x] 3.4 Confirm `estimate_block_budget` stays under `BLOCK_BUDGET_CEILING` with walls + colonnade + spirit-way props.
- [x] 3.5 Verify same-seed reproducibility of all precinct elements (wall, gates, approach, side halls, colonnade).

## 4. Realizer (Java)

- [x] 4.1 Mirror precinct geometry in `TownGenerator.java`, computing wall/gate/spirit-way/side-hall/colonnade from the same deterministic inputs (cx, spine, plaza, shrine, landmark bounds).
- [x] 4.2 Place the precinct wall and side gates as block runs, leaving the spine gate passable.
- [x] 4.3 Dress the spirit-way band (statues/lanterns/stele) reusing the existing lantern/paifang placement path; build the colonnade as a covered walk (posts + roof line).
- [x] 4.4 Place side-hall parcels via the existing parcel realization path with the planner-chosen archetype and capped tier/storey.
- [x] 4.5 Extend `validateRealizedTown` (Java) with the same precinct invariants and a same-seed Python/Java parity assertion.

## 5. Validation artifacts and verification

- [x] 5.1 Regenerate `reports/town_generation_validation.json` and the layout/town-plan previews; review intended diffs (core emptiness down, new precinct elements present).
- [x] 5.2 Run the existing town-generation validation tooling and the Gradle build; ensure both pass.
- [x] 5.3 Verify in-world: the core reads as a walled compound entered through a precinct gate, the spirit way leads to an enclosed plaza, and the spine is traversable end to end from town gate to shrine.
- [x] 5.4 Confirm same-seed regeneration is identical across two runs (planner and realizer).
