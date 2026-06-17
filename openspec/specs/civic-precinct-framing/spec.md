# Civic Precinct Framing

## Purpose

This spec captures the civic-precinct framing for districted cultivation
town plans: a staged processional approach to the plaza, an enclosed
forecourt bounded by flanking halls and a colonnade, a walled precinct
with gates that bounds the fringe, and a validator enforcing the
enclosure and approach invariants.

## Requirements

### Requirement: The civic core stages a processional approach to the plaza

The plan SHALL fill the `civic_core` district's gate-facing entry band and forecourt with a staged approach sequence: a precinct gate at the core's gate-facing edge, followed by a dressed spirit way leading along the spine to the plaza, so the plaza is reached as the culmination of an ordered sequence rather than across bare ground. The approach SHALL preserve the existing ritual axis (plaza, paifang, lantern-lined spine) and its terminus at the shrine.

#### Scenario: The core entry is sequenced, not bare

- **WHEN** a districted cultivation town plan is produced
- **THEN** the `civic_core` district SHALL contain a precinct gate element at its gate-facing edge on the spine axis
- **AND** the band between that gate and the plaza SHALL carry dressed spirit-way elements (e.g. flanking statue/lantern/stele cells) recorded in the ritual-axis metadata
- **AND** the spirit way SHALL connect the precinct gate to the existing plaza along the spine
- **AND** the shrine SHALL remain the sole dominant landmark terminating the axis.

#### Scenario: The approach band is no longer empty

- **WHEN** the empty-cell metric is computed for the `civic_core` district
- **THEN** the contiguous gate-facing entry band that was previously unoccupied SHALL be occupied by approach elements (precinct gate, spirit way, or framing parcels)
- **AND** the count of unoccupied non-street core cells SHALL be lower than for the same seed and site without precinct framing.

### Requirement: The plaza is enclosed by flanking halls and a colonnade

The plan SHALL enclose the plaza forecourt by placing flanking side-hall parcels in the forecourt gaps between the main civic halls and a colonnade along the core's lateral edges, consuming the forecourt voids and the narrow edge slivers so the plaza reads as a bounded courtyard. Enclosure elements SHALL NOT overlap the spine, plaza, lanterns, or any landmark parcel, and SHALL NOT raise a volume above the dominant landmark.

#### Scenario: The forecourt and edge slivers are framed

- **WHEN** a districted cultivation town plan is produced
- **THEN** the plan SHALL contain side-hall parcels flanking the plaza forecourt and colonnade cells along the lateral edges of the `civic_core`
- **AND** these elements SHALL be disjoint from the spine, plaza, lantern cells, and every landmark parcel
- **AND** no enclosure element SHALL exceed the dominant landmark's importance tier or the civic-core storey-band maximum.

### Requirement: A precinct wall with gates wraps the core and bounds the fringe

The plan SHALL enclose the `civic_core` district with a precinct wall along at least its gate-facing and lateral edges, punctuated by at least one passable gate on the spine and one side gate per walled lateral edge, so the core reads as a walled compound and the wall forms the boundary between the core and the adjacent `fringe` district. The wall SHALL be reserved plan structure disjoint from parcels and streets except at its gate cells, and the spine SHALL remain traversable through the wall from the town gate to the shrine.

#### Scenario: The core is a walled compound with a defined fringe edge

- **WHEN** a districted cultivation town plan is produced
- **THEN** the plan SHALL record precinct-wall cells enclosing at least the gate-facing and lateral edges of the `civic_core`
- **AND** the wall SHALL include at least one gate cell on the spine and one side gate per walled lateral edge
- **AND** the wall cells SHALL be disjoint from parcels and from street cells except at gate cells
- **AND** the spine SHALL remain connected from the town gate through the precinct gate to the shrine.

#### Scenario: The wall separates core from fringe

- **WHEN** the `civic_core` shares a lateral edge with a `fringe` district
- **THEN** a precinct-wall run SHALL lie on that shared boundary
- **AND** no `fringe` spirit-field cell SHALL fall inside the walled precinct.

### Requirement: A precinct validator enforces enclosure and approach invariants

The town validator SHALL fail a plan whose `civic_core` lacks the precinct wall, the spine-axis precinct gate, or the staged approach between the gate and the plaza, reporting a precinct invariant; and SHALL pass when the core carries the wall, gate, approach, and an enclosed, still-traversable plaza.

#### Scenario: A missing precinct fails validation

- **WHEN** the validator runs on a town whose `civic_core` has no precinct wall or no spine-axis precinct gate
- **THEN** it SHALL fail and report a precinct invariant
- **AND** it SHALL pass when the core carries the wall, the spine gate, the staged approach, and a traversable spine from town gate to shrine.

#### Scenario: Framing keeps the precinct reproducible and bounded

- **WHEN** a plan with civic-precinct framing is produced twice for the same seed and site
- **THEN** the precinct wall, gates, approach, side halls, and colonnade SHALL be identical in both plans
- **AND** the realized town SHALL remain within the existing block-budget ceiling.
