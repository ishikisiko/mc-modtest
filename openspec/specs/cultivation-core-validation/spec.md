# cultivation-core-validation Specification

## Purpose

Define the automated, dedicated-server, documentation, and manual acceptance
evidence required for the foundation-only cultivation runtime.
## Requirements
### Requirement: Java tests cover profile defaults, codecs, invariants, and immutable updates
The automated Java test suite SHALL cover the exact default profile, full `CultivationProfile.CODEC` round trip, valid spiritual-root total, invalid spiritual-root total, per-affinity range rejection, stability range rejection, negative progress/power/mastery rejection, immutable update behavior, unknown `ResourceLocation` decoding, realm-stage membership validation, and learn/forget/mastery transitions.

#### Scenario: Profile tests run
- **WHEN** `./gradlew test` executes the cultivation profile tests
- **THEN** a valid default and valid non-default profile SHALL pass
- **AND** every requested invalid numeric/root case SHALL fail construction or decoding
- **AND** update tests SHALL prove the old profile instance and nested maps remain unchanged

#### Scenario: Unknown ids are tested
- **WHEN** a test decodes and re-encodes a profile containing unregistered but syntactically valid ids
- **THEN** the round trip SHALL preserve those ids without requiring live registry definitions

#### Scenario: Realm-stage and technique transitions are tested
- **WHEN** service/domain tests exercise valid and invalid realm-stage pairs plus learn, forget, and set-mastery operations
- **THEN** valid operations SHALL preserve all invariants
- **AND** invalid operations SHALL leave the old profile unchanged

### Requirement: Java tests cover every definition codec
The automated Java test suite SHALL round-trip representative `RealmDefinition`, `RealmStageDefinition`, `SpiritualElementDefinition`, `TechniqueDefinition`, `TechniqueCategory`, and `TechniqueRequirements` values. It SHALL also cover locally invalid definition values and stable string category encoding.

#### Scenario: Definition codecs run
- **WHEN** representative valid definitions are encoded and decoded
- **THEN** every declared field and referenced id SHALL be preserved

#### Scenario: Invalid definition metadata is tested
- **WHEN** tests supply a negative order/grade, invalid color, duplicate technique element, missing-realm stage requirement, or out-of-range affinity requirement
- **THEN** the corresponding definition codec or validator SHALL reject it

### Requirement: The snapshot StreamCodec has a real buffer round-trip test
The Java test suite SHALL encode and decode `CultivationSnapshotPayload` through its actual `StreamCodec` and a registry-friendly network buffer/provider compatible with Minecraft `1.21.1`. The test SHALL compare the complete decoded profile and SHALL NOT rely only on source-text matching.

#### Scenario: Snapshot network data round-trips
- **WHEN** a non-default snapshot with a root, learned techniques, and unknown ids is written to and read from the test buffer
- **THEN** the decoded payload SHALL equal the original payload
- **AND** the buffer SHALL be fully consumed as expected by the codec

### Requirement: A deterministic Python validator gates shipped cultivation data
The repository SHALL provide `tools/validate_cultivation_core.py` using only Python standard-library dependencies. It SHALL parse the shipped custom-registry JSON and language files; validate required fields, types, ids, and numeric bounds; enforce unique realm and stage ids plus valid unique stage order; resolve next-realm, technique element, minimum-realm, and minimum-stage references; confirm minimum-stage membership; verify the five foundation elements and `myvillage:basic_breathing`; and require every declared translation key in `en_us` and `zh_cn`. Invalid or missing references MUST NOT be silently ignored.

#### Scenario: Shipped cultivation data is valid
- **WHEN** `python3 tools/validate_cultivation_core.py` runs against the implemented resource tree
- **THEN** it SHALL exit with code `0`
- **AND** it SHALL report the validated registry entry counts and required ids

#### Scenario: A technique references a missing element
- **WHEN** validator input contains a technique element id absent from the spiritual-element registry
- **THEN** validation SHALL fail with the technique id and missing element id

