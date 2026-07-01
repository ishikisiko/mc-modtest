# Town Realization

## Purpose

This spec captures the runtime `/myvillage town` realizer and acceptance checks.

## Requirements

### Requirement: A command builds a town in the live world against terrain
The mod SHALL expose `/myvillage town [seed]`, planning and building a complete districted town up to an approximately 160×160 footprint at the player's location. Rather than refusing when chunks are not preloaded, the command SHALL acquire chunk-load tickets across the planned footprint before placement and release them afterward; if a region cannot be force-loaded or built, the command SHALL report the affected extent rather than silently skip it.

#### Scenario: Summoning a districted town
- **WHEN** an operator runs `/myvillage town` at a location
- **THEN** the mod SHALL force-load the planned footprint and build a town with enclosure, gates, a main-street spine, districts, street-aligned frontage, vertical core landmarks, and lived-in tissue
- **AND** if a region of the footprint cannot be force-loaded or built, the command SHALL report that extent rather than fail silently.

### Requirement: A coordinate command builds a town at an explicit site
The mod SHALL expose `/myvillage townat <seed> <x> <y> <z>`, planning and building the same districted town as `/myvillage town <seed>` but anchored at the explicit coordinate instead of the executing player's position. The command SHALL use the same terrain fitting, footprint force-loading, runtime fallback resolution, reporting, and deterministic seed-and-site behavior as the player-position command.

#### Scenario: RCON builds a town without a player
- **WHEN** an operator runs `/myvillage townat 20260618 512 80 0` from RCON or the server console
- **THEN** the mod SHALL build a districted cultivation town at that explicit site
- **AND** the command SHALL not require a `ServerPlayer`.

#### Scenario: Coordinate town mirrors player-command behavior
- **WHEN** `/myvillage townat <seed> <x> <y> <z>` and `/myvillage town <seed>` are run with equivalent anchor positions and terrain
- **THEN** both commands SHALL produce the same town plan, force-load the same footprint, and report the same placed/skipped/fallback-substitution counts.

#### Scenario: Coordinate town is deterministic by seed and site
- **WHEN** `/myvillage townat <seed> <x> <y> <z>` is run twice on equivalent terrain
- **THEN** the two generated towns SHALL be identical.

### Requirement: Parcels meet the ground via bounded site-fit
Each realized parcel SHALL meet terrain through a plinth, steps, or retaining course, and parcels above the configured slope limit SHALL be skipped rather than force-flattened. For frontage-district parcels, placement SHALL align the building's street-facing wall to the parcel frontage edge and butt adjacent buildings at shared gable walls; for all parcels, placement SHALL align the template ground layer to the parcel surface and provide continuous footprint support so buildings do not float over a one-block hollow.

#### Scenario: A parcel on a slope sits on the ground
- **WHEN** a parcel is realized on sloping terrain within the slope limit
- **THEN** it SHALL be joined to the ground by a plinth, steps, or retaining course
- **AND** it SHALL neither float above nor be buried in the surrounding ground
- **AND** no one-block air gap SHALL remain under the building footprint.

#### Scenario: A frontage run sits as a continuous wall
- **WHEN** consecutive frontage parcels within the slope limit are realized along one street
- **THEN** their buildings SHALL share gable walls and align to the street frontage edge
- **AND** no per-building plinth ring SHALL separate them from the street.

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

### Requirement: A realized town passes district, frontage, and skyline validation
The town-level validator SHALL, in addition to the existing structural invariants, confirm that the plan is partitioned into the required districts, that frontage districts present continuous street-aligned party-wall rows rather than centered-lot gaps, and that the civic core meets the skyline tall-volume minimum.

#### Scenario: A valid districted town passes and a flat one fails
- **WHEN** the town validator runs on a generated districted town
- **THEN** it SHALL pass when the district, frontage, and skyline invariants hold alongside the structural invariants
- **AND** it SHALL fail and report the offending invariant on a town missing districts, presenting centered-lot frontage, or lacking core vertical relief.
