## MODIFIED Requirements

### Requirement: Regression validation protects explicit non-goals
Validation SHALL preserve existing flying-sword payload tests and server checks, SHALL verify that initiation adds no cultivation client-to-server mutation payload, and SHALL verify that the H profile screen remains read-only and retains its sharp-content/background-blur ordering. The implementation diff SHALL contain no cultivation technique executor, meditation/recovery/progression/advancement/combat system, profile schema v2 field, root quality/reroll system, sect/region/worldgen integration, unnecessary BlockEntity/menu/recipe, or item/block beyond the two declared stele BlockItems. Release metadata changes SHALL be limited to the synchronized feature-version files required by `openspec/config.yaml`.

#### Scenario: Scope is reviewed before closeout
- **WHEN** CRAFT/front-door evidence and the final git diff are inspected
- **THEN** every changed implementation file SHALL map to deterministic awakening, basic-technique inheritance, the two steles, their validation, required documentation, or synchronized feature release metadata
- **AND** all excluded gameplay, worldgen, C2S cultivation payload, profile-schema, and flying-sword protocol surfaces SHALL remain unchanged

#### Scenario: Basic breathing is inspected
- **WHEN** shipped technique data and runtime registrations are validated
- **THEN** `myvillage:basic_breathing` SHALL have the declared initiation requirements
- **AND** it SHALL still have no executor, qi cost, recovery, progress, effect, attribute, or advancement implementation

## ADDED Requirements

### Requirement: Java tests pin deterministic root generation
The automated Java suite SHALL cover identical-input equality, input-order independence, fixed golden vectors, multiple UUID fixtures, one through five selected elements, positive-weight eligibility, no replacement, exact `10000` affinity total, positive selected affinities, absence of zero entries, single-element `10000`, candidate-count clamping, empty-candidate failure, maximum legal weights without overflow, invalid weight codec bounds, stable remainder ties, and independence from position, current dimension, time, and raw registry iteration order.

#### Scenario: Root generator tests run
- **WHEN** `./gradlew test` executes the initiation generator tests
- **THEN** all five count outcomes and affinity invariants SHALL be exercised
- **AND** golden vectors SHALL prove exact compatibility with the fixed salt/mixer/bounded-draw algorithm

#### Scenario: Codec weight bounds are tested
- **WHEN** spiritual-element definitions omit, minimize, maximize, underflow, or overflow `awakening_weight`
- **THEN** omission SHALL decode as `1`, values `0` and `1_000_000` SHALL be accepted, and out-of-range values SHALL be rejected

### Requirement: Java tests exercise atomic awakening state transitions
The automated Java suite SHALL cover default awakening; final root/stage/realm and preserved progress, stability, power, techniques, and schema version; exactly one injected profile-committer invocation; repeat no-reroll; rootless non-mortal rejection; empty candidates; failed-update equality; reset replay under unchanged inputs; and current-registry membership. Source validation SHALL verify that the live committer is `CultivationService#replaceProfile`. Actual attachment writes and client snapshot delivery SHALL remain manual integration evidence rather than being inferred from the injected seam.

#### Scenario: Awakening service tests run
- **WHEN** service fixtures execute success, repeat, invalid-state, no-candidate, and rejected-update paths
- **THEN** only success SHALL submit one final root-plus-stage replacement to the injected committer
- **AND** every failure SHALL preserve the old profile without invoking that committer

### Requirement: Java tests exercise definition-driven inheritance behavior
The automated Java suite SHALL cover the shipped basic-breathing minimum realm and stage, absence of element/affinity restrictions, one-through-five-element eligibility, unawakened and wrong-stage failure, evaluator use of current definitions, successful mastery exactly `0`, preservation of every unrelated field/technique, exactly one injected profile-committer invocation, repeat no-reset, missing technique definition, requirement failure, failed-update equality, and preservation of unknown saved technique ids. Source validation SHALL verify that the live committer is `CultivationService#replaceProfile`; actual attachment writes and client snapshot delivery SHALL remain manual integration evidence.

#### Scenario: Requirement evaluator tests run
- **WHEN** profiles below, at, and above declared realm/stage floors plus affinity fixtures are evaluated
- **THEN** results SHALL derive from `TechniqueDefinition.requirements` and current registry ordering/membership
- **AND** missing or ambiguous definitions SHALL fail closed

#### Scenario: Repeat inheritance has prior mastery
- **WHEN** a profile already stores basic breathing at nonzero mastery
- **THEN** normal inheritance SHALL fail without replacing the profile or changing that mastery

### Requirement: Automated validation covers commands, steles, resources, and side safety
Automated command-tree tests SHALL verify both command roots contain `awaken`, `juexing`, `initiate`, and `rumen`; English/pinyin structures and optional-target descendants are equivalent; forbidden arguments are absent; and existing commands remain. Static validation SHALL verify that awaken routes use the awakening service, initiate routes use the inheritance service, both blocks and BlockItems register distinctly, both join the creative tab and `verifyRegistered`, no BlockEntity exists, sided-success handling is present, and common/server code remains free of client-only classloading. Physical main/off-hand dispatch SHALL remain manual interaction evidence.

#### Scenario: Command trees are compared
- **WHEN** tests enumerate `/myvillage cultivation` and `/myvillage xiulian`
- **THEN** all four awakening paths and four inheritance paths SHALL be equivalent in descendants and handlers
- **AND** existing command literals SHALL remain present

#### Scenario: Stele integration is validated
- **WHEN** registration and static interaction-source checks inspect both stele ids
- **THEN** each BlockItem SHALL match its own block and service
- **AND** physical one-use/main-off-hand behavior SHALL remain `not_verified` until directly observed

