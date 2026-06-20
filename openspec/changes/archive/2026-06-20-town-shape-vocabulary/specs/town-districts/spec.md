## ADDED Requirements

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