#### Scenario: A technique references a mismatched realm-stage pair
- **WHEN** validator input contains a minimum stage not owned by its minimum realm
- **THEN** validation SHALL fail with the technique, realm, and stage ids

#### Scenario: A required foundation definition is absent
- **WHEN** any required element, realm, stage, or `basic_breathing` file is missing
- **THEN** validation SHALL fail rather than reducing the required set

### Requirement: Cultivation validation participates in the repository build handoff
The change validation SHALL run, in order, `python3 tools/validate_cultivation_core.py`, `./gradlew test`, and `./gradlew build`. Command documentation SHALL identify these commands. A failing command SHALL block a green change result.

#### Scenario: One automated gate fails
- **WHEN** the cultivation validator, tests, or practical jar build exits non-zero
- **THEN** CRAFT evidence SHALL record the failure
- **AND** the change SHALL NOT be reported complete or closeout-ready

#### Scenario: All three automated gates pass
- **WHEN** every command exits zero on the implemented worktree
- **THEN** the validation evidence SHALL record each real command result separately

### Requirement: Dedicated-server smoke proves registration and side safety
The acceptance handoff SHALL include a bounded dedicated-server or acceptance-server startup that is explicitly stopped. Its evidence SHALL confirm that all three custom datapack registries load, the attachment type registers, the cultivation command subtree registers, the clientbound cultivation payload registers, the existing serverbound flying-sword payload still registers, and no registry-freeze, codec, datapack-path, payload-direction, duplicate-handler, or client-only classloading error occurs.

#### Scenario: Dedicated-server startup succeeds
- **WHEN** the bounded server smoke reaches a fully started state and is then stopped cleanly
- **THEN** the captured log SHALL contain no cultivation registration or side-only failure
- **AND** validation SHALL record the smoke as passed

#### Scenario: Startup cannot complete in the environment
- **WHEN** a real environmental blocker prevents the bounded server smoke
- **THEN** the handoff SHALL record the blocker and available logs
- **AND** it SHALL mark the smoke unverified rather than passed

### Requirement: Manual cultivation acceptance is explicit and evidence-based
The acceptance checklist SHALL cover: default `info`; valid `setroot`; invalid-total `setroot` with unchanged profile; valid `setrealm`; mismatched stage rejection; registered technique learning; unregistered technique rejection; save/restart persistence; true-death preservation; End-return preservation without duplicate copy; and dimension-change snapshot delivery. Each item SHALL record observed pass/fail or `not_verified`.

#### Scenario: Only automated validation has run
- **WHEN** validator, tests, build, and startup smoke pass but no player lifecycle session was observed
- **THEN** save/restart, true-death, End-return, command behavior, and dimension snapshot items SHALL remain `not_verified`
- **AND** the final report SHALL NOT claim those behaviors were observed

#### Scenario: An invalid-root command is manually tested
- **WHEN** an operator records the profile before and after a `setroot` whose total is not `10000`
- **THEN** acceptance SHALL pass that item only when the command fails and the encoded profile remains unchanged

#### Scenario: End return is manually tested
- **WHEN** a player exits the End with a non-default profile
- **THEN** acceptance SHALL pass that item only when every profile field is preserved exactly once on the replacement player

### Requirement: Documentation describes shipped infrastructure and its limits
The same change SHALL add `docs/ai-kb/28_cultivation_core.md`, list it in `docs/ai-kb/INDEX.md`, cross-link it with the cultivation capability specs, and update README with administrator commands, validation steps, profile schema, registry keys/paths, lifecycle/synchronization behavior, and later extension points. README SHALL state that the current implementation contains cultivation data infrastructure only and has no meditation, breakthrough, or technique gameplay.

#### Scenario: Cultivation documentation is reviewed
- **WHEN** implementation is prepared for closeout
- **THEN** the KB entry and README SHALL match the actual commands, fields, paths, and validation commands
- **AND** they SHALL distinguish implemented infrastructure from future gameplay

