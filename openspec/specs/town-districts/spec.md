# Town Districts

## Purpose

This spec captures the districted macro layout for the cultivation town plan: a partition of the town footprint into named districts (`gate`, `market`, `residential`, `civic_core`, `fringe`) each binding a density, storey band, material register, and archetype roster drawn from the active settlement group's district brief.

## Requirements

### Requirement: The planner partitions a town into named districts
The town planner SHALL partition the town footprint into a set of named districts, each tagged with a `kind` drawn from `gate`, `market`, `residential`, `civic_core`, and `fringe`. Every plan cell that is not street or reserved negative space SHALL belong to exactly one district, and districts SHALL NOT overlap.

#### Scenario: A plan assigns every parcel to a district
- **WHEN** a cultivation town plan is produced for a seed and site
- **THEN** the plan SHALL contain at least one district of each of kinds `gate`, `market`, `residential`, and `civic_core`
- **AND** every parcel SHALL reference exactly one district
- **AND** no two districts SHALL share a cell.

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
Given the same seed and site, the planner SHALL produce the same district partition.

#### Scenario: Reproducible districts
- **WHEN** a districted town plan is produced twice for the same seed and site
- **THEN** the two district partitions SHALL be identical in bounds, kinds, and briefs.
