## MODIFIED Requirements

### Requirement: Regression validation protects explicit non-goals
Validation SHALL preserve flying-sword protocol tests, initiation authority, the
read-only sharp H screen, immutable `CultivationService` replacement, and sole
`copyOnDeath` ownership while explicitly allowing the declared v2 profile,
realm lifespan data, Overworld calendar SavedData, clientbound time snapshot,
configuration, and lifespan batching. The diff SHALL contain no meditation,
cultivation gain, reserve consumption, advancement, combat, reincarnation, or
automatic lifespan death/reset behavior.

#### Scenario: Scope is reviewed before closeout
- **WHEN** CRAFT evidence and the final diff are inspected
- **THEN** each changed implementation file SHALL map to v2 migration, calendar/lifespan behavior, time presentation, validation, docs, or coordinated release integration
- **AND** all later meditation/gain/advancement and unrelated protocol surfaces SHALL remain unchanged

#### Scenario: Initiation is inspected after v2 migration
- **WHEN** awakening and inheritance tests run with nonzero lifespan and reserve
- **THEN** both counters SHALL be preserved through exactly one successful replacement

## ADDED Requirements

### Requirement: Automated tests prove v1-to-v2 migration and time arithmetic
Java tests SHALL cover exact v1 fixtures, unknown ids, v2 round trips, negative
counters, unsupported versions, default/reset, all profile-copy helpers,
initiation preservation, checked scale products, calendar eligibility, personal
eligibility, 600-tick batching, warning thresholds, exhaustion, and counter
saturation.

#### Scenario: The migration suite runs
- **WHEN** tests decode representative default, non-default, unknown-id, and over-cap v1 profiles
- **THEN** every old field SHALL be preserved and both new fields SHALL equal zero

#### Scenario: The time suite runs
- **WHEN** eligible/excluded player and config fixtures execute
- **THEN** clock increments, pauses, thresholds, reinterpretation, and overflow handling SHALL match the capability exactly

### Requirement: Lifecycle and UI evidence remain truthful
Automated integration SHALL cover payload codecs/directions, dedicated-server
side safety, registry/config/SavedData loading, and bounded server startup.
Relog, death, End return, dimension change, configuration reinterpretation,
warning delivery, H-screen layout, and clean-stop flush SHALL remain
`not_verified` until each is directly observed or supported by its declared
integration test surface.

#### Scenario: Only unit tests build and server startup pass
- **WHEN** no real client lifecycle session is observed
- **THEN** the manual lifecycle, warning, and visual rows SHALL remain `not_verified`
