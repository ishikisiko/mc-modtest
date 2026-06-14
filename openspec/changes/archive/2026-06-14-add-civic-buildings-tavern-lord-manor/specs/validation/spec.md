## ADDED Requirements

### Requirement: Civic structures carry signature role blocks
Generated civic structures SHALL contain archetype-specific signature
blocks that distinguish them from housing, commercial, and industrial
archetypes. The generated-structure NBT validator SHALL enforce these
signatures alongside the existing house utility-block and blacksmith
forge-block rules.

#### Scenario: A tavern NBT is validated
- **WHEN** generated-structure validation checks a structure whose
  filename starts with `tavern_`
- **THEN** the palette SHALL contain `minecraft:brewing_stand` OR at
  least three `minecraft:barrel` blockstate entries
- **AND** the palette SHALL contain at least one `minecraft:bed`
  blockstate
- **AND** validation SHALL fail with a civic-signature error if
  either check fails.

#### Scenario: A lord manor NBT is validated
- **WHEN** generated-structure validation checks a structure whose
  filename starts with `lord_manor_`
- **THEN** the palette SHALL contain `minecraft:bell` OR
  `minecraft:lectern`
- **AND** the palette SHALL contain at least one banner blockstate
  matching `minecraft:.*banner`
- **AND** validation SHALL fail with a civic-signature error if
  either check fails.

#### Scenario: A non-civic structure is validated
- **WHEN** generated-structure validation checks a structure whose
  filename does not start with `tavern_` or `lord_manor_`
- **THEN** the civic signature rules SHALL NOT apply
- **AND** the existing house and blacksmith rules SHALL apply
  unchanged.

### Requirement: Civic library validator checks generated resources
The civic library validator SHALL validate exported civic NBT files
and the generated civic place/gallery mcfunction files. It SHALL be
invocable separately from the medieval library validator and the
compound library validator.

#### Scenario: The civic library is validated
- **WHEN** `tools/validate_civic_library.py` succeeds
- **THEN** five tavern and three lord manor NBT files SHALL be
  validated
- **AND** each civic NBT SHALL pass the civic signature-block gate
- **AND** generated `place/tavern_*.mcfunction`,
  `place/lord_manor_*.mcfunction`, and
  `gallery/civic.mcfunction` SHALL exist.
