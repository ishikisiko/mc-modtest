## ADDED Requirements

### Requirement: Focused affinity and meditation UI validation covers the revised contract
The repository SHALL add or revise deterministic validators with negative
fixtures for profile v3/migrations, affinity preservation, ten-tick cadence,
normal and spirit arithmetic, direct layer-priced costs, transaction rollback,
inert reserve, four progress/stability caps, pre-cap stability lock, post-cap
affinity consolidation, success halving, Qi-IV ceiling, tabbed H presentation,
bounded button intents, bilingual resources, documentation, version `0.25.0`,
and practical-jar inclusion. Behavioral codec, transaction, and widget routing
claims MUST be proven by Java tests where static source inspection is
insufficient.

#### Scenario: The revised implementation is valid
- **WHEN** all focused validators run against the implemented resources, source, docs, tests, and current jar
- **THEN** they SHALL exit successfully only when every declared invariant is present and internally consistent

#### Scenario: A negative fixture restores items after a successful install
- **WHEN** transaction handling would refund stones after profile installation but snapshot failure
- **THEN** validation SHALL fail with a duplicate-refund diagnostic

#### Scenario: A negative fixture exposes reserve in H
- **WHEN** the tabbed profile or meditation UI still renders the legacy reserve
- **THEN** validation SHALL fail with the stale-reserve presentation path

### Requirement: Revised-loop acceptance separates automation visual review and gameplay
The acceptance handoff SHALL run the strict baseline plus all five playable-loop
change specs, the seven named cultivation/resource validators, validator tests,
Gradle tests/build, jar inspection, bounded stage-1 server smoke, and
CRAFT/front-door checks. Before manual review it SHALL update README/AGENTS/KB
guidance, generate the visual acceptance report, inspect representative H-tab
images, and serve `out/preview/` over HTTP until review ends. Automation and
headless startup SHALL not count as real-client visual or interaction proof.

#### Scenario: Automation passes without a client session
- **WHEN** every automated gate and bounded server smoke passes but no real client opens H
- **THEN** both-tab layout, button interaction, exact inventory consumption, downgrade feedback, and advancement flow SHALL remain `not_verified`

#### Scenario: Visual evidence is prepared
- **WHEN** the worktree is handed to the owner for review
- **THEN** the acceptance report and representative tab images SHALL be inspectable through the served preview
- **AND** the report SHALL state which judgments remain pending

### Requirement: The real-client ledger exercises revised progression without inferred passes
README acceptance SHALL record `pass`, `fail`, or `not_verified` for v1/v2 save
migration, default affinity `10`, Profile/Meditation tabs, all four buttons and
V/B/G/N parity, normal ten-tick gain, each released spirit cost, insufficient
stone downgrade, no progress-cap charge, pre-cap stability lock, post-cap
affinity stability in both modes, all four dynamic stability caps, success
halving, unchanged mastery clock, all four new progress thresholds, Qi-IV
ceiling, interruption, relog/death/dimension preservation, and existing
initiation/flying-sword regressions. Unobserved items SHALL remain `not_verified`
even when validators or server startup pass.

#### Scenario: Exact Qi-II spirit cost is observed
- **WHEN** a real client records inventory and progress before and after one eligible Qi-II spirit settlement
- **THEN** that item SHALL pass only if two stones are removed and progress gains 50 or the exact remaining cap capacity

#### Scenario: H action authority is observed
- **WHEN** each tab button is exercised with valid and invalid authoritative state
- **THEN** the item SHALL pass only when valid actions follow server results and invalid actions are rejected or downgraded without client-authored mutation

#### Scenario: A lifecycle path is not exercised
- **WHEN** save migration, relog, death, or dimension behavior was not directly observed
- **THEN** its ledger entry SHALL remain `not_verified`
