## ADDED Requirements

### Requirement: A path/ground cell is classified along two orthogonal axes

Every path or ground cell in a surface-zoned compound SHALL be classified along
exactly two independent axes:

1. **Material axis (a property of the cell's zone):** the surface kind the cell
   belongs to, resolved through a style slot. The three routes (formal /
   service / tour) SHALL NOT carry material — a cell's material is decided
   solely by the zone it sits in.
2. **Shape axis (a property of the cell's route):** whether the cell lies on a
   straight (BFS-routed) or winding (waypoint-routed) segment. The shape axis
   SHALL NOT influence the material.

A formal-route cell and a tour-route cell sitting in the same zone SHALL resolve
to the same material. The routes are distinguished by the ordered sequence of
zones they pass through, not by per-route material.

#### Scenario: Two routes through the same zone share material

- **WHEN** a formal-route cell and a tour-route cell both sit in the 游园 zone
- **THEN** both cells SHALL resolve to the same `PATH_TOUR` material
- **AND** neither cell's material SHALL be decided by which route it is on.

#### Scenario: A route is a zone sequence, not a material assignment

- **WHEN** the formal route passes through the 中轴, 天井, and again 中轴 zones
- **THEN** the route's cells SHALL resolve to `PATH_FORMAL`, then
  `GROUND_YARD_HEART`, then `PATH_FORMAL` in that order
- **AND** the route SHALL NOT assign a single material to all its cells.

### Requirement: Six surface zones resolve through six style slots

A surface-zoned compound SHALL define six surface zones, each resolving through
a dedicated style slot:

| zone            | surface             | layer  | slot                |
|-----------------|---------------------|--------|---------------------|
| 中轴通途 (formal) | 规整青石路            | path   | `PATH_FORMAL`       |
| 天井/院心 (heart) | 天井灰砖铺地          | ground | `GROUND_YARD_HEART` |
| 廊道 (gallery)   | 廊下木石地面          | ground | `PATH_GALLERY`      |
| 夹道 (alley)     | 白墙夹道              | ground | `PATH_ALLEY`        |
| 游园 (tour)      | 苔石曲径              | path   | `PATH_TOUR`         |
| 水岸 (waterside) | 水边石阶与小桥        | path   | `PATH_WATERSIDE`    |

Each slot SHALL be defined on the style JSON with vanilla-only block ids; the
vanilla profile SHALL resolve every zone to a `minecraft:` id. The slots SHALL
respect the style's `forbidden_blocks` list — the 苔石 zone (`PATH_TOUR`) SHALL
resolve to `minecraft:mossy_stone_bricks`, NOT to `minecraft:cobblestone` or
`minecraft:mossy_cobblestone`.

#### Scenario: The tour zone resolves to mossy stone bricks

- **WHEN** a 游园 cell is generated under the vanilla profile
- **THEN** the cell SHALL resolve to `minecraft:mossy_stone_bricks`
- **AND** the cell SHALL NOT resolve to any cobblestone variant.

#### Scenario: A forbidden block is not used for any zone

- **WHEN** a style's `forbidden_blocks` forbids `minecraft:cobblestone`
- **THEN** no surface zone SHALL resolve to `minecraft:cobblestone`
- **AND** the zone SHALL resolve to a permitted alternative.

### Requirement: The 天井/院心 zone is the eave-drip ring, not the whole yard

The 天井/院心 (`GROUND_YARD_HEART`) zone SHALL be the 1-cell Chebyshev ring
around each `BuildingSlot.footprint` that is already classified `under_eave`
today, UNIONed with `covered_gallery` cells and `moon_platform` cells. The yard
interior (cells not in any of those sets) SHALL remain `open_sky` and resolve to
`GROUND_YARD_OPEN` (grass). The 天井 zone SHALL NOT cover the whole yard.

#### Scenario: The yard interior stays grass

- **WHEN** a yard cell is not in the eave-drip ring, a gallery, or a moon
  platform
- **THEN** the cell SHALL be classified `open_sky` and resolve to
  `GROUND_YARD_OPEN`.

#### Scenario: The eave-drip ring resolves to grey brick

- **WHEN** a yard cell is in the 1-cell Chebyshev ring around a building
  footprint
- **THEN** the cell SHALL be classified 天井/院心 and resolve to
  `GROUND_YARD_HEART`.

### Requirement: Formal and service routes stay straight; the tour route winds

The formal route and the service route SHALL be routed by the existing
single-source shortest-path BFS (straight). The tour route SHALL NOT be a
shortest-path tree — it SHALL be routed as a polyline of scenic waypoints, where
each segment between consecutive waypoints is a single-source shortest-path
tree, and any segment that would otherwise cut straight through a rockery or pond
SHALL be forced around it by an obstacle set. The tour route SHALL produce a
visible bend at each waypoint.

#### Scenario: The formal route is a straight single-source tree

- **WHEN** the formal route is routed from the gate to a door-cell
- **THEN** every formal cell SHALL lie on a single-source shortest-path tree
- **AND** the route SHALL contain no waypoint-induced bend.

#### Scenario: The tour route bends at each waypoint

- **WHEN** the tour route is routed through waypoints W1, W2, W3
- **THEN** the route SHALL be the concatenation of the W1→W2 and W2→W3 shortest
  paths
- **AND** the route SHALL turn at W2 (the W1→W2 and W2→W3 directions differ).

#### Scenario: A tour segment avoids cutting through a rockery

- **WHEN** the shortest path between two tour waypoints would pass through a
  rockery cell
- **THEN** that segment SHALL route around the rockery via the obstacle set
- **AND** no tour cell SHALL coincide with a rockery cell.

### Requirement: The 月洞门 passage is the material boundary between formal and tour

A surface-zoned mansion SHALL place a `moon_gate_passage` parcel — a
voxel-walkable 穿墙通道 through a garden wall — as the start of the tour route.
Cells on the formal side of the 月洞门 (including the formal-axis approach
segment) SHALL resolve to `PATH_FORMAL`; cells on the tour side SHALL resolve to
`PATH_TOUR`. The tour route's waypoint list SHALL begin at a cell inside the
月洞门, so no cell belongs to both the formal backbone and the tour waypoint
set. The passage SHALL apply the existing `moon_gate` motif to the surrounding
wall cells so the opening reads as a 圆洞门.

#### Scenario: The tour route starts inside the 月洞门

- **WHEN** the tour waypoint list is assembled
- **THEN** the first waypoint SHALL be a cell on the tour side of the
  `moon_gate_passage`
- **AND** no waypoint SHALL be on the formal side.

#### Scenario: No cell is both formal and tour

- **WHEN** the formal backbone cell set and the tour waypoint cell set are
  intersected
- **THEN** the intersection SHALL be empty.

#### Scenario: The passage is voxel-walkable and motif-carved

- **WHEN** the `moon_gate_passage` parcel is placed
- **THEN** the passage cells SHALL be voxel-walkable per `courtyard-voxel-walkability`
- **AND** the surrounding wall cells SHALL carry the `moon_gate` motif.

### Requirement: The 水边廊 is a shoreside covered-gallery placement variant

The 水边廊 SHALL reuse the existing `covered_gallery` parcel geometry, placed
along the pond shore (a placement rule), not a new parcel type. Its floor SHALL
resolve through `PATH_GALLERY` (wood-stone mix). It SHALL be a waypoint or
endpoint of the tour route.

#### Scenario: A shoreside gallery reuses covered_gallery geometry

- **WHEN** the 水边廊 is placed
- **THEN** it SHALL be a `covered_gallery` parcel whose cells lie along the pond
  shore
- **AND** its floor SHALL resolve to `PATH_GALLERY`.

### Requirement: The 仆役房 is a service-house archetype on the 夹道

The 生活 route's endpoint SHALL be a `service_house` sub-building (仆役房/厨房/
仓库), a small plain building placed along the 夹道. Its `door_info["front"]`
SHALL be a mandatory path endpoint, so the formal/service BFS reaches it through
the alley. In `chinese_mansion` the service house SHALL be present when the lot
has room; in `chinese_courtyard` it is optional; in `small_courtyard` it is
absent.

#### Scenario: The service house is a path endpoint

- **WHEN** a `service_house` is placed in a mansion with a 夹道
- **THEN** its `door_info["front"]` cell SHALL be in the path endpoint set
- **AND** the formal/service BFS SHALL reach it through the alley.

### Requirement: The waterside crossing is stairs + a slab bridge, not stepping stones

The 水岸 (`PATH_WATERSIDE`) zone SHALL write `stone_brick_stairs` descending to
the waterline, followed by a slab bridge (`oak_slab` or `spruce_slab` at the
water surface y) crossing the pond to the 亭 or island rockery. The deleted
stepping-stone path (`myvillage:rockery_block` cells) SHALL NOT be restored. The
slab bridge SHALL span the pond's narrowest water crossing between the
亭/island and the shore, reaching both shores.

#### Scenario: The waterside crossing uses slabs, not rockery blocks

- **WHEN** the 水岸 zone is generated across a pond
- **THEN** the crossing SHALL be `oak_slab` or `spruce_slab` at the water
  surface y
- **AND** no crossing cell SHALL be `myvillage:rockery_block`.

#### Scenario: The bridge reaches both shores

- **WHEN** the slab bridge is placed
- **THEN** the first bridge cell SHALL be adjacent to a shore cell
- **AND** the last bridge cell SHALL be adjacent to the opposite shore or the
  亭/island.

### Requirement: Each compound family realizes only the zones it has space for

The surface-zone model SHALL apply to `chinese_mansion`, `chinese_courtyard`,
and embedded `small_courtyard`. Each family SHALL realize only the zones its
layout contains: mansion gets the full six zones + 月洞门 + 水边廊 + 仆役房;
`chinese_courtyard` gets 中轴 + 天井 + 廊道 (+ 夹道/仆役房 if present);
`small_courtyard` gets 中轴 + 天井 only. A zone a family lacks SHALL be absent —
the model SHALL NOT force a zone into a layout that has no space for it.

#### Scenario: A small courtyard has no tour or waterside zones

- **WHEN** a `small_courtyard` is generated
- **THEN** it SHALL realize the 中轴 and 天井 zones only
- **AND** no 游园 or 水岸 cell SHALL be present.

#### Scenario: A mansion realizes all six zones

- **WHEN** a `chinese_mansion` is generated with a 花园
- **THEN** it SHALL realize all six surface zones
- **AND** it SHALL place a 月洞门 passage, a 水边廊, and (when the lot has room)
  a 仆役房.
