## MODIFIED Requirements

### Requirement: Path block is written at the natural surface y

The path block SHALL be written at the cell's natural surface y (the same y used
by `_place_yard_ground`):

- `y = -1` in the outer yard band.
- `y = -1` in the inner gate band.
- `y = plinth_h - 1` in the main yard band (on top of the main-yard plinth).

The path block SHALL resolve through one of **three** route slots, chosen by the
route the cell belongs to (not by its zone):

- Formal/service backbone cells → `PATH_FORMAL` (中轴, 规整青石路).
- Tour-route waypoint-segment cells → `PATH_TOUR` (游园, 苔石曲径).
- Waterside cells → `PATH_WATERSIDE` (水岸, 石阶 + 板桥).

The block SHALL be written with tags `["DETAIL", "GROUND", "PROTECTED"]` and
priority `DETAIL (70)`. The PROTECTED tag prevents the `material_variation`
pass from re-coloring the path.

The formal/service backbone SHALL remain the single-source shortest-path tree
from the street gate (unchanged). The tour route and waterside cells are
described in the ADDED requirements below.

#### Scenario: A formal backbone path cell is bluestone at y = -1

- **WHEN** a formal-route cell `(x, z)` lies on the formal backbone
- **THEN** the path pass SHALL write the `PATH_FORMAL` slot's primary block at
  compound `(x, -1, z)` (or `(x, plinth_h - 1, z)` in the main yard).

#### Scenario: A tour-route cell is mossy stone brick

- **WHEN** a tour-route cell `(x, z)` lies on the tour waypoint polyline
- **THEN** the path pass SHALL write the `PATH_TOUR` slot's primary block at the
  cell's natural surface y.

#### Scenario: An off-route yard cell keeps its ground tile

- **WHEN** a BFS-reached yard cell `(x, z)` is NOT on the formal backbone, the
  tour waypoint polyline, or a waterside cell
- **THEN** the path pass SHALL NOT write a path block at `(x, y, z)`
- **AND** the cell SHALL keep the ground tile written by `_place_yard_ground`.

### Requirement: Path network uses vanilla-only block ids

The `PATH_FORMAL`, `PATH_TOUR`, and `PATH_WATERSIDE` slots SHALL be defined on
the style JSON with vanilla-only block ids. The vanilla profile SHALL resolve
the formal path to `minecraft:smooth_stone` (or another `minecraft:` entry); the
tour path SHALL resolve to `minecraft:mossy_stone_bricks`; the waterside path
SHALL resolve to `minecraft:stone_brick_stairs` + `minecraft:oak_slab` (or
`spruce_slab`). The full profile MAY resolve to an external-mod id.

#### Scenario: Vanilla profile resolves the formal path to a vanilla block

- **WHEN** a compound is generated with `--profile vanilla`
- **THEN** every formal backbone cell SHALL be a `minecraft:` id from the
  `PATH_FORMAL` slot.

#### Scenario: Vanilla profile resolves the tour path to mossy stone bricks

- **WHEN** a compound is generated with `--profile vanilla`
- **THEN** every tour-route cell SHALL be `minecraft:mossy_stone_bricks`.

## ADDED Requirements

### Requirement: The tour route is a waypoint polyline, not a shortest-path tree

The tour route SHALL be routed as a polyline of scenic waypoints (the south face
of the `garden_rockery`, the nearest-shore corner of the `garden_pond`, and the
`garden_pavilion`), where each segment between consecutive waypoints is a
single-source shortest-path tree over the same `blocked` set as the formal BFS.
The first waypoint SHALL be a cell on the tour side of the `moon_gate_passage`.
Any segment whose shortest path would pass through a rockery or pond cell SHALL
be forced around it by adding those cells to the segment's `blocked` set. The
tour route SHALL produce a visible turn at each waypoint.

The tour polyline is a separate path set from the formal backbone: no cell SHALL
belong to both the formal backbone and the tour polyline.

#### Scenario: The tour route concatenates per-waypoint shortest paths

- **WHEN** the tour route is routed through waypoints W1 → W2 → W3
- **THEN** the route SHALL be the union of the W1→W2 shortest-path tree and the
  W2→W3 shortest-path tree
- **AND** the route SHALL turn at W2.

#### Scenario: A tour segment routes around a rockery

- **WHEN** the shortest path between two tour waypoints would pass through a
  rockery cell
- **THEN** that segment's `blocked` set SHALL include the rockery cells
- **AND** no tour cell SHALL coincide with a rockery cell.

#### Scenario: The tour route starts inside the 月洞门

- **WHEN** the tour waypoint list is assembled
- **THEN** the first waypoint SHALL be on the tour side of the
  `moon_gate_passage`
- **AND** the intersection of the formal-backbone cell set and the tour cell set
  SHALL be empty.

### Requirement: The waterside crossing is stairs plus a slab bridge

The 水岸 (`PATH_WATERSIDE`) zone SHALL write `stone_brick_stairs` descending to
the waterline, then a slab bridge of `oak_slab` or `spruce_slab` at the water
surface y crossing the pond to the 亭 or island rockery. The slab bridge SHALL
span the pond's narrowest water crossing between the 亭/island and the shore.
The deleted stepping-stone path SHALL NOT be restored: no waterside crossing
cell SHALL be `myvillage:rockery_block`.

#### Scenario: The waterside crossing uses slabs

- **WHEN** the 水岸 zone crosses a pond
- **THEN** the crossing cells SHALL be `oak_slab` or `spruce_slab` at the water
  surface y
- **AND** the descent cells SHALL be `stone_brick_stairs`.

#### Scenario: No stepping-stone rockery cells are written

- **WHEN** the waterside crossing is generated
- **THEN** no crossing cell SHALL be `myvillage:rockery_block`.
