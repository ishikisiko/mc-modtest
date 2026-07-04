# Chinese Mansion Compound

## Purpose

This spec defines the 江南大宅 (Jiangnan-style deep mansion) compound family — a multi-进 courtyard序列 realizing the Suzhou-class 大户府邸 form: 照壁 (side-standing) → 大门 → 前院 (轿厅) → 仪门 → 主院 (敞厅 + 厢) → 二门 → 后院 (楼阁) → 花园 (山水). It is distinct from the one-进 `chinese_courtyard` (北京四合院 form) in plan depth, parcel vocabulary, and the 敞厅 / 楼阁 / 花园 江南 features.
## Requirements
### Requirement: A 江南大宅 is a multi-进 enclosure sequence on a central axis

A `chinese_mansion` compound SHALL be laid out along a central axis as an
ordered sequence of 进 (yards), each realized as the **enclosed negative space**
of buildings placed against the perimeter with form-rule facings (per
`compound-enclosure-planning` + `building-orientation-variants`), separated by
inner gates, ending in a 花园 band. The number of 进 SHALL be controlled by
`CompoundVariant.jin_count ∈ {3, 4}`; the shipped library SHALL use `jin_count=3`.
The 花园 band SHALL open directly off the 后院 with no inner gate between them.

Yard depth is *derived* from the building enclosure, not a pre-cut band
parameter. The 3-cell inner-gate passage width is retained via the
inner-gate passage requirement below.

#### Scenario: A 3-进 mansion has the canonical enclosure sequence

- **WHEN** a `chinese_mansion` compound is generated with `jin_count=3`
- **THEN** the realized layout SHALL produce the ordered 进: 前院 → 仪门 → 主院 → 二门 → 后院 → 花园
- **AND** each 进 SHALL be the enclosed negative space of its facing-buildings
- **AND** no z-band tuple comparison SHALL be used to assert the sequence.

#### Scenario: The 花园 band sits behind 后院 without an inner gate

- **WHEN** a `chinese_mansion` compound is generated
- **THEN** the 花园 band SHALL be the deepest (highest-z in the canonical south frame) non-wall band
- **AND** the 花园 band SHALL border the 后院 band directly with no `inner_gate` parcel between them.

### Requirement: The south entrance is a `gate_house` through-building straddling the perimeter

The `chinese_mansion` south entrance SHALL be realized as a `gate_house` sub-building placed so its south wall is on the perimeter line and its body projects inward into the 前院. The gate-house is a real building volume with a 门楼 roof, 门框, and a passage the player walks through — producing the gate feel that a hole-in-the-wall could not. The gate_house SHALL be built by the existing `build_gate_house` archetype builder. The entrance SHALL NOT be a row of carved air cells in the perimeter wall.

> This requirement group consolidates the former standalone `mansion-gate-house` spec (archived in change `rebuild-mansion-enclosure-plan`); gate-house behavior is part of the `chinese_mansion` compound contract and is validated by `validate_mansion`.

#### Scenario: A gate_house occupies the south perimeter at the axis

- **WHEN** a `chinese_mansion` compound is generated
- **THEN** a `gate_house` building slot SHALL be present
- **AND** the gate_house footprint SHALL straddle the south perimeter line (z=0 band) centered on the axis
- **AND** no carved-air gate opening SHALL be the sole entrance.

### Requirement: The player walks through the gate_house, not past a hole

The gate_house SHALL carry a street-facing door on its south wall (the inward / north-facing passage) and SHALL open onto the 前院 on its north wall (the passage). The player SHALL walk through the gate_house — under its 门楼 roof and past its 门框 — to enter the compound. The passage SHALL be voxel-walkable.

#### Scenario: The gate-house passage is walkable end-to-end

- **WHEN** the voxel-walkability BFS runs from outside the gate_house
- **THEN** the player SHALL reach the 前院 by passing through the gate_house
- **AND** the path SHALL go under the gate_house roof (not around an open hole).

### Requirement: The perimeter walls close around the gate_house with no gap

The perimeter wall SHALL be built with a gap exactly matching the gate_house footprint, and the gate_house's own side walls SHALL close that gap, so the perimeter stays sealed except through the gate_house passage. The compound SHALL NOT leak to the outside except via the gate_house.

