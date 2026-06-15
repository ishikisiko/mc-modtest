## ADDED Requirements

### Requirement: A command builds a town in the live world against terrain
The mod SHALL expose a `/myvillage town [seed]` command that plans and builds a complete town at the player's location, reading the live terrain of the loaded chunks. The command operates only within loaded chunks and SHALL report or clamp footprints that exceed the loaded area.

#### Scenario: Summoning a town
- **WHEN** an operator runs `/myvillage town` at a location with sufficient loaded chunks
- **THEN** the mod SHALL build a town with enclosure, a main-street spine, parcels, streets, and lived-in tissue at that location
- **AND** if the planned footprint exceeds the loaded area, the command SHALL clamp or refuse with a reported extent.

### Requirement: Parcels meet the ground via bounded site-fit
Each realized parcel SHALL meet the terrain through a plinth, steps, or retaining course so it neither floats above nor is buried in the ground. Runtime template placement SHALL align the template's ground layer to the parcel surface and provide continuous footprint support so placed buildings do not float over a one-block hollow. A parcel whose ground is steeper than a configured maximum slope SHALL be skipped rather than force-flattened.

#### Scenario: A parcel on a slope sits on the ground
- **WHEN** a parcel is realized on sloping terrain within the slope limit
- **THEN** it SHALL be joined to the ground by a plinth, steps, or retaining course
- **AND** it SHALL neither float above nor be buried in the surrounding ground
- **AND** no one-block air gap SHALL remain under the building footprint.

#### Scenario: An over-steep parcel is skipped
- **WHEN** a parcel's ground exceeds the configured maximum slope
- **THEN** the parcel SHALL be skipped rather than force-flattened
- **AND** the skip SHALL be reported.

### Requirement: A realized town passes town-level structural validation
A town-level validator SHALL confirm that the enclosure is closed, every parcel is reachable from the spine through the street network, every gate lies on the wall, and no building footprint overlaps a street cell.

#### Scenario: A valid town passes and a broken one fails
- **WHEN** the town validator runs on a generated town
- **THEN** it SHALL pass when the enclosure is closed, all parcels are street-reachable, gates are on the wall, and no footprint overlaps a street
- **AND** it SHALL fail and report the offending invariant on a town that violates any of these.

### Requirement: Town generation is deterministic per seed and site
Given the same seed and the same site, the town generator SHALL produce the same town.

#### Scenario: Reproducible from seed and site
- **WHEN** `/myvillage town <seed>` is run twice on the same site
- **THEN** the two generated towns SHALL be identical.

### Requirement: Town generation ships as a verifiable, complete mod
A town-acceptance pass SHALL produce a built mod jar, a passing town-validator run, a top-down plan preview, and command documentation listing `/myvillage town`. The default town size SHALL be bounded, and an oversize request SHALL be refused or clamped with a reported extent.

#### Scenario: Acceptance produces a complete, documented mod
- **WHEN** a staged acceptance pass is requested for town generation
- **THEN** it SHALL produce a built mod jar and a passing town-validator run
- **AND** it SHALL produce a top-down plan preview and updated docs listing `/myvillage town`.

#### Scenario: Default size is bounded
- **WHEN** a town is requested at or below the default size
- **THEN** it SHALL build within the bounded footprint
- **AND** a request above the bound SHALL be refused or clamped with a reported extent.
