## MODIFIED Requirements

### Requirement: Regression validation protects explicit non-goals
Validation SHALL preserve flying-sword payload tests, the read-only H profile
screen, clientbound-only profile/time snapshots, server-authoritative
meditation intent, immutable profile replacement, and initiation's two distinct
ritual services. The implementation MAY add declared stage caps, fixed Basic
Breathing settlement, stability/mastery gain, reserve conversion, and the
low-grade-stone inventory transaction. It SHALL contain no generic technique
executor/equipment system, combat or exploration cultivation XP, spiritual-
power recovery, element multiplier, random gain, automatic advancement,
breakthrough process, Qi Refining IV+ gain, or extra cultivation item. Release
metadata SHALL follow the synchronized feature-version rule in
`openspec/config.yaml`.

#### Scenario: Scope is reviewed before closeout
- **WHEN** the final implementation diff and validation evidence are inspected
- **THEN** each new gain path SHALL map to an active eligible Basic Breathing settlement and the declared cap/reserve rules
- **AND** every excluded executor, combat, power, advancement, and later-stage surface SHALL remain absent

#### Scenario: Basic Breathing data is inspected
- **WHEN** the shipped technique definition and runtime code are validated
- **THEN** the definition SHALL retain initiation metadata without an executor or gain-rate field
- **AND** the fixed settlement service SHALL own the only declared execution behavior

## ADDED Requirements

### Requirement: Automated validation pins cultivation settlement arithmetic
Java tests and a focused deterministic validator SHALL cover the 100-tick
interval, fixed-point carry, default-year rates `100/10/10`, spirit total
progress `400`, exact caps `300/500/800/1200`, continued capped
stability/mastery, less-than-one session-end remainder loss, and unchanged
lifespan rate in both modes.

#### Scenario: The arithmetic suite runs
- **WHEN** normal and spirit fixtures span partial batches, a complete default year, and cap boundaries
- **THEN** whole outputs and remainders SHALL match the declared rates exactly
- **AND** spirit fixtures SHALL consume one reserve per applied bonus point without changing lifespan speed

### Requirement: Automated validation covers reserve and inventory authority
Tests SHALL exercise persistent reserve, one stone to 100 reserve, ordinary-
inventory-only lookup, cap-aware spending, logical rollback on item/profile
failure, downgrade to normal, one final snapshot, and no per-tick scanning or
snapshot traffic. Negative fixtures SHALL reject direct attachment mutation and
client-authored settlement values.

#### Scenario: Stone-backed settlement is tested
- **WHEN** a fixture requires conversion, applies only part of the credited reserve, and then interrupts
- **THEN** exactly one inventory stone SHALL be consumed
- **AND** the applied bonus and preserved reserve SHALL reconcile to 100 points

#### Scenario: No acceleration resource exists
- **WHEN** a spirit session reaches a bonus settlement with zero reserve and no inventory stone
- **THEN** validation SHALL observe one downgrade and continuing ordinary gain eligibility

### Requirement: Cultivation gain runs the complete automated handoff
Closeout SHALL run strict validation for this change and implemented baseline,
the cultivation core/initiation/lifespan/meditation/gain validators, focused
tests, Gradle tests and build, practical jar inspection, and a bounded dedicated
acceptance-server smoke. Manual H-screen, timing, interruption, inventory, and
feel observations SHALL remain explicit pass/fail/`not_verified` evidence.

#### Scenario: Automated handoff succeeds
- **WHEN** the change is proposed for implementation closeout
- **THEN** every declared automated validator, test, build, jar, and server-smoke surface SHALL pass
- **AND** no unobserved in-game result SHALL be reported as verified
