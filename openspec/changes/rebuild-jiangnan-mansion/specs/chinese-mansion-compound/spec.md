## Purpose

This spec defines the 江南大宅 (Jiangnan-style deep mansion) compound family — a multi-进 courtyard序列 realizing the Suzhou-class 大户府邸 form: 照壁 (side-standing) → 大门 → 前院 (轿厅) → 仪门 → 主院 (敞厅 + 厢) → 二门 → 后院 (楼阁) → 花园 (山水). It is distinct from the one-进 `chinese_courtyard` (北京四合院 form) in plan depth, parcel vocabulary, and the 敞厅 / 楼阁 / 花园 江南 features.

## Requirements

### Requirement: A 江南大宅 is a multi-进 z-band sequence on a central axis

A `chinese_mansion` compound SHALL be laid out along a central axis as an ordered sequence of `(yard_band, inner_gate_band)` pairs ending in a final yard, with a 花园 band behind the last yard. The number of 进 (yards) SHALL be controlled by `CompoundVariant.jin_count ∈ {3, 4}`; the shipped library SHALL use `jin_count=3` (4-进 deferred). Each yard band SHALL be separated from the next by exactly one inner gate band (`仪门` between 前院 and 主院; `二门` between 主院 and 后院). The 花园 band SHALL NOT be separated from 后院 by an inner gate (it opens directly off the 后院).

#### Scenario: A 3-进 mansion has the canonical band sequence

- **WHEN** a `chinese_mansion` compound is generated with `jin_count=3`
- **THEN** the lot SHALL split into the ordered z-bands: 前院 → 仪门 → 主院 → 二门 → 后院 → 花园
- **AND** each yard band SHALL be at least 8 cells deep
- **AND** each inner gate band SHALL be exactly 3 cells deep.

#### Scenario: The 花园 band sits behind 后院 without an inner gate

- **WHEN** a `chinese_mansion` compound is generated
- **THEN** the 花园 band SHALL be the deepest (highest-z in the canonical south frame) non-wall band
- **AND** the 花园 band SHALL border the 后院 band directly with no `inner_gate` parcel between them.

### Requirement: The 照壁 stands off-axis, blocking the sightline without blocking passage

A `chinese_mansion` compound SHALL place a 照壁 (screen wall) parcel node **off the central axis**, to one side of the gate, such that the sightline from the gate to the main axis at any yard is blocked by the 照壁 at an oblique angle. The 照壁 SHALL NOT occupy any cell on the central axis (`axis_x`). The 照壁 SHALL be a free-standing panel (1-2 cells wide, 5-6 cells tall) with a cap ridge, distinct from the perimeter wall.

#### Scenario: The 照壁 never blocks the central axis

- **WHEN** the 照壁 parcel is placed
- **THEN** no cell of the 照壁 SHALL have `x == axis_x`
- **AND** the central-axis column from the gate inward SHALL remain voxel-walkable per `courtyard-voxel-walkability`.

#### Scenario: The 照壁 blocks the sightline to the main hall

- **WHEN** a sightline is cast from the gate-opening center toward the main hall's central bay
- **THEN** the sightline SHALL intersect the 照壁 panel
- **AND** the 照壁 SHALL be positioned to require an oblique (off-axis) approach.

### Requirement: The 敞厅 (open hall) is the main yard's principal building, with an open front facade

The 主院 SHALL contain exactly one `open_hall` parcel (the 敞厅) on the central axis at the inward end. The 敞厅 SHALL be a sub-building whose front facade is open — no full-height front wall, the roof carried by standoff columns resolving through the `COLUMN` slot. The 敞厅's front wall SHALL resolve through a `FACADE_OPEN` slot (vanilla-clean) that emits columns + an open eave, not a closed wall. The 敞厅 MAY be 1 or 2 stories (`stories ∈ {1, 2}` per `multi-story-massing`); the shipped library uses `stories=1` for the 敞厅 (the 楼阁 in 后院 carries the 2-story role).

#### Scenario: The 敞厅 has an open front facade

- **WHEN** the 敞厅 sub-building is generated
- **THEN** the front facade SHALL resolve through the `FACADE_OPEN` slot
- **AND** no full-height wall SHALL be placed on the front facade
- **AND** standoff columns SHALL flank the open front at the `COLUMN` slot.

#### Scenario: The 敞厅's roof is carried by columns, not a front wall

- **WHEN** the 敞厅 roof is generated
- **THEN** the roof's front eave SHALL rest on the standoff columns
- **AND** the player SHALL be able to walk from the 主院 into the 敞厅 interior without a door (the 敞厅 is open-fronted).

### Requirement: The 后院 SHALL contain at least one 楼阁 (tower house)

The 后院 SHALL contain at least one `tower_house` parcel (绣楼 / 藏书楼) with `stories=2`, placed off-axis (east or west of the central axis). The 楼阁 SHALL reuse the existing `multi-story-massing` capability (`stories=2` + floor slab + stairwell + per-story facade band); no new mechanism is added. A `chinese_mansion` variant MAY have a second 楼阁 on the opposite side of the axis (绣楼 + 藏书楼 pair), controlled by `CompoundVariant.tower_count ∈ {1, 2}`.

#### Scenario: A 楼阁 has two walkable stories

- **WHEN** the `tower_house` sub-building is generated with `stories=2`
- **THEN** a floor slab SHALL be placed between story 1 and story 2 per `multi-story-massing`
- **AND** a stairwell SHALL connect the two stories per `multi-story-massing`
- **AND** the player SHALL be able to walk from the 后院 ground up to the second story via the stairwell.

