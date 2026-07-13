## ADDED Requirements

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
The shipped ordinary transitions SHALL be deterministic: qi-sensed mortal to
Qi Refining I SHALL require cap `300`, duration `100`, stability `10`, and cost
`5`; Qi I to II SHALL require cap `500`, duration `100`, stability `20`, and
cost `10`; Qi II to III SHALL require cap `800`, duration `120`, stability `30`,
and cost `15`. Ordinary interruption loss SHALL be zero.

#### Scenario: Qi I completes ordinary advancement
- **WHEN** a player at full Qi-I progress and at least 20 stability remains eligible for 100 advancement ticks
- **THEN** the player SHALL enter Qi II with progress zero and stability reduced by 10

#### Scenario: Ordinary advancement is interrupted
- **WHEN** any of the three ordinary transitions ends before its duration
- **THEN** realm, stage, progress, and stability SHALL remain unchanged

### Requirement: Qi III to Qi IV is the first exact bottleneck
The shipped Qi-III rule SHALL target Qi IV, require cap `1200`, duration `200`
ticks, stability `80`, and successful stability cost `30`. A player/world
interruption SHALL lose exactly `5` stability, while deterministic completion
SHALL use only the `30` success cost rather than also charging interruption loss.

#### Scenario: The bottleneck completes
- **WHEN** an eligible Qi-III player remains valid for all 200 ticks
- **THEN** the profile SHALL enter Qi IV with progress zero and stability reduced by exactly 30

#### Scenario: The bottleneck is interrupted by movement
- **WHEN** the player moves beyond tolerance before tick 200
- **THEN** the stage and progress SHALL remain Qi III and full
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
- **THEN** the session SHALL end once and lose at most 5 stability once

#### Scenario: The server shuts down cleanly
- **WHEN** active advancement sessions are cleared for orderly stop
- **THEN** no interruption penalty SHALL be installed

### Requirement: Success installs one atomic deterministic transition
On the final eligible tick the server SHALL revalidate source stage, cap,
progress, stability, current rule, and target. It SHALL then install one
immutable profile replacement through `CultivationService` with target
realm/stage, progress zero, and stability reduced by the declared success cost.
It SHALL preserve lifespan consumed, reserve, spiritual root, current spiritual
power, learned techniques, every mastery value, and all unrelated fields. It
SHALL roll no random success and SHALL synchronize only the final profile.

#### Scenario: A cross-realm transition succeeds
- **WHEN** qi-sensed mortal completes its valid 100-tick advancement
- **THEN** one replacement SHALL set realm to Qi Refining, stage to Qi I, progress to zero, and deduct 5 stability
- **AND** every unrelated v2 field SHALL equal the source profile

#### Scenario: Completion revalidation fails
- **WHEN** the source rule, cap, progress, stability, or target no longer validates on the final tick
- **THEN** no success replacement SHALL be installed
- **AND** the session SHALL end with a controlled invalidation result

### Requirement: Advancement cannot bank overflow or chain stages
Success SHALL discard no hidden bank because normal gain SHALL never store
overflow; it SHALL always reset visible stage-local progress to zero. One start
intent SHALL complete at most one declared transition and return the session to
idle, including for migrated or administrator-created over-cap values.

#### Scenario: An over-cap debug profile advances
- **WHEN** a valid source profile has progress above its cap and completes one transition
- **THEN** it SHALL enter only the immediate declared target at zero progress
- **AND** another transition SHALL require another explicit start after refilling that stage

### Requirement: Qi IV is the playable advancement release ceiling
Qi Refining IV and later stages SHALL provide no cultivation cap or advancement
rule in this release. The system SHALL reject further gain/advancement with a
controlled unsupported-stage result. It SHALL add no Foundation Establishment
process, major-realm requirements, material/facility/environment rules, pills,
tribulation, random failure, or reincarnation behavior.

#### Scenario: A newly advanced Qi-IV player presses N
- **WHEN** the server resolves no cap or advancement rule for Qi IV
- **THEN** it SHALL reject the request as outside the current release boundary
- **AND** the profile SHALL remain unchanged
