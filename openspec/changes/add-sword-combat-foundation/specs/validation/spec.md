## ADDED Requirements

### Requirement: PAL and sword combat have one focused validator with negative fixtures
The repository SHALL provide a focused standard-library validator and tests that inspect the exact PAL jar identity/metadata/license/API evidence, Gradle local-file failure contract, mod dependency metadata, PAL import side boundary, Qingfeng registration/attributes/creative tab, preference codec/attachment, payload fields/directions/protocol, session/definition wiring, input interception, damage hooks, hit-shape definitions, animation resources, the client-only five-pose first-person item extension, its independent segmented local-skin/sleeve joint renderer and shared corrected frame, the reference viewport-envelope contract, bilingual assets, recipe/tag, docs, and practical-jar contents. Negative fixtures SHALL prove specific failures rather than weaken checks around missing assets or prohibited authority.

#### Scenario: First-person item extension registration, continuity tuning, or pose distinction drifts
- **WHEN** a fixture removes Qingfeng `RegisterClientExtensionsEvent` registration, bypasses `applyForgeHandTransform`, aliases fewer than five move curves, removes the per-move viewport-envelope calibration or its `960x540`/`16:9`/FOV-70 evidence contract, permits a reported move envelope below `0.50` on both axes or wholly inside the lower-right quadrant, moves the revised wind-up/strike/recovery envelope outside its declared normalized ranges, or enables PAL `THIRD_PERSON_MODEL`
- **THEN** the focused validator SHALL fail with a named first-person integration finding

#### Scenario: Source checks pass without viewport evidence
- **WHEN** the first-person source invariants pass but no reference capture demonstrates the per-move temporal envelope, central-band intersection, grip, and clipping requirements
- **THEN** automated validation MAY pass while the revised first-person visual surface remains `not_verified`
- **AND** a fixed amplitude constant or developer source inspection SHALL NOT promote the owner verdict

#### Scenario: First-person arm integration drifts
- **WHEN** a fixture removes client-bootstrap `RenderHandEvent` registration, cancels the ordinary item pass, omits skin/sleeve rendering, wide/slim or right/left selection, the shoulder-driver/forearm/hand hierarchy, the screen-edge elbow connector, joint pose reset, per-move shoulder/elbow/wrist tracks, forward-kinematic grip correction, grip-anchored viewmodel scale, main-hand/invisibility/mode guards, the shared neutral fallback, or the common parent transform, gives the arm an elapsed-time clock separate from the item animator's corrected frame, or introduces copied Epic Fight/GeckoLib code or assets
- **THEN** the focused validator SHALL fail with a named first-person arm-integration finding

#### Scenario: Focused validation passes
- **WHEN** every declared source, resource, protocol, side, definition, documentation, and jar invariant is present
- **THEN** the focused validator SHALL exit zero with named successful checks

#### Scenario: A forbidden C2S field is introduced
- **WHEN** a fixture adds combo index, target, damage, position, velocity, or endpoint authority to an attack payload
- **THEN** validator tests SHALL prove a specific nonzero payload-authority result

#### Scenario: One move or animation drifts
- **WHEN** a fixture removes a move/animation id, changes duration outside tolerance, or aliases all hit shapes
- **THEN** validator tests SHALL prove a specific move-animation/hit-definition failure

#### Scenario: A PAL import leaks to common code
- **WHEN** a fixture adds a PAL client/controller import outside the client combat package
- **THEN** validator tests SHALL prove a side-safety failure

### Requirement: Java tests cover pure combat policy and geometry
Gradle tests SHALL cover CombatMode codec round-trip, default CombatPreference, move-one-through-five progression, timeout reset, fifth-move reset, one-slot buffer, early-input rejection, packet rate policy, weapon/mode interruption and recovery lock, hit deduplication, deterministic maximum targets, OBB/swept-volume geometry, wall/step policy boundaries where pure logic permits, and proof that the client payload model cannot specify combo index. Tests SHALL exercise pure definitions/state/math instead of relying only on source-text assertions.

#### Scenario: The pure combo suite runs
- **WHEN** `./gradlew test` executes
- **THEN** every declared transition, timeout, buffer, interruption, and reset case SHALL pass deterministically without a Minecraft client

#### Scenario: Geometry boundary tests run
- **WHEN** targets are placed just inside and outside each tolerance/sample boundary
- **THEN** narrow-phase and maximum-target results SHALL match the centralized definition deterministically

### Requirement: Gate A B and C are separately evidenced
Validation evidence SHALL record Gate A PAL integration/smoke, Gate B first-move vertical slice, and Gate C complete five-move expansion separately. Gate B SHALL not be inferred from compile-only PAL evidence, and Gate C SHALL not be inferred from one implemented move or five resource names.

