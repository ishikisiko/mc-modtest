## ADDED Requirements

### Requirement: Building parts expose town-frontage metadata
Generated building parts SHALL expose which side faces the town street and where the shopfront or entry opening is located, so the street-room and lived-in-tissue layers can attach to the correct face. When a part does not declare frontage, the largest open side SHALL be used as the default front.

#### Scenario: A part declares its street frontage
- **WHEN** a building part is generated for town use
- **THEN** it SHALL record its street-facing side and the cells of its shopfront or entry opening
- **AND** a part with no declared frontage SHALL default its front to its largest open side.

### Requirement: An importance tier drives massing and roof-grade selection
Town building selection SHALL accept an importance tier that biases massing height and roof grade, where a higher tier yields taller massing and a higher roof grade, and the town's dominant landmark SHALL be selected at the top tier.

#### Scenario: Importance biases the building
- **WHEN** a town parcel requests a building at a given importance tier
- **THEN** a higher tier SHALL bias selection toward taller massing and a higher roof grade
- **AND** the dominant-landmark parcel SHALL request the top importance tier.
