# Sect Worldgen Structure

## Purpose

This spec captures the registration of a custom sect `Structure` that sites a terraced sect compound during chunk generation: it is biome-gated to high-relief mountain biomes, sparsely spaced with a minimum separation as a rare regional landmark, discoverable via `/locate`, and supported by a force-generate command mode that lets an operator build a worldgen-style sect at a chosen location with a chosen detached-spire variant.

See also (narrative): [docs/ai-kb/07_neoforge_worldgen.md](../../../docs/ai-kb/07_neoforge_worldgen.md).

## Requirements

### Requirement: A custom sect structure is sited during world generation

The mod SHALL register a custom sect `Structure` (with its own `StructureType`) that sites a terraced sect compound during chunk generation, without force-loading the surrounding area. Siting SHALL be reproducible for a fixed world seed: the same seed SHALL place sects at the same locations. The structure SHALL bake into the generated chunks so there is no deferred build that pops in after a player approaches.

Baking a sect into the world SHALL NOT stall chunk loading. The per-chunk generation step for a chunk overlapping a sect SHALL do work bounded by that chunk's own area, NOT by the whole sect footprint: generating the chunks of a sect's neighborhood SHALL be bounded overall by the footprint built once, not by the footprint built once per overlapping chunk. Approaching, walking into, or teleporting near a sited sect SHALL continue to load new chunks without freezing.

The realized worldgen sect SHALL remain identical, for a fixed world seed and site, to the sect produced by the force-generate command: the per-chunk slices SHALL join seamlessly, with a volume that straddles a chunk boundary placed consistently in both chunks.

The derived mountain, terraces, and the buildings resting on them SHALL share a single absolute elevation frame: the terrain the sect generates SHALL sit at the same heights as the compound it supports, with no vertical offset that floats the terrain above or below the buildings.

#### Scenario: A sect generates with the world

- **WHEN** a world is generated and a chunk falls within a sited sect
- **THEN** the sect compound and its derived mountain SHALL be written as part of chunk generation without force-loading
- **AND** no build SHALL pop in after the chunk is already generated and a player approaches.

#### Scenario: Approaching a sect does not stall chunk loading

- **WHEN** a player approaches, walks into, or teleports near a sited sect
- **THEN** new chunks SHALL continue to load without freezing
- **AND** the work to generate each chunk overlapping the sect SHALL be bounded by that chunk's area, not by the whole sect footprint.

#### Scenario: Worldgen slices join seamlessly

- **WHEN** a sect spans multiple chunks and a volume straddles a chunk boundary
- **THEN** the volume SHALL be placed consistently across both chunks (same variant and orientation), matching the force-generated sect for the same seed and site.

#### Scenario: Terrain and compound share one elevation frame

- **WHEN** a sect is generated and its derived mountain, terraces, stairs, retaining faces, cliff-back, galleries, cloud-sea, and the buildings are written
- **THEN** they SHALL all be placed in the same absolute world-Y frame
- **AND** the terrain SHALL NOT be offset vertically away from the buildings it supports (no floating terrain mass above the compound).

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
