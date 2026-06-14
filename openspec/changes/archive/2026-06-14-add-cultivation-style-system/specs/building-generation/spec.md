## ADDED Requirements

### Requirement: The settlement-group layer sits above the style profile
The generator's conceptual layering SHALL include a Settlement Group layer above the Style Profile layer. The active group SHALL determine which style profile, archetype roster, and layout strategy generation uses.

#### Scenario: Generation is driven by a group
- **WHEN** a building is generated for a settlement group
- **THEN** the group SHALL determine the style profile applied
- **AND** the group SHALL constrain archetype selection to its roster.

### Requirement: Roof and motif placement is routed through the form registry
Roof construction and decoration motif placement SHALL be performed by looking up the form name in the roof/motif registry rather than by hardcoded dispatch branches.

#### Scenario: The roof pass builds a roof
- **WHEN** the roof pass processes a volume
- **THEN** it SHALL look up the volume's roof type in the roof registry
- **AND** it SHALL invoke the registered handler.

#### Scenario: The decoration pass places a motif
- **WHEN** the exterior decoration pass processes a decoration node
- **THEN** it SHALL look up the motif name in the motif registry
- **AND** it SHALL invoke the registered handler.

### Requirement: Cultivation archetype rosters exist
The generator SHALL provide a `cultivation_town` archetype roster (mortal town buildings) and a `cultivation_sect` archetype roster (sect buildings). Each roster SHALL be classified per the existing town-generation classification (housing, functional, civic, infrastructure, decorative). Sect archetypes SHALL NOT reuse town housing massing and vice versa.

#### Scenario: A sect archetype is generated
- **WHEN** the generator requests a sect-roster archetype
- **THEN** the building pipeline SHALL generate it from a sect massing builder
- **AND** it SHALL NOT reuse a town or medieval housing massing.

#### Scenario: A town build excludes sect archetypes
- **WHEN** the `cultivation_town` group generates its library
- **THEN** it SHALL NOT emit sect-roster archetypes.
