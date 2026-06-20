## MODIFIED Requirements

### Requirement: The planner partitions a town into named districts
The town planner SHALL partition the town footprint into a set of named districts, each tagged with a `kind` drawn from `gate`, `market`, `residential`, `civic_core`, and `fringe`. A district's shape SHALL be an explicit cell set which MAY be non-rectangular (stepped or kinked edges are permitted); the district's `bounds` field SHALL remain its axis-aligned bounding box for spatial queries only and SHALL NOT imply the district fills that box. Every plan cell that is not street or reserved negative space SHALL belong to exactly one district, and districts SHALL NOT overlap.

#### Scenario: A plan assigns every parcel to a district
- **WHEN** a cultivation town plan is produced for a seed and site
- **THEN** the plan SHALL contain at least one district of each of kinds `gate`, `market`, `residential`, and `civic_core`
- **AND** every parcel SHALL reference exactly one district
- **AND** no two districts SHALL share a cell.

#### Scenario: A district may have a non-rectangular cell set
- **WHEN** a district is produced with stepped or kinked boundaries
- **THEN** the district's cell set SHALL be the authoritative shape
- **AND** every parcel assigned to that district SHALL lie fully within that cell set
- **AND** the district's bounding box MAY contain cells that belong to a different district or to negative space.

### Requirement: District assignment is deterministic per seed and site
Given the same seed and site, the planner SHALL produce the same district partition, including each district's full cell set.

#### Scenario: Reproducible districts
- **WHEN** a districted town plan is produced twice for the same seed and site
- **THEN** the two district partitions SHALL be identical in cell sets, kinds, and briefs.
