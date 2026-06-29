## ADDED Requirements

### Requirement: A coordinate command builds a live-terrain sect compound

The mod SHALL expose `/myvillage sectat <seed> <x> <y> <z>`, planning and building the same live-terrain terraced sect compound as `/myvillage sect <seed>` but anchored at the explicit coordinate instead of the executing player's position. The command SHALL use the same force-loading, terrace realization, runtime fallback resolution, reporting, and deterministic seed-and-site behavior as the player-position command.

#### Scenario: RCON builds a sect without a player

- **WHEN** an operator runs `/myvillage sectat 20260618 -512 80 0` from RCON or the server console
- **THEN** the mod SHALL build a live-terrain terraced sect compound at that explicit site
- **AND** the command SHALL not require a `ServerPlayer`.

#### Scenario: Coordinate sect mirrors player-command behavior

- **WHEN** `/myvillage sectat <seed> <x> <y> <z>` and `/myvillage sect <seed>` are run with equivalent anchor positions and terrain
- **THEN** both commands SHALL produce the same terrace plan, force-load the same footprint, realize the same volumes/galleries/stairs, and report the same placed/skipped/fallback-substitution counts.
