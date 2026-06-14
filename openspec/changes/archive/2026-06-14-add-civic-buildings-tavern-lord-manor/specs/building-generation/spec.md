## MODIFIED Requirements

### Requirement: Supported archetypes and tiers are explicit
The current default generated building-library archetypes SHALL be `small_house`, `medium_house`, `blacksmith`, `small_shop`, `medium_shop`, and `big_house`. The current default scale tiers SHALL include `small`, `medium`, `large_lite`, shop variant tiers, and big-house variant tiers. Chinese courtyard sub-building archetypes `main_hall`, `side_wing`, `front_row`, and `gate_house` SHALL be available to the compound generator rather than emitted by the default medieval building-library loop. Civic archetypes `tavern` and `lord_manor` SHALL be available to the civic library generator rather than emitted by the default medieval building-library loop.

#### Scenario: The building library is generated with count 10
- **WHEN** the library generator emits ten entries per archetype
- **THEN** `small_house` entries SHALL use the `small` tier
- **AND** `medium_house` entries 1 through 7 SHALL use `medium`
- **AND** `medium_house` entries 8 through 10 SHALL use `large_lite`
- **AND** `blacksmith` entries 1 through 2 SHALL use `small`
- **AND** `blacksmith` entries 3 through 8 SHALL use `medium`
- **AND** `blacksmith` entries 9 through 10 SHALL use `large_lite`
- **AND** shop and big-house archetypes SHALL emit their configured variant counts.

#### Scenario: A Chinese courtyard sub-building is generated
- **WHEN** the compound generator requests `main_hall`, `side_wing`, `front_row`, or `gate_house`
- **THEN** the building pipeline SHALL generate it from a Chinese massing builder
- **AND** `main_hall` MAY use the multi-story capability
- **AND** these Chinese archetypes SHALL NOT reuse the `small_house` massing.

#### Scenario: A civic archetype is generated
- **WHEN** the civic library generator requests `tavern` or `lord_manor`
- **THEN** the building pipeline SHALL generate it from the civic massing builder
- **AND** the medieval building-library loop SHALL NOT emit civic archetypes
- **AND** the compound generator SHALL NOT emit civic archetypes.

#### Scenario: The new Western families are generated
- **WHEN** the library generator emits the shop and big-house families
- **THEN** `small_shop` SHALL produce at least five 1-story variants
- **AND** `medium_shop` SHALL produce at least five 2-story variants
- **AND** `big_house` SHALL produce at least five variants each with 2 or 3 stories.

### Requirement: The pass order is stable
The current core pass order SHALL be `massing_pass`, `structure_pass`, `mezzanine_floor_pass`, `floor_slab_pass`, `stair_pass`, `facade_detail_pass`, `roof_pass`, `roof_cleanup_pass`, `material_variation_pass`, `interior_furnishing_pass`, and `exterior_decoration_pass`. `mezzanine_floor_pass` SHALL run after `structure_pass` and before `floor_slab_pass`, and SHALL be a no-op for volumes without mezzanine metadata. `floor_slab_pass` and `stair_pass` SHALL run after `mezzanine_floor_pass` and before `facade_detail_pass`, and SHALL be no-ops for single-story volumes. `floor_slab_pass` SHALL skip stories flagged `mezzanine_story` because the mezzanine pass already placed their floor.

#### Scenario: A building is generated for export
- **WHEN** all core passes complete
- **THEN** quality checking SHALL occur before resource export
- **AND** exported reports SHOULD record `quality_check_pass` and `resource_export_pass` after successful completion.

#### Scenario: A multi-story building runs the new passes
- **WHEN** a volume with `stories > 1` is generated
- **THEN** `mezzanine_floor_pass` SHALL run first
- **AND** `floor_slab_pass` SHALL place the inter-story floor slabs after `mezzanine_floor_pass`
- **AND** `stair_pass` SHALL place the stairwell and aligned floor openings before `facade_detail_pass`.

#### Scenario: A single-story building skips the new passes
- **WHEN** a volume with `stories == 1` and no mezzanine meta is generated
- **THEN** `mezzanine_floor_pass`, `floor_slab_pass`, and `stair_pass` SHALL make no changes to the block grid.

#### Scenario: A mezzanine volume runs only the mezzanine pass for its mezzanine story
- **WHEN** a volume carries a `mezzanine` meta field
- **THEN** `mezzanine_floor_pass` SHALL place the partial-height slab over the configured half-plane
- **AND** `floor_slab_pass` SHALL skip the mezzanine story
- **AND** `floor_slab_pass` SHALL still place the full slabs for any non-mezzanine story boundaries.

## ADDED Requirements

### Requirement: Civic archetype classification
The civic archetype family SHALL be classified as civic/public buildings for the purposes of town generation. Civic archetypes SHALL NOT be classified as housing, functional-industry, or decorative. Each civic archetype SHALL declare the civic role blocks it depends on so that validators can confirm functional identity.

#### Scenario: A new civic archetype is proposed
- **WHEN** a contributor proposes a new civic archetype
- **THEN** the proposal SHALL identify the civic role blocks the archetype depends on
- **AND** it SHALL explain how the archetype serves broader town generation as a public/civic anchor.
