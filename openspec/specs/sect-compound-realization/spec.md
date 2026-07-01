# Sect Compound Realization

## Purpose

This spec captures the live-world realization of a sect compound: the `/myvillage sect` command that builds the planned terraced compound against terrain, terrace retaining that meets the ground, runtime palette resolution through the mod-fallback resolver, and a validator that enforces the compound invariants.

## Requirements

### Requirement: A command builds a sect compound in the live world against terrain

The mod SHALL expose `/myvillage sect [seed]`, planning and building a complete terraced sect compound at the player's location. The realizer SHALL acquire chunk-load tickets across the planned footprint before placement and release them afterward; if a region cannot be force-loaded or built, the command SHALL report the affected extent rather than silently skipping it. The build SHALL realize the gate-to-summit ritual axis, the terraces with their slotted volumes, the covered galleries, and the on-axis stairs, and — when the seed selects it — the detached-spire flying-bridge feature.

#### Scenario: Summoning a sect compound

- **WHEN** an operator runs `/myvillage sect` at a location
- **THEN** the mod SHALL force-load the planned footprint and build a sect compound with a mountain gate, ascending terraces, slotted volumes graded by terrace level, covered galleries, on-axis stairs, and a cliff-backed principal hall
- **AND** if a region of the footprint cannot be force-loaded or built, the command SHALL report that extent rather than fail silently.

#### Scenario: Same seed rebuilds the same compound

- **WHEN** `/myvillage sect <seed>` is run twice on equivalent terrain
- **THEN** the two compounds SHALL be structurally identical in terrace layout, slot assignment, axis, galleries, and feature selection.

### Requirement: A coordinate command builds a live-terrain sect compound

The mod SHALL expose `/myvillage sectat <seed> <x> <y> <z>`, planning and building the same live-terrain terraced sect compound as `/myvillage sect <seed>` but anchored at the explicit coordinate instead of the executing player's position. The command SHALL use the same force-loading, terrace realization, runtime fallback resolution, reporting, and deterministic seed-and-site behavior as the player-position command.

#### Scenario: RCON builds a sect without a player

- **WHEN** an operator runs `/myvillage sectat 20260618 -512 80 0` from RCON or the server console
- **THEN** the mod SHALL build a live-terrain terraced sect compound at that explicit site
- **AND** the command SHALL not require a `ServerPlayer`.

#### Scenario: Coordinate sect mirrors player-command behavior

- **WHEN** `/myvillage sectat <seed> <x> <y> <z>` and `/myvillage sect <seed>` are run with equivalent anchor positions and terrain
- **THEN** both commands SHALL produce the same terrace plan, force-load the same footprint, realize the same volumes/galleries/stairs, and report the same placed/skipped/fallback-substitution counts.

### Requirement: Terraces meet the ground through carved retaining, not floating slabs

Realizing a terrace SHALL join it to terrain through a retaining course and an on-axis stair flight to the terrace below, so terraces step the slope rather than floating above a hollow or being buried. Where the planned terrace exceeds the buildable slope, the realizer SHALL carve and retain the platform rather than leave volumes floating, and SHALL report any extent it cannot retain. No one-block air gap SHALL remain beneath a terrace platform or a slotted volume's footprint.

#### Scenario: A terrace steps the slope

- **WHEN** a terrace is realized on sloping terrain
- **THEN** it SHALL be joined to the terrace below by a retaining course and an on-axis stair flight
- **AND** no one-block air gap SHALL remain under the terrace platform or under any volume placed on it.

#### Scenario: The summit hall backs solid ground

- **WHEN** the summit terrace declares a cliff-back edge
- **THEN** the principal hall SHALL be realized against solid ground at that edge, not floating over a drop.

### Requirement: Runtime sect placement resolves palette ids through the mod-fallback resolver

Runtime template placement for `/myvillage sect` SHALL route every structure-palette block id through the runtime mod-fallback resolver before the block reaches the world, so a palette id absent from the live registry is placed as its vanilla fallback rather than as air. Registry-present ids SHALL remain unchanged, and a template whose palette names only registry-present ids SHALL place identically to placement without the resolver.

#### Scenario: A sect is built without optional decor mods

- **WHEN** `/myvillage sect` places a piece whose palette contains an absent optional-mod block id
- **THEN** the realizer SHALL load the template through the fallback-patching helper
- **AND** the fallback block state SHALL be placed instead of air.

#### Scenario: A vanilla-only piece is unaffected

- **WHEN** placement realizes a piece whose palette names only registry-present ids
- **THEN** the placed result SHALL be identical to placement without the resolver.

### Requirement: A validator enforces sect compound invariants

Realization SHALL be checked by a validator that confirms: the ritual axis is ordered and traversable from the gate terrace to the summit; importance tiers are non-decreasing up the terraces with the principal hall and scripture pagoda at the top tiers; every covered gallery and any present flying bridge has both endpoints resting on the volumes/terraces it joins; and the build is reproducible for a fixed seed. The validator SHALL emit a report rather than failing silently.

#### Scenario: Invariants are reported

- **WHEN** a sect compound is realized and validated
- **THEN** the validator SHALL confirm axis ordering and traversability, importance-by-terrace, and gallery/bridge endpoint anchoring
- **AND** SHALL emit a report recording any violated invariant and the affected extent.

### Requirement: The compound realizer places onto worldgen-derived terrain

The sect compound realizer SHALL be reusable by the worldgen path: given a terrace profile and the mountain derived from it, the realizer SHALL place the compound's volumes, galleries, stairs, and the flying-bridge feature onto the derived terrain using the same deterministic geometry as the `/myvillage sect [seed]` on-the-spot build. The existing on-the-spot command behavior SHALL be preserved unchanged when no derived terrain is supplied.

#### Scenario: The same realizer serves command and worldgen

- **WHEN** the worldgen path supplies a terrace profile and its derived mountain
- **THEN** the compound realizer SHALL place the same volumes, galleries, stairs, and feature it would for `/myvillage sect [seed]`, sat on the derived terrain
- **AND** the on-the-spot `/myvillage sect [seed]` build SHALL remain unchanged when no derived terrain is supplied.
