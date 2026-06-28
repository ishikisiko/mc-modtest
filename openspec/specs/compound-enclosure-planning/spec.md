## Purpose

Defines the **building-enclosure planning model** that replaces the z-band-slice
model for `chinese_mansion`. Under this model, buildings are placed first as
orientation-bearing masses; yards are the negative space they enclose; 进 (jin)
are defined by which buildings face which enclosed space, not by pre-cut z-band
coordinates; and the path is a declared planning input, not a post-process patch.

This capability is realized for `chinese_mansion` by the
`rebuild-mansion-enclosure-plan` change. Propagation to `chinese_courtyard` and
`small_courtyard` is a deferred follow-up (see `docs/ai-kb/14_deferred_roadmap.md`).

## Requirements

### Requirement: A compound layout is a placement manifest, not a band-slice

A compound layout SHALL be produced by a planner that emits an ordered
**placement manifest**: a list of placements, each binding an archetype, a
facing, an anchor wall (the perimeter wall the building backs onto), an offset
along that wall, and an importance tier. The planner SHALL NOT compute building
positions from pre-cut z-band tuples or hard-coded band-relative coordinates
(`oy0 + N`, `lot_d - N`, etc.). The same manifest SHALL deterministically realize
to the same grid for a given `(seed, variant)`.

#### Scenario: The manifest encodes the form rule, not coordinates

- **WHEN** the planner produces a manifest for a `chinese_mansion` variant
- **THEN** every placement SHALL specify `(archetype, facing, anchor_wall, offset_along_wall)`
- **AND** no placement SHALL be derived from a z-band tuple or a hard-coded coordinate offset
- **AND** realizing the same manifest twice SHALL yield byte-identical grids.

### Requirement: Yards are derived as enclosed negative space

After the manifest is realized, a yard SHALL be the set of interior cells
enclosed by the buildings and perimeter walls that face it. A 进 (jin) SHALL be
identified by the set of buildings whose doors face a common enclosed space, not
by a z-band index. The planner SHALL NOT pre-allocate yard regions and then drop
buildings into them.

#### Scenario: The 主院 is the space enclosed by the 敞厅 and the two 厢房

- **WHEN** the manifest places `open_hall` (anchor north, facing south),
  `west_wing` (anchor west, facing east), and `east_wing` (anchor east, facing
  west)
- **THEN** the 主院 SHALL be the contiguous interior region enclosed by those
  three buildings' inward faces and the inner gates
- **AND** the 主院 region SHALL contain no building footprint cells.

### Requirement: The 进 sequence is derived from building adjacency

A 3-进 mansion SHALL realize the ordered sequence 前院 → 仪门 → 主院 → 二门 →
后院 → 花园, where each yard is the enclosed space of its facing-buildings and
each inner gate sits at the adjacency boundary between two consecutive yards.
The 进 ordering SHALL be validated by derived-yard adjacency, not by z-band
tuple comparison.

#### Scenario: 仪门 sits between the 前院 and 主院 yards

- **WHEN** the realized layout is examined
- **THEN** the 仪门 SHALL border the 前院 enclosed space on one side and the 主院
  enclosed space on the other
- **AND** no z-band tuple comparison SHALL be used to assert this.

### Requirement: Every building is placed against its anchor wall with the form-rule facing

Each placement SHALL bind the building to the perimeter wall dictated by its
role in the form rule: 正房/open_hall anchor north; 倒座/front_row and the
gate_house anchor south; 西厢 anchors west; 东厢 anchors east; 楼阁 anchors north
with an off-axis offset. The facing SHALL be the form-rule facing (per
`building-orientation-variants`): the door wall faces the yard the building
encloses.

#### Scenario: The 倒座 anchors the south wall and faces north

- **WHEN** the manifest places a `front_row` (倒座)
- **THEN** its anchor wall SHALL be south
- **AND** its facing SHALL be north (door toward the 前院)
- **AND** its door SHALL NOT open onto the street (south).

### Requirement: The path network is a declared planning input reaching every door

The path backbone SHALL be routed in the derived yard space from the gate-house
inner opening to every building's door-cell, where every door-cell is collected
as a mandatory path endpoint from the realized buildings' `door_info["front"]`.
The path SHALL be declared as part of the planning realization, not as a
post-process patch over an already-placed layout.

#### Scenario: Every door-cell is on the path

- **WHEN** the layout is realized
- **THEN** every `BuildingSlot.door_info["front"]` cell SHALL be on a path cell
- **AND** the path backbone SHALL connect the gate-house inner opening to every
  door-cell through the derived yard space
- **AND** no door-cell SHALL be unreachable from the gate-house inner opening.

### Requirement: A realized layout passes voxel-walkability end-to-end

Every realized `chinese_mansion` compound SHALL pass the preserved
`_voxel_walk_bfs` check: the player SHALL walk from the gate-house inner opening
to every door-cell, every garden endpoint, and every 楼阁 second-story stairwell
top via 3D STEP-ADJACENT cells. A realization that fails voxel-walkability SHALL
be rejected by the planner (and, because the derived yard is contiguous by
construction, this SHALL not occur for a well-formed manifest).

#### Scenario: The mansion is end-to-end walkable

- **WHEN** `validate_mansion` runs on a realized `chinese_mansion` compound
- **THEN** no `voxel_unreachable_door` error SHALL fire
- **AND** no `voxel_unreachable_endpoint` error SHALL fire
- **AND** no `voxel_step_cliff` error SHALL fire.