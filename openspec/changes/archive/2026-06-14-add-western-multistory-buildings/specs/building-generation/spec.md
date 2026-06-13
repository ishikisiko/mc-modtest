## ADDED Requirements

### Requirement: Shop archetype family
The generator SHALL support a `shop` archetype family, classified as a functional/commercial archetype family that serves town generation by providing commercial buildings distinct from housing. The family SHALL provide two tiers: `small_shop` (1 story, compact storefront) and `medium_shop` (2 stories, ground-floor storefront with upstairs living). The `medium_shop` SHALL use the multi-story massing capability. A shop node MAY carry an optional `industry` meta field; this change SHALL NOT implement any industry-specific behavior.

#### Scenario: A small shop is generated
- **WHEN** the `small_shop` tier is generated
- **THEN** the massing graph SHALL be a single story with a storefront feature on the front wall (a wide shop opening and/or signage)
- **AND** block placement SHALL occur in later passes.

#### Scenario: A medium shop is generated
- **WHEN** the `medium_shop` tier is generated
- **THEN** the main volume SHALL have `stories == 2`
- **AND** the ground story SHALL carry a storefront feature and the upper story SHALL carry a residential-style window band.

#### Scenario: The industry field carries no behavior
- **WHEN** a shop node is created
- **THEN** an `industry` meta field MAY be present
- **AND** generation output SHALL NOT depend on the value of `industry` in this change.

### Requirement: Big house archetype family
The generator SHALL support a `big_house` archetype family, classified as a housing archetype family that serves town generation by providing larger residences above `small_house` and `medium_house`. A `big_house` SHALL use the multi-story massing capability with 2 to 3 stories chosen per seed.

#### Scenario: A big house is generated
- **WHEN** the `big_house` archetype is generated
- **THEN** the main volume SHALL have `stories` between 2 and 3 inclusive
- **AND** the massing graph SHALL include floor slabs and a stairwell connecting the stories.

### Requirement: New families provide five strongly distinct variants
The `shop` and `big_house` families SHALL each provide at least five generated variants per tier. Variant distinction SHALL be clearly stronger than `small_house` variation. For `shop`, variants SHALL differ on a form axis (story count, roof style, signage, awning/eave, footprint, or entrance). For `big_house`, variants SHALL differ structurally (massing, story count, or roof differences) rather than only by exterior decoration patches.

#### Scenario: The library generates five shop variants
- **WHEN** the library generator emits a shop tier
- **THEN** it SHALL produce at least five variants
- **AND** the variants SHALL differ on at least one form-axis attribute beyond decoration patches.

#### Scenario: The library generates five big house variants
- **WHEN** the library generator emits the `big_house` family
- **THEN** it SHALL produce at least five variants
- **AND** the variants SHALL exhibit structural differences (massing, story count, or roof) rather than only differing decoration patches.

## MODIFIED Requirements

### Requirement: Supported archetypes and tiers are explicit
The current supported generated building archetypes SHALL be `small_house`, `medium_house`, `blacksmith`, `shop`, and `big_house`. The current scale tiers SHALL be `small`, `medium`, and `large_lite`. The `shop` family SHALL be generated as the `small_shop` and `medium_shop` tiers, and the `big_house` family SHALL be generated as a 2–3 story housing family; these multi-story families use story count in addition to footprint-based tiers.

#### Scenario: The building library is generated with count 10
- **WHEN** the library generator emits ten entries per legacy archetype
- **THEN** `small_house` entries SHALL use the `small` tier
- **AND** `medium_house` entries 1 through 7 SHALL use `medium`
- **AND** `medium_house` entries 8 through 10 SHALL use `large_lite`
- **AND** `blacksmith` entries 1 through 2 SHALL use `small`
- **AND** `blacksmith` entries 3 through 8 SHALL use `medium`
- **AND** `blacksmith` entries 9 through 10 SHALL use `large_lite`.

#### Scenario: The new families are generated
- **WHEN** the library generator emits the `shop` and `big_house` families
- **THEN** `small_shop` SHALL produce at least five 1-story variants
- **AND** `medium_shop` SHALL produce at least five 2-story variants
- **AND** `big_house` SHALL produce at least five variants each with 2 or 3 stories.

### Requirement: The pass order is stable
The current core pass order SHALL be `massing_pass`, `structure_pass`, `floor_slab_pass`, `stair_pass`, `facade_detail_pass`, `roof_pass`, `roof_cleanup_pass`, `material_variation_pass`, `interior_furnishing_pass`, and `exterior_decoration_pass`. `floor_slab_pass` and `stair_pass` SHALL run after `structure_pass` and before `facade_detail_pass`, and SHALL be no-ops for single-story volumes.

#### Scenario: A building is generated for export
- **WHEN** all core passes complete
- **THEN** quality checking SHALL occur before resource export
- **AND** exported reports SHOULD record `quality_check_pass` and `resource_export_pass` after successful completion.

#### Scenario: A multi-story building runs the new passes
- **WHEN** a volume with `stories > 1` is generated
- **THEN** `floor_slab_pass` SHALL place the inter-story floor slabs after `structure_pass`
- **AND** `stair_pass` SHALL place the stairwell and aligned floor openings before `facade_detail_pass`.

#### Scenario: A single-story building skips the new passes
- **WHEN** a volume with `stories == 1` is generated
- **THEN** `floor_slab_pass` and `stair_pass` SHALL make no changes to the block grid.
