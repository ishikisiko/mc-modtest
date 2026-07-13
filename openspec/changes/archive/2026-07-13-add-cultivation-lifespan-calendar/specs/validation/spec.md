## ADDED Requirements

### Requirement: Focused lifespan validation checks migration integration and scope
A focused standard-library validator with negative fixtures SHALL inspect v1/v2
codec ownership, both new fields, migration defaults, realm lifespan values,
Overworld SavedData, eligible-mode filters, 600-tick batching, lifecycle hooks,
configuration defaults/warnings, clientbound-only time synchronization, H-screen
labels, initiation preservation, documentation, and jar inclusion. Algorithmic
and lifecycle behavior SHALL be proven by Java/integration tests rather than
source text alone.

#### Scenario: Shipped lifespan integration is valid
- **WHEN** the focused validator runs after implementation and jar build
- **THEN** it SHALL succeed only when every declared source/resource/docs/package invariant is present

#### Scenario: A fixture silently defaults a malformed v2 value
- **WHEN** migration or current decoding resets an unsupported/negative value instead of failing
- **THEN** validation SHALL fail with a schema-migration diagnostic

### Requirement: Lifespan closeout runs the complete cultivation gate set
Closeout SHALL record strict change and baseline validation, cultivation core and
initiation validators/tests updated for schema v2, focused lifespan validator
tests, Gradle tests/build, jar inspection, and bounded server smoke. Existing
initiation and flying-sword regressions SHALL remain green.

#### Scenario: One required gate fails
- **WHEN** any strict spec, validator, test, build, jar, or server-smoke command exits nonzero
- **THEN** the change SHALL not be reported complete or ready for serial integration
