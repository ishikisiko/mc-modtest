## ADDED Requirements

### Requirement: Cultivation initiation has a dedicated deterministic validator
The repository SHALL provide `tools/validate_cultivation_initiation.py` and `tools/tests/test_validate_cultivation_initiation.py`. The validator SHALL inspect the actual OpenSpec, Java wiring, datapack definitions, client/data resources, translations, documentation, version files, and built-jar surface for the initiation change. It SHALL report specific failures for missing or mismatched evidence and SHALL use Java tests, not source-text assertions, as the authority for deterministic math, affinity totals, atomic mutation, repeated-call state, and mastery preservation.

#### Scenario: The dedicated validator passes
- **WHEN** every initiation contract, integration, resource, authority, scope, documentation, release, and packaging invariant is present
- **THEN** `python3 tools/validate_cultivation_initiation.py` SHALL exit `0`

#### Scenario: A core algorithm is only mentioned in source
- **WHEN** source text contains deterministic-looking constants but the required Java golden/invariant tests are absent or failing
- **THEN** the validation handoff SHALL NOT claim deterministic generation is proven

#### Scenario: A validator negative fixture is exercised
- **WHEN** a fixture removes one required resource, alters a requirement, adds a prohibited payload/direct attachment write, or desynchronizes release documentation
- **THEN** the Python test suite SHALL prove a specific nonzero validator result

### Requirement: Initiation validation checks both functional BlockItems completely
Validation SHALL check that `spirit_testing_stele` and `technique_inheritance_stele` are distinct registered blocks and BlockItems, appear in `myvillage:main`, are covered by `ModBlocks.verifyRegistered`, own no BlockEntity, route to their respective services, and have complete blockstate, block model, item model, loot table, mineable tool tag, bilingual names/messages, and jar entries. It SHALL also confirm no recipe, natural generation, or combined state-dependent stele implementation is introduced.

#### Scenario: One stele lacks a loot table or tool tag
- **WHEN** initiation validation scans the shipped resource tree
- **THEN** it SHALL fail with the affected stele id and missing resource type

#### Scenario: Both BlockItems exist but call one combined block behavior
- **WHEN** runtime wiring does not preserve separate testing and inheritance service boundaries
- **THEN** validation SHALL fail rather than accepting registry ids alone

#### Scenario: Jar packaging is inspected
- **WHEN** `./gradlew build` succeeds
- **THEN** validation SHALL inspect the produced jar for both block classes and all declared resources
- **AND** source-tree existence alone SHALL NOT satisfy the packaging gate

### Requirement: Initiation server smoke checks registry, payload, and side errors
The bounded dedicated/acceptance-server smoke SHALL check logs for registry-freeze, datapack-registry, spiritual-element codec, technique codec, duplicate-payload registration, payload-direction, client-only classloading, missing block/item registry, missing model/loot/translation, cultivation-snapshot, and flying-sword-payload regressions. It SHALL stop the server cleanly and SHALL not be described as visual or interaction acceptance.

#### Scenario: Stage-1 acceptance startup is used
- **WHEN** `python3 tools/run_chunky_acceptance.py --stage 1` is the current repository smoke entry
- **THEN** its result SHALL be recorded as bounded server lifecycle/registration evidence only
- **AND** successful startup SHALL NOT mark the two stele interactions or H-screen appearance as passed

#### Scenario: The log contains an initiation registry or side error
- **WHEN** any listed error is present despite the process reaching a started state
- **THEN** the server-smoke gate SHALL fail

### Requirement: Initiation manual acceptance uses pass fail or not_verified
Manual evidence for stele interactions, messages/effects, exact H-screen phase data, repeated-action invariants, persistence, death/dimension lifecycle, command aliases, mining/drops/creative-tab presence, H-screen sharpness, flying-sword/existing-command regression, and absence of execution/progression SHALL use only `pass`, `fail`, or `not_verified`. No automated validator, build, jar listing, or server startup SHALL substitute for direct observation.

#### Scenario: A gameplay item was not personally observed
- **WHEN** the final handoff has no direct client/server evidence for that item
- **THEN** the item SHALL be recorded `not_verified`
- **AND** it SHALL NOT be inferred as `pass` from another gate

#### Scenario: A repeated inheritance is observed
- **WHEN** the reviewer uses the inheritance stele twice after setting nonzero mastery
- **THEN** the item SHALL pass only if the second use reports already learned and mastery remains unchanged
