# Resource Export

## Purpose

This spec captures the current resource export baseline for structure resources that are expected to grow into a broader town-generation pipeline. It is temporary and mutable; proposed changes to output paths, namespaces, target Minecraft version, or generated resource types should be discussed with the project owner first.

## Requirements

### Requirement: NBT export writes vanilla structure templates
The exporter SHALL write gzipped Java NBT structure templates containing `DataVersion`, `author`, `size`, `palette`, `blocks`, and an empty `entities` list.

#### Scenario: A Structure JSON document is exported
- **WHEN** export succeeds for Minecraft `1.21.1`
- **THEN** the root NBT SHALL include DataVersion `3955`
- **AND** `entities` SHALL be an empty list.

### Requirement: Generated BlockGrid structures are normalized before export
Generated building grids SHALL be shifted so their minimum coordinate becomes `[0, 0, 0]` before NBT export.

#### Scenario: A generated building contains negative x or z coordinates
- **WHEN** the resource exporter converts the grid to structure data
- **THEN** it SHALL normalize the grid to origin
- **AND** the exported `size` SHALL cover the normalized bounds.

### Requirement: Current mod resource namespace is myvillage
The current implemented mod resource namespace SHALL be `myvillage`.

#### Scenario: Generated structures are written to mod resources
- **WHEN** the building library generator exports a structure named `small_house_001`
- **THEN** it SHALL write `src/main/resources/data/myvillage/structure/small_house_001.nbt`
- **AND** the resource id used by generated functions SHALL be `myvillage:small_house_001`.

### Requirement: Namespace remains a known temporary decision
The `myvillage` namespace SHALL be treated as the current implementation fact, while `minecraft_town_mod` remains a project-note placeholder until the final mod id is chosen.

#### Scenario: A contributor wants to rename the namespace
- **WHEN** a change would rename generated resources or command ids away from `myvillage`
- **THEN** the contributor SHOULD discuss the change with the project owner first
- **AND** update all affected specs, resource paths, commands, and reports together.

### Requirement: Building library export writes placement functions
The generated building library SHALL write one gallery mcfunction per style or settlement group and one single-place mcfunction per generated building. The civic library SHALL write one civic gallery mcfunction plus one single-place mcfunction per civic structure, mirroring the Chinese courtyard compound export pattern.

#### Scenario: A style gallery is exported
- **WHEN** the `medieval_village` building library is generated
- **THEN** the exporter SHALL write `src/main/resources/data/myvillage/function/gallery/medieval_village.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/<name>.mcfunction` for each generated building.

#### Scenario: A civic library gallery is exported
- **WHEN** the civic library is generated
- **THEN** the exporter SHALL write `src/main/resources/data/myvillage/function/gallery/civic.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/tavern_001.mcfunction` through `tavern_005.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/lord_manor_001.mcfunction` through `lord_manor_003.mcfunction`.

#### Scenario: A cultivation group gallery is exported
- **WHEN** the `cultivation_town` or `cultivation_sect` group is generated
- **THEN** the exporter SHALL write a matching `src/main/resources/data/myvillage/function/gallery/<group_id>.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/<name>.mcfunction` for each generated cultivation structure.

### Requirement: The v0.6 mod exposes debug commands for manual acceptance
The NeoForge mod SHALL expose debug commands for staged manual acceptance: `/myvillage list`, `/myvillage town [seed]`, `/myvillage place <structure_id>`, `/myvillage gallery`, `/myvillage gallery original`, and `/myvillage gallery cultivation`. Command documentation SHALL be prepared with the mod artifact before asking for manual visual acceptance.

#### Scenario: A reviewer prepares for in-game acceptance
- **WHEN** the v0.6 mod jar is built for review
- **THEN** the README SHALL list the available `/myvillage` debug commands
- **AND** `/myvillage list` SHALL report loaded `myvillage` structure templates
- **AND** `/myvillage town [seed]` SHALL build an on-demand living town in loaded chunks
- **AND** `/myvillage place <structure_id>` SHALL place a named template at the player
- **AND** `/myvillage gallery` SHALL place loaded templates with spacing suitable for compound structures
- **AND** `/myvillage gallery original` SHALL place only non-cultivation template groups
- **AND** `/myvillage gallery cultivation` SHALL place only cultivation template groups.

### Requirement: Canonical mod generation includes smoke test and libraries
The canonical mod generation entrypoint SHALL generate `test_house_03.nbt` from the hand-authored Structure JSON DSL, the generated building library, the generated Chinese courtyard compound library, the generated civic library, the cultivation town block library, the cultivation sect building library, and the cultivation sect compound library into `src/main/resources/data/myvillage/structure/`.

