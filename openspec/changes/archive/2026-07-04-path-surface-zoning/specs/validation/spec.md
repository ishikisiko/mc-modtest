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
and reference-style `garden_pavilion` remain visually separated. A separate
pond-side `waterside_gallery` / 水边廊 shed SHALL NOT be present. A violation
SHALL fail with `waterside_gallery_clutter:<reason>`.

`validate_mansion` SHALL also verify that lily pads do not occupy the
bridge clear-water lane. A violation SHALL fail with `pond_lily_clutter:<cell>`.

`validate_mansion` SHALL also verify that the `garden_pavilion` is a waterside
pavilion: its footprint SHALL be dry and at least one footprint cell SHALL be
4-adjacent to pond water. A detached or missing pavilion SHALL fail with
`garden_pavilion_detached_from_pond:<reason>`.

`validate_mansion` SHALL also verify that the `garden_pavilion` carries the
reference-pavilion parts: raised stone base, wood deck, heavy non-fence columns,
double eaves, lanterns, and stone roof ornaments. A mismatch SHALL fail with
`garden_pavilion_reference_mismatch:<reason>`.

`validate_mansion` SHALL also verify that the reference pavilion is not an
edge-corner object and is not visually boxed in: its center x SHALL be near the
mansion axis, its frontage/side scenic openings SHALL be clear, and its
moon-gate-screen water backdrop opening SHALL be clear from y=0 through y=7. A
blocked or missing opening SHALL fail with
`garden_pavilion_reference_mismatch:<reason>`.

`validate_mansion` SHALL also verify that the reference pavilion scene carries
the supplied image's landscape context: visible side water, foreground
flowers/grass, a soft approach path, bamboo, and a green backdrop. A missing
landscape component SHALL fail with
`garden_pavilion_reference_mismatch:<reason>`.

#### Scenario: A reintroduced waterside shed fails

- **WHEN** a parcel with id `waterside_gallery` is present in the pond
  composition
- **THEN** the validator SHALL fail with
  `waterside_gallery_clutter:unexpected:<count>`.

#### Scenario: Lily pads in the clear-water lane fail

- **WHEN** a lily pad lies on a bridge cell or in the clear-water lane adjacent
  to the bridge
- **THEN** the validator SHALL fail with `pond_lily_clutter:<cell>`.

#### Scenario: An underspecified reference pavilion fails

- **WHEN** the garden pavilion is missing a heavy-column, lantern, double-eave,
  stone-base, or roof-ornament requirement
- **THEN** the validator SHALL fail with
  `garden_pavilion_reference_mismatch:<reason>`.

#### Scenario: A detached garden pavilion fails

- **WHEN** the `garden_pavilion` footprint is not 4-adjacent to pond water
- **THEN** the validator SHALL fail with
  `garden_pavilion_detached_from_pond:<center>`.
