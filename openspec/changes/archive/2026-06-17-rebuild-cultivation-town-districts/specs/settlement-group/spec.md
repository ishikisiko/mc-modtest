## ADDED Requirements

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
