# Multi-Story Massing

## Purpose

This spec captures the current multi-story massing capability used by larger generated buildings and compatible compound sub-buildings.

## Requirements

### Requirement: Massing supports stacked stories
The massing graph SHALL represent a building as one or more vertically stacked stories. A volume node SHALL carry a `stories` count of at least 1 and a per-story wall height, and its total height SHALL be `foundation_h + stories * story_wall_h` plus the roof. A single-story building with `stories == 1` SHALL remain equivalent to the current single-volume behavior.

#### Scenario: A two-story volume is built
- **WHEN** an archetype builds a main volume with `stories == 2` and per-story wall height `wall_h`
- **THEN** the volume's wall portion SHALL span `2 * wall_h` cells above the foundation
- **AND** the massing graph SHALL record `stories == 2` on the node.

#### Scenario: A single-story volume is unchanged
- **WHEN** an archetype builds a main volume with `stories == 1`
- **THEN** the generated wall height and roof SHALL match the existing single-story massing behavior.

### Requirement: Floor slabs separate stories
For a volume with `stories > 1`, the generator SHALL place a floor slab between each pair of adjacent stories at the top of the lower story's wall height.

#### Scenario: A floor slab is placed between two stories
- **WHEN** a volume has `stories == 2`
- **THEN** a floor slab SHALL be placed across the interior footprint at the boundary between story 1 and story 2
- **AND** the slab SHALL leave a stairwell opening so the stories are connected.

### Requirement: A stairwell connects stories with aligned openings
For a volume with `stories > 1`, the generator SHALL place a stairwell that connects each story to the one above. The stairwell column position SHALL be chosen during massing and SHALL NOT overlap the door bay or planned window bays. Each per-story floor opening SHALL be vertically aligned so the stairwell is continuous and walkable.

#### Scenario: The stairwell avoids door and windows
- **WHEN** the stairwell position is chosen for a multi-story volume
- **THEN** its footprint SHALL NOT overlap the front door bay
- **AND** it SHALL NOT overlap a planned window bay.

#### Scenario: Floor openings align vertically
- **WHEN** a building has three stories
- **THEN** the floor opening between story 1 and story 2 SHALL be vertically aligned with the opening between story 2 and story 3
- **AND** the stair geometry SHALL allow movement from the ground story up to the top story.

### Requirement: Facade plans one window band per story
Facade planning SHALL produce one window band per story for a multi-story volume. By default, window bands SHALL be vertically aligned across stories. Each per-story band SHALL still satisfy the existing facade rules: openings at least two cells from wall ends, away from the door bay, posts, and occluded intervals.

#### Scenario: A two-story facade is planned
- **WHEN** a two-story volume's front wall is planned
- **THEN** the facade plan SHALL include a window band on story 1 and a window band on story 2
- **AND** by default the story 2 windows SHALL share the along-wall positions of the story 1 windows.

#### Scenario: Per-story bands respect corner and door rules
- **WHEN** a window is placed in any story's band
- **THEN** its along-wall coordinate SHALL be at least two cells from both wall ends
- **AND** it SHALL NOT overlap the door bay, a post position, or an occluded interval.


### Requirement: Mezzanine partial floors cover a configured half-plane
A multi-story volume MAY carry a `mezzanine` meta field naming the covered half-plane (`west` or `east`) and the mezzanine depth. The generator SHALL place a partial floor slab over the configured half-plane only, leaving the uncovered half-plane with an open ceiling from the lower story up to the roof. The mezzanine story SHALL be marked `mezzanine_story = True` so the regular floor slab pass skips it.

#### Scenario: A tavern great hall has a mezzanine
- **WHEN** a volume with `stories == 2` and `mezzanine = {"covers": "west"}` is generated
- **THEN** `mezzanine_floor_pass` SHALL place slabs over the west half-plane at the story boundary
- **AND** the east half-plane SHALL remain open from the ground story floor to the roof
- **AND** `floor_slab_pass` SHALL skip the mezzanine story.

#### Scenario: A non-mezzanine volume skips mezzanine_floor_pass
- **WHEN** a volume without `mezzanine` meta is generated
- **THEN** `mezzanine_floor_pass` SHALL make no changes
- **AND** the regular `floor_slab_pass` SHALL place full slabs as before.

### Requirement: Attached tower volumes rise above the main roof
A massing graph MAY include a `tower_volume` node attached to a main volume. A tower volume SHALL have a footprint smaller than the main volume, SHALL declare `stories` greater than the main volume's `stories`, and SHALL carry its own `story_wall_h` and stairwell meta. The tower's lower stories SHALL share the main volume's wall material so the attachment reads continuously; the tower's roof SHALL be generated independently.

#### Scenario: A lord manor has an attached tower
- **WHEN** a `lord_manor` massing graph is generated
- **THEN** the graph SHALL include one `tower_volume` attached to the main volume
- **AND** the tower's `stories` SHALL be at least one greater than the main's `stories`
- **AND** the tower SHALL reserve its own stairwell footprint connecting its stories.

#### Scenario: A tower reserves its own stairwell
- **WHEN** a `tower_volume` with `stories > 1` is generated
- **THEN** `_reserve_stairwell` SHALL be called scoped to the tower node
- **AND** the tower stairwell SHALL NOT overlap the main volume's stairwell
- **AND** `stair_pass` SHALL place tower stairs independently from main-volume stairs.

### Requirement: Tower belfry carries the civic bell marker
A `tower_volume` MAY carry `belfry = True` meta. When present, the interior furnishing pass SHALL place a `minecraft:bell` block hanging under the tower roof at the top story, centered under the ridge.

#### Scenario: A belfry tower is furnished
- **WHEN** a `tower_volume` with `belfry = True` is furnished
- **THEN** the top story SHALL contain exactly one `minecraft:bell` blockstate
- **AND** the bell SHALL be attached to the underside of the roof blocks.
