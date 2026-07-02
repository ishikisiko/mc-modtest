## ADDED Requirements

### Requirement: Compound validators assert the surface-zone materials

`validate_compound` and `validate_mansion` SHALL assert that each surface-zone
cell resolves to the slot its zone requires. The validator SHALL read each
non-building ground/path cell, classify it into one of the six zones, and check
the resolved block id against the zone's declared slot. A cell whose resolved
block does not match its zone's slot SHALL fail with
`surface_zone_material:<zone>:<cell>`.

The zone classification SHALL follow the `path-surface-zoning` rules: the
eave-drip ring → `GROUND_YARD_HEART`; gallery cells → `PATH_GALLERY`; alley
cells → `PATH_ALLEY`; formal-backbone overlay → `PATH_FORMAL`; tour-polyline
overlay → `PATH_TOUR`; waterside overlay → `PATH_WATERSIDE`; remaining open
yard → `GROUND_YARD_OPEN`.

#### Scenario: A heart-zone cell resolving to grass fails

- **WHEN** a validator inspects a cell in the eave-drip ring that resolved to
  `minecraft:grass_block` (the `GROUND_YARD_OPEN` slot) instead of
  `GROUND_YARD_HEART`
- **THEN** the validator SHALL fail with
  `surface_zone_material:GROUND_YARD_HEART:<cell>`.

#### Scenario: A tour cell resolving to cobblestone fails

- **WHEN** a validator inspects a tour-polyline cell that resolved to a
  cobblestone variant
- **THEN** the validator SHALL fail with `surface_zone_material:PATH_TOUR:<cell>`.

#### Scenario: A correctly-zoned cell passes

- **WHEN** a validator inspects a gallery cell that resolved to the
  `PATH_GALLERY` slot's block
- **THEN** the validator SHALL NOT report a `surface_zone_material` error for
  that cell.

### Requirement: Compound validators assert tour-route connectivity per segment

`validate_mansion` SHALL verify the tour route's connectivity by checking that
each waypoint-to-waypoint segment is a connected single-source shortest-path
tree (each segment's cells form a connected 4-neighbor path from the segment's
source waypoint to its target waypoint). A segment that is disconnected SHALL
fail with `tour_segment_disconnected:<from>-><to>`. A tour route with no
waypoints in a compound that has no 花园 SHALL be a no-op (the small-courtyard
and courtyard families without a garden have no tour route).

#### Scenario: A connected tour segment passes

- **WHEN** a validator inspects a tour segment from W1 to W2 and every cell on
  the segment is 4-neighbor-connected to W1
- **THEN** the validator SHALL NOT report `tour_segment_disconnected`.

#### Scenario: A disconnected tour segment fails

- **WHEN** a validator inspects a tour segment whose cells do not form a
  4-neighbor-connected path from source to target
- **THEN** the validator SHALL fail with
  `tour_segment_disconnected:<from>-><to>`.

#### Scenario: A compound with no garden has no tour check

- **WHEN** a `small_courtyard` or garden-less `chinese_courtyard` is validated
- **THEN** the tour-connectivity check SHALL be a no-op.

### Requirement: Mansion validator asserts the waterside bridge spans both shores

`validate_mansion` SHALL verify that the `PATH_WATERSIDE` slab bridge spans the
pond from one shore to the other (or to the 亭/island). The first bridge cell
SHALL be adjacent to a shore cell; the last bridge cell SHALL be adjacent to the
opposite shore or the 亭/island rockery. A bridge that does not reach both ends
SHALL fail with `waterside_bridge_incomplete:<first|last>`. A mansion whose
garden has no pond crossing (no 亭/island on the water) SHALL skip the check.

#### Scenario: A spanning bridge passes

- **WHEN** a validator inspects a slab bridge whose first cell is shore-adjacent
  and whose last cell is 亭/island-adjacent
- **THEN** the validator SHALL NOT report `waterside_bridge_incomplete`.

#### Scenario: A bridge that does not reach the far shore fails

- **WHEN** a validator inspects a slab bridge whose last cell is not adjacent to
  the opposite shore or the 亭/island
- **THEN** the validator SHALL fail with `waterside_bridge_incomplete:last`.

### Requirement: Mansion validator guards against cluttered waterside composition

`validate_mansion` SHALL verify that the pond, island rockery, waterside bridge,
and 水边廊 remain visually separated. The 水边廊 SHALL be exactly one short,
straight, two-cell-deep `covered_gallery` footprint; it SHALL NOT overlap pond
water, the island rockery, or the bridge; and its recorded water-edge row SHALL
be 4-adjacent to pond water. A violation SHALL fail with
`waterside_gallery_clutter:<reason>`.

`validate_mansion` SHALL also verify that lily pads do not occupy the
bridge/gallery clear-water lanes. A violation SHALL fail with
`pond_lily_clutter:<cell>`.

`validate_mansion` SHALL also verify that the `garden_pavilion` is a waterside
pavilion: its footprint SHALL be dry and at least one footprint cell SHALL be
4-adjacent to pond water. A detached or missing pavilion SHALL fail with
`garden_pavilion_detached_from_pond:<reason>`.

#### Scenario: A ragged all-shore gallery fails

- **WHEN** the 水边廊 footprint is not a short straight two-cell-deep strip
- **THEN** the validator SHALL fail with
  `waterside_gallery_clutter:not_straight` or
  `waterside_gallery_clutter:size:<count>`.

#### Scenario: A gallery overlapping the island or bridge fails

- **WHEN** the 水边廊 footprint overlaps the island rockery or waterside bridge
- **THEN** the validator SHALL fail with
  `waterside_gallery_clutter:overlaps_rockery:<cells>` or
  `waterside_gallery_clutter:overlaps_bridge:<cells>`.

#### Scenario: Lily pads in the clear-water lane fail

- **WHEN** a lily pad lies on a bridge cell or in the clear-water lane adjacent
  to the bridge or 水边廊 water-edge row
- **THEN** the validator SHALL fail with `pond_lily_clutter:<cell>`.

#### Scenario: A detached garden pavilion fails

- **WHEN** the `garden_pavilion` footprint is not 4-adjacent to pond water
- **THEN** the validator SHALL fail with
  `garden_pavilion_detached_from_pond:<center>`.
