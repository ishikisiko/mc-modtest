## MODIFIED Requirements

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
