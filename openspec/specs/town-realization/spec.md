# Town Realization

## Purpose

This spec captures the runtime `/myvillage town` realizer and acceptance checks.

## Requirements

### Requirement: A command builds a town in the live world against terrain
The mod SHALL expose `/myvillage town [seed]`, planning and building a complete town at the player's location using loaded chunks only.

#### Scenario: Summoning a town
- **WHEN** an operator runs `/myvillage town` at a location with sufficient loaded chunks
- **THEN** the mod SHALL build a town with enclosure, gates, a main-street spine, parcels, streets, active frontage, and lived-in tissue
- **AND** if the planned footprint exceeds loaded chunks, the command SHALL refuse or clamp with a reported extent.

### Requirement: Parcels meet the ground via bounded site-fit
Each realized parcel SHALL meet terrain through a plinth, steps, or retaining course, and parcels above the configured slope limit SHALL be skipped rather than force-flattened. Runtime template placement SHALL align the template's ground layer to the parcel surface and provide continuous footprint support so placed buildings do not float over a one-block hollow.

#### Scenario: A parcel on a slope sits on the ground
- **WHEN** a parcel is realized on sloping terrain within the slope limit
- **THEN** it SHALL be joined to the ground by a plinth, steps, or retaining course
- **AND** it SHALL neither float above nor be buried in the surrounding ground
- **AND** no one-block air gap SHALL remain under the building footprint.

### Requirement: Runtime placement resolves optional-mod palette ids before template load
Runtime template placement SHALL route every structure-palette block id through the runtime mod-fallback resolver for the `/myvillage town` realizer and `/myvillage place` before the block reaches the world, so that a palette id absent from the live registry is placed as its vanilla fallback rather than the registry's `minecraft:air` default. Registry-present ids SHALL remain unchanged. For a template whose palette names only registry-present ids, placement output SHALL be identical to placement without the resolver.

#### Scenario: A town is generated without optional decor mods
- **WHEN** `/myvillage town` places a parcel template whose palette contains an absent optional-mod block id
- **THEN** the realizer SHALL load the template through the fallback-patching helper
- **AND** the fallback block state SHALL be placed instead of air.

#### Scenario: A town is generated with optional decor mods
- **WHEN** `/myvillage town` realizes parcels whose templates contain external-mod ids and those mods are installed
- **THEN** the authored external-mod blocks SHALL be placed unchanged.

#### Scenario: A template is placed without optional decor mods
- **WHEN** `/myvillage place` places a template whose palette contains an absent optional-mod block id
- **THEN** the place command SHALL load the template through the fallback-patching helper
- **AND** no one-block air hole SHALL appear where a mod block was authored.

#### Scenario: A vanilla-only template is unaffected
- **WHEN** placement realizes a template whose palette names only registry-present ids
- **THEN** the placed result SHALL be identical to placement without the resolver.

### Requirement: A realized town passes town-level structural validation
A town-level validator SHALL confirm the enclosure is closed, every parcel is reachable from the spine, every gate lies on the wall, and no building footprint overlaps a street cell.

#### Scenario: A valid town passes and a broken one fails
- **WHEN** the town validator runs on a generated town
- **THEN** it SHALL pass when the structural invariants hold
- **AND** it SHALL fail and report the offending invariant on a town that violates any invariant.

### Requirement: Town generation is deterministic per seed and site
Given the same seed and same site, the town generator SHALL produce the same town.

#### Scenario: Reproducible from seed and site
- **WHEN** `/myvillage town <seed>` is run twice on the same site
- **THEN** the two generated towns SHALL be identical.