#### Scenario: The 楼阁 is off-axis

- **WHEN** the `tower_house` parcel is placed
- **THEN** no cell of the 楼阁 footprint SHALL have `x == axis_x`
- **AND** the central axis from 主院 to 后院 SHALL remain open.

### Requirement: The 花园 is a non-axis parcel zone behind the 后院

The 花园 band SHALL span the full interior lot width (excluding the perimeter wall) and SHALL contain at least one of each: `garden_pond`, `garden_rockery`, `garden_pavilion`, plus a `GARDEN_PATH` 曲径 connecting the 后院 exit to each feature. The 花园 SHALL NOT contain full enclosed buildings (亭 are open-sided, per `garden-rockery`).

#### Scenario: The 花园 has pond + rockery + pavilion

- **WHEN** the 花园 band is generated with `garden_scale ∈ {small, large}`
- **THEN** the band SHALL contain at least one `garden_pond`, one `garden_rockery`, and one `garden_pavilion`
- **AND** a `GARDEN_PATH` 曲径 SHALL connect each feature to the 后院 exit per `courtyard-voxel-walkability`.

#### Scenario: A small 花园 has fewer features than a large one

- **WHEN** the 花园 is generated with `garden_scale=small`
- **THEN** it SHALL contain exactly one `garden_pond`, one `garden_rockery`, and one `garden_pavilion`
- **AND** when generated with `garden_scale=large`, it MAY contain additional rockeries or a second pavilion.

### Requirement: 倒座 (front row) leaves a side alley between itself and the perimeter wall

The 前院 MAY contain a `front_row` (倒座) building along the street-side wall. When present, the `front_row` footprint SHALL leave a walkable alley of at least 1 cell between itself and the perimeter wall on at least one side (east or west), so off-axis circulation exists from the gate to the 仪门 without entering the 倒座.

#### Scenario: A 倒座 with a side alley

- **WHEN** the 前院 is generated with a `front_row` parcel
- **THEN** at least one column of cells between the `front_row` east or west edge and the perimeter wall SHALL remain voxel-walkable per `courtyard-voxel-walkability`
- **AND** the alley SHALL connect the gate area to the 仪门 area.

### Requirement: Inner gates (仪门 / 二门) open at least 3 cells for passage

Each inner gate band (`仪门` between 前院 and 主院; `二门` between 主院 and 后院) SHALL open at least 3 cells for passage: the central axis cell plus one cell on each side. The solid flanks of the inner gate (where present) SHALL NOT reduce the open passage below 3 cells.

#### Scenario: An inner gate opens 3 cells

- **WHEN** an inner gate parcel is placed
- **THEN** the inner gate's `passage` meta SHALL contain at least 3 cells: `axis_x - 1`, `axis_x`, `axis_x + 1`
- **AND** each passage cell SHALL be voxel-walkable from both adjacent yards per `courtyard-voxel-walkability`.

### Requirement: 江南大宅 variants are combinatorial

Compound variation SHALL be produced by variant axes combined via a deterministic template table (one row per shipped NBT). The variant axes SHALL include `jin_count` (`3` shipped; `4` deferred), `gate_form` (门厅形制: `flush` / `recessed` / `paifang`), `garden_scale` (`none` / `small` / `large`), `tower_count` (`1` / `2`), `roof_grade` (one of the four `chinese_*` forms), `open_hall_bays` (`3` / `5`). The template table SHALL be hand-authored so each shipped NBT lands on a visibly distinct combination of `gate_form`, `garden_scale`, and `tower_count`.

#### Scenario: The library generates visibly distinct mansions

- **WHEN** the `chinese_mansion` library is generated with defaults
- **THEN** it SHALL emit six mansion instances
- **AND** each instance SHALL differ from every other on at least one of `gate_form`, `garden_scale`, or `tower_count`.

### Requirement: The 江南大宅 SHALL pass voxel-walkability end-to-end

Every `chinese_mansion` compound SHALL pass the `courtyard-voxel-walkability` checks: the player SHALL be able to walk from the gate entry to every door front, every garden feature, and every 楼阁 second story via 3D voxel-walkable STEP-ADJACENT cells. No `voxel_unreachable_*` or `voxel_step_cliff` error SHALL fire.

#### Scenario: The mansion is end-to-end walkable

- **WHEN** `validate_compound` runs on a `chinese_mansion` compound
- **THEN** no `voxel_unreachable_*` error SHALL fire
- **AND** no `voxel_step_cliff` error SHALL fire
- **AND** every door front, every `garden_pond` edge, every `garden_rockery` adjacent cell, and every `tower_house` second-story stairwell top SHALL be in the visited set.

### Requirement (FUTURE EXTENSION): 4-进 adds a deeper 花园 and an optional 跨院

A future extension SHALL add `jin_count=4` with a 第四进 deeper 花园 (假山 + 水池 + 亭 + 书房跨院). This requirement is captured for design continuity; it is NOT validated by any current validator and SHALL NOT be implemented in the `rebuild-jiangnan-mansion` change.

#### Scenario (FUTURE): A 4-进 mansion has a deeper 花园 with a 书房跨院

- **FUTURE: WHEN** a `chinese_mansion` compound is generated with `jin_count=4`
- **FUTURE: THEN** the lot SHALL add a 第四进 yard band behind the 后院
- **FUTURE: AND** the 第四进 SHALL contain a 书房跨院 parcel off the main axis.
