## MODIFIED Requirements

### Requirement: Galleried pavilion (楼阁) and tapering pagoda (塔) massing
The massing grammar SHALL provide a galleried multi-story pavilion (楼阁) with
balustraded balconies at upper stories, and a tapering pagoda (塔) whose
successive storeys follow a non-decreasing inset schedule with at least two
real reductions. The standalone `pagoda` and the `scripture_pavilion` pagoda
form SHALL use this tapering grammar. Every occupied-storey boundary of the
standalone `pagoda` SHALL carry an independently legible projecting eave with
bracket and lifted-corner cues, and its crown SHALL be a pyramidal roof with a
finial.

#### Scenario: The scripture pavilion is a tapering pagoda
- **WHEN** `scripture_pavilion` builds its massing
- **THEN** each story above the first SHALL inset relative to or remain no wider
  than the story below
- **AND** each story SHALL carry its own eave
- **AND** the crown SHALL be a pyramidal roof with a finial.

#### Scenario: The standalone pagoda carries independent storey eaves
- **WHEN** `pagoda` builds a five- or seven-storey landmark
- **THEN** every storey boundary below the crown SHALL have a projecting eave
- **AND** the eave SHALL include bracket support and lifted-corner cues
- **AND** at least two upper-storey footprints SHALL be narrower than the
  storeys below them.

#### Scenario: A pavilion has a balcony
- **WHEN** a galleried pavilion-class volume is built with more than one story
- **THEN** upper stories SHALL carry a balustraded balcony resolving through
  the `BALUSTRADE` slot.
