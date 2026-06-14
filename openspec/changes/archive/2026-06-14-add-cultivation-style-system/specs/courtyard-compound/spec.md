## ADDED Requirements

### Requirement: Sect terraced axial layout strategy
The compound layer SHALL provide a sect layout strategy that arranges sect-roster sub-buildings along a central axis at a monumental scale with hierarchical, terraced building slots. The sect strategy SHALL reuse the existing `CompoundGraph` parcel machinery (perimeter, landscape, circulation, building slots) and the per-building pass pipeline.

#### Scenario: A sect compound is generated
- **WHEN** a sect compound is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` whose building slots are filled by sect-roster sub-buildings
- **AND** the slots SHALL be arranged along a central axis with a hierarchical ordering from entrance to principal hall.

#### Scenario: The sect layout is distinct from the courtyard layout
- **WHEN** the sect layout strategy is selected
- **THEN** it SHALL be selected via the settlement group's layout binding
- **AND** it SHALL NOT be produced by the existing one-courtyard `chinese_courtyard` layout.

### Requirement: Sect compounds may use terraced platform levels
The sect layout MAY place building slots on stepped platform levels (terraces) so that the principal hall sits on a higher base than entrance buildings. Where terraces are used, circulation SHALL connect levels and building footprints SHALL NOT overlap landscape cells.

#### Scenario: A terraced sect compound connects levels
- **WHEN** a sect compound places building slots on more than one platform level
- **THEN** circulation SHALL connect the lower entrance level to the higher hall level
- **AND** no building footprint SHALL overlap water or planting cells.
