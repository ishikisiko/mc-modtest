## Purpose

Defines the **facing-variant system** that lets a sub-building's door land on the
wall that faces its yard, solving the "every building faces south → doors don't
face the yard → enclosure reads as 杂乱无章" defect. The mechanism is
**door-wall relocation** (not full rotation, not x-mirror): the building's
volume, footprint, and roof ridge are unchanged because they are already
geometrically correct for the slot; only the door wall is relocated to the
yard-facing side.

## ADDED Requirements

### Requirement: A sub-building carries a facing that selects its door wall

A sub-building built for a compound SHALL carry a `facing ∈ {south, north, east,
west}` (default `south`, preserving current behavior) that selects which wall
carries the door. The door-placement function (`_door`) SHALL accept a `wall`
argument and place the door on the chosen wall, recording
`graph.meta["door"] = {"volume", "wall", "pos"}` with the wall the door is on.
The facing SHALL NOT rotate or mirror the building volume, footprint, or roof
ridge — those are unchanged.

#### Scenario: A west-facing 厢房 has its door on the west wall

- **WHEN** a `side_wing` is built with `facing=west`
- **THEN** the door SHALL be placed on the west (`-x`) wall
- **AND** the `door_info["front"]` cell SHALL be at `(x0 - 1, y, door_z)`
- **AND** the volume, footprint, and `roof_axis="z"` SHALL be identical to a
  south-facing `side_wing`.

#### Scenario: Default facing preserves current south-facing behavior

- **WHEN** a sub-building is built with no `facing` specified
- **THEN** the door SHALL be on the front (low-z) wall
- **AND** the resulting grid SHALL be byte-identical to the pre-change output
  for that archetype and seed.

### Requirement: The facade and door-info render on the facing-selected wall

The facade-detail pass SHALL read the door's selected wall from
`graph.meta["door"]["wall"]` and carve the doorway on that wall. The
`door_info["front"]` cell SHALL be computed from the door wall's outward
direction: south-facing → `(door_x, y, z0 - 1)`; north-facing →
`(door_x, y, z1 + 1)`; west-facing → `(x0 - 1, y, door_z)`; east-facing →
`(x1 + 1, y, door_z)`.

#### Scenario: A north-facing 倒座 renders its door and front cell correctly

- **WHEN** a `front_row` (倒座) is built with `facing=north`
- **THEN** the doorway SHALL be carved on the back (high-z) wall
- **AND** `door_info["front"]` SHALL be at `(door_x, y, z1 + 1)`
- **AND** the door SHALL face the 前院, not the street.

### Requirement: The form rule determines facing per role, not the variant table

A `chinese_mansion` building's facing SHALL be determined by its role in the
form rule, not by the variant template table:

| role | archetype | facing |
|------|-----------|--------|
| 正房 / 敞厅 | `main_hall` / `open_hall` | south |
| 倒座 | `front_row` | north |
| 西厢 | `side_wing` (west slot) | east |
| 东厢 | `side_wing` (east slot) | west |
| 门屋 | `gate_house` | inward (north, toward 前院) |
| 楼阁 | `tower_house` | toward its enclosing yard |

The variant template table (`gate_form`, `garden_scale`, `tower_count`,
`roof_grade`, `open_hall_bays`, `courtyard_size`) SHALL NOT include a facing
axis. A variant SHALL NOT be able to randomize a role's facing — e.g. a 倒座
SHALL always face north.

#### Scenario: The 倒座 facing is fixed by role, not by seed

- **WHEN** any `chinese_mansion` variant is generated with any seed
- **THEN** the 倒座's facing SHALL be north
- **AND** no template row SHALL be able to produce a south-facing 倒座.

### Requirement: The 敞厅 open facade is exempt from the door-wall rule

The 敞厅 (`open_hall`) SHALL resolve its yard-side through the `FACADE_OPEN` slot
(columns + open eave, no full-height front wall) per `cultivation-form-vocabulary`.
Because the 敞厅 has no front wall, the door-wall selection SHALL be skipped for
it; its `door_info` SHALL be the open-front center. The 敞厅's facing SHALL still
be recorded as `south` for the enclosure planner's anchor-wall logic.

#### Scenario: The 敞厅 skips door-wall logic but records south facing

- **WHEN** an `open_hall` is built
- **THEN** no doorway SHALL be carved (the front is open per `FACADE_OPEN`)
- **AND** `door_info["front"]` SHALL be the open-front center cell
- **AND** the building's facing SHALL be recorded as `south`.

### Requirement: Facing does not disturb the stairwell or colonnade

When the door wall is relocated, the door-placement SHALL honor the existing
stairwell-reservation and colonnade logic so the door does not collide with the
stairwell bay (for multi-story buildings) or a one-sided colonnade. The
`_door` `avoid` parameter SHALL remain effective per selected wall.

#### Scenario: A 2-story 楼阁 door does not collide with its stairwell

- **WHEN** a `tower_house` (stories=2) is built with a non-south facing
- **THEN** the door SHALL be placed on the selected wall at a bay that does not
  overlap the reserved stairwell
- **AND** the stairwell SHALL remain intact.