### Requirement: Regression validation protects explicit non-goals
Validation SHALL preserve the flying-sword protocol, clientbound-only
profile/time/status authority, immutable v2 profile replacement,
server-authoritative cultivation intents, read-only H screen, two independent
initiation rituals, lifespan invariance, stage-local caps, and declared Basic
Breathing settlement. This change MAY add only the fourth bounded action,
definition-owned four-transition sequence, transient advancement states,
deterministic success costs, and exact Qi-III bottleneck loss. It SHALL add no
client target/result, random success, overflow/chaining, Qi IV+ gain or
advancement, Foundation Establishment process, major-realm rule, pill/facility/
environment requirement, tribulation, or reincarnation behavior. Release
metadata SHALL follow `openspec/config.yaml`.

#### Scenario: Scope is reviewed before closeout
- **WHEN** final code, data, payload registrations, and validation evidence are inspected
- **THEN** every transition SHALL map to one of the four declared source-stage rules
- **AND** every deferred later-stage, major-realm, material, random, and client-authority surface SHALL remain absent

#### Scenario: Protocol regression is inspected
- **WHEN** all payload codecs and directions are enumerated
- **THEN** the cultivation intent SHALL contain only four bounded actions without target/result fields
- **AND** the flying-sword input and clientbound cultivation paths SHALL retain their contracts

### Requirement: OpenSpec evidence validates strictly before implementation closeout
The active change SHALL contain proposal, design, tasks, and delta specifications for all six cultivation foundation capabilities. `openspec validate add-cultivation-core-foundation --type change --strict` SHALL pass before closeout, and archive SHALL synchronize the six capabilities into the implemented baseline without editing baseline specs directly during proposal authoring.

#### Scenario: The active change is validated
- **WHEN** strict single-change validation runs
- **THEN** every requirement SHALL use normative `SHALL` or `MUST` language
- **AND** every requirement SHALL have at least one four-hash WHEN/THEN scenario

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
The archived initiation change SHALL retain proposal, design, completed tasks, the `cultivation-initiation-ritual` capability delta, and all affected baseline deltas. Strict change validation SHALL pass before archive, and the synchronized main specs SHALL continue to pass strict validation afterward. Documentation SHALL add and index `docs/ai-kb/29_cultivation_initiation_ritual.md`, update the foundation boundary and README/AGENTS command/acceptance guidance, and apply the synchronized feature version rule from `openspec/config.yaml`.

#### Scenario: Archived evidence and synchronized specs are validated
- **WHEN** the archived change evidence is inspected and `openspec validate --specs --strict` runs
- **THEN** the archive SHALL contain the completed proposal, design, tasks, and every declared delta spec
- **AND** every synchronized main capability SHALL pass strict validation

#### Scenario: Release documentation is inspected
- **WHEN** closeout checks version-sensitive files
- **THEN** mod version, mod metadata, README jar examples, and CHANGELOG SHALL agree
- **AND** README/KB SHALL distinguish implemented initiation from excluded meditation, recovery, progress gain, advancement, and technique execution

### Requirement: Automated tests prove v1-to-v2 migration and time arithmetic
Java tests SHALL cover exact v1 fixtures, unknown ids, v2 round trips, negative
counters, unsupported versions, default/reset, all profile-copy helpers,
initiation preservation, checked scale products, calendar eligibility, personal
eligibility, 600-tick batching, warning thresholds, exhaustion, and counter
saturation.

#### Scenario: The migration suite runs
- **WHEN** tests decode representative default, non-default, unknown-id, and over-cap v1 profiles
- **THEN** every old field SHALL be preserved and both new fields SHALL equal zero

#### Scenario: The time suite runs
- **WHEN** eligible/excluded player and config fixtures execute
- **THEN** clock increments, pauses, thresholds, reinterpretation, and overflow handling SHALL match the capability exactly

