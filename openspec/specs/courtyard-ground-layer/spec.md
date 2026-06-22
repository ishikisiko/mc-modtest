# Courtyard Ground Layer

## Purpose

This spec defines the ground layer of a courtyard compound: how the area around the buildings, water features, and planting beds is filled with a solid, walkable block, and how that fill is classified by exposure (open sky vs under a roof or eave projection).

The ground layer is independent of the path layer (`courtyard-path-network`); the path is a single-block-deep overlay written on top of the ground tile at the same y.

## Requirements

### Requirement: Every parcel cell has a derived `ground_kind`

Every parcel cell in a courtyard compound SHALL be classified into exactly one of three `ground_kind` values: `open_sky` (exposed to the sky), `under_eave` (covered by a roof or eave projection), or `interior` (inside a building). The classification is derived — not stored — and SHALL be computed at generation time by the rule:

1. Default: every cell is `open_sky`.
2. Cells that lie in a `covered_gallery` parcel node's cells SHALL be `under_eave`.
3. Cells that lie in a `moon_platform` parcel node's cells SHALL be `under_eave`.
4. Cells that lie in the 1-cell-wide Chebyshev ring around any `BuildingSlot.footprint` SHALL be `under_eave`. The ring is the set of cells `(x, z)` such that `max(|x - fx|, |z - fz|) == 1` for some footprint cell `(fx, fz)`.
5. Cells that lie inside a building footprint SHALL be `interior`. The building pass overwrites the ground tile at these cells.

#### Scenario: A covered gallery cell is under_eave

- **WHEN** a compound places a `covered_gallery` parcel node
- **THEN** every cell in the gallery's `cells` SHALL have `ground_kind == "under_eave"`.

#### Scenario: A moon platform cell is under_eave

- **WHEN** a compound places a `moon_platform` parcel node
- **THEN** every cell in the moon platform's `cells` SHALL have `ground_kind == "under_eave"`.

#### Scenario: The 1-cell ring around a building is under_eave

- **WHEN** a compound places a `BuildingSlot` with a footprint `F`
- **THEN** for every cell `(fx, fz) in F` and every 4-neighbor-or-diagonal `(x, z)` with `max(|x - fx|, |z - fz|) == 1` and `(x, z) not in F`
- **AND** the cell `(x, z)` SHALL have `ground_kind == "under_eave"`.

#### Scenario: All other parcel cells are open_sky

- **WHEN** a parcel cell does not match any of the rules above
- **THEN** the cell SHALL have `ground_kind == "open_sky"`.

### Requirement: Yard-fill pass writes a solid block at every non-building cell

The compound layer SHALL run a `_place_yard_ground` pass that writes a solid block at every lot-interior cell that is not in `building_cells()`, `water_feature.cells`, `water_jar.cells`, `planting.cells`, or `courtyard_tree.cells`. The block written SHALL resolve through the style's `GROUND_YARD_OPEN` slot for `open_sky` cells and the `GROUND_YARD_UNDER_EAVE` slot for `under_eave` cells.

The natural surface y for the ground block SHALL be:
- `y = -1` in the outer yard band (south of the inner gate).
- `y = -1` in the inner gate band (no plinth here).
- `y = plinth_h - 1` in the main yard band (on top of the main-yard plinth).

The block SHALL be written with tags `["DETAIL", "GROUND"]` and priority `DETAIL (70)`.

#### Scenario: An open-sky cell gets a grass block

- **WHEN** a courtyard compound places an outer-yard cell with `ground_kind == "open_sky"` and the style's `GROUND_YARD_OPEN` slot resolves to `minecraft:grass_block`
- **THEN** the ground-fill pass SHALL write `minecraft:grass_block` at the cell's natural surface y.

#### Scenario: An under-eave cell gets a stone brick block

- **WHEN** a courtyard compound places a cell with `ground_kind == "under_eave"` and the style's `GROUND_YARD_UNDER_EAVE` slot resolves to `minecraft:stone_bricks`
- **THEN** the ground-fill pass SHALL write `minecraft:stone_bricks` at the cell's natural surface y.

#### Scenario: A building cell is not overwritten by yard fill

- **WHEN** a cell lies in a `BuildingSlot.footprint`
- **THEN** the yard-fill pass SHALL NOT write a block at that cell
- **AND** the cell's eventual block SHALL come from the building pass, not the yard-fill pass.

### Requirement: Ground layer uses vanilla-only style slots

The `GROUND_YARD_OPEN` and `GROUND_YARD_UNDER_EAVE` slots SHALL be defined on the style JSON (`tools/buildgen/styles/<style>.json`) with vanilla-only block ids. The vanilla profile (`--profile vanilla`) SHALL resolve every ground block to a `minecraft:` id; the full profile MAY resolve to an external-mod id if the style is the `full` profile.

#### Scenario: Vanilla profile resolves the outer-yard ground to a vanilla block

- **WHEN** a `chinese_courtyard` compound is generated with `--profile vanilla`
- **THEN** every `open_sky` cell in the outer yard SHALL be a `minecraft:grass_block`
- **AND** every `under_eave` cell SHALL be a `minecraft:stone_bricks` (or `minecraft:polished_andesite`).

#### Scenario: The full profile may use external-decor ground blocks

- **WHEN** a `chinese_courtyard` compound is generated with `--profile full`
- **THEN** the ground layer MAY resolve to an external-mod block id from the slot
- **AND** the slot definition SHALL still list `minecraft:` ids as trailing fallbacks so the vanilla profile resolves correctly.

### Requirement: Ground layer is closed — no holes

After `_place_yard_ground` runs, every lot-interior cell that is not a building, water, planting, or tree cell SHALL have a non-air block at the natural surface y. The validator SHALL fail with `ground_layer_hole:<cell>` if any such cell is found empty.

#### Scenario: A walkable cell has no hole

- **WHEN** the validator checks a courtyard compound
- **THEN** every cell in the lot interior that is not a building / water / planting / tree cell SHALL have a non-air block at the natural surface y.
- **AND** the validator SHALL NOT report `ground_layer_hole`.
