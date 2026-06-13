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