### Requirement: Lifecycle and UI evidence remain truthful
Automated integration SHALL cover payload codecs/directions, dedicated-server
side safety, registry/config/SavedData loading, and bounded server startup.
Relog, death, End return, dimension change, configuration reinterpretation,
warning delivery, H-screen layout, and clean-stop flush SHALL remain
`not_verified` until each is directly observed or supported by its declared
integration test surface.

#### Scenario: Only unit tests build and server startup pass
- **WHEN** no real client lifecycle session is observed
- **THEN** the manual lifecycle, warning, and visual rows SHALL remain `not_verified`

### Requirement: Automated tests cover meditation authority and interruption
Tests SHALL cover every legal/unknown action, payload round trip,
sender-derived identity, duplicate/rate-limited starts, idempotent stop, all
eligibility gates, 40-tick transitions, 100-tick damage window, camera-only
rotation, movement tolerance, every interruption/lifecycle cause, no persistent
state, and no profile/inventory mutation.

#### Scenario: The focused meditation suite runs
- **WHEN** Gradle and validator tests execute
- **THEN** normal/spirit preparation, active, rejection, interruption, and cleanup paths SHALL match the server-authoritative contract

### Requirement: Manual meditation evidence is not inferred from startup
Real-client checks SHALL separately record V/B/X input, feedback, camera
rotation, movement/jump, damage, attack, mining, use, mount, swim/flight,
dimension, death, logout, H interaction, and multiplayer authority as `pass`,
`fail`, or `not_verified`.

#### Scenario: Automated gates pass without a client
- **WHEN** tests, build, and server startup pass but no gameplay session is observed
- **THEN** every interaction and feel item SHALL remain `not_verified`

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

### Requirement: Automated tests pin every advancement rule and gate
Java tests and a focused deterministic validator SHALL cover all four source,
target, cap, kind, duration, requirement, success-cost, and interruption-loss
tuples; missing/invalid/self/mismatched targets; unsupported kinds; every start
gate; completion revalidation; and Qi-IV release-ceiling rejection.

#### Scenario: The shipped advancement matrix is tested
- **WHEN** data validation enumerates qi-sensed through Qi IV
- **THEN** it SHALL find ordinary `100/10/5/0`, ordinary `100/20/10/0`, ordinary `120/30/15/0`, and bottleneck `200/80/30/5`
- **AND** it SHALL find no fifth rule

### Requirement: Automated tests prove interruption and atomicity
Tests SHALL exercise every shared interruption, allowed yaw/pitch, idempotent
overlapping hooks, ordinary zero loss, bottleneck exact five loss, administrative
zero loss, one immutable success/penalty replacement, complete v2 preservation,
progress reset, one snapshot, no randomness, and no multi-stage chaining.

#### Scenario: A bottleneck interruption matrix runs
- **WHEN** each player/world interruption is applied to an active Qi-III bottleneck
- **THEN** the session SHALL stop and stability SHALL lose exactly five once
- **AND** ordinary and administrative teardown fixtures SHALL lose zero

#### Scenario: Atomic success runs with non-default v2 data
- **WHEN** each of the four transitions completes from a profile with nonzero lifespan, reserve, power, root, and mastery
- **THEN** only realm, stage, progress, and declared stability cost SHALL differ
- **AND** one final snapshot SHALL be observed

### Requirement: Advancement runs the complete automated and manual handoff
Closeout SHALL run strict validation for this change and implemented baseline,
all cultivation/resource validators introduced by the five serial changes,
focused tests, Gradle tests/build, practical jar inspection, and bounded
acceptance-server smoke. Manual V/B/X/N, H, timing, interruption, two-stele,
inventory, ore/worldgen, and presentation observations SHALL retain explicit
pass/fail/`not_verified` evidence.

#### Scenario: Final serial handoff succeeds
- **WHEN** the five-change feature is proposed for closeout
- **THEN** every automated spec, validator, test, build, jar, and server-smoke surface SHALL pass
- **AND** unobserved real-client behavior SHALL remain `not_verified`

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
