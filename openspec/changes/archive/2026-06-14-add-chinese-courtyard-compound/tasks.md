## 0. Prerequisite

- [x] 0.1 Confirm change `add-western-multistory-buildings` (multi-story capability) is merged; `main_hall` two-story reuse depends on it.

## 1. Chinese style profile

- [x] 1.1 Add a Chinese style JSON under `tools/buildgen/styles/` with the existing profile schema (material_slots, allowed_roof_types incl. 硬山/悬山/歇山, allowed_wall_types, opening styles, motifs, forbidden_blocks, proportions) for sloped roofs, timber frame, white walls.
- [x] 1.2 Add Western-incompatible blocks to `forbidden_blocks` so the quality gate enforces style separation.
- [x] 1.3 Verify the profile loads via `tools/buildgen/style.py` and a smoke build resolves materials through its slots.

## 2. Chinese sub-building archetypes

- [x] 2.1 Implement `build_main_hall` (正房) in `tools/buildgen/archetypes.py` using the Chinese style; allow two stories via the multi-story capability.
- [x] 2.2 Implement `build_side_wing` (厢房) and `build_front_row` (倒座房) as single-story Chinese archetypes; do not reuse `small_house` massing.
- [x] 2.3 Add any Chinese-specific build ops (sloped roof grades, timber detailing) needed by these archetypes.
- [x] 2.4 Generate each sub-building standalone for visual review before wiring the compound.

## 3. CompoundGraph parcel layer

- [x] 3.1 Create `tools/buildgen/compound.py` with a `CompoundGraph` holding parcel nodes (perimeter_wall, water_feature, planting, corridor, path) and building slots.
- [x] 3.2 Implement the one-courtyard (一进) axial layout: gate_house south, front_row, two side_wing east/west, main_hall north; compute lot size from the courtyard-size axis.
- [x] 3.3 Generate each building slot via the existing per-building pipeline and translate the resulting MassingGraph into its parcel position.
- [x] 3.4 Generate the four-sided perimeter_wall with a single gate opening on the central axis.

## 4. Landscape and circulation as structure

- [x] 4.1 Reserve water_feature and planting cells on a parcel occupancy grid; ensure building footprints never overlap them.
- [x] 4.2 Route the central path (gate → main hall) and corridors (wings → main hall) around water/planting; assert gate-to-hall connectivity.

## 5. Combinatorial variants

- [x] 5.1 Define variant axes and options: courtyard size (small/medium/large), water form (pool/channel/third), planting layout (3), roof grade (硬山/悬山/歇山), gate style (3), symmetry (mild-asymmetry default / strict-mirror).
- [x] 5.2 Implement per-seed axis selection; apply strict-mirror by reflecting wings across the axis and mild-asymmetry within bounds for the default.
- [x] 5.3 Implement compound library sampling that emits N distinct combinations (default 6).

## 6. Export and validation

- [x] 6.1 Add a compound export path (single NBT and/or a place mcfunction stamping sub-buildings + landscape) under `src/main/resources/data/myvillage/`; decide form against structure size limits.
- [x] 6.2 Add a compound validator: perimeter encloses all buildings, gate opening on axis, no building/landscape overlap, gate-to-hall connectivity, and ≥6 distinct sampled instances.
- [x] 6.3 Add a quality check that Chinese sub-buildings pass the Chinese style forbidden-block gate.

## 7. Regenerate, verify, document

- [x] 7.1 Generate the compound library and run validators; confirm assets appear under `src/main/resources/data/myvillage/`.
- [x] 7.2 Smoke-test in game: place one compound; confirm axial layout, perimeter+gate, inner water/planting, corridors, and a two-story main hall read correctly.
- [x] 7.3 Update `README.md` with compound generation/output.
- [x] 7.4 Sync `courtyard-compound`, `building-generation`, and `style-profile` spec deltas into `openspec/specs/` on archive.
