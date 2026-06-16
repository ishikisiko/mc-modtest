## MODIFIED Requirements

### Requirement: Cultivation sect terraced axial layout
The compound layer SHALL provide a `cultivation_sect` layout strategy that arranges sect-roster sub-buildings along a central axis at monumental scale across **multiple stacked terrace levels** — not merely two platforms. The axis SHALL ascend from the mountain gate on the lowest terrace to the principal hall on the highest. Each terrace level SHALL form a courtyard (院落) with its own building slots, and adjacent levels SHALL be joined by monumental stairway (蹬道) circulation. The sect strategy SHALL reuse the existing `CompoundGraph` parcel machinery and per-building pass pipeline.

#### Scenario: A sect compound climbs multiple terraces
- **WHEN** a sect compound is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` whose building slots are distributed across three or more terrace levels
- **AND** each level SHALL form a courtyard with its own slots
- **AND** the slots SHALL be ordered along a central axis from the gate at the lowest level to the principal hall at the highest.

#### Scenario: Terrace levels are connected by stairways
- **WHEN** a sect compound places slots on more than one terrace level
- **THEN** monumental stairway circulation SHALL connect each lower level to the next higher level
- **AND** no building footprint SHALL overlap water or planting cells.

#### Scenario: The sect layout is distinct from the courtyard layout
- **WHEN** the sect layout strategy is selected
- **THEN** it SHALL be selected via the settlement group's layout binding
- **AND** it SHALL NOT be produced by the existing one-courtyard `chinese_courtyard` layout.
