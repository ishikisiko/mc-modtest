## MODIFIED Requirements

### Requirement: Realm definitions determine maximum lifespan
The maximum lifespan for a player SHALL be resolved dynamically from the
current registered realm and SHALL not be stored in the profile or a Java
constant. The shipped values SHALL be mortal `80`, qi refining `120`, and
foundation establishment `240`, and golden core `500` cultivation years.

#### Scenario: A player enters qi refining
- **WHEN** the authoritative realm changes from mortal to qi refining
- **THEN** maximum lifespan SHALL immediately resolve as 120 years without resetting consumed ticks

#### Scenario: A stored realm is unavailable
- **WHEN** the profile names a realm absent from current registry access
- **THEN** the raw profile and consumed ticks SHALL remain unchanged
- **AND** maximum, remaining, and exhaustion presentation SHALL be unavailable rather than guessed

#### Scenario: Foundation late advances to Golden Core early
- **WHEN** one successful server-owned transition changes the player's realm to Golden Core
- **THEN** maximum lifespan SHALL resolve as 500 cultivation years from the current registry
- **AND** the profile's consumed raw lifespan ticks SHALL remain unchanged
