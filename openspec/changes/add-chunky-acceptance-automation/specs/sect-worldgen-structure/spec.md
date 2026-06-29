## ADDED Requirements

### Requirement: A coordinate command force-generates a worldgen-style sect

The mod SHALL expose `/myvillage sectat worldgen <seed> <variant|none> <x> <y> <z>`, building a worldgen-style sect with its derived mountain at an explicit coordinate. The command SHALL mirror the existing `/myvillage sect worldgen <seed> [variant]` behavior, including derived mountain generation, detached-spire variant selection, runtime fallback resolution, force-loaded footprint, and reporting, but it SHALL not require a `ServerPlayer`.

#### Scenario: RCON force-generates a worldgen-style sect

- **WHEN** an operator runs `/myvillage sectat worldgen 20260618 pavilion_short_straight_east -512 80 0` from RCON or the server console
- **THEN** the mod SHALL build a worldgen-style sect with the derived mountain at that explicit site
- **AND** it SHALL force the specified detached-spire variant.

#### Scenario: Coordinate worldgen-style sect can force no detached feature

- **WHEN** an operator runs `/myvillage sectat worldgen 20260618 none -512 80 0`
- **THEN** the generated sect SHALL omit the detached-spire feature.

#### Scenario: Coordinate force-generation mirrors player-command behavior

- **WHEN** `/myvillage sectat worldgen <seed> <variant> <x> <y> <z>` and `/myvillage sect worldgen <seed> <variant>` are run with equivalent anchor positions and terrain
- **THEN** both commands SHALL produce the same derived mountain, terrace frame, volumes, galleries, cloud-sea, and detached-spire selection.
