## MODIFIED Requirements

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

### Requirement: Qi IV is the playable advancement release ceiling
Qi Refining IV and later stages SHALL provide no cultivation cap or advancement
rule in this release. The sequence `900 + 100 * target Qi layer` SHALL document
the four released target thresholds and SHALL remain guidance for future authored definitions,
but runtime SHALL NOT use it to synthesize a cap, cost, target, or rule. The
system SHALL reject further gain/advancement with a controlled unsupported-stage
result and SHALL add no Foundation Establishment or random failure behavior.

#### Scenario: A newly advanced Qi-IV player requests advancement
- **WHEN** the server resolves no authored cap or advancement rule for Qi IV
- **THEN** it SHALL reject the request as outside the current release boundary
- **AND** it SHALL not infer a Qi-V cap of `1400`

#### Scenario: Released thresholds are inspected
- **WHEN** target layers I through IV are compared with the documented sequence
- **THEN** their authored caps SHALL equal `1000`, `1100`, `1200`, and `1300` respectively
- **AND** their stability requirements SHALL equal `500`, `550`, `600`, and `650` respectively
