## MODIFIED Requirements

### Requirement: The 花园 is a non-axis parcel zone behind the 后院

The 花园 band SHALL span the full interior lot width (excluding the perimeter
wall) and SHALL contain at least one of each: `garden_pond`, `garden_rockery`,
`garden_pavilion`, plus a `PATH_TOUR` 曲径 connecting the 月洞门 passage to each
feature. The `garden_pavilion` SHALL sit on a dry pond bank with its footprint
4-adjacent to pond water so it reads as a 水亭 rather than a detached garden
building. The `garden_pavilion` roof SHALL be a contiguous thin sloped-eave
roof cap supported by light posts and SHALL NOT use raw/default stair blocks as
a floating ridge cap or a bulky upper slab mass. A 水边廊 (shoreside
`covered_gallery` variant) SHALL line one clean pond-shore run as a short
straight two-cell-deep gallery, and a `PATH_WATERSIDE` stairs + slab bridge
SHALL cross the pond to the 亭 or island rockery. The 水边廊 SHALL NOT overlap
the pond water, the island rockery, or the bridge, SHALL NOT consume the whole
freeform shoreline, and its waterside roof SHALL NOT cover the whole two-cell
footprint as a closed wooden shed. The 花园 SHALL NOT contain full enclosed
buildings (亭 are open-sided, per `garden-rockery`).

The tour route through the 花园 SHALL be a waypoint polyline (not a
shortest-path tree), routed from the `moon_gate_passage` through the rockery
south face, the nearest pond shore, and the 亭, with each segment a shortest
path and obstacle-avoidance forcing any straight segment to curve around the
rockery/pond.

#### Scenario: The 花园 has pond + rockery + pavilion + waterside gallery

- **WHEN** the 花园 band is generated with `garden_scale ∈ {small, large}`
- **THEN** the band SHALL contain at least one `garden_pond`, one
  `garden_rockery`, and one `garden_pavilion`
- **AND** a shoreside `covered_gallery` (水边廊) SHALL line one short straight
  pond-shore run
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

#### Scenario: The pavilion roof is not a floating default-stair cap

- **WHEN** the `garden_pavilion` roof is placed
- **THEN** the roof SHALL form a contiguous cap over the pavilion columns
- **AND** no raw/default stair state SHALL be used as a detached ridge cap above
  the pavilion
- **AND** the upper cap SHALL be smaller than the lower eave layer so the roof
  does not read as a heavy wooden box.

#### Scenario: The waterside gallery remains open rather than shed-like

- **WHEN** the 水边廊 is rendered on a short two-cell-deep footprint
- **THEN** it SHALL still have a roof and balustrade
- **AND** the roof SHALL NOT cover every footprint cell.

#### Scenario: The pond composition stays visually separated

- **WHEN** the 水边廊, pond, island rockery, and waterside bridge are generated
- **THEN** the 水边廊 footprint SHALL NOT overlap pond water, the island rockery,
  or the bridge
- **AND** bridge/gallery clear-water lanes SHALL contain no lily pads.

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

## ADDED Requirements

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
