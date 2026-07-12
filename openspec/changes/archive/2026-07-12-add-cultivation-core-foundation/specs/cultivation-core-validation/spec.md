## ADDED Requirements

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
Validation SHALL preserve existing flying-sword payload tests and server checks and SHALL verify that cultivation integration adds no client-to-server cultivation mutation payload. The implementation diff SHALL contain no structure/worldgen integration, new item/block/entity, technique executor, release metadata, mod version, or changelog change.

#### Scenario: Scope is reviewed before closeout
- **WHEN** CRAFT/front-door evidence and the final git diff are inspected
- **THEN** every changed implementation file SHALL map to the cultivation foundation, its validation, or its required documentation
- **AND** release/version/changelog and excluded gameplay files SHALL remain unchanged

### Requirement: OpenSpec evidence validates strictly before implementation closeout
The active change SHALL contain proposal, design, tasks, and delta specifications for all six cultivation foundation capabilities. `openspec validate add-cultivation-core-foundation --type change --strict` SHALL pass before closeout, and archive SHALL synchronize the six capabilities into the implemented baseline without editing baseline specs directly during proposal authoring.

#### Scenario: The active change is validated
- **WHEN** strict single-change validation runs
- **THEN** every requirement SHALL use normative `SHALL` or `MUST` language
- **AND** every requirement SHALL have at least one four-hash WHEN/THEN scenario
