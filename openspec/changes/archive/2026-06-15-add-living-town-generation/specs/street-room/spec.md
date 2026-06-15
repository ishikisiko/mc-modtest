## ADDED Requirements

### Requirement: Streets present continuous active frontage
Parcel edges that face a street SHALL present an active frontage (shopfront, opening, or counter) rather than a blank wall, and along a main street the frontage SHALL be continuous, with no blank-wall run longer than a configured threshold.

#### Scenario: A main street is lined with active frontage
- **WHEN** the street-room layer processes a main street
- **THEN** each street-facing parcel edge SHALL present a shopfront or opening
- **AND** no continuous blank-wall run along the main street SHALL exceed the configured threshold.

### Requirement: Streets and lanes follow a width and paving hierarchy
The main street SHALL be wider than lanes and alleys and SHALL use a higher-grade paving material, so street rank is legible from the ground.

#### Scenario: Rank is readable from width and paving
- **WHEN** the street network is realized
- **THEN** the main street width SHALL exceed lane width
- **AND** the main street paving grade SHALL be higher than lane paving.

### Requirement: Streets and squares are furnished as rooms
Streets and squares SHALL receive furniture (lanterns, stalls, benches, signboards) scaled to their rank, with the main-street mouth and squares more densely furnished than back lanes, and furniture SHALL NOT block the traversable path. Street-room furniture SHALL be anchored to street or square ground cells, not parcel or template roof cells.

#### Scenario: A furnished street stays traversable
- **WHEN** the street-room layer furnishes a street network
- **THEN** the main-street mouth or square SHALL be more densely furnished than back lanes
- **AND** the traversable path along every street SHALL remain clear of furniture.
- **AND** no street-room furniture SHALL be placed on a parcel template roof.
