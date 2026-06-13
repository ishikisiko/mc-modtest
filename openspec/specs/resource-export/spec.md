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
The generated building library SHALL write one gallery mcfunction per style and one single-place mcfunction per generated building.

#### Scenario: A style gallery is exported
- **WHEN** the `medieval_village` building library is generated
- **THEN** the exporter SHALL write `src/main/resources/data/myvillage/function/gallery/medieval_village.mcfunction`
- **AND** it SHALL write `src/main/resources/data/myvillage/function/place/<name>.mcfunction` for each generated building.

### Requirement: The v0.4 mod exposes debug commands for manual acceptance
The NeoForge mod SHALL expose debug commands for staged manual acceptance: `/myvillage list`, `/myvillage place <structure_id>`, and `/myvillage gallery`. Command documentation SHALL be prepared with the mod artifact before asking for manual visual acceptance.

#### Scenario: A reviewer prepares for in-game acceptance
- **WHEN** the v0.4 mod jar is built for review
- **THEN** the README SHALL list the available `/myvillage` debug commands
- **AND** `/myvillage list` SHALL report loaded `myvillage` structure templates
- **AND** `/myvillage place <structure_id>` SHALL place a named template at the player
- **AND** `/myvillage gallery` SHALL place loaded templates with spacing suitable for compound structures.

### Requirement: Canonical mod generation includes smoke test and libraries
The canonical mod generation entrypoint SHALL generate `test_house_03.nbt` from the hand-authored Structure JSON DSL, the generated building library, and the generated Chinese courtyard compound library into `src/main/resources/data/myvillage/structure/`.

#### Scenario: `generate_all_structures.py` runs with default arguments
- **WHEN** generation succeeds
- **THEN** the output structure directory SHALL contain `test_house_03.nbt`
- **AND** it SHALL contain generated `small_house`, `medium_house`, `blacksmith`, shop, and big-house library NBTs
- **AND** it SHALL contain `main_hall_review.nbt`, `side_wing_review.nbt`, `front_row_review.nbt`, and six `chinese_courtyard_*.nbt` compound structures.

### Requirement: Manual acceptance prep includes mod artifact and command docs
Before a staged manual acceptance pass, contributors SHALL prepare both the buildable mod artifact and current command documentation.

#### Scenario: A staged manual acceptance pass is requested
- **WHEN** generated structures are ready for visual review
- **THEN** the repository SHOULD have a current v0.4 mod jar build path documented
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
