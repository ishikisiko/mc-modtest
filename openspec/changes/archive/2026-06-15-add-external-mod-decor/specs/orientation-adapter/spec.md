## ADDED Requirements

### Requirement: A family-aware orientation adapter resolves blockstates
The generator SHALL provide an orientation adapter that, given a block family, a cell role, and an orientation, returns a fully-propertied blockstate string. Each registered family SHALL declare its own blockstate property grammar so that placement sets the correct properties for that family rather than assuming vanilla stair/slab grammar. Vanilla stair and slab grammar SHALL be expressed as registered families of this adapter, preserving the behavior of the existing `stair_state` / `slab_state` helpers.

#### Scenario: Resolving an orientation for the vanilla stair family
- **WHEN** the adapter is asked for the stair family at a given facing and half
- **THEN** it SHALL return a blockstate equivalent to the previous `stair_state` output (`facing`, `half`, `shape`, `waterlogged` set)
- **AND** existing roof/eave generation that used `stair_state` SHALL place identical blocks.

#### Scenario: Resolving an orientation for a family with non-vanilla grammar
- **WHEN** the adapter resolves a family whose grammar differs from vanilla stairs (e.g. it declares `bottom` and `slanted` instead of `half` and `shape`)
- **THEN** the returned blockstate SHALL set that family's own properties for the requested orientation and cell role
- **AND** it SHALL NOT emit vanilla-only properties that the family does not declare.

### Requirement: The awning family orients as a slanted eave
The orientation adapter SHALL implement the `supplementaries:awning` family as its first non-vanilla family, mapping a roof-edge / eave cell role and facing to the awning's `facing`, `bottom`, and `slanted` properties so the canopy slopes outward over the wall below it rather than sitting flat or facing the wrong way.

#### Scenario: An eave edge places a slanted awning
- **WHEN** an eave or canopy cell requests the awning family facing outward from a wall
- **THEN** the resolved blockstate SHALL set `facing` to the outward direction
- **AND** it SHALL set `slanted=true` for a sloped eave edge
- **AND** the `bottom` property SHALL be set consistent with the awning's vertical position in the eave.

#### Scenario: Awning facing follows the wall it overhangs
- **WHEN** the same eave is generated along two different walls
- **THEN** the awning `facing` for each wall SHALL be the outward facing of that wall
- **AND** awnings on opposite walls SHALL receive opposite facings.

### Requirement: A missing or unregistered family fails loudly, never misplaces
When the adapter is asked for a family it does not have a registered grammar for, it SHALL raise a clear error naming the family rather than emitting a blockstate with guessed or vanilla-default properties.

#### Scenario: Unregistered family is requested
- **WHEN** the adapter is asked to orient a family that has no registered grammar
- **THEN** it SHALL raise an error naming the requested family
- **AND** it SHALL NOT return a partially-propertied or default blockstate.
