## ADDED Requirements

### Requirement: The mountain is derived from the terrace profile

The generator SHALL derive the sect's mountain from the compound's exported terrace profile rather than search for matching natural terrain. The terrace elevations and bounds SHALL be treated as the mountain's skeleton; the slopes beneath and between terraces SHALL be filled with seed-driven noise so the result reads as a natural mountain rather than a stepped cone. Derivation SHALL be deterministic for a fixed seed, and the realized terraces SHALL sit at the profile's planned elevations with no terrace floating or buried.

#### Scenario: Terraces define the mountain, not the reverse

- **WHEN** a sect is generated
- **THEN** the mountain heightfield SHALL be derived so each terrace rests at its planned elevation from the compound's terrace profile
- **AND** the slopes beneath and between terraces SHALL be noise-filled rather than left as bare steps
- **AND** the same seed SHALL produce the same mountain.

#### Scenario: No floating or buried terraces

- **WHEN** the derived mountain is realized under the compound
- **THEN** no terrace platform SHALL float above the derived surface
- **AND** no terrace SHALL be buried below it.

### Requirement: The man-made mountain blends into natural terrain via an outer skirt

The derived mountain SHALL grade into the surrounding natural heightmap through an outer blend skirt, so there is no abrupt cut-off where the man-made relief meets generated terrain. The skirt SHALL interpolate between the derived height and the natural height across a configured radius. The man-made body MAY be solid built stone (it is placed after base terrain and surface generation), and the absence of caves/ore inside it SHALL be acceptable.

#### Scenario: The mountain edge is not a cliff-cut

- **WHEN** the derived mountain meets surrounding natural terrain
- **THEN** an outer blend skirt SHALL interpolate between derived and natural heights across the configured radius
- **AND** no abrupt vertical seam SHALL remain at the man-made/natural boundary except where a cliff-back face is intended.

### Requirement: The summit backs a cliff face

Behind the summit terrace's cliff-back edge, the derivation SHALL produce a sheer cliff face (rather than a graded slope) so the principal hall backs solid rock and faces the drop.

#### Scenario: A sheer face rises behind the summit

- **WHEN** the mountain is derived for a compound whose summit declares a cliff-back edge
- **THEN** a sheer cliff face SHALL be produced at that edge
- **AND** the principal hall SHALL back solid rock at the face.

### Requirement: A manual cloud-sea surface is placed below the upper terraces

The generator SHALL place a horizontal cloud-sea (云海面) surface of translucent blocks at a configured Y between the gate and disciple terraces, so the upper terraces read as floating above cloud. The surface MAY be edged with powder-snow wisps clinging to terrace edges. This SHALL be an explicit placed-block surface; the generator SHALL NOT be required to implement volumetric fog rendering.

#### Scenario: A cloud sea sits under the upper terraces

- **WHEN** a sect's mountain is derived
- **THEN** a horizontal translucent cloud-sea surface SHALL be placed at the configured Y between the gate and disciple terraces
- **AND** the surface SHALL be realized as placed blocks (e.g. translucent glass, with optional powder-snow wisps), not as a volumetric-fog effect.

### Requirement: A solitary peak is raised under the detached-spire feature

When the compound selects the detached-spire flying-bridge feature, the generator SHALL raise a solitary peak (孤峰) under the detached volume, separated from the main mountain by a gap that the flying bridge spans, so the detached volume is reachable only across the bridge. In worldgen this feature SHALL appear randomly per site (per the compound's per-seed selection); a flat or unsupported gap SHALL NOT be left under the detached volume.

#### Scenario: The detached volume stands on its own spire

- **WHEN** a generated sect selects the detached-spire feature
- **THEN** a solitary peak SHALL be raised under the detached volume, separated from the main mountain by a gap
- **AND** the flying bridge SHALL span that gap with endpoints on the main compound and the detached volume
- **AND** the detached volume SHALL rest on the spire, not float over an unsupported gap.

#### Scenario: The feature appears randomly across sites

- **WHEN** many sects are generated across a world
- **THEN** the detached-spire feature SHALL be present on some sites and absent on others according to the per-seed selection
- **AND** when present it SHALL be one of the three defined variants.
