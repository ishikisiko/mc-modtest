# Town Block Variety

## Purpose

This spec captures the variety of frontage rows within a single cultivation town district: distributing parcels across shipped archetype variants rather than one canonical building, applying per-parcel orientation/mirroring while keeping the street edge continuous, and letting variant footprint widths drive frontage subdivision so a row tiles cleanly without reading as a single repeated structure.

## Requirements

### Requirement: Frontage parcels are distributed across shipped template variants
A run of frontage parcels within a single district SHALL be assigned across the shipped variants of its archetype (e.g. `_001/_002/_003`) rather than all using one canonical variant, so a street block does not read as a single repeated building. Variant assignment SHALL be deterministic for a given town seed.

#### Scenario: A residential block shows mixed houses
- **WHEN** a residential district subdivides into four or more frontage parcels and the archetype ships multiple variants
- **THEN** the placed parcels SHALL use at least two distinct shipped variants
- **AND** regenerating the town with the same seed SHALL produce the same variant assignment.

#### Scenario: An archetype with one variant degrades gracefully
- **WHEN** a district's archetype ships only a single variant
- **THEN** every parcel SHALL use that variant without error.

### Requirement: Frontage parcels vary orientation while keeping a continuous street edge
Frontage placement SHALL apply per-parcel orientation or mirroring drawn deterministically from the town seed to multiply apparent variety, while the street-facing edge SHALL remain a continuous party-wall shopfront aligned to the frontage line.

#### Scenario: A street row reads as continuous but varied
- **WHEN** frontage parcels along one street edge are realized
- **THEN** adjacent parcels MAY differ in mirror or orientation
- **AND** the street-facing wall plane SHALL remain flush and continuous along the frontage edge with no parcel set back or rotated off the street line.

### Requirement: Variant footprints drive frontage subdivision width
When variants of the same archetype differ in width, frontage subdivision SHALL size each parcel segment to the width of the variant chosen for that segment so the row tiles without gaps or overlaps.

#### Scenario: Mixed-width variants tile cleanly
- **WHEN** a frontage row mixes variants of differing widths
- **THEN** each parcel's segment width SHALL match its chosen variant's footprint width
- **AND** no two parcels SHALL overlap and no unintended gap SHALL remain between adjacent parcels beyond designed alleys.
