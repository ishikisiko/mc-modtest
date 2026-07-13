## ADDED Requirements

### Requirement: Automated tests prove v3 migration and affinity preservation
Java tests SHALL cover exact v3 defaults, non-default v3 codec and real snapshot
round trips, non-negative affinity validation, v1-to-v2-to-v3 migration,
v2-to-v3 migration with nonzero lifespan/reserve and unknown ids, unsupported
versions, reset behavior, and preservation of affinity/reserve through
initiation, lifespan, settlement, advancement, commands, and copy helpers.
Focused Python validation SHALL inspect current version ownership and all
integration paths without substituting source matching for codec behavior.

#### Scenario: Profile migration tests run
- **WHEN** the automated suite decodes representative v1 and v2 fixtures
- **THEN** both SHALL produce valid schema-v3 profiles with affinity `10`
- **AND** every old value required by its source schema SHALL remain exact

#### Scenario: A copy path omits affinity
- **WHEN** static integration validation finds a profile replacement that resets or drops affinity or reserve
- **THEN** the focused validator SHALL fail with the owning path

### Requirement: Automated tests pin ten-tick gain and direct item costs
Tests SHALL prove eligible-tick counting, partial-session discard, normal gain
for default, zero, and non-default affinity, fixed spirit gain, exact sensed/Qi
I/Qi II/Qi III costs, near-cap clamping, no overflow, no charge at cap or
unsupported stages, pre-cap stability lock, next-batch affinity stability in
both modes, 50% dynamic stability caps, unchanged `10/year` mastery, inert
reserve, and insufficient-cost downgrade to the normal result. Tests SHALL prove the client
cannot author affinity, rate, cost, cap, target, elapsed ticks, or result.

#### Scenario: Settlement arithmetic tests run
- **WHEN** ten-tick fixtures exercise each released source stage and cap boundary
- **THEN** exact progress and inventory deltas SHALL match the current affinity or fixed spirit contract
- **AND** stability SHALL remain locked before full progress, then use affinity without stone cost
- **AND** mastery SHALL remain on its independent configured-year rate

#### Scenario: The player is capped
- **WHEN** a spirit settlement fixture begins with zero remaining capacity
- **THEN** tests SHALL observe zero inventory removal and no reserve mutation
- **AND** eligible stability SHALL advance by affinity up to the derived cap

### Requirement: Automated tests prove inventory and profile rollback semantics
Transaction tests SHALL cover complete removal and commit, insufficient count,
partial-removal rollback, profile validation failure, attachment-install failure,
snapshot failure after successful install, external-container exclusion, and
duplicate/reentrant settlement protection. They SHALL distinguish an installed
profile from a later synchronization failure so rollback cannot duplicate items.

#### Scenario: A pre-install failure occurs
- **WHEN** the full item cost was removed but the profile replacement does not install
- **THEN** tests SHALL prove complete item restoration and unchanged profile state

#### Scenario: A post-install snapshot failure occurs
- **WHEN** the attachment replacement succeeds before client delivery fails
- **THEN** tests SHALL prove the cost and profile result remain committed exactly once

### Requirement: UI tests and evidence cover both H tabs and bounded actions
Automated UI/source tests SHALL verify two tabs, four translatable action
buttons, one-intent click behavior, keyboard parity, absence of reserve labels,
v3 affinity presentation, missing-data states, advisory enablement, disconnect
cleanup, sharp render ordering, and unchanged payload field bounds. Manual
evidence SHALL inspect representative supported window sizes and GUI scales and
record each unobserved layout or action as `not_verified`.

#### Scenario: UI integration tests run
- **WHEN** the H-screen and payload registrations are inspected
- **THEN** every button SHALL route to one of the existing four actions and no numeric authority field SHALL be added

#### Scenario: No real client has been observed
- **WHEN** automated tests and server smoke pass without opening both tabs in Minecraft
- **THEN** button feel, text fit, sharpness, focus, hover, and action feedback SHALL remain `not_verified`

### Requirement: The revised loop runs the complete regression and release handoff
Closeout SHALL record strict validation for this change and the complete spec
baseline; `validate_cultivation_core.py`, `validate_cultivation_initiation.py`,
`validate_spirit_stone_resources.py`, `validate_cultivation_lifespan.py`,
`validate_cultivation_meditation.py`, `validate_cultivation_gain.py`, and
`validate_cultivation_advancement.py`; all validator tests; Gradle tests/build;
current-jar inspection; bounded stage-1 acceptance-server smoke; CRAFT/front-door
checks; documentation and visual evidence; and synchronized feature metadata
for `0.25.0` under the repository version rule. One failing required gate SHALL
block completion, archive, merge, and push as a successful release.

#### Scenario: Every automated gate passes
- **WHEN** the implemented worktree is prepared for human review
- **THEN** evidence SHALL record the real exit status of every required command and the current jar version/content
- **AND** real-client gameplay and layout SHALL remain separate manual verdicts

#### Scenario: One playable-loop regression fails
- **WHEN** any of the five focused change validators, foundation/initiation validator, test, build, jar, server-smoke, or governance gate exits nonzero
- **THEN** the change SHALL not be reported complete or closeout-ready
