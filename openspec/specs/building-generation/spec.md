# Building Generation

## Purpose

This spec captures the current procedural building generation baseline. It is temporary and mutable; proposed changes to module boundaries, pass order, archetype composition, or generated structure semantics should be discussed with the project owner first.

## Requirements

### Requirement: The generator uses a layered pipeline
The building generator SHALL use the following conceptual layers: Style Profile, Building Archetype, Scale Tier, Massing Graph, Facade Grammar, Build Ops, Pass and Protection, Quality Check, and Resource Export.

#### Scenario: A building is generated
- **WHEN** `generate_building(style, archetype, scale_tier, seed)` is called
- **THEN** it SHALL run the configured generation passes in deterministic order for that seed
- **AND** it SHALL return a build context containing the generated graph, block grid, and pass metadata.

### Requirement: Archetypes define semantic massing, not direct blocks
Building archetypes SHALL construct a `MassingGraph` made of semantic nodes and metadata before block placement occurs.

#### Scenario: A small house massing is built
- **WHEN** the `small_house` archetype is generated
- **THEN** the massing graph SHALL include a main volume, a front door definition, interior zones, a path patch, exterior decoration patches, and either a chimney or alternate window-detail metadata
- **AND** block placement SHALL occur in later passes.

### Requirement: Supported archetypes and tiers are explicit
The current supported generated building archetypes SHALL be `small_house`, `medium_house`, and `blacksmith`. The current scale tiers SHALL be `small`, `medium`, and `large_lite`.

#### Scenario: The building library is generated with count 10
- **WHEN** the library generator emits ten entries per archetype
- **THEN** `small_house` entries SHALL use the `small` tier
- **AND** `medium_house` entries 1 through 7 SHALL use `medium`
- **AND** `medium_house` entries 8 through 10 SHALL use `large_lite`
- **AND** `blacksmith` entries 1 through 2 SHALL use `small`
- **AND** `blacksmith` entries 3 through 8 SHALL use `medium`
- **AND** `blacksmith` entries 9 through 10 SHALL use `large_lite`.

### Requirement: Massing coordinates follow the current convention
Generated building massing coordinates SHALL treat low z as the front, high z as the back, positive x as east, negative x as west, and `y=0` as the bottom of the foundation.

#### Scenario: A front-facing entrance is planned
- **WHEN** an archetype records its primary door
- **THEN** it SHALL use wall `front`
- **AND** the corresponding outward direction SHALL be north.

### Requirement: Protected cells survive later normal writes
The block grid SHALL treat cells tagged `PROTECTED` as non-overwritable by normal writes unless the write explicitly forces replacement.

#### Scenario: A later detail pass writes into a protected window
- **WHEN** a normal grid write targets an existing protected window cell
- **THEN** the grid write SHALL return false
- **AND** the protected window blockstate SHALL remain unchanged.

### Requirement: The pass order is stable
The current core pass order SHALL be `massing_pass`, `structure_pass`, `facade_detail_pass`, `roof_pass`, `roof_cleanup_pass`, `material_variation_pass`, `interior_furnishing_pass`, and `exterior_decoration_pass`.

#### Scenario: A building is generated for export
- **WHEN** all core passes complete
- **THEN** quality checking SHALL occur before resource export
- **AND** exported reports SHOULD record `quality_check_pass` and `resource_export_pass` after successful completion.

### Requirement: Facade planning avoids flat, corner-opening walls
Facade planning SHALL split walls into post-bounded bays, keep openings away from building corners, avoid occluded attached-wall intervals, and guarantee at least the style profile's minimum planned window count where possible.

#### Scenario: A facade plan places a window
- **WHEN** a window candidate is selected
- **THEN** its along-wall coordinate SHALL be at least two cells away from both wall ends
- **AND** it SHALL NOT overlap the door bay, a post position, or an occluded interval.