#### Scenario: Only Gate A compiles
- **WHEN** dependency and side-safety startup pass but no client play/stop world smoke is observed
- **THEN** Gate A SHALL remain `not_verified` and Gate B SHALL not be reported complete

#### Scenario: Only move one is functional
- **WHEN** Gate B passes but the remaining state, shapes, animations, or step are absent/placeholders
- **THEN** Gate C and the overall change SHALL remain incomplete

#### Scenario: All gates are evidenced
- **WHEN** each gate has its required commands, runtime observations, artifacts, and no failing invariant
- **THEN** validation MAY proceed to the final manual ledger and closeout checks

### Requirement: Closeout runs the complete relevant automated gate set
Closeout SHALL run strict change validation, affected baseline spec validation, front-door validation, the focused PAL/combat validator and negative-fixture tests, `tools/validate_mod_items.py`, existing flying-sword and complete cultivation validators/tests, `./gradlew test`, `./gradlew build`, practical-jar inspection, a bounded `runAcceptanceServer`, and the client/PAL startup smoke. The final physical-client route SHALL accept `combat_smoke_server`, `combat_smoke_game_dir`, and `combat_smoke_username` Gradle properties so separate clients can connect without sharing mutable launcher state. A nonzero required command SHALL block completion and archive.

#### Scenario: One existing gameplay validator regresses
- **WHEN** combat integration breaks flying-sword or cultivation authority/side/resource behavior
- **THEN** the aggregate gate SHALL fail even if focused combat tests pass

#### Scenario: The dedicated server starts cleanly
- **WHEN** `runAcceptanceServer` loads MyVillage, GuideME, PAL, attachments, payloads, items, and combat services and then stops cleanly
- **THEN** the result SHALL count as registration/dependency/side-safety evidence only
- **AND** it SHALL not count as animation, hitbox, damage, or multiplayer acceptance

#### Scenario: Two isolated physical clients are required
- **WHEN** the bounded multiplayer animation path is reviewed
- **THEN** each client SHALL use a distinct game directory and username while connecting to the same server endpoint
- **AND** every client and server process SHALL be stopped after evidence capture

### Requirement: Practical jar validation covers third-party separation and all combat resources
After the final build, validation SHALL inspect the current-version MyVillage jar for Qingfeng Java/resource entries, all seven PAL animation ids, combat translations, recipe/tag, and absence of PAL classes/nested jar. It SHALL compare the artifact timestamp/version with source release metadata before handoff.

#### Scenario: A resource exists only in the source tree
- **WHEN** the expected model, texture, animation, recipe, tag, language, or class is missing from the built jar
- **THEN** jar validation SHALL fail with the missing entry

#### Scenario: PAL was accidentally shaded
- **WHEN** the built jar contains a PAL package or nested dependency jar
- **THEN** jar validation SHALL fail even if compilation and startup succeeded

### Requirement: Real-client combat acceptance uses pass fail or not_verified
Manual evidence SHALL use only `pass`, `fail`, or `not_verified` for Qingfeng appearance, vanilla diamond-sword behavior, mode action-bar feedback, exact interception, all five animation order/continuity, timeout, hit ranges, side/rear exclusion, wall blocking, per-action deduplication, fifth-step collision/cliff behavior, first-person support, remote multiplayer animation/damage, lifecycle cleanup, persistence, and existing item/cultivation/flying-sword regressions. Automated validators, jar listing, or startup SHALL not promote an unobserved item to `pass`.

#### Scenario: Automation passes without a real client
- **WHEN** every automated command and server smoke passes but no one plays the combat sequence
- **THEN** all visual, interaction, timing-feel, hit-range, first-person, and multiplayer items SHALL remain `not_verified`

#### Scenario: One manual item is observed
- **WHEN** the reviewer directly exercises that item and records evidence
- **THEN** only that item MAY change from `not_verified` to `pass` or `fail`

### Requirement: Visual assets require an explicit human verdict
The original Qingfeng texture/model and seven full-body animations SHALL have review evidence describing source paths, dimensions/format, representative render or in-game observation, blocking defects, and a human verdict state. Passing file-format checks SHALL not silently accept the appearance.

#### Scenario: Asset validators pass before review
- **WHEN** dimensions, alpha, JSON, ids, durations, and jar paths are correct but no visual verdict exists
- **THEN** task validation MAY pass while the human visual verdict remains pending

#### Scenario: The owner rejects an animation or texture
- **WHEN** the recorded verdict is reject or accept-with-changes
- **THEN** closeout SHALL remain blocked until required fixes and replacement evidence are complete
