# Courtyard Compound

## Purpose

This spec captures the current parcel-level compound implementations. A compound is a parcel-level structure made from generated sub-buildings plus perimeter, landscape, and circulation elements. The current compound families are the Chinese one-courtyard layout and the cultivation sect terraced axial layout.

## Requirements

### Requirement: A compound is a parcel layer above the building graph
The generator SHALL represent a courtyard compound as a `CompoundGraph` that owns parcel-level elements such as perimeter wall, water, planting, corridors, and path, plus building slots. Each building slot SHALL be realized by generating a sub-building through the existing per-building `MassingGraph` and pass pipeline.

#### Scenario: A compound is generated
- **WHEN** a courtyard compound is generated for a seed
- **THEN** the result SHALL include a `CompoundGraph` with parcel elements and building slots
- **AND** each building slot SHALL contain a generated sub-building produced by the per-building pipeline.

### Requirement: Chinese one-courtyard axial layout
A Chinese courtyard compound SHALL be laid out along a central north-south axis as a single courtyard. It SHALL place a `gate_house` at the south end of the axis, a `front_row` building behind the gate, two `side_wing` buildings on the east and west sides, and a `main_hall` at the north end, all enclosed by a four-sided `perimeter_wall`.

#### Scenario: The axial buildings are placed
- **WHEN** the one-courtyard layout is generated
- **THEN** the `gate_house` SHALL be on the central axis at the south edge
- **AND** the `main_hall` SHALL be on the central axis at the north edge
- **AND** exactly two `side_wing` buildings SHALL be placed, one east and one west of the inner courtyard
- **AND** a `perimeter_wall` SHALL enclose all buildings on four sides.

#### Scenario: The gate breaks the perimeter on the axis
- **WHEN** the perimeter wall is generated
- **THEN** the wall SHALL have a single gate opening where the `gate_house` meets the central axis.

### Requirement: Water and planting are structural layout elements
Water and planting SHALL occupy parcel cells and participate in layout. Corridors and the central path SHALL route around water and planting rather than overlapping them, and building footprints SHALL NOT overlap water or planting cells.

#### Scenario: A path routes around water
- **WHEN** a compound places a water feature and a central path
- **THEN** the path cells SHALL NOT overlap the water cells
- **AND** the path SHALL remain traversable from the gate to the main hall.

#### Scenario: Buildings do not overlap landscape
- **WHEN** building slots and landscape elements are placed
- **THEN** no building footprint SHALL overlap water or planting cells.

### Requirement: Corridors connect wings along the courtyard
A compound SHALL place corridors that connect the side-wing buildings toward the main hall along the inner courtyard, routing around water and planting.

#### Scenario: Corridors link the inner courtyard
- **WHEN** the inner courtyard is generated
- **THEN** a corridor SHALL connect each side wing toward the main hall
- **AND** corridor cells SHALL NOT overlap water or planting cells.

### Requirement: Compound variants are combinatorial
Compound variation SHALL be produced by independent variant axes combined per seed. The axes SHALL include courtyard size, water form, planting layout, roof grade, gate style, and symmetry mode. By default the symmetry mode SHALL allow mild asymmetry between east and west wings; strict mirror SHALL be available as one symmetry option.

#### Scenario: The library generates distinct compounds
- **WHEN** the compound library is generated with defaults
- **THEN** it SHALL emit six compound instances
- **AND** the instances SHALL differ in at least one variant axis from one another.

### Requirement: Compound resources are part of v0.5 mod acceptance prep
The Chinese courtyard compound library and cultivation sect compound library SHALL be generated, validated, packed into the v0.5 mod jar, and documented in the available command list before staged manual acceptance.

#### Scenario: A courtyard compound is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested for v0.5
- **THEN** `chinese_courtyard_001.nbt` through `chinese_courtyard_006.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage list`, `/myvillage place chinese_courtyard_001`, `/myvillage gallery`, and `/myvillage gallery original`
- **AND** the reviewer SHOULD place at least one compound in game to inspect axial layout, perimeter and gate, landscape, corridors, and the two-story main hall.

#### Scenario: A cultivation sect compound is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested for v0.5
- **THEN** `cultivation_sect_001.nbt` through `cultivation_sect_002.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage place cultivation_sect_001` and `/myvillage gallery cultivation`
- **AND** the reviewer SHOULD place at least one sect compound in game to inspect the terraced axis, gate, side buildings, circulation, and principal hall.

### Requirement: Cultivation sect terraced axial layout
The compound layer SHALL provide a `cultivation_sect` layout strategy that arranges sect-roster sub-buildings along a central axis at monumental scale with hierarchical terraced slots. The sect strategy SHALL reuse the existing `CompoundGraph` parcel machinery and per-building pass pipeline.

#### Scenario: A sect compound is generated
- **WHEN** a sect compound is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` whose building slots are filled by sect-roster sub-buildings
- **AND** the slots SHALL be arranged along a central axis from entrance to principal hall.

#### Scenario: The sect layout is distinct from the courtyard layout
- **WHEN** the sect layout strategy is selected
- **THEN** it SHALL be selected via the settlement group's layout binding
- **AND** it SHALL NOT be produced by the existing one-courtyard `chinese_courtyard` layout.

#### Scenario: A terraced sect compound connects levels
- **WHEN** a sect compound places building slots on more than one platform level
- **THEN** circulation SHALL connect lower entrance levels to higher hall levels
- **AND** no building footprint SHALL overlap water or planting cells.