### Requirement: A focused initiation validator gates integration and packaging
The repository SHALL provide standard-library-only `tools/validate_cultivation_initiation.py` and `tools/tests/test_validate_cultivation_initiation.py`. The validator SHALL check the change capability and KB note; both block/BlockItem registrations, creative-tab and `verifyRegistered` coverage; absence of BlockEntities; all blockstate/model/item-model/loot/tool-tag/lang resources; five legal explicit weights; fixed generator salt and prohibited time/position/current-dimension/unseeded-random dependencies; both services and shared evaluator; block/command service routing; `CultivationService` mutation boundary and absence of direct `player.setData`; unchanged clientbound snapshot direction and absence of cultivation C2S payload; profile schema `1`; basic-breathing requirements/no executor; read-only H screen; synchronized README/KB/CHANGELOG/version/jar examples; and jar inclusion. Algorithmic determinism, affinity math, atomic replacement, and mastery preservation MUST be proven by Java tests rather than source-string checks alone.

#### Scenario: Shipped initiation integration is valid
- **WHEN** `python3 tools/validate_cultivation_initiation.py` runs on the implemented worktree
- **THEN** it SHALL exit `0` only when every declared registration, resource, authority, scope, docs, release, and packaging invariant is satisfied

#### Scenario: A negative validator fixture removes one resource or bypasses a service
- **WHEN** the Python tests supply a missing block asset, invalid weight, direct attachment write, new cultivation C2S payload, wrong basic-breathing requirement, or stale version/doc fixture
- **THEN** validation SHALL fail with a diagnostic naming the violated invariant

### Requirement: Initiation validation runs the complete automated handoff
Closeout validation SHALL separately record targeted strict change validation, all baseline specs strict validation, `python3 tools/validate_cultivation_core.py`, `python3 tools/validate_cultivation_initiation.py`, both corresponding Python unit-test modules, applicable mod-item validation, `./gradlew test`, `./gradlew build`, jar-content inspection, and the repository's bounded dedicated/acceptance-server smoke. A nonzero required command SHALL block completion.

#### Scenario: Every automated gate passes
- **WHEN** the final implemented worktree is prepared for closeout
- **THEN** CRAFT evidence SHALL contain the actual exit result for every required command
- **AND** the built jar SHALL contain both stele classes and every declared block/data/client resource

#### Scenario: One gate fails
- **WHEN** any strict spec, validator, unit-test, Gradle, jar-content, or required server-smoke gate exits nonzero
- **THEN** the change SHALL not be reported complete, archived, merged, or pushed as a successful release

### Requirement: Dedicated-server smoke covers initiation registration and regressions
The bounded server smoke SHALL start and stop cleanly and SHALL inspect logs for registry-freeze, datapack-registry, spiritual-element codec, technique codec, duplicate-payload, payload-direction, client-only classloading, missing block/item registry, missing model/loot/translation, cultivation-snapshot, and flying-sword-payload errors. Passing startup SHALL prove registration and side safety only, not player interaction or visual acceptance.

#### Scenario: The bounded server starts and stops
- **WHEN** the current stage-1 acceptance or equivalent dedicated-server smoke runs
- **THEN** both stele registries and updated cultivation datapack definitions SHALL load without listed errors
- **AND** the process SHALL be stopped and awaited cleanly

#### Scenario: No client session was observed
- **WHEN** server startup passes without a real Minecraft client interaction pass
- **THEN** stele use, H-screen display, persistence, death, dimension, and visual items SHALL remain `not_verified`

### Requirement: Manual initiation acceptance records only observed results
The manual checklist SHALL record `pass`, `fail`, or `not_verified` for the three H-screen phases; one-shot testing-stele awakening and repeat no-reroll/effect; separate inheritance-stele learning and repeat no-mastery-reset; relog, save/restart, true death, dimension change, reset plus exact deterministic reawakening; all eight English/pinyin command routes; stele mining/drops/creative-tab presence; H-screen sharpness; existing cultivation commands and flying sword; and continued absence of technique execution, cultivation/power gain, or automatic qi-refining advancement. Automated build/startup evidence SHALL NOT be upgraded into an observed manual result.

#### Scenario: Only automation has run
- **WHEN** all automated gates and server smoke pass but no real client session was performed
- **THEN** every interaction, persistence, visual, command-behavior, and gameplay-regression checklist item SHALL remain `not_verified`

#### Scenario: Deterministic reset is manually checked
- **WHEN** an operator records all affinities, resets, and reawakens under unchanged deterministic inputs
- **THEN** that item SHALL pass only if every basis-point value is exactly equal

### Requirement: OpenSpec and documentation evidence covers the complete initiation change
The active change SHALL contain proposal, design, tasks, the `cultivation-initiation-ritual` capability, and all affected baseline deltas. Strict validation SHALL pass before implementation closeout. Documentation SHALL add and index `docs/ai-kb/29_cultivation_initiation_ritual.md`, update the foundation boundary and README/AGENTS command/acceptance guidance, and apply the synchronized feature version rule from `openspec/config.yaml`.

#### Scenario: The change is strictly validated
- **WHEN** `openspec validate add-cultivation-initiation-ritual --type change --strict` runs
- **THEN** every requirement SHALL use normative language and scenario coverage
- **AND** every proposal capability SHALL have a matching delta spec

#### Scenario: Release documentation is inspected
- **WHEN** closeout checks version-sensitive files
- **THEN** mod version, mod metadata, README jar examples, and CHANGELOG SHALL agree
- **AND** README/KB SHALL distinguish implemented initiation from excluded meditation, recovery, progress gain, advancement, and technique execution
