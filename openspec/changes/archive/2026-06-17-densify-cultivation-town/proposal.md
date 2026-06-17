## Why

The runtime `/myvillage town` realizer produces a structurally sound town that nonetheless reads as empty and monotonous: districts build a single street-facing building band and leave the entire interior depth as bare grass, every parcel in a district uses the same canonical template, and a placement offset buries every building one block into the ground. The skeleton is good; what is missing is depth, variety, and a correct ground meet.

## What Changes

- **Fix the one-block sink (bug).** Realized buildings currently land one block below street level, burying their bottom layer. Correct the parcel placement Y so a building's floor sits flush with the adjoining street/ground, satisfying the existing `town-realization` "neither float nor be buried" requirement.
- **Densify district interiors (primary fix for emptiness).** Replace the single-frontage-band + one large empty yard with a **double-loaded depth**: a secondary back row of smaller ancillary buildings (厢房/杂物棚) behind the primary frontage, so the interior reads as layered urban fabric (层次) instead of a void.
- **Dress the remaining open space (secondary).** Turn the reserved courtyard negative-space regions into walled 院落 tissue (well, kitchen/herb plots, drying racks, woodpiles, urns, stools) and line the central spine with a 坊市 streetscape (stalls, banner poles, lanterns, carts) so leftover space reads as intentional, lived-in, and market-busy (人烟味/市井).
- **Vary frontage buildings.** Frontage parcels currently hardcode the first template variant; instead cycle across the shipped `_001/_002/_003` variants and apply per-parcel orientation/mirror so a street row no longer reads as one repeated building.

## Capabilities

### New Capabilities
- `town-block-variety`: Frontage parcels within a district SHALL be distributed across the shipped template variants and per-parcel orientation/mirror so a block does not read as a single repeated building, while preserving a continuous party-wall street edge.
- `district-densification`: District interiors SHALL be filled via a secondary depth band of ancillary buildings (primary), dressed courtyard tissue, and a spine streetscape, so reserved open space reads as intentional, layered, lived-in fabric rather than empty lawn.

### Modified Capabilities
<!-- None. The one-block sink is an implementation defect against the existing
     `town-realization` requirement ("neither float above nor be buried ... no
     one-block air gap"); it is fixed in tasks without a requirement change. -->

## Impact

- Code: `src/main/java/com/example/myvillage/town/TownGenerator.java` — parcel placement Y (`realizeParcels`), district subdivision (`subdivideDistrict`/`subdivideFrontage`), template selection (`canonicalTemplate` → variant cycling + `StructurePlaceSettings` rotation/mirror), negative-space dressing (`dressNegativeSpaces` → courtyard + spine streetscape).
- Specs: new `town-block-variety`, `district-densification`; existing `town-realization` and `lived-in-tissue` behavior is honored, not changed.
- Validation/reports: town-generation validation and layout previews regenerate; module-width handling in frontage must account for variant footprints differing in width.
- Existing structure NBTs (`cultivation_house_002/003`, `cultivation_shop_002/003`, etc.) are already shipped; no new authored assets are strictly required for variety, though ancillary back-row templates may be selected from existing small archetypes.
