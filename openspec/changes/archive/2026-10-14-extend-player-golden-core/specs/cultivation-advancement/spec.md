## RENAMED Requirements

- FROM: `### Requirement: Qi IV is the playable advancement release ceiling`
- TO: `### Requirement: Golden Core late is the playable advancement release ceiling`

## MODIFIED Requirements

### Requirement: Advancement reuses the complete gameplay interruption set
Advancement SHALL reuse the complete gameplay interruption set.
Movement beyond the established `0.01`-block tolerance, jump, damage,
attack/swing, mining, other item use, mount, swimming, flight, sleep, game-mode
or conflict ineligibility, dimension change, death, logout, and explicit stop
SHALL interrupt advancement. Yaw and pitch SHALL remain allowed. Interruption
handling SHALL be idempotent. Clean server stop, registry-reload teardown, and
server-side rule/profile invalidation SHALL cancel without a stability penalty.

#### Scenario: View direction changes
- **WHEN** an advancing player changes only yaw or pitch
- **THEN** advancement SHALL continue without reset or penalty

#### Scenario: Damage and movement hooks overlap
- **WHEN** one bottleneck is observed by multiple interruption hooks in the same tick
- **THEN** the session SHALL end once and apply at most its authored interruption stability loss once; the preserved Qi-III bottleneck SHALL still lose at most 5

#### Scenario: The server shuts down cleanly
- **WHEN** active advancement sessions are cleared for orderly stop
- **THEN** no interruption penalty SHALL be installed

### Requirement: Success installs one atomic deterministic transition
On the final eligible tick the server SHALL revalidate source stage, cap,
progress, stability, current rule, and target. It SHALL then install one
immutable profile replacement through `CultivationService` with target
realm/stage, progress zero, and integer-floor half of actual current stability. The compatibility success-cost field SHALL NOT also be subtracted.
It SHALL preserve lifespan consumed, reserve, spiritual root, current spiritual
power subject only to the target stage's ordinary clamp, learned techniques, the active core technique, every mastery value, and all unrelated fields. A larger target spiritual-power cap SHALL NOT refill the pool; entering a longer-lived realm SHALL NOT reset consumed lifespan. It
SHALL roll no random success and SHALL synchronize only the final profile.

#### Scenario: A cross-realm transition succeeds
- **WHEN** qi-sensed mortal completes its valid 100-tick advancement
- **THEN** one replacement SHALL set realm to Qi Refining, stage to Qi I, progress to zero, and retain integer-floor half of current stability
- **AND** every unrelated current-profile field SHALL equal the source profile

#### Scenario: Completion revalidation fails
- **WHEN** the source rule, cap, progress, stability, or target no longer validates on the final tick
- **THEN** no success replacement SHALL be installed
- **AND** the session SHALL end with a controlled invalidation result

#### Scenario: A major-realm bottleneck completes
- **WHEN** an eligible Qi-IX or Foundation-late player remains valid for the entire authored bottleneck duration
- **THEN** one service replacement SHALL enter Foundation early or Golden Core early respectively at zero progress and half of actual prior stability
- **AND** consumed lifespan, technique mastery, active core technique and existing spiritual power SHALL be preserved without an automatic refill

### Requirement: Golden Core late is the playable advancement release ceiling
Golden Core late SHALL be the deliberate terminal player stage and SHALL declare no cultivation cap or advancement rule. Qi IV through IX, all three Foundation stages, and Golden Core early/middle SHALL be playable from their explicit registered source rules. The system SHALL reject gain and advancement at Golden Core late with the controlled release-limit result and SHALL NOT infer a Nascent Soul target, cap, cost or rule. The preserved target-Qi-I through IV threshold examples SHALL remain authored data rather than a runtime formula. All new major-realm transitions SHALL remain deterministic, server-owned and one-stage only, with no random failure, pills, facilities or tribulation added.

#### Scenario: A newly advanced Golden-Core-late player requests advancement
- **WHEN** the server resolves no authored cap or advancement rule for Golden Core late
- **THEN** it SHALL reject the request as outside the current release boundary
- **AND** it SHALL not infer a Nascent Soul rule or cap

#### Scenario: Released thresholds are inspected
- **WHEN** target layers I through IV are compared with the documented sequence
- **THEN** their authored caps SHALL equal `1000`, `1100`, `1200`, and `1300` respectively
- **AND** their stability requirements SHALL equal `500`, `550`, `600`, and `650` respectively

#### Scenario: Qi IV resumes ordinary progression
- **WHEN** an eligible Qi-IV player fills its authored cap and stability requirement and starts advancement
- **THEN** it SHALL advance exactly to Qi V under current data rather than receive the former Qi-IV release-limit rejection
