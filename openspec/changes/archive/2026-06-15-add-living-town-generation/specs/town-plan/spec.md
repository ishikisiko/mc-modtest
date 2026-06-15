## ADDED Requirements

### Requirement: The planner produces an enclosed town skeleton
The town planner SHALL produce a town plan whose perimeter is a closed boundary (城墙) with at least one gate (城门), and every gate SHALL lie on the perimeter boundary.

#### Scenario: A plan has a closed wall with gates on it
- **WHEN** a town plan is produced for a seed and site
- **THEN** the plan's perimeter SHALL form a single closed boundary
- **AND** the plan SHALL contain at least one gate
- **AND** every gate cell SHALL lie on the perimeter boundary, not in the interior.

### Requirement: A main-street spine carries exactly one dominant landmark
The plan SHALL include a main-street spine that connects gates through the town core, and SHALL designate exactly one parcel as the dominant landmark — top importance tier and tallest/highest roof grade — positioned to anchor or terminate the spine.

#### Scenario: The spine anchors a single dominant landmark
- **WHEN** a town plan is produced
- **THEN** a main-street spine SHALL connect at least one gate to the town core
- **AND** exactly one parcel SHALL be flagged as the dominant landmark
- **AND** that landmark SHALL be the highest importance tier in the plan.

### Requirement: Legibility comes from a center-to-edge gradient
The plan SHALL assign each parcel an importance tier that does not increase from the core toward the edge, and each tier SHALL carry a height/roof-grade selection hint where higher tiers map to taller massing and higher roof grades.

#### Scenario: Importance falls off toward the edge
- **WHEN** a town plan assigns parcel importance tiers
- **THEN** no edge parcel SHALL have a higher tier than a core parcel on the same radial
- **AND** each parcel's tier SHALL map to a height/roof-grade hint that rises with the tier.

### Requirement: The plan reserves intentionally-shaped negative space
The plan SHALL reserve named open regions (e.g. 院落, 井台/well-plaza) that are neither building parcels nor street cells, to be dressed later by the lived-in-tissue layer.

#### Scenario: Negative space is a first-class plan element
- **WHEN** a town plan is produced
- **THEN** it SHALL contain at least one reserved negative-space region
- **AND** each reserved region SHALL be disjoint from both building parcels and street cells.

### Requirement: The plan fits the site and accepts a soft brief
The planner SHALL fit the plan within the provided site bounds and record a ground reference for each parcel, and SHALL accept an optional functional brief (e.g. housing/market/civic/defense counts) as guidance it aims toward but is not required to satisfy.

#### Scenario: The plan stays within the site
- **WHEN** a town plan is produced for given site bounds
- **THEN** all plan cells SHALL lie within the site bounds
- **AND** each parcel SHALL record a ground reference level.

#### Scenario: A soft brief guides but does not block generation
- **WHEN** a plan is produced with a functional brief on a site that cannot fit every requested function
- **THEN** the planner SHALL bias the plan toward the brief
- **AND** it SHALL still produce a valid plan when the brief cannot be fully met.
