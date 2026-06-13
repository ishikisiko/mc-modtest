# Structure JSON DSL

## Purpose

This spec captures the current hand-authored structure JSON DSL baseline. It is temporary and mutable; proposed format changes should be discussed with the project owner first.

## Requirements

### Requirement: Structure JSON targets Minecraft 1.21.1
The current Structure JSON DSL SHALL target Minecraft `1.21.1` and DataVersion `3955`.

#### Scenario: A structure is validated or exported
- **WHEN** a caller passes an `mc_version`
- **THEN** the tool SHALL accept `1.21.1`
- **AND** it SHALL reject other Minecraft versions.

### Requirement: Structure JSON defines a bounded structure volume
Every Structure JSON document SHALL define `size` as three positive integers `[x, y, z]`, and all block operation coordinates SHALL be inside that volume.

#### Scenario: An operation references an out-of-bounds coordinate
- **WHEN** validation sees a `set`, `fill`, `line`, or legacy `blocks` coordinate outside `size`
- **THEN** validation SHALL fail.

### Requirement: Palette and state references are resolved deterministically
Structure JSON SHALL support a `palette` object mapping aliases to full blockstate strings. Operation `state` values SHALL resolve either to a palette alias or to a direct blockstate.

#### Scenario: A state reference uses a palette alias
- **WHEN** an operation references `state: "wall"`
- **AND** the palette maps `wall` to `minecraft:oak_planks`
- **THEN** export SHALL write `minecraft:oak_planks` for the affected cells.

### Requirement: Supported operations are set, fill, and line
The current Structure JSON operation forms SHALL be `set`, `fill`, and axis-aligned `line`. Legacy top-level `blocks` entries SHALL also be accepted as individual set operations.

#### Scenario: A diagonal line is provided
- **WHEN** a `line` operation changes more than one coordinate axis
- **THEN** validation/export SHALL reject it.

### Requirement: Optional metadata describes structure library use
Structure JSON metadata, when present, SHALL include a namespaced `id`, `category`, matching `size`, `entrances`, `connections`, positive `weight`, and non-empty string `tags`.

#### Scenario: A building metadata block is validated
- **WHEN** `metadata.category` is `building`
- **THEN** metadata SHALL contain at least one entrance
- **AND** each entrance SHALL include an in-bounds `pos` and a facing of `north`, `south`, `east`, or `west`.

### Requirement: Road metadata requires multiple anchors
Road metadata SHALL include at least two anchors across entrances and connections.

#### Scenario: A road has only one connection
- **WHEN** validation sees `metadata.category` equal to `road`
- **AND** the total number of entrances plus connections is less than two
- **THEN** validation SHALL fail.

### Requirement: Fill-air is explicit
The DSL SHALL only fill unspecified cells with `minecraft:air` when `fill_air` is truthy.

#### Scenario: A structure has `fill_air: true`
- **WHEN** export expands the structure
- **THEN** all unspecified in-bounds cells SHALL be represented as `minecraft:air`
- **AND** these implicit air cells SHALL NOT count as explicit overwrite noise.
