## ADDED Requirements

### Requirement: Power and learned-skill data are validated
Cultivation validation SHALL check stage power-cap bounds, power-rule bounds, generated sword-qi learning requirements, and its registered active-skill reference. Existing profile, learning, meditation and authority checks SHALL remain in force.

#### Scenario: An executable active reference is broken
- **WHEN** the sword-qi catalogue references an absent registered skill
- **THEN** validation SHALL fail with a specific reference diagnostic
