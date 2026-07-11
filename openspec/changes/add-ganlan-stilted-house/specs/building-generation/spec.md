## ADDED Requirements

### Requirement: Raised-floor stilt archetypes expose support metadata
The building generator SHALL support a raised-floor stilt-house archetype path
for the Ganlan reference slice. Generated massing or compound metadata SHALL
record the raised living-floor height, support-post locations, support plane,
entry access route, and underside-clearance intent so validation can inspect the
load-bearing stilt grammar.

#### Scenario: Stilt-house metadata is generated
- **WHEN** a Ganlan stilt-house sample is generated
- **THEN** the generated graph or report SHALL record `raised_floor_y`,
  support-post locations, access-route metadata, and underside-clearance status
- **AND** block placement SHALL use those metadata values rather than hardcoded
  style-name branches.

### Requirement: Raised-floor generation preserves walkable access
The raised-floor generator SHALL create a walkable stair or stepped route from
the path/support plane to the living floor or veranda without blocking the door
or filling the underside.

#### Scenario: Raised entry is generated
- **WHEN** a Ganlan stilt-house sample has an entry door above the support plane
- **THEN** the generator SHALL place a stair, ladder, or stepped landing route
  that reaches the entry
- **AND** the route SHALL not fill the entire underside of the house.
