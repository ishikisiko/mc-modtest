# cultivation-advancement Specification

## Purpose
TBD - created by archiving change add-qi-refining-advancement. Update Purpose after archive.
## Requirements
### Requirement: Advancement starts only from server-validated current data
The server SHALL accept a start-breakthrough intent only for an idle player who
is alive, in survival or adventure, not lifespan-exhausted, awakened, learned in
registered Basic Breathing, supported by stable ground, outside every declared
movement/use/conflict state, and undamaged for 100 ticks. It SHALL resolve the
current stage cap and advancement rule from current `RegistryAccess`, require
progress at least the cap and stability at least the rule requirement, and
resolve the declared target. The client SHALL not select any target or result.

#### Scenario: A capped eligible player starts
- **WHEN** the server receives start breakthrough for an idle player whose current rule, target, progress, and stability are valid
- **THEN** it SHALL enter the rule's ordinary or bottleneck advancement state
- **AND** it SHALL derive all duration, cost, and target values on the server

#### Scenario: Progress is below the cap
- **WHEN** current-stage progress is one point below the registered cap
- **THEN** the request SHALL be rejected without a session or profile mutation

#### Scenario: Stability is below the requirement
- **WHEN** progress is full but stability is below the current rule's requirement
- **THEN** the request SHALL be rejected with the controlled stability reason

### Requirement: Advancement is transient and mutually exclusive with meditation
The UUID-keyed server session manager SHALL own at most one preparing,
meditating, ordinary-advancement, or bottleneck-advancement state per player.
An accepted advancement SHALL count its definition duration in consecutive
eligible server ticks without an additional 40-tick meditation preparation.
Session state SHALL not be persisted in the profile or SavedData.

#### Scenario: A meditating player requests advancement
- **WHEN** start breakthrough arrives during preparation or active meditation
- **THEN** it SHALL be rejected rather than switching, advancing, or settling both systems

#### Scenario: A player reconnects after advancing partway
- **WHEN** a prior transient session ended at logout
- **THEN** no advancement duration SHALL resume automatically

### Requirement: Ordinary advancement uses the exact initial rules
The shipped ordinary transitions SHALL remain deterministic: qi-sensed mortal
to Qi Refining I SHALL require cap `1000`, duration `100`, stability `500`, and
compatibility cost `250`; Qi I to II SHALL require cap `1100`, duration `100`,
stability `550`, and compatibility cost `275`; Qi II to III SHALL require cap
`1200`, duration `120`, stability `600`, and compatibility cost `300`.
Ordinary interruption loss SHALL remain zero. Successful advancement SHALL
reset progress and retain integer-floor half of actual current stability; the
authored compatibility cost SHALL equal half the required stability but SHALL
not replace the halving rule.

#### Scenario: Qi-sensed mortal starts one point below the new cap
- **WHEN** current progress is `999`
- **THEN** advancement SHALL be rejected as below the registered `1000` cap

#### Scenario: Qi I completes ordinary advancement
- **WHEN** a player at progress `1100` and stability `550` remains eligible for 100 advancement ticks
- **THEN** the player SHALL enter Qi II with progress zero and stability `275`

#### Scenario: An odd debug stability value advances
- **WHEN** an otherwise eligible player advances with current stability `551`
- **THEN** successful advancement SHALL retain `275` stability by integer-floor halving

#### Scenario: Qi II ordinary advancement is interrupted
- **WHEN** a full-progress Qi-II transition ends before its 120-tick duration
- **THEN** realm, stage, progress, and stability SHALL remain unchanged

### Requirement: Qi III to Qi IV is the first exact bottleneck
The shipped Qi-III rule SHALL target Qi IV, require cap `1300`, duration `200`
ticks, stability `650`, and compatibility cost `325`. A player/world
interruption SHALL lose exactly `5` stability, while deterministic completion
SHALL retain half of actual current stability without also charging
interruption loss.

#### Scenario: The revised bottleneck completes
- **WHEN** an eligible Qi-III player at progress `1300` remains valid for all 200 ticks
- **THEN** the profile SHALL enter Qi IV with progress zero and half its prior stability, integer-floor rounded

#### Scenario: The revised bottleneck is interrupted by movement
- **WHEN** the full-progress player moves beyond tolerance before tick 200
- **THEN** the stage and progress SHALL remain Qi III and `1300`
- **AND** stability SHALL decrease by exactly 5, clamped at zero

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

### Requirement: Advancement cannot bank overflow or chain stages
Success SHALL discard no hidden bank because normal gain SHALL never store
overflow; it SHALL always reset visible stage-local progress to zero. One start
intent SHALL complete at most one declared transition and return the session to
idle, including for migrated or administrator-created over-cap values.

#### Scenario: An over-cap debug profile advances
- **WHEN** a valid source profile has progress above its cap and completes one transition
- **THEN** it SHALL enter only the immediate declared target at zero progress
- **AND** another transition SHALL require another explicit start after refilling that stage

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
