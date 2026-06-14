## ADDED Requirements

### Requirement: Mezzanine partial floors cover a configured half-plane
A multi-story volume MAY carry a `mezzanine` meta field naming the
covered half-plane (`west` or `east`) and the mezzanine depth. The
generator SHALL place a partial floor slab over the configured
half-plane only, leaving the uncovered half-plane with an open
ceiling from the lower story up to the roof. The mezzanine story
SHALL be marked `mezzanine_story = True` so the regular floor slab
pass skips it.

#### Scenario: A tavern great hall has a mezzanine
- **WHEN** a volume with `stories == 2` and `mezzanine = {"covers": "west"}`
  is generated
- **THEN** `mezzanine_floor_pass` SHALL place slabs over the west
  half-plane at the story boundary
- **AND** the east half-plane SHALL remain open from the ground story
  floor to the roof
- **AND** `floor_slab_pass` SHALL skip the mezzanine story.

#### Scenario: A non-mezzanine volume skips mezzanine_floor_pass
- **WHEN** a volume without `mezzanine` meta is generated
- **THEN** `mezzanine_floor_pass` SHALL make no changes
- **AND** the regular `floor_slab_pass` SHALL place full slabs as
  before.

### Requirement: Attached tower volumes rise above the main roof
A massing graph MAY include a `tower_volume` node attached to a main
volume. A tower volume SHALL have a footprint smaller than the main
volume, SHALL declare `stories` greater than the main volume's
`stories`, and SHALL carry its own `story_wall_h` and stairwell meta.
The tower's lower stories SHALL share the main volume's wall material
so the attachment reads continuously; the tower's roof SHALL be
generated independently.

#### Scenario: A lord manor has an attached tower
- **WHEN** a `lord_manor` massing graph is generated
- **THEN** the graph SHALL include one `tower_volume` attached to the
  main volume
- **AND** the tower's `stories` SHALL be at least one greater than the
  main's `stories`
- **AND** the tower SHALL reserve its own stairwell footprint
  connecting its stories.

#### Scenario: A tower reserves its own stairwell
- **WHEN** a `tower_volume` with `stories > 1` is generated
- **THEN** `_reserve_stairwell` SHALL be called scoped to the tower
  node
- **AND** the tower stairwell SHALL NOT overlap the main volume's
  stairwell
- **AND** `stair_pass` SHALL place tower stairs independently from
  main-volume stairs.

### Requirement: Tower belfry carries the civic bell marker
A `tower_volume` MAY carry `belfry = True` meta. When present, the
interior furnishing pass SHALL place a `minecraft:bell` block hanging
under the tower roof at the top story, centered under the ridge.

#### Scenario: A belfry tower is furnished
- **WHEN** a `tower_volume` with `belfry = True` is furnished
- **THEN** the top story SHALL contain exactly one `minecraft:bell`
  blockstate
- **AND** the bell SHALL be attached to the underside of the roof
  blocks.
