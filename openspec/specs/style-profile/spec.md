# Style Profile

## Purpose

This spec captures the current style profile implementation baseline. It is temporary and mutable; proposed changes to style vocabulary, slot semantics, or validation should be discussed with the project owner first.

## Requirements

### Requirement: Style profiles define abstract material and rule vocabularies
The building generator SHALL load style profiles from `tools/buildgen/styles/<style_id>.json` and expose abstract material slots, allowed roof types, allowed wall types, allowed opening styles, allowed motifs, forbidden block fragments, variation rates, and proportion rules. Style vocabulary validation SHALL require every listed roof type and motif to exist in the corresponding form registry.

#### Scenario: Loading the medieval village style
- **WHEN** the generator requests style id `medieval_village`
- **THEN** it SHALL load `tools/buildgen/styles/medieval_village.json`
- **AND** the loaded profile SHALL include `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`.

#### Scenario: Loading the Chinese courtyard style
- **WHEN** the generator requests style id `chinese_courtyard`
- **THEN** it SHALL load `tools/buildgen/styles/chinese_courtyard.json`
- **AND** the loaded profile SHALL include `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`
- **AND** `allowed_roof_types` SHALL include `硬山`, `悬山`, and `歇山`.

#### Scenario: Loading a style with an unknown form
- **WHEN** a style profile lists an `allowed_roof_types` or `allowed_motifs` entry that is not registered
- **THEN** loading the style SHALL fail with an error identifying the unknown form name.

### Requirement: Build operations resolve primary materials through style slots
Build operations SHALL use style material slots rather than hardcoding concrete block choices for primary building materials.

#### Scenario: A roof block is needed
- **WHEN** a build operation needs roof stairs, roof slabs, or roof planks
- **THEN** it SHOULD resolve them through the `ROOF_DARK` slot
- **AND** it SHOULD preserve the final full Minecraft blockstate string.

### Requirement: Forbidden style blocks are quality-gated
Generated building quality checks SHALL reject generated buildings containing blockstates whose block id matches a fragment listed in the active style profile's `forbidden_blocks`.

#### Scenario: A forbidden block appears in a generated building
- **WHEN** quality validation sees a non-air blockstate whose block id contains a forbidden fragment
- **THEN** the generated building SHALL fail the quality gate
- **AND** the report SHALL include a `forbidden_blocks` error.

#### Scenario: Chinese style excludes Western-only materials
- **WHEN** a Chinese courtyard sub-building or compound is validated
- **THEN** blockstates matching the Chinese style profile's Western-incompatible forbidden fragments SHALL fail validation
- **AND** the generated resource SHALL not be accepted for staged manual review until the forbidden-block gate passes.

### Requirement: Variation preserves protected cells
Material variation SHALL NOT modify cells tagged as `PROTECTED`.

#### Scenario: A protected doorway cell uses a variable slot
- **WHEN** the material variation pass runs
- **THEN** the protected doorway cell SHALL keep its existing blockstate
- **AND** unprotected eligible facade cells MAY be varied according to the style profile.


