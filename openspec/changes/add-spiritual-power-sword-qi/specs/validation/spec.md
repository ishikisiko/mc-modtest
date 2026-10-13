## ADDED Requirements

### Requirement: Spiritual power and sword qi use layered acceptance
Acceptance SHALL include focused pure rules and data tests, affected cultivation and combat regression validators, Gradle tests/build, jar resource inspection, bounded server lifecycle checks, real-client mapped input and a normal acquisition/study/use loop. Each executed test SHALL retain duration and result evidence. Unexecuted multiplayer, owner look/feel/FPS, and lifecycle checks SHALL be `not_verified`; startup alone SHALL NOT count as gameplay evidence.

#### Scenario: Only server startup is observed
- **WHEN** an acceptance server reaches readiness without a client action
- **THEN** the ledger SHALL record startup success and leave unobserved gameplay checks `not_verified`
