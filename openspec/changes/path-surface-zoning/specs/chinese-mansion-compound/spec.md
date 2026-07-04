## MODIFIED Requirements

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
