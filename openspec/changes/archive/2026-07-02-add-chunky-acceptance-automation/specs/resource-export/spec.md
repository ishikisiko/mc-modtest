## ADDED Requirements

### Requirement: Coordinate debug commands place templates and galleries

The NeoForge mod SHALL expose coordinate-addressable debug commands for staged automated acceptance: `/myvillage placeat <structure_id> <x> <y> <z>` and `/myvillage galleryat <all|original|cultivation> <x> <y> <z>`. These commands SHALL mirror the existing `/myvillage place` and `/myvillage gallery` behavior, including grouping, spacing, structure filtering, runtime fallback resolution, and the one-block downward Y offset for generated non-test structures.

#### Scenario: A template is placed at explicit coordinates

- **WHEN** an operator runs `/myvillage placeat chinese_mansion_001 100 80 200`
- **THEN** the command SHALL place `myvillage:chinese_mansion_001` using the same placement behavior as `/myvillage place chinese_mansion_001`
- **AND** because it is a generated non-test structure, the effective template origin SHALL use the same one-block downward Y offset.

#### Scenario: A cultivation gallery is placed at explicit coordinates

- **WHEN** an operator runs `/myvillage galleryat cultivation 0 80 0`
- **THEN** the command SHALL place the same cultivation columns and spacing as `/myvillage gallery cultivation`
- **AND** it SHALL not require a `ServerPlayer`.

#### Scenario: Coordinate placement resolves optional-mod palette ids

- **WHEN** `/myvillage placeat` or `/myvillage galleryat` places a template whose palette contains optional-mod block ids
- **THEN** placement SHALL route the template through the same runtime mod-fallback resolver used by the existing player-position commands.
