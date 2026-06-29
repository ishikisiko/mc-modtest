## ADDED Requirements

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
