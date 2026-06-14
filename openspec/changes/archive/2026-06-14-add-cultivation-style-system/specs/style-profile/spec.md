## ADDED Requirements

### Requirement: Style profile schema includes spirit material slots
The style profile schema SHALL recognize additional material slots `SPIRIT_CRYSTAL` and `RITUAL_METAL` alongside the existing slots. A style profile MAY omit these slots, in which case generators referencing a missing slot SHALL skip placement of that slot's blocks.

#### Scenario: The cultivation sect style defines spirit slots
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** the profile SHALL include a `SPIRIT_CRYSTAL` slot containing amethyst-family blocks
- **AND** it SHALL include a `RITUAL_METAL` slot containing oxidized-copper-family blocks.

#### Scenario: A mortal style omits spirit slots
- **WHEN** the `cultivation_town` style is loaded and a generator requests `SPIRIT_CRYSTAL`
- **THEN** the generator SHALL skip placement of that slot's blocks rather than fail.

### Requirement: Cultivation style profiles exist
The generator SHALL provide `cultivation_town` and `cultivation_sect` style profiles loadable through the existing style-loading mechanism, each defining `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`.

#### Scenario: Loading the cultivation town style
- **WHEN** the generator requests style id `cultivation_town`
- **THEN** it SHALL load `tools/buildgen/styles/cultivation_town.json`
- **AND** the loaded profile SHALL include all required schema sections.

#### Scenario: Loading the cultivation sect style
- **WHEN** the generator requests style id `cultivation_sect`
- **THEN** it SHALL load `tools/buildgen/styles/cultivation_sect.json`
- **AND** `allowed_roof_types` SHALL include `tiered_eave_roof`.

### Requirement: Forbidden blocks are per-style and unlock spirit materials for the sect
The `forbidden_blocks` policy SHALL be evaluated against the active style profile. The `cultivation_sect` profile SHALL NOT forbid `quartz`, `copper`, or `gold_block`, thereby unlocking spirit materials. The `cultivation_town` profile SHALL continue to forbid those palette-breaking materials.

#### Scenario: A spirit material passes in the sect style
- **WHEN** a quartz, copper, or oxidized-copper blockstate appears in a `cultivation_sect` build
- **THEN** the quality forbidden-block gate SHALL NOT reject it.

#### Scenario: A spirit material fails in the town style
- **WHEN** a quartz or copper blockstate appears in a `cultivation_town` build
- **THEN** the quality forbidden-block gate SHALL reject it
- **AND** the report SHALL include a `forbidden_blocks` error.
