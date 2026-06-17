## 1. Fix the one-block sink (P0 bug, ships independently)

- [x] 1.1 Inspect one shipped parcel NBT's bottom layer to confirm it is the floor (not a buried foundation course); record the finding.
- [x] 1.2 In `realizeParcels`, set the template origin Y to `fit.baseY - 1` (set `TEMPLATE_GROUND_LAYER = 0`), raising buildings one block to sit flush with the street course.
- [x] 1.3 Re-check `clearVolume` and `placeFootprintSupport` Y references so footprint support lands directly under the raised floor with no new air gap and headroom clears correctly.
- [ ] 1.4 Verify in-world: building floor is flush with the adjoining street/ground, neither sunk nor floating, no one-block hollow underneath.

## 2. Frontage building variety (town-block-variety)

- [x] 2.1 Add a deterministic per-parcel variant index derived from the town seed (independent of `idx[0]` alley coupling).
- [x] 2.2 In `subdivideFrontage`, choose the variant per segment via `variant(d.rosterHead, k)` instead of a single `canonicalTemplate`.
- [x] 2.3 Make segmentation width-aware: choose the variant at segment start, advance `i` by that variant's `templateWidth`; close the row with an alley when the remaining run cannot fit the smallest variant.
- [x] 2.4 Build a `StructurePlaceSettings` with seeded per-parcel mirror/rotation constrained to keep the frontage edge on the street line; recompute the `px/pz` anchor so the party wall stays flush.
- [x] 2.5 Verify a long block uses ≥2 distinct variants, the street wall remains continuous, and same-seed regeneration is identical.

## 3. Depth densification — secondary band (district-densification, primary)

- [x] 3.1 Add a depth threshold and, in `subdivideDistrict`, when yard depth ≥ threshold, carve a secondary frontage band facing an interior lane behind the primary band.
- [x] 3.2 Run frontage subdivision on the secondary band using a smaller ancillary archetype; reserve a traversable interior lane between the bands.
- [x] 3.3 Reduce the residual `courtyard_yard` region to the depth left after the secondary band.
- [x] 3.4 Verify deep districts gain a back row with a clear interior lane and a smaller residual yard; shallow districts are unchanged and nothing overlaps or blocks circulation.

## 4. Courtyard dressing (district-densification, secondary)

- [x] 4.1 Implement the `courtyard_yard` branch in `dressNegativeSpaces` (currently a no-op): enclose the region edge with a low wall/fence.
- [x] 4.2 Scatter ≥2 distinct domestic prop types (well, planting plot, drying rack, woodpile, urns, seating) on free cells, routed through free-cell/street-cell guards.
- [ ] 4.3 Verify dressed courtyards have an enclosing edge and props, with no prop on a street/alley/gate cell.

## 5. Spine streetscape (district-densification, secondary)

- [x] 5.1 Add a spine-streetscape pass over `plan.spine` placing recurring props (stalls, banner/lantern poles, carts, crates) at spine edges, leaving the central walking width clear.
- [x] 5.2 Apply the existing market-to-lane density falloff so the central square is denser than back lanes.
- [ ] 5.3 Verify the spine reads as a market street and remains traversable end to end.

## 6. Validation and reports

- [x] 6.1 Regenerate `reports/town_generation_validation.json` and the layout/town-plan previews; review intended diffs (parcel counts, footprints).
- [x] 6.2 Confirm same-seed reproducibility across two generations.
- [x] 6.3 Run the existing town-generation validation tooling and the Gradle build; ensure both pass.
