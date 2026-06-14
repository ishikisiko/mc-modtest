## MODIFIED Requirements

### Requirement: Building library export writes placement functions
The generated building library SHALL write one gallery mcfunction per style and one single-place mcfunction per generated building. The civic library SHALL write one civic gallery mcfunction plus one single-place mcfunction per civic structure, mirroring the Chinese courtyard compound export pattern.

#### Scenario: A style gallery is exported
- **WHEN** the `medieval_village` building library is generated
- **THEN** the exporter SHALL write `src/main/resources/data/myvillage/function/gallery/medieval_village.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/<name>.mcfunction` for each generated building.

#### Scenario: A civic library gallery is exported
- **WHEN** the civic library is generated
- **THEN** the exporter SHALL write `src/main/resources/data/myvillage/function/gallery/civic.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/tavern_001.mcfunction` through `tavern_005.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/lord_manor_001.mcfunction` through `lord_manor_003.mcfunction`.

### Requirement: Canonical mod generation includes smoke test and libraries
The canonical mod generation entrypoint SHALL generate `test_house_03.nbt` from the hand-authored Structure JSON DSL, the generated building library, the generated Chinese courtyard compound library, and the generated civic library into `src/main/resources/data/myvillage/structure/`.

#### Scenario: `generate_all_structures.py` runs with default arguments
- **WHEN** generation succeeds
- **THEN** the output structure directory SHALL contain `test_house_03.nbt`
- **AND** it SHALL contain generated `small_house`, `medium_house`, `blacksmith`, shop, and big-house library NBTs
- **AND** it SHALL contain `main_hall_review.nbt`, `side_wing_review.nbt`, `front_row_review.nbt`, and six `chinese_courtyard_*.nbt` compound structures
- **AND** it SHALL contain `tavern_001.nbt` through `tavern_005.nbt` and `lord_manor_001.nbt` through `lord_manor_003.nbt`.

## ADDED Requirements

### Requirement: Civic structures appear in the grouped gallery
The `/myvillage gallery` command SHALL include civic structures in a dedicated civic column, distinct from the housing, shop, blacksmith, Chinese courtyard, and test columns. The civic column SHALL be ordered by archetype (`tavern` before `lord_manor`) and by variant index within each archetype.

#### Scenario: The grouped gallery includes civic structures
- **WHEN** `/myvillage gallery` runs after civic library generation
- **THEN** tavern and lord manor structures SHALL appear in a civic column
- **AND** the civic column spacing SHALL match the 60-block spacing used by other columns.

### Requirement: Civic structures use the same Y offset as other generated structures
Generated civic structures SHALL be placed by `/myvillage place <id>` with the same one-block downward Y offset used by other generated non-test structures, so that terrain-replacement cells sit at ground level.

#### Scenario: A civic structure is placed via the debug command
- **WHEN** `/myvillage place tavern_001` runs
- **THEN** the structure SHALL be placed with a one-block downward Y offset
- **AND** the underlying vanilla equivalent SHALL be `/place template myvillage:tavern_001 ~ ~-1 ~`.