### Requirement: Style profile schema includes civic and furniture slots
The style profile schema SHALL recognize additional material slots `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, `HERALDRY`, `SPIRIT_CRYSTAL`, and `RITUAL_METAL` alongside the existing `BASE_STONE`, `WALL_MAIN`, `FRAME_WOOD`, `ROOF_DARK`, `DETAIL_WOOD`, `LIGHTING`, `GROUND_PATH`, `INTERIOR_WORK`, and `INTERIOR_STORAGE` slots. A style profile MAY omit any of these slots, in which case generators referencing the missing slot SHALL skip placement of that slot's blocks.

#### Scenario: The medieval village style defines civic slots
- **WHEN** the `medieval_village` style is loaded
- **THEN** the profile SHALL include `INTERIOR_CIVIC` containing at least `brewing_stand`, `lectern`, `bell`, `bookshelf`, and `cauldron`
- **AND** it SHALL include `FURNITURE` containing at least `bed` and `chest`
- **AND** it SHALL include `SIGNAGE` containing at least `standing_sign` and `wall_sign`
- **AND** it SHALL include `HERALDRY` containing at least `standing_banner` and `wall_banner`.

#### Scenario: A civic builder resolves a heraldry block
- **WHEN** a civic builder needs a banner block
- **THEN** it SHALL resolve the blockstate through the `HERALDRY` slot
- **AND** the banner color SHALL be sampled deterministically from a curated medieval heraldic palette keyed by the generation seed.

#### Scenario: The cultivation sect style defines spirit slots
- **WHEN** the `cultivation_sect` style is loaded
- **THEN** the profile SHALL include a `SPIRIT_CRYSTAL` slot containing amethyst-family blocks
- **AND** it SHALL include a `RITUAL_METAL` slot containing oxidized-copper-family blocks.

#### Scenario: A mortal style omits spirit slots
- **WHEN** the `cultivation_town` style is loaded and a generator requests `SPIRIT_CRYSTAL`
- **THEN** placement using that optional slot SHALL be skipped rather than failing style loading.

### Requirement: Cultivation style profiles exist
The generator SHALL provide `cultivation_town` and `cultivation_sect` style profiles loadable through the existing style-loading mechanism. The town style SHALL use a mortal timber/stone/clay-tile palette and forbid spirit materials. The sect style SHALL use the mortal base plus spirit materials and SHALL allow cultivation forms.

#### Scenario: Loading the cultivation town style
- **WHEN** the generator requests style id `cultivation_town`
- **THEN** it SHALL load `tools/buildgen/styles/cultivation_town.json`
- **AND** the loaded profile SHALL include all required style sections.

#### Scenario: Loading the cultivation sect style
- **WHEN** the generator requests style id `cultivation_sect`
- **THEN** it SHALL load `tools/buildgen/styles/cultivation_sect.json`
- **AND** `allowed_roof_types` SHALL include `tiered_eave_roof`
- **AND** `allowed_motifs` SHALL include cultivation motifs such as `moon_gate`, `spirit_array`, `incense_altar`, or `cloud_rail`.

### Requirement: Forbidden blocks are per-style
The `forbidden_blocks` policy SHALL be evaluated against the active style profile. `cultivation_town` SHALL continue to forbid quartz, copper, and gold-block palette materials. `cultivation_sect` SHALL NOT forbid quartz, copper, or gold-block materials needed for spirit palettes.

#### Scenario: A spirit material passes in the sect style
- **WHEN** a quartz, copper, or oxidized-copper blockstate appears in a `cultivation_sect` build
- **THEN** the quality forbidden-block gate SHALL NOT reject it.

#### Scenario: A spirit material fails in the town style
- **WHEN** a quartz or copper blockstate appears in a `cultivation_town` build
- **THEN** the quality forbidden-block gate SHALL reject it
- **AND** the report SHALL include a `forbidden_blocks` error.

### Requirement: Forbidden blocks policy is narrowly scoped
The `forbidden_blocks` list SHALL preserve the medieval palette by excluding modern, nether, metallic treasure, and technical blocks. The list SHALL NOT block placement of furniture or signage blocks that are placement-safe in their default state. Specifically, `bed`, `chest`, `banner`, and `sign` SHALL NOT appear in the `medieval_village` style profile's `forbidden_blocks`.

#### Scenario: The medieval village forbidden list is checked
- **WHEN** the `medieval_village` style profile is loaded
- **THEN** `forbidden_blocks` SHALL NOT contain `bed`, `chest`, `banner`, or `sign`
- **AND** it SHALL still contain palette-breaking fragments such as `quartz`, `concrete`, `terracotta`, `warped_`, `crimson_`, `iron_block`, `copper`, `gold_block`, `netherite`, `spawner`, `command_block`, `shulker`, `jukebox`, and `beacon`.

#### Scenario: A civic interior places a bed
- **WHEN** a tavern inn zone places a bed
- **THEN** the quality gate SHALL NOT reject the bed blockstate
- **AND** the bed SHALL be placed as a two-block foot+head blockstate pair with no NBT.

### Requirement: Chests and brewing stands ship empty
Civic interiors MAY place `chest` and `brewing_stand` blocks. Such blocks SHALL be placed in their default empty state with no inventory NBT, no loot table reference, and no brewed potion contents. The export pipeline SHALL NOT inject loot tables for any civic-generated structure.

#### Scenario: A lord manor places a chest
- **WHEN** a lord manor private quarters zone places a chest
- **THEN** the exported block entity (if any) SHALL have an empty inventory
- **AND** no loot table reference SHALL be present.

#### Scenario: A tavern places a brewing stand
- **WHEN** a tavern hall zone places a brewing stand
- **THEN** the exported block entity (if any) SHALL have no brewing contents
- **AND** no ingredient, potion, or fuel item SHALL be present.
