## Context

`TownGenerator.java` plans a town as districts → frontage bands → parcels, then realizes parcels from shipped `.nbt` templates and dresses negative space. Three defects degrade the result:

1. **Sink.** `realizeParcels` places each template at `origin.y = fit.baseY - 1 - TEMPLATE_GROUND_LAYER`. With `fit.baseY = getHeight()` (top solid block + 1) and `TEMPLATE_GROUND_LAYER = 1`, the template's bottom layer lands at `top - 1`, one below the street course laid at `top` (`surfacePos = getHeight() - 1`). The shipped templates' bottom layer is the floor, not a buried foundation course, so every building reads one block sunk.
2. **Monotony.** `subdivideDistrict` calls `canonicalTemplate(d.rosterHead)` = `variant(base, 0)` and hands that single archetype to every parcel in `subdivideFrontage`. The `_002/_003` NBTs ship but are never selected; `placeInWorld` uses a bare `new StructurePlaceSettings()` (no rotation/mirror).
3. **Emptiness.** Each district builds one frontage band of depth `templateDepth + yard(8)`; the rest of the district depth becomes one `courtyard_yard` OpenRegion that `dressNegativeSpaces` explicitly leaves bare. The spine is a bare paved strip.

The emptiness fix is a **fusion: depth-densification (③) dominant, spine streetscape (②) + courtyard dress (①) secondary.**

## Goals / Non-Goals

**Goals:**
- Buildings meet the ground flush (no sink, no float), honoring the existing `town-realization` requirement.
- A street block reads as varied (multiple variants + orientation) while keeping a continuous party-wall edge.
- District interiors read as layered depth; residual open space reads as intentional courtyard tissue; the spine reads as a market street.
- Deterministic for a given seed; reproducible reports.

**Non-Goals:**
- No new authored NBT assets required (reuse shipped variants + existing small archetypes for back rows).
- No change to the district grid, gate/civic-core geometry, or the ritual axis.
- No re-authoring of templates to add foundation courses (we fix the offset instead).

## Decisions

### D1 — Fix the sink by removing the ground-layer offset, after verifying template floor convention
Change the parcel origin to `fit.baseY - 1` (drop `- TEMPLATE_GROUND_LAYER`), raising every building one block so the floor sits at the street course. Before committing, inspect one shipped template's bottom layer to confirm it is floor (not a buried foundation). Keep `TEMPLATE_GROUND_LAYER` as a named constant set to `0` rather than deleting it, so the convention stays documented and tunable.
- *Alternative considered:* re-author every NBT to add a foundation course — rejected as far more work and asset churn for the same visual result.
- *Risk guard:* `clearVolume`/`placeFootprintSupport` Y references must be re-checked so support still lands directly under the (now raised) floor with no new air gap.

### D2 — Variant cycling keyed by a seeded per-parcel index
In `subdivideFrontage`, select the template per segment via `variant(d.rosterHead, k)` where `k` is a deterministic per-parcel counter mixed with the town seed (not the global `idx[0]`, to avoid alley-count coupling). This makes a row alternate `_001/_002/_003`.
- *Alternative:* pure random — rejected; seeded-deterministic is required by spec and keeps reports stable.

### D3 — Width-aware segmentation
Because variants may differ in width, `subdivideFrontage` must advance `i` by the chosen variant's `templateWidth`, not a single fixed `module`. Choose the variant first, then cut the segment to its width; if the remaining run can't fit the smallest variant, close the row with an alley as today.
- This couples D2 and D3: variant choice happens at segment start.

### D4 — Orientation/mirror via StructurePlaceSettings
Replace `new StructurePlaceSettings()` with settings carrying a per-parcel `Mirror` (and, where footprint stays square to the frontage, `Rotation`) drawn from the seed. Constrain choices so the frontage edge stays on the street line (mirror across the street-parallel axis is always safe; rotation only when it preserves the frontage orientation). Recompute the `px/pz` anchor after rotation so the party wall stays flush.

### D5 — Depth densification as a second frontage band (③, dominant)
In `subdivideDistrict`, after the primary band + yard split, if the yard depth ≥ threshold (e.g. small-archetype depth + a 2–3 wide interior lane), carve a **secondary band** facing an interior lane and run `subdivideFrontage` on it with a smaller ancillary archetype. The leftover becomes a (now smaller) `courtyard_yard`. Threshold gating keeps shallow districts unchanged.
- *Alternative:* shrink `yard` constant — rejected; that just narrows the void without adding the layering the user wants.

### D6 — Courtyard dressing (①, secondary)
Implement the `courtyard_yard` branch in `dressNegativeSpaces` (currently a no-op): enclose the region edge (low wall/fence) and scatter ≥2 domestic prop types (well, planting plot, drying rack, woodpile, urns, stools) on free cells, reusing existing prop/placement helpers and respecting `freeParcelCells`/street cells so circulation is never blocked.

### D7 — Spine streetscape (②, secondary)
Add a spine-streetscape pass that walks `plan.spine` and places recurring props (stalls, banner/lantern poles, carts, crates) at the spine edges at a spacing that leaves the central walking width clear, with density following the existing market-to-lane falloff used by the tissue layer.

## Risks / Trade-offs

- **Raising all buildings double-shifts if some templates already have a foundation course** → D1 mandates inspecting a template bottom layer first; the bug report ("all sank") indicates a uniform convention.
- **Width-aware segmentation regressions (gaps/overlaps)** → cover with the town-generation validation pass and a layout preview diff before/after.
- **Densification + dressing overcrowding circulation** → all placement routes through free-cell/street-cell guards; secondary band gated on a depth threshold with a mandatory interior lane.
- **Report churn** → regenerate `reports/town_generation_validation.json` and previews; expect intentional diffs in parcel counts and footprints.
- **Determinism** → all randomness seeded from the town seed; verify same-seed reproducibility in validation.

## Open Questions

- Which ancillary archetype to use for the secondary band — a dedicated small shed variant vs. reusing the smallest `cultivation_house` variant? (Default: smallest existing variant; revisit if it reads wrong.)
- Exact depth threshold for D5 and spine prop spacing for D7 — tune against a generated sample before locking numbers.