#### Scenario: `generate_all_structures.py` runs with default arguments
- **WHEN** generation succeeds
- **THEN** the output structure directory SHALL contain `test_house_03.nbt`
- **AND** it SHALL contain generated `small_house`, `medium_house`, `blacksmith`, shop, and big-house library NBTs
- **AND** it SHALL contain `main_hall_review.nbt`, `side_wing_review.nbt`, `front_row_review.nbt`, and six `chinese_courtyard_*.nbt` compound structures
- **AND** it SHALL contain `tavern_001.nbt` through `tavern_005.nbt` and `lord_manor_001.nbt` through `lord_manor_003.nbt`
- **AND** it SHALL contain `cultivation_town_001.nbt` through `cultivation_town_006.nbt`, standalone sect structures, and `cultivation_sect_001.nbt` through `cultivation_sect_002.nbt`.

### Requirement: Civic structures appear in the grouped gallery
The `/myvillage gallery` and `/myvillage gallery original` commands SHALL include civic structures in a dedicated civic column, distinct from the housing, shop, blacksmith, Chinese courtyard, cultivation, and test columns. The civic column SHALL be ordered by archetype (`tavern` before `lord_manor`) and by variant index within each archetype.

#### Scenario: The grouped gallery includes civic structures
- **WHEN** `/myvillage gallery` runs after civic library generation
- **THEN** tavern and lord manor structures SHALL appear in a civic column
- **AND** the civic column spacing SHALL match the wide debug-gallery spacing used by other columns.
- **WHEN** `/myvillage gallery original` runs after civic library generation
- **THEN** tavern and lord manor structures SHALL appear in the original gallery
- **AND** cultivation structures SHALL NOT appear in that gallery.

### Requirement: Cultivation structures appear in the grouped gallery
The `/myvillage gallery` and `/myvillage gallery cultivation` commands SHALL include cultivation town and cultivation sect structures in dedicated columns. Town blocks SHALL be grouped separately from sect standalone buildings and sect compounds. `/myvillage gallery original` SHALL exclude cultivation structures.

#### Scenario: The grouped gallery includes cultivation structures
- **WHEN** `/myvillage gallery` runs after cultivation library generation
- **THEN** cultivation town structures SHALL appear in a cultivation town column
- **AND** cultivation sect structures SHALL appear in a cultivation sect column
- **AND** the column spacing SHALL match the wide debug-gallery spacing used by other columns.
- **WHEN** `/myvillage gallery cultivation` runs after cultivation library generation
- **THEN** only cultivation town and cultivation sect columns SHALL be placed.

### Requirement: Civic structures use the same Y offset as other generated structures
Generated civic and cultivation structures SHALL be placed by `/myvillage place <id>` with the same one-block downward Y offset used by other generated non-test structures, so that terrain-replacement cells sit at ground level.

#### Scenario: A civic structure is placed via the debug command
- **WHEN** `/myvillage place tavern_001` runs
- **THEN** the structure SHALL be placed with a one-block downward Y offset
- **AND** the underlying vanilla equivalent SHALL be `/place template myvillage:tavern_001 ~ ~-1 ~`.

#### Scenario: A cultivation structure is placed via the debug command
- **WHEN** `/myvillage place cultivation_town_001` or `/myvillage place cultivation_sect_001` runs
- **THEN** the structure SHALL be placed with a one-block downward Y offset
- **AND** the underlying vanilla equivalent SHALL use `/place template myvillage:<id> ~ ~-1 ~`.

### Requirement: Manual acceptance prep includes mod artifact and command docs
Before a staged manual acceptance pass, contributors SHALL prepare both the buildable mod artifact and current command documentation.

#### Scenario: A staged manual acceptance pass is requested
- **WHEN** generated structures are ready for visual review
- **THEN** the repository SHOULD have a current v0.6 mod jar build path documented
- **AND** README and AGENTS guidance SHALL identify the available debug commands
- **AND** relevant specs SHALL state that command documentation is part of acceptance prep, not an optional afterthought.

### Requirement: Worldgen resources are not currently exported
The current export pipeline SHALL NOT claim support for worldgen, jigsaw pools, structure sets, biome placement, entities, villagers, loot tables, or complex block entity NBT.

#### Scenario: A generated mod jar is built
- **WHEN** resources are packed
- **THEN** generated structure templates and debug functions MAY be present
- **AND** generated worldgen registration resources SHALL NOT be assumed present.

### Requirement: Future export scope may include town and NPC systems
Future export changes MAY add town layout resources, structure pools, functional-building metadata, or NPC-related data, but such support SHALL be introduced explicitly rather than implied by the current structure-template export.

#### Scenario: NPC-related export is proposed
- **WHEN** a contributor proposes exporting villager, NPC, profession, loot, or behavior-related data
- **THEN** the proposal SHALL identify the new resource formats and runtime assumptions
- **AND** it SHALL keep the current NBT-only structure export contract clear until those resources are implemented.
