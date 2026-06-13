# Resource Export

## Purpose

This spec captures the current resource export baseline. It is temporary and mutable; proposed changes to output paths, namespaces, target Minecraft version, or generated resource types should be discussed with the project owner first.

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

### Requirement: Canonical mod generation includes smoke test and library
The canonical mod generation entrypoint SHALL generate `test_house_03.nbt` from the hand-authored Structure JSON DSL and the generated building library into `src/main/resources/data/myvillage/structure/`.

#### Scenario: `generate_all_structures.py` runs with default arguments
- **WHEN** generation succeeds
- **THEN** the output structure directory SHALL contain `test_house_03.nbt`
- **AND** it SHALL contain generated `small_house`, `medium_house`, and `blacksmith` library NBTs.

### Requirement: Worldgen resources are not currently exported
The current export pipeline SHALL NOT claim support for worldgen, jigsaw pools, structure sets, biome placement, entities, villagers, loot tables, or complex block entity NBT.

#### Scenario: A generated mod jar is built
- **WHEN** resources are packed
- **THEN** generated structure templates and debug functions MAY be present
- **AND** generated worldgen registration resources SHALL NOT be assumed present.
