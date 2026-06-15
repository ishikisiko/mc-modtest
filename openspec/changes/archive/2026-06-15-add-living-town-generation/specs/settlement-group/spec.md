## MODIFIED Requirements

### Requirement: Settlement groups bind style, roster, and layout
The generator SHALL define a settlement-group layer above the style profile. Each group SHALL bind a `style_id`, an archetype roster, and a layout strategy into one named family. The generator SHALL ship two cultivation groups: `cultivation_town` (mortal, town-generation layout) and `cultivation_sect` (immortal, terraced axial compound layout).

#### Scenario: A group resolves its bindings
- **WHEN** the generator selects the `cultivation_sect` group
- **THEN** it SHALL resolve the `cultivation_sect` style profile
- **AND** it SHALL restrict archetype selection to the sect roster
- **AND** it SHALL use the sect terraced/axial layout strategy.

#### Scenario: A town group uses the town-generation layout
- **WHEN** the generator selects the `cultivation_town` group
- **THEN** it SHALL resolve the `cultivation_town` style profile
- **AND** it SHALL restrict archetype selection to the town roster
- **AND** it SHALL use the town-generation layout rather than the standalone building layout or a single exported block
- **AND** courtyard-street blocks MAY be used by the town planner as an internal parcel form.

## ADDED Requirements

### Requirement: A settlement group supports an optional soft functional brief
A settlement group SHALL be allowed to declare a soft functional brief (e.g. housing / market / civic / defense counts). The town planner SHALL treat any declared brief as guidance to aim toward and SHALL NOT treat it as a hard constraint that must be satisfied.

#### Scenario: The town group carries a soft brief
- **WHEN** the `cultivation_town` group declares a functional brief
- **THEN** the planner SHALL bias the town toward the declared functions
- **AND** generation SHALL still succeed when the brief cannot be fully satisfied on the site.
