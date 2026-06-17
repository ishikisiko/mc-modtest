## MODIFIED Requirements

### Requirement: A command builds a town in the live world against terrain
The mod SHALL expose `/myvillage town [seed]`, planning and building a complete districted town up to an approximately 160×160 footprint at the player's location. Rather than refusing when chunks are not preloaded, the command SHALL acquire chunk-load tickets across the planned footprint before placement and release them afterward; if a region cannot be force-loaded or built, the command SHALL report the affected extent rather than silently skip it.

#### Scenario: Summoning a districted town
- **WHEN** an operator runs `/myvillage town` at a location
- **THEN** the mod SHALL force-load the planned footprint and build a town with enclosure, gates, a main-street spine, districts, street-aligned frontage, vertical core landmarks, and lived-in tissue
- **AND** if a region of the footprint cannot be force-loaded or built, the command SHALL report that extent rather than fail silently.

### Requirement: Parcels meet the ground via bounded site-fit
Each realized parcel SHALL meet terrain through a plinth, steps, or retaining course, and parcels above the configured slope limit SHALL be skipped rather than force-flattened. For frontage-district parcels, placement SHALL align the building's street-facing wall to the parcel frontage edge and butt adjacent buildings at shared gable walls; for all parcels, placement SHALL align the template ground layer to the parcel surface and provide continuous footprint support so buildings do not float over a one-block hollow.

#### Scenario: A parcel on a slope sits on the ground
- **WHEN** a parcel is realized on sloping terrain within the slope limit
- **THEN** it SHALL be joined to the ground by a plinth, steps, or retaining course
- **AND** it SHALL neither float above nor be buried in the surrounding ground
- **AND** no one-block air gap SHALL remain under the building footprint.

#### Scenario: A frontage run sits as a continuous wall
- **WHEN** consecutive frontage parcels within the slope limit are realized along one street
- **THEN** their buildings SHALL share gable walls and align to the street frontage edge
- **AND** no per-building plinth ring SHALL separate them from the street.

## ADDED Requirements

### Requirement: A realized town passes district, frontage, and skyline validation
The town-level validator SHALL, in addition to the existing structural invariants, confirm that the plan is partitioned into the required districts, that frontage districts present continuous street-aligned party-wall rows rather than centered-lot gaps, and that the civic core meets the skyline tall-volume minimum.

#### Scenario: A valid districted town passes and a flat one fails
- **WHEN** the town validator runs on a generated districted town
- **THEN** it SHALL pass when the district, frontage, and skyline invariants hold alongside the structural invariants
- **AND** it SHALL fail and report the offending invariant on a town missing districts, presenting centered-lot frontage, or lacking core vertical relief.
