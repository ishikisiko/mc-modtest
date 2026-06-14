## ADDED Requirements

### Requirement: A town block tiles small courtyards along streets
The generator SHALL provide a `courtyard_street_block` layout strategy that composes multiple small walled courtyards into a single town-block `CompoundGraph`. The block SHALL arrange courtyards on a row/column grid separated by street and lane parcel cells, and each courtyard's building slots SHALL be realized through the existing per-building pass pipeline.

#### Scenario: A town block is generated
- **WHEN** a town block is generated for a seed
- **THEN** the result SHALL be a single `CompoundGraph` whose layout strategy is `courtyard_street_block`
- **AND** it SHALL contain at least two small courtyards
- **AND** each courtyard's building slots SHALL be filled by sub-buildings produced by the per-building pipeline.

### Requirement: Courtyards form continuous street frontage
Adjacent courtyards in a row SHALL share a single party wall on their common lot line so their 院墙 form continuous street frontage, while the outer edge of the block SHALL remain a continuous perimeter wall. Each courtyard's gate SHALL open onto the nearest street.

#### Scenario: Neighboring courtyards share one wall
- **WHEN** two courtyards are tiled adjacently in a row
- **THEN** their shared lot line SHALL contain exactly one wall thickness (no doubled wall, no gap)
- **AND** the outer block edge SHALL remain a continuous wall.

#### Scenario: Gates face the street
- **WHEN** a courtyard is placed in a block
- **THEN** its gate opening SHALL be on the side facing the nearest street.

### Requirement: Streets and lanes are traversable and non-overlapping
Street and lane cells SHALL be parcel layout elements that no courtyard building footprint, wall, or landscape cell overlaps. The streets and lanes SHALL remain traversable across the block.

#### Scenario: Buildings do not overlap circulation
- **WHEN** courtyards and streets/lanes are placed in a block
- **THEN** no courtyard building footprint, wall, or landscape cell SHALL overlap a street or lane cell.

#### Scenario: The block is traversable
- **WHEN** a town block is generated
- **THEN** every courtyard gate SHALL be reachable from the block's street network.

### Requirement: Town blocks are combinatorial variants
Town-block variation SHALL be produced by independent variant axes combined per seed, including block shape (rows × courtyards per row), street width, lane presence, and corner-frontage. The default library SHALL emit a set of distinct town blocks differing in at least one axis.

#### Scenario: The library generates distinct town blocks
- **WHEN** the town-block library is generated with defaults
- **THEN** it SHALL emit multiple town-block instances
- **AND** each instance SHALL differ from the others in at least one variant axis.

### Requirement: The town group uses the courtyard-street layout
The `cultivation_town` settlement group SHALL bind to the `courtyard_street_block` layout strategy, drawing courtyard buildings from its existing archetype roster, and SHALL NOT use the `standalone_library` layout.

#### Scenario: The town group is bound to street tiling
- **WHEN** the `cultivation_town` group's layout strategy is resolved
- **THEN** it SHALL be `courtyard_street_block`
- **AND** the courtyards SHALL draw buildings only from the `cultivation_town` archetype roster.

### Requirement: Town-block resources are part of v0.5 mod acceptance prep
The `cultivation_town` courtyard-street library SHALL be generated, validated, packed into the v0.5 mod jar, and documented in the available command list before staged manual acceptance.

#### Scenario: A town block is prepared for visual review
- **WHEN** a staged manual acceptance pass is requested for v0.5
- **THEN** `cultivation_town_001.nbt` onward SHALL be present under `src/main/resources/data/myvillage/structure/`
- **AND** the command documentation SHALL include `/myvillage place cultivation_town_001` and `/myvillage gallery cultivation_town`
- **AND** the reviewer SHOULD place at least one town block in game to inspect continuous frontage, shared party walls, street and lane traversability, and gate orientation.
