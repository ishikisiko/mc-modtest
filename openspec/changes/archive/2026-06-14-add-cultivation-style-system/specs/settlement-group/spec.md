## ADDED Requirements

### Requirement: Settlement groups bind style, roster, and layout
The generator SHALL define a settlement-group layer above the style profile. Each group SHALL bind a `style_id`, an archetype roster, and a layout strategy into one named family. The generator SHALL ship two groups: `cultivation_town` (mortal, standalone/street layout) and `cultivation_sect` (immortal, terraced axial compound layout).

#### Scenario: A group resolves its bindings
- **WHEN** the generator selects the `cultivation_sect` group
- **THEN** it SHALL resolve the `cultivation_sect` style profile
- **AND** it SHALL restrict archetype selection to the sect roster
- **AND** it SHALL use the sect terraced/axial layout strategy.

#### Scenario: A town group uses the standalone layout
- **WHEN** the generator selects the `cultivation_town` group
- **THEN** it SHALL resolve the `cultivation_town` style profile
- **AND** it SHALL restrict archetype selection to the town roster
- **AND** it SHALL use the standalone building layout rather than the compound layout.

### Requirement: Group rosters do not force archetype sharing
Each settlement group SHALL declare its own archetype roster. A group's roster MAY be disjoint from another group's roster. The generator SHALL NOT require town archetypes (e.g. `民居`/houses, shops) and sect archetypes (e.g. `大殿`/hall, `藏经阁`/tower) to share massing logic.

#### Scenario: Selecting an archetype outside the active roster
- **WHEN** generation requests an archetype that is not in the active group's roster
- **THEN** the generator SHALL reject the request rather than substitute a different group's archetype.

### Requirement: Settlement groups are the documented extension hook
New settlement families SHALL be introduced by adding a new group descriptor (style + roster + layout) rather than by branching on naming conventions of `style_id`. The group layer SHALL be the documented mechanism for future families.

#### Scenario: A contributor adds a new family
- **WHEN** a contributor proposes a new settlement family
- **THEN** they SHALL add a new group descriptor binding its style, roster, and layout
- **AND** they SHALL NOT identify the family by string-matching the `style_id`.
