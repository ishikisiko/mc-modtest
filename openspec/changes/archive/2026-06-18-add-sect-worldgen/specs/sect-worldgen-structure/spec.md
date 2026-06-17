## ADDED Requirements

### Requirement: A custom sect structure is sited during world generation

The mod SHALL register a custom sect `Structure` (with its own `StructureType`) that sites a terraced sect compound during chunk generation, without force-loading the surrounding area. Siting SHALL be reproducible for a fixed world seed: the same seed SHALL place sects at the same locations. The structure SHALL bake into the generated chunks so there is no deferred build that pops in after a player approaches.

#### Scenario: A sect generates with the world

- **WHEN** a world is generated and a chunk falls within a sited sect
- **THEN** the sect compound and its derived mountain SHALL be written as part of chunk generation without force-loading
- **AND** no build SHALL pop in after the chunk is already generated and a player approaches.

#### Scenario: Siting is world-seed reproducible

- **WHEN** two worlds are generated with the same seed
- **THEN** sects SHALL be sited at the same locations in both.

### Requirement: Sects are rare, biome-gated, and spaced as landmarks

The sect structure SHALL be gated to high-relief / mountainous biomes by biome tag and SHALL be placed sparsely with a minimum separation, so a sect reads as a rare regional landmark rather than a common feature. The structure SHALL NOT site in flat lowland biomes.

#### Scenario: A sect prefers mountains and avoids flats

- **WHEN** the generator evaluates candidate locations
- **THEN** it SHALL place sects only in biomes carrying the sect's mountain/high-relief tag
- **AND** it SHALL NOT place a sect in a flat lowland biome.

#### Scenario: Sects keep their distance

- **WHEN** two sects would generate near each other
- **THEN** the configured minimum separation SHALL be enforced so they do not crowd.

### Requirement: Worldgen sects are locatable

The mod SHALL register the sect structure so it is discoverable via `/locate`, so a rare structure players would otherwise never find can be located on demand.

#### Scenario: Locating a sect

- **WHEN** an operator runs `/locate` for the sect structure
- **THEN** the command SHALL report the nearest sited sect's location.

### Requirement: A sect can be force-generated with a chosen detached-spire variant

The sect command SHALL support a force-generate mode that builds a worldgen-style sect (with its derived mountain) at a chosen location regardless of the random siting roll, and SHALL accept selection of the detached-spire flying-bridge variant — one of the three variants, or none — so the feature can be reviewed and tested without waiting for a random world roll. Omitting the variant argument SHALL fall back to the per-seed selection.

#### Scenario: Forcing a specific variant

- **WHEN** an operator force-generates a sect and specifies a detached-spire variant
- **THEN** the generated sect SHALL build with exactly that variant
- **AND** specifying "none" SHALL build a sect without the detached-spire feature.

#### Scenario: Force-generate falls back to per-seed selection

- **WHEN** an operator force-generates a sect without specifying a variant
- **THEN** the sect SHALL use the per-seed variant selection from `sect-compound-layout`.
