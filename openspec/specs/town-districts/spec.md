# Town Districts

## Purpose

This spec captures the districted macro layout for the cultivation town plan: a partition of the town footprint into named districts (`gate`, `market`, `residential`, `civic_core`, `fringe`) each binding a density, storey band, material register, and archetype roster drawn from the active settlement group's district brief.

## Requirements

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

### Requirement: Each district carries a density, storey band, and material register
Every district SHALL bind a `density` target, a `storey_band` (minimum and maximum building floors), and a `material_register` identifier, supplied by the settlement group's district brief rather than hardcoded in the planner.

#### Scenario: District briefs drive parcel hints
- **WHEN** the planner subdivides a district into parcels
- **THEN** each parcel SHALL inherit its district's density, storey band, and material register
- **AND** no parcel's storey count hint SHALL fall outside its district's storey band.

### Requirement: Scale concentrates at the civic core
The plan SHALL make the `civic_core` district the highest importance tier and assign it the tallest storey band and densest target, with the `fringe` district the loosest, producing a legible hierarchy from core to edge.

#### Scenario: Core outranks fringe
- **WHEN** a districted town plan is produced
- **THEN** the `civic_core` district SHALL hold the dominant landmark and the top importance tier
- **AND** the `civic_core` storey-band maximum SHALL be greater than or equal to every other district's maximum
- **AND** the `fringe` district density SHALL be the lowest of all districts.

### Requirement: District assignment is deterministic per seed and site
Given the same seed and site, the planner SHALL produce the same district partition, including each district's full cell set.

#### Scenario: Reproducible districts
- **WHEN** a districted town plan is produced twice for the same seed and site
- **THEN** the two district partitions SHALL be identical in cell sets, kinds, and briefs.

### Requirement: Outer district cells may be clipped to the perimeter shape
For any plan whose perimeter vocabulary entry is not the plain `square`, the planner SHALL emit each `market`, `residential`, and `fringe` district whose axis-aligned bounding box reaches the perimeter curve with an authoritative `cells` set equal to the intersection of the district's AABB and the perimeter interior. The `civic_core` district SHALL remain rectangular (its `cells` set equals its full AABB) so the civic-precinct derivation stays coupled to `core.bounds`. Every district, clipped or not, SHALL still carry its AABB as `bounds` for sorting and spatial queries.

#### Scenario: A non-square perimeter clips its outer districts
- **WHEN** a town plan is produced with any perimeter vocabulary entry other than `square`
- **THEN** each outer district whose AABB reaches the perimeter curve SHALL have a `cells` set equal to `AABB ∩ perimeter_interior`
- **AND** the `civic_core` district SHALL remain a full rectangle (its `cells` equals its AABB)
- **AND** every district SHALL still carry its AABB as `bounds`.

#### Scenario: A square perimeter leaves all districts rectangular
- **WHEN** a town plan is produced with the `square` perimeter vocabulary entry
- **THEN** every district's `cells` set SHALL equal its full AABB (no clipping)
- **AND** the plan SHALL behave identically to the pre-change square-town plan at the district level.

#### Scenario: Parcels respect clipped district cells
- **WHEN** a district is clipped to the perimeter shape and then subdivided into parcels
- **THEN** every emitted parcel SHALL lie entirely within the clipped `cells` set
- **AND** no parcel SHALL cross the perimeter boundary
- **AND** no parcel SHALL be thinner than the minimum footprint, after the existing retry/shift path resolves boundary fragments.

#### Scenario: The civic-core precinct contract is preserved under clipping
- **WHEN** a plan clips its outer districts to a curvy perimeter
- **THEN** the civic-precinct wall, colonnade, spirit-way, and side-hall bounds SHALL still derive from `civic_core.bounds` (the AABB)
- **AND** no precinct cell SHALL fall outside the `civic_core` district's `cells` set.
