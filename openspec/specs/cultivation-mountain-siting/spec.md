# Cultivation Mountain Siting

## Purpose

This spec captures cultivation settlement siting, structural links, ritual axes, and terrace hierarchy that span the compound and town-planning layers.

## Requirements

### Requirement: Covered-gallery and flying-bridge links
The compound layer SHALL provide covered-gallery (廊) and flying-bridge (飞桥/廊桥) link elements that connect cultivation volumes as first-class circulation/structure, each with its own footprint and recorded endpoints, not as decoration patches. A link SHALL connect two volumes, or a volume and a terrace, so that the joined volumes are reachable through the link.

#### Scenario: A gallery connects two pavilions
- **WHEN** a sect compound places a covered gallery between two adjacent volumes
- **THEN** the gallery SHALL be recorded as a circulation/structure element with both endpoints
- **AND** the two volumes SHALL be reachable through the gallery.

#### Scenario: A flying bridge spans the axis
- **WHEN** a flying bridge is placed across the central axis or over a gap
- **THEN** it SHALL be recorded as a link element, not a decoration patch
- **AND** validation SHALL confirm its endpoints rest on the volumes or terraces it joins.

### Requirement: Compound siting context
A cultivation compound SHALL declare a siting context describing how it sits in terrain, at least the dimensions mountain-slope, cliff-back, water-front, and cloud-sea, recorded in compound metadata and emitted by export for in-game placement. The generator SHALL compose against the context, for example by placing the principal hall against the cliff and allowing a pavilion to cantilever over water or void.

#### Scenario: A sect compound declares its siting
- **WHEN** a sect compound is generated
- **THEN** its metadata SHALL include a siting context with mountain/cliff/water/cloud dimensions
- **AND** export SHALL emit the context and the relative terrace levels.

#### Scenario: The rear hall backs the cliff
- **WHEN** a compound's siting context marks a cliff at the rear
- **THEN** the principal hall SHALL be placed against the cliff-back edge of the axis.

### Requirement: Settlement ritual axis
A cultivation settlement SHALL compose a central ritual axis. For the sect, the axis SHALL ascend the terraces from the mountain gate at the foot to the principal hall at the summit. For the cultivation town, the axis SHALL terminate at the shrine, fronted by a plaza and a 牌坊 gate, so the shrine anchors the town rather than being placed as a generic parcel.

#### Scenario: The town axis terminates at the shrine
- **WHEN** a cultivation town is generated
- **THEN** a central axis SHALL terminate at the `town_shrine`
- **AND** a plaza and a 牌坊 gate SHALL front the shrine along that axis.

#### Scenario: The sect axis ascends to the hall
- **WHEN** a sect compound is generated
- **THEN** the axis SHALL run from the mountain gate at the lowest terrace to the principal hall at the highest
- **AND** the gate, intermediate halls, and principal hall SHALL be ordered along that axis.

### Requirement: Importance graded by terrace level
In a terraced sect compound, a building slot's importance tier SHALL increase with its terrace level, so that summit volumes such as the principal hall and scripture pagoda receive a higher importance tier, and therefore taller massing and finer roof grade, than foot-level volumes such as the gate and utilitarian slots.

#### Scenario: Summit volumes outrank foot volumes
- **WHEN** slots are assigned across a multi-level sect compound
- **THEN** a slot on a higher terrace SHALL receive an importance tier at least as high as one on a lower terrace
- **AND** the principal hall and scripture pagoda SHALL receive the compound's highest importance tier.

### Requirement: The siting requirements bind to a realized terraced compound

The compound layer's terrace hierarchy, ritual axis, covered-gallery/flying-bridge links, and siting context SHALL be realized by a concrete sect-compound assembler, not only described. The assembler SHALL expose a default terrace skeleton (gate / disciple / assembly / scripture / summit, count parametric 4–6) and the geometry parameters terrace rise, terrace depth, terrace width taper, axis-stair width, and cliff-back height, so that downstream worldgen can derive terrain from the same parameters.

#### Scenario: The abstract siting is realized, not just specified

- **WHEN** the `cultivation_sect` group is built
- **THEN** the terrace hierarchy, ground-to-summit ritual axis, importance-by-terrace grading, covered galleries, and cliff-back placement SHALL be produced by the realized compound
- **AND** the compound SHALL expose its terrace skeleton and geometry parameters for downstream consumption.

#### Scenario: Links are realized circulation, not decoration

- **WHEN** the compound places a covered gallery or the flying-bridge feature
- **THEN** each SHALL be realized as a circulation/structure link with both endpoints resting on the volumes or terraces it joins
- **AND** SHALL NOT be emitted as a decoration patch.
