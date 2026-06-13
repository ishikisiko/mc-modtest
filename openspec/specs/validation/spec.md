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
The generated-structure NBT validator SHALL check parseability, non-empty palettes and blocks, valid size, roof-like blocks in upper layers, non-empty top layers, key building materials, and expected house or blacksmith function-block markers.

#### Scenario: A house NBT lacks a furnace marker
- **WHEN** generated-structure validation checks a structure whose filename contains `house`
- **AND** no present blockstate contains `furnace`
- **THEN** validation SHALL fail with a house function-block error.

### Requirement: Visual review remains outside full automation
Automated validation SHALL NOT be treated as complete visual acceptance. In-game placement via `/myvillage gallery` or `/place template` remains the review path for visual issues such as roof holes, gable appearance, stair/slab facing, and layout readability.

#### Scenario: Automated validators pass
- **WHEN** all automated validators report success
- **THEN** the generated structures SHALL be considered mechanically valid
- **AND** final visual acceptance SHOULD still include in-game review for appearance-sensitive changes.
