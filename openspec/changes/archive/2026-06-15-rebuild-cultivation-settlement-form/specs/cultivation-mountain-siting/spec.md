## ADDED Requirements

### Requirement: Covered-gallery and flying-bridge links
The compound layer SHALL provide covered-gallery (廊) and flying-bridge (飞桥/廊桥) link elements that connect cultivation volumes as first-class circulation/structure, each with its own footprint and recorded endpoints, not as decoration patches. A link SHALL connect two volumes (or a volume and a terrace) so that the joined volumes are reachable through the link.

#### Scenario: A gallery connects two pavilions
- **WHEN** a sect compound places a covered gallery between two adjacent volumes
- **THEN** the gallery SHALL be recorded as a circulation/structure element with both endpoints
- **AND** the two volumes SHALL be reachable through the gallery.

#### Scenario: A flying bridge spans the axis
- **WHEN** a flying bridge is placed across the central axis or over a gap
- **THEN** it SHALL be recorded as a link element, not a decoration patch
- **AND** validation SHALL confirm its endpoints rest on the volumes/terraces it joins.

### Requirement: Compound siting context
A cultivation compound SHALL declare a siting context describing how it sits in terrain — at least the dimensions mountain-slope, cliff-back, water-front, and cloud-sea — recorded in compound metadata and emitted by export for in-game placement. The generator SHALL compose against the context (for example, the principal hall backs the cliff and a pavilion may cantilever over water or void).

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
In a terraced sect compound, a building slot's importance tier SHALL increase with its terrace level, so that summit volumes (principal hall, scripture pagoda) receive a higher importance tier — and therefore taller massing and finer roof grade — than foot-level volumes (gate, utilitarian slots).

#### Scenario: Summit volumes outrank foot volumes
- **WHEN** slots are assigned across a multi-level sect compound
- **THEN** a slot on a higher terrace SHALL receive an importance tier at least as high as one on a lower terrace
- **AND** the principal hall and scripture pagoda SHALL receive the compound's highest importance tier.
