## ADDED Requirements

### Requirement: A compound is a parcel layer above the building graph
The generator SHALL represent a courtyard compound as a `CompoundGraph` that owns parcel-level elements (perimeter wall, water, planting, corridors, path) and a set of building slots. Each building slot SHALL be realized by generating a sub-building through the existing per-building `MassingGraph` and pass pipeline. The compound layer SHALL place sub-buildings; it SHALL NOT replace per-building generation.

#### Scenario: A compound is generated
- **WHEN** a courtyard compound is generated for a seed
- **THEN** the result SHALL include a `CompoundGraph` with parcel elements and building slots
- **AND** each building slot SHALL contain a generated sub-building produced by the per-building pipeline.

### Requirement: One-courtyard axial layout
A compound SHALL be laid out along a central north–south axis as a single courtyard (一进). It SHALL place a `gate_house` at the south end of the axis, a `front_row` building behind the gate, two `side_wing` buildings on the east and west sides, and a `main_hall` at the north end, all enclosed by a four-sided `perimeter_wall`.

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
Water (`water_feature`) and planting (`planting`) SHALL occupy parcel cells and participate in layout. Corridors (`corridor`) and the central `path` SHALL route around water and planting rather than overlapping them, and building footprints SHALL NOT overlap water or planting cells.

#### Scenario: A path routes around water
- **WHEN** a compound places a `water_feature` and a central `path`
- **THEN** the path cells SHALL NOT overlap the water cells
- **AND** the path SHALL remain traversable from the gate to the main hall.

#### Scenario: Buildings do not overlap landscape
- **WHEN** building slots and landscape elements are placed
- **THEN** no building footprint SHALL overlap a `water_feature` or `planting` cell.

### Requirement: Corridors connect wings along the courtyard
A compound SHALL place corridors (`corridor`, 廊) that connect the `side_wing` buildings toward the `main_hall` along the inner courtyard, routing around water and planting.

#### Scenario: Corridors link the inner courtyard
- **WHEN** the inner courtyard is generated
- **THEN** a `corridor` SHALL connect each `side_wing` toward the `main_hall`
- **AND** corridor cells SHALL NOT overlap water or planting cells.

### Requirement: Compound variants are combinatorial
Compound variation SHALL be produced by independent variant axes combined per seed, not by a fixed list of hand-authored layouts. The axes SHALL include: courtyard size (small, medium, large), water form (pool, channel, and a third form), planting layout (three options), roof grade (硬山, 悬山, 歇山), gate style (three options), and symmetry mode. By default the symmetry mode SHALL allow mild asymmetry between the east and west wings; strict mirror SHALL be available as one symmetry option.

#### Scenario: Two seeds produce different combinations
- **WHEN** two different seeds generate compounds
- **THEN** each compound SHALL select one option per variant axis
- **AND** differing seeds MAY yield different courtyard size, water form, planting layout, roof grade, gate style, or symmetry mode.

#### Scenario: Strict mirror is a selectable symmetry option
- **WHEN** the symmetry mode resolves to strict mirror
- **THEN** the east and west `side_wing` buildings SHALL be mirror images across the central axis.

#### Scenario: Default symmetry allows mild asymmetry
- **WHEN** the symmetry mode resolves to the default
- **THEN** the east and west `side_wing` buildings MAY differ within the allowed asymmetry while keeping the axial layout.

### Requirement: The compound library samples distinct instances
The compound library generation SHALL emit several distinct compound instances by sampling the variant axes. The default sample count SHALL be six distinct combinations.

#### Scenario: The library generates compounds
- **WHEN** the compound library is generated with defaults
- **THEN** it SHALL emit six compound instances
- **AND** the instances SHALL differ in at least one variant axis from one another.
