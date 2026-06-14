## ADDED Requirements

### Requirement: Style profile schema includes civic and furniture slots
The style profile schema SHALL recognize additional material slots
`INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, and `HERALDRY` alongside
the existing `BASE_STONE`, `WALL_MAIN`, `FRAME_WOOD`, `ROOF_DARK`,
`DETAIL_WOOD`, `LIGHTING`, `GROUND_PATH`, `INTERIOR_WORK`, and
`INTERIOR_STORAGE`. A style profile MAY omit any of these slots, in
which case generators referencing the missing slot SHALL skip
placement of that slot's blocks.

#### Scenario: The medieval village style defines civic slots
- **WHEN** the `medieval_village` style is loaded
- **THEN** the profile SHALL include `INTERIOR_CIVIC` containing at
  least `brewing_stand`, `lectern`, `bell`, `bookshelf`, `cauldron`
- **AND** it SHALL include `FURNITURE` containing at least `bed` and
  `chest`
- **AND** it SHALL include `SIGNAGE` containing at least
  `standing_sign` and `wall_sign`
- **AND** it SHALL include `HERALDRY` containing at least
  `standing_banner` and `wall_banner`.

#### Scenario: A civic builder resolves a heraldry block
- **WHEN** a civic builder needs a banner block
- **THEN** it SHALL resolve the blockstate through the `HERALDRY`
  slot
- **AND** the banner color SHALL be sampled deterministically from a
  curated medieval heraldic palette keyed by the generation seed.

### Requirement: Forbidden blocks policy is narrowly scoped
The `forbidden_blocks` list SHALL preserve the medieval palette by
excluding modern, nether, metallic treasure, and technical blocks.
The list SHALL NOT block placement of furniture or signage blocks
that are placement-safe in their default state. Specifically, `bed`,
`chest`, `banner`, and `sign` SHALL NOT appear in the
`medieval_village` style profile's `forbidden_blocks`.

#### Scenario: The medieval village forbidden list is checked
- **WHEN** the `medieval_village` style profile is loaded
- **THEN** `forbidden_blocks` SHALL NOT contain `bed`, `chest`,
  `banner`, or `sign`
- **AND** it SHALL still contain palette-breaking fragments such as
  `quartz`, `concrete`, `terracotta`, `warped_`, `crimson_`,
  `iron_block`, `copper`, `gold_block`, `netherite`, `spawner`,
  `command_block`, `shulker`, `jukebox`, and `beacon`.

#### Scenario: A civic interior places a bed
- **WHEN** a tavern inn zone places a bed
- **THEN** the quality gate SHALL NOT reject the bed blockstate
- **AND** the bed SHALL be placed as a two-block foot+head blockstate
  pair with no NBT.

### Requirement: Chests and brewing stands ship empty
Civic interiors MAY place `chest` and `brewing_stand` blocks. Such
blocks SHALL be placed in their default empty state with no
inventory NBT, no loot table reference, and no brewed potion
contents. The export pipeline SHALL NOT inject loot tables for any
civic-generated structure.

#### Scenario: A lord manor places a chest
- **WHEN** a lord manor private quarters zone places a chest
- **THEN** the exported block entity (if any) SHALL have an empty
  inventory
- **AND** no loot table reference SHALL be present.

#### Scenario: A tavern places a brewing stand
- **WHEN** a tavern hall zone places a brewing stand
- **THEN** the exported block entity (if any) SHALL have no brewing
  contents
- **AND** no ingredient, potion, or fuel item SHALL be present.
