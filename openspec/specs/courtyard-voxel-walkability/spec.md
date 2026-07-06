# Courtyard Voxel Walkability

## Purpose

This spec defines the 3D voxel-walkability check that replaces the 2D-cell multi-source BFS reachability check previously used by `validate_compound` and `validate_small_courtyard`. The 2D check validated that every endpoint was in the BFS-reached set, but a real Minecraft player walks in 3D voxel space — a cell is walkable only if its foot support is solid and its body/head clearance is air, and adjacent cells are walkable only if the height difference is ≤ 1 block. The 2D check passed compounds that were in fact "堵住" (blocked) in 3D — most visibly the `chinese_courtyard` 影壁封轴 + 3-block-cliff defects. This validator catches those.

## Requirements

### Requirement: Voxel walkability uses the standard Minecraft autostep rule

The voxel-walkability validator SHALL classify a cell `(x, y, z)` as STANDABLE iff:
- The block at `(x, y-1, z)` is SOLID (provides foot support).
- The block at `(x, y, z)` is NON-SOLID (body space clear).
- The block at `(x, y+1, z)` is NON-SOLID (head space clear).

The validator SHALL classify two STANDABLE cells `(x_a, y_a, z_a)` and `(x_b, y_b, z_b)` as STEP-ADJACENT iff:
- They are 4-neighbors in (x, z) (`|x_a - x_b| + |z_a - z_b| == 1`).
- The y-difference `|y_a - y_b| ≤ 1` (autostep up 1, free-fall any).

