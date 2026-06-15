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
The plan SHALL include a main-street spine that connects gates through the town core and SHALL designate exactly one dominant-landmark parcel at the top importance tier.

#### Scenario: The spine anchors a single dominant landmark
- **WHEN** a town plan is produced
- **THEN** a main-street spine SHALL connect at least one gate to the town core
- **AND** exactly one parcel SHALL be flagged as the dominant landmark
- **AND** that landmark SHALL be the highest importance tier in the plan.

### Requirement: Legibility comes from a center-to-edge gradient
The plan SHALL assign each parcel an importance tier that does not increase from the core toward the edge, and each tier SHALL carry height and roof-grade hints.

#### Scenario: Importance falls off toward the edge
- **WHEN** a town plan assigns parcel importance tiers
- **THEN** outer parcels SHALL NOT be assigned a higher tier than nearer core parcels
- **AND** each parcel's tier SHALL map to a height and roof-grade hint.

### Requirement: The plan reserves intentionally-shaped negative space
The plan SHALL reserve named open regions that are neither building parcels nor street cells.

#### Scenario: Negative space is a first-class plan element
- **WHEN** a town plan is produced
- **THEN** it SHALL contain at least one reserved negative-space region
- **AND** each reserved region SHALL be disjoint from parcels and street cells.

### Requirement: The plan fits the site and accepts a soft brief
The planner SHALL fit within site bounds, record a ground reference for each parcel, and accept an optional soft functional brief as guidance rather than a hard constraint.

#### Scenario: The plan stays within the site
- **WHEN** a town plan is produced for given site bounds
- **THEN** all plan cells SHALL lie within the site bounds
- **AND** each parcel SHALL record a ground reference level.
