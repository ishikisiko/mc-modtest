# Validation

## Purpose

This spec captures the current validation baseline. It is temporary and mutable; proposed changes to hard gates, warnings, or acceptance criteria should be discussed with the project owner first.

## Requirements

### Requirement: Structure JSON validation checks schema, bounds, metadata, and vanilla block ids
The Structure JSON validator SHALL check JSON shape, size and coordinate bounds, palette resolution, supported operations, metadata rules, and Minecraft `1.21.1` vanilla block id existence.

#### Scenario: A non-vanilla block id is referenced
- **WHEN** Structure JSON validation sees a blockstate outside the `minecraft` namespace
- **THEN** validation SHALL fail because the current validator only validates vanilla Minecraft `1.21.1` blocks.

### Requirement: Build quality check gates export
Generated buildings SHALL pass the build quality check before they are exported into the building library.

#### Scenario: A generated building has no door
- **WHEN** quality checking sees no door blockstate
- **THEN** the building SHALL fail with a `no_entrance` error
- **AND** the generator MAY retry with another deterministic seed attempt before giving up.

### Requirement: Quality warnings do not fail export by themselves
Quality warnings and scores SHALL be diagnostic unless they are represented as hard errors.

#### Scenario: A generated building has few exterior decorations
- **WHEN** quality checking records `few_decorations`
- **THEN** the report SHALL include a warning
- **AND** the building MAY still pass if there are no hard errors.

### Requirement: Exported building library validation checks actual mod resources
The building library resource validator SHALL validate exported NBT and mcfunction files from the mod resources tree, not in-memory generation state.

#### Scenario: A generated NBT file is missing
- **WHEN** the validator expects `small_house_001.nbt`
- **AND** the file is absent from `src/main/resources/data/myvillage/structure/`
- **THEN** validation SHALL fail with `missing_file`.

### Requirement: Exported NBT validation checks roof and interior heuristics
The generated-structure NBT validator SHALL check parseability, non-empty palettes and blocks, valid size, roof-like blocks in upper layers, non-empty top layers, key building materials, and expected archetype signature markers for houses, blacksmiths, civic structures, and cultivation structures.

#### Scenario: A house NBT lacks a furnace marker
- **WHEN** generated-structure validation checks a structure whose filename starts with `small_house`, `medium_house`, or `big_house`
- **AND** no present blockstate contains `furnace`
- **THEN** validation SHALL fail with a house function-block error.

#### Scenario: A cultivation sect NBT lacks sect-form markers
- **WHEN** generated-structure validation checks a cultivation sect standalone or compound structure
- **THEN** the palette or block layout SHALL contain expected sect markers from the cultivation form/material vocabulary
- **AND** validation SHALL fail with a cultivation-signature error if those markers are absent.

### Requirement: Visual review remains outside full automation
Automated validation SHALL NOT be treated as complete visual acceptance. In-game placement via `/myvillage gallery`, `/myvillage gallery original`, `/myvillage gallery cultivation`, or `/place template` remains the review path for visual issues such as roof holes, gable appearance, stair/slab facing, and layout readability.

#### Scenario: Automated validators pass
- **WHEN** all automated validators report success
- **THEN** the generated structures SHALL be considered mechanically valid
- **AND** final visual acceptance SHOULD still include in-game review for appearance-sensitive changes.

### Requirement: Manual acceptance has documented preparation
Staged manual acceptance SHALL start from a prepared mod artifact and command list. The acceptance handoff SHALL NOT rely only on generated NBT files or validator reports.

#### Scenario: A reviewer starts a staged acceptance pass
- **WHEN** a reviewer is asked to inspect generated structures in game
- **THEN** a current mod jar build path SHALL be available or documented
- **AND** the README command list SHALL include `/myvillage list`, `/myvillage place <structure_id>`, `/myvillage gallery`, `/myvillage gallery original`, and `/myvillage gallery cultivation`
- **AND** the changelog SHALL identify the version or fix label under review
- **AND** the reviewer SHOULD first run `/myvillage list` to confirm the expected templates are loaded before placing individual structures.

### Requirement: Compound validation checks generated resources
The compound library validator SHALL validate generated courtyard report data and exported mod resources, including NBT files and generated place/gallery functions.

#### Scenario: The Chinese courtyard library is validated
- **WHEN** `tools/validate_compound_library.py --count 6` succeeds
- **THEN** six distinct compound structures SHALL be validated
- **AND** the validator SHALL confirm exported NBTs include compound landscape markers such as water and planting
- **AND** generated place/gallery functions SHALL exist for the compound library.


### Requirement: Civic structures carry signature role blocks
Generated civic structures SHALL contain archetype-specific signature blocks that distinguish them from housing, commercial, and industrial archetypes. The generated-structure NBT validator SHALL enforce these signatures alongside the existing house utility-block and blacksmith forge-block rules.

#### Scenario: A tavern NBT is validated
- **WHEN** generated-structure validation checks a structure whose filename starts with `tavern_`
- **THEN** the palette SHALL contain `minecraft:brewing_stand` OR at least three `minecraft:barrel` blockstate entries
- **AND** the palette SHALL contain at least one `minecraft:bed` blockstate
- **AND** validation SHALL fail with a civic-signature error if either check fails.

#### Scenario: A lord manor NBT is validated
- **WHEN** generated-structure validation checks a structure whose filename starts with `lord_manor_`
- **THEN** the palette SHALL contain `minecraft:bell` OR `minecraft:lectern`
- **AND** the palette SHALL contain at least one banner blockstate matching `minecraft:.*banner`
- **AND** validation SHALL fail with a civic-signature error if either check fails.

#### Scenario: A non-civic structure is validated
- **WHEN** generated-structure validation checks a structure whose filename does not start with `tavern_` or `lord_manor_`
- **THEN** the civic signature rules SHALL NOT apply
- **AND** the existing house and blacksmith rules SHALL apply unchanged.

### Requirement: Civic library validator checks generated resources
The civic library validator SHALL validate exported civic NBT files and the generated civic place/gallery mcfunction files. It SHALL be invocable separately from the medieval library validator and the compound library validator.

#### Scenario: The civic library is validated
- **WHEN** `tools/validate_civic_library.py` succeeds
- **THEN** five tavern and three lord manor NBT files SHALL be validated
- **AND** each civic NBT SHALL pass the civic signature-block gate
- **AND** generated `place/tavern_*.mcfunction`, `place/lord_manor_*.mcfunction`, and `gallery/civic.mcfunction` SHALL exist.

### Requirement: Cultivation policy and form checks are explicit
Cultivation style policy and form vocabulary regression checks SHALL be invocable separately from broad NBT validation. The policy check SHALL verify that spirit materials pass for sect styles and fail for town styles. The form check SHALL verify that cultivation-only forms are registered and are not invoked by legacy medieval or Chinese generation.

#### Scenario: Cultivation style policy is checked
- **WHEN** `tools/check_style_policy.py` succeeds
- **THEN** a sect-only spirit material SHALL pass the active sect forbidden-block policy
- **AND** the same material SHALL fail the active town forbidden-block policy.

#### Scenario: Cultivation forms are checked
- **WHEN** `tools/check_cultivation_forms.py` succeeds
- **THEN** the registered cultivation forms SHALL be exercisable by cultivation generation
- **AND** legacy medieval and Chinese samples SHALL not invoke cultivation-only forms.