The validator SHALL treat any block outside the vanilla passable set (`minecraft:air`, `minecraft:water`, `minecraft:lava`, and passable decorations: torches, signs, buttons, levers, rails, carpets, flowers, saplings, tall grass, vines, lanterns — see implementation's NON_SOLID set) as SOLID. All other blocks (stone, wood, stairs, slabs, leaves (when persistent), walls, fences, columns, plaques, rockery blocks) are SOLID.

#### Scenario: A path cell on solid ground is STANDABLE

- **WHEN** a path cell at `(x, -1, z)` is `minecraft:gravel` and `(x, 0, z)` + `(x, 1, z)` are air
- **THEN** the cell `(x, 0, z)` is STANDABLE
- **AND** a player can stand at `(x, 0, z)` with feet on the gravel.

#### Scenario: A cell whose body space is occupied is NOT STANDABLE

- **WHEN** a path cell's body space `(x, 0, z)` is occupied by a STRUCTURE block (e.g. a column, wall, or roof block)
- **THEN** the cell is NOT STANDABLE
- **AND** the player cannot stand there.

### Requirement: The validator runs a 3D BFS from the gate-entry STANDABLE column

`validate_compound` SHALL identify the gate-entry STANDABLE column (the lowest y in column `(axis_x, z=1)` — the cell just inside the perimeter gate — where STANDABLE holds) and SHALL run a 3D BFS over STEP-ADJACENT cells, bounded by the lot interior. The visited set SHALL contain every STANDABLE cell reachable from the gate entry. The validator SHALL report the visited-cell count and the unreachable-endpoint count as stats.

#### Scenario: The 3D BFS visits every reachable STANDABLE cell

- **WHEN** the 3D BFS runs from the gate entry
- **THEN** every STANDABLE cell STEP-ADJACENT-connected to the gate entry SHALL be in the visited set
- **AND** cells behind a SOLID wall or above an unbridgeable cliff SHALL be excluded.

### Requirement: Every door front is voxel-reachable

For every `BuildingSlot` whose `door_info` is populated, the cell `door_info["front"]` SHALL have at least one STANDABLE y in its column that is in the visited set. If not, the validator SHALL report `voxel_unreachable_door:<archetype>`.

#### Scenario: A door front is reachable

- **WHEN** the validator inspects a `main_hall` door front at `(x, y_door, z)`
- **THEN** at least one y in column `(x, z)` SHALL be STANDABLE and in the visited set
- **AND** no `voxel_unreachable_door:main_hall` error SHALL fire.

#### Scenario: A door front is unreachable

- **WHEN** the validator inspects a `side_wing` door front whose column has no STANDABLE cell in the visited set
- **THEN** the validator SHALL report `voxel_unreachable_door:side_wing`.

### Requirement: Every landscape endpoint is voxel-reachable

For every water feature, water jar, planting parcel, and moon platform parcel, at least one adjacent shore/edge cell SHALL be STANDABLE and in the visited set. If not, the validator SHALL report `voxel_unreachable_endpoint:<cell>`.

#### Scenario: A water feature edge is reachable

- **WHEN** the validator inspects a `water_feature` parcel
- **THEN** at least one cell 4-adjacent to the water feature's cells SHALL be STANDABLE and in the visited set
- **AND** no `voxel_unreachable_endpoint:<cell>` error SHALL fire for that feature.

### Requirement: No un-bridged step cliff exists between adjacent path cells

For every pair of 4-neighbor cells both in the `path` parcel, where both cells' natural surface y is the cell's standable y, if `|y_a - y_b| ≥ 2` AND no `stone_brick_stairs` block bridges the gap at one of the cells, the validator SHALL report `voxel_step_cliff:<cell_a>-><cell_b>`.

#### Scenario: A 3-block cliff with no stair is flagged

- **WHEN** a path cell at `(x, z_outer)` has surface y=-1, and its 4-neighbor path cell at `(x, z_inner)` has surface y=2, and no `stone_brick_stairs` exists at either cell
- **THEN** the validator SHALL report `voxel_step_cliff:(x, z_outer)->(x, z_inner)`.

#### Scenario: A bridged step is not flagged

- **WHEN** the same cliff exists but a `stone_brick_stairs[facing=<uphill>]` block bridges the gap at `(x, z_outer)`
- **THEN** the validator SHALL NOT report `voxel_step_cliff`.

### Requirement: No path cell is blocked by a SOLID body/head block

For every cell in the `path` parcel, the body space and head space above the standable y SHALL be NON-SOLID. If a STRUCTURE/ROOF/COLUMN block occupies the body or head space of a path cell, the validator SHALL report `voxel_blocked_by_solid:<cell>`.

#### Scenario: A path cell blocked by a column is flagged

- **WHEN** a path cell at `(x, z)` has standable y=0, but a COLUMN block exists at `(x, 0, z)` (occupying body space)
- **THEN** the validator SHALL report `voxel_blocked_by_solid:(x, z)`.

### Requirement: The generalized stair pass bridges every `|Δy| ≥ 2` path-adjacent boundary

`_place_band_transition_stairs` (formerly `_place_plinth_stairs`, generalized) SHALL walk every pair of 4-neighbor path cells and, where the natural surface y differs by `≥ 2`, SHALL place `N = |Δy|` `stone_brick_stairs[facing=<uphill>, half=bottom]` blocks bridging the gap. The pass SHALL skip pairs where either cell is in `building_cells()` or is a `door_info["front"]` cell. The pass SHALL be a no-op for compounds without multiple surface-y bands (e.g. `platform_tier=none`).

#### Scenario: A 2-block cliff gets 2 stairs

- **WHEN** a path cell at `(x, z_outer)` has surface y=-1, and its 4-neighbor path cell at `(x, z_inner)` has surface y=1 (`Δy=2`)
- **THEN** the stair pass SHALL place 2 `stone_brick_stairs` blocks bridging the gap
- **AND** the lower stair SHALL be at `(x, z_outer)` facing toward `(x, z_inner)`.

#### Scenario: A 3-block cliff gets 3 stairs

- **WHEN** the same pair has `Δy=3` (y=-1 vs y=2)
- **THEN** the stair pass SHALL place 3 `stone_brick_stairs` blocks bridging the gap.

#### Scenario: A pair involving a door front is skipped

- **WHEN** a path cell's 4-neighbor is a `door_info["front"]` cell
- **THEN** the stair pass SHALL NOT place a stair at either cell.

### Requirement: The validator runs on every courtyard compound family

`validate_compound` and `validate_small_courtyard` SHALL apply the voxel-walkability checks to every compound family (`chinese_courtyard`, `chinese_mansion`, small-courtyard unit, and any future family). The validator SHALL NOT be silently suppressed for any family.

#### Scenario: The validator runs on `chinese_courtyard`

- **WHEN** `validate_compound` runs on a regenerated `chinese_courtyard` compound (with 照壁侧立 + side alley + multi-cell 垂花门 + transition stairs)
- **THEN** no `voxel_unreachable_*` or `voxel_step_cliff` error SHALL fire.

#### Scenario: The validator runs on `chinese_mansion`

- **WHEN** `validate_compound` runs on a `chinese_mansion` compound
- **THEN** no `voxel_unreachable_*` or `voxel_step_cliff` error SHALL fire.

#### Scenario: The validator surfaces defects in `cultivation_sect_*` / `medieval_*` if present

- **WHEN** `validate_compound` runs on a `cultivation_sect` or `medieval` compound and finds a `voxel_*` defect
- **THEN** the defect SHALL be reported (NOT suppressed)
- **AND** per the `rebuild-jiangnan-mansion` design D9, any such defect SHALL be fixed in a separate small change (not in the mansion change itself).
