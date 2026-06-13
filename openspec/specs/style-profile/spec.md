# Style Profile

## Purpose

This spec captures the current style profile implementation baseline. It is temporary and mutable; proposed changes to style vocabulary, slot semantics, or validation should be discussed with the project owner first.

## Requirements

### Requirement: Style profiles define abstract material and rule vocabularies
The building generator SHALL load style profiles from `tools/buildgen/styles/<style_id>.json` and expose abstract material slots, allowed roof types, allowed wall types, allowed opening styles, allowed motifs, forbidden block fragments, variation rates, and proportion rules.

#### Scenario: Loading the medieval village style
- **WHEN** the generator requests style id `medieval_village`
- **THEN** it SHALL load `tools/buildgen/styles/medieval_village.json`
- **AND** the loaded profile SHALL include `material_slots`, `allowed_roof_types`, `allowed_wall_types`, `allowed_opening_styles`, `allowed_motifs`, `forbidden_blocks`, and `proportions`.

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

### Requirement: Variation preserves protected cells
Material variation SHALL NOT modify cells tagged as `PROTECTED`.

#### Scenario: A protected doorway cell uses a variable slot
- **WHEN** the material variation pass runs
- **THEN** the protected doorway cell SHALL keep its existing blockstate
- **AND** unprotected eligible facade cells MAY be varied according to the style profile.