#### Scenario: The perimeter is sealed around the gate_house

- **WHEN** the realized layout is examined
- **THEN** every south-perimeter cell not under the gate_house SHALL be wall
- **AND** the gate_house side walls SHALL span the gap
- **AND** `validate_mansion`'s perimeter-integrity check SHALL pass.

### Requirement: `gate_type` selects the gate_house footprint and roof grade

The `gate_type` SHALL select the gate_house footprint and roof grade. The `gate_type` values (manzi / jinzhu / guangliang, derived from the variant's `gate_form`) SHALL resolve to `SCALE_TIERS["gate_house"]` footprints rather than selecting the width of a carved hole. The gate_house SHALL be centered on the axis regardless of gate_type.

#### Scenario: A guangliang (广亮门) gate uses the largest footprint

- **WHEN** the variant's `gate_form` resolves to `guangliang`
- **THEN** the gate_house SHALL use the largest `gate_house` footprint
- **AND** the gate_house SHALL be centered on the axis
- **AND** no carved-hole width SHALL be derived from `gate_form`.

### Requirement: The gate_house faces inward to begin the 前院 enclosure

The gate_house's facing SHALL be recorded as inward (its passage opens onto the 前院), making it the first element of the 前院 enclosure. The 照壁 SHALL stand off-axis inside the 前院, completing the entry sequence (街 → 门屋 → 照壁 → 前院).

#### Scenario: The entry sequence reads 街 → 门屋 → 照壁 → 前院

- **WHEN** the realized layout is examined
- **THEN** the gate_house SHALL be the southernmost building
- **AND** the 照壁 SHALL stand off-axis inside the 前院, behind the gate_house
- **AND** the sightline from the gate_house passage SHALL intersect the 照壁.

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

The 主院 SHALL contain exactly one `open_hall` parcel (the 敞厅), the 主院's principal building, anchoring the north wall of the 主院 enclosure. The 敞厅 SHALL be a sub-building whose front facade is open — no full-height front wall, the roof carried by standoff columns resolving through the `COLUMN` slot. The 敞厅's front wall SHALL resolve through a `FACADE_OPEN` slot (vanilla-clean) that emits columns + an open eave, not a closed wall. The 敞厅 MAY be 1 or 2 stories (`stories ∈ {1, 2}` per `multi-story-massing`); the shipped library uses `stories=1` for the 敞厅 (the 楼阁 in 后院 carries the 2-story role). The 敞厅's facing is fixed at `south` per the form rule in `building-orientation-variants`.

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

The 后院 SHALL contain at least one `tower_house` parcel (绣楼 / 藏书楼) with `stories=2`, placed off-axis (east or west of the central axis). The 楼阁 sits at the south edge of the 后院 with its yard space to the north, so it faces north (door→后院), per the form rule in `building-orientation-variants`. The 楼阁 SHALL reuse the existing `multi-story-massing` capability (`stories=2` + floor slab + stairwell + per-story facade band); no new mechanism is added. A `chinese_mansion` variant MAY have a second 楼阁 on the opposite side of the axis (绣楼 + 藏书楼 pair), controlled by `CompoundVariant.tower_count ∈ {1, 2}`.

#### Scenario: A 楼阁 has two walkable stories

- **WHEN** the `tower_house` sub-building is generated with `stories=2`
- **THEN** a floor slab SHALL be placed between story 1 and story 2 per `multi-story-massing`
- **AND** a stairwell SHALL connect the two stories per `multi-story-massing`
- **AND** the player SHALL be able to walk from the 后院 ground up to the second story via the stairwell.

#### Scenario: The 楼阁 is off-axis and faces north

- **WHEN** the `tower_house` parcel is placed
- **THEN** no cell of the 楼阁 footprint SHALL have `x == axis_x`
- **AND** its facing SHALL be north (door opening onto the 后院 yard space north of the tower)
- **AND** the central axis from 主院 to 后院 SHALL remain open.

### Requirement: The 花园 is a non-axis parcel zone behind the 后院

The 花园 band SHALL span the full interior lot width (excluding the perimeter
wall) and SHALL contain at least one of each: `garden_pond`, `garden_rockery`,
`garden_pavilion`, plus a `PATH_TOUR` 曲径 connecting the 月洞门 passage to each
feature. The `garden_pavilion` SHALL sit on a dry pond bank with its footprint
4-adjacent to pond water so it reads as a 水亭 rather than a detached garden
building. When the garden depth allows it, the pond/pavilion composition SHALL
be centered on the mansion axis and the pavilion SHALL sit on the dry south bank
with a south-facing approach and pond water behind it, rather than being
squeezed into the west-side wall corridor or an east-wall corner. The back
perimeter behind that approach, the side sightlines, and the moon-gate screen
behind the water SHALL be opened down to ground level around the pavilion
frontage/backdrop so the reference pavilion is not visually reviewed through a
full-height wall corridor, over a low wall, or against a blank wall.
The `garden_pavilion` SHALL replicate the supplied heavy scenic
pavilion reference as closely as the generator allows: raised stone base,
wooden deck, thick timber posts, railings, trapdoor/lattice bracket details,
hanging lanterns, broad dark-oak double eaves, and grey stone roof ornaments.
The pavilion setting SHALL include the reference image's visible landscape cues:
side water beside the platform, foreground flowers/grass, a soft stone/dirt
approach path, a bamboo cluster, and a green backdrop behind the water.
The `garden_pavilion` SHALL NOT float above the pond bank and SHALL NOT revert
to fence-post/light-eave proportions. The separate right-side 水边廊 / shed
(`waterside_gallery`) SHALL NOT be generated in the pond composition. A
`PATH_WATERSIDE` stairs + slab bridge SHALL cross the pond to the 亭 or island
rockery. The 花园 SHALL NOT contain full enclosed buildings (亭 are open-sided,
per `garden-rockery`).

The tour route through the 花园 SHALL be a waypoint polyline (not a
shortest-path tree), routed from the `moon_gate_passage` through the rockery
south face, the nearest pond shore, and the 亭, with each segment a shortest
path and obstacle-avoidance forcing any straight segment to curve around the
rockery/pond.

#### Scenario: The 花园 has pond + rockery + reference pavilion, without a shed

- **WHEN** the 花园 band is generated with `garden_scale ∈ {small, large}`
- **THEN** the band SHALL contain at least one `garden_pond`, one
  `garden_rockery`, and one `garden_pavilion`
- **AND** no `waterside_gallery` shed SHALL be generated beside the pond
- **AND** a `PATH_WATERSIDE` slab bridge SHALL cross the pond to the 亭/island
- **AND** a `PATH_TOUR` 曲径 SHALL connect the 月洞门 passage to each feature per
  `courtyard-voxel-walkability`.

#### Scenario: The pavilion reads as a water pavilion

- **WHEN** the `garden_pavilion` is placed
- **THEN** every pavilion footprint cell SHALL be dry
- **AND** at least one pavilion footprint cell SHALL be 4-adjacent to
  `garden_pond` water
- **AND** the pavilion SHALL NOT be placed on the far side of the garden lawn
  without a direct pond edge.

#### Scenario: The pavilion has a front approach and pond behind it

- **WHEN** the garden band can fit a 9x9 pavilion south of the pond
- **THEN** the pavilion SHALL be placed on the dry south bank
- **AND** its entry side SHALL face south
- **AND** its water side SHALL face north
- **AND** its center x SHALL be within two cells of the mansion axis
- **AND** the perimeter cells across the pavilion frontage SHALL be clear from
  y=0 through y=7
- **AND** the side perimeter cells beside the pavilion SHALL also be clear from
  y=0 through y=7
- **AND** the moon-gate screen cells across the water backdrop SHALL be clear
  from y=0 through y=7.

#### Scenario: The pavilion replicates the supplied heavy scenic pavilion

- **WHEN** the `garden_pavilion` roof is placed
- **THEN** it SHALL have a raised stone base and wood deck under the columns
- **AND** it SHALL use heavy non-fence timber columns
- **AND** it SHALL have railings, trapdoor/lattice bracket details, and hanging
  lanterns
- **AND** it SHALL have a broad lower dark-oak eave, a smaller raised upper
  eave, and grey stone roof ornaments
- **AND** the surrounding setting SHALL include visible side water, foreground
  flowers/grass, an approach path, bamboo, and a green backdrop.

#### Scenario: The pond composition stays visually separated

- **WHEN** the pavilion, pond, island rockery, and waterside bridge are generated
- **THEN** the pavilion footprint SHALL NOT overlap pond water or the island
  rockery
- **AND** bridge clear-water lanes SHALL contain no lily pads
- **AND** no separate waterside shed SHALL be present.

#### Scenario: The 花园 tour route winds through scenic waypoints

- **WHEN** the `PATH_TOUR` 曲径 is routed
- **THEN** it SHALL be a polyline through the rockery south face, the nearest
  pond shore, and the 亭
- **AND** the route SHALL visibly turn at each waypoint
- **AND** no tour cell SHALL coincide with a rockery or pond cell.

#### Scenario: A small 花园 has fewer features than a large one

- **WHEN** the 花园 is generated with `garden_scale=small`
- **THEN** it SHALL contain exactly one `garden_pond`, one `garden_rockery`, and
  one `garden_pavilion`
- **AND** when generated with `garden_scale=large`, it MAY contain additional
  rockeries or a second pavilion.

### Requirement: 倒座 (front row) leaves a side alley between itself and the perimeter wall

The 前院 MAY contain a `front_row` (倒座) building along the street-side wall. When present, the `front_row` footprint SHALL leave a walkable alley of at least 1 cell between itself and the perimeter wall on at least one side (east or west), so off-axis circulation exists from the gate to the 仪门 without entering the 倒座.

#### Scenario: A 倒座 with a side alley

- **WHEN** the 前院 is generated with a `front_row` parcel
- **THEN** at least one column of cells between the `front_row` east or west edge and the perimeter wall SHALL remain voxel-walkable per `courtyard-voxel-walkability`
- **AND** the alley SHALL connect the gate area to the 仪门 area.

### Requirement: Inner gates sit at the adjacency boundary between consecutive yards

Each inner gate (仪门 between 前院 and 主院; 二门 between 主院 and 后院) SHALL be placed at the adjacency boundary between its two enclosing yards, opening at least 3 cells for passage (the central axis cell plus one cell on each side). The gate's position SHALL be derived from the realized enclosure, not from a z-band tuple. The solid flanks of the inner gate (where present) SHALL NOT reduce the open passage below 3 cells.

#### Scenario: 仪门 borders 前院 and 主院

- **WHEN** the realized layout is examined
- **THEN** the 仪门 SHALL border the 前院 enclosed space on one side and the 主院 enclosed space on the other
- **AND** the 仪门 passage SHALL contain at least the cells `{(axis_x-1, z), (axis_x, z), (axis_x+1, z)}` for the relevant z
- **AND** each passage cell SHALL be voxel-walkable from both adjacent yards per `courtyard-voxel-walkability`.

### Requirement: Every mansion building faces its yard per the form rule

Every building in a `chinese_mansion` compound SHALL face the yard it encloses
per the form rule in `building-orientation-variants`: 正房/open_hall face south;
倒座 faces north; 西厢 faces east; 东厢 faces west; the gate_house faces inward
(toward 前院); 楼阁 faces north (door onto the 后院 yard space north of the
tower). No building's door SHALL open onto the street or away from its yard.

#### Scenario: The 倒座 faces the 前院, not the street

- **WHEN** the realized layout is examined
- **THEN** the 倒座's door SHALL be on its north (high-z) wall, facing the 前院
- **AND** the 倒座's door SHALL NOT open onto the street (south).

### Requirement: 江南大宅 variants are combinatorial

Compound variation SHALL be produced by variant axes combined via a deterministic template table (one row per shipped NBT). The variant axes SHALL include `jin_count` (`3` shipped; `4` deferred), `gate_form` (门厅形制: `flush` / `recessed` / `paifang`), `garden_scale` (`none` / `small` / `large`), `tower_count` (`1` / `2`), `roof_grade` (one of the four `chinese_*` forms), `open_hall_bays` (`3` / `5`). The template table SHALL be hand-authored so each shipped NBT lands on a visibly distinct combination of `gate_form`, `garden_scale`, and `tower_count`.

#### Scenario: The library generates visibly distinct mansions

- **WHEN** the `chinese_mansion` library is generated with defaults
- **THEN** it SHALL emit six mansion instances
- **AND** each instance SHALL differ from every other on at least one of `gate_form`, `garden_scale`, or `tower_count`.

### Requirement: The 江南大宅 SHALL pass voxel-walkability end-to-end

Every `chinese_mansion` compound SHALL pass the `courtyard-voxel-walkability` checks: the player SHALL be able to walk from the gate entry to every door front, every garden feature, and every 楼阁 second story via 3D voxel-walkable STEP-ADJACENT cells. No `voxel_unreachable_*` or `voxel_step_cliff` error SHALL fire.

#### Scenario: The mansion is end-to-end walkable

- **WHEN** `validate_mansion` runs on a `chinese_mansion` compound
- **THEN** no `voxel_unreachable_*` error SHALL fire
- **AND** no `voxel_step_cliff` error SHALL fire
- **AND** every door front, every `garden_pond` edge, every `garden_rockery` adjacent cell, and every `tower_house` second-story stairwell top SHALL be in the visited set.

### Requirement (FUTURE EXTENSION): 4-进 adds a deeper 花园 and an optional 跨院

A future extension SHALL add `jin_count=4` with a 第四进 deeper 花园 (假山 + 水池 + 亭 + 书房跨院). This requirement is captured for design continuity; it is NOT validated by any current validator and SHALL NOT be implemented in the `rebuild-jiangnan-mansion` change.

#### Scenario (FUTURE): A 4-进 mansion has a deeper 花园 with a 书房跨院

- **FUTURE: WHEN** a `chinese_mansion` compound is generated with `jin_count=4`
- **FUTURE: THEN** the lot SHALL add a 第四进 yard band behind the 后院
- **FUTURE: AND** the 第四进 SHALL contain a 书房跨院 parcel off the main axis.

### Requirement: A 月洞门 passage separates the 后院 from the 花园 and is the material boundary

A `chinese_mansion` compound SHALL place a `moon_gate_passage` parcel between
the 后院 and the 花园 — a voxel-walkable 穿墙通道 through a garden screen wall,
with the `moon_gate` motif carved into the surrounding wall cells so the opening
reads as a 圆洞门. The passage SHALL be the **material boundary**: cells on the
后院 side (including the formal-axis approach segment) SHALL resolve to
`PATH_FORMAL`; cells on the 花园 side SHALL resolve to `PATH_TOUR`. The tour
route's waypoint list SHALL begin at a cell on the 花园 side of the passage, so
no cell belongs to both the formal backbone and the tour polyline.

#### Scenario: The 月洞门 passage is voxel-walkable

- **WHEN** the `moon_gate_passage` parcel is placed
- **THEN** the passage cells SHALL be voxel-walkable per
  `courtyard-voxel-walkability`
- **AND** the surrounding wall cells SHALL carry the `moon_gate` motif.

#### Scenario: The material boundary is at the 月洞门

- **WHEN** the formal backbone and tour polyline cell sets are examined
- **THEN** every cell on the 后院 side of the passage SHALL resolve to
  `PATH_FORMAL`
- **AND** every cell on the 花园 side SHALL resolve to `PATH_TOUR`
- **AND** the intersection of the formal and tour cell sets SHALL be empty.

### Requirement: A 仆役房 service house lines the 夹道 when the lot has room

A `chinese_mansion` compound SHALL place a `service_house` sub-building (仆役房/
厨房/仓库) along the 倒座 `side_alley` when the lot has room for it. The service
house SHALL be a small, plain building (no decoration tier) reusing the existing
sub-building machinery. Its `door_info["front"]` SHALL be a mandatory path
endpoint, so the formal/service BFS reaches it through the 夹道. When the lot
has no room, the service house MAY be omitted.

#### Scenario: The service house is reachable through the alley

- **WHEN** a `service_house` is placed along the 夹道
- **THEN** its `door_info["front"]` cell SHALL be in the path endpoint set
- **AND** the formal/service BFS SHALL reach it through the alley cells.
