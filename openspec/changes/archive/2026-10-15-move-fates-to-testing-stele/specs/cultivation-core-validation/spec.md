## MODIFIED Requirements

### Requirement: A focused initiation validator gates integration and packaging
The repository SHALL provide standard-library-only `tools/validate_cultivation_initiation.py` and `tools/tests/test_validate_cultivation_initiation.py`. The validator SHALL check the change capability and KB note; both block/BlockItem registrations, creative-tab and `verifyRegistered` coverage; absence of BlockEntities; all blockstate/model/item-model/loot/tool-tag/lang resources; five legal explicit weights; fixed generator salt and prohibited time/position/current-dimension/unseeded-random dependencies; both services and shared evaluator; block/command service routing; `CultivationService` mutation boundary and absence of direct `player.setData`; unchanged clientbound snapshot direction, bounded input-only cultivation intents, clientbound awakening opening and token-only closure; unchanged current cultivation profile schema; basic-breathing requirements/no executor; read-only H screen; synchronized README/KB/CHANGELOG/version/jar examples; and jar inclusion. Algorithmic determinism, affinity math, atomic replacement, and mastery preservation MUST be proven by Java tests rather than source-string checks alone.

#### Scenario: Shipped initiation integration is valid
- **WHEN** `python3 tools/validate_cultivation_initiation.py` runs on the implemented worktree
- **THEN** it SHALL exit `0` only when every declared registration, resource, authority, scope, docs, release, and packaging invariant is satisfied

#### Scenario: A negative validator fixture removes one resource or bypasses a service
- **WHEN** the Python tests supply a missing block asset, invalid weight, direct attachment write, new cultivation C2S payload, wrong basic-breathing requirement, or stale version/doc fixture
- **THEN** validation SHALL fail with a diagnostic naming the violated invariant


## ADDED Requirements

### Requirement: Awakening handoff validates session authority and result-only H UI
Focused validators and behavioral tests SHALL cover stele routing, first empty-pool initialization, existing-root/draft/confirmed preservation, ordinary packet and command session gates, source-checked diagnostic authority, stale/throttled requests, block/distance/dimension/lifecycle invalidation, closure, read-only H Fates and client-side safety. Closeout SHALL run existing cultivation/fate/gameplay validators, full Gradle tests/build, current-jar inspection, strict specs, clean stage-1 acceptance and bounded real-client evidence. Test timing SHALL distinguish phases, environment, cache and retries.

#### Scenario: A candidate bypass is introduced
- **WHEN** an ordinary command or payload mutates fates without the valid testing-stele session checks
- **THEN** focused automated validation SHALL fail before release

#### Scenario: New UI evidence is prepared
- **WHEN** the awakening flow is submitted for review
- **THEN** the evidence SHALL separately record the mapped stele interaction, root-first presentation, draft close/reopen, confirmation, read-only H result and both-language layouts at all three supported sizes
- **AND** historical 0.49.0 H-page screenshots SHALL not count as current-screen evidence

#### Scenario: Developer evidence passes without owner review
- **WHEN** automated gates and scripted Minecraft interactions pass
- **THEN** owner visual/usability, combat feel and multiplayer verdicts SHALL remain `not_verified` until observed by the owner
