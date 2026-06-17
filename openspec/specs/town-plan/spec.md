# Town Plan

## Purpose

This spec captures the deterministic macro planner for on-demand living-town generation.

## Requirements

### Requirement: The planner produces an enclosed town skeleton
The town planner SHALL produce a plan whose perimeter is a closed boundary with at least one gate, and every gate SHALL lie on the perimeter boundary.

#### Scenario: A plan has a closed wall with gates on it
- **WHEN** a town plan is produced for a seed and site
- **THEN** the plan's perimeter SHALL form a single closed boundary
- **AND** the plan SHALL contain at least one gate
- **AND** every gate cell SHALL lie on the perimeter boundary.

### Requirement: A main-street spine carries exactly one dominant landmark
The plan SHALL include a main-street spine that connects at least one gate through the town core and SHALL designate exactly one dominant-landmark parcel at the top importance tier.

#### Scenario: The spine anchors a single dominant landmark
- **WHEN** a town plan is produced
- **THEN** a main-street spine SHALL connect at least one gate to the town core
- **AND** exactly one parcel SHALL be flagged as the dominant landmark
- **AND** that landmark SHALL be the highest importance tier in the plan.

### Requirement: Cultivation towns terminate a ritual axis at the shrine
For the cultivation town plan, the dominant landmark SHALL be a `civic_core` ritual structure (the shrine), and the ritual axis — front plaza, paifang gate, and lantern-lined approach — SHALL be expressed within the `civic_core` district rather than as the whole-town organizing spine. The main spine SHALL connect a gate to the civic core, and the axis SHALL terminate at the shrine inside that core.

#### Scenario: The shrine anchors the axis within the civic core
- **WHEN** a cultivation town plan is produced
- **THEN** the plan SHALL contain a shrine parcel in the `civic_core` district flagged as the sole dominant landmark
- **AND** the plan SHALL record ritual-axis metadata naming the shrine as the terminus
- **AND** a plaza and paifang gate SHALL front the shrine within the civic core
- **AND** the approach SHALL include lantern cells flanking the axis.

### Requirement: Parcel tiers carry massing hints
The plan SHALL assign each parcel an importance tier derived from its district kind, and each tier SHALL carry height and roof-grade hints. The `civic_core` district SHALL hold the top tier; the shrine terminus MAY be top tier even though it sits at the end of the ritual axis rather than at the geometric center.

#### Scenario: Importance maps to hints via district
- **WHEN** a town plan assigns parcel importance tiers
- **THEN** each parcel's tier SHALL be derived from its district kind
- **AND** each tier SHALL map to a height and roof-grade hint
- **AND** no parcel SHALL exceed the supported top importance tier.

### Requirement: The plan reserves intentionally-shaped negative space
The plan SHALL reserve named open regions that are neither building parcels nor street cells.

#### Scenario: Negative space is a first-class plan element
- **WHEN** a town plan is produced
- **THEN** it SHALL contain at least one reserved negative-space region
- **AND** each reserved region SHALL be disjoint from parcels and street cells.

### Requirement: The plan fits the site and accepts a soft brief
The planner SHALL fit within site bounds up to a mid-size fair footprint of approximately 160×160, record a ground reference for each parcel, and accept a district brief that supplies per-district density, storey band, and material register as guidance.

#### Scenario: The plan stays within the site
- **WHEN** a town plan is produced for given site bounds up to the supported footprint
- **THEN** all plan cells SHALL lie within the site bounds
- **AND** each parcel SHALL record a ground reference level
- **AND** each parcel SHALL carry the density, storey band, and material register of its district.
