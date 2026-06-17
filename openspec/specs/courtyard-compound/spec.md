# Courtyard Compound

## Purpose

This spec captures the current parcel-level compound implementations. A compound is a parcel-level structure made from generated sub-buildings plus perimeter, landscape, and circulation elements. The current compound families are the Chinese one-courtyard layout, the cultivation town courtyard-street block layout, and the cultivation sect terraced axial layout.

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

### Requirement: Compound resources are part of mod acceptance prep
The Chinese courtyard compound library, cultivation town building/block libraries, and cultivation sect building/compound libraries SHALL be generated, validated, packed into the current mod jar, and documented in the available command list before staged manual acceptance. The detailed acceptance-prep handoff (preview generation, HTTP server, changelog) is defined by the validation spec; the scenarios here record the compound-specific artifacts and what a reviewer inspects in game.

#### Scenario: A courtyard compound is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested
- **THEN** `chinese_courtyard_001.nbt` through `chinese_courtyard_006.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage list`, `/myvillage place chinese_courtyard_001`, `/myvillage gallery`, and `/myvillage gallery original`
- **AND** the reviewer SHOULD place at least one compound in game to inspect axial layout, perimeter and gate, landscape, corridors, and the two-story main hall.

#### Scenario: A cultivation town block is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested
- **THEN** `cultivation_town_001.nbt` through `cultivation_town_006.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage place cultivation_town_001` and `/myvillage gallery cultivation`
- **AND** the reviewer SHOULD place at least one town block in game to inspect continuous frontage, shared party walls, street and lane traversability, and gate orientation.

#### Scenario: A cultivation sect compound is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested
- **THEN** `cultivation_sect_001.nbt` through `cultivation_sect_002.nbt` SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** matching sect placement metadata SHALL be present under `src/main/resources/data/myvillage/settlement_meta/`
- **AND** the command documentation SHALL include `/myvillage place cultivation_sect_001` and `/myvillage gallery cultivation`
- **AND** the reviewer SHOULD place at least one sect compound in game to inspect the mountain terraces, gate, per-level courtyards, monumental stairs, covered-gallery/flying-bridge links, siting context, and summit hall/pagoda.

### Requirement: Small-courtyard unit layout
The compound layer SHALL provide a small-courtyard unit layout that produces a compact walled `CompoundGraph` reusing the existing parcel machinery and per-building pass pipeline. A small courtyard SHALL enclose two to four roster buildings around a single small 天井 with a four-sided `perimeter_wall` broken by exactly one gate, at a footprint smaller than the one-진 `chinese_courtyard` layout.

#### Scenario: A small courtyard is generated
- **WHEN** a small-courtyard unit is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` reusing `ParcelNode` and `BuildingSlot` parcel elements
- **AND** it SHALL enclose between two and four building slots around a single small 天井
- **AND** its `perimeter_wall` SHALL have exactly one gate opening.

#### Scenario: The small courtyard is more compact than the one-courtyard layout
- **WHEN** a small-courtyard unit and a one-진 `chinese_courtyard` compound are generated
- **THEN** the small courtyard's lot footprint SHALL be smaller than the one-courtyard layout's lot footprint.

#### Scenario: Small-courtyard buildings respect landscape and walls
- **WHEN** a small courtyard places its building slots, 天井, and perimeter wall
- **THEN** no building footprint SHALL overlap the 天井 landscape cells or the wall cells.

### Requirement: Cultivation town courtyard-street block layout
The compound layer SHALL provide a `courtyard_street_block` layout strategy that tiles small courtyards along streets and optional lanes into one flattened `CompoundGraph`. The block SHALL arrange courtyards on a row/column grid separated by `street` and `lane` parcel cells, and each courtyard's building slots SHALL be realized through the existing per-building pass pipeline. This layout MAY remain generated as a reusable parcel/review form even though the `cultivation_town` settlement group's primary runtime layout is `town_generation` (see settlement-group).

#### Scenario: A cultivation town block is generated
- **WHEN** a town block is generated
- **THEN** the result SHALL be a single `CompoundGraph` whose layout strategy is `courtyard_street_block`
- **AND** it SHALL contain at least two small courtyards
- **AND** each courtyard's building slots SHALL be filled by sub-buildings produced by the per-building pipeline
- **AND** it SHALL include `street` parcel cells and MAY include `lane` parcel cells
- **AND** no building, wall, tianjing, water, or planting cell SHALL overlap street or lane cells
- **AND** each gate SHALL be reachable from the street network.

### Requirement: Town blocks form continuous street frontage
Adjacent courtyards in a row SHALL share a single party wall on their common lot line so their 院墙 form continuous street frontage, while the outer edge of the block SHALL remain a continuous perimeter wall. Each courtyard's gate SHALL open onto the nearest street.

#### Scenario: Neighboring courtyards share one wall
- **WHEN** two courtyards are tiled adjacently in a row
- **THEN** their shared lot line SHALL contain exactly one wall thickness (no doubled wall, no gap)
- **AND** the outer block edge SHALL remain a continuous wall.

#### Scenario: Gates face the street
- **WHEN** a courtyard is placed in a block
- **THEN** its gate opening SHALL be on the side facing the nearest street.

### Requirement: Town blocks are combinatorial variants
Town-block variation SHALL be produced by independent variant axes combined per seed, including block shape (rows × courtyards per row), street width, lane presence, and corner-frontage. The default library SHALL emit a set of distinct town blocks differing in at least one axis.

#### Scenario: The library generates distinct town blocks
- **WHEN** the town-block library is generated with defaults
- **THEN** it SHALL emit multiple town-block instances
- **AND** each instance SHALL differ from the others in at least one variant axis.

### Requirement: Cultivation sect terraced axial layout
The compound layer SHALL provide a `cultivation_sect` layout strategy that arranges sect-roster sub-buildings along a central axis at monumental scale across three or more stacked terrace levels. The axis SHALL ascend from the mountain gate on the lowest terrace to the principal hall on the highest terrace. Each level SHALL form its own courtyard with building slots, and adjacent levels SHALL be connected by monumental stairway circulation. The sect strategy SHALL reuse the existing `CompoundGraph` parcel machinery and per-building pass pipeline. Importance grading by terrace level, terrain siting context, and covered-gallery/flying-bridge link circulation are specified by the cultivation-mountain-siting capability and are not restated here.

#### Scenario: A sect compound is generated
- **WHEN** a sect compound is generated for a seed
- **THEN** the result SHALL be a `CompoundGraph` whose building slots are filled by sect-roster sub-buildings
- **AND** the slots SHALL be arranged along a central axis from the gate at the lowest level to the principal hall at the highest
- **AND** the slots SHALL be distributed across three or more terrace levels
- **AND** each terrace level SHALL have a courtyard parcel node.

#### Scenario: The sect layout is distinct from the courtyard layout
- **WHEN** the sect layout strategy is selected
- **THEN** it SHALL be selected via the settlement group's layout binding
- **AND** it SHALL NOT be produced by the existing one-courtyard `chinese_courtyard` layout.

#### Scenario: A terraced sect compound connects levels
- **WHEN** a sect compound places building slots on more than one platform level
- **THEN** monumental stairway circulation SHALL connect each lower level to the next higher level
- **AND** no building footprint SHALL overlap water or planting cells.
