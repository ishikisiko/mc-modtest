## ADDED Requirements

### Requirement: District interiors carry a secondary depth band
A district whose interior depth behind the primary frontage band exceeds a minimum threshold SHALL place a secondary band of ancillary buildings (smaller back-row structures such as 厢房/sheds) facing an interior lane or yard, so the district reads as layered depth rather than a single street wall fronting empty ground. A circulation gap SHALL separate the primary and secondary bands.

#### Scenario: A deep district gains a back row
- **WHEN** a market or residential district has interior depth greater than the configured minimum behind its primary frontage band
- **THEN** the realizer SHALL place a secondary band of ancillary buildings behind the primary band
- **AND** a traversable gap SHALL remain between the primary and secondary bands
- **AND** the bare-yard area left after densification SHALL be smaller than the pre-densification single-yard area for that district.

#### Scenario: A shallow district is left undensified
- **WHEN** a district's interior depth is at or below the configured minimum
- **THEN** no secondary band SHALL be forced
- **AND** placement SHALL not overlap the primary band or block circulation.

### Requirement: Residual courtyard space is dressed as enclosed lived-in tissue
Reserved courtyard negative-space regions that remain after densification SHALL be dressed as enclosed 院落 tissue with domestic props (such as a well, planting plots, drying racks, woodpiles, urns, seating) without blocking circulation, so leftover space reads as intentional rather than empty lawn.

#### Scenario: A leftover yard becomes a courtyard
- **WHEN** the tissue layer dresses a residual courtyard region
- **THEN** the region SHALL receive an enclosing edge and at least two distinct domestic prop types
- **AND** no prop SHALL block a traversable street, alley, or gate.

### Requirement: The central spine carries a market streetscape
The main-street spine SHALL be lined with a 坊市 streetscape (such as stalls, banner/lantern poles, carts, crates) spaced along its length so the principal axis reads as inhabited and market-busy rather than a bare paved strip, with density following the existing market-to-lane falloff.

#### Scenario: The spine reads as a market street
- **WHEN** the realizer finishes a town
- **THEN** the spine SHALL carry recurring streetscape props along its length near the civic core and market mouths
- **AND** the paved walking width of the spine SHALL remain traversable end to end
- **AND** prop density SHALL be higher near the central square than along the back lanes.
