# Street Frontage

## Purpose

This spec captures the street-aligned, party-wall frontage layout used in market and residential districts of the cultivation town plan, including the typed alley treatment for leftover interstitial space and the validator invariant that rejects centered-lot sparsity.

## Requirements

### Requirement: Buildings align to the street frontage
For parcels in `market` and `residential` districts, realization SHALL align each building's street-facing wall to the parcel's frontage edge rather than centering the building in the lot. The space behind the frontage SHALL become courtyard or yard, not plinth-ringed gap.

#### Scenario: A shop fronts the street
- **WHEN** a market-district parcel adjoining a street is realized
- **THEN** the building's street-facing wall SHALL sit on the parcel frontage edge
- **AND** no continuous plinth ring SHALL be placed around the building between it and the street.

### Requirement: Adjacent frontage parcels share party walls
A run of adjacent frontage parcels along the same street SHALL butt neighboring buildings against shared gable lines so the run reads as one continuous shopfront wall, breaking the run only at a corner, a typed alley, or a slope threshold.

#### Scenario: A row of shops forms a continuous frontage
- **WHEN** three or more frontage parcels sit consecutively along one street segment within the slope limit
- **THEN** their buildings SHALL share gable walls along the street
- **AND** the realized frontage SHALL present a continuous wall with no dead-lawn gaps between buildings.

### Requirement: Leftover interstitial space becomes typed alleys
Space left between district blocks that is too narrow for a parcel SHALL be emitted as a typed `alley` region rather than dead lawn, and alleys SHALL receive no building plinth.

#### Scenario: A narrow gap becomes an alley
- **WHEN** the planner leaves a gap narrower than the minimum parcel width between two frontage blocks
- **THEN** that gap SHALL be recorded as an `alley` region
- **AND** the alley SHALL be disjoint from parcels and SHALL not receive a building plinth.

### Requirement: Frontage validation rejects centered-lot sparsity
The town-plan validator SHALL fail a plan in which a frontage district's buildings are centered with surrounding plinth gaps instead of aligned party-wall rows.

#### Scenario: A sparse frontage fails validation
- **WHEN** the validator runs on a town whose market parcels are centered in oversized lots
- **THEN** it SHALL fail and report a frontage-sparsity invariant
- **AND** it SHALL pass when those parcels present continuous street-aligned frontage.
