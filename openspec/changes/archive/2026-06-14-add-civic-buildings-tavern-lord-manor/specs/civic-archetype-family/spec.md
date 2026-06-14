# Civic Archetype Family

## Purpose

This spec captures the civic archetype family: a procedurally generated
building family covering public/civic buildings, emitted by a dedicated
library loop separate from the medieval housing/commercial library and
the Chinese courtyard compound generator. Initial members are `tavern`
and `lord_manor`. Future civic pieces (market, watchtower, chapel,
archive) may join this family.

## Requirements

### Requirement: Civic family is a distinct generated library
The civic archetype family SHALL be emitted by a dedicated generation
entrypoint separate from the medieval building library loop and the
Chinese compound generator. Civic structures SHALL be placed under the
same `myvillage` resource namespace but identifiable by filename
prefix.

#### Scenario: The civic library is generated
- **WHEN** the civic library generator runs with default arguments
- **THEN** it SHALL emit `tavern_001.nbt` through `tavern_005.nbt`
- **AND** it SHALL emit `lord_manor_001.nbt` through `lord_manor_003.nbt`
- **AND** it SHALL write each file to
  `src/main/resources/data/myvillage/structure/`.

#### Scenario: The medieval library loop is unchanged
- **WHEN** the medieval building library is generated
- **THEN** its output SHALL NOT include any civic-prefixed structure
- **AND** the medieval library loop SHALL NOT reference civic
  archetypes.

### Requirement: Tavern is a great-hall building with mezzanine
The `tavern` archetype SHALL be a two-story building whose ground story
contains a tall great hall and whose upper story is a mezzanine that
covers only part of the footprint, leaving the great hall with a
double-height ceiling over the uncovered portion. The tavern SHALL
include an inn zone with at least one bed and SHALL include at least
one civic Hall marker block.

#### Scenario: A tavern is generated
- **WHEN** the `tavern` archetype is generated
- **THEN** the main volume SHALL have `stories == 2`
- **AND** the main volume SHALL carry a `mezzanine` meta field naming
  the covered half-plane
- **AND** the massing graph SHALL include at least one interior zone
  with kind `tavern_hall`
- **AND** the massing graph SHALL include at least one interior zone
  with kind `tavern_inn`.

#### Scenario: The tavern great hall has double-height ceiling
- **WHEN** the tavern's mezzanine floor is placed
- **THEN** the mezzanine slab SHALL cover only the configured
  half-plane of the main footprint
- **AND** the uncovered half-plane SHALL have an open ceiling up to the
  roof.

### Requirement: Lord manor is a civic residence with an attached tower
The `lord_manor` archetype SHALL be a two-story main volume with an
attached `tower_volume` rising at least one story above the main roof.
The lord manor SHALL include a council chamber with at least one civic
marker block and SHALL display at least one heraldry banner.

#### Scenario: A lord manor is generated
- **WHEN** the `lord_manor` archetype is generated
- **THEN** the massing graph SHALL include a main volume with
  `stories == 2`
- **AND** the massing graph SHALL include exactly one `tower_volume`
  node attached to the main volume
- **AND** the tower volume's `stories` SHALL be at least one greater
  than the main volume's `stories`.

#### Scenario: The lord manor displays heraldry
- **WHEN** a lord manor is generated
- **THEN** the massing graph SHALL include at least one decoration or
  facade meta referencing the `HERALDRY` material slot
- **AND** the generated structure SHALL contain at least one banner
  blockstate.

### Requirement: Tavern variants differ structurally
The five generated tavern variants SHALL differ on at least one
structural axis: mezzanine covered half-plane, stable annex presence,
tower absence (taverns have no tower), footprint, or roof form.
Decoration-patch variation alone SHALL NOT satisfy the variant
distinction.

#### Scenario: Five tavern variants are generated
- **WHEN** the civic library emits the tavern tier
- **THEN** it SHALL produce at least five variants
- **AND** the variants SHALL differ on at least one structural axis
  beyond decoration patches.

### Requirement: Lord manor variants differ structurally
The three generated lord manor variants SHALL differ on at least one
structural axis: tower height above main, attached side, footprint,
wing or courtyard presence, or belfry roof form. Decoration-patch
variation alone SHALL NOT satisfy the variant distinction.

#### Scenario: Three lord manor variants are generated
- **WHEN** the civic library emits the lord manor tier
- **THEN** it SHALL produce at least three variants
- **AND** the variants SHALL differ on at least one structural axis
  beyond decoration patches.

### Requirement: Civic family reuses the medieval_village style
The civic family SHALL reuse the `medieval_village` style profile
rather than introducing a new style file in v1. Civic builders SHALL
reference the new material slots `INTERIOR_CIVIC`, `FURNITURE`,
`SIGNAGE`, and `HERALDRY` defined by the style profile schema.

#### Scenario: A civic archetype requests its style
- **WHEN** a civic builder resolves a material
- **THEN** it SHALL load `tools/buildgen/styles/medieval_village.json`
- **AND** civic interior ops SHALL resolve role blocks through the
  `INTERIOR_CIVIC`, `FURNITURE`, `SIGNAGE`, and `HERALDRY` slots.

### Requirement: Civic family stays compatible with existing manual acceptance
The civic family SHALL be debug-placeable via the existing
`/myvillage place <structure_id>` command and SHALL appear in the
`/myvillage gallery` output as a distinct column. No new command SHALL
be required to inspect civic structures.

#### Scenario: A reviewer places a civic structure
- **WHEN** a reviewer runs `/myvillage place tavern_001`
- **THEN** the structure SHALL load from the `myvillage` namespace
- **AND** it SHALL place with the same one-block downward Y offset used
  by other generated structures.

#### Scenario: The gallery includes a civic column
- **WHEN** a reviewer runs `/myvillage gallery`
- **THEN** tavern and lord manor structures SHALL appear in a civic
  column distinct from the housing, shop, blacksmith, and Chinese
  courtyard columns.
