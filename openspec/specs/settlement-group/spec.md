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

### Requirement: The cultivation town group carries a district brief
The `cultivation_town` settlement group SHALL define a `district_brief`: an ordered set of districts (`gate`, `market`, `residential`, `civic_core`, `fringe`), each binding a density target, a storey band, a material register, and an archetype roster. The planner SHALL consume this brief generically; district behavior SHALL NOT be selected by matching `style_id` prefixes or district-name strings in generation passes.

#### Scenario: The group exposes a district brief
- **WHEN** the `cultivation_town` group is loaded
- **THEN** it SHALL expose a district brief covering `gate`, `market`, `residential`, `civic_core`, and `fringe`
- **AND** each district SHALL bind a density target, storey band, material register, and archetype roster
- **AND** the planner SHALL read those briefs without branching on style or district name strings.

### Requirement: The cultivation town roster includes vertical archetypes
The `cultivation_town` group's archetype roster SHALL include the `pagoda`, `pavilion`, and `bell_drum_tower` archetypes so the civic-core district can satisfy the skyline relief rule.

#### Scenario: Vertical archetypes are available to the core district
- **WHEN** the planner fills the `civic_core` district
- **THEN** the available archetype roster SHALL include `pagoda`, `pavilion`, and `bell_drum_tower`.

### Requirement: The static compound library is district fill material, not a standalone town
The `cultivation_town_NNN` compound library SHALL be classified as a source of courtyard fill tissue that the `residential` and `market` districts draw from, rather than as a standalone settlement output. The `/myvillage place cultivation_town_001` command MAY remain for placing a fragment, but it SHALL NOT be documented as the canonical cultivation town.

#### Scenario: Compound library supplies district fill
- **WHEN** the planner fills a residential or market district
- **THEN** it MAY draw courtyard tissue from the `cultivation_town_NNN` compound library as fill
- **AND** the compound library SHALL NOT be presented as a standalone town output.

### Requirement: The cultivation_sect group resolves to a realized terraced compound

The `cultivation_sect` group's terraced/axial layout strategy SHALL resolve to the `sect-compound-layout` plan and be realized by the `/myvillage sect` command, so selecting the group produces a terraced axial compound built against terrain rather than a single exported block or a standalone building.

#### Scenario: Selecting the sect group builds a compound

- **WHEN** the generator selects the `cultivation_sect` group and a sect is built
- **THEN** it SHALL produce a terraced axial compound via the `sect-compound-layout` plan and the `/myvillage sect` realizer
- **AND** it SHALL restrict archetype selection to the sect roster
- **AND** it SHALL NOT emit a single exported block or a standalone building in place of the compound.
