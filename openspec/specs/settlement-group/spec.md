# Settlement Group

## Purpose

This spec captures settlement groups as the family-level binding between style profiles, archetype rosters, and layout strategies.

## Requirements

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
- **AND** courtyard-street blocks MAY still be generated as internal parcel/review forms.

### Requirement: A settlement group supports an optional soft functional brief
A settlement group SHALL be allowed to declare a soft functional brief. The town planner SHALL treat any declared brief as guidance and SHALL NOT treat it as a hard constraint that must be satisfied.

#### Scenario: The town group carries a soft brief
- **WHEN** the `cultivation_town` group declares a functional brief
- **THEN** the planner SHALL bias the town toward the declared functions
- **AND** generation SHALL still succeed when the brief cannot be fully satisfied on the site.

### Requirement: Group rosters do not force archetype sharing
Each settlement group SHALL declare its own archetype roster. A group's roster MAY be disjoint from another group's roster. The generator SHALL NOT require town archetypes such as houses and shops and sect archetypes such as halls and towers to share massing logic.

#### Scenario: Selecting an archetype outside the active roster
- **WHEN** generation requests an archetype that is not in the active group's roster
- **THEN** the generator SHALL reject the request rather than substitute a different group's archetype.

### Requirement: Settlement groups are the documented extension hook
New settlement families SHALL be introduced by adding a new group descriptor (style, roster, and layout) rather than by branching on naming conventions of `style_id`. The group layer SHALL be the documented mechanism for future families.

#### Scenario: A contributor adds a new family
- **WHEN** a contributor proposes a new settlement family
- **THEN** they SHALL add a new group descriptor binding its style, roster, and layout
- **AND** they SHALL NOT identify the family by string-matching the `style_id`.
