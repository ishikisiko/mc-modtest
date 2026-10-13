## ADDED Requirements

### Requirement: Sword-qi resources ship with the practical jar
The practical mod jar SHALL include the generated sword-qi technique and both language entries, the indexed skill resource, and stage/power rule data. Catalogue outputs SHALL be regenerated from their authored inputs, never edited independently; current user-facing usage SHALL describe learning and casting.

#### Scenario: The built jar is inspected
- **WHEN** the release candidate jar is opened after a fresh build
- **THEN** its resources SHALL contain the same indexed skill and generated technique validated from source
